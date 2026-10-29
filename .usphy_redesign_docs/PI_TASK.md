# PI TASK — XilinxUSPhy Step 1-3（骨架 + 时钟 + 命令路径）

## 你的任务
在 `feat/usphy-redesign` 分支上，基于**已验证可 elaboration 的种子**，搭建新 PHY 的
Step 1（参数化框架）+ Step 2（MMCM 时钟）+ Step 3（DFI 命令→物理 CA 引脚透传）。

## 已验证种子（起点）
文件：`tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/UsPhyProbe.scala`
已确认：DfiConfig 构造 + `slave(Dfi(cfg))` + 物理引脚 bundle → SpinalVerilog 成功。
**不要改这个文件**，它只是证明可行性。你的目标是把它扩展成一个真正的 PHY Component。

## 目标文件
- 主文件：`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/UsPhy.scala`
  （新建，不与旧 `XilinxUSPhy.scala` 冲突——那个在 dfi-new 分支，此分支上没有）
- Package：`spinal.lib.memory.sdram.dfi.phy`
- 类名：`case class UsPhy(cfg: DfiConfig) extends Component`

## Step 1: 参数化框架 + 顶层
1. 定义 `UsPhy(cfg: DfiConfig)` Component。
2. 顶层 port：
   - `val dfi = slave(Dfi(cfg))` — 复用现成 Dfi bundle（已验证可行）
   - `val pads = new Bundle { ... }` — 物理引脚（参照探针的 pads 结构）
   - `val refclk = in(Bool())` — 唯一时钟源
   - `val rst_n = in(Bool())` — 复位
3. 参数化维度从 cfg 中提取（不需要额外参数）：
   - `generation` = cfg.sdram.generation
   - `frequencyRatio` = cfg.frequencyRatio
   - `dataWidth` = cfg.sdram.dataWidth
   - `nranks` = cfg.chipSelectNumber
   - `byteLanes` = cfg.sdram.dataWidth / 8
4. SerDes 倍率自动推导（内部常量，非对外参数）：
   ```scala
   val serdesMult = if (cfg.sdram.generation == SdramGeneration.DDR4) 4 else 2
   ```
5. 物理引脚 pads（per byte lane + per rank）：
   - 数据：dq[byteLanes][8], dqs_t[byteLanes], dqs_c[byteLanes], dm_dbi_n[byteLanes]
   - 命令/时钟：ck_t, ck_c, cke, cs_n[nranks], ca[cfg.addressWidth],
     act_n, bg[bgWidth], cid[cidWidth], odt, reset_n, par
6. **elaboration 验证**：写一个 `object UsPhyElab` main，构造 DDR4 cfg（照探针的
   DfiConfig 字面量），SpinalVerilog(new UsPhy(cfg))，打印 PROBE_OK。
   跑 `sbt "tester/Test/runMain spinal.lib.memory.sdram.dfi.phy.UsPhyElab"` 确认通过。

## Step 2: MMCM 时钟生成
1. 使用 SpinalHDL 自带的 MMCM 黑盒（`spinal.lib.blackbox.xilinx.mmcm.MMCM`）或
   直接用 `MMCM` wrapper。如果仓库里没有合适的 MMCM wrapper，用一个简单的
   `BlackBox` 声明（clk_in → clk_out0/1/2），仿真时不 elaborate 实际 MMCM 逻辑
   （只要接口对就行，P&R 时才需要真实 MMCM）。
2. 派生三路时钟（从 refclk）：
   - `sysClk`：DFI 域时钟（= refclk 本身，或 MMCM 输出）
   - `serdesClk`：SerDes 工作时钟（= refclk × serdesMult / 2，即 DDR4 时 = refclk × 2）
   - `dramClk`：DRAM 域时钟（= refclk / frequencyRatio，即 fr=2 时 = refclk / 2）
   注意：在仿真/elaboration 阶段，这些时钟可以只是 refclk 的别名（打拍/分频），
   关键是**接口和命名正确**，实际频率由 P&R 时 MMCM 配置决定。
