package com.webflux.parallel.webfluxconcurrency.mock

import com.webflux.parallel.webfluxconcurrency.dto.OutboundDetailResponse
import com.webflux.parallel.webfluxconcurrency.dto.OutboundIdsRequest
import com.webflux.parallel.webfluxconcurrency.dto.OutboundIdsResponse
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 가상 외부 API
 * 지연시간(delay) 조절 가능
 */
@RestController
@RequestMapping("/mock")
class MockOutboundController(
    private val mockDataGenerator: MockDataGenerator
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 고객사별 출고 ID 캐시 (동일 요청 시 같은 데이터 반환)
    private val outboundIdsCache = mutableMapOf<String, List<String>>()

    /**
     * A API - 출고 ID 목록 조회
     * GET /mock/{clientCode}/outbound-ids?date=2026-01-01&delay=500
     */
    @GetMapping("/{clientCode}/outbound-ids")
    fun getOutboundIds(
        @PathVariable clientCode: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") date: LocalDate,
        @RequestParam(defaultValue = "0") delay: Long
    ): OutboundIdsResponse {
        // 지연 시간 적용
        if (delay > 0) {
            Thread.sleep(delay)
        }

        val cacheKey = "$clientCode-$date"
        val outboundIds = outboundIdsCache.getOrPut(cacheKey) {
            mockDataGenerator.generateOutboundIds(clientCode, date)
        }

        log.debug("[A API] clientCode={}, date={}, count={}", clientCode, date, outboundIds.size)

        return OutboundIdsResponse(
            clientCode = clientCode,
            date = date,
            totalCount = outboundIds.size,
            outboundIds = outboundIds
        )
    }

    /**
     * B API - 출고 상세 조회
     * POST /mock/{clientCode}/outbounds?delay=500
     * Body: { "outboundIds": ["OB-001", "OB-002", ...] }
     */
    @PostMapping("/{clientCode}/outbounds")
    fun getOutboundDetails(
        @PathVariable clientCode: String,
        @RequestBody request: OutboundIdsRequest,
        @RequestParam(defaultValue = "0") delay: Long
    ): OutboundDetailResponse {
        // 지연 시간 적용
        if (delay > 0) {
            Thread.sleep(delay)
        }

        val details = mockDataGenerator.generateOutboundDetails(clientCode, request.outboundIds)

        log.debug("[B API] clientCode={}, requestCount={}, responseCount={}", 
            clientCode, request.outboundIds.size, details.size)

        return OutboundDetailResponse(data = details)
    }

    /**
     * 캐시 초기화 (테스트용)
     */
    @DeleteMapping("/cache")
    fun clearCache(): Map<String, String> {
        outboundIdsCache.clear()
        log.info("Mock cache cleared")
        return mapOf("status" to "cleared")
    }
}
