package com.douyin.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推荐系统超参数字典 — 全局唯一参数源, 便于调参与 A/B 实验。
 *
 * 命名规范:
 *   W_xxx        = 排序权重 (总和 1.0)
 *   HL_xxx       = 半衰期 (小时)
 *   SG_xxx       = sigmoid 中心点/陡峭度
 *   TH_xxx       = 阈值
 *   ALPHA_xxx    = EMA 平滑系数
 *   DEF_xxx      = 缺省值
 *   PEN_xxx      = 惩罚系数
 *   BONUS_xxx    = 加分值
 *   QW_xxx       = 质量分内部权重
 *   SPW_xxx      = 社交证明内部权重
 *   DAYS_xxx     = 时间窗口 (天)
 *   LIMIT_xxx    = 限制数量
 *   UT_xxx       = 用户类型分值
 *   CI_xxx       = 创作者互动分值
 *   TAG_xxx      = 标签系统参数
 */
public final class RecommendationConfig {

    private RecommendationConfig() {}

    // ==================== 排序权重 (总和 = 1.0) ====================
    public static final double W_CONTENT              = 0.15;
    public static final double W_QUALITY              = 0.07;
    public static final double W_CREATOR_AFFINITY     = 0.07;
    public static final double W_BEHAVIORAL           = 0.05;
    public static final double W_SOCIAL               = 0.05;
    public static final double W_PERSONAL_HISTORY     = 0.04;
    public static final double W_POPULARITY           = 0.04;
    public static final double W_FRESHNESS            = 0.12;
    public static final double W_USER_TYPE            = 0.04;
    public static final double W_EXPLORATION          = 0.03;
    public static final double W_WATCH_PENALTY        = 0.08;
    public static final double W_SOCIAL_PROOF         = 0.08;
    public static final double W_CREATOR_INTERACTION  = 0.10;
    public static final double W_TAG_MATCH            = 0.08;

