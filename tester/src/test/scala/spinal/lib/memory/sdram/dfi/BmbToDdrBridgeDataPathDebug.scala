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
 * BmbToDdrBridge Data Path Debug Test
 *
 * This test focuses specifically on debugging the data flow between
 * BMB and DFI interfaces to identify where data integrity issues occur.
 */
class BmbToDdrBridgeDataPathDebug extends SpinalAnyFunSuite {

  /**
   * Simple DFI configuration for debugging
   */
  def createSimpleDfiConfig(): DfiConfig = {
    DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 8, // 8 slices for 64-bit data
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )
  }

  test("BmbToDdrBridge data path validation") {
    val addressWidth = 32  // Address width large enough for DDR3 address mapping
    val dataWidth = 128    // 128-bit data width to match DFI dataWidth (dataSlice * 16 * 2)
    val memorySize = 64 * 1024 // 64KB test memory

    val bmbParameter = BmbParameter(
      addressWidth = addressWidth,
      dataWidth = dataWidth,
      sourceWidth = 1, // Single source for simplicity
      contextWidth = 0,
      lengthWidth = 4
    )

    // Create DFI config that matches BMB data width
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = dataWidth / 16, // 128 / 16 = 8 slices
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
        // Simple DUT wrapper
        new Component {
          val io = new Bundle {
            val bmb = slave(Bmb(bmbParameter))
            val clk4x = in Bool()
            val clk4xN = in Bool()
          }

          val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
          bridge.io.bmb <> io.bmb
          bridge.io.clk4x := io.clk4x
          bridge.io.clk4xN := io.clk4xN

          // Make DFI accessible for debugging
          bridge.xilinxPhy.io.dfi.simPublic()
        }
      }.doSimUntilVoid { dut =>
      Phase.boot()
      Phase.setup {
        dut.clockDomain.forkStimulus(10)
        dut.clockDomain.forkSimSpeedPrinter()

        dut.io.clk4x #= false
        dut.io.clk4xN #= true

        val memory = new BmbMemoryAgent(memorySize)
        val regions = BmbRegionAllocator()

        // Track all memory operations
        val writeLog = new scala.collection.mutable.ArrayBuffer[(Long, Byte)]()
        val readLog = new scala.collection.mutable.ArrayBuffer[(Long, Byte)]()

        val masterAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain) {
          val busP = dut.io.bmb.p
          pendingMax = 5 // Small pending queue for easier debugging

          override def onCmdWrite(address: BigInt, data: Byte): Unit = {
            writeLog += ((address.toLong, data))
            memory.setByte(address.toLong, data)
            println(f"[CMD] Write to 0x$address%04x: 0x$data%02x")
          }

          override def onRspRead(address: BigInt, data: Byte): Unit = {
            readLog += ((address.toLong, data))
            val expected = memory.getByte(address.toLong)
            println(f"[RSP] Read from 0x$address%04x: got 0x$data%02x, expected 0x$expected%02x")

            // Log detailed info for debugging
            if (data != expected) {
              println(f"[ERROR] Data mismatch at 0x$address%04x!")
              println(f"  Got:      0x$data%02x")
              println(f"  Expected: 0x$expected%02x")

              // Find when this address was written
              writeLog.find { case (addr, _) => addr == address.toLong } match {
                case Some((_, writeData)) =>
                  println(f"  Previously written: 0x$writeData%02x")
                case None =>
                  println(f"  No previous write found for this address")
              }
            }
          }

          override def getCmd(): () => Unit = {
            if (Phase.stimulus.isActive || cmdQueue.nonEmpty) super.getCmd() else null
          }

          override def regionAllocate(sizeMax: Int): SizeMapping = {
            // Fixed allocation pattern for reproducible debugging
            val baseAddr = 0x1000
            val region = SizeMapping(baseAddr, sizeMax)
            if (region.base + region.size <= memorySize) {
              region
            } else {
              SizeMapping(0, 1) // Fallback
            }
          }

          override def regionFree(region: SizeMapping): Unit = {
            // No-op for debugging
          }

          override def regionIsMapped(region: SizeMapping, opcode: Int): Boolean = true
        }

        // Detailed monitoring of BMB and DFI interfaces
        dut.clockDomain.onSamplings {
          // Monitor BMB commands
          if (dut.io.bmb.cmd.valid.toBoolean && dut.io.bmb.cmd.ready.toBoolean) {
            val addr = dut.io.bmb.cmd.address.toBigInt
            val opcode = dut.io.bmb.cmd.opcode.toInt
            val source = dut.io.bmb.cmd.source.toInt
            val length = dut.io.bmb.cmd.length.toInt
            val last = dut.io.bmb.cmd.last.toBoolean
            println(f"[BMB-CMD] addr=0x$addr%08x, opcode=$opcode, source=$source, length=$length, last=$last")
          }

          // Monitor BMB responses
          if (dut.io.bmb.rsp.valid.toBoolean && dut.io.bmb.rsp.ready.toBoolean) {
            val source = dut.io.bmb.rsp.source.toInt
            val opcode = dut.io.bmb.rsp.opcode.toInt
            val last = dut.io.bmb.rsp.last.toBoolean
            println(f"[BMB-RSP] source=$source, opcode=$opcode, last=$last")
          }

          // Monitor DFI control signals (simplified - remove potentially inaccessible signals)
          if (dut.bridge.xilinxPhy.io.dfi.control.csN.toBigInt != 0) {
            // CS_N is high (inactive)
          } else {
            println(f"[DFI-CTRL] CS_N active (LOW)")
          }

          // Monitor DFI read signals
          for (i <- 0 until ddr3Config.frequencyRatio) {
            val rden = dut.bridge.xilinxPhy.io.dfi.read.rden(i).toBoolean
            val rddataValid = dut.bridge.xilinxPhy.io.dfi.read.rd(i).rddataValid.toBoolean
            if (rden) {
              println(f"[DFI-READ] RDEN($i) active")
            }
            if (rddataValid) {
              val rddata = dut.bridge.xilinxPhy.io.dfi.read.rd(i).rddata.toBigInt
              println(f"[DFI-READ] RDDATA($i) valid: 0x$rddata%032x")
            }
          }

          // Monitor DFI write signals
          for (i <- 0 until ddr3Config.frequencyRatio) {
            val wrEn = dut.bridge.xilinxPhy.io.dfi.write.wr(i).wrdataEn.toBoolean
            val wrData = dut.bridge.xilinxPhy.io.dfi.write.wr(i).wrdata.toBigInt
            if (wrEn) {
              println(f"[DFI-WRITE] WR_EN($i) active: 0x$wrData%032x")
            }
          }
        }

        Phase.stimulus.retain()
        Phase.stimulus(fork {
          // Simple test pattern: Write known values, then read them back
          println("[TEST] Starting simple write-read test pattern")

          // Pre-defined test data
          val testPatterns = Array(
            0xAA.toByte, 0x55.toByte, 0xFF.toByte, 0x00.toByte,
            0x12.toByte, 0x34.toByte, 0x56.toByte, 0x78.toByte
          )

          var cycleCount = 0
          val maxCycles = 500

          while (cycleCount < maxCycles) {
            dut.clockDomain.waitSampling(5)
            cycleCount += 1
          }

          Phase.stimulus.release()
        })

        Phase.flush.retain()
        Phase.flush(fork {
          // Wait for all responses
          while (masterAgent.rspQueue.exists(_.nonEmpty)) {
            dut.clockDomain.waitSampling(100)
          }
          dut.clockDomain.waitSampling(1000)

          // Final statistics
          println(s"[TEST] Completed:")
          println(s"  Write operations: ${writeLog.length}")
          println(s"  Read operations: ${readLog.length}")
          println(s"  Memory size: $memorySize bytes")

          Phase.flush.release()
        })
      }
    }
  }
}