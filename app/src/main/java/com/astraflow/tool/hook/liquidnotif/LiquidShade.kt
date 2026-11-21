package com.astraflow.tool.hook.liquidnotif

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.astraflow.frostshade.FrostPreferences
import com.astraflow.tool.config.ConfigKeys
import com.astraflow.tool.config.LiquidNotificationDefaults
import com.astraflow.tool.config.LiquidNotificationPins
import com.astraflow.tool.hook.core.HookHelper
import com.astraflow.tool.hook.liquidcc.LiquidControlCenter
import com.astraflow.tool.hook.liquidnotif.model.LiquidAction
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification
import com.astraflow.tool.hook.liquidnotif.ui.LiquidArrange
import com.astraflow.tool.hook.liquidnotif.ui.LiquidCallbacks
import com.astraflow.tool.hook.liquidnotif.ui.LiquidClearAll
import com.astraflow.tool.hook.liquidnotif.ui.LiquidFonts
import com.astraflow.tool.hook.liquidnotif.ui.LiquidFooter
import com.astraflow.tool.hook.liquidnotif.ui.LiquidLook
import com.astraflow.tool.hook.liquidnotif.ui.LiquidSettings
import com.astraflow.tool.hook.liquidnotif.ui.LiquidShadeList
import com.astraflow.tool.hook.liquidnotif.ui.LiquidUiState
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃通知的总管,全部在系统界面主线程上运行(读通知内容在后台线程)。
 *
 * 系统通知中心拉开、已解锁、功能打开时「接管」:系统自己的通知列表看不见、不接收触摸,
 * 所有通知由我们画在它原来的位置上。背景不截图:通知中心透明,后面的桌面或应用本身透过来;
 * 我们在通知中心窗口根视图的最底下放一层,用安卓原版的区域模糊和一层淡淡的颜色(见 [LiquidBackgroundView]),
 * 系统原来垫在通知下面的底色藏起来(不放在通知页里面:系统左右切到控制中心、上滑收起时会缩放、移动整个通知页)。
 * 背景跟着整个下拉面板走,通知页和控制中心共用(用户 2026-09-26「先做同一层背景吧」);卡片只跟着通知列表。
 * 收起、锁屏或功能关闭时交还系统,系统列表和底色原样恢复。下拉、收起、顶部时间和按钮、横幅照旧由系统负责。
 */
internal object LiquidShade {

    /** 「几分钟前」每隔这么久更新一次 */
    private const val CLOCK_TICK_MS = 30_000L

    /** 通知中心拉开到这个程度算「拉开了」,这时走流光 */
    private const val OPENED_FRACTION = 0.98f

    /**
     * 拉开后这么久以内换上的列表只当作拉开时本来就有的:不做新到的落入动画,也不做撤回的淡出
     * (列表还没按这一次拉开画出来,不知道哪些组看得见)
     */
    private const val SETTLE_MS = 400L

    /** 下拉到这个程度时,我们的区域模糊已经完全不透明(盖住系统原来的模糊);模糊程度再随下拉加到设定值 */
    private const val BLUR_COVER_FRACTION = 0.1f

    /** 下拉到这个程度时,背景那层淡淡的颜色已经完全到位(先快后慢),慢慢拉时顶部不会先亮一会儿 */
    private const val TINT_FULL_FRACTION = 0.35f

    /** 系统显示单独的亮度条时,背景在这么久(秒)里淡出让开,松手后同样淡回来 */
    private const val MIRROR_FADE_SECONDS = 0.15f

    /**
     * 卡片出现的进度往「出现」方向每秒最多走这么多倍全程(整段至少 0.25 秒):慢慢拉、或系统晚一点才告诉我们
     * 拉开了时,卡片也是渐渐出现(用户 2026-09-26「如果速度比较慢，通知出现的方式会突然一下出现」)
     */
    private const val REVEAL_PER_SECOND = 4f

    /** 拉开后这么久以内,排查开关开着时每隔 [TRACE_EVERY_MS] 记一次下拉的细节 */
    private const val TRACE_WINDOW_MS = 1_500L
    private const val TRACE_EVERY_MS = 100L

    /** 估卡片亮度时,后面「较亮」「较暗」各按多亮算(0 到 1) */
    private const val BEHIND_LIGHT = 0.85f
    private const val BEHIND_DARK = 0.2f

    /** 刚拉开时卡片字色的分界亮度(之后在 0.50 到 0.58 之间不切换,避免来回跳) */
    private const val LOOK_SPLIT = 0.54f

    private const val SYSTEM_UI = "com.android.systemui"

    private const val STATE_SHADE = 0

    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler by lazy { Handler(HandlerThread("FrostShade").apply { start() }.looper) }

    val ui = LiquidUiState(savePins = { pins ->
        list()?.context?.let { context ->
            worker.post {
                runCatching {
                    pinPreferences(context).edit()
                        .putString(LiquidNotificationPins.KEY, LiquidNotificationPins.encode(pins)).apply()
                }.onFailure { LiquidLog.w("pins: save failed", it) }
            }
        }
    })

