// ============================================================================
// tb_tc9_sdram_model.v — TC9: Ddr4SdramModel self-test (no PHY)
//
// Scenarios:
//   A: WRITE absorption — ACT + WR command pins, TB drives 16 DDR beats on
//      dq; verify wr_key_last and the stored 256-bit word (wr_word_last).
//   B: READ response — RD on the same location; verify all 16 beats on the
//      dq pin (model-driven). Also: RD of an UNWRITTEN location must raise
//      rd_pending_err.
//   C: DQS echo — dqs_in -> dqs_echo plumb-through (ECHO_DELAY_EDGES=0).
// ============================================================================
`timescale 1ns/1ps
module tb_tc9_sdram_model;
  reg clk = 0, rst_n = 0;
  always #2.5 clk = ~clk;   // ck_t = 100 MHz

  // command pins
  reg [1:0]  cs_n = 2'b11;
  reg [1:0]  cke  = 2'b11;
  reg [12:0] ca   = 13'h0;
  reg        ras_n = 1, cas_n = 1, we_n = 1, act_n = 1;
  reg        bg = 0;
  reg [2:0]  ba = 0;
  reg        cid = 0;
  // data
  wire [15:0] dq_net;
  reg [15:0]  dq_drv = 16'h0;
  reg         tb_drive_en = 0;
  assign dq_net = tb_drive_en ? dq_drv : 16'bz;
  reg  [1:0]  dqs_in = 2'b00;
  wire [1:0]  dqs_echo;

  Ddr4SdramModel #(
    .RANKS(2), .BL_BEATS(16), .MEM_ENTRIES(65536), .KEY_W(16),
    .ECHO_DELAY_EDGES(0)
  ) dut (
    .ck_t(clk), .rst_n(rst_n),
    .cs_n(cs_n), .cke(cke), .ca(ca),
    .ras_n(ras_n), .cas_n(cas_n), .we_n(we_n), .act_n(act_n),
    .bg(bg), .ba(ba), .cid(cid),
    .dq(dq_net),
    .dqs_in(dqs_in), .dqs_echo(dqs_echo),
    .wr_key_last(), .rd_key_last(), .wr_word_last(),
    .rd_driving(), .rd_pending_err()
  );

  integer errors = 0;
  task check(input cond, input string msg);
    begin
      // X is a FAILURE (catches masked/tristate buses), not a pass.
      if (cond !== 1'b1) begin
        errors = errors + 1;
        $display("FAIL: %s", msg);
      end
    end
  endtask

  // NOP bus state
  task nop_bus;
    begin
      act_n = 1; ras_n = 1; cas_n = 1; we_n = 1; bg = 0; ba = 3'b000;
    end
  endtask

  // ACT: decode on the posedge entered here.
  task drive_act(input [15:0] row, input bg_, input [2:0] ba_, input [1:0] cs);
    begin
      act_n = 1'b0; ras_n = row[15]; cas_n = row[14]; we_n = row[13];
      ca = row[12:0]; bg = bg_; ba = ba_; cs_n = cs;
      @(posedge clk);
      nop_bus; cs_n = 2'b11;
    end
  endtask

  // WR: BL16 x16 = 16 beats = 16 ck_t edges (1 beat/edge, 16 bits each).
  //   beat n value = 16'hA000+n. The model absorbs starting on the edge
  //   AFTER the decode posedge (q_wr_pulse becomes visible there), so beat0
  //   is driven AFTER the decode edge and each beat n before edge e_{n+1}
  //   (e_k = negedge for k odd, posedge for k even).
  task drive_wr(input [9:0] col, input bg_, input [2:0] ba_, input [1:0] cs);
    integer n;
    begin
      act_n = 1'b1; ras_n = 1'b1; cas_n = 1'b1; we_n = 1'b0;
      ca = {2'b00, col[9:0], 2'b00}; bg = bg_; ba = ba_; cs_n = cs;
      @(posedge clk);                          // WR decode edge
      nop_bus; cs_n = 2'b11;
      for (n = 0; n < 16; n = n + 1) begin
        tb_drive_en = 1'b1;
        dq_drv = 16'hA000 + n;                 // beat n
        if (n % 2 == 0) @(negedge clk); else @(posedge clk);
      end
      tb_drive_en = 1'b0;
    end
  endtask

  // Expected 256-bit word: W[n*16+15 : n*16] = beat n = 16'hA000+n
  function [255:0] expected_word;
    integer n;
    begin
      expected_word = 256'h0;
      for (n = 0; n < 16; n = n + 1)
        expected_word = expected_word | ((16'hA000 + n) << (n*16));
    end
  endfunction

  // RD: read machine triggers on the FIRST EDGE AFTER the RD decode posedge
  //   (P0) — i.e. the following negedge N0 — because q_rd_pulse is a
  //   posedge-latched pulse first visible at N0. beat0 is driven from N0;
  //   beat k is driven on the window (Nk, Nk+1) where Nk = neg for k even,
  //   pos for k odd. Sample #1 after each Nk (settled value).
  task drive_rd_and_check(input [9:0] col, input bg_, input [2:0] ba_, input [1:0] cs, input [255:0] exp_word);
    integer k;
    begin
      act_n = 1'b1; ras_n = 1'b1; cas_n = 1'b0; we_n = 1'b1;
      ca = {2'b00, col[9:0], 2'b00}; bg = bg_; ba = ba_; cs_n = cs;
      @(posedge clk);                          // P0: RD decode posedge
      nop_bus; cs_n = 2'b11;
      @(negedge clk);                           // N0: read machine triggers; beat0 driven from here
      #1;
      check(dq_net == 16'hA000, "B: beat0 mismatch");
      for (k = 1; k < 16; k = k + 1) begin
        if (k % 2 == 1) @(posedge clk); else @(negedge clk);
        #1;
        check(dq_net == (16'hA000 + k), "B: read beat mismatch");
      end
    end
  endtask

  initial begin
    // ---- reset -----------------------------------------------------------
    rst_n = 1'b0;
    repeat(3) @(posedge clk);
    rst_n = 1'b1;
    repeat(2) @(posedge clk);

    // ---- Scenario A: WRITE ------------------------------------------------
    drive_act(16'h4001, 1'b1, 3'b010, 2'b10);   // row=0x4001 bank={1,010} rank0
    drive_wr (10'h100, 1'b1, 3'b010, 2'b10);
    repeat(2) @(posedge clk);
    // key = {row[5:0]=6'h01, col[4:0]=5'h00, bank=4'b0010, rank=1'b0} = 12'h10A
    
    check(dut.wr_key_last == {6'h01, 5'h00, 4'b1010, 1'b0}, "A: wr_key_last wrong");
    
    check(dut.wr_word_last == expected_word(), "A: wr_word_last wrong");
    check(dut.mem[{6'h01, 5'h00, 4'b1010, 1'b0}] == expected_word(), "A: mem content wrong");

    // ---- Scenario B: READ back same location -------------------------------
    drive_act(16'h4001, 1'b1, 3'b010, 2'b10);
    drive_rd_and_check(10'h100, 1'b1, 3'b010, 2'b10, expected_word());
    repeat(2) @(posedge clk);
    check(!dut.rd_pending_err, "B: spurious rd_pending_err");

    // B2: read UNWRITTEN location (different col32blk = col[4:0]).
    // NB: model keys memory by col[4:0] (BL16 burst granularity), so the
    // "unwritten" column must differ in its LOW 5 bits from the write's
    // col[4:0]=0. 10'h001 -> col[4:0]=1 (distinct location).
    drive_act(16'h4001, 1'b1, 3'b010, 2'b10);
    begin : b2
      act_n = 1'b1; ras_n = 1'b1; cas_n = 1'b0; we_n = 1'b1;
      ca = {2'b00, 10'h001, 2'b00}; bg = 1'b1; ba = 3'b010; cs_n = 2'b10;
      @(posedge clk);
      nop_bus; cs_n = 2'b11;
      repeat(3) @(posedge clk);
      check(dut.rd_pending_err, "B2: rd_pending_err not raised for unwritten loc");
    end

    // ---- Scenario C: DQS echo ----------------------------------------------
    dqs_in = 2'b01; #1;
    check(dqs_echo == 2'b01, "C: dqs_echo[0]");
    dqs_in = 2'b10; #1;
    check(dqs_echo == 2'b10, "C: dqs_echo[1]");
    dqs_in = 2'b00;

    // ---- verdict -----------------------------------------------------------
    if (errors == 0)
      $display("TC9_PASS: SDRAM model self-test (write absorb / read respond / dqs echo)");
    else
      $display("TC9_FAIL: %0d errors", errors);
    $finish;
  end

  // safety timeout
  initial begin
    #200000;
    $display("TC9_FAIL: timeout");
    $finish;
  end
endmodule
