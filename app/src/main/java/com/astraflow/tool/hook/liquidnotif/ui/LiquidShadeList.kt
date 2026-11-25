package com.astraflow.tool.hook.liquidnotif.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.astraflow.tool.config.NotificationBadgeSpec
import com.astraflow.tool.hook.liquidnotif.LiquidLog
import com.astraflow.tool.hook.liquidnotif.model.LiquidAction
import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.emptyBackdrop

private val SIDE = 16.dp
private val CARD_GAP = 8.dp
private val GROUP_GAP = 12.dp
private val CARD_RADIUS = 24.dp
private val CardShape = RoundedCornerShape(CARD_RADIUS)
private val PillShape = RoundedCornerShape(50)
private val MenuShape = RoundedCornerShape(20.dp)
private val ICON_SIZE = 40.dp
private val BOTTOM_SPACE = 104.dp
private val TOP_FADE = 20.dp

/** 列表底边渐隐的高度 */
private val BOTTOM_FADE = 56.dp

/** 下拉时卡片从上方这么远的地方滑到原位 */
private val RISE = 18.dp

/** 清除后空出的位置收拢:不回弹,约 0.25 秒 */
private val LEAVE_COLLAPSE = spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = 0.002f)

/** 应用自己撤回的通知淡出的时长(之后再收拢位置) */
private const val VANISH_MS = 220

/** 按住堆叠往下拉:拉这么远展开到底;松手时往下甩得比这个快(像素/秒)也展开 */
private val PULL_EXPAND_RANGE = 180.dp
private const val PULL_EXPAND_FLING = 900f

/** 滚到头的弹性:最多拉开这么远;每往外一项多挪的比例和最多算几项;拉动的费劲程度 */
private val ELASTIC_MAX = 140.dp
private const val ELASTIC_SPREAD = 0.15f
private const val ELASTIC_MAX_STEPS = 6
private const val ELASTIC_RESISTANCE = 0.5f

/** 快速滑到头时轻轻弹一下:剩余速度至少这么快才弹,弹的初速度按剩余速度的比例,最多这么快(像素/秒) */
private const val ELASTIC_BUMP_MIN = 300f
private const val ELASTIC_BUMP = 0.08f
private const val ELASTIC_BUMP_MAX = 900f

/** 拉开通知中心、展开堆叠、堆叠里来了新通知后这么久以内才走流光;之后才进入画面的卡片不走 */
private const val FLOW_WINDOW_MS = 1_500L
private const val FLOW_DURATION_MS = 900
private const val FLOW_OPEN_STAGGER_MS = 60L
private const val FLOW_EXPAND_STAGGER_MS = 80L

/** 通知中心开着时新到的通知,在这么久以内进入画面才做落入动画(滚动进入画面的不做) */
private const val ARRIVAL_WINDOW_MS = 1_200L

/** 字色在深浅之间切换的时长 */
private const val LOOK_FADE_MS = 320

/** 卡片共用的东西:玻璃、字色、字体 */
@Stable
internal class CardEnv(val glass: GlassEnv, val look: LiquidLook, val fonts: LiquidFonts)

private fun textStyle(family: FontFamily, sizeSp: Float, lineHeight: Float, color: Color, shadow: androidx.compose.ui.graphics.Shadow?) = TextStyle(
    fontFamily = family,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * lineHeight).sp,
    color = color,
    shadow = shadow,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * 通知中心里我们画的全部通知。放在系统通知列表原来的位置上,顶部留白与系统一致;
 * 超出区域的部分往上滚进顶部时渐隐。卡片后面是真实的桌面或应用(背景透明,不截图),卡片是透明玻璃。
 */
@Composable
fun LiquidShadeList(
    state: LiquidUiState,
    fonts: LiquidFonts,
    callbacks: LiquidCallbacks,
    modifier: Modifier = Modifier,
    previewBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    if (!state.shown) return
    val density = LocalDensity.current
    val coordinates = remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val matrix = remember { android.graphics.Matrix() }
    val bitmapPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
    val backgroundSource = state.backgroundSource
    // 只记录读取到的背景，通知内容不进入玻璃的输入画面。
    val recorded = com.kyant.backdrop.backdrops.rememberLayerBackdrop(onDraw = {
        val frame = backgroundSource?.frame?.value
        val location = coordinates.value
        if (frame != null && location != null && com.astraflow.tool.hook.glass.screenToLocal(location, matrix)) {
            drawIntoCanvas { frame.draw(it.nativeCanvas, matrix, bitmapPaint) }
        }
    })
    val backdrop = previewBackdrop ?: recorded
    val light = rememberGlassLight(state.settings.highlightTilt)
    val spec = remember(state.settings, state.dark, density) { GlassSpec.of(state.settings, state.dark, density) }
    val glass = remember(backdrop, light, spec) { GlassEnv(backdrop, light, spec) }
    val look = animatedLook(state.look)
    val env = remember(glass, look, fonts) { CardEnv(glass, look, fonts) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates.value = it }
            .layerBackdrop(recorded),
    ) {
        CardList(state, env, callbacks)
        ClearAllGlass(state, env)
        MenuLayer(state, env, callbacks)
    }
}

/** 字色在深浅之间切换时逐渐过渡,不一下子跳 */
@Composable
private fun animatedLook(target: LiquidLook): LiquidLook {
    val title by animateColorAsState(target.title, tween(LOOK_FADE_MS), label = "title")
    val body by animateColorAsState(target.body, tween(LOOK_FADE_MS), label = "body")
    val secondary by animateColorAsState(target.secondary, tween(LOOK_FADE_MS), label = "secondary")
    val chip by animateColorAsState(target.chip, tween(LOOK_FADE_MS), label = "chip")
    val track by animateColorAsState(target.track, tween(LOOK_FADE_MS), label = "track")
    return LiquidLook(title, body, secondary, chip, track, target.textShadow)
}

/** 下拉时卡片按位置依次出现:越靠上的越早出现;收起时反过来,越靠下的越早消失 */
private fun cascade(expansion: Float, order: Int): Float {
    val start = 0.04f + 0.05f * order.coerceAtMost(8)
    val t = ((expansion - start) / 0.38f).coerceIn(0f, 1f)
    val u = 1f - t
    return 1f - u * u * u
}

/** 列表里卡片以外的一项(底部按钮、没有通知时的那句话)也跟着依次出现 */
private fun Modifier.cascadeIn(state: LiquidUiState, order: Int, risePx: Float): Modifier = graphicsLayer {
    val c = cascade(state.expansion, order)
    alpha = c
    compositingStrategy = FADE_WITHOUT_BOX
    translationY = -(1f - c) * risePx
}

/**
 * 淡入淡出时透明度逐笔作用在画面上,不先画进一块和元素一样大的方框再淡化:
 * 放大出去的部分和卡片外面的阴影不会被方框切掉(用户 2026-09-26「四周好像会有一个矩形，会把它割裂开」)
 */
private val FADE_WITHOUT_BOX = CompositingStrategy.ModulateAlpha

/** 堆叠里裁掉被挡住的部分时,左右和下方多留这么多给卡片外面的阴影 */
private val SHADOW_ROOM = 24.dp

/**
 * 只画 [top](这一层自己的坐标)以下的部分,左右和下方多留 [pad] 给阴影;[top] 不大于 0 时不裁。
 * 堆叠里后方的卡片被前一张挡住的部分不画:卡片是透明的,不裁会从前一张里透出轮廓
 * (用户 2026-09-26「被堆叠的消息是不是应该只露出底边？」)
 */
private fun GraphicsLayerScope.revealBelow(top: Float, pad: Float) {
    if (top <= 0f) {
        clip = false
        return
    }
    clip = true
    shape = BelowShape(top, pad)
}

private data class BelowShape(val top: Float, val pad: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(-pad, top, size.width + pad, size.height + pad))
}

/**
 * 只画 [bottom](这一层自己的坐标)以上的部分,左右和上方多留 [pad];[bottom] 不小于这一层的高度 [height] 时不裁。
 * 标题行收拢时下面的卡片往上补,盖住的部分不画:卡片是透明的,不裁会从卡片里透出标题行。
 */
private fun GraphicsLayerScope.revealAbove(bottom: Float, height: Int, pad: Float) {
    if (bottom >= height) {
        clip = false
        return
    }
    clip = true
    shape = AboveShape(bottom, pad)
}

private data class AboveShape(val bottom: Float, val pad: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(-pad, -pad, size.width + pad, bottom))
}

