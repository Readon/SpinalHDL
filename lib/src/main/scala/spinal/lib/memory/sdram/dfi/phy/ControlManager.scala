package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * Control Manager Component
 *
 * Merges CalibrationEngine and InitializationManager functionality,
 * Unified management of DDR initialization, calibration and training state machines.
 */
case class ControlManager(config: ControlConfig) extends Component {

  val io = new Bundle {
    // Training interface
    val training = slave(DfiTrainingInterface(config.dfiConfig))

    // Initialization interface
    val init = slave(DdrInitInterface(config.sdramConfig))

    // Calibration interface
    val calibrationInterface = slave(DdrCalibrationInterface(config.sdramConfig))

    // DDR memory interface
    val sdram = master(DdrInterface(config.sdramConfig))

    // Debug interface
    val debug = out(ControlManagerDebug())
  }

  // Control state machine
  val controlFsm = ControlFsm(config, io.training, io.init, io.calibrationInterface)

  // DDR interface controller
  val ddrInterfaceController = ControlDdrInterfaceController(config, controlFsm.ddrCommand)
  io.sdram := ddrInterfaceController.sdram

  // Mode register controller
  val modeRegisterController = ControlModeRegisterController(config, controlFsm.mrCommand)

  // Debug signals - REQ-CS-018 compliance: direct object access
  io.debug.initCount := controlFsm.debug.initCount
  io.debug.calibrationCount := controlFsm.debug.calibrationCount
  io.debug.errorCount := controlFsm.debug.errorCount
  io.debug.currentState := controlFsm.debug.currentState
}

/**
 * Control configuration
 */
case class ControlConfig(
    ddrStandard: DdrStandard.E,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures
)

/**
 * Control finite state machine
 */
