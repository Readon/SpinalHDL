package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._
import spinal.lib.memory.sdram.dfi._

/**
 * DFI适配器层
 *
 * 负责接收DfiController的DFI信号，解析命令和数据，
 * 提供标准化的内部接口给其他模块。
 */
case class DfiAdapter(config: DfiAdapterConfig) extends Component {

  val io = new Bundle {
    // DFI接口 - slave接收DfiController信号
    val dfi = slave(Dfi(config.dfiConfig))

    // 内部接口 - 提供给其他模块
    val dfiInternal = master(DfiInternal(config.dfiConfig))

    // 训练接口
    val training = master(DfiTrainingInterface(config.dfiConfig))

    // 初始化接口（使用合并后的DdrInitInterface）
    val init = master(DdrInitInterface(SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 2,
      cidWidth = 0,
      bankWidth = 3,
      columnWidth = 10,
      rowWidth = 14,
      dataWidth = 16,
      ddrMHZ = 400,
      ddrWrLat = 6,
      ddrRdLat = 6,
      sdramtime = SdramTiming(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    )))

    // 调试接口
    val debug = out(DfiAdapterDebug())
  }

  // DFI信号解析逻辑
  val dfiParser = DfiParser(config)
  dfiParser.io.dfi <> io.dfi

  // 内部接口生成
  io.dfiInternal <> dfiParser.io.dfiInternal

  // 训练接口处理
  val trainingHandler = DfiTrainingHandler(config)
  trainingHandler.io.dfiRdTraining <> io.dfi.rdTraining
  trainingHandler.io.dfiWrTraining <> io.dfi.wrTraining
  trainingHandler.io.dfiCaTraining <> io.dfi.caTraining
  io.training <> trainingHandler.io.training

  // 初始化接口处理
  val initHandler = DfiInitHandler(config)
  initHandler.io.dfiStatus <> io.dfi.status
  initHandler.io.dfiUpdate <> io.dfi.update
  io.init <> initHandler.io.init

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := dfiParser.io.debug.commandCount
  io.debug.dataCount := dfiParser.io.debug.dataCount
  io.debug.errorCount := dfiParser.io.debug.errorCount
}

/**
 * DFI适配器配置
 */
case class DfiAdapterConfig(
    dfiConfig: DfiConfig,
    features: DfiDdrPhyFeatures
)


/**
 * DFI解析器
 */
case class DfiParser(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfi = slave(Dfi(config.dfiConfig))
    val dfiInternal = master(DfiInternal(config.dfiConfig))
    val debug = out(DfiParserDebug())
  }

  // 命令解析逻辑
  val commandParser = DfiCommandParser(config)
  commandParser.io.dfiControl <> io.dfi.control
  io.dfiInternal.command <> commandParser.io.command

  // 写数据解析逻辑
  val writeParser = DfiWriteParser(config)
  writeParser.io.dfiWrite <> io.dfi.write
  io.dfiInternal.write <> writeParser.io.write

  // 读数据生成逻辑
  val readGenerator = DfiReadGenerator(config)
  readGenerator.io.dfiRead <> io.dfi.read
  io.dfiInternal.read <> readGenerator.io.read

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandParser.io.debug.commandCount
  io.debug.dataCount := writeParser.io.debug.dataCount
  io.debug.errorCount := U(0, 32 bits)
}

/**
 * DFI命令解析器
 */
case class DfiCommandParser(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiControl = slave(DfiControlInterface(config.dfiConfig))
    val command = master(DfiInternalCommand(config.dfiConfig))
    val debug = out(DfiCommandParserDebug())
  }

  // 命令解码逻辑
  val commandDecoder = DfiCommandDecoder(config)
  commandDecoder.io.dfiControl <> io.dfiControl
  io.command <> commandDecoder.io.command

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandDecoder.io.debug.commandCount
}

/**
 * DFI写解析器
 */
case class DfiWriteParser(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiWrite = slave(DfiWriteInterface(config.dfiConfig))
    val write = master(DfiInternalWrite(config.dfiConfig))
    val debug = out(DfiWriteParserDebug())
  }

  // 写数据处理逻辑
  io.write.valid := io.dfiWrite.wr.map(_.wrdataEn).reduce(_ || _)
  io.write.data := io.dfiWrite.wr.map(_.wrdata).reduce(_ ## _)
  io.write.mask := io.dfiWrite.wr.map(_.wrdataMask).reduce(_ ## _)
  io.write.last := Delay(io.write.valid, 1, init = False)

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.dataCount := CountOne(io.dfiWrite.wr.map(_.wrdataEn))
}

/**
 * DFI读生成器
 */
case class DfiReadGenerator(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiRead = slave(DfiReadInterface(config.dfiConfig))
    val read = master(DfiInternalRead(config.dfiConfig))
  }

  // 读数据处理逻辑
  io.read.ready := True
  io.read.data := io.dfiRead.rd.map(_.rddata).reduce(_ ## _)
  io.read.valid := io.dfiRead.rd.map(_.rddataValid).reduce(_ || _)
  io.read.last := Delay(io.read.valid, 1, init = False)
}

/**
 * DFI命令解码器
 */
case class DfiCommandDecoder(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiControl = slave(DfiControlInterface(config.dfiConfig))
    val command = master(DfiInternalCommand(config.dfiConfig))
    val debug = out(DfiCommandDecoderDebug())
  }

  // 命令解码状态机
  val commandFsm = DfiCommandFsm(config)
  commandFsm.io.dfiControl <> io.dfiControl
  io.command <> commandFsm.io.command

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := commandFsm.io.debug.commandCount
}

