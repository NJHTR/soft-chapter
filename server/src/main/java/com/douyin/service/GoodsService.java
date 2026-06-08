package com.douyin.service;

import com.douyin.common.PageDTO;
import com.douyin.vo.GoodsVO;

import java.util.List;

public interface GoodsService {

    /** 商品推荐列表 (已上架) */
    PageDTO<GoodsVO> listPublished(int pageNo, int pageSize);

    /** 商品详情 */
    GoodsVO getDetail(Long id);

    /** 商品详情 (带当前用户收藏状态) */
    GoodsVO getDetail(Long id, Long userId);

    /** 商家发布商品 */
    GoodsVO publish(Long sellerId, String name, String price, String realPrice,
                    String cover, String imgs, String description, String discount,
                    String guarantee, String specs, String shippingFrom, String shippingFee, String shippingTime);

    /** 商家更新商品 */
    GoodsVO update(Long id, Long sellerId, String name, String price, String realPrice,
                   String cover, String imgs, String description, String discount, Integer status,
                   String guarantee, String specs, String shippingFrom, String shippingFee, String shippingTime);

    /** 商家删除商品 (逻辑删除) */
    void delete(Long id, Long sellerId);

    /** 商家的商品列表 */
    PageDTO<GoodsVO> myGoods(Long sellerId, int pageNo, int pageSize);

    /** 收藏/取消收藏, 返回是否已收藏 */
    boolean toggleFavorite(Long userId, Long goodsId);

    /** 用户收藏列表 */
    List<GoodsVO> myFavorites(Long userId);

    /** 加入购物车 */
    void addToCart(Long userId, Long goodsId, int quantity);

    /** 购物车列表: Map(cart_id, quantity, goods) */
    java.util.List<java.util.Map<String, Object>> myCart(Long userId);

    /** 从购物车移除 */
    void removeFromCart(Long userId, Long cartId);

    /** 更新购物车数量 */
    void updateCartQuantity(Long userId, Long cartId, int quantity);

    /** 卖家的其他商品 */
    List<GoodsVO> sellerOtherGoods(Long sellerId, int limit);

    /** 搜索商品 */
    PageDTO<GoodsVO> search(String keyword, int pageNo, int pageSize);
}
