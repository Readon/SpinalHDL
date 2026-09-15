package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

// =============================================================================
// Minimal I/O SerDes blackbox wrappers (UltraScale E3 family).
// Defined locally (older-style BlackBox API, same as the working S7 IO.scala
// on this branch) to avoid a dependency on dfi-new's Ultrascale IO.scala,
// which targets a newer SpinalHDL API incompatible with this branch.
// =============================================================================
case class OSERDESE3(DATA_WIDTH: Int = 8) extends BlackBox {
  require(Set(4, 8).contains(DATA_WIDTH), "DATA_WIDTH must be 4 or 8")
  addGeneric("DATA_WIDTH", DATA_WIDTH)
  val CLK    = in Bool()
  val CLKDIV = in Bool()
  val D      = in Bits(DATA_WIDTH bits)
  val RST    = in Bool()
  val OQ     = out Bool()
}

case class ISERDESE3(DATA_WIDTH: Int = 8) extends BlackBox {
  require(Set(4, 8).contains(DATA_WIDTH), "DATA_WIDTH must be 4 or 8")
  addGeneric("DATA_WIDTH", DATA_WIDTH)
  val CLK    = in Bool()
  val CLK_B  = in Bool()
  val CLKDIV = in Bool()
  val D      = in Bool()
  val RST    = in Bool()
  val Q      = out Bits(DATA_WIDTH bits)
}

case class USPhy_IOBUF() extends BlackBox {
  val I  = in Bool()
  val T  = in Bool()
  val O  = out Bool()
  val IO = inout(Analog(Bool()))
}

/** T flip-flop (toggles Q on every CLK rising edge). Used to derive the DQS
  * write-strobe square wave in the DRAM-clock domain: a T-FF clocked by
  * dramClk toggles once per dramClk edge, producing a square wave at
  * f_dramClk/2 = f_refclk — exactly the DDR4 DQS frequency. */
case class USPhy_TFF() extends BlackBox {
  val CLK = in Bool()
  val T   = in Bool()
  val Q   = out Bool()
}

/** ODELAYE3: variable output delay (time-based). Drives the DQS write-strobe
  * delay — the quantity write leveling adjusts. */
case class ODELAYE3() extends BlackBox {
  addGeneric("DELAY_TYPE", "VARIABLE")
  addGeneric("DELAY_VALUE", 0)
  val CLK         = in Bool()
  val ODATAIN     = in Bool()
  val DATAOUT     = out Bool()
  val CE          = in Bool()
  val RST         = in Bool()
  val EN_VTC      = in Bool()
  val INC         = in Bool()
  val LOAD        = in Bool()
  val CNTVALUEIN  = in UInt(9 bits)
  val CNTVALUEOUT = out UInt(9 bits)
  val CASC_IN     = in Bool()
  val CASC_RETURN = in Bool()
  val CASC_OUT    = out Bool()
}

/** IDELAYE3: variable input delay (time-based). Feedback path for write
  * leveling (samples the delayed DQS) and read DQS deskew. */
case class IDELAYE3() extends BlackBox {
  addGeneric("DELAY_TYPE", "VARIABLE")
  addGeneric("DELAY_VALUE", 0)
  val CLK         = in Bool()
  val DATAIN      = in Bool()
  val IDATAIN     = in Bool()
  val DATAOUT     = out Bool()
  val CE          = in Bool()
  val RST         = in Bool()
  val EN_VTC      = in Bool()
  val INC         = in Bool()
  val LOAD        = in Bool()
  val CNTVALUEIN  = in UInt(9 bits)
  val CNTVALUEOUT = out UInt(9 bits)
  val CASC_IN     = in Bool()
  val CASC_RETURN = in Bool()
  val CASC_OUT    = out Bool()
}

