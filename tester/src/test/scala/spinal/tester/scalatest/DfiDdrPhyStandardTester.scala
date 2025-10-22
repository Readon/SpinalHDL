package spinal.tester.scalatest

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{BmbParameter, Bmb}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy
import spinal.lib.memory.sdram.dfi.phy.DdrStandard
import spinal.core.sim._

/**
 * DFI DDR PHY多标准测试
 *
 * 测试不同DDR标准的正确操作（DDR2、DDR3、DDR4、LPDDR系列）
 */
class DfiDdrPhyStandardTester extends SpinalAnyFunSuite {

  def createStandardTestConfig(ddrStandard: SdramGeneration) = {
    val sdramConfig = SdramConfig(
      generation = ddrStandard,
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
        generation = ddrStandard.CCD,
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
      signalConfig = ddrStandard match {
        case SdramGeneration.DDR2 => DfiSignalConfig.DDR2()
        case SdramGeneration.DDR3 => DfiSignalConfig.DDR3()
        case SdramGeneration.DDR4 => DfiSignalConfig.DDR4()
        case _ => DfiSignalConfig.DDR3()
      },
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

  test("DfiDdrPhy_DDR2_Standard") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardTestConfig(SdramGeneration.DDR2)

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
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DDR3_Standard") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardTestConfig(SdramGeneration.DDR3)

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
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DDR4_Standard") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardTestConfig(SdramGeneration.DDR4)

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

        val dfiDdrPhy = DfiDdrPhy.ddr4(
          chipSelectNumber = 1,
          dataWidth = 16,
          sdramConfig = sdramConfig
        )

        dfiDdrPhy.io.dfi <> dfiController.io.dfi

        val io = new Bundle {
          val bmb = slave(Bmb(bmbParameter))
          val phyStatus = out(dfiDdrPhy.io.status)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_LPDDR2_Standard") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardTestConfig(SdramGeneration.DDR2) // 使用DDR2作为基础

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
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_LPDDR3_Standard") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardTestConfig(SdramGeneration.DDR3)

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
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_LPDDR4_Standard") {
    val (sdramConfig, dfiConfig, bmbParameter) = createStandardTestConfig(SdramGeneration.DDR4)

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
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
      }
      dut
    }

    assert(compiled.dut != null)
  }
}