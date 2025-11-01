package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.blackbox.xilinx.ultrascale._
import spinal.lib.memory.sdram.dfi._

// Hardware width constants (REQ-CS-013 compliance)
object HardwareWidths {
  val BYTE = 8
  val PATTERN_SEL = 2
  val COUNTER_16 = 16
  val COUNTER_9 = 9
  val COUNTER_8 = 8
  val COUNTER_6 = 6
  val COUNTER_5 = 5
  val COUNTER_4 = 4
  val COUNTER_3 = 3
  val RESPONSE_1 = 1
  val RESPONSE_2 = 2
  val PHASE_COUNT = 4
}

// DQS pattern generator functions to avoid dangling references
object DQSPatterns {
  def DEFAULT: Bits = B"01010101"
  def ALTERNATING: Bits = B"10101010"
  def PREAMBLE: Bits = B"00010101"
  def POSTAMBLE: Bits = B"01010100"
  def STROBE: Bits = B"00000001"
}

// Timing constants
object TimingConstants {
  val STABLE_CYCLES_3 = 3
  val STABLE_CYCLES_4 = 4
  val STABLE_CYCLES_8 = 8
  val GATE_POSITIONS = 32
  val EYE_MAX_DELAY = 256
  val CA_MAX_DELAY = 256
  val TIMEOUT_100 = 100
  val WRITE_TRAINING_TIMEOUT = 100  // Write leveling training timeout cycles
}

// DDR3 JEDEC timing constants
object DDR3TimingConstants {
  val TRCD = 13  // ACT to READ/WRITE delay (cycles)
  val TRP = 13   // PRE to ACT delay (cycles)
  val TRFC = 160 // REFRESH to ACT delay (cycles)
  val TMRD = 4   // MRS to MRS delay (cycles)
  val TZQCS = 64 // ZQCS calibration time (cycles)
  val TRRD = 4   // Row to Row Delay for different ranks (cycles)

  // Power-up and initialization timing constants (REQ-CS-008 compliance)
  val T_PWRUP_CYCLES = 40000   // 200us power-up time (200000ns / 5ns = 40000 cycles)
  val T_RESET_CYCLES = 40000   // 200us reset stabilization time
  val T_CKE_LOW_CYCLES = 10    // Minimum 10 cycles CKE low after reset

  // DQS timing constants (REQ-CS-008 compliance)
  val TCK_DIVISOR = 4          // TCK divisor for DQS initial delay calculation
  val MIN_DELAY = 1            // Minimum delay value for timing calculations
  val WRITE_LATENCY_OFFSET = 1 // Offset for safe write latency calculation

  // System and timing constants (REQ-CS-008 compliance)
  val RESET_TIMEOUT_CYCLES = 1000    // Reset synchronization timeout cycles
  val CDC_TIMEOUT_CYCLES = 1000      // Clock domain crossing timeout cycles
  val MAX_BURST_LENGTH = 8           // Maximum burst length for resource optimization
  val MAX_EYE_DELAY = 256            // Maximum delay for eye training
  val TRAINING_DISTRIBUTOR = 2       // Distributor for training data across byte lanes
}

// DDR commands enum (package-level)
object DdrCmd extends SpinalEnum {
  val NOP, ACT, READ, WRITE, PRE, REF, MRS, ZQCS = newElement()
}

// Initialization state enum (package-level)
object InitState extends SpinalEnum {
  val IDLE, POWER_UP, RESET_STABILIZE, CKE_LOW, MRS_SEQUENCE, ZQ_CALIBRATION, DONE = newElement()
}

// MRS state enum (package-level)
object MrsState extends SpinalEnum {
  val IDLE, MR2, MR3, MR1, MR0, DONE = newElement()
}

// Utility BitSlip object (package-level)
object BitSlip {
  def apply[T <: Data](that: T, length: Int, slip: Bool, whenCond: Bool = null, init: T = null): T = {
    val max = that.getBitsWidth * (length - 1) + 1
    // Use Counter and History from spinal.lib (imported above)
    val ptr = Counter(max, inc = slip) init (max - 2)
    val hist = History(that, length, whenCond, init)
    hist.asBits(ptr, that.getBitsWidth bits).asInstanceOf[T]
  }
}

// Shared command decoding function to eliminate code duplication (REQ-CS-038)
object DdrCommandDecoder {
  def decodeCommand(dfiRasNor: Bool, dfiCasNor: Bool, dfiWeNor: Bool, dfiActNor: Bool,
                    dfiAddress: UInt, addressWidth: Int): SpinalEnumCraft[DdrCmd.type] = {
    val decodedCmd = DdrCmd()

    when((dfiRasNor === False) && (dfiCasNor === True) && (dfiWeNor === True) && (dfiActNor === False)) {
      decodedCmd := DdrCmd.ACT  // ACT: RAS=0, CAS=1, WE=1, ACT=0
    } elsewhen((dfiRasNor === True) && (dfiCasNor === False) && (dfiWeNor === True)) {
      decodedCmd := DdrCmd.READ // READ: RAS=1, CAS=0, WE=1
    } elsewhen((dfiRasNor === True) && (dfiCasNor === False) && (dfiWeNor === False)) {
      decodedCmd := DdrCmd.WRITE // WRITE: RAS=1, CAS=0, WE=0
    } elsewhen((dfiRasNor === False) && (dfiCasNor === False) && (dfiWeNor === True)) {
      decodedCmd := DdrCmd.PRE  // PRE: RAS=0, CAS=0, WE=1
    } elsewhen((dfiRasNor === False) && (dfiCasNor === False) && (dfiWeNor === False)) {
      // MRS/ZQCS/REF discrimination based on address
      val addrBits = if (addressWidth >= 16) {
        dfiAddress(15 downto 14)
      } else if (addressWidth >= 15) {
        dfiAddress(14 downto 13) // Use bits 14:13 for 15-bit addresses
      } else {
        B"00" // Default for smaller addresses
      }
      when(addrBits === U"2'b11") {
        decodedCmd := DdrCmd.ZQCS // ZQCS: A15:A14 = 11
      } elsewhen(addrBits === U"2'b10") {
        decodedCmd := DdrCmd.REF  // REF: A15:A14 = 10
      } otherwise {
        decodedCmd := DdrCmd.MRS  // MRS: A15:A14 = 00 or 01
      }
    } otherwise {
      decodedCmd := DdrCmd.NOP
    }

    decodedCmd
  }
}

// Signal mapping helper - package-level pure-Scala case class (allowed by REQ-CS-037)
case class SignalMapping(padSignal: Bool, dfiSource: Bits)

// DQSPattern module implementation - optimized for resource usage
class DQSPattern(register: Boolean = false) extends Component {
  val io = new Bundle {
    val preamble = in Bool ()
    val postamble = in Bool ()
    val wlevel_en = in Bool ()
    val wlevel_strobe = in Bool ()
    val output = out Bits (HardwareWidths.BYTE bits)
  }

  // Pattern generation logic - optimized with lookup table
  val pattern = Bits(HardwareWidths.BYTE bits)
  val patternSel = UInt(HardwareWidths.PATTERN_SEL bits)

