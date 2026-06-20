package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_payment_audit")
public class PaymentAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long orderId;
    private String action;
    private BigDecimal requestAmount;
    private BigDecimal orderAmount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String paymentMethod;
    private String result;
    private String failReason;
    private String ip;
    private String userAgent;
    private String riskLevel;
    private String riskDetail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
