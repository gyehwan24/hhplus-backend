package kr.hhplus.be.server.concert.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConcertRankingRedisRepository 통합 테스트
 * 로컬 Redis (localhost:6379) 필요
 */
class ConcertRankingRedisRepositoryTest {

    private ConcertRankingRedisRepository rankingRedisRepository;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // 로컬 Redis 연결
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        rankingRedisRepository = new ConcertRankingRedisRepository(redisTemplate);

        // 테스트 전 랭킹 키 삭제
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("콘서트 점수 증가 시 일간/주간/월간 랭킹이 모두 업데이트된다")
    void incrementScore_updatesAllPeriods() {
        // given
        Long concertId = 1L;

        // when
        rankingRedisRepository.incrementScore(concertId);

        // then
        List<Long> dailyRanking = rankingRedisRepository.getTopConcertIds("daily", 10);
        List<Long> weeklyRanking = rankingRedisRepository.getTopConcertIds("weekly", 10);
        List<Long> monthlyRanking = rankingRedisRepository.getTopConcertIds("monthly", 10);

        assertThat(dailyRanking).containsExactly(concertId);
        assertThat(weeklyRanking).containsExactly(concertId);
        assertThat(monthlyRanking).containsExactly(concertId);
    }

    @Test
    @DisplayName("여러 콘서트의 점수를 증가시키면 점수순으로 정렬된다")
    void incrementScore_sortsByScore() {
        // given
        Long concert1 = 1L;
        Long concert2 = 2L;
        Long concert3 = 3L;

        // when - concert2가 가장 많은 점수
        rankingRedisRepository.incrementScore(concert1);
        rankingRedisRepository.incrementScore(concert2);
        rankingRedisRepository.incrementScore(concert2);
        rankingRedisRepository.incrementScore(concert2);
        rankingRedisRepository.incrementScore(concert3);
        rankingRedisRepository.incrementScore(concert3);

        // then - 점수 내림차순: concert2(3) > concert3(2) > concert1(1)
        List<Long> ranking = rankingRedisRepository.getTopConcertIds("daily", 10);
        assertThat(ranking).containsExactly(concert2, concert3, concert1);
    }

    @Test
    @DisplayName("limit 파라미터로 상위 N개만 조회할 수 있다")
    void getTopConcertIds_withLimit() {
        // given
        for (long i = 1; i <= 5; i++) {
            for (int j = 0; j < i; j++) {
                rankingRedisRepository.incrementScore(i);
            }
        }

        // when
        List<Long> top3 = rankingRedisRepository.getTopConcertIds("daily", 3);

        // then - 점수 내림차순 상위 3개: 5(5점) > 4(4점) > 3(3점)
        assertThat(top3).hasSize(3);
        assertThat(top3).containsExactly(5L, 4L, 3L);
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
    void getTopConcertIds_emptyWhenNoData() {
        // when
        List<Long> ranking = rankingRedisRepository.getTopConcertIds("daily", 10);

        // then
        assertThat(ranking).isEmpty();
    }

    @Test
    @DisplayName("TTL이 설정된다")
    void incrementScore_setsTTL() {
        // given
        Long concertId = 1L;

        // when
        rankingRedisRepository.incrementScore(concertId);

        // then
        String dailyKey = "ranking:daily:" + java.time.LocalDate.now();
        Long ttl = redisTemplate.getExpire(dailyKey);

        assertThat(ttl).isGreaterThan(0); // TTL이 설정됨
        assertThat(ttl).isLessThanOrEqualTo(2 * 24 * 60 * 60); // 2일 이하
    }
}
