package kr.hhplus.be.server.concert.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 콘서트 랭킹 Redis Repository
 * Redis Sorted Set을 이용한 일간/주간/월간 랭킹 관리
 */
@Repository
@RequiredArgsConstructor
public class ConcertRankingRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String DAILY_KEY_PREFIX = "ranking:daily:";
    private static final String WEEKLY_KEY_PREFIX = "ranking:weekly:";
    private static final String MONTHLY_KEY_PREFIX = "ranking:monthly:";

    private static final Duration DAILY_TTL = Duration.ofDays(2);
    private static final Duration WEEKLY_TTL = Duration.ofDays(8);
    private static final Duration MONTHLY_TTL = Duration.ofDays(32);

    /**
     * 콘서트 랭킹 점수 증가 (일간/주간/월간 모두 업데이트)
     *
     * @param concertId 콘서트 ID
     */
    public void incrementScore(Long concertId) {
        String concertIdStr = concertId.toString();
        LocalDate now = LocalDate.now();

        // 일간 랭킹
        incrementForPeriod(getDailyKey(now), concertIdStr, DAILY_TTL);

        // 주간 랭킹
        incrementForPeriod(getWeeklyKey(now), concertIdStr, WEEKLY_TTL);

        // 월간 랭킹
        incrementForPeriod(getMonthlyKey(now), concertIdStr, MONTHLY_TTL);
    }

    /**
     * 특정 기간 랭킹 점수 증가 + TTL 설정
     */
    private void incrementForPeriod(String key, String concertId, Duration ttl) {
        redisTemplate.opsForZSet().incrementScore(key, concertId, 1);

        // TTL이 설정되어 있지 않으면 설정 (키 생성 시점에만)
        Long expireSeconds = redisTemplate.getExpire(key);
        if (expireSeconds != null && expireSeconds == -1) {
            redisTemplate.expire(key, ttl);
        }
    }

    /**
     * 랭킹 상위 콘서트 ID 조회
     *
     * @param period 기간 (daily, weekly, monthly)
     * @param limit  조회 개수
     * @return 콘서트 ID 목록 (내림차순)
     */
    public List<Long> getTopConcertIds(String period, int limit) {
        String key = getKeyByPeriod(period);
        Set<String> results = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream()
                .map(Long::valueOf)
                .toList();
    }

    /**
     * 기간에 해당하는 키 반환
     */
    private String getKeyByPeriod(String period) {
        LocalDate now = LocalDate.now();
        return switch (period.toLowerCase()) {
            case "daily" -> getDailyKey(now);
            case "weekly" -> getWeeklyKey(now);
            case "monthly" -> getMonthlyKey(now);
            default -> throw new IllegalArgumentException("Invalid period: " + period);
        };
    }

    /**
     * 일간 키 생성 (ranking:daily:2025-11-30)
     */
    private String getDailyKey(LocalDate date) {
        return DAILY_KEY_PREFIX + date.toString();
    }

    /**
     * 주간 키 생성 (ranking:weekly:2025-W48) - ISO 8601 표준
     */
    private String getWeeklyKey(LocalDate date) {
        int year = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%s%d-W%02d", WEEKLY_KEY_PREFIX, year, week);
    }

    /**
     * 월간 키 생성 (ranking:monthly:2025-11)
     */
    private String getMonthlyKey(LocalDate date) {
        return String.format("%s%d-%02d", MONTHLY_KEY_PREFIX, date.getYear(), date.getMonthValue());
    }
}
