package zen.gtasaskinstudio.app.gl

import android.graphics.Color
import android.opengl.GLES20
import android.util.Log
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.scene.Picking
import zen.gtasaskinstudio.app.scene.SceneBuffers
import zen.gtasaskinstudio.app.scene.TextureSet

/**
 * Renderer OpenGL ES 2.0 untuk satu model. Data model dibangun di CPU oleh
 * SceneBuilder; kelas ini meng-upload dan menggambar. Tekstur (TXD) didekode
 * per material saat pertama dipakai, di thread GL, lalu disimpan sebagai texture GL.
 *
 * OpenGL ES 2.0 renderer for one model. Model data is built on the CPU by
 * SceneBuilder; this class uploads and draws. TXD textures are decoded per
 * material on first use, on the GL thread, and kept as GL textures.
 */
class ModelRenderer : GLSurfaceView.Renderer {

    // Diatur dari thread UI, dibaca di thread GL. / Set on the UI thread, read on the GL thread.
    @Volatile var scene: SceneBuffers? = null
    @Volatile var wireframe = false
    @Volatile var materialColors = true
    @Volatile var showSkeleton = false
    @Volatile var yaw = 25f
    @Volatile var pitch = 10f
    @Volatile var distanceFactor = 2.6f
    @Volatile var panX = 0f
    @Volatile var panY = 0f
    @Volatile var showTextures = true
    /** Balik V (1 - v) sebelum sampling tekstur; default untuk FBX/OBJ. / Flip V (1 - v) before sampling; default for FBX/OBJ. */
    @Volatile var flipV = false
    /** Sumber tekstur dari TXD; diganti dari thread UI. / TXD texture source; replaced from the UI thread. */
    @Volatile var texturesSource: List<TxdTexture>? = null
    private var texturesApplied: List<TxdTexture>? = null
    private val glTextures = HashMap<String, Int>()
    /** Nama material yang sudah dicatat gagal (agar tidak spam log). / Material names already logged as failed. */
    private val missingLogged = HashSet<String>()
    /** Tekstur per slot material (indeks material), dipasang dari UI; menang atas nama TXD. / Per-material-slot textures attached from the UI; they win over TXD names. */
    @Volatile var slotTexturesSource: Map<Int, TxdTexture> = emptyMap()
    private var slotsApplied: Map<Int, TxdTexture>? = null
    /** Salinan MVP frame terakhir untuk pemilihan sentuh. / Copy of the last frame's MVP for touch picking. */
    @Volatile private var lastMvp = FloatArray(16)

    private var litProgram = 0
    private var flatProgram = 0

