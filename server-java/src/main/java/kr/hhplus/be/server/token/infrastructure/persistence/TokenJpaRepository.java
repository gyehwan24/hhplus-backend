package kr.hhplus.be.server.token.infrastructure.persistence;

import kr.hhplus.be.server.token.domain.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Token JPA Repository
 * Spring Data JPA 기반 데이터 접근 계층
 */
interface TokenJpaRepository extends JpaRepository<TokenEntity, Long> {

    /**
     * 토큰 값으로 조회
     */
    Optional<TokenEntity> findByTokenValue(String tokenValue);

    /**
     * 사용자 ID로 활성 토큰 조회
     */
    @Query("SELECT t FROM TokenEntity t WHERE t.userId = :userId AND t.status = 'ACTIVE'")
    Optional<TokenEntity> findActiveTokenByUserId(@Param("userId") Long userId);

    /**
     * 특정 상태의 토큰 목록 조회 (생성 시간 오름차순)
     */
    List<TokenEntity> findByStatusOrderByCreatedAtAsc(TokenStatus status);

    /**
     * 특정 상태의 토큰 개수 조회
     */
    long countByStatus(TokenStatus status);
}
