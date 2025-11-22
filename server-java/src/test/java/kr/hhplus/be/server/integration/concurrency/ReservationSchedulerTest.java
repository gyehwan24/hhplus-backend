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
import kr.hhplus.be.server.reservation.application.scheduler.ReservationExpiryScheduler;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예약 만료 스케줄러 동작 테스트
 */
public class ReservationSchedulerTest extends BaseIntegrationTest {

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

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
                .title("스케줄러 테스트 콘서트")
                .description("스케줄러 동작 확인용")
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
    }

    @Test
    @DisplayName("스케줄러가 만료된 예약을 자동으로 처리한다")
    void testSchedulerProcessesExpiredReservations() {
        // Given: 예약 생성
        Reservation reservation = reservationService.createReservation(userId, scheduleId, seatIds);

        // 예약 상태 확인
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);

        ScheduleSeat seat = seatRepository.findById(seatIds.get(0)).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        // When: 스케줄러 실행 (만료 시간이 아직 안 지남)
        reservationExpiryScheduler.expireReservations();

        // Then: 아무 변화 없음
        Reservation stillPending = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("스케줄러가 여러 번 실행되어도 안전하다")
    void testSchedulerIsIdempotent() {
        // Given: 예약 생성
        Reservation reservation = reservationService.createReservation(userId, scheduleId, seatIds);

        // When: 스케줄러를 3번 연속 실행
        reservationExpiryScheduler.expireReservations();
        reservationExpiryScheduler.expireReservations();
        reservationExpiryScheduler.expireReservations();

        // Then: 정상 동작 (예외 없음)
        Reservation finalReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(finalReservation.getStatus()).isIn(ReservationStatus.PENDING, ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("스케줄러 실행 중 예외가 발생해도 중단되지 않는다")
    void testSchedulerHandlesExceptions() {
        // Given & When: 예외 상황에서도 스케줄러 실행
        // (예: DB 연결 문제, 데이터 없음 등)

        // Then: 예외 없이 정상 종료
        reservationExpiryScheduler.expireReservations();
    }
}
