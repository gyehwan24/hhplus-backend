아래 내용은 7주차 과제의 내용이다. 필수/선택 과제가 있고, 각 과제마다 [나의 설계] 라는 단어를 붙여서 과제에 대한 나의 설계 및 방향성을 작성할 예정이다.

> **각 시스템(랭킹, 비동기) 디자인 설계 및 개발 후 회고 내용을 담은 보고서 제출**

---

## **`[필수] Ranking Design`**

### 과제 요구사항
- (인기도) 빠른 매진 랭킹을 Redis 기반으로 개발하고 설계 및 구현

### [나의 설계]
매진 랭킹 대신에 **결제가 완료된 콘서트 랭킹**을 구현한다.
일간/주간/월간 랭킹을 구현하기 위해 Redis의 **Sorted Set**을 이용한다.

#### 키 설계 (ISO 8601 표준)
```
ranking:daily:2025-11-29      # 일간
ranking:weekly:2025-W48       # 주간 (ISO 8601 주차)
ranking:monthly:2025-11       # 월간
```

#### 데이터 구조
- **member**: `concertId` (조회 시 콘서트 정보는 기존 캐시에서 조회)
- **score**: 결제 완료 건수

#### TTL 설정
- 일간: 2일 (여유 포함)
- 주간: 8일
- 월간: 32일
- **키 생성 시점에만 TTL 설정** (`getExpire() == -1` 체크)

```java
// TTL 설정 방식
redisTemplate.opsForZSet().incrementScore(key, concertId, 1);
if (redisTemplate.getExpire(key) == -1) {  // TTL 없으면
    redisTemplate.expire(key, ttl);
}
```

#### 업데이트 방식
- **결제 완료 시점**에 이벤트 기반으로 `ZINCRBY` 호출
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- 트랜잭션 커밋 후에만 랭킹 업데이트 (롤백 시 반영 안 됨)

#### 조회 방식
- `ZREVRANGE` 명령어로 내림차순 정렬된 Top N의 concertId 조회
- concertId로 콘서트 상세 정보는 기존 캐시(`cache:concert:detail`) 활용
- 응답에 score(예약 건수)는 포함하지 않음 (내부 정렬용)

---

### [설계 결정 과정]

#### Q1. Ranking을 별도 도메인으로 분리할까?

**고민**
- 랭킹은 별도의 도메인인가? 아니면 콘서트의 한 속성인가?

**분석**
| 관점 | Ranking 별도 도메인 | Concert 도메인 포함 |
|------|---------------------|---------------------|
| 데이터 저장소 | Redis (별도) | Redis (별도) |
| 조회 결과 | 콘서트 정보 반환 | 콘서트 정보 반환 |
| API 의미 | "랭킹 조회" | "콘서트 조회 - 랭킹순" |
| 확장성 | 다른 랭킹 추가 용이 | Concert가 비대해질 수 있음 |
| 복잡도 | 파일/클래스 증가 | 단순함 |

**결정: Concert 도메인에 포함**

- "콘서트 랭킹 조회"는 결국 "콘서트 목록을 랭킹순으로 정렬해서 조회"하는 것
- 반환 타입도 `List<Concert>`
- 현재 규모에서 별도 도메인은 과도한 분리
- 랭킹은 콘서트의 **정렬 방식 중 하나**로 보는 것이 자연스러움

#### Q2. 랭킹 업데이트 시점은 언제?

**선택지**
1. 예약 생성 시점
2. 결제 완료 시점

**결정: 결제 완료 시점**

- 예약만 하고 결제를 안 하면 10분 후 만료됨
- 실제 "인기 콘서트"는 결제까지 완료된 것이 더 의미있음
- 결제 완료 = 확정된 수요

#### Q3. 이벤트 기반을 왜 선택했나?

**문제점: 직접 호출 방식**
```java
// PaymentService에서 직접 호출
public Payment processPayment(...) {
    // 결제 로직
    rankingService.incrementScore(concertId);  // ← 결합도 증가
    return payment;
}
```

**문제점**
- Payment 도메인이 Ranking(또는 Concert 랭킹)에 의존
- 트랜잭션 실패 시 Redis 롤백 불가
- 새로운 부가 기능 추가 시 PaymentService 수정 필요

**해결: 이벤트 기반**
```java
// PaymentService는 이벤트만 발행
eventPublisher.publishEvent(new PaymentCompletedEvent(...));

// 리스너에서 처리 (느슨한 결합)
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onPaymentCompleted(PaymentCompletedEvent event) {
    // 트랜잭션 커밋 후에만 실행
}
```

