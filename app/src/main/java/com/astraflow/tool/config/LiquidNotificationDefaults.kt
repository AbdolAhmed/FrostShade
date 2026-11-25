package com.astraflow.tool.config

import com.astraflow.frostshade.FrostStyle

/**
 * 液态玻璃通知各项可调设置:存的名字、默认值和范围(设置页、恢复默认与系统界面一侧共用这一份)。
 * 玻璃的默认值就是液态玻璃底栏的数字,堆叠照苹果的数字(方案 docs/design/liquid-notification.md)。
 * 会因手机而不同的效果(背景模糊、颜色浓淡)开放给用户调,不按机型特调(用户 2026-09-26「把这个值也开放给用户」)。
 */
object LiquidNotificationDefaults {

    /** 一项可调的数 */
    class IntItem(val key: String, val default: Int, val min: Int, val max: Int) {
        fun clamp(value: Int): Int = value.coerceIn(min, max)
    }

    /** 一项开关 */
    class BoolItem(val key: String, val default: Boolean)

    /** 一行文字(优先应用名单、选择页排序方式、打包样式;不截范围) */
    class TextItem(val key: String, val default: String)

    // ── 通知中心背景(透明:后面的桌面或应用本身透过来,不截图) ──

    /**
     * 背景模糊程度(dp):安卓原版区域模糊的程度。0 为透明,看到的就是真实的桌面或应用;默认轻轻模糊
     * (用户 2026-09-26「模糊还是要有的，只不过我们是通过安卓原版给窗口设置模糊程度……把这个值也开放给用户」)
     */
    val BACKGROUND_BLUR = IntItem(ConfigKeys.LIQUID_NOTIFICATION_BACKGROUND_BLUR, FrostStyle.BACKGROUND_BLUR_DP, 0, 100)

    /** 背景压暗(百分比,蒙一层黑)。浅色模式也压暗:通知中心顶部的时间等系统文字是白色的 */
    val BACKGROUND_DIM_LIGHT = IntItem(ConfigKeys.LIQUID_NOTIFICATION_BACKGROUND_DIM_LIGHT, 10, 0, 80)
    val BACKGROUND_DIM_DARK = IntItem(ConfigKeys.LIQUID_NOTIFICATION_BACKGROUND_DIM_DARK, 30, 0, 80)

    /**
     * 后方较亮时加深(百分比):后面是亮色的页面或壁纸时(安卓告诉系统「状态栏图标用深色」),在压暗之上再加深,
     * 顶部时间和卡片才不会发白看不清(用户 2026-09-26「透过来的例如QQ页面就是白色……过曝」)
     */
    val BACKGROUND_BRIGHT_EXTRA = IntItem(ConfigKeys.LIQUID_NOTIFICATION_BACKGROUND_BRIGHT_EXTRA, 35, 0, 70)

    /** 顶部加深(百分比):顶部时间和按钮那一块由上往下淡出的一道加深 */
    val BACKGROUND_TOP_DIM = IntItem(ConfigKeys.LIQUID_NOTIFICATION_BACKGROUND_TOP_DIM, 20, 0, 60)

    // ── 玻璃(底栏的数字) ──

    val SURFACE_LIGHT = IntItem(ConfigKeys.LIQUID_NOTIFICATION_SURFACE_LIGHT, FrostStyle.SURFACE_LIGHT, 0, 80)
    val SURFACE_DARK = IntItem(ConfigKeys.LIQUID_NOTIFICATION_SURFACE_DARK, FrostStyle.SURFACE_DARK, 0, 80)

    /** 卡片影子浓度(百分比):底栏浅色 10、深色 20;这里调的是浅色的数字,深色自动加倍 */
    val SHADOW = IntItem(ConfigKeys.LIQUID_NOTIFICATION_SHADOW, 10, 0, 40)

    // ── 亮边与光效 ──

