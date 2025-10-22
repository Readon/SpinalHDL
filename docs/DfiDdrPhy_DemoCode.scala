/**
 * DfiDdrPhy Demo Code
 *
 * This file contains ready-to-use demo implementations showcasing
 * the DfiDdrPhy component in various configurations.
 */

package spinal.lib.memory.sdram.dfi.phy.demo

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy

/**
 * Demo 1: Basic DDR3 System
 * Simplest possible DDR3 setup with default configurations
 */
class BasicDdr3Demo extends Component {
  // SDRAM configuration for DDR3-1600
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

  // DFI configuration
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

  // BMB configuration
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

  // Instantiate components
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

  val phy = DfiDdrPhy.ddr3(
    chipSelectNumber = 1,
    dataWidth = 16,
    sdramConfig = sdramConfig
  )

  // Connect interfaces
  phy.io.dfi <> dfiController.io.dfi

  // Expose interfaces
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val status = out(phy.io.status)
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.status := phy.io.status
}

/**
 * Demo 2: High-Performance DDR4 System
 * DDR4 with advanced features and multi-chip support
 */
class HighPerformanceDdr4Demo extends Component {
  val chipCount = 2

  // SDRAM configuration for DDR4-3200 dual-rank
  val sdramConfig = SdramConfig(
    generation = SdramGeneration.DDR4,
    bgWidth = 2,
    cidWidth = 1,  // CID for dual-rank
    bankWidth = 4, // DDR4 has 4-bit bank address
    columnWidth = 10,
    rowWidth = 15, // DDR4 has more rows
    dataWidth = 32,
    ddrMHZ = 800,  // DDR4-1600 (800MHz)
    ddrWrLat = 10,
    ddrRdLat = 11,
    sdramtime = SdramTiming(
      generation = 4,
      RFC = 350,
      RAS = 39,
      RP = 15,
      RCD = 15,
      WTR = 8,
      WTP = 20,
      RTP = 8,
      RRD = 6,
      REF = 64,
      FAW = 25
    )
  )

  // DFI configuration with 1:2 frequency ratio
  val dfiConfig = DfiConfig(
    chipSelectNumber = chipCount,
    dataSlice = 4,
    signalConfig = DfiSignalConfig.DDR4(),
    timeConfig = DfiTimeConfig(
      frequencyRatio = 2,  // 1:2 ratio for high performance
      cmdPhase = 0,
      tPhyWrLat = 3,
      tPhyWrData = 1,
      tPhyWrCsLat = 1,
      tPhyWrCsGap = 1,
      tRddataEn = 8,
      tPhyRdlat = 9,
      tPhyRdCslat = 1,
      tPhyRdCsGap = 1
    ),
    sdram = sdramConfig
  )

  // High-performance BMB configuration
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 64,  // 64-bit data bus
    lengthWidth = 6,
    sourceWidth = 4,
    contextWidth = 4,
    canRead = true,
    canWrite = true,
    alignment = BmbParameter.BurstAlignement.WORD
  )

  // Instantiate components
  val dfiController = DfiController(
    bmbp = bmbParameter,
    task = TaskParameter(
      bytePerTaskMax = 128,
      timingWidth = 8,
      refWidth = 16,
      cmdBufferSize = 16,
      dataBufferSize = 16,
      rspBufferSize = 16
    ),
    dfiConfig = dfiConfig,
    addrMap = BankRowColumn
  )

  val phy = DfiDdrPhy.ddr4(
    chipSelectNumber = chipCount,
    dataWidth = 32,
    sdramConfig = sdramConfig,
    features = DfiDdrPhyFeatures(
      dbiSupport = true,
      crcSupport = true,
      caParitySupport = true,
      trainingSupport = true,
      debugSupport = true
    )
  )

  // Connect interfaces
  phy.io.dfi <> dfiController.io.dfi

  // Expose interfaces
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = Vec(master(DdrInterface(sdramConfig)), chipCount)
    val status = out(phy.io.status)
    val training = master(phy.io.training)
  }

  io.bmb <> dfiController.io.bmb
  for (i <- 0 until chipCount) {
    io.sdram(i) <> phy.io.sdram(i)
  }
  io.status := phy.io.status
  io.training <> phy.io.training
}

