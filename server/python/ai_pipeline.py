#!/usr/bin/env python3
"""
AI 训练与评测入口 (M5/M6)

用法:
  python ai_pipeline.py eval --provider {echo|local|api} \
      [--holdout eval_holdout.jsonl] [--out metrics.json] [--limit N]
  python ai_pipeline.py train --dry-run \
      [--data training_data_cleaned_train.jsonl] [--val training_data_cleaned_val.jsonl] \
      [--rank 8] [--alpha 16] [--max-steps 500] [--max-seq-len 768] \
      [--grad-accum 4] [--output ./lora_checkpoint] [--resume-from <checkpoint>]

讲解:
  * train: 训练入口 (QLoRA LoRA 微调)。必须可先 --dry-run 校验数据与配置。
  * eval:  固定 holdout 评测, 输出 metrics.json。
  * 脱敏: pii_mask / pii_residual 与 Java PiiMasker 同一规则表
    (见 docs/ai-agent/DATA_CONTRACT.md 第 4 节), 训练与评测入口强制二次校验。
"""

import argparse
import json
import logging
import math
import os
import re
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone

os.environ["HF_HUB_OFFLINE"] = "1"

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    stream=sys.stderr,
)
log = logging.getLogger("ai-pipeline")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_MODEL = "Qwen/Qwen2.5-3B-Instruct"
SYSTEM_PROMPT = "你是抖音智能助手,回答简洁准确。"

# ===================== PII 脱敏 (与 Java PiiMasker 同规则, DATA_CONTRACT §4) =====================

_SK_KEY = re.compile(r"(?:^|[^A-Za-z0-9])(sk-[A-Za-z0-9]{16,})")
_EMAIL = re.compile(r"([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+)\.([A-Za-z]{2,})")
_IP = re.compile(r"(?<!\d)(\d{1,3}(?:\.\d{1,3}){3})(?!\d)")
_ID18 = re.compile(r"(?<!\d)(\d{6})\d{10}([\dXx]{2})(?![\dXx])")
_ID15 = re.compile(r"(?<!\d)(\d{4})\d{10}(\d)(?!\d)")
_BANK = re.compile(r"(?<!\d)([456]\d{12,18})(?!\d)")
_PHONE = re.compile(r"(?<!\d)(1[3-9]\d{9})(?!\d)")

# 残留检测模式 (未掩码形态才匹配)
_RESIDUAL_PATTERNS = [_SK_KEY, _EMAIL, _IP, _ID18, _ID15, _BANK, _PHONE]


def _mask_sk(m):
    return "sk-" + m.group(1)[3:7] + "\u2022" * 10


def _mask_email(m):
    local, dom, tld = m.group(1), m.group(2), m.group(3)

    def part(s, keep):
        return s[:keep] + "*" * max(3, len(s) - keep)

    return f"{part(local, 1)}@{part(dom, 3)}.{tld}"


def _mask_ip(m):
    parts = m.group(1).split(".")
    return f"{parts[0]}.{parts[1]}.***.***"


def _mask_id18(m):
    return m.group(1) + "*" * 10 + m.group(2)


def _mask_id15(m):
    return m.group(1) + "*" * 10 + m.group(2)


def _mask_bank(m):
    digits = m.group(1)
    return digits[:4] + "*" * (len(digits) - 8) + digits[-4:]


def _mask_phone(m):
    return m.group(1)[:3] + "****" + m.group(1)[-4:]


_MASK_STEPS = [
    (_SK_KEY, _mask_sk),
    (_EMAIL, _mask_email),
    (_IP, _mask_ip),
    (_ID18, _mask_id18),
    (_ID15, _mask_id15),
    (_BANK, _mask_bank),
    (_PHONE, _mask_phone),
]

REFUSAL_WORDS = ("无法回答", "抱歉", "不能", "无法提供", "拒绝回答", "不建议", "不允许", "暂无")


def pii_mask(text):
    """幂等脱敏: 手机/身份证/邮箱/IP/银行卡/sk- 密钥 (与 Java PiiMasker 同规则)"""
    out = text
    for pattern, repl in _MASK_STEPS:
        out = pattern.sub(repl, out)
    if _SK_KEY.search(out):
        out = _SK_KEY.sub(_mask_sk, out)
    return out


def pii_residual(text):
    """检测文本中是否仍存在未掩码的 PII 模式"""
    return any(p.search(text) for p in _RESIDUAL_PATTERNS)