**장점**
- Payment와 Concert 도메인 간 결합도 감소
- 트랜잭션 커밋 후에만 랭킹 업데이트 (데이터 정합성)
- 향후 다른 부가 기능 추가 용이 (알림, 통계 등)

#### Q4. TTL 설정은 어떻게?

**문제: expire()는 존재하는 키에만 적용됨**
```java
// 잘못된 순서
if (!redisTemplate.hasKey(key)) {
    redisTemplate.expire(key, ttl);  // 키가 없으니 무효!
}
redisTemplate.opsForZSet().incrementScore(key, concertId, 1);  // 키 생성 (TTL 없이)
```

**해결: getExpire() 체크**
```java
redisTemplate.opsForZSet().incrementScore(key, concertId, 1);  // 먼저 키 생성
if (redisTemplate.getExpire(key) == -1) {  // TTL 없으면
    redisTemplate.expire(key, ttl);
}
```

- 경쟁 조건이 있어도 같은 TTL을 설정하므로 문제없음
- Lua 스크립트까지는 불필요

---

### [개발 계획]

#### Phase 1: 이벤트 시스템 구축
1. **이벤트 클래스 생성**
   - `PaymentCompletedEvent` 생성
   - 위치: `payment/domain/event/`
   - 필드: `concertId`, `scheduleId`, `userId`, `completedAt`

2. **이벤트 발행 추가**
   - `PaymentService.processPayment()`에서 결제 완료 후 이벤트 발행
   - `ApplicationEventPublisher` 주입

#### Phase 2: Concert 도메인에 랭킹 기능 추가
1. **Redis Repository 생성**
   - `ConcertRankingRedisRepository` 생성
   - 위치: `concert/infrastructure/redis/`
   - 메서드:
     - `incrementScore(concertId)`: ZINCRBY + TTL 설정 (getExpire 체크)
     - `getTopConcertIds(period, limit)`: ZREVRANGE로 concertId 목록 조회

2. **이벤트 리스너 생성**
   - `ConcertRankingEventListener` 생성
   - 위치: `concert/application/`
   - `@TransactionalEventListener(phase = AFTER_COMMIT)`
   - 일간/주간/월간 랭킹 모두 업데이트

3. **ConcertService 확장**
   - `getTopRankedConcerts(period, limit)` 메서드 추가
   - 랭킹 Redis에서 concertId 목록 조회 → 기존 캐시로 콘서트 정보 조회

#### Phase 3: API 구현
1. **랭킹 조회 API**
   - `GET /api/concerts/ranking?period=daily&limit=10`
   - period: daily, weekly, monthly
   - 응답: 콘서트 목록 (score 미포함)

#### Phase 4: 테스트
1. 이벤트 발행/수신 단위 테스트
2. Redis ZINCRBY + TTL 설정 테스트
3. 통합 테스트: 결제 → 랭킹 반영 → 조회

---

## **`[선택] Asynchronous Design`**

### 과제 요구사항
- 대기열 기능에 대해 Redis 기반의 설계를 진행하고, 적절하게 동작할 수 있도록하여 제출

### [나의 설계]
**Redis Only (대기열) + RDB (활성화 이후)** 방식을 사용한다.

#### 데이터 구조
```
대기열 ZSet: queue:waiting
  - member: userId
  - score: 진입 시각 (timestamp)

활성 ZSet: queue:active
  - member: userId
  - score: 만료 시각 (timestamp) ← 핵심!
```

#### 활성유저 만료 처리
- Redis ZSet의 TTL은 키 전체에 적용되므로 개별 유저 만료 불가
- **score = 만료 시각**으로 저장하여 개별 만료 관리
- 스케줄러에서 `ZREMRANGEBYSCORE`로 만료된 유저 정리

#### 대기열 → 활성화 프로세스
1. 대기열(Redis)에서 top 100명 pop + 활성 큐에 추가 (Lua 스크립트로 원자적)
2. RDB Token 테이블에 ACTIVE 상태로 INSERT
3. RDB 실패 시 `rollbackActivation()` 호출하여 Redis 복구

#### RDB 연동 전략
- **대기열**: Redis만 사용 (순번 조회 O(log n))
- **활성화 이후**: RDB Token 테이블에 저장 (예약/결제 시 검증용)
- 기존 `TokenService` 로직 유지하면서 Redis 추가

