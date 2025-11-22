package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 조건부 UPDATE: PENDING이면서 만료된 예약을 CANCELLED로 변경
     * - WHERE절에 status와 expiresAt 조건 포함
     * - @Version으로 optimistic lock 자동 적용
     * @param now 현재 시간
     * @return 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE ReservationEntity r SET r.status = 'CANCELLED' " +
           "WHERE r.status = 'PENDING' AND r.expiresAt < :now")
    int expireIfPendingAndExpired(@Param("now") LocalDateTime now);
}
