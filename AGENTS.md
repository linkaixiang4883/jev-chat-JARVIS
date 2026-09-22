# AGENTS.md — Jev 聊天助手（Android）智能体操作手册

自包含的冷启动手册：读完即可定位代码、改功能、跑构建、查故障。**不引用任何计划文件**（计划会归档）。
产品一句话：挂在聊天 App 旁的**非侵入**对话副驾 —— 无障碍读到对方最新消息 → Jev 判断（意图/危险/需求/动作）→ 生成 3 条候选并由 Jev 排序 → 悬浮窗展示 → 人点「填入」进输入框。**程序永不发送消息、永不碰转账/红包/收款。**

## 技能加载清单（强制，按序）

| # | 技能 | 提供什么 |
|---|---|---|
| 1 | `android-chat-assistant` | 本领域能力边界与合规定级、探针流程、各 App 节点读取配方（微信伪装服务的出处与风险） |
| 2 | `android-chat-automation` | 读/写/生成三段的官方口径（IME 边界、Android 13 受限设置、腾讯规范条款原文） |

领域通用坑写在技能里；本文件只写**本项目**的架构、事实、排查入口。

## 文档同步约束（红线）

- 架构/接口/构建方式变了 → 同一任务内用 `patch` 回写本文件；**不写实施记录、进度日志**（最多一句「当前状态」）。
- 新踩的坑：跨项目→写进技能（如 `android-chat-assistant`），项目细节→本文件「已知限制」。坑不进架构章节。
- 禁止 `git commit` / `git push`（由人拍板）。禁止任何文件/日志出现 `sk-or-` 前缀密钥；**聊天正文不进日志**（现有日志只打 `side:长度`）。
- 目录归属：`app/` 与根 gradle 文件 = Android 构建方；`tools/jev/` = 判断层题目集（Python，PC 上跑）；`docs/` = 公共验收尺子；`apk/` = 签名产物（**刻意入库**，见 `.gitignore` 的 `!/apk/*.apk`）；`_reports/` = 任务交付报告（已 gitignore）。跨边界的问题**只报告、不改**。

## 项目约定

**产品红线（改任何代码都不得违背）**：不 hook / 不 Xposed / 不改目标 App / 不读其数据库（只用系统无障碍 + 截屏）；**绝不自动发送**、绝不 `performAction` 发送按钮；不触碰转账/红包/收款相关元素。

- 应用 ID `com.jev.probe`，根工程名 `jev-android`；Kotlin + 传统 View/XML（**不用 Compose**）；**minSdk 28**（本 fork 保留 28：测试机 MI 8 / Android 9；上游口径是 30 —— 注意 API 30+ 能力如 `takeScreenshot` 在 28 上不可用，OCR 路径要能降级）/ compileSdk 35 / targetSdk 35；版本号在 `app/build.gradle.kts`（当前 **versionCode 5 / 1.4**）。
- **上游跟踪**：`git fetch https://github.com/Finderchangchang/jev-chat-JARVIS.git main:refs/remotes/upstream/main` → `git merge refs/remotes/upstream/main`；冲突约定：**上游代码为准**，本 fork 的差异补成预设/文档（见「架构与数据流 → 网络侧」），不改上游的文件风格。合并后 `./gradlew assembleDebug` 必须绿再收工（上游 v1.3 带 ML Kit，首次构建会拉依赖）。
- 依赖只有 4 个：core-ktx 1.13.1、appcompat 1.7.0、material 1.12.0、constraintlayout 2.1.4。**不引第三方 SDK**：HTTP 用 `HttpURLConnection`、JSON 用 `org.json`、线程用 `java.util.concurrent`。加依赖属架构变更，先说明理由。
- **两条「措辞真源」必须同源**：判断题目在 `tools/jev/questions.py`（PC 校准用）与 `app/.../jev/JevQuestions.kt`（运行时）各一份，**改一处必须同步另一处**。instructions / criteria 用英文，state 里的聊天正文保留中文。
- `Msg.side` 只用 `me` / `other`（对应 Jev state 的 `from`）；「her/她」只出现在题目文案里，代码里不新增。
- 编码 UTF-8；本机中文 Windows，Python 读写显式 `encoding='utf-8'`，脚本开头重绑 stdout（见 `tools/jev/calibrate.py`）。
- 路径全 ASCII（Android 构建工具在中文路径上会炸）。本 checkout 在 `D:\work\app\kotlin\jev-chat-JARVIS`；`docs/acceptance.md` 等文件里的 `H:\ai_tool\jev-android`、`H:\android\{jdk,sdk,gradle-home}` 是**原作者机器**的路径，本机不存在（本机 JDK 21 在 `C:\Program Files\Java\jdk-21.0.10`，SDK 在 `%LOCALAPPDATA%\Android\Sdk`）。

