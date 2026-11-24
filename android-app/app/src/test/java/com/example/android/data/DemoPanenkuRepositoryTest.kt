package id.panenku.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DemoPanenkuRepositoryTest {
    @Test fun rejectionReleasesReservedStockExactlyOnce() = runTest {
        val repo = DemoPanenkuRepository()
        repo.changeOrder(SampleData.order.id, OrderStatus.CANCELLED, "Stok tidak tersedia")
        val cabbage = repo.data.value.products.first { it.id == "sawi" }
        assertEquals(50, cabbage.physical)
        assertEquals(0, cabbage.reserved)
        assertEquals(50, cabbage.available)
        assertTrue(runCatching { repo.changeOrder(SampleData.order.id, OrderStatus.CANCELLED, "Ulang") }.isFailure)
        assertEquals(cabbage, repo.data.value.products.first { it.id == "sawi" })
    }
    @Test fun priceEditsDoNotChangeExistingOrderSnapshot() = runTest {
        val repo = DemoPanenkuRepository()
        val original = repo.data.value.products.first()
        repo.saveProduct(original.copy(price = 99999))
        assertEquals(202500L, repo.data.value.orders.first().total)
        assertEquals(5000L, repo.data.value.orders.first().lines.first().price)
    }
    @Test fun reservedStockCannotBeRemovedFromProduct() = runTest {
        val repo = DemoPanenkuRepository()
        val product = repo.data.value.products.first()
        assertTrue(runCatching { repo.saveProduct(product.copy(physical = 19)) }.isFailure)
        assertEquals(product, repo.data.value.products.first())
    }
    @Test fun invalidOrderTransitionsAndBlankReasonsAreRejected() = runTest {
        val repo = DemoPanenkuRepository()
        assertTrue(runCatching { repo.changeOrder(SampleData.order.id, OrderStatus.DELIVERY) }.isFailure)
        assertTrue(runCatching { repo.changeOrder(SampleData.order.id, OrderStatus.CANCELLED) }.isFailure)
        assertEquals(OrderStatus.PENDING, repo.data.value.orders.first().status)
    }
    @Test fun dispatchConsumesPhysicalAndReservedStockOnce() = runTest {
        val repo = DemoPanenkuRepository()
        val id = SampleData.order.id
        repo.changeOrder(id, OrderStatus.PAYMENT)
        repo.changeOrder(id, OrderStatus.REVIEW)
        repo.changeOrder(id, OrderStatus.PROCESSING)
        repo.changeOrder(id, OrderStatus.DELIVERY)
        val product = repo.data.value.products.first { it.id == "sawi" }
        assertEquals(30, product.physical)
        assertEquals(0, product.reserved)
        assertEquals(OrderStatus.DELIVERY, repo.data.value.orders.first().status)
        assertTrue(runCatching { repo.changeOrder(id, OrderStatus.COMPLETED) }.isFailure)
    }
    @Test fun proposedScheduleKeepsConfirmedSchedule() = runTest {
        val repo = DemoPanenkuRepository()
        val id = SampleData.order.id
        repo.changeOrder(id, OrderStatus.PAYMENT)
        repo.changeOrder(id, OrderStatus.REVIEW)
        repo.changeOrder(id, OrderStatus.PROCESSING)
        repo.proposeSchedule(id, "25 Juni 2026 · 09.00–12.00 WIB", "Hujan deras")
        assertEquals(SampleData.order.schedule, repo.data.value.orders.first().schedule)
        assertTrue(repo.data.value.orders.first().note.contains("menunggu persetujuan pembeli"))
    }
    @Test fun zeroPriceAndBlankNameAreRejected() = runTest {
        val repo = DemoPanenkuRepository()
        val product = repo.data.value.products.first()
        assertTrue(runCatching { repo.saveProduct(product.copy(price = 0)) }.isFailure)
        assertTrue(runCatching { repo.saveProduct(product.copy(name = " ")) }.isFailure)
    }
    @Test fun rupiahUsesIndonesianSeparators() {
        assertEquals("Rp202.500", rupiah(202500))
    }
}
