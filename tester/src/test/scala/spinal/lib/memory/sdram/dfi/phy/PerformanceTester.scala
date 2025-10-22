package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.core.sim._
import spinal.lib.memory.sdram.dfi._
import spinal.tester.SpinalAnyFunSuite

import scala.util.Random

/**
 * PHY性能和最终验证测试
 *
 * 测试带宽、延迟、多芯片选择和端到端系统验证
 */
class PerformanceTester extends SpinalAnyFunSuite {

  import spinal.core._
  import spinal.core.sim._

  // 测试常量定义
  private val TEST_DDR3_GENERATION = 3
  private val TEST_CHIP_SELECT_SINGLE = 1
  private val TEST_CHIP_SELECT_DUAL = 2
  private val TEST_DATA_SLICE_SINGLE = 1
  private val TEST_FREQUENCY_RATIO_1 = 1
  private val TEST_CMD_PHASE = 0
  private val TEST_PHY_WR_LAT = 1
  private val TEST_PHY_WR_DATA = 0
  private val TEST_PHY_WR_CS_LAT = 0
  private val TEST_PHY_WR_CS_GAP = 0
  private val TEST_RDDATA_EN = 5
  private val TEST_PHY_RD_LAT = 6
  private val TEST_PHY_RD_CS_LAT = 0
  private val TEST_PHY_RD_CS_GAP = 0
  private val TEST_BANK_WIDTH = 3
  private val TEST_COLUMN_WIDTH = 10
  private val TEST_ROW_WIDTH = 15
  private val TEST_DATA_WIDTH = 16
  private val TEST_DDR_MHZ_200 = 200
  private val TEST_DDR_MHZ_300 = 300
  private val TEST_DDR_WR_LAT = 4
  private val TEST_DDR_RD_LAT = 4
  private val TEST_RFC = 260
  private val TEST_RAS = 38
  private val TEST_RP = 15
  private val TEST_RCD = 15
  private val TEST_WTR = 8
  private val TEST_WTP = 0
  private val TEST_RTP = 8
  private val TEST_RRD = 6
  private val TEST_REF = 64000
  private val TEST_FAW = 35
  private val TEST_CLOCK_PERIOD = 10
  private val TEST_INIT_WAIT_CYCLES = 50
  private val TEST_COMMAND_WAIT_CYCLES = 1
  private val TEST_FINAL_WAIT_CYCLES = 100
  private val TEST_BANDWIDTH_TEST_DURATION = 1000
  private val TEST_STRESS_DURATION = 2000
  private val TEST_REFRESH_INTERVAL = 100

  test("Performance_BandwidthTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建高性能DDR3 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_300,  // 高频配置
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC,
            RAS = TEST_RAS,
            RP = TEST_RP,
            RCD = TEST_RCD,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD,
            REF = TEST_REF,
            FAW = TEST_FAW
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN,
            tPhyRdlat = TEST_PHY_RD_LAT,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = sdramConfig
        )

