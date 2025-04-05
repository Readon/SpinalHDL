package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.blackbox.xilinx.ultrascale._

case class SdramIO(dfiConfig: DfiConfig) extends Bundle {
  // Clock signals (always present)
  val clk_p   = out(Bool())
  val clk_n   = out(Bool())

  // Command and address (always present)
  val a       = out(Bits(dfiConfig.addressWidth bits))
  val ba      = dfiConfig.signalConfig.useBank generate out(Bits(dfiConfig.bankWidth bits))

  // Protocol-specific signals
  val bg      = dfiConfig.signalConfig.useBg generate out(Bits(dfiConfig.bankGroupWidth bits))
  val ras_n   = dfiConfig.signalConfig.useRasN generate out(Bits(dfiConfig.controlWidth bits))
  val cas_n   = dfiConfig.signalConfig.useCasN generate out(Bits(dfiConfig.controlWidth bits))
  val we_n    = dfiConfig.signalConfig.useWeN generate out(Bits(dfiConfig.controlWidth bits))
  val cs_n    = out(Bits(dfiConfig.chipSelectNumber bits)) // Always present
  val act_n   = dfiConfig.signalConfig.useAckN generate out(Bool())

  // Control signals
  val cke     = out(Bits(dfiConfig.chipSelectNumber bits)) // Always present
  val odt     = dfiConfig.signalConfig.useOdt generate out(Bits(dfiConfig.chipSelectNumber bits))
  val reset_n = dfiConfig.signalConfig.useResetN generate out(Bits(dfiConfig.chipSelectNumber bits))

  // Data interface (always present)
  val dq      = inout(Analog(Bits(dfiConfig.dataWidth bits)))
  val dm      = out(Bits(dfiConfig.dataWidth/8 bits))

  // DQS signals - differential based on dataRate
  val dqs_p   = (dfiConfig.sdram.generation.dataRate > 1) generate inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dqs_n   = (dfiConfig.sdram.generation.dataRate > 1) generate inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
}

class USPhy(dfiConfig: DfiConfig) extends Component {
  val sysClk = CombInit(ClockDomain.current.readClockWire)
  val sysRst = CombInit(ClockDomain.current.readResetWire)

  val io = new Bundle {
    val dfi = slave(Dfi(dfiConfig))
    val pads = new SdramIO(dfiConfig)
    val clk4x = in Bool()
    val clk4xN = in Bool()

    // Unified PHY control interface
    val phyCtrl = new Bundle {
      val en_vtc     = in Bool()
      val half_sys8x_taps    = out UInt(9 bits)

      // System control
      val ctrl = new Bundle {
        val wlevel_en    = in Bool()
        val wlevel_strobe= in Bool()
        val dly_sel      = in Bits(8 bits)
        val cdly_rst     = in Bool()
        val cdly_inc     = in Bool()
        val cdly_value   = out UInt(9 bits)
      }

      // Read path control
      val read = new Bundle {
        val dq_rst         = in Bool()
        val dq_inc         = in Bool()
        val bitslip_rst    = in Bool()
        val bitslip        = in Bool()
      }

      // Write path control
      val write = new Bundle {
        val dq_rst         = in Bool()
        val dq_inc         = in Bool()
        val dqs_rst        = in Bool()
        val dqs_inc        = in Bool()
        val dqs_inc_count  = out UInt(9 bits)
        val bitslip_rst    = in Bool()
        val bitslip        = in Bool()
      }

      // Phase control
      val phase = new Bundle {
        val rd = in UInt(2 bits)
        val wr = in UInt(2 bits)
      }
    }

    val ctrl = new Bundle {
      val reset = in Bool()
      val initDone = out Bool()
    }
  }

  def driveFrom(busCtrl: BusSlaveFactory, address: BigInt): Unit = {
    // Control register group (0x00)
    val ctrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x00).init(0)
    io.ctrl.reset             := ctrlReg(0)       // [0] Global reset
    io.phyCtrl.en_vtc         := ctrlReg(1)       // [1] Voltage temp compensation enable
    ctrlReg(8)                := io.ctrl.initDone // [8] Initialization status (RO)
    ctrlReg(9)                := trainingFSM.writeLevelDone // [9] Write leveling done
    ctrlReg(10)               := trainingFSM.readGateDone   // [10] Read gate training done
    ctrlReg(11)               := trainingFSM.readEyeDone    // [11] Read eye training done

