// ============================================================================
// Ddr4PinModel.v — pin-level DDR4 command decoder (L3 behavioral model)
//
// WHAT THIS IS
//   A behavioral SDRAM command receiver that decodes commands from the
//   PHYSICAL PINS (like a real DRAM would), not from the DFI bus. It samples
//   the CA/command pin level combo on every CK (ck_t) RISING edge and decodes
//   the command TYPE per the JESD79-4B Table 37 truth table — the same
//   encodings the MC (Task2IDFI) emits (see ADR-0008):
//
//     CS#=0 (selected), CKE=1:
//       ACT# = 0                          -> ACTIVATE, row = {RAS,CAS,WE,A[12:0]}
//                                                (RA[15:13] ride RAS#/CAS#/WE# pins)
//       ACT# = 1:
//         RAS=1 CAS=0 WE=1                -> READ   (col=A[colW-1:0], autoPre=A10)
//         RAS=1 CAS=1 WE=0                -> WRITE  (col=A[colW-1:0], autoPre=A10)
//         RAS=0 CAS=1 WE=0                -> PRECHARGE (bank, allBanks=A10)
//         RAS=0 CAS=0 WE=1                -> REFRESH
//         RAS=0 CAS=0 WE=0                -> MRS    (op=A[9:6], bank)
//         RAS=1 CAS=1 WE=1                -> ZQCL if A10=1 else NOP
//       anything else                     -> ILLEGAL (counted + flagged)
//     CS#=1 (deselected)                  -> ignored
//     CKE=0                               -> power-down, ignored (counted)
//
// WHY IT EXISTS (L3)
//   TC7 proved the PHY is a faithful TRANSPARENCY layer (DFI phase-0 bit ->
//   pin). This model proves the SEMANTICS: that the pin level combos are
//   LEGAL JESD79-4B commands of the INTENDED type, decoded exactly ONCE per
//   intended command. It is the first pin-level receiver capable of catching
//   command TIMING/PHASING bugs (e.g. a command held across two DRAM cycles
//   being decoded twice) that a pure transparency check structurally cannot.
//
// ARCHITECTURE (two clean layers)
//   1. COMBINATIONAL decode of the CURRENT pin levels into per-rank onehots
//      (c_*) — "what would the DRAM decode right now".
//   2. SEQUENTIAL sample on ck_t rising edge: publishes 1-cycle pulses
//      (q_*), bumps saturating counters (cnt_*), latches last-decode fields.
//
// SCOPE / LIMITATIONS (v1)
//   - Command DECODE only. No DQ/DQS data return, no timing-param checks
//     (tRCD/tRRD/tFAW...), no MR file side-effects, no ALERT_n. Those are
//     Step-8 (end-to-end) scope.
//   - Samples on ck_t rising edge only (DDR4 rising-edge command decode).
//   - Bank = {bg, ba}; row = {ras,cas,we,a[12:0]} (16 bits); col = a[colW-1:0].
//   - cid is NOT used for rank select (cs_n is the rank selector in this
//     topology); decoded cid is exposed for observation.
//   - last_* fields latch from the highest-index rank that decoded this cycle
//     (shared-bus fields are identical across ranks; rank id differs).
// ============================================================================
`timescale 1ns/1ps
module Ddr4PinModel #(
  parameter NRANKS = 2,
  parameter ADDR_W = 13,          // A[12:0]
  parameter BG_W   = 1,
  parameter BA_W   = 3,
  parameter CID_W  = 1,
  parameter COL_W  = 10,
  parameter ROW_W  = 16           // {RAS,CAS,WE,A[12:0]}
)(
  input  wire                 ck_t,      // DRAM CK (sample on rising edge)
  input  wire                 rst_n,
  input  wire [NRANKS-1:0]    cs_n,      // per-rank CS# (active low)
  input  wire [NRANKS-1:0]    cke,       // per-rank CKE
  input  wire                 act_n,
  input  wire                 ras_n,     // = A16 on ACT
  input  wire                 cas_n,     // = A15 on ACT
  input  wire                 we_n,      // = A14 on ACT
  input  wire [BG_W-1:0]      bg,
  input  wire [BA_W-1:0]      ba,
  input  wire [ADDR_W-1:0]    a,         // A[12:0]
  input  wire [CID_W-1:0]     cid,

  // ---- per-rank decode pulses (1 ck_t cycle wide) -------------------------
  output reg  [NRANKS-1:0]    q_act_pulse,
  output reg  [NRANKS-1:0]    q_rd_pulse,
  output reg  [NRANKS-1:0]    q_wr_pulse,
  output reg  [NRANKS-1:0]    q_pre_pulse,
  output reg  [NRANKS-1:0]    q_ref_pulse,
  output reg  [NRANKS-1:0]    q_mrs_pulse,
  output reg  [NRANKS-1:0]    q_zqcl_pulse,
  output reg  [NRANKS-1:0]    q_nop_pulse,
  output reg  [NRANKS-1:0]    q_illegal_pulse,

  // ---- latched last-decode fields ----------------------------------------
  output reg  [ROW_W-1:0]     last_row,
  output reg  [COL_W-1:0]     last_col,
  output reg  [BG_W+BA_W-1:0] last_bank,
  output reg  [3:0]           last_op,
  output reg                  last_autopre,
  output reg                  last_allbanks,
  output reg  [CID_W-1:0]     last_cid,
  output reg  [($clog2(NRANKS)>0 ? $clog2(NRANKS) : 1)-1:0] last_rank,

  // ---- counters (16-bit saturating) --------------------------------------
  output reg  [15:0]          cnt_act,
  output reg  [15:0]          cnt_rd,
  output reg  [15:0]          cnt_wr,
  output reg  [15:0]          cnt_pre,
  output reg  [15:0]          cnt_ref,
  output reg  [15:0]          cnt_mrs,
  output reg  [15:0]          cnt_zqcl,
  output reg  [15:0]          cnt_nop,
  output reg  [15:0]          cnt_illegal,
  output reg  [15:0]          cnt_pd,
  output reg  [15:0]          cnt_deselect,
  output reg                  illegal_flag
);

  localparam RW = ($clog2(NRANKS)>0) ? $clog2(NRANKS) : 1;

  // ==========================================================================
  // Layer 1: COMBINATIONAL decode of current pin levels (per rank)
  // ==========================================================================
  reg [NRANKS-1:0] c_act, c_rd, c_wr, c_pre, c_ref, c_mrs, c_zqcl, c_nop, c_illegal;
  reg [ROW_W-1:0]  c_row;
  reg [COL_W-1:0]  c_col;
  reg [BG_W+BA_W-1:0] c_bank;
  reg [3:0]        c_op;
  reg              c_autopre, c_allbanks;
  reg [CID_W-1:0]  c_cid;
  reg [RW-1:0]     c_rank;

  integer k;
  always @* begin
    c_act = {NRANKS{1'b0}}; c_rd = {NRANKS{1'b0}}; c_wr = {NRANKS{1'b0}};
    c_pre = {NRANKS{1'b0}}; c_ref = {NRANKS{1'b0}}; c_mrs = {NRANKS{1'b0}};
    c_zqcl = {NRANKS{1'b0}}; c_nop = {NRANKS{1'b0}}; c_illegal = {NRANKS{1'b0}};
    c_row = {ROW_W{1'b0}}; c_col = {COL_W{1'b0}}; c_bank = {(BG_W+BA_W){1'b0}};
    c_op = 4'd0; c_autopre = 1'b0; c_allbanks = 1'b0;
    c_cid = {CID_W{1'b0}}; c_rank = {RW{1'b0}};

    for (k = 0; k < NRANKS; k = k + 1) begin
      if (cs_n[k]) begin
        // Deselected: nothing to decode.
      end else if (!cke[k]) begin
        // Power-down: CKE low, command bus ignored.
      end else if (!act_n) begin
        // ---- ACTIVATE: row = {RAS,CAS,WE,A[12:0]} ------------------------
        c_act[k] = 1'b1;
        c_row   = {ras_n, cas_n, we_n, a};
        c_bank  = {bg, ba};
        c_cid   = cid;
        c_rank  = k[RW-1:0];
      end else begin
        // ---- ACT# = 1: decode {RAS,CAS,WE} -------------------------------
        case ({ras_n, cas_n, we_n})
          3'b101: begin // READ — C[9:0]+A10, bank on BG/BA (Table 17: RD BG=BG BA=BA)
            c_rd[k] = 1'b1;
            c_col = a[COL_W-1:0];
            c_bank = {bg, ba};
            c_autopre = a[10]; c_cid = cid; c_rank = k[RW-1:0];
          end
          3'b110: begin // WRITE — same latching as READ
            c_wr[k] = 1'b1;
            c_col = a[COL_W-1:0];
            c_bank = {bg, ba};
            c_autopre = a[10]; c_cid = cid; c_rank = k[RW-1:0];
          end
          3'b010: begin // PRECHARGE
            c_pre[k] = 1'b1;
            c_bank = {bg, ba}; c_allbanks = a[10];
            c_cid = cid; c_rank = k[RW-1:0];
          end
          3'b001: begin // REFRESH
            c_ref[k] = 1'b1;
            c_cid = cid; c_rank = k[RW-1:0];
          end
          3'b000: begin // MRS
            c_mrs[k] = 1'b1;
            c_op = a[9:6]; c_bank = {bg, ba};
            c_cid = cid; c_rank = k[RW-1:0];
          end
          3'b111: begin // ZQCL or NOP (disambiguated by A10)
            if (a[10]) begin
              c_zqcl[k] = 1'b1;
            end else begin
              c_nop[k] = 1'b1;
            end
            c_cid = cid; c_rank = k[RW-1:0];
          end
          default: begin // ILLEGAL encoding
            c_illegal[k] = 1'b1;
            c_cid = cid; c_rank = k[RW-1:0];
          end
        endcase
      end
    end
  end

  // ==========================================================================
  // Layer 2: SEQUENTIAL sample on ck_t rising edge
  // ==========================================================================
  wire any_decode = |c_act | |c_rd | |c_wr | |c_pre |
                    |c_ref | |c_mrs | |c_zqcl | |c_nop;

  always @(posedge ck_t) begin
    if (!rst_n) begin
      q_act_pulse <= {NRANKS{1'b0}}; q_rd_pulse <= {NRANKS{1'b0}};
      q_wr_pulse <= {NRANKS{1'b0}}; q_pre_pulse <= {NRANKS{1'b0}};
      q_ref_pulse <= {NRANKS{1'b0}}; q_mrs_pulse <= {NRANKS{1'b0}};
      q_zqcl_pulse <= {NRANKS{1'b0}}; q_nop_pulse <= {NRANKS{1'b0}};
      q_illegal_pulse <= {NRANKS{1'b0}};
      cnt_act <= 16'd0; cnt_rd <= 16'd0; cnt_wr <= 16'd0; cnt_pre <= 16'd0;
      cnt_ref <= 16'd0; cnt_mrs <= 16'd0; cnt_zqcl <= 16'd0; cnt_nop <= 16'd0;
      cnt_illegal <= 16'd0; cnt_pd <= 16'd0; cnt_deselect <= 16'd0;
      illegal_flag <= 1'b0;
      last_row <= {ROW_W{1'b0}}; last_col <= {COL_W{1'b0}};
      last_bank <= {(BG_W+BA_W){1'b0}}; last_op <= 4'd0;
      last_autopre <= 1'b0; last_allbanks <= 1'b0;
      last_cid <= {CID_W{1'b0}}; last_rank <= {RW{1'b0}};
    end else begin
      q_act_pulse <= c_act; q_rd_pulse <= c_rd; q_wr_pulse <= c_wr;
      q_pre_pulse <= c_pre; q_ref_pulse <= c_ref; q_mrs_pulse <= c_mrs;
      q_zqcl_pulse <= c_zqcl; q_nop_pulse <= c_nop;
      q_illegal_pulse <= c_illegal;
      cnt_act   <= sat16(cnt_act,   |c_act);
      cnt_rd    <= sat16(cnt_rd,    |c_rd);
      cnt_wr    <= sat16(cnt_wr,    |c_wr);
      cnt_pre   <= sat16(cnt_pre,   |c_pre);
      cnt_ref   <= sat16(cnt_ref,   |c_ref);
      cnt_mrs   <= sat16(cnt_mrs,   |c_mrs);
      cnt_zqcl  <= sat16(cnt_zqcl,  |c_zqcl);
      cnt_nop   <= sat16(cnt_nop,   |c_nop);
      cnt_illegal <= sat16(cnt_illegal, |c_illegal);
      cnt_pd      <= sat16(cnt_pd,      n_pd(cs_n, cke));
      cnt_deselect <= sat16(cnt_deselect, n_desel(cs_n));
      if (|c_illegal) illegal_flag <= 1'b1;
      if (any_decode) begin
        last_row <= c_row; last_col <= c_col; last_bank <= c_bank;
        last_op <= c_op; last_autopre <= c_autopre;
        last_allbanks <= c_allbanks; last_cid <= c_cid; last_rank <= c_rank;
      end
    end
  end

  // ---- helpers -------------------------------------------------------------
  function [15:0] sat16;
    input [15:0] cur;
    input        inc;
    begin
      sat16 = (inc && cur != 16'hFFFF) ? (cur + 16'd1) : cur;
    end
  endfunction

  function [15:0] n_pd;
    input [NRANKS-1:0] cs;
    input [NRANKS-1:0] ck;
    integer i;
    begin
      n_pd = 16'd0;
      for (i = 0; i < NRANKS; i = i + 1)
        if (!cs[i] && !ck[i]) n_pd = n_pd + 16'd1;
    end
  endfunction

  function [15:0] n_desel;
    input [NRANKS-1:0] cs;
    integer i;
    begin
      n_desel = 16'd0;
      for (i = 0; i < NRANKS; i = i + 1)
        if (cs[i]) n_desel = n_desel + 16'd1;
    end
  endfunction

endmodule