  // Encode pattern selection for better LUT usage
  when(io.wlevel_en) {
    patternSel := io.wlevel_strobe ? U"01" | U"00"  // 0x01 or 0x00
  }.elsewhen(io.preamble) {
    patternSel := U"10"  // 0x15
  }.elsewhen(io.postamble) {
    patternSel := U"11"  // 0x54
  } otherwise {
    patternSel := U"00"  // 0x55 (default)
  }

  // Lookup table for patterns - reduces LUT usage
  switch(patternSel) {
    is(U"00") { pattern := DQSPatterns.DEFAULT } // 0x55
    is(U"01") { pattern := DQSPatterns.STROBE } // 0x01
    is(U"10") { pattern := DQSPatterns.PREAMBLE } // 0x15
    is(U"11") { pattern := DQSPatterns.POSTAMBLE } // 0x54
  }

  // Optional registered output - optimized
  if (register) {
    val reg = Reg(Bits(HardwareWidths.BYTE bits)) init (DQSPatterns.DEFAULT)
    reg := pattern
    io.output := reg
  } else {
    io.output := pattern
  }
}

// DDR Command Generator with JEDEC timing constraints - Pipelined for timing
class DdrCommandGenerator(dfiConfig: DfiConfig) extends Component {
  // Use the shared DdrCmd enum from the parent class

  // Command timing parameters (JEDEC DDR3) - use named constants
  val tRCD = DDR3TimingConstants.TRCD
  val tRP = DDR3TimingConstants.TRP
  val tRFC = DDR3TimingConstants.TRFC
  val tMRD = DDR3TimingConstants.TMRD
  val tZQCS = DDR3TimingConstants.TZQCS

  // Pre-compute timing constants for better resource usage
  val tRCD_U = U(tRCD, HardwareWidths.BYTE bits)
  val tRP_U = U(tRP, HardwareWidths.BYTE bits)
  val tRFC_U = U(tRFC, HardwareWidths.COUNTER_9 bits)
  val tMRD_U = U(tMRD, HardwareWidths.COUNTER_3 bits)
  val tZQCS_U = U(tZQCS, HardwareWidths.COUNTER_8 bits)

  // Command state tracking - pipelined
  val lastCommand = Reg(DdrCmd()) init(DdrCmd.NOP)
  val commandTimer = Reg(UInt(HardwareWidths.COUNTER_16 bits)) init(0)
  val commandValid = Reg(Bool()) init(False)

  // Multi-chip select support
  val activeChipSelect = Reg(UInt(log2Up(dfiConfig.chipSelectNumber) bits)) init(0)

  // Rank-to-rank timing parameters - use named constants
  val tRRD = DDR3TimingConstants.TRRD
  val tRRD_U = U(tRRD, HardwareWidths.COUNTER_3 bits)

  // Rank-specific timing tracking for multi-device support
  val rankLastCommand = Vec.fill(dfiConfig.chipSelectNumber)(Reg(DdrCmd()) init(DdrCmd.NOP))
  val rankCommandTimer = Vec.fill(dfiConfig.chipSelectNumber)(Reg(UInt(HardwareWidths.COUNTER_16 bits)) init(0))