### 仓库来源与上游文件（不要改上游文件）

- 本仓库是 **fork**：`origin` = `https://github.com/linkaixiang4883/jev-chat-JARVIS`；**上游（原作者）** = `https://github.com/Finderchangchang/jev-chat-JARVIS`（`IDEA.md` 记的就是这条出处）。上游带来的文件（`CLAUDE.md`、`docs/probe_spec.md`、`docs/acceptance.md` 等）**保留原样**——凡是上游文件里的机器路径、历史结论与现状不符，以本文件为准，不要为了本机去改上游文件（下次 merge 会冲突/被打回）。
- **`CLAUDE.md`（上游文件）不会被注入系统提示词，也不必删**：Hermes 的项目上下文**只加载优先级最高的那一类**——
  `.hermes.md` / `HERMES.md`（从 cwd 向上找 git 根）> `AGENTS.md`（git 根 → cwd 逐级链）> `CLAUDE.md`（仅 cwd）> `.cursorrules`。
  本仓库已有非空 `AGENTS.md`，`CLAUDE.md` 因此被**遮蔽**。实跑验证（2026-09-22，`agent/prompt_builder.py::build_context_files_prompt`）：磁盘上两个文件都在（AGENTS 14002 字符 / CLAUDE 3056 字符），进提示词的只有 AGENTS.md，输出里 `## CLAUDE.md` 不存在。
  - ⇒ **不要在本仓库或其父目录链创建 `.hermes.md`**：它优先于 AGENTS.md，会反过来遮蔽本文件。
  - ⇒ 删 `CLAUDE.md` 无意义（下次 merge 会回来，遮蔽已生效）；它里面想留的硬约束/实测背景要**合并进本文件**，而不是去改它。
  - ⇒ 会话中途新建/删除这些文件对**当前**会话无效（系统提示词每会话只构建一次，仅压缩边界可能重建）——看到旧文件内容属正常，下一会话才换。
- 交付报告写 `_reports/<任务名>_report.md`：做法与证据 → 逐条文件清单 → 验收命令的**真实输出** → 自验缺口。建者不自证，不许编输出。
- **提交身份（2026-09-22 踩过）**：本仓库历史里的 `Vikicc / Finderchangchang <1031066280@qq.com>` 是**原作者本人**（老提交可用 `git rev-list --count upstream/main..<sha>` 验证全来自上游）——**绝不要用这个身份提交我们的工作**（那是冒用）。本地 config 已设为我们自己的身份：`linkaixiang4883 <28226678+linkaixiang4883@users.noreply.github.com>`（GitHub 官方 noreply；id 用 `api.github.com/users/<login>` 取）。**推送到裸 URL 时 `--force-with-lease` 会报 `stale info`**，要用显式租约：`git push --force-with-lease=refs/heads/main:<远端当前 sha> git@github.com:<owner>/<repo>.git main`。
- **上游同步状态**：作者仓库 `Finderchangchang/jev-chat-JARVIS` 持续更新（本 fork 落后 36 个提交，2026-09-22 计）；同步上游时注意 README/AGENTS 会冲突，按「我们的口径优先、上游新内容合并」处理。

## 构建与测试

**环境前置**：JDK 17+，Android SDK 含 platform 35 + build-tools 35。让 Gradle 找到 SDK：设 `ANDROID_HOME`，或在仓库根写 `local.properties`（`sdk.dir=...`，已 gitignore）。
本机复检（2026-09-22）：JDK 21 在 `C:\Program Files\Java\jdk-21.0.10`（`JAVA_HOME` 未设）；SDK 在 `%LOCALAPPDATA%\Android\Sdk`（android-35、build-tools 35 已装）；`local.properties` 首次构建前必须补（本次已补 `sdk.dir=C:/Users/LKX/AppData/Local/Android/Sdk`，文件已 gitignore）；`~/.gradle/wrapper/dists` 无 gradle-8.9（首次构建会自动下载，实测 2m07s）；`adb` 在 SDK 的 `platform-tools` 下但不在 PATH。首次构建若下载失败，加代理参数：`./gradlew -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808 …`。

```bash
./gradlew assembleDebug     # Windows 用 gradlew.bat；产物 app/build/outputs/apk/debug/app-debug.apk
export JEV_KEYSTORE_PROPS=D:/work/keys/jev/jev-release.properties   # 本机签名材料（仓库外；见下）
./gradlew assembleRelease   # 产物 app/build/outputs/apk/release/app-release.apk（已归档 apk/jev-assistant-v1.3-release.apk）
adb install -r apk/jev-assistant-v1.3-release.apk
```

