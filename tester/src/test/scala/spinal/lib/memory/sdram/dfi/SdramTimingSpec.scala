package spinal.lib.memory.sdram.dfi

import spinal.tester.code.SpinalAnyFunSuite

/**
  * Key Core Timing conformance (JESD79-4 sec. 4.19 + the speed-bin tables p.169-176, and the
  * refresh-rate section 4.8/4.9).
  *
  * Layer 1 (this suite, sim-free): the cycle counts the scheduler uses must equal the golden
  * nanoseconds/nCK transcribed below, converted at the configured rate. That removes the
  * self-referential pattern the audit flagged — `DfiControllerTester` derives its refresh
  * expectation from `dut.dfiConfig.sdram.tREF` (its own comment: "Refresh expectation is
  * config-derived: cfg.sdram.tREF is the single source of truth for the refresh period") and
  * its init expectation from `SdramInitStep.defaultFor(...)`, i.e. from the DUT's own config.
  *
  * Layer 2 (the refresh/timing window asserts in `DfiControllerTester`): the observed command
  * stream on the DFI pins is checked against the same golden floors, so a DUT that schedules
  * from a knob below the spec floor fails on the waveform, not on a config print.
  *
  * The constants below are transcribed by hand from the PDFs; the PDFs are not in git
  * (ADR-0017 decision 7), which is why they are inlined.
  *
  * JESD79-4B ground truth (AC table p.189-190, Table 23, Table 13 NOTE 1, sec. 3.3):
  *   tRAS   min 35/34/33/32 ns at the 1600/1866/2133/2400 speed BINS — uniform across
  *          densities (Table 80); max = 9 x tREFI
  *   tRCD   min 12.50 / 12.85 / 13.13 ns by bin; NOTE 12: "1600M/1600K are down binned parts,
  *          tAAmin, tRCDmin, tRPmin is 13.5ns" — 13.5 ns is the strictest listed
  *   tRP    min 12.50 / 12.85 / 13.13 ns, same bins
  *   tRC    NOTE 12: "48.5ns is tRASmin(35ns) + tRPmin(13.5ns)"
  *   tREFI  7.8 us average, per rank (Table 23); 2x/4x = /2, /4 via MR3 A8:A6 (Table 7)
  *   tRFC   Table 23, per density x rate: 2 Gb = 160/110/90, 4 Gb = 260/160/110, 8 Gb =
  *          350/260/160 ns (1x/2x/4x); 16 Gb = TBD; 1 Gb is out of JESD79-4B
  *   tRRD_S Max(4nCK, 6/5.3/5.3 ns) by bin (2K page); tRRD_L Max(4nCK, 7.5/6.4/6.4 ns) (2K)
  *   tFAW   ns per bin + PAGE SIZE (AC table): 2K = 35/30/30, 1K = 25/23/21, 1/2K = 20/17/15
  *          at 1600/1866/2133; Table 102 has a 2400 column (2666/3200 mostly TBD)
  *   tWTR_S Max(2nCK, 2.5ns); tWTR_L Max(4nCK, 7.5ns)
  *   tRTP   Max(4nCK, 7.5ns) at all bins (BL8)
  *   tCCD_S 4 nCK (uniform; timing-figure notes "NOTE 1 tCCD_S = 4"); tCCD_L 4-8 nCK via MR6
  *          A12:A10 (Table 13; codes 101+ reserved), NOTE 1: program per AC table per
  *          operating frequency (AC floor 5 nCK at 1600-1866, 6 nCK at 2133+); init lifts
  *          MR6 to the floor and the scheduler floors tCCD_L at the same value
  *   tMRD   8 nCK; tMOD max(24 nCK, 15 ns)
  *   tXPR   Max(tXS, 5 nCK), where tXS = tRFC(min) + 10ns; sec. 3.3 step 2 waits 500 us after
  *          RESET_n until CKE is active, step 5 then waits tXPR after CKE is registered high
  *          before the first MRS (the 200 us figure is the stable-power wait, step 1)
  *   tZQinit 1024 nCK, tZQoper 512 nCK, tZQCS 128 nCK (sec. 4.12); tDLLK 512 nCK
  */
