package com.webflux.parallel.webfluxconcurrency.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ClientConfig(
    @Value($$"${webclient.max-in-memory-size}") private val maxInMemorySize: Int,
    @Value($$"${webclient.connect-timeout}") private val connectTimeout: Int,
    @Value($$"${webclient.read-timeout}") private val readTimeout: Int
) {

    /**
     * RestTemplate (동기 방식)
     */
    @Bean
    fun restTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeout)
            setReadTimeout(readTimeout)
        }
        return RestTemplate(factory)
    }

    /**
     * WebClient (비동기 방식)
     */
    @Bean
    fun webClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(maxInMemorySize)
            }
            .build()
    }
}