    private fun pinPreferences(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("frostshade_pins", Context.MODE_PRIVATE)

    private var listRef: WeakReference<ViewGroup>? = null
    private var cards: LiquidCardsHost? = null
    private var background: LiquidBackgroundView? = null
    private var reader: LiquidEntries? = null
    private var fonts: LiquidFonts? = null

    /** 正在接管系统通知列表(系统界面各处每帧都会问,只读这一个值) */
    @Volatile
    var active = false
        private set

    private var expanded = false
    private var barState = 0
    private var reachedOpen = false
    private var replying = false
    private var activatedAt = 0L

    private var groups: List<LiquidEntries.ShadeGroup> = emptyList()
    private var listVersion = 0L
    private var shownVersion = -1L
    private var shownSettings: LiquidSettings? = null
    private var shownKeys: List<String> = emptyList()
    private var sbnByKey: Map<String, StatusBarNotification> = emptyMap()
    private var lastDecorCheck = 0L
    private val frameLocation = IntArray(2)

    /** 卡片出现的进度(见 [REVEAL_PER_SECOND])和上一帧的时间 */
    private var revealed = 0f
    private var lastRevealNs = 0L
    private var lastTraceAt = 0L

    // ── 功能开关(系统面板动画里每帧都会问,半秒内复用上一次的结果) ──

    @Volatile
    private var enabledCache = false

    @Volatile
    private var enabledStamp = -10_000L

    fun enabled(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - enabledStamp > 500L) {
            enabledCache = runCatching {
                FrostPreferences.getBoolean(ConfigKeys.LIQUID_NOTIFICATION_ENABLED, true)
            }.getOrDefault(false)
            enabledStamp = now
        }
        return enabledCache
    }

    private fun list(): ViewGroup? = listRef?.get()

    // ── 系统通知列表的状态 ──

    /** 系统通知列表挂上窗口(或第一次见到它) */
    fun onListSeen(list: ViewGroup) {
        if (listRef?.get() === list) return
        if (active) deactivate()
        if (backgroundOn) stopBackground()
        cards?.release()
        cards = null
        (background?.parent as? ViewGroup)?.removeView(background)
        background = null
        listRef = WeakReference(list)
        expanded = SystemFields.bool(list, "mIsExpanded") ?: false
        barState = SystemFields.int(list, "mStatusBarState") ?: 0
        LiquidLog.i("list attached: expanded=$expanded state=$barState")
        refresh()
    }

    fun onExpandedChanged(list: ViewGroup, value: Boolean) {
        onListSeen(list)
        if (expanded == value) return
        expanded = value
        refresh()
    }

    fun onBarStateChanged(list: ViewGroup, value: Int) {
        onListSeen(list)
        if (barState == value) return
        barState = value
        refresh()
    }

    /** 系统开始或结束「全部清除」 */
    fun onClearAllChanged(value: Boolean) {
        if (active) ui.clearing = value
    }

    /** 系统整理好的最终通知列表(主线程;列表对象随后会被系统清空,当场取完) */
    fun onFinalList(entries: List<*>) {
        val r = reader ?: (list()?.context ?: HookHelper.getAppContext())?.let { LiquidEntries(it.applicationContext ?: it, ::scheduleIconRefresh) }
            ?.also { reader = it } ?: return
        groups = r.extract(entries)
        listVersion++
        if (listVersion == 1L && !active) {
            refresh()
            if (active) return
        }
        if (active || enabled()) convert()
    }

    fun onNotificationIconUpdated(row: View) {
        reader?.invalidateIcon(row) ?: return
        scheduleIconRefresh()
    }

    fun onNotificationImageUpdated(image: ImageView) {
        if (Looper.myLooper() == Looper.getMainLooper()) reader?.onImageUpdated(image)
    }

    private fun scheduleIconRefresh() {
        // 一轮系统绑定可能连续更新多条通知,合并到主线程下一次执行,不逐条重画整个列表。
        main.removeCallbacks(refreshIcons)
        main.post(refreshIcons)
    }

    private val refreshIcons = Runnable {
        val updated = reader?.refreshIcons(groups) ?: return@Runnable
        if (updated === groups) return@Runnable
        groups = updated
        listVersion++
        if (active || enabled()) convert()
    }

    /** 状态变化后决定背景在不在、卡片接管还是交还(背景先定:设置在背景开始时读) */
    private fun refresh() {
        refreshBackground()
        val list = list() ?: return
        val want = expanded && barState == STATE_SHADE && listVersion > 0 && enabled()
        if (want == active) return
        if (want) activate(list) else deactivate()
    }

    // ── 接管与交还(只管通知页里的卡片;背景见下面「背景」一节,两页共用) ──

    private fun activate(list: ViewGroup) {
        val host = ensureHosts(list) ?: return
        active = true
        reachedOpen = false
        activatedAt = SystemClock.uptimeMillis()
        ui.now = System.currentTimeMillis()
        ui.clearing = false
        ui.openedAt = 0L
        ui.shown = true
        // 卡片从没出现开始,按 [REVEAL_PER_SECOND] 跟上下拉程度
        revealed = 0f
        lastRevealNs = 0L
        ui.expansion = 0f
        host.visibility = View.VISIBLE
        // 系统列表看不见(不改它自己的透明度,系统读到的仍是原值)
        list.transitionAlpha = 0f
        if (shownVersion != listVersion || shownSettings != ui.settings) convert()
        main.removeCallbacks(clockTick)
        main.postDelayed(clockTick, CLOCK_TICK_MS)
        LiquidLog.i("activated: background=$backgroundOn shade=$shadeFraction")
    }

    private fun deactivate() {
        active = false
        main.removeCallbacks(clockTick)
        if (replying) replying(false)
        ui.shown = false
        ui.reset()
        ui.openedAt = 0L
        ui.clearAll = null
        restoreClearAll()
        cards?.visibility = View.GONE
        list()?.transitionAlpha = 1f
        runCatching { ui.list.requestScrollToItem(0) }
        LiquidLog.i("deactivated")
    }

