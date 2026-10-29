# ADR-0003: 参数化框架先行，DDR4 为首个实例

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q3 = c）

## Context
需求本质是“参数化各种规格”（handoff §2）。维度定为
`generation(DDR2/3/4) × frequencyRatio(1:1/1:2/1:4) × dataWidth × nranks`，
命令编码 / MR 表 / timing 常数全部随参数切换。

MC 侧已验证蓝本 `feat/ddr4-support @ 68e84be5e`（在 monster）提供了**双源核对过的
DDR4 命令编码、MR 表、timing 常数**（Micron 模型 + litedram init.py）。
handoff 曾建议“先 DDR3 起步”，但与现有 MC 蓝本（DDR4）存在张力。

## Decision
1. 建立**参数化框架**：上述四个参数轴是真实的一等公民，命令编码器/MR 表/timing 表
   均为参数化实体，而非硬编码单例。
2. **第一版仅填充 DDR4 实例**，`frequencyRatio` 先做 **1:2**（DFI 最常见），1:4 紧随其后。
3. 命令编码 / MR 表 / timing 常数**复用 MC 侧同一套编码事实**（单一事实来源），
   **不另编一套**，也不为 DDR3 另起编码。

## Rationale
- 最大化复用已验证的 DDR4 编码事实，避免另起 DDR3 编码造成**双源漂移**
  （违反“单一事实来源”原则）。
- 参数化满足“各种规格”诉求，且不把架构焊死在单一代际；后续扩 DDR2/3 = 填参数，非重写。
- 用 DDR4 当首个实例使 MC↔PHY 接口即刻可测（MC 蓝本就是 DDR4）。

## Consequences
- 命令编码器必须是**参数化**的（旧设计的单套硬编码命令路径不符合本 ADR）。
- MC↔PHY 的 DFI 接口以 **DDR4 实例**为准先打通；DDR2/3 实例在框架就绪后补参。
- 参数轴的合法性校验（require）须在编译期拦截非法组合（如不支持的 generation/ratio 组合）。