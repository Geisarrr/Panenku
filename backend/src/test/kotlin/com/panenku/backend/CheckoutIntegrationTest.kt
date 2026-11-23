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
import com.panenku.backend.modules.shared.dto.FulfillmentUpdateRequest
import com.panenku.backend.modules.shared.dto.OrderCancelRequest
import com.panenku.backend.modules.shared.dto.OrderRespondRequest
import com.panenku.backend.modules.shared.service.CatalogService
import com.panenku.backend.modules.shared.service.OrderLifecycleService
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

@SpringBootTest(properties = ["panenku.jwt.secret=test-only-32-byte-secret-for-tests-123456789", "panenku.jwt.issuer=panenku", "scheduler.enabled=false", "panenku.ktp.dummy-enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
@Import(CheckoutIntegrationTest.FixedTime::class)
class CheckoutIntegrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
        @JvmStatic val paymentImageDir = java.nio.file.Files.createTempDirectory("panenku-payment-test-")
        @DynamicPropertySource @JvmStatic fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("panenku.storage.local-dir") { paymentImageDir.toString() }
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
    @Autowired lateinit var orderHistoryRepository: com.panenku.backend.modules.pembeli.repository.OrderStatusHistoryRepository
    @Autowired lateinit var appConfig: com.panenku.backend.config.AppConfig
    @Autowired lateinit var checkout: CheckoutService
    @Autowired lateinit var cart: CartService
    @Autowired lateinit var lifecycle: OrderLifecycleService
    @Autowired lateinit var productService: ProductService
    @Autowired lateinit var catalog: CatalogService
    @Autowired lateinit var scheduleGuard: com.panenku.backend.modules.shared.service.OrderScheduleGuard
    @Autowired lateinit var paymentDestinations: com.panenku.backend.modules.shared.service.PaymentDestinationService
    @Autowired lateinit var farmerPaymentMethods: com.panenku.backend.modules.petani.service.FarmerPaymentMethodService
    @Autowired lateinit var paymentSubmissions: com.panenku.backend.modules.shared.service.PaymentSubmissionService
    @Autowired lateinit var notifications: com.panenku.backend.modules.shared.service.NotificationService
    @Autowired lateinit var refunds: com.panenku.backend.modules.shared.service.RefundService
    @Autowired lateinit var latePayments: com.panenku.backend.modules.shared.service.LatePaymentReportService
    @Autowired lateinit var dummyKtp: com.panenku.backend.modules.petani.service.DummyKtpService
    @Autowired lateinit var actors: com.panenku.backend.common.security.ActorService
    @Autowired lateinit var paymentMethodRepository: com.panenku.backend.modules.shared.repository.PaymentMethodRepository
    @Autowired lateinit var userRepository: com.panenku.backend.modules.shared.repository.UserRepository
    @Autowired lateinit var farmerProfileRepository: com.panenku.backend.modules.shared.repository.FarmerProfileRepository
    @Autowired lateinit var productRepository: com.panenku.backend.modules.shared.repository.ProductRepository
    @Autowired lateinit var stockReservationRepository: com.panenku.backend.modules.pembeli.repository.StockReservationRepository
    @Autowired lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager
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
    lateinit var paymentOption: com.panenku.backend.modules.shared.service.PaymentOptionDTO
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
        db.update("INSERT INTO admin_settings_revision(id,revision) VALUES (1,1) ON CONFLICT (id) DO UPDATE SET revision=1,updated_by=NULL,updated_at=NULL")
        paymentOption = paymentDestinations.listForBuyer(buyer, farmer).single()
    }
    fun addProduct(id: UUID, owner: UUID, name: String, stock: String, price: Long) {
        db.update("INSERT INTO products(id,farmer_id,commodity_id,name,photo_url,price_per_kg,stock_physical,ready_date,publication_status) VALUES (?,?,?,?,?,?,?,'2026-11-12','aktif')", id, owner, commodity, name, "products/dummy.jpg", price, BigDecimal(stock))
    }
    fun request(quantity: String = "0.75", price: Long = 12000): CheckoutRequest = CheckoutRequest(
        listOf(CheckoutItemRequest(product, BigDecimal(quantity), price)), "pickup", date, "08:00-10:00", 0)
    fun deliveryRequest(quantity: String = "0.75"): CheckoutRequest = request(quantity).copy(
        fulfillmentMethod = "delivery", expectedDeliveryFee = 10000,
        deliveryAddress = "Alamat dummy", deliveryDistrict = "kepanjen")

    private fun reviewOrder(finished: Boolean = true): UUID {
        val id = checkout.checkout(buyer, "review-${UUID.randomUUID()}", request()).orderId
        if (finished) db.update("UPDATE orders SET order_status='selesai', received_at=? WHERE id=?",
            java.sql.Timestamp.from(clock.instant()), id)
        return id
    }
    private fun submitReview(id: UUID, body: String = "{\"rating\":5,\"comment\":\"Segar\"}", actor: UUID = buyer) =
        mvc.perform(post("/api/v1/buyer/orders/$id/review").with(jwt().jwt { it.subject(actor.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(body))

    @Test fun `review persists once and updates real farmer rating`() {
        val id = reviewOrder()
        mvc.perform(get("/api/v1/buyer/orders/$id/review").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk)
        submitReview(id).andExpect(status().isCreated).andExpect(jsonPath("$.data.rating").value(5))
            .andExpect(jsonPath("$.data.orderId").value(id.toString()))
            .andExpect(jsonPath("$.data.farmerId").value(farmer.toString()))
        mvc.perform(get("/api/v1/buyer/orders/$id/review").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.comment").value("Segar"))
        mvc.perform(get("/api/v1/buyer/products/$product").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.farmerRating.reviewCount").value(1))
        submitReview(id).andExpect(status().isConflict)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM reviews WHERE order_id=?", Int::class.java, id))
    }
    @Test fun `review rejects another buyer and unauthorized role or suspension`() {
        val id = reviewOrder()
        submitReview(id, actor = otherBuyer).andExpect(status().isForbidden)
        submitReview(id, actor = farmer).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/buyer/orders/$id/review").with(jwt().jwt { it.subject(otherBuyer.toString()) }))
            .andExpect(status().isForbidden)
        db.update("UPDATE users SET status='suspended' WHERE id=?", buyer)
        submitReview(id).andExpect(status().isForbidden)
    }
    @Test fun `review rejects every unfinished status`() {
        val id = reviewOrder(false)
        listOf("menunggu_petani", "menunggu_pembayaran", "diproses", "diantar", "siap_diambil", "menunggu_penerimaan", "ditolak", "dibatalkan", "kedaluwarsa", "gagal_dipenuhi").forEach { state ->
            db.update("UPDATE orders SET order_status=? WHERE id=?", state, id)
            submitReview(id).andExpect(status().isConflict)
        }
        assertEquals(0, db.queryForObject("SELECT count(*) FROM reviews", Int::class.java))
    }
    @Test fun `review validates stars and comment boundaries`() {
        val id = reviewOrder()
        listOf("{}", "{\"rating\":null}", "{\"rating\":0}", "{\"rating\":6}", "{\"rating\":1.5}",
            mapper.writeValueAsString(mapOf("rating" to 5, "comment" to "a".repeat(501)))).forEach {
            submitReview(id, it).andExpect(status().isBadRequest)
        }
        submitReview(id, mapper.writeValueAsString(mapOf("rating" to 1, "comment" to "a".repeat(500))))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.rating").value(1))
    }
    @Test fun `review permits optional comment and rejects missing order`() {
        submitReview(UUID.randomUUID()).andExpect(status().isNotFound)
        submitReview(reviewOrder(), "{\"rating\":4}").andExpect(status().isCreated)
    }
    @Test fun `concurrent review submissions create exactly one review`() {
        val id = reviewOrder()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val tasks = (1..2).map { pool.submit<Int> { start.await(); submitReview(id).andReturn().response.status } }
            start.countDown()
            assertEquals(listOf(201, 409), tasks.map { it.get(20, TimeUnit.SECONDS) }.sorted())
            assertEquals(1, db.queryForObject("SELECT count(*) FROM reviews WHERE order_id=?", Int::class.java, id))
        } finally { pool.shutdownNow() }
    }

    @Test fun `checkout creates private notification and recipient can mark it read`() {
        val order = checkout.checkout(buyer, "notification-${UUID.randomUUID()}", request())
        val notificationId = db.queryForObject("SELECT id FROM notifications WHERE user_id=? AND type='order'", UUID::class.java, farmer)!!
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.unreadCount").value(1))
            .andExpect(jsonPath("$.data.items[0].deepLink").value("/orders/${order.orderId}"))
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(0))
        mvc.perform(put("/api/v1/notifications/$notificationId/read").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isNotFound)
        mvc.perform(put("/api/v1/notifications/$notificationId/read").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.isRead").value(true))
        mvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(0))
        db.update("UPDATE users SET status='suspended' WHERE id=?", farmer)
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(1))
    }
    /**
     * Membuat pesanan lalu memaksa status lewat SQL. Status pemenuhan F5 membutuhkan
     * pembayaran terverifikasi, sedangkan endpoint F6 belum tersedia.
     */
    fun orderFor(
        req: CheckoutRequest = request(),
        status: String = "menunggu_petani",
        payment: String = "belum_dibayar",
        owner: UUID = buyer
    ): UUID {
        val order = checkout.checkout(owner, "f5-${UUID.randomUUID()}", req)
        db.update("UPDATE orders SET order_status=?, payment_status=? WHERE id=?", status, payment, order.orderId)
        return order.orderId
    }
    fun stockPhysical(): BigDecimal =
        db.queryForObject("SELECT stock_physical FROM products WHERE id=?", BigDecimal::class.java, product)!!
    fun reservationStatus(orderId: UUID): String =
        db.queryForObject("SELECT status FROM stock_reservations WHERE order_id=?", String::class.java, orderId)!!
    fun orderStatus(orderId: UUID): String =
        db.queryForObject("SELECT order_status FROM orders WHERE id=?", String::class.java, orderId)!!
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

    private fun testQris(): ByteArray = java.io.ByteArrayOutputStream().use { output ->
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output)
        output.toByteArray()
    }

    private fun qrisUpload(bytes: ByteArray = testQris()) =
        org.springframework.mock.web.MockMultipartFile("image", "qris.png", "image/png", bytes)

    private fun bankRequest(bank: String = "Bank Contoh", number: String = "00012345", holder: String = "Petani Contoh") =
        com.panenku.backend.modules.petani.dto.BankPaymentMethodRequest(bank, number, holder)

    @Test fun `farmer submits dummy ktp and admin reviews with restricted image access`() {
        val admin = createTestAdmin()
        db.update("UPDATE farmer_profiles SET ktp_status='belum_diajukan' WHERE id=?", farmer)
        val submit = "/api/v1/petani/ktp/application"
        fun upload(key: String, nik: String = "DUMMY-0001") = mvc.perform(multipart(submit).file(qrisUpload())
            .param("dummyName", "Petani Contoh").param("dummyNik", nik).header("Idempotency-Key", key)
            .with(jwt().jwt { it.subject(farmer.toString()) }))
        upload("bad-dummy", "1234567890123456").andExpect(status().isBadRequest)
        val created = upload("ktp-demo").andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("menunggu"))
            .andExpect(jsonPath("$.data.dummyNik").value("DUMMY-0001")).andReturn().response.contentAsString
        val id = mapper.readTree(created)["data"]["applicationId"].asText()
        mvc.perform(get("/api/v1/petani/ktp/document").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(content().bytes(testQris()))
        mvc.perform(get("/api/v1/petani/ktp/document").with(jwt().jwt { it.subject(otherFarmer.toString()) }))
            .andExpect(status().isNotFound)
        upload("ktp-demo").andExpect(status().isCreated).andExpect(jsonPath("$.data.applicationId").value(id))
        upload("another-key").andExpect(status().isConflict)
        mvc.perform(get("/api/v1/admin/farmers/verification-queue").param("search", id)
            .with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(1))
        val image = "/api/v1/admin/farmers/$farmer/ktp-document"
        mvc.perform(get(image).with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get(image).with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isOk)
            .andExpect(content().bytes(testQris())).andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(put("/api/v1/admin/farmers/$farmer/verify").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"approved":false,"rejectionReason":"Foto dummy kurang jelas"}"""))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/petani/ktp/status").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("ditolak"))
        mvc.perform(get("/api/v1/admin/farmers/verification-queue").param("status", "ditolak")
            .with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(1))
        upload("new-ktp").andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("menunggu"))
        upload("ktp-demo").andExpect(status().isConflict)
        mvc.perform(get("/api/v1/admin/farmers/$farmer/verification").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.dummyName").value("Petani Contoh"))
            .andExpect(jsonPath("$.data.storeName").value("Toko $farmer"))
        mvc.perform(put("/api/v1/admin/farmers/$farmer/verify").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"approved":true}""")).andExpect(status().isOk)
        mvc.perform(get("/api/v1/petani/ktp/status").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("disetujui"))
        mvc.perform(get("/api/v1/admin/farmers/verification-queue").param("status", "disetujui")
            .with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(2))
        upload("after-approved").andExpect(status().isConflict)
    }

    @Test fun `dummy ktp retries serialize and failed inserts clean up the file`() {
        db.update("UPDATE farmer_profiles SET ktp_status='belum_diajukan' WHERE id=?", farmer)
        val results = parallel(
            { dummyKtp.submit(farmer, "ktp-race", "Asep Dummy", "DUMMY-0001", qrisUpload()) },
            { dummyKtp.submit(farmer, "ktp-race", "Asep Dummy", "DUMMY-0001", qrisUpload()) }
        )
        assertTrue(results.all { it.isSuccess }, results.toString())
        assertEquals(results[0].getOrThrow().applicationId, results[1].getOrThrow().applicationId)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM ktp_dummy_applications WHERE farmer_id=?", Int::class.java, farmer))
        db.update("UPDATE farmer_profiles SET ktp_status='belum_diajukan' WHERE id=?", otherFarmer)
        val before = java.nio.file.Files.list(paymentImageDir).use { it.count() }
        db.execute("CREATE FUNCTION test_ktp_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated failure''; END;'")
        db.execute("CREATE TRIGGER test_ktp_failure BEFORE INSERT ON ktp_dummy_applications FOR EACH ROW EXECUTE FUNCTION test_ktp_failure()")
        try {
            assertThrows(Exception::class.java) { dummyKtp.submit(otherFarmer, "ktp-rollback", "Nama Dummy", "DUMMY-0002", qrisUpload()) }
            assertEquals(before, java.nio.file.Files.list(paymentImageDir).use { it.count() })
            assertEquals("belum_diajukan", db.queryForObject("SELECT ktp_status FROM farmer_profiles WHERE id=?", String::class.java, otherFarmer))
        } finally {
            db.execute("DROP TRIGGER test_ktp_failure ON ktp_dummy_applications")
            db.execute("DROP FUNCTION test_ktp_failure()")
        }
    }

    @Test fun `dummy ktp is disabled by default without explicit opt in`() {
        val disabled = com.panenku.backend.modules.petani.service.DummyKtpService(
            actors, farmerProfileRepository, userRepository, db, mapper, clock, paymentImageDir.toString(), false)
        val ex = assertThrows(ApiException::class.java) { disabled.status(farmer) }
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, ex.status)
    }

    @Test fun `profile updates preserve checkout snapshots and validate delivery slots`() {
        val old = checkout.checkout(buyer, "before-profile-edit", request())
        val body = """{"storeName":"Asep Laris","pickupLocation":"Lokasi baru","serviceArea":["Kepanjen"],"deliveryFee":9000,"shippingEnabled":true,"timeSlots":["08:00-10:00"]}"""
        mvc.perform(put("/api/v1/petani/profile").with(jwt().jwt { it.subject(buyer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/petani/profile").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.serviceArea[0]").value("kepanjen"))
        assertEquals("Lokasi $farmer", db.queryForObject("SELECT pickup_location_snapshot FROM orders WHERE id=?", String::class.java, old.orderId))
        mvc.perform(put("/api/v1/petani/profile").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("08:00-10:00", "23:00-01:00"))).andExpect(status().isBadRequest)
        mvc.perform(put("/api/v1/auth/profile").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"fullName":"Asep","contactPhone":"08000000000"}"""))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/petani/profile").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.ownerName").value("Asep"))
            .andExpect(jsonPath("$.data.storeName").value("Asep Laris"))
        val admin = createTestAdmin()
        mvc.perform(get("/api/v1/admin/users/$farmer").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.ownerName").value("Asep"))
    }

    @Test fun `demo seed only touches dedicated empty local database and is repeatable`() {
        val connection = java.sql.DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        connection.use { it.createStatement().use { stmt -> stmt.execute("CREATE DATABASE panenku_demo") } }
        val uri = java.net.URI(postgres.jdbcUrl.removePrefix("jdbc:"))
        val jdbcUrl = "jdbc:postgresql://${uri.host}:${uri.port}/panenku_demo"
        org.flywaydb.core.Flyway.configure().dataSource(jdbcUrl, postgres.username, postgres.password).load().migrate()
        val temp = java.nio.file.Files.createTempDirectory("panenku-demo-seeder-test-")
        val storage = temp.resolve("panenku-demo-storage")
        fun run(url: String): Pair<Int, String> {
            val process = ProcessBuilder("python3", "scripts/seed_demo.py").redirectErrorStream(true).apply {
                environment()["PANENKU_DEMO_DATABASE_URL"] = url
                environment()["PANENKU_DEMO_CONFIRM"] = "YES"
                environment()["PANENKU_DEMO_PASSWORD"] = "test-only-demo-password"
                environment()["PANENKU_DEMO_STORAGE_DIR"] = storage.toString()
                environment()["PGUSER"] = postgres.username
                environment()["PGPASSWORD"] = postgres.password
            }.start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.waitFor(60, TimeUnit.SECONDS))
            return process.exitValue() to output
        }
        val bad = run("postgresql://${uri.host}:${uri.port}/not_demo")
        assertNotEquals(0, bad.first)
        val demoUrl = "postgresql://${uri.host}:${uri.port}/panenku_demo"
        val first = run(demoUrl)
        assertEquals(0, first.first, first.second)
        assertEquals(3, java.nio.file.Files.list(storage).use { it.count().toInt() })
        val again = run(demoUrl)
        assertEquals(0, again.first, again.second)
        assertTrue(again.second.contains("sudah pernah"))
        java.sql.DriverManager.getConnection(jdbcUrl, postgres.username, postgres.password).use { dbConn ->
            dbConn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT password_hash FROM users WHERE email='mok@example.test'").use { rs ->
                    assertTrue(rs.next()); assertTrue(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("test-only-demo-password", rs.getString(1)))
                }
                stmt.executeQuery("SELECT (SELECT count(*) FROM orders),(SELECT count(*) FROM refunds),(SELECT count(*) FROM payment_submissions),(SELECT count(*) FROM order_status_history)").use { rs ->
                    assertTrue(rs.next()); assertEquals(2, rs.getInt(1)); assertEquals(1, rs.getInt(2)); assertEquals(2, rs.getInt(3)); assertEquals(10, rs.getInt(4))
                }
            }
        }
    }

    @Test fun `payment proof metadata and farmer destination respect the order snapshot`() {
        val order = awaitingPayment()
        val bytes = testQris()
        val upload = org.springframework.mock.web.MockMultipartFile("image", "C:\\fakepath\\bukti.png", "image/png", bytes)
        val proof = paymentSubmissions.submit(buyer, order, "proof-metadata", upload)
        assertEquals("bukti.png", proof.originalFileName)
        assertEquals(bytes.size.toLong(), proof.fileSizeBytes)
        assertEquals(proof, paymentSubmissions.list(farmer, "petani", order).single())
        assertEquals(proof, paymentSubmissions.submit(buyer, order, "proof-metadata", upload))
        val endpoint = "/api/v1/petani/orders/$order/payment-destination"
        mvc.perform(get(endpoint).with(jwt().jwt { it.subject(otherFarmer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get(endpoint).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accountNumber").value("00123"))
        farmerPaymentMethods.updateBank(farmer, paymentOption.id, bankRequest(number = "00999"))
        mvc.perform(get(endpoint).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accountNumber").value("00123"))
    }

    @Test fun `commodity changes are auditable and settings have a shared revision`() {
        val admin = createTestAdmin()
        mvc.perform(put("/api/v1/auth/profile").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"fullName":"Admin Maya"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.fullName").value("Admin Maya"))
        val auth = jwt().jwt { it.subject(admin.toString()) }
        val body = """{"name":"Sawi Contoh","isActive":true}"""
        val created = mvc.perform(post("/api/v1/admin/commodities").with(auth).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val commodityId = UUID.fromString(mapper.readTree(created)["data"]["id"].asText())
        mvc.perform(put("/api/v1/admin/commodities/$commodityId").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"name":"Sawi Contoh","isActive":false}""")).andExpect(status().isOk)
        assertEquals(2, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND action IN ('COMMODITY_CREATED','COMMODITY_UPDATED')", Int::class.java, commodityId))
        val settingsBody = """{"newLimit":8000,"csWhatsapp":"+6280000000000","expectedTransactionLimit":10000000,"expectedCsWhatsapp":null,"reason":"Demo"}"""
        mvc.perform(put("/api/v1/admin/settings").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(settingsBody))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.settingsVersion").value(2))
            .andExpect(jsonPath("$.data.lastChangedBy").value(admin.toString()))
            .andExpect(jsonPath("$.data.lastChangedByName").value("Admin Maya"))
        assertEquals(2, db.queryForObject("SELECT count(*) FROM audit_logs WHERE settings_revision=2", Int::class.java))
        mvc.perform(get("/api/v1/admin/audit-logs").param("action","CS_WHATSAPP_CHANGED")
            .with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].settingsVersion").value(2))
            .andExpect(jsonPath("$.data.items[0].actorEmail").value("$admin@example.test"))
            .andExpect(jsonPath("$.data.items[0].actorName").value("Admin Maya"))
    }

    private fun createTestAdmin(): UUID {
        val id = UUID.randomUUID()
        db.update("INSERT INTO users(id,email,password_hash,role) VALUES (?,?,?,'admin')", id, "$id@example.test", "dummy")
        return id
    }

    @Test fun `refresh sessions rotate token and logout revokes only refresh access`() {
        val register = """{"email":"session@example.test","password":"password123","role":"pembeli","fullName":"Mok"}"""
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register)).andExpect(status().isCreated)
        val login = """{"email":"session@example.test","password":"password123"}"""
        val session = mapper.readTree(mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().response.contentAsString)["data"]
        val first = session["refreshToken"].asText()
        val originalAccess = session["accessToken"].asText()
        assertEquals(0, db.queryForObject("SELECT count(*) FROM auth_refresh_sessions WHERE token_hash=?", Int::class.java, first))
        val refreshed = mapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("refreshToken" to first))))
            .andExpect(status().isOk).andReturn().response.contentAsString)["data"]
        val second = refreshed["refreshToken"].asText()
        assertNotEquals(first, second)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("refreshToken" to first)))).andExpect(status().isUnauthorized)
        repeat(2) { mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("refreshToken" to second)))).andExpect(status().isNoContent) }
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("refreshToken" to second)))).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $originalAccess")).andExpect(status().isOk)
    }

    @Test fun `refresh token expires and concurrent reuse succeeds once`() {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"race-session@example.test","password":"password123","role":"pembeli","fullName":"Mok"}""")).andExpect(status().isCreated)
        val body = """{"email":"race-session@example.test","password":"password123"}"""
        fun newRefresh(): String = mapper.readTree(mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andReturn().response.contentAsString)["data"]["refreshToken"].asText()
        val token = newRefresh()
        fun refresh(): Int = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("refreshToken" to token)))).andReturn().response.status
        assertEquals(listOf(200, 401), parallel({ refresh() }, { refresh() }).map { it.getOrThrow() }.sorted())
        val expiry = newRefresh()
        (clock as TestClock).time.set(Instant.parse("2026-12-10T00:00:00Z"))
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("refreshToken" to expiry)))).andExpect(status().isUnauthorized)
    }

    @Test fun `admin queue and user list are paginated private and filterable`() {
        val admin = createTestAdmin()
        db.update("UPDATE farmer_profiles SET ktp_status='menunggu',ktp_submitted_at=now(),ktp_document_path='private-dummy-path' WHERE id=?", farmer)
        val queue = "/api/v1/admin/farmers/verification-queue"
        mvc.perform(get(queue)).andExpect(status().isUnauthorized)
        mvc.perform(get(queue).with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get(queue).with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.items[0].farmerId").value(farmer.toString()))
            .andExpect(jsonPath("$.data.items[0].hasDocument").value(true)).andExpect(jsonPath("$.data.items[0].ktpDocumentPath").doesNotExist())
        mvc.perform(get(queue).param("size", "101").with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isBadRequest)
        val list = mvc.perform(get("/api/v1/admin/users").param("role", "petani").param("search", "Toko")
            .param("size", "1").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.items[0].role").value("petani"))
            .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist()).andReturn().response.contentAsString
        assertFalse(list.contains("dummy-hash"))
        mvc.perform(get("/api/v1/admin/users").param("role", "invalid").with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/admin/users").param("search", "%").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(0))
        val verify = "/api/v1/admin/farmers/$farmer/verify"
        mvc.perform(put(verify).with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"approved":true}""")).andExpect(status().isOk)
        mvc.perform(get(queue).with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(0))
        mvc.perform(put(verify).with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"approved":true}""")).andExpect(status().isConflict)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND action='VERIFY_KTP_APPROVED'", Int::class.java, farmer))
        db.update("UPDATE users SET status='suspended' WHERE id=?", admin)
        mvc.perform(get(queue).with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/admin/farmers/$farmer/verify").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"approved":true}""")).andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/admin/users/$farmer/suspend").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"suspend":true,"reason":"test"}""")).andExpect(status().isForbidden)
    }

    @Test fun `admin user detail includes active refunds and hides private credentials`() {
        val admin = createTestAdmin()
        val refund = refundRequest(receivedOrder())
        val path = "/api/v1/admin/users/$farmer"
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
        mvc.perform(get(path).with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        val content = mvc.perform(get(path).with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.id").value(farmer.toString()))
            .andExpect(jsonPath("$.data.storeName").value("Toko $farmer"))
            .andExpect(jsonPath("$.data.pickupLocation").value("Lokasi $farmer"))
            .andExpect(jsonPath("$.data.ktpStatus").value("disetujui"))
            .andExpect(jsonPath("$.data.activeRefundCount").value(1))
            .andExpect(jsonPath("$.data.recentActiveRefunds[0].id").value(refund.id.toString()))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andReturn().response.contentAsString
        assertFalse(content.contains("dummy-hash"))
        mvc.perform(get("/api/v1/admin/users/${UUID.randomUUID()}").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isNotFound)
    }

    @Test fun `suspend requires reason shows it in detail and retry does not duplicate audit`() {
        val admin = createTestAdmin()
        val path = "/api/v1/admin/users/$farmer/suspend"
        fun send(body: String, actor: UUID = admin) = mvc.perform(put(path).with(jwt().jwt { it.subject(actor.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content(body))
        send("""{"suspend":true}""").andExpect(status().isBadRequest)
        send("""{"suspend":true,"reason":"  "}""").andExpect(status().isBadRequest)
        send("""{"reason":"tidak ada field suspend"}""").andExpect(status().isBadRequest)
        send("""{"suspend":true,"reason":"Laporan produk"}""", buyer).andExpect(status().isForbidden)
        repeat(2) { send("""{"suspend":true,"reason":"  Laporan produk  "}""").andExpect(status().isOk) }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND action='USER_SUSPENDED'", Int::class.java, farmer))
        mvc.perform(get("/api/v1/admin/users/$farmer").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("suspended"))
            .andExpect(jsonPath("$.data.suspensionReason").value("Laporan produk"))
            .andExpect(jsonPath("$.data.suspendedAt").exists())
        send("""{"suspend":false,"reason":"Sudah ditinjau"}""").andExpect(status().isOk)
        mvc.perform(get("/api/v1/admin/users/$farmer").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("active"))
            .andExpect(jsonPath("$.data.suspensionReason").doesNotExist())
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND action='USER_RESTORED' AND reason='Sudah ditinjau'", Int::class.java, farmer))
    }

    @Test fun `concurrent suspensions make one audited state change`() {
        val first = createTestAdmin()
        val second = createTestAdmin()
        fun suspend(admin: UUID): Int = mvc.perform(put("/api/v1/admin/users/$farmer/suspend")
            .with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON)
            .content("""{"suspend":true,"reason":"Laporan pengguna"}""")).andReturn().response.status
        val results = parallel({ suspend(first) }, { suspend(second) })
        assertTrue(results.all { it.getOrNull() == 200 }, results.toString())
        assertEquals("suspended", db.queryForObject("SELECT status FROM users WHERE id=?", String::class.java, farmer))
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND action='USER_SUSPENDED'", Int::class.java, farmer))
    }

    @Test fun `admin changes limit with required reason and checkout uses new limit`() {
        val admin = createTestAdmin()
        val path = "/api/v1/admin/settings/transaction-limit"
        val oldOrder = checkout.checkout(buyer, "old-limit-order", request())
        val invalidBodies = listOf("""{"newLimit":8000}""", """{"newLimit":8000,"reason":" "}""",
            """{"newLimit":0,"reason":"test"}""", """{"newLimit":8000.5,"reason":"test"}""",
            """{"newLimit":"8000","reason":"test"}""", """{"newLimit":8000,"reason":"test","extra":true}""")
        invalidBodies.forEach { body ->
            mvc.perform(put(path).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
        val body = """{"newLimit":8000,"reason":"Penyesuaian demo"}"""
        mvc.perform(put(path).with(jwt().jwt { it.subject(farmer.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        repeat(2) {
            mvc.perform(put(path).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk).andExpect(jsonPath("$.data.transactionLimit").value(8000))
        }
        mvc.perform(get("/api/v1/admin/settings").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.transactionLimit").value(8000))
        assertThrows(ApiException::class.java) { checkout.checkout(otherBuyer, "new-limit-order", request()) }
        assertEquals(10000000L, db.queryForObject("SELECT transaction_limit_applied FROM orders WHERE id=?", Long::class.java, oldOrder.orderId))
        mvc.perform(get("/api/v1/admin/audit-logs").param("action", "TRANSACTION_LIMIT_CHANGED")
            .with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].oldValues").value("10000000"))
            .andExpect(jsonPath("$.data.items[0].newValues").value("8000"))
            .andExpect(jsonPath("$.data.items[0].reason").value("Penyesuaian demo"))
        mvc.perform(get("/api/v1/admin/audit-logs").with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
    }

    @Test fun `admin settings form saves limit and whatsapp atomically with audited changes`() {
        val admin = createTestAdmin()
        val url = "/api/v1/admin/settings"
        val request = """{"newLimit":8000,"csWhatsapp":"+62 800 0000 0000","expectedTransactionLimit":10000000,"expectedCsWhatsapp":null,"reason":"Perubahan pengaturan demo"}"""
        mvc.perform(get("/api/v1/support/contact")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/support/contact").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.csWhatsapp").doesNotExist())
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isUnauthorized)
        mvc.perform(put(url).with(jwt().jwt { it.subject(buyer.toString()) }).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isForbidden)
        repeat(2) {
            mvc.perform(put(url).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.transactionLimit").value(8000))
                .andExpect(jsonPath("$.data.csWhatsapp").value("+6280000000000"))
        }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TRANSACTION_LIMIT_CHANGED'", Int::class.java))
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CS_WHATSAPP_CHANGED'", Int::class.java))
        mvc.perform(get(url).with(jwt().jwt { it.subject(admin.toString()) })).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.csWhatsapp").value("+6280000000000"))
        listOf(buyer, farmer).forEach { id ->
            mvc.perform(get("/api/v1/support/contact").with(jwt().jwt { it.subject(id.toString()) }))
                .andExpect(status().isOk).andExpect(jsonPath("$.data.csWhatsapp").value("+6280000000000"))
                .andExpect(header().string("Cache-Control", "no-store"))
        }
        db.update("UPDATE users SET status='suspended' WHERE id=?", farmer)
        mvc.perform(get("/api/v1/support/contact").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.csWhatsapp").value("+6280000000000"))
        val stale = """{"newLimit":9000,"csWhatsapp":"+628111111111","expectedTransactionLimit":10000000,"expectedCsWhatsapp":null,"reason":"stale"}"""
        mvc.perform(put(url).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON).content(stale))
            .andExpect(status().isConflict)
        assertEquals("8000", db.queryForObject("SELECT value FROM settings WHERE key='transaction_limit'", String::class.java))
        db.update("UPDATE users SET status='suspended' WHERE id=?", admin)
        mvc.perform(put(url).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isForbidden)
    }

    @Test fun `whatsapp settings reject invalid phone and require reason only for changed limit`() {
        val admin = createTestAdmin()
        val url = "/api/v1/admin/settings"
        fun body(phone: Any?, limit: Any = 10000000, reason: Any? = null, expected: Any? = null) = mapper.writeValueAsString(
            mapOf("newLimit" to limit, "csWhatsapp" to phone, "expectedTransactionLimit" to 10000000,
                "expectedCsWhatsapp" to expected, "reason" to reason))
        for (phone in listOf("", "08123456789", "+0 8123456789", "+62-8123456789", "+621234", "+6212345678901234", 123)) {
            mvc.perform(put(url).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body(phone)))
                .andExpect(status().isBadRequest)
        }
        mvc.perform(put(url).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON)
            .content(body("+6280000000000", 9000))).andExpect(status().isBadRequest)
        assertEquals(0, db.queryForObject("SELECT count(*) FROM settings WHERE key='cs_whatsapp'", Int::class.java))
        mvc.perform(put(url).with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON)
            .content(body("+6280000000000"))).andExpect(status().isOk)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CS_WHATSAPP_CHANGED'", Int::class.java))
        assertEquals(0, db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TRANSACTION_LIMIT_CHANGED'", Int::class.java))
    }

    @Test fun `whatsapp settings rollback both fields when audit fails`() {
        val admin = createTestAdmin()
        db.execute("CREATE FUNCTION test_cs_audit_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN IF NEW.action = ''CS_WHATSAPP_CHANGED'' THEN RAISE EXCEPTION ''simulated failure''; END IF; RETURN NEW; END;'")
        db.execute("CREATE TRIGGER test_cs_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION test_cs_audit_failure()")
        try {
            mvc.perform(put("/api/v1/admin/settings").with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON)
                .content("""{"newLimit":8000,"csWhatsapp":"+6280000000000","expectedTransactionLimit":10000000,"expectedCsWhatsapp":null,"reason":"Uji"}"""))
                .andExpect(status().is5xxServerError)
            assertEquals("10000000", db.queryForObject("SELECT value FROM settings WHERE key='transaction_limit'", String::class.java))
            assertEquals(0, db.queryForObject("SELECT count(*) FROM settings WHERE key='cs_whatsapp'", Int::class.java))
            assertEquals(0, db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TRANSACTION_LIMIT_CHANGED'", Int::class.java))
        } finally {
            db.execute("DROP TRIGGER test_cs_audit_failure ON audit_logs")
            db.execute("DROP FUNCTION test_cs_audit_failure()")
        }
    }

    @Test fun `concurrent settings forms never overwrite a newer admin change`() {
        val first = createTestAdmin(); val second = createTestAdmin()
        fun update(admin: UUID, number: String): Int {
            val request = mapper.writeValueAsString(mapOf("newLimit" to 10000000, "csWhatsapp" to number,
                "expectedTransactionLimit" to 10000000, "expectedCsWhatsapp" to null))
            return mvc.perform(put("/api/v1/admin/settings").with(jwt().jwt { it.subject(admin.toString()) })
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn().response.status
        }
        val results = parallel({ update(first, "+6280000000000") }, { update(second, "+6281111111111") })
        assertEquals(listOf(200, 409), results.map { it.getOrThrow() }.sorted())
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CS_WHATSAPP_CHANGED'", Int::class.java))
    }

    @Test fun `failed audit insert rolls back transaction limit change`() {
        val admin = createTestAdmin()
        db.execute("CREATE FUNCTION test_limit_audit_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated failure''; END;'")
        db.execute("CREATE TRIGGER test_limit_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION test_limit_audit_failure()")
        try {
            mvc.perform(put("/api/v1/admin/settings/transaction-limit").with(jwt().jwt { it.subject(admin.toString()) })
                .contentType(MediaType.APPLICATION_JSON).content("""{"newLimit":8000,"reason":"test"}"""))
            assertEquals("10000000", db.queryForObject("SELECT value FROM settings WHERE key='transaction_limit'", String::class.java))
        } finally {
            db.execute("DROP TRIGGER test_limit_audit_failure ON audit_logs")
            db.execute("DROP FUNCTION test_limit_audit_failure()")
        }
    }

    @Test fun `concurrent admin limit updates serialize and retain audit history`() {
        val firstAdmin = createTestAdmin()
        val secondAdmin = createTestAdmin()
        fun update(admin: UUID, limit: Long): Int = mvc.perform(put("/api/v1/admin/settings/transaction-limit")
            .with(jwt().jwt { it.subject(admin.toString()) }).contentType(MediaType.APPLICATION_JSON)
            .content("""{"newLimit":$limit,"reason":"pengujian"}""")).andReturn().response.status
        val results = parallel({ update(firstAdmin, 11000000) }, { update(secondAdmin, 12000000) })
        assertTrue(results.all { it.getOrNull() == 200 }, results.joinToString { it.exceptionOrNull()?.toString() ?: it.toString() })
        val logs = db.query("SELECT old_values,new_values FROM audit_logs WHERE action='TRANSACTION_LIMIT_CHANGED'") { rs, _ ->
            rs.getString("old_values") to rs.getString("new_values")
        }
        assertEquals(2, logs.size)
        val first = logs.single { it.first == "10000000" }
        val second = logs.single { it.first == first.second }
        assertEquals(second.second, db.queryForObject("SELECT value FROM settings WHERE key='transaction_limit'", String::class.java))
    }

    @Test fun `farmer creates and views bank account without exposing it to other users`() {
        val response = mvc.perform(post("/api/v1/petani/payment-methods/bank")
            .with(jwt().jwt { it.subject(farmer.toString()) }).header("Idempotency-Key", "create-bank")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(bankRequest())))
            .andExpect(status().isCreated).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.accountNumber").value("00012345")).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        val url = "/api/v1/petani/payment-methods/$id/bank"
        mvc.perform(get(url).with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.bankName").value("Bank Contoh"))
            .andExpect(jsonPath("$.data.accountNumber").value("00012345"))
        mvc.perform(get(url).with(jwt().jwt { it.subject(otherFarmer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get(url).with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get(url)).andExpect(status().isUnauthorized)
        val ownList = mvc.perform(get("/api/v1/petani/payment-methods").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertFalse(ownList.contains("00012345"))
        assertTrue(ownList.contains("2345"))
    }

    @Test fun `bank details reject invalid json and invalid account input`() {
        val bodies = listOf(
            """{"bankName":"BRI","accountNumber":12345,"accountHolderName":"Petani"}""",
            """{"bankName":"BRI","accountNumber":null,"accountHolderName":"Petani"}""",
            """{"bankName":"BRI","accountNumber":"12-34","accountHolderName":"Petani"}""",
            """{"bankName":" ","accountNumber":"0012","accountHolderName":"Petani"}""",
            """{"bankName":"BRI","accountNumber":"0012","accountHolderName":" "}""",
            """{"bankName":"BRI","accountNumber":"0012","accountHolderName":"Petani","other":"x"}"""
        )
        bodies.forEachIndexed { index, body ->
            mvc.perform(post("/api/v1/petani/payment-methods/bank").with(jwt().jwt { it.subject(farmer.toString()) })
                .header("Idempotency-Key", "bad-bank-$index").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
        assertEquals(0, db.queryForObject("SELECT count(*) FROM bank_method_requests", Int::class.java))
    }

    @Test fun `bank creation retry is idempotent even concurrently`() {
        val results = parallel(
            { farmerPaymentMethods.createBank(farmer, "bank-retry", bankRequest()) },
            { farmerPaymentMethods.createBank(farmer, "bank-retry", bankRequest()) }
        )
        assertTrue(results.all { it.isSuccess }, results.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        assertEquals(results[0].getOrThrow().id, results[1].getOrThrow().id)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM bank_method_requests WHERE farmer_id=?", Int::class.java, farmer))
        val error = assertThrows(ApiException::class.java) { farmerPaymentMethods.createBank(farmer, "bank-retry", bankRequest(number = "00054321")) }
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.status)
    }

    @Test fun `only owner can update bank and qris cannot be changed to bank`() {
        val bank = farmerPaymentMethods.createBank(farmer, "bank-owner", bankRequest())
        val url = "/api/v1/petani/payment-methods/${bank.id}/bank"
        val body = mapper.writeValueAsString(bankRequest("Bank Baru", "00006789", "Nama Baru"))
        mvc.perform(put(url).with(jwt().jwt { it.subject(otherFarmer.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(put(url).with(jwt().jwt { it.subject(buyer.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(put(url).with(jwt().jwt { it.subject(farmer.toString()) }).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.accountNumber").value("00006789"))
            .andExpect(header().string("Cache-Control", "no-store"))
        assertEquals("00006789", farmerPaymentMethods.bankDetail(farmer, bank.id).accountNumber)
        val replay = farmerPaymentMethods.createBank(farmer, "bank-owner", bankRequest())
        assertEquals(bank.id, replay.id)
        assertEquals("00006789", replay.accountNumber)
        val qris = farmerPaymentMethods.uploadQris(farmer, "bank-qris-type", "Toko", qrisUpload())
        assertThrows(ApiException::class.java) { farmerPaymentMethods.updateBank(farmer, qris.id, bankRequest()) }
        db.update("UPDATE users SET status='suspended' WHERE id=?", farmer)
        assertThrows(ApiException::class.java) { farmerPaymentMethods.updateBank(farmer, bank.id, bankRequest()) }
    }

    @Test fun `bank edits only affect later checkout snapshots`() {
        val oldOrder = checkout.checkout(buyer, "bank-old-order", request())
        farmerPaymentMethods.updateBank(farmer, paymentOption.id, bankRequest("Bank Baru", "00006789", "Nama Baru"))
        val newOrder = checkout.checkout(otherBuyer, "bank-new-order", request())
        lifecycle.respondByFarmer(farmer, oldOrder.orderId, OrderRespondRequest("terima"))
        lifecycle.respondByFarmer(farmer, newOrder.orderId, OrderRespondRequest("terima"))
        assertEquals("00123", paymentDestinations.optionsForBuyer(buyer, oldOrder.orderId).single().accountNumber)
        val newer = paymentDestinations.optionsForBuyer(otherBuyer, newOrder.orderId).single()
        assertEquals("00006789", newer.accountNumber)
        assertEquals("Bank Baru", newer.bankName)
        assertEquals("Nama Baru", newer.accountHolderName)
    }

    @Test fun `deleting bank preserves old order snapshot and hides bank from new checkouts`() {
        val bank = farmerPaymentMethods.createBank(farmer, "bank-to-delete", bankRequest())
        val order = checkout.checkout(buyer, "order-before-bank-delete", request())
        val url = "/api/v1/petani/payment-methods/${bank.id}/bank"
        mvc.perform(delete(url)).andExpect(status().isUnauthorized)
        mvc.perform(delete(url).with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(delete(url).with(jwt().jwt { it.subject(otherFarmer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(delete(url).with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isNoContent).andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(delete(url).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isNoContent)
        mvc.perform(get(url).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isNotFound)
        mvc.perform(put(url).with(jwt().jwt { it.subject(farmer.toString()) }).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(bankRequest()))).andExpect(status().isNotFound)
        val replay = assertThrows(ApiException::class.java) { farmerPaymentMethods.createBank(farmer, "bank-to-delete", bankRequest()) }
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, replay.status)
        assertTrue(farmerPaymentMethods.list(farmer).none { it.id == bank.id })
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        assertEquals("00012345", paymentDestinations.chooseForBuyer(buyer, order.orderId, bank.id).accountNumber)
        val next = checkout.checkout(otherBuyer, "order-after-bank-delete", request())
        lifecycle.respondByFarmer(farmer, next.orderId, OrderRespondRequest("terima"))
        assertTrue(paymentDestinations.optionsForBuyer(otherBuyer, next.orderId).none { it.id == bank.id })
    }

    @Test fun `last bank cannot be deleted with active products or an unavailable qris`() {
        val url = "/api/v1/petani/payment-methods/${paymentOption.id}/bank"
        mvc.perform(delete(url).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isConflict)
        val qris = farmerPaymentMethods.uploadQris(farmer, "alternative-qris", "Toko", qrisUpload())
        val imageKey = db.queryForObject("SELECT qris_image_path FROM farmer_payment_methods WHERE id=?", String::class.java, qris.id)!!
        com.panenku.backend.common.storage.LocalPrivateImageStore(paymentImageDir).delete(imageKey)
        mvc.perform(delete(url).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isConflict)
        db.update("UPDATE products SET publication_status='draft' WHERE farmer_id=?", farmer)
        mvc.perform(delete(url).with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isNoContent)
    }

    @Test fun `concurrent removal of two bank methods leaves one for active products`() {
        val second = farmerPaymentMethods.createBank(farmer, "second-to-delete", bankRequest())
        val attempts = parallel(
            { farmerPaymentMethods.deleteBank(farmer, paymentOption.id) },
            { farmerPaymentMethods.deleteBank(farmer, second.id) }
        )
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(1, farmerPaymentMethods.list(farmer).count { it.methodType == "bank_transfer" })
    }

    @Test fun `simultaneous bank update and checkout keep complete consistent snapshot`() {
        val results = parallel(
            { checkout.checkout(buyer, "bank-race", request()).orderId.toString() },
            { farmerPaymentMethods.updateBank(farmer, paymentOption.id, bankRequest("Bank Baru", "00006789", "Nama Baru")).id.toString() }
        )
        assertTrue(results.all { it.isSuccess }, results.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        val orderId = UUID.fromString(results[0].getOrThrow())
        lifecycle.respondByFarmer(farmer, orderId, OrderRespondRequest("terima"))
        val option = paymentDestinations.optionsForBuyer(buyer, orderId).single()
        assertTrue(listOf(
            Triple("Bank Dummy", "00123", "Petani Dummy"), Triple("Bank Baru", "00006789", "Nama Baru")
        ).contains(Triple(option.bankName, option.accountNumber, option.accountHolderName)))
    }

    private fun awaitingPayment(): UUID {
        // Use a future slot even when an earlier fixture in this test advanced time to receipt.
        val at = ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Jakarta")))
        var day = maxOf(date, at.toLocalDate())
        val slots = listOf("08:00-10:00", "13:00-15:00")
        var slot = slots.firstOrNull { day > at.toLocalDate() || LocalTime.parse(it.substring(0,5)) > at.toLocalTime() }
        if (slot == null) { day = day.plusDays(1); slot = slots.first() }
        val order = checkout.checkout(buyer, "payment-${UUID.randomUUID()}", request().copy(scheduledDate=day,scheduledTimeSlot=slot))
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id)
        return order.orderId
    }

    private fun verifiedOrder(): UUID {
        val order = awaitingPayment()
        val proof = paymentSubmissions.submit(buyer, order, "f7-proof-${UUID.randomUUID()}", qrisUpload())
        paymentSubmissions.decide(farmer, order, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(proof.id, "terima"))
        return order
    }

    fun atSchedule(orderId: UUID) {
        val day = db.queryForObject("SELECT scheduled_date FROM orders WHERE id=?", LocalDate::class.java, orderId)!!
        val slot = db.queryForObject("SELECT scheduled_time_slot FROM orders WHERE id=?", String::class.java, orderId)!!
        (clock as TestClock).time.set(day.atTime(LocalTime.parse(slot.substring(0,5))).atZone(ZoneId.of("Asia/Jakarta")).toInstant())
    }

    private fun receivedOrder(): UUID {
        val order = verifiedOrder()
        atSchedule(order)
        lifecycle.updateFulfillment(farmer, order, FulfillmentUpdateRequest("siap_diambil"))
        lifecycle.updateFulfillment(farmer, order, FulfillmentUpdateRequest("menunggu_penerimaan"))
        lifecycle.receiveByBuyer(buyer, order)
        return order
    }

    private fun refundRequest(orderId: UUID, key: String = "refund-${UUID.randomUUID()}") = refunds.create(
        buyer, key, orderId, "Sayur tidak sesuai", "bank", "Bank Contoh", "00123456", "Pembeli Contoh", qrisUpload()
    )

    private fun adminUser(): UUID {
        val id = UUID.randomUUID()
        db.update("INSERT INTO users(id,email,password_hash,role) VALUES (?,?,?,'admin')", id, "$id@example.test", "dummy")
        return id
    }

    private fun expiredUnpaidOrder(): UUID {
        val order = awaitingPayment()
        val expiry = orderRepository.findById(order).orElseThrow().expiresAt
        (clock as TestClock).time.set(expiry)
        assertTrue(expiration.expire(order, farmer, expiry))
        return order
    }

    @Test fun `late payment report is private and never marks order paid or changes released stock`() {
        val order = expiredUnpaidOrder()
        val physicalBefore = stockPhysical()
        val report = latePayments.create(buyer, "late-1", order, "Saya telanjur transfer", 12000, qrisUpload())
        assertEquals("diajukan", report.status)
        assertEquals(12000L, report.claimedAmount)
        assertEquals(9000L, report.expectedAmount)
        assertEquals(report.id, latePayments.create(buyer, "late-1", order, "Saya telanjur transfer", 12000, qrisUpload()).id)
        assertThrows(ApiException::class.java) { latePayments.create(buyer, "late-1", order, "Saya telanjur transfer", 13000, qrisUpload()) }
        assertThrows(ApiException::class.java) { latePayments.create(buyer, "late-2", order, "Laporan kedua", 12000, qrisUpload()) }
        assertThrows(ApiException::class.java) { latePayments.detail(otherBuyer, "pembeli", report.id) }
        assertThrows(ApiException::class.java) { latePayments.image(farmer, "petani", report.id) }
        val admin = adminUser()
        assertArrayEquals(testQris(), latePayments.image(admin, "admin", report.id).bytes)
        assertEquals("kedaluwarsa", orderStatus(order))
        assertEquals("belum_dibayar", orderRepository.findById(order).orElseThrow().paymentStatus)
        assertEquals("released", reservationStatus(order))
        assertEquals(physicalBefore, stockPhysical())
        assertThrows(Exception::class.java) { db.update("UPDATE late_payment_reports SET claimed_amount=1 WHERE id=?", report.id) }
    }

    @Test fun `late payment report requires expired unverified order and is idempotent concurrently`() {
        val pending = awaitingPayment()
        assertThrows(ApiException::class.java) { latePayments.create(buyer, "not-expired", pending, "Transfer", 9000, qrisUpload()) }
        val expiry = orderRepository.findById(pending).orElseThrow().expiresAt
        (clock as TestClock).time.set(expiry)
        assertTrue(expiration.expire(pending, farmer, expiry))
        val results = parallel(
            { latePayments.create(buyer, "late-race", pending, "Transfer", 9000, qrisUpload()) },
            { latePayments.create(buyer, "late-race", pending, "Transfer", 9000, qrisUpload()) }
        )
        assertTrue(results.all { it.isSuccess }, results.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        assertEquals(results[0].getOrThrow().id, results[1].getOrThrow().id)
        assertEquals(1, latePayments.list(buyer, "pembeli", 0, 20).items.size)
    }

    @Test fun `admin reviews and closes late payment report without declaring refund or payment`() {
        val order = expiredUnpaidOrder()
        val report = latePayments.create(buyer, "late-admin", order, "Transfer sesudah batas", 9000, qrisUpload())
        val admin = adminUser()
        val review = com.panenku.backend.modules.shared.dto.LatePaymentReviewRequest("tindak_lanjut", "Periksa dengan petani")
        val reviewed = latePayments.review(admin, report.id, review)
        assertEquals("tindak_lanjut", reviewed.status)
        assertEquals(reviewed, latePayments.review(admin, report.id, review))
        assertEquals(1, latePayments.list(admin, "admin", 0, 20).items.size)
        val close = com.panenku.backend.modules.shared.dto.LatePaymentCloseRequest("Tindak lanjut dicatat")
        assertEquals("ditutup", latePayments.close(admin, report.id, close).status)
        assertEquals("ditutup", latePayments.close(admin, report.id, close).status)
        assertEquals(2, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND target_type='late_payment_report'", Int::class.java, report.id))
        assertEquals("kedaluwarsa", orderStatus(order))
        assertEquals("belum_dibayar", orderRepository.findById(order).orElseThrow().paymentStatus)
        assertEquals("released", reservationStatus(order))
        assertEquals(0, db.queryForObject("SELECT count(*) FROM refunds WHERE order_id=?", Int::class.java, order))
    }

    @Test fun `admin may close late payment report for insufficient evidence and buyer may report again`() {
        val order = expiredUnpaidOrder()
        val report = latePayments.create(buyer, "late-insufficient", order, "Transfer", 9000, qrisUpload())
        val admin = adminUser()
        assertEquals("ditutup", latePayments.review(admin, report.id,
            com.panenku.backend.modules.shared.dto.LatePaymentReviewRequest("bukti_tidak_cukup", "Bukti tidak terbaca")).status)
        assertEquals("diajukan", latePayments.create(buyer, "late-retry-new", order, "Bukti baru", 9000, qrisUpload()).status)
        assertThrows(ApiException::class.java) { latePayments.close(admin, report.id, com.panenku.backend.modules.shared.dto.LatePaymentCloseRequest("Lain")) }
    }

    @Test fun `late payment report HTTP exposes proof only to owner and admin`() {
        val order = expiredUnpaidOrder()
        val result = mvc.perform(multipart("/api/v1/buyer/late-payment-reports").file(qrisUpload())
            .param("orderId", order.toString()).param("reason", "Transfer terlambat").param("claimedAmount", "9000")
            .header("Idempotency-Key", "http-late").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("diajukan"))
            .andExpect(jsonPath("$.data.evidencePath").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
        val id = UUID.fromString(mapper.readTree(result.response.contentAsString)["data"]["id"].asText())
        val admin = adminUser()
        mvc.perform(get("/api/v1/admin/late-payment-reports/$id/evidence").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(content().bytes(testQris()))
        mvc.perform(get("/api/v1/buyer/late-payment-reports/$id/evidence").with(jwt().jwt { it.subject(otherBuyer.toString()) }))
            .andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/admin/late-payment-reports/$id/review").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"decision":"tindak_lanjut","reason":"Periksa manual"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("tindak_lanjut"))
        mvc.perform(put("/api/v1/admin/late-payment-reports/$id/close").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Sudah dicatat"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("ditutup"))
    }

    @Test fun `late payment report rollback removes uploaded file`() {
        val order = expiredUnpaidOrder()
        val before = java.nio.file.Files.list(paymentImageDir).use { it.count() }
        db.execute("CREATE FUNCTION test_late_payment_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated failure''; END;'")
        db.execute("CREATE TRIGGER test_late_payment_failure BEFORE INSERT ON late_payment_reports FOR EACH ROW EXECUTE FUNCTION test_late_payment_failure()")
        try {
            assertThrows(Exception::class.java) { latePayments.create(buyer, "late-rollback", order, "Transfer", 9000, qrisUpload()) }
            assertEquals(0, db.queryForObject("SELECT count(*) FROM late_payment_reports", Int::class.java))
            assertEquals(before, java.nio.file.Files.list(paymentImageDir).use { it.count() })
        } finally {
            db.execute("DROP TRIGGER test_late_payment_failure ON late_payment_reports")
            db.execute("DROP FUNCTION test_late_payment_failure()")
        }
    }

    @Test fun `F7 full refund completes only when buyer confirms without changing stock`() {
        val order = receivedOrder()
        val physicalBefore = stockPhysical()
        val submitted = refundRequest(order, "refund-full")
        assertEquals("diajukan", submitted.status)
        assertEquals(9000L, submitted.refundAmount)
        assertEquals("00123456", submitted.destinationAccount)
        assertThrows(Exception::class.java) { db.update("UPDATE refunds SET destination_account='999' WHERE id=?", submitted.id) }
        assertThrows(Exception::class.java) { db.update("UPDATE refund_history SET to_status='selesai' WHERE refund_id=?", submitted.id) }
        assertEquals(submitted.id, refundRequest(order, "refund-full").id)
        assertThrows(ApiException::class.java) { refundRequest(order, "other-active") }
        assertThrows(ApiException::class.java) { refunds.confirm(buyer, submitted.id) }
        val accepted = refunds.respond(farmer, submitted.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima"))
        assertEquals("disetujui", accepted.status)
        val transfer = refunds.submitTransfer(farmer, submitted.id, "refund-transfer", qrisUpload())
        assertEquals("menunggu_konfirmasi_dana", transfer.status)
        assertThrows(Exception::class.java) { db.update("UPDATE refunds SET transfer_proof_path='wrong.png' WHERE id=?", submitted.id) }
        assertNull(transfer.buyerConfirmedAt)
        assertEquals(transfer.id, refunds.submitTransfer(farmer, submitted.id, "refund-transfer", qrisUpload()).id)
        val complete = refunds.confirm(buyer, submitted.id)
        assertEquals("selesai", complete.status)
        assertNotNull(complete.buyerConfirmedAt)
        assertEquals(complete, refunds.confirm(buyer, submitted.id))
        assertEquals(physicalBefore, stockPhysical())
        assertThrows(ApiException::class.java) { refundRequest(order, "cannot-refund-twice") }
        assertEquals(listOf("diajukan", "disetujui", "menunggu_konfirmasi_dana", "selesai"), complete.history.map { it.toStatus })
    }

    @Test fun `F7 is limited to verified paid orders and 24 hours after receipt`() {
        val first = receivedOrder()
        val second = receivedOrder()
        val unpaid = awaitingPayment()
        assertThrows(ApiException::class.java) { refundRequest(unpaid) }
        val firstReceived = db.queryForObject("SELECT received_at FROM orders WHERE id=?", java.sql.Timestamp::class.java, first)!!.toInstant()
        val secondReceived = db.queryForObject("SELECT received_at FROM orders WHERE id=?", java.sql.Timestamp::class.java, second)!!.toInstant()
        (clock as TestClock).time.set(firstReceived.plus(Duration.ofHours(24)))
        assertEquals("diajukan", refundRequest(first).status)
        (clock as TestClock).time.set(secondReceived.plus(Duration.ofHours(24)).plusSeconds(1))
        assertThrows(ApiException::class.java) { refundRequest(second) }
    }

    @Test fun `F7 failed fulfillment may request refund without receipt and does not restore stock twice`() {
        val order = verifiedOrder()
        lifecycle.updateFulfillment(farmer, order, FulfillmentUpdateRequest("gagal_dipenuhi", "Panen gagal"))
        assertEquals("released", reservationStatus(order))
        val physicalBefore = stockPhysical()
        (clock as TestClock).time.set(Instant.parse("2027-01-01T00:00:00Z"))
        val result = refundRequest(order)
        assertEquals("diajukan", result.status)
        refunds.respond(farmer, result.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima"))
        refunds.submitTransfer(farmer, result.id, "failed-order-transfer", qrisUpload())
        assertEquals("selesai", refunds.confirm(buyer, result.id).status)
        assertEquals(physicalBefore, stockPhysical())
        assertEquals("released", reservationStatus(order))
    }

    @Test fun `F7 concurrent retries and different keys cannot create multiple active refunds`() {
        val order = receivedOrder()
        val same = parallel({ refundRequest(order, "refund-race") }, { refundRequest(order, "refund-race") })
        assertTrue(same.all { it.isSuccess }, same.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        assertEquals(same[0].getOrThrow().id, same[1].getOrThrow().id)
        assertEquals(1, refunds.list(buyer, "pembeli", 0, 20).items.size)
        val otherOrder = receivedOrder()
        val different = parallel({ refundRequest(otherOrder, "refund-race-a") }, { refundRequest(otherOrder, "refund-race-b") })
        assertEquals(1, different.count { it.isSuccess })
        assertEquals(2, refunds.list(buyer, "pembeli", 0, 20).items.size)
    }

    @Test fun `F7 rejection escalation and admin approval remain manual and auditable`() {
        val order = receivedOrder()
        val refund = refundRequest(order)
        assertThrows(ApiException::class.java) { refunds.respond(farmer, refund.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("tolak")) }
        val rejected = refunds.respond(farmer, refund.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("tolak", "Tidak sesuai"))
        assertEquals("ditolak", rejected.status)
        val dispute = refunds.escalate(buyer, refund.id, "case-1", com.panenku.backend.modules.shared.dto.EscalationRequest("Mohon tinjau"))
        assertEquals(dispute.id, refunds.escalate(buyer, refund.id, "case-1", com.panenku.backend.modules.shared.dto.EscalationRequest("Mohon tinjau")).id)
        assertEquals("dieskalasi", refunds.detail(buyer, "pembeli", refund.id).status)
        val admin = adminUser()
        assertEquals(1, refunds.disputes(admin, 0, 20).items.size)
        val decision = com.panenku.backend.modules.shared.dto.AdminDisputeDecisionRequest("setujui_refund", "Bukti memadai")
        val closed = refunds.decideDispute(admin, dispute.id, decision)
        assertEquals("selesai", closed.status)
        assertEquals(closed, refunds.decideDispute(admin, dispute.id, decision))
        assertEquals("disetujui", refunds.detail(buyer, "pembeli", refund.id).status)
        assertEquals("active", db.queryForObject("SELECT status FROM users WHERE id=?", String::class.java, farmer))
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=? AND action='REFUND_DISPUTE_APPROVED'", Int::class.java, dispute.id))
        assertEquals("menunggu_konfirmasi_dana", refunds.submitTransfer(farmer, refund.id, "after-admin", qrisUpload()).status)
    }

    @Test fun `F7 admin closes dispute after transfer without losing confirmation`() {
        val refund = refundRequest(receivedOrder())
        refunds.respond(farmer, refund.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima"))
        refunds.submitTransfer(farmer, refund.id, "transfer-before-dispute", qrisUpload())
        val dispute = refunds.escalate(buyer, refund.id, "case-after-transfer", com.panenku.backend.modules.shared.dto.EscalationRequest("Dana belum tampak"))
        val admin = adminUser()
        refunds.decideDispute(admin, dispute.id, com.panenku.backend.modules.shared.dto.AdminDisputeDecisionRequest("tutup_sengketa", "Periksa kembali transaksi"))
        assertEquals("menunggu_konfirmasi_dana", refunds.detail(buyer, "pembeli", refund.id).status)
        assertEquals("selesai", refunds.confirm(buyer, refund.id).status)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM refund_transfer_requests WHERE refund_id=?", Int::class.java, refund.id))
    }

    @Test fun `F7 admin approval after transfer preserves proof and does not request another transfer`() {
        val refund = refundRequest(receivedOrder())
        refunds.respond(farmer, refund.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima"))
        refunds.submitTransfer(farmer, refund.id, "approved-existing-proof", qrisUpload())
        val dispute = refunds.escalate(buyer, refund.id, "approve-after-transfer", com.panenku.backend.modules.shared.dto.EscalationRequest("Mohon tinjau"))
        val admin = adminUser()
        refunds.decideDispute(admin, dispute.id, com.panenku.backend.modules.shared.dto.AdminDisputeDecisionRequest("setujui_refund", "Bukti diperiksa"))
        val after = refunds.detail(buyer, "pembeli", refund.id)
        assertEquals("menunggu_konfirmasi_dana", after.status)
        assertTrue(after.hasTransferProof)
        assertThrows(ApiException::class.java) { refunds.submitTransfer(farmer, refund.id, "unwanted-second-transfer", qrisUpload()) }
        assertEquals("selesai", refunds.confirm(buyer, refund.id).status)
    }

    @Test fun `F7 simultaneous different admin decisions cannot both be applied`() {
        val refund = refundRequest(receivedOrder())
        val dispute = refunds.escalate(buyer, refund.id, "concurrent-dispute", com.panenku.backend.modules.shared.dto.EscalationRequest("Mohon dibantu"))
        val adminA = adminUser()
        val adminB = adminUser()
        val results = parallel(
            { refunds.decideDispute(adminA, dispute.id, com.panenku.backend.modules.shared.dto.AdminDisputeDecisionRequest("setujui_refund", "Setuju")) },
            { refunds.decideDispute(adminB, dispute.id, com.panenku.backend.modules.shared.dto.AdminDisputeDecisionRequest("tutup_sengketa", "Ditutup")) }
        )
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id=?", Int::class.java, dispute.id))
    }

    @Test fun `F7 evidence and destination are restricted to transaction parties and admin`() {
        val refund = refundRequest(receivedOrder())
        val admin = adminUser()
        assertArrayEquals(testQris(), refunds.image(farmer, "petani", refund.id, "evidence").bytes)
        assertArrayEquals(testQris(), refunds.image(admin, "admin", refund.id, "evidence").bytes)
        assertThrows(ApiException::class.java) { refunds.detail(otherBuyer, "pembeli", refund.id) }
        assertThrows(ApiException::class.java) { refunds.detail(otherFarmer, "petani", refund.id) }
        assertThrows(ApiException::class.java) { refunds.image(otherFarmer, "petani", refund.id, "evidence") }
        assertThrows(ApiException::class.java) { refunds.respond(otherFarmer, refund.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima")) }
        assertThrows(ApiException::class.java) { refunds.confirm(otherBuyer, refund.id) }
        assertTrue(refunds.list(otherFarmer, "petani", 0, 20).items.isEmpty())
    }

    @Test fun `F7 suspended farmer can settle existing refund but not create bank methods`() {
        val refund = refundRequest(receivedOrder())
        db.update("UPDATE users SET status='suspended' WHERE id=?", farmer)
        assertEquals(refund.id, refunds.detail(farmer, "petani", refund.id).id)
        assertEquals("disetujui", refunds.respond(farmer, refund.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima")).status)
        assertEquals("menunggu_konfirmasi_dana", refunds.submitTransfer(farmer, refund.id, "suspended-transfer", qrisUpload()).status)
        assertThrows(ApiException::class.java) { farmerPaymentMethods.createBank(farmer, "suspended-bank", bankRequest()) }
        assertEquals("selesai", refunds.confirm(buyer, refund.id).status)
    }

    @Test fun `F7 HTTP creates a full refund including delivery and serves private evidence`() {
        val order = checkout.checkout(buyer, "f7-delivery", deliveryRequest())
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id)
        val proof = paymentSubmissions.submit(buyer, order.orderId, "f7-delivery-payment", qrisUpload())
        paymentSubmissions.decide(farmer, order.orderId, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(proof.id, "terima"))
        atSchedule(order.orderId)
        lifecycle.updateFulfillment(farmer, order.orderId, FulfillmentUpdateRequest("diantar"))
        lifecycle.updateFulfillment(farmer, order.orderId, FulfillmentUpdateRequest("menunggu_penerimaan"))
        lifecycle.receiveByBuyer(buyer, order.orderId)
        val response = mvc.perform(multipart("/api/v1/buyer/refunds").file(qrisUpload())
            .param("orderId", order.orderId.toString()).param("reason", "Sayur rusak")
            .param("destinationType", "bank").param("destinationProvider", "Bank Contoh")
            .param("destinationAccount", "00123456").param("destinationName", "Pembeli Contoh")
            .param("refundAmount", "99999999").header("Idempotency-Key", "refund-http")
            .with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.refundAmount").value(19000))
            .andExpect(jsonPath("$.data.evidencePath").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        mvc.perform(get("/api/v1/petani/refunds/$id/evidence").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(content().contentType("image/png")).andExpect(content().bytes(testQris()))
        mvc.perform(get("/api/v1/petani/refunds/$id/evidence").with(jwt().jwt { it.subject(otherFarmer.toString()) }))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/buyer/refunds/$id")).andExpect(status().isUnauthorized)
    }

    @Test fun `F7 HTTP dispute approval leads to farmer transfer and buyer confirmation`() {
        val refund = refundRequest(receivedOrder())
        mvc.perform(put("/api/v1/petani/refunds/${refund.id}/respond").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"decision":"tolak","reason":"Perlu ditinjau"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("ditolak"))
        val response = mvc.perform(post("/api/v1/buyer/refunds/${refund.id}/escalations").with(jwt().jwt { it.subject(buyer.toString()) })
            .header("Idempotency-Key", "http-dispute").contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Mohon tinjau"}"""))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("terbuka")).andReturn()
        val disputeId = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        val admin = adminUser()
        mvc.perform(get("/api/v1/admin/disputes").with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/admin/disputes").with(jwt().jwt { it.subject(admin.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.items[0].id").value(disputeId.toString()))
        mvc.perform(put("/api/v1/admin/disputes/$disputeId/decide").with(jwt().jwt { it.subject(admin.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"decision":"setujui_refund","reason":"Bukti memadai"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("selesai"))
        mvc.perform(multipart("/api/v1/petani/refunds/${refund.id}/transfer-proof").file(qrisUpload())
            .header("Idempotency-Key", "http-refund-transfer").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("menunggu_konfirmasi_dana"))
        mvc.perform(get("/api/v1/buyer/refunds/${refund.id}/transfer-proof").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(content().bytes(testQris()))
        mvc.perform(post("/api/v1/buyer/refunds/${refund.id}/confirm").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("selesai"))
    }

    @Test fun `F7 rejected old request cannot be escalated after another full refund completes`() {
        val order = receivedOrder()
        val old = refundRequest(order, "reject-old")
        refunds.respond(farmer, old.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("tolak", "Perlu data lain"))
        val current = refundRequest(order, "accepted-new")
        refunds.respond(farmer, current.id, com.panenku.backend.modules.shared.dto.RefundDecisionRequest("terima"))
        refunds.submitTransfer(farmer, current.id, "new-transfer", qrisUpload())
        refunds.confirm(buyer, current.id)
        assertThrows(ApiException::class.java) {
            refunds.escalate(buyer, old.id, "old-escalation", com.panenku.backend.modules.shared.dto.EscalationRequest("Tinjau lagi"))
        }
    }

    @Test fun `F7 rollback removes evidence and leaves no refund`() {
        val order = receivedOrder()
        val before = java.nio.file.Files.list(paymentImageDir).use { it.count() }
        db.execute("CREATE FUNCTION test_refund_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated failure''; END;'")
        db.execute("CREATE TRIGGER test_refund_history_failure BEFORE INSERT ON refund_history FOR EACH ROW EXECUTE FUNCTION test_refund_failure()")
        try {
            assertThrows(Exception::class.java) { refundRequest(order) }
            assertEquals(0, db.queryForObject("SELECT count(*) FROM refunds", Int::class.java))
            assertEquals(before, java.nio.file.Files.list(paymentImageDir).use { it.count() })
        } finally {
            db.execute("DROP TRIGGER test_refund_history_failure ON refund_history")
            db.execute("DROP FUNCTION test_refund_failure()")
        }
    }

    @Test fun `F6 upload waits for farmer and verification unlocks F5`() {
        val order = awaitingPayment()
        val image = testQris()
        val upload = paymentSubmissions.submit(buyer, order, "proof-1", qrisUpload(image))
        assertEquals("menunggu_verifikasi", upload.status)
        assertEquals(9000L, upload.expectedAmount)
        assertEquals("verifikasi_pembayaran", orderStatus(order))
        assertEquals("active", reservationStatus(order))
        assertFalse(expiration.expire(order, farmer, Instant.parse("2030-01-01T00:00:00Z")))
        assertArrayEquals(image, paymentSubmissions.image(farmer, "petani", order, upload.id).bytes)
        assertThrows(ApiException::class.java) { paymentSubmissions.image(otherFarmer, "petani", order, upload.id) }
        assertThrows(ApiException::class.java) { paymentSubmissions.list(otherBuyer, "pembeli", order) }
        assertThrows(ApiException::class.java) { paymentSubmissions.decide(otherFarmer, order,
            com.panenku.backend.modules.shared.service.PaymentDecisionRequest(upload.id, "terima")) }
        val admin = UUID.randomUUID()
        db.update("INSERT INTO users(id,email,password_hash,role) VALUES (?,?,?,'admin')", admin, "$admin@example.test", "dummy")
        assertArrayEquals(image, paymentSubmissions.image(admin, "admin", order, upload.id).bytes)
        val decision = com.panenku.backend.modules.shared.service.PaymentDecisionRequest(upload.id, "terima", "Dana masuk")
        val result = paymentSubmissions.decide(farmer, order, decision)
        assertEquals("terverifikasi", result.status)
        assertEquals(result, paymentSubmissions.decide(farmer, order, decision))
        assertEquals("diproses", orderStatus(order))
        assertEquals("terverifikasi", db.queryForObject("SELECT payment_status FROM orders WHERE id=?", String::class.java, order))
        assertEquals("active", reservationStatus(order))
        atSchedule(order)
        assertEquals("siap_diambil", lifecycle.updateFulfillment(farmer, order, FulfillmentUpdateRequest("siap_diambil")).orderStatus)
        assertEquals(2, db.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id=? AND to_status IN ('verifikasi_pembayaran','diproses')", Int::class.java, order))
    }

    @Test fun `F6 rejection retains proof resets deadline and stale decision cannot affect new proof`() {
        val order = awaitingPayment()
        val first = paymentSubmissions.submit(buyer, order, "proof-first", qrisUpload())
        val duplicateWithDifferentContent = assertThrows(ApiException::class.java) {
            paymentSubmissions.submit(buyer, order, "proof-first", qrisUpload(byteArrayOf(1, 2, 3)))
        }
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, duplicateWithDifferentContent.status)
        assertThrows(ApiException::class.java) {
            paymentSubmissions.decide(farmer, order, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(first.id, "tolak", " "))
        }
        (clock as TestClock).time.set(Instant.parse("2026-11-10T06:00:00Z"))
        val reject = com.panenku.backend.modules.shared.service.PaymentDecisionRequest(first.id, "tolak", "Gambar tidak jelas")
        assertEquals("bukti_ditolak", paymentSubmissions.decide(farmer, order, reject).status)
        assertEquals(Instant.parse("2026-11-11T06:00:00Z"), orderRepository.findById(order).orElseThrow().expiresAt)
        val second = paymentSubmissions.submit(buyer, order, "proof-second", qrisUpload())
        assertEquals(first.id, paymentSubmissions.submit(buyer, order, "proof-first", qrisUpload()).id)
        assertEquals("bukti_ditolak", paymentSubmissions.decide(farmer, order, reject).status)
        assertThrows(ApiException::class.java) {
            paymentSubmissions.decide(farmer, order, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(first.id, "terima"))
        }
        assertEquals("verifikasi_pembayaran", orderStatus(order))
        assertEquals("menunggu_verifikasi", paymentSubmissions.list(buyer, "pembeli", order).single { it.id == second.id }.status)
        assertEquals(2, paymentSubmissions.list(farmer, "petani", order).size)
    }

    @Test fun `F6 concurrent retries and different decisions do not duplicate state`() {
        val order = awaitingPayment()
        val uploads = parallel(
            { paymentSubmissions.submit(buyer, order, "concurrent-proof", qrisUpload()) },
            { paymentSubmissions.submit(buyer, order, "concurrent-proof", qrisUpload()) }
        )
        assertTrue(uploads.all { it.isSuccess }, uploads.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        val id = uploads[0].getOrThrow().id
        assertEquals(id, uploads[1].getOrThrow().id)
        val results = parallel(
            { paymentSubmissions.decide(farmer, order, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(id, "terima")) },
            { paymentSubmissions.decide(farmer, order, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(id, "tolak", "Tidak jelas")) }
        )
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, paymentSubmissions.list(buyer, "pembeli", order).size)
        assertEquals("active", reservationStatus(order))
    }

    @Test fun `F6 expiration and invalid ownership prevent upload without stock changes`() {
        val order = awaitingPayment()
        assertThrows(ApiException::class.java) { paymentSubmissions.submit(otherBuyer, order, "wrong-buyer", qrisUpload()) }
        val expiry = orderRepository.findById(order).orElseThrow().expiresAt
        (clock as TestClock).time.set(expiry)
        val results = parallel(
            { runCatching { paymentSubmissions.submit(buyer, order, "late-proof", qrisUpload()) }.isSuccess },
            { expiration.expire(order, farmer, expiry) }
        )
        assertTrue(results.all { it.isSuccess })
        assertFalse(results[0].getOrThrow())
        assertTrue(results[1].getOrThrow())
        assertEquals("kedaluwarsa", orderStatus(order))
        assertEquals("released", reservationStatus(order))
        assertTrue(paymentSubmissions.list(buyer, "pembeli", order).isEmpty())
    }

    @Test fun `F6 farmer verification and scheduler follow same lock order`() {
        val order = awaitingPayment()
        val proof = paymentSubmissions.submit(buyer, order, "scheduler-proof", qrisUpload())
        val results = parallel(
            { paymentSubmissions.decide(farmer, order, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(proof.id, "terima")).status == "terverifikasi" },
            { !expiration.expire(order, farmer, Instant.parse("2030-01-01T00:00:00Z")) }
        )
        assertTrue(results.all { it.isSuccess && it.getOrThrow() }, results.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        assertEquals("diproses", orderStatus(order))
        assertEquals("active", reservationStatus(order))
    }

    @Test fun `F6 failed database insert removes file and leaves order unchanged`() {
        val order = awaitingPayment()
        val before = java.nio.file.Files.list(paymentImageDir).use { it.count() }
        db.execute("CREATE FUNCTION test_payment_upload_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated failure''; END;'")
        db.execute("CREATE TRIGGER test_payment_failure BEFORE INSERT ON payment_submissions FOR EACH ROW EXECUTE FUNCTION test_payment_upload_failure()")
        try {
            assertThrows(Exception::class.java) { paymentSubmissions.submit(buyer, order, "proof-rollback", qrisUpload()) }
            assertEquals("menunggu_pembayaran", orderStatus(order))
            assertTrue(paymentSubmissions.list(buyer, "pembeli", order).isEmpty())
            assertEquals(before, java.nio.file.Files.list(paymentImageDir).use { it.count() })
        } finally {
            db.execute("DROP TRIGGER test_payment_failure ON payment_submissions")
            db.execute("DROP FUNCTION test_payment_upload_failure()")
        }
    }

    @Test fun `F6 decision database failure rolls back proof and order together`() {
        val order = awaitingPayment()
        val proof = paymentSubmissions.submit(buyer, order, "decision-rollback", qrisUpload())
        db.execute("CREATE FUNCTION test_payment_decision_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN IF NEW.order_status = ''diproses'' THEN RAISE EXCEPTION ''simulated failure''; END IF; RETURN NEW; END;'")
        db.execute("CREATE TRIGGER test_decision_failure BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION test_payment_decision_failure()")
        try {
            assertThrows(Exception::class.java) { paymentSubmissions.decide(farmer, order,
                com.panenku.backend.modules.shared.service.PaymentDecisionRequest(proof.id, "terima")) }
            assertEquals("verifikasi_pembayaran", orderStatus(order))
            assertEquals("menunggu_verifikasi", paymentSubmissions.list(buyer, "pembeli", order).single().status)
            assertEquals("active", reservationStatus(order))
        } finally {
            db.execute("DROP TRIGGER test_decision_failure ON orders")
            db.execute("DROP FUNCTION test_payment_decision_failure()")
        }
    }

    @Test fun `F6 upload fails without configured storage or valid image`() {
        val order = awaitingPayment()
        val offline = com.panenku.backend.modules.shared.service.PaymentSubmissionService(
            actors, orderRepository, orderHistoryRepository, db, userRepository, farmerProfileRepository,
            productRepository, stockReservationRepository, paymentDestinations, mapper, clock, scheduleGuard, notifications, "")
        val error = assertThrows(ApiException::class.java) {
            org.springframework.transaction.support.TransactionTemplate(transactionManager).execute {
                offline.submit(buyer, order, "proof-no-storage", qrisUpload())
            }
        }
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.status)
        assertThrows(ApiException::class.java) { paymentSubmissions.submit(buyer, order, "bad-proof", qrisUpload(byteArrayOf(1, 2, 3))) }
        assertEquals("menunggu_pembayaran", orderStatus(order))
        assertTrue(paymentSubmissions.list(buyer, "pembeli", order).isEmpty())
    }

    @Test fun `F6 HTTP proofs are private and decision must name submission`() {
        val order = awaitingPayment()
        val response = mvc.perform(multipart("/api/v1/buyer/orders/$order/payment-proof").file(qrisUpload())
            .header("Idempotency-Key", "http-proof").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("menunggu_verifikasi"))
            .andExpect(jsonPath("$.data.proofImagePath").doesNotExist()).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        mvc.perform(get("/api/v1/petani/orders/$order/payment-proofs/$id/image").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(get("/api/v1/petani/orders/$order/payment-proofs/$id/image").with(jwt().jwt { it.subject(otherFarmer.toString()) }))
            .andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/petani/orders/$order/payment-verify").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"decision":"terima"}"""))
            .andExpect(status().isBadRequest)
        mvc.perform(put("/api/v1/petani/orders/$order/payment-verify").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"submissionId":"$id","decision":"terima"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.status").value("terverifikasi"))
    }

    @Test fun `farmer uploads qris privately and buyer can select it after acceptance`() {
        val image = testQris()
        val response = mvc.perform(multipart("/api/v1/petani/payment-methods/qris").file(qrisUpload(image))
            .param("accountHolderName", "Toko Petani")
            .header("Idempotency-Key", "qris-upload-1").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.methodType").value("qris"))
            .andExpect(jsonPath("$.data.qrisImagePath").doesNotExist()).andReturn()
        val id = UUID.fromString(mapper.readTree(response.response.contentAsString)["data"]["id"].asText())
        mvc.perform(get("/api/v1/petani/payment-methods/$id/qris").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(content().bytes(image)).andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(get("/api/v1/petani/payment-methods/$id/qris").with(jwt().jwt { it.subject(otherFarmer.toString()) }))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/petani/payment-methods").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.length()").value(2))
        val order = checkout.checkout(buyer, "choose-uploaded-qris", request())
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        paymentDestinations.chooseForBuyer(buyer, order.orderId, id)
        assertArrayEquals(image, paymentDestinations.imageForBuyer(buyer, order.orderId).bytes)
    }

    @Test fun `qris changes preserve old order snapshot and new orders use updated image`() {
        val original = testQris()
        val method = farmerPaymentMethods.uploadQris(farmer, "change-qris", "Toko Lama", qrisUpload(original))
        val oldOrder = checkout.checkout(buyer, "before-qris-change", request())
        val updatedImage = java.io.ByteArrayOutputStream().use { out ->
            val bitmap = java.awt.image.BufferedImage(3, 3, java.awt.image.BufferedImage.TYPE_INT_RGB)
            bitmap.setRGB(0, 0, 0xffffff)
            javax.imageio.ImageIO.write(bitmap, "png", out)
            out.toByteArray()
        }
        val put = multipart("/api/v1/petani/payment-methods/${method.id}/qris").file(qrisUpload(updatedImage))
            .param("accountHolderName", "Toko Baru").with(jwt().jwt { it.subject(farmer.toString()) })
        put.with { it.method = "PUT"; it }
        mvc.perform(put).andExpect(status().isOk).andExpect(jsonPath("$.data.accountHolderName").value("Toko Baru"))
        assertArrayEquals(updatedImage, farmerPaymentMethods.image(farmer, method.id).bytes)
        lifecycle.respondByFarmer(farmer, oldOrder.orderId, OrderRespondRequest("terima"))
        assertArrayEquals(original, paymentDestinations.optionImageForBuyer(buyer, oldOrder.orderId, method.id).bytes)
        val newOrder = checkout.checkout(buyer, "after-qris-change", request())
        lifecycle.respondByFarmer(farmer, newOrder.orderId, OrderRespondRequest("terima"))
        assertArrayEquals(updatedImage, paymentDestinations.optionImageForBuyer(buyer, newOrder.orderId, method.id).bytes)
        assertThrows(ApiException::class.java) { farmerPaymentMethods.updateQris(otherFarmer, method.id, "Lain", qrisUpload()) }
        assertThrows(ApiException::class.java) { farmerPaymentMethods.updateQris(farmer, method.id, "Toko", qrisUpload(byteArrayOf(1, 2))) }
        assertArrayEquals(updatedImage, farmerPaymentMethods.image(farmer, method.id).bytes)
    }

    @Test fun `deleting qris keeps existing order and requires another payment method for active products`() {
        val image = testQris()
        val method = farmerPaymentMethods.uploadQris(farmer, "remove-qris", "Toko", qrisUpload(image))
        val order = checkout.checkout(buyer, "qris-before-delete", request())
        assertThrows(ApiException::class.java) { farmerPaymentMethods.deleteQris(otherFarmer, method.id) }
        mvc.perform(delete("/api/v1/petani/payment-methods/${method.id}/qris").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isNoContent)
        assertTrue(farmerPaymentMethods.list(farmer).none { it.id == method.id })
        assertThrows(ApiException::class.java) { farmerPaymentMethods.image(farmer, method.id) }
        assertThrows(ApiException::class.java) { farmerPaymentMethods.uploadQris(farmer, "remove-qris", "Toko", qrisUpload(image)) }
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        assertArrayEquals(image, paymentDestinations.optionImageForBuyer(buyer, order.orderId, method.id).bytes)
        val onlyQris = farmerPaymentMethods.uploadQris(farmer, "last-qris", "Toko", qrisUpload())
        db.update("UPDATE farmer_payment_methods SET is_active=false WHERE id=?", paymentOption.id)
        assertThrows(ApiException::class.java) { farmerPaymentMethods.deleteQris(farmer, onlyQris.id) }
        assertTrue(farmerPaymentMethods.list(farmer).any { it.id == onlyQris.id })
    }

    @Test fun `qris upload retry and simultaneous requests create one method`() {
        val results = parallel(
            { farmerPaymentMethods.uploadQris(farmer, "qris-retry", "Toko", qrisUpload()) },
            { farmerPaymentMethods.uploadQris(farmer, "qris-retry", "Toko", qrisUpload()) }
        )
        assertTrue(results.all { it.isSuccess }, results.joinToString { it.exceptionOrNull()?.toString() ?: "ok" })
        assertEquals(results[0].getOrThrow().id, results[1].getOrThrow().id)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM farmer_payment_methods WHERE farmer_id=? AND method_type='qris'", Int::class.java, farmer))
        assertThrows(ApiException::class.java) { farmerPaymentMethods.uploadQris(farmer, "qris-retry", "Nama berbeda", qrisUpload()) }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM qris_upload_requests WHERE farmer_id=?", Int::class.java, farmer))
    }

    @Test fun `qris upload rejects wrong actors empty or invalid images and missing fields`() {
        mvc.perform(multipart("/api/v1/petani/payment-methods/qris").file(qrisUpload())
            .param("accountHolderName", "Toko").header("Idempotency-Key", "no-auth"))
            .andExpect(status().isUnauthorized)
        mvc.perform(multipart("/api/v1/petani/payment-methods/qris").file(qrisUpload())
            .param("accountHolderName", "Toko").header("Idempotency-Key", "buyer")
            .with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(multipart("/api/v1/petani/payment-methods/qris").file(qrisUpload())
            .param("accountHolderName", "Toko").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isBadRequest)
        mvc.perform(multipart("/api/v1/petani/payment-methods/qris")
            .param("accountHolderName", "Toko").header("Idempotency-Key", "missing-file")
            .with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isBadRequest)
        listOf(byteArrayOf(), byteArrayOf(1, 2, 3), ByteArray(5 * 1024 * 1024 + 1)).forEachIndexed { index, bytes ->
            mvc.perform(multipart("/api/v1/petani/payment-methods/qris").file(qrisUpload(bytes))
                .param("accountHolderName", "Toko").header("Idempotency-Key", "bad-$index")
                .with(jwt().jwt { it.subject(farmer.toString()) })).andExpect(status().isBadRequest)
        }
        db.update("UPDATE users SET status='suspended' WHERE id=?", farmer)
        assertThrows(ApiException::class.java) { farmerPaymentMethods.uploadQris(farmer, "suspended", "Toko", qrisUpload()) }
        assertEquals(0, db.queryForObject("SELECT count(*) FROM qris_upload_requests", Int::class.java))
    }

    @Test fun `qris upload fails closed when storage is not configured`() {
        val service = com.panenku.backend.modules.petani.service.FarmerPaymentMethodService(
            actors, paymentMethodRepository, db, mapper, "")
        val error = assertThrows(ApiException::class.java) {
            org.springframework.transaction.support.TransactionTemplate(transactionManager).execute {
                service.uploadQris(farmer, "no-storage", "Toko", qrisUpload())
            }
        }
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.status)
        assertEquals(0, db.queryForObject("SELECT count(*) FROM qris_upload_requests", Int::class.java))
    }

    @Test fun `qris file is removed if database transaction rolls back`() {
        val before = java.nio.file.Files.list(paymentImageDir).use { it.count() }
        db.execute("CREATE FUNCTION test_qris_upload_failure() RETURNS TRIGGER LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated failure''; END;'")
        db.execute("CREATE TRIGGER test_qris_failure BEFORE INSERT ON qris_upload_requests FOR EACH ROW EXECUTE FUNCTION test_qris_upload_failure()")
        try {
            assertThrows(Exception::class.java) { farmerPaymentMethods.uploadQris(farmer, "rollback", "Toko", qrisUpload()) }
            assertEquals(0, db.queryForObject("SELECT count(*) FROM farmer_payment_methods WHERE farmer_id=? AND method_type='qris'", Int::class.java, farmer))
            assertEquals(before, java.nio.file.Files.list(paymentImageDir).use { it.count() })
        } finally {
            db.execute("DROP TRIGGER test_qris_failure ON qris_upload_requests")
            db.execute("DROP FUNCTION test_qris_upload_failure()")
        }
    }

    @Test fun `payment selection is restricted to order options after farmer acceptance`() {
        val order = checkout.checkout(buyer, "payment-choice", request())
        val other = paymentDestinations.listForBuyer(buyer, otherFarmer).single()
        assertThrows(ApiException::class.java) { paymentDestinations.optionsForBuyer(buyer, order.orderId) }
        assertThrows(ApiException::class.java) { paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id) }
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        assertThrows(ApiException::class.java) {
            paymentDestinations.chooseForBuyer(buyer, order.orderId, other.id)
        }
        db.update("UPDATE farmer_payment_methods SET account_number='00999' WHERE id=?", paymentOption.id)
        assertEquals("00123", paymentDestinations.optionsForBuyer(buyer, order.orderId).single().accountNumber)
        assertEquals("00123", paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id).accountNumber)
        assertThrows(ApiException::class.java) { paymentDestinations.optionsForBuyer(otherBuyer, order.orderId) }
    }

    @Test fun `payment choice can change before proof but snapshot locks after proof`() {
        val second = UUID.randomUUID()
        db.update("INSERT INTO farmer_payment_methods(id,farmer_id,method_type,bank_name,account_number,account_holder_name) VALUES (?,?,'bank_transfer','Bank Kedua','00456','Petani Dummy')", second, farmer)
        val order = checkout.checkout(buyer, "two-methods", request())
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        assertEquals(2, paymentDestinations.optionsForBuyer(buyer, order.orderId).size)
        assertThrows(ApiException::class.java) { paymentSubmissions.submit(buyer, order.orderId, "no-choice", qrisUpload()) }
        assertEquals("menunggu_pembayaran", orderStatus(order.orderId))
        paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id)
        assertEquals("00456", paymentDestinations.chooseForBuyer(buyer, order.orderId, second).accountNumber)
        val proof = paymentSubmissions.submit(buyer, order.orderId, "chosen-proof", qrisUpload())
        val chosen = db.queryForObject("SELECT method_id FROM order_payment_snapshots WHERE order_id=?", UUID::class.java, order.orderId)
        assertEquals(second, chosen)
        paymentSubmissions.decide(farmer, order.orderId, com.panenku.backend.modules.shared.service.PaymentDecisionRequest(proof.id, "tolak", "Tidak jelas"))
        assertThrows(ApiException::class.java) { paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id) }
        assertEquals("00456", paymentDestinations.destinationForBuyer(buyer, order.orderId).accountNumber)
    }

    @Test fun `payment options HTTP require buyer ownership and farmer acceptance`() {
        val order = checkout.checkout(buyer, "http-payment-options", request())
        val optionsUrl = "/api/v1/buyer/orders/${order.orderId}/payment-options"
        val choiceUrl = "/api/v1/buyer/orders/${order.orderId}/payment-destination"
        mvc.perform(get(optionsUrl).with(jwt().jwt { it.subject(buyer.toString()) })).andExpect(status().isConflict)
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        mvc.perform(get(optionsUrl).with(jwt().jwt { it.subject(otherBuyer.toString()) })).andExpect(status().isForbidden)
        mvc.perform(get(optionsUrl).with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data[0].id").value(paymentOption.id.toString()))
            .andExpect(jsonPath("$.data[0].accountNumber").value("00123"))
            .andExpect(header().string("Cache-Control", "no-store"))
        mvc.perform(put(choiceUrl).with(jwt().jwt { it.subject(buyer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"paymentMethodId":"${paymentOption.id}"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.accountNumber").value("00123"))
        mvc.perform(multipart("/api/v1/buyer/orders/${order.orderId}/payment-proof").file(qrisUpload())
            .header("Idempotency-Key", "http-proof-new-flow").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isCreated).andExpect(jsonPath("$.data.status").value("menunggu_verifikasi"))
    }

    @Test fun `bank destination is immutable private and checkout replay remains idempotent`() {
        val req = request()
        val order = checkout.checkout(buyer, "bank-payment", req)
        assertThrows(ApiException::class.java) { paymentDestinations.destinationForBuyer(buyer, order.orderId) }
        db.update("UPDATE farmer_payment_methods SET account_number='00999' WHERE id=?", paymentOption.id)
        assertEquals(order.orderId, checkout.checkout(buyer, "bank-payment", req).orderId)
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        paymentDestinations.chooseForBuyer(buyer, order.orderId, paymentOption.id)
        assertEquals("00123", paymentDestinations.destinationForBuyer(buyer, order.orderId).accountNumber)
        assertThrows(ApiException::class.java) { paymentDestinations.destinationForBuyer(otherBuyer, order.orderId) }
        (clock as TestClock).time.set(order.expiresAt.plusSeconds(24 * 60 * 60))
        assertThrows(ApiException::class.java) { paymentDestinations.destinationForBuyer(buyer, order.orderId) }
        (clock as TestClock).time.set(Instant.parse("2026-11-10T00:00:00Z"))
        paymentSubmissions.submit(buyer, order.orderId, "lock-bank", qrisUpload())
        assertThrows(Exception::class.java) {
            db.update("UPDATE order_payment_snapshots SET account_number='wrong' WHERE order_id=?", order.orderId)
        }
        mvc.perform(get("/api/v1/buyer/orders/${order.orderId}/payment-destination").with(jwt().jwt { it.subject(otherBuyer.toString()) }))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/buyer/farmers/$farmer/payment-methods"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/buyer/farmers/$farmer/payment-methods").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(jsonPath("$.data[0].accountNumberLast4").value("0999"))
            .andExpect(jsonPath("$.data[0].accountNumber").doesNotExist())
        assertEquals(1L, count("orders"))
    }

    @Test fun `qris image is snapshotted and remains private after original is deleted`() {
        val output = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output)
        val image = output.toByteArray()
        val store = com.panenku.backend.common.storage.LocalPrivateImageStore(paymentImageDir)
        val key = store.upload(org.springframework.mock.web.MockMultipartFile("file", "qris.png", "image/png", image))
        val id = UUID.randomUUID()
        db.update("INSERT INTO farmer_payment_methods(id,farmer_id,method_type,qris_image_path,account_holder_name) VALUES (?,?,'qris',?,'Toko Dummy')", id, farmer, key)
        val order = checkout.checkout(buyer, "qris-payment", request())
        store.delete(key)
        lifecycle.respondByFarmer(farmer, order.orderId, OrderRespondRequest("terima"))
        assertArrayEquals(image, paymentDestinations.optionImageForBuyer(buyer, order.orderId, id).bytes)
        mvc.perform(get("/api/v1/buyer/orders/${order.orderId}/payment-options/$id/qris").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(content().bytes(image)).andExpect(header().string("Cache-Control", "no-store"))
        paymentDestinations.chooseForBuyer(buyer, order.orderId, id)
        assertArrayEquals(image, paymentDestinations.imageForBuyer(buyer, order.orderId).bytes)
        assertThrows(ApiException::class.java) { paymentDestinations.imageForBuyer(otherBuyer, order.orderId) }
        mvc.perform(get("/api/v1/buyer/orders/${order.orderId}/payment-destination/qris").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk).andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control", "no-store"))
    }

    @Test fun `new checkout does not select payment before farmer accepts`() {
        val oldRequest = request().copy(paymentMethodId = paymentOption.id, paymentMethodVersion = paymentOption.version)
        assertFalse(mapper.writeValueAsString(request()).contains("paymentMethodId"))
        assertThrows(ApiException::class.java) { checkout.checkout(buyer, "legacy-new", oldRequest) }
        assertEquals(0L, count("orders"))
    }

    @Test fun `historical checkout can replay payment fields without guessed snapshot`() {
        val current = checkout.checkout(buyer, "current-copy", request())
        val oldRequest = request().copy(paymentMethodId = paymentOption.id, paymentMethodVersion = paymentOption.version)
        val normalized = oldRequest.copy(items = oldRequest.items.sortedBy { it.productId }.map {
            it.copy(quantityKg = com.panenku.backend.modules.shared.service.CheckoutMath.kg(it.quantityKg))
        })
        val oldHash = java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(normalized))
            .joinToString("") { "%02x".format(it) }
        val legacyId = UUID.randomUUID()
        db.update("""INSERT INTO orders(id,buyer_id,farmer_id,fulfillment_method,delivery_fee,subtotal_items,total_payment,
            transaction_limit_applied,order_status,payment_status,scheduled_date,scheduled_time_slot,pickup_location_snapshot,
            delivery_address_snapshot,delivery_district_snapshot,buyer_notes,received_at,reserved_at,expires_at,created_at,updated_at,idempotency_key,request_hash)
            SELECT ?,buyer_id,farmer_id,fulfillment_method,delivery_fee,subtotal_items,total_payment,transaction_limit_applied,
            order_status,payment_status,scheduled_date,scheduled_time_slot,pickup_location_snapshot,delivery_address_snapshot,
            delivery_district_snapshot,buyer_notes,received_at,reserved_at,expires_at,created_at,updated_at,?,? FROM orders WHERE id=?""",
            legacyId, "legacy-replay", oldHash, current.orderId)
        assertEquals(legacyId, checkout.checkout(buyer, "legacy-replay", oldRequest).orderId)
        assertEquals(0, db.queryForObject("SELECT count(*) FROM order_payment_snapshots WHERE order_id=?", Int::class.java, legacyId))
        db.update("""INSERT INTO order_payment_snapshots(order_id,method_id,method_type,account_holder_name,bank_name,account_number)
            VALUES (?,?,'bank_transfer','Petani Lama','Bank Lama','00123')""", legacyId, paymentOption.id)
        lifecycle.respondByFarmer(farmer, legacyId, OrderRespondRequest("terima"))
        assertEquals(paymentOption.id, paymentDestinations.optionsForBuyer(buyer, legacyId).single().id)
        assertEquals("00123", paymentDestinations.chooseForBuyer(buyer, legacyId, paymentOption.id).accountNumber)
        assertEquals("menunggu_verifikasi", paymentSubmissions.submit(buyer, legacyId, "legacy-proof", qrisUpload()).status)
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
        val req = ProductRequest(commodity, "Draft", 10000, BigDecimal.ONE, date)
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

    @Test fun `F5 farmer accepting resets payment window and records history`() {
        val id = orderFor()
        (clock as TestClock).time.set(Instant.parse("2026-11-10T01:00:00Z"))
        val view = lifecycle.respondByFarmer(farmer, id, OrderRespondRequest("terima"))

        assertEquals("menunggu_pembayaran", view.orderStatus)
        assertEquals(Instant.parse("2026-11-11T01:00:00Z"), view.expiresAt)
        assertEquals(2, view.history.size)
        assertEquals("petani", view.history.last().actorRole)
        assertEquals("menunggu_petani", view.history.last().fromStatus)
        assertEquals("menunggu_pembayaran", view.history.last().toStatus)
        assertEquals("active", reservationStatus(id))
        assertEquals(BigDecimal("0.75"), reserved())
    }

    @Test fun `F5 rejecting requires reason and releases stock`() {
        val id = orderFor()

        val missingReason = assertThrows(ApiException::class.java) {
            lifecycle.respondByFarmer(farmer, id, OrderRespondRequest("tolak"))
        }
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, missingReason.status)
        assertEquals("menunggu_petani", orderStatus(id))
        assertEquals(BigDecimal("0.75"), reserved())

        assertThrows(ApiException::class.java) {
            lifecycle.respondByFarmer(otherFarmer, id, OrderRespondRequest("tolak", "bukan milik saya"))
        }
        assertEquals("menunggu_petani", orderStatus(id))

        val view = lifecycle.respondByFarmer(farmer, id, OrderRespondRequest("tolak", "Stok habis"))
        assertEquals("ditolak", view.orderStatus)
        assertEquals("released", reservationStatus(id))
        assertEquals(BigDecimal("0.00"), reserved())
        assertEquals(BigDecimal("10.00"), stockPhysical())
    }

    @Test fun `F5 buyer can cancel only before farmer responds`() {
        val id = orderFor()
        val view = lifecycle.cancelByBuyer(buyer, id, OrderCancelRequest("berubah pikiran"))

        assertEquals("dibatalkan", view.orderStatus)
        assertEquals("released", reservationStatus(id))
        assertEquals(BigDecimal("0.00"), reserved())
        assertTrue(view.history.last().note!!.contains("berubah pikiran"))

        val accepted = orderFor(status = "menunggu_pembayaran")
        val conflict = assertThrows(ApiException::class.java) {
            lifecycle.cancelByBuyer(buyer, accepted, OrderCancelRequest())
        }
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.status)
        assertEquals("menunggu_pembayaran", orderStatus(accepted))

        val otherBuyersOrder = orderFor()
        assertThrows(ApiException::class.java) {
            lifecycle.cancelByBuyer(otherBuyer, otherBuyersOrder, OrderCancelRequest())
        }
        assertEquals("menunggu_petani", orderStatus(otherBuyersOrder))
    }

    @Test fun `F5 pickup fulfillment then buyer receive completes order`() {
        val id = orderFor(status = "diproses", payment = "terverifikasi")

        atSchedule(id)
        val ready = lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("siap_diambil"))
        assertEquals("siap_diambil", ready.orderStatus)
        assertEquals("active", reservationStatus(id))
        assertEquals(BigDecimal("0.75"), reserved())
        assertEquals(BigDecimal("10.00"), stockPhysical())

        val waiting = lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("menunggu_penerimaan"))
        assertEquals("menunggu_penerimaan", waiting.orderStatus)
        assertEquals("fulfilled", reservationStatus(id))
        assertEquals(BigDecimal("0.00"), reserved())
        assertEquals(BigDecimal("9.25"), stockPhysical())

        val done = lifecycle.receiveByBuyer(buyer, id)
        assertEquals("selesai", done.orderStatus)
        assertNotNull(done.receivedAt)
        assertEquals("selesai", orderStatus(id))
    }

    @Test fun `F5 delivery fulfillment rejects pickup status`() {
        val id = orderFor(req = deliveryRequest(), status = "diproses", payment = "terverifikasi")

        val invalid = assertThrows(ApiException::class.java) {
            lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("siap_diambil"))
        }
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, invalid.status)
        assertEquals("diproses", orderStatus(id))

        atSchedule(id)
        val shipped = lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("diantar"))
        assertEquals("diantar", shipped.orderStatus)
        val waiting = lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("menunggu_penerimaan"))
        assertEquals("menunggu_penerimaan", waiting.orderStatus)
        assertEquals(BigDecimal("9.25"), stockPhysical())
    }

    @Test fun `F5 fulfillment requires verified payment`() {
        val id = orderFor(status = "diproses", payment = "menunggu_verifikasi")

        val conflict = assertThrows(ApiException::class.java) {
            lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("siap_diambil"))
        }
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.status)
        assertEquals("diproses", orderStatus(id))
        assertEquals("active", reservationStatus(id))
    }

    @Test fun `F5 failed fulfillment requires note and releases stock`() {
        val id = orderFor(status = "diproses", payment = "terverifikasi")

        val missingNote = assertThrows(ApiException::class.java) {
            lifecycle.updateFulfillment(farmer, id, FulfillmentUpdateRequest("gagal_dipenuhi"))
        }
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, missingNote.status)
        assertEquals("diproses", orderStatus(id))

        val view = lifecycle.updateFulfillment(
            farmer, id, FulfillmentUpdateRequest("gagal_dipenuhi", "Panen gagal")
        )
        assertEquals("gagal_dipenuhi", view.orderStatus)
        assertEquals("released", reservationStatus(id))
        assertEquals(BigDecimal("0.00"), reserved())
        assertEquals(BigDecimal("10.00"), stockPhysical())
    }

    @Test fun `F5 order lists and details enforce ownership and pagination`() {
        val first = orderFor()
        val second = orderFor(owner = otherBuyer)

        val buyerPage = lifecycle.listForBuyer(buyer, 0, 20)
        assertEquals(1, buyerPage.items.size)
        assertEquals(first, buyerPage.items.single().orderId)
        assertTrue(buyerPage.items.single().history.isEmpty())

        val farmerPage = lifecycle.listForFarmer(farmer, 0, 20)
        assertEquals(2, farmerPage.items.size)
        assertEquals(setOf(first, second), farmerPage.items.map { it.orderId }.toSet())
        assertEquals("Toko $farmer", lifecycle.detailForFarmer(farmer, first).storeName)

        assertThrows(ApiException::class.java) { lifecycle.detailForBuyer(otherBuyer, first) }
        assertThrows(ApiException::class.java) { lifecycle.detailForFarmer(otherFarmer, first) }
        assertThrows(ApiException::class.java) { lifecycle.listForFarmer(buyer, 0, 20) }
        assertThrows(ApiException::class.java) { lifecycle.listForBuyer(buyer, 0, 0) }
        assertThrows(ApiException::class.java) { lifecycle.listForBuyer(buyer, 0, 101) }
    }

    @Test fun `F5 HTTP endpoints enforce role and return order data`() {
        val id = orderFor()

        mvc.perform(get("/api/v1/buyer/orders").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].orderId").value(id.toString()))
            .andExpect(jsonPath("$.data.items[0].history").isEmpty)

        mvc.perform(get("/api/v1/petani/orders").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].orderId").value(id.toString()))

        mvc.perform(get("/api/v1/buyer/orders").with(jwt().jwt { it.subject(farmer.toString()) }))
            .andExpect(status().isForbidden)

        mvc.perform(get("/api/v1/buyer/orders/$id").with(jwt().jwt { it.subject(otherBuyer.toString()) }))
            .andExpect(status().isForbidden)

        mvc.perform(put("/api/v1/petani/orders/$id/respond").with(jwt().jwt { it.subject(farmer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"decision":"terima"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderStatus").value("menunggu_pembayaran"))

        mvc.perform(post("/api/v1/buyer/orders/$id/cancel").with(jwt().jwt { it.subject(buyer.toString()) }))
            .andExpect(status().isConflict)

        mvc.perform(post("/api/v1/buyer/orders/$id/cancel").with(jwt().jwt { it.subject(buyer.toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"batal"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").exists())
    }
}
