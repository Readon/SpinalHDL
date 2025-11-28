package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._

/**
 * DDR Burst Pattern Generator Utility Class
 *
 * Generates various burst patterns for DDR3 memory validation including:
 * - BL4 (4-beat burst) patterns
 * - BL8 (8-beat burst) patterns
 * - BL16 (16-beat burst) patterns
 * - Sequential vs interleaved burst types
 * - Address alignment and boundary conditions
 * - Burst control patterns (interrupt, resume, terminate)
 */
class BurstPatternGenerator {

  // DDR3 burst configuration constants
  val DDR3_BL4_MIN = 4    // Minimum burst length for DDR3
  val DDR3_BL8_MIN = 8    // Standard burst length for DDR3
  val DDR3_BL16_MIN = 16   // Maximum burst length for DDR3
  val DDR3_BL_ALIGNMENT_MASK = 3  // 4-beat alignment requirement

  // Burst type enumeration
  object BurstType extends Enumeration {
    val SEQUENTIAL, INTERLEAVED = Value
  }

  /**
   * Burst operation information
   */
  case class BurstOperation(
    burstType: BurstType.Value,
    burstLength: Int,
    startAddress: BigInt,
    dataWidth: Int,
    isWrite: Boolean,
    beatCount: Int,
    addressPattern: List[BigInt],
    dataPattern: List[BigInt]
  ) {
    def isValid: Boolean = {
      burstLength == DDR3_BL4_MIN || burstLength == DDR3_BL8_MIN || burstLength == DDR3_BL16_MIN
    }

    def isAligned: Boolean = {
      val alignmentMask = burstLength - 1
      (startAddress & alignmentMask) == 0
    }

    def getBeatData(beatIndex: Int): BigInt = {
      if (beatIndex >= 0 && beatIndex < dataPattern.length) {
        dataPattern(beatIndex)
      } else {
        BigInt(0)
      }
    }

    def getBeatAddress(beatIndex: Int): BigInt = {
      if (beatIndex >= 0 && beatIndex < addressPattern.length) {
        addressPattern(beatIndex)
      } else {
        startAddress
      }
    }
  }

  /**
   * Burst violation information
   */
  case class BurstViolation(
    burstType: BurstType.Value,
    violationType: String,
    expectedBehavior: String,
    actualBehavior: String,
    timestamp: Long
  ) {
    def description: String = {
      s"Burst violation: type=$burstType, violation=$violationType, expected='$expectedBehavior', actual='$actualBehavior', time=$timestamp"
    }
  }

  /**
   * Address alignment requirements for different burst lengths
   */
  def getAlignmentRequirement(burstLength: Int): Int = {
    burstLength match {
      case DDR3_BL4_MIN => 4  // 4-beat alignment
      case DDR3_BL8_MIN => 8  // 8-beat alignment
      case DDR3_BL16_MIN => 16 // 16-beat alignment
      case _ => 4 // Default to 4-beat alignment
    }
  }

  /**
   * Generates sequential burst address pattern
   */
  def generateSequentialAddresses(startAddress: BigInt, burstLength: Int, dataWidth: Int): List[BigInt] = {
    val bytesPerBeat = dataWidth / 8
    List.tabulate(burstLength) { beatIndex =>
      startAddress + (beatIndex * bytesPerBeat)
    }
  }

  /**
   * Generates interleaved burst address pattern
   */
  def generateInterleavedAddresses(startAddress: BigInt, burstLength: Int, dataWidth: Int): List[BigInt] = {
    val bytesPerBeat = dataWidth / 8
    val burstSize = burstLength / 2
    List.tabulate(burstLength) { beatIndex =>
      if (beatIndex % 2 == 0) {
        // Even beats: sequential progression (first half)
        startAddress + ((beatIndex / 2) * bytesPerBeat)
      } else {
        // Odd beats: interleaved offset (second half)
        val interleavedOffset = burstSize * bytesPerBeat
        startAddress + ((((beatIndex - 1) / 2) * bytesPerBeat) + interleavedOffset)
      }
    }
  }

