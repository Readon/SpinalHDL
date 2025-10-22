package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._

package object phy {

  import spinal.lib.memory.sdram.dfi._



  // 类型别名简化使用 - 避免循环引用
  type PhyDfiDdrPhyConfig = DfiDdrPhyConfig
  type PhyDdrStandard = DdrStandard.C
  type PhyDfiDdrPhyFeatures = DfiDdrPhyFeatures
  type PhyDfiDdrPhyStatus = DfiDdrPhyStatus
  type PhyDfiDdrPhyDebug = DfiDdrPhyDebug
}