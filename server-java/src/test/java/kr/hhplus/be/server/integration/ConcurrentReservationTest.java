package kr.hhplus.be.server.integration;

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
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.token.application.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트
 * 동시에 여러 사용자가 같은 좌석 예약 시도, 한 명만 성공
 */
class ConcurrentReservationTest extends BaseIntegrationTest {

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ConcertScheduleJpaRepository scheduleJpaRepository;

    @Autowired
    private ScheduleSeatJpaRepository seatJpaRepository;

    @Autowired
    private ScheduleSeatRepository seatRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TokenService tokenService;

    @Test
    @DisplayName("동시에 10명이 동일 좌석 예약 시도 → 1명만 성공, 9명 실패")
    void concurrentReservation_OnlyOneSucceeds() throws InterruptedException {
        // Given: 콘서트와 좌석 1개 준비
        Concert concert = concertRepository.save(
            Concert.builder()
                .title("Coldplay Concert")
                .description("Concurrency Test")
                .build()
        );

        ConcertSchedule schedule = scheduleJpaRepository.save(
            ConcertSchedule.builder()
                .concertId(concert.getId())
                .venueId(1L)
                .performanceDate(LocalDateTime.now().plusDays(7).toLocalDate())
                .performanceTime(LocalDateTime.now().plusDays(7).toLocalTime())
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(7))
                .build()
        );

        ScheduleSeat seat = seatJpaRepository.save(
            ScheduleSeat.builder()
                .scheduleId(schedule.getId())
                .venueSeatId(1L)
                .price(new BigDecimal("10000"))
                .status(SeatStatus.AVAILABLE)
                .build()
        );

        // 10명의 사용자 토큰 발급
        int userCount = 10;
        for (long userId = 1; userId <= userCount; userId++) {
            tokenService.issueToken(userId);
        }
        tokenService.activateWaitingTokens();

        // When: 10명이 동시에 동일한 좌석 예약 시도
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (long userId = 1; userId <= userCount; userId++) {
            long currentUserId = userId;
            executor.submit(() -> {
                try {
                    Reservation reservation = reservationService.createReservation(
                        currentUserId,
                        schedule.getId(),
                        List.of(seat.getId())
                    );
                    successCount.incrementAndGet();
                    System.out.println("사용자 " + currentUserId + " 예약 성공, 예약 ID: " + reservation.getId());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("사용자 " + currentUserId + " 예약 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 작업 완료 대기
        executor.shutdown();

        // Then: 1명만 성공, 9명 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(9);

        // Then: 좌석 상태 RESERVED 확인
        ScheduleSeat reservedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        System.out.println("\n=== 동시성 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failureCount.get() + "명");
        System.out.println("최종 좌석 상태: " + reservedSeat.getStatus());
    }
}