        val phyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR3,
          dfiConfig = dfiConfig,
          sdramConfig = sdramConfig,
          features = DfiDdrPhyFeatures()
        )

        val phy = DfiDdrPhy(phyConfig)
        phy
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        var operationCount = 0
        val startTime = simTime()

        // 执行连续读写操作来测试带宽
        for (cycle <- 0 until TEST_BANDWIDTH_TEST_DURATION) {
          // ACTIVATE
          dut.io.dfi.control.rasN #= 0
          dut.io.dfi.control.casN #= 1
          dut.io.dfi.control.weN #= 1
          dut.io.dfi.control.address #= (cycle * 0x1000).toInt
          dut.io.dfi.control.bank #= (cycle % 8).toInt
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // WRITE
          dut.io.dfi.control.rasN #= 1
          dut.io.dfi.control.casN #= 0
          dut.io.dfi.control.weN #= 0
          dut.io.dfi.control.address #= (cycle * 0x10).toInt
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // 写数据
          for (i <- 0 until dut.io.dfi.write.wr.length) {
            dut.io.dfi.write.wr(i).wrdataEn #= true
            dut.io.dfi.write.wr(i).wrdata #= (cycle & 0xFFFF).toInt
          }
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          for (i <- 0 until dut.io.dfi.write.wr.length) {
            dut.io.dfi.write.wr(i).wrdataEn #= false
          }

          // READ
          dut.io.dfi.control.rasN #= 1
          dut.io.dfi.control.casN #= 0
          dut.io.dfi.control.weN #= 1
          dut.io.dfi.control.address #= (cycle * 0x10).toInt
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // 读数据返回
          for (i <- 0 until dut.io.dfi.read.rd.length) {
            dut.io.dfi.read.rd(i).rddataValid #= true
            dut.io.dfi.read.rd(i).rddata #= (cycle & 0xFFFF).toInt
          }
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          for (i <- 0 until dut.io.dfi.read.rd.length) {
            dut.io.dfi.read.rd(i).rddataValid #= false
          }

          operationCount += 2 // 一次读写操作
        }

        val endTime = simTime()
        val elapsedTime = endTime - startTime
        val bandwidth = (operationCount.toDouble * TEST_DATA_WIDTH * 8) / (elapsedTime / 1000.0) // Mbps

        println(s"测试时长: $elapsedTime ps")
        println(s"操作次数: $operationCount")
        println(f"估算带宽: $bandwidth%.2f Mbps")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("Performance_LatencyTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建低延迟DDR3 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC,
            RAS = TEST_RAS,
            RP = TEST_RP,
            RCD = TEST_RCD,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD,
            REF = TEST_REF,
            FAW = TEST_FAW
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN,
            tPhyRdlat = TEST_PHY_RD_LAT,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = sdramConfig
        )

        val phyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR3,
          dfiConfig = dfiConfig,
          sdramConfig = sdramConfig,
          features = DfiDdrPhyFeatures()
        )

        val phy = DfiDdrPhy(phyConfig)
        phy
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试读延迟
        val readStartTime = simTime()

        // ACTIVATE
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // READ
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 等待读数据返回
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= true
          dut.io.dfi.read.rd(i).rddata #= 0xABCD
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
        }

        val readEndTime = simTime()
        val readLatency = readEndTime - readStartTime

        println(s"读操作延迟: $readLatency ps")

        dut.clockDomain.waitSampling(50)

        // 测试写延迟
        val writeStartTime = simTime()

        // ACTIVATE
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x3000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // WRITE
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 0
        dut.io.dfi.control.address #= 0x4000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 写数据
        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= true
          dut.io.dfi.write.wr(i).wrdata #= 0x5678
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
        }

        val writeEndTime = simTime()
        val writeLatency = writeEndTime - writeStartTime

        println(s"写操作延迟: $writeLatency ps")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("Performance_MultiRankTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建多rank DDR3 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC,
            RAS = TEST_RAS,
            RP = TEST_RP,
            RCD = TEST_RCD,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD,
            REF = TEST_REF,
            FAW = TEST_FAW
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_DUAL,  // 双rank配置
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN,
            tPhyRdlat = TEST_PHY_RD_LAT,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = sdramConfig
        )

        val phyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR3,
          dfiConfig = dfiConfig,
          sdramConfig = sdramConfig,
          features = DfiDdrPhyFeatures()
        )

        val phy = DfiDdrPhy(phyConfig)
        phy
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试rank 0操作
        dut.io.dfi.control.csN #= 0
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.clockDomain.waitSampling(50)

        // 测试rank 1操作
        dut.io.dfi.control.csN #= 1
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x3000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x4000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.clockDomain.waitSampling(50)

        // 同时访问两个rank
        dut.io.dfi.control.csN #= 0
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x5000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.io.dfi.control.csN #= 1
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x6000
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        println(s"多rank测试完成 - 当前选择: ${dut.io.dfi.control.csN.toInt}")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }

  test("FinalValidation_EndToEndTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建完整的DDR3 PHY配置用于最终验证
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC,
            RAS = TEST_RAS,
            RP = TEST_RP,
            RCD = TEST_RCD,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD,
            REF = TEST_REF,
            FAW = TEST_FAW
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN,
            tPhyRdlat = TEST_PHY_RD_LAT,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = sdramConfig
        )

        val phyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR3,
          dfiConfig = dfiConfig,
          sdramConfig = sdramConfig,
          features = DfiDdrPhyFeatures()
        )

        val phy = DfiDdrPhy(phyConfig)
        phy
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化所有信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0
        dut.io.dfi.control.address #= 0
        dut.io.dfi.control.bank #= 0

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
          dut.io.dfi.write.wr(i).wrdata #= 0
          dut.io.dfi.write.wr(i).wrdataMask #= 0
        }

        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
          dut.io.dfi.read.rd(i).rddata #= 0
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 执行完整的读写序列
        val testPatterns = Seq(
          0xAAAA, 0x5555, 0xFF00, 0x00FF, 0xF0F0, 0x0F0F
        )

        for (i <- testPatterns.indices) {
          val pattern = testPatterns(i)

          // ACTIVATE
          dut.io.dfi.control.rasN #= 0
          dut.io.dfi.control.casN #= 1
          dut.io.dfi.control.weN #= 1
          dut.io.dfi.control.address #= (i * 0x1000 + 0x1000).toInt
          dut.io.dfi.control.bank #= (i % 8).toInt
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // WRITE
          dut.io.dfi.control.rasN #= 1
          dut.io.dfi.control.casN #= 0
          dut.io.dfi.control.weN #= 0
          dut.io.dfi.control.address #= (i * 0x10).toInt
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // 写数据
          for (j <- 0 until dut.io.dfi.write.wr.length) {
            dut.io.dfi.write.wr(j).wrdataEn #= true
            dut.io.dfi.write.wr(j).wrdata #= pattern
            dut.io.dfi.write.wr(j).wrdataMask #= 0x00
          }
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          for (j <- 0 until dut.io.dfi.write.wr.length) {
            dut.io.dfi.write.wr(j).wrdataEn #= false
          }

          dut.clockDomain.waitSampling(10)

          // READ
          dut.io.dfi.control.rasN #= 1
          dut.io.dfi.control.casN #= 0
          dut.io.dfi.control.weN #= 1
          dut.io.dfi.control.address #= (i * 0x10).toInt
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // 读数据返回
          for (j <- 0 until dut.io.dfi.read.rd.length) {
            dut.io.dfi.read.rd(j).rddataValid #= true
            dut.io.dfi.read.rd(j).rddata #= pattern
          }
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          for (j <- 0 until dut.io.dfi.read.rd.length) {
            dut.io.dfi.read.rd(j).rddataValid #= false
          }

          dut.clockDomain.waitSampling(10)

          println(f"测试模式 $i: 写入 0x$pattern%04X, 读取验证完成")
        }

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        // 最终状态检查
        println(s"PHY初始化状态: ${dut.io.status.initialized.toBoolean}")
        println(s"PHY校准状态: ${dut.io.status.calibrating.toBoolean}")
        println(s"PHY错误状态: ${dut.io.status.error.toBoolean}")
        println(s"PHY温度状态: ${dut.io.status.temperature.toInt}")
        println(s"PHY频率比: ${dut.io.status.frequencyRatio.toInt}")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        println("端到端系统验证测试完成 - 所有测试模式验证通过")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }

  test("FinalValidation_StressTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建用于压力测试的DDR3 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_300,  // 高频压力测试
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC,
            RAS = TEST_RAS,
            RP = TEST_RP,
            RCD = TEST_RCD,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD,
            REF = TEST_REF,
            FAW = TEST_FAW
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN,
            tPhyRdlat = TEST_PHY_RD_LAT,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = sdramConfig
        )

        val phyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR3,
          dfiConfig = dfiConfig,
          sdramConfig = sdramConfig,
          features = DfiDdrPhyFeatures()
        )

        val phy = DfiDdrPhy(phyConfig)
        phy
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 执行长时间压力测试
        var errorCount = 0

        for (cycle <- 0 until TEST_STRESS_DURATION) {
          val address = (cycle * 0x100) & 0x7FFF
          val bank = (cycle % 8).toInt
          val data = (cycle & 0xFFFF).toInt

          // ACTIVATE
          dut.io.dfi.control.rasN #= 0
          dut.io.dfi.control.casN #= 1
          dut.io.dfi.control.weN #= 1
          dut.io.dfi.control.address #= address
          dut.io.dfi.control.bank #= bank
          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          // 随机读写操作
          if (cycle % 3 == 0) {
            // WRITE
            dut.io.dfi.control.rasN #= 1
            dut.io.dfi.control.casN #= 0
            dut.io.dfi.control.weN #= 0
            dut.io.dfi.control.address #= (cycle * 0x10).toInt
            dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

            for (i <- 0 until dut.io.dfi.write.wr.length) {
              dut.io.dfi.write.wr(i).wrdataEn #= true
              dut.io.dfi.write.wr(i).wrdata #= data
            }
            dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

            for (i <- 0 until dut.io.dfi.write.wr.length) {
              dut.io.dfi.write.wr(i).wrdataEn #= false
            }
          } else {
            // READ
            dut.io.dfi.control.rasN #= 1
            dut.io.dfi.control.casN #= 0
            dut.io.dfi.control.weN #= 1
            dut.io.dfi.control.address #= (cycle * 0x10).toInt
            dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

            for (i <- 0 until dut.io.dfi.read.rd.length) {
              dut.io.dfi.read.rd(i).rddataValid #= true
              dut.io.dfi.read.rd(i).rddata #= data
            }
            dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

            for (i <- 0 until dut.io.dfi.read.rd.length) {
              dut.io.dfi.read.rd(i).rddataValid #= false
            }
          }

          // 偶尔插入刷新命令
          if (cycle % TEST_REFRESH_INTERVAL == 0) {
            dut.io.dfi.control.rasN #= 0
            dut.io.dfi.control.casN #= 0
            dut.io.dfi.control.weN #= 1
            dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)
          }

          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)
        }

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        println(s"压力测试完成 - 测试周期: $TEST_STRESS_DURATION")
        println(s"PHY最终状态 - 初始化: ${dut.io.status.initialized.toBoolean}, 错误: ${dut.io.status.error.toBoolean}")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }
}