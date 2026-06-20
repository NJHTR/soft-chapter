"""
任务4: 蒸馏训练 — QLoRA 微调 Qwen2.5-7B-Instruct

使用教师模型生成的训练数据, 通过 4-bit QLoRA 微调学生模型。
训练完成后合并并保存适配器。

用法:
  python -m distill.train_distill --train_data training_data_cleaned_train.jsonl --val_data training_data_cleaned_val.jsonl
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


class DistillTrainer:
    """QLoRA 蒸馏训练器"""

    def __init__(self, config: dict = None):
        self.cfg = config or TRAIN_CONFIG
        self.tokenizer = None
        self.model = None

    # ------------------------------------------------------------------
    # 数据加载
    # ------------------------------------------------------------------

    def load_data(self, train_path: str, val_path: str) -> tuple[Dataset, Dataset]:
        """加载 JSONL 训练数据, 格式化为 Qwen Chat Template"""

        def load_jsonl(path: str) -> list[dict]:
            samples = []
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    samples.append(json.loads(line))
            return samples

        train_raw = load_jsonl(train_path)
        val_raw = load_jsonl(val_path)
        log.info("加载数据: train=%d val=%d", len(train_raw), len(val_raw))

        # 格式化为 chat template 文本
        train_texts = [self._format_sample(s) for s in train_raw]
        val_texts = [self._format_sample(s) for s in val_raw]

        train_ds = Dataset.from_dict({"text": train_texts})
        val_ds = Dataset.from_dict({"text": val_texts})

        return train_ds, val_ds

    def _format_sample(self, sample: dict) -> str:
        """将样本格式化为 Qwen Chat Template 文本"""
        ctx = sample.get("context", {})
        summary = sample.get("summary", "")

        # 构建用户输入: 精简的上下文描述
        kw = ctx.get("keyword", sample.get("keyword", ""))
        videos = ctx.get("videos", [])
        users = ctx.get("users", [])
        agg = ctx.get("aggregations", {})
        stats = ctx.get("platformStats", {})

        # 简化为紧凑文本
        parts = [f"搜索词：{kw}"]

        if videos:
            parts.append(f"\n匹配到 {len(videos)} 个相关内容：")
            for v in videos[:12]:
                author = v.get("author", {})
                parts.append(
                    f"- {v.get('title','')} "
                    f"({v.get('likes',0)}赞 | {v.get('plays',0)}播放 | "
                    f"作者: {author.get('name','')})"
                )
        else:
            parts.append("\n暂未匹配到相关内容")

        if users:
            parts.append(f"\n相关创作者: {', '.join(u['name'] for u in users[:5])}")

        parts.append(
            f"\n平台数据: {stats.get('totalVideos',0)}个视频, "
            f"{stats.get('totalUsers',0)}位用户"
        )

        user_content = "\n".join(parts)

        if self.tokenizer:
            # 用 tokenizer 的 chat template
            messages = [
                {"role": "system", "content": "你是SeekFlow视频平台的搜索助手。请根据收到的平台数据，生成详细的搜索摘要。"},
                {"role": "user", "content": user_content},
                {"role": "assistant", "content": summary},
            ]
            try:
                return self.tokenizer.apply_chat_template(
                    messages, tokenize=False, add_generation_prompt=False
                )
            except Exception:
                pass

        # 回退: 手动构建格式
        return (
            f"<|im_start|>system\n你是SeekFlow视频平台的搜索助手。<|im_end|>\n"
            f"<|im_start|>user\n{user_content}<|im_end|>\n"
            f"<|im_start|>assistant\n{summary}<|im_end|>"
        )

    # ------------------------------------------------------------------
    # 模型加载
    # ------------------------------------------------------------------

    def load_model(self):
        """加载 4-bit 量化模型 + LoRA 配置"""
        model_name = self.cfg["base_model"]
        log.info("加载基础模型: %s (QLoRA 4-bit)", model_name)

        self.tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)

        # 设置 pad_token (Qwen 没有 pad token, 用 eos)
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
            self.tokenizer.pad_token_id = self.tokenizer.eos_token_id

        # 4-bit 量化配置
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.bfloat16,
        )

        # 加载模型
        load_kwargs = {
            "quantization_config": bnb_config,
            "device_map": "auto",
            "trust_remote_code": True,
        }

        if self.cfg.get("use_flash_attention_2", True):
            try:
                import flash_attn  # noqa: F401
                load_kwargs["attn_implementation"] = "flash_attention_2"
                log.info("已启用 FlashAttention-2")
            except ImportError:
                log.warning("flash-attn 未安装, 使用默认 attention")

        self.model = AutoModelForCausalLM.from_pretrained(model_name, **load_kwargs)

        # 准备 k-bit 训练
        self.model = prepare_model_for_kbit_training(self.model)

        # LoRA 配置
        lora_config = LoraConfig(
            r=self.cfg["lora_r"],
            lora_alpha=self.cfg["lora_alpha"],
            target_modules=self.cfg["lora_target_modules"],
            lora_dropout=self.cfg["lora_dropout"],
            bias="none",
            task_type=TaskType.CAUSAL_LM,
        )
        self.model = get_peft_model(self.model, lora_config)
        self.model.print_trainable_parameters()

        # 梯度检查点
        if self.cfg.get("gradient_checkpointing", True):
            self.model.config.use_cache = False
            self.model.enable_input_require_grads()

        log.info("模型就绪 (QLoRA + LoRA r=%d)", self.cfg["lora_r"])

    # ------------------------------------------------------------------
    # 训练
    # ------------------------------------------------------------------

    def train(self, train_ds: Dataset, val_ds: Dataset):
        """执行 QLoRA 微调"""

        # Tokenize
        def tokenize_fn(examples):
            result = self.tokenizer(
                examples["text"],
                truncation=True,
                max_length=self.cfg["max_input_length"],
                padding=False,
            )
            # labels = input_ids (loss 只算 assistant 部分视为 -100 的掩码在 collator 中处理)
            result["labels"] = result["input_ids"].copy()
            return result

        log.info("开始 tokenization...")
        train_ds = train_ds.map(tokenize_fn, batched=True, remove_columns=["text"])
        val_ds = val_ds.map(tokenize_fn, batched=True, remove_columns=["text"])

        # Data collator: 对 labels 做 padding
        data_collator = DataCollatorForSeq2Seq(
            tokenizer=self.tokenizer,
            model=self.model,
            padding=True,
            label_pad_token_id=-100,
        )

        # 训练参数
        output_dir = self.cfg["output_dir"]
        os.makedirs(output_dir, exist_ok=True)

        training_args = TrainingArguments(
            output_dir=output_dir,
            num_train_epochs=self.cfg["num_epochs"],
            per_device_train_batch_size=self.cfg["per_device_batch_size"],
            per_device_eval_batch_size=2,
            gradient_accumulation_steps=self.cfg["gradient_accumulation_steps"],
            learning_rate=self.cfg["learning_rate"],
            warmup_ratio=self.cfg["warmup_ratio"],
            lr_scheduler_type="cosine",
            logging_steps=self.cfg["logging_steps"],
            eval_steps=self.cfg.get("eval_steps", 100),
            save_strategy=self.cfg["save_strategy"],
            eval_strategy="steps",
            bf16=True,
            gradient_checkpointing=self.cfg.get("gradient_checkpointing", True),
            report_to="none",
            remove_unused_columns=False,
            load_best_model_at_end=True,
            metric_for_best_model="eval_loss",
            greater_is_better=False,
            save_total_limit=3,
            dataloader_num_workers=0,  # Windows 兼容
        )

        trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=train_ds,
            eval_dataset=val_ds,
            data_collator=data_collator,
            tokenizer=self.tokenizer,
        )

        log.info("开始训练 (epochs=%d, batch=%d, grad_accum=%d)...",
                 self.cfg["num_epochs"], self.cfg["per_device_batch_size"],
                 self.cfg["gradient_accumulation_steps"])

        train_result = trainer.train()

        # 保存训练指标
        self._save_metrics(trainer, train_result)

        # 保存 LoRA 适配器
        adapter_dir = self.cfg["adapter_dir"]
        os.makedirs(adapter_dir, exist_ok=True)
        trainer.save_model(adapter_dir)
        self.tokenizer.save_pretrained(adapter_dir)
        log.info("LoRA 适配器已保存: %s", adapter_dir)

        # 合并权重并保存 (可选, 需要更多磁盘空间)
        log.info("合并 LoRA 权重...")
        try:
            merged_model = self.model.merge_and_unload()
            merged_dir = os.path.join(output_dir, "merged_model")
            merged_model.save_pretrained(merged_dir, safe_serialization=True)
            self.tokenizer.save_pretrained(merged_dir)
            log.info("合并模型已保存: %s", merged_dir)
        except Exception as e:
            log.warning("合并权重失败 (可能内存不足): %s", e)

        log.info("训练完成!")
        return adapter_dir

    def _save_metrics(self, trainer, train_result):
        """保存训练日志"""
        metrics_path = os.path.join(self.cfg["output_dir"], "train_metrics.json")
        metrics = {
            "train_loss": float(train_result.training_loss) if train_result else 0,
            "eval_loss": float(trainer.state.best_metric)
            if trainer.state.best_metric else 0,
            "epoch": float(train_result.metrics.get("epoch", 0)) if train_result else 0,
            "train_runtime_sec": float(train_result.metrics.get("train_runtime", 0))
            if train_result else 0,
        }
        with open(metrics_path, "w") as f:
            json.dump(metrics, f, indent=2)
        log.info("训练指标: %s", json.dumps(metrics, indent=2))


def main():
    parser = argparse.ArgumentParser(description="QLoRA 蒸馏训练")
    parser.add_argument("--train_data", required=True, help="训练集 JSONL 路径")
    parser.add_argument("--val_data", required=True, help="验证集 JSONL 路径")
    parser.add_argument("--epochs", type=int, default=None, help="训练轮数 (覆盖 config)")
    args = parser.parse_args()

    trainer = DistillTrainer()

    # 可选: 覆盖 epochs
    if args.epochs:
        trainer.cfg["num_epochs"] = args.epochs

    # 加载数据
    train_ds, val_ds = trainer.load_data(args.train_data, args.val_data)

    # 加载模型
    trainer.load_model()

    # 训练
    adapter_dir = trainer.train(train_ds, val_ds)
    print(f"\n训练完成! 适配器: {adapter_dir}")


if __name__ == "__main__":
    main()
