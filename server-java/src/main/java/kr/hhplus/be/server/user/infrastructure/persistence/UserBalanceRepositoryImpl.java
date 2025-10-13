package kr.hhplus.be.server.user.infrastructure.persistence;

import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserBalanceRepository 구현체
 * - Infrastructure 계층에 위치
 * - 도메인 인터페이스를 구현
 * - JPA Repository를 감싸는 Adapter 역할
 */
@Repository
@RequiredArgsConstructor
public class UserBalanceRepositoryImpl implements UserBalanceRepository {

    private final UserBalanceJpaRepository jpaRepository;

    @Override
    public Optional<UserBalance> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public UserBalance save(UserBalance userBalance) {
        return jpaRepository.save(userBalance);
    }

    @Override
    public Optional<UserBalance> findByUserIdWithLock(Long userId) {
        return jpaRepository.findByUserIdWithLock(userId);
    }
}
