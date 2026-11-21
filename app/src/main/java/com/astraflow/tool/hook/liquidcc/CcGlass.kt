package com.astraflow.tool.hook.liquidcc

import android.content.Context
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import com.astraflow.tool.hook.liquidnotif.ui.UPRIGHT_LIGHT
import com.astraflow.tool.hook.liquidnotif.ui.glassLightDirection
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.astraflow.tool.hook.glass.CapturedBackground
import com.astraflow.tool.hook.glass.GlassMaterial
import android.graphics.Matrix

/**
 * 控制中心玻璃共用的东西:玻璃的几项设置(与液态玻璃堆叠通知共用)、OPPO 的主题色、光从哪边来、开关状态标记。
 * 全部在系统界面主线程上用。
 */
internal object CcGlassEnv {

    /** 影子半径与底栏、卡片一样 */
    const val SHADOW_RADIUS_DP = 10f

    /** 开着的开关(图标是白色时):主题色的不透明度 */
    const val ACTIVE_ALPHA = 0.55f

    /**
     * 开着的开关(图标是彩色或深色时):白色的不透明度。OPPO「彩色图标」样式下开着是浅色底配彩色图标,
     * 填主题色时蓝色图标落在蓝色底上看不见(用户 2026-09-26「图标会和底色融合在一起，看不清」)
     */
    const val ACTIVE_LIGHT_ALPHA = 0.62f

    /** 卡片和面板里的文字、图标是深色时(例如已连接设备的卡片,OPPO 是浅色底配黑色内容):玻璃用浅色 */
    const val LIGHT_CARD_ALPHA = 0.62f

    /** 开着但暂时不能操作的开关:填色只留这么多(与 OPPO 一样) */
    const val INOPERABLE_FACTOR = 0.3f

    /** 按住时泛起的柔光(白色)最亮多少 */
    const val PRESS_GLOW_ALPHA = 0.16f

    /** 表面白色浓度(液态玻璃堆叠通知「表面白色浓度(深色模式)」:控制中心的图标文字是白色的,玻璃总是深色的样子) */
    var surfaceAlpha = 0.10f

    /** 影子浓度:卡片深色模式的数字(设置值的两倍) */
    var shadowAlpha = 0.20f
    var highlightAlpha = 0.75f
    var tilt = true
    var pressGlow = true
    var backgroundSource: CapturedBackground.Lease? = null

    /** OPPO 的主题色(开着的开关,图标是白色时) */
    var accent = 0xFF247CFF.toInt()

    /**
     * 系统此刻是不是「彩色图标」样式:照 OPPO 自己的判断,设置没设过或为 0、并且没有套用全局主题时是彩色。
     * 读不到图标颜色、或图标颜色正在过渡时按它填色
     */
    var colorfulIcons = true

    /** OPPO 传给开关底子的显示状态标记:开、不可用、开着但不能操作(资源名,新旧开关写法都用这一组) */
    var attrActive = 0
    var attrUnavailable = 0
    var attrInoperable = 0

    /**
     * 光照过来的方向(单位向量,指向玻璃最亮的那一角):手机倾斜的方向转 −45°;竖直拿着时从左上来
     */
    var lightX = UPRIGHT_LIGHT.x
        private set
    var lightY = UPRIGHT_LIGHT.y
        private set

    private val live: MutableSet<CcGlassDrawable> = Collections.newSetFromMap(WeakHashMap())

    fun register(d: CcGlassDrawable) {
        live += d
    }

    /** 亮边转了或设置变了:所有玻璃重画 */
    fun invalidateAll() {
        for (d in live.toList()) d.invalidateSelf()
    }

    /** 按现在的倾斜方向重算光从哪边来(不随倾斜转动时固定在竖直拿着的位置) */
    fun updateHighlight() {
        val light = if (tilt) glassLightDirection(GlassTilt.gravityX, GlassTilt.gravityY) else UPRIGHT_LIGHT
        lightX = light.x
        lightY = light.y
        invalidateAll()
    }

