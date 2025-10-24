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
 * - UnifiedAdapter: 统一适配器，合并DFI协议解析和标准适配功能
 * - DataManager: 数据管理器，统一管理时序控制和数据流
 * - ControlManager: 控制管理器，统一管理DDR初始化、校准和训练状态机
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

  // 子模块实例化 - 新的3模块架构
  val unifiedAdapter = UnifiedAdapter(config.unifiedAdapterConfig)
  val dataManager = DataManager(config.dataManagerConfig)
  val controlManager = ControlManager(config.controlConfig)

  // UnifiedAdapter连接
  unifiedAdapter.io.dfi << io.dfi

  // DataManager连接
  dataManager.io.command << unifiedAdapter.io.internal.command
  dataManager.io.data << unifiedAdapter.io.internal.data
  
  // 转换TimingConfig为TimingParams
  val timingParams = TimingParams()
  timingParams.tRCD := config.timingConfig.tRCD
  timingParams.tRP := config.timingConfig.tRP
  timingParams.tRAS := config.timingConfig.tRAS
  timingParams.tWR := config.timingConfig.tWR
  timingParams.tRTP := config.timingConfig.tRTP
  timingParams.tWTR := config.timingConfig.tWTR
  timingParams.tREFI := config.timingConfig.tREFI
  timingParams.tRFC := config.timingConfig.tRFC
  dataManager.io.timingConfig := timingParams

  // ControlManager连接
  controlManager.io.training << unifiedAdapter.io.internal.training
  controlManager.io.init << unifiedAdapter.io.internal.init
  controlManager.io.calibrationInterface << dataManager.io.calibration

  // DDR接口连接 - 从ControlManager输出
  io.sdram << controlManager.io.sdram

  // 训练接口连接 - 暴露到顶层
  if(config.features.trainingSupport) {
    io.training << unifiedAdapter.io.internal.training
  }

  // 状态和调试信号连接 - 符合REQ-CS-018：使用直接对象访问
  io.status.initialized := True
  io.status.calibrating := False
  io.status.error := False
  io.status.errorCode := 0
  io.status.temperature := 0
  io.status.frequencyRatio := 0

  io.debug.unifiedAdapter := unifiedAdapter.io.debug
  io.debug.dataManager := dataManager.io.debug
  io.debug.controlManager := controlManager.io.debug
}

/**
 * PHY配置类
 */
case class DfiDdrPhyConfig(
    ddrStandard: DdrStandard.E,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures = DfiDdrPhyFeatures()
) {

  // 子模块配置 - 新的3模块架构
  val unifiedAdapterConfig = UnifiedAdapterConfig(
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    ddrStandard = ddrStandard,
    features = features
  )

  val dataManagerConfig = DataManagerConfig(
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    features = features,
    timingConfig = timingConfig,
    ddrStandard = ddrStandard
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

  val controlConfig = ControlConfig(
      ddrStandard = ddrStandard,
      dfiConfig = dfiConfig,
      sdramConfig = sdramConfig,
      features = features
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
  val unifiedAdapter = UnifiedAdapterDebug()
  val dataManager = DataManagerDebug()
  val controlManager = ControlManagerDebug()
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