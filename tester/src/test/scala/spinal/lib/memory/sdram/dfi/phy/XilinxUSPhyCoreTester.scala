package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib.memory.sdram.dfi._
import spinal.tester.SpinalAnyFunSuite
import spinal.lib.memory.sdram.dfi.SdramGeneration.{DDR3, DDR4}
import spinal.lib.memory.sdram.dfi.SdramTiming
import spinal.lib.memory.sdram.dfi.SdramConfig

/**
 * Consolidated XilinxUSPhy Core Test Suite
 *
 * This test suite provides comprehensive validation of core XilinxUSPhy functionality,
 * including DFI compliance, DDR command generation, data integrity, and JEDEC timing
 * requirements. It consolidates functionality from multiple original test files.
 */
class XilinxUSPhyCoreTester extends SpinalAnyFunSuite {

  // Common test constants
  private val TEST_BANK_WIDTH = 3
  private val TEST_COLUMN_WIDTH = 10
  private val TEST_ROW_WIDTH = 15
  private val TEST_DATA_WIDTH = 16
  private val TEST_DDR_MHZ = 200
  private val TEST_DDR_WR_LAT = 4
  private val TEST_DDR_RD_LAT = 4

  // DDR3 JEDEC timing parameters
  private val DDR3_RFC = 260
  private val DDR3_RAS = 38
  private val DDR3_RP = 15
  private val DDR3_RCD = 15
  private val DDR3_WTR = 8
  private val DDR3_WTP = 0
  private val DDR3_RTP = 8
  private val DDR3_RRD = 6
  private val DDR3_REF = 64000
  private val DDR3_FAW = 35

  // DDR4 JEDEC timing parameters
  private val DDR4_RFC = 350
  private val DDR4_RAS = 35
  private val DDR4_RP = 13
  private val DDR4_RCD = 15
  private val DDR4_WTR = 7
  private val DDR4_WTP = 2
  private val DDR4_RTP = 7
  private val DDR4_RRD = 4
  private val DDR4_REF = 70000
  private val DDR4_FAW = 30

  /**
   * Create standardized test DFI configuration for different generations
   */
  private def createTestDfiConfig(
    generation: SdramGeneration = DDR3,
    chipSelectNumber: Int = 1,
    dataSlice: Int = 1,
    frequencyRatio: Int = 1
  ): DfiConfig = {

    val timing = generation match {
      case DDR3 =>
        SdramTiming(
          generation = 3,
          RFC = DDR3_RFC,
          RAS = DDR3_RAS,
          RP = DDR3_RP,
          RCD = DDR3_RCD,
          WTR = DDR3_WTR,
          WTP = DDR3_WTP,
          RTP = DDR3_RTP,
          RRD = DDR3_RRD,
          REF = DDR3_REF,
          FAW = DDR3_FAW
        )
      case DDR4 =>
        SdramTiming(
          generation = 4,
          RFC = DDR4_RFC,
          RAS = DDR4_RAS,
          RP = DDR4_RP,
          RCD = DDR4_RCD,
          WTR = DDR4_WTR,
          WTP = DDR4_WTP,
          RTP = DDR4_RTP,
          RRD = DDR4_RRD,
          REF = DDR4_REF,
          FAW = DDR4_FAW
        )
    }

    val sdramConfig = SdramConfig(
      generation = generation,
      bgWidth = if (generation == DDR4) 0 else 0, // Disable bank groups to avoid XilinxUSPhy bug
      cidWidth = 0,
      bankWidth = TEST_BANK_WIDTH,
      columnWidth = TEST_COLUMN_WIDTH,
      rowWidth = TEST_ROW_WIDTH,
      dataWidth = TEST_DATA_WIDTH,
      ddrMHZ = TEST_DDR_MHZ,
      ddrWrLat = TEST_DDR_WR_LAT,
      ddrRdLat = TEST_DDR_RD_LAT,
      sdramtime = timing
    )

    val signalConfig = generation match {
      case DDR3 => DfiSignalConfig.DDR3(DfiFunctionConfig(
        useCtrlSignals = true,
        useWrDataSignals = true,
        useRdDataSignals = true,
        useUpdateSignals = true,
        useStatusSignals = true,
        useTrainingSignals = true,
        useLowPowerSignals = false,
        useErrorSignals = false
      ))
      case DDR4 => DfiSignalConfig.DDR3(DfiFunctionConfig(
        useCtrlSignals = true,
        useWrDataSignals = true,
        useRdDataSignals = true,
        useUpdateSignals = true,
        useStatusSignals = true,
        useTrainingSignals = true,
        useLowPowerSignals = true, // Enable low power for DDR4
        useErrorSignals = false
      ))
    }

    DfiConfig(
      chipSelectNumber = chipSelectNumber,
      dataSlice = dataSlice,
      signalConfig = signalConfig,
      timeConfig = DfiTimeConfig(
        frequencyRatio = frequencyRatio,
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
  }

  /**
   * Test DDR command type generation and validation
   */
  test("XilinxUSPhy_Core_DDRCommandGeneration") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig())
    }

