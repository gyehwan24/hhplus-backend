package kr.hhplus.be.server.token.application;

import kr.hhplus.be.server.config.redis.DistributedLock;
import kr.hhplus.be.server.token.application.response.QueueStatusResponse;
import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.token.domain.repository.TokenRepository;
import kr.hhplus.be.server.token.infrastructure.redis.QueueRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 토큰/대기열 서비스
 * Redis 기반 대기열 + RDB 기반 토큰 관리
 *
 * 데이터 흐름:
 * 1. 토큰 발급: Redis 대기열에 추가 (RDB 저장 없음)
 * 2. 토큰 활성화: Redis 대기열 → 활성 큐 이동 + RDB Token INSERT
 * 3. 토큰 만료: Redis 활성 큐에서 제거 + RDB Token UPDATE
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;
    private final QueueRedisRepository queueRedisRepository;

    private static final int MAX_ACTIVE_TOKENS = 100; // 동시 활성 토큰 최대 개수
    private static final int TOKEN_EXPIRY_MINUTES = 10; // 토큰 유효 시간 (분)

    /**
     * 대기열에 진입 (토큰 발급)
     * - Redis 대기열에 추가
     * - 사용자당 1개의 대기열/활성 토큰만 존재
     *
     * @param userId 사용자 ID
     * @return 대기열 순번 (1부터 시작)
     * @throws IllegalStateException 이미 대기열에 있거나 활성 토큰이 있는 경우
     */
    public long issueToken(Long userId) {
        // Redis에서 중복 체크 (QueueRedisRepository에서 처리)
        // RDB에서도 ACTIVE 토큰 체크 (트랜잭션 정합성)
        tokenRepository.findActiveTokenByUserId(userId)
            .ifPresent(token -> {
                throw new IllegalStateException("이미 활성화된 토큰이 존재합니다.");
            });

        // Redis 대기열에 추가하고 순번 반환
        return queueRedisRepository.addToWaitingQueue(userId);
    }

    /**
     * 대기열 순번 조회 (Redis ZRANK - O(log n))
     *
     * @param userId 사용자 ID
     * @return 대기열 순번 (1부터 시작, 대기열에 없으면 0)
     */
    @Transactional(readOnly = true)
    public long getQueuePosition(Long userId) {
        return queueRedisRepository.getWaitingPosition(userId);
    }

    /**
     * 대기 중인 유저를 활성화 (스케줄러에서 호출)
     * - Redis 대기열에서 활성 큐로 이동 (Lua 스크립트 - 원자적)
     * - RDB Token 테이블에 ACTIVE 상태로 INSERT
     * - 분산락으로 다중 서버 환경에서 중복 실행 방지
     *
     * @return 활성화된 토큰 개수
     */
    @DistributedLock(key = "'scheduler:token:activate'", waitTime = 3, leaseTime = 30)
    public int activateWaitingTokens() {
        // 현재 활성 유저 수 확인 (Redis 기준)
        long activeCount = queueRedisRepository.getActiveUserCount();
        long availableSlots = MAX_ACTIVE_TOKENS - activeCount;

        if (availableSlots <= 0) {
            return 0; // 여유 없음
        }

        // 만료 시각 계산
        long expireAt = System.currentTimeMillis() + (TOKEN_EXPIRY_MINUTES * 60 * 1000L);
        LocalDateTime expiresAtTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(expireAt), ZoneId.systemDefault());

        // Redis에서 대기열 → 활성 큐 이동 (Lua 스크립트로 원자적)
        List<Long> activatedUserIds = queueRedisRepository.popAndActivate(
            (int) availableSlots, expireAt);

        if (activatedUserIds.isEmpty()) {
            return 0;
        }

        try {
            // RDB Token 테이블에 ACTIVE 상태로 INSERT
            for (Long userId : activatedUserIds) {
                Token token = Token.issueActive(userId, expiresAtTime);
                tokenRepository.save(token);
            }

            log.info("토큰 활성화 완료: {}명", activatedUserIds.size());
            return activatedUserIds.size();

        } catch (Exception e) {
            // RDB 저장 실패 시 Redis 롤백
            log.error("토큰 활성화 RDB 저장 실패, Redis 롤백 수행", e);
            queueRedisRepository.rollbackActivation(activatedUserIds);
            throw e;
        }
    }

    /**
     * 만료된 활성 유저 정리 (스케줄러에서 호출)
     * - Redis 활성 큐에서 만료된 유저 제거
     * - RDB Token 상태를 EXPIRED로 변경
     *
     * @return 만료 처리된 토큰 개수
     */
    @DistributedLock(key = "'scheduler:token:cleanup'", waitTime = 3, leaseTime = 30)
    public int expireExpiredTokens() {
        // Redis에서 만료된 유저 제거
        List<Long> expiredUserIds = queueRedisRepository.removeExpiredActiveUsers();

        if (expiredUserIds.isEmpty()) {
            return 0;
        }

        // RDB에서 해당 유저의 ACTIVE 토큰을 EXPIRED로 변경
        int expiredCount = 0;
        for (Long userId : expiredUserIds) {
            tokenRepository.findActiveTokenByUserId(userId)
                .ifPresent(token -> {
                    Token expired = token.expire();
                    tokenRepository.save(expired);
                });
            expiredCount++;
        }

        log.info("만료된 토큰 정리 완료: {}개", expiredCount);
        return expiredCount;
    }

    /**
     * 토큰 유효성 검증 (RDB 기반 - 트랜잭션 정합성 보장)
     * - 예약/결제 요청 전에 토큰이 활성 상태인지 확인
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
     * 사용자 ID로 활성 상태 확인 (Redis 기반 - 빠른 체크)
     *
     * @param userId 사용자 ID
     * @return 활성 상태 여부
     */
    @Transactional(readOnly = true)
    public boolean isActiveUser(Long userId) {
        return queueRedisRepository.isActiveUser(userId);
    }

    /**
     * 토큰 만료 처리 (예약/결제 완료 후 명시적 만료)
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

    /**
     * 대기열/활성 상태 정보 조회
     *
     * @param userId 사용자 ID
     * @return 상태 정보 (대기열 순번 또는 활성 상태)
     */
    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(Long userId) {
        // 활성 상태인지 확인
        if (queueRedisRepository.isActiveUser(userId)) {
            return QueueStatusResponse.active();
        }

        // 대기열 순번 확인
        long position = queueRedisRepository.getWaitingPosition(userId);
        if (position > 0) {
            return QueueStatusResponse.waiting(position);
        }

        return QueueStatusResponse.notInQueue();
    }
}
