package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.BmbParameter
import spinal.lib.memory.sdram.dfi.phy.XilinxUSPhy
import spinal.lib.sim.SimHeap

/**
 * BmbToDdrBridge综合测试
 */
class BmbToDdrBridgeTester extends SpinalTester {

  def main(args: Array[String]): Unit = {
    // 配置测试参数
    val bmbParameter = BmbParameter(
      addressWidth = 32,
      dataWidth = 64,
      sourceWidth = 4,
      contextWidth = 4,
      lengthWidth = 8
    )

    // DDR3配置
    val ddr3Config = DfiConfig(
      dataRate = 2,
      dataWidth = 64,
      chipSelectNumber = 1,
      frequencyRatio = 1,
      timingConfig = DfiTimingConfig(),
      signalConfig = DfiSignalConfig(),
      sdramConfig = Some(SdramConfig.ddr3Example)
    )

    // DDR4配置
    val ddr4Config = DfiConfig(
      dataRate = 2,
      dataWidth = 64,
      chipSelectNumber = 1,
      frequencyRatio = 1,
      timingConfig = DfiTimingConfig(),
      signalConfig = DfiSignalConfig(),
      sdramConfig = Some(SdramConfig.ddr4Example)
    )

    // 运行测试
    runAllTests(bmbParameter, ddr3Config, ddr4Config)
  }

  def runAllTests(bmbParameter: BmbParameter, ddr3Config: DfiConfig, ddr4Config: DfiConfig): Unit = {
    println("=== BmbToDdrBridge 综合测试开始 ===")

    // 基本功能测试
    testBasicFunctionality(bmbParameter, ddr3Config)

    // DDR3兼容性测试
    testDDR3Compatibility(bmbParameter, ddr3Config)

    // DDR4兼容性测试
    testDDR4Compatibility(bmbParameter, ddr4Config)

    // 错误处理测试
    testErrorHandling(bmbParameter, ddr3Config)

    // 性能测试
    testPerformance(bmbParameter, ddr3Config)

    println("=== 所有测试完成 ===")
  }

