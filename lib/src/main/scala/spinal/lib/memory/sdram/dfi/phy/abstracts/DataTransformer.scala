/**
 * 数据转换器抽象类
 *
 * 统一数据处理模式，避免重复实现
 */
package spinal.lib.memory.sdram.dfi.phy.abstracts

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 数据转换器抽象基类
 */
abstract class DataTransformer[In <: Data, Out <: Data] extends Area {

  // 数据计数器
  protected val inputCount = Reg(UInt(32 bits)) init 0
  protected val outputCount = Reg(UInt(32 bits)) init 0
  protected val errorCount = Reg(UInt(32 bits)) init 0

  /**
   * 数据转换逻辑（由子类实现）
   */
  protected def transform(input: In): Out

  /**
   * 错误处理钩子方法
   */
  protected def onError(error: UInt): Unit = {
    errorCount := errorCount + 1
  }
}

/**
 * FIFO缓冲数据转换器
 */
abstract class FifoDataTransformer[In <: Data, Out <: Data](depth: Int = 16)
  extends DataTransformer[In, Out] {

  // FIFO缓冲
  protected val fifo = StreamFifo(
    dataType = Bits(32 bits), // 临时使用Bits类型
    depth = depth
  )
}