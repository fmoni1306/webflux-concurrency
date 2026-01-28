package com.webflux.parallel.webfluxconcurrency.config

import com.webflux.parallel.webfluxconcurrency.domain.Client
import com.webflux.parallel.webfluxconcurrency.repository.ClientRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DataInitializer(
    private val clientRepository: ClientRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (clientRepository.count() > 0) {
            log.info("고객사 데이터 이미 존재: {} 건", clientRepository.count())
            return
        }

        val clients = (1..200).map { i ->
            Client(
                clientCode = "CLIENT-${i.toString().padStart(3, '0')}",
                name = "고객사 $i"
            )
        }

        clientRepository.saveAll(clients)
        log.info("고객사 데이터 생성 완료: {} 건", clients.size)
    }
}
