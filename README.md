# 扔掉短视频

> 把时间，拿回来。

一个只在**真正刷短视频**时计时的 Android 应用。到了自己设定的每日额度，它只提醒一次，不锁机、不关 App、不上传数据。

中文名：**扔掉短视频**　英文名/仓库名：**ditch-short-video**　当前版本：**0.0.1**

[项目日志](log.md) · [GitHub 仓库](https://github.com/Gaiding2007/ditch-short-video)

---

## 截图

| 首页 | 设置 |
| --- | --- |
| ![首页](docs/screenshots/home.png) | ![设置](docs/screenshots/settings.png) |

| 诊断与页面规则自检 | 超时提醒 |
| --- | --- |
| ![诊断](docs/screenshots/diagnostics.png) | ![通知](docs/screenshots/notification.png) |

---

## 它做什么

- **只在短视频页面计时**：抖音推荐流、快手发现流、微信视频号首页、小红书视频笔记、B 站故事模式才算；聊天、搜索、个人主页、评论页都不算。
- **暂停很及时**：离开页面、切 App、锁屏、熄屏、无障碍服务断开，立刻暂停；回到页面后继续累计。
- **每日总额度**：5 个 App 合并计算，默认 30 分钟，可设 1–720 分钟；每天 0 点重置。
- **超时只提醒一次**：标题和正文可自定义，支持 `{used}` / `{limit}` 占位符；不强制拦截。
- **小米超级岛尽力适配**：Android 16 的 promoted ongoing 通知作为主路径；系统支持就上岛，不支持就普通通知。小米焦点通知参数作为诊断页实验开关预埋，默认关闭。
- **类 GitHub 小绿点**：首页年度热力图，颜色越深代表当天刷得越久；今天有描边，未来日期淡灰。
- **数据只在本地**：没有账号、没有云同步，App 没有申请联网权限。

## 第一版支持的 App

| App | 包名 | 计时页面 |
| --- | --- | --- |
| 抖音 | `com.ss.android.ugc.aweme` | 推荐流主页面（`MainActivity` + 互动栏） |
| 快手 | `com.smile.gifmaker` | 发现/精选首页（`HomeActivity` + 互动栏 + 顶部标签） |
| 微信视频号 | `com.tencent.mm` | 视频号首页（`FinderHomeAffinityUI`），不统计聊天/朋友圈 |
| 小红书 | `com.xingin.xhs` | 带视频证据的笔记详情（`NoteDetailActivity` + 画面/进度控件） |
| 哔哩哔哩 | `tv.danmaku.bili` | 故事/竖屏播放页（`StoryVideoActivity` 等） |

第一版只支持这 5 个 App；更多 App 与自定义规则放在后续版本。

## 怎么判断“真的在刷”

采用**严格页面识别**，宁可漏计，不误计：

1. 先按包名过滤，只处理被监控的 App。
2. 从 `TYPE_WINDOW_STATE_CHANGED` 事件里拿当前 Activity。
3. 用 Activity 名 + 页面结构/互动栏标签/视频控件做组合判断。
4. 搜索页、输入框聚焦、评论页、普通视频详情页等直接否决。
5. 连续 2 秒都识别为短视频页面才开始计时；离开即暂停。

> 停在短视频页面就计时，不额外判断视频是否暂停、评论区是否展开。页面规则基于 2026-11 的 App 版本，App 大改版后可能失效。

## 安装

第一版是调试签名 APK，适合自己侧载体验：

```powershell
# 在项目根目录构建（首次会下载 Gradle 8.14）
.\gradlew.bat :app:assembleDebug

# 产物
# app\build\outputs\apk\debug\app-debug.apk
```

也可以直接用 Android Studio 打开项目，等待同步完成后运行 `app`。`local.properties` 里的 `sdk.dir` 由本机 Android Studio 生成，不提交到仓库。

安装后，按首页“开始前，只差这几步”依次完成：

1. **开启页面识别**：系统设置 → 无障碍 → 扔掉短视频 → 开启。
2. **允许通知**：Android 13+ 需要通知权限；否则超时提醒无法送达。
3. **允许后台运行**：小米手机建议把省电策略设为“无限制”，并允许自启动；否则系统可能清理计时服务。

## 开发

- Kotlin，纯平台 View/API，无 AndroidX/Compose 依赖
- `minSdk 26` / `compileSdk 36` / `targetSdk 36`
- AGP `8.13.1`，Kotlin `2.2.20`，Gradle `8.14`
- 包名：`com.gaiding.ditchshortvideo`
- 签名：项目内调试证书 `keystore/debug.keystore`（仅调试，勿用于正式发布）

开发第一版时所在环境无法联网下载 AGP 的 `aapt2`，因此本地构建通过命令行额外传入：

```powershell
.\gradlew.bat :app:assembleDebug `
  -Pandroid.aapt2FromMavenOverride="D:\applications\SDK\build-tools\36.1.0\aapt2.exe"
```

普通联网环境不需要这个参数，Android Studio 同步后直接构建即可。

页面规则自检在 App 内 **设置 → 诊断与页面规则 → 运行 13 个用例**，不依赖安装真实短视频 App。

## 权限

| 权限 | 用途 |
| --- | --- |
| `BIND_ACCESSIBILITY_SERVICE` | 读取当前页面结构，判断是否短视频页面 |
| `POST_NOTIFICATIONS` | 超时提醒、测试提醒 |
| `POST_PROMOTED_NOTIFICATIONS` | Android 16 上请求 promoted ongoing（超级岛/实时更新） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 跳转系统省电设置，降低后台被清理概率 |
| `VIBRATE` | 提醒振动 |

没有 `INTERNET` 权限；数据只写入本机 SharedPreferences。

## 已知限制

- 5 个 App 的页面规则尚未在真实小米设备上逐页验证，App 改版可能导致漏计/误计；诊断页会显示当前 Activity 和识别原因，方便反馈。
- 只保证普通全屏场景；分屏/小窗的页面归属不保证准确。
- 小米超级岛取决于系统版本和通知权限；侧载应用通常拿不到官方焦点通知权限，`miui.focus.param` 可能被系统忽略。没有上岛时会回退为普通通知。
- 使用调试签名，不适合上架应用商店。
- “强行停止”App 会同时停掉无障碍服务，重新打开后需要在系统设置中再次开启页面识别。

## 版本

- `0.0.1` — 首个可运行版本（2026-11-16）：5 App 严格识别、暂停/继续、每日额度、超时提醒、小米超级岛尽力适配、年度热力图、本地存储、诊断页与 13 项自检。

详细需求、实现记录、验证结果与后续计划见 [log.md](log.md)。
