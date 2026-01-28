package com.webflux.parallel.webfluxconcurrency.client

import com.webflux.parallel.webfluxconcurrency.dto.OutboundDetailResponse
import com.webflux.parallel.webfluxconcurrency.dto.OutboundIdsRequest
import com.webflux.parallel.webfluxconcurrency.dto.OutboundIdsResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.LocalDate

/**
 * 비동기 방식 외부 API 클라이언트 (WebClient)
 */
@Component
class OutboundWebClient(
    private val webClient: WebClient,
    @Value("\${mock.base-url}") private val baseUrl: String,
    @Value("\${mock.default-delay}") private val defaultDelay: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A API - 출고 ID 목록 조회 (비동기)
     */
    fun getOutboundIds(clientCode: String, date: LocalDate, delay: Long = defaultDelay): Mono<OutboundIdsResponse> {
        log.debug("[Async] A API 호출: clientCode={}, date={}", clientCode, date)

        return webClient.get()
            .uri("$baseUrl/$clientCode/outbound-ids?date=$date&delay=$delay")
            .retrieve()
            .bodyToMono<OutboundIdsResponse>()
            .doOnNext { response ->
                log.debug("[Async] A API 응답: clientCode={}, count={}", clientCode, response.totalCount)
            }
    }

    /**
     * B API - 출고 상세 조회 (비동기)
     */
    fun getOutboundDetails(
        clientCode: String,
        outboundIds: List<String>,
        delay: Long = defaultDelay
    ): Mono<OutboundDetailResponse> {
        log.debug("[Async] B API 호출: clientCode={}, count={}", clientCode, outboundIds.size)

        val request = OutboundIdsRequest(outboundIds)

        return webClient.post()
            .uri("$baseUrl/$clientCode/outbounds?delay=$delay")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<OutboundDetailResponse>()
            .doOnNext { response ->
                log.debug("[Async] B API 응답: clientCode={}, count={}", clientCode, response.data.size)
            }
    }
}
