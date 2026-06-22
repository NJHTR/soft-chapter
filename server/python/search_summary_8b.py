#!/usr/bin/env python3
"""
搜索智能摘要 — 支持两种模式:
  - 一次性模式: python search_summary.py --keyword "搜索词"  (兼容旧调用)
  - 常驻服务模式: python search_summary.py --serve            (推荐, 模型只加载一次)

常驻模式协议 (stdin/stdout):
  输入: 每行一个 JSON 对象 {"keyword": "...", "videos": [...], "users": [...], ...}
        也兼容纯文本关键词 (旧版协议)
  输出: 每行 "SUMMARY:摘要内容" 或 "ERROR:错误信息"
  就绪信号: 启动后输出 "READY"
  退出: 发送 "EXIT" 或关闭 stdin

依赖: torch, transformers (已在 requirements.txt)
"""

import argparse
import json
import logging
import os
import sys
import torch

# 阻断所有 HuggingFace Hub 网络请求 (本地模型已下载)
os.environ["HF_HUB_OFFLINE"] = "1"

from transformers import AutoModelForCausalLM, AutoTokenizer

if sys.platform == "win32":
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    stream=sys.stderr,
)
log = logging.getLogger("search-summary")

MODEL_NAME = "Qwen/Qwen3-8B-Instruct"
ADAPTER_DIR = os.path.join(os.path.dirname(__file__), "distill_lora_adapter")


def load_lora_adapter(model):
    if not os.path.isdir(ADAPTER_DIR):
        return model
    adapter_config = os.path.join(ADAPTER_DIR, "adapter_config.json")
    if not os.path.isfile(adapter_config):
        return model
    try:
        from peft import PeftModel
        merged = PeftModel.from_pretrained(model, ADAPTER_DIR)
        log.info("已加载微调适配器: %s", ADAPTER_DIR)
        return merged
    except ImportError:
        log.info("peft 未安装, 跳过适配器加载")
    except Exception as e:
        log.warning("加载适配器失败: %s", e)
    return model


def load_model(model_name):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    log.info("设备: %s, 加载模型: %s", device, model_name)

    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True,
                                              local_files_only=True)

    if device == "cuda":
        try:
            log.info("尝试 fp16 加载 (仅本地)...")
            model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float16,
                device_map="auto",
                trust_remote_code=True,
                local_files_only=True,
            )
        except (torch.cuda.OutOfMemoryError, RuntimeError) as e:
            log.warning("fp16 OOM, 回退到 4-bit 量化: %s", e)
            from transformers import BitsAndBytesConfig
            bnb_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type="nf4",
                bnb_4bit_compute_dtype=torch.float16,
            )
            model = AutoModelForCausalLM.from_pretrained(
                model_name,
                quantization_config=bnb_config,
                device_map="auto",
                trust_remote_code=True,
                local_files_only=True,
            )
    else:
        model = AutoModelForCausalLM.from_pretrained(
            model_name,
            torch_dtype=torch.float32,
            trust_remote_code=True,
            local_files_only=True,
        ).to("cpu")

    model.eval()
    model = load_lora_adapter(model)
    log.info("模型就绪")
    return tokenizer, model, device


