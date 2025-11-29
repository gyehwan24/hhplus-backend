# Redis 캐시 성능 비교 보고서

## 테스트 개요

| 항목 | 값 |
|------|-----|
| 테스트 일시 | 2025-11-26 02:10 ~ 02:27 KST |
| 테스트 도구 | Apache JMeter 5.x |
| 테스트 시간 | 5분 (300초) |
| 동시 사용자 | 400 threads |
| 서버 환경 | Spring Boot + HikariCP (max-pool-size: 3) |

### 테스트 시나리오
- 콘서트 목록 조회: 100 threads (캐시 TTL 5분)
- 콘서트 상세 조회: 50 threads (캐시 TTL 10분)
- 스케줄 조회: 50 threads (캐시 TTL 3분)
- 좌석 조회: 200 threads (캐시 TTL 10초)

---

## 성능 비교 결과

### 핵심 지표 비교

| 지표 | 캐시 OFF | 캐시 ON | 개선율 |
|-----|----------|---------|--------|
| **총 요청 수** | 1,214,985 | 1,320,497 | **+8.7%** |
| **평균 TPS** | 4,044.9/s | 4,400.2/s | **+8.8%** |
| **평균 응답 시간** | 16ms | 10ms | **-37.5%** |
| **최대 응답 시간** | 4,988ms | 4,643ms | **-6.9%** |
| **에러율** | 0.00% | 0.00% | 동일 |

### 시간대별 TPS 추이

#### 캐시 ON
```
0:00-0:14  -   838/s (Ramp-up)
0:14-0:44  - 3,057/s (Avg: 8ms) - 워밍업
0:44-1:14  - 4,675/s (Avg: 8ms)
1:14-1:44  - 4,651/s (Avg: 11ms)
1:44-2:14  - 4,664/s (Avg: 12ms)
2:14-2:44  - 4,790/s (Avg: 11ms)
2:44-3:14  - 4,744/s (Avg: 12ms)
3:14-3:44  - 4,789/s (Avg: 10ms)
3:44-4:14  - 4,858/s (Avg: 9ms)
4:14-4:44  - 4,776/s (Avg: 11ms)
4:44-5:00  - 4,835/s (Avg: 10ms)
최종       - 4,400/s (Avg: 10ms)
```

#### 캐시 OFF
```
0:00-0:29  - 1,464/s (Avg: 15ms) - Ramp-up
0:29-0:59  - 2,706/s (Avg: 12ms)
0:59-1:29  - 3,264/s (Avg: 15ms)
1:29-1:59  - 3,462/s (Avg: 17ms)
1:59-2:29  - 3,672/s (Avg: 16ms)
2:29-2:59  - 3,827/s (Avg: 16ms)
2:59-3:29  - 3,902/s (Avg: 16ms)
3:29-3:59  - 3,953/s (Avg: 16ms)
3:59-4:29  - 4,009/s (Avg: 16ms)
4:29-5:00  - 4,044/s (Avg: 16ms)
최종       - 4,044/s (Avg: 16ms)
```

---

## 분석

### 캐시 효과 확인

#### 1. 응답 시간 37.5% 개선
- 캐시 ON: 평균 **10ms**
- 캐시 OFF: 평균 **16ms**
- Redis 캐시 히트 시 DB 조회 생략으로 6ms 절감

#### 2. 처리량 8.8% 증가
- 캐시 ON: **4,400 TPS**
- 캐시 OFF: **4,044 TPS**
- 5분간 **105,512건** 더 많은 요청 처리

#### 3. 안정성 향상
- 최대 응답 시간 345ms 감소 (4,988ms → 4,643ms)
- 부하 상태에서도 일정한 응답 시간 유지

### 캐시 로그 확인

캐시 OFF 테스트 시 로그에서 매 요청마다 DB 조회 발생 확인:
```
Cache MISS: getAllConcerts() - DB 조회 실행
Cache MISS: getAllConcerts() - DB 조회 실행
Cache MISS: getAllConcerts() - DB 조회 실행
...
```

---

## 캐시 구성 상세

### Redis 캐시 설정
```java
// CacheConfig.java
Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

// 시나리오 1: 콘서트 목록 (5분)
cacheConfigurations.put("cache:concert:list", defaultConfig.entryTtl(Duration.ofMinutes(5)));

// 시나리오 2: 콘서트 상세 (10분)
cacheConfigurations.put("cache:concert:detail", defaultConfig.entryTtl(Duration.ofMinutes(10)));

// 시나리오 3: 콘서트 스케줄 (3분)
cacheConfigurations.put("cache:schedule:list", defaultConfig.entryTtl(Duration.ofMinutes(3)));

// 시나리오 4: 좌석 상태 (10초) - 실시간성 중요
cacheConfigurations.put("cache:seat:available", defaultConfig.entryTtl(Duration.ofSeconds(10)));
```

### 직렬화 방식
- `JdkSerializationRedisSerializer` 사용
- Response DTO에 `Serializable` 인터페이스 구현 필요

---

## 프로덕션 환경 예상 효과

| 조건 | 현재 테스트 | 프로덕션 환경 |
|------|------------|--------------|
| DB 위치 | localhost | 별도 서버 (네트워크 지연 10-50ms) |
| 쿼리 복잡도 | 단순 SELECT | JOIN, 서브쿼리 (100ms+) |
| 데이터 크기 | 소량 | 대용량 |
| 예상 개선율 | 37.5% | **2-10배** |

### 예상 시나리오

```
프로덕션 환경에서:
- DB 쿼리 시간: 100ms (네트워크 + 복잡한 쿼리)
- Redis 조회 시간: 1-5ms

캐시 히트율 90% 가정:
- 캐시 OFF: 100ms × 100% = 100ms 평균
- 캐시 ON: (5ms × 90%) + (100ms × 10%) = 14.5ms 평균
→ 약 7배 성능 향상
```

---

## 결론

### 테스트 결과 요약

| 항목 | 결과 |
|------|------|
| 처리량 개선 | **+8.8%** (4,044 → 4,400 TPS) |
| 응답 시간 개선 | **-37.5%** (16ms → 10ms) |
| 추가 처리량 | 5분간 **+105,512건** |
| 에러율 | 0% (동일) |

### 핵심 메시지

> Redis 캐시 적용으로 **응답 시간 37.5% 개선**, **처리량 8.8% 증가**를 달성했습니다.
> 프로덕션 환경에서는 DB 네트워크 지연과 복잡한 쿼리로 인해 **더 큰 효과**가 기대됩니다.

---

## 테스트 재현 방법

```bash
# 1. 캐시 ON 테스트
# application.yml: spring.cache.type=redis
./gradlew bootRun &
sleep 30
/opt/homebrew/bin/redis-cli FLUSHALL  # 로컬 Redis 초기화
jmeter -n -t docs/6th/jmeter/concert-load-test.jmx \
  -l cache-on-results.jtl \
  -e -o cache-on-report

# 2. 캐시 OFF 테스트
# application.yml: spring.cache.type=none
./gradlew bootRun &
sleep 30
jmeter -n -t docs/6th/jmeter/concert-load-test.jmx \
  -l cache-off-results.jtl \
  -e -o cache-off-report
```

### 주의사항
- 로컬 환경에 Redis가 두 개 실행될 수 있음 (Homebrew Redis + Docker Redis)
- 앱은 `localhost:6379`에 연결하므로 로컬 Redis 사용 확인 필요
- `/opt/homebrew/bin/redis-cli` 로 로컬 Redis 상태 확인

---

*Report generated: 2025-11-26*