def pii_scan_lines(lines, label="数据"):
    """扫描行列表, 返回 (干净行, 含 PII 行数, 含 PII 行 id/key)"""
    clean, skipped = [], []
    for item in lines:
        if isinstance(item, dict):
            payload = "\n".join(
                str(item.get(k, "")) for k in ("keyword", "summary", "user_msg", "assistant_msg", "prompt")
            )
        else:
            payload = str(item)
        if pii_residual(payload):
            skipped.append(item.get("id") or item.get("keyword") or "<unknown>")
            log.warning("PII 残留检测: %s 跳过一行 (id/key=%s)", label, skipped[-1])
        else:
            clean.append(item)
    if skipped:
        log.warning("%s 共跳过 %d 行 PII 残留样本", label, len(skipped))
    return clean, len(skipped), skipped


# ===================== 路径/预算工具 =====================

def resolve_path(path, is_default):
    if os.path.isabs(path) or not is_default:
        return os.path.normpath(path)
    return os.path.normpath(os.path.join(SCRIPT_DIR, path))


def sample_cap(max_steps, grad_accum, per_device_batch):
    return max_steps * grad_accum * per_device_batch


def resolve_time_budget_seconds():
    """TRAIN_MAX_SECONDS 环境变量, 默认 24h"""
    raw = os.environ.get("TRAIN_MAX_SECONDS", "").strip()
    if not raw:
        return 24 * 3600
    try:
        value = int(raw)
    except ValueError:
        raise ValueError(f"TRAIN_MAX_SECONDS 必须是整数秒, 当前值: {raw!r}")
    if value < 0:
        raise ValueError(f"TRAIN_MAX_SECONDS 不能为负: {value}")
    return value


class TimeBudget:
    """训练时长预算 (默认 24h, TRAIN_MAX_SECONDS 可配; 到点优雅保存退出)"""

    def __init__(self, seconds, now_fn=time.time):
        self.seconds = seconds
        self._now_fn = now_fn
        self._start = now_fn()

    def elapsed(self):
        return self._now_fn() - self._start

    def remaining(self):
        return self.seconds - self.elapsed()

    def expired(self):
        return self.elapsed() >= self.seconds


def estimate_vram_gb(rank):
    """QLoRA 4bit 估算 (信息参考, 非精确)"""
    base = 2.4
    lora = rank * 0.02
    activations = 1.2
    return round(base + lora + activations, 2)


# ===================== Worker 客户端 (JSON-lines 协议) =====================

def _read_line_until(stream, timeout_s):
    deadline = time.time() + timeout_s
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


class WorkerClient:
    def __init__(self, model, ready_timeout=120):
        worker_script = os.path.join(SCRIPT_DIR, "ai_worker.py")
        env = dict(os.environ)
        env["PYTHONIOENCODING"] = "utf-8"
        self.proc = subprocess.Popen(
            [sys.executable, worker_script, "--serve", "--model", model],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            bufsize=1,
            env=env,
        )
        self._stderr_lines = []
        self._stderr_thread = threading.Thread(
            target=self._drain_stderr, daemon=True
        )
        self._stderr_thread.start()
        line = _read_line_until(self.proc.stdout, ready_timeout)
        if line != "READY":
            raise RuntimeError(f"worker 启动失败, 预期 READY 实际: {line!r}")

    def _drain_stderr(self):
        for line in self.proc.stderr:
            self._stderr_lines.append(line.rstrip("\n"))

    def request(self, req_id, prompt, max_new_tokens=None):
        payload = {"id": req_id, "prompt": prompt}
        if max_new_tokens is not None:
            payload["max_new_tokens"] = max_new_tokens
        self._write(payload)
        resp = json.loads(_read_line_until(self.proc.stdout, 180))
        if resp.get("id") != req_id:
            raise RuntimeError(f"worker 响应 id 不匹配: {resp.get('id')!r} != {req_id!r}")
        return resp

    def ping(self):
        self._write({"cmd": "ping"})
        return json.loads(_read_line_until(self.proc.stdout, 30))

    def _write(self, payload):
        if self.proc.poll() is not None:
            raise RuntimeError("worker 进程已退出")
        self.proc.stdin.write(json.dumps(payload, ensure_ascii=False) + "\n")
        self.proc.stdin.flush()

    def close(self):
        if self.proc.poll() is None:
            try:
                self._write({"cmd": "exit"})
                self.proc.wait(timeout=10)
            except Exception:
                self.proc.kill()


