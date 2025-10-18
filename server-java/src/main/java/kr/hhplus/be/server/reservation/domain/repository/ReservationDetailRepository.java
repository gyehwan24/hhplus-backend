package kr.hhplus.be.server.reservation.domain.repository;

import kr.hhplus.be.server.reservation.domain.model.ReservationDetail;

import java.util.List;

/**
 * ReservationDetail Repository (Port 역할)
 * - 도메인이 요구하는 저장소 인터페이스
 * - Infrastructure 계층이 이를 구현 (Adapter 역할)
 * - 순수한 도메인 타입만 사용
 */
public interface ReservationDetailRepository {

    ReservationDetail save(ReservationDetail detail);

    List<ReservationDetail> saveAll(List<ReservationDetail> details);

    List<ReservationDetail> findAllByReservationId(Long reservationId);
}
