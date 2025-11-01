package kr.hhplus.be.server;

import kr.hhplus.be.server.common.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Spring Boot Application Context 로딩 테스트
 * Testcontainers를 이용한 통합 테스트 환경에서 실행
 */
class ServerApplicationTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
		// Spring Boot 애플리케이션 컨텍스트가 정상적으로 로드되는지 확인
		// BaseIntegrationTest를 상속하여 MySQL Testcontainer 환경에서 테스트
	}

}
