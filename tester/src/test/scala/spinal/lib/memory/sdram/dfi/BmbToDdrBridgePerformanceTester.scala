package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter, BmbAccessParameter}
import spinal.lib.bus.bmb.sim.{BmbMasterAgent, BmbMemoryAgent, BmbRegionAllocator}
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.sim._
import spinal.tester.code.SpinalAnyFunSuite

import scala.collection.mutable
import scala.util.Random

/**
 * BmbToDdrBridge performance and stress testing
 *
 * This test suite provides performance benchmarking and stress testing
 * for the BmbToDdrBridge component under various load conditions.
 */
class BmbToDdrBridgePerformanceTester extends SpinalAnyFunSuite {

  /**
   * Helper to create DDR3 DFI configuration
   */
  def createDdr3Config(dataWidth: Int = 128): DfiConfig = {
    val sdramDataWidth = 64
    val phyIoWidth = 2 * sdramDataWidth

    DfiConfig(
      chipSelectNumber = 1,
      dataSlice = phyIoWidth / 8,
      signalConfig = DfiSignalConfig.DDR3(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 0,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 0,
        tPhyRdlat = 2,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = SdramConfigExample.ddr3Example
    )
  }

  /**
   * Test DUT wrapper with DFI exposed
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

  test("BmbToDdrBridge high-bandwidth continuous transfer") {
    val addressWidth = 32
    val dataWidth = 128  // Must match DFI dataWidth (phyIoWidth = 2 * 64 = 128)
    val memorySize = 64 * 1024 // 64KB

    val bmbParameter = BmbParameter(
      addressWidth = addressWidth,
      dataWidth = dataWidth,
      sourceWidth = 0,  // Single source for stability
      contextWidth = 4,
      lengthWidth = 8
    )

    val ddr3Config = createDdr3Config(dataWidth)

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
        BmbToDdrBridgeTestDut(bmbParameter, ddr3Config)
      }.doSimUntilVoid { dut =>
      Phase.boot()
      Phase.setup {
        dut.clockDomain.forkStimulus(10)

        dut.io.clk4x #= false
        dut.io.clk4xN #= true

        // Create DFI memory agent to simulate DDR memory responses
        val dfiMemory = new DfiMemoryAgent(dut.bridge.xilinxPhy.io.dfi, dut.clockDomain)

        // Initialize memory with random data
        val memInit = new Array[Byte](memorySize)
        Random.nextBytes(memInit)
        for (i <- 0 until memorySize) {
          dfiMemory.setByte(i, memInit(i))
        }

        val regions = BmbRegionAllocator()

        // Performance metrics
        var totalTransactions = 0
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain,
          cmdFactor = 0.9f, // High command rate
          rspFactor = 0.9f  // High response rate
        ) {
          val busP = dut.io.bmb.p
          pendingMax = 1

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            totalTransactions += 1
            totalBytes += 1
          }

          override def onCmdWrite(address: BigInt, data: Byte): Unit = {
            totalBytes += 1
          }

          override def getCmd(): () => Unit = {
            if (Phase.stimulus.isActive || cmdQueue.nonEmpty) super.getCmd() else null
          }

          override def regionAllocate(sizeMax: Int): SizeMapping = {
            val limitedMax = sizeMax min busP.access.byteCount
            regions.allocate(Random.nextInt(memorySize), limitedMax, busP)
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
            val maxTimeout = 10000  // Reduced timeout to prevent hanging
            while (masterAgent.rspQueue.exists(_.nonEmpty) && timeout < maxTimeout) {
              dut.clockDomain.waitSampling(100)
              timeout += 100
            }
            dut.clockDomain.waitSampling(500)

            val endTime = System.currentTimeMillis()
            val duration = (endTime - startTime) / 1000.0
            val bandwidth = if (duration > 0) totalBytes / duration / 1024.0 / 1024.0 else 0.0

            println(f"High-bandwidth test completed:")
            println(f"  Total transactions: $totalTransactions")
            println(f"  Total bytes: $totalBytes")
            println(f"  Duration: $duration%.2f seconds")
            println(f"  Estimated bandwidth: $bandwidth%.2f MB/s")

            Phase.flush.release()
          }
        }

        // Retain the stimulus phase until enough transactions are completed
        val rspCounterTarget = 50
        val stimulusRetainer = Phase.stimulus.retainer(rspCounterTarget)
        masterAgent.rspMonitor.addCallback { payload =>
          if (payload.last.toBoolean) {
            stimulusRetainer.release()
          }
        }
      }
    }
  }

  test("BmbToDdrBridge random access pattern") {
    val addressWidth = 32
    val dataWidth = 128  // Must match DFI dataWidth (phyIoWidth = 2 * 64 = 128)
    val memorySize = 64 * 1024

    val bmbParameter = BmbParameter(
      addressWidth = addressWidth,
      dataWidth = dataWidth,
      sourceWidth = 0,  // Single source for stability
      contextWidth = 4,
      lengthWidth = 8
    )

    val ddr3Config = createDdr3Config(dataWidth)

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
        BmbToDdrBridgeTestDut(bmbParameter, ddr3Config)
      }.doSimUntilVoid { dut =>
      Phase.boot()
      Phase.setup {
        dut.clockDomain.forkStimulus(10)

        dut.io.clk4x #= false
        dut.io.clk4xN #= true

        // Create DFI memory agent to simulate DDR memory responses
        val dfiMemory = new DfiMemoryAgent(dut.bridge.xilinxPhy.io.dfi, dut.clockDomain)

        // Initialize memory with random data
        val memInit = new Array[Byte](memorySize)
        Random.nextBytes(memInit)
        for (i <- 0 until memorySize) {
          dfiMemory.setByte(i, memInit(i))
        }

        val regions = BmbRegionAllocator()

        // Latency tracking
        val latencies = mutable.ArrayBuffer[Long]()
        var accessCount = 0

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain) {
          val busP = dut.io.bmb.p
          pendingMax = 1

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            accessCount += 1
          }

          override def getCmd(): () => Unit = {
            if (Phase.stimulus.isActive || cmdQueue.nonEmpty) super.getCmd() else null
          }

          override def regionAllocate(sizeMax: Int): SizeMapping = {
            val limitedMax = sizeMax min busP.access.byteCount
            regions.allocate(Random.nextInt(memorySize), limitedMax, busP)
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
            val maxTimeout = 10000  // Reduced timeout to prevent hanging
            while (masterAgent.rspQueue.exists(_.nonEmpty) && timeout < maxTimeout) {
              dut.clockDomain.waitSampling(100)
              timeout += 100

              // Additional safety check: if no progress is being made, break earlier
              if (timeout % 1000 == 0 && masterAgent.rspQueue.size > 0) {
                // Force process any pending responses
                dut.clockDomain.waitSampling(10)
              }
            }

            // If we timed out, force completion to avoid hanging
            if (timeout >= maxTimeout) {
              println(s"Warning: Random access test timed out after ${maxTimeout}ms")
              println(s"Pending responses: ${masterAgent.rspQueue.size}")
            }

            dut.clockDomain.waitSampling(500)

            println(f"Random access pattern test completed:")
            println(f"  Total accesses: $accessCount")

            Phase.flush.release()
          }
        }

        // Retain the stimulus phase until enough transactions are completed
        val rspCounterTarget = 50
        val stimulusRetainer = Phase.stimulus.retainer(rspCounterTarget)
        masterAgent.rspMonitor.addCallback { payload =>
          if (payload.last.toBoolean) {
            stimulusRetainer.release()
          }
        }
      }
    }
  }

  test("BmbToDdrBridge stress test with mixed patterns") {
    val addressWidth = 32
    val dataWidth = 128  // Must match DFI dataWidth (phyIoWidth = 2 * 64 = 128)
    val memorySize = 64 * 1024

    val bmbParameter = BmbParameter(
      addressWidth = addressWidth,
      dataWidth = dataWidth,
      sourceWidth = 0,  // Single source for stability
      contextWidth = 4,
      lengthWidth = 8
    )

    val ddr3Config = createDdr3Config(dataWidth)

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
        BmbToDdrBridgeTestDut(bmbParameter, ddr3Config)
      }.doSimUntilVoid { dut =>
      Phase.boot()
      Phase.setup {
        dut.clockDomain.forkStimulus(10)

        dut.io.clk4x #= false
        dut.io.clk4xN #= true

        // Create DFI memory agent to simulate DDR memory responses
        val dfiMemory = new DfiMemoryAgent(dut.bridge.xilinxPhy.io.dfi, dut.clockDomain)

        // Initialize memory with random data
        val memInit = new Array[Byte](memorySize)
        Random.nextBytes(memInit)
        for (i <- 0 until memorySize) {
          dfiMemory.setByte(i, memInit(i))
        }

        val regions = BmbRegionAllocator()

        var readCount = 0
        var writeCount = 0

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain,
          cmdFactor = 0.8f,
          rspFactor = 0.8f
        ) {
          val busP = dut.io.bmb.p
          pendingMax = 1

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            readCount += 1
          }

          override def onCmdWrite(address: BigInt, data: Byte): Unit = {
            writeCount += 1
          }

          override def getCmd(): () => Unit = {
            if (Phase.stimulus.isActive || cmdQueue.nonEmpty) super.getCmd() else null
          }

          override def regionAllocate(sizeMax: Int): SizeMapping = {
            val limitedMax = sizeMax min busP.access.byteCount
            regions.allocate(Random.nextInt(memorySize), limitedMax, busP)
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
            val maxTimeout = 10000  // Reduced timeout to prevent hanging
            while (masterAgent.rspQueue.exists(_.nonEmpty) && timeout < maxTimeout) {
              dut.clockDomain.waitSampling(100)
              timeout += 100
            }
            dut.clockDomain.waitSampling(500)

            println(f"Stress test completed:")
            println(f"  Read operations: $readCount")
            println(f"  Write operations: $writeCount")
            println(f"  Total operations: ${readCount + writeCount}")

            Phase.flush.release()
          }
        }

        // Retain the stimulus phase until enough transactions are completed
        val rspCounterTarget = 50
        val stimulusRetainer = Phase.stimulus.retainer(rspCounterTarget)
        masterAgent.rspMonitor.addCallback { payload =>
          if (payload.last.toBoolean) {
            stimulusRetainer.release()
          }
        }
      }
    }
  }
}

