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
- Validasi terhadap `ped.ifp` asli (Fase G) **sudah dilakukan** untuk template: file ANP3 dengan 296 animasi dan
  7.797 urutan bone terbaca penuh (`IfpReader`). Seluruh 32 bone template cocok dengan IFP pada nama persis
  (termasuk spasi awal, mis. `" Pelvis"`) DAN ID HAnim, dan tidak ada ID di luar template selain penanda
  tanpa ID (0xFFFFFFFF) dan ujung jari (304, 305). Sebagian kecil urutan memakai nama lain untuk ID yang sama
  (mis. `Normal` untuk ID 0, `" Spine 1"` untuk ID 2), jadi ID adalah kunci yang lebih aman daripada nama.
  `IfpRigCheck` bisa dipakai ulang untuk memeriksa rig hasil ekspor terhadap `ped.ifp` milik pengguna;
  berkas IFP tidak dibundel. Uji di dalam game tetap belum dilakukan.

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
- Kaki dibaca dari mesh juga: rata-rata lateral titik di dasar tubuh menentukan jarak kaki (offset +0,006,
  simpangan baku 0,021), dan lutut ada di 52,5% jalur pinggul-kaki. Pose kaki rapat atau renggang terbaca.
- Lebar bahu memakai pecahan tinggi badan tetap (setengah lebar 0,09 tinggi), bukan pecahan bentang tangan.
  Standar GTA 0,118; empat mod diukur 0,06-0,09, jadi dipilih nilai tengah. Bahu mod tidak bisa ditebak dari
  mesh saja (regresi dan ekstrapolasi sumbu lengan dicoba dan tidak stabil), sehingga ini tetap perkiraan.
- Akurasi terhadap skeleton asli (galat sendi, pecahan tinggi badan, median):
  - 116 DFF stok (in-sample): 2,9%, p90 5,2%, p99 8,0%.
  - Empat mod pose berbeda: KasumiFK (T-pose) 4,8%, mai_korean 2,9%, Tamaki_Nude 3,4%, hira 6,6%.
    Hierarki keempatnya identik dengan standar GTA (32 ID HAnim sama).
  - Terbesar yang tersisa: dada, jari kaki, perut (semuanya kedalaman), sekitar 6-8%.
- Dicoba dan dibatalkan: offset kedalaman berdasarkan arah hadap (dari tonjolan telapak kaki). Membaik di dua
  mod tetapi memburuk di dua lainnya dan di ekor galat 116 DFF stok (p99 8% jadi 14%), jadi tidak dipakai.
  Kedalaman dibiarkan di tengah mesh. Arah hadap yang andal butuh petunjuk dari pengguna.
- Hasilnya **estimasi**, bukan rig terverifikasi. Pengguna harus mengoreksi sendi secara manual sebelum dipakai.
- Uji: contoh OBJ Blender tanpa rig (36.336 vertex, Y-up) menghasilkan kaki di pergelangan
  (y ≈ -0.95 dari dasar -1.02), kepala di atas pinggul, dan lengan simetris.
- Estimasi tidak membaca bentuk mesh di luar bounding box. Tebakan yang lebih cerdas belum dikerjakan.

## 6b. Tes rig dengan ped.ifp (v4.9)

Tombol "Tes rig (ped.ifp)" muncul setelah Auto-rig. Isinya memainkan keyframe ped.ifp pada skeleton hasil estimasi,
untuk melihat apakah rig GTA standar benar-benar bisa digerakkan oleh animasi asli.

- `IfpReader.parse(bytes, withKeys = true)` membaca isi keyframe. Format frame ANP3: tipe 3 = x,y,z,w (int16/4096)
  + waktu (uint16, 1/60 detik), 10 byte; tipe 4 menambah translasi x,y,z (int16/1024), 16 byte.
- `PosePlayer.pose` adalah kinematika maju. Rotasi IFP adalah rotasi LOKAL mutlak (menggantikan rotasi frame DFF),
  matriks lokal kolom = matriks dari kuaternion apa adanya. Posisi anak = posisi induk + rotasi dunia induk x offset lokal.
  Offset lokal = arah standar (`GtaRestPose.DIR`, median 116 DFF) x panjang tulang hasil estimasi. Urutan tipe 4
  (Root, kadang Pelvis) mengganti offset lokal dengan translasi IFP yang diskalakan ke ukuran model.
  Bone yang tidak ada di animasi memakai rotasi diam DFF (`GtaRestPose.REST_Q`), seperti di game.
