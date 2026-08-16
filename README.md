<div align="center">

# Linux.do 助手 · Android

**Linux.do 论坛自动化助手的 Android 原生实现**

由 PC 端 Python 脚本 [`icysaintdx/linuxdosss`](https://github.com/icysaintdx/linuxdosss)（MIT）移植而来

[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%20~%2017-3DDC84.svg)](#支持范围)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.06-4285F4.svg)](https://developer.android.com/jetpack/compose)

</div>

---

## ⚠️ 使用前必读

本工具通过自动化产生浏览、点赞、回复行为，**这类行为违反绝大多数论坛的服务条款**。
上游作者在其 README 中记录过「曾有用户因自动回复被举报」。

- 自动点赞与自动回复**默认关闭**，开启时会弹出风险确认
- 本项目**没���**增强超出上游水平的反检测能力，只做功能移植
- 账号被封禁等一切后果由使用者自行承担
- **仅供学习交流，请勿用于违反社区规则的行为**

---

## 这是什么

一个把「论坛自动化脚本」和「普通浏览器」揉在一起的 Android 应用：

- **浏览器** —— 完整的 WebView 浏览器，用来手动登录、过人机验证
- **脚本** —— 自动浏览话题、深度爬楼、点赞、回帖，实时追踪信任等级进度
- **两者共用同一个 WebView**，所以登录一次即可，脚本直接接管你的会话

### 为什么用 WebView 而不是无障碍服务

上游 PC 脚本的逻辑 100% 基于 DOM 与 CSS 选择器，用 WebView 可以近乎逐行直译；
同时白送一个「正常浏览器」，还避开了 `BIND_ACCESSIBILITY_SERVICE` 这个最敏感的权限。

---

## 功能

### 自动化脚本

| 能力 | 说明 |
|------|------|
| 两种浏览模式 | **深度爬楼**（读完所有楼层，拉高「已读帖子」）/ **快速浏览**（3-5 层换帖，拉高「浏览话题」） |
| 四种停止条件 | 无尽 / 目标话题数 / 目标已读数 / 时间限制 |
| 自动点赞 | 主帖与回复分别设概率，默认关闭 |
| 自动回帖 | 模板完全可自定义，默认关闭 |
| 板块轮换 | 内置 16 个板块，可自由勾选，随机轮换 |
| 等级追踪 | 实时抓取 connect.linux.do 的信任等级与升级指标，运行前后对比**真实增量** |
| 实时日志 | 终端风格控制台，`爬楼 #5 → 25/169` 一眼看清进度与卡点 |

### 回复模板

完整增删改查、单条启停、按分类批量启停、权重随机、JSON 导入导出（走系统文件选择器，不需要存储权限）、一键恢复默认 65 条。

### DNS over HTTPS

内置 Cloudflare / Google / 阿里 / DNSPod / Quad9，也可填自定义地址，带解析测试。

> **已知限制**：Android 的 `WebResourceRequest` **不提供 POST 请求体**（平台限制），
> 因此只能接管 GET 请求。页面加载、图片、GET 接口都覆盖得到，
> 但 POST（例如发回复）会回落到系统 DNS。全覆盖需要 `VpnService`，本项目未采用。

### 悬浮窗

48dp 小白点，可拖动、松手吸附边缘、**点击才展开**控制台。外圈细环在运行时旋转，颜色区分状态。

---

## 支持范围

| 项目 | 范围 |
|------|------|
| Android 版本 | **Android 10（API 29）～ Android 17（API 37）** |
| CPU 架构 | `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64` |

---

## 自行编译

### 环境要求

| 项目 | 要求 |
|------|------|
| JDK | **17 或更高** |
| Android SDK | Platform 37 |
| Gradle | 用仓库自带的 Wrapper，**不需要**单独安装 |

### 三步走

```bash
git clone https://github.com/CTFKFC/LinuxDoAssistant.git
cd LinuxDoAssistant

# Debug 包（无需配置签名，可直接安装调试）
./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

### Release 包与签名

不配置签名也能构建 —— 会**自动回退到 debug 签名**，方便快速试用：

```bash
./gradlew :app:assembleRelease
```

要用你自己的正式签名：

```bash
# 1. 生成密钥库
keytool -genkeypair -v \
  -keystore keystore/release.jks -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 -alias linuxdo

# 2. 填入密码
cp keystore/keystore.properties.example keystore/keystore.properties
$EDITOR keystore/keystore.properties

# 3. 构建
./gradlew :app:assembleRelease
```

> `keystore/*.jks` 与 `keystore.properties` 已在 `.gitignore` 中，不会被提交。
>
> ⚠️ **密钥库丢失后就无法给已安装的应用推送更新**（签名对不上，用户必须卸载重装）。请离线备份。

### 可选构建参数

```bash
# 开启 R8 混淆与资源压缩（默认关闭，需要较多内存）
./gradlew :app:assembleRelease -Plinuxdo.minify=true

# SDK 目录只读、无法自动安装 build-tools 时，指定本地已有的版本
./gradlew :app:assembleDebug -Plinuxdo.buildTools=36.0.0
```

### 检查与测试

```bash
./gradlew lintDebug          # 静态检查，重点看 NewApi
./gradlew testDebugUnitTest  # 单元测试
```

### 常见构建问题

| 现象 | 原因与解法 |
|------|-----------|
| 依赖下载一直卡住不动 | **Gradle 不读 `http_proxy` 环境变量**。在 `gradle.properties` 或 `~/.gradle/gradle.properties` 里加 `systemProp.https.proxyHost` / `proxyPort` |
| `Failed to install build-tools;xx ... licences have not been accepted` | SDK 目录只读或缺少 `licenses/`。用 `-Plinuxdo.buildTools=<你已安装的版本>` 指定 |
| `OutOfMemoryError: Metaspace` | 调高 `gradle.properties` 的 `MaxMetaspaceSize`（≥ 512m） |
| 报错提到 `org.jetbrains.kotlin.android` | **AGP 9.0 起已内置 Kotlin 支持**，不要再声明这个插件 |

### 低内存机器（≤ 4GB）

在 `~/.gradle/gradle.properties` 里覆盖：

```properties
org.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=640m
kotlin.compiler.execution.strategy=in-process
org.gradle.parallel=false
org.gradle.workers.max=1
```

`in-process` 会省掉一整个 Kotlin 守护进程的 JVM，但**代价是 Kotlin 编译器要挤进 Gradle 守护进程的元空间**，`MaxMetaspaceSize` 给少于 512m 会直接 OOM。

---

## 架构

```
app/          Activity、Compose 页面、导航、前台服务、悬浮窗、权限门禁
core/         设计系统（玻璃拟态）、数据层、DoH、WebView 宿主、崩溃兜底
automation/   脚本引擎 + JS Agent（不含任何 Android UI 依赖，可纯 JVM 测试）
```

### 引擎状态机

```
Idle → WaitingForLogin → FetchingLevelInfo → EnteringCategory
     → ListingTopics → BrowsingTopic → [Liking] → [Replying] → …→ Finished
```

引擎通过 `PageAgent` 接口与页面交互，因此可以在**纯 JVM 单测里跑完整会话**，不需要 WebView。

### WebView 的承载方式（本项目最关键的设计）

自动化脚本靠在 WebView 里执行 JS 驱动页面，因此 WebView 必须同时满足两个条件：

1. **一直挂在某个父容器上** —— 没有父容器，Android 会冻结渲染与 JS 计时器
2. **保持真实的全屏视口** —— 视口太小，`window.scrollBy()` 根本滚不动，
   页面永远停在第一屏，楼层计数器不更新

所以：**WebView 永远以全屏尺寸躺在最底层，从不移动、从不缩放、从不销毁**。
切到别的标签页时用不透明背景盖住它即可——被遮挡的 View 依然正常布局与滚动。

退到后台时，前台服务会把它接管到一个**全屏尺寸但移到屏幕外**的悬浮窗上
（`x = -screenWidth` + `FLAG_LAYOUT_NO_LIMITS`），系统仍视其为可见窗口，脚本得以继续。

> ⚠️ 这个技巧在各家 ROM 上表现不一致（MIUI / EMUI 的后台管理更激进）。
> 真机上通常还需要给应用加「电池优化豁免」与「后台运行白名单」，
> 且**不保证在所有厂商 ROM 上都能长时间存活**。

### 从上游继承的关键规则

这些是原作者踩坑踩出来的，改了就会失效：

1. **必须点击 `<a>` 链接进入话题** —— 直接改 URL 不会被 Discourse 计入「浏览话题」
2. **楼层计数器是唯一可信进度源** —— 不能数滚动次数，也不能判断「是否到底」（无限滚动永远到不了底）
3. 楼层计数有两种 DOM 布局：`.timeline-replies`（宽屏）和 `#topic-progress .nums`（窄屏，手机走这条）
4. 滚动节奏下限：等 2–4 秒再滚 600–1200px，太快计数器不更新
5. 楼层连续 3 次不变 → 加大到 1500px 破卡死
6. 未读话题靠 `.badge.badge-notification.new-topic` 识别
7. 登录态统一看 `#current-user`
8. **有人机验证，必须用户手动登录**；检测到 Cloudflare 挑战页时脚本**完全不动**，
   因为无感验证只要中途一刷新就前功尽弃

站点改版时，选择器全部集中在
[`linuxdo_agent.js`](automation/src/main/assets/js/linuxdo_agent.js)
顶部的 `SEL` 对象里，改那一处即可。

---

## 相对上游修复的缺陷

移植过程中修掉的上游问题（代码注释里都标了具体位置）：

| 上游缺陷 | 本项目做法 |
|---|---|
| **JS 字符串拼接注入** —— 回复内容含 `'`/`\`/换行即崩溃 | 纯 Kotlin JSON 编码器 + JS 侧 `JSON.parse`，另转义 `<`/`>`/`&`/U+2028/U+2029，**20 个回归测试覆盖** |
| **子线程创建 Tk root** —— Tcl/Tk 非线程安全 | Compose 单主线程 UI，引擎跑协程，靠 StateFlow 通信 |
| **指标靠中文子串猜测** —— 「阅读时间」被加上帖子数 | 显式别名表精确匹配，匹配不上归 `UNKNOWN` 且不参与计算 |
| **「帖子数量」语义混淆** —— 填 50 实际爬完一个帖子就停 | 拆成「目标话题数」/「目标已读数」两个独立选项 |
| **伪造楼层数据** —— 读不到楼层就 `floors += 3` | 如实记 0 并标记 `unreliable`，UI 显示 ⚠ |
| **裸 `except:`** —— 吞掉 KeyboardInterrupt | `runCatching` + 具体异常 + 写入运行日志 |
| **四份重复爬虫实现** —— 板块列表已漂移 | 单一引擎 + 单一板块表 |

---

## 许可证

本项目以 **MIT** 许可发布，全文见 [LICENSE](LICENSE)。

### 关于上游项目

本项目是 [`icysaintdx/linuxdosss`](https://github.com/icysaintdx/linuxdosss) 的**衍生作品**。

该项目在其 `README.md` 中声明为 **MIT License**，但**仓库根目录并没有 `LICENSE` 文件**。
本项目据其 README 的声明认定为 MIT，并在 [NOTICE.md](NOTICE.md) 中：

- 如实记录了上述情况
- 保留对原作者 `icysaintdx` 的署名
- 附上 MIT 许可证全文，以满足「须在所有副本中包含版权声明与许可声明」的要求
- 逐项列出从上游移植的内容及其对应位置

如果你是原作者并对此有任何异议，请通过 issue 联系，我们会立即配合处理。

第三方依赖的许可证清单见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

---

## 贡献

欢迎 issue 与 PR。提崩溃类 issue 时请附上「设置 → 崩溃日志」的内容，
或 `adb logcat -b crash` 的输出。
