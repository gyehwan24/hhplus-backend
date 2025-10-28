package kr.hhplus.be.server.payment.infrastructure.persistence;

import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Payment Repository 구현체 (Adapter)
 * - PaymentRepository 인터페이스의 구현체로 Adapter 역할 수행
 * - Domain ↔ JPA Entity 변환 책임
 * - 기술적 세부사항 격리
 */
@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = PaymentEntity.from(payment);
        PaymentEntity saved = jpaRepository.save(entity);

        // 불변성 준수: 새로운 인스턴스 반환 (기존 객체 수정 없음)
        return saved.toDomain();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id)
            .map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByReservationId(Long reservationId) {
        return jpaRepository.findByReservationId(reservationId)
            .map(PaymentEntity::toDomain);
    }
}
