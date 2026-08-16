package com.ydm.linuxdo.automation.engine

import com.ydm.linuxdo.automation.model.AutomationState
import com.ydm.linuxdo.automation.model.BrowseMode
import com.ydm.linuxdo.automation.model.Category
import com.ydm.linuxdo.automation.model.FinishReason
import com.ydm.linuxdo.automation.model.LevelInfo
import com.ydm.linuxdo.automation.model.LogEntry
import com.ydm.linuxdo.automation.model.LogLevel
import com.ydm.linuxdo.automation.model.RunConfig
import com.ydm.linuxdo.automation.model.RunStats
import com.ydm.linuxdo.automation.model.StopCondition
import com.ydm.linuxdo.automation.model.TopicItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * 自动化引擎——整个 App 唯一的脚本执行者。
 *
 * ## 设计要点
 *
 * - **单一实现**：UI、前台服务、悬浮窗都只是这个引擎的观察者。
 *   上游把同一套爬虫抄了 4 份（gui / headless / docker / auto_browse），
 *   板块列表都已经开始漂移了。
 * - **可测试**：[PageAgent]、[Delayer]、[Random] 全部注入，
 *   纯 JVM 单测可以零延迟跑完整会话。
 * - **不造假数据**：拿不到楼层计数器时记 0 并累加 `floorsUnreliable`，
 *   而不是像上游那样 `stats["floors"] += 3`。
 *
 * 状态通过 [state] / [stats] / [logs] 三条流对外暴露。
 */
