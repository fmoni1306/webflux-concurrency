package com.webflux.parallel.webfluxconcurrency.repository

import com.webflux.parallel.webfluxconcurrency.domain.Client
import org.springframework.data.jpa.repository.JpaRepository

interface ClientRepository : JpaRepository<Client, Long> {
    fun findByClientCode(clientCode: String): Client?
}
