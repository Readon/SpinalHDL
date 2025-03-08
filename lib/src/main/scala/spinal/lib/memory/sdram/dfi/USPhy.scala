package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.memory.sdram.dfi.interface._
import spinal.lib.memory.sdram.SdramGeneration.DDR3


case class SdramPads(dfiConfig: DfiConfig) extends Bundle {
  val clk_p = out(Bool())
  val clk_n = out(Bool())
  val clk4x = in(Bool())
  val a = out(Bits(dfiConfig.addressWidth bits))
  val ba = out(Bits(dfiConfig.bankWidth bits))
  val dq = inout(Analog(Bits(dfiConfig.dataWidth bits)))
  val dqs_p = inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dqs_n = inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dm = out(Bits(dfiConfig.dataWidth/8 bits))
}

class Oserdese3BlackBox extends BlackBox {
  val generic = new Generic {
    val SIM_DEVICE = "ULTRASCALE"
    val DATA_WIDTH = 8
    val INIT = "FALSE"
    val IS_RST_INVERTED = 0
    val IS_CLK_INVERTED = 0
    val IS_CLKDIV_INVERTED = 0
  }

  val io = new Bundle {
    val RST    = in Bool()
    val CLK    = in Bool()
    val CLKDIV = in Bool()
    val D      = in Bits(8 bits)
    val OQ     = out Bool()
    val T_OUT  = out Bool()
  }

  mapCurrentClockDomain(io.CLK, io.RST)
}

class UsDdrPhy(dfiConfig: DfiConfig) extends Component {
  val io = new Bundle {
    val dfi = slave(Dfi(dfiConfig))
    val pads = new SdramPads(dfiConfig)
    val ctrl = new Bundle {
      val reset = in Bool()
      val initDone = out Bool()
    }
  }

  // Clock domains
  val sys4xDomain = ClockDomain(
    clock = io.pads.clk4x,
    reset = ClockDomain.current.reset,
    frequency = FixedFrequency(ClockDomain.current.frequency.getValue*4) // Assuming 4x clock
  )


  def driveFrom(busCtrl : BusSlaveFactory, address : BigInt) : Unit = {
    val resetReg = busCtrl.createReadAndWrite(Bool(), 0x00, 0) init(False)
    val initDone = busCtrl.createReadOnly(Bool(), 0x04, 0)
    io.ctrl.reset := resetReg
    io.ctrl.initDone := initDone
  }

  // Command path
  val cmdPath = new Area {
    val cmdSignals = Vec(
      io.dfi.control.address,
      io.dfi.control.bank,
      io.dfi.control.rasN,
      io.dfi.control.casN,
      io.dfi.control.weN
    ).flatMap(_.asBools)

    val oserdesVec = Seq.fill(cmdSignals.length)(new Oserdese3BlackBox())
    for((osd,sig) <- oserdesVec.zip(cmdSignals)){
      osd.io.RST    := io.ctrl.reset
      osd.io.CLK    := io.pads.clk4x
      osd.io.CLKDIV := ClockDomain.current.readClockWire
      osd.io.D      := sig.asBits #* 8
    }
  }

  // Data path
  val dataPath = new Area {
    // Write path
    val wrData = io.dfi.write.wr(0).wrdata
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new Oserdese3BlackBox())
    for((osd, data) <- dqOserdes.zip(wrData.asBools)){
      osd.io.D := data.asBits #* 8
    }

    // Read path
    val rdData = io.dfi.read.rd(0).rddata
    for((osd, data) <- dqOserdes.zip(rdData.asBools)){
      data := osd.io.OQ
    }
  }


  // Training FSM
  val trainingFSM = new Area {
    val initDoneReg = RegInit(False)
    val calibDoneReg = RegInit(False)

    val fsm = new StateMachine {
      val stateIdle = new State with EntryPoint {
        whenIsActive {
          when(io.ctrl.reset) {
            goto(stateInit)
          }
        }
      }

      val stateInit = new State {
        onEntry(initDoneReg := False)
        whenIsNext(stateCalibration)
      }

      val stateCalibration = new State {
        onEntry(calibDoneReg := False)
        whenIsNext(stateReady)
      }

      val stateReady = new State {
        onEntry(io.ctrl.initDone := True)
      }
    }
  }
}
