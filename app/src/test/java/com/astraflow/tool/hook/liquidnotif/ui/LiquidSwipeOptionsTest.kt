package com.astraflow.tool.hook.liquidnotif.ui

import com.astraflow.tool.config.LiquidNotificationPins
import com.astraflow.tool.config.LiquidNotificationDefaults as D
import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification
import com.astraflow.tool.hook.liquidnotif.ui.SwipeRule.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 液态玻璃通知的「置顶与左滑选项」(公开反馈 #80「通知中心长按卡片可以选择消息置顶……只有往一侧滑才能消除卡片，另一侧可以选择」):
 * 默认关,和原来一样左右滑都清除;打开后只有向右滑清除,向左滑停住露出按钮;置顶的组排在最前面,
 * 系统拿掉、关掉设置后置顶清掉。
 */
class LiquidSwipeOptionsTest {

    private fun n(key: String) = LiquidNotification(
        key = key, packageName = "p", userId = 0, channelId = null, appLabel = "应用", appIcon = null,
        title = key, text = "", postTime = 1L,
    )

    private fun g(id: String, vararg keys: String) = LiquidGroup(id, "应用", keys.map(::n))

    private fun List<LiquidGroup>.ids() = map { it.id }

    private val width = 1000f
    private val options = 300f

    @Test fun withoutOptionsBothDirectionsDismissAsBefore() {
        assertEquals(Outcome.DISMISS, SwipeRule.decide(400f, 0f, width, 0f))
        assertEquals(Outcome.DISMISS, SwipeRule.decide(-400f, 0f, width, 0f))
        assertEquals("顺着方向甩", Outcome.DISMISS, SwipeRule.decide(-50f, -2000f, width, 0f))
        assertEquals("没滑过三分之一、没甩:弹回", Outcome.REST, SwipeRule.decide(-200f, 0f, width, 0f))
        assertEquals("往回甩不清除", Outcome.REST, SwipeRule.decide(-200f, 2000f, width, 0f))
        assertEquals("没有左滑选项时不拖出阻力", -500f, SwipeRule.resist(-500f, 0f), 0f)
    }

    @Test fun withOptionsOnlyTheRightSwipeDismissesAndTheLeftSwipeReveals() {
        assertEquals(Outcome.DISMISS, SwipeRule.decide(400f, 0f, width, options))
        assertEquals(Outcome.DISMISS, SwipeRule.decide(50f, 2000f, width, options))
        assertEquals("向左滑得再远也不清除", Outcome.REVEAL, SwipeRule.decide(-900f, 0f, width, options))
        assertEquals("向左甩露出", Outcome.REVEAL, SwipeRule.decide(-40f, -900f, width, options))
        assertEquals("过按钮宽度的一半停住", Outcome.REVEAL, SwipeRule.decide(-160f, 0f, width, options))
        assertEquals("不到一半收回", Outcome.REST, SwipeRule.decide(-140f, 0f, width, options))
        assertEquals("露出后往右甩收回", Outcome.REST, SwipeRule.decide(-280f, 900f, width, options))
        assertEquals("拖到按钮宽度以内不费劲", -200f, SwipeRule.resist(-200f, options), 0f)
        assertEquals("拖过按钮宽度后越拖越费劲", -325f, SwipeRule.resist(-400f, options), 0.001f)
    }

    @Test fun pinnedGroupsComeFirstLatestPinFirst() {
        val list = listOf(g("n:A", "A"), g("g:X", "B", "C"), g("n:D", "D"), g("n:E", "E"))
        assertEquals("没有置顶时照原来的先后", list, ShadeListMerge.pinnedFirst(list, emptyMap()))
        val out = ShadeListMerge.pinnedFirst(list, mapOf("C" to 10L, "E" to 20L))
        assertEquals("组里有一条置顶就算置顶,后置顶的在前,其余照原来的先后",
            listOf("n:E", "g:X", "n:A", "n:D"), out.ids())
    }

    private fun state(swipeOptions: Boolean) = LiquidUiState().apply { settings = LiquidSettings(swipeOptions = swipeOptions) }

    @Test fun pinningReordersTheListAndUnpinningRestoresTheSystemOrder() {
        val s = state(true)
        val system = listOf(g("n:A", "A"), g("n:B", "B"), g("n:C", "C"))
        s.update(system, animate = false, visible = emptySet())
        s.togglePin(listOf(n("C")), now = 1L)
        assertEquals(listOf("n:C", "n:A", "n:B"), s.groups.ids())
        assertTrue(s.isPinned(n("C")))
        // 系统再给一次列表:置顶照旧排在前面
        s.update(system, animate = false, visible = emptySet())
        assertEquals(listOf("n:C", "n:A", "n:B"), s.groups.ids())
        // 取消置顶:回到系统的先后
        s.togglePin(listOf(n("C")), now = 2L)
        assertEquals(listOf("n:A", "n:B", "n:C"), s.groups.ids())
        assertFalse(s.isPinned(n("C")))
    }

    @Test fun aGroupButtonPinsTheWholeGroupAndPinsGoAwayWithTheNotification() {
        val s = state(true)
        val system = listOf(g("n:A", "A"), g("g:X", "B", "C"))
        s.update(system, animate = false, visible = emptySet())
        s.togglePin(listOf(n("B"), n("C")), now = 1L)
        assertEquals(listOf("g:X", "n:A"), s.groups.ids())
        // 系统拿掉了 C:B 还置顶着,C 的记录清掉
        s.update(listOf(g("n:A", "A"), g("g:X", "B")), animate = false, visible = emptySet())
        assertEquals(setOf("B"), s.pinned.keys)
        assertEquals(listOf("g:X", "n:A"), s.groups.ids())
    }

    @Test fun pinsDoNothingAndAreClearedWhenTheSettingIsOff() {
        val s = state(false)
        val system = listOf(g("n:A", "A"), g("n:B", "B"))
        s.update(system, animate = false, visible = emptySet())
        s.togglePin(listOf(n("B")), now = 1L)
        assertTrue("关着时不能置顶", s.pinned.isEmpty())
        assertEquals(listOf("n:A", "n:B"), s.groups.ids())

        val on = state(true)
        on.update(system, animate = false, visible = emptySet())
        on.togglePin(listOf(n("B")), now = 1L)
        on.settings = LiquidSettings(swipeOptions = false)
        on.update(system, animate = false, visible = emptySet())
        assertTrue("关掉设置后置顶清掉", on.pinned.isEmpty())
        assertEquals(listOf("n:A", "n:B"), on.groups.ids())
    }

    @Test fun collapsingTheShadeTakesBackTheRevealedOptions() {
        val s = state(true)
        s.revealed = "c:A"
        s.reset()
        assertEquals(null, s.revealed)
    }

    @Test fun restartingRestoresPinsAndTheirOrderWithoutWritingOnEveryListUpdate() {
        var saved = ""
        var writes = 0
        fun session() = LiquidUiState(savePins = { saved = LiquidNotificationPins.encode(it); writes++ })
            .apply { settings = LiquidSettings(swipeOptions = true) }
        val system = listOf(g("n:A", "A"), g("n:B", "B"), g("n:C", "C"))
        val first = session()
        first.update(system, animate = false, visible = emptySet())
        first.togglePin(listOf(n("B")), now = 10L)
        first.togglePin(listOf(n("C")), now = 20L)
        first.reset()

        val restarted = session()
        // 重启后通知列表可能先到，恢复记录时也要立即重排。
        restarted.update(system, animate = false, visible = emptySet())
        restarted.restorePins { LiquidNotificationPins.decode(saved) }
        assertEquals(listOf("n:C", "n:B", "n:A"), restarted.groups.ids())
        assertEquals(mapOf("B" to 10L, "C" to 20L), restarted.pinned)
        assertTrue("内容更新不取消同一条通知的置顶", restarted.isPinned(n("C").copy(postTime = 99L)))
        restarted.update(system, animate = false, visible = emptySet())
        restarted.restorePins { error("同一次运行只读取一次保存记录") }
        assertEquals("未修改置顶时不重复保存", 2, writes)
    }

    @Test fun emptyAndPartialStartupListsDoNotErasePinsBeforeNotificationsReturn() {
        val s = state(true)
        s.restorePins { mapOf("B" to 10L, "C" to 20L) }
        s.update(emptyList(), animate = false, visible = emptySet())
        s.update(listOf(g("n:A", "A")), animate = false, visible = emptySet())
        s.update(listOf(g("n:A", "A"), g("n:B", "B")), animate = false, visible = emptySet())
        assertEquals(mapOf("B" to 10L, "C" to 20L), s.pinned)
        s.update(listOf(g("n:A", "A"), g("n:B", "B"), g("n:C", "C")), animate = false, visible = emptySet())
        assertEquals(listOf("n:C", "n:B", "n:A"), s.groups.ids())
    }

    @Test fun restoredPinsArePersistentlyRemovedAfterWithdrawalOrUnpinning() {
        var saved = LiquidNotificationPins.encode(mapOf("B" to 10L, "C" to 20L))
        fun session() = LiquidUiState(savePins = { saved = LiquidNotificationPins.encode(it) })
            .apply { settings = LiquidSettings(swipeOptions = true) }
        val s = session()
        s.restorePins { LiquidNotificationPins.decode(saved) }
        s.update(listOf(g("n:B", "B"), g("n:C", "C")), animate = false, visible = emptySet())
        s.update(listOf(g("n:B", "B")), animate = false, visible = emptySet())
        assertEquals(mapOf("B" to 10L), LiquidNotificationPins.decode(saved))
        s.togglePin(listOf(n("B")), now = 30L)
        assertTrue(LiquidNotificationPins.decode(saved).isEmpty())
        val restarted = session()
        restarted.restorePins { LiquidNotificationPins.decode(saved) }
        restarted.update(listOf(g("n:B", "B"), g("n:C", "C")), animate = false, visible = emptySet())
        assertTrue("撤回或取消的置顶在重启后不恢复", restarted.pinned.isEmpty())
    }

    @Test fun disablingOptionsClearsSavedPinsEvenWithoutANewSystemList() {
        var saved = LiquidNotificationPins.encode(mapOf("B" to 10L))
        val s = LiquidUiState(savePins = { saved = LiquidNotificationPins.encode(it) })
        s.settings = LiquidSettings(swipeOptions = true)
        s.restorePins { LiquidNotificationPins.decode(saved) }
        s.update(listOf(g("n:A", "A"), g("n:B", "B")), animate = false, visible = emptySet())
        s.settings = LiquidSettings(swipeOptions = false)
        s.restorePins { error("重新打开通知中心不能覆盖当前记录") }
        assertTrue(s.pinned.isEmpty())
        assertTrue(LiquidNotificationPins.decode(saved).isEmpty())
        assertEquals(listOf("n:A", "n:B"), s.groups.ids())

        saved = LiquidNotificationPins.encode(mapOf("C" to 20L))
        val restartedOff = LiquidUiState(savePins = { saved = LiquidNotificationPins.encode(it) })
        restartedOff.restorePins { LiquidNotificationPins.decode(saved) }
        assertTrue("重启时开关已关闭也要清掉保存记录", LiquidNotificationPins.decode(saved).isEmpty())
    }

    @Test fun savedPinsPreserveFullNotificationKeysAndIgnoreInvalidRecords() {
        val pins = mapOf("0|应用|12|消息\"\n|10001" to Long.MAX_VALUE, "10|p|12|null|1010001" to 20L)
        assertEquals(pins, LiquidNotificationPins.decode(LiquidNotificationPins.encode(pins)))
        assertTrue(LiquidNotificationPins.decode("").isEmpty())
        assertTrue(LiquidNotificationPins.decode("invalid").isEmpty())
        assertEquals(mapOf("B" to 20L), LiquidNotificationPins.decode("{\"A\":\"invalid\",\"B\":20,\"C\":-1,\"\":30}"))
    }

    @Test fun theSettingIsOffByDefaultAndCanBeRead() {
        assertFalse(D.SWIPE_OPTIONS.default)
        assertFalse(LiquidSettings().swipeOptions)
        assertTrue("随本页恢复默认", D.SWIPE_OPTIONS in D.BOOLS)
        val on = LiquidSettings.read(getInt = { _, d -> d }, getBoolean = { key, d -> if (key == D.SWIPE_OPTIONS.key) true else d })
        assertTrue(on.swipeOptions)
    }
}
