package kr.hhplus.be.server.payment.infrastructure.kafka;

import kr.hhplus.be.server.config.kafka.KafkaConfig;
import kr.hhplus.be.server.config.kafka.KafkaRetryMessage;
import kr.hhplus.be.server.config.kafka.KafkaRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 결제 완료 메시지 Kafka Producer
 * 트랜잭션 커밋 후 데이터 플랫폼으로 예약 정보 발행
 * - 발행 실패 시 재시도 큐에 등록
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private static final long SYNC_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate;
    private final KafkaRetryService kafkaRetryService;

    /**
     * 결제 완료 메시지를 Kafka로 비동기 발행
     * 발행 실패 시 재시도 큐에 등록
     *
     * @param message 결제 완료 메시지
     */
    public void send(PaymentCompletedMessage message) {
        String key = String.valueOf(message.reservationId());

        CompletableFuture<SendResult<String, PaymentCompletedMessage>> future =
            kafkaTemplate.send(KafkaConfig.PAYMENT_COMPLETED_TOPIC, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KafkaProducer] 메시지 발행 실패 - paymentId: {}, reservationId: {}",
                    message.paymentId(), message.reservationId(), ex);
                // 재시도 큐에 등록
                kafkaRetryService.enqueue(KafkaRetryMessage.of(message));
            } else {
                log.info("[KafkaProducer] 메시지 발행 성공 - topic: {}, partition: {}, offset: {}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * 결제 완료 메시지를 Kafka로 동기 발행 (스케줄러용)
     * 발행 결과를 블로킹하여 대기
     *
     * @param message 결제 완료 메시지
     * @throws ExecutionException   발행 실패 시
     * @throws InterruptedException 스레드 인터럽트 시
     * @throws TimeoutException     타임아웃 시
     */
    public void sendSync(PaymentCompletedMessage message)
        throws ExecutionException, InterruptedException, TimeoutException {
        String key = String.valueOf(message.reservationId());

        SendResult<String, PaymentCompletedMessage> result = kafkaTemplate
            .send(KafkaConfig.PAYMENT_COMPLETED_TOPIC, key, message)
            .get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("[KafkaProducer] 동기 발행 성공 - topic: {}, partition: {}, offset: {}",
            result.getRecordMetadata().topic(),
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset());
    }
}
