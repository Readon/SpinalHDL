package spinal.lib.memory.sdram.dfi

import spinal.tester.code.SpinalAnyFunSuite

/**
  * Mode-register conformance for the DDR4 presets (JESD79-4B sec. 3.5 MR tables + field
  * Tables 1-8, 10-13; DFI spec write-leveling gate).
  *
  * The MC programs MR0..MR6 through the init micro-program
  * ([[Ddr4InitSequencer]]/[[SdramInitStep]] in `MakeTask.scala`), so a mode register is a spec
  * artifact: every bit position means something to the DRAM, and several MC behaviours are
  * *gated* on those bits (`DfiConfig.useRdDbi` reads MR5.A12, `useWrDbi` reads MR5.A11;
  * `SdramConfig.tREF` divides by `SdramTiming.REF_RATE`).
  *
  * The `Golden` object below is a test-side transcription of the bit fields; it is deliberately
  * not a re-export of anything in the DUT, so a preset that is spec-wrong cannot agree with its
  * own checker (the green-but-wrong pattern ADR-0017 exists to prevent). The PDFs are not in git
  * (ADR-0017 decision 7), so the transcription is inlined here.
  *
  * Transcription notes (JESD79-4B; the MR maps live in sec. 3.5, the field tables below):
  *  - MR0: A1:A0 burst length (00=BL8 fixed, 01=BC4/on-the-fly, 10=BC4 fixed, 11=Reserved),
  *    A3 burst type (0=sequential), A8 DLL reset, A7 TM (Test Mode, 0=Normal 1=Test),
  *    A6,A5,A4,A2 = CAS latency code (Table 2), A11:A9 = WR/RTP (Table 1);
  *    RFU=0: A17, A13, A12.
  *  - CAS code (A6,A5,A4,A2) -> CL: 0000=9, 0001=10, 0010=11, 0011=12, 0100=13, 0101=14,
  *    0110=15, 0111=16, 1000=18, 1001=20, 1010=22, 1011=24, 11xx=Reserved (Table 2).
  *  - MR1: A12 Qoff, A11 TDQS, A10:A8 RTT_NOM (Table 3), A7 write leveling enable,
  *    A4:A3 additive latency (00=0, 01=CL-1, 10=CL-2, 11=rsvd), A2:A1 driver impedance
  *    (Table 4), A0 DLL enable; RFU=0: A17, A13, A6, A5.
  *  - MR2: A12 Write CRC, A10:A9 RTT_WR (Table 5), A7:A6 LP ASR, A5:A3 CWL (Table 6);
  *    RFU=0: A17, A13, A11, A8, A2:A0. (A2 is NOT a CA-parity enable — it is RFU; the
  *    DRAM-side CA-parity enable is MR5 A2:A0 != 0, sec. 4.17.)
  *  - MR3: A12:A11 MPR read format, A10:A9 WriteCMD latency CRC/DM (Table 8), A8:A6 fine
  *    granularity refresh (Table 7: 000=1x, 001=2x, 010=4x, 101/110=on-the-fly), A5 temp
  *    sensor, A4 per-DRAM; RFU=0: A17, A13.
  *  - MR4: A12 write preamble, A11 read preamble, A10 RPT, A9 SR abort, A8:A6 CAL (Table 10),
  *    A4 Vref monitor, A3 TCR enable, A2 TCR range, A1 max PD; RFU=0: A17, A13, A5, A0.
  *  - MR5: A12 Read DBI, A11 Write DBI, A10 Data Mask, A9 CA parity persistent error,
  *    A8:A6 RTT_PARK (Table 11), A5 ODT-in-PD, A4 CA parity error status, A3 CRC error clear,
  *    A2:A0 C/A Parity Latency Mode (Table 12: 000=disable, 001=PL4, 010=PL5, 011=PL6,
  *    100=PL8, 101-111 rsvd) — non-zero ENABLES DRAM-side CA parity (sec. 4.17).
  *  - MR6: A12:A10 tCCD_L (Table 13: 000=4 ... 100=8 nCK, codes 101+ rsvd), A7 VrefDQ
  *    training enable, A6 VrefDQ range, A5:A0 VrefDQ value; RFU=0: A17, A13, A9, A8.
  */
class Ddr4ModeRegSpec extends SpinalAnyFunSuite {

