# 扔掉短视频 · 项目日志

> 仓库：<https://github.com/Gaiding2007/ditch-short-video>
> 包名：`com.gaiding.ditchshortvideo`
> 当前版本：`0.2.4`（versionCode `24`）
> 当前阶段：0.2.4 精简版；移除常驻“页面识别守护中”通知与设置页额度卡，备份/导入迁到设置，首页只保留右上角“数据”入口。

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
3. **暂停逻辑**：离开短视频页面、切换到别的 App、锁屏、熄屏、无障碍服务断开，暂停计时；回到短视频页面后重新开始累计。连续识别 2 秒才确认，避免滑动/过场误触。评论区等临时页面最多继续计 30 秒；普通页面抖动/短暂切换保留 3 秒，避免 ColorOS 的 SystemUI、输入法覆盖窗口造成频繁停表。
4. **每日总时长可自定义**：5 个 App 合并计算，默认 30 分钟，可设 1–1440 分钟；每天本地时间 0 点重置。
5. **数据只存本地**：使用 SharedPreferences 保存每日秒数，key 为 `d_yyyy-MM-dd`，保留 400 天；无账号、无云同步、无网络权限。
6. **超时提醒**：到额度当天只提醒一次，不强制锁机、不关 App、不拦截。提醒标题与正文可自定义，支持 `{used}` 和 `{limit}` 占位符。
7. **计时中的常驻倒计时**：识别到短视频页面并开始计时时，发一条低优先级、ongoing 的倒计时通知；下拉通知栏时不立即消失，暂停后保留 3 秒再收起。
8. **小米超级岛**：优先走 Android 16 的 Promoted Ongoing（`POST_PROMOTED_NOTIFICATIONS` + `requestPromotedOngoing`），系统支持时上岛，不支持时自然回退普通通知。0.2.2 起不再在设置/诊断里暴露测试开关，通知由系统自动处理。
9. **类 GitHub 小绿点**：数据页展示全年 7 行 × 周的热力图，按当天刷视频时长/额度分 5 档颜色，今天有描边，未来日期淡灰。
10. **无障碍断开提醒**：页面识别服务被系统中断或销毁时，发一条可点击的通知，点按直接跳到系统无障碍设置；重新连接后自动取消。
11. **版本与仓库**：当前版本 `0.2.4`，每次改动写本日志，每个版本通过 git 提交并推送 GitHub；APK 同时上传 GitHub Release。
12. **独立数据页**：数据从首页拆出，包含最近 7 天摘要、每日曲线、每日明细和年度热力图；首页右上角提供“数据”入口，不必向下滚动。
13. **每日曲线与控制计划**：每日曲线用平滑曲线标出过去每天的具体时长，点上显示分钟标签；触摸点会弹出小标签，显示具体日期、实际时长和计划额度。用户选择一个希望控制到的目标时长（15–120 分钟），积累至少 3 天记录后，生成 14 天从当前水平滑向目标、带轻微噪声的计划曲线，并自动把每天额度设为计划值。前 3 天先按现有水平，之后逐步下降/上升。目标档位可直接点选；启用后可按新目标更新或关闭。
14. **APK 命名**：构建后自动复制一份带版本号的 APK，例如 `ditch-short-video-0.2.4.apk` / `ditch-short-video-0.2.4-debug.apk`；GitHub Release 也使用该名字。
15. **分享图与本地备份**：数据页可把最近 30 天曲线渲染成 PNG 并调用系统分享面板；设置页可把设置、监控 App、计划状态和每日记录导出为 JSON，或从 JSON 导入恢复（导入覆盖当前数据）。文件由系统文件选择器选择，不上传。
16. **分享预览**：0.2.3 起点“分享图”先生成图片并弹出预览，确认后才调用系统分享面板；取消时释放位图。
17. **后台守护（尽力而为）**：0.2.4 起不再使用前台服务/常驻通知，只保留 15 分钟 AlarmManager watchdog、断开提醒和 ColorOS 保活引导。Android 不允许普通 App 自行重新启用无障碍，被强行停止后仍需用户点一次，不能承诺 100% 不被杀；没有 FGS 后，后台存活能力会比 0.2.3 弱。

### 0.3 第一版明确不做

- 5 个 App 之外的任意 App
- 用户自定义页面规则/正则
- 强制拦截、锁机、关闭目标 App、悬浮窗强提醒
- 重复提醒、定时提醒、每日多次提醒
- 云同步、账号、多设备
- 视频内容识别（不判断具体视频内容）
- 分屏/小窗的精确页面归属
- 云同步、账号、多设备（本地 JSON 导出/导入已在 0.2.2 加入）
- 小米以外品牌的“灵动岛”适配
- 应用商店上架与商店资料；0.1.0 起已切换到 release 签名用于内测，但仍不上架。

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
- 发布签名：`keystore/ditch-release.jks`，alias `ditch-release`，RSA 4096，有效期 10950 天；证书 SHA-256 `8D:58:21:E8:F5:F9:B3:03:E5:6A:98:0E:85:2A:85:B4:EE:F2:32:66:16:57:C6:51:39:64:C5:93:F6:F9:B5:0D`。`keystore/release.properties` 和 `.secrets/release-keystore.txt` 均被 `.gitignore` 排除，必须离线备份。
- 调试签名：`keystore/debug.keystore`，alias `androiddebugkey`，store/key 密码均为 `android`。这是公开的调试证书，只用于本地调试，不对外分发。
- 仓库带 Gradle Wrapper（Gradle `8.14`，bin 分发）；`gradle.properties` 关闭 vfs watch、Kotlin 采用 in-process 编译。当前沙箱的离线缓存缺少 AGP 的 aapt2，因此本机构建时额外传 `-Pandroid.aapt2FromMavenOverride=D:\applications\SDK\build-tools\36.1.0\aapt2.exe`；普通联网环境无需该参数。
- `settings.gradle.kts` 用 `resolutionStrategy` 将插件坐标指向本地缓存中的 AGP/Kotlin 构件。
- 本地没有 JUnit 缓存且外网 Maven 不可达，因此不写 `src/test` 单测；页面规则自检放在 App 内“诊断”页运行，共 28 个纯逻辑用例。
- APK 输出：debug 为 `app/build/outputs/apk/debug/app-debug.apk`，release 为 `app/build/outputs/apk/release/app-release.apk`；构建结束会自动复制带版本号的 `ditch-short-video-0.2.4.apk` / `ditch-short-video-0.2.4-debug.apk`。0.1.0 起对外分发使用 release APK。

