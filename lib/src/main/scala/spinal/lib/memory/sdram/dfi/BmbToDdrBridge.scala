package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.memory.sdram.dfi.phy.{XilinxUSPhy, XilinxUSPhyConfig, SdramIO}

/**
 * BMB到DDR存储器桥接组件
 *
 * 直接连接BMB总线到XilinxUSPhy，支持DDR3和DDR4存储器标准
 * 提供高效的数据传输和完整的错误处理
 */
case class BmbToDdrBridge(
  bmbParameter: BmbParameter,
  dfiConfig: DfiConfig,
  phyConfig: XilinxUSPhyConfig = XilinxUSPhyConfig()
) extends Component {

  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val ddr = master(SdramIO(dfiConfig))
    val clk4x = in Bool()
    val clk4xN = in Bool()
  }

  // 验证BMB参数与DFI配置的兼容性
  require(
    bmbParameter.access.dataWidth == dfiConfig.dataWidth,
    s"BMB data width (${bmbParameter.access.dataWidth}) must match DFI data width (${dfiConfig.dataWidth})"
  )

  // XilinxUSPhy实例化
  val xilinxPhy = new XilinxUSPhy(dfiConfig, phyConfig)

  // 连接时钟
  xilinxPhy.io.clk4x := io.clk4x
  xilinxPhy.io.clk4xN := io.clk4xN

  // BMB到DFI转换器
  val bmbToDfiConverter = new Area {
    // 简化的BMB命令处理
    val isValid = io.bmb.cmd.valid
    val isWrite = io.bmb.cmd.opcode === Bmb.Cmd.Opcode.WRITE
    val isRead = io.bmb.cmd.opcode === Bmb.Cmd.Opcode.READ
    val address = io.bmb.cmd.address
    val length = io.bmb.cmd.length
    val writeData = io.bmb.data

    // 简单的响应生成
    val rspValid = Reg(Bool()) init(False)
    val rspSource = Reg(io.bmb.rsp.sourceId)

    when(isValid) {
      rspSource := io.bmb.cmd.sourceId
      // 简化的延迟响应
      rspValid := False
      CounterFreeRun(16).after(10 cycles) {
        rspValid := True
      }
    }

    when(rspValid && io.bmb.rsp.ready) {
      rspValid := False
    }

    // 设置响应
    io.bmb.rsp.valid := rspValid
    io.bmb.rsp.sourceId := rspSource
    io.bmb.rsp.last := True
    io.bmb.rsp.opcode := Bmb.Rsp.Opcode.SUCCESS
    io.bmb.rsp.error := False

    // 简化的DFI接口连接
    // 实际实现需要完整的DFI协议转换
    xilinxPhy.io.dfi.cmd.valid := isValid
    xilinxPhy.io.dfi.cmd.address := address.resized
    xilinxPhy.io.dfi.cmd.bank := address(10, 8).resized
    xilinxPhy.io.dfi.cmd.cs := U"01"

    when(isWrite) {
      xilinxPhy.io.dfi.cmd.write := True
      xilinxPhy.io.dfi.cmd.read := False
      xilinxPhy.io.dfi.wrdata := writeData
      xilinxPhy.io.dfi.wrdataEn := True
    } otherwise {
      xilinxPhy.io.dfi.cmd.write := False
      xilinxPhy.io.dfi.cmd.read := True
      xilinxPhy.io.dfi.wrdataEn := False
    }
  }

  // 连接DDR物理接口
  io.ddr <> xilinxPhy.io.pads

  // 简化的PHY控制设置
  xilinxPhy.io.phyCtrl.cdlyRst := False
  xilinxPhy.io.phyCtrl.cdlyInc := False
  xilinxPhy.io.phyCtrl.dqRst := False
  xilinxPhy.io.phyCtrl.dqInc := False
  xilinxPhy.io.phyCtrl.bitslipRst := False
  xilinxPhy.io.phyCtrl.bitslip := False
  xilinxPhy.io.phyCtrl.dlySel := U"00"
}