#### 토큰 검증 역할 분리
```
┌─ Filter/Interceptor (선택적) ─┐     ┌─ Service Layer (필수) ─┐
│ Redis 활성 큐 체크            │ →   │ RDB Token 테이블 체크   │
│ 빠른 차단 (DB 부하 감소)      │     │ 트랜잭션 정합성 보장    │
│ 유효하지 않으면 403           │     │ 예약/결제 시 최종 검증  │
└───────────────────────────────┘     └─────────────────────────┘
```
- Redis 검증은 최적화 목적 (선택적)
- RDB 검증은 트랜잭션 정합성 목적 (필수)

---

### [설계 결정 과정]

#### Q1. 왜 Redis + RDB 이중 구조인가?

**선택지**
1. Redis Only
2. RDB Only (현재)
3. Redis (대기열) + RDB (활성화 이후)

**분석**
| 방식 | 장점 | 단점 |
|------|------|------|
| Redis Only | 빠름, 단순 | 장애 시 데이터 유실, 트랜잭션 연동 어려움 |
| RDB Only | 안정적, 트랜잭션 보장 | 순번 조회 O(n), 성능 병목 |
| Redis + RDB | 빠른 순번 조회, 안정적 토큰 관리 | 복잡도 증가 |

**결정: Redis (대기열) + RDB (활성화 이후)**

- 대기열 순번 조회는 빈번함 → Redis ZRANK O(log n)
- 활성화된 토큰은 예약/결제와 연결 → RDB 트랜잭션 필요
- 기존 코드의 TokenService 로직을 크게 변경하지 않아도 됨
- Redis 장애 시에도 활성화된 토큰은 RDB에 있으므로 예약/결제 가능

#### Q2. 활성유저 만료는 어떻게?

**문제: Redis ZSet TTL의 한계**
```
Redis TTL은 키 전체에 적용됨
ZSet 내부의 개별 member에 TTL 설정 불가

만약 활성유저 ZSet에 TTL 5분 설정하면?
→ 5분 후 모든 활성유저가 한꺼번에 만료됨 (잘못된 동작)
```

**해결: score = 만료 시각**
```
ZADD queue:active {만료시각_timestamp} {userId}

유효성 검사:
score > 현재시각 → 유효
score ≤ 현재시각 → 만료

스케줄러로 정리:
ZREMRANGEBYSCORE queue:active -inf {현재시각}
```

#### Q3. 대기열 → 활성 이동의 원자성은?

**문제**
```
1. ZPOPMIN queue:waiting  ← 대기열에서 제거
   (여기서 장애 발생하면?)
2. ZADD queue:active      ← 활성 큐에 추가

→ 유저가 사라질 수 있음!
```

**해결: Lua 스크립트로 원자적 처리**
```lua
local users = redis.call('ZPOPMIN', KEYS[1], ARGV[1])
if #users > 0 then
    for i = 1, #users, 2 do
        redis.call('ZADD', KEYS[2], ARGV[2], users[i])
    end
end
return users
```

Redis는 Lua 스크립트를 단일 명령어처럼 원자적으로 실행

#### Q4. RDB 저장 실패 시?

**문제**
```
1. Redis pop + 활성 추가 (Lua) → 성공
2. RDB INSERT → 실패!
→ Redis에는 있는데 RDB에는 없는 상태
```

**해결: rollbackActivation() 메서드**
```java
@Transactional
public void activateWaitingTokens() {
    List<Long> userIds = queueRedisRepo.popAndActivate(100, expireAt);

    try {
        tokenRepository.saveAll(...);  // RDB 저장
    } catch (Exception e) {
        queueRedisRepo.rollbackActivation(userIds);  // Redis 복구
        throw e;
    }
}
```

#### Q5. 대기열 중복 진입 체크

**현재 문제: WAITING 상태 체크 없음**
```java
// 현재 코드 - ACTIVE만 체크
tokenRepository.findActiveTokenByUserId(userId)
    .ifPresent(token -> { throw ... });
```

**해결: Redis에서 중복 체크**
```java
public void addToWaitingQueue(Long userId) {
    if (isInWaitingQueue(userId) || isActiveUser(userId)) {
        throw new AlreadyInQueueException("이미 대기열에 있습니다.");
    }
    redisTemplate.opsForZSet().add(WAITING_QUEUE, userId, now);
}
```

---

### [개발 계획]

#### Phase 1: Redis Repository 구축
1. **QueueRedisRepository 생성**
   - 위치: `token/infrastructure/redis/`
   - 메서드:
     - `addToWaitingQueue(userId)`: ZADD (score = 현재시각) + 중복 체크
     - `getWaitingPosition(userId)`: ZRANK로 순번 조회
     - `isInWaitingQueue(userId)`: ZSCORE로 존재 여부 확인
     - `isActiveUser(userId)`: score 조회 후 만료 여부 확인
     - `popAndActivate(count, expireAt)`: Lua 스크립트로 원자적 이동
     - `rollbackActivation(userIds)`: RDB 실패 시 Redis 복구
     - `removeExpiredActiveUsers()`: ZREMRANGEBYSCORE로 만료 유저 삭제

