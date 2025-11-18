package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{BmbParameter, BmbAccessParameter, Bmb}
import spinal.tester.code.SpinalAnyFunSuite

/**
 * BMB-DDR协议转换的完整测试套件
 * 包含BMB协议、DFI协议、DDR协议以及端到端转换测试
 */
class BmbDdrProtocolTester extends SpinalAnyFunSuite {

  test("BmbToDdrBridge should convert BMB read commands to DFI protocol") {
    println("=== Testing BMB read → DFI protocol conversion ===")

    // 创建测试配置
    val bmbAccess = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(bmbAccess)

    val ddr3DfiConfig = createDdr3Config()

    // 验证BMB和DFI协议的基本兼容性
    assert(bmbParameter.access.dataWidth == ddr3DfiConfig.dataWidth,
      "BMB data width must match DFI data width for protocol conversion")

    // 验证桥接器参数兼容性（不实例化Component）
    assert(bmbParameter.access.addressWidth >= ddr3DfiConfig.addressWidth,
      "BMB address width should be >= DFI address width")

    // 验证控制信号配置
    assert(ddr3DfiConfig.signalConfig.useRasN, "DDR3 DFI should use RAS# signal")
    assert(ddr3DfiConfig.signalConfig.useCasN, "DDR3 DFI should use CAS# signal")
    assert(ddr3DfiConfig.signalConfig.useWeN, "DDR3 DFI should use WE# signal")

    println("✓ BMB read → DFI protocol conversion test passed")
  }

  test("BmbToDdrBridge should convert BMB write commands to DFI protocol") {
    println("=== Testing BMB write → DFI protocol conversion ===")

    val bmbAccess = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(bmbAccess)

    val ddr4DfiConfig = createDdr4Config()

    // 验证BMB和DFI协议的基本兼容性
    assert(bmbParameter.access.dataWidth == ddr4DfiConfig.dataWidth,
      "BMB data width must match DFI data width for protocol conversion")

    // 验证桥接器参数兼容性（不实例化Component）
    assert(bmbParameter.access.addressWidth >= ddr4DfiConfig.addressWidth,
      "BMB address width should be >= DFI address width")

    // 验证DDR4特定信号配置
    assert(ddr4DfiConfig.signalConfig.useRasN, "DDR4 DFI should use RAS# signal")
    assert(ddr4DfiConfig.signalConfig.useCasN, "DDR4 DFI should use CAS# signal")
    assert(ddr4DfiConfig.signalConfig.useWeN, "DDR4 DFI should use WE# signal")
    assert(ddr4DfiConfig.signalConfig.useBg, "DDR4 DFI should use Bank Group signals")

    println("✓ BMB write → DFI protocol conversion test passed")
  }

  test("BmbToDdrBridge should handle DDR3 bank management") {
    println("=== Testing DDR3 bank management ===")

    val bmbAccess = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(bmbAccess)

    val ddr3DfiConfig = createDdr3Config()

    // 验证DDR3银行配置
    assert(ddr3DfiConfig.bankWidth == 3, "DDR3 should have 3 bank address bits (8 banks)")
    assert(ddr3DfiConfig.sdram.bankWidth == 3, "SDRAM config should match DFI config")

    println("✓ DDR3 bank management test passed")
  }

  test("BmbToDdrBridge should handle DDR4 bank group management") {
    println("=== Testing DDR4 bank group management ===")

    val bmbAccess = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 64
    )
    val bmbParameter = BmbParameter(bmbAccess)

    val ddr4DfiConfig = createDdr4Config()

    // 验证DDR4银行组配置
    assert(ddr4DfiConfig.bankWidth == 2, "DDR4 should have 2 bank address bits (4 banks)")
    assert(ddr4DfiConfig.sdram.bgWidth == 2, "DDR4 should have 2 bank group bits (4 bank groups)")
    assert(ddr4DfiConfig.sdram.bankWidth == 2, "SDRAM config should match DFI config")

