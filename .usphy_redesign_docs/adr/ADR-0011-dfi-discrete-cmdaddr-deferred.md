# ADR-0011: DFI 命令接口 — 沿用离散信号（2.1 风格），cmdaddr 推迟

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q9 修正 / 用户确认）

## Context
DFI 6.0.1 引入了 `dfi_cmd_en + dfi_cmdaddr`（打包命令总线），但 §3.1.2 的位映射
表只覆盖 LPDDR5/6/DDR5/HBM4——**DDR4 没有 cmdaddr 映射表**。6.0.1 的 Figure 8 时序图
中 `dfi_address` 与 `dfi_cmdaddr` 并列出现，证明两者并存：离散信号服务 DDR3/4，
打包 cmdaddr 服务 DDR5+/LPDDR5+。

现有 MC 蓝本（feat/ddr4-support）使用离散信号（`dfi_address + rasN/casN/weN/actN/
bg/cid + csN`），28/28 测试全绿。无外部 6.0.1 合规强制约束。

## Decision
**DDR4 首版采用 DFI 2.1 时代的离散命令信号风格**（即已验证 MC bundle 的信号集）。
`dfi_cmdaddr` 打包迁移**推迟**，触发条件 = 出现以下任一：
- 多代际统一命令接口需求（DDR2+3+4 共用一条命令总线）
- DDR5/LPDDR5/LPDDR6 支持需求
- 外部合规要求

## Rationale
1. 蓝本已验证，迁移 = 推倒重来 + 重验 28 项，纯亏。
2. 自包含工程（MC+PHY 均自有），无第三方 IP 兼容刚需。
3. cmdaddr 的价值在多代际统一 + DDR5 原生，但 ADR-0003 定 DDR4 优先，DDR5 很远。
4. 与 ADR-0007 一致：PHY 原样消费现有 `Dfi(config)` bundle，零改动。

## Consequences
- DFI 侧 port = 现成 `Dfi(config)` bundle 原样接（PHY 取 slave 方向），不迁移、不改名。
- 命令编码事实（MC 侧编码 → DFI 离散信号）保持不变。
- 若未来触发迁移，PHY 侧只需在 DFI 接口前加一层 unpack（cmdaddr → 离散信号），
  内部逻辑不受影响。
