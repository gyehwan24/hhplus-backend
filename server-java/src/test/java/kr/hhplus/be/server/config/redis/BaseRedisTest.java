package kr.hhplus.be.server.config.redis;

import com.redis.testcontainers.RedisContainer;
import kr.hhplus.be.server.ServerApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis + MySQL Testcontainers 기반 테스트
 * Redis와 MySQL을 사용하는 모든 테스트의 베이스 클래스입니다.
 * Docker를 통해 Redis 7과 MySQL 8 컨테이너를 자동으로 시작/종료합니다.
 */
@SpringBootTest(classes = ServerApplication.class)
@Testcontainers
@ActiveProfiles("test")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server")
@EntityScan(basePackages = "kr.hhplus.be.server")
public abstract class BaseRedisTest {

    /**
     * MySQL 8 컨테이너
     */
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    /**
     * Redis 7 컨테이너
     * - RedisContainer: Testcontainers 공식 Redis 모듈
     * - redis:7-alpine: 경량 Redis 7 이미지
     * - 자동으로 포트 6379 노출 및 설정
     */
    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    /**
     * MySQL과 Redis 컨테이너 포트를 동적으로 스프링에 설정
     * Testcontainers는 랜덤 포트를 할당하므로,
     * application-test.yml의 고정 포트를 동적 포트로 오버라이드합니다.
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // MySQL 설정
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // Redis 설정 (RedisContainer의 전용 메서드 사용)
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
}
