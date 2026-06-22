# SeekFlow 搜索摘要蒸馏系统

将 DeepSeek（教师模型）的知识蒸馏到 Qwen3-8B（学生模型），用于生成基于真实平台数据的搜索摘要。

## 核心思路

**两阶段训练：公开数据打底 + 蒸馏数据专精**

```
阶段 1 (便宜): LCSTS+CSL+XLSum 公开摘要数据 → 训中文摘要语感
    ↓ 100 万条, ~30h A100, ~240 元
阶段 2 (贵):   DeepSeek 蒸馏数据 → 训搜索分析能力
    ↓ 5000 条, ~4h A100, ~50 元
最终适配器 → 4-bit 量化部署到 RTX 4060 本地推理
```

相比纯蒸馏方案：公开数据几乎免费，给模型打好中文摘要基础后，再用少量高质量蒸馏数据就能学会搜索分析。

## 硬件要求

| 环节 | 最低显存 | 推荐配置 |
|------|---------|---------|
| 数据生成 | 无（仅 API 调用） | 任意 |
| QLoRA 训练 (阶段1+2) | 16 GB | A100 40G / RTX 4090 24G |
| 推理部署 | 6 GB | RTX 4060 8GB |

训练推荐租用 AutoDL：A100 40G 约 8 元/小时。

## 项目结构

```
distill/
├── config.py                 # 全局配置（DB、API、训练超参、两阶段参数）
├── context_builder.py        # 任务1：MySQL → JSON 上下文抽取
├── teacher_generator.py      # 任务2：DeepSeek API 训练数据生成
├── data_cleaner.py           # 任务3：数据清洗与预处理
├── train_distill.py          # 任务4：单阶段 QLoRA 蒸馏训练（兼容旧流程）
├── prepare_public_data.py    # 新增：下载并格式化公开摘要数据集
├── train_two_stage.py        # 新增：两阶段训练编配器
├── summary_service.py        # 任务5：推理部署服务
├── run_pipeline.py           # 一键流水线入口
├── test_distill.py           # 教师/蒸馏模型对比测试
├── autodl_run.sh             # 新增：AutoDL 一键训练脚本
└── README.md
```

## 环境安装

```bash
# 本地开发
pip install pymysql httpx transformers peft datasets accelerate torch numpy

# bitsandbytes — Windows 预编译包
# https://github.com/jithunnair-ml/bitsandbytes-windows-webui/releases
pip install bitsandbytes

# FlashAttention-2（可选，加速训练约 30%）
pip install flash-attn --no-build-isolation
```

## 快速开始

### 方式一：AutoDL 一键训练（推荐）

```bash
# 1. 本地打包
cd server/python
bash pack_for_autodl.sh
# → 生成 autodl_upload.zip

# 2. 上传 autodl_upload.zip 到 AutoDL

# 3. AutoDL 终端
unzip autodl_upload.zip -d /root/autodl-tmp/
cd /root/autodl-tmp/autodl_pack
bash distill/autodl_run.sh

# 可选参数
bash distill/autodl_run.sh --max-samples 500000   # 省钱模式 50 万条
bash distill/autodl_run.sh --skip-stage1           # 只做蒸馏，跳过公开数据
bash distill/autodl_run.sh --dry-run               # 只检查环境

# 4. 训练完成后，下载适配器
# distill_output/stage2_search_adapter/ → 拷回本地替换 distill_lora_adapter/
```

### 方式二：本地/服务器分步执行

```bash
cd server/python

# 步骤 0：准备公开数据（需联网下载 HuggingFace 数据集，约 2-3GB）
python -m distill.prepare_public_data --max_samples 1000000
# 输出: public_data/public_summary_train.jsonl, public_data/public_summary_val.jsonl

# 步骤 1：生成蒸馏数据（DeepSeek API，约 5000 条）
python -m distill.run_pipeline --step generate
python -m distill.run_pipeline --step clean

# 步骤 2：两阶段训练
python -m distill.train_two_stage \
  --stage1_data public_data/public_summary_train.jsonl \
  --stage1_val public_data/public_summary_val.jsonl \
  --stage2_data training_data_cleaned_train.jsonl \
  --stage2_val training_data_cleaned_val.jsonl

# 步骤 3：启动推理服务
python -m distill.summary_service --serve
```

### 方式三：旧版单阶段训练（仅蒸馏数据，不推荐）

```bash
python -m distill.run_pipeline --full
```

## 配置说明

编辑 `config.py` 修改以下配置：

- **DB_CONFIG** — MySQL 连接信息
- **DEEPSEEK_CONFIG** — DeepSeek API 密钥与模型参数
- **TRAIN_CONFIG** — 训练超参（基础模型、LoRA rank、epochs 等）
- **TWO_STAGE_CONFIG** — 两阶段训练参数（阶段1/2的 lr、epochs、样本量）
- **DATA_CONFIG** — 数据生成参数（目标样本数、摘要版本数）
- **SEED_KEYWORDS** — 搜索关键词种子池（60+ 个覆盖多种场景）

## 各步骤详解

### 步骤 0：准备公开数据

