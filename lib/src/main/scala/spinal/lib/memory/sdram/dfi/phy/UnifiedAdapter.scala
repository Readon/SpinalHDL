package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._
import spinal.lib.memory.sdram.dfi._

/**
 * 统一适配器层
 *
 * 合并了DfiAdapter和StandardAdapter的功能，统一处理DFI协议解析、
 * 命令转换和标准适配，简化了模块间接口。
 */
case class UnifiedAdapter(config: UnifiedAdapterConfig) extends Component {

  val io = new Bundle {
    // DFI接口 - slave接收DfiController信号
    val dfi = slave(Dfi(config.dfiConfig))

    // 内部接口 - 提供给DataManager和ControlManager
    val internal = master(UnifiedInternalInterface(config.dfiConfig))

    // 调试接口
    val debug = out(UnifiedAdapterDebug())
  }

  // DFI命令解析器 - 合并DFI解析逻辑
  val commandParser = UnifiedCommandParser(config, io.dfi.control, io.dfi.write, io.dfi.read)

  // 标准适配器 - 处理DDR标准特定的转换
  val standardAdapter = UnifiedStandardAdapter(config, commandParser.parsedCommand, commandParser.parsedData)

  // 训练接口处理器 - 统一训练信号处理
  val trainingProcessor = UnifiedTrainingProcessor(config, io.dfi.rdTraining, io.dfi.wrTraining, io.dfi.caTraining)

  // 初始化接口处理器 - 处理DFI状态和更新信号
  val initProcessor = UnifiedInitProcessor(config, io.dfi.status, io.dfi.update)

  // 内部接口连接 - 统一输出到内部模块
  io.internal.command << standardAdapter.command
  io.internal.data << standardAdapter.data
  io.internal.training.readTraining.req := trainingProcessor.training.readReq
  io.internal.training.writeTraining.req := trainingProcessor.training.writeReq
  io.internal.training.caTraining.req := trainingProcessor.training.caReq
  trainingProcessor.training.readResp := io.internal.training.readTraining.resp
  trainingProcessor.training.writeResp := io.internal.training.writeTraining.resp
  trainingProcessor.training.caResp := io.internal.training.caTraining.resp
  io.internal.init << initProcessor.init

  // DFI读输出由XilinxUSPhy驱动，避免重复赋值冲突
  // 注释掉重复的赋值以避免ASSIGNMENT OVERLAP错误

  // 调试信号聚合 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandParser.debug.commandCount
  io.debug.dataCount := commandParser.debug.dataCount
  io.debug.trainingCount := {
    val reqs = Seq.newBuilder[Bool]
    if (config.dfiConfig.useRdlvlReq) reqs += io.dfi.rdTraining.rdlvlReq.orR
    if (config.dfiConfig.useWrlvlReq) reqs += io.dfi.wrTraining.wrlvlReq.orR
    if (config.dfiConfig.useCalvlReq) reqs += io.dfi.caTraining.calvlReq.orR
    CountOne(reqs.result()).resize(DfiCommonConstants.DEBUG_COUNTER_WIDTH)
  }
  io.debug.errorCount := commandParser.debug.errorCount
}

/**
 * 统一适配器配置
 */
case class UnifiedAdapterConfig(
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    ddrStandard: DdrStandard.E,
    features: DfiDdrPhyFeatures
)

/**
 * 统一命令解析器
 * 合并DFI命令解析和数据解析功能
 */
