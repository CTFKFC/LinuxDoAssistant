package com.ydm.linuxdo.automation.engine

import com.ydm.linuxdo.automation.model.RunStats
import com.ydm.linuxdo.automation.model.StopCondition

/**
 * 判断本次运行是否该停了。
 *
 * 抽成纯函数是为了能在 JVM 单测里穷举「4 种停止条件 × 各种统计状态」，
 * 不需要起 WebView。上游那套判定散落在 `_check_target_reached()` /
 * `browse_cat()` / `run_session()` 三处并各写了一遍，容易改漏。
 */
object TargetEvaluator {

    /**
     * @param stats 当前统计
     * @param condition 停止条件
     * @param nowMillis 当前时间戳
     * @param startedAtMillis 本次运行开始时间戳
     */
    fun isReached(
        stats: RunStats,
        condition: StopCondition,
        nowMillis: Long,
        startedAtMillis: Long,
    ): Boolean = when (condition) {
        StopCondition.Endless -> false

        // 「进入过多少个话题」——语义单一，不掺楼层数
        is StopCondition.TopicCount -> stats.topics >= condition.count

        // 「已读总数」——话题数 + 楼层数，对齐站点「已读帖子」口径
        is StopCondition.ReadCount -> stats.totalRead >= condition.count

        is StopCondition.Duration -> {
            if (startedAtMillis <= 0L) {
                false
            } else {
                val elapsedMinutes = (nowMillis - startedAtMillis).toDouble() / 60_000.0
                elapsedMinutes >= condition.minutes
            }
        }
    }

    /** 剩余量描述，用于倒计时展示。返回 null 表示无尽模式（没有"剩余"概念）。 */
    fun remaining(
        stats: RunStats,
        condition: StopCondition,
        nowMillis: Long,
        startedAtMillis: Long,
    ): Remaining? = when (condition) {
        StopCondition.Endless -> null

        is StopCondition.TopicCount ->
            Remaining.Count((condition.count - stats.topics).coerceAtLeast(0))

        is StopCondition.ReadCount ->
            Remaining.Count((condition.count - stats.totalRead).coerceAtLeast(0))

        is StopCondition.Duration -> {
            val totalMillis = condition.minutes * 60_000L
            val usedMillis = if (startedAtMillis <= 0L) 0L else nowMillis - startedAtMillis
            Remaining.Time((totalMillis - usedMillis).coerceAtLeast(0L))
        }
    }

    sealed interface Remaining {
        data class Count(val value: Int) : Remaining
        data class Time(val millis: Long) : Remaining
    }

    /**
     * 进度 0.0-1.0。无尽模式返回 null（进度条应显示为不确定态）。
     */
    fun progress(
        stats: RunStats,
        condition: StopCondition,
        nowMillis: Long,
        startedAtMillis: Long,
    ): Float? = when (condition) {
        StopCondition.Endless -> null

        is StopCondition.TopicCount ->
            ratio(stats.topics, condition.count)

        is StopCondition.ReadCount ->
            ratio(stats.totalRead, condition.count)

        is StopCondition.Duration -> {
            val total = condition.minutes * 60_000L
            val used = if (startedAtMillis <= 0L) 0L else nowMillis - startedAtMillis
            if (total <= 0L) null else (used.toFloat() / total).coerceIn(0f, 1f)
        }
    }

    private fun ratio(current: Int, target: Int): Float? =
        if (target <= 0) null else (current.toFloat() / target).coerceIn(0f, 1f)
}
