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

  def <<(that: DdrFormattedWriteInterface): Unit = {
    this.valid := that.valid
    this.data := that.data
    this.mask := that.mask
    this.last := that.last
  }
  def >>(that: DdrFormattedWriteInterface): Unit = that << this
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

  def <<(that: DdrFormattedReadInterface): Unit = {
    that.ready := this.ready
    this.data := that.data
    this.valid := that.valid
    this.last := that.last
  }
  def >>(that: DdrFormattedReadInterface): Unit = that << this
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

  def <<(that: DdrWriteDataInterface): Unit = {
    this.valid := that.valid
    this.data := that.data
    this.mask := that.mask
    this.last := that.last
    this.dqs := that.dqs
    this.dqs_n := that.dqs_n
  }
  def >>(that: DdrWriteDataInterface): Unit = that << this
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

  def <<(that: DdrReadDataInterface): Unit = {
    that.ready := this.ready
    this.data := that.data
    this.valid := that.valid
    this.last := that.last
    this.dqs := that.dqs
    this.dqs_n := that.dqs_n
  }
  def >>(that: DdrReadDataInterface): Unit = that << this
}

/**
 * DDR数据接口
 */
/**
 * DDR接口定义
 */
case class DdrInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  // 时钟和控制信号
  val clk = Bool()
  val clk_n = Bool()
  val cke = Bits(1 bits) // 简化为单片选，实际使用时可扩展
  val cs_n = Bits(1 bits)
  val ras_n = Bool()
  val cas_n = Bool()
  val we_n = Bool()

  // 地址和银行信号
  val addr = Bits(Math.max(config.rowWidth, config.columnWidth) bits)
  val ba = Bits(config.bankWidth bits)
  val bg = config.bgWidth > 0 generate Bits(config.bgWidth bits)
  val cid = config.cidWidth > 0 generate Bits(config.cidWidth bits)

  // 数据信号
  val dq = Bits(config.dataWidth bits)
  val dqs = Bits(config.dataWidth / 8 bits)
  val dqs_n = Bits(config.dataWidth / 8 bits)
  val dm = Bits(config.dataWidth / 8 bits)

  // 电源管理
  val odt = Bits(1 bits)
  val reset_n = Bool()

  override def asMaster(): Unit = {
    out(clk, clk_n, cke, cs_n, ras_n, cas_n, we_n, addr, ba, bg, cid, dq, dqs, dqs_n, dm, odt, reset_n)
  }

  def <<(that: DdrInterface): Unit = {
    this.clk := that.clk
    this.clk_n := that.clk_n
    this.cke := that.cke
    this.cs_n := that.cs_n
    this.ras_n := that.ras_n
    this.cas_n := that.cas_n
    this.we_n := that.we_n
    this.addr := that.addr
    this.ba := that.ba
    if (config.bgWidth > 0) this.bg := that.bg
    if (config.cidWidth > 0) this.cid := that.cid
    this.dq := that.dq
    this.dqs := that.dqs
    this.dqs_n := that.dqs_n
    this.dm := that.dm
    this.odt := that.odt
    this.reset_n := that.reset_n
  }
  def >>(that: DdrInterface): Unit = that << this
}
case class DdrDataInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val write = DdrWriteDataInterface(config)
  val read = DdrReadDataInterface(config)

  override def asMaster(): Unit = {
    master(write, read)
  }

  def <<(that: DdrDataInterface): Unit = {
    this.write << that.write
    this.read << that.read
  }
  def >>(that: DdrDataInterface): Unit = that << this
}