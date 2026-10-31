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

- **2026-10-31 · TC10（Step 8c①：e2e 读路径 + 初始化透传验证）完成**：
  - **读使能核心修复**（UsPhy.scala）：`dfi.read.rd(p).rddataValid` 由硬编码 `False`
    改为 `History(rden(p), tPhyRdlat+1)(tPhyRdlat)` —— rden 的纯 tPhyRdlat 周期延迟
    （ISERDESE3 连续解串 DQ，rddataValid 只需镜像移位后的 rden，时长天然等于 rden 时长）。
  - **配置**：E2eMcGen/UsPhyGen `tPhyRdlat` 4→1（匹配模型 tCL + golden SerDes 实际延迟）。
  - **模型 tCL 仿真**：Ddr4SdramModel 增 `TCL_DELAY_EDGES=3` 参数 —— RD 解码后延迟
    3 个 ck_t 边沿再驱动 DQ，使突发对准 ISERDESE3 Q 帧窗（每帧 = 4 ck_t 边沿 = 1 refclk）。
  - **TB 修复**：`bmb_cmd_length` 1→15（Bmb 约定 length=size_in_bytes−1；原来只转发
    第一 fragment，第二片被 BmbAdapter 丢弃）；rsp ready 恒定 1（确定性）；调试探针清除。
  - **验收**：写后同地址读，逐字节精确往返 —— `frag0=0706050403020100`、
    `frag1=0f0e0d0c0b0a0908` 与写入字节完全一致（TB 内置精确比对断言）。
  - **初始化透传**：同一 TB 内验证 MC Ddr4InitSequencer 命令流 —— `INITCHK mrs=7 zqcl=1
    ref=0 illegal=0`（7×MRS + ZQCL 到达模型，无非法命令）。
  - **反证**：撤销 Q 分布的半字交换（`rdBitPos0<-Q(qBase)`）→ 所有半字翻转 →
    两条精确断言均 FAIL（`frag0=0607040502030001`）；恢复后网表逐字节相同，PASS。
  - **零回归**：28/28 DfiControllerTester PASS。
  - 待办：Step 8c②（写 leveling 闭环：PHY IDELAYE3 采 DQS 回响 → 比较 → 调 ODELAYE3）。

- **2026-10-31 · TC10（Step 8c②：写 leveling 闭环）完成**：
  - **职责划分**：MC 永不驱动 `wrlvlEn`（Alignment.scala 注释明确 DFI 级训练发起超出
    MC 范围），故 **TB 充当训练发起方**：拉高 `tr_wrlvlEn` → PHY `wlActive` → 收敛后
    `wrlvlResp` 挣得 → TB 采样 `wlDelayOut` 并放下使能。PHY 既有硬件无需改动
    （ODELAYE3 反馈环 + bang-bang + 漂移窗口收敛判据均为先前步骤落地）。
  - **模型模拟回响**（Ddr4SdramModel）：DQS 输入经 `ECHO_DELAY_PS=2500` 模拟延迟
    返回（`always @(dqs_in[k]) q <= #(ECHO_DELAY_PS/1000.0) dqs_in[k];`）——必须用实数
    ns 换算，文件 timescale 为 1ns/1ps，裸 `#(2500)` 会被解释为 2500ns（千倍过慢，
    首版即踩此坑：回响恒 X）。
  - **拓扑修复**：回响路径此前从未真正接通 —— 模型 `.dqs_echo` 悬空、PHY
    `.pads_dqs_in_t` 读无驱动线（多驱→Z）。现为：模型 `dqs_echo` → TB `dqs_echo_raw`
    →（负证旋钮 `WL_ECHO_CUT_P` 门控）→ `p_dqs_in_t` → PHY 反馈输入。
  - **收敛点推导**：检测器在每个本地 DQS 上升沿后半个周期采样反馈；吸引子为总环路
    延迟 = k×DQS 周期（回响与本地 DQS 同相）：`wb_delay×50ps + 2500ps ≡ 0 (mod 10000ps)`
    → `wb_delay ≡ 150 (mod 200)`。种子 256 → ADD 行走至 350↔351 锁定（漂移≤1<3，
    连续 8 窗达标）→ `wrlvlResp=1`。**实测**：t=2.175µs 挣得，`wb_delay=350`，断言带
    [345..355]。
  - **反证**：负证旋钮 `WL_ECHO_CUT_P=0` 为正式文件常量；反证用变异副本
    `sed 's/parameter WL_ECHO_CUT_P = 0;/parameter WL_ECHO_CUT_P = 1;/' tb_tc10_e2e.v
    > tb_tc10_e2e_cut.v` + `TB_FILE=tb_tc10_e2e_cut.v ./run_tc10.sh`（切断 DQS 反馈）→
    循环无法收敛，2000 周期超时 `resp=0`，W2 两项检查 FAIL → TC10_FAIL。（注：
    xvlog 2023.1 全文件列表下 `-define` 宏不可靠，故弃用宏方案改用变异副本；
    且 run_tc10.sh 自带完整 xvlog+xelab 管线，手工编译会被覆盖。）
  - **零回归**：28/28 DfiControllerTester PASS。
  - 至此 Step 8（端到端集成）三项验收全部达成：初始化透传 ✓、写 leveling 闭环 ✓、
    读写数据完整性 ✓。

