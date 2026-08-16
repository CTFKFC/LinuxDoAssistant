package com.ydm.linuxdo.core.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用级数据容器。
 *
 * 在 Application 里创建一次，所有页面共享同一份 [SettingsStore] / [TemplateStore]。
 * 没有引入 Hilt —— 依赖关系就这么两三个，手工装配比框架更直白，
 * 也省掉了 Hilt 的注解处理器（在这台机器上编译代价不小）。
 */
class AppDataContainer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore = SettingsStore(context)
    val templateStore = TemplateStore(context)
    val runHistoryStore = RunHistoryStore(context)
    val sessionStore = SessionStore(context)

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    init {
        scope.launch {
            templateStore.load()
            runHistoryStore.load()
            sessionStore.load()
        }
    }
}