/** 只画 [left](这一层自己的坐标)右边的部分:左滑露出的按钮只画在卡片挪开后空出来的那一截,不从透明卡片里透出来 */
private data class RightOfShape(val left: Float, val pad: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(left, -pad, size.width + pad, size.height + pad))
}

/**
 * 横划松手时怎么办(纯计算)。没有左滑选项时([optionsWidth] 为 0)左右滑一样:滑过三分之一或顺着方向甩得够快就清除,否则弹回。
 * 有左滑选项时(「置顶与左滑选项」,公开反馈 #80)只有向右清除;向左滑过按钮宽度的一半或向左甩,停在露出按钮的位置,否则收回。
 * [offset] 是卡片此刻挪开的距离(像素,往右为正),[velocity] 是松手时的速度(像素/秒)。
 */
internal object SwipeRule {
    enum class Outcome { DISMISS, REVEAL, REST }

    private const val DISMISS_FLING = 1800f
    private const val REVEAL_FLING = 600f

    fun decide(offset: Float, velocity: Float, width: Float, optionsWidth: Float): Outcome {
        if (optionsWidth <= 0f) {
            val far = abs(offset) > width / 3f
            val fast = abs(velocity) > DISMISS_FLING && sign(velocity) == sign(offset)
            return if (far || fast) Outcome.DISMISS else Outcome.REST
        }
        if (offset > 0f) {
            return if (offset > width / 3f || velocity > DISMISS_FLING) Outcome.DISMISS else Outcome.REST
        }
        return when {
            velocity > REVEAL_FLING -> Outcome.REST
            -offset > optionsWidth / 2f || velocity < -REVEAL_FLING -> Outcome.REVEAL
            else -> Outcome.REST
        }
    }

    /** 向左拖过按钮的宽度后越拖越费劲 */
    fun resist(raw: Float, optionsWidth: Float): Float =
        if (optionsWidth > 0f && raw < -optionsWidth) -optionsWidth + (raw + optionsWidth) * 0.25f else raw
}

/**
 * 系统「清除全部通知」按钮的玻璃样子:摆在系统按钮原来的位置(系统按钮本身藏起来但仍能点),
 * 按下时跟着系统按钮的按下状态一起动,图标按卡片字色着色。
 */
@Composable
private fun ClearAllGlass(state: LiquidUiState, env: CardEnv) {
    val info = state.clearAll ?: return
    val press by animateFloatAsState(if (state.clearPressed) 1f else 0f, spring(0.5f, 300f), label = "clear")
    val look = env.look
    val density = LocalDensity.current
    Box(
        Modifier
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints.fixed(info.rect.width.coerceAtLeast(1), info.rect.height.coerceAtLeast(1)))
                layout(0, 0) { p.place(info.rect.left, info.rect.top) }
            }
            .graphicsLayer {
                alpha = state.clearAlpha.coerceIn(0f, 1f) * cascade(state.expansion, 0)
                compositingStrategy = FADE_WITHOUT_BOX
            }
            .liquidGlass(env.glass, PillShape, press = { press }),
        contentAlignment = Alignment.Center,
    ) {
        val icon = info.icon
        val label = info.label
        if (icon != null) {
            val w = with(density) { icon.width.toDp() }
            val h = with(density) { icon.height.toDp() }
            Image(icon, contentDescription = null, modifier = Modifier.size(w, h), colorFilter = ColorFilter.tint(look.title))
        } else if (label != null) {
            BasicText(label, style = textStyle(env.fonts.medium, 14f, 1.2f, look.title, look.textShadow))
        }
        GlassHighlight(env.glass, PillShape, Modifier.matchParentSize())
    }
}

@Composable
private fun CardList(state: LiquidUiState, env: CardEnv, callbacks: LiquidCallbacks) {
    val density = LocalDensity.current
    val top = with(density) { state.topPadding.toDp() }
    val fade = if (top > TOP_FADE) TOP_FADE else top
    val scope = rememberCoroutineScope()
    val elastic = remember(callbacks, density) { ElasticEdges(scope, callbacks, with(density) { ELASTIC_MAX.toPx() }) }
    // 滚动列表时收回露出的左滑选项(「置顶与左滑选项」)
    LaunchedEffect(state) {
        snapshotFlow { state.list.isScrollInProgress }.collect { if (it && state.revealed != null) state.revealed = null }
    }
    val look = env.look
    val risePx = with(density) { RISE.toPx() }
    // 用户清除了、系统还没从列表里拿掉的先藏起来(见 LiquidUiState.hidden);一组全藏完就不显示这一组
    val groups = state.groups.mapNotNull { g ->
        val items = g.items.filter(state::shows)
        when {
            items.isEmpty() -> null
            items.size == g.items.size -> g
            else -> g.copy(items = items)
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(top = top - fade)
            .fadeEdges(fade, BOTTOM_FADE),
    ) {
        // 「卡片宽度」(公开反馈 #33):卡片占原来宽度的百分之多少,左右留白一样
        val side = SIDE + (maxWidth - SIDE * 2) * (1f - state.settings.cardWidth / 100f) / 2f
        LazyColumn(
            state = state.list,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { state.listTop = it.positionInRoot().y }
                .nestedScroll(elastic),
            contentPadding = PaddingValues(start = side, end = side, top = fade, bottom = BOTTOM_SPACE),
            // 组与组的间距放在每一组里面(见 GroupBlock),一组清除收拢时间距一起收掉
            overscrollEffect = null,
        ) {
            itemsIndexed(groups, key = { _, g -> g.id }) { index, group ->
                GroupBlock(
                    group = group,
                    order = index,
                    state = state,
                    env = env,
                    callbacks = callbacks,
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(dampingRatio = 0.86f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold),
                            fadeOutSpec = tween(180),
                        )
                        .graphicsLayer { translationY = elastic.offset(index, groups.size + 1) },
                )
            }
            val footer = state.footer
            if (footer != null && groups.isNotEmpty() && (footer.manage != null || footer.clearAll != null)) {
                item(key = "footer") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .graphicsLayer { translationY = elastic.offset(groups.size, groups.size + 1) }
                            .cascadeIn(state, groups.size, risePx),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        footer.manage?.let { GlassPill(it, env, callbacks::manage) }
                        footer.clearAll?.let { GlassPill(it, env, callbacks::clearAll) }
                    }
                }
            }
            val empty = state.emptyText
            if (groups.isEmpty() && empty != null) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp).animateItem().cascadeIn(state, 0, risePx), contentAlignment = Alignment.Center) {
                        BasicText(empty, style = textStyle(env.fonts.medium, 15f, 1.3f, look.secondary, look.textShadow))
                    }
                }
            }
        }
    }
}

/**
 * 一组通知。堆叠时(照苹果的数字)最上层卡片完整,后方露出最多两层空白玻璃,每层露出 8dp、
 * 宽度依次乘 94%,略微压暗,露边处一道细阴影;点按展开成一列,上方出现应用名和「收起」。
 * 展开和收起之间按进度摆放:后方的卡片从最上层卡片下面滑出来。
 * 通知中心开着时新到的一组从上方轻轻落入;堆叠里来了新通知时最上层卡片轻弹一下并走一次流光。
 */
