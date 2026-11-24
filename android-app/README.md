# Panenku Android

Aplikasi Android **Panenku** untuk mempertemukan petani, pembeli, dan admin dalam alur penjualan hasil panen: katalog, stok, keranjang, checkout, pembayaran manual, pemenuhan pesanan, refund, serta verifikasi identitas.

Modul Android ini merupakan klien untuk REST API pada folder [`../backend`](../backend).

## Teknologi

| Area | Teknologi |
| --- | --- |
| Bahasa | Kotlin |
| UI | Jetpack Compose dan Material 3 |
| Navigasi | Navigation Compose |
| State | ViewModel, StateFlow, dan Kotlin Coroutines |
| HTTP | Retrofit, OkHttp, dan Gson |
| Gambar | Coil Compose |
| Build | Gradle, Android Gradle Plugin, JDK 17 |
| Pengujian | JUnit, MockWebServer, Robolectric, Compose UI Test, dan Roborazzi |

## Peran dan fitur

| Peran | Fitur utama |
| --- | --- |
| Pembeli | Katalog, keranjang, checkout, pilihan ambil sendiri/pengiriman, pembayaran, pelacakan pesanan, refund, bantuan, notifikasi, dan ulasan. |
| Petani | Profil toko, verifikasi KTP, produk dan stok, foto produk, rekening/QRIS, verifikasi pembayaran, pemenuhan pesanan, serta respons refund. |
| Admin | Verifikasi KTP, pengelolaan pengguna, suspend/pulihkan akun, komoditas, limit transaksi, audit log, sengketa refund, dan laporan pembayaran terlambat. |

## Struktur ringkas

```text
android-app/
├── app/
│   ├── src/main/java/com/example/android/
│   │   ├── data/                 # Model, repository, Retrofit API
│   │   ├── features/             # Implementasi fitur F1–F7
│   │   ├── navigation/           # Navigasi aplikasi
│   │   ├── ui/auth/              # Login, registrasi, verifikasi
│   │   ├── ui/connected/         # Layar terhubung ke backend
│   │   ├── ui/connected/buyer/   # Alur pembeli
│   │   ├── ui/connected/farmer/  # Alur petani
│   │   ├── ui/admin/             # Alur admin
│   │   └── ui/theme/             # Tema dan komponen visual
│   └── src/test/                 # Unit dan UI test
├── docs/                         # Catatan integrasi Android
├── local.properties.example      # Contoh konfigurasi lokal
└── build.gradle.kts
```

## Prasyarat

- Android Studio terbaru dengan Android SDK API 35.
- JDK 17.
- Backend Panenku aktif pada port `8080`.
- Perangkat Android API 26+ atau Android Emulator.

## Menjalankan aplikasi

1. Jalankan backend lebih dahulu dari folder `backend/` sesuai dokumentasi backend.
2. Buka folder `android-app/` sebagai proyek Gradle di Android Studio.
3. Tentukan URL API pada `android-app/local.properties`:

   ```properties
   # Android Emulator
   PANENKU_API_URL=http://10.0.2.2:8080/api/v1/
   ```

   Untuk perangkat fisik melalui USB, jalankan dari terminal:

   ```bash
   adb reverse tcp:8080 tcp:8080
   ```

   lalu gunakan:

   ```properties
   PANENKU_API_URL=http://127.0.0.1:8080/api/v1/
   ```

4. Sinkronkan Gradle, pilih perangkat, lalu tekan **Run**.

Alternatif melalui terminal:

```bash
cd android-app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Kontrak integrasi

- Seluruh request API menggunakan basis URL `BuildConfig.API_BASE_URL`.
- Token JWT disimpan dan dikirim pada request yang membutuhkan autentikasi.
- Upload foto menggunakan `multipart/form-data`; foto produk, KTP, QRIS, bukti pembayaran, dan bukti refund dibatasi JPEG/PNG maksimal 5 MB.
- Respons error dari backend ditampilkan sebagai pesan yang dapat ditindaklanjuti pengguna.

## Foto dan storage lokal untuk testing

Dalam lingkungan development, backend dapat memakai **local private storage** sehingga UAT tidak memerlukan MinIO. Mekanisme ini dipakai untuk foto produk katalog, KTP dummy, QRIS, bukti pembayaran, serta bukti refund. Berkas tidak disajikan sebagai direktori publik; backend mengembalikan URL bertanda tangan dengan masa berlaku terbatas.

Jalankan backend lokal dari root proyek:

```bash
cd backend
bash scripts/start-local-mac.sh
```

Script tersebut mengaktifkan konfigurasi berikut:

```text
PRODUCT_PHOTOS_ENABLED=true
PRODUCT_PHOTOS_LOCAL_DIR=~/Library/Application Support/Panenku/product-photos
PAYMENT_STORAGE_LOCAL_DIR=~/Library/Application Support/Panenku/private
```

Kedua folder dibuat privat dengan izin `700`. Jangan gunakan folder publik atau commit hasil unggahan ke Git. Untuk perangkat fisik, `adb reverse tcp:8080 tcp:8080` tetap diperlukan agar URL foto bertanda tangan dapat diakses melalui `127.0.0.1`.

Gunakan aset aman pada `../backend/demo-assets/foto-dummy/` ketika melakukan UAT. Aset tersebut hanya contoh; memilih gambar dummy tidak otomatis membuat produk atau transaksi. Buat draft produk, unggah foto melalui aplikasi, lalu terbitkan produk agar foto tercatat pada backend.

## Pengujian

Jalankan test Android:

```bash
cd android-app
./gradlew testDebugUnitTest
```

Untuk UAT, uji alur pembeli → petani → pembeli selesai/ulasan, kemudian verifikasi KTP dan sengketa refund dari admin. Pastikan backend aktif dan `adb reverse` sudah terpasang jika memakai perangkat fisik.

## Catatan keamanan

- Jangan commit `local.properties`, token JWT, atau file `.env`.
- Foto sensitif diproses melalui URL privat bertanda tangan dari backend; aplikasi tidak menyimpan kredensial storage.
