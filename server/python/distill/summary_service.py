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
                 max_new_tokens: int = 512, temperature: float = 0.3) -> str:
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
                {"role": "system", "content": "你是SeekFlow视频平台的搜索助手。请根据收到的平台数据，生成详细的中文搜索摘要。"},
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
        """构建推理 prompt (精简版, 与训练格式一致)"""
        if context is None:
            return f'搜索词："{keyword}"\n\n请生成搜索结果摘要。'

        kw = context.get("keyword", keyword)
        videos = context.get("videos", [])
        users = context.get("users", [])
        stats = context.get("platformStats", {})

        parts = [f"搜索词：{kw}"]

        if videos:
            parts.append(f"\n匹配到 {context.get('totalVideos', len(videos))} 个相关内容：")
            for v in videos[:12]:
                author = v.get("author", {})
                parts.append(
                    f"- {v.get('title', '')} "
                    f"({v.get('likes', 0)}赞 | {v.get('plays', 0)}播放 | "
                    f"作者: {author.get('name', '')})"
                )
        else:
            parts.append("\n暂未匹配到相关内容")

        if users:
            parts.append(f"\n相关创作者: {', '.join(u['name'] for u in users[:5])}")

        parts.append(f"\n平台数据: {stats.get('totalVideos', 0)}个视频, {stats.get('totalUsers', 0)}位用户")
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