@Composable
private fun GroupBlock(
    group: LiquidGroup,
    order: Int,
    state: LiquidUiState,
    env: CardEnv,
    callbacks: LiquidCallbacks,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val stackable = settings.stack && group.items.size > 1
    val open = !stackable || group.id in state.expanded
    val progress = remember(group.id) { Animatable(if (open) 1f else 0f) }
    var composeAll by remember(group.id) { mutableStateOf(open) }
    var expandedAt by remember(group.id) { mutableLongStateOf(0L) }
    // 按住堆叠往下拉时,展开程度跟着手指;松手后交回上面的动画
    var dragProgress by remember(group.id) { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val items by rememberUpdatedState(group.items)
    // 整组清完时这一组(连标题行和下面的间距)收拢:1 为原高,0 为收拢完
    val shrink = remember(group.id) { Animatable(1f) }
    // 组里单张卡片清除后它的位置收拢:通知标识 → 1 到 0
    val leaving = remember(group.id) { mutableStateMapOf<String, Float>() }
    // 按了标题行的叉:当时这一组能清除的依次飞出去(通知标识 → 内容版本;之后新来的、应用又更新的不算)
    var clearingKeys by remember(group.id) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    // 整组滑走后组里还剩不能清除的通知:弹回原位
    var stackReset by remember(group.id) { mutableIntStateOf(0) }
    // 应用自己撤回的(见 LiquidUiState.vanishing):展开摆放的各自淡出收拢,堆叠着的整组撤回时整组淡出收拢
    val vanishing = state.vanishing
    val fadingAll = group.items.all { it.key in vanishing }
    // 飞出去以后:这一组最后一张就整组收拢,否则只收拢这一张的位置;收拢完在我们的列表里藏起
    val collapseGroup: (List<LiquidNotification>) -> Unit = { gone ->
        scope.launch {
            shrink.animateTo(0f, LEAVE_COLLAPSE)
            state.hide(gone)
        }
    }
    val cardGone: (LiquidNotification) -> Unit = { n ->
        val others = items.filter { it.key != n.key && it.key !in leaving }
        if (others.isEmpty()) {
            collapseGroup(items)
        } else {
            leaving[n.key] = 1f
            scope.launch {
                animate(1f, 0f, animationSpec = LEAVE_COLLAPSE) { v, _ -> leaving[n.key] = v }
                state.hide(listOf(n))
                leaving.remove(n.key)
            }
        }
    }
    // 滚出画面、不再画这一组时,还没走完的离开动画直接结束
    DisposableEffect(group.id) {
        onDispose { state.settle(items) }
    }
    LaunchedEffect(open) {
        if (open) {
            if (!composeAll) {
                composeAll = true
                // 只剩一张不再堆叠时不算展开,不走流光
                if (stackable) expandedAt = SystemClock.uptimeMillis()
            }
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
            composeAll = false
        }
    }
    // 新到的一组:从上方轻轻落入(拉开时本来就在的、滚动进入画面的不做)
    val arrival = remember(group.id) {
        val at = state.arrivals[group.id]
        Animatable(if (at != null && SystemClock.uptimeMillis() - at < ARRIVAL_WINDOW_MS) 0f else 1f)
    }
    LaunchedEffect(group.id) {
        if (arrival.value < 1f) arrival.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 360f))
    }
    // 堆叠里来了新通知:最上层卡片换成新的,轻弹一下。最上层清除或撤回后露出的是原来就在组里的,不弹
    val frontKey = group.items.first().key
    val seenKeys = remember(group.id) { HashSet<String>() }
    val frontIsNew = seenKeys.isNotEmpty() && frontKey !in seenKeys
    SideEffect { group.items.mapTo(seenKeys) { it.key } }
    var lastFront by remember(group.id) { mutableStateOf(frontKey) }
    val bump = remember(group.id) { Animatable(1f) }
    var refreshedAt by remember(group.id) { mutableLongStateOf(0L) }
    LaunchedEffect(frontKey) {
        if (frontKey != lastFront) {
            lastFront = frontKey
            if (!frontIsNew) return@LaunchedEffect
            refreshedAt = SystemClock.uptimeMillis()
            bump.snapTo(0f)
            bump.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        }
    }
    val clearable = group.items.filter { it.clearable }
    val blanks = if (stackable) min(group.items.size - 1, settings.stackLayers) else 0
    val shown = if (composeAll) group.items else group.items.take(1)
    val scale = settings.stackScale / 100f
    val peekDp = settings.stackPeekDp.dp
    val dim = settings.stackDim / 100f
    val density = LocalDensity.current
    val dropPx = with(density) { 14.dp.toPx() }
    val risePx = with(density) { RISE.toPx() }
    // 堆叠后方露出的层数变化时平滑过渡:少掉的一层淡出、高度跟着变,多出的一层淡入
    val blankLevel = remember(group.id) { Animatable(blanks.toFloat()) }
    var blankLayers by remember(group.id) { mutableIntStateOf(blanks) }
    LaunchedEffect(blanks) {
        if (blanks > blankLayers) blankLayers = blanks
        blankLevel.animateTo(blanks.toFloat(), LEAVE_COLLAPSE)
        blankLayers = blanks
    }
    val drawnBlanks = max(blanks, blankLayers)
    // 标题行:组里只剩一张(或清除、撤回后只会剩一张)时和空出来的位置一起收拢,不突然消失;1 为完整,0 为收拢完
    val leavingKeys by remember(group.id) { derivedStateOf { leaving.keys.toSet() } }
    val staying = group.items.count { n ->
        n.key !in leavingKeys && n.key !in vanishing && clearingKeys[n.key] != n.version &&
            !(state.clearing && n.clearable && open)
    }
    val headerWanted = stackable && staying != 1
    val header = remember(group.id) { Animatable(if (headerWanted) 1f else 0f) }
    var headerKept by remember(group.id) { mutableStateOf(headerWanted) }
    LaunchedEffect(headerWanted) {
        if (headerWanted) {
            headerKept = true
            header.animateTo(1f, LEAVE_COLLAPSE)
        } else {
            header.animateTo(0f, LEAVE_COLLAPSE)
            headerKept = false
        }
    }
    val hasHeader = stackable || headerKept
    // 露出左滑选项时记下的标识(整组、单张各自一种)
    val groupRevealId = "g:" + group.id

    // 组与组的间距放在这一组里面(列表不再统一加间距),整组收拢时间距一起收掉
    Box(modifier.collapseHeight { shrink.value }.padding(bottom = GROUP_GAP)) {
        SwipeAway(
            enabled = stackable && !open && clearable.isNotEmpty(),
            clearing = state.clearing && clearable.size == group.items.size,
            fading = fadingAll && !open,
            order = order,
            reset = stackReset,
            onClaim = callbacks::claimGesture,
            onLeave = { state.willHide(clearable) },
            onSwiped = {
                callbacks.dismiss(clearable)
                state.willHide(clearable)
            },
            // 「置顶与左滑选项」:向左滑露出整组的「置顶」「设置」
            options = if (settings.swipeOptions) ({
                SwipeOptions(
                    env = env,
                    pinned = group.items.any(state::isPinned),
                    onPin = { state.revealed = null; state.togglePin(items) },
                    onSettings = { state.revealed = null; callbacks.settings(items.first()) },
                )
            }) else null,
            revealed = state.revealed == groupRevealId,
            onReveal = { show -> state.revealed = if (show) groupRevealId else state.revealed.takeIf { it != groupRevealId } },
            onGone = {
                val left = items.filter { it.clearable }
                if (fadingAll || left.size == items.size) {
                    collapseGroup(items)
                } else {
                    // 只清掉能清除的,剩下不能清除的弹回原位
                    state.hide(left)
                    stackReset++
                }
            },
            modifier = Modifier.graphicsLayer {
                // 下拉时按位置依次出现(收起时反过来),通知中心开着时新到的一组从上方落入;整组收拢时一起淡掉
                val a = arrival.value
                val c = cascade(state.expansion, order)
                alpha = (a * c * shrink.value).coerceIn(0f, 1f)
                compositingStrategy = FADE_WITHOUT_BOX
                val s = (0.94f + 0.06f * a) * (0.94f + 0.06f * c)
                scaleX = s
                scaleY = s
                translationY = -(1f - a) * dropPx - (1f - c) * risePx
            },
        ) {
            Layout(
                modifier = Modifier.pointerInput(stackable, open) {
                    if (!stackable || open) return@pointerInput
                    // 按住往下拉展开:后面的卡片跟着手指滑出来;松手时拉过三分之一或往下甩就展开,否则弹回
                    val range = PULL_EXPAND_RANGE.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 列表不在最上面时,往下拉是滚动列表
                        if (state.list.canScrollBackward) return@awaitEachGesture
                        var total = 0f
                        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                            // 只接往下拉;往上拉交给列表滚动
                            if (over > 0f) {
                                change.consume()
                                total = over
                            }
                        } ?: return@awaitEachGesture
                        callbacks.claimGesture()
                        composeAll = true
                        dragProgress = (total / range).coerceIn(0f, 1f)
                        val tracker = VelocityTracker()
                        tracker.addPosition(drag.uptimeMillis, drag.position)
                        val completed = verticalDrag(drag.id) { change ->
                            total += change.positionChange().y
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragProgress = (total / range).coerceIn(0f, 1f)
                        }
                        val at = dragProgress ?: 0f
                        val expand = completed && (at > 1f / 3f || tracker.calculateVelocity().y > PULL_EXPAND_FLING)
                        scope.launch {
                            progress.snapTo(at)
                            dragProgress = null
                            if (expand) {
                                expandedAt = SystemClock.uptimeMillis()
                                state.expanded = state.expanded + group.id
                            } else {
                                progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
                                if (group.id !in state.expanded) composeAll = false
                            }
                        }
                    }
                },
                content = {
                    if (hasHeader) {
                        GroupHeader(
                            title = group.appLabel,
                            env = env,
                            onCollapse = { state.expanded = state.expanded - group.id },
                            onClear = if (clearable.isNotEmpty()) {
                                {
                                    // 先请系统删除(通知中心马上收起也不会漏);系统很快就会拿掉,卡片留在原位飞完(见 ShadeListMerge)
                                    callbacks.dismiss(clearable)
                                    state.willHide(clearable)
                                    clearingKeys = clearingKeys + clearable.associate { it.key to it.version }
                                }
                            } else {
                                null
                            },
                        )
                    }
                    for (layer in 1..drawnBlanks) {
                        key(layer) {
                            StackBlank(env = env, dim = dim * layer, peek = peekDp, onTap = {
                                if (state.revealed != null) state.revealed = null else state.expanded = state.expanded + group.id
                            })
                        }
                    }
                    shown.forEachIndexed { i, n ->
                        key(n.key) {
                            val byHeader = clearingKeys[n.key] == n.version
                            SwipeAway(
                                enabled = open && n.clearable,
                                clearing = (state.clearing || byHeader) && n.clearable && open,
                                fading = n.key in vanishing,
                                // 按标题行的叉时按这一组里的先后依次飞出;全部清除时按整个列表的先后
                                order = if (byHeader) i else order + i,
                                onClaim = callbacks::claimGesture,
                                onLeave = { state.willHide(listOf(n)) },
                                onSwiped = {
                                    callbacks.dismiss(listOf(n))
                                    state.willHide(listOf(n))
                                },
                                onGone = { cardGone(n) },
                                options = if (settings.swipeOptions) ({
                                    SwipeOptions(
                                        env = env,
                                        pinned = state.isPinned(n),
                                        onPin = { state.revealed = null; state.togglePin(listOf(n)) },
                                        onSettings = { state.revealed = null; callbacks.settings(n) },
                                    )
                                }) else null,
                                revealed = state.revealed == "c:" + n.key,
                                onReveal = { show ->
                                    val id = "c:" + n.key
                                    state.revealed = if (show) id else state.revealed.takeIf { it != id }
                                },
                                // 飞出或淡出途中这张卡片不再画了(例如收起了这一组):直接结束
                                onAbandon = { state.settle(listOf(n)) },
                                modifier = if (i == 0) {
                                    Modifier.graphicsLayer {
                                        val s = 0.965f + 0.035f * bump.value
                                        scaleX = s
                                        scaleY = s
                                    }
                                } else {
                                    Modifier
                                },
                            ) {
                                Box {
                                    NotificationCard(
                                        n = n,
                                        state = state,
                                        env = env,
                                        callbacks = callbacks,
                                        showActions = open,
                                        openSweepDelay = FLOW_OPEN_STAGGER_MS * (order + i),
                                        sweepAt = if (i == 0) maxOf(expandedAt, refreshedAt) else expandedAt,
                                        sweepDelay = FLOW_EXPAND_STAGGER_MS * i,
                                        onTap = {
                                            when {
                                                // 有卡片露出着左滑选项时,点按先收回
                                                state.revealed != null -> state.revealed = null
                                                stackable && !open -> state.expanded = state.expanded + group.id
                                                else -> callbacks.open(n)
                                            }
                                        },
                                    )
                                    // 数量角标:最上层卡片的角上标这一组的条数
                                    if (i == 0 && stackable && !open && settings.badge.enabled) {
                                        val badgeHidden = group.items.size - 1 - blanks
                                        val badgeText = if (settings.badge.mode == NotificationBadgeSpec.MODE_HIDDEN) {
                                            if (badgeHidden > 0) "+$badgeHidden" else null
                                        } else {
                                            group.items.size.toString()
                                        }
                                        badgeText?.let { StackBadge(env, it, settings.badge) }
                                    }
                                }
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                // 弹簧动画会略微冲过头,摆放只按 0 到 1 算;按住往下拉时跟着手指
                val t = (dragProgress ?: progress.value).coerceIn(0f, 1f)
                val w = constraints.maxWidth
                val full = Constraints(minWidth = w, maxWidth = w)
                var index = 0
                val headerP = if (hasHeader) measurables[index++].measure(full) else null
                val blankMeasurables = measurables.subList(index, index + drawnBlanks)
                index += drawnBlanks
                val cards = measurables.subList(index, measurables.size).map { it.measure(full) }
                val h0 = cards.firstOrNull()?.height ?: 0
                val peek = peekDp.roundToPx()
                val gap = CARD_GAP.roundToPx()
                val blankPlaceables = blankMeasurables.mapIndexed { i, m ->
                    val bw = (w * scale.pow(i + 1)).roundToInt().coerceIn(1, w)
                    m.measure(Constraints.fixed(bw, h0.coerceAtLeast(1)))
                }
                // 标题行收拢时它占的高度跟着变小,下面的卡片往上补
                val headerShown = header.value.coerceIn(0f, 1f)
                val headerFullH = headerP?.height ?: 0
                val headerH = (headerFullH * headerShown).roundToInt()
                // 清除后正在收拢的卡片:它占的位置(连下面的间距)按收拢程度变小,下面的卡片跟着补上
                val kept = FloatArray(cards.size) { i -> shown.getOrNull(i)?.let { leaving[it.key] } ?: 1f }
                val expandedY = IntArray(cards.size)
                var y = headerH
                cards.forEachIndexed { i, p ->
                    expandedY[i] = y
                    y += ((p.height + if (i < cards.lastIndex) gap else 0) * kept[i]).roundToInt()
                }
                val expandedH = if (cards.size < group.items.size) h0 + headerH else y
                // 后方露出的层数正在变化时,高度按过渡中的层数算
                val level = blankLevel.value.coerceIn(0f, drawnBlanks.toFloat())
                val stackedH = h0 + (peek * level).roundToInt()
                val height = lerp(stackedH, expandedH, t)
                val shadowPad = SHADOW_ROOM.toPx()
                // 各张卡片此刻的上沿和看得见的下沿(正在收拢的按收拢后的高度算)
                val cardY = IntArray(cards.size) { i -> lerp(peek * min(i, blanks), expandedY[i], t) }
                val cardBottom = IntArray(cards.size) { i -> cardY[i] + (cards[i].height * kept[i]).roundToInt() }
                val frontBottom = if (cards.isEmpty()) 0 else cardY[0] + h0
                layout(w, height) {
                    // 完全堆叠时不摆标题行,完全展开时不摆空白层:不摆的既不画也接不到触摸
                    if (t > 0f && headerShown > 0f) {
                        headerP?.placeWithLayer(0, 0, zIndex = 0f) {
                            alpha = t * headerShown
                            compositingStrategy = FADE_WITHOUT_BOX
                            // 收拢时往上补的卡片盖住的部分不画(卡片透明,不裁会透出标题行)
                            revealAbove(headerH.toFloat(), headerFullH, shadowPad)
                        }
                    }
                    if (t < 1f) {
                        blankPlaceables.forEachIndexed { i, p ->
                            val layer = i + 1
                            val top = peek * layer
                            // 前一层的下沿:第一层前面是最上层卡片(展开时它在往下移),再往后是上一层
                            val above = if (layer == 1) frontBottom else peek * (layer - 1) + h0
                            // 层数变少时最外面一层淡出,变多时新的一层淡入
                            val present = (level - (layer - 1)).coerceIn(0f, 1f)
                            if (present <= 0f) return@forEachIndexed
                            p.placeWithLayer((w - p.width) / 2, top, zIndex = 100f - layer) {
                                alpha = (1f - t) * present
                                compositingStrategy = FADE_WITHOUT_BOX
                                revealBelow((above - top).toFloat(), shadowPad)
                            }
                        }
                    }
                    cards.forEachIndexed { i, p ->
                        p.placeWithLayer(0, cardY[i], zIndex = 1000f - i) {
                            alpha = if (i == 0) 1f else t
                            compositingStrategy = FADE_WITHOUT_BOX
                            if (i > 0) {
                                // 从前一张下面滑出时由窄变宽:从堆叠时这一层的宽度到原宽,收起时反过来
                                scaleX = lerp(scale.pow(min(i, blanks)), 1f, t)
                                revealBelow((cardBottom[i - 1] - cardY[i]).toFloat(), shadowPad)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 堆叠后方露出的一层:空白玻璃,略微压暗,露边上方一道细阴影(前一张卡的下沿);点按露出的部分也展开这一组。
 * 层数变多时新的一层淡入。
 */
@Composable
private fun StackBlank(env: CardEnv, dim: Float, peek: Dp, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }
    Box(
        Modifier
            .graphicsLayer {
                alpha = enter.value
                compositingStrategy = FADE_WITHOUT_BOX
            }
            .liquidGlass(
                env = env.glass,
                shape = CardShape,
                surface = { if (dim > 0f) drawRect(Color.Black.copy(alpha = dim.coerceAtMost(0.6f))) },
                front = { peekShadow(size.height - peek.toPx()) },
            )
            .pointerInput(Unit) { detectTapGestures { tap() } },
    ) {
        GlassHighlight(env.glass, CardShape, Modifier.matchParentSize())
    }
}

/**
 * 数量角标:堆叠最上层卡片角上的一个小数字块,标注这一组有多少条;
 * 位置、大小和颜色都由设置决定,跟着卡片一起按下放大。
 */
@Composable
private fun StackBadge(env: CardEnv, text: String, badge: NotificationBadgeSpec) {
    val corner = when (badge.corner) {
        0 -> Alignment.TopStart
        2 -> Alignment.BottomStart
        3 -> Alignment.BottomEnd
        else -> Alignment.TopEnd
    }
    Box(Modifier.fillMaxSize()) {
        BasicText(
            text,
            modifier = Modifier
                .align(corner)
                .padding(badge.marginDp.dp)
                .clip(RoundedCornerShape(badge.radiusDp.dp))
                .background(Color(badge.bgColor))
                .padding(horizontal = badge.paddingDp.dp, vertical = (badge.paddingDp * 0.6f).dp),
            style = textStyle(env.fonts.medium, badge.textSp.toFloat(), 1.1f, Color(badge.textColor), null),
        )
    }
}

/** 展开的一组上方:应用名,右边是「收起」和清除这一组的玻璃按钮 */
@Composable
private fun GroupHeader(
    title: String,
    env: CardEnv,
    onCollapse: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val look = env.look
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            title,
            modifier = Modifier.weight(1f),
            style = textStyle(env.fonts.medium, 19f, 1.2f, look.title, look.textShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        GlassPill("Collapse", env, onCollapse)
        if (onClear != null) {
            Spacer(Modifier.width(8.dp))
            GlassClose(env, onClear)
        }
    }
}

@Composable
private fun GlassPill(label: String, env: CardEnv, onTap: () -> Unit) {
    val press = remember { Animatable(0f) }
    val look = env.look
    Box(
        Modifier
            .height(34.dp)
            .liquidGlass(env.glass, PillShape, press = { press.value })
            .pressable(press, onTap),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, modifier = Modifier.padding(horizontal = 15.dp), style = textStyle(env.fonts.medium, 14f, 1.2f, look.title, look.textShadow))
        GlassHighlight(env.glass, PillShape, Modifier.matchParentSize())
    }
}

@Composable
private fun GlassClose(env: CardEnv, onTap: () -> Unit) {
    val press = remember { Animatable(0f) }
    val look = env.look
    Box(
        Modifier
            .size(34.dp)
            .liquidGlass(env.glass, PillShape, press = { press.value })
            .pressable(press, onTap)
            .drawBehind {
                val k = 5.dp.toPx()
                val c = Offset(size.width / 2f, size.height / 2f)
                val sw = 1.8.dp.toPx()
                drawLine(look.title, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), sw, StrokeCap.Round)
                drawLine(look.title, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), sw, StrokeCap.Round)
            },
    ) {
        GlassHighlight(env.glass, PillShape, Modifier.matchParentSize())
    }
}

/**
 * 一条通知的玻璃卡片。普通通知用我们的排版:左边应用图标,中间标题、时间、正文和进度,右边通知自带的小图;
 * 应用自己设计样式的,上面一行应用名和时间,下面放应用自己的排版。底部是按钮,点「回复」类按钮展开输入框。
 * 长按弹出菜单(通知设置)。拉开通知中心、展开堆叠、堆叠里来了新通知时沿上边走一次流光。
 */
@Composable
private fun NotificationCard(
    n: LiquidNotification,
    state: LiquidUiState,
    env: CardEnv,
    callbacks: LiquidCallbacks,
    showActions: Boolean,
    openSweepDelay: Long,
    sweepAt: Long,
    sweepDelay: Long,
    onTap: () -> Unit,
) {
    val settings = state.settings
    val press = remember { Animatable(0f) }
    val flow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val glow = remember(scope) { InteractiveHighlight(scope, surfaceAlpha = 0.06f, spotAlpha = 0.12f, spotRadius = 1.2f) }
    val bounds = remember { arrayOf(Rect.Zero) }
    val openedAt = state.openedAt
    LaunchedEffect(openedAt) {
        if (settings.flowLight && openedAt > 0L && SystemClock.uptimeMillis() - openedAt < FLOW_WINDOW_MS) {
            delay(openSweepDelay)
            flow.snapTo(0f)
            flow.animateTo(1f, tween(FLOW_DURATION_MS, easing = LinearEasing))
        }
    }
    LaunchedEffect(sweepAt) {
        if (settings.flowLight && sweepAt > 0L && SystemClock.uptimeMillis() - sweepAt < FLOW_WINDOW_MS) {
            delay(150L + sweepDelay)
            flow.snapTo(0f)
            flow.animateTo(1f, tween(FLOW_DURATION_MS, easing = LinearEasing))
        }
    }
    val accent = n.accent?.let { Color(it) }
    // 深色、浅色模式都可以开(用户 2026-09-26「氛围光：我觉得不止深色模式，它也可以有」);从应用图标处散开,按「卡片大小」缩小后的位置
    val ambient = settings.ambientGlow && accent != null
    val contentScale = settings.cardScale / 100f
    val replying = state.reply?.takeIf { it.key == n.key && showActions }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .liquidGlass(
                env = env.glass,
                shape = CardShape,
                press = { press.value },
                surface = if (ambient) ({
                    val iconCenter = (14.dp.toPx() + ICON_SIZE.toPx() / 2f) * contentScale
                    ambientGlow(accent!!, Offset(iconCenter, iconCenter))
                }) else null,
                front = if (settings.flowLight) ({ flowLight(flow.value, CARD_RADIUS.toPx()) }) else null,
            )
            .then(if (env.glass.spec.pressGlow) glow.modifier.then(glow.gestureModifier) else Modifier)
            .pressable(
                press = press,
                onTap = onTap,
                onLongPress = {
                    state.revealed = null
                    state.menu = LiquidMenu(n, bounds[0])
                },
            ),
    ) {
        CardContent(n, state, env, callbacks, showActions, replying)
        GlassHighlight(env.glass, CardShape, Modifier.matchParentSize())
    }
}

/**
 * 卡片里的内容:排版、按钮、回复框(外面的玻璃和亮边见 [NotificationCard])。「卡片大小」(公开反馈 #33)不到 100% 时,
 * 字、图标、按钮与留白按同一比例缩小(换一份按比例缩小的屏幕密度),卡片随之变矮;应用自己设计样式的那一块按原来的密度放,不缩小。
 */
@Composable
private fun CardContent(
    n: LiquidNotification,
    state: LiquidUiState,
    env: CardEnv,
    callbacks: LiquidCallbacks,
    showActions: Boolean,
    replying: LiquidReplyTarget?,
) {
    val outer = LocalDensity.current
    val scale = state.settings.cardScale / 100f
    if (scale >= 1f) {
        CardColumn(n, state, env, callbacks, showActions, replying, outer)
        return
    }
    val scaled = remember(outer, scale) { Density(outer.density * scale, outer.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled) {
        CardColumn(n, state, env, callbacks, showActions, replying, outer)
    }
}

@Composable
private fun CardColumn(
    n: LiquidNotification,
    state: LiquidUiState,
    env: CardEnv,
    callbacks: LiquidCallbacks,
    showActions: Boolean,
    replying: LiquidReplyTarget?,
    appDensity: Density,
) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)) {
        if (n.custom != null) {
            AppHeader(n, env, state.now, state.isPinned(n))
            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalDensity provides appDensity) { AppLayout(n) }
        } else {
            StandardContent(n, env, state.now, state.isPinned(n))
        }
        val buttons = if (showActions) n.actions.take(3) else emptyList()
        if (buttons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.padding(start = if (n.custom != null) 0.dp else ICON_SIZE + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (a in buttons) {
                    key(a.index) {
                        ActionChip(a, env) {
                            if (a.replyHint != null) {
                                val same = state.reply?.let { it.key == n.key && it.action.index == a.index } == true
                                state.reply = if (same) null else LiquidReplyTarget(n.key, a)
                            } else {
                                callbacks.action(n, a)
                            }
                        }
                    }
                }
            }
        }
        // 回复框展开、收起都有过渡;收起过程中仍用最后一次的按钮
        var lastReply by remember { mutableStateOf<LiquidReplyTarget?>(null) }
        if (replying != null) lastReply = replying
        AnimatedVisibility(
            visible = replying != null,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) + fadeIn(tween(180)),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 600f)) + fadeOut(tween(120)),
        ) {
            val target = replying ?: lastReply
            if (target != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    ReplyBox(
                        hint = target.action.replyHint ?: "Reply",
                        env = env,
                        callbacks = callbacks,
                        onSend = { text -> callbacks.reply(n, target.action, text) },
                        onClose = { state.reply = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardContent(n: LiquidNotification, env: CardEnv, now: Long, pinned: Boolean) {
    val look = env.look
    val fonts = env.fonts
    Row {
        AppIcon(n.appIcon, n.appLabel, env, Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    n.title,
                    modifier = Modifier.weight(1f),
                    style = textStyle(fonts.medium, 15.5f, 1.34f, look.title, look.textShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pinned) {
                    Spacer(Modifier.width(6.dp))
                    PinMark(look.secondary)
                }
                if (n.showTime) {
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        relativeTime(n.postTime, now),
                        style = textStyle(fonts.regular, 12.5f, 1.34f * 15.5f / 12.5f, look.secondary, look.textShadow),
                    )
                }
            }
            if (n.text.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                BasicText(
                    n.text,
                    style = textStyle(fonts.regular, 14.5f, 1.42f, look.body, look.textShadow),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val progress = n.progress
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                ProgressLine(progress, n.progressIndeterminate, look)
            }
        }
        val thumb = n.thumbnail
        if (thumb != null) {
            Spacer(Modifier.width(10.dp))
            val image = remember(thumb) { thumb.asImageBitmap() }
            Image(image, contentDescription = null, modifier = Modifier.size(ICON_SIZE).clip(RoundedCornerShape(8.dp)))
        }
    }
}

/** 应用自己设计样式时上面那一行:小图标、应用名、时间(与系统给这类通知加的那一行一样);置顶时时间前面一枚小图钉 */
@Composable
private fun AppHeader(n: LiquidNotification, env: CardEnv, now: Long, pinned: Boolean) {
    val look = env.look
    val fonts = env.fonts
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = n.appIcon
        if (icon != null) {
            val image = remember(icon) { icon.asImageBitmap() }
            Image(image, contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            n.appLabel,
            modifier = Modifier.weight(1f),
            style = textStyle(fonts.medium, 13f, 1.3f, look.secondary, look.textShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (pinned) {
            PinMark(look.secondary)
            Spacer(Modifier.width(6.dp))
        }
        if (n.showTime) {
            BasicText(relativeTime(n.postTime, now), style = textStyle(fonts.regular, 12.5f, 1.3f, look.secondary, look.textShadow))
        }
    }
}

/** 应用自己的排版(系统本来就是这样显示这类通知的):按应用给的样子放进卡片,通知更新时重新套用 */
@Composable
private fun AppLayout(n: LiquidNotification) {
    AndroidView(
        factory = { context -> FrameLayout(context) },
        modifier = Modifier.fillMaxWidth().heightIn(max = 256.dp),
        update = { frame ->
            val applied = frame.tag as? AppliedLayout
            val rv = n.custom ?: return@AndroidView
            if (applied != null && applied.key == n.key && applied.version == n.version) return@AndroidView
            runCatching {
                val child = frame.getChildAt(0)
                if (child != null && applied?.layoutId == rv.layoutId) {
                    rv.reapply(frame.context, child)
                } else {
                    frame.removeAllViews()
                    frame.addView(rv.apply(frame.context, frame))
                }
                frame.tag = AppliedLayout(n.key, n.version, rv.layoutId)
            }.onFailure { LiquidLog.w("apply app layout failed", it) }
        },
    )
}

private class AppliedLayout(val key: String, val version: Long, val layoutId: Int)

/** 卡片上的按钮:玻璃上的一块淡色圆角,按下时略微缩小、颜色加深;应用只给了图标的画图标 */
@Composable
private fun ActionChip(action: LiquidAction, env: CardEnv, onTap: () -> Unit) {
    val look = env.look
    val press = remember { Animatable(0f) }
    val icon = action.icon
    val pressed = Modifier
        .graphicsLayer {
            val s = 1f - 0.05f * press.value
            scaleX = s
            scaleY = s
        }
        .clip(PillShape)
        .background(look.chip)
        .drawBehind { drawRect(look.chip.copy(alpha = look.chip.alpha * press.value)) }
        .pressable(press, onTap)
    if (action.title.isEmpty() && icon != null) {
        val image = remember(icon) { icon.asImageBitmap() }
        Box(Modifier.size(34.dp).then(pressed), contentAlignment = Alignment.Center) {
            Image(image, contentDescription = null, modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(look.title))
        }
        return
    }
    Box(Modifier.height(32.dp).then(pressed).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        BasicText(
            action.title,
            style = textStyle(env.fonts.medium, 13.5f, 1.2f, look.title, look.textShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 直接回复的输入框。出现时告诉系统「正在回复」,系统才让通知中心窗口弹出键盘;关闭时告诉系统结束。
 */
@Composable
private fun ReplyBox(
    hint: String,
    env: CardEnv,
    callbacks: LiquidCallbacks,
    onSend: (String) -> Boolean,
    onClose: () -> Unit,
) {
    val look = env.look
    val fonts = env.fonts
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        callbacks.replying(true)
        onDispose { callbacks.replying(false) }
    }
    LaunchedEffect(Unit) {
        // 系统改好窗口(可以弹键盘)要一两帧
        delay(160)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val send = {
        val t = text.trim()
        if (t.isNotEmpty() && onSend(t)) {
            text = ""
            keyboard?.hide()
            onClose()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(look.chip)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                BasicText(hint, style = textStyle(fonts.regular, 14.5f, 1.3f, look.secondary, null))
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = textStyle(fonts.regular, 14.5f, 1.3f, look.title, null),
                cursorBrush = SolidColor(look.title),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
            )
        }
        Spacer(Modifier.width(8.dp))
        val sendPress = remember { Animatable(0f) }
        val ready by animateFloatAsState(if (text.isBlank()) 0f else 1f, tween(160), label = "send")
        Box(
            Modifier
                .height(38.dp)
                .graphicsLayer {
                    val s = 1f - 0.05f * sendPress.value
                    scaleX = s
                    scaleY = s
                }
                .clip(PillShape)
                .background(look.chip)
                .drawBehind { drawRect(look.title.copy(alpha = 0.12f * ready)) }
                .pressable(sendPress, { send() })
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Send", style = textStyle(fonts.medium, 14f, 1.2f, lerpColor(look.secondary, look.title, ready), null))
        }
        Spacer(Modifier.width(6.dp))
        val closePress = remember { Animatable(0f) }
        Box(
            Modifier
                .size(38.dp)
                .graphicsLayer {
                    val s = 1f - 0.05f * closePress.value
                    scaleX = s
                    scaleY = s
                }
                .clip(PillShape)
                .background(look.chip)
                .pressable(closePress, {
                    keyboard?.hide()
                    onClose()
                })
                .drawBehind {
                    val k = 4.5.dp.toPx()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val sw = 1.6.dp.toPx()
                    drawLine(look.secondary, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), sw, StrokeCap.Round)
                    drawLine(look.secondary, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), sw, StrokeCap.Round)
                },
        )
    }
}

/**
 * 长按弹出的菜单:一块玻璃,贴在卡片右下方(下面放不下时放在上方),从靠卡片的一角弹开;点别处关闭,关闭时淡出。
 */
@Composable
private fun MenuLayer(state: LiquidUiState, env: CardEnv, callbacks: LiquidCallbacks) {
    val menu = state.menu
    var last by remember { mutableStateOf<LiquidMenu?>(null) }
    if (menu != null) last = menu
    val shown = last ?: return
    val look = env.look
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = menu != null,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { state.menu = null } },
        ) {
            Layout(
                content = {
                    val press = remember { Animatable(0f) }
                    val grow = remember { Animatable(0.86f) }
                    LaunchedEffect(shown) {
                        grow.snapTo(0.86f)
                        grow.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f))
                    }
                    val grown = Modifier.graphicsLayer {
                        scaleX = grow.value
                        scaleY = grow.value
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                    if (!state.settings.swipeOptions) {
                        Box(
                            grown
                                .liquidGlass(env.glass, MenuShape, press = { press.value })
                                .pressable(press, {
                                    state.menu = null
                                    callbacks.settings(shown.notification)
                                }),
                        ) {
                            BasicText(
                                "Notification settings",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                style = textStyle(env.fonts.medium, 15f, 1.3f, look.title, look.textShadow),
                            )
                            GlassHighlight(env.glass, MenuShape, Modifier.matchParentSize())
                        }
                    } else {
                        // 「置顶与左滑选项」:上面「置顶」(已置顶时「取消置顶」),下面「通知设置」,中间一道细线
                        val n = shown.notification
                        Box(grown.liquidGlass(env.glass, MenuShape, press = { 0f })) {
                            Column(Modifier.width(IntrinsicSize.Max)) {
                                MenuRow(if (state.isPinned(n)) "Unpin" else "Pin", env) {
                                    state.menu = null
                                    state.togglePin(listOf(n))
                                }
                                Box(Modifier.fillMaxWidth().height(0.5.dp).background(look.chip))
                                MenuRow("Notification settings", env) {
                                    state.menu = null
                                    callbacks.settings(n)
                                }
                            }
                            GlassHighlight(env.glass, MenuShape, Modifier.matchParentSize())
                        }
                    }
                },
            ) { measurables, constraints ->
                val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
                val gap = with(density) { 8.dp.roundToPx() }
                val side = with(density) { SIDE.roundToPx() }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val anchor = shown.anchor
                    val x = (anchor.right.roundToInt() - side - p.width).coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                    val below = anchor.bottom.roundToInt() + gap
                    val y = if (below + p.height <= constraints.maxHeight) below else (anchor.top.roundToInt() - gap - p.height).coerceAtLeast(0)
                    p.place(x, y)
                }
            }
        }
    }
}

/** 长按菜单里的一行:按下时底色加深 */
@Composable
private fun MenuRow(label: String, env: CardEnv, onTap: () -> Unit) {
    val press = remember { Animatable(0f) }
    val look = env.look
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(look.chip.copy(alpha = (look.chip.alpha * 1.6f * press.value).coerceAtMost(1f))) }
            .pressable(press, onTap),
    ) {
        BasicText(
            label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            style = textStyle(env.fonts.medium, 15f, 1.3f, look.title, look.textShadow),
        )
    }
}

