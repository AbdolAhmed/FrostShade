package com.astraflow.tool.hook.liquidcc

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/**
 * 控制中心开着的开关填什么颜色(公开反馈 #62「磁贴开关时颜色强制变蓝」):OPPO 打开开关时用弹簧动画把图标从白色
 * 逐帧变成彩色,过渡途中不按图标颜色判断,彩色图标样式下一直是浅色底,不先闪一下主题色;颜色停下后仍按图标颜色填。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class CcGlassActiveFillTest {

    private val blue = 0xFF247CFF.toInt()
    private val active = intArrayOf(android.R.attr.state_activated)
    private val surfaceColor = CcGlassDrawable::class.java
        .getDeclaredMethod("surfaceColor", Float::class.javaPrimitiveType)
        .apply { isAccessible = true }

    @Before fun setup() {
        CcGlassEnv.attrActive = android.R.attr.state_activated
        CcGlassEnv.accent = blue
    }

    @After fun restore() {
        CcGlassEnv.attrActive = 0
        CcGlassEnv.colorfulIcons = true
    }

    private fun tile(iconColor: Int): Pair<CcGlassDrawable, ImageView> {
        val icon = ImageView(RuntimeEnvironment.getApplication()).apply { imageTintList = ColorStateList.valueOf(iconColor) }
        val glass = CcGlassDrawable(1f).apply { glyphView = WeakReference(icon) }
        return glass to icon
    }

    private fun CcGlassDrawable.fill(): Int = surfaceColor.invoke(this, 1f) as Int
    private fun Int.isTheme() = (this and 0xFFFFFF) == (blue and 0xFFFFFF)
    private fun Int.isLight() = (this and 0xFFFFFF) == 0xFFFFFF
    private fun frame(ms: Long = 16) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

    @Test fun turningOnInTheColorfulStyleNeverFlashesTheThemeColour() {
        CcGlassEnv.colorfulIcons = true
        val (glass, icon) = tile(Color.WHITE)
        glass.checkGlyph()
        frame(500)
        glass.state = active
        // 图标从白色(关着)逐帧变成蓝色(开着)
        for (c in listOf(Color.WHITE, Color.WHITE, 0xFFF0F5FF.toInt(), 0xFFC8DCFF.toInt(), 0xFF8CB4FF.toInt(), 0xFF4A90FF.toInt(), blue)) {
            icon.imageTintList = ColorStateList.valueOf(c)
            glass.checkGlyph()
            assertTrue("过渡中按彩色图标样式填浅色", glass.fill().isLight())
            frame()
        }
        frame(300)
        glass.checkGlyph()
        assertTrue("颜色停下后按彩色图标填浅色", glass.fill().isLight())
    }

    /** OPPO 的开关图标是 Lottie 动画视图:打开开关时先播动画,期间着色停在旧的白色,播完才换成彩色 */
    class LottieLikeIcon(context: android.content.Context) : ImageView(context) {
        var playing = false
        fun isAnimating(): Boolean = playing
    }

    @Test fun whileTheSwitchAnimationPlaysTheOldWhiteIconDoesNotDecide() {
        CcGlassEnv.colorfulIcons = true
        val icon = LottieLikeIcon(RuntimeEnvironment.getApplication()).apply { imageTintList = ColorStateList.valueOf(Color.WHITE) }
        val glass = CcGlassDrawable(1f).apply { glyphView = WeakReference(icon) }
        glass.checkGlyph()
        frame(500)
        glass.state = active
        icon.playing = true
        // 动画播 0.6 秒,着色一直停在白色、不变化
        repeat(40) {
            glass.checkGlyph()
            assertTrue("动画期间按彩色图标样式填浅色,不闪主题色", glass.fill().isLight())
            frame()
        }
        icon.playing = false
        icon.imageTintList = ColorStateList.valueOf(blue)
        repeat(20) { glass.checkGlyph(); assertTrue(glass.fill().isLight()); frame() }
    }

    @Test fun theNextTimeTheSwitchRemembersHowItLookedWhenOn() {
        // 系统样式判断失误(按普通样式)时,第一次打开等动画播完以后照图标填浅色;记下来以后再打开,动画期间也是浅色
        CcGlassEnv.colorfulIcons = false
        val icon = LottieLikeIcon(RuntimeEnvironment.getApplication()).apply { imageTintList = ColorStateList.valueOf(blue) }
        val glass = CcGlassDrawable(1f).apply { glyphView = WeakReference(icon) }
        glass.state = active
        glass.checkGlyph()
        frame(300)
        glass.checkGlyph()
        assertTrue(glass.fill().isLight())
        glass.state = intArrayOf()
        icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        glass.checkGlyph()
        frame(500)
        glass.checkGlyph()
        glass.state = active
        icon.playing = true
        repeat(40) { glass.checkGlyph(); assertTrue("记着这个开关开着时是浅色", glass.fill().isLight()); frame() }
    }

    @Test fun thePlainStyleFillsTheThemeColourFromTheFirstFrame() {
        CcGlassEnv.colorfulIcons = false
        val (glass, _) = tile(Color.WHITE)
        glass.checkGlyph()
        glass.state = active
        glass.checkGlyph()
        assertTrue(glass.fill().isTheme())
        frame(300)
        glass.checkGlyph()
        assertTrue(glass.fill().isTheme())
    }

    @Test fun anIconThatHasSettledStillDecidesTheFill() {
        // 判断成彩色图标样式,但图标停下后是白色(例如没认出来的主题):按图标填主题色,白色图标不落在浅色底上
        CcGlassEnv.colorfulIcons = true
        val (glass, _) = tile(Color.WHITE)
        glass.state = active
        glass.checkGlyph()
        frame(300)
        glass.checkGlyph()
        assertTrue(glass.fill().isTheme())
    }
}
