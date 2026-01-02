# Kafka 적용 분석: 콘서트 예매 시스템

## 1. 개요

콘서트 예매 시스템에서 대용량 트래픽이 발생할 수 있는 지점을 분석하고, Kafka 적용 여부를 검토한 문서입니다.

---

## 2. 대용량 트래픽 발생 지점 분석

### 2.1 트래픽 발생 시나리오

```
[티켓 오픈 시점]
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  1. 대기열 진입 (수만 명 동시 접속)                        │
│     └─→ 대기열 토큰 발급                                  │
├─────────────────────────────────────────────────────────┤
│  2. 좌석 조회 (활성 사용자 다수 조회)                      │
│     └─→ 실시간 좌석 현황 확인                             │
├─────────────────────────────────────────────────────────┤
│  3. 좌석 예약 (동시 예약 시도)                            │
│     └─→ 동일 좌석 경쟁 상황                               │
├─────────────────────────────────────────────────────────┤
│  4. 결제 처리                                            │
│     └─→ 예약 확정 및 외부 시스템 연동                      │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 지점별 Kafka 적용 분석

### 3.1 대기열 진입

| 항목 | 내용 |
|------|------|
| **현재 구현** | Redis Sorted Set (ZADD) |
| **트래픽 특성** | 순간적 대량 요청, 실시간 순위 조회 필요 |
| **Kafka 적용** | **부적합** |

#### 현재 구현 방식
```java
// TokenRedisRepository
public long addToWaitingQueue(Long userId) {
    double score = System.currentTimeMillis();
    redisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, String.valueOf(userId), score);
    return getWaitingPosition(userId);
}
```

#### Kafka가 부적합한 이유

1. **실시간 순위 조회 필요**
   - 사용자는 "현재 대기 순번"을 즉시 확인해야 함
   - Kafka는 메시지 큐로 순위 조회 기능 없음
   - Redis ZRANK: O(log N)으로 즉시 순위 반환

2. **동기 응답 필수**
   - 대기열 진입 후 즉시 대기 번호 반환 필요
   - Kafka는 비동기 처리에 적합

3. **이미 최적화됨**
   - Redis는 초당 10만+ 요청 처리 가능
   - 메모리 기반으로 지연 시간 최소화

#### 결론
```
Redis Sorted Set이 대기열 시스템의 최적 솔루션
Kafka 적용 시 오히려 복잡도 증가, 성능 저하
```

---

### 3.2 좌석 조회

| 항목 | 내용 |
|------|------|
| **현재 구현** | Redis Cache (TTL 10초) |
| **트래픽 특성** | 읽기 집중, 동일 데이터 반복 조회 |
| **Kafka 적용** | **부적합** |

#### 현재 구현 방식
```java
@Cacheable(cacheNames = "cache:seat:available", key = "#scheduleId")
public List<SeatResponse> getAvailableSeats(Long scheduleId) {
    return scheduleSeatRepository.findAvailableByScheduleId(scheduleId)
            .stream()
            .map(SeatResponse::from)
            .toList();
}
```

#### Kafka가 부적합한 이유

1. **조회(Read) 작업**
   - Kafka는 이벤트 스트리밍/메시지 전달 용도
   - 데이터 조회에는 캐시(Redis, Local Cache)가 적합

2. **실시간 데이터 필요**
   - 좌석 현황은 최신 상태 반영 필요
   - Kafka Consumer 지연 시 오래된 데이터 제공 위험

3. **이미 최적화됨**
   - 캐시 히트 시 DB 접근 없음
   - TTL 10초로 적절한 최신성 유지

#### 결론
```
캐시가 읽기 집중 트래픽의 최적 솔루션
Kafka는 조회 작업에 적합하지 않음
```

---

### 3.3 좌석 예약

| 항목 | 내용 |
|------|------|
| **현재 구현** | 분산락 (Redisson) + 비관적 락 |
| **트래픽 특성** | 동시 쓰기, 동일 자원 경쟁 |
| **Kafka 적용** | **부적합** |

#### 현재 구현 방식
```java
@DistributedLock(key = "'reservation:' + #scheduleId")
public Reservation createReservation(Long userId, Long scheduleId, List<Long> seatIds) {
    List<ScheduleSeat> seats = scheduleSeatRepository.findAllByIdWithLock(seatIds);
    // 좌석 상태 검증 및 예약 처리
}
```

#### Kafka가 부적합한 이유

1. **동기 응답 필수**
   - 예약 성공/실패를 즉시 사용자에게 알려야 함
   - Kafka 비동기 처리 시 "예약됐나요?" 상태 불명확

2. **원자성(Atomicity) 필요**
   - 좌석 상태 확인 → 예약 생성 → 좌석 상태 변경이 하나의 트랜잭션
   - Kafka 메시지 처리는 트랜잭션 보장 어려움

3. **순서 보장의 한계**
   - 동일 좌석에 대한 요청은 순서대로 처리되어야 함
   - Kafka 파티션 내 순서 보장되지만, 여러 좌석 조합 시 복잡

4. **경쟁 상태 해결 불가**
   ```
   [시나리오: 좌석 A에 대해 User1, User2 동시 예약 시도]

   Kafka 사용 시:
   - 두 요청 모두 큐에 들어감
   - Consumer가 순차 처리하더라도 첫 번째 처리 중 두 번째 사용자는 대기
   - 결국 락과 동일한 효과, 추가 복잡도만 증가

   현재 분산락 방식:
   - 첫 번째 요청이 락 획득, 즉시 처리
   - 두 번째 요청은 락 대기 후 실패 응답
   - 단순하고 명확한 처리
   ```

#### 결론
```
분산락 + DB 트랜잭션이 동시성 제어의 최적 솔루션
Kafka는 동기적 경쟁 상태 해결에 부적합
```

---

### 3.4 결제 처리 → 외부 시스템 연동

| 항목 | 내용 |
|------|------|
| **현재 구현** | Kafka Producer/Consumer |
| **트래픽 특성** | 결제 후 비동기 처리 가능 |
| **Kafka 적용** | **적합 (구현 완료)** |

#### 구현된 아키텍처
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  PaymentService │────▶│ PaymentCompleted │────▶│ ConcertRanking  │
│                 │     │     Event        │     │ EventListener   │
│  (결제 처리)      │     │                  │     │ (Redis 랭킹)     │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌──────────────────┐     ┌─────────────────┐
                        │ DataPlatform     │────▶│ Kafka Topic     │
                        │ EventListener    │     │payment.completed│
                        └──────────────────┘     └─────────────────┘
                                                        │
                                                        ▼
                                                 ┌─────────────────┐
                                                 │ DataPlatform    │
                                                 │ KafkaConsumer   │
                                                 └─────────────────┘
                                                        │
                                                        ▼
                                                 ┌─────────────────┐
                                                 │ DataPlatform    │
                                                 │ Client (외부)    │
                                                 └─────────────────┘
```