  /**
   * Generates data pattern for burst operations
   */
  def generateDataPattern(burstLength: Int, dataWidth: Int, pattern: String): List[BigInt] = {
    pattern match {
      case "sequential" =>
        List.tabulate(burstLength) { beatIndex =>
          BigInt(beatIndex + 1) << (dataWidth / 8) // Simple incremental pattern
        }

      case "alternating" =>
        List.tabulate(burstLength) { beatIndex =>
          if (beatIndex % 2 == 0) {
            BigInt("AA55AA55AA55AA55", 16)
          } else {
            BigInt("55AA55AA55AA55AA", 16)
          }
        }

      case "random" =>
        // Use a simple pseudo-random generator for testing
        val seed = 0x12345678L
        var currentSeed = seed
        List.tabulate(burstLength) { _ =>
          currentSeed = (currentSeed * 1103515245 + 12345) & 0x7FFFFFFF
          BigInt(currentSeed) << (dataWidth / 8)
        }

      case "walking_ones" =>
        List.tabulate(burstLength) { beatIndex =>
          BigInt(1) << (beatIndex % dataWidth)
        }

      case "walking_zeros" =>
        val allOnes = (BigInt(1) << dataWidth) - 1
        List.tabulate(burstLength) { beatIndex =>
          allOnes ^ (BigInt(1) << (beatIndex % dataWidth))
        }

      case "all_ones" =>
        List.fill(burstLength)((BigInt(1) << dataWidth) - 1)

      case "all_zeros" =>
        List.fill(burstLength)(BigInt(0))

      case _ =>
        List.fill(burstLength)(BigInt(0xdeadbeef))
    }
  }

  /**
   * Generates BL4 burst operation
   */
  def generateBL4Burst(
    startAddress: BigInt,
    dataWidth: Int,
    isWrite: Boolean,
    burstType: BurstType.Value = BurstType.SEQUENTIAL,
    dataPattern: String = "sequential"
  ): BurstOperation = {
    val burstLength = DDR3_BL4_MIN
    val alignment = getAlignmentRequirement(burstLength)
    val alignedAddress = startAddress & ~((alignment - 1).toBigInt)

    val addressPattern = burstType match {
      case BurstType.SEQUENTIAL => generateSequentialAddresses(alignedAddress, burstLength, dataWidth)
      case BurstType.INTERLEAVED => generateInterleavedAddresses(alignedAddress, burstLength, dataWidth)
    }

    val dataPat = generateDataPattern(burstLength, dataWidth, dataPattern)

    BurstOperation(
      burstType = burstType,
      burstLength = burstLength,
      startAddress = alignedAddress,
      dataWidth = dataWidth,
      isWrite = isWrite,
      beatCount = burstLength,
      addressPattern = addressPattern,
      dataPattern = dataPat
    )
  }

  /**
   * Generates BL8 burst operation
   */
  def generateBL8Burst(
    startAddress: BigInt,
    dataWidth: Int,
    isWrite: Boolean,
    burstType: BurstType.Value = BurstType.SEQUENTIAL,
    dataPattern: String = "sequential"
  ): BurstOperation = {
    val burstLength = DDR3_BL8_MIN
    val alignment = getAlignmentRequirement(burstLength)
    val alignedAddress = startAddress & ~((alignment - 1).toBigInt)

    val addressPattern = burstType match {
      case BurstType.SEQUENTIAL => generateSequentialAddresses(alignedAddress, burstLength, dataWidth)
      case BurstType.INTERLEAVED => generateInterleavedAddresses(alignedAddress, burstLength, dataWidth)
    }

    val dataPat = generateDataPattern(burstLength, dataWidth, dataPattern)

    BurstOperation(
      burstType = burstType,
      burstLength = burstLength,
      startAddress = alignedAddress,
      dataWidth = dataWidth,
      isWrite = isWrite,
      beatCount = burstLength,
      addressPattern = addressPattern,
      dataPattern = dataPat
    )
  }

