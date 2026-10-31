package zen.gtasaskinstudio.app.rig

/**
 * Konstanta pose diam skeleton standar GTA SA, median dari 116 DFF skin stok (hanya angka, tanpa aset game):
 * arah offset lokal tiap bone terhadap induknya (vektor satuan di ruang lokal induk), panjang offset stok,
 * dan rotasi lokal diam (kuaternion w,x,y,z). Dipakai untuk memainkan pose dari ped.ifp pada hasil auto-rig.
 * Indeks = urutan frame di [GtaStandardRig.BONES].
 *
 * Rest-pose constants of the standard GTA SA skeleton, medians over 116 stock skin DFFs (numbers only, no game assets):
 * the direction of each bone's local offset from its parent (unit vector in the parent's local space), the stock offset
 * length, and the rest local rotation (quaternion w,x,y,z). Used to play ped.ifp poses on an auto-rig result.
 * Index = frame order in [GtaStandardRig.BONES].
 */
object GtaRestPose {

    /** Arah offset lokal (satuan). / Local offset direction (unit). */
    val DIR: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.0, 0.0, 0.0),  // 0
        doubleArrayOf(0.0, 0.0, 0.0),  // 1
        doubleArrayOf(-0.9847, -0.1742, 0.0),  // 2
        doubleArrayOf(0.0, 0.0, -1.0),  // 3
        doubleArrayOf(0.0, 0.0, 1.0),  // 4
        doubleArrayOf(-0.1242, -0.989, 0.0799),  // 5
        doubleArrayOf(0.9278, -0.3731, -0.0022),  // 6
        doubleArrayOf(1.0, -0.0008, 0.0),  // 7
        doubleArrayOf(0.7557, -0.371, -0.5397),  // 8
        doubleArrayOf(0.7466, -0.3732, 0.5508),  // 9
        doubleArrayOf(1.0, -0.0004, 0.0),  // 10
        doubleArrayOf(-0.0001, 0.003, -1.0),  // 11
        doubleArrayOf(-0.0001, 0.003, 1.0),  // 12
        doubleArrayOf(1.0, 0.0, 0.0),  // 13
        doubleArrayOf(0.9661, -0.1139, -0.2316),  // 14
        doubleArrayOf(0.9581, -0.0959, 0.2699),  // 15
        doubleArrayOf(0.5804, 0.8141, -0.0195),  // 16
        doubleArrayOf(1.0, 0.0, 0.0),  // 17
        doubleArrayOf(1.0, 0.0, 0.0),  // 18
        doubleArrayOf(1.0, 0.0, 0.0),  // 19
        doubleArrayOf(1.0, 0.0, 0.0),  // 20
        doubleArrayOf(1.0, 0.0, 0.0),  // 21
        doubleArrayOf(1.0, 0.0, 0.0),  // 22
        doubleArrayOf(1.0, 0.0, 0.0),  // 23
        doubleArrayOf(1.0, 0.0, 0.0),  // 24
        doubleArrayOf(1.0, 0.0, 0.0),  // 25
        doubleArrayOf(1.0, 0.0, 0.0),  // 26
        doubleArrayOf(1.0, 0.0, 0.0),  // 27
        doubleArrayOf(1.0, 0.0, 0.0),  // 28
        doubleArrayOf(0.7271, 0.6865, 0.0),  // 29
        doubleArrayOf(1.0, 0.0, 0.0),  // 30
        doubleArrayOf(1.0, 0.0, 0.0),  // 31
        doubleArrayOf(0.7141, 0.7, 0.0),  // 32
    )

    /** Panjang offset lokal stok, meter. / Stock local offset length, meters. */
    val STOCK_LEN: DoubleArray = doubleArrayOf(0.0000, 0.0000, 0.0210, 0.1024, 0.1024, 0.0510, 0.1875, 0.2718, 0.1845, 0.1835, 0.2718, 0.0318, 0.0318, 0.1384, 0.0869, 0.0882, 0.0214, 0.1668, 0.2830, 0.2560, 0.0884, 0.0468, 0.1668, 0.2950, 0.2560, 0.0884, 0.0468, 0.4556, 0.4117, 0.1624, 0.4572, 0.4117, 0.1622)

    /** Rotasi lokal diam w,x,y,z. / Rest local rotation w,x,y,z. */
    val REST_Q: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0, 0.0),  // 0
        doubleArrayOf(0.5, -0.5, -0.5, -0.5),  // 1
        doubleArrayOf(0.5, -0.5, -0.5, -0.5),  // 2
        doubleArrayOf(0.0303, -0.0283, 0.9991, 0.0008),  // 3
        doubleArrayOf(0.0359, 0.0304, -0.9989, 0.0011),  // 4
        doubleArrayOf(1.0, 0.0, 0.0, 0.0004),  // 5
        doubleArrayOf(0.7186, 0.0, 0.0, 0.6954),  // 6
        doubleArrayOf(1.0, 0.0, 0.0, 0.0),  // 7
        doubleArrayOf(0.735, -0.0513, -0.022, 0.6758),  // 8
        doubleArrayOf(0.7473, 0.1112, 0.0725, 0.6511),  // 9
        doubleArrayOf(0.9921, 0.0, 0.0, 0.1253),  // 10
        doubleArrayOf(0.0912, -0.6353, 0.0869, 0.7619),  // 11
        doubleArrayOf(0.0916, 0.6325, -0.0865, 0.7642),  // 12
        doubleArrayOf(0.9958, 0.0, 0.0, -0.091),  // 13
        doubleArrayOf(0.7669, -0.0346, 0.0506, 0.6388),  // 14
        doubleArrayOf(0.7656, 0.0414, -0.0671, 0.6385),  // 15
        doubleArrayOf(0.568, 0.0, 0.0, 0.823),  // 16
        doubleArrayOf(0.8903, 0.0072, 0.4553, -0.0056),  // 17
        doubleArrayOf(1.0, 0.0, 0.0, -0.0053),  // 18
        doubleArrayOf(0.707, -0.7071, -0.0121, -0.0021),  // 19
        doubleArrayOf(0.9891, -0.0054, 0.0003, 0.1472),  // 20
        doubleArrayOf(0.9618, 0.0, 0.0, 0.2737),  // 21
        doubleArrayOf(0.8896, -0.0068, -0.4566, -0.0064),  // 22
        doubleArrayOf(1.0, 0.0, 0.0, -0.0036),  // 23
        doubleArrayOf(0.7067, 0.7071, 0.0226, 0.012),  // 24
        doubleArrayOf(0.9893, 0.0001, -0.0002, 0.1456),  // 25
        doubleArrayOf(0.9635, 0.0, 0.0, 0.2678),  // 26
        doubleArrayOf(0.9998, 0.0, 0.0, -0.0178),  // 27
        doubleArrayOf(0.9982, -0.0006, -0.0359, 0.0481),  // 28
        doubleArrayOf(0.7071, 0.0, 0.0, 0.7071),  // 29
        doubleArrayOf(0.9998, 0.0, 0.0, -0.0181),  // 30
        doubleArrayOf(0.9985, 0.0005, 0.0303, 0.0464),  // 31
        doubleArrayOf(0.7071, 0.0, 0.0, 0.7071),  // 32
    )

    /**
     * Pose berdiri (frame pertama animasi IDLE_stance): rotasi lokal w,x,y,z. Rotasi diam DFF sengaja "ngawur" bila
     * ditumpuk apa adanya; pose berdiri datang dari IFP. Dipakai sebagai tampilan awal dan untuk tes.
     * Standing pose (first frame of IDLE_stance): local rotations w,x,y,z. The DFF rest rotations are deliberately
     * "off" when composed as they are; the standing pose comes from the IFP. Used as the initial view and for tests.
     */
    val STANCE_Q: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0, 0.0),  // 0
        doubleArrayOf(0.7071, 0.0010, 0.0012, 0.7071),  // 1
        doubleArrayOf(0.4908, -0.5091, -0.5091, -0.4908),  // 2
        doubleArrayOf(0.0381, 0.0532, 0.9978, 0.0059),  // 3
        doubleArrayOf(0.0579, -0.0584, -0.9936, -0.0779),  // 4
        doubleArrayOf(0.9999, -0.0010, 0.0110, -0.0022),  // 5
        doubleArrayOf(0.7186, 0.0, 0.0, 0.6954),  // 6
        doubleArrayOf(0.9978, 0.0049, 0.0044, 0.0664),  // 7
        doubleArrayOf(0.7398, -0.0513, -0.0225, 0.6705),  // 8
        doubleArrayOf(0.7521, 0.1104, 0.0730, 0.6456),  // 9
        doubleArrayOf(0.9915, -0.0034, -0.0234, 0.1277),  // 10
        doubleArrayOf(0.0537, -0.6505, -0.0532, 0.7557),  // 11
        doubleArrayOf(0.0110, 0.6818, 0.0093, 0.7314),  // 12
        doubleArrayOf(0.9777, 0.0061, 0.0935, -0.1878),  // 13
        doubleArrayOf(0.7555, -0.0173, 0.0625, 0.6520),  // 14
        doubleArrayOf(0.7557, 0.0420, -0.0669, 0.6502),  // 15
        doubleArrayOf(0.5670, 0.0, 0.0, 0.8237),  // 16
        doubleArrayOf(0.8395, 0.0266, 0.5035, -0.2024),  // 17
        doubleArrayOf(0.9697, -0.1331, 0.0894, -0.1841),  // 18
        doubleArrayOf(0.6740, -0.7341, 0.0271, 0.0779),  // 19
        doubleArrayOf(0.9803, 0.0, 0.0, 0.1973),  // 20
        doubleArrayOf(0.9932, 0.0, 0.0, 0.1162),  // 21
        doubleArrayOf(0.8214, 0.1499, -0.5492, -0.0344),  // 22
        doubleArrayOf(0.9741, -0.0115, -0.1216, -0.1905),  // 23
        doubleArrayOf(0.8895, 0.4449, -0.0505, -0.0913),  // 24
        doubleArrayOf(0.9803, 0.0, 0.0, 0.1973),  // 25
        doubleArrayOf(0.9932, 0.0, 0.0, 0.1162),  // 26
        doubleArrayOf(0.9769, 0.0, 0.0, -0.2139),  // 27
        doubleArrayOf(0.9844, -0.0986, -0.0273, 0.1431),  // 28
        doubleArrayOf(0.7071, 0.0, 0.0, 0.7071),  // 29
        doubleArrayOf(0.9844, 0.0, 0.0, -0.1758),  // 30
        doubleArrayOf(0.9892, 0.0466, 0.0772, 0.1157),  // 31
        doubleArrayOf(0.6954, 0.0002, 0.0037, 0.7186),  // 32
    )
}