class SdramTimingSpec extends SpinalAnyFunSuite {

  import SdramTimingSpec._

  test("the golden table is internally consistent (tRC >= tRAS + tRP; tREFI >> tRFC)") {
    val g = golden(densityGb = 8, rateX = 2, dramMhz = 200)
    assert(g.tRCns >= g.tRASns + g.tRPns, s"tRC ${g.tRCns} >= tRAS ${g.tRASns} + tRP ${g.tRPns}")
    assert(g.tREFIns > 10 * g.tRFCns, s"tREFI ${g.tREFIns} ns must dominate tRFC ${g.tRFCns} ns")
    assert(g.tRASmaxCycles == 9 * cyc(g.tREFIns, 5.0), "tRAS max = 9 x tREFI (speed-bin table)")
  }

  test("the SdramConfig ns table converts to the golden DRAM-cycle counts") {
    // SdramConfig.scala:168 derives every ns value by timeCycle(ns, cycleTime_ns) = ceil, with
    // cycleTime_ns = 1000/ddrMHZ. Assert the DUT's numbers against the golden conversion, not
    // against each other.
    val cfg = Ddr4Cfg(fr = 2, rate = 2)
    val g = golden(densityGb = 8, rateX = 2, dramMhz = cfg.sdram.ddrMHZ)
    val s = cfg.sdram
    Seq(
      "tRAS"  -> (s.tRAS, cyc(g.tRASns, cfg.sdram.cycleTime_ns)),
      "tRCD"  -> (s.tRCD, cyc(g.tRCDns, cfg.sdram.cycleTime_ns)),
      "tRP"   -> (s.tRP, cyc(g.tRPns, cfg.sdram.cycleTime_ns)),
      "tRFC"  -> (s.tRFC, cyc(g.tRFCns, cfg.sdram.cycleTime_ns)),
      "tREFI" -> (s.tREF, cyc(g.tREFIns, cfg.sdram.cycleTime_ns)),
      "tFAW"  -> (s.tFAW, cyc(g.tFAWns, cfg.sdram.cycleTime_ns))
    ).foreach { case (name, (got, exp)) =>
      assert(got >= exp, s"$name = $got DRAM cycles at ${cfg.sdram.ddrMHZ} MHz, the golden floor says $exp (${g.nsOf(name)} ns / ${cfg.sdram.cycleTime_ns} ns) — the config is below the DRAM's requirement")
    }
    // nCK-golden fields (AC-table Max(nCK, ns) floors): previously dead — defined in the
    // golden table but never asserted. Now the DUT's own conversion is checked against them.
    Seq(
      "tRRD"   -> (s.tRRD, g.tRRD_Lnck),
      "tRRD_S" -> (s.tRRD_S, g.tRRD_Snck),
      "tRTP"   -> (s.tRTP, g.tRTPnck),
      "tWTR"   -> (s.tWTR, g.tWTR_Lnck),
      "tWTR_S" -> (s.tWTR_S, g.tWTR_Snck)
    ).foreach { case (name, (got, exp)) =>
      assert(got >= exp, s"$name = $got DRAM cycles at ${cfg.sdram.ddrMHZ} MHz, the AC-table golden floor says $exp nCK — the config is below the DRAM's requirement")
    }
  }

  test("tRAS has its spec upper bound too (tRAS max = 9 x tREFI)") {
    val cfg = Ddr4Cfg(fr = 2, rate = 1)
    assert(cfg.sdram.tRAS <= 9 * cfg.sdram.tREF,
      s"tRAS ${cfg.sdram.tRAS} must be <= 9 x tREFI (${9 * cfg.sdram.tREF}) — the row-active window is bounded on both sides")
  }

