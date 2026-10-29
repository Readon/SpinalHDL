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