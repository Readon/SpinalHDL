package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 标准适配器层
 *
 * 负责适配不同DDR标准的电气特性和协议要求，
 * 处理标准特定的命令编码和电源状态转换。
 */
case class StandardAdapter(config: StandardAdapterConfig) extends Component {

  val io = new Bundle {
    // 内部DFI接口
    val dfiInternal = slave(DfiInternal(config.dfiConfig))

    // 标准接口 - 提供给时序生成器
    val standardInterface = master(DdrStandardInterface(config.sdramConfig))

    // 命令接口 - 输出到时序生成器
    val command = master(DdrCommandInterface(config.sdramConfig))

    // 数据接口 - 输出到数据路径
    val data = master(DdrDataInterface(config.sdramConfig))

    // 初始化接口 - 输出到初始化管理器
    val init = master(DdrInitInterface(config.sdramConfig))

    // 调试接口
    val debug = out(StandardAdapterDebug())
  }

  // 标准特定的命令编码器
  val commandEncoder = DdrCommandEncoder(config)
  commandEncoder.io.dfiCommand := io.dfiInternal.command
  io.command := commandEncoder.io.command

  // 数据适配器
  val dataAdapter = DdrDataAdapter(config)
  dataAdapter.io.dfiWrite := io.dfiInternal.write
  dataAdapter.io.dfiRead := io.dfiInternal.read
  io.data := dataAdapter.io.data

  // 标准接口生成器
  val standardInterfaceGenerator = DdrStandardInterfaceGenerator(config)
  standardInterfaceGenerator.io.command := io.command
  standardInterfaceGenerator.io.data := io.data
  io.standardInterface := standardInterfaceGenerator.io.standardInterface

  // 初始化接口生成器
  val initInterfaceGenerator = DdrInitInterfaceGenerator(config)
  initInterfaceGenerator.io.dfiInternal := io.dfiInternal
  io.init := initInterfaceGenerator.io.init

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandEncoder.io.debug.commandCount
  io.debug.dataCount := dataAdapter.io.debug.dataCount
}

/**
 * 标准适配器配置
 */
case class StandardAdapterConfig(
    ddrStandard: DdrStandard.E,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures
)


/**
 * DDR命令编码器
 */
case class DdrCommandEncoder(config: StandardAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiCommand = slave(DfiInternalCommand(config.dfiConfig))
    val command = master(DdrCommandInterface(config.sdramConfig))
    val debug = out(DdrCommandEncoderDebug())
  }

  // 命令转换逻辑
  val cmd = DdrCommand()
  switch(io.dfiCommand.command) {
    is(DdrCommand.NOP) { cmd := DdrCommand.NOP }
    is(DdrCommand.ACT) { cmd := DdrCommand.ACT }
    is(DdrCommand.READ) { cmd := DdrCommand.READ }
    is(DdrCommand.WRITE) { cmd := DdrCommand.WRITE }
    is(DdrCommand.PRE) { cmd := DdrCommand.PRE }
    is(DdrCommand.REF) { cmd := DdrCommand.REF }
    is(DdrCommand.MRS) { cmd := DdrCommand.MRS }
    default { cmd := DdrCommand.NOP }
  }

  // 输出命令
  io.command.valid := io.dfiCommand.valid
  io.command.cmd := cmd
  io.command.addr := io.dfiCommand.address.asBits
  io.command.ba := io.dfiCommand.bank.asBits
  io.command.autoPrecharge := False // 根据需要设置
  io.command.burstLength := B"3'b000" // BL8

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := CountOne(Seq(io.command.valid))
}

/**
 * DDR数据适配器
 */
case class DdrDataAdapter(config: StandardAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiWrite = slave(DfiInternalWrite(config.dfiConfig))
    val dfiRead = slave(DfiInternalRead(config.dfiConfig))
    val data = master(DdrDataInterface(config.sdramConfig))
    val debug = out(DdrDataAdapterDebug())
  }

  // 写数据适配
  io.data.write.valid := io.dfiWrite.valid
  io.data.write.data := io.dfiWrite.data
  io.data.write.mask := io.dfiWrite.mask
  io.data.write.last := io.dfiWrite.last
  io.data.write.dqs := 0 // 根据DDR标准生成
  io.data.write.dqs_n := 0

  // 读数据适配
  io.data.read.ready := io.dfiRead.ready
  io.data.read.data := io.dfiRead.data
  io.data.read.valid := io.dfiRead.valid
  io.data.read.last := io.dfiRead.last
  io.data.read.dqs := 0 // 从DDR接口接收
  io.data.read.dqs_n := 0

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.dataCount := CountOne(Seq(io.dfiWrite.valid, io.dfiRead.valid))
}

/**
 * DDR标准接口生成器
 */
case class DdrStandardInterfaceGenerator(config: StandardAdapterConfig) extends Area {

  val io = new Bundle {
    val command = slave(DdrCommandInterface(config.sdramConfig))
    val data = slave(DdrDataInterface(config.sdramConfig))
    val standardInterface = master(DdrStandardInterface(config.sdramConfig))
  }

  // 标准接口信号生成
  io.standardInterface.cmd := io.command.cmd
  io.standardInterface.addr := io.command.addr
  io.standardInterface.ba := io.command.ba
  io.standardInterface.cke := True // 默认使能
  io.standardInterface.cs_n := !io.command.valid
  io.standardInterface.odt := False // 根据需要设置
  io.standardInterface.reset_n := True // 默认非复位
}

/**
 * DDR初始化接口生成器
 */
case class DdrInitInterfaceGenerator(config: StandardAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiInternal = slave(DfiInternal(config.dfiConfig))
    val init = master(DdrInitInterface(config.sdramConfig))
  }

  // 初始化信号生成
  io.init.initStart := False // 从DFI状态接口获取
  io.init.initComplete := False
  io.init.powerUp := True
  io.init.modeRegisterSet := Vec(False, False, False, False)
  io.init.zqCalibration := False
}

/**
 * 调试接口定义
 */
case class StandardAdapterDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val dataCount = UInt(32 bits)
}

case class DdrCommandEncoderDebug() extends Bundle {
  val commandCount = UInt(32 bits)
}

case class DdrDataAdapterDebug() extends Bundle {
  val dataCount = UInt(32 bits)
}