package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.core.sim._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.interfaces._
import spinal.tester.SpinalAnyFunSuite

import scala.util.Random

/**
 * DFI适配器单元测试
 *
 * 测试DFI信号到内部命令的转换功能
 */
class DfiAdapterTester extends SpinalAnyFunSuite {

  import spinal.core._
  import spinal.core.sim._
  import spinal.lib.bus.bmb.BmbParameter

  // 测试常量定义
  private val TEST_DDR3_GENERATION = 3
  private val TEST_CHIP_SELECT_SINGLE = 1
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
  private val TEST_DDR_MHZ = 200
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
  private val TEST_INIT_WAIT_CYCLES = 10
  private val TEST_COMMAND_WAIT_CYCLES = 1
  private val TEST_FINAL_WAIT_CYCLES = 50

  test("DfiAdapter_BasicCommandParsing") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ,
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

        val config = DfiAdapterConfig(
          dfiConfig = dfiConfig,
          features = DfiDdrPhyFeatures()
        )

        DfiAdapter(config)
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化所有输入信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0
        dut.io.dfi.control.address #= 0
        dut.io.dfi.control.bank #= 0

        // 测试NOP命令
        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试ACTIVATE命令
        dut.io.dfi.control.rasN #= 0
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x1000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 验证内部命令
        assert(dut.io.dfiInternal.command.valid.toBoolean)
        assert(dut.io.dfiInternal.command.command.toEnum == DdrCommand.ACT)
        assert(dut.io.dfiInternal.command.address.toLong == 0x1000)
        assert(dut.io.dfiInternal.command.bank.toInt == 1)

        // 测试READ命令
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.io.dfi.control.bank #= 2
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        assert(dut.io.dfiInternal.command.valid.toBoolean)
        assert(dut.io.dfiInternal.command.command.toEnum == DdrCommand.READ)
        assert(dut.io.dfiInternal.command.address.toLong == 0x2000)
        assert(dut.io.dfiInternal.command.bank.toInt == 2)

        // 测试WRITE命令
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 0
        dut.io.dfi.control.address #= 0x3000
        dut.io.dfi.control.bank #= 3
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        assert(dut.io.dfiInternal.command.valid.toBoolean)
        assert(dut.io.dfiInternal.command.command.toEnum == DdrCommand.WRITE)
        assert(dut.io.dfiInternal.command.address.toLong == 0x3000)
        assert(dut.io.dfiInternal.command.bank.toInt == 3)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("DfiAdapter_WriteDataParsing") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ,
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

        val config = DfiAdapterConfig(
          dfiConfig = dfiConfig,
          features = DfiDdrPhyFeatures()
        )

        DfiAdapter(config)
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        // 初始化写数据信号
        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
          dut.io.dfi.write.wr(i).wrdata #= 0
          dut.io.dfi.write.wr(i).wrdataMask #= 0
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 发送写数据
        val testData = 0xABCD
        val testMask = 0x03

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= true
          dut.io.dfi.write.wr(i).wrdata #= testData
          dut.io.dfi.write.wr(i).wrdataMask #= testMask
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 验证内部写接口
        assert(dut.io.dfiInternal.write.valid.toBoolean)
        assert(dut.io.dfiInternal.write.data.toInt == testData)
        assert(dut.io.dfiInternal.write.mask.toInt == testMask)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("DfiAdapter_ReadDataGeneration") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ,
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

        val config = DfiAdapterConfig(
          dfiConfig = dfiConfig,
          features = DfiDdrPhyFeatures()
        )

        DfiAdapter(config)
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化读数据信号
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
          dut.io.dfi.read.rd(i).rddata #= 0
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 发送读数据
        val testData = 0x5678
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= true
          dut.io.dfi.read.rd(i).rddata #= testData
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 验证内部读接口
        assert(dut.io.dfiInternal.read.valid.toBoolean)
        assert(dut.io.dfiInternal.read.data.toInt == testData)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("DfiAdapter_TrainingInterface") {
    SimConfig.withVcdWave
      .compile {
        // 创建DDR3配置
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ,
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

        val config = DfiAdapterConfig(
          dfiConfig = dfiConfig,
          features = DfiDdrPhyFeatures()
        )

        DfiAdapter(config)
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

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

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试读训练请求
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        assert(dut.io.training.readTraining.req.toBoolean)

        // 测试写训练请求
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.wrTraining.wrlvlReq.getWidth) {
          dut.io.dfi.wrTraining.wrlvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        assert(dut.io.training.writeTraining.req.toBoolean)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  // 边界情况测试
  test("DfiAdapter_BoundaryConditions") {
    SimConfig.withVcdWave
      .compile {
        // 使用最小配置进行边界测试
        val sdramConfig = SdramConfig(
          generation = SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = 1,
          columnWidth = 1,
          rowWidth = 1,
          dataWidth = 8,
          ddrMHZ = 100,
          ddrWrLat = 1,
          ddrRdLat = 1,
          sdramtime = SdramTiming(
            generation = TEST_DDR3_GENERATION,
            RFC = 1,
            RAS = 1,
            RP = 1,
            RCD = 1,
            WTR = 1,
            WTP = 0,
            RTP = 1,
            RRD = 1,
            REF = 1,
            FAW = 1
          )
        )

        val dfiConfig = DfiConfig(
          chipSelectNumber = 1,
          dataSlice = 1,
          signalConfig = DfiSignalConfig.DDR3(),
          timeConfig = DfiTimeConfig(
            frequencyRatio = 1,
            cmdPhase = 0,
            tPhyWrLat = 1,
            tPhyWrData = 0,
            tPhyWrCsLat = 0,
            tPhyWrCsGap = 0,
            tRddataEn = 1,
            tPhyRdlat = 1,
            tPhyRdCslat = 0,
            tPhyRdCsGap = 0
          ),
          sdram = sdramConfig
        )

        val config = DfiAdapterConfig(
          dfiConfig = dfiConfig,
          features = DfiDdrPhyFeatures()
        )

        DfiAdapter(config)
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_CLOCK_PERIOD)

        // 初始化信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        dut.clockDomain.waitSampling(50)

        // 测试最小配置下的基本功能
        dut.io.dfi.control.rasN #= 0
        dut.clockDomain.waitSampling(1)

        assert(dut.io.dfiInternal.command.valid.toBoolean)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }
}