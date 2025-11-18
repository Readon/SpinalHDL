package spinal.demo
/*


import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.BmbParameter
import spinal.lib.memory.sdram.dfi.DfiController

/**
 * BMB到DDR桥接示例
 *
 * 演示如何使用新的BMB到DDR桥接功能
 */
object BmbDdrExample {

  def main(args: Array[String]): Unit = {
    // 生成DDR3示例
    generateDdr3Example()

    // 生成DDR4示例
    generateDdr4Example()

    println("示例生成完成!")
  }

  def generateDdr3Example(): Unit = {
    SpinalConfig(
      targetDirectory = "tester/src/test/scala/spinal/demo/generated",
      defaultConfigForClockDomains = ClockDomainConfig(
        resetActiveLevel = LOW
      )
    ).generate {
      new Ddr3Example()
    }

    println("DDR3示例已生成到: tester/src/test/scala/spinal/demo/generated/Ddr3Example.v")
  }

  def generateDdr4Example(): Unit = {
    SpinalConfig(
      targetDirectory = "tester/src/test/scala/spinal/demo/generated",
      defaultConfigForClockDomains = ClockDomainConfig(
        resetActiveLevel = LOW
      )
    ).generate {
      new Ddr4Example()
    }

    println("DDR4示例已生成到: tester/src/test/scala/spinal/demo/generated/Ddr4Example.v")
  }
}

/**
 * DDR3示例设计
 */
class Ddr3Example extends Component {

  // BMB总线参数
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 64,
    sourceWidth = 4,
    contextWidth = 4,
    lengthWidth = 8
  )

  val io = new Bundle {
    // 系统时钟和复位
    val clk = in Bool()
    val reset_n = in Bool()

    // BMB总线接口
    val bmb = slave(Bmb(bmbParameter))

    // DDR3物理接口
    val ddr3 = master(SdramIO(
      DfiConfig(
        dataRate = 2,
        dataWidth = 64,
        chipSelectNumber = 1,
        frequencyRatio = 1,
        timingConfig = DfiTimingConfig(),
        signalConfig = DfiSignalConfig(),
        sdramConfig = Some(SdramConfig.ddr3Example)
      )
    ))

    // 状态和调试接口
    val init_done = out Bool()
    val error = out Bool()
  }

  // 设置时钟域
  val clockDomain = ClockDomain(
    clock = io.clk,
    reset = io.reset_n,
    config = ClockDomainConfig(
      resetActiveLevel = LOW
    )
  )

  // 在该时钟域中实例化组件
  val area = new ClockingArea(clockDomain) {
    // 创建DDR3控制器
    val ddr3Controller = DfiController.ddr3(bmbParameter)

    // 连接BMB总线
    ddr3Controller.io.bmb <> io.bmb

    // 连接DDR3物理接口
    ddr3Controller.io.ddr <> io.ddr3

    // 简单的状态指示
    val initCounter = Reg(UInt(16 bits)) init(0)
    when(!ddr3Controller.io.bmb.rsp.valid) {
      initCounter := initCounter + 1
    }

    io.init_done := initCounter >= 1000
    io.error := ddr3Controller.io.bmb.rsp.valid && ddr3Controller.io.bmb.rsp.error
  }
}

/**
 * DDR4示例设计
 */
class Ddr4Example extends Component {

  // BMB总线参数
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 64,
    sourceWidth = 4,
    contextWidth = 4,
    lengthWidth = 8
  )

  val io = new Bundle {
    // 系统时钟和复位
    val clk = in Bool()
    val reset_n = in Bool()

    // BMB总线接口
    val bmb = slave(Bmb(bmbParameter))

    // DDR4物理接口
    val ddr4 = master(SdramIO(
      DfiConfig(
        dataRate = 2,
        dataWidth = 64,
        chipSelectNumber = 1,
        frequencyRatio = 1,
        timingConfig = DfiTimingConfig(),
        signalConfig = DfiSignalConfig(),
        sdramConfig = Some(SdramConfig.ddr4Example)
      )
    ))

    // 状态和调试接口
    val init_done = out Bool()
    val error = out Bool()
    val ddr4_ready = out Bool()
  }

  // 设置时钟域
  val clockDomain = ClockDomain(
    clock = io.clk,
    reset = io.reset_n,
    config = ClockDomainConfig(
      resetActiveLevel = LOW
    )
  )

  // 在该时钟域中实例化组件
  val area = new ClockingArea(clockDomain) {
    // 创建DDR4控制器
    val ddr4Controller = DfiController.ddr4(bmbParameter)

    // 连接BMB总线
    ddr4Controller.io.bmb <> io.bmb

    // 连接DDR4物理接口
    ddr4Controller.io.ddr <> io.ddr4

    // DDR4特定状态
    val initCounter = Reg(UInt(16 bits)) init(0)
    val bankGroupReady = Reg(Bool()) init(False)

    when(!ddr4Controller.io.bmb.rsp.valid) {
      initCounter := initCounter + 1
      when(initCounter >= 2000) {
        bankGroupReady := True
      }
    }

    io.init_done := initCounter >= 2000
    io.error := ddr4Controller.io.bmb.rsp.valid && ddr4Controller.io.bmb.rsp.error
    io.ddr4_ready := bankGroupReady
  }
}

