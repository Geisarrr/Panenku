package id.panenku.app.data.buyer

import id.panenku.app.data.OrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BuyerTransactionTest {
    private fun request(id: String = "checkout-1", address: String = "utama", mode: Fulfillment = Fulfillment.DELIVERY,
                        date: LocalDate = LocalDate.of(2026, 6, 24), slot: String = if (mode == Fulfillment.DELIVERY) deliverySlots.first() else pickupSlots.first()) = CheckoutRequest(id, address, mode, date, slot)
    private suspend fun cart(repo: DemoBuyerRepository) { repo.addToCart("sawi", 2);repo.addToCart("tomat", 1) }
    private suspend fun paid(repo: DemoBuyerRepository, mode: Fulfillment = Fulfillment.DELIVERY): String {
        cart(repo);val id = repo.createOrder(request(mode = mode));repo.simulateAcceptance(id)
        repo.submitPaymentProof(id, "content://test/payment");repo.simulatePaymentReview(id, true);return id
    }
    @Test fun checkoutReservesOnceAndDoubleTapReturnsSameOrder() = runTest {
        val repo = DemoBuyerRepository();cart(repo)
        val id = repo.createOrder(request());val state = repo.data.value
        assertEquals(22500L, state.orders.first().total)
        assertEquals(OrderStatus.PENDING, state.orders.first().order.status)
        assertEquals(22, state.products.first { it.id == "sawi" }.reserved)
        assertEquals(id, repo.createOrder(request()))
        assertEquals(state, repo.data.value)
        assertTrue(state.cart.isEmpty())
    }
    @Test fun deliveryRejectsOutOfAreaButPickupIsFree() = runTest {
        val repo = DemoBuyerRepository();cart(repo);val previous = repo.data.value
        assertTrue(runCatching { repo.createOrder(request(address = "cabang")) }.isFailure)
        assertEquals(previous, repo.data.value)
        val id = repo.createOrder(request(address = "cabang", mode = Fulfillment.PICKUP))
        val order = repo.data.value.orders.first { it.id == id }
        assertEquals(0L, order.order.deliveryFee);assertEquals(20000L, order.total)
    }
    @Test fun checkoutLimitIncludesDeliveryFee() = runTest {
        val repo = DemoBuyerRepository();cart(repo);val previous = repo.data.value
        assertTrue(runCatching { repo.createOrder(request(), limit = 20000) }.isFailure)
        assertEquals(previous, repo.data.value)
        repo.createOrder(request(), limit = 22500)
        assertEquals(22500L, repo.data.value.orders.first().total)
    }
    @Test fun readinessSundayAndServiceSlotsAreValidated() = runTest {
        val repo = DemoBuyerRepository();cart(repo)
        val invalid = listOf(request(date = LocalDate.of(2026,6,22)), request(date = LocalDate.of(2026,6,28)), request(slot = "00.00–03.00"))
        invalid.forEach { assertTrue(runCatching { repo.createOrder(it) }.isFailure) }
        assertEquals(2, repo.data.value.cart["sawi"])
        assertTrue(repo.data.value.checkoutRequests.isEmpty())
    }
    @Test fun checkoutRevalidatesStaleStockWithoutPartialMutation() = runTest {
        val repo = DemoBuyerRepository(initialData = BuyerSamples.initial.copy(cart = mapOf("sawi" to 31)))
        val previous = repo.data.value
        assertTrue(runCatching { repo.createOrder(request()) }.isFailure)
        assertEquals(previous, repo.data.value)
    }
    @Test fun editingAddressBookDoesNotChangeExistingOrderSnapshot() = runTest {
        val repo = DemoBuyerRepository();cart(repo);val id = repo.createOrder(request())
        val snapshot = repo.data.value.orders.first { it.id == id }
        repo.saveAddress(snapshot.address.copy(street = "Alamat baru di Lowokwaru nomor 99"), false)
        assertEquals(snapshot, repo.data.value.orders.first { it.id == id })
        assertNotEquals(snapshot.address.street, repo.data.value.addresses.first().street)
    }
    @Test fun savingAddressDoesNotSelectItUnlessRequested() = runTest {
        val repo = DemoBuyerRepository()
        val address = initialBuyerAddresses().first().copy(id = "baru", label = "Dapur baru")
        repo.saveAddress(address, false);assertEquals(0, repo.data.value.profile.selectedAddress)
        repo.selectAddress("baru");assertEquals(2, repo.data.value.profile.selectedAddress)
        assertTrue(runCatching { repo.saveAddress(address.copy(phone = ""), true) }.isFailure)
        assertTrue(runCatching { repo.selectAddress("tidak-ada") }.isFailure)
        assertEquals(3, repo.data.value.addresses.size)
    }
    @Test fun paymentRequiresAcceptanceAndDeadlineIsStrict() = runTest {
        val clock = TransactionClock(BuyerSamples.now);val repo = DemoBuyerRepository(clock);cart(repo)
        val id = repo.createOrder(request())
        assertTrue(runCatching { repo.submitPaymentProof(id,"content://test/payment") }.isFailure)
        repo.simulateAcceptance(id)
        assertEquals(clock.current.plus(Duration.ofHours(24)), repo.data.value.orders.first().paymentDeadline)
        clock.current = clock.current.plus(Duration.ofHours(24))
        assertTrue(runCatching { repo.submitPaymentProof(id,"content://test/payment") }.isFailure)
    }
    @Test fun rejectedProofHasNewDeadlineAndHistorySurvivesRetry() = runTest {
        val clock = TransactionClock(BuyerSamples.now);val repo = DemoBuyerRepository(clock)
        val id = "PES-2026-003";repo.submitPaymentProof(id,"content://test/first")
        clock.current = clock.current.plus(Duration.ofHours(2));repo.simulatePaymentReview(id,false)
        val rejected = repo.data.value.orders.first { it.id == id }
        assertEquals(clock.current.plus(Duration.ofHours(24)), rejected.paymentDeadline)
        assertEquals(ProofStatus.REJECTED,rejected.proofs.single().status)
        repo.submitPaymentProof(id,"content://test/retry")
        clock.current = clock.current.plus(Duration.ofHours(48))
        repo.simulatePaymentReview(id,true)
        val reviewed = repo.data.value.orders.first { it.id == id }
        assertEquals(2,reviewed.proofs.size);assertEquals(ProofStatus.REJECTED,reviewed.proofs.first().status)
        assertEquals(OrderStatus.PROCESSING,reviewed.order.status)
    }
    @Test fun receiptNeedsArrivalAndConsumesReservedStockOnce() = runTest {
        val repo=DemoBuyerRepository();val id=paid(repo)
        assertTrue(runCatching { repo.confirmReceipt(id) }.isFailure)
        repo.simulateArrival(id);repo.confirmReceipt(id)
        val after=repo.data.value;val order=after.orders.first { it.id==id }
        assertEquals(OrderStatus.COMPLETED,order.order.status);assertEquals(repo.now,order.receivedAt)
        assertEquals(repo.now.plus(Duration.ofHours(24)),refundDeadline(order))
        assertEquals(48,after.products.first { it.id=="sawi" }.physical)
        assertEquals(20,after.products.first { it.id=="sawi" }.reserved)
        assertTrue(runCatching { repo.confirmReceipt(id) }.isFailure)
        assertEquals(after,repo.data.value)
    }
    @Test fun pickupUsesReadyForPickupAndStillNeedsReceiptConfirmation() = runTest {
        val repo=DemoBuyerRepository();val id=paid(repo,Fulfillment.PICKUP)
        repo.simulateArrival(id)
        val ready=repo.data.value.orders.first { it.id==id }
        assertEquals(TrackingStage.PICKUP_READY,ready.tracking);assertEquals(OrderStatus.PROCESSING,ready.order.status)
        repo.confirmReceipt(id);assertEquals(OrderStatus.COMPLETED,repo.data.value.orders.first { it.id==id }.order.status)
    }
    @Test fun acceptingScheduleChangesOnlySchedule() = runTest {
        val repo=DemoBuyerRepository();val old=repo.data.value.orders.first { it.id=="PES-2026-006" }
        repo.respondSchedule(old.id,true);val changed=repo.data.value.orders.first { it.id==old.id }
        assertEquals(scheduleLabel(LocalDate.of(2026,6,25),deliverySlots.first()),changed.order.schedule)
        assertEquals(old.total,changed.total);assertEquals(old.address,changed.address);assertEquals(old.order.lines,changed.order.lines)
        assertEquals(old.order.schedule,changed.proposal!!.originalSchedule)
        repo.respondSchedule(old.id,true);assertEquals(changed,repo.data.value.orders.first { it.id==old.id })
        assertTrue(runCatching { repo.respondSchedule(old.id,false) }.isFailure)
    }
    @Test fun rejectingScheduleKeepsOriginalAppointment() = runTest {
        val repo=DemoBuyerRepository();val old=repo.data.value.orders.first { it.id=="PES-2026-006" }
        repo.respondSchedule(old.id,false)
        val rejected=repo.data.value.orders.first { it.id==old.id }
        assertEquals(old.order.schedule,rejected.order.schedule);assertEquals(ProposalStatus.REJECTED,rejected.proposal!!.status)
    }
    @Test fun deliveryIssueDoesNotAutomaticallyCompleteOrRefundOrder() = runTest {
        val repo=DemoBuyerRepository();val id="PES-2026-001"
        repo.reportDeliveryIssue(id,"Sebagian sayuran terlihat rusak saat pemeriksaan.")
        val order=repo.data.value.orders.first { it.id==id }
        assertEquals(OrderStatus.DELIVERY,order.order.status);assertNull(order.receivedAt)
        assertFalse(isRefundEligible(order,repo.now));assertTrue(repo.data.value.refunds.isEmpty())
    }
    @Test fun emptyCartCannotCreateOrder() = runTest {
        val repo=DemoBuyerRepository();assertTrue(runCatching { repo.createOrder(request()) }.isFailure)
        assertTrue(repo.data.value.checkoutRequests.isEmpty())
    }
}
private class TransactionClock(var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}
