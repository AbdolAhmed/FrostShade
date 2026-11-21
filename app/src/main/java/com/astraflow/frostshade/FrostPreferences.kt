package com.astraflow.frostshade

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log

internal object FrostPreferences {
    const val GROUP = "frostshade"

    @Volatile
    private var remote: SharedPreferences? = null
    private var acquire: (() -> SharedPreferences)? = null
    private var retryAt = 0L

    @Synchronized
    fun connect(acquire: () -> SharedPreferences) {
        this.acquire = acquire
        reconnect()
    }

    private fun preferences(): SharedPreferences? = remote ?: reconnect()

    @Synchronized
    private fun reconnect(): SharedPreferences? {
        remote?.let { return it }
        val acquire = acquire ?: return null
        val now = SystemClock.uptimeMillis()
        if (now < retryAt) return null
        retryAt = now + 1_000
        return runCatching { acquire() }
            .onSuccess { remote = it }
            .onFailure { Log.w("FrostShade", "Settings service unavailable", it) }
            .getOrNull()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = preferences()?.getBoolean(key, default) ?: default

    fun getInt(key: String, default: Int): Int = preferences()?.getInt(key, default) ?: default

    fun getString(key: String, default: String): String = preferences()?.getString(key, default) ?: default
}
