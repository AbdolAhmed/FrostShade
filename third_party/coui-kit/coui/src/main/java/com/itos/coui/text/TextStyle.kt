package com.itos.coui.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiTextStyle

/** 使用显式或当前内容样式显示普通文字。 */
@Composable
fun Text(modifier: Modifier = Modifier, text: String, style: TextStyle = LocalCouiTextStyle.current,
    color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true) {
    val resolvedColor = if (color.isSpecified) color else if (style.color.isSpecified) style.color else CouiTheme.contentColor
    BasicText(text, modifier, style.merge(TextStyle(color = resolvedColor, fontSize = fontSize, fontWeight = fontWeight,
        textAlign = textAlign ?: TextAlign.Unspecified, lineHeight = lineHeight)),
        maxLines = maxLines, overflow = overflow, softWrap = softWrap)
}
