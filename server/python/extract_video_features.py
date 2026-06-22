#!/usr/bin/env python3
"""
内容特征提取流水线 v3.0
增强特性 (vs v2.x):
  - Whisper ASR 语音转文字 → 提取对话关键词
  - Qwen2-VL-2B 自由画面描述 → 替代硬编码 CLIP 零样本标签
  - BGE-M3 开放词汇多标签分类 → 从 tag_vocabulary.json 动态匹配, 不再受固定标签限制
  - OpenCV 人脸检测 → 人脸数量/特写比例
  - 增强音频特征 → 新增 ZCR / 频谱滚降/带宽/onset
  - 增强质量评分 → 综合多模态信号

模型加载策略 (适配 RTX 4060 8GB):
  CLIP(1.5G,常驻) → Whisper(1.6G,用完释放) → Qwen2-VL(~4G,用完释放) → BGE-M3(CPU,用完释放)
  峰值显存 ~5.5GB

用法兼容 v2.x 全部 CLI 参数. 首次运行需下载模型 ~8GB.
"""

import os

# bitsandbytes CUDA DLL 与 torch 在 Windows 上冲突, 必须在 torch 导入前禁用
if not os.environ.get("BITSANDBYTES_NOWELCOME"):
    os.environ["BITSANDBYTES_NOWELCOME"] = "1"

import argparse
import gc
import json
import subprocess
import sys
import tempfile
import time
import traceback
from pathlib import Path

# torch 必须在 librosa 之前导入, 否则 llvmlite/numba CUDA DLL 冲突
import torch
import numpy as np
import jieba
import librosa
import cv2
from PIL import Image

# ============================================================================
#  配置
# ============================================================================

CLIP_MODEL = "OFA-Sys/chinese-clip-vit-large-patch14-336px"
TEXT_EMBED_MODEL = "BAAI/bge-m3"
WHISPER_MODEL = "large-v3-turbo"          # whisper 模型尺寸, 比 large-v3 省 1.5GB 几乎同质量
VLM_MODEL = "Qwen/Qwen2-VL-2B-Instruct"   # 画面自由描述

FRAME_COUNT = 16
FRAME_SIZE = 336
CLIP_DIM = 1024
AUDIO_SR = 22050
AUDIO_DURATION = 30
CONTENT_VECTOR_DIM = 512
CLIP_VISUAL_WEIGHT = 0.55
CLIP_TEXT_WEIGHT = 0.45

# VLM 描述用的关键帧数 (从16帧里选)
VLM_KEY_FRAMES = 4

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

# 标签词库路径 (相对于本文件)
_TAG_VOCAB_PATH = os.path.join(os.path.dirname(__file__), "tag_vocabulary.json")
_TAG_CACHE_PATH = os.path.join(os.path.dirname(__file__), "tag_vocabulary_cache.npy")

# ============================================================================
#  模型加载/卸载管理
# ============================================================================

_model_registry = {}


def _loaded(name):
    return name in _model_registry


def _put_model(name, model):
    _model_registry[name] = model


def _pop_model(name):
    """取出模型并释放显存"""
    model = _model_registry.pop(name, None)
    if model is not None:
        del model
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()


def _unload_all_gpu():
    """释放所有GPU模型"""
    for key in list(_model_registry.keys()):
        _pop_model(key)


# ============================================================================
#  标签词库 & 开放词汇匹配
# ============================================================================

