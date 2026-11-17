# 扔掉短视频 · 项目日志

> 仓库：<https://github.com/Gaiding2007/ditch-short-video>
> 包名：`com.gaiding.ditchshortvideo`
> 当前版本：`0.0.4`（versionCode `4`）
> 当前阶段：0.0.4 修复 B 站冷启动首次识别、版本号显示改为 BuildConfig；待真机复测。

---

## 0. 项目需求与必要记忆

### 0.1 产品目标

做一个安卓 App，中文名 **扔掉短视频**，英文名和仓库名 **ditch-short-video**。它只做一件事：在用户真的进入短视频页面时计时，帮助用户看见并限制每天刷短视频的时间。

### 0.2 已确认的需求口径

1. **指定检测哪些软件**：第一版固定支持 5 个 App，用户可在首页开关中勾选要监控的 App：
   - 抖音 `com.ss.android.ugc.aweme`
   - 快手 `com.smile.gifmaker`
   - 微信视频号 `com.tencent.mm`（只统计视频号，不统计聊天/朋友圈）
   - 小红书 `com.xingin.xhs`（只统计有视频画面/播放证据的笔记或视频流，图文不计）
   - 哔哩哔哩 `tv.danmaku.bili`（只统计故事/竖屏播放页）
2. **严格页面识别**：只有识别到短视频页面才开始/继续计时；识别不到就不计时。宁可漏计，不误计聊天、搜索、个人主页；评论区短暂查看按宽限处理。
3. **暂停逻辑**：离开短视频页面、切换到别的 App、锁屏、熄屏、无障碍服务断开，立即暂停；回到短视频页面后重新开始累计。连续识别 2 秒才确认，避免滑动/过场误触。评论区等临时页面最多继续计 30 秒，其他页面切换最多宽限 2 秒。
4. **每日总时长可自定义**：5 个 App 合并计算，默认 30 分钟，可设 1–1440 分钟；每天本地时间 0 点重置。
5. **数据只存本地**：使用 SharedPreferences 保存每日秒数，key 为 `d_yyyy-MM-dd`，保留 400 天；无账号、无云同步、无网络权限。
6. **超时提醒**：到额度当天只提醒一次，不强制锁机、不关 App、不拦截。提醒标题与正文可自定义，支持 `{used}` 和 `{limit}` 占位符。
7. **计时中的常驻倒计时**：识别到短视频页面并开始计时时，发一条低优先级、ongoing 的倒计时通知；下拉通知栏时不立即消失，暂停后保留 2 秒再收起。
8. **小米超级岛**：优先走 Android 16 的 Promoted Ongoing（`POST_PROMOTED_NOTIFICATIONS` + `requestPromotedOngoing`），系统支持时上岛，不支持时自然回退普通通知；`miui.focus.param` 作为实验开关预埋，默认关闭。侧载应用无法保证拿到小米官方焦点通知权限。
9. **类 GitHub 小绿点**：首页展示全年 7 行 × 周的热力图，按当天刷视频时长/额度分 5 档颜色，今天有描边，未来日期淡灰。
10. **版本与仓库**：当前版本 `0.0.4`，每次改动写本日志，每个版本通过 git 提交并推送 GitHub；APK 同时上传 GitHub Release。

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
- 本地没有 JUnit 缓存且外网 Maven 不可达，因此不写 `src/test` 单测；页面规则自检放在 App 内“诊断”页运行，共 22 个纯逻辑用例。
- APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

### 0.5 代码地图

```
app/src/main/java/com/gaiding/ditchshortvideo/
├── DitchApp.kt                     Application，创建通知渠道
├── MainActivity.kt                 首页：进度环、准备步骤、App 开关、年度热力图
├── SettingsActivity.kt             额度、提醒文案、权限、计时规则、清空数据、关于
├── DiagnosticsActivity.kt          运行状态、22 项页面规则自检、超级岛诊断
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
│   └── DetectorSelfTest.kt         22 个纯逻辑用例
├── notify/
│   ├── ReminderNotifier.kt         通知渠道、超时提醒、测试提醒、上岛 extras
│   ├── ReminderActionReceiver.kt   通知“关闭提醒”动作
│   └── XiaomiIsland.kt             小米超级岛诊断与实验参数
├── system/SystemStatus.kt          权限/后台状态与跳转
└── ui/                             平台 View 工具、进度环、热力图
```

---

## 1. 版本记录

