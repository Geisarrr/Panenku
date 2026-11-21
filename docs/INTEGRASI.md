# Integrasi frontend–backend Panenku

## Kode yang berjalan

`MainActivity` → `ConnectedPanenkuApp` → `ConnectedViewModel` → `PanenkuApi` → controller Spring Boot → PostgreSQL.

Package aplikasi tetap `id.panenku.app` agar sesuai kode slicing dan resource `R`; applicationId tetap `com.example.android` agar tidak mengganti identitas instalasi proyek pengguna. Lokasi file Kotlin di `com/example/android` tidak menentukan deklarasi package Kotlin.

Tema, Page, Panel, Field, MediaPicker, tombol dan aset slicing dipakai kembali. Layar aktif untuk data server ada di `ui/connected/Connected*.kt`. Ini adaptasi layar slicing untuk kontrak API, bukan penggantian isi SampleData. Navigasi lama `navigation/PanenkuApp.kt`, `Demo*Repository` dan layar simulasi tetap sebagai referensi; tidak dipanggil MainActivity. Sejumlah layar lama yang belum diadaptasi tidak tersedia di navigasi aktif.

`data/remote/PanenkuApi.kt` menangani Retrofit, envelope respons, token, refresh sekali pada 401, multipart, dan error server. DTO data kompleks masih memakai JsonObject mengikuti nama field backend; belum seluruhnya dipetakan ke DTO Kotlin bertipe khusus. Tidak ada fallback otomatis ke data dummy jika server gagal.

## Cakupan aktif

| Fungsi | Perilaku nyata |
|---|---|
| Auth | Register pembeli/petani, login `/auth/sessions`, role server, refresh token, logout. |
| F1 | Katalog/halaman berikutnya, tambah/edit produk petani, unggah foto, harga dan stok dari server. |
| F2 | Keranjang server dengan jumlah desimal, edit/hapus item, reservasi atomik oleh checkout backend. |
| F3 | Opsi jadwal berdasarkan item dan area server, pilihan tanggal/slot, usulan petani dan keputusan pembeli dengan versi jadwal. Backend saat ini membatasi usulan sebelum bukti pembayaran; UI mengikuti batas ini. |
| F4 | Preview total server dan checkout memakai quote token serta Idempotency-Key. Angka total UI tidak dikirim sebagai otoritas transaksi. |
| F5 | Daftar/detail/riwayat pesanan, pembatalan pembeli, petani menerima/menolak, pemenuhan, pembeli konfirmasi penerimaan. Status diperbarui dengan tombol, belum realtime/push. |
| F6 | Pilihan rekening/QRIS snapshot, gambar QRIS privat, multipart bukti, petani melihat gambar lalu menerima/menolak bukti. Tidak memindahkan uang atau memverifikasi bank otomatis. |
| F7 | Pengajuan multipart, daftar/status, tanggapan petani, transfer balik beserta bukti, konfirmasi dana pembeli dan eskalasi admin. Nominal ditentukan server. |
| Pendukung | Profil pembeli/toko, rekening bank toko, verifikasi KTP dummy pengembangan, antrean verifikasi admin, daftar refund admin. |

Belum diadaptasi ke navigasi aktif: dashboard/statistik admin lengkap, putusan sengketa admin, ulasan, chat/notifikasi, login Google, pemulihan kata sandi, pengelolaan beberapa alamat, edit/hapus rekening/QRIS toko, serta layar laporan transfer terlambat. Endpoint backend untuk sebagian ini sudah ada. Mengunggah KTP pada stack pengembangan wajib memakai dokumen contoh, bukan identitas asli.

## Mengapa konfigurasi Android diubah

File yang di-copy ke repository belum lengkap: PanenkuViewModel, fungsi RatingStars dan gambar yang diacu belum ada. File tersebut dipulihkan dari hasil slicing sebelumnya. Namespace diperbaiki. Konfigurasi Android diseragamkan dengan kombinasi yang diuji pada hasil slicing: AGP 8.9.1, Gradle 8.13, Kotlin 2.1.20, JDK 17 dan SDK 35. Backend mempertahankan plugin/toolchain sendiri. Versi katalog lama masih ada sebagai referensi tetapi build aktif memakai dependensi eksplisit; jangan mengubahnya lalu mengira dependensi aktif ikut berubah.

## Setup teman setelah pull

Lihat langkah Windows di README. Script tidak mengubah database lain dan tidak menghapus volume. Run pertama mengunduh image/dependensi; tunggu log startup sebelum login. Port lokal 8080 dan 9000 harus kosong. Jika sudah memakai port tersebut, ubah binding port pada compose **dan** base URL Android/endpoint foto.

Stop stack tanpa menghapus data:

```powershell
docker compose --env-file backend/.env.integration -f backend/docker-compose.integration.yml down
```

Jangan menambahkan `-v` bila ingin mempertahankan data. Jangan mengirim `.env.integration`, `local.properties`, token, atau password ke Git.

