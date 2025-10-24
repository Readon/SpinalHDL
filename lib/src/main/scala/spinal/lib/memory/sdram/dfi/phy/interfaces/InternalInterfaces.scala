/**
 * 内部接口定义
 *
 * 定义PHY内部模块之间的通信接口
 */
package spinal.lib.memory.sdram.dfi.phy.interfaces

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

/**
 * 内部DFI接口定义
 */
case class DfiInternal(config: DfiConfig) extends Bundle with IMasterSlave {
  // 简化的内部接口，专注于核心功能
  val command = DfiInternalCommand(config)
  val write = DfiInternalWrite(config)
  val read = DfiInternalRead(config)

  override def asMaster(): Unit = {
    master(command)
    master(write)
    slave(read)
  }

  def <<(that: DfiInternal): Unit = {
    this.command << that.command
    this.write << that.write
    that.read << this.read
  }
  def >>(that: DfiInternal): Unit = that << this
}

/**
 * 内部命令接口
 */
case class DfiInternalCommand(config: DfiConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(config.addressWidth bits)
  val bank = UInt(config.bankWidth bits)
  val chipSelect = UInt(log2Up(config.chipSelectNumber) bits)
  val command = DdrCommand()
  val bg = config.useBg generate UInt(config.bankGroupWidth bits)
  val cid = config.useCid generate UInt(config.chipIdWidth bits)

  override def asMaster(): Unit = {
    out(valid, address, bank, chipSelect, command)
    if(config.useBg) out(bg)
    if(config.useCid) out(cid)
  }

  def <<(that: DfiInternalCommand): Unit = {
    this.valid := that.valid
    this.address := that.address
    this.bank := that.bank
    this.chipSelect := that.chipSelect
    this.command := that.command
    if(config.useBg) this.bg := that.bg
    if(config.useCid) this.cid := that.cid
  }
  def >>(that: DfiInternalCommand): Unit = that << this
}

/**
 * DDR命令枚举（统一枚举）
 */
object DdrCommand extends SpinalEnum {
  val NOP, ACT, READ, WRITE, PRE, REF, MRS, ZQCS = newElement()
}

/**
 * 内部写接口
 */
case class DfiInternalWrite(config: DfiConfig) extends Bundle with IMasterSlave {
  val valid = Bool()
  val data = Bits(config.dataWidth bits)
  val mask = Bits(config.dataWidth / 8 bits)
  val last = Bool()

  override def asMaster(): Unit = {
    out(valid, data, mask, last)
  }

  def <<(that: DfiInternalWrite): Unit = {
    this.valid := that.valid
    this.data := that.data
    this.mask := that.mask
    this.last := that.last
  }
  def >>(that: DfiInternalWrite): Unit = that << this
}

/**
 * 内部读接口
 */
case class DfiInternalRead(config: DfiConfig) extends Bundle with IMasterSlave {
  val ready = Bool()
  val data = Bits(config.dataWidth bits)
  val valid = Bool()
  val last = Bool()

  override def asMaster(): Unit = {
    in(ready)
    out(data, valid, last)
  }

  def <<(that: DfiInternalRead): Unit = {
    that.ready := this.ready
    this.data := that.data
    this.valid := that.valid
    this.last := that.last
  }
  def >>(that: DfiInternalRead): Unit = that << this
}

/**
 * 训练接口
 */
case class DfiTrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val readTraining = DfiReadTrainingInternal(config)
  val writeTraining = DfiWriteTrainingInternal(config)
  val caTraining = DfiCaTrainingInternal(config)

  override def asMaster(): Unit = {
    master(readTraining, writeTraining, caTraining)
  }

  def <<(that: DfiTrainingInterface): Unit = {
    this.readTraining << that.readTraining
    this.writeTraining << that.writeTraining
    this.caTraining << that.caTraining
  }
  def >>(that: DfiTrainingInterface): Unit = that << this
}

/**
 * DDR校准接口
 */
case class DdrCalibrationInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  val writeData = Bits(config.dataWidth bits)
  val writeMask = Bits(config.dataWidth / 8 bits)
  val readData = Bits(config.dataWidth bits)
  val valid = Bool()

  override def asMaster(): Unit = {
    out(writeData, writeMask, readData, valid)
  }

  def <<(that: DdrCalibrationInterface): Unit = {
    this.writeData := that.writeData
    this.writeMask := that.writeMask
    this.readData := that.readData
    this.valid := that.valid
  }
  def >>(that: DdrCalibrationInterface): Unit = that << this
}

/**
 * 内部读训练接口
 */
case class DfiReadTrainingInternal(config: DfiConfig) extends Bundle with IMasterSlave {
  val req = Bool()
  val gateReq = Bool()
  val resp = Bits(config.readLevelingResponseWidth bits)
  val gateResp = Bits(config.readLevelingResponseWidth bits)

  override def asMaster(): Unit = {
    out(req, gateReq)
    in(resp, gateResp)
  }

  def <<(that: DfiReadTrainingInternal): Unit = {
    this.req := that.req
    this.gateReq := that.gateReq
    that.resp := this.resp
    that.gateResp := this.gateResp
  }
  def >>(that: DfiReadTrainingInternal): Unit = that << this
}

/**
 * 内部写训练接口
 */
case class DfiWriteTrainingInternal(config: DfiConfig) extends Bundle with IMasterSlave {
  val req = Bool()
  val resp = Bits(config.writeLevelingResponseWidth bits)

  override def asMaster(): Unit = {
    out(req)
    in(resp)
  }

  def <<(that: DfiWriteTrainingInternal): Unit = {
    this.req := that.req
    that.resp := this.resp
  }
  def >>(that: DfiWriteTrainingInternal): Unit = that << this
}

/**
 * 内部CA训练接口
 */
case class DfiCaTrainingInternal(config: DfiConfig) extends Bundle with IMasterSlave {
  val req = Bool()
  val capture = Bits(config.caTrainingMCIFWidth bits)
  val resp = Bits(config.caTrainingResponseWidth bits)

  override def asMaster(): Unit = {
    out(req, capture)
    in(resp)
  }

  def <<(that: DfiCaTrainingInternal): Unit = {
    this.req := that.req
    this.capture := that.capture
    that.resp := this.resp
  }
  def >>(that: DfiCaTrainingInternal): Unit = that << this
}
