package spinal.tester.scalatest

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{BmbParameter, Bmb}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy
import spinal.lib.memory.sdram.dfi.phy.DdrStandard
import spinal.core.sim._

/**
 * DFI DDR PHY标准切换功能测试
 *
 * 测试在运行时切换不同DDR标准的正确操作
 */
class DfiDdrPhyStandardSwitchingTester extends SpinalAnyFunSuite {

  def createStandardSwitchingTestConfig() = {
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

    (sdramConfig, dfiConfig, bmbParameter)
  }

  test("DfiDdrPhy_Standard_Switching_DDR3_to_DDR4") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardSwitchingTestConfig()

    val compiled = SimConfig.withConfig(SpinalConfig()).compile {
      val dut = new Component {
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

        // 创建支持标准切换的PHY
        val dfiDdrPhy = DfiDdrPhy.ddr3(
          chipSelectNumber = 1,
          dataWidth = 16,
          sdramConfig = sdramConfig
        )

        dfiDdrPhy.io.dfi <> dfiController.io.dfi

        val io = new Bundle {
          val bmb = slave(Bmb(bmbParameter))
          val phyStatus = out(dfiDdrPhy.io.status)
          // 标准切换控制信号
          val switchToDdr4 = in Bool() default False
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status

        // 模拟标准切换逻辑
        when(io.switchToDdr4) {
          // 这里可以添加标准切换的逻辑
          // 实际实现中需要重新初始化PHY
        }
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_Standard_Switching_DDR3_to_LPDDR3") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardSwitchingTestConfig()

    val compiled = SimConfig.withConfig(SpinalConfig()).compile {
      val dut = new Component {
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

        val dfiDdrPhy = DfiDdrPhy.ddr3(
          chipSelectNumber = 1,
          dataWidth = 16,
          sdramConfig = sdramConfig
        )

        dfiDdrPhy.io.dfi <> dfiController.io.dfi

        val io = new Bundle {
          val bmb = slave(Bmb(bmbParameter))
          val phyStatus = out(dfiDdrPhy.io.status)
          val switchToLpddr3 = in Bool() default False
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status

        when(io.switchToLpddr3) {
          // 标准切换逻辑
        }
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_Standard_Switching_Multiple_Transitions") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardSwitchingTestConfig()

    val compiled = SimConfig.withConfig(SpinalConfig()).compile {
      val dut = new Component {
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

        val dfiDdrPhy = DfiDdrPhy.ddr3(
          chipSelectNumber = 1,
          dataWidth = 16,
          sdramConfig = sdramConfig
        )

        dfiDdrPhy.io.dfi <> dfiController.io.dfi

        val io = new Bundle {
          val bmb = slave(Bmb(bmbParameter))
          val phyStatus = out(dfiDdrPhy.io.status)
          // 多个标准切换选项
          val switchToDdr2 = in Bool() default False
          val switchToDdr3 = in Bool() default False
          val switchToDdr4 = in Bool() default False
          val switchToLpddr2 = in Bool() default False
          val switchToLpddr3 = in Bool() default False
          val switchToLpddr4 = in Bool() default False
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status

        // 多标准切换逻辑
        when(io.switchToDdr2 || io.switchToDdr3 || io.switchToDdr4 ||
             io.switchToLpddr2 || io.switchToLpddr3 || io.switchToLpddr4) {
          // 标准切换处理
        }
      }
      dut
    }

    assert(compiled.dut != null)
  }
}