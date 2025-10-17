package kr.hhplus.be.server.payment.application;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.payment.domain.enums.PaymentStatus;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.user.domain.UserBalance;
import kr.hhplus.be.server.user.domain.repository.UserBalanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationDetailRepository reservationDetailRepository;

    @Mock
    private UserBalanceRepository userBalanceRepository;

    @Mock
    private ScheduleSeatRepository scheduleSeatRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("결제 성공 - 잔액 차감, 예약 확정, 좌석 확정")
    void processPayment_성공() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        BigDecimal paymentAmount = new BigDecimal("50000");

        // 예약 정보 (PENDING 상태)
        Reservation reservation = Reservation.create(userId, 1L, paymentAmount);
        setReservationId(reservation, reservationId);

        // 예약 상세 (좌석 정보)
        ReservationDetail detail1 = createReservationDetail(reservationId, 1L, new BigDecimal("25000"));
        ReservationDetail detail2 = createReservationDetail(reservationId, 2L, new BigDecimal("25000"));
        List<ReservationDetail> details = List.of(detail1, detail2);

        // 좌석 정보 (RESERVED 상태)
        ScheduleSeat seat1 = createReservedSeat(1L, new BigDecimal("25000"));
        ScheduleSeat seat2 = createReservedSeat(2L, new BigDecimal("25000"));
        List<ScheduleSeat> seats = List.of(seat1, seat2);

        // 사용자 잔액 (충분한 잔액)
        UserBalance userBalance = UserBalance.create(userId, new BigDecimal("100000"));

        // 결제 저장 결과
        Payment savedPayment = Payment.complete(reservationId, userId, paymentAmount);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationDetailRepository.findAllByReservationId(reservationId)).thenReturn(details);
        when(scheduleSeatRepository.findAllById(List.of(1L, 2L))).thenReturn(seats);
        when(userBalanceRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userBalance));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // when
        Payment result = paymentService.processPayment(reservationId, userId);

        // then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getAmount()).isEqualTo(paymentAmount);

        // 잔액 차감 검증 (도메인 로직)
        assertThat(userBalance.getCurrentBalance()).isEqualTo(new BigDecimal("50000"));

        // 예약 확정 검증 (도메인 로직)
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        // 좌석 확정 검증 (도메인 로직)
        assertThat(seats).allMatch(s -> s.getStatus() == SeatStatus.SOLD);

        // 결제 저장 검증 (유일한 명시적 save)
        verify(paymentRepository).save(any(Payment.class));

        // Dirty Checking으로 자동 저장되므로 명시적 save는 호출되지 않음
        verify(userBalanceRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(scheduleSeatRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("결제 실패 - 예약을 찾을 수 없음")
    void processPayment_실패_예약없음() {
        // given
        Long reservationId = 999L;
        Long userId = 1L;

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(reservationId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("예약을 찾을 수 없습니다");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패 - 사용자 불일치")
    void processPayment_실패_사용자불일치() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        Long anotherUserId = 2L;

        Reservation reservation = Reservation.create(anotherUserId, 1L, new BigDecimal("50000"));
        setReservationId(reservation, reservationId);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(reservationId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("예약한 사용자가 아닙니다");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패 - 이미 확정된 예약")
    void processPayment_실패_이미확정된예약() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = Reservation.create(userId, 1L, new BigDecimal("50000"));
        setReservationId(reservation, reservationId);
        reservation.confirm(); // 이미 확정

        // 예약 상세 및 좌석 mock 설정 (confirm() 호출 전까지 필요)
        ReservationDetail detail = createReservationDetail(reservationId, 1L, new BigDecimal("50000"));
        ScheduleSeat seat = createReservedSeat(1L, new BigDecimal("50000"));
        UserBalance userBalance = UserBalance.create(userId, new BigDecimal("100000"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationDetailRepository.findAllByReservationId(reservationId)).thenReturn(List.of(detail));
        when(scheduleSeatRepository.findAllById(List.of(1L))).thenReturn(List.of(seat));
        when(userBalanceRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userBalance));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(reservationId, userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("예약 대기 상태에서만");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패 - 잔액 부족")
    void processPayment_실패_잔액부족() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;
        BigDecimal paymentAmount = new BigDecimal("50000");

        Reservation reservation = Reservation.create(userId, 1L, paymentAmount);
        setReservationId(reservation, reservationId);

        ReservationDetail detail = createReservationDetail(reservationId, 1L, paymentAmount);
        ScheduleSeat seat = createReservedSeat(1L, paymentAmount);

        // 잔액 부족
        UserBalance userBalance = UserBalance.create(userId, new BigDecimal("10000"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationDetailRepository.findAllByReservationId(reservationId)).thenReturn(List.of(detail));
        when(scheduleSeatRepository.findAllById(List.of(1L))).thenReturn(List.of(seat));
        when(userBalanceRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userBalance));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(reservationId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("잔액이 부족합니다");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패 - 사용자 잔액 정보 없음")
    void processPayment_실패_사용자잔액없음() {
        // given
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = Reservation.create(userId, 1L, new BigDecimal("50000"));
        setReservationId(reservation, reservationId);

        ReservationDetail detail = createReservationDetail(reservationId, 1L, new BigDecimal("50000"));
        ScheduleSeat seat = createReservedSeat(1L, new BigDecimal("50000"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationDetailRepository.findAllByReservationId(reservationId)).thenReturn(List.of(detail));
        when(scheduleSeatRepository.findAllById(List.of(1L))).thenReturn(List.of(seat));
        when(userBalanceRepository.findByUserIdWithLock(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(reservationId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자 잔액 정보를 찾을 수 없습니다");

        verify(paymentRepository, never()).save(any());
    }

    // Helper methods
    private void setReservationId(Reservation reservation, Long id) {
        try {
            java.lang.reflect.Field field = Reservation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(reservation, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ReservationDetail createReservationDetail(Long reservationId, Long seatId, BigDecimal price) {
        return ReservationDetail.builder()
            .reservationId(reservationId)
            .seatId(seatId)
            .price(price)
            .build();
    }

    private ScheduleSeat createReservedSeat(Long id, BigDecimal price) {
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(1L)
            .venueSeatId(id)
            .price(price)
            .status(SeatStatus.AVAILABLE)
            .build();
        seat.reserve(); // RESERVED 상태로 변경
        return seat;
    }
}
