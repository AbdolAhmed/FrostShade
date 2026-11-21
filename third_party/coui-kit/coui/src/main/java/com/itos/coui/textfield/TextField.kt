package com.itos.coui.textfield

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.itos.coui.R
import com.itos.coui.dialog.DialogTextKind
import com.itos.coui.dialog.rememberDialogTextStyle
import com.itos.coui.theme.CouiTheme

/**
 * 字符串由调用方持有；密码显隐也由调用方受控。
 * underline 为 true 时是独立输入框，自带下划线与聚焦线；放进 Group 的行内时传 false，由行间分隔线分隔。
 */
@Composable
fun TextField(
    modifier: Modifier = Modifier, value: String, onValueChange: (String) -> Unit,
    enabled: Boolean = true, label: String? = null, placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isPassword: Boolean = false, passwordVisible: Boolean = false, onPasswordVisibilityChange: ((Boolean) -> Unit)? = null,
    underline: Boolean = true,
) {
    FieldFrame(modifier, value.isEmpty(), enabled, label, placeholder, isPassword, passwordVisible, onPasswordVisibilityChange, underline) { field, style ->
        BasicTextField(value = value, onValueChange = onValueChange, modifier = field, enabled = enabled,
            textStyle = style, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(CouiTheme.colorScheme.accent))
    }
}

/** 直接使用调用方的编辑状态，保留选区、组合文本及光标位置。 */
@Composable
fun TextField(
    modifier: Modifier = Modifier, state: TextFieldState,
    enabled: Boolean = true, label: String? = null, placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, onKeyboardAction: KeyboardActionHandler? = null,
    singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isPassword: Boolean = false, passwordVisible: Boolean = false, onPasswordVisibilityChange: ((Boolean) -> Unit)? = null,
    underline: Boolean = true,
) {
    val mask = remember { OutputTransformation { for (i in 0 until length) replace(i, i + 1, "•") } }
    FieldFrame(modifier, state.text.isEmpty(), enabled, label, placeholder, isPassword, passwordVisible, onPasswordVisibilityChange, underline) { field, style ->
        BasicTextField(state = state, modifier = field, enabled = enabled, textStyle = style,
            keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction,
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minLines, maxLines),
            outputTransformation = if (isPassword && !passwordVisible) mask else null,
            cursorBrush = SolidColor(CouiTheme.colorScheme.accent))
    }
}

@Composable
private fun FieldFrame(modifier: Modifier, empty: Boolean, enabled: Boolean, label: String?, placeholder: String?,
    isPassword: Boolean, passwordVisible: Boolean, onPasswordVisibilityChange: ((Boolean) -> Unit)?, underline: Boolean,
    editor: @Composable (Modifier, TextStyle) -> Unit) {
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorScheme
    val style = rememberDialogTextStyle(DialogTextKind.Input).copy(color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .3f))
    var focused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        progress.snapTo(0f)
        if (focused) progress.animateTo(1f, tween(250, easing = CubicBezierEasing(0f, 0f, .1f, 1f)))
    }
    val divider = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)
    val hint = if (dark) Color(0x4DFFFFFF) else Color(0x42000000)
    Column(modifier) {
        if (label != null) BasicText(label, style = style.copy(color = colors.onSurfaceVariant))
        Row(Modifier.fillMaxWidth().drawBehind {
            if (!underline) return@drawBehind
            val normal = .33000004.dp.roundToPx().coerceAtLeast(1).toFloat()
            val active = 1.dp.roundToPx().coerceAtLeast(1).toFloat()
            drawRect(divider, Offset(0f, size.height - normal), Size(size.width, normal))
            if (focused) drawRect(colors.accent, Offset(0f, size.height - active), Size(size.width * progress.value, active))
        // 独立输入上下各 15dp(coui_input_edit_text_no_title_padding_top/bottom);分组行内与行的 contentPadding
        // 合计 13dp(coui_input_edit_text_no_title_padding_top/bottom_inPreference),见 RESOURCE_SOURCES.md
        }.padding(vertical = if (underline) 15.dp else 13.dp - com.itos.coui.layout.CouiPageDefaults.contentPadding),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (empty && placeholder != null) BasicText(placeholder, style = style.copy(color = hint))
                editor(Modifier.fillMaxWidth().heightIn(min = 22.dp)
                    .then(if (isPassword) Modifier.semantics { password() } else Modifier)
                    .onFocusChanged { focused = it.isFocused }, style)
            }
            if (isPassword && onPasswordVisibilityChange != null) {
                BasicText(stringResource(if (passwordVisible) R.string.coui_password_hide else R.string.coui_password_show),
                    Modifier.padding(start = 8.dp).clickable(enabled = enabled, role = Role.Button) { onPasswordVisibilityChange(!passwordVisible) }
                        .padding(8.dp), style = style.copy(color = colors.accentOnSurface))
            }
        }
    }
}
