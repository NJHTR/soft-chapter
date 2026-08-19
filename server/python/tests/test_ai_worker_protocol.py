"""ai_worker JSON-lines 协议测试 (echo 模式, 子进程)"""

import json
import subprocess
import sys
import threading
import time

import pytest

WORKER = __import__("os").path.join(
    __import__("os").path.dirname(__import__("os").path.dirname(__import__("os").path.abspath(__file__))),
    "ai_worker.py",
)


def _read_line(stream, timeout=30):
    deadline = time.time() + timeout
    while True:
        remaining = deadline - time.time()
        if remaining <= 0:
            raise TimeoutError("等待 worker 输出超时")
        line = stream.readline()
        if line == "":
            raise EOFError("worker 输出流已关闭")
        line = line.strip()
        if line:
            return line


@pytest.fixture(scope="module")
def worker():
    proc = subprocess.Popen(
        [sys.executable, WORKER, "--serve", "--model", "echo", "--max-new-tokens", "256"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        bufsize=1,
        env={**__import__("os").environ, "PYTHONIOENCODING": "utf-8"},
    )
    stderr_lines = []

    def drain():
        for line in proc.stderr:
            stderr_lines.append(line.rstrip("\n"))

    threading.Thread(target=drain, daemon=True).start()
    yield proc, stderr_lines
    if proc.poll() is None:
        proc.kill()
    proc.wait(timeout=10)


def _send(proc, obj):
    proc.stdin.write(json.dumps(obj, ensure_ascii=False) + "\n")
    proc.stdin.flush()


def test_startup_ready(worker):
    proc, _ = worker
    assert _read_line(proc.stdout, timeout=30) == "READY"


def test_request_response_id_and_reply(worker):
    proc, _ = worker
    _send(proc, {"id": "abc-1", "prompt": "你好, 抖音"})
    resp = json.loads(_read_line(proc.stdout))
    assert resp["id"] == "abc-1"
    assert resp["reply"] == "你好, 抖音"
    assert resp["model"] == "echo"
    assert isinstance(resp["tokens"], int) and resp["tokens"] > 0
    assert isinstance(resp["ms"], int)


def test_echo_truncates_long_prompt(worker):
    proc, _ = worker
    long_prompt = "长" * 5000
    _send(proc, {"id": "trunc", "prompt": long_prompt})
    resp = json.loads(_read_line(proc.stdout))
    assert resp["id"] == "trunc"
    assert len(resp["reply"]) == 2048
    assert resp["reply"] == long_prompt[:2048]


def test_request_overrides_max_new_tokens(worker):
    proc, _ = worker
    _send(proc, {"id": "mnt", "prompt": "hi", "max_new_tokens": 128})
    resp = json.loads(_read_line(proc.stdout))
    assert resp["id"] == "mnt"
    assert resp["reply"] == "hi"


def test_ping_pong(worker):
    proc, _ = worker
    _send(proc, {"cmd": "ping"})
    resp = json.loads(_read_line(proc.stdout))
    assert resp["cmd"] == "pong"
    assert isinstance(resp["uptime_s"], int) and resp["uptime_s"] >= 0


def test_malformed_json_does_not_crash(worker):
    proc, _ = worker
    proc.stdin.write("{not json\n")
    proc.stdin.flush()
    resp = json.loads(_read_line(proc.stdout))
    assert "error" in resp
    assert proc.poll() is None
    _send(proc, {"id": "after-bad", "prompt": "still alive"})
    resp2 = json.loads(_read_line(proc.stdout))
    assert resp2["id"] == "after-bad"
    assert resp2["reply"] == "still alive"


def test_missing_prompt_returns_error(worker):
    proc, _ = worker
    _send(proc, {"id": "no-prompt"})
    resp = json.loads(_read_line(proc.stdout))
    assert resp["id"] == "no-prompt"
    assert "error" in resp


def test_exit_command_code_zero(worker):
    proc, _ = worker
    _send(proc, {"cmd": "exit"})
    assert proc.wait(timeout=15) == 0


def test_eof_exits_code_zero():
    proc = subprocess.Popen(
        [sys.executable, WORKER, "--serve", "--model", "echo"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
    )
    assert _read_line(proc.stdout, timeout=30) == "READY"
    proc.stdin.close()
    assert proc.wait(timeout=15) == 0
