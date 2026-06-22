"""
两阶段蒸馏训练编配器

阶段 1 (便宜): 公开摘要数�?(LCSTS+CSL+XLSum) → 训中文摘要语感
阶段 2 (贵): DeepSeek蒸馏数据 → 训搜索分析能力

AutoDL 推荐配置:
  - 阶段1: RTX 4090 24G, ~4-6小时, ~10-20�?
  - 阶段2: A100 40G, ~2-4小时, ~30-50�?

两阶段总成本: ~50-100�?

用法 (AutoDL 上一键运�?:
  # 先准备数据
  python -m distill.prepare_public_data --max_samples 200000

  # 生成蒸馏数据 (如果有新关键词想覆盖)
  python -m distill.run_pipeline --keywords_file new_keywords.txt

  # 两阶段训练
  python -m distill.train_two_stage \
    --stage1_data public_data/public_summary_train.jsonl \
    --stage1_val public_data/public_summary_val.jsonl \
    --stage2_data training_data_cleaned_train.jsonl \
    --stage2_val training_data_cleaned_val.jsonl \
    --base_model Qwen/Qwen3-8B-Instruct
"""

import argparse
import json
import logging
import os
import sys
import time

import torch
from datasets import Dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    BitsAndBytesConfig,
    Trainer,
    DataCollatorForSeq2Seq,
)
from peft import LoraConfig, get_peft_model, PeftModel, TaskType, prepare_model_for_kbit_training

from .config import TRAIN_CONFIG

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

# ============================================================================
# 场�?
# ============================================================================

STAGE1_CONFIG = {
    "description": "阶段1: 公开摘要数据 → 中文摘要语感",
    "num_epochs": 3,
    "learning_rate": 2e-4,
    "lora_r": 32,           # 阶段1 用一半 rank, 预留容量给阶段2
    "lora_alpha": 64,
    "max_input_length": 768,
    "adapter_subdir": "stage1_public_adapter",
}

STAGE2_CONFIG = {
    "description": "阶段2: DeepSeek蒸馏数据 → 搜索分析能�?,
    "num_epochs": 5,
    "learning_rate": 1e-4,  # 阶段2 学习率更低, 精细调�?
    "lora_r": 64,            # 全文 rank (stage1 的 adapter 会被合并再�?train)
    "lora_alpha": 128,
    "max_input_length": 1024,
    "adapter_subdir": "stage2_search_adapter",
}


# ============================================================================
# 数据加载
# ============================================================================

def load_jsonl(path: str) -> list[dict]:
    samples = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            samples.append(json.loads(line))
    return samples


def format_sample_qwen(sample: dict, tokenizer=None) -> str:
    """将样本格式化为 Qwen Chat Template

    支持两种输入格�?
    1. 公开数据: {"text": "<|im_start|>...<|im_end|>"} (已预格�?)
    2. 蒸馏数据: {"context": {...}, "summary": "..."}
    """
    # 公开数据: 已经预格式化
    if "text" in sample and "<|im_start|>" in sample["text"]:
        return sample["text"]

    # 蒸馏数据: 现场构�?
    ctx = sample.get("context", {})
    summary = sample.get("summary", "")

    kw = ctx.get("keyword", sample.get("keyword", ""))
    videos = ctx.get("videos", [])
    users = ctx.get("users", [])

    parts = [f"搜索词：{kw}"]

    if videos:
        parts.append(f"\n匹配到 {len(videos)} 个相关内容：")
        for v in videos[:12]:
            parts.append(
                f"- {v.get('title', '')[:60]} "
                f"({v.get('likes', 0)}赞 | {v.get('plays', 0)}播放)"
            )
    else:
        parts.append("\n暂未匹配到相关内容")

    if users:
        top_users = sorted(users, key=lambda u: u.get("followerCount", 0), reverse=True)
        parts.append(f"\n相关创作者: {', '.join(u['name'] for u in top_users[:5])}")

    user_content = "\n".join(parts)

    messages = [
        {"role": "system", "content": "你是SeekFlow视频平台的搜索助手。请根据收到的平台数据，生成详细的搜索摘要。"},
        {"role": "user", "content": user_content},
        {"role": "assistant", "content": summary},
    ]

    if tokenizer:
        try:
            return tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=False
            )
        except Exception:
            pass

    return (
        f"<|im_start|>system\n你是SeekFlow视频平台的搜索助手。<|im_end|>\n"
        f"<|im_start|>user\n{user_content}<|im_end|>\n"
        f"<|im_start|>assistant\n{summary}<|im_end|>"
    )


# ============================================================================
# 模型工具
# ============================================================================

