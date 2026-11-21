package com.itos.coui.progress

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.DialogLoadingIndicator
import com.itos.coui.theme.CouiTheme

/** 行内不定进度；任务状态由宿主持有。 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, color: Color = CouiTheme.colorScheme.onSurface, size: Dp = 24.dp) {
    DialogLoadingIndicator(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }, color)
}
