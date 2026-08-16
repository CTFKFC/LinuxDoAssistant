package com.ydm.linuxdo.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃拟态设计令牌。
 *
 * ## v2 调整（针对「视觉对比太弱、不够玻璃」的反馈）
 *
 * 玻璃感 = **透**（看得到背后的光）+ **厚**（有边缘折射）+ **亮**（有高光）。
 * v1 三样都太保守：填充 alpha 只有 0.14、边缘高光 0.28、背景光斑 0.55，
 * 叠在一起就是一片灰扑扑的半透明板。
 *
 * v2 的做法：
 * - 填充上下差拉大（0.22 → 0.06），制造"玻璃有厚度"的渐变
 * - 边缘做成**斜面**：左上亮边 0.55 + 右下暗边，而不是均匀描边
 * - 新增**镜面高光**：左上角一道斜向光斑，模拟顶光打在玻璃面上
 * - 背景光斑饱和度与半径都加大，让透过来的颜色真的看得出来
 */
object GlassTokens {

    // ---------------------------------------------------------------
    // 圆角
    // ---------------------------------------------------------------
    val RadiusSmall: Dp = 14.dp
    val RadiusMedium: Dp = 22.dp
    val RadiusLarge: Dp = 30.dp
    val RadiusPill: Dp = 999.dp

    // ---------------------------------------------------------------
    // 模糊半径（仅 API 31+ 生效）
    // ---------------------------------------------------------------
    val BlurSubtle: Dp = 16.dp
    val BlurRegular: Dp = 32.dp
    val BlurStrong: Dp = 56.dp

    // ---------------------------------------------------------------
    // 玻璃层填充：上亮下暗，差值拉大才有厚度感
    // ---------------------------------------------------------------
    val FillDarkTop = Color.White.copy(alpha = 0.22f)
    val FillDarkBottom = Color.White.copy(alpha = 0.06f)
    val FillLightTop = Color.White.copy(alpha = 0.82f)
    val FillLightBottom = Color.White.copy(alpha = 0.52f)

    /** API 29-30 无实时模糊，填充再实一档补偿 */
    val FillDarkTopNoBlur = Color.White.copy(alpha = 0.26f)
    val FillDarkBottomNoBlur = Color.White.copy(alpha = 0.09f)
    val FillLightTopNoBlur = Color.White.copy(alpha = 0.90f)
    val FillLightBottomNoBlur = Color.White.copy(alpha = 0.66f)

    // ---------------------------------------------------------------
    // 边缘斜面：左上亮、右下暗，是"玻璃有厚度"最关键的一笔
    // ---------------------------------------------------------------
    val BorderWidth: Dp = 1.dp
    val BorderHighlightDark = Color.White.copy(alpha = 0.55f)
    val BorderShadowDark = Color.White.copy(alpha = 0.03f)
    val BorderHighlightLight = Color.White.copy(alpha = 0.95f)
    val BorderShadowLight = Color.Black.copy(alpha = 0.10f)

    // ---------------------------------------------------------------
    // 镜面高光：左上角的一道斜向亮斑
    // ---------------------------------------------------------------
    val SpecularDark = Color.White.copy(alpha = 0.18f)
    val SpecularLight = Color.White.copy(alpha = 0.55f)

    // ---------------------------------------------------------------
    // 阴影
    // ---------------------------------------------------------------
    val ElevationResting: Dp = 3.dp
    val ElevationHovered: Dp = 14.dp
    val ElevationPressed: Dp = 1.dp

    // ---------------------------------------------------------------
    // 交互缩放
    // ---------------------------------------------------------------
    const val ScaleResting = 1.00f
    const val ScaleHovered = 1.025f
    const val ScalePressed = 0.965f

    // ---------------------------------------------------------------
    // 背景极光：饱和度拉满，玻璃才有东西可折射
    // ---------------------------------------------------------------
    val AuroraCyan = Color(0xFF00E5FF)
    val AuroraBlue = Color(0xFF2E6BFF)
    val AuroraViolet = Color(0xFF9D4EFF)
    val AuroraMagenta = Color(0xFFFF2E93)
    val AuroraTeal = Color(0xFF00FFC2)

    /** 底色压得更黑，光斑才跳得出来 */
    val ScrimDark = Color(0xFF05060C)
    val ScrimLight = Color(0xFFEEF2F9)

    /**
     * 玻璃层背后垫的实色底。
     *
     * 无障碍要求正文对比度 ≥ 4.5:1。半透明玻璃本身保证不了（背景亮度会漂），
     * 所以统一先铺一层不透明底再叠玻璃。
     */
    val A11yBackdropDark = Color(0xFF0B0E18)
    val A11yBackdropLight = Color(0xFFFFFFFF)
}

/**
 * 动效令牌。
 *
 * v2 补了「入场错峰」与「呼吸」两组时长——这是「不够高级」反馈的主要缺口：
 * 静态构图再精致，没有生命感也会显得平。
 */
object MotionTokens {
    const val DurationInstant = 90
    const val DurationFast = 140
    const val DurationMedium = 260
    const val DurationSlow = 460
    const val DurationEntrance = 520
    const val DurationAmbient = 12_000

    /** 卡片入场每张错开的毫秒数 */
    const val StaggerStepMillis = 55

    /** 运行时呼吸光效周期 */
    const val BreathPeriodMillis = 2_600

    /** 标准缓动：起步快、收尾慢 */
    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 强调缓动：主按钮、数字变化，带一点"弹" */
    val EasingEmphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 入场缓动：从下方浮起并淡入 */
    val EasingEntrance: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    val EasingExit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SpringDampingRatio = 0.72f
    const val SpringStiffness = 340f
}
