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

    /** VLM 自由画面描述 (Qwen2-VL) */
    private String visualDesc;

    /** 场景标签 JSON: ["厨房","室内"] */
    private String sceneTags;

    /** 物体标签 JSON: ["美食","人物"] */
    private String objectTags;

    /** 视觉 embedding JSON float array */
    private String visualEmbedding;

    /** 多标签分类 JSON: [{"label":"美食","confidence":0.85}, ...] */
    private String categories;

    /** 情绪/氛围 */
    private String mood;

    /** 视频风格 */
    private String style;

    /** 画质标签 */
    private String qualityLabel;

    /** 音乐流派 */
    private String musicGenre;

    /** 内容属性标签 JSON: ["有人脸","有字幕",...] */
    private String contentAttributes;

    /** ASR 语音转录文本 */
    private String transcript;

    /** ASR 转录关键词 JSON */
    private String asrKeywords;

    /** 是否有语音 */
    private Boolean hasSpeech;

    /** 人脸数量 (平均每帧) */
    private Double faceCount;

    /** 人脸占比 0-1 */
    private Double faceRatio;

    /** 是否人脸特写 */
    private Boolean isCloseup;

    /** 是否单人出镜 */
    private Boolean isSinglePerson;

    /** 是否多人场景 */
    private Boolean isMultiPerson;

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

    /** 频谱滚降 */
    private Double musicRolloff;

    /** 频谱带宽 */
    private Double musicBandwidth;

    /** 过零率 */
    private Double musicZcr;

    /** 节拍复杂度 */
    private Double musicOnsetRate;

    /** MFCC JSON float array */
    private String musicMfcc;

    /** 是否包含音乐 */
    private Boolean audioHasMusic;

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
