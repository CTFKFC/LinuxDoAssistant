package com.ydm.linuxdo

import android.app.Application
import com.ydm.linuxdo.core.automation.AutomationController
import com.ydm.linuxdo.core.browser.DohProvider
import com.ydm.linuxdo.core.browser.DohRequestHandler
import com.ydm.linuxdo.core.browser.DohResolver
import com.ydm.linuxdo.core.browser.WebViewHost
import com.ydm.linuxdo.core.crash.CrashReporter
import com.ydm.linuxdo.core.data.AppDataContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LinuxDoApp : Application() {

    lateinit var dohResolver: DohResolver
        private set

    lateinit var dohHandler: DohRequestHandler
        private set

    lateinit var data: AppDataContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 崩溃兜底要最先装：后面任何初始化崩了都能留下日志
        CrashReporter.install(this)

        dohResolver = DohResolver()
        dohHandler = DohRequestHandler(dohResolver)
        data = AppDataContainer(this)

        // ★ WebView 必须在这里初始化，不能放 Compose 的 DisposableEffect。
        //   副作用在 composition 之后才跑，冷启动若恢复到浏览器 Tab，
        //   AndroidView 工厂会先执行 → requireWebView() 抛异常闪退（v1.3.0 的崩溃）。
        WebViewHost.initialize(this, dohHandler)

        // 模板来源换成数据库实现（替换掉 P3 阶段的内置种子实现）
        AutomationController.templateProvider = data.templateStore

        // DoH 设置变化时实时应用，不需要重启 App
        scope.launch {
            data.settings
                .map { Triple(it.dohEnabled, it.dohProviderId, it.dohCustomUrl) }
                .distinctUntilChanged()
                .collect { (enabled, id, customUrl) ->
                    dohResolver.configure(
                        when {
                            !enabled -> null
                            id == "custom" && customUrl.isNotBlank() -> DohProvider.custom(customUrl)
                            else -> DohProvider.byId(id) ?: DohProvider.CLOUDFLARE
                        },
                    )
                }
        }
    }
}