  /**
   * Generates BL16 burst operation
   */
  def generateBL16Burst(
    startAddress: BigInt,
    dataWidth: Int,
    isWrite: Boolean,
    burstType: BurstType.Value = BurstType.SEQUENTIAL,
    dataPattern: String = "sequential"
  ): BurstOperation = {
    val burstLength = DDR3_BL16_MIN
    val alignment = getAlignmentRequirement(burstLength)
    val alignedAddress = startAddress & ~((alignment - 1).toBigInt)

    val addressPattern = burstType match {
      case BurstType.SEQUENTIAL => generateSequentialAddresses(alignedAddress, burstLength, dataWidth)
      case BurstType.INTERLEAVED => generateInterleavedAddresses(alignedAddress, burstLength, dataWidth)
    }

    val dataPat = generateDataPattern(burstLength, dataWidth, dataPattern)

    BurstOperation(
      burstType = burstType,
      burstLength = burstLength,
      startAddress = alignedAddress,
      dataWidth = dataWidth,
      isWrite = isWrite,
      beatCount = burstLength,
      addressPattern = addressPattern,
      dataPattern = dataPat
    )
  }

  /**
   * Generates burst with address alignment testing
   */
  def generateAlignedBurst(
    burstLength: Int,
    startAddress: BigInt,
    dataWidth: Int,
    isWrite: Boolean,
    burstType: BurstType.Value = BurstType.SEQUENTIAL,
    dataPattern: String = "sequential"
  ): BurstOperation = {
    val alignment = getAlignmentRequirement(burstLength)
    val alignedAddress = startAddress & ~((alignment - 1).toBigInt)

    val addressPattern = burstType match {
      case BurstType.SEQUENTIAL => generateSequentialAddresses(alignedAddress, burstLength, dataWidth)
      case BurstType.INTERLEAVED => generateInterleavedAddresses(alignedAddress, burstLength, dataWidth)
    }

    val dataPat = generateDataPattern(burstLength, dataWidth, dataPattern)

    BurstOperation(
      burstType = burstType,
      burstLength = burstLength,
      startAddress = alignedAddress,
      dataWidth = dataWidth,
      isWrite = isWrite,
      beatCount = burstLength,
      addressPattern = addressPattern,
      dataPattern = dataPat
    )
  }

  /**
   * Generates burst with address misalignment for testing error cases
   */
  def generateMisalignedBurst(
    burstLength: Int,
    startAddress: BigInt,
    dataWidth: Int,
    isWrite: Boolean,
    burstType: BurstType.Value = BurstType.SEQUENTIAL,
    dataPattern: String = "sequential"
  ): BurstOperation = {
    // Intentionally misalign the address
    val misalignedAddress = startAddress + 1

    val addressPattern = burstType match {
      case BurstType.SEQUENTIAL => generateSequentialAddresses(misalignedAddress, burstLength, dataWidth)
      case BurstType.INTERLEAVED => generateInterleavedAddresses(misalignedAddress, burstLength, dataWidth)
    }

    val dataPat = generateDataPattern(burstLength, dataWidth, dataPattern)

    BurstOperation(
      burstType = burstType,
      burstLength = burstLength,
      startAddress = misalignedAddress,
      dataWidth = dataWidth,
      isWrite = isWrite,
      beatCount = burstLength,
      addressPattern = addressPattern,
      dataPattern = dataPat
    )
  }

