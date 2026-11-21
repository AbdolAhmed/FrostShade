package com.astraflow.tool.hook.liquidnotif

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * 点开、按按钮、直接回复、划掉、收起通知中心,都交给安卓的系统通知服务完成(各家系统都有这个服务),
 * 系统自己的记录(已点开、点开后自动删除、已回复、统计)因此保持正确。
 */
internal object LiquidSystem {

    private val service: Any? by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "statusbar") as IBinder
            Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.onFailure { LiquidLog.w("status bar service unavailable", it) }.getOrNull()
    }

    private fun method(name: String, paramCount: Int): Method? =
        service?.javaClass?.methods?.firstOrNull { it.name == name && it.parameterTypes.size == paramCount }

    /** 这条通知在列表里的位置,系统用它记录用户看到了什么 */
    private fun visibility(key: String, rank: Int, count: Int): Any? = runCatching {
        val cls = Class.forName("com.android.internal.statusbar.NotificationVisibility")
        cls.getMethod("obtain", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(null, key, rank, count, true)
    }.getOrNull()

    /** 系统通知统计里的常量(划掉的位置、用户态度),按名字从系统里读 */
    private fun stat(name: String): Int? = runCatching {
        Class.forName("android.service.notification.NotificationStats").getField(name).getInt(null)
    }.onFailure { LiquidLog.w("NotificationStats.$name unavailable", it) }.getOrNull()

    private fun send(context: Context, intent: PendingIntent, fillIn: Intent? = null): Boolean = runCatching {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        intent.send(context, 0, fillIn, null, null, null, options)
        true
    }.onFailure { LiquidLog.w("send pending intent failed", it) }.getOrDefault(false)

    /** 点开:发出这条通知的跳转,并告诉系统它被点开了(系统据此自动删除该删的通知) */
    fun open(context: Context, sbn: StatusBarNotification, rank: Int, count: Int): Boolean {
        val intent = sbn.notification.contentIntent ?: return false
        if (!send(context, intent)) return false
        runCatching { method("onNotificationClick", 2)?.invoke(service, sbn.key, visibility(sbn.key, rank, count)) }
            .onFailure { LiquidLog.w("report click failed", it) }
        return true
    }

    /** 按通知底部的按钮:发出按钮的动作,并告诉系统按了第几个 */
    fun action(context: Context, sbn: StatusBarNotification, index: Int, rank: Int, count: Int): Boolean {
        val action: Notification.Action = sbn.notification.actions?.getOrNull(index) ?: return false
        val intent = action.actionIntent ?: return false
        if (!send(context, intent)) return false
        reportAction(sbn, index, action, rank, count)
        return true
    }

    private fun reportAction(sbn: StatusBarNotification, index: Int, action: Notification.Action, rank: Int, count: Int) {
        runCatching {
            method("onNotificationActionClick", 5)?.invoke(service, sbn.key, index, action, visibility(sbn.key, rank, count), false)
        }.onFailure { LiquidLog.w("report action failed", it) }
    }

    /**
     * 直接回复:把输入的文字按安卓的回复格式交给这个按钮的动作,和系统自己的回复框一样,
     * 并告诉系统这条通知已回复。
     */
    fun reply(context: Context, sbn: StatusBarNotification, index: Int, text: CharSequence, rank: Int, count: Int): Boolean {
        val action: Notification.Action = sbn.notification.actions?.getOrNull(index) ?: return false
        val intent = action.actionIntent ?: return false
        val inputs = action.remoteInputs?.takeIf { it.isNotEmpty() } ?: return false
        val input = inputs.firstOrNull { it.allowFreeFormInput } ?: return false
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs, fillIn, Bundle().apply { putCharSequence(input.resultKey, text) })
        RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        if (!send(context, intent, fillIn)) return false
        runCatching { method("onNotificationDirectReplied", 1)?.invoke(service, sbn.key) }
            .onFailure { LiquidLog.w("report reply failed", it) }
        reportAction(sbn, index, action, rank, count)
        return true
    }

    /** 划掉一条:按「用户在通知栏里划掉」告诉系统 */
    fun dismiss(sbn: StatusBarNotification, rank: Int, count: Int) {
        if (!sbn.isClearable) return
        val surface = stat("DISMISSAL_SHADE") ?: return
        val sentiment = stat("DISMISS_SENTIMENT_NEUTRAL") ?: return
        @Suppress("DEPRECATION") val userId = sbn.userId
        runCatching {
            method("onNotificationClear", 6)?.invoke(
                service, sbn.packageName, userId, sbn.key, surface, sentiment, visibility(sbn.key, rank, count),
            ) ?: error("onNotificationClear not found")
        }.onFailure { LiquidLog.w("dismiss failed", it) }
    }

    /** 打开这个应用的通知设置(有类别时直接到这一类),并收起通知中心 */
    fun openSettings(context: Context, pkg: String, channelId: String?) {
        val intent = if (channelId != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }.putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }.onFailure { LiquidLog.w("open notification settings failed", it) }
        collapse()
    }

    /** 收起通知中心(系统自己的收起动画) */
    fun collapse() {
        runCatching { method("collapsePanels", 0)?.invoke(service) ?: error("collapsePanels not found") }
            .onFailure { LiquidLog.w("collapse failed", it) }
    }

    // ── 回复时让系统界面的窗口可以弹出键盘 ──

    /** 安卓原版管通知中心窗口的对象,由挂钩在它建好时记下 */
    @Volatile
    var shadeWindow: WeakReference<Any>? = null

    private var remoteInputMethod: Method? = null

    /**
     * 告诉系统「正在直接回复」:系统据此让通知中心窗口可以获得键盘(安卓原版回复框走的就是这一步);
     * 回复结束时再告诉系统结束。
     */
    fun setReplying(active: Boolean) {
        val controller = shadeWindow?.get() ?: return LiquidLog.w("shade window controller not captured")
        runCatching {
            val m = remoteInputMethod ?: controller.javaClass.getMethod("onRemoteInputActive", Boolean::class.javaPrimitiveType)
                .also { remoteInputMethod = it }
            m.invoke(controller, active)
        }.onFailure { LiquidLog.w("set remote input active failed", it) }
    }
}
