package spinal.lib.memory.sdram.dfi

import spinal.core._

class SdramGeneration(
    val RESETn: Boolean,
    val ODT: Boolean,
    val DQS: Boolean,
    val FAW: Boolean,
    val CCD: Int,
    val burstLength: Int,
    val dataRate: Int,
    // Bank-group aware CAS-to-CAS (tCCD) in nCK, JESD79-4B Table 13.
    // 0 => collapse to CCD (no bank groups): DDR/DDR2/DDR3 keep exactly the old single-value behavior.
    val ccdL: Int = 0,
    val ccdS: Int = 0,
    // --- Protocol capability flags (ADR-0002) ---------------------------------------------
    // Consumers gate BEHAVIOR on these flags, never on `generation == SdramGeneration.XXX`
    // identity. Identity remains the right key only for generation-SPECIFIC PAYLOADS (e.g. the
    // DDR4 init step table), and that knowledge is centralized in one named home
    // (SdramInitStep.defaultFor), not scattered across builders.
    // Modern DFI command bus: ACT_n / BG / CID pins exist alongside RAS_n/CAS_n/WE_n
    // (DFI 5.2 §4.3, JESD79-4B Table 16). All-or-nothing: the three wires appear together.
    val hasModernCmdBus: Boolean = false,
    // DBI (data bus inversion): physical pin-set feature, enabled via mode registers
    // (JESD79-4B §4.11 MR5 A11/A12).
    val hasDbi: Boolean = false,
    // CA parity: the DRAM checks even parity over the CA pins (JESD79-4B §4.17 /
    // DFI 5.2 §4.12).
    val hasCaParity: Boolean = false
) {
  def tCCD_L = if (ccdL != 0) ccdL else CCD
  def tCCD_S = if (ccdS != 0) ccdS else CCD
}

object SdramGeneration {
  val SDR = new SdramGeneration(
    RESETn = false,
    ODT = false,
    DQS = false,
    FAW = false,
    CCD = 1,
    burstLength = 1,
    dataRate = 1
  )
  val DDR2 = new SdramGeneration(
    RESETn = false,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 2,
    burstLength = 4,
    dataRate = 2
  )
  val DDR3 = new SdramGeneration(
    RESETn = true,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 4,
    burstLength = 8,
    dataRate = 2
  )
  val MYDDR = new SdramGeneration(
    RESETn = true,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 4,
    burstLength = 8,
    dataRate = 2
  )
  // DDR4: fixed BL8, 2nCK data rate, bank-group aware timing (tCCD_L per the AC table:
  // 5 nCK @1600/1866, 6 @2133 — SdramConfig.tCCD_L picks it per operating frequency and the
  // init sequence programs MR6 (Table 13) with the same value; tCCD_S 4 nCK uniform, config
  // keeps 8 as margin). tRRD/tWTR L/S variants live in SdramTiming (ns-based, like all
  // other cross-bank timings in this codebase).
  val DDR4 = new SdramGeneration(
    RESETn = true,
    ODT = true,
    DQS = true,
    FAW = true,
    CCD = 4,
    burstLength = 8,
    dataRate = 2,
    ccdL = 5, // tCCD_L floor at the lowest AC bin (JESD79-4B p.189: 5 nCK @1600/1866, 6 @2133).
             // SdramConfig.tCCD_L lifts it per operating frequency; the init sequence
             // programs MR6 (Table 13) with the same value. (Was 4: below every bin floor.)
    ccdS = 8,  // tCCD_S = 4 nCK uniform (JESD79-4 timing-figure notes); 8 is a deliberate conservative margin
    hasModernCmdBus = true,
    hasDbi = true,
    hasCaParity = true
  )

  /** JESD79-4B AC table (p.189) tCCD_L floor in nCK, by DRAM CK frequency: 5 @ DDR4-1600/1866,
    * 6 @ DDR4-2133; Table 102 has a 2400 column (2666/3200 mostly TBD) with tCCD_L = 6 nCK,
    * so >=2400 keeps the strictest known floor (6). Sub-1600 (this repo's 200 MHz test point)
    * uses the 1600 value. */
  def ddr4TCCD_L_floor(dramMhz: Int): Int = if (dramMhz >= 1066) 6 else 5
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
  def ddrStartDelay = 600 * ddrMHZ // 600uS, exact for any ddrMHZ (was 600000/(1000/ddrMHZ), int-div wrong at non-divisor rates)

  def tREF = {
    val base = if (sdramtime.REF_NS != 0) timeCycle(sdramtime.REF_NS, cycleTime_ns)
    else (sdramtime.REF * ddrMHZ) / rowSize
    // Rate multiplier shortens tREFI (2x => /2, 4x => /4), generation-agnostic.
    // Integer division is exact for the intended rates (1/2/4).
    base / sdramtime.REF_RATE
  }
  def rowSize = 1 << rowWidth
  def tRCD = timeCycle(sdramtime.RCD, cycleTime_ns)
  def tRP = timeCycle(sdramtime.RP, cycleTime_ns)
  def tRFC = timeCycle(sdramtime.RFC, cycleTime_ns)
  // All timing values below are in MC clock cycles:
  // - timeCycle() converts ns -> MC cycles (cycleTime_ns = 1000/ddrMHZ ns)
  // - ddrWrLat/ddrRdLat/tWR are already expressed in MC clock cycles
  def tWTR =
    math.max(timeCycle(sdramtime.WTR, cycleTime_ns), ddrWrLat + generation.burstLength / generation.dataRate + tWR)