- Rotasi diam DFF bila ditumpuk apa adanya memang "ngawur" (orientasi aneh yang normal di game); pose berdiri datang dari
  IFP. `GtaRestPose.STANCE_Q` menyimpan frame pertama `IDLE_stance` untuk tampilan awal tanpa ped.ifp dan untuk tes.
- Ruang GTA: X kanan, Y depan, Z atas. Ke ruang model: atas = sumbu tinggi, +lateral = sisi L, depan = lateral x atas.
  Arah hadap tidak bisa dibaca dari mesh; kotak "Tukar kiri/kanan" membalik L/R sekaligus arah hadap.
- Pose digambar di samping model (digeser 0,8 bentang tangan ke sisi L) karena mesh belum punya bobot; mesh belum ikut bergerak.
- Hasil pada ped.ifp asli milik pengguna (296 animasi, 32 bone cocok): 2.664 pose (3 waktu x 296 animasi x 3 model)
  semuanya terbatas dan lengkap. `IDLE_stance`: kepala di atas pinggul, kaki di bawah, tangan menjuntai di bawah leher.
  `WALK_player`: kaki bergantian sekitar -0,4 sampai +0,4 terhadap pinggul. Tinggi pose sama dengan tinggi rig diam.
  Uji ini dijalankan di luar CI karena memakai berkas milik pengguna; tes CI memakai IFP sintetis.

## 6c. Auto-weight dan pratinjau skinning (v5.0)

Bobot untuk model tanpa bobot, dan mesh yang ikut pose di tes rig.

- `AutoWeights.compute`: jarak tiap vertex ke segmen 23 bone utama (bone kecil digabung: Belly ke Spine, dada ke Spine1,
  alis dan rahang ke Head, Finger01 ke Finger, Root ke Pelvis). Bobot = 1 / (jarak / jari-jari^0,7 + eps)^6, hanya bone yang
  jaraknya <= 1,5 x terdekat, bone sisi seberang diberi penalti jarak 2,5x, lalu dihaluskan 2 putaran lewat tepi mesh,
  dan diambil 4 terbesar (batas GTA SA). Segmen paha mulai 20% di bawah sendi pinggul; segmen Pelvis turun 3% tinggi badan.
  Jari-jari per bone disetel (coordinate descent) pada separuh dari 116 DFF stok.
- Akurasi terhadap bobot asli (bone kecil digabung ke bone utama di kedua sisi; L1 = separuh jumlah selisih mutlak per vertex,
  0 = identik, 1 = sama sekali beda):
  - 116 DFF stok, rig dari estimasi: bone dominan benar 73,8%, L1 0,282. Separuh yang tidak dipakai menyetel: 73,6% dan 0,285.
  - Rig asli (bukan estimasi): 77,2% dan 0,253. Selisihnya adalah harga dari galat estimasi sendi.
  - Empat mod (tidak dipakai menyetel): 66,4% dan 0,304 dengan rig estimasi, 78,9% dan 0,230 dengan rig asli.
  - Kaki, lengan, kepala 80-96% benar. Terlemah: pinggul/perut (Pelvis dan Spine saling tertukar), leher, klavikula.
- `SkinPreview`: linear blend skinning di CPU. Rotasi tiap bone = swing dari arah segmen diam ke arah segmen berpose;
  bone torso juga disejajarkan garis bahu / pinggulnya. Puntiran sepanjang lengan dan kaki TIDAK diikuti.
- Regangan sisi segitiga setelah deformasi pada seluruh 296 animasi (3 waktu per animasi): bmymib (stok) median 1,00,
  p99 1,50, maks 2,45; KasumiFK (mod T-pose) median 1,00, p99 1,77, maks 9,9 (bagian tipis seperti ikat pinggang dan
  untaian rambut yang terlepas dari badan ikut tertarik).
- Ini PRATINJAU untuk memeriksa rig, bukan bobot akhir. Bobot tidak disimpan dan belum ditulis ke Skin PLG (Fase F).

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
