package com.ydm.linuxdo.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ydm.linuxdo.automation.model.BrowseMode
import com.ydm.linuxdo.automation.model.DefaultCategories
import com.ydm.linuxdo.automation.model.RunConfig
import com.ydm.linuxdo.automation.model.StopCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置存储。
 *
 * 用 DataStore Preferences 而不是 SharedPreferences：前者是协程/Flow 原生的，
 * 读写不会阻塞主线程，也不会有 `apply()` 静默丢数据的问题。
 *
 * ## 安全默认值
 *
 * `enableLike` / `enableReply` **默认 false**，沿用上游 v8.4 之后的负责任做法：
 * L 站可能检测自动回复，曾有用户因此被举报。开启需要用户主动确认。
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val browseMode = stringPreferencesKey("browse_mode")
        val stopType = stringPreferencesKey("stop_type")
        val stopValue = intPreferencesKey("stop_value")

        val enableLike = booleanPreferencesKey("enable_like")
        val enableReply = booleanPreferencesKey("enable_reply")
        val enableExtraDelay = booleanPreferencesKey("enable_extra_delay")
        val replyRiskAcknowledged = booleanPreferencesKey("reply_risk_acknowledged")

        val likeRate = floatPreferencesKey("like_rate")
        val likeReplyRate = floatPreferencesKey("like_reply_rate")
        val replyRate = floatPreferencesKey("reply_rate")
        val waitMin = floatPreferencesKey("wait_min")
        val waitMax = floatPreferencesKey("wait_max")

        val enabledCategories = stringSetPreferencesKey("enabled_categories")

        val dohEnabled = booleanPreferencesKey("doh_enabled")
        val dohProviderId = stringPreferencesKey("doh_provider_id")
        val dohCustomUrl = stringPreferencesKey("doh_custom_url")

        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val animatedBackground = booleanPreferencesKey("animated_background")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            browseMode = runCatching {
                BrowseMode.valueOf(p[Keys.browseMode] ?: BrowseMode.DEEP.name)
            }.getOrDefault(BrowseMode.DEEP),
            stopType = runCatching {
                StopType.valueOf(p[Keys.stopType] ?: StopType.TOPIC_COUNT.name)
            }.getOrDefault(StopType.TOPIC_COUNT),
            stopValue = p[Keys.stopValue] ?: 30,
            enableLike = p[Keys.enableLike] ?: false,
            enableReply = p[Keys.enableReply] ?: false,
            enableExtraDelay = p[Keys.enableExtraDelay] ?: true,
            replyRiskAcknowledged = p[Keys.replyRiskAcknowledged] ?: false,
            likeRate = p[Keys.likeRate] ?: 0.30f,
            likeReplyRate = p[Keys.likeReplyRate] ?: 0.15f,
            replyRate = p[Keys.replyRate] ?: 0.05f,
            waitMin = p[Keys.waitMin] ?: 1f,
            waitMax = p[Keys.waitMax] ?: 3f,
            enabledCategorySlugs = p[Keys.enabledCategories]
                ?: DefaultCategories.defaultEnabled.map { it.slug }.toSet(),
            dohEnabled = p[Keys.dohEnabled] ?: false,
            dohProviderId = p[Keys.dohProviderId] ?: "cloudflare",
            dohCustomUrl = p[Keys.dohCustomUrl].orEmpty(),
            overlayEnabled = p[Keys.overlayEnabled] ?: true,
            dynamicColor = p[Keys.dynamicColor] ?: false,
            animatedBackground = p[Keys.animatedBackground] ?: true,
        )
    }

    suspend fun update(block: (MutableEditor) -> Unit) {
        context.dataStore.edit { prefs ->
            block(MutableEditor(prefs))
        }
    }

    /** 对外只暴露语义化的写入方法，避免调用方直接碰 Key */
    class MutableEditor(private val prefs: androidx.datastore.preferences.core.MutablePreferences) {
        fun browseMode(value: BrowseMode) { prefs[Keys.browseMode] = value.name }
        fun stopType(value: StopType) { prefs[Keys.stopType] = value.name }
        fun stopValue(value: Int) { prefs[Keys.stopValue] = value.coerceAtLeast(1) }
        fun enableLike(value: Boolean) { prefs[Keys.enableLike] = value }
        fun enableReply(value: Boolean) { prefs[Keys.enableReply] = value }
        fun enableExtraDelay(value: Boolean) { prefs[Keys.enableExtraDelay] = value }
        fun replyRiskAcknowledged(value: Boolean) { prefs[Keys.replyRiskAcknowledged] = value }
        fun likeRate(value: Float) { prefs[Keys.likeRate] = value.coerceIn(0f, 1f) }
        fun likeReplyRate(value: Float) { prefs[Keys.likeReplyRate] = value.coerceIn(0f, 1f) }
        fun replyRate(value: Float) { prefs[Keys.replyRate] = value.coerceIn(0f, 1f) }
        fun waitRange(min: Float, max: Float) {
            val lo = min.coerceAtLeast(0f)
            prefs[Keys.waitMin] = lo
            prefs[Keys.waitMax] = max.coerceAtLeast(lo)
        }
        fun enabledCategories(slugs: Set<String>) { prefs[Keys.enabledCategories] = slugs }
        fun dohEnabled(value: Boolean) { prefs[Keys.dohEnabled] = value }
        fun dohProviderId(value: String) { prefs[Keys.dohProviderId] = value }
        fun dohCustomUrl(value: String) { prefs[Keys.dohCustomUrl] = value }
        fun overlayEnabled(value: Boolean) { prefs[Keys.overlayEnabled] = value }
        fun dynamicColor(value: Boolean) { prefs[Keys.dynamicColor] = value }
        fun animatedBackground(value: Boolean) { prefs[Keys.animatedBackground] = value }
    }
}

