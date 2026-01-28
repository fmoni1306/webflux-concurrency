# 출고 데이터 수집 스케줄러 성능 개선 분석

## 📊 성능 측정 결과

### 전체 현황
| 항목 | 값 |
|------|-----|
| 총 수집건수 | 85,366건 |
| API 호출수 | 865회 |
| 수집시간 | 274초 (4.5분) |
| 클라이언트 최대 동시실행 | 2 |
| 청크 최대 동시실행 | 62 |

동기 순차시 대략 4시간 소요됨

### 저장 로직 상세 (5,000건 기준)
| 단계 | 소요시간 | 비고 |
|------|----------|------|
| findExistingReceiptNos | 52ms | ✅ 개선 완료 |
| 엔티티 변환 | 6ms | ✅ 빠름 |
| **outboundRepository.saveAll** | **5,736ms** | ⚠️ JPA 개별 INSERT |
| bulkInsertOutboundCost | 280ms | ✅ JDBC 벌크 |
| saveAllExternalOutbound | 163ms | ✅ JDBC 벌크 |
| **createTrackingRecords** | **79,202ms** | 🔴 N+1 문제 |
| **총 소요시간** | **85,440ms** | 청크당 약 85초 |

---

## ✅ 적용 완료된 개선사항

### 1. 수집/저장 분리 패턴
**Before**: API 호출하면서 동시에 저장 (트랜잭션 범위 큼)
**After**: 1단계 전체 수집 → 2단계 배치 저장

```kotlin
// 1단계: 전체 데이터 수집 (병렬)
val allOutbounds: List<Pair<Client, Outbound>> = Flux.fromIterable(keys)
    .flatMap({ ... }, clientParallel)
    .collectList()
    .block()

// 2단계: 클라이언트별 배치 저장
groupedByClient.forEach { (client, outbounds) ->
    outbounds.chunked(BATCH_SIZE).forEach { chunk ->
        outboundService.createThreePlBtoCOutboundByEtomarsResponseData(...)
    }
}
```

### 2. flatMap concurrency 제한
**Before**: concurrency 미설정 (기본값 256 → 모든 요청 동시 호출)
**After**: clientParallel=30, chunkParallel=30 명시적 제한

```kotlin
Flux.fromIterable(keys)
    .flatMap({ key ->
        // B API 호출
        Flux.fromIterable(chunks)
            .flatMap({ chunk ->
                apiClient.postB2cOutboundDetailList(...)
            }, chunkParallel)  // 청크 병렬 제한
    }, clientParallel)  // 클라이언트 병렬 제한
```

### 3. 중복 체크 쿼리 최적화 (방안 2)
**Before**: 클라이언트의 모든 출고 조회 후 메모리에서 비교 (O(n²))
```kotlin
val allReadySavedOutbounds = outboundRepository.findAllByClientId(client.id)
// SELECT * FROM outbound WHERE client_id = ?  (수만 건)
```

**After**: IN 쿼리로 필요한 receiptNo만 조회 (O(n))
```kotlin
val existingReceiptNos = outboundRepository
    .findExistingReceiptNos(client.id.value, receiptNosToCheck)
    .toSet()
// SELECT receipt_no FROM outbound WHERE client_id = ? AND receipt_no IN (...)
```

### 4. Contract 캐싱 (방안 1)
**Before**: 청크마다 Contract 조회 (60청크 = 60번 조회)
```kotlin
outbounds.chunked(BATCH_SIZE).forEach { chunk ->
    val contractDomains = contractService.findAllByClientId(client.id.value)  // 매번 호출
    outboundService.createThreePlBtoCOutboundByEtomarsResponseData(chunk, ...)
}
```

**After**: 클라이언트당 1번만 조회
```kotlin
groupedByClient.forEach { (client, outbounds) ->
    val contractDomains = contractService.findAllByClientId(client.id.value)  // 1번만
    outbounds.chunked(BATCH_SIZE).forEach { chunk ->
        outboundService.createThreePlBtoCOutboundByEtomarsResponseData(chunk, ..., contractDomains)
    }
}
```

