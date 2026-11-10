package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.exception.*
import com.panenku.backend.modules.shared.dto.*
import com.panenku.backend.modules.shared.entity.Product
import com.panenku.backend.modules.shared.repository.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CatalogService(
    private val products: ProductRepository, private val commodities: CommodityRepository,
    private val farmers: FarmerProfileRepository, private val accounts: UserRepository
) {
    fun list(search: String, commodityId: UUID?, sort: String, page: Int, size: Int): PageDTO<ProductDTO> {
        if (page < 0 || size !in 1..100 || search.length > 100) invalid("Paginasi atau pencarian tidak valid.")
        val direction = when (sort) { "price_asc" -> Sort.Direction.ASC; "price_desc" -> Sort.Direction.DESC; else -> invalid("Urutan harus price_asc atau price_desc.") }
        val escaped = search.trim().lowercase().replace("!", "!!").replace("%", "!%").replace("_", "!_")
        val result = products.catalog(commodityId, "%$escaped%", PageRequest.of(page, size, Sort.by(direction, "pricePerKg").and(Sort.by("id"))))
        return PageDTO(result.content.map(::dto), page, size, result.totalElements)
    }
    fun detail(id: UUID): ProductDetailDTO {
        val product = products.findById(id).orElseGet { missing("Produk tidak ditemukan.") }
        requireAvailable(product, allowSoldOut = true)
        val farmer = farmers.findById(product.farmerId).orElseThrow()
        return ProductDetailDTO(dto(product), farmer.pickupLocation, farmer.shippingEnabled, farmer.deliveryFee,
            split(farmer.serviceArea), split(farmer.timeSlots))
    }
    fun requireAvailable(product: Product, allowSoldOut: Boolean = false) {
        if (!isAvailable(product, allowSoldOut)) conflict("Produk atau petani tidak tersedia untuk pembelian baru.")
    }
    fun isAvailable(product: Product, allowSoldOut: Boolean = false): Boolean {
        val user = accounts.findById(product.farmerId).orElse(null)
        val farmer = farmers.findById(product.farmerId).orElse(null)
        val commodity = commodities.findById(product.commodityId).orElse(null)
        return user?.status == "active" && user.role == "petani" && farmer?.ktpStatus == "disetujui" &&
            commodity?.isActive == true && product.publicationStatus == "aktif" && (allowSoldOut || product.available.signum() > 0)
    }
    fun dto(p: Product): ProductDTO = ProductDTO(p.id, p.farmerId, farmers.findById(p.farmerId).orElseThrow().storeName,
        p.commodityId, commodities.findById(p.commodityId).orElseThrow().name, p.name, p.description, p.photoUrl,
        p.pricePerKg, p.stockPhysical, p.stockReserved, p.available, p.readyDate, p.publicationStatus)
    fun commodities(): List<CommodityDTO> = commodities.findAllByIsActiveTrueOrderByName().map { CommodityDTO(it.id, it.name, it.isActive) }
    companion object { fun split(value: String): List<String> = value.split('|').map(String::trim).filter(String::isNotEmpty) }
}
