package com.astraflow.tool.hook.notifcommon

/**
 * 通知归组与排序的纯计算:「堆叠通知」(系统卡片堆叠)与「液态玻璃堆叠通知」共用。
 * 不碰安卓与系统界面的类,单独可测;两边各自把通知换成这里的 [Item],拿回分好组的结果。
 *
 * 归组:同一组(系统合成的组)/同一应用(相邻的同应用通知也叠)/全部通知叠为一组。
 * 排序:分区内按「单元」重排——优先应用 → 叠放的优先(可选) → 排序依据(方向) → 原来的先后。
 */
internal object NotificationArrange {

    /** 叠放范围 */
    const val GROUP_SYSTEM = 0
    const val GROUP_APP = 1
    const val GROUP_ALL = 2

    /** 排序依据 */
    const val SORT_HEIGHT = 0
    const val SORT_TIME = 1
    const val SORT_LABEL = 2
    const val SORT_IMPORTANCE = 3

    /** 一组排序设置;[enabled] 为否时单元之间保持原序 */
    data class Spec(
        val enabled: Boolean,
        val primary: Int = SORT_TIME,
        val desc: Boolean = false,
        val stackedFirst: Boolean = false,
    )

    /** 一条通知交给归组排序的数据 */
    class Item<T>(
        val value: T,
        /** 这条通知自己的标识;没有组、没有应用名时,它自己就是一个单元 */
        val ownKey: String,
        /** 系统合成的组的标识;不在组里为空 */
        val groupKey: String?,
        val packageName: String?,
        /** 列表分区号;取同一个值的一段是同一个分区 */
        val section: Int,
        /** 发出时间(毫秒) */
        val postTime: Long,
        /** 通知重要性(0~5,取不到按 0) */
        val importance: Int,
        /** 系统卡片的高度(像素);取不到按 0(按高度排序时沉到最后) */
        val heightPx: Int,
        /** 应用名 */
        val appLabel: String,
        /** 这一条本身算不算一组(系统还没拆开的组,按它的子项数);拆开后都是 1 */
        val unitSize: Int = 1,
    )

    /** 排好的一个单元:单元的标识(堆叠用的组键)和按顺序的成员 */
    class Run<T>(val key: String, val items: List<T>)

    /** 一条通知归到哪个单元:[ownKey] 与 [groupKey] 见 [Item] */
    fun unitKey(grouping: Int, ownKey: String, groupKey: String?, packageName: String?): String = when (grouping) {
        GROUP_APP -> packageName?.let { "a:$it" } ?: ownKey
        GROUP_ALL -> "a:all"
        else -> groupKey ?: ownKey
    }

    private class Unit<T>(val key: String, val section: Int, val members: MutableList<Item<T>>)

    /**
     * 把 [items] 归成单元、分区内重排、输出按顺序的分组。
     *
     * - 单元 = 列表里相邻且单元键相同的一段;单元键见 [unitKey]。
     * - 分区 = 相邻且分区号相同的一段单元;排序只在分区内进行,分区本身不动。
     * - [sortEnabled] 开着时单元之间:优先应用(名单越靠前越前)→ 能叠放的排在单条前面(可选)→
     *   排序依据(自然方向:高度高的在前、发出时间新的在前、应用名 A 到 Z、重要性高的在前;[spec] 的 desc 整体反转)→
     *   原来的先后。关着时单元保持原序。
     * - 成员次序:同一组照系统原样;同一应用与全部通知按发出时间由新到旧(新到的那一摞最上面是最新的)。
     * - [priorityRank] 是优先应用名单:包名 → 名次(越小越前);不在名单里的排在有名单的后面。
     */
    fun <T : Any> arrange(
        items: List<Item<T>>,
        grouping: Int,
        spec: Spec,
        priorityRank: Map<String, Int>,
    ): List<Run<T>> {
        val keys = items.map { unitKey(grouping, it.ownKey, it.groupKey, it.packageName) }
        val units = ArrayList<Unit<T>>(items.size)
        items.forEachIndexed { i, it ->
            val last = units.lastOrNull()
            if (last != null && last.key == keys[i]) {
                last.members.add(it)
            } else {
                units.add(Unit(keys[i], it.section, mutableListOf(it)))
            }
        }
        val out = ArrayList<Unit<T>>(units.size)
        var s = 0
        while (s < units.size) {
            var e = s
            while (e < units.size && units[e].section == units[s].section) e++
            val run = units.subList(s, e)
            out += if (spec.enabled) sortUnits(run, spec, priorityRank) else run
            s = e
        }
        return out.map { u ->
            val members = if (grouping == GROUP_SYSTEM) u.members else u.members.sortedByDescending { it.postTime }
            Run(u.key, members.map { it.value })
        }
    }

    private fun <T : Any> stacked(u: Unit<T>): Boolean = u.members.size >= 2 || u.members.first().unitSize >= 2

    private fun <T : Any> sortUnits(units: List<Unit<T>>, spec: Spec, priority: Map<String, Int>): List<Unit<T>> {
        return units.mapIndexed { i, u -> Triple(u, priorityRank(u, priority), i) }
            .sortedWith { (ua, ra, ia), (ub, rb, ib) ->
                if (ra != rb) return@sortedWith ra - rb
                if (spec.stackedFirst) {
                    val sa = stacked(ua)
                    if (sa != stacked(ub)) return@sortedWith if (sa) -1 else 1
                }
                val c = compareUnits(ua, ub, spec.primary)
                if (c != 0) return@sortedWith if (spec.desc) -c else c
                ia - ib
            }
            .map { it.first }
    }

    private fun <T : Any> priorityRank(u: Unit<T>, priority: Map<String, Int>): Int =
        u.members.mapNotNull { priority[it.packageName] }.minOrNull() ?: Int.MAX_VALUE

    /** 排序依据的自然方向:返回 <0 时 a 应排在 b 前 */
    private fun <T : Any> compareUnits(a: Unit<T>, b: Unit<T>, primary: Int): Int = when (primary) {
        SORT_HEIGHT -> b.members.first().heightPx - a.members.first().heightPx
        SORT_LABEL -> a.members.first().appLabel.compareTo(b.members.first().appLabel, ignoreCase = true)
        SORT_IMPORTANCE -> maxImportance(b) - maxImportance(a)
        else -> newestTime(b).compareTo(newestTime(a))
    }

    private fun <T : Any> newestTime(u: Unit<T>): Long = u.members.maxOf { it.postTime }

    private fun <T : Any> maxImportance(u: Unit<T>): Int = u.members.maxOf { it.importance }
}
