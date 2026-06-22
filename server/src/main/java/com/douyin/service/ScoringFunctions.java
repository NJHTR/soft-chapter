package com.douyin.service;

/**
 * 科学评分函数库 — 替代硬编码 clamp/线性插值。
 *
 * 每个函数都有明确的数学含义, 参数可调且物理意义清晰。
 */
public final class ScoringFunctions {

    private ScoringFunctions() {}

    // ==================== 核心函数 ====================

    /**
     * Logistic sigmoid: f(x) = 1 / (1 + e^{-k(x - x0)})
     *
     * 机器学习中最常用的激活/归一化函数。
     * x0 = 中心点 (sigmoid(x0) = 0.5)
     * k  = 陡峭度 (k 越大越接近阶跃函数)
     *
     * 物理意义: 二分类概率 / 费米-狄拉克分布
     */
    public static double sigmoid(double x, double center, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * (x - center)));
    }

    /**
     * 指数衰减: f(t) = e^{-λt}
     *
     * λ = ln(2) / halfLife
     * 由微分方程 dN/dt = -λN 导出 (放射性衰变定律)
     *
     * @param t        经过的时间
     * @param halfLife 半衰期 (与 t 同单位)
     * @return 0~1, t=halfLife 时 = 0.5
     */
    public static double expDecay(double t, double halfLife) {
        if (t <= 0) return 1.0;
        double lambda = Math.log(2) / halfLife;
        return Math.exp(-lambda * t);
    }

    /**
     * 对数归一化: f(x) = ln(x/refMin) / ln(refMax/refMin)
     *
     * 适用于跨越数量级的指标 (如粉丝数: 10 vs 10000000)
     * 基于韦伯-费希纳定律: 人类感知与刺激的对数成正比
     *
     * @return 0~1, 超过 refMax 时 = 1
     */
    public static double logNormalize(double x, double refMin, double refMax) {
        if (x <= refMin) return 0.0;
        if (x >= refMax) return 1.0;
        return Math.log(x / refMin) / Math.log(refMax / refMin);
    }

    /**
     * 线性归一化: f(x) = (x - min) / (max - min) → clamp [0,1]
     */
    public static double linearNormalize(double x, double min, double max) {
        if (max <= min) return 0.5;
        return clamp((x - min) / (max - min), 0, 1);
    }

    // ==================== 复合函数 ====================

    /**
     * 时间感知的指数衰减 sigmoid:
     * 旧内容即使匹配好也会随时间衰减, 但有近期互动则"翻红"
     *
     * @param timeScore   纯时间衰减 [0,1]
     * @param pulseScore  近期互动活跃度 [0,1]
     * @param isActive    是否有近期互动
     * @return 综合时效分
     */
    public static double timeAwareFreshness(double timeScore, double pulseScore, boolean isActive) {
        if (isActive) {
            // 翻红: 互动活跃度主导, 时间衰减影响降低
            return Math.max(0.5, timeScore * 0.3 + pulseScore * 0.7);
        }
        return timeScore;
    }

    /**
     * 威尔逊区间下界 (Wilson score interval lower bound)
     *
     * 用于小样本的二项比例估计。比简单的 p = likes/views 更科学,
     * 因为 1/1=100% 显然不如 100/1000=10% 可信。
     *
     * @param positives 正样本数 (如 likes)
     * @param total     总样本数 (如 views)
     * @param z         z-score (95% 置信度 = 1.96)
     * @return 下界估计 [0,1]
     */
    public static double wilsonLowerBound(long positives, long total, double z) {
        if (total == 0) return 0;
        double p = (double) positives / total;
        double n = total;
        double z2 = z * z;
        double numerator = p + z2 / (2 * n)
                - z * Math.sqrt((p * (1 - p) + z2 / (4 * n)) / n);
        double denominator = 1 + z2 / n;
        return numerator / denominator;
    }

    /** Wilson 95% 置信度快捷方式 */
    public static double wilson95(long positives, long total) {
        return wilsonLowerBound(positives, total, 1.96);
    }

    // ==================== 距离/相似度 ====================

    /**
     * 余弦相似度
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 工具 ====================

    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /** 将 [0,1] 值映射到 [-1,1] 区间, 保持 0.5 → 0 */
    public static double recenter(double normalized) {
        return (normalized - 0.5) * 2.0;
    }
}
