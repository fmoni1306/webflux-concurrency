package com.webflux.parallel.webfluxconcurrency

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebfluxConcurrencyApplication

fun main(args: Array<String>) {
    runApplication<WebfluxConcurrencyApplication>(*args)
}
