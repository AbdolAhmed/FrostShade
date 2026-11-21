package com.astraflow.tool.hook.liquidnotif.ui

import com.astraflow.tool.config.LiquidNotificationDefaults as D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 液态玻璃通知的「卡片宽度」「卡片大小」(公开反馈 #33):默认都是 100%,卡片和原来一模一样;存下的数按范围收回;
 * 随本页恢复默认、随配置备份。
 */
class LiquidCardSizeSettingsTest {

    private fun read(width: Int? = null, scale: Int? = null) = LiquidSettings.read(
        getInt = { key, default ->
            when (key) {
                D.CARD_WIDTH.key -> width ?: default
                D.CARD_SCALE.key -> scale ?: default
                else -> default
            }
        },
        getBoolean = { _, default -> default },
    )

    @Test fun theDefaultsKeepTheOriginalCards() {
        val s = read()
        assertEquals(100, s.cardWidth)
        assertEquals(100, s.cardScale)
        assertEquals(LiquidSettings(), s)
    }

    @Test fun storedValuesAreReadAndKeptInRange() {
        assertEquals(85, read(width = 85).cardWidth)
        assertEquals(80, read(scale = 80).cardScale)
        assertEquals("最窄 70%", 70, read(width = 40).cardWidth)
        assertEquals("最大 100%,不放大", 100, read(scale = 130).cardScale)
    }

    @Test fun bothAreListedAsIntegerSettings() {
        assertTrue(D.INTS.containsAll(listOf(D.CARD_WIDTH, D.CARD_SCALE)))
    }
}
