package com.douyin.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private Long userId;
    private Long goodsId;
    private String goodsName;
    private String goodsCover;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String status;
    private String idempotencyKey;
    private String paymentMethod;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static OrderVO from(com.douyin.entity.Order o) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setUserId(o.getUserId());
        vo.setGoodsId(o.getGoodsId());
        vo.setGoodsName(o.getGoodsName());
        vo.setGoodsCover(o.getGoodsCover());
        vo.setPrice(o.getPrice());
        vo.setQuantity(o.getQuantity());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setStatus(o.getStatus());
        vo.setIdempotencyKey(o.getIdempotencyKey());
        vo.setPaymentMethod(o.getPaymentMethod());
        vo.setReceiverName(o.getReceiverName());
        vo.setReceiverPhone(o.getReceiverPhone());
        vo.setReceiverAddress(o.getReceiverAddress());
        vo.setRemark(o.getRemark());
        vo.setCreateTime(o.getCreateTime());
        vo.setUpdateTime(o.getUpdateTime());
        return vo;
    }
}
