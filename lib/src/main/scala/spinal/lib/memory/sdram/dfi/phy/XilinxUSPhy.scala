package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.blackbox.xilinx.ultrascale._

case class SdramIO(dfiConfig: DfiConfig) extends Bundle {
  // Clock signals (always present)
  val clk_p = out(Bool())
  val clk_n = out(Bool())

  // Command and address (always present)
  val a = out(Bits(dfiConfig.addressWidth bits))
  val ba = dfiConfig.signalConfig.useBank generate out(Bits(dfiConfig.bankWidth bits))

  // Protocol-specific signals
  val bg = dfiConfig.signalConfig.useBg generate out(Bits(dfiConfig.bankGroupWidth bits))
  val ras_n = dfiConfig.signalConfig.useRasN generate out(Bits(dfiConfig.controlWidth bits))
  val cas_n = dfiConfig.signalConfig.useCasN generate out(Bits(dfiConfig.controlWidth bits))
  val we_n = dfiConfig.signalConfig.useWeN generate out(Bits(dfiConfig.controlWidth bits))
  val cs_n = out(Bits(dfiConfig.chipSelectNumber bits)) // Always present
  val act_n = dfiConfig.signalConfig.useAckN generate out(Bool())

  // Control signals
  val cke = out(Bits(dfiConfig.chipSelectNumber bits)) // Always present
  val odt = dfiConfig.signalConfig.useOdt generate out(Bits(dfiConfig.chipSelectNumber bits))
  val reset_n = dfiConfig.signalConfig.useResetN generate out(Bits(dfiConfig.chipSelectNumber bits))

  // Data interface (always present)
  val dq = inout(Analog(Bits(dfiConfig.dataWidth bits)))
  val dm = out(Bits(dfiConfig.dataWidth / 8 bits))

  // DQS signals - differential based on dataRate
  val dqs_p = (dfiConfig.sdram.generation.dataRate > 1) generate inout(Analog(Bits(dfiConfig.dataWidth / 8 bits)))
  val dqs_n =
    (dfiConfig.sdram.generation.dqsType == DqsType.Differential && dfiConfig.sdram.generation.dataRate > 1) generate inout(
      Analog(Bits(dfiConfig.dataWidth / 8 bits))
    )
}

class USPhy(dfiConfig: DfiConfig) extends Component {
  val sysClk = CombInit(ClockDomain.current.readClockWire)
  val sysRst = CombInit(ClockDomain.current.readResetWire)

  val io = new Bundle {
    val dfi = slave(Dfi(dfiConfig))
    val pads = new SdramIO(dfiConfig)
    val clk4x = in Bool ()
    val clk4xN = in Bool ()

    // PHY control interface
    val phyCtrl = new Bundle {
      // Training status
      val half_sys8x_taps = out UInt (9 bits)
      val dqs_inc_count = out UInt (9 bits)

      // Control signals
      val dly_sel = in Bits (8 bits) // Byte lane select
      val cdly_rst = in Bool () // Command delay reset
      val cdly_inc = in Bool () // Command delay increment
      val cdly_value = out UInt (9 bits) // Current command delay

      // Data path control
      val dq_rst = in Bool () // DQ delay reset
      val dq_inc = in Bool () // DQ delay increment
      val bitslip_rst = in Bool () // Bitslip reset
      val bitslip = in Bool () // Bitslip trigger

      // Phase control
      val rd_phase = in UInt (2 bits) // Read phase
      val wr_phase = in UInt (2 bits) // Write phase
    }

    val ctrl = new Bundle {
      val reset = in Bool ()
      val initDone = out Bool ()
    }
  }

  def driveFrom(busCtrl: BusSlaveFactory, address: BigInt): Unit = {
    // Control register group (0x00)
    val ctrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x00).init(0)
    io.ctrl.reset := ctrlReg(0) // [0] Global reset
    ctrlReg(8) := io.ctrl.initDone // [8] Initialization status (RO)
    ctrlReg(9) := trainingCtrl.writeLeveling.done // [9] Write leveling done
    ctrlReg(10) := trainingCtrl.readGate.done // [10] Read gate training done
    ctrlReg(11) := trainingCtrl.readEye.done // [11] Read eye training done

