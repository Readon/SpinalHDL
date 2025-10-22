/**
 * 时序控制器抽象类
 *
 * 统一时序控制逻辑，避免重复实现
 */
package spinal.lib.memory.sdram.dfi.phy.abstracts

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 时序控制器抽象基类
 */
abstract class TimingController(config: TimingConfig) extends Area {

  // 时序参数输出（使用Bundle形式，但内部使用TimingConfig）
  val io = new Bundle {
    val tRCD = out UInt(8 bits)
    val tRP = out UInt(8 bits)
    val tRAS = out UInt(8 bits)
    val tWR = out UInt(8 bits)
    val tRTP = out UInt(8 bits)
    val tWTR = out UInt(8 bits)
    val tREFI = out UInt(16 bits)
    val tRFC = out UInt(8 bits)
  }

  // 时序参数寄存器
  val tRCD = Reg(UInt(8 bits)) init config.tRCD
  val tRP = Reg(UInt(8 bits)) init config.tRP
  val tRAS = Reg(UInt(8 bits)) init config.tRAS
  val tWR = Reg(UInt(8 bits)) init config.tWR
  val tRTP = Reg(UInt(8 bits)) init config.tRTP
  val tWTR = Reg(UInt(8 bits)) init config.tWTR
  val tREFI = Reg(UInt(16 bits)) init config.tREFI
  val tRFC = Reg(UInt(8 bits)) init config.tRFC

  // 输出时序参数
  io.tRCD := tRCD
  io.tRP := tRP
  io.tRAS := tRAS
  io.tWR := tWR
  io.tRTP := tRTP
  io.tWTR := tWTR
  io.tREFI := tREFI
  io.tRFC := tRFC

  /**
   * 更新时序参数
   */
  def updateTiming(newConfig: TimingConfig): Unit = {
    tRCD := newConfig.tRCD
    tRP := newConfig.tRP
    tRAS := newConfig.tRAS
    tWR := newConfig.tWR
    tRTP := newConfig.tRTP
    tWTR := newConfig.tWTR
    tREFI := newConfig.tREFI
    tRFC := newConfig.tRFC
  }

  /**
   * 通用定时器函数
   */
  def timing(loadValid: Bool, loadValue: UInt, timingWidth: Int = 16): Area = new Area {
    val value = Reg(UInt(timingWidth bits)) randBoot()
    val increment = value =/= loadValue.resized
    val busy = CombInit(increment)
    value := value + increment.asUInt.resized
    when(loadValid) {
      value := 0
    }
  }
}