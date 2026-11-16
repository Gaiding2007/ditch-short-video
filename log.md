# 扔掉短视频 · 项目日志

> 仓库：<https://github.com/Gaiding2007/ditch-short-video>
> 包名：`com.gaiding.ditchshortvideo`
> 当前版本：`0.0.1`（versionCode `1`）
> 当前阶段：第一版，已构建并在 Android 16 模拟器完成基础验证；真实小米设备与真实短视频 App 页面规则仍待用户实测。

---

## 0. 项目需求与必要记忆

### 0.1 产品目标

做一个安卓 App，中文名 **扔掉短视频**，英文名和仓库名 **ditch-short-video**。它只做一件事：在用户真的进入短视频页面时计时，帮助用户看见并限制每天刷短视频的时间。

### 0.2 已确认的需求口径

1. **指定检测哪些软件**：第一版固定支持 5 个 App，用户可在首页开关中勾选要监控的 App：
   - 抖音 `com.ss.android.ugc.aweme`
   - 快手 `com.smile.gifmaker`
   - 微信视频号 `com.tencent.mm`（只统计视频号，不统计聊天/朋友圈）
   - 小红书 `com.xingin.xhs`（只统计带视频证据的笔记详情）
   - 哔哩哔哩 `tv.danmaku.bili`（只统计故事/竖屏播放页）
2. **严格页面识别**：只有识别到短视频页面才开始/继续计时；识别不到就不计时。宁可漏计，不误计聊天、搜索、个人主页、评论页。
3. **暂停逻辑**：离开短视频页面、切换到别的 App、锁屏、熄屏、无障碍服务断开，立即暂停；回到短视频页面后重新开始累计。连续识别 2 秒才确认，避免滑动/过场误触。
4. **每日总时长可自定义**：5 个 App 合并计算，默认 30 分钟，可设 1–720 分钟；每天本地时间 0 点重置。
5. **数据只存本地**：使用 SharedPreferences 保存每日秒数，key 为 `d_yyyy-MM-dd`，保留 400 天；无账号、无云同步、无网络权限。
6. **超时提醒**：到额度当天只提醒一次，不强制锁机、不关 App、不拦截。提醒标题与正文可自定义，支持 `{used}` 和 `{limit}` 占位符。
7. **小米超级岛**：优先走 Android 16 的 Promoted Ongoing（`POST_PROMOTED_NOTIFICATIONS` + `requestPromotedOngoing`），系统支持时上岛，不支持时自然回退普通通知；`miui.focus.param` 作为实验开关预埋，默认关闭。侧载应用无法保证拿到小米官方焦点通知权限。
8. **类 GitHub 小绿点**：首页展示全年 7 行 × 周的热力图，按当天刷视频时长/额度分 5 档颜色，今天有描边，未来日期淡灰。
9. **版本与仓库**：第一版版本号 `0.0.1`，每次改动写本日志，每个版本通过 git 提交并推送 GitHub。

### 0.3 第一版明确不做

- 5 个 App 之外的任意 App
- 用户自定义页面规则/正则
- 强制拦截、锁机、关闭目标 App、悬浮窗强提醒
- 重复提醒、定时提醒、每日多次提醒
- 云同步、账号、多设备
- 视频内容识别（不判断具体视频内容）
- 分屏/小窗的精确页面归属
- 详细统计图表、数据导出
- 小米以外品牌的“灵动岛”适配
- 应用商店上架与正式签名

### 0.4 技术与环境基线

- 纯 Kotlin + Android 平台 View/API，**不依赖 AndroidX/Compose**，降低离线构建和依赖风险。
- `minSdk 26`，`compileSdk 36`，`targetSdk 36`，Java 17 target。
- AGP `8.13.1`，Kotlin `2.2.20`，Gradle `8.14`。
- 构建必须使用工作区内的 Gradle 缓存（沙箱环境 `C:\Users\14127\.gradle` 不可写）：
  ```powershell
  $env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-home'
  & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat" :app:assembleDebug `
      --console=plain --offline --no-daemon `
      -Pandroid.aapt2FromMavenOverride="D:\applications\SDK\build-tools\36.1.0\aapt2.exe"
  ```
