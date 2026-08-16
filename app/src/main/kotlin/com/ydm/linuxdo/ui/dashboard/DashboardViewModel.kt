package com.ydm.linuxdo.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ydm.linuxdo.LinuxDoApp
import com.ydm.linuxdo.automation.model.AutomationState
import com.ydm.linuxdo.automation.model.RunConfig
import com.ydm.linuxdo.automation.model.StopCondition
import com.ydm.linuxdo.core.automation.AutomationController
import com.ydm.linuxdo.core.data.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 仪表盘 ViewModel。
 *
 * ## 登录判定顺序（按用户建议重排）
 *
 * v1.1.0 只靠「在页面里跑 JS 查 #current-user」判断登录，
 * 必须等页面加载完（还可能卡在 Cloudflare 挑战），
 * 于是出现「明明登录了却一直显示等待登录」。
 *
 * 现在改成三级递进，快的先出结果：
 *
 * 1. **读 Cookie**（毫秒级）—— 有 Discourse 的 `_t` 票据就先认为已登录
 * 2. **对指纹**（毫秒级）—— 票据和上次存的一致，直接用本地用户名显示「欢迎回来，xxx」
 * 3. **跑 JS + 抓等级**（秒级）—— 拿到权威用户名后回存，供下次冷启动命中
 *
 * 没有票据才提示用户去登录。
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val data = (app as LinuxDoApp).data

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** 用户在「任务」页配置的运行参数 */
    var runConfig: RunConfig = RunConfig(
        stopCondition = StopCondition.TopicCount(30),
    )

    init {
        viewModelScope.launch {
            AutomationController.logs.collect { entries ->
                _uiState.value = _uiState.value.copy(logs = entries)
            }
        }

        viewModelScope.launch {
            combine(
                AutomationController.state,
                AutomationController.stats,
                AutomationController.levelInfo,
                AutomationController.siteDelta,
            ) { state, stats, level, delta ->
                val current = _uiState.value
                current.copy(
                    engineStatus = state.toStatus(),
                    statusDetail = state.describe(),
                    levelInfo = level,
                    stats = stats,
                    siteDelta = delta,
                    overallProgress = level?.overallProgress,
                    // 站点权威用户名优先，其次用本地存的
                    username = level?.username?.takeIf { it.isNotBlank() } ?: current.username,
                    elapsedText = formatElapsed(stats.startedAtMillis),
                )
            }.collect { _uiState.value = it }
        }

        // ---- 第 1、2 级：Cookie 判定，冷启动立刻出结果 ----
        viewModelScope.launch {
            restoreSessionFromCookie()
        }

        // ---- 第 3 级：等级信息回来后，把权威用户名存下来 ----
        viewModelScope.launch {
            AutomationController.levelInfo.collect { info ->
                val name = info?.username.orEmpty()
                if (name.isNotBlank()) {
                    SessionStore.readAuthCookie()?.let { cookie ->
                        data.sessionStore.save(name, SessionStore.fingerprint(cookie))
                    }
                }
            }
        }

        // 用时每秒刷新
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val s = _uiState.value
                if (s.engineStatus == EngineStatus.RUNNING ||
                    s.engineStatus == EngineStatus.WAITING_LOGIN
                ) {
                    _uiState.value = s.copy(elapsedText = formatElapsed(s.stats.startedAtMillis))
                }
            }
        }
    }

    /**
     * 冷启动时的快速登录判定。
     *
     * 只读 Cookie 与本地记录，不碰页面——所以不会打断可能正在进行的
     * Cloudflare 无感验证，也不需要等页面加载。
     */
    private suspend fun restoreSessionFromCookie() {
        // 等数据层把本地会话读出来
        delay(300)

        val cookie = SessionStore.readAuthCookie()
        if (cookie == null) {
            _uiState.value = _uiState.value.copy(loggedIn = false, greeting = null)
            return
        }

        val saved = data.sessionStore.session.value
        val sameSession = saved.matches(cookie)

        _uiState.value = _uiState.value.copy(
            loggedIn = true,
            username = if (sameSession) saved.username else _uiState.value.username,
            greeting = if (sameSession && saved.hasUsername) {
                "欢迎回来，${saved.username}"
            } else {
                null
            },
        )
    }

    fun start() {
        AutomationController.start(runConfig)
    }

    fun stop() {
        AutomationController.stop()
    }

    private fun formatElapsed(startedAtMillis: Long): String {
        if (startedAtMillis <= 0L) return "0:00"
        val seconds = ((System.currentTimeMillis() - startedAtMillis) / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

private fun AutomationState.toStatus(): EngineStatus = when (this) {
    AutomationState.Idle -> EngineStatus.IDLE
    is AutomationState.WaitingForLogin -> EngineStatus.WAITING_LOGIN
    AutomationState.Paused -> EngineStatus.PAUSED
    is AutomationState.Finished -> EngineStatus.FINISHED
    is AutomationState.Failed -> EngineStatus.FAILED
    else -> EngineStatus.RUNNING
}

private fun AutomationState.describe(): String = when (this) {
    AutomationState.Idle -> "未开始"
    is AutomationState.WaitingForLogin -> "等待登录 ${elapsedSeconds}s"
    AutomationState.FetchingLevelInfo -> "获取等级"
    is AutomationState.EnteringCategory -> "进入 ${category.name}"
    is AutomationState.ListingTopics -> "读取话题列表"
    is AutomationState.BrowsingTopic ->
        floor?.let { "爬楼 ${it.current}/${it.total}" } ?: "浏览话题"
    is AutomationState.Liking -> "点赞"
    is AutomationState.Replying -> "回复中"
    AutomationState.Paused -> "已暂停"
    is AutomationState.Finished -> "已完成"
    is AutomationState.Failed -> "出错"
}
