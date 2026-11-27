# 连点器 (AutoClicker)

基于 Android 无障碍服务的连点工具，支持多点位、顺序执行、单击/滑动、间隔抖动与坐标偏移。免 root。

## 用 GitHub 云端编译 APK（无需本地 SDK）

1. 打开 https://github.com ，登录（没有就免费注册一个）。
2. 点右上角 **+** → **New repository**，仓库名随便填（如 `autoclicker`），选 **Public**，点 **Create repository**。
3. 在仓库页面点 **uploading an existing file**（上传已有文件），把本文件夹里的**所有内容**（`.github` 文件夹、`app` 文件夹、`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`README.md`）全选拖进去，点 **Commit changes**。
   - 注意：`.github` 是隐藏文件夹，Mac 上按 `Cmd + Shift + .` 显示；拖拽时要把它一起拖进去。
4. 点顶部 **Actions** 标签 → 左侧点 **Build APK** → 右侧点 **Run workflow** → **Run workflow**。
   - （如果推送后没自动跑，手动触发这一步即可。）
5. 等 2–5 分钟，绿色对勾出现后，点进这次运行，在页面底部 **Artifacts** 里下载 `app-debug`。
6. 下载后解压得到 `app-debug.apk`，传到手机上安装即可。
   - 手机需要允许「安装未知来源应用」。

## 手机上使用

1. 首次打开 App，点 **去开启**，在系统设置中找到「连点器服务」并打开无障碍权限。
2. 点 **去授权**，授予悬浮窗权限。
3. 点 **添加点位**，填 X/Y 坐标（或点「取点」用悬浮窗里的十字标记定位）。
4. 设置每个点位的次数、间隔、时长；滑动类型还需填终点坐标。
5. 设置全局循环次数和间隔抖动百分比。
6. 点 **开始** 运行；悬浮窗上有独立的开始/停止按钮，可随时控制。

> 提示：间隔抖动建议 10–30%，坐标偏移建议 2–5px，让点击节奏更接近真人。
> 单点的「次数」设为 0 表示无限重复，会卡住后续点位，请谨慎使用。

## 技术栈

- Kotlin + XML 布局 + ViewBinding
- AccessibilityService (`dispatchGesture`) 注入手势
- Foreground Service 保活
- Coroutines + Flow 调度
- DataStore + Gson 持久化配置
- minSdk 24 (Android 7.0+) / targetSdk 34
