package com.ydm.linuxdo.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 当前是否深色主题，供玻璃组件按主题选填充色 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** 当前设备是否支持运行时高斯模糊（API 31+），供组件决定用真模糊还是降级方案 */
val LocalBlurSupported = staticCompositionLocalOf { false }

private val BrandCyan = Color(0xFF3DE8FF)
private val BrandBlue = Color(0xFF5B8CFF)
private val BrandViolet = Color(0xFFA974FF)
private val BrandGreen = Color(0xFF3DFFA8)
private val BrandAmber = Color(0xFFFFC64D)
private val BrandRose = Color(0xFFFF6B8A)

private val DarkScheme = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF00323C),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFA6F0FF),
    secondary = BrandViolet,
    onSecondary = Color(0xFF23103F),
    tertiary = BrandGreen,
    onTertiary = Color(0xFF00382A),
    error = BrandRose,
    background = Color(0xFF05060C),
    onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF0B0E18),
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = Color(0xFF161C2E),
    onSurfaceVariant = Color(0xFFA8B4CC),
    outline = Color(0xFF39435C),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color.White,
    secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFF059669),
    error = Color(0xFFE11D48),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE7EBF3),
    onSurfaceVariant = Color(0xFF44506A),
    outline = Color(0xFFC3CBDA),
)

/** 数值展示用的等宽风格字体，让统计数字对齐不跳动 */
private val AppTypography = Typography().run {
    copy(
        // 主视觉数字：字重拉满、字距收紧，才有"仪表"的分量
        displayLarge = displayLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = displayMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        displaySmall = displaySmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        // 小标签用宽字距，和紧排的数字形成对比
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    )
}

/** 统计数字专用样式 */
val StatNumberStyle = TextStyle(
    fontSize = 30.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1).sp,
)

/** 仪表盘主视觉的超大数字 */
val HeroNumberStyle = TextStyle(
    fontSize = 56.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-2.5).sp,
)

@Composable
fun LinuxDoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 是否跟随系统取色（Monet，API 31+） */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // ★ 动态取色是 API 31+ 独有，minSdk 29 必须做版本判断，
    //   否则在 Android 10/11 上会 NoSuchMethodError 闪退。
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalBlurSupported provides supportsRuntimeBlur(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** 状态色语义（运行中/暂停/完成/失败） */
object StatusColors {
    val running = BrandGreen
    val paused = BrandAmber
    val finished = BrandBlue
    val failed = BrandRose
    val idle = Color(0xFF64748B)
}
