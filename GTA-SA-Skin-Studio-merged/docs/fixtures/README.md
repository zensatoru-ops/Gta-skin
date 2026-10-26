# Fixtures

Dua jenis fixture dipakai untuk tes, lihat `TEST_PLAN.md` untuk detail penuh.

## `M-xx` — buatan sendiri (masuk repo publik)

File DFF/TXD kecil yang dibuat khusus untuk menguji parser dan validator
terhadap input rusak atau ekstrem: chunk size palsu, vertex count ekstrem,
nesting berlebihan, indeks/NaN/bobot tidak valid, file terpotong, dan fuzz
berseed. File-file ini tidak berasal dari aset game atau karya pihak ketiga,
jadi aman masuk repo publik begitu dibuat (belum dibuat per Phase 2).

Rencana penempatan: `docs/fixtures/M-xx/` (dibuat saat Phase 3 dimulai).

## `F-xx` — asli, dari sampel nyata (TIDAK masuk repo publik)

Disimpan secara lokal di `fixtures-private/admin_audit/`, yang di-`.gitignore`
di repo ini. Hanya hash SHA-256, ukuran file, dan hasil analisis yang
dipublikasikan — lihat `docs/research/sample-analysis-A74_Alice.md` dan
`GTA_SA_Skin_Studio_Ringkasan_Lengkap.md` bagian 4.

| ID | File | Status |
|---|---|---|
| F-01 | `A74_Alice.dff` | Ada secara lokal, dianalisis |
| F-02 | `A74_Alice.txd` | Ada secara lokal, dianalisis |
| F-04 | DFF non-skin dari APK stok | Belum ada — lihat `GTA_SA_Skin_Studio_Ringkasan_Lengkap.md` bagian 10 |

Jangan commit isi folder `fixtures-private/` ke git. Folder ini sengaja
dikosongkan di repo (hanya ada `.gitkeep`), lihat `.gitignore` di root.
