package kr.hhplus.be.server.config.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 설정
 *
 * Redis 기반 분산락 구현을 위한 RedissonClient 설정
 *
 * Redisson 장점:
 * - 자동 Watchdog: 락 자동 갱신으로 긴 작업 중 락 해제 방지
 * - Reentrant Lock: 같은 스레드에서 재진입 가능
 * - Pub/Sub 기반: 폴링 대신 이벤트 기반으로 효율적
 * - 프로덕션 검증: 안정성이 검증된 구현
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    /**
     * RedissonClient Bean 등록
     *
     * Single Server 모드로 Redis에 연결합니다.
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(100)     // 커넥션 풀 크기 (50 → 100)
                .setConnectionMinimumIdleSize(20)  // 최소 유휴 커넥션 (10 → 20)
                .setIdleConnectionTimeout(30000)   // 유휴 커넥션 타임아웃 (30초)
                .setTimeout(5000)               // 명령 타임아웃 (5초)
                .setRetryAttempts(3)            // 재시도 횟수
                .setRetryInterval(1500);        // 재시도 간격 (ms)

        return Redisson.create(config);
    }
}
