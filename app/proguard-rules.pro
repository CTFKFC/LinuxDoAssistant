# 保留 JS Agent 调用的 JavascriptInterface 方法名
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# WebView 自动化相关
# ---------------------------------------------------------------------------
# JS agent 通过 evaluateJavascript 调用，不依赖 Java 反射，无需额外 keep。
# 但如果将来加了 @JavascriptInterface 桥接方法，上面那条 keep 必须保留。

# ---------------------------------------------------------------------------
# OkHttp / DoH
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# 协程
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# 保留崩溃日志里的行号与源文件名，否则真机崩溃栈无法定位
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