def load_tag_vocabulary() -> dict:
    """加载标签词库 JSON"""
    if not os.path.exists(_TAG_VOCAB_PATH):
        return {}
    with open(_TAG_VOCAB_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def _get_tag_embeddings(tag_list: list[str]) -> np.ndarray:
    """计算标签的 BGE-M3 嵌入, 结果缓存到磁盘"""
    # 检查缓存
    if os.path.exists(_TAG_CACHE_PATH):
        cached = np.load(_TAG_CACHE_PATH)
        if cached.shape[0] == len(tag_list):
            return cached

    model = _get_bge_model()
    embeddings = model.encode(tag_list, normalize_embeddings=True, show_progress_bar=False)

    try:
        np.save(_TAG_CACHE_PATH, embeddings)
    except Exception:
        pass  # 缓存失败不阻塞

    return embeddings


def open_vocabulary_classify(text: str, tag_vocab: dict, top_k: int = 5) -> dict:
    """
    用 BGE-M3 做开放词汇语义匹配.
    text: 组合后的描述文本
    tag_vocab: 标签词库 dict
    返回: { dimension_name: [{label, confidence}, ...], ... }
    """
    if not text.strip() or not tag_vocab:
        return {}

    model = _get_bge_model()

    text_emb = model.encode([text], normalize_embeddings=True)[0]

    result = {}
    for dim_name in ["categories", "scenes", "objects", "moods", "styles",
                      "music_genres", "content_attributes", "quality_levels"]:
        tags = tag_vocab.get(dim_name, [])
        if not tags:
            continue
        tag_embs = _get_tag_embeddings(tags)
        sims = tag_embs @ text_emb  # cosine similarity (already normalized)
        top_idx = np.argsort(sims)[::-1][:top_k]
        result[dim_name] = [
            {"label": tags[i], "confidence": round(float(sims[i]), 4)}
            for i in top_idx if sims[i] > 0.15
        ]

    return result


# ============================================================================
#  CLIP 视觉分析
# ============================================================================

def _get_clip():
    if "clip" not in _model_registry:
        from transformers import ChineseCLIPModel, ChineseCLIPProcessor
        print(f"  [CLIP] 加载 {CLIP_MODEL} (device={DEVICE})...", file=sys.stderr)
        model = ChineseCLIPModel.from_pretrained(CLIP_MODEL).to(DEVICE).eval()
        processor = ChineseCLIPProcessor.from_pretrained(CLIP_MODEL)
        _put_model("clip", (model, processor))
        print(f"  [CLIP] 加载完成", file=sys.stderr)
    return _model_registry["clip"]


def _tensor_from_clip(output):
    if hasattr(output, 'pooler_output'):
        return output.pooler_output
    if isinstance(output, torch.Tensor):
        return output
    if hasattr(output, 'last_hidden_state'):
        return output.last_hidden_state[:, 0, :]
    raise TypeError(f"Unexpected CLIP output type: {type(output)}")


@torch.no_grad()
def analyze_images_clip(image_paths: list[str]) -> dict:
    """CLIP 视觉 embedding + 零样本分类 (保留用于兜底和视觉向量提取)"""
    model, processor = _get_clip()
    batch_size = 8
    all_visual_embeds = []

    for start in range(0, len(image_paths), batch_size):
        batch_paths = image_paths[start:start + batch_size]
        images = []
        for fp in batch_paths:
            try:
                images.append(Image.open(fp).convert("RGB"))
            except Exception:
                # 损坏的图片用黑图代替
                images.append(Image.new("RGB", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0)))
        inputs = processor(images=images, return_tensors="pt", padding=True).to(DEVICE)
        img_embeds = _tensor_from_clip(model.get_image_features(**inputs))
        img_embeds = img_embeds / img_embeds.norm(dim=-1, keepdim=True)
        all_visual_embeds.append(img_embeds.cpu().numpy())

    visual_array = np.concatenate(all_visual_embeds, axis=0)
    visual_embedding = visual_array.mean(axis=0)
    visual_embedding = visual_embedding / (np.linalg.norm(visual_embedding) + 1e-8)
    frame_diversity = float(np.mean(np.std(visual_array, axis=0)))

    return {
        "visual_embedding": visual_embedding.round(6).tolist(),
        "_visual_array": visual_array,
        "_frame_diversity": frame_diversity,
    }


# ============================================================================
#  Whisper ASR 语音转文字
# ============================================================================

def transcribe_audio(audio_path: str) -> dict:
    """Whisper 语音转录, 用完即释放模型"""
    if not audio_path or not os.path.exists(audio_path):
        return {"transcript": "", "asr_keywords": [], "has_speech": False, "language": ""}

    try:
        import whisper
        print(f"  [ASR] 加载 Whisper {WHISPER_MODEL}...", file=sys.stderr)
        model = whisper.load_model(WHISPER_MODEL).to(DEVICE)
        result = model.transcribe(
            audio_path,
            language="zh",
            initial_prompt="以下是普通话的句子。",
            verbose=False,
        )
        transcript = result.get("text", "").strip()
        detected_lang = result.get("language", "")

        # 释放 Whisper
        del model
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

        # 提取关键词
        asr_keywords = []
        has_speech = False
        if transcript and len(transcript) > 3:
            has_speech = True
            words = jieba.cut(transcript)
            word_freq = {}
            for w in words:
                w = w.strip()
                if len(w) < 2:
                    continue
                word_freq[w] = word_freq.get(w, 0) + 1
            top_words = sorted(word_freq.items(), key=lambda x: -x[1])[:20]
            asr_keywords = [w for w, _ in top_words]

        print(f"  [ASR] 转录完成: {transcript[:80]}...", file=sys.stderr)
        return {
            "transcript": transcript,
            "asr_keywords": asr_keywords,
            "has_speech": has_speech,
            "language": detected_lang,
        }

    except Exception as e:
        print(f"  [ASR] 转录失败: {e}", file=sys.stderr)
        return {"transcript": "", "asr_keywords": [], "has_speech": False, "language": ""}


# ============================================================================
#  Qwen2-VL 画面自由描述
# ============================================================================

