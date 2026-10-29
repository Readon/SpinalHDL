# ADR-0010: 初始化 + 训练控制权 — MC 拥有全部编排，PHY 仅做两件 PHY 专属事

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q8 = a）

## Context
MC 侧已完整实现：
- `Ddr4InitSequencer`（MR3→MR6→MR5→MR4→MR2→MR1→MR0→ZQCL，8 步）
- Boot training 经 ctrlmsg（code 0x04=WriteLeveling / 0x07=ZQ）
- Legacy wrlvl 握手（DDR2/3）
- 命令编码（ADR-0008）

PHY 对 DFI 命令流的职责边界待定。写 leveling 有特殊点：DFI 中
`wrlvlReq/Ack/Strobe` 是 PHY 活动——MC 发起请求（orchestration），但 DQS 回采 +
ODELAY 调整是 PHY 干的（ADR-0002 真实闭环）。

## Decision
**(a) MC 拥有全部序列编排（init + 训练调度）。PHY 只做两件 PHY 专属的事：**
1. **DFI 命令流 → 物理 CA/CK/CS/ODT/RESET 引脚的 per-phase 透传 + 静态 CA 接线映射**
   （随 generation 切换，复用 MC 同一套编码事实）。
2. **写 leveling 的真实 DQS 回采闭环**（MC 发 `wrlvlReq`，PHY 跑闭环、回 `wrlvlAck`）。

PHY 内**无** init FSM、**无** MR 表、**无**自主训练决策、**无**命令编码/解码。

## Rationale
1. 与 ADR-0008 一脉相承：「做什么命令/何时/间隔」全是 MC 的事。
2. (b)（PHY 自主 init/training）会在 PHY 里再造 init sequencer / 训练 FSM，与 MC 的
   Ddr4InitSequencer 形成**双源冲突**，违反单一事实来源原则。
3. 写 leveling 是唯一例外——它物理上只能在 PHY 里做（需要真实 DQS 回采 + ODELAYE3）。
4. 这让 PHY 的「代际相关逻辑」收窄到两块：CA 引脚接线表 + 写 leveling 闭环。

## Consequences
- PHY 无状态命令逻辑；验证面极小。
- 初始化序列由 MC 通过 DFI 命令流透传（PHY 不参与决策）。
- 训练调度（何时发起 write leveling / read training / ZQ）由 MC 决定，PHY 被动响应。
- 读训练第一版为 stub（ADR-0002），PHY 侧仅实现 DFI 接口握手 + 可注入反馈端口。
