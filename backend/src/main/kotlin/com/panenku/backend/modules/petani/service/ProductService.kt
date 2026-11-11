package com.panenku.backend.modules.petani.service

import com.panenku.backend.common.exception.*
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.petani.dto.ProductRequest
import com.panenku.backend.modules.shared.dto.*
import com.panenku.backend.modules.shared.entity.Product
import com.panenku.backend.modules.shared.repository.*
import com.panenku.backend.modules.shared.service.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ProductService(
    private val actors: ActorService, private val products: ProductRepository, private val commodities: CommodityRepository,
    private val farmers: FarmerProfileRepository, private val payments: PaymentMethodRepository, private val catalog: CatalogService,
    private val clock: Clock
) {
    fun list(actor: UUID, page: Int, size: Int): PageDTO<ProductDTO> {
        actors.require(actor, "petani")
        if (page < 0 || size !in 1..100) invalid("Paginasi tidak valid.")
        val result = products.findAllByFarmerId(actor, PageRequest.of(page, size, Sort.by("id")))
        return PageDTO(result.content.map(catalog::dto), page, size, result.totalElements)
    }
    fun detail(actor: UUID, id: UUID): ProductDTO { actors.require(actor, "petani"); return catalog.dto(owned(actor, id)) }
    fun save(actor: UUID, id: UUID?, request: ProductRequest): ProductDTO {
        actors.require(actor, "petani")
        val farmer = farmers.lockById(actor) ?: conflict("Lengkapi profil petani.")
        val commodity = commodities.lockById(request.commodityId) ?: missing("Komoditas tidak ditemukan.")
        if (!commodity.isActive) invalid("Komoditas nonaktif.")
        val product = id?.let { owned(actor, it) } ?: Product(farmerId = actor)
        if (request.name.isBlank() || request.name.length > 255 || request.pricePerKg <= 0 || (request.description?.length ?: 0) > 5000 || (request.photoUrl?.length ?: 0) > 512) invalid("Data produk tidak valid.")
        if (request.publicationStatus !in listOf("draft", "aktif", "nonaktif")) invalid("Status publikasi tidak valid.")
        val stock = CheckoutMath.kg(request.stockPhysical, allowZero = true)
        if (stock < product.stockReserved) conflict("Stok fisik tidak boleh di bawah stok yang dicadangkan.")
        if (request.publicationStatus == "aktif") {
            if (farmer.ktpStatus != "disetujui") forbidden("KTP belum disetujui.")
            if (farmer.pickupLocation.isNullOrBlank() || payments.findAllByFarmerId(actor).none { it.complete() }) conflict("Lengkapi lokasi dan metode pembayaran.")
            if (stock.signum() <= 0 || request.photoUrl.isNullOrBlank()) invalid("Produk aktif memerlukan stok dan foto.")
        }
        product.commodityId = request.commodityId; product.name = request.name.trim(); product.description = request.description
        product.photoUrl = request.photoUrl; product.pricePerKg = request.pricePerKg; product.stockPhysical = stock
        product.readyDate = request.readyDate; product.publicationStatus = request.publicationStatus; product.updatedAt = Instant.now(clock)
        products.saveAndFlush(product)
        return catalog.dto(product)
    }
    fun deactivate(actor: UUID, id: UUID) {
        actors.require(actor, "petani")
        val product = owned(actor, id)
        product.publicationStatus = "nonaktif"; product.updatedAt = Instant.now(clock)
    }
    private fun owned(actor: UUID, id: UUID): Product {
        val product = products.lockById(id) ?: missing("Produk tidak ditemukan.")
        if (product.farmerId != actor) forbidden("Produk bukan milik petani ini.")
        return product
    }
}
