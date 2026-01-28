# WebFlux Concurrency Study

WebFlux 비동기 병렬 처리 학습 프로젝트

---

## 시나리오: 외부 출고 데이터 수집기

### 배경

외부 API에서 출고(Outbound) 데이터를 가져와 DB에 저장하는 시스템

### 제약사항

| 항목 | 값 |
|------|-----|
| 고객사 수 | 약 200개 |
| 고객사별 일일 출고 | 1,000 ~ 30,000건 (편차 큼) |
| 일일 최대 총 출고 건수 | 약 20만 건 |
| API 1회 상세 조회 | 최대 99건 |
| 출고당 Cost | 2~3건 |
| 배치 저장 단위 | 1,000건 |

### 핵심 문제

동기 방식으로 순차 처리 시:
- 고객사 200개 × 평균 100회 API 호출 × 500ms = **약 2.7시간**
- DB 저장까지 포함하면 더 오래 걸림

---

## 데이터 구조

### Client (고객사)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK, Auto Increment |
| clientCode | String | 고객사 코드 (유니크, API 경로 식별자) |
| name | String | 고객사명 |

### Outbound (출고)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK, Auto Increment |
| outboundCode | String | 우리가 생성한 UUID (Cost 매핑용) |
| outboundId | String | 외부 API에서 받은 ID (조회/중복체크용) |
| clientCode | String | 고객사 코드 |
| orderNo | String | 주문번호 |
| status | String | 출고 상태 |
| shippedAt | LocalDateTime | 출고일시 |

### OutboundCost (출고 비용)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK, Auto Increment |
| outboundCode | String | 출고 코드 (UUID, FK) |
| costType | String | 비용 유형 (SHIPPING, PACKING 등) |
| amount | BigDecimal | 금액 |

### 관계
- Client : Outbound = 1 : N (clientCode로 연결)
- Outbound : OutboundCost = 1 : N (outboundCode로 FK 연결)

### 코드 생성 규칙
- **clientCode**: DB에 저장된 고객사 식별자 (API 경로에 사용)
- **outboundCode**: 저장 시 UUID로 생성 → Outbound와 OutboundCost 매핑에 사용
- **outboundId**: 외부 API가 제공하는 ID (A API 응답값, 조회 요청에 사용)

---

## 처리 흐름

### 동기 방식 흐름

```
1. ClientRepository.findAll() → 200개 고객사
        ↓
2. for (client in clients)                    ← 순차
   │
   ├─ A API 호출: GET /mock/{clientCode}/outbound-ids
   │  → outboundIds: ["OB-001", ..., "OB-5000"]
   │
   ├─ 99개씩 청크 분할
   │  → [["OB-001"~"OB-099"], ["OB-100"~"OB-198"], ...]
   │
   └─ for (chunk in chunks)                   ← 순차
      │
      ├─ B API 호출: POST /mock/{clientCode}/outbounds
      │  → 상세 데이터 99건 + Cost
      │
      └─ buffer에 누적
         │
         └─ if (buffer.size >= 1000)
            ├─ UUID 생성 (outboundCode)
            ├─ Outbound 배치 저장
            └─ OutboundCost 배치 저장
```

### 비동기 방식 흐름

```
1. ClientRepository.findAll() → 200개 고객사
        ↓
2. Flux.fromIterable(clients)
   .flatMap(client -> {                       ← Level 1 병렬 (N개)
      │
      ├─ A API 호출 (WebClient)
      │  → Mono<List<outboundId>>
      │
      └─ .flatMapMany { ids ->
            Flux.fromIterable(ids.chunked(99))
            .flatMap(chunk -> {                ← Level 2 병렬 (M개)
               │
               └─ B API 호출 (WebClient)
                  → Mono<List<OutboundDetail>>
            }, M)
      }
   }, N)
   .collectList()                             ← 전체 수집 (20만건)
        ↓
3. 수집 완료 후 배치 저장
   │
   └─ collected.chunked(1000).forEach { batch ->
         ├─ UUID 생성 (outboundCode)
         ├─ Outbound 배치 저장
         └─ OutboundCost 배치 저장
      }
```

### 병렬 처리 레벨
- **Level 1**: 고객사별 병렬 (최대 N개 고객사 동시 처리)
- **Level 2**: 청크별 병렬 (고객사 내에서 M개 청크 동시 처리)

---

## 배치 저장 방식 비교

### 방식 A: 스트리밍 배치 (1000건 모일 때마다 저장)

```
API 호출 → 데이터 흘러옴 → 1000건 모이면 바로 저장 → 반복
```

