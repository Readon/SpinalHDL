package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.core.sim._
import spinal.lib.memory.sdram.dfi._
import spinal.tester.SpinalAnyFunSuite

import scala.util.Random

/**
 * DFI DDR PHY集成测试
 *
 * 测试完整PHY组件与DfiController的集成
 */
class DfiDdrPhyTester extends SpinalAnyFunSuite {

  import spinal.core._
  import spinal.core.sim._
  import spinal.lib.bus.bmb.BmbParameter

  // 测试常量定义
  private val TEST_DDR3_GENERATION = 3
  private val TEST_DDR4_GENERATION = 4
  private val TEST_CHIP_SELECT_SINGLE = 1
  private val TEST_CHIP_SELECT_DUAL = 2
  private val TEST_DATA_SLICE_SINGLE = 1
  private val TEST_DATA_SLICE_DUAL = 2
  private val TEST_FREQUENCY_RATIO_1 = 1
  private val TEST_FREQUENCY_RATIO_2 = 2
  private val TEST_CMD_PHASE = 0
  private val TEST_PHY_WR_LAT = 1
  private val TEST_PHY_WR_DATA = 0
  private val TEST_PHY_WR_CS_LAT = 0
  private val TEST_PHY_WR_CS_GAP = 0
  private val TEST_RDDATA_EN = 5
  private val TEST_PHY_RD_LAT = 6
  private val TEST_PHY_RD_CS_LAT = 0
  private val TEST_PHY_RD_CS_GAP = 0
  private val TEST_BANK_WIDTH_DDR3 = 3
  private val TEST_BANK_WIDTH_DDR4 = 2
  private val TEST_COLUMN_WIDTH = 10
  private val TEST_ROW_WIDTH_DDR3 = 15
  private val TEST_ROW_WIDTH_DDR4 = 16
  private val TEST_DATA_WIDTH = 16
  private val TEST_DATA_WIDTH_LPDDR = 32
  private val TEST_DDR_MHZ_200 = 200
  private val TEST_DDR_MHZ_400 = 400
  private val TEST_DDR_WR_LAT = 4
  private val TEST_DDR_RD_LAT = 4
  private val TEST_RFC_DDR3 = 260
  private val TEST_RFC_DDR4 = 350
  private val TEST_RAS_DDR3 = 38
  private val TEST_RAS_DDR4 = 35
  private val TEST_RP_DDR3 = 15
  private val TEST_RP_DDR4 = 14
  private val TEST_RCD_DDR3 = 15
  private val TEST_RCD_DDR4 = 14
  private val TEST_WTR = 8
  private val TEST_WTP = 0
  private val TEST_RTP = 8
  private val TEST_RRD_DDR3 = 6
  private val TEST_RRD_DDR4 = 8
  private val TEST_REF = 64000
  private val TEST_FAW_DDR3 = 35
  private val TEST_FAW_DDR4 = 30
  private val TEST_CLOCK_PERIOD = 10
  private val TEST_INIT_WAIT_CYCLES = 50
  private val TEST_COMMAND_WAIT_CYCLES = 1
  private val TEST_FINAL_WAIT_CYCLES = 100

  test("DfiDdrPhy_DDR3_Integration") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH_DDR3,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR3,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
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
            REF = TEST_REF,
            FAW = TEST_FAW_DDR3
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

        // 创建PHY - 使用新的3模块架构
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

        // 初始化写数据信号
        dut.io.dfi.write.wr.foreach { wr =>
          wr.wrdataEn #= false
          wr.wrdata #= 0
          wr.wrdataMask #= 0
        }

        // 初始化读数据信号
        dut.io.dfi.read.rd.foreach { rd =>
          rd.rddataValid #= false
          rd.rddata #= 0
        }

        // 初始化训练信号
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlGateReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlGateReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.wrTraining.wrlvlReq.getWidth) {
          dut.io.dfi.wrTraining.wrlvlReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.caTraining.calvlReq.getWidth) {
          dut.io.dfi.caTraining.calvlReq(i) #= false
        }

        // 初始化状态信号
        dut.io.dfi.status.initStart #= false
        dut.io.dfi.status.initComplete #= false        
        dut.io.dfi.status.freqRatio #= 0

        for (i <- 0 until dut.io.dfi.status.dramClkDisable.getWidth) {
          dut.io.dfi.status.dramClkDisable(i) #= false
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试基本命令传输
        // 发送ACTIVATE命令
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 发送READ命令
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

        // 发送WRITE命令和数据
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 0
        dut.io.dfi.control.address #= 0x3000
        dut.io.dfi.control.bank #= 2
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 发送写数据
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

        // 验证PHY状态
        println(s"PHY初始化状态: ${dut.io.status.initialized.toBoolean}")
        println(s"PHY校准状态: ${dut.io.status.calibrating.toBoolean}")
        println(s"PHY错误状态: ${dut.io.status.error.toBoolean}")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("DfiDdrPhy_DDR4_Integration") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR4 PHY配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR4,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH_DDR4,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR4,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_400,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = SdramTiming(
            generation = TEST_DDR4_GENERATION,
            RFC = TEST_RFC_DDR4,
            RAS = TEST_RAS_DDR4,
            RP = TEST_RP_DDR4,
            RCD = TEST_RCD_DDR4,
            WTR = TEST_WTR,
            WTP = TEST_WTP,
            RTP = TEST_RTP,
            RRD = TEST_RRD_DDR4,
            REF = TEST_REF,
            FAW = TEST_FAW_DDR4
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR4(),
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
          ddrStandard = DdrStandard.DDR4,
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

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试DDR4特有的ACTIVATE命令（带Bank Group）
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 发送READ命令
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("DfiDdrPhy_FrequencyRatioTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3 PHY配置，使用2:1频率比
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH_DDR3,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR3,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
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
            REF = TEST_REF,
            FAW = TEST_FAW_DDR3
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
          dataSlice = TEST_DATA_SLICE_SINGLE,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = TEST_FREQUENCY_RATIO_2,  // 2:1频率比
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

        // 测试频率比配置
        dut.io.dfi.status.freqRatio #= 1
        dut.clockDomain.waitSampling(10)

        // 发送命令序列
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

        // 验证频率比状态
        println(s"频率比: ${dut.io.status.frequencyRatio.toInt}")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }

  test("DfiDdrPhy_TrainingInterfaceTest") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3 PHY配置，启用训练功能
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH_DDR3,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH_DDR3,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ_200,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
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
            REF = TEST_REF,
            FAW = TEST_FAW_DDR3
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
          features = DfiDdrPhyFeatures(trainingSupport = true)
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

        // 测试写电平训练请求
        for (i <- 0 until dut.io.dfi.wrTraining.wrlvlReq.getWidth) {
          dut.io.dfi.wrTraining.wrlvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        // 验证训练接口响应
        println(s"写训练请求状态: ${dut.io.dfi.wrTraining.wrlvlReq(0).toBoolean}")

        // 测试读电平训练请求
        for (i <- 0 until dut.io.dfi.wrTraining.wrlvlReq.getWidth) {
          dut.io.dfi.wrTraining.wrlvlReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        println(s"读训练请求状态: ${dut.io.dfi.rdTraining.rdlvlReq(0).toBoolean}")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }
}