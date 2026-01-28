package com.webflux.parallel.webfluxconcurrency.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "outbound_cost")
class OutboundCost(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "outbound_code", nullable = false)
    val outboundCode: String,  // UUID (Outbound와 매핑)

    @Column(name = "cost_type", nullable = false)
    val costType: String,

    @Column(name = "amount", nullable = false)
    val amount: BigDecimal,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
