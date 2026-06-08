package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.service.GoodsService;
import com.douyin.service.ShopMessageService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.GoodsVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final GoodsService goodsService;
    private final JwtUtil jwtUtil;
    private final ShopMessageService shopMessageService;

    public ShopController(GoodsService goodsService, JwtUtil jwtUtil,
                          ShopMessageService shopMessageService) {
        this.goodsService = goodsService;
        this.jwtUtil = jwtUtil;
        this.shopMessageService = shopMessageService;
    }

    /** 商品推荐列表 */
    @GetMapping("/recommended")
    public Result<PageDTO<GoodsVO>> recommended(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(goodsService.listPublished(pageNo, pageSize));
    }

    /** 商品详情 (带收藏状态) */
    @GetMapping("/detail/{id}")
    public Result<GoodsVO> detail(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        GoodsVO vo = goodsService.getDetail(id, userId);
        if (vo == null) return Result.fail("商品不存在");
        return Result.ok(vo);
    }

    /** 商家发布商品 */
    @PostMapping("/goods")
    public Result<GoodsVO> publish(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        String name = (String) body.get("name");
        String price = body.get("price") != null ? body.get("price").toString() : "0";
        String realPrice = body.get("real_price") != null ? body.get("real_price").toString() : null;
        String cover = (String) body.get("cover");
        String imgs = body.get("imgs") != null ? body.get("imgs").toString() : null;
        String description = (String) body.get("description");
        String discount = (String) body.get("discount");
        String guarantee = (String) body.get("guarantee");
        String specs = body.get("specs") != null ? body.get("specs").toString() : null;
        String shippingFrom = (String) body.get("shipping_from");
        String shippingFee = body.get("shipping_fee") != null ? body.get("shipping_fee").toString() : null;
        String shippingTime = (String) body.get("shipping_time");

        if (name == null || name.isEmpty()) return Result.fail("商品名称不能为空");
        if (price == null || price.isEmpty()) return Result.fail("商品价格不能为空");

        GoodsVO vo = goodsService.publish(userId, name, price, realPrice, cover, imgs, description,
                discount, guarantee, specs, shippingFrom, shippingFee, shippingTime);
        return Result.ok(vo);
    }

    /** 商家更新商品 */
    @PutMapping("/goods/{id}")
    public Result<GoodsVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                   HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");

        String name = (String) body.get("name");
        String price = body.get("price") != null ? body.get("price").toString() : null;
        String realPrice = body.get("real_price") != null ? body.get("real_price").toString() : null;
        String cover = (String) body.get("cover");
        String imgs = body.get("imgs") != null ? body.get("imgs").toString() : null;
        String description = (String) body.get("description");
        String discount = (String) body.get("discount");
        Integer status = body.get("status") != null
                ? Integer.valueOf(body.get("status").toString()) : null;
        String guarantee = (String) body.get("guarantee");
        String specs = body.get("specs") != null ? body.get("specs").toString() : null;
        String shippingFrom = (String) body.get("shipping_from");
        String shippingFee = body.get("shipping_fee") != null ? body.get("shipping_fee").toString() : null;
        String shippingTime = (String) body.get("shipping_time");

        GoodsVO vo = goodsService.update(id, userId, name, price, realPrice, cover, imgs,
                description, discount, status, guarantee, specs, shippingFrom, shippingFee, shippingTime);
        if (vo == null) return Result.fail("商品不存在或无权限");
        return Result.ok(vo);
    }

    /** 商家删除商品 */
    @DeleteMapping("/goods/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        goodsService.delete(id, userId);
        return Result.ok();
    }

    /** 商家的商品列表 */
    @GetMapping("/my-goods")
    public Result<PageDTO<GoodsVO>> myGoods(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(goodsService.myGoods(userId, pageNo, pageSize));
    }

    /** 收藏/取消收藏 */
    @PostMapping("/favorite/{goodsId}")
    public Result<Map<String, Boolean>> toggleFavorite(@PathVariable Long goodsId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        boolean favorited = goodsService.toggleFavorite(userId, goodsId);
        return Result.ok(Map.of("favorited", favorited));
    }

    /** 我的收藏 */
    @GetMapping("/favorites")
    public Result<java.util.List<GoodsVO>> myFavorites(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(goodsService.myFavorites(userId));
    }

    /** 加入购物车 */
    @PostMapping("/cart")
    public Result<Void> addToCart(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        Long goodsId = body.get("goods_id") != null ? Long.valueOf(body.get("goods_id").toString()) : null;
        int quantity = body.get("quantity") != null ? Integer.parseInt(body.get("quantity").toString()) : 1;
        if (goodsId == null) return Result.fail("商品ID不能为空");
        goodsService.addToCart(userId, goodsId, quantity);
        // 推送消息通知
        GoodsVO g = goodsService.getDetail(goodsId);
        String goodsName = g != null ? g.getName() : "商品";
        shopMessageService.create(userId, "已加入购物车",
                "您已将「" + goodsName + "」加入购物车", "cart", goodsId);
        return Result.ok();
    }

    /** 购物车列表 */
    @GetMapping("/cart")
    public Result<java.util.List<java.util.Map<String, Object>>> myCart(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        return Result.ok(goodsService.myCart(userId));
    }

    /** 从购物车移除 */
    @DeleteMapping("/cart/{cartId}")
    public Result<Void> removeFromCart(@PathVariable Long cartId, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        goodsService.removeFromCart(userId, cartId);
        return Result.ok();
    }

    /** 更新购物车数量 */
    @PutMapping("/cart/{cartId}")
    public Result<Void> updateCartQuantity(@PathVariable Long cartId, @RequestBody Map<String, Object> body,
                                           HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        int quantity = body.get("quantity") != null ? Integer.parseInt(body.get("quantity").toString()) : 1;
        goodsService.updateCartQuantity(userId, cartId, quantity);
        return Result.ok();
    }

    /** 卖家的其他商品 */
    @GetMapping("/seller/{sellerId}/goods")
    public Result<java.util.List<GoodsVO>> sellerGoods(@PathVariable Long sellerId,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(goodsService.sellerOtherGoods(sellerId, limit));
    }

    /** 搜索商品 */
    @GetMapping("/search")
    public Result<PageDTO<GoodsVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(goodsService.search(keyword, pageNo, pageSize));
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }
}
