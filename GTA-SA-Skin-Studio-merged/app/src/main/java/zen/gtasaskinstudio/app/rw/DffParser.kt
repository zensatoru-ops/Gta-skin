package zen.gtasaskinstudio.app.rw

import java.util.TreeSet

/**
 * Parser DFF defensif untuk subset yang terbukti di sampel (RW 3.6.0.3,
 * geometri non-native, trilist, Skin PLG format baru). Semua ukuran dan
 * jumlah elemen diperiksa terhadap sisa buffer dan [Limits] sebelum
 * dialokasikan; fitur yang tidak dikenal dilaporkan, tidak dibuang diam-diam.
 *
 * Defensive DFF parser for the subset proven on the sample. Every size and
 * element count is checked against the remaining buffer and [Limits] before
 * allocation; unknown features are reported, never silently dropped.
 */
class DffParser private constructor(private val data: ByteArray) {

    private val issues = ArrayList<Issue>()
    private val unsupported = TreeSet<Int>()
    private var multiMorphReported = false

    companion object {
        fun parse(data: ByteArray): DffParseResult = DffParser(data).run()
    }

    private fun run(): DffParseResult {
        return try {
            val model = parseFile()
            if (unsupported.isNotEmpty()) {
                issues.add(
                    Issue(
                        Severity.WARNING, IssueCode.UNSUPPORTED_CHUNKS, -1,
                        unsupported.joinToString(",") { "0x" + Integer.toHexString(it) },
                    )
                )
            }
            val report = DffValidator.validate(model)
            issues.addAll(report.issues)
            DffParseResult.Success(model, issues, report.skinStats)
        } catch (e: ParseFailure) {
            issues.add(e.issue)
            DffParseResult.Failure(issues)
        } catch (e: RuntimeException) {
            issues.add(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, -1, e.javaClass.simpleName))
            DffParseResult.Failure(issues)
        }
    }

    private fun fail(code: IssueCode, offset: Int, detail: String = ""): Nothing =
        throw ParseFailure(Issue(Severity.ERROR, code, offset, detail))

    // ---------------------------------------------------------------- file

    private fun parseFile(): DffModel {
        if (data.size > Limits.MAX_FILE_BYTES) {
            fail(IssueCode.FILE_TOO_LARGE, -1, data.size.toString())
        }
        val root = ByteReader(data, 0, data.size)
        var model: DffModel? = null
        var clumps = 0
        while (root.remaining >= 12) {
            val h = root.chunk()
            if (h.type == Rw.CLUMP) {
                clumps++
                if (clumps == 1) {
                    model = parseClump(h)
                } else if (clumps == 2) {
                    issues.add(Issue(Severity.WARNING, IssueCode.MULTI_CLUMP, h.start))
                }
            } else {
                unsupported.add(h.type)
            }
            root.pos = h.contentEnd
        }
        return model ?: fail(IssueCode.NO_CLUMP, 0)
    }

    // --------------------------------------------------------------- clump

    private fun parseClump(h: ChunkHeader): DffModel {
        val r = window(h)
        var frames: List<Frame> = emptyList()
        var geometries: List<Geometry> = emptyList()
        val atomics = ArrayList<Atomic>()
        while (r.remaining >= 12) {
            val c = r.chunk()
            when (c.type) {
                Rw.STRUCT -> Unit
                Rw.FRAME_LIST -> frames = parseFrameList(c)
                Rw.GEOMETRY_LIST -> geometries = parseGeometryList(c)
                Rw.ATOMIC -> atomics.add(parseAtomic(c))
                Rw.EXTENSION -> noteExtensionChildren(c)
                else -> unsupported.add(c.type)
            }
            r.pos = c.contentEnd
        }
        if (geometries.isEmpty()) fail(IssueCode.NO_GEOMETRY, h.start)
        if (geometries.size > 1) {
            issues.add(Issue(Severity.WARNING, IssueCode.MULTI_GEOMETRY, h.start, geometries.size.toString()))
        }
        return DffModel(h.version, frames, geometries, atomics)
    }

    private fun window(h: ChunkHeader): ByteReader = ByteReader(data, h.contentStart, h.contentEnd)

    /** Catat tipe chunk anak sebuah Extension sebagai "tidak didukung" tanpa mengurainya. */
    private fun noteExtensionChildren(ext: ChunkHeader, ignore: Set<Int> = emptySet()) {
        val r = window(ext)
        while (r.remaining >= 12) {
            val c = r.chunk()
            if (c.type !in ignore) unsupported.add(c.type)
            r.pos = c.contentEnd
        }
    }

    // -------------------------------------------------------------- frames

    private fun parseFrameList(h: ChunkHeader): List<Frame> {
        val r = window(h)
        val s = r.chunk()
        if (s.type != Rw.STRUCT) fail(IssueCode.UNEXPECTED_STRUCTURE, s.start, "frame list struct")
        val sr = window(s)
        val count = sr.i32()
        if (count < 0 || count > Limits.MAX_FRAMES) fail(IssueCode.TOO_MANY_ELEMENTS, s.start, "frames=$count")
        if (sr.remaining < ByteReader.multiplyOrFail(count, 56)) fail(IssueCode.TRUNCATED, s.start, "frames=$count")
        val frames = ArrayList<Frame>(count)
        for (i in 0 until count) {
            val rot = sr.floats(9)
            val pos = sr.floats(3)
            val parent = sr.i32()
            val flags = sr.i32()
            frames.add(Frame(i, parent, rot, pos, flags))
        }
        r.pos = s.contentEnd

        for (i in 0 until count) {
            if (r.remaining < 12) {
                issues.add(Issue(Severity.WARNING, IssueCode.UNEXPECTED_STRUCTURE, r.pos, "frame extensions missing from $i"))
                break
            }
            val e = r.chunk()
            if (e.type == Rw.EXTENSION) {
                parseFrameExtension(e, frames[i])
            } else {
                unsupported.add(e.type)
            }
            r.pos = e.contentEnd
        }
        return frames
    }

    private fun parseFrameExtension(e: ChunkHeader, frame: Frame) {
        val r = window(e)
        while (r.remaining >= 12) {
            val c = r.chunk()
            when (c.type) {
                Rw.FRAME_NAME -> frame.name = r.asciiOf(c)
                Rw.HANIM -> parseHAnim(c, frame)
                else -> unsupported.add(c.type)
            }
            r.pos = c.contentEnd
        }
    }

    private fun parseHAnim(c: ChunkHeader, frame: Frame) {
        val r = window(c)
        r.i32() // versi HAnim / HAnim version
        frame.boneId = r.i32()
        val nodes = r.i32()
        if (nodes < 0 || nodes > Limits.MAX_HANIM_NODES) {
            fail(IssueCode.TOO_MANY_ELEMENTS, c.start, "hanimNodes=$nodes")
        }
        frame.hanimNodeCount = nodes
        // Daftar node (flags, keyframe size, lalu 12 byte per node) tidak dibutuhkan
        // untuk inspeksi; hanya jumlahnya yang dipakai validator.
    }

    // ------------------------------------------------------------ geometry

    private fun parseGeometryList(h: ChunkHeader): List<Geometry> {
        val r = window(h)
        val s = r.chunk()
        if (s.type != Rw.STRUCT) fail(IssueCode.UNEXPECTED_STRUCTURE, s.start, "geometry list struct")
        val count = window(s).i32()
        if (count < 0 || count > Limits.MAX_GEOMETRIES) fail(IssueCode.TOO_MANY_ELEMENTS, s.start, "geometries=$count")
        r.pos = s.contentEnd
        val out = ArrayList<Geometry>(count)
        for (i in 0 until count) {
            val g = r.chunk()
            if (g.type != Rw.GEOMETRY) fail(IssueCode.UNEXPECTED_STRUCTURE, g.start, "expected geometry, type=${g.type}")
            out.add(parseGeometry(g))
            r.pos = g.contentEnd
        }
        return out
    }

    private class GeoStruct(
        val flags: Int,
        val vertexCount: Int,
        val triangleCount: Int,
        val positions: FloatArray,
        val normals: FloatArray?,
        val uv: FloatArray?,
        val hasPrelit: Boolean,
        val uvLayerCount: Int,
        val triIndices: IntArray,
        val triMaterial: IntArray,
    )

    private fun parseGeometry(h: ChunkHeader): Geometry {
        val r = window(h)
        var gs: GeoStruct? = null
        var materials: List<Material> = emptyList()
        var ext: ChunkHeader? = null
        while (r.remaining >= 12) {
            val c = r.chunk()
            when (c.type) {
                Rw.STRUCT -> if (gs == null) gs = parseGeometryStruct(c, h.version)
                Rw.MATERIAL_LIST -> materials = parseMaterialList(c, h.version)
                Rw.EXTENSION -> ext = c
                else -> unsupported.add(c.type)
            }
            r.pos = c.contentEnd
        }
        val g = gs ?: fail(IssueCode.UNEXPECTED_STRUCTURE, h.start, "geometry has no struct")

        var skin: Skin? = null
        var binMeshes: List<BinMesh>? = null
        if (ext != null) {
            val er = window(ext)
            while (er.remaining >= 12) {
                val c = er.chunk()
                when (c.type) {
                    Rw.BIN_MESH -> binMeshes = parseBinMesh(c)
                    Rw.SKIN -> skin = parseSkin(c, g.vertexCount)
                    else -> unsupported.add(c.type)
                }
                er.pos = c.contentEnd
            }
        }
        return Geometry(
            flags = g.flags,
            vertexCount = g.vertexCount,
            triangleCount = g.triangleCount,
            positions = g.positions,
            normals = g.normals,
            uv = g.uv,
            hasPrelit = g.hasPrelit,
            uvLayerCount = g.uvLayerCount,
            triIndices = g.triIndices,
            triMaterial = g.triMaterial,
            materials = materials,
            skin = skin,
            binMeshes = binMeshes,
        )
    }

    private fun parseGeometryStruct(c: ChunkHeader, version: Int): GeoStruct {
        val r = window(c)
        val flags = r.i32()
        val nTri = r.i32()
        val nVert = r.i32()
        val nMorph = r.i32()
        if (nTri < 0 || nTri > Limits.MAX_TRIANGLES) fail(IssueCode.TOO_MANY_ELEMENTS, c.start, "triangles=$nTri")
        if (nVert < 0 || nVert > Limits.MAX_VERTICES) fail(IssueCode.TOO_MANY_ELEMENTS, c.start, "vertices=$nVert")
        if (nMorph < 1 || nMorph > Limits.MAX_MORPH_TARGETS) fail(IssueCode.TOO_MANY_ELEMENTS, c.start, "morphTargets=$nMorph")
        if ((flags and Rw.GEO_NATIVE) != 0) fail(IssueCode.NATIVE_GEOMETRY_UNSUPPORTED, c.start, "flags=0x" + Integer.toHexString(flags))

        if (version < 0x34000) r.skip(12) // ambient/specular/diffuse

        val prelit = (flags and Rw.GEO_PRELIT) != 0
        if (prelit) r.skip(nVert * 4)

        var texCount = (flags and 0x00FF0000) ushr 16
        if (texCount == 0) {
            texCount = if ((flags and Rw.GEO_TEXTURED2) != 0) 2 else if ((flags and Rw.GEO_TEXTURED) != 0) 1 else 0
        }
        if (texCount > Limits.MAX_UV_LAYERS) fail(IssueCode.TOO_MANY_ELEMENTS, c.start, "uvLayers=$texCount")
        var uv: FloatArray? = null
        if (texCount > 0) {
            uv = r.floats(nVert * 2)
            r.skip((texCount - 1) * nVert * 8)
        }

        if (r.remaining < nTri * 8) fail(IssueCode.TRUNCATED, c.start, "triangles=$nTri")
        val idx = IntArray(nTri * 3)
        val mat = IntArray(nTri)
        for (i in 0 until nTri) {
            val v2 = r.u16()
            val v1 = r.u16()
            val m = r.u16()
            val v3 = r.u16()
            idx[i * 3] = v1
            idx[i * 3 + 1] = v2
            idx[i * 3 + 2] = v3
            mat[i] = m
        }

        var positions: FloatArray? = null
        var normals: FloatArray? = null
        for (m in 0 until nMorph) {
            r.skip(16) // bounding sphere
            val hasV = r.i32()
            val hasN = r.i32()
            if (hasV != 0) {
                if (m == 0) positions = r.floats(nVert * 3) else r.skip(nVert * 12)
            }
            if (hasN != 0) {
                if (m == 0) normals = r.floats(nVert * 3) else r.skip(nVert * 12)
            }
        }
        if (nMorph > 1 && !multiMorphReported) {
            multiMorphReported = true
            issues.add(Issue(Severity.WARNING, IssueCode.MULTI_MORPH, c.start, nMorph.toString()))
        }
        if (positions == null) fail(IssueCode.UNEXPECTED_STRUCTURE, c.start, "geometry has no vertices")
        if (r.remaining != 0) {
            issues.add(Issue(Severity.WARNING, IssueCode.TRAILING_BYTES, c.start, r.remaining.toString()))
        }
        return GeoStruct(flags, nVert, nTri, positions, normals, uv, prelit, texCount, idx, mat)
    }

    // ----------------------------------------------------------- materials

    private fun parseMaterialList(h: ChunkHeader, version: Int): List<Material> {
        val r = window(h)
        val s = r.chunk()
        if (s.type != Rw.STRUCT) fail(IssueCode.UNEXPECTED_STRUCTURE, s.start, "material list struct")
        val sr = window(s)
        val count = sr.i32()
        if (count < 0 || count > Limits.MAX_MATERIALS) fail(IssueCode.TOO_MANY_ELEMENTS, s.start, "materials=$count")
        if (sr.remaining < count * 4) fail(IssueCode.TRUNCATED, s.start, "materials=$count")
        val instance = IntArray(count) { sr.i32() }
        r.pos = s.contentEnd

        val out = ArrayList<Material>(count)
        for (i in 0 until count) {
            val inst = instance[i]
            if (inst == -1) {
                val m = r.chunk()
                if (m.type != Rw.MATERIAL) fail(IssueCode.UNEXPECTED_STRUCTURE, m.start, "expected material, type=${m.type}")
                out.add(parseMaterial(m, version))
                r.pos = m.contentEnd
            } else if (inst in 0 until out.size) {
                out.add(out[inst])
            } else {
                issues.add(Issue(Severity.WARNING, IssueCode.UNEXPECTED_STRUCTURE, h.start, "material instance $inst at $i"))
                out.add(Material(-1, null, null))
            }
        }
        return out
    }

    private fun parseMaterial(h: ChunkHeader, version: Int): Material {
        val r = window(h)
        var rgba = -1
        var texName: String? = null
        var maskName: String? = null
        while (r.remaining >= 12) {
            val c = r.chunk()
            when (c.type) {
                Rw.STRUCT -> {
                    val sr = window(c)
                    sr.i32() // flags
                    rgba = sr.i32()
                }
                Rw.TEXTURE -> {
                    val tr = window(c)
                    var strings = 0
                    while (tr.remaining >= 12) {
                        val t = tr.chunk()
                        if (t.type == Rw.STRING) {
                            if (strings == 0) texName = tr.asciiOf(t) else if (strings == 1) maskName = tr.asciiOf(t)
                            strings++
                        } else if (t.type == Rw.EXTENSION) {
                            noteExtensionChildren(t)
                        }
                        tr.pos = t.contentEnd
                    }
                }
                Rw.EXTENSION -> noteExtensionChildren(c)
                else -> unsupported.add(c.type)
            }
            r.pos = c.contentEnd
        }
        return Material(rgba, texName, maskName)
    }

    // ------------------------------------------------------------- binmesh

    /** Hanya trilist (flags = 0); tristrip dilaporkan dan diabaikan. */
    private fun parseBinMesh(h: ChunkHeader): List<BinMesh>? {
        val r = window(h)
        val flags = r.i32()
        val count = r.i32()
        val total = r.i32()
        if (flags != 0) {
            issues.add(Issue(Severity.WARNING, IssueCode.UNSUPPORTED_CHUNKS, h.start, "binmesh flags=$flags"))
            return null
        }
        if (count < 0 || count > Limits.MAX_MATERIALS) fail(IssueCode.TOO_MANY_ELEMENTS, h.start, "binMeshes=$count")
        if (total < 0 || total > Limits.MAX_TRIANGLES * 3) fail(IssueCode.TOO_MANY_ELEMENTS, h.start, "binMeshIndices=$total")
        val out = ArrayList<BinMesh>(count)
        var sum = 0L
        for (i in 0 until count) {
            val n = r.i32()
            val mat = r.i32()
            if (n < 0 || n > total || r.remaining < n * 4) fail(IssueCode.TRUNCATED, h.start, "binMesh=$i indices=$n")
            sum += n
            if (sum > total) fail(IssueCode.UNEXPECTED_STRUCTURE, h.start, "binMesh index total exceeded")
            out.add(BinMesh(mat, IntArray(n) { r.i32() }))
        }
        if (sum != total.toLong()) {
            issues.add(Issue(Severity.WARNING, IssueCode.UNEXPECTED_STRUCTURE, h.start, "binMesh total=$total sum=$sum"))
        }
        return out
    }

    // ---------------------------------------------------------------- skin

    private fun parseSkin(h: ChunkHeader, vertexCount: Int): Skin? {
        val r = window(h)
        val numBones = r.u8()
        val numUsed = r.u8()
        val maxW = r.u8()
        r.u8()
        val expected = 4L + numUsed + vertexCount * 4L + vertexCount * 16L + numBones * 64L + 12L
        if (expected != h.size.toLong()) {
            issues.add(
                Issue(
                    Severity.WARNING, IssueCode.SKIN_FORMAT_UNKNOWN, h.start,
                    "size=${h.size} expected=$expected bones=$numBones used=$numUsed",
                )
            )
            return null
        }
        val used = IntArray(numUsed) { r.u8() }
        val indices = r.bytes(vertexCount * 4)
        val weights = r.floats(vertexCount * 4)
        val matrices = r.floats(numBones * 16)
        return Skin(numBones, maxW, used, indices, weights, matrices)
    }

    // -------------------------------------------------------------- atomic

    private fun parseAtomic(h: ChunkHeader): Atomic {
        val r = window(h)
        var frame = -1
        var geom = -1
        var flags = 0
        val benign = setOf(Rw.RIGHT_TO_RENDER, Rw.MATFX, Rw.PIPELINE_SET, Rw.SKY_GFX)
        while (r.remaining >= 12) {
            val c = r.chunk()
            when (c.type) {
                Rw.STRUCT -> {
                    val sr = window(c)
                    frame = sr.i32()
                    geom = sr.i32()
                    if (sr.remaining >= 4) flags = sr.i32()
                }
                Rw.EXTENSION -> noteExtensionChildren(c, benign)
                else -> unsupported.add(c.type)
            }
            r.pos = c.contentEnd
        }
        return Atomic(frame, geom, flags)
    }
}
