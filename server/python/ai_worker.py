#!/usr/bin/env python3
"""
常驻 AI 推理 worker (JSON-lines stdin/stdout 协议, 与 Java 侧已锁定)

协议:
  * 启动: 模型就绪后打印一行 "READY"
  * 输入 (stdin, 每行一个 JSON 对象):
      {"id": "...", "prompt": "...", "max_new_tokens"?: 1..512}
      {"cmd": "ping"}   -> 心跳
      {"cmd": "exit"}   -> 退出 (退出码 0)
  * 输出 (stdout, 每行一个 JSON 对象, 协议外内容一律禁止):
      {"id": "...", "reply": "...", "model": "...", "tokens": N, "ms": N}
      {"id": "...", "error": "..."}
      {"cmd": "pong", "uptime_s": N}
  * stdin EOF -> 退出 (退出码 0)
  * 日志只写 stderr

用法:
  python ai_worker.py --serve --model echo
  python ai_worker.py --serve --model qwen2.5-3b --max-new-tokens 256 --max-context-length 2048
"""

import argparse
import hashlib
import json
import logging
import os
import sys
import time
from collections import OrderedDict

os.environ["HF_HUB_OFFLINE"] = "1"

if sys.platform == "win32":
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    stream=sys.stderr,
)
log = logging.getLogger("ai-worker")

MODEL_NAME = "Qwen/Qwen2.5-3B-Instruct"
SYSTEM_PROMPT = "你是抖音智能助手,回答简洁准确。"
MAX_NEW_TOKENS_HARD_CAP = 512
CACHE_SIZE = 256
CACHE_KEY_CHARS = 64


class LRUCache:
    def __init__(self, capacity):
        self._data = OrderedDict()
        self._capacity = capacity

    def get(self, key):
        if key not in self._data:
            return None
        self._data.move_to_end(key)
        return self._data[key]

    def put(self, key, value):
        if key in self._data:
            self._data.move_to_end(key)
        self._data[key] = value
        while len(self._data) > self._capacity:
            self._data.popitem(last=False)


def cache_key(prompt):
    head = prompt[:CACHE_KEY_CHARS].encode("utf-8")
    return hashlib.sha1(head).hexdigest()


class Qwen2_5_3B:
    def __init__(self, max_context_length):
        import torch

        self._torch = torch
        self.max_context_length = max_context_length
        log.info("设备探测中...")
        if torch.cuda.is_available():
            log.info("CUDA 可用, 优先 4bit NF4 (bitsandbytes)")
            self.tokenizer, self.model, self.device = self._load_cuda()
        else:
            log.warning("未检测到 CUDA, 使用 CPU fp32 (速度较慢)")
            self.tokenizer, self.model, self.device = self._load_cpu()
        self.model.eval()
        log.info("模型就绪: %s (device=%s)", MODEL_NAME, self.device)

    def _base_kwargs(self):
        return {
            "trust_remote_code": True,
            "local_files_only": True,
        }

    def _load_tokenizer(self):
        from transformers import AutoTokenizer

        return AutoTokenizer.from_pretrained(MODEL_NAME, **self._base_kwargs())

    def _load_cuda(self):
        import torch
        from transformers import AutoModelForCausalLM

        tokenizer = self._load_tokenizer()
        try:
            from transformers import BitsAndBytesConfig

            bnb_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type="nf4",
                bnb_4bit_compute_dtype=torch.float16,
                bnb_4bit_use_double_quant=True,
            )
            model = AutoModelForCausalLM.from_pretrained(
                MODEL_NAME,
                quantization_config=bnb_config,
                device_map="auto",
                torch_dtype=torch.float16,
                **self._base_kwargs(),
            )
            log.info("4bit NF4 加载成功")
            return tokenizer, model, "cuda"
        except Exception as e:
            log.warning("4bit 加载失败 (%s), 回退 fp16", e)
        model = AutoModelForCausalLM.from_pretrained(
            MODEL_NAME,
            torch_dtype=torch.float16,
            device_map="auto",
            **self._base_kwargs(),
        )
        return tokenizer, model, "cuda"

    def _load_cpu(self):
        import torch
        from transformers import AutoModelForCausalLM

        tokenizer = self._load_tokenizer()
        model = AutoModelForCausalLM.from_pretrained(
            MODEL_NAME,
            torch_dtype=torch.float32,
            **self._base_kwargs(),
        ).to("cpu")
        tokenizer.pad_token_id = tokenizer.eos_token_id
        return tokenizer, model, "cpu"

    def generate(self, prompt, max_new_tokens):
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ]
        text = self.tokenizer.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )
        inputs = self.tokenizer(
            text,
            return_tensors="pt",
            truncation="left",
            max_length=self.max_context_length,
        )
        input_len = inputs["input_ids"].shape[1]
        inputs = {k: v.to(self.model.device) for k, v in inputs.items()}
        with self._torch.no_grad():
            gen_ids = self.model.generate(
                **inputs,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                temperature=0.3,
                use_cache=True,
            )
        new_tokens = int(gen_ids.shape[1] - input_len)
        reply = self.tokenizer.decode(
            gen_ids[0][input_len:], skip_special_tokens=True
        ).strip()
        return reply, new_tokens


