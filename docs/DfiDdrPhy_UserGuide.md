# DfiDdrPhy 用户指南

## 概述

DfiDdrPhy 是 SpinalHDL 中用于 DDR 存储器物理层的组件，支持多种 DDR 标准（DDR2、DDR3、DDR4、LPDDR2、LPDDR3、LPDDR4）。该组件作为 DfiController 的 slave，实现 DFI 3.1 接口协议。

## 主要特性

- **多标准支持**: 支持 DDR2/3/4 和 LPDDR 系列标准
- **DFI 3.1 合规**: 完整实现 DFI 3.1 接口组
- **频率比支持**: 支持 1:1、1:2、1:4 频率比
- **多芯片选择**: 支持多个芯片选择 (CS)
- **高级功能**: 支持 DBI、CRC、CA 奇偶校验
- **训练支持**: 集成写电平校准、读校准、CA 训练

## 基本使用

### DDR3 PHY 创建

```scala
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy

val phy = DfiDdrPhy.ddr3(
  chipSelectNumber = 1,    // 芯片选择数量
  dataWidth = 16,          // 数据宽度
  sdramConfig = sdramConfig // SDRAM 配置
)
```

### DDR4 PHY 创建

```scala
val phy = DfiDdrPhy.ddr4(
  chipSelectNumber = 1,
  dataWidth = 16,
  sdramConfig = sdramConfig
)
```

### 通用 PHY 创建

```scala
val phy = new DfiDdrPhy(DfiDdrPhyConfig(
  ddrStandard = DdrStandard.LPDDR3,  // DDR 标准
  dfiConfig = dfiConfig,              // DFI 配置
  sdramConfig = sdramConfig,          // SDRAM 配置
  features = DfiDdrPhyFeatures(       // 特性配置
    dbiSupport = true,
    crcSupport = true,
    caParitySupport = true
  )
))
```

## 接口说明

### DFI 接口 (Slave)

```scala
val dfi = slave(Dfi(dfiConfig))
```

DFI 接口包含以下接口组：
- **控制接口**: 命令和地址信号
- **写数据接口**: 写数据和掩码
- **读数据接口**: 读数据和有效信号
- **更新接口**: 状态更新信号
- **低功耗接口**: 低功耗控制信号
- **训练接口**: 训练相关信号

### SDRAM 接口 (Master)

```scala
val sdram = master(DdrInterface(sdramConfig))
```

SDRAM 接口根据选择的 DDR 标准提供相应的信号。

### 状态接口

```scala
val status = out(DfiDdrPhyStatus())
```

状态接口提供：
- `initialized`: 初始化完成状态
- `calibrating`: 校准进行中状态
- `error`: 错误状态
- `errorCode`: 错误代码
- `temperature`: 温度信息
- `frequencyRatio`: 当前频率比

### 训练接口

```scala
val training = master(DfiTrainingInterface(dfiConfig))
```

训练接口用于与外部训练逻辑通信。

## 配置选项

### DfiDdrPhyConfig

```scala
case class DfiDdrPhyConfig(
  ddrStandard: DdrStandard.C,           // DDR 标准选择
  dfiConfig: DfiConfig,                 // DFI 配置
  sdramConfig: SdramConfig,             // SDRAM 配置
  features: DfiDdrPhyFeatures = DfiDdrPhyFeatures() // 特性配置
)
```

### DfiDdrPhyFeatures

```scala
case class DfiDdrPhyFeatures(
  dbiSupport: Boolean = true,           // DBI 支持
  crcSupport: Boolean = true,           // CRC 支持
  caParitySupport: Boolean = true,      // CA 奇偶校验支持
  lowPowerSupport: Boolean = true,      // 低功耗支持
  trainingSupport: Boolean = true,      // 训练支持
  debugSupport: Boolean = true          // 调试支持
)
```

## 与 DfiController 集成

