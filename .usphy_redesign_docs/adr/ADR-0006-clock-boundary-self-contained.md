# ADR-0006: 顶层时钟边界 — PHY 内部自产全部时钟（单一 refclk 入）

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q6 = a）

## Context
PHY 是对上一 DFI、对下一 SDRAM pin 的黑盒。内部至少需要三路时钟域：
DRAM CK/CKE、SerDes 工作时钟、DFI 时钟域。来源候选：
- (a) PHY 内部自产全部：顶层仅 `refclk` 入，内 MMCM 派生 DRAM CK/CKE + SerDes clk + DFI clk，
  `frequencyRatio` 定派生倍数。
- (b) 外部给 DRAM CK，PHY 只做 SerDes 内时钟 + DFI 域 MMCM。
- (c) 外部给全部，PHY 内无 MMCM（纯打拍）。

## Decision
**(a) PHY 内部自产全部时钟。**
- 顶层唯一时钟输入 = `refclk`（可选 `refclk2` 视跨时钟域需求，后续确认）。
- PHY 内部 MMCM/PLL 派生：DRAM CK/CKE、SerDes 工作时钟、DFI 时钟域。
- `frequencyRatio` 决定 DFI 域与 DRAM 域的派生倍数关系。

## Rationale
1. 自包含时钟生成最干净，与 ADR-0001（MMCM 生成 DFI 时钟）及 handoff 表述一致。
2. (b)/(c) 把时钟责任外推，顶层接口复杂化，且与“MMCM 生成的 DFI 时钟”说法冲突。
3. 单一 refclk 进、各域内派生，最贴合 DFI 黑盒语义，也最易验证（时钟域数量与来源清晰）。

## Consequences
- MMCM 配置成为顶层参数的一部分（输入频率、各路输出频率/相位由 generation + frequencyRatio 推出）。
- 复位/时钟使能（CKE）的时序须按 JEDEC 上电序列（初始化子树，待展开）。
- 跨时钟域（DFI 域 ↔ DRAM 域 ↔ SerDes 域）的 CDC 需显式处理——bad 设计 §3 罪状 7
  曾把两级 FF 瞬态不一致误报为 CDC 错误，新设计要区分“真 CDC 违规”与“正常多拍瞬态”。