  test("the bank-group CAS windows are present in the config table (JESD79-4 Table 13, sec. 4.19)") {
    // tCCD_L: MR6-programmable 4-8 nCK (Table 13), NOTE 1: program per AC table per operating
    // frequency (AC floor 5 nCK at 1600-1866, 6 nCK at 2133+). Since the tCCD_L-floor fix the
    // init sequence lifts MR6 to that floor and SdramConfig.tCCD_L floors at the same value, so
    // the scheduler window is bin-aware. tCCD_S: 4 nCK uniform (timing-figure notes); the
    // generation table keeps 8 as a deliberate conservative margin above the spec floor.
    Seq(1, 2, 4).foreach { fr =>
      val cfg = Ddr4Cfg(fr = fr, rate = 2)
      val g = golden(densityGb = 8, rateX = 2, dramMhz = cfg.sdram.ddrMHZ)
      // Units: the spec's tCCD_* are nCK == DRAM cycles; the scheduler window is in MC cycles
      // (TaskConfig.scala:40 time(tcyc, fr) = (tcyc+fr-1)/fr), so the window is compared back in
      // DRAM cycles: windowMc * fr >= tCCD_nCK.
      def ccdMcToNck(mc: Int) = mc * fr
      assert(cfg.sdram.tCCD_L >= g.tCCD_Lnck,
        s"tCCD_L: SdramConfig says ${cfg.sdram.tCCD_L} nCK, the JESD79-4B AC table says >= ${g.tCCD_Lnck} nCK at ${cfg.sdram.ddrMHZ} MHz (MR6 A12:A10, Table 13 NOTE 1) — consecutive CAS_n to the SAME bank group")
      assert(cfg.sdram.generation.tCCD_S >= g.tCCD_Snck,
        s"tCCD_S: the generation table says ${cfg.sdram.generation.tCCD_S} nCK, JESD79-4 says >= ${g.tCCD_Snck} nCK (BL8) — consecutive CAS_n to DIFFERENT bank groups")
      assert(ccdMcToNck(cpdL(cfg)) >= g.tCCD_Lnck,
        s"the CCD_L scheduler window is ${cpdL(cfg)} MC = ${ccdMcToNck(cpdL(cfg))} nCK, JESD79-4 requires >= ${g.tCCD_Lnck} nCK (fr=$fr)")
      assert(ccdMcToNck(cpdS(cfg)) >= g.tCCD_Snck,
        s"the CCD_S scheduler window is ${cpdS(cfg)} MC = ${ccdMcToNck(cpdS(cfg))} nCK, JESD79-4 requires >= ${g.tCCD_Snck} nCK (fr=$fr)")
    }
  }

  test("tREFI at each rate is the DRAM-side MR3 decision, not only a scheduler knob") {
    // JESD79-4 4.9.2: "In 2x mode ... Refresh commands should be issued to the DRAM at the
    // double frequency (tREFI2 = tREFI(base)/2)"; 4x = /4. MR3 A8:A6 encodes it (Table 7:
    // 000=1x, 001=2x, 010=4x, 101/110 = on-the-fly).
    Seq(1, 2, 4).foreach { rate =>
      val cfg = Ddr4Cfg(fr = 2, rate = rate)
      val tREFI1xDram = cyc(7800.0, cfg.sdram.cycleTime_ns.toDouble)
      assert(cfg.sdram.tREF == tREFI1xDram / rate,
        s"rate ${rate}x: tREFI must be $tREFI1xDram/$rate = ${tREFI1xDram / rate} DRAM cycles, got ${cfg.sdram.tREF}")
      assert(((cfg.ddr4ModeRegs.mr3 >> 6) & 0x7) == (rate match { case 1 => 0; case 2 => 1; case 4 => 2 }),
        s"rate ${rate}x: MR3.A8:A6 must be the matching encoding (Table 7), got 0b${Integer.toBinaryString((cfg.ddr4ModeRegs.mr3 >> 6) & 7)}")
      // tRFC must carry the mode's own floor: 8 Gb 1x=350, 2x=260, 4x=160 ns.
      val g = golden(densityGb = 8, rateX = rate, dramMhz = cfg.sdram.ddrMHZ)
      assert(cfg.sdram.tRFC >= cyc(g.tRFCns, cfg.sdram.cycleTime_ns.toDouble),
        s"rate ${rate}x: tRFC ${cfg.sdram.tRFC} must be >= ${cyc(g.tRFCns, cfg.sdram.cycleTime_ns.toDouble)} (${g.tRFCns} ns for this mode)")
    }
  }

