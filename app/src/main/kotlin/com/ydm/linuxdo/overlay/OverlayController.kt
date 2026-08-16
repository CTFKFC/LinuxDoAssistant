package com.ydm.linuxdo.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ydm.linuxdo.core.browser.WebViewHost
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮窗控制器。
 *
 * ## 三个窗口，各司其职
 *
 * | 窗口 | 尺寸 | 用途 | 可见 |
 * |------|------|------|------|
 * | 小白点 | 48dp 圆点 | 主人要的：可拖动、点击才展开 | ✅ |
 * | 控制面板 | 全屏遮罩 | 点小白点后弹出的玻璃控制台 | 点开才有 |
 * | WebView 宿主 | **1×1 px 全透明** | 让 WebView 挂在"可见"窗口上 | ❌ |
 *
 * ## 那个 1×1 窗口为什么必须存在
 *
 * Android 会**冻结后台应用的 WebView 渲染与 JS 计时器**，脚本一退到后台就停。
 * 原本的设计是把 WebView 放进悬浮窗保持可见，但主人要求悬浮窗是个小白点，
 * 塞不下 WebView。于是拆出这个 1×1 的全透明窗口专门挂 WebView：
 * 系统认为它仍然附着在可见窗口上，渲染就不会被冻结，而用户什么也看不到。
 *
 * ⚠️ 这个技巧在各家 ROM 上的表现不完全一致（小米/华为/OPPO 的后台管理更激进），
 * 真机上可能仍需要给应用加「电池优化豁免/后台运行白名单」。
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val density = context.resources.displayMetrics.density

    private var dotView: View? = null
    private var panelView: View? = null
    private var webViewHostView: ViewGroup? = null

    // ★ 每开一个窗口就 new 一个 Owner，关掉即作废。
    //   复用会触发 "SavedStateRegistry was already restored"（v1.1.0 的崩溃根因）
    private var dotOwner: OverlayLifecycleOwner? = null
    private var panelOwner: OverlayLifecycleOwner? = null

    private var expanded = false

    val isShowing: Boolean get() = dotView != null

    companion object {
        private const val DOT_SIZE_DP = 48

        /** 是否已获得悬浮窗权限 */
        fun canDrawOverlays(context: Context): Boolean =
            Settings.canDrawOverlays(context)

        // minSdk 29，TYPE_APPLICATION_OVERLAY（API 26+）必定可用
        fun overlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    // ===================================================================
    // 显示 / 隐藏
    // ===================================================================

    fun show(
        onToggleRun: () -> Unit,
        onOpenApp: () -> Unit,
    ) {
        if (!canDrawOverlays(context)) return
        if (dotView != null) return

        dotOwner = OverlayLifecycleOwner().apply { bringUp() }

        attachWebViewHost()
        attachDot(onToggleRun = onToggleRun, onOpenApp = onOpenApp)
    }

    fun hide() {
        collapse()
        dotView?.let { runCatching { windowManager.removeView(it) } }
        dotView = null
        webViewHostView?.let {
            // 摘 WebView 前先从这个容器里取下来，避免被一起销毁
            WebViewHost.detachFromParent()
            runCatching { windowManager.removeView(it) }
        }
        webViewHostView = null
        dotOwner?.onDestroy()
        dotOwner = null
    }

    // ===================================================================
    // WebView 宿主（1×1 全透明）
    // ===================================================================

    private fun attachWebViewHost() {
        if (!WebViewHost.isInitialized) return
        if (webViewHostView != null) return

        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        // ★ 必须是**全屏尺寸**，然后整个挪到屏幕外。
        //
        //   v1.3.0 用的是 1×1 像素窗口，以为"挂着就行"。但 1×1 = 1 像素视口，
        //   window.scrollBy(0, 800) 在里面根本滚不动，页面永远停在第一屏，
        //   楼层计数器不更新 → 日志刷屏"找不到楼层计数器"。
        //
        //   现在给它真实的全屏视口，再用 x = -screenW 把窗口挪到屏幕左侧之外：
        //   系统仍认为它是可见窗口（照常布局、照常跑 JS），用户什么也看不到。
        val params = WindowManager.LayoutParams(
            screenW, screenH,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                // 允许窗口超出屏幕边界，否则系统会把它拉回可视区
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -screenW
            y = 0
        }

        val container = android.widget.FrameLayout(context)
        runCatching {
            windowManager.addView(container, params)
            WebViewHost.detachFromParent()
            container.addView(
                WebViewHost.requireWebView(),
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            webViewHostView = container
        }
    }

    /**
     * 把 WebView 收回到悬浮窗宿主上。
     *
     * Activity 退到后台时调用——此时 Activity 的窗口不再可见，
     * WebView 留在那里会被系统冻结，脚本就停了。
     */
    fun reattachWebView() {
        val holder = webViewHostView ?: run {
            attachWebViewHost()
            webViewHostView
        } ?: return
        val wv = WebViewHost.webViewOrNull() ?: return
        if (wv.parent === holder) return
        runCatching {
            WebViewHost.detachFromParent()
            holder.addView(
                wv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    /** 前台恢复时把 WebView 还给 Activity */
    fun releaseWebView() {
        WebViewHost.detachFromParent()
    }

    // ===================================================================
    // 小白点
    // ===================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDot(onToggleRun: () -> Unit, onOpenApp: () -> Unit) {
        val size = (DOT_SIZE_DP * density).roundToInt()
        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = context.resources.displayMetrics.widthPixels - size - (12 * density).roundToInt()
            y = context.resources.displayMetrics.heightPixels / 3
        }

        val owner = dotOwner ?: return
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { OverlayDot() }
        }

        // 拖动 + 点击：手动区分，避免拖动被误判成点击
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val touchSlop = (8 * density)

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        snapToEdge(view, params, size)
                    } else {
                        // 没拖动 = 点击 → 展开面板
                        toggleExpand(onToggleRun = onToggleRun, onOpenApp = onOpenApp)
                    }
                    true
                }

                else -> false
            }
        }

        runCatching {
            windowManager.addView(view, params)
            dotView = view
        }
    }

    /** 松手后吸附到最近的屏幕边缘 */
    private fun snapToEdge(view: View, params: WindowManager.LayoutParams, size: Int) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val margin = (12 * density).roundToInt()
        params.x = if (params.x + size / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - size - margin
        }
        params.y = params.y.coerceIn(
            margin,
            context.resources.displayMetrics.heightPixels - size - margin,
        )
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ===================================================================
    // 展开面板
    // ===================================================================

    private fun toggleExpand(onToggleRun: () -> Unit, onOpenApp: () -> Unit) {
        if (expanded) collapse() else expand(onToggleRun = onToggleRun, onOpenApp = onOpenApp)
    }

    private fun expand(onToggleRun: () -> Unit, onOpenApp: () -> Unit) {
        if (panelView != null) return

        val owner = OverlayLifecycleOwner().apply { bringUp() }
        panelOwner = owner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            // 面板需要接收点击，但不抢输入法焦点
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                OverlayPanel(
                    onToggleRun = onToggleRun,
                    onOpenApp = {
                        collapse()
                        onOpenApp()
                    },
                    onDismiss = { collapse() },
                )
            }
        }

        runCatching {
            windowManager.addView(view, params)
            panelView = view
            expanded = true
        }
    }

    private fun collapse() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelOwner?.onDestroy()
        panelOwner = null
        expanded = false
    }
}
