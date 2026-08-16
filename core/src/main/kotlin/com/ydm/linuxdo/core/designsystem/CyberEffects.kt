package com.ydm.linuxdo.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 流光描边（Beam Outline）。
 *
 * 卡片边框上有一道光束在绕圈跑，只在 [active] 时出现。
 *
 * ## 为什么选它
 *
 * 「运行中」这个状态需要一个**持续、周期性、不打扰**的视觉信号。
 * 闪烁的红点太吵，进度条又占地方。绕边跑的光束正好：
 * 余光能感知到它在动，正眼看又不抢内容。
 *
 * ## 性能
 *
 * 实现上只是一个 `sweepGradient` 描边 + 每帧改一个旋转角度，
 * 没有离屏缓冲、没有模糊，在 Helio G25 这类弱 GPU 上也扛得住。
 * 关键是 brush 在 [drawWithCache] 里只构造一次，每帧只做 rotate。
 */
fun Modifier.beamOutline(
    active: Boolean,
    color: Color,
    cornerRadius: Dp = GlassTokens.RadiusLarge,
    width: Dp = 1.5.dp,
    periodMillis: Int = 2_800,
): Modifier = composed {
    if (!active) return@composed this

    val transition = rememberInfiniteTransition(label = "beam")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "beam-angle",
    )

    drawWithCache {
        val strokePx = width.toPx()
        val radiusPx = cornerRadius.toPx()
        // 光束本体：一段亮弧，其余透明
        val brush = Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.42f to Color.Transparent,
            0.50f to color,
            0.58f to Color.Transparent,
            1.00f to Color.Transparent,
            center = Offset(size.width / 2f, size.height / 2f),
        )
        onDrawWithContent {
            drawContent()
            rotate(degrees = angle) {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius(radiusPx),
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}

/**
 * 扩散阴影（Diffused Shadow）。
 *
 * 用**彩色的柔和光晕**替代 Material 默认的黑色硬阴影。
 *
 * ## 为什么换掉黑阴影
 *
 * 黑阴影是「纸片浮在白纸上」的隐喻，配深色玻璃界面会显脏。
 * 而彩色扩散阴影像是卡片自己在发光，和极光背景是同一套光影逻辑，
 * 这是廉价感与高级感的分水岭之一。
 *
 * ## 性能
 *
 * 一个径向渐变矩形，构造在 cache 里，每帧零分配。
 * 比 `Modifier.shadow()` 还便宜（后者要走 RenderNode 的阴影通道）。
 */
fun Modifier.diffusedShadow(
    color: Color,
    alpha: Float = 0.45f,
    spread: Dp = 26.dp,
    offsetY: Dp = 10.dp,
): Modifier = drawWithCache {
    val spreadPx = spread.toPx()
    val offsetPx = offsetY.toPx()
    val brush = Brush.radialGradient(
        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
        center = Offset(size.width / 2f, size.height / 2f + offsetPx),
        radius = (size.maxDimension / 2f) + spreadPx,
    )
    onDrawWithContent {
        // 先画光晕，再画内容，光晕才会在下面
        drawRect(brush = brush)
        drawContent()
    }
}

/**
 * 渐变遮罩（Gradient Mask）。
 *
 * 让滚动列表在顶部/底部**渐隐**，而不是被硬生生切断。
 *
 * 硬切边缘是"这是个被裁剪的容器"的信号，渐隐则暗示"内容还在继续"，
 * 是滚动界面里最省力的精致度提升。
 */
fun Modifier.edgeFade(
    topFade: Dp = 28.dp,
    bottomFade: Dp = 48.dp,
    color: Color = Color.Black,
): Modifier = drawWithCache {
    val topPx = topFade.toPx()
    val bottomPx = bottomFade.toPx()
    val top = Brush.verticalGradient(
        colors = listOf(color, Color.Transparent),
        startY = 0f,
        endY = topPx,
    )
    val bottom = Brush.verticalGradient(
        colors = listOf(Color.Transparent, color),
        startY = size.height - bottomPx,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        if (topPx > 0f) drawRect(brush = top, size = Size(size.width, topPx))
        if (bottomPx > 0f) {
            drawRect(
                brush = bottom,
                topLeft = Offset(0f, size.height - bottomPx),
                size = Size(size.width, bottomPx),
            )
        }
    }
}

/**
 * 流动镜面（Liquid Glass 的核心）。
 *
 * 高光位置随 [offset] 变化——把滚动进度接进来，高光就会在玻璃面上流动，
 * 像光线扫过一块真玻璃。这是 Liquid Glass 里唯一值得单独实现的部分，
 * 其余（形变、折射）在手机 GPU 上代价过高、收益有限。
 */
@Composable
fun Modifier.liquidSpecular(
    offset: Float,
    darkTheme: Boolean = LocalIsDarkTheme.current,
): Modifier {
    val color = if (darkTheme) GlassTokens.SpecularDark else GlassTokens.SpecularLight
    return this.drawWithCache {
        val cx = size.width * (0.1f + 0.8f * offset.coerceIn(0f, 1f))
        val brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(cx, -size.height * 0.2f),
            radius = size.maxDimension * 0.8f,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }
}