- **2026-10-31 · 合并上游 `origin/dev`（de72faf60）**：
  - 上游自 merge-base 领先 187 提交，主要落在 SpinalHDL core（Phase.scala +195、
    sim/package.scala +169 等），**dfi 包零改动**（我方 MC/PHY 源码不受影响）。
  - 唯一冲突：`dfi/function/BmbSpliter.scala`（modify/delete）。我方已在 1c53f0228
    删除其为被遮蔽死代码（通配符导入使 BmbAligner 绑定到 lib BmbSpecificBridges 版，
    fork 拷贝零引用且携带 rsp.last 重算旧 bug）；上游仅做机械性 range→bitsRange
    改名。**裁决：保留删除**。解决后 28/28 + TC10 复验全绿（网表对新 core 重新生成，
    数值与合并前逐项一致）。merge commit 4b06bd38c。

- **2026-10-31 · TC10（Step 8c③：Plan A —— 硅语义 SerDes 模型 + 读路径精确对齐）完成**：
  - **替换 golden 模型**：`custom_unisims_sim.v` 新增硅语义行为模型 OSERDESE3/ISERDESE3
    （OSERDESE3：CLKDIV 上升沿载入 D、每个 dramClk 边沿弹出 1 bit、MSB 先发、零输出
    流水；ISERDESE3：全部 ck 边沿上的 4 拍移位寄存器、CLKDIV 上升沿呈现、Q[0]=最新
    /Q[3]=最旧，符合 PG050 极性）。run_tc10.sh 移除 golden+VIP bootstrap。
  - **撤掉仿真 hack**：`oserdes.CLKDIV := sysClk`（原 `!sysClk` 是 golden 模型的 NBA
    采样 workaround）；IOBUF T 窗口延长 1 周期（wrEnExt2）以覆盖新模型零延迟首发 bit。
    读分布公式未变（qPair=2*(fr-1-p)），注释重写为生成写帧的严格逆映射
    （phase 0 位于 D 的 MSB、线上最先发出）。
  - **读对齐**：Ddr4SdramModel 增加 1 边沿 arm 级 `rd_armed` —— RD 解码边沿锁存、
    下一边沿开始驱动，使 8-beat 突发恰好贴合两个相邻 ISERDESE3 Q 窗（各 4 个 ck
    边沿）、与 MC rddataValid 采样窗一致。
  - **验收**：逐字节精确往返 `frag0=0706050403020100` / `frag1=0f0e0d0c0b0a0908`；
    INITCHK mrs=7 zqcl=1 illegal=0、WLDBG resp=1 delay=350、W1DBG word 与 8c①/② 一致。
  - **反证**（两变异体均按预期 FAIL）：
    (a) `MODEL_FILE=Ddr4SdramModel_cut.v`（删 arm 级立即驱动）→ 4 errors、zz 污染、
        丢字节 0x01/0x03 → arm 延迟不可或缺；
    (b) `PHY_FILE=UsPhy_cut.v`（读 Q 索引相位互换，64 行）→ 2 errors、frag 半区互换
        （`frag0=0302010007060504`）→ 相位映射不可或缺。
    run_tc10.sh 新增 MODEL_FILE/PHY_FILE 变异旋钮（与 TB_FILE 同机制）。
  - **零回归**：28/28 DfiControllerTester PASS。
  - commit 9375c59f3。
