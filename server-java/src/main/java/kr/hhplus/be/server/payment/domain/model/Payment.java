package kr.hhplus.be.server.payment.domain.model;

import kr.hhplus.be.server.payment.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 도메인 모델 (순수 POJO)
 * - JPA 어노테이션 제거
 * - 프레임워크에 의존하지 않는 순수 비즈니스 로직
 * - 불변 객체 (final 필드)
 */
public class Payment {

    private final Long id;
    private final Long reservationId;
    private final Long userId;
    private final BigDecimal amount;
    private final PaymentStatus status;
    private final LocalDateTime paidAt;
    private final String failureReason;

    /**
     * private 생성자 - 외부에서 직접 생성 불가
     */
    private Payment(Long id, Long reservationId, Long userId, BigDecimal amount,
                   PaymentStatus status, LocalDateTime paidAt, String failureReason) {
        this.id = id;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
        this.failureReason = failureReason;
    }

    /**
     * 결제 완료 생성 (팩토리 메서드)
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @return 결제 완료 객체
     */
    public static Payment complete(Long reservationId, Long userId, BigDecimal amount) {
        validatePaymentCreation(reservationId, userId, amount);

        return new Payment(
            null,  // ID는 저장 시 할당
            reservationId,
            userId,
            amount,
            PaymentStatus.COMPLETED,
            LocalDateTime.now(),
            null
        );
    }

    /**
     * 결제 실패 생성 (팩토리 메서드)
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param failureReason 실패 사유
     * @return 결제 실패 객체
     */
    public static Payment fail(Long reservationId, Long userId, BigDecimal amount, String failureReason) {
        validatePaymentCreation(reservationId, userId, amount);

        return new Payment(
            null,
            reservationId,
            userId,
            amount,
            PaymentStatus.FAILED,
            null,
            failureReason
        );
    }

    /**
     * 결제 취소 (불변 객체 - 새 인스턴스 반환)
     * @return 취소된 새로운 결제 객체
     */
    public Payment cancel() {
        return new Payment(
            this.id,
            this.reservationId,
            this.userId,
            this.amount,
            PaymentStatus.CANCELLED,
            this.paidAt,
            this.failureReason
        );
    }

    /**
     * 영속성 계층에서 저장된 데이터를 도메인 객체로 재구성 (Reconstitute Pattern)
     * - 비즈니스 로직 없이 순수하게 상태만 복원
     * - Infrastructure 계층 전용 메서드
     * - 이미 저장된 데이터는 유효하다고 가정하므로 검증 없이 생성
     *
     * @param id 저장된 ID
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param status 결제 상태 (모든 상태 허용)
     * @param paidAt 결제 시간
     * @param failureReason 실패 사유
     * @return 재구성된 도메인 객체
     */
    public static Payment reconstitute(Long id, Long reservationId, Long userId,
                                       BigDecimal amount, PaymentStatus status,
                                       LocalDateTime paidAt, String failureReason) {
        return new Payment(id, reservationId, userId, amount, status, paidAt, failureReason);
    }

    /**
     * 결제 완료 여부 확인
     * @return 결제 완료 여부
     */
    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    /**
     * 결제 생성 시 공통 검증
     */
    private static void validatePaymentCreation(Long reservationId, Long userId, BigDecimal amount) {
        if (reservationId == null) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