    private var vertexBuf = 0
    private var uvBuf = 0
    private var indexBuf = 0
    private var wireBuf = 0
    private var skelLineBuf = 0
    private var skelPointBuf = 0
    private var uploaded: SceneBuffers? = null
    private var skelLineCount = 0
    private var skelPointCount = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val pan = FloatArray(16)
    private val viewPanned = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)
    private var aspect = 1f
    private var viewW = 0
    private var viewH = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.08f, 0.13f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        litProgram = buildProgram(LIT_VERTEX, LIT_FRAGMENT)
        flatProgram = buildProgram(FLAT_VERTEX, FLAT_FRAGMENT)
        // Konteks GL baru: buffer lama tidak berlaku lagi. / New GL context: old buffers are gone.
        uploaded = null
        vertexBuf = 0; indexBuf = 0; wireBuf = 0; skelLineBuf = 0; skelPointBuf = 0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height == 0) 1f else width.toFloat() / height
    }

    override fun onDrawFrame(gl: GL10?) {
        // Pasang ulang viewport tiap frame: beberapa driver (Adreno di Android 10) mengembalikannya setelah konteks diganti.
        // Re-apply the viewport every frame: some drivers (Adreno on Android 10) reset it after a context switch.
        if (viewW > 0 && viewH > 0) GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = scene ?: return
        if (uploaded !== s) upload(s)
        if (texturesSource !== texturesApplied) {
            deleteTextures()
            texturesApplied = texturesSource
        }
        if (slotTexturesSource !== slotsApplied) {
            deleteTextures()
            slotsApplied = slotTexturesSource
        }

        val r = s.radius
        val d = distanceFactor * r
        val p = Math.toRadians(pitch.toDouble())
        val y = Math.toRadians(yaw.toDouble())
        val ex = (d * cos(p) * sin(y)).toFloat()
        val ey = (d * sin(p)).toFloat()
        val ez = (d * cos(p) * cos(y)).toFloat()

        Matrix.perspectiveM(proj, 0, 45f, aspect, r * 0.05f, r * 60f)
        Matrix.setLookAtM(view, 0, ex, ey, ez, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(pan, 0)
        Matrix.translateM(pan, 0, panX * r, panY * r, 0f)
        Matrix.multiplyMM(viewPanned, 0, pan, 0, view, 0)

        // GTA memakai sumbu Z ke atas; GL kita Y ke atas. / GTA is Z-up; our GL view is Y-up.
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -90f, 1f, 0f, 0f)
        Matrix.translateM(model, 0, -s.center[0], -s.center[1], -s.center[2])
        Matrix.multiplyMM(viewModel, 0, viewPanned, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, viewModel, 0)
        lastMvp = mvp.copyOf()

        if (wireframe) {
            drawSolid(s, flatShade = true)
            drawWire(s)
        } else {
            drawSolid(s, flatShade = false)
        }
        if (showSkeleton) drawSkeleton()
    }

    private fun drawSolid(s: SceneBuffers, flatShade: Boolean) {
        GLES20.glUseProgram(litProgram)
        val aPos = GLES20.glGetAttribLocation(litProgram, "aPos")
        val aNormal = GLES20.glGetAttribLocation(litProgram, "aNormal")
        val aUv = GLES20.glGetAttribLocation(litProgram, "aUv")
        val uMvp = GLES20.glGetUniformLocation(litProgram, "uMvp")
        val uNormal = GLES20.glGetUniformLocation(litProgram, "uNormalMat")
        val uColor = GLES20.glGetUniformLocation(litProgram, "uColor")
        val uTex = GLES20.glGetUniformLocation(litProgram, "uTex")
        val uUseTex = GLES20.glGetUniformLocation(litProgram, "uUseTex")
        val uFlipV = GLES20.glGetUniformLocation(litProgram, "uFlipV")
        GLES20.glUniform1f(uFlipV, if (flipV) 1f else 0f)

        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uNormal, 1, false, viewModel, 0)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, uvBuf)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuf)

        if (flatShade) {
            // Lapisan gelap di bawah garis wireframe, digeser sedikit agar tidak z-fighting.
            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glPolygonOffset(1f, 1f)
        }
        for (draw in s.draws) {
            val c = if (flatShade) DARK else if (materialColors) paletteColor(draw.materialIndex) else NEUTRAL
            GLES20.glUniform4f(uColor, c[0], c[1], c[2], 1f)
            val texId = if (!flatShade && showTextures) textureForDraw(s, draw.materialIndex) else 0
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1f(uUseTex, if (texId != 0) 1f else 0f)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, draw.count, GLES20.GL_UNSIGNED_SHORT, draw.start * 2)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        if (flatShade) GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aUv)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawWire(s: SceneBuffers) {
        GLES20.glUseProgram(flatProgram)
        val aPos = GLES20.glGetAttribLocation(flatProgram, "aPos")
        val uMvp = GLES20.glGetUniformLocation(flatProgram, "uMvp")
        val uColor = GLES20.glGetUniformLocation(flatProgram, "uColor")
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, 0.75f, 0.88f, 1f, 1f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, wireBuf)
        GLES20.glDrawElements(GLES20.GL_LINES, s.wireIndices.size, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawSkeleton() {
        if (skelLineCount == 0 && skelPointCount == 0) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(flatProgram)
        val aPos = GLES20.glGetAttribLocation(flatProgram, "aPos")
        val uMvp = GLES20.glGetUniformLocation(flatProgram, "uMvp")
        val uColor = GLES20.glGetUniformLocation(flatProgram, "uColor")
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, 0.91f, 0.71f, 0.30f, 1f)
        GLES20.glEnableVertexAttribArray(aPos)

        if (skelLineCount > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skelLineBuf)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
            GLES20.glLineWidth(3f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, skelLineCount)
        }
        if (skelPointCount > 0) {
            GLES20.glUniform4f(uColor, 1f, 0.95f, 0.6f, 1f)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skelPointBuf)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, skelPointCount)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    // ------------------------------------------------------------- upload

    private fun upload(s: SceneBuffers) {
        val ids = IntArray(6)
        val old = intArrayOf(vertexBuf, uvBuf, indexBuf, wireBuf, skelLineBuf, skelPointBuf).filter { it != 0 }.toIntArray()
        if (old.isNotEmpty()) GLES20.glDeleteBuffers(old.size, old, 0)

        GLES20.glGenBuffers(6, ids, 0)
        vertexBuf = ids[0]; uvBuf = ids[1]; indexBuf = ids[2]; wireBuf = ids[3]; skelLineBuf = ids[4]; skelPointBuf = ids[5]

        bufferFloats(vertexBuf, s.vertices)
        bufferFloats(uvBuf, s.uvs)
        bufferShorts(indexBuf, s.indices)
        bufferShorts(wireBuf, s.wireIndices)
        bufferFloats(skelLineBuf, s.skeletonLines)
        bufferFloats(skelPointBuf, s.framePositions)
        skelLineCount = s.skeletonLines.size / 3
        skelPointCount = s.framePositions.size / 3
        uploaded = s
    }

    private fun bufferFloats(id: Int, data: FloatArray) {
        val fb: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun bufferShorts(id: Int, data: ShortArray) {
        val sb: ShortBuffer = ByteBuffer.allocateDirect(data.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        sb.put(data).position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, id)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, data.size * 2, sb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /** Indeks material di bawah titik sentuh (NDC), memakai MVP frame terakhir. Dipanggil dari thread UI. / Material index under a touch point (NDC), using the last frame's MVP. Called from the UI thread. */
    fun pickMaterial(nx: Float, ny: Float): Int? {
        val s = scene ?: return null
        return Picking.pickMaterial(s, lastMvp, nx, ny)
    }

    // ------------------------------------------------------------ textures

    /** Pilih tekstur untuk satu draw: slot yang dipasang lebih dulu, lalu nama dari TXD. / Texture for one draw: attached slot first, then the TXD name. */
    private fun textureForDraw(s: SceneBuffers, materialIndex: Int): Int {
        slotTexturesSource[materialIndex]?.let { return uploadTexture("slot:$materialIndex", it) }
        return textureFor(s.materialNames.getOrNull(materialIndex))
    }

    /** ID texture GL untuk nama material, 0 bila tidak ada. Dekode dan upload saat pertama dibutuhkan. */
    private fun textureFor(materialName: String?): Int {
        val src = texturesApplied ?: return 0
        val key = materialName?.lowercase() ?: return 0
        glTextures[key]?.let { return it }
        if (key in missingLogged) return 0
        val t = TextureSet.find(src, materialName)
        if (t == null) {
            // Dicatat sekali per nama, supaya kasus "TXD tidak cocok" terlihat di logcat.
            // Logged once per name so "TXD does not match" shows up in logcat.
            missingLogged.add(key)
            Log.w(TAG, "no TXD texture for material '$materialName' (TXD has ${src.size} textures)")
            return 0
        }
        return uploadTexture(key, t)
    }

    /** Unggah satu tekstur ke GL dan simpan di cache [key]. / Upload one texture to GL and cache it under [key]. */
    private fun uploadTexture(key: String, t: TxdTexture): Int {
        glTextures[key]?.let { return it }
        if (key in missingLogged) return 0
        val rgba = TextureSet.rgba(t)
        // Buffer native-order langsung, sama seperti buffer vertex. / Direct native-order buffer, like the vertex buffers.
        val pixels = ByteBuffer.allocateDirect(rgba.size).order(ByteOrder.nativeOrder())
        pixels.put(rgba).position(0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, t.width, t.height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels,
        )
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "glTexImage2D '${t.name}' ${t.width}x${t.height} failed: 0x${Integer.toHexString(err)}")
            GLES20.glDeleteTextures(1, ids, 0)
            missingLogged.add(key)
            return 0
        }
        glTextures[key] = ids[0]
        return ids[0]
    }

    private fun deleteTextures() {
        if (glTextures.isNotEmpty()) {
            val ids = glTextures.values.toIntArray()
            GLES20.glDeleteTextures(ids.size, ids, 0)
            glTextures.clear()
        }
        missingLogged.clear()
    }

    // ------------------------------------------------------------ shaders

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, v)
        GLES20.glAttachShader(program, f)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("GL link failed: $log")
        }
        return program
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("GL shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val TAG = "GtaSkinStudio/GL"
        private val NEUTRAL = floatArrayOf(0.78f, 0.78f, 0.82f)
        private val DARK = floatArrayOf(0.10f, 0.11f, 0.17f)

        /** Warna deterministik per indeks material (sudut emas pada hue). */
        fun paletteColor(index: Int): FloatArray {
            val hue = (index * 137.508f) % 360f
            val rgb = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.92f))
            return floatArrayOf(Color.red(rgb) / 255f, Color.green(rgb) / 255f, Color.blue(rgb) / 255f)
        }

        private const val LIT_VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uNormalMat;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec2 aUv;
            varying vec3 vNormal;
            varying vec2 vUv;
            void main() {
                vNormal = (uNormalMat * vec4(aNormal, 0.0)).xyz;
                vUv = aUv;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """
        private const val LIT_FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            uniform sampler2D uTex;
            uniform float uUseTex;
            uniform float uFlipV;
            varying vec3 vNormal;
            varying vec2 vUv;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 light = normalize(vec3(0.3, 0.5, 0.8));
                float d = abs(dot(n, light));
                vec3 base = uColor.rgb;
                if (uUseTex > 0.5) {
                    // GTA memakai UV di luar 0..1 (pengulangan). fract() meniru WRAP tanpa
                    // bergantung pada tekstur power-of-two (GLES 2.0 hanya mengizinkan REPEAT pada POT).
                    // GTA tiles UVs outside 0..1. fract() emulates WRAP without requiring power-of-two textures.
                    // FBX/OBJ memakai V dari bawah; gambar kita dari atas. uFlipV = 1 membalik V.
                    // FBX/OBJ use V from the bottom; our images are top-first. uFlipV = 1 flips V.
                    vec2 uv = vec2(vUv.x, mix(vUv.y, 1.0 - vUv.y, uFlipV));
                    vec4 t = texture2D(uTex, fract(uv));
                    if (t.a < 0.5) discard; // alpha cutout DXT1 / DXT3 / DXT5
                    base = t.rgb;
                }
                gl_FragColor = vec4(base * (0.30 + 0.70 * d), 1.0);
            }
        """
        private const val FLAT_VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                gl_PointSize = 9.0;
            }
        """
        private const val FLAT_FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
