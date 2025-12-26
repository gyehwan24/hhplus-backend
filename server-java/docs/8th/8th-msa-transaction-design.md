# MSA 전환 시 트랜잭션 분리 설계 문서

## 1. 개요

콘서트 예약 시스템의 규모가 확장되어 MSA로 전환할 때, 도메인별 배포 단위 분리와 그에 따른 트랜잭션 처리 방안을 설계한다.

---

## 2. 현재 시스템 분석

### 2.1 현재 아키텍처 (모놀리식)

```
┌─────────────────────────────────────────────────────────┐
│                    Monolithic Application                │
├─────────────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────────┐  ┌─────────┐  ┌────────┐ │
│  │ Concert │  │ Reservation │  │ Payment │  │  User  │ │
│  └─────────┘  └─────────────┘  └─────────┘  └────────┘ │
├─────────────────────────────────────────────────────────┤
│                    Single Database                       │
└─────────────────────────────────────────────────────────┘
```

### 2.2 현재 결제 플로우 (단일 트랜잭션)

```java
@Transactional
public Payment processPayment(Long reservationId, Long userId) {
    // 1. 예약 조회 및 검증
    Reservation reservation = reservationRepository.findById(reservationId);

    // 2. 좌석 조회
    List<ScheduleSeat> seats = scheduleSeatRepository.findAllById(seatIds);

    // 3. 잔액 차감 (User 도메인)
    UserBalance userBalance = userBalanceRepository.findByUserIdWithLock(userId);
    userBalance.use(reservation.getTotalAmount());

    // 4. 예약 확정 (Reservation 도메인)
    Reservation confirmedReservation = reservation.confirm();
    reservationRepository.save(confirmedReservation);

    // 5. 좌석 확정 (Concert 도메인)
    seats.forEach(ScheduleSeat::confirm);

    // 6. 결제 저장 (Payment 도메인)
    Payment payment = Payment.complete(reservationId, userId, amount);
    return paymentRepository.save(payment);
}
```

**문제점**: 4개 도메인(User, Reservation, Concert, Payment)이 하나의 트랜잭션에서 변경됨

---

## 3. MSA 도메인 분리 설계

### 3.1 서비스 분리 단위

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Concert    │  │ Reservation  │  │   Payment    │  │     User     │
│   Service    │  │   Service    │  │   Service    │  │   Service    │
├──────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤
│ - 콘서트       │  │ - 예약        │  │ - 결제        │  │ - 잔액        │
│ - 일정        │  │ - 예약상세     │  │ - 결제이력      │  │ - 사용자정보    │
│ - 좌석        │  │              │  │              │  │              │
├──────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤
│  Concert DB  │  │Reservation DB│  │  Payment DB  │  │   User DB    │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

### 3.2 분리 기준

| 서비스 | 책임 | 분리 이유 |
|--------|------|----------|
| **Concert** | 콘서트, 일정, 좌석 관리 | 조회 트래픽 높음, 독립 스케일링 필요 |
| **Reservation** | 예약 생성/관리 | 핵심 비즈니스, 독립적 배포 필요 |
| **Payment** | 결제 처리 | 높은 안정성/보안 요구 |
| **User** | 사용자, 잔액 관리 | 인증/인가와 연계, 독립적 관리 |

---

## 4. 트랜잭션 처리의 한계

### 4.1 분산 트랜잭션의 문제

MSA에서는 **단일 트랜잭션으로 여러 서비스를 묶을 수 없다.**

```
❌ 불가능한 시나리오
@Transactional  // Concert DB + User DB + Reservation DB + Payment DB?
public void processPayment() {
    concertService.confirmSeat();      // Concert DB
    userService.deductBalance();       // User DB
    reservationService.confirm();      // Reservation DB
    paymentService.savePayment();      // Payment DB
}
```

### 4.2 2PC(Two-Phase Commit)를 사용하지 않는 이유

| 문제점 | 설명 |
|--------|------|
| **성능 저하** | 모든 참여자의 준비 완료까지 대기 |
| **가용성 감소** | 한 서비스라도 실패하면 전체 롤백 |
| **확장성 제한** | 서비스가 늘어날수록 복잡도 급증 |
| **네트워크 의존** | 분산 환경에서 신뢰성 보장 어려움 |