/** 进度条:数值变化时平滑走过去;不确定进度时一段亮条来回走 */
@Composable
private fun ProgressLine(progress: Float, indeterminate: Boolean, look: LiquidLook) {
    val fill = look.title.copy(alpha = look.title.alpha * 0.85f)
    if (indeterminate) {
        val t = rememberInfiniteTransition(label = "progress")
        val x by t.animateFloat(
            initialValue = -0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
            label = "progress",
        )
        Box(
            Modifier.fillMaxWidth().height(4.dp).drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(look.track, cornerRadius = r)
                val left = (x * size.width).coerceAtLeast(0f)
                val right = ((x + 0.35f) * size.width).coerceAtMost(size.width)
                if (right > left) drawRoundRect(fill, topLeft = Offset(left, 0f), size = Size(right - left, size.height), cornerRadius = r)
            },
        )
    } else {
        val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = 180f), label = "progress")
        Box(
            Modifier.fillMaxWidth().height(4.dp).drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(look.track, cornerRadius = r)
                val w = size.width * shown
                if (w > 0f) drawRoundRect(fill, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
            },
        )
    }
}

@Composable
private fun AppIcon(icon: Bitmap?, label: String, env: CardEnv, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(9.dp)
    if (icon != null) {
        val image = remember(icon) { icon.asImageBitmap() }
        Image(image, contentDescription = label, modifier = modifier.size(ICON_SIZE).clip(shape))
    } else {
        // 系统尚未准备好图标时保留位置,准备好后补上真实图像,不以文字或通用图案代替。
        Box(modifier.size(ICON_SIZE))
    }
}

