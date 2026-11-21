package com.astraflow.tool.hook.liquidnotif

import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.astraflow.tool.hook.core.HookHelper
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

/**
 * 液态玻璃通知(方案见 docs/design/liquid-notification.md):系统原生的通知中心保持原样
 * (下拉、收起、顶部时间和按钮),在它的通知区域里把原来的通知藏起来,所有通知由星流自己画。
 *
 * 只挂安卓原版系统界面里各家都有的部分,不引用任何厂商加的类:
 * - 取列表:通知列表「画完后」的监听,拿到系统已过滤、排好序、分好组的最终列表;
 * - 通知列表(NotificationStackScrollLayout):展开与否、解锁与否、触摸、滚动位置、「下面是不是空白」、
 *   「正在全部清除」这几处。接管时系统列表不接收触摸,系统问滚动位置和空白时按我们的列表回答,
 *   系统面板的收起、上推等手势因此照旧;
 * - 通知中心窗口(NotificationShadeWindowControllerImpl):建好时记下它,直接回复时请它让窗口弹出键盘;
 * - 整个下拉面板拉开了多少(NotificationShadeDepthController 收到的安卓原版拉开进度):背景跟着它走,
 *   通知页和控制中心共用同一层背景;
 * - 安卓发给系统界面的状态栏外观(CommandQueue.onSystemBarAttributesChanged):后面的应用要不要深色状态栏图标,
 *   据此知道后面是亮还是暗(不截图)。
 * 锁屏时一律交还系统。
 */
object LiquidNotificationHook {

    private const val PKG = "com.android.systemui"
    private const val STACK_COORDINATOR = "$PKG.statusbar.notification.collection.coordinator.StackCoordinator"
    private const val RENDER_LIST_LISTENER = "$PKG.statusbar.notification.collection.listbuilder.OnAfterRenderListListener"
    private const val LIST_CONTAINER = "$PKG.statusbar.notification.stack.NotificationStackScrollLayout"
    private const val SHADE_WINDOW = "$PKG.shade.NotificationShadeWindowControllerImpl"
    private const val DEPTH_CONTROLLER = "$PKG.statusbar.NotificationShadeDepthController"
    private const val COMMAND_QUEUE = "$PKG.statusbar.CommandQueue"
    private const val NOTIFICATION_ROW = "$PKG.statusbar.notification.row.ExpandableNotificationRow"

    private val FLOAT = Float::class.javaPrimitiveType
    private val BOOL = Boolean::class.javaPrimitiveType
    private val INT = Int::class.javaPrimitiveType

    fun register(classLoader: ClassLoader) {
        hookFinalList(classLoader)
        hookNotificationIcons(classLoader)
        hookListContainer(classLoader)
        hookShadeWindow(classLoader)
        hookShadeExpansion(classLoader)
        hookSystemBars(classLoader)
    }

    // ── 取列表 ──

    private fun hookFinalList(cl: ClassLoader) {
        val coordinator = runCatching { cl.loadClass(STACK_COORDINATOR) }.getOrNull()
            ?: return LiquidLog.w("list: StackCoordinator not found")
        val attach = coordinator.declaredMethods.firstOrNull { it.name == "attach" && it.parameterTypes.size == 1 }
            ?: return LiquidLog.w("list: StackCoordinator.attach not found")
        val listenerType = runCatching { cl.loadClass(RENDER_LIST_LISTENER) }.getOrNull()
            ?: return LiquidLog.w("list: OnAfterRenderListListener not found")
        HookHelper.hookMethod(attach, afterCallback = { chain, result ->
            val pipeline = chain.args.firstOrNull()
            if (pipeline != null) {
                val listener = Proxy.newProxyInstance(cl, arrayOf(listenerType)) { proxy, method, args ->
                    when (method.name) {
                        "onAfterRenderList" -> {
                            val list = args?.firstOrNull() as? List<*>
                            // 这里在系统界面自己的列表流程里,出错绝不能传回系统
                            if (list != null) runCatching { LiquidShade.onFinalList(list) }.onFailure { LiquidLog.w("list: read failed", it) }
                            null
                        }
                        "equals" -> proxy === args?.firstOrNull()
                        "hashCode" -> System.identityHashCode(proxy)
                        "toString" -> "FrostShadeNotificationListener"
                        else -> null
                    }
                }
                runCatching {
                    pipeline.javaClass.getMethod("addOnAfterRenderListListener", listenerType).invoke(pipeline, listener)
                    LiquidLog.i("list: listening to the final notification list")
                }.onFailure { LiquidLog.w("list: register listener failed", it) }
            }
            result
        })
    }

    // ── 系统通知列表 ──

