package kr.hhplus.be.server.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationDetailJpaRepository extends JpaRepository<ReservationDetailEntity, Long> {

    List<ReservationDetailEntity> findByReservationId(Long reservationId);
}
