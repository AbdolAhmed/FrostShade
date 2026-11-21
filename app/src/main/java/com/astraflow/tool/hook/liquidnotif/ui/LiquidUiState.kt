package com.astraflow.tool.hook.liquidnotif.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.astraflow.tool.config.LiquidNotificationDefaults
import com.astraflow.tool.config.NotificationBadgeSpec
import com.astraflow.tool.hook.liquidnotif.model.LiquidAction
import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification

/** 卡片用的两种字重:手机自带字体的常规与中粗。 */
@Immutable
data class LiquidFonts(val regular: FontFamily, val medium: FontFamily) {
    companion object {
        /** 手机自带字体:常规用默认字体,中粗用同一字体的 500 字重。 */
        fun system(): LiquidFonts = LiquidFonts(
            regular = FontFamily.Default,
            medium = FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 500, false))),
        )
    }
}

/**
 * 用户在设置页调的各项(打开通知中心时读一次)。默认值是液态玻璃底栏和苹果堆叠的数字,
 * 见 [LiquidNotificationDefaults]。
 */
@Immutable
data class LiquidSettings(
    val backgroundBlurDp: Int = LiquidNotificationDefaults.BACKGROUND_BLUR.default,
    val dimLight: Int = LiquidNotificationDefaults.BACKGROUND_DIM_LIGHT.default,
    val dimDark: Int = LiquidNotificationDefaults.BACKGROUND_DIM_DARK.default,
    val brightExtra: Int = LiquidNotificationDefaults.BACKGROUND_BRIGHT_EXTRA.default,
    val topDim: Int = LiquidNotificationDefaults.BACKGROUND_TOP_DIM.default,
    val surfaceLight: Int = LiquidNotificationDefaults.SURFACE_LIGHT.default,
    val surfaceDark: Int = LiquidNotificationDefaults.SURFACE_DARK.default,
    val highlight: Int = LiquidNotificationDefaults.HIGHLIGHT.default,
    val shadow: Int = LiquidNotificationDefaults.SHADOW.default,
    val highlightTilt: Boolean = LiquidNotificationDefaults.HIGHLIGHT_TILT.default,
    val pressGlow: Boolean = LiquidNotificationDefaults.PRESS_GLOW.default,
    val flowLight: Boolean = LiquidNotificationDefaults.FLOW_LIGHT.default,
    val ambientGlow: Boolean = LiquidNotificationDefaults.AMBIENT_GLOW.default,
    val stack: Boolean = LiquidNotificationDefaults.STACK.default,
    val stackLayers: Int = LiquidNotificationDefaults.STACK_LAYERS.default,
    val stackPeekDp: Int = LiquidNotificationDefaults.STACK_PEEK.default,
    val stackScale: Int = LiquidNotificationDefaults.STACK_SCALE.default,
    val stackDim: Int = LiquidNotificationDefaults.STACK_DIM.default,
    val cardWidth: Int = LiquidNotificationDefaults.CARD_WIDTH.default,
    val cardScale: Int = LiquidNotificationDefaults.CARD_SCALE.default,
    /** 置顶与左滑选项(公开反馈 #80) */
    val swipeOptions: Boolean = LiquidNotificationDefaults.SWIPE_OPTIONS.default,
    /** 叠放范围:0 同一组,1 同一应用,2 全部通知 */
    val grouping: Int = LiquidNotificationDefaults.GROUPING.default,
    val sortEnabled: Boolean = LiquidNotificationDefaults.SORT_ENABLED.default,
    /** 排序依据:0 卡片高度,1 发出时间,2 应用名,3 重要性 */
    val sortPrimary: Int = LiquidNotificationDefaults.SORT_PRIMARY.default,
    val sortDesc: Boolean = LiquidNotificationDefaults.SORT_DESC.default,
    val sortStackedFirst: Boolean = LiquidNotificationDefaults.SORT_STACKED_FIRST.default,
    /** 优先应用名单(一行一个包名,按顺序排在最前) */
    val sortPriority: String = LiquidNotificationDefaults.SORT_PRIORITY.default,
    /** 数量角标的全部样式(打包存的,读回时解开) */
    val badge: NotificationBadgeSpec = NotificationBadgeSpec.DEFAULT,
) {
    companion object {
        /** 按设置读取(系统界面读模块设置,星流页面里的预览读星流自己的设置),超出范围的数按各项上下限截住 */
        fun read(
            getInt: (String, Int) -> Int,
            getBoolean: (String, Boolean) -> Boolean,
            getString: (String, String) -> String = { _, d -> d },
        ): LiquidSettings {
            val d = LiquidNotificationDefaults
            fun int(item: LiquidNotificationDefaults.IntItem) = item.clamp(getInt(item.key, item.default))
            fun bool(item: LiquidNotificationDefaults.BoolItem) = getBoolean(item.key, item.default)
            fun text(item: LiquidNotificationDefaults.TextItem) = getString(item.key, item.default)
            return LiquidSettings(
                backgroundBlurDp = int(d.BACKGROUND_BLUR),
                dimLight = int(d.BACKGROUND_DIM_LIGHT),
                dimDark = int(d.BACKGROUND_DIM_DARK),
                brightExtra = int(d.BACKGROUND_BRIGHT_EXTRA),
                topDim = int(d.BACKGROUND_TOP_DIM),
                surfaceLight = int(d.SURFACE_LIGHT),
                surfaceDark = int(d.SURFACE_DARK),
                highlight = int(d.HIGHLIGHT),
                shadow = int(d.SHADOW),
                highlightTilt = bool(d.HIGHLIGHT_TILT),
                pressGlow = bool(d.PRESS_GLOW),
                flowLight = bool(d.FLOW_LIGHT),
                ambientGlow = bool(d.AMBIENT_GLOW),
                stack = bool(d.STACK),
                stackLayers = int(d.STACK_LAYERS),
                stackPeekDp = int(d.STACK_PEEK),
                stackScale = int(d.STACK_SCALE),
                stackDim = int(d.STACK_DIM),
                cardWidth = int(d.CARD_WIDTH),
                cardScale = int(d.CARD_SCALE),
                swipeOptions = bool(d.SWIPE_OPTIONS),
                grouping = int(d.GROUPING),
                sortEnabled = bool(d.SORT_ENABLED),
                sortPrimary = int(d.SORT_PRIMARY),
                sortDesc = bool(d.SORT_DESC),
                sortStackedFirst = bool(d.SORT_STACKED_FIRST),
                sortPriority = text(d.SORT_PRIORITY),
                badge = NotificationBadgeSpec.decode(text(d.BADGE)),
            )
        }

        /** 优先应用名单(一行一个包名)→ 包名对应名次(越小越前) */
        fun priorityRank(value: String): Map<String, Int> = value.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapIndexed { i, pkg -> pkg to i }
            .toMap()
    }
}

