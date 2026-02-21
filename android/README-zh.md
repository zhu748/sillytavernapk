# SillyTavern Android APK（免 Termux）

这个目录提供一个原生 Android 壳：
- APK 启动后自动在后台拉起内置 Node.js
- 本地运行 SillyTavern 服务
- 通过 WebView 打开 `http://127.0.0.1:8000`

## 1) 先准备资产（在仓库根目录执行）

```powershell
tools\prepare-android-assets.ps1
```

脚本会做三件事：
1. 若 `node_modules` 不存在，执行 `npm ci --omit=dev`
2. 自动下载 `nodejs-mobile-v18.20.4-android.zip`
3. 复制 SillyTavern + 依赖到 `android/app/src/main/assets/app`

## 2) 构建 APK

在 Android Studio 打开 `android/` 目录后构建（Build > Build APK）。

或在命令行构建（Windows）：

```powershell
cd android
.\gradlew.bat assembleDebug
```

产物路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 3) 安装到手机

把 `app-debug.apk` 安装到安卓手机，打开应用后会自动启动 SillyTavern。

## 注意

- 首次启动会把 `assets/app` 解包到应用私有目录，时间取决于你的 `node_modules` 体积。
- 当前默认本地端口 `8000`，仅监听本机回环地址。
- 如果你改了 SillyTavern 代码或依赖，需要重新执行第 1 步并重新打包 APK。
