package com.panenku.backend.modules.petani.service

import com.panenku.backend.common.exception.forbidden
import com.panenku.backend.common.exception.missing
import com.panenku.backend.common.storage.LocalPrivateImageStore
import com.panenku.backend.modules.petani.dto.ProductPhotoAccess
import com.panenku.backend.modules.shared.entity.Product
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Opt-in local development adapter. Files stay private; URLs expire after 15 minutes. */
@Component
class LocalProductPhotoStore(
    @Value("\${panenku.product-photos.local-dir:}") private val directory: String,
    @Value("\${panenku.product-photos.local-base-url:http://127.0.0.1:8080}") private val baseUrl: String,
    @Value("\${panenku.jwt.secret}") private val secret: String,
    private val clock: Clock
) {
    val selected get() = directory.isNotBlank()
    private val store by lazy {
        require(secret.toByteArray().size >= 32) { "JWT_SECRET minimal 32 byte" }
        LocalPrivateImageStore(Path.of(directory), 25_000_000L)
    }
    internal fun put(product: Product, image: ValidatedProductImage): String =
        "local-products/${product.farmerId}/${product.id}/${store.upload(image.bytes)}"

    fun managed(product: Product, key: String?): Boolean = key != null && pattern.matches(key) &&
        key.startsWith("local-products/${product.farmerId}/${product.id}/")

    internal fun signed(key: String): ProductPhotoAccess {
        fileName(key)
        val expiry = Instant.now(clock).plusSeconds(900).epochSecond
        val base = java.net.URI(baseUrl)
        require(base.scheme in setOf("http", "https") && base.host != null && base.rawQuery == null &&
            base.rawFragment == null && base.rawUserInfo == null) { "URL dasar foto lokal tidak valid" }
        val url = UriComponentsBuilder.fromUriString(baseUrl.trimEnd('/'))
            .path("/api/v1/product-photos/local")
            .queryParam("key", key).queryParam("expires", expiry).queryParam("signature", signature(key, expiry))
            .build().encode().toUriString()
        return ProductPhotoAccess(url, Instant.ofEpochSecond(expiry))
    }

    fun read(key: String, expires: Long, signature: String): ByteArray {
        if (!selected) missing("Penyimpanan foto lokal tidak aktif.")
        val now = Instant.now(clock).epochSecond
        if (!pattern.matches(key) || expires <= now || expires > now + 900 ||
            !Regex("[0-9a-f]{64}").matches(signature) || !MessageDigest.isEqual(
                signature.toByteArray(Charsets.US_ASCII), signature(key, expires).toByteArray(Charsets.US_ASCII))) {
            forbidden("Tautan foto tidak valid atau sudah kedaluwarsa.")
        }
        return try { store.read(fileName(key)) }
        catch (_: java.nio.file.NoSuchFileException) { missing("Foto produk tidak ditemukan.") }
    }

    internal fun delete(key: String) = store.delete(fileName(key))
    private fun fileName(key: String): String {
        if (!pattern.matches(key)) forbidden("Kunci foto tidak valid.")
        return key.substringAfterLast('/')
    }
    private fun signature(key: String, expires: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal("panenku-local-product-v1\n$key\n$expires".toByteArray(Charsets.UTF_8)))
    }
    private companion object {
        const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val pattern = Regex("local-products/$UUID_PATTERN/$UUID_PATTERN/$UUID_PATTERN\\.(jpg|png)")
    }
}
