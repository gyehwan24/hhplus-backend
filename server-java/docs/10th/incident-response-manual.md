# 콘서트 예약 시스템 장애 대응 매뉴얼

## 1. 개요

### 1.1 문서 목적
본 매뉴얼은 콘서트 예약 시스템에서 발생할 수 있는 장애 상황에 대한 대응 절차를 정의합니다.

### 1.2 시스템 구성

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│ Spring Boot │────▶│    MySQL    │
│             │     │ Application │     │   (3307)    │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────┴──────┐
                    │    Redis    │
                    │   (6379)    │
                    └─────────────┘
```

### 1.3 핵심 컴포넌트

| 컴포넌트 | 역할 | 장애 시 영향 |
|----------|------|-------------|
| Application | API 서비스 | 전체 서비스 중단 |
| MySQL | 데이터 저장 | 예약/결제 불가 |
| Redis | 대기열/캐시 | 대기열 불가, 성능 저하 |

---

## 2. 장애 등급 정의

| 등급 | 정의 | 대응 시간 | 예시 |
|------|------|----------|------|
| P1 (Critical) | 전체 서비스 중단 | 즉시 (15분 내) | 애플리케이션 다운, DB 장애 |
| P2 (High) | 핵심 기능 장애 | 30분 내 | 예약 불가, 결제 실패 |
| P3 (Medium) | 부분 기능 저하 | 2시간 내 | 조회 지연, 캐시 미스 |
| P4 (Low) | 경미한 이슈 | 24시간 내 | 로그 오류, 비핵심 기능 |

---

## 3. 장애 유형별 대응 절차

### 3.1 애플리케이션 장애

#### 3.1.1 애플리케이션 다운 (P1)

**증상:**
- Health check 실패
- API 응답 없음 (Connection Refused)

**확인 명령:**
```bash
# Health check
curl -s http://localhost:8080/actuator/health

# 프로세스 확인
ps aux | grep java

# 로그 확인
tail -100 /var/log/app/application.log
```

**대응 절차:**
1. 즉시 재시작 시도
   ```bash
   # Docker 환경
   docker-compose restart app

   # 직접 실행
   ./gradlew bootRun
   ```
2. 로그 분석으로 원인 파악
3. OOM 의심 시 힙 덤프 확인
4. 필요 시 롤백

**예방 조치:**
- Health check 모니터링 설정
- Auto-restart 설정
- 메모리 모니터링 알림

---

#### 3.1.2 응답 시간 급증 (P2)

**증상:**
- P95 응답 시간 > 1초
- 타임아웃 에러 증가

**확인 명령:**
```bash
# 스레드 덤프
jstack <PID> > thread_dump.txt

# GC 로그 확인
jstat -gc <PID> 1000 10

# DB 커넥션 확인
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

**대응 절차:**
1. 스레드 덤프로 병목 확인
2. Slow query 확인
3. GC 상태 확인
4. 트래픽 제한 (Rate Limiting)

**원인별 조치:**

| 원인 | 조치 |
|------|------|
| DB 쿼리 지연 | 인덱스 추가, 쿼리 최적화 |
| GC 지연 | 힙 크기 조정, GC 튜닝 |
| 스레드 고갈 | 스레드 풀 크기 증가 |
| 외부 API 지연 | 타임아웃 조정, 서킷브레이커 |

---

#### 3.1.3 토큰 발급 API 에러 (P2) - 부하테스트에서 발견

**증상:**
- POST /api/queue/token HTTP 500 에러
- 대기열 진입 실패
- 에러율 > 1%

**확인 명령:**
```bash
# Redis 상태 확인
redis-cli INFO stats | grep instantaneous_ops_per_sec
redis-cli INFO clients

# 애플리케이션 로그
grep "queue/token" /var/log/app/application.log | tail -50
```

**대응 절차:**
1. Redis 연결 상태 확인
2. Redisson 클라이언트 상태 확인
3. 동시 요청 수 제한 (Rate Limiting)
4. 필요 시 Redis 재시작

**즉시 완화:**
```bash
# Rate Limiting 적용 (nginx)
limit_req_zone $binary_remote_addr zone=token:10m rate=10r/s;
```

---

### 3.2 데이터베이스 장애

#### 3.2.1 MySQL 연결 불가 (P1)

**증상:**
- Connection refused
- Too many connections

