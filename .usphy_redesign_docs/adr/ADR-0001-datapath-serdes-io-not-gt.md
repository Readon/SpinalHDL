# ADR-0001: 数据通路 SerDes 选型 — I/O SerDes，排除 GT/GTP

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q1 = b）

## Context
DDR PHY 的数据通路（DQ/DQS/DM）需要做并串转换以满足 DDR3/4 的每-pin 速率。
候选两条路：
- **I/O SerDes**：OSERDESE3 / ISERDESE3（2x/4x）+ IDELAYE3/ODELAYE3 + bitslip，挂在普通 I/O bank。
- **GT/GTX/GTH transceiver**：独立高速收发器引脚，自带 PLL/CDR/均衡。

用户在口头沟通中提到过“GT/GTP”，需在架构地基处钉死。

## Decision
数据通路（DQ/DQS/DM）**仅用 I/O SerDes**（OSERDESE3/ISERDESE3 + IDELAYE3/ODELAYE3 + bitslip）。
命令/地址/时钟（CA/CK/CKE/CS/ODT）走**并行 I/O + MMCM 生成的 DFI 时钟**，不做并串。
**排除 GT/GTX** 用于 DDR 数据通路。

## Rationale（物理兼容性是一票否决项）
1. GT 的 I/O 是 **ac-coupled 差分 transceiver 引脚**，物理上**无法直连 DDR SDRAM 的 SSTL 差分/单端引脚**（DQ 为 SSTL12/15，DQS 为差分）。要用 GT 必须在中间插入电平转换 + 再定时，面积/复杂度大增且零性能收益。
2. Xilinx 官方 DDR 控制器 IP（MIG）的数据通路即为 **I/O 标准 + OSERDESE3/ISERDESE3**，从不把 GT 接到 DDR。
3. I/O SerDes 4x 模式足以覆盖 DDR4-3200（每 pin 1600 Mbps 差分）量级。

## Consequences
- 数据通路受 I/O bank 可用数量与每-bank 速度等级约束；高总带宽需多 bank 并行堆叠。
- P&R 阶段需确认所选 bank 的速度等级能否闭合 3200 MT/s 的 I/O 时序。
- 旧 `dfi-new:XilinxUSPhy` 把 CA/CK 也塞进 OSERDESE3 的做法（handoff §3 罪状 1/2）与本 ADR 相悖，属根基性错误，弃用。