  import Ddr4ModeRegGolden._

  test("MR0 default (0x130) decodes to the fields its own comment claims (sec. 3.5 MR0; CAS code Table 2)") {
    val mr0 = Ddr4ModeRegs().mr0
    assert(mr0 == 0x130, f"default MR0 changed: 0x$mr0%04X")
    assert(burstLength(mr0) == 8, f"MR0 A1:A0=00 => BL8 fixed (sec. 3.5 MR0 table); got 0x$mr0%04X")
    assert(burstSequential(mr0), "MR0 A3=0 => sequential burst (sec. 3.5 MR0 table)")
    assert(dllReset(mr0), "MR0 A8=1: the init DLL reset, required by JESD79-4 sec. 3.3 step 12")
    assert(casLatency(mr0) == Some(15), s"MR0 CAS code must decode to CL15, got ${casLatency(mr0)}")
    // AL lives in MR1 A4:A3, NOT in MR0 — MR0 A6:A4,A2 is the CAS Latency field
    // (Table 2). The scheduler's AL=0 assumption is a statement about MR1, which the MC writes 0.
    assert(additiveLatency(Ddr4ModeRegs().mr1) == 0, "MR1 A4:A3 = 00 => AL disabled; the scheduler assumes AL=0")
    assert(encodeCl(15) == mr0, "the CL encoder (Table 2 field A6,A5,A4,A2, plus the A8 DLL reset it sets) must reproduce the default MR0")
  }

  test("no MR preset sets an RFU bit (JESD79-4B sec. 3.5: RFU fields 'must be programmed to 0')") {
    // Per-register must-be-zero RFU bits, restricted to the on-bus 14-bit MRS OP code
    // A13:A0; A16:A14 are command pins at MRS time (JESD79-4B Table 16 NOTE 1) and A17 is a
    // phantom bit (Table 16 NOTE 12), so neither is programmable. The old single 0x1E000
    // mask was wrong on both ends: it checked dead bits A16:A14 and missed A12 (MR0) and
    // the per-register RFU bits of MR1-MR6.
    val rfu = Seq(
      ("MR0", Ddr4ModeRegs().mr0, 0x3000), // A13, A12
      ("MR1", Ddr4ModeRegs().mr1, 0x2060), // A13, A6, A5
      ("MR2", Ddr4ModeRegs().mr2, 0x2907), // A13, A11, A8, A2:A0
      ("MR3", Ddr4ModeRegs().mr3, 0x2000), // A13
      ("MR4", Ddr4ModeRegs().mr4, 0x2021), // A13, A5, A0
      ("MR5", Ddr4ModeRegs().mr5, 0x2000), // A13
      ("MR6", Ddr4ModeRegs().mr6, 0x2300)  // A13, A9, A8
    ).foreach { case (name, op, mask) =>
      assert((op & mask) == 0, s"$name preset OP code sets an RFU bit (mask 0x$mask%X must be 0): 0x$op%04X")
    }
  }

  test("the CAS decoder covers the whole speed-bin map and rejects Reserved codes") {
    val golden = Seq(0x0 -> 9, 0x1 -> 10, 0x2 -> 11, 0x3 -> 12, 0x4 -> 13, 0x5 -> 14, 0x6 -> 15,
                     0x7 -> 16, 0x8 -> 18, 0x9 -> 20, 0xa -> 22, 0xb -> 24)
    // Table 2 keys the field as the 4-tuple (A6, A5, A4, A2) — not a contiguous slice.
    def clBits(code: Int) = ((code >> 3) & 1) << 6 | ((code >> 2) & 1) << 5 | ((code >> 1) & 1) << 4 | (code & 1) << 2
    golden.foreach { case (code, cl) => assert(casLatency(clBits(code) | (1 << 8)) == Some(cl), s"code $code => CL$cl, got ${casLatency(clBits(code) | (1 << 8))}") }
    assert(casLatency(clBits(0xc) | (1 << 8)).isEmpty, "11xx is Reserved in the speed-bin table")
  }

