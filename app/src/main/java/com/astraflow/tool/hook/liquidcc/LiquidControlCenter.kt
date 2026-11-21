package com.astraflow.tool.hook.liquidcc

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import com.astraflow.tool.config.ConfigKeys
import com.astraflow.tool.config.LiquidControlCenterDefaults
import com.astraflow.tool.config.LiquidNotificationDefaults
import com.astraflow.frostshade.FrostPreferences
import com.astraflow.tool.hook.liquidnotif.LiquidLog
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * 液态玻璃控制中心(OPPO 专属,方案 docs/design/liquid-control-center.md):在 OPPO 自己的控制中心上
 * 原地把开关、按钮、音乐卡片、设备卡片和长按弹出的面板的底子换成液态玻璃,OPPO 的排列、分页、编辑模式、手势全部照旧。
 *
 * 只按资源名找部件(ColorOS 13.1 到 16.1 都有 `qs_tile_bg`),不用混淆名、合成名;每一种部件单独找,
 * 找不到的那一部分保持 OPPO 原样(用户 2026-09-26「你得考虑到低版本的 OPPO，不要用过于脆弱的逻辑」)。
 * 由液态玻璃通知的背景每一帧调用 [sync](背景画之前),OPPO 重新设它自己的底子后,下一帧画之前又换回玻璃。
 * 全部在系统界面主线程上运行。
 */
internal object LiquidControlCenter {

    /** 装着通知页和控制中心的那一层,和控制中心的内容区 */
    private const val PAGER = "panel_view_pager"
    private const val PAGE_CONTENT = "qs_container_area_layout"
    private const val PAGE_CLASS = "OplusQSRootView"

    /** 长按开关、音乐卡片弹出的面板放在窗口根视图下的这一层(不在控制中心那一页里) */
    private const val DIALOGS = "view_dialog_container"

    /** OPPO 开关的默认圆角(资源名);开关自己的圆角读不到时用 */
    private const val DEFAULT_RADIUS = "qs_tile_corner_radius_default"

    /** 开关底子(各种大小,读开关自己的圆角) */
    private const val TILE_BG = "qs_tile_bg"

    /** 宽开关里显示开关状态的圆形图标底 */
    private const val DISC = "oplus_qs_tile_icon_bg"

    /** 开关的图标(读它的着色决定开着时填什么颜色) */
    private const val GLYPH = "qs_icon"

    /** 响铃三段开关的选中滑块,和滑块下面的三个图标 */
    private const val INDICATOR = "indicator_view"
    private val INDICATOR_ICONS = listOf("left_icon_view", "middle_icon_view", "right_icon_view")

    /**
     * 圆形或胶囊形的底子:宽开关的圆形图标底、响铃三段开关的选中滑块、编辑等按钮。
     * 隐私指示(资源名 privacy_chip)不换:它的绿色、橙色就是「麦克风或相机正在用」的提醒。
     */
    private val ROUND_BG = setOf(
        DISC,
        INDICATOR,
        "qs_edit_button",
        "quicksettings_settings_button_icon",
        "qs_more_button_icon",
        "add_tile_view",
        "save_view",
    )

    /** 音乐卡片:外层放玻璃,里层原来的底子藏起(外层会裁掉超出范围的内容,阴影要画在外层) */
    private const val MEDIA_PANEL = "oplus_qs_media_panel"
    private const val MEDIA_BG = "bg"

    /**
     * 设备卡片(耳机、手表这类已连接或附近的设备,和「添加设备」的入口):底子在卡片里面这一层;
     * 值是 OPPO 给这种卡片定的圆角那一档(用户 2026-09-26「耳机、设备等……你需要考虑到部分图标成为拓展形状后的呈现」)
     */
    private val CARDS = mapOf(
        "square_device_constraintlayout" to CardShape.SQUARE,
        "square_entrance_constraintLayout" to CardShape.SQUARE,
        "rectangleCoLayout" to CardShape.ROUND_RECT,
        "rectangle_entrance_constraintLayout" to CardShape.ROUND_RECT,
        "no_device_constraintLayout" to CardShape.CIRCLE,
    )