# ===================== DeepSeek 远端 provider =====================

def _call_deepseek(prompt, req_id, timeout=60):
    import httpx

    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("DEEPSEEK_API_KEY 未配置: api provider 需要环境变量 DEEPSEEK_API_KEY")
    base_url = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
    model = os.environ.get("DEEPSEEK_MODEL", "deepseek-chat")
    resp = httpx.post(
        f"{base_url}/v1/chat/completions",
        headers={"Authorization": f"Bearer {api_key}"},
        json={
            "model": model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            "max_tokens": 512,
            "temperature": 0.3,
        },
        timeout=timeout,
    )
    resp.raise_for_status()
    data = resp.json()
    reply = data["choices"][0]["message"]["content"].strip()
    usage = data.get("usage") or {}
    tokens = (usage.get("completion_tokens") or 0) + (usage.get("prompt_tokens") or 0)
    return reply, tokens


# ===================== 评测 (eval) =====================

def _p95(sorted_ms):
    if not sorted_ms:
        return 0
    idx = max(0, math.ceil(0.95 * len(sorted_ms)) - 1)
    return round(sorted_ms[idx], 1)


def run_eval(args):
    holdout_path = resolve_path(args.holdout, args.holdout == "eval_holdout.jsonl")
    out_path = resolve_path(args.out, args.out == "metrics.json")

    if not os.path.isfile(holdout_path):
        log.error("holdout 文件不存在: %s", holdout_path)
        return 1

    items = []
    with open(holdout_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            items.append(item)
    if args.limit is not None:
        items = items[: args.limit]

    log.info("加载 holdout %d 条 (provider=%s)", len(items), args.provider)

    clean, skipped_pii, _ = pii_scan_lines(items, label="holdout")
    pii_ids = set()
    for item in items:
        if item not in clean:
            pii_ids.add(item.get("id"))

    client = None
    try:
        if args.provider == "echo":
            client = WorkerClient("echo")
        elif args.provider == "local":
            client = WorkerClient("qwen2.5-3b")
        elif args.provider != "api":
            log.error("未知 provider: %s", args.provider)
            return 1

        results = []
        for item in items:
            req_id = item.get("id")
            prompt = item.get("prompt", "")
            expectation = item.get("expectation") or {}
            etype = expectation.get("type")
            evalue = expectation.get("value", "")

            if req_id in pii_ids:
                results.append(
                    {"id": req_id, "reply": "", "error": "pii_residual", "ms": 0, "etype": etype, "evalue": evalue}
                )
                continue

            start = time.time()
            try:
                if args.provider == "api":
                    reply, _tokens = _call_deepseek(prompt, req_id)
                else:
                    resp = client.request(req_id, prompt)
                    if resp.get("error"):
                        raise RuntimeError(resp["error"])
                    reply = resp.get("reply") or ""
                ms = int(round((time.time() - start) * 1000))
                results.append(
                    {"id": req_id, "reply": reply, "error": None, "ms": ms, "etype": etype, "evalue": evalue}
                )
            except Exception as e:
                ms = int(round((time.time() - start) * 1000))
                log.warning("条目 %s 失败: %s", req_id, e)
                results.append(
                    {"id": req_id, "reply": "", "error": str(e), "ms": ms, "etype": etype, "evalue": evalue}
                )
    finally:
        if client is not None:
            client.close()

    total = len(results)
    failures = [
        {"id": r["id"], "error": r["error"]}
        for r in results
        if r["error"] is not None or not r["reply"].strip()
    ]
    passed = total - len(failures)

    def _match(r):
        reply = r["reply"]
        if r["etype"] == "refuse":
            return any(w in reply for w in REFUSAL_WORDS)
        if r["etype"] == "contains":
            return r["evalue"] in reply
        return bool(reply.strip())

    ok = [r for r in results if not r["error"] and r["reply"].strip()]
    format_ok = [r for r in ok if r["etype"] in ("contains", "refuse") and _match(r)]
    format_total = [r for r in ok if r["etype"] in ("contains", "refuse")]
    refuse_ok = [r for r in ok if r["etype"] == "refuse" and _match(r)]
    refuse_total = [r for r in ok if r["etype"] == "refuse"]

    latencies = sorted(r["ms"] for r in results if r["ms"] > 0)
    metrics = {
        "provider": args.provider,
        "holdout": os.path.basename(holdout_path),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "total": total,
        "passed": passed,
        "task_success_rate": round(passed / total, 4) if total else 0.0,
        "format_accuracy": round(len(format_ok) / len(format_total), 4) if format_total else 0.0,
        "refusal_accuracy": round(len(refuse_ok) / len(refuse_total), 4) if refuse_total else 0.0,
        "avg_latency_ms": round(sum(r["ms"] for r in results) / total, 1) if total else 0.0,
        "p95_latency_ms": _p95(latencies),
        "max_latency_ms": max(latencies) if latencies else 0,
        "failures": failures,
    }

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)
    log.info(
        "评测完成: total=%d passed=%d 成功率=%.2f 格式=%.2f 拒答=%.2f 失败=%d -> %s",
        total, passed, metrics["task_success_rate"], metrics["format_accuracy"],
        metrics["refusal_accuracy"], len(failures), out_path,
    )
    return 0


