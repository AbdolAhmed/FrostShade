package com.itos.coui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.slider.CouiSliderDefaults
import com.itos.coui.theme.CouiTheme

/** 确定进度的水平指示器，进度范围为 0..1；状态由宿主持有。 */
@Composable
fun ProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float,
    color: Color = CouiTheme.colorScheme.accent,
    trackColor: Color = CouiSliderDefaults.colors(CouiTheme.colorScheme.accent, CouiTheme.isDark).inactiveTrack,
) {
    require(progress.isFinite()) { "进度必须为有限数值" }
    val fraction = progress.coerceIn(0f, 1f)
    Canvas(modifier.defaultMinSize(minWidth = 144.dp, minHeight = 4.dp).progressSemantics(fraction)) {
        val radius = size.height / 2f
        drawRoundRect(trackColor, cornerRadius = CornerRadius(radius))
        if (fraction > 0f) {
            val width = size.width * fraction
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
            drawRoundRect(color, topLeft = Offset(left, 0f), size = Size(width, size.height),
                cornerRadius = CornerRadius(minOf(radius, width / 2f)))
        }
    }
}
