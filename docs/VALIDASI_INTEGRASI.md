# Validasi integrasi Panenku

Tanggal: 20 November 2026 (WIB).

- Android assembleDebug: berhasil, APK dihasilkan.
- Android unit/Compose/API: 71 tes lulus, 0 gagal. Termasuk 11 tes transport API dan 1 smoke test layar login server.
- lintDebug: 0 error, 52 warning. Warning mencakup saran katalog versi, pembaruan dependensi, style Compose dan resource lama.
- Backend classes: berhasil pada JDK 17.
- Backend nyata dijalankan dengan PostgreSQL 16 terisolasi: 38 permintaan HTTP berhasil/guard yang diharapkan terverifikasi.
- Alur API nyata: registrasi/login, profil petani/rekening, produk, katalog, keranjang 0,25 kg, opsi jadwal, preview, checkout dan retry dengan orderId sama, usulan/keputusan jadwal, pemilihan pembayaran, unggah dan verifikasi bukti, penolakan fulfillment sebelum jadwal, gagal dipenuhi, refund, bukti transfer balik, konfirmasi refund, pelepasan reservasi stok, refresh/logout.

## Batas validasi

Uji API memakai fixture lokal untuk status verifikasi petani dan metadata foto produk. Tidak memverifikasi layanan MinIO/foto produk atau seluruh UI emulator terhadap backend. Fulfillment selesai/penerimaan normal belum diuji langsung; jalur refund yang diuji berasal dari pesanan terbayar gagal dipenuhi. Upload bukti memakai gambar contoh, bukan transfer bank nyata.

Docker Compose lengkap belum berhasil dijalankan pada lingkungan pengerjaan. Image MinIO siap pakai tidak tersedia; stack memakai Dockerfile source MinIO yang sudah ada di repository. Percobaan build Dockerfile source terhenti karena kapasitas disk lingkungan, sehingga build MinIO dan startup seluruh stack perlu diverifikasi pada laptop tim. Backend untuk uji API dijalankan melalui Gradle dengan PostgreSQL Docker. Script PowerShell belum dijalankan pada Windows dalam sesi ini.

GitHub menolak penulisan blob dengan HTTP 403 Resource not accessible by integration. Tidak ada perubahan berhasil dipush ke GitHub. Gunakan bundle yang disertakan untuk mengirim commit lewat akun Git lokal.
