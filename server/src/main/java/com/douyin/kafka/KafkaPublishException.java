package com.douyin.kafka;

/** Raised when a durable Kafka publication cannot be recorded. */
public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
