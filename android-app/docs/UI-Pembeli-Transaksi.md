# UI transaksi pembeli — delapan layar

Dasar kode: branch integrasi-api, commit 6e40a1e952f647007ba3c22c1e6b422e7b483f11.
Referensi: delapan SVG dalam PAPB PANENKU (7).zip. PAPB PANENKU (6).zip melebihi batas unduh 32 MiB pada sesi ini, sehingga layarnya belum diimplementasikan atau diverifikasi.

## Mengapa bukan hanya BuyerViewModel.kt?

BuyerViewModel.kt yang ditempel memakai BuyerRepository dan fungsi simulasi. Mengganti pesan pada ViewModel tersebut tidak mengubah tampilan. MainActivity aplikasi ini membuka ConnectedPanenkuApp. Implementasi ini memasang Compose ke alur tersebut dan menambah operasi pembeli pada ConnectedViewModel; jalur demo lama tetap tersedia bagi tes lama, tetapi tidak menjadi sumber data layar baru.

## Layar yang dipasang

1. Keranjang → Checkout: metode penerimaan, alamat, jadwal tersedia dari server, produk, total, persetujuan, buat pesanan.
2. Checkout → Ubah alamat: daftar dan formulir alamat.
3. Checkout berhasil → Pesanan berhasil dibuat.
4. Pesanan → Lacak pesanan: tujuan, jadwal efektif, riwayat, ringkasan, konfirmasi penerimaan.
5. Lacak pesanan → Lihat pembayaran: rekening/QRIS aktual dari snapshot pesanan, unggah bukti pertama.
6. Pembayaran dengan status bukti_ditolak: catatan penolakan, batas unggah baru, riwayat, unggah ulang.
7. Usulan jadwal terbuka → Perubahan jadwal: terima/tolak menggunakan versi proposal dari server.
8. Pesanan yang memenuhi syarat → Ajukan refund: alasan, foto, rekening, persetujuan, nominal penuh.

Navigasi bawah disembunyikan pada delapan halaman transaksi sesuai referensi. Halaman penjual, login/register, notifikasi, ulasan dan halaman pembeli lain tetap memakai implementasi yang sudah ada. Beranda/katalog/keranjang/daftar pesanan tidak diklaim sudah sesuai ZIP (6).

## Perilaku data dan batas API

- Data produk, jumlah, harga, ongkir, jadwal, status, deadline dan rekening dibaca dari API. Gambar produk menggunakan URL API; ilustrasi sukses dan ikon adalah aset asli Figma yang disimpan lokal. Nilai contoh Figma hanya berada di source test.
- Checkout memakai fulfillment-options dan checkout/preview. Perubahan metode/alamat/jadwal membatalkan persetujuan/quote lama. POST checkout menyertakan token dan harga dari quote; mekanisme idempotensi lama tetap dipakai saat koneksi gagal.
- Backend menyediakan satu deliveryAddress pada auth/profile, belum memiliki CRUD beberapa alamat. Alamat tambahan disimpan lokal per ID akun. Halaman alamat menjelaskan ini. Memilih/menyunting alamat utama juga memperbarui deliveryAddress tanpa mengganti nama/telepon pemilik akun dengan identitas penerima. Kecamatan harus dipilih dari area layanan toko; aplikasi tidak menebaknya dari teks alamat.
- Pembayaran dilakukan di aplikasi bank/pembayaran milik pengguna. Tujuan pembayaran dipilih melalui API sebelum unggah. QRIS ditampilkan hanya bila gambar aktual tersedia. Tidak ada rekening atau QRIS contoh yang dipakai untuk transaksi.
- Unggah hanya saat menunggu_pembayaran, sebelum expiresAt, tanpa proposal jadwal terbuka. Setelah bukti dikirim, status mengikuti server; pembeli tidak dapat menyetujui bukti atas nama petani.
- Konfirmasi penerimaan membutuhkan dialog persetujuan. Refund memerlukan pembayaran terverifikasi, batas 24 jam setelah penerimaan (atau gagal_dipenuhi), serta tidak memiliki pengajuan aktif/refund selesai. Server memvalidasi ulang seluruh aturan.
- Backend belum menyediakan endpoint laporan masalah pengiriman umum. Tombol masalah memberi petunjuk dan akses refund bila memenuhi syarat; tidak mengirim laporan ke endpoint pembayaran terlambat yang berbeda makna.
- Notifikasi/ulasan dari perubahan teman tetap dipertahankan. Folder backend dan konfigurasi Gradle tidak diubah.

## Cara memeriksa pada perangkat

Buka android-app di Android Studio, gunakan Gradle JDK 21 yang sudah dikonfigurasi, dan pastikan PANENKU_API_URL lokal menunjuk backend aktif. Masuk sebagai pembeli yang sah. Tambah produk satu toko, checkout, pilih alamat dan jadwal, periksa total lalu beri persetujuan. Setelah petani menerima, pilih tujuan pembayaran dan unggah foto nyata. Penolakan, proses pengantaran dan usulan jadwal harus berasal dari aksi akun petani; tidak ada tombol simulasi di pembeli. Uji penerimaan/refund hanya memakai data pengujian tim.

Pratinjau memakai fixture Figma di pengujian, bukan bukti transaksi backend nyata. Status bar dan gesture bar tetap dikelola Android, sehingga ikon/jam sistem mengikuti HP.

## Berkas utama

- ui/connected/ConnectedApp.kt: memasang rute transaksi pembeli.
- ui/connected/ConnectedViewModel.kt: pemuatan detail/proof/refund, alamat, QRIS, unggah dan penerimaan.
- ui/connected/buyer/BuyerTransactionDesign.kt: token dan komponen desain bersama.
- ui/connected/buyer/BuyerCheckoutScreens.kt: checkout, alamat dan konfirmasi pesanan.
- ui/connected/buyer/BuyerOrderScreens.kt: pembayaran, tracking dan keputusan jadwal.
- ui/connected/buyer/BuyerRefundScreen.kt: form refund.
- ui/connected/buyer/BuyerAddresses.kt: penyimpanan alamat per akun.
- res/drawable-nodpi/buyer_*.png: ikon dan ilustrasi asli.
- src/test/.../buyer: uji API, aturan dan pratinjau native.
