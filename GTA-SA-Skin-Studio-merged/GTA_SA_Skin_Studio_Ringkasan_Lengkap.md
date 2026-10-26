# GTA SA Skin Studio — Ringkasan Lengkap

Dokumen tunggal yang merangkum seluruh pekerjaan sampai saat ini: tujuan, riset, lisensi, analisis sampel, status format, rencana build APK, keputusan, dan langkah berikutnya.

Tanggal: 25 Oktober 2026 · Status: **Phase 0 dan Phase 1 selesai (dokumen), Phase 2 belum dimulai**

Catatan penting: belum ada kode aplikasi. Semua klaim kompatibilitas Android di bawah memakai status `VERIFIED` / `INFERRED` / `UNKNOWN` dan tidak dinyatakan sebagai fakta kalau belum diuji di game.

---

## 1. Tujuan proyek

Aplikasi Android untuk membuka, memeriksa, mengedit, dan mengekspor model karakter (skin/ped) **GTA San Andreas Android Classic** (bukan Definitive Edition).

- Target OS: Android 7-14 (minSdk 24).
- Fokus: mesh, skeleton, skinning, rigging. Viewer 3D hanyalah fondasi.
- Bahasa aplikasi: **Indonesia + English penuh** (UI dan pesan error dua bahasa).
- Distribusi: **hanya GitHub**, tidak ke Google Play.
- Bukan tujuan awal: seluruh fitur RenderWare, 2DFX, collision, multi-clump, MatFX kompleks, menyalin seluruh DragonFF.

## 2. Prinsip kerja

1. Evidence-first: keputusan format harus didukung sampel, spesifikasi, atau uji nyata.
2. Satu fase pada satu waktu; jangan loncat ke fitur lanjutan.
3. Parser defensif: semua ukuran, offset, indeks, dan alokasi divalidasi.
4. Tidak membuang data diam-diam; fitur tak didukung dilaporkan atau ekspor diblokir.
5. Round-trip internal **bukan** bukti kompatibilitas game.
6. Pisahkan fakta dan asumsi: `VERIFIED`, `IMPLEMENTED_UNVERIFIED`, `INFERRED`, `UNKNOWN`, `UNSUPPORTED`, `LOSSY`.
7. Aset game asli tidak masuk repo publik.

## 3. Hasil audit DragonFF-Multicomp (Phase 0)

Sumber: pembacaan statis source dari `DragonFF-multi-clump.zip` (50 file, ~12.960 baris Python). Zip tidak memuat `.git`, jadi **tidak ada commit hash** dan diff terhadap upstream tidak dilakukan.

**Struktur dan ketergantungan Blender**
- `gtaLib/` (DFF, TXD, skin, native WDGL, platform lain) **tidak memakai `bpy`** sama sekali. `ops/`, `gui/`, dan `__init__.py` terikat Blender.
- Dengan begitu logika format sudah terpisah dan bisa dijadikan rujukan.

**Yang didukung referensi (terbaca di kode)**
- Chunk header 12 byte, decode versi RW, Clump, Frame List, Atomic, Geometry, Material List, Bin Mesh.
- Skin PLG format baru dan lama, HAnim, hierarki frame.
- Native OpenGL (WDGL): **dibaca, tidak ditulis**.
- Exporter menulis geometri gaya PC non-native.

**Celah**
- Skin split tidak dibaca dan ditulis sebagai nol (LOSSY).
- Tidak ada platform tekstur OGL/PVRTC/ETC/ATC; platform TXD yang dikenali: D3D8, D3D9, PS2, Xbox, GC, PSP.
- Tidak ada pemeriksaan batas; jumlah elemen dari header langsung jadi batas loop; error ditelan ke `print`; nama tekstur non-ASCII terpotong; pembacaan Python per-vertex lambat.

**Lisensi**
- Proyek utama: GPL-3.0-or-later. `gtaLib/pyffi/utils/*`: BSD 3-Clause. `pytristrip` (opsional): lisensi belum dicek.
- Menerjemahkan kode `gtaLib` ke aplikasi menjadikan aplikasi turunan GPL-3.0.
- Koreksi: GPL **tidak otomatis melarang** Google Play. Hal itu tidak relevan lagi karena distribusi hanya lewat GitHub.
- Dengan source terbuka di GitHub, GPL-3.0 paling praktis: sertakan lisensi, atribusi (Parik; pyffi), dan source. Bukan nasihat hukum.

**Audit ZModeler Android v2.8:** `ZModeler_Android_APK_Audit.md` disebut di dokumen induk tetapi **tidak ada di sesi ini**. Hanya temuan yang tertulis di dokumen induk yang dipakai (indikasi class DFF, DFFSkin, DFFHanim, IFP, OBJ, COL, OpenGL ES; audit statis, belum terbukti bekerja).

## 4. Analisis sampel pertama (F-01 / F-02)

Sampel: skin mod pihak ketiga "A74 Alice", dikirim pemilik proyek. Klaim pemilik: berjalan di GTA SA Android (APK 2.0) dan PC. **Belum diuji oleh kami.** Analisis memakai skrip sementara berbatas, bukan parser aplikasi.

