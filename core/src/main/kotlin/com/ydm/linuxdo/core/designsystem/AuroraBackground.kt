package com.ydm.linuxdo.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 极光背景——玻璃拟态的「被折射对象」。
 *
 * ## v3：性能重写（v2 在低端机上把界面拖垮了）
 *
 * v2 每一帧都要重新构造 6 个 `Brush.radialGradient` 并绘制 6 个全屏圆 + 2 层遮罩。
 * 在 Redmi 9A（Helio G25）这类机器上，光背景就吃满了帧预算，
 * 用户实测「特别慢」。
 *
 * v3 的做法：
 * 1. **离屏渲染一次**：把所有光斑画进一张 **1/8 分辨率**的 [ImageBitmap]
 * 2. **每帧只贴这张图**：一次 `drawImage`，代价约等于一次纹理拷贝
 * 3. 动画不再重画光斑，只让这张图**缓慢平移+微缩放**
 *
 * 顺带的好处：1/8 分辨率放大回去天然就是糊的，
 * 等于免费得到了高斯模糊效果，而且在所有 API 上一致
 * （`Modifier.blur()` 只在 API 31+ 生效，本项目要支持 Android 10）。
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = LocalIsDarkTheme.current,
    /** 关掉动画（省电 / 低端机 / 无障碍减弱动效）；关掉后完全静止，零每帧开销 */
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = if (darkTheme) GlassTokens.ScrimDark else GlassTokens.ScrimLight

    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.DurationAmbient * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora-phase",
    )

    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize().background(base)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // 1/8 分辨率的离屏图：只在尺寸变化时重画一次
            val bitmap = auroraCache.obtain(
                widthPx = w.roundToInt(),
                heightPx = h.roundToInt(),
                darkTheme = darkTheme,
                density = density.density,
                layoutDirection = layoutDirection,
            )

            // 动画只做平移 + 微缩放，不重画任何渐变
            val driftX = if (animated) cos(phase) * w * 0.05f else 0f
            val driftY = if (animated) sin(phase * 0.7f) * h * 0.04f else 0f
            val overscan = 1.14f
            val dstW = (w * overscan).roundToInt()
            val dstH = (h * overscan).roundToInt()

            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    x = ((w - dstW) / 2f + driftX).roundToInt(),
                    y = ((h - dstH) / 2f + driftY).roundToInt(),
                ),
                dstSize = IntSize(dstW, dstH),
            )

            // 晕影：单层渐变，开销可忽略
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, base.copy(alpha = 0.42f)),
                    center = Offset(w * 0.5f, h * 0.45f),
                    radius = maxOf(w, h) * 0.78f,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(base.copy(alpha = 0.55f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.16f,
                ),
            )
        }
        content()
    }
}

/**
 * 极光离屏图缓存。
 *
 * 只在「尺寸或主题变化」时重新生成，其余时间直接复用，
 * 避免每帧构造渐变对象——这是 v3 性能提升的关键。
 */
private object auroraCache {
    private var cached: ImageBitmap? = null
    private var cachedKey: String = ""

    /** 缩放比：1/8 既够糊又够省，再低会出现明显色带 */
    private const val DOWNSCALE = 8

    fun obtain(
        widthPx: Int,
        heightPx: Int,
        darkTheme: Boolean,
        density: Float,
        layoutDirection: LayoutDirection,
    ): ImageBitmap {
        val lowW = (widthPx / DOWNSCALE).coerceAtLeast(16)
        val lowH = (heightPx / DOWNSCALE).coerceAtLeast(16)
        val key = "$lowW×$lowH×$darkTheme"

        cached?.let { if (cachedKey == key) return it }

        val bitmap = ImageBitmap(lowW, lowH)
        val canvas = GraphicsCanvas(bitmap)
        CanvasDrawScope().draw(
            density = androidx.compose.ui.unit.Density(density),
            layoutDirection = layoutDirection,
            canvas = canvas,
            size = Size(lowW.toFloat(), lowH.toFloat()),
        ) {
            paintBlobs(darkTheme)
        }

        cached = bitmap
        cachedKey = key
        return bitmap
    }

    private fun DrawScope.paintBlobs(darkTheme: Boolean) {
        val w = size.width
        val h = size.height
        val alpha = if (darkTheme) 1.0f else 0.45f
        val r = maxOf(w, h)

        // (颜色, x比例, y比例, 半径系数)
        val blobs = listOf(
            Blob(GlassTokens.AuroraBlue, 0.10f, 0.04f, 1.05f),
            Blob(GlassTokens.AuroraViolet, 0.94f, 0.14f, 0.95f),
            Blob(GlassTokens.AuroraCyan, 0.74f, 0.82f, 0.88f),
            Blob(GlassTokens.AuroraMagenta, 0.04f, 0.96f, 0.80f),
            Blob(GlassTokens.AuroraTeal, 0.46f, 0.42f, 0.72f),
            Blob(GlassTokens.AuroraBlue, 0.62f, 0.00f, 0.66f),
        )

        blobs.forEach { b ->
            val center = Offset(b.cx * w, b.cy * h)
            val radius = r * b.radiusScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        b.color.copy(alpha = alpha * 0.60f),
                        b.color.copy(alpha = alpha * 0.24f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }

    private data class Blob(
        val color: Color,
        val cx: Float,
        val cy: Float,
        val radiusScale: Float,
    )
}
