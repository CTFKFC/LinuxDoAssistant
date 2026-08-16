package com.ydm.linuxdo.automation.engine

import com.ydm.linuxdo.automation.model.FloorInfo
import com.ydm.linuxdo.automation.model.LevelInfo
import com.ydm.linuxdo.automation.model.TopicItem

/**
 * 引擎与页面之间的唯一接缝。
 *
 * 抽成接口有两个目的：
 * 1. [AutomationEngine] 可以在纯 JVM 单测里用假实现跑完整流程，不需要 WebView；
 * 2. 页面操作的实现（JS 注入细节）被隔离，L 站改版只影响实现类。
 *
 * 所有方法都可能因页面状态异常而失败，约定：
 * - 返回布尔的方法：`false` 表示"没做成"，不抛异常
 * - 返回可空对象的方法：`null` 表示"读不到"，调用方需显式处理（不许凭空造数）
 */
interface PageAgent {

    /** 导航到指定 URL 并等待加载完成 */
    suspend fun navigate(url: String)

    /** 浏览器后退（用于从话题详情回到板块列表） */
    suspend fun goBack()

    suspend fun currentUrl(): String

    /** 注入 JS agent（每次页面加载后都要调用，agent 自身幂等） */
    suspend fun injectAgent()

    // --- 登录 ---

    suspend fun isLoggedIn(): Boolean

    suspend fun loggedInUsername(): String

    /**
     * 当前是否停在 Cloudflare 人机验证页。
     *
     * ★ 检测到挑战时引擎必须**什么都不做**：不 navigate、不 reload。
     *   Cloudflare 的无感验证会自己转几秒过掉，中途一刷新就前功尽弃，
     *   用户会看到"验证永远过不去"。
     *
     * @return first=是否在挑战页, second=是否需要用户手动点（Turnstile）
     */
    suspend fun detectChallenge(): Pair<Boolean, Boolean>

    // --- 话题列表 ---

    /** 点击"回复数"表头排序；返回是否点到了 */
    suspend fun sortByReplies(): Boolean

    /** 返回 (未读, 已读) 两组话题 */
    suspend fun getTopics(): Pair<List<TopicItem>, List<TopicItem>>

    /**
     * 通过点击 `<a>` 进入话题。
     * ★ 不能改 location.href —— Discourse 只在真实点击时才计入「浏览话题」。
     */
    suspend fun clickTopic(topicId: String): Boolean

    /** 回列表后确认小蓝点已消失 */
    suspend fun checkBadgeGone(topicId: String): Boolean

    // --- 楼层 ---

    /** 读楼层计数器；读不到返回 null（调用方必须标记为 unreliable） */
    suspend fun getFloorInfo(): FloorInfo?

    suspend fun scrollBy(px: Int)

    // --- 互动 ---

    /** 返回 (按钮总数, 每个按钮是否已点赞) */
    suspend fun getLikeState(): Pair<Int, List<Boolean>>

    suspend fun like(index: Int): Boolean

    suspend fun openReply(): Boolean

    suspend fun isReplyEditorOpen(): Boolean

    suspend fun fillReply(content: String): Boolean

    suspend fun submitReply(): Boolean

    // --- 等级 ---

    suspend fun getLevelInfo(): LevelInfo?
}