  test("MR1 default keeps write leveling disabled and Qoff off (sec. 3.5 MR1; RTT_NOM Table 3)") {
    val mr1 = Ddr4ModeRegs().mr1
    assert(((mr1 >> 7) & 1) == 0, "MR1.A7 write leveling enable is 0 in the default preset")
    assert(((mr1 >> 12) & 1) == 0, "MR1.A12 Qoff=0 => output buffer enabled")
    assert(rttNom(mr1) == 0, "MR1.A10:A8 RTT_NOM=0 (RZQ/1 off) — the reference model's ODT monitor has no RTT term, so the preset must stay inert")
    // DDR4 write leveling needs the DRAM-side MR1.A7 write-leveling enable (JESD79-4B sec. 3.5:
    // "A7 Write Leveling Enable, 0 = Disable, 1 = Enable"). Nothing in this repo sets MR1.A7, so
    // the legacy write leveling handshake (io.legacyWrlvlTrigger) can only work for a user who
    // injects MR1.A7 themselves. Assert the gate is at least observable, and record the gap.
    assert(writeLevelingEnable(mr1) == false,
      "MR1.A7 is not programmed by any preset: the DDR4 write-leveling precondition (JESD79-4B " +
      "sec. 3.5 MR1.A7) is unmet by the built-in preset")
    // JESD79-4 sec. 3.3 power-up init: the DLL must be ENABLED (MR1.A0=1) before the MR0
    // DLL-reset bit re-locks it; a preset with A0=0 leaves the DRAM's DLL off on real silicon.
    assert((mr1 & 1) == 1,
      "MR1.A0=1 (DLL enable) is required by JESD79-4 sec. 3.3; the default preset must set it")
  }

  test("MR3 refresh-rate bits and REF_RATE must be the same decision (Table 7, sec. 4.9.2)") {
    // JESD79-4 Table 7: A8:A6 = 000 1x, 001 fixed 2x, 010 fixed 4x, 101 on-the-fly 2x,
    // 110 on-the-fly 4x; the scheduler divides tREFI by SdramTiming.REF_RATE (SdramConfig.tREF).
    assert(refreshRateOf(Ddr4ModeRegs().mr3) == 1, "default MR3 = normal 1x refresh")
    Seq(1 -> 0x000, 2 -> 0x040, 4 -> 0x080).foreach { case (rate, bits) =>
      assert(refreshRateOf(bits) == rate, f"MR3.A8:A6=0b${Integer.toBinaryString(bits >> 6)} => ${rate}x")
    }
    assert(refreshRateOf(0x140) == -1, "101 = on-the-fly 2x: the MC has no per-command rate path, so it must not be read as a fixed rate")
    // Cross-check: a config that shortens tREFI by REF_RATE=2 must also program MR3=001, or the
    // DRAM and the MC disagree about the refresh period (tREFI2 = tREFI/2, sec. 4.9.2).
    Seq(1, 2, 4).foreach { rate =>
      val cfg = Ddr4Cfg(fr = 2, rate = rate)
      assert(cfg.sdram.tREF == 1560 / rate, s"tREFI at ${rate}x must be ${1560 / rate} DRAM cycles (7800ns/5ns/${rate}), got ${cfg.sdram.tREF}")
      assert(refreshRateOf(cfg.ddr4ModeRegs.mr3) == rate,
        s"REF_RATE=$rate shortens the MC's tREFI to ${cfg.sdram.tREF} cycles, but MR3.A8:A6=" +
        f"0b${Integer.toBinaryString((cfg.ddr4ModeRegs.mr3 >> 6) & 7)} tells the DRAM ${refreshRateOf(cfg.ddr4ModeRegs.mr3)}x — non-conformant")
    }
  }

  test("MR4 TCR is disabled and the MC has no derating path (sec. 3.5 MR4)") {
    val mr4 = Ddr4ModeRegs().mr4
    assert(((mr4 >> 3) & 1) == 0, "MR4.A3 TCR must stay 0: the scheduler has no 3.9us derating path")
    // JESD79-4 sec. 4.8: above 85 C the DRAM requires tREFI halved; TCR off means the *system*
    // must sense temperature and re-program. Nothing in the repo does, so the gate's refresh
    // conformance is scoped to the normal-temperature 7.8us figure.
    assert(tcrEnable(mr4) == false)
  }

