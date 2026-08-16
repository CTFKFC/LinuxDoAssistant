package com.ydm.linuxdo.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 全部自绘图标——本项目**零图标依赖**。
 *
 * ## 为什么不用 material-icons
 *
 * - `material-icons-extended` 有上千个图标类，在本机（双核 Celeron / 1.7GB 内存）上
 *   `mergeExtDexDebug` 一个任务就跑十几分钟不出结果，APK 从 29MB 胀到 71MB。
 * - `material-icons-core` 又**不是** material3 的传递依赖，得单独引，
 *   而项目总共只用 9 个图标，为此多引一个库不划算。
 *
 * 于是全部用官方 Material 的 SVG 路径数据自绘。构建快了一个数量级，APK 也瘦回去了。
 */
object AppIcons {

    private fun icon(name: String, pathData: String): ImageVector {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = nodes,
            fill = SolidColor(Color.Black),
        ).build()
    }

    /** 仪表盘：四宫格 */
    val Dashboard: ImageVector by lazy {
        icon(
            "Dashboard",
            "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z",
        )
    }

    /** 浏览器：地球 */
    val Public: ImageVector by lazy {
        icon(
            "Public",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 " +
                "17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9" +
                "-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1" +
                " 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z",
        )
    }

    /** 任务：闪电 */
    val Bolt: ImageVector by lazy {
        icon(
            "Bolt",
            "M11 21h-1l1-7H7.5c-.88 0-.33-.75-.31-.78C8.48 10.94 10.42 7.54 13 3h1l-1 " +
                "7h3.5c.4 0 .62.19.4.66C12.96 17.55 11 21 11 21z",
        )
    }

    /** 停止：实心方块 */
    val Stop: ImageVector by lazy {
        icon("Stop", "M6 6h12v12H6z")
    }

    /** 播放 */
    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow", "M8 5v14l11-7z")
    }

    /** 设置：齿轮 */
    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41" +
                ".12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36" +
                "-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.5" +
                "7-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58" +
                "c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12." +
                "22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 " +
                "0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1." +
                "92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3" +
                ".6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
        )
    }

    /** 刷新 */
    val Refresh: ImageVector by lazy {
        icon(
            "Refresh",
            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.58 8 8 8c3.73 0 6.84" +
                "-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3." +
                "14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
        )
    }

    /** 首页 */
    val Home: ImageVector by lazy {
        icon("Home", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z")
    }

    /** 后退箭头 */
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    }

    /** 前进箭头 */
    val ArrowForward: ImageVector by lazy {
        icon("ArrowForward", "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z")
    }
}
