# Analisis sampel F-01/F-02 — "A74 Alice"

Sampel: skin mod pihak ketiga, dikirim pemilik proyek. Klaim pemilik: berjalan
di GTA SA Android (APK 2.0) dan PC. **Belum diuji oleh tim ini** — lihat
`TEST_PLAN.md` kategori GAME. Analisis memakai skrip sementara berbatas,
bukan parser aplikasi yang sebenarnya.

## Berkas

| File | SHA-256 | Ukuran |
|---|---|---|
| `A74_Alice.dff` | `3239c8a91edd80933cc5ae8f7a4babf174d4a8e20572e7a7180a6bdb9b4543b7` | 5.320.238 byte |
| `A74_Alice.txd` | `a65077030f7b5f4d39032c97e18ff425aadabc43bb130e34f824e8187f66e0e7` | 16.791.960 byte |

Berkas asli disimpan di `fixtures-private/admin_audit/` secara lokal, tidak
masuk repo publik — lihat `docs/fixtures/README.md`.

## DFF

- Versi RenderWare **3.6.0.3** (`0x36003`).
- Satu clump / satu atomic / satu geometry; struktur chunk habis tepat di
  akhir file (tidak ada sisa byte tak terjelaskan).
- **Non-native**, trilist (Bin Mesh flags 0), prelit, normal per-vertex,
  1 layer UV. **Tidak ada Native Data PLG** (tidak memakai WDGL).
- 63.078 vertex, 88.652 segitiga, 25 material (25 mesh, total 265.956
  indeks).
- 33 frame, parent index semuanya valid (tidak ada referensi ke luar
  jangkauan).
- Skin PLG format baru: 32 bone, 28 di antaranya benar-benar terpakai,
  maksimum 4 bobot per vertex. Ukuran chunk cocok persis dengan rumus
  format baru (format lama sudah dieliminasi dari kemungkinan berdasarkan
  ukuran).
- Right to Render = `(0x116, 1)`.

## TXD

- 20 tekstur, semuanya platform 9 (**D3D9**).
- 14 tekstur DXT1, 6 tekstur raster 32-bit (format `8888`).
- Mipmap penuh pada tekstur-tekstur besar.
- Dua tekstur 2048×2048, delapan tekstur 1024×1024, satu tekstur 619×1295
  (bukan pangkat dua — dimensi non-power-of-two tetap valid di format TXD,
  dicatat karena relevan untuk kompatibilitas GPU tertentu).
- Total ukuran tekstur terdekode: 16,8 MB.
- Tidak ditemukan tekstur berformat PVRTC/ETC/ATC/OGL (native) di sampel
  ini.

## Kesimpulan (INFERRED, bukan VERIFIED di game)

Build target kemungkinan menerima DFF gaya PC dan TXD D3D9. Exporter
kemungkinan tidak wajib menulis format native, dan dekoder DXT1 + 8888
cukup untuk menangani sampel ini. Status ini tetap `INFERRED` sampai
GAME-01 dijalankan — lihat `TEST_PLAN.md`.

## Batas kesimpulan

- Satu skin mod, bukan aset APK stok — file stok bisa berbeda strukturnya,
  terutama soal pemakaian WDGL native (baris ini tetap `UNKNOWN` sampai
  F-04 dianalisis).
- Tidak mewakili kasus skin split, MatFX kompleks, atau multi-clump —
  fitur-fitur ini tetap `UNSUPPORTED` di aplikasi (lihat `FORMAT_SUPPORT.md`).

## Catatan praktis

- 63 ribu vertex mendekati batas indeks 16-bit (65.535) — relevan untuk
  limit parser yang diusulkan di `FORMAT_SUPPORT.md`.
- Tekstur besar (2048×2048 × 2, dst.) membebani memori GPU pada perangkat
  Android kelas menengah — relevan untuk keputusan desain viewer di Phase 4.
- Tekstur `Nikke_Alice` dan `Alan74` kemungkinan pratinjau yang tidak
  dirujuk material manapun — belum dicek secara eksplisit terhadap daftar
  material.
- Ekstensi MatFX/Rockstar pada 25 material belum diperiksa satu per satu.

## Tidak diklaim

- Bahwa sampel benar-benar berjalan di Android (hanya klaim pemilik, belum
  diverifikasi tim ini).
- Bahwa bobot dan indeks bone di skin PLG valid secara semantik (hanya
  ukuran chunk yang diperiksa, bukan isinya).
- Bahwa seluruh DFF/TXD bawaan APK memakai format yang sama dengan sampel
  ini.