  /**
   * Validates burst operation for correctness
   */
  def validateBurstOperation(burstOp: BurstOperation): List[BurstViolation] = {
    val violations = scala.collection.mutable.ListBuffer[BurstViolation]()

    // Check burst length validity
    if (!burstOp.isValid) {
      violations += BurstViolation(
        burstType = burstOp.burstType,
        violationType = "INVALID_BURST_LENGTH",
        expectedBehavior = s"Burst length should be one of: ${DDR3_BL4_MIN}, ${DDR3_BL8_MIN}, ${DDR3_BL16_MIN}",
        actualBehavior = s"Actual burst length: ${burstOp.burstLength}",
        timestamp = System.nanoTime()
      )
    }

    // Check address alignment
    if (!burstOp.isAligned) {
      violations += BurstViolation(
        burstType = burstOp.burstType,
        violationType = "ADDRESS_MISALIGNMENT",
        expectedBehavior = s"Address should be aligned to ${getAlignmentRequirement(burstOp.burstLength)} bytes",
        actualBehavior = s"Actual address: ${burstOp.startAddress}",
        timestamp = System.nanoTime()
      )
    }

    // Check address pattern consistency
    val expectedAddressPattern = burstOp.burstType match {
      case BurstType.SEQUENTIAL => generateSequentialAddresses(burstOp.startAddress, burstOp.beatCount, burstOp.dataWidth)
      case BurstType.INTERLEAVED => generateInterleavedAddresses(burstOp.startAddress, burstOp.beatCount, burstOp.dataWidth)
    }

    if (burstOp.addressPattern != expectedAddressPattern) {
      violations += BurstViolation(
        burstType = burstOp.burstType,
        violationType = "ADDRESS_PATTERN_MISMATCH",
        expectedBehavior = s"Expected address pattern: ${expectedAddressPattern}",
        actualBehavior = s"Actual address pattern: ${burstOp.addressPattern}",
        timestamp = System.nanoTime()
      )
    }

    // Check data pattern length (for interrupted bursts, use beatCount instead of burstLength)
    val expectedDataLength = if (burstOp.beatCount != burstOp.burstLength) burstOp.beatCount else burstOp.burstLength
    if (burstOp.dataPattern.length != expectedDataLength) {
      violations += BurstViolation(
        burstType = burstOp.burstType,
        violationType = "DATA_PATTERN_LENGTH_MISMATCH",
        expectedBehavior = s"Data pattern should have ${expectedDataLength} beats",
        actualBehavior = s"Actual data pattern length: ${burstOp.dataPattern.length}",
        timestamp = System.nanoTime()
      )
    }

    violations.toList
  }

  /**
   * Generates burst control sequence for testing interruption/resumption
   */
  def generateBurstControlSequence(
    burstLength: Int,
    interruptionPoint: Int,
    resumePoint: Int,
    dataWidth: Int
  ): (BurstOperation, BurstOperation) = {
    val startAddress = BigInt(0x1000)
    val originalBurst = generateAlignedBurst(
      burstLength = burstLength,
      startAddress = startAddress,
      dataWidth = dataWidth,
      isWrite = false,
      burstType = BurstType.SEQUENTIAL,
      dataPattern = "sequential"
    )

    // Create resumed burst from interruption point
    val resumeAddress = startAddress + (resumePoint * dataWidth / 8)
    val remainingBeats = burstLength - resumePoint
    val resumedDataPattern = originalBurst.dataPattern.drop(resumePoint)

    // Generate resumed address pattern that continues from interruption point
    val resumedAddressPattern = List.tabulate(remainingBeats) { i =>
      resumeAddress + (i * dataWidth / 8)
    }

    val resumedBurst = BurstOperation(
      burstType = BurstType.SEQUENTIAL,
      burstLength = burstLength, // Keep full burst length for validity
      startAddress = resumeAddress,
      dataWidth = dataWidth,
      isWrite = false,
      beatCount = remainingBeats, // But actual beat count is reduced
      addressPattern = resumedAddressPattern,
      dataPattern = resumedDataPattern
    )

    (originalBurst, resumedBurst)
  }

  /**
   * Generates mixed burst patterns for comprehensive testing
   */
  def generateMixedBurstPatterns(dataWidth: Int): List[BurstOperation] = {
    val burstLengths = List(DDR3_BL4_MIN, DDR3_BL8_MIN, DDR3_BL16_MIN)
    val burstTypes = List(BurstType.SEQUENTIAL, BurstType.INTERLEAVED)
    val dataPatterns = List("sequential", "alternating", "random", "walking_ones")

    for {
      burstLength <- burstLengths
      burstType <- burstTypes
      dataPattern <- dataPatterns
      isWrite <- List(false, true) // Read and write
    } yield {
      val startAddress = BigInt(0x1000 + (burstLength * burstType.id * dataPattern.hashCode))
      generateAlignedBurst(
        burstLength = burstLength,
        startAddress = startAddress,
        dataWidth = dataWidth,
        isWrite = isWrite,
        burstType = burstType,
        dataPattern = dataPattern
      )
    }
  }

