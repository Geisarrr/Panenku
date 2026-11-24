# UI admin Panenku — Figma v1

Implementasi Jetpack Compose berdasarkan sembilan SVG pada PAPB PANENKU (3).zip. Berkas AdminScreens.kt dari pengguna diubah menjadi komponen yang menerima snapshot server dan callback. Alur aktif tetap melalui ConnectedPanenkuApp dan ConnectedViewModel, mempertahankan pembeli, petani, onboarding, dan fungsi administratif tim.

## Cakupan desain

| Layar | Node Figma | Integrasi |
|---|---|---|
| Login admin | 46:2079 | POST auth/sessions; peran admin diperiksa dari respons server; akun salah peran dikeluarkan |
| Ringkasan | 46:1986 | Total antrean verifikasi/sengketa, status pengguna, audit; tidak menghitung hanya jumlah baris pratinjau |
| Verifikasi petani | 46:1901 | GET antrean dan detail verifikasi; dokumen terlindungi; PUT verify setelah pemeriksaan dan konfirmasi |
| Pengguna & status akun | 46:1816 | GET users/detail; PUT suspend untuk suspensi/pemulihan, alasan dan konfirmasi eksplisit |
| Kelola komoditas | 46:1740 | GET/POST/PUT admin/commodities; nama dan isActive sesuai kontrak API |
| Pengaturan | 46:1372 | GET/PUT admin/settings; alasan perubahan limit dan optimistic locking nilai lama |
| Sengketa refund | 46:1428 | GET disputes/detail/refund; bukti pembayaran/refund terlindungi; PUT decide |
| Moderasi produk | 46:1677 | Tampilan disiapkan; endpoint laporan dan keputusan moderasi tidak tersedia pada backend yang diperiksa |
| Pantau seluruh pesanan | 46:1548 | Tampilan disiapkan; endpoint daftar/detail semua pesanan admin tidak tersedia pada backend yang diperiksa |

Desain dan aset dikonfirmasi pada file Figma OFj847BJFoT1TACWnmw5Ta. Endpoint admin juga diperiksa pada branch integrasi-api GitHub, commit ec4e0ab8083c00bc0c339ea9160d86c73f0ca02b. Sumber backend tidak diubah. Jangan menganggap kedua layanan yang belum tersedia sudah dapat dipakai hanya karena tampilan ada.

## Perilaku yang dipertahankan

- Masuk aplikasi: splash → pilih peran → Akses administrator → login admin → ringkasan. Tidak ada pendaftaran admin publik atau akun admin otomatis. Email/kata sandi contoh Figma tidak diisi sebagai kredensial.
- Token tetap dikelola PanenkuApi; dokumen, bukti pembayaran, dan refund diambil melalui sesi API, bukan URL publik. Kata sandi tidak disimpan ke rememberSaveable/disk.
- Semua perubahan harus berasal dari respons server. Tidak ada data contoh Admin Maya, angka 8/3, keputusan demo, atau audit buatan dalam aplikasi produksi. Data contoh hanya pada pengujian/pratinjau.
- Verifikasi membutuhkan tiga pemeriksaan dan konfirmasi. Snapshot pengajuan dimuat kembali sebelum PUT untuk mendeteksi pergantian applicationId/submittedAt/status. Backend tetap memvalidasi keputusan dan hak akses.
- Keputusan yang sudah diakui server tidak dilaporkan sebagai gagal hanya karena pemuatan ulang daftar gagal. Tampilan meminta memuat ulang daftar dalam kondisi tersebut; tidak ada pengulangan POST otomatis.
- Suspensi akun terpisah dari keputusan refund. Admin tidak dapat menangguhkan dirinya sendiri/admin lain. Pemulihan membutuhkan catatan pemeriksaan pada UI ini. Daftar tetap memakai filter semula saat diperbarui.
- Penyimpanan pengaturan memakai expectedTransactionLimit dan expectedCsWhatsapp, termasuk JSON null jika nomor belum diatur. Perubahan limit memerlukan alasan. Nomor CS memakai +kode negara, 8–15 digit, mengikuti validasi server.
- Server komoditas hanya menerima name/isActive. Deskripsi dan alasan komoditas tidak dipura-pura tersimpan; isian tersebut dibuat read-only dengan keterangan yang jelas. Satuan tetap kilogram/Rp per kg. Nonaktif bukan hapus permanen.
- Sengketa hanya menyediakan status terbuka dan keputusan akhir, belum ada simpan catatan sementara/status ditinjau. Tombol keputusan memakai setujui_refund/tutup_sengketa. Menutup tiket tidak mengonfirmasi dana diterima pembeli dan tidak memindahkan uang. Filter ditutup menampilkan keputusan terkonfirmasi pada sesi ini; audit menyediakan riwayat administratif.
- Pilihan tiket dan catatan keputusan yang belum dikirim dipertahankan saat membuka bukti lalu kembali; fakta tiket/refund dimuat ulang dari server.
- Laporan pembayaran terlambat dan notifikasi tim tetap dapat dibuka melalui ringkasan. Audit dan seluruh bukti tersedia sebagai halaman tambahan.
- Pembeli dan penjual tetap memakai alur serta UI yang sudah ada. Paket menyertakan dependensi UI pembeli v3 agar tidak ada helper/aset yang hilang pada root aplikasi gabungan.

