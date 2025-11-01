package spinal.lib.memory.sdram.dfi

import spinal.core._

// Common constants for DFI modules (REQ-CS-008 compliance)
object DfiCommonConstants {
  // Debug counter widths
  val DEBUG_COUNTER_WIDTH = 32
  val TIMER_WIDTH = 16

  // FIFO and buffer depths
  val FIFO_DEPTH = 16
  val DEFAULT_FIFO_DEPTH = 16

  // Memory register constants (these will be used in context where B() is available)
  val MR_DEFAULT_INT = 0x0000
  val MR0_DDR3_INT = 0x0520

  // Training response constants (values to be used in context)
  val TRAINING_SUCCESS_1BIT_INT = 1
  val TRAINING_SUCCESS_2BIT_INT = 3

  // Common bit widths
  val BYTE_WIDTH = 8
  val PHASE_COUNT = 4

  // Address and bank widths
  val MAX_BANK_WIDTH = 8
  val MAX_ADDR_WIDTH = 16
}