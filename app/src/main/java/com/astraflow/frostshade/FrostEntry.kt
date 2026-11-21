package com.astraflow.frostshade

import android.util.Log
import com.astraflow.tool.hook.core.HookHelper
import com.astraflow.tool.hook.liquidcc.LiquidControlCenterHook
import com.astraflow.tool.hook.liquidnotif.LiquidNotificationHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class FrostEntry : XposedModule() {
    private var processName: String? = null
    private var registered = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (processName != SYSTEM_UI || param.packageName != SYSTEM_UI || registered) return
        val major = FrostPlatform.currentMajor()
        if (!FrostPlatform.supports(major)) {
            log(Log.INFO, TAG, "SystemUI skipped for ColorOS ${major ?: "unknown"}")
            return
        }
        registered = true
        HookHelper.init(this)
        FrostPreferences.connect { getRemotePreferences(FrostPreferences.GROUP) }
        val loader = param.defaultClassLoader
        runCatching { LiquidNotificationHook.register(loader) }
            .onFailure { log(Log.ERROR, TAG, "Notification shade registration failed", it) }
        runCatching { LiquidControlCenterHook.register(loader) }
            .onFailure { log(Log.ERROR, TAG, "Control center registration failed", it) }
        log(Log.INFO, TAG, "SystemUI initialized for ColorOS $major")
    }

    private companion object {
        const val TAG = "FrostShade"
        const val SYSTEM_UI = "com.android.systemui"
    }
}