**확인 명령:**
```bash
# MySQL 상태
docker exec hhplus-mysql mysqladmin -u root -p status

# 연결 수 확인
docker exec hhplus-mysql mysql -u root -p -e "SHOW STATUS LIKE 'Threads_connected';"

# 프로세스 리스트
docker exec hhplus-mysql mysql -u root -p -e "SHOW PROCESSLIST;"
```

**대응 절차:**
1. MySQL 컨테이너 상태 확인
   ```bash
   docker ps | grep mysql
   docker logs hhplus-mysql --tail 100
   ```
2. 연결 정리
   ```sql
   -- 유휴 연결 종료
   KILL <process_id>;
   ```
3. 컨테이너 재시작
   ```bash
   docker-compose restart mysql
   ```

**예방 조치:**
- max_connections 증가
- Connection Pool 모니터링
- 연결 타임아웃 설정

---

#### 3.2.2 Slow Query (P3)

**증상:**
- 특정 API 응답 지연
- DB CPU 급증

**확인 명령:**
```bash
# Slow query 로그 확인
docker exec hhplus-mysql mysql -u root -p -e "SHOW VARIABLES LIKE 'slow_query%';"

# 실행 중인 쿼리 확인
docker exec hhplus-mysql mysql -u root -p -e "SHOW FULL PROCESSLIST;"
```

**대응 절차:**
1. 문제 쿼리 식별
2. EXPLAIN으로 실행 계획 분석
3. 인덱스 추가 또는 쿼리 최적화
4. 임시로 캐시 TTL 연장

---

#### 3.2.3 Connection Pool 고갈 (P2)

**증상:**
- HikariCP timeout 에러
- "Connection is not available" 로그

**확인 명령:**
```bash
# HikariCP 메트릭
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

**대응 절차:**
1. 현재 연결 상태 확인
2. 긴 트랜잭션 확인 및 종료
3. Pool 크기 동적 조정 (재시작 필요)

**설정 변경:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 기존 3에서 증가
      connection-timeout: 30000
```

---

### 3.3 Redis 장애

#### 3.3.1 Redis 연결 불가 (P1)

**증상:**
- 대기열 기능 전체 중단
- 캐시 미스로 DB 부하 급증

**확인 명령:**
```bash
# Redis 상태
redis-cli ping

# Docker 컨테이너 상태
docker ps | grep redis
docker logs hhplus-redis --tail 100
```

**대응 절차:**
1. Redis 컨테이너 상태 확인
2. 메모리 사용량 확인
   ```bash
   redis-cli INFO memory
   ```
3. 컨테이너 재시작
   ```bash
   docker-compose restart redis
   ```
4. AOF 파일 손상 시 복구
   ```bash
   redis-check-aof --fix appendonly.aof
   ```

**Fallback:**
- 대기열: 순차 처리로 전환
- 캐시: DB 직접 조회 (성능 저하 감수)

---

#### 3.3.2 Redis 메모리 부족 (P2)

**증상:**
- OOM 에러
- 키 저장 실패

**확인 명령:**
```bash
redis-cli INFO memory
redis-cli DBSIZE
```

**대응 절차:**
1. 메모리 사용량 분석
   ```bash
   redis-cli --bigkeys
   ```
2. 만료된 키 정리
   ```bash
   redis-cli KEYS "expired:*" | xargs redis-cli DEL
   ```
3. maxmemory 증가 또는 eviction 정책 조정

---

### 3.4 트래픽 폭주

#### 3.4.1 티켓팅 오픈 시 트래픽 급증 (P2)

**증상:**
- TPS 급증 (평소 대비 10배 이상)
- 응답 시간 증가
- 에러율 상승

**대응 절차:**

**사전 대비 (D-1):**
```bash
# 1. 캐시 워밍
curl http://localhost:8080/api/concerts
curl http://localhost:8080/api/concerts/1/schedules

# 2. Redis 메모리 확보
redis-cli FLUSHDB  # 테스트 환경만!

# 3. 스케일 아웃 (가능한 경우)
docker-compose up -d --scale app=2
```

**실시간 대응:**
```bash
# 1. 모니터링 강화
watch -n 5 'curl -s http://localhost:8080/actuator/metrics/http.server.requests'

# 2. Rate Limiting 강화
# nginx 설정 또는 애플리케이션 레벨

# 3. 불필요 기능 비활성화
# 랭킹 조회 등 비핵심 API 일시 차단
```

