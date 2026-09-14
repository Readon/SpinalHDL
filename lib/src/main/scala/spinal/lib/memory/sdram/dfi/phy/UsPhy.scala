package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram.dfi._

/** Xilinx UltraScale DDR PHY (redesign) — Steps 1-3 skeleton (elaboration-validated).
  *
  * Architecture ground truth: see `.usphy_redesign_docs/` (11 ADRs, review report,
  * architecture diagram, glossary). Key decisions baked in here:
  *   ADR-0001  datapath = I/O SerDes only; command/clock = PARALLEL I/O (never serialized)
  *   ADR-0003  parameterized framework (generation / frequencyRatio / dataWidth / nranks)
  *   ADR-0004  SerDes multiplier auto-derived from generation (not an external param)
  *   ADR-0006  single refclk in; PHY self-generates all clocks internally
  *   ADR-0007  reuse the existing Dfi bundle verbatim (PHY is the DFI slave)
  *   ADR-0008  command ENCODING is owned by the MC; PHY does per-phase pass-through +
  *             a STATIC generation-dependent CA pin mapping only (no encoding/FSM/arbitration)
  *   ADR-0012  top-level port list (refclk, rst_n, dfi, physical pads)
  *
  * Steps 4-5 (write/read datapath through I/O SerDes) and Steps 6-7 (write-leveling real
  * closed-loop, read-training stub) are intentionally NOT implemented yet.
  */
case class UsPhy(cfg: DfiConfig) extends Component {

  // ---------------------------------------------------------------------------
  // Step 1: top-level ports (ADR-0006, ADR-0012)
  // ---------------------------------------------------------------------------
  // ADR-0006: the ONLY clock entering the PHY is refclk; everything else is derived
  // internally. rst_n is asynchronous, released synchronously inside.
  val refclk = in Bool()
  val rst_n  = in Bool()

  // ADR-0007: consume the existing Dfi bundle verbatim (PHY = DFI slave). No adapter,
  // no re-declaration of DFI signals.
  val dfi = slave(Dfi(cfg))

  // Parameter axes (ADR-0003). All derived from cfg — no extra external knobs.
  val generation     = cfg.sdram.generation
  val frequencyRatio = cfg.frequencyRatio
  val dataWidth      = cfg.sdram.dataWidth
  val nranks         = cfg.chipSelectNumber
  val byteLanes      = dataWidth / 8
  // ADR-0004: SerDes multiplier auto-derived from generation (internal, not a param).
  val serdesMult     = if (generation == SdramGeneration.DDR4) 4 else 2

  // Physical pads (ADR-0012 frozen port list). Scale with the parameters above.
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
    // Data path (per byte lane) — I/O SerDes carrier, filled in Steps 4-5
    val dq       = out(Vec(Vec(Bool(), 8), byteLanes))
    val dqs_t    = out(Vec(Bool(), byteLanes))
    val dqs_c    = out(Vec(Bool(), byteLanes))
    val dm_dbi_n = out(Vec(Bool(), byteLanes))
  }

  // ---------------------------------------------------------------------------
  // Step 2: clock generation (skeleton) — ADR-0006
  // ---------------------------------------------------------------------------
  // refclk is the sole source; an MMCM derives the DRAM / SerDes / DFI clocks.
  // In this elaboration skeleton every domain aliases refclk (the real frequency
  // division is a P&R-stage MMCM concern — what matters now is the domain naming
  // and the reset discipline). The rest of the design targets these three names.
  val sysClk    = refclk   // DFI clock domain (MC-PHY interface)
  val serdesClk = refclk   // SerDes work clock  (real: refclk * serdesMult / 2)
  val dramClk   = refclk   // DRAM clock         (real: refclk / frequencyRatio)

  // Asynchronous reset, 2-stage synchronous release (ADR-0006 consequence).
  val rst = Reg(Bool()) init(True)
  rst := !rst_n
  val rstSynced = RegNext(rst) init(True)

  // ---------------------------------------------------------------------------
  // Step 3: DFI command -> parallel CA pins (per-phase pass-through) — ADR-0008
  // ---------------------------------------------------------------------------
  // The DFI control bus packs ONE command per DFI phase, phase 0 in the LSBs.
  // The PHY does NOT decode or re-encode: it forwards the MC-encoded command onto
  // the parallel DRAM CA/command pins. This is a STATIC, generation-dependent pin
  // mapping — the only "logic" in the command path (ADR-0008). No SerDes here
  // (ADR-0001). No FSM, no arbitration, no MR table (those live in the MC, ADR-0010).
  //
  // Skeleton note: we forward the phase-0 slice combinationally. Two refinements
  // are deliberately deferred to the full Step 3 (structure is proven here):
  //   (a) spreading the `frequencyRatio` phases across successive DRAM clock edges
  //       (+ the DFI->DRAM clock-domain crossing), and
  //   (b) the generation-specific STATIC mapping of the discrete command control
  //       signals (dfi.control.rasN/casN/weN/actN) onto their CA-pin POSITIONS —
  //       e.g. on DDR4 the RAS/CAS/WE pins double as RA[15:13] during ACT. This is
  //       the "static CA pin mapping" of ADR-0008: a per-generation wiring table,
  //       still no encoding/FSM/arbitration.
  // Phase 0 occupies the LOWEST (width / frequencyRatio) bits of each packed
  // per-phase DFI control vector (phase N is at [N*w .. N*w+w-1]). Take the low slice.
  def phase0(bits: Bits): Bits = bits.take(bits.getWidth / frequencyRatio)

  // Command/address bus (always present; MC-packed, bit order preserved, ADR-0008).
  // Carries the address portion; the discrete command controls map to CA-pin
  // positions per refinement (b) above.
  pads.ca := phase0(dfi.control.address).asBools
  // Per-rank control (always present; CS is per-rank, orthogonal to byte lanes).
  pads.cs_n := phase0(dfi.control.csN).asBools
  pads.cke  := phase0(dfi.control.cke).asBools
  // Conditionally-generated command controls — guarded on the config flag (Scala-level,
  // elaboration-time, so only the taken branch compiles). Absent signals tie to their
  // inactive level. This is the generation-dependent STATIC mapping (ADR-0008).
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

  // ---------------------------------------------------------------------------
  // Datapath placeholders (Steps 4-5) — tied off so the skeleton elaborates.
  // Will be replaced by per-byte-lane I/O SerDes (OSERDESE3/ISERDESE3 + O/I-DELAYE3).
  // ---------------------------------------------------------------------------
  for (l <- pads.dq) l.foreach(_ := False)
  pads.dqs_t.foreach(_ := False)
  pads.dqs_c.foreach(_ := False)
  pads.dm_dbi_n.foreach(_ := False)

  // Tie off the DFI PHY-side (slave) outputs so the skeleton elaborates cleanly.
  // (Real drivers arrive in Steps 4-7.)
  dfi.read.rd.foreach { r =>
    r.rddataValid := False
    r.rddata := 0
    r.rddataDbiN := 0
  }
}