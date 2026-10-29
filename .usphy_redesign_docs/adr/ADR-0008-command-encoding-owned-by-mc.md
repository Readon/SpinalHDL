# ADR-0008: 命令编码归 MC；PHY = per-phase 传输 + 静态 CA 引脚映射

- Status: Accepted (事实推导)
- Date: 2026-09-14
- Decider: digital-rtl（sub-agent 核查 feat/ddr4-support 证实，用户无异议即采纳）

## Context
sub-agent 核查 Task2IDFI.scala：MC 已把命令**编码好**放进 DFI control 总线。
- DDR4: ACT = actN=0 + RA[15:13] 走 RAS/CAS/WE 脚；READ=(1,1,0,1)、WRITE=(1,1,1,0)、
  PRE=(1,0,1,0)[+A10]、REF=(1,0,0,1)、MRS=(1,0,0,0)、ZQCL=(1,1,1,1)（非 ACT 均 actN=1）。
- legacy DDR3: ACTIVE=b011 / WRITE=b100 / READ=b101 / PRE=b010 / REF=b001。
PHY 从 DFI 收到的是**已编码命令**，不是原始 RAS/CAS/WE 语义。

## Decision
PHY **不做命令编码 / 解码 / 仲裁**。PHY 职责仅两项：
1. **per-phase 传输**：按 frequencyRatio 把 DFI per-phase 命令向量展开/折叠到 DRAM 时钟沿。
2. **静态 CA 引脚映射**：一张随 generation 切换的接线表
   （DDR4: actN/bg/cid + RA-on-RAS/CAS/WE；DDR3: rasN/casN/weN/BA），复用 MC 同一套
   编码事实，**不另编**。

## Rationale
1. 「做什么命令、何时、间隔多少」是 MC（timing 控制器）的职责；PHY 是物理层搬运工。
2. 把命令编码塞进 PHY 正是 bad 设计罪状 3 的根源（RAS/CAS/WE 当互斥选择 + 凭空造
   act_n）。分离后 PHY 无状态命令逻辑，消除一整类 bug，并大幅缩小验证面。
3. 进一步收窄 ADR-0003：PHY 连「编码」都不做，只做「接线」——命令事实 100% 留在 MC。

## Consequences
- PHY 内**无**命令 FSM、**无** MR 表、**无** timing 常数（全在 MC）。
- 初始化序列（MR3→MR6→MR5→MR4→MR2→MR1→MR0→ZQCL，共 8 步，MC 的 Ddr4InitSequencer
  发出）由 PHY **透传**，不参与决策。
- PHY 唯一的「代际相关」逻辑 = 那张 CA 接线表。
