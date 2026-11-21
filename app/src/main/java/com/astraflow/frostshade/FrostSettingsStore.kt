package com.astraflow.frostshade

import android.content.SharedPreferences
import android.util.Log
import com.astraflow.tool.config.ConfigKeys
import com.astraflow.tool.config.LiquidControlCenterDefaults as Controls
import com.astraflow.tool.config.LiquidNotificationDefaults as Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class FrostSettingsStore(private val local: SharedPreferences, scope: CoroutineScope) {
    private val localValues = MutableStateFlow(defaults + local.all.filterKeys { it in defaults })
    val values = localValues.asStateFlow()

    private val remote = MutableStateFlow<SharedPreferences?>(null)

    init {
        scope.launch {
            combine(localValues, remote) { values, prefs -> values to prefs }.collect { (values, prefs) ->
                if (prefs == null) return@collect
                runCatching {
                    check(prefs.edit().withValues(values).commit())
                }.onFailure {
                    Log.w("FrostShade", "Settings sync failed", it)
                }
            }
        }
    }

    fun connect(prefs: SharedPreferences?) {
        remote.value = prefs
    }

    fun set(key: String, value: Any) {
        require(defaults.containsKey(key))
        local.edit().withValues(mapOf(key to value)).apply()
        localValues.value = localValues.value + (key to value)
    }

    fun resetAppearance() {
        val appearance = defaults - ConfigKeys.LIQUID_NOTIFICATION_ENABLED - ConfigKeys.LIQUID_CONTROL_CENTER_ENABLED
        local.edit().withValues(appearance).apply()
        localValues.value = localValues.value + appearance
    }

    private fun SharedPreferences.Editor.withValues(values: Map<String, *>): SharedPreferences.Editor = apply {
        for ((key, value) in values) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }
    }

    private companion object {
        val defaults = buildMap<String, Any> {
            put(ConfigKeys.LIQUID_NOTIFICATION_ENABLED, true)
            put(ConfigKeys.LIQUID_CONTROL_CENTER_ENABLED, true)
            (Notifications.INTS + Controls.INTS).forEach { put(it.key, it.default) }
            (Notifications.BOOLS + Controls.BOOLS).forEach { put(it.key, it.default) }
            Notifications.TEXTS.forEach { put(it.key, it.default) }
        }
    }
}
