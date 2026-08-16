package com.ydm.linuxdo.core.browser

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS 解析器。
 *
 * ## 为什么需要
 *
 * Android 的 WebView **没有任何官方 API 可以指定 DNS**。系统 DNS 被污染时，
 * linux.do 会直接打不开或被劫持。唯一的办法是接管 WebView 的请求，
 * 自己用 DoH 解析域名后再发起连接（见 [DohRequestHandler]）。
 *
 * ## 已知限制（必须让用户知道）
 *
 * `WebResourceRequest` **不提供 POST 请求体**（Android 平台限制，至今未开放），
 * 所以 POST 请求无法接管，只能回落到系统 DNS。
 * 页面加载、图片、GET 接口都能覆盖；发回复这种 POST 覆盖不到。
 * 要全覆盖只能上 VpnService，代价是 VPN 权限 + 与用户已有代理冲突。
 */
class DohResolver(
    private val bootstrapClient: OkHttpClient = defaultBootstrapClient(),
) {

    @Volatile
    private var current: Dns = Dns.SYSTEM

    @Volatile
    private var currentProvider: DohProvider? = null

    /** 当前是否启用了 DoH */
    val enabled: Boolean get() = currentProvider != null

    val provider: DohProvider? get() = currentProvider

    /** 供 OkHttp 使用的 Dns 实现；未启用 DoH 时就是系统解析 */
    val dns: Dns get() = current

    fun configure(provider: DohProvider?) {
        if (provider == null) {
            current = Dns.SYSTEM
            currentProvider = null
            return
        }
        current = buildDoh(provider)
        currentProvider = provider
    }

    private fun buildDoh(provider: DohProvider): Dns =
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.url.toHttpUrl())
            .apply {
                // bootstrap IP 用来解析 DoH 服务器自身的域名，
                // 否则会陷入"要解析 DNS 服务器地址得先解析 DNS"的死循环
                val boots = provider.bootstrapIps.mapNotNull { ip ->
                    runCatching { InetAddress.getByName(ip) }.getOrNull()
                }
                if (boots.isNotEmpty()) bootstrapDnsHosts(boots)
            }
            .includeIPv6(provider.includeIPv6)
            .post(provider.usePost)
            .build()

    /**
     * 解析测试，供设置页的「测试」按钮用。
     * 返回解析到的 IP 与耗时，失败则返回错误信息。
     */
    fun test(host: String): TestResult {
        val start = System.nanoTime()
        return try {
            val addresses = current.lookup(host)
            val millis = (System.nanoTime() - start) / 1_000_000
            if (addresses.isEmpty()) {
                TestResult.Failure(host, "解析结果为空", millis)
            } else {
                TestResult.Success(host, addresses.map { it.hostAddress ?: "?" }, millis)
            }
        } catch (e: UnknownHostException) {
            TestResult.Failure(host, e.message ?: "域名解析失败", (System.nanoTime() - start) / 1_000_000)
        } catch (e: Exception) {
            TestResult.Failure(host, e.message ?: e::class.simpleName.orEmpty(), (System.nanoTime() - start) / 1_000_000)
        }
    }

    sealed interface TestResult {
        val host: String
        val millis: Long

        data class Success(
            override val host: String,
            val addresses: List<String>,
            override val millis: Long,
        ) : TestResult

        data class Failure(
            override val host: String,
            val message: String,
            override val millis: Long,
        ) : TestResult
    }

    companion object {
        fun defaultBootstrapClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * DoH 服务商。
 *
 * bootstrapIps 是服务商自身域名的已知 IP，用来打破「解析 DNS 服务器也需要 DNS」的循环。
 */
data class DohProvider(
    val id: String,
    val displayName: String,
    val url: String,
    val bootstrapIps: List<String> = emptyList(),
    val includeIPv6: Boolean = false,
    val usePost: Boolean = false,
) {
    companion object {
        val CLOUDFLARE = DohProvider(
            id = "cloudflare",
            displayName = "Cloudflare",
            url = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
        )

        val GOOGLE = DohProvider(
            id = "google",
            displayName = "Google",
            url = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4"),
        )

        val ALIDNS = DohProvider(
            id = "alidns",
            displayName = "阿里 DNS",
            url = "https://dns.alidns.com/dns-query",
            bootstrapIps = listOf("223.5.5.5", "223.6.6.6"),
        )

        val DNSPOD = DohProvider(
            id = "dnspod",
            displayName = "DNSPod",
            url = "https://doh.pub/dns-query",
            bootstrapIps = listOf("1.12.12.12", "120.53.53.53"),
        )

        val QUAD9 = DohProvider(
            id = "quad9",
            displayName = "Quad9",
            url = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112"),
        )

        val presets: List<DohProvider> = listOf(CLOUDFLARE, GOOGLE, ALIDNS, DNSPOD, QUAD9)

        fun byId(id: String): DohProvider? = presets.firstOrNull { it.id == id }

        /** 用户自定义 DoH 地址 */
        fun custom(url: String): DohProvider = DohProvider(
            id = "custom",
            displayName = "自定义",
            url = url,
        )
    }
}
