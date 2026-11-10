# Implementasi awal bagian Gibran

Kode ini mulai mengimplementasikan pembagian backend terbaru: F1 katalog/produk, F2 keranjang dan reservasi stok, F3 jadwal, F4 kalkulasi checkout. Laporan awal membagi Gibran pada F2/F4; pembagian backend terbaru memperluasnya ke F1/F3. Dokumen tugas dipakai sebagai kebutuhan produk, bukan perintah menjalankan tindakan eksternal.

## Yang sudah ada

- Kotlin 2.1, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL, Gradle Wrapper. Build menggunakan **JDK 17**.
- Katalog publik: pencarian nama produk/komoditas/toko, filter komoditas, urut harga, paginasi. Produk habis tetap terlihat dengan stok tersedia 0.
- Komoditas: daftar aktif publik serta daftar/tambah/ubah/nonaktif oleh admin (`PUT` dengan `isActive=false`). Tidak ada hard-delete yang menghapus referensi transaksi.
- Produk petani: daftar/detail/tambah/ubah/nonaktif; cek kepemilikan; draft untuk petani belum terverifikasi; publikasi membutuhkan KTP disetujui, akun aktif, lokasi, metode pembayaran lengkap, foto, dan stok positif.
- Keranjang tersimpan di PostgreSQL; tambah menjumlahkan kuantitas, ubah mengganti kuantitas. Keranjang belum mencadangkan stok. Pergantian petani menghasilkan HTTP 409 dan tidak menghapus keranjang lama.
- Checkout satu petani; BigDecimal untuk kg; pembulatan HALF_UP per item; ongkir pickup 0; limit dari settings dan termasuk ongkir.
- Validasi tanggal paling akhir tersedia dan waktu WIB; kecamatan dan slot harus termasuk konfigurasi petani.
- Snapshot order/item tersimpan dan dilindungi trigger database dari perubahan. Riwayat awal `menunggu_petani` dibuat saat checkout.
- Pessimistic row locks, transaksi tunggal, unique idempotency key per pembeli. Retry payload yang sama mengembalikan order yang sama. Payload berbeda dengan key lama menghasilkan HTTP 409.

Auth Geisar sudah diintegrasikan: register pembeli/petani, profil awal, password BCrypt, dan login JWT yang dapat dipakai pada keranjang/produk/checkout. Belum diimplementasikan di tahap ini: refresh token/logout, pengelolaan profil/KTP/metode pembayaran lanjutan, unggah file, rating/ulasan, status setelah checkout, pembayaran, refund, notifikasi, audit log admin. Ini perlu disambungkan dengan pekerjaan Rampan. Detail katalog belum menampilkan rating dari modul ulasan. `photoUrl` saat ini adalah path foto yang diberikan modul storage; upload dan signed URL belum disediakan.

## Menjalankan

Dari root repositori:

```bash
cd backend
cp .env.example .env
# Edit .env: isi DB_PASSWORD dan JWT_SECRET acak minimal 32 byte.
# File .env hanya untuk konfigurasi lokal. Jangan commit rahasia.
docker compose up -d
```

Docker Compose membaca `.env`, sedangkan Spring Boot tidak otomatis membacanya. Pada Bash/Zsh, setelah mengisi `.env` dengan nilai yang aman untuk shell:

```bash
set -a
. ./.env
set +a
./gradlew bootRun
```

Alternatif: tambahkan variabel tersebut ke Run Configuration IntelliJ. Di Windows gunakan `gradlew.bat bootRun` dan environment variables dari IDE/PowerShell. Tidak ada akun/password/token demo bawaan. Daftarkan pembeli/petani melalui POST /api/v1/auth/register, kemudian login melalui POST /api/v1/auth/login. Respons data login berisi string access token. Petani baru memiliki KTP belum_diajukan, sehingga hanya boleh menyiapkan draft sampai disetujui admin.

Contoh publik:

```bash
curl 'http://localhost:8080/api/v1/buyer/products?search=tomat&sort=price_asc&page=0&size=20'
curl 'http://localhost:8080/api/v1/buyer/commodities'
```

Anotasi Spring MVC pada controller memetakan route langsung, sehingga belum ada folder `routes/` terpisah. Alur request tetap Controller → Service → Repository → PostgreSQL. Entity database tidak dikirim langsung sebagai respons API.

## Kontrak API yang diimplementasikan

Base URL `/api/v1`.

