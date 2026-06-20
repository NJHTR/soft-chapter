package com.douyin.service;

import com.douyin.common.PageDTO;
import com.douyin.vo.OrderVO;
import jakarta.servlet.http.HttpServletRequest;

public interface OrderService {

    /** 下单(从购物车批量或单品直购) */
    OrderVO place(Long userId, Long goodsId, Integer quantity,
                  String receiverName, String receiverPhone, String receiverAddress, String remark);

    /** 从购物车批量下单 */
    java.util.List<OrderVO> placeFromCart(Long userId, java.util.List<Long> cartIds,
                                          String receiverName, String receiverPhone,
                                          String receiverAddress, String remark);

    /** 买家查看订单列表 */
    PageDTO<OrderVO> myOrders(Long userId, String status, int pageNo, int pageSize);

    /** 取消订单 */
    void cancel(Long userId, Long orderId);

    /** 卖家发货 */
    void ship(Long sellerId, Long orderId);

    /** 确认收货 */
    void confirmReceive(Long userId, Long orderId);

    /** 卖家查看订单(自己商品的订单) */
    PageDTO<OrderVO> sellerOrders(Long sellerId, String status, int pageNo, int pageSize);

    /** 买家付款(含安全校验+幂等+状态机+策略+重试) */
    void pay(Long userId, Long orderId, String paymentMethod,
             String idempotencyKey, HttpServletRequest req);
}
