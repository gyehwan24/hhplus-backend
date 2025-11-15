package kr.hhplus.be.server.integration.concurrency;

import kr.hhplus.be.server.common.BaseIntegrationTest;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.ConcertRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.concert.infrastructure.persistence.ConcertScheduleJpaRepository;
import kr.hhplus.be.server.concert.infrastructure.persistence.ScheduleSeatJpaRepository;
import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.application.exception.ConcurrentReservationException;
import kr.hhplus.be.server.reservation.application.exception.SeatNotAvailableException;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대규모 동시 예약 테스트
 * - 100명의 사용자가 10개의 좌석을 동시에 예약 시도
 */
public class ConcurrentReservationAdvancedTest extends BaseIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ConcertScheduleJpaRepository scheduleJpaRepository;

    @Autowired
    private ScheduleSeatJpaRepository seatJpaRepository;

    @Autowired
    private ScheduleSeatRepository seatRepository;

    private Long scheduleId;
    private List<Long> seatIds;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        // 콘서트 생성
        Concert concert = concertRepository.save(
            Concert.builder()
                .title("인기 콘서트")
                .description("대규모 동시성 테스트용 콘서트")
                .build()
        );

        // 스케줄 생성
        ConcertSchedule schedule = scheduleJpaRepository.save(
            ConcertSchedule.builder()
                .concertId(concert.getId())
                .venueId(1L)
                .performanceDate(LocalDateTime.now().plusDays(7).toLocalDate())
                .performanceTime(LocalDateTime.now().plusDays(7).toLocalTime())
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(6))
                .build()
        );
        scheduleId = schedule.getId();

        // 10개 좌석 생성
        seatIds = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ScheduleSeat seat = seatJpaRepository.save(
                ScheduleSeat.builder()
                    .scheduleId(schedule.getId())
                    .venueSeatId((long) i)
                    .price(new BigDecimal("50000"))
                    .status(SeatStatus.AVAILABLE)
                    .build()
            );
            seatIds.add(seat.getId());
        }

        // 100명 사용자 ID만 생성 (실제 User 엔티티 없이)
        userIds = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            userIds.add((long) i);
        }
    }

    @Test
    @DisplayName("100명이 10개 좌석을 동시에 예약하면 10명만 성공한다")
    void test100UsersReserving10Seats() throws InterruptedException {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 시작 보장
        CountDownLatch endLatch = new CountDownLatch(100);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger lockTimeoutCount = new AtomicInteger(0);

        Map<Long, Long> seatToUser = new ConcurrentHashMap<>();  // 좌석별 성공한 사용자
        Random random = new Random();

        // When
        for (Long userId : userIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드가 준비될 때까지 대기

                    // 각 사용자는 랜덤하게 1개 좌석 선택
                    Long seatId = seatIds.get(random.nextInt(10));

                    Reservation reservation = reservationService.createReservation(
                        userId, scheduleId, List.of(seatId)
                    );

                    successCount.incrementAndGet();
                    seatToUser.put(seatId, userId);

                } catch (SeatNotAvailableException e) {
                    failureCount.incrementAndGet();
                } catch (ConcurrentReservationException e) {
                    lockTimeoutCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 최대 10초 대기
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isLessThanOrEqualTo(10);
        assertThat(successCount.get() + failureCount.get() + lockTimeoutCount.get()).isEqualTo(100);

        // 각 좌석은 정확히 한 명에게만 배정됨
        for (Long seatId : seatIds) {
            ScheduleSeat seat = seatRepository.findById(seatId).orElseThrow();
            if (seat.getStatus() == SeatStatus.RESERVED) {
                assertThat(seatToUser).containsKey(seatId);
            }
        }

        // 성공/실패 통계 출력
        System.out.println("========== 동시성 테스트 결과 ==========");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패 (좌석 없음): " + failureCount.get() + "명");
        System.out.println("실패 (락 타임아웃): " + lockTimeoutCount.get() + "명");
        System.out.println("예약된 좌석 수: " + seatToUser.size());
        System.out.println("=====================================");

        executor.shutdown();
    }

    @Test
    @DisplayName("여러 사용자가 동일한 여러 좌석을 동시에 예약 시도")
    void testMultipleSeatsReservation() throws InterruptedException {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(20);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 처음 3개 좌석을 예약하려는 시나리오
        List<Long> targetSeats = seatIds.subList(0, 3);

        // When
        for (int i = 0; i < 20; i++) {
            Long userId = userIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // 3개 좌석 한번에 예약 시도
                    Reservation reservation = reservationService.createReservation(
                        userId, scheduleId, targetSeats
                    );

                    successCount.incrementAndGet();
                    System.out.println("User " + userId + " 예약 성공!");

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 동시 시작
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        // Then
        assertThat(successCount.get()).isEqualTo(1);  // 1명만 성공
        assertThat(failureCount.get()).isEqualTo(19);  // 19명 실패

        // 예약된 좌석 상태 확인
        for (Long seatId : targetSeats) {
            ScheduleSeat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("다른 스케줄의 좌석 예약 시도 시 실패")
    void testDifferentScheduleSeatReservation() throws InterruptedException {
        // Given
        // 다른 스케줄 생성
        ConcertSchedule otherSchedule = scheduleJpaRepository.save(
            ConcertSchedule.builder()
                .concertId(1L)  // 같은 콘서트
                .venueId(1L)
                .performanceDate(LocalDateTime.now().plusDays(14).toLocalDate())
                .performanceTime(LocalDateTime.now().plusDays(14).toLocalTime())
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(13))
                .build()
        );

        // 다른 스케줄의 좌석 생성
        ScheduleSeat otherSeat = seatJpaRepository.save(
            ScheduleSeat.builder()
                .scheduleId(otherSchedule.getId())
                .venueSeatId(999L)
                .price(new BigDecimal("50000"))
                .status(SeatStatus.AVAILABLE)
                .build()
        );

        // When & Then
        try {
            // 현재 스케줄에 대해 다른 스케줄의 좌석 예약 시도
            reservationService.createReservation(
                userIds.get(0),
                scheduleId,  // 현재 스케줄
                List.of(otherSeat.getId())  // 다른 스케줄의 좌석
            );

            // 실패해야 함
            assertThat(false).isTrue();

        } catch (SeatNotAvailableException e) {
            // 예상된 예외
            assertThat(e.getMessage()).contains("예약할 수 없습니다");
            assertThat(e.getMessage()).contains(String.valueOf(otherSeat.getId()));
        }

        // 좌석 상태는 여전히 AVAILABLE
        ScheduleSeat seat = seatRepository.findById(otherSeat.getId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }
}