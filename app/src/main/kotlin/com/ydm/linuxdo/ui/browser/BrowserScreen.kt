package com.ydm.linuxdo.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ydm.linuxdo.core.browser.WebViewHost
import com.ydm.linuxdo.core.designsystem.AppIcons
import com.ydm.linuxdo.core.designsystem.GlassProgressBar
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.LocalIsDarkTheme
import com.ydm.linuxdo.core.designsystem.StatusColors
import kotlinx.coroutines.delay

/**
 * 浏览器页的「外壳」。
 *
 * ## 为什么这里没有 AndroidView
 *
 * WebView 由 [com.ydm.linuxdo.ui.AppRoot] 统一持有，**永远全屏躺在最底层**。
 * 这个 Composable 只负责在它上面浮一层地址栏、进度条与提示条。
 *
 * 这样改是因为 v1.3.0 的教训：那时 WebView 归 BrowserScreen 管，
 * 切走标签页 Compose 就把它 dispose 掉，脚本直接断；
 * 而且冷启动时 `AndroidView` 工厂比 `DisposableEffect` 先跑，
 * `requireWebView()` 抛异常闪退。
 *
 * 现在 WebView 的生命周期和任何一个页面都解耦了。
 */
@Composable
fun BrowserChrome(
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by WebViewHost.state.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }

    // 进浏览器页才真正加载站点；启动时不加载，避免无网络时整片白屏
    LaunchedEffect(Unit) {
        WebViewHost.loadInitialIfNeeded()
    }

    LaunchedEffect(state.url) {
        if (!editing) address = state.url
    }

    var slowLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.loadStartedAtMillis) {
        slowLoading = false
        if (state.loading) {
            delay(8_000)
            slowLoading = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.statusBarsPadding())

            // ---- 地址栏（浮在网页之上）----
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(GlassTokens.RadiusPill),
                interactive = false,
                darkTheme = LocalIsDarkTheme.current,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { WebViewHost.goBack() },
                        enabled = state.canGoBack,
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = "后退",
                            tint = if (state.canGoBack) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }

                    BasicTextField(
                        value = address,
                        onValueChange = { address = it; editing = true },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                WebViewHost.loadUrl(address)
                                editing = false
                            },
                        ),
                    )

                    IconButton(onClick = { WebViewHost.reload() }) {
                        Icon(
                            AppIcons.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (state.loading) {
                GlassProgressBar(
                    progress = state.progress / 100f,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    height = 3.dp,
                )
            }

            if (!loggedIn) {
                LoginHintBar()
            }
        }

        // ---- 错误 / 慢加载提示（居中浮层）----
        state.error?.let { err ->
            ErrorOverlay(
                message = err.friendlyMessage,
                detail = err.failingUrl,
                suggestsDoh = err.suggestsDoh,
                onRetry = { WebViewHost.reload() },
            )
        }

        if (state.error == null && state.loading && slowLoading && state.isBlank) {
            SlowLoadingHint()
        }
    }
}

@Composable
private fun LoginHintBar() {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(GlassTokens.RadiusSmall),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
        tint = StatusColors.paused.copy(alpha = 0.25f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Home,
                contentDescription = null,
                tint = StatusColors.paused,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "请在此手动登录（含人机验证），登录成功后脚本会自动接管",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    detail: String,
    suggestsDoh: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GlassTokens.RadiusLarge),
            interactive = false,
            darkTheme = LocalIsDarkTheme.current,
            tint = StatusColors.failed.copy(alpha = 0.18f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (suggestsDoh) {
                        "linux.do 的域名解析失败。可到「设置 → DNS over HTTPS」打开 DoH" +
                            "（国内网络建议选阿里或 DNSPod），或确认代理已开启。"
                    } else {
                        "连不上 linux.do。请检查网络或代理是否正常。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(2.dp))
                GlassSurface(
                    shape = RoundedCornerShape(GlassTokens.RadiusPill),
                    onClick = onRetry,
                    darkTheme = LocalIsDarkTheme.current,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                ) {
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlowLoadingHint() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(GlassTokens.RadiusLarge),
            interactive = false,
            darkTheme = LocalIsDarkTheme.current,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "正在连接 linux.do…",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "已经等了一会儿。如果一直连不上，多半是网络到不了站点——\n" +
                        "可以开代理，或到「设置 → DNS over HTTPS」打开 DoH。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