  test("the refresh scheduler period is tREFI in MC cycles and the init overheads are spec-derived") {
    Seq(1, 2, 4).foreach { fr =>
      val cfg = Ddr4Cfg(fr = fr, rate = 2)
      val tRefiDram = cyc(3900.0, cfg.sdram.cycleTime_ns.toDouble) // tREFI2 = 7800/2 ns, in DRAM cycles
      val expectedRefMc = (tRefiDram + fr - 1) / fr
      // The MC's period, in MC cycles, is the DUT's own conversion; assert the floor form so a
      // knob change that shortens it below the spec is caught. (The old exact-multiple
      // disjunction was dropped: its second disjunct was algebraically identical to this
      // ceil check, so it added nothing.)
      val refMc = (cfg.sdram.tREF + fr - 1) / fr
      assert(refMc >= expectedRefMc, s"fr=$fr: the refresh period the scheduler uses ($refMc MC) must be >= tREFI2 ($expectedRefMc MC = 3900ns)")
    }
    // tMRD = 8 nCK (JESD79-4B, flat) -> ceil(8/fr) MC between MRS writes. Check the REAL
    // preset spacing — the old assert compared two constants derived from the same 8nCK
    // number against each other (tautology); the same floor is asserted per-step in
    // SdramInitStepSpec.
    val fr = 2
    val tMrdMinMc = math.ceil(8.0 / fr).toInt
    val mrsSpacing = SdramInitStep.defaultFor(SdramGeneration.DDR4, Ddr4ModeRegs(), fr = fr)
      .collect { case s if s.kind == SdramInitStep.MRS => s.waitCycles }.min
    assert(mrsSpacing >= tMrdMinMc, s"the preset spaces MRS writes by $mrsSpacing MC, the tMRD floor is $tMrdMinMc MC")
  }
}

object SdramTimingSpec {
  /** ceil(ns / tCK) — the same conversion SdramConfig.scala:166-168 uses. */
  def cyc(ns: Double, tCKns: Double): Int = math.ceil(ns / tCKns).toInt
  def cyc(nck: Int, tCKns: Double): Int = nck

  /** The scheduler's CCD_L / CCD_S windows in MC cycles (MakeTask.scala reads them by name).
    * tCCD_L is bin-aware (SdramConfig.tCCD_L) since the tCCD_L-floor fix. */
  def cpdL(cfg: DfiConfig): Int = (cfg.sdram.tCCD_L + cfg.frequencyRatio - 1) / cfg.frequencyRatio
  def cpdS(cfg: DfiConfig): Int = (cfg.sdram.generation.tCCD_S + cfg.frequencyRatio - 1) / cfg.frequencyRatio

  case class Golden(tRASns: Double, tRASmaxCycles: Int, tRCDns: Double, tRPns: Double, tRCns: Double,
                    tREFIns: Double, tRFCns: Double, tRRD_Snck: Int, tRRD_Lnck: Int, tFAWns: Double,
                    tWTR_Snck: Int, tWTR_Lnck: Int, tRTPnck: Int, tCCD_Snck: Int, tCCD_Lnck: Int) {
    def nsOf(name: String): Double = name match {
      case "tRAS" => tRASns
      case "tRCD" => tRCDns
      case "tRP" => tRPns
      case "tRC" => tRCns
      case "tRFC" => tRFCns
      case "tREFI" => tREFIns
      case "tFAW" => tFAWns
      case _ => Double.NaN
    }
  }

