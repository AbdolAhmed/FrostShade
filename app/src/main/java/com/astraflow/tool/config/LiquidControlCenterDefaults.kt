package com.astraflow.tool.config

import com.astraflow.tool.config.LiquidNotificationDefaults.BoolItem
import com.astraflow.tool.config.LiquidNotificationDefaults.IntItem

/**
 * 液态玻璃控制中心自己的可调设置(方案 docs/design/liquid-control-center.md)。
 * 背景与玻璃的各项和液态玻璃堆叠通知共用,见 [LiquidNotificationDefaults](用户 2026-09-26 选「背景的几项设置两个功能页里改的是同一份」)。
 */
object LiquidControlCenterDefaults {

    /**
     * 浅色模式下背景在控制中心那一页额外压暗:单独的开关(用户 2026-09-26「可以做成独立的开关」)。
     * OPPO 在控制中心里的图标文字一律是白色的,浅色模式的背景太浅会看不清;默认开
     */
    val DIM_LIGHT_ENABLED = BoolItem(ConfigKeys.LIQUID_CONTROL_CENTER_DIM_LIGHT_ENABLED, true)

    /** 额外压暗多少(百分比):按控制中心在屏幕上显示的程度平滑过渡 */
    val DIM_LIGHT = IntItem(ConfigKeys.LIQUID_CONTROL_CENTER_DIM_LIGHT, 35, 0, 70)

    /** 全部可调的数(恢复默认时逐项写回) */
    val INTS: List<IntItem> = listOf(DIM_LIGHT)

    /** 全部开关(不含总开关) */
    val BOOLS: List<BoolItem> = listOf(DIM_LIGHT_ENABLED)
}
