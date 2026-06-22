"""
准备公开摘要数据集 → 统一 Chat Template 格式

数据源:
  - LCSTS: 微博短文本摘�? (~240万�? 取10万)
  - CSL: 中文科学论文摘�? (~40万�? 取5万)
  - XLSum-Chinese: BBC新闻摘�? (~3万条, 全�?
  - NLPCC 2017: 新闻摘�? (~5万条)

输出: public_summary_train.jsonl / public_summary_val.jsonl
格式: {"instruction": "...", "input": "...", "output": "..."}

用法:
  python -m distill.prepare_public_data --max_samples 200000
"""

import argparse
import json
import logging
import os
import random
import sys

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

# 中文摘要 prompt 模板 (多样性)
PROMPT_TEMPLATES = [
    "请为以下内容写一段简洁的中文摘要：\n\n{text}",
    "帮我把这篇文章提炼成一段摘要：\n\n{text}",
    "用一段话总结下面内容的核心要点：\n\n{text}",
    "请阅读以下文字，写一段精炼的摘要：\n\n{text}",
    "你是一位专业编辑，请为以下内容撰写摘要：\n\n{text}",
    "Summarize the following in Chinese, capturing the key points:\n\n{text}",
    "把这段文字压缩成一段摘要，保留核心信息：\n\n{text}",
]

# SeekFlow 风格的 system prompt (与阶段2保持一�?
SYSTEM_PROMPT = (
    "你是SeekFlow视频平台的智能编辑助手。你的任务是将用户提供的内容提炼为精炼、"
    "信息量大的摘要。语言流畅自然，结构清晰。"
)


def load_lcsts(max_samples: int = 100000) -> list[dict]:
    """加载 LCSTS 微博摘要数据 (PART_I.txt + PART_II.txt)"""
    from datasets import load_dataset
    log.info("加载 LCSTS...")
    try:
        ds = load_dataset("lansinuote/LCSTS", split="train", streaming=True)
    except Exception:
        # 备�? 尝试原始格�?
        try:
            ds = load_dataset("CSTV/lcsts", split="train", streaming=True)
        except Exception:
            log.warning("LCSTS 下载失败, 跳过")
            return []

    samples = []
    for item in ds:
        text = item.get("content", item.get("text", item.get("document", "")))
        summary = item.get("summary", item.get("title", ""))
        if not text or not summary:
            continue
        if len(text) < 20 or len(summary) < 5:
            continue
        samples.append({"text": text.strip(), "summary": summary.strip()})
        if len(samples) >= max_samples:
            break

    log.info("LCSTS: %d 条", len(samples))
    return samples


def load_csl(max_samples: int = 50000) -> list[dict]:
    """加载 CSL 论文摘要数据"""
    from datasets import load_dataset
    log.info("加载 CSL...")
    try:
        ds = load_dataset("p208p2002/csl-1.8G-filtered", split="train", streaming=True)
    except Exception:
        try:
            ds = load_dataset("neuclir/csl", split="train", streaming=True)
        except Exception:
            log.warning("CSL 下载失败, 跳过")
            return []

    samples = []
    for item in ds:
        # CSL 格式: {title, abstract, keyword, category, ...}
        text = item.get("title", "") + "。"
        keywords = item.get("keyword", item.get("keywords", ""))
        if keywords:
            if isinstance(keywords, list):
                text += "关键词：" + "、".join(keywords)
            else:
                text += "关键词：" + str(keywords)
        summary = item.get("abstract", "")
        if not summary or len(summary) < 10:
            continue
        samples.append({"text": text.strip(), "summary": summary.strip()})
        if len(samples) >= max_samples:
            break

    log.info("CSL: %d 条", len(samples))
    return samples


def load_xlsum_chinese(max_samples: int = 50000) -> list[dict]:
    """加载 XLSum 中文摘要"""
    from datasets import load_dataset
    log.info("加载 XLSum-Chinese...")
    try:
        ds = load_dataset("GEM/xlsum", "chinese_simplified", split="train", streaming=True)
    except Exception:
        log.warning("XLSum 下载失败, 跳过")
        return []

    samples = []
    for item in ds:
        text = item.get("text", "")
        summary = item.get("summary", "")
        if not text or not summary:
            continue
        if len(text) < 30 or len(summary) < 10:
            continue
        samples.append({"text": text.strip()[:2048], "summary": summary.strip()})
        if len(samples) >= max_samples:
            break

    log.info("XLSum: %d 条", len(samples))
    return samples