def load_base_model(model_name: str, use_flash_attn: bool = True):
    """加载 4-bit 量化基础模型 (不含 LoRA)"""
    log.info("加载基础模型: %s (QLoRA 4-bit)", model_name)

    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16,
    )

    load_kwargs = {
        "quantization_config": bnb_config,
        "device_map": "auto",
        "trust_remote_code": True,
    }

    if use_flash_attn:
        try:
            import flash_attn  # noqa: F401
            load_kwargs["attn_implementation"] = "flash_attention_2"
            log.info("已启用 FlashAttention-2")
        except ImportError:
            log.warning("flash-attn 未安装, 使用 SDPA")

    model = AutoModelForCausalLM.from_pretrained(model_name, **load_kwargs)
    model = prepare_model_for_kbit_training(model)

    return model, tokenizer


def apply_lora(model, r: int, alpha: int, target_modules: list[str] = None):
    """给模型附加 LoRA 适配器"""
    if target_modules is None:
        target_modules = ["q_proj", "k_proj", "v_proj", "o_proj",
                          "gate_proj", "up_proj", "down_proj"]

    lora_config = LoraConfig(
        r=r,
        lora_alpha=alpha,
        target_modules=target_modules,
        lora_dropout=0.05,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
    )
    model = get_peft_model(model, lora_config)
    model.config.use_cache = False
    model.enable_input_require_grads()
    model.print_trainable_parameters()
    return model


# ============================================================================
# 训练执行
# ============================================================================

def run_training(
    model,
    tokenizer,
    train_path: str,
    val_path: str,
    output_dir: str,
    config: dict,
    base_config: dict,
):
    """执行一次训练, 保存 LoRA 适配器"""
    # 加载原始数据
    train_raw = load_jsonl(train_path)
    val_raw = load_jsonl(val_path)
    log.info("%s: train=%d val=%d", config["description"], len(train_raw), len(val_raw))

    # 格式�?
    train_texts = [format_sample_qwen(s, tokenizer) for s in train_raw]
    val_texts = [format_sample_qwen(s, tokenizer) for s in val_raw]

    train_ds = Dataset.from_dict({"text": train_texts})
    val_ds = Dataset.from_dict({"text": val_texts})

    # Tokenize
    max_len = config.get("max_input_length", base_config["max_input_length"])

    def tokenize_fn(examples):
        result = tokenizer(
            examples["text"],
            truncation=True,
            max_length=max_len,
            padding=False,
        )
        result["labels"] = result["input_ids"].copy()
        return result

    log.info("Tokenization (max_length=%d)...", max_len)
    train_ds = train_ds.map(tokenize_fn, batched=True, remove_columns=["text"])
    val_ds = val_ds.map(tokenize_fn, batched=True, remove_columns=["text"])

    data_collator = DataCollatorForSeq2Seq(
        tokenizer=tokenizer, model=model, padding=True, label_pad_token_id=-100
    )

    os.makedirs(output_dir, exist_ok=True)

    training_args = TrainingArguments(
        output_dir=output_dir,
        num_train_epochs=config["num_epochs"],
        per_device_train_batch_size=base_config["per_device_batch_size"],
        per_device_eval_batch_size=2,
        gradient_accumulation_steps=base_config["gradient_accumulation_steps"],
        learning_rate=config["learning_rate"],
        warmup_ratio=base_config["warmup_ratio"],
        lr_scheduler_type="cosine",
        logging_steps=10,
        eval_steps=50,
        save_strategy="steps",
        save_steps=200,
        eval_strategy="steps",
        bf16=True,
        gradient_checkpointing=True,
        report_to="none",
        remove_unused_columns=False,
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
        save_total_limit=2,
        dataloader_num_workers=0,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        data_collator=data_collator,
        tokenizer=tokenizer,
    )

    log.info("开始训练: %s (epochs=%d, lr=%.1e, batch=%d, grad_accum=%d)...",
             config["description"],
             config["num_epochs"],
             config["learning_rate"],
             base_config["per_device_batch_size"],
             base_config["gradient_accumulation_steps"])

    t0 = time.time()
    train_result = trainer.train()
    elapsed = (time.time() - t0) / 60
    log.info("训练完成, 耗时 %.1f min", elapsed)

    # 保存指标
    metrics = {
        "stage": config["description"],
        "train_loss": float(train_result.training_loss) if train_result else 0,
        "eval_loss": float(trainer.state.best_metric) if trainer.state.best_metric else 0,
        "epoch": float(train_result.metrics.get("epoch", 0)) if train_result else 0,
        "elapsed_min": round(elapsed, 1),
    }
    metrics_path = os.path.join(output_dir, "train_metrics.json")
    with open(metrics_path, "w") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)
    log.info("训练指标: %s", json.dumps(metrics, indent=2, ensure_ascii=False))

    return trainer, metrics


# ============================================================================
# 主流程
# ============================================================================

