/**
 * 标准接口定义
 *
 * 定义DDR标准特定的接口
 */
package spinal.lib.memory.sdram.dfi.phy.interfaces

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

/**
 * 空调试接口
 */
case class EmptyDebug() extends Bundle

/**
 * 标准调试接口
 */
case class StandardDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val dataCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}

/**
 * 时序调试接口
 */
case class TimingDebug() extends Bundle {
  val commandCount = UInt(32 bits)
  val timingViolationCount = UInt(32 bits)
}

/**
 * 状态机调试接口
 */
case class StateMachineDebug() extends Bundle {
  val stateCount = UInt(32 bits)
  val transitionCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}