    /**
     * 从控制中心的视图取资源:状态标记;主题色照 OPPO 的取法——界面库的主色,在深色模式下取
     * (OPPO 控制中心开着的开关用的就是深色模式的主色),取不到时用安卓的强调色
     */
    fun prepare(context: Context) {
        val res = context.resources
        attrActive = res.getIdentifier("state_active", "attr", SYSTEM_UI)
        attrUnavailable = res.getIdentifier("state_unavailable", "attr", SYSTEM_UI)
        attrInoperable = res.getIdentifier("state_inoperable", "attr", SYSTEM_UI)
        val primary = res.getIdentifier("couiColorPrimary", "attr", SYSTEM_UI)
        accent = resolveColorAttr(nightContext(context), primary)
            ?: resolveColorAttr(context, primary)
            ?: resolveColorAttr(context, android.R.attr.colorAccent)
            ?: accent
        val colorfulSetting = runCatching {
            Settings.Secure.getInt(context.contentResolver, "qs_tile_colorful_config", 0) == 0
        }.getOrDefault(true)
        // 套用了全局主题时 OPPO 不用彩色图标(主题在系统界面的这个开关资源上写明)
        val globalTheme = res.getIdentifier("status_bar_qs_icon_bg_drawable_global_theme", "bool", SYSTEM_UI)
            .takeIf { it != 0 }
            ?.let { runCatching { res.getBoolean(it) }.getOrNull() } ?: false
        colorfulIcons = colorfulSetting && !globalTheme
    }

    /** 同一个系统界面主题、强制深色模式的环境 */
    private fun nightContext(context: Context): Context? = runCatching {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val night = context.createConfigurationContext(config)
        val theme = context.resources.getIdentifier("Theme.SystemUI", "style", SYSTEM_UI)
        if (theme != 0) ContextThemeWrapper(night, theme) else night
    }.getOrNull()

    private fun resolveColorAttr(context: Context?, attr: Int): Int? {
        if (context == null || attr == 0) return null
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return null
        return when {
            tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> tv.data
            tv.resourceId != 0 -> runCatching { context.getColor(tv.resourceId) }.getOrNull()
            else -> null
        }
    }

    /** 图标颜色是不是白色(或接近白的浅灰):是的话开着填主题色,不是(彩色、深色)填浅色 */
    fun isLightNeutral(color: Int): Boolean {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val spread = max(r, max(g, b)) - min(r, min(g, b))
        return luminance > 0.7f && spread < 0.2f
    }

    /** 各种图标视图的「正在播放动画吗」(OPPO 开关图标是 Lottie 的动画视图,方法名是公开的 isAnimating);没有这个方法的为空 */
    private val animatingMethods = HashMap<Class<*>, java.lang.reflect.Method?>()

    /**
     * 开关图标此刻是不是正在播放 OPPO 的开关动画:打开、关闭开关时 OPPO 先播一段图标动画,动画期间图标的着色
     * 停在上一个状态(打开时还是白色),播完才换成新状态的颜色;这段时间的着色不能拿来判断填什么
     */
    fun isAnimating(view: View): Boolean {
        val method = animatingMethods.getOrPut(view.javaClass) {
            runCatching { view.javaClass.getMethod("isAnimating").takeIf { it.returnType == java.lang.Boolean.TYPE } }.getOrNull()
        } ?: return false
        return runCatching { method.invoke(view) as Boolean }.getOrDefault(false)
    }

    const val SYSTEM_UI = "com.android.systemui"

}

/**
 * 控制中心的一块玻璃底图,换在 OPPO 各部件的底子那一层上(方案 docs/design/liquid-control-center.md)。
 * 画法照液态玻璃卡片:外围柔影(只画在玻璃外面)、透明的白色表面、按住时泛起柔光、玻璃的边(细亮边和弧面,
 * 光的方向随手机倾斜转动)。开关的开、不可用、按住从 OPPO 传给底子那一层的显示状态里读;
 * 开着时按图标的颜色填:图标白色填主题色,图标彩色或深色填浅色(和 OPPO 原来的深浅搭配一样)。
 * 圆角由 [radiusOf] 给(读不到时按短边的一半),或由 [corners] 给四个角各自的圆角。
 * 本身没有大小和留白,换上去不会引起重新排版。
 */
