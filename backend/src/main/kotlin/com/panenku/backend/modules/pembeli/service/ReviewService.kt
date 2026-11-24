package com.panenku.backend.modules.pembeli.service

import com.panenku.backend.common.exception.*
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.dto.*
import com.panenku.backend.modules.pembeli.repository.OrderRepository
import com.panenku.backend.modules.pembeli.repository.ReviewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class ReviewService(private val actors: ActorService, private val orders: OrderRepository,
    private val reviews: ReviewRepository, private val clock: Clock) {
    @Transactional
    fun submit(actor: UUID, orderId: UUID, request: ReviewRequest): ReviewDTO {
        actors.require(actor, "pembeli")
        // Serialize submissions on the existing order; UNIQUE(order_id) remains the database backstop.
        val order = orders.lockById(orderId) ?: missing("Pesanan tidak ditemukan.")
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        if (order.orderStatus != "selesai") conflict("Ulasan hanya dapat dikirim setelah pesanan selesai.")
        val rating = try { request.rating.intValueExact() } catch (_: ArithmeticException) { invalid("Bintang wajib bilangan bulat 1 sampai 5.") }
        if (rating !in 1..5) invalid("Bintang wajib bernilai 1 sampai 5.")
        if ((request.comment?.length ?: 0) > 500) invalid("Komentar maksimal 500 karakter.")
        if (reviews.find(orderId) != null) conflict("Ulasan pesanan ini sudah terkirim.")
        val review = ReviewDTO(UUID.randomUUID(), order.id, actor, order.farmerId, rating,
            request.comment?.trim()?.takeIf { it.isNotEmpty() }, Instant.now(clock))
        reviews.insert(review)
        return review
    }

    @Transactional(readOnly = true)
    fun get(actor: UUID, orderId: UUID): ReviewDTO? {
        actors.require(actor, "pembeli")
        val order = orders.findById(orderId).orElse(null) ?: missing("Pesanan tidak ditemukan.")
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        return reviews.find(orderId)
    }
}
