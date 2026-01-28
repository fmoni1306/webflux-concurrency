# 가상 스레드 vs WebFlux 비교

## 📊 전체 비교표

| 기준 | 가상 스레드 (Java 21) | WebFlux |
|------|----------------------|---------|
| **기존 코드** | 동기 코드 그대로 사용 | 전부 Reactive로 변경 필요 |
| **학습 비용** | 낮음 | 높음 (Reactor, Mono, Flux) |
| **팀 숙련도** | Java 알면 OK | Reactive 경험 필요 |
| **라이브러리 호환** | JPA, JDBC 등 그대로 | R2DBC, WebClient 등 논블로킹 필수 |
| **디버깅** | 쉬움 (스택트레이스 명확) | 어려움 (체인 추적 힘듦) |
| **백프레셔** | 직접 구현 | 내장 지원 |
| **스레드 오버헤드** | 낮음 (경량 스레드) | 낮음 (이벤트 루프) |
| **블로킹 I/O** | 괜찮음 (자동 전환) | 불가 (논블로킹 필수) |

---

## 🔄 백프레셔(배압) 차이

### 백프레셔란?
> **생산자가 너무 빨리 데이터 보내면 소비자가 "잠깐 멈춰!"라고 신호 보내는 것**

### WebFlux - 내장 지원

```kotlin
Flux.range(1, 1_000_000)
    .flatMap({ id ->
        apiClient.call(id)  // 빠르게 생산
    }, 10)  // ← 동시에 10개만 처리 (백프레셔)
    .subscribe()
```

- `flatMap(concurrency)`, `limitRate()`, `buffer()` 등 내장
- 자동으로 흐름 제어
- 메모리 폭발 방지

### 가상 스레드 - 직접 구현 필요

```kotlin
Executors.newVirtualThreadPerTaskExecutor().use { executor ->
    val semaphore = Semaphore(10)  // ← 직접 구현!
    
    ids.forEach { id ->
        semaphore.acquire()
        executor.submit {
            try {
                apiClient.call(id)
            } finally {
                semaphore.release()
            }
        }
    }
}
```

- Semaphore, BlockingQueue 등 직접 구현 필요
- 안 하면 100만 개 요청 동시에 날림 → 메모리 폭발

### 백프레셔 비교

| 항목 | WebFlux | 가상 스레드 |
|------|---------|------------|
| 백프레셔 | **내장** | 직접 구현 |
| 코드 | `flatMap(10)` | `Semaphore(10)` |
| 실수 가능성 | 낮음 | 높음 (깜빡하면 터짐) |

---

## 🎯 선택 가이드

### 가상 스레드를 선택해야 할 때

- ✅ 기존 동기 코드/라이브러리 유지하고 싶을 때
- ✅ 팀에 Reactive 경험 없을 때
- ✅ 단순 I/O 병렬 처리가 목적일 때
- ✅ 빠르게 적용해야 할 때
- ✅ JPA, JDBC 등 기존 스택 사용 중일 때

### WebFlux를 선택해야 할 때

- ✅ 신규 프로젝트 + 팀이 Reactive 숙련됐을 때
- ✅ 스트리밍, 백프레셔가 중요할 때 (실시간 데이터)
- ✅ 이미 R2DBC, WebClient 등 논블로킹 스택 쓸 때
- ✅ Spring WebFlux 기반 프로젝트일 때
- ✅ 복잡한 비동기 흐름 제어가 필요할 때

---

## 💻 코드 비교

### 같은 작업을 두 방식으로 구현

**시나리오**: 1000개 API를 동시에 10개씩 호출

### WebFlux

```kotlin
Flux.fromIterable(ids)
    .flatMap({ id ->
        webClient.get()
            .uri("/api/$id")
            .retrieve()
            .bodyToMono(Response::class.java)
    }, 10)  // concurrency 10
    .collectList()
    .block()
```

### 가상 스레드

```kotlin
val semaphore = Semaphore(10)
val results = Collections.synchronizedList(mutableListOf<Response>())

Executors.newVirtualThreadPerTaskExecutor().use { executor ->
    val futures = ids.map { id ->
        executor.submit {
            semaphore.acquire()
            try {
                val response = restTemplate.getForObject("/api/$id", Response::class.java)
                results.add(response)
            } finally {
                semaphore.release()
            }
        }
    }
    futures.forEach { it.get() }
}
```

---

## 📝 결론

| 상황 | 추천 |
|------|------|
| 단순 API 병렬 호출 | **가상 스레드** (간단) |
| 실시간 스트리밍/이벤트 | **WebFlux** (적합) |
| 기존 프로젝트 성능 개선 | **가상 스레드** (변경 최소) |
| 신규 + Reactive 스택 | **WebFlux** (일관성) |

### 한 줄 요약

> **"가상 스레드 = 쉽고 실용적, WebFlux = 강력하지만 러닝커브"**

---

## 🤔 회고

이 프로젝트에서는 WebFlux를 선택했지만, 돌이켜보면 가상 스레드로 구현했어도 충분했을 것 같다.

하지만 WebFlux를 선택한 덕분에:
- Reactive 패러다임 학습
- flatMap concurrency, 백프레셔 개념 이해
- Mono, Flux 스트림 처리 경험

다음 프로젝트에서는 상황에 맞게 선택할 수 있게 됐다.
