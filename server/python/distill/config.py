"""
全局配置 — 所有模块共享
"""

import os

# ===================== 数据库 =====================
DB_CONFIG = {
    "host": "8.134.23.170",
    "port": 3306,
    "user": "dev",
    "password": "XrKk4Kxe@H2_rtBeqwd12edqyg3qnj.,12,12",
    "database": "douyin",
    "charset": "utf8mb4",
}

# ===================== DeepSeek API (教师模型) =====================
DEEPSEEK_CONFIG = {
    "api_key": "sk-0da9464a8aea4f639b4ae1a5b11b050c",
    "base_url": "https://api.deepseek.com",
    "model": "deepseek-v4-flash",
    "max_tokens": 2048,
    "temperature_augment": 0.9,   # 视频扩增: 高温度增加多样性
    "temperature_summary": 0.8,   # 摘要生成: 中等温度
}

# ===================== 蒸馏训练 =====================
TRAIN_CONFIG = {
    "base_model": "Qwen/Qwen3-8B-Instruct",  # 升级到 8B (RTX 4060 4-bit 可跑)
    "output_dir": os.path.join(os.path.dirname(__file__), "..", "distill_output"),
    "adapter_dir": os.path.join(os.path.dirname(__file__), "..", "distill_lora_adapter"),

    # QLoRA
    "lora_r": 64,
    "lora_alpha": 128,
    "lora_dropout": 0.05,
    "lora_target_modules": ["q_proj", "k_proj", "v_proj", "o_proj",
                            "gate_proj", "up_proj", "down_proj"],

    # 训练超参
    "num_epochs": 5,
    "per_device_batch_size": 2,
    "gradient_accumulation_steps": 8,  # effective batch = 16
    "learning_rate": 2e-4,
    "warmup_ratio": 0.05,
    "max_input_length": 512,
    "max_output_length": 512,

    # 其他
    "use_flash_attention_2": True,
    "gradient_checkpointing": True,
    "save_strategy": "steps",
    "eval_steps": 100,
    "logging_steps": 10,
}

# ===================== 两阶段训练 =====================
TWO_STAGE_CONFIG = {
    # 阶段1: 公开数据 → 中文摘要语感
    "stage1": {
        "num_epochs": 3,
        "learning_rate": 2e-4,
        "lora_r": 32,
        "lora_alpha": 64,
        "max_input_length": 768,
        "adapter_subdir": "stage1_public_adapter",
        "datasets": ["LCSTS", "CSL", "XLSum", "NLPCC"],
        "target_samples": 1000000,
    },
    # 阶段2: DeepSeek蒸馏 → 搜索分析
    "stage2": {
        "num_epochs": 5,
        "learning_rate": 1e-4,
        "lora_r": 64,
        "lora_alpha": 128,
        "max_input_length": 1024,
        "adapter_subdir": "stage2_search_adapter",
        "target_samples": 5000,  # 蒸馏数据量, 比原先可少很多
    },
}

# ===================== 数据生成 =====================
DATA_CONFIG = {
    "total_samples": 5000,
    "real_video_limit": 8,          # 每次查询最多取真实视频数
    "min_real_for_augment": 5,      # 少于5条则触发扩增
    "virtual_video_count": 10,      # 每次扩增生成的虚拟视频数
    "summary_versions": 3,          # 每个关键词生成几个风格版本
    "training_data_path": os.path.join(os.path.dirname(__file__), "..", "training_data.jsonl"),
    "cleaned_data_path": os.path.join(os.path.dirname(__file__), "..", "training_data_cleaned.jsonl"),
}

# ===================== 搜索关键词种子池 =====================
SEED_KEYWORDS = [
    # 常见搜索意图
    "美食教程", "搞笑段子", "舞蹈教学", "旅行攻略", "音乐推荐",
    "美妆教程", "健身减肥", "数码评测", "萌宠日常", "影视解说",
    "日常vlog", "游戏实况", "汽车评测", "母婴育儿", "家居装修",
    "才艺表演", "动漫剪辑", "乡村生活", "科技新闻", "时尚穿搭",
    # 热门话题
    "编程入门", "AI人工智能", "手机摄影", "户外探险", "美食探店",
    "汉服文化", "电竞比赛", "摩托车旅行", "理财知识", "考研学习",
    # 长尾搜索
    "红烧肉做法", "猫咪搞笑视频", "华为手机评测", "三亚旅行攻略",
    "瑜伽初学者", "古风歌曲", "篮球教学", "面试技巧", "英语学习",
    "股票入门", "单反摄影", "蛋糕烘焙", "流浪猫救助", "星际争霸",
    # 商品/直播相关
    "手机壳推荐", "直播带货", "便宜好物", "品牌折扣",
    "最新手机", "护肤品推荐", "直播间福利", "限时秒杀",
    # 空结果/长尾
    "量子计算机原理", "深海探测", "古董鉴定方法",
    "冷门电影推荐", "小众音乐风格", "手工皮具制作",
]
