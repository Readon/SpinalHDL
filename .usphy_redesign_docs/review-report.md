# XilinxUSPhy 设计评审报告

> 评审对象：`dfi-new` 分支 `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`（1875 行）
> 配套文件：`IO.scala`（OSERDESE3/ISERDESE3/ODELAYE3/IDELAYE3 黑盒）、`XilinxUSPhyTester.scala`
> 评审日期：2026-09-14
> 评审人：digital-rtl agent
> 结论：**弃用，另起炉灶。** 四大根基性错误 + 两处伪造完成度，不可在旧地基上迭代。

---

## 1. 命令/地址被串行化（根基性错误）

**位置**：L636-656（`oserverdesVec` 循环）

**现象**：把 RAS/CAS/WE/A/BA/CS/CKE/ODT 每个信号各塞进一颗 OSERDESE3，串行化为数据流送出。

```scala
// L636-656
val oserdesVec = Seq.fill(handler.signals.length)(new OSERDESE3())
val odelayVec  = Seq.fill(handler.signals.length)(new ODELAYE3(...))
for (((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex) {
    serdes.D := handler.signalMappings(i).dfiSource  // 每个 CA 信号单独串行
    ...
}
```

**为什么错**：DDR3/DDR4 的 CA/命令引脚是**同步并行总线**——每个 DRAM 时钟沿（上升+下降）采样一组完整的 {RA, BA, CS, CKE, RAS, CAS, WE, ODT, ACT, BG, CID} 电平组合。JEDEC 定义的命令 = 一个时钟沿上所有 CA 引脚的电平快照（JESD79-4B Table 37 "Command Truth Table"；JESD79-3 Table 31 同理）。**不存在"把每个 CA 信号独立串行化"的接口。**

**后果**：PHY 自造了 JEDEC 不存在的接口形态。即使 SerDes 时序正确，DRAM 看到的也不是合法的命令——因为命令是"同一沿上多引脚的组合"，拆开串行后丢失了组合关系。

**正确做法**（ADR-0001/0008）：CA/命令/时钟走**并行 I/O 引脚**，不做并串。只有数据通路（DQ/DQS/DM）才用 I/O SerDes。

---

## 2. 时钟发生器错（根基性错误）

**位置**：L171-200（`clockGen`）

**现象**：把 8bit 时钟图案送 OSERDESE3 → ODELAYE3 → OBUFDS，当作 DDR 的 CK 输出。

```scala
// L171-200
val serdes = new OSERDESE3()
serdes.D := clkPattern  // 8bit 图案
val delay = new ODELAYE3(delayType = "VARIABLE")
delay.ODATAIN := serdes.OQ
val buf = new OBUFDS()
buf.I := delay.DATAOUT & clkGated
io.pads.clk_p := buf.O
io.pads.clk_n := buf.OB
```

**为什么错**：DDR 的 CK 是一对**干净的差分时钟**（CK_t/CK_c），由 MMCM/PLL 产生，频率精确等于 DRAM 目标频率。它**不是数据流**——不需要 SerDes 串行化。把时钟当数据流串行化：
1. 引入 SerDes 打拍延迟（非确定性），破坏 CK 的相位精度。
2. ODELAYE3 的延时分辨率（~ps 级）远粗于 MMCM 的频率精度，无法保证 CK 频率正确。
3. OBUFDS 输出的是"被串行化再延时的脉冲"，不是稳定正弦/方波时钟。

**JEDEC 引用**：JESD79-4B §2.7 "CK_t, CK_c — Clock: CK_t and CK_c are differential clock inputs. All address and control input signals are sampled on the crossing..."——CK 是稳定的差分时钟输入，不是串行数据。

**正确做法**（ADR-0006）：PHY 顶层仅 `refclk` 入，内部 MMCM 派生 DRAM CK/CKE（干净差分时钟），不经 SerDes。

---

## 3. DDR 命令编码全错（根基性错误）

**位置**：L537-583（`switch(cmdGen.currentCmd)` 三段）

**现象**：把 RAS/CAS/WE 当**互斥选择**——每个命令只置低其中一个信号：

