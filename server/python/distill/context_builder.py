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

    def build(self, keyword: str, with_collaborative: bool = True) -> dict:
        """主入口: 输入关键词, 输出完整上下文 JSON

        with_collaborative=False 时跳过行为信号查询（兼容旧流程）
        """
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

            result = {
                "keyword": keyword,
                "totalVideos": len(videos),
                "videos": videos,
                "users": users,
                "aggregations": aggregations,
                "platformStats": platform_stats,
                "need_augment": need_augment,
            }

            # 协同行为信号 (系统层护城河)
            if with_collaborative:
                try:
                    result["collaborative"] = self._build_collaborative(conn, keyword, videos)
                except Exception as e:
                    log.warning("协同信号查询失败, 降级基础上下文: %s", e)
                    result["collaborative"] = self._empty_collaborative()

            return result
        finally:
            conn.close()

    def build_batch(self, keywords: list[str], with_collaborative: bool = True) -> list[dict]:
        """批量构建上下文 (复用连接, 复用单个 build 保证协同信号一致)"""
        results = []
        for kw in keywords:
            try:
                kw = kw.strip()
                if not kw:
                    continue
                results.append(self.build(kw, with_collaborative=with_collaborative))
            except Exception as e:
                log.warning("批量构建失败 keyword=%s: %s", kw, e)
        return results

    def enrich(self, context: dict) -> dict:
        """给已有上下文补充协同行为信号 (供推理时 Java 传入的基础上下文使用)"""
        keyword = context.get("keyword", "")
        if not keyword:
            return context
        conn = self._connect()
        try:
            context["collaborative"] = self._build_collaborative(conn, keyword, context.get("videos", []))
        except Exception as e:
            log.warning("协同信号补充失败, 降级: %s", e)
            context["collaborative"] = self._empty_collaborative()
        finally:
            conn.close()
        return context

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
                "type": self._type_label(row.get("type")),
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
    # 协同行为信号 (系统层护城河)
    # ------------------------------------------------------------------

    def _build_collaborative(self, conn, keyword: str, videos: list[dict]) -> dict:
        """聚合所有协同行为信号"""
        return {
            "coSearch": self._co_search_chains(conn, keyword),
            "coWatch": self._co_watch_chains(conn, videos),
            "commentWisdom": self._comment_wisdom(conn, videos),
            "contentGaps": self._content_gaps(conn, keyword),
            "temporalTrend": self._temporal_trend(conn, keyword),
            "hotContext": self._hot_context(conn, keyword, videos),
        }

    def _co_search_chains(self, conn, keyword: str, topk: int = 8) -> list[dict]:
        """共搜链路: 搜了此关键词的用户还搜了什么

        逻辑: 找到最近搜过 keyword 的用户, 统计他们同时段内还搜了哪些词
        """
        sql = """
            SELECT s2.keyword, COUNT(DISTINCT s2.user_id) AS co_users
            FROM t_search_history s1
            JOIN t_search_history s2
              ON s1.user_id = s2.user_id
             AND s2.keyword != %s
             AND s2.create_time BETWEEN
                  DATE_SUB(s1.create_time, INTERVAL 30 MINUTE)
                  AND DATE_ADD(s1.create_time, INTERVAL 30 MINUTE)
            WHERE s1.keyword = %s
              AND s1.create_time > DATE_SUB(NOW(), INTERVAL 60 DAY)
              AND s2.create_time > DATE_SUB(NOW(), INTERVAL 60 DAY)
            GROUP BY s2.keyword
            ORDER BY co_users DESC
            LIMIT %s
        """
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (keyword, keyword, topk))
                rows = cur.fetchall()
            if rows:
                total = sum(r["co_users"] for r in rows) or 1
                return [
                    {"keyword": r["keyword"], "strength": round(r["co_users"] / total, 3)}
                    for r in rows
                ]
        except Exception as e:
            log.warning("共搜链路查询失败: %s", e)
        return []

    def _co_watch_chains(self, conn, videos: list[dict], topk: int = 8) -> list[dict]:
        """共看链路: 看过这些视频的用户还看了什么

        逻辑: 找到看过匹配视频的用户, 统计他们观看的其他高频视频
        """
        matched_ids = [v["id"] for v in videos if v.get("id")]
        if not matched_ids:
            return []

        placeholders = ",".join(["%s"] * len(matched_ids))
        sql = f"""
            SELECT v.desc AS title, v.like_count AS likes, v.play_count AS plays,
                   COUNT(DISTINCT wh2.user_id) AS co_watchers
            FROM t_watch_history wh1
            JOIN t_watch_history wh2
              ON wh1.user_id = wh2.user_id
             AND wh2.video_id NOT IN ({placeholders})
            JOIN t_video v ON wh2.video_id = v.id AND v.is_delete = 0
            WHERE wh1.video_id IN ({placeholders})
              AND wh1.create_time > DATE_SUB(NOW(), INTERVAL 30 DAY)
            GROUP BY wh2.video_id, v.desc, v.like_count, v.play_count
            ORDER BY co_watchers DESC
            LIMIT %s
        """
        params = matched_ids + matched_ids + [topk]
        try:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                rows = cur.fetchall()
            if rows:
                total = sum(r["co_watchers"] for r in rows) or 1
                return [
                    {
                        "title": (r["title"] or "")[:50],
                        "likes": r["likes"] or 0,
                        "plays": r["plays"] or 0,
                        "strength": round(r["co_watchers"] / total, 3),
                    }
                    for r in rows
                ]
        except Exception as e:
            log.warning("共看链路查询失败: %s", e)
        return []

    def _comment_wisdom(self, conn, videos: list[dict], topk: int = 5) -> list[dict]:
        """评论智慧: 匹配视频下的高质量评论

        逻辑: 取匹配视频下点赞最高的评论, 按点赞降序
        """
        matched_ids = [v["id"] for v in videos if v.get("id")]
        if not matched_ids:
            return []

        placeholders = ",".join(["%s"] * len(matched_ids))
        sql = f"""
            SELECT c.content, c.like_count,
                   u.nickname AS author_name
            FROM t_comment c
            LEFT JOIN t_user u ON c.user_id = u.uid
            WHERE c.video_id IN ({placeholders})
              AND c.is_delete = 0
              AND c.parent_id = 0
              AND c.content != ''
              AND LENGTH(c.content) >= 5
            ORDER BY c.like_count DESC
            LIMIT %s
        """
        try:
            with conn.cursor() as cur:
                cur.execute(sql, matched_ids + [topk])
                rows = cur.fetchall()
            return [
                {
                    "content": (r["content"] or "")[:200],
                    "likes": r["like_count"] or 0,
                    "author": r["author_name"] or "匿名",
                }
                for r in rows if r["content"]
            ]
        except Exception as e:
            log.warning("评论查询失败: %s", e)
        return []

    def _content_gaps(self, conn, keyword: str) -> dict:
        """内容缺口: 检测平台在某些方向的内容缺失

        逻辑: 用标签体系反查 — 哪些相关标签/品类在平台上内容很少
        """
        # 查找包含此关键词的标签/品类, 统计其内容量
        like_kw = f"%{keyword}%"
        sql = """
            SELECT vc.text_category, COUNT(*) AS cnt
            FROM t_video_content vc
            JOIN t_video v ON vc.video_id = v.id AND v.is_delete = 0
            WHERE (vc.keywords LIKE %s OR vc.scene_tags LIKE %s
                   OR vc.text_category LIKE %s)
            GROUP BY vc.text_category
            ORDER BY cnt ASC
            LIMIT 10
        """
        gaps = []
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (like_kw, like_kw, like_kw))
                rows = cur.fetchall()
            if rows:
                gaps = [
                    {"category": r["text_category"] or "综合", "count": r["cnt"]}
                    for r in rows
                ]
        except Exception as e:
            log.warning("内容缺口查询失败: %s", e)

        # 平台总量
        total_cat = sum(g["count"] for g in gaps) if gaps else 0

        return {
            "categories": gaps,
            "totalRelated": total_cat,
            "isSparse": total_cat < 5,
        }

    def _temporal_trend(self, conn, keyword: str) -> dict:
        """时序趋势: 该关键词在过去 30 天的搜索热度变化"""
        sql = """
            SELECT
                DATE(create_time) AS day,
                COUNT(*) AS volume
            FROM t_search_history
            WHERE keyword = %s
              AND create_time > DATE_SUB(NOW(), INTERVAL 30 DAY)
            GROUP BY DATE(create_time)
            ORDER BY day
        """
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (keyword,))
                rows = cur.fetchall()

            if not rows:
                return {"trend": "flat", "recentVolume": 0, "daily": []}

            volumes = [r["volume"] for r in rows]
            recent = volumes[-7:] if len(volumes) >= 7 else volumes
            older = volumes[:-7] if len(volumes) > 7 else []

            avg_recent = sum(recent) / len(recent) if recent else 0
            avg_older = sum(older) / len(older) if older else avg_recent

            if avg_older > 0:
                change = (avg_recent - avg_older) / avg_older
            else:
                change = 0

            if change > 0.3:
                trend = "rising"
            elif change < -0.3:
                trend = "declining"
            else:
                trend = "stable"

            return {
                "trend": trend,
                "changeRatio": round(change, 2),
                "recentVolume": sum(recent),
                "daily": [{"date": str(r["day"]), "volume": r["volume"]} for r in rows[-14:]],
            }
        except Exception as e:
            log.warning("时序趋势查询失败: %s", e)
        return {"trend": "unknown", "recentVolume": 0, "daily": []}

    def _hot_context(self, conn, keyword: str, videos: list[dict]) -> dict:
        """热点上下文: 搜索词相关的平台热点信号

        逻辑:
          1. 品类分布 — 匹配视频覆盖了哪些品类
          2. 完播率 — 用户观看这些视频的完成情况
          3. 内容类型分布 — video/image/text
        """
        # 品类分布
        categories = Counter()
        type_dist = Counter()
        for v in videos:
            for tag in v.get("tags", []):
                categories[tag] += 1
            type_dist[v.get("type", "video")] += 1

        # 完播率 (基于 t_watch_history)
        completion_rate = 0.0
        matched_ids = [v["id"] for v in videos if v.get("id")]
        if matched_ids:
            placeholders = ",".join(["%s"] * len(matched_ids))
            sql = f"""
                SELECT
                    AVG(CASE WHEN video_duration > 0
                        THEN LEAST(watch_duration / video_duration, 1.0)
                        ELSE 0 END) AS avg_completion
                FROM t_watch_history
                WHERE video_id IN ({placeholders})
            """
            try:
                with conn.cursor() as cur:
                    cur.execute(sql, matched_ids)
                    row = cur.fetchone()
                if row and row["avg_completion"]:
                    completion_rate = round(float(row["avg_completion"]), 3)
            except Exception:
                pass

        return {
            "topCategories": [{"name": c, "count": n} for c, n in categories.most_common(6)],
            "typeDistribution": dict(type_dist),
            "avgCompletionRate": completion_rate,
        }

    def _empty_collaborative(self) -> dict:
        return {
            "coSearch": [],
            "coWatch": [],
            "commentWisdom": [],
            "contentGaps": {"categories": [], "totalRelated": 0, "isSparse": True},
            "temporalTrend": {"trend": "unknown", "recentVolume": 0, "daily": []},
            "hotContext": {"topCategories": [], "typeDistribution": {}, "avgCompletionRate": 0},
        }

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
    def _type_label(raw: str | None) -> str:
        """DB type → 中文显示名"""
        if not raw:
            return "视频"
        return {
            "recommend-video": "视频",
            "long-video": "长视频",
            "image": "图文",
            "text": "文字",
        }.get(raw, raw)

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
