package spinal.lib.blackbox.xilinx.ultrascale

import spinal.core._
import spinal.core.fiber.{Handle, Unset}
import spinal.lib._
import spinal.lib.bus.bmb._
import spinal.lib.bus.misc.SizeMapping

// UltraScale MMCME3_BASE primitive interface changes compared to 7-series MMCME2_BASE:
// 1. Added active-low reset signal RST (documentation reference: Xilinx UG572)
// 2. DADDR width changed from 7-bit to 8-bit
// 3. DRDY moved to output port
// Note: DI/DO widths remain 16-bit, DCLK remains input

case class Mmcme3Dbus() extends Bundle with IMasterSlave {
  val DO = out Bits(16 bits)
  val DRDY = out Bool()
  val RST = in Bool()           // New active-low reset
  val DWE = in Bool()
  val DEN = in Bool()
  val DADDR = in UInt(8 bits)   // Changed from 7 to 8 bits
  val DI = in Bits(16 bits)

  override def asMaster() = {
    out(DWE, DEN, DADDR, DI, RST)
    in(DO, DRDY)
  }
}

// Retained control logic with necessary modifications
object Mmcme3Ctrl {
  def getBmbCapabilities(accessSource: BmbAccessCapabilities) = BmbSlaveFactory.getBmbCapabilities(
    accessSource,
    addressWidth = addressWidth,
    dataWidth = 32
  )
  def addressWidth = 12
}

case class Mmcme3Ctrl(p: BmbParameter) extends Component {
  val io = new Bundle {
    val ctrl = slave(Bmb(p))
    val dbus = master(Mmcme3Dbus())
  }

  assert(p.access.dataWidth == 32)
  val state = RegInit(U"00")
  val context = Reg(io.ctrl.cmd.context)
  val source = Reg(io.ctrl.cmd.source)
  
  // Drive new RST signal (active-low, default to deasserted)
  io.dbus.RST := True
  
  // Modified DADDR width handling
  io.dbus.DEN := io.ctrl.cmd.valid && state === 0
  io.dbus.DWE := io.ctrl.cmd.isWrite
  io.dbus.DADDR := (io.ctrl.cmd.address >> 2).resized
  io.dbus.DI := io.ctrl.cmd.data(15 downto 0)
  io.ctrl.cmd.ready := io.dbus.DEN

  io.ctrl.rsp.valid := state === 2
  io.ctrl.rsp.data := io.dbus.DO.resized
  io.ctrl.rsp.context := context
  io.ctrl.rsp.source := source
  io.ctrl.rsp.last := True
  io.ctrl.rsp.setSuccess()

  switch(state) {
    is(0) {
      context := io.ctrl.cmd.context
      source := io.ctrl.cmd.source
      when(io.ctrl.cmd.fire) { state := 1 }
    }
    is(1) {
      when(io.dbus.DRDY) { state := 2 }
    }
    is(2) {
      when(io.ctrl.rsp.ready) { state := 0 }
    }
  }
}

class Mmcme3CtrlGenerator(CtrlOffset: Handle[BigInt] = Unset)
                         (implicit interconnect: BmbInterconnectGenerator, decoder: BmbImplicitPeripheralDecoder = null) extends Area {

  val accessSource = Handle[BmbAccessCapabilities]
  val accessCapabilities = Handle(Mmcme3Ctrl.getBmbCapabilities(accessSource))
  val accessRequirements = Handle[BmbAccessParameter]

  val logic = Handle(Mmcme3Ctrl(accessRequirements.toBmbParameter()))
  val ctrl = Handle(logic.io.ctrl)
  val dbus = Handle(logic.io.dbus)

  interconnect.addSlave(
    accessSource = accessSource,
    accessCapabilities = accessCapabilities,
    accessRequirements = accessRequirements,
    bus = ctrl,
    mapping = Handle(SizeMapping(CtrlOffset, 1 << Mmcme3Ctrl.addressWidth))
  )

  if (decoder != null) interconnect.addConnection(decoder.bus, ctrl)
}