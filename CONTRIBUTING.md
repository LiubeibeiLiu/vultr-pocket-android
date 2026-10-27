# Contributing / 参与贡献

Keep the app small, self-contained and understandable. A native Android layer owns credentials and networking; one bundled web interface owns presentation. Do not introduce a second product UI or a backend without discussing the need.

## Before a pull request

1. Describe the concrete problem and expected behavior in an issue or PR.
2. Use synthetic data, documentation IP ranges and fake transports. Do not call a real reboot endpoint in a test.
3. Run `pwsh -File tests/run.ps1`. For native changes, also build a signed APK using your own key; explain any missing device validation.
4. For visible UI changes, attach a mobile-width browser or device capture and identify which it is. Redact account details.
5. Keep commits free of tokens, password verifiers, signing materials and private logs. Retain third-party notices.

Bug reports should include app version, Android/WebView version, reproduction steps, expected/actual behavior, and redacted errors. “HTTP 403” is useful; a live token is not.

请用合成数据与可复现步骤说明问题。修改流量或计费逻辑时，附官方字段与口径出处，区分实例、账户、估算和实际账单。没有真机验收就明确写出，不把组件测试描述成真机通过。

Contributions are submitted under the project's MIT license, except third-party assets which retain their own licenses. Keep PRs focused and describe what was tested and what remains unverified.
