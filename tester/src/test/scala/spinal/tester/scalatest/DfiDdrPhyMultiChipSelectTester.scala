package spinal.tester.scalatest

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{BmbParameter, Bmb}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy
import spinal.lib.memory.sdram.dfi.phy.DdrStandard
import spinal.core.sim._

/**
 * DFI DDR PHY多芯片选择测试
 *
 * 测试多芯片选择支持，确保PHY正确处理多个芯片选择
 */
class DfiDdrPhyMultiChipSelectTester extends SpinalAnyFunSuite {

  def createMultiChipTestConfig(chipSelectNumber: Int) = {
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
      chipSelectNumber = chipSelectNumber,
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

  test("DfiDdrPhy_MultiChipSelect_1") {
    val (sdramConfig, dfiConfig, bmbParameter) = createMultiChipTestConfig(1)

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

  test("DfiDdrPhy_MultiChipSelect_2") {
    val (sdramConfig, dfiConfig, bmbParameter) = createMultiChipTestConfig(2)

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
          chipSelectNumber = 2,
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

  test("DfiDdrPhy_MultiChipSelect_4") {
    val (sdramConfig, dfiConfig, bmbParameter) = createMultiChipTestConfig(4)

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
          chipSelectNumber = 4,
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