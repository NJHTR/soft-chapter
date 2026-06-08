package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_goods")
public class Goods {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sellerId;

    private String name;

    private BigDecimal price;

    private BigDecimal realPrice;

    private String cover;

    private String imgs;

    private String description;

    private Integer sold;

    private String discount;

    /** 保障信息 */
    private String guarantee;

    /** 规格参数 JSON */
    private String specs;

    /** 发货地 */
    private String shippingFrom;

    /** 运费 */
    private BigDecimal shippingFee;

    /** 发货时间描述 */
    private String shippingTime;

    /** 1=上架 0=下架 */
    private Integer status;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
