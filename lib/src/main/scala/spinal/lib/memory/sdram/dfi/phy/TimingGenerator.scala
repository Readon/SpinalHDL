package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 时序生成器
 *
 * 负责生成精确的DDR时序，处理频率比转换，
 * 管理命令时序约束。
 */
case class TimingGenerator(config: TimingGeneratorConfig) extends Component {

  val io = new Bundle {
    // 命令输入
    val command = slave(DdrCommandInterface(config.sdramConfig))

    // 时序输出
    val timing = master(DdrTimingInterface(config.sdramConfig))

    // 调试接口
    val debug = out(TimingGeneratorDebug())
  }

  // 时序参数寄存器
  val timingRegs = TimingRegs(config)
  timingRegs.io.config.tCK := U(config.timingConfig.tCK, 16 bits)
  timingRegs.io.config.tRCD := U(config.timingConfig.tRCD, 8 bits)
  timingRegs.io.config.tRP := U(config.timingConfig.tRP, 8 bits)
  timingRegs.io.config.tRAS := U(config.timingConfig.tRAS, 8 bits)
  timingRegs.io.config.tWR := U(config.timingConfig.tWR, 8 bits)
  timingRegs.io.config.tRTP := U(config.timingConfig.tRTP, 8 bits)
  timingRegs.io.config.tWTR := U(config.timingConfig.tWTR, 8 bits)
  timingRegs.io.config.tREFI := U(config.timingConfig.tREFI, 16 bits)
  timingRegs.io.config.tRFC := U(config.timingConfig.tRFC, 8 bits)

  // 命令时序控制器
  val commandTimingController = CommandTimingController(config)
  commandTimingController.io.command <> io.command
  commandTimingController.io.timingParams <> timingRegs.io.params

  // 时序接口生成器
  val timingInterfaceGenerator = TimingInterfaceGenerator(config)
  timingInterfaceGenerator.io.commandTiming <> commandTimingController.io.timing
  io.timing <> timingInterfaceGenerator.io.timing

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandTimingController.io.debug.commandCount
  io.debug.timingViolationCount := commandTimingController.io.debug.timingViolationCount
}

/**
 * 时序生成器配置
 */
case class TimingGeneratorConfig(
    ddrStandard: DdrStandard.C,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    timingConfig: TimingConfig
)


/**
 * 时序寄存器
 */
case class TimingRegs(config: TimingGeneratorConfig) extends Area {

  val io = new Bundle {
    val config = TimingConfig(
      tCK = 0,
      tRCD = 0,
      tRP = 0,
      tRAS = 0,
      tWR = 0,
      tRTP = 0,
      tWTR = 0,
      tREFI = 0,
      tRFC = 0
    )
    val params = master(TimingParams())
  }

  // 时序参数寄存器
  val tRCD = Reg(UInt(8 bits)) init 0
  val tRP = Reg(UInt(8 bits)) init 0
  val tRAS = Reg(UInt(8 bits)) init 0
  val tWR = Reg(UInt(8 bits)) init 0
  val tRTP = Reg(UInt(8 bits)) init 0
  val tWTR = Reg(UInt(8 bits)) init 0
  val tREFI = Reg(UInt(16 bits)) init 0
  val tRFC = Reg(UInt(8 bits)) init 0

  // 更新时序参数
  when(True) {
    tRCD := io.config.tRCD
    tRP := io.config.tRP
    tRAS := io.config.tRAS
    tWR := io.config.tWR
    tRTP := io.config.tRTP
    tWTR := io.config.tWTR
    tREFI := io.config.tREFI
    tRFC := io.config.tRFC
  }

  // 输出时序参数
  io.params.tRCD := tRCD
  io.params.tRP := tRP
  io.params.tRAS := tRAS
  io.params.tWR := tWR
  io.params.tRTP := tRTP
  io.params.tWTR := tWTR
  io.params.tREFI := tREFI
  io.params.tRFC := tRFC
}

/**
  * 时序参数接口
  */
case class TimingParams() extends Bundle with IMasterSlave {
  val tRCD = UInt(8 bits)
  val tRP = UInt(8 bits)
  val tRAS = UInt(8 bits)
  val tWR = UInt(8 bits)
  val tRTP = UInt(8 bits)
  val tWTR = UInt(8 bits)
  val tREFI = UInt(16 bits)
  val tRFC = UInt(8 bits)

  override def asMaster(): Unit = {
    out(tRCD, tRP, tRAS, tWR, tRTP, tWTR, tREFI, tRFC)
  }
}

/**
 * 命令时序控制器
 */