### 0.5 代码地图

```
app/src/main/java/com/gaiding/ditchshortvideo/
├── DitchApp.kt                     Application，创建通知渠道
├── MainActivity.kt                 首页：进度环、准备步骤、App 开关；右上角数据/设置入口
├── SettingsActivity.kt             提醒文案、权限、计时规则、备份/导入、清空数据、关于
├── DataActivity.kt                 独立数据页：每日曲线、控制计划、明细、热力图、分享图
├── DiagnosticsActivity.kt          运行状态、最近事件、28 项页面规则自检、系统通知信息
├── data/
│   ├── MonitoredApps.kt            5 个 App 元数据
│   ├── DayUtils.kt                 日期/时长/文案格式化
│   ├── PlanEngine.kt               3 天门槛、14 天额度滑降、噪声、自动应用
│   ├── BackupManager.kt            本地 JSON 备份/导入与字段校验
│   ├── Prefs.kt                    设置、提醒文案、计划字段
│   └── UsageStore.kt               每日秒数、400 天清理、备份快照/恢复
├── monitor/
│   ├── ShortVideoAccessibilityService.kt  1 秒 tick、页面事件、暂停、提醒触发
│   ├── ShortVideoDetector.kt       严格页面识别规则
│   ├── SnapshotBuilder.kt          无障碍节点树抓取
│   ├── PageSnapshot.kt             节点/页面/识别结果模型
│   ├── MonitorRuntime.kt           运行时状态供 UI 读取
│   ├── MonitorLog.kt               最近页面/计时事件，诊断页复制
│   ├── Guardian.kt                 断开提醒渠道、15 分钟 watchdog
│   ├── GuardReceiver.kt            watchdog 检查与断线提醒
│   ├── BootReceiver.kt             开机重建 watchdog
│   └── DetectorSelfTest.kt         28 个纯逻辑用例
├── notify/
│   ├── ReminderNotifier.kt         通知渠道、超时提醒、上岛 extras
│   ├── ReminderActionReceiver.kt   通知“关闭提醒”动作
│   └── XiaomiIsland.kt             小米超级岛诊断与实验参数
├── share/
│   ├── ChartShare.kt               生成最近 30 天曲线 PNG、预览与系统分享
│   └── ShareProvider.kt            只读 ContentProvider，临时共享图表
├── system/SystemStatus.kt          权限/后台状态与跳转
└── ui/                             平台 View 工具、进度环、热力图、每日曲线
```

---

## 1. 版本记录

### 0.2.4 — 2026-11-27

精简与归位版。用户（m06565）要求：不要“页面识别守护中”常驻通知；版本号 0.2.4；删掉设置里的“调整额度”，把数据页的导入/导出备份移到设置，首页“你的数据”卡删掉，只保留右上角“数据”入口。

#### 功能与修复

- **移除前台守护服务**：删除 `KeepAliveService` 及 `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` 权限和 manifest 声明；不再有 `guard_service_v1` 渠道与 id 2100 常驻通知。15 分钟 `AlarmManager` watchdog、断线提醒（id 2101）和 OPPO/ColorOS 保活引导保留。
- **首页精简**：删除“你的数据”摘要卡、`dataSummary` 与相关刷新逻辑；右上角“数据/设置”入口保留，首页 hero 的“调整额度”仍可用。
- **设置归位**：删除设置页“每日额度”卡和额度对话框，额度继续在首页 hero 调整；把“备份与导出”卡及 SAF 导出/导入、`onActivityResult` 逻辑从数据页迁到设置页；权限区不再显示依赖 FGS 的“后台守护”行。
- **数据页精简**：删除备份/导入卡与相关 import；分享图、曲线、控制计划、每日明细、年度热力图保留。
- **文案**：ColorOS 卡改为“定时检查会尽量提醒你，但不会常驻通知”。
- **版本**：`versionCode 24` / `versionName 0.2.4`；APK 自动命名 `ditch-short-video-0.2.4.apk` / `ditch-short-video-0.2.4-debug.apk`。

#### 验证

- `:app:assembleDebug` 成功（30s，38 tasks），debug APK 1,031,484 字节；`:app:assembleRelease` 成功（33s，45 tasks），release APK 761,448 字节，签名证书与 0.2.3 相同。
- API 36 模拟器：首页无“你的数据”卡；`dumpsys activity services` 无 `KeepAliveService`；通知栏没有“页面识别守护中”常驻通知，只剩 `guard_alert_v1` 断线提醒渠道。
- 设置页无每日额度卡、无“后台守护”行；备份/导出卡可打开 SAF 并成功导出 JSON（682 字节），导入成功提示“已导入 0 天记录”（测试时无每日记录）。
- 数据页无备份卡，分享预览仍正常；release 包页面规则自检 28/28 通过；版本显示 v0.2.4。
- OPPO/ColorOS 真机未验证；“无 FGS 后后台存活能力变弱”是已知取舍。

