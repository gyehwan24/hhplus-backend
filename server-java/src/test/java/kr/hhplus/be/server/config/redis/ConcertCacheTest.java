package kr.hhplus.be.server.config.redis;

import kr.hhplus.be.server.concert.application.ConcertService;
import kr.hhplus.be.server.concert.application.response.ConcertResponse;
import kr.hhplus.be.server.concert.application.response.ConcertScheduleResponse;
import kr.hhplus.be.server.concert.application.response.SeatResponse;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import kr.hhplus.be.server.concert.infrastructure.persistence.ConcertJpaRepository;
import kr.hhplus.be.server.concert.infrastructure.persistence.ConcertScheduleJpaRepository;
import kr.hhplus.be.server.concert.infrastructure.persistence.ScheduleSeatJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐시 동작 테스트
 *
 * 4가지 캐시 시나리오:
 * 1. 콘서트 목록 조회 (5분)
 * 2. 콘서트 상세 조회 (10분)
 * 3. 콘서트 스케줄 조회 (3분)
 * 4. 좌석 상태 조회 (10초)
 */
@DisplayName("캐시 동작 테스트")
class ConcertCacheTest extends BaseRedisTest {

    @Autowired
    private ConcertService concertService;

    @Autowired
    private ConcertJpaRepository concertJpaRepository;

    @Autowired
    private ConcertScheduleJpaRepository scheduleJpaRepository;

    @Autowired
    private ScheduleSeatJpaRepository seatJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Concert testConcert;
    private ConcertSchedule testSchedule;
    private ScheduleSeat testSeat1;
    private ScheduleSeat testSeat2;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 생성
        testConcert = concertJpaRepository.save(
            Concert.create("테스트 콘서트", "테스트 아티스트", "설명", "http://image.url", 120)
        );

