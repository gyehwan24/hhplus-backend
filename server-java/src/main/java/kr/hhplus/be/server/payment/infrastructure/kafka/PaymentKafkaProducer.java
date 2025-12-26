package kr.hhplus.be.server.payment.infrastructure.kafka;

import kr.hhplus.be.server.config.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 결제 완료 메시지 Kafka Producer
 * 트랜잭션 커밋 후 데이터 플랫폼으로 예약 정보 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate;

    /**
     * 결제 완료 메시지를 Kafka로 발행
     *
     * @param message 결제 완료 메시지
     */
    public void send(PaymentCompletedMessage message) {
        String key = String.valueOf(message.reservationId());

        CompletableFuture<SendResult<String, PaymentCompletedMessage>> future =
            kafkaTemplate.send(KafkaConfig.PAYMENT_COMPLETED_TOPIC, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KafkaProducer] 메시지 발행 실패 - reservationId: {}",
                    message.reservationId(), ex);
            } else {
                log.info("[KafkaProducer] 메시지 발행 성공 - topic: {}, partition: {}, offset: {}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