  test("MR5 DBI bits are what gates the MC datapath (Tables 28/29, sec. 4.11)") {
    Seq(0x0000, 0x1000, 0x0800, 0x1800).foreach { mr5 =>
      val cfg = Ddr4Cfg(fr = 2, mr5 = mr5)
      val rdBit = (mr5 >> 12) & 1
      val wrBit = (mr5 >> 11) & 1
      assert(cfg.useRdDbi == (rdBit == 1), s"MR5.A12=$rdBit must gate useRdDbi (${cfg.useRdDbi})")
      assert(cfg.useWrDbi == (wrBit == 1), s"MR5.A11=$wrBit must gate useWrDbi (${cfg.useWrDbi})")
      assert(dbiRead(mr5) == (rdBit == 1) && dbiWrite(mr5) == (wrBit == 1), "the golden decoder must agree with the gate")
    }
  }

  test("CA parity gates on MR5 A2:A0 and the init sequence lifts MR6 tCCD_L (Table 12, Table 13)") {
    val regs = Ddr4ModeRegs()
    assert((regs.mr5 & 0x7) == 0, "raw MR5 A2:A0 CA-parity latency = 000 (disabled) in the default preset")
    assert(regs.mr6 == 0x000, "raw MR6 default: tCCD_L field A12:A10 = 000")
    // The DRAM-side CA-parity enable is MR5 A2:A0 != 0 (JESD79-4B Table 12 + sec. 4.17).
    // DfiConfig elaboration-gates useCaParity on it and fails fast in the catastrophic
    // direction: a parity-surface config that leaves the latency mode at 0 drives parity
    // the DRAM ignores.
    val cfg = Ddr4Cfg(fr = 2, caParity = true, mr5 = 0x0001)
    assert(cfg.useCaParity, "parity surface + MR5 PL4 -> useCaParity on")
    assert((cfg.ddr4ModeRegs.mr5 & 0x7) != 0, "the DRAM-side MR5 gate is what enables parity, not signal presence")
    // Table 13 NOTE 1: program tCCD_L per the AC table; at the 400 MT/s test point the floor
    // is 5 nCK, so the init sequence lifts MR6 A12:A10 from 000 to 001.
    val mr6Step = SdramInitStep.defaultFor(SdramGeneration.DDR4, regs, fr = 2)
      .find(s => s.kind == SdramInitStep.MRS && s.mrIndex == 6)
      .map(_.opCode)
    assert(mr6Step == Some(0x400), s"init MR6 must program tCCD_L = 5 nCK (AC floor, code 001 in A12:A10), got $mr6Step")
  }
}

/**
  * Test-side golden decoder for the DDR4 mode registers (JESD79-4B sec. 3.5 MR tables +
  * Tables 2, 3, 6, 7, 12, 13). Not derived
  * from the DUT. Bit positions are the A-bit numbers the tables use; the MR-select field
  * (BG0, BA1:BA0) is carried in the command's bank field, not in the OP code, so it is out of
  * these masks.
  */
object Ddr4ModeRegGolden {
  private val clMap = Map(0x0 -> 9, 0x1 -> 10, 0x2 -> 11, 0x3 -> 12, 0x4 -> 13, 0x5 -> 14,
                          0x6 -> 15, 0x7 -> 16, 0x8 -> 18, 0x9 -> 20, 0xa -> 22, 0xb -> 24)
  def casField(mr0: Int): Int = ((mr0 >> 6) & 1) << 3 | ((mr0 >> 5) & 1) << 2 | ((mr0 >> 4) & 1) << 1 | ((mr0 >> 2) & 1)
  def casLatency(mr0: Int): Option[Int] = clMap.get(casField(mr0))
  /** Build an MR0 OP code with only the CAS code and the DLL-reset bit set. */
  def encodeCl(cl: Int): Int = {
    val code = clMap.collectFirst { case (c, `cl`) => c }.get
    (code & 1) << 2 | (code >> 1 & 1) << 4 | (code >> 2 & 1) << 5 | (code >> 3 & 1) << 6 | (1 << 8)
  }
  def burstLength(mr0: Int): Int = (mr0 & 0x3) match { case 0 => 8; case 1 => -1; case 2 => 4; case _ => -1 }
  def burstSequential(mr0: Int): Boolean = ((mr0 >> 3) & 1) == 0
  def dllReset(mr0: Int): Boolean = ((mr0 >> 8) & 1) == 1
  /** Additive latency: MR1 A4:A3 (00=0, 01=CL-1, 10=CL-2, 11=rsvd). NOT an MR0 field (its
    * A6:A4,A2 is the CAS-latency field), and NOT A5:A4 (the old decoder read the wrong pair). */
  def additiveLatency(mr1: Int): Int = (mr1 >> 3) & 0x3
  def writeLevelingEnable(mr1: Int): Boolean = ((mr1 >> 7) & 1) == 1
  def rttNom(mr1: Int): Int = (mr1 >> 8) & 0x7
  /** 1/2/4 for the fixed encodings; -1 for on-the-fly/reserved (no fixed scheduler meaning). */
  def refreshRateOf(mr3: Int): Int = (mr3 >> 6) & 0x7 match {
    case 0 => 1
    case 1 => 2
    case 2 => 4
    case _ => -1
  }
  def tcrEnable(mr4: Int): Boolean = ((mr4 >> 3) & 1) == 1
  def dbiRead(mr5: Int): Boolean = ((mr5 >> 12) & 1) == 1
  def dbiWrite(mr5: Int): Boolean = ((mr5 >> 11) & 1) == 1
  def caParityLatency(mr5: Int): Int = mr5 & 0x7
}

