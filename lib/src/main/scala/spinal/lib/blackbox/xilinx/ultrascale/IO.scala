package spinal.lib.blackbox.xilinx.ultrascale

import spinal.core._

/** Enhanced OSERDESE3 with 8-bit data width */
case class OSERDESE3(
  DATA_WIDTH       : Int = 8,
  INIT             : Boolean = false,
  IS_CLK_INVERTED    : Boolean = false,
  IS_CLKDIV_INVERTED : Boolean = false,
  IS_RST_INVERTED    : Boolean = false,
  SIM_DEVICE       : String = "ULTRASCALE_PLUS"
) extends BlackBox {
  
  addGeneric("DATA_WIDTH", DATA_WIDTH)
  addGeneric("INIT", if(INIT) "1" else "0")  
  addGeneric("IS_CLK_INVERTED", if(IS_CLK_INVERTED) "1" else "0")
  addGeneric("IS_CLKDIV_INVERTED", if(IS_CLKDIV_INVERTED) "1" else "0")
  addGeneric("IS_RST_INVERTED", if(IS_RST_INVERTED) "1" else "0")
  addGeneric("SIM_DEVICE", SIM_DEVICE)

  val OQ       = out Bool()
  val T_OUT    = out Bool()
  val CLK      = in Bool()
  val CLKDIV   = in Bool()
  val D        = in Bits(8 bits)
  val RST      = in Bool()
  val T        = in Bool()
}

/** Enhanced ODELAYE3 with 9-bit delay resolution */
case class ODELAYE3(
  CASCADE          : String = "NONE",
  DELAY_FORMAT     : String = "TIME",
  DELAY_TYPE       : String = "FIXED",
  DELAY_VALUE      : Int = 0,
  REFCLK_FREQUENCY : Double = 300.0,
  IS_CLK_INVERTED  : Boolean = false,
  IS_RST_INVERTED  : Boolean = false,
  SIM_DEVICE       : String = "ULTRASCALE_PLUS",
  UPDATE_MODE      : String = "ASYNC"
) extends BlackBox {
  
  addGeneric("CASCADE", CASCADE)
  addGeneric("DELAY_FORMAT", DELAY_FORMAT)
  addGeneric("DELAY_TYPE", DELAY_TYPE)
  addGeneric("DELAY_VALUE", DELAY_VALUE)
  addGeneric("REFCLK_FREQUENCY", REFCLK_FREQUENCY)
  addGeneric("IS_CLK_INVERTED", if(IS_CLK_INVERTED) "1" else "0")
  addGeneric("IS_RST_INVERTED", if(IS_RST_INVERTED) "1" else "0")
  addGeneric("SIM_DEVICE", SIM_DEVICE)
  addGeneric("UPDATE_MODE", UPDATE_MODE)

  val CASC_OUT      = out Bool()
  val CNTVALUEOUT   = out Bits(9 bits)
  val DATAOUT       = out Bool()
  val CASC_IN       = in Bool()
  val CASC_RETURN   = in Bool()
  val CE            = in Bool()
  val CLK           = in Bool()
  val CNTVALUEIN    = in Bits(9 bits)
  val ODATAIN       = in Bool()
  val INC           = in Bool()
  val LOAD          = in Bool()
  val EN_VTC        = in Bool()
  val RST           = in Bool()
}

/** Enhanced IDELAYE3 with cascading support */
case class IDELAYE3(
  CASCADE          : String = "NONE",
  DELAY_FORMAT     : String = "TIME",
  DELAY_TYPE       : String = "FIXED",
  DELAY_VALUE      : Int = 0,
  REFCLK_FREQUENCY : Double = 300.0,
  IS_CLK_INVERTED  : Boolean = false,
  IS_RST_INVERTED  : Boolean = false,
  SIM_DEVICE       : String = "ULTRASCALE_PLUS",
  UPDATE_MODE      : String = "ASYNC"
) extends BlackBox {
  
  addGeneric("CASCADE", CASCADE)
  addGeneric("DELAY_FORMAT", DELAY_FORMAT)
  addGeneric("DELAY_TYPE", DELAY_TYPE)
  addGeneric("DELAY_VALUE", DELAY_VALUE)
  addGeneric("REFCLK_FREQUENCY", REFCLK_FREQUENCY)
  addGeneric("IS_CLK_INVERTED", if(IS_CLK_INVERTED) "1" else "0")
  addGeneric("IS_RST_INVERTED", if(IS_RST_INVERTED) "1" else "0")
  addGeneric("SIM_DEVICE", SIM_DEVICE)
  addGeneric("UPDATE_MODE", UPDATE_MODE)

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
  SIM_DEVICE       : String = "ULTRASCALE",
  USE_IBUFDISABLE  : String = "TRUE"
) extends BlackBox {
  
  addGeneric("SIM_DEVICE", SIM_DEVICE)
  addGeneric("USE_IBUFDISABLE", USE_IBUFDISABLE)

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
  DATA_WIDTH       : Int = 8,
  FIFO_ENABLE      : String = "FALSE",
  FIFO_SYNC_MODE   : String = "FALSE",
  IS_CLK_INVERTED   : Boolean = false,
  IS_CLK_B_INVERTED : Boolean = false,
  IS_RST_INVERTED   : Boolean = false,
  SIM_DEVICE       : String = "ULTRASCALE_PLUS"
) extends BlackBox {
  
  addGeneric("DATA_WIDTH", DATA_WIDTH)
  addGeneric("FIFO_ENABLE", FIFO_ENABLE)
  addGeneric("FIFO_SYNC_MODE", FIFO_SYNC_MODE)
  addGeneric("IS_CLK_INVERTED", if(IS_CLK_INVERTED) "1" else "0")
  addGeneric("IS_CLK_B_INVERTED", if(IS_CLK_B_INVERTED) "1" else "0")
  addGeneric("IS_RST_INVERTED", if(IS_RST_INVERTED) "1" else "0")
  addGeneric("SIM_DEVICE", SIM_DEVICE)

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