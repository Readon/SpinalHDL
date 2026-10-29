# ADR-0005: 验证层级 — 功能仿真 + 时序收敛目标，写 leveling 须 cycle-accurate

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q5 = b）

## Context
验证做到哪一层？
- (a) 仅功能仿真：DFI 握手/命令序列正确即可，SerDes 用抽象模型。
- (b) 功能仿真 + 明确时序收敛目标：功能正确之外，声明 P&R 阶段的时序闭合目标
  （MMCM 余量、SerDes 路径、I/O 速度等级）。
- (c) 全栈含真实 SDRAM 模型 + 完整时序收敛：最贵。

## Decision
**(b) 功能仿真为主，附带明确的时序收敛目标声明。**
- 功能层面：DFI 握手、命令编码序列、初始化序列、写 leveling 闭环在仿真中正确。
- 时序层面：不以“跑通仿真”为终点，而是**声明** P&R 必须闭合的目标
  （MMCM 输入/输出余量、SerDes 4x 路径、I/O bank 速度等级 ≥ 目标速率）。
- **写 leveling 真实闭环必须达到 cycle-accurate**：SerDes 打拍/延迟按 cycle 精确建模，
  不能抽象成“一拍对齐”的黑箱——否则 DQS 回采对齐无物理意义。
- 读训练第一版为 stub（ADR-0002），不要求 cycle-accurate。

## Rationale
1. 写 leveling 是真实闭环，其正确性直接依赖 cycle 精度；抽象掉就打回了“伪对齐”。
2. 时序收敛目标声明让“功能绿”不等于“交付”——P&R 阶段有据可依。
3. 不把全栈（真实 SDRAM 模型）压进第一版，控制范围（呼应 ADR-0002 读训练 stub）。

## Consequences
- testbench 需支持 cycle-accurate 的 SerDes 延迟建模（写 leveling 路径）。
- 交付物须附一份“时序收敛目标清单”（非实测，是 P&R 验收准则）。
