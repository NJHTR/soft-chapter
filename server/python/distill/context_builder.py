"""
任务1: 数据库字段抽取与上下文构建

从 MySQL 提取真实平台数据, 构建结构化 JSON 上下文,
供教师模型生成训练样本使用。

用法:
  builder = ContextBuilder()
  ctx = builder.build(keyword="美食教程")

输出 JSON:
  { keyword, totalVideos, videos[], users[], aggregations, platformStats, need_augment }
"""

import json
import logging
import re
from collections import Counter
from typing import Optional

import pymysql

from .config import DB_CONFIG

log = logging.getLogger(__name__)


class ContextBuilder:
    """从 MySQL 数据库抽取搜索相关数据, 构建富上下文 JSON"""

    def __init__(self, db_config: dict = None):
        self.db = db_config or DB_CONFIG

    # ------------------------------------------------------------------
    # 公开方法
    # ------------------------------------------------------------------

    def build(self, keyword: str) -> dict:
        """主入口: 输入关键词, 输出完整上下文 JSON"""
        keyword = keyword.strip()
        if not keyword:
            return self._empty_context(keyword)

        conn = self._connect()

        try:
            videos = self._search_videos(conn, keyword)
            users = self._search_users(conn, keyword)
            aggregations = self._compute_aggregations(conn, videos)
            platform_stats = self._platform_stats(conn)
            need_augment = len(videos) < 5

            return {
                "keyword": keyword,
                "totalVideos": len(videos),
                "videos": videos,
                "users": users,
                "aggregations": aggregations,
                "platformStats": platform_stats,
                "need_augment": need_augment,
            }
        finally:
            conn.close()

    def build_batch(self, keywords: list[str]) -> list[dict]:
        """批量构建上下文 (复用连接)"""
        conn = self._connect()
        try:
            results = []
            for kw in keywords:
                try:
                    kw = kw.strip()
                    if not kw:
                        continue
                    videos = self._search_videos(conn, kw)
                    users = self._search_users(conn, kw)
                    agg = self._compute_aggregations(conn, videos)
                    stats = self._platform_stats(conn)
                    results.append({
                        "keyword": kw,
                        "totalVideos": len(videos),
                        "videos": videos,
                        "users": users,
                        "aggregations": agg,
                        "platformStats": stats,
                        "need_augment": len(videos) < 5,
                    })
                except Exception as e:
                    log.warning("批量构建失败 keyword=%s: %s", kw, e)
            return results
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # 数据库连接
    # ------------------------------------------------------------------

    def _connect(self):
        return pymysql.connect(
            host=self.db["host"],
            port=self.db["port"],
            user=self.db["user"],
            password=self.db["password"],
            database=self.db["database"],
            charset=self.db["charset"],
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=10,
            read_timeout=30,
        )

    # ------------------------------------------------------------------
    # 视频搜索
    # ------------------------------------------------------------------

    def _search_videos(self, conn, keyword: str, limit: int = 8) -> list[dict]:
        """模糊匹配视频标题/描述/标签, 最多返回 limit 条"""
        like_kw = f"%{keyword}%"
        sql = """
            SELECT v.id, v.desc, v.duration, v.type, v.like_count, v.comment_count,
                   v.share_count, v.collect_count, v.play_count, v.create_time,
                   v.author_user_id, v.music_title, v.cover_url, v.status,
                   u.uid AS author_uid, u.nickname AS author_name,
                   u.follower_count AS author_followers, u.signature AS author_signature,
                   u.avatar_168_url AS author_avatar,
                   vc.keywords, vc.text_category, vc.scene_tags, vc.object_tags,
                   vc.quality_score
            FROM t_video v
            LEFT JOIN t_user u ON v.author_user_id = u.uid
            LEFT JOIN t_video_content vc ON v.id = vc.video_id
            WHERE v.is_delete = 0
              AND v.status = 'APPROVED'
              AND (v.desc LIKE %s
                   OR vc.keywords LIKE %s
                   OR vc.text_category LIKE %s
                   OR v.music_title LIKE %s)
            ORDER BY v.like_count DESC
            LIMIT %s
        """
        with conn.cursor() as cur:
            cur.execute(sql, (like_kw, like_kw, like_kw, like_kw, limit))
            rows = cur.fetchall()

        result = []
        for row in rows:
            result.append({
                "id": row["id"],
                "title": self._safe_str(row["desc"], f"视频{row['id']}"),
                "description": self._safe_str(row["desc"], ""),
                "type": self._safe_str(row["type"], "video"),
                "duration": self._safe_float(row["duration"], 15.0),
                "tags": self._parse_tags(row),
                "likes": self._safe_int(row["like_count"], 0),
                "comments": self._safe_int(row["comment_count"], 0),
                "shares": self._safe_int(row["share_count"], 0),
                "plays": self._safe_int(row["play_count"], 0),
                "qualityScore": self._safe_float(row["quality_score"], 0.5),
                "publishTime": self._safe_datetime(row["create_time"]),
                "author": {
                    "id": self._safe_int(row["author_uid"], 0),
                    "name": self._safe_str(row["author_name"], "SeekFlow用户"),
                    "followerCount": self._safe_int(row["author_followers"], 0),
                    "verified": self._safe_int(row["author_followers"], 0) > 1000,
                    "avatar": self._safe_str(row["author_avatar"], ""),
                },
            })
        return result

    def _parse_tags(self, row: dict) -> list[str]:
        """合并 keywords + scene_tags + object_tags"""
        tags = set()
        for field in ("keywords", "scene_tags", "object_tags"):
            val = row.get(field)
            if not val or not isinstance(val, str):
                continue
            try:
                parsed = json.loads(val)
                if isinstance(parsed, list):
                    for item in parsed:
                        if isinstance(item, str) and 1 < len(item) <= 20:
                            tags.add(item)
            except (json.JSONDecodeError, TypeError):
                continue
        if row.get("text_category") and row["text_category"] != "综合":
            tags.add(row["text_category"])
        return list(tags)[:10]

    # ------------------------------------------------------------------
    # 用户搜索
    # ------------------------------------------------------------------

    def _search_users(self, conn, keyword: str, limit: int = 5) -> list[dict]:
        like_kw = f"%{keyword}%"
        sql = """
            SELECT uid, unique_id, nickname, signature, follower_count,
                   following_count, total_favorited, video_count, avatar_168_url
            FROM t_user
            WHERE is_delete = 0
              AND (nickname LIKE %s OR unique_id LIKE %s OR signature LIKE %s)
            ORDER BY follower_count DESC
            LIMIT %s
        """
        with conn.cursor() as cur:
            cur.execute(sql, (like_kw, like_kw, like_kw, limit))
            rows = cur.fetchall()

        return [{
            "id": row["uid"],
            "uniqueId": self._safe_str(row["unique_id"], ""),
            "name": self._safe_str(row["nickname"], "SeekFlow用户"),
            "signature": self._safe_str(row["signature"], ""),
            "followerCount": self._safe_int(row["follower_count"], 0),
            "followingCount": self._safe_int(row["following_count"], 0),
            "totalFavorited": self._safe_int(row["total_favorited"], 0),
            "videoCount": self._safe_int(row["video_count"], 0),
            "avatar": self._safe_str(row["avatar_168_url"], ""),
        } for row in rows]

    # ------------------------------------------------------------------
    # 聚合统计
    # ------------------------------------------------------------------

    def _compute_aggregations(self, conn, videos: list[dict]) -> dict:
        """基于匹配视频计算统计聚合"""
        if not videos:
            return {
                "totalLikes": 0, "totalComments": 0, "totalPlays": 0,
                "avgDuration": 0, "hotTags": [], "totalShares": 0,
            }

        total_likes = sum(v["likes"] for v in videos)
        total_comments = sum(v["comments"] for v in videos)
        total_plays = sum(v["plays"] for v in videos)
        total_shares = sum(v["shares"] for v in videos)
        durations = [v["duration"] for v in videos if v["duration"] > 0]
        avg_duration = round(sum(durations) / len(durations), 1) if durations else 0

        # 热门标签 Top5
        tag_counter = Counter()
        for v in videos:
            for tag in v.get("tags", []):
                tag_counter[tag] += 1
        hot_tags = [t for t, _ in tag_counter.most_common(5)]

        return {
            "totalLikes": total_likes,
            "totalComments": total_comments,
            "totalPlays": total_plays,
            "totalShares": total_shares,
            "avgDuration": avg_duration,
            "hotTags": hot_tags,
        }

    # ------------------------------------------------------------------
    # 平台统计
    # ------------------------------------------------------------------

    def _platform_stats(self, conn) -> dict:
        """全平台统计数据"""
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT COUNT(*) AS cnt FROM t_video WHERE is_delete = 0 AND status = 'APPROVED'")
                total_videos = cur.fetchone()["cnt"]

                cur.execute("SELECT COUNT(*) AS cnt FROM t_user WHERE is_delete = 0")
                total_users = cur.fetchone()["cnt"]

                cur.execute("SELECT COUNT(*) AS cnt FROM t_goods WHERE is_delete = 0 AND status = 1")
                total_goods = cur.fetchone()["cnt"]

                cur.execute("SELECT COUNT(*) AS cnt FROM t_live_room WHERE is_delete = 0 AND status = 'LIVE'")
                active_lives = cur.fetchone()["cnt"]

                return {
                    "totalVideos": total_videos,
                    "totalUsers": total_users,
                    "totalGoods": total_goods,
                    "activeLives": active_lives,
                }
        except Exception:
            return {"totalVideos": 0, "totalUsers": 0, "totalGoods": 0, "activeLives": 0}

    # ------------------------------------------------------------------
    # 工具方法
    # ------------------------------------------------------------------

    def _empty_context(self, keyword: str) -> dict:
        return {
            "keyword": keyword,
            "totalVideos": 0,
            "videos": [],
            "users": [],
            "aggregations": {"totalLikes": 0, "totalComments": 0, "totalPlays": 0,
                             "avgDuration": 0, "hotTags": [], "totalShares": 0},
            "platformStats": {"totalVideos": 0, "totalUsers": 0, "totalGoods": 0, "activeLives": 0},
            "need_augment": keyword != "",
        }

    @staticmethod
    def _safe_str(val, default=""):
        if val is None or (isinstance(val, str) and not val.strip()):
            return default
        return str(val)[:500]

    @staticmethod
    def _safe_int(val, default=0):
        try:
            return int(val) if val is not None else default
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _safe_float(val, default=0.0):
        try:
            return float(val) if val is not None else default
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _safe_datetime(val, default="2025-01-01"):
        if val is None:
            return default
        try:
            return val.strftime("%Y-%m-%d")
        except AttributeError:
            return str(val)[:10]