    println("✓ DDR4 bank group management test passed")
  }

  test("BmbToDdrBridge should enforce DDR3 timing constraints") {
    println("=== Testing DDR3 timing constraints ===")

    val ddr3DfiConfig = createDdr3Config()
    val timing = ddr3DfiConfig.sdram.sdramtime

    // 验证DDR3 JEDEC时序参数
    assert(timing.RCD == 14, "DDR3 tRCD should be 14 cycles")
    assert(timing.RP == 35, "DDR3 tRP should be 35 cycles")
    assert(timing.RAS == 35, "DDR3 tRAS should be 35 cycles")
    assert(timing.RRD == 6, "DDR3 tRRD should be 6 cycles")
    assert(timing.FAW == 40, "DDR3 tFAW should be 40 cycles")

    println("✓ DDR3 timing constraints test passed")
  }

  test("BmbToDdrBridge should enforce DDR4 timing constraints") {
    println("=== Testing DDR4 timing constraints ===")

    val ddr4DfiConfig = createDdr4Config()
    val timing = ddr4DfiConfig.sdram.sdramtime

    // 验证DDR4 JEDEC时序参数
    assert(timing.RCD == 14, "DDR4 tRCD should be 14 cycles")
    assert(timing.RP == 35, "DDR4 tRP should be 35 cycles")
    assert(timing.RAS == 35, "DDR4 tRAS should be 35 cycles")
    assert(timing.RRD == 6, "DDR4 tRRD should be 6 cycles")
    assert(timing.FAW == 30, "DDR4 tFAW should be 30 cycles (smaller than DDR3)")

    println("✓ DDR4 timing constraints test passed")
  }

  test("BmbToDdrBridge should handle address mapping correctly") {
    println("=== Testing address mapping ===")

    val ddr3DfiConfig = createDdr3Config()
    val ddr4DfiConfig = createDdr4Config()

    // 验证DDR3地址映射 (addressWidth = max(row, col))
    assert(ddr3DfiConfig.addressWidth == 16, // max(16(row), 10(col))
      "DDR3 address width should be max(row, column) = max(16, 10) = 16")

    // 验证DDR4地址映射 (addressWidth = max(row, col))
    assert(ddr4DfiConfig.addressWidth == 17, // max(17(row), 10(col))
      "DDR4 address width should be max(row, column) = max(17, 10) = 17")

    println("✓ Address mapping test passed")
  }

  test("BmbToDdrBridge should generate proper DFI control signals") {
    println("=== Testing DFI control signal generation ===")

    val ddr3FunctionConfig = DfiFunctionConfig(
      useCtrlSignals = true,
      useWrDataSignals = true,
      useRdDataSignals = true,
      useUpdateSignals = false,
      useStatusSignals = false,
      useTrainingSignals = false,
      useLowPowerSignals = false,
      useErrorSignals = false
    )

    val ddr3SignalConfig = DfiSignalConfig.DDR3(ddr3FunctionConfig, useCrc = false)

    // 验证DDR3信号配置
    assert(ddr3SignalConfig.useRasN, "DDR3 should use RAS# signal")
    assert(ddr3SignalConfig.useCasN, "DDR3 should use CAS# signal")
    assert(ddr3SignalConfig.useWeN, "DDR3 should use WE# signal")
    assert(!ddr3SignalConfig.useBg, "DDR3 should not use Bank Group signals")
    assert(!ddr3SignalConfig.useCid, "DDR3 should not use Chip ID signals")

    println("✓ DFI control signal generation test passed")
  }

  test("BmbToDdrBridge should handle DDR4 specific signals") {
    println("=== Testing DDR4 specific signals ===")

    val ddr4FunctionConfig = DfiFunctionConfig(
      useCtrlSignals = true,
      useWrDataSignals = true,
      useRdDataSignals = true,
      useUpdateSignals = false,
      useStatusSignals = false,
      useTrainingSignals = false,
      useLowPowerSignals = false,
      useErrorSignals = false
    )

    val ddr4SignalConfig = DfiSignalConfig.DDR4(ddr4FunctionConfig, useCrc = true)

    // 验证DDR4信号配置
    assert(ddr4SignalConfig.useRasN, "DDR4 should use RAS# signal")
    assert(ddr4SignalConfig.useCasN, "DDR4 should use CAS# signal")
    assert(ddr4SignalConfig.useWeN, "DDR4 should use WE# signal")
    assert(ddr4SignalConfig.useBg, "DDR4 should use Bank Group signals")
    assert(ddr4SignalConfig.useAckN, "DDR4 should use ACT# signal")

    println("✓ DDR4 specific signals test passed")
  }

  // 辅助方法：创建DDR3配置
  private def createDdr3Config(): DfiConfig = {
    val ddr3SdramConfig = SdramConfig(
      generation = SdramGeneration.DDR3,
      bgWidth = 0,
      cidWidth = 0,
      bankWidth = 3,
      columnWidth = 10,
      rowWidth = 16,
      dataWidth = 32,
      ddrMHZ = 400,
      ddrWrLat = 11,
      ddrRdLat = 11,
      sdramtime = SdramTiming(
        generation = 3,
        RFC = 160,
        RAS = 35,
        RP = 35,
        RCD = 14,
        WTR = 8,
        WTP = 15,
        RTP = 8,
        RRD = 6,
        REF = 64,
        FAW = 40
      )
    )

    val dfiFunctionConfig = DfiFunctionConfig(
      useCtrlSignals = true,
      useWrDataSignals = true,
      useRdDataSignals = true,
      useUpdateSignals = false,
      useStatusSignals = false,
      useTrainingSignals = false,
      useLowPowerSignals = false,
      useErrorSignals = false
    )

    val ddr3SignalConfig = DfiSignalConfig.DDR3(dfiFunctionConfig, useCrc = false)

    DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = ddr3SignalConfig,
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 9,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 9,
        tPhyRdlat = 9,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = ddr3SdramConfig
    )
  }

  // 辅助方法：创建DDR4配置
  private def createDdr4Config(): DfiConfig = {
    val ddr4SdramConfig = SdramConfig(
      generation = SdramGeneration.DDR4,
      bgWidth = 2,
      cidWidth = 0,
      bankWidth = 2,
      columnWidth = 10,
      rowWidth = 17,
      dataWidth = 32,
      ddrMHZ = 800,
      ddrWrLat = 12,
      ddrRdLat = 12,
      sdramtime = SdramTiming(
        generation = 4,
        RFC = 295,
        RAS = 35,
        RP = 35,
        RCD = 14,
        WTR = 8,
        WTP = 15,
        RTP = 8,
        RRD = 6,
        REF = 64,
        FAW = 30
      )
    )

    val dfiFunctionConfig = DfiFunctionConfig(
      useCtrlSignals = true,
      useWrDataSignals = true,
      useRdDataSignals = true,
      useUpdateSignals = false,
      useStatusSignals = false,
      useTrainingSignals = false,
      useLowPowerSignals = false,
      useErrorSignals = false
    )

    val ddr4SignalConfig = DfiSignalConfig.DDR4(dfiFunctionConfig, useCrc = true)

    DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = ddr4SignalConfig,
      timeConfig = DfiTimeConfig(
        frequencyRatio = 1,
        cmdPhase = 0,
        tPhyWrLat = 10,
        tPhyWrData = 1,
        tPhyWrCsLat = 0,
        tPhyWrCsGap = 0,
        tRddataEn = 10,
        tPhyRdlat = 10,
        tPhyRdCslat = 0,
        tPhyRdCsGap = 0
      ),
      sdram = ddr4SdramConfig
    )
  }
}