  // Command generation logic - pipelined for timing
  val currentCmd = Reg(DdrCmd()) init(DdrCmd.NOP)
  val cmdAddress = Reg(Bits(dfiConfig.addressWidth bits)) init(0)
  val cmdBank = Reg(Bits(dfiConfig.bankWidth bits)) init(0)
  val cmdCsN = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0) // Initialize to active (0) for proper chip select
  val cmdCke = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)
  val cmdOdt = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)
  val cmdResetN = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)

  // Initialization command inputs - changed to regular signals to allow external assignment
  val initCmdValid = Bool()
  initCmdValid := False  // Default assignment to prevent latches
  val initCmd = DdrCmd()
  initCmd := DdrCmd.NOP  // Default assignment
  val initAddr = Bits(dfiConfig.addressWidth bits)
  initAddr := B(0, dfiConfig.addressWidth bits)  // Default assignment
  val initBa = Bits(dfiConfig.bankWidth bits)
  initBa := B(0, dfiConfig.bankWidth bits)  // Default assignment
  val initCsN = Bits(dfiConfig.chipSelectNumber bits)
  initCsN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)  // Default to inactive
  val initCke = Bits(dfiConfig.chipSelectNumber bits)
  initCke := B(0, dfiConfig.chipSelectNumber bits)  // Default assignment
  val initOdt = Bits(dfiConfig.chipSelectNumber bits)
  initOdt := B(0, dfiConfig.chipSelectNumber bits)  // Default assignment
  val initResetN = Bits(dfiConfig.chipSelectNumber bits)
  initResetN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)  // Default to inactive

  // DFI inputs - input ports cannot have default assignments
  val dfiRasNor = in Bool()
  val dfiCasNor = in Bool()
  val dfiWeNor = in Bool()
  val dfiActNor = in Bool()
  val dfiAddress = in(UInt(dfiConfig.addressWidth bits))
  val dfiCsN = in(Bits(dfiConfig.chipSelectNumber bits))
  val dfiBank = in(Bits(dfiConfig.bankWidth bits))
  val dfiCke = in(Bits(dfiConfig.chipSelectNumber bits))
  val dfiOdt = in(Bits(dfiConfig.chipSelectNumber bits))
  val dfiResetN = in(Bits(dfiConfig.chipSelectNumber bits))

  // Control signals - input ports cannot have default assignments
  val padOverride = in Bool()

  // Pipeline stage 2: Command decoding with registered inputs or init override
  val decodedCmd = DdrCmd()
  // Use pipelined address bits for timing optimization

  // Priority: initialization commands override DFI commands during init
  when(padOverride) {
    // Use initialization commands
    decodedCmd := initCmd
    currentCmd := initCmd
    cmdAddress := initAddr
    cmdBank := initBa
    cmdCsN := initCsN
    cmdCke := initCke
    cmdOdt := initOdt
    cmdResetN := initResetN
    commandValid := initCmdValid
  } otherwise {
    // Normal DFI command decoding using shared function (REQ-CS-038)
    decodedCmd := DdrCommandDecoder.decodeCommand(
      dfiRasNor, dfiCasNor, dfiWeNor, dfiActNor,
      dfiAddress, dfiConfig.addressWidth
    )

    // Pipeline stage 3: Register decoded command and generate outputs - optimized
    val decodedCmdReg = RegNext(decodedCmd) init(DdrCmd.NOP)
    val cmdAddressReg = RegNext(dfiAddress.asBits) init(0)
    // Fixed: Properly handle bank signal with correct width
    val cmdBankReg = RegNext(if (dfiConfig.signalConfig.useBank) {
      dfiBank.orR ? dfiBank.asBits.resize(dfiConfig.bankWidth) | B(0, dfiConfig.bankWidth bits)
    } else {
      B(0, dfiConfig.bankWidth bits)
    }) init(0)
    val cmdCsNReg = RegNext(dfiCsN) init(0) // Align with LiteX: init to active (0) for proper chip select during init

    currentCmd := decodedCmdReg
    cmdAddress := cmdAddressReg
    cmdBank := cmdBankReg
    cmdCsN := cmdCsNReg

    // Command timing validation - pipelined
    val timingValid = Reg(Bool()) init(True)
    val timingValidNext = Bool()

    // Multi-device timing validation: check both global and rank-specific constraints - optimized
    val globalTimingValid = Bool()
    val rankTimingValid = Bool()

    // Pre-compute timing checks to reduce critical path
    val lastCmdReg = RegNext(lastCommand) init(DdrCmd.NOP)
    val cmdTimerReg = RegNext(commandTimer) init(0)

    switch(lastCmdReg) {
      is(DdrCmd.ACT) {
        globalTimingValid := cmdTimerReg >= tRCD_U
      }
      is(DdrCmd.PRE) {
        globalTimingValid := cmdTimerReg >= tRP_U
      }
      is(DdrCmd.REF) {
        globalTimingValid := cmdTimerReg >= tRFC_U
      }
      is(DdrCmd.MRS) {
        globalTimingValid := cmdTimerReg >= tMRD_U
      }
      is(DdrCmd.ZQCS) {
        globalTimingValid := cmdTimerReg >= tZQCS_U
      }
      default {
        globalTimingValid := True
      }
    }

    // Rank-to-rank timing constraints for multi-device operation - optimized
    val rankToRankValid = Bool()
    if (dfiConfig.chipSelectNumber > 1) {
      // Check if any rank violates tRRD constraint - pipelined
      val rankViolations = Vec.fill(dfiConfig.chipSelectNumber)(Bool())
      val rankLastCmdRegs = Vec.fill(dfiConfig.chipSelectNumber)(Reg(DdrCmd()) init(DdrCmd.NOP))
      val rankTimerRegs = Vec.fill(dfiConfig.chipSelectNumber)(Reg(UInt(16 bits)) init(0))

      for (rank <- 0 until dfiConfig.chipSelectNumber) {
        rankLastCmdRegs(rank) := RegNext(rankLastCommand(rank)) init(DdrCmd.NOP)
        rankTimerRegs(rank) := RegNext(rankCommandTimer(rank)) init(0)
        rankViolations(rank) := (rankLastCmdRegs(rank) === DdrCmd.ACT) && (rankTimerRegs(rank) < tRRD_U)
      }
      rankToRankValid := !rankViolations.reduce(_ || _)
    } else {
      rankToRankValid := True
    }

    timingValidNext := globalTimingValid && rankToRankValid
    timingValid := timingValidNext

    // Command execution - pipelined
    commandValid := timingValid && (currentCmd =/= DdrCmd.NOP)
  }

  // Update command timer and last command - pipelined (only for DFI commands)
  val lastCommandNext = DdrCmd()
  val commandTimerNext = UInt(16 bits)

  when(commandValid && !padOverride) {
    lastCommandNext := currentCmd
    commandTimerNext := 0
  } otherwise {
    lastCommandNext := lastCommand
    commandTimerNext := commandTimer + 1
  }

  lastCommand := lastCommandNext
  commandTimer := commandTimerNext

  // Rank-specific command handling and timing constraints
  for (rank <- 0 until dfiConfig.chipSelectNumber) {
    val rankCommandValid = commandValid && cmdCsN(rank) === False // Command targets this rank
    val rankLastCommandNext = DdrCmd()
    val rankCommandTimerNext = UInt(16 bits)

    when(rankCommandValid && !padOverride) {
      rankLastCommandNext := currentCmd
      rankCommandTimerNext := 0
    } otherwise {
      rankLastCommandNext := rankLastCommand(rank)
      rankCommandTimerNext := rankCommandTimer(rank) + 1
    }

    rankLastCommand(rank) := rankLastCommandNext
    rankCommandTimer(rank) := rankCommandTimerNext
  }

  // Bank assignment based on command - pipelined
  when(!padOverride) {
    if (dfiConfig.signalConfig.useBank) {
      cmdBank := dfiBank.orR ? dfiBank.asBits.resize(dfiConfig.bankWidth) | B(0, dfiConfig.bankWidth bits)
    }
  }

  // Multi-chip select handling - pipelined
  when(cmdCsN === 0) { // All chips selected
    activeChipSelect := 0
  } otherwise {
    // Find first active chip select - multi-device routing
    activeChipSelect := OHToUInt(cmdCsN)
  }

  // Multi-chip select command routing: ensure commands are properly routed to selected chips
  val chipSelectMask = Reg(Bits(dfiConfig.chipSelectNumber bits)) init((BigInt(1) << dfiConfig.chipSelectNumber) - 1)
  chipSelectMask := cmdCsN // Store the chip select mask for command routing

  // CKE control - per chip with proper timing - optimized with pipelining and reduced resources
  val ckeTimer = Vec.fill(dfiConfig.chipSelectNumber)(Reg(UInt(6 bits)) init(0)) // Further reduced width for LUT optimization
  val ckeControlReg = Vec.fill(dfiConfig.chipSelectNumber)(Reg(Bool()) init(False))
  // Fixed: Use appropriate width for timing registers
  val rankTimerRegs = Vec.fill(dfiConfig.chipSelectNumber)(Reg(UInt(16 bits)) init(0)) // Use wider timer to avoid truncation

  for (i <- 0 until dfiConfig.chipSelectNumber) {
    rankTimerRegs(i) := RegNext(rankCommandTimer(i)) init(0)

    when(dfiCke(i)) {
      ckeTimer(i) := 0
    } otherwise {
      ckeTimer(i) := ckeTimer(i) + 1
    }
    when(!padOverride) {
      // Multi-device CKE control: respect rank-specific timing and global constraints - pipelined and optimized
      val rankCkeValid = rankTimerRegs(i) >= U(5) // Minimum time between commands for this rank
      val ckeTimerValid = ckeTimer(i) >= U(10) // Minimum CKE high time
      ckeControlReg(i) := dfiCke(i) && ckeTimerValid && rankCkeValid
      cmdCke(i) := ckeControlReg(i)
    }
  }

  // ODT control - per chip with command-based activation - pipelined and optimized
  val odtActive = Reg(Bool()) init(False)
  val odtActiveNext = Bool()
  val odtControlReg = Vec.fill(dfiConfig.chipSelectNumber)(Reg(Bool()) init(False))

  switch(currentCmd) {
    is(DdrCmd.READ, DdrCmd.WRITE) {
      odtActiveNext := True
    }
    default {
      odtActiveNext := False
    }
  }
  odtActive := odtActiveNext

  // Per-chip ODT control with multi-device support - optimized
  for (i <- 0 until dfiConfig.chipSelectNumber) {
    when(!padOverride) {
      // Multi-device ODT: respect per-chip control and rank-specific timing - pipelined and optimized
      val rankOdtValid = rankTimerRegs(i) >= U(2) // Minimum ODT timing for this rank
      val chipSelectActive = cmdCsN(i) === False // This chip is selected
      odtControlReg(i) := dfiOdt(i) && odtActive && chipSelectActive && rankOdtValid
      cmdOdt(i) := odtControlReg(i)
    }
  }

  // Reset control - per chip - pipelined
  for (i <- 0 until dfiConfig.chipSelectNumber) {
    when(!padOverride) {
      cmdResetN(i) := dfiResetN(i)
    }
  }
}