3. 复位：`rst_n` 异步复位，内部做 2 级同步释放（`Reg(rst_n)` → `Reg(prevRst)`）。
4. **elaboration 验证**：同上，SpinalVerilog 通过。

## Step 3: DFI 命令 → 物理 CA 引脚（per-phase 透传）
1. 从 `dfi.control` 提取 per-phase 命令信息。DFI 的 control 信号是按 frequencyRatio
   复制的（每 phase 一份）。对于 fr=2：`dfi.control.rasN` 是 `Bits(2 bits)`（phase0+phase1）。
2. 将 per-phase 命令映射到 DRAM 物理引脚：
   - `pads.ca` ← `dfi.control.address`（直接透传，位序不变）
   - `pads.cs_n` ← `dfi.control.csN`（per-rank）
   - `pads.act_n` ← `dfi.control.actN`（DDR4）
   - `pads.bg` ← `dfi.control.bg`
   - `pads.cid` ← `dfi.control.cid`
   - `pads.cke` ← `dfi.control.cke`
   - `pads.odt` ← `dfi.control.odt`
   - `pads.reset_n` ← `dfi.control.resetN`（如果存在）
   - `pads.par` ← CA parity（从 dfi.control 的 parity 信号，如果存在）
3. **不做命令编码/解码**——PHY 是纯透传 + 静态接线。命令编码归 MC（ADR-0008）。
4. 对于 RAS/CAS/WE：DFI 里它们是**独立的 per-phase 信号**（`dfi.control.rasN` 等），
   直接透传到相应的 CA 引脚位（具体哪几位由 generation 决定——DDR4 的 RAS/CAS/WE
   引脚兼做 RA[15:13]，所以这里只是接线，不做逻辑）。
5. **elaboration 验证**：SpinalVerilog 通过，Verilog 中能看到 dfi→pads 的连接。

## 红线（违反 = 任务失败）
1. **不串行化 CA/CK**：命令/时钟走并行 I/O，只有 DQ/DQS/DM 才用 SerDes（Step 4-5 才做）。
2. **不做命令编码**：PHY 不决定"做什么命令"，只透传 MC 已编码好的信号。
3. **不伪造训练**：Step 1-3 不涉及训练，但如果写了任何 `received := expected` 模式的
   代码 = 立即失败。
4. **不修改 MC 侧文件**：只新建 `UsPhy.scala`，不动 `DfiController`/`DfiConfig`/`DFI.scala`。
5. **复用现成 Dfi bundle**：`slave(Dfi(cfg))` 原样接，不另编信号。

## 验证方式
每步完成后跑：
```
sbt -batch "tester/Test/runMain spinal.lib.memory.sdram.dfi.phy.UsPhyElab"
```
看到 `PROBE_OK` 即通过。如果 elaboration 报错，修到通过为止。

## 注意事项
- `DfiConfig` 构造照探针文件的字面量（已验证可编译）。
- `dfi.read.rd` 是 `Vec`，`rddataDbiN` 是 `Bits`（不是 Vec），tie-off 用 `:= 0`。
- `dfi.status.dataByteDisable` 是 `Bits`，tie-off 用 `:= 0`。
- `pads.dq` 是 `Vec(Vec(Bool, 8), N)`，flatten 有歧义，用嵌套 `for(l <- pads.dq) l.foreach(...)`。
- SpinalHDL 版本：Scala 2.12，sbt 1.10.0，Java 17。
- 仓库路径：`/home/yindong/Workspace/SpinalHDL`
- 当前分支：`feat/usphy-redesign`（已 checkout，不要切分支）

## 完成标准
1. `UsPhy.scala` 存在于 `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/`
2. `UsPhyElab.scala`（或类似）存在于 tester 目录，能跑通
3. `sbt "tester/Test/runMain spinal.lib.memory.sdram.dfi.phy.UsPhyElab"` 输出 `PROBE_OK`
4. git status 只显示新增文件（不修改任何已有文件）
5. 代码中有清晰的注释标注 ADR 编号（如 `// ADR-0008: command encoding owned by MC`）
