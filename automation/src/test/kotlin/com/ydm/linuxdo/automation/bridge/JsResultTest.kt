package com.ydm.linuxdo.automation.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [JsResult.unquote] 的测试。
 *
 * 这段逻辑处理的是 `evaluateJavascript` 的**两层编码**：
 * JS agent 返回 JSON 字符串 → WebView 又把它编码成 JSON 字符串字面量。
 * 解错一个转义，整条自动化链路就断了，所以必须逐种转义验证。
 */
class JsResultTest {

    @Test
    fun `null 与空`() {
        assertNull(JsResult.unquote(null))
        assertNull(JsResult.unquote("null"))
    }

    @Test
    fun `普通字符串脱去外层引号`() {
        assertEquals("abc", JsResult.unquote("\"abc\""))
    }

    @Test
    fun `中文`() {
        assertEquals("感谢分享", JsResult.unquote("\"感谢分享\""))
    }

    @Test
    fun `转义的双引号`() {
        assertEquals("a\"b", JsResult.unquote("\"a\\\"b\""))
    }

    @Test
    fun `转义的反斜杠`() {
        assertEquals("a\\b", JsResult.unquote("\"a\\\\b\""))
    }

    @Test
    fun `换行回车制表`() {
        assertEquals("a\nb", JsResult.unquote("\"a\\nb\""))
        assertEquals("a\rb", JsResult.unquote("\"a\\rb\""))
        assertEquals("a\tb", JsResult.unquote("\"a\\tb\""))
    }

    @Test
    fun `unicode 转义`() {
        assertEquals("<", JsResult.unquote("\"\\u003c\""))
        assertEquals("喵", JsResult.unquote("\"\\u55b5\""))
    }

    @Test
    fun `转义的斜杠`() {
        assertEquals("a/b", JsResult.unquote("\"a\\/b\""))
    }

    @Test
    fun `完整信封 - 真实场景`() {
        // WebView 实际会给出这种形态
        val raw = "\"{\\\"ok\\\":true,\\\"data\\\":{\\\"current\\\":25,\\\"total\\\":169}}\""
        assertEquals("""{"ok":true,"data":{"current":25,"total":169}}""", JsResult.unquote(raw))
    }

    @Test
    fun `信封里含引号内容`() {
        val raw = "\"{\\\"ok\\\":true,\\\"data\\\":{\\\"actual\\\":\\\"他说\\\\\\\"你好\\\\\\\"\\\"}}\""
        assertEquals("""{"ok":true,"data":{"actual":"他说\"你好\""}}""", JsResult.unquote(raw))
    }

    @Test
    fun `非字符串字面量原样返回`() {
        assertEquals("123", JsResult.unquote("123"))
        assertEquals("true", JsResult.unquote("true"))
        assertEquals("{\"a\":1}", JsResult.unquote("{\"a\":1}"))
    }

    @Test
    fun `空字符串字面量`() {
        assertEquals("", JsResult.unquote("\"\""))
    }

    @Test
    fun `结尾孤立反斜杠不崩`() {
        // 不该抛异常
        JsResult.unquote("\"abc\\\"")
    }
}