    /** 原生通知的异步图像绑定、内容更新和主题重绑定完成后,重新读取实际图标。 */
    private fun hookNotificationIcons(cl: ClassLoader) {
        // 系统可能异步替换同一图标,不会再次调用 onNotificationUpdated。
        // 只监听安卓原版图片部件;回调只处理 LiquidEntries 已登记的来源图标。
        val imageUpdates = setOf("setImageDrawable", "setImageResource", "setImageURI", "invalidateDrawable",
            "setImageTintList", "setImageTintMode", "setImageTintBlendMode", "setColorFilter")
        for (method in ImageView::class.java.declaredMethods) {
            if (method.name !in imageUpdates || method.parameterTypes.size != 1) continue
            HookHelper.hookMethod(method, afterCallback = { chain, result ->
                (chain.thisObject as? ImageView)?.let { image ->
                    runCatching { LiquidShade.onNotificationImageUpdated(image) }
                        .onFailure { LiquidLog.w("icons: image refresh failed", it) }
                }
                result
            })
        }
        View::class.java.getDeclaredMethod("setBackgroundDrawable", android.graphics.drawable.Drawable::class.java)
            .let { method -> HookHelper.hookMethod(method, afterCallback = { chain, result ->
                (chain.thisObject as? ImageView)?.let { image -> LiquidShade.onNotificationImageUpdated(image) }
                result
            }) }
        val row = runCatching { cl.loadClass(NOTIFICATION_ROW) }.getOrNull()
            ?: return LiquidLog.w("icons: ExpandableNotificationRow not found")
        val updated = row.declaredMethods.firstOrNull { it.name == "onNotificationUpdated" && it.parameterTypes.isEmpty() }
            ?: return LiquidLog.w("icons: onNotificationUpdated not found")
        HookHelper.hookMethod(updated, afterCallback = { chain, result ->
            (chain.thisObject as? View)?.let { view ->
                runCatching { LiquidShade.onNotificationIconUpdated(view) }
                    .onFailure { LiquidLog.w("icons: refresh failed", it) }
            }
            result
        })
    }

    private fun hookListContainer(cl: ClassLoader) {
        val nssl = runCatching { cl.loadClass(LIST_CONTAINER) }.getOrNull()
            ?: return LiquidLog.w("container: NotificationStackScrollLayout not found")

        fun declared(name: String, vararg params: Class<*>?) =
            nssl.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.contentEquals(params) }
                .also { if (it == null) LiquidLog.w("container: $name not found") }

