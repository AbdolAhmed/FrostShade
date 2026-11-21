package com.astraflow.tool.hook.liquidnotif.ui

import com.astraflow.tool.hook.liquidnotif.model.LiquidGroup
import com.astraflow.tool.hook.liquidnotif.model.LiquidNotification
import com.astraflow.tool.hook.notifcommon.NotificationArrange
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidArrangeTest {
    private val settings = LiquidSettings(grouping = NotificationArrange.GROUP_APP)
    private val input = listOf(
        notification("message-1", "com.example.messages", 10),
        notification("message-2", "com.example.messages", 20),
        notification("reminder", "com.example.calendar", 30),
        notification("message-3", "com.example.messages", 40),
        notification("message-4", "com.example.messages", 50),
    )

    @Test
    fun separatedRunsOfTheSameAppHaveDistinctKeys() {
        val stacks = LiquidArrange.arrange(input, settings)

        assertEquals(
            listOf(listOf("message-2", "message-1"), listOf("reminder"), listOf("message-4", "message-3")),
            stacks.map { stack -> stack.items.map { it.key } },
        )
        assertEquals(stacks.size, stacks.map { it.id }.toSet().size)
    }

    @Test
    fun switchingFromAllNotificationsToSameAppKeepsDistinctKeysAfterRefresh() {
        val state = LiquidUiState()
        val allNotifications = settings.copy(grouping = NotificationArrange.GROUP_ALL)
        state.settings = allNotifications
        state.shown = true
        state.update(LiquidArrange.arrange(input, allNotifications), animate = false, visible = emptySet())
        assertEquals(1, state.groups.size)

        state.shown = false
        state.settings = settings
        state.shown = true
        val stacks = LiquidArrange.arrange(input, settings)
        state.update(stacks, animate = false, visible = emptySet())
        state.update(stacks, animate = true, visible = state.groups.map { it.id }.toSet())

        assertEquals(3, state.groups.size)
        assertEquals(3, state.groups.map { it.id }.toSet().size)
        assertEquals(input.flatMap { it.items }.map { it.key }.toSet(), state.groups.flatMap { it.items }.map { it.key }.toSet())
    }

    @Test
    fun sortingAndArrivalKeepTheOpenStackDistinct() {
        val state = LiquidUiState()
        val stacks = LiquidArrange.arrange(input, settings)
        state.settings = settings
        state.update(stacks, animate = false, visible = emptySet())
        val openStack = stacks.first().id
        state.expanded = setOf(openStack)

        val sortedSettings = settings.copy(sortEnabled = true)
        state.settings = sortedSettings
        val updated = LiquidArrange.arrange(
            listOf(notification("message-5", "com.example.messages", 60)) + input,
            sortedSettings,
        )
        state.update(updated, animate = true, visible = stacks.map { it.id }.toSet())

        assertEquals(state.groups.size, state.groups.map { it.id }.toSet().size)
        assertEquals(setOf(openStack), state.expanded)
        assertEquals(
            listOf("message-5", "message-2", "message-1"),
            state.groups.single { it.id == openStack }.items.map { it.key },
        )
    }

    private fun notification(key: String, packageName: String, time: Long): LiquidGroup {
        val item = LiquidNotification(
            key = key,
            packageName = packageName,
            userId = 0,
            channelId = null,
            appLabel = packageName,
            appIcon = null,
            title = key,
            text = "",
            postTime = time,
        )
        return LiquidGroup("n:$key", packageName, listOf(item))
    }
}
