package spinal.lib.misc

import spinal.core._
import spinal.core.fiber._
import spinal.lib._

import scala.collection.mutable

sealed trait InterruptMode
object EDGE_RISING extends InterruptMode
object EDGE_FALLING extends InterruptMode
object LEVEL_HIGH extends InterruptMode
object LEVEL_LOW extends InterruptMode
object SPURIOUS extends InterruptMode

trait InterruptCtrlFiber extends Nameable{
  val lock = Lock()

  def defaultInterruptMode: InterruptMode

  def createInterruptMaster(id: Int): InterruptNode
  def createInterruptSlave(id: Int, mode: InterruptMode): InterruptNode

  def createInterruptSlave(id: Int): InterruptNode = createInterruptSlave(id, defaultInterruptMode)

  val mappedInterrupts = mutable.LinkedHashMap[InterruptNode, InterruptNode]()

  def mapUpInterrupt(id: Int, node: InterruptNode): Unit = mapUpInterrupt(id, node, defaultInterruptMode)

  def mapUpInterrupt(id: Int, node: InterruptNode, mode: InterruptMode): Unit = {
    val local = createInterruptSlave(id, mode)
    local.setLambdaName(node.isNamed && this.isNamed)(s"${this.getName()}_from_${node.getName}")
    local << node
    mappedInterrupts(node) = local
  }

  def mapDownInterrupt(id: Int, node: InterruptNode): Unit = {
    val local = createInterruptMaster(id)
    local.setLambdaName(node.isNamed && this.isNamed)(s"${this.getName()}_to_${node.getName}")
    node << local
    mappedInterrupts(node) = local
  }

  def retain() = lock.retain()
  def release() = lock.release()
}