def load_nlpcc(max_samples: int = 50000) -> list[dict]:
    """加载 NLPCC 2017 中文摘要数据"""
    from datasets import load_dataset
    log.info("加载 NLPCC 2017...")
    try:
        ds = load_dataset("chinianh/NLPCC2017_Summary", split="train", streaming=True)
    except Exception:
        try:
            ds = load_dataset("nlpcc2017/summarization", split="train", streaming=True)
        except Exception:
            log.warning("NLPCC 下载失败, 跳过")
            return []

    samples = []
    for item in ds:
        text = item.get("article", item.get("text", item.get("content", "")))
        summary = item.get("summary", item.get("headline", ""))
        if not text or not summary:
            continue
        if len(text) < 20 or len(summary) < 5:
            continue
        samples.append({"text": text.strip()[:2048], "summary": summary.strip()})
        if len(samples) >= max_samples:
            break

    log.info("NLPCC: %d 条", len(samples))
    return samples


def format_chat_template(sample: dict) -> str:
    """将样本格式化为 Qwen Chat Template 文本"""
    text = sample["text"]
    summary = sample["summary"]

    # 随机选一个 prompt 模板, 增加多样�?
    template = random.choice(PROMPT_TEMPLATES)
    user_content = template.format(text=text)

    return (
        f"<|im_start|>system\n{SYSTEM_PROMPT}<|im_end|>\n"
        f"<|im_start|>user\n{user_content}<|im_end|>\n"
        f"<|im_start|>assistant\n{summary}<|im_end|>"
    )


def main():
    parser = argparse.ArgumentParser(description="准备公开摘要训练数据")
    parser.add_argument("--max_samples", type=int, default=1000000,
                        help="总计最大样本量 (默认100万, 0=全量不限)")
    parser.add_argument("--output_dir", default=None,
                        help="输出目录 (默认为 distill 同级的 public_data/)")
    parser.add_argument("--val_ratio", type=float, default=0.02,
                        help="验证集比例 (默认2%)")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    random.seed(args.seed)

    # 输出路径
    if args.output_dir is None:
        args.output_dir = os.path.join(os.path.dirname(__file__), "..", "public_data")
    os.makedirs(args.output_dir, exist_ok=True)

    # 全量加载所有数据源 (不限单源配额, 总控由 max_samples 截断)
    no_limit = args.max_samples if args.max_samples > 0 else 999999999
    all_samples = []
    for name, loader in [
        ("LCSTS", load_lcsts),
        ("CSL", load_csl),
        ("XLSum", load_xlsum_chinese),
        ("NLPCC", load_nlpcc),
    ]:
        loaded = loader(no_limit)
        log.info("  %s: %d 条", name, len(loaded))
        all_samples.extend(loaded)
    log.info("全量加载: %d 条", len(all_samples))

    if not all_samples:
        log.error("没有加载到任何数据!")
        sys.exit(1)

    # 去重 (按文本的简�?hash)
    seen = set()
    deduped = []
    for s in all_samples:
        h = hash(s["text"][:100])
        if h not in seen:
            seen.add(h)
            deduped.append(s)
    log.info("去重后: %d 条 (移除 %d 条重复)",
             len(deduped), len(all_samples) - len(deduped))

    # 打乱
    random.shuffle(deduped)

    # 总控截断 (全量加载后按 max_samples 截取)
    if args.max_samples > 0 and len(deduped) > args.max_samples:
        deduped = deduped[:args.max_samples]
        log.info("截断至: %d 条", args.max_samples)

    # 拆分训练/验证
    val_count = max(1000, int(len(deduped) * args.val_ratio))
    train_samples = deduped[val_count:]
    val_samples = deduped[:val_count]

    # 格式化并写入
    def write_jsonl(samples, path):
        formatted = []
        for s in samples:
            formatted.append({"text": format_chat_template(s)})
        with open(path, "w", encoding="utf-8") as f:
            for item in formatted:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        log.info("写入 %s: %d 条", path, len(formatted))

    train_path = os.path.join(args.output_dir, "public_summary_train.jsonl")
    val_path = os.path.join(args.output_dir, "public_summary_val.jsonl")
    write_jsonl(train_samples, train_path)
    write_jsonl(val_samples, val_path)

    log.info("完成! 训练集: %d 条, 验证集: %d �?",
             len(train_samples), len(val_samples))


if __name__ == "__main__":
    main()
