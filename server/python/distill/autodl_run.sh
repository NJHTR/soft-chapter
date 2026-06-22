#!/bin/bash
# ============================================================================
# AutoDL 一键训练脚本 — 两阶段搜索摘要蒸馏
#
# 用法:
#   chmod +x autodl_run.sh
#   ./autodl_run.sh                          # 全量 100 万条公开数据
#   ./autodl_run.sh --max-samples 500000     # 50 万条 (省钱)
#   ./autodl_run.sh --skip-stage1            # 跳过公开数据, 只做蒸馏
#   ./autodl_run.sh --dry-run                # 只检查环境, 不训练
#
# 输出: distill_output/stage2_search_adapter/ (拷贝回本地用)
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PYTHON_DIR="$(dirname "$SCRIPT_DIR")"
WORK_DIR="$PYTHON_DIR"
PUBLIC_DATA_DIR="$WORK_DIR/public_data"

# ---- 参数 ----
MAX_SAMPLES=1000000
SKIP_STAGE1=false
DRY_RUN=false
BASE_MODEL="Qwen/Qwen3-8B-Instruct"

while [[ $# -gt 0 ]]; do
    case $1 in
        --max-samples) MAX_SAMPLES="$2"; shift 2 ;;
        --skip-stage1) SKIP_STAGE1=true; shift ;;
        --dry-run)     DRY_RUN=true; shift ;;
        --base-model)  BASE_MODEL="$2"; shift 2 ;;
        --help|-h)
            echo "用法: ./autodl_run.sh [选项]"
            echo ""
            echo "选项:"
            echo "  --max-samples N    公开数据最大条数 (默认 1000000)"
            echo "  --skip-stage1      跳过公开数据预训练, 只做蒸馏"
            echo "  --dry-run          只检查环境, 不训练"
            echo "  --base-model NAME  基础模型名 (默认 Qwen/Qwen3-8B-Instruct)"
            exit 0 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

echo "============================================"
echo " SeekFlow 搜索摘要蒸馏 · 两阶段训练"
echo "============================================"
echo "工作目录:   $WORK_DIR"
echo "公开数据量: $MAX_SAMPLES 条"
echo "基础模型:   $BASE_MODEL"
echo "跳过阶段1:  $SKIP_STAGE1"
echo "仅检查:     $DRY_RUN"
echo "============================================"
echo ""

# ---- 环境准备 ----
cd "$WORK_DIR"

echo "[1/5] 检查 Python 环境..."
python3 --version || python --version

if command -v nvidia-smi &> /dev/null; then
    echo ""
    nvidia-smi --query-gpu=name,memory.total --format=csv,noheader 2>/dev/null || true
    echo ""
fi

echo "[2/5] 安装依赖..."
if ! command -v uv &> /dev/null; then
    pip install uv -q
fi
uv sync --extra train 2>/dev/null || uv sync 2>/dev/null || {
    echo "uv sync 失败, 尝试 pip..."
    pip install -r requirements.txt -q
}
echo "  依赖就绪"

# ---- 下载模型 (提前缓存, 避免训练时下载超时) ----
echo ""
echo "[3/5] 预下载基础模型 (避免训练中途断网)..."
python3 -c "
import os
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
from transformers import AutoTokenizer
print('下载 tokenizer...')
AutoTokenizer.from_pretrained('$BASE_MODEL', trust_remote_code=True)
print('tokenizer 就绪')
" || echo "  模型预下载失败, 训练时会自动重试"

# ---- 准备公开数据 ----
echo ""
echo "[4/5] 准备公开摘要数据..."
if $SKIP_STAGE1; then
    echo "  跳过阶段1 (--skip-stage1)"
else
    python3 -m distill.prepare_public_data \
        --max_samples "$MAX_SAMPLES" \
        --output_dir "$PUBLIC_DATA_DIR"

    STAGE1_TRAIN="$PUBLIC_DATA_DIR/public_summary_train.jsonl"
    STAGE1_VAL="$PUBLIC_DATA_DIR/public_summary_val.jsonl"

    if [[ ! -f "$STAGE1_TRAIN" ]]; then
        echo "  错误: 公开数据准备失败, 缺少 $STAGE1_TRAIN"
        exit 1
    fi
    echo "  训练集: $(wc -l < "$STAGE1_TRAIN") 条"
    echo "  验证集: $(wc -l < "$STAGE1_VAL") 条"
fi

# ---- 检查蒸馏数据 ----
STAGE2_TRAIN="$WORK_DIR/training_data_cleaned_train.jsonl"
STAGE2_VAL="$WORK_DIR/training_data_cleaned_val.jsonl"

if [[ ! -f "$STAGE2_TRAIN" ]]; then
    echo ""
    echo "  蒸馏训练数据不存在, 正在生成..."
    python3 -m distill.run_pipeline 2>&1 | tail -20
fi

if [[ ! -f "$STAGE2_TRAIN" ]]; then
    echo "  错误: 蒸馏数据缺失, 请先上传 training_data_cleaned_train.jsonl"
    exit 1
fi
echo "  蒸馏训练集: $(wc -l < "$STAGE2_TRAIN") 条"
echo "  蒸馏验证集: $(wc -l < "$STAGE2_VAL") 条"

# ---- 训练 ----
echo ""
echo "[5/5] 开始两阶段训练..."
if $DRY_RUN; then
    echo "  (dry-run 模式, 跳过训练)"
    echo ""
    echo "环境检查通过! 可以执行:"
    echo "  python -m distill.train_two_stage \\"
    echo "    --stage1_data $STAGE1_TRAIN \\"
    echo "    --stage1_val $STAGE1_VAL \\"
    echo "    --stage2_data $STAGE2_TRAIN \\"
    echo "    --stage2_val $STAGE2_VAL \\"
    echo "    --base_model $BASE_MODEL"
    exit 0
fi

SKIP_FLAG=""
if $SKIP_STAGE1; then
    SKIP_FLAG="--skip_stage1"
fi

python3 -m distill.train_two_stage \
    --stage1_data "$STAGE1_TRAIN" \
    --stage1_val "$STAGE1_VAL" \
    --stage2_data "$STAGE2_TRAIN" \
    --stage2_val "$STAGE2_VAL" \
    --base_model "$BASE_MODEL" \
    $SKIP_FLAG

# ---- 完成 ----
echo ""
echo "============================================"
echo " 训练完成!"
echo "============================================"
echo ""
echo "最终适配器 (拷贝回本地):"
echo "  $WORK_DIR/distill_output/stage2_search_adapter/"
echo ""
echo "合并模型 (可直接推理):"
echo "  $WORK_DIR/distill_output/merged_model/"
echo ""
echo "拷贝回本地后替换:"
echo "  server/python/distill_lora_adapter/"
echo "============================================"
