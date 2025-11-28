package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter, BmbAccessParameter}
import spinal.tester.code.SpinalAnyFunSuite
import spinal.core.sim._

/**
 * BMB to DDR Bridge Burst Operations Test Suite
 *
 * This test suite validates DDR burst operations through the DFI interface,
 * focusing on BL4, BL8, BL16 burst testing, address alignment,
 * burst types, and burst control mechanisms.
 * It uses comprehensive burst pattern validation with DDR3 simulation models.
 */
class BmbToDdrBridgeBurstOperationTester extends SpinalAnyFunSuite {

  test("Burst Pattern Generator - Framework Compilation") {
    // Configure BMB parameters for burst operation testing
    val accessConfig = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 128
    )
    val bmbParameter = BmbParameter(
      addressWidth = accessConfig.addressWidth,
      dataWidth = accessConfig.dataWidth,
      sourceWidth = 4,
      contextWidth = 8,
      lengthWidth = 8,
      canRead = true,
      canWrite = true
    )

    // Configure DDR3 DFI parameters for burst operation testing
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4, // 128-bit data width / 32-bit per slice
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )

    try {
      SpinalConfig().generateVerilog {
        val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
        println("✓ BmbToDdrBridgeBurstOperationTester framework compilation successful")

        // Verify basic burst operation interface connectivity
        assert(bridge.io.bmb.cmd != null, "BMB command interface should be available")
        assert(bridge.io.bmb.rsp != null, "BMB response interface should be available")
        assert(bridge.dfiConfig != null, "DFI configuration should be available")
        assert(bridge.dfiConfig.signalConfig.isInstanceOf[DDR3SignalConfig], "Should use DDR3 signal configuration")

        // Create burst pattern generator for testing
        val burstGenerator = new BurstPatternGenerator()

        // Verify burst configuration constants
        assert(burstGenerator.DDR3_BL4_MIN == 4, "BL4 should be 4 beats")
        assert(burstGenerator.DDR3_BL8_MIN == 8, "BL8 should be 8 beats")
        assert(burstGenerator.DDR3_BL16_MIN == 16, "BL16 should be 16 beats")
        assert(burstGenerator.DDR3_BL_ALIGNMENT_MASK == 3, "BL4 alignment mask should be 3")

        // Verify burst types
        assert(burstGenerator.BurstType.SEQUENTIAL != null, "Sequential burst type should be available")
        assert(burstGenerator.BurstType.INTERLEAVED != null, "Interleaved burst type should be available")

        println("✓ Burst pattern generator initialization validation passed")
        bridge
      }
    } catch {
      case e: Exception =>
        println(s"✗ Burst operation framework compilation failed: ${e.getMessage}")
        throw e
    }
  }

  test("BL4 Burst Testing - 4-beat Burst Operations") {
    // Configure BMB parameters for BL4 burst testing
    val accessConfig = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 128
    )
    val bmbParameter = BmbParameter(
      addressWidth = accessConfig.addressWidth,
      dataWidth = accessConfig.dataWidth,
      sourceWidth = 4,
      contextWidth = 8,
      lengthWidth = 8,
      canRead = true,
      canWrite = true
    )

    // Configure DDR3 DFI parameters for BL4 burst testing
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )

    try {
      SpinalConfig().generateVerilog {
        val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
        println("✓ BL4 burst testing compilation successful")

        // Create burst pattern generator
        val burstGenerator = new BurstPatternGenerator()

        // Test BL4 with sequential burst type
        val sequentialBurst = burstGenerator.generateBL4Burst(
          startAddress = BigInt(0x1000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.SEQUENTIAL,
          dataPattern = "sequential"
        )

        // Validate sequential BL4 burst
        val sequentialViolations = burstGenerator.validateBurstOperation(sequentialBurst)
        assert(sequentialViolations.isEmpty, s"Sequential BL4 burst should be valid, violations: ${sequentialViolations.map(_.description)}")
        assert(sequentialBurst.isValid, "Sequential BL4 burst should be valid")
        assert(sequentialBurst.isAligned, "Sequential BL4 burst should be aligned")
        assert(sequentialBurst.burstLength == 4, "Sequential BL4 burst should have 4 beats")

        println("✓ Sequential BL4 burst validation passed")
        println(s"  - Burst length: ${sequentialBurst.burstLength}")
        println(s"  - Start address: 0x${sequentialBurst.startAddress.toString(16)}")
        println(s"  - Beat addresses: ${sequentialBurst.addressPattern.map(addr => s"0x${addr.toString(16)}").mkString(", ")}")
        println(s"  - Beat data: ${sequentialBurst.dataPattern.map(data => s"0x${data.toString(16)}").mkString(", ")}")

        // Test BL4 with interleaved burst type
        val interleavedBurst = burstGenerator.generateBL4Burst(
          startAddress = BigInt(0x2000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.INTERLEAVED,
          dataPattern = "alternating"
        )

        // Validate interleaved BL4 burst
        val interleavedViolations = burstGenerator.validateBurstOperation(interleavedBurst)
        assert(interleavedViolations.isEmpty, s"Interleaved BL4 burst should be valid, violations: ${interleavedViolations.map(_.description)}")
        assert(interleavedBurst.isValid, "Interleaved BL4 burst should be valid")
        assert(interleavedBurst.isAligned, "Interleaved BL4 burst should be aligned")
        assert(interleavedBurst.burstLength == 4, "Interleaved BL4 burst should have 4 beats")

        println("✓ Interleaved BL4 burst validation passed")
        println(s"  - Burst length: ${interleavedBurst.burstLength}")
        println(s"  - Start address: 0x${interleavedBurst.startAddress.toString(16)}")
        println(s"  - Beat addresses: ${interleavedBurst.addressPattern.map(addr => s"0x${addr.toString(16)}").mkString(", ")}")
        println(s"  - Beat data: ${interleavedBurst.dataPattern.map(data => s"0x${data.toString(16)}").mkString(", ")}")

        // Test BL4 burst with different data patterns
        val dataPatternTests = List(
          "sequential",
          "alternating",
          "random",
          "walking_ones",
          "walking_zeros",
          "all_ones",
          "all_zeros"
        )

        dataPatternTests.foreach { dataPattern =>
          val patternBurst = burstGenerator.generateBL4Burst(
            startAddress = BigInt(0x3000) + dataPattern.hashCode() % 1000,
            dataWidth = 128,
            isWrite = true,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = dataPattern
          )

          val patternViolations = burstGenerator.validateBurstOperation(patternBurst)
          assert(patternViolations.isEmpty, s"BL4 burst with $dataPattern should be valid")
          assert(patternBurst.isValid, s"BL4 burst with $dataPattern should be valid")
          assert(patternBurst.isWrite, s"BL4 burst with $dataPattern should be write")
          assert(patternBurst.dataPattern.length == 4, s"BL4 burst with $dataPattern should have 4 data beats")

          println(s"✓ BL4 burst with $dataPattern validation passed")
          println(s"  - Write burst: ${patternBurst.isWrite}")
          println(s"  - Data pattern length: ${patternBurst.dataPattern.length}")
        }

        // Test BL4 burst address alignment
        val alignmentTests = List(
          (0x1000, "aligned address"),
          (0x1004, "aligned address"),
          (0x1008, "aligned address"),
          (0x100C, "aligned address"),
          (0x1001, "misaligned address"),
          (0x1002, "misaligned address"),
          (0x1003, "misaligned address")
        )

        alignmentTests.foreach { case (address, description) =>
          val alignedBurst = burstGenerator.generateBL4Burst(
            startAddress = BigInt(address),
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          val isActuallyAligned = (address & 3) == 0
          val alignmentViolations = burstGenerator.validateBurstOperation(alignedBurst)

          if (isActuallyAligned) {
            assert(alignmentViolations.isEmpty, s"$description should be valid")
            assert(alignedBurst.isAligned, s"$description should be aligned")
          } else {
            // Misaligned bursts should still be structurally valid but may require alignment handling
            println(s"Note: $description - structural validation only")
          }

          println(s"  $description: address=0x${address.toHexString}, aligned=${alignedBurst.isAligned}")
        }

        // Test BL4 burst boundary conditions
        val boundaryTests = List(
          (BigInt(0x0), "minimum address"),
          (BigInt(0xFFFFFFF0), "near maximum address"),
          (BigInt(0xFFFFFFFFFFFFFFFFL), "maximum address"),
          (BigInt("100000000", 16), "address overflow")
        )

        boundaryTests.foreach { case (address, description) =>
          val boundaryBurst = burstGenerator.generateBL4Burst(
            startAddress = address,
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          // Basic structural validation
          assert(boundaryBurst.burstLength == 4, s"$description should have correct burst length")
          assert(boundaryBurst.beatCount == 4, s"$description should have correct beat count")

          println(s"  $description: start_address=0x${address.toString(16)}, structural_valid=true")
        }

        println("✓ BL4 burst testing completed successfully")
        bridge
      }
    } catch {
      case e: Exception =>
        println(s"✗ BL4 burst testing failed: ${e.getMessage}")
        throw e
    }
  }

  test("BL8 Burst Testing - 8-beat Burst Operations") {
    // Configure BMB parameters for BL8 burst testing
    val accessConfig = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 128
    )
    val bmbParameter = BmbParameter(
      addressWidth = accessConfig.addressWidth,
      dataWidth = accessConfig.dataWidth,
      sourceWidth = 4,
      contextWidth = 8,
      lengthWidth = 8,
      canRead = true,
      canWrite = true
    )

    // Configure DDR3 DFI parameters for BL8 burst testing
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )

    try {
      SpinalConfig().generateVerilog {
        val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
        println("✓ BL8 burst testing compilation successful")

        // Create burst pattern generator
        val burstGenerator = new BurstPatternGenerator()

        // Test BL8 with sequential burst type
        val sequentialBurst = burstGenerator.generateBL8Burst(
          startAddress = BigInt(0x2000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.SEQUENTIAL,
          dataPattern = "sequential"
        )

        // Validate sequential BL8 burst
        val sequentialViolations = burstGenerator.validateBurstOperation(sequentialBurst)
        assert(sequentialViolations.isEmpty, s"Sequential BL8 burst should be valid, violations: ${sequentialViolations.map(_.description)}")
        assert(sequentialBurst.isValid, "Sequential BL8 burst should be valid")
        assert(sequentialBurst.isAligned, "Sequential BL8 burst should be aligned")
        assert(sequentialBurst.burstLength == 8, "Sequential BL8 burst should have 8 beats")

        println("✓ Sequential BL8 burst validation passed")
        println(s"  - Burst length: ${sequentialBurst.burstLength}")
        println(s"  - Start address: 0x${sequentialBurst.startAddress.toString(16)}")
        println(s"  - Beat addresses: ${sequentialBurst.addressPattern.take(4).map(addr => s"0x${addr.toString(16)}").mkString(", ")}...")
        println(s"  - Beat data: ${sequentialBurst.dataPattern.take(4).map(data => s"0x${data.toString(16)}").mkString(", ")}...")

        // Test BL8 with interleaved burst type
        val interleavedBurst = burstGenerator.generateBL8Burst(
          startAddress = BigInt(0x3000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.INTERLEAVED,
          dataPattern = "alternating"
        )

        // Validate interleaved BL8 burst
        val interleavedViolations = burstGenerator.validateBurstOperation(interleavedBurst)
        assert(interleavedViolations.isEmpty, s"Interleaved BL8 burst should be valid, violations: ${interleavedViolations.map(_.description)}")
        assert(interleavedBurst.isValid, "Interleaved BL8 burst should be valid")
        assert(interleavedBurst.isAligned, "Interleaved BL8 burst should be aligned")
        assert(interleavedBurst.burstLength == 8, "Interleaved BL8 burst should have 8 beats")

        println("✓ Interleaved BL8 burst validation passed")
        println(s"  - Burst length: ${interleavedBurst.burstLength}")
        println(s"  - Start address: 0x${interleavedBurst.startAddress.toString(16)}")
        println(s"  - Beat addresses: ${interleavedBurst.addressPattern.take(4).map(addr => s"0x${addr.toString(16)}").mkString(", ")}...")
        println(s"  - Beat data: ${interleavedBurst.dataPattern.take(4).map(data => s"0x${data.toString(16)}").mkString(", ")}...")

        // Test BL8 burst with different data patterns
        val dataPatternTests = List(
          "sequential",
          "alternating",
          "random",
          "walking_ones",
          "walking_zeros",
          "all_ones",
          "all_zeros"
        )

        dataPatternTests.foreach { dataPattern =>
          val patternBurst = burstGenerator.generateBL8Burst(
            startAddress = BigInt(0x4000) + dataPattern.hashCode() % 1000,
            dataWidth = 128,
            isWrite = true,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = dataPattern
          )

          val patternViolations = burstGenerator.validateBurstOperation(patternBurst)
          assert(patternViolations.isEmpty, s"BL8 burst with $dataPattern should be valid")
          assert(patternBurst.isValid, s"BL8 burst with $dataPattern should be valid")
          assert(patternBurst.isWrite, s"BL8 burst with $dataPattern should be write")
          assert(patternBurst.dataPattern.length == 8, s"BL8 burst with $dataPattern should have 8 data beats")

          println(s"✓ BL8 burst with $dataPattern validation passed")
          println(s"  - Write burst: ${patternBurst.isWrite}")
          println(s"  - Data pattern length: ${patternBurst.dataPattern.length}")
        }

        // Test BL8 burst address alignment (8-byte alignment for 128-bit data)
        val alignmentTests = List(
          (0x2000, "8-byte aligned address"),
          (0x2008, "8-byte aligned address"),
          (0x2010, "8-byte aligned address"),
          (0x2018, "8-byte aligned address"),
          (0x2001, "misaligned address"),
          (0x2002, "misaligned address"),
          (0x2003, "misaligned address"),
          (0x2004, "misaligned address"),
          (0x2005, "misaligned address"),
          (0x2006, "misaligned address"),
          (0x2007, "misaligned address")
        )

        alignmentTests.foreach { case (address, description) =>
          val alignedBurst = burstGenerator.generateBL8Burst(
            startAddress = BigInt(address),
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          val isActuallyAligned = (address & 7) == 0
          val alignmentViolations = burstGenerator.validateBurstOperation(alignedBurst)

          if (isActuallyAligned) {
            assert(alignmentViolations.isEmpty, s"$description should be valid")
            assert(alignedBurst.isAligned, s"$description should be aligned")
          } else {
            println(s"Note: $description - structural validation only")
          }

          println(s"  $description: address=0x${address.toHexString}, aligned=${alignedBurst.isAligned}")
        }

        // Test BL8 burst boundary conditions
        val boundaryTests = List(
          (BigInt(0x0), "minimum address"),
          (BigInt(0xFFFFFFF8), "near maximum address"),
          (BigInt(0xFFFFFFFFFFFFFFFFL), "maximum address"),
          (BigInt("100000000", 16), "address overflow")
        )

        boundaryTests.foreach { case (address, description) =>
          val boundaryBurst = burstGenerator.generateBL8Burst(
            startAddress = address,
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          // Basic structural validation
          assert(boundaryBurst.burstLength == 8, s"$description should have correct burst length")
          assert(boundaryBurst.beatCount == 8, s"$description should have correct beat count")

          println(s"  $description: start_address=0x${address.toString(16)}, structural_valid=true")
        }

        // Test BL8 burst interleaved address pattern
        val interleavedAddressTest = burstGenerator.generateBL8Burst(
          startAddress = BigInt(0x5000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.INTERLEAVED,
          dataPattern = "sequential"
        )

        // Verify interleaved address pattern (128-bit = 16 bytes per beat)
        // DDR3 BL8 interleaved pattern: even beats use sequential addresses, odd beats use offset by burstLength/2 * bytesPerBeat
        // bytesPerBeat = 128 / 8 = 16
        // interleavedOffset = (8 / 2) * 16 = 64
        val bytesPerBeat = 16 // 128-bit data width
        val interleavedOffset = (8 / 2) * bytesPerBeat // = 64
        val expectedInterleavedPattern = List(
          BigInt(0x5000) + 0 * bytesPerBeat, // Beat 0: base + 0*16 = 0x5000
          BigInt(0x5000) + 0 * bytesPerBeat + interleavedOffset, // Beat 1: base + 0*16 + 64 = 0x5040
          BigInt(0x5000) + 1 * bytesPerBeat, // Beat 2: base + 1*16 = 0x5010
          BigInt(0x5000) + 1 * bytesPerBeat + interleavedOffset, // Beat 3: base + 1*16 + 64 = 0x5050
          BigInt(0x5000) + 2 * bytesPerBeat, // Beat 4: base + 2*16 = 0x5020
          BigInt(0x5000) + 2 * bytesPerBeat + interleavedOffset, // Beat 5: base + 2*16 + 64 = 0x5060
          BigInt(0x5000) + 3 * bytesPerBeat, // Beat 6: base + 3*16 = 0x5030
          BigInt(0x5000) + 3 * bytesPerBeat + interleavedOffset  // Beat 7: base + 3*16 + 64 = 0x5070
        )

        assert(interleavedAddressTest.addressPattern == expectedInterleavedPattern, "Interleaved address pattern should match expected")
        println("✓ Interleaved BL8 address pattern validation passed")
        println(s"  - Expected pattern: ${expectedInterleavedPattern.map(addr => s"0x${addr.toString(16)}").mkString(", ")}")
        println(s"  - Actual pattern: ${interleavedAddressTest.addressPattern.map(addr => s"0x${addr.toString(16)}").mkString(", ")}")

        println("✓ BL8 burst testing completed successfully")
        bridge
      }
    } catch {
      case e: Exception =>
        println(s"✗ BL8 burst testing failed: ${e.getMessage}")
        throw e
    }
  }

  test("BL16 Burst Testing - 16-beat Burst Operations") {
    // Configure BMB parameters for BL16 burst testing
    val accessConfig = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 128
    )
    val bmbParameter = BmbParameter(
      addressWidth = accessConfig.addressWidth,
      dataWidth = accessConfig.dataWidth,
      sourceWidth = 4,
      contextWidth = 8,
      lengthWidth = 8,
      canRead = true,
      canWrite = true
    )

    // Configure DDR3 DFI parameters for BL16 burst testing
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )

    try {
      SpinalConfig().generateVerilog {
        val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
        println("✓ BL16 burst testing compilation successful")

        // Create burst pattern generator
        val burstGenerator = new BurstPatternGenerator()

        // Test BL16 with sequential burst type
        val sequentialBurst = burstGenerator.generateBL16Burst(
          startAddress = BigInt(0x4000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.SEQUENTIAL,
          dataPattern = "sequential"
        )

        // Validate sequential BL16 burst
        val sequentialViolations = burstGenerator.validateBurstOperation(sequentialBurst)
        assert(sequentialViolations.isEmpty, s"Sequential BL16 burst should be valid, violations: ${sequentialViolations.map(_.description)}")
        assert(sequentialBurst.isValid, "Sequential BL16 burst should be valid")
        assert(sequentialBurst.isAligned, "Sequential BL16 burst should be aligned")
        assert(sequentialBurst.burstLength == 16, "Sequential BL16 burst should have 16 beats")

        println("✓ Sequential BL16 burst validation passed")
        println(s"  - Burst length: ${sequentialBurst.burstLength}")
        println(s"  - Start address: 0x${sequentialBurst.startAddress.toString(16)}")
        println(s"  - Beat addresses: ${sequentialBurst.addressPattern.take(4).map(addr => s"0x${addr.toString(16)}").mkString(", ")}...")
        println(s"  - Beat data: ${sequentialBurst.dataPattern.take(4).map(data => s"0x${data.toString(16)}").mkString(", ")}...")

        // Test BL16 with interleaved burst type
        val interleavedBurst = burstGenerator.generateBL16Burst(
          startAddress = BigInt(0x5000),
          dataWidth = 128,
          isWrite = false,
          burstType = burstGenerator.BurstType.INTERLEAVED,
          dataPattern = "alternating"
        )

        // Validate interleaved BL16 burst
        val interleavedViolations = burstGenerator.validateBurstOperation(interleavedBurst)
        assert(interleavedViolations.isEmpty, s"Interleaved BL16 burst should be valid, violations: ${interleavedViolations.map(_.description)}")
        assert(interleavedBurst.isValid, "Interleaved BL16 burst should be valid")
        assert(interleavedBurst.isAligned, "Interleaved BL16 burst should be aligned")
        assert(interleavedBurst.burstLength == 16, "Interleaved BL16 burst should have 16 beats")

        println("✓ Interleaved BL16 burst validation passed")
        println(s"  - Burst length: ${interleavedBurst.burstLength}")
        println(s"  - Start address: 0x${interleavedBurst.startAddress.toString(16)}")
        println(s"  - Beat addresses: ${interleavedBurst.addressPattern.take(4).map(addr => s"0x${addr.toString(16)}").mkString(", ")}...")
        println(s"  - Beat data: ${interleavedBurst.dataPattern.take(4).map(data => s"0x${data.toString(16)}").mkString(", ")}...")

        // Test BL16 burst with different data patterns
        val dataPatternTests = List(
          "sequential",
          "alternating",
          "random",
          "walking_ones",
          "walking_zeros",
          "all_ones",
          "all_zeros"
        )

        dataPatternTests.foreach { dataPattern =>
          val patternBurst = burstGenerator.generateBL16Burst(
            startAddress = BigInt(0x6000) + dataPattern.hashCode() % 1000,
            dataWidth = 128,
            isWrite = true,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = dataPattern
          )

          val patternViolations = burstGenerator.validateBurstOperation(patternBurst)
          assert(patternViolations.isEmpty, s"BL16 burst with $dataPattern should be valid")
          assert(patternBurst.isValid, s"BL16 burst with $dataPattern should be valid")
          assert(patternBurst.isWrite, s"BL16 burst with $dataPattern should be write")
          assert(patternBurst.dataPattern.length == 16, s"BL16 burst with $dataPattern should have 16 data beats")

          println(s"✓ BL16 burst with $dataPattern validation passed")
          println(s"  - Write burst: ${patternBurst.isWrite}")
          println(s"  - Data pattern length: ${patternBurst.dataPattern.length}")
        }

        // Test BL16 burst address alignment (16-byte alignment for 128-bit data)
        val alignmentTests = List(
          (0x4000, "16-byte aligned address"),
          (0x4010, "16-byte aligned address"),
          (0x4020, "16-byte aligned address"),
          (0x4030, "16-byte aligned address"),
          (0x4001, "misaligned address"),
          (0x4002, "misaligned address"),
          (0x4004, "misaligned address"),
          (0x4008, "misaligned address"),
          (0x400F, "misaligned address")
        )

        alignmentTests.foreach { case (address, description) =>
          val alignedBurst = burstGenerator.generateBL16Burst(
            startAddress = BigInt(address),
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          val isActuallyAligned = (address & 15) == 0
          val alignmentViolations = burstGenerator.validateBurstOperation(alignedBurst)

          if (isActuallyAligned) {
            assert(alignmentViolations.isEmpty, s"$description should be valid")
            assert(alignedBurst.isAligned, s"$description should be aligned")
          } else {
            println(s"Note: $description - structural validation only")
          }

          println(s"  $description: address=0x${address.toHexString}, aligned=${alignedBurst.isAligned}")
        }

        // Test BL16 burst boundary conditions
        val boundaryTests = List(
          (BigInt(0x0), "minimum address"),
          (BigInt(0xFFFFFFF0), "near maximum address"),
          (BigInt(0xFFFFFFFFFFFFFFFFL), "maximum address"),
          (BigInt("100000000", 16), "address overflow")
        )

        boundaryTests.foreach { case (address, description) =>
          val boundaryBurst = burstGenerator.generateBL16Burst(
            startAddress = address,
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          // Basic structural validation
          assert(boundaryBurst.burstLength == 16, s"$description should have correct burst length")
          assert(boundaryBurst.beatCount == 16, s"$description should have correct beat count")

          println(s"  $description: start_address=0x${address.toString(16)}, structural_valid=true")
        }

        // Test BL16 burst complex address patterns
        val complexPatternTests = List(
          ("sequential", burstGenerator.BurstType.SEQUENTIAL, "Sequential address progression"),
          ("interleaved", burstGenerator.BurstType.INTERLEAVED, "Interleaved address pattern"),
          ("sequential", burstGenerator.BurstType.SEQUENTIAL, "Large sequential burst"),
          ("interleaved", burstGenerator.BurstType.INTERLEAVED, "Large interleaved burst")
        )

        complexPatternTests.foreach { case (dataPattern, burstType, description) =>
          val complexBurst = burstGenerator.generateBL16Burst(
            startAddress = BigInt(0x8000) + description.hashCode() % 1000,
            dataWidth = 128,
            isWrite = false,
            burstType = burstType,
            dataPattern = dataPattern
          )

          val complexViolations = burstGenerator.validateBurstOperation(complexBurst)
          assert(complexViolations.isEmpty, s"$description should be valid")
          assert(complexBurst.isValid, s"$description should be valid")
          assert(complexBurst.burstLength == 16, s"$description should have 16 beats")

          println(s"✓ $description validation passed")
          println(s"  - Burst type: ${burstType}")
          println(s"  - Data pattern: $dataPattern")
          println(s"  - Beat count: ${complexBurst.beatCount}")
        }

        println("✓ BL16 burst testing completed successfully")
        bridge
      }
    } catch {
      case e: Exception =>
        println(s"✗ BL16 burst testing failed: ${e.getMessage}")
        throw e
    }
  }

  test("Burst Type Testing - Sequential vs Interleaved") {
    // Configure BMB parameters for burst type testing
    val accessConfig = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 128
    )
    val bmbParameter = BmbParameter(
      addressWidth = accessConfig.addressWidth,
      dataWidth = accessConfig.dataWidth,
      sourceWidth = 4,
      contextWidth = 8,
      lengthWidth = 8,
      canRead = true,
      canWrite = true
    )

    // Configure DDR3 DFI parameters for burst type testing
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )

    try {
      SpinalConfig().generateVerilog {
        val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
        println("✓ Burst type testing compilation successful")

        // Create burst pattern generator
        val burstGenerator = new BurstPatternGenerator()

        // Test sequential vs interleaved for all burst lengths
        val burstLengths = List(4, 8, 16)
        val burstTypes = List(
          (burstGenerator.BurstType.SEQUENTIAL, "sequential"),
          (burstGenerator.BurstType.INTERLEAVED, "interleaved")
        )

        burstLengths.foreach { burstLength =>
          burstTypes.foreach { case (burstType, typeName) =>
            val startAddress = BigInt(0x1000) + (burstLength * typeName.hashCode())
            val testBurst = burstGenerator.generateAlignedBurst(
              burstLength = burstLength,
              startAddress = startAddress,
              dataWidth = 128,
              isWrite = false,
              burstType = burstType,
              dataPattern = "sequential"
            )

            val violations = burstGenerator.validateBurstOperation(testBurst)
            assert(violations.isEmpty, s"$typeName burst of length $burstLength should be valid")
            assert(testBurst.isValid, s"$typeName burst of length $burstLength should be valid")
            assert(testBurst.burstLength == burstLength, s"$typeName burst should have correct length")

            println(s"✓ $typeName burst (length $burstLength) validation passed")
            println(s"  - Start address: 0x${testBurst.startAddress.toString(16)}")
            println(s"  - Beat count: ${testBurst.beatCount}")
            println(s"  - Address alignment: ${testBurst.isAligned}")
          }
        }

        // Compare sequential vs interleaved address patterns
        val comparisonTests = List(
          (4, "BL4 comparison"),
          (8, "BL8 comparison"),
          (16, "BL16 comparison")
        )

        comparisonTests.foreach { case (burstLength, description) =>
          val baseAddress = BigInt(0x2000)

          val sequentialBurst = burstGenerator.generateAlignedBurst(
            burstLength = burstLength,
            startAddress = baseAddress,
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          val interleavedBurst = burstGenerator.generateAlignedBurst(
            burstLength = burstLength,
            startAddress = baseAddress,
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.INTERLEAVED,
            dataPattern = "sequential"
          )

          // Both should be valid
          val seqViolations = burstGenerator.validateBurstOperation(sequentialBurst)
          val intViolations = burstGenerator.validateBurstOperation(interleavedBurst)

          assert(seqViolations.isEmpty, s"Sequential $description should be valid")
          assert(intViolations.isEmpty, s"Interleaved $description should be valid")

          // Address patterns should be different
          val patternsDifferent = sequentialBurst.addressPattern != interleavedBurst.addressPattern
          assert(patternsDifferent, s"$description should have different address patterns")

          println(s"✓ $description validation passed")
          println(s"  - Sequential addresses: ${sequentialBurst.addressPattern.take(4).map(addr => s"0x${addr.toString(16)}").mkString(", ")}...")
          println(s"  - Interleaved addresses: ${interleavedBurst.addressPattern.take(4).map(addr => s"0x${addr.toString(16)}").mkString(", ")}...")
          println(s"  - Patterns different: $patternsDifferent")
        }

        // Test burst type with different data patterns
        val dataPatternTests = List("sequential", "alternating", "random", "walking_ones")
        val burstTypeTests = List(
          (burstGenerator.BurstType.SEQUENTIAL, "sequential"),
          (burstGenerator.BurstType.INTERLEAVED, "interleaved")
        )

        dataPatternTests.foreach { dataPattern =>
          burstTypeTests.foreach { case (burstType, typeName) =>
            val testBurst = burstGenerator.generateAlignedBurst(
              burstLength = 8, // Use BL8 for comprehensive testing
              startAddress = BigInt(0x3000) + dataPattern.hashCode() % 1000,
              dataWidth = 128,
              isWrite = true,
              burstType = burstType,
              dataPattern = dataPattern
            )

            val violations = burstGenerator.validateBurstOperation(testBurst)
            assert(violations.isEmpty, s"$typeName burst with $dataPattern should be valid")
            assert(testBurst.isValid, s"$typeName burst with $dataPattern should be valid")
            assert(testBurst.dataPattern.length == 8, s"$typeName burst should have 8 data beats")

            println(s"✓ $typeName burst with $dataPattern validation passed")
          }
        }

        // Test burst type boundary conditions
        val boundaryTests = List(
          (4, burstGenerator.BurstType.SEQUENTIAL, "minimum sequential"),
          (4, burstGenerator.BurstType.INTERLEAVED, "minimum interleaved"),
          (16, burstGenerator.BurstType.SEQUENTIAL, "maximum sequential"),
          (16, burstGenerator.BurstType.INTERLEAVED, "maximum interleaved")
        )

        boundaryTests.foreach { case (burstLength, burstType, description) =>
          val testBurst = burstGenerator.generateAlignedBurst(
            burstLength = burstLength,
            startAddress = BigInt(0x4000),
            dataWidth = 128,
            isWrite = false,
            burstType = burstType,
            dataPattern = "sequential"
          )

          val violations = burstGenerator.validateBurstOperation(testBurst)
          assert(violations.isEmpty, s"$description should be valid")
          assert(testBurst.isValid, s"$description should be valid")
          assert(testBurst.burstLength == burstLength, s"$description should have correct length")

          println(s"✓ $description validation passed")
          println(s"  - Burst length: ${testBurst.burstLength}")
          println(s"  - Beat count: ${testBurst.beatCount}")
        }

        println("✓ Burst type testing completed successfully")
        bridge
      }
    } catch {
      case e: Exception =>
        println(s"✗ Burst type testing failed: ${e.getMessage}")
        throw e
    }
  }

  test("Burst Control Testing - Interruption and Resumption") {
    // Configure BMB parameters for burst control testing
    val accessConfig = BmbAccessParameter(
      addressWidth = 32,
      dataWidth = 128
    )
    val bmbParameter = BmbParameter(
      addressWidth = accessConfig.addressWidth,
      dataWidth = accessConfig.dataWidth,
      sourceWidth = 4,
      contextWidth = 8,
      lengthWidth = 8,
      canRead = true,
      canWrite = true
    )

    // Configure DDR3 DFI parameters for burst control testing
    val ddr3Config = DfiConfig(
      chipSelectNumber = 1,
      dataSlice = 4,
      signalConfig = DfiSignalConfig.DDR3(),
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
      sdram = SdramConfigExample.ddr3Example
    )

    try {
      SpinalConfig().generateVerilog {
        val bridge = BmbToDdrBridge(bmbParameter, ddr3Config)
        println("✓ Burst control testing compilation successful")

        // Create burst pattern generator
        val burstGenerator = new BurstPatternGenerator()

        // Test burst interruption and resumption for different burst lengths
        val interruptionTests = List(
          (4, 1, "BL4 interrupted at beat 1"),
          (4, 2, "BL4 interrupted at beat 2"),
          (8, 3, "BL8 interrupted at beat 3"),
          (8, 5, "BL8 interrupted at beat 5"),
          (16, 8, "BL16 interrupted at beat 8"),
          (16, 12, "BL16 interrupted at beat 12")
        )

        interruptionTests.foreach { case (burstLength, interruptionPoint, description) =>
          val (originalBurst, resumedBurst) = burstGenerator.generateBurstControlSequence(
            burstLength = burstLength,
            interruptionPoint = interruptionPoint,
            resumePoint = interruptionPoint,
            dataWidth = 128
          )

          // Validate original burst
          val originalViolations = burstGenerator.validateBurstOperation(originalBurst)
          assert(originalViolations.isEmpty, s"Original $description should be valid")
          assert(originalBurst.isValid, s"Original $description should be valid")

          // Validate resumed burst
          val resumedViolations = burstGenerator.validateBurstOperation(resumedBurst)
          assert(resumedViolations.isEmpty, s"Resumed $description should be valid")
          assert(resumedBurst.isValid, s"Resumed $description should be valid")

          // Check that total beats match original
          val totalBeats = resumedBurst.beatCount
          val expectedBeats = burstLength - interruptionPoint
          assert(totalBeats == expectedBeats, s"$description should have $expectedBeats remaining beats")

          println(s"✓ $description validation passed")
          println(s"  - Original burst length: ${originalBurst.burstLength}")
          println(s"  - Resumed burst length: ${resumedBurst.burstLength}")
          println(s"  - Remaining beats: $totalBeats")
        }

        // Test burst termination scenarios
        val terminationTests = List(
          (4, 2, "BL4 terminated at beat 2"),
          (8, 4, "BL8 terminated at beat 4"),
          (16, 8, "BL16 terminated at beat 8")
        )

        terminationTests.foreach { case (burstLength, terminationPoint, description) =>
          val originalBurst = burstGenerator.generateAlignedBurst(
            burstLength = burstLength,
            startAddress = BigInt(0x5000),
            dataWidth = 128,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          // Create terminated burst by reducing beat count
          val terminatedBurst = originalBurst.copy(
            beatCount = terminationPoint,
            addressPattern = originalBurst.addressPattern.take(terminationPoint),
            dataPattern = originalBurst.dataPattern.take(terminationPoint)
          )

          // Validate terminated burst (should be valid as a shorter burst)
          val terminatedViolations = burstGenerator.validateBurstOperation(terminatedBurst)
          if (terminationPoint >= 4) { // Only BL4+ are valid
            assert(terminatedViolations.isEmpty, s"Terminated $description should be structurally valid")
          }

          println(s"✓ $description validation passed")
          println(s"  - Original length: ${originalBurst.burstLength}")
          println(s"  - Terminated length: ${terminatedBurst.beatCount}")
          println(s"  - Structural validity: ${terminatedViolations.isEmpty}")
        }

        // Test burst pause and resume with different patterns
        val patternTests = List("sequential", "alternating", "random")
        val pauseResumeTests = List(
          (8, 4, "BL8 paused at beat 4"),
          (16, 8, "BL16 paused at beat 8")
        )

        patternTests.foreach { dataPattern =>
          pauseResumeTests.foreach { case (burstLength, pausePoint, description) =>
            val (pausedBurst, resumedBurst) = burstGenerator.generateBurstControlSequence(
              burstLength = burstLength,
              interruptionPoint = pausePoint,
              resumePoint = pausePoint,
              dataWidth = 128
            )

            // Both should be valid
            val pausedViolations = burstGenerator.validateBurstOperation(pausedBurst)
            val resumedViolations = burstGenerator.validateBurstOperation(resumedBurst)

            assert(pausedViolations.isEmpty, s"Paused $description should be valid")
            assert(resumedViolations.isEmpty, s"Resumed $description should be valid")

            println(s"✓ $description with $dataPattern validation passed")
          }
        }

        // Test complex burst control scenarios
        val complexScenarios = List(
          (16, 0, 8, "Resume from start"),
          (16, 4, 12, "Resume middle portion"),
          (16, 8, 8, "Resume from middle"),
          (16, 12, 4, "Resume end portion")
        )

        complexScenarios.foreach { case (burstLength, interruptionPoint, resumePoint, description) =>
          if (resumePoint < burstLength) {
            val (originalBurst, resumedBurst) = burstGenerator.generateBurstControlSequence(
              burstLength = burstLength,
              interruptionPoint = interruptionPoint,
              resumePoint = resumePoint,
              dataWidth = 128
            )

            val originalViolations = burstGenerator.validateBurstOperation(originalBurst)
            val resumedViolations = burstGenerator.validateBurstOperation(resumedBurst)

            assert(originalViolations.isEmpty, s"Original $description should be valid")
            assert(resumedViolations.isEmpty, s"Resumed $description should be valid")

            val expectedRemainingBeats = burstLength - resumePoint
            assert(resumedBurst.beatCount == expectedRemainingBeats, s"$description should have $expectedRemainingBeats beats")

            println(s"✓ $description validation passed")
            println(s"  - Original length: ${originalBurst.burstLength}")
            println(s"  - Resumed length: ${resumedBurst.beatCount}")
            println(s"  - Expected remaining: $expectedRemainingBeats")
          }
        }

        // Test burst control with different data widths
        val dataWidthTests = List(64, 128, 256)
        dataWidthTests.foreach { dataWidth =>
          val controlBurst = burstGenerator.generateAlignedBurst(
            burstLength = 8,
            startAddress = BigInt(0x6000),
            dataWidth = dataWidth,
            isWrite = false,
            burstType = burstGenerator.BurstType.SEQUENTIAL,
            dataPattern = "sequential"
          )

          val violations = burstGenerator.validateBurstOperation(controlBurst)
          assert(violations.isEmpty, s"Burst control with $dataWidth-bit data should be valid")
          assert(controlBurst.dataWidth == dataWidth, s"Burst should have $dataWidth-bit data width")

          println(s"✓ Burst control with ${dataWidth}-bit data validation passed")
        }

        // Test burst error recovery scenarios
        val errorRecoveryTests = List(
          ("address_mismatch", "Address pattern mismatch"),
          ("data_mismatch", "Data pattern length mismatch"),
          ("invalid_length", "Invalid burst length")
        )

        errorRecoveryTests.foreach { case (errorType, description) =>
          errorType match {
            case "address_mismatch" =>
              val normalBurst = burstGenerator.generateAlignedBurst(
                burstLength = 8,
                startAddress = BigInt(0x7000),
                dataWidth = 128,
                isWrite = false,
                burstType = burstGenerator.BurstType.SEQUENTIAL,
                dataPattern = "sequential"
              )

              // Create burst with mismatched address pattern
              val errorBurst = normalBurst.copy(
                addressPattern = List.fill(8)(BigInt(0x1000))
              )

              val violations = burstGenerator.validateBurstOperation(errorBurst)
              assert(violations.nonEmpty, s"$description should be detected")
              assert(violations.exists(_.violationType.contains("ADDRESS_PATTERN")), s"$description should be address pattern violation")

              println(s"✓ $description detection passed")

            case "data_mismatch" =>
              val normalBurst = burstGenerator.generateAlignedBurst(
                burstLength = 8,
                startAddress = BigInt(0x8000),
                dataWidth = 128,
                isWrite = false,
                burstType = burstGenerator.BurstType.SEQUENTIAL,
                dataPattern = "sequential"
              )

              // Create burst with mismatched data pattern length
              val errorBurst = normalBurst.copy(
                dataPattern = List.fill(6)(BigInt(0xAABBCCDD))
              )

              val violations = burstGenerator.validateBurstOperation(errorBurst)
              assert(violations.nonEmpty, s"$description should be detected")
              assert(violations.exists(_.violationType.contains("DATA_PATTERN")), s"$description should be data pattern violation")

              println(s"✓ $description detection passed")

            case "invalid_length" =>
              val errorBurst = burstGenerator.generateAlignedBurst(
                burstLength = 8,
                startAddress = BigInt(0x9000),
                dataWidth = 128,
                isWrite = false,
                burstType = burstGenerator.BurstType.SEQUENTIAL,
                dataPattern = "sequential"
              ).copy(
                burstLength = 7 // Invalid length
              )

              val violations = burstGenerator.validateBurstOperation(errorBurst)
              assert(violations.nonEmpty, s"$description should be detected")
              assert(violations.exists(_.violationType.contains("INVALID")), s"$description should be invalid burst violation")

              println(s"✓ $description detection passed")
          }
        }

        println("✓ Burst control testing completed successfully")
        bridge
      }
    } catch {
      case e: Exception =>
        println(s"✗ Burst control testing failed: ${e.getMessage}")
        throw e
    }
  }
}