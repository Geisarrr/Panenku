package com.panenku.backend

import com.panenku.backend.modules.petani.service.ProductPhotoService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import com.panenku.backend.modules.petani.dto.ProductRequest
import java.math.BigDecimal

@SpringBootTest(properties=["panenku.jwt.secret=test-only-32-byte-secret-for-tests-123456789",
    "scheduler.enabled=false", "panenku.product-photos.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
@Import(CheckoutIntegrationTest.FixedTime::class)
class LocalProductPhotoIntegrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
        val directory = Files.createTempDirectory("panenku-local-products-test-")
        @DynamicPropertySource @JvmStatic fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url", postgres::getJdbcUrl)
            r.add("spring.datasource.username", postgres::getUsername)
            r.add("spring.datasource.password", postgres::getPassword)
            r.add("panenku.product-photos.local-dir") { directory.toString() }
            r.add("panenku.product-photos.local-base-url") { "http://127.0.0.1:8080" }
        }
    }
    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var photos: ProductPhotoService
    lateinit var f: CheckoutIntegrationTest
    val bytes get() = Files.readAllBytes(Path.of("demo-assets/foto-dummy/produk-sawi-segar.png"))
    fun file() = MockMultipartFile("file", "produk-sawi-segar.png", "image/png", bytes)
    fun draft() = f.productService.save(f.farmer, null, ProductRequest(f.commodity,"Sawi Lokal",25000,BigDecimal.TEN,f.date))
    @BeforeEach fun seed() {
        f = CheckoutIntegrationTest(); context.autowireCapableBeanFactory.autowireBean(f); f.seed()
    }
    @Test fun `dummy sawi upload signed image publication and checkout work without MinIO`() {
        val product = draft()
        val photo = photos.upload(f.farmer, product.id, file())
        assertTrue(photo.photoUrl.startsWith("local-products/${f.farmer}/${product.id}/"))
        f.mvc.perform(get(URI(photo.photoAccessUrl))).andExpect(status().isOk)
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes))
            .andExpect(header().string("Cache-Control","no-store"))
        val active = f.productService.save(f.farmer,product.id,ProductRequest(f.commodity,"Sawi Lokal",25000,BigDecimal.TEN,f.date,"aktif"))
        assertNotNull(active.photoAccessUrl)
        assertTrue(active.availableForPurchase)
        val order = f.checkout.checkout(f.buyer,"local-photo-${UUID.randomUUID()}",
            f.request().copy(items=listOf(com.panenku.backend.modules.pembeli.dto.CheckoutItemRequest(product.id,BigDecimal.ONE,25000))))
        assertEquals(25000L,order.totalPayment)
    }
    @Test fun `F1 publication requires approved KTP complete payment positive stock and uploaded local photo`() {
        val product = draft()
        fun publish(stock: BigDecimal = BigDecimal.TEN) = f.productService.save(f.farmer, product.id,
            ProductRequest(f.commodity, "Sawi Lokal", 25000, stock, f.date, "aktif"))
        assertThrows(com.panenku.backend.common.exception.ApiException::class.java) { publish() }
        photos.upload(f.farmer, product.id, file())
        f.db.update("UPDATE farmer_profiles SET ktp_status='menunggu' WHERE id=?", f.farmer)
        assertThrows(com.panenku.backend.common.exception.ApiException::class.java) { publish() }
        f.db.update("UPDATE farmer_profiles SET ktp_status='disetujui' WHERE id=?", f.farmer)
        f.db.update("UPDATE farmer_payment_methods SET is_active=false WHERE farmer_id=?", f.farmer)
        assertThrows(com.panenku.backend.common.exception.ApiException::class.java) { publish() }
        f.db.update("UPDATE farmer_payment_methods SET is_active=true WHERE farmer_id=?", f.farmer)
        assertThrows(com.panenku.backend.common.exception.ApiException::class.java) { publish(BigDecimal.ZERO) }
        assertTrue(publish().availableForPurchase)
    }

    @Test fun `F1 to F4 local photo cart schedule preview and idempotent checkout keep server amounts`() {
        val product = draft()
        photos.upload(f.farmer, product.id, file())
        f.productService.save(f.farmer, product.id, ProductRequest(f.commodity, "Sawi Lokal", 25000, BigDecimal.TEN, f.date, "aktif"))
        val cart = f.cart.add(f.buyer, com.panenku.backend.modules.pembeli.dto.CartItemRequest(product.id, BigDecimal("0.25")))
        assertEquals(1, cart.items.size)
        val options = f.mvc.perform(post("/api/v1/buyer/fulfillment-options")
            .with(jwt().jwt { it.subject(f.buyer.toString()) }).contentType("application/json")
            .content("""{"items":[{"productId":"${product.id}","quantityKg":0.25}],"fulfillmentMethod":"pickup"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val scheduled = f.mapper.readTree(options).path("data").path("dates").first()
        val body = """{"items":[{"productId":"${product.id}","quantityKg":0.25}],"fulfillmentMethod":"pickup","scheduledDate":"${scheduled.path("date").asText()}","scheduledTimeSlot":"${scheduled.path("timeSlots").first().asText()}"}"""
        val preview = f.mvc.perform(post("/api/v1/buyer/checkout/preview")
            .with(jwt().jwt { it.subject(f.buyer.toString()) }).contentType("application/json").content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.subtotalItems").value(6250))
            .andExpect(jsonPath("$.data.deliveryFee").value(0)).andExpect(jsonPath("$.data.totalPayment").value(6250))
            .andExpect(jsonPath("$.data.transactionLimitApplied").value(10000000))
            .andReturn().response.contentAsString
        val quote = f.mapper.readTree(preview).path("data")
        val request = f.mapper.readTree(body) as com.fasterxml.jackson.databind.node.ObjectNode
        (request.path("items").first() as com.fasterxml.jackson.databind.node.ObjectNode).put("expectedPricePerKg", 25000)
        request.put("expectedDeliveryFee", 0).put("expectedCheckoutToken", quote.path("quoteToken").asText())
            .put("expectedFulfillmentToken", quote.path("expectedFulfillmentToken").asText())
        val key = UUID.randomUUID().toString()
        val responses = (1..2).map {
            f.mvc.perform(post("/api/v1/buyer/checkout").with(jwt().jwt { it.subject(f.buyer.toString()) })
                .header("Idempotency-Key", key).contentType("application/json").content(request.toString()))
                .andExpect(status().isCreated).andExpect(jsonPath("$.data.totalPayment").value(6250))
                .andReturn().response.contentAsString
        }
        assertEquals(f.mapper.readTree(responses[0]).path("data"), f.mapper.readTree(responses[1]).path("data"))
        assertEquals(1, f.db.queryForObject("SELECT count(*) FROM orders", Int::class.java))
    }

    @Test fun `unsigned tampered expired and cross product image URLs are refused`() {
        val photo = photos.upload(f.farmer,draft().id,file())
        val url = URI(photo.photoAccessUrl)
        f.mvc.perform(get(url.path).param("key",photo.photoUrl).param("expires",photo.photoExpiresAt.epochSecond.toString())
            .param("signature","0".repeat(64))).andExpect(status().isForbidden)
        f.mvc.perform(get(URI(photo.photoAccessUrl.replace("local-products/", "local-products/../")))).andExpect(status().is4xxClientError)
        f.mvc.perform(get(URI(photo.photoAccessUrl.replace(photo.photoUrl.substringAfterLast('/'),"${UUID.randomUUID()}.png"))))
            .andExpect(status().isForbidden)
        (f.clock as CheckoutIntegrationTest.TestClock).time.set(photo.photoExpiresAt)
        f.mvc.perform(get(url)).andExpect(status().isForbidden)
    }
    @Test fun `upload enforces owner and compensates rollback`() {
        val product = draft()
        f.mvc.perform(multipart("/api/v1/petani/products/${product.id}/photo").file(file()))
            .andExpect(status().isUnauthorized)
        f.mvc.perform(multipart("/api/v1/petani/products/${product.id}/photo").file(file())
            .with(jwt().jwt { it.subject(f.otherFarmer.toString()) })).andExpect(status().isForbidden)
        val before = Files.list(directory).use { it.count() }
        TransactionTemplate(f.transactionManager).execute {
            photos.upload(f.farmer,product.id,file()); it.setRollbackOnly()
        }
        assertEquals(before,Files.list(directory).use { it.count() })
        assertNull(f.productService.detail(f.farmer,product.id).photoUrl)
    }
    @Test fun `photo replacement preserves old link until its expiry`() {
        val product = draft()
        val first = photos.upload(f.farmer,product.id,file())
        val second = photos.upload(f.farmer,product.id,file())
        assertNotEquals(first.photoUrl,second.photoUrl)
        assertEquals(second.photoUrl,f.productService.detail(f.farmer,product.id).photoUrl)
        f.mvc.perform(get(URI(first.photoAccessUrl))).andExpect(status().isOk)
    }
}
