package kr.hhplus.be.server.common;

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

/**
 * 통합 테스트 기본 클래스
 * - Testcontainers를 사용한 실제 MySQL 환경
 * - 모든 통합 테스트는 이 클래스를 상속
 */
@SpringBootTest(classes = ServerApplication.class)
@Testcontainers
@ActiveProfiles("test")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server")
@EntityScan(basePackages = "kr.hhplus.be.server")
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(false);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
