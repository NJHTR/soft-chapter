package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_visitor")
public class Visitor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被访问者(主页主人) */
    private Long userId;

    /** 访问者 */
    private Long visitorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
