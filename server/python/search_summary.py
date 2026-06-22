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

MODEL_NAME = "Qwen/Qwen2.5-3B-Instruct"
ADAPTER_DIR = os.path.join(os.path.dirname(__file__), "summary_lora_adapter")


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

    # 指令部分 — 要求多段落输出
    kw = keyword
    lines.append(f"""

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
    return "\n".join(lines)


def generate(tokenizer, model, device, keyword_or_context):
    if isinstance(keyword_or_context, dict):
        user_content = build_user_message(keyword_or_context)
    else:
        kw = keyword_or_context
        user_content = (
            f'用户搜索了"{kw}"。\n\n'
            f'请按照以下格式生成搜索结果摘要，每段用 ## 标题分隔：\n\n'
            f'## 搜索概况\n(根据关键词分析这个搜索主题的概况)\n\n'
            f'## 智能解答\n(直接回答用户"{kw}"相关的问题，给出知识性解答)\n\n'
            f'## 浏览建议\n(给用户提供浏览和筛选建议)\n'
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
            temperature=0.5,
            top_p=0.85,
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
