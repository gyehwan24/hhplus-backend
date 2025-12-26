package kr.hhplus.be.server.config.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 처리 설정
 * - @Async 어노테이션 활성화
 * - Spring 기본 ThreadPool 사용
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