case class UnifiedCommandParser(config: UnifiedAdapterConfig,
                                dfiControl: DfiControlInterface,
                                dfiWrite: DfiWriteInterface,
                                dfiRead: DfiReadInterface) extends Area {

  // 输出信号 - directionless
  val parsedCommand = UnifiedParsedCommand(config.dfiConfig)
  val parsedData = UnifiedParsedData(config.dfiConfig)
  val debug = UnifiedCommandParserDebug()

  // 命令解析逻辑
  val cmd = DdrCommand()
  val addr = UInt(config.dfiConfig.addressWidth bits)
  val bank = UInt(config.dfiConfig.bankWidth bits)

  // 简化的命令解码
  val rasN = dfiControl.rasN.orR ? B"0" | B"1"
  val casN = dfiControl.casN.orR ? B"0" | B"1"
  val weN = dfiControl.weN.orR ? B"0" | B"1"

  when(rasN.orR && casN.orR && weN.orR) {
    cmd := DdrCommand.NOP
  } elsewhen(!rasN.orR && casN.orR && weN.orR) {
    cmd := DdrCommand.ACT
  } elsewhen(rasN.orR && !casN.orR && weN.orR) {
    cmd := DdrCommand.READ
  } elsewhen(rasN.orR && !casN.orR && !weN.orR) {
    cmd := DdrCommand.WRITE
  } elsewhen(!rasN.orR && !casN.orR && weN.orR) {
    cmd := DdrCommand.PRE
  } otherwise {
    cmd := DdrCommand.NOP
  }

  // 输出解析结果
  parsedCommand.valid := True
  parsedCommand.command := cmd
  parsedCommand.address := dfiControl.address.asUInt.resized
  parsedCommand.bank := dfiControl.bank.orR ? dfiControl.bank.asUInt | U"0"
  parsedCommand.chipSelect := dfiControl.csN.asUInt(log2Up(config.dfiConfig.chipSelectNumber) - 1 downto 0)

  // 数据解析 - 根据dataSlice配置处理数据
  parsedData.writeValid := dfiWrite.wr.map(_.wrdataEn).reduce(_ || _)
  // 当dataSlice=1时，只使用第一个数据片段；否则拼接所有片段
  val rawWriteData = if (config.dfiConfig.dataSlice == 1) dfiWrite.wr.head.wrdata else dfiWrite.wr.map(_.wrdata).reduce(_ ## _)
  parsedData.writeData := rawWriteData(config.sdramConfig.dataWidth - 1 downto 0)
  val rawWriteMask = if (config.dfiConfig.dataSlice == 1) dfiWrite.wr.head.wrdataMask else dfiWrite.wr.map(_.wrdataMask).reduce(_ ## _)
  parsedData.writeMask := rawWriteMask(config.sdramConfig.dataWidth / 8 - 1 downto 0)
  
  val rawReadData = if (config.dfiConfig.dataSlice == 1) dfiRead.rd.head.rddata else dfiRead.rd.map(_.rddata).reduce(_ ## _)
  parsedData.readData := rawReadData(config.sdramConfig.dataWidth - 1 downto 0)
  parsedData.readValid := dfiRead.rd.map(rd => if (config.dfiConfig.useRddataDnv) rd.rddataDnv.orR else False).reduce(_ || _)

  // Debug signals - REQ-CS-018 compliance: direct object access using common constants
  debug.commandCount := CountOne(Seq(parsedCommand.valid)).resize(DfiCommonConstants.DEBUG_COUNTER_WIDTH)
  debug.dataCount := CountOne(Seq(parsedData.writeValid, parsedData.readValid)).resize(DfiCommonConstants.DEBUG_COUNTER_WIDTH)
  debug.errorCount := U(0, DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
}

/**
 * 统一标准适配器
 * 处理DDR标准特定的命令和数据转换
 */
case class UnifiedStandardAdapter(config: UnifiedAdapterConfig,
                                  parsedCommand: UnifiedParsedCommand,
                                  parsedData: UnifiedParsedData) extends Area {

  // 输出信号 - directionless
  val command = DdrCommandInterface(config.sdramConfig)
  val data = DdrDataInterface(config.sdramConfig)

  // 命令转换 - 根据DDR标准调整
  command.valid := parsedCommand.valid
  command.cmd := parsedCommand.command
  command.addr := parsedCommand.address.asBits
  command.ba := parsedCommand.bank.asBits
  command.autoPrecharge := False
  command.burstLength := B"3'b000" // BL8

  // 数据接口直通 - 标准特定的处理可以在这里添加
  data.write.valid := parsedData.writeValid
  data.write.data := parsedData.writeData
  data.write.mask := parsedData.writeMask
  data.write.last := False // 根据需要设置
  data.write.dqs := 0 // 根据DDR标准生成
  data.write.dqs_n := 0

  parsedData.readReady := data.read.ready
  data.read.data := parsedData.readData
  data.read.valid := parsedData.readValid
  data.read.last := False // 根据需要设置
  data.read.dqs := 0 // 从DDR接口接收
  data.read.dqs_n := 0
}

/**
 * 统一训练处理器
 */
case class UnifiedTrainingProcessor(config: UnifiedAdapterConfig,
                                    dfiRdTraining: DfiReadTrainingInterface,
                                    dfiWrTraining: DfiWriteTrainingInterface,
                                    dfiCaTraining: DfiCATrainingInterface) extends Area {

  // 输出信号 - directionless
  val training = UnifiedTrainingInterface(config.dfiConfig)
  val debug = UnifiedTrainingProcessorDebug()

  // 训练信号聚合 - 使用条件生成检查
  if (config.dfiConfig.useRdlvlReq) {
    training.readReq := dfiRdTraining.rdlvlReq.orR
  } else {
    training.readReq := False
  }
  if (config.dfiConfig.useWrlvlReq) {
    training.writeReq := dfiWrTraining.wrlvlReq.orR
  } else {
    training.writeReq := False
  }
  if (config.dfiConfig.useCalvlReq) {
    training.caReq := dfiCaTraining.calvlReq.orR
  } else {
    training.caReq := False
  }

  // 训练响应信号 - 使用条件生成检查
  // if (config.dfiConfig.useRdlvlResp) {
  //   training.readResp := dfiRdTraining.rdlvlResp.orR ? dfiRdTraining.rdlvlResp | B"0"
  // } else {
  //   training.readResp := B"0".resized
  // }
  // if (config.dfiConfig.useWrlvlResp) {
  //   training.writeResp := dfiWrTraining.wrlvlResp.orR ? dfiWrTraining.wrlvlResp | B"0"
  // } else {
  //   training.writeResp := B"0".resized
  // }
  // if (config.dfiConfig.useCalvlResp) {
  //   training.caResp := dfiCaTraining.calvlResp.orR ? dfiCaTraining.calvlResp | B"0"
  // } else {
  //   training.caResp := B"0".resized
  // }

  // Debug signals - REQ-CS-018 compliance: direct object access using common constants
  debug.trainingCount := CountOne(Seq(training.readReq, training.writeReq, training.caReq)).resize(DfiCommonConstants.DEBUG_COUNTER_WIDTH)
  debug.errorCount := U(0, DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
}

/**
 * 统一初始化处理器
 */
case class UnifiedInitProcessor(config: UnifiedAdapterConfig,
                                dfiStatus: DfiStatusInterface,
                                dfiUpdate: DfiUpdateInterface) extends Area {

  // 输出信号 - directionless
  val init = DdrInitInterface(config.sdramConfig)

  // 初始化信号处理 - 使用条件生成检查
  if (config.dfiConfig.useInitStart) {
    init.initStart := dfiStatus.initStart
    // init.initComplete := dfiStatus.initComplete
  } else {
    init.initStart := False
    // init.initComplete := True
  }
  init.powerUp := True
  init.modeRegisterSet := Vec(False, False, False, False)
  init.zqCalibration := False
  if (config.dfiConfig.useFreqRatio) {
    init.freqRatio := dfiStatus.freqRatio.resized
  } else {
    init.freqRatio := B"0".resized
  }
  if (config.dfiConfig.useStatusSignals) {
    init.dramClkDisable := dfiStatus.dramClkDisable.resized
  } else {
    init.dramClkDisable := B"0".resized
  }
}

/**
 * 统一内部接口定义
 */
case class UnifiedInternalInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val command = DdrCommandInterface(config.sdram)
  val data = DdrDataInterface(config.sdram)
  val training = DfiTrainingInterface(config)
  val init = DdrInitInterface(config.sdram)

  override def asMaster(): Unit = {
    master(command, data, training, init)
  }

  def <<(that: UnifiedInternalInterface): Unit = {
    this.command << that.command
    this.data << that.data
    this.training << that.training
    this.init << that.init
  }
  def >>(that: UnifiedInternalInterface): Unit = that << this
}

/**
 * 统一解析命令接口
 */
case class UnifiedParsedCommand(config: DfiConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val command = DdrCommand()
  val address = UInt(config.addressWidth bits)
  val bank = UInt(config.bankWidth bits)
  val chipSelect = UInt(log2Up(config.chipSelectNumber) bits)

  override def asMaster(): Unit = {
    out(valid, command, address, bank, chipSelect)
  }

  def <<(that: UnifiedParsedCommand): Unit = {
    this.valid := that.valid
    this.command := that.command
    this.address := that.address
    this.bank := that.bank
    this.chipSelect := that.chipSelect
  }
  def >>(that: UnifiedParsedCommand): Unit = that << this
}

/**
 * 统一解析数据接口
 */
case class UnifiedParsedData(config: DfiConfig) extends Bundle with IMasterSlave {
   val writeValid = Bool()
   val writeData = Bits(config.sdram.dataWidth bits)
   val writeMask = Bits(config.sdram.dataWidth / 8 bits)
   val readReady = Bool()
   val readData = Bits(config.sdram.dataWidth bits)
   val readValid = Bool()

  override def asMaster(): Unit = {
    out(writeValid, writeData, writeMask, readReady)
    in(readData, readValid)
  }

  def <<(that: UnifiedParsedData): Unit = {
    this.writeValid := that.writeValid
    this.writeData := that.writeData
    this.writeMask := that.writeMask
    this.readReady := that.readReady
    that.readData := this.readData
    that.readValid := this.readValid
  }
  def >>(that: UnifiedParsedData): Unit = that << this
}

/**
 * 统一命令接口
 */
case class UnifiedCommandInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val cmd = DdrCommand()
  val addr = Bits(config.addressWidth bits)
  val ba = Bits(config.bankWidth bits)
  val autoPrecharge = Bool()
  val burstLength = Bits(3 bits)

  override def asMaster(): Unit = {
    out(valid, cmd, addr, ba, autoPrecharge, burstLength)
  }

  def <<(that: UnifiedCommandInterface): Unit = {
    this.valid := that.valid
    this.cmd := that.cmd
    this.addr := that.addr
    this.ba := that.ba
    this.autoPrecharge := that.autoPrecharge
    this.burstLength := that.burstLength
  }
  def >>(that: UnifiedCommandInterface): Unit = that << this
}

/**
 * 统一数据接口
 */
case class UnifiedDataInterface(config: DfiConfig) extends Bundle with IMasterSlave {
   val writeValid = Bool()
   val writeData = Bits(config.sdram.dataWidth bits)
   val writeMask = Bits(config.sdram.dataWidth / 8 bits)
   val readReady = Bool()
   val readData = Bits(config.sdram.dataWidth bits)
   val readValid = Bool()

  override def asMaster(): Unit = {
    out(writeValid, writeData, writeMask, readReady)
    in(readData, readValid)
  }

  def <<(that: UnifiedDataInterface): Unit = {
    this.writeValid := that.writeValid
    this.writeData := that.writeData
    this.writeMask := that.writeMask
    this.readReady := that.readReady
    that.readData := this.readData
    that.readValid := this.readValid
  }
  def >>(that: UnifiedDataInterface): Unit = that << this
}

/**
 * 统一训练接口
 */
case class UnifiedTrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val readReq = Bool()
  val writeReq = Bool()
  val caReq = Bool()
  val readResp = Bits(config.readLevelingResponseWidth bits)
  val writeResp = Bits(config.writeLevelingResponseWidth bits)
  val caResp = Bits(config.caTrainingResponseWidth bits)

  override def asMaster(): Unit = {
    out(readReq, writeReq, caReq)
    in(readResp, writeResp, caResp)
  }

  def <<(that: UnifiedTrainingInterface): Unit = {
    this.readReq := that.readReq
    this.writeReq := that.writeReq
    this.caReq := that.caReq
    that.readResp := this.readResp
    that.writeResp := this.writeResp
    that.caResp := this.caResp
  }
  def >>(that: UnifiedTrainingInterface): Unit = that << this
}

/**
 * 调试接口定义
 */
case class UnifiedAdapterDebug() extends Bundle {
  val commandCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val dataCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val trainingCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val errorCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
}

case class UnifiedCommandParserDebug() extends Bundle {
  val commandCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val dataCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val errorCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
}

case class UnifiedTrainingProcessorDebug() extends Bundle {
  val trainingCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val errorCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
}