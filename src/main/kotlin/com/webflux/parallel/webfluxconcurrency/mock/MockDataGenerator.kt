package com.webflux.parallel.webfluxconcurrency.mock

import com.webflux.parallel.webfluxconcurrency.dto.OutboundCostDetail
import com.webflux.parallel.webfluxconcurrency.dto.OutboundDetail
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

@Component
class MockDataGenerator {

    private val statuses = listOf("SHIPPED", "DELIVERED", "IN_TRANSIT", "PENDING")
    private val costTypes = listOf("SHIPPING", "PACKING", "HANDLING")

    /**
     * 고객사별 출고 ID 목록 생성
     * 고객사별로 1,000 ~ 30,000건 사이 (편차 큼)
     */
    fun generateOutboundIds(clientCode: String, date: LocalDate): List<String> {
//        val count = Random.nextInt(1000, 30001)
        val count = 100 // 테스트용으로 고객사당 100건 => 총 2만건
        return (1..count).map { idx ->
            "OB-${clientCode}-${date.toString().replace("-", "")}-${idx.toString().padStart(6, '0')}"
        }
    }

    /**
     * 출고 상세 데이터 생성
     */
    fun generateOutboundDetails(clientCode: String, outboundIds: List<String>): List<OutboundDetail> {
        return outboundIds.map { outboundId ->
            OutboundDetail(
                outboundId = outboundId,
                clientCode = clientCode,
                orderNo = "ORD-${Random.nextInt(100000, 999999)}",
                status = statuses.random(),
                shippedAt = LocalDateTime.now().minusHours(Random.nextLong(1, 72)),
                costs = generateCosts()
            )
        }
    }

    /**
     * 출고당 비용 2~3건 생성
     */
    private fun generateCosts(): List<OutboundCostDetail> {
        val costCount = Random.nextInt(2, 4)
        return costTypes.shuffled().take(costCount).map { costType ->
            OutboundCostDetail(
                costType = costType,
                amount = BigDecimal(Random.nextInt(500, 10000))
            )
        }
    }
}