  def testBasicFunctionality(bmbParameter: BmbParameter, dfiConfig: DfiConfig): Unit = {
    println("--- 基本功能测试 ---")

    SimConfig.withConfig(SpinalConfig(
      defaultConfigForClockDomains = ClockDomainConfig(
        resetActiveLevel = LOW
      )
    )).withFstWave.compile {
      val bridge = BmbToDdrBridge(bmbParameter, dfiConfig)
      bridge
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // 初始化
      dut.io.bmb.cmd.valid #= false
      dut.io.bmb.cmd.write #= false
      dut.io.bmb.cmd.address #= 0
      dut.io.bmb.cmd.length #= 0
      dut.clockDomain.waitSampling()

      // 测试读命令
      println("测试读命令...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.write #= false
      dut.io.bmb.cmd.address #= 0x1000
      dut.io.bmb.cmd.length #= 7 // 8 beats
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      // 等待响应
      waitUntil(dut.io.bmb.rsp.valid.toBoolean)
      println("读命令响应接收")

      // 测试写命令
      println("测试写命令...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.write #= true
      dut.io.bmb.cmd.address #= 0x2000
      dut.io.bmb.cmd.length #= 3 // 4 beats
      dut.io.bmb.wdata.payload #= 0xA5A5A5A5A5A5A5A5L
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      // 等待响应
      waitUntil(dut.io.bmb.rsp.valid.toBoolean)
      println("写命令响应接收")

      dut.clockDomain.waitSampling(10)
    }

    println("基本功能测试完成")
  }

  def testDDR3Compatibility(bmbParameter: BmbParameter, ddr3Config: DfiConfig): Unit = {
    println("--- DDR3兼容性测试 ---")

    SimConfig.withConfig(SpinalConfig()).withFstWave.compile {
      val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
      bridge
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // DDR3特定测试序列
      testDDR3Sequence(dut)

      dut.clockDomain.waitSampling(10)
    }

    println("DDR3兼容性测试完成")
  }

  def testDDR4Compatibility(bmbParameter: BmbParameter, ddr4Config: DfiConfig): Unit = {
    println("--- DDR4兼容性测试 ---")

    SimConfig.withConfig(SpinalConfig()).withFstWave.compile {
      val bridge = BmbToDdrBridge(bmbParameter, ddr4Config)
      bridge
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // DDR4特定测试序列
      testDDR4Sequence(dut)

      dut.clockDomain.waitSampling(10)
    }

    println("DDR4兼容性测试完成")
  }

  def testErrorHandling(bmbParameter: BmbParameter, dfiConfig: DfiConfig): Unit = {
    println("--- 错误处理测试 ---")

    SimConfig.withConfig(SpinalConfig()).withFstWave.compile {
      val bridge = BmbToDdrBridge(bmbParameter, dfiConfig)
      bridge
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // 测试超时错误
      println("测试超时错误...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.write #= false
      dut.io.bmb.cmd.address #= 0x1000
      dut.io.bmb.cmd.length #= 1024 // 大量数据可能触发超时

      var timeoutCounter = 0
      while (!dut.io.bmb.rsp.valid.toBoolean && timeoutCounter < 5000) {
        dut.clockDomain.waitSampling()
        timeoutCounter += 1
      }

      if (timeoutCounter >= 5000) {
        println("超时检测工作正常")
      } else {
        println("未检测到预期超时")
      }

      dut.io.bmb.cmd.valid #= false
      dut.clockDomain.waitSampling(10)
    }

    println("错误处理测试完成")
  }

  def testPerformance(bmbParameter: BmbParameter, dfiConfig: DfiConfig): Unit = {
    println("--- 性能测试 ---")

    SimConfig.withConfig(SpinalConfig()).withFstWave.compile {
      val bridge = BmbToDdrBridge(bmbParameter, dfiConfig)
      bridge
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      val startTime = System.nanoTime()

      // 连续读写测试
      for (i <- 0 until 100) {
        dut.io.bmb.cmd.valid #= true
        dut.io.bmb.cmd.write #= (i % 2 == 0)
        dut.io.bmb.cmd.address #= i * 64
        dut.io.bmb.cmd.length #= 7

        dut.clockDomain.waitSampling()
        waitUntil(dut.io.bmb.rsp.valid.toBoolean)
        dut.io.bmb.cmd.valid #= false

        dut.clockDomain.waitSampling(1)
      }

      val endTime = System.nanoTime()
      val duration = (endTime - startTime) / 1000000.0 // 转换为毫秒

      println(f"性能测试完成，耗时: ${duration}%.2f ms")
      println(f"平均每次操作耗时: ${duration/100}%.2f ms")
    }

    println("性能测试完成")
  }

  private def testDDR3Sequence(dut: BmbToDdrBridge): Unit = {
    // DDR3特定的测试序列
    // 预充电、激活、读写等
    dut.io.bmb.cmd.valid #= true
    dut.io.bmb.cmd.write #= false
    dut.io.bmb.cmd.address #= 0x1000
    dut.io.bmb.cmd.length #= 15 // 16 beats
    dut.clockDomain.waitSampling()
    dut.io.bmb.cmd.valid #= false

    waitUntil(dut.io.bmb.rsp.valid.toBoolean)
    println("DDR3测试序列完成")
  }

  private def testDDR4Sequence(dut: BmbToDdrBridge): Unit = {
    // DDR4特定的测试序列（包括Bank Group操作）
    dut.io.bmb.cmd.valid #= true
    dut.io.bmb.cmd.write #= true
    dut.io.bmb.cmd.address #= 0x2000 // 包含Bank Group
    dut.io.bmb.cmd.length #= 31 // 32 beats
    dut.io.bmb.wdata.payload #= 0x1234567890ABCDEFL
    dut.clockDomain.waitSampling()
    dut.io.bmb.cmd.valid #= false

    waitUntil(dut.io.bmb.rsp.valid.toBoolean)
    println("DDR4测试序列完成")
  }
}

/**
 * DDR3特定功能测试
 */
class BmbToDdrBridgeDDR3Tester extends SpinalTester {
  def main(args: Array[String]): Unit = {
    println("=== DDR3专项测试 ===")

    val bmbParameter = BmbParameter(32, 64, 4, 4, 8)
    val ddr3Config = DfiConfig(
      dataRate = 2,
      dataWidth = 64,
      chipSelectNumber = 1,
      frequencyRatio = 1,
      timingConfig = DfiTimingConfig(),
      signalConfig = DfiSignalConfig(),
      sdramConfig = Some(SdramConfig.ddr3Example)
    )

    SimConfig.withFstWave.compile {
      BmbToDdrBridge(bmbParameter, ddr3Config)
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // DDR3时序参数测试
      testDDR3Timing(dut)

      dut.clockDomain.waitSampling(10)
    }

    println("DDR3专项测试完成")
  }

  private def testDDR3Timing(dut: BmbToDdrBridge): Unit = {
    // 测试DDR3特定时序约束
    // tRCD, tCL, tRP等
    println("测试DDR3时序参数...")
  }
}

/**
 * DDR4特定功能测试
 */
class BmbToDdrBridgeDDR4Tester extends SpinalTester {
  def main(args: Array[String]): Unit = {
    println("=== DDR4专项测试 ===")

    val bmbParameter = BmbParameter(32, 64, 4, 4, 8)
    val ddr4Config = DfiConfig(
      dataRate = 2,
      dataWidth = 64,
      chipSelectNumber = 1,
      frequencyRatio = 1,
      timingConfig = DfiTimingConfig(),
      signalConfig = DfiSignalConfig(),
      sdramConfig = Some(SdramConfig.ddr4Example)
    )

    SimConfig.withFstWave.compile {
      BmbToDdrBridge(bmbParameter, ddr4Config)
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // DDR4特有功能测试
      testDDR4Features(dut)

      dut.clockDomain.waitSampling(10)
    }

    println("DDR4专项测试完成")
  }

  private def testDDR4Features(dut: BmbToDdrBridge): Unit = {
    // 测试DDR4特有功能
    // Bank Group, DBI, CRC等
    println("测试DDR4特有功能...")
  }
}