| File | SHA-256 | Ukuran |
|---|---|---|
| `A74_Alice.dff` | `3239c8a91edd80933cc5ae8f7a4babf174d4a8e20572e7a7180a6bdb9b4543b7` | 5.320.238 byte |
| `A74_Alice.txd` | `a65077030f7b5f4d39032c97e18ff425aadabc43bb130e34f824e8187f66e0e7` | 16.791.960 byte |

**DFF**
- Versi RW **3.6.0.3** (`0x36003`), satu clump / atomic / geometry; struktur habis tepat di akhir file.
- **Non-native**, trilist (Bin Mesh flags 0), prelit, normal, 1 UV layer. **Tidak ada Native Data PLG.**
- 63.078 vertex, 88.652 segitiga, 25 material (25 mesh, 265.956 indeks).
- 33 frame, parent index valid.
- Skin PLG format baru: 32 bone, 28 terpakai, maks 4 weight/vertex; ukuran chunk cocok persis dengan rumus format baru.
- Right to Render = (0x116, 1).

**TXD**
- 20 tekstur, semua platform 9 (**D3D9**); 14 DXT1, 6 raster 32-bit (8888). Mipmap penuh di tekstur besar.
- Dua tekstur 2048x2048, delapan 1024x1024; satu tekstur 619x1295 (bukan pangkat dua). Total 16,8 MB.
- Tidak ada PVRTC/ETC/ATC/OGL.

**Kesimpulan (INFERRED, bukan VERIFIED di game):** build target menerima DFF gaya PC dan TXD D3D9. Exporter kemungkinan tidak wajib menulis format native, dan dekoder DXT1 + 8888 cukup untuk sampel ini.

**Batas kesimpulan:** satu skin mod, bukan aset APK stok. File stok bisa berbeda; baris WDGL tetap `UNKNOWN`.

**Catatan praktis**
- 63 ribu vertex mendekati batas indeks 16-bit (65.535).
- Tekstur besar membebani memori GPU HP kelas menengah.
- Tekstur `Nikke_Alice` dan `Alan74` kemungkinan pratinjau yang tidak dirujuk material (belum dicek).
- Ekstensi MatFX/Rockstar pada 25 material belum diperiksa satu per satu.

## 5. Matriks dukungan format (ringkas)

Detail lengkap ada di `FORMAT_SUPPORT.md`. Status mengacu ke sampel, bukan seluruh game.

| Fitur | Status |
|---|---|
| Versi RW 3.6.0.3 | VERIFIED (file sampel) |
| Geometri non-native, trilist | VERIFIED (file sampel) |
| Native Data PLG / WDGL | Tidak ada di sampel; **UNKNOWN** untuk build |
| Skin PLG format baru | VERIFIED ukuran; isi (weight, indeks) INFERRED |
| Frame/hierarki (33 frame) | VERIFIED (file sampel) |
| TXD D3D9, DXT1 dan 8888 | VERIFIED (file sampel) |
| Diterima game Android | INFERRED (klaim pemilik); GAME-01..03 belum dijalankan |
| Skin split, MatFX, 2DFX, COL, multi-clump, IFP | UNSUPPORTED (dilaporkan; ekspor diblokir bila data hilang) |

**Subset MVP yang diusulkan:** DFF RW 3.6.0.3 non-native + Skin PLG baru + Bin Mesh trilist + TXD D3D9 (DXT1, 8888).

**Pertanyaan teknis**

| ID | Pertanyaan | Status |
|---|---|---|
| Q1 | DFF apa yang diterima build target? | Sebagian terjawab (INFERRED): PC-style |
| Q2 | Representasi native pada sampel? | Tidak ada di sampel; UNKNOWN untuk stok |
| Q3 | Platform/kompresi TXD? | Sebagian terjawab (INFERRED): D3D9, DXT1 + 8888 |
| Q4 | Versi RW? | VERIFIED untuk sampel: 3.6.0.3 |
| Q5 | Hasil ekspor jalan di game? | UNKNOWN (belum ada exporter) |
| Q6 | Skin split pada skin target? | Tidak terdeteksi di sampel (INFERRED) |
| Q7 | Lisensi | GPL-3.0 sebagai asumsi sementara; keputusan eksplisit pemilik diperlukan |

## 6. Rencana tes

Detail di `TEST_PLAN.md`; kategori: PAR (parser), VAL (validator), FMT, SKL, TXD, REN, EDT, EXP, AND, GAME.

- Fixture buatan sendiri (M-xx) untuk tes parser tanpa aset game: chunk size palsu, vertex count ekstrem, nesting berlebihan, indeks/NaN/weight invalid, file terpotong, fuzz berseed.
- Fixture asli (F-xx): F-01/F-02 sudah ada (disimpan di `fixtures-private/`, bukan di repo). F-04 (DFF non-skin dari APK stok) paling berguna berikutnya.
- Limit awal (usulan, bukan fakta format): input 64 MiB, nesting 32, vertex 65.535 per geometry, bone maks 255.
- Uji di game dicatat per build dan perangkat; klaim kompatibilitas hanya untuk kombinasi yang diuji.