#### 本版不做 / 推荐下一步

- 不重新加入前台服务或常驻通知；如果 ColorOS 仍频繁断开，只能继续优化 watchdog、保活引导和恢复路径，不能绕过系统安全模型。
- 下一步建议在真实 OPPO / 一加设备上观察 0.2.4 的断开频率，再决定是否需要更积极的提醒策略。

#### 发布

- GitHub Release：`v0.2.4`（release id `460555514`）。
- APK：asset id `800349272`，`ditch-short-video-0.2.4.apk`，761,448 字节。
- 下载：<https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.2.4/ditch-short-video-0.2.4.apk>

### 0.2.3 — 2026-11-26

分享预览与后台守护版。用户（m05916）提出：分享图应先有预览；版本号 0.2.3；OPPO 用户反馈 ColorOS 经常把页面识别/后台断掉，碎片化刷视频时大部分时间监测不到，希望克服。用户同时肯定整体好用。

#### 功能与修复

- **分享图预览**：`ChartShare` 拆成 `RenderedChart(bitmap, uri)`、`create()` 和 `share()`；`DataActivity.showSharePreview()` 先弹 `AlertDialog` + `ImageView` 预览，按钮“取消/分享”；确认后才打开系统分享面板，dismiss 时回收位图。
- **前台守护**：新增 `KeepAliveService`（`specialUse` 前台服务，低重要度常驻通知），在无障碍连接、主界面 `onResume`、开机后尝试启动；通知 id `2100`，渠道 `guard_service_v1`。
- **断线 watchdog**：新增 `Guardian`、`GuardReceiver`、`BootReceiver`；每 15 分钟用 `AlarmManager.setAndAllowWhileIdle` 检查无障碍是否仍启用/连接：服务活着就尝试拉起 FGS，断开就发“页面识别已断开”通知（id `2101`，渠道 `guard_alert_v1`，点按去无障碍设置）。
- **OPPO/ColorOS 引导**：`SystemStatus.isOppo()` 识别 OPPO/OnePlus/realme；`SettingsActivity` 增加“后台守护”权限行；OPPO 设备额外显示保活卡（自启动、电池无限制、最近任务加锁、允许后台运行）和“打开自启动设置/应用详情”按钮。
- **版本**：`versionCode 23` / `versionName 0.2.3`；APK 自动命名 `ditch-short-video-0.2.3.apk` / `ditch-short-video-0.2.3-debug.apk`。

#### 验证

- `:app:assembleDebug` 成功（38 tasks），debug APK `ditch-short-video-0.2.3-debug.apk`，1,036,936 字节；`:app:assembleRelease` 成功（25s，45 tasks），release APK `ditch-short-video-0.2.3.apk`，766,428 字节。仅有既有弃用警告。
- release 包已卸载 debug 后安装到 API 36 模拟器，版本 v0.2.3；28/28 页面规则自检通过。
- API 36 模拟器：分享预览弹窗正常，图内包含 v0.2.3、曲线和图例；点“分享”可打开系统分享面板；空数据预览的 Y 轴新增最小 4 单位，避免出现重复的“0 0 1 2 2”标签。
- `dumpsys activity services` 确认 release 的 `KeepAliveService` 运行且 `isForeground=true`，类型 `specialUse`；`dumpsys alarm` 确认 `GuardReceiver` 的 15 分钟 `ELAPSED_WAKEUP` 已安排。
- 用仅 debug 的 `GuardDebugReceiver` 触发 `Guardian.notifyDisconnected`，`dumpsys notification` 确认 id `2101`、渠道 `guard_alert_v1`、标题“页面识别已断开”；该 debug receiver 已删除，发布包不包含。
- 设置页出现“后台守护”行（前台服务运行时显示已开启）；模拟器非 OPPO，ColorOS 保活卡未显示，真机路径待验证。

#### 已知限制

- **Android 安全限制**：普通 App 不能自行重新启用无障碍服务。被系统“强行停止”/清理后，用户仍需手动打开一次 App 或无障碍设置；本版只能降低被清理概率、尽快提醒、把恢复入口做短，不能承诺 100% 不断开。
- 前台服务会显示一条低重要度常驻通知；这是 Android 对前台服务的硬性要求。
- 15 分钟 watchdog 是 `setAndAllowWhileIdle`，Doze 下可能被推迟；系统彻底 force-stop 后闹钟也会被取消，直到用户再次打开 App 或重启。
- ColorOS 各版本设置路径不同，`openAutoStartSettings()` 失败时回退应用详情，需要用户按卡片文字手动找。
- 分享预览在超大字体/小屏上可能挤压按钮；目前只在 API 36 模拟器验证。

#### 本版仍不做

- 绕过系统安全模型自动重开无障碍、隐藏前台服务通知。
- 云端保活、账号多设备、远程唤醒。
- 针对每个 OPPO 型号做精确设置深链。

#### 推荐下一步

1. 找一台真实 OPPO/ColorOS 手机跑 24 小时，记录 FGS 是否被清、watchdog 提醒是否到达、重启后是否恢复。
2. 若 ColorOS 仍频繁清理，再评估引导用户把 App 加入“允许后台运行/自启动/最近任务锁定”，而不是继续加隐蔽保活。
3. 观察分享预览弹窗在窄屏、大字体下的按钮布局。

#### 发布

