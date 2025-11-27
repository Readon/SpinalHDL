package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.bus.bmb.sim.{BmbMasterAgent, BmbMemoryAgent, BmbRegionAllocator}
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.sim._
import spinal.tester.code.SpinalAnyFunSuite

import scala.util.Random

/**
 * BmbToDdrBridge comprehensive regression test suite
 *
 * Automated test execution for continuous integration and test coverage measurement.
 * Runs all predefined test scenarios and generates a comprehensive report.
 */
class BmbToDdrBridgeRegressionTester extends SpinalAnyFunSuite {

  import BmbToDdrBridgeTestConfig._

  /**
   * Test DUT wrapper with DFI interface exposed for simulation
   */
  case class BmbToDdrBridgeTestDut(
    bmbParameter: BmbParameter,
    dfiConfig: DfiConfig
  ) extends Component {
    val io = new Bundle {
      val bmb = slave(Bmb(bmbParameter))
      val clk4x = in Bool()
      val clk4xN = in Bool()
    }

    val bridge = BmbToDdrBridge(bmbParameter, dfiConfig)
    bridge.io.bmb <> io.bmb
    bridge.io.clk4x := io.clk4x
    bridge.io.clk4xN := io.clk4xN

    // Expose DFI interface for memory simulation
    bridge.xilinxPhy.io.dfi.simPublic()
  }

  /**
   * Run a single test scenario
   */
  def runScenario(scenario: TestScenario): TestResult = {
    var transactionsCompleted = 0
    var errorsDetected = 0
    val startTime = System.currentTimeMillis()

    try {
      SimConfig
        .withVerilator
        .addSimulatorFlag("-Wno-PINMISSING")
        .addRtl("tester/src/test/resources/OSERDESE3.v")
        .addRtl("tester/src/test/resources/ISERDESE3_stub.v")
        .addRtl("tester/src/test/resources/ODELAYE3.v")
        .addRtl("tester/src/test/resources/IDELAYE3.v")
        .addRtl("tester/src/test/resources/OBUFDS.v")
        .addRtl("tester/src/test/resources/IOBUF.v")
        .compile {
          BmbToDdrBridgeTestDut(scenario.toBmbParameter, scenario.toDfiConfig)
        }.doSimUntilVoid { dut =>
        Phase.boot()
        Phase.setup {
          dut.clockDomain.forkStimulus(10)

          dut.io.clk4x #= false
          dut.io.clk4xN #= true

          // Create DFI memory agent to simulate DDR memory responses
          val dfiMemory = new DfiMemoryAgent(dut.bridge.xilinxPhy.io.dfi, dut.clockDomain)

          // Initialize memory with random data
          val memInit = new Array[Byte](scenario.memorySize)
          Random.nextBytes(memInit)
          for (i <- 0 until scenario.memorySize) {
            dfiMemory.setByte(i, memInit(i))
          }

          val regions = BmbRegionAllocator()

          val masterAgent = new BmbMasterAgent(
            dut.io.bmb,
            dut.clockDomain,
            scenario.cmdFactor,
            scenario.rspFactor
          ) {
            val busP = dut.io.bmb.p
            pendingMax = 1  // Limit pending transactions for stability

            override def onRspRead(address: BigInt, data: Byte): Unit = {
              transactionsCompleted += 1
            }

            override def getCmd(): () => Unit = {
              if (Phase.stimulus.isActive || cmdQueue.nonEmpty) super.getCmd() else null
            }

            override def regionAllocate(sizeMax: Int): SizeMapping = {
              val limitedMax = sizeMax min busP.access.byteCount
              regions.allocate(Random.nextInt(scenario.memorySize), limitedMax, busP)
            }

            override def regionFree(region: SizeMapping): Unit = {
              regions.free(region)
            }

            override def regionIsMapped(region: SizeMapping, opcode: Int): Boolean = true
          }

          // Retain the flush phase until all BMB responses are received
          Phase.flush.retain()
          Phase.flush {
            fork {
              var timeout = 0
              val maxTimeout = 50000
              while (masterAgent.rspQueue.exists(_.nonEmpty) && timeout < maxTimeout) {
                dut.clockDomain.waitSampling(100)
                timeout += 100
              }
              dut.clockDomain.waitSampling(500)
              Phase.flush.release()
            }
          }

          // Retain the stimulus phase until enough transactions are completed
          val rspCounterTarget = scenario.transactionCount min 50
          val stimulusRetainer = Phase.stimulus.retainer(rspCounterTarget)
          masterAgent.rspMonitor.addCallback { payload =>
            if (payload.last.toBoolean) {
              stimulusRetainer.release()
            }
          }
        }
      }

      val endTime = System.currentTimeMillis()
      val duration = (endTime - startTime) / 1000.0

      TestResult(
        scenario = scenario,
        passed = true,
        transactionsCompleted = transactionsCompleted,
        errorsDetected = errorsDetected,
        duration = duration
      )
    } catch {
      case e: Exception =>
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime) / 1000.0

        println(s"Test scenario '${scenario.name}' failed with exception: ${e.getMessage}")
        e.printStackTrace()

        TestResult(
          scenario = scenario,
          passed = false,
          transactionsCompleted = transactionsCompleted,
          errorsDetected = errorsDetected,
          duration = duration,
          additionalInfo = Map("error" -> e.getMessage)
        )
    }
  }

  test("BmbToDdrBridge regression test suite") {
    val collector = new TestResultCollector()

    // println("\n" + "=" * 80)
    // println("BmbToDdrBridge Comprehensive Regression Test Suite")
    // println("=" * 80)

    // Run all predefined scenarios
    Scenarios.all.foreach { scenario =>
      println(s"\nRunning scenario: ${scenario.name}")
      val result = runScenario(scenario)
      // result.report()
      collector.add(result)
    }

    // Generate final report
    collector.reportAll()

    // Assert all tests passed
    assert(collector.allPassed, "Some regression tests failed")
  }
}
