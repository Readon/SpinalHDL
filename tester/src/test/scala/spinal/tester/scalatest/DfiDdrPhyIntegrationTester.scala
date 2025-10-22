package spinal.tester.scalatest

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{BmbParameter, Bmb}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy
import spinal.lib.memory.sdram.dfi.phy.DdrStandard
import spinal.core.sim._

/**
 * DFI DDR PHY集成测试
 *
 * 测试DfiDdrPhy与DfiController的集成，确保PHY正确作为slave接收DFI信号
 */
class DfiDdrPhyIntegrationTester extends SpinalAnyFunSuite {

  test("DfiDdrPhy_DfiController_Integration") {
    // 测试配置
    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 0,
      cidWidth = 0,
      bankWidth = 3,
      columnWidth = 10,
      rowWidth = 13,
      dataWidth = 16,
      ddrMHZ = 400,
      ddrWrLat = 6,
      ddrRdLat = 6,
      sdramtime = SdramTiming(
        generation = 3,
        RFC = 160,
        RAS = 35,
        RP = 15,
        RCD = 15,
        WTR = 8,
        WTP = 15,
        RTP = 8,
        RRD = 6,
        REF = 64,
        FAW = 35
      )
    )

    val dfiConfig = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 2,
      signalConfig = DfiSignalConfig.DDR3(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 1,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 5,
        tPhyRdlat = 6,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = sdramConfig
    )

    val bmbParameter = BmbParameter(
      addressWidth = 32,
      dataWidth = 32,
      lengthWidth = 6,
      sourceWidth = 0,
      contextWidth = 4,
      canRead = true,
      canWrite = true,
      alignment = BmbParameter.BurstAlignement.WORD
    )

    // 创建测试组件
    val compiled = SimConfig.withConfig(SpinalConfig()).compile {
      val dut = new Component {
        // DfiController作为master
        val dfiController = DfiController(
          bmbp = bmbParameter,
          task = TaskParameter(
            bytePerTaskMax = 64,
            timingWidth = 8,
            refWidth = 16,
            cmdBufferSize = 8,
            dataBufferSize = 8,
            rspBufferSize = 8
          ),
          dfiConfig = dfiConfig,
          addrMap = RowBankColumn
        )

        // DfiDdrPhy作为slave
        val dfiDdrPhy = DfiDdrPhy.ddr3(
          chipSelectNumber = 1,
          dataWidth = 16,
          sdramConfig = sdramConfig
        )

        // 连接DFI接口
        dfiDdrPhy.io.dfi <> dfiController.io.dfi

        // 暴露BMB接口用于测试
        val io = new Bundle {
          val bmb = slave(Bmb(bmbParameter))
          val phyStatus = out(dfiDdrPhy.io.status)
          val phyDebug = out(dfiDdrPhy.io.debug)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.phyDebug := dfiDdrPhy.io.debug
      }
      dut
    }

    // 验证编译成功
    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_BmbBridge_Interoperability") {
    // 测试DfiDdrPhy与BmbBridge的互操作性
    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 0,
      cidWidth = 0,
      bankWidth = 3,
      columnWidth = 10,
      rowWidth = 13,
      dataWidth = 16,
      ddrMHZ = 400,
      ddrWrLat = 6,
      ddrRdLat = 6,
      sdramtime = SdramTiming(
        generation = 3,
        RFC = 160,
        RAS = 35,
        RP = 15,
        RCD = 15,
        WTR = 8,
        WTP = 15,
        RTP = 8,
        RRD = 6,
        REF = 64,
        FAW = 35
      )
    )

    val dfiConfig = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 2,
      signalConfig = DfiSignalConfig.DDR3(),
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 1,
        tPhyWrData = 0,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 5,
        tPhyRdlat = 6,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = sdramConfig
    )

    val bmbParameter = BmbParameter(
      addressWidth = 32,
      dataWidth = 32,
      lengthWidth = 6,
      sourceWidth = 0,
      contextWidth = 4,
      canRead = true,
      canWrite = true,
      alignment = BmbParameter.BurstAlignement.WORD
    )

    val taskConfig = TaskConfig(
      taskParameter = TaskParameter(
        bytePerTaskMax = 64,
        timingWidth = 8,
        refWidth = 16,
        cmdBufferSize = 8,
        dataBufferSize = 8,
        rspBufferSize = 8
      ),
      contextWidth = 4,
      writeTokenInterfaceWidth = 4,
      writeTokenBufferSize = 8,
      canRead = true,
      canWrite = true
    )

    val compiled = SimConfig.withConfig(SpinalConfig()).compile {
      val dut = new Component {
        // BmbBridge
        val bmbBridge = BmbBridge(
          bmbp = bmbParameter,
          taskConfig = taskConfig,
          dfiConfig = dfiConfig,
          addrMap = RowBankColumn
        )

        // Control组件
        val control = Control(
          taskConfig = taskConfig,
          dfiConfig = dfiConfig
        )

        // Alignment组件
        val alignment = Alignment(dfiConfig)

        // DfiDdrPhy
        val dfiDdrPhy = DfiDdrPhy.ddr3(
          chipSelectNumber = 1,
          dataWidth = 16,
          sdramConfig = sdramConfig
        )

        // 连接流水线
        bmbBridge.io.taskPort <> control.io.input
        control.io.output <> alignment.io.input
        alignment.io.output <> dfiDdrPhy.io.dfi

        // 暴露接口
        val io = new Bundle {
          val bmb = slave(Bmb(bmbParameter))
          val phyStatus = out(dfiDdrPhy.io.status)
        }

        io.bmb <> bmbBridge.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }
}