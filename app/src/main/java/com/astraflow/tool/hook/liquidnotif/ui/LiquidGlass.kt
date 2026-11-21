package com.astraflow.tool.hook.liquidnotif.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.astraflow.frostshade.FrostStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/** 卡片按下时放大的量:与底栏一样,宽度多出 16dp */
private val PRESS_GROW = 16.dp

/**
 * 按下时最多放大这么多(底栏宽约 266dp,多出 16dp 约是 6%)。小按钮照 16dp 算会放大三四成,
 * 放大出去的部分被切开(用户 2026-09-26「那个清除按钮，如果我长按它放大的话，四周好像会有一个矩形」)
 */
private const val PRESS_GROW_MAX = 0.06f

/** 影子半径与底栏一样 */
private val SHADOW_RADIUS = 10.dp

/** 流光(照星河岛的数字):最亮 55% 白,光段占卡片宽度 28%,宽 1.5dp,只画在上部 40dp 内 */
private const val FLOW_ALPHA = 0.55f
private const val FLOW_WIDTH_FRACTION = 0.28f
private val FLOW_STROKE = 1.5.dp
private val FLOW_BAND = 40.dp

/** 氛围光(照星河岛的数字):中心 22%、中段 7%(在 45% 处)、边缘 0,半径 150dp,纵向压扁到 0.7 */
private const val GLOW_ALPHA = 0.22f
private const val GLOW_MID_ALPHA = 0.07f
private const val GLOW_MID_STOP = 0.45f
private val GLOW_RADIUS = 150.dp
private const val GLOW_VERTICAL_SCALE = 0.7f

/**
 * 玻璃的边(照手势侧边栏的液态玻璃):亮边宽 1dp;亮边里面的弧面是三道 1.5dp 的白线,由外往里越来越淡,最亮 16% 白。
 * 光从哪边来照底栏亮边的算法:光源位置减去参考点(0.5, 0.7)就是方向,竖直拿着时从左上来
 */
private val RIM_WIDTH = 1.dp
private val LENS_STEP = 1.5.dp
private val LENS_FALLOFF = floatArrayOf(1f, 0.45f, 0.15f)
private const val LENS_ALPHA = 0.16f

/** 堆叠露边的细阴影:前一张卡的下沿往下 4dp 内淡出 */
private val PEEK_SHADOW = 4.dp
private const val PEEK_SHADOW_ALPHA = 0.14f

/** 玻璃的参数(像素),由设置和深浅色模式算出 */
@Immutable
internal class GlassSpec(
    val highlightAlpha: Float,
    val surface: Color,
    val shadowAlpha: Float,
    val pressGlow: Boolean,
    val pressGrowPx: Float,
) {
    companion object {
        fun of(s: LiquidSettings, dark: Boolean, density: Density): GlassSpec = with(density) {
            GlassSpec(
                highlightAlpha = s.highlight / 100f,
                surface = Color.White.copy(alpha = (if (dark) s.surfaceDark else s.surfaceLight) / 100f),
                // 底栏:浅色 10%、深色 20%;设置里调的是浅色的数字,深色加倍
                shadowAlpha = (s.shadow / 100f * if (dark) 2f else 1f).coerceIn(0f, 1f),
                pressGlow = s.pressGlow,
                pressGrowPx = PRESS_GROW.toPx(),
            )
        }
    }
}

/** 每张玻璃共用的东西:背景层(玻璃取背后内容的地方)、光从哪边来(底栏那道亮边的光源)、参数 */
@Stable
internal class GlassEnv(
    val backdrop: Backdrop,
    val light: State<Offset>,
    val spec: GlassSpec,
)

/** 按下时的放大倍数:照底栏多出 16dp,最多 6%;卡片和它外面的阴影共用 */
private fun pressScale(width: Float, growPx: Float, press: Float): Float {
    val w = width.coerceAtLeast(1f)
    return lerp(1f, 1f + min(growPx / w, PRESS_GROW_MAX), press)
}

