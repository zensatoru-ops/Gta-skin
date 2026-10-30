package zen.gtasaskinstudio.app.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.scene.SceneBuffers
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * GLSurfaceView dengan kontrol sentuh: 1 jari = putar, 2 jari = geser,
 * cubit = zoom. Rendering hanya saat ada perubahan (RENDERMODE_WHEN_DIRTY).
 */
@SuppressLint("ViewConstructor")
class ModelGLView(context: Context) : GLSurfaceView(context) {

    val modelRenderer = ModelRenderer()

    /** Penanda terakhir tombol reset yang sudah diproses (dipakai host Compose). */
    var lastResetTick = 0

    private var lastX = 0f
    private var lastY = 0f
    private var lastCount = 0

    // Deteksi ketuk: geser atau cubit membatalkan ketuk. / Tap detection: a drag or pinch cancels the tap.
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    /** Dipanggil saat ketuk mengenai material; argumennya indeks material. / Called on a tap that hits a material; the argument is the material index. */
    var onMaterialPick: ((Int) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                modelRenderer.distanceFactor =
                    (modelRenderer.distanceFactor / detector.scaleFactor).coerceIn(0.8f, 12f)
                requestRender()
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(modelRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // Ukuran berubah (panel bawah berubah tinggi): minta gambar ulang agar tidak tersisa frame lama.
    // Size changed (the bottom panel changed height): request a redraw so no stale frame remains.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestRender()
    }

    fun setScene(scene: SceneBuffers?) {
        if (modelRenderer.scene !== scene) {
            modelRenderer.scene = scene
            requestRender()
        }
    }

    fun setDisplay(wireframe: Boolean, materialColors: Boolean, showSkeleton: Boolean, showTextures: Boolean) {
        if (modelRenderer.wireframe != wireframe ||
            modelRenderer.materialColors != materialColors ||
            modelRenderer.showSkeleton != showSkeleton ||
            modelRenderer.showTextures != showTextures
        ) {
            modelRenderer.wireframe = wireframe
            modelRenderer.materialColors = materialColors
            modelRenderer.showSkeleton = showSkeleton
            modelRenderer.showTextures = showTextures
            requestRender()
        }
    }

    /** Sumber TXD; hanya diganti bila objeknya berbeda. / TXD source; replaced only when the object changes. */
    fun setTextures(textures: List<TxdTexture>?) {
        if (modelRenderer.texturesSource !== textures) {
            modelRenderer.texturesSource = textures
            requestRender()
        }
    }

    /** Balik V tekstur (untuk FBX/OBJ). / Flip texture V (for FBX/OBJ). */
    fun setFlipV(flip: Boolean) {
        if (modelRenderer.flipV != flip) {
            modelRenderer.flipV = flip
            requestRender()
        }
    }

    /** Tekstur per slot material; hanya diganti bila objeknya berbeda. / Per-slot textures; replaced only when the object changes. */
    fun setSlotTextures(slots: Map<Int, TxdTexture>) {
        if (modelRenderer.slotTexturesSource !== slots) {
            modelRenderer.slotTexturesSource = slots
            requestRender()
        }
    }

    fun resetCamera() {
        modelRenderer.yaw = 25f
        modelRenderer.pitch = 10f
        modelRenderer.distanceFactor = 2.6f
        modelRenderer.panX = 0f
        modelRenderer.panY = 0f
        requestRender()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)

        var cx = 0f
        var cy = 0f
        for (i in 0 until e.pointerCount) {
            cx += e.getX(i)
            cy += e.getY(i)
        }
        cx /= e.pointerCount
        cy /= e.pointerCount

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = cx; lastY = cy; lastCount = e.pointerCount
                downX = e.x; downY = e.y; moved = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Jumlah jari berubah: mulai ulang agar tidak melompat.
                lastX = cx; lastY = cy; lastCount = -1
                moved = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.x - downX, e.y - downY) > touchSlop) moved = true
                if (lastCount != e.pointerCount) {
                    lastX = cx; lastY = cy; lastCount = e.pointerCount
                } else if (!scaleDetector.isInProgress) {
                    val dx = cx - lastX
                    val dy = cy - lastY
                    lastX = cx; lastY = cy
                    if (e.pointerCount == 1) {
                        modelRenderer.yaw -= dx * 0.4f
                        modelRenderer.pitch = (modelRenderer.pitch + dy * 0.4f).coerceIn(-89f, 89f)
                    } else if (height > 0) {
                        // Satuan: radius model per piksel layar. / Units: model radii per screen pixel.
                        val k = 2f * modelRenderer.distanceFactor * 0.4142f / height
                        modelRenderer.panX += dx * k
                        modelRenderer.panY -= dy * k
                    }
                    requestRender()
                }
            }
            MotionEvent.ACTION_UP -> {
                // Ketuk tunggal yang tidak bergeser: pilih material di bawah jari. / A single unmoved tap picks the material under the finger.
                if (!moved && e.pointerCount == 1 && width > 0 && height > 0 && !scaleDetector.isInProgress) {
                    val nx = 2f * e.x / width - 1f
                    val ny = 1f - 2f * e.y / height
                    modelRenderer.pickMaterial(nx, ny)?.let { onMaterialPick?.invoke(it) }
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
