package com.astraflow.tool.config

import org.json.JSONObject

/** 本机通知置顶记录，不随配置备份；仅保存通知标识与置顶时间。 */
object LiquidNotificationPins {
    const val KEY = "liquid_notification_pins"

    fun encode(pins: Map<String, Long>): String = JSONObject(pins).toString()

    fun decode(value: String): Map<String, Long> = runCatching {
        val data = JSONObject(value)
        buildMap {
            for (key in data.keys()) {
                val time = data.optLong(key, 0L)
                if (key.isNotEmpty() && time > 0L) put(key, time)
            }
        }
    }.getOrDefault(emptyMap())
}
