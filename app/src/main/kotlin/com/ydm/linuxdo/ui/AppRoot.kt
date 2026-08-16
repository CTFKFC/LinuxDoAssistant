package com.ydm.linuxdo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ydm.linuxdo.LinuxDoApp
import com.ydm.linuxdo.core.browser.WebViewHost
import com.ydm.linuxdo.core.designsystem.AppIcons
import com.ydm.linuxdo.core.designsystem.AuroraBackground
import com.ydm.linuxdo.core.designsystem.GlassBottomBar
import com.ydm.linuxdo.core.designsystem.GlassNavItem
import com.ydm.linuxdo.core.designsystem.LinuxDoTheme
import com.ydm.linuxdo.service.AutomationService
import com.ydm.linuxdo.ui.browser.BrowserChrome
import com.ydm.linuxdo.ui.dashboard.DashboardScreen
import com.ydm.linuxdo.ui.dashboard.DashboardViewModel
import com.ydm.linuxdo.ui.settings.SettingsScreen
import com.ydm.linuxdo.ui.tasks.TasksScreen
import com.ydm.linuxdo.ui.templates.TemplatesScreen

/**
 * 应用根容器。
 *
 * ## ★ WebView 的承载方式（v1.4.0 彻底重构，前几版都错了）
 *
 * 自动化脚本靠在 WebView 里执行 JS 驱动页面。要让它跑得动，WebView 必须同时满足：
 *
 * 1. **一直挂在某个父容器上** —— 没有父容器，Android 冻结渲染与 JS 计时器
 * 2. **保持真实的全屏视口** —— 这一条 v1.3.0 漏了，代价惨重
 *
 * v1.3.0 曾把 WebView 塞进一个 **1dp 的隐形容器**，以为"挂着就行"。
 * 但 1dp 容器 = **1 像素高的视口**，`window.scrollBy(0, 800)` 在里面根本滚不动，
 * 页面永远停在第一屏 → 楼层计数器不更新 → 日志刷屏"找不到楼层计数器"。
 *
 * 现在的做法：**WebView 永远以全屏尺寸躺在最底层，从不移动、从不缩放、从不销毁**。
 * 切到别的标签页时，用不透明的 [AuroraBackground] 盖在它上面即可——
 * 被遮挡的 View 依然正常布局与滚动，脚本照跑。
 *
 * 浏览器标签页只是"把盖子掀开"，并在 WebView 之上浮一层地址栏。
 */
@Composable
fun AppRoot() {
    LinuxDoTheme {
        val context = LocalContext.current
        val app = context.applicationContext as LinuxDoApp

        var selectedTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD.key) }
        var showTemplates by remember { mutableStateOf(false) }

        val dashboardViewModel: DashboardViewModel = viewModel()
        val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
        val settings by app.data.settings.collectAsStateWithLifecycle()

        dashboardViewModel.runConfig = settings.toRunConfig()

        val isBrowser = selectedTab == AppTab.BROWSER.key && !showTemplates

        var bottomBarHeight by remember { mutableStateOf(0.dp) }
        var barExpanded by rememberSaveable { mutableStateOf(true) }
        val density = LocalDensity.current
        val permissionGate = rememberPermissionGate()

        // 进浏览器页自动收起底栏：网页占满全屏，且收起后的胶囊在右下角，
        // 与左下角的 Cloudflare 验证框互不遮挡
        LaunchedEffect(isBrowser) {
            barExpanded = !isBrowser
        }

        Box(modifier = Modifier.fillMaxSize()) {

            // ---------------------------------------------------------
            // 第 0 层：WebView（永远全屏、永远存在）
            // ---------------------------------------------------------
            AndroidView(
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).also { holder ->
                        attachWebView(holder)
                    }
                },
                update = { holder ->
                    // 悬浮窗可能把 WebView 借走了，回到前台时收回来
                    val wv = WebViewHost.webViewOrNull()
                    if (wv != null && wv.parent !== holder) {
                        attachWebView(holder)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // ---------------------------------------------------------
            // 第 1 层：非浏览器页 → 不透明背景盖住 WebView
            // ---------------------------------------------------------
            if (!isBrowser) {
                AuroraBackground(animated = settings.animatedBackground) {
                    if (showTemplates) {
                        TemplatesScreen(onBack = { showTemplates = false })
                    } else {
                        when (selectedTab) {
                            AppTab.DASHBOARD.key -> DashboardScreen(
                                state = dashboardState,
                                bottomInset = bottomBarHeight,
                                onStart = {
                                    permissionGate.ensureThen {
                                        if (settings.overlayEnabled) {
                                            AutomationService.start(context)
                                        }
                                        dashboardViewModel.start()
                                    }
                                },
                                onStop = {
                                    dashboardViewModel.stop()
                                    AutomationService.stop(context)
                                },
                            )

                            AppTab.TASKS.key -> TasksScreen()

                            AppTab.SETTINGS.key -> SettingsScreen(
                                onOpenTemplates = { showTemplates = true },
                            )
                        }
                    }
                }
            } else {
                // 浏览器页：只在 WebView 之上浮一层地址栏与提示条
                BrowserChrome(loggedIn = dashboardState.loggedIn)
            }

            // ---------------------------------------------------------
            // 第 2 层：底栏
            // ---------------------------------------------------------
            if (!showTemplates) {
                GlassBottomBar(
                    items = AppTab.entries.map { it.toNavItem() },
                    selectedKey = selectedTab,
                    onSelect = { selectedTab = it },
                    expanded = barExpanded,
                    onToggleExpanded = { barExpanded = !barExpanded },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .onSizeChanged { size ->
                            with(density) { bottomBarHeight = size.height.toDp() }
                        },
                )
            }
        }
    }
}

/** 把单例 WebView 以**全屏尺寸**挂到容器上；未初始化时安全跳过 */
private fun attachWebView(holder: android.widget.FrameLayout) {
    val wv = WebViewHost.webViewOrNull() ?: return
    WebViewHost.detachFromParent()
    holder.addView(
        wv,
        android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
}

enum class AppTab(val key: String, val label: String) {
    DASHBOARD("dashboard", "仪表盘"),
    BROWSER("browser", "浏览器"),
    TASKS("tasks", "任务"),
    SETTINGS("settings", "设置"),
    ;

    fun toNavItem(): GlassNavItem = GlassNavItem(
        key = key,
        label = label,
        icon = when (this) {
            DASHBOARD -> AppIcons.Dashboard
            BROWSER -> AppIcons.Public
            TASKS -> AppIcons.Bolt
            SETTINGS -> AppIcons.Settings
        },
    )
}
