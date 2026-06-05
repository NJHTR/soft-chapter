package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_video_content")
public class VideoContent {

    @TableId(type = IdType.INPUT)
    private Long videoId;

    /** Qwen2.5-VL 自然语言描述 */
    private String visualDesc;

    /** 场景标签 JSON: ["厨房","室内"] */
    private String sceneTags;

    /** 物体标签 JSON: ["美食","人物"] */
    private String objectTags;

    /** 视觉 embedding JSON float array */
    private String visualEmbedding;

    /** BPM */
    private Double musicBpm;

    /** 调性 */
    private String musicKey;

    /** 能量 0-1 */
    private Double musicEnergy;

    /** 效价 0-1 */
    private Double musicValence;

    /** 频谱质心 */
    private Double musicSpectral;

    /** MFCC JSON float array */
    private String musicMfcc;

    /** 关键词 JSON */
    private String keywords;

    /** 分类 */
    private String textCategory;

    /** 文本语义向量 JSON float array */
    private String textEmbedding;

    /** 融合内容向量 JSON float array */
    private String contentVector;

    /** 质量分 */
    private Double qualityScore;

    /** 提取状态: 0=待处理 1=完成 2=失败 */
    private Integer extractStatus;

    /** 提取耗时(ms) */
    private Integer extractTimeMs;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
