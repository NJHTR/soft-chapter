package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_cart")
public class Cart {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long goodsId;
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
