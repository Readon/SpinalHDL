package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 数据路径组件
 *
 * 负责处理读写数据的传输、格式转换和时序对齐。
 * 支持不同DDR标准的特定数据处理要求。
 */
case class DataPath(config: DataPathConfig) extends Component {

  val io = new Bundle {
    // 时序输入
    val timing = slave(DdrTimingInterface(config.sdramConfig))

    // 数据输入输出
    val data = slave(DdrDataInterface(config.sdramConfig))

    // 校准接口
    val calibration = master(DdrCalibrationInterface(config.sdramConfig))

    // 调试接口
    val debug = out(DataPathDebug())
  }

  // 数据缓冲器
  val writeBuffer = WriteDataBuffer(config)
  writeBuffer.io.timing <> io.timing
  writeBuffer.io.writeData <> io.data.write

  // 读数据处理器
  val readProcessor = ReadDataProcessor(config)
  readProcessor.io.timing <> io.timing
  readProcessor.io.readData <> io.data.read

  // 数据格式转换器
  val dataFormatter = DataFormatter(config)
  dataFormatter.io.writeBuffer <> writeBuffer.io.formattedWrite
  dataFormatter.io.readProcessor <> readProcessor.io.formattedRead

  // 校准接口连接
  io.calibration <> dataFormatter.io.calibration

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.writeCount := writeBuffer.io.debug.writeCount
  io.debug.readCount := readProcessor.io.debug.readCount
}

/**
 * 数据路径配置
 */
case class DataPathConfig(
    ddrStandard: DdrStandard.C,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures
)

/**
 * 写数据缓冲器
 */
case class WriteDataBuffer(config: DataPathConfig) extends Area {

  val io = new Bundle {
    val timing = slave(DdrTimingInterface(config.sdramConfig))
    val writeData = slave(DdrWriteDataInterface(config.sdramConfig))
    val formattedWrite = master(DdrFormattedWriteInterface(config.sdramConfig))
    val debug = out(WriteDataBufferDebug())
  }

  // 写数据缓冲
  val writeFifo = StreamFifo(
    dataType = DdrWriteDataInterface(config.sdramConfig),
    depth = 16
  )

  writeFifo.io.push.payload := io.writeData
  writeFifo.io.push.valid := io.writeData.valid

  io.formattedWrite.valid := writeFifo.io.pop.valid && io.timing.dataReady
  io.formattedWrite.data := writeFifo.io.pop.payload.data
  io.formattedWrite.mask := writeFifo.io.pop.payload.mask
  io.formattedWrite.last := writeFifo.io.pop.payload.last
  writeFifo.io.pop.ready := io.formattedWrite.valid

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.writeCount := CountOne(Seq(io.writeData.valid))
}

/**
 * 读数据处理器
 */
case class ReadDataProcessor(config: DataPathConfig) extends Area {

  val io = new Bundle {
    val timing = slave(DdrTimingInterface(config.sdramConfig))
    val readData = slave(DdrReadDataInterface(config.sdramConfig))
    val formattedRead = master(DdrFormattedReadInterface(config.sdramConfig))
    val debug = out(ReadDataProcessorDebug())
  }

  // 读数据缓冲
  val readFifo = StreamFifo(
    dataType = DdrReadDataInterface(config.sdramConfig),
    depth = 16
  )

  readFifo.io.push.payload := io.readData
  readFifo.io.push.valid := io.readData.valid

  io.formattedRead.ready := readFifo.io.pop.ready
  io.formattedRead.data := readFifo.io.pop.payload.data
  io.formattedRead.valid := readFifo.io.pop.valid && io.timing.dataValid
  io.formattedRead.last := readFifo.io.pop.payload.last
  readFifo.io.pop.ready := io.formattedRead.ready

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.readCount := CountOne(Seq(io.readData.valid))
}

/**
 * 数据格式转换器
 */
case class DataFormatter(config: DataPathConfig) extends Area {

  val io = new Bundle {
    val writeBuffer = slave(DdrFormattedWriteInterface(config.sdramConfig))
    val readProcessor = slave(DdrFormattedReadInterface(config.sdramConfig))
    val calibration = master(DdrCalibrationInterface(config.sdramConfig))
  }

  // 数据格式转换逻辑（根据DDR标准）
  io.calibration.writeData := io.writeBuffer.data
  io.calibration.writeMask := io.writeBuffer.mask
  io.calibration.readData := io.readProcessor.data
  io.calibration.valid := io.writeBuffer.valid || io.readProcessor.valid
}


/**
 * 调试接口定义
 */
case class DataPathDebug() extends Bundle {
  val writeCount = UInt(32 bits)
  val readCount = UInt(32 bits)
}

case class WriteDataBufferDebug() extends Bundle {
  val writeCount = UInt(32 bits)
}

case class ReadDataProcessorDebug() extends Bundle {
  val readCount = UInt(32 bits)
}