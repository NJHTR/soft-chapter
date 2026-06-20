#!/usr/bin/env python3
"""
从平台数据库导出搜索摘要训练数据
用法: python export_training_data.py --min-freq 2 --output training_data.jsonl

查询策略:
  - t_search_history: 聚合搜索频率
  - t_video + t_video_content: 匹配视频的元数据、分类、标签
  - 基于真实数据用模板生成摘要, 确保内容准确反映平台状态
"""

import argparse
import json
import logging
import os
import sys
from collections import Counter, defaultdict

import pymysql

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("export-training")

# ========== 数据库配置 (与 application-dev.yml 一致) ==========
DB_CONFIG = {
    "host": "8.134.23.170",
    "port": 3306,
    "user": "dev",
    "password": "XrKk4Kxe@H2_rtBeqwd12edqyg3qnj.,12,12",
    "database": "douyin",
    "charset": "utf8mb4",
}

TYPE_LABELS = {
    "recommend-video": "短视频",
    "long-video": "长视频",
    "image": "图文",
    "text": "纯文字",
}


def get_connection():
    return pymysql.connect(**DB_CONFIG, cursorclass=pymysql.cursors.DictCursor)


def fetch_keywords(conn, min_freq=2, min_len=2, max_len=30, limit=100):
    """从搜索历史聚合高频关键词"""
    sql = """
        SELECT keyword, COUNT(*) AS freq
        FROM t_search_history
        WHERE LENGTH(keyword) BETWEEN %s AND %s
        GROUP BY keyword
        HAVING COUNT(*) >= %s
        ORDER BY freq DESC
        LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (min_len, max_len, min_freq, limit))
        return cur.fetchall()


def fetch_matching_videos(conn, keyword, limit=30):
    """获取匹配关键词的视频及其内容特征"""
    sql = """
        SELECT v.id, v.`desc`, v.type, v.like_count, v.comment_count,
               v.share_count, v.collect_count, v.play_count, v.duration,
               v.author_user_id,
               COALESCE(NULLIF(u.nickname, ''), u.unique_id, CONCAT('用户', u.uid)) AS author_name,
               vc.text_category, vc.keywords, vc.quality_score
        FROM t_video v
        LEFT JOIN t_video_content vc ON v.id = vc.video_id
        LEFT JOIN t_user u ON v.author_user_id = u.uid
        WHERE v.is_delete = 0
          AND v.status = 'APPROVED'
          AND (v.`desc` LIKE CONCAT('%%', %s, '%%')
               OR vc.keywords LIKE CONCAT('%%', %s, '%%')
               OR vc.text_category LIKE CONCAT('%%', %s, '%%'))
        ORDER BY v.like_count DESC
        LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (keyword, keyword, keyword, limit))
        return cur.fetchall()


def fetch_category_videos(conn, category, limit=20):
    """获取某分类的热门视频 (用于跨品类扩展训练数据)"""
    sql = """
        SELECT v.id, v.`desc`, v.type, v.like_count, v.comment_count,
               v.share_count, v.collect_count, v.play_count, v.duration,
               v.author_user_id,
               COALESCE(NULLIF(u.nickname, ''), u.unique_id, CONCAT('用户', u.uid)) AS author_name,
               vc.text_category, vc.keywords, vc.quality_score
        FROM t_video v
        JOIN t_video_content vc ON v.id = vc.video_id
        LEFT JOIN t_user u ON v.author_user_id = u.uid
        WHERE v.is_delete = 0
          AND v.status = 'APPROVED'
          AND vc.text_category = %s
        ORDER BY v.like_count DESC
        LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (category, limit))
        return cur.fetchall()


def format_number(n):
    """格式化数字: 12345 -> 1.2万"""
    if n is None:
        return "0"
    n = int(n)
    if n >= 10000:
        return f"{n / 10000:.1f}万"
    if n >= 1000:
        return f"{n / 1000:.1f}k"
    return str(n)


def avg_or(videos, field, default=0):
    vals = [v.get(field, 0) or 0 for v in videos]
    return int(sum(vals) / len(vals)) if vals else default


def is_valid_keyword(kw):
    """过滤无效关键词: 纯数字、纯符号、单个英文字母"""
    if len(kw) < 2:
        return False
    if kw.isdigit():
        return False
    if all(c in '!@#$%^&*()_+-=[]{}|;:,.<>?/~`\'\"' for c in kw):
        return False
    if len(kw) == 1 and kw.isascii():
        return False
    return True


def build_summary(keyword, videos, total_count):
    """基于真实平台数据用模板构建搜索摘要"""
    if not videos:
        return f"关于「{keyword}」的搜索结果：目前平台暂时没有相关内容，建议尝试其他关键词搜索，或者换个角度查找感兴趣的内容。我们会持续更新更多优质作品。"

    lines = []
    lines.append(f"关于「{keyword}」的搜索结果：")

    # 1. 数量概览
    lines.append(f"平台共收录 {total_count} 个相关作品，以下是根据热度为你筛选的精华内容。")

    # 2. 品类分布
    cats = Counter()
    for v in videos:
        cat = v.get("text_category") or "其他"
        if cat != "其他":
            cats[cat] += 1
    if cats:
        top_cats = [c for c, _ in cats.most_common(4)]
        lines.append(f"内容主要涵盖：{'、'.join(top_cats)}等方向。")

    # 3. 类型分布
    types = Counter()
    for v in videos:
        t = v.get("type") or "recommend-video"
        types[TYPE_LABELS.get(t, t)] += 1
    if len(types) > 1:
        type_parts = []
        for label, cnt in types.most_common(3):
            pct = int(cnt / len(videos) * 100)
            type_parts.append(f"{label}占{pct}%")
        lines.append(f"作品类型分布：{'，'.join(type_parts)}。")
    elif types:
        label = list(types.keys())[0]
        lines.append(f"作品类型以{label}为主。")

    # 4. 热门作者
    authors = Counter()
    for v in videos:
        author = v.get("author_name")
        if author:
            authors[author] += 1
    if authors:
        top_authors = [a for a, _ in authors.most_common(3)]
        lines.append(f"热门创作者：{'、'.join(top_authors)}等，他们的相关作品获得较多关注。")

    # 5. 热度参考
    total_likes = sum(v.get("like_count") or 0 for v in videos)
    if total_likes > 0:
        lines.append(f"相关作品累计获得 {format_number(total_likes)} 次点赞，社区互动活跃。")

    # 6. 浏览建议
    lines.append("建议浏览方式：在「综合」标签查看全部内容，或切换到「视频」标签只看短视频和长视频，切换到「用户」标签可关注相关创作者获取持续更新。")

    return "\n".join(lines)


def expand_from_categories(conn, existing_keywords, min_freq=1):
    """从 t_video_content 提取所有品类作为额外训练关键词"""
    sql = """
        SELECT vc.text_category, COUNT(*) AS cnt
        FROM t_video_content vc
        JOIN t_video v ON vc.video_id = v.id
        WHERE v.is_delete = 0
          AND v.status = 'APPROVED'
          AND vc.text_category IS NOT NULL
          AND vc.text_category != ''
        GROUP BY vc.text_category
        ORDER BY cnt DESC
        LIMIT 50
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    extra = []
    for row in rows:
        cat = row["text_category"]
        if cat and cat not in existing_keywords and len(cat) >= 2:
            extra.append({"keyword": cat, "freq": row["cnt"]})
    return extra


