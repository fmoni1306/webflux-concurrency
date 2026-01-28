package com.webflux.parallel.webfluxconcurrency.controller

import com.webflux.parallel.webfluxconcurrency.repository.OutboundBatchRepository
import com.webflux.parallel.webfluxconcurrency.service.CollectResult
import com.webflux.parallel.webfluxconcurrency.service.OutboundAsyncCollector
import com.webflux.parallel.webfluxconcurrency.service.OutboundSyncCollector
import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/collect")
class CollectorController(
    private val outboundSyncCollector: OutboundSyncCollector,
    private val outboundAsyncCollector: OutboundAsyncCollector,
    private val outboundBatchRepository: OutboundBatchRepository,
    @Value("\${mock.default-delay}") private val defaultDelay: Long,
    @Value("\${collector.client-parallel}") private val defaultClientParallel: Int,
    @Value("\${collector.chunk-parallel}") private val defaultChunkParallel: Int
) {

    /**
     * 동기 순차 방식 수집
     * POST /collect/sync?date=2026-01-01&delay=500
     */
    @PostMapping("/sync")
    fun collectSync(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") date: LocalDate,
        @RequestParam(required = false) delay: Long?
    ): CollectResult {
        return outboundSyncCollector.collect(date, delay ?: defaultDelay)
    }

    /**
     * 동기 멀티스레드 방식 수집
     * POST /collect/sync-parallel?date=2026-01-01&delay=500&clientParallel=10&chunkParallel=10
     */
    @PostMapping("/sync-parallel")
    fun collectSyncParallel(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") date: LocalDate,
        @RequestParam(required = false) delay: Long?,
        @RequestParam(required = false) clientParallel: Int?,
        @RequestParam(required = false) chunkParallel: Int?
    ): CollectResult {
        return outboundSyncCollector.collectParallel(
            date = date,
            delay = delay ?: defaultDelay,
            clientParallel = clientParallel ?: defaultClientParallel,
            chunkParallel = chunkParallel ?: defaultChunkParallel
        )
    }

    /**
     * 비동기 방식 수집
     * POST /collect/async?date=2026-01-01&delay=500&clientParallel=10&chunkParallel=10
     */
    @PostMapping("/async")
    fun collectAsync(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") date: LocalDate,
        @RequestParam(required = false) delay: Long?,
        @RequestParam(required = false) clientParallel: Int?,
        @RequestParam(required = false) chunkParallel: Int?
    ): CollectResult {
        return outboundAsyncCollector.collect(
            date = date,
            delay = delay ?: defaultDelay,
            clientParallel = clientParallel ?: defaultClientParallel,
            chunkParallel = chunkParallel ?: defaultChunkParallel
        )
    }

    /**
     * 데이터 초기화 (테스트용)
     * DELETE /collect/data
     */
    @DeleteMapping("/data")
    fun deleteData(): Map<String, String> {
        outboundBatchRepository.deleteAllOutbounds()
        return mapOf("status" to "deleted")
    }
}
