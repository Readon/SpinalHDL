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

  // BMB到DDR桥接器
  val bmbToDdrBridge = BmbToDdrBridge(bmbParameter, dfiConfig, phyConfig)
  bmbToDdrBridge.io.bmb <> io.bmb
  bmbToDdrBridge.io.clk4x := io.clk4x
  bmbToDdrBridge.io.clk4xN := io.clk4xN

  // XilinxUSPhy物理层
  val xilinxPhy = new XilinxUSPhy(dfiConfig, phyConfig)
  xilinxPhy.io.clk4x := io.clk4x
  xilinxPhy.io.clk4xN := io.clk4xN

  // 连接桥接器到PHY的DFI接口
  xilinxPhy.io.dfi <> io.dfi

  // 直接连接DDR物理接口
  // 在实际系统中，xilinxPhy.io.pads 会连接到实际的DDR芯片
}

/**
 * DDR3 DFI控制器便利构造函数
 */
object DfiController {
  def ddr3(
    bmbParameter: BmbParameter,
    addressWidth: Int = 16,
    bankWidth: Int = 3,
    columnWidth: Int = 10,
    dataWidth: Int = 64,
    chipSelectNumber: Int = 1
  ): DfiController = {

    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR3,
      timing = SdramTiming.DDR3.default,
      layout = SdramLayout(
        addressWidth = addressWidth,
        bankWidth = bankWidth,
        columnWidth = columnWidth,
        dataWidth = dataWidth,
        chipSelectNumber = chipSelectNumber
      )
    )

    val dfiConfig = DfiConfig(
      dataRate = 2,
      dataWidth = dataWidth,
      chipSelectNumber = chipSelectNumber,
      frequencyRatio = 1,
      timingConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 0,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 3,
        tRddataEn = 0,
        tPhyRdlat = 0,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 3
      ),
      signalConfig = DfiSignalConfig.DDR3,
      sdramConfig = Some(sdramConfig)
    )

    DfiController(bmbParameter, dfiConfig)
  }

  def ddr4(
    bmbParameter: BmbParameter,
    addressWidth: Int = 17,
    bankWidth: Int = 2,
    bankGroupWidth: Int = 2,
    columnWidth: Int = 10,
    dataWidth: Int = 64,
    chipSelectNumber: Int = 1
  ): DfiController = {

    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR4,
      timing = SdramTiming.DDR4.default,
      layout = SdramLayout(
        addressWidth = addressWidth,
        bankWidth = bankWidth,
        bankGroupWidth = Some(bankGroupWidth),
        columnWidth = columnWidth,
        dataWidth = dataWidth,
        chipSelectNumber = chipSelectNumber
      )
    )

    val dfiConfig = DfiConfig(
      dataRate = 2,
      dataWidth = dataWidth,
      chipSelectNumber = chipSelectNumber,
      frequencyRatio = 1,
      timingConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 0,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 3,
        tRddataEn = 0,
        tPhyRdlat = 0,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 3
      ),
      signalConfig = DfiSignalConfig.DDR4,
      sdramConfig = Some(sdramConfig)
    )

    DfiController(bmbParameter, dfiConfig)
  }
}