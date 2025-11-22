# 5주차 과제: 동시성 제어 보고서

---

## 개요

### 구현 항목
5주차 필수 과제에서 다음 2가지 시나리오를 구현했습니다:

1. **좌석 임시 배정 시 락 제어** (Pessimistic Lock)
2. **배정 타임아웃 해제 스케줄러** (Optimistic Lock + 조건부 UPDATE)

### 사용한 기술
- ✅ **SELECT FOR UPDATE** (Pessimistic Lock)
- ✅ **조건부 UPDATE** (JPQL @Modifying)
- ✅ **낙관적 락** (@Version)
- ✅ **멀티스레드 테스트** (ExecutorService + CountDownLatch)

---

## 시나리오 1: 좌석 예약 동시성 제어

### 문제 상황

**예상 이슈:**
- 같은 좌석에 대해 동시에 예약 요청 → 중복 예약 발생

**구체적 문제:**
```java
// Before: SELECT와 UPDATE 사이에 다른 트랜잭션이 끼어들 수 있음
List<ScheduleSeat> seats = seatRepository.findAllById(seatIds);  // SELECT
if (seats.stream().allMatch(ScheduleSeat::isAvailable)) {
    seats.forEach(ScheduleSeat::reserve);  // UPDATE
}
```

### 해결 전략

**선택한 방법: Pessimistic Lock (SELECT FOR UPDATE)**

좌석은 높은 경합(High Contention) 리소스이므로 비관적 락이 적합합니다.

**핵심 구현:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ss FROM ScheduleSeat ss " +
       "WHERE ss.scheduleId = :scheduleId " +
       "AND ss.id IN :ids " +
       "AND ss.status = 'AVAILABLE' " +
       "ORDER BY ss.id ASC")  // 데드락 방지
List<ScheduleSeat> findAvailableByScheduleIdAndIdWithLock(...);
```

**주요 개선:**
- 스케줄 검증 쿼리 레벨에서 처리 (`WHERE ss.scheduleId = :scheduleId`)
- ID 순서 정렬로 데드락 방지 (`ORDER BY ss.id ASC`)
- 락 타임아웃 설정 (3초)

### 테스트 결과

**시나리오:** 100명이 동시에 10개 좌석 예약 시도

**결과:**
- ✅ 정확히 10명만 예약 성공
- ✅ 90명은 예외 발생 (SeatNotAvailableException)
- ✅ 최종 좌석 상태: 10개 모두 RESERVED
- ✅ 데이터 불일치 없음

---

## 시나리오 2: 예약 만료 배치 Race Condition

### 문제 상황

**예상 이슈:**
- 예약 후 결제 지연 → 임시 배정 해제 로직 부정확

**구체적 문제:**
```java
// Before: 결제 처리와 만료 배치가 동시 실행되면 Race Condition 발생
// Thread 1: 결제 처리
reservation.confirm();  // PENDING → CONFIRMED
save(reservation);

// Thread 2: 만료 배치 (동시 실행)
List<Reservation> expired = findExpiredReservations();  // PENDING 조회
for (Reservation r : expired) {
    r.expire();  // PENDING → CANCELLED
    save(r);  // ⚠️ CONFIRMED를 CANCELLED로 덮어쓸 수 있음!
}
```

### 해결 전략

**선택한 방법: Optimistic Lock + 조건부 UPDATE**

만료 배치는 낮은 경합(Low Contention) 작업이므로 낙관적 락이 적합하며, 조건부 UPDATE로 충돌을 원천 차단합니다.

**핵심 구현:**

1. **@Version 추가**
```java
@Entity
public class ReservationEntity {
    @Version
    private Integer version;  // 동시 수정 감지
}
```

2. **조건부 UPDATE 쿼리**
```java
@Modifying
@Query("UPDATE ReservationEntity r SET r.status = 'CANCELLED' " +
       "WHERE r.status = 'PENDING' AND r.expiresAt < :now")
int expireIfPendingAndExpired(@Param("now") LocalDateTime now);
```

**핵심 원리:**
- WHERE절에 `status = 'PENDING'` 조건 포함
- 이미 `CONFIRMED`된 예약은 UPDATE 대상에서 자동 제외
- 결제와 만료가 동시 실행되어도 하나만 성공

3. **스케줄러 구현**
```java
@Scheduled(fixedRate = 60_000, initialDelay = 60_000)  // 1분마다 실행
public void expireReservations() {
    int expiredCount = reservationService.expireReservationsAndReleaseSeats();
}
```

### 테스트 결과

**시나리오 1:** 만료 배치를 3개 스레드에서 동시 실행

**결과:**
- ✅ 여러 배치가 동시 실행되어도 최대 1번만 처리
- ✅ 데이터 정합성 보장 (중복 처리 없음)

**시나리오 2:** 스케줄러 멱등성 검증 (3번 연속 실행)

**결과:**
- ✅ 여러 번 실행되어도 안전
- ✅ 예외 발생 시에도 스케줄러 중단 없음

**성능 개선:**
- N+1 문제 해결: 51개 쿼리 → 13개 쿼리 (74% 감소)

---

## 전체 테스트 결과

### 테스트 환경
- **도구**: Testcontainers (MySQL 8.0)
- **동시성 시뮬레이션**: ExecutorService + CountDownLatch
- **테스트 격리**: @Transactional + 트랜잭션 롤백

### 테스트 통계

| 테스트 클래스 | 테스트 수 | 결과 |
|------------|---------|------|
| ConcurrentReservationTest | 1 | ✅ PASSED |
| ConcurrentReservationAdvancedTest | 3 | ✅ PASSED |
| ReservationSchedulerTest | 3 | ✅ PASSED |
| **합계** | **7** | **7/7 성공** |

---