    /**
     * 弹出的面板:长按开关弹出的详情面板、长按音乐卡片弹出的音乐面板(用户 2026-09-26「部分长按展开的项目，没有成为液态」)。
     * 面板弹出时 OPPO 会把后面的控制中心淡出,玻璃后面只剩背景
     */
    private val PANELS = setOf("detail_container", "oplus_media_carousel_layout")
    private const val PANEL_RADIUS = "qs_detail_bg_corner_radius"

    /** 方形设备卡片里的设备图标(卡片没有文字,按它判断内容深浅) */
    private const val DEVICE_ICON = "device_image"

    /** 设备卡片的圆角:国内版和一加海外版各用一组(资源名,和 OPPO 自己的取法一样) */
    private enum class CardShape(val domestic: String, val export: String) {
        SQUARE("separate_card_corner_radius_square", "oos_separate_card_corner_radius_square"),
        ROUND_RECT("separate_card_corner_radius_round_rect", "oos_separate_card_corner_radius_round_rect"),
        CIRCLE("separate_card_corner_radius_circle", "oos_separate_card_corner_radius_semicircle"),
    }

    private enum class Kind { TILE, ROUND, MEDIA, CARD, PANEL }

    private class Target(view: View, val kind: Kind) {
        val ref = WeakReference(view)

        /**
         * 宽开关(放大过的):整块底子始终是关着的样子,开关状态只显示在圆形图标底上(OPPO 原来就是这样);
         * 整块底子也按开着填色,圆形图标底就和它融在一起(用户 2026-09-26「圆形图标会和底部圆角长矩形的颜色融合」)
         */
        var hasDisc = false

        /** 这个开关的图标 */
        var glyph: WeakReference<View>? = null

        /** 响铃三段开关滑块下面的三个图标(滑块停在哪个上面,就读哪个的着色) */
        var icons: List<WeakReference<View>> = emptyList()

        /** 设备卡片的圆角那一档 */
        var card: CardShape? = null

        /** 卡片和面板里用来判断内容深浅的文字或图标 */
        var content: WeakReference<View>? = null
    }

    // ── 功能开关(每帧都会问,半秒内复用上一次的结果) ──

    @Volatile
    private var enabledCache = false

    @Volatile
    private var enabledStamp = -10_000L

