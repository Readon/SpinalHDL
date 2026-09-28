`timescale 1ns/1ps
// glbl — Xilinx global reset. GSR deasserted (0) for steady-state sim.
// The unisims reference glbl.GSR hierarchically, so an instance named `glbl`
// must exist at the top level (instantiated in the TB).
module glbl(GSR, GWE, PRLD);
  output GSR;
  output GWE;
  output PRLD;
  reg GSR_r;
  initial begin
    GSR_r = 1'b1;   // assert reset at time 0
    #100 GSR_r = 1'b0;  // deassert after 100 units
  end
  assign GSR  = GSR_r;
  assign GWE  = 1'b0;
  assign PRLD = 1'b0;
endmodule