package com.webflux.parallel.webfluxconcurrency.dto

/**
 * B API 요청 Body
 * POST /mock/{clientCode}/outbounds
 */
data class OutboundIdsRequest(
    val outboundIds: List<String>
)