/**
 * DFI命令有限状态机
 */
case class DfiCommandFsm(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiControl = slave(DfiControlInterface(config.dfiConfig))
    val command = master(DfiInternalCommand(config.dfiConfig))
    val debug = out(DfiCommandFsmDebug())
  }

  // 命令解码逻辑
  val cmd = DdrCommand()
  val addr = UInt(config.dfiConfig.addressWidth bits)
  val bank = UInt(config.dfiConfig.bankWidth bits)

  // 简化的命令解码
  val rasN = io.dfiControl.rasN.orR ? B"0" | B"1"
  val casN = io.dfiControl.casN.orR ? B"0" | B"1"
  val weN = io.dfiControl.weN.orR ? B"0" | B"1"

  when(rasN.orR && casN.orR && weN.orR) {
    cmd := DdrCommand.NOP
  } otherwise {
    when(!rasN.orR && casN.orR && weN.orR) {
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
  }

  // 输出命令
  io.command.valid := True
  io.command.command := cmd
  io.command.address := io.dfiControl.address.asUInt.resized
  io.command.bank := io.dfiControl.bank.orR ? io.dfiControl.bank.asUInt | U"0"
  io.command.chipSelect := io.dfiControl.csN.asUInt(log2Up(config.dfiConfig.chipSelectNumber) - 1 downto 0)

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.commandCount := CountOne(Seq(io.command.valid))
}

/**
 * DFI训练处理器
 */
case class DfiTrainingHandler(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiRdTraining = slave(DfiReadTrainingInterface(config.dfiConfig))
    val dfiWrTraining = slave(DfiWriteTrainingInterface(config.dfiConfig))
    val dfiCaTraining = slave(DfiCATrainingInterface(config.dfiConfig))
    val training = master(DfiTrainingInterface(config.dfiConfig))
  }

  // 训练接口直通
  val rdReq = io.dfiRdTraining.rdlvlReq.orR ? io.dfiRdTraining.rdlvlReq | B"0"
  val rdGateReq = io.dfiRdTraining.rdlvlGateReq.orR ? io.dfiRdTraining.rdlvlGateReq | B"0"
  val rdResp = io.dfiRdTraining.rdlvlResp.orR ? io.dfiRdTraining.rdlvlResp | B"0"

  io.training.readTraining.req := rdReq.orR
  io.training.readTraining.gateReq := rdGateReq.orR
  io.training.readTraining.resp := rdResp
  io.training.readTraining.gateResp := rdResp

  val wrReq = io.dfiWrTraining.wrlvlReq.orR ? io.dfiWrTraining.wrlvlReq | B"0"
  val wrResp = io.dfiWrTraining.wrlvlResp.orR ? io.dfiWrTraining.wrlvlResp | B"0"

  io.training.writeTraining.req := wrReq.orR
  io.training.writeTraining.resp := wrResp

  val caReq = io.dfiCaTraining.calvlReq.orR ? io.dfiCaTraining.calvlReq | B"0"
  val caCapture = io.dfiCaTraining.calvlCapture.orR ? io.dfiCaTraining.calvlCapture | B"0"
  val caResp = io.dfiCaTraining.calvlResp.orR ? io.dfiCaTraining.calvlResp | B"0"

  io.training.caTraining.req := caReq.orR
  io.training.caTraining.capture := caCapture
  io.training.caTraining.resp := caResp
}

/**
 * DFI初始化处理器
 */
case class DfiInitHandler(config: DfiAdapterConfig) extends Area {

  val io = new Bundle {
    val dfiStatus = slave(DfiStatusInterface(config.dfiConfig))
    val dfiUpdate = slave(DfiUpdateInterface(config.dfiConfig))
    val init = master(DdrInitInterface(SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 2,
      cidWidth = 0,
      bankWidth = 3,
      columnWidth = 10,
      rowWidth = 14,
      dataWidth = 16,
      ddrMHZ = 400,
      ddrWrLat = 6,
      ddrRdLat = 6,
      sdramtime = SdramTiming(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    )))
  }

  // 初始化接口直通
  val initStart = io.dfiStatus.initStart
  val initComplete = io.dfiStatus.initComplete
  val freqRatio = io.dfiStatus.freqRatio
  val dramClkDisable = io.dfiStatus.dramClkDisable

  io.init.initStart := initStart
  io.init.initComplete := initComplete
  io.init.freqRatio := freqRatio
  io.init.dramClkDisable := dramClkDisable
}

/**
 * 调试接口定义
 */
case class DfiAdapterDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val dataCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}

case class DfiParserDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val dataCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}

case class DfiCommandParserDebug() extends Bundle {
  val commandCount = UInt(32 bits)
}

case class DfiWriteParserDebug() extends Bundle {
  val dataCount = UInt(32 bits)
}

case class DfiCommandDecoderDebug() extends Bundle {
  val commandCount = UInt(32 bits)
}

case class DfiCommandFsmDebug() extends Bundle {
  val commandCount = UInt(32 bits)
}