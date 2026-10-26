# Studi DragonFF (Phase 0)

Audit statis terhadap source `DragonFF-multi-clump.zip` (50 file, ~12.960
baris Python). Zip tidak memuat `.git`, jadi tidak ada commit hash dan tidak
ada diff terhadap upstream yang dilakukan.

## Struktur dan ketergantungan Blender

- `gtaLib/` (DFF, TXD, skin, native WDGL, platform lain) **tidak memakai
  `bpy`** sama sekali — logika format RenderWare terpisah bersih dari UI
  Blender.
- `ops/`, `gui/`, dan `__init__.py` terikat Blender (operator, panel UI,
  registrasi add-on).
- Konsekuensi: `gtaLib/` bisa dijadikan rujukan logika format tanpa perlu
  memahami integrasi Blender-nya.

## Yang didukung (terbaca di kode)

- Chunk header 12 byte, decode versi RW.
- Clump, Frame List, Atomic, Geometry, Material List, Bin Mesh.
- Skin PLG format baru dan lama.
- HAnim, hierarki frame.
- Native OpenGL (WDGL): **dibaca, tidak ditulis**.
- Exporter menulis geometri gaya PC (non-native) saja.
- Platform TXD yang dikenali: D3D8, D3D9, PS2, Xbox, GC, PSP.

## Celah yang ditemukan

- Skin split tidak dibaca dan ditulis sebagai nol — LOSSY bila skin target
  memakai skin split.
- Tidak ada platform tekstur OGL/PVRTC/ETC/ATC (relevan untuk aset native
  Android, yang belum diketahui dipakai atau tidak di APK stok — lihat
  `FORMAT_SUPPORT.md`).
- Tidak ada pemeriksaan batas: jumlah elemen dari header langsung dipakai
  sebagai batas loop tanpa validasi terhadap sisa ukuran file.
- Error ditelan ke `print` alih-alih dilaporkan terstruktur.
- Nama tekstur non-ASCII terpotong.
- Pembacaan Python per-vertex lambat (relevan untuk keputusan Kotlin vs
  C++/NDK di Phase 3, lihat `GTA_SA_Skin_Studio_Ringkasan_Lengkap.md`
  bagian 7).

## Implikasi untuk parser aplikasi ini

Celah di atas adalah alasan langsung di balik prinsip kerja #3 (parser
defensif: semua ukuran, offset, indeks, dan alokasi divalidasi) dan #4
(fitur tak didukung dilaporkan, bukan dibuang diam-diam). Parser Kotlin pada
Phase 3 tidak menyalin perilaku "jumlah elemen header langsung jadi batas
loop" dari DragonFF — setiap jumlah divalidasi terhadap sisa ukuran file dan
limit yang diusulkan di `FORMAT_SUPPORT.md` sebelum dipakai mengalokasikan
memori.

## Lisensi

- Proyek utama DragonFF: GPL-3.0-or-later.
- `gtaLib/pyffi/utils/*`: BSD 3-Clause.
- `pytristrip` (opsional): lisensi belum dicek.
- Menerjemahkan kode `gtaLib` ke aplikasi ini menjadikan modul terkait
  GPL-3.0. Lihat `NOTICE.md` dan `GTA_SA_Skin_Studio_Ringkasan_Lengkap.md`
  bagian 3 untuk detail dan penegasan bahwa ini bukan nasihat hukum.

## Audit ZModeler Android v2.8

`ZModeler_Android_APK_Audit.md` disebut di dokumen ringkasan utama tapi
belum ada sebagai dokumen terpisah di repo ini. Temuan yang tercatat sejauh
ini (dari audit statis, belum terbukti bekerja): indikasi class `DFF`,
`DFFSkin`, `DFFHanim`, `IFP`, `OBJ`, `COL`, OpenGL ES. Audit penuh menyusul
bila APK ZModeler dianalisis ulang secara terpisah.