        declared("setIsExpanded", BOOL)?.let { m ->
            HookHelper.hookMethod(m, afterCallback = { chain, result ->
                val v = chain.thisObject as? ViewGroup
                val expanded = chain.args.firstOrNull() as? Boolean
                if (v != null && expanded != null) runCatching { LiquidShade.onExpandedChanged(v, expanded) }
                    .onFailure { LiquidLog.w("container: expanded update failed", it) }
                result
            })
        }
        declared("setStatusBarState", INT)?.let { m ->
            HookHelper.hookMethod(m, afterCallback = { chain, result ->
                val v = chain.thisObject as? ViewGroup
                val state = chain.args.firstOrNull() as? Int
                if (v != null && state != null) runCatching { LiquidShade.onBarStateChanged(v, state) }
                    .onFailure { LiquidLog.w("container: state update failed", it) }
                result
            })
        }
        // 接管时系统列表看不见,也不能收到触摸(否则会点到、划到看不见的系统卡片)
        declared("dispatchTouchEvent", MotionEvent::class.java)?.let { m ->
            HookHelper.hookMethod(m, beforeCallback = { if (LiquidShade.active) false else null })
        }
        // 系统只拿这两个数比大小:滚动位置为 0 表示在最上面,滚动位置不小于可滚范围表示已到底
        declared("getOwnScrollY")?.let { m ->
            HookHelper.hookMethod(m, beforeCallback = { if (LiquidShade.active) ownScrollY() else null })
        }
        declared("getScrollRange")?.let { m ->
            HookHelper.hookMethod(m, beforeCallback = {
                if (LiquidShade.active) ownScrollY() + if (LiquidShade.ui.canScrollDown) 1 else 0 else null
            })
        }
        declared("isBelowLastNotification", FLOAT, FLOAT)?.let { m ->
            HookHelper.hookMethod(m, beforeCallback = { chain ->
                if (LiquidShade.active) {
                    val y = chain.args.getOrNull(1) as? Float ?: 0f
                    y > LiquidShade.ui.contentBottom()
                } else {
                    null
                }
            })
        }
        declared("setClearAllInProgress", BOOL)?.let { m ->
            HookHelper.hookMethod(m, afterCallback = { chain, result ->
                val v = chain.args.firstOrNull() as? Boolean
                if (v != null) runCatching { LiquidShade.onClearAllChanged(v) }
                result
            })
        }
        LiquidLog.i("container: hooks installed")
    }

    private fun ownScrollY(): Int = if (LiquidShade.ui.scrolledFromTop) 1 else 0

    // ── 通知中心窗口 ──

    private fun hookShadeWindow(cl: ClassLoader) {
        val controller = runCatching { cl.loadClass(SHADE_WINDOW) }.getOrNull()
            ?: return LiquidLog.w("window: NotificationShadeWindowControllerImpl not found")
        controller.declaredConstructors.forEach { c ->
            HookHelper.hookConstructor(c, afterCallback = { chain, result ->
                chain.thisObject?.let { LiquidSystem.shadeWindow = WeakReference(it) }
                result
            })
        }
        LiquidLog.i("window: hooks installed")
    }

    // ── 整个下拉面板拉开了多少 ──

    /**
     * 安卓原版把「下拉面板拉开了多少」通知给系统界面里要跟着它变的部件(ShadeExpansionListener),
     * 管背景深浅的 NotificationShadeDepthController 是其中之一。事件里有拉开程度(0 到 1)和是否打开着;
     * 通知页和控制中心分开的系统把两页合在一起报(取拉开得更多的那一页),背景据此在两页之间保持不变。
     * 只在它处理完以后读,不改系统的处理。
     */
    private fun hookShadeExpansion(cl: ClassLoader) {
        val depth = runCatching { cl.loadClass(DEPTH_CONTROLLER) }.getOrNull()
            ?: return LiquidLog.w("expansion: NotificationShadeDepthController not found")
        val m = depth.declaredMethods.firstOrNull {
            it.name == "onPanelExpansionChanged" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0].simpleName == "ShadeExpansionChangeEvent"
        } ?: return LiquidLog.w("expansion: onPanelExpansionChanged not found")
        val event = m.parameterTypes[0]
        val fraction = runCatching { event.getMethod("getFraction") }.getOrNull()
        val expanded = runCatching { event.getMethod("getExpanded") }.getOrNull()
        if (fraction == null || expanded == null) return LiquidLog.w("expansion: event values not found")
        HookHelper.hookMethod(m, afterCallback = { chain, result ->
            chain.args.firstOrNull()?.let { e ->
                // 这里在系统界面自己的面板流程里,出错绝不能传回系统
                runCatching { LiquidShade.onShadeExpansion(fraction.invoke(e) as Float, expanded.invoke(e) as Boolean) }
                    .onFailure { LiquidLog.w("expansion: update failed", it) }
            }
            result
        })
        LiquidLog.i("expansion: hooks installed")
    }

    // ── 后面是亮还是暗 ──

    /**
     * 安卓(窗口管理)告诉系统界面每个屏幕的状态栏外观:前两个参数是屏幕编号和外观值,后面带发起的应用包名。
     * 在系统界面收这个消息的线程上调用,交给总管自己换到主线程。
     */
    private fun hookSystemBars(cl: ClassLoader) {
        val queue = runCatching { cl.loadClass(COMMAND_QUEUE) }.getOrNull()
            ?: return LiquidLog.w("bars: CommandQueue not found")
        val m = queue.declaredMethods.firstOrNull {
            it.name == "onSystemBarAttributesChanged" && it.parameterTypes.size >= 2 &&
                it.parameterTypes[0] == INT && it.parameterTypes[1] == INT
        } ?: return LiquidLog.w("bars: onSystemBarAttributesChanged not found")
        HookHelper.hookMethod(m, afterCallback = { chain, result ->
            val display = chain.args.getOrNull(0) as? Int
            val appearance = chain.args.getOrNull(1) as? Int
            val pkg = chain.args.firstOrNull { it is String } as? String
            if (display != null && appearance != null) {
                runCatching { LiquidShade.onSystemBars(display, appearance, pkg) }.onFailure { LiquidLog.w("bars: update failed", it) }
            }
            result
        })
        hookBarRegistration()
        LiquidLog.i("bars: hooks installed")
    }

    /**
     * 系统界面启动时向安卓登记状态栏,登记结果里带着当时的状态栏外观和应用包名(安卓原版登记结果的公开字段);
     * 记下来,重启系统界面后第一次拉下也知道后面是亮是暗(之后的变化由上面那一处告诉我们)。
     */
    private fun hookBarRegistration() {
        val proxy = runCatching { Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub\$Proxy") }.getOrNull()
            ?: return LiquidLog.w("bars: status bar service not found")
        val m = proxy.declaredMethods.firstOrNull { it.name == "registerStatusBar" }
            ?: return LiquidLog.w("bars: registerStatusBar not found")
        HookHelper.hookMethod(m, afterCallback = { _, result ->
            runCatching {
                if (result != null) {
                    val appearance = result.javaClass.getField("mAppearance").getInt(result)
                    val pkg = result.javaClass.getField("mPackageName").get(result) as? String
                    LiquidShade.onSystemBars(Display.DEFAULT_DISPLAY, appearance, pkg)
                }
            }.onFailure { LiquidLog.w("bars: registration read failed", it) }
            result
        })
    }
}
