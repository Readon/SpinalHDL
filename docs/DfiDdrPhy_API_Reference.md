# DfiDdrPhy API 参考文档

## 类层次结构

```
spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy
├── DfiDdrPhyConfig
├── DdrStandard
├── DfiDdrPhyFeatures
├── DfiDdrPhyStatus
└── DfiDdrPhyDebug
```

## DfiDdrPhy 类

### 构造函数

```scala
case class DfiDdrPhy(config: DfiDdrPhyConfig) extends Component
```

### 工厂方法

#### ddr3

```scala
def ddr3(
  chipSelectNumber: Int = 1,
  dataWidth: Int = 16,
  sdramConfig: SdramConfig,
  features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
): DfiDdrPhy
```

**参数:**
- `chipSelectNumber`: 芯片选择数量 (默认: 1)
- `dataWidth`: 数据宽度，位 (默认: 16)
- `sdramConfig`: SDRAM 配置
- `features`: PHY 特性配置 (默认: 默认特性)

**返回值:** 配置为 DDR3 的 DfiDdrPhy 实例

#### ddr4

```scala
def ddr4(
  chipSelectNumber: Int = 1,
  dataWidth: Int = 16,
  sdramConfig: SdramConfig,
  features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
): DfiDdrPhy
```

**参数:**
- `chipSelectNumber`: 芯片选择数量 (默认: 1)
- `dataWidth`: 数据宽度，位 (默认: 16)
- `sdramConfig`: SDRAM 配置
- `features`: PHY 特性配置 (默认: 默认特性)

**返回值:** 配置为 DDR4 的 DfiDdrPhy 实例

### 接口

#### io.dfi

```scala
val dfi = slave(Dfi(config.dfiConfig))
```

DFI slave 接口，接收来自 DfiController 的 DFI 信号。

**类型:** `Dfi`

#### io.sdram

```scala
val sdram = master(DdrInterface(config.sdramConfig))
```

DDR 存储器 master 接口，驱动 DDR 存储器信号。

**类型:** `DdrInterface`

#### io.status

```scala
val status = out(DfiDdrPhyStatus())
```

PHY 状态输出接口。

**类型:** `DfiDdrPhyStatus`

#### io.training

```scala
val training = master(DfiTrainingInterface(config.dfiConfig))
```

训练接口，用于与外部训练逻辑通信。

**类型:** `DfiTrainingInterface`

#### io.debug

```scala
val debug = out(DfiDdrPhyDebug())
```

调试接口，提供内部状态信息。

**类型:** `DfiDdrPhyDebug`

## 配置类

### DfiDdrPhyConfig

```scala
case class DfiDdrPhyConfig(
  ddrStandard: DdrStandard.C,
  dfiConfig: DfiConfig,
  sdramConfig: SdramConfig,
  features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
)
```

#### 字段

- `ddrStandard`: DDR 标准选择
- `dfiConfig`: DFI 接口配置
- `sdramConfig`: SDRAM 配置
- `features`: PHY 特性配置

### DdrStandard

```scala
object DdrStandard extends SpinalEnum {
  val DDR2, DDR3, DDR4, LPDDR2, LPDDR3, LPDDR4 = newElement()
}
```

DDR 标准枚举。

**值:**
- `DDR2`: DDR2 SDRAM
- `DDR3`: DDR3 SDRAM
- `DDR4`: DDR4 SDRAM
- `LPDDR2`: LPDDR2 SDRAM
- `LPDDR3`: LPDDR3 SDRAM
- `LPDDR4`: LPDDR4 SDRAM

### DfiDdrPhyFeatures

```scala
case class DfiDdrPhyFeatures(
  dbiSupport: Boolean = true,
  crcSupport: Boolean = true,
  caParitySupport: Boolean = true,
  lowPowerSupport: Boolean = true,
  trainingSupport: Boolean = true,
  debugSupport: Boolean = true
)
```

PHY 特性配置。

#### 字段

- `dbiSupport`: DBI (Data Bus Inversion) 支持 (默认: true)
- `crcSupport`: CRC (Cyclic Redundancy Check) 支持 (默认: true)
- `caParitySupport`: CA (Command Address) 奇偶校验支持 (默认: true)
- `lowPowerSupport`: 低功耗模式支持 (默认: true)
- `trainingSupport`: 训练功能支持 (默认: true)
- `debugSupport`: 调试功能支持 (默认: true)

## 状态和调试接口

### DfiDdrPhyStatus

```scala
case class DfiDdrPhyStatus() extends Bundle {
  val initialized = Bool()
  val calibrating = Bool()
  val error = Bool()
  val errorCode = Bits(8 bits)
  val temperature = Bits(8 bits)
  val frequencyRatio = Bits(2 bits)
}
```

PHY 状态接口。

#### 字段

- `initialized`: 初始化完成标志
- `calibrating`: 校准进行中标志
- `error`: 错误状态标志
- `errorCode`: 错误代码 (8 位)
- `temperature`: 温度信息 (8 位)
- `frequencyRatio`: 当前频率比 (2 位)

### DfiDdrPhyDebug

```scala
case class DfiDdrPhyDebug() extends Bundle {
  val dfiAdapter = EmptyDebug()
  val standardAdapter = EmptyDebug()
  val timingGenerator = EmptyDebug()
  val dataPath = EmptyDebug()
  val calibrationEngine = EmptyDebug()
  val initializationManager = EmptyDebug()
}
```

调试接口，提供各子模块的调试信息。

#### 字段

