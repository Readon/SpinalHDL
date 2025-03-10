package spinal.lib.blackbox.xilinx.ultrascale

import spinal.core._

/** OSERDESE3 for UltraScale (replaces OSERDESE2 with significant changes)
  * @param DATA_RATE        "DDR" or "SDR"
  * @param DATA_WIDTH       8, 4, 2 for DDR; 1 for SDR
  * @param INIT             Initial value
  * @param IS_CLK_INVERTED  Clock inversion
  * @param IS_RST_INVERTED  Reset inversion
  */
case class OSERDESE3(DATA_RATE : String = "DDR",
                     DATA_WIDTH : Int = 8,
                     INIT : Boolean = false,
                     IS_CLK_INVERTED : Boolean = false,
                     IS_RST_INVERTED : Boolean = false) extends BlackBox {
  
  addGeneric("DATA_RATE", DATA_RATE)
  addGeneric("DATA_WIDTH", DATA_WIDTH)
  addGeneric("INIT", if(INIT) "1" else "0")
  addGeneric("IS_CLK_INVERTED", if(IS_CLK_INVERTED) "1" else "0")
  addGeneric("IS_RST_INVERTED", if(IS_RST_INVERTED) "1" else "0")

  val OQ = out Bool()
  val T_OUT = out Bool()
  val CLK = in Bool()
  val CLKDIV = in Bool()
  val D = in Bits(8 bits)
  val RST = in Bool()
  val T = in Bool()
}

/** ODELAYE3 for UltraScale (replaces ODELAYE2 with new features)
  * @param DELAY_FORMAT       "TIME" or "COUNT"
  * @param DELAY_VALUE        Delay in picoseconds or tap count
  * @param REFCLK_FREQUENCY   Reference clock frequency in MHz
  * @param UPDATE_MODE        "ASYNC" or "SYNC"
  */
case class ODELAYE3(DELAY_FORMAT : String = "TIME",
                    DELAY_VALUE : Int = 0,
                    REFCLK_FREQUENCY : Double = 300.0,
                    UPDATE_MODE : String = "ASYNC") extends BlackBox {
  
  addGeneric("DELAY_FORMAT", DELAY_FORMAT)
  addGeneric("DELAY_VALUE", DELAY_VALUE)
  addGeneric("REFCLK_FREQUENCY", REFCLK_FREQUENCY)
  addGeneric("UPDATE_MODE", UPDATE_MODE)

  val CASC_OUT = out Bool()
  val CNTVALUEOUT = out Bits(9 bits)  // UltraScale uses 9-bit delay resolution
  val DATAOUT = out Bool()
  val CASC_IN = in Bool()
  val CASC_RETURN = in Bool()
  val CE = in Bool()
  val CLK = in Bool()
  val CNTVALUEIN = in Bits(9 bits)
  val INC = in Bool()
  val LD = in Bool()
  val LOAD = in Bool()
  val ODATAIN = in Bool()
}

// IDELAYE3 replaces IDELAYE2 with enhanced features
case class IDELAYE3(DELAY_FORMAT : String = "TIME",
                    DELAY_VALUE : Int = 0,
                    REFCLK_FREQUENCY : Double = 300.0,
                    UPDATE_MODE : String = "ASYNC") extends BlackBox {
  
  addGeneric("DELAY_FORMAT", DELAY_FORMAT)
  addGeneric("DELAY_VALUE", DELAY_VALUE)
  addGeneric("REFCLK_FREQUENCY", REFCLK_FREQUENCY)
  addGeneric("UPDATE_MODE", UPDATE_MODE)

  val CASC_OUT = out Bool()
  val CNTVALUEOUT = out Bits(9 bits)
  val DATAOUT = out Bool()
  val CASC_IN = in Bool()
  val CASC_RETURN = in Bool()
  val CE = in Bool()
  val CLK = in Bool()
  val CNTVALUEIN = in Bits(9 bits)
  val INC = in Bool()
  val LD = in Bool()
  val LOAD = in Bool()
  val IDATAIN = in Bool()
}

// Unchanged primitives from 7-series
case class OBUFDS() extends BlackBox {
  val I = in Bool()
  val O, OB = out Bool()
  O := I
  OB := !I
}

case class IOBUFDS() extends BlackBox {
  val I, T = in Bool()
  val O = out Bool()
  val IO, IOB = inout(Analog(Bool()))
}

case class IOBUF() extends BlackBox {
  val I, T = in Bool()
  val O = out Bool()
  val IO = inout(Analog(Bool()))

  when(T) {
    IO := I
  }
  O := IO
}

/** ISERDESE3 for UltraScale (replaces ISERDESE2)
  * @param DATA_WIDTH        8, 4, 2 for DDR; 1 for SDR
  * @param FIFO_ENABLE      "FALSE" or "TRUE"
  * @param FIFO_SYNC_MODE   "FALSE" or "TRUE"
  */
case class ISERDESE3(DATA_WIDTH : Int = 8,
                     FIFO_ENABLE : String = "FALSE",
                     FIFO_SYNC_MODE : String = "FALSE") extends BlackBox {
  
  addGeneric("DATA_WIDTH", DATA_WIDTH)
  addGeneric("FIFO_ENABLE", FIFO_ENABLE)
  addGeneric("FIFO_SYNC_MODE", FIFO_SYNC_MODE)

  val Q = out Bits(8 bits)
  val CLK = in Bool()
  val CLKDIV = in Bool()
  val D = in Bool()
  val FIFO_RD_CLK = in Bool()
  val FIFO_RD_EN = in Bool()
  val RST = in Bool()
  val CLK_B = in Bool()  // Differential clock input
}

/** IDELAYCTRL remains compatible but requires different reference clock handling
  * in UltraScale architectures
  */
case class IDELAYCTRL() extends BlackBox {
  val REFCLK = in Bool()
  val RST = in Bool()
  val RDY = out Bool()

  RDY := True
}