# ===================== 训练数据 (train) =====================

def _load_data_lines(path, cap):
    """读取前 cap 行 JSONL"""
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
            if cap and len(rows) >= cap:
                break
    return rows


def _count_lines(path):
    n = 0
    with open(path, "r", encoding="utf-8") as f:
        for _ in f:
            n += 1
    return n


def _to_chat(item):
    """兼容两种导出形态: 摘要类 {keyword, summary} / 对话类 {user_msg, assistant_msg}"""
    if item.get("keyword") and item.get("summary"):
        user = f"用户搜索了「{item['keyword']}」。请生成一段简洁准确的搜索摘要。"
        return user, item["summary"]
    if item.get("user_msg") and item.get("assistant_msg"):
        return item["user_msg"], item["assistant_msg"]
    raise ValueError(f"无法识别的训练样本结构: {sorted(item.keys())}")


class LocalDataset:
    def __init__(self, rows, tokenizer, max_seq_len):
        import torch

        self._torch = torch
        self.examples = []
        for user, assistant in rows:
            messages = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user},
                {"role": "assistant", "content": assistant},
            ]
            text = tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=False
            )
            prompt_text = tokenizer.apply_chat_template(
                messages[:-1], tokenize=False, add_generation_prompt=True
            )
            enc = tokenizer(text, truncation=True, max_length=max_seq_len)
            prompt_len = len(
                tokenizer(prompt_text, add_special_tokens=False)["input_ids"]
            )
            labels = list(enc["input_ids"])
            labels[:prompt_len] = [-100] * prompt_len
            self.examples.append(
                {"input_ids": enc["input_ids"], "attention_mask": enc["attention_mask"], "labels": labels}
            )

    def __len__(self):
        return len(self.examples)

    def __getitem__(self, idx):
        return {k: self._torch.tensor(v) for k, v in self.examples[idx].items()}


def build_train_plan(args):
    data_path = resolve_path(args.data, args.data == "training_data_cleaned_train.jsonl")
    val_path = resolve_path(args.val, args.val == "training_data_cleaned_val.jsonl")
    out_dir = resolve_path(args.output, args.output == "./lora_checkpoint")
    if not os.path.isfile(data_path):
        raise FileNotFoundError(f"训练数据不存在: {data_path}")
    if not os.path.isfile(val_path):
        raise FileNotFoundError(f"验证数据不存在: {val_path}")

    total_rows = _count_lines(data_path)
    cap = sample_cap(args.max_steps, args.grad_accum, 1)
    rows = _load_data_lines(data_path, cap)
    clean_rows, skipped_pii, _ = pii_scan_lines(rows, label="训练数据")
    effective_batch = args.grad_accum * 1
    return {
        "base_model": BASE_MODEL,
        "data_path": data_path,
        "val_path": val_path,
        "out_dir": out_dir,
        "total_rows": total_rows,
        "loaded_rows": len(rows),
        "clean_rows": len(clean_rows),
        "skipped_pii": skipped_pii,
        "per_device_batch": 1,
        "grad_accum": args.grad_accum,
        "effective_batch": effective_batch,
        "max_steps": args.max_steps,
        "max_seq_len": args.max_seq_len,
        "rank": args.rank,
        "alpha": args.alpha,
        "resume_from": args.resume_from,
        "vram_est_gb": estimate_vram_gb(args.rank),
        "max_seconds": resolve_time_budget_seconds(),
    }


