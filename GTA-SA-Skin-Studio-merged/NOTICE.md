# Atribusi pihak ketiga / Third-party notices

Proyek ini **tidak** menyalin kode dari proyek lain pada Phase 2 (fondasi
Android, tanpa parser). Riset format pada Phase 0 mempelajari struktur file
RenderWare DFF/TXD dengan merujuk pada dua proyek sumber-terbuka sebagai
bahan rujukan logika format, tercatat di sini untuk transparansi dan untuk
kepatuhan lisensi bila kode rujukan tersebut diterjemahkan ke Kotlin pada
fase berikutnya (Phase 3 dan seterusnya).

| Proyek | Penulis | Lisensi | Dipakai sebagai |
|---|---|---|---|
| DragonFF | Parik (dan kontributor) | GPL-3.0-or-later | Rujukan struktur chunk RenderWare (DFF/TXD/Skin PLG) — dibaca untuk riset, lihat `docs/research/dragonff-study.md` |
| pyffi (`gtaLib/pyffi/utils`, dependensi DragonFF) | pyffi contributors | BSD 3-Clause | Disebut dalam audit lisensi sumber, tidak dipakai langsung di Phase 2 |

Karena logika format DragonFF berlisensi GPL-3.0-or-later, kode aplikasi apa
pun yang menerjemahkan logika tersebut (direncanakan mulai Phase 3) menjadikan
modul terkait tunduk pada GPL-3.0. Ini alasan proyek ini memakai lisensi
GPL-3.0 secara keseluruhan — lihat `GTA_SA_Skin_Studio_Ringkasan_Lengkap.md`
bagian 3. Ini bukan nasihat hukum.

Tidak ada aset game asli (model, tekstur) dari GTA San Andreas atau mod pihak
ketiga yang disertakan di repo ini.