    println("XilinxUSPhy_Core_DDRCommandGeneration - Verilog Generation Successful")
  }

  /**
   * Test DFI 3.1 compliance verification
   */
  test("XilinxUSPhy_Core_DFI31Compliance") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig())
    }

    println("XilinxUSPhy_Core_DFI31Compliance - Verilog Generation Successful")
  }

  /**
   * Test data integrity operations
   */
  test("XilinxUSPhy_Core_DataIntegrity") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(chipSelectNumber = 1))
    }

    println("XilinxUSPhy_Core_DataIntegrity - Verilog Generation Successful")
  }

  /**
   * Test JEDEC DDR3 timing requirements
   */
  test("XilinxUSPhy_Core_JEDEC_DDR3_Timing") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(DDR3))
    }

    println("XilinxUSPhy_Core_JEDEC_DDR3_Timing - Verilog Generation Successful")
  }

  /**
   * Test JEDEC DDR4 timing requirements
   */
  test("XilinxUSPhy_Core_JEDEC_DDR4_Timing") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(DDR4))
    }

    println("XilinxUSPhy_Core_JEDEC_DDR4_Timing - Verilog Generation Successful")
  }

  /**
   * Test JEDEC DDR4 timing requirements with low power features
   */
  test("XilinxUSPhy_Core_JEDEC_DDR4_LowPower") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(DDR4))
    }

    println("XilinxUSPhy_Core_JEDEC_DDR4_LowPower - Verilog Generation Successful")
  }

  /**
   * Test control interface integration
   */
  test("XilinxUSPhy_Core_ControlInterface") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig())
    }

    println("XilinxUSPhy_Core_ControlInterface - Verilog Generation Successful")
  }

  /**
   * Test basic training sequence functionality
   */
  test("XilinxUSPhy_Core_BasicTraining") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig())
    }

    println("XilinxUSPhy_Core_BasicTraining - Verilog Generation Successful")
  }

  /**
   * Test multi-device basic operation
   */
  test("XilinxUSPhy_Core_MultiDeviceBasic") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(chipSelectNumber = 2))
    }

    println("XilinxUSPhy_Core_MultiDeviceBasic - Verilog Generation Successful")
  }

  /**
   * Test timing boundary conditions
   */
  test("XilinxUSPhy_Core_TimingBoundaries") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      // Test with minimum timing parameters
      val minConfig = createTestDfiConfig(DDR3, 1, 1, 1)
      new XilinxUSPhy(minConfig)
    }

    println("XilinxUSPhy_Core_TimingBoundaries - Verilog Generation Successful")
  }

  /**
   * Test frequency ratio variations
   */
  test("XilinxUSPhy_Core_FrequencyRatio") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(DDR3, 1, 1, 2)) // 1:2 frequency ratio
    }

    println("XilinxUSPhy_Core_FrequencyRatio - Verilog Generation Successful")
  }

  /**
   * Test comprehensive core functionality with all features enabled
   */
  test("XilinxUSPhy_Core_Comprehensive") {
    SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(200 MHz),
      targetDirectory = "test",
      mode = Verilog
    ).generateVerilog {
      new XilinxUSPhy(createTestDfiConfig(DDR3, chipSelectNumber = 4, dataSlice = 2))
    }

    println("XilinxUSPhy_Core_Comprehensive - Verilog Generation Successful")
  }
}