def print_dry_run_plan(plan):
    print("========== 训练计划 (dry-run) ==========")
    print(f"基础模型      : {plan['base_model']} (HF 本地缓存, local_files_only)")
    print(f"训练数据      : {plan['data_path']} (共 {plan['total_rows']} 行)")
    print(f"  实际加载    : {plan['loaded_rows']} 行 (样本上限 max_steps*grad_accum*batch)")
    print(f"  PII 跳过    : {plan['skipped_pii']} 行")
    print(f"验证数据      : {plan['val_path']}")
    print(f"批次计划      : per_device_batch={plan['per_device_batch']} grad_accum={plan['grad_accum']} "
          f"effective_batch={plan['effective_batch']} max_steps={plan['max_steps']}")
    print(f"序列长度      : max_seq_len={plan['max_seq_len']} (截断)")
    print(f"LoRA          : rank={plan['rank']} alpha={plan['alpha']} target=q/k/v/o_proj")
    print(f"输出目录      : {plan['out_dir']}")
    print(f"提前停止      : 每 100 步 eval, 早停 patience=2")
    if plan["resume_from"]:
        print(f"断点恢复      : {plan['resume_from']}")
    print(f"时长预算      : {plan['max_seconds']}s (TRAIN_MAX_SECONDS, 到点保存退出)")
    print(f"预估显存      : ~{plan['vram_est_gb']}GB (QLoRA 4bit, 仅供参考)")
    print(f"恢复/输出      : 本次为 dry-run, 不创建任何 checkpoint/适配器文件")
    print("========================================")


def _load_peft_model(torch, plan):
    from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

    if torch.cuda.is_available():
        kwargs = {"device_map": "auto", "trust_remote_code": True, "local_files_only": True}
        try:
            bnb_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type="nf4",
                bnb_4bit_compute_dtype=torch.float16,
                bnb_4bit_use_double_quant=True,
            )
            model = AutoModelForCausalLM.from_pretrained(
                BASE_MODEL, quantization_config=bnb_config, torch_dtype=torch.float16, **kwargs
            )
            log.info("QLoRA: 4bit NF4 加载成功")
        except Exception as e:
            log.warning("4bit 加载失败 (%s), 回退 bf16", e)
            model = AutoModelForCausalLM.from_pretrained(
                BASE_MODEL, torch_dtype=torch.bfloat16, **kwargs
            )
            log.info("QLoRA: bf16 兜底加载成功")
    else:
        log.warning("无 CUDA, 使用 CPU fp32 训练 (极慢, 仅建议小数据验证)")
        model = AutoModelForCausalLM.from_pretrained(
            BASE_MODEL, torch_dtype=torch.float32,
            trust_remote_code=True, local_files_only=True,
        ).to("cpu")
    return model


