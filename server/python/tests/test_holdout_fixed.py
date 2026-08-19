"""eval_holdout.jsonl 固定性测试: sha256 与本次提交冻结值一致, 防止评测集漂移"""

import hashlib
import json
import os

SERVER_PYTHON = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOLDOUT = os.path.join(SERVER_PYTHON, "eval_holdout.jsonl")

FROZEN_SHA256 = "580af48612056ded325a8401a5a42843af01619caaebf1d4585ecf9b6921bbc0"


def _sha256(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def test_holdout_sha256_frozen():
    assert os.path.isfile(HOLDOUT)
    assert _sha256(HOLDOUT) == FROZEN_SHA256


def test_holdout_structure():
    items = []
    with open(HOLDOUT, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                items.append(json.loads(line))
    assert len(items) == 10
    ids = [i["id"] for i in items]
    assert len(ids) == len(set(ids)), "id 必须唯一"
    for item in items:
        assert item["prompt"].strip()
        etype = item["expectation"]["type"]
        assert etype in ("contains", "refuse")
        if etype == "contains":
            assert item["expectation"]["value"].strip()
        else:
            assert "value" not in item["expectation"]


def test_holdout_has_two_refusal_entries():
    items = [json.loads(l) for l in open(HOLDOUT, "r", encoding="utf-8") if l.strip()]
    refuse = [i for i in items if i["expectation"]["type"] == "refuse"]
    assert len(refuse) == 2