    public static Map<String, Double> weightMap() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("content",          W_CONTENT);
        m.put("quality",          W_QUALITY);
        m.put("creator_affinity", W_CREATOR_AFFINITY);
        m.put("behavioral",       W_BEHAVIORAL);
        m.put("social",           W_SOCIAL);
        m.put("personal_history", W_PERSONAL_HISTORY);
        m.put("popularity",       W_POPULARITY);
        m.put("freshness",        W_FRESHNESS);
        m.put("user_type",        W_USER_TYPE);
        m.put("exploration",      W_EXPLORATION);
        m.put("watch_penalty",    W_WATCH_PENALTY);
        m.put("social_proof",     W_SOCIAL_PROOF);
        m.put("creator_interact", W_CREATOR_INTERACTION);
        m.put("tag_match",        W_TAG_MATCH);
        return m;
    }

    // ==================== 默认分值 ====================
    /** 无信号时的保底分 (低) */
    public static final double DEF_SCORE_LOW   = 0.1;
    /** 无信号时的保底分 (中) */
    public static final double DEF_SCORE_MED   = 0.3;
    /** 无信号时的保底分 (中高) */
    public static final double DEF_SCORE_HALF  = 0.5;
    /** 默认质量分 */
    public static final double DEF_QUALITY     = 0.5;
    /** 默认完播率 */
    public static final double DEF_COMPLETION  = 0.5;
    /** 默认标签权重 */
    public static final double DEF_TAG_WEIGHT  = 0.3;
    /** 默认标签置信度 */
    public static final double DEF_TAG_CONF    = 0.5;

    // ==================== 时间衰减半衰期 (小时) ====================
    public static final double HL_FRESHNESS      = 48;
    public static final double HL_SOCIAL_PROOF   = 720;  // 30天
    public static final double HL_INTERACTION    = 720;  // 30天

    // ==================== Sigmoid 参数 ====================
    /** 互动率 → 质量分: 中心 0.35, 陡度 6.0 */
    public static final double SG_QUALITY_CENTER    = 0.35;
    public static final double SG_QUALITY_STEEPNESS = 6.0;
    /** 热度趋势: interactions/hour 中心 3.0, 陡度 0.5 */
    public static final double SG_TREND_CENTER      = 3.0;
    public static final double SG_TREND_STEEPNESS   = 0.5;
    /** 内容相似度: cosine 中心 0.5, 陡度 8.0 */
    public static final double SG_CONTENT_CENTER    = 0.5;
    public static final double SG_CONTENT_STEEPNESS = 8.0;
    /** 社交证明互动率: 中心 0.5, 陡度 4.0 */
    public static final double SG_SOCIAL_INTERACT_CENTER = 0.5;
    public static final double SG_SOCIAL_INTERACT_STEEP   = 4.0;

    // ==================== EMA 平滑系数 ====================
    public static final double ALPHA_SHORT_TERM     = 0.15;
    public static final double ALPHA_LONG_TERM      = 0.05;
    public static final double ALPHA_CATEGORY       = 0.92;
    public static final double ALPHA_PROFILE        = 0.10;
    public static final double ALPHA_COMPLETION     = 0.05;
    public static final double ALPHA_BOUNCE         = 0.05;
    public static final double ALPHA_MUSIC_BPM      = 0.05;
    public static final double ALPHA_MUSIC_ENERGY   = 0.10;

    // ==================== 召回参数 ====================
    public static final int RECALL_PER_CHANNEL   = 50;
    public static final int CANDIDATE_POOL_SIZE  = 200;
    public static final double EXPLORE_INJECT_RATE = 0.12;
    public static final int RECALL_OVERSAMPLE_CONTENT = 2;
    public static final int RECALL_OVERSAMPLE_CF      = 3;
    public static final int RECALL_OVERSAMPLE_EXPLORE = 3;
    public static final int CF_RECENT_LIKED_LIMIT    = 50;
    public static final int SOCIAL_FOLLOW_LIMIT      = 100;
    public static final int SOCIAL_RECENT_AUTHOR_LIMIT = 20;
    public static final int BACKLOG_FOLLOW_LIMIT     = 100;
    public static final int EXPLORE_RANDOM_FACTOR     = 3;  // 分母, 实际随机度 = 0.3

    // ==================== 多样性限制 ====================
    public static final int MAX_PER_AUTHOR      = 2;
    public static final int MAX_PER_CATEGORY    = 3;
    public static final int MAX_PER_TYPE        = 3;
    public static final int DIVERSITY_MAX_SKIP  = 10;
    public static final int DIVERSITY_TIME_SKIP = 8;
    public static final int DIVERSITY_EXPLORE_MIN    = 3;
    public static final long DIVERSITY_RECENT_HOURS  = 48;

    // ==================== 质量分内部权重 ====================
    public static final double QW_ENGAGE_RATE    = 0.30;
    public static final double QW_CONTENT_QUAL   = 0.25;
    public static final double QW_COMPLETION     = 0.25;
    public static final double QW_FOLLOWER       = 0.20;
    /** 互动总和中 collect/share 的加倍系数 */
    public static final double QW_COLLECT_MULT   = 2.0;
    public static final double QW_SHARE_MULT     = 2.0;
    /** 互动率缩放到 sigmoid 输入前的倍数 */
    public static final double QW_ENGAGE_SCALE   = 5.0;

    // ==================== 社交证明内部权重 ====================
    public static final double SPW_FOLLOWER       = 0.10;
    public static final double SPW_LIKE           = 0.10;
    public static final double SPW_COLLECT        = 0.30;
    public static final double SPW_SHARE          = 0.25;
    public static final double SPW_INTERACT_RATE  = 0.25;
    public static final double SPW_COLLECT_MULT   = 2.0;
    public static final double SPW_SHARE_MULT     = 3.0;
    public static final double SPW_INTERACT_SCALE = 10.0;
    /** 对数归一化参考值 */
    public static final double LOG_REF_FOLLOWER   = 1_000_000;
    public static final double LOG_REF_LIKES      = 100_000;
    public static final double LOG_REF_COLLECT    = 10_000;
    public static final double LOG_REF_SHARE      = 10_000;

    // ==================== 互动惩罚 ====================
    public static final double PEN_LIKED_BASE       = 0.95;
    public static final double PEN_COLLECTED_BASE   = 0.80;
    public static final double PEN_OLDER_WATCHED    = 0.15;
    public static final double PEN_INTERACTION_FLOOR = 0.10;

    // ==================== 观看历史惩罚 ====================
    public static final double PEN_FINISHED_BASE    = 0.8;
    public static final double PEN_FINISHED_REPEAT_FACTOR = 0.5;
    public static final double PEN_FINISHED_FLOOR   = 0.3;
    public static final double PEN_FINISHED_CEIL    = 1.0;
    public static final double PEN_FAST_SWIPE       = 0.7;
    public static final double PEN_HALF_WATCH       = 0.4;
    public static final double PEN_HABITUAL         = 0.2;
    public static final double PEN_SMALL_PORTION    = 0.5;

    // ==================== 内容匹配 ====================
    /** 长期/短期向量融合权重 */
    public static final double CM_LONG_W  = 0.6;
    public static final double CM_SHORT_W = 0.4;

    // ==================== 行为匹配 (Behavioral) ====================
    public static final double BEH_FULL_QUERY_MATCH  = 0.3;
    public static final double BEH_PARTIAL_WORD_MATCH = 0.08;
    public static final double BEH_TREND_CAP         = 0.2;
    public static final double BEH_TREND_PER_HIT     = 0.06;
    public static final double BEH_DEFAULT_CAT_W     = 0.1;
    public static final double BEH_CAT_W_MULT        = 0.2;
    public static final double BEH_MULTI_CAT_BONUS   = 0.08;
    public static final double BEH_MOOD_BONUS        = 0.04;
    public static final double BEH_DURATION_BONUS    = 0.1;
    public static final double BEH_DURATION_MIN      = 10;
    public static final double BEH_DURATION_MAX      = 90;

    // ==================== 个人历史匹配 ====================
    public static final double PH_REPEAT_COMPLETION_BONUS = 0.2;
    public static final double PH_DURATION_EXACT_BONUS    = 0.15;
    public static final double PH_DURATION_MARGIN_BONUS   = 0.05;
    public static final double PH_DURATION_MARGIN_FACTOR  = 0.5;
    public static final double PH_BPM_EXACT_BONUS         = 0.12;
    public static final double PH_BPM_MARGIN_BONUS        = 0.05;
    public static final double PH_BPM_MARGIN_FACTOR       = 0.3;
    public static final double PH_ENERGY_TIGHT_BONUS      = 0.10;
    public static final double PH_ENERGY_LOOSE_BONUS      = 0.05;
    public static final double PH_ENERGY_TIGHT_THRESHOLD  = 0.15;
    public static final double PH_ENERGY_LOOSE_THRESHOLD  = 0.30;
    public static final double PH_GENRE_BONUS       = 0.03;
    public static final double PH_MOOD_BONUS         = 0.03;
    public static final double PH_SPEECH_BONUS       = 0.02;
    public static final double PH_CLOSEUP_BONUS      = 0.02;
    public static final double PH_AUDIO_MUSIC_BONUS  = 0.02;

    // ==================== 社交分 ====================
    public static final double SOC_FOLLOWED    = 1.0;
    public static final double SOC_INDIRECT    = 0.3;
    public static final double SOC_NONE        = 0.1;

    // ==================== 热度/新鲜度 ====================
    public static final double PULSE_LIKE_NORM   = 50.0;
    public static final double PULSE_WATCH_NORM  = 200.0;
    public static final double PULSE_LIKE_W      = 0.6;
    public static final double PULSE_WATCH_W     = 0.4;
    public static final double PULSE_INACTIVE    = 0.02;

    // ==================== 用户类型分值 ====================
    public static final double UT_PASSIVE_CONSUMER_QUALITY_W = 0.8;
    public static final double UT_PASSIVE_CONSUMER_BASE      = 0.2;
    public static final double UT_SOCIAL_BUTTERFLY  = 0.5;
    public static final double UT_POWER_LIKER_SCALE = 5.0;
    public static final double UT_COLLECTOR_MATCH   = 0.8;
    public static final double UT_COLLECTOR_DEFAULT = 0.4;
    public static final double UT_ACTIVE_SEARCHER   = 0.5;
    public static final double UT_CREATOR_FAN_MATCH = 0.9;
    public static final double UT_CREATOR_FAN_DEFAULT = 0.3;
    public static final double UT_EXPLORER          = 0.6;
    public static final double UT_NEW_USER          = 0.7;
    public static final double UT_BALANCED          = 0.5;

    // ==================== 探索激励 ====================
    public static final double EXPLORE_CAT_MULT    = 0.4;
    public static final double EXPLORE_NEW_AUTHOR  = 0.15;
    public static final double EXPLORE_HIGH_QUAL   = 0.1;
    public static final double EXPLORE_QUAL_THRESHOLD = 0.6;

    // ==================== 创作者互动信号 ====================
    public static final double CI_DOUBLE_RELATION  = 0.9;
    public static final double CI_INTERACTED_ONLY  = 0.8;
    public static final double CI_FOLLOWED_ONLY    = 0.7;
    public static final long   CI_RECENCY_HOURS    = 168;  // 7天
    public static final double CI_RECENCY_BOOST    = 0.3;

    // ==================== 创作者亲和力 ====================
    public static final double CA_DEFAULT        = 0.1;
    public static final double CA_CLAMP_MIN      = -0.5;
    public static final double CA_CLAMP_MAX      = 1.0;

    // ==================== 品类反馈 ====================
    public static final double CAT_SKIP_CEIL     = 0.20;
    public static final double CAT_SKIP_PER      = 0.02;
    public static final double CAT_LIKE_CEIL     = 0.12;
    public static final double CAT_LIKE_PER      = 0.02;

    // ==================== 标签匹配 (TagMatch) ====================
    public static final double TM_SRC_DIVERSITY_PER_SRC = 0.25;
    public static final double TM_TAG_QUAL_BASE   = 0.7;
    public static final double TM_TAG_QUAL_SRC_W  = 0.3;
    public static final double TM_SEARCH_HIT_MULT = 0.8;
    public static final double TM_TREND_HIT_MULT  = 0.4;
    public static final double TM_CATEGORY_CROSS_MULT = 0.3;
    public static final int    TM_TOP_TAGS_LIMIT  = 15;

    // ==================== 近期互动 (RecentEngagement) ====================
    public static final int RE_ACTIVE_LIKE_MIN  = 1;
    public static final int RE_ACTIVE_WATCH_MIN = 3;

    // ==================== 时间窗口 (天) ====================
    public static final int DAYS_RECENT_WATCHED    = 1;
    public static final int DAYS_OLDER_WATCHED     = 7;
    public static final int DAYS_RECENT_ENGAGEMENT = 7;
    public static final int DAYS_CATEGORY_FEEDBACK = 7;
    public static final int DAYS_TRENDING_KEYWORD  = 3;
    public static final int DAYS_BACKLOG_MIN       = 1;
    public static final int DAYS_BACKLOG_MAX       = 90;
    public static final int DAYS_TAG_STALE         = 7;
    public static final int DAYS_BEHAVIOR_VALID    = 7;
    public static final int DAYS_CALIBRATION_SINCE = 3;

    // ==================== 扫描限制 ====================
    public static final int LIMIT_TRENDING_PROFILES = 200;
    public static final int LIMIT_TRENDING_KEYWORDS = 10;
    public static final int LIMIT_TRENDING_MIN_FREQ = 2;
    public static final int LIMIT_TAG_CALIBRATION   = 50;
    public static final int LIMIT_MULTI_LABEL       = 5;
    public static final int LIMIT_TREND_HOT_VIDEOS  = 100;
    public static final int LIMIT_TREND_PROFILES    = 500;
    public static final int LIMIT_MAX_PHRASES       = 8;
    public static final int LIMIT_CREATOR_AFFINITY  = 50;
    public static final int LIMIT_RECENT_SEARCHES   = 10;

    // ==================== 缓存 ====================
    public static final long TRENDING_CACHE_MS      = 300_000;  // 5分钟
    public static final int  SESSION_MAP_MAX        = 20000;

    // ==================== 标签系统 ====================
    public static final int TAG_COMMENT_THRESHOLD    = 3;
    public static final int TAG_COMMENT_FLUSH_MULT   = 2;
    public static final int TAG_SEARCH_THRESHOLD     = 3;
    public static final int TAG_COWATCH_THRESHOLD    = 5;
    public static final double TAG_MIN_WEIGHT        = 0.05;
    public static final double TAG_DECAY_FACTOR      = 0.85;
    public static final double TAG_DEFAULT_STALE_W   = 0.3;
    public static final double TAG_STALE_CONSIDER_W  = 0.05;
    public static final double TAG_INHERIT_W_DISCOUNT = 0.6;
    public static final double TAG_INHERIT_C_DISCOUNT = 0.7;
    public static final double TAG_INHERIT_MIN_CONF   = 0.4;
    public static final double TAG_INHERIT_MIN_ADJUSTED = 0.3;
    public static final int TAG_INHERIT_TOP_K         = 5;
    public static final int TAG_INHERIT_POOL_SIZE     = 200;
    public static final int TAG_MAX_LEN              = 32;

    // ==================== 标签信号权重 ====================
    /** co_search 最低分阈值 */
    public static final double COSEARCH_MIN_SCORE    = 0.3;
    public static final double COSEARCH_W_MIN        = 0.1;
    public static final double COSEARCH_W_MAX        = 0.6;
    /** 共观最低完播率 */
    public static final double COWATCH_MIN_COMPLETION = 0.5;
    /** 标签扩散最低置信度 */
    public static final double TAG_DIFFUSE_MIN_CONF  = 0.45;
    public static final double TAG_DIFFUSE_W_DISCOUNT = 0.55;
    public static final double TAG_DIFFUSE_W_MIN     = 0.15;
    public static final double TAG_DIFFUSE_W_MAX     = 0.5;
    public static final double TAG_DIFFUSE_C_DISCOUNT = 0.6;
    /** AI 初始标签缺省权重/置信度 */
    public static final double KI_SCENE_W = 0.45;
    public static final double KI_SCENE_C = 0.5;
    public static final double KI_KEYWORD_W = 0.4;
    public static final double KI_KEYWORD_C = 0.4;
    public static final double KI_ATTR_W = 0.35;
    public static final double KI_ATTR_C = 0.4;

    // ==================== 评论/搜索标签公式参数 ====================
    /** 评论标签: 权重 = clamp(count/total*2, 0.1, 0.5) */
    public static final double COMMENT_W_SCALE  = 2.0;
    public static final double COMMENT_W_MIN    = 0.1;
    public static final double COMMENT_W_MAX    = 0.5;
    public static final double COMMENT_C_BASE   = 0.3;
    public static final double COMMENT_C_PER    = 0.05;
    public static final double COMMENT_C_MIN    = 0.3;
    public static final double COMMENT_C_MAX    = 0.8;
    /** 搜索标签: 权重 = clamp(count*0.08*avgComp, 0.15, 0.7) */
    public static final double SEARCH_W_PER     = 0.08;
    public static final double SEARCH_W_MIN     = 0.15;
    public static final double SEARCH_W_MAX     = 0.7;
    public static final double SEARCH_C_BASE    = 0.3;
    public static final double SEARCH_C_PER     = 0.06;
    public static final double SEARCH_C_MIN     = 0.3;
    public static final double SEARCH_C_MAX     = 0.85;
    /** 趋势标签 */
    public static final double TREND_W_BASE     = 0.15;
    public static final double TREND_W_RANGE    = 0.35;
    public static final double TREND_W_MIN      = 0.1;
    public static final double TREND_W_MAX      = 0.5;
    public static final double TREND_C_BASE     = 0.25;
    public static final double TREND_C_RANGE    = 0.25;
    public static final double TREND_C_MIN      = 0.2;
    public static final double TREND_C_MAX      = 0.5;
    /** 合并权重: 同源 EMA old*0.7+new*0.3 */
    public static final double MERGE_SAME_SRC_OLD_W = 0.7;
    public static final double MERGE_SAME_SRC_NEW_W = 0.3;
    /** 最小评论长度 */
    public static final int COMMENT_MIN_LEN = 4;
    /** 最小关键词长度 */
    public static final int KEYWORD_MIN_LEN = 2;

    // ==================== 行为验证 (校准) ====================
    public static final double VAL_COMPLETION_BONUS   = 0.15;
    public static final double VAL_BOUNCE_PENALTY     = 0.20;
    public static final double VAL_LIKE_BONUS         = 0.10;
    public static final double VAL_COMMENT_BONUS      = 0.05;
    public static final double VAL_QUALITY_AI_W       = 0.7;
    public static final double VAL_QUALITY_BEHAVIOR_W = 0.3;
    public static final double VAL_HIGH_COMPLETION    = 0.7;
    public static final double VAL_LOW_COMPLETION     = 0.25;
    public static final double VAL_HIGH_BOUNCE        = 0.6;
    public static final double VAL_QUALITY_THRESHOLD  = 0.7;
    public static final int    VAL_MIN_VIEWS          = 10;
    public static final double VAL_EPSILON            = 0.01;
    public static final double VAL_CONF_FLOOR         = 0.1;
    public static final double VAL_FACTOR_MIN         = 0.6;
    public static final double VAL_FACTOR_MAX         = 1.3;

    // ==================== 搜索建议权重 ====================
    public static final double SS_CO_SEARCH_CAP   = 0.8;
    public static final double SS_CO_SEARCH_PER   = 0.08;
    public static final double SS_CONTENT_KW      = 0.5;
    public static final double SS_CATEGORY        = 0.4;
    public static final double SS_DESC_PHRASE     = 0.35;
    public static final double SS_TREND_CAT       = 0.3;
    public static final double SS_CO_SEARCH_WRITE_MIN = 0.2;
    public static final double SS_CO_SEARCH_WRITE_MAX = 0.6;
    public static final double SS_CO_SEARCH_WRITE_PER = 0.08;
    public static final double SS_PREFIX_BASE     = 0.3;
    public static final double SS_PREFIX_LIKE_PER = 0.05;
    public static final int    SS_PREFIX_LIKE_DIV = 100;
    public static final int    SS_PREFIX_LIKE_CAP = 5;
    public static final double SS_SEMANTIC_BASE   = 0.5;
    public static final double SS_SEMANTIC_OVERLAP = 0.1;
    public static final int    SS_SEMANTIC_OVERLAP_CAP = 3;
    public static final double SS_SEMANTIC_WORD   = 0.35;
    public static final double SS_SEMANTIC_KW     = 0.3;
    public static final double SS_CONTEXT_KW      = 0.45;
    public static final double SS_CONTEXT_CAT     = 0.4;
    public static final double SS_CONTEXT_CO      = 0.5;
    public static final double SS_FREQ_BASE       = 0.2;
    public static final double SS_FREQ_PER        = 0.04;
    public static final double SS_FREQ_MAX        = 0.6;
    public static final double SS_HINT_TYPE_THRESHOLD = 0.5;
    public static final int    SS_PREFIX_OVERSAMPLE    = 3;
    public static final int    SS_SEMANTIC_OVERSAMPLE  = 3;
    public static final int    SS_CONTEXT_OVERSAMPLE   = 2;
    public static final int    SS_FREQ_OVERSAMPLE      = 2;
    public static final int    SS_HOT_QUERIES_LIMIT    = 5;
    public static final int    SS_RECENT_WATCHERS      = 100;
    public static final int    SS_CO_WATCHERS          = 50;
    public static final int    SS_FREQ_PROFILES        = 200;
    public static final int    SS_HOT_PROFILES         = 50;
    public static final int    SS_HOT_PER_USER         = 2;
    public static final int    SS_HOT_RANK_PROFILES    = 500;
    public static final int    SS_HOT_RANK_VIDEOS      = 100;
    public static final int    SS_SEARCH_BATCH_SIZE    = 15;
    public static final int    SS_KW_TRUNCATE          = 5;
    public static final int    SS_CATEGORY_TOP         = 3;
    public static final int    SS_USER_CAP             = 5;
    public static final int    SS_SNIPPET_PAD          = 12;
    public static final int    SS_VIDEO_BATCH_LIMIT    = 20;
    public static final int    SS_MAX_WORD_LEN         = 20;
    public static final int    SS_KW_MIN_LEN           = 2;
    public static final int    SS_KW_MAX_LEN           = 40;
    public static final int    SS_DESC_MIN_LEN         = 2;
    public static final int    SS_DESC_MAX_LEN         = 30;

    // ==================== 用户画像 ====================
    /** 创作者亲和力增量 (等效观看秒数) */
    public static final double UP_AFF_INC_LIKE         = 30.0;
    public static final double UP_AFF_INC_COLLECT      = 40.0;
    public static final double UP_AFF_INC_SHARE        = 35.0;
    public static final double UP_AFF_INC_COMMENT      = 20.0;
    public static final double UP_AFF_INC_FOLLOW       = 50.0;
    public static final double UP_AFF_INC_PROFILE_VISIT = 5.0;
    public static final double UP_BOUNCE_THRESHOLD_SEC  = 3.0;
    public static final double UP_SEARCH_WATCH_MIN_SEC  = 5.0;
    public static final double UP_MUSIC_COMPLETION_MIN  = 0.5;
    public static final double UP_CAT_INCREMENT_MAX     = 0.3;
    public static final double UP_CAT_NORM_SEC          = 300.0;
    public static final double UP_CAT_DECAY_FLOOR       = 0.01;
    public static final double UP_AFF_BOUNCE_DAMP       = 0.1;
    public static final double UP_AFF_OLD_DECAY         = 0.95;
    public static final double UP_AFF_INC_FACTOR        = 0.01;
    public static final double UP_AFF_INIT_MIN          = -1.0;
    public static final double UP_AFF_CLAMP_MIN         = -0.5;
    public static final double UP_AFF_CLAMP_MAX         = 1.0;
    public static final double UP_TRAFFIC_INCREMENT     = 0.02;
    public static final double UP_SOCIAL_ENGAGE_PER_FOLLOW = 0.02;
    public static final double UP_SOCIAL_ENGAGE_PER_VISIT = 0.01;
    public static final double UP_SOCIAL_RATE_FACTOR    = 0.1;
    public static final double UP_SEARCH_FREQ_SCALE     = 10.0;
    public static final double UP_SESSION_SEC_PER_VIDEO = 15.0;
    public static final double UP_BPM_INIT_MIN_FACTOR   = 0.8;
    public static final double UP_BPM_INIT_MAX_FACTOR   = 1.2;
    public static final double UP_BPM_MIN_RANGE         = 10.0;
    public static final double UP_BPM_RANGE_PAD         = 5.0;
    public static final int    UP_ACTIVE_HOURS_CAP      = 255;

    // ==================== 用户分类阈值 ====================
    public static final int    UCLASS_MIN_VIEWS        = 20;
    public static final double UCLASS_BOUNCE_HIGH      = 0.65;
    public static final double UCLASS_LIKE_LOW         = 0.05;
    public static final double UCLASS_COLLECT_LOW      = 0.02;
    public static final double UCLASS_SOCIAL_HIGH      = 0.3;
    public static final int    UCLASS_PROFILE_VISIT_MIN = 10;
    public static final double UCLASS_LIKE_HIGH        = 0.4;
    public static final double UCLASS_COLLECT_HIGH     = 0.1;
    public static final double UCLASS_SEARCH_HIGH      = 2.0;
    public static final double UCLASS_AFF_HIGH         = 0.5;
    public static final double UCLASS_CAT_DIV_SIG      = 0.1;
    public static final int    UCLASS_CAT_DIV_MIN       = 5;

    // ==================== 用户分层阈值 ====================
    public static final int SEG_NEW_USER   = 50;
    public static final int SEG_LIGHT_MAX  = 300;
    public static final int SEG_MEDIUM_MAX = 1500;
    public static final int SEG_LIGHT_DAYS_MIN = 3;
    public static final int SEG_MEDIUM_DAYS_MIN = 5;

    // ==================== 特征提取 ====================
    public static final int FEATURE_MAX_RETRIES   = 5;
    public static final long FEATURE_BASE_DELAY_MS = 10_000;

    // ==================== 内容向量继承 ====================
    public static final int INHERIT_POOL_SIZE = 200;
    public static final int INHERIT_TOP_K     = 5;
    public static final double INHERIT_MIN_SIMILARITY = 0.3;

    // ==================== 品类负反馈 / 正反馈 ====================
    public static final int CAT_SKIP_COUNT_THRESHOLD    = 3;
    public static final int CAT_LIKE_COUNT_THRESHOLD    = 2;
    public static final double CAT_WELL_WATCHED_COMPLETION = 0.8;

    // ==================== 用户体验 ====================
    public static final int DEFAULT_VIDEO_DURATION_SEC = 60;
}
