package com.douyin.service.payment;

/**
 * 支付状态机 — 定义合法状态流转，防止非法状态跳转
 *
 * PENDING → PAID → SHIPPED → RECEIVED
 * PENDING → CANCELLED
 * PAID → REFUNDING → REFUNDED
 */
public enum PaymentStateMachine {

    PENDING("PENDING") {
        @Override
        public PaymentStateMachine pay() { return PAID; }
        @Override
        public PaymentStateMachine cancel() { return CANCELLED; }
    },
    PAID("PAID") {
        @Override
        public PaymentStateMachine ship() { return SHIPPED; }
        @Override
        public PaymentStateMachine startRefund() { return REFUNDING; }
    },
    SHIPPED("SHIPPED") {
        @Override
        public PaymentStateMachine confirmReceive() { return RECEIVED; }
        @Override
        public PaymentStateMachine startRefund() { return REFUNDING; }
    },
    RECEIVED("RECEIVED") {
        @Override
        public PaymentStateMachine startRefund() { return REFUNDING; }
    },
    CANCELLED("CANCELLED") {},
    REFUNDING("REFUNDING") {
        @Override
        public PaymentStateMachine completeRefund() { return REFUNDED; }
    },
    REFUNDED("REFUNDED") {};

    private final String code;

    PaymentStateMachine(String code) { this.code = code; }

    public String getCode() { return code; }

    public static PaymentStateMachine fromCode(String code) {
        for (PaymentStateMachine s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        throw new IllegalStateException("未知支付状态: " + code);
    }

    // 默认实现 — 子类按需覆盖
    public PaymentStateMachine pay() { throw new IllegalStateException("当前状态[" + code + "]不可执行支付"); }
    public PaymentStateMachine cancel() { throw new IllegalStateException("当前状态[" + code + "]不可取消"); }
    public PaymentStateMachine ship() { throw new IllegalStateException("当前状态[" + code + "]不可执行发货"); }
    public PaymentStateMachine confirmReceive() { throw new IllegalStateException("当前状态[" + code + "]不可确认收货"); }
    public PaymentStateMachine startRefund() { throw new IllegalStateException("当前状态[" + code + "]不可发起退款"); }
    public PaymentStateMachine completeRefund() { throw new IllegalStateException("当前状态[" + code + "]不可完成退款"); }

    /**
     * 校验并执行状态流转
     * @return 新状态
     */
    public static String transition(String currentStatus, String action) {
        PaymentStateMachine current = fromCode(currentStatus);
        return switch (action) {
            case "pay" -> current.pay().getCode();
            case "cancel" -> current.cancel().getCode();
            case "ship" -> current.ship().getCode();
            case "confirm_receive" -> current.confirmReceive().getCode();
            case "start_refund" -> current.startRefund().getCode();
            case "complete_refund" -> current.completeRefund().getCode();
            default -> throw new IllegalStateException("未知操作: " + action);
        };
    }
}
