package zen.gtasaskinstudio.app.rig

/**
 * Template skeleton standar ped GTA SA: hanya data hierarki (nama, ID HAnim, parent).
 * Tidak ada aset game yang dibundel. Index = urutan frame di DFF (0 = frame kontainer).
 *
 * GTA SA standard ped skeleton template: hierarchy data only (names, HAnim IDs, parents).
 * No game assets are bundled. Index = DFF frame order (0 = container frame).
 * Sumber: 116 DFF uji, seluruhnya identik. Lihat docs/phase-e-rig-spec.md bagian 2.
 */
data class RigBone(val index: Int, val name: String, val parent: Int, val hanimId: Int?)

object GtaStandardRig {

    /** Nama dipertahankan persis, termasuk spasi di awal sebagian bone. / Names kept exactly, including leading spaces. */
    val BONES: List<RigBone> = listOf(
        RigBone(0, "", -1, null),
        RigBone(1, "Root", 0, 0),
        RigBone(2, " Pelvis", 1, 1),
        RigBone(3, " R Thigh", 2, 51),
        RigBone(4, " L Thigh", 2, 41),
        RigBone(5, " Spine", 2, 2),
        RigBone(6, "Belly", 5, 201),
        RigBone(7, " Spine1", 5, 3),
        RigBone(8, "R breast", 7, 301),
        RigBone(9, "L breast", 7, 302),
        RigBone(10, " Neck", 7, 4),
        RigBone(11, "Bip01 R Clavicle", 10, 21),
        RigBone(12, "Bip01 L Clavicle", 10, 31),
        RigBone(13, " Head", 10, 5),
        RigBone(14, "R Brow", 13, 7),
        RigBone(15, "L Brow", 13, 6),
        RigBone(16, "Jaw", 13, 8),
        RigBone(17, " L UpperArm", 12, 32),
        RigBone(18, " L ForeArm", 17, 33),
        RigBone(19, " L Hand", 18, 34),
        RigBone(20, " L Finger", 19, 35),
        RigBone(21, "L Finger01", 20, 36),
        RigBone(22, " R UpperArm", 11, 22),
        RigBone(23, " R ForeArm", 22, 23),
        RigBone(24, " R Hand", 23, 24),
        RigBone(25, " R Finger", 24, 25),
        RigBone(26, "R Finger01", 25, 26),
        RigBone(27, " L Calf", 4, 42),
        RigBone(28, " L Foot", 27, 43),
        RigBone(29, " L Toe0", 28, 44),
        RigBone(30, " R Calf", 3, 52),
        RigBone(31, " R Foot", 30, 53),
        RigBone(32, " R Toe0", 31, 54),
    )

    /** Jumlah frame di DFF (termasuk kontainer). / Frame count in the DFF, including the container. */
    const val FRAME_COUNT = 33

    /** Bone dengan bobot (yang punya ID HAnim), tidak termasuk kontainer. / Weighted bones (with HAnim ID), excluding the container. */
    val WEIGHTED: List<RigBone> = BONES.filter { it.hanimId != null }
}
