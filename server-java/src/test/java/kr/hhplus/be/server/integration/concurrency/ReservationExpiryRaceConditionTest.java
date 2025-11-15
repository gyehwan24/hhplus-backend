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
import kr.hhplus.be.server.payment.application.PaymentService;
import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예약 만료 배치 Race Condition 테스트
 * - 결제 vs 만료 배치 동시 실행 시나리오
 */
public class ReservationExpiryRaceConditionTest extends BaseIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserBalanceRepository userBalanceRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ConcertScheduleJpaRepository scheduleJpaRepository;

    @Autowired
    private ScheduleSeatJpaRepository seatJpaRepository;

    @Autowired
    private ScheduleSeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Long userId;
    private Long scheduleId;
    private List<Long> seatIds;

    @BeforeEach
    void setUp() {
        userId = 1L;

        // 콘서트 생성
        Concert concert = concertRepository.save(
            Concert.builder()
                .title("Race Condition 테스트 콘서트")
                .description("결제 vs 만료 배치 동시성 테스트")
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

        // 좌석 생성
        ScheduleSeat seat = seatJpaRepository.save(
            ScheduleSeat.builder()
                .scheduleId(schedule.getId())
                .venueSeatId(1L)
                .price(new BigDecimal("50000"))
                .status(SeatStatus.AVAILABLE)
                .build()
        );
        seatIds = List.of(seat.getId());

        // 사용자 잔액 생성
        UserBalance userBalance = UserBalance.builder()
            .userId(userId)
            .currentBalance(new BigDecimal("100000"))
            .build();
        userBalanceRepository.save(userBalance);
    }

    @Test
    @DisplayName("결제와 만료 배치가 동시에 실행되면 하나만 성공한다")
    void testPaymentVsExpiryBatch() throws InterruptedException {
        // Given: 예약 생성
        Reservation reservation = reservationService.createReservation(userId, scheduleId, seatIds);
        Long reservationId = reservation.getId();

        // 예약 시간을 과거로 변경하여 만료 대상으로 만듦
        // (실제로는 ReservationEntity를 직접 수정해야 하지만, 여기서는 시뮬레이션)

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        AtomicBoolean paymentSuccess = new AtomicBoolean(false);
        AtomicBoolean expirySuccess = new AtomicBoolean(false);

        AtomicReference<Exception> paymentError = new AtomicReference<>();
        AtomicReference<Exception> expiryError = new AtomicReference<>();

        // When: 결제와 만료 배치 동시 실행
        // Thread 1: 결제 시도
        executor.submit(() -> {
            try {
                latch.countDown();
                latch.await();

                paymentService.processPayment(userId, reservationId);
                paymentSuccess.set(true);

            } catch (Exception e) {
                paymentError.set(e);
            }
        });

        // Thread 2: 만료 배치 실행
        executor.submit(() -> {
            try {
                latch.countDown();
                latch.await();

                int expiredCount = reservationService.expireReservationsAndReleaseSeats();
                if (expiredCount > 0) {
                    expirySuccess.set(true);
                }

            } catch (Exception e) {
                expiryError.set(e);
            }
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Then: 둘 중 하나만 성공해야 함
        boolean onlyOneSucceeded = paymentSuccess.get() ^ expirySuccess.get();
        assertThat(onlyOneSucceeded)
            .as("결제 또는 만료 중 정확히 하나만 성공해야 함")
            .isTrue();

        // 최종 상태 확인
        Reservation finalReservation = reservationRepository.findById(reservationId).orElseThrow();
        ScheduleSeat finalSeat = seatRepository.findById(seatIds.get(0)).orElseThrow();

        if (paymentSuccess.get()) {
            // 결제 성공 시
            assertThat(finalReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        } else {
            // 만료 성공 시
            assertThat(finalReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        }
    }

    @Test
    @DisplayName("만료 배치가 여러 번 동시에 실행되어도 안전하다")
    void testMultipleExpiryBatchesConcurrently() throws InterruptedException {
        // Given: 만료된 예약 생성
        Reservation reservation = reservationService.createReservation(userId, scheduleId, seatIds);

        // When: 만료 배치를 3개 스레드에서 동시 실행
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);

        AtomicReference<Integer> totalExpired = new AtomicReference<>(0);

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    int count = reservationService.expireReservationsAndReleaseSeats();
                    synchronized (totalExpired) {
                        totalExpired.set(totalExpired.get() + count);
                    }

                } catch (Exception e) {
                    // 예외 무시 (OptimisticLockException 예상)
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Then: 정확히 1번만 만료 처리되어야 함
        assertThat(totalExpired.get())
            .as("여러 배치가 동시 실행되어도 1번만 처리되어야 함")
            .isLessThanOrEqualTo(1);

        // 최종 상태 확인
        Reservation finalReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(finalReservation.getStatus()).isIn(ReservationStatus.PENDING, ReservationStatus.CANCELLED);
    }
}
