package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.ConcertRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.concert.infrastructure.persistence.ConcertScheduleJpaRepository;
import kr.hhplus.be.server.concert.infrastructure.persistence.ScheduleSeatJpaRepository;
import kr.hhplus.be.server.common.BaseIntegrationTest;
import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.token.application.TokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좌석 만료 통합 테스트
 * 예약 만료 시 좌석 재예약 가능 확인
 */
class SeatExpiryIntegrationTest extends BaseIntegrationTest {

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
    private ReservationRepository reservationRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("예약 만료 후 다른 사용자가 동일 좌석 재예약 성공")
    void expiredReservation_ReleasedSeat_CanBeReservedByAnotherUser() {
        // Given: 콘서트와 좌석 준비
        Concert concert = concertRepository.save(
            Concert.builder()
                .title("Test Concert")
                .description("Expiry Test")
                .build()
        );

        ConcertSchedule schedule = scheduleJpaRepository.save(
            ConcertSchedule.builder()
                .concertId(concert.getId())
                .venueId(1L)
                .performanceDate(LocalDateTime.now().plusDays(30).toLocalDate())
                .performanceTime(LocalDateTime.now().plusDays(30).toLocalTime())
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(20))
                .maxSeatsPerUser(4)
                .status(ScheduleStatus.AVAILABLE)
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

        // 첫 번째 사용자 토큰 발급 및 활성화
        Long firstUserId = 100L;
        tokenService.issueToken(firstUserId);
        tokenService.activateWaitingTokens();

        // When: 첫 번째 사용자가 예약
        Reservation firstReservation = reservationService.createReservation(
            firstUserId,
            schedule.getId(),
            List.of(seat.getId())
        );

        // Then: 예약 성공 및 좌석 RESERVED 상태 확인
        assertThat(firstReservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        ScheduleSeat reservedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        // Given: 예약 만료 시간을 과거로 강제 변경 (테스트용)
        jdbcTemplate.update(
            "UPDATE reservations SET expires_at = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(1),  // 1분 전으로 설정
            firstReservation.getId()
        );

        // When: 만료 처리 실행
        int expiredCount = reservationService.expireReservationsAndReleaseSeats();

        // Then: 예약 만료 및 좌석 해제 확인
        assertThat(expiredCount).isEqualTo(1);

        Reservation expiredReservation = reservationRepository.findById(firstReservation.getId()).orElseThrow();
        assertThat(expiredReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        ScheduleSeat releasedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // When: 두 번째 사용자가 동일 좌석 재예약
        Long secondUserId = 200L;
        tokenService.issueToken(secondUserId);
        tokenService.activateWaitingTokens();

        Reservation secondReservation = reservationService.createReservation(
            secondUserId,
            schedule.getId(),
            List.of(seat.getId())
        );

        // Then: 재예약 성공
        assertThat(secondReservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(secondReservation.getUserId()).isEqualTo(secondUserId);

        ScheduleSeat reReservedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(reReservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }
}
