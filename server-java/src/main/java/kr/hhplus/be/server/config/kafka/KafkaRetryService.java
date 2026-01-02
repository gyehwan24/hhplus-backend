package kr.hhplus.be.server.config.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 재시도 큐 관리 서비스
 * Redis List를 사용하여 발행 실패 메시지를 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaRetryService {

    private static final String RETRY_QUEUE_KEY = "kafka:retry:payment-completed";
    private static final int MAX_RETRY_COUNT = 3;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 재시도 큐에 메시지 등록
     */
    public void enqueue(KafkaRetryMessage retryMessage) {
        try {
            String json = objectMapper.writeValueAsString(retryMessage);
            redisTemplate.opsForList().rightPush(RETRY_QUEUE_KEY, json);
            log.info("[KafkaRetryService] 재시도 큐 등록 - paymentId: {}, retryCount: {}",
                retryMessage.message().paymentId(), retryMessage.retryCount());
        } catch (JsonProcessingException e) {
            log.error("[KafkaRetryService] 재시도 큐 등록 실패 - 직렬화 오류", e);
        }
    }

    /**
     * 재시도 큐에서 메시지 가져오기 (LPOP)
     */
    public KafkaRetryMessage dequeue() {
        String json = redisTemplate.opsForList().leftPop(RETRY_QUEUE_KEY);
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, KafkaRetryMessage.class);
        } catch (JsonProcessingException e) {
            log.error("[KafkaRetryService] 재시도 메시지 역직렬화 실패", e);
            return null;
        }
    }

    /**
     * 최대 재시도 횟수 초과 여부 확인
     */
    public boolean isMaxRetryExceeded(KafkaRetryMessage retryMessage) {
        return retryMessage.retryCount() >= MAX_RETRY_COUNT;
    }

    /**
     * 재시도 횟수 증가 후 큐에 다시 등록
     */
    public void requeueWithIncrement(KafkaRetryMessage retryMessage) {
        KafkaRetryMessage incremented = retryMessage.incrementRetry();
        enqueue(incremented);
    }

    /**
     * 재시도 큐 크기 조회
     */
    public Long getQueueSize() {
        return redisTemplate.opsForList().size(RETRY_QUEUE_KEY);
    }
}
