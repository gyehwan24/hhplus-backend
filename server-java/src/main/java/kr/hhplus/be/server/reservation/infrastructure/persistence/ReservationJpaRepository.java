package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findByUserId(Long userId);

    /**
     * 만료된 예약 조회
     * @param now 현재 시간
     * @param status 예약 상태 (PENDING)
     * @return 만료된 예약 엔티티 목록
     */
    @Query("SELECT r FROM ReservationEntity r WHERE r.expiresAt < :now AND r.status = :status")
    List<ReservationEntity> findExpiredReservations(@Param("now") LocalDateTime now, @Param("status") ReservationStatus status);
}