---

## 4. 모니터링 체크리스트

### 4.1 일상 점검 (매일)

| 항목 | 확인 방법 | 정상 기준 |
|------|----------|----------|
| 애플리케이션 상태 | `curl /actuator/health` | UP |
| 에러율 | 로그 분석 | < 0.1% |
| 응답 시간 | 메트릭 확인 | P95 < 500ms |
| DB 연결 | HikariCP 메트릭 | < 80% |
| Redis 메모리 | `redis-cli INFO memory` | < 70% |

### 4.2 정기 점검 (주간)

| 항목 | 확인 내용 |
|------|----------|
| Slow Query | 1초 이상 쿼리 분석 |
| 디스크 사용량 | 로그, DB 데이터 |
| 백업 상태 | DB 백업 정상 여부 |
| 인증서 만료 | SSL 인증서 확인 |

### 4.3 알림 설정

```yaml
# Prometheus AlertManager 예시
alerts:
  - name: HighErrorRate
    condition: error_rate > 1%
    severity: P2

  - name: SlowResponse
    condition: response_time_p95 > 1s
    severity: P2

  - name: DBConnectionHigh
    condition: hikari_connections_active > 80%
    severity: P3

  - name: RedisMemoryHigh
    condition: redis_memory_used > 80%
    severity: P3
```

---

## 5. 복구 절차

### 5.1 롤백 절차

```bash
# 1. 현재 버전 확인
docker images | grep concert-reservation

# 2. 이전 버전으로 롤백
docker-compose stop app
docker tag concert-reservation:previous concert-reservation:latest
docker-compose up -d app

# 3. 정상 동작 확인
curl http://localhost:8080/actuator/health
```

### 5.2 데이터 복구

```bash
# MySQL 복구
docker exec hhplus-mysql mysql -u root -p hhplus < backup.sql

# Redis 복구 (AOF)
docker-compose stop redis
cp /backup/appendonly.aof /data/redis/
docker-compose start redis
```

---

## 6. 에스컬레이션 경로

| 단계 | 담당자 | 연락 방법 | 조건 |
|------|--------|----------|------|
| 1차 | 당직자 | Slack 알림 | 자동 알림 |
| 2차 | 개발팀 리드 | 전화 | P1 또는 30분 미해결 |
| 3차 | 인프라팀 | 전화 | 인프라 관련 또는 1시간 미해결 |
| 4차 | CTO | 전화 | 2시간 미해결 또는 P1 확대 |

---

## 7. 장애 사후 분석 (Post-Mortem)

### 7.1 템플릿

```markdown
## 장애 보고서

### 개요
- 발생 시각:
- 복구 시각:
- 영향 범위:
- 등급:

### 타임라인
- HH:MM - 장애 감지
- HH:MM - 초기 대응
- HH:MM - 원인 파악
- HH:MM - 복구 완료

### 근본 원인
(상세 기술)

### 재발 방지 대책
1. 단기 조치:
2. 장기 조치:

### 교훈
(팀 공유 사항)
```

### 7.2 재발 방지 체크리스트

- [ ] 모니터링 알림 추가/조정
- [ ] 문서 업데이트
- [ ] 자동화 스크립트 개선
- [ ] 팀 공유 및 교육

---

## 8. 부록

### 8.1 유용한 명령어 모음

```bash
# 시스템 상태
docker-compose ps
docker stats

# 애플리케이션
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/actuator/metrics | jq

# MySQL
docker exec hhplus-mysql mysql -u application -p -e "SHOW PROCESSLIST;"

# Redis
redis-cli INFO
redis-cli MONITOR  # 실시간 명령 모니터링 (주의: 성능 영향)

# 로그
docker logs hhplus-mysql --tail 100 -f
docker logs hhplus-redis --tail 100 -f
```

### 8.2 연락처

| 역할 | 이름 | 연락처 |
|------|------|--------|
| 개발팀 리드 | - | - |
| 인프라 담당 | - | - |
| DBA | - | - |

---

## 변경 이력

| 버전 | 일자 | 변경 내용 | 작성자 |
|------|------|----------|--------|
| 1.0 | 2025-12-27 | 최초 작성 | - |
