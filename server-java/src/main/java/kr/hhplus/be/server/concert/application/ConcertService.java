package kr.hhplus.be.server.concert.application;

import kr.hhplus.be.server.concert.application.response.ConcertResponse;
import kr.hhplus.be.server.concert.application.response.ConcertScheduleResponse;
import kr.hhplus.be.server.concert.application.response.SeatResponse;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.ScheduleSeat;
import kr.hhplus.be.server.concert.domain.repository.ConcertRepository;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.domain.repository.ScheduleSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

import java.time.LocalDate;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;
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
    @Cacheable(cacheNames = "cache:schedule:list", key = "#concertId + ':' + #fromDate + ':' + #toDate")
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
    @Cacheable(cacheNames = "cache:seat:available", key = "#scheduleId", sync = true)
    public List<SeatResponse> getAvailableSeats(Long scheduleId) {
        return seatRepository.findByScheduleId(scheduleId)
            .stream()
            .filter(ScheduleSeat::isAvailable)
            .map(SeatResponse::from)
            .toList();
    }

    /**
     * 전체 콘서트 목록 조회
     *
     * @return 콘서트 목록
     */
    @Cacheable(cacheNames = "cache:concert:list", key = "'all'")
    public List<ConcertResponse> getAllConcerts() {
        return concertRepository.findAll()
            .stream()
            .map(ConcertResponse::from)
            .toList();
    }

    /**
     * 콘서트 상세 조회
     *
     * @param concertId 콘서트 ID
     * @return 콘서트 상세 정보
     * @throws IllegalArgumentException 콘서트가 존재하지 않는 경우
     */
    @Cacheable(cacheNames = "cache:concert:detail", key = "#concertId")
    public ConcertResponse getConcertById(Long concertId) {
        return concertRepository.findById(concertId)
            .map(ConcertResponse::from)
            .orElseThrow(() -> new IllegalArgumentException("Concert not found: " + concertId));
    }
}
