# Vultr Pocket · Vultr 掌上云管

**把 Vultr 实例、流量和账单，装进一个轻量 Android 看板。**

[English](README.md) · [源码构建](docs/BUILDING.md) · [数据口径](docs/DATA.md) · [安全说明](SECURITY.md)

![Android 8+](https://img.shields.io/badge/Android-8.0%2B-647b9d)
![MIT 许可证](https://img.shields.io/badge/License-MIT-647b9d)
![无需后端](https://img.shields.io/badge/Backend-None-647b9d)

Vultr Pocket 是独立运行的非官方 Vultr 安卓管理看板。原生 Android 代码直连 Vultr v2 REST API，界面使用 APK 内置的 HTML、CSS 和 JavaScript。无需部署服务器，无需在 VPS 安装采集代理，无第三方分析服务。

当前界面为简体中文。本仓库提供源码和构建说明，尚未附带公开发行的预编译 APK。

<p align="center">
  <img src="docs/images/overview.jpg" width="240" alt="仪表：实例状态和账户速览">
  <img src="docs/images/traffic.jpg" width="240" alt="流量：套餐配额与本月用量">
  <img src="docs/images/settings.jpg" width="240" alt="设置：自动刷新选择器">
</p>

截图使用**合成的组件测试数据**与文档专用 IP，属于浏览器预览，不是真实账户或真机截屏。预览适配器不打包进 APK。

## 功能

| 页面 | 内容 |
| --- | --- |
| 仪表 | 实例状态、账户入站、待结算与数据获取时间 |
| 实例 | 机房、套餐、IPv4/IPv6、vCPU/内存/磁盘配置；点击 IP 复制 |
| 流量 | 月入站/出站、套餐参考剩余、账户带宽余额、可左右滑动的每日曲线 |
| 账户 | 待结算、可用余额视角、最近账单与付款 |
| 设置 | 原生 Token 输入、密码修改、本地日志、前台自动刷新、可选重启 |

- **凭据留在手机。** 首次启动自行创建访问密码。Token 通过原生窗口输入，经 Android Keystore 加密保存，不传入页面 JavaScript。
- **减少重复登录。** 记住登录使用固定 30 天有效的加密票据；系统验证调用 Android 系统认证界面，支持方式取决于设备能力。
- **弱网体验。** 先展示符合有效期和月份条件的缓存，再更新。读取失败保留旧数据，不推进成功刷新时间；具体原因放入本地日志。
- **只在前台自动刷新。** 可选关闭、1、3、5 分钟。启动/自动刷新遵守持久化的 60 秒成功间隔，手动刷新不受此间隔限制；无后台刷新任务。
- **默认监控。** 重启默认关闭，开启后每次仍需原生确认；只提交固定的单实例 v2 reboot 请求，不自动重试。
- **轻量实现。** Java 与 Android 平台 API、本地 SVG 和字体、原生 Web 技术；无 Gradle、npm 依赖或第三方 Java 运行库。

## 开始使用

1. 按[构建说明](docs/BUILDING.md)生成并签名 APK，安装到 Android 8.0 或以上设备。应用为 Java DEX，无本地 ABI 库，可用于 arm64-v8a。
2. 首次启动自行设置访问密码，**没有统一默认密码**。
3. 在 **设置 → API Token** 输入自己在 [Vultr API 设置](https://my.vultr.com/settings/#settingsapi)创建的 Token。它来自你的 Vultr 账户，不由本项目提供。
4. 按手机实际出口网络配置 Vultr API 来源 IP 访问控制。Token 正确但 ACL 不允许时，也会访问失败。不要在 Issue 中粘贴密钥或未脱敏的错误内容。
5. 刷新看板；红点的具体原因可在 **设置 → 日志 → 视图** 查看。

Token 本身可具有监控以外的账户权限。应用的接口白名单不会缩小 Token 在 Vultr 的实际权限，详见[安全说明](SECURITY.md)。

## 无 Token 预览

安装 Node.js 22 或以上版本后运行：

```sh
node tools/preview.cjs
```

打开 `http://127.0.0.1:8088`，创建临时预览密码。开发预览只监听本机回环地址，使用合成数据，不请求 Vultr；状态仅存内存，重启后重置。Token 输入与系统验证须在 Android 内使用。

## 数据怎么看

- 每日流量取 Vultr 返回的 UTC 日期记录，最多请求 60 天；它不是实时网速。缺失日期留空，不补假零。
- 实例字节数按十进制 GB 展示。套餐剩余按实例月出站估算，不能作为账户共享配额结论。
- 账户流量来自 `/account/bandwidth`，不保证等于当前可见计算实例之和。入站流量不等于出站计费用量。
- 金额以美元 `$` 展示，无汇率换算。账户余额对 API 原值反号，呈现可用余额；待结算独立显示。
- vCPU、内存和磁盘是配置容量，不是使用率；没有实时 CPU 或主机遥测采集。
- “运行中”仅表示云平台控制面状态，不证明 SSH 或应用健康。重启被接受，不等于系统已启动完成。

完整定义及官方出处见[数据口径](docs/DATA.md)。

## 开发与校验

```powershell
pwsh -File tests/run.ps1
```

测试包含纯 JVM 策略检查与 Node 页面/桥接回归，不需要真实 Token。SDK、签名和构建命令见[构建说明](docs/BUILDING.md)。

```text
src/app/vultrpocket/mobile/   原生 API、加密存储、鉴权、生命周期、弹窗
assets/web/                  APK 内置界面，不含 API 凭据
tests/                       JVM 与 JavaScript 回归测试
tools/                       合成数据浏览器预览，不进入 APK
docs/                        构建、数据口径、预览截图
licenses/                    字体与图标许可证
```

**验证边界：** 本地测试及 APK 编译不能替代真机 Keystore、指纹/人脸、关闭后恢复、前后台与手机网络验收。本项目未执行真实 VPS 重启验收；启用重启前应在可弃用实例上验证。

## 参与和许可

问题报告、修复和文档改进请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)。提供复现步骤和脱敏日志；安全问题按 [SECURITY.md](SECURITY.md) 私下报告。

项目代码采用 [MIT](LICENSE)。Tinos 字体为 SIL OFL 1.1，Lucide 来源 SVG 图标为 ISC，保留各自声明，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目不分发 Times New Roman。

本项目独立维护，与 Vultr 无隶属或背书关系；Vultr 名称仅用于说明对接的服务。
