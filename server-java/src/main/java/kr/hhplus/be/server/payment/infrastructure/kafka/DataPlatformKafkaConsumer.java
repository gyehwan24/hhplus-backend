package kr.hhplus.be.server.payment.infrastructure.kafka;

import kr.hhplus.be.server.config.kafka.KafkaConfig;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 데이터 플랫폼 Kafka Consumer
 * 결제 완료 메시지를 수신하여 데이터 플랫폼으로 전송
 * - 수동 커밋으로 메시지 유실 방지
 * - Redis 기반 멱등성 체크로 중복 처리 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformKafkaConsumer {

    private static final String PROCESSED_KEY_PREFIX = "kafka:processed:";
    private static final Duration PROCESSED_KEY_TTL = Duration.ofHours(24);

    private final DataPlatformClient dataPlatformClient;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
        topics = KafkaConfig.PAYMENT_COMPLETED_TOPIC,
        groupId = "data-platform-consumer-group"
    )
    public void consume(ConsumerRecord<String, PaymentCompletedMessage> record,
                        Acknowledgment ack) {
        PaymentCompletedMessage message = record.value();
        String eventKey = PROCESSED_KEY_PREFIX + message.paymentId();

        log.info("[DataPlatformConsumer] 메시지 수신 - paymentId: {}, partition: {}, offset: {}",
            message.paymentId(),
            record.partition(),
            record.offset());

        // 멱등성 체크: 이미 처리된 메시지인지 확인
        if (Boolean.TRUE.equals(redisTemplate.hasKey(eventKey))) {
            log.info("[DataPlatformConsumer] 이미 처리된 메시지 - paymentId: {}", message.paymentId());
            ack.acknowledge();  // 중복 메시지도 커밋하여 무한 루프 방지
            return;
        }

        try {
            dataPlatformClient.sendReservationData(message);

            // 처리 완료 후 Redis에 기록 (24시간 TTL)
            redisTemplate.opsForValue().set(eventKey, "1", PROCESSED_KEY_TTL);

            log.info("[DataPlatformConsumer] 처리 완료 - paymentId: {}", message.paymentId());
            ack.acknowledge();  // 처리 성공 시에만 커밋
        } catch (Exception e) {
            log.error("[DataPlatformConsumer] 처리 실패 - paymentId: {}",
                message.paymentId(), e);
            // 커밋하지 않음 → Kafka가 자동으로 재시도
            throw e;
        }
    }
}