- **签名材料在仓库外**：`D:\work\keys\jev\{jev-release.p12, jev-release.properties}`（PKCS12，alias `jev`；properties 含口令，**不许进仓库/日志/git**）。仓库里备了 `env.ps1`（已 gitignore）导出 `JEV_KEYSTORE_PROPS`；bash 用 `export JEV_KEYSTORE_PROPS=D:/work/keys/jev/jev-release.properties`。
- properties 读不到时 AGP **静默出未签名包**（`app-release-unsigned.apk`，不报错）→ 打完 release 必须 `apksigner verify` 复核。
- **签名身份**：v1.3 起用**新建的密钥**（`CN=Jev Assistant …`，证书 SHA-256 `f190d62d…`），与 v1.0–v1.2 的签名**不同** → 老用户升级必须**先卸载**（否则签名冲突）；原签名材料留在原开发机（`H:\android\keys`），拿回来才能无缝续签。
- debug ↔ release 签名不同，必须卸载重装；卸载会清掉密钥与设置；MIUI / HyperOS 重装后悬浮窗权限会被重置。

**判断层（PC，Python，标准库 only）**

```bash
export OPENCODE_API_KEY=...              # Zen 的 Jev + Go 的起草共用；只从环境变量读，绝不落盘/进日志
export TYPESAFE_API_KEY=...              # 可选：Jev 后端切 TypeSafe 官方时才需要
python tools/jev/demo_meme.py [--provider zen|typesafe]
python tools/jev/calibrate.py [--provider zen|typesafe] [--limit N]
python -m unittest discover -s tools/jev/tests -v   # provider 路由 / 请求头 / 错误映射单测（stdlib，不联网）
```

`calibrate.py` 退出码：`0`=闸门全过，`2`=闸门未达标（`danger_level` MAE < 1.0、`true_intent`/`she_needs` 命中 ≥ 60%），`1`=有请求错误；报告写 `tools/jev/report/calibration.{json,md}`（已 gitignore）。

**测试现状**：Kotlin 侧有 1 个单测 `app/src/test/java/com/jev/probe/jev/JevEndpointsTest.kt`（`./gradlew.bat testDebugUnitTest` → 6 passed；`testImplementation("junit:junit:4.13.2")` 是唯一新增依赖，仅测试期、不进 APK、有单测）。验证 = 构建零 error + 真机冒烟（`docs/acceptance.md` D 节：新消息 ~1.5s 出窗且 ≥3 条已排序候选、填入后未发送、自己发的不触发、切后台悬浮窗隐藏、10 分钟静默期零调用、断网给可读错误不崩、密钥不进 logcat）。**改采集层或填回链路后必须真机跑一遍，别只看编译通过。**

`docs/acceptance.md` 是公共尺子：A 构建 / B 探针门禁（P1，**已有结论**：伪装服务可读微信 8.0.78 节点，路线 A）/ C 判断层闸门 / D 真机冒烟。

## 当前状态

v1.4（versionCode 5）：**上游 v1.3 已合并**（三路接口可配 judge/reply/vision、知识库 + 关联上下文、**OCR 兜底**（ML Kit 中文离线，APK 27M、arm64-only）、官网 `site/`），并在其上加了本 fork 的**两个预设**：**判断 = OpenCode Zen**（`/zen/v1/systemone`，`jev-1.13-free` 免费）、**回复 = OpenCode Go**（`/zen/go/v1`，`glm-5.3-flash`，请求带 `x-opencode-session` + 自述 UA）。已用仓库外密钥签名（证书 SHA-256 `f190d62d…`）并装到测试机（MI 8 UD / Android 9）；归档 `apk/jev-assistant-v1.4-release.apk`（同目录的 `apk/jev-assistant-v1.3-release.apk` 是上游官方包）。微信/QQ/X 三线实测结论来自 v1.2；v1.3 的 OCR/知识库/三路配置**未真机验证**。

## 架构与数据流

