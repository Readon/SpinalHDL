package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._
import spinal.lib.memory.sdram.dfi._

/**
 * 数据管理器
 *
 * 合并了TimingGenerator和DataPath的功能，统一管理时序控制和数据流。
 * 负责命令调度、时序参数管理、数据缓冲和格式转换。
 */
case class DataManager(config: DataManagerConfig) extends Component {

  val io = new Bundle {
    // 命令输入 - 来自UnifiedAdapter
    val command = slave(DdrCommandInterface(config.sdramConfig))

    // 数据输入输出 - 来自UnifiedAdapter
    val data = slave(DdrDataInterface(config.sdramConfig))

    // 时序配置输入 - 运行时可配置的硬件寄存器
    val timingConfig = slave(TimingParams())

    // DDR接口输出
    val ddr = master(DdrInterface(config.sdramConfig))

    // 校准接口 - 从DataPath合并
    val calibration = master(DdrCalibrationInterface(config.sdramConfig))

    // 调试接口
    val debug = out(DataManagerDebug())
  }

  // 时序控制器 - 管理命令时序和调度
  val timingController = TimingController(config, io.command, io.timingConfig)

  // 数据处理器 - 管理数据流和缓冲
  val dataProcessor = DataProcessor(config, io.data, timingController.timing)

  // DDR接口生成器 - 生成最终的DDR信号
  val ddrInterfaceGenerator = DdrInterfaceGenerator(config, timingController.timingCommand, dataProcessor.processedData)
  io.ddr << ddrInterfaceGenerator.ddr

  // 校准接口连接 - 从DataPath合并
  io.calibration := dataProcessor.calibration

  // 调试信号聚合
  io.debug.commandCount := timingController.debug.commandCount
  io.debug.dataCount := dataProcessor.debug.dataCount
  io.debug.timingViolationCount := timingController.debug.timingViolationCount
}

/**
 * 数据管理器配置
 */
case class DataManagerConfig(
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures,
    timingConfig: TimingConfig,
    ddrStandard: DdrStandard.E = DdrStandard.DDR3
)

/**
 * 时序控制器
 * 负责命令调度和时序管理
 */
case class TimingController(config: DataManagerConfig,
                            command: DdrCommandInterface,
                            timingConfig: TimingParams) extends Area {

  // 输出信号 - directionless
  val timing = DdrTimingInterface(config.sdramConfig)
  val timingCommand = DdrCommandInterface(config.sdramConfig)
  val debug = TimingControllerDebug()

  // 时序参数管理 - 直接使用输入的硬件接口
  // timingConfig已经是硬件Bundle，包含所有时序参数

  // 命令调度器
  val scheduler = DataManagerCommandScheduler(config, command, timingConfig)
  timing << scheduler.timing
  timingCommand << scheduler.scheduledCommand

  // 调试信号
  debug.commandCount := scheduler.debug.commandCount
  debug.timingViolationCount := scheduler.debug.timingViolationCount
}

/**
 * 数据处理器
 * 负责数据缓冲、格式转换和流控制
 */
case class DataProcessor(config: DataManagerConfig,
                         data: DdrDataInterface,
                         timing: DdrTimingInterface) extends Area {

  // 输出信号 - directionless
  val processedData = DdrDataInterface(config.sdramConfig)
  val calibration = DdrCalibrationInterface(config.sdramConfig)
  val debug = DataProcessorDebug()

  // 写数据缓冲器
  val writeBuffer = StreamFifo(
    dataType = DdrWriteDataInterface(config.sdramConfig),
    depth = 16
  )

  // 写数据格式转换
  writeBuffer.io.push.payload.valid := data.write.valid
  writeBuffer.io.push.payload.data := data.write.data
  writeBuffer.io.push.payload.mask := data.write.mask
  writeBuffer.io.push.payload.last := data.write.last
  writeBuffer.io.push.payload.dqs := data.write.dqs
  writeBuffer.io.push.payload.dqs_n := data.write.dqs_n
  writeBuffer.io.push.valid := data.write.valid

  // 读数据缓冲器
  val readBuffer = StreamFifo(
    dataType = DdrReadDataInterface(config.sdramConfig),
    depth = 16
  )

  // 读数据格式转换
  data.read.ready := readBuffer.io.push.ready
  readBuffer.io.push.payload.data := data.read.data
  readBuffer.io.push.payload.valid := data.read.valid
  readBuffer.io.push.payload.last := data.read.last
  readBuffer.io.push.payload.dqs := data.read.dqs
  readBuffer.io.push.payload.dqs_n := data.read.dqs_n
  readBuffer.io.push.valid := data.read.valid

  // 输出到DDR接口
  processedData.write.valid := writeBuffer.io.pop.valid && timing.dataReady
  processedData.write.data := writeBuffer.io.pop.payload.data
  processedData.write.mask := writeBuffer.io.pop.payload.mask
  processedData.write.last := writeBuffer.io.pop.payload.last
  processedData.write.dqs := writeBuffer.io.pop.payload.dqs
  processedData.write.dqs_n := writeBuffer.io.pop.payload.dqs_n
  // 避免组合环路：使用寄存器来缓冲valid信号
  val writeValidReg = Reg(Bool()) init False
  writeValidReg := processedData.write.valid
  writeBuffer.io.pop.ready := writeValidReg

  // 修复组合环路：processedData.read.ready 应该是外部输入，表示下游准备好接收数据
  // readBuffer.io.pop.ready 基于时序控制和下游准备状态
  processedData.read.data := readBuffer.io.pop.payload.data
  processedData.read.valid := readBuffer.io.pop.valid && timing.dataValid
  processedData.read.last := readBuffer.io.pop.payload.last
  processedData.read.dqs := readBuffer.io.pop.payload.dqs
  processedData.read.dqs_n := readBuffer.io.pop.payload.dqs_n
  // 避免组合环路：使用寄存器来缓冲ready信号
  val readReadyReg = Reg(Bool()) init False
  readReadyReg := processedData.read.ready
  readBuffer.io.pop.ready := readReadyReg && timing.dataValid

  // 数据格式转换器 - 从DataPath合并
  // TODO: 需要实现DataFormatter和DataPathConfig
  // 暂时简化处理，直接连接数据流

  // 调试信号
  debug.dataCount := CountOne(Seq(data.write.valid, data.read.valid)).resize(32)
}

