# ADR-0013: L2 边界 — 命令整形归 MC；UsPhy 逐相位交付（per-phase delivery）

- Status: Accepted (Rev.2 取代 Rev.1 的 Decision 2)
- Date: 2026-11-27
- Decider: pi（用户授权按优先级推进 L2 边界裁定）

## Rev.2（2026-10-29，TC8 实测推翻 Rev.1 Decision 2）

**实测证据（TC8：`sim/tc8/`，Ddr4PinModel 引脚级解码模型 ↔ UsPhy 闭环）**：

Rev.1 认为 "PHY 硬编码 phase0 透传即可，无需相位逻辑"。TC8 证明这是错的：
DFI 向量只在 DFI 时钟沿更新，而 DRAM（及任何引脚级接收方）在 **每个 dramClk
上升沿**采样引脚电平。硬编码 phase0 时，一个 DFI 周期内的 **两个 DRAM 周期**
采到的是同一片 phase0 → **每条命令被解码两次**（TC8 RED：9 个计数器全部双倍）。

**协议依据**：DFI 6.0.1 §2.1 —— DFI 相位 i 对应 DRAM 时钟对中的特定边沿；
PHY 必须把每个相位交付给它对应的 DRAM 周期（per-phase delivery 是
"per-phase transport" 的本义义务，不属于 MC 的"整形"范畴）。

**Rev.2 决定**：

1. UsPhy 增加 `dramPhase`（dramClk 域自由翻转寄存器）+ `phaseSel()`：
   每个 DRAM 周期抽取 packed 向量的第 i 片送到引脚。
   MC 任意 `cmdPhase ∈ [0, frequencyRatio)` 均被正确 honoring，
   **Rev.1 的 `require(cmdPhase == 0)` 废除**。
2. Rev.1 的 Decision 1（命令编码/NOP 保持/电平型信号保持归 MC）与
   Decision 3（setup/hold 归板级，UNVERIFIED_ON_BOARD）**维持不变**。
3. **反证（负例）**：
   - 关掉翻转（`dramPhase := False`）→ TC8 9 项计数双倍失败；
   - 切断 BA 映射（`pads.ba := 0`）→ TC8 仅 bank 字段失败（chk3/chk10），
     计数正常。两故障维度独立可辨。
4. 关联修订：ADR-0012 修正案（新增专用 `ba` port；A[2:0] 时间复用方案被否决——
   实测其破坏 ACT 时的 RA[2:0]，且与真实硅片 BG0/BA0/BA1 专用引脚不符）。

## Context

L1（`fa9a21f83`，UsPhy pads 引出 ras_n/cas_n/we_n）遗留了一个模糊的 "L2 gap"：
"multi-cycle command shaping (holding a command across the CK half-cycles)"。
本 ADR 将该 gap 拆解并逐一定界。

**代码证据（feat/usphy-redesign 工作树）**：

1. `function/Task2IDFI.scala`：MC 只向 `cmdphase(cfg.timeConfig.cmdPhase)` **单个相位**
   写命令；所有相位默认 `payload.setAll()`（全 1 = DDR4 NOP）+ `valid.clear()`。
2. `function/DfiAlignment.scala::CAAlignment`：phase i 打包进 packed 向量的第 i 片
   （LSB 端起）；空闲默认值是确定性 NOP（csN/actN/ras/cas/we=1，cid/address/bank=0）；
   **CKE 在所有相位强制 =1**（`caAlignment.io.cke.setAll()`）。
3. `DfiController.scala`：显式支持任意 `cmdPhase ∈ [0, frequencyRatio)`
   （elaboration assert + 注释 "any command phase ... is supported"）。
4. `phy/UsPhy.scala`：硬编码 `phase0()`（取最低片）透传到引脚，忽略 phase≥1 与 valid 位。
5. UsPhy 全部在用配置（UsPhyElab/UsPhyGen）均为 `cmdPhase = 0`。

**协议事实**：

- JESD79-4B：DDR4 命令 = CK **上升沿**采样的 {ACT#,RAS#/A16,CAS#/A15,WE#/A14,BG,BA,A,CS}
  电平组合（Table 37）。与 DDR3 不同，DDR4 无 2N 模式下沿命令、无半周期内命令翻转。
- 电平型信号（CKE/RESET#/ODT）要求持续电平：CKE 已由 MC 全相位保持（证据 2），
  RESET# 是静态 pad，ODT 静态 tie-off。
- DFI 规范（6.0.1 §2/§4 通则）：DFI 相位与 DRAM 时钟沿的对齐关系是实现（PHY）属性，
  MC 被刻意做成可在任意相位放命令，以便适配不同 PHY 的对齐约定。

## Decision

1. **命令整形所有权 = MC，且已满足**。"整形"分解为三项，全部落在 MC 侧并已实现：
   (a) 命令编码（ADR-0008）；(b) 非命令相位保持 NOP（证据 1/2）；
   (c) 电平型信号持续保持（CKE 全相位 =1，证据 2）。
   DDR4 无跨 CK 半周期的命令翻转需求，故 **PHY 侧无需额外的多周期整形逻辑**。
2. **UsPhy 的相位契约 = 消费 phase 0**。由于 MC 合法地支持任意 cmdPhase 而 UsPhy
   硬编码 phase0，`cmdPhase ≠ 0` 会导致引脚永久 NOP（静默功能失效）。
   处置：UsPhy 顶部加 `require(cfg.timeConfig.cmdPhase == 0)`，
   与仓库"不支持的组合在 elaboration 期拒绝"的惯例一致（参 DfiController 的 assert）。
   ⚠️ **【已被 Rev.2 取代】** TC8 实测证明硬编码 phase0 导致每条命令被解码两次；
   正确契约是逐相位交付（见上方 Rev.2）。
3. **引脚级 setup/hold（相对 CK 上升沿）= 板级/P&R 属性**（ODDR/IODR 对齐、
   IBUFDS skew），行为仿真的时钟对齐无法表达，按 `UNVERIFIED_ON_BOARD` 对待
   （与 ADR-0002 的训练路径同等处置）。

## Rationale

1. DFI 协议的分工本来就是：MC 决定"什么命令、何时、持有多久"，PHY 忠实搬运。
   证据 1-3 表明 MC 已把协议要求的全部整形义务做完；PHY 再引入整形逻辑只会
   违背 ADR-0008 并放大验证面。
2. require 守卫把"隐性的相位假设"变成 elaboration 期的显式契约，
   消灭最坏的一类 bug（静默 NOP），成本一行。
3. setup/hold 归入板级是正确的诚实边界：xsim 行为模型里 pin 与 CK 的相对
   时序由 TB 时钟生成方式决定，RTL 层面无可验证对象。

## Consequences

- UsPhy v1 的可用配置空间收窄为 `cmdPhase == 0`（其余不变）。
  未来若要支持 cmdPhase≠0，需同时改 UsPhy 的 phase 抽取为 `cfg.timeConfig.cmdPhase`
  索引，并把相位-沿对齐约定文档化。
- L1 提交中模糊的 "L2 gap" 表述由本 ADR 取代；UsPhy.scala Step-3 注释已同步。
- 下一步（L3）：命令解码型 SDRAM 行为模型 —— 有了本 ADR 的相位契约，
  行为模型只需在 dramClk 上升沿采样引脚电平即可解码命令，无需建模半周期保持。
