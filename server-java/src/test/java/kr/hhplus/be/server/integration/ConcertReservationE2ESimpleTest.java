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
import kr.hhplus.be.server.payment.application.PaymentService;
import kr.hhplus.be.server.payment.domain.enums.PaymentStatus;
import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.token.application.TokenService;
import kr.hhplus.be.server.token.domain.Token;
import kr.hhplus.be.server.token.domain.TokenStatus;
import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E 통합 테스트: 토큰 발급 → 예약 → 결제 전체 플로우
 */
@DisplayName("콘서트 예약 E2E 통합 테스트")
class ConcertReservationE2ESimpleTest extends BaseIntegrationTest {

    @Autowired 
    private TokenService tokenService;

    @Autowired
    private ReservationService reservationService;
    
    @Autowired 
    private PaymentService paymentService;
    
    @Autowired 
    private UserBalanceRepository userBalanceRepository;
    
    @Autowired 
    private ScheduleSeatRepository seatRepository;
    
    @Autowired 
    private ConcertRepository concertRepository;
    
    @Autowired 
    private ConcertScheduleJpaRepository scheduleJpaRepository;
    
    @Autowired 
    private ScheduleSeatJpaRepository seatJpaRepository;
    
    @Autowired 
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("토큰 발급 → 좌석 예약 요청 → 결제 완료 전체 플로우 성공")
    void completeReservationFlow_Success() {
        // ===== Given: 테스트 데이터 준비 =====
        Long userId = 999L;

        // 사용자 잔액 (50,000원)
        userBalanceRepository.save(
            UserBalance.builder().userId(userId).currentBalance(new BigDecimal("50000")).build()
        );

        // 콘서트 & 일정
        Concert concert = concertRepository.save(
            Concert.builder().title("Test Concert").description("Test").build()
        );
        ConcertSchedule schedule = scheduleJpaRepository.save(
            ConcertSchedule.builder()
                .concertId(concert.getId())
                .performanceDate(LocalDateTime.now().plusDays(7).toLocalDate())
                .performanceTime(LocalDateTime.now().plusDays(7).toLocalTime())
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(7))
                .build()
        );

        // 좌석 (10,000원)
        ScheduleSeat seat = seatJpaRepository.save(
            ScheduleSeat.builder()
                .scheduleId(schedule.getId())
                .venueSeatId(1L)
                .price(new BigDecimal("10000"))
                .status(SeatStatus.AVAILABLE)
                .build()
        );

        // ===== When & Then: 전체 플로우 =====

        // Step 1: 토큰 발급
        Token token = tokenService.issueToken(userId);
        assertThat(token.getStatus()).isEqualTo(TokenStatus.WAITING);

        // Step 2: 토큰 활성화
        tokenService.activateWaitingTokens();
        Token activeToken = tokenService.getTokenByValue(token.getTokenValue());
        assertThat(activeToken.getStatus()).isEqualTo(TokenStatus.ACTIVE);

        // Step 3: 토큰 검증
        tokenService.validateToken(activeToken.getTokenValue()); // 예외 없으면 성공

        // Step 4: 좌석 예약
        Reservation reservation = reservationService.createReservation(
            userId, schedule.getId(), List.of(seat.getId())
        );
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10000"));

        // Step 5: 결제 처리
        Payment payment = paymentService.processPayment(reservation.getId(), userId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("10000"));

        // ===== Then: 최종 상태 검증 =====

        // 예약 상태: CONFIRMED
        Reservation confirmedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(confirmedReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        // 좌석 상태: SOLD
        ScheduleSeat soldSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(soldSeat.getStatus()).isEqualTo(SeatStatus.SOLD);

        // 잔액: 40,000원
        UserBalance updatedBalance = userBalanceRepository.findByUserId(userId).orElseThrow();
        assertThat(updatedBalance.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("40000"));
        assertThat(updatedBalance.getTotalUsed()).isEqualByComparingTo(new BigDecimal("10000"));
    }
}