## 7. Rencana build APK

**Jalur:** build di **GitHub Actions** (ubuntu-22.04, JDK 17, Android SDK). Alur: saya tulis project dan workflow → push → Actions menjalankan `./gradlew assembleDebug` → APK jadi artifact (nanti GitHub Releases) → dipasang di HP dan hasilnya dilaporkan balik.

Tidak butuh Android Studio. Lingkungan sesi ini punya Java 21 dan Gradle, tetapi akses ke repositori Android (Google Maven, Gradle) tampaknya diblokir (belum dipastikan penuh), sehingga hasil build yang sah datang dari CI.

**Stack yang disetujui**
- Kotlin + Jetpack Compose untuk UI.
- Viewer: `GLSurfaceView` + OpenGL ES 2.0.
- Parser: Kotlin dulu; pindah ke C++/NDK hanya bila pengukuran menunjukkan perlu (sampel 63 ribu vertex jadi ujian awal).
- minSdk 24 (Android 7); akses file lewat Storage Access Framework, tanpa path absolut.
- Lisensi: GPL-3.0 (sementara). Repo publik masuk akal.

## 8. Roadmap fase

| Fase | Isi | Status |
|---|---|---|
| 0 | Audit dan riset | Selesai |
| 1 | `FORMAT_SUPPORT.md`, `TEST_PLAN.md`, `docs/fixtures/README.md` | Selesai (dokumen) |
| 2 | Fondasi Android: project, SAF, layar error, CI | **Berikutnya** |
| 3 | Parser defensif + validator + tes unit | Belum |
| 4 | Scene model + viewer 3D | Belum |
| 5 | Skeleton dan inspeksi skin | Belum |
| 6 | Edit mesh + undo/redo | Belum |
| 7 | TXD dan material | Belum |
| 8 | Ekspor DFF + round-trip | Belum |
| 9 | Rigging dan edit weights | Belum |
| 10 | Uji game, performa, stabilisasi | Belum |

Aturan: satu fase pada satu waktu. Selesai fase = acceptance criteria PASS dengan bukti tes, bukan sekadar kompilasi berhasil.

## 9. Keputusan yang sudah diambil

- Target: GTA SA Android Classic, Android 7-14.
- Distribusi hanya GitHub; tidak ke Google Play.
- Bahasa aplikasi: Indonesia + English penuh.
- Tech stack seperti bagian 7.
- Aset game dan karya pihak ketiga tidak masuk repo publik (hanya hash dan hasil analisis).

## 10. Yang masih dibutuhkan

| Hal | Status |
|---|---|
| Repo GitHub kosong | Belum; cara 1 (kirim `user/repo`, saya sambungkan) atau cara 2 (saya kirim zip, kamu push) |
| Akun GitHub terhubung ke Claude | Belum terhubung di sesi ini (jika memilih cara 1) |
| Nama package | Belum; default usulan `id.gtasaskinstudio.app` |
| Keputusan lisensi final | GPL-3.0 sementara |
| DFF non-skin dari APK stok (F-04) | Opsional tapi berguna untuk cek apakah file stok native |
| Uji F-01 di game Android (GAME-01) | Belum; hanya klaim pemilik |
| `ZModeler_Android_APK_Audit.md` | Tidak ada di sesi ini; kirim bila ingin dipakai |

## 11. Berkas proyek

```
GTA-SA-Skin-Studio/
├── FORMAT_SUPPORT.md
├── TEST_PLAN.md
├── GTA_SA_Skin_Studio_Ringkasan_Lengkap.md   (dokumen ini)
└── docs/
    ├── fixtures/README.md
    └── research/
        ├── dragonff-study.md
        └── sample-analysis-A74_Alice.md
```

Aset privat (tidak masuk repo): `fixtures-private/admin_audit/A74_Alice.dff` dan `.txd`.

## 12. Tidak diklaim

- Bahwa sampel benar-benar berjalan di Android (hanya klaim pemilik).
- Bahwa bobot dan indeks bone di skin valid (hanya ukuran chunk yang diperiksa).
- Bahwa semua DFF/TXD bawaan APK memakai format yang sama dengan sampel.
- Bahwa APK dapat dibangun dari lingkungan ini.
- Kompatibilitas ekspor dengan game (exporter belum ada).
- Nasihat hukum soal lisensi.

## 13. Langkah berikutnya

1. Pilih cara kirim repo (cara 1 atau 2) dan konfirmasi nama package.
2. Mulai **Phase 2**: project Kotlin + Compose, workflow GitHub Actions, layar buka file via SAF, tampilan error dua bahasa. Target: APK debug yang terpasang dan bisa memilih `A74_Alice.dff`.
3. Lanjut Phase 3 (parser defensif) hanya setelah Phase 2 lolos acceptance criteria.
