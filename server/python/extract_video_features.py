#!/usr/bin/env python3
"""
视频内容特征提取流水线 v2
- 视觉理解: Chinese-CLIP ViT-L/14 (transformers 直出视觉向量, 零样本文本分类)
- 文本理解: Chinese-CLIP 文本编码 + BGE-M3 深度语义
- 音频分析: librosa (BPM / MFCC / 能量 / 效价)
- 向量融合: CLIP 视觉+文本 → 融合音频 → 512 维内容向量

用法: python extract_video_features.py --video-url <url> --video-id <id> [--desc ...] [--music-title ...]
RTX 4060 8GB + i9-14900, 首次需下载模型(~3GB), 后续单视频 ~3-5 秒
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
import traceback
from pathlib import Path

import httpx
import jieba
import librosa
import numpy as np
from sentence_transformers import SentenceTransformer
from PIL import Image
import torch

# ===================== 配置 =====================
CLIP_MODEL = "OFA-Sys/chinese-clip-vit-large-patch14-336px"  # 中文 CLIP, 1024-dim
TEXT_EMBED_MODEL = "BAAI/bge-m3"  # 深度文本语义, 1024-dim

FRAME_COUNT = 16          # 采样帧数
FRAME_SIZE = 336          # Chinese-CLIP ViT-L 原生分辨率
CLIP_DIM = 1024           # ViT-L 输出维度
AUDIO_SR = 22050
AUDIO_DURATION = 30       # 最多分析前 30 秒音频
CONTENT_VECTOR_DIM = 512  # 融合向量维度
CLIP_VISUAL_WEIGHT = 0.55 # 视觉权重 (CLIP 空间内)
CLIP_TEXT_WEIGHT = 0.45   # 文本权重 (CLIP 空间内)

# GPU 配置
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

# ===================== 零样本分类标签集 =====================

CATEGORY_LABELS = [
    "美食", "舞蹈", "搞笑", "知识科普", "旅行风景",
    "音乐演唱", "时尚美妆", "体育运动", "数码科技", "萌宠动物",
    "影视娱乐", "日常Vlog", "游戏电竞", "汽车机车", "母婴亲子",
    "家居装修", "才艺展示", "二次元动漫", "乡村三农", "综合"
]

SCENE_LABELS = [
    "室内房间", "厨房", "餐厅", "办公室", "教室", "商场店铺",
    "城市街道", "公园绿地", "海边沙滩", "山川自然", "演播舞台",
    "车内空间", "夜景城市", "天空航拍", "运动场馆", "田园乡村"
]

OBJECT_LABELS = [
    "人物", "美食菜品", "宠物猫狗", "手机", "电脑笔记本",
    "化妆品", "乐器", "运动器材", "汽车", "花卉植物",
    "书本", "玩具", "家具", "服饰", "餐具厨具"
]

MOOD_LABELS = ["欢乐", "温馨", "伤感", "酷炫", "搞笑", "安静", "紧张", "治愈", "热血"]

STYLE_LABELS = ["Vlog记录", "教程教学", "才艺表演", "日常随拍", "混剪剪辑", "口播解说", "直播互动", "旅拍打卡"]

QUALITY_LABELS = ["画质精良专业", "画质清晰良好", "画质一般普通", "画面抖动模糊"]


# ===================== 工具函数 =====================

def ensure_ffmpeg():
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("[ERROR] ffmpeg 未安装或不在 PATH 中", file=sys.stderr)
        sys.exit(1)


def download_video(url: str, dest: str):
    print(f"  [下载] {url[:80]}...")
    import urllib.request
    urllib.request.urlretrieve(url, dest)
    size_mb = os.path.getsize(dest) / 1024 / 1024
    print(f"  [下载] 完成, {size_mb:.1f} MB")


def _get_video_duration(video_path: str) -> float:
    cmd = [
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", video_path
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    try:
        return float(result.stdout.strip())
    except (ValueError, AttributeError):
        return 15.0


def extract_frames(video_path: str, output_dir: str, count: int = FRAME_COUNT, size: int = FRAME_SIZE) -> list[str]:
    out_pattern = os.path.join(output_dir, "frame_%03d.jpg")
    duration = _get_video_duration(video_path)
    interval = max(1, duration / count)
    cmd = [
        "ffmpeg", "-y", "-i", video_path,
        "-vf", f"fps=1/{interval},scale={size}:{size}:force_original_aspect_ratio=decrease,pad={size}:{size}:(ow-iw)/2:(oh-ih)/2",
        "-q:v", "2",
        "-frames:v", str(count),
        out_pattern
    ]
    subprocess.run(cmd, capture_output=True, check=True)
    import glob
    frames = sorted(glob.glob(os.path.join(output_dir, "frame_*.jpg")))
    if not frames:
        fallback = os.path.join(output_dir, "frame_000.jpg")
        subprocess.run([
            "ffmpeg", "-y", "-i", video_path, "-vframes", "1",
            "-vf", f"scale={size}:{size}:force_original_aspect_ratio=decrease,pad={size}:{size}:(ow-iw)/2:(oh-ih)/2",
            fallback
        ], capture_output=True, check=True)
        frames = [fallback]
    print(f"  [抽帧] 共 {len(frames)} 帧")
    return frames


def extract_audio(video_path: str, output_path: str, max_duration: int = AUDIO_DURATION) -> str:
    cmd = [
        "ffmpeg", "-y", "-i", video_path,
        "-t", str(max_duration), "-ar", str(AUDIO_SR), "-ac", "1", "-q:a", "0",
        output_path
    ]
    result = subprocess.run(cmd, capture_output=True)
    if result.returncode != 0 or not os.path.exists(output_path):
        return ""
    return output_path


# ===================== 视觉分析 (Chinese-CLIP) =====================

def _get_clip():
    """懒加载 Chinese-CLIP 模型 + 处理器"""
    if not hasattr(_get_clip, "_model"):
        from transformers import ChineseCLIPModel, ChineseCLIPProcessor
        print(f"  [CLIP] 加载模型 {CLIP_MODEL} (device={DEVICE})...")
        _get_clip._model = ChineseCLIPModel.from_pretrained(CLIP_MODEL).to(DEVICE).eval()
        _get_clip._processor = ChineseCLIPProcessor.from_pretrained(CLIP_MODEL)
        print(f"  [CLIP] 加载完成")
    return _get_clip._model, _get_clip._processor


def _tensor_from_clip(output):
    """兼容新版 transformers 返回 BaseModelOutputWithPooling 而非纯 tensor"""
    if hasattr(output, 'pooler_output'):
        return output.pooler_output
    if isinstance(output, torch.Tensor):
        return output
    # dict-like
    if hasattr(output, 'last_hidden_state'):
        return output.last_hidden_state[:, 0, :]
    raise TypeError(f"Unexpected CLIP output type: {type(output)}")


@torch.no_grad()
def analyze_frames_clip(frame_paths: list[str]) -> dict:
    """
    Chinese-CLIP 视觉分析:
    1. 逐帧提取 1024-dim 视觉 embedding
    2. 零样本分类: 品类 / 场景 / 物体 / 情绪 / 风格 / 画质
    返回: { visual_embedding, visual_desc, scene_tags, object_tags, category, mood, style, quality_label }
    """
    model, processor = _get_clip()

    # --- 1. 提取每帧视觉向量 ---
    all_visual_embeds = []
    batch_size = 8
    for start in range(0, len(frame_paths), batch_size):
        batch_paths = frame_paths[start:start + batch_size]
        images = [Image.open(fp).convert("RGB") for fp in batch_paths]
        inputs = processor(images=images, return_tensors="pt", padding=True).to(DEVICE)
        img_embeds = _tensor_from_clip(model.get_image_features(**inputs))  # (B, 1024)
        img_embeds = img_embeds / img_embeds.norm(dim=-1, keepdim=True)
        all_visual_embeds.append(img_embeds.cpu().numpy())

    visual_array = np.concatenate(all_visual_embeds, axis=0)  # (N, 1024)
    visual_embedding = visual_array.mean(axis=0)               # (1024,) 视频级均值
    visual_embedding = visual_embedding / (np.linalg.norm(visual_embedding) + 1e-8)

    # --- 2. 零样本分类 ---
    def zero_shot_classify(label_texts: list[str]) -> np.ndarray:
        """对所有帧做零样本分类, 返回 (N_frames, N_labels) 平均概率"""
        text_inputs = processor(text=label_texts, return_tensors="pt", padding=True).to(DEVICE)
        text_embeds = _tensor_from_clip(model.get_text_features(**text_inputs))  # (L, 1024)
        text_embeds = text_embeds / text_embeds.norm(dim=-1, keepdim=True)

        visual_t = torch.from_numpy(visual_array).to(DEVICE).float()  # (N, 1024)
        logits = visual_t @ text_embeds.T * model.logit_scale.exp()    # (N, L)
        probs = logits.softmax(dim=-1).cpu().numpy()
        return probs.mean(axis=0)  # 均值聚合各帧

    cat_probs = zero_shot_classify(CATEGORY_LABELS)
    scene_probs = zero_shot_classify(SCENE_LABELS)
    object_probs = zero_shot_classify(OBJECT_LABELS)
    mood_probs = zero_shot_classify(MOOD_LABELS)
    style_probs = zero_shot_classify(STYLE_LABELS)
    quality_probs = zero_shot_classify(QUALITY_LABELS)

    # --- 3. 提取标签(置信度 > 阈值) ---
    def top_labels(labels: list[str], probs: np.ndarray, threshold: float = 0.08, top_k: int = 5) -> list[str]:
        idxs = np.argsort(probs)[::-1]
        result = []
        for i in idxs:
            if probs[i] >= threshold and len(result) < top_k:
                result.append(labels[i])
        return result

    category_top = CATEGORY_LABELS[int(np.argmax(cat_probs))]
    scene_tags = top_labels(SCENE_LABELS, scene_probs, threshold=0.05)
    object_tags = top_labels(OBJECT_LABELS, object_probs, threshold=0.05)
    mood_top = MOOD_LABELS[int(np.argmax(mood_probs))]
    style_top = STYLE_LABELS[int(np.argmax(style_probs))]
    quality_label = QUALITY_LABELS[int(np.argmax(quality_probs))]

    # 丰富 scene_tags: 加入 mood / style / quality 作为属性标签
    enriched_scene = list(scene_tags)
    for prefix, val in [("情绪:", mood_top), ("风格:", style_top), ("画质:", quality_label)]:
        if val:
            enriched_scene.append(f"{prefix}{val}")

    # 生成结构化视觉描述
    visual_desc = f"品类: {category_top} | 场景: {', '.join(scene_tags[:3])} | "
    visual_desc += f"物体: {', '.join(object_tags[:3])} | 情绪: {mood_top} | 风格: {style_top}"

    print(f"  [视觉] 品类={category_top} 场景={scene_tags[:3]} 物体={object_tags[:3]} 情绪={mood_top}")

    return {
        "visual_embedding": visual_embedding.round(6).tolist(),
        "visual_desc": visual_desc,
        "scene_tags": enriched_scene[:20],
        "object_tags": object_tags[:15],
        "category": category_top,
        "mood": mood_top,
        "style": style_top,
        "quality_label": quality_label,
        # 原始分类概率, 用于质量评分
        "_cat_probs": {l: round(float(p), 4) for l, p in zip(CATEGORY_LABELS, cat_probs)},
        "_frame_diversity": float(np.mean(np.std(visual_array, axis=0))),
    }


# ===================== 音频分析 (librosa) =====================

def analyze_audio(audio_path: str) -> dict:
    if not audio_path or not os.path.exists(audio_path):
        return _empty_audio_features()

    try:
        y, sr = librosa.load(audio_path, sr=AUDIO_SR, duration=AUDIO_DURATION, mono=True)
        if len(y) < sr * 0.5:
            return _empty_audio_features()

        tempo, _ = librosa.beat.beat_track(y=y, sr=sr)
        bpm = float(tempo[0]) if hasattr(tempo, '__len__') else float(tempo)

        spectral = float(np.mean(librosa.feature.spectral_centroid(y=y, sr=sr)[0]))

        energy = float(np.mean(librosa.feature.rms(y=y)[0]))
        energy = min(1.0, energy * 10)

        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
        mfcc_mean = np.mean(mfcc, axis=1).tolist()

        chroma = librosa.feature.chroma_cqt(y=y, sr=sr)
        chroma_mean = np.mean(chroma, axis=1)
        key_idx = int(np.argmax(chroma_mean))
        key_names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
        key = key_names[key_idx]

        major_chroma = np.mean([chroma_mean[i] for i in [0, 4, 5, 9]])
        minor_chroma = np.mean([chroma_mean[i] for i in [0, 3, 5, 8]])
        valence = float(major_chroma / (minor_chroma + 1e-6))
        valence = min(1.0, max(0.0, valence / (valence + 1.0)))

        features = {
            "music_bpm": round(bpm, 1),
            "music_key": key,
            "music_energy": round(energy, 3),
            "music_valence": round(valence, 3),
            "music_spectral": round(spectral, 1),
            "music_mfcc": [round(v, 5) for v in mfcc_mean],
        }
        print(f"  [音频] BPM={bpm:.0f} Key={key} Energy={energy:.2f} Valence={valence:.2f}")
        return features

    except Exception as e:
        print(f"  [音频] 分析失败: {e}", file=sys.stderr)
        return _empty_audio_features()


def _empty_audio_features() -> dict:
    return {
        "music_bpm": 0, "music_key": "", "music_energy": 0,
        "music_valence": 0, "music_spectral": 0, "music_mfcc": [0] * 13
    }


# ===================== 文本分析 (jieba + BGE-M3) =====================

def analyze_text(visual_desc: str, clip_category: str, clip_tags: list[str],
                 original_desc: str = "", music_title: str = "") -> dict:
    """jieba 关键词 + BGE-M3 深度语义向量"""
    print(f"  [文本] 加载 BGE-M3...")
    model = _get_text_embedder()

    # 拼接所有文本
    tags_str = " ".join(clip_tags) if clip_tags else ""
    combined = f"{original_desc} {music_title} {clip_category} {tags_str} {visual_desc}"
    if not combined.strip():
        return _empty_text_features()

    # jieba 关键词
    words = jieba.cut(combined)
    word_freq = {}
    for w in words:
        w = w.strip()
        if len(w) < 2 or w in _stop_words():
            continue
        word_freq[w] = word_freq.get(w, 0) + 1

    top_words = sorted(word_freq.items(), key=lambda x: -x[1])[:15]
    keywords = [w for w, _ in top_words]

    # BGE-M3 文本向量 (深度语义, 与 CLIP 互补)
    embedding = model.encode(combined[:1024], normalize_embeddings=True).tolist()

    # 品类: 优先使用 CLIP 零样本结果 (视觉比文本规则更准)
    category = clip_category if clip_category and clip_category != "综合" else _classify_text(combined, keywords)

    print(f"  [文本] 品类={category} 关键词={keywords[:8]}")

    return {
        "keywords": keywords,
        "text_category": category,
        "text_embedding": embedding
    }


def _classify_text(text: str, keywords: list[str]) -> str:
    """规则兜底分类 (CLIP 分类失效时启用)"""
    text_lower = text.lower()
    kw_set = set(k.lower() for k in keywords)

    rules = {
        "美食": ["美食", "吃", "菜", "做饭", "烹饪", "厨房", "餐厅", "火锅", "烤肉", "甜品",
                 "红烧", "家常", "探店", "食材", "食谱", "炒", "炖", "煮", "烤"],
        "舞蹈": ["舞蹈", "跳舞", "舞", "编舞", "街舞", "芭蕾", "舞蹈室"],
        "搞笑": ["搞笑", "笑", "段子", "鬼畜", "整蛊", "沙雕", "幽默", "调侃", "恶搞"],
        "知识科普": ["教程", "教学", "技巧", "知识", "科普", "学习", "干货", "经验", "方法", "入门", "进阶", "详解"],
        "旅行风景": ["旅行", "旅游", "景点", "风景", "户外", "打卡", "自驾", "徒步"],
        "音乐演唱": ["音乐", "歌唱", "弹奏", "乐器", "BGM", "吉他", "钢琴", "演唱", "合唱"],
        "时尚美妆": ["穿搭", "时尚", "美妆", "护肤", "发型", "搭配", "衣服", "口红"],
        "体育运动": ["运动", "健身", "跑步", "篮球", "足球", "游泳", "瑜伽"],
        "数码科技": ["科技", "数码", "手机", "电脑", "AI", "智能", "编程"],
        "萌宠动物": ["猫", "狗", "宠物", "萌宠", "狗狗", "猫咪"],
        "影视娱乐": ["电影", "电视剧", "综艺", "剧情", "演员", "剪辑", "花絮"],
        "日常Vlog": ["日常", "生活", "Vlog", "vlog", "记录", "碎片", "随拍"],
        "游戏电竞": ["游戏", "电竞", "LOL", "王者", "吃鸡", "原神"],
        "汽车机车": ["汽车", "机车", "摩托车", "改装", "试驾"],
        "乡村三农": ["农村", "三农", "种地", "养殖", "田园", "乡村"],
    }

    scores = {}
    for cat, cat_words in rules.items():
        score = 0
        for w in cat_words:
            if w in text_lower:
                score += 1
            if w in kw_set:
                score += 2
        if score > 0:
            scores[cat] = score

    if scores:
        return max(scores, key=scores.get)
    return "综合"


def _get_text_embedder():
    if not hasattr(_get_text_embedder, "_model"):
        _get_text_embedder._model = SentenceTransformer(TEXT_EMBED_MODEL, device=DEVICE)
    return _get_text_embedder._model


def _empty_text_features() -> dict:
    return {"keywords": [], "text_category": "综合", "text_embedding": [0] * 1024}


def _stop_words() -> set:
    return {
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "还",
        "可以", "这个", "那个", "什么", "怎么", "因为", "所以", "但是", "虽然",
        "如果", "然后", "这样", "那样", "觉得", "知道", "喜欢", "比较",
        "一下", "一点", "一些", "很多", "非常", "真的", "确实", "有点",
        "视频", "大家", "感觉", "可能", "应该", "已经", "之后", "之前",
        "哈", "哈哈哈", "笑", "啦", "吧", "呢", "吗", "哦", "嗯", "啊"
    }


# ===================== 向量融合 =====================

def fuse_content_vector(
    visual_embedding: list[float],   # CLIP 视觉 (1024-dim, CLIP 空间)
    clip_text_embedding: list[float], # CLIP 文本 (1024-dim, 同一空间)
    mfcc: list[float],
    bpm: float,
    energy: float,
    valence: float
) -> list[float]:
    """
    高质量融合:
    1. CLIP 视觉 + CLIP 文本 → 加权平均 (同空间可直接融合)
    2. 取前 496 维 + 音频 16 维 → 512 维 L2 归一化
    """
    vis = np.array(visual_embedding, dtype=np.float64)
    txt = np.array(clip_text_embedding, dtype=np.float64)

    # CLIP 空间内加权融合
    if len(txt) == 0 or np.allclose(txt, 0):
        combined = vis
    else:
        combined = CLIP_VISUAL_WEIGHT * vis + CLIP_TEXT_WEIGHT * txt
        combined = combined / (np.linalg.norm(combined) + 1e-8)

    # 取前 496 维
    clip_part = combined[:496] if len(combined) >= 496 else np.pad(combined, (0, 496 - len(combined)))

    # 音频特征
    bpm_norm = min(1.0, (bpm or 0) / 200.0)
    audio_part = list(mfcc)[:13] + [bpm_norm, energy or 0, valence or 0]
    audio_part = audio_part + [0.0] * (16 - len(audio_part))

    fused = np.concatenate([clip_part, audio_part])
    norm = np.linalg.norm(fused)
    if norm > 0:
        fused = fused / norm
    return fused[:CONTENT_VECTOR_DIM].tolist()


# ===================== 质量评分 =====================

def estimate_quality(visual_result: dict, audio_features: dict, text_features: dict) -> float:
    """多维度质量评分 0-1"""
    score = 0.5

    # 1. 画质 (CLIP 零样本画质评估)
    quality_label = visual_result.get("quality_label", "")
    if "精良" in quality_label:
        score += 0.15
    elif "清晰" in quality_label:
        score += 0.08

    # 2. 帧间多样性 (静态画面 = 低质量)
    frame_div = visual_result.get("_frame_diversity", 0)
    if frame_div > 0.15:
        score += 0.10
    elif frame_div > 0.08:
        score += 0.05

    # 3. 分类置信度
    cat_probs = visual_result.get("_cat_probs", {})
    if cat_probs:
        top_conf = max(cat_probs.values())
        if top_conf > 0.5:
            score += 0.10
        elif top_conf > 0.3:
            score += 0.05

    # 4. 内容丰富度
    scene_count = len(visual_result.get("scene_tags", []))
    object_count = len(visual_result.get("object_tags", []))
    if scene_count >= 3:
        score += 0.05
    if object_count >= 2:
        score += 0.05

    # 5. 音频
    if audio_features.get("music_bpm", 0) > 0:
        score += 0.05
    if len(text_features.get("keywords", [])) >= 3:
        score += 0.05

    return round(min(1.0, score), 3)


# ===================== 主流程 =====================

def process_video(video_url: str, video_id: int, api_base: str = "http://localhost:9191") -> dict:
    t_start = time.time()
    features = {"video_id": video_id}

    with tempfile.TemporaryDirectory() as tmpdir:
        video_path = os.path.join(tmpdir, "video.mp4")
        frames_dir = os.path.join(tmpdir, "frames")
        audio_path = os.path.join(tmpdir, "audio.wav")
        os.makedirs(frames_dir, exist_ok=True)

        try:
            # 1. 下载
            download_video(video_url, video_path)

            # 2. 抽帧 + CLIP 视觉分析
            frame_paths = extract_frames(video_path, frames_dir)
            visual = analyze_frames_clip(frame_paths)
            features.update({k: v for k, v in visual.items() if not k.startswith("_")})

            # 3. 音频分析
            audio_file = extract_audio(video_path, audio_path)
            audio_feats = analyze_audio(audio_file)
            features.update(audio_feats)

            # 4. 文本分析 (jieba + BGE-M3)
            text_feats = analyze_text(
                visual_desc=visual["visual_desc"],
                clip_category=visual["category"],
                clip_tags=visual.get("scene_tags", []) + visual.get("object_tags", []),
                original_desc=os.environ.get("VIDEO_DESC", ""),
                music_title=os.environ.get("MUSIC_TITLE", "")
            )
            features.update(text_feats)

            # 5. CLIP 文本向量 (与视觉同一空间)
            clip_text_emb = _encode_clip_text(
                f"{os.environ.get('VIDEO_DESC', '')} {os.environ.get('MUSIC_TITLE', '')} {visual['visual_desc']}"
            )
            features["clip_text_embedding"] = clip_text_emb

            # 6. 融合内容向量
            content_vector = fuse_content_vector(
                visual_embedding=visual["visual_embedding"],
                clip_text_embedding=clip_text_emb,
                mfcc=audio_feats.get("music_mfcc", [0] * 13),
                bpm=audio_feats.get("music_bpm", 0),
                energy=audio_feats.get("music_energy", 0),
                valence=audio_feats.get("music_valence", 0)
            )
            features["content_vector"] = content_vector

            # 7. 质量评分
            features["quality_score"] = estimate_quality(visual, audio_feats, text_feats)

        except Exception as e:
            traceback.print_exc()
            features["extract_status"] = 2
            features["error"] = str(e)
            return features

    elapsed_ms = int((time.time() - t_start) * 1000)
    features["extract_status"] = 1
    features["extract_time_ms"] = elapsed_ms
    print(f"\n[完成] 视频 {video_id} 特征提取完成, 耗时 {elapsed_ms}ms")

    # 清理内部字段
    features.pop("_cat_probs", None)
    features.pop("_frame_diversity", None)
    features.pop("category", None)
    features.pop("mood", None)
    features.pop("style", None)
    features.pop("quality_label", None)
    features.pop("clip_text_embedding", None)

    # 8. 写回 Java 后端
    save_to_backend(features, api_base)
    return features


def _encode_clip_text(text: str) -> list[float]:
    """用 CLIP 文本编码器编码文本 (与视觉同一空间)"""
    if not text.strip():
        return [0.0] * CLIP_DIM
    try:
        model, processor = _get_clip()
        inputs = processor(text=[text[:512]], return_tensors="pt", padding=True, truncation=True).to(DEVICE)
        emb = _tensor_from_clip(model.get_text_features(**inputs))
        emb = emb / emb.norm(dim=-1, keepdim=True)
        return emb.cpu().numpy()[0].round(6).tolist()
    except Exception:
        return [0.0] * CLIP_DIM


def save_to_backend(features: dict, api_base: str):
    try:
        url = f"{api_base}/api/video/content-features"
        resp = httpx.post(url, json=features, timeout=60)
        if resp.status_code == 200:
            print(f"  [保存] 特征已写回后端")
        else:
            print(f"  [保存] 失败: HTTP {resp.status_code} {resp.text[:200]}", file=sys.stderr)
    except Exception as e:
        print(f"  [保存] 请求失败: {e}", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description="视频内容特征提取流水线 v2")
    parser.add_argument("--video-url", required=True, help="视频文件 URL")
    parser.add_argument("--video-id", required=True, type=int, help="视频 ID")
    parser.add_argument("--desc", default="", help="视频描述")
    parser.add_argument("--music-title", default="", help="音乐标题")
    parser.add_argument("--api-base", default="http://localhost:9191", help="Java 后端地址")
    args = parser.parse_args()

    os.environ["VIDEO_DESC"] = args.desc
    os.environ["MUSIC_TITLE"] = args.music_title
    # 国内 Hugging Face 镜像 (Java ProcessBuilder 已设置，这里做兜底)
    if not os.environ.get("HF_ENDPOINT"):
        os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

    ensure_ffmpeg()

    result = process_video(args.video_url, args.video_id, args.api_base)

    json_output = {k: v for k, v in result.items()
                   if k not in ("text_embedding", "content_vector", "visual_embedding",
                                "visual_desc", "music_mfcc")}
    json_output["text_embedding_size"] = len(result.get("text_embedding", []))
    json_output["content_vector_size"] = len(result.get("content_vector", []))
    json_output["visual_embedding_size"] = len(result.get("visual_embedding", []))
    print(f"\n[结果] {json.dumps(json_output, ensure_ascii=False, indent=2)}")


if __name__ == "__main__":
    main()
