package com.ydm.linuxdo.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ydm.linuxdo.automation.model.AutomationState
import com.ydm.linuxdo.core.automation.AutomationController
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.LinuxDoTheme
import com.ydm.linuxdo.core.designsystem.StatusColors

/**
 * 小白点。
 *
 * 主人的要求：**不要太大、可以移动、点击才有界面**。
 * 所以这里只画一个 48dp 的圆点，拖动与点击由 [OverlayController] 的
 * touch listener 处理（Compose 的手势与 WindowManager 拖动混用会打架）。
 *
 * 圆点本身承载最小信息量：
 * - 中心白点：始终可见，是「抓手」
 * - 外圈细环：运行中时旋转，一眼能看出脚本还活着
 * - 环的颜色：绿=运行 / 黄=等登录或暂停 / 蓝=已完成 / 红=出错
 */
@Composable
fun OverlayDot() {
    LinuxDoTheme {
        val state by AutomationController.state.collectAsStateWithLifecycle()

        val running = state !is AutomationState.Idle &&
            state !is AutomationState.Finished &&
            state !is AutomationState.Failed

        val ringColor = when (state) {
            is AutomationState.WaitingForLogin, AutomationState.Paused -> StatusColors.paused
            is AutomationState.Finished -> StatusColors.finished
            is AutomationState.Failed -> StatusColors.failed
            AutomationState.Idle -> StatusColors.idle
            else -> StatusColors.running
        }

        val transition = rememberInfiniteTransition(label = "dot")
        val sweepStart by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dot-sweep",
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2

                // 底环：半透明，保证在任何壁纸上都看得见
                drawArc(
                    color = Color.Black.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke,
                    ),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )

                // 状态环：运行中转圈，静止时画整圈
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(ringColor.copy(alpha = 0.2f), ringColor, ringColor.copy(alpha = 0.2f)),
                    ),
                    startAngle = if (running) sweepStart else 0f,
                    sweepAngle = if (running) 110f else 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke,
                    ),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // 中心小白点
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(50)),
            )
        }
    }
}

/**
 * 点击小白点后展开的玻璃控制台。
 *
 * 全屏遮罩，点空白处收起。只放最必要的：状态、统计、开始/停止、打开应用。
 */
@Composable
fun OverlayPanel(
    onToggleRun: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    LinuxDoTheme {
        val state by AutomationController.state.collectAsStateWithLifecycle()
        val stats by AutomationController.stats.collectAsStateWithLifecycle()

        val running = state !is AutomationState.Idle &&
            state !is AutomationState.Finished &&
            state !is AutomationState.Failed

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(0.88f),
                shape = RoundedCornerShape(GlassTokens.RadiusLarge),
                interactive = false,
                darkTheme = true,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Linux.do 助手",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.shortLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat("话题", stats.topics.toString(), Modifier.weight(1f))
                        MiniStat("爬楼", stats.floors.toString(), Modifier.weight(1f))
                        MiniStat("已读", stats.totalRead.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat("点赞", stats.likesTotal.toString(), Modifier.weight(1f))
                        MiniStat("回复", stats.replies.toString(), Modifier.weight(1f))
                        MiniStat(
                            "异常",
                            stats.floorsUnreliable.toString(),
                            Modifier.weight(1f),
                            highlight = stats.hasUnreliableFloors,
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PanelButton(
                            text = if (running) "停止" else "开始",
                            accent = if (running) StatusColors.failed else StatusColors.running,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleRun,
                        )
                        PanelButton(
                            text = "打开应用",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenApp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(GlassTokens.RadiusSmall),
        interactive = false,
        darkTheme = true,
        tint = if (highlight) StatusColors.paused.copy(alpha = 0.25f) else Color.Unspecified,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PanelButton(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(GlassTokens.RadiusPill),
        onClick = onClick,
        darkTheme = true,
        tint = accent.copy(alpha = 0.35f),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun AutomationState.shortLabel(): String = when (this) {
    AutomationState.Idle -> "未运行"
    is AutomationState.WaitingForLogin -> "等待登录 ${elapsedSeconds}s"
    AutomationState.FetchingLevelInfo -> "获取等级信息"
    is AutomationState.EnteringCategory -> "进入 ${category.name}"
    is AutomationState.ListingTopics -> "读取话题列表"
    is AutomationState.BrowsingTopic ->
        floor?.let { "爬楼 ${it.current}/${it.total}" } ?: "浏览话题"
    is AutomationState.Liking -> "点赞中"
    is AutomationState.Replying -> "回复中"
    AutomationState.Paused -> "已暂停"
    is AutomationState.Finished -> "已完成"
    is AutomationState.Failed -> "出错：$message"
}
