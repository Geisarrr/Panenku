package id.panenku.app.ui.buyer

import androidx.lifecycle.*
import id.panenku.app.data.buyer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BuyerViewModel(private val repository: BuyerRepository) : ViewModel() {
    val data = repository.data
    val now get() = repository.now
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private fun execute(message: String, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); onSuccess(); messages.send(message) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.send(e.message ?: "Perubahan belum dapat disimpan.") }
        }
    }
    fun saveProfile(profile: BuyerProfile) = execute("Profil demo disimpan.") { repository.saveProfile(profile) }
    fun saveReview(orderId: String, rating: Int, tags: Set<String>, comment: String) = execute("Ulasan demo berhasil dikirim.") { repository.saveReview(orderId, rating, tags, comment) }
    fun submitRefund(request: RefundRequest, onSuccess: () -> Unit) = execute("Pengajuan refund demo dicatat.", onSuccess) { repository.submitRefund(request) }
    fun respond(orderId: String, approved: Boolean) = execute("Balasan petani disimulasikan.") { repository.simulateRefundResponse(orderId, approved) }
    fun confirmReceived(orderId: String) = execute("Konfirmasi dana demo dicatat. Refund selesai.") { repository.confirmRefundReceived(orderId) }
    fun reportMissing(orderId: String) = execute("Laporan dana belum diterima dicatat. Refund tetap terbuka.") { repository.reportMissingRefund(orderId) }
    fun createTicket(orderId: String, onSuccess: (String) -> Unit) = execute("Tiket bantuan demo tercatat.") { onSuccess(repository.createTicket(orderId)) }
    fun addEvidence(ticketId: String, note: String, uri: String?, onSuccess: () -> Unit) = execute("Keterangan tiket demo ditambahkan.", onSuccess) { repository.addTicketEvidence(ticketId, note, uri) }
    fun addToCart(productId: String) = execute("Produk ditambahkan ke keranjang demo.") { repository.addToCart(productId, 1) }
    fun reorder(orderId: String, onSuccess: () -> Unit) = execute("Produk pesanan lama ditambahkan ke keranjang dengan harga saat ini.", onSuccess) { repository.reorder(orderId) }
    fun setCartQuantity(productId: String, quantity: Int) = execute("Keranjang demo diperbarui.") { repository.setCartQuantity(productId, quantity) }
    fun saveAddress(address: BuyerAddress, makeDefault: Boolean, done: () -> Unit) = execute("Alamat demo disimpan.", done) { repository.saveAddress(address, makeDefault) }
    fun selectAddress(id: String) = execute("Alamat utama diperbarui.") { repository.selectAddress(id) }
    fun checkout(request: CheckoutRequest, limit: Long, done: (String) -> Unit) = execute("Pesanan demo berhasil dibuat.") { done(repository.createOrder(request, limit)) }
    fun acceptOrder(id: String) = execute("Penerimaan petani disimulasikan.") { repository.simulateAcceptance(id) }
    fun submitProof(id: String, uri: String) = execute("Bukti demo terkirim untuk pemeriksaan petani.") { repository.submitPaymentProof(id, uri) }
    fun reviewPayment(id: String, approved: Boolean) = execute("Pemeriksaan pembayaran disimulasikan.") { repository.simulatePaymentReview(id, approved) }
    fun arrive(id: String) = execute("Pesanan tiba / siap diambil disimulasikan.") { repository.simulateArrival(id) }
    fun receive(id: String) = execute("Penerimaan pesanan dicatat. Batas pengajuan refund dimulai.") { repository.confirmReceipt(id) }
    fun reportDeliveryIssue(id: String, text: String, done: () -> Unit) = execute("Laporan pengiriman demo dicatat.", done) { repository.reportDeliveryIssue(id, text) }
    fun respondSchedule(id: String, accepted: Boolean) = execute("Tanggapan jadwal disimpan.") { repository.respondSchedule(id, accepted) }
    fun message(text: String) { viewModelScope.launch { messages.send(text) } }
    companion object {
        fun factory(repository: BuyerRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BuyerViewModel(repository) as T
        }
    }
}