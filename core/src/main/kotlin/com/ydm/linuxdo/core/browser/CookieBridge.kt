package com.ydm.linuxdo.core.browser

import android.webkit.CookieManager
import okhttp3.Headers
import okhttp3.Request

/**
 * WebView CookieManager 与 OkHttp 之间的 Cookie 桥。
 *
 * ## 为什么必须有
 *
 * 一旦用 [DohRequestHandler] 接管 WebView 的请求，请求就不再经过 WebView 自己的
 * 网络栈，**Cookie 也就不会被自动带上**。结果是：用户明明登录了，
 * 被接管的请求却全是未登录状态，页面直接跳登录页。
 *
 * 所以必须双向同步：
 * - 发请求前：从 CookieManager 取出该 URL 的 Cookie 塞进请求头
 * - 收响应后：把 `Set-Cookie` 写回 CookieManager
 *
 * 这个类做了纯逻辑与 Android API 的分离，[mergeSetCookies] / [buildRequestCookieHeader]
 * 都是纯函数，可以在 JVM 单测里直接验证。
 */
object CookieBridge {

    /** 请求头里不该由我们转发的字段（由 OkHttp 自己管理，转发会出错） */
    private val STRIPPED_REQUEST_HEADERS = setOf(
        "accept-encoding", // 让 OkHttp 自己协商压缩，否则拿到的是压缩流
        "connection",
        "host",
        "upgrade",
        "keep-alive",
        "transfer-encoding",
        "te",
        "trailer",
        "proxy-authorization",
        "proxy-connection",
    )

    /** 响应头里不该原样交回 WebView 的字段 */
    private val STRIPPED_RESPONSE_HEADERS = setOf(
        "content-encoding", // OkHttp 已解压，再报编码会让 WebView 二次解压失败
        "content-length",   // 解压后长度已变
        "transfer-encoding",
        "connection",
        "keep-alive",
        "trailer",
    )

    /** 把 WebView 给的请求头过滤后加到 OkHttp 请求上 */
    fun applyRequestHeaders(builder: Request.Builder, headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            if (name.lowercase() !in STRIPPED_REQUEST_HEADERS) {
                builder.header(name, value)
            }
        }
    }

    /** 从 CookieManager 取该 URL 的 Cookie 串；没有则返回 null */
    fun buildRequestCookieHeader(url: String, manager: CookieManager = CookieManager.getInstance()): String? =
        runCatching { manager.getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 把响应里的 Set-Cookie 全部写回 CookieManager */
    fun persistSetCookies(url: String, headers: Headers, manager: CookieManager = CookieManager.getInstance()) {
        val cookies = headers.values("Set-Cookie")
        if (cookies.isEmpty()) return
        cookies.forEach { raw ->
            runCatching { manager.setCookie(url, raw) }
        }
        runCatching { manager.flush() }
    }

    /** 过滤响应头，返回可以安全交给 WebView 的那部分 */
    fun sanitizeResponseHeaders(headers: Headers): Map<String, String> = buildMap {
        headers.names().forEach { name ->
            if (name.lowercase() !in STRIPPED_RESPONSE_HEADERS) {
                // 多值头用逗号合并；Set-Cookie 已单独处理过，这里保留给 WebView 也无妨
                put(name, headers.values(name).joinToString(", "))
            }
        }
    }

    /**
     * 合并已有 Cookie 串与新的 Cookie 串，同名以新的为准。纯函数，便于单测。
     *
     * @return `a=1; b=2` 形式；两者都为空时返回 null
     */
    fun mergeSetCookies(existing: String?, additional: String?): String? {
        val map = LinkedHashMap<String, String>()
        fun absorb(raw: String?) {
            raw?.split(';')
                ?.mapNotNull { part ->
                    val trimmed = part.trim()
                    val idx = trimmed.indexOf('=')
                    if (idx <= 0) null else trimmed.substring(0, idx) to trimmed.substring(idx + 1)
                }
                ?.forEach { (k, v) -> map[k] = v }
        }
        absorb(existing)
        absorb(additional)
        return if (map.isEmpty()) null else map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