### 0.0.4 — 2026-11-17

针对真机复测的 B 站冷启动问题，以及“版本号仍显示 v0.0.1”的显示缺陷。

#### 用户反馈

- B 站：直接打开视频仍不计时；必须先点开一次评论区再关掉，才开始计时。
- 软件内版本号仍显示 `v0.0.1`。
- 询问 APK 签名是怎么做的。
- 下一版版本号 `0.0.4`。

#### 功能与修复

- **B 站冷启动修复**：
  - 去掉 `bilibili()` 对 `MainActivityV2` 的硬排除；故事页只要出现 `tv.danmaku.bili:id/story_title`（或 Story 类 Activity/类名线索）并有视频画面，就按故事模式计时，不再等 Activity 名稳定。普通 `VideoDetailsActivity`、`UnitedBizDetailsActivity`、直播仍排除。
  - 新增 `ShortVideoDetector.needsActivityFallback(pkg)`，小红书/B 站允许 Activity 暂时为空或残留。
  - 新增 `pageRetryUntil`：进入/切换页面后的 8 秒内，如果识别尚未成功，每秒主动重抓一次节点树。解决“首次打开时无障碍事件早于可读节点树到达，必须开评论区触发下一次事件”的根因。
  - 非 Activity 的窗口事件对小红书/B 站会清掉过期 Activity，避免 `MainActivityV2` 把真实故事页挡掉。
- **版本号显示修复**：首页 footer、设置页 footer、诊断页复制文本/标题共 4 处硬编码 `v0.0.1` 改为 `BuildConfig.VERSION_NAME`；`versionCode 4`、`versionName 0.0.4`。
- 自检新增“B 站故事页 Activity 残留主界面也应靠标题识别”，共 **22** 个用例。

#### 签名说明

- 工程使用 `keystore/debug.keystore`，alias `androiddebugkey`，store/key 密码均为 `android`；Gradle 的 `debugLocal` signingConfig 指向它。所有 APK 都用这张调试证书签名。
- 证书 SHA-256：`7A:4F:D0:FC:F7:60:75:03:B7:2D:B4:5E:82:5A:03:A9:0D:0A:78:10:91:E2:A4:59:8B:71:DF:CD:B8:CA:4A:38`；SHA-1：`35:90:22:CB:14:37:F6:B8:8E:36:FE:10:EA:42:FE:60:ED:70:7D:D3`。
- 调试证书只适合侧载和自用，不适合应用商店；正式发布需要新建 release keystore、备份密码，并确保后续升级使用同一证书。

#### 验证

- `:app:assembleDebug` 成功（约 23s，37 tasks），APK `app/build/outputs/apk/debug/app-debug.apk`，1,006,474 字节；`versionCode 4`、`versionName 0.0.4`。
- 模拟器安装并冷启动成功；无障碍、通知、电池白名单三项全绿。
- 诊断页“运行 22 个用例”结果 **22/22**。
- 启动本地伪抖音测试包后倒计时通知 ID `1000` 正常，usage 从 0 增长到 7 秒，确认计时链路没有回归。
- 首页 footer 与设置页 footer 均显示 `v0.0.4`。

#### 已知限制

- B 站真实页面的 Activity/节点仍未逐页采集；本次按“故事标题 + 视频画面 + 冷启动重试”修复，若 B 站改版仍可能失效。需要用户在真机复测，失败时从诊断页复制 Activity/原因。
- 为避免漏计，B 站/小红书在页面切换后的 8 秒内会每秒抓一次节点树，短时耗电会略增；识别成功后即停止重试。
- 评论区 30 秒宽限、后台无障碍被系统杀死、正式签名/上架仍未解决。

#### 0.0.4 仍暂不做

- 正式 release 签名、应用商店上架、隐私政策。
- 前台保活服务、无障碍断开自动恢复。
- 更多 App、自定义页面规则、详细统计、云同步。
- 评论区“长时间停留自动暂停”的复杂策略。

#### 推荐下一步

1. 真机复测 B 站“首次打开视频”和“退出再进”两条路径；若仍漏计，记录诊断页的 Activity 与识别原因。
2. 评估轻量前台服务/无障碍断开通知，作为 0.0.5 的后台可靠性方案。
3. 确定正式签名方案：生成 release keystore，离线备份，不再使用 debug 证书发版。

### 0.0.3 — 2026-11-17

