# TASK — XilinxUSPhy 重构（从零搭骨架）

## 背景
旧 `dfi-new:XilinxUSPhy.scala`（1875 行）已被评审判定弃用（四大根基性错误 + 
两处伪造完成度）。本任务在 monster 上**新建** PHY 文件，从零搭建正确架构。
在 `feat/usphy-redesign` 分支上**覆盖**原路径的 `XilinxUSPhy.scala`。旧版本保留在 `dfi-new` 分支（git 可追溯），不影响。

## 架构蓝图
参见 `architecture.html`（已 scp 到 monster 的 `/home/yindong/Workspace/SpinalHDL/`）。
核心要点：
1. **数据通路**：仅 DQ/DQS/DM 走 I/O SerDes（OSERDESE3/ISERDESE3 + ODELAYE3/IDELAYE3 + bitslip）。
   每 byte lane 独立。SerDes 倍率由代际/速率自动推导（DDR4 → 4x）。
2. **命令/时钟**：CA/CK/CKE/CS/ODT/RESET 走**并行 I/O 引脚**，不做并串。
   CK 由 MMCM 产生的干净差分时钟驱动。
3. **命令编码**：归 MC（已验证蓝本 `feat/ddr4-support`）。PHY 只做 per-phase 透传 +
   静态 CA 引脚映射（随 generation 切换的接线表）。
4. **初始化/训练**：MC 拥有全部编排。PHY 仅做两件 PHY 专属事：
   (a) DFI 命令流透传到物理引脚；
   (b) 写 leveling 真实 DQS 回采闭环（ISERDESE3 采集 → 比较 → 调 ODELAYE3）。
5. **时钟**：顶层仅 `refclk` + `rst_n`，内部 MMCM 自产 DRAM CK/CKE + SerDes clk + DFI clk。
6. **DFI 接口**：直接复用现成 `spinal.lib.memory.sdram.dfi.Dfi(cfg)` bundle（PHY 取 slave 方向）。
   不另编信号、不迁移 cmdaddr。
7. **读训练**：第一版为显式 stub（接口完整 + 反馈可注入 + 标注 UNVERIFIED_ON_BOARD）。
   **严禁** `received := expected` 后 `match := received === expected` 的自欺逻辑。

## 顶层 Port（ADR-0012 冻结）
- `refclk`: in, 1 bit
- `rst_n`: in, 1 bit
- `dfi`: slave(Dfi(cfg)) — 复用现成 bundle
- SDRAM 引脚（per byte lane × per rank，DDR4 x32 为例）：
  - data: `dq[L][8]` inout, `dqs_t[L]` inout, `dqs_c[L]` inout, `dm_dbi_n[L]` inout
  - cmd/clk: `ck_t/ck_c` out, `cke` out, `cs_n[R]` out, `ca[A]` out,
    `act_n` out, `bg[B]` out, `cid[C]` out, `odt` out, `reset_n` out, `par` out
- 排除：VDD/VDDQ/VPP/VREFCA/VREFDQ（board-level）

## 参数化维度（对外参数）
- `generation`: DDR4（首版）
- `frequencyRatio`: 2（首版）— DFI:DRAM 时钟比
- `dataWidth`: 32（首版）
- `nranks`: 1~4

## 实施步骤（每步原子提交 + 反证 + 零回归）

### Step 1: 顶层骨架 + 参数化框架
- 新建 `XilinxUSPhy.scala`（同名新文件，或在子目录 `phy/v2/`）
- 定义顶层 Component：refclk/rst_n/dfi/SDRAM 引脚
- 参数化框架：generation/frequencyRatio/dataWidth/nranks 四维 + require 校验
- SerDes 倍率自动推导（内部派生量，非对外参数）
- **验收**：elaboration 通过（sbt runMain 生成 Verilog），参数非法组合报错
- **反证**：把 require 故意放宽 → elaboration 应报错 → 恢复
- **零回归**：老 28 项测试不变

### Step 2: MMCM 时钟生成
- 顶层 refclk → MMCM → 派生 DRAM CK/CKE + SerDes clk + DFI clk
- frequencyRatio 决定派生倍数
- 复位同步释放
- **验收**：elaboration 通过，Verilog 中可见 MMCM 实例 + 正确频率配置
- **反证**：把 MMCM 输出频率改错 → assertion 应触发（如果有的话）→ 恢复

### Step 3: DFI 命令 → 物理 CA 引脚（per-phase 透传 + 静态映射）
- 从 `dfi.control` 取 per-phase 命令向量
- 按 frequencyRatio 展开/折叠到 DRAM 时钟沿
- 静态 CA 引脚映射表（随 generation 切换）：
  - DDR4: actN/bg/cid + RAS/CAS/WE 脚（承载 RA[15:13]）+ BA + CA
  - DDR3: rasN/casN/weN + BA + CA
- csN per-rank 映射
- **验收**：仿真中给 DFI 命令，观察 SDRAM 引脚输出 = 预期电平
- **反证**：改映射表一个 bit → 输出应变 → 恢复
- **零回归**：老 28 项不变