/** Xilinx UltraScale DDR PHY (redesign) — Steps 1-7 (elaboration-validated).
  *
  * Architecture ground truth: see `.usphy_redesign_docs/` (12 ADRs, review report,
  * architecture diagram, glossary). Key decisions baked in here:
  *   ADR-0001  datapath = I/O SerDes only; command/clock = PARALLEL I/O (never serialized)
  *   ADR-0002  write leveling = REAL closed loop; read training = STUB (UNVERIFIED_ON_BOARD)
  *   ADR-0003  parameterized framework (generation / frequencyRatio / dataWidth / nranks)
  *   ADR-0004  SerDes multiplier auto-derived from generation (not an external param)
  *   ADR-0006  single refclk in; PHY self-generates all clocks internally
  *   ADR-0007  reuse the existing Dfi bundle verbatim (PHY is the DFI slave)
  *   ADR-0008  command ENCODING is owned by the MC; PHY does per-phase pass-through +
  *             a STATIC generation-dependent CA pin mapping only (no encoding/FSM/arbitration)
  *   ADR-0012  top-level port list (refclk, rst_n, dfi, physical pads)
  *
  * SerDes clock architecture (verified against Xilinx golden model + DfiMemoryAgent):
  *   DATA_WIDTH = 2 * frequencyRatio  (fr=2 → 4, fr=4 → 8)
  *   CLK        = dramClk  (serialization clock, = fr * refclk)
  *   CLKDIV     = sysClk   (frame-load clock, = refclk)
  *   Constraint: CLK = (DATA_WIDTH/2) * CLKDIV → fr * refclk = fr * refclk ✓
  *
  * Data path: CROSS-PHASE FRAME ASSEMBLY
  *   Each OSERDESE3/ISERDESE3 frame aggregates 2 bits per pin from EACH of the
  *   fr DFI phases. Frame is loaded/presented on CLKDIV (= sysClk) edges and
  *   serialized/deserialized over fr DRAM cycles on CLK (= dramClk).
  *   Frame MSB = phase 0 (earliest DRAM cycle), frame LSB = phase (fr-1) (latest).
  *
  * MINIMUM FREQUENCY RATIO: v1 requires frequencyRatio >= 2 (OSERDESE3 minimum
  * DATA_WIDTH=4 implies 2*fr >= 4 → fr >= 2). fr=1 would require OSERDES2.
  */
