package com.panenku.backend

import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.modules.petani.service.ProductImageValidator
import com.panenku.backend.modules.petani.service.LocalProductPhotoStore
import com.panenku.backend.modules.petani.service.ProductPhotoStorage
import com.panenku.backend.modules.shared.entity.Product
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Clock
import javax.imageio.ImageIO

class ProductPhotoStorageTest {
    private fun image() = ByteArrayOutputStream().use { out -> ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out); ProductImageValidator.validate(MockMultipartFile("file", "a.png", "image/png", out.toByteArray())) }
    private fun noLocalStore() = LocalProductPhotoStore("", "http://localhost:8080", "x".repeat(32), Clock.systemUTC())
    @Test fun `F1 disabled storage reports unavailable instead of inventing a photo`() {
        val storage = ProductPhotoStorage(false, "http://localhost:9000", "http://localhost:9000", "", "", "panenku-products", noLocalStore())
        assertNull(storage.access(Product()))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ApiException::class.java) { storage.put(Product(), image()) }.status)
    }
    @Test fun `F1 inaccessible storage reports service unavailable`() {
        val storage = ProductPhotoStorage(true, "http://127.0.0.1:1", "http://127.0.0.1:1", "test-user", "test-password", "panenku-products", noLocalStore())
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ApiException::class.java) { storage.put(Product(), image()) }.status)
    }
}
