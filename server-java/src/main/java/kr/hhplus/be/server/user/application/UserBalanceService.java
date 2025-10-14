package kr.hhplus.be.server.user.application;

import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

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

        UserBalance userBalance = userBalanceRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        userBalance.charge(amount);

        return userBalanceRepository.save(userBalance);
    }

    /**
     * 잔액 조회
     * @param userId 사용자 ID
     * @return UserBalance
     */
    public UserBalance getBalance(Long userId) {
        UserBalance userBalance = userBalanceRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return userBalance;
    }

    // TODO: use() 메서드 추가
}