    val HIGHLIGHT = IntItem(ConfigKeys.LIQUID_NOTIFICATION_HIGHLIGHT, FrostStyle.HIGHLIGHT, 0, 100)
    val HIGHLIGHT_TILT = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_HIGHLIGHT_TILT, true)
    val PRESS_GLOW = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_PRESS_GLOW, true)
    val FLOW_LIGHT = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_FLOW_LIGHT, true)
    val AMBIENT_GLOW = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_AMBIENT_GLOW, false)

    // ── 堆叠(苹果的数字) ──

    val STACK = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_STACK, true)

    /** 叠在后面露出几层 */
    val STACK_LAYERS = IntItem(ConfigKeys.LIQUID_NOTIFICATION_STACK_LAYERS, 2, 1, 3)

    /** 每层露出的高度(dp) */
    val STACK_PEEK = IntItem(ConfigKeys.LIQUID_NOTIFICATION_STACK_PEEK, 8, 4, 16)

    /** 后面每一层的宽度是前一层的百分之多少 */
    val STACK_SCALE = IntItem(ConfigKeys.LIQUID_NOTIFICATION_STACK_SCALE, 94, 80, 100)

    /** 后面每一层压暗多少(百分比) */
    val STACK_DIM = IntItem(ConfigKeys.LIQUID_NOTIFICATION_STACK_DIM, 6, 0, 30)

    // ── 卡片尺寸(公开反馈 #33:不堆叠时每条通知都是一张完整的大卡片,很占地方) ──

    /** 卡片宽度(百分比):卡片在通知列表里占多宽,左右留白一样;100 就是原来的宽度 */
    val CARD_WIDTH = IntItem(ConfigKeys.LIQUID_NOTIFICATION_CARD_WIDTH, 100, 70, 100)

    /** 卡片大小(百分比):卡片里的字、图标、按钮与留白一起缩小,卡片随之变矮;100 就是原来的大小 */
    val CARD_SCALE = IntItem(ConfigKeys.LIQUID_NOTIFICATION_CARD_SCALE, 100, 70, 100)

    // ── 通知操作(公开反馈 #80) ──

    /**
     * 置顶与左滑选项:开着时长按菜单多出「置顶」,向左滑卡片停住并露出「置顶」「设置」,只有向右滑清除;
     * 置顶的通知排在列表最上面。关着(默认)时左右滑都清除,长按只有「通知设置」
     */
    val SWIPE_OPTIONS = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_SWIPE_OPTIONS, false)

    // ── 叠放范围 ──

    /** 叠放范围:0 同一组(默认),1 同一应用(相邻的同应用通知也叠),2 全部通知叠为一组 */
    val GROUPING = IntItem(ConfigKeys.LIQUID_NOTIFICATION_GROUPING, 0, 0, 2)

    // ── 通知排序(分区内重排;置顶仍排在最前) ──

    val SORT_ENABLED = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_SORT_ENABLED, false)

    /** 排序依据:0 卡片高度,1 发出时间(默认),2 应用名,3 重要性 */
    val SORT_PRIMARY = IntItem(ConfigKeys.LIQUID_NOTIFICATION_SORT_PRIMARY, 1, 0, 3)

    /** 反转排序方向 */
    val SORT_DESC = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_SORT_DESC, false)

    /** 能叠放的通知排在单条通知前面 */
    val SORT_STACKED_FIRST = BoolItem(ConfigKeys.LIQUID_NOTIFICATION_SORT_STACKED_FIRST, false)

    /** 优先应用:一行一个包名,按顺序排在最前 */
    val SORT_PRIORITY = TextItem(ConfigKeys.LIQUID_NOTIFICATION_SORT_PRIORITY, "")

    /** 「选择优先应用」页的列表排序方式 */
    val SORT_PRIORITY_SORT_MODE = TextItem(ConfigKeys.LIQUID_NOTIFICATION_SORT_PRIORITY_SORT_MODE, "label_asc")

    /** 数量角标的全部样式,打包成一行(格式见 NotificationBadgeSpec,默认关) */
    val BADGE = TextItem(ConfigKeys.LIQUID_NOTIFICATION_BADGE, NotificationBadgeSpec.DEFAULT.encode())

    /** 全部可调的数(恢复默认时逐项写回) */
    val INTS: List<IntItem> = listOf(
        BACKGROUND_BLUR, BACKGROUND_DIM_LIGHT, BACKGROUND_DIM_DARK, BACKGROUND_BRIGHT_EXTRA, BACKGROUND_TOP_DIM,
        SURFACE_LIGHT, SURFACE_DARK, SHADOW,
        HIGHLIGHT,
        STACK_LAYERS, STACK_PEEK, STACK_SCALE, STACK_DIM, GROUPING,
        CARD_WIDTH, CARD_SCALE,
        SORT_PRIMARY,
    )

    /** 全部开关(不含总开关) */
    val BOOLS: List<BoolItem> = listOf(
        HIGHLIGHT_TILT, PRESS_GLOW, FLOW_LIGHT, AMBIENT_GLOW, STACK, SWIPE_OPTIONS,
        SORT_ENABLED, SORT_DESC, SORT_STACKED_FIRST,
    )

    /** 全部文字 */
    val TEXTS: List<TextItem> = listOf(SORT_PRIORITY, SORT_PRIORITY_SORT_MODE, BADGE)
}
