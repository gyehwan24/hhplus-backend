package kr.hhplus.be.server.payment.infrastructure.kafka;

import kr.hhplus.be.server.config.kafka.KafkaConfig;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 데이터 플랫폼 Kafka Consumer
 * 결제 완료 메시지를 수신하여 데이터 플랫폼으로 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformKafkaConsumer {

    private final DataPlatformClient dataPlatformClient;

    @KafkaListener(
        topics = KafkaConfig.PAYMENT_COMPLETED_TOPIC,
        groupId = "data-platform-consumer-group"
    )
    public void consume(ConsumerRecord<String, PaymentCompletedMessage> record) {
        PaymentCompletedMessage message = record.value();

        log.info("[DataPlatformConsumer] 메시지 수신 - reservationId: {}, partition: {}, offset: {}",
            message.reservationId(),
            record.partition(),
            record.offset());

        try {
            dataPlatformClient.sendReservationData(message);
            log.info("[DataPlatformConsumer] 처리 완료 - reservationId: {}", message.reservationId());
        } catch (Exception e) {
            log.error("[DataPlatformConsumer] 처리 실패 - reservationId: {}",
                message.reservationId(), e);
            throw e;
        }
    }
}
