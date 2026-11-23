package com.panenku.backend.modules.shared.controller

import com.panenku.backend.common.exception.missing
import com.panenku.backend.modules.petani.service.LocalProductPhotoStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class LocalProductPhotoController(private val storage: LocalProductPhotoStore,
    @Value("\${panenku.product-photos.enabled:false}") private val enabled: Boolean) {
    // No bearer token: authorization is the short-lived HMAC URL, just like F1 presigned URLs.
    @GetMapping("/api/v1/product-photos/local")
    fun image(@RequestParam key: String, @RequestParam expires: Long, @RequestParam signature: String): ResponseEntity<ByteArray> {
        if (!enabled) missing("Foto produk lokal tidak aktif.")
        val bytes = storage.read(key, expires, signature)
        return ResponseEntity.ok().contentType(if (key.endsWith(".png")) MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(bytes)
    }
}
