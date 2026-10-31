// ============================================================================
// custom_unisims_sim.v — Behavioral ODELAYE3 / IDELAYE3 for xsim.
//
// PURPOSE: The Xilinx golden ODELAYE3 model does NOT produce a measurable
// output phase shift from CNTVALUEIN in xsim (proven by 9 probe methods).
// These replacements provide a DETERMINISTIC, LINEAR delay proportional to
// CNTVALUEIN so the write-leveling closed loop can be demonstrated.
//
// ODELAYE3: DATAOUT = ODATAIN #(CNTVALUEIN * STEP_PS). Passthrough otherwise.
// IDELAYE3: DATAOUT = DATAIN  #(CNTVALUEIN * STEP_PS). Passthrough otherwise.
//
// STEP_PS = 50 ps per tap. Max CNTVALUEIN = 511 -> max delay 25.55 ns.
// In production silicon the per-tap delay is ~1-5 ps (process-dependent);
// 50 ps is a SIMULATION CHOICE to make the bang-bang loop observable at the
// 10 ns DQS period. The loop LOGIC (bang-bang direction, convergence) is
// independent of the absolute step size.
// ============================================================================
`timescale 1ps/1ps

module ODELAYE3 #(
  parameter DELAY_TYPE  = "VAR_LOAD",
  parameter DELAY_VALUE = 0
)(
  input  wire        CLK,
  input  wire        ODATAIN,
  output wire        DATAOUT,
  input  wire        CE,
  input  wire        RST,
  input  wire        EN_VTC,
  input  wire        INC,
  input  wire        LOAD,
  input  wire [8:0]  CNTVALUEIN,
  output wire [8:0]  CNTVALUEOUT,
  input  wire        CASC_IN,
  input  wire        CASC_RETURN,
  output wire        CASC_OUT
);
  localparam integer STEP_PS = 50;
  reg [31:0] d_ps;
  always @* d_ps = CNTVALUEIN * STEP_PS;
  reg q;
  // File timescale is 1ps/1ps, so a bare #(d_ps) is already in picoseconds.
  always @(ODATAIN) q <= #(d_ps) ODATAIN;
  assign DATAOUT     = q;
  assign CNTVALUEOUT = CNTVALUEIN;
  assign CASC_OUT    = CASC_IN;
endmodule

module IDELAYE3 #(
  parameter DELAY_TYPE  = "VARIABLE",
  parameter DELAY_VALUE = 0
)(
  input  wire        CLK,
  input  wire        DATAIN,
  input  wire        IDATAIN,
  output wire        DATAOUT,
  input  wire        CE,
  input  wire        RST,
  input  wire        EN_VTC,
  input  wire        INC,
  input  wire        LOAD,
  input  wire [8:0]  CNTVALUEIN,
  output wire [8:0]  CNTVALUEOUT,
  input  wire        CASC_IN,
  input  wire        CASC_RETURN,
  output wire        CASC_OUT
);
  localparam integer STEP_PS = 50;
  reg [31:0] d_ps;
  always @* d_ps = CNTVALUEIN * STEP_PS;
  reg q;
  always @(DATAIN) q <= #(d_ps) DATAIN;
  assign DATAOUT     = q;
  assign CNTVALUEOUT = CNTVALUEIN;
  assign CASC_OUT    = CASC_IN;
endmodule
// ============================================================================
// OSERDESE3 — silicon-semantics behavioral model (UltraScale+, WIRE=0,
// DATA_WIDTH=4, fr=2). Replaces the Xilinx golden+SIP model in this bench.
//
// Semantics (PG050 usage table):
//   * On each CLKDIV posedge (RST=0): the 4-bit D word is captured, D[3]
//     first (MSB-first serialization).
//   * Each of the 4 bits is placed on OQ at successive CLK edges (both
//     polarities: 4 CLK edges per CLKDIV period at fr=2).
//   * A 16-deep bit FIFO sits between capture and OQ so that a coincident
//     CLKDIV-posedge / CLK-posedge never loses a bit.
// Structure: two always blocks (push on CLKDIV posedge, pop on each CLK
// edge) with DISJOINT driver registers, so no in-block edge detection is
// needed. At a coincident edge the push/pop order is scheduler-defined but
// stable per build; worst case the first bit of the very first post-reset
// frame slips one CLK edge (transient, that frame is wren-gated to zero).
// KNOWN SIMPLIFICATION: the silicon output pipeline latency (~2 CLK edges)
// is OMITTED — bits can appear on OQ at the load edge itself. The
// Ddr4SdramModel write collector does not enforce CWL, so a constant
// burst-placement offset relative to the write command is immaterial; only
// the DQ-vs-DQS relative alignment matters and is preserved.
// ============================================================================
module OSERDESE3 #(
  parameter DATA_WIDTH = 4
)(
  input  wire        CLK,
  input  wire        CLKDIV,
  input  wire [3:0]  D,
  input  wire        RST,
  output wire        OQ
);
  localparam DEPTH = 16;
  reg [DEPTH-1:0] mem;
  reg [4:0] wptr, rptr;
  reg oq_q;
  integer a;

  // PUSH: capture D on CLKDIV posedge (MSB -> lowest FIFO slot first).
  // The #1 offset lands the write 1ps AFTER the edge, so the same-timestep
  // POP (active region) always observes the pre-push FIFO state: pop-before-
  // push semantics pinned, no elaboration-dependent ordering.
  always @(posedge CLKDIV or posedge RST) begin
    if (RST) begin
      wptr = 5'd0;
    end else begin
      #1 begin
        a = wptr[3:0];
        mem[a]         = D[0];
        mem[(a+1)&15]  = D[1];
        mem[(a+2)&15]  = D[2];
        mem[(a+3)&15]  = D[3];
        wptr = wptr + 5'd4;
      end
    end
  end

  // POP: one bit per CLK edge (both polarities). oq_q is assigned #1 after the
  // edge so same-edge observers (the SDRAM model's write absorber) always see
  // the pre-pop value — deterministic one-edge-later visibility on the pad.
  always @(posedge CLK or negedge CLK or posedge RST) begin
    if (RST) begin
      rptr = 5'd0;
      oq_q = 1'b0;
    end else if (wptr != rptr) begin
      #1 oq_q = mem[rptr[3:0]];
      rptr = rptr + 5'd1;
    end
  end

  assign OQ = oq_q;
endmodule

// ============================================================================
// ISERDESE3 — silicon-semantics behavioral model (UltraScale+, WIRE=0,
// DATA_WIDTH=4, fr=2). Replaces the Xilinx golden+SIP model in this bench.
//
// Semantics (PG050 usage table):
//   * D is sampled on BOTH edges of CLK (4 samples per CLKDIV period).
//   * On each CLKDIV posedge (RST=0), Q[3:0] presents the four most recent
//     samples: Q[3] = OLDEST, Q[0] = NEWEST.
//   * At a coincident CLKDIV/CLK posedge the present/sample order is
//     scheduler-defined but stable per build (window may or may not include
//     the same-edge sample); the read-side tap mapping in UsPhy.scala is
//     calibrated for the realized behavior.
// NOTE: the golden+SIP model observed Q[0]=EARLIEST in this bench — the
// opposite index polarity. The read-side tap assignment in UsPhy.scala is
// re-derived for this documented polarity.
// ============================================================================
module ISERDESE3 #(
  parameter DATA_WIDTH = 4
)(
  input  wire        CLK,
  input  wire        CLK_B,
  input  wire        CLKDIV,
  input  wire        D,
  input  wire        RST,
  output wire [3:0]  Q
);
  reg [3:0] win;   // win[0] = newest sample
  reg [3:0] win_sh; // race-free snapshot of win (see PRESENT note)
  reg [3:0] q_q;

  // SAMPLE: shift D into the 4-sample window on every CLK edge.
  always @(posedge CLK or negedge CLK or posedge RST) begin
    if (RST) win = 4'b0;
    else     win = {win[2:0], D};
  end

  // SHADOW: copy win 1ps after each CLK posedge (the CLK edge immediately
  // before the next CLKDIV posedge). The #1 offset means the copy lands in a
  // timestep where no clock edge occurs, so PRESENT (CLKDIV posedge, which
  // coincides with a CLK negedge) always reads a stable snapshot: the 4
  // samples STRICTLY before the PRESENT edge. Without this indirection the
  // SAMPLE and PRESENT blocks share a timestep and their relative order is
  // elaboration-dependent, shifting Q by one sample per build.
  initial win_sh = 4'b0;
  always @(posedge CLK) begin
    #1 win_sh = win;
  end

  // PRESENT: freeze the window on CLKDIV posedge. q@T = the 4 samples STRICTLY
  // BEFORE T (identical to silicon ISERDESE3 Q semantics).
  always @(posedge CLKDIV or posedge RST) begin
    if (RST) q_q = 4'b0;
    else     q_q = {win_sh[0], win_sh[1], win_sh[2], win_sh[3]};  // Q[0]=newest .. Q[3]=oldest
  end

  assign Q = q_q;
endmodule