```
[聊天 App 的聊天窗]  微信 com.tencent.mm / QQ com.tencent.mobileqq / X com.twitter.android / 飞书 com.ss.android.lark
   │ ① 无障碍事件 TYPE_WINDOW_STATE_CHANGED / CONTENT_CHANGED / VIEW_SCROLLED
   ▼
capture/ChatCaptureService.onAccessibilityEvent()
   │   TYPE_WINDOW_STATE_CHANGED 时用 rootInActiveWindow.packageName 判「是否离开聊天 App」
   │   （不用事件自带的包名：输入法/状态栏事件会误导，导致悬浮窗闪烁）→ 非适配包则 hide()
   ▼
capture/ChatCaptureService.maybeCapture()
   │ ② adapters[前台包名].extract(root, resources) → ChatSnapshot(title, List<Msg(side,text)>)；不在聊天窗返回 null
   │ ③ 去重 signature() = 最近 6 条的 "side:text"：同签名且窗在 → 什么都不做；
   │    同签名但窗没了 → 只 showIdle 补窗（不重新分析，省 token）；换 App 清空签名
   │ ④ 触发条件：latestFrom == "other" && prefs.autoAnalyze && isAllowed(title)；
   │    任一不满足 → showIdle（气泡暗着，用户手点「分析当前对话」）
   ▼ debounce 800ms（postDelayed 吸收 content-changed 事件风暴）
capture/ChatCaptureService.runAnalysis()
   │ ⑤ 两个 worker 任务**并行**提交（固定线程池 2 线程）：
   │     A) jev/JudgeClient.judge(snapshot, relationship, ctx) → 判断路一次请求出 7 题（~1s）→ overlay.showJudgment()
   │     B) jev/ReplyClient.draft(snapshot, relationship, ctx) 起草 3 条（回复路）→ jev/JudgeClient.rank(...) 排序 → overlay.showReplies()
   │    判断先渲染，候选慢一步补上；analyzing 标志防重入；ctx 来自 core/kb/ContextBuilder（知识库+历史，开关关掉则不带）
   │    正文读不到时（adapters 返回空 messages）先走 capture/ocr/：ScreenCapture 截屏 + MlKitOcr 中文识别（限频/退避/躲悬浮窗）
   ▼
overlay/OverlayController（TYPE_APPLICATION_OVERLAY：可拖气泡 + 半透明面板）
   │ ⑥ 面板内容：危险等级徽章（配色随分数）→ 对方真实意图 + 把握 → 「要什么 · 动作 · 可给实质」→ 3 张候选卡（Jev 排序 + 占比）
   ▼ 用户点「复制」或「填入」
capture/ChatCaptureService.fillInput(text)   （worker 线程，含 sleep 校验，勿挪主线程）
   │ ⑦ 三级兜底：ACTION_SET_TEXT → 150ms 后刷新读回逐字校验 → 失败则 ACTION_CLICK 聚焦重试 → 再失败剪贴板 + ACTION_PASTE（先清空防重复）
   ▼
[聊天 App 输入框已填好]  —— **到此为止，绝不点发送按钮**
```

网络侧（v1.4 = 上游的三路接口可配 + 本 fork 的两个 opencode 预设；**无 SDK，HttpURLConnection + org.json**）：
端点由 `core/Prefs.kt` 按 provider 拼，发包在 `jev/{JudgeClient,ReplyClient,VisionClient}.kt`，公共层 `jev/HttpJson.kt`：

```
判断（JudgeClient） judge_provider ∈ { openrouter | typesafe | opencode-zen | custom }
  openrouter   → $judge_base_url/alpha/decisions        model typesafe/jev-1.13
  typesafe     → $judge_base_url/v1/systemone           model jev-latest
  opencode-zen → $judge_base_url/zen/v1/systemone       model jev-1.13-free（免费；本 fork 预设）
  custom       → 地址栏按原样 POST（要自带完整路径）
回复（ReplyClient） $reply_base_url + /chat/completions（OpenAI 兼容）；本 fork 预设 OpenCode Go：
  https://opencode.ai/zen/go/v1 → /chat/completions，model glm-5.3-flash
视觉（VisionClient） $vision_base_url + /chat/completions（image_url 内容块，OCR 兜底用）
```

- 请求体形状不变：判断/排序 `{model,state,questions} → {model,answers,usage}`；回复 `{model,messages[],temperature} → choices[0].message.content`。
- **请求头按主机给**（`HttpJson.headersFor(url, sessionId)`）：`openrouter.ai` → `HTTP-Referer`/`X-Title`；**`opencode.ai` → 自述 `User-Agent` + `x-opencode-session`**；其它主机为空。
- **opencode.ai 的两条硬约束（实测）**：库默认 UA 会被 **Cloudflare error code 1010** 拦（403）→ 必须自述 UA；Go 的 chat 端缺 `x-opencode-session` → `400 MissingSessionID`（值只需不透明且稳定，`Prefs.opencodeSession` 首次读取生成 UUID 落盘）。Zen 的判断端点不需要该头。
- Zen 免费档只在 `/zen/v1/systemone` 对普通客户端开放；免费 **chat** 模型一律 `403 FreeTierError`（付费 `jev-1.13` 无 Zen 余额时 `402`）。

一次完整分析 = **3 个 HTTP 请求**（1 次生成 + 2 次判断）；state 只带最近 10 条消息 + 关系描述。

## 组件生命周期

**启动（依赖顺序）**

