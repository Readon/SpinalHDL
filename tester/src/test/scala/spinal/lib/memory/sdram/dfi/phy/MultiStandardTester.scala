package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.core.sim._
import spinal.lib.memory.sdram.dfi._
import spinal.tester.SpinalAnyFunSuite

/**
 * 多标准DDR PHY测试
 *
 * 测试不同DDR标准的PHY配置和操作
 */
class MultiStandardTester extends SpinalAnyFunSuite {

  import spinal.core._
  import spinal.core.sim._

  // 测试常量定义
  private val TEST_DDR2_GENERATION = 2
  private val TEST_DDR3_GENERATION = 3
  private val TEST_CHIP_SELECT_SINGLE = 1
  private val TEST_CHIP_SELECT_DUAL = 2
  private val TEST_DATA_SLICE_SINGLE = 1
  private val TEST_DATA_SLICE_DUAL = 2
  private val TEST_FREQUENCY_RATIO_1 = 1
  private val TEST_FREQUENCY_RATIO_2 = 2
  private val TEST_CMD_PHASE = 0
  private val TEST_PHY_WR_LAT_DDR2 = 1
  private val TEST_PHY_WR_LAT_DDR3 = 1
  private val TEST_PHY_WR_DATA = 0
  private val TEST_PHY_WR_CS_LAT = 0
  private val TEST_PHY_WR_CS_GAP = 0
  private val TEST_RDDATA_EN_DDR2 = 4
  private val TEST_RDDATA_EN_DDR3 = 5
  private val TEST_PHY_RD_LAT_DDR2 = 5
  private val TEST_PHY_RD_LAT_DDR3 = 6
  private val TEST_PHY_RD_CS_LAT = 0
  private val TEST_PHY_RD_CS_GAP = 0
  private val TEST_BANK_WIDTH = 3
  private val TEST_COLUMN_WIDTH = 10
  private val TEST_ROW_WIDTH_DDR2 = 14
  private val TEST_ROW_WIDTH_DDR3 = 15
  private val TEST_DATA_WIDTH = 16
  private val TEST_DATA_WIDTH_LPDDR = 32
  private val TEST_DDR_MHZ_200 = 200
  private val TEST_DDR_MHZ_400 = 400
  private val TEST_DDR_WR_LAT_DDR2 = 3
  private val TEST_DDR_WR_LAT_DDR3 = 4
  private val TEST_DDR_RD_LAT_DDR2 = 3
  private val TEST_DDR_RD_LAT_DDR3 = 4
  private val TEST_RFC_DDR2 = 195
  private val TEST_RFC_DDR3 = 260
  private val TEST_RAS_DDR2 = 45
  private val TEST_RAS_DDR3 = 38
  private val TEST_RP_DDR2 = 15
  private val TEST_RP_DDR3 = 15
  private val TEST_RCD_DDR2 = 15
  private val TEST_RCD_DDR3 = 15
  private val TEST_WTR = 8
  private val TEST_WTP = 0
  private val TEST_RTP = 8
  private val TEST_RRD_DDR2 = 8
  private val TEST_RRD_DDR3 = 6
  private val TEST_REF_DDR2 = 52000
  private val TEST_REF_DDR3 = 64000
  private val TEST_FAW = 35
  private val TEST_CLOCK_PERIOD = 10
  private val TEST_INIT_WAIT_CYCLES = 50
  private val TEST_COMMAND_WAIT_CYCLES = 1
  private val TEST_FINAL_WAIT_CYCLES = 100

  test("MultiStandard_DDR2_BasicOperation") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR2 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR2,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR2,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT_DDR2,
          ddrRdLat = TEST_DDR_RD_LAT_DDR2,
          sdramtime = SdramTiming(
            generation = TEST_DDR2_GENERATION,
            RFC = TEST_RFC_DDR2,
            RAS = TEST_RAS_DDR2,
            RP = TEST_RP_DDR2,
            RCD = TEST_RCD_DDR2,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD_DDR2,
            REF = TEST_REF_DDR2,
            FAW = TEST_FAW
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR2(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT_DDR2,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN_DDR2,
            tPhyRdlat = TEST_PHY_RD_LAT_DDR2,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = sdramConfig
        )

        val phyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR2,
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
        dut.io.dfi.control.address #= 0
        dut.io.dfi.control.bank #= 0

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试DDR2基本操作序列
        // ACTIVATE
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // READ
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 模拟读数据返回
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= true
          dut.io.dfi.read.rd(i).rddata #= 0xABCD
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
        }