/**
 * 字的配色。卡片看上去亮时用深色字,暗时用浅色字:按「后面是亮还是暗(安卓告诉系统的状态栏图标深浅)×
 * 背景压暗 + 玻璃表面的白」估出来,不截图量亮度。玻璃本身的白色浓度才看深浅色模式(照底栏)。
 */
@Immutable
data class LiquidLook(
    val title: Color,
    val body: Color,
    val secondary: Color,
    /** 卡片上按钮的底色 */
    val chip: Color,
    /** 进度条的底槽 */
    val track: Color,
    val textShadow: Shadow?,
) {
    companion object {
        val OnLight = LiquidLook(
            title = Color(0f, 0f, 0f, 0.88f),
            body = Color(0f, 0f, 0f, 0.74f),
            secondary = Color(0f, 0f, 0f, 0.46f),
            chip = Color(0f, 0f, 0f, 0.07f),
            track = Color(0f, 0f, 0f, 0.10f),
            textShadow = null,
        )
        val OnDark = LiquidLook(
            title = Color(1f, 1f, 1f, 0.98f),
            body = Color(1f, 1f, 1f, 0.88f),
            secondary = Color(1f, 1f, 1f, 0.58f),
            chip = Color(1f, 1f, 1f, 0.14f),
            track = Color(1f, 1f, 1f, 0.18f),
            textShadow = Shadow(Color(0f, 0f, 0f, 0.16f), Offset(0f, 1f), 3f),
        )
    }
}

/** 系统通知列表底部原有的按钮(安卓原版有「管理」「全部清除」);厂商把它挪到别处时为空 */
@Immutable
data class LiquidFooter(val manage: String?, val clearAll: String?)

/** 长按弹出的菜单:对哪条通知,卡片在界面里的位置 */
@Immutable
data class LiquidMenu(val notification: LiquidNotification, val anchor: Rect)

/**
 * 系统「清除全部通知」按钮在我们界面里的位置(界面自己的坐标)与它的图标或文字;
 * 系统按钮原来的样子藏起来,我们在这里画成玻璃,点击仍由系统按钮处理。
 */
