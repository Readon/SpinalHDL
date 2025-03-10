/*
 * Xilinx UltraScale FPGA Configuration Primitives for SpinalHDL
 * 
 * Note: Compared to 7-series:
 * - STARTUPE2 replaced by STARTUPE3 with modified generics and added USRCCLKTS port
 * - BSCANE2 remains unchanged in UltraScale architecture
 */

package spinal.lib.blackbox.xilinx.ultrascale

import spinal.core._
import spinal.lib.com.jtag.JtagTapInstructionCtrl

object STARTUPE3 {
  def driveSpiClk(clk: Bool): STARTUPE3 = {
    val bb = STARTUPE3()
    bb.CLK := False
    bb.GSR := False
    bb.GTS := False
    bb.KEYCLEARB := True
    bb.PACK := False
    bb.USRCCLKO := clk
    // UltraScale-specific change: USRCCLKTS now directly controlled
    bb.USRCCLKTS := False  
    bb.USRDONEO := True
    bb.USRDONETS := False
    bb
  }
}

case class STARTUPE3() extends BlackBox {
  // UltraScale-specific generic changes
  addGeneric("PROG_USER", "FALSE")        // Renamed from PROG_USR in 7-series
  addGeneric("SIM_CCLK_FREQ", 0.0)
  addGeneric("FACTORY_CAL", "DISABLE")    // New generic parameter in UltraScale

  val CFGCLK = out Bool()
  val CFGMCLK = out Bool()
  val EOS = out Bool()
  val PREQ = out Bool()
  // Added in UltraScale
  val DNA_PORT = out Bits(57 bits)        // New DNA port for device identification
  val CLK = in Bool()
  val GSR = in Bool()
  val GTS = in Bool()
  val KEYCLEARB = in Bool()
  val PACK = in Bool()
  val USRCCLKO = in Bool()
  val USRCCLKTS = in Bool()              // Port order changed in UltraScale
  val USRDONEO = in Bool()
  val USRDONETS = in Bool()
}

// BSCANE2 remains identical to 7-series in UltraScale
case class BSCANE2(userId: Int) extends BlackBox {
  addGeneric("DISABLE_JTAG", "FALSE")
  addGeneric("JTAG_CHAIN", userId)

  val CAPTURE  = out Bool()
  val DRCK  = out Bool()
  val RESET  = out Bool()
  val RUNTEST  = out Bool()
  val SEL  = out Bool()
  val SHIFT  = out Bool()
  val TCK  = out Bool()
  val TDI  = out Bool()
  val TMS  = out Bool()
  val UPDATE  = out Bool()
  val TDO  = in Bool()

  def toJtagTapInstructionCtrl() = {
    val i = JtagTapInstructionCtrl()
    i.tdi     <> TDI
    i.enable  <> SEL
    i.capture <> CAPTURE
    i.shift   <> SHIFT
    i.update  <> UPDATE
    i.reset   <> RESET
    i.tdo     <> TDO
    i
  }
}