/**
 * DDR接口生成器
 * 生成最终的DDR物理接口信号
 */
case class DdrInterfaceGenerator(config: DataManagerConfig,
                                 timingCommand: DdrCommandInterface,
                                 processedData: DdrDataInterface) extends Area {

  // 输出信号 - directionless
  val ddr = DdrInterface(config.sdramConfig)

  // 时钟信号生成
  ddr.clk := ClockDomain.current.readClockWire
  ddr.clk_n := !ClockDomain.current.readClockWire

  // 控制信号生成
  ddr.cke(0) := True // 默认使能
  ddr.cs_n(0) := !timingCommand.valid
  ddr.ras_n := True
  ddr.cas_n := True
  ddr.we_n := True
  ddr.addr := 0
  ddr.ba := B"0".resized // 默认bank地址为0，使用resized确保位宽匹配

  // 根据命令设置控制信号
  when(timingCommand.valid) {
    switch(timingCommand.cmd) {
      is(DdrCommand.NOP) {
        // NOP - 所有信号为高
      }
      is(DdrCommand.ACT) {
        ddr.ras_n := False
        ddr.addr := timingCommand.addr
        ddr.ba := timingCommand.ba
      }
      is(DdrCommand.READ) {
        ddr.cas_n := False
        ddr.we_n := True
        ddr.addr := timingCommand.addr
        ddr.ba := timingCommand.ba
      }
      is(DdrCommand.WRITE) {
        ddr.cas_n := False
        ddr.we_n := False
        ddr.addr := timingCommand.addr
        ddr.ba := timingCommand.ba
      }
      is(DdrCommand.PRE) {
        ddr.ras_n := False
        ddr.we_n := False
        ddr.addr := timingCommand.addr
        ddr.ba := timingCommand.ba
      }
      is(DdrCommand.REF) {
        ddr.ras_n := False
        ddr.cas_n := False
        ddr.addr := timingCommand.addr
        ddr.ba := timingCommand.ba
      }
      is(DdrCommand.MRS) {
        ddr.ras_n := False
        ddr.cas_n := False
        ddr.we_n := False
        ddr.addr := timingCommand.addr
        ddr.ba := timingCommand.ba
      }
    }
  }

  // 数据信号连接
  ddr.dq := processedData.write.data
  ddr.dqs := processedData.write.dqs
  ddr.dqs_n := processedData.write.dqs_n
  ddr.dm := processedData.write.mask

  // 其他信号默认值 - 使用条件生成
  if (config.sdramConfig.bgWidth > 0) {
    ddr.bg := B"0"
  }
  if (config.sdramConfig.cidWidth > 0) {
    ddr.cid := B"0"
  }
  // ODT和reset_n信号总是存在的，根据需要设置
  ddr.odt := B"0"
  ddr.reset_n := True
}

/**
 * 数据管理器命令调度器
 * 负责命令的时序调度和冲突检测
 */
case class DataManagerCommandScheduler(config: DataManagerConfig,
                                       command: DdrCommandInterface,
                                       timingParams: TimingParams) extends Area {

  // 输出信号 - directionless
  val timing = DdrTimingInterface(config.sdramConfig)
  val scheduledCommand = DdrCommandInterface(config.sdramConfig)
  val debug = CommandSchedulerDebug()

  // 命令队列
  val commandQueue = Vec(Reg(DdrCommandInterface(config.sdramConfig)), 4)
  val queueValid = RegInit(Vec(False, False, False, False))
  val queueHead = Reg(UInt(2 bits)) init 0

  // 命令入队
  when(command.valid && !queueValid(0)) {
    commandQueue(0) := command
    queueValid(0) := True
  }

  // 命令调度
  val currentCommand = commandQueue(queueHead)
  val canSchedule = queueValid(queueHead) && checkTimingConstraints(currentCommand)

  timing.cmdValid := canSchedule
  // timing.cmdReady := !queueValid.orR || canSchedule
  timing.dataValid := False // 根据数据状态设置
  // timing.dataReady := True
  timing.busy := False // 简化处理，避免组合环路
  timing.idle := !canSchedule

  // 调度命令输出
  scheduledCommand.valid := canSchedule
  scheduledCommand.assignUnassignedByName(currentCommand)

  // 出队逻辑
  when(canSchedule) {
    queueValid(queueHead) := False
    queueHead := queueHead + 1
  }

  // 时序约束检查
  def checkTimingConstraints(cmd: DdrCommandInterface): Bool = {
    // 简化的时序检查 - 可以根据需要扩展
    cmd.valid
  }

  // 调试信号
  debug.commandCount := CountOne(Seq(command.valid)).resize(32)
  debug.timingViolationCount := U(0, 32 bits)
}

/**
 * 调试接口定义
 */
case class DataManagerDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val dataCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}

case class TimingControllerDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}

case class DataProcessorDebug() extends Bundle {
  val dataCount = UInt(32 bits)
}

case class CommandSchedulerDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}