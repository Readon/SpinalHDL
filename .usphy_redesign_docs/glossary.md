# Glossary — XilinxUSPhy 重构

> 术语表的单一事实来源。新增术语在此登记，正文引用统一用语义名。

- **I/O SerDes** — OSERDESE3 / ISERDESE3（2x/4x）+ IDELAYE3/ODELAYE3 + bitslip。
  挂在普通 I/O bank 上的并串转换器，本 PHY 数据通路（DQ/DQS/DM）的唯一 SerDes 载体。
  见 ADR-0001。
- **GT / GTX / GTH transceiver** — Xilinx 独立高速收发器，ac-coupled 差分引脚，自带
  PLL/CDR/均衡，面向 PCIe/以太网（数十 Gbps）。**不适用于 DDR**（I/O 物理层不兼容
  SSTL DDR 引脚）。本重构中排除。见 ADR-0001。
- **DFI (DDR PHY Interface)** — MC ↔ PHY 之间的标准接口。本 PHY 对上即 DFI，对接
  `feat/ddr4-support` 的 DfiController。
- **frequencyRatio** — DFI 时钟 : DRAM 时钟之比，取值 1:1 / 1:2 / 1:4。决定每周期
  承载的 DDR 事件数与相位管理复杂度。本项目先 1:2，后 1:4。
- **写 leveling (write leveling)** — 对齐 DQS 与 DQ 的写相位，依赖 **DQS 回采**闭环。
  本 PHY 做真实闭环（档 B）。见 ADR-0002。
- **读 gate / read eye (read training)** — 读训练的两阶段：gate 捕获有效窗口起点，
  eye 扫描最优采样点。第一版为显式 stub（档 A），标注 `UNVERIFIED_ON_BOARD`。
- **UNVERIFIED_ON_BOARD** — 标记某段逻辑“仿真接口合规、但未在上板/完整 SDRAM 模型下
  验证”，其“通过”不具备物理可信度。用于读训练第一版。
- **编码事实 (encoding facts)** — MC 侧 `feat/ddr4-support` 已双源核对的 DDR4 命令编码、
  MR 表、timing 常数。PHY 必须**复用**这套事实（单一事实来源），不得另编。见 ADR-0003。
- **并行 I/O 命令/时钟路径** — CA/CK/CKE/CS/ODT 不走 SerDes，用并行 I/O 引脚 + MMCM
  生成的 DFI 时钟驱动。与“数据通路 I/O SerDes”相对。见 ADR-0001。
- **MMCM** — Mixed-Mode Clock Manager，生成 DFI 时钟及各 PHY 内部时钟域。| SerDes 倍率 | OSERDESE3/ISERDESE3 的 2x/4x 串行加速比。由代际/速率自动推导，不作对外参数（ADR-0004） |
| cycle-accurate 仿真 | SerDes 打拍/延迟按 cycle 精确建模的验证层级，写 leveling 闭环须在此层级正确（ADR-0005） |
| 自产时钟 | PHY 顶层仅 refclk 入，内部 MMCM 派生 DRAM CK/CKE + SerDes clk + DFI clk（ADR-0006） |

- **DFI 6.0.1** — 本项目 DFI 接口所遵循的 spec 版本（MC 代码注释引用 Table 12/47/48/55）。
- **命令编码归 MC** — DFI control 总线携带的是*已编码命令*；PHY 只做 per-phase 传输 + 静态 CA 引脚映射，不做编码/解码/仲裁（ADR-0008）。

- **byte lane (octet)** — DDR4 数据通路的原子单位 = 8 DQ + 1 DQS 差分对 + 1 DM/DBI。
  x16 = 2 个 byte lane，x32 = 4 个 byte lane。写 leveling 按 byte lane 独立（JESD79-4B
  L2701：“both byte lanes should be leveled independently … separate feedback mechanism
  for each byte lane”；L11929 “for each DQS_t/DQS_c pair in case of x16”）。
  注意：**“subchannel” 不是 JEDEC 术语**（全文 0 次出现），勿用。
- **CS_n（per-rank）** — 片选按 **rank** 计，是命令编码的一部分（JESD79-4B L713：“CS_n
  provides for external Rank selection … CS_n is considered part of the command code”）。
  x32 ballout 仅有 CS_n（+ CS1_n 留给 3DS 叠 die）。DFI 侧 `csN = Bits(chipSelectNumber ×
  frequencyRatio)`（DFI.scala:13）印证：按 chip(rank) 计宽、按 phase 细分，**与 dataWidth 无关**。
- **64-bit ≠ 单器件** — DDR4 器件只有 x4/x8/x16/x32，**无 x64**。系统级 64-bit 接口 =
  2 个物理 x32 channel（2 套 CA/CK/CS/ODT 命令总线），是拓扑翻倍，不是 dataWidth 旋钮。
- **单 DFI 出口 / 单物理 channel** — MC 的 DfiController 只暴露一个 `io.dfi = master(Dfi(cfg))`。MakeTask “both channels” 指 ctrlmsg(DDR4) vs legacy-wrlvl(DDR2/3) 两条**训练传输通道**，非物理 channel。

## 全通路测试词汇（8c⑫，2026-11-01）

- **全通路测试 (full-path test)** — 激励自最上层接口（BMB）注入、穿越 MC→DFI→PHY→
  引脚→行为模型的全链路，断言落在两端可见的物理字节与协议事件上（区别于仅 MC 级
  的 28/28 或仅 DFI 级的 TC10/11/12）。TC13 为第一个全通路压力 TB。
- **source（BMB 源）** — BMB 事务的发起者编号（`BmbCmd.source`，宽度 `bmbp.sourceWidth`）。
  MC 支持多源并发并允许**乱序应答**（同一源的连续事务可被后发事务的 rsp 超车）；
  请求方靠 `context` 字段自行配对。TC13 用 2 源共享地址空间强制交织。
- **背压饱和 (backpressure saturation)** — 故意长时间拉低 `bmb_rsp.ready` 验证 MC 的
  rsp 排队能力与最终排空（无死锁、无丢失/重复、吞吐>0）。TC13 取保守窗口
  （100 refclk 拍 ≪ rsp 缓冲寿命）；长于缓冲寿命属滥用（触发 MC 侧 isStall assert）。
- **带掩码写 (masked write)** — `BmbCmd.mask` 部分置位的写：仅启用字节落存储，禁用字节
  保留旧值。端到端语义链：BMB mask → MC `wrdataMask` → PHY DM 脚（非 DBI 模式下
  DM = 写字节使能，JEDEC 正确用法）→ 模型按字节选择吸收。
- **DM 双语义** — DDR4 DM/DBI 引脚的双重角色：DBI 模式下为数据反码指示（逻辑 1 =
  正常极性），非 DBI 模式下为写字节使能。UsPhy（`UsPhy.scala:435` 附近）与
  Ddr4SdramModel（`USE_WRDBI` 参数）均以同一开关选择语义；全掩码/DBI 恒 1 时两种
  语义退化为同一行为（向后兼容依据）。
