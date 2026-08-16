package com.ydm.linuxdo.core.browser

import com.ydm.linuxdo.automation.bridge.JsArgs
import com.ydm.linuxdo.automation.bridge.JsResult
import com.ydm.linuxdo.automation.engine.PageAgent
import com.ydm.linuxdo.automation.model.FloorInfo
import com.ydm.linuxdo.automation.model.LevelInfo
import com.ydm.linuxdo.automation.model.LevelRequirement
import com.ydm.linuxdo.automation.model.MetricKey
import com.ydm.linuxdo.automation.model.TopicItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 用 [WebViewHost] 实现的 [PageAgent]。
 *
 * 这是「引擎」与「真实浏览器」之间唯一的粘合层：
 * 引擎完全不知道 WebView 的存在，只调 PageAgent 的方法；
 * 这里负责把调用翻译成 JS、执行、解包信封、转成领域模型。
 *
 * ## 参数传递纪律
 *
 * 所有带参数的调用**一律走 [JsArgs.call]**，绝不手工拼字符串。
 * 上游 `linux_do_gui.py:953` 正是因为拼字符串，回复内容里出现引号就崩。
 */
class WebViewPageAgent(
    private val host: WebViewHost = WebViewHost,
) : PageAgent {

    // ---------------------------------------------------------------
    // 导航
    // ---------------------------------------------------------------

    override suspend fun navigate(url: String) {
        host.loadUrl(url)
        awaitPageIdle()
    }

    override suspend fun goBack() {
        host.goBack()
        awaitPageIdle()
    }

    override suspend fun currentUrl(): String = host.state.value.url

    override suspend fun injectAgent() {
        host.injectAgent()
    }

    /** 等页面加载完成；超时也返回，让上层自己靠选择器判断 */
    private suspend fun awaitPageIdle(timeoutMillis: Long = 20_000) {
        withTimeoutOrNull(timeoutMillis) {
            // 先等它真的开始加载，避免读到上一页的 loading=false
            delay(300)
            while (host.state.value.loading) {
                delay(200)
            }
        }
    }

    // ---------------------------------------------------------------
    // 登录
    // ---------------------------------------------------------------

    override suspend fun isLoggedIn(): Boolean =
        call("isLoggedIn")?.optBoolean("loggedIn", false) ?: false

    override suspend fun loggedInUsername(): String =
        call("isLoggedIn")?.optString("username").orEmpty()

    override suspend fun detectChallenge(): Pair<Boolean, Boolean> {
        val data = call("detectChallenge") ?: return false to false
        return data.optBoolean("challenge", false) to data.optBoolean("interactive", false)
    }

    // ---------------------------------------------------------------
    // 话题列表
    // ---------------------------------------------------------------

    override suspend fun sortByReplies(): Boolean =
        call("sortByReplies")?.optBoolean("clicked", false) ?: false

    override suspend fun getTopics(): Pair<List<TopicItem>, List<TopicItem>> {
        val data = call("getTopics") ?: return emptyList<TopicItem>() to emptyList()
        return parseTopics(data.optJSONArray("unread")) to parseTopics(data.optJSONArray("read"))
    }

    private fun parseTopics(array: org.json.JSONArray?): List<TopicItem> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank() || id == "null") continue
                add(
                    TopicItem(
                        id = id,
                        url = o.optString("url"),
                        title = o.optString("title"),
                        unread = o.optBoolean("unread", false),
                    ),
                )
            }
        }
    }

    override suspend fun clickTopic(topicId: String): Boolean =
        call("clickTopic", "topicId" to topicId)?.optBoolean("clicked", false) ?: false

    override suspend fun checkBadgeGone(topicId: String): Boolean =
        call("checkBadgeGone", "topicId" to topicId)?.optBoolean("gone", false) ?: false

    // ---------------------------------------------------------------
    // 楼层
    // ---------------------------------------------------------------

    override suspend fun getFloorInfo(): FloorInfo? {
        val data = call("getFloorInfo") ?: return null
        val current = data.optInt("current", -1)
        val total = data.optInt("total", -1)
        if (current < 0 || total <= 0) return null
        return FloorInfo(
            current = current,
            total = total,
            source = data.optString("source", "unknown"),
        )
    }

    override suspend fun scrollBy(px: Int) {
        call("scrollBy", "px" to px)
    }

    // ---------------------------------------------------------------
    // 互动
    // ---------------------------------------------------------------

    override suspend fun getLikeState(): Pair<Int, List<Boolean>> {
        val data = call("getLikeState") ?: return 0 to emptyList()
        val count = data.optInt("count", 0)
        val arr = data.optJSONArray("liked")
        val liked = buildList {
            if (arr != null) for (i in 0 until arr.length()) add(arr.optBoolean(i, false))
        }
        return count to liked
    }

    override suspend fun like(index: Int): Boolean =
        call("like", "index" to index)?.optBoolean("liked", false) ?: false

    override suspend fun openReply(): Boolean =
        call("openReply")?.optBoolean("opened", false) ?: false

    override suspend fun isReplyEditorOpen(): Boolean =
        call("isReplyEditorOpen")?.optBoolean("open", false) ?: false

    override suspend fun fillReply(content: String): Boolean {
        // ★ content 经 JsArgs 做 JSON 编码，任何字符都逃不出字符串上下文
        val data = call("fillReply", "content" to content) ?: return false
        if (!data.optBoolean("filled", false)) return false
        // 回读校验：确认编辑器里的内容真的等于我们要发的内容
        return data.optString("actual") == content
    }

    override suspend fun submitReply(): Boolean =
        call("submitReply")?.optBoolean("submitted", false) ?: false

    // ---------------------------------------------------------------
    // 等级
    // ---------------------------------------------------------------

    override suspend fun getLevelInfo(): LevelInfo? {
        val data = call("getLevelInfo") ?: return null
        val reqArray = data.optJSONArray("requirements")
        val requirements = buildList {
            if (reqArray != null) {
                for (i in 0 until reqArray.length()) {
                    val o = reqArray.optJSONObject(i) ?: continue
                    val rawName = o.optString("rawName")
                    if (rawName.isBlank()) continue
                    add(
                        LevelRequirement(
                            rawName = rawName,
                            // 显式映射表精确匹配，匹配不上归 UNKNOWN 且不参与任何计算
                            key = MetricKey.fromRawName(rawName),
                            current = o.optString("current"),
                            required = o.optString("required"),
                            kind = o.optString("kind"),
                        ),
                    )
                }
            }
        }
        return LevelInfo(
            username = data.optString("username"),
            level = data.optString("level"),
            nextLevel = data.optString("nextLevel"),
            requirements = requirements,
            capturedAtMillis = System.currentTimeMillis(),
        )
    }

    // ---------------------------------------------------------------
    // 底层调用
    // ---------------------------------------------------------------

    /**
     * 调用 agent 方法并解包信封。
     *
     * 信封形如 `{"ok":true,"data":{...}}` 或 `{"ok":false,"error":"..."}`。
     * 失败时返回 null，由调用方决定怎么降级——绝不把异常当成"合法的空结果"。
     */
    private suspend fun call(method: String, vararg args: Pair<String, Any?>): JSONObject? {
        val expr = JsArgs.call(method, *args)
        val raw = host.evaluate(expr) ?: return null

        // 第一层：evaluateJavascript 把 JS 的返回值又 JSON 编码了一遍
        val json = JsResult.unquote(raw) ?: return null

        return runCatching {
            val envelope = JSONObject(json)
            if (!envelope.optBoolean("ok", false)) return null
            // data 可能是对象，也可能是 null（例如 getFloorInfo 读不到时）
            envelope.optJSONObject("data")
        }.getOrNull()
    }
}
