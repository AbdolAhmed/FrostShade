package com.astraflow.frostshade

import android.app.Application
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FrostApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal lateinit var settings: FrostSettingsStore
        private set
    private var service: XposedService? = null

    override fun onCreate() {
        super.onCreate()
        settings = FrostSettingsStore(getSharedPreferences(FrostPreferences.GROUP, MODE_PRIVATE), scope)
        XposedServiceHelper.registerListener(this)
    }

    @Synchronized
    override fun onServiceBind(service: XposedService) {
        this.service = service
        scope.launch {
            if (!FrostPlatform.supports(FrostPlatform.currentMajor())) return@launch
            val remote = runCatching { service.getRemotePreferences(FrostPreferences.GROUP) }
                .onFailure { Log.w("FrostShade", "Could not open LSPosed settings", it) }
                .getOrNull()
            synchronized(this@FrostApplication) {
                if (this@FrostApplication.service === service) settings.connect(remote)
            }
        }
    }

    @Synchronized
    override fun onServiceDied(service: XposedService) {
        if (this.service !== service) return
        this.service = null
        settings.connect(null)
    }
}
