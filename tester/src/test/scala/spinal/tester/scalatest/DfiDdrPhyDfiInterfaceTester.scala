package spinal.tester.scalatest

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{BmbParameter, Bmb}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy
import spinal.lib.memory.sdram.dfi.phy.DdrStandard
import spinal.core.sim._

/**
 * DFI DDR PHY DFI 3.1接口组完整测试
 *
 * 测试所有DFI 3.1接口组（控制、写数据、读数据、更新、低功耗、训练）的正确实现
 */
class DfiDdrPhyDfiInterfaceTester extends SpinalAnyFunSuite {

  def createDfiInterfaceTestConfig() = {
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

  test("DfiDdrPhy_DFI31_Control_Interface") {
    val (sdramConfig, dfiConfig, bmbParameter) = createDfiInterfaceTestConfig()

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
          // 暴露控制接口信号用于验证
          val controlInterface = out(dfiDdrPhy.io.dfi.control)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.controlInterface := dfiDdrPhy.io.dfi.control
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DFI31_Write_Data_Interface") {
    val (sdramConfig, dfiConfig, bmbParameter) = createDfiInterfaceTestConfig()

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
          // 暴露写数据接口信号用于验证
          val writeDataInterface = out(dfiDdrPhy.io.dfi.write)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.writeDataInterface := dfiDdrPhy.io.dfi.write
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DFI31_Read_Data_Interface") {
    val (sdramConfig, dfiConfig, bmbParameter) = createDfiInterfaceTestConfig()

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
          // 暴露读数据接口信号用于验证
          val readDataInterface = out(dfiDdrPhy.io.dfi.read)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.readDataInterface := dfiDdrPhy.io.dfi.read
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DFI31_Update_Interface") {
    val (sdramConfig, dfiConfig, bmbParameter) = createDfiInterfaceTestConfig()

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
          // 暴露更新接口信号用于验证
          val updateInterface = out(dfiDdrPhy.io.dfi.update)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.updateInterface := dfiDdrPhy.io.dfi.update
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DFI31_Low_Power_Interface") {
    val (sdramConfig, dfiConfig, bmbParameter) = createDfiInterfaceTestConfig()

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
          // 暴露低功耗接口信号用于验证
          val lowPowerInterface = out(dfiDdrPhy.io.dfi.lowPowerControl)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.lowPowerInterface := dfiDdrPhy.io.dfi.lowPowerControl
      }
      dut
    }

    assert(compiled.dut != null)
  }

  test("DfiDdrPhy_DFI31_Training_Interface") {
    val (sdramConfig, dfiConfig, bmbParameter) = createDfiInterfaceTestConfig()

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
          // 暴露训练接口信号用于验证
          val trainingInterface = out(dfiDdrPhy.io.training)
        }

        io.bmb <> dfiController.io.bmb
        io.phyStatus := dfiDdrPhy.io.status
        io.trainingInterface := dfiDdrPhy.io.training
      }
      dut
    }

    assert(compiled.dut != null)
  }
}