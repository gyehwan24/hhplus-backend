package kr.hhplus.be.server.token.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Token 도메인 단위 테스트
 * - Spring 없이 순수 Java로 테스트
 * - 도메인 로직의 정합성 검증
 */
class TokenTest {

    @Test
    @DisplayName("토큰 발급 성공 - WAITING 상태로 생성")
    void issue_success() {
        // given
        Long userId = 1L;

        // when
        Token token = Token.issue(userId);

        // then
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getStatus()).isEqualTo(TokenStatus.WAITING);
        assertThat(token.getTokenValue()).isNotNull();
        assertThat(token.getCreatedAt()).isNotNull();
        assertThat(token.getActivatedAt()).isNull();
        assertThat(token.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("토큰 발급 실패 - userId가 null")
    void issue_fail_nullUserId() {
        // given
        Long userId = null;

        // when & then
        assertThatThrownBy(() -> Token.issue(userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    @DisplayName("토큰 활성화 성공 - WAITING → ACTIVE")
    void activate_success() {
        // given
        Token waitingToken = Token.issue(1L);

        // when
        Token activeToken = waitingToken.activate();

        // then
        assertThat(activeToken.getStatus()).isEqualTo(TokenStatus.ACTIVE);
        assertThat(activeToken.getActivatedAt()).isNotNull();
        assertThat(activeToken.getExpiresAt()).isNotNull();
        assertThat(activeToken.getExpiresAt()).isAfter(activeToken.getActivatedAt());

        // 원본은 변경되지 않음 (Immutable)
        assertThat(waitingToken.getStatus()).isEqualTo(TokenStatus.WAITING);
    }

    @Test
    @DisplayName("토큰 활성화 실패 - WAITING 상태가 아님")
    void activate_fail_notWaiting() {
        // given
        Token activeToken = Token.issue(1L).activate();

        // when & then
        assertThatThrownBy(() -> activeToken.activate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("대기 중인 토큰만 활성화할 수 있습니다.");
    }

    @Test
    @DisplayName("토큰 만료 처리 성공")
    void expire_success() {
        // given
        Token activeToken = Token.issue(1L).activate();

        // when
        Token expiredToken = activeToken.expire();

        // then
        assertThat(expiredToken.getStatus()).isEqualTo(TokenStatus.EXPIRED);

        // 원본은 변경되지 않음 (Immutable)
        assertThat(activeToken.getStatus()).isEqualTo(TokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("토큰 만료 여부 확인 - EXPIRED 상태")
    void isExpired_expiredStatus() {
        // given
        Token token = Token.issue(1L).activate().expire();

        // when & then
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    @DisplayName("토큰 만료 여부 확인 - expiresAt 시간 초과")
    void isExpired_timeExpired() {
        // given
        // reconstitute를 사용하여 10분 전에 만료된 토큰 생성
        Token token = Token.reconstitute(
            1L,
            "test-token",
            1L,
            TokenStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(20),
            LocalDateTime.now().minusMinutes(20),
            LocalDateTime.now().minusMinutes(10) // 10분 전 만료
        );

        // when & then
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    @DisplayName("토큰 만료 여부 확인 - 아직 만료 안됨")
    void isExpired_notExpired() {
        // given
        Token token = Token.issue(1L).activate();

        // when & then
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("토큰 활성 상태 확인 - ACTIVE이고 만료 안됨")
    void isActive_activeAndNotExpired() {
        // given
        Token token = Token.issue(1L).activate();

        // when & then
        assertThat(token.isActive()).isTrue();
    }

    @Test
    @DisplayName("토큰 활성 상태 확인 - ACTIVE이지만 만료됨")
    void isActive_activeButExpired() {
        // given
        Token token = Token.reconstitute(
            1L,
            "test-token",
            1L,
            TokenStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(20),
            LocalDateTime.now().minusMinutes(20),
            LocalDateTime.now().minusMinutes(10) // 10분 전 만료
        );

        // when & then
        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("토큰 활성 상태 확인 - WAITING 상태")
    void isActive_waiting() {
        // given
        Token token = Token.issue(1L);

        // when & then
        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("reconstitute로 토큰 재구성 성공")
    void reconstitute_success() {
        // given
        Long id = 1L;
        String tokenValue = "test-token-uuid";
        Long userId = 100L;
        TokenStatus status = TokenStatus.ACTIVE;
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
        LocalDateTime activatedAt = LocalDateTime.now().minusMinutes(20);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        // when
        Token token = Token.reconstitute(
            id, tokenValue, userId, status,
            createdAt, activatedAt, expiresAt
        );

        // then
        assertThat(token.getId()).isEqualTo(id);
        assertThat(token.getTokenValue()).isEqualTo(tokenValue);
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getStatus()).isEqualTo(status);
        assertThat(token.getCreatedAt()).isEqualTo(createdAt);
        assertThat(token.getActivatedAt()).isEqualTo(activatedAt);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
    }
}