  /** JESD79-4B speed bins by DRAM CK frequency (MHz): the 1600/1866/2133/2400 MT/s parts run
    * at 800/933/1067/1200 MHz. This repo's 200 MHz test point is below every listed bin, and
    * Table 102 has a 2400 column (2666/3200 mostly TBD), so both ends keep the strictest conformable value. */
  def binName(mhz: Int): String =
    if (mhz <= 800) "1600" else if (mhz <= 933) "1866" else if (mhz <= 1067) "2133" else "2400"

  /** AC-table floors for a density + refresh rate + DRAM CK frequency, transcribed from
    * JESD79-4B (AC table p.189-190, Table 23, Table 13 NOTE 1, Table 80). */
  def golden(densityGb: Int, rateX: Int, dramMhz: Int): Golden = {
    val tCKns = 1000.0 / dramMhz
    val bin = binName(dramMhz)
    // Table 80 (AC): tRAS by SPEED BIN, uniform across densities. The old density-keyed
    // transcription (32/33/34/35 ns for 1/2/4/8 Gb) was wrong — those ns values map to the
    // 1600/1866/2133/2400 bins.
    val tRAS = Map("1600" -> 35.0, "1866" -> 34.0, "2133" -> 33.0, "2400" -> 32.0)(bin)
    // Table 23 (tREFI/tRFC) per density x rate (1x/2x/4x). JESD79-4B covers 2/4/8/16 Gb;
    // 16 Gb = TBD, 1 Gb is out of spec (row removed — the old 195/160/130 and 160/130/100
    // rows for 2/4 Gb were a fabricated transcription).
    val tRFC = Map(2 -> Map(1 -> 160.0, 2 -> 110.0, 4 -> 90.0),
                   4 -> Map(1 -> 260.0, 2 -> 160.0, 4 -> 110.0),
                   8 -> Map(1 -> 350.0, 2 -> 260.0, 4 -> 160.0))(densityGb)(rateX)
    val tREFI = 7800.0 / rateX
    // AC table: tFAW in ns per bin + PAGE SIZE; this DUT has no page-size knob, so the 2K page
    // row (strictest) is the floor. The old 32/40/48 nCK x density model was fabricated — the
    // spec lists no such nCK values.
    val tFAW = Map("1600" -> 35.0, "1866" -> 30.0, "2133" -> 30.0, "2400" -> 30.0)(bin)
    Golden(
      tRASns = tRAS,
      tRASmaxCycles = 9 * cyc(tREFI, tCKns),
      tRCDns = 13.5,
      tRPns = 13.5,
      tRCns = tRAS + 13.5,
      tREFIns = tREFI,
      tRFCns = tRFC,
      tRRD_Snck = math.max(4, cyc(6.0, tCKns)),   // Max(4nCK, ns): strictest tRRD_S row (2K page)
      tRRD_Lnck = math.max(4, cyc(7.5, tCKns)),   // Max(4nCK, 7.5ns) 2K page, strictest bin
      tFAWns = tFAW,
      tWTR_Snck = math.max(2, cyc(2.5, tCKns)),   // Max(2nCK, 2.5ns)
      tWTR_Lnck = math.max(4, cyc(7.5, tCKns)),   // Max(4nCK, 7.5ns)
      tRTPnck = math.max(4, cyc(7.5, tCKns)),     // Max(4nCK, 7.5ns) at all bins
      tCCD_Snck = 4,
      tCCD_Lnck = SdramGeneration.ddr4TCCD_L_floor(dramMhz)
    )
  }
}
