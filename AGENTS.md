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

- 应用 ID `com.jev.probe`，根工程名 `jev-android`；Kotlin + 传统 View/XML（**不用 Compose**）；**minSdk 28**（2026-09-22 从 30 降下来：测试机是 MI 8 / Android 9，minSdk 30 的包装不上，MIUI 还会把它报成误导性的 `INSTALL_FAILED_USER_RESTRICTED`）/ compileSdk 35 / targetSdk 35；版本号在 `app/build.gradle.kts`（当前 versionCode 4 / 1.3）。
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

v1.3（versionCode 4）：**接入商改造完成** —— Jev 判断走 OpenCode Zen 免费档 `jev-1.13-free`（可切 TypeSafe 官方 `jev-latest`），候选起草走 OpenCode Go `glm-5.3-flash`；微信 8.0.78、手机 QQ 9.3.50、X 12.25.2 三线曾真机全链路通（改造后**未接真机**验证）；已编译并**安装到测试机**（MI 8 UD / Android 9，minSdk 28 的 debug 包，功能未验）；**v1.3 release 已签名并装机**（新建密钥在仓库外 `D:\work\keys\jev\`，证书 SHA-256 `f190d62d…`；归档 `apk/jev-assistant-v1.3-release.apk`；手机上已从 debug 换成 release 版）；飞书采集部分（正文待 OCR）。

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
   │     A) jev/JevClient.judge(snapshot, relationship)  → 1 次 decisions 调用出 7 题（~1s）→ overlay.showJudgment()
   │     B) jev/JevClient.draftAndRank(snapshot, relationship) → 生成模型起草 3 条 + 1 次 decisions 排序 → overlay.showReplies()
   │    判断先渲染，候选慢一步补上；analyzing 标志防重入
   ▼
overlay/OverlayController（TYPE_APPLICATION_OVERLAY：可拖气泡 + 半透明面板）
   │ ⑥ 面板内容：危险等级徽章（配色随分数）→ 对方真实意图 + 把握 → 「要什么 · 动作 · 可给实质」→ 3 张候选卡（Jev 排序 + 占比）
   ▼ 用户点「复制」或「填入」
capture/ChatCaptureService.fillInput(text)   （worker 线程，含 sleep 校验，勿挪主线程）
   │ ⑦ 三级兜底：ACTION_SET_TEXT → 150ms 后刷新读回逐字校验 → 失败则 ACTION_CLICK 聚焦重试 → 再失败剪贴板 + ACTION_PASTE（先清空防重复）
   ▼
[聊天 App 输入框已填好]  —— **到此为止，绝不点发送按钮**
```

网络侧（`jev/JevEndpoints.kt` 路由 + `jev/JevClient.kt` 发送；**无 SDK，HttpURLConnection + org.json**）：

```
Jev 判断 · 默认  POST https://opencode.ai/zen/v1/systemone        model=jev-1.13-free（免费）   Authorization: Bearer <opencode_key>
Jev 判断 · 备选  POST https://api.typesafe.ai/v1/systemone        model=jev-latest             Authorization: Bearer <typesafe_key>
候选起草         POST https://opencode.ai/zen/go/v1/chat/completions  model=glm-5.3-flash      + 头 x-opencode-session: <UUID，存 prefs>
                                                                   body={model,messages[],temperature} → choices[0].message.content → JSON 数组取 3 条
```

- 三个请求体形状：判断/排序都是 `{model, state, questions} → {model, answers, usage}`（两个后端一致）。
- **每个请求都要自述 `User-Agent`**（`jev-assistant-android/1.3`）：库默认 UA 打 opencode.ai 会被 **Cloudflare error code 1010** 拦（实测 403）。
- **Go 必须带 `x-opencode-session`**（实测缺 → `400 MissingSessionID`）；Zen 的判断端点**不需要**该头。
- Zen 的免费档只在 `/zen/v1/systemone` 这条 Jev 路可用；免费 **chat** 模型对非 OpenCode 客户端是关闭的（实测 403 `FreeTierError`）。

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
// capture/ChatAppAdapter.kt
interface ChatAppAdapter {
    val pkg: String                                                          // 前台包名，作查表键
    fun extract(root: AccessibilityNodeInfo, res: Resources): ChatSnapshot?   // 不在聊天窗 → null
}
// capture/ChatCaptureService.kt:37
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