1. 系统绑定无障碍服务 → 入口类 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`（伪装类名，全部逻辑继承自 `ChatCaptureService`）。**不要改这个类名或它的 Manifest 注册**——伪装正是微信暴露节点树的原因。
2. `ChatCaptureService.onServiceConnected()`：`Prefs(this)` → `OverlayController(this)` → 挂 `overlay.onManualAnalyze`（面板「重新分析」回调）。
3. `KeepAliveService.start(this)`：前台服务（`foregroundServiceType=specialUse` + IMPORTANCE_MIN 常驻通知，`START_STICKY`），把进程抬到前台重要性，抗 MIUI/HyperOS 冻结。
4. `main.postDelayed(900ms)` 自愈补偿：被 ROM 杀掉后重连时主动跑一次 `maybeCapture()`，气泡自己回来，不必等用户滚动。
5. 首次捕获 → 去重 → 触发条件通过 → 800ms debounce → `runAnalysis()`。

**退出 / 拆解（逆序）**

1. `ChatCaptureService.onDestroy()`：`overlay.onManualAnalyze = null`（切断死后回调，杜绝幽灵点击）→ `overlay.hide()`（`WindowManager.removeView`）→ `overlay = null` → `worker.shutdownNow()`（丢队列；此后 `submit()` 捕 `RejectedExecutionException` 静默丢弃，不崩进程）。
2. `SettingsActivity.onDestroy()` → `worker.shutdownNow()`。
3. 悬浮窗：`hide()` 后置空引用；下次 `show*` 由 `ensureRoot()` 懒重建，`Settings.canDrawOverlays=false` 时只打一条日志、不出窗。

**悬浮窗状态机**：`showIdle`（气泡 alpha 0.55；从未分析过时面板给「分析当前对话」按钮）→ `showLoading`（自动展开）→ `showJudgment`（判断先到）→ `showReplies`（候选补齐）→ 点「填入」自动折叠（`toggle()`，露出输入框与键盘）；`showError` 复用同一面板。气泡可拖动（位置存 `bubbleX/Y`）、长按弹菜单（打开设置 / 隐藏助手 / 取消）。

## 关键接口签名

**采集层：新增一个聊天 App 只动这两处**

```kotlin
// capture/ChatAppAdapter.kt（v1.3 起是三态契约，服务靠它决定要不要走 OCR 兜底）
interface ChatAppAdapter {
    val pkg: String
    fun extract(root: AccessibilityNodeInfo, res: Resources): ChatSnapshot?
    // null             → 不在该 App 的聊天窗（列表页/朋友圈/设置）→ 服务什么都不做
    // messages 为空     → 在聊天窗但树里没正文（自绘控件）→ 服务可走截屏 + OCR 兜底
    // messages 非空     → 正常采集
}
// capture/ChatCaptureService.kt:46
private val adapters = listOf(WeChatAdapter(), QQAdapter(), XAdapter(), FeishuAdapter()).associateBy { it.pkg }
```

接入步骤：① 新写一个 `ChatAppAdapter`，把当前窗口变成 `ChatSnapshot(title, List<Msg(side, text)>)`；② 加进 `adapters`。判断、候选、悬浮窗、填回全部复用。动手前先 `adb shell uiautomator dump` 看目标 App 暴露了什么：

| 适配器 | 包名 | 判「在聊天窗」的依据 | 正文来源 | 判「我 / 对方」 |
|---|---|---|---|---|
| `WeChatAdapter` | `com.tencent.mm` | 存在气泡 `com.tencent.mm:id/bkl` | 气泡节点 `text` | 气泡中心 x > 屏宽/2 → me |
| `QQAdapter` | `com.tencent.mobileqq` | 存在 `id/mjn` 节点（全程单 SplashActivity，**不能按 Activity 判**） | `id/mjn` 的 `text` | 气泡左右边离头像列（屏宽×0.13）谁近；**不用中心点** |
| `XAdapter` | `com.twitter.android` | 有可编辑节点（唯一的 EditText）**且**解析出行 > 0 | `android.view.View` 的 `contentDescription`：`发件人：正文。时间。Read。`（`。` 是字段分隔符，尾部 chrome 按 Read/已读→时间→句号 顺序剥离） | content-desc 发件人是「你」/「You」→ me；列表页行含 `, @` 直接排除 |
| `FeishuAdapter` | `com.ss.android.lark` | 出现 `:id/message` 或 `:id/bubble_content_container` | 消息带里**无 id 的 TextView**（churn 用 `isChrome(id)` 排除：标题/昵称/时间/系统提示/输入框）；正文自绘，多数情况只有带 TextView 的内容可读 | 文本中心 x > 屏宽/2 → me |

标题来源：微信走 `findTitleInActionBar()`（首个气泡上方、顶部 14% 内、横向大致居中的短文本）；QQ 先取 `id/371`，取不到再回退该函数；飞书取 `:id/group_name`；X 用该函数但把居中带放宽到 15%–85%（X 左对齐标题）。所有树遍历都有 5000/6000 节点上限护栏。

**数据模型与判断层**

```kotlin
data class Msg(val side: String, val text: String)                        // "me" | "other"
data class ChatSnapshot(val title: String?, val messages: List<Msg>) {
    val latestFrom: String? ; fun signature(): String                     // 最近 6 条签名
}
data class Analysis(val trueIntent: Choice?, val dangerLevel: Score?, val sheNeeds: Choice?,
                    val shouldReplyNow: Double?, val bestAction: Choice?, val tensionResolved: Double?,
                    val literalQuestion: Double?, val rankedReplies: List<RankedReply>,
                    val latencyMs: Long, val error: String? = null)

