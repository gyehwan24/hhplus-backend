package kr.hhplus.be.server.user.application;

import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserBalanceService 단위 테스트
 * - Mock을 사용한 순수 단위 테스트
 * - Repository를 Mock으로 처리
 */
@ExtendWith(MockitoExtension.class)
class UserBalanceServiceTest {

    @Mock
    private UserBalanceRepository userBalanceRepository;

    @InjectMocks
    private UserBalanceService userBalanceService;

    @Test
    @DisplayName("포인트 충전 성공")
    void charge_정상금액_성공() {

    }

    @Test
    @DisplayName("잔액 조회 성공")
    void getBalance_성공() {

    }

    // TODO: use() 메서드 테스트 추가
}
