package com.webflux.parallel.webfluxconcurrency.service

import com.webflux.parallel.webfluxconcurrency.client.OutboundWebClient
import com.webflux.parallel.webfluxconcurrency.domain.Outbound
import com.webflux.parallel.webfluxconcurrency.domain.OutboundCost
import com.webflux.parallel.webfluxconcurrency.dto.OutboundDetail
import com.webflux.parallel.webfluxconcurrency.repository.ClientRepository
import com.webflux.parallel.webfluxconcurrency.repository.OutboundBatchRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * 비동기 방식 출고 데이터 수집기
 * WebClient + 2단계 병렬 처리
 */
@Service
class OutboundAsyncCollector(
    private val clientRepository: ClientRepository,
    private val outboundWebClient: OutboundWebClient,
    private val outboundBatchRepository: OutboundBatchRepository,
    @Value("\${collector.chunk-size}") private val chunkSize: Int,
    @Value("\${collector.batch-size}") private val batchSize: Int,
    @Value("\${collector.client-parallel}") private val defaultClientParallel: Int,
    @Value("\${collector.chunk-parallel}") private val defaultChunkParallel: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 비동기 방식으로 전체 출고 데이터 수집
     * 방식 B: 전체 수집 후 배치 저장
     */
    fun collect(
        date: LocalDate,
        delay: Long,
        clientParallel: Int = defaultClientParallel,
        chunkParallel: Int = defaultChunkParallel
    ): CollectResult {
        val startTime = System.currentTimeMillis()

        // 1. 고객사 목록 조회
        val clients = clientRepository.findAll()
        log.info("[Async] 수집 시작: date={}, clients={}, clientParallel={}, chunkParallel={}",
            date, clients.size, clientParallel, chunkParallel)

        val apiCallCount = AtomicInteger(0)

        // 2. 전체 데이터 수집 (병렬)
        val allDetails = Flux.fromIterable(clients)
            .flatMap({ client ->
                // A API - 출고 ID 목록 조회
                outboundWebClient.getOutboundIds(client.clientCode, date, delay)
                    .doOnNext { apiCallCount.incrementAndGet() }
                    .flatMapMany { idsResponse ->
                        // 99개씩 청크 분할 후 병렬 조회
                        val chunks = idsResponse.outboundIds.chunked(chunkSize)
                        Flux.fromIterable(chunks)
                            .flatMap({ chunk ->
                                // B API - 출고 상세 조회
                                outboundWebClient.getOutboundDetails(client.clientCode, chunk, delay)
                                    .doOnNext { apiCallCount.incrementAndGet() }
                                    .flatMapMany { response -> Flux.fromIterable(response.data) }
                            }, chunkParallel)  // Level 2 병렬
                    }
            }, clientParallel)  // Level 1 병렬
            .collectList()
            .block() ?: emptyList()

        val collectTime = System.currentTimeMillis() - startTime
        log.info("[Async] 수집 완료: count={}, apiCalls={}, elapsed={}ms",
            allDetails.size, apiCallCount.get(), collectTime)

        // 3. 배치 저장 (수집 완료 후)
        val saveStartTime = System.currentTimeMillis()
        var totalOutboundCount = 0
        var totalCostCount = 0

        allDetails.chunked(batchSize).forEach { batch ->
            val (outboundCount, costCount) = saveBatch(batch)
            totalOutboundCount += outboundCount
            totalCostCount += costCount
        }

        val saveTime = System.currentTimeMillis() - saveStartTime
        val totalTime = System.currentTimeMillis() - startTime

        log.info(
            "[Async] 저장 완료: outbounds={}, costs={}, collectTime={}ms, saveTime={}ms, totalTime={}ms",
            totalOutboundCount, totalCostCount, collectTime, saveTime, totalTime
        )

        return CollectResult(
            totalOutboundCount = totalOutboundCount,
            totalCostCount = totalCostCount,
            apiCallCount = apiCallCount.get(),
            elapsedTimeMs = totalTime
        )
    }

    /**
     * 배치 저장
     */
    private fun saveBatch(details: List<OutboundDetail>): Pair<Int, Int> {
        val now = LocalDateTime.now()
        val outbounds = mutableListOf<Outbound>()
        val costs = mutableListOf<OutboundCost>()

        for (detail in details) {
            val outboundCode = UUID.randomUUID().toString()

            outbounds.add(
                Outbound(
                    outboundCode = outboundCode,
                    outboundId = detail.outboundId,
                    clientCode = detail.clientCode,
                    orderNo = detail.orderNo,
                    status = detail.status,
                    shippedAt = detail.shippedAt,
                    createdAt = now
                )
            )

            for (costDetail in detail.costs) {
                costs.add(
                    OutboundCost(
                        outboundCode = outboundCode,
                        costType = costDetail.costType,
                        amount = costDetail.amount,
                        createdAt = now
                    )
                )
            }
        }

        val outboundCount = outboundBatchRepository.batchInsertOutbounds(outbounds)
        val costCount = outboundBatchRepository.batchInsertOutboundCosts(costs)

        log.debug("[Async] 배치 저장: outbounds={}, costs={}", outboundCount, costCount)

        return Pair(outboundCount, costCount)
    }
}
