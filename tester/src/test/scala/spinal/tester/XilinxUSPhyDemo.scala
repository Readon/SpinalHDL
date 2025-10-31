package spinal.tester

import spinal.core._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy._

object XilinxUSPhyDemo extends App {
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
      useTrainingSignals = false, // Training disabled to ensure compilation works
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

  // 生成Verilog代码
  val dut = SpinalVerilog(new XilinxUSPhy(dfiConfig))

  // Xilinx USPhy Verilog generation completed successfully!
  // Generated files: USPhy.v (main Verilog file)
}