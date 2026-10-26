# Rencana tes / Test plan

Kategori tes mengikuti prinsip kerja #1 (evidence-first) dan #5 (round-trip
internal bukan bukti kompatibilitas game). Setiap kategori punya tujuan,
jenis fixture, dan kriteria lolos yang berbeda.

## Kategori

| Kode | Nama | Tujuan |
|---|---|---|
| PAR | Parser | Parser tidak crash, tidak hang, tidak out-of-bounds pada input valid maupun rusak |
| VAL | Validator | Validator menolak/menandai data yang melanggar batas (ukuran, indeks, NaN) sebelum dipakai |
| FMT | Format round-trip | Baca-tulis-baca menghasilkan data semantik yang sama (bukan byte-identik) |
| SKL | Skeleton/skinning | Hierarki frame dan bobot skin terbaca benar dan konsisten dengan mesh |
| TXD | Tekstur | Dekode TXD menghasilkan gambar yang benar secara visual untuk tiap platform didukung |
| REN | Render | Viewer 3D menampilkan mesh+skeleton+tekstur tanpa artefak yang jelas salah |
| EDT | Edit | Operasi edit (mesh/weight) menghasilkan state yang valid dan bisa di-undo |
| EXP | Ekspor | File hasil ekspor valid secara format (lolos parser sendiri) |
| AND | Android-spesifik | SAF, siklus hidup activity, rotasi layar, memori rendah |
| GAME | Uji di game nyata | Hasil ekspor benar-benar dimuat dan tampil benar di GTA SA Android |

## Fixture

### Buatan sendiri (M-xx) — tanpa aset game

Dipakai untuk PAR dan VAL, tidak butuh izin pihak ketiga.

| ID | Deskripsi |
|---|---|
| M-01 | Chunk size dipalsukan lebih besar dari sisa file |
| M-02 | Vertex count ekstrem (0, 1, 65.536, nilai negatif via overflow) |
| M-03 | Nesting chunk berlebihan (melebihi batas 32) |
| M-04 | Indeks bone di luar jangkauan |
| M-05 | Bobot skin NaN / Infinity / negatif / tidak dinormalisasi |
| M-06 | File terpotong di tengah header |
| M-07 | File terpotong di tengah data geometri |
| M-08 | Fuzz berseed (mutasi byte acak dari fixture valid, seed dicatat agar reproducible) |

Kriteria lolos PAR/VAL untuk M-xx: tidak crash, tidak hang (timeout wajar),
error dilaporkan dengan pesan dua bahasa yang jelas, tidak ada alokasi memori
tak terbatas dari nilai header yang dipalsukan.

### Asli (F-xx) — dari sampel nyata

Disimpan di `fixtures-private/`, **tidak** masuk repo publik. Hanya hash dan
hasil analisis yang dipublikasikan (lihat dokumen ringkasan bagian 4 dan 11).

| ID | Deskripsi | Status |
|---|---|---|
| F-01 | `A74_Alice.dff` — skin mod pihak ketiga | Ada, dianalisis (lihat `docs/research/sample-analysis-A74_Alice.md`) |
| F-02 | `A74_Alice.txd` — tekstur pasangan F-01 | Ada, dianalisis |
| F-03 | (dicadangkan) | Belum ada |
| F-04 | DFF non-skin dari APK stok game | Belum ada — prioritas berikutnya, untuk cek apakah aset stok memakai format native (WDGL) |

### GAME-xx — uji di perangkat nyata

Dicatat per build APK dan per perangkat (model, versi Android, versi game).
Klaim kompatibilitas hanya berlaku untuk kombinasi yang benar-benar diuji —
tidak digeneralisasi ke perangkat lain tanpa bukti terpisah.

| ID | Deskripsi | Status |
|---|---|---|
| GAME-01 | Muat F-01 (tanpa modifikasi) di GTA SA Android, cek tampil benar | Belum dijalankan |
| GAME-02 | Ekspor ulang F-01 tanpa edit (round-trip), cek masih tampil benar | Belum — butuh exporter (Phase 8) |
| GAME-03 | Ekspor F-01 dengan edit minor, cek masih tampil benar | Belum — butuh editor (Phase 6) + exporter |

## Urutan eksekusi per fase

Tes ditambahkan seiring fase berjalan — lihat `GTA_SA_Skin_Studio_Ringkasan_Lengkap.md`
bagian 8:

1. Phase 2: AND (SAF, layar error, lifecycle dasar) — belum ada parser untuk diuji.
2. Phase 3: PAR + VAL dengan fixture M-xx, mulai F-01/F-02 sebagai input baca.
3. Phase 4-5: REN + SKL.
4. Phase 6: EDT.
5. Phase 7: TXD (dekode visual).
6. Phase 8: FMT (round-trip) + EXP.
7. Phase 9: SKL lanjutan (edit weight).
8. Phase 10: GAME-01..03 di perangkat nyata, lalu stabilisasi.

Acceptance criteria setiap fase = PASS dengan bukti tes tercatat (log, hash,
atau tangkapan layar), bukan sekadar kompilasi berhasil (prinsip kerja #2 dan
aturan roadmap di bagian 8).