// Helper class to manage command/address/bank signals and their output connections
class CmdSignalHandler(dfiConfig: DfiConfig) extends Component {
  def regroupSignals(input: Bits, width: Int): Vec[Bits] = {
    val slices = input.subdivideIn(width bits)
    Vec.tabulate(width) { i =>
      Cat(slices.map(_(i)))
    }
  }

  // IO interface for external signals
  val io = new Bundle {
    // Interface for external DFI signals
    val dfi = new Bundle {
      val rasNor = in Bool()
      val casNor = in Bool()
      val weNor = in Bool()
      val actNor = in Bool()
      val address = in UInt(dfiConfig.addressWidth bits)
      val csN = in Bits(dfiConfig.chipSelectNumber bits)
      val bank = in Bits(dfiConfig.bankWidth bits)
      val cke = in Bits(dfiConfig.chipSelectNumber bits)
      val odt = in Bits(dfiConfig.chipSelectNumber bits)
      val resetN = in Bits(dfiConfig.chipSelectNumber bits)
    }

    // Interface for initialization commands
    val init = new Bundle {
      val cmdValid = in Bool()
      val cmd = in(DdrCmd())
      val addr = in(Bits(dfiConfig.addressWidth bits))
      val ba = in(Bits(dfiConfig.bankWidth bits))
      val csN = in(Bits(dfiConfig.chipSelectNumber bits))
      val cke = in(Bits(dfiConfig.chipSelectNumber bits))
      val odt = in(Bits(dfiConfig.chipSelectNumber bits))
      val resetN = in(Bits(dfiConfig.chipSelectNumber bits))
    }

    // Control signals
    val padOverride = in Bool()
  }

  // Instantiate DDR command generator
  val cmdGen = new DdrCommandGenerator(dfiConfig)

  val pads = out(new SdramIO(dfiConfig))

  // Connect DFI signals to command generator
  cmdGen.dfiRasNor := io.dfi.rasNor
  cmdGen.dfiCasNor := io.dfi.casNor
  cmdGen.dfiWeNor := io.dfi.weNor
  cmdGen.dfiActNor := io.dfi.actNor
  cmdGen.dfiAddress := io.dfi.address
  cmdGen.dfiCsN := io.dfi.csN
  cmdGen.dfiBank := io.dfi.bank
  cmdGen.dfiCke := io.dfi.cke
  cmdGen.dfiOdt := io.dfi.odt
  cmdGen.dfiResetN := io.dfi.resetN

  // Synchronize DFI inputs for better timing - avoid direct access to child component signals
  val cmdAddressReg = Reg(Bits(dfiConfig.addressWidth bits)) init(0)
  val cmdBankReg = Reg(Bits(dfiConfig.bankWidth bits)) init(0)
  val commandValidReg = Reg(Bool()) init(False)
  
  // Update registers from DFI signals directly
  cmdAddressReg := io.dfi.address.asBits
  cmdBankReg := io.dfi.bank
  commandValidReg := True // Always valid when DFI signals are present
  
  val syncedAddressBits = RegNextWhen(cmdAddressReg, commandValidReg)
  val syncedBankBits = RegNextWhen(cmdBankReg, commandValidReg)

  // Expose synced signals as outputs to avoid hierarchy violations
  val syncedAddressOut = out(Vec.fill(dfiConfig.addressWidth)(Bool()))
  val syncedBankOut = out(Vec.fill(dfiConfig.bankWidth)(Bool()))

  // Connect internal synced signals to outputs
  for (i <- 0 until dfiConfig.addressWidth) {
    syncedAddressOut(i) := syncedAddressBits(i downto i).asBool
  }
  for (i <- 0 until dfiConfig.bankWidth) {
    syncedBankOut(i) := syncedBankBits(i downto i).asBool
  }

  // Define signal mappings based on pads structure
  val signalMappings = new scala.collection.mutable.ArrayBuffer[SignalMapping]()

  // Create mappings for each pad signal
  // Address signals - always present (with init override support)
  for (i <- 0 until dfiConfig.addressWidth) {
    val addressSignal = Bool()
    // Fixed: Use init override during initialization to align with LiteX semantics
    when(io.padOverride) {
      addressSignal := io.init.addr(i)
    } otherwise {
      addressSignal := syncedAddressBits(i)
    }
    signalMappings += SignalMapping(pads.a(i), addressSignal.asBits)
  }

  // Bank signals - if used (with init override support)
  if (dfiConfig.signalConfig.useBank) {
    for (i <- 0 until dfiConfig.bankWidth) {
      val bankSignal = Bool()
      // Fixed: Use init override during initialization to align with LiteX semantics
      when(io.padOverride) {
        bankSignal := io.init.ba(i)
      } otherwise {
        bankSignal := syncedBankBits(i)
      }
      signalMappings += SignalMapping(pads.ba(i), bankSignal.asBits)
    }
  }

  // RAS_N signals - if used (generated from command)
  if (dfiConfig.signalConfig.useRasN) {
    val rasN = Bits(dfiConfig.controlWidth bits)
    val currentCmdReg = Reg(DdrCmd()) init(DdrCmd.NOP)

    // Decode command from DFI signals using shared function (REQ-CS-038)
    currentCmdReg := DdrCommandDecoder.decodeCommand(
      io.dfi.rasNor, io.dfi.casNor, io.dfi.weNor, io.dfi.actNor,
      io.dfi.address, dfiConfig.addressWidth
    )
    
    switch(currentCmdReg) {
      is(DdrCmd.ACT, DdrCmd.PRE, DdrCmd.REF, DdrCmd.MRS, DdrCmd.ZQCS) {
        rasN := 0
      }
      default {
        rasN := (1 << dfiConfig.controlWidth) - 1
      }
    }
    val rasNSignals = regroupSignals(rasN, dfiConfig.controlWidth)
    for (i <- 0 until dfiConfig.controlWidth) {
      signalMappings += SignalMapping(pads.ras_n(i), rasNSignals(i))
    }
  }

  // CAS_N signals - if used (generated from command)
  if (dfiConfig.signalConfig.useCasN) {
    val casN = Bits(dfiConfig.controlWidth bits)
    val currentCmdReg = Reg(DdrCmd()) init(DdrCmd.NOP)

    // Decode command from DFI signals using shared function (REQ-CS-038)
    currentCmdReg := DdrCommandDecoder.decodeCommand(
      io.dfi.rasNor, io.dfi.casNor, io.dfi.weNor, io.dfi.actNor,
      io.dfi.address, dfiConfig.addressWidth
    )
    
    switch(currentCmdReg) {
      is(DdrCmd.READ, DdrCmd.WRITE, DdrCmd.REF, DdrCmd.MRS, DdrCmd.ZQCS) {
        casN := 0
      }
      default {
        casN := (1 << dfiConfig.controlWidth) - 1
      }
    }
    val casNSignals = regroupSignals(casN, dfiConfig.controlWidth)
    for (i <- 0 until dfiConfig.controlWidth) {
      signalMappings += SignalMapping(pads.cas_n(i), casNSignals(i))
    }
  }

