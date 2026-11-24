package id.panenku.app.ui

import id.panenku.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanenkuViewModelTest {
    @Test fun rejectedDecisionDoesNotChangeStateAndValidDecisionDoes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = PanenkuViewModel(DemoPanenkuRepository())
            runCurrent()
            assertFalse(vm.state.value.loading)
            vm.decide("VER-019", "Ditolak", " ", true)
            assertNull(vm.state.value.decisions["VER-019"])
            vm.decide("VER-019", "Ditolak", "Foto identitas terpotong", true)
            assertEquals("Ditolak · Foto identitas terpotong", vm.state.value.decisions["VER-019"])
            vm.viewModelScopeForTestCleanup()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun repositoryFailureSurfacesErrorAndRetryRecovers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var fail = true
            val repo = object : PanenkuRepository {
                override val data = MutableStateFlow(SampleData.data)
                override suspend fun load() { if (fail) error("Koneksi gagal") }
                override suspend fun saveProduct(product: Product) = Unit
                override suspend fun changeOrder(id: String, target: OrderStatus, reason: String) = Unit
                override suspend fun proposeSchedule(id: String, schedule: String, reason: String) = Unit
            }
            val vm = PanenkuViewModel(repo)
            runCurrent()
            assertEquals("Koneksi gagal", vm.state.value.error)
            fail = false
            vm.reload()
            runCurrent()
            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.loading)
            vm.viewModelScopeForTestCleanup()
        } finally { Dispatchers.resetMain() }
    }
}
private fun PanenkuViewModel.viewModelScopeForTestCleanup() {
    androidx.lifecycle.ViewModelStore().apply { put("test", this@viewModelScopeForTestCleanup); clear() }
}
