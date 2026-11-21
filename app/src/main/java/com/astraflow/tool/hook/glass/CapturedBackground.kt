package com.astraflow.tool.hook.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.content.pm.PackageManager
import android.hardware.HardwareBuffer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import com.astraflow.tool.hook.core.HookHelper
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** 系统窗口共用的背景画面。画面仅存于内存，读取时排除宿主窗口，禁止读取安全图层。 */
internal class BackgroundFrame(
    val bitmap: Bitmap,
    val screenWidth: Int,
    val screenHeight: Int,
    val tones: BackgroundTones?,
) {
    fun draw(canvas: Canvas, screenToLocal: Matrix, paint: Paint) {
        val matrix = Matrix(screenToLocal)
        matrix.preScale(screenWidth.toFloat() / bitmap.width, screenHeight.toFloat() / bitmap.height)
        canvas.drawBitmap(bitmap, matrix, paint)
    }
}

/** 小范围亮度统计与画面分别更新，不因每帧换图重建文字和图标。 */
internal class BackgroundTones(val columns: Int, val rows: Int, private val values: FloatArray) {
    fun luminance(rect: RectF, width: Int, height: Int): Float {
        val left = (rect.left / width * columns).toInt().coerceIn(0, columns - 1)
        val right = (rect.right / width * columns).toInt().coerceIn(left, columns - 1)
        val top = (rect.top / height * rows).toInt().coerceIn(0, rows - 1)
        val bottom = (rect.bottom / height * rows).toInt().coerceIn(top, rows - 1)
        var sum = 0f
        var count = 0
        for (y in top..bottom) for (x in left..right) { sum += values[y * columns + x]; count++ }
        return sum / count.coerceAtLeast(1)
    }

    companion object {
        fun read(bitmap: Bitmap): BackgroundTones? {
            val pixels = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            return try {
                val cols = 12
                val rows = 24
                val samples = FloatArray(cols * rows) { i ->
                    val x = ((i % cols + 0.5f) / cols * pixels.width).toInt().coerceAtMost(pixels.width - 1)
                    val y = ((i / cols + 0.5f) / rows * pixels.height).toInt().coerceAtMost(pixels.height - 1)
                    val c = pixels.getPixel(x, y)
                    (0.2126f * ((c ushr 16) and 255) + 0.7152f * ((c ushr 8) and 255) + 0.0722f * (c and 255)) / 255f
                }
                BackgroundTones(cols, rows, samples)
            } finally { pixels.recycle() }
        }
    }
}

/** 每个窗口一份读取器。后台最多一个请求；通知卡片、控制中心和滑条共用硬件画面。 */
internal class CapturedBackground private constructor(private val root: View) : Choreographer.FrameCallback {
    private val main = Handler(Looper.getMainLooper())
    private val mutableFrame = mutableStateOf<BackgroundFrame?>(null)
    val frame: State<BackgroundFrame?> get() = mutableFrame
    private val users = IdentityHashMap<Lease, Boolean>()
    private val callbacks = IdentityHashMap<Lease, () -> Unit>()
    private val clock = Choreographer.getInstance()
    private var scheduled = false
    private var callbackPosted = false
    private var busy = false
    private var epoch = 0
    private var requestedAt = 0L
    private var sampledAt = 0L
    private var tones: BackgroundTones? = null
    private var lastFailure: String? = null
    private val reader = DisplayBackgroundReader()
    private val canCapture = root.context.checkSelfPermission("android.permission.READ_FRAME_BUFFER") == PackageManager.PERMISSION_GRANTED

    class Lease internal constructor(private val source: CapturedBackground) : AutoCloseable {
        val frame: State<BackgroundFrame?> get() = source.frame
        fun setActive(active: Boolean) = source.setActive(this, active)
        fun onFrame(callback: () -> Unit) { source.callbacks[this] = callback }
        override fun close() = source.release(this)
    }

    private fun setActive(lease: Lease, active: Boolean) {
        val enabled = active && canCapture
        if (!users.containsKey(lease) || users[lease] == enabled) return
        users[lease] = enabled
        if (users.values.any { it }) {
            if (!scheduled) {
                scheduled = true
                postNextFrame()
            }
        } else {
            epoch++
            scheduled = false
            callbackPosted = false
            clock.removeFrameCallback(this)
            mutableFrame.value?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            mutableFrame.value = null
            tones = null
        }
    }

    private fun postNextFrame() {
        if (!scheduled || callbackPosted || users.values.none { it }) return
        callbackPosted = true
        clock.postFrameCallbackDelayed(this, FRAME_DELAY_MS)
    }

