"""pii_mask 脱敏规则: 规则对齐 DATA_CONTRACT §4, 幂等, 训练行扫描无残留"""

import json
import os

from ai_pipeline import pii_mask, pii_residual, pii_scan_lines

SERVER_PYTHON = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_phone_mask():
    assert pii_mask("13812348000") == "138****8000"


def test_id_card_masks():
    assert pii_mask("110101199001011234") == "110101**********34"
    assert pii_mask("11010119900101123X") == "110101**********3X"
    assert pii_mask("110101900101123") == "1101**********3"


def test_email_mask():
    assert pii_mask("a@b.com") == "a***@b***.com"
    assert pii_mask("user@dom.com") == "u***@dom***.com"


def test_ip_mask():
    assert pii_mask("8.134.23.170") == "8.134.***.***"


def test_bank_card_mask():
    assert pii_mask("6222123456781234") == "6222********1234"


def test_sk_key_mask():
    test_key = "sk-test000000000000000000000000"
    out = pii_mask(test_key)
    assert out.startswith("sk-test")
    assert "\u2022\u2022\u2022" in out
    assert test_key[4:] not in out


def test_documented_scenario():
    text = "电话 13812348000 邮箱 a@b.com 卡号 6222123456781234"
    out = pii_mask(text)
    assert "138****8000" in out
    assert "a***@b***.com" in out
    assert "6222********1234" in out


def test_idempotent():
    samples = [
        "电话 13812348000 邮箱 a@b.com 卡号 6222123456781234 IP 8.134.23.170",
        "身份证 11010119900101123X 联系 user@dom.com",
        "密钥 sk-test000000000000000000000000 已轮换",
        "普通文本没有敏感信息 2025-08-14",
    ]
    for s in samples:
        once = pii_mask(s)
        twice = pii_mask(once)
        assert once == twice, f"脱敏不幂等: {s!r}"


def test_residual_detection():
    assert pii_residual("联系方式 13812348000")
    assert pii_residual("邮箱 a@b.com")
    assert not pii_residual("联系方式 138****8000")
    assert not pii_residual("已脱敏 110101**********34")


def test_training_lines_have_no_residual_pii():
    """训练数据强制二次校验: 逐行扫描, 不得存在未掩码 PII"""
    path = os.path.join(SERVER_PYTHON, "training_data_cleaned_train.jsonl")
    if not os.path.isfile(path):
        import pytest

        pytest.skip("训练数据文件缺失")
    dirty = []
    with open(path, "r", encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            payload = "\n".join(
                str(item.get(k, ""))
                for k in ("keyword", "summary", "user_msg", "assistant_msg")
            )
            if pii_residual(payload):
                dirty.append(lineno)
                if len(dirty) >= 5:
                    break
    assert not dirty, f"训练数据存在 PII 残留行: {dirty}"


def test_pii_scan_lines_skips_dirty_rows():
    clean_rows = [
        {"id": "ok1", "prompt": "普通问题"},
        {"id": "ok2", "prompt": "再一个普通问题"},
    ]
    dirty_rows = [{"id": "bad1", "prompt": "电话 13812348000 联系我"}]
    rows = clean_rows + dirty_rows
    clean, skipped, skipped_ids = pii_scan_lines(rows)
    assert len(clean) == 2
    assert skipped == 1
    assert skipped_ids == ["bad1"]