class JudgeClient(private val prefs: Prefs) {            // 判断路：读 judge_provider / judge_base_url / judge_key / judge_model
    fun judge(snapshot: ChatSnapshot, relationship: String, ctx: ChatContext? = null): Analysis   // 7 题；错误不抛，放进 Analysis.error
    fun rank(snapshot: ChatSnapshot, relationship: String, candidates: List<String>,
             ctx: ChatContext? = null): List<RankedReply>
}
class ReplyClient(private val prefs: Prefs) {            // 回复路：任何 OpenAI 兼容 /chat/completions
    fun draft(snapshot: ChatSnapshot, relationship: String, ctx: ChatContext? = null): List<String>   // 起草 3 条候选
    fun ping(): String ; fun summarize(text: String): String
}
class VisionClient(private val prefs: Prefs) {           // 视觉路（OCR 兜底）
    fun ask(imageBase64Jpeg: String, prompt: String): String
    companion object {
        fun encodeJpeg(bitmap: Bitmap, quality: Int = 80): String
        fun supportsVision(baseUrl: String): Boolean     // DeepSeek 官方无视觉，返回 false
    }
}
object HttpJson {                                        // 三路共用：POST + 429/529 退避 + 失败归一化成 ApiException(route,status,snippet)
    fun post(url: String, key: String, body: JSONObject, route: String,
             extraHeaders: Map<String, String> = emptyMap()): JSONObject
    fun headersFor(url: String, sessionId: String = ""): Map<String, String>   // openrouter → 署名头；opencode.ai → UA + x-opencode-session
}

object JevQuestions {
    fun judge(): JSONObject                                    // 7 题：literal_question / true_intent / danger_level(10 档) /
                                                               // should_reply_now / best_action / she_needs / tension_resolved
    fun buildState(snapshot: ChatSnapshot, relationship: String,
                   background: String = "", history: List<LogEntry> = emptyList()): JSONObject
                                                               // {"chat":{relationship,messages[{from,text}],latest_from}}（+ 可选 background/history）
    fun rankQuestion(candidates: List<String>): JSONObject                                 // 恰好 3 条，key = reply_a / reply_b / reply_c
}
```

HTTP 行为：连接 15s / 读 25s；429、529 退避重试 3 次（500ms×2^attempt）；4xx 直接失败不重试；异常转人话（`401` 密钥无效或未设置、`402` Zen 余额不足、`403 FreeTierError` 只允许 OpenCode 客户端、`400 MissingSessionID` 缺头、`403 + error code 1010` 被 Cloudflare 拦 UA、超时→「网络超时」）。密钥只作为 `Authorization` 头出现，永不落日志；**每个请求必须自述 `User-Agent`，Go 请求另带 `x-opencode-session`**。

**配置项（`core/Prefs.kt`，SharedPreferences 名 `jev_assistant`）**

| 键 | 含义 |
|---|---|
| `judge_provider` | 判断路 provider：`openrouter`（默认）/ `typesafe` / **`opencode-zen`（本 fork 预设，免费 `jev-1.13-free`）** / `custom` |
| `judge_base_url` / `judge_key` / `judge_model` | 判断路地址 / 密钥 / 模型（预设各自带默认；`custom` 要自带完整 URL） |
| `reply_base_url` / `reply_key` / `reply_model` | 回复路；尾部自动拼 `/chat/completions`。**本 fork 预设 `https://opencode.ai/zen/go/v1` + `glm-5.3-flash`**；key 留空继承判断路 |
| `vision_base_url` / `vision_key` / `vision_model` | 视觉路（OCR 兜底用；key 留空继承回复路） |
| `opencode_session` | opencode.ai 的 `x-opencode-session`：首次读取生成 UUID 落盘（Go 缺它 → 400；判断端点不需要，带上无害） |
| `context_enabled` / `context_history_count` / `auto_summary` | 知识库与历史：是否记录 / 条数 / 自动摘要 |
| `ocr_engine` / `ocr_unknown_apps` / `ocr_fallback` / `ocr_auto_analyze` | OCR 兜底：引擎（mlkit/vision）、未适配 App 是否用、读不到正文时兜底、OCR 模式自动分析 |
| `relationship` | 关系描述，进 Jev state |
| `enabled` | 总开关（关掉则事件分支直接 hide） |
| `whitelist` | 会话白名单，**按标题包含匹配**；空 = 所有会话；标题为空且白名单非空 → 不生效 |
| `overlay_opacity` / `bubble_x` / `bubble_y` | 面板透明度（60–100）/ 气泡记忆位置（-1 = 默认） |
| `auto_analyze` | 对方新消息是否自动分析；关 = 只出气泡等手点 |
| `openrouter_key`（旧） | v1.2 的单一 key：上游一次性迁移会把它搬到 `judge_key`。**注意：本 fork 早期版本存的是 `opencode_key`，不在迁移范围** → 从那个版本升级要重填一次 |

