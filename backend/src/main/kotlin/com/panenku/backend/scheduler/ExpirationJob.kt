package com.panenku.backend.scheduler

import com.panenku.backend.modules.pembeli.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class ExpirationJob(
    private val orderRepository: OrderRepository, private val expiration: OrderExpirationService,
    private val clock: Clock, @Value("\${scheduler.expiration.batchSize:100}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    init { require(batchSize in 1..500) { "Batch kedaluwarsa harus antara 1 dan 500." } }

    // Each order has its own transaction; one failure does not roll back the entire batch.
    @Scheduled(fixedDelayString = "\${scheduler.expiration.fixedDelay:300000}", initialDelayString = "\${scheduler.expiration.initialDelay:300000}")
    fun expireOverdueOrders() {
        val now = Instant.now(clock)
        orderRepository.findExpiredOrdersBefore(now, PageRequest.of(0, batchSize)).forEach { order ->
            try { expiration.expire(order.id, order.farmerId, now) }
            catch (ex: RuntimeException) { logger.error("Gagal memproses kedaluwarsa order {}", order.id, ex) }
        }
    }
}
