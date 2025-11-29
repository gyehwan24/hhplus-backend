package kr.hhplus.be.server.token.application;

import kr.hhplus.be.server.token.application.response.QueueStatusResponse;
import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.token.domain.repository.TokenRepository;
import kr.hhplus.be.server.token.infrastructure.redis.QueueRedisRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * TokenService 단위 테스트
 * - Redis 기반 대기열 + RDB 기반 토큰 관리
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @InjectMocks
    private TokenService tokenService;

    @Test
    @DisplayName("대기열 진입 성공 - Redis 대기열에 추가")
    void issueToken_success() {
        // given
        Long userId = 1L;
        when(tokenRepository.findActiveTokenByUserId(userId))
            .thenReturn(Optional.empty());
        when(queueRedisRepository.addToWaitingQueue(userId))
            .thenReturn(5L); // 5번째 대기

        // when
        long position = tokenService.issueToken(userId);

        // then
        assertThat(position).isEqualTo(5L);
        verify(tokenRepository).findActiveTokenByUserId(userId);
        verify(queueRedisRepository).addToWaitingQueue(userId);
    }

    @Test
    @DisplayName("대기열 진입 실패 - 이미 활성화된 토큰 존재 (RDB)")
    void issueToken_fail_alreadyActiveInRdb() {
        // given
        Long userId = 1L;
        Token activeToken = Token.issueActive(userId, LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findActiveTokenByUserId(userId))
            .thenReturn(Optional.of(activeToken));

        // when & then
        assertThatThrownBy(() -> tokenService.issueToken(userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 활성화된 토큰이 존재합니다.");
        verify(tokenRepository).findActiveTokenByUserId(userId);
        verify(queueRedisRepository, never()).addToWaitingQueue(anyLong());
    }

    @Test
    @DisplayName("대기열 진입 실패 - 이미 대기열에 있음 (Redis)")
    void issueToken_fail_alreadyInQueue() {
        // given
        Long userId = 1L;
        when(tokenRepository.findActiveTokenByUserId(userId))
            .thenReturn(Optional.empty());
        when(queueRedisRepository.addToWaitingQueue(userId))
            .thenThrow(new IllegalStateException("이미 대기열에 있습니다."));

        // when & then
        assertThatThrownBy(() -> tokenService.issueToken(userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 대기열에 있습니다");
    }

    @Test
    @DisplayName("대기열 순번 조회 - Redis ZRANK")
    void getQueuePosition_success() {
        // given
        Long userId = 1L;
        when(queueRedisRepository.getWaitingPosition(userId))
            .thenReturn(3L);

        // when
        long position = tokenService.getQueuePosition(userId);

        // then
        assertThat(position).isEqualTo(3L);
        verify(queueRedisRepository).getWaitingPosition(userId);
    }

    @Test
    @DisplayName("대기열 순번 조회 - 대기열에 없으면 0")
    void getQueuePosition_notInQueue() {
        // given
        Long userId = 1L;
        when(queueRedisRepository.getWaitingPosition(userId))
            .thenReturn(0L);

        // when
        long position = tokenService.getQueuePosition(userId);

        // then
        assertThat(position).isZero();
    }

    @Test
    @DisplayName("대기열 상태 조회 - 활성 유저")
    void getQueueStatus_active() {
        // given
        Long userId = 1L;
        when(queueRedisRepository.isActiveUser(userId)).thenReturn(true);

        // when
        QueueStatusResponse response = tokenService.getQueueStatus(userId);

        // then
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.position()).isZero();
    }

    @Test
    @DisplayName("대기열 상태 조회 - 대기 중")
    void getQueueStatus_waiting() {
        // given
        Long userId = 1L;
        when(queueRedisRepository.isActiveUser(userId)).thenReturn(false);
        when(queueRedisRepository.getWaitingPosition(userId)).thenReturn(5L);

        // when
        QueueStatusResponse response = tokenService.getQueueStatus(userId);

        // then
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.position()).isEqualTo(5L);
    }

    @Test
    @DisplayName("대기열 상태 조회 - 큐에 없음")
    void getQueueStatus_notInQueue() {
        // given
        Long userId = 1L;
        when(queueRedisRepository.isActiveUser(userId)).thenReturn(false);
        when(queueRedisRepository.getWaitingPosition(userId)).thenReturn(0L);

        // when
        QueueStatusResponse response = tokenService.getQueueStatus(userId);

        // then
        assertThat(response.status()).isEqualTo("NOT_IN_QUEUE");
    }

    @Test
    @DisplayName("토큰 활성화 성공 - Redis pop + RDB INSERT")
    void activateWaitingTokens_success() {
        // given
        when(queueRedisRepository.getActiveUserCount()).thenReturn(97L); // 3 slots
        when(queueRedisRepository.popAndActivate(anyInt(), anyLong()))
            .thenReturn(List.of(1L, 2L, 3L));
        when(tokenRepository.save(any(Token.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        int count = tokenService.activateWaitingTokens();

        // then
        assertThat(count).isEqualTo(3);
        verify(queueRedisRepository).popAndActivate(eq(3), anyLong());
        verify(tokenRepository, times(3)).save(any(Token.class));
    }

    @Test
    @DisplayName("토큰 활성화 - 슬롯 없음")
    void activateWaitingTokens_noSlots() {
        // given
        when(queueRedisRepository.getActiveUserCount()).thenReturn(100L);

        // when
        int count = tokenService.activateWaitingTokens();

        // then
        assertThat(count).isZero();
        verify(queueRedisRepository, never()).popAndActivate(anyInt(), anyLong());
    }

    @Test
    @DisplayName("토큰 활성화 - 대기자 없음")
    void activateWaitingTokens_noWaiting() {
        // given
        when(queueRedisRepository.getActiveUserCount()).thenReturn(50L);
        when(queueRedisRepository.popAndActivate(anyInt(), anyLong()))
            .thenReturn(List.of());

        // when
        int count = tokenService.activateWaitingTokens();

        // then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("토큰 활성화 RDB 실패 시 Redis 롤백")
    void activateWaitingTokens_rollbackOnRdbFailure() {
        // given
        List<Long> activatedUsers = List.of(1L, 2L, 3L);
        when(queueRedisRepository.getActiveUserCount()).thenReturn(97L);
        when(queueRedisRepository.popAndActivate(anyInt(), anyLong()))
            .thenReturn(activatedUsers);
        when(tokenRepository.save(any(Token.class)))
            .thenThrow(new RuntimeException("DB 저장 실패"));

        // when & then
        assertThatThrownBy(() -> tokenService.activateWaitingTokens())
            .isInstanceOf(RuntimeException.class);
        verify(queueRedisRepository).rollbackActivation(activatedUsers);
    }

    @Test
    @DisplayName("만료된 활성 유저 정리")
    void expireExpiredTokens_success() {
        // given
        List<Long> expiredUserIds = List.of(1L, 2L);
        Token token1 = Token.issueActive(1L, LocalDateTime.now().minusMinutes(1));
        Token token2 = Token.issueActive(2L, LocalDateTime.now().minusMinutes(1));

        when(queueRedisRepository.removeExpiredActiveUsers()).thenReturn(expiredUserIds);
        when(tokenRepository.findActiveTokenByUserId(1L)).thenReturn(Optional.of(token1));
        when(tokenRepository.findActiveTokenByUserId(2L)).thenReturn(Optional.of(token2));
        when(tokenRepository.save(any(Token.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        int count = tokenService.expireExpiredTokens();

        // then
        assertThat(count).isEqualTo(2);
        verify(queueRedisRepository).removeExpiredActiveUsers();
        verify(tokenRepository, times(2)).save(any(Token.class));
    }

    @Test
    @DisplayName("토큰 검증 성공 - 활성 토큰")
    void validateToken_success() {
        // given
        Token activeToken = Token.issueActive(1L, LocalDateTime.now().plusMinutes(5));
        when(tokenRepository.findByTokenValue("active-token"))
            .thenReturn(Optional.of(activeToken));

        // when & then
        assertThatCode(() -> tokenService.validateToken("active-token"))
            .doesNotThrowAnyException();
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
    }

    @Test
    @DisplayName("토큰 검증 실패 - 만료된 토큰")
    void validateToken_fail_expired() {
        // given
        Token expiredToken = Token.issueActive(1L, LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenValue("expired-token"))
            .thenReturn(Optional.of(expiredToken));

        // when & then
        assertThatThrownBy(() -> tokenService.validateToken("expired-token"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("활성 상태가 아닌 토큰입니다");
    }
}
