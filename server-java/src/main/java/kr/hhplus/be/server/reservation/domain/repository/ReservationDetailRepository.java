package kr.hhplus.be.server.reservation.domain.repository;

import kr.hhplus.be.server.reservation.domain.ReservationDetail;

import java.util.List;

public interface ReservationDetailRepository {

    ReservationDetail save(ReservationDetail detail);

    List<ReservationDetail> saveAll(List<ReservationDetail> details);

    List<ReservationDetail> findByReservationId(Long reservationId);
}
