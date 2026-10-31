// ============================================================================
// Ddr4SdramModel — command-driven behavioral SDRAM (x16, 2 ranks)
//
// Extends Ddr4PinModel (command decode, TC8-validated) with a DATA PATH:
//   * WRITE: on a decoded WR command, WAIT for DQ to become defined (the PHY
//     drives DQ tPhyWrLat DFI cycles after the WR command), then absorb
//     BL_BEATS=8 DDR beats (8 ck_t edges, both edges) = 128 bits = one BL8 x16
//     burst, store in mem[]. (This codebase's DDR4 is BL8, SdramConfig.scala.)
//   * READ:  on a decoded RD command, drive BL_BEATS=8 DDR beats from mem[]
//     starting on the edge AFTER the command decode (tCL ~ half-cycle
//     approximation; deterministic for TB alignment).
//   * DQS ECHO: dqs_in (PHY outgoing DQS during write leveling) is echoed
//     back on dqs_echo (the dqs_in_t pin) after ECHO_DELAY_EDGES ck_t edges
//     (tc1/tc4 mechanism).
//
// Storage key (16 bits): {row[5:0], col[4:0], bank[3:0], rank[1:0]}
//   col field = latched WR/RD column[4:0]. Row wraps modulo 64 — self-test/
//   e2e pick distinct rows, no collision.
//
// Bidirectional DQ: model drives only during a read burst (dq = z otherwise);
// the PHY drives during write bursts (and tri-states otherwise — IOBUF.T).
// Contention-free by construction if the TB/MC never overlaps them.
// ============================================================================
`timescale 1ns/1ps
module Ddr4SdramModel #(
  parameter RANKS          = 2,
  parameter BL_BEATS       = 8,    // BL8 x16: 8 beats = 8 ck_t edges
  parameter MEM_ENTRIES    = 65536,
  parameter KEY_W          = 16,   // {row[5:0], col[4:0], bank[3:0], rank[1:0]}
  parameter ECHO_DELAY_PS    = 0,   // analog DQS echo delay in ps (granularity-preserving for WL)
  parameter TCL_DELAY_EDGES  = 2    // tCL emulation: delay before driving DQ on read;
                                     // MUST land the first beat's drive interval on a
                                     // refclk POSEDGE (bursts align to refclk cycles,
                                     // one 4-beat Q window per PRESENT edge).
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
  inout  wire [15:0]           dq,
  input  wire [1:0]            dqs_in,     // PHY outgoing DQS (write leveling)
  output wire [1:0]            dqs_echo,   // returned DQS (dqs_in_t pin)
  // ---- observability --------------------------------------------------------
  output reg  [KEY_W-1:0]      wr_key_last,
  output reg  [KEY_W-1:0]      rd_key_last,
  output reg  [127:0]          wr_word_last,  // last absorbed write burst (BL8 x16 = 128b)
  output wire                  rd_driving,
  output reg                   rd_pending_err // read from unwritten location
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
  // Per-rank ROW/BANK STATE (latched on ACT; WR/RD only carry COLUMN)
  // NOTE: last_* fields come from Ddr4PinModel's single layer-2 always block,
  // so readers always see a CONSISTENT snapshot (all-pre or all-post edge).
  // Assumes at most one rank ACTs per ck_t cycle (true in this topology's
  // MC behavior; shared-bus fields would otherwise alias).
  // ==========================================================================
  localparam NBANKS = 16;  // 1 BG + 3 BA
  // Per-(rank,bank) open-row state: DDR4 banks are independent; RD/WR carry
  // their bank on BG/BA (Table 17), so the row key must come from the
  // command's own bank, not a single per-rank "last ACT" shadow.
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
  reg [127:0] mem [0:MEM_ENTRIES-1];
  reg         written [0:MEM_ENTRIES-1];
  integer mi;
  initial begin
    for (mi = 0; mi < MEM_ENTRIES; mi = mi + 1) begin
      mem[mi] = 128'h0;
      written[mi] = 1'b0;
    end
  end

  // ==========================================================================
  // WRITE burst absorber
  //   BL8 x16: 8 beats, one per ck_t edge (pos or neg). The PHY drives DQ
  //   tPhyWrLat DFI cycles AFTER the WR command, so on the WR pulse we enter
  //   a WAIT state and capture beat0 on the first edge where DQ is defined
  //   (non-Z); then collect BL_BEATS beats.
  //   Accumulator invariant: newest beat at top slot [127:112]; each shift
  //   {dq, wr_reg[127:16]} slides old content DOWN one slot (slot 0 falls off).
  //   Start parks beat0 at TOP ({dq, 112'h0}); completion word =
  //   {dq, wr_reg[127:16]} => {b7, b6, ..., b0}.
  // ==========================================================================
  reg        waiting;
  reg        collecting;
  reg [3:0]  wr_edges;
  reg [127:0] wr_reg;

  wire wr_cmd     = |q_wr_pulse;
  wire dq_defined = (dq !== 16'hzzzzzzzzzzzzzzzz);

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      waiting <= 1'b0; collecting <= 1'b0;
      wr_edges <= 4'd0; wr_reg <= 128'h0;
      wr_key_last <= {KEY_W{1'b0}}; wr_word_last <= 128'h0;
    end else if (wr_cmd && !collecting && !waiting) begin
      waiting     <= 1'b1;
      wr_key_last <= {row_state[wr_rank*NBANKS + last_bank][5:0], last_col[4:0], last_bank, wr_rank[RW-1:0]};
    end else if (waiting) begin
      // Skip leading zero beats (OSERDESE3 pipeline flush artifact).
      // Start capturing on the first non-zero DQ value.
      if (dq_defined && (dq !== 16'h0000)) begin
        collecting <= 1'b1;
        waiting    <= 1'b0;
        wr_edges   <= 4'd1;
        wr_reg     <= {dq, 112'h0};
      end
    end else if (collecting) begin
      if (wr_edges == BL_BEATS - 1) begin
        mem[wr_key_last] <= {dq, wr_reg[127:16]};
        wr_word_last     <= {dq, wr_reg[127:16]};
        collecting <= 1'b0;
        written[wr_key_last] <= 1'b1;
      end else begin
        wr_reg   <= {dq, wr_reg[127:16]};
        wr_edges <= wr_edges + 4'd1;
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
  reg [127:0] rd_reg;

  wire rd_cmd = |q_rd_pulse;

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      driving <= 1'b0; rd_armed <= 1'b0; rd_edges <= 5'd0; rd_cl <= 5'd0; rd_reg <= 128'h0;
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
        rd_reg   <= rd_reg >> 16;
        rd_edges <= rd_edges + 5'd1;
      end
    end
  end

  assign rd_driving = driving;
  assign dq = driving ? rd_reg[15:0] : 16'bz;

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
      for (ek = 0; ek < 2; ek = ek + 1) begin : g_lane
        reg q;
        // File timescale is 1ns/1ps: convert the ps parameter to a REAL ns delay
        // (a bare #(ECHO_DELAY_PS) would be parsed in ns -> 1000x too slow).
        always @(dqs_in[ek]) q <= #(ECHO_DELAY_PS / 1000.0) dqs_in[ek];
        assign dqs_echo[ek] = q;
      end
    end
  endgenerate

endmodule
