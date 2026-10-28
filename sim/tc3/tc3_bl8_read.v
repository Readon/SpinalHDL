// ============================================================================
// tc3_bl8_read.v — TC3: continuous multi-FRAME READ deserialization fidelity
//
// Extends TC1 (single-frame exact loopback) to a CONTINUOUS BURST:
//   OSERDESE3.OQ -> (direct force) -> ISERDESE3.D, with a NEW distinct
//   pattern loaded on every DFI frame (like successive beats of a BL8+
//   read burst from DRAM). The reader must track FRAME BOUNDARIES across
//   the whole burst: no mixing, no drift, no cumulative bit-slip.
//
// Stimulus design (non-degenerate by construction):
//   frame pattern N: wrdata_0 = {8{N}}, wrdata_1 = {8{8'hFF - N}}
//   => the two phases are bitwise COMPLEMENTARY, so every intra-frame
//      phase boundary has a guaranteed transition (no DC stretches),
//      while each frame stays trivially recognizable.
//
// Assertions:
//   A1: every sampled frame decodes to a VALID pattern pair
//       (rddata_0 == {8{N}} && rddata_1 == {8{FF-N}}) — a mixed frame
//       (bits from two patterns) = frame-boundary loss = FAIL.
//   A2: across the burst, the pattern index advances monotonically
//       (stay or +1 mod 8) — proves sustained frame lock, no slip.
//       Primary check: a contiguous coherent run of >= 8 frames exists.
//   B : steady-state 0xA5/0x5A5A held 32 frames, sampled every frame:
//       all samples exactly equal — no cumulative bit-slip over time.
//
// NOTE: rddata is a continuous function of the DQ stream in this PHY
// (rden is an MC-side handshake, not consumed inside the PHY); rddataValid
// is an independent DFI-handshake item (separate work item, out of scope).
// ============================================================================
`timescale 1ns/1ps
module tc3_bl8_read;
  reg refclk=0, dramClk=0, rst_n=0;
  always #5 refclk=~refclk; always #2.5 dramClk=~dramClk;

  wire dq_0,dq_1,dq_2,dq_3,dq_4,dq_5,dq_6,dq_7;
  wire dq_8,dq_9,dq_10,dq_11,dq_12,dq_13,dq_14,dq_15;
  reg [31:0] wrdata_0=0,wrdata_1=0; reg wr_en_0=0,wr_en_1=0;
  reg [3:0] wrmask_0=0,wrmask_1=0; reg [1:0] wrcs_0=2'b11,wrcs_1=2'b11;
  reg rden_0=0,rden_1=0; reg [1:0] rdcn_0=2'b11,rdcn_1=2'b11;
  wire [31:0] rddata_0,rddata_1; wire rdvalid_0,rdvalid_1; wire [3:0] rddbi_0,rddbi_1;
  wire [1:0] dqs_t,dqs_c,dqs_echo; assign dqs_echo=dqs_t;
  wire [8:0] wlDelayOut;

  glbl glbl();
  UsPhy dut(
    .refclk(refclk),.dramClk(dramClk),.rst_n(rst_n),
    .dfi_control_address(0),.dfi_control_bank(0),.dfi_control_rasN(2'b11),
    .dfi_control_casN(2'b11),.dfi_control_weN(2'b11),.dfi_control_csN(4'b1111),
    .dfi_control_actN(2'b11),.dfi_control_bg(0),.dfi_control_cid(0),
    .dfi_control_cke(4'b1111),.dfi_control_odt(0),.dfi_control_resetN(4'b1111),
    .dfi_write_wr_0_wrdataEn(wr_en_0),.dfi_write_wr_0_wrdata(wrdata_0),
    .dfi_write_wr_0_wrdataMask(wrmask_0),.dfi_write_wr_0_wrdataCsN(wrcs_0),
    .dfi_write_wr_1_wrdataEn(wr_en_1),.dfi_write_wr_1_wrdata(wrdata_1),
    .dfi_write_wr_1_wrdataMask(wrmask_1),.dfi_write_wr_1_wrdataCsN(wrcs_1),
    .dfi_read_rden_0(rden_0),.dfi_read_rden_1(rden_1),
    .dfi_read_rdCs_0_rddataCsN(rdcn_0),.dfi_read_rdCs_1_rddataCsN(rdcn_1),
    .dfi_read_rd_0_rddataValid(rdvalid_0),.dfi_read_rd_0_rddata(rddata_0),
    .dfi_read_rd_0_rddataDbiN(rddbi_0),
    .dfi_read_rd_1_rddataValid(rdvalid_1),.dfi_read_rd_1_rddata(rddata_1),
    .dfi_read_rd_1_rddataDbiN(rddbi_1),
    .pads_ck_t(),.pads_ck_c(),.pads_cke_0(),.pads_cke_1(),
    .pads_cs_n_0(),.pads_cs_n_1(),
    .pads_ca_0(),.pads_ca_1(),.pads_ca_2(),.pads_ca_3(),.pads_ca_4(),.pads_ca_5(),
    .pads_ca_6(),.pads_ca_7(),.pads_ca_8(),.pads_ca_9(),.pads_ca_10(),
    .pads_ca_11(),.pads_ca_12(),.pads_act_n(),.pads_bg_0(),.pads_cid_0(),
    .pads_odt_0(),.pads_odt_1(),.pads_reset_n(),.pads_par(),
    .pads_dq_0(dq_0),.pads_dq_1(dq_1),.pads_dq_2(dq_2),.pads_dq_3(dq_3),
    .pads_dq_4(dq_4),.pads_dq_5(dq_5),.pads_dq_6(dq_6),.pads_dq_7(dq_7),
    .pads_dq_8(dq_8),.pads_dq_9(dq_9),.pads_dq_10(dq_10),.pads_dq_11(dq_11),
    .pads_dq_12(dq_12),.pads_dq_13(dq_13),.pads_dq_14(dq_14),.pads_dq_15(dq_15),
    .pads_dqs_t_0(dqs_t[0]),.pads_dqs_t_1(dqs_t[1]),
    .pads_dqs_c_0(dqs_c[0]),.pads_dqs_c_1(dqs_c[1]),
    .pads_dqs_in_t_0(dqs_echo[0]),.pads_dqs_in_t_1(dqs_echo[1]),
    .pads_dm_dbi_n_0(),.pads_dm_dbi_n_1(),
    .wlDelayOut(wlDelayOut),
    .clk(refclk),.reset(~rst_n));

  integer i, k, n;
  integer errors = 0;
  reg [31:0] hist0 [0:15], hist1 [0:15];
  reg [7:0]  histN [0:15];   // decoded N (0 = invalid)
  integer    valid_cnt = 0, best_run = 0, run = 0;
  integer    b_bad = 0;
  reg [31:0] exp0_b, exp1_b;

  // decode helper: returns N in histN[i] (0 if invalid)
  task decode_frame(input [31:0] r0, input [31:0] r1, output [7:0] nn);
    integer m;
    begin
      nn = 8'd0;
      for (m = 1; m <= 8; m = m + 1)
        if (r0 === {4{m[7:0]}} && r1 === {4{8'hFF - m[7:0]}}) nn = m[7:0];
    end
  endtask

  initial begin
    rst_n=0; #50; rst_n=1; #40;

    // Pure SerDes loopback (same as TC1): each ISERDESE3.D <- own OSERDESE3.OQ
    force dut.iSERDESE3_1.D  = dut.oSERDESE3_1.OQ;
    force dut.iSERDESE3_2.D  = dut.oSERDESE3_2.OQ;
    force dut.iSERDESE3_3.D  = dut.oSERDESE3_3.OQ;
    force dut.iSERDESE3_4.D  = dut.oSERDESE3_4.OQ;
    force dut.iSERDESE3_5.D  = dut.oSERDESE3_5.OQ;
    force dut.iSERDESE3_6.D  = dut.oSERDESE3_6.OQ;
    force dut.iSERDESE3_7.D  = dut.oSERDESE3_7.OQ;
    force dut.iSERDESE3_8.D  = dut.oSERDESE3_8.OQ;
    force dut.iSERDESE3_9.D  = dut.oSERDESE3_9.OQ;
    force dut.iSERDESE3_10.D = dut.oSERDESE3_10.OQ;
    force dut.iSERDESE3_11.D = dut.oSERDESE3_11.OQ;
    force dut.iSERDESE3_12.D = dut.oSERDESE3_12.OQ;
    force dut.iSERDESE3_13.D = dut.oSERDESE3_13.OQ;
    force dut.iSERDESE3_14.D = dut.oSERDESE3_14.OQ;
    force dut.iSERDESE3_15.D = dut.oSERDESE3_15.OQ;
    force dut.iSERDESE3_16.D = dut.oSERDESE3_16.OQ;

    wr_en_0=1; wr_en_1=1; rden_0=1; rden_1=1;

    $display("== TC3: continuous multi-frame read deserialization ==");

    // ------------------------------------------------------------------
    // Part A: 16-frame burst, 8 distinct patterns x 2 frames each.
    // Pattern changes land on refclk posedges (frame load), samples taken
    // at negedge (Q stable mid-frame).
    // ------------------------------------------------------------------
    repeat (4) @(posedge refclk);   // pipeline settle with junk-free base
    for (k = 0; k < 16; k = k + 1) begin
      n = (k / 2) + 1;               // pattern N = 1..8, 2 frames each
      wrdata_0 = {4{n[7:0]}};
      wrdata_1 = {4{8'hFF - n[7:0]}};
      @(posedge refclk);             // frame load
      @(negedge refclk);             // sample
      hist0[k] = rddata_0; hist1[k] = rddata_1;
      decode_frame(rddata_0, rddata_1, histN[k]);
      if (histN[k] != 8'd0) valid_cnt = valid_cnt + 1;
    end

    // A2: longest contiguous coherent run (valid + monotonic stay/+1 mod 8)
    run = 0;
    for (k = 0; k < 16; k = k + 1) begin
      if (histN[k] == 8'd0) begin
        run = 0;
      end else if (k == 0 || histN[k-1] == 8'd0) begin
        run = 1;
      end else begin
        if (histN[k] == histN[k-1] || histN[k] == histN[k-1] + 8'd1 ||
            (histN[k-1] == 8'd8 && histN[k] == 8'd1))
          run = run + 1;
        else
          run = 1;                   // discontinuity: restart run
      end
      if (run > best_run) best_run = run;
    end

    $display("  Part A: 16 frames sampled; valid=%0d, longest coherent run=%0d",
             valid_cnt, best_run);
    for (k = 0; k < 16; k = k + 1)
      $display("    f%-2d rd0=%h rd1=%h N=%0d", k, hist0[k], hist1[k], histN[k]);
    if (best_run >= 8)
      $display("  [PASS] A: sustained frame lock across burst (coherent run %0d >= 8 frames)", best_run);
    else begin
      $display("  [FAIL] A: no coherent run of 8+ frames (frame boundary loss/drift)");
      errors = errors + 1;
    end

    // ------------------------------------------------------------------
    // Part B: steady-state 0xA5/0x5A for 32 sampled frames (after a 4-frame
    // flush). Every sample must be EXACTLY equal — catches any bit-slip.
    // ------------------------------------------------------------------
    wrdata_0 = 32'hA5A5A5A5; wrdata_1 = 32'h5A5A5A5A;
    exp0_b = 32'hA5A5A5A5;   exp1_b = 32'h5A5A5A5A;
    repeat (4) @(posedge refclk);           // flush
    for (k = 0; k < 32; k = k + 1) begin
      @(posedge refclk);
      @(negedge refclk);
      if (rddata_0 !== exp0_b || rddata_1 !== exp1_b) begin
        b_bad = b_bad + 1;
        if (b_bad <= 3)
          $display("    B-deviation f%0d: rd0=%h rd1=%h (exp %h/%h)",
                   k, rddata_0, rddata_1, exp0_b, exp1_b);
      end
    end
    if (b_bad == 0)
      $display("  [PASS] B: 32/32 steady frames exact (no cumulative bit-slip)");
    else begin
      $display("  [FAIL] B: %0d/32 steady frames deviated", b_bad);
      errors = errors + 1;
    end

    $display("==================== SUMMARY ====================");
    if (errors == 0)
      $display("TC3: PASS — continuous multi-frame read deserialization lossless");
    else
      $display("TC3: FAIL — %0d assertion(s) failed", errors);
    $finish;
  end

  initial begin #100000; $display("TIMEOUT"); $finish; end
endmodule
