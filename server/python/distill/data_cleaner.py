"""
任务3: 数据清洗与预处理

对教师模型生成的 JSONL 进行质量过滤, 输出干净的训练/验证集。

过滤规则:
1. 格式校验 — 必填字段完整性
2. 长度检查 — 摘要 300-800 中文字符
3. 矛盾检测 — 视频数=0 但摘要声称有内容
4. 编辑距离去重 — 同关键词多个摘要间避免高度重复
5. 黑名单词过滤 — 水印/广告检测
6. 数值截断 — likes/plays 超出 2σ 则钳制
"""

import json
import logging
import os
import re
import random
from collections import Counter, defaultdict

import numpy as np

from .config import DATA_CONFIG

log = logging.getLogger(__name__)

# 广告/水印黑名单
BLACKLIST_WORDS = [
    "点击链接", "加微信", "扫码下载", "点击下载", "复制口令",
    "打开淘宝", "打开拼多多", "关注公众号", "VX:", "QQ群",
    "http://", "https://", "www.", ".com", "加Q",
]

# 必填字段
REQUIRED_FIELDS = {"keyword", "context", "summary"}
REQUIRED_CONTEXT_FIELDS = {"keyword", "videos", "users", "aggregations", "platformStats"}


class DataCleaner:
    """训练数据清洗器"""

    def __init__(self, input_path: str = None, output_path: str = None):
        self.input_path = input_path or DATA_CONFIG["training_data_path"]
        self.output_path = output_path or DATA_CONFIG["cleaned_data_path"]
        self.stats = Counter()

    # ------------------------------------------------------------------
    # 公开方法
    # ------------------------------------------------------------------

    def clean(self) -> tuple[str, str]:
        """主入口: 清洗 → 划分训练/验证集 → 写入文件"""
        log.info("开始数据清洗: %s", self.input_path)

        # 加载全部样本
        raw = self._load_jsonl(self.input_path)
        log.info("原始样本: %d 条", len(raw))
        self.stats["raw"] = len(raw)

        # 逐步过滤
        samples = self._filter_format(raw)
        samples = self._filter_length(samples)
        samples = self._filter_contradiction(samples)
        samples = self._filter_blacklist(samples)
        samples = self._deduplicate(samples)
        samples = self._clip_numerics(samples)

        log.info("清洗后样本: %d 条 (过滤率 %.1f%%)",
                 len(samples), (1 - len(samples) / max(self.stats["raw"], 1)) * 100)

        # 分层抽样划分
        random.seed(42)
        random.shuffle(samples)

        # 按关键词类别分组以确保分布均匀
        train_set, val_set = self._stratified_split(samples, val_ratio=0.1)

        # 写入
        train_path = self.output_path.replace(".jsonl", "_train.jsonl")
        val_path = self.output_path.replace(".jsonl", "_val.jsonl")

        self._write_jsonl(train_path, train_set)
        self._write_jsonl(val_path, val_set)

        log.info("训练集: %d 条 → %s", len(train_set), train_path)
        log.info("验证集: %d 条 → %s", len(val_set), val_path)

        # 统计报告
        self._print_stats()

        return train_path, val_path

    # ------------------------------------------------------------------
    # 过滤步骤
    # ------------------------------------------------------------------

    def _filter_format(self, samples: list[dict]) -> list[dict]:
        """1. 格式校验: 必填字段完整 + JSON 结构正确"""
        kept = []
        for s in samples:
            ok = True
            # 顶层字段
            missing = REQUIRED_FIELDS - set(s.keys())
            if missing:
                self.stats["missing_fields"] += 1
                ok = False

            # context 字段
            ctx = s.get("context", {})
            if isinstance(ctx, dict):
                ctx_missing = REQUIRED_CONTEXT_FIELDS - set(ctx.keys())
                if ctx_missing:
                    self.stats["missing_ctx_fields"] += 1
                    ok = False
            else:
                self.stats["invalid_context"] += 1
                ok = False

            if ok:
                kept.append(s)
        self.stats["format_ok"] = len(kept)
        log.info("格式校验: %d → %d (过滤 %d)",
                 len(samples), len(kept), len(samples) - len(kept))
        return kept

    def _filter_length(self, samples: list[dict]) -> list[dict]:
        """2. 摘要长度检查: 中文字符 300-800"""
        kept = []
        for s in samples:
            summary = s.get("summary", "")
            cn_chars = self._count_chinese(summary)
            if 300 <= cn_chars <= 800:
                kept.append(s)
            else:
                self.stats["length_out_of_range"] += 1
                if cn_chars < 100:
                    self.stats["too_short"] += 1
                elif cn_chars > 1000:
                    self.stats["too_long"] += 1
        self.stats["length_ok"] = len(kept)
        log.info("长度检查: %d → %d (过滤 %d, 太短:%d 太长:%d)",
                 len(samples), len(kept), len(samples) - len(kept),
                 self.stats["too_short"], self.stats["too_long"])
        return kept

    def _filter_contradiction(self, samples: list[dict]) -> list[dict]:
        """3. 矛盾检测: 视频数=0 但摘要声称有内容"""
        kept = []
        for s in samples:
            ctx = s.get("context", {})
            total_videos = ctx.get("totalVideos", len(ctx.get("videos", [])))
            summary = s.get("summary", "")

            if total_videos == 0:
                # 摘要不应包含"以下是/第一个视频/热门内容"等
                positive_patterns = [
                    r"以下是", r"第.*个视频", r"热门内容", r"点赞数", r"播放量",
                    r"例如", r"代表作", r"亮点视频"
                ]
                has_positive = any(re.search(p, summary) for p in positive_patterns)
                has_negative = any(w in summary for w in ["未找到", "暂无", "没有相关", "空空如也", "试试"])

                if has_positive and not has_negative:
                    self.stats["contradiction"] += 1
                    continue
            kept.append(s)
        log.info("矛盾检测: %d → %d (过滤 %d)",
                 len(samples), len(kept), len(samples) - len(kept))
        return kept

    def _filter_blacklist(self, samples: list[dict]) -> list[dict]:
        """4. 黑名单词检测"""
        kept = []
        for s in samples:
            summary = s.get("summary", "")
            if any(w in summary for w in BLACKLIST_WORDS):
                self.stats["blacklist_hit"] += 1
                continue
            kept.append(s)
        log.info("黑名单检测: %d → %d (过滤 %d)",
                 len(samples), len(kept), len(samples) - len(kept))
        return kept

    def _deduplicate(self, samples: list[dict]) -> list[dict]:
        """5. 同关键词多风格摘要去重 — 编辑距离太近的丢弃"""
        # 按关键词分组
        by_kw = defaultdict(list)
        for s in samples:
            by_kw[s["keyword"]].append(s)

        kept = []
        for kw, group in by_kw.items():
            if len(group) <= 1:
                kept.extend(group)
                continue

            # 按摘要长度排序, 保留最长的 N 条, 其余检查编辑距离
            group.sort(key=lambda x: len(x["summary"]), reverse=True)
            selected = [group[0]]
            for s in group[1:]:
                # 与已选中的每条比较
                dup = False
                for sel in selected:
                    dist = self._edit_distance(s["summary"], sel["summary"])
                    max_len = max(len(s["summary"]), len(sel["summary"]))
                    similarity = 1 - dist / max(max_len, 1)
                    if similarity > 0.85:
                        dup = True
                        self.stats["near_dup"] += 1
                        break
                if not dup:
                    selected.append(s)
            kept.extend(selected)

        log.info("去重: %d → %d (过滤 %d)", len(samples), len(kept), len(samples) - len(kept))
        return kept

    def _clip_numerics(self, samples: list[dict]) -> list[dict]:
        """6. 数值截断: likes/plays 超出 2σ 的钳制到上限"""
        # 收集所有视频的 likes 和 plays
        all_likes = []
        all_plays = []
        for s in samples:
            for v in s.get("context", {}).get("videos", []):
                all_likes.append(v.get("likes", 0))
                all_plays.append(v.get("plays", 0))

        if not all_likes:
            return samples

        likes_arr = np.array(all_likes)
        plays_arr = np.array(all_plays)

        likes_mean, likes_std = likes_arr.mean(), likes_arr.std()
        plays_mean, plays_std = plays_arr.mean(), plays_arr.std()

        likes_cap = int(likes_mean + 2 * likes_std) if likes_std > 0 else int(likes_mean * 3)
        plays_cap = int(plays_mean + 2 * plays_std) if plays_std > 0 else int(plays_mean * 3)

        clipped = 0
        for s in samples:
            for v in s.get("context", {}).get("videos", []):
                if v.get("likes", 0) > likes_cap:
                    v["likes"] = likes_cap
                    clipped += 1
                if v.get("plays", 0) > plays_cap:
                    v["plays"] = plays_cap
                    clipped += 1

        self.stats["numeric_clipped"] = clipped
        log.info("数值截断: likes>%d → %d, plays>%d → %d (钳制 %d 处)",
                 likes_cap, likes_cap, plays_cap, plays_cap, clipped)
        return samples

    # ------------------------------------------------------------------
    # 分层抽样
    # ------------------------------------------------------------------

    def _stratified_split(self, samples: list[dict], val_ratio: float = 0.1):
        """按关键词类别分层抽样, 确保验证集分布均匀"""
        # 关键词类别: 使用标签/品类作为分层依据
        categories = defaultdict(list)
        no_cat = []

        for s in samples:
            ctx = s.get("context", {})
            cats = set()
            for v in ctx.get("videos", []):
                for tag in v.get("tags", []):
                    if len(tag) >= 2:
                        cats.add(tag)
            if cats:
                # 用第一个标签作为类别
                cat = sorted(cats)[0]
                categories[cat].append(s)
            else:
                no_cat.append(s)

        train, val = [], []

        # 每类抽取 val_ratio
        for cat, items in categories.items():
            random.shuffle(items)
            split_idx = max(1, int(len(items) * val_ratio))
            val.extend(items[:split_idx])
            train.extend(items[split_idx:])

        # 无类别样本随机划分
        random.shuffle(no_cat)
        split_idx = max(1, int(len(no_cat) * val_ratio))
        val.extend(no_cat[:split_idx])
        train.extend(no_cat[split_idx:])

        return train, val

    # ------------------------------------------------------------------
    # 工具方法
    # ------------------------------------------------------------------

    def _load_jsonl(self, path: str) -> list[dict]:
        samples = []
        if not os.path.exists(path):
            return samples
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    samples.append(json.loads(line))
                except json.JSONDecodeError:
                    self.stats["json_error"] += 1
        return samples

    def _write_jsonl(self, path: str, samples: list[dict]):
        with open(path, "w", encoding="utf-8") as f:
            for s in samples:
                f.write(json.dumps(s, ensure_ascii=False) + "\n")

    @staticmethod
    def _count_chinese(text: str) -> int:
        """统计中文字符数 (不含标点和空白)"""
        return sum(1 for c in text if '一' <= c <= '鿿')

    @staticmethod
    def _edit_distance(a: str, b: str) -> int:
        """Levenshtein 编辑距离"""
        if len(a) < len(b):
            return DataCleaner._edit_distance(b, a)
        if len(b) == 0:
            return len(a)

        prev = list(range(len(b) + 1))
        for i, ca in enumerate(a):
            curr = [i + 1]
            for j, cb in enumerate(b):
                insert = prev[j + 1] + 1
                delete = curr[j] + 1
                substitute = prev[j] + (0 if ca == cb else 1)
                curr.append(min(insert, delete, substitute))
            prev = curr
        return prev[-1]

    def _print_stats(self):
        log.info("=" * 50)
        log.info("数据清洗统计:")
        for key, val in sorted(self.stats.items()):
            log.info("  %s: %s", key, val)