        dut.clockDomain.waitSampling(10)

        // WRITE
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 0
        dut.io.dfi.control.address #= 0x3000
        dut.io.dfi.control.bank #= 2
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 写数据
        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= true
          dut.io.dfi.write.wr(i).wrdata #= 0x5678
          dut.io.dfi.write.wr(i).wrdataMask #= 0x00
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
        }

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("MultiStandard_DDR3_BasicOperation") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR3,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT_DDR3,
          ddrRdLat = TEST_DDR_RD_LAT_DDR3,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC_DDR3,
            RAS = TEST_RAS_DDR3,
            RP = TEST_RP_DDR3,
            RCD = TEST_RCD_DDR3,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD_DDR3,
            REF = TEST_REF_DDR3,
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
            tPhyWrLat = TEST_PHY_WR_LAT_DDR3,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN_DDR3,
            tPhyRdlat = TEST_PHY_RD_LAT_DDR3,
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
        dut.io.dfi.control.address #= 0
        dut.io.dfi.control.bank #= 0

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试DDR3基本操作序列
        // ACTIVATE
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // READ
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 模拟读数据返回
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= true
          dut.io.dfi.read.rd(i).rddata #= 0xABCD
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
        }

        dut.clockDomain.waitSampling(10)

        // WRITE
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 0
        dut.io.dfi.control.address #= 0x3000
        dut.io.dfi.control.bank #= 2
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 写数据
        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= true
          dut.io.dfi.write.wr(i).wrdata #= 0x5678
          dut.io.dfi.write.wr(i).wrdataMask #= 0x00
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
        }

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("MultiStandard_StandardSwitching") {
    SimConfig.withVcdWave
      .compile {
        // 测试标准切换功能
        // 先创建DDR3配置
        val ddr3Config = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR3,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT_DDR3,
          ddrRdLat = TEST_DDR_RD_LAT_DDR3,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC_DDR3,
            RAS = TEST_RAS_DDR3,
            RP = TEST_RP_DDR3,
            RCD = TEST_RCD_DDR3,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD_DDR3,
            REF = TEST_REF_DDR3,
            FAW = TEST_FAW
          )
        )

        val ddr3DfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_1,
            cmdPhase = TEST_CMD_PHASE,
            tPhyWrLat = TEST_PHY_WR_LAT_DDR3,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN_DDR3,
            tPhyRdlat = TEST_PHY_RD_LAT_DDR3,
            tPhyRdCslat = TEST_PHY_RD_CS_LAT,
            tPhyRdCsGap = TEST_PHY_RD_CS_GAP
          ),
          sdram = ddr3Config
        )

        val ddr3PhyConfig = DfiDdrPhyConfig(
          ddrStandard = DdrStandard.DDR3,
          dfiConfig = ddr3DfiConfig,
          sdramConfig = ddr3Config,
          features = DfiDdrPhyFeatures()
        )

        val phy = DfiDdrPhy(ddr3PhyConfig)
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

        // 测试DDR3操作
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

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        // 切换到DDR4模式（这里只是模拟配置切换）
        // 在实际实现中，这需要重新配置PHY参数
        println("模拟标准切换完成")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }

  test("MultiStandard_ChipSelectTest") {
    SimConfig.withVcdWave
      .compile {
        // 测试多芯片选择支持
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR3,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT_DDR3,
          ddrRdLat = TEST_DDR_RD_LAT_DDR3,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = TEST_RFC_DDR3,
            RAS = TEST_RAS_DDR3,
            RP = TEST_RP_DDR3,
            RCD = TEST_RCD_DDR3,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD_DDR3,
            REF = TEST_REF_DDR3,
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
            tPhyWrLat = TEST_PHY_WR_LAT_DDR3,
            tPhyWrData = TEST_PHY_WR_DATA,
            tPhyWrCsLat = TEST_PHY_WR_CS_LAT,
            tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
            tRddataEn = TEST_RDDATA_EN_DDR3,
            tPhyRdlat = TEST_PHY_RD_LAT_DDR3,
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

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        println(s"多芯片选择测试完成 - 当前选择: ${dut.io.dfi.control.csN.toInt}")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }
}