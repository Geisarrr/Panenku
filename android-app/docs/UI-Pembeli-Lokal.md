# UI pembeli yang disesuaikan dengan kode lokal

Dasar dua file utama adalah ConnectedApp.kt dan ConnectedViewModel.kt dari Kode-Pembeli-Lokal-20261123-144957.zip. Setelah normalisasi LF/CRLF, keduanya cocok dengan commit 89ecb9ae3561d6a6685d256f7cf0a5f9fc77443f. Paket sebelumnya memakai dasar 6e40a1e, sehingga installer memang berhenti saat melihat versi yang berbeda.

## Yang dipertahankan

- ConnectedLogin lokal beserta login/register pembeli/petani tetap utuh.
- Cabang navigasi penjual dan admin tetap menggunakan komponen lokal semula.
- Semua nama fungsi ViewModel lokal tetap ada; tambahan difokuskan pada transaksi pembeli.
- Tidak mengubah backend, MainActivity, PanenkuApi, Gradle, .idea, atau local.properties.

## Yang ditambahkan

Delapan layar transaksi sesuai referensi PAPB PANENKU (7).zip: checkout, alamat, pesanan berhasil dibuat, pembayaran awal, bukti ditolak/unggah ulang, tracking/konfirmasi penerimaan, perubahan jadwal dan form refund. ZIP (6) belum tercakup karena batas unduh 32 MiB.

Komponen tampilan disertakan pada namespace ui.connected.buyer. Font, ikon, dan ilustrasi punya nama resource buyer_* agar tidak memerlukan atau menimpa komponen tampilan penjual. Lisensi font Inter tersedia pada Buyer-Inter-OFL.txt.

Layar terhubung melalui ConnectedPanenkuApp ke API yang sudah tersedia. Harga/ongkir/jadwal/tujuan pembayaran/status berasal dari server; angka dan foto contoh hanya dipakai pada tes. Tidak ada aksi simulasi penerimaan atau verifikasi pembayaran oleh pembeli.

## Ketentuan alur

- Checkout memerlukan alamat dalam area layanan (untuk antar), jadwal tersedia, quote dari server, dan persetujuan pengguna. Perubahan pilihan membatalkan quote lama; kegagalan koneksi memakai mekanisme idempotensi.
- Pilih tujuan pembayaran, transfer lewat aplikasi bank/pembayaran, lalu unggah bukti JPG/PNG maksimal 5 MB. Pembayaran tetap diperiksa petani. Batas waktu dan perubahan status mengikuti server.
- Terima/tolak usulan jadwal menggunakan versi proposal server. Konfirmasi barang diterima membutuhkan dialog persetujuan.
- Refund menggunakan total penuh yang dibayar; pembayaran harus terverifikasi dan memenuhi batas waktu. Pengajuan aktif/refund selesai mencegah duplikasi; server memvalidasi ulang.
- API hanya menyediakan satu deliveryAddress pada profil. Alamat tambahan disimpan lokal per akun; alamat utama disinkronkan ke profil tanpa mengganti nama/telepon pemilik akun dengan data penerima.
- Pelaporan pengiriman umum belum memiliki endpoint. Tombol masalah memberikan petunjuk dan akses refund bila memenuhi syarat.
- Beranda/katalog/daftar pesanan serta fitur ulasan yang belum ada pada versi lokal tidak ditambahkan lewat paket ini.

## Pemasangan

Gunakan Terapkan-UI-Pembeli.ps1 dalam paket v2. Skrip memeriksa semua hash sebelum menulis, menerima perbedaan LF/CRLF, lalu membuat cadangan file lama di Temp. Dua hash dasar utama berasal dari ZIP lokal yang diunggah, bukan memaksa kode mengikuti paket lama. Jika kode lokal berubah lagi, installer berhenti untuk mencegah kehilangan perubahan.

Buka android-app, jalankan backend seperti biasa, dan gunakan URL API lokal yang sesuai perangkat. Tampilan transaksi baru muncul setelah masuk sebagai pembeli melalui alur ConnectedPanenkuApp. Pratinjau pengujian memakai data Figma, bukan transaksi pada backend nyata.