    fun enabled(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - enabledStamp > 500L) {
            enabledCache = runCatching {
                FrostPreferences.getBoolean(ConfigKeys.LIQUID_CONTROL_CENTER_ENABLED, true)
            }.getOrDefault(false)
            enabledStamp = now
        }
        return enabledCache
    }

    /** 浅色模式下背景在控制中心那一页额外压暗(0 到 1),下拉面板打开时读 */
    var dimLight = LiquidControlCenterDefaults.DIM_LIGHT.default / 100f
        private set

    /** 控制中心那一页和弹出面板那一层没找到时,隔多久再找 */
    private const val SEARCH_INTERVAL_MS = 1_000L

    private var pageRef: WeakReference<ViewGroup>? = null
    private var dialogsRef: WeakReference<ViewGroup>? = null
    private var pageSearchAt = -10_000L
    private var dialogsSearchAt = -10_000L
    private var pagerId = 0
    private var contentId = 0
    private var dialogsId = 0
    private val targets = ArrayList<Target>()
    private var dirty = true
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { dirty = true }
    private var listenedTree: WeakReference<ViewTreeObserver>? = null
    private val names = WeakHashMap<Resources, SparseArray<String>>()
    private val loc = IntArray(2)

    /** 换上玻璃之前原来的底子(还原用);每个视图一块我们的玻璃 */
    private val originals = WeakHashMap<View, Drawable?>()
    private val glass = WeakHashMap<View, CcGlassDrawable>()
    private val loggedMissing = HashSet<String>()

    init {
        GlassTilt.onMoved = { CcGlassEnv.updateHighlight() }
    }

    /** 下拉面板打开时(背景开始):读玻璃的几项设置(与液态玻璃堆叠通知共用)和自己的额外压暗 */
    fun onShadeOpened(context: Context) {
        val d = LiquidNotificationDefaults
        fun int(item: LiquidNotificationDefaults.IntItem) = item.clamp(FrostPreferences.getInt(item.key, item.default))
        fun bool(item: LiquidNotificationDefaults.BoolItem) = FrostPreferences.getBoolean(item.key, item.default)
        CcGlassEnv.surfaceAlpha = int(d.SURFACE_DARK) / 100f
        CcGlassEnv.shadowAlpha = (int(d.SHADOW) / 100f * 2f).coerceIn(0f, 1f)
        CcGlassEnv.highlightAlpha = int(d.HIGHLIGHT) / 100f
        CcGlassEnv.tilt = bool(d.HIGHLIGHT_TILT)
        CcGlassEnv.pressGlow = bool(d.PRESS_GLOW)
        // 额外压暗是单独的开关(用户 2026-09-26「可以做成独立的开关」),关着时两页背景完全一致
        dimLight = if (bool(LiquidControlCenterDefaults.DIM_LIGHT_ENABLED)) int(LiquidControlCenterDefaults.DIM_LIGHT) / 100f else 0f
        // 圆角按像素记,换了显示大小后要重读
        dimens.clear()
        CcGlassEnv.prepare(context)
        CcGlassEnv.updateHighlight()
    }

    /** 下拉面板收起或锁屏时归还系统背景。 */
    fun onShadeClosed() {
        GlassTilt.stop()
        restoreAll()
    }

    /**
     * 每一帧(液态玻璃通知的背景画之前):功能开着时找控制中心和弹出面板那一层,把各部件的底子换成玻璃;
     * 关着时还原。[root] 是通知中心窗口的根视图。
     */
    fun sync(root: ViewGroup) {
        if (!enabled()) {
            restoreAll()
            GlassTilt.stop()
            return
        }
        val page = page(root) ?: return
        val dialogs = dialogs(root)
        if (dirty) scan(page, dialogs)
        for (t in targets) apply(t)
        val shown = pageShown(page, dialogs)
        if (shown > 0.01f && CcGlassEnv.tilt) GlassTilt.start(page.context) else GlassTilt.stop()
    }

    /** 控制中心在屏幕上显示的程度(0 到 1):看它的显隐、透明度和左右切换时的平移 */
    fun visibleFraction(root: ViewGroup): Float {
        val page = page(root) ?: return 0f
        return pageShown(page, dialogs(root))
    }

    private fun pageShown(page: View, dialogs: ViewGroup?): Float {
        if (page.visibility != View.VISIBLE) return 0f
        val w = page.width
        if (w <= 0) return 0f
        val slid = (1f - abs(page.translationX) / w).coerceIn(0f, 1f)
        // 弹出面板时 OPPO 把控制中心淡出(面板后面只剩背景),这时仍算在控制中心这一页,额外压暗和亮边转动都照旧
        val faded = if (dialogShowing(dialogs)) 1f else page.alpha * page.transitionAlpha
        return (faded * slid).coerceIn(0f, 1f)
    }

    private fun dialogShowing(dialogs: ViewGroup?): Boolean {
        if (dialogs == null || dialogs.visibility != View.VISIBLE) return false
        for (i in 0 until dialogs.childCount) {
            if (dialogs.getChildAt(i).visibility == View.VISIBLE) return true
        }
        return false
    }

    /**
     * 控制中心这一页:装着通知页和控制中心的那一层(资源名 panel_view_pager)里,装着控制中心内容区
     * (资源名 qs_container_area_layout)的那一个子视图;内容区换了名字时认 OPPO 控制中心这一层的类名
     * (OplusQSRootView,ColorOS 15 起一直是它)。ColorOS 15 起通知与控制中心分开的样式就是这样;
     * 找不到时(合在一起的样式)什么都不改。
     */
    private fun page(root: ViewGroup): ViewGroup? {
        pageRef?.get()?.let { if (it.isAttachedToWindow && it.rootView === root) return it }
        pageRef = null
        // 没找到时(合在一起的样式、较早的系统)每秒最多再找一次,不每帧翻一遍整个窗口
        val now = SystemClock.uptimeMillis()
        if (now - pageSearchAt < SEARCH_INTERVAL_MS) return null
        pageSearchAt = now
        val res = root.resources
        if (pagerId == 0) pagerId = res.getIdentifier(PAGER, "id", CcGlassEnv.SYSTEM_UI)
        if (contentId == 0) contentId = res.getIdentifier(PAGE_CONTENT, "id", CcGlassEnv.SYSTEM_UI)
        if (pagerId == 0) return missing("pager id")
        val pager = root.findViewById<ViewGroup>(pagerId) ?: return missing("pager")
        for (i in 0 until pager.childCount) {
            val child = pager.getChildAt(i) as? ViewGroup ?: continue
            val isPage = (contentId != 0 && child.findViewById<View>(contentId) != null) ||
                child.javaClass.simpleName == PAGE_CLASS
            if (isPage) {
                pageRef = WeakReference(child)
                listen(child)
                dirty = true
                LiquidLog.i("control center page found: ${child.javaClass.simpleName}")
                return child
            }
        }
        return missing("control center page")
    }

    /** 弹出面板那一层(窗口根视图下,资源名 view_dialog_container);没有时(较早的系统)面板保持原样 */
    private fun dialogs(root: ViewGroup): ViewGroup? {
        dialogsRef?.get()?.let { if (it.isAttachedToWindow && it.rootView === root) return it }
        dialogsRef = null
        val now = SystemClock.uptimeMillis()
        if (now - dialogsSearchAt < SEARCH_INTERVAL_MS) return null
        dialogsSearchAt = now
        if (dialogsId == 0) dialogsId = root.resources.getIdentifier(DIALOGS, "id", CcGlassEnv.SYSTEM_UI)
        if (dialogsId == 0) return missing("dialog container id")
        val found = root.findViewById<View>(dialogsId) as? ViewGroup ?: return missing("dialog container")
        dialogsRef = WeakReference(found)
        dirty = true
        return found
    }

    /** 视图有增减、重新排版时重新找一遍部件(弹出面板和控制中心在同一个窗口,一起算) */
    private fun listen(page: View) {
        val tree = page.viewTreeObserver
        if (listenedTree?.get() === tree && tree.isAlive) return
        runCatching { listenedTree?.get()?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener) }
        tree.addOnGlobalLayoutListener(layoutListener)
        listenedTree = WeakReference(tree)
    }

    private fun scan(page: ViewGroup, dialogs: ViewGroup?) {
        dirty = false
        targets.clear()
        collect(page)
        if (dialogs != null) collect(dialogs)
        for (t in targets) resolve(t)
        if (LiquidLog.on) logUnhandled(page, dialogs)
    }

    private fun collect(v: View) {
        val name = nameOf(v)
        when {
            name == TILE_BG -> targets += Target(v, Kind.TILE)
            name in ROUND_BG -> targets += Target(v, Kind.ROUND)
            name == MEDIA_PANEL -> targets += Target(v, Kind.MEDIA)
            name in PANELS -> targets += Target(v, Kind.PANEL)
            else -> CARDS[name]?.let { shape -> targets += Target(v, Kind.CARD).also { it.card = shape } }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
    }

    /** 找每个部件读颜色用的图标或文字 */
    private fun resolve(t: Target) {
        val v = t.ref.get() ?: return
        when (t.kind) {
            Kind.TILE -> {
                // 宽开关:这个开关里有显示状态的圆形图标底(OPPO 在开关缩成最小时会清掉它,那时没有)
                val tile = tileRoot(v) ?: return
                t.hasDisc = findByName(tile, DISC)?.background != null
                if (!t.hasDisc) t.glyph = findByName(tile, GLYPH)?.let { WeakReference(it) }
            }
            Kind.ROUND -> {
                val parent = v.parent as? ViewGroup ?: return
                when (nameOf(v)) {
                    DISC -> t.glyph = findByName(parent, GLYPH)?.let { WeakReference(it) }
                    INDICATOR -> t.icons = INDICATOR_ICONS.mapNotNull { findByName(parent, it) }.map { WeakReference(it) }
                }
            }
            Kind.CARD, Kind.PANEL -> t.content = firstContent(v)?.let { WeakReference(it) }
            Kind.MEDIA -> Unit
        }
    }

    private var lastUnhandledLog = 0L

    /**
     * 排查用(手机上打开排查开关才记):控制中心和弹出面板里有底子、但这里没有处理的视图,记下名字、类型、底子类型和大小,
     * 用来找还没换成玻璃的部件。每 3 秒最多记一次,每次最多 60 条;不记内容。
     */
    private fun logUnhandled(page: ViewGroup, dialogs: ViewGroup?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUnhandledLog < 3_000L) return
        lastUnhandledLog = now
        val handled = targets.mapNotNull { it.ref.get() }.toHashSet()
        var count = 0
        fun walk(v: View, depth: Int) {
            if (count >= 60) return
            val bg = v.background
            if (bg != null && v !in handled && bg !is CcGlassDrawable && v.width > 0 && v.height > 0) {
                count++
                LiquidLog.i(
                    "cc unhandled: name=${nameOf(v).ifEmpty { "-" }} view=${v.javaClass.simpleName} " +
                        "bg=${bg.javaClass.simpleName} ${v.width}x${v.height} depth=$depth shown=${v.isShown}",
                )
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(page, 0)
        if (dialogs != null) walk(dialogs, 0)
        // 窗口根视图里控制中心以外的直接子视图(还有别的弹出层时从这里找)
        val root = page.rootView as? ViewGroup ?: return
        for (i in 0 until root.childCount) {
            val c = root.getChildAt(i)
            if (c.visibility == View.VISIBLE) {
                LiquidLog.i("cc root child: name=${nameOf(c).ifEmpty { "-" }} view=${c.javaClass.simpleName} bg=${c.background?.javaClass?.simpleName} ${c.width}x${c.height}")
            }
        }
    }

    /** 一个开关的最外层:往上找有「当前圆角」的那个视图(OPPO 可调大小的开关都是);找不到为空 */
    private fun tileRoot(v: View): View? {
        var cur = v.parent as? View
        var depth = 0
        while (cur != null && depth < 4) {
            if (radiusMethod(cur.javaClass) != null) return cur
            cur = cur.parent as? View
            depth++
        }
        return null
    }

    private fun findByName(root: View, name: String): View? {
        if (nameOf(root) == name) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findByName(root.getChildAt(i), name)?.let { return it }
        }
        return null
    }

    /** 卡片和面板里第一个显示着的文字;没有文字时(方形设备卡片)看设备图标,再没有看第一个有图的图标 */
    private fun firstContent(root: View): View? =
        firstOf(root) { it is TextView && it.visibility == View.VISIBLE && !it.text.isNullOrEmpty() }
            ?: findByName(root, DEVICE_ICON)?.takeIf { it is ImageView && it.drawable != null }
            ?: firstOf(root) { it is ImageView && it.visibility == View.VISIBLE && it.drawable != null }

    private fun firstOf(v: View, test: (View) -> Boolean): View? {
        if (test(v)) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) firstOf(v.getChildAt(i), test)?.let { return it }
        }
        return null
    }

    /**
     * 资源名(没有名字的视图,例如运行时生成编号的,记为空)。按视图自己的资源查:
     * 设备卡片这类插件做的部件,编号属于插件自己
     */
    private fun nameOf(v: View): String {
        val id = v.id
        if (id == View.NO_ID) return ""
        val res = v.resources ?: return ""
        val cache = names.getOrPut(res) { SparseArray() }
        cache.get(id)?.let { return it }
        val name = runCatching { res.getResourceEntryName(id) }.getOrDefault("")
        cache.put(id, name)
        return name
    }

    private fun apply(t: Target) {
        val v = t.ref.get() ?: return
        when (t.kind) {
            Kind.TILE, Kind.ROUND -> {
                val g = swap(v, t) ?: return
                g.showsState = !t.hasDisc
                g.glyphView = if (t.icons.isNotEmpty()) nearest(v, t.icons) else t.glyph
                g.checkGlyph()
            }
            Kind.MEDIA -> {
                // 外层原来可能没有底子(底子在里层),玻璃照样放在外层
                if (v.background !is CcGlassDrawable) {
                    val g = glassFor(v, t) ?: return
                    originals[v] = v.background
                    v.background = g
                }
                val inner = (v as? ViewGroup)?.let { findChildByName(it, MEDIA_BG) } ?: return
                val innerBg = inner.background ?: return
                if (innerBg is Hidden) return
                originals[inner] = innerBg
                inner.background = Hidden()
            }
            Kind.CARD, Kind.PANEL -> {
                val g = swap(v, t) ?: return
                // 内容是深色(例如已连接的设备:OPPO 用浅色底配黑色内容)就用浅色玻璃,内容是白色就用透明玻璃
                g.solidLight = contentDark(t.content?.get()) == true
            }
        }
    }

    /** 把视图的底子换成玻璃,返回这块玻璃;原来没有底子的不补,圆角读不到的保持原样(都返回空) */
    private fun swap(v: View, t: Target): CcGlassDrawable? {
        val bg = v.background ?: return null
        if (bg is CcGlassDrawable) return bg
        val g = glassFor(v, t) ?: return null
        originals[v] = bg
        v.background = g
        return g
    }

    /** 响铃三段开关:滑块此刻停在哪个图标上(按左右位置最近的那个) */
    private fun nearest(anchor: View, icons: List<WeakReference<View>>): WeakReference<View>? {
        anchor.getLocationInWindow(loc)
        val center = loc[0] + anchor.width / 2f
        var best: WeakReference<View>? = null
        var bestDistance = Float.MAX_VALUE
        for (ref in icons) {
            val icon = ref.get() ?: continue
            icon.getLocationInWindow(loc)
            val d = abs(loc[0] + icon.width / 2f - center)
            if (d < bestDistance) {
                bestDistance = d
                best = ref
            }
        }
        return best
    }

    // ── 内容深浅 ──

    /** 亮度低于这个算深色内容 */
    private const val DARK_CONTENT = 0.5f

    /** 图标图片的亮度(按图片本身算一次,图片换了再算);算不出时为负 */
    private val imageLuminance = WeakHashMap<Drawable, Float>()

    /** 内容是不是深色:文字看字的颜色,图标看着色,没有着色时看图片本身;判断不了为空 */
    private fun contentDark(v: View?): Boolean? = when (v) {
        is TextView -> luminance(v.currentTextColor) < DARK_CONTENT
        is ImageView -> {
            val tint = v.imageTintList
            val lum = if (tint != null) luminance(tint.getColorForState(v.drawableState, tint.defaultColor)) else v.drawable?.let(::drawableLuminance)
            lum?.let { it < DARK_CONTENT }
        }
        else -> null
    }

    private fun luminance(color: Int): Float =
        0.2126f * Color.red(color) / 255f + 0.7152f * Color.green(color) / 255f + 0.0722f * Color.blue(color) / 255f

    /** 把图片的一份副本画到 16×16 的小图上,按不透明程度加权算平均亮度(不动 OPPO 正在显示的那一份) */
    private fun drawableLuminance(d: Drawable): Float? {
        imageLuminance[d]?.let { return it.takeIf { v -> v >= 0f } }
        val result = runCatching {
            val copy = d.constantState?.newDrawable()?.mutate() ?: return@runCatching -1f
            val size = 16
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, size, size)
            copy.draw(Canvas(bitmap))
            var weight = 0f
            var sum = 0f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val p = bitmap.getPixel(x, y)
                    val a = Color.alpha(p) / 255f
                    if (a < 0.05f) continue
                    weight += a
                    sum += a * luminance(p)
                }
            }
            bitmap.recycle()
            if (weight < 1f) -1f else sum / weight
        }.getOrDefault(-1f)
        imageLuminance[d] = result
        return result.takeIf { it >= 0f }
    }

    private fun findChildByName(group: ViewGroup, name: String): View? {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            if (nameOf(c) == name) return c
        }
        return null
    }

    /** 每个视图一块玻璃;圆角读不到的(较早的系统没有这个资源)返回空,这个部件保持原样 */
    private fun glassFor(v: View, t: Target): CcGlassDrawable? {
        glass[v]?.let { return it }
        val density = v.resources.displayMetrics.density
        val made = when (t.kind) {
            Kind.TILE, Kind.MEDIA -> CcGlassDrawable(density, radiusOf = { cornerRadius(v) })
            Kind.ROUND -> CcGlassDrawable(density).also { it.alwaysOn = nameOf(v) == INDICATOR }
            Kind.CARD -> {
                val shape = t.card ?: return null
                val r = dimen(v.resources, if (oneplusExport) shape.export else shape.domestic) ?: return null
                CcGlassDrawable(density).also { it.radiusOverride = r }
            }
            Kind.PANEL -> {
                val r = dimen(v.resources, PANEL_RADIUS) ?: return null
                CcGlassDrawable(density).also { it.radiusOverride = r }
            }
        }
        // 上一层会裁掉超出部件范围的内容时,外围柔影画不全,只剩被裁成直边的一块深色,这一块不画柔影
        if ((v.parent as? ViewGroup)?.clipChildren == true) made.outerShadow = false
        made.host = WeakReference(v)
        glass[v] = made
        return made
    }

    /**
     * 开关或音乐卡片的圆角:从这一层往上找有「当前圆角」的那个视图(OPPO 可调大小的开关都有,
     * 大小变化的变形动画里也跟着变);找不到时用 OPPO 开关的默认圆角(资源名),再没有就按短边的一半
     */
    private fun cornerRadius(v: View): Float? {
        var cur: View? = v
        var depth = 0
        while (cur != null && depth < 4) {
            radiusMethod(cur.javaClass)?.let { m ->
                runCatching { (m.invoke(cur) as? Float)?.takeIf { it > 0f } }.getOrNull()?.let { return it }
            }
            cur = cur.parent as? View
            depth++
        }
        return dimen(v.resources, DEFAULT_RADIUS)
    }

    /** 按资源名读的圆角(像素);没有这个资源记为负,不再反复查 */
    private val dimens = HashMap<String, Float>()

    private fun dimen(res: Resources, name: String): Float? {
        val px = dimens.getOrPut(name) {
            val id = res.getIdentifier(name, "dimen", CcGlassEnv.SYSTEM_UI)
            (if (id != 0) runCatching { res.getDimension(id) }.getOrNull() else null) ?: -1f
        }
        if (px < 0f) {
            missing("dimen $name")
            return null
        }
        return px
    }

    /** 一加海外版:设备卡片用另一组圆角(OPPO 按这两个系统属性判断,这里照做) */
    private val oneplusExport: Boolean by lazy {
        prop("ro.oplus.image.system_ext.brand").contains("oneplus", ignoreCase = true) &&
            !prop("ro.oplus.image.my_stock.type").contains("domestic", ignoreCase = true)
    }

    private fun prop(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as? String
    }.getOrNull().orEmpty()

    private val radiusMethods = HashMap<Class<*>, Method?>()

    private fun radiusMethod(cls: Class<*>): Method? = radiusMethods.getOrPut(cls) {
        runCatching { cls.getMethod("getViewRadius").takeIf { it.returnType == Float::class.javaPrimitiveType } }.getOrNull()
    }

    /** 还原原来的底子(只还仍是我们玻璃的那些;OPPO 已经换过的不动) */
    fun restoreAll() {
        if (originals.isEmpty()) return
        for ((view, original) in originals.entries.toList()) {
            val current = view.background
            if (current is CcGlassDrawable || current is Hidden) view.background = original
        }
        originals.clear()
        glass.clear()
        targets.clear()
        dirty = true
        LiquidLog.i("control center restored")
    }

    private fun missing(what: String): ViewGroup? {
        if (loggedMissing.add(what)) LiquidLog.i("control center: $what not found, keeping the system look")
        return null
    }

    /** 藏起来的原底子的占位:透明,没有大小和留白 */
    private class Hidden : ColorDrawable(0)
}
