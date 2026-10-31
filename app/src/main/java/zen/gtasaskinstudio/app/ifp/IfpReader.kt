package zen.gtasaskinstudio.app.ifp

import zen.gtasaskinstudio.app.rig.GtaStandardRig

/**
 * Isi keyframe satu bone: waktu dalam detik, rotasi w,x,y,z per frame, dan translasi x,y,z per frame (hanya tipe 4, root).
 * Keyframe contents of one bone: times in seconds, rotation w,x,y,z per frame, and translation x,y,z per frame (type 4 only, root).
 */
class IfpKeys(val times: FloatArray, val rotations: FloatArray, val translations: FloatArray?)

/** Satu urutan keyframe untuk satu bone. / One keyframe sequence for one bone. */
class IfpSequence(
    val name: String,
    val frameType: Int,
    val frameCount: Int,
    val boneId: Long,
    /** Null bila diparse tanpa isi keyframe. / Null when parsed without keyframe contents. */
    val keys: IfpKeys? = null,
)

class IfpAnimation(val name: String, val sequences: List<IfpSequence>)

class IfpFile(val name: String, val animations: List<IfpAnimation>)

sealed class IfpResult {
    class Success(val file: IfpFile) : IfpResult()
    class Failure(val reason: String) : IfpResult()
}

/**
 * Pembaca IFP format ANP3 (animasi ped GTA SA). Hanya membaca nama animasi, nama bone, ID bone, dan jumlah
 * keyframe; isi keyframe dilewati. Dipakai untuk memvalidasi rig terhadap ped.ifp milik pengguna, tidak ada
 * berkas IFP yang dibundel.
 *
 * Reader for the ANP3 IFP format (GTA SA ped animations). It reads only animation names, bone names, bone IDs and
 * keyframe counts; keyframe contents are skipped. It validates a rig against the user's own ped.ifp; no IFP file is bundled.
 *
 * Tata letak / layout (little-endian): "ANP3", u32 size, char[24] name, u32 animCount; per animation: char[24] name,
 * u32 objectCount, u32 frameBytes, u32 unknown; per object: char[24] name, u32 frameType, u32 frameCount, u32 boneId,
 * then frameCount frames: 10 bytes (type 3, rotation) or 16 bytes (type 4, root with translation).
 */
object IfpReader {

    private const val HEADER = 36
    private const val ANIM_HEADER = 36
    private const val OBJECT_HEADER = 36

    private fun frameBytes(type: Int): Int = when (type) {
        3 -> 10
        4 -> 16
        else -> -1
    }

    /** Satuan waktu ANP3: 1/60 detik. / ANP3 time unit: 1/60 second. */
    private const val TICKS_PER_SECOND = 60f
    private const val ROT_SCALE = 4096f
    private const val POS_SCALE = 1024f

    /**
     * @param withKeys true membaca isi keyframe (waktu, rotasi, translasi); false hanya melewatinya (lebih hemat).
     *                 true reads keyframe contents (time, rotation, translation); false only skips them (cheaper).
     */
    fun parse(bytes: ByteArray, withKeys: Boolean = false): IfpResult {
        if (bytes.size < HEADER) return IfpResult.Failure("too short")
        if (bytes[0] != 'A'.code.toByte() || bytes[1] != 'N'.code.toByte() ||
            bytes[2] != 'P'.code.toByte() || bytes[3] != '3'.code.toByte()
        ) return IfpResult.Failure("not ANP3")
        val name = cstr(bytes, 8)
        val animCount = u32(bytes, 32)
        // Setiap animasi butuh minimal satu header; tolak hitungan yang mustahil. / Each animation needs at least a header; reject impossible counts.
        if (animCount < 0 || animCount.toLong() * ANIM_HEADER > bytes.size - HEADER) return IfpResult.Failure("bad animation count")
        var off = HEADER
        val anims = ArrayList<IfpAnimation>()
        for (a in 0 until animCount) {
            if (off + ANIM_HEADER > bytes.size) return IfpResult.Failure("truncated animation header")
            val aName = cstr(bytes, off)
            val objCount = u32(bytes, off + 24)
            off += ANIM_HEADER
            if (objCount < 0 || objCount.toLong() * OBJECT_HEADER > bytes.size - off) return IfpResult.Failure("bad object count in $aName")
            val seqs = ArrayList<IfpSequence>()
            for (o in 0 until objCount) {
                if (off + OBJECT_HEADER > bytes.size) return IfpResult.Failure("truncated object header")
                val oName = cstr(bytes, off)
                val type = u32(bytes, off + 24)
                val frames = u32(bytes, off + 28)
                val boneId = u32(bytes, off + 32).toLong() and 0xFFFFFFFFL
                off += OBJECT_HEADER
                val fb = frameBytes(type)
                if (fb < 0) return IfpResult.Failure("unknown frame type $type")
                if (frames < 0 || frames.toLong() * fb > bytes.size - off) return IfpResult.Failure("truncated frames in $aName/$oName")
                val keys = if (withKeys) readKeys(bytes, off, type, frames) else null
                off += frames * fb
                seqs.add(IfpSequence(oName, type, frames, boneId, keys))
            }
            anims.add(IfpAnimation(aName, seqs))
        }
        return IfpResult.Success(IfpFile(name, anims))
    }

