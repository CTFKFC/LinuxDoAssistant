package com.ydm.linuxdo.ui.dashboard

import com.ydm.linuxdo.automation.model.LevelInfo
import com.ydm.linuxdo.automation.model.RunStats

/**
 * 仪表盘 UI 状态。
 *
 * 刻意区分「站点真实数据」和「本次运行的本地统计」：
 * - [levelInfo] 来自 connect.linux.do，是站点的权威数字
 * - [stats] 是本次会话的本地计数
 *
 * 上游把两者混在一起显示（运行中把本地估算值直接加到站点数字上再展示），
 * 导致界面上的数字既不是站点真值也不是本次增量。这里分开展示、分别标注来源。
 */
data class DashboardUiState(
    val engineStatus: EngineStatus = EngineStatus.IDLE,
    val statusDetail: String = "未开始",
    val levelInfo: LevelInfo? = null,
    val stats: RunStats = RunStats(),
    /** 本次运行相对开始时的站点增量（结束后才有值） */
    val siteDelta: Map<String, Int> = emptyMap(),
    val overallProgress: Float? = null,
    val loggedIn: Boolean = false,
    val username: String = "",
    val elapsedText: String = "0:00",
    val remainingText: String? = null,
    /** 有本地会话记录时的欢迎语，例如「欢迎回来，foo」 */
    val greeting: String? = null,
    /** 实时运行日志，喂给终端风格面板 */
    val logs: List<com.ydm.linuxdo.automation.model.LogEntry> = emptyList(),
)

enum class EngineStatus {
    IDLE,
    WAITING_LOGIN,
    RUNNING,
    PAUSED,
    FINISHED,
    FAILED,
}
