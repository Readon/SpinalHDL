# ADR-0014: 自定义硅语义 SerDes 行为模型（替代 golden+VIP）

- Status: Accepted
- Date: 2026-10-31
- Decider: pi（用户批准 Plan A，m02938「那就实施A」）

## Decision

e2e xsim（TC10/TC11）不再使用 Xilinx golden 模型 + VIP（`.vp`）管线，改为
`sim/tc10/custom_unisims_sim.v` / `sim/tc11/custom_unisims_sim.v` 内的
**自定义硅语义行为模型**：

- **OSERDESE3**（DATA_WIDTH=4）：CLKDIV 上升沿一次性载入 D[3:0]（16 深 bit
  FIFO 推入 4 bit），此后每个 CLK 边沿（正/负）弹出一个 bit 到 OQ，
  **D[3] 最先发出（MSB-first）**。
- **ISERDESE3**：对所有 CLK 边沿维护 4 拍移位窗 `win = {win[2:0], D}`
  （win[0]=最新样本）；CLKDIV 上升沿呈现 `Q = {win[0],win[1],win[2],win[3]}`
  → **Q[0]=最新、Q[3]=最旧**（PG050 极性）。
- ODELAYE3/IDELAYE3 沿用既有自定义模型（STEP_PS=50/tap）。

由此可以**驱逐 golden 模型的 NBA 采样 workaround**：`UsPhy.scala` 恢复
`oserdes.CLKDIV := sysClk`（非反转），读分布注释重写为生成写帧的严格逆映射
（phase 0 位于 D 的 MSB、线上最先发出；Q 极性 Q[0]=最新）。

## Context

- golden OSERDESE3 在 CLKDIV 正沿 NBA 区加载 D，看到的是上一周期的值；
  早期只能用 `CLKDIV := !sysClk` 翻转对齐 MC 寄存输出稳定时刻（仿真 hack，
  留在 RTL 里会误导综合）。
- 自定义 ODELAYE3/IDELAYE3 早已先例（golden 在 xsim 不可用），本次补齐
  SerDes 半边，使整条数据通路脱离 vendor 模型（TC4 模式的推广）。
- 模型同时暴露内部信号（win[]/q_q/mem[]）供 TB 分层排障。

## Consequences

1. **零输出流水假设**：OSERDESE3 模型无 2-CLK 输出管道（硅上 OQ 相对 D 载入
   有约 2 个 CLK 的固定延迟）。后果：**模型里的绝对延迟 ≠ 硅片绝对延迟**；
   但 e2e 闭环中两端（PHY 发送/接收、PHY/模型）用同一套模型，**相对对齐
   仍然有效**。因此：
   - 仿真中测得的 `tPhyRdlat`/`tPhyWrData`/`wb_delay` 等数值是
     「模型世界」的自洽解，**不得直接用于板级调试的绝对时间戳推断**；
   - 相对关系（如 WB 延迟 ≡ 150 mod 200 的吸引子结构、Q 窗与 rddataValid
     窗的对齐）可以迁移解读。
2. **同沿事件顺序由调度器定义**：ISERDESE3 在同时间戳的多个边沿样本间
   不做仲裁（xsim 事件序稳定但不保证语义）；模型消费方（UsPhy 读分布）
   只依赖「refclk 正沿呈现的 Q = 之前 4 个 ck 边沿样本」这一窗口性质，
   该性质与边沿先后无关。
3. 模型与 RTL 的时序契约集中在两处，改动需同步回归：
   - 写：8-beat 收集窗（模型自对准，见 ADR-0015）；
   - 读：1 边沿 arm（`rd_armed`）使 8-beat 突发贴合两个相邻 Q 窗。
4. 反证体系保留：`MODEL_FILE`/`PHY_FILE`/`TB_FILE` 变异旋钮 + TC10/TC11
   双 e2e + 28/28 SpinalSim 三层闸门。

## Alternatives considered

- **保留 golden+VIP**：xvlog 2023.1 全文件列表下 `-define` 不可靠、VIP 管线
  脆弱（glbl 引用冲突、`.vp` 版本耦合），且 golden 模型迫使 RTL 背 NBA
  workaround。否决。
- **xsim 纯 event-driven 手写 SerDes**（不用 Verific/xvlog）：失去与
  Verific 编译的一致性，工具链分裂。否决。
