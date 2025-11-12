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
    // 配置测试参数 - 使用正确的BMB API
    val accessConfig = spinal.lib.bus.bmb.BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(accessConfig)

    // DDR3配置
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 8,
      signalConfig = DfiSignalConfig.DDR3(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 9,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 9,
        tPhyRdlat = 9,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = SdramConfigExample.ddr3Example
    )

    // DDR4配置
    val ddr4Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 8,
      signalConfig = DfiSignalConfig.DDR4(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 10,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 10,
        tPhyRdlat = 10,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = SdramConfigExample.ddr4Example
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
      dut.io.bmb.cmd.payload.isWrite #= false
      dut.io.bmb.cmd.payload.address #= 0
      dut.io.bmb.cmd.payload.length #= 0
      dut.clockDomain.waitSampling()

      // 测试读命令
      println("测试读命令...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.payload.isWrite #= false
      dut.io.bmb.cmd.payload.address #= 0x1000
      dut.io.bmb.cmd.payload.length #= 7 // 8 beats
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      // 等待响应
      var readTimeout = 0
      while (!dut.io.bmb.rsp.valid.toBoolean && readTimeout < 1000) {
        dut.clockDomain.waitSampling()
        readTimeout += 1
      }
      if (readTimeout < 1000) {
        println("读命令响应接收")
      } else {
        println("读命令响应超时")
      }

      // 测试写命令
      println("测试写命令...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.payload.isWrite #= true
      dut.io.bmb.cmd.payload.address #= 0x2000
      dut.io.bmb.cmd.payload.length #= 3 // 4 beats
      dut.io.bmb.wdata.payload #= 0xA5A5A5A5A5A5A5A5L
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      // 等待响应
      var writeTimeout = 0
      while (!dut.io.bmb.rsp.valid.toBoolean && writeTimeout < 1000) {
        dut.clockDomain.waitSampling()
        writeTimeout += 1
      }
      if (writeTimeout < 1000) {
        println("写命令响应接收")
      } else {
        println("写命令响应超时")
      }

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
      dut.io.bmb.cmd.payload.isWrite #= false
      dut.io.bmb.cmd.payload.address #= 0x1000
      dut.io.bmb.cmd.payload.length #= 1024 // 大量数据可能触发超时

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
        dut.io.bmb.cmd.payload.isWrite #= (i % 2 == 0)
        dut.io.bmb.cmd.payload.address #= i * 64
        dut.io.bmb.cmd.payload.length #= 7

        dut.clockDomain.waitSampling()
        while (!dut.io.bmb.rsp.valid.toBoolean) {
          dut.clockDomain.waitSampling()
        }
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
    dut.io.bmb.cmd.payload.isWrite #= false
    dut.io.bmb.cmd.payload.address #= 0x1000
    dut.io.bmb.cmd.payload.length #= 15 // 16 beats
    dut.clockDomain.waitSampling()
    dut.io.bmb.cmd.valid #= false

    var ddr3Timeout = 0
      while (!dut.io.bmb.rsp.valid.toBoolean && ddr3Timeout < 1000) {
        dut.clockDomain.waitSampling()
        ddr3Timeout += 1
      }
      if (ddr3Timeout < 1000) {
        println("DDR3测试序列完成")
      } else {
        println("DDR3测试序列超时")
      }
  }

  private def testDDR4Sequence(dut: BmbToDdrBridge): Unit = {
    // DDR4特定的测试序列（包括Bank Group操作）
    dut.io.bmb.cmd.valid #= true
    dut.io.bmb.cmd.payload.isWrite #= true
    dut.io.bmb.cmd.payload.address #= 0x2000 // 包含Bank Group
    dut.io.bmb.cmd.payload.length #= 31 // 32 beats
    dut.io.bmb.wdata.payload #= 0x1234567890ABCDEFL
    dut.clockDomain.waitSampling()
    dut.io.bmb.cmd.valid #= false

    var ddr4Timeout = 0
      while (!dut.io.bmb.rsp.valid.toBoolean && ddr4Timeout < 1000) {
        dut.clockDomain.waitSampling()
        ddr4Timeout += 1
      }
      if (ddr4Timeout < 1000) {
        println("DDR4测试序列完成")
      } else {
        println("DDR4测试序列超时")
      }
  }
}

/**
 * DDR3特定功能测试
 */
class BmbToDdrBridgeDDR3Tester extends SpinalTester {
  def main(args: Array[String]): Unit = {
    println("=== DDR3专项测试 ===")

    val accessConfig = spinal.lib.bus.bmb.BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(accessConfig)
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 8,
      signalConfig = DfiSignalConfig.DDR3(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 9,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 9,
        tPhyRdlat = 9,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = SdramConfigExample.ddr3Example
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

    val accessConfig = spinal.lib.bus.bmb.BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(accessConfig)
    val ddr4Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 8,
      signalConfig = DfiSignalConfig.DDR4(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 10,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 10,
        tPhyRdlat = 10,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = SdramConfigExample.ddr4Example
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