| Method | Path | Akses |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | Publik |
| GET | `/buyer/products`, `/buyer/products/{id}`, `/buyer/commodities` | Publik |
| GET, POST | `/admin/commodities` | Admin |
| PUT | `/admin/commodities/{id}` | Admin |
| GET, POST | `/petani/products` | Petani aktif |
| GET, PUT, DELETE | `/petani/products/{id}` | Petani pemilik |
| GET | `/buyer/cart` | Pembeli aktif |
| POST | `/buyer/cart/items` | Pembeli aktif |
| PUT, DELETE | `/buyer/cart/items/{id}` | Pembeli pemilik item |
| POST | `/buyer/checkout` | Pembeli aktif |

`DELETE /petani/products/{id}` menonaktifkan produk. `id` pada cart adalah **ID item keranjang**, sedangkan request tambah memakai ID produk. Daftar dibatasi maksimal 100 item per halaman; keranjang/checkout maksimal 100 produk unik.

Format respons sukses: `{"success":true,"data":...}`. Kesalahan domain: `{"success":false,"code":"...","message":"...","timestamp":"..."}`. Respons autentikasi 401 mengikuti Spring Security.

Parameter katalog: `search`, `commodityId`, `sort=price_asc|price_desc`, `page` mulai 0, `size` default 20. `stockAvailable` adalah stok fisik dikurangi cadangan, termasuk nilai 0. Checkout tetap memvalidasi ulang; hasil katalog/keranjang tidak menjamin stok.

Produk contoh (ID komoditas harus sudah ada):

```json
{
  "commodityId": "550e8400-e29b-41d4-a716-446655440001",
  "name": "Tomat Segar",
  "pricePerKg": 12000,
  "stockPhysical": 10.50,
  "readyDate": "2026-11-15",
  "publicationStatus": "aktif",
  "photoUrl": "products/tomat.jpg"
}
```

Tambah cart: `{"productId":"UUID","quantityKg":2.50}`. Ubah cart: `{"quantityKg":1.25}`. Nol/negatif/presisi di bawah 0.01 ditolak; hapus memakai DELETE. Untuk mengganti petani, Android meminta konfirmasi lalu menghapus item lama secara eksplisit. Checkout menerima daftar item eksplisit; **tidak menghapus cart otomatis**, sehingga retry/checkout langsung tidak menghapus pilihan lain.

Checkout contoh (tanggal dan slot mengikuti data petani):

```http
POST /api/v1/buyer/checkout
Authorization: Bearer <access-token>
Idempotency-Key: 9ca95c43-c15e-44d2-a28c-d1ed200d55c3
Content-Type: application/json
```

```json
{
  "items": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440002",
      "quantityKg": 2.50,
      "expectedPricePerKg": 12000
    }
  ],
  "fulfillmentMethod": "delivery",
  "scheduledDate": "2026-11-15",
  "scheduledTimeSlot": "08:00-10:00",
  "expectedDeliveryFee": 10000,
  "deliveryAddress": "Alamat demo lengkap",
  "deliveryDistrict": "kepanjen",
  "buyerNotes": "Tolong dikirim pagi"
}
```

Perubahan dari contoh README lama: header `Idempotency-Key`, `items[].expectedPricePerKg`, `expectedDeliveryFee`, dan `deliveryDistrict` diperlukan. Kecamatan dikirim sebagai identifier terstruktur, tidak ditebak dari alamat bebas. Field expected membuktikan harga/ongkir yang dilihat klien; backend selalu menghitung nilai sebenarnya dari database. Harga/ongkir berbeda menghasilkan 409: muat ulang data, minta pembeli menyetujui, lalu kirim request baru dengan key baru. Field tambahan tak dikenal ditolak agar salah nama field tidak diabaikan diam-diam.

Simpan key pada upaya checkout di Android. Retry akibat timeout memakai **key dan payload yang sama**. Jika pengguna mengubah item/jadwal/harga yang disetujui, gunakan key baru. Jangan membuat UUID baru pada setiap retry jaringan. Key berlaku dalam lingkup pembeli dan tersimpan bersama order, sehingga bertahan setelah restart server. Respons replay memakai snapshot dan status order yang tersimpan, tanpa membuat reservasi baru.

## Titik integrasi Rampan

