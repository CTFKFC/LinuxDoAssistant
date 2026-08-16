package com.ydm.linuxdo.automation.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [JsArgs] 的回归测试。
 *
 * 核心用例直接对应上游 `linux_do_gui.py:953-963` 的注入缺陷：
 * 那里用 f-string 把内容拼进 JS 字符串字面量，内容含 `'` / `\` / 换行即崩。
 * 下面每个 `原地复现上游崩溃` 用例，在上游实现下都会产出非法 JS。
 */
class JsArgsTest {

    // ---------------------------------------------------------------
    // 字符串转义
    // ---------------------------------------------------------------

    @Test
    fun `普通中文原样保留`() {
        assertEquals("\"感谢分享！学习了\"", JsArgs.encodeString("感谢分享！学习了"))
    }

    @Test
    fun `原地复现上游崩溃 - 单引号`() {
        // 上游: textarea.value = '这是'引号'测试';  → JS 语法错误
        val encoded = JsArgs.encodeString("这是'引号'测试")
        // JSON 字符串里单引号无需转义，但必须仍被引号安全包裹
        assertEquals("\"这是'引号'测试\"", encoded)
        assertTrue(encoded.startsWith("\"") && encoded.endsWith("\""))
    }

    @Test
    fun `原地复现上游崩溃 - 双引号`() {
        assertEquals("\"他说\\\"你好\\\"\"", JsArgs.encodeString("他说\"你好\""))
    }

    @Test
    fun `原地复现上游崩溃 - 反斜杠`() {
        assertEquals("\"C:\\\\path\\\\to\"", JsArgs.encodeString("C:\\path\\to"))
    }

    @Test
    fun `原地复现上游崩溃 - 换行与回车`() {
        assertEquals("\"第一行\\n第二行\\r\\n第三行\"", JsArgs.encodeString("第一行\n第二行\r\n第三行"))
    }

    @Test
    fun `script 闭合标签被转义 - 防提前闭合脚本`() {
        val encoded = JsArgs.encodeString("</script><img onerror=alert(1)>")
        assertFalse("不能出现裸的 <", encoded.contains('<'))
        assertFalse("不能出现裸的 >", encoded.contains('>'))
        assertTrue(encoded.contains("\\u003c"))
        assertTrue(encoded.contains("\\u003e"))
    }

    @Test
    fun `JS 特有换行符 U+2028 U+2029 被转义`() {
        // 这两个在 JSON 里合法，但在 JS 字符串字面量里会直接断行
        assertEquals("\"a\\u2028b\"", JsArgs.encodeString("a\u2028b"))
        assertEquals("\"a\\u2029b\"", JsArgs.encodeString("a\u2029b"))
    }

    @Test
    fun `控制字符被转成 unicode 转义`() {
        assertEquals("\"a\\u0000b\"", JsArgs.encodeString("a\u0000b"))
        assertEquals("\"\\u0007\"", JsArgs.encodeString("\u0007"))
    }

    @Test
    fun `制表符退格换页`() {
        assertEquals("\"\\t\\b\\f\"", JsArgs.encodeString("\t\b\u000C"))
    }

    @Test
    fun `emoji 与代理对原样保留`() {
        assertEquals("\"喵～🐱✨\"", JsArgs.encodeString("喵～🐱✨"))
    }

    @Test
    fun `空字符串`() {
        assertEquals("\"\"", JsArgs.encodeString(""))
    }

    // ---------------------------------------------------------------
    // 值编码
    // ---------------------------------------------------------------

    @Test
    fun `各类型编码`() {
        assertEquals("null", JsArgs.encode(null))
        assertEquals("true", JsArgs.encode(true))
        assertEquals("42", JsArgs.encode(42))
        assertEquals("42", JsArgs.encode(42L))
        assertEquals("[1,2,3]", JsArgs.encode(listOf(1, 2, 3)))
    }

    @Test
    fun `NaN 与 Infinity 退化为 null 而不是产出非法 JSON`() {
        assertEquals("null", JsArgs.encode(Double.NaN))
        assertEquals("null", JsArgs.encode(Double.POSITIVE_INFINITY))
        assertEquals("null", JsArgs.encode(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `map 编码`() {
        assertEquals("""{"topicId":"123"}""", JsArgs.args("topicId" to "123"))
        assertEquals("""{"index":0}""", JsArgs.args("index" to 0))
    }

    @Test
    fun `map 的 key 也会被转义`() {
        assertEquals("""{"a\"b":1}""", JsArgs.args("a\"b" to 1))
    }

    // ---------------------------------------------------------------
    // 调用表达式
    // ---------------------------------------------------------------

    @Test
    fun `无参调用`() {
        assertEquals("window.__ld.getTopics()", JsArgs.call("getTopics"))
    }

    @Test
    fun `带参调用是双层编码`() {
        // 内层：{"topicId":"123"} ；外层再把这段 JSON 编码成 JS 字符串字面量
        assertEquals(
            """window.__ld.clickTopic("{\"topicId\":\"123\"}")""",
            JsArgs.call("clickTopic", "topicId" to "123"),
        )
    }

    @Test
    fun `带引号内容的调用表达式仍然是合法 JS`() {
        val expr = JsArgs.call("fillReply", "content" to "他说\"你好\"\n换行")
        // 表达式里除了首尾包裹的引号，不能有任何未转义的引号破坏结构
        assertTrue(expr.startsWith("window.__ld.fillReply(\""))
        assertTrue(expr.endsWith("\")"))
        assertFalse("不能有裸换行", expr.contains('\n'))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法方法名被拒绝`() {
        JsArgs.call("evil(); alert(1); //")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空方法名被拒绝`() {
        JsArgs.call("")
    }
}
