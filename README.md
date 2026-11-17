# 扔掉短视频

> 把时间，拿回来。

一个只在**真正刷短视频**时计时的 Android 应用。到了自己设定的每日额度，它只提醒一次，不锁机、不关 App、不上传数据。

中文名：**扔掉短视频**　英文名/仓库名：**ditch-short-video**　当前版本：**0.1.0**（内测）

[项目日志](log.md) · [GitHub 仓库](https://github.com/Gaiding2007/ditch-short-video) · [下载 APK](https://github.com/Gaiding2007/ditch-short-video/releases/latest)

---

## 截图

| 首页 | 设置 |
| --- | --- |
| ![首页](docs/screenshots/home.png) | ![设置](docs/screenshots/settings.png) |

| 诊断与页面规则自检 | 超时提醒 |
| --- | --- |
| ![诊断](docs/screenshots/diagnostics.png) | ![通知](docs/screenshots/notification.png) |

| 计时中的常驻倒计时 |
| --- |
| ![倒计时](docs/screenshots/countdown.png) |

---

## 它做什么

- **只在短视频页面计时**：抖音推荐流、快手发现流、微信视频号首页、小红书视频笔记/视频流、B 站故事模式才算；聊天、搜索、个人主页不计时，评论区短暂停留最多宽限 30 秒。
- **暂停很及时**：离开页面、切 App、锁屏、熄屏、无障碍服务断开，立刻暂停；回到页面后继续累计。
- **每日总额度**：5 个 App 合并计算，默认 30 分钟，可设 1–1440 分钟；每天 0 点重置。
- **计时中的常驻倒计时**：识别到短视频页面后，通知栏会出现一条静默倒计时，显示还能看多久；下拉通知栏不会瞬间消失，暂停后保留 2 秒再收起。
- **超时只提醒一次**：标题和正文可自定义，支持 `{used}` / `{limit}` 占位符；不强制拦截。
- **小米超级岛尽力适配**：Android 16 的 promoted ongoing 通知作为主路径；系统支持就上岛，不支持就普通通知。小米焦点通知参数作为诊断页实验开关预埋，默认关闭。
- **类 GitHub 小绿点**：首页年度热力图，颜色越深代表当天刷得越久；今天有描边，未来日期淡灰。
- **数据只在本地**：没有账号、没有云同步，App 没有申请联网权限。
- **断开提醒**：页面识别被系统关闭或服务中断时，发一条可点击的通知，点按直接跳到系统无障碍设置；重新连接后自动收起。

## 第一版支持的 App

| App | 包名 | 计时页面 |
| --- | --- | --- |
| 抖音 | `com.ss.android.ugc.aweme` | 推荐流主页面（`MainActivity` + 互动栏/视频画面） |
| 快手 | `com.smile.gifmaker` | 发现/精选首页（`HomeActivity` + 顶部标签 + 互动/画面） |
| 微信视频号 | `com.tencent.mm` | 视频号首页（`FinderHomeAffinityUI`），不统计聊天/朋友圈 |
| 小红书 | `com.xingin.xhs` | 视频笔记或视频流（有画面/播放进度 + 互动证据） |
| 哔哩哔哩 | `tv.danmaku.bili` | 故事/竖屏播放页（`story_title` 结构优先，Activity 暂时缺失/残留也能识别） |

第一版只支持这 5 个 App；更多 App 与自定义规则放在后续版本。

## 怎么判断“真的在刷”

采用**严格页面识别**，宁可漏计，不误计：

1. 先按包名过滤，只处理被监控的 App。
2. 从 `TYPE_WINDOW_STATE_CHANGED` 事件里拿当前 Activity；只接受真正的 Activity/UI 类，评论弹层、Popup、FrameLayout 不会清空底层页面。
3. 用 Activity 名 + 页面结构/互动栏标签/视频控件做组合判断；B 站优先看故事页标题结构，小红书优先看视频画面/播放进度，允许 Activity 暂时为空。
4. 搜索页、输入框聚焦、图文页、普通视频详情页等直接否决；评论区等临时页最多宽限 30 秒，普通页面切换只宽限 2 秒。
5. 连续 2 秒都识别为短视频页面才开始计时；B 站/小红书进入页面后的 8 秒内会主动重抓节点树，避免第一次打开时事件早于页面内容；离开 App、锁屏、熄屏立即暂停。

> 停在短视频页面就计时，不额外判断视频是否暂停、评论区是否展开。页面规则基于 2026-11 的 App 版本，App 大改版后可能失效。

## 安装

0.1.0 起使用 **release 签名** 的 `app-release.apk`，可以从 [GitHub Releases](https://github.com/Gaiding2007/ditch-short-video/releases/latest) 下载；也可以本地构建：

```powershell
# 在项目根目录构建（首次会下载 Gradle 8.14）
.\gradlew.bat :app:assembleRelease

# 产物
# app\build\outputs\apk\release\app-release.apk
```

本地构建 release 需要 `keystore/release.properties` 和 `keystore/ditch-release.jks`；这两个文件已被 `.gitignore` 排除，不随仓库分发。

也可以直接用 Android Studio 打开项目，等待同步完成后运行 `app`。`local.properties` 里的 `sdk.dir` 由本机 Android Studio 生成，不提交到仓库。

> 从 0.0.5（debug 签名）升级到 0.1.0（release 签名）必须**先卸载旧版**，否则会报签名不一致。卸载会清空本地记录；内测朋友请直接全新安装 0.1.0。

安装后，按首页“开始前，只差这几步”依次完成：

1. **开启页面识别**：系统设置 → 无障碍 → 扔掉短视频 → 开启。
2. **允许通知**：Android 13+ 需要通知权限；否则超时提醒无法送达。
3. **允许后台运行**：小米手机建议把省电策略设为“无限制”，并允许自启动；否则系统可能清理计时服务。

## 开发

- Kotlin，纯平台 View/API，无 AndroidX/Compose 依赖
- `minSdk 26` / `compileSdk 36` / `targetSdk 36`
- AGP `8.13.1`，Kotlin `2.2.20`，Gradle `8.14`
- 包名：`com.gaiding.ditchshortvideo`
- 发布签名：`keystore/ditch-release.jks`（RSA 4096，30 年），alias `ditch-release`；证书 SHA-256 `8D:58:21:E8:F5:F9:B3:03:E5:6A:98:0E:85:2A:85:B4:EE:F2:32:66:16:57:C6:51:39:64:C5:93:F6:F9:B5:0D`
- `keystore/release.properties` 保存签名配置，和 `.jks` 一样被 `.gitignore` 排除；密码另存 `.secrets/release-keystore.txt`，**务必离线备份，丢失后无法再升级已安装的 App**。
- 调试构建仍使用 `keystore/debug.keystore`，只用于本地调试；不要拿 debug 包对外分发。

开发第一版时所在环境无法联网下载 AGP 的 `aapt2`，因此本地构建通过命令行额外传入：

```powershell
.\gradlew.bat :app:assembleRelease `
  -Pandroid.aapt2FromMavenOverride="D:\applications\SDK\build-tools\36.1.0\aapt2.exe"
```

普通联网环境不需要这个参数，Android Studio 同步后直接构建即可。

页面规则自检在 App 内 **设置 → 诊断与页面规则 → 运行 22 个用例**，不依赖安装真实短视频 App。

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

- 5 个 App 的页面规则已在模拟器和本地伪 App 上验证，但**尚未在真实小米 15 Pro 上逐页验证**；App 改版可能导致漏计/误计。遇到不计时，请到诊断页复制当前 Activity/识别原因反馈。
- 只保证普通全屏场景；分屏/小窗的页面归属不保证准确。
- 评论区：临时页面最多继续计 30 秒，普通页面切换最多 2 秒；如果评论页特征识别不到，会按普通 2 秒宽限暂停。这是 0.0.3 的折中策略。
- 小米超级岛取决于系统版本和通知权限；侧载应用通常拿不到官方焦点通知权限，`miui.focus.param` 可能被系统忽略。没有上岛时会回退为普通通知。
- B 站首次进入故事页的修复基于“故事标题 + 视频画面 + 8 秒重抓”；真实 B 站版本若改掉 `story_title` 或改用其他播放器，仍可能失效。遇到漏计，请到诊断页复制 Activity/识别原因反馈。
- 0.1.0 起使用 release 签名，可做小范围内测；仍未做应用商店上架、隐私政策和正式商店资料。
- “强行停止”App 会同时停掉无障碍服务；从最近任务划掉也可能被 MIUI 清理。当前只能通过电池无限制/自启动/最近任务加锁降低概率，尚未加入前台保活服务。
- 计时中的倒计时是 LOW 重要度静默通知，不会响铃或震动；下拉通知栏可见，暂停后 2 秒收起。

## 版本

- `0.1.0` — 2026-11-17：首个 release 签名内测版；版本号 `0.1.0` / versionCode `10`；功能与 0.0.5 一致，切换正式发布证书，用于超小范围内测。
- `0.0.5` — 2026-11-17：新增无障碍断开提醒，点击通知直达系统无障碍设置；重新连接自动取消；后台保活仍只做提醒，不加前台服务。
- `0.0.4` — 2026-11-17：修复 B 站首次打开故事页不计时（故事标题兜底 + 8 秒重抓 + 清除过期 Activity）；软件内版本号改用 `BuildConfig.VERSION_NAME`；22 项自检。
- `0.0.3` — 2026-11-17：B 站故事页结构兜底、小红书视频流识别、评论区 30 秒宽限/普通切换 2 秒、倒计时收起缩短到 2 秒；21 项自检。
- `0.0.2` — 2026-11-17：上限提高到 1440 分钟；修复评论弹层/滑动导致停表；放宽抖音/快手/小红书识别；新增计时中的常驻倒计时；17 项自检。
- `0.0.1` — 2026-11-16：首个可运行版本：5 App 严格识别、暂停/继续、每日额度、超时提醒、小米超级岛尽力适配、年度热力图、本地存储、诊断页与 13 项自检。

详细需求、实现记录、验证结果与后续计划见 [log.md](log.md)。