@Immutable
data class LiquidClearAll(val rect: IntRect, val icon: ImageBitmap?, val label: String?)

/** 正在直接回复:哪条通知的哪个按钮 */
@Immutable
data class LiquidReplyTarget(val key: String, val action: LiquidAction)

/** 我们画的通知的全部状态,系统界面一侧更新,界面只读(菜单、回复、展开的组由界面自己改) */
@Stable
class LiquidUiState(private val savePins: (Map<String, Long>) -> Unit = {}) {
    /** 正在接管系统通知列表;不接管时界面什么都不建(传感器、图层都不占) */
    var shown by mutableStateOf(false)
    /** 这一次拉开通知中心的时间(毫秒,开机后计时);流光只在拉开后的一小段时间里走 */
    var openedAt by mutableLongStateOf(0L)

    /**
     * 界面画的列表:系统的列表并上正在离开、动画还没走完的通知(见 [update]、[ShadeListMerge])。
     * 用户清除后已藏起的也还在里面,界面按 [shows] 跳过。
     */
    var groups by mutableStateOf<List<LiquidGroup>>(emptyList())
        private set

    /** 系统最近一次给的列表 */
    private var fresh: List<LiquidGroup> = emptyList()
    var settings by mutableStateOf(LiquidSettings())
    var dark by mutableStateOf(false)
    internal var backgroundSource by mutableStateOf<com.astraflow.tool.hook.glass.CapturedBackground.Lease?>(null)
    var look by mutableStateOf(LiquidLook.OnLight)

    /** 通知中心拉开的程度,0 到 1;卡片按各自位置跟着它依次出现 */
    var expansion by mutableFloatStateOf(0f)

    /** 系统「清除全部通知」按钮(没显示时为空)、它的透明度和是否正被按住 */
    var clearAll by mutableStateOf<LiquidClearAll?>(null)
    var clearAlpha by mutableFloatStateOf(1f)
    var clearPressed by mutableStateOf(false)
    /** 我们这一层界面的左上角在屏幕上的位置;系统清除按钮的屏幕位置按它换成界面坐标 */
    var hostOffset by mutableStateOf(IntOffset.Zero)
    var now by mutableLongStateOf(System.currentTimeMillis())
    /** 列表从这里开始往下排(界面自己的坐标,像素),与系统通知列表的顶部留白一致 */
    var topPadding by mutableIntStateOf(0)
    var footer by mutableStateOf<LiquidFooter?>(null)
    /** 没有通知时系统显示的那句话;系统不显示时为空 */
    var emptyText by mutableStateOf<String?>(null)
    /** 系统正在全部清除:能清除的卡片依次飞走 */
    var clearing by mutableStateOf(false)
    /** 点开了的组 */
    var expanded by mutableStateOf<Set<String>>(emptySet())
    var menu by mutableStateOf<LiquidMenu?>(null)
    var reply by mutableStateOf<LiquidReplyTarget?>(null)

    /**
     * 置顶的通知(「置顶与左滑选项」,公开反馈 #80):通知标识 → 置顶的时间。置顶的组排在列表最上面(见 [ShadeListMerge.pinnedFirst]);
     * 经 [savePins] 保存，重启后由 [restorePins] 恢复；系统拿掉这条通知、或关掉这个设置后清掉。
     */
    var pinned by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    private var pinsRestored = false
    /** 重启后尚未出现在列表里的置顶通知；开机时系统会分批恢复，不能按空列表提前清除。 */
    private var pendingPins: Set<String> = emptySet()

    /** 向左滑露出选项的那一块(组或单张卡片的标识,见 LiquidShadeList);同一时间只有一块,点按卡片、滚动列表、收起通知中心时收回 */
    var revealed by mutableStateOf<String?>(null)

    /** 通知中心开着时新到的组:组的标识 → 到达时间(毫秒,开机后计时);这些组出现时做落入动画 */
    var arrivals by mutableStateOf<Map<String, Long>>(emptyMap())

    /**
     * 用户已经清除、离开的动画已经走完的通知:通知标识 → 清除时的内容版本。在我们的列表里藏起来,不等系统
     * (系统有时晚一点才拿掉,或者还要留着这条通知一段时间,之前这时会空出一块);应用更新了这条通知(版本变了)
     * 就重新显示;系统拿掉以后这条记录在 [update] 里清掉。收起通知中心时不清,下次拉开仍然藏着。
     */
    var hidden by mutableStateOf<Map<String, Long>>(emptyMap())

