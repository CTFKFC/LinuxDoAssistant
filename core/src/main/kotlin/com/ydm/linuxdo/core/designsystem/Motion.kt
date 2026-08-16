package com.ydm.linuxdo.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 入场动效：从下方浮起并淡入，按 [index] 错峰。
 *
 * 一屏卡片同时出现会显得"啪"地一下很廉价；错开 55ms 依次浮起，
 * 视觉上就有了层次和分量。这是「高级感」最便宜也最有效的一招。
 */
fun Modifier.enterStaggered(index: Int, enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.toLong() * MotionTokens.StaggerStepMillis)
        visible = true
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = MotionTokens.DurationEntrance,
            easing = MotionTokens.EasingEntrance,
        ),
        label = "enter-$index",
    )

    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 36f
        // 轻微放大，让"浮起"更立体
        val s = 0.96f + 0.04f * progress
        scaleX = s
        scaleY = s
    }
}

/**
 * 呼吸光效：脚本运行时给关键元素加一层缓慢明暗脉动。
 *
 * 作用是让界面「活着」——用户瞟一眼就知道脚本没死，
 * 比任何文字状态都直观。
 */
@Composable
fun rememberBreathAlpha(active: Boolean, min: Float = 0.55f, max: Float = 1f): Float {
    if (!active) return max
    val transition = rememberInfiniteTransition(label = "breath")
    val value by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.BreathPeriodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath-alpha",
    )
    return value
}

/**
 * 数字滚动：统计值变化时平滑过渡，而不是直接跳变。
 *
 * 爬楼数每几秒 +1，直接跳数字会有"闪"的感觉；
 * 用 260ms 的强调缓动过渡过去，观感上就顺滑很多。
 */
@Composable
fun animatedCount(target: Int): Int {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = MotionTokens.DurationMedium,
            easing = MotionTokens.EasingEmphasized,
        ),
        label = "count",
    )
    return value
}

/** 浮动光晕：给主按钮之类的元素加一圈随呼吸变化的外发光 */
@Composable
fun rememberGlowRadius(active: Boolean, base: Float = 0f, peak: Float = 18f): Float {
    if (!active) return base
    val transition = rememberInfiniteTransition(label = "glow")
    val value by transition.animateFloat(
        initialValue = base,
        targetValue = peak,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.BreathPeriodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-radius",
    )
    return value
}