def run_train(args):
    try:
        plan = build_train_plan(args)
    except (FileNotFoundError, ValueError) as e:
        log.error("训练配置校验失败: %s", e)
        return 1

    print_dry_run_plan(plan)

    if args.dry_run:
        log.info("dry-run 校验通过, 不创建任何文件")
        return 0

    import torch
    from transformers import (
        AutoTokenizer,
        DataCollatorForSeq2Seq,
        Trainer,
        TrainingArguments,
        TrainerCallback,
        EarlyStoppingCallback,
    )
    from peft import LoraConfig, TaskType, get_peft_model

    budget = TimeBudget(plan["max_seconds"])
    stopped_by_budget = {"flag": False}

    class TimeBudgetCallback(TrainerCallback):
        def on_step_end(self, args_, state, control, **kwargs):
            if state.global_step % 10 == 0 and budget.expired():
                log.warning("训练时长预算耗尽 (%ds), 保存后优雅退出", plan["max_seconds"])
                stopped_by_budget["flag"] = True
                control.should_save = True
                control.should_training_stop = True

    log.info("加载基础模型: %s", BASE_MODEL)
    tokenizer = AutoTokenizer.from_pretrained(
        BASE_MODEL, trust_remote_code=True, local_files_only=True
    )
    tokenizer.pad_token_id = tokenizer.eos_token_id
    model = _load_peft_model(torch, plan)

    lora_config = LoraConfig(
        r=plan["rank"],
        lora_alpha=plan["alpha"],
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    data_path = resolve_path(args.data, args.data == "training_data_cleaned_train.jsonl")
    val_path = resolve_path(args.val, args.val == "training_data_cleaned_val.jsonl")
    cap = sample_cap(args.max_steps, args.grad_accum, 1)
    rows = _load_data_lines(data_path, cap)
    clean_rows, skipped_pii, _ = pii_scan_lines(rows, label="训练数据")
    train_ds = LocalDataset(clean_rows, tokenizer, plan["max_seq_len"])
    val_rows = _load_data_lines(val_path, 256)
    val_ds = LocalDataset(val_rows, tokenizer, plan["max_seq_len"])

    use_bf16 = torch.cuda.is_available() and torch.cuda.is_bf16_supported()
    training_args = TrainingArguments(
        output_dir=plan["out_dir"],
        per_device_train_batch_size=1,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=plan["grad_accum"],
        learning_rate=2e-4,
        warmup_steps=10,
        max_steps=plan["max_steps"],
        bf16=use_bf16,
        fp16=torch.cuda.is_available() and not use_bf16,
        save_strategy="steps",
        save_steps=100,
        save_total_limit=2,
        eval_strategy="steps",
        eval_steps=100,
        logging_steps=10,
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
        gradient_checkpointing=True,
        max_grad_norm=1.0,
        seed=42,
        report_to=[],
        dataloader_num_workers=0,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        data_collator=DataCollatorForSeq2Seq(
            tokenizer, padding="longest", label_pad_token_id=-100
        ),
        callbacks=[
            EarlyStoppingCallback(early_stopping_patience=2),
            TimeBudgetCallback(),
        ],
    )

    res = trainer.train(resume_from_checkpoint=plan["resume_from"])
    if stopped_by_budget["flag"] or res.global_step < plan["max_steps"]:
        log.info("训练提前结束 (budget=%s, steps=%d), 保存最终 checkpoint", stopped_by_budget["flag"], res.global_step)
        trainer.save_model(plan["out_dir"] + "/final")

    losses = [h for h in trainer.state.log_history if "loss" in h]
    train_losses = [h["loss"] for h in losses if "eval_loss" not in h]
    eval_losses = [h["eval_loss"] for h in losses if "eval_loss" in h]

    print("========== 训练报告 ==========")
    print(f"数据行数       : 加载 {len(clean_rows)} (PII 跳过 {skipped_pii})")
    print(f"完成步数       : {res.global_step} / {plan['max_steps']} (max 硬上限)")
    print(f"train_loss     : 最后={train_losses[-1] if train_losses else 'N/A'} "
          f"最低={min(train_losses) if train_losses else 'N/A'}")
    print(f"eval_loss      : 最后={eval_losses[-1] if eval_losses else 'N/A'} "
          f"最低={min(eval_losses) if eval_losses else 'N/A'}")
    print(f"停止原因       : {'时长预算/早停' if res.global_step < plan['max_steps'] else '达到 max_steps'}")
    print(f"checkpoint     : {plan['out_dir']}")
    print("==============================")
    log.info("训练完成, 退出码 0")
    return 0


# ===================== 入口 =====================

def main(argv=None):
    parser = argparse.ArgumentParser(description="AI 训练与评测入口")
    sub = parser.add_subparsers(dest="command", required=True)

    p_eval = sub.add_parser("eval", help="固定 holdout 评测")
    p_eval.add_argument("--holdout", default="eval_holdout.jsonl")
    p_eval.add_argument("--provider", required=True, choices=["echo", "local", "api"])
    p_eval.add_argument("--out", default="metrics.json")
    p_eval.add_argument("--limit", type=int, default=None)
    p_eval.set_defaults(func=run_eval)

    p_train = sub.add_parser("train", help="LoRA/QLoRA 训练")
    p_train.add_argument("--dry-run", action="store_true", help="仅校验数据与配置并打印计划, 不训练")
    p_train.add_argument("--data", default="training_data_cleaned_train.jsonl")
    p_train.add_argument("--val", default="training_data_cleaned_val.jsonl")
    p_train.add_argument("--rank", type=int, default=8)
    p_train.add_argument("--alpha", type=int, default=16)
    p_train.add_argument("--max-steps", type=int, default=500)
    p_train.add_argument("--max-seq-len", type=int, default=768)
    p_train.add_argument("--grad-accum", type=int, default=4)
    p_train.add_argument("--output", default="./lora_checkpoint")
    p_train.add_argument("--resume-from", default=None)
    p_train.set_defaults(func=run_train)

    args = parser.parse_args(argv)
    if args.command == "train" and (args.max_steps < 1 or args.grad_accum < 1 or args.max_seq_len < 1):
        parser.error("--max-steps/--grad-accum/--max-seq-len 必须 >= 1")
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())