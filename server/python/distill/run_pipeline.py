#!/usr/bin/env python3
"""
蒸馏流水线入口 — 一键执行全流程

用法:
  # 完整流程
  python -m distill.run_pipeline --full

  # 分步执行
  python -m distill.run_pipeline --step generate   # 只生成训练数据
  python -m distill.run_pipeline --step clean       # 只清洗数据
  python -m distill.run_pipeline --step train       # 只训练
  python -m distill.run_pipeline --step serve       # 启动推理服务

环境准备:
  pip install pymysql httpx transformers peft bitsandbytes datasets accelerate torch numpy
  pip install flash-attn --no-build-isolation  # 可选, 加速训练
"""

import argparse
import logging
import sys

from .config import SEED_KEYWORDS, DATA_CONFIG, TRAIN_CONFIG

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("pipeline")


def step_generate():
    """步骤1+2: 数据库上下文构建 + 教师模型数据生成"""
    from .context_builder import ContextBuilder
    from .teacher_generator import TeacherGenerator

    log.info("=" * 60)
    log.info("Step 1-2: 构建上下文 + 教师模型生成训练数据")
    log.info("=" * 60)

    builder = ContextBuilder()
    generator = TeacherGenerator()

    n = generator.generate_samples(
        keywords=SEED_KEYWORDS,
        context_builder=builder,
        total_samples=DATA_CONFIG["total_samples"],
        output_path=DATA_CONFIG["training_data_path"],
    )

    log.info("生成完成: %d 条训练样本", n)
    return n > 0


def step_clean():
    """步骤3: 数据清洗 + 划分训练/验证集"""
    from .data_cleaner import DataCleaner

    log.info("=" * 60)
    log.info("Step 3: 数据清洗与预处理")
    log.info("=" * 60)

    cleaner = DataCleaner()
    train_path, val_path = cleaner.clean()

    if not train_path or not val_path:
        log.error("清洗后无有效数据!")
        return False

    log.info("训练集: %s", train_path)
    log.info("验证集: %s", val_path)
    return True


def step_train(train_data: str = None, val_data: str = None):
    """步骤4: QLoRA 蒸馏训练"""
    from .train_distill import DistillTrainer

    log.info("=" * 60)
    log.info("Step 4: QLoRA 蒸馏训练")
    log.info("=" * 60)

    if not train_data:
        train_data = DATA_CONFIG["cleaned_data_path"].replace(".jsonl", "_train.jsonl")
    if not val_data:
        val_data = DATA_CONFIG["cleaned_data_path"].replace(".jsonl", "_val.jsonl")

    trainer = DistillTrainer()
    train_ds, val_ds = trainer.load_data(train_data, val_data)
    trainer.load_model()
    adapter_dir = trainer.train(train_ds, val_ds)

    log.info("适配器已保存: %s", adapter_dir)
    return True


def step_serve():
    """步骤5: 启动推理服务"""
    from .summary_service import SummaryService

    log.info("=" * 60)
    log.info("Step 5: 启动推理服务")
    log.info("=" * 60)

    service = SummaryService()
    service.serve()


def main():
    parser = argparse.ArgumentParser(description="搜索摘要蒸馏流水线")
    parser.add_argument("--full", action="store_true", help="执行完整流程")
    parser.add_argument("--step", choices=["generate", "clean", "train", "serve"],
                        help="执行单个步骤")
    parser.add_argument("--train_data", default=None, help="训练集路径 (step=train)")
    parser.add_argument("--val_data", default=None, help="验证集路径 (step=train)")
    parser.add_argument("--samples", type=int, default=None, help="目标样本数 (覆盖 config)")
    args = parser.parse_args()

    if args.samples:
        DATA_CONFIG["total_samples"] = args.samples

    if args.full:
        steps = ["generate", "clean", "train"]
    elif args.step:
        steps = [args.step]
    else:
        parser.print_help()
        return

    for step in steps:
        if step == "generate":
            if not step_generate():
                log.error("数据生成失败, 终止流水线")
                return
        elif step == "clean":
            if not step_clean():
                log.error("数据清洗失败")
                return
        elif step == "train":
            if not step_train(args.train_data, args.val_data):
                log.error("训练失败")
                return
        elif step == "serve":
            step_serve()

    log.info("流水线完成!")


if __name__ == "__main__":
    main()
