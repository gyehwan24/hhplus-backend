package kr.hhplus.be.server.concert.application;

import kr.hhplus.be.server.concert.application.response.ConcertScheduleResponse;
import kr.hhplus.be.server.concert.application.response.SeatResponse;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus;
import kr.hhplus.be.server.concert.domain.enums.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertScheduleRepository scheduleRepository;

    @Mock
    private ScheduleSeatRepository seatRepository;

    @InjectMocks
    private ConcertService concertService;

    @Test
    @DisplayName("예약 가능한 일정만 조회")
    void getAvailableSchedules_예약가능_필터링() {
        // given
        Long concertId = 1L;
        LocalDate fromDate = LocalDate.now();
        LocalDate toDate = fromDate.plusMonths(1);

        List<ConcertSchedule> mockSchedules = List.of(
            createSchedule(1L, fromDate, true),
            createSchedule(2L, fromDate.plusDays(1), false)
        );

        when(scheduleRepository.findByConcertIdAndDateRange(concertId, fromDate, toDate))
            .thenReturn(mockSchedules);

        // when
        List<ConcertScheduleResponse> result = concertService.getAvailableSchedules(concertId, fromDate, toDate);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).concertId()).isEqualTo(1L);

        // Repository 호출 검증
        verify(scheduleRepository).findByConcertIdAndDateRange(concertId, fromDate, toDate);
    }

    @Test
    @DisplayName("예약 불가능한 일정은 제외")
    void getAvailableSchedules_예약불가_제외() {
        // given
        Long concertId = 1L;
        LocalDate fromDate = LocalDate.now();
        LocalDate toDate = fromDate.plusMonths(1);

        // 모두 예약 불가능
        List<ConcertSchedule> mockSchedules = List.of(
            createSchedule(1L, fromDate, false),
            createSchedule(2L, fromDate.plusDays(1), false)
        );

        when(scheduleRepository.findByConcertIdAndDateRange(concertId, fromDate, toDate))
            .thenReturn(mockSchedules);

        // when
        List<ConcertScheduleResponse> result = concertService.getAvailableSchedules(concertId, fromDate, toDate);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("예약 가능한 좌석만 조회")
    void getAvailableSeats_예약가능_필터링() {
        // given
        Long scheduleId = 1L;

        List<ScheduleSeat> mockSeats = List.of(
            createSeat(1L, scheduleId, SeatStatus.AVAILABLE),
            createSeat(2L, scheduleId, SeatStatus.RESERVED),  // 예약됨
            createSeat(3L, scheduleId, SeatStatus.AVAILABLE)
        );

        when(seatRepository.findByScheduleId(scheduleId))
            .thenReturn(mockSeats);

        // when
        List<SeatResponse> result = concertService.getAvailableSeats(scheduleId);

        // then
        assertThat(result).hasSize(2);
        verify(seatRepository).findByScheduleId(scheduleId);
    }

    @Test
    @DisplayName("모든 좌석이 예약된 경우 빈 리스트 반환")
    void getAvailableSeats_모두예약됨_빈리스트() {
        // given
        Long scheduleId = 1L;

        List<ScheduleSeat> mockSeats = List.of(
            createSeat(1L, scheduleId, SeatStatus.RESERVED),
            createSeat(2L, scheduleId, SeatStatus.SOLD)
        );

        when(seatRepository.findByScheduleId(scheduleId))
            .thenReturn(mockSeats);

        // when
        List<SeatResponse> result = concertService.getAvailableSeats(scheduleId);

        // then
        assertThat(result).isEmpty();
    }

    // === 테스트 헬퍼 메서드 ===

    private ConcertSchedule createSchedule(Long id, LocalDate date, boolean isOpen) {
        ConcertSchedule schedule = ConcertSchedule.builder()
            .concertId(1L)
            .venueId(1L)
            .performanceDate(date)
            .performanceTime(LocalTime.of(19, 0))
            .bookingOpenAt(isOpen ? LocalDateTime.now().minusHours(1) : LocalDateTime.now().plusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusDays(1))
            .maxSeatsPerUser(4)
            .status(ScheduleStatus.AVAILABLE)
            .build();

        // ID를 강제로 설정하기 위한 리플렉션 (테스트 용도)
        // 실제 구현에서는 JPA가 자동으로 할당
        try {
            var field = ConcertSchedule.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(schedule, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return schedule;
    }

    private ScheduleSeat createSeat(Long id, Long scheduleId, SeatStatus status) {
        ScheduleSeat seat = ScheduleSeat.builder()
            .scheduleId(scheduleId)
            .venueSeatId(id)
            .price(new BigDecimal("50000"))
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
}
