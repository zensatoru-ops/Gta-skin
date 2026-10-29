# Fase E — Rig humanoid (spesifikasi)

Status: **spesifikasi, belum ada kode**. Dibuat sebelum implementasi sesuai
`Master_Project_Workflow_v3` bagian 5.9 ("jangan mengarang skeleton otomatis
tanpa spesifikasi") dan bagian 487 ("tidak ada klaim auto-rig universal").

## 1. Ruang lingkup

Dalam:
- Rig untuk model **humanoid** yang memakai hierarki skeleton standar ped GTA SA
  (33 frame, 32 bone HAnim).
- Sumber FBX yang **sudah membawa Skin deformer** (bobot dan nama bone ada di file).
- Ekspor ke DFF dengan Skin PLG (Fase F, dirujuk di sini sebagai syarat).

Di luar (belum dikerjakan):
- Auto-rig universal, untuk model non-humanoid atau tanpa skeleton.
- OBJ tanpa bobot: butuh penanda manual (lihat bagian 6), bukan tebakan.
- Animasi. `ped.ifp` hanya dipakai untuk validasi di Fase G.

## 2. Template skeleton

Template berisi **data hierarki saja** (nama, ID HAnim, index parent). Tidak ada
aset game yang dibundel ke app.

Diambil dari parser pada 116 DFF uji: semuanya punya hierarki yang identik.
Tabel di bawah dari `army.dff`. Index = urutan frame di DFF.

| Index | Parent | Nama frame (persis) | HAnim ID |
|---|---|---|---|
| 1 | 0 | `Root` | 0 |
| 2 | 1 | ` Pelvis` | 1 |
| 3 | 2 | ` R Thigh` | 51 |
| 4 | 2 | ` L Thigh` | 41 |
| 5 | 2 | ` Spine` | 2 |
| 6 | 5 | `Belly` | 201 |
| 7 | 5 | ` Spine1` | 3 |
| 8 | 7 | `R breast` | 301 |
| 9 | 7 | `L breast` | 302 |
| 10 | 7 | ` Neck` | 4 |
| 11 | 10 | `Bip01 R Clavicle` | 21 |
| 12 | 10 | `Bip01 L Clavicle` | 31 |
| 13 | 10 | ` Head` | 5 |
| 14 | 13 | `R Brow` | 7 |
| 15 | 13 | `L Brow` | 6 |
| 16 | 13 | `Jaw` | 8 |
| 17 | 12 | ` L UpperArm` | 32 |
| 18 | 17 | ` L ForeArm` | 33 |
| 19 | 18 | ` L Hand` | 34 |
| 20 | 19 | ` L Finger` | 35 |
| 21 | 20 | `L Finger01` | 36 |
| 22 | 11 | ` R UpperArm` | 22 |
| 23 | 22 | ` R ForeArm` | 23 |
| 24 | 23 | ` R Hand` | 24 |
| 25 | 24 | ` R Finger` | 25 |
| 26 | 25 | `R Finger01` | 26 |
| 27 | 4 | ` L Calf` | 42 |
| 28 | 27 | ` L Foot` | 43 |
| 29 | 28 | ` L Toe0` | 44 |
| 30 | 3 | ` R Calf` | 52 |
| 31 | 30 | ` R Foot` | 53 |
| 32 | 31 | ` R Toe0` | 54 |

Catatan:
- Nama frame memakai spasi di awal pada sebagian bone (`" Pelvis"`). Template
  **menyimpan nama persis**, karena pencocokan dengan `ped.ifp` dilakukan per nama.
- Urutan `hanimNodeIds` (`0,1,2,3,4,5,8,6,7,31,32,...`) adalah urutan bone di Skin PLG.
  Urutan ini harus dipertahankan saat menulis ulang bobot.

## 3. Pemetaan bone dari FBX

Sumber bobot: deformer `Skin` dan `Cluster` pada FBX.

Aturan:
1. Setiap `Cluster` dicocokkan ke bone template lewat **nama Model** yang terhubung
   (`Connections`). Pencocokan memakai nama yang sudah dinormalisasi: spasi di awal
   dan akhir dibuang, lalu dibandingkan tanpa membedakan huruf besar/kecil.
2. Bone yang tidak cocok dengan template → **error**, bukan dilewati diam-diam.
3. Bone template yang tidak punya Cluster → bobot 0 untuk semua vertex, dilaporkan
   sebagai WARNING.
4. Maksimum 4 bobot per vertex (batas Skin PLG). Jika lebih, ambil 4 terbesar lalu
   normalisasi, dan laporkan jumlah vertex yang terpotong.
5. Jumlah bobot per vertex harus 1.0 ± 1e-3 setelah normalisasi. Vertex tanpa bobot
   dilaporkan (sama seperti `unweightedVertices` di `SkinStats`).

## 4. Validasi

- Hierarki hasil rig harus **identik** dengan template (jumlah frame, nama, parent,
  ID HAnim). Tes JVM membandingkan tabel di bagian 2.
- Setiap index bone di Skin PLG harus < jumlah bone, sama seperti validator DFF yang
  sudah ada (`badIndexVertices`).
- Round-trip: DFF hasil ekspor harus dibaca ulang oleh `DffParser` tanpa error dan
  dengan `SkinStats` yang sama dengan sumber.
- Uji di game (Fase G) memakai `ped.ifp` asli: nama bone di DFF harus cocok dengan
  nama di IFP. **Ini belum bisa diuji tanpa file `ped.ifp`.**

## 5. Batas dan risiko

- Bind pose: transform bind harus dihitung dari posisi bone di template, bukan dari
  posisi bone di FBX. Ini membutuhkan verifikasi terhadap `BindPose.kt` yang sudah ada.
- Skala dan satuan FBX (`UnitScaleFactor`) belum diterapkan di Fase D. Rig tidak boleh
  dibuat sebelum satuan ini ditangani, kalau tidak skeleton akan salah ukuran.
- Model dengan frame tambahan (mod) bisa punya hierarki berbeda. Mereka ditolak
  dengan pesan jelas, bukan dipaksa cocok.

## 6. OBJ tanpa bobot (estimasi)

OBJ tidak punya bobot atau tulang. Yang dibuat sekarang:
- `HumanoidFitter.detectUp` menebak sumbu tinggi dari rentang terbesar, dan pemanggil boleh menimpanya.
- `HumanoidFitter.estimate` memberi posisi sendi dari bounding box memakai proporsi yang diukur dari
  116 DFF skin GTA SA (median tinggi dan posisi lateral per bone; hanya angka, tanpa aset game).
  Sisi L di +lateral dan R di -lateral. Semua 32 bone punya proporsi sendiri.
- Pose lengan dibaca dari mesh: ujung lateral terluar (tangan) menentukan tinggi dan jarak tangan, sehingga
  pose T, A, dan lengan turun terbaca. Offset tangan diukur dari 120 DFF (simpangan baku tinggi 0,011).
  Sumbu tinggi dan sumbu lateral dipilih otomatis karena mod diekspor dengan sumbu berbeda (terlihat Y, X, Z).
  Sisi L/R tidak bisa dibaca dari bounding box; `estimate(..., mirror = true)` menukarnya (perlu tombol UI).
- Akurasi terhadap skeleton asli (galat sendi, pecahan tinggi badan):
  - 116 DFF stok (in-sample): median 3,0%, p90 5,7%, p99 8,0%.
  - Empat mod pose berbeda: KasumiFK (T-pose) median 6,1% (sebelumnya p90 30% jadi 13%), mai_korean 4,2%,
    Tamaki_Nude 3,4%, hira 6,6%. Hierarki keempatnya identik dengan standar GTA (32 ID HAnim sama).
- Hasilnya **estimasi**, bukan rig terverifikasi. Pengguna harus mengoreksi sendi secara manual sebelum dipakai.
- Uji: contoh OBJ Blender tanpa rig (36.336 vertex, Y-up) menghasilkan kaki di pergelangan
  (y ≈ -0.95 dari dasar -1.02), kepala di atas pinggul, dan lengan simetris.
- Estimasi tidak membaca bentuk mesh di luar bounding box. Tebakan yang lebih cerdas belum dikerjakan.

## 7. Urutan kerja yang disarankan

1. Tes JVM untuk tabel template (bagian 2) dan pemetaan nama (bagian 3), tanpa data
   game.
2. Parser Cluster/Skin FBX dan pembentukan bobot, dengan fixture sintetis.
3. Penulis Skin PLG (Fase F), dengan tes round-trip.
4. Validasi terhadap `ped.ifp` (Fase G), setelah file itu tersedia.

## 8. Yang masih dibutuhkan dari pengguna

- File `ped.ifp` asli, untuk validasi nama bone (Fase G).
- Uji pemetaan bobot: FBX Hinata sudah punya **5 deformer Skin dan 325 Cluster**,
  jadi bisa dipakai. Yang belum diketahui: apakah nama Model yang ditunjuk Cluster
  cocok dengan nama bone template. Itu akan terlihat saat parser Cluster dibuat (langkah 2).
