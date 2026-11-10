# Integrasi bagian Gibran dan Geisar

Integrasi awal menggabungkan dua riwayat: backend fork Gibran (3c4eca5) dan backend kelompok Geisar (ac3d65f). Lima konflik add/add diselesaikan sebagai satu konfigurasi, bukan dengan mengosongkan build.gradle.kts.

## Konfigurasi akhir

- Spring Boot 3.5.7, Kotlin 2.1.21, JDK 17, Gradle Wrapper 8.14.3. Semua plugin, repositories, JPA all-open/no-arg, dan dependensi test PostgreSQL tetap tersedia.
- Dependensi JJWT 0.12.3 dari auth Geisar ditambahkan. Decoder resource-server memakai HS256; penerbit token juga memakai HS256 secara eksplisit, issuer yang sama, dan subject UUID user.
- app.jwt.secret/issuer mengacu ke panenku.jwt.secret/issuer. JWT_SECRET tetap wajib minimal 32 byte UTF-8. JWT_EXPIRATION mengatur masa token dalam milidetik, default 24 jam. Tidak ada secret produksi bawaan.
- Satu entity User dan UserRepository untuk autentikasi dan pemeriksaan role/locking, satu ApiResponse bersama, dan satu ExceptionHandler. User timestamps memakai Instant agar konsisten dengan TIMESTAMPTZ PostgreSQL.
- Registrasi membuat user serta profil awal secara atomik. Buyer memerlukan fullName; farmer memerlukan storeName dan dimulai tanpa verifikasi KTP. Hanya role pembeli/petani yang dapat didaftarkan publik. Validasi input dijalankan dengan @Valid dan annotation target field Kotlin.
- Login mengembalikan access token pada data, mempertahankan bentuk respons auth Geisar. Logout/refresh token belum tersedia. Petani suspended tetap dapat login untuk penyelesaian transaksi lama; operasi baru pada modul Gibran tetap diblokir berdasarkan database.
- .idea/, local.properties, .kotlin/, .env, build/, dan .gradle/ dalam backend diabaikan Git. Jangan commit .env asli.

## Contoh auth

POST /api/v1/auth/register:

```json
{"email":"pembeli@example.test","password":"password-demo-123","role":"pembeli","fullName":"Pembeli Demo"}
```

POST /api/v1/auth/login:

```json
{"email":"pembeli@example.test","password":"password-demo-123"}
```

Gunakan string data dari respons login sebagai Authorization: Bearer <token> pada endpoint privat. Akun petani memakai role petani dan storeName. Tidak ada pendaftaran admin publik. KTP, metode pembayaran, dan profil usaha lanjutan perlu disambungkan dengan modul Geisar berikutnya.

## Pengujian

Docker aktif diperlukan untuk ./gradlew test. Testcontainers memakai PostgreSQL 16; tidak menyentuh database development. Pengujian mencakup alur signup → login → cart, signup petani → draft produk, penolakan publikasi petani belum terverifikasi, dan seluruh tes checkout/stok yang sudah tersedia.

## Mengambil hasil saat laptop sedang berkonflik

Simpan stash backup-lokal-sebelum-integrasi-Geisar. Sesudah commit integrasi tersedia pada fork, batalkan percobaan merge di laptop lalu ambil commit integrasi:

```bash
git merge --abort
git fetch gibran
git merge --ff-only gibran/backend
```

Commit integrasi sudah memuat kedua parent, sehingga tidak perlu mengulang resolusi konflik yang sama. Jika ada perubahan/commit baru di laptop atau remote, periksa status/riwayat terlebih dahulu. Jangan reset atau force-push.

Jangan langsung git stash pop: stash berisi pengosongan build.gradle.kts dan pengaturan IDE lokal. Backup tetap disimpan; ambil hanya perubahan yang masih diperlukan setelah integrasi diperiksa. Misalnya untuk mengembalikan isi oke.txt yang dicadangkan, periksa git stash list, lalu gunakan git restore --source=stash@{0} -- backend/oke.txt dari shell yang sesuai (PowerShell memerlukan tanda kutip pada stash@{0}).


