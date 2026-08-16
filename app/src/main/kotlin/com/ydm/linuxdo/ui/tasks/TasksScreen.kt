package com.ydm.linuxdo.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ydm.linuxdo.LinuxDoApp
import com.ydm.linuxdo.automation.model.BrowseMode
import com.ydm.linuxdo.automation.model.DefaultCategories
import com.ydm.linuxdo.core.data.StopType
import com.ydm.linuxdo.core.designsystem.GlassChip
import com.ydm.linuxdo.core.designsystem.GlassSegmented
import com.ydm.linuxdo.core.designsystem.GlassSettingRow
import com.ydm.linuxdo.core.designsystem.GlassSliderRow
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassSwitch
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.LocalIsDarkTheme
import com.ydm.linuxdo.core.designsystem.SectionTitle
import kotlinx.coroutines.launch

/**
 * 任务页：配置这次要怎么跑。
 *
 * 关键设计：把上游那个语义混淆的「帖子数量」拆成了
 * **目标话题数** 与 **目标已读数** 两个独立选项，各自带说明文案，
 * 用户不会再填 50 结果爬完一个帖子就停。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as LinuxDoApp
    val store = app.data.settingsStore
    val settings by app.data.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showReplyRisk by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.statusBarsPadding().height(8.dp)) }

        item {
            Text(
                text = "任务配置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ---- 浏览模式 ----
        item { SectionTitle("浏览模式") }
        item {
            GlassSegmented(
                options = BrowseMode.entries,
                selected = settings.browseMode,
                onSelect = { mode -> scope.launch { store.update { it.browseMode(mode) } } },
                label = { if (it == BrowseMode.DEEP) "深度爬楼" else "快速浏览" },
            )
        }
        item {
            Text(
                text = if (settings.browseMode == BrowseMode.DEEP) {
                    "完整读完帖子所有楼层，主要拉高「已读帖子」"
                } else {
                    "只爬 3-5 层就换帖，主要拉高「浏览话题」"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // ---- 停止条件 ----
        item { SectionTitle("停止条件") }
        items(StopType.entries.toList()) { type ->
            GlassSettingRow(
                title = type.label,
                subtitle = type.hint,
                onClick = { scope.launch { store.update { it.stopType(type) } } },
                trailing = {
                    GlassSwitch(
                        checked = settings.stopType == type,
                        onCheckedChange = { scope.launch { store.update { e -> e.stopType(type) } } },
                    )
                },
            )
        }
        if (settings.stopType != StopType.ENDLESS) {
            item {
                NumberField(
                    label = when (settings.stopType) {
                        StopType.TOPIC_COUNT -> "目标话题数"
                        StopType.READ_COUNT -> "目标已读数"
                        StopType.DURATION -> "运行分钟数"
                        StopType.ENDLESS -> ""
                    },
                    value = settings.stopValue,
                    onValueChange = { v -> scope.launch { store.update { it.stopValue(v) } } },
                )
            }
        }

        // ---- 功能开关 ----
        item { SectionTitle("功能开关") }
        item {
            GlassSettingRow(
                title = "自动点赞",
                subtitle = "默认关闭",
                trailing = {
                    GlassSwitch(
                        checked = settings.enableLike,
                        onCheckedChange = { v -> scope.launch { store.update { it.enableLike(v) } } },
                    )
                },
            )
        }
        item {
            GlassSettingRow(
                title = "自动回复",
                subtitle = "⚠ L 站可能检测自动回复，默认关闭",
                trailing = {
                    GlassSwitch(
                        checked = settings.enableReply,
                        onCheckedChange = { v ->
                            if (v && !settings.replyRiskAcknowledged) {
                                showReplyRisk = true
                            } else {
                                scope.launch { store.update { it.enableReply(v) } }
                            }
                        },
                    )
                },
            )
        }
        item {
            GlassSettingRow(
                title = "额外等待延迟",
                subtitle = "爬楼本身已有 2-4 秒间隔，可关闭",
                trailing = {
                    GlassSwitch(
                        checked = settings.enableExtraDelay,
                        onCheckedChange = { v ->
                            scope.launch { store.update { it.enableExtraDelay(v) } }
                        },
                    )
                },
            )
        }

        // ---- 概率 ----
        if (settings.enableLike) {
            item { SectionTitle("点赞概率") }
            item {
                GlassSliderRow(
                    title = "主帖点赞率",
                    value = settings.likeRate,
                    onValueChange = { v -> scope.launch { store.update { it.likeRate(v) } } },
                )
            }
            item {
                GlassSliderRow(
                    title = "回复点赞率",
                    value = settings.likeReplyRate,
                    onValueChange = { v -> scope.launch { store.update { it.likeReplyRate(v) } } },
                )
            }
        }
        if (settings.enableReply) {
            item {
                GlassSliderRow(
                    title = "回复概率",
                    value = settings.replyRate,
                    onValueChange = { v -> scope.launch { store.update { it.replyRate(v) } } },
                    valueRange = 0f..0.5f,
                )
            }
        }

        // ---- 板块 ----
        item { SectionTitle("参与板块（${settings.enabledCategorySlugs.size}/${DefaultCategories.all.size}）") }
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(GlassTokens.RadiusMedium),
                interactive = false,
                darkTheme = LocalIsDarkTheme.current,
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DefaultCategories.all.forEach { cat ->
                        val selected = cat.slug in settings.enabledCategorySlugs
                        GlassChip(
                            text = cat.name,
                            selected = selected,
                            onClick = {
                                val next = settings.enabledCategorySlugs.toMutableSet()
                                if (selected) next -= cat.slug else next += cat.slug
                                scope.launch { store.update { it.enabledCategories(next) } }
                            },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }

    if (showReplyRisk) {
        ReplyRiskDialog(
            onConfirm = {
                showReplyRisk = false
                scope.launch {
                    store.update {
                        it.enableReply(true)
                        it.replyRiskAcknowledged(true)
                    }
                }
            },
            onDismiss = { showReplyRisk = false },
        )
    }
}

/**
 * 自动回复风险确认。
 *
 * 沿用上游 v8.4 的做法：这个功能有实际风险（社区反馈曾有用户因自动回复被举报），
 * 必须让用户明确知情后主动开启，不能默默打开。
 */
@Composable
private fun ReplyRiskDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ 自动回复风险提示") },
        text = {
            Text(
                "据社区反馈，L 站可能存在检测自动回复的机制：\n\n" +
                    "• 曾有用户因自动回复被举报\n" +
                    "• 可能影响账号信任等级甚至被封禁\n" +
                    "• 建议仅在必要时谨慎使用\n\n" +
                    "确定要启用吗？",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("我已知晓，启用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(GlassTokens.RadiusSmall),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(5)
                    text = digits
                    digits.toIntOrNull()?.let { onValueChange(it.coerceAtLeast(1)) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
