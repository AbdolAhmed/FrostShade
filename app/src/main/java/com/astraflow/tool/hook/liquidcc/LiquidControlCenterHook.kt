package com.astraflow.tool.hook.liquidcc

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.astraflow.tool.hook.core.HookHelper
import com.astraflow.tool.hook.liquidnotif.LiquidLog
import com.astraflow.tool.hook.liquidnotif.LiquidShade
import java.lang.reflect.Field
import java.util.WeakHashMap
import kotlin.math.min

/**
 * 液态玻璃控制中心的亮度条、音量条(方案 docs/design/liquid-control-center.md)。
 * 它们不是用底子画的,而是在自己的绘制里画轨道和填充:接 OPPO 滑条(OplusQsVerticalSeekBar,ColorOS 15 起一直存在)
 * 的绘制,功能开着、下拉面板的背景在时,按 OPPO 界面库记下的轨道形状、圆角和填充范围画玻璃轨道和浅色填充,
 * 否则照 OPPO 原样画。图标仍由 OPPO 画(填充盖过图标时 OPPO 自己把图标变深)。
 * 开关、按钮、音乐卡片不在这里,见 [LiquidControlCenter]。
 */
object LiquidControlCenterHook {

    private const val SLIDER = "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar"

    /** 填充:浅色,不透明度 */
    private const val FILL_ALPHA = 0.45f

    fun register(classLoader: ClassLoader) {
        val slider = runCatching { classLoader.loadClass(SLIDER) }.getOrNull()
            ?: return LiquidLog.w("control center: slider class not found, sliders keep the system look")
        val onDraw = runCatching { slider.getDeclaredMethod("onDraw", Canvas::class.java) }.getOrNull()
            ?: return LiquidLog.w("control center: slider onDraw not found, sliders keep the system look")
        HookHelper.hookMethod(onDraw, beforeCallback = { chain ->
            val bar = chain.thisObject as? View
            val canvas = chain.args.firstOrNull() as? Canvas
            // 这里在系统界面自己的绘制流程里,出错绝不能传回系统;画不了就照 OPPO 原样画
            if (bar != null && canvas != null && active() && runCatching { drawSlider(bar, canvas) }.getOrDefault(false)) Unit else null
        })
        LiquidLog.i("control center: slider hook installed")
    }

    private fun active(): Boolean = LiquidShade.backgroundShowing && LiquidControlCenter.enabled()

    private val fields = HashMap<String, Field?>()

    /** OPPO 界面库滑条的公开字段(轨道形状、填充范围、圆角),拖动时的回弹拉伸已算在里面 */
    private fun field(target: Any, name: String): Field? = fields.getOrPut(name) {
        var c: Class<*>? = target.javaClass
        var found: Field? = null
        while (c != null && found == null) {
            found = runCatching { c.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
            c = c.superclass
        }
        found
    }

    private val tracks = WeakHashMap<View, CcGlassDrawable>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()

    private fun drawSlider(bar: View, canvas: Canvas): Boolean {
        val track = field(bar, "mClipProgressRect")?.get(bar) as? Rect ?: return false
        val progress = field(bar, "mProgressRect")?.get(bar) as? Rect ?: return false
        if (track.width() <= 0 || track.height() <= 0) return false
        val radius = (field(bar, "mCurProgressRadius")?.get(bar) as? Float)
            ?.takeIf { it > 0f }
            ?: (min(track.width(), track.height()) / 2f)
        val glass = tracks.getOrPut(bar) { CcGlassDrawable(bar.resources.displayMetrics.density, plain = true) }
        glass.host = java.lang.ref.WeakReference(bar)
        glass.radiusOverride = radius
        glass.setBounds(track)
        glass.draw(canvas)
        // 填充:只画在轨道形状里面
        if (progress.width() > 0 && progress.height() > 0) {
            rect.set(track)
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clip)
            fill.color = Color.argb((FILL_ALPHA * 255f).toInt(), 255, 255, 255)
            canvas.drawRect(progress, fill)
            canvas.restoreToCount(save)
        }
        return true
    }
}