## Pembaruan infrastruktur Geisar (2d26973)

Infrastruktur dan scheduler ditambahkan ke hasil integrasi awal (3da00f4), dengan riwayat commit kedua pekerjaan dipertahankan.

- AppConfig memakai constructor binding melalui EnableConfigurationProperties; JWT dan MinIO mengacu ke nilai konfigurasi yang sama dengan komponen auth/storage.
- Scheduler diaktifkan melalui EnableScheduling, dapat dimatikan dengan SCHEDULER_ENABLED=false. Default interval/initial delay 5 menit dan batch 100 order; EXPIRATION_DELAY_MS, EXPIRATION_INITIAL_DELAY_MS, EXPIRATION_BATCH_SIZE dapat disesuaikan.
- Kandidat hanya menunggu_petani/menunggu_pembayaran dengan pembayaran belum_dibayar/bukti_ditolak dan expires_at <= waktu server UTC. Tidak auto-expire verifikasi pembayaran, pesanan dibayar, terminal, atau yang belum jatuh tempo.
- Tiap order diproses dalam satu transaksi dengan lock petani → profil → produk urut UUID → order. Status diperiksa ulang setelah lock. Reservasi active menjadi released dan stock_reserved dikurangi tepat sekali; stock_physical tetap. Snapshot tidak diubah.
- Riwayat transisi kedaluwarsa dicatat dengan actor_role=system, actor_id=NULL. Migrasi V3 memperbolehkan aktor sistem tanpa menyamar sebagai pengguna. Identitas aktor manusia tetap wajib UUID user.
- Satu kegagalan di batch tidak membatalkan keberhasilan order lain. Kegagalan order tersebut me-rollback perubahan stok, reservasi, order, dan riwayat sekaligus, lalu dicatat pada log untuk ditangani/diulang.
- DecimalValidator diselaraskan dengan perhitungan kg/uang di checkout. IdempotencyStore in-memory dari infrastruktur belum dipakai untuk checkout: checkout tetap memakai key/hash unik di PostgreSQL agar tahan restart.
- FileStorage Geisar masih berupa placeholder TODO dan belum benar-benar mengunggah/menghapus file. Modul bukti pembayaran/KTP/refund tidak boleh menganggap path hasil helper ini sebagai unggahan yang sudah tersimpan. Integrasi MinIO dan signed URL masih pekerjaan berikutnya.

### Mengambil pembaruan setelah push ditolak

Jika laptop sudah berhasil mengambil integrasi awal dan tidak sedang merge:

```bash
git fetch gibran
git merge --ff-only gibran/backend
git fetch origin
```

Lihat git status -sb. Bila branch kelompok masih commit 2d26973, commit itu sudah menjadi ancestor hasil integrasi ini, sehingga git push origin backend dapat dilakukan tanpa force. Jika ada commit kelompok yang lebih baru, selaraskan dahulu; jangan menimpa pekerjaan rekan. Jangan menjalankan git merge --abort ketika tidak ada proses merge aktif.


## Target Java 17

Build menggunakan toolchain JDK 17 dan target bytecode Kotlin JVM 17, mengikuti target Java awal proyek Geisar. Spring Boot 3.5.7 dan dependensi yang digunakan mendukung Java 17. Tidak perlu mengubah instalasi Java milik anggota tim lain.

Untuk Windows, pilih instalasi JDK 17 di Gradle JDK Android Studio. Terminal memakai JAVA_HOME sendiri; arahkan ke folder JDK 17 yang benar lalu jalankan .\gradlew.bat --version. Gradle tetap versi 8.14.3. Setelah Docker Desktop menunjukkan Engine running, jalankan .\gradlew.bat test dari folder backend. Mengubah JAVA_HOME pada sesi PowerShell hanya berlaku untuk sesi tersebut.
