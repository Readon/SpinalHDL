package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.core.sim._
import spinal.lib.memory.sdram.dfi._
import spinal.tester.SpinalAnyFunSuite

/**
 * DFI 3.1合规性测试
 *
 * 验证所有必需的DFI 3.1接口信号组的正确实现
 */
class Dfi31ComplianceTester extends SpinalAnyFunSuite {

  import spinal.core._
  import spinal.core.sim._

  // 测试常量定义
  private val TEST_DDR3_GENERATION = 3
  private val TEST_CHIP_SELECT_SINGLE = 1
  private val TEST_DATA_SLICE_SINGLE = 1
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
  private val TEST_INIT_WAIT_CYCLES = 50
  private val TEST_COMMAND_WAIT_CYCLES = 1
  private val TEST_FINAL_WAIT_CYCLES = 100

  test("Dfi31_ControlInterfaceGroup") {
    SimConfig.withVcdWave
      .compile {
        // 创建支持完整DFI 3.1控制接口的PHY配置
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

        // 初始化所有控制信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0
        dut.io.dfi.control.address #= 0
        dut.io.dfi.control.bank #= 0

        // 初始化可选控制信号
        for (i <- 0 until dut.io.dfi.control.cke.getWidth) {
          dut.io.dfi.control.cke(i) #= true
        }
        for (i <- 0 until dut.io.dfi.control.odt.getWidth) {
          dut.io.dfi.control.odt(i) #= false
        }
        for (i <- 0 until dut.io.dfi.control.resetN.getWidth) {
          dut.io.dfi.control.resetN(i) #= true
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试所有基本命令
        val commands = Seq(
          ("ACTIVATE", false, true, true, 0x1000, 1),
          ("READ", true, false, true, 0x2000, 1),
          ("WRITE", true, false, false, 0x3000, 2),
          ("PRECHARGE", false, true, false, 0x4000, 0),
          ("REFRESH", false, false, true, 0, 0),
          ("ZQCS", true, true, false, 0, 0)
        )

        for ((cmdName, rasN, casN, weN, addr, bank) <- commands) {
          dut.io.dfi.control.rasN #= (if (rasN) 1 else 0)
          dut.io.dfi.control.casN #= (if (casN) 1 else 0)
          dut.io.dfi.control.weN #= (if (weN) 1 else 0)
          dut.io.dfi.control.address #= addr
          dut.io.dfi.control.bank #= bank

          dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

          println(s"测试命令: $cmdName - RAS_N: $rasN, CAS_N: $casN, WE_N: $weN")
        }

        // 测试芯片选择和时钟使能
        dut.io.dfi.control.csN #= 1
        for (i <- 0 until dut.io.dfi.control.cke.getWidth) {
          dut.io.dfi.control.cke(i) #= false
        }
        dut.clockDomain.waitSampling(10)

        dut.io.dfi.control.csN #= 0
        for (i <- 0 until dut.io.dfi.control.cke.getWidth) {
          dut.io.dfi.control.cke(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        // 测试ODT控制
        for (i <- 0 until dut.io.dfi.control.odt.getWidth) {
          dut.io.dfi.control.odt(i) #= true
        }
        dut.clockDomain.waitSampling(5)
        for (i <- 0 until dut.io.dfi.control.odt.getWidth) {
          dut.io.dfi.control.odt(i) #= false
        }

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("Dfi31_WriteInterfaceGroup") {
    SimConfig.withVcdWave
      .compile {
        // 创建支持完整DFI 3.1写接口的PHY配置
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

        // 初始化控制信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        // 初始化写接口信号
        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
          dut.io.dfi.write.wr(i).wrdata #= 0
          dut.io.dfi.write.wr(i).wrdataMask #= 0
          for (j <- 0 until dut.io.dfi.write.wr(i).wrdataCsN.getWidth) {
            dut.io.dfi.write.wr(i).wrdataCsN(j) #= false
          }
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 发送WRITE命令
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 0
        dut.io.dfi.control.address #= 0x3000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 发送写数据
        val testData = 0xABCD
        val testMask = 0x03

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= true
          dut.io.dfi.write.wr(i).wrdata #= testData
          dut.io.dfi.write.wr(i).wrdataMask #= testMask
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.write.wr.length) {
          dut.io.dfi.write.wr(i).wrdataEn #= false
        }

        // 测试写数据片选
        for (i <- 0 until dut.io.dfi.write.wr.length) {
          for (j <- 0 until dut.io.dfi.write.wr(i).wrdataCsN.getWidth) {
            dut.io.dfi.write.wr(i).wrdataCsN(j) #= true
          }
        }
        dut.clockDomain.waitSampling(5)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        println(s"写接口测试完成 - 数据: $testData, 掩码: $testMask")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }

  test("Dfi31_ReadInterfaceGroup") {
    SimConfig.withVcdWave
      .compile {
        // 创建支持完整DFI 3.1读接口的PHY配置
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

        // 初始化控制信号
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 1
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.csN #= 0

        // 初始化读接口信号
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
          dut.io.dfi.read.rd(i).rddata #= 0
          for (j <- 0 until dut.io.dfi.read.rdCs(i).rddataCsN.getWidth) {
            dut.io.dfi.read.rdCs(i).rddataCsN(j) #= false
          }
        }

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 发送READ命令
        dut.io.dfi.control.rasN #= 1
        dut.io.dfi.control.casN #= 0
        dut.io.dfi.control.weN #= 1
        dut.io.dfi.control.address #= 0x2000
        dut.io.dfi.control.bank #= 1
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        // 模拟读数据返回
        val testData = 0x5678
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= true
          dut.io.dfi.read.rd(i).rddata #= testData
        }
        dut.clockDomain.waitSampling(TEST_COMMAND_WAIT_CYCLES)

        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
        }

        // 测试读数据片选
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          for (j <- 0 until dut.io.dfi.read.rdCs(i).rddataCsN.getWidth) {
            dut.io.dfi.read.rdCs(i).rddataCsN(j) #= true
          }
        }
        dut.clockDomain.waitSampling(5)

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        println(s"读接口测试完成 - 数据: $testData")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }

  test("Dfi31_TrainingInterfaceGroup") {
    SimConfig.withVcdWave
      .compile {
        // 创建支持完整DFI 3.1训练接口的PHY配置
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

        // 初始化训练接口信号
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

        // 测试写电平训练
        for (i <- 0 until dut.io.dfi.wrTraining.wrlvlReq.getWidth) {
          dut.io.dfi.wrTraining.wrlvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        println(s"写电平训练请求状态: ${dut.io.status.initialized.toBoolean}")

        // 测试读电平训练
        for (i <- 0 until dut.io.dfi.wrTraining.wrlvlReq.getWidth) {
          dut.io.dfi.wrTraining.wrlvlReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        println(s"读电平训练请求状态: ${dut.io.status.calibrating.toBoolean}")

        // 测试门控训练
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlGateReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlGateReq(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        println(s"读门控训练请求状态: ${dut.io.status.error.toBoolean}")

        // 测试CA训练
        for (i <- 0 until dut.io.dfi.rdTraining.rdlvlGateReq.getWidth) {
          dut.io.dfi.rdTraining.rdlvlGateReq(i) #= false
        }
        for (i <- 0 until dut.io.dfi.caTraining.calvlReq.getWidth) {
          dut.io.dfi.caTraining.calvlReq(i) #= true
        }
        dut.clockDomain.waitSampling(10)

        println(s"CA训练请求状态: ${dut.io.status.temperature.toInt}")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("Dfi31_StatusInterfaceGroup") {
    SimConfig.withVcdWave
      .compile {
        // 创建支持完整DFI 3.1状态接口的PHY配置
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

        // 初始化状态接口信号
        dut.io.dfi.status.initStart #= false
        dut.io.dfi.status.initComplete #= false
        dut.io.dfi.status.freqRatio #= 0
        dut.io.dfi.status.dramClkDisable #= 0

        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)

        // 测试初始化开始
        dut.io.dfi.status.initStart #= true
        dut.clockDomain.waitSampling(10)

        println(s"初始化开始状态: ${dut.io.status.initialized.toBoolean}")

        // 测试初始化完成
        dut.io.dfi.status.initStart #= false
        dut.io.dfi.status.initComplete #= true
        dut.clockDomain.waitSampling(10)

        println(s"初始化完成状态: ${dut.io.status.calibrating.toBoolean}")

        // 测试频率比配置
        dut.io.dfi.status.freqRatio #= 1 // 2:1频率比
        dut.clockDomain.waitSampling(10)

        println(s"频率比状态: ${dut.io.status.frequencyRatio.toInt}")

        // 测试DRAM时钟禁用
        dut.io.dfi.status.dramClkDisable #= 1
        dut.clockDomain.waitSampling(10)

        println(s"DRAM时钟禁用状态: ${dut.io.status.temperature.toInt}")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)
        simSuccess()
      }
  }

  test("Dfi31_FrequencyRatioTest") {
    SimConfig.withVcdWave
      .compile {
        // 测试不同频率比配置
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

        // 测试1:1频率比
        dut.io.dfi.status.freqRatio #= 0
        dut.clockDomain.waitSampling(20)

        println(s"1:1频率比状态: ${dut.io.status.frequencyRatio.toInt}")

        // 测试1:2频率比
        dut.io.dfi.status.freqRatio #= 1
        dut.clockDomain.waitSampling(20)

        println(s"1:2频率比状态: ${dut.io.status.frequencyRatio.toInt}")

        // 测试1:4频率比
        dut.io.dfi.status.freqRatio #= 2
        dut.clockDomain.waitSampling(20)

        println(s"1:4频率比状态: ${dut.io.status.frequencyRatio.toInt}")

        dut.clockDomain.waitSampling(TEST_FINAL_WAIT_CYCLES)

        // 验证最终频率比状态
        println(s"最终频率比: ${dut.io.status.frequencyRatio.toInt}")

        dut.clockDomain.waitSampling(50)
        simSuccess()
      }
  }
}