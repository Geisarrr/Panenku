# UI pembeli lanjutan v3

Paket kumulatif untuk kode gabungan pengguna setelah pull integrasi-api (dasar remote 6e40a1e). Sumber desain: Figma OFj847BJFoT1TACWnmw5Ta. Backend, konfigurasi Gradle, local.properties, .idea, onboarding, dan shell penjual tidak diedit oleh paket.

## Cakupan desain

Tujuh layar tambahan:

| Layar | Node Figma | Data / aksi |
|---|---|---|
| Beranda | 46:3297 | GET buyer/products, buyer/commodities, auth/profile, notifications, buyer/cart |
| Hasil pencarian / kategori | 46:3416 | GET buyer/products dengan search, commodityId, sort, page; tab toko mengelompokkan katalog publik berdasarkan farmerId |
| Detail produk | 46:3503 | GET buyer/products/{id}, POST buyer/cart/items, profil toko yang sudah ada |
| Kategori | 46:3582 | Komoditas aktif dari server; riwayat produk yang benar-benar dibuka selama sesi |
| Keranjang | 46:3815 | GET cart, PUT jumlah absolut, DELETE hanya baris terpilih, preview dan checkout hanya produk terpilih |
| Keranjang kosong | 46:3939 | Kondisi cart.items kosong, tombol kembali berbelanja |
| Notifikasi | 46:4986 | GET notifikasi berhalaman, PUT read/read-all, navigasi internal pesanan/refund/tiket |

ZIP (15) berisi node 46:3416 dan 46:4986. ZIP (14) melewati batas baca 32 MiB; atas persetujuan pengguna, lima layar utama sisanya ditemukan langsung di Figma. Jangan menganggap isi ZIP (14) sudah diekstrak.

Paket juga membawa 15 layar yang dikerjakan sebelumnya: checkout, alamat, pesanan dibuat, bukti pembayaran awal/ditolak, pelacakan, tanggapan jadwal, formulir refund, daftar pesanan, pesanan selesai, ulasan, detail refund, tiket bantuan, profil toko, akun. Total 22 layar utama pembeli. Login/registrasi dan alur petani/admin memakai implementasi tim yang sudah ada.

## Integrasi dan batas layanan yang nyata

- Foto, harga, stok, kategori, identitas toko, pesanan, serta status notifikasi berasal dari API. Foto contoh hanya ada di pengujian.
- Desain berisi contoh diskon, jumlah buah per kg, pengiriman instan/besok, galeri lima foto, dan kutipan ulasan publik. API belum menyediakan semua data tersebut. Tampilan mempertahankan struktur, warna, tipografi, serta aset dekoratif desain; teks transaksi mengikuti data sebenarnya. Tidak ada promo, estimasi tiba, kutipan ulasan, atau keberhasilan transaksi yang dibuat-buat.
- Jenis penerimaan memakai pickup/delivery; jadwal dan tarif divalidasi API saat checkout. Satu pesanan mengikuti satu toko. Isi keranjang toko lain tidak otomatis dihapus.
- Pilihan produk diterapkan pada fulfillment-options, preview, dan checkout. Perubahan jumlah memakai PUT absolut. Hapus massal meminta konfirmasi terlebih dahulu.
- Endpoint checkout saat ini tidak mengosongkan keranjang. Setelah server mengakui pesanan, frontend membersihkan baris pesanan yang ID serta jumlahnya masih cocok dengan snapshot. Baris yang tidak dipilih tetap dipertahankan. Jika isi berubah atau cleanup gagal, pesanan tetap dinyatakan berhasil dan ada peringatan memeriksa keranjang. Tidak mengulang POST pesanan untuk mengulang cleanup. API belum memiliki operasi cleanup atomik dengan versi keranjang; perubahan serentak dari perangkat lain tetap memerlukan pemeriksaan pengguna.
- Kunci idempotensi checkout dan eskalasi tetap dipakai. Respons jaringan hilang tidak dianggap bukti transaksi gagal.
- Notifikasi menulis status baca ke server. Tautan hanya menerima /orders/{id}, /orders/{id}/schedule, /refunds/{id}, /escalations/{id}; tidak membuka URL eksternal dari payload. Status pesanan dimuat untuk menentukan halaman tujuan. Kepemilikan tetap diperiksa server.
- Ulasan memakai endpoint yang sudah ada, hanya rating/comment. Tag dirangkum ke comment. Endpoint tidak tersedia tidak diperlakukan sebagai sukses.
- Alamat tambahan dan keterangan tiket tersimpan lokal per akun; hanya alamat utama disinkronkan melalui auth/profile. UI menjelaskan keterbatasan lampiran tiket. Email akun tetap read-only. Nomor CS sesuai pengguna: 0812345678; WhatsApp membuka draf, tidak mengirim otomatis.

## Resolusi merge

ConnectedApp.kt berangkat dari berkas hasil auto-merge yang diunggah pengguna. ConnectedViewModel.kt mempertahankan fungsi pembeli dan fungsi farmerWorkspace/farmerOrder/saveFarmerProduct dari kedua sisi konflik, menghapus duplikasi deliveryAreas/shippingEnabled, dan mempertahankan seluruh 102 nama fungsi awal. Tidak ada pemilihan keseluruhan ours/theirs. Pengguna masih harus menandai resolusi dengan git add, lalu menyelesaikan merge commit.

## Verifikasi

Pengujian memakai MockWebServer untuk kontrak API, Robolectric + Compose untuk interaksi, dan renderer Android native untuk pratinjau. Data pada gambar pratinjau adalah fixture pengujian, bukan data yang dimasukkan ke aplikasi produksi. Versi Android/status bar di HP dapat berbeda dari contoh iOS pada Figma.

Lisensi font Inter: Buyer-Inter-OFL.txt. Aset dekoratif berasal dari file desain yang disediakan pengguna; font dan aset disertakan agar tidak ada referensi drawable yang tertinggal.
