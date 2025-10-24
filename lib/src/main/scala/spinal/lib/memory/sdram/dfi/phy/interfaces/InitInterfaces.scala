/**
 * 初始化接口定义
 *
 * 定义初始化相关的接口
 */
package spinal.lib.memory.sdram.dfi.phy.interfaces

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

/**
 * DDR初始化命令接口
 */
case class DdrInitCommandInterface() extends Bundle with IMasterSlave {
  val valid = Bool()
  val cmd = DdrCommand()
  val done = Bool()

  override def asMaster(): Unit = {
    out(valid, cmd)
    in(done)
  }

  def <<(that: DdrInitCommandInterface): Unit = {
    this.valid := that.valid
    this.cmd := that.cmd
    that.done := this.done
  }
  def >>(that: DdrInitCommandInterface): Unit = that << this
}

/**
 * 模式寄存器命令接口
 */
case class ModeRegisterCommandInterface() extends Bundle with IMasterSlave {
  val valid = Bool()
  val mr0 = Bits(16 bits)
  val mr1 = Bits(16 bits)
  val mr2 = Bits(16 bits)
  val mr3 = Bits(16 bits)
  val done = Bool()

  override def asMaster(): Unit = {
    out(valid, mr0, mr1, mr2, mr3)
    in(done)
  }

  def <<(that: ModeRegisterCommandInterface): Unit = {
    this.valid := that.valid
    this.mr0 := that.mr0
    this.mr1 := that.mr1
    this.mr2 := that.mr2
    this.mr3 := that.mr3
    that.done := this.done
  }
  def >>(that: ModeRegisterCommandInterface): Unit = that << this
}

/**
 * DDR命令接口
 */
case class DdrCommandInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val cmd = DdrCommand()
  val addr = Bits(Math.max(config.rowWidth, config.columnWidth) bits)
  val ba = Bits(config.bankWidth bits)
  val bg = config.bgWidth > 0 generate Bits(config.bgWidth bits)
  val cid = config.cidWidth > 0 generate Bits(config.cidWidth bits)
  val autoPrecharge = Bool()
  val burstLength = Bits(3 bits)

  override def asMaster(): Unit = {
    out(valid, cmd, addr, ba, bg, cid, autoPrecharge, burstLength)
  }

  def <<(that: DdrCommandInterface): Unit = {
    this.valid := that.valid
    this.cmd := that.cmd
    this.addr := that.addr
    this.ba := that.ba
    if (config.bgWidth > 0) this.bg := that.bg
    if (config.cidWidth > 0) this.cid := that.cid
    this.autoPrecharge := that.autoPrecharge
    this.burstLength := that.burstLength
  }
  def >>(that: DdrCommandInterface): Unit = that << this
}

/**
 * DDR标准接口
 */
case class DdrStandardInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  // 标准DDR命令信号
  val cmd = DdrCommand()
  val addr = Bits(Math.max(config.rowWidth, config.columnWidth) bits)
  val ba = Bits(config.bankWidth bits)
  val bg = config.bgWidth > 0 generate Bits(config.bgWidth bits)
  val cid = config.cidWidth > 0 generate Bits(config.cidWidth bits)

  // 控制信号
  val cke = Bool()
  val cs_n = Bool()
  val odt = Bool()
  val reset_n = Bool()

  override def asMaster(): Unit = {
    out(cmd, addr, ba, bg, cid, cke, cs_n, odt, reset_n)
  }

  def <<(that: DdrStandardInterface): Unit = {
    this.cmd := that.cmd
    this.addr := that.addr
    this.ba := that.ba
    if (config.bgWidth > 0) this.bg := that.bg
    if (config.cidWidth > 0) this.cid := that.cid
    this.cke := that.cke
    this.cs_n := that.cs_n
    this.odt := that.odt
    this.reset_n := that.reset_n
  }
  def >>(that: DdrStandardInterface): Unit = that << this
}

/**
 * DDR初始化接口（合并了DfiInitInterface的所有字段）
 */
case class DdrInitInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val initStart = Bool()
  val initComplete = Bool()
  val powerUp = Bool()
  val modeRegisterSet = Vec(Bool(), 4) // MR0-MR3
  val zqCalibration = Bool()
  val freqRatio = Bits(2 bits) // 从原DfiInitInterface
  val dramClkDisable = Bits(1 bits) // 从原DfiInitInterface，简化为单片选

  override def asMaster(): Unit = {
    out(initStart, powerUp, modeRegisterSet, zqCalibration, freqRatio, dramClkDisable)
    in(initComplete)
  }

  def <<(that: DdrInitInterface): Unit = {
    this.initStart := that.initStart
    that.initComplete := this.initComplete
    this.powerUp := that.powerUp
    this.modeRegisterSet := that.modeRegisterSet
    this.zqCalibration := that.zqCalibration
    this.freqRatio := that.freqRatio
    this.dramClkDisable := that.dramClkDisable
  }
  def >>(that: DdrInitInterface): Unit = that << this
}