/** 按住时照底栏的手感放大(弹簧),松手或开始滚动时恢复;点按、长按分别处理 */
@Composable
private fun Modifier.pressable(press: Animatable<Float, AnimationVector1D>, onTap: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier {
    val scope = rememberCoroutineScope()
    // 通知内容更新时回调跟着换,不打断正在进行的触摸
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val hasLong = onLongPress != null
    return this.pointerInput(hasLong) {
        detectTapGestures(
            onPress = {
                val rise = scope.launch { press.animateTo(1f, spring(0.5f, 300f)) }
                tryAwaitRelease()
                rise.cancel()
                scope.launch { press.animateTo(0f, spring(0.5f, 300f)) }
            },
            onLongPress = if (hasLong) ({ _: Offset -> long?.invoke() }) else null,
            onTap = { tap() },
        )
    }
}

/**
 * 左右滑动,滑过三分之一或甩得够快就滑走;松手没滑走时弹回原位。滑动中卡片随距离淡出、略微缩小并向滑动方向倾斜。
 * [clearing] 为真时(全部清除、或按了标题行的叉)按 [order] 依次自己向右飞走,开始时调 [onLeave](记下正在离开)。
 * [fading] 为真时(应用自己撤回了)原地淡出、略微缩小;撤回的又回来了([fading] 变回假)就恢复。
 * 决定清除时先调 [onSwiped](请系统删除;通知中心马上收起也不会漏),飞出去或淡出以后调 [onGone](收拢位置、在列表里藏起)。
 * 飞出去的动画一旦开始就走完,不会被系统结束「全部清除」打断而停在半路空出一块;
 * 飞出或淡出途中这个元素不再画了(动画走不完),调 [onAbandon] 直接结束。
 * [reset] 变了就弹回原位(整组滑走后组里还剩不能清除的通知)。
 * 给了 [options] 时(「置顶与左滑选项」,公开反馈 #80)只有向右滑清除:向左滑卡片停住,右边露出这些按钮(见 [SwipeRule]),
 * 露出或收回时调 [onReveal];[revealed] 变假(别的卡片露出了、点按了卡片、滚动了列表)就收回。
 */
@Composable
private fun SwipeAway(
    enabled: Boolean,
    clearing: Boolean,
    order: Int,
    onClaim: () -> Unit,
    onSwiped: () -> Unit,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
    fading: Boolean = false,
    onLeave: () -> Unit = {},
    onAbandon: () -> Unit = {},
    reset: Int = 0,
    options: (@Composable () -> Unit)? = null,
    revealed: Boolean = false,
    onReveal: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    // 正在离开;离开的动画已经走完
    var gone by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val claim by rememberUpdatedState(onClaim)
    val leave by rememberUpdatedState(onLeave)
    val swiped by rememberUpdatedState(onSwiped)
    val left by rememberUpdatedState(onGone)
    val abandon by rememberUpdatedState(onAbandon)
    val reveal by rememberUpdatedState(onReveal)
    val revealedNow by rememberUpdatedState(revealed)
    val hasOptions = options != null
    // 左滑露出的按钮一共多宽(含与卡片之间的空隙);开始横划后才放上按钮,不横划的卡片不多画
    var optionsWidth by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    val finish = {
        done = true
        left()
    }
    DisposableEffect(Unit) {
        onDispose { if (gone && !done) abandon() }
    }
    LaunchedEffect(clearing) {
        if (clearing && !gone) {
            gone = true
            leave()
            // 放在这个元素自己的协程里:系统结束「全部清除」时这里的效果会被取消,飞出去的动画不能跟着停
            scope.launch {
                delay(40L * order.coerceAtMost(8))
                offset.animateTo(width * 1.2f, tween(260, easing = FastOutLinearInEasing))
                finish()
            }
        }
    }
    LaunchedEffect(fading) {
        if (fading) {
            if (gone) return@LaunchedEffect
            gone = true
            fade.animateTo(0f, tween(VANISH_MS, easing = FastOutLinearInEasing))
            finish()
        } else if (fade.value < 1f) {
            // 撤回的又回来了
            gone = false
            done = false
            fade.animateTo(1f, tween(VANISH_MS))
        }
    }
    LaunchedEffect(reset) {
        if (reset > 0 && gone) {
            gone = false
            done = false
            offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 380f))
        }
    }
    // 别的卡片露出了选项、点按了卡片、滚动了列表(或这一块不再有左滑选项):收回
    LaunchedEffect(revealed, hasOptions) {
        if ((!revealed || !hasOptions) && !gone && offset.value < 0f) {
            offset.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f))
            armed = false
        }
    }
    Box(modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }) {
        if (options != null && (armed || revealed)) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // 只画卡片挪开后空出来的那一截,露出得越多越清楚
                        val shown = -offset.value
                        alpha = if (shown <= 0f) 0f else (shown / optionsWidth.coerceAtLeast(1)).coerceIn(0f, 1f)
                        clip = true
                        shape = RightOfShape(width + offset.value, SHADOW_ROOM.toPx())
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.onSizeChanged { optionsWidth = it.width }.padding(start = OPTIONS_GAP)) { options() }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer {
                    // 有左滑选项时只有向右滑是清除:向左滑不淡出、不倾斜
                    val toward = if (hasOptions) max(offset.value, 0f) else offset.value
                    val f = (abs(toward) / (width * 1.1f)).coerceIn(0f, 1f)
                    val v = fade.value.coerceIn(0f, 1f)
                    alpha = (1f - f) * v
                    compositingStrategy = FADE_WITHOUT_BOX
                    val s = (1f - 0.04f * f) * (0.94f + 0.06f * v)
                    scaleX = s
                    scaleY = s
                    rotationZ = 3f * f * sign(toward)
                }
                .pointerInput(enabled && !gone, hasOptions) {
                    if (!enabled || gone) return@pointerInput
                    val tracker = VelocityTracker()
                    var raw = 0f
                    val rest: suspend () -> Unit = {
                        offset.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f))
                        if (!revealedNow) armed = false
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            raw = offset.value
                            if (hasOptions) armed = true
                            claim()
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            // 按钮还没量好时也按左滑选项的规矩判断,不会向左清除
                            val optionsPx = if (hasOptions) max(optionsWidth, 1).toFloat() else 0f
                            when (SwipeRule.decide(offset.value, v, width.toFloat(), optionsPx)) {
                                SwipeRule.Outcome.DISMISS -> {
                                    if (revealedNow) reveal(false)
                                    gone = true
                                    swiped()
                                    scope.launch {
                                        offset.animateTo(sign(offset.value) * width * 1.15f, tween(200, easing = FastOutLinearInEasing))
                                        finish()
                                    }
                                }
                                SwipeRule.Outcome.REVEAL -> {
                                    reveal(true)
                                    scope.launch { offset.animateTo(-optionsPx, spring(dampingRatio = 0.86f, stiffness = 420f)) }
                                }
                                SwipeRule.Outcome.REST -> {
                                    if (revealedNow) reveal(false)
                                    scope.launch { rest() }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                if (hasOptions && revealedNow) offset.animateTo(-max(optionsWidth, 1).toFloat(), spring(dampingRatio = 0.62f, stiffness = 420f))
                                else rest()
                            }
                        },
                    ) { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        if (hasOptions) {
                            raw += dx
                            val target = SwipeRule.resist(raw, optionsWidth.toFloat())
                            scope.launch { offset.snapTo(target) }
                        } else {
                            scope.launch { offset.snapTo(offset.value + dx) }
                        }
                    }
                },
        ) { content() }
    }
}