def _select_key_frames(image_paths: list[str], n_key: int = VLM_KEY_FRAMES) -> list[str]:
    """从全部帧中均匀选取关键帧 + 首尾"""
    if len(image_paths) <= n_key:
        return image_paths
    idxs = [0]  # 首帧
    step = max(1, len(image_paths) // (n_key - 1))
    for i in range(step, len(image_paths) - 1, step):
        idxs.append(i)
    idxs.append(len(image_paths) - 1)  # 尾帧
    # 去重 + 限数
    idxs = sorted(set(idxs))[:n_key]
    return [image_paths[i] for i in idxs]


def describe_frames_vlm(image_paths: list[str]) -> dict:
    """
    Qwen2-VL-2B 对关键帧做自由画面描述, 用完释放模型.
    返回: { visual_description, vlm_scene, vlm_objects, vlm_actions }
    """
    key_frames = _select_key_frames(image_paths)
    if not key_frames:
        return {"visual_description": "", "vlm_tags": []}

    try:
        from transformers import Qwen2VLForConditionalGeneration, AutoProcessor
        from qwen_vl_utils import process_vision_info

        print(f"  [VLM] 加载 {VLM_MODEL}...", file=sys.stderr)
        model = Qwen2VLForConditionalGeneration.from_pretrained(
            VLM_MODEL, torch_dtype=torch.bfloat16, device_map="auto"
        )
        processor = AutoProcessor.from_pretrained(VLM_MODEL)

        prompt = (
            "请用一段中文详细描述这个画面的内容，重点说明："
            "场景在哪里、有没有人物、人物在做什么动作、"
            "画面中有哪些主要物体、整体氛围和情绪是什么。"
            "描述控制在100字以内。"
        )

        descriptions = []
        for fp in key_frames:
            try:
                img = Image.open(fp).convert("RGB")
                messages = [
                    {"role": "user", "content": [
                        {"type": "image", "image": img},
                        {"type": "text", "text": prompt},
                    ]}
                ]
                text = processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
                image_inputs, _ = process_vision_info(messages)
                inputs = processor(text=[text], images=image_inputs, return_tensors="pt").to(model.device)

                generated_ids = model.generate(**inputs, max_new_tokens=256, do_sample=False)
                generated_ids = [gid[len(inputs.input_ids[0]):] for gid in generated_ids]
                desc = processor.batch_decode(generated_ids, skip_special_tokens=True)[0].strip()
                if desc:
                    descriptions.append(desc)
            except Exception as e:
                print(f"  [VLM] 单帧描述失败: {e}", file=sys.stderr)

        # 释放 VLM
        del model
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

        combined = " | ".join(descriptions) if descriptions else ""
        print(f"  [VLM] {len(descriptions)}/{len(key_frames)} 帧描述完成", file=sys.stderr)

        return {
            "visual_description": combined,
            "vlm_tags": _extract_vlm_keywords(combined),
        }

    except Exception as e:
        print(f"  [VLM] 模型加载失败: {e}", file=sys.stderr)
        return {"visual_description": "", "vlm_tags": []}


def _extract_vlm_keywords(desc: str) -> list[str]:
    """从 VLM 描述中提取短标签"""
    if not desc:
        return []
    words = jieba.cut(desc)
    word_freq = {}
    for w in words:
        w = w.strip()
        if len(w) < 2:
            continue
        word_freq[w] = word_freq.get(w, 0) + 1
    # 取高频 + 长词 (长词通常信息量更大)
    scored = sorted(word_freq.items(), key=lambda x: (-x[1], -len(x[0])))
    return [w for w, _ in scored[:12]]


# ============================================================================
#  人脸检测 (OpenCV)
# ============================================================================

def _get_face_detector():
    if "face_detector" not in _model_registry:
        # 使用 OpenCV DNN 人脸检测器
        model_file = os.path.join(cv2.data.haarcascades, "haarcascade_frontalface_default.xml")
        if not os.path.exists(model_file):
            model_file = os.path.join(cv2.data.haarcascades, "haarcascade_frontalface_alt.xml")
        cascade = cv2.CascadeClassifier(model_file)
        _put_model("face_detector", cascade)
    return _model_registry["face_detector"]


def detect_faces(image_paths: list[str]) -> dict:
    """检测关键帧中的人脸, 返回人脸数量和面部占比"""
    cascade = _get_face_detector()
    key_frames = _select_key_frames(image_paths, n_key=6)

    total_faces = 0
    frame_face_ratios = []
    frames_with_faces = 0

    for fp in key_frames:
        try:
            img = cv2.imread(fp)
            if img is None:
                continue
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            faces = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
            n = len(faces)
            total_faces += n
            if n > 0:
                frames_with_faces += 1
                # 面部占比 = 人脸框面积总和 / 图片面积
                img_area = gray.shape[0] * gray.shape[1]
                face_area = sum(w * h for (x, y, w, h) in faces)
                frame_face_ratios.append(min(1.0, face_area / img_area))
        except Exception:
            continue

    avg_faces = total_faces / max(1, len(key_frames))
    avg_ratio = np.mean(frame_face_ratios) if frame_face_ratios else 0.0
    has_faces = frames_with_faces > 0

    is_closeup = avg_ratio > 0.15  # 面部占画面 15% 以上视为特写
    is_single_person = has_faces and avg_faces < 1.8 and frames_with_faces >= len(key_frames) * 0.6
    is_multi_person = avg_faces >= 2.5

    print(f"  [人脸] 平均{avg_faces:.1f}张/帧 占比{avg_ratio:.3f} 特写={is_closeup}", file=sys.stderr)

    return {
        "face_count": round(avg_faces, 1),
        "face_ratio": round(avg_ratio, 4),
        "has_faces": has_faces,
        "is_closeup": is_closeup,
        "is_single_person": is_single_person,
        "is_multi_person": is_multi_person,
    }


# ============================================================================
#  增强音频分析
# ============================================================================

def analyze_audio_enhanced(audio_path: str) -> dict:
    """
    增强音频特征 (librosa):
      BPM, Key, Energy, Valence, Spectral Centroid/Rolloff/Bandwidth,
      ZCR, MFCC, Onset Rate
    """
    if not audio_path or not os.path.exists(audio_path):
        return _empty_audio_features()

    try:
        y, sr = librosa.load(audio_path, sr=AUDIO_SR, duration=AUDIO_DURATION, mono=True)
        if len(y) < sr * 0.5:
            return _empty_audio_features()

        # BPM
        tempo, _ = librosa.beat.beat_track(y=y, sr=sr)
        bpm = float(tempo[0]) if hasattr(tempo, '__len__') else float(tempo)

        # Spectral features
        spectral_centroid = float(np.mean(librosa.feature.spectral_centroid(y=y, sr=sr)[0]))
        spectral_rolloff = float(np.mean(librosa.feature.spectral_rolloff(y=y, sr=sr)[0]))
        spectral_bandwidth = float(np.mean(librosa.feature.spectral_bandwidth(y=y, sr=sr)[0]))

        # Energy (RMS)
        energy = float(np.mean(librosa.feature.rms(y=y)[0]))
        energy = min(1.0, energy * 10)

        # Zero-crossing rate (区分语音 vs 音乐)
        zcr = float(np.mean(librosa.feature.zero_crossing_rate(y)[0]))

        # MFCC
        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
        mfcc_mean = np.mean(mfcc, axis=1).tolist()

        # Chroma → Key & Valence
        chroma = librosa.feature.chroma_cqt(y=y, sr=sr)
        chroma_mean = np.mean(chroma, axis=1)
        key_idx = int(np.argmax(chroma_mean))
        key_names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
        key = key_names[key_idx]

        major_chroma = np.mean([chroma_mean[i] for i in [0, 4, 5, 9]])
        minor_chroma = np.mean([chroma_mean[i] for i in [0, 3, 5, 8]])
        valence = float(major_chroma / (minor_chroma + 1e-6))
        valence = min(1.0, max(0.0, valence / (valence + 1.0)))

        # Onset rate (节奏复杂度)
        onset_env = librosa.onset.onset_strength(y=y, sr=sr)
        onset_rate = float(np.mean(onset_env > np.mean(onset_env) * 1.5))

        # 粗略判断是否有音乐 / 有语音
        has_music = bpm > 0 and spectral_centroid > 800
        has_speech_indicator = zcr > 0.05 and energy > 0.1

        features = {
            "music_bpm": round(bpm, 1),
            "music_key": key,
            "music_energy": round(energy, 3),
            "music_valence": round(valence, 3),
            "music_spectral": round(spectral_centroid, 1),
            "music_rolloff": round(spectral_rolloff, 1),
            "music_bandwidth": round(spectral_bandwidth, 1),
            "music_zcr": round(zcr, 4),
            "music_onset_rate": round(onset_rate, 4),
            "music_mfcc": [round(v, 5) for v in mfcc_mean],
            "audio_has_music": has_music,
            "audio_has_speech_indicator": has_speech_indicator,
        }
        print(f"  [音频] BPM={bpm:.0f} Key={key} Energy={energy:.2f} "
              f"OnsetRate={onset_rate:.2f} HasMusic={has_music}", file=sys.stderr)
        return features

    except Exception as e:
        print(f"  [音频] 分析失败: {e}", file=sys.stderr)
        return _empty_audio_features()


def _empty_audio_features() -> dict:
    return {
        "music_bpm": 0, "music_key": "", "music_energy": 0,
        "music_valence": 0, "music_spectral": 0,
        "music_rolloff": 0, "music_bandwidth": 0, "music_zcr": 0, "music_onset_rate": 0,
        "music_mfcc": [0] * 13,
        "audio_has_music": False, "audio_has_speech_indicator": False,
    }


# ============================================================================
#  文本分析 (jieba + BGE-M3 + 开放词汇分类)
# ============================================================================

_bge_model_cache = None  # 模块级 BGE-M3 缓存，避免每次调用都从磁盘加载

def _get_bge_model():
    """获取缓存的 BGE-M3 模型，首次调用时加载"""
    global _bge_model_cache
    if _bge_model_cache is None:
        from sentence_transformers import SentenceTransformer
        print(f"  [模型] 加载 BGE-M3 ({TEXT_EMBED_MODEL}) 到 {DEVICE}...", file=sys.stderr)
        _bge_model_cache = SentenceTransformer(TEXT_EMBED_MODEL, device=DEVICE)
    return _bge_model_cache

def analyze_text_enhanced(
    visual_desc: str = "",
    vlm_desc: str = "",
    asr_transcript: str = "",
    original_desc: str = "",
    music_title: str = "",
) -> dict:
    """
    文本语义分析 v3:
      1. jieba 关键词提取 (综合所有文本源)
      2. BGE-M3 深度语义向量
      3. 开放词汇多标签分类 (匹配 tag_vocabulary.json)
    """
    print(f"  [文本] 加载 BGE-M3...", file=sys.stderr)

    # 组合全部文本源
    parts = []
    if original_desc:
        parts.append(original_desc)
    if music_title:
        parts.append(f"[音乐]{music_title}")
    if vlm_desc:
        parts.append(vlm_desc)
    if visual_desc:
        parts.append(visual_desc)
    if asr_transcript:
        parts.append(f"[语音]{asr_transcript}")
    combined = " ".join(parts)

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

    top_words = sorted(word_freq.items(), key=lambda x: -x[1])[:20]
    keywords = [w for w, _ in top_words]

    # BGE-M3 embedding (复用缓存模型)
    model = _get_bge_model()
    embedding = model.encode(combined[:1536], normalize_embeddings=True).tolist()

    # 开放词汇分类
    tag_vocab = load_tag_vocabulary()
    open_classification = open_vocabulary_classify(combined[:2048], tag_vocab)

    # 提取简化分类结果
    categories = open_classification.get("categories", [])
    scenes = [x["label"] for x in open_classification.get("scenes", [])[:5]]
    objects = [x["label"] for x in open_classification.get("objects", [])[:5]]
    moods = open_classification.get("moods", [])
    styles = open_classification.get("styles", [])
    music_genres = open_classification.get("music_genres", [])
    quality = open_classification.get("quality_levels", [])
    content_attrs = open_classification.get("content_attributes", [])

    text_category = categories[0]["label"] if categories else "综合"
    mood_top = moods[0]["label"] if moods else ""
    style_top = styles[0]["label"] if styles else ""
    quality_label = quality[0]["label"] if quality else ""
    music_genre_top = music_genres[0]["label"] if music_genres else ""

    print(f"  [文本] 分类={text_category} 关键词={keywords[:8]}", file=sys.stderr)

    return {
        "keywords": keywords,
        "text_category": text_category,
        "text_embedding": embedding,
        # 开放词汇多标签
        "categories": categories,
        "scene_tags": scenes,
        "object_tags": objects,
        "mood": mood_top,
        "style": style_top,
        "quality_label": quality_label,
        "music_genre": music_genre_top,
        "content_attributes": [x["label"] for x in content_attrs[:8]],
        "_open_classification": open_classification,
    }


def _empty_text_features() -> dict:
    return {
        "keywords": [], "text_category": "综合", "text_embedding": [0] * 1024,
        "categories": [], "scene_tags": [], "object_tags": [],
        "mood": "", "style": "", "quality_label": "", "music_genre": "",
        "content_attributes": [], "_open_classification": {},
    }


def _stop_words() -> set:
    return {
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "还",
        "可以", "这个", "那个", "什么", "怎么", "因为", "所以", "但是", "虽然",
        "如果", "然后", "这样", "那样", "觉得", "知道", "喜欢", "比较",
        "一下", "一点", "一些", "很多", "非常", "真的", "确实", "有点",
        "视频", "大家", "感觉", "可能", "应该", "已经", "之后", "之前",
        "哈", "哈哈哈", "笑", "啦", "吧", "呢", "吗", "哦", "嗯", "啊",
    }


# ============================================================================
#  CLIP 文本编码
# ============================================================================

def _encode_clip_text(text: str) -> list[float]:
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


# ============================================================================
#  向量融合
# ============================================================================

def fuse_content_vector(
    visual_embedding: list[float] = None,
    clip_text_embedding: list[float] = None,
    bge_text_embedding: list[float] = None,
    mfcc: list[float] = None,
    bpm: float = 0,
    energy: float = 0,
    valence: float = 0,
    onset_rate: float = 0,
    has_visual: bool = True,
    has_audio: bool = True,
) -> list[float]:
    """融合视觉 + 文本 + 增强音频 → 512 维"""
    # 视觉/文本部分 (480 维)
    if has_visual and visual_embedding and clip_text_embedding:
        vis = np.array(visual_embedding, dtype=np.float64)
        txt = np.array(clip_text_embedding, dtype=np.float64)
        if len(txt) == 0 or np.allclose(txt, 0):
            combined = vis
        else:
            combined = CLIP_VISUAL_WEIGHT * vis + CLIP_TEXT_WEIGHT * txt
            combined = combined / (np.linalg.norm(combined) + 1e-8)
        clip_part = combined[:480] if len(combined) >= 480 else np.pad(combined, (0, 480 - len(combined)))
    elif bge_text_embedding:
        bge = np.array(bge_text_embedding, dtype=np.float64)
        bge = bge / (np.linalg.norm(bge) + 1e-8)
        clip_part = bge[:480] if len(bge) >= 480 else np.pad(bge, (0, 480 - len(bge)))
    else:
        clip_part = np.zeros(480, dtype=np.float64)

    # 音频部分 (32 维, 从 16 维扩展)
    if has_audio and mfcc:
        bpm_norm = min(1.0, (bpm or 0) / 200.0)
        onset_norm = min(1.0, (onset_rate or 0) * 5)
        audio_part = list(mfcc)[:13] + [
            bpm_norm, energy or 0, valence or 0,
            onset_norm,
            # 保留填充
            0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0,
        ]
        audio_part = audio_part[:32]
    else:
        audio_part = [0.0] * 32

    audio_arr = np.array(audio_part, dtype=np.float64)
    fused = np.concatenate([clip_part, audio_arr])
    norm = np.linalg.norm(fused)
    if norm > 0:
        fused = fused / norm
    return fused[:CONTENT_VECTOR_DIM].tolist()


# ============================================================================
#  增强质量评分
# ============================================================================

def estimate_quality_enhanced(
    visual_result: dict,
    audio_features: dict,
    text_features: dict,
    face_features: dict,
    asr_result: dict,
    has_visual: bool = True,
    has_audio: bool = True,
) -> float:
    """多维度质量评分 0-1, v3 增强版"""
    score = 0.5

    # 视觉维度
    if has_visual:
        quality_label = text_features.get("quality_label", "")
        if "精良" in quality_label:
            score += 0.12
        elif "清晰" in quality_label:
            score += 0.06

        frame_div = visual_result.get("_frame_diversity", 0)
        if frame_div > 0.15:
            score += 0.08
        elif frame_div > 0.08:
            score += 0.04

        categories = text_features.get("categories", [])
        if categories:
            top_conf = categories[0].get("confidence", 0)
            if top_conf > 0.6:
                score += 0.08
            elif top_conf > 0.4:
                score += 0.04

        scene_count = len(text_features.get("scene_tags", []))
        object_count = len(text_features.get("object_tags", []))
        if scene_count >= 3:
            score += 0.04
        if object_count >= 2:
            score += 0.04

    # 语音维度
    if has_audio:
        if audio_features.get("music_bpm", 0) > 0:
            score += 0.03
        if asr_result.get("has_speech", False):
            score += 0.05
        # 节奏丰富度
        if audio_features.get("music_onset_rate", 0) > 0.05:
            score += 0.02

    # 文本维度
    if len(text_features.get("keywords", [])) >= 3:
        score += 0.04
    if len(text_features.get("content_attributes", [])) >= 3:
        score += 0.03

    # 人脸维度
    if face_features.get("has_faces", False):
        score += 0.02
    if face_features.get("is_single_person", False):
        score += 0.02  # 单人出镜通常内容更聚焦

    return round(min(1.0, score), 3)


# ============================================================================
#  工具函数: 下载 / 抽帧 / 音频提取
# ============================================================================

def ensure_ffmpeg():
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("[ERROR] ffmpeg 未安装或不在 PATH 中", file=sys.stderr)
        sys.exit(1)


def download_url(url: str, dest: str, api_base: str = None, retries: int = 5):
    if "://" not in url and api_base:
        url = f"{api_base}/api/file/url?path={url}"
    print(f"  [下载] {url[:100]}...", file=sys.stderr)
    import urllib.request
    last_err = None
    for attempt in range(retries):
        try:
            urllib.request.urlretrieve(url, dest)
            size_mb = os.path.getsize(dest) / 1024 / 1024
            print(f"  [下载] 完成, {size_mb:.2f} MB", file=sys.stderr)
            return
        except Exception as e:
            last_err = e
            if attempt < retries - 1:
                delay = 2 ** attempt
                print(f"  [下载] 重试 {attempt + 1}/{retries}, {delay}s 后重试: {e}", file=sys.stderr)
                time.sleep(delay)
    raise RuntimeError(f"下载失败 (已重试{retries}次): {last_err}")


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


def extract_frames(video_path: str, output_dir: str, count: int = FRAME_COUNT,
                   size: int = FRAME_SIZE) -> list[str]:
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
    print(f"  [抽帧] 共 {len(frames)} 帧", file=sys.stderr)
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


def download_images(image_urls: list[str], dest_dir: str, api_base: str = None) -> list[str]:
    paths = []
    for i, url in enumerate(image_urls):
        # 清洗 Windows shell 传参可能残留的引号/括号
        url = url.strip().strip('"').strip("'").lstrip("[").rstrip("]").strip()
        ext = ".jpg"
        if url.lower().endswith(".png"):
            ext = ".png"
        elif url.lower().endswith(".webp"):
            ext = ".webp"
        dest = os.path.join(dest_dir, f"image_{i:02d}{ext}")
        download_url(url, dest, api_base)
        paths.append(dest)
    return paths


# ============================================================================
#  主流程: Video / Image / Text
# ============================================================================

def process_video(video_url: str, video_id: int, desc: str = "", music_title: str = "",
                  api_base: str = "http://localhost:9191") -> dict:
    t_start = time.time()
    features = {"video_id": video_id}

    with tempfile.TemporaryDirectory() as tmpdir:
        video_path = os.path.join(tmpdir, "video.mp4")
        frames_dir = os.path.join(tmpdir, "frames")
        audio_path = os.path.join(tmpdir, "audio.wav")
        os.makedirs(frames_dir, exist_ok=True)

        try:
            # --- Step 1: 下载 + 抽帧 + 提音频 ---
            download_url(video_url, video_path, api_base)
            # 用 ffprobe 获取真实时长 (纠正入库时可能的错误时长)
            features["actual_duration"] = round(_get_video_duration(video_path), 2)
            frame_paths = extract_frames(video_path, frames_dir)
            audio_file = extract_audio(video_path, audio_path)

            # --- Step 2: CLIP 视觉 embedding (常驻GPU) ---
            visual = analyze_images_clip(frame_paths)
            features["visual_embedding"] = visual["visual_embedding"]

            # --- Step 3: Whisper ASR (加载→转录→释放) ---
            asr = transcribe_audio(audio_file)
            features["transcript"] = asr["transcript"]
            features["asr_keywords"] = asr["asr_keywords"]
            features["has_speech"] = asr["has_speech"]
            features["language"] = asr["language"]

            # --- Step 4: Qwen2-VL 画面描述 (加载→描述→释放) ---
            vlm = describe_frames_vlm(frame_paths)
            visual_desc = vlm.get("visual_description", "")

            # 兜底: VLM 失败时用传统方式生成 visual_desc
            if not visual_desc:
                visual_desc = f"视频内容, 包含 {len(frame_paths)} 帧画面"

            features["visual_description"] = visual_desc
            features["visual_desc"] = visual_desc  # 兼容 v2.x 字段名
            features["vlm_tags"] = vlm.get("vlm_tags", [])

            # --- Step 5: 人脸检测 (CPU, OpenCV) ---
            face = detect_faces(frame_paths)
            features.update(face)

            # --- Step 6: 增强音频分析 (CPU, librosa) ---
            audio_feats = analyze_audio_enhanced(audio_file)
            features.update(audio_feats)

            # --- Step 7: 文本分析 + 开放词汇分类 (BGE-M3) ---
            text_feats = analyze_text_enhanced(
                visual_desc="",
                vlm_desc=visual_desc,
                asr_transcript=asr.get("transcript", ""),
                original_desc=desc,
                music_title=music_title,
            )
            # 合并文本特征
            for k, v in text_feats.items():
                if not k.startswith("_"):
                    features[k] = v

            # --- Step 8: CLIP 文本编码 ---
            clip_text_emb = _encode_clip_text(
                f"{desc} {music_title} {visual_desc}"
            )
            features["clip_text_embedding"] = clip_text_emb  # 临时, cleanup 时删除

            # --- Step 9: 融合向量 ---
            content_vector = fuse_content_vector(
                visual_embedding=visual["visual_embedding"],
                clip_text_embedding=clip_text_emb,
                mfcc=audio_feats.get("music_mfcc", [0] * 13),
                bpm=audio_feats.get("music_bpm", 0),
                energy=audio_feats.get("music_energy", 0),
                valence=audio_feats.get("music_valence", 0),
                onset_rate=audio_feats.get("music_onset_rate", 0),
                has_visual=True,
                has_audio=True,
            )
            features["content_vector"] = content_vector

            # --- Step 10: 质量评分 ---
            features["quality_score"] = estimate_quality_enhanced(
                visual, audio_feats, text_feats, face, asr,
                has_visual=True, has_audio=True,
            )

        except Exception as e:
            traceback.print_exc()
            features["extract_status"] = 2
            features["error"] = str(e)
            _unload_all_gpu()
            return features

    # --- 释放 CLIP ---
    _unload_all_gpu()

    elapsed_ms = int((time.time() - t_start) * 1000)
    features["extract_status"] = 1
    features["extract_time_ms"] = elapsed_ms
    print(f"\n[完成] 视频 {video_id} 特征提取完成, 耗时 {elapsed_ms}ms", file=sys.stderr)

    _cleanup_internal(features)
    return features


def process_image(image_urls: list[str], video_id: int, desc: str = "",
                  api_base: str = "http://localhost:9191") -> dict:
    """图文模式: 下载图片 → CLIP视觉 + 文本分析 → 融合 (无音频/VLM/ASR)"""
    t_start = time.time()
    features = {"video_id": video_id}

    with tempfile.TemporaryDirectory() as tmpdir:
        try:
            image_paths = download_images(image_urls, tmpdir, api_base)
            visual = analyze_images_clip(image_paths)
            features["visual_embedding"] = visual["visual_embedding"]
            features["visual_description"] = ""
            features["visual_desc"] = ""

            # 无音频
            audio_feats = _empty_audio_features()
            features.update(audio_feats)
            features["transcript"] = ""
            features["asr_keywords"] = []
            features["has_speech"] = False
            features["language"] = ""

            # 人脸
            face = detect_faces(image_paths)
            features.update(face)

            # 文本分析
            text_feats = analyze_text_enhanced(
                visual_desc="",
                vlm_desc="",
                asr_transcript="",
                original_desc=desc,
                music_title="",
            )
            for k, v in text_feats.items():
                if not k.startswith("_"):
                    features[k] = v

            clip_text_emb = _encode_clip_text(f"{desc}")
            features["clip_text_embedding"] = clip_text_emb

            content_vector = fuse_content_vector(
                visual_embedding=visual["visual_embedding"],
                clip_text_embedding=clip_text_emb,
                has_visual=True,
                has_audio=False,
            )
            features["content_vector"] = content_vector

            features["quality_score"] = estimate_quality_enhanced(
                visual, audio_feats, text_feats, face,
                {"transcript": "", "has_speech": False},
                has_visual=True, has_audio=False,
            )

        except Exception as e:
            traceback.print_exc()
            features["extract_status"] = 2
            features["error"] = str(e)
            _unload_all_gpu()
            return features

    _unload_all_gpu()
    elapsed_ms = int((time.time() - t_start) * 1000)
    features["extract_status"] = 1
    features["extract_time_ms"] = elapsed_ms
    print(f"\n[完成] 图文 {video_id} 特征提取完成, 耗时 {elapsed_ms}ms", file=sys.stderr)

    _cleanup_internal(features)
    return features


def process_text_only(video_id: int, desc: str, api_base: str = "http://localhost:9191") -> dict:
    """纯文本模式: jieba关键词 + BGE-M3语义向量 + 开放词汇分类 → 512d"""
    t_start = time.time()
    features = {"video_id": video_id}

    try:
        features["visual_embedding"] = [0.0] * CLIP_DIM
        features["visual_description"] = ""
        features["visual_desc"] = ""
        features["vlm_tags"] = []

        audio_feats = _empty_audio_features()
        features.update(audio_feats)
        features["transcript"] = ""
        features["asr_keywords"] = []
        features["has_speech"] = False
        features["language"] = ""

        face_default = {
            "face_count": 0, "face_ratio": 0, "has_faces": False,
            "is_closeup": False, "is_single_person": False, "is_multi_person": False,
        }
        features.update(face_default)

        text_feats = analyze_text_enhanced(
            visual_desc="",
            vlm_desc="",
            asr_transcript="",
            original_desc=desc,
            music_title="",
        )
        if not text_feats.get("keywords") and desc:
            words = jieba.cut(desc)
            kw = [w.strip() for w in words if len(w.strip()) >= 2 and w.strip() not in _stop_words()]
            text_feats["keywords"] = kw[:20]
        for k, v in text_feats.items():
            if not k.startswith("_"):
                features[k] = v

        features["clip_text_embedding"] = [0.0] * CLIP_DIM

        content_vector = fuse_content_vector(
            bge_text_embedding=text_feats.get("text_embedding", [0] * 1024),
            has_visual=False,
            has_audio=False,
        )
        features["content_vector"] = content_vector

        features["quality_score"] = estimate_quality_enhanced(
            {}, audio_feats, text_feats, face_default,
            {"transcript": "", "has_speech": False},
            has_visual=False, has_audio=False,
        )

    except Exception as e:
        traceback.print_exc()
        features["extract_status"] = 2
        features["error"] = str(e)
        _unload_all_gpu()
        return features

    _unload_all_gpu()
    elapsed_ms = int((time.time() - t_start) * 1000)
    features["extract_status"] = 1
    features["extract_time_ms"] = elapsed_ms
    print(f"\n[完成] 纯文字 {video_id} 特征提取完成, 耗时 {elapsed_ms}ms", file=sys.stderr)

    _cleanup_internal(features)
    return features


def _cleanup_internal(features: dict):
    """删除仅用于内部计算、不需要存储到数据库的字段"""
    for key in ["clip_text_embedding", "_open_classification"]:
        features.pop(key, None)


# ============================================================================
#  入口
# ============================================================================

def main():
    parser = argparse.ArgumentParser(description="内容特征提取流水线 v3.0")
    parser.add_argument("--mode", default="video", choices=["video", "image", "text"],
                        help="内容类型: video(视频) / image(图文) / text(纯文字)")
    parser.add_argument("--video-url", default="", help="视频文件 URL (mode=video)")
    parser.add_argument("--image-urls", default="",
                        help="图片 URL 列表, JSON数组字符串 (mode=image)")
    parser.add_argument("--video-id", required=True, type=int, help="内容 ID")
    parser.add_argument("--desc", default="", help="描述/正文文本")
    parser.add_argument("--music-title", default="", help="音乐标题 (mode=video)")
    parser.add_argument("--api-base", default="http://localhost:9191",
                        help="Java 后端地址 (用于下载文件)")
    args = parser.parse_args()

    # Hugging Face 国内镜像
    if not os.environ.get("HF_ENDPOINT"):
        os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

    if args.mode == "video":
        ensure_ffmpeg()
        if not args.video_url:
            print("[ERROR] mode=video 需要 --video-url", file=sys.stderr)
            sys.exit(1)
        result = process_video(
            video_url=args.video_url,
            video_id=args.video_id,
            desc=args.desc,
            music_title=args.music_title,
            api_base=args.api_base,
        )
    elif args.mode == "image":
        if not args.image_urls:
            print("[ERROR] mode=image 需要 --image-urls (JSON数组)", file=sys.stderr)
            sys.exit(1)
        try:
            image_urls = json.loads(args.image_urls)
        except json.JSONDecodeError:
            image_urls = [u.strip() for u in args.image_urls.split(",") if u.strip()]
        if not image_urls:
            print("[ERROR] --image-urls 解析后无有效URL", file=sys.stderr)
            sys.exit(1)
        result = process_image(
            image_urls=image_urls,
            video_id=args.video_id,
            desc=args.desc,
            api_base=args.api_base,
        )
    elif args.mode == "text":
        result = process_text_only(
            video_id=args.video_id,
            desc=args.desc,
            api_base=args.api_base,
        )

    # 机器可读完整结果 (单行 JSON, 供 Java 解析)
    class _NpEncoder(json.JSONEncoder):
        def default(self, o):
            if isinstance(o, np.ndarray):
                return o.tolist()
            if hasattr(o, 'dtype'):
                return o.item()
            return super().default(o)

    full_json = json.dumps(result, ensure_ascii=False, cls=_NpEncoder)
    print(f"__RESULT__{full_json}")

    # 人类可读摘要
    summary = {k: v for k, v in result.items()
               if k not in ("text_embedding", "content_vector", "visual_embedding",
                            "visual_description", "music_mfcc")}
    summary["text_embedding_size"] = len(result.get("text_embedding", []))
    summary["content_vector_size"] = len(result.get("content_vector", []))
    summary["visual_embedding_size"] = len(result.get("visual_embedding", []))
    print(f"\n[结果] {json.dumps(summary, ensure_ascii=False, indent=2, cls=_NpEncoder)}")


if __name__ == "__main__":
    main()
