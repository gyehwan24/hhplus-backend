package kr.hhplus.be.server.token.application;

import kr.hhplus.be.server.config.redis.DistributedLock;
import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.token.domain.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 토큰/대기열 서비스
 * 대기열 토큰 발급, 활성화, 만료 처리를 담당
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;

    private static final int MAX_ACTIVE_TOKENS = 100; // 동시 활성 토큰 최대 개수

    /**
     * 토큰 발급
     * - 사용자당 1개의 활성 토큰만 존재
     *
     * @param userId 사용자 ID
     * @return 발급된 토큰
     */
    public Token issueToken(Long userId) {
        // 이미 활성 토큰이 있는지 확인
        tokenRepository.findActiveTokenByUserId(userId)
            .ifPresent(token -> {
                throw new IllegalStateException("이미 활성화된 토큰이 존재합니다.");
            });

        // 새 토큰 발급
        Token token = Token.issue(userId);
        return tokenRepository.save(token);
    }

    /**
     * 대기열 순번 조회
     *
     * @param tokenValue 토큰 값 (UUID)
     * @return 대기열 순번 (1부터 시작, 대기 중이 아니면 0)
     */
    @Transactional(readOnly = true)
    public long getQueuePosition(String tokenValue) {
        Token token = tokenRepository.findByTokenValue(tokenValue)
            .orElseThrow(() -> new IllegalArgumentException("토큰을 찾을 수 없습니다."));

        if (token.getStatus() != TokenStatus.WAITING) {
            return 0; // 대기 중이 아니면 순번 0
        }

        // 자신보다 먼저 생성된 WAITING 토큰 개수 = 순번
        List<Token> waitingTokens = tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.WAITING);

        for (int i = 0; i < waitingTokens.size(); i++) {
            if (waitingTokens.get(i).getTokenValue().equals(tokenValue)) {
                return i + 1; // 1부터 시작
            }
        }

        return 0;
    }

    /**
     * 대기 중인 토큰을 활성화
     * - 배치 또는 스케줄러에서 주기적으로 호출
     * - 현재 활성 토큰이 MAX_ACTIVE_TOKENS보다 적으면 대기 토큰을 활성화
     * - 분산락으로 다중 서버 환경에서 중복 실행 방지
     *
     * @return 활성화된 토큰 개수
     */
    @DistributedLock(key = "'scheduler:token:activate'", waitTime = 3, leaseTime = 30)
    public int activateWaitingTokens() {
        // 현재 활성 토큰 개수 확인
        long activeCount = tokenRepository.countByStatus(TokenStatus.ACTIVE);
        long availableSlots = MAX_ACTIVE_TOKENS - activeCount;

        if (availableSlots <= 0) {
            return 0; // 여유 없음
        }

        // 대기 중인 토큰 조회 (생성 시간 순)
        List<Token> waitingTokens = tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.WAITING);

        // 활성화 가능한 만큼만 처리
        int activateCount = (int) Math.min(availableSlots, waitingTokens.size());

        for (int i = 0; i < activateCount; i++) {
            Token token = waitingTokens.get(i);
            Token activated = token.activate();
            tokenRepository.save(activated);
        }

        return activateCount;
    }

    /**
     * 만료된 활성 토큰 정리
     * - 배치 또는 스케줄러에서 주기적으로 호출
     * - ACTIVE 상태이지만 expiresAt이 지난 토큰을 EXPIRED로 변경
     *
     * @return 만료 처리된 토큰 개수
     */
    public int expireExpiredTokens() {
        List<Token> activeTokens = tokenRepository.findByStatusOrderByCreatedAt(TokenStatus.ACTIVE);

        int expiredCount = 0;
        for (Token token : activeTokens) {
            if (token.isExpired()) {
                Token expired = token.expire();
                tokenRepository.save(expired);
                expiredCount++;
            }
        }

        return expiredCount;
    }

    /**
     * 토큰 유효성 검증
     * - 예약 요청 전에 토큰이 활성 상태인지 확인
     *
     * @param tokenValue 토큰 값 (UUID)
     * @throws IllegalArgumentException 토큰을 찾을 수 없는 경우
     * @throws IllegalStateException 활성 상태가 아닌 경우
     */
    @Transactional(readOnly = true)
    public void validateToken(String tokenValue) {
        Token token = tokenRepository.findByTokenValue(tokenValue)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (!token.isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 토큰입니다. 현재 상태: " + token.getStatus());
        }
    }

    /**
     * 토큰 만료 처리 (사용자가 예약 완료 후 명시적 만료)
     *
     * @param tokenValue 토큰 값 (UUID)
     * @return 만료된 토큰
     */
    public Token expireToken(String tokenValue) {
        Token token = tokenRepository.findByTokenValue(tokenValue)
            .orElseThrow(() -> new IllegalArgumentException("토큰을 찾을 수 없습니다."));

        Token expired = token.expire();
        return tokenRepository.save(expired);
    }

    /**
     * 토큰 ID로 조회
     *
     * @param id 토큰 ID
     * @return 토큰
     */
    @Transactional(readOnly = true)
    public Token getToken(Long id) {
        return tokenRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("토큰을 찾을 수 없습니다."));
    }

    /**
     * 토큰 값으로 조회
     *
     * @param tokenValue 토큰 값 (UUID)
     * @return 토큰
     */
    @Transactional(readOnly = true)
    public Token getTokenByValue(String tokenValue) {
        return tokenRepository.findByTokenValue(tokenValue)
            .orElseThrow(() -> new IllegalArgumentException("토큰을 찾을 수 없습니다."));
    }
}
