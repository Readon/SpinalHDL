package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.memory.sdram.dfi.phy.{XilinxUSPhy, XilinxUSPhyConfig, SdramIO}

/**
 * DFI控制器组件
 *
 * 连接BMB总线到DDR存储器，使用XilinxUSPhy作为物理层实现
 * 支持DDR3和DDR4标准，提供完整的DFI 3.1兼容接口
 */
case class DfiController(
  bmbParameter: BmbParameter,
  dfiConfig: DfiConfig,
  phyConfig: XilinxUSPhyConfig = XilinxUSPhyConfig()
) extends Component {

  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val dfi = master(Dfi(dfiConfig))
    val clk4x = in Bool()
    val clk4xN = in Bool()
  }

  // 验证参数兼容性
  require(
    bmbParameter.access.dataWidth == dfiConfig.dataWidth,
    s"BMB data width (${bmbParameter.access.dataWidth}) must match DFI data width (${dfiConfig.dataWidth})"
  )

  // XilinxUSPhy物理层
  val xilinxPhy = new XilinxUSPhy(dfiConfig, phyConfig)
  xilinxPhy.io.clk4x := io.clk4x
  xilinxPhy.io.clk4xN := io.clk4xN

  // BMB到DDR桥接器
  val bmbToDdrBridge = BmbToDdrBridge(bmbParameter, dfiConfig, phyConfig)
  bmbToDdrBridge.io.bmb <> io.bmb
  bmbToDdrBridge.io.clk4x := io.clk4x
  bmbToDdrBridge.io.clk4xN := io.clk4xN

  // 连接桥接器内部PHY的DFI接口到外部DFI接口
  xilinxPhy.io.dfi <> io.dfi

  // 连接桥接器内部PHY到外部PHY的DDR物理接口
  xilinxPhy.io.pads <> bmbToDdrBridge.xilinxPhy.io.pads
}

/**
 * DDR3 DFI控制器便利构造函数
 */
object DfiController {
  def ddr3(
    bmbParameter: BmbParameter,
    addressWidth: Int = 16,
    bankWidth: Int = 3,  // DDR3: 8 banks
    columnWidth: Int = 10,
    dataWidth: Int = 64,
    chipSelectNumber: Int = 1
  ): DfiController = {

    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 0,
      cidWidth = 0,
      bankWidth = bankWidth,
      columnWidth = columnWidth,
      rowWidth = addressWidth,
      dataWidth = dataWidth,
      ddrMHZ = 800,  // DDR3 default frequency
      ddrWrLat = 11,
      ddrRdLat = 11,
      sdramtime = SdramTiming(
        generation = 3,
        RFC = 160,
        RAS = 35,
        RP = 35,
        RCD = 14,
        WTR = 8,
        WTP = 15,
        RTP = 8,
        RRD = 6,
        REF = 64,
        FAW = 40
      )
    )

    val dfiConfig = DfiConfig(
      chipSelectNumber = chipSelectNumber,
      dataSlice = 8,
      signalConfig = DfiSignalConfig.DDR3,
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 9,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 9,
        tPhyRdlat = 9,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = sdramConfig
    )

    DfiController(bmbParameter, dfiConfig)
  }

  def ddr4(
    bmbParameter: BmbParameter,
    addressWidth: Int = 17,
    bankWidth: Int = 4,  // DDR4: 4 banks per group
    bankGroupWidth: Int = 2,  // DDR4: 4 bank groups
    columnWidth: Int = 10,
    dataWidth: Int = 64,
    chipSelectNumber: Int = 1
  ): DfiController = {

    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR4,
      bgWidth = bankGroupWidth,
      cidWidth = 0,
      bankWidth = bankWidth,
      columnWidth = columnWidth,
      rowWidth = addressWidth,
      dataWidth = dataWidth,
      ddrMHZ = 1600, // DDR4 default frequency
      ddrWrLat = 12,
      ddrRdLat = 12,
      sdramtime = SdramTiming(
        generation = 4,
        RFC = 295,
        RAS = 35,
        RP = 35,
        RCD = 14,
        WTR = 8,
        WTP = 15,
        RTP = 8,
        RRD = 6,
        REF = 64,
        FAW = 30
      )
    )

    val dfiConfig = DfiConfig(
      chipSelectNumber = chipSelectNumber,
      dataSlice = 8,
      signalConfig = DfiSignalConfig.DDR4,
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 10,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 10,
        tPhyRdlat = 10,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = sdramConfig
    )

    DfiController(bmbParameter, dfiConfig)
  }
}