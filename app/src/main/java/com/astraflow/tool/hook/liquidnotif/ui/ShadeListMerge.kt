package com.astraflow.tool.hook.liquidnotif.ui

import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification

/**
 * 把系统新给的通知列表并进我们正在显示的列表,让离开的卡片走完动画。纯计算,不碰界面。
 *
 * 系统拿掉通知往往比动画快(按标题行的叉时系统几乎立刻把整组拿掉,飞出去的动画还没开始这一组就没了,
 * 用户 2026-09-26「点击这个小叉清除通知时，是没有动画的」),所以正在离开的卡片先留在原位,动画走完再拿掉。
 */
internal object ShadeListMerge {

    /**
     * [fresh] 是系统新给的列表,[shown] 是我们正在显示的列表,[leaving] 是正在离开(动画还没走完)的通知标识,
     * [visible] 判断一条通知在我们的列表里是不是还显示着(用户清除后已藏起的不算)。
     *
     * 1. 组的标识接上:系统新给的一组标识是新的,而正在显示的某一组标识已经没了、又和它有同一条通知
     *    (系统把一组拆成单条、或把单条并成一组,标识都会变),就沿用那一组的标识,列表不会把这一块整块换掉重来。
     * 2. 正在离开的通知留在原来的位置:不管系统是否已经拿掉、挪进了别的组,都放回它在 [shown] 里的那一组、
     *    原来的前后位置;那一组整组都没了,就把这一组放回原来在它前面的那一组后面。
     */
    fun merge(
        shown: List<LiquidGroup>,
        fresh: List<LiquidGroup>,
        leaving: Set<String>,
        visible: (LiquidNotification) -> Boolean = { true },
    ): List<LiquidGroup> {
        val renamed = keepIds(shown, fresh, visible)
        if (leaving.isEmpty()) return renamed
        val ghosts = shown.map { g -> g.items.withIndex().filter { it.value.key in leaving } }
        if (ghosts.all { it.isEmpty() }) return renamed
        val out = renamed.mapNotNull { g ->
            val kept = g.items.filter { it.key !in leaving }
            when {
                kept.isEmpty() -> null
                kept.size == g.items.size -> g
                else -> g.copy(items = kept)
            }
        }.toMutableList()
        shown.forEachIndexed { gi, old ->
            val back = ghosts[gi]
            if (back.isEmpty()) return@forEachIndexed
            var at = out.indexOfFirst { it.id == old.id }
            if (at < 0) {
                // 这一组系统已经整组拿掉,或整组都在离开:放回原来在它前面、现在还在的那一组后面
                val before = (gi - 1 downTo 0).firstNotNullOfOrNull { j ->
                    out.indexOfFirst { it.id == shown[j].id }.takeIf { it >= 0 }
                }
                at = if (before == null) 0 else before + 1
                out.add(at, old.copy(items = emptyList()))
            }
            val items = out[at].items.toMutableList()
            for ((oi, ghost) in back) {
                // 放回原来在它前面、现在还在的那一张后面
                val prev = (oi - 1 downTo 0).firstNotNullOfOrNull { j ->
                    val k = old.items[j].key
                    items.indexOfFirst { it.key == k }.takeIf { it >= 0 }
                }
                items.add(if (prev == null) 0 else prev + 1, ghost)
            }
            out[at] = out[at].copy(items = items)
        }
        return out
    }

    /**
     * 置顶的组排在最前面(公开反馈 #80):组里有一条置顶就算置顶,后置顶的排在前面;没置顶的照原来的先后。
     * [pinned] 是通知标识 → 置顶的时间。
     */
    fun pinnedFirst(groups: List<LiquidGroup>, pinned: Map<String, Long>): List<LiquidGroup> {
        if (pinned.isEmpty() || groups.isEmpty()) return groups
        val at = LongArray(groups.size) { i -> groups[i].items.maxOfOrNull { pinned[it.key] ?: Long.MIN_VALUE } ?: Long.MIN_VALUE }
        val top = groups.indices.filter { at[it] != Long.MIN_VALUE }
        if (top.isEmpty()) return groups
        return top.sortedByDescending { at[it] }.map { groups[it] } + groups.indices.filter { at[it] == Long.MIN_VALUE }.map { groups[it] }
    }

    /** 组的标识接上(见 [merge] 第 1 条);只接还显示着的组,已经全部藏起的不接 */
    private fun keepIds(
        shown: List<LiquidGroup>,
        fresh: List<LiquidGroup>,
        visible: (LiquidNotification) -> Boolean,
    ): List<LiquidGroup> {
        if (shown.isEmpty()) return fresh
        val freshIds = fresh.mapTo(HashSet()) { it.id }
        val orphans = shown.filter { g -> g.id !in freshIds && g.items.any(visible) }
        if (orphans.isEmpty()) return fresh
        val shownIds = shown.mapTo(HashSet()) { it.id }
        val used = HashSet<String>()
        return fresh.map { g ->
            if (g.id in shownIds) return@map g
            val keys = g.items.mapTo(HashSet()) { it.key }
            val old = orphans.firstOrNull { o -> o.id !in used && o.items.any { it.key in keys } } ?: return@map g
            used += old.id
            g.copy(id = old.id)
        }
    }
}
