package com.ydm.linuxdo.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieBridgeTest {

    @Test
    fun `合并 cookie - 两者都为空返回 null`() {
        assertNull(CookieBridge.mergeSetCookies(null, null))
        assertNull(CookieBridge.mergeSetCookies("", ""))
    }

    @Test
    fun `合并 cookie - 只有一边`() {
        assertEquals("a=1", CookieBridge.mergeSetCookies("a=1", null))
        assertEquals("b=2", CookieBridge.mergeSetCookies(null, "b=2"))
    }

    @Test
    fun `合并 cookie - 拼接不同 key`() {
        assertEquals("a=1; b=2", CookieBridge.mergeSetCookies("a=1", "b=2"))
    }

    @Test
    fun `合并 cookie - 同名以新值覆盖`() {
        assertEquals("a=2", CookieBridge.mergeSetCookies("a=1", "a=2"))
    }

    @Test
    fun `合并 cookie - 保持插入顺序`() {
        assertEquals("a=1; b=2; c=3", CookieBridge.mergeSetCookies("a=1; b=2", "c=3"))
    }

    @Test
    fun `合并 cookie - 值里含等号不被截断`() {
        // JWT / base64 的值里经常有 = 号
        assertEquals("t=abc=def==", CookieBridge.mergeSetCookies("t=abc=def==", null))
    }

    @Test
    fun `合并 cookie - 忽略无等号的碎片`() {
        assertEquals("a=1", CookieBridge.mergeSetCookies("a=1; garbage", null))
    }

    @Test
    fun `合并 cookie - 容忍多余空白`() {
        assertEquals("a=1; b=2", CookieBridge.mergeSetCookies("  a=1 ;  b=2  ", null))
    }
}

class DohProviderTest {

    @Test
    fun `五个预设都在`() {
        assertEquals(5, DohProvider.presets.size)
        listOf("cloudflare", "google", "alidns", "dnspod", "quad9").forEach { id ->
            assertNotNull("缺少预设 $id", DohProvider.byId(id))
        }
    }

    @Test
    fun `每个预设都有 bootstrap IP`() {
        // 没有 bootstrap IP 会陷入"解析 DNS 服务器也需要 DNS"的死循环
        DohProvider.presets.forEach { p ->
            assertTrue("${p.id} 缺少 bootstrap IP", p.bootstrapIps.isNotEmpty())
        }
    }

    @Test
    fun `每个预设的 URL 都是 https`() {
        DohProvider.presets.forEach { p ->
            assertTrue("${p.id} 不是 https", p.url.startsWith("https://"))
        }
    }

    @Test
    fun `未知 id 返回 null`() {
        assertNull(DohProvider.byId("nonexistent"))
    }

    @Test
    fun `自定义服务商`() {
        val custom = DohProvider.custom("https://example.com/dns-query")
        assertEquals("custom", custom.id)
        assertEquals("https://example.com/dns-query", custom.url)
    }
}