- GitHub Release：`v0.2.3`，release id `458340598`；APK asset id `792214374`，`ditch-short-video-0.2.3.apk`，766,428 字节。
- 下载：https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.2.3/ditch-short-video-0.2.3.apk
- release 签名与 0.2.2 相同，可直接覆盖升级；模拟器 release 安装后 28/28 通过。
- 说明：OPPO / ColorOS 真机尚未实测，保活效果只能按“降低被清理概率 + 尽快提醒 + 一键恢复”描述，不能承诺 100% 不断开。

### 0.2.2 — 2026-11-25

数据工具版。用户（m03754）提出六项：控制计划时长点不动；“我的数据”独立成页、不要主页下滑才能找到；图表图片分享；本地备份/导出；小米超级岛正常、设置里的相关测试可删；版本号 0.2.2。

#### 功能与修复

- **控制计划档位修复**：`DataActivity.renderPlanBody()` 不再每次重建时把 `selectedGoal` 重置为已保存目标；点 15/30/45/60/90/120 分后保持高亮，点“生成计划并启用”使用当前选择。档位改为 6 个等宽、可点击的胶囊，避免小屏上点不中。
- **首页数据入口**：品牌标题右侧新增“数据”和“设置”两个 pill；点“数据”直接打开 `DataActivity`，不用滚到首页底部。
- **分享图**：`share/ChartShare.kt` 用 `DailyCurveView` 的 `fitToWidth` 模式渲染 1440px PNG，包含标题、日期、平滑实际曲线、计划虚线、图例和版本页脚；`share/ShareProvider.kt` 是只读 ContentProvider（authority `${applicationId}.share`），分享时授予临时读权限，不申请存储权限。
- **本地备份/导出**：`data/BackupManager.kt` 通过 SAF 导出/导入 JSON，包含每日额度、监控 App、通知开关、提醒文案、计划状态和每日记录；导入前弹确认框，导入会覆盖当前设置和记录。`UsageStore` 增加 `snapshot()`/`replaceAll()`。第一次导出发现 `monitoredPackages` 被 org.json 序列化成字符串，已改为显式 `JSONArray`；导入损坏文件时返回“备份文件内容不完整或已损坏”，不再直接抛原始 JSON 异常。
- **移除超级岛测试入口**：`SettingsActivity` 删除“发送测试”，提醒卡保留全宽“保存文案”；`DiagnosticsActivity` 删除“实验：附加小米焦点通知参数”开关和“发送测试提醒”，只保留只读“系统通知”信息卡。系统仍按 AOSP promoted ongoing 路径自动上岛/回退普通通知。
- **版本**：`versionCode 22` / `versionName 0.2.2`；APK 自动命名 `ditch-short-video-0.2.2.apk` / `ditch-short-video-0.2.2-debug.apk`。

#### 验证

- `:app:assembleDebug` 成功（约 23s，38 tasks），最终 debug APK 1,048,010 字节；`:app:assembleRelease` 成功（25s，45 tasks），release APK 757,664 字节。仅有既有弃用/可简化条件警告。
- release 包 `ditch-short-video-0.2.2.apk` 已安装到 API 36 模拟器，版本 v0.2.2，28/28 页面规则自检通过。
- API 36 模拟器：控制计划点 45 分后保持选中；生成计划后显示“目标 45 分钟 · 今天自动额度 42 分钟”，计划曲线正常。
- 分享图生成 1440×1086 PNG（81,569 字节），系统分享面板可打开；图中含实际曲线、计划虚线、图例和 `v0.2.2` 页脚。
- 本地备份导出 756 字节 JSON，`monitoredPackages` 为数组；导入同一份备份成功，Toast “已导入 3 天记录”，每日明细恢复 30/40/50 分钟；损坏 JSON 会显示中文错误。
- 28/28 页面规则自检通过；设置页不再有“发送测试”，诊断页不再有超级岛实验开关/测试按钮。
- 截图更新：`home.png`、`data.png`、`plan.png`、`backup.png`、`share.png`、`diagnostics.png`、`settings.png`。

#### 发布

- GitHub Release：`v0.2.2`，release id `458313355`；APK asset id `792112300`，`ditch-short-video-0.2.2.apk`，757,664 字节。
- 下载：https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.2.2/ditch-short-video-0.2.2.apk

#### 已知限制

- 分享图是静态 PNG，不含按 App 分曲线；一次只导出最近 30 天。图例中的“计划”在未启用计划时仍显示，但曲线不画虚线。
- 备份是明文 JSON，不加密；导入会覆盖当前数据，建议先导出再导入。
- B 站真实评论页仍需逐页验证；0.2.2 的自检只覆盖结构特征。
- 曲线静态标签在点数多时仍可能偏密；触摸浮标靠近边缘可能遮住一小段曲线。

#### 本版仍不做

- 云同步、账号、多设备自动同步。
- 按 App 分曲线、分享图自定义模板/水印。
- 备份加密、自动定时备份。

#### 推荐下一步

1. 在真实小米 15 Pro 上验证 0.2.2 的分享图、备份文件选择器和超级岛通知。
2. 观察计划档位在窄屏/大字体下是否仍可点；必要时改成下拉选择。
3. 收集 B 站评论区的真实页面结构，补一条诊断事件样本。

### 0.2.1 — 2026-11-23

曲线交互版。用户（m03518）要求：曲线用光滑曲线而不是折线；曲线上的点加标签，触摸时用小悬浮标签显示具体日期和时间；版本号改为 0.2.1。

#### 功能与修复