2. **Lua 스크립트 작성**
   - 대기열 → 활성 큐 원자적 이동
   - `RedisScript` 클래스로 관리

#### Phase 2: TokenService 리팩토링
1. **토큰 발급 수정**
   - `issueToken()`: Redis 대기열에 추가 + 중복 체크
   - 반환값: 대기열 순번

2. **순번 조회 수정**
   - `getQueuePosition()`: Redis ZRANK 사용 (O(log n))
   - 기존 O(n) 루프 조회 제거

3. **토큰 활성화 수정**
   - `activateWaitingTokens()`:
     - Redis에서 pop + 활성 추가 (Lua 스크립트)
     - RDB Token 테이블에 ACTIVE 상태로 INSERT
     - 실패 시 rollbackActivation() 호출
   - 분산락 유지 (`@DistributedLock`)

4. **토큰 검증**
   - `validateToken()`: RDB 검증 유지 (트랜잭션 정합성)
   - Filter에서 Redis 검증은 선택적으로 추가 가능

#### Phase 3: 스케줄러 수정
1. **TokenActivationScheduler 수정**
   - 기존 로직 유지 + Redis 연동

2. **ActiveUserCleanupScheduler 추가**
   - **분산락 적용** (`@DistributedLock`)
   - 10초마다 만료된 활성 유저 정리
   - `ZREMRANGEBYSCORE queue:active -inf {현재시각}`
   - RDB Token 상태도 EXPIRED로 업데이트

#### Phase 4: API 수정
1. **토큰 발급 API**
   - 응답에 대기열 순번 포함

2. **대기열 순번 조회 API**
   - Redis ZRANK 기반으로 빠른 응답

#### Phase 5: 테스트
1. Redis 대기열 ZADD/ZRANK 테스트
2. 중복 진입 방지 테스트
3. Lua 스크립트 원자적 이동 테스트
4. RDB 실패 시 rollback 테스트
5. 활성 유저 만료 처리 테스트
6. 기존 예약/결제 플로우와 통합 테스트

---

## 기술 스택 결정사항

| 항목 | 선택 | 이유 |
|------|------|------|
| Redis 클라이언트 | Spring Data Redis (Lettuce) | 기존 설정 활용 |
| 원자적 연산 | Lua 스크립트 | 대기열→활성 이동 시 |
| 이벤트 처리 | @TransactionalEventListener | 트랜잭션 커밋 후 실행 보장 |
| 분산락 | Redisson (기존) | 스케줄러 중복 실행 방지 |

---

## 장애 대응 전략

### 랭킹 시스템
- Redis 장애 시: 빈 결과 반환 또는 에러 (서비스 영향 낮음)
- graceful degradation 적용

### 대기열 시스템
- **이미 활성화된 토큰**: RDB에 있으므로 예약/결제 가능
- **신규 대기열 진입**: Redis 장애 시 일시 중단
- **RDB Fallback은 비현실적**: 대기열 데이터가 Redis에만 있으므로 복구 불가
- **권장**: 프로덕션 환경에서는 **Redis Sentinel/Cluster** 구성으로 고가용성 확보

---

## 파일 구조 (예상)

```
server-java/src/main/java/kr/hhplus/be/server/
├── concert/
│   ├── application/
│   │   ├── ConcertService.java (수정: 랭킹 조회 추가)
│   │   └── ConcertRankingEventListener.java (신규)
│   ├── infrastructure/
│   │   └── redis/
│   │       └── ConcertRankingRedisRepository.java (신규)
│   └── interfaces/
│       └── ConcertController.java (수정: 랭킹 조회 API)
├── token/
│   ├── application/
│   │   └── TokenService.java (수정)
│   ├── infrastructure/
│   │   ├── redis/
│   │   │   └── QueueRedisRepository.java (신규)
│   │   └── persistence/
│   │       └── TokenRepositoryImpl.java
│   └── scheduler/
│       ├── TokenActivationScheduler.java (수정)
│       └── ActiveUserCleanupScheduler.java (신규)
├── payment/
│   ├── application/
│   │   └── PaymentService.java (이벤트 발행 추가)
│   └── domain/
│       └── event/
│           └── PaymentCompletedEvent.java (신규)
```
