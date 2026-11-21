# Mengirim perubahan integrasi ke GitHub

Perubahan dibuat dari main commit `1bee7cb1f857906f32ee71621da50e543ff9fe0d`. Koneksi GitHub pada sesi pengerjaan menolak operasi penulisan dengan 403 `Resource not accessible by integration`. Tidak ada branch, commit, atau PR baru yang berhasil dikirim ke GitHub.

Paket hasil berisi `Panenku-Integrasi.bundle` untuk mengambil commit lengkap lewat Git, patch cadangan, serta salinan source tanpa folder build/rahasia lokal.

## Pilihan utama: ambil commit dari bundle

Simpan pekerjaan lokal terlebih dahulu memakai commit atau stash sesuai kebiasaan tim. Git akan menolak switch bila perubahan lokal yang belum disimpan akan tertimpa; jangan memakai `--force` atau `reset --hard` untuk melewatinya.

Dari folder repository Panenku di laptop, sesuaikan path file hasil ekstraksi:

```powershell
git fetch "C:\Users\NAMA\Downloads\Panenku-Integrasi-API\Panenku-Integrasi.bundle" codex/integrasi-api-f1-f7
git switch -c integrasi-api FETCH_HEAD
git push -u origin integrasi-api
```

Bundle ini memerlukan commit dasar di atas (sudah ada di repository GitHub yang diperiksa). `git fetch` dari bundle tidak mengubah working tree. Jika nama branch `integrasi-api` sudah dipakai, pilih nama baru. Jika fetch/switch/push gagal, baca error sebelum melanjutkan. Tidak perlu menyalin manual tiap file Kotlin.

Setelah push berhasil, teman mengambilnya:

```powershell
git fetch origin
git switch --track origin/integrasi-api
```

Jika sudah berada pada branch itu, gunakan `git pull --ff-only`. Setelah tinjauan tim, buat PR dari `integrasi-api` ke `main`; merge mengikuti keputusan tim.

## Cadangan: patch atau source

`Panenku-Integrasi.patch` adalah patch Git binary terhadap commit dasar. Untuk working tree yang cocok dan bersih, `git apply --check --index PATH_PATCH` memeriksa kecocokannya, lalu `git apply --index PATH_PATCH` menerapkan perubahan. Jangan menggabungkan metode patch dan bundle untuk perubahan yang sama.

Folder `Panenku/` adalah source lengkap yang bisa dibuka langsung di Android Studio pada subfolder `android-app`. Salinan source ini tidak membawa riwayat `.git`; gunakan bundle untuk memperbarui repository yang sudah ada.

Setelah kode masuk, ikuti README untuk menjalankan backend Docker dan aplikasi Android. Pull kode tidak menyalakan server secara otomatis; setiap laptop tetap menjalankan Docker dan emulator sendiri.
