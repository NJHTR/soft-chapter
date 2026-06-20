package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long sellerId;

    private Long goodsId;

    private String goodsName;

    private String goodsCover;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalAmount;

    /** PENDING / PAID / SHIPPED / RECEIVED / CANCELLED */
    private String status;

    /** 幂等键: 防止重复支付 */
    private String idempotencyKey;

    /** 支付方式: wallet/wechat/alipay/bank */
    private String paymentMethod;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
