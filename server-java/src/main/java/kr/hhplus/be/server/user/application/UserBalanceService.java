package kr.hhplus.be.server.user.application;

import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserBalanceService {

    private final UserBalanceRepository userBalanceRepository;

    /**
     * 포인트 충전
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 UserBalance
     */
    @Transactional
    public UserBalance charge(Long userId, BigDecimal amount) {
        // TODO: 구현
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }

    /**
     * 잔액 조회
     * @param userId 사용자 ID
     * @return UserBalance
     */
    public UserBalance getBalance(Long userId) {
        // TODO: 구현
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }

    // TODO: use() 메서드 추가
}
