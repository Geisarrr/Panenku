# Panenku Backend

REST API untuk aplikasi Android Panenku, tugas praktikum Pengembangan Aplikasi Perangkat Bergerak, Teknik Informatika Universitas Brawijaya 2026.

## Tim backend dan status

| Pengembang | Tanggung jawab sesuai pembagian backend | Implementasi tersedia |
|---|---|---|
| **Mochammad Fani Al Gibran (Gibran)** | F1 katalog/produk; F2 keranjang/reservasi stok; F3 jadwal; F4 kalkulasi/checkout | Katalog, komoditas, produk petani, keranjang, validasi area/jadwal, checkout atomik, snapshot dan idempotensi database. |
| **Mohammad Geisar Rampan (Geisar/Rampan)** | Auth/user; F5 status pesanan; F6 pembayaran; F7 refund/eskalasi; admin; infrastruktur/scheduler | Register/login, profil awal, fondasi infrastruktur, dan scheduler kedaluwarsa yang telah diselaraskan dalam integrasi bersama. Endpoint status lanjutan, pembayaran/refund, serta sebagian admin masih perlu dibuat. |

Integrasi menggunakan satu entity/repository pengguna, satu format respons, JWT subject UUID, dan JDK 17. Kontribusi awal Geisar terlihat pada commit [auth](https://github.com/Geisarrr/Panenku/commit/ac3d65f33a8ded3e298e511b9366694f9bef8b4d) dan [infrastruktur](https://github.com/Geisarrr/Panenku/commit/2d26973ba79ce5ddcc6cf991ba43f64f16bc2b15).

**Backend ini belum merupakan seluruh MVP.** Refresh/logout, pengelolaan profil lanjutan, verifikasi KTP, metode pembayaran, alur pesanan setelah checkout, bukti pembayaran, refund, ulasan, notifikasi, audit admin, dan integrasi MinIO belum lengkap. Helper storage mengembalikan error 503 sampai benar-benar tersedia; tidak menghasilkan path unggahan fiktif.

## Persyaratan

- JDK **17**; konfigurasi Kotlin juga menargetkan JVM 17.
- Gradle Wrapper **8.14.3** yang ada di repositori; tidak perlu memasang Gradle global.
- Docker Desktop dengan **Linux engine** aktif untuk PostgreSQL development dan tes Testcontainers.
- Stack aplikasi: Kotlin 2.1.21, Spring Boot 3.5.7, Spring Data JPA, Flyway, PostgreSQL 16.

Buka folder **backend** sebagai proyek Gradle di Android Studio. Jalankan perintah berikut dari folder ini. Pengaturan Gradle JDK pada IDE dan JAVA_HOME pada terminal bisa berbeda; cek keduanya.

## 1. Menjalankan tes

Tes memakai PostgreSQL sementara sendiri, tidak memakai database development dan tidak membutuhkan .env.

PowerShell:

```powershell
java -version
.\gradlew.bat --version
docker version
.\gradlew.bat test
.\gradlew.bat bootJar
```

Di macOS/Linux gunakan `./gradlew test bootJar`. `docker version` harus menampilkan bagian Client dan Server. Target akhir build adalah `BUILD SUCCESSFUL`. Pengujian mencakup role/kepemilikan, register/login/me, validasi nominal dan kg, keranjang, jadwal WIB, limit, snapshot, checkout bersamaan, retry idempoten, serta expiry/rollback stok.

## 2. Menjalankan server development

```powershell
Copy-Item .env.example .env
```

Edit .env: ganti DB_PASSWORD dan JWT_SECRET; secret JWT minimal 32 byte UTF-8. Jangan commit .env asli. Nilai contoh hanyalah placeholder.

```powershell
docker compose up -d
```

Compose membaca .env secara otomatis. **Spring Boot tidak otomatis membaca .env.** Masukkan variabel ke Run Configuration IDE atau export pada terminal. Contoh PowerShell (ganti placeholder dengan nilai yang sama seperti .env):

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/panenku"
$env:DB_USER = "panenku"
$env:DB_PASSWORD = "<nilai DB_PASSWORD milikmu>"
$env:JWT_SECRET = "<nilai JWT_SECRET milikmu, minimal 32 byte>"
.\gradlew.bat bootRun
```

Bash/Zsh dapat menggunakan `set -a`, `. ./.env`, lalu `set +a` sebelum `./gradlew bootRun`, dengan nilai .env yang sesuai format shell. Server default `http://localhost:8080`.

Database awal tidak memiliki akun demo, admin, komoditas, produk, atau persetujuan KTP otomatis. Katalog kosong pada instalasi baru adalah kondisi normal. Register petani membuat profil belum_diajukan; publikasi dan checkout memerlukan data verifikasi/lokasi/metode bayar yang nanti disediakan modul terkait. Tes integrasi sudah menyediakan fixture dummy untuk memeriksa alur tersebut.

## Kontrak API yang tersedia

Base URL: `/api/v1`. Semua request body memakai `Content-Type: application/json`. Semua endpoint privat memakai `Authorization: Bearer <token>` dan memeriksa akun/role/kepemilikan di database.

| Method | Path | Akses / hasil |
|---|---|---|
| POST | `/auth/register` | Publik; daftar pembeli/petani; HTTP 201 dengan UserDTO. Admin tidak dapat didaftar publik. |
| POST | `/auth/login` | Publik; HTTP 200; data berisi **string access token**. |
| GET | `/auth/me` | Login; ID, email, role dan status akun terbaru; tanpa password/hash. |
| GET | `/buyer/products` | Publik; katalog aktif petani terverifikasi dan tidak disuspend. |
| GET | `/buyer/products/{id}` | Publik; produk, toko, lokasi, area, slot dan ongkir; belum ada rating/ulasan. |
| GET | `/buyer/commodities` | Publik; komoditas aktif. |
| GET, POST | `/admin/commodities` | Admin; daftar atau tambah komoditas. |
| PUT | `/admin/commodities/{id}` | Admin; ubah nama/isActive. Nonaktifkan dengan isActive=false. |
| GET, POST | `/petani/products` | Petani aktif; daftar milik sendiri atau tambah produk. |
| GET, PUT, DELETE | `/petani/products/{id}` | Petani pemilik; DELETE menonaktifkan produk, tidak hard-delete. |
| GET | `/buyer/cart` | Pembeli; keranjang dan subtotal terbaru. |
| POST | `/buyer/cart/items` | Pembeli; tambah jumlah item. |
| PUT, DELETE | `/buyer/cart/items/{id}` | Pembeli pemilik; ganti jumlah atau hapus item. ID adalah ID item cart. |
| POST | `/buyer/checkout` | Pembeli; HTTP 201, snapshot order serta cadangan stok atomik. |

Rute lain pada [rancangan MVP](docs/RANCANGAN_MVP.md) masih merupakan target. Tidak ada endpoint refresh/logout, daftar/detail order, penerimaan, pembayaran/refund atau upload berkas yang siap dipakai saat ini.

### Respons dan autentikasi

Contoh daftar pembeli:

```json
{"email":"pembeli@example.test","password":"password-demo-123","role":"pembeli","fullName":"Pembeli Demo","contactPhone":"081234"}
```

Untuk petani gunakan role `petani` dan isi `storeName`. Password minimal 8 karakter dan maksimal 72 byte UTF-8 (batas BCrypt). Email dinormalisasi menjadi huruf kecil.

Login memakai body berikut:

```json
{"email":"pembeli@example.test","password":"password-demo-123"}
```

Respons login:

```json
{"success":true,"data":"<access-token>","message":"Login berhasil","timestamp":"2026-11-11T00:00:00Z"}
```

Gunakan token itu untuk GET `/auth/me` agar frontend mengetahui role/status. Token memakai HS256, issuer JWT_ISSUER (default panenku), subject UUID, serta exp wajib; default berlaku 24 jam lewat JWT_EXPIRATION (milidetik). Role dari perangkat/token tidak memberikan hak admin. Akun disuspend dapat membaca identitasnya dan nantinya menyelesaikan transaksi lama; operasi pembelian/publikasi baru diblokir.

Error memiliki `success=false`, `code`, `message`, dan `timestamp`. Status yang dipakai: 400 input salah, 401 token/login tidak valid, 403 akses ditolak, 404 tidak ditemukan, 405 method salah, 409 data/stok/harga berubah, 415 Content-Type salah, 500 kesalahan internal, serta 503 storage belum tersedia. JWT 401 tetap menyertakan WWW-Authenticate. Detail internal database tidak dikirim ke klien.

### Katalog dan produk

Parameter katalog: `search` (nama produk/komoditas/toko, maksimal 100 karakter), `commodityId` (UUID), `sort=price_asc|price_desc`, `page` mulai 0, `size` 1–100 (default 20). Daftar memakai PageDTO: items, page, size, totalElements. Stok habis tetap dapat ditampilkan dengan stockAvailable=0.

Request POST/PUT produk:

```json
{"commodityId":"<UUID komoditas aktif>","name":"Tomat Segar","pricePerKg":12000,"stockPhysical":10.50,"readyDate":"2026-11-15","publicationStatus":"draft","description":"Produk demo","photoUrl":"products/tomat.jpg"}
```

Status: draft/aktif/nonaktif. Draft mengizinkan stok 0; publikasi aktif membutuhkan stok positif, foto, KTP disetujui, akun aktif, lokasi pengambilan, dan metode bayar lengkap. Stok fisik tidak boleh diturunkan di bawah cadangan aktif. photoUrl saat ini berupa path; upload dan signed URL belum tersedia. Tanggal tersedia di masa depan diperbolehkan. Tanggal/jadwal pesanan yang sudah dibuat tidak ikut berubah saat produk diubah.

### Keranjang dan checkout

Tambah cart: `{"productId":"<UUID produk>","quantityKg":2.50}`. Tambah menjumlahkan quantity yang sudah ada; PUT memakai `{"quantityKg":1.25}` untuk mengganti jumlah. Kg minimal/langkah 0.01, maksimal dua desimal bermakna. Cart belum mencadangkan stok dan selalu membaca harga terbaru.

Satu cart/checkout untuk satu petani. Item petani lain ditolak 409 tanpa menghapus cart lama. Android perlu meminta konfirmasi sebelum menghapus/mengganti item.

Checkout wajib memakai header `Idempotency-Key` (1–128 huruf/angka/_/-). Contoh body:

```json
{
  "items":[{"productId":"<UUID produk>","quantityKg":2.50,"expectedPricePerKg":12000}],
  "fulfillmentMethod":"delivery",
  "scheduledDate":"2026-11-15",
  "scheduledTimeSlot":"08:00-10:00",
  "expectedDeliveryFee":10000,
  "deliveryAddress":"Alamat lengkap pembeli",
  "deliveryDistrict":"kepanjen",
  "buyerNotes":"Tolong kirim pagi"
}
```

Ganti UUID/tanggal/slot sesuai data nyata. Nominal rupiah harus integer; pecahan, null, dan field wajib yang hilang ditolak. Backend membaca harga dan ongkir dari database, lalu membandingkannya dengan nilai expected yang disetujui pembeli. Jika berubah, muat ulang ringkasan dan minta persetujuan sebelum request baru.

- Subtotal item = kg × Rp/kg, HALF_UP per item ke rupiah. Total = subtotal produk + ongkir. Limit settings default Rp10.000.000 termasuk ongkir; tepat limit diterima.
- Pickup memakai ongkir 0. Delivery membutuhkan layanan aktif, alamat dan identifier kecamatan yang cocok. service_area/time_slots profil sementara dipisahkan karakter `|`; kecamatan dibandingkan persis.
- Tanggal paling awal mengikuti ready_date paling akhir dari semua item dan tanggal hari ini di WIB. Slot wajib sesuai konfigurasi; slot yang sudah dimulai hari ini ditolak.
- Order awal menunggu_petani, pembayaran belum_dibayar, expiry 24 jam. Snapshot menyimpan nama/komoditas, harga, jumlah, ready_date, lokasi/alamat, jadwal, ongkir dan limit. Pemesanan/reservasi seluruh item dalam satu transaksi PostgreSQL dengan row locks.
- Simpan key dan payload untuk retry akibat timeout. Key sama + payload sama mengembalikan order yang sama; payload berbeda dengan key lama ditolak 409. Key unik per pembeli dan tahan restart server. Checkout menerima item eksplisit dan tidak menghapus cart otomatis.

### Scheduler dan database

Flyway V1–V3 sudah tersedia. Jangan mengedit migrasi yang sudah diterapkan di database tim; tambah versi berikutnya. JPA memakai ddl-auto=validate. Snapshot order/item dilindungi trigger; riwayat transaksi tidak dihapus dengan cascade.

Scheduler default setiap 5 menit, initial delay 5 menit, batch 100. Ia hanya mengekspirasi menunggu_petani/menunggu_pembayaran dengan pembayaran belum_dibayar/bukti_ditolak dan expires_at <= server UTC. Reservasi dilepas tepat sekali, stock_reserved dikurangi, stock_physical tetap; transisi dicatat sebagai aktor system. Pesanan sedang verifikasi atau sudah dibayar tidak otomatis kedaluwarsa. Satu order gagal tidak membatalkan batch lain. SCHEDULER_ENABLED=false mematikan job.

## Dokumen pengembangan

- [Hasil audit backend dan batas cakupan](docs/AUDIT_BACKEND.md).
- [Panduan implementasi katalog/checkout](docs/IMPLEMENTASI_GIBRAN.md).
- [Catatan integrasi Gibran–Geisar](docs/INTEGRASI_GEISAR.md).
- [Rancangan target MVP: ERD, class diagram, skema dan endpoint rencana](docs/RANCANGAN_MVP.md).

PRD dan laporan tugas merupakan dokumen eksternal yang dibagikan tim; belum disimpan di repositori. Tabel anggota serta pembagian fungsi laporan tetap tersimpan dalam dokumen rancangan. Perubahan branch backend perlu digabungkan lewat pull request agar muncul di main; audit ini tidak mengubah branch/frontend Iffah dan Ifka.