    private fun ensureHosts(list: ViewGroup): LiquidCardsHost? {
        val parent = list.parent as? ViewGroup ?: return null
        cards?.let { if (it.parent !== parent) { it.release(); cards = null } }
        placeBackground(list)
        cards?.let { return it }
        return runCatching {
            val h = LiquidCardsHost(list.context, list, ::syncFrame, ::acceptsDown)
            h.id = View.generateViewId()
            val f = fonts ?: LiquidFonts.system().also { fonts = it }
            h.setContent { LiquidShadeList(state = ui, fonts = f, callbacks = callbacks) }
            h.visibility = View.GONE
            parent.addView(h, parent.indexOfChild(list) + 1, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            SystemFields.fillConstraintParent(h)
            cards = h
            LiquidLog.i("cards added to ${parent.javaClass.name}")
            h
        }.onFailure { LiquidLog.w("cards setup failed", it) }.getOrNull()
    }

    /**
     * 背景放在通知中心窗口根视图的最底下(所有子视图下面):系统原来垫在通知下面的底色画在它上面,
     * 背景在的时候由我们藏起(见 [hideUnderlay]);通知页和控制中心页也都在它上面。
     * 两页左右切换、被缩放、移动时背景都不跟着动(用户 2026-09-26「先做同一层背景吧」)。
     */
    private fun placeBackground(list: ViewGroup) {
        val root = list.rootView as? ViewGroup ?: return
        val bg = background?.takeIf { it.parent === root } ?: run {
            (background?.parent as? ViewGroup)?.removeView(background)
            background = null
            runCatching {
                LiquidBackgroundView(list.context, ::syncBackground).also {
                    // 系统重排布局时会按编号记下每个子视图,没有编号的会出错
                    it.id = View.generateViewId()
                    it.visibility = View.GONE
                }
            }.onFailure { LiquidLog.w("background setup failed", it) }.getOrNull() ?: return
        }
        if (root.indexOfChild(bg) == 0) {
            background = bg
            return
        }
        runCatching {
            if (bg.parent === root) root.removeView(bg)
            root.addView(bg, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            SystemFields.fillConstraintParent(bg)
            background = bg
            LiquidLog.i("background placed at the bottom of ${root.javaClass.name}")
        }.onFailure { LiquidLog.w("background placement failed", it) }
    }

    /**
     * 通知列表往上找,直到窗口根视图的直接子视图:它就是「装着通知页的那一层」
     * (这台手机上通知页和控制中心并排装在这一层里,左右切换在它里面进行)
     */
    private fun pageContainer(list: View, root: ViewGroup): View? {
        var v: View = list
        while (true) {
            val p = v.parent as? View ?: return null
            if (p === root) return v
            v = p
        }
    }

    /**
     * 按下的位置归不归我们:菜单开着时全归我们(点别处关菜单);否则只有列表区域里、最后一张卡片以上的归我们,
     * 顶部时间和按钮那一块、卡片下面的空白都交给系统。
     */
    private fun acceptsDown(x: Float, y: Float): Boolean {
        if (!active) return false
        if (ui.menu != null) return true
        val density = list()?.resources?.displayMetrics?.density ?: 1f
        return y <= ui.contentBottom() + 8f * density
    }

    private fun readSettings(): LiquidSettings =
        LiquidSettings.read(getInt = FrostPreferences::getInt, getBoolean = FrostPreferences::getBoolean, getString = FrostPreferences::getString)

    private fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ── 通知内容 ──

    private fun convert() {
        val r = reader ?: return
        val version = listVersion
        val snapshot = groups
        // 归组与重排用的设置在主线程快照一份(系统在打开通知中心时读进来)
        val s = ui.settings
        worker.post {
            val models = runCatching { r.convert(snapshot) }
                .onFailure { LiquidLog.w("convert failed", it) }
                .getOrNull() ?: return@post
            val arranged = runCatching { LiquidArrange.arrange(models, s) }
                .onFailure { LiquidLog.w("arrange failed", it) }
                .getOrDefault(models)
            main.post {
                if (version != listVersion || s != ui.settings) return@post
                val all = snapshot.flatMap { g -> g.items.map { it.sbn } }
                shownKeys = all.map { it.key }
                sbnByKey = all.associateBy { it.key }
                val before = ui.groups.filter(ui::showsAny).mapTo(HashSet()) { it.id }
                // 通知中心开着(拉开一会儿以后)时,离开的卡片先留着走完动画;看得见的组按列表此刻画出来的算
                val animate = active && SystemClock.uptimeMillis() - activatedAt >= SETTLE_MS
                val visible = if (animate) ui.list.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key } else emptySet()
                ui.update(arranged, animate, visible)
                shownVersion = version
                shownSettings = s
                markArrivals(before)
                val keys = shownKeys.toSet()
                ui.menu?.let { if (it.notification.key !in keys) ui.menu = null }
                ui.reply?.let { if (it.key !in keys) ui.reply = null }
            }
        }
    }

    /**
     * 通知中心开着时新到的组记下到达时间,界面据此做落入动画(拉开那一刻已有的不算)。
     * [before] 是换上新列表之前显示着的组;标识接上的组(见 [LiquidUiState.update])不算新到。
     */
    private fun markArrivals(before: Set<String>) {
        if (!active) return
        val now = SystemClock.uptimeMillis()
        if (now - activatedAt < SETTLE_MS) return
        val fresh = ui.groups.filter { it.id !in before && ui.showsAny(it) }.map { it.id }
        if (fresh.isEmpty()) return
        ui.arrivals = ui.arrivals.filterValues { now - it < 3_000L } + fresh.associateWith { now }
    }

    private val callbacks = object : LiquidCallbacks {
        override fun open(n: LiquidNotification) {
            val list = list() ?: return
            val sbn = sbnByKey[n.key] ?: return
            if (LiquidSystem.open(list.context, sbn, rank(n.key), shownKeys.size)) LiquidSystem.collapse()
        }

        override fun action(n: LiquidNotification, action: LiquidAction) {
            val list = list() ?: return
            val sbn = sbnByKey[n.key] ?: return
            // 与系统通知按钮一致：需要解锁的动作即使通过广播执行，也在发送成功后收起通知中心。
            if (LiquidSystem.action(list.context, sbn, action.index, rank(n.key), shownKeys.size) &&
                (action.intent?.isActivity == true ||
                    sbn.notification.actions?.getOrNull(action.index)?.isAuthenticationRequired == true)
            ) {
                LiquidSystem.collapse()
            }
        }

        override fun reply(n: LiquidNotification, action: LiquidAction, text: String): Boolean {
            val list = list() ?: return false
            val sbn = sbnByKey[n.key] ?: return false
            return LiquidSystem.reply(list.context, sbn, action.index, text, rank(n.key), shownKeys.size)
        }

        override fun replying(active: Boolean) = this@LiquidShade.replying(active)

        override fun dismiss(items: List<LiquidNotification>) {
            for (n in items) sbnByKey[n.key]?.let { LiquidSystem.dismiss(it, rank(n.key), shownKeys.size) }
        }

        override fun settings(n: LiquidNotification) {
            val list = list() ?: return
            LiquidSystem.openSettings(list.context, n.packageName, n.channelId)
        }

        override fun clearAll() {
            list()?.let { SystemFields.footerButton(it, "dismiss_text")?.performClick() }
        }

        override fun manage() {
            list()?.let { SystemFields.footerButton(it, "manage_text")?.performClick() }
        }

        override fun claimGesture() {
            cards?.parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun replying(value: Boolean) {
        if (replying == value) return
        replying = value
        LiquidSystem.setReplying(value)
    }

    private fun rank(key: String): Int = shownKeys.indexOf(key).coerceAtLeast(0)

    // ── 每一帧:跟着系统通知列表的位置、透明度和拉开程度走 ──

    private fun syncFrame() {
        val list = list() ?: return
        val host = cards ?: return
        if (!active) {
            if (host.visibility != View.GONE) host.visibility = View.GONE
            return
        }
        if (!enabled()) {
            refresh()
            return
        }
        // 系统有时会把整个列表的显隐效果重设,这里每帧确认它仍然看不见
        if (list.transitionAlpha != 0f) list.transitionAlpha = 0f
        val top = SystemFields.topPadding(list)
        if (top != null && ui.topPadding != top) ui.topPadding = top
        val fraction = SystemFields.expansionFraction(list) ?: 1f
        // 卡片按各自的位置跟着下拉程度依次出现(界面里读这个数,见 LiquidShadeList 的 cascade);
        // 往「出现」方向限速,往「收起」方向跟手
        val nowNs = System.nanoTime()
        val dt = if (lastRevealNs == 0L) 0f else ((nowNs - lastRevealNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastRevealNs = nowNs
        revealed = if (fraction <= revealed) fraction else minOf(fraction, revealed + dt * REVEAL_PER_SECOND)
        if (ui.expansion != revealed) ui.expansion = revealed
        // 还没跟上时再要一帧,手指停住时也会继续渐渐出现
        if (revealed < fraction) host.postInvalidateOnAnimation()
        if (!reachedOpen && fraction >= OPENED_FRACTION) {
            reachedOpen = true
            ui.openedAt = SystemClock.uptimeMillis()
        }
        val compose = host.compose
        compose.visibility = list.visibility
        compose.alpha = list.alpha
        compose.translationX = list.translationX
        compose.translationY = list.translationY
        val loc = frameLocation
        compose.getLocationOnScreen(loc)
        if (ui.hostOffset.x != loc[0] || ui.hostOffset.y != loc[1]) ui.hostOffset = IntOffset(loc[0], loc[1])
        // 背景的顶部加深淡到第一张卡片的上沿(背景两页共用,只在这里量);只在通知页完全拉开时量,
        // 下拉途中、收起途中沿用上一次的,背景不跟着通知列表的移动变
        if (fraction >= OPENED_FRACTION) measureTopEnd(list)
        val now = SystemClock.uptimeMillis()
        if (LiquidLog.on && now - activatedAt < TRACE_WINDOW_MS && now - lastTraceAt >= TRACE_EVERY_MS) {
            lastTraceAt = now
            LiquidLog.i(
                "pull t=${now - activatedAt} f=${"%.3f".format(fraction)} shown=${"%.3f".format(revealed)} " +
                    "listAlpha=${"%.2f".format(list.alpha)} listY=${list.translationY.roundToInt()} " +
                    "listVis=${list.visibility} shade=${"%.3f".format(shadeFraction)} groups=${ui.groups.size}",
            )
        }
        syncClearAll(list, compose)
        if (now - lastDecorCheck > 250L) {
            lastDecorCheck = now
            val footer = SystemFields.footer(list)
            if (footer != ui.footer) ui.footer = footer
            val empty = SystemFields.emptyText(list)
            if (empty != ui.emptyText) ui.emptyText = empty
        }
    }

    // ── 背景:通知页和控制中心共用一层 ──

    /** 整个下拉面板拉开的程度,0 到 1(安卓原版的拉开进度;两页合在一起报时取拉开得更多的那一页) */
    private var shadeFraction = 0f

    /** 整个下拉面板打开着(没有完全收起) */
    private var shadeOpen = false

    /** 背景正在显示 */
    private var backgroundOn = false

    /** 背景正在显示(液态玻璃控制中心据此决定亮度音量条画不画玻璃) */
    val backgroundShowing: Boolean get() = backgroundOn

    /**
     * 背景要不要有:液态玻璃堆叠通知、液态玻璃控制中心打开任意一个就有,铺满两页
     * (用户 2026-09-26 选「任一打开就铺满两页」)
     */
    private fun backgroundEnabled(): Boolean = enabled() || LiquidControlCenter.enabled()

    /**
     * 顶部加深淡到哪里(背景自己的坐标,像素):第一张卡片的上沿。只在通知页接管并完全拉开时量,
     * 切到控制中心后沿用;从没量过时,背景开始时按通知列表的版面位置量一次
     */
    private var topEnd = 0f

    /** 拖动亮度条、系统显示单独的亮度条时背景让开的程度(0 到 1),上一帧的时间 */
    private var mirrorAway = 0f
    private var lastBackgroundNs = 0L
    private var mirrorRef: WeakReference<View>? = null
    private var mirrorId = -1

    /**
     * 安卓原版的下拉面板拉开进度(见 [LiquidNotificationHook]):背景跟着它走,不跟着通知页走,
     * 左右切到控制中心时背景不变(用户 2026-09-26「先做同一层背景吧」)。
     */
    fun onShadeExpansion(fraction: Float, expanded: Boolean) {
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            main.post { onShadeExpansion(fraction, expanded) }
            return
        }
        shadeFraction = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        shadeOpen = expanded || shadeFraction > 0f
        refreshBackground()
        if (backgroundOn) syncBackground()
    }

    /** 背景在不在:下拉面板打开着(或通知列表拉开着)、已解锁、两个功能打开任意一个时在 */
    private fun refreshBackground() {
        // 液态玻璃控制中心关掉了:换上去的玻璃还回去(背景没有时每帧处理不会来,在这里还)
        if (!LiquidControlCenter.enabled()) LiquidControlCenter.restoreAll()
        val want = (shadeOpen || expanded) && barState == STATE_SHADE && backgroundEnabled()
        if (want == backgroundOn) return
        if (want) startBackground() else stopBackground()
    }

    private fun startBackground() {
        val list = list() ?: return
        placeBackground(list)
        val bg = background ?: return
        backgroundOn = true
        // 设置在打开下拉面板时读一次,卡片也用这一份
        ui.settings = readSettings()
        ui.restorePins {
            val saved = runCatching { pinPreferences(list.context).getString(LiquidNotificationPins.KEY, "") }.getOrNull()
            LiquidNotificationPins.decode(saved.orEmpty())
        }
        ui.dark = isDark(list.context)
        ui.backgroundSource = bg.capture
        // 区域模糊的目标程度(像素,至少 1)、顶部加深;颜色和字色按后面亮暗算好
        val density = list.resources.displayMetrics.density
        blurTargetPx = (ui.settings.backgroundBlurDp * density).roundToInt().coerceAtLeast(1)
        topDimTarget = ui.settings.topDim / 100f
        updateTone(snap = true)
        if (topEnd <= 0f) measureTopEnd(list)
        mirrorAway = 0f
        lastBackgroundNs = 0L
        // 液态玻璃控制中心的玻璃也在这时读设置(与本功能共用的几项)
        LiquidControlCenter.onShadeOpened(list.context)
        com.astraflow.tool.hook.liquidcc.CcGlassEnv.backgroundSource = bg.capture
        bg.update(radius = 1, blurAlpha = 0f, dim = 0f, topDim = 0f, topEnd = topEnd)
        bg.visibility = View.VISIBLE
        LiquidLog.i("background on: blur=${blurTargetPx}px areaBlur=${bg.hasAreaBlur} behindLight=$behindLight shade=$shadeFraction")
    }

    private fun stopBackground() {
        backgroundOn = false
        ui.backgroundSource = null
        LiquidControlCenter.onShadeClosed()
        com.astraflow.tool.hook.liquidcc.CcGlassEnv.backgroundSource = null
        restoreUnderlay()
        background?.let {
            it.update(radius = 1, blurAlpha = 0f, dim = 0f, topDim = 0f, topEnd = topEnd)
            it.visibility = View.GONE
        }
        LiquidLog.i("background off")
    }

    /** 后台背景读取完成，只更新实际亮暗；卡片图片由绘制层直接使用。 */
    fun onBackgroundFrame() {
        if (!backgroundOn) return
        updateTone(snap = false)
        background?.postInvalidateOnAnimation()
    }

    /**
     * 每一帧(背景自己画之前)和拉开进度变了时:区域模糊在下拉开头就完全不透明(盖住系统原来的模糊),
     * 程度随下拉先快后慢地从 1 像素加到设定值,颜色在下拉前段就到位;两页之间都不变。
     * 系统原来垫在底下的那几层一直藏着;系统显示单独的亮度条时背景让开。
     */
    private fun syncBackground() {
        if (!backgroundOn) return
        val bg = background ?: return
        val list = list() ?: return
        if (!backgroundEnabled()) {
            refresh()
            return
        }
        val nowNs = System.nanoTime()
        val dt = if (lastBackgroundNs == 0L) 0f else ((nowNs - lastBackgroundNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastBackgroundNs = nowNs
        // 压暗平滑地变到新的值(后面换了亮暗时不一下子跳,约 0.1 秒)
        if (abs(targetDim - currentDim) > 0.002f) currentDim += (targetDim - currentDim) * 0.3f
        // 拖动亮度条时让开,松手后回来
        val away = if (mirrorShown(list)) 1f else 0f
        if (mirrorAway != away) {
            val step = dt / MIRROR_FADE_SECONDS
            mirrorAway = if (away > mirrorAway) minOf(1f, mirrorAway + step) else maxOf(0f, mirrorAway - step)
            bg.postInvalidateOnAnimation()
        }
        val keep = 1f - mirrorAway
        val f = shadeFraction
        val tint = easeOut((f / TINT_FULL_FRACTION).coerceIn(0f, 1f)) * keep
        // 液态玻璃控制中心开着时:浅色模式下控制中心那一页额外压暗(它的图标文字是白色的),
        // 按控制中心在屏幕上显示的程度平滑过渡;模糊不变
        val root = list.rootView as? ViewGroup
        val ccOn = LiquidControlCenter.enabled()
        val ccExtra = if (ccOn && !ui.dark && root != null) LiquidControlCenter.dimLight * LiquidControlCenter.visibleFraction(root) else 0f
        val dim = 1f - (1f - currentDim) * (1f - ccExtra)
        bg.update(
            radius = 1 + ((blurTargetPx - 1) * easeOut(f)).roundToInt(),
            blurAlpha = (f / BLUR_COVER_FRACTION).coerceIn(0f, 1f) * keep,
            dim = dim * tint,
            topDim = topDimTarget * tint,
            topEnd = topEnd,
        )
        hideUnderlay(list)
        // 液态玻璃控制中心:各部件的底子在这一帧画之前换成玻璃(关着时还原)
        if (root != null) LiquidControlCenter.sync(root)
    }

    /**
     * 顶部加深淡到的位置:第一张卡片的上沿 = 通知列表在根视图里的版面位置 + 它的顶部留白。
     * 按版面位置算,不算通知页左右切换时的平移和缩小,背景在两页之间不动。
     */
    private fun measureTopEnd(list: ViewGroup) {
        val root = list.rootView
        val padding = SystemFields.topPadding(list) ?: return
        var y = list.translationY + padding
        var v: View? = list
        while (v != null && v !== root) {
            y += v.top
            v = v.parent as? View
        }
        if (y > 0f) topEnd = y
    }

    /**
     * 系统正在显示单独的亮度条:拖动亮度条时,系统只留这一条、藏起其余部分,好让人看清亮度的变化。
     * 这一条是安卓原版窗口根视图里的一层(`brightness_mirror_container`,平时隐藏),只在根视图的直接子视图里找。
     */
    private fun mirrorShown(list: View): Boolean {
        val root = list.rootView as? ViewGroup ?: return false
        var v = mirrorRef?.get()?.takeIf { it.parent === root }
        if (v == null) {
            if (mirrorId == -1) mirrorId = list.resources.getIdentifier("brightness_mirror_container", "id", SYSTEM_UI)
            if (mirrorId == 0) return false
            for (i in 0 until root.childCount) {
                val c = root.getChildAt(i)
                if (c.id == mirrorId) {
                    v = c
                    break
                }
            }
            mirrorRef = v?.let { WeakReference(it) }
        }
        return v != null && v.visibility == View.VISIBLE && v.alpha > 0.01f
    }

    // ── 背景的颜色、卡片的字色、系统原来的底色 ──

    /**
     * 后面是亮还是暗:安卓告诉系统界面的「状态栏图标用深色」(应用顶部是浅色时这样要;桌面按壁纸决定)。
     * 不截图量亮度(用户 2026-09-26「我们不是实时透传吗？怎么还要截图啊？」)。
     */
    @Volatile
    private var behindLight = false

    /** 区域模糊的目标程度(像素)和顶部加深(0 到 1),拉开时按设置算好 */
    private var blurTargetPx = 1
    private var topDimTarget = 0f

    /** 整体压暗(0 到 1):目标值和正在显示的值(平滑地变过去) */
    private var targetDim = 0f
    private var currentDim = 0f

    /**
     * 安卓发给系统界面的状态栏外观(任意线程)。只记系统界面以外的应用发来的:通知中心自己拉开后也会发,
     * 那不是后面的应用。
     */
    fun onSystemBars(displayId: Int, appearance: Int, packageName: String?) {
        if (displayId != Display.DEFAULT_DISPLAY || packageName == SYSTEM_UI) return
        val light = appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0
        main.post {
            if (behindLight == light) return@post
            behindLight = light
            LiquidLog.i("behind light=$light from $packageName")
            if (backgroundOn) {
                updateTone(snap = false)
                // 压暗要一帧一帧平滑地变过去
                background?.postInvalidateOnAnimation()
            }
        }
    }

    /**
     * 压暗 = 1 −(1 − 基础压暗)×(1 − 后面较亮时加深);卡片字色按「后面亮暗 × 压暗 + 玻璃表面的白」
     * 估出来的亮度选。[snap] 为真时(刚拉开)直接用新值,不从旧值慢慢变过去。
     */
    private fun updateTone(snap: Boolean) {
        val s = ui.settings
        background?.capture?.frame?.value?.let { frame ->
            val tone = frame.tones?.luminance(android.graphics.RectF(0f, 0f, frame.screenWidth.toFloat(), frame.screenHeight.toFloat()), frame.screenWidth, frame.screenHeight)
            if (tone != null) behindLight = if (behindLight) tone >= 0.48f else tone >= 0.58f
        }
        val base = (if (ui.dark) s.dimDark else s.dimLight) / 100f
        val extra = if (behindLight) s.brightExtra / 100f else 0f
        targetDim = 1f - (1f - base) * (1f - extra)
        if (snap) currentDim = targetDim
        val surface = (if (ui.dark) s.surfaceDark else s.surfaceLight) / 100f
        val behind = if (behindLight) BEHIND_LIGHT else BEHIND_DARK
        val card = behind * (1f - targetDim) * (1f - surface) + surface
        ui.look = if (snap) {
            if (card >= LOOK_SPLIT) LiquidLook.OnLight else LiquidLook.OnDark
        } else {
            pickLook(card)
        }
    }

    /** 先快后慢:0 到 1 */
    private fun easeOut(x: Float): Float {
        val u = 1f - x.coerceIn(0f, 1f)
        return 1f - u * u
    }

    /** 卡片亮用深色字、暗用浅色字;在两者之间留一段不切换,避免来回跳 */
    private fun pickLook(luminance: Float): LiquidLook = when {
        ui.look === LiquidLook.OnLight && luminance < 0.50f -> LiquidLook.OnDark
        ui.look === LiquidLook.OnDark && luminance > 0.58f -> LiquidLook.OnLight
        else -> ui.look
    }

    /** 藏起来的系统底色(背景结束时恢复) */
    private val underlay = ArrayList<WeakReference<View>>()

    /**
     * 系统原来垫在通知下面的底色:通知中心窗口根视图里、装着通知页那一层以下的子视图(不含我们的背景),
     * 按位置找,不认名字。背景在的时候两页都藏着(通知页和控制中心装在同一层里,都在它们上面);
     * 单独的亮度条排在这一层上面,不在其中。
     */
    private fun hideUnderlay(list: ViewGroup) {
        val root = list.rootView as? ViewGroup ?: return
        val page = pageContainer(list, root) ?: return
        for (i in 0 until root.indexOfChild(page)) {
            val v = root.getChildAt(i) ?: continue
            if (v === background || v.transitionAlpha == 0f) continue
            if (underlay.none { it.get() === v }) underlay.add(WeakReference(v))
            v.transitionAlpha = 0f
        }
    }

    private fun restoreUnderlay() {
        for (ref in underlay) ref.get()?.let { if (it.transitionAlpha != 1f) it.transitionAlpha = 1f }
        underlay.clear()
    }

    // ── 系统的「清除全部通知」按钮 ──

    private var clearRef: WeakReference<View>? = null
    private var clearHidden: WeakReference<View>? = null
    /**
     * 装着卡片界面的那一层(共享通知容器)原来裁不裁超出部分。横屏时系统把清除按钮放在这一层外面(右边),
     * 玻璃按钮画在卡片界面里、摆在系统按钮的位置,这一层一裁就看不见;接管清除按钮期间改为不裁,交还时还原。
     */
    private var clipRestore: Pair<WeakReference<ViewGroup>, Boolean>? = null
    private var lastClearSearch = 0L
    private var clearText: String? = null
    private var clearIconFrom: Any? = null
    private var clearIcon: androidx.compose.ui.graphics.ImageBitmap? = null
    private val clearLocation = IntArray(2)

    /**
     * 系统自己的「清除全部通知」按钮(安卓原版给它的说明文字是「清除所有通知」,各家沿用):
     * 找到后把它原来的样子藏起来(过渡透明度为 0,仍然能点),在同一位置画一个玻璃按钮,
     * 按下时跟着它的按下状态一起动。点击仍由系统的按钮处理(清除动画、确认都照系统)。
     */
    private fun syncClearAll(list: ViewGroup, compose: View) {
        var button = clearRef?.get()?.takeIf { it.isAttachedToWindow && isClearAll(it) }
        val now = SystemClock.uptimeMillis()
        if (button == null && now - lastClearSearch > 1_000L) {
            lastClearSearch = now
            button = findClearAll(list)
            clearRef = button?.let { WeakReference(it) }
        }
        if (button == null) {
            if (ui.clearAll != null) ui.clearAll = null
            return
        }
        if (button.transitionAlpha != 0f) button.transitionAlpha = 0f
        clearHidden = WeakReference(button)
        unclipContainer(list)
        if (!button.isShown || button.alpha <= 0.01f || button.width <= 0) {
            if (ui.clearAll != null) ui.clearAll = null
            return
        }
        button.getLocationOnScreen(clearLocation)
        val x = clearLocation[0] - ui.hostOffset.x
        val y = clearLocation[1] - ui.hostOffset.y
        val rect = IntRect(x, y, x + button.width, y + button.height)
        val icon = clearIconOf(button)
        val label = if (icon == null) (button as? TextView)?.text?.toString()?.takeIf { it.isNotBlank() } else null
        val info = LiquidClearAll(rect, icon, label)
        if (info != ui.clearAll) ui.clearAll = info
        if (ui.clearAlpha != button.alpha) ui.clearAlpha = button.alpha
        if (ui.clearPressed != button.isPressed) ui.clearPressed = button.isPressed
    }

    private fun restoreClearAll() {
        clearHidden?.get()?.let { if (it.transitionAlpha == 0f) it.transitionAlpha = 1f }
        clearHidden = null
        restoreClip()
    }

    private fun unclipContainer(list: ViewGroup) {
        val container = list.parent as? ViewGroup ?: return
        if (clipRestore?.first?.get() === container) return
        restoreClip()
        clipRestore = WeakReference(container) to container.clipChildren
        if (container.clipChildren) container.clipChildren = false
    }

    private fun restoreClip() {
        val (ref, was) = clipRestore ?: return
        clipRestore = null
        ref.get()?.let { if (it.clipChildren != was) it.clipChildren = was }
    }

    private fun isClearAll(v: View): Boolean {
        val text = clearText ?: return false
        return v.contentDescription?.toString() == text
    }

    /** 从装着通知页的那一层往下找(跳过系统通知列表和我们自己的界面),说明文字是「清除所有通知」且正在显示的那个 */
    private fun findClearAll(list: ViewGroup): View? {
        if (clearText == null) {
            clearText = runCatching {
                val id = list.resources.getIdentifier("accessibility_clear_all", "string", "com.android.systemui")
                if (id != 0) list.resources.getString(id) else null
            }.getOrNull() ?: return null
        }
        val root = list.rootView as? ViewGroup ?: return null
        val page = pageContainer(list, root) ?: return null
        val skip = setOfNotNull<View>(list, cards, background)
        fun walk(v: View): View? {
            if (v in skip) return null
            if (v.visibility == View.VISIBLE && isClearAll(v)) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))?.let { return it }
            }
            return null
        }
        return walk(page)
    }

    /** 系统按钮上的图标画成图,玻璃按钮上按字色重新着色 */
    private fun clearIconOf(button: View): androidx.compose.ui.graphics.ImageBitmap? {
        val drawable = (button as? ImageView)?.drawable ?: return null
        if (drawable === clearIconFrom) return clearIcon
        val w = (button.width - button.paddingLeft - button.paddingRight).coerceAtLeast(1)
        val h = (button.height - button.paddingTop - button.paddingBottom).coerceAtLeast(1)
        val bitmap = runCatching {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val copy = drawable.constantState?.newDrawable(button.resources)?.mutate() ?: drawable
            copy.setBounds(0, 0, w, h)
            copy.draw(Canvas(b))
            b
        }.onFailure { LiquidLog.w("clear icon draw failed", it) }.getOrNull()
        clearIconFrom = drawable
        clearIcon = bitmap?.asImageBitmap()
        return clearIcon
    }

    private val clockTick = object : Runnable {
        override fun run() {
            if (!active) return
            ui.now = System.currentTimeMillis()
            main.postDelayed(this, CLOCK_TICK_MS)
        }
    }

    /**
     * 读安卓原版通知列表里的几个值。名字都是安卓原版系统界面的,各家系统都有;
     * 读不到时返回空,由调用处按「没有这一项」处理。
     */
    private object SystemFields {
        private val fields = HashMap<String, Field?>()
        private val methods = HashMap<String, Method?>()
        private val ids = HashMap<String, Int>()

        private fun field(cls: Class<*>, name: String): Field? = fields.getOrPut("${cls.name}#$name") {
            var c: Class<*>? = cls
            var found: Field? = null
            while (c != null && found == null) {
                found = runCatching { c.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
                c = c.superclass
            }
            found
        }

        private fun method(cls: Class<*>, name: String): Method? = methods.getOrPut("${cls.name}#$name") {
            var c: Class<*>? = cls
            var found: Method? = null
            while (c != null && found == null) {
                found = runCatching { c.getDeclaredMethod(name).apply { isAccessible = true } }.getOrNull()
                c = c.superclass
            }
            found
        }

        fun bool(target: Any, name: String): Boolean? = runCatching { field(target.javaClass, name)?.getBoolean(target) }.getOrNull()

        fun int(target: Any, name: String): Int? = runCatching { field(target.javaClass, name)?.getInt(target) }.getOrNull()

        private fun obj(target: Any, name: String): Any? = runCatching { field(target.javaClass, name)?.get(target) }.getOrNull()

        /** 通知列表顶部留白(列表自己的坐标,像素) */
        fun topPadding(list: ViewGroup): Int? = runCatching { method(list.javaClass, "getTopPadding")?.invoke(list) as? Int }.getOrNull()

        /** 通知中心拉开的程度,0 到 1 */
        fun expansionFraction(list: ViewGroup): Float? = runCatching {
            val ambient = obj(list, "mAmbientState") ?: return null
            method(ambient.javaClass, "getExpansionFraction")?.invoke(ambient) as? Float
        }.getOrNull()

        private fun id(list: View, name: String): Int = ids.getOrPut(name) {
            list.resources.getIdentifier(name, "id", "com.android.systemui")
        }

        /** 系统通知列表底部的按钮;底部整块不显示或按钮不显示时为空 */
        fun footerButton(list: ViewGroup, idName: String): TextView? {
            val footer = obj(list, "mFooterView") as? View ?: return null
            if (!footer.isShown) return null
            val id = id(list, idName)
            if (id == 0) return null
            return (footer.findViewById<View>(id) as? TextView)?.takeIf { it.visibility == View.VISIBLE }
        }

        fun footer(list: ViewGroup): LiquidFooter? {
            val manage = footerButton(list, "manage_text")?.text?.toString()?.takeIf { it.isNotBlank() }
            val clear = footerButton(list, "dismiss_text")?.text?.toString()?.takeIf { it.isNotBlank() }
            return if (manage == null && clear == null) null else LiquidFooter(manage, clear)
        }

        /** 没有通知时系统显示的那句话 */
        fun emptyText(list: ViewGroup): String? {
            val empty = obj(list, "mEmptyShadeView") as? View ?: return null
            if (empty.visibility != View.VISIBLE) return null
            return firstText(empty)
        }

        private fun firstText(view: View): String? {
            if (view is TextView) return view.text?.toString()?.takeIf { it.isNotBlank() }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) firstText(view.getChildAt(i))?.let { return it }
            }
            return null
        }

        /**
         * 上级视图是约束布局时,把视图的四边约束到上级四边(安卓原版约束布局的公开字段);
         * 其它布局用铺满即可。
         */
        fun fillConstraintParent(view: View) {
            val lp = view.layoutParams ?: return
            val names = listOf("topToTop", "bottomToBottom", "startToStart", "endToEnd")
            val targets = names.mapNotNull { n -> runCatching { lp.javaClass.getField(n) }.getOrNull() }
            if (targets.size != names.size) return
            targets.forEach { it.setInt(lp, 0) }
            lp.width = 0
            lp.height = 0
            // 约束布局按这些字段重新算内部标记
            runCatching { lp.javaClass.getMethod("validate").invoke(lp) }
            view.layoutParams = lp
        }
    }

    /** 排查记录用:当前接管状态的一句话 */
    fun describe(): String = "active=$active expanded=$expanded state=$barState top=${ui.topPadding} " +
        "groups=${ui.groups.size} areaBlur=${background?.hasAreaBlur} behindLight=$behindLight"
}
