package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ConcertScheduleJpaRepository extends JpaRepository<ConcertSchedule, Long> {

    List<ConcertSchedule> findByConcertId(Long concertId);

    @Query("SELECT cs FROM ConcertSchedule cs " + 
        "WHERE cs.concertId = :concertId AND cs.performanceDate BETWEEN :fromDate AND :toDate "+
        "ORDER BY cs.performanceDate, cs.performanceTime")
    List<ConcertSchedule> findByConcertIdAndDateRange(
        @Param("concertId") Long concertId, 
        @Param("fromDate") LocalDate fromDate, 
        @Param("toDate") LocalDate toDate);
}