class EchoModel:
    """echo 模式: 不加载模型, 截断原样返回 (供无 GPU 环境与 Java 联调)"""

    def __init__(self, max_context_length):
        self.max_context_length = max_context_length

    def generate(self, prompt, max_new_tokens):
        reply = prompt[: self.max_context_length]
        return reply, max(1, len(reply))


def load_model(name, max_context_length):
    if name == "echo":
        log.info("echo 模式: 不加载模型")
        return EchoModel(max_context_length)
    if name == "qwen2.5-3b":
        return Qwen2_5_3B(max_context_length)
    raise ValueError(f"未知模型: {name}")


def main():
    parser = argparse.ArgumentParser(description="常驻 AI 推理 worker")
    parser.add_argument("--serve", action="store_true", help="常驻服务模式 (默认即服务, 该参数为兼容保留)")
    parser.add_argument("--model", default="echo", choices=["echo", "qwen2.5-3b"])
    parser.add_argument("--max-new-tokens", type=int, default=256)
    parser.add_argument("--max-context-length", type=int, default=2048)
    args = parser.parse_args()

    if not args.serve:
        log.warning("未显式指定 --serve, 仍以常驻模式运行")
    if args.max_new_tokens < 1:
        log.error("--max-new-tokens 必须 >= 1")
        return 2

    start_time = time.time()
    model = load_model(args.model, args.max_context_length)
    cache = LRUCache(CACHE_SIZE) if args.model == "qwen2.5-3b" else None

    print("READY", flush=True)

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except json.JSONDecodeError as e:
            print(json.dumps({"id": None, "error": f"无效请求 (JSON 解析失败): {e}"}), flush=True)
            continue

        cmd = req.get("cmd")
        if cmd == "ping":
            print(json.dumps({"cmd": "pong", "uptime_s": int(time.time() - start_time)}), flush=True)
            continue
        if cmd == "exit":
            print(json.dumps({"cmd": "pong", "uptime_s": int(time.time() - start_time)}), flush=True)
            return 0

        req_id = req.get("id")
        prompt = req.get("prompt", "")
        if not isinstance(prompt, str) or not prompt.strip():
            print(json.dumps({"id": req_id, "error": "请求缺少非空 prompt 字段"}), flush=True)
            continue

        max_new_tokens = args.max_new_tokens
        if req.get("max_new_tokens") is not None:
            try:
                max_new_tokens = int(req["max_new_tokens"])
            except (TypeError, ValueError):
                print(json.dumps({"id": req_id, "error": "max_new_tokens 必须为整数"}), flush=True)
                continue
        max_new_tokens = max(1, min(max_new_tokens, MAX_NEW_TOKENS_HARD_CAP))

        try:
            key = cache_key(prompt) if cache else None
            start = time.time()
            cached = cache.get(key) if cache else None
            if cached is not None:
                reply, tokens = cached
                ms = 0
                log.info("cache_hit id=%s", req_id)
            else:
                reply, tokens = model.generate(prompt, max_new_tokens)
                ms = int(round((time.time() - start) * 1000))
                if cache is not None:
                    cache.put(key, (reply, tokens))
            log.info("id=%s model=%s tokens=%d ms=%d", req_id, args.model, tokens, ms)
            print(
                json.dumps(
                    {
                        "id": req_id,
                        "reply": reply,
                        "model": args.model,
                        "tokens": tokens,
                        "ms": ms,
                    }
                ),
                flush=True,
            )
        except Exception as e:
            log.exception("请求处理失败 id=%s", req_id)
            print(json.dumps({"id": req_id, "error": str(e)}), flush=True)

    return 0


if __name__ == "__main__":
    sys.exit(main())