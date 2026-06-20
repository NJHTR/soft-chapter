package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_video_tag")
public class VideoTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    private String tag;
    private String source;
    private Double weight;
}