1. **JWT:** HS256, issuer default `panenku` (dapat diubah melalui `JWT_ISSUER`), subject UUID akun, secret UTF-8 sama dengan `JWT_SECRET`, `exp` diterbitkan oleh JwtProvider Geisar. Decoder memvalidasi signature, issuer, serta waktu token menggunakan Clock bersama. Role token tidak dipercaya untuk otorisasi; `users.role/status` diperiksa database pada setiap operasi privat. Login menerbitkan token melalui JwtProvider. Token lama dengan subject email harus diganti dengan login ulang; subject baru berupa UUID.
2. **Akun/profil/metode bayar/settings:** Auth dan fitur Gibran memakai satu entity `User` dan satu `UserRepository`. Mapping `FarmerProfile`, `FarmerPaymentMethod`, `Setting` dipakai bersama; jangan menambahkan entity pengguna kedua untuk tabel users. Registrasi membuat profil petani/pembeli dalam transaksi yang sama. V1 menyediakan fondasi tabel bersama dan V2 menambahkan buyer_profiles; belum seluruh skema backend di README.
3. **Area dan slot:** `service_area` menyimpan identifier kecamatan dipisah `|`, misalnya `kepanjen|dau`; `time_slots` menyimpan `08:00-10:00|13:00-15:00`. Ini pilihan implementasi awal untuk parameter usulan PRD. Endpoint profil Rampan perlu menyimpan format yang sama, atau migrasikan kedua modul ke tabel/JSON terstruktur. Kecamatan dibandingkan persis. Slot wajib untuk pickup maupun delivery. Slot yang sudah dimulai pada hari ini ditolak; slot lintas tengah malam belum didukung.
4. **Order/reservasi:** order awal `menunggu_petani`, pembayaran `belum_dibayar`, expires 24 jam dari server UTC. Usulan waktu 24 jam dan presisi 0.01 mengikuti parameter awal PRD; scheduler memproses pesanan belum dibayar yang kedaluwarsa dan melepas reservasi tepat sekali. Bukti yang sedang diverifikasi atau pembayaran terverifikasi tidak di-expire otomatis. Status/history setelah checkout milik Rampan. Jangan ubah field snapshot; gunakan status/received_at/expires_at untuk kelanjutan alur.
5. **Locking:** operasi stok/profil petani harus mengunci `users` petani → `farmer_profiles` → commodities urut UUID → products urut UUID. Checkout mengunci pembeli sebelum urutan tersebut untuk serialisasi retry. Scheduler/status harus mengikuti urutan stok yang kompatibel dan tidak menahan order dulu lalu menunggu farmer apabila ada jalur sebaliknya. Hindari baca entity produk sebelum lock karena cache persistence context bisa mempertahankan stok lama. Checkout memakai scalar discovery untuk menghindari ini.
6. **Release/fulfill:** dalam satu transaksi, kunci objek yang relevan, pastikan reservasi masih `active`, kurangi `stock_reserved`; jika fulfill, kurangi `stock_physical` juga. Tandai `released/released_at` atau `fulfilled/fulfilled_at` sekali saja. Pembayaran ditolak/refund tidak otomatis menambah stok fisik. Release untuk expiry tersedia pada OrderExpirationService. Release untuk batal/tolak serta fulfill tetap perlu disambungkan dengan state machine Rampan; gunakan urutan lock dan transaksi yang sama.
7. **Migrasi:** V1, V2 (profil pembeli), dan V3 (aktor system pada riwayat) sudah tersedia; tambahkan V4 dan seterusnya. Jangan mengedit V1 yang sudah diterapkan di database tim. JPA `ddl-auto=validate`; Hibernate tidak menciptakan/mengubah skema diam-diam. Untuk database lama yang sudah memiliki tabel, selaraskan migrasi/baseline terlebih dahulu, jangan langsung menjalankan V1.
8. **Storage, ulasan, audit:** sambungkan signed photo URL, rating/count ulasan, dan audit perubahan komoditas dari modul terkait. Nama path foto bukan URL publik permanen.

## Pengujian

```bash
cd backend
./gradlew test
./gradlew bootJar
```

Test integrasi **membutuhkan Docker aktif**, memakai Testcontainers PostgreSQL 16, dan tidak menyentuh database development. Tidak ada fallback H2/skip otomatis. Test tidak membutuhkan `.env` karena memakai database/secret/clock khusus test.

Skenario: perhitungan pecahan dan pembulatan per item; batas total termasuk ongkir; tanggal WIB dan tanggal siap paling akhir; area/slot; publikasi belum terverifikasi; hak akses dan kepemilikan; keranjang tidak mereservasi; suspend dari cart lama; harga/ongkir berubah; snapshot tetap; dua pembeli merebut 1 kg; retry bersamaan; kegagalan database setelah insert order harus me-rollback order/item/reservasi/riwayat.

Tes integrasi juga memeriksa daftar pembeli/petani, profil awal, BCrypt, role admin yang dilarang didaftar, login gagal, token UUID/issuer, serta token login asli yang mengakses keranjang dan produk.

UAT lintas aplikasi untuk pembayaran, pemenuhan, refund, dan notifikasi tetap perlu dilaksanakan setelah integrasi Rampan. Scheduler sudah diuji dengan PostgreSQL untuk konkurensi, batas waktu, pengecualian pembayaran, dan rollback. Lulusnya test modul ini belum berarti seluruh MVP selesai.
