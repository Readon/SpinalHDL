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