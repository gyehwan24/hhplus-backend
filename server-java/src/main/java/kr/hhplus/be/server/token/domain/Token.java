package kr.hhplus.be.server.token.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 대기열 토큰 도메인 모델
 * - 사용자의 예약 권한을 관리하는 토큰
 */
public class Token {

    private final Long id;
    private final String tokenValue;       // UUID
    private final Long userId;
    private final TokenStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime activatedAt;  // 활성화 시각
    private final LocalDateTime expiresAt;     // 만료 시각

    /**
     * private 생성자 - 외부에서 직접 생성 불가
     */
    private Token(Long id, String tokenValue, Long userId, TokenStatus status,
                  LocalDateTime createdAt, LocalDateTime activatedAt,
                  LocalDateTime expiresAt) {
        this.id = id;
        this.tokenValue = tokenValue;
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 토큰 발급 (팩토리 메서드)
     * @param userId 사용자 ID
     * @return 대기 상태의 새로운 토큰
     */
    public static Token issue(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        return new Token(
            null,
            UUID.randomUUID().toString(),
            userId,
            TokenStatus.WAITING,
            now,
            null,
            null
        );
    }

    /**
     * 토큰 활성화 (불변 객체 - 새 인스턴스 반환)
     * @return 활성화된 새로운 토큰 인스턴스
     */
    public Token activate() {
        if (this.status != TokenStatus.WAITING) {
            throw new IllegalStateException("대기 중인 토큰만 활성화할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        return new Token(
            this.id,
            this.tokenValue,
            this.userId,
            TokenStatus.ACTIVE,
            this.createdAt,
            now,
            now.plusMinutes(10)  // 활성화 후 10분간 유효
        );
    }

    /**
     * 토큰 만료 (불변 객체 - 새 인스턴스 반환)
     * @return 만료된 새로운 토큰 인스턴스
     */
    public Token expire() {
        return new Token(
            this.id,
            this.tokenValue,
            this.userId,
            TokenStatus.EXPIRED,
            this.createdAt,
            this.activatedAt,
            this.expiresAt
        );
    }

    /**
     * 토큰 만료 여부 확인
     * @return 만료 여부
     */
    public boolean isExpired() {
        if (this.status == TokenStatus.EXPIRED) {
            return true;
        }

        if (this.status == TokenStatus.ACTIVE && this.expiresAt != null) {
            return LocalDateTime.now().isAfter(this.expiresAt);
        }

        return false;
    }

    /**
     * 토큰 활성 상태 확인
     * @return 활성 상태 여부
     */
    public boolean isActive() {
        return this.status == TokenStatus.ACTIVE && !isExpired();
    }

    /**
     * 영속성 계층에서 저장된 데이터를 도메인 객체로 재구성 (Reconstitute Pattern)
     * - 비즈니스 로직 없이 순수하게 상태만 복원
     * - Infrastructure 계층 전용 메서드
     *
     * @param id 저장된 ID
     * @param tokenValue 토큰 값 (UUID)
     * @param userId 사용자 ID
     * @param status 토큰 상태
     * @param createdAt 생성 시각
     * @param activatedAt 활성화 시각
     * @param expiresAt 만료 시각
     * @return 재구성된 도메인 객체
     */
    public static Token reconstitute(Long id, String tokenValue, Long userId,
                                    TokenStatus status, LocalDateTime createdAt,
                                    LocalDateTime activatedAt, LocalDateTime expiresAt) {
        return new Token(id, tokenValue, userId, status, createdAt, activatedAt, expiresAt);
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public Long getUserId() {
        return userId;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