## Layanan yang masih dibutuhkan dari tim backend

Ini daftar kebutuhan integrasi, bukan deklarasi endpoint baru yang sudah tersedia:

1. Daftar dan detail laporan produk untuk admin, keputusan tidak melanggar/nonaktif beserta alasan dan audit.
2. Daftar/detail semua pesanan untuk admin, pencarian/filter/pagination, item dan total tersimpan, jadwal dan histori aktor.
3. Opsional: deskripsi/alasan penonaktifan serta jumlah produk per komoditas, simpan catatan peninjauan sengketa dan riwayat seluruh tiket ditutup.

Frontend tidak menggunakan endpoint buyer/petani untuk menyamar sebagai layanan admin. Pada dua layar yang belum memiliki layanan, tidak ada tindakan nonaktif palsu, transaksi buatan, atau permintaan ke endpoint tebakan.

## Berkas utama

- ui/admin/AdminScreens.kt: ringkasan, verifikasi, pengguna, pengaturan; adapter tanda tangan lama tetap ada agar graph lama dapat dikompilasi.
- ui/admin/AdminDesign.kt: komponen sesuai Figma dan login admin.
- ui/admin/AdminOperations.kt: komoditas, sengketa, rancangan moderasi/pantau pesanan.
- ui/admin/AdminData.kt: snapshot layar admin.
- ui/admin/AdminAppFlow.kt: navigasi aktif dan pemanggilan API saat masuk halaman.
- ui/auth/OpeningScreens.kt: pintu masuk login admin tanpa mengganti alur petani/pembeli.
- ui/connected/ConnectedApp.kt dan ConnectedViewModel.kt: penyambungan root dan API.
- res/drawable-nodpi/admin_*.png: ikon asli desain; font Inter dan komponen bersama memakai dependensi yang sudah ada.

## Pengujian

MockWebServer memeriksa kontrak, hak peran, pagination, verifikasi data berubah, alasan tindakan, acknowledgement sebelum refresh, optimistic locking, dan batas keputusan refund. Compose/Robolectric merender sembilan layar serta menguji navigasi dari login aktif. Pratinjau native memakai fixture pengujian, bukan data produksi. Dua tampilan tanpa layanan dapat dirender dengan fixture untuk menilai tata letak; tombol keputusannya tetap tidak aktif.

Server Windows/PostgreSQL pengguna tidak diakses dari lingkungan pengujian ini. Untuk emulator Android Studio, API lokal menggunakan http://10.0.2.2:8080/api/v1/ dan backend harus berjalan secara terpisah. Tidak perlu mengubah backend untuk memasang paket frontend ini.
