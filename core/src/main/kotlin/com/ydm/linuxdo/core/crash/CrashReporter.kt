package com.ydm.linuxdo.core.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃兜底。
 *
 * ## 为什么这个很重要
 *
 * 本机内存只有 1.7GB，跑不起安卓模拟器，所以**真机验证只能靠用户**。
 * 万一在用户手机上闪退，如果什么都没留下，就只能靠猜。
 *
 * 这里在进程启动最早期挂上默认异常处理器，把堆栈 + 设备信息写到应用私有目录，
 * 用户可以在「设置 → 关于 → 导出日志」里导出发回来。
 *
 * 注意：处理完必须调用原来的 handler，否则系统不会正常终止进程，
 * 会留下一个僵死的界面。
 */
object CrashReporter {

    private const val DIR_NAME = "crash"
    private const val MAX_FILES = 20

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, thread, throwable) }
            // 交回系统，保证进程正常结束（不然会卡在"无响应"）
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun crashDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun listReports(context: Context): List<File> =
        crashDir(context).listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    fun readAll(context: Context): String {
        val files = listReports(context)
        if (files.isEmpty()) return "暂无崩溃记录"
        return files.joinToString("\n\n${"=".repeat(60)}\n\n") { file ->
            runCatching { file.readText() }.getOrElse { "读取 ${file.name} 失败: ${it.message}" }
        }
    }

    fun clear(context: Context) {
        listReports(context).forEach { it.delete() }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context)

        // 只保留最近 MAX_FILES 份
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES - 1)
            ?.forEach { it.delete() }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val stack = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()

        File(dir, "crash-$stamp.txt").writeText(
            buildString {
                appendLine("时间     : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("线程     : ${thread.name}")
                appendLine("设备     : ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("系统     : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI      : ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("异常     : ${throwable::class.java.name}: ${throwable.message}")
                appendLine()
                appendLine(stack)
            },
        )
    }
}