/** 左滑露出的按钮与卡片右沿之间的空隙 */
private val OPTIONS_GAP = 10.dp

/**
 * 左滑露出的按钮(「置顶与左滑选项」,公开反馈 #80):「置顶」(已置顶时为「取消置顶」)、「设置」,
 * 和底部「全部清除」一样的玻璃小胶囊。
 */
@Composable
private fun SwipeOptions(env: CardEnv, pinned: Boolean, onPin: () -> Unit, onSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassPill(if (pinned) "Unpin" else "Pin", env, onPin)
        GlassPill("Settings", env, onSettings)
    }
}

/** 置顶标记:时间旁边一枚小图钉,颜色同时间文字(公开反馈 #80) */
@Composable
private fun PinMark(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .drawBehind {
                val u = size.minDimension / 12f
                val c = Offset(size.width / 2f, size.height / 2f)
                // 斜着的图钉:钉帽、钉座、钉尖
                rotate(45f, c) {
                    drawRoundRect(color, topLeft = Offset(4.5f * u, 1f * u), size = Size(3f * u, 4f * u), cornerRadius = CornerRadius(0.8f * u))
                    drawRoundRect(color, topLeft = Offset(3f * u, 4.6f * u), size = Size(6f * u, 1.4f * u), cornerRadius = CornerRadius(0.7f * u))
                    drawLine(color, Offset(6f * u, 6f * u), Offset(6f * u, 11f * u), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
                }
            },
    )
}

