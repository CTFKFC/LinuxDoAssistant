# NOTICE

本项目（LinuxDoAssistant，Android 版）是对以下上游项目的**衍生作品**（derivative work）。

---

## 上游项目

**linuxdosss** — Linux.do 论坛刷帖助手（Python / DrissionPage / Tkinter）

- 作者：`icysaintdx`
- 仓库：https://github.com/icysaintdx/linuxdosss
- 声明的许可证：**MIT License**
- 本项目参考的版本：**v8.5**

### ⚠️ 关于上游许可证的一点如实说明

上游仓库的 `README.md` 中明确写有：

> ## 许可证
>
> MIT License

**但该仓库根目录并没有 `LICENSE` 文件**，也没有在任何源文件头部附上 MIT 的完整条款与版权行。

因此本项目的处理方式是：

1. 按上游 README 中的声明，认定其以 MIT 许可发布；
2. 在此显著位置保留对原作者 `icysaintdx` 的署名；
3. 附上 MIT 许可证全文（见下）以满足 MIT 「须在所有副本中包含版权声明与许可声明」的要求；
4. 如原作者后续补充或变更了许可条款，本项目将据此调整。

如果你是原作者并对此有任何异议，请通过上游仓库 issue 联系，我们会立即配合处理。

### 从上游移植的内容

本项目重写了全部代码（Python → Kotlin），但以下**领域知识**直接来自上游，属于受版权保护的表达：

| 内容 | 上游位置 | 本项目位置 |
|------|---------|-----------|
| Discourse DOM 选择器集合 | `linux_do_gui.py`、`test_floor_climbing.py` | `automation/src/main/assets/js/linuxdo_agent.js` |
| 「必须点击链接而非跳 URL」的核心结论 | `linux_do_gui.py:1007-1029` | 同上 `clickTopic()` |
| 楼层计数器双布局解析法 | `linux_do_gui.py:657-702` | 同上 `getFloorInfo()` |
| 爬楼节奏参数（2-4s / 600-1200px / 卡住阈值 3） | `linux_do_gui.py:704-798` | `AutomationEngine.climbDeep()` |
| 16 个板块及其路径与默认开关 | `linux_do_gui.py:123-140` | `DefaultCategories.kt` |
| 65 条回复模板 | `linux_do_gui.py:152-226` | `DefaultReplyTemplates.kt` |
| connect.linux.do 等级页四种 DOM 结构 | `linux_do_gui.py:412-526` | `linuxdo_agent.js` `getLevelInfo()` |

### 相对上游的修正

本项目并非逐行照搬，以下上游缺陷已在移植中修复（详见 `README.md`）：
JS 字符串拼接注入、子线程创建 Tk root、指标中文子串误匹配、
「帖子数量」语义混淆、楼层数据造假、四份重复实现等。

---

## 上游许可证全文（MIT）

```
MIT License

Copyright (c) icysaintdx

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 本项目许可证

本项目同样以 **MIT License** 发布，全文见根目录 `LICENSE`。

---

## 第三方依赖

所有第三方库的许可证清单由 AboutLibraries Gradle 插件在构建时自动生成，
产物见 `app/src/main/res/raw/aboutlibraries.json`，
并可在 App 内「设置 → 关于 → 开源许可」页面查看。

主要依赖及其许可证：

| 依赖 | 许可证 |
|------|--------|
| Kotlin / Coroutines（JetBrains） | Apache-2.0 |
| AndroidX（Core / Lifecycle / Activity / Navigation / Room / DataStore） | Apache-2.0 |
| Jetpack Compose + Material 3 | Apache-2.0 |
| Hilt / Dagger（Google） | Apache-2.0 |
| OkHttp + okhttp-dnsoverhttps（Square） | Apache-2.0 |
| AboutLibraries（Mike Penz） | Apache-2.0 |

---

## 免责声明

本工具通过自动化手段产生浏览、点赞、回复行为，**这类行为违反绝大多数论坛的服务条款**，
上游作者也在其 README 中记录了「曾有用户因自动回复被举报」。

- 自动点赞与自动回复默认**关闭**，开启时会弹出风险确认
- 使用本工具造成的一切后果（包括但不限于账号被封禁）由使用者自行承担
- 本项目仅供学习交流，请勿用于任何违反社区规则的行为
