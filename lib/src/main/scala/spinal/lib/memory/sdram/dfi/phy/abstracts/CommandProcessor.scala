/**
 * 命令处理器抽象类
 *
 * 统一命令处理逻辑，避免重复实现
 */
package spinal.lib.memory.sdram.dfi.phy.abstracts

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 命令处理器抽象基类
 */
abstract class CommandProcessor[InCmd <: SpinalEnum, OutCmd <: SpinalEnum] extends Area {

  // 命令计数器
  protected val inputCount = Reg(UInt(32 bits)) init 0
  protected val outputCount = Reg(UInt(32 bits)) init 0
  protected val errorCount = Reg(UInt(32 bits)) init 0

  /**
   * 命令转换逻辑（由子类实现）
   */
  protected def convert(input: SpinalEnumElement[_]): SpinalEnumElement[_]

  /**
   * 错误处理钩子方法
   */
  protected def onError(error: UInt): Unit = {
    errorCount := errorCount + 1
  }
}

/**
 * 命令映射表
 */
class CommandMapping[InCmd <: SpinalEnum, OutCmd <: SpinalEnum](
    inputEnum: InCmd,
    outputEnum: OutCmd
) {
  private val mapping = scala.collection.mutable.Map[SpinalEnumElement[_], SpinalEnumElement[_]]()

  /**
   * 添加命令映射
   */
  def map(input: SpinalEnumElement[_], output: SpinalEnumElement[_]): this.type = {
    mapping += (input -> output)
    this
  }

  /**
   * 执行映射转换
   */
  def convert(input: SpinalEnumElement[_]): SpinalEnumElement[_] = {
    mapping.get(input).orNull // 返回null，需要子类处理
  }
}