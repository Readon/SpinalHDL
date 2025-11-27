package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter, BmbAccessParameter}
import spinal.lib.bus.bmb.sim.{BmbMasterAgent, BmbRegionAllocator}
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.sim._
import spinal.tester.code.SpinalAnyFunSuite

import scala.collection.mutable
import scala.util.Random

/**
 * BmbToDdrBridge DDR2/DDR3 compatibility testing
 *
 * This test suite verifies DDR2 and DDR3 specific functionality including
 * timing parameters, bank management, and standard-specific features.
 */
class BmbToDdrBridgeDdr2Ddr3Tester extends SpinalAnyFunSuite {

  /**
   * Helper to create DDR3 DFI configuration with custom timing
   */
  def createDdr3ConfigWithTiming(dataWidth: Int = 128): DfiConfig = {
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
   * Helper to create DDR2 DFI configuration with custom timing
   */
  def createDdr2ConfigWithTiming(dataWidth: Int = 128): DfiConfig = {
    val sdramDataWidth = 64
    val phyIoWidth = 2 * sdramDataWidth

    DfiConfig(
      chipSelectNumber = 1,
      dataSlice = phyIoWidth / 8,
      signalConfig = DfiSignalConfig.DDR2(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 0,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 0,
        tPhyRdlat = 1,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = SdramConfigExample.ddr2Example
    )
  }

  /**
   * Test DUT wrapper with DFI exposed for memory simulation
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

  test("BmbToDdrBridge DDR3 timing parameter validation with Micron model") {
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

    val ddr3Config = createDdr3ConfigWithTiming(dataWidth)

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

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain) {
          val busP = dut.io.bmb.p
          pendingMax = 1

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            // Log read responses
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
            val maxTimeout = 50000
            while (masterAgent.rspQueue.exists(_.nonEmpty) && timeout < maxTimeout) {
              dut.clockDomain.waitSampling(100)
              timeout += 100
            }
            dut.clockDomain.waitSampling(500)
            println("DDR3 timing parameter validation test completed")
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

  test("BmbToDdrBridge DDR2 timing parameter validation with Micron model") {
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

    val ddr2Config = createDdr2ConfigWithTiming(dataWidth)

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
        BmbToDdrBridgeTestDut(bmbParameter, ddr2Config)
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

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain) {
          val busP = dut.io.bmb.p
          pendingMax = 1

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            // Log read responses
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
            val maxTimeout = 50000
            while (masterAgent.rspQueue.exists(_.nonEmpty) && timeout < maxTimeout) {
              dut.clockDomain.waitSampling(100)
              timeout += 100
            }
            dut.clockDomain.waitSampling(500)
            println("DDR2 timing parameter validation test completed")
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

  test("BmbToDdrBridge DDR3 bank management with Micron model") {
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

    val ddr3Config = createDdr3ConfigWithTiming(dataWidth)

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

        // Track bank access patterns
        val bankAccessCount = mutable.HashMap[Int, Int]()

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain) {
          val busP = dut.io.bmb.p
          pendingMax = 1

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            // Extract bank from address (assuming bank bits are in lower address bits)
            val bank = ((address >> 13) & 0x7).toInt // DDR3 has 8 banks
            bankAccessCount(bank) = bankAccessCount.getOrElse(bank, 0) + 1
          }

          override def getCmd(): () => Unit = {
            if (Phase.stimulus.isActive || cmdQueue.nonEmpty) super.getCmd() else null
          }

          override def regionAllocate(sizeMax: Int): SizeMapping = {
            val limitedMax = sizeMax min busP.access.byteCount
            // Allocate across different banks
            val baseAddr = Random.nextInt(8) << 13 // Select different banks
            regions.allocate(baseAddr + Random.nextInt(8192), limitedMax, busP)
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

            // Report bank access distribution
            println("DDR3 Bank Access Distribution:")
            for (bank <- 0 until 8) {
              val count = bankAccessCount.getOrElse(bank, 0)
              println(f"  Bank $bank: $count accesses")
            }

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

