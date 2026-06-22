"""
任务5: 推理部署 — 加载蒸馏模型, 提供 generate(keyword, context) 接口

替代原先 3B 模型的搜索摘要生成。

特性:
- 4-bit 量化加载 (RTX 4060 8GB)
- LRU 推理缓存 (同 keyword+context hash 不重复生成)
- 与现有 Java 后端 search_summary.py 协议兼容

用法:
  service = SummaryService()
  service.load()
  summary = service.generate("美食教程", context_dict)
"""

import hashlib
import json
import logging
import os
import sys
import time
from functools import lru_cache
from threading import Lock
from typing import Optional

# 阻断所有 HuggingFace Hub 网络请求 (本地模型已下载)
os.environ["HF_HUB_OFFLINE"] = "1"

import torch
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
)

from .config import TRAIN_CONFIG

log = logging.getLogger(__name__)


class SummaryService:
    """蒸馏模型搜索摘要推理服务"""

    def __init__(
        self,
        adapter_dir: str = None,
        base_model: str = None,
        cache_size: int = 256,
    ):
        self.adapter_dir = adapter_dir or TRAIN_CONFIG["adapter_dir"]
        self.base_model = base_model or TRAIN_CONFIG["base_model"]
        self.cache_size = cache_size
        self.tokenizer = None
        self.model = None
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.lock = Lock()
        self._cache = {}  # 简易 LRU
        self._cache_order = []

    # ------------------------------------------------------------------
    # 模型加载
    # ------------------------------------------------------------------

    def load(self, use_adapter: bool = True):
        """加载蒸馏模型 (4-bit 量化 + 可选 LoRA 适配器)"""
        log.info("加载蒸馏模型: base=%s adapter=%s device=%s",
                 self.base_model, self.adapter_dir if use_adapter else "none", self.device)

        # 优先本地缓存, 避免无网时卡在 HuggingFace 重试
        self.tokenizer = AutoTokenizer.from_pretrained(
            self.base_model, trust_remote_code=True, local_files_only=True
        )
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        if self.device == "cuda":
            bnb_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type="nf4",
                bnb_4bit_use_double_quant=True,
                bnb_4bit_compute_dtype=torch.bfloat16,
            )
            self.model = AutoModelForCausalLM.from_pretrained(
                self.base_model,
                quantization_config=bnb_config,
                device_map="auto",
                trust_remote_code=True,
                local_files_only=True,
            )
        else:
            self.model = AutoModelForCausalLM.from_pretrained(
                self.base_model,
                torch_dtype=torch.float32,
                trust_remote_code=True,
                local_files_only=True,
            ).to("cpu")

        self.model.eval()

        # 尝试加载 LoRA 适配器
        if use_adapter and os.path.isdir(self.adapter_dir):
            adapter_config = os.path.join(self.adapter_dir, "adapter_config.json")
            if os.path.isfile(adapter_config):
                try:
                    from peft import PeftModel
                    self.model = PeftModel.from_pretrained(
                        self.model, self.adapter_dir
                    )
                    log.info("已加载 LoRA 适配器: %s", self.adapter_dir)
                except Exception as e:
                    log.warning("加载适配器失败, 使用基础模型: %s", e)

        log.info("模型就绪, 显存: %.1f GB", torch.cuda.memory_allocated() / 1e9
        if self.device == "cuda" else 0)

    # ------------------------------------------------------------------
    # 推理
    # ------------------------------------------------------------------

    def generate(self, keyword: str, context: dict = None,
                 max_new_tokens: int = 1024, temperature: float = 0.5) -> str:
        """生成搜索摘要"""
        if self.model is None:
            return "[错误] 模型未加载"

        # 缓存检查
        cache_key = self._hash(keyword, context)
        cached = self._cache_get(cache_key)
        if cached:
            return cached

        with self.lock:
            prompt = self._build_prompt(keyword, context)

            messages = [
                {"role": "system", "content": (
                    "你是SeekFlow视频平台的智能搜索助手。你的任务是基于平台返回的真实数据，"
                    "生成一份结构清晰、内容丰富的多段落搜索摘要。"
                    "你必须严格按照要求的格式组织输出，使用Markdown标记但不使用代码块。"
                    "语言流畅自然，像一位专业的编辑在为用户梳理搜索结果。"
                )},
                {"role": "user", "content": prompt},
            ]

            try:
                text = self.tokenizer.apply_chat_template(
                    messages, tokenize=False, add_generation_prompt=True
                )
            except Exception as e:
                log.error("chat_template 失败: %s", e)
                return f"[模板错误] {e}"

            # 处理 list 类型返回值
            if isinstance(text, list):
                text = text[0] if text else ""

            inputs = self.tokenizer(text, return_tensors="pt")
            if self.device == "cuda":
                inputs = {k: v.to("cuda") for k, v in inputs.items()}

            t0 = time.time()
            with torch.no_grad():
                outputs = self.model.generate(
                    **inputs,
                    max_new_tokens=max_new_tokens,
                    temperature=temperature,
                    top_p=0.85,
                    do_sample=True,
                    repetition_penalty=1.15,
                    pad_token_id=self.tokenizer.eos_token_id,
                )

            generated = outputs[0][inputs["input_ids"].shape[1]:]
            result = self.tokenizer.decode(generated, skip_special_tokens=True).strip()
            elapsed = (time.time() - t0) * 1000

            log.info("推理完成: %dms, %d chars", int(elapsed), len(result))

            # 写入缓存
            self._cache_put(cache_key, result)
            return result

    # ------------------------------------------------------------------
    # Prompt 构建
    # ------------------------------------------------------------------

    def _build_prompt(self, keyword: str, context: dict = None) -> str:
        """构建推理 prompt (多段落格式: 数据洞察 + 智能解答 + 浏览建议)"""
        if context is None:
            return (
                f'用户搜索了"{keyword}"。\n\n'
                f'请按照以下格式生成搜索结果摘要，每段用 ## 标题分隔：\n\n'
                f'## 搜索概况\n(根据关键词分析这个搜索主题的概况)\n\n'
                f'## 智能解答\n(直接回答用户"{keyword}"相关的问题，给出知识性解答)\n\n'
                f'## 浏览建议\n(给用户提供浏览和筛选建议)\n'
            )

        kw = context.get("keyword", keyword)
        videos = context.get("videos", [])
        users = context.get("users", [])
        total_videos = context.get("totalVideos", len(videos))
        total_likes = context.get("totalLikes", 0)
        total_plays = context.get("totalPlays", 0)
        total_comments = context.get("totalComments", 0)
        avg_duration = context.get("avgDuration", 0)
        top_categories = context.get("topCategories", "综合")
        type_dist = context.get("typeDistribution", "")
        user_count = context.get("totalUsers", 0)
        total_followers = context.get("totalFollowers", 0)

        parts = []

        # 数据部分
        parts.append(f"【搜索关键词】{kw}")
        parts.append(f"【搜索结果统计】共找到 {total_videos} 个视频、{user_count} 位创作者")
        parts.append(f"【内容品类】{top_categories}")
        if type_dist:
            parts.append(f"【内容类型分布】{type_dist}")
        parts.append(f"【互动数据】共 {total_plays} 播放，{total_likes} 点赞，{total_comments} 评论")
        if avg_duration > 0:
            parts.append(f"【平均时长】约 {avg_duration} 秒")
        if total_followers > 0:
            parts.append(f"【创作者粉丝总量】{total_followers}")

        # 视频列表
        if videos:
            parts.append(f"\n【热门视频 TOP8】")
            for v in videos[:8]:
                parts.append(
                    f"- {v.get('title', '')[:60]} "
                    f"[{v.get('type', '视频')} | {v.get('category', '综合')} | "
                    f"{v.get('likes', 0)}赞 | {v.get('plays', 0)}播放 | "
                    f"质量分{v.get('qualityScore', 0):.1f}]"
                )

        # 创作者列表
        if users:
            top_users = sorted(users, key=lambda u: u.get("followerCount", 0), reverse=True)
            parts.append(f"\n【相关创作者 TOP5】")
            for u in top_users[:5]:
                # 计算粉丝量级
                fc = u.get("followerCount", 0)
                if fc >= 10000:
                    level = f"{fc/10000:.1f}万粉丝"
                elif fc >= 1000:
                    level = f"{fc/1000:.0f}千粉丝"
                else:
                    level = f"{fc}粉丝"
                parts.append(f"- {u['name']} ({level})")

        # 指令部分 — 要求多段落输出
        parts.append(f"""

请基于以上真实数据，用专业编辑的口吻生成一份多段落搜索摘要。严格按以下结构输出，每段用 ## 标题：

## 数据洞察
基于上面的数据进行分析：搜索结果的整体质量和热度如何？
哪些品类的内容最丰富？内容类型分布有什么特点？
（不要简单罗列数据，要给出有见地的分析。至少 3 句话。）

## 智能解答
用户搜索"{kw}"，很可能想了解什么？请结合你的知识，给出关于"{kw}"的知识性解答。
如果搜索词是一个问题，请直接回答；如果是名词，请介绍其背景、要点和有趣的信息。
（至少 4 句话，越详细越好。）

## 浏览建议
根据搜索结果的数据特征，给用户具体的浏览建议：
- 可以从哪些角度筛选内容？
- 有哪些优质创作者值得关注？
- 如何找到最适合自己的内容？
（至少 3 条建议。）
""")
        return "\n".join(parts)

    # ------------------------------------------------------------------
    # 缓存 (简易 LRU)
    # ------------------------------------------------------------------

    @staticmethod
    def _hash(keyword: str, context: dict = None) -> str:
        raw = keyword + json.dumps(context or {}, sort_keys=True, ensure_ascii=False)
        return hashlib.md5(raw.encode()).hexdigest()

    def _cache_get(self, key: str) -> Optional[str]:
        return self._cache.get(key)

    def _cache_put(self, key: str, value: str):
        if key in self._cache:
            return
        if len(self._cache) >= self.cache_size:
            # 淘汰最旧的
            oldest = self._cache_order.pop(0) if self._cache_order else None
            if oldest:
                self._cache.pop(oldest, None)
        self._cache[key] = value
        self._cache_order.append(key)

    def clear_cache(self):
        self._cache.clear()
        self._cache_order.clear()

    # ------------------------------------------------------------------
    # 常驻服务模式 (兼容现有 search_summary.py 协议)
    # ------------------------------------------------------------------

    def serve(self):
        """stdin/stdout 常驻服务, 兼容 Java SearchSuggestionService"""
        self.load()
        print("READY", flush=True)
        log.info("服务就绪, 等待输入...")

        for line in sys.stdin:
            raw = line.strip()
            if not raw:
                continue
            if raw == "EXIT":
                log.info("收到退出信号")
                break
            if len(raw) > 8000:
                print("ERROR:输入过长", flush=True)
                continue

            # 解析输入
            try:
                if raw.startswith("{"):
                    data = json.loads(raw)
                    keyword = data.get("keyword", "")
                    if not keyword:
                        print("ERROR:JSON缺少keyword字段", flush=True)
                        continue
                    context = data
                else:
                    keyword = raw
                    context = None
            except json.JSONDecodeError:
                keyword = raw
                context = None

            if len(keyword) > 100:
                print("ERROR:关键词过长", flush=True)
                continue

            try:
                result = self.generate(keyword, context)
                if not result or not result.strip():
                    print("ERROR:模型返回空结果", flush=True)
                    continue
                print(f"SUMMARY:{result}", flush=True)
                log.info("生成完成: %s → %s", keyword, result[:60])
            except Exception as e:
                log.error("生成失败: %s", e, exc_info=True)
                print(f"ERROR:{e}", flush=True)
                # 清除 CUDA 缓存, 防止显存碎片累积
                try:
                    import torch
                    torch.cuda.empty_cache()
                except Exception:
                    pass

        log.info("服务退出")


def main():
    service = SummaryService()
    service.serve()

if __name__ == "__main__":
    main()
