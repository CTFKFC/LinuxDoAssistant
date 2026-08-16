package com.ydm.linuxdo.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 给悬浮窗里的 ComposeView 提供三件套 Owner。
 *
 * ## 为什么需要
 *
 * `ComposeView` 依赖 `ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner` /
 * `ViewTreeViewModelStoreOwner`。Activity 里这些由框架自动提供，
 * 但**通过 WindowManager 直接 addView 的悬浮窗没有宿主**，不手动挂会抛
 * `IllegalStateException: ViewTreeLifecycleOwner not found from DecorView`。
 *
 * ## ⚠️ 这个类是一次性的（v1.1.0 崩溃的根因）
 *
 * `SavedStateRegistryController.performRestore()` **一个实例只能调用一次**，
 * 第二次会抛 `IllegalStateException: SavedStateRegistry was already restored.`。
 * 而且 Lifecycle 到 DESTROYED 之后不能再回到 CREATED。
 *
 * v1.1.0 把这个类当成可复用的成员变量，每次展开面板都调一次 [onCreate]，
 * 结果**悬浮窗第二次点击必崩**（用户实测：Xiaomi M2004J7AC / Android 12）。
 *
 * 现在的约定：**每开一个窗口就 new 一个**，关掉即作废，绝不复用。
 * [onCreate] 内部再加一道幂等保护，防止误用。
 */
class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    private var restored = false
    private var destroyed = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** 幂等：重复调用不会再次 performRestore，也就不会崩 */
    fun onCreate() {
        if (destroyed) return
        if (!restored) {
            savedStateController.performRestore(null)
            restored = true
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        if (destroyed) return
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        if (destroyed) return
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        if (destroyed) return
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onStop() {
        if (destroyed) return
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    /** 一次完整的 CREATED → STARTED → RESUMED */
    fun bringUp() {
        onCreate()
        onStart()
        onResume()
    }
}