enum class StopType(val label: String, val hint: String) {
    ENDLESS("无尽模式", "持续运行直到手动停止"),

    // ★ 这两项是拆开的，直接对应上游那个语义混淆的 bug：
    //   上游只有一个「帖子数量」，深度爬楼时实际按 话题+楼层 判定，
    //   用户填 50 以为是 50 个帖子，其实爬完一个 50 层的帖子就停了。
    TOPIC_COUNT("目标话题数", "进入过多少个话题后停止"),
    READ_COUNT("目标已读数", "话题数 + 爬过的楼层数达标后停止"),

    DURATION("时间限制", "运行满多少分钟后停止"),
}

data class AppSettings(
    val browseMode: BrowseMode = BrowseMode.DEEP,
    val stopType: StopType = StopType.TOPIC_COUNT,
    val stopValue: Int = 30,
    val enableLike: Boolean = false,
    val enableReply: Boolean = false,
    val enableExtraDelay: Boolean = true,
    val replyRiskAcknowledged: Boolean = false,
    val likeRate: Float = 0.30f,
    val likeReplyRate: Float = 0.15f,
    val replyRate: Float = 0.05f,
    val waitMin: Float = 1f,
    val waitMax: Float = 3f,
    val enabledCategorySlugs: Set<String> = emptySet(),
    val dohEnabled: Boolean = false,
    val dohProviderId: String = "cloudflare",
    val dohCustomUrl: String = "",
    val overlayEnabled: Boolean = true,
    val dynamicColor: Boolean = false,
    val animatedBackground: Boolean = true,
) {
    /** 转成引擎要的运行配置 */
    fun toRunConfig(): RunConfig = RunConfig(
        browseMode = browseMode,
        stopCondition = when (stopType) {
            StopType.ENDLESS -> StopCondition.Endless
            StopType.TOPIC_COUNT -> StopCondition.TopicCount(stopValue)
            StopType.READ_COUNT -> StopCondition.ReadCount(stopValue)
            StopType.DURATION -> StopCondition.Duration(stopValue)
        },
        enableLike = enableLike,
        enableReply = enableReply,
        enableExtraDelay = enableExtraDelay,
        likeRate = likeRate,
        likeReplyRate = likeReplyRate,
        replyRate = replyRate,
        waitRangeSeconds = waitMin..waitMax,
        enabledCategorySlugs = enabledCategorySlugs.toList(),
    )
}
