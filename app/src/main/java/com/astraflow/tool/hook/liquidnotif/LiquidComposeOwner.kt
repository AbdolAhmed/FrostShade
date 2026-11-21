package com.astraflow.tool.hook.liquidnotif

import android.view.View
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 我们画的通知挂在系统界面自己的视图里,系统界面进程里没有 Activity:界面库需要的生命周期、存储、
 * 状态保存由这里提供。系统界面自己也带着一份界面库,两份互不相认,所以重组调度也由这里单独提供,
 * 不去用窗口根视图上的那一份。
 */
internal class LiquidComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var created = false
    private var recomposer: Recomposer? = null
    private var scope: CoroutineScope? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    /** 主线程调用:界面用的重组调度,第一次调用时建立,之后一直用同一份 */
    fun compositionContext(): CompositionContext {
        recomposer?.let { return it }
        val context = AndroidUiDispatcher.CurrentThread
        val r = Recomposer(context)
        val s = CoroutineScope(context + Job())
        s.launch { r.runRecomposeAndApplyChanges() }
        recomposer = r
        scope = s
        return r
    }

    /** 挂上窗口时调用,之后一直处于运行状态 */
    fun create() {
        if (created) return
        created = true
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** 整个界面拆掉时调用,之后这份不再使用 */
    fun destroy() {
        if (created) {
            created = false
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
        recomposer?.cancel()
        scope?.cancel()
        recomposer = null
        scope = null
    }
}
