# ADR-0012: PHY 顶层 Port 清单冻结

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q9 终局）

## Context
汇总 ADR-0001~0011 的所有决策，冻结 PHY 顶层 port 清单。

## Decision

### 时钟/复位（ADR-0006：PHY 自产时钟）
| Port | Dir | Width | Note |
|------|-----|-------|------|
| `refclk` | in | 1 | 唯一时钟源，内部 MMCM 派生 DRAM CK/CKE + SerDes clk + DFI clk |
| `rst_n` | in | 1 | 异步复位，内部同步释放 |

### DFI 侧（ADR-0007/0011：复用现成 bundle，离散信号）
| Port | Dir | Note |
|------|-----|------|
| `dfi` | slave(`Dfi(cfg)`) | 完整 Dfi bundle 原样接，含 12 子组（control/write/read/update/status/4×training/lowPower/error） |

PHY **不**镜像 MC 私有训练端口（`dfiCtrlMsg` / `bootTrain*` / `legacyWrLvl*`）——
这些是 MC 内部编排用的，不属于 DFI 标准对 PHY 的接口。写 leveling 走 DFI 标准
`wrTraining` 子组（`wrlvlReq/Ack/Strobe`）。

### SDRAM 物理引脚侧（DDR4 x32 为例；参数化后随 generation/dataWidth 缩放）

**数据通路（per byte lane，x32 = 4 lanes）：**
| Port | Dir | Width[lane] | Note |
|------|-----|-------------|------|
| `dq` | inout | 8 | SSTL12 单端 |
| `dqs_t` | inout | 1 | SSTL12_D 差分 |
| `dqs_c` | inout | 1 | SSTL12_D 差分 |
| `dm_dbi_n` | inout | 1 | DM 与 DBI 同引脚复用（JEDEC L8910） |

**命令/时钟（per channel）：**
| Port | Dir | Width | Note |
|------|-----|-------|------|
| `ck_t` | out | 1 | 差分时钟 |
| `ck_c` | out | 1 | 差分时钟 |
| `cke` | out | 1 | |
| `cs_n` | out | nranks | per-rank 片选（JEDEC L713） |
| `ca` | out | addrWidth | 命令地址总线（ACT 时为完整 RA，RD/WR 时为 C[12:0]+AP；**不含 BA**，见修正案） |
| `act_n` | out | 1 | DDR4 激活指示 |
| `bg` | out | bgWidth | Bank Group |
| `ba` | out | dfiBankWidth = bankWidth−bgWidth | Bank Address（修正案新增，专用引脚） |
| `cid` | out | cidWidth | Chip ID |
| `odt` | out | 1 | On-Die Termination |
| `reset_n` | out | 1 | 硬件复位 |
| `par` | out | 1 | CA Parity 输出 |

**排除在数字 port 外（board-level 职责）：**
- VDD / VDDQ / VPP / VREFCA / VREFDQ — 模拟供电/参考电压，由 PCB 电阻分压器提供，
  不由 FPGA 数字逻辑驱动。

### 参数化维度（对外参数，ADR-0003/0004）
| Param | Values | Note |
|-------|--------|------|
| `generation` | DDR4（首版） | 后续扩 DDR2/3 |
| `frequencyRatio` | 2（首版）/ 4 | DFI:DRAM 时钟比 |
| `dataWidth` | 32（首版） | 64 = 2×channel 拓扑翻倍 |
| `nranks` | 1~4 | per-channel rank 数 |

## Rationale
1. 自包含时钟（ADR-0006）→ 顶层仅 refclk+rst_n，最干净。
2. DFI 侧原样复用（ADR-0007/0011）→ 零 adapter、零迁移。
3. SDRAM 引脚严格按 JEDEC79-4B x32 ballout → 无冗余、无遗漏。
4. VREF/电源排除 → PHY 是数字 IP，不管模拟板级。
5. 参数化维度 = handoff §2 的四维，与 ADR-0003 一致。

## Consequences
- 框图/任务书以此为顶层接口基准。
- 若后续需要 VREF 微调（某些高带宽设计），作为独立扩展 port 加入，不污染首版。
- 64-bit 支持 = 实例化 2 份本 PHY（各自独立 CA/CK/CS/ODT，共享 refclk/MMCM）。

## 修正案（2026-10-29，TC8 发现）

原冻结清单遗漏了 BA 的物理载体：`ca` 注释虽写"含 RA/BA/CA"，但 MC 蓝本
（Task2IDFI，经 legacy 编码器验证）在 ACT 时将 **完整行地址** 放入
`dfi.address`，BA 单独走 `dfi.bank`；而真实 DDR4 x16 硅片中 BG0/BA0/BA1
是 **专用引脚**（JESD79-4B §2.8 寻址表 + Table 17 命令真值表：ACT 行的
A[12:0] 列 = 完整 RA，BG/BA 列为独立列），不存在 BA 时间复用 A[2:0] 的机制。
后果：原清单下 bank 信息 **无路可达 DRAM**（功能性死结）。

处置：
1. 新增 `ba` out port（宽度 `dfiBankWidth = bankWidth − bgWidth`，本配置 = 3）。
2. 曾尝试的"A[2:0] 在 ACT/PRE 时复用为 BA"方案被 **否决**：TC8 实测其破坏
   ACT 时的 RA[2:0]（row 0xE123 → 0xE124），与 MC 的完整行编码矛盾。
3. `ca` 的注释相应更正为不含 BA。
4. 静态映射（ADR-0008 的 CA 接线表扩充）：`ba` ← `dfi.bank` 相位片的低
   `dfiBankWidth` 位（MC 约定高位零扩展）；`bg` ← `dfi.bg` 相位片。
