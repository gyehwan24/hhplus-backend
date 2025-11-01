package kr.hhplus.be.server.token.domain.repository;

import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;

import java.util.List;
import java.util.Optional;

/**
 * Token Repository 인터페이스 (Port)
 * - Domain Layer가 정의하고, Infrastructure Layer가 구현
 * - Port & Adapter 패턴의 Port 역할
 */
public interface TokenRepository {

    /**
     * 토큰 저장
     * @param token 저장할 토큰
     * @return 저장된 토큰 (ID 할당됨)
     */
    Token save(Token token);

    /**
     * 토큰 ID로 조회
     * @param id 토큰 ID
     * @return 토큰 (Optional)
     */
    Optional<Token> findById(Long id);

    /**
     * 토큰 값으로 조회
     * @param tokenValue 토큰 값 (UUID)
     * @return 토큰 (Optional)
     */
    Optional<Token> findByTokenValue(String tokenValue);

    /**
     * 사용자 ID로 활성 토큰 조회
     * @param userId 사용자 ID
     * @return 활성 토큰 (Optional)
     */
    Optional<Token> findActiveTokenByUserId(Long userId);

    /**
     * 특정 상태의 토큰 목록 조회 (생성 시간 순)
     * @param status 토큰 상태
     * @return 토큰 목록 (생성 시간 오름차순)
     */
    List<Token> findByStatusOrderByCreatedAt(TokenStatus status);

    /**
     * 특정 상태의 토큰 개수 조회
     * @param status 토큰 상태
     * @return 토큰 개수
     */
    long countByStatus(TokenStatus status);
}
