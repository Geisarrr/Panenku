package com.panenku.backend

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.util.Date
import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.modules.pembeli.dto.*
import com.panenku.backend.modules.pembeli.service.*
import com.panenku.backend.modules.petani.dto.ProductRequest
import com.panenku.backend.modules.petani.service.ProductService
import com.panenku.backend.modules.shared.service.CatalogService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import java.util.concurrent.*

@SpringBootTest(properties = ["panenku.jwt.secret=test-only-32-byte-secret-for-tests-123456789", "panenku.jwt.issuer=panenku", "scheduler.enabled=false"])
@AutoConfigureMockMvc
@Testcontainers
@Import(CheckoutIntegrationTest.FixedTime::class)
class CheckoutIntegrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
        @DynamicPropertySource @JvmStatic fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
    class TestClock : Clock() {
        val time = java.util.concurrent.atomic.AtomicReference(Instant.parse("2026-11-10T00:00:00Z"))
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(time.get(), zone)
        override fun instant(): Instant = time.get()
    }
    @TestConfiguration class FixedTime {
        @Bean @Primary fun fixedClock(): Clock = TestClock()
    }
    @Autowired lateinit var clock: Clock
    @Autowired lateinit var db: JdbcTemplate
    @Autowired lateinit var jwtProvider: com.panenku.backend.common.auth.JwtProvider
    @Autowired lateinit var expiration: com.panenku.backend.scheduler.OrderExpirationService
    @Autowired lateinit var orderRepository: com.panenku.backend.modules.pembeli.repository.OrderRepository
    @Autowired lateinit var appConfig: com.panenku.backend.config.AppConfig
    @Autowired lateinit var checkout: CheckoutService
    @Autowired lateinit var cart: CartService
    @Autowired lateinit var productService: ProductService
    @Autowired lateinit var catalog: CatalogService
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    val buyer = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val otherBuyer = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val farmer = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val otherFarmer = UUID.fromString("00000000-0000-0000-0000-000000000004")
    val commodity = UUID.fromString("00000000-0000-0000-0000-000000000005")
    val product = UUID.fromString("00000000-0000-0000-0000-000000000006")
    val secondProduct = UUID.fromString("00000000-0000-0000-0000-000000000007")
    val date = LocalDate.parse("2026-11-12")
    @BeforeEach fun seed() {
        (clock as TestClock).time.set(Instant.parse("2026-11-10T00:00:00Z"))
        db.execute("TRUNCATE order_status_history, stock_reservations, order_items, orders, cart, products, commodities, farmer_payment_methods, farmer_profiles, users CASCADE")
        listOf(buyer to "pembeli", otherBuyer to "pembeli", farmer to "petani", otherFarmer to "petani").forEach { (id, role) ->
            db.update("INSERT INTO users(id,email,password_hash,role) VALUES (?,?,?,?)", id, "$id@example.test", "dummy-hash", role)
        }
        listOf(farmer, otherFarmer).forEach { id ->
            db.update("INSERT INTO farmer_profiles(id,store_name,pickup_location,service_area,delivery_fee,shipping_enabled,ktp_status,time_slots) VALUES (?,?,?,'kepanjen|dau',10000,true,'disetujui','08:00-10:00|13:00-15:00')", id, "Toko $id", "Lokasi $id")
            db.update("INSERT INTO farmer_payment_methods(id,farmer_id,method_type,bank_name,account_number,account_holder_name) VALUES (?,?,'bank_transfer','Bank Dummy','00123','Petani Dummy')", UUID.randomUUID(), id)
        }
        db.update("INSERT INTO commodities(id,name) VALUES (?,'Tomat')", commodity)
        addProduct(product, farmer, "Tomat Segar", "10.00", 12000)
        addProduct(secondProduct, farmer, "Sawi", "10.00", 8000)
        db.update("INSERT INTO settings(key,value) VALUES ('transaction_limit','10000000') ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value")
    }
    fun addProduct(id: UUID, owner: UUID, name: String, stock: String, price: Long) {
        db.update("INSERT INTO products(id,farmer_id,commodity_id,name,photo_url,price_per_kg,stock_physical,ready_date,publication_status) VALUES (?,?,?,?,?,?,?,'2026-11-12','aktif')", id, owner, commodity, name, "products/dummy.jpg", price, BigDecimal(stock))
    }
    fun request(quantity: String = "0.75", price: Long = 12000): CheckoutRequest = CheckoutRequest(
        listOf(CheckoutItemRequest(product, BigDecimal(quantity), price)), "pickup", date, "08:00-10:00", 0)
    fun count(table: String): Long = db.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!
    fun reserved(): BigDecimal = db.queryForObject("SELECT stock_reserved FROM products WHERE id=?", BigDecimal::class.java, product)!!
    fun <T> parallel(first: () -> T, second: () -> T): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2); val start = CountDownLatch(1)
        try {
            val futures = listOf(first, second).map { task -> pool.submit(Callable { ready.countDown(); start.await(); runCatching(task) }) }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown()
            return futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
    }

    @Test fun `expired order releases reserved stock once under concurrent jobs`() {
        val order = checkout.checkout(buyer, "expire-race", request())
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val results = parallel(
            { if (expiration.expire(order.orderId, farmer, order.expiresAt)) completed.incrementAndGet(); order },
            { if (expiration.expire(order.orderId, farmer, order.expiresAt)) completed.incrementAndGet(); order }
        )
        assertTrue(results.all { it.isSuccess }, results.toString()); assertEquals(1, completed.get())
        assertEquals(BigDecimal("0.00"), reserved())
        assertEquals(BigDecimal("10.00"), db.queryForObject("SELECT stock_physical FROM products WHERE id=?", BigDecimal::class.java, product))
        assertEquals("kedaluwarsa", db.queryForObject("SELECT order_status FROM orders WHERE id=?", String::class.java, order.orderId))
        assertEquals("released", db.queryForObject("SELECT status FROM stock_reservations WHERE order_id=?", String::class.java, order.orderId))
        assertEquals(2L, count("order_status_history"))
        assertEquals("system", db.queryForObject("SELECT actor_role FROM order_status_history WHERE to_status='kedaluwarsa'", String::class.java))
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM order_status_history WHERE to_status='kedaluwarsa' AND actor_id IS NULL", Long::class.java))
    }
    @Test fun `expiration honors exact cutoff and records previous status`() {
        val order = checkout.checkout(buyer, "expire-boundary", request())
        db.update("UPDATE orders SET order_status='menunggu_pembayaran',payment_status='bukti_ditolak' WHERE id=?", order.orderId)
        assertFalse(expiration.expire(order.orderId, farmer, order.expiresAt.minusNanos(1)))
        assertEquals(BigDecimal("0.75"), reserved())
        assertTrue(expiration.expire(order.orderId, farmer, order.expiresAt))
        assertFalse(expiration.expire(order.orderId, farmer, order.expiresAt.plusSeconds(1)))
        assertEquals("menunggu_pembayaran", db.queryForObject("SELECT from_status FROM order_status_history WHERE to_status='kedaluwarsa'", String::class.java))
    }
    @Test fun `expiration excludes payment verification paid future and terminal orders`() {
        val now = Instant.parse("2026-11-11T00:00:00Z")
        val cases = listOf(
            Triple("menunggu_petani", "belum_dibayar", true),
            Triple("menunggu_pembayaran", "belum_dibayar", true),
            Triple("menunggu_petani", "menunggu_verifikasi", false),
            Triple("verifikasi_pembayaran", "menunggu_verifikasi", false),
            Triple("diproses", "terverifikasi", false),
            Triple("dibatalkan", "belum_dibayar", false)
        )
        val orders = cases.mapIndexed { i, (status, payment, _) ->
            checkout.checkout(buyer, "expiry-case-$i", request("0.10")).also {
                db.update("UPDATE orders SET order_status=?,payment_status=? WHERE id=?", status, payment, it.orderId)
            }
        }
        val future = checkout.checkout(buyer, "expiry-future", request("0.10"))
        db.update("UPDATE orders SET expires_at=? WHERE id=?", java.sql.Timestamp.from(now.plusSeconds(1)), future.orderId)
        val candidates = orderRepository.findExpiredOrdersBefore(now, org.springframework.data.domain.PageRequest.of(0, 100))
        assertEquals(orders.take(2).map { it.orderId }.toSet(), candidates.map { it.id }.toSet())
        orders.forEachIndexed { i, order -> assertEquals(cases[i].third, expiration.expire(order.orderId, farmer, now)) }
        assertFalse(expiration.expire(future.orderId, farmer, now))
        assertEquals(BigDecimal("0.50"), reserved())
    }
    @Test fun `expiration failure rolls back order reservation stock and history together`() {
        val order = checkout.checkout(buyer, "expire-rollback", request())
        db.execute("CREATE FUNCTION test_fail_expiry() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN IF NEW.to_status = ''kedaluwarsa'' THEN RAISE EXCEPTION ''simulated expiry history failure''; END IF; RETURN NEW; END;'")
        db.execute("CREATE TRIGGER test_expiry_failure BEFORE INSERT ON order_status_history FOR EACH ROW EXECUTE FUNCTION test_fail_expiry()")
        try {
            assertThrows(Exception::class.java) { expiration.expire(order.orderId, farmer, order.expiresAt) }
            assertEquals("menunggu_petani", db.queryForObject("SELECT order_status FROM orders WHERE id=?", String::class.java, order.orderId))
            assertEquals("active", db.queryForObject("SELECT status FROM stock_reservations WHERE order_id=?", String::class.java, order.orderId))
            assertEquals(BigDecimal("0.75"), reserved()); assertEquals(1L, count("order_status_history"))
        } finally { db.execute("DROP TRIGGER test_expiry_failure ON order_status_history"); db.execute("DROP FUNCTION test_fail_expiry()") }
    }
    @Test fun `scheduler continues other orders when one expiration fails`() {
        val first = checkout.checkout(buyer, "expiry-batch-1", request())
        val second = checkout.checkout(otherBuyer, "expiry-batch-2", request())
        db.update("UPDATE orders SET expires_at=? WHERE id=?", java.sql.Timestamp.from(first.expiresAt.minusSeconds(2)), first.orderId)
        db.update("UPDATE orders SET expires_at=? WHERE id=?", java.sql.Timestamp.from(second.expiresAt.minusSeconds(1)), second.orderId)
        db.execute("""CREATE FUNCTION test_fail_one_expiry() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
            IF NEW.order_id = '${first.orderId}'::uuid AND NEW.to_status = 'kedaluwarsa' THEN
                RAISE EXCEPTION 'simulated isolated expiry failure';
            END IF; RETURN NEW; END; $$""")
        db.execute("CREATE TRIGGER test_one_expiry_failure BEFORE INSERT ON order_status_history FOR EACH ROW EXECUTE FUNCTION test_fail_one_expiry()")
        try {
            val job = com.panenku.backend.scheduler.ExpirationJob(orderRepository, expiration, Clock.fixed(first.expiresAt, ZoneOffset.UTC), 2)
            job.expireOverdueOrders()
            assertEquals("menunggu_petani", db.queryForObject("SELECT order_status FROM orders WHERE id=?", String::class.java, first.orderId))
            assertEquals("kedaluwarsa", db.queryForObject("SELECT order_status FROM orders WHERE id=?", String::class.java, second.orderId))
            assertEquals(BigDecimal("0.75"), reserved())
        } finally { db.execute("DROP TRIGGER test_one_expiry_failure ON order_status_history"); db.execute("DROP FUNCTION test_fail_one_expiry()") }
    }
    @Test fun `shared AppConfig binds the same JWT and storage settings`() {
        assertEquals("test-only-32-byte-secret-for-tests-123456789", appConfig.jwt.secret)
        assertEquals("panenku", appConfig.jwt.issuer)
        assertEquals(86400000L, appConfig.jwt.expiration)
        assertEquals("localhost:9000", appConfig.minio.endpoint)
        assertEquals("panenku-files", appConfig.minio.bucket)
    }

    @Test fun `audit fractional rupiah and null or missing fee are rejected`() {
        val requestJson = mapper.readTree(mapper.writeValueAsString(request())) as com.fasterxml.jackson.databind.node.ObjectNode
        val invalidBodies = listOf(
            requestJson.deepCopy().apply { put("expectedDeliveryFee", 0.9) },
            requestJson.deepCopy().apply { putNull("expectedDeliveryFee") },
            requestJson.deepCopy().apply { remove("expectedDeliveryFee") },
            requestJson.deepCopy().apply { (get("items")[0] as com.fasterxml.jackson.databind.node.ObjectNode).put("expectedPricePerKg", 12000.9) }
        )
        invalidBodies.forEachIndexed { i, body ->
            mvc.perform(post("/api/v1/buyer/checkout").with(jwt().jwt { it.subject(buyer.toString()) })
                .header("Idempotency-Key", "audit-numeric-$i").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
        assertEquals(0L, count("orders"))
    }
    @Test fun `audit missing idempotency header returns documented JSON error`() {
        mvc.perform(post("/api/v1/buyer/checkout").with(jwt().jwt { it.subject(buyer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request())))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }
    @Test fun `audit authentication failures return JSON and retain bearer challenge`() {
        listOf(null, "Bearer forged").forEach { authorizationHeader ->
            val req = get("/api/v1/buyer/cart")
            if (authorizationHeader != null) req.header("Authorization", authorizationHeader)
            mvc.perform(req).andExpect(status().isUnauthorized).andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
    }
    @Test fun `audit JWT helper rejects token without expiration`() {
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder().subject(buyer.toString()).issuer("panenku").build())
        token.sign(MACSigner("test-only-32-byte-secret-for-tests-123456789"))
        assertFalse(jwtProvider.validateToken(token.serialize()))
    }
    @Test fun `audit catalog filters invalid farmer role and uses locale independent search`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals(1, catalog.list("SAWI", null, "price_asc", 0, 20).items.size)
        } finally { java.util.Locale.setDefault(previous) }
        db.update("UPDATE users SET role='pembeli' WHERE id=?", farmer)
        assertEquals(0L, catalog.list("", null, "price_asc", 0, 20).totalElements)
    }
    @Test fun `audit unconfigured storage never reports a successful upload or deletion`() {
        val storage = com.panenku.backend.common.storage.FileStorage()
        val file = org.springframework.mock.web.MockMultipartFile("file", "dummy.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val upload = assertThrows(ApiException::class.java) { storage.upload("payment", file) }
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, upload.status)
        val deletion = assertThrows(ApiException::class.java) { storage.delete("payment/dummy.jpg") }
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, deletion.status)
    }
    @Test fun `audit independent buyers may use the same idempotency key`() {
        val results = parallel({ checkout.checkout(buyer, "client-key", request()) }, { checkout.checkout(otherBuyer, "client-key", request()) })
        assertTrue(results.all { it.isSuccess }, results.toString())
        assertNotEquals(results[0].getOrThrow().orderId, results[1].getOrThrow().orderId)
        assertEquals(2L, count("orders")); assertEquals(BigDecimal("1.50"), reserved())
    }
    @Test fun `audit checkout racing with expiration cannot corrupt stock`() {
        db.update("UPDATE products SET stock_physical=1 WHERE id=?", product)
        val old = checkout.checkout(buyer, "audit-old", request())
        val results = parallel(
            { assertTrue(expiration.expire(old.orderId, farmer, old.expiresAt)); old },
            { checkout.checkout(otherBuyer, "audit-new", request()) }
        )
        assertTrue(results[0].isSuccess, results.toString())
        if (results[1].isFailure) assertTrue(results[1].exceptionOrNull() is ApiException)
        val active = db.queryForObject("SELECT coalesce(sum(quantity_kg),0) FROM stock_reservations WHERE product_id=? AND status='active'", BigDecimal::class.java, product)!!
        assertEquals(0, active.compareTo(reserved()))
        assertTrue(active <= BigDecimal.ONE)
        assertEquals("released", db.queryForObject("SELECT status FROM stock_reservations WHERE order_id=?", String::class.java, old.orderId))
        assertEquals(BigDecimal("1.00"), db.queryForObject("SELECT stock_physical FROM products WHERE id=?", BigDecimal::class.java, product))
    }

    @Test fun `audit me returns current role and status without credentials`() {
        mvc.perform(get("/api/v1/auth/me").with(jwt().jwt { it.subject(buyer.toString()).claim("role", "admin") }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.id").value(buyer.toString()))
            .andExpect(jsonPath("$.data.role").value("pembeli")).andExpect(jsonPath("$.data.status").value("active"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
        db.update("UPDATE users SET status='suspended' WHERE id=?", buyer)
        mvc.perform(get("/api/v1/auth/me").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("suspended"))
        mvc.perform(get("/api/v1/buyer/cart").with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        db.update("DELETE FROM users WHERE id=?", buyer)
        mvc.perform(get("/api/v1/auth/me").with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isUnauthorized)
    }
    @Test fun `audit invalid or absent JWT subject is unauthorized`() {
        listOf(null, "not-a-uuid").forEach { subject ->
            val claims = JWTClaimsSet.Builder().issuer("panenku").expirationTime(Date.from(Instant.parse("2026-11-10T01:00:00Z")))
            if (subject != null) claims.subject(subject)
            val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims.build())
            token.sign(MACSigner("test-only-32-byte-secret-for-tests-123456789"))
            assertFalse(jwtProvider.validateToken(token.serialize()))
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token.serialize()))
                .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
    }
    @Test fun `audit HTTP routing errors have stable JSON responses`() {
        mvc.perform(put("/api/v1/auth/login").with(jwt().jwt { it.subject(buyer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed).andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        mvc.perform(post("/api/v1/buyer/cart/items").with(jwt().jwt { it.subject(buyer.toString()) })
            .contentType(MediaType.TEXT_PLAIN).content("not JSON"))
            .andExpect(status().isUnsupportedMediaType).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        mvc.perform(get("/api/v1/buyer/not-implemented").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test fun `audit concurrent registrations cannot duplicate email or profile`() {
        val body = """{"email":"audit.race@example.test","password":"password123","role":"pembeli","fullName":"Nama"}"""
        val results = parallel({ register(body).andReturn().response.status }, { register(body).andReturn().response.status })
        assertTrue(results.all { it.isSuccess }, results.toString())
        assertEquals(listOf(201, 409), results.map { it.getOrThrow() }.sorted())
        assertEquals(5L, count("users")); assertEquals(1L, count("buyer_profiles"))
    }

    @Test fun `audit first checkout and persisted replay have identical timestamp precision`() {
        (clock as TestClock).time.set(Instant.parse("2026-11-10T00:00:00.123456789Z"))
        val first = checkout.checkout(buyer, "audit-microseconds", request())
        val replay = checkout.checkout(buyer, "audit-microseconds", request())
        assertEquals(first, replay)
        assertEquals(Instant.parse("2026-11-11T00:00:00.123456Z"), first.expiresAt)
        assertEquals(1L, count("orders")); assertEquals(BigDecimal("0.75"), reserved())
    }
    @Test fun `UAT04 two buyers cannot oversell last kilogram`() {
        db.update("UPDATE products SET stock_physical=1 WHERE id=?", product)
        val results = parallel({ checkout.checkout(buyer, "race-1", request()) }, { checkout.checkout(otherBuyer, "race-2", request()) })
        assertEquals(1, results.count { it.isSuccess }); assertEquals(1L, count("orders")); assertEquals(1L, count("stock_reservations"))
        assertEquals(BigDecimal("0.75"), reserved())
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is ApiException)
    }
    @Test fun `UAT18 concurrent retries create only one order and reservation`() {
        val results = parallel({ checkout.checkout(buyer, "same-key", request()) }, { checkout.checkout(buyer, "same-key", request()) })
        assertTrue(results.all { it.isSuccess }, results.toString())
        assertEquals(results[0].getOrThrow().orderId, results[1].getOrThrow().orderId)
        assertEquals(1L, count("orders")); assertEquals(1L, count("stock_reservations")); assertEquals(1L, count("order_status_history"))
        assertEquals(BigDecimal("0.75"), reserved())
    }
    @Test fun `reusing idempotency key with different payload is rejected`() {
        checkout.checkout(buyer, "unique", request())
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "unique", request("0.50")) }
        assertEquals(1L, count("orders"))
    }
    @Test fun `UAT05 totals use exact kg and pickup is free`() {
        val req = request("2.5").copy(items = listOf(CheckoutItemRequest(product, BigDecimal("2.5"), 12000), CheckoutItemRequest(secondProduct, BigDecimal("1.25"), 8000)),
            fulfillmentMethod = "delivery", expectedDeliveryFee = 10000, deliveryAddress = "Alamat dummy", deliveryDistrict = "kepanjen")
        val result = checkout.checkout(buyer, "delivery", req)
        assertEquals(40000L, result.subtotalItems); assertEquals(50000L, result.totalPayment)
        assertEquals(2L, count("stock_reservations"))
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), result.expiresAt)
        assertEquals(0L, checkout.checkout(otherBuyer, "pickup", request()).deliveryFee)
    }
    @Test fun `UAT06 exact limit works and excess fee leaves no partial order`() {
        db.update("UPDATE products SET price_per_kg=10000000 WHERE id=?", product)
        assertEquals(10000000L, checkout.checkout(buyer, "at-limit", request("1", 10000000)).totalPayment)
        val invalid = request("1", 10000000).copy(fulfillmentMethod = "delivery", expectedDeliveryFee = 10000, deliveryAddress = "Alamat", deliveryDistrict = "kepanjen")
        assertThrows(ApiException::class.java) { checkout.checkout(otherBuyer, "above-limit", invalid) }
        assertEquals(1L, count("orders")); assertEquals(BigDecimal("1.00"), reserved())
    }
    @Test fun `UAT03 and UAT07 reject early date missing slot out of area and disabled delivery`() {
        val requests = listOf(request().copy(scheduledDate = date.minusDays(1)), request().copy(scheduledTimeSlot = "09:00-11:00"),
            request().copy(fulfillmentMethod = "delivery", expectedDeliveryFee = 10000, deliveryAddress = "Alamat", deliveryDistrict = "outside"))
        requests.forEachIndexed { i, req -> assertThrows(ApiException::class.java) { checkout.checkout(buyer, "invalid-$i", req) } }
        db.update("UPDATE farmer_profiles SET shipping_enabled=false WHERE id=?", farmer)
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "disabled", request().copy(fulfillmentMethod = "delivery", expectedDeliveryFee = 10000, deliveryAddress = "Alamat", deliveryDistrict = "kepanjen")) }
        assertEquals(0L, count("orders")); assertEquals(BigDecimal("0.00"), reserved())
    }
    @Test fun `UAT11 stored snapshots survive product and profile changes and replay`() {
        val first = checkout.checkout(buyer, "snapshot", request())
        db.update("UPDATE products SET name='Changed',price_per_kg=15000,ready_date='2026-11-20' WHERE id=?", product)
        db.update("UPDATE farmer_profiles SET delivery_fee=20000,pickup_location='New location' WHERE id=?", farmer)
        db.update("UPDATE settings SET value='100' WHERE key='transaction_limit'")
        assertEquals(first, checkout.checkout(buyer, "snapshot", request()))
        assertThrows(Exception::class.java) { db.update("UPDATE orders SET delivery_fee=1,total_payment=total_payment+1 WHERE id=?", first.orderId) }
        assertThrows(Exception::class.java) { db.update("UPDATE order_items SET product_name_snapshot='tampered' WHERE order_id=?", first.orderId) }
    }
    @Test fun `changed price or delivery fee requires buyer consent`() {
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "old-price", request(price = 11000)) }
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "old-fee", request().copy(fulfillmentMethod = "delivery", expectedDeliveryFee = 0, deliveryAddress = "Alamat", deliveryDistrict = "kepanjen")) }
        assertEquals(0L, count("orders")); assertEquals(BigDecimal("0.00"), reserved())
    }
    @Test fun `one unavailable item rejects the whole checkout`() {
        db.update("UPDATE products SET stock_physical=0 WHERE id=?", secondProduct)
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "partial", request().copy(items = request().items + CheckoutItemRequest(secondProduct, BigDecimal.ONE, 8000))) }
        assertEquals(0L, count("orders")); assertEquals(0L, count("order_items")); assertEquals(0L, count("stock_reservations")); assertEquals(BigDecimal("0.00"), reserved())
    }
    @Test fun `database failure after inserting order rolls back all writes`() {
        db.execute("CREATE FUNCTION test_fail_reservation() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated storage failure''; END;'")
        db.execute("CREATE TRIGGER test_failure BEFORE INSERT ON stock_reservations FOR EACH ROW EXECUTE FUNCTION test_fail_reservation()")
        try {
            assertThrows(Exception::class.java) { checkout.checkout(buyer, "db-failure", request()) }
            assertEquals(0L, count("orders")); assertEquals(0L, count("order_items")); assertEquals(0L, count("stock_reservations")); assertEquals(0L, count("order_status_history")); assertEquals(BigDecimal("0.00"), reserved())
        } finally {
            db.execute("DROP TRIGGER test_failure ON stock_reservations"); db.execute("DROP FUNCTION test_fail_reservation()")
        }
    }
    @Test fun `UAT16 old cart cannot bypass farmer suspension`() {
        cart.add(buyer, CartItemRequest(product, BigDecimal.ONE))
        db.update("UPDATE users SET status='suspended' WHERE id=?", farmer)
        assertFalse(cart.get(buyer).items.single().available)
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "suspended", request()) }
        assertEquals(0L, catalog.list("", null, "price_asc", 0, 20).totalElements)
        assertEquals(0L, count("orders"))
    }
    @Test fun `cart persists quantities recalculates and does not reserve stock`() {
        val item = cart.add(buyer, CartItemRequest(product, BigDecimal("2.5"))).items.single()
        assertEquals(BigDecimal("30000"), cart.get(buyer).subtotalItems)
        assertEquals(BigDecimal("0.00"), reserved())
        cart.update(buyer, item.id, BigDecimal("1.25"))
        assertEquals(BigDecimal("15000"), cart.get(buyer).subtotalItems)
        assertThrows(ApiException::class.java) { cart.update(otherBuyer, item.id, BigDecimal.ONE) }
        cart.delete(buyer, item.id); assertTrue(cart.get(buyer).items.isEmpty())
    }
    @Test fun `mixed farmer and duplicate product checkout are rejected`() {
        db.update("UPDATE products SET farmer_id=? WHERE id=?", otherFarmer, secondProduct)
        cart.add(buyer, CartItemRequest(product, BigDecimal.ONE))
        assertThrows(ApiException::class.java) { cart.add(buyer, CartItemRequest(secondProduct, BigDecimal.ONE)) }
        assertEquals(1, cart.get(buyer).items.size)
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "mixed", request().copy(items = request().items + CheckoutItemRequest(secondProduct, BigDecimal.ONE, 8000))) }
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "duplicate", request().copy(items = request().items + request().items)) }
        assertEquals(0L, count("orders"))
    }
    @Test fun `UAT02 unverified farmer can save draft but cannot publish`() {
        db.update("UPDATE farmer_profiles SET ktp_status='menunggu' WHERE id=?", farmer)
        val req = ProductRequest(commodity, "Draft", 10000, BigDecimal.ONE, date, photoUrl = "products/dummy.jpg")
        assertEquals("draft", productService.save(farmer, null, req).publicationStatus)
        assertThrows(ApiException::class.java) { productService.save(farmer, null, req.copy(publicationStatus = "aktif")) }
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "unverified", request()) }
    }
    @Test fun `product ownership and reserved stock floor are enforced`() {
        checkout.checkout(buyer, "reserve", request())
        val req = ProductRequest(commodity, "Updated", 12000, BigDecimal("0.5"), date, "aktif", photoUrl = "products/dummy.jpg")
        assertThrows(ApiException::class.java) { productService.save(farmer, product, req) }
        assertThrows(ApiException::class.java) { productService.save(otherFarmer, product, req.copy(stockPhysical = BigDecimal.TEN)) }
        assertEquals(BigDecimal("0.75"), reserved())
    }
    @Test fun `catalog searches commodities and stores filters and sorts price`() {
        assertEquals(2, catalog.list("tomat", commodity, "price_asc", 0, 20).items.size)
        assertEquals(secondProduct, catalog.list("Toko", null, "price_asc", 0, 20).items.first().id)
        assertEquals(product, catalog.list("", null, "price_desc", 0, 20).items.first().id)
        assertEquals(0, catalog.list("%", null, "price_asc", 0, 20).items.size)
    }

    @Test fun `JWT signature issuer and expiration are validated by the real decoder`() {
        fun token(secret: String = "test-only-32-byte-secret-for-tests-123456789", issuer: String = "panenku", expiry: Instant? = Instant.parse("2026-11-10T00:00:00Z").plusSeconds(3600)): String {
            val claims = JWTClaimsSet.Builder().subject(buyer.toString()).issuer(issuer)
            if (expiry != null) claims.expirationTime(Date.from(expiry))
            val signed = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims.build())
            signed.sign(MACSigner(secret))
            return signed.serialize()
        }
        mvc.perform(get("/api/v1/buyer/cart").header("Authorization", "Bearer " + token())).andExpect(status().isOk)
        listOf(token(issuer = "wrong"), token(expiry = Instant.parse("2026-11-10T00:00:00Z").minusSeconds(3600)), token(expiry = null),
            token(secret = "another-test-only-32-byte-secret-123456789")).forEach {
            mvc.perform(get("/api/v1/buyer/cart").header("Authorization", "Bearer $it")).andExpect(status().isUnauthorized)
        }
    }

    private fun register(body: String) = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
    private fun loginToken(email: String, password: String = "password123"): String {
        val body = mapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andReturn()
        return mapper.readTree(response.response.contentAsString)["data"].asText()
    }
    @Test fun `Geisar registration login and authenticated buyer cart work together`() {
        val response = register("""{"email":"Buyer.New@example.test","password":"password123","role":"pembeli","fullName":"Pembeli Baru","contactPhone":"081234"}""")
            .andExpect(status().isCreated).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        assertEquals("Pembeli Baru", db.queryForObject("SELECT full_name FROM buyer_profiles WHERE id=?", String::class.java, id))
        val hash = db.queryForObject("SELECT password_hash FROM users WHERE id=?", String::class.java, id)
        assertNotEquals("password123", hash)
        assertTrue(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("password123", hash))
        val token = loginToken("BUYER.NEW@example.test")
        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertEquals(id.toString(), claims.subject); assertEquals("panenku", claims.issuer)
        mvc.perform(get("/api/v1/buyer/cart").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        mvc.perform(get("/api/v1/petani/products").header("Authorization", "Bearer $token")).andExpect(status().isForbidden)
    }
    @Test fun `farmer registration creates unverified profile and can create draft`() {
        val response = register("""{"email":"new.farmer@example.test","password":"password123","role":"petani","storeName":"Toko Baru","contactPhone":"081234"}""")
            .andExpect(status().isCreated).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        assertEquals("belum_diajukan", db.queryForObject("SELECT ktp_status FROM farmer_profiles WHERE id=?", String::class.java, id))
        assertEquals("081234", db.queryForObject("SELECT contact_phone FROM farmer_profiles WHERE id=?", String::class.java, id))
        val token = loginToken("new.farmer@example.test")
        val request = ProductRequest(commodity, "Draft Auth", 12000, BigDecimal.ONE, date)
        mvc.perform(post("/api/v1/petani/products").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.publicationStatus").value("draft"))
        mvc.perform(post("/api/v1/petani/products").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request.copy(publicationStatus = "aktif"))))
            .andExpect(status().isForbidden)
    }
    @Test fun `auth validates requests and public admin signup stays forbidden`() {
        register("""{"email":"admin@example.test","password":"password123","role":"admin"}""").andExpect(status().isForbidden)
        listOf(
            """{"email":"bad","password":"password123","role":"pembeli","fullName":"Nama"}""",
            """{"email":"new@example.test","password":"short","role":"pembeli","fullName":"Nama"}""",
            """{"email":"new@example.test","password":"password123","role":"unknown"}""",
            """{"email":"new@example.test","password":"password123","role":"petani"}""",
            """{"email":"new@example.test","password":"password123","role":"pembeli"}"""
        ).forEach { register(it).andExpect(status().isBadRequest) }
        assertEquals(4L, count("users"))
        register("""{"email":"ok@example.test","password":"password123","role":"pembeli","fullName":"Nama"}""").andExpect(status().isCreated)
        register("""{"email":"OK@example.test","password":"password123","role":"pembeli","fullName":"Nama"}""").andExpect(status().isConflict)
    }
    @Test fun `wrong credentials fail without exposing account details`() {
        register("""{"email":"auth@example.test","password":"password123","role":"pembeli","fullName":"Nama"}""").andExpect(status().isCreated)
        listOf("auth@example.test", "missing@example.test").forEach {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("email" to it, "password" to "wrong-password"))))
                .andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("Email atau password salah"))
        }
    }
    @Test fun `failed profile save rolls registration back atomically`() {
        db.execute("CREATE FUNCTION test_fail_profile() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated profile failure''; END;'")
        db.execute("CREATE TRIGGER test_profile_failure BEFORE INSERT ON buyer_profiles FOR EACH ROW EXECUTE FUNCTION test_fail_profile()")
        try {
            register("""{"email":"rollback@example.test","password":"password123","role":"pembeli","fullName":"Nama"}""")
                .andExpect(status().isInternalServerError).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Terjadi kesalahan server. Silakan coba lagi."))
            assertEquals(4L, count("users")); assertEquals(0L, count("buyer_profiles"))
        } finally { db.execute("DROP TRIGGER test_profile_failure ON buyer_profiles"); db.execute("DROP FUNCTION test_fail_profile()") }
    }
    @Test fun `HTTP public catalog and protected buyer operations respect database roles`() {
        mvc.perform(get("/api/v1/buyer/products")).andExpect(status().isOk)
        mvc.perform(get("/api/v1/buyer/cart")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/buyer/cart").header("Authorization", "Bearer forged")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/buyer/cart").with(jwt().jwt { it.subject(farmer.toString()).claim("role", "pembeli") })).andExpect(status().isForbidden)
        mvc.perform(post("/api/v1/admin/commodities").with(jwt().jwt { it.subject(buyer.toString()).claim("role", "admin") })
            .contentType(MediaType.APPLICATION_JSON).content("""{"name":"Cabai"}""")).andExpect(status().isForbidden)
        mvc.perform(post("/api/v1/buyer/checkout").with(jwt().jwt { it.subject(buyer.toString()) })
            .header("Idempotency-Key", "http-checkout").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request())))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.orderStatus").value("menunggu_petani"))
    }
}
