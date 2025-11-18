// Simple ISERDESE3 stub for Verilator (legacy Verilog syntax only)
// This is only for simulation / structural purposes in tests.

`timescale 1ps/1ps

module ISERDESE3 #(
    parameter DATA_WIDTH = 8,
    parameter DYN_CLKDIV_INV_EN = "FALSE",
    parameter FIFO_ENABLE = "TRUE",
    parameter FIFO_SYNC_MODE = "FALSE",
    parameter IS_CLK_INVERTED = 1'b0,
    // Match UltraScale ISERDESE3 generics used in generated RTL
    parameter IS_CLK_B_INVERTED = 1'b0,
    parameter IS_CLKB_INVERTED = 1'b1,
    parameter IS_CLKDIV_INVERTED = 1'b0,
    parameter IS_CLKDIVP_INVERTED = 1'b0,
    parameter IS_D_INVERTED = 1'b0,
    parameter IS_RST_INVERTED = 1'b0,
    parameter SIM_DEVICE = "ULTRASCALE"
) (
    input  wire                  CLK,
    input  wire                  CLKB,
    input  wire                  CLK_B,
    input  wire                  CLKDIV,
    input  wire                  CLKDIVP,
    input  wire                  D,
    output wire [7:0]            Q,
    output wire                  INTERNAL_DIVCLK,
    input  wire                  RST,
    input  wire                  FIFO_RD_CLK,
    input  wire                  FIFO_RD_EN,
    output wire                  FIFO_EMPTY,
    output wire                  FIFO_FULL
);


    // Simple internal div clock modeling: reuse CLKDIV
    assign INTERNAL_DIVCLK = CLKDIV;

    // Very small behavioral model: shift register on CLK, simple FIFO status
    reg [7:0] q_reg;
    reg       fifo_empty_reg;
    reg       fifo_full_reg;

    assign Q          = q_reg;
    assign FIFO_EMPTY = fifo_empty_reg;
    assign FIFO_FULL  = fifo_full_reg;

    initial begin
        q_reg          = 8'b0;
        fifo_empty_reg = 1'b1;
        fifo_full_reg  = 1'b0;
    end

    always @(posedge CLK or posedge RST) begin
        if (RST) begin
            q_reg          <= 8'b0;
            fifo_empty_reg <= 1'b1;
            fifo_full_reg  <= 1'b0;
        end else begin
            // Simple serial-to-parallel shift, ignore all advanced features
            q_reg          <= {q_reg[6:0], D};
            fifo_empty_reg <= 1'b0;
            fifo_full_reg  <= 1'b0;
        end
    end

endmodule

