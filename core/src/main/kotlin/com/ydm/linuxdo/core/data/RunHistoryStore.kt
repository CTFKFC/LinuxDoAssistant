package com.ydm.linuxdo.core.data

import android.content.Context
import com.ydm.linuxdo.automation.model.RunStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 运行历史。
 *
 * 每跑完一次会话追加一条，供「数据」页出图与统计。
 * 只保留最近 [MAX_RECORDS] 条，避免文件无限增长。
 */
class RunHistoryStore(context: Context) {

    private val file = File(context.filesDir, "run_history.json")
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<RunRecord>>(emptyList())
    val records: StateFlow<List<RunRecord>> = _records.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _records.value = if (file.exists()) {
                runCatching { parse(file.readText()) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }

    suspend fun append(stats: RunStats, finishReason: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = RunRecord(
                startedAtMillis = stats.startedAtMillis,
                durationMillis = stats.elapsedMillis,
                topics = stats.topics,
                floors = stats.floors,
                likes = stats.likesTotal,
                replies = stats.replies,
                floorsUnreliable = stats.floorsUnreliable,
                finishReason = finishReason,
            )
            val next = (_records.value + record).takeLast(MAX_RECORDS)
            _records.value = next
            runCatching { file.writeText(serialize(next)) }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _records.value = emptyList()
            runCatching { file.delete() }
        }
    }

    /** 汇总统计，供「数据」页顶部展示 */
    fun summary(): HistorySummary {
        val list = _records.value
        return HistorySummary(
            sessions = list.size,
            totalTopics = list.sumOf { it.topics },
            totalFloors = list.sumOf { it.floors },
            totalLikes = list.sumOf { it.likes },
            totalReplies = list.sumOf { it.replies },
            totalDurationMillis = list.sumOf { it.durationMillis },
        )
    }

    private fun serialize(list: List<RunRecord>): String {
        val array = JSONArray()
        list.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("startedAt", r.startedAtMillis)
                    put("duration", r.durationMillis)
                    put("topics", r.topics)
                    put("floors", r.floors)
                    put("likes", r.likes)
                    put("replies", r.replies)
                    put("floorsUnreliable", r.floorsUnreliable)
                    put("finishReason", r.finishReason)
                },
            )
        }
        return JSONObject().apply {
            put("version", 1)
            put("records", array)
        }.toString()
    }

    private fun parse(json: String): List<RunRecord> {
        val array = JSONObject(json).optJSONArray("records") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    RunRecord(
                        startedAtMillis = o.optLong("startedAt"),
                        durationMillis = o.optLong("duration"),
                        topics = o.optInt("topics"),
                        floors = o.optInt("floors"),
                        likes = o.optInt("likes"),
                        replies = o.optInt("replies"),
                        floorsUnreliable = o.optInt("floorsUnreliable"),
                        finishReason = o.optString("finishReason"),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MAX_RECORDS = 200
    }
}

data class RunRecord(
    val startedAtMillis: Long,
    val durationMillis: Long,
    val topics: Int,
    val floors: Int,
    val likes: Int,
    val replies: Int,
    val floorsUnreliable: Int,
    val finishReason: String,
) {
    val totalRead: Int get() = topics + floors
}

data class HistorySummary(
    val sessions: Int = 0,
    val totalTopics: Int = 0,
    val totalFloors: Int = 0,
    val totalLikes: Int = 0,
    val totalReplies: Int = 0,
    val totalDurationMillis: Long = 0,
)
