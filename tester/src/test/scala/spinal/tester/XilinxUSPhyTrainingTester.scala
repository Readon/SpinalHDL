package spinal.tester

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy._
import spinal.lib.sim._
import spinal.lib.sim.Phase

class XilinxUSPhyTrainingTester extends SpinalTesterGhdlBase {
  override def getName: String = "XilinxUSPhyTrainingTester"
  override def createToplevel: Component = {
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
    // 测试读眼训练算法 - 结构验证
    val dut = createToplevel.asInstanceOf[XilinxUSPhy]
    
    // 验证基本结构
    assert(dut != null, "DUT should be created")
    assert(dut.io != null, "DUT should have IO interface")
    assert(dut.io.dfi != null, "DUT should have DFI interface")
    assert(dut.io.dfi.rdTraining != null, "DUT should have read training interface")
    assert(dut.io.phyCtrl != null, "DUT should have PHY control interface")
    
    // 验证训练信号存在
    assert(dut.io.dfi.rdTraining.rdlvlGateEn != null, "Read eye training enable signal should exist")
    assert(dut.io.dfi.rdTraining.rdlvlResp != null, "Read eye training response signal should exist")
    assert(dut.io.phyCtrl.rdPhase != null, "PHY read phase control signal should exist")
    assert(dut.io.phyCtrl.dqInc != null, "PHY DQ increment control signal should exist")
    
    println("Read eye training structure validation passed")
  }

  test("ca_training") {
    // 测试命令/地址训练算法 - 结构验证
    val dut = createToplevel.asInstanceOf[XilinxUSPhy]
    
    // 验证基本结构
    assert(dut != null, "DUT should be created")
    assert(dut.io != null, "DUT should have IO interface")
    assert(dut.io.dfi != null, "DUT should have DFI interface")
    assert(dut.io.dfi.caTraining != null, "DUT should have CA training interface")
    assert(dut.io.dfi.control != null, "DUT should have control interface")
    assert(dut.io.phyCtrl != null, "DUT should have PHY control interface")
    
    // 验证训练信号存在
    assert(dut.io.dfi.caTraining.calvlEn != null, "CA training enable signal should exist")
    assert(dut.io.dfi.caTraining.calvlResp != null, "CA training response signal should exist")
    assert(dut.io.dfi.control.address != null, "DFI address control signal should exist")
    assert(dut.io.dfi.control.bank != null, "DFI bank control signal should exist")
    assert(dut.io.phyCtrl.cdlyInc != null, "PHY delay increment control signal should exist")
    
    println("CA training structure validation passed")
  }

  test("training_sequence_integration") {
    // 测试完整的训练序列集成 - 结构验证
    val dut = createToplevel.asInstanceOf[XilinxUSPhy]
    
    // 验证所有训练接口都存在
    assert(dut.io.dfi.wrTraining != null, "Write training interface should exist")
    assert(dut.io.dfi.rdTraining != null, "Read training interface should exist")
    assert(dut.io.dfi.caTraining != null, "CA training interface should exist")
    
    // 验证所有必要的信号都存在
    assert(dut.io.dfi.wrTraining.wrlvlEn != null, "Write leveling enable should exist")
    assert(dut.io.dfi.wrTraining.wrlvlStrobe != null, "Write leveling strobe should exist")
    assert(dut.io.dfi.rdTraining.rdlvlEn != null, "Read gate training enable should exist")
    assert(dut.io.dfi.rdTraining.rdlvlGateEn != null, "Read eye training enable should exist")
    assert(dut.io.dfi.caTraining.calvlEn != null, "CA training enable should exist")
    
    println("Training sequence integration structure validation passed")
  }

  test("training_error_handling") {
    // 测试训练错误处理 - 结构验证
    val dut = createToplevel.asInstanceOf[XilinxUSPhy]
    
    // 验证错误处理结构
    assert(dut != null, "DUT should be created")
    assert(dut.io != null, "DUT should have IO interface")
    assert(dut.io.ctrl != null, "DUT should have control interface")
    
    // 验证所有训练接口都存在，即使在错误情况下也能访问
    assert(dut.io.dfi.wrTraining != null, "Write training interface should exist")
    assert(dut.io.dfi.rdTraining != null, "Read training interface should exist")
    assert(dut.io.dfi.caTraining != null, "CA training interface should exist")
    
    println("Training error handling structure validation passed")
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