  // WE_N signals - if used (generated from command)
  if (dfiConfig.signalConfig.useWeN) {
    val weN = Bits(dfiConfig.controlWidth bits)
    val currentCmdReg = Reg(DdrCmd()) init(DdrCmd.NOP)

    // Decode command from DFI signals using shared function (REQ-CS-038)
    currentCmdReg := DdrCommandDecoder.decodeCommand(
      io.dfi.rasNor, io.dfi.casNor, io.dfi.weNor, io.dfi.actNor,
      io.dfi.address, dfiConfig.addressWidth
    )
    
    switch(currentCmdReg) {
      is(DdrCmd.WRITE, DdrCmd.PRE, DdrCmd.REF, DdrCmd.MRS, DdrCmd.ZQCS) {
        weN := 0
      }
      default {
        weN := (1 << dfiConfig.controlWidth) - 1
      }
    }
    val weNSignals = regroupSignals(weN, dfiConfig.controlWidth)
    for (i <- 0 until dfiConfig.controlWidth) {
      signalMappings += SignalMapping(pads.we_n(i), weNSignals(i))
    }
  }

  // CS_N signals - always present (with init override support)
  val cmdCsNReg = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)
  // Fixed: Use init override during initialization to align with LiteX semantics
  when(io.padOverride) {
    cmdCsNReg := io.init.csN
  } otherwise {
    cmdCsNReg := io.dfi.csN
  }
  val csNSignals = regroupSignals(cmdCsNReg, dfiConfig.chipSelectNumber)
  for (i <- 0 until dfiConfig.chipSelectNumber) {
    signalMappings += SignalMapping(pads.cs_n(i), csNSignals(i))
  }

  // CKE signals - always present (with init override support)
  val cmdCkeReg = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)
  // Fixed: Use init override during initialization to align with LiteX semantics
  when(io.padOverride) {
    cmdCkeReg := io.init.cke
  } otherwise {
    cmdCkeReg := io.dfi.cke
  }
  val ckeSignals = regroupSignals(cmdCkeReg, dfiConfig.chipSelectNumber)
  for (i <- 0 until dfiConfig.chipSelectNumber) {
    signalMappings += SignalMapping(pads.cke(i), ckeSignals(i))
  }

  // ODT signals - if used (with init override support)
  if (dfiConfig.signalConfig.useOdt) {
    val cmdOdtReg = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)
    // Fixed: Use init override during initialization to align with LiteX semantics
    when(io.padOverride) {
      cmdOdtReg := io.init.odt
    } otherwise {
      cmdOdtReg := io.dfi.odt
    }
    val odtSignals = regroupSignals(cmdOdtReg, dfiConfig.chipSelectNumber)
    for (i <- 0 until dfiConfig.chipSelectNumber) {
      signalMappings += SignalMapping(pads.odt(i), odtSignals(i))
    }
  }

  // ResetN signals - if used (with init override support)
  if (dfiConfig.signalConfig.useResetN) {
    val cmdResetNReg = Reg(Bits(dfiConfig.chipSelectNumber bits)) init(0)
    // Fixed: Use init override during initialization to align with LiteX semantics
    when(io.padOverride) {
      cmdResetNReg := io.init.resetN
    } otherwise {
      cmdResetNReg := io.dfi.resetN
    }
    val resetNSignals = regroupSignals(cmdResetNReg, dfiConfig.chipSelectNumber)
    for (i <- 0 until dfiConfig.chipSelectNumber) {
      signalMappings += SignalMapping(pads.reset_n(i), resetNSignals(i))
    }
  }

  // ACT_N signal - if used
  if (dfiConfig.signalConfig.useAckN) {
    val currentCmdReg = Reg(DdrCmd()) init(DdrCmd.NOP)

    // Decode command from DFI signals using shared function (REQ-CS-038)
    currentCmdReg := DdrCommandDecoder.decodeCommand(
      io.dfi.rasNor, io.dfi.casNor, io.dfi.weNor, io.dfi.actNor,
      io.dfi.address, dfiConfig.addressWidth
    )
    
    signalMappings += SignalMapping(pads.act_n, (currentCmdReg === DdrCmd.ACT).asBits)
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

  // Method to get DFI source signal without hierarchy violations
  def getDfiSource(index: Int): Bits = {
    if (index < signalMappings.length) {
      signalMappings(index).dfiSource
    } else {
      B(0, 1 bits) // Default fallback
    }
  }

  // Note: DdrCommandGenerator connections removed to prevent hierarchy violations
  // The CmdSignalHandler now handles initialization logic internally using io.init signals
}

// Complete Training Controller with proper DFI integration - optimized
class TrainingController(config: DfiConfig) extends Component {

  val io = new Bundle {
    // Input signals (moved from constructor parameters)
    val initDone = in Bool()
    val writeLevelingSampledData = in Bits(HardwareWidths.BYTE bits)
    val readGateSampledData = in Bits(HardwareWidths.BYTE bits)
    val readEyeSampledData = in(Vec(Bits(HardwareWidths.BYTE bits), HardwareWidths.PHASE_COUNT))
    val caSampledAddr = in Bits(config.addressWidth bits)
    val caSampledBank = in Bits(HardwareWidths.BYTE bits)
    val caCurrentCmd = in(DdrCmd())
    val wrLvlEn = in Bool()
    val wrLvlStrobe = in Bool()
    val rdLvlEn = in Bool()
    val rdLvlGateEn = in Bool()
    val caLvlEn = in Bool()

    // PHY control interface outputs
    val half_sys8x_taps = out UInt(HardwareWidths.COUNTER_9 bits)
    val dqs_inc_count = out UInt(HardwareWidths.COUNTER_9 bits)
    val cdly_value = out UInt(HardwareWidths.COUNTER_9 bits)
    val training_cdly_inc = out Bool()
    val training_dq_inc = out Bool()
    val training_bitslip = out Bool()

    // Expose training status signals as outputs to avoid hierarchy violations
    val writeLevelingDone = out Bool()
    val readGateDone = out Bool()
    val readEyeDone = out Bool()
    val caTrainingDone = out Bool()

    // Expose training response signals as outputs to avoid hierarchy violations
    val readGateResponse = out Bits(HardwareWidths.RESPONSE_1 bits)
    val readEyeResponse = out Bits(HardwareWidths.RESPONSE_1 bits)
    val writeLevelingResponse = out Bits(HardwareWidths.RESPONSE_1 bits)
    val caTrainingResponse = out Bits(HardwareWidths.RESPONSE_2 bits)

    // Expose cdly_value as output to avoid hierarchy violations
    val cdly_value_out = out UInt(HardwareWidths.COUNTER_9 bits)
  }

  // Use input parameters directly to avoid hierarchy violations
  // These signals are already registered in the parent XilinxUSPhy component
  // Signal aliases removed - use original signals directly per coding standards

