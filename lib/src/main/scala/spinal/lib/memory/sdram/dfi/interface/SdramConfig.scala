package spinal.lib.memory.sdram.dfi

import spinal.core._

object DqsType extends SpinalEnum {
  val SingleEnded, Differential = newElement()
}

class SdramGeneration(
    val RESETn: Boolean,
    val ODT: Boolean,
    val DQS: Boolean,
    val FAW: Boolean,
    val CCD: Int,
    val burstLength: Int,
    val dataRate: Int,
    val dqsType: DqsType.E
)

object SdramGeneration {
  val SDR = new SdramGeneration(
    RESETn = false,
    ODT = false,
    DQS = false,
    FAW = false,
    CCD = 1,
    burstLength = 1,
    dataRate = 1,
    dqsType = DqsType.SingleEnded
  )
  val DDR2 = new SdramGeneration(
    RESETn = false,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 2,
    burstLength = 4,
    dataRate = 2,
    dqsType = DqsType.SingleEnded
  )
  val DDR3 = new SdramGeneration(
    RESETn = true,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 4,
    burstLength = 8,
    dataRate = 2,
    dqsType = DqsType.SingleEnded
  )
  val DDR4 = new SdramGeneration(
    RESETn = true,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 4,
    burstLength = 8,
    dataRate = 2,
    dqsType = DqsType.Differential
  )
  val MYDDR = new SdramGeneration(
    RESETn = true,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 4,
    burstLength = 8,
    dataRate = 2,
    dqsType = DqsType.SingleEnded
  )
}

case class SdramConfig(
    generation: SdramGeneration,
    bgWidth: Int,
    cidWidth: Int,
    bankWidth: Int,
    columnWidth: Int,
    rowWidth: Int,
    dataWidth: Int,
    ddrMHZ: Int,
    ddrWrLat: Int,
    ddrRdLat: Int,
    sdramtime: SdramTiming
) {

  def burstLength = generation.burstLength
  def wordAddressWidth = bankWidth + columnWidth + rowWidth
  def chipAddressWidth = Math.max(columnWidth, rowWidth)
  def bankCount = 1 << bankWidth
  def capacity = BigInt(1) << byteAddressWidth
  def byteAddressWidth = bankWidth + columnWidth + rowWidth + log2Up(bytePerWord)
  def bytePerWord = dataWidth / 8
  def columnSize = 1 << columnWidth
  def ddrStartdelay = 600000 / (1000 / ddrMHZ) // 600uS

  def tREF = (sdramtime.REF * ddrMHZ) / rowSize
  def rowSize = 1 << rowWidth
  def tRCD = timeCycle(sdramtime.RCD, cycleTime_ns)
  def tRP = timeCycle(sdramtime.RP, cycleTime_ns)
  def tRFC = timeCycle(sdramtime.RFC, cycleTime_ns)
  def tWTR =
    math.max(timeCycle(sdramtime.WTR, cycleTime_ns), ddrWrLat + generation.burstLength / generation.dataRate + tWR)

  def tRTW = ddrRdLat + generation.burstLength / generation.dataRate + tWR

  def tWR = 5 + 1

  def tRAS = timeCycle(sdramtime.RAS, cycleTime_ns)

  def tRTP = math.max(timeCycle(sdramtime.RTP, cycleTime_ns), generation.burstLength / generation.dataRate)

  def tRRD = math.max(timeCycle(sdramtime.RRD, cycleTime_ns), generation.burstLength / generation.dataRate)

  def tFAW = timeCycle(sdramtime.FAW, cycleTime_ns)

  def timeCycle(time: Int, cycTime: Int) = (time + cycTime - 1) / cycTime

  def cycleTime_ns = 1000 / ddrMHZ

  def tPhyWrlat = ddrWrLat - 2
  def tRddataEn = ddrRdLat - 2
}

case class SdramTiming(
    generation: Int,
    RFC: Int, // ns // Command Period (REF to ACT)
    RAS: Int, // ns// Command Period (ACT to PRE)   Per bank
    RP: Int, // ns // Command Period (PRE to ACT)
    RCD: Int, // ns // Active Command To Read / Write Command Delay Time
    WTR: Int, // ns// WRITE to READ
    WTP: Int, // ns// WRITE to PRE (WRITE recovery time)
    RTP: Int, // ns// READ to PRE
    RRD: Int, // ns// ACT to ACT cross bank
    REF: Int, // us // Refresh Cycle Time (single row)
    FAW: Int
) //ns // Four ACTIVATE windows

// Example configurations
object SdramConfigExample {
  val ddr2Example = SdramConfig(
    generation = SdramGeneration.DDR2,
    bgWidth = 0, // DDR2 doesn't have bank groups
    cidWidth = 0,
    bankWidth = 3, // 8 banks
    columnWidth = 10,
    rowWidth = 15,
    dataWidth = 64,
    ddrMHZ = 400,
    ddrWrLat = 9,
    ddrRdLat = 9,
    sdramtime = SdramTiming(
      generation = 2,
      RFC = 127,
      RAS = 40,
      RP = 15,
      RCD = 15,
      WTR = 8,
      WTP = 15,
      RTP = 8,
      RRD = 10,
      REF = 64,
      FAW = 45
    )
  )

  val ddr3Example = SdramConfig(
    generation = SdramGeneration.DDR3,
    bgWidth = 0, // DDR3 doesn't have bank groups
    cidWidth = 0,
    bankWidth = 3, // 8 banks
    columnWidth = 10,
    rowWidth = 16,
    dataWidth = 64,
    ddrMHZ = 800,
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

  val ddr4Example = SdramConfig(
    generation = SdramGeneration.DDR4,
    bgWidth = 2, // 4 bank groups
    cidWidth = 0,
    bankWidth = 2, // 4 banks per group
    columnWidth = 10,
    rowWidth = 17,
    dataWidth = 64,
    ddrMHZ = 1600,
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
}
