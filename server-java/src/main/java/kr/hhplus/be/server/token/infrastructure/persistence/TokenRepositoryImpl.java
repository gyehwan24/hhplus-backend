package kr.hhplus.be.server.token.infrastructure.persistence;

import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.token.domain.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Token Repository 구현체 (Adapter)
 * - PaymentRepository 인터페이스(Port)의 구현체로 Adapter 역할 수행
 * - Domain ↔ JPA Entity 변환 책임
 * - 기술적 세부사항 격리
 */
@Repository
@RequiredArgsConstructor
public class TokenRepositoryImpl implements TokenRepository {

    private final TokenJpaRepository jpaRepository;

    @Override
    public Token save(Token token) {
        TokenEntity entity = TokenEntity.from(token);
        TokenEntity saved = jpaRepository.save(entity);

        // 불변성 준수: 새로운 인스턴스 반환 (기존 객체 수정 없음)
        return saved.toDomain();
    }

    @Override
    public Optional<Token> findById(Long id) {
        return jpaRepository.findById(id)
            .map(TokenEntity::toDomain);
    }

    @Override
    public Optional<Token> findByTokenValue(String tokenValue) {
        return jpaRepository.findByTokenValue(tokenValue)
            .map(TokenEntity::toDomain);
    }

    @Override
    public Optional<Token> findActiveTokenByUserId(Long userId) {
        return jpaRepository.findActiveTokenByUserId(userId)
            .map(TokenEntity::toDomain);
    }

    @Override
    public List<Token> findByStatusOrderByCreatedAt(TokenStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(status).stream()
            .map(TokenEntity::toDomain)
            .toList();
    }

    @Override
    public long countByStatus(TokenStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
