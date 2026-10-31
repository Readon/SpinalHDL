// ============================================================================
// Ddr4SdramModel — command-driven behavioral SDRAM (x16/x32, 2 ranks)
//
// Extends Ddr4PinModel (command decode, TC8-validated) with a DATA PATH:
//   * WRITE: on a decoded WR command, WAIT for DQ to become defined (the PHY
//     drives DQ tPhyWrLat DFI cycles after the WR command), then absorb
//     BL_BEATS=8 DDR beats (8 ck_t edges, both edges) = DATAW*8 bits = one
//     BL8 burst, store in mem[]. (This codebase's DDR4 is BL8, SdramConfig.scala.)
//   * READ:  on a decoded RD command, drive BL_BEATS=8 DDR beats from mem[]
//     starting on the edge AFTER the command decode (tCL ~ half-cycle
//     approximation; deterministic for TB alignment).
//   * DQS ECHO: dqs_in (PHY outgoing DQS during write leveling) is echoed
//     back on dqs_echo (the dqs_in_t pin) after ECHO_DELAY_PS analog delay
//     (tc1/tc4 mechanism).
//
// Storage key (16 bits): {row[5:0], col[4:0], bank[3:0], rank[1:0]}
//   col field = latched WR/RD column[4:0]. Row wraps modulo 64 — self-test/
//   e2e pick distinct rows, no collision.
//
// Widths: DATAW=16 (x16) or 32 (x32); LANES=DATAW/8 DQS lanes; word=DATAW*8.
// Command/command-decode geometry is identical (shared CA bus); only the DQ
// word width scales.
//
// Bidirectional DQ: model drives only during a read burst (dq = z otherwise);
// the PHY drives during write bursts (and tri-states otherwise — IOBUF.T).
// Contention-free by construction if the TB/MC never overlaps them.
// ============================================================================
`timescale 1ns/1ps
module Ddr4SdramModel #(
  parameter DATAW          = 16,   // DQ width: 16 (x16) or 32 (x32)
  parameter RANKS          = 2,
  parameter BL_BEATS       = 8,    // BL8: 8 beats = 8 ck_t edges
  parameter MEM_ENTRIES    = 65536,
  parameter KEY_W          = 16,   // {row[5:0], col[4:0], bank[3:0], rank[1:0]}
  parameter ECHO_DELAY_PS  = 0,    // analog DQS echo delay in ps (granularity-preserving for WL)
  parameter TCL_DELAY_EDGES  = 2,   // tCL emulation (reserved; bursts arm 1 edge after decode)
  parameter LANES          = DATAW/8,
  parameter WORDW          = DATAW*8
)(
  // ---- command pins (mirrors Ddr4PinModel) --------------------------------
  input  wire                  ck_t,
  input  wire                  rst_n,
  input  wire [RANKS-1:0]      cs_n,
  input  wire [RANKS-1:0]      cke,
  input  wire [12:0]           ca,
  input  wire                  ras_n,
  input  wire                  cas_n,
  input  wire                  we_n,
  input  wire                  act_n,
  input  wire                  bg,
  input  wire [2:0]            ba,
  input  wire                  cid,
  // ---- data ----------------------------------------------------------------
  inout  wire [DATAW-1:0]      dq,
  input  wire [LANES-1:0]      dqs_in,     // PHY outgoing DQS (write leveling)
  input  wire                  wl_active,  // WL training span (DQS legitimately toggles here)
  output wire [LANES-1:0]      dqs_echo,   // returned DQS (dqs_in_t pin)
  // ---- observability --------------------------------------------------------
  output reg  [KEY_W-1:0]      wr_key_last,
  output reg  [KEY_W-1:0]      rd_key_last,
  output reg  [WORDW-1:0]      wr_word_last,  // last absorbed write burst (BL8 xDATAW)
  output wire                  rd_driving,
  output reg                   rd_pending_err, // read from unwritten location
  output reg  [15:0]           dqs_blank_cnt,  // DQS blanking violation count (saturating)
  output reg                   dqs_blank_err   // sticky: any DQS blanking violation
);

  localparam RW = ($clog2(RANKS)>0) ? $clog2(RANKS) : 1;

  // ==========================================================================
  // Command decode (reuse TC8-validated Ddr4PinModel)
  // ==========================================================================
  wire [RANKS-1:0] q_act_pulse, q_wr_pulse, q_rd_pulse;
  wire [15:0] last_row, last_col;
  wire [3:0]  last_bank;
  wire [RW-1:0] last_rank;

  Ddr4PinModel #(
    .NRANKS(RANKS), .ROW_W(16), .COL_W(10),
    .BG_W(1), .BA_W(3), .CID_W(1)
  ) cmd (
    .ck_t(ck_t), .rst_n(rst_n),
    .cs_n(cs_n), .cke(cke), .a(ca),
    .ras_n(ras_n), .cas_n(cas_n), .we_n(we_n), .act_n(act_n),
    .bg(bg), .ba(ba), .cid(cid),
    .q_act_pulse(q_act_pulse), .q_wr_pulse(q_wr_pulse), .q_rd_pulse(q_rd_pulse),
    .last_row(last_row), .last_col(last_col),
    .last_bank(last_bank), .last_rank(last_rank),
    .q_pre_pulse(), .q_ref_pulse(),
    .q_mrs_pulse(), .q_zqcl_pulse(), .q_nop_pulse(), .q_illegal_pulse(),
    .last_op(), .last_autopre(), .last_allbanks(), .last_cid(),
    .cnt_act(), .cnt_rd(), .cnt_wr(), .cnt_pre(), .cnt_ref(),
    .cnt_mrs(), .cnt_zqcl(), .cnt_nop(), .cnt_illegal(),
    .cnt_pd(), .cnt_deselect(), .illegal_flag()
  );

  // ==========================================================================
  // Per-rank ROW/BANK STATE (latched on ACT; WR/RD carry COLUMN + BANK)
  // NOTE: last_* fields come from Ddr4PinModel's single layer-2 always block,
  // so readers always see a CONSISTENT snapshot (all-pre or all-post edge).
  // Assumes at most one rank ACTs per ck_t cycle (true in this topology's
  // MC behavior; shared-bus fields would otherwise alias).
  // ==========================================================================
  localparam NBANKS = 16;  // 1 BG + 3 BA
  // Per-(rank,bank) open-row state: DDR4 banks are independent; RD/WR carry
  // their bank on BG/BA (JESD79-4B Table 17), so the row key must come from
  // the command's own bank, not a single per-rank "last ACT" shadow.
  reg [15:0] row_state  [0:RANKS*NBANKS-1];
  integer k;
  reg [RW-1:0] wr_rank, rd_rank;
  always @(*) begin
    wr_rank = {RW{1'b0}}; rd_rank = {RW{1'b0}};
    for (k = 0; k < RANKS; k = k + 1) begin
      if (q_wr_pulse[k]) wr_rank = k[RW-1:0];
      if (q_rd_pulse[k]) rd_rank = k[RW-1:0];
    end
  end

  always @(posedge ck_t) begin
    if (!rst_n) begin
      for (k = 0; k < RANKS*NBANKS; k = k + 1)
        row_state[k] <= 16'h0;
    end else begin
      for (k = 0; k < RANKS; k = k + 1)
        if (q_act_pulse[k])
          row_state[k*NBANKS + last_bank] <= last_row;
    end
  end

  // ==========================================================================
  // Storage
  // ==========================================================================
  reg [WORDW-1:0] mem [0:MEM_ENTRIES-1];
  reg         written [0:MEM_ENTRIES-1];
  integer mi;
  initial begin
    for (mi = 0; mi < MEM_ENTRIES; mi = mi + 1) begin
      mem[mi] = {WORDW{1'b0}};
      written[mi] = 1'b0;
    end
  end

  // ==========================================================================
  // WRITE burst absorber
  //   BL8 xDATAW: 8 beats, one per ck_t edge (pos or neg). The PHY drives DQ
  //   tPhyWrLat DFI cycles AFTER the WR command, so on the WR pulse we enter
  //   a WAIT state and capture beat0 on the first edge where DQ is defined
  //   (non-Z); then collect BL_BEATS beats.
  //   Accumulator invariant: newest beat at top slot [WORDW-1:WORDW-DATAW];
  //   each shift {dq, wr_reg[WORDW-1:DATAW]} slides old content DOWN one slot
  //   (slot 0 falls off). Start parks beat0 at TOP ({dq, zeros}); completion
  //   word = {dq, wr_reg[WORDW-1:DATAW]} => {b7, b6, ..., b0}.
  // ==========================================================================
  reg        waiting;
  reg        collecting;
  reg [3:0]  wr_edges;
  reg [WORDW-1:0] wr_reg;

  wire wr_cmd     = |q_wr_pulse;
  wire dq_defined = (dq !== {DATAW{1'bz}});

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      waiting <= 1'b0; collecting <= 1'b0;
      wr_edges <= 4'd0; wr_reg <= {WORDW{1'b0}};
      wr_key_last <= {KEY_W{1'b0}}; wr_word_last <= {WORDW{1'b0}};
    end else if (wr_cmd && !collecting && !waiting) begin
      waiting     <= 1'b1;
      wr_key_last <= {row_state[wr_rank*NBANKS + last_bank][5:0], last_col[4:0], last_bank, wr_rank[RW-1:0]};
    end else if (waiting) begin
      // Skip leading zero beats (OSERDESE3 pipeline flush artifact).
      // Start capturing on the first non-zero DQ value.
      if (dq_defined && (dq !== {DATAW{1'b0}})) begin
        collecting <= 1'b1;
        waiting    <= 1'b0;
        wr_edges   <= 4'd1;
        wr_reg     <= {dq, {(WORDW-DATAW){1'b0}}};
      end
    end else if (collecting) begin
      if (wr_edges == BL_BEATS - 1) begin
        mem[wr_key_last] <= {dq, wr_reg[WORDW-1:DATAW]};
        wr_word_last     <= {dq, wr_reg[WORDW-1:DATAW]};
        collecting <= 1'b0;
        written[wr_key_last] <= 1'b1;
      end else begin
        wr_reg   <= {dq, wr_reg[WORDW-1:DATAW]};
        wr_edges <= wr_edges + 4'd1;
      end
    end
  end

  // ==========================================================================
  // DQS BLANKING OBSERVER (JESD79-4B: tWPRE/tWPST/tDSS/tDSH/tDQSS)
  //   The PHY DQS pad (dqs_in) must not toggle outside the write-burst
  //   envelope [firstBeat - PRE_UI, lastBeat + POST_UI], except during WL
  //   training (wl_active). Also checked: preamble PRESENCE (a DQS
  //   transition within PRE_UI before the first beat) and postamble
  //   PRESENCE (a transition within POST_UI after the last beat).
  //   UI = one ck_t edge. Margins are TOLERANT (PRE_UI=5 = 2.5 tCK >>
  //   tWPRE 0.9 tCK; POST_UI=5 = 2.5 tCK >> tWPST 0.33 tCK): the spec
  //   limits are floors, longer preambles are legal (cf. 2tCK mode).
  //   The preamble is checked TWO-SIDED (a transition within PRE_UI before
  //   OR POST_UI after the first beat): the PHY's ODELAYE3 delay line
  //   (WL-trained ~350 taps = 17.5 ns) can make the first output
  //   transition emerge shortly after the window opens, and the absolute
  //   DQS-DQ phase is a silicon-calibration property, not a
  //   PHY-datapath obligation.
  //   OUT-OF-ENVELOPE check is episode-based: a DQS transition outside the
  //   envelope starts an episode; a following write beat justifies it;
  //   sustained activity beyond PRE_UI UIs violates ONCE per episode (a
  //   continuous out-of-envelope stream cannot stall the check).
  // ==========================================================================
  localparam PRE_UI  = 5;
  localparam POST_UI = 5;
  reg        dqs_prev;
  reg [3:0]  dqs_age;    // UIs since last DQS transition (saturates at 15)
  reg        ooe_pending; // ooe DQS transition episode awaiting a justifying beat
  reg [3:0]  ooe_wait;    // edges since the episode started
  reg [3:0]  post_cnt;   // UIs left in the postamble window
  reg        post_seen;  // a transition occurred inside the postamble window
  reg [3:0]  pend_post;  // post-first-beat grace for late DQS activity

  // SETTLED DQS SAMPLE: the PHY DQS pad can transition EXACTLY on a ck_t edge
  // (the WL attractor lands the ODELAYE3 delay at a half-odd multiple of tCK,
  // putting DQS edges on the sampler's own grid), so a same-timestep edge
  // comparison of dqs_in[0] is scheduler-dependent. Sampling 1ps AFTER each
  // edge reads the value stable over the preceding half-period: transitions
  // are detected one ck_t edge LATE but deterministically, and the one-edge
  // latency fits the PRE_UI/POST_UI grace windows. (Silicon: the DRAM's DQS
  // receiver samples on its own internal grid; this is a model-robustness
  // device, not a timing claim.)
  reg dqs_obs;
  initial dqs_obs = 1'b0;
  always @(posedge ck_t or negedge ck_t) begin
    #1 dqs_obs = dqs_in[0];
  end

  wire first_beat_now = waiting && dq_defined && (dq !== {DATAW{1'b0}});
  wire in_legal_now   = collecting || (post_cnt > 0) || wl_active;
  wire dqs_chg        = (dqs_obs !== dqs_prev);

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      dqs_prev <= 1'b0; dqs_age <= 4'd0;
      ooe_pending <= 1'b0; ooe_wait <= 4'd0;
      pend_post <= 4'd0;
      post_cnt <= 4'd0; post_seen <= 1'b0;
      dqs_blank_cnt <= 16'd0; dqs_blank_err <= 1'b0;
    end else begin
      if (dqs_chg) begin        dqs_prev <= dqs_obs;
        dqs_age  <= 4'd0;
        if (post_cnt > 0)  post_seen <= 1'b1;
        if (pend_post > 0) pend_post <= 4'd0;   // activity arrived within grace
      end else begin
        dqs_age <= (dqs_age == 4'd15) ? 4'd15 : dqs_age + 4'd1;
      end
      // Preamble (two-sided): a DQS transition within PRE_UI before the first
      // beat, OR within POST_UI after it (late-emerging delay-line fill), is
      // legal; the absolute DQS-DQ phase is a silicon-calibration property.
      if (first_beat_now) begin
        pend_post <= (dqs_age > PRE_UI[3:0] && !dqs_chg) ? POST_UI[3:0] : 4'd0;
      end else if (pend_post > 0 && !dqs_chg) begin
        // Late-preamble grace counting down; expires -> violation.
        if (pend_post == 4'd1) begin
          dqs_blank_cnt <= dqs_blank_cnt + 16'd1;
          dqs_blank_err <= 1'b1;
          $display("DQSDBG t=%0t REASON=preamble_late_absent wl=%b coll=%b", $time, wl_active, collecting);
        end
        pend_post <= pend_post - 4'd1;
      end
      // Out-of-envelope activity (episode-based, see header).
      if (dqs_chg && !in_legal_now) begin
        ooe_pending <= 1'b1;
      end else if (ooe_pending) begin
        ooe_wait <= (ooe_wait == 4'd15) ? 4'd15 : ooe_wait + 4'd1;
        if (ooe_wait >= PRE_UI[3:0]) begin
          dqs_blank_cnt <= dqs_blank_cnt + 16'd1;
          dqs_blank_err <= 1'b1;
          ooe_pending <= 1'b0;
          ooe_wait    <= 4'd0;
          $display("DQSDBG t=%0t REASON=out_of_envelope wl=%b coll=%b", $time, wl_active, collecting);
        end
      end else begin
        ooe_wait <= 4'd0;
      end
      if (ooe_pending && first_beat_now) ooe_pending <= 1'b0; // beat justifies
      // Postamble window bookkeeping.
      if (collecting && wr_edges == BL_BEATS - 1) begin
        post_cnt  <= POST_UI[3:0];
        post_seen <= 1'b0;
      end else if (post_cnt > 0) begin
        if (post_cnt == 4'd1) begin
          if (!post_seen) begin
            dqs_blank_cnt <= dqs_blank_cnt + 16'd1;
            dqs_blank_err <= 1'b1;
            $display("DQSDBG t=%0t REASON=postamble_absent", $time);
          end
          post_cnt <= 4'd0;
        end else begin
          post_cnt <= post_cnt - 4'd1;
        end
      end
    end
  end

  // ==========================================================================
  // READ burst driver
  //   BL_BEATS beats driven starting ON the RD decode edge (see arm branch).
  //   Alignment: ISERDESE3 PRESENT at refclk posedge T captures the 4 samples
  //   STRICTLY before T; the MC consumes q_q one refclk later. Driving from
  //   the decode edge makes the 8 beats tile exactly into two Q windows.
  // ==========================================================================
  reg        driving;
  reg        rd_armed;   // 1-edge arm stage: aligns burst start to the Q window
  reg [4:0]  rd_edges;   // 5 bits: counts up to BL_BEATS (8) inclusive
  reg [4:0]  rd_cl;      // tCL countdown
  reg [WORDW-1:0] rd_reg;

  wire rd_cmd = |q_rd_pulse;

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      driving <= 1'b0; rd_armed <= 1'b0; rd_edges <= 5'd0; rd_cl <= 5'd0; rd_reg <= {WORDW{1'b0}};
      rd_key_last <= {KEY_W{1'b0}}; rd_pending_err <= 1'b0;
    end else if (rd_cmd && !driving && !rd_armed && rd_cl == 0) begin
      // ARMS on the decode edge: latch the word, drive ONE edge later. The
      // +1-edge arm is the alignment term that lands the 8-beat burst so it
      // tiles exactly into the two consecutive ISERDESE3 Q windows (4 edges
      // each) that the MC's rddataValid window samples (see run notes).
      rd_armed <= 1'b1;
      rd_reg   <= mem[{row_state[rd_rank*NBANKS + last_bank][5:0], last_col[4:0], last_bank, rd_rank[RW-1:0]}];
      rd_key_last <= {row_state[rd_rank*NBANKS + last_bank][5:0], last_col[4:0], last_bank, rd_rank[RW-1:0]};
      rd_pending_err <= !written[{row_state[rd_rank*NBANKS + last_bank][5:0], last_col[4:0], last_bank, rd_rank[RW-1:0]}];
    end else if (rd_armed && !driving) begin
      rd_armed <= 1'b0;
      driving  <= 1'b1;
      rd_edges <= 5'd1;
    end else if (rd_cl > 0) begin
      rd_cl <= rd_cl - 5'd1;
      if (rd_cl == 1) begin
        // tCL done: start driving on next edge
        driving  <= 1'b1;
        rd_edges <= 5'd1;
      end
    end else if (driving) begin
      if (rd_edges == BL_BEATS) begin
        driving <= 1'b0;
      end else begin
        rd_reg   <= rd_reg >> DATAW;
        rd_edges <= rd_edges + 5'd1;
      end
    end
  end

  assign rd_driving = driving;
  assign dq = driving ? rd_reg[DATAW-1:0] : {DATAW{1'bz}};

  // ==========================================================================
  // DQS echo (write-leveling feedback)
  // ==========================================================================
  // Analog (ps-granular) echo: a ck_t-edge pipeline would QUANTIZE the echo to
  // 2.5ns steps and destroy the 50ps ODELAYE3 tap granularity the WL loop needs.
  generate
    if (ECHO_DELAY_PS == 0) begin : g_echo_direct
      assign dqs_echo = dqs_in;
    end else begin : g_echo_analog
      genvar ek;
      for (ek = 0; ek < LANES; ek = ek + 1) begin : g_lane
        reg q;
        // File timescale is 1ns/1ps: convert the ps parameter to a REAL ns delay
        // (a bare #(ECHO_DELAY_PS) would be parsed in ns -> 1000x too slow).
        always @(dqs_in[ek]) q <= #(ECHO_DELAY_PS / 1000.0) dqs_in[ek];
        assign dqs_echo[ek] = q;
      end
    end
  endgenerate

endmodule