def main():
    parser = argparse.ArgumentParser(description="导出搜索摘要训练数据")
    parser.add_argument("--min-freq", type=int, default=2,
                        help="关键词最低搜索次数 (默认: 2)")
    parser.add_argument("--min-len", type=int, default=2,
                        help="关键词最小长度 (默认: 2)")
    parser.add_argument("--output", default="training_data.jsonl",
                        help="输出 JSONL 文件路径")
    parser.add_argument("--limit", type=int, default=100,
                        help="最多导出关键词数 (默认: 100)")
    parser.add_argument("--no-expand", action="store_true",
                        help="不从品类扩展训练数据")
    parser.add_argument("--db-host", default=None, help="数据库地址 (可覆盖)")
    parser.add_argument("--db-port", type=int, default=None, help="数据库端口")
    args = parser.parse_args()

    if args.db_host:
        DB_CONFIG["host"] = args.db_host
    if args.db_port:
        DB_CONFIG["port"] = args.db_port

    log.info("连接数据库: %s:%d/%s", DB_CONFIG["host"], DB_CONFIG["port"],
             DB_CONFIG["database"])
    conn = get_connection()
    log.info("数据库连接成功")

    try:
        # 1. 获取搜索历史高频关键词
        log.info("查询搜索历史关键词 (min_freq=%d)...", args.min_freq)
        keywords = fetch_keywords(conn, min_freq=args.min_freq,
                                  min_len=args.min_len, limit=args.limit)
        log.info("获取到 %d 个高频关键词", len(keywords))

        # 2. 扩展品类关键词
        if not args.no_expand:
            existing = {kw["keyword"] for kw in keywords}
            extra = expand_from_categories(conn, existing)
            log.info("品类扩展增加 %d 个关键词", len(extra))
            keywords.extend(extra)

        # 3. 为每个关键词查询匹配视频并生成摘要
        examples = []
        seen_keywords = set()
        skipped_no_match = 0

        for i, kw_row in enumerate(keywords):
            keyword = kw_row["keyword"].strip()
            if keyword in seen_keywords:
                continue
            if not is_valid_keyword(keyword):
                log.info("跳过无效关键词: %s", keyword)
                continue
            seen_keywords.add(keyword)

            freq = kw_row.get("freq", 0)
            log.info("[%d/%d] 处理关键词: %s (搜索次数: %d)",
                     i + 1, len(keywords), keyword, freq)

            videos = fetch_matching_videos(conn, keyword)
            total_count = len(videos)

            if total_count == 0:
                skipped_no_match += 1
                # 即便没有匹配也生成一条"暂无内容"的训练数据
                # 这有助于模型学会如实反馈

            summary = build_summary(keyword, videos, total_count)
            examples.append({
                "keyword": keyword,
                "summary": summary,
            })

        log.info("共生成 %d 条训练数据, %d 个关键词无匹配结果",
                 len(examples), skipped_no_match)

        # 4. 输出 JSONL
        output_path = args.output
        if not os.path.isabs(output_path):
            output_path = os.path.join(os.path.dirname(__file__), output_path)

        with open(output_path, "w", encoding="utf-8") as f:
            for ex in examples:
                f.write(json.dumps(ex, ensure_ascii=False) + "\n")

        log.info("训练数据已导出: %s (%d 条)", output_path, len(examples))
        log.info("下一步: python finetune_summary.py --data %s --epochs 3",
                 os.path.basename(output_path))

    finally:
        conn.close()
        log.info("数据库连接已关闭")


if __name__ == "__main__":
    main()
