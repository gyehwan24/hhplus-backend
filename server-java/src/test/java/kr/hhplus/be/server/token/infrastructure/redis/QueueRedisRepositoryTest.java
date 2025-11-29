package kr.hhplus.be.server.token.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QueueRedisRepository 통합 테스트
 * 로컬 Redis (localhost:6379) 필요
 */
class QueueRedisRepositoryTest {

    private QueueRedisRepository queueRedisRepository;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // 로컬 Redis 연결
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        queueRedisRepository = new QueueRedisRepository(redisTemplate);

        // 테스트 전 큐 키 삭제
        Set<String> keys = redisTemplate.keys("queue:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("대기열에 유저 추가 시 순번이 반환된다")
    void addToWaitingQueue_returnsPosition() {
        // when
        long position1 = queueRedisRepository.addToWaitingQueue(1L);
        long position2 = queueRedisRepository.addToWaitingQueue(2L);
        long position3 = queueRedisRepository.addToWaitingQueue(3L);

        // then
        assertThat(position1).isEqualTo(1);
        assertThat(position2).isEqualTo(2);
        assertThat(position3).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 대기열에 있는 유저는 중복 추가할 수 없다")
    void addToWaitingQueue_throwsWhenDuplicate() {
        // given
        queueRedisRepository.addToWaitingQueue(1L);

        // when & then
        assertThatThrownBy(() -> queueRedisRepository.addToWaitingQueue(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 대기열에 있습니다");
    }

    @Test
    @DisplayName("이미 활성화된 유저는 대기열에 추가할 수 없다")
    void addToWaitingQueue_throwsWhenAlreadyActive() {
        // given - 유저를 활성 큐에 직접 추가
        long expireAt = System.currentTimeMillis() + 600_000; // 10분 후
        redisTemplate.opsForZSet().add("queue:active", "1", expireAt);

        // when & then
        assertThatThrownBy(() -> queueRedisRepository.addToWaitingQueue(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 활성화된 상태입니다");
    }

    @Test
    @DisplayName("대기열 순번 조회")
    void getWaitingPosition_returnsCorrectPosition() {
        // given
        queueRedisRepository.addToWaitingQueue(1L);
        queueRedisRepository.addToWaitingQueue(2L);
        queueRedisRepository.addToWaitingQueue(3L);

        // when & then
        assertThat(queueRedisRepository.getWaitingPosition(1L)).isEqualTo(1);
        assertThat(queueRedisRepository.getWaitingPosition(2L)).isEqualTo(2);
        assertThat(queueRedisRepository.getWaitingPosition(3L)).isEqualTo(3);
        assertThat(queueRedisRepository.getWaitingPosition(99L)).isEqualTo(0); // 없는 유저
    }

    @Test
    @DisplayName("대기열에서 활성 큐로 원자적 이동 (Lua 스크립트)")
    void popAndActivate_movesUsersAtomically() {
        // given
        queueRedisRepository.addToWaitingQueue(1L);
        queueRedisRepository.addToWaitingQueue(2L);
        queueRedisRepository.addToWaitingQueue(3L);

        long expireAt = System.currentTimeMillis() + 600_000; // 10분 후

        // when
        List<Long> activatedUsers = queueRedisRepository.popAndActivate(2, expireAt);

        // then
        assertThat(activatedUsers).containsExactly(1L, 2L); // 먼저 진입한 순서대로

        // 대기열에는 3만 남음
        assertThat(queueRedisRepository.getWaitingPosition(1L)).isEqualTo(0);
        assertThat(queueRedisRepository.getWaitingPosition(2L)).isEqualTo(0);
        assertThat(queueRedisRepository.getWaitingPosition(3L)).isEqualTo(1);

        // 활성 큐에 1, 2가 있음
        assertThat(queueRedisRepository.isActiveUser(1L)).isTrue();
        assertThat(queueRedisRepository.isActiveUser(2L)).isTrue();
        assertThat(queueRedisRepository.isActiveUser(3L)).isFalse();
    }

    @Test
    @DisplayName("활성 유저 만료 확인")
    void isActiveUser_checksExpiration() {
        // given - 만료된 유저 추가
        long expiredTime = System.currentTimeMillis() - 1000; // 1초 전
        redisTemplate.opsForZSet().add("queue:active", "1", expiredTime);

        // 유효한 유저 추가
        long validTime = System.currentTimeMillis() + 600_000; // 10분 후
        redisTemplate.opsForZSet().add("queue:active", "2", validTime);

        // when & then
        assertThat(queueRedisRepository.isActiveUser(1L)).isFalse(); // 만료됨
        assertThat(queueRedisRepository.isActiveUser(2L)).isTrue();  // 유효함
    }

    @Test
    @DisplayName("만료된 활성 유저 제거")
    void removeExpiredActiveUsers_removesExpiredOnly() {
        // given
        long expiredTime = System.currentTimeMillis() - 1000; // 1초 전
        long validTime = System.currentTimeMillis() + 600_000; // 10분 후

        redisTemplate.opsForZSet().add("queue:active", "1", expiredTime);
        redisTemplate.opsForZSet().add("queue:active", "2", expiredTime);
        redisTemplate.opsForZSet().add("queue:active", "3", validTime);

        // when
        List<Long> expiredUsers = queueRedisRepository.removeExpiredActiveUsers();

        // then
        assertThat(expiredUsers).containsExactlyInAnyOrder(1L, 2L);
        assertThat(queueRedisRepository.isActiveUser(1L)).isFalse();
        assertThat(queueRedisRepository.isActiveUser(2L)).isFalse();
        assertThat(queueRedisRepository.isActiveUser(3L)).isTrue();
    }

    @Test
    @DisplayName("롤백 시 활성 큐에서 제거되고 대기열 맨 앞에 복원된다")
    void rollbackActivation_restoresUsersToFrontOfQueue() {
        // given - 활성 큐에 유저 추가
        long expireAt = System.currentTimeMillis() + 600_000;
        redisTemplate.opsForZSet().add("queue:active", "1", expireAt);
        redisTemplate.opsForZSet().add("queue:active", "2", expireAt);

        // 대기열에 다른 유저 추가
        queueRedisRepository.addToWaitingQueue(3L);

        // when
        queueRedisRepository.rollbackActivation(List.of(1L, 2L));

        // then - 활성 큐에서 제거됨
        assertThat(queueRedisRepository.isActiveUser(1L)).isFalse();
        assertThat(queueRedisRepository.isActiveUser(2L)).isFalse();

        // 대기열 맨 앞에 복원됨 (1, 2가 3보다 앞)
        assertThat(queueRedisRepository.getWaitingPosition(1L)).isEqualTo(1);
        assertThat(queueRedisRepository.getWaitingPosition(2L)).isEqualTo(2);
        assertThat(queueRedisRepository.getWaitingPosition(3L)).isEqualTo(3);
    }

    @Test
    @DisplayName("대기열/활성 유저 수 조회")
    void getQueueCounts() {
        // given
        queueRedisRepository.addToWaitingQueue(1L);
        queueRedisRepository.addToWaitingQueue(2L);

        long expireAt = System.currentTimeMillis() + 600_000;
        redisTemplate.opsForZSet().add("queue:active", "3", expireAt);

        // when & then
        assertThat(queueRedisRepository.getWaitingUserCount()).isEqualTo(2);
        assertThat(queueRedisRepository.getActiveUserCount()).isEqualTo(1);
    }
}