- **平滑曲线**：`DailyCurveView` 从逐段直线改为单调三次插值（monotone cubic），曲线经过每个数据点，避免过冲和负值；实际曲线和计划虚线都使用平滑路径。
- **点标签**：实际数据点上方显示分钟数值（如 30、40、50），标签稀疏策略只在数据点很多时降低密度，避免刷屏；计划点仍以小圆点表示。
- **触摸浮标**：点按曲线上的点，出现深色小标签，显示“11月23日 周一 / 实际 50 分钟 / 计划 37 分钟”。没有实际记录的日期显示“实际 无记录”；计划点显示计划额度。浮标显示 3.5 秒后自动收起，也可再次点击其他点。
- **触摸不抢滚动**：手指在曲线上横向拖动超过 10dp 时让外层横向滚动接管；轻点才选中点。
- **日期标签防重叠**：底部日期标签做右侧/左侧边界约束和简单防重叠，避免末端标签挤在一起。
- **版本**：`versionCode 21` / `versionName 0.2.1`；APK 自动命名为 `ditch-short-video-0.2.1.apk` / `ditch-short-video-0.2.1-debug.apk`。
- **文案**：数据页曲线说明增加“轻触曲线上的点查看日期与时长”。

#### 验证

- `:app:assembleDebug` 成功；API 36 模拟器安装 0.2.1 debug，30/40/50 分钟种子数据下曲线平滑、30/40/50 标签正常。
- 点击 50 分钟点弹窗显示“11月23日 周一 / 实际 50 分钟”；启用计划后点击未来点显示“11月24日 周二 / 实际 无记录 / 计划 36 分钟”。
- 28/28 页面规则自检通过（曲线改动不影响识别逻辑）。
- 截图更新：`docs/screenshots/data.png`（平滑曲线+标签）、`plan.png`（计划虚线）、`tooltip.png`（触摸浮标）。

#### 发布

- GitHub Release：`v0.2.1`，release id `455563806`；APK asset id `783328856`，`ditch-short-video-0.2.1.apk`，750,576 字节。
- 下载：https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.2.1/ditch-short-video-0.2.1.apk

#### 已知限制

- 浮标靠近图表顶部/右侧时会放在点的左侧或下方，可能遮住一小段曲线；点本身始终可见。
- 数据点很多时静态数值标签会变密；目前数据页最多 44 个点，已做间隔抽样。
- 浮标没有无障碍朗读，读屏用户只能依赖页面整体描述。
- 未来日期没有实际记录，浮标显示“实际 无记录”是预期行为。

#### 本版仍不做

- 曲线缩放/拖拽查看更长历史、导出图表图片。
- 按 App 分曲线、计划失败后的强制拦截。
- 数据导出/云同步。

#### 推荐下一步

1. 让一加/ColorOS 用户复测 0.2.1，确认 3 秒宽限和触摸曲线没有引入新问题。
2. 观察数值标签在 15/30/120 分钟档位下是否拥挤；必要时只显示选中点或每 3 天一个。
3. 下一版可考虑数据备份/导出，换机不丢记录。

### 0.2.0 — 2026-11-23

数据页与自动控制计划版。用户（m03005）提出：APK 名称带版本号；B 站评论区识别；新增每日曲线；数据单独一页；选择希望控制到的时长后，用几天数据生成带正常波动的计划曲线并自动设置每日额度。

#### 功能与修复

- **独立数据页**：新增 `DataActivity`，集中展示最近 7 天摘要、每日曲线、控制计划、最近 30 天每日明细和年度热力图；首页只保留“你的数据”摘要卡与入口。
- **每日曲线**：新增 `DailyCurveView`，横轴为 30 天过去 + 计划启用后的 14 天未来，纵轴为分钟；绿色实线为实际时长，琥珀虚线为计划额度，今天有竖线，可横向滑动。
- **自动控制计划**：新增 `PlanEngine`。至少 3 天有记录后才能生成；目标可选 15/30/45/60/90/120 分钟；以最近 7 天有记录日的平均分钟为起点，14 天滑向目标，每天叠加 ±3 分钟的确定性噪声，范围 1–1440；生成后立即应用当天额度，并在 App 启动、服务启动和日切时继续应用。
- **计划与手动额度互斥**：手动修改每日额度会关闭自动计划；清空记录也会关闭计划，避免用旧基线继续生成。
- **B 站评论区**：`ShortVideoDetector.isCommentLike` 增加 B 站专用分支：Activity 含 `Comment`/`Reply`，或页面同时出现至少 2 个“评论/回复/发送/说点什么/条评论”标记，才判评论页；普通播放页单个“评论”按钮不会误判。
- **版本与 APK 命名**：`versionCode 20` / `versionName 0.2.0`；构建结束自动复制 `ditch-short-video-0.2.0.apk`（release）和 `ditch-short-video-0.2.0-debug.apk`（debug）。
- **额度输入修复**：设置页额度输入过滤从 3 位改为 4 位，保证能输入 1440。

#### 验证

- `:app:assembleDebug` 与 `:app:assembleRelease` 均成功（约 26–29s）；release APK `ditch-short-video-0.2.0.apk` 约 747 KB。
- API 36 模拟器：release 覆盖安装成功，版本显示 `v0.2.0`；诊断页 **28/28** 通过。
- 用 3 天种子数据（30/40/50 分钟）验证计划：目标 30 分钟、起点 40 分钟，第 1 天自动额度 37 分钟；曲线同时显示实际折线与计划虚线，明细显示“今天 50 分钟 · 计划 37 分钟”。
- 截图更新：`docs/screenshots/home.png`、`data.png`、`plan.png`、`diagnostics.png`。

#### 发布

- GitHub Release：`v0.2.0`，release id `455487860`；APK asset id `783121569`，`ditch-short-video-0.2.0.apk`，747,224 字节。
- 下载：https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.2.0/ditch-short-video-0.2.0.apk

#### 已知限制

