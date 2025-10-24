/**
 * 时序接口定义
 *
 * 定义时序控制相关的接口
 */
package spinal.lib.memory.sdram.dfi.phy.interfaces

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

/**
 * 命令时序接口
 */
case class CommandTimingInterface() extends Bundle with IMasterSlave {
  val scheduled = Bool()
  val ready = Bool()
  val delay = UInt(8 bits)
  val priority = UInt(3 bits)

  override def asMaster(): Unit = {
    out(scheduled, ready, delay, priority)
  }

  def <<(that: CommandTimingInterface): Unit = {
    this.scheduled := that.scheduled
    this.ready := that.ready
    this.delay := that.delay
    this.priority := that.priority
  }
  def >>(that: CommandTimingInterface): Unit = that << this
}

/**
 * 时序配置（纯Scala类）
 */
case class TimingConfig(
    tCK: Int, // MHz
    tRCD: Int, // cycles
    tRP: Int,  // cycles
    tRAS: Int, // cycles
    tWR: Int,  // cycles
    tRTP: Int, // cycles
    tWTR: Int, // cycles
    tREFI: Int, // cycles
    tRFC: Int  // cycles
)

/**
 * DDR时序接口
 */
case class DdrTimingInterface(config: SdramConfig) extends Bundle with IMasterSlave {
  // 时序控制信号
  val cmdValid = Bool()
  val cmdReady = Bool()
  val dataValid = Bool()
  val dataReady = Bool()

  // 时序状态
  val busy = Bool()
  val idle = Bool()

  override def asMaster(): Unit = {
    out(cmdValid, dataValid, busy, idle)
    in(cmdReady, dataReady)
  }

  def <<(that: DdrTimingInterface): Unit = {
    this.cmdValid := that.cmdValid
    that.cmdReady := this.cmdReady
    this.dataValid := that.dataValid
    that.dataReady := this.dataReady
    this.busy := that.busy
    this.idle := that.idle
  }
  def >>(that: DdrTimingInterface): Unit = that << this
}