    private fun readKeys(b: ByteArray, start: Int, type: Int, frames: Int): IfpKeys {
        val fb = frameBytes(type)
        val times = FloatArray(frames)
        val rot = FloatArray(frames * 4)
        val tr = if (type == 4) FloatArray(frames * 3) else null
        for (i in 0 until frames) {
            val o = start + i * fb
            // Urutan di berkas x,y,z,w (int16/4096), lalu waktu (uint16); tipe 4 menambah x,y,z translasi (int16/1024).
            // On disk: x,y,z,w (int16/4096), then time (uint16); type 4 adds x,y,z translation (int16/1024).
            rot[i * 4] = s16(b, o + 6) / ROT_SCALE
            rot[i * 4 + 1] = s16(b, o) / ROT_SCALE
            rot[i * 4 + 2] = s16(b, o + 2) / ROT_SCALE
            rot[i * 4 + 3] = s16(b, o + 4) / ROT_SCALE
            times[i] = u16(b, o + 8) / TICKS_PER_SECOND
            if (tr != null) {
                tr[i * 3] = s16(b, o + 10) / POS_SCALE
                tr[i * 3 + 1] = s16(b, o + 12) / POS_SCALE
                tr[i * 3 + 2] = s16(b, o + 14) / POS_SCALE
            }
        }
        return IfpKeys(times, rot, tr)
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun s16(b: ByteArray, o: Int): Int = u16(b, o).toShort().toInt()

    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun cstr(b: ByteArray, o: Int): String {
        var n = 0
        while (n < 24 && b[o + n].toInt() != 0) n++
        return String(b, o, n, Charsets.ISO_8859_1)
    }
}

/** Pemakaian satu bone template di IFP. / Usage of one template bone in the IFP. */
class BoneUsage(val name: String, val hanimId: Int, val exact: Int, val sameIdOtherName: Int)

class IfpRigReport(val bones: List<BoneUsage>, val unknownIds: Set<Long>) {
    /** Semua bone template dipakai dengan nama DAN ID yang persis sama. / Every template bone is used with the exact name AND ID. */
    val allMatch: Boolean get() = bones.all { it.exact > 0 }
    val missing: List<BoneUsage> get() = bones.filter { it.exact == 0 }
}

object IfpRigCheck {

    /** ID tanpa bone template yang wajar: penanda tanpa ID (0xFFFFFFFF) dan ujung jari (304, 305). */
    private val IGNORED_IDS = setOf(0xFFFFFFFFL, 304L, 305L)

    fun check(file: IfpFile): IfpRigReport {
        val exact = HashMap<Pair<String, Long>, Int>()
        val byId = HashMap<Long, Int>()
        val seenIds = HashSet<Long>()
        for (anim in file.animations) for (s in anim.sequences) {
            exact.merge(s.name to s.boneId, 1, Int::plus)
            byId.merge(s.boneId, 1, Int::plus)
            seenIds.add(s.boneId)
        }
        val templateIds = GtaStandardRig.WEIGHTED.mapNotNull { it.hanimId?.toLong() }.toSet()
        val bones = GtaStandardRig.WEIGHTED.map { b ->
            val id = b.hanimId!!.toLong()
            val ex = exact[b.name to id] ?: 0
            BoneUsage(b.name, b.hanimId, ex, (byId[id] ?: 0) - ex)
        }
        return IfpRigReport(bones, seenIds - templateIds - IGNORED_IDS)
    }
}