```kotlin
Flux.fromIterable(clients)
    .flatMap { /* API 호출 - 매우 빠름 */ }
    .buffer(1000)
    .publishOn(Schedulers.boundedElastic())   // ← 스레드 전환 필요!
    .flatMap { /* DB 저장 - 느림 (블로킹) */ }
```

#### 방식 A의 주의점: Schedulers.boundedElastic()

WebFlux는 적은 수의 스레드(보통 CPU 코어 수)로 논블로킹하게 동작한다.  
하지만 **JdbcTemplate은 블로킹**이라 DB 응답 올 때까지 스레드가 멈춘다.

WebFlux 스레드가 블로킹되면 다른 요청 처리를 못하고 전체 시스템이 멈출 수 있다.

**해결**: `Schedulers.boundedElastic()`으로 스레드를 전환해서 블로킹 작업을 별도 스레드풀에서 처리

```
[WebFlux 스레드 4개]              [boundedElastic 스레드 수백개]
        │                                  │
   API 호출 (논블로킹)              DB 저장 (블로킹)
   빠르게 처리                      느려도 괜찮음
        │                                  │
        └───── publishOn() ────────────────┘
                 스레드 전환
```

| Scheduler | 용도 | 스레드 수 |
|-----------|------|----------|
| `parallel()` | CPU 연산 | 코어 수 |
| `boundedElastic()` | 블로킹 I/O (DB, 파일) | 최대 수백 개 |
| `single()` | 순차 작업 | 1개 |

#### 방식 A의 주의점: 배압(Backpressure)

**배압**이란 생산자(Producer)가 소비자(Consumer)보다 빠를 때 발생하는 문제를 제어하는 메커니즘이다.

```
API 호출 (초당 1000건 생성)  →  DB 저장 (초당 100건 처리)
         ↓
    메모리에 계속 쌓임
         ↓
    OutOfMemoryError 💥
```

방식 A에서는 API 호출은 병렬로 빠르게 되는데, DB 저장은 느리니까 중간에 데이터가 계속 쌓인다.  
이걸 제어하려면 배압 전략을 고려해야 한다.

---

### 방식 B: 전체 수집 후 배치 (선택한 방식 ✅)

```
API 호출 (병렬) → 20만건 전부 수집 → 1000건씩 배치 저장
```

```kotlin
// 1단계: WebFlux 스레드에서 API 호출
val collected = flux.collectList().block()

// 2단계: 여기서부터는 일반 스레드 (블로킹 OK)
collected.chunked(1000).forEach { batch ->
    jdbcTemplate.batchUpdate(...)
}
```

수집과 저장이 완전히 분리되어 있어서:
- `Schedulers.boundedElastic()` 불필요
- 배압 고려 불필요

---

### 방식 비교표

| 항목 | 방식 A (스트리밍 배치) | 방식 B (전체 수집 후 배치) |
|------|----------------------|-------------------------|
| 메모리 사용 | 낮음 (1000건 유지) | 높음 (20만건 보관) |
| API 호출과 저장 | 동시 진행 | 완전 분리 |
| 진행 상황 파악 | 어려움 | 명확 (수집 완료 → 저장 시작) |
| 에러 발생 시 | 일부 이미 저장됨 | 저장 전이라 롤백 쉬움 |
| 전체 소요 시간 | 비슷하거나 약간 빠름 | 비슷 |
| 구현 복잡도 | 높음 (배압, Scheduler 고려) | 낮음 |

### 방식 B 선택 이유

1. **디버깅/모니터링 용이** - "수집 완료: 20만건, 소요시간: X초" → "저장 시작" 이렇게 단계가 명확함
2. **실제 시나리오와 일치** - 실무에서 사용했던 방식이라 경험 기반으로 학습 가능
3. **메모리 20만건** - Outbound 객체 기준 대략 100~200MB 정도로 감당 가능한 수준
4. **구현 단순** - 배압, Scheduler 전환 고려 불필요

### 방식 A가 더 나은 경우
- 데이터가 수백만 건 이상일 때
- 메모리 제약이 심할 때
- 실시간 처리가 필요할 때

---

## 비교 테스트

| 방식 | 엔드포인트 | 설명 |
|------|-----------|------|
| 동기 순차 | `POST /collect/sync` | RestTemplate + 단일 스레드 순차 |
| 동기 멀티스레드 | `POST /collect/sync-parallel` | RestTemplate + ExecutorService 병렬 |
| 비동기 병렬 | `POST /collect/async` | WebClient + Flux.flatMap 병렬 |

---

## 기술 스택

