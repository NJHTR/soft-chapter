package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_message")
public class ShopMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String type;
    private Long relatedId;
    private Integer isRead;
    private LocalDateTime createTime;
}
