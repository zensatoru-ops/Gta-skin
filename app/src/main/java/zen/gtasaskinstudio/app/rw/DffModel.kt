package zen.gtasaskinstudio.app.rw

/**
 * Model internal hasil parse DFF — sengaja tidak mengikuti layout byte
 * (lihat blueprint, bagian scene-model). Hanya subset yang sudah terbukti
 * di sampel: geometri non-native, trilist, Skin PLG baru.
 *
 * Internal model produced by the DFF parser — intentionally not tied to the
 * byte layout. Only the subset proven on the sample: non-native geometry,
 * triangle lists, new-format Skin PLG.
 */
class Frame(
    val index: Int,
    /** -1 = akar / root. */
    val parent: Int,
    /** Matriks rotasi 3x3, baris = right, up, at (9 float). */
    val rotation: FloatArray,
    val position: FloatArray,
    val flags: Int,
) {
    var name: String? = null

    /** ID node HAnim bila frame ini bertanda bone / HAnim node ID if this frame is a bone. */
    var boneId: Int? = null

    /** Jumlah node pada hierarki HAnim yang dimulai di frame ini (0 jika bukan akar hierarki). */
    var hanimNodeCount: Int = 0

    /** Urutan ID node HAnim pada akar hierarki; indeks = indeks bone di Skin PLG. Kosong bila tidak terbaca. */
    var hanimNodeIds: IntArray = IntArray(0)
}

class Material(
    /** Warna RGBA, byte 0 = R ... byte 3 = A, dikemas di Int little-endian. */
    val rgba: Int,
    val textureName: String?,
    val maskName: String?,
)

class Skin(
    val numBones: Int,
    val maxWeightsPerVertex: Int,
    val usedBones: IntArray,
    /** 4 byte per vertex. */
    val boneIndices: ByteArray,
    /** 4 float per vertex. */
    val weights: FloatArray,
    /** numBones * 16 float, disimpan apa adanya. */
    val inverseMatrices: FloatArray,
)

class Geometry(
    val flags: Int,
    val vertexCount: Int,
    val triangleCount: Int,
    /** 3 float per vertex. */
    val positions: FloatArray,
    /** 3 float per vertex, atau null bila tidak ada. */
    val normals: FloatArray?,
    /** Lapisan UV pertama saja, 2 float per vertex, atau null. */
    val uv: FloatArray?,
    val hasPrelit: Boolean,
    val uvLayerCount: Int,
    /** 3 indeks vertex per segitiga (a, b, c). */
    val triIndices: IntArray,
    val triMaterial: IntArray,
    val materials: List<Material>,
    val skin: Skin?,
    /**
     * Grup Bin Mesh (trilist) bila ada dan valid; null bila tidak ada atau
     * berformat tristrip (belum didukung). Pada sampel, id material di
     * triangle semua 0, sehingga Bin Mesh-lah yang menentukan material.
     */
    val binMeshes: List<BinMesh>?,
)

/** Satu grup Bin Mesh: indeks trilist untuk satu material. / One Bin Mesh group: trilist indices for one material. */
class BinMesh(val materialIndex: Int, val indices: IntArray)

class Atomic(val frame: Int, val geometry: Int, val flags: Int)

class DffModel(
    val rwVersion: Int,
    val frames: List<Frame>,
    val geometries: List<Geometry>,
    val atomics: List<Atomic>,
)

sealed class DffParseResult {
    /** [issues] sudah termasuk hasil validasi; [skinStats] kunci = indeks geometri. */
    data class Success(
        val model: DffModel,
        val issues: List<Issue>,
        val skinStats: Map<Int, SkinStats>,
    ) : DffParseResult()
    data class Failure(val issues: List<Issue>) : DffParseResult()
}
