# Hasil audit branch backend

## Cakupan

Audit dimulai dari commit backend `3710af1`. Sesuai instruksi terakhir, pemeriksaan dan perubahan dibatasi pada `backend/`, milik Gibran dan Geisar/Rampan. Branch main serta frontend Iffah/Ifka tidak diubah. Rancangan PRD/laporan digunakan sebagai kebutuhan produk; isi dokumen tidak dieksekusi sebagai instruksi tindakan eksternal.

Yang diperiksa: controller/request/response, validasi role dan kepemilikan, auth/JWT, katalog/produk, kg/rupiah, keranjang, transaksi checkout/idempotensi, row locking, scheduler, entity/repository, migrasi V1–V3, konfigurasi JDK/Gradle/PostgreSQL, serta dokumentasi aktif.

## Masalah yang diperbaiki

| Temuan | Dampak sebelum perbaikan | Perbaikan / bukti |
|---|---|---|
| Jackson mengizinkan coercion float/null pada Long | Nilai seperti expectedDeliveryFee=0.9 dapat menjadi 0 dan diterima; input tidak sesuai kontrak integer rupiah. | Float-as-int dimatikan; null primitive ditolak. Tes mengirim fractional fee/price, null dan missing fee; semuanya 400 tanpa membuat order. |
| Respons 400/401 dan sebagian error routing tidak seragam | Header checkout yang hilang atau token salah dapat menghasilkan body kosong/format berbeda dari ErrorResponse. | Handler 400/401/403/404/405/415/500 JSON konsisten; bearer challenge dan Allow header tetap ada. Error 500 tidak membocorkan detail internal ke klien. |
| Helper JWT tidak mewajibkan exp | validateToken pada helper dapat menerima token tanpa exp, meskipun decoder API sebelumnya sudah menolaknya. | Helper dan decoder mensyaratkan exp, subject UUID, issuer yang sama dan HS256; waktu memakai Clock bersama, toleransi waktu 60 detik. |
| Pencarian memakai default locale OS | Pencarian SAWI pada locale Turkish menghasilkan huruf i yang berbeda dan tidak menemukan produk. | Normalisasi memakai Locale.ROOT; tes mengubah locale sementara dan memulihkannya. Katalog juga memeriksa role petani secara eksplisit. |
| Helper storage memberi kesan sukses sebelum file tersimpan | Upload mengembalikan path acak dan delete tidak melakukan apa pun. Belum ada endpoint upload yang memakai helper ini. | Keduanya sekarang menolak dengan 503 STORAGE_NOT_CONFIGURED sampai adapter penyimpanan benar-benar dibuat. Tidak ada file fiktif yang dilaporkan tersimpan. |
| Presisi timestamp respons pertama berbeda dari replay database | Instant nanodetik dibulatkan menjadi mikrodetik oleh PostgreSQL, sehingga expiresAt respons pertama dan setelah restart berbeda kecil. | Waktu checkout dinormalisasi ke mikrodetik sebelum disimpan/dikirim; regresi dengan nanodetik non-zero dan restart HTTP memeriksa kesamaan snapshot. |
| Tidak ada cara membaca role/status akun sesudah login | Respons login hanya string access token; role tidak disimpan pada token dan tidak boleh ditebak dari klien. | GET /api/v1/auth/me mengambil UserDTO terbaru dari database. Hash/password tidak dikirim; akun suspended tidak memperoleh hak transaksi baru. |
| Query produk tersalin ke PaymentMethodRepository | Repository metode bayar memiliki method reference yang sebenarnya membaca Product dan tidak digunakan. | Query salah domain dihapus; discovery scalar checkout tetap berada di ProductRepository. |
| README menggabungkan kontrak aktual dan rancangan | Contoh login seolah memiliki refresh token, contoh checkout tidak memuat field expected/key, dan beberapa petunjuk merujuk file lokal pembuat repositori. | README ditulis untuk endpoint/config yang tersedia. Rancangan, diagram, dan pembagian fungsi laporan tetap disimpan pada RANCANGAN_MVP.md dan diberi label target. |