**填回链（`ChatCaptureService` 私有，worker 线程）**：`fillInput(text)` → `trySetText()`（`ACTION_SET_TEXT` + sleep 150ms + `readInput()` 内 `refresh()` 绕过节点缓存，**逐字相等**才判成功）→ 失败则 `findEditable()` + `ACTION_CLICK` 聚焦 + 300ms 后重试 → 再失败走剪贴板 + `ACTION_PASTE`（先 `setTextRaw("")` 清空，防 SET_TEXT 静默生效后重复）。成功提示「已填入，确认后自己发送」，兜底提示「已复制，长按输入框粘贴」；**任何分支都不点发送**。

## 并发模型速查

| 执行体 | 数量 | 职责 | 边界 |
|---|---|---|---|
| 主线程（`Handler(Looper.getMainLooper())`，代码里叫 `main`） | 1 | 所有无障碍回调、`maybeCapture`、悬浮窗 WindowManager 操作、debounce/postDelayed | 绝不阻塞：无 sleep、无网络、无 IO |
| `ChatCaptureService.worker` | `newFixedThreadPool(2)` | A：`judge()`；B：`draftAndRank()` 与 `fillInput()`（内含 sleep 300/150ms） | 结果一律 `main.post{}` 回主线程渲染；服务销毁后提交被静默丢弃 |
| `SettingsActivity.worker` | `newSingleThreadExecutor` | 「连通测试」`analyze()` | `onDestroy` 里 shutdownNow |
| 状态变量 | — | `lastSignature`、`analyzing`、`pendingSnapshot`、`currentSnapshot`(@Volatile)、`activePkg`、`foregroundPkg` | 只在主线程读写（`currentSnapshot` 例外，标注了 @Volatile） |

## 故障排查入口

统一日志 tag：**`JEVASSIST`**（所有类共用）。

```bash
adb logcat -s JEVASSIST
adb shell settings get secure enabled_accessibility_services   # 应含 com.jev.probe/com.google.android.accessibility.selecttospeak.SelectToSpeakService
adb shell uiautomator dump /sdcard/k.xml && adb pull /sdcard/k.xml   # 核对节点 id（微信 dump 不到，靠伪装服务在 App 里读）
```

关键日志行（原文）：

| 行 | 含义 |
|---|---|
| `capture service connected` | 服务已连接（重连也会打） |
| `snapshot[pkg] title=… n=… other:12 \| me:30` | 抓到快照：**只打 side 与长度，绝不打正文**（红线） |
| `overlay: canDrawOverlays=false` | 悬浮窗权限没给（MIUI/HyperOS 重装后常丢） |
| `overlay: toggle expanded=true x=… y=… saved=(…)` | 面板展开/收起与坐标 |
| `fill: setText readback=… want=…` / `fill: paste=… readback=…` | 填回三级兜底的实际结果 |
| `judge failed: …` | 判断请求异常，人话错误随后经 `showError` 上悬浮窗 |

排查树：

