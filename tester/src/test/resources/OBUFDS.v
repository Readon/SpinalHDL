// OBUFDS Simulation Stub for XilinxUSPhy
// This is a simplified behavioral model for simulation purposes only
// It does not represent the actual timing and behavior of the Xilinx UltraScale primitive

`timescale 1ps/1ps

module OBUFDS #(
    parameter CAPACITANCE = "DONT_CARE",
    parameter IOSTANDARD = "DEFAULT",
    parameter SLEW = "SLOW"
) (
    input  wire                  I,
    output wire                  O,
    output wire                  OB
);

    // Simple differential output buffer
    // In real hardware, this would provide proper differential signaling
    // For simulation, we just pass through the signals
    
    assign O = I;
    assign OB = ~I; // Complementary output for differential signaling

endmodule