class AutomationEngine(
    private val agent: PageAgent,
    private val templates: TemplateProvider,
    private val delayer: Delayer = Delayer.Real,
    private val random: Random = Random.Default,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectUrl: String = DEFAULT_CONNECT_URL,
) {

    private val _state = MutableStateFlow<AutomationState>(AutomationState.Idle)
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(RunStats())
    val stats: StateFlow<RunStats> = _stats.asStateFlow()

    private val _logs = MutableSharedFlow<LogEntry>(replay = 200, extraBufferCapacity = 200)
    val logs: SharedFlow<LogEntry> = _logs.asSharedFlow()

    /** 运行前的等级快照，用于结束时算真实增量 */
    var initialLevelInfo: LevelInfo? = null
        private set

    /** 运行后的等级快照 */
    var finalLevelInfo: LevelInfo? = null
        private set

    @Volatile
    private var stopRequested = false

    @Volatile
    private var pauseRequested = false

    fun requestStop() {
        stopRequested = true
    }

    fun requestPause() {
        pauseRequested = true
    }

    fun requestResume() {
        pauseRequested = false
    }

    val isPaused: Boolean get() = pauseRequested

    // ===================================================================
    // 主流程
    // ===================================================================

    /**
     * 跑一次完整会话。这是个挂起函数，调用方负责放到合适的协程作用域里。
     *
     * @param config 本次运行配置
     * @param categories 参与轮换的板块（已按用户勾选过滤）
     * @param loginTimeoutMillis 等待用户手动登录的最长时间
     */
    suspend fun run(
        config: RunConfig,
        categories: List<Category>,
        loginTimeoutMillis: Long = DEFAULT_LOGIN_TIMEOUT_MILLIS,
    ) {
        stopRequested = false
        pauseRequested = false

        val startedAt = delayer.nowMillis()
        _stats.value = RunStats(startedAtMillis = startedAt)
        initialLevelInfo = null
        finalLevelInfo = null

        try {
            // --- 1. 等用户手动登录（人机验证只能人来过） ---
            if (!awaitLogin(loginTimeoutMillis, startedAt)) {
                finish(FinishReason.LOGIN_TIMEOUT)
                return
            }

            if (categories.isEmpty()) {
                log(LogLevel.ERROR, "没有启用任何板块，无法开始")
                finish(FinishReason.NO_CATEGORIES)
                return
            }

            // --- 2. 抓运行前的等级快照 ---
            _state.value = AutomationState.FetchingLevelInfo
            initialLevelInfo = fetchLevelInfo()?.also {
                log(LogLevel.INFO, "当前等级 ${it.level} 级，目标 ${it.nextLevel} 级")
            }

            logRunHeader(config, categories)

            // --- 3. 板块轮换主循环 ---
            var rotation = categories.shuffled(random)
            while (shouldKeepRunning(config, startedAt)) {
                for (category in rotation) {
                    if (!shouldKeepRunning(config, startedAt)) break
                    browseCategory(category, config, startedAt)
                    if (!shouldKeepRunning(config, startedAt)) break

                    logProgress(config, startedAt)

                    if (config.enableExtraDelay) {
                        randomDelay(
                            config.waitRangeSeconds.start + 1f,
                            config.waitRangeSeconds.endInclusive + 2f,
                            "切换板块",
                        )
                    }
                }

                // 非无尽模式跑完一轮就退出
                if (config.stopCondition != StopCondition.Endless) break

                rotation = rotation.shuffled(random)
                log(LogLevel.INFO, "开始下一轮浏览")
            }

            finish(if (stopRequested) FinishReason.USER_STOPPED else FinishReason.TARGET_REACHED)
        } catch (e: CancellationException) {
            // 协程被取消（例如服务被杀），正常路径，不当作错误
            _state.value = AutomationState.Finished(FinishReason.USER_STOPPED, _stats.value)
            throw e
        } catch (e: Throwable) {
            log(LogLevel.ERROR, "运行出错: ${e.message}")
            _state.value = AutomationState.Failed(
                message = e.message ?: e::class.simpleName.orEmpty(),
                cause = e.cause?.message,
            )
        }
    }

    // ===================================================================
    // 登录
    // ===================================================================

    /**
     * 轮询等待用户完成登录。
     *
     * ★ 绝不代填账号密码：L 站有人机验证，上游 headless 版那套自动填表
     *   在真实环境下根本过不去，而且把密码塞进命令行/环境变量本身就不安全。
     */
    private suspend fun awaitLogin(timeoutMillis: Long, startedAt: Long): Boolean {
        // ★ 只在明显不在站内时才导航。
        //   用户可能正停在 Cloudflare 挑战页上，这时候 navigate 会把无感验证打断，
        //   回到原点重新验证——PC 端作者当年也踩过，所以他的实现里注释写着
        //   "不刷新页面，避免打断用户输入"。
        val current = agent.currentUrl()
        if (!current.contains("linux.do")) {
            agent.navigate(baseUrl)
        }
        agent.injectAgent()

        if (agent.isLoggedIn()) {
            val name = agent.loggedInUsername()
            log(LogLevel.SUCCESS, "已登录${if (name.isNotBlank()) "：$name" else ""}")
            return true
        }

        var challengeLogged = false
        var hintLogged = false

        while (coroutineContext.isActive && !stopRequested) {
            val elapsed = delayer.nowMillis() - startedAt
            if (elapsed >= timeoutMillis) {
                log(LogLevel.ERROR, "等待登录超时（${timeoutMillis / 1000} 秒）")
                return false
            }

            _state.value = AutomationState.WaitingForLogin(elapsed / 1000)
            delayer.sleep(LOGIN_POLL_INTERVAL_MILLIS)

            agent.injectAgent()

            // 先看是不是卡在人机验证上——是的话就安静等着，绝不碰页面
            val (challenge, interactive) = agent.detectChallenge()
            if (challenge) {
                if (!challengeLogged) {
                    log(
                        LogLevel.WARN,
                        if (interactive) {
                            "检测到人机验证，请在浏览器页点一下验证框"
                        } else {
                            "检测到人机验证（无感），正在自动通过，请勿操作"
                        },
                    )
                    challengeLogged = true
                }
                // ★ 关键：什么都不做，让 Cloudflare 自己转完
                continue
            }
            challengeLogged = false

            if (agent.isLoggedIn()) {
                val name = agent.loggedInUsername()
                log(LogLevel.SUCCESS, "检测到登录成功${if (name.isNotBlank()) "：$name" else ""}，开始运行")
                return true
            }

            if (!hintLogged) {
                log(LogLevel.WARN, "未检测到登录，请在浏览器页手动登录，登录后脚本会自动接管")
                hintLogged = true
            }
        }
        return false
    }

    // ===================================================================
    // 板块
    // ===================================================================

    private suspend fun browseCategory(category: Category, config: RunConfig, startedAt: Long) {
        _state.value = AutomationState.EnteringCategory(category)
        log(LogLevel.INFO, "进入板块：${category.name}")

        agent.navigate(baseUrl + category.path)
        randomDelay(2f, 4f, "板块加载")
        agent.injectAgent()

        // 按回复数排序，优先啃高楼层帖子（爬楼效率更高）
        if (agent.sortByReplies()) {
            log(LogLevel.DEBUG, "已按回复数排序")
            delayer.sleep(2_000)
        }

        _state.value = AutomationState.ListingTopics(category)
        val (unread, read) = agent.getTopics()
        log(LogLevel.INFO, "找到 ${unread.size} 个未读话题，${read.size} 个已读话题")

        val candidates = pickCandidates(unread, read)
        if (candidates.isEmpty()) {
            log(LogLevel.WARN, "板块 ${category.name} 没有可浏览的话题")
            return
        }

        val take = minOf(config.topicsPerCategory.random(random), candidates.size)
        val selected = candidates.shuffled(random).take(take)

        for (topic in selected) {
            if (!shouldKeepRunning(config, startedAt)) break
            browseTopic(category, topic, config, startedAt)
            if (config.enableExtraDelay) {
                randomDelay(0.5f, 1.5f, "准备下一个话题")
            }
        }
    }

    /**
     * 优先浏览未读话题（带小蓝点），不足 3 个时补几个已读的。
     * 与上游 `get_topics()` 的策略一致。
     */
    internal fun pickCandidates(
        unread: List<TopicItem>,
        read: List<TopicItem>,
    ): List<TopicItem> = when {
        unread.isEmpty() -> read
        unread.size < MIN_UNREAD_BEFORE_TOPUP -> unread + read.take(MIN_UNREAD_BEFORE_TOPUP)
        else -> unread
    }

    // ===================================================================
    // 话题
    // ===================================================================

    private suspend fun browseTopic(
        category: Category,
        topic: TopicItem,
        config: RunConfig,
        startedAt: Long,
    ) {
        log(LogLevel.INFO, "浏览${if (topic.unread) "未读" else "已读"}话题：${topic.title}")
        _state.value = AutomationState.BrowsingTopic(category, topic, null)

        // ★ 必须点击链接。直接改 URL 不会被 Discourse 计入「浏览话题」。
        if (!agent.clickTopic(topic.id)) {
            log(LogLevel.WARN, "点击话题失败，跳过：${topic.title}")
            return
        }

        randomDelay(3f, 5f, "话题页加载")
        agent.injectAgent()

        _stats.update { it.copy(topics = it.topics + 1) }

        climbFloors(category, topic, config, startedAt)

        randomDelay(1f, 2f, "阅读后")

        doLikes(topic, config)
        doReply(topic, config)

        // 回列表
        agent.goBack()
        randomDelay(2f, 3f, "返回板块列表")
        agent.injectAgent()

        if (topic.unread) {
            if (agent.checkBadgeGone(topic.id)) {
                log(LogLevel.SUCCESS, "小蓝点已消失，话题已标记为已读")
            } else {
                log(LogLevel.WARN, "小蓝点仍在，可能浏览时长不够")
            }
        }
    }

    // ===================================================================
    // 爬楼
    // ===================================================================

    private suspend fun climbFloors(
        category: Category,
        topic: TopicItem,
        config: RunConfig,
        startedAt: Long,
    ) {
        val info = agent.getFloorInfo()
        if (info == null) {
            // ★ 上游此处 stats["floors"] += 3 凭空造数；这里如实记 0 并打标
            _stats.update { it.copy(floorsUnreliable = it.floorsUnreliable + 1) }
            log(LogLevel.WARN, "读不到楼层计数器，本帖不计入爬楼数（统计可能偏低）")
            fallbackScroll(config)
            return
        }

        when {
            config.browseMode == BrowseMode.QUICK -> climbQuick(category, topic, info.total, info.current, config, startedAt)
            info.total < MIN_FLOORS_FOR_DEEP -> {
                log(LogLevel.DEBUG, "总楼层仅 ${info.total}，走快速浏览")
                climbQuick(category, topic, info.total, info.current, config, startedAt)
            }
            else -> climbDeep(category, topic, info.total, info.current, config, startedAt)
        }
    }

    /**
     * 深度爬楼：读完所有楼层。
     *
     * 节奏参数沿用 PC 端已验证的值：等 2-4s → 滚 600-1200px。
     * 太快楼层计数器不更新，也来不及被判定为已读。
     */
    private suspend fun climbDeep(
        category: Category,
        topic: TopicItem,
        total: Int,
        startFloor: Int,
        config: RunConfig,
        startedAt: Long,
    ) {
        log(LogLevel.INFO, "深度爬楼：总 $total 层，从第 $startFloor 层开始")

        var current = startFloor
        var last = startFloor
        var stuck = 0
        var scrolls = 0

        while (current < total && shouldKeepRunning(config, startedAt)) {
            awaitResume()

            randomDelay(DEEP_WAIT_MIN, DEEP_WAIT_MAX, reason = null)
            agent.scrollBy(random.nextInt(DEEP_SCROLL_MIN, DEEP_SCROLL_MAX))
            scrolls++
            delayer.sleep(500)

            val info = agent.getFloorInfo()
            if (info != null) {
                current = info.current
                if (current > last) {
                    val climbed = current - last
                    _stats.update { it.copy(floors = it.floors + climbed) }
                    last = current
                    stuck = 0
                    _state.value = AutomationState.BrowsingTopic(category, topic, info)
                    log(LogLevel.DEBUG, "爬楼 #$scrolls → $current/$total（本帖已爬 ${current - startFloor} 层）")
                } else {
                    stuck++
                    if (stuck >= STUCK_THRESHOLD) {
                        log(LogLevel.DEBUG, "楼层卡住，加大滚动距离")
                        agent.scrollBy(STUCK_SCROLL_PX)
                        delayer.sleep(1_000)
                        stuck = 0
                    }
                }
            }

            if (scrolls >= MAX_SCROLLS_DEEP) {
                log(LogLevel.WARN, "达到单帖最大滚动次数，结束本帖")
                break
            }
        }

        log(LogLevel.INFO, "爬楼完成：$startFloor → $current，共 ${current - startFloor} 层")
    }

    /** 快速浏览：只爬 3-5 层就换帖，用来拉「浏览话题」数 */
    private suspend fun climbQuick(
        category: Category,
        topic: TopicItem,
        total: Int,
        startFloor: Int,
        config: RunConfig,
        startedAt: Long,
    ) {
        val target = random.nextInt(QUICK_TARGET_MIN, QUICK_TARGET_MAX + 1)
        log(LogLevel.INFO, "快速浏览：目标爬 $target 层（总 $total 层）")

        var current = startFloor
        var last = startFloor
        var scrolls = 0

        while ((current - startFloor) < target && current < total && shouldKeepRunning(config, startedAt)) {
            awaitResume()

            randomDelay(QUICK_WAIT_MIN, QUICK_WAIT_MAX, reason = null)
            agent.scrollBy(random.nextInt(QUICK_SCROLL_MIN, QUICK_SCROLL_MAX))
            scrolls++
            delayer.sleep(300)

            val info = agent.getFloorInfo()
            if (info != null) {
                current = info.current
                if (current > last) {
                    val climbed = current - last
                    _stats.update { it.copy(floors = it.floors + climbed) }
                    last = current
                    _state.value = AutomationState.BrowsingTopic(category, topic, info)
                }
            }

            if (scrolls >= MAX_SCROLLS_QUICK) break
        }

        log(LogLevel.DEBUG, "快速浏览完成：$startFloor → $current")
    }

    /** 读不到楼层信息时的兜底滚动——只是让页面动一动，不计入任何统计 */
    private suspend fun fallbackScroll(config: RunConfig) {
        repeat(FALLBACK_SCROLL_TIMES) {
            if (stopRequested) return
            randomDelay(1f, 2f, reason = null)
            agent.scrollBy(random.nextInt(QUICK_SCROLL_MIN, QUICK_SCROLL_MAX))
        }
    }

    // ===================================================================
    // 点赞 / 回复
    // ===================================================================

    private suspend fun doLikes(topic: TopicItem, config: RunConfig) {
        if (!config.enableLike) return

        val (count, liked) = agent.getLikeState()
        if (count <= 0) return

        // 主帖
        if (random.nextFloat() < config.likeRate && liked.getOrNull(0) != true) {
            _state.value = AutomationState.Liking(topic, 0)
            if (agent.like(0)) {
                _stats.update { it.copy(likesMain = it.likesMain + 1) }
                log(LogLevel.SUCCESS, "点赞主帖")
                if (config.enableExtraDelay) {
                    randomDelay(config.waitRangeSeconds.start, config.waitRangeSeconds.endInclusive, "点赞后")
                }
            }
        }

        // 回复（最多看前 MAX_REPLY_LIKES 个）
        val upper = minOf(count, MAX_REPLY_LIKES)
        for (i in 1 until upper) {
            if (stopRequested) return
            if (liked.getOrNull(i) == true) continue
            if (random.nextFloat() >= config.likeReplyRate) continue

            _state.value = AutomationState.Liking(topic, i)
            if (agent.like(i)) {
                _stats.update { it.copy(likesReply = it.likesReply + 1) }
                log(LogLevel.SUCCESS, "点赞回复 #$i")
                if (config.enableExtraDelay) {
                    randomDelay(config.waitRangeSeconds.start, config.waitRangeSeconds.endInclusive, "点赞回复后")
                }
            }
        }
    }

    private suspend fun doReply(topic: TopicItem, config: RunConfig) {
        if (!config.enableReply) return
        if (random.nextFloat() >= config.replyRate) return

        val content = templates.pickRandom(random)
        if (content.isNullOrBlank()) {
            log(LogLevel.WARN, "没有可用的回复模板，跳过回复")
            return
        }

        _state.value = AutomationState.Replying(topic, content)
        log(LogLevel.INFO, "准备回复：$content")

        if (config.enableExtraDelay) {
            randomDelay(config.waitRangeSeconds.start, config.waitRangeSeconds.endInclusive, "准备回帖")
        }

        if (!agent.openReply()) {
            log(LogLevel.WARN, "未找到回复按钮")
            return
        }
        randomDelay(1.5f, 3f, "等待编辑器")

        if (!agent.isReplyEditorOpen()) {
            log(LogLevel.WARN, "回复编辑器未打开")
            return
        }

        // ★ 内容经 JsArgs 做 JSON 编码后传入，不做任何字符串拼接
        if (!agent.fillReply(content)) {
            log(LogLevel.WARN, "填入回复内容失败")
            return
        }
        randomDelay(0.8f, 1.5f, "输入后")

        if (agent.submitReply()) {
            _stats.update { it.copy(replies = it.replies + 1) }
            log(LogLevel.SUCCESS, "回复已提交")
            randomDelay(2f, 4f, "提交后")
        } else {
            log(LogLevel.WARN, "提交回复失败")
        }
    }

    // ===================================================================
    // 收尾
    // ===================================================================

    private suspend fun finish(reason: FinishReason) {
        // 再抓一次等级信息，算真实增量（而不是本地估算值）
        if (reason != FinishReason.LOGIN_TIMEOUT) {
            _state.value = AutomationState.FetchingLevelInfo
            finalLevelInfo = fetchLevelInfo()
            logLevelDelta()
        }

        val final = _stats.value.copy(elapsedMillis = delayer.nowMillis() - _stats.value.startedAtMillis)
        _stats.value = final

        log(LogLevel.INFO, "本次：话题 ${final.topics}｜爬楼 ${final.floors}｜已读 ${final.totalRead}｜点赞 ${final.likesTotal}｜回复 ${final.replies}")
        if (final.hasUnreliableFloors) {
            log(LogLevel.WARN, "有 ${final.floorsUnreliable} 个话题读不到楼层计数器，爬楼数可能偏低")
        }
        _state.value = AutomationState.Finished(reason, final)
    }

    private suspend fun fetchLevelInfo(): LevelInfo? {
        agent.navigate(connectUrl)
        randomDelay(3f, 4f, "等级页加载")
        agent.injectAgent()
        return agent.getLevelInfo()
    }

    /** 对比运行前后的站点真实数据 */
    private suspend fun logLevelDelta() {
        val before = initialLevelInfo ?: return
        val after = finalLevelInfo ?: return

        log(LogLevel.INFO, "站点真实进度变化：")
        val beforeMap = before.requirements.associateBy { it.rawName }
        after.requirements.forEach { req ->
            val old = beforeMap[req.rawName] ?: return@forEach
            val o = old.currentInt
            val n = req.currentInt
            if (o != null && n != null) {
                val delta = n - o
                val sign = if (delta >= 0) "+$delta" else "$delta"
                log(LogLevel.INFO, "  ${req.displayName}: $o → $n ($sign)")
            } else {
                log(LogLevel.INFO, "  ${req.displayName}: ${old.current} → ${req.current}")
            }
        }
    }

    // ===================================================================
    // 辅助
    // ===================================================================

    private suspend fun shouldKeepRunning(config: RunConfig, startedAt: Long): Boolean {
        if (stopRequested || !coroutineContext.isActive) return false
        return !TargetEvaluator.isReached(
            stats = _stats.value,
            condition = config.stopCondition,
            nowMillis = delayer.nowMillis(),
            startedAtMillis = startedAt,
        )
    }

    /** 暂停时挂在这里，直到恢复或停止 */
    private suspend fun awaitResume() {
        if (!pauseRequested) return
        _state.value = AutomationState.Paused
        while (pauseRequested && !stopRequested && coroutineContext.isActive) {
            delayer.sleep(300)
        }
    }

    private suspend fun randomDelay(minSec: Float, maxSec: Float, reason: String?) {
        val lo = minSec.coerceAtLeast(0f)
        val hi = maxSec.coerceAtLeast(lo)
        val seconds = if (hi <= lo) lo else lo + random.nextFloat() * (hi - lo)
        reason?.let { log(LogLevel.DEBUG, "等待 %.1fs（%s）".format(seconds, it)) }
        delayer.sleep((seconds * 1000).toLong())
    }

    private fun logRunHeader(config: RunConfig, categories: List<Category>) {
        val modeText = when (val c = config.stopCondition) {
            StopCondition.Endless -> "无尽模式（手动停止）"
            is StopCondition.TopicCount -> "目标 ${c.count} 个话题"
            is StopCondition.ReadCount -> "目标已读 ${c.count}（话题+楼层）"
            is StopCondition.Duration -> "运行 ${c.minutes} 分钟"
        }
        val browseText = if (config.browseMode == BrowseMode.DEEP) "深度爬楼" else "快速浏览"
        val features = buildList {
            if (config.enableLike) add("自动点赞")
            if (config.enableReply) add("自动回复")
            if (config.enableExtraDelay) add("额外延迟")
        }.ifEmpty { listOf("仅浏览") }

        log(LogLevel.INFO, "运行模式：$modeText｜$browseText")
        log(LogLevel.INFO, "启用功能：${features.joinToString("、")}")
        log(LogLevel.INFO, "参与板块：${categories.size} 个")
    }

    private fun logProgress(config: RunConfig, startedAt: Long) {
        val s = _stats.value
        val remaining = TargetEvaluator.remaining(
            stats = s,
            condition = config.stopCondition,
            nowMillis = delayer.nowMillis(),
            startedAtMillis = startedAt,
        )
        val tail = when (remaining) {
            is TargetEvaluator.Remaining.Count -> "，剩余 ${remaining.value}"
            is TargetEvaluator.Remaining.Time -> "，剩余 ${remaining.millis / 60_000} 分"
            null -> ""
        }
        log(LogLevel.INFO, "进度：话题 ${s.topics}｜爬楼 ${s.floors}｜已读 ${s.totalRead}$tail")
    }

    private fun log(level: LogLevel, message: String) {
        _logs.tryEmit(LogEntry(delayer.nowMillis(), level, message))
    }

    private inline fun MutableStateFlow<RunStats>.update(block: (RunStats) -> RunStats) {
        value = block(value)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://linux.do"
        const val DEFAULT_CONNECT_URL = "https://connect.linux.do"

        /** 等用户手动登录的默认上限：10 分钟（人机验证可能要点几次） */
        const val DEFAULT_LOGIN_TIMEOUT_MILLIS = 600_000L
        private const val LOGIN_POLL_INTERVAL_MILLIS = 3_000L

        /** 未读话题少于这个数就补几个已读的 */
        internal const val MIN_UNREAD_BEFORE_TOPUP = 3

        /** 总楼层低于这个数没必要深度爬 */
        private const val MIN_FLOORS_FOR_DEEP = 10

        // 深度爬楼节奏（PC 端实测有效的参数，别乱改）
        private const val DEEP_WAIT_MIN = 2f
        private const val DEEP_WAIT_MAX = 4f
        private const val DEEP_SCROLL_MIN = 600
        private const val DEEP_SCROLL_MAX = 1_200
        private const val MAX_SCROLLS_DEEP = 200

        // 快速浏览节奏
        private const val QUICK_WAIT_MIN = 1f
        private const val QUICK_WAIT_MAX = 2f
        private const val QUICK_SCROLL_MIN = 400
        private const val QUICK_SCROLL_MAX = 800
        private const val QUICK_TARGET_MIN = 3
        private const val QUICK_TARGET_MAX = 5
        private const val MAX_SCROLLS_QUICK = 10

        /** 楼层连续多少次不变判定为卡住 */
        private const val STUCK_THRESHOLD = 3
        private const val STUCK_SCROLL_PX = 1_500

        private const val FALLBACK_SCROLL_TIMES = 3

        /** 单帖最多给前几条回复点赞 */
        private const val MAX_REPLY_LIKES = 5
    }
}

/** 回复模板来源。抽成接口是为了让引擎不依赖 Room。 */
interface TemplateProvider {
    /** 按权重随机取一条启用的模板；没有可用模板返回 null */
    suspend fun pickRandom(random: Random): String?
}
