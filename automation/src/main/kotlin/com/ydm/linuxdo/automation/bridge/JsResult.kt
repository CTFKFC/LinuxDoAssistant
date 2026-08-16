package com.ydm.linuxdo.automation.bridge

/**
 * 解析 `evaluateJavascript` 的返回值。
 *
 * ## 为什么需要"两层解包"
 *
 * 1. JS agent 里每个方法都被 `envelope()` 包过，返回的是
 *    **一个 JSON 字符串**，例如 `{"ok":true,"data":{...}}`
 * 2. `evaluateJavascript` 会把 JS 的返回值再做一次 JSON 编码交给 Kotlin，
 *    所以 Kotlin 拿到的是**一个 JSON 字符串字面量**：
 *    `"{\"ok\":true,\"data\":{...}}"`
 *
 * 于是必须先把外层的字符串字面量解出来，才能拿到真正的 JSON 文本。
 *
 * [unquote] 是纯函数，可以在 JVM 单测里穷举各种转义组合。
 */
object JsResult {

    /**
     * 把 JSON 字符串字面量还原成原始文本。
     *
     * - 输入 `"abc"`（含引号）→ 输出 `abc`
     * - 输入 `"a\"b"` → 输出 `a"b`
     * - 输入 `null` / 空 → 输出 null
     * - 输入不是字符串字面量（例如 `123`、`{...}`）→ 原样返回
     */
    fun unquote(raw: String?): String? {
        if (raw == null) return null
        if (raw == "null") return null
        if (raw.length < 2) return raw
        if (!raw.startsWith('"') || !raw.endsWith('"')) return raw

        val body = raw.substring(1, raw.length - 1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\') {
                sb.append(c)
                i++
                continue
            }
            if (i + 1 >= body.length) {
                sb.append(c)
                break
            }
            when (val next = body[i + 1]) {
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'b' -> { sb.append('\b'); i += 2 }
                'f' -> { sb.append(''); i += 2 }
                'u' -> {
                    if (i + 5 < body.length) {
                        val hex = body.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append(c); i++
                        }
                    } else {
                        sb.append(c); i++
                    }
                }
                else -> { sb.append(next); i += 2 }
            }
        }
        return sb.toString()
    }
}