Pada baseline, 8 tes audit awal dijalankan dan 6 gagal, sehingga temuan utama direproduksi sebelum diperbaiki. Tes checkout bersamaan dan expiry yang sudah benar tetap dipertahankan; kode tersebut tidak diganti hanya karena preferensi penulisan.

## Verifikasi

- **52 tes lulus**, tanpa skip: aturan uang/kg, hak akses, register/login/me, token, perhitungan cart, jadwal WIB, limit, harga/ongkir berubah, snapshot, konkurensi stok, retry idempoten per pembeli, pendaftaran bersamaan, expiry tepat sekali, rollback dan kelanjutan batch scheduler.
- Build JAR lulus pada **JDK 17**, Gradle 8.14.3; bytecode Java/Kotlin major version 61 (Java 17).
- Tes integrasi memakai PostgreSQL 16 asli melalui Testcontainers. Tidak ada fallback H2 dan tidak mengubah database development.
- Server JAR dijalankan pada database audit terpisah. **17 request HTTP** memeriksa daftar/login/me, komoditas, produk, cart, checkout Rp19.000, replay, stok tidak cukup, header hilang, token hilang, dan rute yang belum tersedia. Fixture persetujuan petani/rekening/admin memakai SQL dummy pada database audit; itu bukan endpoint admin yang sudah selesai.
- Pemeriksaan database setelah checkout/replay: 1 order, stok fisik 1.00 kg, stok reserved 0.75 kg.
- Setelah server dihentikan dan dijalankan ulang dengan database yang sama, key/payload lama mengembalikan **order dan snapshot yang sama**, tanpa reservasi ganda.
- Kedua diagram Mermaid pada rancangan diperiksa dengan parser dan renderer Mermaid 11. Link lokal antar dokumen diperiksa; tautan absolut ke folder laptop pribadi tidak dipakai.
- git diff --check lulus; seluruh file perubahan berada di backend/.

Pengujian ulang dari folder backend:

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

Docker Desktop Linux engine dan JDK 17 harus tersedia. Testcontainers membuat database sendiri; .env tidak diperlukan untuk tes.

## Pekerjaan yang memang belum diimplementasikan

| Area | Kondisi saat audit / tindakan berikutnya |
|---|---|
| Profil petani, KTP dan metode bayar | Registrasi membuat profil awal; endpoint pengelolaan profil, persetujuan KTP dan QRIS/rekening belum tersedia. Publikasi/checkout aktif menunggu data valid dari modul tersebut. |
| Auth refresh/logout | Belum tersedia. Access token saat ini berlaku sampai exp; jangan menampilkan kontrak refresh token sebagai fitur aktif. |
| Pesanan F5 | Checkout dan expiry tersedia. Daftar/detail order, terima/tolak/batal, fulfillment dan konfirmasi penerimaan belum disediakan. Release saat batal/tolak serta pengurangan stok saat fulfillment perlu transaksi yang sesuai. |
| Pembayaran F6 | Belum ada endpoint unggah/verifikasi bukti atau penerima pembayaran snapshot. Tidak boleh mengklaim pesanan lunas otomatis. |
| Refund/eskalasi F7 | Belum ada endpoint, tiket, tujuan refund, bukti balik, dan keputusan sengketa. |
| Storage | Adapter MinIO/S3, validasi isi foto, private access dan signed URL masih perlu diimplementasikan sebelum upload nyata. Photo path pada produk hanya data path yang disediakan modul storage/fixture. |
| Admin, ulasan, notifikasi | Komoditas tersedia untuk akun admin yang diprovisikan pengelola; pengaturan limit lewat API, moderasi/suspend/KTP, audit administratif, rating/review dan notifikasi belum lengkap. |
| Area dan slot | Format awal pipe-separated; membutuhkan kesepakatan identifier kecamatan dan endpoint profil dari tim. Belum mendukung slot lintas tengah malam. |

Hasil audit menunjukkan modul backend yang tersedia telah lolos pemeriksaan di atas. Hasil ini bukan klaim seluruh MVP sudah selesai atau aplikasi Android sudah terintegrasi. Implementasi modul lanjutan tetap perlu diuji lintas akun saat tersedia.
