#!/bin/bash
# ============================================================================
# 本地打包脚本 — 生成 AutoDL 上传用 zip
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

echo "打包 AutoDL 训练文件..."
echo ""

# 清理旧文件
rm -rf "$TMPDIR" "$ZIPFILE"
mkdir -p "$TMPDIR"

# === 核心训练脚本 ===
cp -r "$SCRIPT_DIR/distill" "$TMPDIR/distill"

# === 蒸馏训练数据 ===
cp "$SCRIPT_DIR/training_data_cleaned_train.jsonl" "$TMPDIR/" 2>/dev/null || echo "  注意: 缺少 training_data_cleaned_train.jsonl"
cp "$SCRIPT_DIR/training_data_cleaned_val.jsonl" "$TMPDIR/" 2>/dev/null || echo "  注意: 缺少 training_data_cleaned_val.jsonl"

# === 项目配置 ===
cp "$SCRIPT_DIR/pyproject.toml" "$TMPDIR/"
cp "$SCRIPT_DIR/uv.lock" "$TMPDIR/"
cp "$SCRIPT_DIR/requirements.txt" "$TMPDIR/" 2>/dev/null || true

# === 清理 __pycache__ ===
find "$TMPDIR" -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find "$TMPDIR" -name "*.pyc" -delete 2>/dev/null || true

# === 打包 ===
cd "$TMPDIR/.."
zip -r "$ZIPFILE" "$(basename "$TMPDIR")" -q
rm -rf "$TMPDIR"

echo ""
echo "打包完成: $ZIPFILE"
echo "大小: $(du -h "$ZIPFILE" | cut -f1)"
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