针对用户真机复测的定向修复版：解决 B 站第一次进视频不计时、小红书短视频流识别不到，并把评论区宽限和页面切换后的倒计时收起时间做成可解释的两档。

#### 用户反馈

- B 站：直接看视频不计时；先打开一次评论区再关掉，才开始计时。
- 小红书：仍然识别不到；用户指出小红书的短视频不一定是“笔记详情页”。
- 其余三个 App 正常；30 秒评论区宽限可以接受。
- 关闭短视频后约 8 秒才停止计时，希望缩短。
- 下一版版本号 0.0.3。

#### 功能与修复

- **B 站结构兜底**：不再只依赖 `Activity` 名。只要页面有 `tv.danmaku.bili:id/story_title`（或 Story 类 Activity/类名线索）且有视频画面，就按故事/竖屏视频计时；没有故事标题的普通视频详情（即使有画面和点赞/评论/分享）不计时。这样第一次进入故事页、Activity 还没稳定时也能识别。
- **小红书识别放宽**：不再强制 `NoteDetailActivity`。接受 `NoteDetail`、`Video`、`Player`、`Feed`、`IndexActivityV2` 或 Activity 暂时为空；必须有视频画面/播放进度等视频证据，再结合互动栏。图文信息流、评论、搜索、登录、聊天、个人页仍排除。
- **评论区宽限**：新增 `ShortVideoDetector.isCommentLike(page)`，识别“全部评论/评论区/说点什么/发送评论/条评论/回复”等评论页特征。评论区等临时页面最多继续计 **30 秒**；普通页面切换（如关闭视频回到主页/个人页）只宽限 **2 秒**，避免把离开短视频后的一段时间继续算进去。
- **倒计时收起加快**：`COUNTDOWN_LINGER_MS` 从 `8_000` 改为 `2_000`；暂停后约 2 秒取消常驻倒计时。下拉通知栏时仍会短暂保留，不会瞬间消失。
- 设置页计时规则同步为“评论区等临时页面最多继续计 30 秒”，不再写“评论页都不计时”。
- 自检扩展到 **21 个用例**：新增小红书视频流/图文信息流、B 站故事页 Activity 缺失、B 站普通视频缺故事标题等场景；诊断页按钮改为动态读取用例数。

#### 验证

- `:app:assembleDebug` 成功（约 23s，37 tasks），APK `app/build/outputs/apk/debug/app-debug.apk`，974,602 字节；`versionCode 3`、`versionName 0.0.3`。
- 模拟器安装并冷启动成功；无障碍、通知、电池白名单三项全绿。
- 诊断页“运行 21 个用例”结果 **21/21**，所有纯逻辑用例符合预期。
- 启动本地伪抖音测试包后倒计时通知 ID `1000` 正常 post；按 Home 离开后 1 秒仍在，约 5 秒时已消失，符合“2 秒 linger + 1 秒 tick”的预期；usage 停止增长。
- 首页、设置、诊断页渲染正常，无新崩溃。

#### 已知限制

- B 站/小红书的新规则仍基于结构特征，真实 App 改版后可能再次失效；需要用户用诊断页反馈实际 Activity/识别原因。
- 评论区宽限依赖评论页文本/Activity 特征；少数机型或版本可能识别不到，仍会按普通 2 秒宽限暂停。
- 真实小米 15 Pro 尚未复测 0.0.3；“删后台停无障碍”仍未解决。
- 仍使用调试签名；Release APK 仅适合侧载。

#### 0.0.3 仍暂不做

- 无障碍被系统杀死后的自动恢复/前台保活服务（继续评估，不在本版仓促加入）。
- 分屏/小窗精确识别、更多 App、自定义页面规则、云同步、正式签名/商店上架。
- 评论区“长时间停留自动暂停”的更复杂策略；本版只做 30 秒宽限。

#### 推荐下一步

1. 用户真机复测 B 站和抖音/小红书，重点看“第一次进视频是否立即计时”“评论区停留 30 秒后是否暂停”。
2. 如果 B 站/小红书仍有漏计，用诊断页复制实际 Activity + 识别原因，再做一轮规则校准。
3. 评估 0.0.4 的前台保活/无障碍断开提示，但先确认用户是否愿意接受常驻通知。

### 0.0.2 — 2026-11-17

用户实测反馈后的修正版：放宽识别、修复评论弹层导致停表、增加计时中的常驻倒计时，并把每日上限提高到 24 小时。

#### 功能与修复