- 计划曲线使用确定性噪声，同一天重绘不会跳变；调整目标后会按当前基线重新生成。
- 计划启用当天如果实际时长已超过新额度，首页会直接显示超额；这是数据事实，不会追改历史记录。
- 只有 3 天数据时基线波动较大；计划是行为提示，不是强制锁机。
- 每日曲线只展示最近 30 天实际记录；更早数据仍保留在热力图/本地存储中。
- 计划启用当天，实际点和计划点在同一天，曲线末端的日期标签可能重叠；不影响数值，后续版本再优化排版。
- B 站评论区的规则已通过 28 项纯逻辑用例，但当前没有安装真实 B 站逐页验证。
- 计划自动额度与手动额度互斥；手动改额度会关闭计划，这是有意的。

#### 本版仍不做

- 机器学习预测、按 App 分别设额度、计划失败后的强制拦截。
- 数据导出/备份、云同步、多设备。
- 应用商店上架。

#### 推荐下一步

1. 让 B 站用户实测“进视频→看评论→返回视频”的计时与 30 秒宽限。
2. 观察 3 天计划是否太激进；如需要，把 14 天滑降改成可选的“温和/标准”两档。
3. 收集一加/ColorOS 0.2.0 的“最近事件”，确认 3 秒宽限和计划自动应用没有引入新的停表。

### 0.1.2 — 2026-11-17

一加 ColorOS 计时稳定性修复版：用户回传 0.1.1 的“最近事件”日志后，确认抖音页会在“计时”和“Activity 为空”之间快速翻转，同时还夹着 `com.android.systemui`、输入法 `SoftInputWindow` 的覆盖窗口事件。旧逻辑会把这些系统窗口当成真实前台切换，立即清掉宽限，造成顶栏计时图标断续、最终少计。

#### 用户反馈

- 一加 PLC110 / Android 16：无障碍、通知、省电白名单、服务连接均正常，但刷抖音时图标断断续续，刷久了直接消失，记录时间远低于实际。
- 日志显示同一个抖音页面反复出现“开始计时”和“Activity 为空，等待更强的推荐流证据”；`com.android.systemui/FrameLayout`、`com.baidu.input_oppo/SoftInputWindow` 会短暂抢占前台。
- 内测同学建议：滑动时可能短暂关掉计时，把保留时间延长到 3 秒。

#### 功能与修复

- **3 秒宽限**：`PAGE_GRACE_MS` 从 2 秒提高到 3 秒；`COUNTDOWN_LINGER_MS` 同步改为 3 秒，暂停后倒计时不会立刻消失。
- **SystemUI/输入法覆盖窗口门控**：`ShortVideoAccessibilityService` 新增 `overlayPackage` / `overlaySince` 与 `isSystemOverlay()`；`com.android.systemui`、`SoftInputWindow`、`inputmethod`/`input_oppo` 等覆盖窗口到达时，先保持当前 App 最多 3 秒，不立即清空 Activity/宽限；只有覆盖持续超过 3 秒才把覆盖包提升为前台并暂停。
- **保留抖音/快手的 Activity**：`ShortVideoDetector.shouldClearActivityOnOverlay(pkg)` 只对小红书、B 站返回 true；抖音/快手遇到非 Activity 覆盖窗口时不再清空 `MainActivity`，避免被推到更弱的空 Activity 兜底。
- 服务连接/事件日志继续保留；诊断页可看到“系统覆盖窗口=…，暂不切换”和“系统覆盖窗口超过 3 秒，暂停计时”。
- 版本提升到 `versionCode 12` / `versionName 0.1.2`，release 签名不变，可直接覆盖 0.1.1。

#### 验证

- `:app:assembleRelease` 成功（27s）；release APK `app/build/outputs/apk/release/app-release.apk`。
- API 36 模拟器覆盖安装 0.1.2，版本显示 `v0.1.2`；诊断页“运行 26 个用例”结果 **26/26**。
- 通知栏倒计时、暂停/恢复、事件日志正常；SystemUI 覆盖窗口的 3 秒门控在模拟器上无法稳定复现 ColorOS 的真实事件序列，仍需一加真机复测。

#### 已知限制

- 3 秒宽限会把“已经离开短视频页”的最多 3 秒继续计入；这是用少量多计换稳定性的取舍。
- 如果 ColorOS 直接冻结/杀死无障碍进程，任何页面规则都无能为力；仍只能依赖系统的无障碍开关和断开提醒。
- 事件日志是进程内日志，服务被杀后丢失。

#### 本版仍不做

- 自动重开无障碍、前台常驻保活、开机自启。
- 分 App 单独宽限/自定义规则、新 App、云同步、统计导出。
- 应用商店上架、软著材料。

#### 推荐下一步

1. 让一加同学覆盖安装 0.1.2，重现同样场景后复制诊断信息，重点看“最近事件”里是否还出现 `Activity 为空` 与 `systemui` 切换。
2. 若 3 秒仍不够，再评估按“滑动/评论/覆盖窗口”分场景的动态宽限，而不是继续全局加长。
3. 若日志显示服务被系统杀死，再讨论前台服务保活；不要在没有证据前增加常驻通知。

#### 发布信息

- GitHub Release：`v0.1.2`（release id `448712734`），APK asset id `759293600`，731,772 字节，下载 <https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.1.2/app-release.apk>。
- 0.1.2 与 0.1.1 使用同一 release 证书，可直接覆盖升级，不必卸载。

### 0.1.1 — 2026-11-17

内测反馈修复版：一加 ColorOS 上动画/页面事件里的 Activity 可能为空，导致抖音/快手识别失败；同时增加最近事件日志，方便定位“计时图标断续、记录偏少”。