Linux/macOS: salin `backend/.env.integration.example` ke `backend/.env.integration`, ganti tiga secret dengan nilai acak lokal yang berbeda (JWT minimal 32 byte), lalu dari root:

```bash
docker compose --env-file backend/.env.integration -f backend/docker-compose.integration.yml up --build -d
```

Storage bukti backend saat ini memerlukan POSIX. Docker Linux menyediakan itu pada Windows; menjalankan bootRun langsung di Windows bukan jalur unggahan yang direkomendasikan. Stack memakai volume privat untuk bukti dan bucket terpisah untuk foto produk. Tidak ada seed/reset otomatis; database baru mulai kosong.

## Menyiapkan toko untuk uji pertama

1. Daftar petani dan pembeli lewat aplikasi.
2. Petani → Akun → simpan lokasi pengambilan, slot seperti `09:00-12:00`, dan ongkir/area jika layanan antar diaktifkan.
3. Tambahkan rekening toko lewat Akun.
4. Petani mengunggah dokumen **dummy** lewat Akun. Admin yang sudah disiapkan backend masuk lalu memeriksa pengajuan dan menyetujui.
5. Admin menambahkan komoditas melalui API `POST /api/v1/admin/commodities` dengan token admin, sesuai README backend. Form pengelolaan komoditas admin belum ada di navigasi aktif.
6. Petani membuat produk sebagai `draft`, membuka edit, mengunggah foto, kemudian kembali ke daftar dan membuka edit lagi agar data foto terbaru terambil. Lengkapi harga/stok/tanggal dan ubah menjadi `aktif`.
7. Pembeli login → Toko → masukkan misalnya `0,25` kg → Keranjang → jadwal → preview total → buat pesanan.
8. Petani login dan menerima pesanan. Pembeli memuat ulang status, memilih pembayaran, mentransfer secara manual dan mengunggah bukti. Petani memeriksa bukti serta rekening sebenarnya sebelum menyetujui.
9. Petani memperbarui pemenuhan hingga menunggu penerimaan. Pembeli mengonfirmasi barang diterima. Uji refund mengikuti batas waktu dan aturan server.

Penyiapan akun admin mengikuti prosedur backend/tim; aplikasi tidak menyediakan cara menaikkan role sendiri. Ada seeder demo lokal terpisah `backend/scripts/seed_demo.py` dengan prasyarat dan pembatasannya sendiri. Seeder bukan bagian startup otomatis stack ini.

## Alamat API tiap laptop

Bawaan Android Emulator tidak bergantung IP laptop: `http://10.0.2.2:8080/api/v1/`. Tidak perlu memasukkan alamat laptop teman ke Git.

Override lokal dalam `android-app/local.properties` (pertahankan `sdk.dir` milik Android Studio):

```properties
PANENKU_API_URL=http://10.0.2.2:8080/api/v1/
```

Atau Gradle `-PPANENKU_API_URL=https://server-kalian/api/v1/`. Akhiran `/` wajib. Sync/rebuild setelah mengubah base URL.

Untuk HP fisik, gunakan IP LAN laptop. Binding compose sekarang loopback: perlu mengubah port api/foto agar dapat diakses LAN, menyesuaikan firewall, `PANENKU_API_URL`, dan `MINIO_PUBLIC_ENDPOINT`. Tetap batasi akses sesuai lingkungan pengembangan. Untuk produksi gunakan HTTPS dan konfigurasi server yang sesuai. Izin HTTP Android hanya di manifest debug.

## Sesi dan pengulangan permintaan

Token hanya disimpan di memori ViewModel/API, tidak ditulis ke file/log. Rotasi refresh otomatis diserialisasi, percobaan permintaan setelah refresh maksimal satu kali. Setelah proses aplikasi mati, pengguna login ulang; pemulihan sesi persisten dengan Android Keystore belum diimplementasikan.

Idempotency-Key disimpan sebagai pasangan hash payload dan key, dipisahkan per pengguna. Kesalahan jaringan mempertahankan key. Keberhasilan terkonfirmasi melepas key supaya tindakan baru yang disengaja dapat memakai key baru. Form tidak bisa mengirim dua aksi sekaligus. Quote checkout berada di memori; setelah aplikasi mati, periksa daftar pesanan server sebelum mencoba checkout baru bila hasil sebelumnya belum diketahui. Belum ada outbox persisten untuk memulihkan checkout yang ambigu setelah process death.

## Pengujian

Android dari folder `android-app`:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Test API memakai MockWebServer untuk login, header token, kg desimal, refresh/rotasi, konflik server, multipart berisi byte nyata, logout, dan key retry. Tes tersebut bukan pengganti pengujian dua perangkat terhadap backend sebenarnya. Hasil validasi sesi pengerjaan dicatat pada `docs/VALIDASI_INTEGRASI.md`.
