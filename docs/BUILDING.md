# Build from source / 源码构建

[English README](../README.md) · [中文介绍](../README.zh-CN.md)

## Requirements / 环境

- Windows with PowerShell 7 (`pwsh`). The build script currently uses Windows Android SDK executables; Linux/macOS builds are not provided.
- JDK 21 or later, with `java`, `javac` and `keytool` on PATH. Local validation used JDK 22.
- Official Android SDK Platform 36 and Build Tools 36.0.0, installed through Google's SDK tools. Set `ANDROID_HOME` to the SDK root.
- Node.js 22+ for local message-catalog generation during builds, JavaScript tests and the optional browser preview. No npm installation is needed.

The app uses Java 8 bytecode, minSdk 26 and targetSdk 36. It has no native `.so` files and no ABI split: the same APK works with arm64-v8a. No Gradle or Android Studio project generation step is required.

当前构建脚本面向 Windows/PowerShell 7，使用官方 SDK 命令行工具。Android Studio 不是运行依赖。测试仅依赖 JDK 和 Node；编译 APK 才需要 Android SDK。请按 Android 官方 SDK 使用条款自行安装所需工具。

## Run tests / 运行测试

From the repository root:

```powershell
pwsh -File tests/run.ps1
```

Tests compile into ignored `build/tests-*` directories. They cover endpoint restrictions, credential policy, encrypted login ticket retention, refresh lifecycle, reboot policy/client behavior and UI/bridge flows. They use fixtures and fake transports, not a real account.

## Create your signing key / 创建自己的签名密钥

Generate a key once and keep a secure backup outside the repository. Future updates must use the same application ID and signing key. A different signing key cannot update an existing installation; uninstalling clears its local credentials and data.

创建一次并妥善备份；后续更新必须使用同一签名。不要提交密钥、密码、个人证书或签名后的个人安装包。

```powershell
# Run interactively. Values are read without displaying them or saving to shell history.
$signingSecret = Read-Host 'Signing password' -AsSecureString
$env:VP_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $signingSecret).Password
$env:VP_KEY_PASSWORD = $env:VP_STORE_PASSWORD
New-Item -ItemType Directory -Force build | Out-Null
keytool -genkeypair -keystore build/release.jks -storetype JKS `
  -alias vultr-pocket -keyalg RSA -keysize 3072 -validity 10000 `
  -dname 'CN=Vultr Pocket' `
  -storepass:env VP_STORE_PASSWORD -keypass:env VP_KEY_PASSWORD
```

The password is present in this process's environment while signing. Close the shell or clear the environment afterward. `.env.example` lists variable names only; the script does **not** automatically load `.env`.

## Build a signed release / 编译签名 Release

```powershell
try {
  .\build.ps1 `
    -BuildTools (Join-Path $env:ANDROID_HOME 'build-tools/36.0.0') `
    -AndroidJar (Join-Path $env:ANDROID_HOME 'platforms/android-36/android.jar') `
    -SigningKey (Join-Path $PWD 'build/release.jks') `
    -OutputApk (Join-Path $PWD 'build/Vultr-Pocket-Android.apk')
} finally {
  Remove-Item Env:VP_STORE_PASSWORD, Env:VP_KEY_PASSWORD -ErrorAction SilentlyContinue
}
```

For an existing keystore, set the same environment variables and pass its path. `-KeyAlias` can override the default `vultr-pocket` alias.

The script runs `javac → D8 --release → aapt2 → zipalign → apksigner`, checks signatures/alignment and expected APK members, and generates a UTC build timestamp. Asset packaging is allowlisted to prevent local credential files from entering an APK. No Vultr token or password verifier is needed at build time.

脚本生成 `assets/build-info.json` 和 `build/`，它们不纳入源码版本控制。安装包首次启动自行设置访问密码；不从构建配置注入默认密码或 Token。

## Install / 安装

```powershell
adb install -r build/Vultr-Pocket-Android.apk
```

`adb` is optional; you can transfer your own signed APK to your phone and install through Android's normal UI. Neither installing nor signing publishes the APK anywhere.

## Browser preview / 浏览器预览

```powershell
node tools/preview.cjs
```

Use `http://127.0.0.1:8088`. `PORT` can select another local port. This tool intentionally serves only synthetic data, binds to loopback, rejects cross-origin API requests, and never accepts a real Vultr token. Its password/session state is memory-only. Restart it for a clean preview.

Do not deploy this preview as a public service. It is a UI development tool, not a production backend or a replacement for the Android vault.

## Acceptance boundaries / 验收边界

Compilation, JVM/JavaScript tests and browser captures are distinct from Android device acceptance. Check these on your own test phone: first setup, relaunch retention, expiration/logout, Keystore recovery, system authentication cancellation, rotation/keyboard/back, mobile ACL changes, foreground/background cancellation, and updates signed with your own key. Optional real reboot needs a disposable VPS and explicit operator action; automated tests never reboot a real instance.