```scala
// L537-583 (简化)
switch(cmdGen.currentCmd) {
  is(ACT, PRE, REF, MRS, ZQCS) { rasN := 0 }  // 这些命令 RAS=0
  default { rasN := 1 }
}
switch(cmdGen.currentCmd) {
  is(READ, WRITE, REF, MRS, ZQCS) { casN := 0 }  // 这些命令 CAS=0
  default { casN := 1 }
}
switch(cmdGen.currentCmd) {
  is(WRITE, PRE, REF, MRS, ZQCS) { weN := 0 }  // 这些命令 WE=0
  default { weN := 1 }
}
```

**为什么错**：DDR3/DDR4 的命令编码是 **RAS/CAS/WE 三者的电平组合**，不是互斥选择。JESD79-3 Table 31 / JESD79-4B Table 37 定义了每种命令的 {RAS, CAS, WE} 组合：

| 命令 | RAS_n | CAS_n | WE_n | (DDR4 ACT_n) |
|------|-------|-------|------|--------------|
| ACTIVATE | 0 | 0 | 0 | 0 |
| READ | 1 | 1 | 0 | 1 |
| WRITE | 1 | 0 | 1 | 1 |
| PRECHARGE (single) | 0 | 1 | 1 | 1 |
| REFRESH | 0 | 0 | 1 | 1 |
| MRS | 0 | 0 | 0 | 1 |
| NOP | 1 | 1 | 1 | 1 |

按上面的代码逻辑：
- ACT 得到 RAS=0, CAS=1, WE=1 → 这是 **PRECHARGE**，不是 ACTIVATE。
- READ 得到 RAS=1, CAS=0, WE=1 → 这是 **WRITE**，不是 READ。
- MRS 得到 RAS=0, CAS=0, WE=0 → 这是 **ACTIVATE**（DDR3）或合法 MRS（DDR4 需 ACT_n=1）。

**另外**：L614-616 凭空造了 `act_n` pad（DDR3 无此引脚，仅 DDR4 有）；L537 的编码完全不区分 DDR3 vs DDR4。

**JEDEC 引用**：JESD79-3 Table 31（DDR3 Command Truth Table）；JESD79-4B Table 37（DDR4 Command Truth Table）。

**正确做法**（ADR-0008）：命令编码归 MC。PHY 收到的 DFI 命令已是编码好的，只做 per-phase 透传 + 静态 CA 引脚映射。

---

## 4. 训练是伪造的（最严重）

### 4.1 Write Leveling 自我赋值恒真

**位置**：L1138-1141

```scala
when(start && strobe) {
    receivedPattern := expectedPattern  // ← 期望直接赋给"接收"
    patternMatch := receivedPattern === expectedPattern  // ← 恒 TRUE
}
```

**为什么错**：`receivedPattern` 从未从任何物理引脚（DQS 回采）采样，而是直接被赋值为 `expectedPattern`。随后的比较 `receivedPattern === expectedPattern` **永远是 TRUE**。这意味着：
- 写 leveling 永远"成功"，无论实际 DQS 对齐如何。
- 从未真正采样 DQ/DQS 引脚。
- 这是**自欺式完美对齐**——违反团队红线"不发明协议语义 / 不写校准通过"。

**JEDEC 引用**：JESD79-4B §4.17 "Write Leveling"：PHY 必须通过 DRAM 的 DQS 回采路径（feedback mechanism）检测 DQS 与 DQ 的对齐，并调整发送延迟直到对齐。L2701："both byte lanes should be leveled independently. Therefore, a separate feedback mechanism should be available for each byte lane."

**正确做法**（ADR-0002）：RTL 内含真实的 DQS 回采闭环（ISERDESE3 采集 DQS 回采 → 比较 → 调 ODELAYE3）。

### 4.2 Read Gate / Read Eye / CA — 接收寄存器从未赋值

**位置**：L1173（ReadGate）、L1250（ReadEye）、L1349（CA）

```scala
// L1173 (ReadGate)
val receivedData = Reg(Bits(8 bits)) init(0)  // ← 从未从 ISERDESE3/DQ 采样
val expectedPattern = B"01010101"
val patternValid = receivedData === expectedPattern  // ← 比较无意义的值
```

**为什么错**：`receivedData` 是 `Reg(Bits(8 bits)) init(0)`，**在整个文件中从未被赋值**（没有 `:=` 连接到 ISERDESE3 输出或任何数据源）。仿真中它一直是初始值 0（或 X），综合中浮空。拿一个从未更新的寄存器和 expected 比较，得到的"门眼检测结果"是**纯噪声**。

**正确做法**（ADR-0002）：第一版读训练为显式 stub，但必须做成"接口完整 + 反馈可注入 + 标注 UNVERIFIED_ON_BOARD"，绝不自我赋值恒真。