### Step 4: 数据通路 — 写路径（OSERDESE3 + ODELAYE3）
- per byte lane 实例化 OSERDESE3(4x) + ODELAYE3(VARIABLE)
- DFI wrdata → SerDes → ODELAY → pad
- DQS 写 preamble/postamble 生成
- DM 信号跟随
- **验收**：elaboration 通过；仿真中 wrdata 正确串行化到 pad
- **反证**：断开一个 lane 的 SerDes → 该 lane 输出应异常 → 恢复
- **零回归**：老 28 项不变

### Step 5: 数据通路 — 读路径（ISERDESE3 + IDELAYE3 + bitslip）
- per byte lane 实例化 IDELAYE3 + ISERDESE3(4x) + bitslip
- pad → IDELAY → ISERDES → DFI rddata
- rddata_valid 生成
- **验收**：elaboration 通过；仿真中 pad 数据正确反序列化到 rddata
- **反证**：断开一个 lane → rddata 该 lane 应无效 → 恢复
- **零回归**：老 28 项不变

### Step 6: 写 leveling 真实闭环
- ISERDESE3 采集 DQS 回采（SDRAM 的 DQS 反馈路径）
- 比较 DQS 回采与 DQ 对齐
- 根据比较结果调 ODELAYE3（CE/INC）
- 收敛判定（N 个连续对齐 cycle → 完成）
- 完成后回 `wrlvlAck`
- **验收**：cycle-accurate 仿真中，给可配置延迟的 DQS 回采激励，
  训练收敛且 ODELAY 值 = 正确补偿量
- **反证**：把 DQS 回采接成常量 0 → 训练不应"成功"（应超时/报错）→ 恢复
- **零回归**：老 28 项不变

### Step 7: 读训练 stub（接口完整 + 反馈可注入）
- DFI rdTraining 接口握手完整
- 反馈信号 = 可注入测试端口（`io.testFeedback`）
- 显式标注 `UNVERIFIED_ON_BOARD`（注释 + 日志）
- **验收**：仿真中注入正确反馈 → 训练"完成"；注入错误反馈 → 不完成
- **反证**：把反馈端口短接到 expected → 应触发 assertion（禁止自赋值恒真）→ 恢复
- **零回归**：老 28 项不变

### Step 8: 集成 + 端到端仿真
- 连接 MC（DfiController）→ PHY → SDRAM 行为模型
- 跑完整初始化序列（MC 的 Ddr4InitSequencer 透传）
- 跑写 leveling 闭环
- 跑读写数据通路
- **验收**：端到端仿真通过，数据通路正确
- **零回归**：老 28 项不变

## 进度日志

- **2026-10-29 · TC8（Step 8 的前置闭环）完成**：
  - 新增 `sim/tc8/`：`Ddr4PinModel.v`（引脚级命令解码行为模型，dramClk 上升沿采样，
    22 项检查）+ TB ↔ UsPhy 闭环。
  - **RED → GREEN**：初版 UsPhy 两条实锤缺陷——(a) 硬编码 phase0 逐 DRAM 周期透传
    → 每条命令被解码 2 次（违反 DFI 6.0.1 §2.1 逐相位交付）；(b) BA 无物理载体
    （原冻结 port 清单遗漏专用 BA 引脚）。
  - **修复**：UsPhy 增 `dramPhase` 翻转 + `phaseSel()` 逐相位抽取；port 清单增
    专用 `ba` 引脚（ADR-0012 修正案）；`require(cmdPhase==0)` 废除（ADR-0013 Rev.2）。
  - **反证**：关翻转 → 9 项计数双倍失败；切 BA → 仅 bank 字段失败。两维度独立可辨。
  - **零回归**：lib elaboration 通过；UsPhyGen 生成正常。
  - 待办：Step 8 主体（MC DfiController → UsPhy → 行为模型 端到端：初始化序列 +
    写 leveling + 读写数据通路）。

- **2026-10-29 · TC9（Step 8a：命令驱动型 SDRAM 行为模型）完成**：
  - 新增 `sim/tc9/`：`Ddr4SdramModel.v`（复用 TC8 的 `Ddr4PinModel` 作命令解码子实例，
    增加数据通路：WRITE 吸收 16 拍 DDR 数据入 mem[]；READ 从 mem[] 驱 16 拍；
    DQS 回声；未写位置读 → `rd_pending_err`）+ 独立 mini-TB（无 PHY）。
  - **键约定**：mem 键 = `{row[5:0], col32blk=col[4:0], bank[3:0], rank}`；
    col32blk 为 BL16 x16 突发粒度（32B），自测/e2e 选不同 col[4:0] 区分位置。
  - **调试中抓获的真缺陷**（均经反证固化）：
    (a) 写累加器起步 `wr_reg<=dq` 使首拍落入会被左移丢弃的低 16 位 → 改为
        `wr_reg<={dq,240'h0}`（首拍置顶，14 次移位后落 slot1，完成字取 `[255:16]`）；
    (b) TB 空闲时向双向 DQ 注 `x` 把模型读数据掩成 X，且 `check(!cond)` 对 X 视同通过
        → 空闲改注 `z`、`check` 改 `cond!==1'b1`（X 判失败）——此前场景 B 属假阳性；
    (c) 读采样对齐：读机器在 RD 解码正沿之后的**首个边沿**（负沿）才触发，beat0 自该
        负沿起驱动 → TB 采样点整体后移一拍。
  - **反证**（严格判定下）：(A) 复原写累加器 bug → A+B 挂；(B) 读加载清零 → B 16 拍全挂；
    (C) 禁用未写检测 → B2 挂。恢复后 `TC9_PASS`。
  - 待办：Step 8b（集成顶层 + Verilog 生成 + 冒烟）、8c（端到端 TB：初始化序列 +
    写 leveling + 数据完整性；零回归 28/28）。

