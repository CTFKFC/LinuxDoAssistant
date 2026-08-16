# 第三方依赖许可证

本文件列出本项目直接依赖的第三方组件及其许可证。
上游项目（linuxdosss）的归属见 `NOTICE.md`。

---

## 依赖清单

| 组件 | 版本 | 许可证 | 用途 |
|------|------|--------|------|
| Kotlin Stdlib / Coroutines | 2.4.10 / 1.9.0 | Apache-2.0 | 语言运行时与并发 |
| AndroidX Core KTX | 1.13.1 | Apache-2.0 | 基础扩展 |
| AndroidX Lifecycle | 2.8.7 | Apache-2.0 | 生命周期、ViewModel |
| AndroidX Activity Compose | 1.9.3 | Apache-2.0 | Activity 与 Compose 集成 |
| Jetpack Compose（BOM） | 2026.06.01 | Apache-2.0 | UI 框架 |
| Material 3 | 随 BOM | Apache-2.0 | 设计系统基础 |
| AndroidX DataStore Preferences | 1.1.1 | Apache-2.0 | 设置持久化 |
| OkHttp | 5.4.0 | Apache-2.0 | HTTP 客户端（DoH 请求接管） |
| OkHttp DNS-over-HTTPS | 5.4.0 | Apache-2.0 | DoH 解析 |
| JUnit | 4.13.2 | EPL-1.0 | 单元测试（仅测试期） |

### 构建工具

| 工具 | 版本 | 许可证 |
|------|------|--------|
| Gradle | 9.7.0 | Apache-2.0 |
| Android Gradle Plugin | 9.3.1 | Apache-2.0 |

---

## 图标

应用内 9 个图标（Dashboard / Public / Bolt / Stop / PlayArrow / Settings /
Refresh / Home / ArrowBack / ArrowForward）的路径数据取自
**Google Material Symbols**（Apache-2.0），在 `AppIcons.kt` 中以
`ImageVector` 形式重绘，未引入 `material-icons-*` 依赖。

Material Symbols 版权归 Google LLC，以 Apache License 2.0 授权。

---

## Apache License 2.0 全文

上表中标注 Apache-2.0 的组件均适用以下条款：

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

完整条款见 https://www.apache.org/licenses/LICENSE-2.0

---

## Eclipse Public License 1.0（JUnit）

JUnit 4 以 EPL-1.0 授权，仅在测试期使用，**不打包进发布的 APK**。
完整条款见 https://www.eclipse.org/legal/epl-v10.html