#### Kafka가 적합한 이유

1. **비동기 처리 가능**
   - 결제 완료 후 외부 시스템 전송은 즉시 응답 불필요
   - 사용자는 결제 성공 여부만 확인하면 됨

2. **시스템 간 느슨한 결합**
   - 결제 시스템과 데이터 플랫폼 독립적 운영
   - 데이터 플랫폼 장애 시에도 결제 정상 처리

3. **재처리 용이**
   - 메시지 영속성으로 실패 시 재처리 가능
   - Consumer 장애 복구 후 누락 없이 처리

4. **부하 평탄화**
   - 순간적 결제 폭주 시 Kafka가 버퍼 역할
   - Consumer는 처리 가능한 속도로 소비

#### 구현 코드
```java
// DataPlatformEventListener.java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onPaymentCompleted(PaymentCompletedEvent event) {
    PaymentCompletedMessage message = toMessage(event);
    kafkaProducer.send(message);
}

// PaymentKafkaProducer.java
public void send(PaymentCompletedMessage message) {
    String key = String.valueOf(message.reservationId());
    kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, key, message);
}

// DataPlatformKafkaConsumer.java
@KafkaListener(topics = KafkaConfig.PAYMENT_COMPLETED_TOPIC)
public void consume(ConsumerRecord<String, PaymentCompletedMessage> record) {
    dataPlatformClient.sendReservationData(record.value());
}
```

---

## 4. 기술 선택 요약

| 지점 | 현재 기술 | Kafka 적용 | 근거 |
|------|----------|-----------|------|
| 대기열 진입 | Redis ZSet | X | 실시간 순위 조회, 동기 응답 필요 |
| 좌석 조회 | Redis Cache | X | 읽기 작업, 캐시가 최적 |
| 좌석 예약 | 분산락 + DB | X | 동기 응답, 원자성, 경쟁 상태 해결 |
| 외부 연동 | **Kafka** | O | 비동기 가능, 느슨한 결합, 재처리 |

---

## 5. Kafka vs 다른 기술 비교

### 5.1 대기열: Kafka vs Redis

| 기준 | Kafka | Redis ZSet |
|------|-------|------------|
| 순위 조회 | 불가 | O(log N) |
| 지연 시간 | ms 단위 | μs 단위 |
| 처리량 | 높음 | 매우 높음 |
| 적합성 | 이벤트 스트리밍 | 실시간 랭킹/대기열 |

### 5.2 동시성 제어: Kafka vs 분산락

| 기준 | Kafka | 분산락 (Redisson) |
|------|-------|-------------------|
| 응답 시간 | 비동기 (늦음) | 동기 (즉시) |
| 트랜잭션 | 복잡 | DB 트랜잭션 활용 |
| 구현 복잡도 | 높음 | 낮음 |
| 적합성 | 비동기 작업 | 동시성 제어 |

---

## 6. 결론

### Kafka를 적용한 곳
- **결제 완료 → 데이터 플랫폼 연동**: 비동기 처리가 가능하고, 시스템 간 결합도를 낮춰야 하는 지점

### Kafka를 적용하지 않은 곳
- **대기열**: Redis가 실시간 순위 조회에 최적
- **좌석 조회**: 캐시가 읽기 집중 트래픽에 최적
- **좌석 예약**: 분산락이 동시성 제어에 최적

### 핵심 원칙
```
"모든 곳에 Kafka를 적용하는 것이 아니라,
 Kafka가 해결할 수 있는 문제에만 적용한다."

- 비동기 처리가 가능한가?
- 시스템 간 결합도를 낮춰야 하는가?
- 메시지 영속성과 재처리가 필요한가?
- 부하 평탄화가 필요한가?

위 질문에 "예"라면 Kafka 적용 검토
```
