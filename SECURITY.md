# Security / 安全说明

## Credentials and local storage

- There is no shared default password or bundled password hash. First launch creates a local password (8–512 characters, repeated confirmation), hashed with PBKDF2-HMAC-SHA256, a random 24-byte salt and 210,000 iterations.
- Vultr token entry is native Android UI. The token is encrypted with an Android Keystore AES-GCM key, never returned through the WebView bridge, never placed in a URL and never written into source assets.
- Remember-login uses a separate Keystore-encrypted, password-bound ticket with a fixed 30-day lifetime, not an indefinitely sliding session. Logout revokes it; changing the app password invalidates old tickets. System verification is an alternative login mode and depends on Android/device support.
- Cached snapshots use a separate Keystore key, are bound to the token and expire after 24 hours or a UTC month change. Backups are disabled in the manifest.

首次启动自行设置密码，不存在统一初始密码。更换手机、卸载或清除应用数据可丢失本地密码、Token 和缓存。忘记密码没有远程找回入口；清除数据后须重新配置。Keystore 失效也需要重新配置凭据。

## Network and WebView boundary

- Native requests use HTTPS to the fixed `api.vultr.com/v2` origin with endpoint allowlists, bounded responses, timeouts, pagination limits and error mapping. Redirects are not followed.
- The bundled page has no direct Vultr access. The WebView uses a local content security policy, blocks external navigation and does not use remote scripts, remote fonts, analytics, cookies or DOM storage.
- Settings, logs, copy operations and instance controls require an unlocked native session. Login failures are throttled. Auto-refresh is foreground-only; this is not a promise that Vultr account-wide rate limits cannot be reached by repeated explicit manual actions.
- The optional write surface is restricted to one instance reboot endpoint, disabled by default and confirmed natively. A reboot can interrupt service. A timeout is an unknown outcome, so POST is never retried automatically.

## Limits

An app password is a local UI access barrier, not a new permission scope on the Vultr token. Keystore keys are not all bound to a hardware-backed biometric operation. A compromised/rooted device, malicious app build or exploited WebView/native bridge can defeat these protections. Do not describe this as a hardware-audited vault or as a scoped read-only Vultr credential.

应用的接口限制不改变 Token 在 Vultr 的权限。默认只读表示应用行为；启用重启会产生服务中断。保护设备锁屏并使用可信构建，按实际网络配置 Vultr ACL。不要为了便捷公开密钥或盲目扩大 ACL。

## Logs and publication

App logs omit tokens, Authorization headers, full account response bodies and monetary amounts, but may include source IPs needed to diagnose ACL failures. Redact source IPs, instance identifiers/names and account data before sharing logs or screenshots.

Never commit `.env`, signing keys/passwords, local vault files, real API responses, personal APKs or screenshots of a real account. `.gitignore` is only a safeguard, not a secret-removal tool. If a live credential is published, revoke it at the provider first; deleting the current file does not remove history or existing copies.

## Report a vulnerability / 漏洞报告

Do not place tokens or exploitable private-account details in a public issue. Use GitHub's private vulnerability reporting **if the repository offers it**. If it is unavailable, open a minimal issue asking for a private reporting channel without posting the vulnerability details. No guaranteed response window or formal security audit is claimed.

当前公开版本为 1.1.0。安全修复针对当前主分支；不承诺维护个人历史构建或第三方再分发版本。真机认证、Keystore 和真实重启仍需设备/账户验收，离线测试不能替代。