/**
 * Demo 3: Low-Power LPDDR3 System
 * LPDDR3 with power management and training
 */
class LowPowerLpddr3Demo extends Component {
  // SDRAM configuration for LPDDR3-2133
  val sdramConfig = SdramConfig(
    generation = SdramGeneration.DDR3,  // LPDDR3 based on DDR3
    bgWidth = 0,
    cidWidth = 0,
    bankWidth = 3,
    columnWidth = 10,
    rowWidth = 14,
    dataWidth = 32,
    ddrMHZ = 533,  // LPDDR3-1066 (533MHz)
    ddrWrLat = 8,
    ddrRdLat = 10,
    sdramtime = SdramTiming(
      generation = 3,
      RFC = 210,
      RAS = 42,
      RP = 18,
      RCD = 18,
      WTR = 8,
      WTP = 15,
      RTP = 8,
      RRD = 10,
      REF = 64,
      FAW = 50
    )
  )

  // DFI configuration
  val dfiConfig = DfiConfig(
    chipSelectNumber = 1,
    dataSlice = 4,
    signalConfig = DfiSignalConfig.DDR3(),
    timeConfig = DfiTimeConfig(
      frequencyRatio = 2,
      cmdPhase = 0,
      tPhyWrLat = 2,
      tPhyWrData = 0,
      tPhyWrCsLat = 0,
      tPhyWrCsGap = 0,
      tRddataEn = 7,
      tPhyRdlat = 8,
      tPhyRdCslat = 0,
      tPhyRdCsGap = 0
    ),
    sdram = sdramConfig
  )

  // Standard BMB configuration
  val bmbParameter = BmbParameter(
    addressWidth = 30,
    dataWidth = 32,
    lengthWidth = 6,
    sourceWidth = 0,
    contextWidth = 4,
    canRead = true,
    canWrite = true,
    alignment = BmbParameter.BurstAlignement.WORD
  )

  // Instantiate components
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

  val phy = new DfiDdrPhy(DfiDdrPhyConfig(
    ddrStandard = DdrStandard.LPDDR3,
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    features = DfiDdrPhyFeatures(
      dbiSupport = true,
      crcSupport = false,  // LPDDR3 may not support CRC
      caParitySupport = true,
      lowPowerSupport = true,
      trainingSupport = true,
      debugSupport = true
    )
  ))

  // Training controller for LPDDR3
  val trainingController = new Area {
    val trainingState = RegInit(U(0, 3 bits))

    // Simple training state machine
    switch(trainingState) {
      is(0) { // Idle
        when(phy.io.training.readTraining.req) {
          trainingState := 1
        }
      }
      is(1) { // Read training
        // Handle read training
        when(phy.io.training.readTraining.resp.orR) {
          trainingState := 2
        }
      }
      is(2) { // Write training
        when(phy.io.training.writeTraining.req.orR) {
          // Handle write training
          trainingState := 3
        }
      }
      is(3) { // Complete
        // Training complete
      }
    }
  }

  // Connect interfaces
  phy.io.dfi <> dfiController.io.dfi

  // Expose interfaces
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val status = out(phy.io.status)
    val training = master(phy.io.training)
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.status := phy.io.status
  io.training <> phy.io.training
}

/**
 * Demo 4: Multi-Standard Switching System
 * Demonstrates runtime DDR standard switching
 */
class MultiStandardSwitchingDemo extends Component {
  // Configuration for multiple standards
  val standards = Seq(DdrStandard.DDR3, DdrStandard.DDR4, DdrStandard.LPDDR3)

  // Base SDRAM config (will be modified per standard)
  val baseSdramConfig = SdramConfig(
    generation = SdramGeneration.DDR3,  // Default
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

  // DFI config
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
    sdram = baseSdramConfig
  )

  // BMB config
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

  // Controller
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

  // Standard selector
  val currentStandard = RegInit(DdrStandard.DDR3)

