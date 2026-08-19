"""ai_pipeline.py train --dry-run: 不产生任何 checkpoint 文件, 退出码 0"""

import glob
import os
import subprocess
import sys

import pytest

SERVER_PYTHON = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PIPELINE = os.path.join(SERVER_PYTHON, "ai_pipeline.py")


def _run(argv, cwd):
    return subprocess.run(
        [sys.executable, PIPELINE] + argv,
        cwd=cwd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=600,
    )


def _all_files(root):
    return {
        os.path.normpath(p)
        for p in glob.glob(os.path.join(root, "**", "*"), recursive=True)
    }


def test_dry_run_exit_zero_no_checkpoint_files(tmp_path):
    before = _all_files(tmp_path)
    result = _run(
        ["train", "--dry-run", "--output", str(tmp_path / "out_ckpt")], cwd=tmp_path
    )
    assert result.returncode == 0, result.stdout + result.stderr
    after = _all_files(tmp_path)
    assert before == after, f"dry-run 不应创建任何文件: {after - before}"
    assert not (tmp_path / "out_ckpt").exists()
    assert "dry-run" in result.stdout or "dry-run" in result.stderr
    assert "训练计划" in result.stdout


def test_dry_run_reports_plan_details(tmp_path):
    result = _run(
        ["train", "--dry-run", "--rank", "8", "--max-steps", "50"], cwd=tmp_path
    )
    assert result.returncode == 0
    assert "Qwen2.5-3B-Instruct" in result.stdout
    assert "训练数据" in result.stdout
    assert "max_steps=50" in result.stdout


def test_dry_run_does_not_touch_old_script_outputs(tmp_path):
    sentinel = tmp_path / "sentinel_dir"
    sentinel.mkdir()
    result = _run(["train", "--dry-run", "--output", str(tmp_path / "out2")], cwd=tmp_path)
    assert result.returncode == 0
    assert sentinel.is_dir()
    assert not (tmp_path / "out2").exists()


def test_eval_echo_produces_metrics(tmp_path):
    result = _run(
        [
            "eval",
            "--provider",
            "echo",
            "--holdout",
            os.path.join(SERVER_PYTHON, "eval_holdout.jsonl"),
            "--out",
            str(tmp_path / "metrics.json"),
        ],
        cwd=tmp_path,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert (tmp_path / "metrics.json").exists()
    import json

    metrics = json.loads((tmp_path / "metrics.json").read_text(encoding="utf-8"))
    assert metrics["total"] == 10
    assert metrics["failures"] == []
