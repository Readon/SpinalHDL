package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.blackbox.xilinx.ultrascale._
import spinal.lib.memory.sdram.dfi._

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

class XilinxUSPhy(dfiConfig: DfiConfig) extends Component {
  // Clock domain access through parameters to avoid hierarchy violations
  override val clockDomain = ClockDomain.current
  val sysClk = CombInit(clockDomain.readClockWire)
  val sysRst = CombInit(clockDomain.readResetWire)

  // Configuration parameters area - must be defined before any usage
  val configParams = new Area {
    // SDRAM timing parameters
    val burstLength = U(dfiConfig.sdram.burstLength, 4 bits)
    val casLatency = U(dfiConfig.sdram.ddrRdLat, 4 bits)
    
    // Error status signals
    val clockError = Bool()
    val resetError = Bool()
    val initError = Bool()
    
    // Resource monitoring configuration
    val enableResourceMonitoring = Bool()
    val standardMode = Bool()
    val advancedMode = Bool()
    
    // Low power configuration
    val autoClockGating = Bool()
    val autoPowerDown = Bool()
  }
  
  // Initialize configParams signals
  configParams.enableResourceMonitoring := False
  configParams.standardMode := True
  configParams.advancedMode := False
  configParams.autoClockGating := False
  configParams.autoPowerDown := False
  
  // Error signals will be assigned by specific error detection modules
  // Do not initialize them here to avoid assignment conflicts

  // Shared DDR Command definitions are now in XilinxUSPhyTypes.scala

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

