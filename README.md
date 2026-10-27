# GTA SA Skin Studio

Aplikasi Android untuk membuka, memeriksa, mengedit, dan mengekspor model karakter
(skin/ped) **GTA San Andreas Android Classic** (bukan Definitive Edition).

Android application for opening, inspecting, editing, and exporting character
(skin/ped) models for **GTA San Andreas Android Classic** (not the Definitive Edition).

Status proyek lengkap: lihat [`GTA_SA_Skin_Studio_Ringkasan_Lengkap.md`](./GTA_SA_Skin_Studio_Ringkasan_Lengkap.md).

## Status

**Phase 3–5** (parser/validator DFF, viewer 3D OpenGL ES 2.0, inspeksi
skeleton/skin) sudah ditulis — status `IMPLEMENTED_UNVERIFIED` di perangkat:
parser diuji di JVM (32 tes unit + 1 berkas DFF nyata), kode UI/GL/SAF belum
pernah dikompilasi/dijalankan di Android. Belum ada TXD/tekstur, editor, atau
export. Lihat roadmap di dokumen ringkasan, bagian 8.

| Fase | Isi | Status |
|---|---|---|
| 0 | Audit dan riset | Selesai |
| 1 | Dokumentasi format & rencana tes | Selesai |
| 2 | Fondasi Android: project, SAF, layar error, CI | Selesai (CI hijau) |
| 3 | Parser defensif + validator + tes unit | IMPLEMENTED_UNVERIFIED |
| 4 | Viewer 3D (GLES 2.0) | IMPLEMENTED_UNVERIFIED |
| 5 | Inspeksi skeleton & skin | IMPLEMENTED_UNVERIFIED |
| 6 | Perbaikan parser (struct size, trailing bytes) + skeleton bind pose | IMPLEMENTED_UNVERIFIED |
| 7 | Tekstur TXD (D3D9 DXT1/3/5, A8R8G8B8) + tampil di viewer | IMPLEMENTED_UNVERIFIED |
| 8-10 | Lihat dokumen ringkasan | Belum |

## Target

- minSdk 24 (Android 7.0), target Android 14.
- Kotlin + Jetpack Compose.
- Viewer 3D: `GLSurfaceView` + OpenGL ES 2.0 (Phase 4, belum diuji di perangkat).
- Akses file lewat Storage Access Framework (SAF) — tidak memakai path absolut.
- Bahasa aplikasi: Indonesia (default) + English penuh, termasuk pesan error.
- Distribusi: **hanya GitHub Releases**, tidak ke Google Play.

## Lisensi

GPL-3.0-or-later (sementara, lihat dokumen ringkasan bagian 3 dan 7 untuk alasan).
Lihat [`LICENSE`](./LICENSE). Atribusi pihak ketiga ada di [`NOTICE.md`](./NOTICE.md)
(riset format mengacu pada DragonFF oleh Parik dan pyffi, keduanya tidak disalin
kodenya ke aplikasi ini pada Phase 2).

Aset game asli dan karya pihak ketiga **tidak** masuk repo ini — lihat
`fixtures-private/` yang sengaja kosong dan di-`.gitignore`.

## Build

Build resmi dilakukan lewat GitHub Actions (lihat
`.github/workflows/android-ci.yml`). Setiap push ke `main` dan setiap pull
request menghasilkan APK debug sebagai artifact.

Build lokal butuh Android Studio (Hedgehog+) atau Gradle 8.7+ dan Android SDK
(compileSdk 34, build-tools 34.0.0). Karena wrapper jar tidak disertakan di
repo ini (dibuat lewat `gradle wrapper` saat pertama kali build), gunakan
Gradle yang terinstal untuk generate wrapper:

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## Struktur

```
GTA-SA-Skin-Studio/
├── app/                        # modul aplikasi Android
├── docs/
│   ├── fixtures/README.md      # deskripsi fixture tes (M-xx, F-xx)
│   └── research/                # hasil audit Phase 0
├── fixtures-private/            # kosong di repo publik, untuk aset privat lokal
├── FORMAT_SUPPORT.md            # matriks dukungan format RW/DFF/TXD
├── TEST_PLAN.md                 # rencana tes PAR/VAL/FMT/SKL/TXD/REN/EDT/EXP/AND/GAME
└── GTA_SA_Skin_Studio_Ringkasan_Lengkap.md
```

## Kontribusi

Proyek masih tahap awal (Phase 3–5). Evidence-first: klaim kompatibilitas format
harus berstatus `VERIFIED`, `INFERRED`, atau `UNKNOWN` — tidak boleh dinyatakan
sebagai fakta tanpa uji nyata. Lihat prinsip kerja di dokumen ringkasan bagian 2.

## Catatan Phase 2 (audit lanjutan)

Perubahan dari draft awal setelah pemeriksaan statis:

- Gradle wrapper target dinaikkan ke **8.7** (AGP 8.5.2 butuh Gradle ≥8.7; 8.5 akan gagal sync di CI).
- `LICENSE` diisi teks resmi GPL-3.0 penuh dari FSF (sebelumnya hanya header).
- `MainScreen` memakai `TopAppBar` (Material3 experimental) → ditandai `@OptIn(ExperimentalMaterial3Api::class)` eksplisit, bukan dibiarkan jadi warning yang bisa menjadi error lint.
- `formatBytes` dan `LocalLang` dipindah keluar dari file Compose (`util/Format.kt`, `i18n/LocalLang.kt`) supaya `AppStrings.kt` bisa dites murni di JVM tanpa classpath Android (`testDebugUnitTest` di CI sekarang punya isi).
- `SafFileInfo.kt`: query cursor ditulis eksplisit (bukan `?:` menyatu dengan `.use {}`) untuk menghindari null-safety yang ambigu; `SUPPORTED_MIME_TYPES` disederhanakan ke `*/*` saja (memasukkan `application/octet-stream` di array yang sama dengan `*/*` tidak menambah apa pun — `*/*` sudah mencakup semuanya).
- Direktori kosong (`res/xml`, `res/values-v26`, `java/.../ui/`) dihapus — tidak tersimpan di git dan tidak punya isi.
- Workflow CI: tambah `permissions: contents: read` (least-privilege default untuk `GITHUB_TOKEN`).
- Tes unit baru: `AppStringsTest` (setiap `StrKey` wajib py Indonesia + Inggris, tidak boleh fallback ke nama enum) dan `FormatTest` (termasuk dua angka nyata dari sampel F-01/F-02: 5.320.238 byte → `5.07 MB`, 16.791.960 byte → `16.01 MB`).

Belum diverifikasi: `./gradlew assembleDebug` di lingkungan ini (akses jaringan ke Google Maven/Gradle Plugin Portal diblokir). Build sah tetap lewat GitHub Actions — lihat langkah berikutnya.
