package com.astraflow.tool.hook.core

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

internal object HookHelper {
    var xposedInterface: XposedModule? = null
        private set

    fun init(module: XposedModule) {
        xposedInterface = module
    }

    fun hookMethod(
        method: Method,
        beforeCallback: ((Chain) -> Any?)? = null,
        afterCallback: ((Chain, Any?) -> Any?)? = null,
    ) = hook(method, beforeCallback, afterCallback)

    fun hookConstructor(
        constructor: Constructor<*>,
        afterCallback: (Chain, Any?) -> Any?,
    ) = hook(constructor, null, afterCallback)

    private fun hook(
        target: Executable,
        beforeCallback: ((Chain) -> Any?)?,
        afterCallback: ((Chain, Any?) -> Any?)?,
    ) {
        val module = checkNotNull(xposedInterface)
        runCatching {
            module.hook(target)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(Hooker { chain ->
                    val replacement = beforeCallback?.invoke(chain)
                    if (replacement != null) {
                        if (replacement == Unit) null else replacement
                    } else {
                        val result = chain.proceed()
                        if (afterCallback == null) result else afterCallback(chain, result)
                    }
                })
        }.onFailure { module.log(Log.WARN, "FrostShade", "Hook failed for $target", it) }
    }

    fun getAppContext(): Application? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
    }.getOrNull()
}
