package zen.gtasaskinstudio.app.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.scene.SceneBuffers

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
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Jumlah jari berubah: mulai ulang agar tidak melompat.
                lastX = cx; lastY = cy; lastCount = -1
            }
            MotionEvent.ACTION_MOVE -> {
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
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
