package com.panenku.backend.modules.pembeli.service

import com.panenku.backend.common.exception.*
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.dto.*
import com.panenku.backend.modules.pembeli.entity.CartItem
import com.panenku.backend.modules.pembeli.repository.CartRepository
import com.panenku.backend.modules.shared.repository.ProductRepository
import com.panenku.backend.modules.shared.service.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional
class CartService(private val actors: ActorService, private val carts: CartRepository,
                  private val products: ProductRepository, private val catalog: CatalogService) {
    fun get(actor: UUID): CartDTO { actors.require(actor, "pembeli"); return view(actor) }
    fun add(actor: UUID, request: CartItemRequest): CartDTO {
        actors.require(actor, "pembeli")
        CheckoutMath.kg(request.quantityKg)
        val product = products.findById(request.productId).orElseGet { missing("Produk tidak ditemukan.") }
        catalog.requireAvailable(product)
        val current = carts.findAllByBuyerIdOrderById(actor)
        if (current.any { products.findById(it.productId).orElseThrow().farmerId != product.farmerId }) conflict("Keranjang berbeda petani. Hapus item lama setelah konfirmasi pembeli.")
        val existing = current.find { it.productId == product.id }
        if (existing == null && current.size >= 100) invalid("Maksimal 100 produk per keranjang.")
        val quantity = CheckoutMath.kg((existing?.quantityKg ?: BigDecimal.ZERO).add(request.quantityKg))
        if (quantity > product.available) conflict("Jumlah melebihi stok tersedia.")
        carts.saveAndFlush((existing ?: CartItem(buyerId = actor, productId = product.id)).apply { quantityKg = quantity })
        return view(actor)
    }
    fun update(actor: UUID, id: UUID, quantity: BigDecimal): CartDTO {
        actors.require(actor, "pembeli")
        val item = owned(actor, id)
        val value = CheckoutMath.kg(quantity)
        val product = products.findById(item.productId).orElseThrow()
        catalog.requireAvailable(product)
        if (value > product.available) conflict("Jumlah melebihi stok tersedia.")
        item.quantityKg = value
        carts.flush()
        return view(actor)
    }
    fun delete(actor: UUID, id: UUID) { actors.require(actor, "pembeli"); carts.delete(owned(actor, id)) }
    private fun owned(actor: UUID, id: UUID): CartItem {
        val item = carts.findById(id).orElseGet { missing("Item keranjang tidak ditemukan.") }
        if (item.buyerId != actor) forbidden("Item keranjang bukan milik pembeli ini.")
        return item
    }
    private fun view(actor: UUID): CartDTO {
        val items = carts.findAllByBuyerIdOrderById(actor).map { item ->
            val p = products.findById(item.productId).orElseThrow()
            val available = catalog.isAvailable(p) && item.quantityKg <= p.available
            CartItemDTO(item.id, p.id, p.name, item.quantityKg, p.pricePerKg, CheckoutMath.item(item.quantityKg, p.pricePerKg), p.available, available)
        }
        return CartDTO(items, items.fold(BigDecimal.ZERO) { sum, item -> sum.add(BigDecimal.valueOf(item.itemSubtotal)) })
    }
}
