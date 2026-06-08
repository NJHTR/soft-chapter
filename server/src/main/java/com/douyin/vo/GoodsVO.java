package com.douyin.vo;

import com.douyin.entity.Goods;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GoodsVO {
    private Long id;
    private Long sellerId;
    private String name;
    private BigDecimal price;
    private BigDecimal realPrice;
    private String cover;
    private List<String> imgs;
    private String description;
    private Integer sold;
    private String discount;
    private Integer status;
    private String createTime;
    private String guarantee;
    private String specs;
    private String shippingFrom;
    private BigDecimal shippingFee;
    private String shippingTime;

    /** 是否已收藏 (当前用户) */
    private Boolean isFavorited;

    /** 卖家昵称 */
    private String sellerName;
    /** 卖家头像 */
    private String sellerAvatar;

    public static GoodsVO from(Goods g) {
        GoodsVO vo = new GoodsVO();
        vo.setId(g.getId());
        vo.setSellerId(g.getSellerId());
        vo.setName(g.getName());
        vo.setPrice(g.getPrice());
        vo.setRealPrice(g.getRealPrice());
        vo.setCover(g.getCover());
        vo.setDescription(g.getDescription());
        vo.setSold(g.getSold() != null ? g.getSold() : 0);
        vo.setDiscount(g.getDiscount());
        vo.setStatus(g.getStatus());
        vo.setCreateTime(g.getCreateTime() != null ? g.getCreateTime().toString() : null);
        vo.setGuarantee(g.getGuarantee() != null ? g.getGuarantee() : "");
        vo.setSpecs(g.getSpecs());
        vo.setShippingFrom(g.getShippingFrom() != null ? g.getShippingFrom() : "");
        vo.setShippingFee(g.getShippingFee() != null ? g.getShippingFee() : BigDecimal.ZERO);
        vo.setShippingTime(g.getShippingTime() != null ? g.getShippingTime() : "");

        // 解析 JSON 数组
        if (g.getImgs() != null && !g.getImgs().isEmpty()) {
            try {
                vo.setImgs(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(g.getImgs(), List.class));
            } catch (Exception ignored) {}
        }
        return vo;
    }
}
