package com.ydm.linuxdo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ydm.linuxdo.BuildConfig
import com.ydm.linuxdo.LinuxDoApp
import com.ydm.linuxdo.core.browser.DohProvider
import com.ydm.linuxdo.core.browser.DohResolver
import com.ydm.linuxdo.core.crash.CrashReporter
import com.ydm.linuxdo.core.designsystem.GlassChip
import com.ydm.linuxdo.core.designsystem.GlassSettingRow
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassSwitch
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.LocalIsDarkTheme
import com.ydm.linuxdo.core.designsystem.SectionTitle
import com.ydm.linuxdo.core.designsystem.StatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LinuxDoApp
    val store = app.data.settingsStore
    val settings by app.data.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var dohTestResult by remember { mutableStateOf<String?>(null) }
    var showCrashLogs by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.statusBarsPadding().height(8.dp)) }
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ---------------- DNS ----------------
        item { SectionTitle("DNS over HTTPS") }
        item {
            GlassSettingRow(
                title = "启用 DoH",
                subtitle = "用加密 DNS 解析，规避 DNS 污染",
                trailing = {
                    GlassSwitch(
                        checked = settings.dohEnabled,
                        onCheckedChange = { v -> scope.launch { store.update { it.dohEnabled(v) } } },
                    )
                },
            )
        }
        if (settings.dohEnabled) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GlassTokens.RadiusSmall),
                    interactive = false,
                    darkTheme = LocalIsDarkTheme.current,
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DohProvider.presets.take(3).forEach { p ->
                                GlassChip(p.displayName, settings.dohProviderId == p.id) {
                                    scope.launch { store.update { it.dohProviderId(p.id) } }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DohProvider.presets.drop(3).forEach { p ->
                                GlassChip(p.displayName, settings.dohProviderId == p.id) {
                                    scope.launch { store.update { it.dohProviderId(p.id) } }
                                }
                            }
                            GlassChip("自定义", settings.dohProviderId == "custom") {
                                scope.launch { store.update { it.dohProviderId("custom") } }
                            }
                        }
                    }
                }
            }
            if (settings.dohProviderId == "custom") {
                item {
                    TextInputRow(
                        label = "DoH 地址",
                        value = settings.dohCustomUrl,
                        placeholder = "https://example.com/dns-query",
                        onValueChange = { v -> scope.launch { store.update { it.dohCustomUrl(v) } } },
                    )
                }
            }
            item {
                GlassSettingRow(
                    title = "解析测试",
                    subtitle = dohTestResult ?: "点击测试 linux.do 的解析结果",
                    onClick = {
                        scope.launch {
                            dohTestResult = "测试中…"
                            val r = withContext(Dispatchers.IO) {
                                app.dohResolver.test("linux.do")
                            }
                            dohTestResult = when (r) {
                                is DohResolver.TestResult.Success ->
                                    "✓ ${r.addresses.joinToString()} (${r.millis}ms)"
                                is DohResolver.TestResult.Failure ->
                                    "✗ ${r.message} (${r.millis}ms)"
                            }
                        }
                    },
                )
            }
            item {
                Text(
                    text = "⚠ 已知限制：Android 不向应用提供 POST 请求体，因此只能接管 GET 请求。" +
                        "页面加载、图片、GET 接口都覆盖得到，但 POST（例如发回复）会走系统 DNS。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.paused,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // ---------------- 回复模板 ----------------
        item { SectionTitle("回复模板") }
        item {
            GlassSettingRow(
                title = "管理回复模板",
                subtitle = "新增 / 编辑 / 删除 / 导入导出",
                onClick = onOpenTemplates,
            )
        }

        // ---------------- 悬浮窗 ----------------
        item { SectionTitle("悬浮窗") }
        item {
            GlassSettingRow(
                title = "启用悬浮窗",
                subtitle = "小白点，可拖动，点击展开控制台",
                trailing = {
                    GlassSwitch(
                        checked = settings.overlayEnabled,
                        onCheckedChange = { v ->
                            scope.launch { store.update { it.overlayEnabled(v) } }
                        },
                    )
                },
            )
        }

        // ---------------- 外观 ----------------
        item { SectionTitle("外观") }
        item {
            GlassSettingRow(
                title = "跟随系统取色",
                subtitle = "需要 Android 12 及以上",
                trailing = {
                    GlassSwitch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { v -> scope.launch { store.update { it.dynamicColor(v) } } },
                    )
                },
            )
        }
        item {
            GlassSettingRow(
                title = "背景动效",
                subtitle = "关闭可省电（低端机建议关闭）",
                trailing = {
                    GlassSwitch(
                        checked = settings.animatedBackground,
                        onCheckedChange = { v ->
                            scope.launch { store.update { it.animatedBackground(v) } }
                        },
                    )
                },
            )
        }

        // ---------------- 关于 ----------------
        item { SectionTitle("关于") }
        item {
            GlassSettingRow(
                title = "版本",
                subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
        }
        item {
            GlassSettingRow(
                title = "开源许可与免责声明",
                subtitle = "本项目基于 icysaintdx/linuxdosss（MIT）移植",
                onClick = { showAbout = true },
            )
        }
        item {
            GlassSettingRow(
                title = "崩溃日志",
                subtitle = "共 ${CrashReporter.listReports(context).size} 条，可导出反馈",
                onClick = { showCrashLogs = true },
            )
        }

        item { Spacer(Modifier.height(100.dp)) }
    }

    if (showCrashLogs) {
        AlertDialog(
            onDismissRequest = { showCrashLogs = false },
            title = { Text("崩溃日志") },
            text = {
                Text(
                    text = CrashReporter.readAll(context).take(2000),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CrashReporter.clear(context)
                    showCrashLogs = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showCrashLogs = false }) { Text("关闭") } },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = {
                Text(
                    "本项目是 icysaintdx/linuxdosss（其 README 声明为 MIT 许可）的 Android 移植版，" +
                        "以 MIT 许可发布。详见项目根目录 LICENSE 与 NOTICE.md。\n\n" +
                        "⚠️ 免责声明\n" +
                        "本工具通过自动化产生浏览、点赞、回复行为，这类行为违反绝大多数论坛的服务条款。" +
                        "自动点赞与自动回复默认关闭。使用本工具造成的一切后果（包括账号被封禁）" +
                        "由使用者自行承担。仅供学习交流。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun TextInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.RadiusSmall),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Box {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; onValueChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