  /** tWTR_S: WRITE to READ across different bank groups (DDR4 only).
    * Falls back to tWTR when WTR_S is unset. Floor is the write burst itself, NOT tWTR:
    * the S variant must be allowed to be shorter than the same-group (L) variant.
    */
  def tWTR_S =
    if (sdramtime.WTR_S == 0) tWTR
    else math.max(timeCycle(sdramtime.WTR_S, cycleTime_ns), generation.burstLength / generation.dataRate)

  def tRTW = ddrRdLat + generation.burstLength / generation.dataRate + tWR

  /** Write recovery time (tWR) in MC clock cycles.
    * Derived from the user-supplied sdramtime.WTP (ns) when non-zero; otherwise
    * falls back to 15 ns: JESD79-4 (in-repo original) Note 2 gives WRmin[cycles] =
    * Roundup(tWR[ns]/tCK[ns]), so the ns value is the authority for DDR4. For DDR3/DDR2 the
    * in-repo specs are silent (ADR-0018), but both in-repo Micron models parameterize
    * TWR = 15000 ps, so the same fallback is the characterized value there.
    * (Was a hardcoded 6-cycle approximation that undershot the spec at >=DDR4-2400.)
    */
  def tWR = if (sdramtime.WTP != 0) timeCycle(sdramtime.WTP, cycleTime_ns) else timeCycle(15, cycleTime_ns)

  def tRAS = timeCycle(sdramtime.RAS, cycleTime_ns)

  def tRTP = math.max(math.max(timeCycle(sdramtime.RTP, cycleTime_ns), generation.burstLength / generation.dataRate), ddr4SpecFloorNck)

  def tRRD = math.max(math.max(timeCycle(sdramtime.RRD, cycleTime_ns), generation.burstLength / generation.dataRate), ddr4SpecFloorNck)

  /** Scheduled tCCD_L in nCK: DDR4 follows the AC table per operating frequency
    * (JESD79-4B p.189); Table 13 NOTE 1 requires MR6 to be programmed with this same value —
    * Ddr4InitSequencer applies the lift to the default init sequence. A generation-table value above
    * the bin floor (e.g. a custom SdramGeneration) wins; the standard DDR4 entry (4 nCK) is always
    * below it. Other generations
    * keep the generation-table value. */
  def tCCD_L = if (generation == SdramGeneration.DDR4) math.max(SdramGeneration.ddr4TCCD_L_floor(ddrMHZ), generation.tCCD_L) else generation.tCCD_L

  /** JESD79-4B AC floors (p.189-190) in nCK: tRRD_L (2K page — the strictest page size, this
    * DUT has no page-size knob) = Max(4 nCK, 7.5 ns @1600 / 6.4 ns @1866+); tRTP =
    * max(4 nCK, 7.5 ns) at all bins. 7.5 ns is the strictest constant in the table, so
    * max(4, ceil(7.5 ns/tCK)) meets or exceeds every bin's floor (tRRD_L at most 1 nCK
    * stricter than the per-bin table at 1866/2133 — legal: a scheduler floor, not a
    * programmed value). 0 for non-DDR4 generations (their floor stays the burst length). */
  private def ddr4SpecFloorNck: Int =
    if (generation == SdramGeneration.DDR4) math.max(4, math.ceil(7.5 / cycleTime_ns).toInt) else 0

  /** tRRD_S: ACT to ACT across different bank groups (DDR4 only).
    * Falls back to tRRD when RRD_S is unset; floor is the burst, not tRRD (S may be shorter).
    */
  def tRRD_S =
    if (sdramtime.RRD_S == 0) tRRD
    else math.max(timeCycle(sdramtime.RRD_S, cycleTime_ns), generation.burstLength / generation.dataRate)

  def tFAW = timeCycle(sdramtime.FAW, cycleTime_ns)

  // Floating-point ns->cycle conversion: integer division truncated the cycle time to 0
  // for ddrMHZ > 1000 (e.g. DDR4-3200 @1600 MHz) and caused divide-by-zero in timeCycle/
  // ddrStartDelay at elaboration. ceil() keeps the worst-case (largest) cycle count.

  def timeCycle(time: Int, cycTime: Double): Int = math.ceil(time.toDouble / cycTime).toInt

  def cycleTime_ns = 1000.0 / ddrMHZ

  // ddrWrLat/ddrRdLat are counted MC cycles from the MC-side command issue; the DFI data
  // window (tphy_wrdata/tRddataEn) is referenced to the PHY's command sampling, which trails
  // the issue by 2 clocks, hence the -2. Calibrated value validated end-to-end against the
  // Micron pin model in DfiE2eGen.
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
    FAW: Int,
    // DDR4 bank-group variants (ns). 0 => fall back to the shared value, so pre-DDR4
    // SdramTiming literals keep compiling and behave exactly as before.
    WTR_S: Int = 0, // tWTR_S: WRITE to READ, cross bank group
    RRD_S: Int = 0, // tRRD_S: ACT to ACT, cross bank group
    // Absolute refresh interval tREFI in ns (e.g. 7800). 0 => legacy per-row
    // derivation (REF us / rows), preserving pre-DDR4 behavior bit-for-bit.
    // Generation-agnostic: any SDRAM whose datasheet gives an absolute tREFI can use it.
    REF_NS: Int = 0,
    // Refresh rate multiplier, applied to whatever tREFI the base derivation produced.
    // Generation-agnostic scheduler knob: 1 = base rate, 2 = half tREFI, 4 = quarter.
    // (DDR4 gives it protocol meaning via MR4 temperature-controlled refresh, JESD79-4B
    // Table 24, where RFC must carry the mode's tRFC(min): 8Gb 1x=350, 2x=260, 4x=160.
    // Older generations can use the same knob for conservative margining.)
    // Default 1 keeps every pre-existing timing literal unchanged.
    REF_RATE: Int = 1
) //ns // Four ACTIVATE windows
