package com.ydm.linuxdo.core.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * 用 DoH 接管 WebView 请求的处理器。
 *
 * ## 只接管 GET 的原因（这是 Android 平台限制，不是偷懒）
 *
 * `WebResourceRequest` 接口**只暴露 method / url / headers，没有请求体**。
 * 也就是说 POST 请求的 body 我们根本拿不到，转发出去必然丢数据。
 * 所以 POST 一律返回 null 交还给 WebView 自己的网络栈（走系统 DNS）。
 *
 * 同样交还的还有：非 http/https（如 blob:、data:、ws:）、WebSocket 升级请求。
 *
 * ## 线程
 *
 * `shouldInterceptRequest` 在 WebView 的后台线程上调用，可以同步做网络 IO，
 * 但必须有超时，否则会卡住整个页面加载。
 */
class DohRequestHandler(
    private val resolver: DohResolver,
    clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
) {

    private val client: OkHttpClient = clientBuilder
        .dns(DelegatingDns { resolver.dns })
        // ★ 关闭自动重定向：3xx 要原样交回 WebView，让它自己处理，
        //   否则 WebView 的历史记录、referer、同源判断都会错乱
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * @return 接管成功返回响应；任何不适合/失败的情况返回 null，
     *         由 WebView 用自己的网络栈重新发起
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!resolver.enabled) return null
        if (!shouldHandle(request)) return null

        val url = request.url.toString()

        return try {
            val builder = Request.Builder().url(url).get()
            CookieBridge.applyRequestHeaders(builder, request.requestHeaders.orEmpty())
            CookieBridge.buildRequestCookieHeader(url)?.let { builder.header("Cookie", it) }

            client.newCall(builder.build()).execute().use { response ->
                CookieBridge.persistSetCookies(url, response.headers)

                val body = response.body
                val contentType = body?.contentType()
                val mime = contentType?.let { "${it.type}/${it.subtype}" } ?: "text/plain"
                val charset = contentType?.charset()?.name()

                // 必须整体读出来：response 在 use{} 结束时会关闭，
                // 直接把 byteStream 交给 WebView 会读到已关闭的流
                val bytes = body?.bytes() ?: ByteArray(0)

                WebResourceResponse(
                    mime,
                    charset,
                    response.code,
                    // reasonPhrase 不能为空，否则构造函数抛 IllegalArgumentException
                    response.message.ifBlank { httpReason(response.code) },
                    CookieBridge.sanitizeResponseHeaders(response.headers),
                    ByteArrayInputStream(bytes),
                )
            }
        } catch (e: Exception) {
            // 接管失败一律回落，绝不因为 DoH 出问题就让用户打不开网页
            null
        }
    }

    private fun shouldHandle(request: WebResourceRequest): Boolean {
        if (!"GET".equals(request.method, ignoreCase = true)) return false

        val scheme = request.url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        // WebSocket 握手不能被普通 HTTP 请求替代
        val headers = request.requestHeaders.orEmpty()
        if (headers.entries.any { it.key.equals("Upgrade", true) }) return false

        return true
    }

    private fun httpReason(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Status $code"
    }
}

/**
 * 把 DNS 查询委托给一个可变的提供者。
 *
 * OkHttpClient 一旦构建，dns 就固定了。用户在设置页换 DoH 服务商时
 * 不想重建整个 client（会丢连接池），所以套一层间接。
 */
private class DelegatingDns(private val provider: () -> okhttp3.Dns) : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> = provider().lookup(hostname)
}