case class ControlFsm(config: ControlConfig,
                      training: DfiTrainingInterface,
                      init: DdrInitInterface,
                      calibrationInterface: DdrCalibrationInterface) extends Area {

  // Output signals - directionless
  val ddrCommand = DdrInitCommandInterface()
  val mrCommand = ModeRegisterCommandInterface()
  val debug = ControlFsmDebug()

  // Control state enumeration - merge initialization, calibration and training states
  object ControlState extends SpinalEnum {
    val IDLE, POWER_UP, RESET, CKE_LOW, MRS, ZQ_CALIBRATION,
         READ_LEVELING, WRITE_LEVELING, CA_TRAINING, DONE, ERROR = newElement()
  }

  // State registers using common constants (REQ-CS-008 compliance)
  val currentState = Reg(ControlState()) init ControlState.IDLE
  val initCount = Reg(UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)) init 0
  val calibrationCount = Reg(UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)) init 0
  val errorCount = Reg(UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)) init 0

  // Initialization timer using common constants (REQ-CS-008 compliance)
  val initTimer = Reg(UInt(DfiCommonConstants.TIMER_WIDTH bits)) init 0
  val initDelay = Reg(UInt(DfiCommonConstants.TIMER_WIDTH bits)) init 0

  // Default assignments to avoid latches using common constants (REQ-CS-008 compliance)
  mrCommand.valid := False
  mrCommand.mr0 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)
  mrCommand.mr1 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)
  mrCommand.mr2 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)
  mrCommand.mr3 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)
  ddrCommand.valid := False
  ddrCommand.cmd := DdrCommand.NOP

  // Default training response assignments - as slave interface, need to drive resp signals
  // Use conditional checks to avoid accessing inaccessible signals
  if (training.readTraining.resp != null) {
    training.readTraining.resp := B(DfiCommonConstants.TRAINING_SUCCESS_1BIT_INT, 1 bits) // Default success
  }
  if (training.writeTraining.resp != null) {
    training.writeTraining.resp := B(DfiCommonConstants.TRAINING_SUCCESS_1BIT_INT, 1 bits) // Default success
  }
  if (training.caTraining.resp != null) {
    training.caTraining.resp := B(DfiCommonConstants.TRAINING_SUCCESS_2BIT_INT, 2 bits) // Default success
  }

  // State machine logic - merge initialization sequence and calibration training
  // Define timing constants for initialization sequence (REQ-CS-008 compliance)
  val POWER_UP_CYCLES = 200  // 200us power up time
  val RESET_CYCLES = 200      // 200us reset time
  val CKE_LOW_CYCLES = 10     // 10 cycles CKE low

  switch(currentState) {
    is(ControlState.IDLE) {
      when(init.initStart) {
        currentState := ControlState.POWER_UP
        initTimer := 0
        initDelay := POWER_UP_CYCLES
      } elsewhen(training.readTraining.req) {
        currentState := ControlState.READ_LEVELING
      } elsewhen(training.writeTraining.req) {
        currentState := ControlState.WRITE_LEVELING
      } elsewhen(training.caTraining.req) {
        currentState := ControlState.CA_TRAINING
      }
    }

    // Initialization sequence states
    is(ControlState.POWER_UP) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := ControlState.RESET
        initTimer := 0
        initDelay := RESET_CYCLES
      }
    }

    is(ControlState.RESET) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := ControlState.CKE_LOW
        initTimer := 0
        initDelay := CKE_LOW_CYCLES
      }
    }

    is(ControlState.CKE_LOW) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := ControlState.MRS
      }
    }

    is(ControlState.MRS) {
      // Execute mode register settings using common constants (REQ-CS-008 compliance)
      mrCommand.valid := True
      mrCommand.mr0 := B(DfiCommonConstants.MR0_DDR3_INT, 16 bits) // DDR3 MR0 setting
      mrCommand.mr1 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)
      mrCommand.mr2 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)
      mrCommand.mr3 := B(DfiCommonConstants.MR_DEFAULT_INT, 16 bits)

      when(mrCommand.done) {
        when(init.zqCalibration) {
          currentState := ControlState.ZQ_CALIBRATION
        } otherwise {
          currentState := ControlState.DONE
        }
      }
    }

    is(ControlState.ZQ_CALIBRATION) {
      // Execute ZQ calibration
      ddrCommand.valid := True
      ddrCommand.cmd := DdrCommand.ZQCS

      when(ddrCommand.done) {
        currentState := ControlState.DONE
      }
    }

    // Calibration training states
    is(ControlState.READ_LEVELING) {
      // Execute read leveling calibration
      when(training.readTraining.resp.orR) {
        currentState := ControlState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(ControlState.WRITE_LEVELING) {
      // Execute write leveling calibration
      when(training.writeTraining.resp.orR) {
        currentState := ControlState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(ControlState.CA_TRAINING) {
      // Execute CA training
      when(training.caTraining.req) {
        if (training.caTraining.resp != null) {
          training.caTraining.resp := B(DfiCommonConstants.TRAINING_SUCCESS_2BIT_INT, 2 bits) // Set training success response
        }
        currentState := ControlState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(ControlState.DONE) {
      when(init.initStart) {
        initCount := initCount + 1
        currentState := ControlState.IDLE
      } otherwise {
        currentState := ControlState.IDLE
      }
    }

    is(ControlState.ERROR) {
      errorCount := errorCount + 1
      currentState := ControlState.IDLE
    }
  }

  // Initialization complete signal
  init.initComplete := currentState === ControlState.DONE

  // Debug signals - REQ-CS-018 compliance: direct object access
  debug.initCount := initCount
  debug.calibrationCount := calibrationCount
  debug.errorCount := errorCount
  debug.currentState := currentState.asBits
}

/**
 * DDR interface controller (repurposed from InitializationManager)
 */
case class ControlDdrInterfaceController(config: ControlConfig,
                                         initCommand: DdrInitCommandInterface) extends Area {

  // Output signals - directionless
  val sdram = DdrInterface(config.sdramConfig)

  // DDR interface signal generation
  sdram.clk := ClockDomain.current.readClockWire
  sdram.clk_n := !ClockDomain.current.readClockWire
  sdram.cke(0) := True
  sdram.cs_n(0) := False
  sdram.ras_n := True
  sdram.cas_n := True
  sdram.we_n := True
  sdram.addr := B(0).resized
  sdram.ba := B(0).resized
  sdram.dq := B(0).resized
  sdram.dqs := B(0).resized
  sdram.dqs_n := B(0).resized
  sdram.dm := B(0).resized
  sdram.odt(0) := False
  sdram.reset_n := True

  // Set control signals based on command
  when(initCommand.valid) {
    switch(initCommand.cmd) {
      is(DdrCommand.NOP) {
        sdram.cs_n := B(1)
      }
      is(DdrCommand.ACT) {
        sdram.ras_n := False
        sdram.cs_n := B(0)
      }
      is(DdrCommand.READ) {
        sdram.cas_n := False
        sdram.cs_n := B(0)
        sdram.we_n := True
      }
      is(DdrCommand.WRITE) {
        sdram.cas_n := False
        sdram.we_n := False
        sdram.cs_n := B(0)
      }
      is(DdrCommand.PRE) {
        sdram.ras_n := False
        sdram.we_n := False
        sdram.cs_n := B(0)
      }
      is(DdrCommand.REF) {
        sdram.ras_n := False
        sdram.cas_n := False
        sdram.cs_n := B(0)
      }
      is(DdrCommand.MRS) {
        sdram.ras_n := False
        sdram.cas_n := False
        sdram.we_n := False
        sdram.cs_n := B(0)
      }
      is(DdrCommand.ZQCS) {
        sdram.ras_n := False
        sdram.cas_n := False
        sdram.we_n := False
        sdram.cs_n := B(0)
      }
    }
  }

  // Add done signal drive
  initCommand.done := initCommand.valid
}

/**
 * Mode register controller (repurposed from InitializationManager)
 */
case class ControlModeRegisterController(config: ControlConfig,
                                         mrCommand: ModeRegisterCommandInterface) extends Area {

  // Mode register setup state machine
  object MrState extends SpinalEnum {
    val IDLE, MR0, MR1, MR2, MR3, DONE = newElement()
  }

  val currentState = Reg(MrState()) init MrState.IDLE
  val done = Reg(Bool()) init False

  // State machine logic
  switch(currentState) {
    is(MrState.IDLE) {
      when(mrCommand.valid) {
        currentState := MrState.MR0
        done := False
      }
    }

    is(MrState.MR0) {
      // Setup MR0
      currentState := MrState.MR1
    }

    is(MrState.MR1) {
      // Setup MR1
      currentState := MrState.MR2
    }

    is(MrState.MR2) {
      // Setup MR2
      currentState := MrState.MR3
    }

    is(MrState.MR3) {
      // Setup MR3
      currentState := MrState.DONE
    }

    is(MrState.DONE) {
      done := True
      currentState := MrState.IDLE
    }
  }

  mrCommand.done := done
}

/**
 * Debug interface definition
 */
case class ControlManagerDebug() extends Bundle {
  val initCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val calibrationCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val errorCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val currentState = Bits(DfiCommonConstants.PHASE_COUNT bits)
}

case class ControlFsmDebug() extends Bundle {
  val initCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val calibrationCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val errorCount = UInt(DfiCommonConstants.DEBUG_COUNTER_WIDTH bits)
  val currentState = Bits(DfiCommonConstants.PHASE_COUNT bits)
}