- 每日上限从 `1–720` 分钟改为 `1–1440` 分钟；`Prefs.MAX_LIMIT`、首页/Settings 提示与输入校验同步；`versionCode 2`、`versionName 0.0.2`。
- 修复评论区/弹层导致停表：新增 `looksLikeActivity(cls, pkg)`，只有真正的 Activity 变化才重置页面；`FrameLayout`、`PopupWindow`、`Dialog`、`Fragment` 等窗口事件不再清空底层 Activity。
- 抖音：`MainActivity` 下允许“互动栏 ≥2 项”或“视频画面 + 互动/推荐标签”计时，仍排除搜索、详情、朋友、个人页、直播。
- 快手：`HomeActivity` 下“顶部关注/发现/精选/推荐 + 互动 ≥2 或视频画面”计时；修复刷feed时被 `FrameLayout` 事件打断。
- 小红书：`NoteDetailActivity` 下允许“视频画面、SeekBar、时长文本、播放/暂停标签”之一作为视频证据，再结合互动；不再强制要求无障碍树里出现 SurfaceView。
- 新增计时中的常驻倒计时通知：`countdown` 渠道（LOW）、ID `1000`、`CATEGORY_STOPWATCH`、ongoing/onlyAlertOnce、`setWhen` + `setUsesChronometer(true)` + `setChronometerCountDown(true)`；标题“计时中 · 还能看 N 分钟”，正文“离开短视频页面会自动暂停。”；额度用完时改为“今天额度已用完”。
- 倒计时暂停后保留 `COUNTDOWN_LINGER_MS=8_000L` 再取消，避免下拉通知栏（SystemUI 成为前台）时通知瞬间消失；`onInterrupt/onDestroy` 立即取消。
- 设置页预览在使用量为 0 时改用 `limit` 作为示例，不再显示“已经看了 1 分钟”。
- 自检新增 4 个场景（抖音评论区、快手画面兜底、小红书无进度条、小红书无画面但有视频进度），共 **17** 个用例；诊断页按钮与文案同步。

#### 验证

- `:app:assembleDebug` 成功（约 20s，37 tasks），APK `app/build/outputs/apk/debug/app-debug.apk`，1,011,884 字节。
- 模拟器安装/冷启动/设置/诊断页正常；无障碍、通知、电池白名单三项全绿。
- 诊断页“运行 17 个用例”结果 **17/17**。
- 启动本地伪抖音测试包后，倒计时通知 ID `1000` 正常 post；`dumpsys notification` 确认 channel `short_video_countdown_v1`、importance 2、flags `ONGOING_EVENT|ONLY_ALERT_ONCE`、`android.chronometerCountDown=true`。
- 下拉通知栏可看到“计时中 · 还能看 30 分钟 / 离开短视频页面会自动暂停。”，停留超过 8 秒后自动收起；离开短视频 App 后倒计时取消。
- 真实超时提醒（ID `1001`）与 `PROMOTED_ONGOING` 路径沿用 0.0.1 验证结果。

#### 已知限制

- 真实抖音/快手/小红书仍未在小米 15 Pro 上逐页确认；本次放宽了规则，需要用户用诊断页反馈实际 Activity/识别原因，再继续收紧/补充。
- 评论区仍是“按页面证据计时”：评论弹层不再清空 Activity，但如果视频画面/互动栏完全被遮住，识别会暂停；这是待讨论的行为，不在本版强行改变。
- 删后台/强行停止仍可能让 MIUI 停掉无障碍服务；本版只保留电池白名单引导，未加入前台保活服务。
- 倒计时通知属于“静默”通知（LOW 渠道），不会响铃/震动；如需更醒目要另做提醒方式。
- 仍使用调试签名；Release APK 也使用该证书，仅适合侧载。

#### 0.0.2 仍暂不做

- 评论区“继续计时/暂停”的最终策略（先讨论再定）。
- 无障碍被系统杀死后的自动恢复、前台保活服务。
- 分屏/小窗精确识别、更多 App、自定义页面规则、云同步、正式签名/商店上架。

#### 推荐下一步

1. 用真机诊断页收集 5 个 App 的实际 Activity/节点，做一轮规则校准。
2. 讨论并实现评论区策略（建议“短暂查看继续计时，长时间停留暂停”）。
3. 评估轻量前台保活服务 + 无障碍断开提示，作为 0.0.3 的后台可靠性改进。

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
