package com.panenku.backend.modules.pembeli.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.panenku.backend.common.exception.*
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.dto.*
import com.panenku.backend.modules.pembeli.entity.*
import com.panenku.backend.modules.pembeli.repository.*
import com.panenku.backend.modules.shared.repository.*
import com.panenku.backend.modules.shared.service.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class CheckoutService(
    private val actors: ActorService, private val accounts: UserRepository,
    private val products: ProductRepository, private val commodities: CommodityRepository,
    private val farmers: FarmerProfileRepository, private val payments: PaymentMethodRepository,
    private val settings: SettingRepository, private val orders: OrderRepository,
    private val orderItems: OrderItemRepository, private val reservations: StockReservationRepository,
    private val history: OrderStatusHistoryRepository, private val scheduling: SchedulingService,
    private val mapper: ObjectMapper, private val clock: Clock
) {
    @Transactional
    fun checkout(actor: UUID, key: String, request: CheckoutRequest): OrderDTO {
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(key)) invalid("Idempotency-Key wajib, maksimal 128 karakter huruf/angka/_/-.")
        if (request.items.isEmpty() || request.items.size > 100 || request.items.map { it.productId }.distinct().size != request.items.size) invalid("Isi 1–100 item produk unik.")
        val normalized = request.copy(items = request.items.sortedBy { it.productId }.map { it.copy(quantityKg = CheckoutMath.kg(it.quantityKg)) })
        if ((request.buyerNotes?.length ?: 0) > 1000 || (request.deliveryAddress?.length ?: 0) > 2000 || (request.deliveryDistrict?.length ?: 0) > 100 || request.scheduledTimeSlot.length > 100) invalid("Teks request terlalu panjang.")
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(normalized)).joinToString("") { "%02x".format(it) }
        // Serialize same-buyer retries/cart edits. All stock-changing operations lock farmer then products.
        actors.require(actor, "pembeli")
        orders.findByBuyerIdAndIdempotencyKey(actor, key)?.let {
            if (it.requestHash != hash) conflict("Idempotency-Key sudah digunakan untuk request berbeda.")
            return dto(it)
        }
        val discovered = normalized.items.map { products.reference(it.productId) ?: missing("Produk tidak ditemukan.") }
        val farmerId = discovered.first().farmerId
        if (discovered.any { it.farmerId != farmerId }) invalid("Satu checkout hanya untuk satu petani.")
        val farmerAccount = accounts.lockById(farmerId) ?: conflict("Petani tidak tersedia.")
        val farmer = farmers.lockById(farmerId) ?: conflict("Profil petani tidak tersedia.")
        if (farmerAccount.role != "petani" || farmerAccount.status != "active" || farmer.ktpStatus != "disetujui") conflict("Petani belum terverifikasi atau disuspend.")
        if (farmer.pickupLocation.isNullOrBlank() || payments.findAllByFarmerId(farmerId).none { it.complete() }) conflict("Profil atau metode pembayaran belum lengkap.")
        val commodityMap = discovered.map { it.commodityId }.distinct().sorted().associateWith { commodities.lockById(it) ?: conflict("Komoditas tidak tersedia.") }
        val locked = normalized.items.map { item ->
            // Scalar discovery avoids cached entities before acquiring row locks.
            products.lockById(item.productId) ?: missing("Produk tidak ditemukan.")
        }
        locked.forEachIndexed { index, product ->
            val item = normalized.items[index]
            if (product.farmerId != farmerId || commodityMap[product.commodityId]?.isActive != true || product.publicationStatus != "aktif") conflict("Produk tidak tersedia.")
            if (product.pricePerKg != item.expectedPricePerKg) conflict("Harga berubah. Muat ulang dan konfirmasi harga terbaru.")
            if (item.quantityKg > product.available) conflict("Stok berubah atau tidak mencukupi.")
        }
        scheduling.validate(request.scheduledDate, request.scheduledTimeSlot, locked.map { it.readyDate }, CatalogService.split(farmer.timeSlots))
        val fee = when (request.fulfillmentMethod) {
            "pickup" -> 0L
            "delivery" -> {
                if (!farmer.shippingEnabled || request.deliveryAddress.isNullOrBlank() || request.deliveryDistrict.isNullOrBlank() ||
                    CatalogService.split(farmer.serviceArea).none { it == request.deliveryDistrict }) invalid("Layanan antar atau kecamatan/alamat tidak tersedia.")
                farmer.deliveryFee
            }
            else -> invalid("Metode pemenuhan harus pickup atau delivery.")
        }
        if (fee != request.expectedDeliveryFee) conflict("Ongkir berubah. Konfirmasi ulang ongkir terbaru.")
        val limit = settings.lockByKey("transaction_limit")?.value?.toLongOrNull()?.takeIf { it > 0 } ?: conflict("Limit transaksi belum dikonfigurasi dengan benar.")
        val subtotals = locked.mapIndexed { index, p -> CheckoutMath.item(normalized.items[index].quantityKg, p.pricePerKg) }
        val total = CheckoutMath.total(subtotals, fee, limit)
        // PostgreSQL TIMESTAMPTZ stores microseconds; keep first response identical to later replays.
        val now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
        val order = orders.saveAndFlush(Order(buyerId = actor, farmerId = farmerId, fulfillmentMethod = request.fulfillmentMethod,
            deliveryFee = fee, subtotalItems = total - fee, totalPayment = total, transactionLimitApplied = limit,
            scheduledDate = request.scheduledDate, scheduledTimeSlot = request.scheduledTimeSlot,
            pickupLocationSnapshot = farmer.pickupLocation, deliveryAddressSnapshot = if (request.fulfillmentMethod == "delivery") request.deliveryAddress else null,
            deliveryDistrictSnapshot = if (request.fulfillmentMethod == "delivery") request.deliveryDistrict else null,
            buyerNotes = request.buyerNotes, reservedAt = now, expiresAt = now.plus(Duration.ofHours(24)), createdAt = now, updatedAt = now,
            idempotencyKey = key, requestHash = hash))
        locked.forEachIndexed { index, p ->
            val quantity = normalized.items[index].quantityKg
            orderItems.save(OrderItem(orderId = order.id, productId = p.id, productNameSnapshot = p.name,
                commoditySnapshot = commodityMap.getValue(p.commodityId).name, readyDateSnapshot = p.readyDate,
                quantityKg = quantity, pricePerKgSnapshot = p.pricePerKg, itemSubtotal = subtotals[index]))
            reservations.save(StockReservation(productId = p.id, orderId = order.id, quantityKg = quantity, reservedAt = now))
            p.stockReserved = p.stockReserved.add(quantity); p.updatedAt = now
        }
        history.save(OrderStatusHistory(orderId = order.id, actorId = actor, changedAt = now, note = "Checkout dan reservasi stok."))
        // Explicit flush also surfaces DB constraint errors before returning success; any failure rolls everything back.
        orders.flush()
        return dto(order)
    }
    private fun dto(order: Order): OrderDTO = OrderDTO(order.id, order.orderStatus, order.paymentStatus, order.subtotalItems,
        order.deliveryFee, order.totalPayment, order.transactionLimitApplied, order.fulfillmentMethod, order.scheduledDate,
        order.scheduledTimeSlot, order.pickupLocationSnapshot, order.deliveryAddressSnapshot, order.deliveryDistrictSnapshot,
        order.buyerNotes, order.expiresAt, orderItems.findAllByOrderIdOrderByProductId(order.id).map {
            OrderItemDTO(it.productId, it.productNameSnapshot, it.commoditySnapshot, it.readyDateSnapshot, it.quantityKg, it.pricePerKgSnapshot, it.itemSubtotal)
        })
}
