package com.webflux.parallel.webfluxconcurrency.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "outbound")
class Outbound(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "outbound_code", nullable = false, unique = true)
    val outboundCode: String,  // UUID (우리가 생성)

    @Column(name = "outbound_id", nullable = false)
    val outboundId: String,    // 외부 API에서 받은 ID

    @Column(name = "client_code", nullable = false)
    val clientCode: String,

    @Column(name = "order_no", nullable = false)
    val orderNo: String,

    @Column(name = "status", nullable = false)
    val status: String,

    @Column(name = "shipped_at", nullable = false)
    val shippedAt: LocalDateTime,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