case class CommandTimingController(config: TimingGeneratorConfig) extends Area {

  val io = new Bundle {
    val command = slave(DdrCommandInterface(config.sdramConfig))
    val timingParams = slave(TimingParams())
    val timing = master(CommandTimingInterface())
    val debug = out(CommandTimingControllerDebug())
  }

  // 命令时序状态机
  val commandScheduler = CommandScheduler(config)
  commandScheduler.io.command <> io.command
  commandScheduler.io.timingParams <> io.timingParams
  io.timing <> commandScheduler.io.timing

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandScheduler.io.debug.commandCount
  io.debug.timingViolationCount := commandScheduler.io.debug.timingViolationCount
}


/**
 * 命令调度器
 */
case class CommandScheduler(config: TimingGeneratorConfig) extends Area {

  val io = new Bundle {
    val command = slave(DdrCommandInterface(config.sdramConfig))
    val timingParams = slave(TimingParams())
    val timing = master(CommandTimingInterface())
    val debug = out(CommandSchedulerDebug())
  }

  // 命令队列
  val commandQueue = Vec(Reg(DdrCommandInterface(config.sdramConfig)), 4)
  val queueValid = RegInit(Vec(False, False, False, False))
  val queueHead = Reg(UInt(2 bits)) init 0
  val queueTail = Reg(UInt(2 bits)) init 0

  // 命令入队逻辑
  when(io.command.valid && !queueValid(0) && queueHead === 0) {
    commandQueue(0) := io.command
    queueValid(0) := True
  }

  // 命令调度逻辑
  val currentCommand = commandQueue(queueHead)
  val commandScheduled = queueValid(queueHead) && canSchedule(currentCommand.cmd)

  io.timing.scheduled := commandScheduled
  io.timing.ready := !queueValid.orR || commandScheduled
  io.timing.delay := calculateDelay(currentCommand.cmd)
  io.timing.priority := calculatePriority(currentCommand.cmd)

  // 出队逻辑
  when(commandScheduled) {
    queueValid(queueHead) := False
    queueHead := queueHead + 1
  }

  // 命令调度辅助函数
  def canSchedule(cmd: DdrCommand.C): Bool = {
    // 简化的调度逻辑
    cmd.mux(
      DdrCommand.NOP -> True,
      DdrCommand.ACT -> True,
      DdrCommand.READ -> True,
      DdrCommand.WRITE -> True,
      DdrCommand.PRE -> True,
      DdrCommand.REF -> True,
      DdrCommand.MRS -> True,
      DdrCommand.ZQCS -> True
    )
  }

  def calculateDelay(cmd: DdrCommand.C): UInt = {
    // 简化的延迟计算
    cmd.mux(
      DdrCommand.NOP -> U(0),
      DdrCommand.ACT -> io.timingParams.tRCD,
      DdrCommand.READ -> U(0),
      DdrCommand.WRITE -> U(0),
      DdrCommand.PRE -> io.timingParams.tRP,
      DdrCommand.REF -> U(0),
      DdrCommand.MRS -> U(0),
      DdrCommand.ZQCS -> U(0)
    )
  }

  def calculatePriority(cmd: DdrCommand.C): UInt = {
    // 简化的优先级计算
    cmd.mux(
      DdrCommand.NOP -> U(0),
      DdrCommand.ACT -> U(1),
      DdrCommand.READ -> U(2),
      DdrCommand.WRITE -> U(2),
      DdrCommand.PRE -> U(1),
      DdrCommand.REF -> U(3),
      DdrCommand.MRS -> U(4),
      DdrCommand.ZQCS -> U(3)
    )
  }

  // 调试信号
  val schedulerCommandCount = CountOne(Seq(io.command.valid))
  val schedulerTimingViolationCount = U(0, 32 bits)

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := schedulerCommandCount
  io.debug.timingViolationCount := schedulerTimingViolationCount
}

/**
 * 时序接口生成器
 */
case class TimingInterfaceGenerator(config: TimingGeneratorConfig) extends Area {

  val io = new Bundle {
    val commandTiming = slave(CommandTimingInterface())
    val timing = master(DdrTimingInterface(config.sdramConfig))
  }

  // 时序接口信号生成
  io.timing.cmdValid := io.commandTiming.scheduled
  io.timing.cmdReady := io.commandTiming.ready
  io.timing.dataValid := False // 根据数据路径状态设置
  io.timing.dataReady := True
  io.timing.busy := !io.commandTiming.ready
  io.timing.idle := io.commandTiming.ready && !io.commandTiming.scheduled
}

/**
 * 调试接口定义
 */
case class TimingGeneratorDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}

case class CommandTimingControllerDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}

case class CommandSchedulerDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}