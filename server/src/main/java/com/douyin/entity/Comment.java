package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_comment")
public class Comment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 视频ID */
    private Long videoId;

    /** 评论用户ID */
    private Long userId;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Long likeCount;

    /** 回复数量 */
    private Integer replyCount;

    /** 父评论ID(一级评论为0) */
    private Long parentId;

    /** 回复目标用户ID */
    private Long replyToUserId;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