  // Create training modules with proper signal isolation using local registered signals
  val writeLevelingModule = if (config.useWrlvlEn) {
    val module = new WriteLevelingModule(config)
    module.io.wrLvlEn := io.wrLvlEn
    module.io.wrLvlStrobe := io.wrLvlStrobe
    module.io.sampledData := io.writeLevelingSampledData
    module
  } else null
  val readGateModule = if (config.useRdlvlEn) {
    val module = new ReadGateModule(config)
    module.io.rdLvlEn := io.rdLvlEn
    module.io.sampledData := io.readGateSampledData
    module
  } else null
  val readEyeModule = if (config.useRdlvlGateEn) {
    val module = new ReadEyeModule(config, io.readEyeSampledData.length)
    module.io.rdLvlGateEn := io.rdLvlGateEn
    module.io.sampledData := io.readEyeSampledData
    module
  } else null
  val caTrainingModule = if (config.useCalvlEn) {
    val module = new CATrainingModule(config)
    module.io.caLvlEn := io.caLvlEn
    module.io.sampledAddr := io.caSampledAddr
    module.io.sampledBank := io.caSampledBank
    module.io.currentCmd := io.caCurrentCmd
    module
  } else null

  // Centralized DQ increment control - single assignment only
  val writeLevelingDqInc = if (config.useWrlvlEn && writeLevelingModule != null) {
    writeLevelingModule.io.dqIncrement
  } else {
    False
  }
  val readEyeDqInc = if (config.useRdlvlGateEn && readEyeModule != null) {
    readEyeModule.io.dqIncrement
  } else {
    False
  }
  val dqIncrementControl = writeLevelingDqInc || readEyeDqInc

  // Centralized CDLY increment control - single assignment only
  val caTrainingCdlyInc = if (config.useCalvlEn && caTrainingModule != null) {
    caTrainingModule.io.cdlyIncrement
  } else {
    False
  }
  val cdlyIncrementControl = caTrainingCdlyInc

  // Output signals are now defined in io Bundle per coding standards

  // Connect to phy control interface - pipelined (only if modules exist)
  // Use conditional assignment to avoid conflicts
  io.half_sys8x_taps := 0
  io.training_cdly_inc := cdlyIncrementControl
  io.training_dq_inc := dqIncrementControl
  io.training_bitslip := False
  
  // Assign cdly_value and dqs_inc_count with conditional logic
  if (config.useWrlvlEn && writeLevelingModule != null) {
    io.cdly_value := writeLevelingModule.io.cdlyCount
    io.cdly_value_out := writeLevelingModule.io.cdlyCount
    io.dqs_inc_count := writeLevelingModule.io.dqsIncCount
  } else {
    io.cdly_value := 0
    io.cdly_value_out := 0
    io.dqs_inc_count := 0
  }

  // Training output assignments using conditional Area generation
  if (config.useWrlvlEn) {
    val writeLevelingArea = new Area {
      io.writeLevelingDone := writeLevelingModule.io.done
      io.writeLevelingResponse := writeLevelingModule.io.response
    }
  } else {
    val writeLevelingArea = new Area {
      io.writeLevelingDone := False
      io.writeLevelingResponse := B(0, 1 bits)
    }
  }

  if (config.useRdlvlEn) {
    val readGateArea = new Area {
      io.readGateDone := readGateModule.io.done
      io.readGateResponse := readGateModule.io.response
    }
  } else {
    val readGateArea = new Area {
      io.readGateDone := False
      io.readGateResponse := B(0, 1 bits)
    }
  }

  if (config.useRdlvlGateEn) {
    val readEyeArea = new Area {
      io.readEyeDone := readEyeModule.io.done
      io.readEyeResponse := readEyeModule.io.response
    }
  } else {
    val readEyeArea = new Area {
      io.readEyeDone := False
      io.readEyeResponse := B(0, 1 bits)
    }
  }

  if (config.useCalvlEn) {
    val caTrainingArea = new Area {
      io.caTrainingDone := caTrainingModule.io.done
      io.caTrainingResponse := caTrainingModule.io.response
    }
  } else {
    val caTrainingArea = new Area {
      io.caTrainingDone := False
      io.caTrainingResponse := B(0, 2 bits)
    }
  }

  val fsm = new StateMachine {
    val idle = new State with EntryPoint
    val wrLevel = if (config.useWrlvlEn) new State else null
    val rdGate = if (config.useRdlvlEn) new State else null
    val rdEye = if (config.useRdlvlGateEn) new State else null
    val caTrain = if (config.useCalvlEn) new State else null
    val done = new State

    idle.whenIsActive {
      // Only check training enables if corresponding modules exist
      // This prevents hierarchy violations when training is disabled
      if (config.useWrlvlEn && wrLevel != null) {
        when(io.wrLvlEn) {
          goto(wrLevel)
        }
      }
      if (config.useRdlvlEn && rdGate != null) {
        when(io.rdLvlEn) {
          goto(rdGate)
        }
      }
      if (config.useRdlvlGateEn && rdEye != null) {
        when(io.rdLvlGateEn) {
          goto(rdEye)
        }
      }
      if (config.useCalvlEn && caTrain != null) {
        when(io.caLvlEn) {
          goto(caTrain)
        }
      }
    }

    if (config.useWrlvlEn && wrLevel != null) {
      wrLevel.whenIsActive {
        when(writeLevelingModule.io.done) {
          goto(idle)
        }
      }
    }

    if (config.useRdlvlEn && rdGate != null) {
      rdGate.whenIsActive {
        when(readGateModule.io.done) {
          goto(idle)
        }
      }
    }

    if (config.useRdlvlGateEn && rdEye != null) {
      rdEye.whenIsActive {
        when(readEyeModule.io.done) {
          goto(idle)
        }
      }
    }

    if (config.useCalvlEn && caTrain != null) {
      caTrain.whenIsActive {
        when(caTrainingModule.io.done) {
          goto(idle)
        }
      }
    }

    done.whenIsActive {
      // Note: initDone assignment handled externally to avoid overlap
      goto(idle)
    }
  }
}

class WriteLevelingModule(config: DfiConfig) extends Component {
  val io = new Bundle {
    val wrLvlEn = in Bool()
    val wrLvlStrobe = in Bool()
    val sampledData = in Bits(HardwareWidths.BYTE bits)
    val done = out Bool()
    val cdlyCount = out UInt(HardwareWidths.COUNTER_9 bits)
    val dqsIncCount = out UInt(HardwareWidths.COUNTER_9 bits)
    val response = out Bits(config.writeLevelingResponseWidth bits)
    val dqIncrement = out Bool() // Output to be connected externally
  }

  // Use input signals from io Bundle instead of constructor parameters
  val wrLvlEnReg = io.wrLvlEn
  val wrLvlStrobeReg = io.wrLvlStrobe
  val sampledDataReg = io.sampledData

