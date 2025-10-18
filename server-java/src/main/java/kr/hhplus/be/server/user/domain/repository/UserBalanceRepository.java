package kr.hhplus.be.server.user.domain.repository;

import kr.hhplus.be.server.user.domain.UserBalance;

import java.util.Optional;

/**
 * UserBalance Repository 인터페이스
 * - 도메인 계층에 위치 (Infrastructure에 의존하지 않음)
 * - DIP(Dependency Inversion Principle) 적용
 */
public interface UserBalanceRepository {

    Optional<UserBalance> findByUserId(Long userId);
    
    UserBalance save(UserBalance userBalance);
    
    Optional<UserBalance> findByUserIdWithLock(Long userId);
}