    override fun doFrame(frameTimeNanos: Long) {
        callbackPosted = false
        if (!scheduled || users.values.none { it }) return
        if (busy) return
        if (!root.isAttachedToWindow || !root.isShown || frameTimeNanos - requestedAt < FRAME_INTERVAL_NS) {
            postNextFrame()
            return
        }
        val excluded = rootSurface(root) ?: run { postNextFrame(); return }
        val size = Point()
        @Suppress("DEPRECATION")
        root.display?.getRealSize(size)
        if (size.x <= 0 || size.y <= 0) { postNextFrame(); return }
        requestedAt = frameTimeNanos
        val sample = tones == null || frameTimeNanos - sampledAt >= TONE_INTERVAL_NS
        val generation = epoch
        busy = true
        worker.execute {
            val result = runCatching { reader.read(excluded, size.x, size.y) }
            val bitmap = result.getOrNull()
            val nextTones = if (sample && bitmap != null) runCatching { BackgroundTones.read(bitmap) }.getOrNull() else null
            main.post {
                busy = false
                if (generation != epoch || users.values.none { it }) {
                    bitmap?.recycle()
                    postNextFrame()
                    return@post
                }
                if (bitmap != null) {
                    if (nextTones != null) { tones = nextTones; sampledAt = frameTimeNanos }
                    val previous = mutableFrame.value?.bitmap
                    mutableFrame.value = BackgroundFrame(bitmap, size.x, size.y, tones)
                    if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
                    lastFailure = null
                } else {
                    mutableFrame.value = null
                    tones = null
                    val message = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.cause?.message ?: it.message}" } ?: "empty background buffer"
                    if (message != lastFailure) {
                        lastFailure = message
                        HookHelper.xposedInterface?.log(4, "FrostShade", message)
                    }
                }
                callbacks.values.toList().forEach { callback ->
                    runCatching(callback).onFailure { HookHelper.xposedInterface?.log(4, "FrostShade", "background update failed: ${it.message}") }
                }
                postNextFrame()
            }
        }
    }

    private fun release(lease: Lease) {
        setActive(lease, false)
        users.remove(lease)
        callbacks.remove(lease)
        if (users.isEmpty()) synchronized(sources) { sources.remove(root) }
    }

    companion object {
        private const val FRAME_INTERVAL_NS = 33_000_000L
        private const val FRAME_DELAY_MS = 33L
        private const val TONE_INTERVAL_NS = 500_000_000L
        private val sources = WeakHashMap<View, CapturedBackground>()
        private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "AstraGlassBackground").apply { isDaemon = true } }

        fun acquire(host: View): Lease {
            check(Looper.myLooper() === Looper.getMainLooper())
            val root = host.rootView
            val source = synchronized(sources) { sources.getOrPut(root) { CapturedBackground(root) } }
            return Lease(source).also { source.users[it] = false }
        }

        private fun rootSurface(host: View): SurfaceControl? = runCatching {
            val root = View::class.java.getMethod("getViewRootImpl").invoke(host) ?: return null
            var type: Class<*>? = root.javaClass
            while (type != null) {
                val field = runCatching { type!!.getDeclaredField("mSurfaceControl").apply { isAccessible = true } }.getOrNull()
                if (field != null) return field.get(root) as? SurfaceControl
                type = type.superclass
            }
            null
        }.getOrNull()
    }
}

/** 安卓原版显示画面接口。排除自己的窗口，不读取厂商接口，不启用安全内容捕获。 */
private class DisplayBackgroundReader {
    private val type by lazy { Class.forName("android.window.ScreenCapture") }
    private val argsType by lazy { Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs") }
    private val builderType by lazy { Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs\$Builder") }
    private val builderCtor by lazy { builderType.getConstructor(IBinder::class.java) }
    private val exclude by lazy { builderType.getMethod("setExcludeLayers", Array<SurfaceControl>::class.java) }
    private val setSize by lazy { builderType.getMethod("setSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) }
    private val build by lazy { builderType.getMethod("build") }
    private val capture by lazy { type.getMethod("captureDisplay", argsType) }
    private val physicalIds by lazy { SurfaceControl::class.java.getMethod("getPhysicalDisplayIds") }
    private val displayToken by lazy { SurfaceControl::class.java.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType) }

    fun read(excluded: SurfaceControl, width: Int, height: Int): Bitmap? {
        val ids = physicalIds.invoke(null) as LongArray
        check(ids.isNotEmpty()) { "display unavailable" }
        val token = displayToken.invoke(null, ids[0]) as? IBinder ?: return null
        val builder = builderCtor.newInstance(token)
        exclude.invoke(builder, arrayOf(excluded))
        setSize.invoke(builder, (width * 0.5f).roundToInt().coerceAtLeast(1), (height * 0.5f).roundToInt().coerceAtLeast(1))
        val shot = capture.invoke(null, build.invoke(builder)) ?: return null
        val buffer = shot.javaClass.getMethod("getHardwareBuffer").invoke(shot) as? HardwareBuffer ?: return null
        return try {
            val colorSpace = shot.javaClass.getMethod("getColorSpace").invoke(shot) as? ColorSpace
            val hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            software
        } finally { buffer.close() }
    }
}

/** 由三个屏幕坐标构造变换，包含滚动、父层缩放和平移；不依赖硬件画布的未定义初始矩阵。 */
internal fun screenToLocal(coordinates: LayoutCoordinates, result: Matrix): Boolean {
    if (!coordinates.isAttached) return false
    val a = coordinates.localToScreen(Offset.Zero)
    val b = coordinates.localToScreen(Offset(1f, 0f))
    val c = coordinates.localToScreen(Offset(0f, 1f))
    if (!a.x.isFinite() || !a.y.isFinite() || !b.x.isFinite() || !b.y.isFinite() || !c.x.isFinite() || !c.y.isFinite()) return false
    val localToScreen = Matrix().apply {
        setValues(floatArrayOf(b.x - a.x, c.x - a.x, a.x, b.y - a.y, c.y - a.y, a.y, 0f, 0f, 1f))
    }
    return localToScreen.invert(result)
}
