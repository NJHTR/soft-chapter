"""
任务2: 教师模型数据生成

调用 DeepSeek-V4-Flash API 生成高质量训练样本:
1. 视频扩增 — 真实视频不足时, 生成虚拟视频补齐
2. 摘要生成 — 多风格批量生成 15+ 行中文摘要

用法:
  gen = TeacherGenerator()
  samples = gen.generate_samples(keywords, context_builder)

输出 JSONL 格式:
  {"keyword": "...", "context": {...}, "summary": "..."}
"""

import hashlib
import copy
import json
import logging
import os
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Optional

import requests

from .config import DEEPSEEK_CONFIG, DATA_CONFIG, SEED_KEYWORDS
from .context_builder import ContextBuilder

log = logging.getLogger(__name__)

# ===================== 提示词模板 =====================

PROMPT_AUGMENT_VIDEOS = """你是一个短视频平台的内容运营专家。平台名称为 SeekFlow。

用户搜索了关键词「{keyword}」。以下是平台上真实存在的 {real_count} 个相关视频：

{real_videos_json}

请根据以上真实数据，虚构 {virtual_count} 个逼真的视频记录，要求：
1. 风格和内容方向与真实视频一致，但标题、作者、互动数据各不相同
2. 互动数值（likes/plays/comments）应符合小型平台的量级（一般视频几十到几百赞，热门上千）
3. 作者名称使用中文网名风格（如"小熊软糖""数码小白"等）
4. 标签使用2-5个中文词语
5. 输出必须是严格的 JSON 数组，每个元素包含字段: title, description, type(video/image/text), duration(秒), tags(字符串数组), likes(整数), comments(整数), plays(整数), shares(整数), author(name字符串, followerCount整数, verified布尔值), publishTime(格式YYYY-MM-DD)
6. 不要输出任何 JSON 之外的文字。

输出格式示例:
[{{"title": "...", "description": "...", "type": "video", "duration": 30, "tags": ["标签1","标签2"], "likes": 234, "comments": 15, "plays": 1200, "shares": 8, "author": {{"name": "作者名", "followerCount": 500, "verified": false}}, "publishTime": "2025-08-15"}}]"""

PROMPT_GENERATE_SUMMARY = """你是 SeekFlow 短视频平台的搜索助手。请根据以下真实的平台搜索数据，生成一段详细的搜索结果摘要。

{context_text}

要求：
1. 摘要 8-18 行（中文，每行 20-40 字），总字数控制在 300-800 字
2. 结构应包含：搜索概览（匹配数量/类型分布）、内容亮点（具体视频例子和互动数据）、创作者介绍、观看建议
3. 如果视频数量为 0，请自然地告知用户未找到相关内容，并给出搜索建议
4. 语言风格：{style}（{style_desc}）
5. 不要使用 Markdown 标记，不要输出 JSON，直接输出摘要正文
6. 不要加"搜索结果摘要"之类的标题前缀"""

SUMMARY_STYLES = {
    "warm": "亲切温暖", "warm_desc": "像朋友聊天一样自然亲切，多用'你'、'哦'、'呢'等语气词",
    "professional": "专业简洁", "professional_desc": "像产品经理写报告，数据清晰、逻辑分明、语气克制",
    "lively": "活泼有趣", "lively_desc": "像短视频博主一样有活力，多用'超赞'、'绝了'、'必看'等活力词汇",
}


