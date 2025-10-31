package kr.hhplus.be.server.concert.domain.repository;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConcertScheduleRepository {
    Optional<ConcertSchedule> findById(Long id);
    List<ConcertSchedule> findByConcertId(Long concertId);
    List<ConcertSchedule> findByConcertIdAndDateRange(Long concertId, LocalDate fromDate, LocalDate toDate);
}