def main():
    parser = argparse.ArgumentParser(description="两阶段蒸馏训练编配器")
    parser.add_argument("--stage1_data", required=True, help="阶段1 公开数据训练集 JSONL")
    parser.add_argument("--stage1_val", required=True, help="阶段1 公开数据验证集 JSONL")
    parser.add_argument("--stage2_data", required=True, help="阶段2 蒸馏数据训练集 JSONL")
    parser.add_argument("--stage2_val", required=True, help="阶段2 蒸馏数据验证集 JSONL")
    parser.add_argument("--base_model", default="Qwen/Qwen3-8B-Instruct",
                        help="基础模型 (默认 Qwen3-8B)")
    parser.add_argument("--output_dir", default=None,
                        help="输出根目录 (默认为 distill_output)")
    parser.add_argument("--skip_stage1", action="store_true",
                        help="跳过阶段1 (仅执行阶段2)")
    parser.add_argument("--stage1_only", action="store_true",
                        help="仅执行阶段1")
    parser.add_argument("--stage1_adapter", default=None,
                        help="已有的阶段1适配器路径 (跳过阶段1训�?)
    args = parser.parse_args()

    cfg = TRAIN_CONFIG.copy()
    cfg["base_model"] = args.base_model

    if args.output_dir is None:
        args.output_dir = cfg["output_dir"]
    os.makedirs(args.output_dir, exist_ok=True)

    # 阶段1 输出路�?
    stage1_output = os.path.join(args.output_dir, STAGE1_CONFIG["adapter_subdir"])
    # 阶段2 输出路�?
    stage2_output = cfg["adapter_dir"]  # 用 config 中的最终适配器路�?

    # ========================================================================
    # 阶段 1: 公开数据预训�?
    # ========================================================================
    stage1_adapter = args.stage1_adapter

    if not args.skip_stage1 and not stage1_adapter:
        log.info("=" * 60)
        log.info("阶段 1: 公开摘要数据预训�?")
        log.info("=" * 60)

        model, tokenizer = load_base_model(args.base_model)
        model = apply_lora(
            model,
            r=STAGE1_CONFIG["lora_r"],
            alpha=STAGE1_CONFIG["lora_alpha"],
        )

        trainer, metrics = run_training(
            model, tokenizer,
            args.stage1_data, args.stage1_val,
            output_dir=stage1_output,
            config=STAGE1_CONFIG,
            base_config=cfg,
        )

        # 保存阶段1适配�?
        trainer.save_model(stage1_output)
        tokenizer.save_pretrained(stage1_output)
        log.info("阶段1适配器已保存: %s", stage1_output)

        # 释放模型
        del model, trainer
        torch.cuda.empty_cache()

        stage1_adapter = stage1_output
    elif stage1_adapter:
        log.info("使用已有阶段1适配器: %s", stage1_adapter)

    if args.stage1_only:
        log.info("仅执行阶段1, 完成!")
        print(f"\n阶段1适配器: {stage1_adapter}")
        return

    # ========================================================================
    # 阶段 2: 搜索摘要蒸馏
    # ========================================================================
    log.info("=" * 60)
    log.info("阶段 2: 搜索摘要蒸馏")
    log.info("=" * 60)

    # 重新加载基础模�?
    model, tokenizer = load_base_model(args.base_model)

    # 加载阶段1适配器 (如果�?
    if stage1_adapter and os.path.isdir(stage1_adapter):
        log.info("加载阶段1适配器: %s", stage1_adapter)
        model = PeftModel.from_pretrained(model, stage1_adapter, is_trainable=True)
        log.info("阶段1适配器已加载, 继续训练")

    # 应用阶段2 LoRA 配置 (如果是全新模型, 需要先 attach LoRA)
    # PeftModel 已经 attach 了 LoRA, 不需要重�?apply
    # 但如果 stage1 被跳过了, 就需要 attach
    if not isinstance(model, PeftModel):
        model = apply_lora(
            model,
            r=STAGE2_CONFIG["lora_r"],
            alpha=STAGE2_CONFIG["lora_alpha"],
        )

    trainer, metrics = run_training(
        model, tokenizer,
        args.stage2_data, args.stage2_val,
        output_dir=stage2_output,
        config=STAGE2_CONFIG,
        base_config=cfg,
    )

    # 保存最终适配器
    trainer.save_model(stage2_output)
    tokenizer.save_pretrained(stage2_output)
    log.info("最终适配器已保存: %s", stage2_output)

    # 尝试合并权重
    log.info("合并 LoRA 权重...")
    try:
        merged_model = model.merge_and_unload()
        merged_dir = os.path.join(args.output_dir, "merged_model")
        merged_model.save_pretrained(merged_dir, safe_serialization=True)
        tokenizer.save_pretrained(merged_dir)
        log.info("合并模型已保存: %s", merged_dir)
    except Exception as e:
        log.warning("合并权重失败 (可能内存不足): %s", e)

    # 释放
    del model, trainer
    torch.cuda.empty_cache()

    log.info("=" * 60)
    log.info("两阶段训练完成!")
    log.info("  阶段1适配器: %s", stage1_adapter)
    log.info("  阶段2适配器: %s", stage2_output)
    log.info("=" * 60)

    print(f"\n两阶段训练完成!")
    print(f"  阶段1适配器: {stage1_adapter}")
    print(f"  最终适配器: {stage2_output}")


if __name__ == "__main__":
    main()
