package com.ydm.linuxdo.core.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 全局唯一的 WebView 宿主。
 *
 * ## 为什么必须是单例、且用 Application Context
 *
 * 1. **登录态挂在它身上**。用户手动过完人机验证登录后，会话在这个 WebView 的
 *    Cookie 与内存状态里。切标签页时如果 WebView 被销毁重建，登录就白做了。
 * 2. **脚本跑在它身上**。自动化引擎正在这个 WebView 里点击、滚动、读楼层，
 *    销毁 = 脚本中断。
 * 3. **P5 要把它搬到悬浮窗**。后台时需要从 Activity 的窗口 detach、
 *    再 attach 到 WindowManager 的悬浮窗，用 Activity Context 会导致 Activity 泄漏。
 *
 * 所以：用 Application Context 创建，跨 Activity/窗口存活，由调用方负责 attach/detach。
 */
@SuppressLint("StaticFieldLeak") // 故意的：用的是 Application Context，不持有 Activity
object WebViewHost {

    private const val DEFAULT_URL = "https://linux.do"

    /** 移动端 UA。用站点的移动版布局，楼层计数走 #topic-progress 那条路径 */
    private const val MOBILE_UA_SUFFIX = " LinuxDoAssistant/1.0"

    private var webView: WebView? = null
    private var handler: DohRequestHandler? = null
    private var agentScript: String? = null

    @Volatile
    private var initialLoadStarted = false

    /** 空白占位页：让 WebView 一创建就有内容可渲染，不会出现"整片全白" */
    private const val BLANK_PAGE_HTML =
        "<html><body style=\"margin:0;background:#0E1320\"></body></html>"

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    /** 页面加载完成的回调，用于注入 JS agent 后通知引擎 */
    private val pageFinishedListeners = mutableListOf<(String) -> Unit>()

    val isInitialized: Boolean get() = webView != null

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(context: Context, dohHandler: DohRequestHandler) {
        if (webView != null) return

        val appContext = context.applicationContext
        handler = dohHandler
        agentScript = loadAgentScript(appContext)

        // minSdk 29，此 API（19+）必定可用
        WebView.setWebContentsDebuggingEnabled(true)

        val wv = WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = userAgentString + MOBILE_UA_SUFFIX
                // 人机验证页面常需要第三方资源，混合内容按站点默认走
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webViewClient = createWebViewClient()
            webChromeClient = createChromeClient()
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        webView = wv

        // ★ 这里**故意不 loadUrl**。
        //
        // 之前在 initialize() 里直接 loadUrl("https://linux.do")，结果是：
        // 网络到不了 linux.do 时（没代理/被墙），WebView 在拿到任何响应前
        // 不渲染任何内容，用户看到的就是一整片空白，一直白到 TCP 超时（几分钟）。
        // 用户实测「开代理秒出，不开代理要等到『无法访问』才有界面」正是这个。
        //
        // 改成由界面在真正需要时调 [loadInitialIfNeeded]，
        // 并且先铺一个本地占位页，保证 WebView 立刻有东西可画。
        wv.loadDataWithBaseURL(null, BLANK_PAGE_HTML, "text/html", "UTF-8", null)
    }

