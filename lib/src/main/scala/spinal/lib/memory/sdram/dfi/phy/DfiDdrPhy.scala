package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._
import spinal.lib.memory.sdram.dfi._
// import spinal.lib.memory.sdram.dfi.phy.interfaces.DfiTrainingInterface

/**
 * DFI接口多标准DDR PHY组件
 *
 * 作为DfiController的slave组件，接收DFI信号并驱动DDR存储器接口。
 * 支持DDR2、DDR3、DDR4以及LPDDR系列存储器标准。
 *
 * 架构组成：
 * - DfiAdapter: DFI信号适配器，接收DfiController的DFI信号
 * - StandardAdapter: 标准适配器，适配不同DDR标准的电气特性
 * - TimingGenerator: 时序生成器，生成精确的DDR时序
 * - DataPath: 数据路径，处理读写数据传输
 * - CalibrationEngine: 校准引擎，执行训练和校准操作
 * - InitializationManager: 初始化管理器，执行DDR初始化序列
 */
case class DfiDdrPhy(config: DfiDdrPhyConfig) extends Component {

  // DFI接口 - 作为slave接收DfiController的信号
  val io = new Bundle {
    val dfi = slave(Dfi(config.dfiConfig))

    // DDR存储器接口
    val sdram = master(DdrInterface(config.sdramConfig))

    // 配置和控制接口 - 暂时注释掉以解决递归定义问题
    // val config = in(DfiDdrPhyConfig(DdrStandard.DDR3, config.dfiConfig, config.sdramConfig))
    val status = out(DfiDdrPhyStatus())

    // 训练接口 - 从内部适配器暴露
    val training = master(DfiTrainingInterface(config.dfiConfig))

    // 测试和调试接口
    val testMode = in Bool() default False
    val debug = out(DfiDdrPhyDebug())
  }

  // 子模块实例化
  val dfiAdapter = DfiAdapter(config.dfiAdapterConfig)
  val standardAdapter = StandardAdapter(config.standardAdapterConfig)
  val timingGenerator = TimingGenerator(config.timingGeneratorConfig)
  val dataPath = DataPath(config.dataPathConfig)
  val calibrationEngine = CalibrationEngine(config.calibrationConfig)
  val initializationManager = InitializationManager(config.initializationConfig)

  // DFI适配器连接
  dfiAdapter.io.dfi <> io.dfi

  // 标准适配器连接
  standardAdapter.io.dfiInternal <> dfiAdapter.io.dfiInternal

  // 时序生成器连接
  timingGenerator.io.command <> standardAdapter.io.command
  // timingGenerator.io.timingConfig <> config.timingConfig

  // 数据路径连接
  dataPath.io.timing <> timingGenerator.io.timing
  dataPath.io.data <> standardAdapter.io.data

  // 校准引擎连接
  calibrationEngine.io.training <> dfiAdapter.io.training
  calibrationEngine.io.calibrationInterface <> dataPath.io.calibration

  // 训练接口连接 - 暴露到顶层
  if(config.features.trainingSupport) {
    io.training <> dfiAdapter.io.training
  }

  // 初始化管理器连接
  initializationManager.io.init <> dfiAdapter.io.init
  initializationManager.io.initializationInterface <> standardAdapter.io.init
  initializationManager.io.sdram <> io.sdram

  // 状态和调试信号连接 - 符合REQ-CS-018：使用直接对象访问
  io.status.initialized := True
  io.status.calibrating := False
  io.status.error := False
  io.status.errorCode := 0
  io.status.temperature := 0
  io.status.frequencyRatio := 0

  io.debug.dfiAdapter := EmptyDebug()
  io.debug.standardAdapter := EmptyDebug()
  io.debug.timingGenerator := EmptyDebug()
  io.debug.dataPath := EmptyDebug()
  io.debug.calibrationEngine := EmptyDebug()
  io.debug.initializationManager := EmptyDebug()
}

/**
 * PHY配置类
 */
