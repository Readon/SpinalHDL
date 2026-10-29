# ADR-0007: DFI 接口契约 = 直接复用现成 Dfi(config) bundle，不另编信号

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q7 = a）

## Context
sub-agent 只读核查 feat/ddr4-support 确认：MC 侧已存在完整
`spinal.lib.memory.sdram.dfi.Dfi(config)` bundle —— 53 个命名信号 / 11 个子组
（control / write / read / update / status / rdTraining / wrTraining / caTraining /
levelingTraining / phyRequestedTraining / lowPowerControl / error），DFI 版本 = 6.0.1
（代码注释引用 Table 12/47/48/55）。control 组**同时**携带 legacy（rasN/casN/weN）
与 DDR4（actN/bg/cid）。候选：
- (a) PHY 顶层直接 instantiate 该 bundle 作为 DFI 端口（PHY 侧取对应方向）。
- (b) PHY 另写一套 DFI 信号 + adapter 对接。

## Decision
**(a)** PHY 顶层 DFI 端口 = 直接 `Dfi(config)`，不另编任何 DFI 信号。
方向：MC 是 DFI master（发 control / write / *_training.req），PHY 是 DFI slave
（回 read / status / *_training.ack / err / alertN）。config 由 MC 与 PHY 共享同源。

## Rationale
1. ADR-0003「单一事实来源」的最直接落地：PHY 与 MC 共用同一 DFI 类型，从类型系统
   层面杜绝两侧信号漂移。
2. DFI 版本语义随 bundle 继承，无需单独声明。
3. bad 设计罪状之一即自造了 JEDEC 不存在的接口；(b) 是同类错误的复刻。

## Consequences
- PHY 的 DFI 端口类型即 `Dfi(config)`；无 adapter 层。
- 若未来 DFI 升级，PHY 自动跟随（改共享 bundle，不动私有定义）。
- 需确认一处接线细节：64-bit / 多 subchannel 时 `control.csN` 是否已按 subchannel
  拆分（影响 PHY 侧 CS 模型，见 ADR-0009）。
