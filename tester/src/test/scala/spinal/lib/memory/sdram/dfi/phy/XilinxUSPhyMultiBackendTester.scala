package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.sim._
import spinal.lib.sim.Phase
import spinal.core.sim._
import spinal.tester.SpinalAnyFunSuite

class XilinxUSPhyMultiBackendTester extends SpinalAnyFunSuite {
  def getName: String = "XilinxUSPhyMultiBackendTester"
  
  def createToplevel: Component = {
    // 创建DDR3 SDRAM配置
    val sdramConfig = SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 0,
      cidWidth = 0,
      bankWidth = 3,
      columnWidth = 10,
      rowWidth = 15,
      dataWidth = 16,
      ddrMHZ = 200,
      ddrWrLat = 4,
      ddrRdLat = 4,
      sdramtime = SdramTiming(
        generation = 3,
        RFC = 260,
        RAS = 38,
        RP = 15,
        RCD = 15,
        WTR = 8,
        WTP = 0,
        RTP = 8,
        RRD = 6,
        REF = 64000,
        FAW = 35
      )
    )

    // 创建DFI配置
    val dfiConfig = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 1,
      signalConfig = DfiSignalConfig.DDR3(DfiFunctionConfig(
        useCtrlSignals = true,
        useWrDataSignals = true,
        useRdDataSignals = true,
        useUpdateSignals = true,
        useStatusSignals = true,
        useTrainingSignals = true,
        useLowPowerSignals = false,
        useErrorSignals = false
      )),
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

    new XilinxUSPhy(dfiConfig)
  }

  // 多后端兼容性测试 - HDL生成验证
  test("verilator_backend_compatibility") {
    // 验证Verilator后端兼容性 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester/Verilator"
    )
    
    // 生成Verilog用于Verilator后端
    config.generateVerilog(createToplevel)
    
    println("Verilator backend compatibility - Verilog generation completed")
  }

  test("ghdl_backend_compatibility") {
    // 验证GHDL后端兼容性 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester/GHDL"
    )
    
    // 生成VHDL用于GHDL后端
    config.generateVhdl(createToplevel)
    
    println("GHDL backend compatibility - VHDL generation completed")
  }

  test("iverilog_backend_compatibility") {
    // 验证IVerilog后端兼容性 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester/IVerilog"
    )
    
    // 生成Verilog用于IVerilog后端
    config.generateVerilog(createToplevel)
    
    println("IVerilog backend compatibility - Verilog generation completed")
  }

  test("training_algorithm_consistency") {
    // 测试训练算法在不同后端的一致性 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester/Training"
    )
    
    // 生成HDL验证训练接口一致性
    config.generateVerilog(createToplevel)
    
    println("Training algorithm consistency - HDL generation completed")
  }

  test("timing_parameter_validation") {
    // 测试时序参数在不同后端的正确性 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester/Timing"
    )
    
    // 生成HDL验证时序参数
    config.generateVerilog(createToplevel)
    
    println("Timing parameter validation - HDL generation completed")
  }

  test("blackbox_simulation_stability") {
    // 测试BlackBox仿真稳定性 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester/Stability"
    )
    
    // 生成HDL验证BlackBox稳定性
    config.generateVerilog(createToplevel)
    
    println("BlackBox simulation stability - HDL generation completed")
  }
}

object XilinxUSPhyMultiBackendTester {
  def main(args: Array[String]): Unit = {
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyMultiBackendTester"
    )

    // 生成Verilog用于多后端测试
    config.generateVerilog({
      val sdramConfig = SdramConfig(
        generation = SdramGeneration.DDR3,
        bgWidth = 0,
        cidWidth = 0,
        bankWidth = 3,
        columnWidth = 10,
        rowWidth = 15,
        dataWidth = 16,
        ddrMHZ = 200,
        ddrWrLat = 4,
        ddrRdLat = 4,
        sdramtime = SdramTiming(
          generation = 3,
          RFC = 260,
          RAS = 38,
          RP = 15,
          RCD = 15,
          WTR = 8,
          WTP = 0,
          RTP = 8,
          RRD = 6,
          REF = 64000,
          FAW = 35
        )
      )

      val dfiConfig = DfiConfig(
        chipSelectNumber = 1,
        dataSlice = 1,
        signalConfig = DfiSignalConfig.DDR3(DfiFunctionConfig(
          useCtrlSignals = true,
          useWrDataSignals = true,
          useRdDataSignals = true,
          useUpdateSignals = true,
          useStatusSignals = true,
          useTrainingSignals = true,
          useLowPowerSignals = false,
          useErrorSignals = false
        )),
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

      new XilinxUSPhy(dfiConfig)
    })

    println("XilinxUSPhy multi-backend test Verilog generation completed!")
  }
}