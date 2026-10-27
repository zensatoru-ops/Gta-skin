# Keputusan parser FBX (Fase D)

Status: IMPLEMENTED_UNVERIFIED (uji JVM lulus; belum diuji dengan berkas FBX dari perangkat).

## Keputusan
Parser FBX biner ditulis sendiri di Kotlin (`fbx/FbxBinaryReader.kt`, `fbx/FbxImporter.kt`).

Alasan:
- Tanpa dependensi native dan tanpa NDK, sehingga build GitHub Actions tetap sederhana.
- Tidak ada beban lisensi tambahan; kode berada di bawah lisensi proyek.
- Setiap bagian bisa diuji dengan fixture buatan sendiri (`FbxImporterTest`).

## Opsi yang dipertimbangkan
1. Parser biner sendiri di Kotlin (dipilih).
2. ufbx (library C tunggal lewat NDK/JNI). Belum dipilih: butuh build native, dan lisensi perlu diverifikasi di repo resminya sebelum dipakai.
3. Autodesk FBX SDK. Tidak dipilih: SDK ini memakai EULA proprietary yang perlu dikaji sebelum distribusi GPL-3; diletakkan sebagai opsi terakhir.

## Matriks varian yang didukung
| Aspek | Didukung | Belum didukung |
|---|---|---|
| Format | FBX biner | FBX ASCII |
| Versi | 7.x (header 13 byte < 7500, 25 byte >= 7500) | versi lain (dibaca tetapi tidak diuji) |
| Geometri | Mesh pertama (`Geometry`, tipe `Mesh`) | geometri kedua dan seterusnya (dilaporkan sebagai WARNING) |
| Vertex | `Vertices` (double) | Native/Morph |
| Polygon | `PolygonVertexIndex`, segi tiga, segi empat, n-gon (kipas) | - |
| Normal | `LayerElementNormal`, ByPolygonVertex atau ByVertice, Direct | IndexToDirect untuk normal |
| UV | `LayerElementUV`, ByPolygonVertex atau ByVertice, Direct atau IndexToDirect | lapisan UV kedua |
| Array | raw dan zlib (encoding 0 dan 1) | - |
| Transform Model, satuan | belum diterapkan (dilaporkan INFO) | - |
| Material | satu material; nama dari node Material pertama (dilaporkan INFO) | pemetaan material per polygon, tekstur dari FBX |
| Skeleton, skin | belum dibaca | semua data rig |

## Batas yang sengaja dipasang
- Vertex keluaran dibatasi 65535 (indeks 16-bit renderer).
- Node dibatasi 2.000.000, kedalaman 64, dan ukuran array sesuai batas berkas.
- Setiap pembacaan diperiksa terhadap panjang berkas; berkas rusak menghasilkan kegagalan terstruktur, tidak crash.