---

## 5. 资源监控是假的

**位置**：L1707-1744

```scala
val activeLuts = Counter(32 bits, inc = enableResourceMonitoring)  // ← "track LUT usage"
val activeFfs  = Counter(32 bits, inc = enableResourceMonitoring)  // ← "track FF usage"
```

**为什么错**：用运行时 `Counter` 假装测量 LUT/FF 占用。LUT/FF 是**综合后**的资源统计，在 RTL 仿真中不存在"当前使用了多少个 LUT"的概念。这个 counter 只是在计数时钟周期，跟 LUT/FF 毫无关系。`tasks.md` 的 8.1/8.2（"Optimize LUT/FF usage"）全靠这几行凑数。

**正确做法**：资源优化是 P&R 阶段的工作，RTL 里无法也不应该"监控 LUT 用量"。

---

## 6. 初始化魔数

**位置**：L1553-1571

```scala
initAddr := B"16'h0520"  // MR0: "BL8, CL5, DLL Reset"
initAddr := B"16'h0004"  // MR1: "Enable DLL, AL=0, RttNom=60ohm"
initAddr := B"16'h0008"  // MR2: "CWL=5, RttWR=60ohm"
initBa := B(2, ...)       // ← 用 bank 字段"选 MR2"
```

**为什么错**：
1. **MR 值不是合法 JEDEC 编码**：DDR4 MR0 的 bit 布局是 [BL8:0, WR:1, RTT_NOM:5:2, RD:2:0, ...]。`0x0520` 解出来不是合理的 CL/CWL/RTT 组合（需要对照 JESD79-4B Table 43-48 逐 bit 验证）。
2. **用 `initBa` 选 MR 是概念错位**：MR 编号来自**地址 A2A0**（不是 bank 字段）。`initBa := B(2)` 试图"用 bank 选 MR2"，但 DRAM 根本不这么工作。
3. **MR 写入顺序错**：JEDEC 规定 MR3 最先写（MPR 配置），然后 MR6/MR5/MR4/MR2/MR1，最后 MR0（DLL reset）。MC 侧 `Ddr4InitSequencer` 的顺序是 MR3→MR6→MR5→MR4→MR2→MR1→MR0→ZQCL，这里是乱的。

**JEDEC 引用**：JESD79-4B §4.8 "Initialization" + Table 43-48（MR bit definitions）；JESD79-4B Figure 136（initialization sequence）。

**正确做法**（ADR-0010）：初始化序列由 MC 的 `Ddr4InitSequencer` 产生，PHY 透传。PHY 不持有 MR 表。

---

## 7. 其它硬伤

| 位置 | 问题 | 说明 |
|------|------|------|
| L854 | 写数据字节索引算术错 | byte lane 到 DQ 的映射偏移计算有误 |
| L928 | 动态表达式给 Vec 下标 | SpinalHDL 不支持 runtime index，综合时报错 |
| L989 | 多 rank 映射到同一批 byte lane | 读路由错——多个 rank 的读数据混在同一组 DQ 上无 CS 区分 |
| L1605-1658 | 两级 FF 瞬态不一致当"CDC 错误" | 正常的多拍传播被误报为跨时钟域违规 |
| L1697 | `initError` 条件恒 False | 永远不会触发，虚假的"错误检测" |

---

## 8. 假的多规格

**声称**：1:1/1:2/1:4 frequency ratio + 多 rank + 多代际。

**实际**：只有一套（错的）DDR3 命令路径。所谓"多规格"是 config 参数换了名字但逻辑完全相同——没有 per-generation 的命令编码切换、没有 per-ratio 的相位管理、没有 per-rank 的 CS 路由。

---

## 总结

| 类别 | 数量 | 可否修补 |
|------|------|----------|
| 根基性错误（接口形态/时钟/编码） | 3 | ❌ 需推倒重来 |
| 伪造完成度（训练/资源监控） | 2 | ❌ 需重写 |
| 概念错位（init/多规格） | 2 | ❌ 需重写 |
| 局部硬伤（索引/Vec/CDC） | 5 | ⚠️ 理论上可修，但地基已烂 |

**结论**：不在旧文件上迭代。在 monster 上另起新文件，复用 MC 侧已验证的编码事实（ADR-0003/0008），按 ADR-0001~0012 搭建正确架构。
