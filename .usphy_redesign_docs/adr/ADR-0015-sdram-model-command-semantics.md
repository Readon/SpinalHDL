# ADR-0015: SDRAM 行为模型命令语义 — RD/WR 携 bank + 每 (rank,bank) 页状态

- Status: Accepted
- Date: 2026-10-31
- Decider: pi（TC11 随机压力暴露模型缺陷后，按规格裁定）

## Decision

`Ddr4SdramModel.v` / `Ddr4PinModel.v`（TC9 引入，TC10/TC11 共用）的命令语义修正：

1. **READ/WRITE 命令显式携带 bank**：`c_bank = {bg, ba}` 在 RD（3'b101）与
   WR（3'b110）解码分支一并提取。依据 **JESD79-4B Table 17**：RD/WR 行的
   BG/BA 列均为 `BG`/`BA`（不是 `—`）。此前模型沿用「隐式 bank」（RD/WR 的
   BA 不采样）的 DDR3 时代假设，导致访问非最近激活 bank 时键错单元。
2. **打开页状态改为每 (rank,bank) 一份**：`row_state[rank*16+bank]`（16 bank
   /rank），ACT 脉冲锁存 `row_state[k*NBANKS+last_bank] <= last_row`；
   WR/RD 的存储键 = `{row_state[rank*16+bank_cmd][5:0], col[4:0], bank_cmd,
   rank}` —— bank 取自**命令自身的 BG/BA**，而非「最后一次 ACT 的 bank」影子
   变量。
3. 存储粒度：一个 16B 字占一个 32B 单元（BL16 x16 = 8 拍 x16 = 128b = 16B，
   键 col 字段 = 物理 column[4:0]，即 colBytes[5:1]）。

## Context

TC11（512W+512R 随机压力，内容寻址校验）首次让流量离开「单 bank 顺序」形态：
16 bank 随机命中下，每次读取都落到别的 bank，旧的 per-rank 单页影子把
~一半读取键到错误单元（959 errors → 修 bank 解码后仍 192 → 修页状态后 0）。
MC 与 PHY 经 DFI/引脚相关性日志排除嫌疑（DFI 上每条 BMB 读恰一条 RD 命令，
引脚合法、计数吻合）。

## Consequences

- **TC11 地址契约收紧**：TB 地址限定 16B 对齐（colBytes ∈ {0,16,32,48}）。
  原因：MC 的 16B 突发以 8 个物理列（16B）步进，非对齐 16B 读会拆成两条
  DRAM 突发、跨越模型的两个 32B 单元，内容寻址期望失效。非对齐（双突发）读
  是 **MC 能力**，已由 28/28 SpinalSim（DfiMemoryAgent）覆盖，不在 PHY 数据
  通路测试范围内（TC11 头部已注明）。
- 模型对 TC10 的单 bank 交通语义不变（同步复制后 TC10 复验 PASS）。
- 模型仍是**简化行为模型**：无 timing 违规检查（tRRD/tFAW 等）、无 precharge
  状态机约束——它验证的是 PHY/MC 的**引脚合法性与数据完整性**，不是 DRAM
  时序合规性（后者归 MC 的 28/28）。

## Alternatives considered

- **扩大模型存储粒度到 16B 单元**（支持非对齐读）：键空间翻倍、收益局限于
  TB 覆盖面，且 MC 侧非对齐路径已有 SpinalSim 覆盖。推迟（如需再做）。
- **在模型里实现完整 bank 状态机**（ACT/RD/WR 冲突、PRE 强制）：偏离
  「合法性监视 + 数据回环」的定位，复杂度不成比例。否决。
