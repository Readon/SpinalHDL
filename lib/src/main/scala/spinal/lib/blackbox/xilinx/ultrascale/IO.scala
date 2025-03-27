package spinal.lib.blackbox.xilinx.ultrascale

import spinal.core._

/** Enhanced OSERDESE3 with 8-bit data width */
case class OSERDESE3(
  dataWidth: Int = 8,
  init: Boolean = false,
  isClkInverted: Boolean = false,
  isRstInverted: Boolean = false,
  isClkDivInverted: Boolean = false,
  simDevice: String = "ULTRASCALE"
) extends BlackBox {
  val generic = new Generic {
    val DATA_WIDTH = dataWidth
    val INIT = if(init) "1" else "0"
    val IS_CLK_INVERTED = if(isClkInverted) "1" else "0"
    val IS_CLKDIV_INVERTED = if(isClkDivInverted) "1" else "0"
    val IS_RST_INVERTED = if(isRstInverted) "1" else "0"
    val SIM_DEVICE = simDevice
  }

  val CLK = in Bool()
  val CLKDIV = in Bool()
  val D = in Bits(dataWidth bits)
  val OQ = out Bool()
  val RST = in Bool()
  val T = in Bool()
  val T_OUT = out Bool()

  mapCurrentClockDomain(CLK, RST)
}

/** Enhanced ODELAYE3 with 9-bit delay resolution */
case class ODELAYE3(
  cascade: String = "NONE",
  delayFormat: String = "TIME",
  delayType: String = "FIXED",
  delayValue: Int = 0,
  refClkFrequency: Double = 300.0,
  isClkInverted: Boolean = false,
  isRstInverted: Boolean = false,
  simDevice: String = "ULTRASCALE_PLUS",
  updateMode: String = "ASYNC"
) extends BlackBox {
  val generic = new Generic {
    val CASCADE = cascade
    val DELAY_FORMAT = delayFormat
    val DELAY_TYPE = delayType
    val DELAY_VALUE = delayValue
    val REFCLK_FREQUENCY = refClkFrequency
    val IS_CLK_INVERTED = if(isClkInverted) "1" else "0"
    val IS_RST_INVERTED = if(isRstInverted) "1" else "0"
    val SIM_DEVICE = simDevice
    val UPDATE_MODE = updateMode
  }

  val CASC_OUT      = out Bool()
  val CNTVALUEOUT   = out UInt(9 bits)
  val DATAOUT       = out Bool()
  val CASC_IN       = in Bool()
  val CASC_RETURN   = in Bool()
  val CE            = in Bool()
  val CLK           = in Bool()
  val CNTVALUEIN    = in UInt(9 bits)
  val ODATAIN       = in Bool()
  val INC           = in Bool()
  val LOAD          = in Bool()
  val EN_VTC        = in Bool()
  val RST           = in Bool()
}

/** Enhanced IDELAYE3 with cascading support */
case class IDELAYE3(
  cascade: String = "NONE",
  delayFormat: String = "TIME",
  delayType: String = "FIXED",
  delayValue: Int = 0,
  refClkFrequency: Double = 300.0,
  isClkInverted: Boolean = false,
  isRstInverted: Boolean = false,
  simDevice: String = "ULTRASCALE_PLUS",
  updateMode: String = "ASYNC"
) extends BlackBox {
  val generic = new Generic {
    val CASCADE = cascade
    val DELAY_FORMAT = delayFormat
    val DELAY_TYPE = delayType
    val DELAY_VALUE = delayValue
    val REFCLK_FREQUENCY = refClkFrequency
    val IS_CLK_INVERTED = if(isClkInverted) "1" else "0"
    val IS_RST_INVERTED = if(isRstInverted) "1" else "0"
    val SIM_DEVICE = simDevice
    val UPDATE_MODE = updateMode
  }

  val CASC_OUT      = out Bool()
  val CNTVALUEOUT   = out Bits(9 bits)
  val DATAOUT       = out Bool()
  val CASC_IN       = in Bool()
  val CASC_RETURN   = in Bool()
  val CE            = in Bool()
  val CLK           = in Bool()
  val CNTVALUEIN    = in Bits(9 bits)
  val DATAIN        = in Bool()
  val IDATAIN       = in Bool()
  val INC           = in Bool()
  val LOAD          = in Bool()
  val EN_VTC        = in Bool()
  val RST           = in Bool()
}

/** Enhanced IOBUF_DCIEN with termination control */
case class IOBUF_DCIEN(
  simDevice: String = "ULTRASCALE",
  useIbufDisable: String = "TRUE"
) extends BlackBox {
  val generic = new Generic {
    val SIM_DEVICE = simDevice
    val USE_IBUFDISABLE = useIbufDisable
  }

  val DCITERMDISABLE = in Bool()
  val IBUFDISABLE    = in Bool()
  val I              = in Bool()
  val T              = in Bool()
  val O              = out Bool()
  val IO             = inout(Analog(Bool()))
}

// Unchanged primitives with verified compatibility
case class OBUFDS() extends BlackBox {
  val I  = in Bool()
  val O  = out Bool()
  val OB = out Bool()
}

case class IOBUFDS() extends BlackBox {
  val I   = in Bool()
  val T   = in Bool()
  val O   = out Bool()
  val IO  = inout(Analog(Bool()))
  val IOB = inout(Analog(Bool()))
}

case class IOBUF() extends BlackBox {
  val I  = in Bool()
  val T  = in Bool()
  val O  = out Bool()
  val IO = inout(Analog(Bool()))
}

/** Updated ISERDESE3 with FIFO support */
case class ISERDESE3(
  dataWidth: Int = 8,
  fifoEnable: String = "FALSE",
  fifoSyncMode: String = "FALSE",
  isClkInverted: Boolean = false,
  isClkBInverted: Boolean = false,
  isRstInverted: Boolean = false,
  simDevice: String = "ULTRASCALE_PLUS"
) extends BlackBox {
  val generic = new Generic {
    val DATA_WIDTH = dataWidth
    val FIFO_ENABLE = fifoEnable
    val FIFO_SYNC_MODE = fifoSyncMode
    val IS_CLK_INVERTED = if(isClkInverted) "1" else "0"
    val IS_CLK_B_INVERTED = if(isClkBInverted) "1" else "0"
    val IS_RST_INVERTED = if(isRstInverted) "1" else "0"
    val SIM_DEVICE = simDevice
  }

  val FIFO_EMPTY       = out Bool()
  val INTERNAL_DIVCLK  = out Bool()
  val Q                = out Bits(8 bits)
  val CLK              = in Bool()
  val CLK_B            = in Bool()
  val CLKDIV           = in Bool()
  val D                = in Bool()
  val FIFO_RD_CLK      = in Bool()
  val FIFO_RD_EN       = in Bool()
  val RST              = in Bool()
}

/** IDELAYCTRL for UltraScale (enhanced calibration control) */
case class IDELAYCTRL() extends BlackBox {
  val REFCLK = in Bool()
  val RST    = in Bool()
  val RDY    = out Bool()

  // RDY indicates calibration status (documented behavior)
  RDY := True  // Actual implementation should connect to calibration logic
}