package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 初始化管理器
 *
 * 负责执行DDR初始化序列，管理模式寄存器配置，
 * 处理复位和唤醒流程。
 */
case class InitializationManager(config: InitializationConfig) extends Component {

  val io = new Bundle {
    // 初始化接口（使用合并后的DdrInitInterface）
    val init = slave(DdrInitInterface(config.sdramConfig))

    // 初始化接口
    val initializationInterface = slave(DdrInitInterface(config.sdramConfig))

    // DDR存储器接口
    val sdram = master(DdrInterface(config.sdramConfig))

    // 调试接口
    val debug = out(InitializationManagerDebug())
  }

  // 初始化状态机
  val initFsm = InitializationFsm(config)
  initFsm.io.dfiInit <> io.init
  initFsm.io.ddrInit <> io.initializationInterface

  // DDR接口控制器
  val ddrInterfaceController = DdrInterfaceController(config)
  ddrInterfaceController.io.initCommand <> initFsm.io.ddrCommand
  io.sdram <> ddrInterfaceController.io.sdram

  // 模式寄存器控制器
  val modeRegisterController = ModeRegisterController(config)
  modeRegisterController.io.mrCommand <> initFsm.io.mrCommand

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.initCount := initFsm.io.debug.initCount
  io.debug.errorCount := initFsm.io.debug.errorCount
}

/**
 * 初始化配置
 */
case class InitializationConfig(
    ddrStandard: DdrStandard.C,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig
)

/**
 * 初始化有限状态机
 */
case class InitializationFsm(config: InitializationConfig) extends Area {

  val io = new Bundle {
    val dfiInit = slave(DdrInitInterface(config.sdramConfig)) // 使用合并后的接口
    val ddrInit = slave(DdrInitInterface(config.sdramConfig))
    val ddrCommand = master(DdrInitCommandInterface())
    val mrCommand = master(ModeRegisterCommandInterface())
    val debug = out(InitializationFsmDebug())
  }

  // 初始化状态枚举
  object InitState extends SpinalEnum {
    val IDLE, POWER_UP, RESET, CKE_LOW, MRS, ZQ_CALIBRATION, DONE, ERROR = newElement()
  }

  // 状态寄存器
  val currentState = Reg(InitState()) init InitState.IDLE
  val initCount = Reg(UInt(32 bits)) init 0
  val errorCount = Reg(UInt(32 bits)) init 0

  // 初始化定时器
  val initTimer = Reg(UInt(16 bits)) init 0
  val initDelay = Reg(UInt(16 bits)) init 0

  // 状态机逻辑
  switch(currentState) {
    is(InitState.IDLE) {
      when(io.dfiInit.initStart) {
        currentState := InitState.POWER_UP
        initTimer := 0
        initDelay := 200 // 200us power up time
      }
    }

    is(InitState.POWER_UP) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := InitState.RESET
        initTimer := 0
        initDelay := 200 // 200us reset time
      }
    }

    is(InitState.RESET) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := InitState.CKE_LOW
        initTimer := 0
        initDelay := 10 // 10 cycles CKE low
      }
    }

    is(InitState.CKE_LOW) {
      initTimer := initTimer + 1
      when(initTimer >= initDelay) {
        currentState := InitState.MRS
      }
    }

    is(InitState.MRS) {
      // 执行模式寄存器设置
      io.mrCommand.valid := True
      io.mrCommand.mr0 := B"16'h0520" // 示例MR0设置
      io.mrCommand.mr1 := B"16'h0000"
      io.mrCommand.mr2 := B"16'h0000"
      io.mrCommand.mr3 := B"16'h0000"

      when(io.mrCommand.done) {
        when(io.ddrInit.zqCalibration) {
          currentState := InitState.ZQ_CALIBRATION
        } otherwise {
          currentState := InitState.DONE
        }
      }
    }

    is(InitState.ZQ_CALIBRATION) {
      // 执行ZQ校准
      io.ddrCommand.valid := True
      io.ddrCommand.cmd := DdrCommand.ZQCS

      when(io.ddrCommand.done) {
        currentState := InitState.DONE
      }
    }

    is(InitState.DONE) {
      initCount := initCount + 1
      when(io.dfiInit.initStart) {
        currentState := InitState.IDLE
      }
    }

    is(InitState.ERROR) {
      errorCount := errorCount + 1
      currentState := InitState.IDLE
    }
  }

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.initCount := initCount
  io.debug.errorCount := errorCount
}



/**
 * DDR接口控制器
 */
case class DdrInterfaceController(config: InitializationConfig) extends Area {

  val io = new Bundle {
    val initCommand = slave(DdrInitCommandInterface())
    val sdram = master(DdrInterface(config.sdramConfig))
  }

  // DDR接口信号生成
  io.sdram.clk := ClockDomain.current.readClockWire
  io.sdram.clk_n := !ClockDomain.current.readClockWire
  io.sdram.cke(0) := True
  io.sdram.cs_n(0) := False
  io.sdram.ras_n := True
  io.sdram.cas_n := True
  io.sdram.we_n := True
  io.sdram.addr := B"0"
  io.sdram.ba := B"0"
  io.sdram.dq := B"0"
  io.sdram.dqs := B"0"
  io.sdram.dqs_n := B"0"
  io.sdram.dm := B"0"
  io.sdram.odt(0) := False
  io.sdram.reset_n := True

  // 根据命令设置控制信号
  when(io.initCommand.valid) {
    switch(io.initCommand.cmd) {
      is(DdrCommand.NOP) {
        io.sdram.cs_n := B"1"
      }
      is(DdrCommand.ACT) {
        io.sdram.ras_n := False
        io.sdram.cs_n := B"0"
      }
      is(DdrCommand.READ) {
        io.sdram.cas_n := False
        io.sdram.cs_n := B"0"
        io.sdram.we_n := True
      }
      is(DdrCommand.WRITE) {
        io.sdram.cas_n := False
        io.sdram.we_n := False
        io.sdram.cs_n := B"0"
      }
      is(DdrCommand.PRE) {
        io.sdram.ras_n := False
        io.sdram.we_n := False
        io.sdram.cs_n := B"0"
      }
      is(DdrCommand.REF) {
        io.sdram.ras_n := False
        io.sdram.cas_n := False
        io.sdram.cs_n := B"0"
      }
      is(DdrCommand.MRS) {
        io.sdram.ras_n := False
        io.sdram.cas_n := False
        io.sdram.we_n := False
        io.sdram.cs_n := B"0"
      }
      is(DdrCommand.ZQCS) {
        io.sdram.ras_n := False
        io.sdram.cas_n := False
        io.sdram.we_n := False
        io.sdram.cs_n := B"0"
      }
    }
  }
}

/**
 * 模式寄存器控制器
 */
case class ModeRegisterController(config: InitializationConfig) extends Area {

  val io = new Bundle {
    val mrCommand = slave(ModeRegisterCommandInterface())
  }

  // 模式寄存器设置状态机
  object MrState extends SpinalEnum {
    val IDLE, MR0, MR1, MR2, MR3, DONE = newElement()
  }

  val currentState = Reg(MrState()) init MrState.IDLE
  val done = Reg(Bool()) init False

  // 状态机逻辑
  switch(currentState) {
    is(MrState.IDLE) {
      when(io.mrCommand.valid) {
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

  io.mrCommand.done := done
}

/**
 * 调试接口定义
 */
case class InitializationManagerDebug() extends Bundle {
  val initCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}

case class InitializationFsmDebug() extends Bundle {
  val initCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}