def build_user_message(context: dict) -> str:
    """构建多段落搜索摘要 prompt: 数据洞察 + 智能解答 + 浏览建议"""
    keyword = context.get("keyword", "")
    videos = context.get("videos", [])
    users = context.get("users", [])
    total_videos = context.get("totalVideos", len(videos))
    total_likes = context.get("totalLikes", 0)
    total_plays = context.get("totalPlays", 0)
    total_comments = context.get("totalComments", 0)
    avg_duration = context.get("avgDuration", 0)
    top_categories = context.get("topCategories", "综合")
    type_dist = context.get("typeDistribution", "")
    user_count = context.get("totalUsers", len(users))
    total_followers = context.get("totalFollowers", 0)

    lines = []

    # 数据部分
    lines.append(f"【搜索关键词】{keyword}")
    lines.append(f"【搜索结果统计】共找到 {total_videos} 个视频、{user_count} 位创作者")
    lines.append(f"【内容品类】{top_categories}")
    if type_dist:
        lines.append(f"【内容类型分布】{type_dist}")
    lines.append(f"【互动数据】共 {total_plays} 播放，{total_likes} 点赞，{total_comments} 评论")
    if avg_duration > 0:
        lines.append(f"【平均时长】约 {avg_duration} 秒")
    if total_followers > 0:
        lines.append(f"【创作者粉丝总量】{total_followers}")

    # 视频列表
    if videos:
        lines.append("\n【热门视频 TOP8】")
        for v in videos[:8]:
            lines.append(
                f"- {v.get('title', '')[:60]} "
                f"[{v.get('type', '视频')} | {v.get('category', '综合')} | "
                f"{v.get('likes', 0)}赞 | {v.get('plays', 0)}播放 | "
                f"质量分{v.get('qualityScore', 0):.1f}]"
            )

    # 创作者列表
    if users:
        top_users = sorted(users, key=lambda u: u.get("followerCount", 0), reverse=True)
        lines.append("\n【相关创作者 TOP5】")
        for u in top_users[:5]:
            fc = u.get("followerCount", 0)
            if fc >= 10000:
                level = f"{fc/10000:.1f}万粉丝"
            elif fc >= 1000:
                level = f"{fc/1000:.0f}千粉丝"
            else:
                level = f"{fc}粉丝"
            lines.append(f"- {u['name']} ({level})")

    # --- 协同行为信号 ---
    collab = context.get("collaborative")
    if collab:
        # 共搜链路
        co_search = collab.get("coSearch", [])
        if co_search:
            items = [f'"{c["keyword"]}"(共搜强度{c["strength"]})' for c in co_search[:5]]
            lines.append(f"\n【协同搜索】搜过「{keyword}」的用户还搜了：{'、'.join(items)}")

        # 共看链路
        co_watch = collab.get("coWatch", [])
        if co_watch:
            items = [f'《{c["title"]}》({c["likes"]}赞, 共看强度{c["strength"]})' for c in co_watch[:5]]
            lines.append(f"\n【协同观看】看过上述视频的用户还看了：{'、'.join(items)}")

        # 评论智慧
        wisdom = collab.get("commentWisdom", [])
        if wisdom:
            items = [f'「{c["content"][:80]}」— {c["author"]}({c["likes"]}赞)' for c in wisdom[:3]]
            lines.append(f"\n【精选评论】{'；'.join(items)}")

        # 内容缺口
        gaps = collab.get("contentGaps", {})
        gap_cats = gaps.get("categories", [])
        if gap_cats:
            sparse_cats = [g for g in gap_cats if g["count"] < 5]
            if sparse_cats:
                sparse_names = [g["category"] for g in sparse_cats[:3]]
                lines.append(f"\n【内容缺口】平台在「{'」「'.join(sparse_names)}」方向内容稀缺（各不足5条），属于蓝海品类")
            else:
                cat_summary = '、'.join(f'{g["category"]}({g["count"]}条)' for g in gap_cats[:5])
                lines.append(f"\n【品类分布】{cat_summary}")
        if gaps.get("isSparse"):
            lines.append("  注意：该搜索方向整体内容稀疏，平台仅有少量相关内容")

        # 时序趋势
        trend = collab.get("temporalTrend", {})
        if trend.get("recentVolume", 0) > 0:
            trend_cn = {"rising": "上升", "declining": "下降", "stable": "平稳"}.get(trend.get("trend"), "未知")
            lines.append(
                f"\n【搜索趋势】近30天热度{trend_cn}"
                f"(变化率{trend.get('changeRatio', 0):.0%}，近7天{trend['recentVolume']}次搜索)"
            )

        # 热点上下文
        hot = collab.get("hotContext", {})
        if hot:
            top_cats = hot.get("topCategories", [])
            if top_cats:
                cat_strs = [f'{c["name"]}({c["count"]})' for c in top_cats[:5]]
                lines.append(f"【内容品类分布】{' / '.join(cat_strs)}")
            type_dist2 = hot.get("typeDistribution", {})
            if type_dist2:
                type_str = '/'.join(f'{k}:{v}' for k, v in type_dist2.items())
                lines.append(f"【类型分布】{type_str}")
            if hot.get("avgCompletionRate", 0) > 0:
                lines.append(f"【平均完播率】{hot['avgCompletionRate']:.0%}")

    # 指令部分 — 两模块输出
    kw = keyword
    lines.append(f"""

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
    return "\n".join(lines)


def generate(tokenizer, model, device, keyword_or_context):
    if isinstance(keyword_or_context, dict):
        user_content = build_user_message(keyword_or_context)
    else:
        kw = keyword_or_context
        user_content = (
            f'用户搜索了"{kw}"。\n\n'
            f'请按以下两模块结构输出，每段用 ## 标题：\n\n'
            f'## 智能解读\n'
            f'(纯知识解答：解释"{kw}"的概念、背景、文化含义。不引用任何平台数据。至少4句话。)\n\n'
            f'## 平台发现\n'
            f'(基于你收到的平台数据做洞察。没有数据就诚实说当前平台暂无相关数据。)\n'
        )

    messages = [{"role": "user", "content": user_content}]

    try:
        text = tokenizer.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )
    except Exception as e:
        log.error("apply_chat_template 失败: %s", e)
        return f"[模板错误] {e}"

    if isinstance(text, list):
        text = text[0] if text else ""
    if not isinstance(text, str):
        log.error("chat_template 返回异常类型: %s", type(text))
        return f"[类型错误] chat_template 返回了 {type(text).__name__}"

    try:
        inputs = tokenizer(text, return_tensors="pt")
    except Exception as e:
        log.error("tokenizer 编码失败: %s, text[:100]=%s", e, repr(text[:100]))
        return f"[编码错误] {e}"
    if device == "cuda":
        inputs = {k: v.to("cuda") for k, v in inputs.items()}

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=512,
            temperature=0.7,
            top_p=0.92,
            do_sample=True,
            repetition_penalty=1.15,
            pad_token_id=tokenizer.eos_token_id,
        )

    generated = outputs[0][inputs["input_ids"].shape[1]:]
    result = tokenizer.decode(generated, skip_special_tokens=True).strip()
    return result


def run_oneshot(args):
    keyword = args.keyword.strip()
    if not keyword:
        print("", flush=True)
        return

    tokenizer, model, device = load_model(args.model)
    result = generate(tokenizer, model, device, keyword)
    log.info("完成, %d 字符: %s", len(result), result[:80])
    print(result, flush=True)


def run_serve(args):
    tokenizer, model, device = load_model(args.model)

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
            print(f"ERROR:输入过长", flush=True)
            continue

        try:
            if raw.startswith("{"):
                context = json.loads(raw)
                keyword = context.get("keyword", "")
                if not keyword:
                    print(f"ERROR:JSON缺少keyword字段", flush=True)
                    continue
            else:
                keyword = raw
                context = None
        except json.JSONDecodeError:
            keyword = raw
            context = None

        if len(keyword) > 100:
            print(f"ERROR:关键词过长", flush=True)
            continue

        try:
            result = generate(tokenizer, model, device, context if context else keyword)
            print(f"SUMMARY:{result}", flush=True)
            print("__END__", flush=True)
            log.info("生成完成: %s → %s", keyword, result[:80])
        except Exception as e:
            log.error("生成失败: %s", e)
            print(f"ERROR:{e}", flush=True)

    log.info("服务退出")


def main():
    parser = argparse.ArgumentParser(description="搜索摘要生成")
    parser.add_argument("--keyword", default=None, help="搜索关键词 (一次性模式)")
    parser.add_argument("--model", default=MODEL_NAME, help="HuggingFace 模型名")
    parser.add_argument("--serve", action="store_true", help="常驻服务模式")
    args = parser.parse_args()

    if args.serve:
        run_serve(args)
    elif args.keyword:
        run_oneshot(args)
    else:
        parser.error("请指定 --keyword 或 --serve")


if __name__ == "__main__":
    main()
