#!/usr/bin/env python3
"""
LoRA 微调搜索摘要模型
用法: python finetune_summary.py --data training_data.jsonl --epochs 3

训练数据格式 (JSONL, 每行一条):
  {"keyword": "猫咪", "summary": "关于猫咪的搜索结果, 包含..."}

首次运行需安装: pip install peft accelerate bitsandbytes
"""

import argparse
import json
import logging
import os
import torch
from datasets import Dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    BitsAndBytesConfig,
)
from peft import LoraConfig, get_peft_model, TaskType, PeftModel

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("finetune")

BASE_MODEL = "Qwen/Qwen2.5-3B-Instruct"
OUTPUT_DIR = "summary_lora"
PROMPT = """你是一个视频平台的搜索助手。用户搜索了关键词"{keyword}"。

根据平台已有的内容数据，生成一段简洁的搜索摘要，描述可能找到什么类型的相关内容，并给用户一些浏览建议。

要求：总共控制在10行以内，不要使用emoji或特殊符号，语言自然流畅。"""


def load_training_data(path):
    """加载 JSONL 训练数据"""
    examples = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            keyword = item["keyword"].strip()
            summary = item["summary"].strip()
            if keyword and summary:
                examples.append({"keyword": keyword, "summary": summary})
    log.info("加载 %d 条训练数据", len(examples))
    return examples


def format_example(item, tokenizer):
    """格式化为 chat template"""
    user = PROMPT.format(keyword=item["keyword"])
    messages = [
        {"role": "user", "content": user},
        {"role": "assistant", "content": item["summary"]},
    ]
    return tokenizer.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=False
    )


def train(data_path, epochs, lora_r, output_dir):
    examples = load_training_data(data_path)
    if len(examples) < 10:
        log.warning("训练数据不足 (需要至少 10 条), 退出")
        return False

    log.info("加载基础模型: %s", BASE_MODEL)
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, trust_remote_code=True)

    # 格式化所有样本
    texts = [format_example(e, tokenizer) for e in examples]
    dataset = Dataset.from_dict({"text": texts})

    def tokenize_fn(batch):
        result = tokenizer(
            batch["text"], truncation=True, max_length=512, padding=False
        )
        result["labels"] = result["input_ids"].copy()
        return result

    dataset = dataset.map(tokenize_fn, batched=True, remove_columns=["text"])

    # 根据是否有 GPU 选择加载方式
    use_cuda = torch.cuda.is_available()
    if use_cuda:
        log.info("GPU 可用, 使用 4-bit 量化 + LoRA")
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
        )
        model = AutoModelForCausalLM.from_pretrained(
            BASE_MODEL,
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=True,
        )
    else:
        log.info("CPU 模式 (速度较慢)")
        model = AutoModelForCausalLM.from_pretrained(
            BASE_MODEL, trust_remote_code=True
        )

    # LoRA 配置
    lora_config = LoraConfig(
        r=lora_r,
        lora_alpha=lora_r * 2,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # 训练参数
    training_args = TrainingArguments(
        output_dir=output_dir,
        num_train_epochs=epochs,
        per_device_train_batch_size=1,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        warmup_steps=10,
        logging_steps=5,
        save_strategy="epoch",
        fp16=use_cuda,
        report_to="none",
        remove_unused_columns=False,
    )

    from transformers import Trainer
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=dataset,
        tokenizer=tokenizer,
    )

    log.info("开始训练...")
    trainer.train()

    # 保存 LoRA 适配器
    adapter_dir = os.path.join(output_dir, "adapter")
    model.save_pretrained(adapter_dir)
    tokenizer.save_pretrained(adapter_dir)
    log.info("LoRA 适配器已保存到: %s", adapter_dir)

    # 同时保存一份到默认位置供 search_summary.py 加载
    default_adapter = os.path.join(os.path.dirname(__file__), "summary_lora_adapter")
    model.save_pretrained(default_adapter)
    tokenizer.save_pretrained(default_adapter)
    log.info("已复制到默认位置: %s", default_adapter)

    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LoRA 微调搜索摘要模型")
    parser.add_argument("--data", required=True, help="训练数据 JSONL 文件")
    parser.add_argument("--epochs", type=int, default=3, help="训练轮数")
    parser.add_argument("--lora-r", type=int, default=8, help="LoRA rank (越大效果越好但越慢)")
    parser.add_argument("--output", default=OUTPUT_DIR, help="输出目录")
    args = parser.parse_args()

    ok = train(args.data, args.epochs, args.lora_r, args.output)
    if ok:
        log.info("微调完成! 适配器保存在: %s/adapter", args.output)
    else:
        log.error("微调失败")
