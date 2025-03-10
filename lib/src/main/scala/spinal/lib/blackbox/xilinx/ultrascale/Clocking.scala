package spinal.lib.blackbox.xilinx.ultrascale

import spinal.core._
import spinal.core.fiber.Handle

/**
  * Xilinx UltraScale Clock Buffer with Clock Enable
  * @see UG574 (UltraScale Architecture Libraries Guide) Ch.3
  */

object BUFGCE{
  def on(that: Bool, clockEnable: Bool): Bool = {
    val bufgce = BUFGCE()
    bufgce.setCompositeName(that, "BUFGCE")
    bufgce.I := that
    bufgce.CE := clockEnable
    bufgce.O
  }
  def onReset(that : Handle[ClockDomain]): Handle[ClockDomain] = Handle {
    that.copy(reset = BUFG.on(that.reset))
  }}

case class BUFGCE() extends BlackBox{
  val I = in Bool()
  val CE = in Bool()
  val O = out Bool()
  O := CE.mux(True -> I, False -> False)
}

object BUFG{
  def on(that : Bool) : Bool = {
    val bufg = BUFG()
    bufg.setCompositeName(that, "BUFG")
    bufg.I := that
    bufg.O
  }

  def onReset(that : Handle[ClockDomain]): Handle[ClockDomain] = Handle {
    that.copy(reset = BUFG.on(that.reset))
  }
}

case class BUFG() extends BlackBox {
  val I = in Bool()
  val O = out Bool()

  O := I
}

/**
  * UltraScale PLLE3 Advanced Configuration
  * @note Replacement for PLLE2 in 7-series devices
  * @param CLKIN_PERIOD Input clock period in ns
  * @param CLKFBOUT_MULT Feedback clock multiplier
  * @param CLKOUT0_DIVIDE Output divider for CLKOUT0
  * @see UG574 Ch.3, PG213 (PLLE3/MMCME3 Advanced Features)
  */
case class PLLE3_BASE(
  CLKIN_PERIOD   : Double = 0.0,
  CLKFBOUT_MULT  : Int    = 2,
  CLKOUT0_DIVIDE : Int    = 1,
  CLKOUT0_PHASE  : Double = 0.0,
  CLKOUT0_DUTY   : Double = 0.5,
  STARTUP_WAIT   : String = "FALSE"
) extends BlackBox {
  // Parameter validation
  assert(CLKIN_PERIOD >= 0.938 && CLKIN_PERIOD <= 52.631, "Invalid CLKIN_PERIOD range")
  assert(CLKFBOUT_MULT >= 2 && CLKFBOUT_MULT <= 64, "CLKFBOUT_MULT out of range")
  assert(CLKOUT0_DIVIDE >= 2 && CLKOUT0_DIVIDE <= 128, "CLKOUT0_DIVIDE out of range")

  // Generic parameter mapping
  addGeneric("CLKIN_PERIOD", CLKIN_PERIOD)
  addGeneric("CLKFBOUT_MULT", CLKFBOUT_MULT)
  addGeneric("CLKOUT0_DIVIDE", CLKOUT0_DIVIDE)
  addGeneric("CLKOUT0_PHASE", CLKOUT0_PHASE)
  addGeneric("CLKOUT0_DUTY", CLKOUT0_DUTY)
  addGeneric("STARTUP_WAIT", STARTUP_WAIT)

  // Port definitions
  val CLKIN    = in Bool()   // Primary input clock
  val CLKFBIN  = in Bool()   // Feedback clock input
  val RST      = in Bool()   // Active-high reset
  val CLKOUT0  = out Bool()  // Primary output clock
  val LOCKED   = out Bool()  // PLL lock status

  // Clock domain mapping
  mapClockDomain(clock = CLKIN, reset = RST, enable = False)
}

/**
  * UltraScale MMCME3 Advanced Configuration
  * @note Supports dynamic reconfiguration features
  * @param CLKIN1_PERIOD Input clock period (ns)
  * @param CLKFBOUT_MULT_F Feedback multiplier (fine resolution)
  * @param DIVCLK_DIVIDE Division ratio for all outputs
  * @param CLKOUT0_DIVIDE_F Output 0 division (fine resolution)
  */