#### 用户反馈

- 一加 PLC110 / Android 16 内测同学：无障碍、通知、省电白名单、服务连接都正常，但计时中为 false；诊断时前台是 `com.android.systemui`，Activity 为 `—`，原因“等待打开短视频 App”。
- 顶栏计时图标断断续续，刷得久了会消失；最终记录时间远低于实际。
- 规则自检 22/22 通过，说明纯规则用例本身没有失败；问题更可能在真机事件/Activity 获取或页面节点抓取。

#### 功能与修复

- **抖音/快手 Activity 为空兜底**：`ShortVideoDetector` 的兜底包集合扩展到抖音、快手；当 Activity 为空或不是 Activity 类时，只要页面同时具备视频画面 + 明确推荐/发现类标签与互动证据，也按短视频页计时。
  - 抖音：推荐/发现类标签 + 视频画面 + 至少 2 类互动（点赞/评论/分享/收藏）。
  - 快手：发现/精选/推荐/关注类标签 + 视频画面 + 至少 2 类互动。
  - 仍然排除搜索结果、详情/个人/直播、输入框聚焦等页面；普通视频详情不会仅凭“有视频画面”误计。
- **最近事件日志**：新增 `monitor/MonitorLog.kt`，在内存里保留最近 80 条页面/计时事件；只记录时间、App、Activity 类名、切换/识别/计时动作，**不记录页面文字**。
  - 诊断页新增“最近事件”卡片，显示最近 8 条；复制诊断信息时一并复制完整日志。
  - 用于区分“服务被系统杀掉”“Activity 为空”“识别规则不命中”“只差一次确认”等不同原因。
- **连接状态区分**：首页准备步骤不再只看系统里的无障碍开关，而是要求服务真正连接；已开启但服务未连接时显示“页面识别未连接”，按钮变为“重新连接”，避免“显示已开启但其实没计时”。
- 新增 4 个纯逻辑用例：抖音 Activity 为空但强证据、快手 Activity 为空但强证据、抖音搜索页不应误计、快手个人页不应误计；总计 26 项。
- 版本提升到 `versionCode 11` / `versionName 0.1.1`；0.1.0 的 release 签名保持不变，可直接覆盖升级。

#### 验证

- `:app:assembleDebug` 成功（29s），`:app:assembleRelease` 成功（21s）；release APK `app/build/outputs/apk/release/app-release.apk`，731,360 字节。
- 在 API 36 模拟器上覆盖安装 0.1.1 release 包，版本显示 `v0.1.1`；诊断页“运行 26 个用例”结果 **26/26**。
- 真实一加设备尚未复测；0.1.1 是否解决“Activity 为空”导致的少计，需要内测同学安装后复现并复制诊断信息。

#### 已知限制

- 一加问题目前只能确认“Activity 为空/事件缺失”这一条路径已兜底；如果 ColorOS 在后台冻结了无障碍进程，App 仍然无法自行恢复，只能依赖系统的无障碍开关和断开提醒。
- 事件日志只保留进程内最近 80 条；服务重启/进程被杀后，之前的日志会丢失。要看“被杀前发生了什么”，需要系统日志或复现后立刻复制。
- Activity 为空兜底要求页面结构足够强；如果一加上的节点树也被裁剪，仍可能漏计。下一步需要真实一加日志确认。
- 没有加入自动重连无障碍、前台保活服务；这两项涉及系统权限和耗电，暂不擅自扩大范围。

#### 本版仍不做

- 无障碍服务自动重开、前台常驻保活、开机自启。
- 新 App、自定义规则、云同步、详细统计。
- 应用商店上架、软著材料。

#### 推荐下一步

1. 让一加内测同学安装 0.1.1，重现“刷久了图标消失/记录偏少”，然后到诊断页点“复制诊断信息”发回来；重点看“最近事件”里是 Activity=空、节点抓取失败，还是服务断开。
2. 如果日志显示服务频繁断开，再评估前台服务/断开自动提醒；如果只是 Activity 为空，继续放宽对应 App 的结构规则。
3. 收集 2–3 台不同品牌的内测日志后，再决定是否把“最近事件”升级成可导出的诊断文件。

#### 发布信息

- GitHub Release：`v0.1.1`（release id `448686522`），APK asset id `759223871`，731,360 字节，下载 <https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.1.1/app-release.apk>。
- 0.1.1 与 0.1.0 使用同一 release 证书，可直接覆盖升级，不必卸载。

### 0.0.5 — 2026-11-17

用户确认 0.0.4 功能一切正常；后台可靠性这一版只加“无障碍断开提醒”，不加前台保活服务。

#### 用户反馈

- 功能一切正常。
- 需要确定正式签名方案（本版仍使用项目内调试证书，不切换签名）。
- 咨询学校软著提交流程与 AI 生成代码的合规问题。
- 后台可靠性先只做断开提醒。

#### 功能与修复

- 新增 `ReminderNotifier.SERVICE_CHANNEL_ID = "service_status_v1"`、`SERVICE_ID = 1100`。
- 新增 `showServiceStopped(context)`：通知标题“计时已停止”，正文“页面识别被系统关闭了。点这里重新开启，计时才会继续。”；点击直接打开系统无障碍设置；`AUTO_CANCEL`，可滑动清除。
- 无障碍服务 `onInterrupt()` / `onDestroy()` 在连接状态下发送断开提醒；`onServiceConnected()` 取消旧提醒；`cancelAll()` 同时清理 ID `1100`。
- 版本提升到 `versionCode 5` / `versionName 0.0.5`；页面规则仍为 22 项自检，未改识别逻辑。

