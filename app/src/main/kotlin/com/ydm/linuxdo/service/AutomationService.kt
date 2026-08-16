package com.ydm.linuxdo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ydm.linuxdo.MainActivity
import com.ydm.linuxdo.R
import com.ydm.linuxdo.automation.model.AutomationState
import com.ydm.linuxdo.core.automation.AutomationController
import com.ydm.linuxdo.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 自动化前台服务。
 *
 * 两个职责：
 * 1. **保活**：挂前台通知，避免脚本跑到一半进程被回收
 * 2. **托管悬浮窗**：小白点 + 1×1 的 WebView 宿主窗口
 *
 * ## foregroundServiceType 的版本差异
 *
 * - API 34+ 必须声明具体类型，本场景既不是 dataSync 也不是 mediaPlayback，
 *   用 `specialUse` 并在清单里给出 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明
 * - API 29-33 普通前台服务即可
 */
class AutomationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: OverlayController? = null
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AutomationController.stop()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE -> {
                if (AutomationController.isRunning) AutomationController.stop()
                return START_STICKY
            }
        }

        startForegroundCompat(buildNotification("准备中", ""))
        showOverlayIfAllowed()
        observeState()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        watchJob?.cancel()
        overlay?.hide()
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    // ===================================================================
    // 悬浮窗
    // ===================================================================

    private fun showOverlayIfAllowed() {
        if (!OverlayController.canDrawOverlays(this)) return
        if (overlay != null) return

        overlay = OverlayController(this).apply {
            show(
                onToggleRun = {
                    if (AutomationController.isRunning) {
                        AutomationController.stop()
                    }
                },
                onOpenApp = {
                    startActivity(
                        Intent(this@AutomationService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        },
                    )
                },
            )
        }
    }

    // ===================================================================
    // 通知
    // ===================================================================

    private fun observeState() {
        watchJob?.cancel()
        watchJob = scope.launch {
            combine(
                AutomationController.state,
                AutomationController.stats,
            ) { state, stats ->
                val title = when (state) {
                    AutomationState.Idle -> "未运行"
                    is AutomationState.WaitingForLogin -> "等待登录"
                    AutomationState.Paused -> "已暂停"
                    is AutomationState.Finished -> "已完成"
                    is AutomationState.Failed -> "出错"
                    else -> "运行中"
                }
                val detail = "话题 ${stats.topics} · 爬楼 ${stats.floors} · " +
                    "已读 ${stats.totalRead} · 赞 ${stats.likesTotal}"
                title to detail
            }.collect { (title, detail) ->
                notificationManager().notify(NOTIFICATION_ID, buildNotification(title, detail))
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, detail: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_running)
            .setContentTitle("Linux.do 助手 · $title")
            .setContentText(detail)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    /** minSdk 29，通知渠道（API 26+）是必需的，不需要版本判断 */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "脚本运行状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示脚本运行进度，常驻以防止进程被回收"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        /**
         * 当前运行中的服务实例。
         *
         * 用来做**前后台 WebView 交接**：Activity 停止时把 WebView 还给悬浮窗，
         * 恢复时 Compose 的 AndroidView 会再抢回来。
         * 不做交接的话，Activity 一停，WebView 所在窗口不可见，脚本就停了。
         */
        @Volatile
        private var instance: AutomationService? = null

        /** Activity 退到后台时调用：把 WebView 挂到屏幕外的全屏悬浮窗上 */
        fun handOffWebViewToOverlay() {
            instance?.overlay?.reattachWebView()
        }

        private const val CHANNEL_ID = "automation_status"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.ydm.linuxdo.action.STOP"
        const val ACTION_TOGGLE = "com.ydm.linuxdo.action.TOGGLE"

        fun start(context: Context) {
            // minSdk 29：一律走 startForegroundService
            context.startForegroundService(Intent(context, AutomationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutomationService::class.java))
        }
    }
}