  // PHY with dynamic configuration
  val phy = new DfiDdrPhy(DfiDdrPhyConfig(
    ddrStandard = currentStandard,
    dfiConfig = dfiConfig,
    sdramConfig = baseSdramConfig,
    features = DfiDdrPhyFeatures(
      trainingSupport = true,
      debugSupport = true
    )
  ))

  // Standard switching logic
  val standardIndex = RegInit(U(0, 2 bits))
  val switchRequest = RegInit(False)

  when(switchRequest) {
    switch(standardIndex) {
      is(0) { currentStandard := DdrStandard.DDR3 }
      is(1) { currentStandard := DdrStandard.DDR4 }
      is(2) { currentStandard := DdrStandard.LPDDR3 }
    }
    standardIndex := standardIndex + 1
    switchRequest := False
  }

  // Connect interfaces
  phy.io.dfi <> dfiController.io.dfi

  // Expose interfaces
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val status = out(phy.io.status)
    val control = new Bundle {
      val switchStandard = in Bool()
      val currentStandard = out(DdrStandard())
    }
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.status := phy.io.status
  io.control.switchStandard <> switchRequest
  io.control.currentStandard := currentStandard
}

/**
 * Demo 5: Performance Monitoring System
 * DDR system with comprehensive performance monitoring
 */
class PerformanceMonitoringDemo extends Component {
  // Standard DDR3 configuration
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

  // Instantiate components
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

  val phy = DfiDdrPhy.ddr3(
    chipSelectNumber = 1,
    dataWidth = 16,
    sdramConfig = sdramConfig,
    features = DfiDdrPhyFeatures(debugSupport = true)
  )

  // Performance monitoring
  val performanceMonitor = new Area {
    val transactionCount = RegInit(U(0, 32 bits))
    val readCount = RegInit(U(0, 32 bits))
    val writeCount = RegInit(U(0, 32 bits))
    val errorCount = RegInit(U(0, 16 bits))
    val cycleCount = RegInit(U(0, 32 bits))

    // Counters
    cycleCount := cycleCount + 1

    when(dfiController.io.bmb.fire) {
      transactionCount := transactionCount + 1
      when(dfiController.io.bmb.wr) {
        writeCount := writeCount + 1
      } otherwise {
        readCount := readCount + 1
      }
    }

    when(phy.io.status.error) {
      errorCount := errorCount + 1
    }

    // Calculated metrics
    val bandwidthMBps = (transactionCount * U(4)) / (cycleCount / U(400))  // Approximate
    val errorRate = errorCount / transactionCount
    val readWriteRatio = readCount / writeCount
  }

  // Connect interfaces
  phy.io.dfi <> dfiController.io.dfi

  // Expose interfaces
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val status = out(phy.io.status)
    val performance = out(new Bundle {
      val transactionCount = UInt(32 bits)
      val readCount = UInt(32 bits)
      val writeCount = UInt(32 bits)
      val errorCount = UInt(16 bits)
      val bandwidth = UInt(32 bits)
    })
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.status := phy.io.status
  io.performance.transactionCount := performanceMonitor.transactionCount
  io.performance.readCount := performanceMonitor.readCount
  io.performance.writeCount := performanceMonitor.writeCount
  io.performance.errorCount := performanceMonitor.errorCount
  io.performance.bandwidth := performanceMonitor.bandwidthMBps
}

// Example usage in test
object DfiDdrPhyDemo {
  def main(args: Array[String]): Unit = {
    // Generate Verilog for basic DDR3 demo
    SpinalVerilog(new BasicDdr3Demo)

    // Generate Verilog for high-performance DDR4 demo
    SpinalVerilog(new HighPerformanceDdr4Demo)

    // Generate Verilog for low-power LPDDR3 demo
    SpinalVerilog(new LowPowerLpddr3Demo)

    // Generate Verilog for multi-standard switching demo
    SpinalVerilog(new MultiStandardSwitchingDemo)

    // Generate Verilog for performance monitoring demo
    SpinalVerilog(new PerformanceMonitoringDemo)
  }
}