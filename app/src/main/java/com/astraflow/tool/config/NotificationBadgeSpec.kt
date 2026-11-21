package com.astraflow.tool.config

/**
 * 数量角标的全部样式:「堆叠通知」与「液态玻璃堆叠通知」共用一份,
 * 各自存成自己设置前缀下的一行打包值(服务器每个配置包的键数有上限,角标的九项打包成一个键)。
 * 打包格式(逗号分隔):开关(0/1),数字含义(0 总条数/1 未露出),角标位置(0 左上/1 右上/2 左下/3 右下),
 * 字号 sp,内边距 dp,角标圆角 dp,边距 dp,背景色 ARGB,文字色 ARGB。
 */
data class NotificationBadgeSpec(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val corner: Int = 1,
    val textSp: Int = 10,
    val paddingDp: Int = 5,
    val radiusDp: Int = 8,
    val marginDp: Int = 8,
    val bgColor: Int = 0x66000000,
    val textColor: Int = 0xFFFFFFFF.toInt(),
) {
    fun encode(): String = listOf(
        if (enabled) 1 else 0, mode, corner, textSp, paddingDp, radiusDp, marginDp, bgColor, textColor,
    ).joinToString(",")

    companion object {
        val DEFAULT = NotificationBadgeSpec()

        const val MODE_COUNT = 0
        const val MODE_HIDDEN = 1

        const val CORNER_TOP_START = 0
        const val CORNER_TOP_END = 1
        const val CORNER_BOTTOM_START = 2
        const val CORNER_BOTTOM_END = 3

        val TEXT_SP_RANGE = 8..16
        val PADDING_DP_RANGE = 2..12
        val RADIUS_DP_RANGE = 0..16
        val MARGIN_DP_RANGE = 0..16

        /** 读回一行打包值;少了、坏了都按默认 */
        fun decode(value: String): NotificationBadgeSpec = runCatching {
            val p = value.split(',').map { it.trim().toInt() }
            if (p.size < 9) return@runCatching DEFAULT
            NotificationBadgeSpec(
                enabled = p[0] != 0,
                mode = p[1].coerceIn(MODE_COUNT, MODE_HIDDEN),
                corner = p[2].coerceIn(CORNER_TOP_START, CORNER_BOTTOM_END),
                textSp = p[3].coerceIn(TEXT_SP_RANGE),
                paddingDp = p[4].coerceIn(PADDING_DP_RANGE),
                radiusDp = p[5].coerceIn(RADIUS_DP_RANGE),
                marginDp = p[6].coerceIn(MARGIN_DP_RANGE),
                bgColor = p[7],
                textColor = p[8],
            )
        }.getOrDefault(DEFAULT)
    }
}