`prepare_public_data.py` 自动下载 4 个公开摘要数据集并统一格式化：

| 数据集 | 规模 | 内容 |
|--------|------|------|
| LCSTS | ~240 万条 | 微博短文本 → 摘要标题 |
| CSL | ~40 万条 | 论文标题+关键词 → 摘要 |
| XLSum-Chinese | ~3 万条 | BBC 新闻 → 摘要 |
| NLPCC 2017 | ~5 万条 | 新闻 → 摘要 |

全量加载后去重打乱，按 `--max_samples` 截断（默认 100 万），随机选用 7 种 prompt 模板增加多样性。

```bash
# 全量 100 万条
python -m distill.prepare_public_data

# 省钱 50 万条
python -m distill.prepare_public_data --max_samples 500000

# 全部数据（~300 万条）
python -m distill.prepare_public_data --max_samples 0
```

### 步骤 1+2：蒸馏数据生成

1. `ContextBuilder` 连接 MySQL，对每个关键词执行搜索
2. 若真实视频不足 5 条，`TeacherGenerator` 调用 DeepSeek API 生成虚拟视频补齐
3. 对每个关键词生成 3 种风格的摘要
4. 写入 `training_data.jsonl`

### 步骤 3：数据清洗

`DataCleaner` 执行 6 道过滤：

| 过滤步骤 | 说明 |
|---------|------|
| 格式校验 | 必填字段完整性检查 |
| 长度检查 | 摘要中文字符 300-800 |
| 矛盾检测 | 视频数=0 但摘要声称有内容的剔除 |
| 黑名单过滤 | 广告/水印词检测 |
| 编辑距离去重 | 同关键词多风格摘要相似度 >85% 的去重 |
| 数值截断 | likes/plays 超出 2σ 的钳制 |

输出 `training_data_cleaned_train.jsonl` 和 `training_data_cleaned_val.jsonl`。

### 步骤 4：两阶段训练

**阶段 1 — 公开数据预训练**

- 数据：LCSTS + CSL + XLSum + NLPCC（100 万条）
- 配置：LoRA rank=32, lr=2e-4, 3 epochs
- 目标：学会流畅中文摘要表达
- 输出：`stage1_public_adapter/`

**阶段 2 — 搜索摘要蒸馏**

- 数据：DeepSeek 蒸馏数据（5000 条）
- 配置：在阶段1适配器上继续训练，rank=64, lr=1e-4, 5 epochs
- 目标：学会搜索数据分析 + 三段式结构化输出
- 输出：`stage2_search_adapter/`（最终适配器）

```bash
# 完整两阶段
python -m distill.train_two_stage \
  --stage1_data public_data/public_summary_train.jsonl \
  --stage1_val public_data/public_summary_val.jsonl \
  --stage2_data training_data_cleaned_train.jsonl \
  --stage2_val training_data_cleaned_val.jsonl

# 跳过阶段1（已有适配器）
python -m distill.train_two_stage \
  --stage2_data training_data_cleaned_train.jsonl \
  --stage2_val training_data_cleaned_val.jsonl \
  --stage1_adapter /path/to/stage1_adapter

# 只做阶段1
python -m distill.train_two_stage \
  --stage1_data ... --stage1_val ... --stage2_data ... --stage2_val ... \
  --stage1_only
```

### 步骤 5：推理部署

`SummaryService` 提供两种使用方式：

**方式 A：Python 直接调用**

```python
from distill.summary_service import SummaryService

service = SummaryService()
service.load()
summary = service.generate("美食教程", context_dict)
```

**方式 B：stdin/stdout 常驻服务（Java 后端当前方式）**

```
# 输入（stdin）
{"keyword": "美食教程", "videos": [...], ...}

# 输出（stdout）
SUMMARY:根据您的搜索"美食教程"，平台共匹配到23条相关内容...
__END__
```

Java 端通过 `ProcessBuilder` 启动 `python -m distill.summary_service`，收到 `READY` 后开始通信。

## 对比测试

```bash
python -m distill.test_distill
```

在 5 个不同搜索词上对比教师模型（DeepSeek API）与蒸馏模型（Qwen3-8B-QLoRA）的输出质量和延迟。

## 常见问题

**Q: 训练时 OOM？**
减小 `per_device_batch_size` 或增大 `gradient_accumulation_steps`，effective batch 保持不变即可。

**Q: DeepSeek API 限流（429）？**
代码内置指数退避重试（最多 5 次），等待时间自动递增。

**Q: 如何更新蒸馏数据？**
平台新增视频/用户后，重新运行 `--step generate && --step clean` 生成新的训练数据，再跑阶段2训练即可。

**Q: AutoDL 训练中断了怎么办？**
训练脚本使用 `load_best_model_at_end=True`，重跑会自动从最新 checkpoint 恢复。

**Q: 公开数据下载太慢？**
AutoDL 上设置 HuggingFace 镜像：
```bash
export HF_ENDPOINT=https://hf-mirror.com
```

## 与现有系统集成

训练完成后，用新的 `stage2_search_adapter/` 替换 `server/python/distill_lora_adapter/`，`summary_service.py` 会自动加载新适配器。Java 端无需任何改动。
