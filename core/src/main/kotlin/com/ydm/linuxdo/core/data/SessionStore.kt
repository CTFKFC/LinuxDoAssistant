package com.ydm.linuxdo.core.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * 登录会话存储。
 *
 * ## 为什么要有它（用户反馈驱动）
 *
 * v1.1.0 的问题：明明已经登录了，仪表盘还一直显示「等待登录」，
 * 一直要等到 JS 探测 + 抓完等级信息才变。体验上就是"App 不知道我登录了"。
 *
 * 根因是登录态**只**通过在页面里执行 JS 查 `#current-user` 来判断，
 * 而那要等页面加载完（还可能卡在 Cloudflare 挑战）。
 *
 * 改进（按用户建议）：
 * 1. **先读 Cookie** —— Discourse 的登录票据是 `_t`，CookieManager 里立刻能读到，
 *    不需要等页面
 * 2. 有票据 → 立刻认为已登录，用本地存的用户名显示「欢迎 xxx」
 * 3. 再去抓等级信息（慢操作放到后面）
 * 4. 没票据 → 才提示用户登录
 * 5. 登录成功后把「用户名 + 票据指纹」存起来，下次冷启动直接命中
 *
 * ## 只存指纹，不存 Cookie 本身
 *
 * 存的是票据的 SHA-256 指纹，用来判断「还是不是同一个登录态」。
 * 真正的 Cookie 由 WebView 的 CookieManager 自己持久化，
 * 我们不复制一份到应用目录——那等于凭空多一个泄露点。
 */
class SessionStore(private val context: Context) {

    private val _session = MutableStateFlow(SavedSession())
    val session: StateFlow<SavedSession> = _session.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val prefs = context.sessionDataStore.data.first()
        _session.value = SavedSession(
            username = prefs[KEY_USERNAME].orEmpty(),
            cookieFingerprint = prefs[KEY_FINGERPRINT].orEmpty(),
        )
    }

    suspend fun save(username: String, cookieFingerprint: String) = withContext(Dispatchers.IO) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_FINGERPRINT] = cookieFingerprint
        }
        _session.value = SavedSession(username, cookieFingerprint)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        context.sessionDataStore.edit { it.clear() }
        _session.value = SavedSession()
    }

    companion object {
        private val KEY_USERNAME = stringPreferencesKey("session_username")
        private val KEY_FINGERPRINT = stringPreferencesKey("session_cookie_fp")

        const val SITE_URL = "https://linux.do"

        /** Discourse 的登录票据 Cookie 名 */
        private val AUTH_COOKIE_NAMES = listOf("_t", "_forum_session")

        /**
         * 从 CookieManager 里取登录票据。
         * 返回 null 表示当前没有登录票据。
         */
        fun readAuthCookie(url: String = SITE_URL): String? {
            val raw = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                ?: return null
            val pairs = raw.split(';').mapNotNull { part ->
                val t = part.trim()
                val i = t.indexOf('=')
                if (i <= 0) null else t.substring(0, i) to t.substring(i + 1)
            }.toMap()

            // `_t` 是 Discourse 的持久登录票据，有它基本就是登录状态
            AUTH_COOKIE_NAMES.forEach { name ->
                pairs[name]?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        /** 当前是否持有登录票据 */
        fun hasAuthCookie(url: String = SITE_URL): Boolean = readAuthCookie(url) != null

        /** 票据指纹：只存哈希，不存票据本身 */
        fun fingerprint(cookieValue: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(cookieValue.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(32)
    }
}

data class SavedSession(
    val username: String = "",
    val cookieFingerprint: String = "",
) {
    val hasUsername: Boolean get() = username.isNotBlank()

    /** 当前票据是否还是当初存下来的那一个 */
    fun matches(currentCookie: String?): Boolean {
        if (currentCookie == null || cookieFingerprint.isBlank()) return false
        return SessionStore.fingerprint(currentCookie) == cookieFingerprint
    }
}
