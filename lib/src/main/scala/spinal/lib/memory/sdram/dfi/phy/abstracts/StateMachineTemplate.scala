/**
 * 状态机模板抽象类
 *
 * 统一状态机实现模式，避免重复代码
 */
package spinal.lib.memory.sdram.dfi.phy.abstracts

import spinal.core._
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 状态机模板抽象基类
 */
abstract class StateMachineTemplate extends Area {

  // 调试接口
  val io = new Bundle {
    val debug = out(StateMachineDebug())
  }

  // 状态计数器
  protected val stateCount = Reg(UInt(32 bits)) init 0
  protected val transitionCount = Reg(UInt(32 bits)) init 0
  protected val errorCount = Reg(UInt(32 bits)) init 0

  // 状态机实例
  protected val fsm = new StateMachine {
    // 子类需要实现状态定义
  }

  // 调试输出
  io.debug.stateCount := stateCount
  io.debug.transitionCount := transitionCount
  io.debug.errorCount := errorCount

  /**
   * 状态转换钩子方法
   */
  protected def onStateChange(from: State, to: State): Unit = {
    transitionCount := transitionCount + 1
  }

  /**
   * 错误处理钩子方法
   */
  protected def onError(error: UInt): Unit = {
    errorCount := errorCount + 1
  }

  /**
   * 状态计数钩子方法
   */
  protected def onStateEntry(state: State): Unit = {
    stateCount := stateCount + 1
  }
}