      // Training control signals - changed to out to allow training controller assignment
      val training_cdly_inc = out Bool () // Training command delay increment
      val training_dq_inc = out Bool () // Training DQ/DQS delay increment
      val training_bitslip = out Bool () // Training bitslip trigger
    }

    val ctrl = new Bundle {
      val reset = in Bool ()
      val initDone = out Bool ()
    }
  }

  // DRAM clock disable - defined early to avoid forward reference
  val dramClkDisable = RegInit(False)

  def driveFrom(busCtrl: BusSlaveFactory, address: BigInt): Unit = {
    // Control register group (0x00)
    val ctrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x00).init(0)
    io.ctrl.reset := ctrlReg(0) // [0] Global reset
    ctrlReg(8) := io.ctrl.initDone // [8] Initialization status (RO)
    // Use global training signals to avoid hierarchy violations
    val writeLevelingDone = RegNext(trainingWriteLevelingDone) init(False)
    val readGateDone = RegNext(trainingReadGateDone) init(False)
    val readEyeDone = RegNext(trainingReadEyeDone) init(False)

    ctrlReg(9) := writeLevelingDone // [9] Write leveling done
    ctrlReg(10) := readGateDone // [10] Read gate training done
    ctrlReg(11) := readEyeDone // [11] Read eye training done

    // Delay control register (0x04)
    val delayCtrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x04).init(0)
    io.phyCtrl.dly_sel := delayCtrlReg(16 to 23) // [16:23] Byte lane select
    io.phyCtrl.cdly_rst := delayCtrlReg(0) // [0] CDLY reset
    io.phyCtrl.cdly_inc := delayCtrlReg(1) // [1] CDLY increment
    // Use global training signals to avoid hierarchy violations
    val writeLevelingCdlyCount = RegNext(trainingCdlyValueOut) init(U(0, 9 bits))
    delayCtrlReg(24 to 31) := writeLevelingCdlyCount.asBits.resize(8) // [24:31] Wlevel counter

    // Data path control register (0x08)
    val dataCtrlReg = busCtrl.createWriteOnly(Bits(32 bits), 0x08)
    io.phyCtrl.dq_rst := dataCtrlReg(0) // [0] DQ reset
    io.phyCtrl.dq_inc := dataCtrlReg(1) // [1] DQ increment
    io.phyCtrl.bitslip_rst := dataCtrlReg(2) // [2] Bitslip reset
    io.phyCtrl.bitslip := dataCtrlReg(3) // [3] Bitslip trigger

    // Status registers
    busCtrl.read(io.phyCtrl.half_sys8x_taps ## io.phyCtrl.cdly_value, 0x10) // [0x10] Taps + CDLY value
    busCtrl.read(io.phyCtrl.dqs_inc_count, 0x14) // [0x14] DQS increment count
    // Error status register (0x18)
    val errorStatusReg = busCtrl.createReadOnly(Bits(32 bits), 0x18)
    errorStatusReg(0) := configParams.clockError // [0] Clock error
    errorStatusReg(1) := configParams.resetError // [1] Reset synchronization error
    errorStatusReg(2) := configParams.initError  // [2] Initialization error
    // busCtrl.read(trainingCtrl.readGate.io.shiftCounter.asBits.resize(16), 0x16) // [0x16-0x17] Read calibration shift

    // Configuration register (0x18)
    val configReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x18).init(0)
    io.phyCtrl.rd_phase := configReg(13 downto 12).asUInt // [1:0] Read phase
    io.phyCtrl.wr_phase := configReg(15 downto 14).asUInt // [3:2] Write phase
    // Register FSM state to avoid hierarchy violations
    val allTrainingDone = RegNext(
      (trainingWriteLevelingDone && trainingReadGateDone &&
       trainingReadEyeDone && trainingCaTrainingDone) ||
      (trainingWriteLevelingDone && Bool(!dfiConfig.useRdlvlEn) && Bool(!dfiConfig.useRdlvlGateEn) && Bool(!dfiConfig.useCalvlEn)) ||
      (trainingReadGateDone && Bool(!dfiConfig.useWrlvlEn) && Bool(!dfiConfig.useRdlvlGateEn) && Bool(!dfiConfig.useCalvlEn)) ||
      (trainingReadEyeDone && Bool(!dfiConfig.useWrlvlEn) && Bool(!dfiConfig.useRdlvlEn) && Bool(!dfiConfig.useCalvlEn)) ||
      (trainingCaTrainingDone && Bool(!dfiConfig.useWrlvlEn) && Bool(!dfiConfig.useRdlvlEn) && Bool(!dfiConfig.useRdlvlGateEn))
    ) init(False)
    configReg(16) := allTrainingDone // [16] Calibration done status
  }
  
  // ==========================================================================
  // DDR Initialization and Power Management - JEDEC DDR3 Compliant
  // ==========================================================================
  val initManager = new Area {
    // Initialization FSM states - JEDEC DDR3 Power-up and Initialization Sequence
    // InitState is now defined in XilinxUSPhyTypes.scala
    val currentState = Reg(InitState()) init(InitState.IDLE)
    val initTimer = Reg(UInt(20 bits)) init(0)  // Extended timer for 200us timing

    // JEDEC DDR3 timing parameters (assuming 200MHz controller clock = 5ns cycle)
    val tPWRUP = 40000   // 200us power-up time (200000ns / 5ns = 40000 cycles)
    val tRESET = 40000   // 200us reset stabilization time
    val tCKE_LOW = 10    // Minimum 10 cycles CKE low after reset
    val tMRD = 4         // 4 cycles between MRS commands
    val tZQCS = 64       // ZQCS calibration time

    // Mode register programming state
    // MrsState is now defined in XilinxUSPhyTypes.scala
    val mrsState = Reg(MrsState()) init(MrsState.IDLE)
    val mrsTimer = Reg(UInt(8 bits)) init(0)

    // Initialization control signals
    val initActive = currentState =/= InitState.IDLE
    val initComplete = currentState === InitState.DONE

    // Override pad signals during initialization - aligned with LiteX semantics
    // During init, PHY-generated signals take precedence over DFI signals
    val padOverride = initActive && (currentState =/= InitState.DONE)
    
    // Debug output for initialization state - removed for production code
    // Note: Initialization state can be monitored through io.ctrl.initDone signal

    // Initialization sequence control
    val initStart = !io.ctrl.reset && RegNext(io.ctrl.reset, True) // Start on reset deassertion

    // State machine logic
    switch(currentState) {
      is(InitState.IDLE) {
        when(initStart) {
          currentState := InitState.POWER_UP
          initTimer := 0
        }
      }

      is(InitState.POWER_UP) {
        // Phase 1: Power-up - reset_n low, CKE low, wait 200us
        initTimer := initTimer + 1
        when(initTimer >= tPWRUP) {
          currentState := InitState.RESET_STABILIZE
          initTimer := 0
        }
      }

      is(InitState.RESET_STABILIZE) {
        // Phase 2: Reset stabilization - reset_n high, CKE low, wait 200us
        initTimer := initTimer + 1
        when(initTimer >= tRESET) {
          currentState := InitState.CKE_LOW
          initTimer := 0
        }
      }

      is(InitState.CKE_LOW) {
        // Phase 3: CKE low period - ensure stable operation before MRS
        initTimer := initTimer + 1
        when(initTimer >= tCKE_LOW) {
          currentState := InitState.MRS_SEQUENCE
          mrsState := MrsState.MR2  // Start MRS sequence
          mrsTimer := 0
        }
      }

      is(InitState.MRS_SEQUENCE) {
        // Phase 4: Mode Register Programming - MR2, MR3, MR1, MR0
        switch(mrsState) {
          is(MrsState.MR2) {
            mrsTimer := mrsTimer + 1
            when(mrsTimer >= tMRD) {
              mrsState := MrsState.MR3
              mrsTimer := 0
            }
          }
          is(MrsState.MR3) {
            mrsTimer := mrsTimer + 1
            when(mrsTimer >= tMRD) {
              mrsState := MrsState.MR1
              mrsTimer := 0
            }
          }
          is(MrsState.MR1) {
            mrsTimer := mrsTimer + 1
            when(mrsTimer >= tMRD) {
              mrsState := MrsState.MR0
              mrsTimer := 0
            }
          }
          is(MrsState.MR0) {
            mrsTimer := mrsTimer + 1
            when(mrsTimer >= tMRD) {
              mrsState := MrsState.DONE
              currentState := InitState.ZQ_CALIBRATION
              initTimer := 0
            }
          }
        }
      }

      is(InitState.ZQ_CALIBRATION) {
        // Phase 5: ZQ Calibration - ZQCS command
        initTimer := initTimer + 1
        when(initTimer >= tZQCS) {
          currentState := InitState.DONE
        }
      }

      is(InitState.DONE) {
        // Initialization complete - allow normal operation
        when(io.ctrl.reset) {
          currentState := InitState.IDLE
        }
      }
  
      // Connect initialization error signal to configParams
      configParams.initError := currentState === InitState.IDLE && initStart
    }
    // Generate initialization command signals
    val initCmdValid = Bool()
    val initCmd = DdrCmd()
    val initAddr = Bits(dfiConfig.addressWidth bits)
    val initBa = Bits(dfiConfig.bankWidth bits)
    val initCsN = Bits(dfiConfig.chipSelectNumber bits)
    val initCke = Bits(dfiConfig.chipSelectNumber bits)
    val initOdt = Bits(dfiConfig.chipSelectNumber bits)
    val initResetN = Bits(dfiConfig.chipSelectNumber bits)

    // Default values - aligned with LiteX initialization semantics
    initCmdValid := False
    initCmd := DdrCmd.NOP
    initAddr := B(0, dfiConfig.addressWidth bits)
    initBa := B(0, dfiConfig.bankWidth bits)
    initCsN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits) // Initialize to inactive (all 1's) per JEDEC spec
    initCke := B(0, dfiConfig.chipSelectNumber bits) // Initialize to disabled (0) per JEDEC spec
    initOdt := B(0, dfiConfig.chipSelectNumber bits) // Initialize to disabled
    initResetN := B(0, dfiConfig.chipSelectNumber bits) // Initialize to reset (0) per JEDEC spec

    // Generate commands based on current state
    switch(currentState) {
      is(InitState.POWER_UP) {
        // Power-up: reset_n=0, CKE=0
        initResetN := B(0, dfiConfig.chipSelectNumber bits)
        initCke := B(0, dfiConfig.chipSelectNumber bits)
      }
      is(InitState.RESET_STABILIZE, InitState.CKE_LOW) {
        // Reset stabilization and CKE low: reset_n=1, CKE=0
        initResetN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)
        initCke := B(0, dfiConfig.chipSelectNumber bits)
      }
      is(InitState.MRS_SEQUENCE) {
        // MRS commands: CKE=1, reset_n=1 (aligned with LiteX)
        initCke := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)
        initResetN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)

        switch(mrsState) {
          is(MrsState.MR2) {
            initCmdValid := True
            initCmd := DdrCmd.MRS
            initBa := B(2, dfiConfig.bankWidth bits)  // MR2
            initAddr := B"16'h0008".resize(dfiConfig.addressWidth)  // MR2 value: CWL=5, RttWR=60ohm
            initCsN := B(0, dfiConfig.chipSelectNumber bits) // Activate chip select during MRS
          }
          is(MrsState.MR3) {
            initCmdValid := True
            initCmd := DdrCmd.MRS
            initBa := B(3, dfiConfig.bankWidth bits)  // MR3
            initAddr := B"16'h0000".resize(dfiConfig.addressWidth)  // MR3 value: MPR disabled
            initCsN := B(0, dfiConfig.chipSelectNumber bits) // Activate chip select during MRS
          }
          is(MrsState.MR1) {
            initCmdValid := True
            initCmd := DdrCmd.MRS
            initBa := B(1, dfiConfig.bankWidth bits)  // MR1
            initAddr := B"16'h0004".resize(dfiConfig.addressWidth)  // MR1 value: Enable DLL, AL=0, RttNom=60ohm
            initCsN := B(0, dfiConfig.chipSelectNumber bits) // Activate chip select during MRS
          }
          is(MrsState.MR0) {
            initCmdValid := True
            initCmd := DdrCmd.MRS
            initBa := B(0, dfiConfig.bankWidth bits)  // MR0
            initAddr := B"16'h0520".resize(dfiConfig.addressWidth)  // MR0 value: BL8, CL5, DLL Reset
            initCsN := B(0, dfiConfig.chipSelectNumber bits) // Activate chip select during MRS
          }
        }
      }
      is(InitState.ZQ_CALIBRATION) {
        // ZQCS command: CKE=1, reset_n=1 (aligned with LiteX)
        initCke := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)
        initResetN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)

        initCmdValid := True
        initCmd := DdrCmd.ZQCS
        initAddr := B"16'h400".resize(dfiConfig.addressWidth)  // ZQCS address pattern
        initCsN := B(0, dfiConfig.chipSelectNumber bits) // Activate chip select during ZQCS
      }
      is(InitState.DONE) {
        // Normal operation: CKE=1, reset_n=1
        initCke := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)
        initResetN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits)
      }
    }
  }

  // TrainingController will be instantiated after dataPath and cmdPath are defined
  // to avoid forward reference issues
  val trainingCtrl = new Area {
    // Placeholder for sampled data - will be assigned after dataPath and cmdPath are defined
    val writeLevelingSampledData = Bits(8 bits)
    val readGateSampledData = Bits(8 bits)
    val readEyeSampledData = Vec.fill(4)(Bits(8 bits))
    val caSampledAddr = Bits(dfiConfig.addressWidth bits)
    val caSampledBank = Bits(8 bits)
    val caCurrentCmd = DdrCmd()

    // Training status signals exposed as Area members to avoid hierarchy violations
    val writeLevelingDone = Bool()
    val readGateDone = Bool()
    val readEyeDone = Bool()
    val caTrainingDone = Bool()
    val cdly_value_out = UInt(9 bits)
    val readGateResponse = Bits(1 bits)
    val readEyeResponse = Bits(1 bits)
    val writeLevelingResponse = Bits(1 bits)
    val caTrainingResponse = Bits(2 bits)

    // Placeholder for controller - will be instantiated after data is available
    var controller: TrainingController = null
  }

  // Training status signals are now defined globally to avoid assignment conflicts
  // They will be assigned in the global scope below

  // Define training status signals outside the Area to make them accessible
  val trainingWriteLevelingDone = Bool()
  val trainingReadGateDone = Bool()
  val trainingReadEyeDone = Bool()
  val trainingCaTrainingDone = Bool()
  val trainingCdlyValueOut = UInt(9 bits)
  val trainingReadGateResponse = Bits(1 bits)
  val trainingReadEyeResponse = Bits(1 bits)
  val trainingWriteLevelingResponse = Bits(1 bits)
  val trainingCaTrainingResponse = Bits(2 bits)

  // Training signals are now assigned later in the code to avoid conflicts

  // Initialization state machine integration
  // The initialization sequence is now handled by ControlManager through UnifiedAdapter
  // Training starts after initialization is complete

  // ==========================================================================
  // Enhanced Reset Synchronization and Initialization - Optimized
  // ==========================================================================
  // Reset synchronization between DFI clock domain and DDR clock domain - optimized
  val dfiResetSync = new Area {
    // Synchronize reset from DFI domain to DDR domain - pipelined
    val resetFF1 = RegNext(io.ctrl.reset) init(False)
    val resetFF2 = RegNext(resetFF1) init(False)
    val dfiResetSynced = resetFF2

    // Synchronize initialization done from DDR domain to DFI domain - pipelined
    val initDoneFF1 = RegNext(io.ctrl.initDone) init(False)
    val initDoneFF2 = RegNext(initDoneFF1) init(False)
    val initDoneSynced = initDoneFF2

    // Error detection for reset synchronization
    val resetSyncError = RegInit(False)
    val resetTimeoutCounter = RegInit(U(0, 16 bits))

    // Detect reset synchronization issues (metastability or stuck resets)
    when(io.ctrl.reset && !dfiResetSynced) {
      resetTimeoutCounter := resetTimeoutCounter + 1
      when(resetTimeoutCounter >= 1000) { // Timeout after ~1000 cycles
        resetSyncError := True
      }
    } otherwise {
      resetTimeoutCounter := 0
      resetSyncError := False
    }

    // Connect error signals to configParams
    configParams.resetError := resetSyncError
  }

  // Clock domain crossing for control signals - optimized
  val cdcControl = new Area {
    // Synchronize CKE control across domains - pipelined
    val ckeFF1 = RegNext(io.dfi.control.cke.orR) init(False)
    val ckeFF2 = RegNext(ckeFF1) init(False)
    val ckeSynced = ckeFF2

    // Synchronize ODT control across domains - pipelined
    val odtFF1 = RegNext(io.dfi.control.odt.orR) init(False)
    val odtFF2 = RegNext(odtFF1) init(False)
    val odtSynced = odtFF2

    // Error detection for clock domain crossing
    val cdcError = RegInit(False)
    val cdcTimeoutCounter = RegInit(U(0, 16 bits))

    // Detect CDC issues (metastability or stuck signals)
    val ckeChanged = ckeFF1 =/= ckeFF2
    val odtChanged = odtFF1 =/= odtFF2

    when((ckeChanged || odtChanged) && cdcTimeoutCounter < 1000) {
      cdcTimeoutCounter := cdcTimeoutCounter + 1
      when(cdcTimeoutCounter >= 999) {
        cdcError := True
      }
    } otherwise {
      cdcTimeoutCounter := 0
      cdcError := False
    }

    // Connect clock error signal to configParams
    configParams.clockError := cdcError
  }


  // Global training active signal to avoid assignment conflicts
  val trainingActiveShared = RegInit(False)
  // Single assignment with conditional logic to avoid overlap
  trainingActiveShared := {
    if (dfiConfig.signalConfig.useWrlvlEn) {
      io.dfi.wrTraining.wrlvlEn.orR
    } else if (dfiConfig.signalConfig.useRdlvlEn) {
      io.dfi.rdTraining.rdlvlEn.orR
    } else if (dfiConfig.signalConfig.useRdlvlGateEn) {
      io.dfi.rdTraining.rdlvlGateEn.orR
    } else {
      False
    }
  }

  // Enhanced DDR clock generation with phase control and enable/disable - optimized
  val clockGen = new Area {
    // Clock enable control - can be disabled during low power states
    val clkEnable = RegInit(True)
    val clkDisableReq = RegInit(False) // Initialize to avoid forward reference

    // Phase control for different frequency ratios (1:1, 1:2, 1:4) - configurable
    val phaseSelect = RegInit(U"00") // 0: 0°, 1: 90°, 2: 180°, 3: 270°
    // Assign phaseSelect based on DFI configuration
    phaseSelect := io.phyCtrl.wr_phase // Use write phase for clock phase control

    // Clock pattern generation based on frequency ratio - optimized lookup table
    val clkPattern = Bits(8 bits)
    val freqRatio = UInt(3 bits)
    freqRatio := dfiConfig.frequencyRatio

    // Pre-computed patterns for better timing - optimized with registered selection
    val patterns_1to1 = Vec(B"1010_1010", B"1010_1010", B"1010_1010", B"1010_1010")
    val patterns_1to2 = Vec(B"1100_1100", B"0011_0011", B"1100_1100", B"0011_0011")
    val patterns_1to4 = Vec(B"1000_1000", B"0010_0010", B"0001_0001", B"0100_0100")

    // Generate appropriate clock pattern based on ratio and phase - pipelined with registered mux
    val patternSelReg = Reg(Bits(8 bits)) init(B"1010_1010")
    val freqRatioReg = RegNext(freqRatio) init(U(1))
    val phaseSelectReg = RegNext(phaseSelect) init(U"00")

    // Break critical path with registered frequency ratio selection
    val selectedPattern = Bits(8 bits)
    switch(freqRatioReg) {
      is(U(1)) { selectedPattern := patterns_1to1(phaseSelectReg.resize(2)) }
      is(U(2)) { selectedPattern := patterns_1to2(phaseSelectReg.resize(2)) }
      is(U(4)) { selectedPattern := patterns_1to4(phaseSelectReg.resize(2)) }
      default { selectedPattern := B"1010_1010" }
    }
    patternSelReg := selectedPattern
    clkPattern := patternSelReg

    // OSERDESE3 for clock serialization - optimized reset
    val serdes = new OSERDESE3(hasTristate = true)
    serdes.RST := sysRst | io.ctrl.reset
    serdes.CLK := io.clk4x
    serdes.CLKDIV := sysClk
    serdes.D := clkPattern
    // Fixed: Add missing T signal to prevent NO DRIVER ON error
    serdes.T := False  // Always drive output (not tristate)

    // ODELAYE3 with phase control for fine timing adjustment - optimized
    val delay = new ODELAYE3(delayType = "VARIABLE")
    delay.RST := sysRst | io.ctrl.reset | io.phyCtrl.cdly_rst
    delay.CLK := sysClk
    // EN_VTC controlled by DFI interface - disabled during training
    val trainingActive = RegInit(False)
    val trainingActiveNext = Bool()

    // Use shared training active signal to avoid assignment conflicts
    trainingActive := trainingActiveShared
    // Assign trainingActiveNext to avoid unassigned register
    trainingActiveNext := trainingActiveShared
    delay.EN_VTC := io.dfi.update.ctrlupdAck && !trainingActive
    delay.CE := io.phyCtrl.cdly_inc
    delay.INC := True
    delay.ODATAIN := serdes.OQ
    // Fixed: Add missing CNTVALUEIN to prevent NO DRIVER ON error
    delay.CNTVALUEIN := U(0, 9 bits) // Default to no additional delay
    // Fixed: Add missing cascade and load ports to match simulation stubs
    // Note: CASC_OUT is output port, leave unconnected in standalone mode
    if(delay.cascade != "NONE") {
      delay.CASC_IN := False   // No cascade input in standalone mode
      delay.CASC_RETURN := False // No cascade return in standalone mode
    }
    if(delay.delayType == "VAR_LOAD") delay.LOAD := False       // No load operation in VARIABLE mode
    // Apply simulation-friendly defaults after manual assignments to avoid overlap
    // Note: setSimulationDefaults() calls are removed to prevent assignment overlaps
    // The blackbox components are configured with manual assignments only

    // Clock enable/disable control - pipelined
    val clkGated = Reg(Bool()) init(True)
    val clkGatedNext = Reg(Bool()) init(True)
    clkGatedNext := clkEnable && !clkDisableReq
    clkGated := clkGatedNext

    // Differential buffer with enable control
    val buf = new OBUFDS()
    buf.I := delay.DATAOUT & clkGated
    // Apply simulation-friendly defaults after manual assignments to avoid overlap
    // Note: setSimulationDefaults() calls are removed to prevent assignment overlaps
    // The blackbox components are configured with manual assignments only

    io.pads.clk_p := buf.O
    io.pads.clk_n := buf.OB

    // Update clock enable based on DFI control - optimized with pipelined logic
    val ckeOrR = RegNext(io.dfi.control.cke.orR) init(False)
    val initDone = RegNext(io.ctrl.initDone) init(False)
    val clkEnableNext = ckeOrR && initDone
    clkEnable := clkEnableNext
  }


  // DDR Command Generator is now defined in XilinxUSPhyTypes.scala

  // CmdSignalHandler is now defined in XilinxUSPhyTypes.scala

  // Command and Control signals path
  val cmdPath = new Area {
    // Command signals handling
    val handler = new CmdSignalHandler(dfiConfig) // Instantiate the handler

    // Connect DFI signals to handler inputs to avoid hierarchy violations
    handler.dfiRasN_or := io.dfi.control.rasN.orR
    handler.dfiCasN_or := io.dfi.control.casN.orR
    handler.dfiWeN_or := io.dfi.control.weN.orR
    handler.dfiActN_or := (if (dfiConfig.signalConfig.useAckN) io.dfi.control.actN.orR else False)
    handler.dfiAddress := io.dfi.control.address.asUInt
    handler.dfiCsN := io.dfi.control.csN
    handler.dfiBank := (if (dfiConfig.signalConfig.useBank) io.dfi.control.bank.orR.asBits.resize(dfiConfig.bankWidth) else B(0, dfiConfig.bankWidth bits))
    handler.dfiCke := io.dfi.control.cke
    handler.dfiOdt := io.dfi.control.odt
    handler.dfiResetN := io.dfi.control.resetN

    // Create OSERDES and ODELAY for each signal identified by the handler
    val oserdesVec = Seq.fill(handler.signalMappings.length)(new OSERDESE3(hasTristate = true))
    val odelayVec = Seq.fill(handler.signalMappings.length)(new ODELAYE3(delayType = "VARIABLE", refClkFrequency = 200))

    // Expose synced signals as Area outputs to avoid hierarchy violations
    val syncedAddressOut = Vec.fill(15)(Bool())
    val syncedBankOut = Vec.fill(3)(Bool())

    // Register synced signals locally - use RegNext to avoid direct access
    for (i <- 0 until syncedAddressOut.length) {
      syncedAddressOut(i) := RegNext(handler.syncedAddressOut(i)) init(False)
    }
    for (i <- 0 until syncedBankOut.length) {
      syncedBankOut(i) := RegNext(handler.syncedBankOut(i)) init(False)
    }

    // Create local copies for internal use to avoid hierarchy violations
    val syncedAddressLocal = Vec.fill(15)(Reg(Bool()) init(False)) // Address width
    val syncedBankLocal = Vec.fill(3)(Reg(Bool()) init(False)) // Bank width

    // Assign from exposed signals
    for (i <- 0 until syncedAddressLocal.length) {
      syncedAddressLocal(i) := syncedAddressOut(i)
    }
    for (i <- 0 until syncedBankLocal.length) {
      syncedBankLocal(i) := syncedBankOut(i)
    }

    // Process each signal through OSERDES and ODELAY
    for (((serdes, delay), i) <- oserdesVec.zip(odelayVec).zipWithIndex) {
      serdes.RST := io.ctrl.reset | sysRst
      serdes.CLK := io.clk4x
      serdes.CLKDIV := sysClk

      // Fixed: Create local copies of DFI signals to avoid hierarchy violations
      // Don't access handler's internal signals directly
      val dfiSourceBits = Bits(8 bits)
      // Assign based on signal index to avoid direct access to handler internals
      if (i < 15) {
        // Address signals (0-14 for 15-bit address)
        dfiSourceBits := handler.syncedAddressOut(i).asBits.resize(8)
      } else if (i >= 15 && i < 18) {
        // Bank signals (15-17 for 3-bit bank, if enabled)
        val bankIndex = i - 15
        if (bankIndex < handler.syncedBankOut.length) {
          dfiSourceBits := handler.syncedBankOut(bankIndex).asBits.resize(8)
        } else {
          dfiSourceBits := B(0, 8 bits)
        }
      } else {
        // Control signals (RAS_N, CAS_N, WE_N, etc.)
        // These need to be generated from DFI signals directly
        dfiSourceBits := B(0, 8 bits) // Default for other signals
      }
      serdes.D := dfiSourceBits

      // Fixed: Add missing T signal to prevent NO DRIVER ON error
      if (serdes.hasTristate) {
        serdes.T := False  // Always drive output (not tristate)
      }

      delay.RST := io.ctrl.reset | sysRst | io.phyCtrl.cdly_rst
      delay.CLK := sysClk
      delay.EN_VTC := True // Always enabled after training
      delay.CE := io.phyCtrl.cdly_inc
      delay.INC := True
      delay.ODATAIN := serdes.OQ
      // Fixed: Add missing CNTVALUEIN to prevent NO DRIVER ON error
      delay.CNTVALUEIN := U(0, 9 bits) // Default to no additional delay
      // Fixed: Add missing cascade and load ports to match simulation stubs
      // Note: CASC_OUT is output port, leave unconnected in standalone mode
      if(delay.cascade != "NONE") {
        delay.CASC_IN := False   // No cascade input in standalone mode
        delay.CASC_RETURN := False // No cascade return in standalone mode
      }
      if(delay.delayType == "VAR_LOAD") delay.LOAD := False

      // Create local signal to avoid hierarchy violations
      val padSignalLocal = Bool()
      padSignalLocal := delay.DATAOUT
      
      // Fixed: Connect directly to pads to avoid hierarchy violations
      // Don't use handler's connectOutput method which tries to assign to child component outputs
      // Instead, connect directly to the appropriate pad based on signal index
      if (i < 15) {
        // Address signals
        if (i < io.pads.a.getWidth) {
          io.pads.a(i) := padSignalLocal
        }
      } else if (i >= 15 && i < 18) {
        // Bank signals
        val bankIndex = i - 15
        if (dfiConfig.signalConfig.useBank && bankIndex < io.pads.ba.getWidth) {
          io.pads.ba(bankIndex) := padSignalLocal
        }
      } else {
        // Other control signals would be handled here based on the signal mapping
        // Default: no connection
      }
    }
  }

  // DQSPattern is now defined in XilinxUSPhyTypes.scala

  val dqsPath = new Area {
    // ==========================================================================
    // DQS Timing Control - Optimized
    // ==========================================================================
    // Control signals - pipelined
    val dqs_preamble = Reg(Bool()) init(False)
    val dqs_postamble = Reg(Bool()) init(False)
    val dqs_oe = Reg(Bool()) init(False)
    val dq_oe = Reg(Bool()) init(False)  // Output enable for DQ signals

    // Write data enable from DFI interface - connected from dataPath
    val wrDataEn = Bool()

    // ==========================================================================
    // Write Latency and Timing Generation - Optimized
    //==========================================================================
    // Ensure writeLatency is at least 3 for proper preamble/postamble
    val safeWriteLatency = Math.ceil(dfiConfig.sdram.ddrWrLat / dfiConfig.frequencyRatio).toInt - 1

    // Generate timing signals from delay taps with proper synchronization - pipelined
    val wrDataEnDelayed = History(wrDataEn, safeWriteLatency + 2)
    val dqOeNext = wrDataEnDelayed(safeWriteLatency)  // Add extra register for better timing
    val dqsOeNext = Bool()
    if (dfiConfig.signalConfig.useWrlvlEn) {
      dqsOeNext := Mux(io.dfi.wrTraining.wrlvlEn.orR, True, dqOeNext)
    } else {
      dqsOeNext := dqOeNext
    }

    dq_oe := dqOeNext
    dqs_oe := dqsOeNext

    // Improved preamble/postamble generation with proper timing - pipelined
    val preambleNext = wrDataEnDelayed(safeWriteLatency - 1) & ~wrDataEnDelayed(safeWriteLatency)
    val postambleNext = wrDataEnDelayed(safeWriteLatency + 1) & ~wrDataEnDelayed(safeWriteLatency)

    dqs_preamble := preambleNext
    dqs_postamble := postambleNext

    // Delay line for output enable - optimized
    val delayLine = History(dqs_preamble | dqs_postamble | dqs_oe, 1)

    // ==========================================================================
    // DQS Pattern Generation
    // ==========================================================================
    // DQS pattern generator for serialization
    val pattern = new DQSPattern
    pattern.io.preamble := dqs_preamble
    pattern.io.postamble := dqs_postamble
    if (dfiConfig.signalConfig.useWrlvlEn) {
      pattern.io.wlevel_en := io.dfi.wrTraining.wrlvlEn.orR
    } else {
      pattern.io.wlevel_en := False
    }
    if (dfiConfig.signalConfig.useWrlvlStrobe) {
      pattern.io.wlevel_strobe := io.dfi.wrTraining.wrlvlStrobe.orR
    } else {
      pattern.io.wlevel_strobe := False
    }

    //==========================================================================
    // DQS Output Path - Byte Lanes
    // ==========================================================================
    // DQS OSERDES for byte lanes with delay
    val dqsWidth = io.pads.dqs_p.getWidth
    val oserdesVec = Seq.fill(dqsWidth)(new OSERDESE3(hasTristate=true))
    // Align with LiteX: set initial delay value to tck/4 for DQS (usphy.py:276)
    // Calculate tck/4 based on current frequency - parameterized for different platforms
    // LiteX uses tck/4 as the initial DQS delay for proper timing alignment
    val sysClkFreq = dfiConfig.frequencyRatio * dfiConfig.sdram.ddrMHZ * 1e6 // System clock frequency in Hz
    val tckPeriodPs = (1e12 / sysClkFreq).toInt // Clock period in picoseconds
    val dqsInitialDelay = Math.max(1, tckPeriodPs / 4) // tck/4 as per LiteX implementation, ensure at least 1
    
    // Timing configuration: System clock freq: ${sysClkFreq/1e6} MHz, TCK: ${tckPeriodPs} ps, DQS initial delay: ${dqsInitialDelay} taps
    // Note: Timing values can be monitored through phyCtrl interface for debugging
    
    val odelayVec = Seq.fill(dqsWidth)(new ODELAYE3(delayType="VARIABLE", delayValue=dqsInitialDelay, refClkFrequency = 200))

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
      val trainingActiveDqs = RegInit(False)
      val trainingActiveDqsNext = Bool()

      // Use shared training active signal to avoid assignment conflicts
      trainingActiveDqs := trainingActiveShared
      // Assign trainingActiveDqsNext to avoid unassigned register
      trainingActiveDqsNext := trainingActiveShared
      delay.EN_VTC := io.dfi.update.ctrlupdAck && !trainingActiveDqs
      delay.CE := io.phyCtrl.dq_inc & io.phyCtrl.dly_sel(i / 8) // Proper flattened phyCtrl signals
      delay.INC := True // Always increment (decrement handled by reset+increment)
      delay.ODATAIN := serdes.OQ
      // Fixed: Add missing CNTVALUEIN to prevent NO DRIVER ON error
      delay.CNTVALUEIN := U(0, 9 bits) // Default to no additional delay
      // Fixed: Add missing cascade and load ports to match simulation stubs
      // Note: CASC_OUT is output port, leave unconnected in standalone mode
      if(delay.cascade != "NONE") {
        delay.CASC_IN := False   // No cascade input in standalone mode
        delay.CASC_RETURN := False // No cascade return in standalone mode
      }
      if(delay.delayType == "VAR_LOAD") delay.LOAD := False
      // Apply simulation-friendly defaults after manual assignments to avoid overlap
      // Note: setSimulationDefaults() calls are removed to prevent assignment overlaps
      // The blackbox components are configured with manual assignments only

      // Connect differential or single-ended buffer based on dqsType and dataRate
      assert(dfiConfig.sdram.generation.dataRate > 1, "PHY do not support signal data rate.")
      if(dfiConfig.sdram.generation.dqsType == DqsType.Differential) {
        val buf = new IOBUFDSE3()
        buf.I := delay.DATAOUT
        buf.T := serdes.T_OUT
        // Apply simulation-friendly defaults after manual assignments to avoid overlap
        // Note: setSimulationDefaults() calls are removed to prevent assignment overlaps
        // The blackbox components are configured with manual assignments only

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
    // Burst Configuration and Data Ordering - Optimized
    // ==========================================================================
    // Burst length configuration (from SDRAM config) - use configurable parameter
    val burstLength = configParams.burstLength.resize(4) // Use configurable burst length
    val maxBurstLength = 8 // Fixed maximum for resource optimization
    val burstOrder = Vec.fill(maxBurstLength)(UInt(3 bits))

    // Generate burst ordering based on burst length - optimized with registered computation
    val burstOrderReg = Vec.fill(maxBurstLength)(Reg(UInt(3 bits)) init(0))

    // Pre-compute burst ordering for better timing
    switch(burstLength) {
      is(4) { // BL4: 0,1,2,3
        for (i <- 0 until 4) burstOrderReg(i) := U(i)
        for (i <- 4 until maxBurstLength) burstOrderReg(i) := U(0)
      }
      is(8) { // BL8: 0,1,2,3,4,5,6,7
        for (i <- 0 until 8) burstOrderReg(i) := U(i)
      }
      default { // Default to sequential
        for (i <- 0 until maxBurstLength) burstOrderReg(i) := U(i)
      }
    }

    // Use registered burst order for timing closure
    for (i <- 0 until maxBurstLength) burstOrder(i) := burstOrderReg(i)

    // ==========================================================================
    // Write Path (DQ and DM) - Optimized
    // ==========================================================================
    // Write data serialization components - shared configuration
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3(hasTristate = true))
    val dmOserdes = Seq.fill(dfiConfig.dataWidth / 8)(new OSERDESE3(hasTristate = true))

    // Data reordering for burst - optimized with pipelining
    val reorderedWrData = Vec.fill(dfiConfig.dataWidth)(Reg(Bits(8 bits)) init(0))
    val reorderedWrMask = Vec.fill(dfiConfig.dataWidth / 8)(Reg(Bits(8 bits)) init(0))

    // Initialize with current data (no reordering for now - can be enhanced later)
    for (i <- 0 until dfiConfig.dataWidth) {
      val byteIndex = i / 8
      val bitIndex = i % 8
      // Add bounds checking to prevent IndexOutOfBoundsException
      if (byteIndex < wrData.length) {
        // Fixed: Ensure proper bit range within data width
        val maxBit = if (bitIndex * 8 + 7 < wrData(byteIndex).getWidth) bitIndex * 8 + 7 else wrData(byteIndex).getWidth - 1
        val minBit = bitIndex * 8
        if (minBit <= maxBit) {
          reorderedWrData(i) := wrData(byteIndex)(maxBit downto minBit).resize(8)
        } else {
          reorderedWrData(i) := B(0, 8 bits)
        }
      } else {
        // Default to first byte if out of bounds (safe fallback)
        if (wrData.nonEmpty) {
          val maxBit = if (bitIndex * 8 + 7 < wrData(0).getWidth) bitIndex * 8 + 7 else wrData(0).getWidth - 1
          val minBit = bitIndex * 8
          if (minBit <= maxBit) {
            reorderedWrData(i) := wrData(0)(maxBit downto minBit).resize(8)
          } else {
            reorderedWrData(i) := B(0, 8 bits)
          }
        } else {
          reorderedWrData(i) := B(0, 8 bits)
        }
      }
    }
    // Fixed: Ensure proper width for write mask
    for (i <- 0 until dfiConfig.dataWidth / 8) {
      if (i < wrDataMask.getWidth) {
        // Use proper bit extraction and width matching
        val maskBit = wrDataMask(i).asBits
        reorderedWrMask(i) := maskBit.resize(8)
      } else {
        reorderedWrMask(i) := B(0, 8 bits)
      }
    }

    // Configure and connect DQ OSERDES to pads - optimized configuration
    for(((osd, data), i) <- dqOserdes.zip(reorderedWrData).zipWithIndex) {
      // Configure OSERDES - shared parameters
      val dataBitslip = BitSlip(data, 2, io.phyCtrl.bitslip)
      osd.D := dataBitslip
      osd.CLK := io.clk4x
      osd.CLKDIV := sysClk
      osd.RST := io.ctrl.reset | sysRst
      osd.T := ~dqsPath.dq_oe // Use dqsPath's dq_oe for output enable

      // Connect to IO buffer
      val buf = new IOBUF()
      buf.I := osd.OQ
      buf.T := osd.T_OUT
      io.pads.dq(i) := buf.IO
      // Apply simulation-friendly defaults after manual assignments to avoid overlap
      // Note: setSimulationDefaults() calls are removed to prevent assignment overlaps
      // The blackbox components are configured with manual assignments only
    }

    // Configure and connect DM OSERDES to pads - optimized
    for(((dmOsd, mask), i) <- dmOserdes.zip(reorderedWrMask).zipWithIndex) {
      // Configure OSERDES for DM - shared parameters
      val maskBitslip = BitSlip(mask, 2, io.phyCtrl.bitslip)
      dmOsd.D := maskBitslip
      dmOsd.CLK := io.clk4x
      dmOsd.CLKDIV := sysClk
      dmOsd.RST := io.ctrl.reset | sysRst
      dmOsd.T := ~dqsPath.dq_oe // Same timing as DQ

      // Connect DM directly to pads (no tristate needed for DM)
      io.pads.dm(i) := dmOsd.OQ
    }

    // ==========================================================================
    // Read Path (DQ with DQS Gating) - Optimized
    // ==========================================================================
    // Read data deserialization components - shared configuration
    val rdIserdes = Seq.fill(dfiConfig.dataWidth)(new ISERDESE3(fifoEnable = true))
    val rdDelay = Seq.fill(dfiConfig.dataWidth)(new IDELAYE3(delayType = "VARIABLE", refClkFrequency = 200))

    // DQS gating for read path - pipelined
    val dqsGate = Reg(Bool()) init(False)
    val readActive = Reg(Bool()) init(False)

    // Read timing control - generate read data valid based on read commands - optimized
    val readCommandActive = RegInit(False)
    val readDataValidDelay = Vec.fill(8)(RegInit(False)) // Individual registers for better LUT optimization
    

    // Detect read command from DFI interface - pipelined
    val readCmdDetected = io.dfi.control.casN.orR && !io.dfi.control.weN.orR && io.dfi.control.rasN.orR
    val readCmdReg = RegNext(readCmdDetected) init(False)
    val ckeReg = RegNext(io.dfi.control.cke.orR) init(False)

    when(readCmdReg && ckeReg) {
      readCommandActive := True
    }

    // Shift read data valid through delay line - optimized with individual assignments
    readDataValidDelay(0) := readCommandActive
    readDataValidDelay(1) := readDataValidDelay(0)
    readDataValidDelay(2) := readDataValidDelay(1)
    readDataValidDelay(3) := readDataValidDelay(2)
    readDataValidDelay(4) := readDataValidDelay(3)
    readDataValidDelay(5) := readDataValidDelay(4)
    readDataValidDelay(6) := readDataValidDelay(5)
    readDataValidDelay(7) := readDataValidDelay(6)

    // Read data valid timing (adjust delay based on CAS latency) - configurable and optimized
    val casLatency = configParams.casLatency // Use configurable CAS latency
    val rdDataValid = Reg(Bool()) init(False)
    rdDataValid := readDataValidDelay(casLatency.resize(3)) // Adjusted for pipeline delay with proper indexing

    // DQS gating control - pipelined
    val dqsGateNext = Bool()
    if (dfiConfig.signalConfig.useRdlvlGateEn) {
      dqsGateNext := rdDataValid && !io.dfi.rdTraining.rdlvlGateEn.orR
    } else {
      dqsGateNext := rdDataValid
    }
    val readActiveNext = rdDataValid

    dqsGate := dqsGateNext
    readActive := readActiveNext

    // Configure read path components - optimized shared parameters
    val enVtcShared = Bool()
    val trainingActiveSharedNext = Bool()

    // Use global training active signal to avoid assignment conflicts
    enVtcShared := io.dfi.update.ctrlupdAck && !trainingActiveShared

    for (((serdes, delay), i) <- rdIserdes.zip(rdDelay).zipWithIndex) {
      // Configure delay line with proper reset and control signals - shared parameters
      delay.RST := sysRst | io.ctrl.reset | io.phyCtrl.dq_rst
      delay.CLK := sysClk
      delay.EN_VTC := enVtcShared
      delay.CE := io.phyCtrl.dq_inc && io.phyCtrl.dly_sel(i / 8)
      delay.INC := True
      // Fixed: Drive both DATAIN and IDATAIN to prevent NO DRIVER ON error
      delay.DATAIN := io.pads.dq(i)  // Primary data input
      delay.IDATAIN := io.pads.dq(i) // Secondary data input (typically same as DATAIN)
      delay.CNTVALUEIN := U(0, 9 bits) // Default to no additional delay
      // Fixed: Add missing cascade and load ports to match simulation stubs
      // Note: CASC_OUT is output port, leave unconnected in standalone mode
      if(delay.cascade != "NONE") {
        delay.CASC_IN := False   // No cascade input in standalone mode
        delay.CASC_RETURN := False // No cascade return in standalone mode
      }
      if(delay.delayType == "VAR_LOAD") delay.LOAD := False

      // Configure ISERDESE3 with DQS gating - shared parameters
      serdes.CLK := io.clk4x
      serdes.CLK_B := io.clk4xN
      serdes.CLKDIV := sysClk
      serdes.RST := io.ctrl.reset | sysRst
      serdes.D := delay.DATAOUT
      // Enable FIFO mode for better timing with DQS
      serdes.FIFO_RD_EN := dqsGate
      // Fixed: Add missing FIFO_RD_CLK to prevent NO DRIVER ON error
      serdes.FIFO_RD_CLK := sysClk  // Use the same clock as CLKDIV

      // FIFO status monitoring - removed for production code
      // Note: FIFO status can be monitored through serdes.FIFO_EMPTY signal if needed
    }

    // Connect read data to DFI interface with proper timing - optimized
    for((serdes, data) <- rdIserdes.zip(rdData)) {
      val deserializedData = BitSlip(serdes.Q, 2, io.phyCtrl.bitslip)
      // Fixed: Ensure proper width matching for read data
      val maskedData = deserializedData & (rdDataValid.asBits.resize(deserializedData.getWidth))
      data := maskedData.resize(data.getWidth) // Ensure proper width for DFI interface
    }

    // Generate rddata_valid signal for DFI - optimized
    for (rd <- io.dfi.read.rd) {
      rd.rddataValid := rdDataValid
    }

    // ==========================================================================
    // Multi-Rank Data Slice Handling
    // ==========================================================================
    // Handle data slices for multi-rank configurations
    val rankCount = dfiConfig.chipSelectNumber
    val dataSlices = Vec.fill(rankCount)(Bits(dfiConfig.dataWidth bits))

    // Data slice selection based on active chip select - enhanced for multi-device
    // Create local copies to avoid hierarchy violations
    val activeRankLocal = Reg(UInt(log2Up(dfiConfig.chipSelectNumber) bits)) init(0)
    val chipSelectMaskLocal = Reg(Bits(dfiConfig.chipSelectNumber bits)) init((BigInt(1) << dfiConfig.chipSelectNumber) - 1)
    
    // Expose cmdGen signals through handler to avoid hierarchy violations
    // Use DFI signals directly instead of accessing internal cmdGen signals
    val dfiCsNReg = RegNext(io.dfi.control.csN) init(B(0, dfiConfig.chipSelectNumber bits))
    
    // Calculate active chip select from DFI signals directly
    when(dfiCsNReg === 0) { // All chips selected
      activeRankLocal := 0
    } otherwise {
      // Find first active chip select - multi-device routing
      activeRankLocal := OHToUInt(dfiCsNReg)
    }
    
    // Use DFI chip select mask directly
    chipSelectMaskLocal := dfiCsNReg

    for (rank <- 0 until rankCount) {
      // Initialize rankData with default value to prevent latch
      val rankData = Bits(dfiConfig.dataWidth bits)
      rankData := 0  // Default value
      
      when(chipSelectMaskLocal(rank) === False) { // This rank is selected
        // Route data from the appropriate byte lanes for this rank
        for (byte <- 0 until dfiConfig.dataWidth / 8) {
          val byteIndex = rank * (dfiConfig.dataWidth / 8 / rankCount) + byte
          if (byteIndex < rdData.length) {
            // Fixed: Properly handle data width conversion for multi-rank
            val sourceData = rdData(byteIndex)
            val byteStart = byte * 8
            val byteEnd = Math.min(byteStart + 7, sourceData.getWidth - 1)
            if (byteStart < sourceData.getWidth) {
              if (byteEnd >= byteStart) {
                rankData(byteEnd downto byteStart) := sourceData(byteEnd downto byteStart)
              }
            }
          }
        }
      }
      dataSlices(rank) := rankData
    }

    // Connect data slices to DFI (if multi-rank read is supported)
    // Note: Current DFI spec may not support per-rank read data,
    // this is for future extension
  }

  // Connect initialization command inputs to command generator
  cmdPath.handler.initCmdValid := initManager.initCmdValid
  cmdPath.handler.initCmd := initManager.initCmd
  cmdPath.handler.initAddr := initManager.initAddr
  cmdPath.handler.initBa := initManager.initBa
  cmdPath.handler.initCsN := initManager.initCsN
  cmdPath.handler.initCke := initManager.initCke
  cmdPath.handler.initOdt := initManager.initOdt
  cmdPath.handler.initResetN := initManager.initResetN

  // Control signals
  cmdPath.handler.padOverride := initManager.padOverride

  // Direct connection of DFI control signals to pads (when not overridden by initialization)
  when(!initManager.padOverride) {
    // Chip select signals
    for (i <- 0 until dfiConfig.chipSelectNumber) {
      if (i < io.pads.cs_n.getWidth) {
        io.pads.cs_n(i) := io.dfi.control.csN(i)
      }
    }

    // Clock enable signals
    for (i <- 0 until dfiConfig.chipSelectNumber) {
      if (i < io.pads.cke.getWidth) {
        io.pads.cke(i) := io.dfi.control.cke(i)
      }
    }

    // ODT signals (if enabled)
    if (dfiConfig.signalConfig.useOdt) {
      for (i <- 0 until dfiConfig.chipSelectNumber) {
        if (i < io.pads.odt.getWidth) {
          io.pads.odt(i) := io.dfi.control.odt(i)
        }
      }
    }

    // Reset signals (if enabled)
    if (dfiConfig.signalConfig.useResetN) {
      for (i <- 0 until dfiConfig.chipSelectNumber) {
        if (i < io.pads.reset_n.getWidth) {
          io.pads.reset_n(i) := io.dfi.control.resetN(i)
        }
      }
    }

    // RAS, CAS, WE signals (if enabled)
    if (dfiConfig.signalConfig.useRasN) {
      for (i <- 0 until dfiConfig.controlWidth) {
        if (i < io.pads.ras_n.getWidth) {
          io.pads.ras_n(i) := io.dfi.control.rasN(i)
        }
      }
    }

    if (dfiConfig.signalConfig.useCasN) {
      for (i <- 0 until dfiConfig.controlWidth) {
        if (i < io.pads.cas_n.getWidth) {
          io.pads.cas_n(i) := io.dfi.control.casN(i)
        }
      }
    }

    if (dfiConfig.signalConfig.useWeN) {
      for (i <- 0 until dfiConfig.controlWidth) {
        if (i < io.pads.we_n.getWidth) {
          io.pads.we_n(i) := io.dfi.control.weN(i)
        }
      }
    }

    // Act_N signal (if enabled)
    if (dfiConfig.signalConfig.useAckN) {
      io.pads.act_n := io.dfi.control.actN.orR
    }
  } otherwise {
    // During initialization, use initialization manager signals
    io.pads.cs_n := initManager.initCsN
    io.pads.cke := initManager.initCke

    if (dfiConfig.signalConfig.useOdt) {
      io.pads.odt := initManager.initOdt
    }

    if (dfiConfig.signalConfig.useResetN) {
      io.pads.reset_n := initManager.initResetN
    }

    // Decode initCmd into individual control signals during initialization
    val initRasN = RegNext(False) init(False)
    val initCasN = RegNext(False) init(False)
    val initWeN = RegNext(False) init(False)
    val initActN = RegNext(False) init(False)

    switch(initManager.initCmd) {
      is(DdrCmd.ACT) {
        initRasN := False
        initCasN := True
        initWeN := True
        initActN := False
      }
      is(DdrCmd.READ) {
        initRasN := True
        initCasN := False
        initWeN := True
        initActN := True
      }
      is(DdrCmd.WRITE) {
        initRasN := True
        initCasN := False
        initWeN := False
        initActN := True
      }
      is(DdrCmd.PRE) {
        initRasN := False
        initCasN := True
        initWeN := False
        initActN := True
      }
      is(DdrCmd.REF) {
        initRasN := False
        initCasN := False
        initWeN := True
        initActN := True
      }
      is(DdrCmd.MRS) {
        initRasN := False
        initCasN := False
        initWeN := False
        initActN := True
      }
      default { // NOP
        initRasN := True
        initCasN := True
        initWeN := True
        initActN := True
      }
    }

    if (dfiConfig.signalConfig.useRasN) {
      io.pads.ras_n := B(initRasN, dfiConfig.controlWidth bits)
    }

    if (dfiConfig.signalConfig.useCasN) {
      io.pads.cas_n := B(initCasN, dfiConfig.controlWidth bits)
    }

    if (dfiConfig.signalConfig.useWeN) {
      io.pads.we_n := B(initWeN, dfiConfig.controlWidth bits)
    }

    if (dfiConfig.signalConfig.useAckN) {
      io.pads.act_n := initActN
    }
  }

  // Now that dataPath and cmdPath are defined, complete the trainingCtrl initialization
  trainingCtrl.writeLevelingSampledData := RegNext(dataPath.rdIserdes(0).Q(7 downto 0)) init(B(0, 8 bits)) // Sample first byte lane for write leveling
  trainingCtrl.readGateSampledData := RegNext(dataPath.rdIserdes(0).Q(7 downto 0)) init(B(0, 8 bits)) // Sample first byte lane for read gate training
  for (i <- 0 until 4) {
    val byteIndex = (i * 2) % (dfiConfig.dataWidth / 8) // Distribute across available byte lanes
    if (byteIndex < dataPath.rdIserdes.length) {
      trainingCtrl.readEyeSampledData(i) := RegNext(dataPath.rdIserdes(byteIndex).Q(7 downto 0)) init(B(0, 8 bits))
    } else {
      trainingCtrl.readEyeSampledData(i) := RegNext(dataPath.rdIserdes(0).Q(7 downto 0)) init(B(0, 8 bits)) // Use first byte lane as fallback
    }
  }
  trainingCtrl.caSampledAddr := RegNext(cmdPath.syncedAddressOut.take(dfiConfig.addressWidth).asBits.resize(dfiConfig.addressWidth)) init(B(0, dfiConfig.addressWidth bits)) // Sample address bits with proper resizing
  trainingCtrl.caSampledBank := RegNext(cmdPath.syncedBankOut.take(dfiConfig.bankWidth).asBits.resize(8)) init(B(0, 8 bits)) // Sample bank signals from exposed signals
  
  // Create local copy to avoid hierarchy violation
  val currentCmdLocal = Reg(DdrCmd()) init(DdrCmd.NOP)
  
  // Decode current command from DFI signals directly to avoid hierarchy violations
  val dfiRasN_or = io.dfi.control.rasN.orR
  val dfiCasN_or = io.dfi.control.casN.orR
  val dfiWeN_or = io.dfi.control.weN.orR
  val dfiActN_or = if (dfiConfig.signalConfig.useAckN) io.dfi.control.actN.orR else False
  
  // Decode command from DFI signals directly
  when((dfiRasN_or === False) && (dfiCasN_or === True) && (dfiWeN_or === True) && (dfiActN_or === False)) {
    currentCmdLocal := DdrCmd.ACT
  } elsewhen((dfiRasN_or === True) && (dfiCasN_or === False) && (dfiWeN_or === True)) {
    currentCmdLocal := DdrCmd.READ
  } elsewhen((dfiRasN_or === True) && (dfiCasN_or === False) && (dfiWeN_or === False)) {
    currentCmdLocal := DdrCmd.WRITE
  } elsewhen((dfiRasN_or === False) && (dfiCasN_or === False) && (dfiWeN_or === True)) {
    currentCmdLocal := DdrCmd.PRE
  } elsewhen((dfiRasN_or === False) && (dfiCasN_or === False) && (dfiWeN_or === False)) {
    // Use the highest available bits for ZQCS/REF detection
    val addrBits = if (dfiConfig.addressWidth >= 16) {
      io.dfi.control.address(15 downto 14)
    } else if (dfiConfig.addressWidth >= 15) {
      io.dfi.control.address(14 downto 13) // Use bits 14:13 for 15-bit addresses
    } else {
      B"00" // Default for smaller addresses
    }
    when(addrBits === B"11") {
      currentCmdLocal := DdrCmd.ZQCS
    } elsewhen(addrBits === B"10") {
      currentCmdLocal := DdrCmd.REF
    } otherwise {
      currentCmdLocal := DdrCmd.MRS
    }
  } otherwise {
    currentCmdLocal := DdrCmd.NOP
  }
  
  trainingCtrl.caCurrentCmd := currentCmdLocal // Sample current command

  // Move training control signal extraction and registration before TrainingController instantiation
  // Extract training control signals to avoid hierarchy violations
  val wrLvlEn = if (dfiConfig.useWrlvlEn && io.dfi.wrTraining != null) io.dfi.wrTraining.wrlvlEn.orR else False
  val wrLvlStrobe = if (dfiConfig.useWrlvlEn && io.dfi.wrTraining != null) io.dfi.wrTraining.wrlvlStrobe.orR else False
  val rdLvlEn = if (dfiConfig.useRdlvlEn && io.dfi.rdTraining != null) io.dfi.rdTraining.rdlvlEn.orR else False
  val rdLvlGateEn = if (dfiConfig.useRdlvlGateEn && io.dfi.rdTraining != null) io.dfi.rdTraining.rdlvlGateEn.orR else False
  val caLvlEn = if (dfiConfig.useCalvlEn && io.dfi.caTraining != null && io.dfi.caTraining.calvlEn != null) io.dfi.caTraining.calvlEn.orR else False

  // Register training control signals to avoid hierarchy violations
  val wrLvlEnReg = RegNext(wrLvlEn) init(False)
  val wrLvlStrobeReg = RegNext(wrLvlStrobe) init(False)
  val rdLvlEnReg = RegNext(rdLvlEn) init(False)
  val rdLvlGateEnReg = RegNext(rdLvlGateEn) init(False)
  val caLvlEnReg = RegNext(caLvlEn) init(False)

  // Instantiate TrainingController only if training is enabled to avoid hierarchy violations
  if (dfiConfig.useWrlvlEn || dfiConfig.useRdlvlEn || dfiConfig.useRdlvlGateEn || dfiConfig.useCalvlEn) {
    trainingCtrl.controller = new TrainingController(
      dfiConfig,
      initManager.initComplete,
      trainingCtrl.writeLevelingSampledData,
      trainingCtrl.readGateSampledData,
      trainingCtrl.readEyeSampledData,
      trainingCtrl.caSampledAddr,
      trainingCtrl.caSampledBank,
      trainingCtrl.caCurrentCmd,
      wrLvlEn,    // Use original DFI signal
      wrLvlStrobe, // Use original DFI signal
      rdLvlEn,    // Use original DFI signal
      rdLvlGateEn, // Use original DFI signal
      caLvlEn     // Use original DFI signal
    )
  }

  // Connect training status signals - use direct assignment approach
  // Set default values (for when training is disabled)
  trainingWriteLevelingDone := False
  trainingReadGateDone := False
  trainingReadEyeDone := False
  trainingCaTrainingDone := False
  trainingCdlyValueOut := U(0, 9 bits)
  trainingReadGateResponse := B(0, 1 bits)
  trainingReadEyeResponse := B(0, 1 bits)
  trainingWriteLevelingResponse := B(0, 1 bits)
  trainingCaTrainingResponse := B(0, 2 bits)

  // Override with controller signals only if controller exists and training is enabled
  if (dfiConfig.useWrlvlEn || dfiConfig.useRdlvlEn || dfiConfig.useRdlvlGateEn || dfiConfig.useCalvlEn) {
    // These assignments will override the defaults above
    trainingWriteLevelingDone := trainingCtrl.controller.writeLevelingDone
    trainingReadGateDone := trainingCtrl.controller.readGateDone
    trainingReadEyeDone := trainingCtrl.controller.readEyeDone
    trainingCaTrainingDone := trainingCtrl.controller.caTrainingDone
    trainingCdlyValueOut := trainingCtrl.controller.cdly_value_out
    trainingReadGateResponse := trainingCtrl.controller.readGateResponse
    trainingReadEyeResponse := trainingCtrl.controller.readEyeResponse
    trainingWriteLevelingResponse := trainingCtrl.controller.writeLevelingResponse
    trainingCaTrainingResponse := trainingCtrl.controller.caTrainingResponse
  }

  // Connect trainingCtrl area signals to global training signals to avoid hierarchy violations
  trainingCtrl.writeLevelingDone := trainingWriteLevelingDone
  trainingCtrl.readGateDone := trainingReadGateDone
  trainingCtrl.readEyeDone := trainingReadEyeDone
  trainingCtrl.caTrainingDone := trainingCaTrainingDone
  trainingCtrl.cdly_value_out := trainingCdlyValueOut
  trainingCtrl.readGateResponse := trainingReadGateResponse
  trainingCtrl.readEyeResponse := trainingReadEyeResponse
  trainingCtrl.writeLevelingResponse := trainingWriteLevelingResponse
  trainingCtrl.caTrainingResponse := trainingCaTrainingResponse

  // Connect PHY control outputs to training controller (only if training is enabled)
  if (dfiConfig.useWrlvlEn || dfiConfig.useRdlvlEn || dfiConfig.useRdlvlGateEn || dfiConfig.useCalvlEn) {
    io.phyCtrl.half_sys8x_taps := trainingCtrl.controller.half_sys8x_taps
    io.phyCtrl.dqs_inc_count := trainingCtrl.controller.dqs_inc_count
    io.phyCtrl.training_cdly_inc := trainingCtrl.controller.training_cdly_inc
    io.phyCtrl.training_dq_inc := trainingCtrl.controller.training_dq_inc
    io.phyCtrl.training_bitslip := trainingCtrl.controller.training_bitslip
    io.phyCtrl.cdly_value := trainingCtrl.controller.cdly_value_out
  } else {
    io.phyCtrl.half_sys8x_taps := U(0, 9 bits)
    io.phyCtrl.dqs_inc_count := U(0, 9 bits)
    io.phyCtrl.training_cdly_inc := False
    io.phyCtrl.training_dq_inc := False
    io.phyCtrl.training_bitslip := False
    io.phyCtrl.cdly_value := U(0, 9 bits)
  }
  
  // Training control signals already defined and registered above

  // BitSlip is now defined in XilinxUSPhyTypes.scala

  // TrainingController is now defined in XilinxUSPhyTypes.scala



  // ==========================================================================
  // Resource Usage Monitoring - Optimized
  // ==========================================================================
  val resourceMonitor = new Area {
    // Monitor LUT/FF usage through synthesis-time counters - conditionally enabled
    val enableResourceMonitoring = configParams.enableResourceMonitoring // Use dedicated config parameter
    val activeLuts = Counter(32 bits, inc = enableResourceMonitoring) // Track active LUT usage
    val activeFfs = Counter(32 bits, inc = enableResourceMonitoring)  // Track active FF usage

    // BRAM usage monitoring disabled for resource optimization - no BRAM used in this design
    // val bramUsage = RegInit(U(0, 8 bits)) // Commented out to minimize BRAM usage

    // Performance counters - optimized with conditional enable based on feature set
    val trainingCycles = Counter(32 bits, inc = enableResourceMonitoring && (configParams.standardMode || configParams.advancedMode)) // Track training time
    val activeCycles = Counter(32 bits, inc = enableResourceMonitoring && (configParams.standardMode || configParams.advancedMode))  // Track active operation time

    // Advanced feature counters - only enabled in advanced mode
    val errorCount = Counter(16 bits, inc = configParams.advancedMode) // Track error events
    val retrainingCount = Counter(8 bits, inc = configParams.advancedMode) // Track retraining events

    // Update counters based on activity - pipelined and feature-aware with null safety
    val trainingActive = RegNext(
      (Bool(dfiConfig.useWrlvlEn) && trainingWriteLevelingDone) ||
      (Bool(dfiConfig.useRdlvlEn) && trainingReadGateDone) ||
      (Bool(dfiConfig.useRdlvlGateEn) && trainingReadEyeDone) ||
      (Bool(dfiConfig.useCalvlEn) && trainingCaTrainingDone)
    ) init(False)
    val ckeActive = RegNext(io.dfi.control.cke.orR) init(False)

    // Feature-set aware counter updates
    when(trainingActive && (configParams.standardMode || configParams.advancedMode)) {
      trainingCycles.increment()
    }

    when(ckeActive && (configParams.standardMode || configParams.advancedMode)) {
      activeCycles.increment()
    }

    // Advanced mode error tracking
    when(configParams.advancedMode && (configParams.clockError || configParams.resetError || configParams.initError)) {
      errorCount.increment()
    }
  }

  // ==========================================================================
  // DFI Status Interface Implementation - Drive Required Signals
  // ==========================================================================
  // USPhy is a slave component but must drive certain status and control signals
  
  // Drive DFI status signals with safe default values
  if (dfiConfig.useAlertN) {
    io.dfi.status.alertN := B((BigInt(1) << (dfiConfig.alertWidth * dfiConfig.frequencyRatio)) - 1, dfiConfig.alertWidth * dfiConfig.frequencyRatio bits) // No alert by default
  }
  io.dfi.status.initComplete := initManager.initComplete
  // Connect initDone to both DFI status and external control interface
  io.ctrl.initDone := initManager.initComplete
  
  // Drive DFI training request signals (PHY responds to controller requests)
  if (dfiConfig.useRdlvlReq) {
    io.dfi.rdTraining.rdlvlReq := B(0, dfiConfig.readLevelingPhyIFWidth bits) // No read leveling request by default
  }
  if (dfiConfig.useRdlvlGateReq) {
    io.dfi.rdTraining.rdlvlGateReq := B(0, dfiConfig.readLevelingPhyIFWidth bits) // No read gate request by default
  }
  if (dfiConfig.useWrlvlReq) {
    io.dfi.wrTraining.wrlvlReq := B(0, dfiConfig.writeLevelingPhyIFWidth bits) // No write leveling request by default
  }
  if (dfiConfig.useCalvlReq) {
    io.dfi.caTraining.calvlReq := B(0, dfiConfig.caTrainingPhyIFWidth bits) // No CA training request by default
  }
  
  // Drive PHY chip select signals for training (responses to controller)
  if (dfiConfig.usePhyRdlvlCsN) {
    io.dfi.rdTraining.phyRdlvlCsN := B((BigInt(1) << (dfiConfig.chipSelectNumber * dfiConfig.readTrainingPhyIFWidth)) - 1, dfiConfig.chipSelectNumber * dfiConfig.readTrainingPhyIFWidth bits) // Default to inactive
  }
  if (dfiConfig.usePhyRdlvlGateCsN) {
    io.dfi.rdTraining.phyRdlvlGateCsN := B((BigInt(1) << (dfiConfig.readTrainingPhyIFWidth * dfiConfig.chipSelectNumber)) - 1, dfiConfig.readTrainingPhyIFWidth * dfiConfig.chipSelectNumber bits) // Default to inactive
  }
  if (dfiConfig.usePhyWrlvlCsN) {
    io.dfi.wrTraining.phyWrlvlCsN := B((BigInt(1) << (dfiConfig.chipSelectNumber * dfiConfig.writeLevelingPhyIFWidth)) - 1, dfiConfig.chipSelectNumber * dfiConfig.writeLevelingPhyIFWidth bits) // Default to inactive
  }
  if (dfiConfig.usePhyCalvlCsN) {
    io.dfi.caTraining.phyCalvlCsN := B((BigInt(1) << dfiConfig.chipSelectNumber) - 1, dfiConfig.chipSelectNumber bits) // Default to inactive
  }
  
  // Connect internal signals for internal use
  val internalInitStart = initManager.initActive
  val internalInitComplete = initManager.initComplete
  
  // Internal signals for use within the PHY
  val internalFreqRatio = clockGen.freqRatio
  val internalDramClkDisable = dramClkDisable

  // Update clockGen clkDisableReq after dramClkDisable is defined
  clockGen.clkDisableReq := dramClkDisable
  
  // Assign dramClkDisable with default value (low power control not available yet)
  dramClkDisable := False

  // ==========================================================================
  // DFI Update Interface Implementation - Optimized
  // ==========================================================================
  // Control update handshake - pipelined
  if (dfiConfig.useCtrlupdReq) {
    val ctrlupdAckReg = RegNext(io.dfi.update.ctrlupdReq) init(False)
    io.dfi.update.ctrlupdAck := ctrlupdAckReg
  }

  // PHY update request - PHY can request updates (e.g., after training) - optimized
  if (dfiConfig.usePhyupdReq) {
    // PHY requests update after training completion
    val fsmDoneReg = Reg(Bool()) init(False)
    // Use global training signals to avoid hierarchy violations
    val allTrainingDone = RegNext(
      (trainingWriteLevelingDone && trainingReadGateDone &&
       trainingReadEyeDone && trainingCaTrainingDone) ||
      (trainingWriteLevelingDone && Bool(!dfiConfig.useRdlvlEn) && Bool(!dfiConfig.useRdlvlGateEn) && Bool(!dfiConfig.useCalvlEn)) ||
      (trainingReadGateDone && Bool(!dfiConfig.useWrlvlEn) && Bool(!dfiConfig.useRdlvlGateEn) && Bool(!dfiConfig.useCalvlEn)) ||
      (trainingReadEyeDone && Bool(!dfiConfig.useWrlvlEn) && Bool(!dfiConfig.useRdlvlEn) && Bool(!dfiConfig.useCalvlEn)) ||
      (trainingCaTrainingDone && Bool(!dfiConfig.useWrlvlEn) && Bool(!dfiConfig.useRdlvlEn) && Bool(!dfiConfig.useRdlvlGateEn))
    ) init(False)
    fsmDoneReg := allTrainingDone
    val phyUpdateReq = fsmDoneReg && !RegNext(fsmDoneReg)
    io.dfi.update.phyupdReq := phyUpdateReq
    io.dfi.update.phyupdType := B"01" // Training complete update type
  }

  // ==========================================================================
  // DFI Training Response Signals Implementation - Optimized
  // ==========================================================================
  // Training responses are now handled by the TrainingController FSM
  // Default values to avoid driver conflicts - optimized with null safety
  // Centralized training response assignment to avoid conflicts
  if (dfiConfig.useRdlvlResp && io.dfi.rdTraining != null) {
    val rdLvlRespReg = Reg(Bits(1 bits)) init(0)
    // Use TrainingController's exposed signals to avoid hierarchy violations
    val readGateDone = RegNext(trainingCtrl.readGateDone) init(False)
    val readGateResponse = RegNext(trainingCtrl.readGateResponse) init(B(0, 1 bits))
    val readEyeDone = RegNext(trainingCtrl.readEyeDone) init(False)
    val readEyeResponse = RegNext(trainingCtrl.readEyeResponse) init(B(0, 1 bits))
    
    // Assign response based on which training module is active and done
    when(readGateDone) {
      rdLvlRespReg := readGateResponse
    }.elsewhen(readEyeDone) {
      rdLvlRespReg := readEyeResponse
    }.otherwise {
      rdLvlRespReg := 0
    }
    io.dfi.rdTraining.rdlvlResp := rdLvlRespReg
  }

  if (dfiConfig.useWrlvlResp && io.dfi.wrTraining != null) {
    val wrLvlRespReg = Reg(Bits(1 bits)) init(0)
    // Use TrainingController's exposed signals to avoid hierarchy violations
    val writeLevelingDone = RegNext(trainingCtrl.writeLevelingDone) init(False)
    val writeLevelingResponse = RegNext(trainingCtrl.writeLevelingResponse) init(B(0, 1 bits))
    
    when(writeLevelingDone) {
      wrLvlRespReg := writeLevelingResponse
    }.otherwise {
      wrLvlRespReg := 0
    }
    io.dfi.wrTraining.wrlvlResp := wrLvlRespReg
  }

  if (dfiConfig.useCalvlResp && io.dfi.caTraining != null) {
    val caLvlRespReg = Reg(Bits(2 bits)) init(0)
    // Use TrainingController's exposed signals to avoid hierarchy violations
    val caTrainingDone = RegNext(trainingCtrl.caTrainingDone) init(False)
    val caTrainingResponse = RegNext(trainingCtrl.caTrainingResponse) init(B(0, 2 bits))
    
    when(caTrainingDone) {
      caLvlRespReg := caTrainingResponse
    }.otherwise {
      caLvlRespReg := 0
    }
    io.dfi.caTraining.calvlResp := caLvlRespReg
  }


  // ==========================================================================
  // Low-Power Mode Optimizations
  // ==========================================================================
  val lowPowerCtrl = new Area {
    // Automatic clock gating based on activity
    val clockGateEnable = configParams.autoClockGating && !io.dfi.control.cke.orR
    val powerDownEnable = configParams.autoPowerDown && !io.dfi.control.cke.orR

    // Low power state machine
    val lpState = RegInit(U(0, 2 bits)) // 0: active, 1: clock gated, 2: power down
    switch(lpState) {
      is(U(0)) { // Active
        when(clockGateEnable) { lpState := U(1) }
      }
      is(U(1)) { // Clock gated
        when(powerDownEnable) { lpState := U(2) }
        .elsewhen(!clockGateEnable) { lpState := U(0) }
      }
      is(U(2)) { // Power down
        when(!powerDownEnable) { lpState := U(0) }
      }
    }
  
    // Apply low power controls
    // Note: clockGen.clkEnable is already assigned in clockGen area
    // Low power control should use a separate signal to avoid assignment conflicts
    when(lpState >= U(2)) {
      // Additional power down logic would go here
    }
  
    // DFI Low Power Control Interface
    if (dfiConfig.useLpCtrlReq) {
      // Acknowledge low power requests
      io.dfi.lowPowerControl.lpAck := io.dfi.lowPowerControl.lpCtrlReq
    }
  }
}
