// ============================================================================
// Ddr4SdramModel — command-driven behavioral SDRAM (x16, 2 ranks)
//
// Extends Ddr4PinModel (command decode, TC8-validated) with a DATA PATH:
//   * WRITE: on a decoded WR command, absorb 16 DDR beats (16 ck_t edges,
//     both edges) = 256 bits = one BL16 x16 burst, store in mem[].
//     First beat absorbed on the SAME edge as the command decode.
//   * READ:  on a decoded RD command, drive 16 DDR beats from mem[] starting
//     on the edge AFTER the command decode (tCL ~ half-cycle approximation;
//     deterministic for TB alignment).
//   * DQS ECHO: dqs_in (PHY outgoing DQS during write leveling) is echoed
//     back on dqs_echo (the dqs_in_t pin) after ECHO_DELAY_EDGES ck_t edges
//     (tc1/tc4 mechanism).
//
// Storage key (12 bits): {row[5:0], col32blk[4:0], bank[3:0], rank[1:0]}
//   col32blk = col[4:0] = 32-byte block index (BL16 x16 spans exactly 32B).
//   Row wraps modulo 64 — self-test/e2e pick distinct rows, no collision.
//
// Bidirectional DQ: model drives only during a read burst (dq = z otherwise);
// the PHY drives during write bursts (and tri-states otherwise — IOBUF.T).
// Contention-free by construction if the TB/MC never overlaps them.
// ============================================================================
`timescale 1ns/1ps
module Ddr4SdramModel #(
  parameter RANKS          = 2,
  parameter BL_BEATS       = 16,   // BL16 x16: 16 beats = 16 ck_t edges
  parameter MEM_ENTRIES    = 65536,
  parameter KEY_W          = 16,   // {row[5:0], col[4:0], bank[3:0], rank[1:0]}
  parameter ECHO_DELAY_EDGES = 0
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
  output reg  [255:0]          wr_word_last,  // last absorbed write burst
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
  reg [15:0] row_state  [0:RANKS-1];
  reg [3:0]  bank_state [0:RANKS-1];
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
      for (k = 0; k < RANKS; k = k + 1) begin
        row_state[k]  <= 16'h0;
        bank_state[k] <= 4'h0;
      end
    end else begin
      for (k = 0; k < RANKS; k = k + 1)
        if (q_act_pulse[k]) begin
          row_state[k]  <= last_row;
          bank_state[k] <= last_bank;
        end
    end
  end

  // ==========================================================================
  // Storage
  // ==========================================================================
  reg [255:0] mem [0:MEM_ENTRIES-1];
  reg         written [0:MEM_ENTRIES-1];
  integer mi;
  initial begin
    for (mi = 0; mi < MEM_ENTRIES; mi = mi + 1) begin
      mem[mi] = 256'h0;
      written[mi] = 1'b0;
    end
  end

  // ==========================================================================
  // WRITE burst absorber
  //   BL16 x16: 16 beats, one per ck_t edge (pos or neg), starting on the
  //   first edge AFTER the WR decode edge (when q_wr_pulse becomes visible).
  //   Accumulator invariant: newest beat at top slot [255:240]; each shift
  //   {dq, wr_reg[255:16]} slides old content DOWN one slot (slot 0 falls off).
  //   Start therefore parks beat0 at TOP ({dq, 240'h0}) so it survives all
  //   14 interior shifts and sits in slot 1 at completion, inside [255:16].
  //   Completion word = {dq, wr_reg[255:16]} => {b15, b14, ..., b0}.
  // ==========================================================================
  reg        collecting;
  reg [3:0]  wr_edges;
  reg [255:0] wr_reg;

  wire wr_cmd = |q_wr_pulse;

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      collecting <= 1'b0; wr_edges <= 4'd0; wr_reg <= 256'h0;
      wr_key_last <= {KEY_W{1'b0}}; wr_word_last <= 256'h0;
    end else if (wr_cmd && !collecting) begin
      collecting <= 1'b1;
      wr_edges   <= 4'd1;
      wr_reg     <= {dq, 240'h0};
      wr_key_last <= {row_state[wr_rank][5:0], last_col[4:0], bank_state[wr_rank], wr_rank[RW-1:0]};
    end else if (collecting) begin
      if (wr_edges == BL_BEATS - 1) begin
        mem[wr_key_last] <= {dq, wr_reg[255:16]};
        wr_word_last     <= {dq, wr_reg[255:16]};
        collecting <= 1'b0;
        written[wr_key_last] <= 1'b1;
      end else begin
        wr_reg   <= {dq, wr_reg[255:16]};
        wr_edges <= wr_edges + 4'd1;
      end
    end
  end

  // ==========================================================================
  // READ burst driver
  //   16 beats driven starting on the edge AFTER the RD command decode.
  //   Model drives dq only while rd_driving (z otherwise).
  // ==========================================================================
  reg        driving;
  reg [4:0]  rd_edges;   // 5 bits: counts up to BL_BEATS (16) inclusive
  reg [255:0] rd_reg;

  wire rd_cmd = |q_rd_pulse;

  always @(posedge ck_t or negedge ck_t) begin
    if (!rst_n) begin
      driving <= 1'b0; rd_edges <= 5'd0; rd_reg <= 256'h0;
      rd_key_last <= {KEY_W{1'b0}}; rd_pending_err <= 1'b0;
    end else if (rd_cmd && !driving) begin
      driving <= 1'b1;
      rd_edges <= 5'd1;
      rd_reg   <= mem[{row_state[rd_rank][5:0], last_col[4:0], bank_state[rd_rank], rd_rank[RW-1:0]}];
      rd_key_last <= {row_state[rd_rank][5:0], last_col[4:0], bank_state[rd_rank], rd_rank[RW-1:0]};
      rd_pending_err <= !written[{row_state[rd_rank][5:0], last_col[4:0], bank_state[rd_rank], rd_rank[RW-1:0]}];
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
  generate
    if (ECHO_DELAY_EDGES == 0) begin : g_echo_direct
      assign dqs_echo = dqs_in;
    end else begin : g_echo_pipe
      reg [1:0] pipe [0:ECHO_DELAY_EDGES-1];
      integer ei;
      always @(posedge ck_t or negedge ck_t) begin
        pipe[0] <= dqs_in;
        for (ei = 1; ei < ECHO_DELAY_EDGES; ei = ei + 1) pipe[ei] <= pipe[ei-1];
      end
      assign dqs_echo = pipe[ECHO_DELAY_EDGES-1];
    end
  endgenerate

endmodule
