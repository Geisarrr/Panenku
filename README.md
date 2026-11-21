# Panenku

Android Jetpack Compose + Spring Boot REST API. Aplikasi aktif sekarang memakai server untuk login, katalog, keranjang, checkout, pesanan, pembayaran, dan refund. Data demo lama tetap tersedia sebagai referensi kode; tidak digunakan oleh MainActivity.

## Jalankan setelah pull (Windows + Android Emulator)

Prasyarat: Docker Desktop dalam mode Linux containers berjalan, Android Studio, Android SDK 35, JDK 17 untuk Gradle Android, dan internet untuk build pertama.

1. Dari root repository jalankan:

   ```powershell
   powershell -ExecutionPolicy Bypass -File backend/scripts/start-integration.ps1
   ```

   Script membuat rahasia lokal sekali dalam `backend/.env.integration`, lalu membangun dan menjalankan PostgreSQL, MinIO, dan backend. Database stack ini terpisah dari PostgreSQL yang sudah dipasang di laptop. Tidak ada reset atau penghapusan data otomatis.

2. Tunggu backend selesai startup:

   ```powershell
   docker compose --env-file backend/.env.integration -f backend/docker-compose.integration.yml logs -f api
   ```

   Tunggu `Started ApplicationKt`. Ctrl+C pada perintah **logs** hanya menutup pemantauan log; container tetap berjalan.

3. Android Studio → File → Open → **folder `android-app`**. Pilih Gradle JDK 17, izinkan unduhan SDK 35, tunggu Gradle Sync. Jalankan konfigurasi `app` pada Android Emulator.
4. Daftar akun pembeli atau petani dari aplikasi, lalu login dengan akun itu. Akun kosong tidak otomatis berisi pesanan atau produk contoh. Akun admin harus disiapkan melalui prosedur backend; tidak ada pendaftaran admin publik.
5. Untuk percobaan lengkap, siapkan petani, komoditas, produk, dan metode pembayaran sesuai [panduan integrasi](docs/INTEGRASI.md).

Alamat API bawaan emulator: `http://10.0.2.2:8080/api/v1/`. Backend laptop: `http://localhost:8080/api/v1/`.

## Pembaruan berikutnya

Pull branch integrasi yang dipakai tim, lalu jalankan ulang script di atas untuk membangun perubahan backend. Buka/sync Android bila file Gradle berubah. Rahasia, SDK path, database, dan gambar lokal tidak disimpan di Git.

[Struktur kode, cakupan F1–F7, pengaturan HP/server, pengujian dan batasan](docs/INTEGRASI.md).