---

## 5. 해결 방안: Saga 패턴

### 5.1 Saga 패턴 개요

각 서비스의 로컬 트랜잭션을 순차적으로 실행하고, 실패 시 **보상 트랜잭션**으로 롤백한다.

### 5.2 Orchestration vs Choreography

| 방식 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **Choreography** | 각 서비스가 이벤트 발행/구독 | 느슨한 결합 | 흐름 파악 어려움 |
| **Orchestration** | 중앙 Orchestrator가 순서 제어 | 흐름 명확, 보상 로직 관리 용이 | 단일 장애점(SPOF) |

**선택: Orchestration**

결제 플로우는 순서가 중요하고 보상 로직이 복잡하므로 Orchestration이 적합하다.

### 5.3 SPOF(단일 장애점) 완화 방안

```
┌─────────────────────────────────────────────┐
│           Payment Orchestrator              │
│  ┌─────────────────────────────────────┐   │
│  │         Saga State Store            │   │
│  │   (DB에 상태 저장, 재시작 시 복구)    │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
              │
              ├── Active-Standby 이중화
              ├── Kubernetes 자동 복구 (Health Check)
              └── 상태 저장으로 장애 복구 가능
```

---

## 6. 결제 Saga 설계

### 6.1 작업 순서 결정

```
1. 좌석 확정 (Concert Service)
2. 잔액 차감 (User Service)
3. 예약 확정 (Reservation Service)
4. 결제 저장 (Payment Service)
```

**순서 결정 기준:**

| 순서 | 작업 | 이유 |
|------|------|------|
| 1 | 좌석 확정 | 경쟁이 가장 심한 자원, 빠른 선점 필요 |
| 2 | 잔액 차감 | 잔액 부족 시 빠른 실패(Fast Fail) |
| 3 | 예약 확정 | 내부 상태 변경, 실패 확률 낮음 |
| 4 | 결제 저장 | 모든 작업 완료의 증거, 최종 기록 |

**핵심 원칙:**
> "복구하기 어려운 작업일수록 나중에, 경쟁이 심한 자원일수록 먼저"

### 6.2 Saga 흐름도

```
┌──────────────────────────────────────────────────────────────────┐
│                     Payment Orchestrator                          │
└──────────────────────────────────────────────────────────────────┘
        │
        │ (1) 좌석 확정 요청
        ▼
┌──────────────────┐
│  Concert Service │ ──── 성공 ────┐
└──────────────────┘              │
        │                         │
        │ (2) 잔액 차감 요청         ▼
        ▼
┌──────────────────┐
│   User Service   │ ──── 성공 ────┐
└──────────────────┘              │
        │                         │
        │ (3) 예약 확정 요청         ▼
        ▼
┌──────────────────┐
│Reservation Service│ ──── 성공 ───┐
└──────────────────┘              │
        │                         │
        │ (4) 결제 저장 요청         ▼
        ▼
┌──────────────────┐
│ Payment Service  │ ──── 성공 ────→ 완료!
└──────────────────┘
```

### 6.3 보상 트랜잭션 (실패 시나리오)

| 실패 지점 | 보상 작업 |
|----------|----------|
| 좌석 확정 실패 | 없음 (첫 단계) |
| 잔액 차감 실패 | 좌석 확정 취소 |
| 예약 확정 실패 | 잔액 복구 → 좌석 확정 취소 |
| 결제 저장 실패 | 예약 취소 → 잔액 복구 → 좌석 확정 취소 |

```
실패 시 보상 흐름 (역순)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

결제 저장 실패 시:
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ 좌석 확정      │ ←─ │ 잔액 복구      │ ←─ │ 예약 취소       │
│    취소       │    │              │    │              │
└──────────────┘    └──────────────┘    └──────────────┘
      (3)                 (2)                 (1)
```

---

## 7. 서비스 간 통신 방식

