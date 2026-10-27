# Matriks dukungan format / Format support matrix

Status mengacu pada sampel F-01/F-02 (`A74_Alice.dff` / `.txd`), **bukan**
klaim untuk seluruh aset game. Legenda status:

- `VERIFIED` — diuji langsung dan terbukti benar.
- `IMPLEMENTED_UNVERIFIED` — kode sudah ada, belum diuji.
- `INFERRED` — disimpulkan dari bukti tidak langsung (mis. klaim pemilik, analisis statis).
- `UNKNOWN` — belum ada data.
- `UNSUPPORTED` — sengaja tidak didukung; ekspor diblokir bila data terkait hilang.
- `LOSSY` — dibaca tapi tidak ditulis penuh, data bisa hilang saat ekspor.

## DFF (model)

| Fitur | Status | Catatan |
|---|---|---|
| Chunk header 12 byte | VERIFIED (sampel) | Dari audit statis DragonFF + sampel F-01 |
| RW version 3.6.0.3 (`0x36003`) | VERIFIED (sampel) | Satu-satunya versi yang teruji sejauh ini |
| Clump / Frame List / Atomic / Geometry / Material List / Bin Mesh | VERIFIED (sampel) | |
| Geometri non-native (gaya PC), trilist | VERIFIED (sampel) | Bin Mesh flags 0 di sampel |
| Native Data PLG / WDGL (OpenGL native) | UNKNOWN (build) | DragonFF hanya membaca, tidak menulis; sampel tidak memakainya |
| Skin PLG format baru | VERIFIED ukuran (sampel) | Isi (bobot, indeks bone) INFERRED — ukuran chunk cocok, isi belum divalidasi semantik |
| Skin PLG format lama | UNKNOWN | Dukungan baca ada di DragonFF, belum ada sampel |
| Skin split | UNSUPPORTED | Tidak dibaca/ditulis oleh DragonFF (LOSSY di sana); diblokir di aplikasi ini bila terdeteksi |
| HAnim / hierarki frame | VERIFIED (sampel, 33 frame, parent index valid) | |
| MatFX kompleks | UNSUPPORTED (Phase 2) | Belum diperiksa per-material di sampel |
| 2DFX | UNSUPPORTED | Di luar tujuan awal |
| Collision (COL) | UNSUPPORTED | Di luar tujuan awal |
| Multi-clump | UNSUPPORTED | Di luar tujuan awal |

## TXD (tekstur)

| Fitur | Status | Catatan |
|---|---|---|
| Platform D3D8 | UNKNOWN | Dikenali di DragonFF, belum ada sampel |
| Platform D3D9 | VERIFIED (sampel) | 20/20 tekstur sampel pakai platform ini |
| DXT1 | VERIFIED (sampel) | 14/20 tekstur sampel |
| Raster 32-bit (8888) | VERIFIED (sampel) | 6/20 tekstur sampel |
| Mipmap | VERIFIED (sampel, pada tekstur besar) | |
| PVRTC / ETC / ATC / OGL native | UNSUPPORTED | Tidak ada di DragonFF maupun sampel; kemungkinan dibutuhkan untuk aset stok APK — lihat F-04 |
| Platform PS2 / Xbox / GC / PSP | UNKNOWN | Dikenali di DragonFF, tidak relevan untuk Android, tidak diprioritaskan |

## Diterima oleh game Android

| Klaim | Status |
|---|---|
| Build target menerima DFF gaya PC + TXD D3D9 | INFERRED (klaim pemilik, belum ada GAME-01..03) |
| Hasil ekspor aplikasi ini bisa dipakai di game | UNKNOWN (exporter belum ada, lihat Phase 8) |

## Subset MVP yang diusulkan

DFF RW 3.6.0.3 non-native + Skin PLG baru + Bin Mesh trilist + TXD D3D9
(DXT1, 8888). Apa pun di luar ini dilaporkan sebagai tidak didukung, bukan
diabaikan diam-diam (prinsip kerja #4).

## Limit parser awal (usulan, bukan fakta format)

| Batas | Nilai | Sumber |
|---|---|---|
| Ukuran input maksimum | 64 MiB | Usulan, belum diuji |
| Kedalaman nesting chunk maksimum | 32 | Usulan, belum diuji |
| Vertex per geometry | 65.535 | Mendekati batas indeks 16-bit; sampel F-01 (63.078 vertex) sudah dekat batas ini |
| Bone maksimum | 255 | Usulan, belum diuji |

Lihat `GTA_SA_Skin_Studio_Ringkasan_Lengkap.md` bagian 5 untuk daftar
pertanyaan teknis terbuka (Q1-Q7) dan bagian 4 untuk detail penuh analisis
sampel.

## 3b. Temuan Phase 3–5 / Phase 3–5 findings

- Sampel A74_Alice: id material di triangle semuanya 0; **Bin Mesh PLG (0x50E) yang menentukan material** (25 grup). Status: VERIFIED pada 1 sampel.
- Chunk tak dikenal pada sampel: `0x133` (belum teridentifikasi, UNKNOWN) dan `0x253f2fc` (ekstensi material reflection, INFERRED). Keduanya dilaporkan sebagai peringatan, tidak diparse.
- Bin Mesh tristrip belum didukung (UNSUPPORTED); hanya trilist.
- Parser/validator/scene: IMPLEMENTED_UNVERIFIED di perangkat; diverifikasi di JVM (32 tes, 72 ms pada sampel, 0 error).
- Batas pertahanan: lihat `Limits` di `rw/Issue.kt`.

## 3c. Temuan Phase 6–7 / Phase 6–7 findings

- DFF: pola `0x0100000C` pada struct clump (byte 19 = `01`) muncul di KasumiFK dan mai_korean; dibaca dari 24 bit rendah dan dilaporkan sebagai WARNING `STRUCT_SIZE_HIGH_BYTE`. Status: VERIFIED pada 2 sampel mod, tanpa error setelah perbaikan.
- DFF: data non-chunk setelah clump teratas (teks base64, sekitar 35 KB) dibuang dengan WARNING `TRAILING_BYTES`. Asal teks tidak diketahui (UNKNOWN); tidak berpengaruh pada geometri.
- Urutan bone Skin PLG mengikuti daftar node HAnim, bukan urutan frame. Status: VERIFIED pada maccer dan 117 DFF stock/mod.
- Pose bind (inverse-bone) sama dengan pose istirahat pada 116 dari 117 file; selisih ≤ 0,05. Alice berselisih ~0,25 namun tetap berfungsi di game; dicatat sebagai INFO, bukan error.
- Orientasi model tidak dipakai sebagai kriteria: sejumlah file terbalik di ZModeler namun normal di game.
- TXD (A74_Alice.txd): 20 tekstur, platform D3D9 (9). Format: DXT1 (FourCC `DXT1`), A8R8G8B8 (D3DFMT 21). Level mip 0 cocok persis dengan ukuran `w×h/2` (DXT1) atau `w×h×4` (ARGB). Status: VERIFIED pada 1 sampel; DXT3/DXT5 belum diuji pada sampel.
- Dimensi tekstur: 16 bit rendah = width, 16 bit tinggi = height. Byte ke-2 dari field level = jumlah level mip.
- Mip > 0 tidak diparse (hanya mip 0 yang dipakai).
- Dekode DXT dan ARGB di CPU (`rw/TxdDecoder.kt`); hasil PNG Alan74 dan Nikke_Alice sudah diperiksa visual. Status: IMPLEMENTED_UNVERIFIED di perangkat.
