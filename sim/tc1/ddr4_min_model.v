// ============================================================================
// ddr4_min_model.v — TB-controlled DDR4 x16 tape-recorder (minimal)
// ============================================================================
// Driven by the testbench (record_en / play_en), not by decoded DRAM commands.
//   record_en: capture dq[15:0] on every CK edge (DDR) into rec_buf
//   play_en:   replay rec_buf on every CK edge, driving dq
// DQS echo: passthrough (dqs_in -> dqs_echo) for the WL feedback loop.
//
// Inout discipline: the model drives dq only while playing; the PHY drives dq
// only while writing. The TB sequences these exclusively (never simultaneous).
// ============================================================================
`timescale 1ns/1ps

module ddr4_min_model #(
  parameter integer NREC = 16
)(
  input  wire        ck_t,
  input  wire        record_en,
  input  wire        play_en,
  inout  wire [15:0] dq,
  input  wire [1:0]  dqs_in,
  output wire [1:0]  dqs_echo
);

  localparam integer N = NREC;
  reg [15:0] rec_buf [0:N-1];
  integer i;
  initial for (i = 0; i < N; i = i + 1) rec_buf[i] = 16'h0;

  reg      recording = 0;
  reg      playing   = 0;
  integer  idx       = 0;
  reg [15:0] play_data = 16'h0;

  always @(posedge ck_t) begin
    if (record_en) begin recording <= 1; idx <= 0; end
    if (play_en)   begin playing   <= 1; idx <= 0; end
  end

  // Record dq on both CK edges (DDR) while recording.
  always @(posedge ck_t or negedge ck_t) begin
    if (recording) begin
      rec_buf[idx] <= dq;
      if (idx < N - 1) idx <= idx + 1;
      else recording <= 0;
    end
  end

  // Replay while playing.
  always @(posedge ck_t or negedge ck_t) begin
    if (playing) begin
      play_data <= rec_buf[idx];
      if (idx < N - 1) idx <= idx + 1;
      else playing <= 0;
    end
  end

  // Drive dq only during playback.
  assign dq = playing ? play_data : 16'bz;

  // DQS echo (passthrough for write-leveling feedback).
  assign dqs_echo[0] = dqs_in[0];
  assign dqs_echo[1] = dqs_in[1];

endmodule