# SeekFlow 搜索摘要蒸馏系统

将 DeepSeek-V4-Flash（教师模型）的知识蒸馏到 Qwen2.5-7B-Instruct（学生模型），用于生成基于真实平台数据的搜索摘要。

## 硬件要求

| 环节 | 最低显存 | 推荐配置 |
|------|---------|---------|
| 数据生成 | 无（仅 API 调用） | 任意 |
| QLoRA 训练 | 8 GB | RTX 4060 / RTX 3070 |
| 推理部署 | 6 GB | RTX 4060 / RTX 3060 |

## 环境安装

```bash
# 基础依赖
pip install pymysql httpx transformers peft datasets accelerate torch numpy

# bitsandbytes — Windows 预编译包 https://github.com/jithunnair-ml/bitsandbytes-windows-webui/releases
pip install bitsandbytes

# FlashAttention-2（可选，加速训练约 30%）
pip install flash-attn --no-build-isolation
```

确认数据库可连通后再进行数据生成步骤。

## 项目结构

```
distill/
├── config.py              # 全局配置（DB、API、训练超参）
├── context_builder.py     # 任务1：MySQL → JSON 上下文抽取
├── teacher_generator.py   # 任务2：DeepSeek API 训练数据生成
├── data_cleaner.py        # 任务3：数据清洗与预处理
├── train_distill.py       # 任务4：QLoRA 蒸馏训练
├── summary_service.py     # 任务5：推理部署服务
├── run_pipeline.py        # 一键流水线入口
├── test_distill.py        # 教师/蒸馏模型对比测试
└── README.md
```

## 快速开始

### 方式一：一键全流程

```bash
cd server/python
python -m distill.run_pipeline --full
```

依次执行：生成训练数据 → 数据清洗 → QLoRA 训练。

### 方式二：分步执行

```bash
# 步骤1+2：从数据库构建上下文 + DeepSeek API 生成训练样本（约 5000 条）
python -m distill.run_pipeline --step generate

# 步骤3：数据清洗（格式校验、去重、矛盾检测、分层抽样）
python -m distill.run_pipeline --step clean

# 步骤4：QLoRA 微调 Qwen2.5-7B-Instruct
python -m distill.run_pipeline --step train

# 步骤5：启动推理服务（stdin/stdout 常驻模式，供 Java 后端调用）
python -m distill.run_pipeline --step serve
```

## 配置说明

编辑 `config.py` 修改以下配置：

- **DB_CONFIG** — MySQL 连接信息
- **DEEPSEEK_CONFIG** — DeepSeek API 密钥与模型参数
- **TRAIN_CONFIG** — 训练超参（LoRA rank、epochs、batch size 等）
- **DATA_CONFIG** — 数据生成参数（目标样本数、摘要版本数）
- **SEED_KEYWORDS** — 搜索关键词种子池（60+ 个覆盖多种场景）

## 各步骤详解

### 步骤 1+2：训练数据生成

1. `ContextBuilder` 连接 MySQL，对每个关键词执行：
   - 模糊匹配视频（desc / keywords / category / music_title）
   - 模糊匹配用户（nickname / unique_id / signature）
   - 计算聚合统计（总赞数、热门标签、平均时长）
   - 收集平台统计（视频总量、用户总量、商品数、直播数）
2. 若真实视频不足 5 条，`TeacherGenerator._augment_videos()` 调用 DeepSeek API 生成虚拟视频补齐
3. 对每个关键词生成 3 种风格的摘要（亲切温暖 / 专业简洁 / 活泼有趣）
4. 写入 `training_data.jsonl`（JSONL 格式，每行一个样本）

```bash
# 自定义样本数
python -m distill.run_pipeline --step generate --samples 10000
```

### 步骤 3：数据清洗

`DataCleaner` 执行 6 道过滤：

| 过滤步骤 | 说明 |
|---------|------|
| 格式校验 | 必填字段完整性检查 |
| 长度检查 | 摘要中文字符 300-800 |
| 矛盾检测 | 视频数=0 但摘要声称有内容的剔除 |
| 黑名单过滤 | 广告/水印词检测（"加微信""http://"等） |
| 编辑距离去重 | 同关键词多风格摘要相似度 >85% 的去重 |
| 数值截断 | likes/plays 超出 2σ 的钳制 |

输出 `training_data_cleaned_train.jsonl` 和 `training_data_cleaned_val.jsonl`（90/10 分层抽样）。

### 步骤 4：QLoRA 蒸馏训练

- **量化方案**：4-bit NF4 + Double Quantization + bfloat16 计算
- **LoRA 配置**：rank=128, alpha=256, dropout=0.05, 目标所有线性投影层
- **训练配置**：10 epochs, effective batch=16 (4×4), lr=2e-4 cosine, warmup 5%
- **内存优化**：FlashAttention-2 + gradient checkpointing
- **输出**：LoRA 适配器（`distill_lora_adapter/`）+ 合并模型（`distill_output/merged_model/`）

```bash
# 自定义 epoch 数
python -m distill.train_distill \
  --train_data training_data_cleaned_train.jsonl \
  --val_data training_data_cleaned_val.jsonl \
  --epochs 15
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

**方式 B：stdin/stdout 常驻服务**

与 Java `SearchSuggestionService` 通过 ProcessBuilder 通信：

```
# 输入（stdin）
{"keyword": "美食教程", "videos": [...], ...}
# 或直接传关键词字符串
美食教程

# 输出（stdout）
SUMMARY:根据您的搜索"美食教程"，平台共匹配到23条相关内容...
```

Java 端通过 `ProcessBuilder` 启动：`python -m distill.summary_service`，收到 `READY` 信号后开始通信。

## 对比测试

```bash
python -m distill.test_distill
```

在 5 个不同搜索词上对比教师模型（DeepSeek API）与蒸馏模型（Qwen2.5-7B-QLoRA）的输出质量和延迟。

## 常见问题

**Q: bitsandbytes 在 Windows 上安装失败？**
下载预编译 wheel：https://github.com/jithunnair-ml/bitsandbytes-windows-webui/releases

**Q: 训练时 OOM？**
减小 `per_device_batch_size` 或增大 `gradient_accumulation_steps`，effective batch 保持不变即可。

**Q: DeepSeek API 限流（429）？**
代码内置指数退避重试（最多 5 次），等待时间自动递增。也可调低 `max_concurrency`。

**Q: 如何更新训练数据？**
平台新增视频/用户后，重新运行 `--step generate && --step clean && --step train` 即可迭代蒸馏模型。

## 与现有系统集成

蒸馏模型部署后将替代当前的 `search_summary.py`（3B 模型），Java 端无需改动 —— `summary_service.py` 的 `serve()` 模式实现了完全兼容的 `READY / SUMMARY: / ERROR:` 协议。