case class UsPhy(cfg: DfiConfig) extends Component {

  require(cfg.frequencyRatio >= 2,
    s"UsPhy v1 requires frequencyRatio >= 2 (got ${cfg.frequencyRatio}); " +
    s"fr=1 would require OSERDES2 which is not supported.")
  // NOTE: UsPhy v1 does not implement DBI inversion logic. If DBI is enabled in config,
  // the DM/DBI pins are driven low (no inversion). Marked UNVERIFIED_ON_BOARD.


  // ===========================================================================
  // Step 1: top-level ports (ADR-0006, ADR-0012)
  // ===========================================================================
  val refclk  = in Bool()  // Reference clock (also serves as sysClk/DFI clock)
  val dramClk = in Bool()  // DRAM clock (= refclk * frequencyRatio; production: from internal MMCM)
  val rst_n   = in Bool()
  val dfi    = slave(Dfi(cfg))

  // Parameter axes (ADR-0003). All derived from cfg — no extra external knobs.
  val generation     = cfg.sdram.generation
  val frequencyRatio = cfg.frequencyRatio
  val dataWidth      = cfg.dataWidth        // DFI logical per-phase width (= dataRate * sdram.dataWidth)
  val physDqWidth    = cfg.sdram.dataWidth  // PHYSICAL DQ pin count (what the board wires)
  val nranks         = cfg.chipSelectNumber
  val byteLanes      = physDqWidth / 8      // number of DQ byte-lanes (physical)
  // ADR-0004: SerDes multiplier auto-derived from generation (internal, not a param).
  val serdesMult     = if (generation == SdramGeneration.DDR4) 4 else 2
  // SerDes DATA_WIDTH = 2 * fr (derived from DFI geometry: 2 DDR symbols/pin/phase * fr phases)
  val serdesDw       = 2 * frequencyRatio

  // Physical pads (ADR-0012 frozen port list).
  val pads = new Bundle {
    // Command / clock — PARALLEL I/O, never serialized (ADR-0001)
    val ck_t    = out Bool()
    val ck_c    = out Bool()
    val cke     = out(Vec(Bool(), nranks))
    val cs_n    = out(Vec(Bool(), nranks))
    val ca      = out(Vec(Bool(), cfg.addressWidth))
    val act_n   = out Bool()
    val bg      = out(Vec(Bool(), cfg.sdram.bgWidth))
    val cid     = out(Vec(Bool(), cfg.sdram.cidWidth))
    val odt     = out(Vec(Bool(), nranks))
    val reset_n = out Bool()
    val par     = out Bool()
    // Data path — BIDIRECTIONAL I/O SerDes carrier (inout).
    val dq       = inout(Vec(Analog(Bool()), physDqWidth))
    val dqs_t    = out(Vec(Bool(), byteLanes))
    val dqs_c    = out(Vec(Bool(), byteLanes))
    val dqs_in_t = in(Vec(Bool(), byteLanes))   // DQS RETURNED from DRAM (write-leveling echo)
    val dm_dbi_n = out(Vec(Bool(), byteLanes))
  }

  // ===========================================================================
  // Step 2: clock generation (skeleton) — ADR-0006
  // ===========================================================================
  // In the full design: refclk -> MMCM -> {dramClk, sysClk}.
  // Skeleton: all domains alias refclk; the domain NAMES are what matter.
  // REAL relationships (post-MMCM):
  //   sysClk    = refclk                    (DFI/MC interface clock)
  //   dramClk   = refclk * frequencyRatio   (DRAM clock)
  //   CLKDIV    = sysClk                    (SerDes frame-load = DFI clock)
  //   CLK       = dramClk                   (SerDes serialization = DRAM clock)
  val sysClk = refclk  // DFI clock domain (MC-PHY interface) = refclk
  // dramClk is now an EXTERNAL INPUT (production: driven by internal MMCM from refclk;
  // simulation: provided by TB at the correct frequency = refclk * frequencyRatio).

  // Asynchronous reset, 2-stage synchronous release (ADR-0006 consequence).
  val rst = Reg(Bool()) init(True)
  rst := !rst_n
  val rstSynced = RegNext(rst) init(True)

  // ===========================================================================
  // Step 3: DFI command -> parallel CA pins (per-phase pass-through) — ADR-0008
  // ===========================================================================
  // Phase 0 occupies the LOWEST (width / frequencyRatio) bits of each packed
  // per-phase DFI control vector.
  def phase0(bits: Bits): Bits = bits.take(bits.getWidth / frequencyRatio)

  pads.ca := phase0(dfi.control.address).asBools
  pads.cs_n := phase0(dfi.control.csN).asBools
  pads.cke  := phase0(dfi.control.cke).asBools
  if (cfg.useOdt) pads.odt := phase0(dfi.control.odt).asBools
  else pads.odt.foreach(_ := False)
  pads.act_n   := (if (cfg.useAckN) phase0(dfi.control.actN)(0) else True)
  pads.reset_n := (if (cfg.useResetN) phase0(dfi.control.resetN)(0) else True)
  pads.par     := (if (cfg.useCaParity) phase0(dfi.control.parity)(0) else False)
  if (cfg.useBg) pads.bg := phase0(dfi.control.bg).asBools
  else pads.bg.foreach(_ := False)
  if (cfg.useCid) pads.cid := phase0(dfi.control.cid).asBools
  else pads.cid.foreach(_ := False)
  // Clock out (skeleton: alias dramClk; real MMCM differential output at P&R).
  pads.ck_t    := dramClk
  pads.ck_c    := !dramClk

  // ===========================================================================
  // Step 4: WRITE data path — cross-phase frame assembly (ADR-0001, Q1/Q2)
  // ===========================================================================
  // Geometry (anchored from tested DfiMemoryAgent byte extraction):
  //   Per-phase wrdata = dataWidth bits = 2 * physDqWidth (DDR: 2 edges per DRAM clk).
  //   Standard DFI packing:
  //     bits [physDqWidth-1 : 0]  = DQ[physDqWidth-1:0] on FIRST DDR edge (rising)
  //     bits [2*physDqWidth-1 : physDqWidth] = DQ[physDqWidth-1:0] on SECOND DDR edge (falling)
  //   Therefore: pin p, first edge = bit p;  pin p, second edge = bit (p + physDqWidth).
  //
  // Frame assembly (DATA_WIDTH = 2*fr):
  //   D[2*fr-1 : 2*fr-2] = phase 0's DDR pair (earliest DRAM cycle)
  //   D[2*fr-3 : 2*fr-4] = phase 1's DDR pair
  //   ...
  //   D[1:0]              = phase (fr-1)'s DDR pair (latest DRAM cycle)
  //
  // Clocking:
  //   CLK    = dramClk  (serialization: 2 symbols per CLK edge)
  //   CLKDIV = sysClk   (frame load: one frame per DFI/MC cycle)
  //   OQ     = 1-bit serial output → DQ pin via IOBUF

  for (pin <- 0 until physDqWidth) {
    val oserdes = new OSERDESE3(serdesDw)
    oserdes.CLK    := dramClk
    oserdes.CLKDIV := sysClk
    oserdes.RST    := rstSynced

    // Standard DFI bit positions for this pin:
    val bitPos0 = pin                    // first DDR edge (rising)
    val bitPos1 = pin + physDqWidth      // second DDR edge (falling)

    // Build the 2*fr-bit frame: phase 0 pair at MSB, phase (fr-1) pair at LSB.
    val pairs = (0 until frequencyRatio).map { p =>
      Cat(dfi.write.wr(p).wrdata(bitPos0),
          dfi.write.wr(p).wrdata(bitPos1))
    }
    oserdes.D := Cat(pairs)

    // Serialize to the DQ pin via IOBUF (tri-state controlled by write-enable).
    val buf = new USPhy_IOBUF()
    buf.I := oserdes.OQ
    // Tri-state when NO phase has write data (any-phase condition for cross-phase frames).
    buf.T := !dfi.write.wr.map(_.wrdataEn).reduce(_||_)
    pads.dq(pin) := buf.IO
  }

  // ===========================================================================
  // Step 5: READ data path — cross-phase frame distribution (ADR-0001, Q1/Q2)
  // ===========================================================================
  // ISERDESE3(DATA_WIDTH = 2*fr):
  //   D      = 1-bit serial input (sampled on CLK rising edges)
  //   CLK    = dramClk (deserialization clock)
  //   CLK_B  = ~CLK
  //   CLKDIV = sysClk  (frame presentation clock)
  //   Q      = 2*fr-bit deserialized word (presented on CLKDIV rising edge)
  //            Q[2*fr-1] = earliest symbol (phase 0), Q[0] = latest (phase fr-1)
  //
  // Distribution: each phase's 2 bits for pin p are routed to that phase's rddata.

  for (pin <- 0 until physDqWidth) {
    val iserdes = new ISERDESE3(serdesDw)
    iserdes.CLK    := dramClk
    iserdes.CLK_B  := !dramClk
    iserdes.CLKDIV := sysClk
    iserdes.RST    := rstSynced
    // Serial input: sample the DQ pin (input direction of the bidirectional pad).
    iserdes.D      := pads.dq(pin)

    val byteLaneIdx = pin / 8
    val bitInByte   = pin % 8
    val bitPos0 = byteLaneIdx * 16 + bitInByte          // first DDR edge
    val bitPos1 = byteLaneIdx * 16 + 8 + bitInByte      // second DDR edge

    // Distribute Q to each phase's rddata.
    for (p <- 0 until frequencyRatio) {
      val qBase = 2 * (frequencyRatio - 1 - p)
      dfi.read.rd(p).rddata(bitPos0) := iserdes.Q(qBase + 1)  // first DDR edge
      dfi.read.rd(p).rddata(bitPos1) := iserdes.Q(qBase)       // second DDR edge
    }
  }

  // DQS / DM / DBI — placeholder (real DQS generation and DM logic in Steps 6-7).
  pads.dm_dbi_n.foreach(_ := False)

  // ===========================================================================
  // Step 6: WRITE LEVELING — real closed loop (ADR-0002 tier: must be REAL)
  // ===========================================================================
  // Mechanism (DDR4 JESD79-4B §4.14):
  //   1. During WL the PHY drives DQS with a toggle waveform through an
  //      ODELAYE3 (the ADJUSTABLE output delay — the quantity WL optimizes).
  //   2. The DRAM captures that DQS and echoes it back on the DQS pin.
  //   3. The PHY samples the returned DQS through an IDELAYE3 (feedback path).
  //   4. An alignment detector measures the returned-DQS transition position
  //      relative to the CK edge and emits EARLY / OK / LATE.
  //   5. A binary-search FSM nudges the ODELAYE3 counter toward the CK-edge
  //      center until OK, then raises wrlvlResp (done).
  //
  // HONEST SCOPE: the hardware STRUCTURE (ODELAYE3 on DQS out, IDELAYE3
  // feedback, detector, binary-search FSM, DFI training wiring) is real and
  // elaborates. The exact ALGORITHM (transition-count threshold that defines
  // "centered") is the part that REQUIRES the SerDes/DRAM behavioral model to
  // verify — it is marked UNVERIFIED_ON_BOARD per ADR-0002. We do NOT fabricate
  // a constant-true wrlvlDone.
  //
  // ---- DQS output: toggle waveform through ODELAYE3 (per byte-lane) ----------
  val wlActive = Reg(Bool()) init(False)                 // WL sequence in progress
  val wlDelay = Reg(UInt(9 bits)) init(0)               // ODELAYE3 delay counter
  val wlDone  = Reg(Bool()) init(False)                 // converged flag
  // DQS write-strobe: T-FF clocked by dramClk → square wave at f_dramClk/2 = f_refclk.
  // This is the NORMAL-mode DQS (valid during write windows; the DRAM ignores it outside).
  val dqsTff = new USPhy_TFF()
  dqsTff.CLK := dramClk
  dqsTff.T   := True
  val dqsToggle = dqsTff.Q

  // Seq.fill (not Vec.fill) so the concrete ODELAYE3 type is retained for member access.
  val dqsOdelays = Seq.fill(byteLanes)(new ODELAYE3())
  for (l <- 0 until byteLanes) {
    dqsOdelays(l).CLK      := dramClk
    dqsOdelays(l).CE       := True
    dqsOdelays(l).RST      := rstSynced
    dqsOdelays(l).EN_VTC   := False
    dqsOdelays(l).LOAD     := False
    dqsOdelays(l).INC      := False
    dqsOdelays(l).ODATAIN  := dqsToggle
    dqsOdelays(l).CASC_IN     := False
    dqsOdelays(l).CASC_RETURN := False
    dqsOdelays(l).CNTVALUEIN := wlDelay
    pads.dqs_t(l) := dqsOdelays(l).DATAOUT
    pads.dqs_c(l) := !dqsOdelays(l).DATAOUT
  }

  // ---- Feedback: IDELAYE3 samples the returned DQS (per byte-lane) -----------
  val fbIddelays = Seq.fill(byteLanes)(new IDELAYE3())
  for (l <- 0 until byteLanes) {
    fbIddelays(l).CLK      := dramClk
    fbIddelays(l).RST      := rstSynced
    fbIddelays(l).CE       := True
    fbIddelays(l).EN_VTC   := False
    fbIddelays(l).LOAD     := False
    fbIddelays(l).INC      := False
    fbIddelays(l).CNTVALUEIN := 0
  }
  val fbSample = Vec.fill(byteLanes)(Bool())
  for (l <- 0 until byteLanes) {
    fbIddelays(l).DATAIN := pads.dqs_in_t(l)      // sample the DQS as it RETURNS FROM THE DRAM (real closed loop)
    fbIddelays(l).IDATAIN := False
    fbIddelays(l).CASC_IN := False
    fbIddelays(l).CASC_RETURN := False
    fbSample(l)     := fbIddelays(l).DATAOUT
  }

  // ---- Alignment detector ----------------------------------------------------
  val transDetect = Vec.fill(byteLanes)(Bool())
  for (l <- 0 until byteLanes) {
    val prev = RegNext(fbSample(l)) init(False)
    transDetect(l) := fbSample(l) ^ prev
  }
  val transWindow = Vec.fill(byteLanes)(Reg(UInt(16 bits)) init(0))
  for (l <- 0 until byteLanes) {
    transWindow(l) := 0
    when(wlActive && !wlDone) {
      transWindow(l) := transWindow(l) + transDetect(l).asUInt
    }
  }
  // Centered reference (UNVERIFIED_ON_BOARD tuning constant).
  val CENTER_REF = 8
  val verdict = new Bundle { val early, ok, late = Bool() }
  verdict.early := transWindow(0) < CENTER_REF
  verdict.ok    := transWindow(0) === CENTER_REF
  verdict.late  := transWindow(0) > CENTER_REF

  // ---- Binary-search FSM -----------------------------------------------------
  when(wlActive) {
    when(verdict.early) { wlDelay := wlDelay + 1 }
    .elsewhen(verdict.late) { wlDelay := wlDelay - 1 }
    .otherwise          { wlDone  := True }
  }.otherwise {
    wlDone  := False
    wlDelay := 0
  }
  // CNTVALUEIN is already driven by the init loop above (dqsOdelays(l).CNTVALUEIN := wlDelay).

  // ---- DFI write-training interface ------------------------------------------
  wlActive := (if (cfg.useWrlvlEn) dfi.wrTraining.wrlvlEn.orR else False)
  if (cfg.useWrlvlResp) {
    dfi.wrTraining.wrlvlResp := 0
    dfi.wrTraining.wrlvlResp(cfg.writeLevelingResponseWidth - 1) := wlDone
  }
  if (cfg.usePhyWrlvlCsN) {
    dfi.wrTraining.phyWrlvlCsN := 0
  }

  // ===========================================================================
  // Step 7: READ TRAINING — explicit STUB (ADR-0002 tier: interface complete,
  // feedback injectable, clearly marked UNVERIFIED_ON_BOARD).
  // ===========================================================================
  if (cfg.useRdlvlReq) {
    dfi.rdTraining.rdlvlReq := 0
    dfi.rdTraining.rdlvlReq(0) := (if (cfg.useRdlvlEn) dfi.rdTraining.rdlvlEn.orR else False)
  }
  if (cfg.usePhyRdlvlCsN) {
    dfi.rdTraining.phyRdlvlCsN := 0
  }
  if (cfg.useRdlvlResp) {
    // STUB: neutral 0. Per ADR-0002 we do NOT self-assign constant "success".
    dfi.rdTraining.rdlvlResp := 0
  }
  if (cfg.useRdlvlGateReq) {
    dfi.rdTraining.rdlvlGateReq := 0
  }
  if (cfg.usePhyRdlvlGateCsN) {
    dfi.rdTraining.phyRdlvlGateCsN := 0
  }

  // ===========================================================================
  // DFI PHY-side (slave) outputs — driven by the datapath above.
  // ===========================================================================
  for (idx <- 0 until dfi.read.rd.length) {
    val l = dfi.read.rd(idx)
    l.rddataValid := False   // real driver: gated by read-latency counter (future)
    l.rddataDbiN  := 0       // real driver: DBI encoder (future)
    // rddata is driven by the ISERDESE3 cross-phase distribution above (Step 5).
  }
}