### 7.1 메시지 브로커 활용

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────┐
│ Orchestrator│────▶│  Message Broker │────▶│   Service   │
│             │◀────│ (Kafka/RabbitMQ)│◀────│             │
└─────────────┘     └─────────────────┘     └─────────────┘
```

**REST 동기 호출을 피하는 이유:**
- 서비스 간 강한 결합
- 장애 전파 (한 서비스 다운 시 연쇄 실패)
- 타임아웃 관리 복잡

### 7.2 사용자 응답 방식: Request-Reply 패턴

결제는 사용자에게 즉각적인 결과를 알려야 하므로 **Request-Reply 패턴**을 사용한다.

```
┌────────┐    ┌─────────┐    ┌──────────────┐    ┌─────────┐
│ Client │───▶│   API   │───▶│ Orchestrator │───▶│ Message │
│        │    │ Gateway │    │              │    │  Queue  │
└────────┘    └─────────┘    └──────────────┘    └─────────┘
                                    │                  │
                                    │   correlationId  │
                                    │◀─────────────────┘
                                    │    (결과 수신)
                                    ▼
                             결과 응답 반환
```

**흐름:**
1. Orchestrator가 메시지 발행 시 `correlationId` 포함
2. 각 서비스는 처리 후 응답 큐에 결과 발행
3. Orchestrator는 `correlationId`로 결과 매칭
4. 모든 단계 완료 후 클라이언트에 응답

---

## 8. 멱등성(Idempotency) 처리

### 8.1 멱등성이 필요한 이유

메시지가 중복 전달될 수 있다:
- 네트워크 재시도
- 서비스 재시작
- 메시지 브로커 재전송

### 8.2 멱등성 키 설계

**클라이언트 → API:**
```
POST /api/payments
Headers:
  Idempotency-Key: "user-123-order-456-20241207-001"
```

**서비스 간 메시지:**
```json
{
  "sagaId": "SAGA-20241207-abc123",
  "stepId": "SEAT-CONFIRM-001",
  "payload": {
    "seatIds": [1, 2, 3],
    "scheduleId": 100
  }
}
```

### 8.3 sagaId만 사용하면 안 되는 이유

**문제 시나리오:**
```
1. 좌석 확정 (sagaId: abc123) ✅ 성공
2. 잔액 차감 (sagaId: abc123) ❌ 실패
3. 보상: 좌석 확정 취소
4. 재시도: 좌석 확정 (sagaId: abc123) → 이미 처리됨으로 무시!
```

**해결:**
```
첫 시도:  { "sagaId": "abc123", "stepId": "seat-confirm-v1" }
보상:     { "sagaId": "abc123", "stepId": "seat-cancel-v1" }
재시도:   { "sagaId": "abc123", "stepId": "seat-confirm-v2" }  // 새 stepId
```

### 8.4 멱등성 구현 방식

```java
@Service
public class SeatService {

    public void confirmSeat(SagaMessage message) {
        // 이미 처리된 stepId인지 확인
        if (idempotencyStore.exists(message.getStepId())) {
            return; // 중복 요청 무시
        }

        // 비즈니스 로직 수행
        seat.confirm();

        // stepId 저장
        idempotencyStore.save(message.getStepId());
    }
}
```

---

## 9. Saga 상태 관리

Saga 상태 관리는 Temporal, Axon 등 **프레임워크가 자동으로 처리**한다.

### 9.1 Saga 상태 종류

| 상태 | 설명 |
|------|------|
| `STARTED` | Saga 시작됨 |
| `IN_PROGRESS` | 단계 진행 중 |
| `COMPLETED` | 모든 단계 성공 |
| `COMPENSATING` | 보상 트랜잭션 진행 중 |
| `FAILED` | 실패 (보상 완료 후) |

### 9.2 장애 복구

프레임워크가 자동으로 처리:
1. 각 단계 완료 시 상태 자동 저장
2. 서버 재시작 시 미완료 Saga 자동 감지
3. 마지막 성공 단계부터 재개 또는 보상 트랜잭션 실행

---

## 10. 전체 아키텍처

```
                            ┌─────────────────────────────────────┐
                            │            API Gateway              │
                            └─────────────────────────────────────┘
                                            │
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Payment Orchestrator                                │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │   Saga State    │  │  Step Executor  │  │ Compensation    │             │
│  │     Store       │  │                 │  │    Handler      │             │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────┘
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    │                       │                       │
                    ▼                       ▼                       ▼
        ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
        │  Message Broker │     │  Message Broker │     │  Message Broker │
        │   (Request)     │     │   (Request)     │     │   (Request)     │
        └────────┬────────┘     └────────┬────────┘     └────────┬────────┘
                 │                       │                       │
                 ▼                       ▼                       ▼
        ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
        │ Concert Service │     │  User Service   │     │  Reservation    │
        │                 │     │                 │     │    Service      │
        └─────────────────┘     └─────────────────┘     └─────────────────┘
                 │                       │                       │
                 ▼                       ▼                       ▼
        ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
        │   Concert DB    │     │    User DB      │     │  Reservation DB │
        └─────────────────┘     └─────────────────┘     └─────────────────┘
