package com.ydm.linuxdo.ui.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ydm.linuxdo.LinuxDoApp
import com.ydm.linuxdo.core.data.ReplyTemplate
import com.ydm.linuxdo.core.data.TemplateStore
import com.ydm.linuxdo.core.designsystem.GlassChip
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassSwitch
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.LocalIsDarkTheme
import com.ydm.linuxdo.core.designsystem.StatusColors
import kotlinx.coroutines.launch

/**
 * 回复模板管理。
 *
 * 主人明确要求「自动回复可以用户自己替换更改添加」，所以这里是完整 CRUD：
 * 新增 / 编辑 / 删除 / 单条启停 / 按分类批量启停 / 权重 / 导入导出 / 恢复默认。
 *
 * 导入导出走 SAF（系统文件选择器），不需要存储权限。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LinuxDoApp
    val store = app.data.templateStore
    val templates by store.templates.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var groupFilter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ReplyTemplate?>(null) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val groups = remember(templates) { templates.map { it.group }.distinct() }
    val visible = remember(templates, groupFilter) {
        if (groupFilter == null) templates else templates.filter { it.group == groupFilter }
    }
    val enabledCount = templates.count { it.enabled }

    // ---- 导出 ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(store.exportJson().toByteArray())
                }
            }.onSuccess { message = "已导出 ${templates.size} 条模板" }
                .onFailure { message = "导出失败：${it.message}" }
        }
    }

    // ---- 导入 ----
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                message = if (json == null) {
                    "读取文件失败"
                } else {
                    when (val r = store.importJson(json, replace = false)) {
                        is TemplateStore.ImportResult.Success ->
                            "导入 ${r.added} 条，跳过 ${r.skipped} 条（重复或不合规）"
                        is TemplateStore.ImportResult.Failure -> "导入失败：${r.reason}"
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.statusBarsPadding().height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "回复模板",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "共 ${templates.size} 条，启用 $enabledCount 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBack) { Text("返回") }
        }

        // ---- 分类筛选 ----
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip("全部", groupFilter == null) { groupFilter = null }
            groups.forEach { g ->
                GlassChip(g, groupFilter == g) { groupFilter = if (groupFilter == g) null else g }
            }
        }

        // ---- 操作条 ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallGlassButton("新增", Modifier.weight(1f)) { adding = true }
            SmallGlassButton("导入", Modifier.weight(1f)) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            SmallGlassButton("导出", Modifier.weight(1f)) {
                exportLauncher.launch("linuxdo-templates.json")
            }
            SmallGlassButton("重置", Modifier.weight(1f)) { confirmReset = true }
        }

        // 分类批量启停
        if (groupFilter != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallGlassButton("全部启用", Modifier.weight(1f)) {
                    scope.launch { store.setGroupEnabled(groupFilter!!, true) }
                }
                SmallGlassButton("全部停用", Modifier.weight(1f)) {
                    scope.launch { store.setGroupEnabled(groupFilter!!, false) }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.id }) { item ->
                TemplateRow(
                    item = item,
                    onToggle = { scope.launch { store.setEnabled(item.id, it) } },
                    onEdit = { editing = item },
                    onDelete = { scope.launch { store.delete(item.id) } },
                )
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    // ---- 弹窗 ----
    if (adding) {
        TemplateEditDialog(
            initial = "",
            title = "新增模板",
            onConfirm = { text ->
                scope.launch {
                    val err = store.add(text)
                    message = err ?: "已添加"
                    if (err == null) adding = false
                }
            },
            onDismiss = { adding = false },
        )
    }

    editing?.let { item ->
        TemplateEditDialog(
            initial = item.content,
            title = "编辑模板",
            onConfirm = { text ->
                scope.launch {
                    val err = store.update(item.id, text)
                    message = err ?: "已保存"
                    if (err == null) editing = null
                }
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("恢复默认模板") },
            text = { Text("会清空当前所有模板（包括自定义的），恢复为内置的 65 条。确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        store.resetToDefaults()
                        message = "已恢复默认"
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } },
        )
    }
}

@Composable
private fun TemplateRow(
    item: ReplyTemplate,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.RadiusSmall),
        onClick = onEdit,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDelete) {
                Text("删除", color = StatusColors.failed, style = MaterialTheme.typography.labelMedium)
            }
            GlassSwitch(checked = item.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun TemplateEditDialog(
    initial: String,
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GlassTokens.RadiusSmall),
                    interactive = false,
                    darkTheme = LocalIsDarkTheme.current,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "至少 6 个字（避免被判定为无意义回复），当前 ${text.trim().length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (text.trim().length < 6) {
                        StatusColors.paused
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SmallGlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(GlassTokens.RadiusPill),
        onClick = onClick,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