  /**
   * Generates boundary condition test cases
   */
  def generateBoundaryTestCases(dataWidth: Int): List[BurstOperation] = {
    val burstLengths = List(DDR3_BL4_MIN, DDR3_BL8_MIN, DDR3_BL16_MIN)

    burstLengths.flatMap { burstLength =>
      val alignment = getAlignmentRequirement(burstLength)

      List(
        // Aligned cases
        generateAlignedBurst(
          burstLength = burstLength,
          startAddress = BigInt(0x1000), // Aligned
          dataWidth = dataWidth,
          isWrite = false,
          burstType = BurstType.SEQUENTIAL
        ),

        generateAlignedBurst(
          burstLength = burstLength,
          startAddress = BigInt(0x2000), // Aligned
          dataWidth = dataWidth,
          isWrite = true,
          burstType = BurstType.SEQUENTIAL
        ),

        // Misaligned cases
        generateMisalignedBurst(
          burstLength = burstLength,
          startAddress = BigInt(0x1001), // Misaligned by 1
          dataWidth = dataWidth,
          isWrite = false,
          burstType = BurstType.SEQUENTIAL
        ),

        generateMisalignedBurst(
          burstLength = burstLength,
          startAddress = BigInt(0x1000 + alignment - 1), // Just before alignment boundary
          dataWidth = dataWidth,
          isWrite = true,
          burstType = BurstType.SEQUENTIAL
        ),

        // Interleaved cases
        generateAlignedBurst(
          burstLength = burstLength,
          startAddress = BigInt(0x3000), // Aligned
          dataWidth = dataWidth,
          isWrite = false,
          burstType = BurstType.INTERLEAVED
        ),

        generateAlignedBurst(
          burstLength = burstLength,
          startAddress = BigInt(0x4000), // Aligned
          dataWidth = dataWidth,
          isWrite = true,
          burstType = BurstType.INTERLEAVED
        )
      )
    }
  }

  /**
   * Gets burst information for validation
   */
  def getBurstInfo(burstOp: BurstOperation): Map[String, Any] = {
    Map(
      "burstType" -> burstOp.burstType,
      "burstLength" -> burstOp.burstLength,
      "startAddress" -> burstOp.startAddress,
      "dataWidth" -> burstOp.dataWidth,
      "isWrite" -> burstOp.isWrite,
      "beatCount" -> burstOp.beatCount,
      "isValid" -> burstOp.isValid,
      "isAligned" -> burstOp.isAligned,
      "alignmentRequirement" -> getAlignmentRequirement(burstOp.burstLength),
      "addressPattern" -> burstOp.addressPattern,
      "dataPattern" -> burstOp.dataPattern
    )
  }

  /**
   * Compares two burst operations for equivalence
   */
  def compareBurstOperations(burst1: BurstOperation, burst2: BurstOperation): Boolean = {
    burst1.burstType == burst2.burstType &&
    burst1.burstLength == burst2.burstLength &&
    burst1.startAddress == burst2.startAddress &&
    burst1.dataWidth == burst2.dataWidth &&
    burst1.isWrite == burst2.isWrite &&
    burst1.beatCount == burst2.beatCount &&
    burst1.addressPattern == burst2.addressPattern &&
    burst1.dataPattern == burst2.dataPattern
  }

  /**
   * Creates burst operation summary for reporting
   */
  def createBurstSummary(burstOp: BurstOperation): String = {
    val alignmentStatus = if (burstOp.isAligned) "ALIGNED" else "MISALIGNED"
    val validityStatus = if (burstOp.isValid) "VALID" else "INVALID"

    s"""Burst Operation Summary:
       |  Type: ${burstOp.burstType}
       |  Length: ${burstOp.burstLength} beats
       |  Start Address: 0x${burstOp.startAddress.toString(16)}
       |  Data Width: ${burstOp.dataWidth} bits
       |  Operation: ${if (burstOp.isWrite) "WRITE" else "READ"}
       |  Beat Count: ${burstOp.beatCount}
       |  Alignment: $alignmentStatus (${getAlignmentRequirement(burstOp.burstLength)} bytes required)
       |  Validity: $validityStatus
       |  Address Pattern: ${burstOp.addressPattern.map(addr => s"0x${addr.toString(16)}").mkString(", ")}
       |  Data Pattern: ${burstOp.dataPattern.map(data => s"0x${data.toString(16)}").mkString(", ")}""".stripMargin
  }
}