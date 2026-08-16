package com.ydm.linuxdo.core.data

import android.content.Context
import com.ydm.linuxdo.automation.engine.TemplateProvider
import com.ydm.linuxdo.automation.model.DefaultReplyTemplates
import com.ydm.linuxdo.automation.model.ReplyTemplateRules
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
import kotlin.random.Random

/**
 * 回复模板存储。
 *
 * ## 为什么用 JSON 文件而不是 Room
 *
 * 数据形态就是一个扁平列表（几十到几百条），没有关联、没有复杂查询、没有分页。
 * Room 会引入 KSP 注解处理器，在本机（双核 Celeron / 1.7GB）上显著拖慢每次构建，
 * 收益却接近零。用一个 JSON 文件 + 内存 StateFlow 完全够用，且导入导出天然就是这个格式。
 *
 * 首次启动会 seed 上游那 65 条模板。
 */
class TemplateStore(context: Context) : TemplateProvider {

    private val file = File(context.filesDir, "reply_templates.json")
    private val mutex = Mutex()

    private val _templates = MutableStateFlow<List<ReplyTemplate>>(emptyList())
    val templates: StateFlow<List<ReplyTemplate>> = _templates.asStateFlow()

    /** 启用中的模板（引擎实际会用的） */
    val enabledTemplates: List<ReplyTemplate>
        get() = _templates.value.filter { it.enabled }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = if (file.exists()) {
                runCatching { parse(file.readText()) }.getOrNull() ?: seed()
            } else {
                seed().also { persist(it) }
            }
            _templates.value = list
        }
    }

    // ---------------------------------------------------------------
    // 增删改
    // ---------------------------------------------------------------

    /** @return 失败原因；成功返回 null */
    suspend fun add(content: String, group: String = "自定义"): String? =
        mutate { list ->
            when (val v = ReplyTemplateRules.validate(content)) {
                is ReplyTemplateRules.Result.Invalid -> return@mutate list to v.reason
                ReplyTemplateRules.Result.Valid -> Unit
            }
            val normalized = ReplyTemplateRules.normalizeForDedup(content)
            if (list.any { ReplyTemplateRules.normalizeForDedup(it.content) == normalized }) {
                return@mutate list to "已存在相同内容的模板"
            }
            val item = ReplyTemplate(
                id = nextId(list),
                content = content.trim(),
                group = group,
                enabled = true,
                weight = 1,
            )
            (list + item) to null
        }

    suspend fun update(id: Long, content: String): String? =
        mutate { list ->
            when (val v = ReplyTemplateRules.validate(content)) {
                is ReplyTemplateRules.Result.Invalid -> return@mutate list to v.reason
                ReplyTemplateRules.Result.Valid -> Unit
            }
            val normalized = ReplyTemplateRules.normalizeForDedup(content)
            if (list.any { it.id != id && ReplyTemplateRules.normalizeForDedup(it.content) == normalized }) {
                return@mutate list to "已存在相同内容的模板"
            }
            list.map { if (it.id == id) it.copy(content = content.trim()) else it } to null
        }

    suspend fun delete(id: Long) {
        mutate { list -> list.filterNot { it.id == id } to null }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        mutate { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } to null }
    }

    suspend fun setWeight(id: Long, weight: Int) {
        mutate { list ->
            list.map { if (it.id == id) it.copy(weight = weight.coerceIn(1, 100)) else it } to null
        }
    }

    suspend fun setGroupEnabled(group: String, enabled: Boolean) {
        mutate { list ->
            list.map { if (it.group == group) it.copy(enabled = enabled) else it } to null
        }
    }

    suspend fun resetToDefaults() {
        mutate { _ -> seed() to null }
    }

    // ---------------------------------------------------------------
    // 导入导出
    // ---------------------------------------------------------------

    fun exportJson(): String = serialize(_templates.value)

    /** @return 成功导入的条数，或失败原因 */
    suspend fun importJson(json: String, replace: Boolean): ImportResult = withContext(Dispatchers.IO) {
        val incoming = runCatching { parse(json) }.getOrNull()
            ?: return@withContext ImportResult.Failure("JSON 格式不正确")
        if (incoming.isEmpty()) return@withContext ImportResult.Failure("文件里没有可用的模板")

        var added = 0
        var skipped = 0
        mutate { current ->
            val base = if (replace) emptyList() else current
            val seen = base.map { ReplyTemplateRules.normalizeForDedup(it.content) }.toMutableSet()
            val result = base.toMutableList()
            var id = nextId(base)
            incoming.forEach { item ->
                if (ReplyTemplateRules.validate(item.content) !is ReplyTemplateRules.Result.Valid) {
                    skipped++
                    return@forEach
                }
                val key = ReplyTemplateRules.normalizeForDedup(item.content)
                if (!seen.add(key)) {
                    skipped++
                    return@forEach
                }
                result += item.copy(id = id++)
                added++
            }
            result.toList() to null
        }
        ImportResult.Success(added = added, skipped = skipped)
    }

    sealed interface ImportResult {
        data class Success(val added: Int, val skipped: Int) : ImportResult
        data class Failure(val reason: String) : ImportResult
    }

    // ---------------------------------------------------------------
    // TemplateProvider
    // ---------------------------------------------------------------

    /** 按权重随机。权重越大被选中概率越高。 */
    override suspend fun pickRandom(random: Random): String? {
        val pool = enabledTemplates
        if (pool.isEmpty()) return null
        val total = pool.sumOf { it.weight }
        if (total <= 0) return pool.random(random).content
        var roll = random.nextInt(total)
        for (item in pool) {
            roll -= item.weight
            if (roll < 0) return item.content
        }
        return pool.last().content
    }

    // ---------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------

    private suspend fun mutate(
        block: (List<ReplyTemplate>) -> Pair<List<ReplyTemplate>, String?>,
    ): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (next, error) = block(_templates.value)
            if (error == null) {
                _templates.value = next
                persist(next)
            }
            error
        }
    }

    private fun nextId(list: List<ReplyTemplate>): Long =
        (list.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun seed(): List<ReplyTemplate> =
        DefaultReplyTemplates.all.mapIndexed { index, s ->
            ReplyTemplate(
                id = index + 1L,
                content = s.content,
                group = s.group.displayName,
                enabled = true,
                weight = 1,
            )
        }

    private fun persist(list: List<ReplyTemplate>) {
        runCatching { file.writeText(serialize(list)) }
    }

    private fun serialize(list: List<ReplyTemplate>): String {
        val array = JSONArray()
        list.forEach { t ->
            array.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("content", t.content)
                    put("group", t.group)
                    put("enabled", t.enabled)
                    put("weight", t.weight)
                },
            )
        }
        return JSONObject().apply {
            put("version", 1)
            put("templates", array)
        }.toString(2)
    }

    private fun parse(json: String): List<ReplyTemplate> {
        val trimmed = json.trim()
        // 兼容两种格式：{"templates":[...]} 和裸数组 [...]
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("templates") ?: JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                when (val entry = array.opt(i)) {
                    // 也接受纯字符串数组，方便用户手写导入文件
                    is String -> add(
                        ReplyTemplate(id = i + 1L, content = entry, group = "导入", enabled = true, weight = 1),
                    )
                    is JSONObject -> {
                        val content = entry.optString("content")
                        if (content.isNotBlank()) {
                            add(
                                ReplyTemplate(
                                    id = entry.optLong("id", i + 1L),
                                    content = content,
                                    group = entry.optString("group", "导入"),
                                    enabled = entry.optBoolean("enabled", true),
                                    weight = entry.optInt("weight", 1).coerceIn(1, 100),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ReplyTemplate(
    val id: Long,
    val content: String,
    val group: String,
    val enabled: Boolean,
    val weight: Int,
)
