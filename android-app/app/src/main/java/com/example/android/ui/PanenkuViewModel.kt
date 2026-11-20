package id.panenku.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.panenku.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PanenkuUiState(
    val loading: Boolean = true, val data: DashboardData = DashboardData(),
    val error: String? = null, val decisions: Map<String, String> = emptyMap(),
    val settings: Map<String, Map<String, String>> = emptyMap()
)
class PanenkuViewModel(private val repository: PanenkuRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(PanenkuUiState())
    val state = mutableState.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    init {
        viewModelScope.launch { repository.data.collect { data -> mutableState.update { it.copy(data = data) } } }
        reload()
    }
    fun reload() = viewModelScope.launch {
        mutableState.update { it.copy(loading = true, error = null) }
        try { repository.load(); mutableState.update { it.copy(loading = false) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableState.update { it.copy(loading = false, error = e.message ?: "Gagal memuat data.") } }
    }
    fun message(text: String) { viewModelScope.launch { messages.send(text) } }
    private fun execute(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); messages.send("Perubahan demo tersimpan selama aplikasi terbuka.") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { messages.send(e.message ?: "Perubahan gagal disimpan.") }
    } }
    fun saveProduct(product: Product) = execute { repository.saveProduct(product) }
    fun changeOrder(id: String, target: OrderStatus, reason: String = "") = execute { repository.changeOrder(id, target, reason) }
    fun proposeSchedule(id: String, schedule: String, reason: String) = execute { repository.proposeSchedule(id, schedule, reason) }
    fun saveSettings(key: String, values: Map<String, String>) {
        mutableState.update { it.copy(settings = it.settings + (key to values)) }
        message("Pengaturan demo disimpan.")
    }
    fun decide(key: String, decision: String, reason: String, requireReason: Boolean = false) {
        if (requireReason && reason.isBlank()) { message("Alasan atau catatan wajib diisi."); return }
        mutableState.update { it.copy(decisions = it.decisions + (key to listOf(decision, reason).filter(String::isNotBlank).joinToString(" · "))) }
        message("Keputusan demo dicatat.")
    }
    companion object {
        fun factory(repository: PanenkuRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PanenkuViewModel(repository) as T
        }
    }
}