        testSchedule = scheduleJpaRepository.save(
            ConcertSchedule.builder()
                .concertId(testConcert.getId())
                .venueId(1L)
                .performanceDate(LocalDate.now().plusDays(7))
                .performanceTime(LocalTime.of(19, 0))
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(6))
                .maxSeatsPerUser(4)
                .status(ScheduleStatus.AVAILABLE)
                .build()
        );

        testSeat1 = seatJpaRepository.save(
            ScheduleSeat.builder()
                .scheduleId(testSchedule.getId())
                .venueSeatId(1L)
                .price(BigDecimal.valueOf(100000))
                .status(SeatStatus.AVAILABLE)
                .build()
        );

        testSeat2 = seatJpaRepository.save(
            ScheduleSeat.builder()
                .scheduleId(testSchedule.getId())
                .venueSeatId(2L)
                .price(BigDecimal.valueOf(100000))
                .status(SeatStatus.AVAILABLE)
                .build()
        );
    }

    @AfterEach
    void tearDown() {
        // 캐시 초기화
        clearAllCaches();

        // 데이터 정리
        seatJpaRepository.deleteAll();
        scheduleJpaRepository.deleteAll();
        concertJpaRepository.deleteAll();
    }

    private void clearAllCaches() {
        String[] cacheNames = {"cache:concert:list", "cache:concert:detail",
                               "cache:schedule:list", "cache:seat:available"};
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Nested
    @DisplayName("시나리오 1: 콘서트 목록 조회 캐시 (TTL 5분)")
    class ConcertListCacheTest {

        @Test
        @DisplayName("첫 조회 시 캐시 미스, 재조회 시 캐시 적중")
        void getAllConcerts_cacheHitMiss() {
            // Given
            String cacheKey = "cache:concert:list::all";
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

            // When: 첫 번째 호출 - 캐시 미스
            List<ConcertResponse> firstResult = concertService.getAllConcerts();

            // Then: 캐시에 저장됨
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
            assertThat(firstResult).hasSize(1);
            assertThat(firstResult.get(0).title()).isEqualTo("테스트 콘서트");

            // When: 두 번째 호출 - 캐시 적중
            List<ConcertResponse> secondResult = concertService.getAllConcerts();

            // Then: 동일한 결과 반환
            assertThat(secondResult).hasSize(1);
            assertThat(secondResult.get(0).id()).isEqualTo(firstResult.get(0).id());
        }

        @Test
        @DisplayName("캐시 TTL 확인 (5분 = 300초)")
        void getAllConcerts_ttlCheck() {
            // Given
            String cacheKey = "cache:concert:list::all";
            concertService.getAllConcerts();

            // Then: TTL이 5분(300초) 이하
            Long ttl = redisTemplate.getExpire(cacheKey);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(300);
        }

        @Test
        @DisplayName("캐시 수동 무효화")
        void getAllConcerts_cacheEviction() {
            // Given: 캐시에 데이터 저장
            String cacheKey = "cache:concert:list::all";
            concertService.getAllConcerts();
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

            // When: 캐시 무효화
            Cache cache = cacheManager.getCache("cache:concert:list");
            cache.evict("all");

            // Then: 캐시가 비어있음
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
        }
    }

    @Nested
    @DisplayName("시나리오 2: 콘서트 상세 조회 캐시 (TTL 10분)")
    class ConcertDetailCacheTest {

        @Test
        @DisplayName("첫 조회 시 캐시 미스, 재조회 시 캐시 적중")
        void getConcertById_cacheHitMiss() {
            // Given
            Long concertId = testConcert.getId();
            String cacheKey = "cache:concert:detail::" + concertId;
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

            // When: 첫 번째 호출 - 캐시 미스
            ConcertResponse firstResult = concertService.getConcertById(concertId);

            // Then: 캐시에 저장됨
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
            assertThat(firstResult.title()).isEqualTo("테스트 콘서트");

            // When: 두 번째 호출 - 캐시 적중
            ConcertResponse secondResult = concertService.getConcertById(concertId);

            // Then: 동일한 결과 반환
            assertThat(secondResult.id()).isEqualTo(firstResult.id());
            assertThat(secondResult.title()).isEqualTo(firstResult.title());
        }

        @Test
        @DisplayName("캐시 TTL 확인 (10분 = 600초)")
        void getConcertById_ttlCheck() {
            // Given
            Long concertId = testConcert.getId();
            String cacheKey = "cache:concert:detail::" + concertId;
            concertService.getConcertById(concertId);

            // Then: TTL이 10분(600초) 이하
            Long ttl = redisTemplate.getExpire(cacheKey);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(600);
        }

        @Test
        @DisplayName("캐시 수동 무효화")
        void getConcertById_cacheEviction() {
            // Given: 캐시에 데이터 저장
            Long concertId = testConcert.getId();
            String cacheKey = "cache:concert:detail::" + concertId;
            concertService.getConcertById(concertId);
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

            // When: 캐시 무효화
            Cache cache = cacheManager.getCache("cache:concert:detail");
            cache.evict(concertId);

            // Then: 캐시가 비어있음
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
        }
    }

    @Nested
    @DisplayName("시나리오 3: 콘서트 스케줄 조회 캐시 (TTL 3분)")
    class ScheduleCacheTest {

        @Test
        @DisplayName("첫 조회 시 캐시 미스, 재조회 시 캐시 적중")
        void getAvailableSchedules_cacheHitMiss() {
            // Given
            Long concertId = testConcert.getId();
            LocalDate fromDate = LocalDate.now();
            LocalDate toDate = LocalDate.now().plusDays(30);
            String cacheKey = "cache:schedule:list::" + concertId + ":" + fromDate + ":" + toDate;

            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

            // When: 첫 번째 호출 - 캐시 미스
            List<ConcertScheduleResponse> firstResult = concertService.getAvailableSchedules(concertId, fromDate, toDate);

            // Then: 캐시에 저장됨
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
            assertThat(firstResult).hasSize(1);

            // When: 두 번째 호출 - 캐시 적중
            List<ConcertScheduleResponse> secondResult = concertService.getAvailableSchedules(concertId, fromDate, toDate);

            // Then: 동일한 결과 반환
            assertThat(secondResult).hasSize(firstResult.size());
            assertThat(secondResult.get(0).scheduleId()).isEqualTo(firstResult.get(0).scheduleId());
        }

        @Test
        @DisplayName("캐시 TTL 확인 (3분 = 180초)")
        void getAvailableSchedules_ttlCheck() {
            // Given
            Long concertId = testConcert.getId();
            LocalDate fromDate = LocalDate.now();
            LocalDate toDate = LocalDate.now().plusDays(30);
            String cacheKey = "cache:schedule:list::" + concertId + ":" + fromDate + ":" + toDate;

            concertService.getAvailableSchedules(concertId, fromDate, toDate);

            // Then: TTL이 3분(180초) 이하
            Long ttl = redisTemplate.getExpire(cacheKey);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(180);
        }

        @Test
        @DisplayName("캐시 수동 무효화")
        void getAvailableSchedules_cacheEviction() {
            // Given
            Long concertId = testConcert.getId();
            LocalDate fromDate = LocalDate.now();
            LocalDate toDate = LocalDate.now().plusDays(30);
            String cacheKey = "cache:schedule:list::" + concertId + ":" + fromDate + ":" + toDate;
            String evictKey = concertId + ":" + fromDate + ":" + toDate;

            concertService.getAvailableSchedules(concertId, fromDate, toDate);
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

            // When: 캐시 무효화
            Cache cache = cacheManager.getCache("cache:schedule:list");
            cache.evict(evictKey);

            // Then: 캐시가 비어있음
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
        }
    }

    @Nested
    @DisplayName("시나리오 4: 좌석 상태 조회 캐시 (TTL 10초)")
    class SeatCacheTest {

        @Test
        @DisplayName("첫 조회 시 캐시 미스, 재조회 시 캐시 적중")
        void getAvailableSeats_cacheHitMiss() {
            // Given
            Long scheduleId = testSchedule.getId();
            String cacheKey = "cache:seat:available::" + scheduleId;

            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

            // When: 첫 번째 호출 - 캐시 미스
            List<SeatResponse> firstResult = concertService.getAvailableSeats(scheduleId);

            // Then: 캐시에 저장됨
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
            assertThat(firstResult).hasSize(2);

            // When: 두 번째 호출 - 캐시 적중
            List<SeatResponse> secondResult = concertService.getAvailableSeats(scheduleId);

            // Then: 동일한 결과 반환
            assertThat(secondResult).hasSize(firstResult.size());
        }

        @Test
        @DisplayName("캐시 TTL 확인 (10초)")
        void getAvailableSeats_ttlCheck() {
            // Given
            Long scheduleId = testSchedule.getId();
            String cacheKey = "cache:seat:available::" + scheduleId;

            concertService.getAvailableSeats(scheduleId);

            // Then: TTL이 10초 이하
            Long ttl = redisTemplate.getExpire(cacheKey);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("캐시 수동 무효화 (Write-Through 시뮬레이션)")
        void getAvailableSeats_cacheEviction() {
            // Given: 캐시에 데이터 저장
            Long scheduleId = testSchedule.getId();
            String cacheKey = "cache:seat:available::" + scheduleId;

            concertService.getAvailableSeats(scheduleId);
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

            // When: 캐시 무효화 (예약 서비스에서 호출하는 것과 동일)
            Cache cache = cacheManager.getCache("cache:seat:available");
            cache.evict(scheduleId);

            // Then: 캐시가 비어있음
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

            // When: 다시 조회하면 캐시에 저장됨
            concertService.getAvailableSeats(scheduleId);
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
        }
    }

    @Nested
    @DisplayName("캐시 무효화 통합 테스트")
    class CacheEvictionIntegrationTest {

        @Test
        @DisplayName("서로 다른 캐시는 독립적으로 동작")
        void differentCaches_independent() {
            // Given: 모든 캐시에 데이터 저장
            Long concertId = testConcert.getId();
            Long scheduleId = testSchedule.getId();
            LocalDate fromDate = LocalDate.now();
            LocalDate toDate = LocalDate.now().plusDays(30);

            concertService.getAllConcerts();
            concertService.getConcertById(concertId);
            concertService.getAvailableSchedules(concertId, fromDate, toDate);
            concertService.getAvailableSeats(scheduleId);

            // 모든 캐시가 저장되었는지 확인
            assertThat(redisTemplate.hasKey("cache:concert:list::all")).isTrue();
            assertThat(redisTemplate.hasKey("cache:concert:detail::" + concertId)).isTrue();
            assertThat(redisTemplate.hasKey("cache:schedule:list::" + concertId + ":" + fromDate + ":" + toDate)).isTrue();
            assertThat(redisTemplate.hasKey("cache:seat:available::" + scheduleId)).isTrue();

            // When: 좌석 캐시만 무효화
            Cache seatCache = cacheManager.getCache("cache:seat:available");
            seatCache.evict(scheduleId);

            // Then: 좌석 캐시만 삭제됨, 나머지는 유지
            assertThat(redisTemplate.hasKey("cache:concert:list::all")).isTrue();
            assertThat(redisTemplate.hasKey("cache:concert:detail::" + concertId)).isTrue();
            assertThat(redisTemplate.hasKey("cache:schedule:list::" + concertId + ":" + fromDate + ":" + toDate)).isTrue();
            assertThat(redisTemplate.hasKey("cache:seat:available::" + scheduleId)).isFalse();
        }
    }
}
