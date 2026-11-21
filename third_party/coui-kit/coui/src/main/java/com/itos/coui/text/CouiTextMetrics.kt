package com.itos.coui.text

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 共用 TextView 字体度量和行距算法，角色、字体来源及预设仍由组件持有。 */
internal fun couiTextStyle(typeface: Typeface, weight: Int, pixels: Float, lineSpacing: Float,
    includeFontPadding: Boolean, density: Density): TextStyle {
    val metrics = Paint().apply { this.typeface = typeface; textSize = pixels }.fontMetricsInt
    val base = metrics.descent - metrics.ascent
    // lineSpacingMultiplier 作用于 ascent/descent；额外间距在行下方，末行不加。
    val height = base + (base * (lineSpacing - 1f)).roundToInt()
    return TextStyle(fontFamily = FontFamily(typeface), fontWeight = FontWeight(weight), fontSynthesis = FontSynthesis.None,
        fontSize = with(density) { pixels.toSp() }, lineHeight = with(density) { height.toFloat().toSp() },
        platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Top,
            trim = LineHeightStyle.Trim.LastLineBottom, mode = LineHeightStyle.Mode.Minimum))
}

/** 限制系统字号倍率后直接换算基准 sp，避免 Android 非线性缩放的逆算误差。 */
internal fun couiAdaptFontSize(sizeSp: Float, configuration: Configuration, density: Density, maxScale: Float): Float {
    val exempt = configuration.densityDpi == 300 || configuration.densityDpi == 296 ||
        configuration.smallestScreenWidthDp <= 210
    val scale = if (exempt) density.fontScale else density.fontScale.coerceAtMost(maxScale)
    return with(Density(density.density, scale)) { sizeSp.sp.toPx().roundToInt().toFloat() }
}
