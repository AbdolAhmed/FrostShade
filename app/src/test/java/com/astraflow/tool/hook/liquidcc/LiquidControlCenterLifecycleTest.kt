package com.astraflow.tool.hook.liquidcc

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class LiquidControlCenterLifecycleTest {
    @After
    fun restoreOriginals() = LiquidControlCenter.restoreAll()

    @Test
    fun closingShadeRestoresGlassTilesAndPreservesNewSystemBackgrounds() {
        val context = RuntimeEnvironment.getApplication()
        val replaced = View(context)
        val changedBySystem = View(context)
        val original = ColorDrawable(Color.BLACK)
        val systemReplacement = ColorDrawable(Color.BLUE)
        val backgrounds = savedBackgrounds()
        backgrounds[replaced] = original
        backgrounds[changedBySystem] = original
        replaced.background = CcGlassDrawable(context.resources.displayMetrics.density)
        changedBySystem.background = systemReplacement

        LiquidControlCenter.onShadeClosed()

        assertSame(original, replaced.background)
        assertSame(systemReplacement, changedBySystem.background)
    }

    @Suppress("UNCHECKED_CAST")
    private fun savedBackgrounds(): MutableMap<View, Drawable?> =
        LiquidControlCenter::class.java.getDeclaredField("originals").apply { isAccessible = true }
            .get(LiquidControlCenter) as MutableMap<View, Drawable?>
}
