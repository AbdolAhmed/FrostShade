package com.astraflow.tool.hook.liquidnotif

import android.os.SystemClock
import android.util.Log
import com.astraflow.tool.hook.core.HookHelper

internal object LiquidLog {
    private const val TAG = "FrostShade"
    private const val INFO = 4
    private const val WARN = 5

    @Volatile
    private var checkedAt = -10_000L

    @Volatile
    private var switchOn = false

    private fun switchedOn(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - checkedAt > 3_000L) {
            checkedAt = now
            switchOn = runCatching {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                    .invoke(null, "debug.frostshade.trace", "0") == "1"
            }.getOrDefault(false)
        }
        return switchOn
    }

    /** 排查开关开着(每帧的细节记录先问这个,关着时不拼字符串) */
    val on: Boolean get() = switchedOn()

    fun i(msg: String) {
        Log.i(TAG, msg)
        if (switchedOn()) runCatching { HookHelper.xposedInterface?.log(INFO, TAG, msg) }
    }

    fun w(msg: String, t: Throwable? = null) {
        if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
        if (switchedOn()) {
            runCatching {
                val xi = HookHelper.xposedInterface
                if (t != null) xi?.log(WARN, TAG, msg, t) else xi?.log(WARN, TAG, msg)
            }
        }
    }
}
