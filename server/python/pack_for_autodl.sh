#!/bin/bash
# ============================================================================
# 本地打包脚本 — 生成 AutoDL 上传用 zip (完整版)
#
# 用法:
#   chmod +x pack_for_autodl.sh
#   ./pack_for_autodl.sh
#
# 输出: autodl_upload.zip (上传到 AutoDL 然后解压)
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TMPDIR="$SCRIPT_DIR/autodl_pack"
ZIPFILE="$SCRIPT_DIR/autodl_upload.zip"

echo "打包 AutoDL 训练文件 (完整版)..."
echo ""

# 清理旧文件
rm -rf "$TMPDIR" "$ZIPFILE"
mkdir -p "$TMPDIR"

# === 核心训练脚本 (完整 distill 目录) ===
echo "[1/7] 蒸馏训练脚本..."
cp -r "$SCRIPT_DIR/distill" "$TMPDIR/distill"

# === 清理 __pycache__ ===
find "$TMPDIR" -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find "$TMPDIR" -name "*.pyc" -delete 2>/dev/null || true

# === 蒸馏训练数据 ===
echo "[2/7] 蒸馏训练数据..."
cp "$SCRIPT_DIR/training_data_cleaned_train.jsonl" "$TMPDIR/" 2>/dev/null || echo "  注意: 缺少 training_data_cleaned_train.jsonl"
cp "$SCRIPT_DIR/training_data_cleaned_val.jsonl" "$TMPDIR/" 2>/dev/null || echo "  注意: 缺少 training_data_cleaned_val.jsonl"

# === 原始训练数据 (raw, 未清洗, 备用) ===
cp "$SCRIPT_DIR/training_data.jsonl" "$TMPDIR/" 2>/dev/null || echo "  注意: 缺少 training_data.jsonl"

# === 项目配置 ===
echo "[3/7] 项目配置文件..."
cp "$SCRIPT_DIR/pyproject.toml" "$TMPDIR/"
cp "$SCRIPT_DIR/uv.lock" "$TMPDIR/"
cp "$SCRIPT_DIR/requirements.txt" "$TMPDIR/" 2>/dev/null || true

# === 所有 Python 独立脚本 (非 distill 包) ===
echo "[4/7] 辅助 Python 脚本..."
cp "$SCRIPT_DIR/search_summary.py" "$TMPDIR/"
cp "$SCRIPT_DIR/extract_video_features.py" "$TMPDIR/"
cp "$SCRIPT_DIR/finetune_summary.py" "$TMPDIR/"
cp "$SCRIPT_DIR/export_training_data.py" "$TMPDIR/"
cp "$SCRIPT_DIR/correct_video_durations.py" "$TMPDIR/"

# === 标签词典 ===
echo "[5/7] 标签词典..."
cp "$SCRIPT_DIR/tag_vocabulary.json" "$TMPDIR/"

# === 现有适配器 (作为 fallback / 基线) ===
echo "[6/7] 现有 LoRA 适配器 (基线)..."
if [ -d "$SCRIPT_DIR/distill_lora_adapter" ]; then
    cp -r "$SCRIPT_DIR/distill_lora_adapter" "$TMPDIR/distill_lora_adapter"
    echo "  适配器已包含"
else
    echo "  注意: distill_lora_adapter 不存在"
fi

# === distil_output 目录 (如果有之前的训练产物) ===
if [ -d "$SCRIPT_DIR/distill_output" ]; then
    echo "  检测到 distill_output 目录, 一并打包..."
    cp -r "$SCRIPT_DIR/distill_output" "$TMPDIR/distill_output"
fi

# === 打包 ===
echo "[7/7] 压缩打包..."
cd "$TMPDIR/.."
zip -r "$ZIPFILE" "$(basename "$TMPDIR")" -q
rm -rf "$TMPDIR"

echo ""
echo "==========================================="
echo " 打包完成!"
echo "==========================================="
echo "文件: $ZIPFILE"
echo "大小: $(du -h "$ZIPFILE" | cut -f1)"
echo ""
echo "内容清单:"
unzip -l "$ZIPFILE" | tail -n +4 | head -n -2
echo ""
echo "下一步:"
echo "  1. 上传 $ZIPFILE 到 AutoDL"
echo "  2. AutoDL 终端执行:"
echo "     unzip autodl_upload.zip -d /root/autodl-tmp/"
echo "     cd /root/autodl-tmp/autodl_pack"
echo "     bash distill/autodl_run.sh"
echo ""
echo "  可选参数:"
echo "     bash distill/autodl_run.sh --max-samples 500000   # 省钱"
echo "     bash distill/autodl_run.sh --skip-stage1           # 只蒸馏"
echo "     bash distill/autodl_run.sh --dry-run               # 检查环境"