- 项目内 debug 签名：`keystore/debug.keystore`，alias `androiddebugkey`，store/key 密码均为 `android`。这是公开的调试证书，不用于正式发布。
- 仓库带 Gradle Wrapper（Gradle `8.14`，bin 分发）；`gradle.properties` 关闭 vfs watch、Kotlin 采用 in-process 编译。当前沙箱的离线缓存缺少 AGP 的 aapt2，因此本机构建时额外传 `-Pandroid.aapt2FromMavenOverride=D:\applications\SDK\build-tools\36.1.0\aapt2.exe`；普通联网环境无需该参数。
- `settings.gradle.kts` 用 `resolutionStrategy` 将插件坐标指向本地缓存中的 AGP/Kotlin 构件。
- 本地没有 JUnit 缓存且外网 Maven 不可达，因此不写 `src/test` 单测；页面规则自检放在 App 内“诊断”页运行，共 13 个纯逻辑用例。
- APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

### 0.5 代码地图

```
app/src/main/java/com/gaiding/ditchshortvideo/
├── DitchApp.kt                     Application，创建通知渠道
├── MainActivity.kt                 首页：进度环、准备步骤、App 开关、年度热力图
├── SettingsActivity.kt             额度、提醒文案、权限、计时规则、清空数据、关于
├── DiagnosticsActivity.kt          运行状态、13 项页面规则自检、超级岛诊断
├── data/
│   ├── MonitoredApps.kt            5 个 App 元数据
│   ├── DayUtils.kt                 日期/时长/文案格式化
│   ├── Prefs.kt                    设置与提醒文案
│   └── UsageStore.kt               每日秒数、400 天清理
├── monitor/
│   ├── ShortVideoAccessibilityService.kt  1 秒 tick、页面事件、暂停、提醒触发
│   ├── ShortVideoDetector.kt       严格页面识别规则
│   ├── SnapshotBuilder.kt          无障碍节点树抓取
│   ├── PageSnapshot.kt             节点/页面/识别结果模型
│   ├── MonitorRuntime.kt           运行时状态供 UI 读取
│   └── DetectorSelfTest.kt         13 个纯逻辑用例
├── notify/
│   ├── ReminderNotifier.kt         通知渠道、超时提醒、测试提醒、上岛 extras
│   ├── ReminderActionReceiver.kt   通知“关闭提醒”动作
│   └── XiaomiIsland.kt             小米超级岛诊断与实验参数
├── system/SystemStatus.kt          权限/后台状态与跳转
└── ui/                             平台 View 工具、进度环、热力图
```

---

## 1. 版本记录

### 0.0.1 — 2026-11-16

第一版可运行工程，完成从空仓库到 APK 的完整链路。

#### 功能

- 建立 Android 工程、启动页、设置页、诊断页、通知渠道、无障碍服务。
- 实现 5 个 App 的严格页面识别：
  - 抖音：`MainActivity` + 至少 3 类互动栏标签（点赞/评论/分享/收藏）。
  - 快手：`HomeActivity` + 至少 2 类互动栏 + 顶部关注/发现/精选/推荐。
  - 微信视频号：`FinderHomeAffinityUI`（或 Finder+Home），排除个人页/搜索/直播/评论/朋友圈/聊天。
  - 小红书：`NoteDetailActivity` + 视频 Surface/VideoView/Texture/Player + 进度/时长/播放控件，排除评论页。
  - B 站：`StoryVideoActivity` / `StoryTransparentActivity`，排除普通详情页、直播、首页。
  - 任意页面 Activity 名含 search 或输入框聚焦时否决。
- 实现 1 秒 tick、连续 2 次正向确认、离开页面/切 App/锁屏/熄屏/服务断开立即暂停；每次最多累计 3 秒，跨日重置。
- 实现每日额度、上限 1–720 分钟、当天只提醒一次；提醒成功 post 后才写 `last_reminder_date`，失败 60 秒后重试。
- 通知支持标题/正文自定义与 `{used}`/`{limit}` 占位符；ongoing、BigText、“关闭提醒” action；Android 16 上设置 `setShortCriticalText("额度已用完")` 和 `android.requestPromotedOngoing=true`，系统支持时上岛/提升，否则普通通知。
- 小米焦点通知 `miui.focus.param` 构造代码预埋，诊断页可开实验开关；默认关闭，避免侧载无权限时产生不可预期行为。
- 首页环形进度、今日/剩余、每日上限调整、三步准备卡、5 App 开关、全年热力图、隐私 footer；首次进入三件事引导。
- 设置页：额度、提醒开关/文案、测试提醒、权限与后台、计时规则、清空数据、诊断入口、GitHub 链接、隐私说明。
- 诊断页：服务/计时/前台/Activity/原因/识别/连续确认/会话/屏幕/锁屏/更新时间、13 项页面规则自检、超级岛诊断与实验开关、设备信息、复制诊断。
- 纯平台自定义 View：`ProgressRingView`、`HeatmapView`；无 AndroidX 依赖。

#### 修复与验证