    /**
     * 已经决定清除、动画还没走完的:通知标识 → 清除时的内容版本。动画走完前留在原位(系统先拿掉也一样);
     * 收起通知中心时动画会被打断,这些也算藏起(见 [reset])
     */
    private var willHide: Map<String, Long> = emptyMap()

    /**
     * 应用自己撤回、系统已经拿掉的通知(通知标识):通知中心开着、卡片在画面上看得见时先留在原位,
     * 界面让它淡出、收拢位置,走完再 [hide]
     */
    var vanishing by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 决定清除时先记下(动画走完再 [hide]) */
    fun willHide(items: List<LiquidNotification>) {
        val added = items.filter { it.key !in willHide }
        if (added.isEmpty()) return
        willHide = willHide + added.associate { it.key to it.version }
    }

    /** 离开的动画走完:在我们的列表里藏起,不再留在原位 */
    fun hide(items: List<LiquidNotification>) {
        if (items.isEmpty()) return
        // 按清除那一刻的版本藏:清除以后应用又更新了这条,仍然显示
        hidden = hidden + items.associate { it.key to (willHide[it.key] ?: it.version) }
        val keys = items.mapTo(HashSet()) { it.key }
        willHide = willHide - keys
        if (vanishing.any { it in keys }) vanishing = vanishing - keys
        publish(ShadeListMerge.merge(groups, fresh, leaving(), ::shows))
    }

    /**
     * 这几条里还在离开的,动画直接结束:卡片不再画了(滚出画面,或飞出途中收起了这一组),
     * 动画走不完,不能一直留在原位
     */
    fun settle(items: List<LiquidNotification>) {
        hide(items.filter { it.key in willHide || it.key in vanishing })
    }

    /** 还在离开的全部直接结束(收起通知中心时):清除的藏起,撤回的拿掉 */
    private fun endLeaving() {
        if (willHide.isEmpty() && vanishing.isEmpty()) return
        if (willHide.isNotEmpty()) {
            hidden = hidden + willHide
            willHide = emptyMap()
        }
        if (vanishing.isNotEmpty()) vanishing = emptySet()
        publish(ShadeListMerge.merge(groups, fresh, emptySet(), ::shows))
    }

    private fun leaving(): Set<String> = if (vanishing.isEmpty()) willHide.keys else willHide.keys + vanishing

    /**
     * 换上系统新给的列表(系统界面主线程)。[animate] 为真时(通知中心开着),正在离开的通知先留在原位走完动画;
     * 应用自己撤回的,所在的组在画面上看得见([visible] 是列表此刻画出来的组)、而且是展开摆放的,
     * 或整组都撤回了,也先留着淡出。组的标识沿用正在显示的(见 [ShadeListMerge])。
     */
    fun update(system: List<LiquidGroup>, animate: Boolean, visible: Set<Any>) {
        fresh = system
        val versions = HashMap<String, Long>()
        for (g in system) for (n in g.items) versions[n.key] = n.version
        reconcilePins(versions.keys)
        // 系统已经拿掉的、应用又更新过的,藏起来的记录清掉
        if (hidden.isNotEmpty()) {
            val keep = hidden.filter { (key, version) -> versions[key] == version }
            if (keep.size != hidden.size) hidden = keep
        }
        if (!animate) {
            if (vanishing.isNotEmpty()) vanishing = emptySet()
            publish(ShadeListMerge.merge(groups, system, willHide.keys, ::shows))
        } else {
            // 撤回的又回来了就不再淡出
            val gone = vanishing.filterTo(HashSet()) { it !in versions }
            for (g in groups) {
                if (g.id !in visible) continue
                val showing = g.items.filter(::shows)
                val missing = showing.filter { it.key !in versions && it.key !in willHide && it.key !in gone }
                if (missing.isEmpty()) continue
                val open = !settings.stack || showing.size <= 1 || g.id in expanded
                // 堆叠着的一组只少了后面几张时不留(层数平滑变少),整组都没了才整组淡出
                if (open || missing.size == showing.size) missing.mapTo(gone) { it.key }
            }
            if (gone != vanishing) vanishing = gone
            publish(ShadeListMerge.merge(groups, system, leaving(), ::shows))
        }
        // 没了的组不再算展开
        val ids = groups.mapTo(HashSet()) { it.id }
        if (expanded.any { it !in ids }) expanded = expanded.intersect(ids)
    }

