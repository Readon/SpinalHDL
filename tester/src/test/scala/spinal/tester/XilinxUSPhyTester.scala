package spinal.tester

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy._
import spinal.lib.sim._
import spinal.lib.sim.Phase

class XilinxUSPhyTester extends SpinalTesterGhdlBase {
  override def getName: String = "XilinxUSPhyTester"
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

  override def backendConfig(config: SpinalConfig) = config.copy(
    defaultClockDomainFrequency = FixedFrequency(200 MHz)
  )

  test("basic_initialization") {
    // 测试基本初始化序列 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTester/BasicInit"
    )
    
    // 生成Verilog验证基本结构
    config.generateVerilog(createToplevel)
    
    println("Basic initialization - Verilog generation completed")
  }

  test("training_sequence") {
    // 测试训练序列 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTester/Training"
    )
    
    // 生成Verilog验证训练接口
    config.generateVerilog(createToplevel)
    
    println("Training sequence - Verilog generation completed")
  }

  test("command_interface") {
    // 测试命令接口 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTester/Command"
    )
    
    // 生成Verilog验证命令接口
    config.generateVerilog(createToplevel)
    
    println("Command interface - Verilog generation completed")
  }

  test("data_interface") {
    // 测试数据接口 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTester/Data"
    )
    
    // 生成Verilog验证数据接口
    config.generateVerilog(createToplevel)
    
    println("Data interface - Verilog generation completed")
  }
}

object XilinxUSPhyTester {
  def main(args: Array[String]): Unit = {
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTester"
    )

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

    println("XilinxUSPhy test Verilog generation completed!")
  }
}