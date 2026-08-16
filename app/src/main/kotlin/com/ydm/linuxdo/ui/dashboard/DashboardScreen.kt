package com.ydm.linuxdo.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ydm.linuxdo.automation.model.LevelRequirement
import com.ydm.linuxdo.core.designsystem.AppIcons
import com.ydm.linuxdo.core.designsystem.GlassProgressBar
import com.ydm.linuxdo.core.designsystem.GlassSurface
import com.ydm.linuxdo.core.designsystem.GlassTokens
import com.ydm.linuxdo.core.designsystem.HeroNumberStyle
import com.ydm.linuxdo.core.designsystem.LevelProgressRing
import com.ydm.linuxdo.core.designsystem.LocalIsDarkTheme
import com.ydm.linuxdo.core.designsystem.PrimaryGlassButton
import com.ydm.linuxdo.core.designsystem.SectionTitle
import com.ydm.linuxdo.core.designsystem.StatNumberStyle
import com.ydm.linuxdo.core.designsystem.StatusColors
import com.ydm.linuxdo.core.designsystem.animatedCount
import com.ydm.linuxdo.core.designsystem.enterStaggered
import com.ydm.linuxdo.core.designsystem.beamOutline
import com.ydm.linuxdo.core.designsystem.diffusedShadow
import com.ydm.linuxdo.core.designsystem.rememberBreathAlpha

/**
 * 仪表盘（v2 重做）。
 *
 * ## 相对 v1 的改动（针对「布局不好看」的反馈）
 *
 * v1 是「等级环 + 四个等大方格 + 一长串指标」，所有元素视觉权重一样，
 * 眼睛没有落点，所以显得平。v2 重排了信息层级：
 *
 * 1. **主视觉**：超大的「本次已读」数字压住整屏 —— 这是用户最关心的数
 * 2. **次级**：等级环 + 目标等级，贴在主数字旁边
 * 3. **三级**：四个小统计块，字号明显小一档
 * 4. **四级**：站点指标列表，只在拿到数据后出现
 *
 * 外加：卡片错峰入场、数字滚动过渡、运行时状态点呼吸。
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val running = state.engineStatus == EngineStatus.RUNNING ||
        state.engineStatus == EngineStatus.WAITING_LOGIN ||
        state.engineStatus == EngineStatus.PAUSED

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.statusBarsPadding().height(4.dp)) }

        item { StatusHeader(state, running, Modifier.enterStaggered(0)) }

        item { HeroCard(state, running, Modifier.enterStaggered(1)) }

        item { SessionStatsGrid(state, Modifier.enterStaggered(2)) }

        item {
            TerminalLogPanel(
                logs = state.logs,
                running = running,
                modifier = Modifier.enterStaggered(3),
            )
        }

        val requirements = state.levelInfo?.requirements.orEmpty()
        if (requirements.isNotEmpty()) {
            item { SectionTitle("站点指标 · 来自 connect.linux.do") }
            indexedItems(requirements) { index, req ->
                RequirementRow(
                    req = req,
                    delta = state.siteDelta[req.rawName],
                    modifier = Modifier.enterStaggered(4 + index),
                )
            }
        }

        item { Spacer(Modifier.height(2.dp)) }

        item {
            PrimaryGlassButton(
                text = if (running) "停止脚本" else "开始脚本",
                onClick = if (running) onStop else onStart,
                accent = if (running) StatusColors.failed else MaterialTheme.colorScheme.primary,
                modifier = Modifier.enterStaggered(5),
                leading = {
                    Icon(
                        imageVector = if (running) AppIcons.Stop else AppIcons.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }

        // 给底栏留出空间
        item { Spacer(Modifier.height(bottomInset + 16.dp)) }
    }
}

/** LazyListScope 没有内置的带索引 items，补一个（错峰入场需要索引） */
private inline fun <T> LazyListScope.indexedItems(
    list: List<T>,
    crossinline block: @Composable (Int, T) -> Unit,
) {
    items(list.size) { index -> block(index, list[index]) }
}

