package kr.hhplus.be.server.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 캐시 설정
 *
 * 캐시 적용 시나리오:
 * 1. 콘서트 목록 조회 (5분)
 * 2. 콘서트 상세 조회 (10분)
 * 3. 콘서트 스케줄 조회 (3분)
 * 4. 좌석 상태 조회 (10초)
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info(">>> CacheConfig: Creating RedisCacheManager with connectionFactory: {}",
                connectionFactory.getClass().getName());

        // JDK 직렬화 사용 (Serializable 구현 필요)
        JdkSerializationRedisSerializer jdkSerializer = new JdkSerializationRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jdkSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 시나리오 1: 콘서트 목록 (5분)
        cacheConfigurations.put("cache:concert:list",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 시나리오 2: 콘서트 상세 (10분)
        cacheConfigurations.put("cache:concert:detail",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 시나리오 3: 콘서트 스케줄 (3분)
        cacheConfigurations.put("cache:schedule:list",
                defaultConfig.entryTtl(Duration.ofMinutes(3)));

        // 시나리오 4: 좌석 상태 (10초)
        cacheConfigurations.put("cache:seat:available",
                defaultConfig.entryTtl(Duration.ofSeconds(10)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();

        log.info(">>> RedisCacheManager created successfully with caches: {}", cacheConfigurations.keySet());
        return cacheManager;
    }

    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.error(">>> Cache GET Error - cache: {}, key: {}, error: {}",
                    cache.getName(), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.error(">>> Cache PUT Error - cache: {}, key: {}, error: {}",
                    cache.getName(), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error(">>> Cache EVICT Error - cache: {}, key: {}, error: {}",
                    cache.getName(), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error(">>> Cache CLEAR Error - cache: {}, error: {}",
                    cache.getName(), exception.getMessage(), exception);
            }
        };
    }
}
