#!/usr/bin/env python3
"""
蒸馏模型测试脚本 — 在 5 个不同搜索词上对比教师模型与蒸馏模型输出

用法:
  python -m distill.test_distill
"""

import json
import logging
import time
from dataclasses import dataclass

from .config import SEED_KEYWORDS

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("test")

# 测试关键词 — 覆盖不同场景
TEST_KEYWORDS = [
    "美食教程",       # 热门品类, 有真实视频
    "冷门电影推荐",    # 长尾搜索, 可能无结果
    "猫咪搞笑视频",    # 萌宠类
    "编程入门",        # 知识类
    "直播带货",        # 商品/直播相关
]


@dataclass
class TestCase:
    keyword: str
    context: dict
    teacher_summary: str = ""
    distill_summary: str = ""
    teacher_time_ms: float = 0
    distill_time_ms: float = 0


def run_tests():
    """执行全部测试并输出报告"""
    log.info("=" * 70)
    log.info("蒸馏模型对比测试")
    log.info("=" * 70)

    # 1. 构建搜索上下文
    from .context_builder import ContextBuilder

    log.info("\n[1/3] 从数据库构建测试上下文...")
    builder = ContextBuilder()
    test_cases = []

    for kw in TEST_KEYWORDS:
        try:
            ctx = builder.build(kw)
            test_cases.append(TestCase(keyword=kw, context=ctx))
            log.info("  %s → %d 条真实视频", kw, ctx["totalVideos"])
        except Exception as e:
            log.warning("  构建上下文失败 %s: %s", kw, e)
            test_cases.append(TestCase(keyword=kw, context={"keyword": kw, "videos": [], "users": []}))

    # 2. 教师模型生成 (DeepSeek API)
    from .teacher_generator import TeacherGenerator

    log.info("\n[2/3] 教师模型 (DeepSeek) 生成摘要...")
    teacher = TeacherGenerator()

    for tc in test_cases:
        try:
            t0 = time.time()
            summary = teacher._generate_summary(tc.context, style="专业简洁",
                                                desc="专业简洁，数据清晰")
            tc.teacher_time_ms = (time.time() - t0) * 1000
            tc.teacher_summary = summary or "[API 调用失败]"
            log.info("  %s → %d chars (%.0fms)", tc.keyword,
                     len(tc.teacher_summary), tc.teacher_time_ms)
        except Exception as e:
            tc.teacher_summary = f"[错误] {e}"
            log.error("  %s 教师模型失败: %s", tc.keyword, e)

    # 3. 蒸馏模型生成
    from .summary_service import SummaryService

    log.info("\n[3/3] 蒸馏模型 (Qwen2.5-7B-QLoRA) 生成摘要...")

    try:
        service = SummaryService()
        service.load()
    except Exception as e:
        log.error("蒸馏模型加载失败: %s", e)
        log.warning("将跳过蒸馏模型测试 (可能需要先运行训练)")
        service = None

    if service:
        for tc in test_cases:
            try:
                t0 = time.time()
                tc.distill_summary = service.generate(tc.keyword, tc.context)
                tc.distill_time_ms = (time.time() - t0) * 1000
                log.info("  %s → %d chars (%.0fms)", tc.keyword,
                         len(tc.distill_summary), tc.distill_time_ms)
            except Exception as e:
                tc.distill_summary = f"[错误] {e}"
                log.error("  %s 蒸馏模型失败: %s", tc.keyword, e)

    # 4. 输出对比报告
    print("\n" + "=" * 70)
    print("对比报告")
    print("=" * 70)

    for i, tc in enumerate(test_cases):
        print(f"\n{'─' * 60}")
        print(f"测试 {i + 1}: 「{tc.keyword}」")
        print(f"  真实视频数: {tc.context.get('totalVideos', 0)}")
        print(f"  需扩增: {'是' if tc.context.get('need_augment', True) else '否'}")

        print(f"\n  [教师模型 DeepSeek] ({tc.teacher_time_ms:.0f}ms)")
        print(f"  {'─' * 50}")
        teacher_preview = tc.teacher_summary[:300]
        for line in teacher_preview.split("\n")[:5]:
            print(f"  | {line[:80]}")
        if len(tc.teacher_summary) > 300:
            print(f"  | ... (共 {len(tc.teacher_summary)} 字)")

        if tc.distill_summary:
            print(f"\n  [蒸馏模型 Qwen2.5-7B] ({tc.distill_time_ms:.0f}ms)")
            print(f"  {'─' * 50}")
            distill_preview = tc.distill_summary[:300]
            for line in distill_preview.split("\n")[:5]:
                print(f"  | {line[:80]}")
            if len(tc.distill_summary) > 300:
                print(f"  | ... (共 {len(tc.distill_summary)} 字)")
        else:
            print(f"\n  [蒸馏模型] 未加载, 请先运行训练")

    # 5. 统计摘要
    print(f"\n{'=' * 70}")
    print("统计摘要")
    print(f"{'=' * 70}")

    teacher_ok = [tc for tc in test_cases if tc.teacher_summary and "错误" not in tc.teacher_summary]
    distill_ok = [tc for tc in test_cases if tc.distill_summary and "错误" not in tc.distill_summary]

    print(f"  教师模型成功数: {len(teacher_ok)}/{len(test_cases)}")
    if teacher_ok:
        avg_time = sum(tc.teacher_time_ms for tc in teacher_ok) / len(teacher_ok)
        avg_len = sum(len(tc.teacher_summary) for tc in teacher_ok) / len(teacher_ok)
        print(f"  教师平均耗时: {avg_time:.0f}ms")
        print(f"  教师平均长度: {avg_len:.0f} 字")

    print(f"  蒸馏模型成功数: {len(distill_ok)}/{len(test_cases)}")
    if distill_ok:
        avg_time = sum(tc.distill_time_ms for tc in distill_ok) / len(distill_ok)
        avg_len = sum(len(tc.distill_summary) for tc in distill_ok) / len(distill_ok)
        print(f"  蒸馏平均耗时: {avg_time:.0f}ms (目标 < 3000ms)")
        print(f"  蒸馏平均长度: {avg_len:.0f} 字")


if __name__ == "__main__":
    run_tests()