/**
 * 液态玻璃的玻璃本身:玻璃底层绘制(上游玻璃库的 drawBackdrop,不加任何效果、不用它的亮边和影子),
 * 表面一层白,外面一圈影子,按下时放大。[surface] 画在内容下面,[front] 画在内容上面。
 * 玻璃的边(细亮边和弧面)不在这里画,由放在内容最后的 [GlassHighlight] 画,在内容上面。
 *
 * 卡片后面是真实的桌面或应用(通知中心背景是透明的,不截图),拿不到它的画面,所以不加模糊、弯折这些
 * 作用在背后画面上的效果;卡片是透明玻璃。
 */
internal fun Modifier.liquidGlass(
    env: GlassEnv,
    shape: RoundedCornerShape,
    press: () -> Float = { 0f },
    surface: (DrawScope.() -> Unit)? = null,
    front: (DrawScope.() -> Unit)? = null,
): Modifier {
    val spec = env.spec
    return this
        .outerShadow(shape, spec.shadowAlpha) { width -> pressScale(width, spec.pressGrowPx, press()) }
        .drawBackdrop(
            backdrop = env.backdrop,
            shape = { shape },
            effects = {
                blur(FrostStyle.BLUR_DP.dp.toPx())
                lens(FrostStyle.LENS_INSET_DP.dp.toPx(), FrostStyle.LENS_SHIFT_DP.dp.toPx() * (1f + press() * 0.12f))
            },
            highlight = null,
            shadow = null,
            layerBlock = {
                val s = pressScale(size.width, spec.pressGrowPx, press())
                scaleX = s
                scaleY = s
            },
            onDrawSurface = {
                drawRect(spec.surface)
                surface?.invoke(this)
            },
            onDrawFront = front,
        )
}

/**
 * 玻璃的边,照手势侧边栏那一版液态玻璃复制、改写(用户 2026-09-28「你这次写的液态玻璃效果其实挺好的，我还挺喜欢的……
 * 顺带着把我们的液态通知堆叠和液态控制中心也改一下」):最外沿一道 1dp 的细亮边——光照过来的那一角最亮、对角次亮、
 * 两侧中段几乎没有;亮边里面约 4.5dp 的一窄条弧面(三道由外往里越来越淡的白线),像光在圆边上聚拢。
 * 都是普通的抗锯齿描边,圆角上不再有原来那道着色程序画的亮边的锯齿毛边(用户 2026-09-28「边缘有锯齿毛边，
 * 这是液态通知和控制中心一直的问题」)。光从哪边来照原来的亮边:底栏的角度,随手机倾斜转动(设置里可关),强度用「亮边强度」。
 * 放在玻璃元素内容的最后、铺满它:在内容上面,跟着玻璃一起按下放大、按玻璃的外形裁边。
 */
@Composable
internal fun GlassHighlight(env: GlassEnv, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    val spec = env.spec
    if (spec.highlightAlpha <= 0f) return
    Canvas(modifier) {
        val rr = (shape.createOutline(size, layoutDirection, this) as? Outline.Rounded)?.roundRect ?: return@Canvas
        drawGlassEdge(rr.topLeftCornerRadius.x, env.light.value, spec.highlightAlpha)
    }
}

/** 画亮边和弧面:铺满这一块,圆角 [corner],光从 [light] 那一角来,整体不透明度 [alpha] */
internal fun DrawScope.drawGlassEdge(corner: Float, light: Offset, alpha: Float) {
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f || alpha <= 0.002f) return
    val r = corner.coerceAtMost(min(w, h) / 2f)
    // 渐变从最亮的那一处(沿光的方向最远的那一角或那条边)到正对面,光转到哪个方向,最亮处都是 95%
    val half = abs(light.x) * w / 2f + abs(light.y) * h / 2f
    val start = center + light * half
    val end = center - light * half
    val rim = RIM_WIDTH.toPx()
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.25f to Color.White.copy(alpha = 0.28f),
            0.5f to Color.White.copy(alpha = 0.08f),
            0.75f to Color.White.copy(alpha = 0.22f),
            1f to Color.White.copy(alpha = 0.7f),
            start = start,
            end = end,
        ),
        Offset(rim / 2f, rim / 2f),
        Size(w - rim, h - rim),
        CornerRadius(max(0f, r - rim / 2f)),
        alpha = alpha,
        style = Stroke(rim),
    )
    val step = LENS_STEP.toPx()
    val lens = Brush.linearGradient(
        0f to Color.White.copy(alpha = LENS_ALPHA),
        0.3f to Color.White.copy(alpha = LENS_ALPHA * 0.25f),
        0.5f to Color.White.copy(alpha = LENS_ALPHA * 0.1f),
        0.7f to Color.White.copy(alpha = LENS_ALPHA * 0.25f),
        1f to Color.White.copy(alpha = LENS_ALPHA * 0.7f),
        start = start,
        end = end,
    )
    // 背景折射已表现弧面，只保留一道淡内光，不再叠三圈线。
    for (i in 0..0) {
        val inset = rim + step * i + step / 2f
        if (w <= inset * 2f || h <= inset * 2f) break
        drawRoundRect(
            lens,
            Offset(inset, inset),
            Size(w - inset * 2f, h - inset * 2f),
            CornerRadius(max(0f, r - inset)),
            alpha = alpha * LENS_FALLOFF[i],
            style = Stroke(step),
        )
    }
}