- Kotlin 2.2.21
- Spring Boot 4.0.2
- Spring WebFlux (WebClient)
- Spring MVC (RestTemplate - 비교용)
- MySQL
- JdbcTemplate (Batch Insert)

---

## 프로젝트 구조

```
src/main/kotlin/com/webflux/parallel/webfluxconcurrency/
├── WebfluxConcurrencyApplication.kt
│
├── mock/                             # 가상 외부 API
│   ├── MockOutboundController.kt
│   └── MockDataGenerator.kt
│
├── domain/                           # 엔티티
│   ├── Client.kt
│   ├── Outbound.kt
│   └── OutboundCost.kt
│
├── dto/                              # 외부 API 응답 DTO
│   ├── OutboundIdsResponse.kt
│   └── OutboundDetailResponse.kt
│
├── repository/                       # 저장소
│   ├── ClientRepository.kt
│   └── OutboundBatchRepository.kt
│
├── client/                           # 외부 API 클라이언트
│   ├── OutboundWebClient.kt          # 비동기
│   └── OutboundRestClient.kt         # 동기
│
├── service/                          # 비즈니스 로직
│   ├── OutboundSyncCollector.kt      # 동기 수집
│   └── OutboundAsyncCollector.kt     # 비동기 수집
│
└── controller/                       # API 엔드포인트
    └── CollectorController.kt
```

---

## API 명세

### Mock API (가상 외부 API)

#### A API - 출고 ID 목록 조회

```
GET /mock/{clientCode}/outbound-ids?date={date}&delay={delay}
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| clientCode | String | - | 고객사 코드 (Path) |
| date | LocalDate | - | 조회 날짜 |
| delay | long | 0 | 응답 지연 시간 (ms) |

**응답 예시:**
```json
{
  "clientCode": "CLIENT-001",
  "date": "2026-01-01",
  "totalCount": 5000,
  "outboundIds": ["OB-001", "OB-002", "OB-5000"]
}
```

#### B API - 출고 상세 조회

```
POST /mock/{clientCode}/outbounds?delay={delay}
```

**요청 Body:**
```json
{
  "outboundIds": ["OB-001", "OB-002", "OB-099"]
}
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| clientCode | String | - | 고객사 코드 (Path) |
| delay | long | 0 | 응답 지연 시간 (ms) |

**응답 예시:**
```json
{
  "data": [
    {
      "outboundId": "OB-001",
      "clientCode": "CLIENT-001",
      "orderNo": "ORD-12345",
      "status": "SHIPPED",
      "shippedAt": "2026-01-01T10:30:00",
      "costs": [
        { "costType": "SHIPPING", "amount": 3000 },
        { "costType": "PACKING", "amount": 500 }
      ]
    }
  ]
}
```

### 수집 API

```
POST /collect/sync?date={date}&delay={delay}
```
동기 순차 방식으로 전체 데이터 수집

```
POST /collect/sync-parallel?date={date}&delay={delay}&clientParallel={n}&chunkParallel={m}
```
동기 멀티스레드 방식으로 전체 데이터 수집

```
POST /collect/async?date={date}&delay={delay}&clientParallel={n}&chunkParallel={m}
```
비동기 방식으로 전체 데이터 수집

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| date | LocalDate | - | 수집 대상 날짜 |
| delay | long | 500 | API 응답 지연 시간 (ms) |
| clientParallel | int | 10 | 고객사 동시 처리 수 (Level 1) |
| chunkParallel | int | 10 | 청크 동시 처리 수 (Level 2) |

---

## 학습 포인트

1. **WebClient 비동기 호출** - block() 없이 처리
2. **ExecutorService 멀티스레드** - 동기 방식에서의 병렬 처리
3. **2단계 병렬 처리** - 고객사별 + 청크별 동시 처리
4. **Flux.flatMap + concurrency** - 병렬 호출 수 제어
5. **청크 분할** - 99개씩 ID 분할 처리
6. **대용량 응답 처리** - WebClient 버퍼 사이즈 설정
7. **배치 저장 방식 선택** - 스트리밍 vs 전체 수집 후 저장
8. **동기 vs 비동기 성능 비교** - 같은 병렬 수에서의 차이

---

## 설정 포인트

### WebClient 버퍼 사이즈
```kotlin
WebClient.builder()
    .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) } // 10MB
```

### 응답 크기 예상
| 항목 | 크기 |
|------|------|
| 출고 1건 (Cost 포함) | 약 350~450 bytes |
| 99건 응답 | 약 35~45 KB |

---

## 단순화한 것들

- ❌ 에러 시 별도 테이블 저장
- ❌ 중복 처리 로직 (단순 INSERT)
- ❌ 복잡한 트랜잭션 전략