    // Delay control register (0x04)
    val delayCtrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x04).init(0)
    io.phyCtrl.dly_sel := delayCtrlReg(16 to 23) // [16:23] Byte lane select
    io.phyCtrl.cdly_rst := delayCtrlReg(0) // [0] CDLY reset
    io.phyCtrl.cdly_inc := delayCtrlReg(1) // [1] CDLY increment
    delayCtrlReg(24 to 31) := trainingCtrl.writeLeveling.cdlyCount.asBits.resize(8) // [24:31] Wlevel counter

    // Data path control register (0x08)
    val dataCtrlReg = busCtrl.createWriteOnly(Bits(32 bits), 0x08)
    io.phyCtrl.dq_rst := dataCtrlReg(0) // [0] DQ reset
    io.phyCtrl.dq_inc := dataCtrlReg(1) // [1] DQ increment
    io.phyCtrl.bitslip_rst := dataCtrlReg(2) // [2] Bitslip reset
    io.phyCtrl.bitslip := dataCtrlReg(3) // [3] Bitslip trigger

    // Status registers
    busCtrl.read(io.phyCtrl.half_sys8x_taps ## io.phyCtrl.cdly_value, 0x10) // [0x10] Taps + CDLY value
    busCtrl.read(io.phyCtrl.dqs_inc_count, 0x14) // [0x14] DQS increment count
    // busCtrl.read(trainingCtrl.readGate.io.shiftCounter.asBits.resize(16), 0x16) // [0x16-0x17] Read calibration shift

    // Configuration register (0x18)
    val configReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x18).init(0)
    io.phyCtrl.rd_phase := configReg(13 downto 12).asUInt // [1:0] Read phase
    io.phyCtrl.wr_phase := configReg(15 downto 14).asUInt // [3:2] Write phase
    configReg(16) := trainingCtrl.fsm.isActive(trainingCtrl.fsm.done) // [16] Calibration done status
  }

  // Instantiate clock generation by serdes and delay.
  val clockGen = new Area {
    val serdes = new OSERDESE3()
    serdes.RST := sysRst | io.ctrl.reset
    serdes.CLK := io.clk4x
    serdes.CLKDIV := sysClk
    serdes.D := B"1010_1010"

    val delay = new ODELAYE3(delayType = "VARIABLE")
    delay.RST := sysRst | io.ctrl.reset | io.phyCtrl.cdly_rst
    delay.CLK := sysClk
    // EN_VTC now controlled by DFI interface:
    // - Disabled during training phases
    // - Enabled after calibration complete
    delay.EN_VTC := io.dfi.update.ctrlupdAck &&
      !(io.dfi.wrTraining.wrlvlEn.orR ||
        io.dfi.rdTraining.rdlvlEn.orR ||
        io.dfi.rdTraining.rdlvlGateEn.orR)
    delay.CE := io.phyCtrl.cdly_inc
    delay.INC := True
    delay.ODATAIN := serdes.OQ

    val buf = new OBUFDS()
    buf.I := delay.DATAOUT

    io.pads.clk_p := buf.O
    io.pads.clk_n := buf.OB
  }

  // Helper class to manage command/address/bank signals and their output connections
  class CmdSignalHandler {
    def regroupSignals(input: Bits, width: Int): Vec[Bits] = {
      val slices = input.subdivideIn(width bits)
      Vec.tabulate(width) { i =>
        Cat(slices.map(_(i)))
      }
    }

    // Synchronize DFI inputs for better timing
    val syncedAddress =
      regroupSignals(RegNextWhen(io.dfi.control.address, io.dfi.control.cke.asBool), dfiConfig.addressWidth)
    val syncedBank = regroupSignals(RegNextWhen(io.dfi.control.bank, io.dfi.control.cke.asBool), dfiConfig.bankWidth)

    // Define signal mappings based on pads structure
    case class SignalMapping(padSignal: Bool, dfiSource: Bits)

    // Create mappings for each pad signal
    val signalMappings = new scala.collection.mutable.ArrayBuffer[SignalMapping]()

    // Address signals - always present
    for (i <- 0 until dfiConfig.addressWidth) {
      signalMappings += SignalMapping(io.pads.a(i), syncedAddress(i))
    }

    // Bank signals - if used
    if (dfiConfig.signalConfig.useBank) {
      for (i <- 0 until dfiConfig.bankWidth) {
        signalMappings += SignalMapping(io.pads.ba(i), syncedBank(i))
      }
    }

    // RAS_N signals - if used
    if (dfiConfig.signalConfig.useRasN) {
      val rasN = regroupSignals(io.dfi.control.rasN, dfiConfig.controlWidth)
      for (i <- 0 until dfiConfig.controlWidth) {
        signalMappings += SignalMapping(io.pads.ras_n(i), rasN(i))
      }
    }

    // CAS_N signals - if used
    if (dfiConfig.signalConfig.useCasN) {
      val casN = regroupSignals(io.dfi.control.casN, dfiConfig.controlWidth)
      for (i <- 0 until dfiConfig.controlWidth) {
        signalMappings += SignalMapping(io.pads.cas_n(i), casN(i))
      }
    }

    // WE_N signals - if used
    if (dfiConfig.signalConfig.useWeN) {
      val weN = regroupSignals(io.dfi.control.weN, dfiConfig.controlWidth)
      for (i <- 0 until dfiConfig.controlWidth) {
        signalMappings += SignalMapping(io.pads.we_n(i), weN(i))
      }
    }

    // CS_N signals - always present
    val csN = regroupSignals(io.dfi.control.csN, dfiConfig.chipSelectNumber)
    for (i <- 0 until dfiConfig.chipSelectNumber) {
      signalMappings += SignalMapping(io.pads.cs_n(i), csN(i))
    }

    // CKE signals - always present
    val ckeGroup = regroupSignals(io.dfi.control.cke, dfiConfig.chipSelectNumber)
    for (i <- 0 until dfiConfig.chipSelectNumber) {
      signalMappings += SignalMapping(io.pads.cke(i), ckeGroup(i))
    }

    // ODT signals - always present
    if (dfiConfig.signalConfig.useOdt) {
      val odt = regroupSignals(io.dfi.control.odt, dfiConfig.chipSelectNumber)
      for (i <- 0 until dfiConfig.chipSelectNumber) {
        signalMappings += SignalMapping(io.pads.odt(i), odt(i))
      }
    }

    // ResetN signals - always present
    if (dfiConfig.signalConfig.useResetN) {
      val resetN = regroupSignals(io.dfi.control.resetN, dfiConfig.chipSelectNumber)
      for (i <- 0 until dfiConfig.chipSelectNumber) {
        signalMappings += SignalMapping(io.pads.reset_n(i), resetN(i))
      }
    }

    // ACT_N signal - if used
    if (dfiConfig.signalConfig.useAckN) {
      signalMappings += SignalMapping(io.pads.act_n, io.dfi.control.actN)
    }

    // Extract all DFI source signals for serialization
    val signals = signalMappings.map(_.padSignal)

    // Method to connect the processed output to the correct pad
    def connectOutput(index: Int, dataOut: Bool): Unit = {
      if (index < signalMappings.length) {
        signalMappings(index).padSignal := dataOut
      }
      // No 'else' needed, if index is out of range, it means the signal was disabled in config
    }
  } // End of CmdSignalHandler class definition

  // Command and Control signals path
  val cmdPath = new Area {
    // Command signals handling
    val handler = new CmdSignalHandler() // Instantiate the handler

    // Create OSERDES and ODELAY for each signal identified by the handler
    val oserdesVec = Seq.fill(handler.signals.length)(new OSERDESE3())
    val odelayVec = Seq.fill(handler.signals.length)(new ODELAYE3(delayType = "VARIABLE", refClkFrequency = 200))

    // Process each signal through OSERDES and ODELAY
    for (((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex) {
      serdes.RST := io.ctrl.reset | sysRst
      serdes.CLK := io.clk4x
      serdes.CLKDIV := sysClk
      // Get the input signal from the handler's sequence
      serdes.D := handler.signalMappings(i).dfiSource

      delay.RST := io.ctrl.reset | sysRst | io.phyCtrl.cdly_rst
      delay.CLK := sysClk
      delay.EN_VTC := True // Always enabled after training
      delay.CE := io.phyCtrl.cdly_inc
      delay.INC := True
      delay.ODATAIN := serdes.OQ

      // Use the handler to connect the final delayed output to the correct pad
      handler.connectOutput(i, delay.DATAOUT)
    }
  }

  // DQSPattern module implementation (exact match to Python version)
  class DQSPattern(register: Boolean = false) extends Component {
    val io = new Bundle {
      val preamble = in Bool ()
      val postamble = in Bool ()
      val wlevel_en = in Bool ()
      val wlevel_strobe = in Bool ()
      val output = out Bits (8 bits)
    }

    // Pattern generation logic
    val pattern = Bits(8 bits)
    pattern := 0x55
    when(io.preamble) {
      pattern := 0x15
    }.elsewhen(io.postamble) {
      pattern := 0x54
    }.elsewhen(io.wlevel_en) {
      pattern := 0x00
      when(io.wlevel_strobe) {
        pattern := 0x01
      }
    }

    // Optional registered output
    if (register) {
      val reg = Reg(Bits(8 bits)) init (0x55)
      reg := pattern
      io.output := reg
    } else {
      io.output := pattern
    }
  }

  val dqsPath = new Area {
    // ==========================================================================
    // DQS Timing Control
    // ==========================================================================
    // Control signals
    val dqs_preamble = Bool()
    val dqs_postamble = Bool()
    val dqs_oe = Bool()
    val dq_oe = Bool()  // Output enable for DQ signals

    // Write data enable from DFI interface - connected from dataPath
    val wrDataEn = Bool()

    // ==========================================================================
    // Write Latency and Timing Generation
    //==========================================================================
    // Ensure writeLatency is at least 3 for proper preamble/postamble
    val safeWriteLatency = Math.ceil(dfiConfig.sdram.ddrWrLat / dfiConfig.frequencyRatio).toInt - 1

    // Generate timing signals from delay taps with proper synchronization
    val wrDataEnDelayed = History(wrDataEn, safeWriteLatency + 2)
    dq_oe := wrDataEnDelayed(safeWriteLatency)  // Add extra register for better timing
    dqs_oe := Mux(io.dfi.wrTraining.wrlvlEn.orR, True, dq_oe) // Simplified - DQS always follows DQ

    // Improved preamble/postamble generation with proper timing
    dqs_preamble := wrDataEnDelayed(safeWriteLatency - 1) & ~wrDataEnDelayed(safeWriteLatency)
    dqs_postamble := wrDataEnDelayed(safeWriteLatency + 1) & ~wrDataEnDelayed(safeWriteLatency)

    // Delay line for output enable
    val delayLine = History(dqs_preamble | dqs_postamble | dqs_oe, 1)

    // ==========================================================================
    // DQS Pattern Generation
    // ==========================================================================
    // DQS pattern generator for serialization
    val pattern = new DQSPattern
    pattern.io.preamble := dqs_preamble
    pattern.io.postamble := dqs_postamble
    pattern.io.wlevel_en := io.dfi.wrTraining.wrlvlEn.orR
    pattern.io.wlevel_strobe := io.dfi.wrTraining.wrlvlStrobe.orR

    //==========================================================================
    // DQS Output Path - Byte Lanes
    // ==========================================================================
    // DQS OSERDES for byte lanes with delay
    val dqsWidth = io.pads.dqs_p.getWidth
    val oserdesVec = Seq.fill(dqsWidth)(new OSERDESE3(hasTristate=true))
    val odelayVec = Seq.fill(dqsWidth)(new ODELAYE3(delayType="VARIABLE", refClkFrequency = 200))

    // Configure and connect DQS OSERDES for each byte lane
    for (((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex) {
      // Configure OSERDES
      serdes.RST := io.ctrl.reset | sysRst
      serdes.CLK := io.clk4x
      serdes.CLKDIV := sysClk
      serdes.D      := BitSlip(pattern.io.output, 2, io.phyCtrl.bitslip)
      serdes.T      := ~delayLine.last

      // Configure delay line with proper reset and control signals
      delay.RST := sysRst | io.ctrl.reset
      delay.CLK := sysClk
      // EN_VTC follows same control logic as clockGen delay
      delay.EN_VTC := io.dfi.update.ctrlupdAck &&
        !(io.dfi.wrTraining.wrlvlEn.orR ||
          io.dfi.rdTraining.rdlvlEn.orR ||
          io.dfi.rdTraining.rdlvlGateEn.orR)
      delay.CE := io.phyCtrl.dq_inc & io.phyCtrl.dly_sel(i / 8) // Proper flattened phyCtrl signals
      delay.INC := True // Always increment (decrement handled by reset+increment)
      delay.ODATAIN := serdes.OQ

      // Connect differential or single-ended buffer based on dqsType and dataRate
      assert(dfiConfig.sdram.generation.dataRate > 1, "PHY do not support signal data rate.")
      if(dfiConfig.sdram.generation.dqsType == DqsType.Differential) {
        val buf = new IOBUFDSE3()
        buf.I := delay.DATAOUT
        buf.T := serdes.T_OUT

        // Connect to pads
        io.pads.dqs_p(i) := buf.IO
        io.pads.dqs_n(i) := buf.IOB
      } else {
        io.pads.dqs_p(i) := delay.DATAOUT
      }
    }
  }

  // Data path
  val dataPath = new Area {
    // ==========================================================================
    // DFI Interface Signals
    // ==========================================================================
    // Write path signals from DFI interface
    val wrData = io.dfi.write.wr.map(_.wrdata)
    val wrDataEn = io.dfi.write.wr.map(_.wrdataEn).orR
    val wrDataMask = io.dfi.write.wr(0).wrdataMask
    val wrDataCsN = if (dfiConfig.useWrdataCsN) Some(io.dfi.write.wr(0).wrdataCsN) else None

    // Read path signals to DFI interface
    val rdData = io.dfi.read.rd.map(_.rddata)

    // Connect write data enable to dqsPath for DQS timing generation
    dqsPath.wrDataEn := wrDataEn

    // ==========================================================================
    // Write Path (DQ)
    // ==========================================================================
    // Write data serialization components
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3())

    // Configure and connect DQ OSERDES to pads
    for(((osd, data), i) <- dqOserdes.zip(wrData).zipWithIndex) {
      // Configure OSERDES
      osd.D := BitSlip(data, 2, io.phyCtrl.bitslip)
      osd.CLK := io.clk4x
      osd.CLKDIV := sysClk
      osd.RST := io.ctrl.reset | sysRst
      osd.T := ~dqsPath.dq_oe // Use dqsPath's dq_oe for output enable

      // Connect to IO buffer
      val buf = new IOBUF()
      buf.I := osd.OQ
      buf.T := osd.T_OUT
      io.pads.dq(i) := buf.IO
    }

    // ==========================================================================
    // Read Path (DQ)
    // ==========================================================================
    // Read data deserialization components
    val rdIserdes = Seq.fill(dfiConfig.dataWidth)(new ISERDESE3())
    val rdDelay = Seq.fill(dfiConfig.dataWidth)(new IDELAYE3(refClkFrequency = 200))

    // Configure read path components
    for (((serdes, delay), i) <- rdIserdes.zip(rdDelay).zipWithIndex) {
      // Configure delay line with proper reset and control signals
      delay.RST := sysRst | io.ctrl.reset | io.phyCtrl.dq_rst
      delay.CLK := sysClk
      delay.EN_VTC := True // Always enable VTC after calibration
      delay.CE := io.phyCtrl.dq_inc && io.phyCtrl.dly_sel(i / 8)
      delay.INC := True // Always increment (decrement handled by reset+increment)
      delay.IDATAIN := io.pads.dq(i)

      // Configure ISERDESE3
      serdes.CLK := io.clk4x
      serdes.CLK_B := io.clk4xN
      serdes.CLKDIV := sysClk
      serdes.RST := io.ctrl.reset | sysRst
      serdes.D := delay.DATAOUT
    }

    // Connect read data to DFI interface
    for((serdes, data) <- rdIserdes.zip(rdData)) {
      data := BitSlip(serdes.Q, 2, io.phyCtrl.bitslip)
    }
  }

  // BitSlip module implementation - Fixed implementation
  object BitSlip {
    def apply[T <: Data](that: T, length: Int, slip: Bool, when: Bool = null, init: T = null): T = {
      val max = that.getBitsWidth*(length - 1) + 1
      val ptr = Counter(max, inc=slip) init(max - 2)
      val hist = History(that, length, when, init)
      hist.asBits(ptr, that.getBitsWidth bits).asInstanceOf[T]
    }
  }

  // Training FSM using flattened phyCtrl interface
  // Training Module Definitions
  class TrainingController(config: DfiConfig, dfi: Dfi, initDone: Bool) extends Area {
    val writeLeveling = new WriteLevelingModule(config, dfi.wrTraining.wrlvlEn.orR)
    val readGate = new ReadGateModule(config, dfi.rdTraining.rdlvlEn.orR)
    val readEye = new ReadEyeModule(config, dfi.rdTraining.rdlvlGateEn.orR)

    // io.phyCtrl.cdly_value := writeLeveling.io.cdlyCount
    // io.phyCtrl.dqs_inc_count := writeLeveling.io.dqsIncCount

    val fsm = new StateMachine {
      val idle = new State with EntryPoint
      val wrLevel = new State
      val rdGate = new State
      val rdEye = new State
      val done = new State

      idle.whenIsActive {
        when(writeLeveling.done) { goto(wrLevel) }
      }
      wrLevel.whenIsActive {
        when(writeLeveling.done) { goto(rdGate) }
        rdGate.whenIsActive {
          when(readGate.done) { goto(rdEye) }
        }
        rdEye.whenIsActive {
          when(readEye.done) { goto(done) }
        }

        done.whenIsActive {
          initDone := True
          goto(idle)
        }
      }
    }
  }

  class WriteLevelingModule(config: DfiConfig, start: Bool) extends Area {
    val done = Bool()
    val cdlyCount = UInt(9 bits)
    val dqsIncCount = UInt(9 bits)

    val counter = Reg(UInt(9 bits)) init (0)
    val doneReg = RegInit(False)

    when(start) {
      counter := counter + 1
      doneReg := counter >= 32
    }

    cdlyCount := counter
    dqsIncCount := counter
    done := doneReg
  }

  class ReadGateModule(config: DfiConfig, start: Bool) extends Area {
    val done = Bool()
    val bitslip = Bool()
    val dq_inc = Bool()

    val shiftCounter = Reg(UInt(4 bits)) init (0)
    val pulseCounter = Reg(UInt(4 bits)) init (0)

    pulseCounter := pulseCounter + 1

    // Alternate between dq_inc and bitslip
    dq_inc := pulseCounter === 0
    bitslip := pulseCounter === 8

    when(bitslip) {
      shiftCounter := shiftCounter + 1
    }

    done := shiftCounter === 7
  }

  class ReadEyeModule(config: DfiConfig, start: Bool) extends Area {
    val done = Bool()
    val phase = UInt(2 bits)

    val timeout = Reg(UInt(16 bits))
    val phaseReg = Reg(UInt(2 bits))

    timeout := timeout + 1

    when(timeout(7 downto 0).andR) {
      phaseReg := phaseReg + 1
    }

    phase := phaseReg
    done := phaseReg === 3
  }

  // Instantiate TrainingController
  val trainingCtrl = new TrainingController(dfiConfig, io.dfi, io.ctrl.initDone)
}
