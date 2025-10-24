package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 控制管理器组件
 *
 * 合并了CalibrationEngine和InitializationManager的功能，
 * 统一管理DDR初始化、校准和训练状态机。
 */
case class ControlManager(config: ControlConfig) extends Component {

  val io = new Bundle {
    // 训练接口
    val training = slave(DfiTrainingInterface(config.dfiConfig))

    // 初始化接口
    val init = slave(DdrInitInterface(config.sdramConfig))

    // 校准接口
    val calibrationInterface = slave(DdrCalibrationInterface(config.sdramConfig))

    // DDR存储器接口
    val sdram = master(DdrInterface(config.sdramConfig))

    // 调试接口
    val debug = out(ControlManagerDebug())
  }

  // 控制状态机
  val controlFsm = ControlFsm(config, io.training, io.init, io.calibrationInterface)

  // DDR接口控制器
  val ddrInterfaceController = ControlDdrInterfaceController(config, controlFsm.ddrCommand)
  io.sdram := ddrInterfaceController.sdram

  // 模式寄存器控制器
  val modeRegisterController = ControlModeRegisterController(config, controlFsm.mrCommand)

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.initCount := controlFsm.debug.initCount
  io.debug.calibrationCount := controlFsm.debug.calibrationCount
  io.debug.errorCount := controlFsm.debug.errorCount
  io.debug.currentState := controlFsm.debug.currentState
}

/**
 * 控制配置
 */
case class ControlConfig(
    ddrStandard: DdrStandard.E,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures
)

/**
 * 控制有限状态机
 */
