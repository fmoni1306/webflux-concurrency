package com.webflux.parallel.webfluxconcurrency.client

import com.webflux.parallel.webfluxconcurrency.dto.OutboundDetailResponse
import com.webflux.parallel.webfluxconcurrency.dto.OutboundIdsRequest
import com.webflux.parallel.webfluxconcurrency.dto.OutboundIdsResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject
import java.time.LocalDate

/**
 * 동기 방식 외부 API 클라이언트 (RestTemplate)
 * 성능 비교용
 */
@Component
class OutboundRestClient(
    private val restTemplate: RestTemplate,
    @Value("\${mock.base-url}") private val baseUrl: String,
    @Value("\${mock.default-delay}") private val defaultDelay: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A API - 출고 ID 목록 조회 (동기)
     */
    fun getOutboundIds(clientCode: String, date: LocalDate, delay: Long = defaultDelay): OutboundIdsResponse {
        val url = "$baseUrl/$clientCode/outbound-ids?date=$date&delay=$delay"

        log.debug("[Sync] A API 호출: clientCode={}, date={}", clientCode, date)

        return restTemplate.getForObject<OutboundIdsResponse>(url)
            ?: throw RuntimeException("Failed to get outbound ids for clientCode=$clientCode")
    }

    /**
     * B API - 출고 상세 조회 (동기)
     */
    fun getOutboundDetails(
        clientCode: String,
        outboundIds: List<String>,
        delay: Long = defaultDelay
    ): OutboundDetailResponse {
        val url = "$baseUrl/$clientCode/outbounds?delay=$delay"
        val request = OutboundIdsRequest(outboundIds)

        log.debug("[Sync] B API 호출: clientCode={}, count={}", clientCode, outboundIds.size)

        return restTemplate.postForObject<OutboundDetailResponse>(url, request)
            ?: throw RuntimeException("Failed to get outbound details for clientCode=$clientCode")
    }
}
