package com.panenku.backend

import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.modules.shared.service.CheckoutMath
import com.panenku.backend.modules.shared.service.SchedulingService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.*

class CheckoutRulesTest {
    @Test fun `UAT05 decimal quantities and fee produce 50000 rupiah`() {
        val items = listOf(CheckoutMath.item(BigDecimal("2.5"), 12000), CheckoutMath.item(BigDecimal("1.25"), 8000))
        assertEquals(50000L, CheckoutMath.total(items, 10000, 10000000))
        assertEquals(40000L, CheckoutMath.total(items, 0, 10000000))
    }
    @Test fun `half up rounding happens on each item`() {
        assertEquals(1L, CheckoutMath.item(BigDecimal("0.01"), 50))
        assertEquals(2L, CheckoutMath.total(listOf(CheckoutMath.item(BigDecimal("0.01"), 50), CheckoutMath.item(BigDecimal("0.01"), 50)), 0, 100))
    }
    @Test fun `UAT06 total at limit accepted and shipping over limit rejected`() {
        assertEquals(10000000L, CheckoutMath.total(listOf(9990000), 10000, 10000000))
        assertThrows(ApiException::class.java) { CheckoutMath.total(listOf(10000000), 1, 10000000) }
    }
    @Test fun `kg rejects zero negative excess precision and database overflow`() {
        listOf("0", "-0.01", "0.001", "100000000.00").forEach {
            assertThrows(ApiException::class.java) { CheckoutMath.kg(BigDecimal(it)) }
        }
        assertEquals(BigDecimal("2.50"), CheckoutMath.kg(BigDecimal("2.500")))
    }
    @Test fun `huge monetary values fail without long overflow`() {
        assertThrows(ApiException::class.java) { CheckoutMath.item(BigDecimal("2"), Long.MAX_VALUE) }
        assertThrows(ApiException::class.java) { CheckoutMath.total(listOf(Long.MAX_VALUE, 1), 0, Long.MAX_VALUE) }
    }
    private val scheduling = SchedulingService(Clock.fixed(Instant.parse("2026-11-09T18:00:00Z"), ZoneOffset.UTC))
    private val slots = listOf("08:00-10:00", "13:00-15:00")
    @Test fun `UAT03 latest ready date determines earliest schedule`() {
        val ready = listOf(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-12"))
        assertThrows(ApiException::class.java) { scheduling.validate(LocalDate.parse("2026-11-11"), slots[0], ready, slots) }
        scheduling.validate(LocalDate.parse("2026-11-12"), slots[0], ready, slots)
    }
    @Test fun `schedule uses Jakarta date even before UTC midnight`() {
        assertThrows(ApiException::class.java) { scheduling.validate(LocalDate.parse("2026-11-09"), slots[0], listOf(LocalDate.parse("2026-11-08")), slots) }
        scheduling.validate(LocalDate.parse("2026-11-10"), slots[0], listOf(LocalDate.parse("2026-11-08")), slots)
    }
    @Test fun `schedule rejects unconfigured malformed reversed and elapsed slots`() {
        val date = LocalDate.parse("2026-11-10")
        listOf("11:00-12:00", "99:00-99:30", "15:00-13:00", "00:00-00:30").forEach {
            assertThrows(ApiException::class.java) { scheduling.validate(date, it, listOf(date), slots + listOf("99:00-99:30", "15:00-13:00", "00:00-00:30")) }
        }
    }
}
