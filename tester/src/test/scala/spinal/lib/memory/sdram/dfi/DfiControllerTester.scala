package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.BmbParameter
import spinal.lib.memory.sdram.dfi.phy.XilinxUSPhy

/**
 * DfiController测试
 *
 * 测试新实现的DfiController与BMB总线接口的集成功能
 * 基于XilinxUSPhy物理层实现
 */
class DfiControllerTester extends SpinalTester {

  def main(args: Array[String]): Unit = {
    println("=== DfiController 测试开始 ===")

    val bmbParameter = BmbParameter(
      addressWidth = 32,
      dataWidth = 64,
      sourceWidth = 4,
      contextWidth = 4,
      lengthWidth = 8
    )

    // 测试DDR3控制器
    testDdr3Controller(bmbParameter)

    // 测试DDR4控制器
    testDdr4Controller(bmbParameter)

    println("=== DfiController 测试完成 ===")
  }

  def testDdr3Controller(bmbParameter: BmbParameter): Unit = {
    println("--- DDR3 DfiController测试 ---")

    SimConfig.withConfig(SpinalConfig()).withFstWave.compile {
      DfiController.ddr3(bmbParameter)
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // 初始化
      dut.io.bmb.cmd.valid #= false
      dut.clockDomain.waitSampling()

      // DDR3读操作测试
      println("测试DDR3读操作...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.write #= false
      dut.io.bmb.cmd.address #= 0x1000
      dut.io.bmb.cmd.length #= 7
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      // 等待响应
      waitUntil(dut.io.bmb.rsp.valid.toBoolean)
      println("DDR3读操作完成")

      // DDR3写操作测试
      println("测试DDR3写操作...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.write #= true
      dut.io.bmb.cmd.address #= 0x2000
      dut.io.bmb.cmd.length #= 3
      dut.io.bmb.wdata.payload #= 0xDEADBEEFDEADBEEFL
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      waitUntil(dut.io.bmb.rsp.valid.toBoolean)
      println("DDR3写操作完成")

      dut.clockDomain.waitSampling(10)
    }

    println("DDR3 DfiController测试完成")
  }

  def testDdr4Controller(bmbParameter: BmbParameter): Unit = {
    println("--- DDR4 DfiController测试 ---")

    SimConfig.withConfig(SpinalConfig()).withFstWave.compile {
      DfiController.ddr4(bmbParameter)
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      // 初始化
      dut.io.bmb.cmd.valid #= false
      dut.clockDomain.waitSampling()

      // DDR4读写操作测试
      println("测试DDR4读写操作...")
      dut.io.bmb.cmd.valid #= true
      dut.io.bmb.cmd.write #= false
      dut.io.bmb.cmd.address #= 0x3000
      dut.io.bmb.cmd.length #= 15
      dut.clockDomain.waitSampling()
      dut.io.bmb.cmd.valid #= false

      waitUntil(dut.io.bmb.rsp.valid.toBoolean)
      println("DDR4操作完成")

      dut.clockDomain.waitSampling(10)
    }

    println("DDR4 DfiController测试完成")
  }
}

/**
 * DfiController性能测试
 */
class DfiControllerPerformanceTester extends SpinalTester {
  def main(args: Array[String]): Unit = {
    println("=== DfiController 性能测试 ===")

    val bmbParameter = BmbParameter(32, 64, 4, 4, 8)

    SimConfig.withConfig(SpinalConfig()).compile {
      DfiController.ddr3(bmbParameter)
    }.doSim { dut =>
      dut.clockDomain.forkStimulus(10 ns)

      val startTime = System.nanoTime()

      // 连续操作测试
      for (i <- 0 until 50) {
        dut.io.bmb.cmd.valid #= true
        dut.io.bmb.cmd.write #= (i % 2 == 0)
        dut.io.bmb.cmd.address #= i * 256
        dut.io.bmb.cmd.length #= 7

        dut.clockDomain.waitSampling()
        waitUntil(dut.io.bmb.rsp.valid.toBoolean)
        dut.io.bmb.cmd.valid #= false

        dut.clockDomain.waitSampling(1)
      }

      val endTime = System.nanoTime()
      val duration = (endTime - startTime) / 1000000.0

      println(f"性能测试完成，耗时: ${duration}%.2f ms")
    }

    println("DfiController 性能测试完成")
  }
}
