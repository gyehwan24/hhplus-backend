package kr.hhplus.be.server.concert.application;

import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * TODO: 이 메서드를 구현하세요
     * 힌트:
     * 1. scheduleRepository.findByConcertIdAndDateRange()를 호출하여 일정 목록을 가져옵니다
     * 2. stream()을 사용하여 ConcertSchedule::isBookingOpen으로 필터링합니다
     * 3. 응답 DTO로 변환합니다 (ConcertScheduleResponse::from)
     * 4. List로 수집하여 반환합니다
     */
    public Object getAvailableSchedules(Long concertId, LocalDate fromDate, LocalDate toDate) {
        // TODO: 구현하세요
        return null;
    }

    /**
     * 예약 가능한 좌석 조회
     *
     * @param scheduleId 일정 ID
     * @return 예약 가능한 좌석 목록
     *
     * TODO: 이 메서드를 구현하세요
     * 힌트:
     * 1. seatRepository.findByScheduleId()를 호출하여 좌석 목록을 가져옵니다
     * 2. stream()을 사용하여 status가 SeatStatus.AVAILABLE인 좌석만 필터링합니다
     * 3. 응답 DTO로 변환합니다 (SeatResponse::from)
     * 4. List로 수집하여 반환합니다
     */
    public Object getAvailableSeats(Long scheduleId) {
        // TODO: 구현하세요
        return null;
    }
}
