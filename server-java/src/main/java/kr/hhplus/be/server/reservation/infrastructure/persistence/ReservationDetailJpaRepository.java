package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.ReservationDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationDetailJpaRepository extends JpaRepository<ReservationDetail, Long> {

    List<ReservationDetail> findByReservationId(Long reservationId);
}
