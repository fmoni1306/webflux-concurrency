package com.webflux.parallel.webfluxconcurrency.repository

import com.webflux.parallel.webfluxconcurrency.domain.Outbound
import com.webflux.parallel.webfluxconcurrency.domain.OutboundCost
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class OutboundBatchRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Outbound 배치 저장
     */
    fun batchInsertOutbounds(outbounds: List<Outbound>): Int {
        if (outbounds.isEmpty()) return 0

        val sql = """
            INSERT INTO outbound (outbound_code, outbound_id, client_code, order_no, status, shipped_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val batchArgs = outbounds.map { outbound ->
            arrayOf(
                outbound.outboundCode,
                outbound.outboundId,
                outbound.clientCode,
                outbound.orderNo,
                outbound.status,
                Timestamp.valueOf(outbound.shippedAt),
                Timestamp.valueOf(outbound.createdAt)
            )
        }

        val result = jdbcTemplate.batchUpdate(sql, batchArgs)
        val insertedCount = result.sum()

        log.debug("Batch inserted {} outbounds", insertedCount)
        return insertedCount
    }

    /**
     * OutboundCost 배치 저장
     */
    fun batchInsertOutboundCosts(costs: List<OutboundCost>): Int {
        if (costs.isEmpty()) return 0

        val sql = """
            INSERT INTO outbound_cost (outbound_code, cost_type, amount, created_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        val batchArgs = costs.map { cost ->
            arrayOf(
                cost.outboundCode,
                cost.costType,
                cost.amount,
                Timestamp.valueOf(cost.createdAt)
            )
        }

        val result = jdbcTemplate.batchUpdate(sql, batchArgs)
        val insertedCount = result.sum()

        log.debug("Batch inserted {} outbound costs", insertedCount)
        return insertedCount
    }

    /**
     * 테이블 데이터 삭제 (테스트용)
     * FK 제약 때문에 outbound_cost 먼저 삭제
     */
    fun deleteAllOutbounds() {
        jdbcTemplate.update("DELETE FROM outbound_cost")
        jdbcTemplate.update("DELETE FROM outbound")
        log.info("All outbound data deleted")
    }
}
