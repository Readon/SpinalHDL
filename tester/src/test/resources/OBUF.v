// OBUF Simulation Stub for XilinxUSPhy
// This is a simplified behavioral model for simulation purposes only
// It does not represent the actual timing and behavior of Xilinx UltraScale primitive

`timescale 1ps/1ps

module OBUF #(
    parameter CAPACITANCE = "DONT_CARE",
    parameter IOSTANDARD = "DEFAULT",
    parameter SLEW = "SLOW"
) (
    input  wire                  I,
    output wire                  O
);

    // Simple output buffer
    // In real hardware, this would provide proper output buffering
    // For simulation, we just pass through the signal
    
    assign O = I;

endmodule