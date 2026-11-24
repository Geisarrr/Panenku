# Perbaikan bug integrasi Panenku — 24 November 2026

Basis: `integrasi-api` pada `7baa29869f6154bb2e57a1b084de6f7c4ddce8bd`.
Branch perbaikan: `feature/gibran-integrasi-bugfix`.

## Temuan dan perbaikan

| ID | Reproduksi sebelum perbaikan | Hasil perbaikan |
| --- | --- | --- |
| BUG-01 | Jumlah keranjang 3 kg, stok turun menjadi 1 kg. Baris ditandai tidak tersedia sehingga editor jumlah terkunci dan ViewModel menolak penurunan ke 0,5 kg. | Editor tetap dapat dipakai; jumlah yang tidak melebihi stok diteruskan ke backend. Backend tetap menentukan apakah produk boleh diubah. |
| BUG-02 | Produk keranjang menjadi nonaktif/tidak tersedia dan belum terpilih. Checkbox dinonaktifkan, sehingga tidak dapat dipilih untuk dihapus. | Produk dapat dipilih dan dihapus. Checkout tetap dinonaktifkan jika pilihan memuat produk tidak tersedia. Pilih semua tetap memilih produk yang tersedia. |
| BUG-03 | State checkout dipulihkan dengan preview metode/alamat yang berbeda. UI memakai preview tanpa memeriksa kecocokannya; fixture pickup bahkan menampilkan ongkir delivery. | UI hanya menampilkan dan mengizinkan persetujuan preview yang cocok dengan metode, tanggal, slot, serta alamat/kecamatan delivery. Preview pickup dengan ongkir bukan nol ditolak. Nilai pembayaran tetap berasal dari backend; fixture pickup diperbaiki menjadi ongkir nol. |
| BUG-04 | Login pembeli, buka Akun pada shell baru. Halaman laporan transfer terlambat yang sudah tersedia tidak memiliki jalur masuk. | Menu laporan pembayaran terlambat membuka halaman dan endpoint yang sudah ada, lalu kembali ke Akun. |
| BUG-05 | Login petani, buka Profil. Shell baru belum menyediakan rute ke daftar notifikasi transaksi. | Profil membuka daftar notifikasi yang sudah ada, mendukung tandai dibaca, dan kembali ke Profil. |
| BUG-06 | Pesanan delivery terverifikasi berubah dari diproses ke diantar. Stok fisik belum berkurang sampai menunggu penerimaan, bertentangan dengan PRD. | Stok fisik berkurang dan reservasi dipenuhi ketika pengantaran dimulai. Tahap menunggu penerimaan tetap memproses hanya reservasi aktif sehingga tidak memotong stok dua kali dan kompatibel dengan pesanan delivery lama. |

## Verifikasi

- Reproduksi merah Android: pemilihan baris stok kurang gagal; preview metode berbeda masih menampilkan persetujuan.
- Reproduksi merah backend: stok fisik setelah pengantaran dimulai masih `10.00`, padahal seharusnya `9.25`.
- Backend: **263 test lulus**, 0 gagal; `bootJar` berhasil.
- Suite khusus `ProductPhotoIntegrationTest` (11 skenario MinIO) dikecualikan. Test foto lokal, publikasi, dan integrasi F1–F4 ikut dijalankan; implementasi storage tidak diubah.
- Android: **226 test lulus**, 0 gagal; `:app:assembleDebug` berhasil. Termasuk navigasi buyer ke bantuan pembayaran dan navigasi petani ke notifikasi, tandai satu dibaca, lalu kembali ke profil.

Contoh perintah di Windows PowerShell, dari direktori backend (Docker harus aktif):

```powershell
.\gradlew.bat test --tests '*CheckoutIntegrationTest' --tests '*OrderStatusRulesTest' --tests '*LocalProductPhotoIntegrationTest' bootJar
```

Dari direktori android-app:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Pemeriksaan manual setelah memasang perbaikan

1. Bangun ulang backend/container API karena terdapat perubahan aturan stok. Gunakan konfigurasi lokal yang sudah ada; database dan volume tidak perlu dihapus. MinIO tidak perlu diaktifkan.
2. Build/install APK terbaru. Pada emulator Android, pertahankan alamat API lokal sesuai konfigurasi proyek.
3. Pembeli: turunkan jumlah item yang stoknya berkurang; hapus item nonaktif; pastikan item tidak tersedia tidak dapat dicheckout.
4. Pembeli: preview pickup menunjukkan ongkir Rp0; delivery memakai total dari backend. Ubah metode, jadwal, atau alamat lalu periksa ulang preview dan persetujuannya.
5. Pembeli: Akun → Laporan pembayaran terlambat → kembali. Uji laporan lengkap hanya dengan pesanan demo yang memenuhi syarat.
6. Petani: Profil → Notifikasi transaksi → tandai dibaca → kembali.
7. Pesanan delivery demo: mulai pengantaran lalu tandai menunggu penerimaan. Pastikan stok fisik hanya berkurang sekali. Untuk pickup, stok tetap berkurang saat diserahkan.

## Batas hasil

Percobaan test yang membuka dialog jumlah terhenti dengan `AppNotIdleException` pada pengukuran layout Robolectric, baik pada graphics native maupun legacy. Belum dapat dipastikan apakah kondisi itu terjadi di perangkat. Test akhir memeriksa kontrol koreksi/hapus dan pengiriman perubahan jumlah melalui ViewModel; membuka dialog, mengetik jumlah, dan menyimpan tetap wajib diuji manual.

Pengujian otomatis dijalankan pada lingkungan cloud, bukan Android Studio atau HP pengguna. Test Compose menggunakan Robolectric; render dari fixture bukan screenshot UAT pada perangkat. Pengujian manual perangkat dan checklist UAT menyeluruh tetap perlu dilakukan. Hasil ini tidak merupakan jaminan bahwa seluruh aplikasi bebas dari setiap kemungkinan bug.

`main`, README backend, dan implementasi storage foto lokal tidak diubah. Tidak ada push atau merge otomatis; penerbitan mengikuti konfirmasi handoff tim.
