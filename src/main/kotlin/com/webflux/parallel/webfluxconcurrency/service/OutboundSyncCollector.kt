package com.webflux.parallel.webfluxconcurrency.service

import com.webflux.parallel.webfluxconcurrency.client.OutboundRestClient
import com.webflux.parallel.webfluxconcurrency.domain.Outbound
import com.webflux.parallel.webfluxconcurrency.domain.OutboundCost
import com.webflux.parallel.webfluxconcurrency.dto.OutboundDetail
import com.webflux.parallel.webfluxconcurrency.repository.ClientRepository
import com.webflux.parallel.webfluxconcurrency.repository.OutboundBatchRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 동기 방식 출고 데이터 수집기
 * RestTemplate + 순차/멀티스레드 처리
 */
@Service
class OutboundSyncCollector(
    private val clientRepository: ClientRepository,
    private val outboundRestClient: OutboundRestClient,
    private val outboundBatchRepository: OutboundBatchRepository,
    @Value("\${collector.chunk-size}") private val chunkSize: Int,
    @Value("\${collector.batch-size}") private val batchSize: Int,
    @Value("\${collector.client-parallel}") private val defaultClientParallel: Int,
    @Value("\${collector.chunk-parallel}") private val defaultChunkParallel: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 동기 순차 방식
     */
    fun collect(date: LocalDate, delay: Long): CollectResult {
        return collectInternal(date, delay, 1, 1)
    }

    /**
     * 동기 멀티스레드 방식
     */
    fun collectParallel(
        date: LocalDate,
        delay: Long,
        clientParallel: Int = defaultClientParallel,
        chunkParallel: Int = defaultChunkParallel
    ): CollectResult {
        return collectInternal(date, delay, clientParallel, chunkParallel)
    }

    private fun collectInternal(
        date: LocalDate,
        delay: Long,
        clientParallel: Int,
        chunkParallel: Int
    ): CollectResult {
        val startTime = System.currentTimeMillis()

        // 1. 고객사 목록 조회
        val clients = clientRepository.findAll()
        log.info("[Sync] 수집 시작: date={}, clients={}, clientParallel={}, chunkParallel={}",
            date, clients.size, clientParallel, chunkParallel)

        val allDetails = Collections.synchronizedList(mutableListOf<OutboundDetail>())
        val apiCallCount = AtomicInteger(0)

        // 2. 고객사별 처리
        val clientExecutor = Executors.newFixedThreadPool(clientParallel)
        val clientFutures = clients.map { client ->
            clientExecutor.submit {
                // A API - 출고 ID 목록 조회
                val idsResponse = outboundRestClient.getOutboundIds(client.clientCode, date, delay)
                apiCallCount.incrementAndGet()

                // 99개씩 청크 분할
                val chunks = idsResponse.outboundIds.chunked(chunkSize)

                // 청크별 처리
                val chunkExecutor = Executors.newFixedThreadPool(chunkParallel)
                val chunkFutures = chunks.map { chunk ->
                    chunkExecutor.submit {
                        // B API - 출고 상세 조회
                        val detailResponse = outboundRestClient.getOutboundDetails(client.clientCode, chunk, delay)
                        apiCallCount.incrementAndGet()
                        allDetails.addAll(detailResponse.data)
                    }
                }
                chunkFutures.forEach { it.get() }
                chunkExecutor.shutdown()
            }
        }
        clientFutures.forEach { it.get() }
        clientExecutor.shutdown()

        val collectTime = System.currentTimeMillis() - startTime
        log.info("[Sync] 수집 완료: count={}, apiCalls={}, elapsed={}ms",
            allDetails.size, apiCallCount.get(), collectTime)

        // 3. 배치 저장
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
            "[Sync] 저장 완료: outbounds={}, costs={}, collectTime={}ms, saveTime={}ms, totalTime={}ms",
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

        log.debug("[Sync] 배치 저장: outbounds={}, costs={}", outboundCount, costCount)

        return Pair(outboundCount, costCount)
    }
}

data class CollectResult(
    val totalOutboundCount: Int,
    val totalCostCount: Int,
    val apiCallCount: Int,
    val elapsedTimeMs: Long
)
