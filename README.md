# 歇一会儿（Take a Break）

一个供个人使用的 iPhone / Android 休息提醒应用。安卓调试安装包可从 `android/` 源码构建，生成于 `android/app/build/outputs/apk/debug/app-debug.apk`。

每页及弹窗的当前文字见 [页面文案](docs/页面文案.md)。

## 功能

- 设置累计使用时长、休息时长和提前解锁的最少字数。
- 达到时长后显示休息界面。休息时间结束后自动解锁；也可以在应用里写够字后提前解锁。
- 输入框可使用手机系统输入法的语音听写按钮，把说的话转成文字。应用本身不录音、不上传文字。
- Android 可选“休息时暂停视频”：支持 Bilibili、YouTube、抖音、快手的标准媒体播放会话。音乐应用（例如网易云音乐）不在暂停名单中。视频应用不提供标准媒体控制时，暂停可能不起作用。
- Android 主界面分为“计时”“设置”“准备”三页。计时页的圆形主控轻点开始、暂停或继续；暂停保留已累计时间，长按才结束并清零。
- 圆形主控有触摸缩放与回弹、水波纹、轻震动，外围显示本轮使用进度；计时中进度环缓慢呼吸，暂停时改为暖色。Android 主界面固定竖屏，避免从横屏视频返回时按钮被挤出视野。
- 计时或暂停时按住圆形按钮约 1.6 秒，会出现逐渐填满的暖色圆环和由慢到快的短震；完成时恢复最初的较长震动并结束清零，不播放自制钟声。提前松手按普通轻点处理；移出按钮取消。未开始计时时长按不会触发结束反馈。实际震感和伴随声音取决于手机振动马达、系统设置和硬件。
- “准备”页自动检查必需权限，一次点击按顺序打开尚未授权的系统页面，返回应用后继续。页面按手机品牌提示后台运行设置，并提供本应用的系统详情入口；厂商专有开关需用户在系统设置中操作。
- Android 提醒间隔限制在 1–240 分钟，休息时间限制在 1–120 分钟，输入后会自动收敛到范围内。所有设置保存在本机。

## 两端的实际效果

| 平台 | 使用时长怎么统计 | 提醒方式 | 休息下限 |
| --- | --- | --- | --- |
| Android | 亮屏、屏幕已解锁的时间，包括本应用和其他应用 | 前台服务在其他应用上显示全屏悬浮提醒 | 1 分钟 |
| iPhone | 苹果“屏幕使用时间”统计所选 App、类别或网站的使用时间 | 系统在所选 App 或网站上显示拦截页 | 15 分钟 |

两端都不能阻止用户通过系统设置撤销权限或卸载应用。iPhone 不能把任意自定义界面强行弹到所有应用上方；拦截页也不能直接输入文字，需要回到“歇一会儿”应用中写字。安卓使用悬浮窗覆盖其他应用，但系统界面、权限设置以及部分厂商系统可能显示在悬浮窗上方。

## Android 构建

1. 可直接安装 APK（手机需要允许该来源安装应用），或用 Android Studio 打开 `android/` 并同步 Gradle。项目使用 Android SDK 35、JDK 17 及以上、Android Gradle Plugin 8.7.3。
2. 运行到 Android 8.0 及以上的手机。首次点击“开启休息提醒”会进入“准备”页；点击“逐项配置运行准备”，依次完成悬浮窗、使用情况访问、精确提醒、忽略电池优化、通知权限。已开启的权限自动跳过；授权后返回应用继续。视频控制权限只在开启视频暂停时要求。
3. “准备”页根据手机厂商标识自动显示 OPPO、realme、一加、小米、vivo、三星、华为等品牌的后台设置提示，并打开本应用的系统详情。OPPO K10 建议在“应用详情 → 耗电管理”中开启“允许完全后台行为”和“允许应用自启动”。厂商开关无法由第三方应用代开或可靠读取，因此这部分是建议，不再要求手动勾选确认。厂商若暂停后台服务或延迟闹钟，到点提醒可能延迟。
4. 如需视频暂停，在“设置”页打开开关，并授权“通知使用权”。安卓系统会授予读取所有通知的能力；本应用只查询媒体播放会话，不读取或保存通知内容。无需视频暂停时可关闭开关，也可在系统设置中撤销此权限。
5. 通知栏保留“歇一会儿正在计时”。计时页轻点圆形按钮会暂停并保留进度，再次轻点继续；长按会彻底停止并清零。持续后台计时会增加耗电。

项目已包含 Gradle wrapper。安装 JDK 17 或更新版本与 Android SDK 35 后，在 `android/` 运行 `./gradlew assembleDebug`（Windows 使用 `.\gradlew.bat assembleDebug`）。如 Android SDK 不在默认路径，请在本机设置 `ANDROID_HOME` 或创建 `android/local.properties`；该文件不会提交到仓库。

## iPhone 构建

1. 在 macOS 安装最新版 Xcode 与 [XcodeGen](https://github.com/yonaskolb/XcodeGen)，在 `ios/` 执行 `xcodegen generate`。
2. 将 `ios/project.yml` 中的 `YOUR_TEAM_ID`、三个 Bundle ID，以及三个 entitlements 文件中的 App Group ID 改成你自己的值，并在 Apple Developer 后台为三个 Target 开启 Family Controls；主应用及 Monitor 扩展同时开启同一个 App Groups 容器。
3. 使用 Xcode 打开生成的 `TakeABreak.xcodeproj`，连接 iOS 16 及以上的真机，签名运行。授予“屏幕使用时间”权限后，选择要限制的 App、类别或网站，再点击“开启提醒”。
4. 发布到 TestFlight / App Store 前，必须向 Apple 申请主应用及扩展的 Family Controls 分发 entitlement。

## 开发状态

安卓 APK 已在 OPPO K10 / Android 14 真机安装测试：Bilibili 播放时到点覆盖且媒体会话从 `PLAYING` 变为 `PAUSED`；关闭视频暂停开关时，休息页仍覆盖 Bilibili，而媒体会话保持 `PLAYING`。YouTube 到点覆盖、休息到时自动解除、写够字提前解除、锁屏暂停累计计时也已验证。0.4 版验证了圆形主控的开始、暂停、继续、长按清零，暂停时进度不增加；1 分钟阈值下 Bilibili 上方的新休息弹窗、中文输入法提示、输入达标后提前解锁，以及 OPPO 品牌设置卡片布局。0.5 版检查了进度环、暂停色和横屏视频返回后的竖屏布局。0.6 版验证了短按暂停、按满结束、移出取消，以及停止状态下长按不会触发结束。1.0 版保留过程短震，并恢复 0.6 版完成时的较长震动；自制钟声已移除。其他品牌指引尚未真机验证，后台管理可能不同，不能保证所有设备都准时弹出。iPhone 代码需在 macOS/Xcode 中签名构建，当前 Windows 环境无法生成 IPA，也尚未做 iPhone 真机测试。Android 前台服务的 `specialUse` 类型需要在 Google Play 提审时说明用途。

## 参考的官方平台文档

- [Apple Screen Time frameworks](https://developer.apple.com/documentation/ScreenTimeAPIDocumentation)
- [Apple DeviceActivityEvent threshold](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent/threshold)
- [Apple monitoring minimum interval](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/monitoringerror/intervaltooshort)
- [Apple Family Controls entitlement](https://developer.apple.com/documentation/Xcode/configuring-family-controls)
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW)
