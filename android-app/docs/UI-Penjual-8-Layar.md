# UI penjual — delapan layar

Acuan: delapan SVG dalam `PAPB PANENKU (12).zip`. Implementasi Jetpack Compose pada lebar acuan 375; tata letak, warna, ikon, bentuk kartu, dan hierarki teks mengikuti desain. Konten yang lebih panjang dapat digulir. Inset status/navigation bar mengikuti Android perangkat, bukan gambar status bar iOS pada desain.

## Alur aktif

`ConnectedApp.kt` membuka `FarmerConnectedApp` setelah login akun berperan `petani`:

1. Beranda penjual: ringkasan pesanan, pengiriman hari ini, stok rendah, dan produk aktif.
2. Stok & Produk: pencarian, filter status, fisik/dipesan/tersedia, edit/tambah produk.
3. Form produk: unggah foto, informasi panen, syarat penerbitan, simpan draft, terbitkan.
4. Daftar pesanan: tab status dan akses rincian/bukti/penyiapan.
5. Detail menunggu konfirmasi: data pembeli, rincian transaksi tersimpan, terima/tolak dengan alasan.
6. Verifikasi pembayaran: metadata bukti, nominal tujuan, riwayat, buka gambar asli melalui API, keputusan pemeriksaan.
7. Penyiapan: transisi antar/ambil sampai menunggu konfirmasi pembeli, pelaporan gagal pemenuhan.
8. Usulan jadwal: tanggal, slot layanan, alasan, total tetap, dan status persetujuan.

Profil dan refund memakai halaman yang sudah ada; tidak ada desain baru untuk keduanya dalam ZIP. Login, registrasi, halaman pembeli, admin, konfigurasi Gradle, dan backend tidak diubah.

## Data dan API

Data/foto produk, nama toko, angka ringkasan, pesanan, dan bukti dibaca dari API, bukan data Figma yang dipasang ke aplikasi. Foto contoh dan akun ilustrasi hanya ada dalam `src/test`. Karena itu nama, jumlah, foto, tanggal, dan status pada aplikasi mengikuti database masing-masing.

`farmerWorkspace()` mengambil seluruh halaman produk/pesanan dan metadata toko/bukti secara berurutan agar hitungan dan filter tidak terbatas pada 20 item pertama. `farmerOrder()` memuat detail serta bukti/tujuan pembayaran untuk pesanan yang perlu diverifikasi.

Produk baru dibuat sebagai draft sebelum foto pertama diunggah. ID draft disimpan dalam state form agar percobaan unggah ulang tidak membuat produk kedua. Token dan kunci idempotensi tetap menggunakan implementasi API yang sudah ada.

Aturan backend tetap berlaku. Desain menampilkan tombol usulan jadwal pada penyiapan, tetapi API yang ada hanya menerima perubahan sebelum pembayaran/bukti pernah diajukan. Form menampilkan penjelasan dan menonaktifkan pengiriman bila tidak memenuhi syarat. Status Selesai tetap menunggu konfirmasi pembeli.

## Berkas utama

- `ui/connected/ConnectedApp.kt`: pengalihan peran petani ke alur baru.
- `ui/connected/ConnectedViewModel.kt`: pemuatan workspace/detail dan penyimpanan produk.
- `ui/connected/farmer/FarmerDesign.kt`: tema dan komponen tampilan.
- `ui/connected/farmer/FarmerStockScreens.kt`: beranda, stok, form produk.
- `ui/connected/farmer/FarmerOrderScreens.kt`: daftar dan detail pesanan, pembayaran, penyiapan, jadwal.
- `ui/connected/farmer/FarmerAppShell.kt`: navigasi dan sambungan aksi ke ViewModel.
- `res/drawable-nodpi/farmer_*`: aset ikon desain.
- Font Inter menggunakan `seller_inter.ttf` dan lisensi yang sudah ada.

## Validasi

Build debug, unit test, tes Compose/Robolectric, dan lint dijalankan. Render delapan layar memakai fixture khusus tes; pratinjau tersebut bukan tangkapan dari server produksi. Pengujian API menggunakan MockWebServer untuk pagination, detail bukti, unggah produk, dan retry draft. Belum menjalankan pengujian pada HP pengguna atau database pengguna.
