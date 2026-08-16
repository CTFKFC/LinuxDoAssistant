package com.ydm.linuxdo.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 统计小卡——仪表盘上「浏览话题 150/200」这种格子。
 *
 * [unreliable] 为 true 时右上角显示 ⚠，用来诚实提示「这个数可能偏低」，
 * 对应引擎里读不到楼层计数器的情况。上游遇到这种情况直接编个数字，本项目不这么干。
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    target: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    progress: Float? = null,
    unreliable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val darkTheme = LocalIsDarkTheme.current
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(GlassTokens.RadiusMedium),
        onClick = onClick,
        darkTheme = darkTheme,
        tint = accent.copy(alpha = 0.12f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (unreliable) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusColors.paused,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = StatNumberStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (target != null) {
                    Text(
                        text = " / $target",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                GlassProgressBar(progress = progress, accent = accent)
            }
        }
    }
}

/** 细长玻璃进度条，带流光高亮 */
@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    height: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(MotionTokens.DurationSlow, easing = MotionTokens.EasingStandard),
        label = "progress",
    )
    val trackColor = if (LocalIsDarkTheme.current) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(GlassTokens.RadiusPill)),
    ) {
        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        )
        if (animated > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.copy(alpha = 0.75f), accent),
                ),
                size = androidx.compose.ui.geometry.Size(size.width * animated, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
        }
    }
}

/**
 * 信任等级环形进度——仪表盘顶部的主视觉。
 *
 * 用 Canvas 画而不是用 CircularProgressIndicator，是为了能做渐变描边 + 自定义端点。
 */
@Composable
fun LevelProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 10.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentEnd: Color = MaterialTheme.colorScheme.secondary,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(MotionTokens.DurationSlow, easing = MotionTokens.EasingEmphasized),
        label = "ring",
    )
    val trackColor = if (LocalIsDarkTheme.current) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - stroke,
            size.height - stroke,
        )
        // 底环
        drawArc(
            color = trackColor,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 进度环
        if (animated > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(accent, accentEnd, accent)),
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** 玻璃主按钮 */
@Composable
fun PrimaryGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    leading: @Composable (() -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.RadiusLarge),
        onClick = onClick,
        enabled = enabled,
        darkTheme = LocalIsDarkTheme.current,
        tint = accent.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            if (leading != null) Spacer(Modifier.size(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 区块标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    )
}