### 5. 스케줄러 @Transactional 제거
**Before**: 스케줄러 전체가 하나의 트랜잭션 (API 호출 + 저장 전체)
**After**: 저장 시에만 트랜잭션 (1000건 단위)

### 6. 반환 타입 정리
**Before**: `Mono<List<Outbound>>` (내부 로직은 동기인데 Mono로 감싸기만)
**After**: `List<Outbound>` (의미 없는 Mono 제거)

---

## 🔴 미적용 개선사항 (추후 적용 필요)

### 1. outboundRepository.saveAll → JDBC 벌크 INSERT
**현재**: JPA saveAll (5,000건 = 5,000번 개별 INSERT) → 5.7초
**개선**: JDBC batchUpdate 사용 → 예상 500ms 이하

```kotlin
// 현재 - JPA
val saved = outboundRepository.saveAll(entities)

// 개선 - JDBC bulk insert
outboundJdbcTemplate.bulkInsertOutbound(entities)
```

**예상 효과**: 5,736ms → ~500ms (90% 개선)

### 2. createTrackingRecords N+1 문제 해결
**현재**: 건별로 존재 여부 확인 (5,000건 = 5,000번 SELECT) → 79초
```kotlin
outboundIds.mapIndexedNotNull { index, outboundId ->
    if (deliveryTrackingRepository.existsByOutboundId(outboundId)) {  // N번 쿼리
        return@mapIndexedNotNull null
    }
    // ...
}
```

**개선**: IN 쿼리로 일괄 조회
```kotlin
// 1번 쿼리로 이미 존재하는 orderNo 일괄 조회
val existingOrderNos = deliveryTrackingJpaRepository
    .findAllByOrderNoIn(orderNos)
    .map { it.orderNo }
    .toSet()

outboundIds.mapIndexedNotNull { index, outboundId ->
    if (orderNos[index] in existingOrderNos) {  // Set으로 O(1) 비교
        return@mapIndexedNotNull null
    }
    // ...
}
```

**예상 효과**: 79,202ms → ~1,000ms (98% 개선)

---

## 📈 예상 최종 성능

### 현재 (5,000건 기준)
| 단계 | 소요시간 |
|------|----------|
| saveAll | 5,736ms |
| createTrackingRecords | 79,202ms |
| 기타 | 501ms |
| **총** | **85,439ms** |

### 개선 후 예상
| 단계 | 소요시간 |
|------|----------|
| bulkInsertOutbound | ~500ms |
| createTrackingRecords | ~1,000ms |
| 기타 | 501ms |
| **총** | **~2,000ms** |

**예상 개선율**: 85초 → 2초 (약 **97% 개선**)

---

## 🔧 병렬 처리 설정 가이드

### 현재 설정
```kotlin
companion object {
    private const val DEFAULT_CLIENT_PARALLEL = 30
    private const val DEFAULT_CHUNK_PARALLEL = 30
    private const val CHUNK_SIZE = 99
    private const val BATCH_SIZE = 5000
}
```

### 설정 선택 기준
| 설정 | 낮게 (10) | 높게 (50) |
|------|-----------|-----------|
| 속도 | 느림 | 빠름 |
| 외부 API 부하 | 적음 | 큼 |
| Rate Limit 위험 | 낮음 | 높음 |
| 안정성 | 높음 | 낮음 |

**권장**: 외부 API Rate Limit과 서버 안정성을 고려해 10~30 사이로 설정

---

## 📁 관련 파일

- `OutboundB2cScheduler.kt` - 스케줄러 (수집/저장 분리, 병렬 제어)
- `OutboundServiceImpl.kt` - 저장 로직 (중복 체크 최적화)
- `OutboundJpaRepository.kt` - findExistingReceiptNos 추가
- `OutboundRepository.kt` - 인터페이스
- `OutboundRepositoryImpl.kt` - 구현체


