package kr.hhplus.be.server.payment.domain.repository;

import kr.hhplus.be.server.payment.domain.model.Payment;

import java.util.Optional;

/**
 * Payment Repository (Port 역할)
 * - 도메인이 요구하는 저장소 인터페이스
 * - Infrastructure 계층이 이를 구현 (Adapter 역할)
 * - 순수한 도메인 타입만 사용
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByReservationId(Long reservationId);
}
