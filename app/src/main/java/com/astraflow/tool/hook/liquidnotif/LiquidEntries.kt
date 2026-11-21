package com.astraflow.tool.hook.liquidnotif

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import com.astraflow.tool.hook.liquidnotif.model.LiquidAction
import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 把系统整理好的最终通知列表换成卡片要用的数据。普通通知的来源图标读取安卓原版卡片已经绑定的图像。
 *
 * 系统的列表项是安卓原版的三种:单条通知;一组通知(带摘要和子项,这台手机上同一应用的多条合成一组);
 * 以及把几组装在一起的分类(安卓 16 起系统自动归类的那种)。一组就是一个堆叠,分类拆开按里面的组逐个放。
 *
 * [extract] 在系统界面主线程上当场取出通知与来源图标(列表对象随后会被系统清空);
 * [convert] 读头像与内容,放在后台线程做。
 */
internal class LiquidEntries(private val context: Context, private val onIconReady: () -> Unit = {}) {

    /** 一组要显示的通知,按系统给的顺序 */
    class ShadeGroup(val id: String, val items: List<ShadeItem>)

    /** 一条通知和排序要用的几项(重要性、系统卡片高度、分区号) */
    class ShadeItem(val sbn: StatusBarNotification, val importance: Int, val heightPx: Int, val section: Int,
        val icon: Bitmap? = null, val row: WeakReference<View>? = null)

    private val density = context.resources.displayMetrics.density
    private val iconPx = (40 * density).roundToInt()
    private val thumbPx = (40 * density).roundToInt()
    private val actionIconPx = (20 * density).roundToInt()
    private val appAccents = ConcurrentHashMap<String, Int>()
    private val thumbs = LruCache<String, Bitmap>(96)
    private val actionIcons = LruCache<String, Bitmap>(64)
    private val labels = ConcurrentHashMap<String, String>()
    private val methods = ConcurrentHashMap<String, Method>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    // 原生视图只在主线程读取;后台只收到独立的图片。弱引用不延长系统通知卡片的生命周期。
    private val nativeIcons = WeakHashMap<View, Bitmap>()
    private val iconRows = WeakHashMap<ImageView, WeakReference<View>>()
    private var copyingIcon = false

    /** 主线程:按系统给的顺序取出每一组要显示的通知 */
    fun extract(entries: List<*>): List<ShadeGroup> {
        val out = ArrayList<ShadeGroup>(entries.size)
        for (entry in entries) collect(entry, out)
        return out
    }

    private fun collect(entry: Any?, out: MutableList<ShadeGroup>) {
        entry ?: return
        val children = call(entry, "getChildren") as? List<*>
        if (children == null) {
            sbnOf(entry)?.let { out += ShadeGroup("n:${it.key}", listOf(item(entry, it))) }
            return
        }
        if (method(entry, "getSummary") == null) {
            // 几组装在一起的分类:按里面的组逐个放
            for (c in children) collect(c, out)
            return
        }
        // 一组通知:有子项就只放子项,摘要只是个壳;没有子项才放摘要
        val section = sectionOf(entry)
        val items = children.mapNotNull { c -> sbnOf(c)?.let { item(c, it, section) } }
            .ifEmpty { listOfNotNull(sbnOf(call(entry, "getSummary"))?.let { item(entry, it, section) }) }
        if (items.isEmpty()) return
        val key = call(entry, "getKey") as? String ?: items.first().sbn.groupKey
        out += ShadeGroup("g:$key", items)
    }

    private fun item(entry: Any?, sbn: StatusBarNotification, section: Int = -1): ShadeItem {
        val row = call(representative(entry), "getRow") as? View
        return ShadeItem(sbn, importanceOf(entry), heightOf(entry), if (section >= 0) section else sectionOf(entry),
            nativeIcon(row), row?.let { WeakReference(it) })
    }

    /** 安卓原版通知卡片完成绑定或重新绑定后,弃用旧图;通知时间没变也必须更新图标。 */
    fun invalidateIcon(row: View) { nativeIcons.remove(row) }

    /** 图标本身可能在通知更新完成后才换图或着色,只处理已经登记的原生来源图标。 */
    fun onImageUpdated(image: ImageView): Boolean {
        if (copyingIcon) return false
        val row = iconRows[image]?.get() ?: return false
        nativeIcons.remove(row)
        onIconReady()
        return true
    }