class TeacherGenerator:
    """DeepSeek API 教师模型数据生成器"""

    def __init__(self, api_config: dict = None):
        self.cfg = api_config or DEEPSEEK_CONFIG
        self.max_retries = 5
        self.max_concurrency = 8  # DeepSeek 免费额度并发限制

    # ------------------------------------------------------------------
    # 公开方法
    # ------------------------------------------------------------------

    def generate_samples(
        self,
        keywords: list[str],
        context_builder: ContextBuilder,
        total_samples: int = None,
        output_path: str = None,
    ) -> int:
        """主入口: 对关键词列表生成训练样本, 写入 JSONL"""
        total_samples = total_samples or DATA_CONFIG["total_samples"]
        output_path = output_path or DATA_CONFIG["training_data_path"]

        log.info("开始生成训练样本: target=%d keywords=%d", total_samples, len(keywords))

        # 已生成的关键词集 (去重)
        seen_keywords = self._load_existing_keywords(output_path)
        pending_kws = [k for k in keywords if k not in seen_keywords]

        # 如果关键词不够, 循环使用
        cycles = 0
        while len(pending_kws) * DATA_CONFIG["summary_versions"] < total_samples:
            cycles += 1
            rotated = [f"{k}{cycles}" for k in keywords if f"{k}{cycles}" not in seen_keywords]
            pending_kws.extend(rotated)
            if cycles > 50:
                break

        needed = total_samples // DATA_CONFIG["summary_versions"]
        pending_kws = pending_kws[:needed]

        log.info("需生成 %d 个关键词 × %d 风格 = %d 样本",
                 len(pending_kws), DATA_CONFIG["summary_versions"],
                 len(pending_kws) * DATA_CONFIG["summary_versions"])

        # 只对基础关键词构建 DB 上下文, 循环关键词复用
        log.info("从数据库构建上下文 (仅基础关键词)...")
        base_contexts = {kw: context_builder.build(kw) for kw in keywords}
        log.info("基础上下文构建完成: %d 条", len(base_contexts))

        # 组装待处理列表: (keyword, context), 循环关键词复用基础上下文
        pending: list[tuple[str, dict]] = []
        for kw in pending_kws:
            base_kw = self._resolve_base_keyword(kw, keywords)
            ctx = copy.deepcopy(base_contexts.get(base_kw, base_contexts.get(keywords[0], {})))
            ctx["keyword"] = kw
            pending.append((kw, ctx))

        log.info("待处理: %d 条", len(pending))

        # 并行生成摘要
        total_written = 0
        with open(output_path, "a", encoding="utf-8") as f:
            with ThreadPoolExecutor(max_workers=self.max_concurrency) as pool:
                futures = {
                    pool.submit(self._process_one, ctx): kw
                    for kw, ctx in pending
                }

                for i, future in enumerate(as_completed(futures)):
                    kw = futures[future]
                    try:
                        samples = future.result()
                        for sample in samples:
                            f.write(json.dumps(sample, ensure_ascii=False) + "\n")
                            total_written += 1
                    except Exception as e:
                        log.error("生成失败 keyword=%s: %s", kw, e)

                    if (i + 1) % 10 == 0:
                        log.info("进度: %d/%d, 已写入 %d 条",
                                 i + 1, len(pending), total_written)

        log.info("生成完成: 共写入 %d 条样本 → %s", total_written, output_path)
        return total_written

    @staticmethod
    def _resolve_base_keyword(kw: str, bases: list[str]) -> str:
        """将 '美食教程3' 解析回基础关键词 '美食教程'"""
        for base in sorted(bases, key=len, reverse=True):
            if kw == base:
                return base
            if kw.startswith(base) and re.match(r'\d+$', kw[len(base):]):
                return base
        return bases[0] if bases else kw

    # ------------------------------------------------------------------
    # 单个关键词处理
    # ------------------------------------------------------------------

    def _process_one(self, ctx: dict) -> list[dict]:
        """处理单个关键词: 可选扩增 → 多风格生成摘要 → 返回样本列表"""
        keyword = ctx["keyword"]

        # 1. 视频扩增 (如果需要)
        if ctx.get("need_augment", False):
            virtual_videos = self._augment_videos(keyword, ctx["videos"])
            ctx = self._merge_virtual(ctx, virtual_videos)

        # 2. 并行生成多风格摘要
        samples = []
        style_keys = ["warm", "professional", "lively"]
        for style_key in style_keys:
            style_desc = SUMMARY_STYLES[style_key]
            desc = SUMMARY_STYLES[style_key + "_desc"]
            summary = self._generate_summary(ctx, style=style_desc, desc=desc)
            if summary:
                samples.append({
                    "keyword": keyword,
                    "context": ctx,
                    "summary": summary,
                    "style": style_key,
                })
        return samples

    # ------------------------------------------------------------------
    # 视频扩增
    # ------------------------------------------------------------------

    def _augment_videos(self, keyword: str, real_videos: list[dict]) -> list[dict]:
        """调用 DeepSeek API 生成虚拟视频"""
        if not keyword.strip():
            return []

        real_json = json.dumps([self._simplify_video(v) for v in real_videos], ensure_ascii=False, indent=2)
        prompt = PROMPT_AUGMENT_VIDEOS.format(
            keyword=keyword,
            real_count=len(real_videos),
            real_videos_json=real_json or "[]",
            virtual_count=DATA_CONFIG["virtual_video_count"],
        )

        result = self._api_call(prompt, temperature=DEEPSEEK_CONFIG["temperature_augment"])
        if not result:
            return []

        # 解析 JSON 数组
        try:
            # 提取 JSON 部分 (模型偶尔会多输出解释文字)
            json_start = result.find("[")
            json_end = result.rfind("]") + 1
            if json_start >= 0 and json_end > json_start:
                result = result[json_start:json_end]
            virtual = json.loads(result)
            if isinstance(virtual, list):
                log.info("扩增 %d 个虚拟视频: keyword=%s", len(virtual), keyword)
                return virtual
        except json.JSONDecodeError as e:
            log.warning("扩增结果 JSON 解析失败: %s, result[:200]=%s", e, result[:200])

        return []

    def _simplify_video(self, v: dict) -> dict:
        """精简视频信息用于 prompt"""
        return {
            "title": v.get("title", ""),
            "type": v.get("type", "video"),
            "duration": v.get("duration", 15),
            "tags": v.get("tags", []),
            "likes": v.get("likes", 0),
            "comments": v.get("comments", 0),
            "plays": v.get("plays", 0),
            "author": {"name": v.get("author", {}).get("name", ""),
                       "followerCount": v.get("author", {}).get("followerCount", 0)},
        }

    def _merge_virtual(self, ctx: dict, virtual_videos: list[dict]) -> dict:
        """将虚拟视频合并到上下文"""
        if not virtual_videos:
            return ctx
        ctx = copy.deepcopy(ctx)
        existing_ids = set(v.get("id", -1) for v in ctx["videos"])

        orig_count = len(ctx["videos"])
        for vv in virtual_videos:
            vid = -(len(ctx["videos"]) + 1)  # 负数 ID 表示虚拟
            if vid in existing_ids:
                continue
            ctx["videos"].append({
                "id": vid,
                "title": vv.get("title", ""),
                "description": vv.get("description", ""),
                "type": vv.get("type", "video"),
                "duration": vv.get("duration", 15),
                "tags": vv.get("tags", []),
                "likes": vv.get("likes", 0),
                "comments": vv.get("comments", 0),
                "shares": vv.get("shares", 0),
                "plays": vv.get("plays", 0),
                "qualityScore": 0.7,
                "publishTime": vv.get("publishTime", "2025-01-01"),
                "author": {
                    "id": None,
                    "name": vv.get("author", {}).get("name", "SeekFlow创作者"),
                    "followerCount": vv.get("author", {}).get("followerCount", 100),
                    "verified": vv.get("author", {}).get("verified", False),
                    "avatar": "",
                },
                "_virtual": True,
            })
        ctx["totalVideos"] = len(ctx["videos"])
        ctx["need_augment"] = False

        # 更新聚合
        if orig_count < len(ctx["videos"]):
            ctx["aggregations"]["totalLikes"] += sum(v.get("likes", 0) for v in virtual_videos)
            ctx["aggregations"]["totalComments"] += sum(v.get("comments", 0) for v in virtual_videos)
            ctx["aggregations"]["totalPlays"] += sum(v.get("plays", 0) for v in virtual_videos)
            new_tags = [t for v in virtual_videos for t in v.get("tags", [])]
            from collections import Counter
            all_tags = ctx["aggregations"].get("hotTags", []) + new_tags
            ctx["aggregations"]["hotTags"] = [t for t, _ in Counter(all_tags).most_common(5)]

        return ctx

    # ------------------------------------------------------------------
    # 摘要生成
    # ------------------------------------------------------------------

    def _generate_summary(self, ctx: dict, style: str, desc: str) -> Optional[str]:
        """调用 DeepSeek API 生成摘要"""
        context_text = self._format_context_for_prompt(ctx)
        prompt = PROMPT_GENERATE_SUMMARY.format(
            context_text=context_text,
            style=style,
            style_desc=desc,
        )

        result = self._api_call(prompt, temperature=DEEPSEEK_CONFIG["temperature_summary"],
                                max_tokens=DEEPSEEK_CONFIG["max_tokens"])
        return result.strip() if result else None

    def _format_context_for_prompt(self, ctx: dict) -> str:
        """将 JSON 上下文格式化为自然文本"""
        parts = []
        kw = ctx["keyword"]
        total = ctx["totalVideos"]

        # 搜索概况
        if total == 0:
            parts.append(f'用户搜索了「{kw}」，但平台上暂未找到直接相关的内容。')
        else:
            parts.append(f'用户搜索了「{kw}」，平台共匹配到 {total} 条相关内容：')

        # 具体视频
        for i, v in enumerate(ctx["videos"][:15], 1):
            is_virtual = v.get("_virtual", False)
            tag = "(虚拟)" if is_virtual else ""
            author = v.get("author", {})
            parts.append(
                f"  {i}.{tag}《{v['title']}》— {v.get('type','video')}类, "
                f"时长{v.get('duration',15)}秒, "
                f"{v.get('likes',0)}赞/{v.get('comments',0)}评/{v.get('plays',0)}播放, "
                f"作者: {author.get('name','未知')} (粉丝{author.get('followerCount',0)})"
            )
            if v.get("tags"):
                parts.append(f"     标签: {' / '.join(v['tags'][:8])}")

        # 聚合统计
        agg = ctx.get("aggregations", {})
        parts.append(
            f"\n统计: 总{v.get('likes',0) if total > 0 else 0}赞, "
            f"热门标签: {'、'.join(agg.get('hotTags', [])[:5])}, "
            f"平均时长: {agg.get('avgDuration', 0)}秒"
        )

        # 匹配用户
        users = ctx.get("users", [])
        if users:
            parts.append(f"\n匹配到 {len(users)} 位相关创作者:")
            for u in users[:5]:
                parts.append(f"  · {u['name']} (粉丝{u['followerCount']}, 作品{u.get('videoCount',0)}个)")

        # 平台统计
        stats = ctx.get("platformStats", {})
        parts.append(
            f"\n平台概况: {stats.get('totalVideos',0)}个视频, "
            f"{stats.get('totalUsers',0)}位用户, "
            f"{stats.get('totalGoods',0)}件商品, "
            f"{stats.get('activeLives',0)}个直播中"
        )

        return "\n".join(parts)

    # ------------------------------------------------------------------
    # API 调用 (指数退避重试)
    # ------------------------------------------------------------------

    def _api_call(self, prompt: str, temperature: float = 0.8,
                  max_tokens: int = 2048) -> Optional[str]:
        """调用 DeepSeek Chat API, 带指数退避重试 (使用 requests)"""
        url = f"{self.cfg['base_url']}/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.cfg['api_key']}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.cfg["model"],
            "messages": [{"role": "user", "content": prompt}],
            "temperature": temperature,
            "max_tokens": max_tokens,
        }

        last_error = None
        for attempt in range(self.max_retries):
            try:
                resp = requests.post(
                    url, json=payload, headers=headers,
                    timeout=(30, 120),  # (connect_timeout, read_timeout)
                )
                if resp.status_code == 200:
                    data = resp.json()
                    return data["choices"][0]["message"]["content"]
                elif resp.status_code == 429:
                    wait = min(2 ** attempt * 5, 120)
                    log.warning("API 限流, 等待 %ds...", wait)
                    time.sleep(wait)
                elif resp.status_code >= 500:
                    wait = min(2 ** attempt * 3, 60)
                    log.warning("API 服务端错误 %d, 等待 %ds...", resp.status_code, wait)
                    time.sleep(wait)
                else:
                    log.error("API 错误 %d: %s", resp.status_code, resp.text[:200])
                    return None
            except requests.RequestException as e:
                last_error = e
                wait = min(2 ** attempt * 3, 60)
                log.warning("API 网络错误 (尝试 %d/%d): %s, 等待 %ds...",
                            attempt + 1, self.max_retries, e, wait)
                time.sleep(wait)

        log.error("API 调用最终失败: %s", last_error)
        return None

    # ------------------------------------------------------------------
    # 去重
    # ------------------------------------------------------------------

    def _load_existing_keywords(self, path: str) -> set[str]:
        """从已有 JSONL 加载已生成的关键词集合"""
        existing = set()
        if not os.path.exists(path):
            return existing
        try:
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        item = json.loads(line)
                        existing.add(item.get("keyword", ""))
                    except json.JSONDecodeError:
                        continue
        except Exception:
            pass
        return existing