object JevEndpoints {                                        // jev/JevEndpoints.kt：纯 Kotlin 路由层（有单测）
    fun systemOne(provider: String): Route                   // zen → /zen/v1/systemone；typesafe → api.typesafe.ai/v1/systemone
    fun replyChat(sessionId: String, model: String): Route   // OpenCode Go chat/completions + x-opencode-session 头
    fun jevModel(provider: String, configured: String): String   // 留空 → 按后端取默认（jev-1.13-free / jev-latest）
    fun keyFor(provider: String, opencodeKey: String, typesafeKey: String): String
}
class JevClient(cfg: ProviderConfig) {
    data class ProviderConfig(jevProvider, jevModel, replyModel, opencodeKey, typesafeKey, sessionId)
    companion object { fun fromPrefs(p: Prefs): JevClient }  // 服务与设置页共用，避免构造参数漂移
    fun judge(snapshot: ChatSnapshot, relationship: String): Analysis                     // 7 题，1 次 systemone
    fun draftAndRank(snapshot: ChatSnapshot, relationship: String): List<RankedReply>     // Go 起草 3 条 + 1 次 systemone 排序
    fun analyze(snapshot: ChatSnapshot, relationship: String): Analysis                   // 串行两段（设置页「连通测试」用）
}

object JevQuestions {
    fun judge(): JSONObject                                    // 7 题：literal_question / true_intent / danger_level(10 档) /
                                                               // should_reply_now / best_action / she_needs / tension_resolved
    fun buildState(snapshot: ChatSnapshot, relationship: String): JSONObject               // {"chat":{relationship,messages[{from,text}],latest_from}}
    fun rankQuestion(candidates: List<String>): JSONObject                                 // 恰好 3 条，key = reply_a / reply_b / reply_c
}
```

HTTP 行为：连接 15s / 读 25s；429、529 退避重试 3 次（500ms×2^attempt）；4xx 直接失败不重试；异常转人话（`401` 密钥无效或未设置、`402` Zen 余额不足、`403 FreeTierError` 只允许 OpenCode 客户端、`400 MissingSessionID` 缺头、`403 + error code 1010` 被 Cloudflare 拦 UA、超时→「网络超时」）。密钥只作为 `Authorization` 头出现，永不落日志；**每个请求必须自述 `User-Agent`，Go 请求另带 `x-opencode-session`**。

**配置项（`core/Prefs.kt`，SharedPreferences 名 `jev_assistant`）**

| 键 | 含义 |
|---|---|
| `opencode_key` | OpenCode API Key（`sk-` 开头）：Zen 的 Jev + Go 的起草共用；App 私有存储（**明文是当前基线**，待换 EncryptedSharedPreferences）；不进日志/仓库 |
| `typesafe_key` | TypeSafe 官方 key，仅 `jev_provider=typesafe` 时用 |
| `jev_provider` | `zen`（默认，免费 `jev-1.13-free`）或 `typesafe`（官方 `jev-latest`） |
| `jev_model` | Jev 模型，留空 = 按后端取默认 |
| `opencode_session` | Go 请求的 `x-opencode-session` 值：首次读取生成 UUID 并落盘（缺它 → 400） |
| `reply_model` | 起草候选的生成模型，默认 `glm-5.3-flash`（OpenCode Go 的 chat/completions 族；便宜、中文自然） |
| `relationship` | 关系描述，进 Jev state |
| `enabled` | 总开关（关掉则事件分支直接 hide） |
| `whitelist` | 会话白名单，**按标题包含匹配**；空 = 所有会话；标题为空且白名单非空 → 不生效 |
| `overlay_opacity` | 60–100，越低越透 |
| `bubble_x` / `bubble_y` | 气泡记忆位置（-1 = 默认） |
| `auto_analyze` | 对方新消息是否自动分析；关 = 只出气泡等手点 |

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
- **接口报错对照** → `402`=Zen 余额不足（付费 `jev-1.13` 需充值，或退回 `jev-1.13-free`）；`403 FreeTierError`=该模型只允许在 OpenCode 客户端内用（Jev 必须走 `/zen/v1/systemone`）；`400 MissingSessionID`=Go 请求缺 `x-opencode-session`；`403 + error code 1010`=Cloudflare 拦 UA（必须自述 UA）；`401`=key 错/未填。
- **同内容不重复分析**是**特性**：气泡被 ROM 杀掉只会补窗（`showIdle`），不会重新烧 token。
- **服务被杀 / 气泡消失** → 前台保活 + 自启动 + 省电无限制三项都要；HyperOS 上仍可能被杀，重连后 900ms 补偿自动补窗，再交互一次即自愈。
- **填入失败** → 看 `fill:` 两行；微信有 IME 组合态时走剪贴板兜底，提示「已复制，长按输入框粘贴」。
- **判断答得不合理 / 题目互相矛盾** → 改 `tools/jev/questions.py` 措辞并用 `calibrate.py` 复测闸门，通过后**同步到 `JevQuestions.kt`**；禁止只改 Kotlin 一侧。

## 已知限制与后续方向

- **飞书正文**：自绘控件，无障碍树里没有文字；当前只能分析带 TextView 的内容。后续 = `takeScreenshot()` + 按气泡裁剪 + ML Kit 中文 OCR（路线 B，`takeScreenshot` 可用性已在 P1 验证）。
- **X 只按中文界面验证**：分隔符 `：`、`上午/下午`、`Read` 出自中文界面实测；英文界面仅做了兜底，未验。
- **群聊**：按一对一关系分析，「对方」与 `relationship` 对群聊不准。
- **密钥存储**：明文 SharedPreferences（App 私有），换 EncryptedSharedPreferences 是既定后续。
- **接入商（2026-09-22 改造）**：Jev 走 OpenCode Zen 免费档 `jev-1.13-free`（属限时免费，随时可能调整或被客户端门收紧；被关时切 `typesafe` 官方或给 Zen 充值）；TypeSafe 官方端点 `api.typesafe.ai/v1/systemone` **未在本机实测**（无 key），首次真机连通测试即验收；OpenCode Go 的生成额度按模型计（`glm-5.3-flash` 每月 $60）。
- **v1.2 → v1.3 升级**：prefs 键 `openrouter_key` → `opencode_key`，未做迁移 —— 老用户需重填 key；v1.3 的 release 包未重打（`apk/` 里仍是 v1.2 / OpenRouter 版）。
- **伪装服务**：微信一旦改混淆策略即失效 —— 本项目最大的外部依赖风险。
- **国产 ROM 保活**：前台服务 + 自启动 + 省电无限制仍可能被杀，接受「短暂消失、自愈」。
- **无自动化测试 / CI**：验证 = 构建零 error + 真机冒烟；改采集或填回后必须补真机证据。
- **文档漂移**：`docs/probe_spec.md` 描述的 P1 探针（实验组 + 对照组普通服务、`com.jev.probe.DUMP` 广播、节点 dump）已不在代码里，工程现在只有伪装服务这一个入口；该文件仅作历史依据。
- **扩展点**：桌面端 / 网页（换采集层，判断与回填逻辑复用）；`tools/jev/fixtures/labeled_set.json`（现 30 条）继续扩样提高校准置信度。
