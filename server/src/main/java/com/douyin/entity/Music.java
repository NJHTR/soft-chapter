package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_music")
public class Music {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String artist;
    private String album;
    private String coverUrl;
    private Integer duration;

    /** 来源: netease/local */
    private String source;

    /** 来源ID (网易云歌曲ID) */
    private Long sourceId;

    /** 播放地址 */
    private String playUrl;

    /** 歌词 */
    private String lyric;

    private LocalDateTime createTime;
}