```scala
val dfiController = DfiController(
  bmbp = bmbParameter,
  task = taskParameter,
  dfiConfig = dfiConfig,
  addrMap = RowBankColumn
)

val phy = DfiDdrPhy.ddr3(
  chipSelectNumber = 1,
  dataWidth = 16,
  sdramConfig = sdramConfig
)

// 连接 DFI 接口
phy.io.dfi <> dfiController.io.dfi
```

## 频率比配置

PHY 支持不同的 DFI 频率比：

- **1:1**: DFI 时钟频率等于存储器时钟频率
- **1:2**: DFI 时钟频率是存储器时钟频率的 1/2
- **1:4**: DFI 时钟频率是存储器时钟频率的 1/4

```scala
val dfiConfig = DfiConfig(
  // ... 其他配置
  timeConfig = DfiTimeConfig(
    frequencyRatio = 2,  // 1:2 频率比
    // ... 其他时间配置
  )
)
```

## 多芯片选择

支持多个芯片选择 (CS)：

```scala
val phy = DfiDdrPhy.ddr3(
  chipSelectNumber = 4,  // 4 个芯片选择
  dataWidth = 16,
  sdramConfig = sdramConfig
)
```

## 调试和监控

### 调试接口

```scala
val debug = out(DfiDdrPhyDebug())
```

调试接口提供各子模块的状态信息。

### 状态监控

```scala
when(phy.io.status.error) {
  // 处理错误
  val errorCode = phy.io.status.errorCode
  // ... 错误处理逻辑
}
```

## 最佳实践

### 1. 配置验证

在使用 PHY 之前，确保所有配置参数正确：

```scala
assert(dfiConfig.chipSelectNumber > 0)
assert(sdramConfig.dataWidth % 8 == 0)
assert(dfiConfig.timeConfig.frequencyRatio >= 1 && dfiConfig.timeConfig.frequencyRatio <= 4)
```

### 2. 时序约束

确保为 DFI 和 SDRAM 接口添加适当的时序约束：

```scala
// 在顶层模块中添加时序约束
addAttribute("keep", phy.io.dfi)
addAttribute("keep", phy.io.sdram)
```

### 3. 复位和初始化

正确处理复位和初始化序列：

```scala
val resetCtrl = new ResetController
resetCtrl.addReset(phy.io.reset, duration = 10)

// 等待初始化完成
when(phy.io.status.initialized) {
  // 开始正常操作
}
```

### 4. 错误处理

实现错误检测和处理：

```scala
when(phy.io.status.error) {
  // 记录错误
  errorLogger.log(phy.io.status.errorCode)

  // 可选：触发复位或降级模式
  when(errorIsRecoverable(phy.io.status.errorCode)) {
    resetSequence.start()
  } otherwise {
    enterSafeMode()
  }
}
```

## 故障排除

### 常见问题

1. **编译错误**: 检查所有配置参数的类型和范围
2. **时序违规**: 验证时钟域和时序约束
3. **接口不匹配**: 确保 DFI 配置与控制器匹配
4. **初始化失败**: 检查 SDRAM 配置和电源时序

### 调试技巧

1. 使用调试接口监控内部状态
2. 添加信号探针观察关键信号
3. 使用仿真验证配置正确性
4. 检查时序报告中的违规

## 性能优化

### 1. 资源优化

- 根据需要启用/禁用特性
- 优化数据宽度和芯片选择数量
- 使用合适的频率比减少接口复杂度

### 2. 时序优化

- 正确设置延迟参数
- 使用适当的流水线
- 优化时钟域交叉

### 3. 功耗优化

- 启用低功耗模式
- 使用时钟门控
- 优化训练频率

## 版本兼容性

- SpinalHDL 1.7.0+
- 支持 Scala 2.12/2.13
- 兼容主流 FPGA 和 ASIC 流程

## 参考资料

- [DFI 3.1 规范](https://www.dfi.org/)
- [JEDEC DDR 标准](https://www.jedec.org/)
- SpinalHDL 文档