    /** 首次进入浏览器页时才真正加载站点，避免启动即挂起 */
    fun loadInitialIfNeeded() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        loadUrl(DEFAULT_URL)
    }

    /** 供 Compose 的 AndroidView 使用；调用方需先从旧父容器 detach */
    fun requireWebView(): WebView =
        webView ?: error("WebViewHost 尚未 initialize()")

    /**
     * 安全访问器。
     *
     * ★ UI 层一律用这个，不要用 [requireWebView]。
     *   v1.3.0 崩溃就是因为 BrowserScreen 的 AndroidView 工厂直接调了 requireWebView()，
     *   而 Compose 的 DisposableEffect 比 composition 晚执行，冷启动恢复到浏览器 Tab 时
     *   还没初始化 → IllegalStateException 直接闪退。
     *   初始化现在提前到了 Application.onCreate，但 UI 仍然不该假设它一定就绪。
     */
    fun webViewOrNull(): WebView? = webView

    fun detachFromParent() {
        webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
    }

    fun addPageFinishedListener(listener: (String) -> Unit) {
        pageFinishedListeners += listener
    }

    fun removePageFinishedListener(listener: (String) -> Unit) {
        pageFinishedListeners -= listener
    }

    // ---------------------------------------------------------------
    // 导航
    // ---------------------------------------------------------------

    fun loadUrl(url: String) {
        webView?.post { webView?.loadUrl(normalizeUrl(url)) }
    }

    fun reload() = webView?.post { webView?.reload() }

    fun goBack() {
        webView?.post { if (webView?.canGoBack() == true) webView?.goBack() }
    }

    fun goForward() {
        webView?.post { if (webView?.canGoForward() == true) webView?.goForward() }
    }

    /** 把用户输入的内容规范成 URL；不像 URL 就当搜索词 */
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.isEmpty() -> DEFAULT_URL
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
            else -> "https://linux.do/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    // ---------------------------------------------------------------
    // JS 执行
    // ---------------------------------------------------------------

    /**
     * 执行 JS 并拿到返回值。
     *
     * WebView 的 `evaluateJavascript` 回调必须在主线程注册，
     * 这里包成挂起函数，超时返回 null 而不是永久挂起。
     */
    suspend fun evaluate(script: String, timeoutMillis: Long = 15_000): String? =
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                val wv = webView
                if (wv == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                wv.post {
                    runCatching {
                        wv.evaluateJavascript(script) { value ->
                            if (cont.isActive) cont.resume(value)
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }

    /** 注入 JS agent（幂等，agent 自身会判断版本） */
    suspend fun injectAgent() {
        val script = agentScript ?: return
        evaluate(script, timeoutMillis = 10_000)
    }

    private fun loadAgentScript(context: Context): String? = runCatching {
        context.assets.open("js/linuxdo_agent.js").bufferedReader().use { it.readText() }
    }.getOrNull()

    // ---------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------

    private fun createWebViewClient() = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
        ): WebResourceResponse? {
            if (request == null) return null
            return handler?.intercept(request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            _state.value = _state.value.copy(
                url = url.orEmpty(),
                loading = true,
                progress = 0,
                // 新一次加载开始，清掉上次的错误
                error = null,
                loadStartedAtMillis = System.currentTimeMillis(),
            )
        }

        /**
         * 主文档加载失败时记下来，交给界面展示可读的错误卡片。
         *
         * 只处理主文档（`request.isForMainFrame`）——子资源（图片、字体）失败很常见，
         * 报出来只会干扰用户。
         */
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: android.webkit.WebResourceError?,
        ) {
            if (request?.isForMainFrame != true) return
            _state.value = _state.value.copy(
                loading = false,
                error = PageError(
                    code = error?.errorCode ?: 0,
                    description = error?.description?.toString().orEmpty(),
                    failingUrl = request.url?.toString().orEmpty(),
                ),
            )
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            if (request?.isForMainFrame != true) return
            val code = errorResponse?.statusCode ?: 0
            if (code < 400) return
            _state.value = _state.value.copy(
                error = PageError(
                    code = code,
                    description = "HTTP $code ${errorResponse?.reasonPhrase.orEmpty()}",
                    failingUrl = request.url?.toString().orEmpty(),
                ),
            )
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            _state.value = _state.value.copy(
                url = url.orEmpty(),
                title = view?.title.orEmpty(),
                loading = false,
                progress = 100,
                canGoBack = view?.canGoBack() ?: false,
                canGoForward = view?.canGoForward() ?: false,
            )
            CookieManager.getInstance().flush()
            val finishedUrl = url.orEmpty()
            pageFinishedListeners.toList().forEach { it(finishedUrl) }
        }
    }

    private fun createChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            _state.value = _state.value.copy(progress = newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            _state.value = _state.value.copy(title = title.orEmpty())
        }
    }
}

data class BrowserState(
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** 主文档加载失败的信息；null 表示没出错 */
    val error: PageError? = null,
    /** 本次加载开始的时间戳，用于界面判断"是不是卡太久了" */
    val loadStartedAtMillis: Long = 0L,
) {
    /** 是否还没有渲染过任何真实页面（用于决定要不要盖占位层） */
    val isBlank: Boolean get() = url.isBlank() || url == "about:blank"
}

data class PageError(
    val code: Int,
    val description: String,
    val failingUrl: String,
) {
    /**
     * 给用户看的人话。
     *
     * 重点是把「连不上」和「DNS 解析失败」区分开——前者多半要开代理，
     * 后者开 DoH 就可能解决，两种指引完全不同。
     */
    val friendlyMessage: String
        get() = when (code) {
            -2 -> "域名解析失败（DNS 可能被污染）"
            -6, -7 -> "连接不上服务器"
            -8 -> "连接超时"
            -1 -> "未知网络错误"
            else -> description.ifBlank { "加载失败（错误码 $code）" }
        }

    /** DNS 类错误开 DoH 有戏；连不上/超时多半得靠代理 */
    val suggestsDoh: Boolean get() = code == -2
}
