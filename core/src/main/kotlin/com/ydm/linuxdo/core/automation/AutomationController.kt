package com.ydm.linuxdo.core.automation

import com.ydm.linuxdo.automation.engine.AutomationEngine
import com.ydm.linuxdo.automation.engine.TemplateProvider
import com.ydm.linuxdo.automation.model.AutomationState
import com.ydm.linuxdo.automation.model.Category
import com.ydm.linuxdo.automation.model.DefaultCategories
import com.ydm.linuxdo.automation.model.DefaultReplyTemplates
import com.ydm.linuxdo.automation.model.LevelInfo
import com.ydm.linuxdo.automation.model.LogEntry
import com.ydm.linuxdo.automation.model.RunConfig
import com.ydm.linuxdo.automation.model.RunStats
import com.ydm.linuxdo.core.browser.WebViewPageAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 自动化控制器——App 进程内唯一的引擎持有者。
 *
 * UI（仪表盘）、前台服务、悬浮窗都通过它观察同一份状态、发同一套指令，
 * 不各自持有引擎实例。这样切标签、退到后台、从悬浮窗操作，看到的都是同一个真相。
 */
object AutomationController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val agent = WebViewPageAgent()

    /**
     * 模板来源。P4 接入 Room 后会被替换成数据库实现；
     * 在那之前先用内置的 65 条种子数据，保证功能可用。
     */
    @Volatile
    var templateProvider: TemplateProvider = SeedTemplateProvider

    private var engine: AutomationEngine? = null
    private var job: Job? = null

    private val _state = MutableStateFlow<AutomationState>(AutomationState.Idle)
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(RunStats())
    val stats: StateFlow<RunStats> = _stats.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _levelInfo = MutableStateFlow<LevelInfo?>(null)
    val levelInfo: StateFlow<LevelInfo?> = _levelInfo.asStateFlow()

    /** 运行前后的站点真实增量，key 为指标原始名 */
    private val _siteDelta = MutableStateFlow<Map<String, Int>>(emptyMap())
    val siteDelta: StateFlow<Map<String, Int>> = _siteDelta.asStateFlow()

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(
        config: RunConfig,
        categories: List<Category> = DefaultCategories.defaultEnabled,
    ) {
        if (isRunning) return

        _logs.value = emptyList()
        _siteDelta.value = emptyMap()

        val e = AutomationEngine(
            agent = agent,
            templates = templateProvider,
        )
        engine = e

        // 三条流各自转发到控制器的状态上，UI 只订阅控制器
        scope.launch { e.state.collect { _state.value = it } }
        scope.launch { e.stats.collect { _stats.value = it } }
        scope.launch {
            e.logs.collect { entry ->
                _logs.value = (_logs.value + entry).takeLast(MAX_LOG_ENTRIES)
            }
        }

        job = scope.launch {
            e.run(config = config, categories = categories)
            _levelInfo.value = e.finalLevelInfo ?: e.initialLevelInfo
            _siteDelta.value = computeDelta(e.initialLevelInfo, e.finalLevelInfo)
        }

        // 首次拿到等级信息就先显示，不必等跑完
        scope.launch {
            while (job?.isActive == true) {
                e.initialLevelInfo?.let { if (_levelInfo.value == null) _levelInfo.value = it }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    fun stop() {
        engine?.requestStop()
    }

    fun pause() {
        engine?.requestPause()
    }

    fun resume() {
        engine?.requestResume()
    }

    val isPaused: Boolean get() = engine?.isPaused == true

    /** 供「登录检测」用：不启动完整会话，只问一下当前是否已登录 */
    suspend fun checkLoggedIn(): Boolean = runCatching {
        agent.injectAgent()
        agent.isLoggedIn()
    }.getOrDefault(false)

    suspend fun currentUsername(): String = runCatching {
        agent.loggedInUsername()
    }.getOrDefault("")

    private fun computeDelta(before: LevelInfo?, after: LevelInfo?): Map<String, Int> {
        if (before == null || after == null) return emptyMap()
        val beforeMap = before.requirements.associateBy { it.rawName }
        return buildMap {
            after.requirements.forEach { req ->
                val old = beforeMap[req.rawName]?.currentInt
                val now = req.currentInt
                if (old != null && now != null) put(req.rawName, now - old)
            }
        }
    }

    private const val MAX_LOG_ENTRIES = 500
}

/**
 * 内置模板提供者（P4 前的过渡实现）。
 *
 * 直接用移植过来的 65 条种子数据，等 Room 就位后由数据库实现接管。
 */
object SeedTemplateProvider : TemplateProvider {
    private val contents = DefaultReplyTemplates.all.map { it.content }

    override suspend fun pickRandom(random: Random): String? =
        contents.randomOrNull(random)
}
