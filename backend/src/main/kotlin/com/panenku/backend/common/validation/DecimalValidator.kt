package com.panenku.backend.common.validation

import com.panenku.backend.modules.shared.service.CheckoutMath
import java.math.BigDecimal
import java.math.RoundingMode

/** Shared decimal rules, consistent with catalog/cart/checkout. */
object DecimalValidator {
    fun roundToRupiah(amount: BigDecimal): Long = amount.setScale(0, RoundingMode.HALF_UP).longValueExact()
    fun isValidKg(quantity: BigDecimal): Boolean = quantity.signum() > 0 &&
        quantity <= BigDecimal("99999999.99") && quantity.stripTrailingZeros().scale() <= 2
    fun calculateItemSubtotal(quantityKg: BigDecimal, pricePerKg: Long): Long = CheckoutMath.item(quantityKg, pricePerKg)
}