case class MMCME3_BASE(
  CLKIN1_PERIOD    : Double = 10.0,
  CLKFBOUT_MULT_F  : Double = 5.0,
  DIVCLK_DIVIDE    : Int    = 1,
  CLKOUT0_DIVIDE_F : Double = 1.0,
  CLKOUT0_PHASE    : Double = 0.0,
  BANDWIDTH        : String = "OPTIMIZED"
) extends BlackBox {
  // Parameter validation
  assert(List("OPTIMIZED", "HIGH", "LOW").contains(BANDWIDTH), "Invalid BANDWIDTH setting")
  assert(CLKFBOUT_MULT_F >= 2.0 && CLKFBOUT_MULT_F <= 64.0, "CLKFBOUT_MULT_F out of range")
  
  // Generic configuration
  addGeneric("BANDWIDTH", BANDWIDTH)
  addGeneric("CLKIN1_PERIOD", CLKIN1_PERIOD)
  addGeneric("CLKFBOUT_MULT_F", CLKFBOUT_MULT_F)
  addGeneric("DIVCLK_DIVIDE", DIVCLK_DIVIDE)
  addGeneric("CLKOUT0_DIVIDE_F", CLKOUT0_DIVIDE_F)
  addGeneric("CLKOUT0_PHASE", CLKOUT0_PHASE)

  // Port definitions
  val CLKIN1   = in Bool()    // Primary input clock
  val CLKFBIN  = in Bool()    // Feedback clock input
  val RST      = in Bool()    // Active-high reset
  val PWRDWN   = in Bool()    // Power-down signal
  val CLKOUT0  = out Bool()   // Primary output clock
  val LOCKED   = out Bool()   // MMCM lock status
  
  // Dynamic reconfiguration ports
  val DCLK     = in Bool()    // Configuration clock
  val DEN      = in Bool()    // Configuration enable
  val DADDR    = in Bits(7 bits)  // Configuration address
  val DI       = in Bits(16 bits) // Configuration data input
  val DO       = out Bits(16 bits)// Configuration data output
  val DRDY     = out Bool()   // Data ready signal

  // Clock domain mapping
  mapClockDomain(clock = CLKIN1, reset = RST)
}

/**
  * UltraScale Clock Input Buffer
  * @note Supports differential clock inputs
  */
case class IBUFDS() extends BlackBox {
  val I  = in Bool()  // Differential positive input
  val IB = in Bool()  // Differential negative input
  val O  = out Bool() // Buffer output
}

/**
  * Regional Clock Buffer for UltraScale
  * @note Used for clock distribution within clock regions
  */
case class BUFGCE_DIV() extends BlackBox {
  val I        = in Bool()   // Clock input
  val CE       = in Bool()   // Clock enable
  val CLR      = in Bool()   // Synchronous clear
  val DIV      = in UInt(3 bits) // Division ratio
  val O        = out Bool()  // Output clock
}

/**
  * Clock Capable I/O Buffer for UltraScale
  * @note Combines input buffer and clock routing
  */
case class IBUFG() extends BlackBox {
  val I = in Bool()  // Clock input pad
  val O = out Bool() // Global clock network output
}

// Preserve unchanged 7-series primitives for UltraScale compatibility
object BUFIO {
  def on(that: Bool): Bool = {
    val bufio = BUFIO()
    bufio.setCompositeName(that, "BUFIO")
    bufio.I := that
    bufio.O
  }
}

case class BUFIO() extends BlackBox {
  val I = in Bool()
  val O = out Bool()
}

object IBUF {
  def on(that: Bool): Bool = {
    val ibuf = IBUF()
    ibuf.setCompositeName(that, "IBUF")
    ibuf.I := that
    ibuf.O
  }
}

case class IBUF() extends BlackBox {
  val I = in Bool()
  val O = out Bool()

  O := I
}