/**
 * 滚到头的弹性:列表滚到顶再往下拉、滚到底再往上推时,卡片之间的间距随拉动稍微拉开
 * (越靠外的拉开越多,拉得越远越费劲),松手后带一点回弹收回;快速滑到头时也轻轻弹一下。
 * 不用安卓自带的整块拉伸效果。另外列表开始滚动时这次触摸归列表,系统面板不再抢。
 */
@Stable
private class ElasticEdges(
    private val scope: CoroutineScope,
    private val callbacks: LiquidCallbacks,
    private val maxPx: Float,
) : NestedScrollConnection {
    /** 拉开的距离(像素):正数是在顶上往下拉,负数是在底下往上推 */
    private var pull by mutableFloatStateOf(0f)
    private var settle: Job? = null

    /** 第 [index] 项(一共 [count] 项)此刻要往下挪多少:离拉动那一端越远挪得越多 */
    fun offset(index: Int, count: Int): Float {
        val p = pull
        if (p == 0f) return 0f
        val steps = if (p > 0f) index else count - 1 - index
        return p * (1f + ELASTIC_SPREAD * steps.coerceIn(0, ELASTIC_MAX_STEPS))
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        callbacks.claimGesture()
        val p = pull
        // 拉开着的时候往回推:先把间距收回去,收完了再滚动列表
        if (p != 0f && available.y != 0f && sign(available.y) != sign(p)) {
            settle?.cancel()
            val next = if (p > 0f) maxOf(0f, p + available.y) else minOf(0f, p + available.y)
            pull = next
            return Offset(0f, next - p)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        settle?.cancel()
        val p = pull
        val room = (1f - abs(p) / maxPx).coerceIn(0f, 1f)
        pull = p + available.y * ELASTIC_RESISTANCE * room * room
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pull == 0f) return Velocity.Zero
        // 拉开着松手:带一点回弹收回,不再往下惯性滚动
        springBack(0f)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // 快速滑到头还剩速度:轻轻弹一下
        if (pull == 0f && abs(available.y) > ELASTIC_BUMP_MIN) {
            springBack((available.y * ELASTIC_BUMP).coerceIn(-ELASTIC_BUMP_MAX, ELASTIC_BUMP_MAX))
        }
        return Velocity.Zero
    }

    private fun springBack(velocity: Float) {
        settle?.cancel()
        val from = pull
        settle = scope.launch {
            animate(from, 0f, initialVelocity = velocity, animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)) { v, _ -> pull = v }
        }
    }
}

/**
 * 按 [fraction](1 为原高,0 为收拢完)报给上一层的高度,下面的卡片跟着补上;
 * 内容照原样从上往下画(清除时已经飞出去或淡掉了)。
 */
private fun Modifier.collapseHeight(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val h = (p.height * fraction().coerceIn(0f, 1f)).roundToInt()
    layout(p.width, h) { p.place(0, 0) }
}

/**
 * 上下两端渐隐:卡片往上滚进顶部区域、往下滚到屏幕底部时慢慢消失,
 * 不在列表边缘把卡片从中间硬切开(用户 2026-09-26「底部就会有很明显的割裂感」)
 */
private fun Modifier.fadeEdges(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val t = (top.toPx() / h).coerceIn(0f, 0.5f)
        val b = (bottom.toPx() / h).coerceIn(0f, 0.5f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to (if (t > 0f) Color.Transparent else Color.Black),
                t to Color.Black,
                (1f - b) to Color.Black,
                1f to (if (b > 0f) Color.Transparent else Color.Black),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** 几分钟前、几小时前;超过一天写日期 */
internal fun relativeTime(postTime: Long, now: Long): String {
    val minutes = ((now - postTime) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(postTime))
    }
}
