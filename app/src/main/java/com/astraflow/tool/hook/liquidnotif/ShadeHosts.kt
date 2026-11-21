package com.astraflow.tool.hook.liquidnotif

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.astraflow.tool.hook.glass.CapturedBackground
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 通知中心的背景:放在通知中心窗口根视图的最底下,铺满屏幕,不接触摸。自己不画后面的画面(不截图):
 * 通知中心窗口是半透明的,后面的桌面或应用本身透过来,永远是实时的。这里只做两件事——
 * 1. 安卓原版的「区域模糊」:向本窗口要一块(ViewRootImpl.createBackgroundBlurDrawable,安卓 12 起系统自带,
 *    位置每一帧跟着本视图走),铺满屏幕,把后面的画面实时模糊,程度是用户设的值。按安卓原版的规矩它画在
 *    窗口整体的模糊上面,所以系统原来的模糊不关、不改(用户 2026-09-26「通过安卓原版给窗口设置模糊程度」
 *    「不然的话，我们很多东西太过于依赖针对某款机型去做特调」)。程度为 0 时安卓不画这块区域,最低用 1 像素,
 *    看起来就是透明。
 * 2. 一层淡淡的颜色:整体压暗,顶部时间那一块再由上往下加深一点(用户同日「把它的背景直接做透，只加淡淡的效果」)。
 *
 * 通知页和控制中心共用这一层(用户同日「先做同一层背景吧」):它不在任何一页里面,左右切换时不动。
 * 每一帧画之前调用 [onFrame],由总管按整个下拉面板的拉开程度更新它(控制中心那一页没有我们的卡片,不能靠卡片那边)。
 */
@SuppressLint("ViewConstructor")
internal class LiquidBackgroundView(context: Context, private val onFrame: () -> Unit) : View(context) {

