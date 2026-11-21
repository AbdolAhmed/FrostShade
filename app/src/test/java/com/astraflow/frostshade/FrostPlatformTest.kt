package com.astraflow.frostshade

import com.astraisland.events.FluidCloudVariants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostPlatformTest {
    @Test
    fun onlyColorOs15And16CanLoadHooks() {
        assertTrue(FrostPlatform.supports(FluidCloudVariants.forRelease("V15.0.0")?.major))
        assertTrue(FrostPlatform.supports(FluidCloudVariants.forRelease("V16.0.0")?.major))
        assertFalse(FrostPlatform.supports(FluidCloudVariants.forRelease("V17.0.0")?.major))
        assertFalse(FrostPlatform.supports(FluidCloudVariants.forRelease("V18.0.0")?.major))
        assertFalse(FrostPlatform.supports(FluidCloudVariants.forRelease("")?.major))
    }
}