#### 验证

- `:app:assembleDebug` 成功（28s，37 tasks），APK 975,950 字节。
- 模拟器安装 `0.0.5`；`dumpsys package` 确认 `versionCode=5` / `versionName=0.0.5`。
- 清除无障碍服务后，通知 ID `1100` 正常 post，通知栏显示“计时已停止”；重新开启服务后自动取消。
- GitHub Release：`v0.0.5`（release id `448498420`），APK asset id `758684875`，下载 <https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.0.5/app-debug.apk>。
- 无法在“强行停止”场景验证：进程被系统直接杀死时不会执行 `onDestroy()`，这是系统限制，不是通知逻辑遗漏。

#### 已知限制

- 强行停止、系统直接杀进程、部分 MIUI 清理策略下，服务来不及发通知；本版没有前台服务，无法保证 100% 捕获。
- 通知依赖 `POST_NOTIFICATIONS`；用户关闭通知权限时不会弹出。
- 真实小米 15 Pro 仍需验证：从最近任务划掉、系统省电清理、重启后的实际表现。

#### 本版仍不做

- 前台保活服务、开机自启、无障碍自动恢复。
- 正式 release 签名与商店上架。
- 软著登记材料生成；AI 合规问题另行确认。

#### 推荐下一步

1. 在小米 15 Pro 上分别测试：系统设置里关闭无障碍、划掉最近任务、重启手机，确认哪些场景能收到提醒。
2. 若强行停止场景必须覆盖，再评估前台服务 + 常驻通知，代价是耗电和通知栏占用。
3. 签名方案：继续用调试证书发 0.0.x；正式上架前再生成 release keystore，避免现在切换导致老用户必须卸载。

### 0.1.0 — 2026-11-17

首个 release 签名内测版。功能与 0.0.5 一致，只做版本号、签名和分发方式的收尾，用于超小范围朋友内测。

#### 用户决策

- 学校软著平台要求必须勾选“非 AI”，用户决定**暂时不登记软著**；不提交 AI 生成的代码/文档，也不做虚假承诺。
- 下一版定为 `0.1.0`，开始超小范围内测。
- 用户询问“给朋友发安装包算不算分发，是否要重新考虑签名”。结论：把 APK 复制给朋友安装，已经属于对外提供复制件；从安全角度必须按“会离开自己设备”处理，不能继续使用公开的 debug 证书。

#### 签名切换

- 新建 release keystore：`keystore/ditch-release.jks`，alias `ditch-release`，RSA 4096，有效期 10950 天。
- 证书 SHA-256：`8D:58:21:E8:F5:F9:B3:03:E5:6A:98:0E:85:2A:85:B4:EE:F2:32:66:16:57:C6:51:39:64:C5:93:F6:F9:B5:0D`。
- `keystore/release.properties` 保存 store/key 密码与 alias；和 `.jks` 一起被 `.gitignore` 排除。
- 密码与备份说明另存 `.secrets/release-keystore.txt`，同样不提交 git。**丢了这个文件或 jks，已安装用户永远无法升级，只能卸载重装。**
- `app/build.gradle.kts` 新增 `release` signingConfig；`assembleRelease` 使用该证书，`assembleDebug` 仍用调试证书。
- 从 0.0.5（debug）升级到 0.1.0（release）必须卸载旧版；全新安装不受影响。

#### 版本与构建

- `versionCode 10` / `versionName 0.1.0`（给 0.1.x 留出编号空间）。
- `:app:assembleRelease` 成功，APK `app/build/outputs/apk/release/app-release.apk`，729,644 字节。
- `apksigner verify` 签名通过，`aapt2 dump badging` 显示 `versionCode='10' versionName='0.1.0'`，未开启 debuggable。
- 模拟器卸载旧 debug 包后全新安装 release 包，冷启动成功；首页/设置 footer 显示 `v0.1.0`，引导页三项权限全部绿色。
- 页面识别逻辑未改，0.0.4 的 **22/22** 自检结果继续适用；本版未新增识别规则。
- 代码提交 `241236b feat: 扔掉短视频 0.1.0`，已推送 `main`。
- GitHub Release：`v0.1.0`（release id `448529706`，已设为 latest），APK asset id `758766575`，729,644 字节，下载 <https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.1.0/app-release.apk>。

#### 已知限制

- 真实小米 15 Pro 仍需做一次全新安装验证（签名切换后不能覆盖安装旧 debug 包）。
- 仍未做应用商店上架、隐私政策、正式商店资料。
- 后台保活仍只有电池白名单 + 断开提醒，没有前台服务；强行停止场景仍无法发通知。
- release keystore 只在本机与 `.secrets` 中，若电脑丢失且没有离线备份，将无法再发布升级包。

#### 本版仍不做

- 前台保活服务、开机自启、无障碍自动恢复。
- 应用商店上架、隐私政策、商店截图/素材。
- 软著登记材料；AI 生成代码的合规问题不由本项目解决。
- 更多 App、自定义页面规则、详细统计、云同步。

#### 推荐下一步

1. 先把 0.1.0 发给 1–3 个朋友，要求“卸载旧版后全新安装”，收集机型/系统/是否能收到断开提醒。
2. 如果朋友反馈“划掉后台后收不到提醒”，再评估 0.1.1 是否加前台服务；不要在没有真实需求前增加常驻通知。
3. 正式上架前再准备隐私政策、商店素材和 release 签名备份演练。

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
- GitHub Release：`v0.0.4`（release id `448466048`），APK asset id `758604534`，下载 <https://github.com/Gaiding2007/ditch-short-video/releases/download/v0.0.4/app-debug.apk>。

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
