#!/usr/bin/env python3
"""
一次性批量修正视频时长: 用 ffprobe 探测真实时长, 直接写 t_video.duration

跳过特征提取流水线, 仅下载视频文件 -> ffprobe -> UPDATE
"""

import argparse
import os
import subprocess
import sys
import tempfile
import time
import urllib.request


import pymysql

def _require_env(name):
    value = os.environ.get(name, "").strip()
    if not value:
        print(f"缺少环境变量 {name}: 数据库密码必须通过环境变量提供", file=sys.stderr)
        sys.exit(1)
    return value

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "localhost"),
    "port": int(os.environ.get("DB_PORT", "3306")),
    "user": os.environ.get("DB_USER", "root"),
    "password": _require_env("DB_PASSWORD"),
    "database": os.environ.get("DB_NAME", "douyin"),
    "charset": "utf8mb4",
}

API_BASE = "http://localhost:9191"


def get_conn():
    return pymysql.connect(**DB_CONFIG)


def probe_duration(video_path: str) -> float:
    """ffprobe 获取视频实际时长(秒), 失败返回 -1"""
    cmd = [
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", video_path
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return float(result.stdout.strip())
    except (ValueError, AttributeError, subprocess.TimeoutExpired):
        return -1.0


def download_video(url: str, dest: str, retries: int = 3):
    """下载视频到临时文件"""
    if "://" not in url:
        url = f"{API_BASE}/api/file/url?path={url}"
    for attempt in range(retries):
        try:
            urllib.request.urlretrieve(url, dest)
            return True
        except Exception as e:
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
            else:
                print(f"  [下载失败] {e}", file=sys.stderr)
                return False
    return False


def main():
    parser = argparse.ArgumentParser(description="一次性批量修正视频时长")
    parser.add_argument("--dry-run", action="store_true", help="只检查, 不写数据库")
    parser.add_argument("--limit", type=int, default=0, help="限制修正数量, 0=不限制")
    parser.add_argument("--max-duration", type=float, default=5.0,
                        help="时长<=此值的视频视为可疑 (默认5s)")
    parser.add_argument("--skip-updated", action="store_true", default=True,
                        help="跳过最近7天内已更新的视频")
    args = parser.parse_args()

    conn = get_conn()
    cursor = conn.cursor(pymysql.cursors.DictCursor)

    # 查询可疑时长的视频
    sql = """
        SELECT id, video_url, duration, `desc`, type
        FROM t_video
        WHERE is_delete = 0
          AND (duration IS NULL OR duration <= %s)
        ORDER BY id
    """
    if args.limit > 0:
        sql += f" LIMIT {args.limit}"

    cursor.execute(sql, (args.max_duration,))
    videos = cursor.fetchall()
    total = len(videos)
    print(f"找到 {total} 个可疑视频 (duration <= {args.max_duration}s)")

    corrected = 0
    skipped = 0
    failed = 0

    for i, v in enumerate(videos):
        vid = v["id"]
        url = v["video_url"]
        old_dur = v["duration"]
        vtype = v.get("type", "")
        desc = (v.get("desc") or "")[:40]

        # 图文/纯文字没有视频文件, 跳过
        if vtype in ("image", "text"):
            skipped += 1
            continue

        if not url:
            print(f"[{i+1}/{total}] videoId={vid} 无视频URL, 跳过")
            skipped += 1
            continue

        print(f"[{i+1}/{total}] videoId={vid} type={vtype} old={old_dur}s \"{desc}\" ... ", end="", flush=True)

        with tempfile.TemporaryDirectory() as tmpdir:
            video_path = os.path.join(tmpdir, "video.mp4")
            if not download_video(url, video_path):
                print("下载失败")
                failed += 1
                continue

            actual = probe_duration(video_path)
            if actual <= 0:
                print(f"ffprobe 失败")
                failed += 1
                continue

            actual = round(actual, 2)
            if old_dur and abs(actual - old_dur) < 0.5:
                print(f"正确 ({actual}s), 无需修正")
                skipped += 1
                continue

            if args.dry_run:
                print(f"-> {actual}s (dry-run, 未写入)")
                corrected += 1
            else:
                cursor.execute(
                    "UPDATE t_video SET duration = %s WHERE id = %s",
                    (actual, vid)
                )
                conn.commit()
                print(f"{old_dur}s -> {actual}s [OK]")
                corrected += 1

    cursor.close()
    conn.close()

    print(f"\n完成: 修正 {corrected} | 跳过 {skipped} | 失败 {failed} | 共 {total}")
    if args.dry_run:
        print("(dry-run 模式, 未实际写入)")


if __name__ == "__main__":
    main()
