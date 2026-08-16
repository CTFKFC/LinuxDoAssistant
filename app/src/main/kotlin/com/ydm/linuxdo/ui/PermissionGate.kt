package com.ydm.linuxdo.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ydm.linuxdo.overlay.OverlayController

/**
 * 运行前的权限门禁。
 *
 * 需要两个权限，缺一会导致「跑到一半就停」这种最难排查的问题，所以在开始前就问清楚：
 *
 * | 权限 | 缺了会怎样 |
 * |------|-----------|
 * | 悬浮窗 `SYSTEM_ALERT_WINDOW` | 小白点出不来；更要命的是承载 WebView 的 1×1 窗口建不了，退到后台脚本必挂 |
 * | 通知 `POST_NOTIFICATIONS`（API 33+） | 前台服务通知不显示，进程更容易被系统回收 |
 *
 * 两个都是「引导用户去系统设置」而不是静默失败。
 */
@Composable
fun rememberPermissionGate(): PermissionGate {
    val context = LocalContext.current
    var showOverlayRationale by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 通知被拒也不阻断，只是保活能力下降 */ }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 从系统设置回来后，如果拿到了权限就继续之前的动作
        if (OverlayController.canDrawOverlays(context)) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = {
                showOverlayRationale = false
                // 用户不给权限也允许继续，只是必须留在前台
                pendingAction?.invoke()
                pendingAction = null
            },
            title = { Text("需要悬浮窗权限") },
            text = {
                Text(
                    "Android 会冻结后台应用的网页渲染与定时器，脚本一退到后台就会停。\n\n" +
                        "本应用用一个 1×1 像素的透明悬浮窗承载网页，让它在后台仍被系统视为可见，" +
                        "同时显示一个可拖动的小白点方便随时查看进度。\n\n" +
                        "不授权也能用，但**必须保持应用在前台**，息屏或切走就会中断。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    overlayLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    pendingAction?.invoke()
                    pendingAction = null
                }) { Text("暂不，保持前台运行") }
            },
        )
    }

    return remember(context) {
        PermissionGate(
            context = context,
            requestNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            requestOverlay = { action ->
                pendingAction = action
                showOverlayRationale = true
            },
        )
    }
}

class PermissionGate(
    private val context: Context,
    private val requestNotification: () -> Unit,
    private val requestOverlay: ((() -> Unit)) -> Unit,
) {
    fun hasOverlay(): Boolean = OverlayController.canDrawOverlays(context)

    /**
     * 确保权限齐备后执行 [action]。
     * 悬浮窗权限缺失时会先解释再引导；用户拒绝也会继续执行（降级为前台运行）。
     */
    fun ensureThen(action: () -> Unit) {
        requestNotification()
        if (hasOverlay()) action() else requestOverlay(action)
    }
}
