package kr.hhplus.be.server.config.kafka;

import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentCompletedMessage;

import java.time.LocalDateTime;

/**
 * Kafka 재시도 메시지
 * Redis 재시도 큐에 저장되는 메시지 래퍼
 */
public record KafkaRetryMessage(
    PaymentCompletedMessage message,
    int retryCount,
    LocalDateTime createdAt
) {
    public static KafkaRetryMessage of(PaymentCompletedMessage message) {
        return new KafkaRetryMessage(message, 0, LocalDateTime.now());
    }

    public KafkaRetryMessage incrementRetry() {
        return new KafkaRetryMessage(message, retryCount + 1, createdAt);
    }
}