    /** 这条通知置顶着(开着「置顶与左滑选项」时) */
    fun isPinned(n: LiquidNotification): Boolean = settings.swipeOptions && n.key in pinned

    /** 打开通知中心、读入真实设置后恢复一次；之后只按开关与当前列表核对，不覆盖本次操作。 */
    fun restorePins(read: () -> Map<String, Long>) {
        if (!pinsRestored) {
            pinned = read().toMap()
            pendingPins = pinned.keys.toSet()
            pinsRestored = true
        }
        reconcilePins(fresh.flatMap { it.items }.mapTo(HashSet()) { it.key })
        publish(ShadeListMerge.merge(groups, fresh, leaving(), ::shows))
    }

    private fun reconcilePins(keys: Set<String>) {
        if (pinned.isEmpty()) return
        val keep = if (settings.swipeOptions) pinned.filterKeys { it in keys || it in pendingPins } else emptyMap()
        pendingPins = if (settings.swipeOptions) pendingPins - keys else emptySet()
        changePins(keep)
    }

    private fun changePins(value: Map<String, Long>) {
        if (pinned == value) return
        pinned = value
        pendingPins = pendingPins.intersect(value.keys)
        savePins(value)
    }

    /** 置顶或取消置顶:这几条都置顶着就一起取消,否则一起置顶(一组的按钮管整组);列表随即重排 */
    fun togglePin(items: List<LiquidNotification>, now: Long = System.currentTimeMillis()) {
        if (items.isEmpty() || !settings.swipeOptions) return
        val keys = items.mapTo(HashSet()) { it.key }
        changePins(if (keys.all { it in pinned }) pinned - keys else pinned + keys.filter { it !in pinned }.associateWith { now })
        publish(ShadeListMerge.merge(groups, fresh, leaving(), ::shows))
    }

    /** 换上新的列表:置顶的组排到最前面(没开「置顶与左滑选项」时照系统的先后) */
    private fun publish(list: List<LiquidGroup>) {
        groups = if (settings.swipeOptions) ShadeListMerge.pinnedFirst(list, pinned) else list
    }

    /** 这条通知该不该显示(用户清除了、内容也没再更新的不显示) */
    fun shows(n: LiquidNotification): Boolean = hidden[n.key] != n.version

    /** 这一组有没有还显示着的通知 */
    fun showsAny(g: LiquidGroup): Boolean = g.items.any(::shows)

    val list = LazyListState()

    /** 列表区域的上沿(界面自己的坐标,像素) */
    @Volatile
    var listTop = 0f

    /**
     * 最后一张卡片的下沿(界面自己的坐标,像素);最后一张还在下面没滚到时返回最大值。
     * 系统问「这个位置是不是通知下面的空白」、按下的位置归不归我们时用(系统界面主线程上读)。
     */
    fun contentBottom(): Float {
        val info = list.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return listTop + info.beforeContentPadding
        val last = items.last()
        if (last.index < info.totalItemsCount - 1) return Float.MAX_VALUE
        return listTop + (last.offset + last.size - info.viewportStartOffset)
    }

    /** 列表是否已经滚过开头、是否还能往下滚(系统问滚动位置时按这两项回答) */
    val scrolledFromTop: Boolean get() = list.canScrollBackward
    val canScrollDown: Boolean get() = list.canScrollForward

    /** 回到收起的样子(收起通知中心时) */
    fun reset() {
        expanded = emptySet()
        menu = null
        reply = null
        revealed = null
        clearing = false
        arrivals = emptyMap()
        // 离开的动画被收起打断:清除的下次拉开也藏着,撤回的直接拿掉
        endLeaving()
    }
}

/** 卡片上的操作,由系统界面一侧交给系统完成 */
interface LiquidCallbacks {
    fun open(n: LiquidNotification)
    fun action(n: LiquidNotification, action: LiquidAction)

    /** 直接回复;成功返回真 */
    fun reply(n: LiquidNotification, action: LiquidAction, text: String): Boolean

    /** 回复框出现或关闭:出现时系统才让通知中心窗口弹出键盘 */
    fun replying(active: Boolean)
    fun dismiss(items: List<LiquidNotification>)
    fun settings(n: LiquidNotification)
    fun clearAll()
    fun manage()

    /** 列表开始滚动或卡片开始横划:这次触摸归列表,系统面板不再抢 */
    fun claimGesture()
}