/**
  * DUT-side DDR4 config factory for the conformance suites, following the canonical recipe the
  * existing specs use (SdramCmdTruthTableSpec.scala:14-30), with the refresh-rate, DBI and CA
  * parity axes exposed.
  *
  * `rate` sets BOTH halves of the refresh-rate decision: `SdramTiming.REF_RATE` (the MC side,
  * divides tREFI) and MR3.A8:A6 (the DRAM side, JESD79-4 Table 7). tREFI is the absolute
  * `REF_NS` = 7800 ns, not the legacy per-row `REF` (us) derivation
  * (SdramConfig.scala:116-122: REF_NS=0 falls back to `REF*ddrMHZ/rowSize`).
  */
object Ddr4Cfg {
  def timing(rate: Int): SdramTiming = SdramTiming(
    generation = 4, RFC = rate match { case 1 => 350; case 2 => 260; case _ => 160 }, // JESD79-4 8 Gb tRFC: 1x=350, 2x=260, 4x=160 ns
    RAS = 35, RP = 14, RCD = 14, WTR = 8, WTP = 0, RTP = 12, RRD = 6,
    REF = 7800, FAW = 240,
    REF_NS = 7800, REF_RATE = rate)

  def apply(fr: Int,
            rate: Int = 1,
            ranks: Int = 1,
            ddrMHz: Int = 200,
            mr0: Int = 0x130,
            mr1: Int = 0x001,
            mr2: Int = 0x000,
            mr5: Int = 0x0000,
            caParity: Boolean = false,
            rowWidth: Int = 17,
            dataWidth: Int = 16,
            initSteps: Option[List[SdramInitStep]] = None): DfiConfig = {
    val bgWidth = if (fr >= 4) 2 else 1
    val cidWidth = if (ranks > 2) 2 else 1
    val sdram = SdramConfig(SdramGeneration.DDR4, bgWidth = bgWidth, cidWidth = cidWidth, bankWidth = 4,
      columnWidth = 10, rowWidth = rowWidth, dataWidth = dataWidth, ddrMHZ = ddrMHz,
      ddrWrLat = 12, ddrRdLat = 12, sdramtime = timing(rate))
    val timeConfig = DfiTimeConfig(
      frequencyRatio = fr, cmdPhase = 0, tPhyWrLat = sdram.tPhyWrlat, tPhyWrData = 0,
      tPhyWrCsLat = 0, tPhyWrCsGap = 3, tRddataEn = sdram.tRddataEn, tPhyRdlat = 4,
      tPhyRdCslat = 0, tPhyRdCsGap = 3)
    val mr3 = rate match { case 2 => 0x040; case 4 => 0x080; case _ => 0x000 }
    DfiConfig(
      chipSelectNumber = ranks, dataSlice = 1,
      signalConfig = new DDR4SignalConfig(DfiFunctionConfig(useStatusSignals = caParity), false) {
        override val useParityIn = caParity
        override val useAlertN = caParity
      },
      timeConfig = timeConfig, sdram = sdram,
      ddr4ModeRegs = Ddr4ModeRegs(mr0 = mr0, mr1 = mr1, mr2 = mr2, mr3 = mr3, mr5 = mr5),
      initSteps = initSteps)
  }
}
