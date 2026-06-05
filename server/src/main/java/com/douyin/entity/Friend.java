package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_friend")
public class Friend {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起方 */
    private Long userId;

    /** 接收方 */
    private Long friendId;

    /** 0待确认 1已接受 2已拒绝 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
