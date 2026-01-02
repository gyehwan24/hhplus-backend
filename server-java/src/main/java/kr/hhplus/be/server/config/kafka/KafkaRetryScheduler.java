package kr.hhplus.be.server.config.kafka;

import kr.hhplus.be.server.payment.infrastructure.kafka.PaymentKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Kafka 재시도 스케줄러
 * 재시도 큐에 있는 메시지를 주기적으로 재발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaRetryScheduler {

    private final KafkaRetryService kafkaRetryService;
    private final PaymentKafkaProducer paymentKafkaProducer;

    /**
     * 10초마다 재시도 큐 확인 및 재발행
     */
    @Scheduled(fixedDelay = 10000)
    public void processRetryQueue() {
        Long queueSize = kafkaRetryService.getQueueSize();
        if (queueSize == null || queueSize == 0) {
            return;
        }

        log.info("[KafkaRetryScheduler] 재시도 큐 처리 시작 - queueSize: {}", queueSize);

        int processedCount = 0;
        int maxProcessPerRun = 100;  // 한 번에 최대 100개 처리

        while (processedCount < maxProcessPerRun) {
            KafkaRetryMessage retryMessage = kafkaRetryService.dequeue();
            if (retryMessage == null) {
                break;
            }

            processRetryMessage(retryMessage);
            processedCount++;
        }

        log.info("[KafkaRetryScheduler] 재시도 큐 처리 완료 - processedCount: {}", processedCount);
    }

    private void processRetryMessage(KafkaRetryMessage retryMessage) {
        // 최대 재시도 횟수 초과 체크
        if (kafkaRetryService.isMaxRetryExceeded(retryMessage)) {
            log.error("[KafkaRetryScheduler] 최대 재시도 횟수 초과 - paymentId: {}, retryCount: {}. 메시지 폐기됨.",
                retryMessage.message().paymentId(),
                retryMessage.retryCount());
            // TODO: 알림 발송 (Slack, 이메일 등)
            return;
        }

        try {
            // 동기 발행 시도
            paymentKafkaProducer.sendSync(retryMessage.message());
            log.info("[KafkaRetryScheduler] 재시도 성공 - paymentId: {}, retryCount: {}",
                retryMessage.message().paymentId(),
                retryMessage.retryCount());
        } catch (Exception e) {
            log.error("[KafkaRetryScheduler] 재시도 실패 - paymentId: {}, retryCount: {}",
                retryMessage.message().paymentId(),
                retryMessage.retryCount(), e);
            // 재시도 횟수 증가 후 큐에 다시 등록
            kafkaRetryService.requeueWithIncrement(retryMessage);
        }
    }
}
