package com.itos.coui.button

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.DialogButtonDefaults
import com.itos.coui.dialog.DialogTextKind
import com.itos.coui.dialog.rememberDialogTextStyle
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent

enum class CouiButtonStyle { Filled, Tinted, Plain, Destructive }
enum class CouiButtonSize { Small, Medium }

/**
 * 页面操作按钮；点击和启用状态由调用方控制，宽度由调用方决定。尺寸取 ColorOS COUIButton：
 * Medium 为大号（最小高 44dp、最小宽 132dp、左右 12dp 上下 8dp 留白、16sp），Small 为小号
 * （最小高 28dp、最小宽 52dp、左右 8dp 上下 6dp 留白、14sp），胶囊形。来源见 RESOURCE_SOURCES.md。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Button(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    style: CouiButtonStyle = CouiButtonStyle.Filled,
    size: CouiButtonSize = CouiButtonSize.Medium,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = CouiTheme.isDark
    val theme = CouiTheme.colorScheme
    val primary = DialogButtonDefaults.primaryColors(theme.accent)
    val secondary = DialogButtonDefaults.secondaryColors(dark)
    val destructive = CouiDialogDefaults.colors(dark).destructive
    val surface = when (style) {
        CouiButtonStyle.Filled -> if (enabled) primary.surface else primary.disabledSurface
        // 危险操作与次要按钮同一层灰底，只把文字换成危险色，放在页面或底栏上都有按钮的外形
        CouiButtonStyle.Tinted, CouiButtonStyle.Destructive -> if (enabled) secondary.surface else secondary.disabledSurface
        CouiButtonStyle.Plain -> Color.Transparent
    }
    val foreground = when (style) {
        CouiButtonStyle.Filled -> if (enabled) primary.content else primary.disabledContent
        CouiButtonStyle.Tinted -> if (enabled) secondary.content else secondary.disabledContent
        CouiButtonStyle.Plain -> if (enabled) theme.accentOnSurface else theme.accentOnSurface.copy(alpha = .3f)
        CouiButtonStyle.Destructive -> if (enabled) destructive else destructive.copy(alpha = .3f)
    }
    val source = remember { MutableInteractionSource() }
    val motion = rememberCouiTouchMotion(enabled)
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(interactive = enabled, dark = dark,
        preset = if (style == CouiButtonStyle.Filled) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.OpaqueMedium2,
        cancelOnConsumedMove = true)
    val small = size == CouiButtonSize.Small
    Row(modifier.defaultMinSize(minWidth = if (small) 52.dp else 132.dp, minHeight = if (small) 28.dp else 44.dp).graphicsLayer {
        scaleX = motion.scaleX.value + press.scale.value - 1f
        scaleY = motion.scaleY.value + press.scale.value - 1f
        translationX = motion.translationX.value
        translationY = motion.translationY.value
    }.couiTouchMotion(motion, enabled, cancelOnConsumedMove = true)
        .couiPressMotion(press, enabled, cancelOnConsumedMove = true).then(lightInput)
        .clip(DialogButtonDefaults.shape).drawWithContent { drawContent(); drawLight?.invoke(this) }
        .background(surface).couiButtonFeedback(dark, source, enabled, pressFeedback = true)
        .combinedClickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button,
            onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 6.dp else 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val text = rememberDialogTextStyle(DialogTextKind.Button)
        ProvideCouiContent(color = foreground, textStyle = if (small) text.copy(fontSize = 14.sp) else text.copy(fontSize = 16.sp),
            content = { content() })
    }
}