  // Write leveling pattern generation - alternating 0x55/0xAA pattern (aligned with LiteX)
  val patternGenerator = new Area {
    val pattern = Reg(Bits(HardwareWidths.BYTE bits)) init(DQSPatterns.DEFAULT) // Start with 0x55
    val patternToggle = RegInit(False)

    when(wrLvlEnReg) {
      patternToggle := !patternToggle
      // Fixed: Align with LiteX pattern generation - alternate between 0x55 and 0xAA
      pattern := patternToggle ? DQSPatterns.ALTERNATING | DQSPatterns.DEFAULT // Alternate between 0x55 and 0xAA
    } otherwise {
      patternToggle := patternToggle
      // Fixed: Maintain pattern when not in write leveling mode
      pattern := pattern
    }
  }

  // DQS delay line control for write leveling
  val dqsDelayControl = new Area {
    val delayCounter = Reg(UInt(HardwareWidths.COUNTER_9 bits)) init(0)

    // Increment delay during training sweeps
    when(wrLvlEnReg && wrLvlStrobeReg) {
      delayCounter := delayCounter + 1
    }
  }

  // Write leveling completion detection (aligned with LiteX)
  val completionDetector = new Area {
    val doneReg = RegInit(False)
    val patternMatch = RegInit(False)
    val timeoutCounter = Reg(UInt(HardwareWidths.COUNTER_8 bits)) init(0)
    val stableCounter = Reg(UInt(HardwareWidths.COUNTER_4 bits)) init(0) // Require stable pattern for multiple cycles

    // Sample received pattern during strobe from actual DQ sampling
    val receivedPattern = Reg(Bits(HardwareWidths.BYTE bits)) init(0)
    val expectedPattern = patternGenerator.pattern
    val currentMatch = Bool()

    // Default assignment to prevent latch
    currentMatch := False

    when(wrLvlEnReg && wrLvlStrobeReg) {
      // Sample from actual DQ pins through read path
      // Connect to first byte lane for write leveling feedback
      receivedPattern := sampledDataReg
      currentMatch := receivedPattern === expectedPattern
      
      // Fixed: Require stable pattern for multiple cycles (aligned with LiteX)
      when(currentMatch) {
        stableCounter := stableCounter + 1
      } otherwise {
        stableCounter := 0
      }
      
      patternMatch := stableCounter >= TimingConstants.STABLE_CYCLES_3 // Require 3 consecutive matches
      timeoutCounter := timeoutCounter + 1
    } otherwise {
      receivedPattern := receivedPattern
      patternMatch := patternMatch
      timeoutCounter := timeoutCounter
      stableCounter := stableCounter
    }

    // Complete when stable pattern matches or timeout reached
    when(patternMatch || timeoutCounter >= TimingConstants.WRITE_TRAINING_TIMEOUT) {
      doneReg := True
    }
  }

  // Centralized output assignments to avoid conflicts
  io.done := completionDetector.doneReg
  io.cdlyCount := dqsDelayControl.delayCounter
  io.dqsIncCount := dqsDelayControl.delayCounter
  io.response := completionDetector.patternMatch ? B"1" | B"0"
  io.dqIncrement := wrLvlEnReg && wrLvlStrobeReg // Increment only during active training with strobe
}

class ReadGateModule(config: DfiConfig) extends Component {
  val io = new Bundle {
    val rdLvlEn = in Bool()
    val sampledData = in Bits(HardwareWidths.BYTE bits)
    val done = out Bool()
    val bitslip = out Bool()
    val dq_inc = out Bool()
    val response = out Bits(config.readLevelingResponseWidth bits)
  }

  // Use input signals from io Bundle instead of constructor parameters
  val rdLvlEnReg = io.rdLvlEn
  val sampledDataReg = io.sampledData

  // Read gate training implementation - Fixed bit width
  val gateTraining = new Area {
    val shiftCounter = Reg(UInt(6 bits)) init(0) // Sweep through gate positions
    val pulseCounter = Reg(UInt(5 bits)) init(0) // Fixed: Increased to 5 bits to handle comparison with U"10000"
    val gateFound = RegInit(False)
    val doneReg = RegInit(False)

    // Training pattern recognition - look for valid read data window (aligned with LiteX)
    val patternRecognizer = new Area {
      val receivedData = Reg(Bits(8 bits)) init(0)
      val expectedPattern = DQSPatterns.DEFAULT // Expected read gate training pattern (alternating 0/1)
      val patternValid = receivedData === expectedPattern
      val validWindowCounter = Reg(UInt(3 bits)) init(0)

      // Count consecutive valid patterns to confirm stable window
      when(patternValid) {
        validWindowCounter := validWindowCounter + 1
      } otherwise {
        validWindowCounter := 0
      }

      val stableWindow = validWindowCounter >= 3 // Require 3 consecutive valid patterns
      
      // Sample actual data from DQ pins through read path - Fixed: Use real sampling
      // Connect to first byte lane for read gate training
      when(rdLvlEnReg) {
        receivedData := sampledDataReg
      } otherwise {
        receivedData := receivedData
      }
    }

    // Control signals for delay and bitslip
    val dqIncrement = RegInit(False)
    val bitslipTrigger = RegInit(False)
    // Assign dqIncrement and bitslipTrigger based on training logic
    dqIncrement := rdLvlEnReg && (pulseCounter < U(8))
    bitslipTrigger := rdLvlEnReg && (pulseCounter >= U(8)) && (pulseCounter < U(16)) && (pulseCounter(2 downto 0) === 0)

    when(rdLvlEnReg) {
      pulseCounter := pulseCounter + 1

      // Phase 1: Increment DQ delay to find initial alignment
      when(pulseCounter < U(8)) { // Fixed: Use U() for comparison
        dqIncrement := True
        bitslipTrigger := False
      }
      // Phase 2: Use bitslip to fine-tune gate position
      .elsewhen(pulseCounter >= U(8) && pulseCounter < U(16)) { // Fixed: Use U() for comparison
        dqIncrement := False
        bitslipTrigger := (pulseCounter(2 downto 0) === 0) // Bitslip every 8 pulses
      }
      .otherwise {
        dqIncrement := False
        bitslipTrigger := False
      }

      // Check for valid read window
      when(patternRecognizer.stableWindow && !gateFound) {
        gateFound := True
      }

      // Move to next gate position when bitslip occurs
      when(bitslipTrigger) {
        shiftCounter := shiftCounter + 1
      }

      // Complete when gate found or max positions reached
      when(gateFound || shiftCounter >= U(32)) { // Fixed: Use U() for comparison
        doneReg := True
      }
    }
  }

  // Centralized output assignments to avoid conflicts
  io.done := gateTraining.doneReg
  io.dq_inc := gateTraining.dqIncrement
  io.bitslip := gateTraining.bitslipTrigger
  io.response := gateTraining.gateFound ? B"1" | B"0"
}

class ReadEyeModule(config: DfiConfig, dataSampleCount: Int) extends Component {
  val io = new Bundle {
    val rdLvlGateEn = in Bool()
    val sampledData = in(Vec(Bits(8 bits), dataSampleCount))
    val done = out Bool()
    val phase = out UInt(2 bits)
    val response = out Bits(config.readLevelingResponseWidth bits)
    val dqIncrement = out Bool() // Output to be connected externally
  }

