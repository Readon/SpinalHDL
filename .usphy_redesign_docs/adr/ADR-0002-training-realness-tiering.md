# ADR-0002: 训练真实性分级 — 写 leveling 做实，读训练显式 stub

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q2 = C）

## Context
旧 `dfi-new:XilinxUSPhy` 的训练是**伪造完成度**（handoff §3 罪状 4，最严重）：
- WriteLeveling：`receivedPattern := expectedPattern`（期望直接赋给接收），随后
  `patternMatch := receivedPattern === expectedPattern` **恒真** → 永远“成功”，从未采样 DQ。
- ReadGate / ReadEye / CA：`receivedData` / `receivedCA` 等“接收”寄存器**从未赋值**
  （仿真 = X，综合 = 浮空），却拿去和 expected 比较判定“找到门眼/对齐”。

团队红线：**不发明协议语义 / 不写“校准通过”**。

## Decision
训练分两档，分别对待：
- **写 leveling（write leveling）= 真实闭环（档 B）**：RTL 内含真实的 DQS 回采路径
  （ISERDESE3 采集 DQS 回采 → 比较/对齐判决 → 调整 DQS 发送延迟 ODELAYE3）。
  训练结果来自真实数据流，非自我赋值。
- **读 gate / read eye = 显式 stub（档 A，第一版）**：DFI 训练握手接口**完整合规**，
  “反馈信号”在 RTL 中是**可注入的测试端口**（testbench 喂值），并显式标注
  **`UNVERIFIED_ON_BOARD`**。

硬性禁令：任何形如 `received := expected` 后 `match := received === expected` 的
自我赋值恒真逻辑一律禁止出现。

## Rationale
- 写 leveling 只需 **DQS 回采**即可构成真实闭环，无需完整 SDRAM 行为模型就能在带
  可配置延迟的激励下有意义地运行——这是 DDR 训练的基石，正是 bad 设计伪造得最狠之处，必须真做。
- 读 gate/read eye 的有效训练**依赖完整的 DDR SDRAM 行为模型**（读数据回环 + 可编程
  延迟/眼宽）才有物理意义；第一版以“接口合规 + 反馈可注入 + 明确未验证”为先，
  避免落入“伪完美对齐”陷阱。

## Consequences
- testbench 必须为读训练提供**反馈注入点**（可控的 received 值入口）。
- 读训练在**上板前必须升级为真实闭环**（引入 SDRAM 行为模型 + 真实 ISERDESE3 采样），
  在此之前其“通过”不具备物理可信度，交付物中标注 `UNVERIFIED_ON_BOARD`。
- 写 leveling 的真实闭环是本 PHY 相对旧设计的第一处实质性可信增量。

## Amendment Rev.2（2026-10-31）— 读 leveling 升级：stub → 真实闭环（方案 A）

读 **leveling** 已从档 A stub 升级为真实闭环（读 gate / read eye 仍为档 A）：
- 读路径引入真实可调元件：每 lane 一条 IDELAYE3（`pads.dqs_in_t → IDELAYE3
  (CNTVALUEIN = rlDelay) → rlSample`），rlDelay 为 dramClk 域 9-bit 计数器
  （种子 256，观测端口 `rdDelayOut`）。
- 训练引擎镜像写 leveling：bang-bang 检测器在每个本地 DQS 上升沿采样
  `rlSample(0)`，16 边沿滑动窗漂移 < 3 taps 连续 8 窗判收敛；`rdlvlResp`
  由收敛检查**赚取**（绝不自我赋值），违反恒真禁令。
- 仿真参考源 = TB 振荡器（rdlvlEn 期间接收到 pad 的代理 DRAM 读 DQS；模型
  不产生读方向 DQS）。均衡点 = 延迟为整数倍 DQS 周期（rdDelay ≡ 0 mod 100
  taps），实测 TC10/TC11/TC12 收敛于 199（极限环 199↔200）。
- 模拟边界（如实记录）：训练所得延迟**不参与** DQ 采样时刻（读 ISERDESE3
  仍以 dramClk 网格采样），DQS-DQ 绝对相位仍是硅片校准属性（ADR-0014 精神）。
- 证据：三 TB `RLDBG resp=1 delay=199`；反证 `RCUT_P=1`（切断参考源）→
  `rdlvlResp` 永不赚取 → R2 失败。
- 仍属档 A stub：读 gate / read eye（`rdlvlGate*`）、DQ 眼图裕量测量。
