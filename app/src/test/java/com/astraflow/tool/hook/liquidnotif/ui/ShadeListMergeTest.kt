package com.astraflow.tool.hook.liquidnotif.ui

import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 液态玻璃通知:系统新给的列表并进正在显示的列表(2026-09-26 第四轮,用户「点击这个小叉清除通知时，是没有动画的」)。
 * 正在离开的卡片留在原位走完动画;系统把一组拆成单条、或把单条并成一组时,组的标识沿用原来的。
 */
class ShadeListMergeTest {

    private fun n(key: String) = LiquidNotification(
        key = key,
        packageName = "p",
        userId = 0,
        channelId = null,
        appLabel = "应用",
        appIcon = null,
        title = key,
        text = "",
        postTime = 1L,
    )

    private fun g(id: String, vararg keys: String) = LiquidGroup(id, "应用", keys.map(::n))

    private fun List<LiquidGroup>.shape() = map { it.id to it.items.map { n -> n.key } }

    @Test
    fun `没有离开的、标识都没变——原样用系统的列表`() {
        val fresh = listOf(g("g:X", "A", "B"), g("n:C", "C"))
        assertSame(fresh, ShadeListMerge.merge(listOf(g("g:X", "A", "B")), fresh, emptySet()))
    }

    @Test
    fun `按标题行的叉后系统立刻整组拿掉——这一组留在原位走完动画`() {
        val shown = listOf(g("n:P", "P"), g("g:X", "A", "B"), g("n:Q", "Q"))
        val fresh = listOf(g("n:P", "P"), g("n:Q", "Q"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("A", "B"))
        assertEquals(listOf("n:P" to listOf("P"), "g:X" to listOf("A", "B"), "n:Q" to listOf("Q")), out.shape())
    }

    @Test
    fun `系统逐条拿掉时剩下一条被拆成单条——不另起一块,整组仍按原样离开`() {
        val shown = listOf(g("g:X", "A", "B", "C"), g("n:Q", "Q"))
        val fresh = listOf(g("n:C", "C"), g("n:Q", "Q"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("A", "B", "C"))
        assertEquals(listOf("g:X" to listOf("A", "B", "C"), "n:Q" to listOf("Q")), out.shape())
    }

    @Test
    fun `两条里划掉一条、系统把剩下的拆成单条——沿用原来的组标识,划掉的留在原位`() {
        val shown = listOf(g("g:X", "A", "B"))
        val fresh = listOf(g("n:A", "A"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("B"))
        assertEquals(listOf("g:X" to listOf("A", "B")), out.shape())
        // 动画走完以后:只剩 A,标识仍是原来那一组
        assertEquals(listOf("g:X" to listOf("A")), ShadeListMerge.merge(out, fresh, emptySet()).shape())
    }

    @Test
    fun `单条来了第二条被并成一组——沿用单条原来的标识`() {
        val shown = listOf(g("n:A", "A"), g("n:Q", "Q"))
        val fresh = listOf(g("g:X", "N", "A"), g("n:Q", "Q"))
        val out = ShadeListMerge.merge(shown, fresh, emptySet())
        assertEquals(listOf("n:A" to listOf("N", "A"), "n:Q" to listOf("Q")), out.shape())
    }

    @Test
    fun `组中间一张撤回——留在原来前后之间`() {
        val shown = listOf(g("g:X", "A", "B", "C"))
        val fresh = listOf(g("g:X", "A", "C"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("B"))
        assertEquals(listOf("g:X" to listOf("A", "B", "C")), out.shape())
    }

    @Test
    fun `正在离开、系统还没拿掉——只出现一次,在原来的位置`() {
        val shown = listOf(g("g:X", "A", "B"))
        val fresh = listOf(g("g:X", "B", "A"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("B"))
        assertEquals(listOf("g:X" to listOf("A", "B")), out.shape())
    }

    @Test
    fun `最上面一组整组离开——放回最上面`() {
        val shown = listOf(g("g:X", "A"), g("n:P", "P"))
        val fresh = listOf(g("n:P", "P"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("A"))
        assertEquals(listOf("g:X" to listOf("A"), "n:P" to listOf("P")), out.shape())
    }

    @Test
    fun `已经全部藏起的组不拿来接标识`() {
        val shown = listOf(g("g:Y", "C"), g("n:P", "P"))
        val fresh = listOf(g("n:C", "C"), g("n:P", "P"))
        val out = ShadeListMerge.merge(shown, fresh, emptySet(), visible = { it.key != "C" })
        assertEquals(listOf("n:C" to listOf("C"), "n:P" to listOf("P")), out.shape())
    }

    @Test
    fun `一组拆成两条单条——只有一条接上原来的标识,标识不重复`() {
        val shown = listOf(g("g:X", "A", "B"))
        val fresh = listOf(g("n:A", "A"), g("n:B", "B"))
        val out = ShadeListMerge.merge(shown, fresh, emptySet())
        assertEquals(listOf("g:X" to listOf("A"), "n:B" to listOf("B")), out.shape())
    }

    @Test
    fun `合并结果里组标识和通知标识都不重复`() {
        val shown = listOf(g("g:X", "A", "B", "C"), g("n:P", "P"), g("g:Y", "D", "E"))
        val fresh = listOf(g("n:E", "E"), g("n:C", "C"), g("n:P", "P"), g("n:F", "F"))
        val out = ShadeListMerge.merge(shown, fresh, setOf("A", "B", "D"))
        val ids = out.map { it.id }
        val keys = out.flatMap { it.items.map { n -> n.key } }
        assertEquals(ids.distinct(), ids)
        assertEquals(keys.distinct(), keys)
        assertEquals(setOf("A", "B", "C", "D", "E", "F", "P"), keys.toSet())
    }
}
