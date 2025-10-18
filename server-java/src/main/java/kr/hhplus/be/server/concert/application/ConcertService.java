package kr.hhplus.be.server.concert.application;

import kr.hhplus.be.server.concert.application.response.ConcertScheduleResponse;
import kr.hhplus.be.server.concert.application.response.SeatResponse;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository seatRepository;

    /**
     * 예약 가능한 콘서트 일정 조회
     *
     * @param concertId 콘서트 ID
     * @param fromDate 조회 시작 날짜
     * @param toDate 조회 종료 날짜
     * @return 예약 가능한 일정 목록
     */
    public List<ConcertScheduleResponse> getAvailableSchedules(Long concertId, LocalDate fromDate, LocalDate toDate) {
        return scheduleRepository.findByConcertIdAndDateRange(concertId, fromDate, toDate)
            .stream()
            .filter(ConcertSchedule::isBookingOpen)
            .map(ConcertScheduleResponse::from)
            .toList();
    }

    /**
     * 예약 가능한 좌석 조회
     *
     * @param scheduleId 일정 ID
     * @return 예약 가능한 좌석 목록
     */
    public List<SeatResponse> getAvailableSeats(Long scheduleId) {
        return seatRepository.findByScheduleId(scheduleId)
            .stream()
            .filter(ScheduleSeat::isAvailable)
            .map(SeatResponse::from)
            .toList();    
    }
}
