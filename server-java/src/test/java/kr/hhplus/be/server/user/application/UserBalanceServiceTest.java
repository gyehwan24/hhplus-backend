package kr.hhplus.be.server.user.application;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;

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
        // given - 테스트 데이터 준비
        Long userId = 1L;
        BigDecimal initialAmount = new BigDecimal("10000");
        BigDecimal chargeAmount = new BigDecimal("20000");
        BigDecimal expectedAmount = new BigDecimal("30000");

        UserBalance userBalance = UserBalance.create(userId, initialAmount);

        when(userBalanceRepository.findByUserId(userId))
            .thenReturn(Optional.of(userBalance));
        when(userBalanceRepository.save(any(UserBalance.class)))
            .thenAnswer(returnsFirstArg());
        // when 
        UserBalance newBalance = userBalanceService.charge(userId, chargeAmount);
        
        // then
        assertThat(newBalance.getCurrentBalance()).isEqualTo(expectedAmount);
        verify(userBalanceRepository).findByUserId(userId);
        verify(userBalanceRepository).save(any(UserBalance.class));
    }

    @Test
    @DisplayName("잔액 조회 성공")
    void getBalance_성공() {
        // given
        Long userId = 1L;
        BigDecimal initialAmount = new BigDecimal("10000");
        UserBalance userBalance = UserBalance.create(userId, initialAmount);

        when(userBalanceRepository.findByUserId(userId))
            .thenReturn(Optional.of(userBalance));
        
        // when
        UserBalance result = userBalanceService.getBalance(userId);

        // then
        assertThat(result.getCurrentBalance()).isEqualTo(userBalance.getCurrentBalance());
        verify(userBalanceRepository).findByUserId(userId);
    }

    // TODO: use() 메서드 테스트 추가
}
