package id.panenku.app.data.buyer

import id.panenku.app.data.OrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DemoBuyerRepositoryTest {
    private fun request(orderId: String = BuyerSamples.completed.id) = RefundRequest(orderId,
        "Kondisi sayuran tidak sesuai", "Sayur layu dan tomat rusak saat diterima.", "content://test/photo", "BCA", "000000000000", "Rina Catering", true)
    @Test fun reviewRequiresCompletedOrderAndValidRating() = runTest {
        val repo = DemoBuyerRepository()
        assertTrue(runCatching { repo.saveReview("PES-2026-004", 5, emptySet(), "Segar") }.isFailure)
        assertTrue(runCatching { repo.saveReview(BuyerSamples.completed.id, 0, emptySet(), "") }.isFailure)
        assertTrue(repo.data.value.reviews.isEmpty())
        repo.saveReview(BuyerSamples.completed.id, 5, setOf("Pengiriman"), "Segar dan tepat waktu")
        assertEquals(5, repo.data.value.reviews.single().rating)
        assertTrue(runCatching { repo.saveReview(BuyerSamples.completed.id, 4, emptySet(), "Ulang") }.isFailure)
    }
    @Test fun refundWindowEndsExactlyAt24Hours() = runTest {
        val end = refundDeadline(BuyerSamples.completed)!!
        val before = DemoBuyerRepository(Clock.fixed(end.minusMillis(1), ZoneOffset.UTC))
        before.submitRefund(request())
        val at = DemoBuyerRepository(Clock.fixed(end, ZoneOffset.UTC))
        assertTrue(runCatching { at.submitRefund(request()) }.isFailure)
        assertTrue(at.data.value.refunds.isEmpty())
    }
    @Test fun refundRequiresReceiptOrPaidFulfillmentFailure() {
        val pending = BuyerSamples.completed.copy(order = BuyerSamples.completed.order.copy(status = OrderStatus.PENDING), receivedAt = null)
        assertFalse(isRefundEligible(pending, BuyerSamples.now))
        assertTrue(isRefundEligible(pending.copy(paidFulfillmentFailed = true), BuyerSamples.now))
    }
    @Test fun activeAndCompletedRefundsCannotBeSubmittedTwice() = runTest {
        val repo = DemoBuyerRepository();repo.submitRefund(request())
        assertTrue(runCatching { repo.submitRefund(request()) }.isFailure)
        repo.simulateRefundResponse(BuyerSamples.completed.id, true)
        repo.confirmRefundReceived(BuyerSamples.completed.id)
        assertTrue(runCatching { repo.submitRefund(request()) }.isFailure)
        assertEquals(1, repo.data.value.refunds.size)
    }
    @Test fun rejectedRefundCanBeResubmittedWithoutLosingHistory() = runTest {
        val repo = DemoBuyerRepository();repo.submitRefund(request());repo.simulateRefundResponse(BuyerSamples.completed.id, false)
        repo.submitRefund(request())
        assertEquals(2, repo.data.value.refunds.size)
        assertEquals(BuyerRefundStatus.REJECTED, repo.data.value.refunds.first().status)
        assertEquals(2, repo.data.value.refunds.map { it.id }.distinct().size)
    }
    @Test fun refundAmountComesFromFullPaymentSnapshot() = runTest {
        val old = BuyerSamples.historical
        val repo = DemoBuyerRepository(Clock.fixed(old.receivedAt!!.plusSeconds(1), ZoneOffset.UTC))
        repo.submitRefund(request(old.id))
        assertEquals(212500L, repo.data.value.refunds.single().amount)
    }
    @Test fun confirmationRequiresProofAndCannotBeRepeated() = runTest {
        val repo = DemoBuyerRepository();repo.submitRefund(request())
        assertTrue(runCatching { repo.confirmRefundReceived(BuyerSamples.completed.id) }.isFailure)
        repo.simulateRefundResponse(BuyerSamples.completed.id, true)
        repo.confirmRefundReceived(BuyerSamples.completed.id)
        assertTrue(runCatching { repo.confirmRefundReceived(BuyerSamples.completed.id) }.isFailure)
    }
    @Test fun missingFundsKeepsRefundOpenAndTicketCreationIsIdempotent() = runTest {
        val repo = DemoBuyerRepository();repo.submitRefund(request());repo.simulateRefundResponse(BuyerSamples.completed.id, true)
        assertTrue(runCatching { repo.createTicket(BuyerSamples.completed.id) }.isFailure)
        repo.reportMissingRefund(BuyerSamples.completed.id);repo.reportMissingRefund(BuyerSamples.completed.id)
        val ticketId = repo.createTicket(BuyerSamples.completed.id)
        assertEquals(ticketId, repo.createTicket(BuyerSamples.completed.id))
        assertEquals(1, repo.data.value.tickets.size)
        assertEquals(BuyerRefundStatus.AWAITING_FUNDS, repo.data.value.refunds.single().status)
        repo.confirmRefundReceived(BuyerSamples.completed.id)
        assertEquals(1, repo.data.value.tickets.size)
    }
    @Test fun noResponseEscalationRequires24Hours() = runTest {
        val clock = MutableClock(BuyerSamples.now)
        val repo = DemoBuyerRepository(clock);repo.submitRefund(request())
        assertTrue(runCatching { repo.createTicket(BuyerSamples.completed.id) }.isFailure)
        clock.current = clock.current.plus(Duration.ofHours(24))
        repo.createTicket(BuyerSamples.completed.id)
        assertEquals(TicketIssue.NO_RESPONSE, repo.data.value.tickets.single().issue)
    }
    @Test fun incompleteProofAccountOrConfirmationCannotCreateRefund() = runTest {
        val repo = DemoBuyerRepository()
        listOf(request().copy(photoUri = ""), request().copy(account = ""), request().copy(confirmed = false)).forEach { invalid ->
            assertTrue(runCatching { repo.submitRefund(invalid) }.isFailure)
        }
        assertTrue(repo.data.value.refunds.isEmpty())
    }
    @Test fun reorderIsAtomicAndRespectsAvailableStock() = runTest {
        val repo = DemoBuyerRepository();repo.addToCart("tomat", 6)
        assertTrue(runCatching { repo.reorder(BuyerSamples.completed.id) }.isFailure)
        assertEquals(mapOf("tomat" to 6), repo.data.value.cart)
        repo.setCartQuantity("tomat", 0);repo.reorder(BuyerSamples.completed.id)
        assertEquals(mapOf("sawi" to 20, "tomat" to 10), repo.data.value.cart)
        assertTrue(runCatching { repo.addToCart("cabai", 1) }.isFailure)
    }
    @Test fun csMessageExcludesAccountAddressAndUserEvidence() {
        val ticket = BuyerTicket("TKT-026", "RF-002", "PES-2026-002", TicketIssue.MISSING_FUNDS, BuyerSamples.now,
            notes = listOf("Rekening 123456789, alamat rahasia"), evidenceUris = listOf("content://private/ktp"))
        val text = safeCsMessage(ticket)
        assertTrue(text.contains("#TKT-026"));assertTrue(text.contains("#RF-002"))
        assertFalse(text.contains("123456789"));assertFalse(text.contains("alamat rahasia"));assertFalse(text.contains("content://"))
    }
    @Test fun profileValidationDoesNotDiscardSavedValues() = runTest {
        val repo = DemoBuyerRepository()
        val profile = BuyerProfile(name = "Dapur Rina", email = "rina@contoh.id", phone = "081234567890")
        repo.saveProfile(profile)
        assertTrue(runCatching { repo.saveProfile(profile.copy(email = "tidakvalid")) }.isFailure)
        assertEquals(profile, repo.data.value.profile)
    }
}
private class MutableClock(var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}
