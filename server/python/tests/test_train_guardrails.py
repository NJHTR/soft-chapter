"""训练护栏测试: TRAIN_MAX_SECONDS 时长预算、样本上限"""

import os

import pytest

from ai_pipeline import (
    TimeBudget,
    resolve_time_budget_seconds,
    sample_cap,
)


class FakeClock:
    def __init__(self):
        self.t = 0.0

    def __call__(self):
        return self.t


def test_time_budget_default_is_24h(monkeypatch):
    monkeypatch.delenv("TRAIN_MAX_SECONDS", raising=False)
    assert resolve_time_budget_seconds() == 24 * 3600


def test_time_budget_from_env(monkeypatch):
    monkeypatch.setenv("TRAIN_MAX_SECONDS", "600")
    assert resolve_time_budget_seconds() == 600


def test_time_budget_invalid_env(monkeypatch):
    monkeypatch.setenv("TRAIN_MAX_SECONDS", "not-a-number")
    with pytest.raises(ValueError):
        resolve_time_budget_seconds()


def test_time_budget_negative_env(monkeypatch):
    monkeypatch.setenv("TRAIN_MAX_SECONDS", "-5")
    with pytest.raises(ValueError):
        resolve_time_budget_seconds()


def test_time_budget_expiry():
    clock = FakeClock()
    budget = TimeBudget(seconds=100, now_fn=clock)
    assert not budget.expired()
    clock.t = 99.9
    assert not budget.expired()
    assert budget.remaining() > 0
    clock.t = 100.0
    assert budget.expired()
    assert budget.remaining() <= 0


def test_sample_cap_math():
    assert sample_cap(max_steps=500, grad_accum=4, per_device_batch=1) == 2000
    assert sample_cap(max_steps=1, grad_accum=1, per_device_batch=1) == 1
    assert sample_cap(max_steps=100, grad_accum=8, per_device_batch=1) == 800


def test_budget_honored_in_plan(tmp_path, monkeypatch):
    monkeypatch.setenv("TRAIN_MAX_SECONDS", "30")
    from ai_pipeline import build_train_plan

    data = tmp_path / "train.jsonl"
    val = tmp_path / "val.jsonl"
    for p in (data, val):
        p.write_text(
            '{"keyword": "k", "summary": "s", "context": {}, "style": "professional"}\n'
            * 20,
            encoding="utf-8",
        )
    plan = build_train_plan(
        type(
            "Args",
            (),
            {
                "data": str(data),
                "val": str(val),
                "output": str(tmp_path / "ckpt"),
                "max_steps": 10,
                "grad_accum": 2,
                "max_seq_len": 64,
                "rank": 8,
                "alpha": 16,
                "resume_from": None,
            },
        )()
    )
    assert plan["max_seconds"] == 30
    assert plan["loaded_rows"] == 20  # 样本上限 = 10*2*1 = 20
