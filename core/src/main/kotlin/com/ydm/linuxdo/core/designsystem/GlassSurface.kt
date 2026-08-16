package com.ydm.linuxdo.core.designsystem

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * 玻璃拟态容器——整套 UI 的基础积木。
 *
 * ## v2：三层叠出真正的「玻璃」
 *
 * v1 只有一层半透明填充，所以看起来像塑料板。v2 改成三层：
 *
 * 1. **填充层**：上亮下暗的竖向渐变（有厚度）
 * 2. **镜面高光**：左上角一道斜向亮斑（有顶光）
 * 3. **边缘斜面**：左上亮边 + 右下暗边（有折射边）
 *
 * 这三层是玻璃质感的最小充分集，缺任何一层都会"塌"回半透明色块。
 *
 * ## 关于真实高斯模糊
 *
 * Compose 的 `Modifier.blur()` **只在 API 31+ 生效**，31 以下是 no-op，
 * 而主人要求支持 Android 10（API 29）。所以本组件不依赖运行时模糊，
 * 改由 [AuroraBackground] 直接画"已经糊掉"的大半径光斑——
 * 数学上等价于模糊结果，且全 API 表现一致、零离屏开销。
 * [supportsRuntimeBlur] 只用来在高版本上微调填充浓度。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassTokens.RadiusMedium),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactive: Boolean = onClick != null,
    darkTheme: Boolean = true,
    /** 玻璃面额外的着色（例如主按钮用品牌色微微染一下） */
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        !interactive || !enabled -> GlassTokens.ScaleResting
        pressed -> GlassTokens.ScalePressed
        hovered -> GlassTokens.ScaleHovered
        else -> GlassTokens.ScaleResting
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.EasingStandard),
        label = "glass-scale",
    )

    val targetElevation = when {
        !interactive || !enabled -> GlassTokens.ElevationResting
        pressed -> GlassTokens.ElevationPressed
        hovered -> GlassTokens.ElevationHovered
        else -> GlassTokens.ElevationResting
    }
    val elevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        label = "glass-elevation",
    )

    val fillBrush = rememberGlassFill(darkTheme, tint)
    val borderBrush = rememberGlassBorder(darkTheme)
    val specularColor = if (darkTheme) GlassTokens.SpecularDark else GlassTokens.SpecularLight

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            // 第 0 层：无障碍兜底底色（保证正文对比度不随背景漂移）
            .background(
                if (darkTheme) {
                    GlassTokens.A11yBackdropDark.copy(alpha = 0.42f)
                } else {
                    GlassTokens.A11yBackdropLight.copy(alpha = 0.52f)
                },
            )
            // 第 1 层：厚度渐变
            .background(fillBrush)
            // 第 2 层：左上镜面高光
            .drawWithCache {
                val radius = size.maxDimension * 0.75f
                val brush = Brush.radialGradient(
                    colors = listOf(specularColor, Color.Transparent),
                    center = Offset(size.width * 0.12f, -size.height * 0.25f),
                    radius = radius,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = brush)
                }
            }
            // 第 3 层：边缘斜面
            .border(GlassTokens.BorderWidth, borderBrush, shape)
            .then(
                if (interactive) {
                    Modifier
                        .hoverable(interactionSource, enabled = enabled)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(
                                color = if (darkTheme) Color.White else Color.Black,
                            ),
                            enabled = enabled && onClick != null,
                            onClick = { onClick?.invoke() },
                        )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/** 玻璃面填充：上亮下暗的竖向渐变 */
@Composable
private fun rememberGlassFill(darkTheme: Boolean, tint: Color): Brush {
    val blurCapable = supportsRuntimeBlur()
    return remember(darkTheme, tint, blurCapable) {
        val top = when {
            darkTheme && blurCapable -> GlassTokens.FillDarkTop
            darkTheme -> GlassTokens.FillDarkTopNoBlur
            blurCapable -> GlassTokens.FillLightTop
            else -> GlassTokens.FillLightTopNoBlur
        }
        val bottom = when {
            darkTheme && blurCapable -> GlassTokens.FillDarkBottom
            darkTheme -> GlassTokens.FillDarkBottomNoBlur
            blurCapable -> GlassTokens.FillLightBottom
            else -> GlassTokens.FillLightBottomNoBlur
        }
        val colors = if (tint != Color.Unspecified) {
            listOf(blend(top, tint), blend(bottom, tint))
        } else {
            listOf(top, bottom)
        }
        Brush.verticalGradient(colors)
    }
}

/**
 * 边缘斜面。
 *
 * 用**对角**线性渐变而不是均匀描边：左上角接近纯白、右下角几乎透明，
 * 视觉上就成了一圈有厚度的折射边，这是塑料感与玻璃感的分界线。
 */
@Composable
private fun rememberGlassBorder(darkTheme: Boolean): Brush = remember(darkTheme) {
    Brush.linearGradient(
        colors = if (darkTheme) {
            listOf(
                GlassTokens.BorderHighlightDark,
                GlassTokens.BorderHighlightDark.copy(alpha = 0.16f),
                GlassTokens.BorderShadowDark,
            )
        } else {
            listOf(
                GlassTokens.BorderHighlightLight,
                GlassTokens.BorderHighlightLight.copy(alpha = 0.30f),
                GlassTokens.BorderShadowLight,
            )
        },
    )
}

private fun blend(base: Color, tint: Color): Color = Color(
    red = base.red * 0.55f + tint.red * 0.45f,
    green = base.green * 0.55f + tint.green * 0.45f,
    blue = base.blue * 0.55f + tint.blue * 0.45f,
    alpha = (base.alpha + tint.alpha * 0.5f).coerceAtMost(1f),
)

/**
 * 当前设备是否支持运行时高斯模糊。
 *
 * `Modifier.blur()` / `RenderEffect.createBlurEffect()` 均要求 API 31+。
 * 低于 31 调用不崩但完全无效，所以必须显式判断后走降级方案。
 */
fun supportsRuntimeBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 常用尺寸预设 */
object GlassDefaults {
    val cardShape: Shape get() = RoundedCornerShape(GlassTokens.RadiusMedium)
    val panelShape: Shape get() = RoundedCornerShape(GlassTokens.RadiusLarge)
    val pillShape: Shape get() = RoundedCornerShape(GlassTokens.RadiusPill)
    val blurRadius: Dp get() = GlassTokens.BlurRegular
}