/**
 * 卡片外面的阴影(照底栏:半径 10dp 的黑色柔影)。卡片是透明玻璃,阴影只画在卡片外面,
 * 不透进卡片里发黑;按下放大时跟着卡片一起放大。
 */
private fun Modifier.outerShadow(shape: Shape, alpha: Float, scaleOf: (width: Float) -> Float): Modifier {
    if (alpha <= 0f) return this
    return drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val outline = path.asAndroidPath()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((alpha * 255f).roundToInt().coerceIn(0, 255), 0, 0, 0)
            maskFilter = BlurMaskFilter(SHADOW_RADIUS.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        onDrawBehind {
            val s = scaleOf(size.width)
            scale(scaleX = s, scaleY = s, pivot = center) {
                clipPath(path, ClipOp.Difference) {
                    drawIntoCanvas { it.nativeCanvas.drawPath(outline, paint) }
                }
            }
        }
    }
}

/** 流光:沿卡片上边缘走一道 1.5dp 宽的亮光,[progress] 从 0 到 1 走完一次,亮度按 sin(进度·π) 起落 */
internal fun DrawScope.flowLight(progress: Float, cornerRadiusPx: Float) {
    if (progress <= 0f || progress >= 1f) return
    val strength = (sin(progress * PI) * FLOW_ALPHA).toFloat()
    if (strength <= 0.003f) return
    val segment = size.width * FLOW_WIDTH_FRACTION
    val x0 = -segment + (size.width + segment) * progress
    val brush = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.5f to Color.White.copy(alpha = strength),
        1f to Color.Transparent,
        startX = x0,
        endX = x0 + segment,
    )
    val band = min(size.height * 0.45f, FLOW_BAND.toPx())
    // 线画在卡片边上,外面一半被卡片形状裁掉,留在里面的正好是亮边的宽度
    clipRect(bottom = band) {
        drawRoundRect(brush, cornerRadius = CornerRadius(cornerRadiusPx), style = Stroke(width = FLOW_STROKE.toPx() * 2f))
    }
}

/** 氛围光:通知主色从 [center](应用图标处)向外淡出,只画在卡片里 */
internal fun DrawScope.ambientGlow(color: Color, center: Offset) {
    val r = GLOW_RADIUS.toPx()
    val brush = Brush.radialGradient(
        0f to color.copy(alpha = GLOW_ALPHA),
        GLOW_MID_STOP to color.copy(alpha = GLOW_MID_ALPHA),
        1f to color.copy(alpha = 0f),
        center = center,
        radius = r,
    )
    scale(scaleX = 1f, scaleY = GLOW_VERTICAL_SCALE, pivot = center) {
        drawCircle(brush, radius = r, center = center)
    }
}

/** 堆叠露边的细阴影:前一张卡的下沿([edgeY],这一层自己的坐标)往下淡出 */
internal fun DrawScope.peekShadow(edgeY: Float) {
    val h = PEEK_SHADOW.toPx()
    drawRect(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = PEEK_SHADOW_ALPHA), 1f to Color.Transparent, startY = edgeY, endY = edgeY + h),
        topLeft = Offset(0f, edgeY),
        size = Size(size.width, h),
    )
}
