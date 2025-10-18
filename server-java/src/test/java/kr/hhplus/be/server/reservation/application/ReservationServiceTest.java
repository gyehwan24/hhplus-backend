package kr.hhplus.be.server.reservation.application;

import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;
import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.repository.ReservationDetailRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ScheduleSeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationDetailRepository reservationDetailRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("예약 생성 성공 - 좌석 예약 및 예약 정보 저장")
    void createReservation_성공() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(1L, 2L);

        List<ScheduleSeat> availableSeats = List.of(
            createSeat(1L, scheduleId, SeatStatus.AVAILABLE, new BigDecimal("50000")),
            createSeat(2L, scheduleId, SeatStatus.AVAILABLE, new BigDecimal("50000"))
        );

        Reservation savedReservation = Reservation.create(userId, scheduleId, new BigDecimal("100000"));
        setReservationId(savedReservation, 1L);

        // when: Mock 설정
        when(seatRepository.findAllByIdWithLock(seatIds)).thenReturn(availableSeats);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);

        // when: 실행
        Reservation result = reservationService.createReservation(userId, scheduleId, seatIds);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100000"));

        verify(seatRepository).findAllByIdWithLock(seatIds);
        verify(reservationRepository).save(any(Reservation.class));
        verify(reservationDetailRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("좌석 개수가 맞지 않으면 예외 발생")
    void createReservation_좌석개수불일치_예외() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(1L, 2L);

        // 1개의 좌석만 조회됨 (2개 요청했지만)
        List<ScheduleSeat> foundSeats = List.of(
            createSeat(1L, scheduleId, SeatStatus.AVAILABLE, new BigDecimal("50000"))
        );

        // when: Mock 설정
        when(seatRepository.findAllByIdWithLock(seatIds)).thenReturn(foundSeats);

        // when & then
        assertThatThrownBy(() -> reservationService.createReservation(userId, scheduleId, seatIds))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("좌석");
    }

    @Test
    @DisplayName("이미 예약된 좌석이 포함되면 예외 발생")
    void createReservation_이미예약됨_예외() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(1L, 2L);

        List<ScheduleSeat> seats = List.of(
            createSeat(1L, scheduleId, SeatStatus.AVAILABLE, new BigDecimal("50000")),
            createSeat(2L, scheduleId, SeatStatus.RESERVED, new BigDecimal("50000"))  // 이미 예약됨
        );

        // when: Mock 설정
        when(seatRepository.findAllByIdWithLock(seatIds)).thenReturn(seats);

        // when & then
        assertThatThrownBy(() -> reservationService.createReservation(userId, scheduleId, seatIds))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("예약 가능 상태가 아닙니다");
    }

    @Test
    @DisplayName("좌석 목록이 비어있으면 예외 발생")
    void createReservation_좌석없음_예외() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of();

        // when & then
        assertThatThrownBy(() -> reservationService.createReservation(userId, scheduleId, seatIds))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("좌석");
    }

    @Test
    @DisplayName("예약 생성 시 좌석 상태가 RESERVED로 변경됨")
    void createReservation_좌석상태변경_확인() {
        // given
        Long userId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(1L);

        ScheduleSeat availableSeat = createSeat(1L, scheduleId, SeatStatus.AVAILABLE, new BigDecimal("50000"));
        List<ScheduleSeat> availableSeats = List.of(availableSeat);

        Reservation savedReservation = Reservation.create(userId, scheduleId, new BigDecimal("50000"));
        setReservationId(savedReservation, 1L);

        when(seatRepository.findAllByIdWithLock(seatIds)).thenReturn(availableSeats);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);

        // when: 실행
        reservationService.createReservation(userId, scheduleId, seatIds);

        // then: 
        assertThat(availableSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    // ============================================
    // 테스트 헬퍼 메서드
    // ============================================

    private ScheduleSeat createSeat(Long id, Long scheduleId, SeatStatus status, BigDecimal price) {
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(scheduleId)
            .venueSeatId(id)
            .price(price)
            .status(status)
            .build();

        // ID를 강제로 설정하기 위한 리플렉션 (테스트 용도)
        try {
            var field = ScheduleSeat.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(seat, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return seat;
    }

    private void setReservationId(Reservation reservation, Long id) {
        try {
            var field = Reservation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(reservation, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