case class DfiDdrPhyConfig(
    ddrStandard: DdrStandard.C,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
) {

  // 子模块配置
  val dfiAdapterConfig = DfiAdapterConfig(
    dfiConfig = dfiConfig,
    features = features
  )

  val standardAdapterConfig = StandardAdapterConfig(
    ddrStandard = ddrStandard,
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    features = features
  )

  val timingGeneratorConfig = TimingGeneratorConfig(
    ddrStandard = ddrStandard,
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    timingConfig = timingConfig
  )

  val dataPathConfig = DataPathConfig(
    ddrStandard = ddrStandard,
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    features = features
  )

  val calibrationConfig = CalibrationConfig(
      ddrStandard = ddrStandard,
      dfiConfig = dfiConfig,
      sdramConfig = sdramConfig,
      features = features
  )

  val initializationConfig = InitializationConfig(
      ddrStandard = ddrStandard,
      dfiConfig = dfiConfig,
      sdramConfig = sdramConfig
  )

  // 运行时配置接口
  val timingConfig = TimingConfig(
    tCK = sdramConfig.ddrMHZ,
    tRCD = sdramConfig.tRCD,
    tRP = sdramConfig.tRP,
    tRAS = sdramConfig.tRAS,
    tWR = sdramConfig.tWR,
    tRTP = sdramConfig.tRTP,
    tWTR = sdramConfig.tWTR,
    tREFI = sdramConfig.tREF,
    tRFC = sdramConfig.tRFC
  )
}

/**
 * DDR标准枚举
 */
object DdrStandard extends SpinalEnum {
  val DDR2, DDR3, DDR4, LPDDR2, LPDDR3, LPDDR4 = newElement()
}

/**
 * PHY特性配置
 */
case class DfiDdrPhyFeatures(
    dbiSupport: Boolean = true,
    crcSupport: Boolean = true,
    caParitySupport: Boolean = true,
    lowPowerSupport: Boolean = true,
    trainingSupport: Boolean = true,
    debugSupport: Boolean = true
)

/**
 * PHY状态接口
 */
case class DfiDdrPhyStatus() extends Bundle {
  val initialized = Bool()
  val calibrating = Bool()
  val error = Bool()
  val errorCode = Bits(8 bits)
  val temperature = Bits(8 bits)
  val frequencyRatio = Bits(2 bits)
}

/**
 * PHY调试接口
 */
case class DfiDdrPhyDebug() extends Bundle {
  val dfiAdapter = EmptyDebug()
  val standardAdapter = EmptyDebug()
  val timingGenerator = EmptyDebug()
  val dataPath = EmptyDebug()
  val calibrationEngine = EmptyDebug()
  val initializationManager = EmptyDebug()
}

/**
 * 工厂方法：创建DDR3 PHY
 */
object DfiDdrPhy {
  def ddr3(
      chipSelectNumber: Int = 1,
      dataWidth: Int = 16,
      sdramConfig: SdramConfig,
      features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
  ): DfiDdrPhy = {

    val dfiConfig = DfiConfig(
      chipSelectNumber = chipSelectNumber,
      dataSlice = dataWidth / 8,
      signalConfig = DfiSignalConfig.DDR3(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 1,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 5,
        tPhyRdlat = 6,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = sdramConfig
    )

    DfiDdrPhy(DfiDdrPhyConfig(
      ddrStandard = DdrStandard.DDR3,
      dfiConfig = dfiConfig,
      sdramConfig = sdramConfig,
      features = features
    ))
  }

  /**
   * 工厂方法：创建DDR4 PHY
   */
  def ddr4(
      chipSelectNumber: Int = 1,
      dataWidth: Int = 16,
      sdramConfig: SdramConfig,
      features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
  ): DfiDdrPhy = {

    val dfiConfig = DfiConfig(
      chipSelectNumber = chipSelectNumber,
      dataSlice = dataWidth / 8,
      signalConfig = DfiSignalConfig.DDR4(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 1,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 5,
        tPhyRdlat = 6,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = sdramConfig
    )

    DfiDdrPhy(DfiDdrPhyConfig(
      ddrStandard = DdrStandard.DDR4,
      dfiConfig = dfiConfig,
      sdramConfig = sdramConfig,
      features = features
    ))
  }
}