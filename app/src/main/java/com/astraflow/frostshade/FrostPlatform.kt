package com.astraflow.frostshade

import com.astraisland.events.FluidCloudVariants

internal object FrostPlatform {
    fun currentMajor(): Int? = FluidCloudVariants.current()?.major

    fun supports(major: Int?): Boolean = major == 15 || major == 16
}
