package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.common.PageDTO;
import com.douyin.entity.Cart;
import com.douyin.entity.Goods;
import com.douyin.entity.Order;
import com.douyin.mapper.CartMapper;
import com.douyin.mapper.GoodsMapper;
import com.douyin.mapper.OrderMapper;
import com.douyin.service.OrderService;
import com.douyin.service.PaymentSecurityService;
import com.douyin.service.WalletService;
import com.douyin.service.payment.IdempotencyManager;
import com.douyin.service.payment.PaymentStateMachine;
import com.douyin.service.payment.PaymentStrategy;
import com.douyin.service.payment.PaymentStrategyFactory;
import com.douyin.service.payment.RetryUtil;
import com.douyin.vo.OrderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final GoodsMapper goodsMapper;
    private final CartMapper cartMapper;
    private final WalletService walletService;
    private final PaymentSecurityService securityService;
    private final PaymentStrategyFactory strategyFactory;
    private final IdempotencyManager idempotencyManager;

    public OrderServiceImpl(GoodsMapper goodsMapper, CartMapper cartMapper,
                            WalletService walletService, PaymentSecurityService securityService,
                            PaymentStrategyFactory strategyFactory, IdempotencyManager idempotencyManager) {
        this.goodsMapper = goodsMapper;
        this.cartMapper = cartMapper;
        this.walletService = walletService;
        this.securityService = securityService;
        this.strategyFactory = strategyFactory;
        this.idempotencyManager = idempotencyManager;
    }

    @Override
    @Transactional
    public OrderVO place(Long userId, Long goodsId, Integer quantity,
                         String receiverName, String receiverPhone,
                         String receiverAddress, String remark) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            throw new RuntimeException("商品已下架");
        }

        BigDecimal price = goods.getRealPrice() != null ? goods.getRealPrice() : goods.getPrice();
        Order order = new Order();
        order.setUserId(userId);
        order.setSellerId(goods.getSellerId());
        order.setGoodsId(goodsId);
        order.setGoodsName(goods.getName());
        order.setGoodsCover(goods.getCover());
        order.setPrice(price);
        order.setQuantity(quantity != null ? quantity : 1);
        order.setTotalAmount(price.multiply(BigDecimal.valueOf(order.getQuantity())));
        order.setStatus("PENDING");
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        save(order);

        // 更新销量
        goods.setSold((goods.getSold() != null ? goods.getSold() : 0) + order.getQuantity());
        goodsMapper.updateById(goods);

        log.info("Order placed: userId={}, orderId={}, amount={}", userId, order.getId(), order.getTotalAmount());
        return OrderVO.from(order);
    }

    @Override
    @Transactional
    public List<OrderVO> placeFromCart(Long userId, List<Long> cartIds,
                                       String receiverName, String receiverPhone,
                                       String receiverAddress, String remark) {
        List<OrderVO> orders = new ArrayList<>();
        for (Long cartId : cartIds) {
            Cart cart = cartMapper.selectById(cartId);
            if (cart == null || !cart.getUserId().equals(userId)) continue;

            OrderVO vo = place(userId, cart.getGoodsId(), cart.getQuantity(),
                    receiverName, receiverPhone, receiverAddress, remark);
            orders.add(vo);

            // 下单成功 → 清除购物车该项
            cartMapper.deleteById(cartId);
        }
        return orders;
    }

    @Override
    public PageDTO<OrderVO> myOrders(Long userId, String status, int pageNo, int pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }
        Page<Order> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<OrderVO> list = page.getRecords().stream().map(OrderVO::from).toList();
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, list);
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        String newStatus = PaymentStateMachine.transition(order.getStatus(), "cancel");
        order.setStatus(newStatus);
        updateById(order);
    }

    @Override
    @Transactional
    public void ship(Long sellerId, Long orderId) {
        Order order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods == null || !goods.getSellerId().equals(sellerId)) {
            throw new RuntimeException("无权限操作此订单");
        }
        String newStatus = PaymentStateMachine.transition(order.getStatus(), "ship");
        order.setStatus(newStatus);
        updateById(order);
    }

    @Override
    @Transactional
    public void confirmReceive(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        String newStatus = PaymentStateMachine.transition(order.getStatus(), "confirm_receive");
        order.setStatus(newStatus);
        updateById(order);
    }

    @Override
    @Transactional
    public void pay(Long userId, Long orderId, String paymentMethod,
                    String idempotencyKey, HttpServletRequest req) {
        // ============ 1. 幂等性校验 ============
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            if (!idempotencyManager.tryAcquire(idempotencyKey, userId, orderId)) {
                log.warn("[幂等] 重复支付请求: userId={}, orderId={}, key={}", userId, orderId, idempotencyKey);
                throw new RuntimeException("支付请求已受理，请勿重复提交");
            }
        }

        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }

        // ============ 2. 状态机校验 ============
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态[" + order.getStatus() + "]不可支付");
        }

        // ============ 3. 金额一致性 + 安全校验 ============
        BigDecimal balanceBefore = walletService.getBalance(userId);
        String failReason = securityService.validatePay(
                userId, orderId, order.getTotalAmount(), order.getTotalAmount(),
                balanceBefore, paymentMethod, req);
        if (failReason != null) throw new RuntimeException(failReason);

        // ============ 4. 策略模式: 选择支付策略并执行 ============
        PaymentStrategy strategy = strategyFactory.getStrategy(paymentMethod != null ? paymentMethod : "wallet");
        BigDecimal balanceAfter = balanceBefore;

        try {
            balanceAfter = RetryUtil.executeWithRetry(() -> {
                BigDecimal result = strategy.execute(userId, order);
                return result != null ? result : balanceBefore;
            }, "支付执行[订单#" + orderId + " " + paymentMethod + "]");
        } catch (RuntimeException e) {
            log.error("[支付] 支付执行失败: orderId={}, msg={}", orderId, e.getMessage());
            throw new RuntimeException("支付失败: " + e.getMessage());
        }

        // ============ 5. 状态机流转: PENDING → PAID ============
        String newStatus = PaymentStateMachine.transition(order.getStatus(), "pay");
        order.setStatus(newStatus);
        order.setPaymentMethod(paymentMethod != null ? paymentMethod : "wallet");
        order.setIdempotencyKey(idempotencyKey);
        updateById(order);

        // ============ 6. 事后审计 ============
        String riskLevel = order.getTotalAmount().compareTo(new BigDecimal("5000")) >= 0 ? "MEDIUM" : "LOW";
        securityService.auditPaySuccess(userId, orderId, order.getTotalAmount(),
                balanceBefore, balanceAfter, paymentMethod, riskLevel, req);

        log.info("[支付完成] userId={}, orderId={}, method={}, amount={}, status={}",
                userId, orderId, paymentMethod, order.getTotalAmount(), newStatus);
    }

    @Override
    public PageDTO<OrderVO> sellerOrders(Long sellerId, String status, int pageNo, int pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getSellerId, sellerId)
                .orderByDesc(Order::getCreateTime);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }
        Page<Order> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<OrderVO> list = page.getRecords().stream().map(OrderVO::from).toList();
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, list);
    }
}
