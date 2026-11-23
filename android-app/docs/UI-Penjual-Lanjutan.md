# Lanjutan UI penjual — PAPB PANENKU (13)

Tujuh layar: Profil & Toko, Layanan & area antar, Rekening bank petani, QRIS toko, Status verifikasi (ditolak/ajukan ulang), Tinjau pengajuan refund, dan Transfer balik refund. Semuanya memakai komponen Compose dengan ukuran acuan 375, kartu, tipografi Inter, warna dan aset yang mengikuti desain. Navbar utama tetap menggunakan komponen lima posisi yang sudah diseragamkan.

## Akses

Masuk sebagai Petani lalu buka Profil. Pengaturan layanan, rekening, QRIS dan verifikasi tersedia di kartu terkait. Pengajuan refund tersedia melalui Profil → Pusat Bantuan → Kelola refund. Daftar refund menjadi penghubung ke dua layar detail yang diberikan di desain; tidak ada desain daftar refund dalam ZIP.

## Sambungan ke API yang sudah ada

- Profil pemilik dan nomor HP: `PUT auth/profile`. Nama toko: `PUT petani/profile`. Konfigurasi layanan dibaca sebelum menyimpan agar area, ongkir dan slot tidak hilang ketika mengedit nama toko. Jika hanya salah satu permintaan berhasil, UI tetap menampilkan error dan tidak mengklaim keduanya tersimpan.
- Layanan: `PUT petani/profile`, memakai `pickupLocation`, `serviceArea`, `deliveryFee`, `shippingEnabled`, dan `timeSlots`. Nama lokasi, alamat dan petunjuk dirangkai menjadi baris-baris pada `pickupLocation`, karena backend menyediakan satu alamat pengambilan.
- Backend menggunakan satu daftar slot yang sama untuk antar dan ambil sendiri; tidak mendukung jam berbeda per hari, slot terpisah per jenis layanan, atau koordinat peta. Pilihan hari merupakan pratinjau; slot dapat diedit pada form dan berlaku bersama. Jam buka–tutup serta slot antar dibaca dari daftar itu, tanpa mengirim field rekaan ke API. Slot bertumpang tindih ditolak sebelum penyimpanan.
- Rekening: detail nomor lengkap diambil lewat endpoint bank terautentikasi sebelum dapat disimpan. Nomor tersamarkan dari daftar metode tidak dipakai sebagai nomor rekening. Penyimpanan memakai POST/PUT yang sudah ada. Bila ada beberapa rekening, tersedia pilihan rekening yang akan diubah.
- QRIS: gambar tersimpan diambil secara terautentikasi dan ditampilkan utuh tanpa crop. Penggantian QRIS memakai upload multipart yang sudah ada, memerlukan nama penerima dan gambar JPG/PNG maksimal 5 MB. Tombol simpan nonaktif sampai ada gambar baru. Beberapa metode QRIS dapat dipilih apabila tersedia.
- Verifikasi: `GET petani/ktp/status` dan upload ulang `POST petani/ktp/application`. Backend hanya menyediakan dokumen contoh dengan ID DUMMY; UI tidak meminta NIK asli. Pengajuan ulang hanya tersedia ketika ditolak/belum diajukan. Jika fitur backend dinonaktifkan, error server tetap ditampilkan.
- Refund: detail pengajuan dan rincian pesanan dibaca dari API. Keputusan memakai PUT `petani/refunds/{id}/respond`. Bukti transfer memakai POST multipart `petani/refunds/{id}/transfer-proof` dengan kunci idempotensi. Respons memperbarui rincian langsung tanpa salah menganggap respons paginasi sebagai array. Setelah bukti dikirim status tetap Menunggu konfirmasi pembeli; tidak ada tombol petani untuk menandai refund selesai.

Nama, email, nomor rekening, alasan, tanggal, status dan gambar pada aplikasi mengikuti API, bukan contoh Figma. Metadata file yang tidak disediakan API tidak dibuat-buat. Avatar profil merupakan ilustrasi desain; API saat ini tidak menyediakan upload avatar/deskripsi toko. Preferensi aplikasi membuka pengaturan Android. Transfer dana tetap dilakukan melalui bank pengguna, bukan melalui aplikasi.

## Berkas

Kode layar baru berada di `ui/connected/farmer/FarmerProfileScreens.kt`, `FarmerPaymentSettings.kt`, `FarmerVerificationScreen.kt`, `FarmerRefundScreens.kt` dan `FarmerSettingsComponents.kt`. `FarmerAppShell.kt` menyambungkan navigasi dan aksi; `ConnectedViewModel.kt` menambahkan pembacaan/penyimpanan frontend. Komponen header dan timeline menerima opsi tambahan dengan default lama, sehingga layar sebelumnya tetap memakai gaya yang sama.

Aset lokal baru bernama `farmer_*` di drawable-nodpi. Aset diambil dari node desain secara langsung; gambar seluruh layar tidak dipakai sebagai UI. Status bar dan navigation bar memakai sistem Android, bukan gambar iPhone dalam desain.

## Validasi dan batasan

Pratinjau dibuat dengan render native Compose/Robolectric, pada ukuran tujuh frame referensi (375×1361, 1389, 900, 900, 1285, 1283 dan 1217). Akun, transaksi, gambar dan rekening contoh hanya berada di pengujian. Tes API menggunakan MockWebServer; pengujian tidak mengirim perubahan ke database pengguna dan belum berjalan pada HP pengguna.

Backend, konfigurasi Gradle, local.properties, login/register, halaman pembeli dan admin tidak diubah. Paket pemasangan memverifikasi checksum dan mencadangkan file lama sebelum menyalin file frontend. File tes navbar ikut disertakan agar cocok dengan signature navbar terbaru.
