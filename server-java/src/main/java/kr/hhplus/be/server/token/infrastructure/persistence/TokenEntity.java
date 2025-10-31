package kr.hhplus.be.server.token.infrastructure.persistence;

import jakarta.persistence.*;
import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Token JPA 엔티티 (Infrastructure 전용)
 * - 순수 도메인 모델과 분리
 * - ORM 매핑 책임만 담당
 * - package-private (외부 노출 방지)
 */
@Entity
@Table(name = "tokens", indexes = {
    @Index(name = "idx_token_value", columnList = "tokenValue"),
    @Index(name = "idx_user_id_status", columnList = "userId, status"),
    @Index(name = "idx_status_created_at", columnList = "status, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
class TokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String tokenValue;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime activatedAt;

    private LocalDateTime expiresAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Domain → Entity 변환 (Infrastructure 책임)
     * @param domain 순수 도메인 객체
     * @return JPA 엔티티
     */
    public static TokenEntity from(Token domain) {
        TokenEntity entity = new TokenEntity();
        entity.tokenValue = domain.getTokenValue();
        entity.userId = domain.getUserId();
        entity.status = domain.getStatus();
        entity.createdAt = domain.getCreatedAt();
        entity.activatedAt = domain.getActivatedAt();
        entity.expiresAt = domain.getExpiresAt();
        return entity;
    }

    /**
     * Entity → Domain 변환
     * Reconstitute Pattern: 저장된 데이터를 도메인 객체로 재구성
     * @return 순수 도메인 객체
     */
    public Token toDomain() {
        return Token.reconstitute(
            this.id,
            this.tokenValue,
            this.userId,
            this.status,
            this.createdAt,
            this.activatedAt,
            this.expiresAt
        );
    }
}