- **2026-10-31 · TC11（Step 8c④：随机 e2e 压力 + 模型页状态修复）完成**：
  - 新增 `sim/tc11/`：`tb_tc11_stress.v`（512W+512R Bmb 随机事务，LCG(SEED=42)，
    内容寻址载荷 `byte[i]=key[7:0]^key[15:8]^i`（免历史记分牌），64 槽近期地址环
    限读，~10% 空闲间隙，地址 16B 对齐）+ 复用 TC10 拓扑与自定义模型。
  - **暴露并修复模型真缺陷**（MC/PHY 经 DFI 引脚相关性日志排除嫌疑）：
    (a) RD/WR 须显式解 bank（JESD79-4B Table 17：RD/WR 行 BG/BA 列皆有效），
        原模型沿用 DDR3 隐式 bank 假设；
    (b) 打开页状态 per-rank 单份 → **per-(rank,bank)**（16 bank/rank 随机命中下
        旧影子把读键到错误单元）。详见 ADR-0015。
  - **验收**：512/512 读逐字节精确往返、context 回显 2'b01、`PINCHK
    mrs=7 zqcl=1 ref=23 act=841 wr=512 rd=512 pre=558 illegal=0`（引脚计数
    == BMB 计数：无重复/丢失）。
  - **反证**（两变异体均 1024 errors FAIL）：`MODEL_FILE=Ddr4SdramModel_noarm.v`
    （删 1 边沿 arm 级）；`PHY_FILE=UsPhy_phaseswap.v`（读 Q tap 相位互换）。
  - **零回归**：28/28 PASS；TC10 同步修复模型后复验 PASS。
  - commit 7e3f96008（含 .gitignore 白名单修复 33c4f818f）。

- **2026-10-31 · Step 8c⑤：配置矩阵 elaboration 冒烟完成**：
  - 新增 `tester/.../dfi/phy/E2eSmokeGen.scala`：对超出黄金点（x16/fr=2/nranks=2）
    的两组配置跑 SpinalVerilog elaboration（UsPhy + DfiController 双侧）：
    x32/fr=2/nranks=2、x16/fr=2/nranks=1。产出写入临时目录，不动根网表，无 xsim。
  - **内置常驻反证**：fr=1 必须被 `UsPhy.scala` 的 `require(frequencyRatio>=2)`
    拒绝——若静默放行说明边界悄悄消失。
  - **结果**：`SMOKE_x32_fr2_OK` / `SMOKE_x16_fr2nr1_OK` /
    `SMOKE_negative_fr1_rejected_OK` / `SMOKE_ALL_OK`（一次通过，无框架 bug）。
  - commit d61d7e998。

- **2026-10-31 · 文档收尾（Plan 阶段 3）**：
  - ADR-0014（自定义硅语义 SerDes 模型：零延迟假设与绝对/相对延迟迁移规则）、
    ADR-0015（模型命令语义：Table 17 RD/WR bank + per-(rank,bank) 页状态）。
  - 新增「已知限制」章节（见下）。

## 已知限制（KNOWN LIMITATIONS）

以下为**有意保留的边界**（非欠账；硬件范围已冻结，见 2026-10-31 目标裁定）：

1. **中途复位 = 未定义行为**：`rst_n` 在突发进行中拉低的语义未定义、未建模
   （板级同样如此）；复位只在突发之间生效。
2. **fr=1 不支持**：`UsPhy` 要求 `frequencyRatio >= 2`（OSERDESE3/ISERDESE3
   k=4；fr=1 需 OSERDES2，v1 范围外）。fr=2 之外的高 fr 仅有 elaboration
   冒烟覆盖，无 e2e 仿真。
3. **SerDes 模型零输出流水**（ADR-0014）：仿真中的绝对延迟（tPhyRdlat/wb_delay
   等）是模型世界的自洽解，**不得用于板级绝对时间戳推断**；相对对齐关系可迁移。
4. **TC11 排除非对齐读**（ADR-0015）：模型 32B 单元粒度 vs MC 16B 突发步进；
   非对齐双突发读是 MC 能力，由 28/28 SpinalSim 覆盖。
5. **§二 残留骨架**（有意不补）：无 MMCM（dramClk 顶层直入，ck_t/ck_c 别名）；
   DQS 写选通为自由翻转 TFF（无 tDQSS 空白）；无 DBI/DM；读训练 stub
   （`rdlvlResp:=0`，ADR-0002 tier A）；DFI update/status 未驱动。
6. **§三 板级属性**（UNVERIFIED_ON_BOARD，行为仿真不可表达）：CA setup/hold、
   MMCM 绝对相位、PVT/VTC、硅片眼图签核。
7. **模型键截断**：row[5:0]/col[4:0] 环绕（64×32 单元/rank/bank），TB 地址
   契约限定在该空间内。

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
