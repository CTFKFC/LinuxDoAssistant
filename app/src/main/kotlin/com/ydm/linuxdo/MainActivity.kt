package com.ydm.linuxdo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ydm.linuxdo.service.AutomationService
import com.ydm.linuxdo.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }

    /**
     * 退到后台时把 WebView 交给悬浮窗。
     *
     * Activity 不可见后，挂在它窗口上的 WebView 会被系统冻结渲染与 JS 计时器，
     * 脚本随即停摆。交给屏幕外的全屏悬浮窗后才能继续跑。
     * 回到前台时，AppRoot 里 AndroidView 的 update 回调会自动抢回来。
     */
    override fun onStop() {
        super.onStop()
        AutomationService.handOffWebViewToOverlay()
    }
}
