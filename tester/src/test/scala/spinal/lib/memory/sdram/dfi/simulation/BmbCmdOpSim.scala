package spinal.lib.memory.sdram.dfi.simulation

import spinal.core._
import spinal.core.sim._
import spinal.demo.phy.BmbCmdOp
import spinal.lib.bus.bmb.BmbParameter
import spinal.lib.memory.sdram.dfi._

/**
 * BMB命令操作仿真测试
 *
 * 测试BMB总线命令转换为DFI操作的仿真功能
 */
object BmbCmdOpSim {

  // 测试常量定义
  private val TEST_TIMING_WIDTH = 3
  private val TEST_REF_WIDTH = 23
  private val TEST_CMD_BUFFER_SIZE = 64
  private val TEST_DATA_BUFFER_SIZE = 64
  private val TEST_RSP_BUFFER_SIZE = 64
  private val TEST_CHIP_SELECT_SINGLE = 1
  private val TEST_DATA_SLICE_SINGLE = 1
  private val TEST_FREQUENCY_RATIO_1 = 1
  private val TEST_CMD_PHASE = 0
  private val TEST_PHY_WR_LAT = 1
  private val TEST_PHY_WR_DATA = 2
  private val TEST_PHY_WR_CS_GAP = 3
  private val TEST_RDDATA_EN = 1
  private val TEST_PHY_RD_LAT = 4
  private val TEST_PHY_RD_CS_GAP = 3
  private val TEST_PHY_RD_CS_LAT = 0
  private val TEST_PHY_WR_CS_LAT = 0
  private val TEST_BANK_WIDTH = 3
  private val TEST_COLUMN_WIDTH = 10
  private val TEST_ROW_WIDTH = 15
  private val TEST_DATA_WIDTH = 16
  private val TEST_DDR_MHZ = 100
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
  private val TEST_ADDRESS_WIDTH = 32
  private val TEST_DATA_WIDTH_BMB = 16
  private val TEST_SOURCE_WIDTH = 1
  private val TEST_CONTEXT_WIDTH = 2
  private val TEST_LENGTH_WIDTH = 6
  private val TEST_SIMULATION_CLOCK_PERIOD = 10
  private val TEST_INIT_WAIT_CYCLES = 10
  private val TEST_RSP_VALID_WAIT_CYCLES = 31

  def main(args: Array[String]): Unit = {
    val sdramtime = SdramTiming(
      generation = 3,
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
    val sdram = SdramConfig(
      SdramGeneration.DDR3,
      bgWidth = 0,
      cidWidth = 0,
      bankWidth = TEST_BANK_WIDTH,
      columnWidth = TEST_COLUMN_WIDTH,
      rowWidth = TEST_ROW_WIDTH,
      dataWidth = TEST_DATA_WIDTH,
      ddrMHZ = TEST_DDR_MHZ,
      ddrWrLat = TEST_DDR_WR_LAT,
      ddrRdLat = TEST_DDR_RD_LAT,
      sdramtime = sdramtime
    )
    val timeConfig = DfiTimeConfig(
      frequencyRatio = TEST_FREQUENCY_RATIO_1,
      cmdPhase = TEST_CMD_PHASE,
      tPhyWrLat = sdram.tPhyWrlat,
      tPhyWrData = TEST_PHY_WR_DATA,
      tPhyWrCsGap = TEST_PHY_WR_CS_GAP,
      tRddataEn = sdram.tRddataEn,
      tPhyRdlat = TEST_PHY_RD_LAT,
      tPhyRdCsGap = TEST_PHY_RD_CS_GAP,
      tPhyRdCslat = TEST_PHY_RD_CS_LAT,
      tPhyWrCsLat = TEST_PHY_WR_CS_LAT
    )
    val dfiConfig: DfiConfig = DfiConfig(
      chipSelectNumber = TEST_CHIP_SELECT_SINGLE,
      dataSlice = TEST_DATA_SLICE_SINGLE,
      signalConfig = new DDR3SignalConfig(DfiFunctionConfig(), false) {
        override val useWrdataCsN = false
        override val useRddataCsN = false
      },
      timeConfig = timeConfig,
      sdram = sdram
    )
    val bmbp: BmbParameter = BmbParameter(
      addressWidth = sdram.byteAddressWidth + log2Up(dfiConfig.chipSelectNumber),
      dataWidth = dfiConfig.beatWidth,
      sourceWidth = TEST_SOURCE_WIDTH,
      contextWidth = TEST_CONTEXT_WIDTH,
      lengthWidth = TEST_LENGTH_WIDTH,
      alignment = BmbParameter.BurstAlignement.WORD
    )
    SimConfig.withWave
      .compile {
        val dut = BmbCmdOp(bmbp, dfiConfig)
        dut
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(TEST_SIMULATION_CLOCK_PERIOD)
        dut.io.bmb.rsp.valid #= false
        dut.io.bmb.rsp.last #= false
        dut.io.initDone #= false
        dut.clockDomain.waitSampling(TEST_INIT_WAIT_CYCLES)
        dut.io.initDone #= true
        dut.io.bmb.rsp.valid #= true
        dut.clockDomain.waitSampling(TEST_RSP_VALID_WAIT_CYCLES)
        dut.io.bmb.rsp.last #= true
        dut.clockDomain.waitSampling()
        dut.io.bmb.rsp.valid #= false
        dut.io.bmb.rsp.last #= false
        dut.clockDomain.waitSampling(100)
        simSuccess()
      }
  }
}
