package id.panenku.app.ui.connected

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import id.panenku.app.data.remote.flag
import id.panenku.app.data.remote.text
import id.panenku.app.ui.components.*

@Composable
internal fun ConnectedNotifications(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    Page("Notifikasi", "Pesan pesanan, pembayaran, refund, dan akun", onBack = back) {
        item {
            Panel("Notifikasi transaksi") {
                Text(if (s.unreadNotificationCount > 0) "${s.unreadNotificationCount} notifikasi belum dibaca." else "Semua notifikasi sudah dibaca.")
                SecondaryButton("Muat ulang", vm::notifications)
                if (s.unreadNotificationCount > 0) PrimaryButton("Tandai semua sudah dibaca", vm::readAllNotifications)
            }
        }
        if (s.notifications.isEmpty() && !s.busy) item { EmptyState("Belum ada notifikasi", "Pesan transaksi baru akan muncul di sini.") }
        items(s.notifications, key = { it.text("id") }) { notice ->
            Panel(notice.text("title")) {
                notice.text("body").takeIf { it.isNotBlank() }?.let { Text(it) }
                Text(notice.text("createdAt"))
                StatusBadge(if (notice.flag("isRead")) "Sudah dibaca" else "Belum dibaca", if (notice.flag("isRead")) Tone.NEUTRAL else Tone.INFO)
                if (!notice.flag("isRead")) PrimaryButton("Tandai sudah dibaca", { vm.readNotification(notice.text("id")) })
            }
        }
    }
}
