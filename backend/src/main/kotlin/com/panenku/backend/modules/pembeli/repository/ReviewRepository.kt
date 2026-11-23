package com.panenku.backend.modules.pembeli.repository

import com.panenku.backend.modules.pembeli.dto.ReviewDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ReviewRepository(private val db: JdbcTemplate) {
    fun find(orderId: UUID): ReviewDTO? = db.query("SELECT * FROM reviews WHERE order_id=?", { rs, _ ->
        ReviewDTO(rs.getObject("id", UUID::class.java), rs.getObject("order_id", UUID::class.java),
            rs.getObject("buyer_id", UUID::class.java), rs.getObject("farmer_id", UUID::class.java),
            rs.getInt("rating"), rs.getString("comment"), rs.getTimestamp("created_at").toInstant())
    }, orderId).singleOrNull()

    fun insert(review: ReviewDTO) {
        db.update("INSERT INTO reviews(id,order_id,buyer_id,farmer_id,rating,comment,created_at) VALUES (?,?,?,?,?,?,?)",
            review.id, review.orderId, review.buyerId, review.farmerId, review.rating, review.comment,
            java.sql.Timestamp.from(review.createdAt))
    }
}
