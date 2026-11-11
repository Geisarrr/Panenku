package com.panenku.backend.common.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID

/** JWT issued by Geisar's auth module and accepted by the shared resource-server decoder. */
@Component
class JwtProvider(
    @Value("\${app.jwt.secret}") private val secret: String,
    @Value("\${app.jwt.issuer}") private val issuer: String,
    @Value("\${app.jwt.expiration:86400000}") private val expirationMs: Long,
    private val clock: Clock
) {
    init { require(expirationMs > 0) { "JWT_EXPIRATION harus positif." } }
    private fun getKey() = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    // Subject is the immutable user UUID, not the email.
    fun generateToken(userId: String): String {
        val now = Instant.now(clock)
        return Jwts.builder().subject(userId).issuer(issuer).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs))).signWith(getKey(), Jwts.SIG.HS256).compact()
    }
    fun validateToken(token: String): Boolean = try {
        claims(token)
        true
    } catch (_: Exception) { false }
    fun extractUserId(token: String): String = claims(token).subject
    private fun claims(token: String): io.jsonwebtoken.Claims {
        val signed = Jwts.parser().verifyWith(getKey()).requireIssuer(issuer).clockSkewSeconds(60)
            .clock { Date.from(Instant.now(clock)) }.build().parseSignedClaims(token)
        require(signed.header.algorithm == "HS256") { "Algoritma token tidak sesuai." }
        requireNotNull(signed.payload.expiration) { "Token wajib memiliki waktu kedaluwarsa." }
        UUID.fromString(signed.payload.subject)
        return signed.payload
    }
}