- 修复 `Ui.edgeToEdge` 在 `onCreate` 阶段访问 `Window.getInsetsController()` 导致 `DecorView` 为空、启动崩溃的问题：先访问 `window.decorView` 再取 `windowInsetsController`。
- 修复 `DiagnosticsActivity` 对可空类型误用 `lateinit`、`Ui` 中把 `setBackgroundResource` 写成属性赋值的编译错误。
- 修复 `aapt2` 离线缓存缺失：`android.aapt2FromMavenOverride` 指向 SDK build-tools。
- 修复 `~/.android` 不可写导致 debug keystore 生成失败：项目内生成 `keystore/debug.keystore` 并配置 `debugLocal` 签名。
- 修复首次启动引导在 Activity 尚未 resume 时弹窗的风险：改为 `window.decorView.post { ... }`。

#### 模拟器验证记录

环境：Android 16（API 36）Google APIs x86_64 模拟器，AVD `DitchTest`，`-no-window -gpu swiftshader_indirect -grpc 0`，项目内 `GRADLE_USER_HOME` 离线构建。

- `:app:assembleDebug` 成功，APK 正常安装、冷启动成功，首页/设置/诊断页可正常渲染。
- 无障碍服务绑定成功；首页状态从“未连接”变为“已暂停/等待打开短视频 App”。
- 诊断页“运行 13 个用例”结果 **通过 13/13**。
- 用测试包 `com.ss.android.ugc.aweme`（仅本地测试，不提交仓库）模拟抖音 `MainActivity` 与点赞/评论/分享节点：
  - 进入页面约 7 秒后，`usage_records.xml` 记录 `d_2026-11-16=7`。
  - 按 Home 离开后等待 7 秒，秒数保持 `11` 不变，确认暂停。
  - 再回到测试页面，秒数增长到 `15`，确认继续计时。
- 测试提醒发送成功；`dumpsys notification` 确认 flags 含 `ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`，extras 含 `android.requestPromotedOngoing=true` 与 `android.shortCriticalText=额度已用完`；通知“关闭提醒” action 可取消通知。
- 通过临时把额度设为 1 分钟、使用量设为 65 秒，再进入测试页面，成功触发真实超时通知 ID `1001`，标题/正文为自定义内容，`last_reminder_date` 写入当天日期，验证“当天只提醒一次”的持久化状态。
- 将暂停原因文案统一为“等待打开短视频 App”（替代偏技术的“当前 App 不在监控范围”），重新构建成功；干净安装（`pm clear`）后重新授权通知/无障碍/电池白名单，首页三项准备全部变绿，进入测试页面 6 秒后 `d_2026-11-16=6`，诊断页自检再次 **13/13**，最终首页/诊断截图已更新。

#### 已知限制

- 真实抖音/快手/微信视频号/小红书/B 站页面规则**尚未在真机逐页验证**；这些 App 改版后规则可能失效，需要根据诊断页的实际 Activity/节点补充规则。
- 第一版只支持 5 个 App；分屏/小窗场景不保证精确归属；停在短视频页面即计时，不判断视频是否暂停或评论面板是否展开。
- 小米超级岛依赖系统能力；侧载包通常无法获得官方焦点通知权限，`miui.focus.param` 可能被整包忽略，AOSP promoted ongoing 也可能只显示图标+短文本或普通通知。需要在小米 15 Pro / HyperOS 4 真机上确认实际表现。
- 用 Android 设置“强行停止”App 会同时停掉无障碍服务，这是系统行为；重新打开 App 后需要在系统设置里重新开启页面识别。
- 设置页预览在当天使用量不足 1 分钟时显示“已经看了 1 分钟”，这是为了预览占位符效果而取的最小值；真实提醒只会在达到额度后发送。
- 使用调试签名，不适合直接发布到应用商店；正式版需要独立 release keystore、隐私政策和商店资料。

#### 下一步（0.0.2 候选）

1. 在小米 15 Pro 真机上逐页验证 5 个 App，收集诊断页的 Activity 名与节点特征，补充/收紧规则。
2. 根据真机表现调整超级岛参数与通知文案长度；确认是否需要引导用户开启“焦点通知”权限。
3. 评估增加更多 App、可配置页面规则、每日/每周统计、提醒方式扩展。
4. 处理用户实测反馈的误计/漏计、耗电、后台保活问题。

---

## 2. 变更日志约定

- 每次功能、修复、构建配置、版本号变更，都在本文件追加一条记录。
- 记录至少包含：日期、版本、改了什么、为什么改、如何验证、已知风险。
- 版本号遵循 `0.0.x` 递增；每次版本完成后提交并推送 GitHub。
- 需求口径变化必须先更新第 0 节，再改代码。
