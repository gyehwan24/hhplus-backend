package kr.hhplus.be.server.user.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.user.domain.UserBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * UserBalance JPA Repository
 * - Spring Data JPA 인터페이스
 * - Infrastructure 계층에 위치
 */
public interface UserBalanceJpaRepository extends JpaRepository<UserBalance, Long> {

    Optional<UserBalance> findByUserId(Long userId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT ub FROM UserBalance ub WHERE ub.userId = :userId")
    Optional<UserBalance> findByUserIdWithLock(@Param("userId") Long userId);
}
