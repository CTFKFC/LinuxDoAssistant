package com.ydm.linuxdo.automation.bridge

/**
 * 把参数安全地送进 `evaluateJavascript` 的最小 JSON 编码器。
 *
 * ## 为什么不用 org.json / kotlinx.serialization
 *
 * - `org.json` 在 JVM 单元测试里是**空壳实现**（所有方法抛
 *   `RuntimeException("Stub!")`），意味着最该被测试的转义逻辑根本测不了。
 * - 这里要编码的东西极简（字符串 / 数字 / 布尔 / 扁平 Map），
 *   自己写 40 行反而零依赖、可在纯 JVM 下 100% 覆盖。
 *
 * ## 这是在修什么 bug
 *
 * 上游 `linux_do_gui.py:953-963`：
 * ```python
 * s.pg.run_js(f"textarea.value = '{content}';")   # ← 直接拼进 JS 字符串字面量
 * ```
 * 注释还写着「使用安全的方式传递内容」，但只要回复内容里出现
 * `'`、`\`、换行、`</script>`，JS 就语法崩溃甚至被注入。
 * 上游那 68 条模板恰好都是无引号中文才没炸——用户一改模板就出事。
 *
 * 本实现的策略：内容永远编码成 **JSON 字符串字面量**，
 * JS 侧统一 `JSON.parse` 取出，任何字符都不可能逃逸出字符串上下文。
 */
object JsArgs {

    /** 把任意值编码为 JSON 文本 */
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> encodeString(value)
        is Boolean -> value.toString()
        is Int, is Long, is Short, is Byte -> value.toString()
        is Float -> encodeDouble(value.toDouble())
        is Double -> encodeDouble(value)
        is Map<*, *> -> encodeMap(value)
        is Iterable<*> -> value.joinToString(",", "[", "]") { encode(it) }
        else -> encodeString(value.toString())
    }

    /** 便捷构造：`args("topicId" to "123")` → `{"topicId":"123"}` */
    fun args(vararg pairs: Pair<String, Any?>): String = encodeMap(pairs.toMap())

    /**
     * 生成一次完整的调用表达式，例如
     * `window.__ld.fillReply("{\"content\":\"他说\\\"你好\\\"\"}")`
     *
     * 注意这里是**两层编码**：内层把参数编码成 JSON 文本，
     * 外层再把这段 JSON 文本本身编码成一个 JS 字符串字面量。
     * JS 侧收到的是字符串，`JSON.parse` 之后才是对象。
     */
    fun call(method: String, vararg pairs: Pair<String, Any?>): String {
        require(method.matches(SAFE_METHOD)) { "非法方法名: $method" }
        return if (pairs.isEmpty()) {
            "window.__ld.$method()"
        } else {
            "window.__ld.$method(${encodeString(args(*pairs))})"
        }
    }

    private val SAFE_METHOD = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")

    private fun encodeMap(map: Map<*, *>): String =
        map.entries.joinToString(",", "{", "}") { (k, v) ->
            "${encodeString(k.toString())}:${encode(v)}"
        }

    private fun encodeDouble(d: Double): String =
        // JSON 不支持 NaN / Infinity，退化成 null 而不是产出非法 JSON
        if (d.isNaN() || d.isInfinite()) "null" else d.toString()

    /**
     * 把字符串编码为 JSON 字符串字面量（含首尾引号）。
     *
     * 额外处理的三类字符（标准 JSON 不要求，但注入到 HTML/JS 上下文时必须转）：
     * - `<` `>` → `<` `>`：防止 `</script>` 提前闭合脚本标签
     * - `&`     → `&`：防 HTML 实体解析
     * - U+2028 / U+2029：JS 里是合法换行符，会直接截断字符串字面量
     */
    fun encodeString(s: String): String {
        val sb = StringBuilder(s.length + 16)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                '<' -> sb.append("\\u003c")
                '>' -> sb.append("\\u003e")
                '&' -> sb.append("\\u0026")
                ' ' -> sb.append("\\u2028")
                ' ' -> sb.append("\\u2029")
                else ->
                    if (ch < ' ') {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
