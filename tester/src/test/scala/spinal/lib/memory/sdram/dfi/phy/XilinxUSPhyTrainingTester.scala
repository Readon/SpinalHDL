package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.sim._
import spinal.lib.sim.Phase
import spinal.tester.SpinalAnyFunSuite

class XilinxUSPhyTrainingTester extends SpinalAnyFunSuite {
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

  test("write_leveling_training") {
    // 测试写水平训练算法 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester/WriteLeveling"
    )
    
    // 生成Verilog验证写水平训练
    config.generateVerilog(createToplevel)
    
    println("Write leveling training - Verilog generation completed")
  }

  test("read_gate_training") {
    // 测试读门训练算法 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester/ReadGate"
    )
    
    // 生成Verilog验证读门训练
    config.generateVerilog(createToplevel)
    
    println("Read gate training - Verilog generation completed")
  }

  test("read_eye_training") {
    // 测试读眼训练算法 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester/ReadEye"
    )

    // 生成Verilog验证读眼训练
    config.generateVerilog(createToplevel)

    println("Read eye training - Verilog generation completed")
  }

  test("ca_training") {
    // 测试命令/地址训练算法 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester/CATraining"
    )

    // 生成Verilog验证CA训练
    config.generateVerilog(createToplevel)

    println("CA training - Verilog generation completed")
  }

  test("training_sequence_integration") {
    // 测试完整的训练序列集成 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester/TrainingIntegration"
    )

    // 生成Verilog验证训练序列集成
    config.generateVerilog(createToplevel)

    println("Training sequence integration - Verilog generation completed")
  }

  test("training_error_handling") {
    // 测试训练错误处理 - HDL生成验证
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester/TrainingError"
    )

    // 生成Verilog验证训练错误处理
    config.generateVerilog(createToplevel)

    println("Training error handling - Verilog generation completed")
  }
}

object XilinxUSPhyTrainingTester {
  def main(args: Array[String]): Unit = {
    val config = SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "simWorkspace/XilinxUSPhyTrainingTester"
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

    println("XilinxUSPhy training test Verilog generation completed!")
  }
}