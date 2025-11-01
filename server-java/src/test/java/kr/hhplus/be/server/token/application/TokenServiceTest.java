package kr.hhplus.be.server.token.application;

import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.token.domain.repository.TokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TokenService 테스트
 * - Mockito를 사용한 서비스 레이어 테스트
 * - Repository 동작은 Mock으로 대체
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @InjectMocks
    private TokenService tokenService;

    @Test
    @DisplayName("토큰 발급 성공 - 신규 사용자")
    void issueToken_success_newUser() {
        // given
        Long userId = 1L;
        when(tokenRepository.findActiveTokenByUserId(userId))
            .thenReturn(Optional.empty());
        when(tokenRepository.save(any(Token.class)))
            .thenAnswer(invocation -> {
                Token token = invocation.getArgument(0);
                return Token.reconstitute(
                    1L,
                    token.getTokenValue(),
                    token.getUserId(),
                    token.getStatus(),
                    token.getCreatedAt(),
                    token.getActivatedAt(),
                    token.getExpiresAt()
                );
            });

        // when
        Token token = tokenService.issueToken(userId);

        // then
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getStatus()).isEqualTo(TokenStatus.WAITING);
        assertThat(token.getTokenValue()).isNotNull();
        verify(tokenRepository).findActiveTokenByUserId(userId);
        verify(tokenRepository).save(any(Token.class));
    }

    @Test
    @DisplayName("토큰 발급 실패 - 이미 활성화된 토큰 존재")
    void issueToken_fail_alreadyActive() {
        // given
        Long userId = 1L;
        Token activeToken = Token.issue(userId).activate();
        when(tokenRepository.findActiveTokenByUserId(userId))
            .thenReturn(Optional.of(activeToken));

        // when & then
        assertThatThrownBy(() -> tokenService.issueToken(userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 활성화된 토큰이 존재합니다.");
        verify(tokenRepository).findActiveTokenByUserId(userId);
        verify(tokenRepository, never()).save(any(Token.class));
    }

    @Test
    @DisplayName("대기열 순번 조회 성공 - 3번째 대기")
    void getQueuePosition_success() {
        // given
        Token myToken = Token.reconstitute(
            3L,
            "my-token",
            1L,
            TokenStatus.WAITING,
            LocalDateTime.now().minusMinutes(30),
            null,
            null
        );
        when(tokenRepository.findByTokenValue("my-token"))
            .thenReturn(Optional.of(myToken));
        when(tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.WAITING))
            .thenReturn(List.of(
                Token.reconstitute(1L, "token-1", 10L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(50), null, null),
                Token.reconstitute(2L, "token-2", 11L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(40), null, null),
                myToken,
                Token.reconstitute(4L, "token-4", 12L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(20), null, null)
            ));

        // when
        long position = tokenService.getQueuePosition("my-token");

        // then
        assertThat(position).isEqualTo(3L);
        verify(tokenRepository).findByTokenValue("my-token");
        verify(tokenRepository).findByStatusOrderByCreatedAt(TokenStatus.WAITING);
    }

    @Test
    @DisplayName("대기열 순번 조회 실패 - 토큰 없음")
    void getQueuePosition_fail_notFound() {
        // given
        when(tokenRepository.findByTokenValue("unknown-token"))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.getQueuePosition("unknown-token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("토큰을 찾을 수 없습니다.");
        verify(tokenRepository).findByTokenValue("unknown-token");
    }

    @Test
    @DisplayName("대기열 순번 조회 - 대기 상태가 아니면 0 반환")
    void getQueuePosition_notWaiting_returnsZero() {
        // given
        Token activeToken = Token.issue(1L).activate();
        when(tokenRepository.findByTokenValue("active-token"))
            .thenReturn(Optional.of(activeToken));

        // when
        long position = tokenService.getQueuePosition("active-token");

        // then
        assertThat(position).isZero();
        verify(tokenRepository).findByTokenValue("active-token");
    }

    @Test
    @DisplayName("대기 토큰 활성화 성공 - 3개 슬롯, 5개 대기 → 3개 활성화")
    void activateWaitingTokens_success() {
        // given
        when(tokenRepository.countByStatus(TokenStatus.ACTIVE))
            .thenReturn(97L); // 100 - 97 = 3개 슬롯

        List<Token> waitingTokens = List.of(
            Token.reconstitute(1L, "token-1", 10L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(50), null, null),
            Token.reconstitute(2L, "token-2", 11L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(40), null, null),
            Token.reconstitute(3L, "token-3", 12L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(30), null, null),
            Token.reconstitute(4L, "token-4", 13L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(20), null, null),
            Token.reconstitute(5L, "token-5", 14L, TokenStatus.WAITING, LocalDateTime.now().minusMinutes(10), null, null)
        );
        when(tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.WAITING))
            .thenReturn(waitingTokens);
        when(tokenRepository.save(any(Token.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        int activatedCount = tokenService.activateWaitingTokens();

        // then
        assertThat(activatedCount).isEqualTo(3);
        verify(tokenRepository).countByStatus(TokenStatus.ACTIVE);
        verify(tokenRepository).findByStatusOrderByCreatedAt(TokenStatus.WAITING);
        verify(tokenRepository, times(3)).save(any(Token.class));
    }

    @Test
    @DisplayName("대기 토큰 활성화 - 슬롯 없음")
    void activateWaitingTokens_noSlots() {
        // given
        when(tokenRepository.countByStatus(TokenStatus.ACTIVE))
            .thenReturn(100L); // 슬롯 없음

        // when
        int activatedCount = tokenService.activateWaitingTokens();

        // then
        assertThat(activatedCount).isZero();
        verify(tokenRepository).countByStatus(TokenStatus.ACTIVE);
        verify(tokenRepository, never()).findByStatusOrderByCreatedAt(any());
        verify(tokenRepository, never()).save(any(Token.class));
    }

    @Test
    @DisplayName("대기 토큰 활성화 - 대기자 없음")
    void activateWaitingTokens_noWaiting() {
        // given
        when(tokenRepository.countByStatus(TokenStatus.ACTIVE))
            .thenReturn(50L); // 50개 슬롯
        when(tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.WAITING))
            .thenReturn(List.of()); // 대기자 없음

        // when
        int activatedCount = tokenService.activateWaitingTokens();

        // then
        assertThat(activatedCount).isZero();
        verify(tokenRepository).countByStatus(TokenStatus.ACTIVE);
        verify(tokenRepository).findByStatusOrderByCreatedAt(TokenStatus.WAITING);
        verify(tokenRepository, never()).save(any(Token.class));
    }

    @Test
    @DisplayName("만료된 토큰 정리 성공")
    void expireExpiredTokens_success() {
        // given
        Token expiredToken1 = Token.reconstitute(
            1L,
            "token-1",
            10L,
            TokenStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(30),
            LocalDateTime.now().minusMinutes(30),
            LocalDateTime.now().minusMinutes(5) // 5분 전 만료
        );
        Token expiredToken2 = Token.reconstitute(
            2L,
            "token-2",
            11L,
            TokenStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(25),
            LocalDateTime.now().minusMinutes(25),
            LocalDateTime.now().minusMinutes(1) // 1분 전 만료
        );

        when(tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.ACTIVE))
            .thenReturn(List.of(expiredToken1, expiredToken2));
        when(tokenRepository.save(any(Token.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        int expiredCount = tokenService.expireExpiredTokens();

        // then
        assertThat(expiredCount).isEqualTo(2);
        verify(tokenRepository).findByStatusOrderByCreatedAt(TokenStatus.ACTIVE);
        verify(tokenRepository, times(2)).save(any(Token.class));
    }

    @Test
    @DisplayName("만료된 토큰 정리 - 만료된 토큰 없음")
    void expireExpiredTokens_nothingToExpire() {
        // given
        Token activeToken = Token.reconstitute(
            1L,
            "token-1",
            10L,
            TokenStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(5),
            LocalDateTime.now().minusMinutes(5),
            LocalDateTime.now().plusMinutes(5) // 아직 유효
        );

        when(tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.ACTIVE))
            .thenReturn(List.of(activeToken));

        // when
        int expiredCount = tokenService.expireExpiredTokens();

        // then
        assertThat(expiredCount).isZero();
        verify(tokenRepository).findByStatusOrderByCreatedAt(TokenStatus.ACTIVE);
        verify(tokenRepository, never()).save(any(Token.class));
    }

    @Test
    @DisplayName("토큰 검증 성공 - 활성 토큰")
    void validateToken_success() {
        // given
        Token activeToken = Token.issue(1L).activate();
        when(tokenRepository.findByTokenValue("active-token"))
            .thenReturn(Optional.of(activeToken));

        // when & then
        assertThatCode(() -> tokenService.validateToken("active-token"))
            .doesNotThrowAnyException();
        verify(tokenRepository).findByTokenValue("active-token");
    }

    @Test
    @DisplayName("토큰 검증 실패 - 토큰 없음")
    void validateToken_fail_notFound() {
        // given
        when(tokenRepository.findByTokenValue("unknown-token"))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.validateToken("unknown-token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("유효하지 않은 토큰입니다.");
        verify(tokenRepository).findByTokenValue("unknown-token");
    }

    @Test
    @DisplayName("토큰 검증 실패 - 대기 상태 토큰")
    void validateToken_fail_waiting() {
        // given
        Token waitingToken = Token.issue(1L);
        when(tokenRepository.findByTokenValue("waiting-token"))
            .thenReturn(Optional.of(waitingToken));

        // when & then
        assertThatThrownBy(() -> tokenService.validateToken("waiting-token"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("활성 상태가 아닌 토큰입니다");
        verify(tokenRepository).findByTokenValue("waiting-token");
    }

    @Test
    @DisplayName("토큰 검증 실패 - 만료된 토큰")
    void validateToken_fail_expired() {
        // given
        Token expiredToken = Token.reconstitute(
            1L,
            "expired-token",
            1L,
            TokenStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(30),
            LocalDateTime.now().minusMinutes(30),
            LocalDateTime.now().minusMinutes(5) // 5분 전 만료
        );
        when(tokenRepository.findByTokenValue("expired-token"))
            .thenReturn(Optional.of(expiredToken));

        // when & then
        assertThatThrownBy(() -> tokenService.validateToken("expired-token"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("활성 상태가 아닌 토큰입니다");
        verify(tokenRepository).findByTokenValue("expired-token");
    }
}
