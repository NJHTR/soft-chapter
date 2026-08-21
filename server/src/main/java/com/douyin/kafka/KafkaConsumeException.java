package com.douyin.kafka;

/**
 * 消费失败信号：向上抛出后由 kafkaListenerContainerFactory 的
 * DefaultErrorHandler 进行指数退避重试，耗尽后进入 DLQ。
 */
public class KafkaConsumeException extends RuntimeException {

    private final String group;

    public KafkaConsumeException(String group, Throwable cause) {
        super("kafka consume failed: " + group, cause);
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}