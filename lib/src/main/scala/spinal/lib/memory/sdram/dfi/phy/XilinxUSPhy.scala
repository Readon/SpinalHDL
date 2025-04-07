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
    ctrlReg(9) := trainingCtrl.writeLeveling.io.done // [9] Write leveling done
    ctrlReg(10) := trainingCtrl.readGate.io.done // [10] Read gate training done
    ctrlReg(11) := trainingCtrl.readEye.io.done // [11] Read eye training done

    // Delay control register (0x04)
    val delayCtrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x04).init(0)
    io.phyCtrl.dly_sel := delayCtrlReg(16 to 23) // [16:23] Byte lane select
    io.phyCtrl.cdly_rst := delayCtrlReg(0) // [0] CDLY reset
    io.phyCtrl.cdly_inc := delayCtrlReg(1) // [1] CDLY increment
    delayCtrlReg(24 to 31) := trainingCtrl.writeLeveling.io.cdlyCount.asBits.resize(8) // [24:31] Wlevel counter

    // Data path control register (0x08)
    val dataCtrlReg = busCtrl.createWriteOnly(Bits(32 bits), 0x08)
    io.phyCtrl.dq_rst := dataCtrlReg(0) // [0] DQ reset
    io.phyCtrl.dq_inc := dataCtrlReg(1) // [1] DQ increment
    io.phyCtrl.bitslip_rst := dataCtrlReg(2) // [2] Bitslip reset
    io.phyCtrl.bitslip := dataCtrlReg(3) // [3] Bitslip trigger

    // Status registers
    busCtrl.read(io.phyCtrl.half_sys8x_taps ## io.phyCtrl.cdly_value, 0x10) // [0x10] Taps + CDLY value
    busCtrl.read(io.phyCtrl.dqs_inc_count, 0x14) // [0x14] DQS increment count
    busCtrl.read(trainingCtrl.readGate.io.shiftCounter.asBits.resize(16), 0x16) // [0x16-0x17] Read calibration shift

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
    val signals: Seq[Bool] = signalMappings.map(_.padSignal)

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
    val dqs_preamble = Reg(Bool()) init (False)
    val dqs_postamble = Reg(Bool()) init (False)
    val dqs_oe = Reg(Bool()) init (False)
    val dq_oe = Reg(Bool()) init (False) // Output enable for DQ signals

    // Write data enable from DFI interface - connected from dataPath
    val wrDataEn = Reg(Bool()) init (False)

    // ==========================================================================
    // Write Latency and Timing Generation
    // ==========================================================================
    // Write latency calculation with fixed compensation
    val writeCompensation = 2 // Fixed compensation value for reliable operation

    // Calculate base latency from DFI configuration
    // If tPhyWrLat is not available, use a safe default value
    val baseLatency = dfiConfig.timeConfig.tPhyWrLat

    // Apply DDR write latency compensation and add fixed compensation
    val writeLatency = baseLatency - dfiConfig.sdram.ddrWrLat + writeCompensation

    // Ensure writeLatency is at least 3 for proper preamble/postamble
    val safeWriteLatency = Math.max(3, writeLatency)

    // Delay line for timing generation - add extra taps for safety
    val delayTaps = safeWriteLatency + 3
    val wrDelay = new TappedDelayLine(1, delayTaps)
    wrDelay.io.input := wrDataEn

    // Generate timing signals from delay taps with proper synchronization
    val wrDataEnDelayed = RegNext(wrDelay.io.taps(safeWriteLatency)) init (False)
    dq_oe := RegNext(wrDataEnDelayed) init (False) // Add extra register for better timing
    dqs_oe := dq_oe // Simplified - DQS always follows DQ

    // Improved preamble/postamble generation with proper timing
    dqs_preamble := wrDelay.io.taps(safeWriteLatency - 1) & ~wrDataEnDelayed
    dqs_postamble := wrDelay.io.taps(safeWriteLatency + 1) & ~wrDataEnDelayed

    // Delay line for output enable
    val delayLine = new TappedDelayLine(1, 1)
    delayLine.io.input := dqs_preamble | dqs_postamble | dqs_oe

    // ==========================================================================
    // DQS Pattern Generation
    // ==========================================================================
    // DQS pattern generator for serialization
    val pattern = new DQSPattern
    pattern.io.preamble := dqs_preamble
    pattern.io.postamble := dqs_postamble
    pattern.io.wlevel_en := io.dfi.wrTraining.wrlvlEn.orR
    pattern.io.wlevel_strobe := io.dfi.wrTraining.wrlvlStrobe.orR

    // BitSlip for pattern alignment
    val bitslip = new BitSlip(8)
    bitslip.io.input := pattern.io.output
    bitslip.io.rst := io.phyCtrl.bitslip_rst | sysRst
    bitslip.io.slp := io.phyCtrl.bitslip

    // ==========================================================================
    // DQS Output Path - Byte Lanes
    // ==========================================================================
    // DQS OSERDES for byte lanes with delay
    val dqsWidth = io.pads.dqs_p.getWidth
    val oserdesVec = Seq.fill(dqsWidth)(new OSERDESE3())
    val odelayVec = Seq.fill(dqsWidth)(new ODELAYE3(delayType = "VARIABLE", refClkFrequency = 200))

    // Configure and connect DQS OSERDES for each byte lane
    for (((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex) {
      // Configure OSERDES
      serdes.RST := io.ctrl.reset | sysRst
      serdes.CLK := io.clk4x
      serdes.CLKDIV := sysClk
      serdes.D := bitslip.io.output
      serdes.T := ~delayLine.io.output

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
      if (dfiConfig.sdram.generation.dataRate > 1) {
        if (dfiConfig.sdram.generation.dqsType == DqsType.Differential) {
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
  }

  // Data path
  val dataPath = new Area {
    // ==========================================================================
    // DFI Interface Signals
    // ==========================================================================
    // Write path signals from DFI interface
    val wrData = io.dfi.write.wr(0).wrdata
    val wrDataEn = io.dfi.write.wr(0).wrdataEn
    val wrDataMask = io.dfi.write.wr(0).wrdataMask
    val wrDataCsN = if (dfiConfig.useWrdataCsN) Some(io.dfi.write.wr(0).wrdataCsN) else None

    // Read path signals to DFI interface
    val rdData = io.dfi.read.rd(0).rddata

    // Connect write data enable to dqsPath for DQS timing generation
    dqsPath.wrDataEn := wrDataEn

    // ==========================================================================
    // Write Path (DQ)
    // ==========================================================================
    // Write data serialization components
    val wrBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3())

    // Configure BitSlip for write data
    for ((slip, data) <- wrBitslip.zip(wrData.asBools)) {
      slip.io.input := data.asBits #* 8
      slip.io.rst := io.phyCtrl.bitslip_rst | sysRst
      slip.io.slp := io.phyCtrl.bitslip
    }

    // Configure and connect DQ OSERDES to pads
    for (((osd, slip), i) <- dqOserdes.zip(wrBitslip).zipWithIndex) {
      // Configure OSERDES
      osd.D := slip.io.output
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
    val rdBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
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

      // Connect to bitslip
      rdBitslip(i).io.input := serdes.Q
    }

    // Connect read data to DFI interface
    for ((slip, data) <- rdBitslip.zip(rdData.asBools)) {
      data := slip.io.output(0)
    }
  }

  // BitSlip module implementation - Fixed implementation
  class BitSlip(width: Int) extends Component {
    val io = new Bundle {
      val input = in Bits (width bits)
      val output = out Bits (width bits)
      val rst = in Bool ()
      val slp = in Bool ()
    }

    // Create a shift register that captures input on each slip pulse
    val shiftReg = Reg(Vec(Bits(width bits), width))
    val ptr = Counter(width, inc = io.slp)

    // Initialize the shift register
    for (i <- 0 until width) {
      shiftReg(i) init (B(0, width bits))
    }

    // Update shift register on slip pulse
    when(io.slp) {
      for (i <- 0 until width - 1) {
        shiftReg(i + 1) := shiftReg(i)
      }
      shiftReg(0) := io.input
    }

    // Reset handling
    when(io.rst) {
      ptr.clear()
      for (i <- 0 until width) {
        shiftReg(i) := B(0, width bits)
      }
    }

    // Output is selected based on pointer value
    io.output := shiftReg(ptr.value)
  }

  // TappedDelayLine module implementation
  class TappedDelayLine(width: Int, ntaps: Int) extends Component {
    val io = new Bundle {
      val input = in Bool ()
      val taps = out Vec (Bool(), ntaps)
      val output = out Bool ()
    }

    val delayLine = Vec(Reg(Bool()) init (False), ntaps)
    delayLine(0) := io.input
    for (i <- 1 until ntaps) {
      delayLine(i) := delayLine(i - 1)
    }
    io.taps := delayLine
    io.output := io.taps(ntaps - 1)
  }

  // Training FSM using flattened phyCtrl interface
  // Training Module Definitions
  class TrainingController(config: DfiConfig) extends Area {
    val io = new Bundle {
      val dfi = slave(Dfi(config))
      val phyCtrl = master(DfiPhyControlInterface(config))
      val status = new Bundle {
        val initDone = out(Bool())
      }
    }

    val writeLeveling = new WriteLevelingModule(config)
    val readGate = new ReadGateModule(config)
    val readEye = new ReadEyeModule(config)

    writeLeveling.io.start := io.dfi.wrTraining.wrlvlEn.orR
    readGate.io.start := io.dfi.rdTraining.rdlvlEn.orR
    readEye.io.start := io.dfi.rdTraining.rdlvlGateEn.orR

    io.phyCtrl.cdly_value := writeLeveling.io.cdlyCount
    io.phyCtrl.dqs_inc_count := writeLeveling.io.dqsIncCount

    val fsm = new StateMachine {
      val idle = new State with EntryPoint
      val wrLevel = new State
      val rdGate = new State
      val rdEye = new State
      val done = new State

      idle.whenIsActive {
        when(writeLeveling.io.start) { goto(wrLevel) }
      }
      wrLevel.whenIsActive {
        when(writeLeveling.io.done) { goto(rdGate) }
        rdGate.whenIsActive {
          when(readGate.io.done) { goto(rdEye) }
        }
        rdEye.whenIsActive {
          when(readEye.io.done) { goto(done) }
        }

        done.whenIsActive {
          io.status.initDone := True
          goto(idle)
        }
      }
    }
  }

  class WriteLevelingModule(config: DfiConfig) extends Area {
    val io = new Bundle {
      val start = in(Bool())
      val done = out(Bool())
      val cdlyCount = out(UInt(9 bits))
      val dqsIncCount = out(UInt(9 bits))
    }

    val counter = Reg(UInt(9 bits)) init (0)
    val doneReg = RegInit(False)

    when(io.start) {
      counter := counter + 1
      doneReg := counter >= 32
    }

    io.cdlyCount := counter
    io.dqsIncCount := counter
    io.done := doneReg
  }

  class ReadGateModule(config: DfiConfig) extends Area {
    val io = new Bundle {
      val start = in(Bool())
      val done = out(Bool())
      val bitslip = out(Bool())
      val dq_inc = out(Bool())
    }

    val shiftCounter = Reg(UInt(4 bits)) init (0)
    val pulseCounter = Reg(UInt(4 bits)) init (0)

    pulseCounter := pulseCounter + 1

    // Alternate between dq_inc and bitslip
    io.dq_inc := pulseCounter === 0
    io.bitslip := pulseCounter === 8

    when(io.bitslip) {
      shiftCounter := shiftCounter + 1
    }

    io.done := shiftCounter === 7
  }

  class ReadEyeModule(config: DfiConfig) extends Area {
    val io = new Bundle {
      val start = in(Bool())
      val done = out(Bool())
      val phase = out(UInt(2 bits))
    }

    val timeout = Reg(UInt(16 bits))
    val phaseReg = Reg(UInt(2 bits))

    timeout := timeout + 1

    when(timeout(7 downto 0).andR) {
      phaseReg := phaseReg + 1
    }

    io.phase := phaseReg
    io.done := phaseReg === 3
  }

  // Instantiate TrainingController
  val trainingCtrl = new TrainingController(dfiConfig)
  trainingCtrl.io.dfi <> io.dfi
  trainingCtrl.io.phyCtrl <> io.phyCtrl
  trainingCtrl.io.status.initDone := io.ctrl.initDone
}
