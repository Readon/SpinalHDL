package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._

/**
 * 校准引擎组件
 *
 * 负责执行DDR训练和校准操作，包括写电平校准、读校准、CA训练等。
 * 支持不同DDR标准的特定训练流程。
 */
case class CalibrationEngine(config: CalibrationConfig) extends Component {

  val io = new Bundle {
    // 训练接口
    val training = slave(DfiTrainingInterface(config.dfiConfig))

    // 校准接口
    val calibrationInterface = slave(DdrCalibrationInterface(config.sdramConfig))

    // 调试接口
    val debug = out(CalibrationEngineDebug())
  }

  // 校准状态机
  val calibrationFsm = CalibrationFsm(config)
  calibrationFsm.io.training := io.training
  calibrationFsm.io.calibrationInterface := io.calibrationInterface

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.calibrationCount := calibrationFsm.io.debug.calibrationCount
  io.debug.errorCount := calibrationFsm.io.debug.errorCount
}

/**
 * 校准配置
 */
case class CalibrationConfig(
    ddrStandard: DdrStandard.E,
    dfiConfig: DfiConfig,
    sdramConfig: SdramConfig,
    features: DfiDdrPhyFeatures
)

/**
 * 校准有限状态机
 */
case class CalibrationFsm(config: CalibrationConfig) extends Area {

  val io = new Bundle {
    val training = slave(DfiTrainingInterface(config.dfiConfig))
    val calibrationInterface = slave(DdrCalibrationInterface(config.sdramConfig))
    val debug = out(CalibrationFsmDebug())
  }

  // 校准状态枚举
  object CalibrationState extends SpinalEnum {
    val IDLE, READ_LEVELING, WRITE_LEVELING, CA_TRAINING, DONE, ERROR = newElement()
  }

  val currentState = Reg(CalibrationState()) init CalibrationState.IDLE
  val calibrationCount = Reg(UInt(32 bits)) init 0
  val errorCount = Reg(UInt(32 bits)) init 0

  // 状态机逻辑
  switch(currentState) {
    is(CalibrationState.IDLE) {
      when(io.training.readTraining.req) {
        currentState := CalibrationState.READ_LEVELING
      } elsewhen(io.training.writeTraining.req) {
        currentState := CalibrationState.WRITE_LEVELING
      } elsewhen(io.training.caTraining.req) {
        currentState := CalibrationState.CA_TRAINING
      }
    }

    is(CalibrationState.READ_LEVELING) {
      // 执行读电平校准
      when(io.training.readTraining.resp.orR) {
        currentState := CalibrationState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(CalibrationState.WRITE_LEVELING) {
      // 执行写电平校准
      when(io.training.writeTraining.resp.orR) {
        currentState := CalibrationState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(CalibrationState.CA_TRAINING) {
      // 执行CA训练
      when(io.training.caTraining.resp.orR) {
        currentState := CalibrationState.DONE
        calibrationCount := calibrationCount + 1
      }
    }

    is(CalibrationState.DONE) {
      currentState := CalibrationState.IDLE
    }

    is(CalibrationState.ERROR) {
      errorCount := errorCount + 1
      currentState := CalibrationState.IDLE
    }
  }

  // 调试信号 - 符合REQ-CS-018：使用直接对象访问
  io.debug.calibrationCount := calibrationCount
  io.debug.errorCount := errorCount
}

/**
 * 调试接口定义
 */
case class CalibrationEngineDebug() extends Bundle {
  val calibrationCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}

case class CalibrationFsmDebug() extends Bundle {
  val calibrationCount = UInt(32 bits)
  val errorCount = UInt(32 bits)
}