    /** 主线程:补上异步绑定完成的图标,不重新读取会被系统清空的通知列表。 */
    fun refreshIcons(groups: List<ShadeGroup>): List<ShadeGroup> {
        var changed = false
        val updated = groups.map { group ->
            var groupChanged = false
            val items = group.items.map { item ->
                val row = item.row?.get() ?: return@map item
                val icon = nativeIcon(row) ?: return@map item
                if (icon === item.icon || item.icon?.sameAs(icon) == true) return@map item
                groupChanged = true
                ShadeItem(item.sbn, item.importance, item.heightPx, item.section, icon, item.row)
            }
            if (!groupChanged) group else { changed = true; ShadeGroup(group.id, items) }
        }
        return if (changed) updated else groups
    }

    /**
     * 只用安卓原版 ExpandableNotificationRow → NotificationViewWrapper.getIcon。
     * 读取系统已经选好、着色和排好的图标,不猜通知种类、资源编号或厂商替换规则。
     * 记录实际图像的绘制结果,不要求图像提供 ConstantState;不继承视图的隐藏状态或动画透明度。
     * 图像的临时绘制尺寸和透明度在 finally 中恢复,不改变原视图、图片回调、着色和状态。
     */
    private fun nativeIcon(row: View?): Bitmap? {
        row ?: return null
        nativeIcons[row]?.let { return it }
        val image = call(call(row, "getNotificationViewWrapper"), "getIcon") as? ImageView ?: return null
        if (iconRows.put(image, WeakReference(row)) == null) {
            image.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) onImageUpdated(image)
            }
        }
        val source = image.drawable ?: return null
        copyingIcon = true
        return runCatching {
            val width = image.width.takeIf { it > 0 } ?: iconPx
            val height = image.height.takeIf { it > 0 } ?: iconPx
            val copy = ImageView(image.context).apply {
                setImageDrawable(recordedDrawable(source))
                background = image.background?.let(::recordedDrawable)
                scaleType = image.scaleType
                imageMatrix = image.imageMatrix
                setPadding(image.paddingLeft, image.paddingTop, image.paddingRight, image.paddingBottom)
                // 系统施加的颜色已在实际 Drawable 上,不再次着色或清除其颜色。
                measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                layout(0, 0, width, height)
            }
            // Picture 支持系统图标中的硬件位图;普通软件 Canvas 无法绘制此类图像。
            val picture = Picture()
            val canvas = picture.beginRecording(iconPx, iconPx)
            try {
                val scale = iconPx.toFloat() / maxOf(width, height)
                canvas.translate((iconPx - width * scale) / 2, (iconPx - height * scale) / 2)
                canvas.scale(scale, scale)
                copy.draw(canvas)
            } finally { picture.endRecording() }
            val bitmap = Bitmap.createBitmap(picture, iconPx, iconPx, Bitmap.Config.ARGB_8888)
            bitmap.also { nativeIcons[row] = it }
        }.onFailure { LiquidLog.w("notification icon copy failed", it) }.getOrNull()
            .also { copyingIcon = false }
    }

    /** 只在本次记录期间读取原图,不把它转交给新 ImageView,原图的回调始终属于系统。 */
    private fun recordedDrawable(source: Drawable): Drawable = object : Drawable() {
        override fun getIntrinsicWidth() = source.intrinsicWidth
        override fun getIntrinsicHeight() = source.intrinsicHeight
        override fun draw(canvas: Canvas) {
            val originalBounds = Rect(source.bounds)
            val originalAlpha = source.alpha
            try {
                source.bounds = bounds
                source.alpha = 255
                source.draw(canvas)
            } finally {
                source.bounds = originalBounds
                source.alpha = originalAlpha
            }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun sbnOf(entry: Any?): StatusBarNotification? = call(entry, "getSbn") as? StatusBarNotification

    /** 这一条自己、或这一组的代表条目(安卓原版 ListEntry.getRepresentativeEntry) */
    private fun representative(entry: Any?): Any? = entry?.let { call(it, "getRepresentativeEntry") ?: it }

    /** 通知重要性(安卓原版 NotificationListenerService.Ranking;取不到按 0) */
    private fun importanceOf(entry: Any?): Int {
        val rep = representative(entry) ?: return 0
        (call(rep, "getImportance") as? Int)?.let { return it }
        return (call(call(rep, "getRanking"), "getImportance") as? Int) ?: 0
    }

    /** 系统卡片的高度(卡片还没造出来时取不到,按 0) */
    private fun heightOf(entry: Any?): Int {
        val row = call(representative(entry) ?: return 0, "getRow") ?: return 0
        return (call(row, "getIntrinsicHeight") as? Int) ?: 0
    }

    /** 列表分区号(安卓原版 PipelineEntry.getSectionIndex);取不到按 0 */
    private fun sectionOf(entry: Any?): Int = (call(entry, "getSectionIndex") as? Int) ?: 0

    private fun method(target: Any, name: String): Method? {
        val id = "${target.javaClass.name}#$name"
        methods[id]?.let { return it }
        if (id in missing) return null
        val m = runCatching { target.javaClass.getMethod(name) }.getOrNull()
        if (m == null) missing += id else methods[id] = m
        return m
    }

    private fun call(target: Any?, name: String): Any? {
        target ?: return null
        val m = method(target, name) ?: return null
        return runCatching { m.invoke(target) }.getOrNull()
    }

    /** 后台线程:换成卡片数据 */
    fun convert(groups: List<ShadeGroup>): List<LiquidGroup> = groups.map { g ->
        val items = g.items.map { toLiquid(it.sbn, it) }
        LiquidGroup(id = g.id, appLabel = items.first().appLabel, items = items)
    }

    private fun toLiquid(sbn: StatusBarNotification, meta: ShadeItem = ShadeItem(sbn, 0, 0, 0)): LiquidNotification {
        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        val (title, text) = content(extras)
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val progress = when {
            indeterminate -> 0f
            max > 0 -> (extras.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max).coerceIn(0f, 1f)
            else -> null
        }
        val actions = n.actions.orEmpty().mapIndexedNotNull { i, a -> action(sbn, a, i) }
        val label = label(sbn.packageName)
        val custom = customView(n)
        val icon = meta.icon
        @Suppress("DEPRECATION") val userId = sbn.userId
        return LiquidNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            userId = userId,
            channelId = n.channelId,
            appLabel = label,
            appIcon = icon,
            title = title.ifEmpty { label },
            text = text,
            postTime = if (n.`when` > 0L) n.`when` else sbn.postTime,
            showTime = extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true),
            thumbnail = thumbnail(sbn, extras),
            progress = progress,
            progressIndeterminate = indeterminate,
            actions = actions,
            clearable = sbn.isClearable,
            contentIntent = n.contentIntent,
            accent = accent(sbn, icon),
            custom = custom,
            version = sbn.postTime,
            importance = meta.importance,
            cardHeightPx = meta.heightPx,
            section = meta.section,
        )
    }

    private fun action(sbn: StatusBarNotification, a: Notification.Action, index: Int): LiquidAction? {
        val label = a.title?.toString()?.trim().orEmpty()
        val input = a.remoteInputs?.firstOrNull { it.allowFreeFormInput }
        val icon = if (label.isEmpty()) actionIcon(sbn, a.getIcon(), index) else null
        if (label.isEmpty() && icon == null) return null
        return LiquidAction(
            title = label,
            icon = icon,
            intent = a.actionIntent,
            replyHint = input?.let { it.label?.toString()?.trim()?.ifEmpty { null } ?: label.ifEmpty { "回复" } },
            index = index,
        )
    }

    /**
     * 应用自己设计的样式(应用自己给的排版,不是系统的标准样式)。系统的标准样式也放在这两个位置上,
     * 但它们的排版来自系统自己的资源,由此区分。展开后的样子优先。
     */
    private fun customView(n: Notification): RemoteViews? {
        fun own(rv: RemoteViews?): RemoteViews? = rv?.takeIf { (it.layoutId ushr 24) != SYSTEM_RESOURCE_PACKAGE }
        return own(n.bigContentView) ?: own(n.contentView)
    }

    /** 标题和正文:消息类取最后一条消息;长文本取全文;列表类逐行拼起来;其余取标题和正文 */
    private fun content(extras: Bundle): Pair<String, String> {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        if (!messages.isNullOrEmpty()) {
            val last = messages.last() as? Bundle
            val text = last?.getCharSequence("text")?.toString().orEmpty()
            val sender = last?.getCharSequence("sender")?.toString()
            val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            return if (!conversation.isNullOrEmpty()) {
                conversation to (if (!sender.isNullOrEmpty()) "$sender：$text" else text)
            } else {
                (sender ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()) to text
            }
        }
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (text.isEmpty()) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!lines.isNullOrEmpty()) return title to lines.joinToString("\n")
        }
        return title to text
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun isSystem(pkg: String) = pkg == "android" || pkg == "com.android.systemui"

    /** 没有字的按钮画图标(按通知颜色着色,没有颜色时按白色;卡片上再按字色着色) */
    private fun actionIcon(sbn: StatusBarNotification, icon: Icon?, index: Int): Bitmap? {
        icon ?: return null
        val cacheKey = "${sbn.key}:${sbn.postTime}:$index"
        actionIcons.get(cacheKey)?.let { return it }
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return null
        return runCatching { draw(drawable, actionIconPx, crop = false) }.getOrNull()?.also { actionIcons.put(cacheKey, it) }
    }

    /** 通知自带的大图标(头像、封面),没有时用大图通知的图,画成卡片右边的小图 */
    private fun thumbnail(sbn: StatusBarNotification, extras: Bundle): Bitmap? {
        val cacheKey = "${sbn.key}:${sbn.postTime}"
        thumbs.get(cacheKey)?.let { return it }
        val drawable = runCatching { sbn.notification.getLargeIcon()?.loadDrawable(context) }.getOrNull()
            ?: runCatching { extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)?.loadDrawable(context) }.getOrNull()
            ?: runCatching {
                extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
                    ?.let { android.graphics.drawable.BitmapDrawable(context.resources, it) }
            }.getOrNull()
            ?: return null
        return runCatching { draw(drawable, thumbPx) }.getOrNull()?.also { thumbs.put(cacheKey, it) }
    }

    /** 主色:通知自己给了颜色就用它,否则取应用图标里鲜艳部分的平均色 */
    private fun accent(sbn: StatusBarNotification, icon: Bitmap?): Int? {
        val c = sbn.notification.color
        if (c != Notification.COLOR_DEFAULT && c != 0) return c or 0xFF000000.toInt()
        if (icon == null || isSystem(sbn.packageName)) return null
        appAccents[sbn.packageName]?.let { return it.takeIf { v -> v != 0 } }
        val color = iconColor(icon)
        appAccents[sbn.packageName] = color ?: 0
        return color
    }

    private fun iconColor(icon: Bitmap): Int? {
        val small = Bitmap.createScaledBitmap(icon, 16, 16, true)
        var r = 0f
        var g = 0f
        var b = 0f
        var weight = 0f
        val hsv = FloatArray(3)
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val p = small.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                Color.colorToHSV(p, hsv)
                val w = hsv[1] * hsv[2]
                if (w < 0.15f) continue
                r += Color.red(p) * w
                g += Color.green(p) * w
                b += Color.blue(p) * w
                weight += w
            }
        }
        if (small !== icon) small.recycle()
        if (weight <= 0f) return null
        return Color.rgb((r / weight).roundToInt(), (g / weight).roundToInt(), (b / weight).roundToInt())
    }

    /** 画成指定大小;[crop] 为真时按正方形居中裁切,否则按比例放进方框 */
    private fun draw(drawable: Drawable, size: Int, crop: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w > 0 && h > 0 && w != h) {
            val scale = if (crop) size.toFloat() / minOf(w, h) else size.toFloat() / maxOf(w, h)
            val dw = (w * scale).roundToInt()
            val dh = (h * scale).roundToInt()
            val left = (size - dw) / 2
            val top = (size - dh) / 2
            drawable.setBounds(left, top, left + dw, top + dh)
        } else {
            drawable.setBounds(0, 0, size, size)
        }
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        /** 安卓系统自己的资源都在这个编号下(资源编号的最高一段) */
        const val SYSTEM_RESOURCE_PACKAGE = 0x01
    }
}