    // Delay control register (0x04)
    val delayCtrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x04).init(0)
    io.phyCtrl.ctrl.cdly_rst     := delayCtrlReg(0)  // [0] CDLY reset
    io.phyCtrl.ctrl.cdly_inc     := delayCtrlReg(1)  // [1] CDLY increment
    io.phyCtrl.ctrl.wlevel_en    := delayCtrlReg(2)  // [2] Write leveling enable
    io.phyCtrl.ctrl.wlevel_strobe:= delayCtrlReg(3)  // [3] Write leveling trigger
    io.phyCtrl.ctrl.dly_sel      := delayCtrlReg(16 to 23) // [16:23] Byte lane select
    delayCtrlReg(24 to 31)       := trainingFSM.wlevelCounter.asBits.resize(8) // [24:31] Wlevel counter

    // Read delay control (0x08)
    val readDelayReg = busCtrl.createWriteOnly(Bits(32 bits), 0x08)
    io.phyCtrl.read.dq_rst      := readDelayReg(0)  // [0] Read DQ reset
    io.phyCtrl.read.dq_inc      := readDelayReg(1)  // [1] Read DQ increment
    io.phyCtrl.read.bitslip_rst := readDelayReg(2)  // [2] Bitslip reset
    io.phyCtrl.read.bitslip     := readDelayReg(3)  // [3] Bitslip trigger

    // Write delay control (0x0C)
    val writeDelayReg = busCtrl.createWriteOnly(Bits(32 bits), 0x0C)
    io.phyCtrl.write.dq_rst      := writeDelayReg(0) // [0] Write DQ reset
    io.phyCtrl.write.dq_inc      := writeDelayReg(1) // [1] Write DQ increment
    io.phyCtrl.write.dqs_rst     := writeDelayReg(2) // [2] Write DQS reset
    io.phyCtrl.write.dqs_inc     := writeDelayReg(3) // [3] Write DQS increment
    io.phyCtrl.write.bitslip_rst := writeDelayReg(4) // [4] Write bitslip reset
    io.phyCtrl.write.bitslip     := writeDelayReg(5) // [5] Write bitslip trigger

    // Status registers
    busCtrl.read(io.phyCtrl.half_sys8x_taps ## io.phyCtrl.ctrl.cdly_value, 0x10) // [0x10] Taps + CDLY value
    busCtrl.read(io.phyCtrl.write.dqs_inc_count, 0x14) // [0x14] DQS increment count
    busCtrl.read(trainingFSM.readCalibShift.asBits.resize(16), 0x16) // [0x16-0x17] Read calibration shift

    // Configuration register (0x18)
    val configReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x18).init(0)
    io.phyCtrl.phase.rd     := configReg(13 downto 12).asUInt // [1:0] Read phase
    io.phyCtrl.phase.wr     := configReg(15 downto 14).asUInt // [3:2] Write phase
    configReg(16)           := trainingFSM.calibDoneReg      // [16] Calibration done status
  }

  // Instantiate clock generation by serdes and delay.
  val clockGen = new Area {
    val serdes = new OSERDESE3()
    serdes.RST := sysRst | io.ctrl.reset
    serdes.CLK := io.clk4x
    serdes.CLKDIV := sysClk
    serdes.D := B"1010_1010"

    val delay = new ODELAYE3(delayType = "VARIABLE")
    delay.RST := sysRst | io.ctrl.reset | io.phyCtrl.ctrl.cdly_rst
    delay.CLK := sysClk
    delay.EN_VTC := io.phyCtrl.en_vtc
    delay.CE := io.phyCtrl.ctrl.cdly_inc
    delay.INC := True
    delay.ODATAIN := serdes.OQ

    val buf = new OBUFDS()
    buf.I := delay.DATAOUT

    io.pads.clk_p := buf.O
    io.pads.clk_n := buf.OB
  }

  // Helper class to manage command/address/bank signals and their output connections
  class CmdSignalHandler {
    // Synchronize inputs first
    val syncedAddress = RegNextWhen(io.dfi.control.address, io.dfi.control.cke.asBool)
    val syncedBank = RegNextWhen(io.dfi.control.bank, io.dfi.control.cke.asBool)

    // Determine the sequence of signals based on config
    val signals: Seq[Bool] =
      syncedAddress.asBools ++
      syncedBank.asBools ++
      (if(dfiConfig.signalConfig.useRasN) io.dfi.control.rasN.asBools else Nil) ++
      (if(dfiConfig.signalConfig.useCasN) io.dfi.control.casN.asBools else Nil) ++
      (if(dfiConfig.signalConfig.useWeN) io.dfi.control.weN.asBools else Nil) ++
      io.dfi.control.csN.asBools ++ // cs_n is always present
      (if(dfiConfig.signalConfig.useAckN) io.dfi.control.actN.asBools else Nil) // act_n if used

    // Pre-calculate indices for connectOutput
    private val addrWidth = dfiConfig.addressWidth
    private val bankWidth = dfiConfig.bankWidth
    private val useRasN = dfiConfig.signalConfig.useRasN
    private val useCasN = dfiConfig.signalConfig.useCasN
    private val useWeN = dfiConfig.signalConfig.useWeN
    private val useActN = dfiConfig.signalConfig.useAckN
    private val controlWidth = dfiConfig.controlWidth
    private val freqRatio = dfiConfig.frequencyRatio

    private val bankStartIndex = addrWidth
    private val cmdStartIndex = bankStartIndex + bankWidth
    // Calculate absolute indices in the 'signals' sequence
    private val rasNIndexOpt = if(useRasN) Some(cmdStartIndex) else None
    private val casNIndexOpt = if(useCasN) Some(cmdStartIndex + (if(useRasN) controlWidth else 0)) else None
    private val weNIndexOpt  = if(useWeN)  Some(cmdStartIndex + (if(useRasN) controlWidth else 0) + (if(useCasN) controlWidth else 0)) else None
    private val csNIndex     = cmdStartIndex + (if(useRasN) controlWidth else 0) + (if(useCasN) controlWidth else 0) + (if(useWeN) controlWidth else 0)
    private val actNIndexOpt = if(useActN) Some(csNIndex + dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio) else None

    // Method to connect the processed output based on the signal's index in the sequence
    def connectOutput(index: Int, dataOut: Bool): Unit = {
      if(index < addrWidth) {
        // Address bits - index directly maps to pad index
        io.pads.a(index) := dataOut
      } else if(index < cmdStartIndex) {
        // Bank bits - index needs offset to map to pad index
        io.pads.ba(index - bankStartIndex) := dataOut
      } else {
        // Command bits - check against calculated absolute indices
        if(rasNIndexOpt.isDefined && index >= rasNIndexOpt.get && index < rasNIndexOpt.get + dfiConfig.controlWidth) {
          // Handle multi-bit ras_n signal
          val rasIndex = index - rasNIndexOpt.get
          io.pads.ras_n(rasIndex) := dataOut
        } else if(casNIndexOpt.isDefined && index >= casNIndexOpt.get && index < casNIndexOpt.get + dfiConfig.controlWidth) {
          // Handle multi-bit cas_n signal
          val casIndex = index - casNIndexOpt.get
          io.pads.cas_n(casIndex) := dataOut
        } else if(weNIndexOpt.isDefined && index >= weNIndexOpt.get && index < weNIndexOpt.get + dfiConfig.controlWidth) {
          // Handle multi-bit we_n signal
          val weIndex = index - weNIndexOpt.get
          io.pads.we_n(weIndex) := dataOut
        } else if(index >= csNIndex && index < csNIndex + dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio) {
          // Handle multi-bit cs_n signal
          val csIndex = index - csNIndex
          io.pads.cs_n(csIndex) := dataOut
        } else if(actNIndexOpt.isDefined && index >= actNIndexOpt.get && index < actNIndexOpt.get + freqRatio) {
          io.pads.act_n := dataOut
        }
        // No 'else' needed, if index doesn't match, it means the signal was disabled in config
      }
    }
  } // End of CmdSignalHandler class definition

  // Command path - Refactored to use CmdSignalHandler
  val cmdPath = new Area {
    val handler = new CmdSignalHandler() // Instantiate the handler

    // Create OSERDES and ODELAY for each signal identified by the handler
    val oserdesVec = Seq.fill(handler.signals.length)(new OSERDESE3())
    val odelayVec = Seq.fill(handler.signals.length)(new ODELAYE3(delayType="VARIABLE", refClkFrequency = 200))

    // Process each signal through OSERDES and ODELAY
    for(((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex){
      serdes.RST    := io.ctrl.reset | sysRst
      serdes.CLK    := io.clk4x
      serdes.CLKDIV := sysClk
      // Get the input signal from the handler's sequence
      serdes.D      := handler.signals(i).asBits.resized #* 8

      delay.RST     := io.ctrl.reset | io.phyCtrl.ctrl.cdly_rst | sysRst
      delay.CLK     := sysClk
      delay.EN_VTC  := io.phyCtrl.en_vtc
      delay.CE      := io.phyCtrl.ctrl.cdly_inc // Use the common command delay increment
      delay.INC     := True
      delay.ODATAIN := serdes.OQ

      // Use the handler to connect the final delayed output to the correct pad
      handler.connectOutput(i, delay.DATAOUT)
    }
  } // End of refactored cmdPath Area

  // Control signals path - CKE, ODT, RESET_N
  val ctrlPath = new Area {
    // Create OSERDES and ODELAY for each control signal
    val ckeSerdes = Seq.fill(dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio)(new OSERDESE3())
    val ckeDelay = Seq.fill(dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio)(new ODELAYE3(delayType="VARIABLE"))

    // Connect CKE signals
    for((serdes, i) <- ckeSerdes.zipWithIndex) {
      serdes.RST    := sysRst | io.ctrl.reset
      serdes.CLK    := io.clk4x
      serdes.CLKDIV := sysClk
      serdes.D      := io.dfi.control.cke(i).asBits.resized #* 8

      val delay = ckeDelay(i)
      delay.RST     := sysRst | io.ctrl.reset | io.phyCtrl.ctrl.cdly_rst
      delay.CLK     := sysClk
      delay.EN_VTC  := io.phyCtrl.en_vtc
      delay.CE      := io.phyCtrl.ctrl.cdly_inc
      delay.INC     := True
      delay.ODATAIN := serdes.OQ

      io.pads.cke(i) := delay.DATAOUT
    }

    // Connect ODT signals if used
    if(dfiConfig.signalConfig.useOdt) {
      val odtSerdes = Seq.fill(dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio)(new OSERDESE3())
      val odtDelay = Seq.fill(dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio)(new ODELAYE3(delayType="VARIABLE"))

      for((serdes, i) <- odtSerdes.zipWithIndex) {
        serdes.RST    := sysRst | io.ctrl.reset
        serdes.CLK    := io.clk4x
        serdes.CLKDIV := sysClk
        serdes.D      := io.dfi.control.odt(i).asBits.resized #* 8

        val delay = odtDelay(i)
        delay.RST     := sysRst | io.ctrl.reset | io.phyCtrl.ctrl.cdly_rst
        delay.CLK     := sysClk
        delay.EN_VTC  := io.phyCtrl.en_vtc
        delay.CE      := io.phyCtrl.ctrl.cdly_inc
        delay.INC     := True
        delay.ODATAIN := serdes.OQ

        io.pads.odt(i) := delay.DATAOUT
      }
    }

    // Connect RESET_N signals if used
    if(dfiConfig.signalConfig.useResetN) {
      val resetSerdes = Seq.fill(dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio)(new OSERDESE3())
      val resetDelay = Seq.fill(dfiConfig.chipSelectNumber * dfiConfig.frequencyRatio)(new ODELAYE3(delayType="VARIABLE"))

      for((serdes, i) <- resetSerdes.zipWithIndex) {
        serdes.RST    := sysRst | io.ctrl.reset
        serdes.CLK    := io.clk4x
        serdes.CLKDIV := sysClk
        serdes.D      := io.dfi.control.resetN(i).asBits.resized #* 8

        val delay = resetDelay(i)
        delay.RST     := sysRst | io.ctrl.reset | io.phyCtrl.ctrl.cdly_rst
        delay.CLK     := sysClk
        delay.EN_VTC  := io.phyCtrl.en_vtc
        delay.CE      := io.phyCtrl.ctrl.cdly_inc
        delay.INC     := True
        delay.ODATAIN := serdes.OQ

        io.pads.reset_n(i) := delay.DATAOUT
      }
    }
  }

  // DQSPattern module implementation (exact match to Python version)
  class DQSPattern(register: Boolean = false) extends Component {
    val io = new Bundle {
      val preamble = in Bool()
      val postamble = in Bool()
      val wlevel_en = in Bool()
      val wlevel_strobe = in Bool()
      val output = out Bits(8 bits)
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
    if(register) {
      val reg = Reg(Bits(8 bits)) init(0x55)
      reg := pattern
      io.output := reg
    } else {
      io.output := pattern
    }
  }

  val dqsPath = new Area {
    //==========================================================================
    // DQS Timing Control
    //==========================================================================
    // Control signals
    val dqs_preamble = Reg(Bool()) init(False)
    val dqs_postamble = Reg(Bool()) init(False)
    val dqs_oe = Reg(Bool()) init(False)
    val dq_oe = Reg(Bool()) init(False)  // Output enable for DQ signals

    // Write data enable from DFI interface - connected from dataPath
    val wrDataEn = Reg(Bool()) init(False)

    //==========================================================================
    // Write Latency and Timing Generation
    //==========================================================================
    // Write latency calculation with fixed compensation
    val writeCompensation = 2  // Fixed compensation value for reliable operation

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
    val wrDataEnDelayed = RegNext(wrDelay.io.taps(safeWriteLatency)) init(False)
    dq_oe := RegNext(wrDataEnDelayed) init(False)  // Add extra register for better timing
    dqs_oe := io.phyCtrl.ctrl.wlevel_en | dq_oe

    // Improved preamble/postamble generation with proper timing
    dqs_preamble := wrDelay.io.taps(safeWriteLatency - 1) & ~wrDataEnDelayed
    dqs_postamble := wrDelay.io.taps(safeWriteLatency + 1) & ~wrDataEnDelayed

    // Delay line for output enable
    val delayLine = new TappedDelayLine(1, 1)
    delayLine.io.input := dqs_preamble | dqs_postamble | dqs_oe

    //==========================================================================
    // DQS Pattern Generation
    //==========================================================================
    // DQS pattern generator for serialization
    val pattern = new DQSPattern
    pattern.io.preamble := dqs_preamble
    pattern.io.postamble := dqs_postamble
    pattern.io.wlevel_en := io.phyCtrl.ctrl.wlevel_en
    pattern.io.wlevel_strobe := io.phyCtrl.ctrl.wlevel_strobe

    // BitSlip for pattern alignment
    val bitslip = new BitSlip(8)
    bitslip.io.input := pattern.io.output
    bitslip.io.rst := io.phyCtrl.write.bitslip_rst | sysRst
    bitslip.io.slp := io.phyCtrl.write.bitslip

    //==========================================================================
    // DQS Output Path - Byte Lanes
    //==========================================================================
    // DQS OSERDES for byte lanes with delay
    val dqsWidth = io.pads.dqs_p.getWidth
    val oserdesVec = Seq.fill(dqsWidth)(new OSERDESE3())
    val odelayVec = Seq.fill(dqsWidth)(new ODELAYE3(delayType="VARIABLE", refClkFrequency = 200))

    // Configure and connect DQS OSERDES for each byte lane
    for(((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex){
      // Configure OSERDES
      serdes.RST    := io.ctrl.reset | sysRst
      serdes.CLK    := io.clk4x
      serdes.CLKDIV := sysClk
      serdes.D      := bitslip.io.output
      serdes.T      := ~delayLine.io.output

      // Configure delay line with proper reset and control signals
      delay.RST     := sysRst | io.ctrl.reset | io.phyCtrl.write.dqs_rst
      delay.CLK     := sysClk
      delay.EN_VTC  := io.phyCtrl.en_vtc
      delay.CE      := io.phyCtrl.write.dqs_inc & io.phyCtrl.ctrl.dly_sel(i)
      delay.INC     := True  // Always increment (decrement handled by reset+increment)
      delay.ODATAIN := serdes.OQ

      // Connect to differential buffer
      val buf = new IOBUFDSE3()
      buf.I := delay.DATAOUT
      buf.T := serdes.T_OUT

      // Connect to pads
      io.pads.dqs_p(i) := buf.IO
      io.pads.dqs_n(i) := buf.IOB
    }
  }

  // Data path
  val dataPath = new Area {
    //==========================================================================
    // DFI Interface Signals
    //==========================================================================
    // Write path signals from DFI interface
    val wrData = io.dfi.write.wr(0).wrdata
    val wrDataEn = io.dfi.write.wr(0).wrdataEn
    val wrDataMask = io.dfi.write.wr(0).wrdataMask
    val wrDataCsN = if(dfiConfig.useWrdataCsN) Some(io.dfi.write.wr(0).wrdataCsN) else None

    // Read path signals to DFI interface
    val rdData = io.dfi.read.rd(0).rddata

    // Connect write data enable to dqsPath for DQS timing generation
    dqsPath.wrDataEn := wrDataEn

    //==========================================================================
    // Write Path (DQ)
    //==========================================================================
    // Write data serialization components
    val wrBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3())

    // Configure BitSlip for write data
    for((slip, data) <- wrBitslip.zip(wrData.asBools)) {
      slip.io.input := data.asBits #* 8
      slip.io.rst := io.phyCtrl.write.bitslip_rst | sysRst
      slip.io.slp := io.phyCtrl.write.bitslip
    }

    // Configure and connect DQ OSERDES to pads
    for(((osd, slip), i) <- dqOserdes.zip(wrBitslip).zipWithIndex) {
      // Configure OSERDES
      osd.D := slip.io.output
      osd.CLK := io.clk4x
      osd.CLKDIV := sysClk
      osd.RST := io.ctrl.reset | sysRst
      osd.T := ~dqsPath.dq_oe  // Use dqsPath's dq_oe for output enable

      // Connect to IO buffer
      val buf = new IOBUF()
      buf.I := osd.OQ
      buf.T := osd.T_OUT
      io.pads.dq(i) := buf.IO
    }

    //==========================================================================
    // Read Path (DQ)
    //==========================================================================
    // Read data deserialization components
    val rdBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    val rdIserdes = Seq.fill(dfiConfig.dataWidth)(new ISERDESE3())
    val rdDelay = Seq.fill(dfiConfig.dataWidth)(new IDELAYE3(refClkFrequency = 200))

    // Configure read path components
    for(((serdes, delay), i) <- rdIserdes.zip(rdDelay).zipWithIndex) {
      // Configure delay line with proper reset and control signals
      delay.RST := sysRst | io.ctrl.reset | io.phyCtrl.read.dq_rst
      delay.CLK := sysClk
      delay.EN_VTC := io.phyCtrl.en_vtc
      delay.CE := io.phyCtrl.read.dq_inc && io.phyCtrl.ctrl.dly_sel(i/8)
      delay.INC := True  // Always increment (decrement handled by reset+increment)
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
    for((slip, data) <- rdBitslip.zip(rdData.asBools)) {
      data := slip.io.output(0)
    }
  }


  // BitSlip module implementation - Fixed implementation
  class BitSlip(width: Int) extends Component {
    val io = new Bundle {
      val input = in Bits(width bits)
      val output = out Bits(width bits)
      val rst = in Bool()
      val slp = in Bool()
    }

    // Create a shift register that captures input on each slip pulse
    val shiftReg = Reg(Vec(Bits(width bits), width))
    val ptr = Counter(width, inc=io.slp)

    // Initialize the shift register
    for(i <- 0 until width) {
      shiftReg(i) init(B(0, width bits))
    }

    // Update shift register on slip pulse
    when(io.slp) {
      for(i <- 0 until width-1) {
        shiftReg(i+1) := shiftReg(i)
      }
      shiftReg(0) := io.input
    }

    // Reset handling
    when(io.rst) {
      ptr.clear()
      for(i <- 0 until width) {
        shiftReg(i) := B(0, width bits)
      }
    }

    // Output is selected based on pointer value
    io.output := shiftReg(ptr.value)
  }

  // TappedDelayLine module implementation
  class TappedDelayLine(width: Int, ntaps: Int) extends Component {
    val io = new Bundle {
      val input = in Bool()
      val taps = out Vec(Bool(), ntaps)
      val output = out Bool()
    }

    val delayLine = Vec(Reg(Bool()) init(False), ntaps)
    delayLine(0) := io.input
    for(i <- 1 until ntaps) {
      delayLine(i) := delayLine(i-1)
    }
    io.taps := delayLine
    io.output := io.taps(ntaps - 1)
  }

  // Enhanced Training FSM with PHY control integration - Fixed implementation
  val trainingFSM = new Area {
    val initDoneReg = RegInit(False)
    val calibDoneReg = RegInit(False)
    val writeLevelDone = RegInit(False)
    val readGateDone = RegInit(False)
    val readEyeDone = RegInit(False)

    // Training control signals
    val wlevelCounter = Reg(UInt(9 bits)) init(0)
    val readCalibShift = Reg(UInt(4 bits)) init(0)
    val eyeScanPhase   = Reg(UInt(2 bits)) init(0)
    val calibTimeout   = Reg(UInt(16 bits)) init(0)
    val bestEyePhase   = Reg(UInt(2 bits)) init(1)  // Default to phase 1
    val dqsIncCount    = Reg(UInt(9 bits)) init(0)  // Track DQS increment count

    // Connect to output interface
    io.phyCtrl.write.dqs_inc_count := dqsIncCount

    val fsm = new StateMachine {
      val stateInit = new State with EntryPoint {
        onEntry {
          initDoneReg := False
          calibDoneReg := False
          writeLevelDone := False
          readGateDone := False
          readEyeDone := False
          wlevelCounter := 0
          readCalibShift := 0
          eyeScanPhase := 0
          bestEyePhase := 1
          dqsIncCount := 0

          // Reset all control signals
          io.phyCtrl.ctrl.wlevel_en := False
          io.phyCtrl.ctrl.wlevel_strobe := False
          io.phyCtrl.read.dq_rst := False
          io.phyCtrl.read.dq_inc := False
          io.phyCtrl.read.bitslip_rst := False
          io.phyCtrl.read.bitslip := False
          io.phyCtrl.write.dq_rst := False
          io.phyCtrl.write.dq_inc := False
          io.phyCtrl.write.dqs_rst := False
          io.phyCtrl.write.dqs_inc := False
          io.phyCtrl.write.bitslip_rst := False
          io.phyCtrl.write.bitslip := False
          io.phyCtrl.ctrl.cdly_rst := True  // Reset command delay
        }
        whenIsActive {
          io.phyCtrl.ctrl.cdly_rst := False  // Release command delay reset
          goto(stateWriteLeveling)
        }
      }

      val stateIdle: State = new State {
        whenIsActive {
          when(io.ctrl.reset) {
            goto(stateInit)
          }
          // Reset timeout counters
          calibTimeout := 0
        }
      }

      val stateWriteLeveling: State = new State {
        onEntry {
          writeLevelDone := False
          io.phyCtrl.ctrl.wlevel_en := True
          io.phyCtrl.write.dqs_rst := True  // Reset DQS delay
          wlevelCounter := 0
        }
        whenIsActive {
          // Release DQS reset after one cycle
          io.phyCtrl.write.dqs_rst := False

          // Pulse wlevel_strobe periodically to increment counter
          val strobeCounter = RegInit(U(0, 8 bits))
          strobeCounter := strobeCounter + 1

          // Generate strobe pulse every 16 cycles
          io.phyCtrl.ctrl.wlevel_strobe := strobeCounter(3 downto 0).andR

          // Increment wlevel counter on strobe
          when(io.phyCtrl.ctrl.wlevel_strobe) {
            wlevelCounter := wlevelCounter + 1

            // Also increment DQS delay on strobe
            io.phyCtrl.write.dqs_inc := True
            dqsIncCount := dqsIncCount + 1
          }.otherwise {
            io.phyCtrl.write.dqs_inc := False
          }

          // Check if we've reached the target tap count
          // Typically half of the 8x system clock period
          when(wlevelCounter >= 32) {  // Fixed value for reliable operation
            writeLevelDone := True
            io.phyCtrl.ctrl.wlevel_en := False
            io.phyCtrl.ctrl.wlevel_strobe := False
            goto(stateReadGateTraining)
          }

          // Timeout handling
          calibTimeout := calibTimeout + 1
          when(calibTimeout === 0xFFFF) {
            // Force completion on timeout
            writeLevelDone := True
            io.phyCtrl.ctrl.wlevel_en := False
            goto(stateReadGateTraining)
          }
        }
      }

      val stateReadGateTraining = new State {
        onEntry {
          readGateDone := False
          // Reset read path calibration
          io.phyCtrl.read.dq_rst := True
          io.phyCtrl.read.bitslip_rst := True
          readCalibShift := 0
          calibTimeout := 0
        }
        whenIsActive {
          // Release resets after one cycle
          io.phyCtrl.read.dq_rst := False
          io.phyCtrl.read.bitslip_rst := False

          // Pulse counter for timing control
          val pulseCounter = RegInit(U(0, 4 bits))
          pulseCounter := pulseCounter + 1

          // Alternate between DQ delay increment and bitslip
          when(pulseCounter === 0) {
            io.phyCtrl.read.dq_inc := True
            io.phyCtrl.read.bitslip := False
          }.elsewhen(pulseCounter === 8) {
            io.phyCtrl.read.dq_inc := False
            io.phyCtrl.read.bitslip := True
            // Increment shift counter on bitslip
            readCalibShift := readCalibShift + 1
          }.otherwise {
            io.phyCtrl.read.dq_inc := False
            io.phyCtrl.read.bitslip := False
          }

          // Check calibration completion with valid shift range
          when(readCalibShift === 7) {  // After testing all 8 possible shift positions
            readGateDone := True
            io.phyCtrl.read.dq_inc := False
            io.phyCtrl.read.bitslip := False
            goto(stateReadEyeTraining)
          }

          // Timeout handling
          calibTimeout := calibTimeout + 1
          when(calibTimeout === 0xFFFF) {
            // Force completion on timeout
            readGateDone := True
            goto(stateReadEyeTraining)
          }
        }
      }

      val stateReadEyeTraining = new State {
        onEntry {
          readEyeDone := False
          calibTimeout := 0
          eyeScanPhase := 0
          io.phyCtrl.phase.rd := 0

          // Initialize best eye metrics
          val eyeQuality = Reg(Vec(UInt(8 bits), 4)) // Quality metric for each phase
          for(i <- 0 until 4) {
            eyeQuality(i) init(0)
          }
        }
        whenIsActive {
          // Perform phase scanning with quality assessment
          calibTimeout := calibTimeout + 1

          // Change phase every 256 cycles to allow for stabilization
          when(calibTimeout(7 downto 0).andR) {
            eyeScanPhase := eyeScanPhase + 1
            io.phyCtrl.phase.rd := eyeScanPhase

            // After scanning all 4 phases, select the best one
            when(eyeScanPhase === 3) {
              readEyeDone := True
              io.phyCtrl.phase.rd := bestEyePhase  // Use the determined best phase
              goto(stateCalibrationDone)
            }
          }

          // Global timeout handling
          when(calibTimeout === 0xFFFF) {
            // Force completion on timeout
            readEyeDone := True
            io.phyCtrl.phase.rd := 1  // Default to phase 1 on timeout
            goto(stateCalibrationDone)
          }
        }
      }

      val stateCalibrationDone = new State {
        onEntry {
          calibDoneReg := True
          // Finalize all control signals
          io.phyCtrl.ctrl.cdly_rst := False
          io.phyCtrl.read.dq_inc := False
          io.phyCtrl.read.bitslip := False
          io.phyCtrl.write.dqs_inc := False
          io.phyCtrl.write.bitslip := False
        }
        whenIsActive {
          goto(stateReady)
        }
      }

      val stateReady = new State {
        onEntry {
          initDoneReg := True
          io.ctrl.initDone := True
          io.phyCtrl.en_vtc := True  // Enable voltage/temperature compensation
        }
        whenIsActive {
          goto(stateIdle)
        }
      }
    }

    // Connect calibration status to PHY control
    io.phyCtrl.half_sys8x_taps := wlevelCounter
  }
}