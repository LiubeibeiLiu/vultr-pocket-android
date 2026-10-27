# Data definitions / 数据口径

The app consumes Vultr v2 fields; it does not install a telemetry agent. Upstream data can be delayed or incomplete. Empty values remain empty rather than becoming fabricated zeros.

| Source | Display and boundary / 展示及边界 |
| --- | --- |
| `GET /instances` | Cloud Compute instances; selected fields only. Passwords and KVM URLs are discarded. It does not promise coverage of bare metal or every Vultr product. |
| `GET /instances/{id}/bandwidth?date_range=60` | Returned UTC daily inbound/outbound byte series, with monthly totals filtered to the current UTC month. No real-time Mbps. Missing days break the curve. |
| `GET /account/bandwidth` | Official `current_month_to_date` GB totals and credit/overage fields. These are separate from instance byte sums and plan estimates. |
| `GET /account` | `balance`, `pending_charges`, `last_payment_date`, `last_payment_amount`; missing dates remain blank. |
| `GET /billing/invoices` | Paginated invoice history, displaying the latest 12 records. |
| `GET /plans`, `GET /regions` | Reference configuration, catalogue price and location. Reference plan prices are not final invoices. |
| `GET /instances/{id}` | Focused status polling after an explicitly submitted reboot. Control-plane state is not a guest health check. |
| `POST /instances/{id}/reboot` | Optional, disabled by default, native confirmation required. A 204 response means accepted; no automatic POST retry. |

## Bandwidth / 流量

Instance GB uses bytes ÷ 1,000,000,000. Account GB fields are kept as returned. The quota estimate is:

```text
estimated remaining GB = max(instance.allowed_bandwidth - month outbound bytes / 1e9, 0)
estimated used percent = month outbound GB / instance.allowed_bandwidth * 100
```

This uses the instance's nominal allowance and available reported daily data. It does not prove the actual billable account remaining. Partial reporting, hourly allocation, free credits, purchases and other products can change the account result. Do not add plan allowances together and label them as an official pooled account allowance.

Vultr's bandwidth billing counts outbound traffic. The first traffic card's **本月已用 · 入站** intentionally shows account inbound, using exactly the same source as the next account-traffic card. It is a transfer statistic, not quota consumption. Inbound + outbound is transmission total, not billable outbound usage.

实例套餐剩余是参考估算；账户配额涉及按小时获配等规则，未知汇总规则不作推断。账户 `credits` 字段保留官方数值，不将其冒充已确认的剩余 GB。每日流量条数不代表实例运行天数。

## Money / 费用

Vultr's documented amounts are dollars. Metadata is USD and the UI uses `$`; there is no FX conversion. Currency-formatting support in tests does not mean the app can retrieve or convert a CNY account.

The account card presents **available credit** by displaying `-account.balance`: API `-10` becomes `$10.00`, API `10` becomes `−$10.00`, and zero becomes `$0.00`. This presentation follows the credit/debit examples in the official schema; the account field description itself does not fully document sign semantics. Confirm against your Vultr statement before using it for accounting. Pending charges remain a separate amount and are not deducted again.

账户余额采用可用余额视角反号展示；API 原始字段、发票金额和待结算不修改。账单为准，页面汇总不是财务对账结论。

## Status and freshness / 状态与时效

Healthy status requires the relevant control-plane fields (`active`, `running`, `ok`), not a network probe. Assigned CPU/RAM/disk are configuration values; no live CPU, memory-utilization or interface-speed metrics are inferred.

Snapshots are local, encrypted, token-bound, limited to 24 hours and the current UTC month. Old snapshots are marked internally as stale and affected page indicators turn red; details are available in logs. Only a complete successful refresh advances the persisted success guard. Manual refresh remains available after failures.

After reboot, status checks back off through 5, 10, 20 and 30 seconds, then stay at 30 seconds, only on the foreground instance page. The observation window is at most 10 minutes. An API that remains healthy throughout may not expose the actual guest reboot transition; application readiness remains unverified.

## Official references / 官方出处

- [Vultr API v2 documentation](https://www.vultr.com/api/)
- [Official OpenAPI schema](https://github.com/vultr/vultr-java/blob/master/api/openapi.yaml): `get-instance-bandwidth`, account, billing and reboot operations
- [Official Go SDK instance fields](https://github.com/vultr/govultr/blob/master/instance.go)
- [Official Go SDK account fields](https://github.com/vultr/govultr/blob/master/account.go)
- [Official Go SDK billing fields](https://github.com/vultr/govultr/blob/master/billing.go)
- [How is bandwidth usage calculated?](https://docs.vultr.com/support/platform/billing/how-is-bandwidth-usage-calculated)
- [How are bandwidth caps calculated?](https://docs.vultr.com/support/platform/billing/how-are-bandwidth-caps-calculated)

Upstream schemas and policies can change. Check current official documentation when changing parsing or quota logic; a catalogue or schema example is not evidence of a specific account's invoice.