- `dfiAdapter`: DFI 适配器调试信息
- `standardAdapter`: 标准适配器调试信息
- `timingGenerator`: 时序生成器调试信息
- `dataPath`: 数据路径调试信息
- `calibrationEngine`: 校准引擎调试信息
- `initializationManager`: 初始化管理器调试信息

## 相关配置类

### DfiConfig

```scala
case class DfiConfig(
  chipSelectNumber: Int,
  dataSlice: Int,
  signalConfig: DfiSignalConfig,
  timeConfig: DfiTimeConfig,
  sdram: SdramConfig
)
```

DFI 接口配置。

#### 字段

- `chipSelectNumber`: 芯片选择数量
- `dataSlice`: 数据片数量
- `signalConfig`: 信号配置
- `timeConfig`: 时间配置
- `sdram`: SDRAM 配置

### DfiTimeConfig

```scala
case class DfiTimeConfig(
  frequencyRatio: Int,
  cmdPhase: Int,
  tPhyWrLat: Int,
  tPhyWrData: Int,
  tPhyWrCsLat: Int,
  tPhyWrCsGap: Int,
  tRddataEn: Int,
  tPhyRdlat: Int,
  tPhyRdCslat: Int,
  tPhyRdCsGap: Int
)
```

DFI 时间配置。

#### 字段

- `frequencyRatio`: 频率比 (1, 2, 或 4)
- `cmdPhase`: 命令相位
- `tPhyWrLat`: 写延迟
- `tPhyWrData`: 写数据延迟
- `tPhyWrCsLat`: 写 CS 延迟
- `tPhyWrCsGap`: 写 CS 间隙
- `tRddataEn`: 读数据使能延迟
- `tPhyRdlat`: 读延迟
- `tPhyRdCslat`: 读 CS 延迟
- `tPhyRdCsGap`: 读 CS 间隙

### SdramConfig

```scala
case class SdramConfig(
  generation: SdramGeneration,
  bgWidth: Int,
  cidWidth: Int,
  bankWidth: Int,
  columnWidth: Int,
  rowWidth: Int,
  dataWidth: Int,
  ddrMHZ: Int,
  ddrWrLat: Int,
  ddrRdLat: Int,
  sdramtime: SdramTiming
)
```

SDRAM 配置。

#### 字段

- `generation`: SDRAM 代系
- `bgWidth`: Bank Group 宽度
- `cidWidth`: Chip ID 宽度
- `bankWidth`: Bank 地址宽度
- `columnWidth`: 列地址宽度
- `rowWidth`: 行地址宽度
- `dataWidth`: 数据宽度
- `ddrMHZ`: DDR 时钟频率 (MHz)
- `ddrWrLat`: 写延迟
- `ddrRdLat`: 读延迟
- `sdramtime`: SDRAM 时序参数

## 常量和枚举

### 错误代码

```scala
object DfiDdrPhyErrorCodes {
  val NO_ERROR = 0x00
  val INIT_TIMEOUT = 0x01
  val CALIBRATION_FAILED = 0x02
  val SIGNAL_INTEGRITY_ERROR = 0x03
  val TEMPERATURE_WARNING = 0x04
  val TEMPERATURE_CRITICAL = 0x05
  val VOLTAGE_ERROR = 0x06
  val CONFIGURATION_ERROR = 0x07
}
```

### 频率比常量

```scala
object DfiFrequencyRatio {
  val RATIO_1_1 = 1
  val RATIO_1_2 = 2
  val RATIO_1_4 = 4
}
```

## 方法和函数

### 配置验证

```scala
def validateConfig(config: DfiDdrPhyConfig): Boolean
```

验证配置参数的正确性。

**参数:**
- `config`: 要验证的配置

**返回值:** 配置是否有效

### 性能监控

```scala
def getPerformanceMetrics(): DfiDdrPhyMetrics
```

获取性能指标。

**返回值:** 性能指标对象

### 诊断函数

```scala
def runBuiltInSelfTest(): DfiDdrPhyTestResult
```

运行内置自测试。

**返回值:** 测试结果

## 异常和错误

### DfiDdrPhyException

```scala
class DfiDdrPhyException(message: String, errorCode: Int)
  extends Exception(message)
```

PHY 相关异常。

### 配置异常

```scala
class DfiDdrPhyConfigException(message: String)
  extends DfiDdrPhyException(message, CONFIGURATION_ERROR)
```

配置错误异常。

### 运行时异常

```scala
class DfiDdrPhyRuntimeException(message: String, errorCode: Int)
  extends DfiDdrPhyException(message, errorCode)
```

运行时错误异常。

## 兼容性

### SpinalHDL 版本

- 支持 SpinalHDL 1.7.0+

### Scala 版本

- 支持 Scala 2.12.x
- 支持 Scala 2.13.x

### FPGA 工具链

- Xilinx Vivado
- Intel Quartus
- 其他支持 SpinalHDL 的工具链

## 性能特性

### 资源使用 (Xilinx Kintex-7, DDR3-1600)

- LUT: ~5000-8000
- FF: ~3000-5000
- BRAM: 2-4 (取决于配置)
- DSP: 0

### 时序要求

- 目标频率: 200-400 MHz (取决于配置)
- 建立时间裕量: >0.5ns
- 保持时间裕量: >0.3ns

## 版本历史

### v1.0.0

- 初始版本
- 支持 DDR3/DDR4
- 基本 DFI 3.1 功能

### v1.1.0

- 添加 LPDDR 支持
- 改进训练算法
- 增强错误处理

### v1.2.0

- 添加多芯片支持
- 改进性能监控
- 扩展调试功能

## 参考资料

- [DFI 3.1 规范](https://www.dfi.org/)
- [JEDEC DDR 标准](https://www.jedec.org/)
- SpinalHDL 文档