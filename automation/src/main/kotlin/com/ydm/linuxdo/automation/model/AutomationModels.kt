package com.ydm.linuxdo.automation.model

/** 浏览深度策略 */
enum class BrowseMode {
    /** 深度爬楼：读完帖子所有楼层，主要拉高「已读帖子」 */
    DEEP,

    /** 快速浏览：只爬 3-5 层就换帖，主要拉高「浏览话题」 */
    QUICK,
}

/**
 * 停止条件。
 *
 * ## 为什么拆成四种
 *
 * 上游只有三种（endless / topics / time），但 `topics` 在深度爬楼模式下
 * 实际判定的是 `topic + floors >= target`（`linux_do_gui.py:1170`），
 * 而 GUI 上写的却是「帖子数量: [50] 个」。用户填 50，以为是 50 个帖子，
 * 实际上爬完一个 50 层的帖子就停了。
 *
 * 这里把两种语义拆成两个独立选项，UI 文案也分别写清楚。
 */
sealed interface StopCondition {
    /** 无尽模式，只能手动停止 */
    data object Endless : StopCondition

    /** 进入过 [count] 个话题后停止 */
    data class TopicCount(val count: Int) : StopCondition

    /** 已读总数（话题数 + 爬过的楼层数）达到 [count] 后停止 */
    data class ReadCount(val count: Int) : StopCondition

    /** 运行满 [minutes] 分钟后停止 */
    data class Duration(val minutes: Int) : StopCondition
}

/** 单次运行的完整配置 */
data class RunConfig(
    val browseMode: BrowseMode = BrowseMode.DEEP,
    val stopCondition: StopCondition = StopCondition.Endless,

    /** 自动点赞总开关，默认关（沿用上游的安全默认值） */
    val enableLike: Boolean = false,

    /** 自动回复总开关，默认关；L 站可能检测自动回复 */
    val enableReply: Boolean = false,

    /** 额外等待延迟开关（爬楼本身已自带 2-4s 间隔） */
    val enableExtraDelay: Boolean = true,

    /** 对主帖点赞的概率 0.0-1.0 */
    val likeRate: Float = 0.30f,

    /** 对回复点赞的概率 0.0-1.0 */
    val likeReplyRate: Float = 0.15f,

    /** 发回复的概率 0.0-1.0 */
    val replyRate: Float = 0.05f,

    /** 额外等待区间（秒） */
    val waitRangeSeconds: ClosedFloatingPointRange<Float> = 1f..3f,

    /** 每个板块随机挑几个话题 */
    val topicsPerCategory: IntRange = 3..8,

    /** 参与轮换的板块 slug 列表 */
    val enabledCategorySlugs: List<String> = emptyList(),
) {
    init {
        require(likeRate in 0f..1f) { "likeRate 必须在 0..1，实际 $likeRate" }
        require(likeReplyRate in 0f..1f) { "likeReplyRate 必须在 0..1，实际 $likeReplyRate" }
        require(replyRate in 0f..1f) { "replyRate 必须在 0..1，实际 $replyRate" }
        require(waitRangeSeconds.start >= 0f) { "等待时间不能为负" }
        require(!topicsPerCategory.isEmpty()) { "topicsPerCategory 不能为空区间" }
    }
}

/**
 * 运行时统计。
 *
 * [floorsUnreliable] 用来诚实标记「有帖子拿不到楼层计数器」的情况——
 * 上游遇到这种情况直接 `stats["floors"] += 3` 凭空造数（`linux_do_gui.py:813`），
 * 这里改成不计数 + 打标，UI 上显示 ⚠ 提示统计可能偏低。
 */
data class RunStats(
    val topics: Int = 0,
    val floors: Int = 0,
    val likesMain: Int = 0,
    val likesReply: Int = 0,
    val replies: Int = 0,
    /** 有多少个话题没能读到楼层计数器 */
    val floorsUnreliable: Int = 0,
    val startedAtMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
) {
    /** 已读总数 = 话题数 + 爬过的楼层数（对齐站点「已读帖子」口径） */
    val totalRead: Int get() = topics + floors

    val likesTotal: Int get() = likesMain + likesReply

    /** 楼层统计是否可能偏低 */
    val hasUnreliableFloors: Boolean get() = floorsUnreliable > 0

    fun valueOf(field: StatField): Int = when (field) {
        StatField.TOPICS -> topics
        StatField.POSTS_READ -> floors
        StatField.LIKES_GIVEN -> likesTotal
        StatField.REPLIES -> replies
    }
}

/** 一条升级指标（站点原始数据 + 解析结果） */
data class LevelRequirement(
    val rawName: String,
    val key: MetricKey,
    val current: String,
    val required: String,
    val kind: String,
) {
    val currentInt: Int? get() = current.filter { it.isDigit() }.toIntOrNull()
    val requiredInt: Int? get() = required.filter { it.isDigit() }.toIntOrNull()

    /** 站点原始名优先展示，保证站点改名时用户仍看得懂 */
    val displayName: String
        get() = rawName.ifBlank { key.displayName }

    val progress: Float?
        get() {
            val c = currentInt ?: return null
            val r = requiredInt ?: return null
            if (r <= 0) return null
            return (c.toFloat() / r).coerceIn(0f, 1f)
        }
}

/** 用户等级快照 */
data class LevelInfo(
    val username: String = "",
    val level: String = "",
    val nextLevel: String = "",
    val requirements: List<LevelRequirement> = emptyList(),
    val capturedAtMillis: Long = 0L,
) {
    /** 总体进度 = 各可量化指标进度的平均值 */
    val overallProgress: Float?
        get() {
            val values = requirements.mapNotNull { it.progress }
            return if (values.isEmpty()) null else values.average().toFloat()
        }
}

/** 话题列表项 */
data class TopicItem(
    val id: String,
    val url: String,
    val title: String,
    val unread: Boolean,
)

/** 楼层进度；[source] 记录取自哪种 DOM 布局，便于排查 */
data class FloorInfo(
    val current: Int,
    val total: Int,
    val source: String,
)

/** 板块 */
data class Category(
    val slug: String,
    val name: String,
    val path: String,
    val enabledByDefault: Boolean,
)

/** 引擎状态机。UI / 前台服务 / 悬浮窗都只观察这个。 */
sealed interface AutomationState {
    data object Idle : AutomationState

    /** 等用户在浏览器里手动完成登录（含人机验证） */
    data class WaitingForLogin(val elapsedSeconds: Long) : AutomationState

    data object FetchingLevelInfo : AutomationState

    data class EnteringCategory(val category: Category) : AutomationState

    data class ListingTopics(val category: Category) : AutomationState

    data class BrowsingTopic(
        val category: Category,
        val topic: TopicItem,
        val floor: FloorInfo?,
    ) : AutomationState

    data class Liking(val topic: TopicItem, val index: Int) : AutomationState

    data class Replying(val topic: TopicItem, val content: String) : AutomationState

    data object Paused : AutomationState

    data class Finished(val reason: FinishReason, val stats: RunStats) : AutomationState

    data class Failed(val message: String, val cause: String?) : AutomationState
}

enum class FinishReason {
    TARGET_REACHED,
    USER_STOPPED,
    LOGIN_TIMEOUT,
    NO_CATEGORIES,
    ERROR,
}

/** 运行日志级别 */
enum class LogLevel { DEBUG, INFO, SUCCESS, WARN, ERROR }

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val message: String,
)