internal class CcGlassDrawable(
    private val density: Float,
    private val radiusOf: (() -> Float?)? = null,
    /** 亮度音量条的轨道:不读开关状态,按住不发光(OPPO 自己会把整条缩小) */
    private val plain: Boolean = false,
) : Drawable() {

    var host: WeakReference<View>? = null
    private val material = GlassMaterial()
    private val global = Matrix()
    private val screenToLocal = Matrix()

    private var alphaValue = 255
    private var active = false
    private var unavailable = false
    private var inoperable = false
    private var pressed = false
    private var pressFrom = 0f
    private var pressAt = 0L

    /** 圆角的覆盖值(亮度音量条按 OPPO 记下的圆角画) */
    var radiusOverride: Float? = null

    /** 四个角各自的圆角(左上、右上、右下、左下,像素);有它时不用 [radiusOf] */
    var corners: FloatArray? = null

    /**
     * 开着时填色:宽开关的整块底子不填(开关状态只显示在它的圆形图标底上,两者同色会融在一起);
     * 值变了时重画
     */
    var showsState = true
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    /** 用浅色填(卡片和面板里的文字、图标是深色时,例如已连接设备的卡片:OPPO 是浅色底配黑色内容);值变了时重画 */
    var solidLight = false
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    /** 一直按开着填色(响铃三段开关的选中滑块:OPPO 一直把它画成开着的样子,不靠显示状态) */
    var alwaysOn = false

    /**
     * 画不画外围柔影。部件的上一层会裁掉超出部件范围的内容时(宽开关的圆形图标底:OPPO 的图标容器不放开裁剪)不画:
     * 柔影只剩圆形与方形边框之间那一圈,被裁成直边的深色方块。
     */
    var outerShadow = true

    /** 这个开关的图标(读它的着色决定开着时填什么颜色);没有时按系统设置判断 */
    var glyphView: WeakReference<View>? = null
    private var lastGlyph: Int? = null

    /**
     * 图标颜色正在过渡:OPPO 换开关状态时先播一段图标动画,动画期间图标的着色停在上一个状态(彩色图标样式下打开时
     * 还是白色),播完才换成新状态的颜色;有的地方还会逐帧改颜色。过渡途中的颜色不拿来判断填什么,不然打开开关时
     * 先闪一下主题色(公开反馈 #62「磁贴开关时颜色强制变蓝」)。开关状态刚变、图标正在播放动画、或上一帧图标颜色
     * 还在变时算过渡中;过渡中按这个开关上次停稳时开着的样子填([restActiveLight]),没有记录时按系统样式
     */
    private var glyphSettling = false
    private var stateChangedAt = 0L

    /** 这个开关上次开着、图标停稳时是不是填浅色(图标是彩色或深色);还没见过时为空 */
    private var restActiveLight: Boolean? = null

    private val rect = RectF()
    private val path = Path()
    private val radii = FloatArray(8)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        maskFilter = BlurMaskFilter(CcGlassEnv.SHADOW_RADIUS_DP * density, BlurMaskFilter.Blur.NORMAL)
    }

    /**
     * 玻璃的边用的笔:白色,整体透明度乘上开关此刻的透明度。OPPO 展开、收起控制中心时把开关一个个调淡,
     * 调淡乘进每一笔的透明度,边就跟着开关一起淡,不会开关淡没了只剩一圈边(用户 2026-09-27「上滑的时候……
     * 缺失图标中的内容只留下边框，甚至是细线」)
     */
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val edgePath = Path()
    private val edgeRect = RectF()
    private val edgeRadii = FloatArray(8)

    /** 亮边和弧面的渐变(从最亮那一角斜着到对角);玻璃的位置、大小或光的方向变了才重做 */
    private var rimShader: Shader? = null
    private var lensShader: Shader? = null
    private val shaderRect = RectF()
    private var shaderLightX = 0f
    private var shaderLightY = 0f

    init {
        CcGlassEnv.register(this)
    }

    override fun isStateful(): Boolean = !plain

    override fun onStateChange(state: IntArray): Boolean {
        if (plain) return false
        val a = CcGlassEnv.attrActive != 0 && state.contains(CcGlassEnv.attrActive)
        val u = CcGlassEnv.attrUnavailable != 0 && state.contains(CcGlassEnv.attrUnavailable)
        val i = CcGlassEnv.attrInoperable != 0 && state.contains(CcGlassEnv.attrInoperable)
        val p = state.contains(android.R.attr.state_pressed)
        var changed = false
        if (a != active || u != unavailable || i != inoperable) {
            if (a != active) {
                stateChangedAt = SystemClock.uptimeMillis()
                glyphSettling = true
            }
            active = a
            unavailable = u
            inoperable = i
            changed = true
        }
        if (p != pressed) {
            pressFrom = pressAmount()
            pressAt = SystemClock.uptimeMillis()
            pressed = p
            changed = true
        }
        if (changed) invalidateSelf()
        return changed
    }

    /**
     * 每帧由控制中心调用:图标换了颜色(OPPO 在开关状态变化时重新着色)就重画;图标动画播完、颜色停下不变、
     * 开关状态也已变了一会儿,才按图标颜色填,并记下这个开关开着时的样子
     */
    fun checkGlyph() {
        val c = glyphColor()
        val changed = c != lastGlyph
        lastGlyph = c
        val animating = glyphView?.get()?.let(CcGlassEnv::isAnimating) == true
        val settling = changed || animating || SystemClock.uptimeMillis() - stateChangedAt < SETTLE_GRACE_MS
        if (!settling && (active || alwaysOn) && c != null) restActiveLight = !CcGlassEnv.isLightNeutral(c)
        if (changed || settling != glyphSettling) {
            glyphSettling = settling
            if (active || alwaysOn) invalidateSelf()
        }
    }

    /** 图标此刻显示的着色(着色随开关状态变化时,按图标当前的显示状态取) */
    private fun glyphColor(): Int? {
        val icon = glyphView?.get() as? ImageView ?: return null
        val tint = icon.imageTintList ?: return null
        return tint.getColorForState(icon.drawableState, tint.defaultColor)
    }

    /** 按住的程度,0 到 1:按下 0.12 秒亮起,松开 0.2 秒淡去 */
    private fun pressAmount(): Float {
        val t = (SystemClock.uptimeMillis() - pressAt).toFloat()
        return if (pressed) {
            (pressFrom + t / PRESS_IN_MS).coerceAtMost(1f)
        } else {
            (pressFrom - t / PRESS_OUT_MS).coerceAtLeast(0f)
        }
    }

    private fun radius(w: Float, h: Float): Float {
        val half = min(w, h) / 2f
        val r = radiusOverride ?: radiusOf?.invoke() ?: half
        return r.coerceIn(0f, half)
    }

    /** 四个角的圆角(左上、右上、右下、左下),各自不超过短边的一半 */
    private fun cornerRadii(w: Float, h: Float): FloatArray {
        val half = min(w, h) / 2f
        val c = corners
        val out = FloatArray(4)
        if (c != null && c.size >= 4) {
            for (k in 0 until 4) out[k] = c[k].coerceIn(0f, half)
        } else {
            out.fill(radius(w, h))
        }
        return out
    }

    private fun shapePath(c: FloatArray) {
        for (k in 0 until 4) {
            radii[k * 2] = c[k]
            radii[k * 2 + 1] = c[k]
        }
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
    }

    /** 表面的颜色:平时透明的白;开着时按图标的颜色填主题色或浅色;已连接设备的卡片一直浅色 */
    private fun surfaceColor(a: Float): Int {
        if (solidLight) return withAlpha(Color.WHITE, CcGlassEnv.LIGHT_CARD_ALPHA * a)
        if ((active || alwaysOn) && showsState && !plain) {
            val g = if (glyphSettling) null else glyphColor()
            val lightFill = if (g != null) !CcGlassEnv.isLightNeutral(g) else restActiveLight ?: CcGlassEnv.colorfulIcons
            val factor = if (inoperable) CcGlassEnv.INOPERABLE_FACTOR else 1f
            return if (lightFill) {
                withAlpha(Color.WHITE, CcGlassEnv.ACTIVE_LIGHT_ALPHA * factor * a)
            } else {
                withAlpha(CcGlassEnv.accent, CcGlassEnv.ACTIVE_ALPHA * factor * a)
            }
        }
        return withAlpha(Color.WHITE, CcGlassEnv.surfaceAlpha * a)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val c = cornerRadii(w, h)
        val a = alphaValue / 255f * if (unavailable) 0.5f else 1f
        rect.set(b)
        shapePath(c)
        // 外围柔影:只画在玻璃外面(玻璃是透明的,画在里面会透出一片黑)
        if (outerShadow && CcGlassEnv.shadowAlpha > 0.002f) {
            val save = canvas.save()
            canvas.clipOutPath(path)
            shadow.alpha = (CcGlassEnv.shadowAlpha * a * 255f).toInt().coerceIn(0, 255)
            canvas.drawPath(path, shadow)
            canvas.restoreToCount(save)
        }
        val press = if (plain || !CcGlassEnv.pressGlow) 0f else pressAmount()
        val view = host?.get()
        val frame = CcGlassEnv.backgroundSource?.frame?.value
        val glassDrawn = if (view != null && frame != null) {
            global.reset()
            view.transformMatrixToGlobal(global)
            global.invert(screenToLocal) && material.draw(canvas, frame, screenToLocal, path, rect, c, density,
                surfaceColor(1f), a, CcGlassEnv.highlightAlpha * a, CcGlassEnv.lightX, CcGlassEnv.lightY, press = press)
        } else false
        if (!glassDrawn) {
            fill.color = surfaceColor(a)
            canvas.drawPath(path, fill)
        }
        // 按住时泛起柔光
        if (press > 0.002f) {
            fill.color = withAlpha(Color.WHITE, CcGlassEnv.PRESS_GLOW_ALPHA * press * a)
            canvas.drawPath(path, fill)
        }
        // 玻璃的边:细亮边和弧面
        if (!glassDrawn && CcGlassEnv.highlightAlpha > 0.002f) drawEdge(canvas, c, a)
        // 按住的光还在变:下一帧再画
        if (press > 0f && press < 1f || pressed && press < 1f) invalidateSelf()
    }

    /**
     * 玻璃的边,照手势侧边栏那一版液态玻璃复制(用户 2026-09-28「你这次写的液态玻璃效果其实挺好的……顺带着把我们的
     * 液态通知堆叠和液态控制中心也改一下」):最外沿一道 1dp 的细亮边——光照过来的那一角最亮、对角次亮、两侧中段几乎没有;
     * 亮边里面三道 1.5dp 的白线,由外往里越来越淡,像光在圆边上聚拢。都是普通的抗锯齿描边,
     * 圆角上不再有原来那道着色程序画的亮边的锯齿毛边(用户 2026-09-28「边缘有锯齿毛边，这是液态通知和控制中心一直的问题」)。
     * 强度用「亮边强度」,再乘上开关此刻的透明度 [a]。
     */
    private fun drawEdge(canvas: Canvas, c: FloatArray, a: Float) {
        val alpha = CcGlassEnv.highlightAlpha * a
        val w = rect.width()
        val h = rect.height()
        if (alpha <= 0.002f || w <= 1f || h <= 1f) return
        aimShaders()
        val rim = RIM_WIDTH_DP * density
        edge.strokeWidth = rim
        edge.shader = rimShader
        edge.alpha = alphaOf(alpha)
        insetPath(c, rim / 2f)
        canvas.drawPath(edgePath, edge)
        val step = LENS_STEP_DP * density
        edge.strokeWidth = step
        edge.shader = lensShader
        for (i in 0..0) {
            val inset = rim + step * i + step / 2f
            if (w <= inset * 2f || h <= inset * 2f) break
            edge.alpha = alphaOf(alpha * LENS_FALLOFF[i])
            insetPath(c, inset)
            canvas.drawPath(edgePath, edge)
        }
        edge.shader = null
    }

    /** 按玻璃现在的位置、大小和光的方向准备两道渐变(没变就用上次的) */
    private fun aimShaders() {
        val lx = CcGlassEnv.lightX
        val ly = CcGlassEnv.lightY
        if (rimShader != null && shaderRect == rect && shaderLightX == lx && shaderLightY == ly) return
        shaderRect.set(rect)
        shaderLightX = lx
        shaderLightY = ly
        // 渐变从最亮的那一处(沿光的方向最远的那一角或那条边)到正对面,光转到哪个方向,最亮处都是 95%
        val half = abs(lx) * rect.width() / 2f + abs(ly) * rect.height() / 2f
        val sx = rect.centerX() + lx * half
        val sy = rect.centerY() + ly * half
        val ex = rect.centerX() - lx * half
        val ey = rect.centerY() - ly * half
        rimShader = LinearGradient(sx, sy, ex, ey, RIM_COLORS, RIM_STOPS, Shader.TileMode.CLAMP)
        lensShader = LinearGradient(sx, sy, ex, ey, LENS_COLORS, LENS_STOPS, Shader.TileMode.CLAMP)
    }

    /** 玻璃外形往里收 [inset] 的那一圈(四个角的圆角跟着收小) */
    private fun insetPath(c: FloatArray, inset: Float) {
        edgeRect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        for (k in 0 until 4) {
            val r = max(0f, c[k] - inset)
            edgeRadii[k * 2] = r
            edgeRadii[k * 2 + 1] = r
        }
        edgePath.reset()
        edgePath.addRoundRect(edgeRect, edgeRadii, Path.Direction.CW)
    }

    private fun alphaOf(alpha: Float): Int = (alpha * 255f).roundToInt().coerceIn(0, 255)

    override fun getOutline(outline: Outline) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        shapePath(cornerRadii(b.width().toFloat(), b.height().toFloat()))
        outline.setPath(path)
        outline.alpha = 0f
    }

    override fun setAlpha(alpha: Int) {
        if (alphaValue == alpha) return
        alphaValue = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun withAlpha(color: Int, alpha: Float): Int {
        val base = Color.alpha(color) / 255f
        val out = (base * alpha * 255f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (out shl 24)
    }

    private companion object {
        const val PRESS_IN_MS = 120f
        const val PRESS_OUT_MS = 200f

        /** 开关状态变了以后至少这么久按过渡中处理(OPPO 的颜色动画可能晚一两帧才起步) */
        const val SETTLE_GRACE_MS = 150L

        /** 玻璃的边(照手势侧边栏):亮边 1dp;弧面三道 1.5dp,由外往里 1、0.45、0.15 倍,最亮 16% 白 */
        const val RIM_WIDTH_DP = 1f
        const val LENS_STEP_DP = 1.5f
        val LENS_FALLOFF = floatArrayOf(1f, 0.45f, 0.15f)
        val RIM_COLORS = whites(0.95f, 0.28f, 0.08f, 0.22f, 0.7f)
        val RIM_STOPS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val LENS_COLORS = whites(0.16f, 0.16f * 0.25f, 0.16f * 0.1f, 0.16f * 0.25f, 0.16f * 0.7f)
        val LENS_STOPS = floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f)

        fun whites(vararg alphas: Float): IntArray =
            IntArray(alphas.size) { Color.argb((alphas[it] * 255f).roundToInt(), 255, 255, 255) }
    }
}