@Composable
private fun StatusHeader(
    state: DashboardUiState,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (state.engineStatus) {
        EngineStatus.RUNNING -> StatusColors.running
        EngineStatus.WAITING_LOGIN, EngineStatus.PAUSED -> StatusColors.paused
        EngineStatus.FINISHED -> StatusColors.finished
        EngineStatus.FAILED -> StatusColors.failed
        EngineStatus.IDLE -> StatusColors.idle
    }
    // 运行时状态点呼吸：一眼看出脚本还活着，比任何文字都直观
    val breath = rememberBreathAlpha(running, min = 0.35f)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Linux.do 助手",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                // 冷启动先靠 Cookie 命中本地记录，立刻能显示"欢迎回来"，
                // 不必等抓完等级信息
                text = state.greeting
                    ?: when {
                        state.loggedIn && state.username.isNotBlank() -> "@${state.username}"
                        state.loggedIn -> "已登录"
                        else -> "未登录"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassSurface(
            shape = RoundedCornerShape(GlassTokens.RadiusPill),
            interactive = false,
            darkTheme = LocalIsDarkTheme.current,
            tint = statusColor.copy(alpha = 0.30f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(8.dp).graphicsLayer { alpha = breath }) {
                    drawCircle(color = statusColor)
                }
                Spacer(Modifier.size(7.dp))
                Text(
                    text = state.statusDetail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 主视觉卡：超大「本次已读」+ 等级环。
 *
 * 跑脚本的人最关心「读了多少」，所以让这个数字占绝对视觉主导，
 * 其余信息都退到它周围。
 */
@Composable
private fun HeroCard(state: DashboardUiState, running: Boolean, modifier: Modifier = Modifier) {
    val info = state.levelInfo
    val readCount = animatedCount(state.stats.totalRead)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            // 彩色扩散阴影替代黑色硬阴影：卡片像自己在发光，
            // 和极光背景是同一套光影逻辑
            .diffusedShadow(
                color = MaterialTheme.colorScheme.primary,
                alpha = if (running) 0.30f else 0.16f,
            )
            // 运行时边框跑一道流光，余光就能感知脚本还活着
            .beamOutline(
                active = running,
                color = MaterialTheme.colorScheme.primary,
                cornerRadius = GlassTokens.RadiusLarge,
            ),
        shape = RoundedCornerShape(GlassTokens.RadiusLarge),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本次已读",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = readCount.toString(),
                            style = HeroNumberStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.stats.hasUnreliableFloors) {
                            Text(
                                text = " ⚠",
                                style = MaterialTheme.typography.titleMedium,
                                color = StatusColors.paused,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    Text(
                        text = "话题 ${state.stats.topics} · 爬楼 ${state.stats.floors}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    LevelProgressRing(
                        progress = state.overallProgress ?: 0f,
                        modifier = Modifier.size(96.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = info?.level?.ifBlank { "-" } ?: "-",
                            style = StatNumberStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (info?.nextLevel.isNullOrBlank()) "等级" else "→ ${info?.nextLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            state.overallProgress?.let { p ->
                GlassProgressBar(progress = p, height = 8.dp)
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "用时 ${state.elapsedText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.remainingText?.let {
                    Text(
                        text = "  ·  剩余 $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                state.overallProgress?.let {
                    Text(
                        text = "${(it * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatsGrid(state: DashboardUiState, modifier: Modifier = Modifier) {
    val s = state.stats
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard("浏览话题", s.topics, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            MiniStatCard(
                label = "爬楼层数",
                value = s.floors,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                warn = s.hasUnreliableFloors,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard("点赞", s.likesTotal, StatusColors.running, Modifier.weight(1f))
            MiniStatCard("回复", s.replies, StatusColors.paused, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
) {
    val animated = animatedCount(value)
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(GlassTokens.RadiusMedium),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
        tint = accent.copy(alpha = 0.14f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (warn) {
                    Spacer(Modifier.size(4.dp))
                    Text("⚠", style = MaterialTheme.typography.labelMedium, color = StatusColors.paused)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = animated.toString(),
                style = StatNumberStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RequirementRow(
    req: LevelRequirement,
    delta: Int?,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.RadiusSmall),
        interactive = false,
        darkTheme = LocalIsDarkTheme.current,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = req.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (delta != null && delta != 0) {
                    Text(
                        text = if (delta > 0) "+$delta" else "$delta",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (delta > 0) StatusColors.running else StatusColors.failed,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    text = "${req.current} / ${req.required}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            req.progress?.let {
                Spacer(Modifier.height(8.dp))
                GlassProgressBar(progress = it, height = 4.dp)
            }
        }
    }
}