case class ControlFsm(config: ControlConfig,
                      training: DfiTrainingInterface,
                      init: DdrInitInterface,
                      calibrationInterface: DdrCalibrationInterface) extends Area {

  // 输出信号 - directionless
  val ddrCommand = DdrInitCommandInterface()
  val mrCommand = ModeRegisterCommandInterface()
  val debug = ControlFsmDebug()

  // 控制状态枚举 - 合并初始化、校准和训练状态
  object ControlState extends SpinalEnum {
    val IDLE, POWER_UP, RESET, CKE_LOW, MRS, ZQ_CALIBRATION,
         READ_LEVELING, WRITE_LEVELING, CA_TRAINING, DONE, ERROR = newElement()
  }

  // 状态寄存器
  val currentState = Reg(ControlState()) init ControlState.IDLE
  val initCount = Reg(UInt(32 bits)) init 0
  val calibrationCount = Reg(UInt(32 bits)) init 0
  val errorCount = Reg(UInt(32 bits)) init 0

  // 初始化定时器
  val initTimer = Reg(UInt(16 bits)) init 0
  val initDelay = Reg(UInt(16 bits)) init 0

  // 状态机逻辑 - 合并初始化序列和校准训练
  switch(currentState) {
    is(ControlState.IDLE) {
      when(init.initStart) {
        currentState := ControlState.POWER_UP
        initTimer := 0
        initDelay := 200 // 200us power up time
      } elsewhen(training.readTraining.req) {
        currentState := ControlState.READ_LEVELING
      } elsewhen(training.writeTraining.req) {
        currentState := ControlState.WRITE_LEVELING
      } elsewhen(training.caTraining.req) {
        currentState := ControlState.CA_TRAINING
      }
    }

    // 初始化序列状态
    is(ControlState.POWER_UP) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := ControlState.RESET
        initTimer := 0
        initDelay := 200 // 200us reset time
      }
    }

    is(ControlState.RESET) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := ControlState.CKE_LOW
        initTimer := 0
        initDelay := 10 // 10 cycles CKE low
      }
    }

    is(ControlState.CKE_LOW) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := ControlState.MRS
      }
    }

    is(ControlState.MRS) {
      // 执行模式寄存器设置
      mrCommand.valid := True
      mrCommand.mr0 := B"16'h0520" // 示例MR0设置
      mrCommand.mr1 := B"16'h0000"
      mrCommand.mr2 := B"16'h0000"
      mrCommand.mr3 := B"16'h0000"

      when(mrCommand.done) {
        when(init.zqCalibration) {
          currentState := ControlState.ZQ_CALIBRATION
        } otherwise {
          currentState := ControlState.DONE
        }
      }
    }

    is(ControlState.ZQ_CALIBRATION) {
      // 执行ZQ校准
      ddrCommand.valid := True
      ddrCommand.cmd := DdrCommand.ZQCS

      when(ddrCommand.done) {
        currentState := ControlState.DONE
      }
    }

    // 校准训练状态
    is(ControlState.READ_LEVELING) {
      // 执行读电平校准
      when(training.readTraining.resp.orR) {
        currentState := ControlState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(ControlState.WRITE_LEVELING) {
      // 执行写电平校准
      when(training.writeTraining.resp.orR) {
        currentState := ControlState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(ControlState.CA_TRAINING) {
      // 执行CA训练
      when(training.caTraining.resp.orR) {
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

  // 初始化完成信号
  init.initComplete := currentState === ControlState.DONE

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  debug.initCount := initCount
  debug.calibrationCount := calibrationCount
  debug.errorCount := errorCount
  debug.currentState := currentState.asBits
}

/**
 * DDR接口控制器（从InitializationManager复用）
 */
case class ControlDdrInterfaceController(config: ControlConfig,
                                         initCommand: DdrInitCommandInterface) extends Area {

  // 输出信号 - directionless
  val sdram = DdrInterface(config.sdramConfig)

  // DDR接口信号生成
  sdram.clk := ClockDomain.current.readClockWire
  sdram.clk_n := !ClockDomain.current.readClockWire
  sdram.cke(0) := True
  sdram.cs_n(0) := False
  sdram.ras_n := True
  sdram.cas_n := True
  sdram.we_n := True
  sdram.addr := B"0".resized
  sdram.ba := B"0".resized
  sdram.dq := B"0".resized
  sdram.dqs := B"0".resized
  sdram.dqs_n := B"0".resized
  sdram.dm := B"0".resized
  sdram.odt(0) := False
  sdram.reset_n := True

  // 根据命令设置控制信号
  when(initCommand.valid) {
    switch(initCommand.cmd) {
      is(DdrCommand.NOP) {
        sdram.cs_n := B"1"
      }
      is(DdrCommand.ACT) {
        sdram.ras_n := False
        sdram.cs_n := B"0"
      }
      is(DdrCommand.READ) {
        sdram.cas_n := False
        sdram.cs_n := B"0"
        sdram.we_n := True
      }
      is(DdrCommand.WRITE) {
        sdram.cas_n := False
        sdram.we_n := False
        sdram.cs_n := B"0"
      }
      is(DdrCommand.PRE) {
        sdram.ras_n := False
        sdram.we_n := False
        sdram.cs_n := B"0"
      }
      is(DdrCommand.REF) {
        sdram.ras_n := False
        sdram.cas_n := False
        sdram.cs_n := B"0"
      }
      is(DdrCommand.MRS) {
        sdram.ras_n := False
        sdram.cas_n := False
        sdram.we_n := False
        sdram.cs_n := B"0"
      }
      is(DdrCommand.ZQCS) {
        sdram.ras_n := False
        sdram.cas_n := False
        sdram.we_n := False
        sdram.cs_n := B"0"
      }
    }
  }
}

/**
 * 模式寄存器控制器（从InitializationManager复用）
 */
case class ControlModeRegisterController(config: ControlConfig,
                                         mrCommand: ModeRegisterCommandInterface) extends Area {

  // 模式寄存器设置状态机
  object MrState extends SpinalEnum {
    val IDLE, MR0, MR1, MR2, MR3, DONE = newElement()
  }

  val currentState = Reg(MrState()) init MrState.IDLE
  val done = Reg(Bool()) init False

  // 状态机逻辑
  switch(currentState) {
    is(MrState.IDLE) {
      when(mrCommand.valid) {
        currentState := MrState.MR0
        done := False
      }
    }

    is(MrState.MR0) {
      // 设置MR0
      currentState := MrState.MR1
    }

    is(MrState.MR1) {
      // 设置MR1
      currentState := MrState.MR2
    }

    is(MrState.MR2) {
      // 设置MR2
      currentState := MrState.MR3
    }

    is(MrState.MR3) {
      // 设置MR3
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
 * 调试接口定义
 */
case class ControlManagerDebug() extends Bundle {
  val initCount = UInt(32 bits)
  val calibrationCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
  val currentState = Bits(4 bits)
}

case class ControlFsmDebug() extends Bundle {
  val initCount = UInt(32 bits)
  val calibrationCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
  val currentState = Bits(4 bits)
}