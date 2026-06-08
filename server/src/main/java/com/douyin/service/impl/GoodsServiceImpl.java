package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.douyin.common.PageDTO;
import com.douyin.entity.Cart;
import com.douyin.entity.FavoriteGoods;
import com.douyin.entity.Goods;
import com.douyin.entity.User;
import com.douyin.mapper.CartMapper;
import com.douyin.mapper.FavoriteGoodsMapper;
import com.douyin.mapper.GoodsMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.service.GoodsService;
import com.douyin.vo.GoodsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoodsServiceImpl implements GoodsService {

    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final FavoriteGoodsMapper favoriteMapper;
    private final CartMapper cartMapper;

    public GoodsServiceImpl(GoodsMapper goodsMapper, UserMapper userMapper,
                            FavoriteGoodsMapper favoriteMapper, CartMapper cartMapper) {
        this.goodsMapper = goodsMapper;
        this.userMapper = userMapper;
        this.favoriteMapper = favoriteMapper;
        this.cartMapper = cartMapper;
    }

    @Override
    public PageDTO<GoodsVO> listPublished(int pageNo, int pageSize) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 1)
                .orderByDesc(Goods::getCreateTime);
        Page<Goods> page = new Page<>(pageNo, pageSize);
        Page<Goods> result = goodsMapper.selectPage(page, wrapper);
        List<GoodsVO> list = result.getRecords().stream()
                .map(GoodsVO::from)
                .peek(this::fillSeller)
                .toList();
        return new PageDTO<>((int) result.getTotal(), pageNo, pageSize, list);
    }

    @Override
    public GoodsVO getDetail(Long id) {
        return getDetail(id, null);
    }

    @Override
    public GoodsVO getDetail(Long id, Long userId) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getIsDelete() != null && goods.getIsDelete() == 1) return null;
        GoodsVO vo = GoodsVO.from(goods);
        fillSeller(vo);
        // 收藏状态
        if (userId != null) {
            vo.setIsFavorited(favoriteMapper.selectCount(new LambdaQueryWrapper<FavoriteGoods>()
                    .eq(FavoriteGoods::getUserId, userId)
                    .eq(FavoriteGoods::getGoodsId, id)) > 0);
        }
        return vo;
    }

    @Override
    public GoodsVO publish(Long sellerId, String name, String price, String realPrice,
                           String cover, String imgs, String description, String discount,
                           String guarantee, String specs, String shippingFrom, String shippingFee, String shippingTime) {
        Goods goods = new Goods();
        goods.setSellerId(sellerId);
        goods.setName(name);
        goods.setPrice(new BigDecimal(price));
        if (realPrice != null && !realPrice.isEmpty()) goods.setRealPrice(new BigDecimal(realPrice));
        goods.setCover(cover);
        goods.setImgs(imgs);
        goods.setDescription(description);
        goods.setDiscount(discount);
        goods.setSold(0);
        goods.setStatus(1);
        goods.setGuarantee(guarantee != null ? guarantee : "");
        goods.setSpecs(specs);
        goods.setShippingFrom(shippingFrom != null ? shippingFrom : "");
        goods.setShippingFee(shippingFee != null && !shippingFee.isEmpty() ? new BigDecimal(shippingFee) : BigDecimal.ZERO);
        goods.setShippingTime(shippingTime != null ? shippingTime : "");
        goodsMapper.insert(goods);
        log.info("商品发布: id={} seller={} name={}", goods.getId(), sellerId, name);
        return GoodsVO.from(goods);
    }

    @Override
    public GoodsVO update(Long id, Long sellerId, String name, String price, String realPrice,
                          String cover, String imgs, String description, String discount, Integer status,
                          String guarantee, String specs, String shippingFrom, String shippingFee, String shippingTime) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || !goods.getSellerId().equals(sellerId)) return null;
        if (name != null) goods.setName(name);
        if (price != null) goods.setPrice(new BigDecimal(price));
        if (realPrice != null) goods.setRealPrice(realPrice.isEmpty() ? null : new BigDecimal(realPrice));
        if (cover != null) goods.setCover(cover);
        if (imgs != null) goods.setImgs(imgs);
        if (description != null) goods.setDescription(description);
        if (discount != null) goods.setDiscount(discount);
        if (status != null) goods.setStatus(status);
        if (guarantee != null) goods.setGuarantee(guarantee);
        if (specs != null) goods.setSpecs(specs);
        if (shippingFrom != null) goods.setShippingFrom(shippingFrom);
        if (shippingFee != null) goods.setShippingFee(shippingFee.isEmpty() ? null : new BigDecimal(shippingFee));
        if (shippingTime != null) goods.setShippingTime(shippingTime);
        goodsMapper.updateById(goods);
        return GoodsVO.from(goods);
    }

    @Override
    public void delete(Long id, Long sellerId) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || !goods.getSellerId().equals(sellerId)) return;
        goodsMapper.deleteById(id);
    }

    @Override
    public PageDTO<GoodsVO> myGoods(Long sellerId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getSellerId, sellerId)
                .orderByDesc(Goods::getCreateTime);
        Page<Goods> page = new Page<>(pageNo, pageSize);
        Page<Goods> result = goodsMapper.selectPage(page, wrapper);
        List<GoodsVO> list = result.getRecords().stream()
                .map(GoodsVO::from)
                .toList();
        return new PageDTO<>((int) result.getTotal(), pageNo, pageSize, list);
    }

    @Override
    public boolean toggleFavorite(Long userId, Long goodsId) {
        LambdaQueryWrapper<FavoriteGoods> wrapper = new LambdaQueryWrapper<FavoriteGoods>()
                .eq(FavoriteGoods::getUserId, userId)
                .eq(FavoriteGoods::getGoodsId, goodsId);
        FavoriteGoods exist = favoriteMapper.selectOne(wrapper);
        if (exist != null) {
            favoriteMapper.deleteById(exist.getId());
            return false;
        }
        FavoriteGoods f = new FavoriteGoods();
        f.setUserId(userId);
        f.setGoodsId(goodsId);
        favoriteMapper.insert(f);
        return true;
    }

    @Override
    public List<GoodsVO> myFavorites(Long userId) {
        List<FavoriteGoods> favs = favoriteMapper.selectList(new LambdaQueryWrapper<FavoriteGoods>()
                .eq(FavoriteGoods::getUserId, userId)
                .orderByDesc(FavoriteGoods::getCreateTime));
        if (favs.isEmpty()) return List.of();
        List<Long> goodsIds = favs.stream().map(FavoriteGoods::getGoodsId).toList();
        List<Goods> goodsList = goodsMapper.selectBatchIds(goodsIds);
        // 保持收藏顺序
        java.util.Map<Long, GoodsVO> voMap = goodsList.stream()
                .map(GoodsVO::from)
                .peek(this::fillSeller)
                .collect(Collectors.toMap(GoodsVO::getId, v -> v));
        List<GoodsVO> result = new ArrayList<>();
        for (Long gid : goodsIds) {
            GoodsVO vo = voMap.get(gid);
            if (vo != null) {
                vo.setIsFavorited(true);
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public void addToCart(Long userId, Long goodsId, int quantity) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getGoodsId, goodsId);
        Cart exist = cartMapper.selectOne(wrapper);
        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + quantity);
            cartMapper.updateById(exist);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setGoodsId(goodsId);
            cart.setQuantity(quantity);
            cartMapper.insert(cart);
        }
    }

    @Override
    public List<java.util.Map<String, Object>> myCart(Long userId) {
        List<Cart> cartItems = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));
        if (cartItems.isEmpty()) return List.of();
        List<Long> goodsIds = cartItems.stream().map(Cart::getGoodsId).toList();
        List<Goods> goodsList = goodsMapper.selectBatchIds(goodsIds);
        java.util.Map<Long, GoodsVO> voMap = goodsList.stream()
                .map(GoodsVO::from)
                .peek(this::fillSeller)
                .collect(Collectors.toMap(GoodsVO::getId, v -> v));
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        for (Cart item : cartItems) {
            GoodsVO vo = voMap.get(item.getGoodsId());
            if (vo != null) {
                java.util.Map<String, Object> entry = new java.util.HashMap<>();
                entry.put("cart_id", item.getId());
                entry.put("quantity", item.getQuantity());
                entry.put("goods", vo);
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public void removeFromCart(Long userId, Long cartId) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getId, cartId);
        cartMapper.delete(wrapper);
    }

    @Override
    public void updateCartQuantity(Long userId, Long cartId, int quantity) {
        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getId, cartId));
        if (cart != null) {
            cart.setQuantity(Math.max(1, quantity));
            cartMapper.updateById(cart);
        }
    }

    @Override
    public List<GoodsVO> sellerOtherGoods(Long sellerId, int limit) {
        List<Goods> list = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getSellerId, sellerId)
                .eq(Goods::getStatus, 1)
                .orderByDesc(Goods::getCreateTime)
                .last("LIMIT " + limit));
        return list.stream().map(GoodsVO::from).peek(this::fillSeller).toList();
    }

    @Override
    public PageDTO<GoodsVO> search(String keyword, int pageNo, int pageSize) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 1)
                .like(Goods::getName, keyword)
                .orderByDesc(Goods::getCreateTime);
        Page<Goods> page = new Page<>(pageNo, pageSize);
        Page<Goods> result = goodsMapper.selectPage(page, wrapper);
        List<GoodsVO> list = result.getRecords().stream()
                .map(GoodsVO::from)
                .peek(this::fillSeller)
                .toList();
        return new PageDTO<>((int) result.getTotal(), pageNo, pageSize, list);
    }

    private void fillSeller(GoodsVO vo) {
        if (vo.getSellerId() != null) {
            User user = userMapper.selectById(vo.getSellerId());
            if (user != null) {
                vo.setSellerName(user.getNickname());
                vo.setSellerAvatar(user.getAvatar168Url());
            }
        }
    }
}
