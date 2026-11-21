package com.astraflow.tool.hook.liquidnotif.ui

import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.notifcommon.NotificationArrange

/**
 * 归组与重排(纯计算,不碰界面):同一组照系统原样;同一应用把相邻的同应用通知归成一组;
 * 全部通知归成一组(组里按发出时间由新到旧,最新的一条在最上面)。
 * 排序开着时分区内按单元重排:优先应用 → 叠放的优先(可选) → 排序依据(方向);
 * 「置顶与左滑选项」的置顶仍在最前(见 ShadeListMerge.pinnedFirst)。
 */
internal object LiquidArrange {

    /** 全部通知叠为一组时的组名(展开后标题行显示) */
    private const val ALL_LABEL = "全部通知"

    fun arrange(groups: List<LiquidGroup>, settings: LiquidSettings): List<LiquidGroup> {
        val grouping = settings.grouping
        val sort = NotificationArrange.Spec(
            enabled = settings.sortEnabled,
            primary = settings.sortPrimary,
            desc = settings.sortDesc,
            stackedFirst = settings.sortStackedFirst,
        )
        if (!sort.enabled && grouping == NotificationArrange.GROUP_SYSTEM) return groups
        val items = groups.flatMap { g ->
            g.items.map { n ->
                NotificationArrange.Item(
                    value = n,
                    ownKey = n.key,
                    groupKey = g.id,
                    packageName = n.packageName,
                    section = n.section,
                    postTime = n.postTime,
                    importance = n.importance,
                    heightPx = n.cardHeightPx,
                    appLabel = n.appLabel,
                    unitSize = g.items.size,
                )
            }
        }
        return NotificationArrange.arrange(items, grouping, sort, LiquidSettings.priorityRank(settings.sortPriority))
            .map { run ->
                LiquidGroup(
                    id = run.key,
                    appLabel = if (grouping == NotificationArrange.GROUP_ALL) ALL_LABEL else run.items.first().appLabel,
                    items = run.items,
                )
            }
    }
}