    var capture: CapturedBackground.Lease? = null
        private set

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        // 这里在系统界面自己的绘制流程里,出错绝不能传回系统
        runCatching(onFrame).onFailure { LiquidLog.w("background frame sync failed", it) }
        true
    }

    private var blur: Drawable? = null
    private var setRadius: Method? = null
    private var blurTried = false
    private var radiusPx = 1
    private var blurAlpha = 0f
    private var dim = 0f
    private var topDim = 0f
    private var topEnd = 0f
    private val topPaint = Paint()
    private var topShaderDim = -1f
    private var topShaderEnd = -1f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** 这个窗口有没有要到安卓原版的区域模糊(排查记录用) */
    val hasAreaBlur: Boolean get() = blur != null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
        ensureBlur()
        capture = CapturedBackground.acquire(this).also { lease ->
            lease.onFrame {
                LiquidShade.onBackgroundFrame()
                com.astraflow.tool.hook.liquidcc.CcGlassEnv.invalidateAll()
            }
            lease.setActive(blurAlpha > 0.002f)
        }
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        blur?.let {
            it.alpha = 0
            it.callback = null
        }
        blur = null
        setRadius = null
        blurTried = false
        capture?.close()
        capture = null
        super.onDetachedFromWindow()
    }

    private fun ensureBlur() {
        if (blur != null || blurTried) return
        blurTried = true
        blur = runCatching {
            val root = View::class.java.getMethod("getViewRootImpl").invoke(this) ?: error("view root unavailable")
            val d = root.javaClass.getMethod("createBackgroundBlurDrawable").invoke(root) as Drawable
            setRadius = d.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
            d.javaClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType).invoke(d, 0f)
            d.callback = this
            d
        }.onFailure { LiquidLog.w("area blur unavailable", it) }.getOrNull()
        blur?.let {
            if (width > 0 && height > 0) it.setBounds(0, 0, width, height)
            applyBlur(it)
        }
        LiquidLog.i("area blur ${if (blur != null) "ready" else "missing"}")
    }

    /**
     * 每帧由总管调用(见 [onFrame])。[radius] 区域模糊的程度(像素,至少 1),[blurAlpha] 这块区域的不透明度(0 为不画),
     * [dim] 整体压暗,[topDim] 顶部加深,[topEnd] 顶部加深淡到哪里(本视图坐标,像素)。
     */
    fun update(radius: Int, blurAlpha: Float, dim: Float, topDim: Float, topEnd: Float) {
        val r = radius.coerceAtLeast(1)
        val a = blurAlpha.coerceIn(0f, 1f)
        capture?.setActive(a > 0.002f)
        var changed = false
        if (r != radiusPx || abs(a - this.blurAlpha) > 0.002f) {
            radiusPx = r
            this.blurAlpha = a
            blur?.let(::applyBlur)
            changed = true
        }
        if (abs(dim - this.dim) > 0.002f || abs(topDim - this.topDim) > 0.002f || abs(topEnd - this.topEnd) > 0.5f) {
            this.dim = dim
            this.topDim = topDim
            this.topEnd = topEnd
            changed = true
        }
        if (changed) invalidate()
    }

    private fun applyBlur(d: Drawable) {
        runCatching { setRadius?.invoke(d, radiusPx) }.onFailure { LiquidLog.w("area blur radius failed", it) }
        d.alpha = (blurAlpha * 255f).roundToInt().coerceIn(0, 255)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        blur?.setBounds(0, 0, w, h)
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === blur || super.verifyDrawable(who)

    /** 半透明时不必先画到另一层上 */
    override fun hasOverlappingRendering(): Boolean = false

    override fun onDraw(canvas: Canvas) {
        val d = blur
        // 这里在系统界面自己的绘制流程里,出错绝不能传回系统
        if (d != null && blurAlpha > 0.002f) runCatching { d.draw(canvas) }.onFailure { LiquidLog.w("area blur draw failed", it) }
        if (dim > 0.002f) canvas.drawColor((dim.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)
        if (topDim > 0.002f && topEnd > 1f) {
            if (topShaderDim != topDim || topShaderEnd != topEnd) {
                topShaderDim = topDim
                topShaderEnd = topEnd
                val top = (topDim.coerceIn(0f, 1f) * 255f).roundToInt() shl 24
                topPaint.shader = LinearGradient(0f, 0f, 0f, topEnd, top, 0, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), topEnd, topPaint)
        }
    }
}

/**
 * 我们画的通知的宿主:插在系统通知列表的旁边(同一个上级视图里、紧挨在它上面),铺满上级视图;
 * 里面的界面从系统通知列表的位置开始,宽度与它相同,一直延伸到上级视图的底边。
 *
 * 卡片以外的地方不接触摸:卡片上方(顶部时间和按钮那一块)和最后一张卡片下面的空白,
 * 触摸照旧交给系统,系统面板因此照旧处理点空白收起、从空白处上推收起。
 * 每一帧画之前调用 [onFrame],由它同步系统通知列表的位置、透明度和拉开程度。
 */
@SuppressLint("ViewConstructor")
internal class LiquidCardsHost(
    context: Context,
    private val target: View,
    private val onFrame: () -> Unit,
    private val acceptsDown: (x: Float, y: Float) -> Boolean,
) : ViewGroup(context) {

    val compose = ComposeView(context)
    private val owner = LiquidComposeOwner()

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        runCatching(onFrame).onFailure { LiquidLog.w("frame sync failed", it) }
        true
    }

    private val targetLayout = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (l != ol || t != ot || r != or || b != ob) requestLayout()
    }

    init {
        clipChildren = false
        clipToPadding = false
        owner.attachTo(this)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        addView(compose)
    }

    fun setContent(content: @Composable () -> Unit) {
        compose.setParentCompositionContext(owner.compositionContext())
        compose.setContent(content)
    }

    override fun onAttachedToWindow() {
        owner.create()
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
        target.addOnLayoutChangeListener(targetLayout)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        target.removeOnLayoutChangeListener(targetLayout)
        super.onDetachedFromWindow()
    }

    /** 整个拆掉:从系统视图里移除并释放界面 */
    fun release() {
        (parent as? ViewGroup)?.removeView(this)
        compose.disposeComposition()
        owner.destroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            // 换成界面自己的坐标(界面摆在系统通知列表的位置上,还可能跟着它平移)
            val x = ev.x - compose.left - compose.translationX
            val y = ev.y - compose.top - compose.translationY
            if (!acceptsDown(x, y)) return false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        // 系统通知列表与本视图在同一上级里依次排版,它先排,这里取它这一轮量出的宽度;
        // 高度从它的上沿一直到上级视图的底边:列表可以滚到屏幕底部,在底边渐隐,不在半中间切断卡片
        val cw = if (target.measuredWidth > 0) target.measuredWidth else w
        val below = (h - (target.top - top).coerceAtLeast(0)).coerceAtLeast(0)
        val ch = maxOf(below, target.measuredHeight)
        compose.measure(
            MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = target.left - left
        val y = target.top - top
        compose.layout(x, y, x + compose.measuredWidth, y + compose.measuredHeight)
    }

    override fun shouldDelayChildPressedState(): Boolean = false
}