```

---

## 11. 실무 적용 시 고려사항

### 11.1 점진적 도입 전략

| 단계 | 적용 범위 | 설명 |
|------|----------|------|
| 1단계 | 모놀리식 유지 + 이벤트 도입 | 내부 이벤트로 관심사 분리 |
| 2단계 | 핵심 서비스만 분리 | Payment, User 먼저 분리 |
| 3단계 | 전체 MSA 전환 | 모든 도메인 분리 |

### 11.2 모니터링 필수 요소

- Saga 성공/실패율
- 각 단계별 소요 시간
- 보상 트랜잭션 발생 빈도
- 멱등성 키 중복 요청 비율

### 11.3 Saga 프레임워크 선택 가이드

실무에서는 Saga 상태 관리를 직접 구현하기보다 **검증된 프레임워크**를 사용한다.

| 프레임워크 | 언어 | 특징 | 적합한 상황 |
|-----------|------|------|------------|
| **Temporal** | Java, Go, etc | 강력한 상태 관리, 자동 재시도/복구 | 복잡한 워크플로우, 대규모 시스템 |
| **Axon Framework** | Java/Kotlin | CQRS + Event Sourcing + Saga 통합 | Spring 기반, 이벤트 소싱 도입 시 |
| **AWS Step Functions** | 서버리스 | AWS 관리형, 시각적 워크플로우 편집 | AWS 환경, 빠른 구축 필요 시 |
| **Eventuate Tram** | Java | 경량, Kafka 기반 | 중소규모, 심플한 Saga |

**프레임워크가 자동으로 해주는 것:**
- Saga 상태 저장/복구
- 실패 시 자동 재시도
- 보상 트랜잭션 관리
- 서버 재시작 시 중단점부터 재개

**Temporal 코드 예시:**
```java
@WorkflowImpl
public class PaymentWorkflowImpl implements PaymentWorkflow {

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Temporal이 각 단계 상태를 자동 저장
        concertActivity.confirmSeat(request.getSeatIds());
        userActivity.deductBalance(request.getUserId(), request.getAmount());
        reservationActivity.confirm(request.getReservationId());
        return paymentActivity.save(request);
    }
}
// 중간에 서버가 죽어도 재시작 시 자동으로 마지막 단계부터 재개!
```

**상황별 권장:**
- **Java + Spring 환경**: Temporal 또는 Axon Framework
- **AWS 올인**: Step Functions
- **심플하게 시작**: Eventuate Tram

---

## 12. 결론

> **마이크로서비스 환경에서는 분산 환경의 특성상 단일 ACID 트랜잭션을 적용할 수 없으므로, Saga 패턴을 활용하여 최종적 일관성(Eventual Consistency)을 보장한다.**

### 12.1 핵심 설계 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| Saga 방식 | Orchestration | 흐름 명확, 보상 로직 관리 용이 |
| 작업 순서 | 좌석→잔액→예약→결제 | 경쟁 자원 먼저, 최종 기록 마지막 |
| 통신 방식 | 메시지 브로커 + Request-Reply | 느슨한 결합 + 즉각 응답 |
| 멱등성 | sagaId + stepId | 재시도/보상 구분 가능 |

### 12.2 트레이드오프

**얻는 것:**
- 서비스별 독립 배포/스케일링
- 장애 격리
- 기술 스택 다양화 가능

**잃는 것:**
- 즉각적인 일관성 (최종 일관성으로 대체)
- 운영 복잡도 증가
- 디버깅 난이도 증가

> MSA 전환은 "은탄환"이 아니다. 시스템 규모와 팀 역량을 고려하여 점진적으로 도입해야 한다.
