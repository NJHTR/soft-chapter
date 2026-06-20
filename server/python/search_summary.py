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

    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)

    if device == "cuda":
        # 先尝试 fp16, OOM 则自动回退 4-bit 量化
        try:
            log.info("尝试 fp16 加载...")
            model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float16,
                device_map="auto",
                trust_remote_code=True,
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
            )
    else:
        model = AutoModelForCausalLM.from_pretrained(
            model_name,
            torch_dtype=torch.float32,
            trust_remote_code=True,
        ).to("cpu")

    model.eval()
    model = load_lora_adapter(model)
    log.info("模型就绪")
    return tokenizer, model, device


def build_user_message(context: dict) -> str:
    """将搜索上下文构建为自然段落, 然后追加极简指令"""
    keyword = context.get("keyword", "")
    videos = context.get("videos", [])
    users = context.get("users", [])
    total_videos = context.get("totalVideos", len(videos))
    total_users = context.get("totalUsers", len(users))
    top_categories = context.get("topCategories", "")

    lines = []

    # 1. 用一句话概括搜索结果
    if videos:
        titles = "、".join(v.get("title", "")[:40] for v in videos[:10] if v.get("title"))
        cat_info = f"，主要品类是{top_categories}" if top_categories else ""
        lines.append(
            f'用户搜索了"{keyword}"。平台共找到{total_videos}个相关视频{cat_info}，'
            f'例如：{titles}。'
        )
    else:
        lines.append(f'用户搜索了"{keyword}"。平台暂未找到相关视频。')

    # 2. 热门视频的点赞数 (挑前3)
    hot = [v for v in videos[:10] if v.get("likes", 0) > 0][:3]
    if hot:
        hot_str = "，".join(
            f"《{v['title'][:30]}》有{v['likes']}个赞" for v in hot
        )
        lines.append(f"其中热门内容：{hot_str}。")

    # 3. 匹配到的创作者
    if users:
        user_str = "、".join(u.get("name", "") for u in users[:5] if u.get("name"))
        if user_str:
            lines.append(f"相关创作者包括：{user_str}。")

    # 4. 极简指令 — 不放系统提示里, 直接放在数据后面
    kw = keyword
    lines.append(
        f"请用流畅自然的中文，写一段3-5句话的搜索结果摘要，"
        f'告诉用户搜索"{kw}"能在平台看到什么样的内容。'
        f"直接写摘要正文，不要加标题、前缀或任何格式标记。"
    )

    return "\n".join(lines)


def generate(tokenizer, model, device, keyword_or_context):
    if isinstance(keyword_or_context, dict):
        user_content = build_user_message(keyword_or_context)
    else:
        user_content = (
            f'用户搜索了"{keyword_or_context}"。'
            f'请用流畅自然的中文，写一段2-4句话的搜索结果摘要，'
            f'告诉用户搜索"{keyword_or_context}"能在平台看到什么样的内容。'
            f'直接写摘要正文，不要加任何前缀或格式标记。'
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
            max_new_tokens=256,
            temperature=0.2,
            top_p=0.8,
            do_sample=True,
            repetition_penalty=1.2,
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
