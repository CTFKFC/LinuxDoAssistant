package com.ydm.linuxdo.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydm.linuxdo.automation.model.LogEntry
import com.ydm.linuxdo.automation.model.LogLevel
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.LocalIsDarkTheme
import com.ydm.linuxdo.core.designsystem.StatusColors
import com.ydm.linuxdo.core.designsystem.beamOutline
import com.ydm.linuxdo.core.designsystem.edgeFade
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 终端风格实时日志流。
 *
 * ## 为什么这是本次 UI 改版里最重要的一块
 *
 * 之前界面上只有几个统计数字，脚本在干什么完全是黑箱——
 * 卡住了、在等验证、还是在正常爬楼，用户一概不知，只能干等。
 *
 * 这块日志流同时解决观感与实用两件事：
 * - **实用**：`爬楼 #5 → 25/169` 这种输出让人一眼看出进度和卡点
 * - **观感**：等宽字体 + 色标 + 光标闪烁，是最正统的"赛博"，
 *   而且它的科技感来自**真实的信息密度**，不是装饰，所以不会中二
 *
 * ## 性能
 *
 * 纯文字渲染，是所有效果里最省的。只保留最近 [MAX_VISIBLE] 行，
 * 避免长会话后列表膨胀。
 */
@Composable
fun TerminalLogPanel(
    logs: List<LogEntry>,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val visible = remember(logs) { logs.takeLast(MAX_VISIBLE) }

    // 新日志进来自动滚到底
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) {
            listState.animateScrollToItem(visible.lastIndex)
        }
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .beamOutline(
                active = running,
                color = StatusColors.running,
                cornerRadius = GlassTokens.RadiusMedium,
            ),
        shape = RoundedCornerShape(GlassTokens.RadiusMedium),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            TerminalHeader(running, visible.size)

            Spacer(Modifier.height(8.dp))

            if (visible.isEmpty()) {
                Text(
                    text = "$ 等待任务开始…",
                    style = terminalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PANEL_HEIGHT.dp)
                        // 上下渐隐，暗示"还有更多内容"，而不是被硬切
                        .edgeFade(
                            topFade = 20.dp,
                            bottomFade = 20.dp,
                            color = GlassTokens.A11yBackdropDark,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visible, key = { it.timestampMillis.toString() + it.message.hashCode() }) { entry ->
                        LogLine(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalHeader(running: Boolean, lineCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 三个圆点：终端窗口的通用符号，一眼就知道这块是"控制台"
        listOf(StatusColors.failed, StatusColors.paused, StatusColors.running).forEach { c ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { alpha = 0.75f },
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(7.dp)) {
                    drawCircle(color = c)
                }
            }
            Spacer(Modifier.width(5.dp))
        }

        Spacer(Modifier.width(4.dp))
        Text(
            text = "运行日志",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = if (running) "● LIVE" else "$lineCount 行",
            style = terminalStyle(size = 10),
            color = if (running) StatusColors.running else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.SUCCESS -> StatusColors.running
        LogLevel.WARN -> StatusColors.paused
        LogLevel.ERROR -> StatusColors.failed
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = timeFormat.format(Date(entry.timestampMillis)),
            style = terminalStyle(size = 10),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.level.marker(),
            style = terminalStyle(size = 10),
            color = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.message,
            style = terminalStyle(),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 闪烁光标——终端的标志性元素，几乎零成本 */
@Composable
fun TerminalCursor(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier = Modifier
            .width(7.dp)
            .height(14.dp)
            .graphicsLayer { this.alpha = if (active) alpha else 0.25f },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRect(color = color)
        }
    }
}

private fun LogLevel.marker(): String = when (this) {
    LogLevel.DEBUG -> "···"
    LogLevel.INFO -> "[i]"
    LogLevel.SUCCESS -> "[✓]"
    LogLevel.WARN -> "[!]"
    LogLevel.ERROR -> "[✗]"
}

@Composable
private fun terminalStyle(size: Int = 11) = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = (size + 5).sp,
)

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

private const val MAX_VISIBLE = 120
private const val PANEL_HEIGHT = 168