- **2026-10-30 · TC10（Step 8b：e2e 正向路径冒烟）完成**：
  - 新增 `sim/tc10/`：`tb_tc10_e2e.v`（Bmb 写 2×64-bit fragment → MC DfiController →
    DFI bus → UsPhy → DRAM 引脚 → Ddr4SdramModel 写吸收器）+ `E2eMcGen.scala`
    （tPhyWrData=0，关闭 WrAlignment 延迟链）。
  - **PHY 写入 SerDes 修复**（UsPhy.scala）：
    (a) `CLKDIV := !sysClk`：golden OSERDESE3 在 CLKDIV 正沿 NBA 区加载 D，看到的是
        上一周期的值；翻转到 refclk 负沿对齐 MC 寄存输出已稳定的时刻。
    (b) D-gate：`Mux(wrEnLive, Cat(pairs), Cat(pairs).clearAll)` 防止 wren 低时重载垃圾。
    (c) T 窗口扩展：`!(wrEnLive | wrEnExt)` 覆盖 OSERDESE3 末拍 1-CLK-edge 管道延迟。
  - **模型适配**：Ddr4SdramModel 跳过前导零拍（OSERDESE3 管道冲刷伪影）。
  - **MC 配套修复**（前置 commit）：Alignment.scala 空闲训练输出 + MakeTask.scala
    ACT/PRE 站点门控（防 Verilog 幻影命令风暴）。
  - **反证**：还原 CLKDIV=sysClk → X/Z 污染 + 缺字节 → TC10_FAIL（3 errors）。
  - **零回归**：28/28 DfiControllerTester PASS。
  - 待办：Step 8c（端到端完整 TB：初始化序列透传 + 写 leveling 闭环 + 读写数据完整性）。

## 关键约束（红线）
1. **不发明协议语义**：所有命令编码/MR 值/timing 常数复用 MC 蓝本，不另编。
2. **不写校准通过**：任何训练逻辑必须有真实数据源（或明确标注 UNVERIFIED_ON_BOARD 的注入端口）。
3. **不串行化 CA/CK**：命令/时钟走并行 I/O，只有 DQ/DQS/DM 走 SerDes。
4. **单一事实来源**：DFI 接口 = 现成 `Dfi(cfg)` bundle；命令编码 = MC 侧已验证事实。
5. **每步原子提交**：一个 git commit = 一个功能增量，附反证记录。
6. **零回归底线**：`sbt -batch "tester/testOnly spinal.lib.memory.sdram.dfi.DfiControllerTester"` 始终 28/28。

## 文件位置
- 工作分支：从 `dev`（或 `feat/ddr4-support`）拉新分支 `feat/usphy-redesign`
- 主文件（覆盖旧文件，同路径同包名）：
  `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`
  （旧版本保留在 `dfi-new` 分支，git 可追溯）
- 黑盒（复用现有）：`lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala`
- MC 蓝本参考：`feat/ddr4-support` 分支的 `lib/src/main/scala/spinal/lib/memory/sdram/dfi/` 目录
- 测试：`tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhyTester.scala`

## 参考资料
- ADR 全集：`/workspace/ddr4work/adr/`（11 条，scp 到 monster 的 `/home/yindong/Workspace/SpinalHDL/adr/`）
- 评审报告：`/workspace/ddr4work/review-report.md`
- 术语表：`/workspace/ddr4work/glossary.md`
- DFI 6.0.1 spec：monster `/tmp/dfi.txt`
- JESD79-4B spec：monster `/tmp/ddr4.txt`
- 架构框图：`/workspace/ddr4work/architecture.html`
- 旧设计参照（只读）：`/workspace/phyreview/XilinxUSPhy.scala`

## 执行方式
在 monster 上用 pi 执行：
```
NODE22=/home/yindong/.nvm/versions/node/v22.20.0/bin/node
CLI=/home/yindong/node_modules/@earendil-works/pi-coding-agent/dist/cli.js
export CONTEXT7_API_KEY="$(cat ~/.pi/context7-key)"
"$NODE22" "$CLI" --provider deepseek --print "$(cat TASK.md)" > /tmp/pi_run.log 2>&1
```
每步完成后轮询 `/tmp/pi_run.log`，确认 elaboration + 测试通过后进入下一步。
