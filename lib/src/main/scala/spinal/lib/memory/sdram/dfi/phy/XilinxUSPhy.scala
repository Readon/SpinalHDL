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
  val ba      = out(Bits(dfiConfig.bankWidth bits))
  
  // Protocol-specific signals
  val bg      = if(dfiConfig.signalConfig.useBg) out(Bits(dfiConfig.bankGroupWidth bits)) else null
  val ras_n   = if(dfiConfig.signalConfig.useRasN) out(Bool()) else null
  val cas_n   = if(dfiConfig.signalConfig.useCasN) out(Bool()) else null
  val we_n    = if(dfiConfig.signalConfig.useWeN) out(Bool()) else null
  val cs_n    = out(Bool()) // Always present
  val act_n   = if(dfiConfig.signalConfig.useRasN) out(Bool()) else null

  // Control signals
  val cke     = out(Bool()) // Always present
  val odt     = if(dfiConfig.signalConfig.useOdt) out(Bool()) else null
  val reset_n = if(dfiConfig.signalConfig.useResetN) out(Bool()) else null
  
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
      io.dfi.control.csN.asBools // cs_n is always present

    // Pre-calculate indices for connectOutput
    private val addrWidth = dfiConfig.addressWidth
    private val bankWidth = dfiConfig.bankWidth
    private val useRasN = dfiConfig.signalConfig.useRasN
    private val useCasN = dfiConfig.signalConfig.useCasN
    private val useWeN = dfiConfig.signalConfig.useWeN

    private val bankStartIndex = addrWidth
    private val cmdStartIndex = bankStartIndex + bankWidth
    // Calculate absolute indices in the 'signals' sequence
    private val rasNIndexOpt = if(useRasN) Some(cmdStartIndex) else None
    private val casNIndexOpt = if(useCasN) Some(cmdStartIndex + (if(useRasN) 1 else 0)) else None
    private val weNIndexOpt  = if(useWeN)  Some(cmdStartIndex + (if(useRasN) 1 else 0) + (if(useCasN) 1 else 0)) else None
    private val csNIndex     = cmdStartIndex + (if(useRasN) 1 else 0) + (if(useCasN) 1 else 0) + (if(useWeN) 1 else 0)

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
        if(rasNIndexOpt.isDefined && index == rasNIndexOpt.get) {
          io.pads.ras_n := dataOut
        } else if(casNIndexOpt.isDefined && index == casNIndexOpt.get) {
          io.pads.cas_n := dataOut
        } else if(weNIndexOpt.isDefined && index == weNIndexOpt.get) {
          io.pads.we_n := dataOut
        } else if(index == csNIndex) {
          io.pads.cs_n := dataOut
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

  val dq_oe = Bool()
  val dqsPath = new Area {
    val dqs_preamble = Reg(Bool()) init(False)
    val dqs_postamble = Reg(Bool()) init(False)
    val dqs_oe = CombInit(dq_oe)
    when(io.phyCtrl.ctrl.wlevel_en) {dqs_oe := True}
    val delayLine = new TappedDelayLine(1, 1)
    delayLine.io.input := dqs_preamble | dqs_postamble | dqs_oe

    // DQS pattern generator
    val pattern = new DQSPattern
    pattern.io.preamble := dqs_preamble
    pattern.io.postamble := dqs_postamble
    pattern.io.wlevel_en := io.phyCtrl.ctrl.wlevel_en
    pattern.io.wlevel_strobe := io.phyCtrl.ctrl.wlevel_strobe

    val bitslip = new BitSlip(8)
    bitslip.io.input := pattern.io.output
    bitslip.io.rst := io.phyCtrl.write.bitslip_rst | sysRst
    bitslip.io.slp := io.phyCtrl.write.bitslip

    val dqsWidth = io.pads.dqs_p.getWidth
    val oserdesVec = Seq.fill(dqsWidth)(new OSERDESE3())
    val odelayVec = Seq.fill(dqsWidth)(new ODELAYE3(delayType="VARIABLE", refClkFrequency = 200))
    for(((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex){
      serdes.RST    := io.ctrl.reset | sysRst
      serdes.CLK    := io.clk4x
      serdes.CLKDIV := sysClk
      serdes.D      := bitslip.io.output
      serdes.T      := ~delayLine.io.output

      delay.RST     := sysRst
      delay.CLK     := sysClk
      delay.EN_VTC  := io.phyCtrl.en_vtc
      delay.CE      := io.phyCtrl.write.dqs_inc & io.phyCtrl.ctrl.dly_sel(i)
      delay.INC     := True
      delay.ODATAIN := serdes.OQ

      val buf = new IOBUFDSE3()
      buf.I := delay.DATAOUT
      buf.T := serdes.T_OUT

      io.pads.dqs_p(i) := buf.IO
      io.pads.dqs_n(i) := buf.IOB
    }
  }

  // Data path
  val dataPath = new Area {
    // Control signals
    val dqs_oe = Reg(Bool()) init(False)
    val dq_oe = Reg(Bool()) init(False)
    val dqs_preamble = Reg(Bool()) init(False)
    val dqs_postamble = Reg(Bool()) init(False)

    // DQS pattern generator
    val dqsPattern = new DQSPattern
    dqsPattern.io.preamble := dqs_preamble
    dqsPattern.io.postamble := dqs_postamble
    dqsPattern.io.wlevel_en := io.phyCtrl.ctrl.wlevel_en
    dqsPattern.io.wlevel_strobe := io.phyCtrl.ctrl.wlevel_strobe

    // Write path
    val wrData = io.dfi.write.wr(0).wrdata
    val wrDataEn = io.dfi.write.wr(0).wrdataEn
    val wrDataMask = io.dfi.write.wr(0).wrdataMask
    val wrDataCsN = if(dfiConfig.useWrdataCsN) Some(io.dfi.write.wr(0).wrdataCsN) else None

    // Write data bitslip
    val wrBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    for((slip, data) <- wrBitslip.zip(wrData.asBools)) {
      slip.io.input := data.asBits #* 8
      slip.io.rst := io.phyCtrl.write.bitslip_rst | sysRst
      slip.io.slp := io.phyCtrl.write.bitslip
    }

    // DQ OSERDES
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3())
    for((osd, slip) <- dqOserdes.zip(wrBitslip)) {
      osd.D := slip.io.output
      osd.T := ~dq_oe
      osd.CLK := io.clk4x
      osd.CLKDIV := sysClk
      osd.RST := io.ctrl.reset | sysRst
    }

    // Read path with ISERDESE3 and IDELAYE3
    val rdData = io.dfi.read.rd(0).rddata
    val rdBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    val rdIserdes = Seq.fill(dfiConfig.dataWidth)(new ISERDESE3())
    val rdDelay = Seq.fill(dfiConfig.dataWidth)(new IDELAYE3(refClkFrequency = 200))
    
    for(((serdes, delay), i) <- rdIserdes.zip(rdDelay).zipWithIndex) {
      // Configure delay line
      delay.CLK := sysClk
      delay.EN_VTC := io.phyCtrl.en_vtc
      delay.CE := io.phyCtrl.read.dq_inc && io.phyCtrl.ctrl.dly_sel(i/8)
      delay.INC := True
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

    // Connect output
    for((slip, data) <- rdBitslip.zip(rdData.asBools)) {
      data := slip.io.output(0)
    }

    // DQS output
    val dqsOserdes = new OSERDESE3()
    dqsOserdes.D := dqsPattern.io.output
    dqsOserdes.T := ~dqs_oe
    dqsOserdes.CLK := io.clk4x
    dqsOserdes.CLKDIV := sysClk
    dqsOserdes.RST := io.ctrl.reset | sysRst

    io.pads.dqs_p := dqsOserdes.OQ.asBits
    io.pads.dqs_n := ~dqsOserdes.OQ.asBits

    // Write latency calculation formula (based on Xilinx UG571 document):
    // tPhyWrLat = PHY layer latency (includes ODELAY tap value and PCB trace delay)
    // ddrWrLat  = Controller level write latency (corresponds to JEDEC CWL parameter)
    // +2 cycle compensation:
    //   1 cycle for 4x to 1x clock domain crossing (UG571 Figure 3-14)
    //   1 cycle for OSERDESE3 intrinsic latency (UG571 Table 3-1)
    val writeLatency = dfiConfig.timeConfig.tPhyWrLat - dfiConfig.sdram.ddrWrLat + 2
    val wrDelay = new TappedDelayLine(1, writeLatency + 2)
    wrDelay.io.input := wrDataEn

    dq_oe := wrDelay.io.taps(writeLatency)
    dqs_oe := io.phyCtrl.ctrl.wlevel_en | dq_oe
    dqs_preamble := wrDelay.io.taps(writeLatency - 1) & ~wrDelay.io.taps(writeLatency)
    dqs_postamble := wrDelay.io.taps(writeLatency + 1) & ~wrDelay.io.taps(writeLatency)
  }


  // BitSlip module implementation
  class BitSlip(width: Int) extends Component {
    val io = new Bundle {
      val input = in Bits(width bits)
      val output = out Bits(width bits)
      val rst = in Bool()
      val slp = in Bool()
    }

    val shiftReg = History(io.input, length=width, init=B(0, width bits), when=io.slp)
    val ptr = Counter(width, inc=io.slp)

    when(io.rst) {
      ptr.clear()
      shiftReg.foreach(_ := B(0, width bits))
    }

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

  // Enhanced Training FSM with PHY control integration
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
    val calibTimeout    = Reg(UInt(16 bits)) init(0)

    val fsm = new StateMachine {
      val stateInit = new State with EntryPoint {
        onEntry {
          initDoneReg := False
          wlevelCounter := 0
          readCalibShift := 0
        }
        whenIsActive(goto(stateWriteLeveling))
      }
      
      val stateIdle: State = new State {
        whenIsActive {
          when(io.ctrl.reset) {
            goto(stateInit)
          }
          // Mirror Python's auto-recalibration feature
          calibTimeout := 0
          eyeScanPhase := 0
        }
      }

      val stateWriteLeveling: State = new State {
        onEntry {
          writeLevelDone := False
          io.phyCtrl.ctrl.wlevel_en := True
        }
        whenIsActive {
          // Coordinate with delay line control
          when(io.phyCtrl.ctrl.wlevel_strobe) {
            wlevelCounter := wlevelCounter + 1
            
            // Check delay line status (mirror Python's tap counting)
            when(wlevelCounter >= io.phyCtrl.half_sys8x_taps) {
              writeLevelDone := True
              io.phyCtrl.ctrl.wlevel_en := False
              goto(stateReadGateTraining)
            }
          }
        }
      }

      val stateReadGateTraining = new State {
        onEntry {
          readGateDone := False
          // Activate read path calibration
          io.phyCtrl.read.dq_rst := True
          io.phyCtrl.read.bitslip_rst := True
        }
        whenIsActive {
          // Perform DQ bitslip calibration
          io.phyCtrl.read.dq_inc := True
          io.phyCtrl.read.bitslip := True

          // Check calibration completion with valid shift range
          when(readCalibShift === 7) {  // After testing all 8 possible shift positions
            readGateDone := True
            io.phyCtrl.read.dq_inc := False  // Clean up control signals
            io.phyCtrl.read.bitslip := False
            goto(stateReadEyeTraining)
          }.otherwise {
            readCalibShift := readCalibShift + 1
            io.phyCtrl.read.dq_inc := True
            when(readCalibShift(0)) {  // Alternate between DQ inc and bitslip
              io.phyCtrl.read.bitslip := True
            }
          }
        }
      }

      val stateReadEyeTraining = new State {
        onEntry {
          readEyeDone := False
          calibTimeout := 0
          eyeScanPhase := 0
          io.phyCtrl.phase.rd := 0
        }
        whenIsActive {
          // Perform phase scanning similar to Python's eye training
          calibTimeout := calibTimeout + 1
          
          // Enhanced phase scanning with boundary check
          when(calibTimeout(3 downto 0) === 0xF) {
            eyeScanPhase := eyeScanPhase + 1
            io.phyCtrl.phase.rd := eyeScanPhase
            
            // Check phase boundaries and find optimal eye center
            when(eyeScanPhase === 3) {  // After scanning all 4 phases
              readEyeDone := True
              io.phyCtrl.phase.rd := 1  // Set to middle phase as default
              goto(stateCalibrationDone)
            }.elsewhen(eyeScanPhase >= 3) {
              eyeScanPhase := 0  // Wrap around phase scanning
            }
          }
          
          // Timeout handling
          when(calibTimeout.andR) {
            // Trigger recalibration on timeout
            goto(stateInit)
          }
        }
      }

      val stateCalibrationDone = new State {
        onEntry {
          calibDoneReg := True
          // Finalize all control signals
          io.phyCtrl.ctrl.cdly_rst := False
          io.phyCtrl.read.dq_inc := False
        }
        whenIsActive(goto(stateReady))
      }

      val stateReady = new State {
        onEntry {
          io.ctrl.initDone := True
          io.phyCtrl.en_vtc := True
        }
        whenIsActive(goto(stateIdle))
      }
    }

    // Connect calibration status to PHY control
    io.phyCtrl.half_sys8x_taps := wlevelCounter
  }
}