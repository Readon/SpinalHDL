/**
 * 数据接口定义
 *
 * 定义数据处理相关的接口
 */
package spinal.lib.memory.sdram.dfi.phy.interfaces

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

/**
 * 格式化写接口
 */
case class DdrFormattedWriteInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val data = Bits(config.dataWidth bits)
  val mask = Bits(config.dataWidth / 8 bits)
  val last = Bool()

  override def asMaster(): Unit = {
    out(valid, data, mask, last)
  }
}

/**
 * 格式化读接口
 */
case class DdrFormattedReadInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val ready = Bool()
  val data = Bits(config.dataWidth bits)
  val valid = Bool()
  val last = Bool()

  override def asMaster(): Unit = {
    in(ready)
    out(data, valid, last)
  }
}

/**
 * DDR写数据接口
 */
case class DdrWriteDataInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val data = Bits(config.dataWidth bits)
  val mask = Bits(config.dataWidth / 8 bits)
  val last = Bool()
  val dqs = Bits(config.dataWidth / 8 bits)
  val dqs_n = Bits(config.dataWidth / 8 bits)

  override def asMaster(): Unit = {
    out(valid, data, mask, last, dqs, dqs_n)
  }
}

/**
 * DDR读数据接口
 */
case class DdrReadDataInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val ready = Bool()
  val data = Bits(config.dataWidth bits)
  val valid = Bool()
  val last = Bool()
  val dqs = Bits(config.dataWidth / 8 bits)
  val dqs_n = Bits(config.dataWidth / 8 bits)

  override def asMaster(): Unit = {
    in(ready)
    out(data, valid, last, dqs, dqs_n)
  }
}

/**
 * DDR数据接口
 */
/**
 * DDR接口定义
 */
case class DdrInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  // 时钟和控制信号
  val clk = out Bool()
  val clk_n = out Bool()
  val cke = out Bits(1 bits) // 简化为单片选，实际使用时可扩展
  val cs_n = out Bits(1 bits)
  val ras_n = out Bool()
  val cas_n = out Bool()
  val we_n = out Bool()

  // 地址和银行信号
  val addr = out Bits(Math.max(config.rowWidth, config.columnWidth) bits)
  val ba = out Bits(config.bankWidth bits)
  val bg = config.bgWidth > 0 generate out Bits(config.bgWidth bits)
  val cid = config.cidWidth > 0 generate out Bits(config.cidWidth bits)

  // 数据信号
  val dq = out Bits(config.dataWidth bits)
  val dqs = out Bits(config.dataWidth / 8 bits)
  val dqs_n = out Bits(config.dataWidth / 8 bits)
  val dm = out Bits(config.dataWidth / 8 bits)

  // 电源管理
  val odt = out Bits(1 bits)
  val reset_n = out Bool()

  override def asMaster(): Unit = {
    out(clk, clk_n, cke, cs_n, ras_n, cas_n, we_n, addr, ba, bg, cid, dq, dqs, dqs_n, dm, odt, reset_n)
  }
}
case class DdrDataInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val write = DdrWriteDataInterface(config)
  val read = DdrReadDataInterface(config)

  override def asMaster(): Unit = {
    master(write, read)
  }
}