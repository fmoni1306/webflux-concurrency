package com.webflux.parallel.webfluxconcurrency.dto

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * B API 응답 - 출고 상세 목록
 * POST /mock/{clientCode}/outbounds
 */
data class OutboundDetailResponse(
    val data: List<OutboundDetail>
)

data class OutboundDetail(
    val outboundId: String,
    val clientCode: String,
    val orderNo: String,
    val status: String,
    val shippedAt: LocalDateTime,
    val costs: List<OutboundCostDetail>
)

data class OutboundCostDetail(
    val costType: String,
    val amount: BigDecimal
)
