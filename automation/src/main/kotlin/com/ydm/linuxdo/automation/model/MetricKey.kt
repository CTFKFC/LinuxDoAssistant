package com.ydm.linuxdo.automation.model

/**
 * 信任等级升级指标的显式标识。
 *
 * ## 为什么需要这个
 *
 * 上游 `linux_do_gui.py:2330-2335` 用中文子串猜测匹配指标：
 * ```python
 * if "浏览" in name or "阅读" in name or "话题" in name:
 *     added = stats.get("topic", 0)
 * ```
 * 三个后果：
 * 1. 「阅读时间(分钟)」命中 `"阅读"`，于是把**帖子数**加到**分钟数**上；
 * 2. 「浏览话题」和「已阅读话题」同时命中，拿到**同一个值**，重复计数；
 * 3. 站点新增指标时静默算错，没有任何告警。
 *
 * 这里改成：显式别名表精确匹配 → 匹配不上就归入 [UNKNOWN]，
 * **原样透传、不参与任何本地估算**，并在 UI 上标注「站点数据」。
 */
enum class MetricKey(
    /** UI 展示名 */
    val displayName: String,
    /** 站点上可能出现的名称（精确匹配，非子串） */
    val aliases: Set<String>,
    /** 本地运行统计中哪一项会推动这个指标；null = 脚本无法影响 */
    val drivenBy: StatField? = null,
) {
    TOPICS_VIEWED(
        displayName = "浏览的话题",
        aliases = setOf("浏览的话题", "浏览话题", "话题浏览量", "Topics viewed"),
        drivenBy = StatField.TOPICS,
    ),
    TOPICS_VIEWED_ALL_TIME(
        displayName = "浏览的话题（全部）",
        aliases = setOf("浏览的话题（全部）", "浏览的话题(全部)", "Topics viewed all time"),
        drivenBy = StatField.TOPICS,
    ),
    POSTS_READ(
        displayName = "已读帖子",
        aliases = setOf("已读帖子", "阅读的帖子", "Posts read"),
        drivenBy = StatField.POSTS_READ,
    ),
    POSTS_READ_ALL_TIME(
        displayName = "已读帖子（全部）",
        aliases = setOf("已读帖子（全部）", "已读帖子(全部)", "Posts read all time"),
        drivenBy = StatField.POSTS_READ,
    ),
    LIKES_GIVEN(
        displayName = "给出的赞",
        aliases = setOf("给出的赞", "点赞数量", "送出的赞", "Likes given"),
        drivenBy = StatField.LIKES_GIVEN,
    ),
    LIKES_RECEIVED(
        displayName = "收到的赞",
        aliases = setOf("收到的赞", "获赞数量", "Likes received"),
        // 别人点赞我们，脚本无法直接影响
        drivenBy = null,
    ),
    LIKES_RECEIVED_USERS(
        displayName = "获赞用户数",
        aliases = setOf("获赞用户数", "点赞用户数", "Likes received (unique users)"),
        drivenBy = null,
    ),
    TOPICS_REPLIED_TO(
        displayName = "回复的话题",
        aliases = setOf("回复的话题", "参与回复的话题", "Topics replied to"),
        drivenBy = StatField.REPLIES,
    ),
    DAYS_VISITED(
        displayName = "访问天数",
        aliases = setOf("访问天数", "登录天数", "Days visited"),
        // 一天最多 +1，且与浏览量无关，不做本地估算
        drivenBy = null,
    ),
    READING_TIME(
        displayName = "阅读时间",
        aliases = setOf("阅读时间", "阅读时长", "Reading time"),
        // ★ 上游正是在这里把帖子数加到了分钟数上
        drivenBy = null,
    ),
    FLAGGED_POSTS(
        displayName = "被举报的帖子",
        aliases = setOf("被举报的帖子", "被举报帖子", "Flagged posts"),
        drivenBy = null,
    ),
    FLAGGED_BY_USERS(
        displayName = "举报你的用户",
        aliases = setOf("举报你的用户", "被举报用户数", "Flagged by users"),
        drivenBy = null,
    ),
    SUSPENDED(
        displayName = "禁言",
        aliases = setOf("禁言", "禁言记录", "Suspended"),
        drivenBy = null,
    ),
    SILENCED(
        displayName = "封禁",
        aliases = setOf("封禁", "封禁记录", "Silenced"),
        drivenBy = null,
    ),

    /** 站点新增或改名的指标：原样展示，绝不参与计算 */
    UNKNOWN(
        displayName = "",
        aliases = emptySet(),
        drivenBy = null,
    ),
    ;

    companion object {
        private val lookup: Map<String, MetricKey> = buildMap {
            // 必须写全限定名：buildMap 的接收者是 MutableMap，
            // 直接写 entries 会解析成 Map 自己的 entries 而不是枚举常量列表
            MetricKey.entries.filter { it != UNKNOWN }.forEach { key ->
                key.aliases.forEach { alias -> put(normalize(alias), key) }
            }
        }

        /** 归一化：去空白、全角括号转半角、大小写统一 */
        fun normalize(raw: String): String =
            raw.trim()
                .replace('（', '(')
                .replace('）', ')')
                .replace(Regex("\\s+"), "")
                .lowercase()

        /** 精确匹配（归一化后），匹配不上返回 [UNKNOWN] */
        fun fromRawName(rawName: String): MetricKey =
            lookup[normalize(rawName)] ?: UNKNOWN
    }
}

/** 一次运行会话中可统计的字段 */
enum class StatField {
    /** 进入过的话题数 */
    TOPICS,

    /** 已读帖子数（爬过的楼层） */
    POSTS_READ,

    /** 主动点赞数（主帖 + 回复） */
    LIKES_GIVEN,

    /** 发出的回复数 */
    REPLIES,
}
