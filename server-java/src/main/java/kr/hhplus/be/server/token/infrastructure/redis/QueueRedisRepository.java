package kr.hhplus.be.server.token.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Redis 기반 대기열 Repository
 *
 * 데이터 구조:
 * - queue:waiting (ZSet): 대기 중인 유저 (score = 진입 시각 timestamp)
 * - queue:active (ZSet): 활성 유저 (score = 만료 시각 timestamp)
 */
@Slf4j
@Repository
public class QueueRedisRepository {

    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String ACTIVE_QUEUE_KEY = "queue:active";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> popAndActivateScript;

    public QueueRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.popAndActivateScript = createPopAndActivateScript();
    }

    /**
     * Lua 스크립트 생성 - 대기열 → 활성 큐 원자적 이동
     */
    private DefaultRedisScript<List> createPopAndActivateScript() {
        String script = """
            local waitingKey = KEYS[1]
            local activeKey = KEYS[2]
            local count = tonumber(ARGV[1])
            local expireAt = tonumber(ARGV[2])

            -- 대기열에서 count만큼 pop (점수 낮은 순 = 먼저 진입한 순)
            local users = redis.call('ZPOPMIN', waitingKey, count)

            local result = {}
            if #users > 0 then
                for i = 1, #users, 2 do
                    local userId = users[i]
                    -- 활성 큐에 추가 (score = 만료 시각)
                    redis.call('ZADD', activeKey, expireAt, userId)
                    table.insert(result, userId)
                end
            end

            return result
            """;

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(List.class);
        return redisScript;
    }

    /**
     * 대기열에 유저 추가
     *
     * @param userId 유저 ID
     * @return 대기열 순번 (1부터 시작)
     * @throws IllegalStateException 이미 대기열에 있는 경우
     */
    public long addToWaitingQueue(Long userId) {
        String userIdStr = userId.toString();

        // 중복 체크 - 대기열 또는 활성 큐에 있는지 확인
        if (isInWaitingQueue(userId)) {
            throw new IllegalStateException("이미 대기열에 있습니다.");
        }
        if (isActiveUser(userId)) {
            throw new IllegalStateException("이미 활성화된 상태입니다.");
        }

        // 대기열에 추가 (score = 현재 시각 timestamp)
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, userIdStr, score);

        // 순번 반환 (0-based rank + 1)
        Long rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userIdStr);
        return rank != null ? rank + 1 : 1;
    }

    /**
     * 대기열 순번 조회
     *
     * @param userId 유저 ID
     * @return 대기열 순번 (1부터 시작, 대기열에 없으면 0)
     */
    public long getWaitingPosition(Long userId) {
        String userIdStr = userId.toString();
        Long rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userIdStr);
        return rank != null ? rank + 1 : 0;
    }

    /**
     * 대기열에 있는지 확인
     *
     * @param userId 유저 ID
     * @return 대기열 존재 여부
     */
    public boolean isInWaitingQueue(Long userId) {
        String userIdStr = userId.toString();
        Double score = redisTemplate.opsForZSet().score(WAITING_QUEUE_KEY, userIdStr);
        return score != null;
    }

    /**
     * 활성 유저인지 확인 (만료되지 않은 경우만)
     *
     * @param userId 유저 ID
     * @return 활성 상태 여부
     */
    public boolean isActiveUser(Long userId) {
        String userIdStr = userId.toString();
        Double expireAt = redisTemplate.opsForZSet().score(ACTIVE_QUEUE_KEY, userIdStr);

        if (expireAt == null) {
            return false;
        }

        // score = 만료 시각이므로, 현재 시각보다 크면 유효
        return expireAt > System.currentTimeMillis();
    }

    /**
     * 대기열에서 활성 큐로 이동 (Lua 스크립트 사용 - 원자적)
     *
     * @param count    이동할 유저 수
     * @param expireAt 만료 시각 (timestamp)
     * @return 활성화된 유저 ID 목록
     */
    @SuppressWarnings("unchecked")
    public List<Long> popAndActivate(int count, long expireAt) {
        List<String> keys = List.of(WAITING_QUEUE_KEY, ACTIVE_QUEUE_KEY);
        List<Object> result = redisTemplate.execute(
                popAndActivateScript,
                keys,
                String.valueOf(count),
                String.valueOf(expireAt)
        );

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = new ArrayList<>();
        for (Object item : result) {
            userIds.add(Long.parseLong(item.toString()));
        }

        log.info("대기열 → 활성 큐 이동 완료: {}명, 만료시각: {}", userIds.size(), expireAt);
        return userIds;
    }

    /**
     * RDB 저장 실패 시 Redis 롤백
     * - 활성 큐에서 제거하고 대기열 맨 앞에 다시 추가
     *
     * @param userIds 롤백할 유저 ID 목록
     */
    public void rollbackActivation(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // 활성 큐에서 제거
        String[] userIdArray = userIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new);
        redisTemplate.opsForZSet().remove(ACTIVE_QUEUE_KEY, (Object[]) userIdArray);

        // 대기열 맨 앞에 다시 추가 (가장 낮은 score로)
        Set<String> firstUser = redisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, 0);
        Double minScore = null;
        if (firstUser != null && !firstUser.isEmpty()) {
            minScore = redisTemplate.opsForZSet().score(WAITING_QUEUE_KEY, firstUser.iterator().next());
        }
        double baseScore = minScore != null ? minScore - userIds.size() : System.currentTimeMillis();

        for (int i = 0; i < userIds.size(); i++) {
            redisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, userIds.get(i).toString(), baseScore + i);
        }

        log.warn("활성화 롤백 완료: {}명", userIds.size());
    }

    /**
     * 만료된 활성 유저 제거 (스케줄러에서 호출)
     * - score <= 현재시각인 유저들을 일괄 제거
     *
     * @return 제거된 유저 ID 목록
     */
    public List<Long> removeExpiredActiveUsers() {
        long now = System.currentTimeMillis();

        // 만료된 유저 조회 (score <= 현재시각)
        Set<String> expiredUsers = redisTemplate.opsForZSet()
                .rangeByScore(ACTIVE_QUEUE_KEY, 0, now);

        if (expiredUsers == null || expiredUsers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> expiredUserIds = expiredUsers.stream()
                .map(Long::parseLong)
                .toList();

        // 제거
        redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_QUEUE_KEY, 0, now);

        log.info("만료된 활성 유저 제거 완료: {}명", expiredUserIds.size());
        return expiredUserIds;
    }

    /**
     * 현재 활성 유저 수 조회
     *
     * @return 활성 유저 수
     */
    public long getActiveUserCount() {
        Long count = redisTemplate.opsForZSet().zCard(ACTIVE_QUEUE_KEY);
        return count != null ? count : 0;
    }

    /**
     * 현재 대기열 유저 수 조회
     *
     * @return 대기열 유저 수
     */
    public long getWaitingUserCount() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY);
        return count != null ? count : 0;
    }
}
