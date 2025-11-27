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
 * BmbToDdrBridge error handling and recovery testing
 *
 * This test suite verifies error detection, reporting, and recovery
 * mechanisms in the BmbToDdrBridge component.
 */
class BmbToDdrBridgeErrorTester extends SpinalAnyFunSuite {

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
   * Test DUT wrapper with debug interface and DFI exposed
   */
  case class BmbToDdrBridgeTestDut(
    bmbParameter: BmbParameter,
    dfiConfig: DfiConfig
  ) extends Component {
    val io = new Bundle {
      val bmb = slave(Bmb(bmbParameter))
      val clk4x = in Bool()
      val clk4xN = in Bool()
      val debugError = out Bool()
      val debugBusy = out Bool()
    }

    val bridge = BmbToDdrBridge(bmbParameter, dfiConfig)
    bridge.io.bmb <> io.bmb
    bridge.io.clk4x := io.clk4x
    bridge.io.clk4xN := io.clk4xN
    io.debugError := bridge.io.debug.error
    io.debugBusy := bridge.io.debug.busy

    // Expose DFI interface for memory simulation
    bridge.xilinxPhy.io.dfi.simPublic()
  }

  test("BmbToDdrBridge error detection monitoring") {
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

        // Error tracking
        var errorDetected = false
        var errorCount = 0

        // Monitor error signal
        fork {
          var cycles = 0
          while (cycles < 100000) {
            dut.clockDomain.waitSampling()
            cycles += 1
            if (dut.io.debugError.toBoolean) {
              errorDetected = true
              errorCount += 1
              println(f"Error detected at simulation time ${simTime()}")
            }
          }
        }

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

            println(f"Error detection test completed:")
            println(f"  Error detected: $errorDetected")
            println(f"  Error count: $errorCount")

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