- **气泡不出现** → ① 无障碍是否启用（上面那条 settings 命令）② 悬浮窗权限 ③ 前台包名在不在 `adapters` ④ 是否真在聊天窗（`extract` 返回 null 就永远不显示，日志里连 `snapshot[...]` 都没有）。
- **微信读不到消息** → 伪装类名/包名是否被改（Manifest 的 `android:name`、`MainActivity.a11yComponent`、`@xml/config_disguised` 三处必须一致）；微信升级后失效是已知风险，先用真机复核 `id/bkl` 是否还在。
- **不自动分析** → 最新一条是否对方发的（`latestFrom == "other"`）、`auto_analyze` 是否开、白名单是否把标题滤掉、密钥是否已设（未设给「未设置 OpenCode 密钥」）。
- **接口报错对照（v1.4 三路配置后）** → 文案自带路由前缀（`判断接口 HTTP 401：…` / `回复接口 …`，来自 `HttpJson.ApiException`）；`400 MissingSessionID`=回复路地址是 opencode.ai 但没带 `x-opencode-session`（正常由 `Prefs.opencodeSession` 自动带上，只有地址域写错时才会丢）；`403 + error code 1010`=UA 被 Cloudflare 拦（只有 opencode.ai 主机注入自述 UA，别把 base 写成裸 IP）；`402`=Zen 余额不足（付费模型；免费档用 `jev-1.13-free`）；`403 FreeTierError`=免费 chat 模型只能在 OpenCode 客户端内用（Jev 走 `/zen/v1/systemone` 不受限）。
- **同内容不重复分析**是**特性**：气泡被 ROM 杀掉只会补窗（`showIdle`），不会重新烧 token。
- **服务被杀 / 气泡消失** → 前台保活 + 自启动 + 省电无限制三项都要；HyperOS 上仍可能被杀，重连后 900ms 补偿自动补窗，再交互一次即自愈。
- **填入失败** → 看 `fill:` 两行；微信有 IME 组合态时走剪贴板兜底，提示「已复制，长按输入框粘贴」。
- **判断答得不合理 / 题目互相矛盾** → 改 `tools/jev/questions.py` 措辞并用 `calibrate.py` 复测闸门，通过后**同步到 `JevQuestions.kt`**；禁止只改 Kotlin 一侧。

## 已知限制与后续方向

- **飞书正文**：自绘控件，无障碍树里没有文字；当前只能分析带 TextView 的内容。后续 = `takeScreenshot()` + 按气泡裁剪 + ML Kit 中文 OCR（路线 B，`takeScreenshot` 可用性已在 P1 验证）。
- **X 只按中文界面验证**：分隔符 `：`、`上午/下午`、`Read` 出自中文界面实测；英文界面仅做了兜底，未验。
- **群聊**：按一对一关系分析，「对方」与 `relationship` 对群聊不准。
- **密钥存储**：明文 SharedPreferences（App 私有），换 EncryptedSharedPreferences 是既定后续。
- **TypeSafe 直连预设未实测**：`api.typesafe.ai/v1/systemone`（上游内置预设之一）本机没有 `TYPESAFE_API_KEY`，未真机验证过 —— 首次连通测试即验收。
- **升级路径**：v1.2 → 上游 v1.3 有一次性迁移（`openrouter_key` → `judge_key`）；但**本 fork 中间那版 v1.3（键名 `opencode_key`）不在迁移范围** → 从它升到 v1.4 需要在设置里重填一次 key。v1.4 包已归档 `apk/jev-assistant-v1.4-release.apk`（本 fork 签名）；同目录 `apk/jev-assistant-v1.3-release.apk` 是**上游官方包**（作者签名，两者签名不同，换装要先卸载）。
- **本 fork 与上游的差异面**（下次合并上游时逐条对照，别弄丢）：minSdk 28（上游 30）；判断 `opencode-zen` 与回复 `OpenCode Go` 两个预设（`Prefs` 常量 + `SettingsActivity` 的 pills/`providerOf`/`resolveJudgeProvider`/`expandJudgeUrl`/`defaultJudge*`）；`HttpJson.headersFor` 的 UA + `x-opencode-session` 注入与 `Prefs.opencodeSession`；`tools/jev/` 的 provider 参数（zen/typesafe + `--provider`）；签名密钥在仓库外、`apk/` 里多一个 v1.4 包。
- **OpenCode Zen 免费档**：`jev-1.13-free` 限时免费、随时可能调整（被关时切 TypeSafe 直连预设或给 Zen 充值）；回复路走 Go 吃订阅额度（`glm-5.3-flash` 每月 $60 档）。
- **OCR 兜底在 Android 9 上不可用**：`AccessibilityService.takeScreenshot` 是 API 30+，本机测试机（MI 8 / SDK 28）跑不到 OCR 路径（编译能过、运行会失败）——要在 28 上用 OCR 得另找方案。
- **伪装服务**：微信一旦改混淆策略即失效 —— 本项目最大的外部依赖风险。
- **国产 ROM 保活**：前台服务 + 自启动 + 省电无限制仍可能被杀，接受「短暂消失、自愈」。
- **无自动化测试 / CI**：验证 = 构建零 error + 真机冒烟；改采集或填回后必须补真机证据。
- **文档漂移**：`docs/probe_spec.md` 描述的 P1 探针（实验组 + 对照组普通服务、`com.jev.probe.DUMP` 广播、节点 dump）已不在代码里，工程现在只有伪装服务这一个入口；该文件仅作历史依据。
- **扩展点**：桌面端 / 网页（换采集层，判断与回填逻辑复用）；`tools/jev/fixtures/labeled_set.json`（现 30 条）继续扩样提高校准置信度。