  // Use input signals from io Bundle instead of constructor parameters
  val rdLvlGateEnReg = io.rdLvlGateEn
  val sampledDataReg = io.sampledData

  // Read eye training with delay sweep implementation
  val eyeTraining = new Area {
    val delayCounter = Reg(UInt(9 bits)) init(0) // Sweep through delay taps
    val phaseReg = Reg(UInt(2 bits)) init(0) // Current phase (0°, 90°, 180°, 270°)
    val eyeFound = RegInit(False)
    val doneReg = RegInit(False)
    val timeout = Reg(UInt(16 bits)) init(0)

    // Training pattern recognition for eye training (aligned with LiteX)
    val patternRecognizer = new Area {
      val receivedData = Vec.fill(4)(Reg(Bits(8 bits)) init(B(0, 8 bits))) // Data from all phases
      val expectedPattern = B"10101010" // Expected read eye training pattern (alternating 1/0)
      val phaseValid = Vec.fill(4)(Bool())

      // Check validity for each phase
      for (i <- 0 until 4) {
        phaseValid(i) := receivedData(i) === expectedPattern
      }

      val allPhasesValid = phaseValid.reduce(_ && _)
      val validEyeCounter = Reg(UInt(4 bits)) init(0)

      // Count consecutive valid samples for each phase
      when(allPhasesValid) {
        validEyeCounter := validEyeCounter + 1
      } otherwise {
        validEyeCounter := 0
      }

      val stableEye = validEyeCounter >= 8 // Require 8 consecutive valid samples
      
      // Sample actual data from DQ pins through read path for each phase - Fixed: Use real sampling
      // Use direct assignments to avoid hierarchy violations
      for (i <- 0 until 4) {
        // Sample from different byte lanes for multi-phase eye training
        if (i < sampledDataReg.length) {
          receivedData(i) := rdLvlGateEnReg ? sampledDataReg(i) | receivedData(i)
        } else {
          receivedData(i) := rdLvlGateEnReg ? B(0, 8 bits) | receivedData(i) // Use default value for safety
        }
      }
    }

    // Delay sweep control
    val sweepActive = RegInit(False)
    // Assign sweepActive based on rdLvlGateEnReg
    sweepActive := rdLvlGateEnReg

    when(rdLvlGateEnReg) {
      timeout := timeout + 1
      sweepActive := True

      // Sweep through delay values for current phase
      when(timeout(6 downto 0).andR) { // Increment delay periodically
        delayCounter := delayCounter + 1

        // Check if current delay position has valid eye
        when(patternRecognizer.stableEye && !eyeFound) {
          eyeFound := True
        }

        // Move to next phase when eye found or max delay reached
        when(eyeFound || delayCounter >= DDR3TimingConstants.MAX_EYE_DELAY) {
          when(phaseReg < 3) {
            phaseReg := phaseReg + 1
            delayCounter := 0 // Reset delay for next phase
            eyeFound := False // Reset for next phase
          } otherwise {
            // All phases complete
            doneReg := True
          }
        }
      }
    }
  }

  // Centralized output assignments to avoid conflicts
  io.phase := eyeTraining.phaseReg
  io.done := eyeTraining.doneReg
  io.response := eyeTraining.eyeFound ? B"1" | B"0"
  io.dqIncrement := rdLvlGateEnReg && eyeTraining.timeout(6 downto 0).andR // Increment only during active sweep
}

class CATrainingModule(config: DfiConfig) extends Component {
  val io = new Bundle {
    val caLvlEn = in Bool()
    val sampledAddr = in Bits(config.addressWidth bits)
    val sampledBank = in Bits(8 bits)
    val currentCmd = in(DdrCmd())
    val done = out Bool()
    val response = out Bits(config.caTrainingResponseWidth bits)
    val cdlyIncrement = out Bool() // Output to be connected externally
  }

  // Use input signals from io Bundle instead of constructor parameters
  val caLvlEnReg = io.caLvlEn
  val sampledAddrReg = io.sampledAddr
  val sampledBankReg = io.sampledBank
  val currentCmdReg = io.currentCmd

  // Command/Address training implementation
  val caTraining = new Area {
    val delayCounter = Reg(UInt(9 bits)) init(0) // Sweep through CA delay taps
    val caFound = RegInit(False)
    val doneReg = RegInit(False)
    val timeout = Reg(UInt(16 bits)) init(0)

    // CA delay line control
    val delayControl = new Area {
      val delayIncrement = RegInit(False)
      
      // Increment delay during training sweeps
      when(caLvlEnReg) {
        when(timeout(7 downto 0).andR) { // Increment delay periodically
          delayIncrement := True
          delayCounter := delayCounter + 1
        } otherwise {
          delayIncrement := False
        }
      } otherwise {
        delayIncrement := False
      }
    }

    // CA training completion detection (aligned with LiteX)
    val completionDetector = new Area {
      val receivedCA = Reg(Bits(16 bits)) init(0) // Received CA pattern (address + bank + cmd)
      val expectedCAPattern = B"16'hAAAA" // Expected CA training pattern (alternating 1010)
      val caValid = receivedCA === expectedCAPattern
      val validCACounter = Reg(UInt(4 bits)) init(0)

      // Count consecutive valid CA patterns
      when(caValid) {
        validCACounter := validCACounter + 1
      } otherwise {
        validCACounter := 0
      }

      val stableCA = validCACounter >= 4 // Require 4 consecutive valid patterns

      // Sample actual CA signals from command/address path
      val sampledCmd = Bits(8 bits)

      // Encode current command into sampled pattern (aligned with LiteX)
      switch(currentCmdReg) {
        is(DdrCmd.NOP) { sampledCmd := B"00000000" }
        is(DdrCmd.ACT) { sampledCmd := B"00000001" }
        is(DdrCmd.READ) { sampledCmd := B"00000010" }
        is(DdrCmd.WRITE) { sampledCmd := B"00000011" }
        is(DdrCmd.PRE) { sampledCmd := B"00000100" }
        is(DdrCmd.REF) { sampledCmd := B"00000101" }
        is(DdrCmd.MRS) { sampledCmd := B"00000110" }
        is(DdrCmd.ZQCS) { sampledCmd := B"00000111" }
      }

      // Fixed: Ensure 16-bit total width (6 bits addr + 8 bits bank + 2 bits cmd = 16 bits)
      receivedCA := sampledAddrReg(5 downto 0) ## sampledBankReg ## sampledCmd(7 downto 6)
    }

    when(caLvlEnReg) {
      timeout := timeout + 1

      // Check CA validity at current delay position
      when(completionDetector.stableCA && !caFound) {
        caFound := True
      }

      // Complete when CA alignment found or max delay reached
      when(caFound || delayCounter >= DDR3TimingConstants.MAX_EYE_DELAY) {
        doneReg := True
      }
    }
  }

  // Centralized output assignments to avoid conflicts
  io.done := caTraining.doneReg
  io.response := caTraining.caFound ? B"11" | B"00" // 2-bit success/failure response
  io.cdlyIncrement := caTraining.delayControl.delayIncrement
}