/**
 * 高性能DDR4示例（支持多通道）
 */
class HighPerformanceDdr4Example extends Component {

  // BMB总线参数（更宽的数据总线）
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 128,    // 128位数据总线
    sourceWidth = 8,
    contextWidth = 8,
    lengthWidth = 12
  )

  val io = new Bundle {
    // 系统时钟和复位
    val clk = in Bool()
    val reset_n = in Bool()

    // BMB总线接口
    val bmb = slave(Bmb(bmbParameter))

    // DDR4物理接口（双rank）
    val ddr4 = master(SdramIO(
      DfiConfig(
        dataRate = 4,        // 4:1频率比
        dataWidth = 128,
        chipSelectNumber = 2, // 双rank
        frequencyRatio = 4,
        timingConfig = DfiTimingConfig(),
        signalConfig = DfiSignalConfig(),
        sdramConfig = Some(SdramConfig(
          generation = SdramGeneration.DDR4,
          timing = SdramTiming.DDR4.highPerformance,
          layout = SdramLayout(
            addressWidth = 18,
            bankWidth = 2,
            bankGroupWidth = Some(2),
            columnWidth = 10,
            dataWidth = 128,
            chipSelectNumber = 2
          )
        ))
      )
    ))

    // 高级状态接口
    val init_done = out Bool()
    val error = out Bool()
    val performance_mode = out Bool()
    val bank_groups_active = out UInt(2 bits)
  }

  // 设置时钟域
  val clockDomain = ClockDomain(
    clock = io.clk,
    reset = io.reset_n,
    config = ClockDomainConfig(
      resetActiveLevel = LOW
    )
  )

  // 在该时钟域中实例化组件
  val area = new ClockingArea(clockDomain) {
    // 自定义DDR4控制器配置
    val customDdr4Config = DfiConfig(
      dataRate = 4,
      dataWidth = 128,
      chipSelectNumber = 2,
      frequencyRatio = 4,
      timingConfig = DfiTimingConfig(),
      signalConfig = DfiSignalConfig(),
      sdramConfig = Some(SdramConfig(
        generation = SdramGeneration.DDR4,
        timing = SdramTiming.DDR4.highPerformance,
        layout = SdramLayout(
          addressWidth = 18,
          bankWidth = 2,
          bankGroupWidth = Some(2),
          columnWidth = 10,
          dataWidth = 128,
          chipSelectNumber = 2
        )
      ))
    )

    // 创建高性能DDR4控制器
    val ddr4Controller = DfiController(bmbParameter, customDdr4Config)

    // 连接BMB总线
    ddr4Controller.io.bmb <> io.bmb

    // 连接DDR4物理接口
    ddr4Controller.io.ddr <> io.ddr4

    // 高级状态管理
    val initCounter = Reg(UInt(16 bits)) init(0)
    val performanceTimer = Reg(UInt(32 bits)) init(0)
    val bankGroupUsage = Reg(UInt(2 bits)) init(0)

    when(!ddr4Controller.io.bmb.rsp.valid) {
      initCounter := initCounter + 1
    } otherwise {
      performanceTimer := performanceTimer + 1
      // 模拟Bank Group使用情况
      bankGroupUsage := (bankGroupUsage + 1) & 3
    }

    io.init_done := initCounter >= 3000
    io.error := ddr4Controller.io.bmb.rsp.valid && ddr4Controller.io.bmb.rsp.error
    io.performance_mode := performanceTimer >= 10000
    io.bank_groups_active := bankGroupUsage
  }
}

*/

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}

/**
  * BMB to DDR example (stub).
  *
  * The original example depended on an older DFI/DDR controller API and no
  * longer compiles after the enhance-bmb-ddr-bridge refactor. To keep the
  * tester project compiling while the new controller/PHY integration is
  * finalized, this file provides a minimal BMB-only stub that can be
  * elaborated to Verilog.
  */
object BmbDdrExample {
  def main(args: Array[String]): Unit = {
    SpinalConfig(
      targetDirectory = "tester/src/test/scala/spinal/demo/generated",
      defaultConfigForClockDomains = ClockDomainConfig(
        resetActiveLevel = LOW
      )
    ).generate(new MinimalBmbExample)
  }
}

class MinimalBmbExample extends Component {
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 64,
    sourceWidth = 4,
    contextWidth = 4,
    lengthWidth = 8
  )

  val io = new Bundle {
    val clk   = in Bool()
    val reset = in Bool()
    val bmb   = slave(Bmb(bmbParameter))
  }

  val cd = ClockDomain(
    clock = io.clk,
    reset = io.reset,
    config = ClockDomainConfig(resetActiveLevel = LOW)
  )

  val area = new ClockingArea(cd) {
    // TODO: hook this up to the new DFI/DDR controller once the API is stable.
    // For now this is just a structural stub to keep tests compiling.
  }
}
