// ============================================================================
// USPhy_IOBUF.v — Bidirectional I/O buffer (matches SpinalHDL blackbox)
// ============================================================================
module USPhy_IOBUF(I, T, O, IO);
  input  wire I;
  input  wire T;
  output wire O;
  inout  wire IO;

  assign IO = T ? 1'bz : I;
  assign O  = IO;
endmodule

// ============================================================================
// USPhy_TFF.v — T flip-flop (toggle on CLK rising edge)
// ============================================================================
module USPhy_TFF(CLK, T, Q);
  input  wire CLK;
  input  wire T;
  output wire Q;

  reg Q_reg = 0;
  always @(posedge CLK)
    if (T) Q_reg <= ~Q_reg;
  assign Q = Q_reg;
endmodule

