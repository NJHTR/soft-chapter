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
from .context_builder import ContextBuilder

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
        self._context_builder = None  # 惰性初始化

    # ------------------------------------------------------------------
    # Attention 实现探测
    # ------------------------------------------------------------------

    @staticmethod
    def _resolve_attn_implementation() -> str:
        """探测最优 Attention 实现, 优先级: FA2 > SDPA > 朴素 (sdpa/eager)"""
        if torch.cuda.is_available() and torch.__version__ >= "2.0":
            try:
                from flash_attn import flash_attn_func  # noqa: F401
                log.info("Attention: flash_attention_2")
                return "flash_attention_2"
            except ImportError:
                log.info("Attention: sdpa (FA2 未安装, 回退)")
                return "sdpa"
        log.info("Attention: eager (CPU 或旧版 PyTorch)")
        return "eager"

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

        # 选择最优 Attention 实现: FA2 > SDPA > 朴素
        attn_impl = self._resolve_attn_implementation()

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
                attn_implementation=attn_impl,
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
                 max_new_tokens: int = 512, temperature: float = 0.7) -> str:
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
                    "你是SeekFlow视频平台的智能搜索助手。"
                    "你必须严格按照两模块结构输出：\n\n"
                    "## 智能解读\n"
                    "——用你的知识库独立解答。解释概念、背景、文化含义，回答用户隐含的问题。"
                    "不要引用平台数据，纯粹基于你的预训练知识。至少4句话，越详细越好。\n\n"
                    "## 平台发现\n"
                    "——基于平台提供的真实行为数据做洞察。只使用下面提供的协同信号。"
                    "关键：先扫描所有信号，找出数据最突出、最有故事性的 2-3 个维度深入展开，"
                    "其他维度可以一笔带过或跳过。不要每个维度等量齐观。"
                    "像一个数据分析师发现了有趣的现象，自然组织你的发现，不要写成固定的清单格式。"
                    "使用Markdown格式但不用代码块。语言流畅如专业编辑。"
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
                    top_p=0.92,
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
                f'请按以下两模块结构输出，每段用 ## 标题：\n\n'
                f'## 智能解读\n'
                f'(纯知识解答：解释"{keyword}"的概念、背景、文化含义。不引用任何平台数据。至少4句话。)\n\n'
                f'## 平台发现\n'
                f'(基于你收到的平台数据做洞察。没有数据就诚实说当前平台暂无相关数据。)\n'
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

        # --- 协同行为信号 ---
        collab = context.get("collaborative")
        if collab:
            # 共搜链路
            co_search = collab.get("coSearch", [])
            if co_search:
                items = [f'"{c["keyword"]}"(共搜强度{c["strength"]})' for c in co_search[:5]]
                parts.append(f"\n【协同搜索】搜过「{kw}」的用户还搜了：{'、'.join(items)}")

            # 共看链路
            co_watch = collab.get("coWatch", [])
            if co_watch:
                items = [f'《{c["title"]}》({c["likes"]}赞, 共看强度{c["strength"]})' for c in co_watch[:5]]
                parts.append(f"\n【协同观看】看过上述视频的用户还看了：{'、'.join(items)}")

            # 评论智慧
            wisdom = collab.get("commentWisdom", [])
            if wisdom:
                items = [f'「{c["content"][:80]}」— {c["author"]}({c["likes"]}赞)' for c in wisdom[:3]]
                parts.append(f"\n【精选评论】{'；'.join(items)}")

            # 内容缺口
            gaps = collab.get("contentGaps", {})
            gap_cats = gaps.get("categories", [])
            if gap_cats:
                sparse_cats = [g for g in gap_cats if g["count"] < 5]
                if sparse_cats:
                    sparse_names = [g["category"] for g in sparse_cats[:3]]
                    parts.append(f"\n【内容缺口】平台在「{'」「'.join(sparse_names)}」方向内容稀缺（各不足5条），属于蓝海品类")
                else:
                    cat_summary = '、'.join(f'{g["category"]}({g["count"]}条)' for g in gap_cats[:5])
                    parts.append(f"\n【品类分布】{cat_summary}")
            if gaps.get("isSparse"):
                parts.append("  注意：该搜索方向整体内容稀疏，平台仅有少量相关内容")

            # 时序趋势
            trend = collab.get("temporalTrend", {})
            if trend.get("recentVolume", 0) > 0:
                trend_cn = {"rising": "上升", "declining": "下降", "stable": "平稳"}.get(trend.get("trend"), "未知")
                parts.append(
                    f"\n【搜索趋势】近30天热度{trend_cn}"
                    f"(变化率{trend.get('changeRatio', 0):.0%}，近7天{trend['recentVolume']}次搜索)"
                )

            # 热点上下文
            hot = collab.get("hotContext", {})
            if hot:
                top_cats = hot.get("topCategories", [])
                if top_cats:
                    cat_strs = [f'{c["name"]}({c["count"]})' for c in top_cats[:5]]
                    parts.append(f"【内容品类分布】{' / '.join(cat_strs)}")
                type_dist = hot.get("typeDistribution", {})
                if type_dist:
                    type_str = '/'.join(f'{k}:{v}' for k, v in type_dist.items())
                    parts.append(f"【类型分布】{type_str}")
                if hot.get("avgCompletionRate", 0) > 0:
                    parts.append(f"【平均完播率】{hot['avgCompletionRate']:.0%}")

        # 指令部分 — 两模块输出
        parts.append(f"""

以上数据分为两类用途：
- 「协同搜索/协同观看/内容缺口/搜索趋势/品类分布/完播率」→ 仅用于下面的「平台发现」段落
- 视频列表和创作者列表 → 仅用于「平台发现」段落的数据支撑

严格按以下两模块结构输出，每段用 ## 标题：

## 智能解读
用户搜索"{kw}"，很可能想了解什么？请结合你的知识库，给出关于"{kw}"的深度知识性解答。
如果搜索词是一个问题请直接回答；如果是名词请介绍其背景、要点、文化含义和有趣信息。
**禁止在此段落引用任何平台数据**（如视频数量、点赞数、共搜词等都不许出现）。
至少 4 句话，越详细越好。像百科全书一样专业又生动。

## 平台发现
基于上面提供的平台行为数据，给出数据驱动的洞察发现。
**禁止在此段落注入你自有的知识**，只能基于提供的协同信号做分析。

重要——不要按固定模板逐条罗列。按以下方式组织：

1. 先快速扫描所有信号，找出 2-3 个数据最突出的维度（比如共搜强度特别高的、内容缺口明显的、趋势剧烈变化的）。这些就是你本次要深入展开的焦点。

2. 围绕焦点展开分析时，从下面选一个最合适的叙事角度切入（不要每个都用一遍）：
   - 如果共搜信号强 → 写成"用户需求地图"：搜这个词的人在同时寻找什么？揭示了什么潜在需求链？
   - 如果内容缺口明显 → 写成"蓝海发现"：平台在这个方向的哪块细分是空白的？意味着什么机会？
   - 如果趋势剧烈变化 → 写成"风向洞察"：热度在涨还是跌？可能的原因是什么？
   - 如果评论/社区信号突出 → 写成"用户心声"：用户在讨论什么？有什么共识或分歧？

3. 不突出的维度可以一笔带过甚至不写。宁可 2 个点说透，不要 5 个点蜻蜓点水。

叙事要有层次感——先抛出发现，再引用数据佐证，最后点出意味。至少 3 条发现。
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
                # 补充协同行为信号 (若 Java 端未传入)
                if context and "collaborative" not in context:
                    if self._context_builder is None:
                        self._context_builder = ContextBuilder()
                    context = self._context_builder.enrich(context)

                result = self.generate(keyword, context)
                if not result or not result.strip():
                    print("ERROR:模型返回空结果", flush=True)
                    continue
                print(f"SUMMARY:{result}", flush=True)
                print("__END__", flush=True)
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
