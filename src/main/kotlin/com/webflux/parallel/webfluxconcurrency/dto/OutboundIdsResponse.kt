package com.webflux.parallel.webfluxconcurrency.dto

import java.time.LocalDate

/**
 * A API 응답 - 출고 ID 목록
 * GET /mock/{clientCode}/outbound-ids
 */
data class OutboundIdsResponse(
    val clientCode: String,
    val date: LocalDate,
    val totalCount: Int,
    val outboundIds: List<String>
)
