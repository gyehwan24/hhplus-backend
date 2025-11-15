package kr.hhplus.be.server.reservation.domain.repository;

import kr.hhplus.be.server.reservation.domain.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reservation Repository (Port 역할)
 * - 도메인이 요구하는 저장소 인터페이스
 * - Infrastructure 계층이 이를 구현 (Adapter 역할)
 * - 순수한 도메인 타입만 사용
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(Long id);

    Optional<Reservation> findByUserId(Long userId);

    /**
     * 만료된 예약 목록 조회
     * @param now 현재 시간
     * @return 만료 시간이 지났지만 아직 PENDING 상태인 예약 목록
     */
    List<Reservation> findExpiredReservations(LocalDateTime now);

    /**
     * 조건부 UPDATE: PENDING 상태이면서 만료된 예약을 CANCELLED로 변경
     * - 결제와 만료 배치의 race condition 방지
     * - @Version을 통한 optimistic lock 적용
     * @param now 현재 시간
     * @return 업데이트된 예약 수
     */
    int expireIfPendingAndExpired(LocalDateTime now);
}
