// ============================================================================
// tc7_strobe_pins.v — TC7: pin-level command strobe fidelity (L1 closure)
//
// Purpose: close the verification gap left by the L1 commit (fa9a21f83), which
// exposed pads.ras_n/cas_n/we_n but was verified only by elaboration + netlist
// port presence. This TB drives KNOWN DFI command level combinations into
// phase 0 and asserts the physical pins reproduce them EXACTLY, per the
// JESD79-4B Table 37 encodings that the MC (Task2IDFI) actually emits.
//
// Contract under test (ADR-0008 + ADR-0013):
//   - PHY is a pure per-phase pass-through of phase 0 (no encoding, no FSM);
//   - phase 0 = LOWEST (width/frequencyRatio) bits of each packed DFI control
//     vector (CAAlignment packs phase i into slice i, LSB-first);
//   - DDR4 command type = {ACT#,RAS#/A16,CAS#/A15,WE#/A14,BG,BA,A,CS#} level
//     combo, so a pin-level receiver decodes the command TYPE from the pins.
//
// Vector set (single rank; CS# selects the rank; A10 isolates PRE vs PREA and
// ZQCL vs NOP — RAS/CAS/WE/ACT alone are ambiguous for those pairs):
//   idx  command      ACT# RAS# CAS# WE# A10 CS#
//   ----------------------------------------------
//    0   NOP          1    1    1    1    0   1
//    1   ACT (RA=010) 0    0    1    0    0   0
//    2   READ         1    1    0    1    0   0
//    3   WRITE        1    1    1    0    0   0
//    4   PRE          1    0    1    0    0   0
//    5   PREA         1    0    1    0    1   0
//    6   REF          1    0    0    1    0   0
//    7   MRS          1    0    0    0    0   0
//    8   ZQCL         1    1    1    1    1   0
//    9   READ rank1   1    1    0    1    0   1(rank1)
//
// Checks per vector: ras_n/cas_n/we_n/act_n mirror the phase-0 DFI bits,
// cs_n_0/cs_n_1 mirror the per-rank CS phase-0 bits, ca[10] mirrors A10 and
// all other ca[] are 0 (address driven 0), cke_0 stays 1.
//
// Neg-proof expectation (run script note): inverting any one mapping in
// UsPhy.scala (e.g. ras_n := phase0(casN)) MUST trip at least one FAIL here.
// ============================================================================
`timescale 1ns/1ps
module tc7_strobe_pins;
  reg refclk = 0, dramClk = 0, rst_n = 0;
  always #5   refclk  = ~refclk;   // 100 MHz
  always #2.5 dramClk = ~dramClk;  // 200 MHz = 2x refclk (fr=2)

  // ---- DFI control drivers (phase 1 bits pinned to NOP=1) ------------------
  reg [1:0] dfi_actN = 2'b11, dfi_rasN = 2'b11, dfi_casN = 2'b11, dfi_weN = 2'b11;
  reg [1:0] dfi_csN  = 2'b11;
  reg [25:0] dfi_addr = 26'h0;  // phase0 = bits[12:0]

  // ---- DUT ------------------------------------------------------------------
  glbl glbl();
  UsPhy dut (
    .refclk(refclk), .dramClk(dramClk), .rst_n(rst_n),
    .dfi_control_address(dfi_addr), .dfi_control_bank(8'h0),
    .dfi_control_rasN(dfi_rasN), .dfi_control_casN(dfi_casN),
    .dfi_control_weN(dfi_weN),
    .dfi_control_csN({2'b11, dfi_csN}),
    .dfi_control_actN(dfi_actN),
    .dfi_control_bg(2'h0), .dfi_control_cid(2'h0),
    .dfi_control_cke(4'b1111), .dfi_control_odt(4'h0), .dfi_control_resetN(4'b1111),
    .dfi_write_wr_0_wrdataEn(1'b0), .dfi_write_wr_0_wrdata(32'h0),
    .dfi_write_wr_0_wrdataMask(4'h0), .dfi_write_wr_0_wrdataCsN(2'b11),
    .dfi_write_wr_1_wrdataEn(1'b0), .dfi_write_wr_1_wrdata(32'h0),
    .dfi_write_wr_1_wrdataMask(4'h0), .dfi_write_wr_1_wrdataCsN(2'b11),
    .dfi_read_rden_0(1'b0), .dfi_read_rden_1(1'b0),
    .dfi_read_rdCs_0_rddataCsN(2'b11), .dfi_read_rdCs_1_rddataCsN(2'b11),
    .pads_ck_t(), .pads_ck_c(),
    .pads_cke_0(), .pads_cke_1(),
    .pads_cs_n_0(), .pads_cs_n_1(),
    .pads_ca_0(), .pads_ca_1(), .pads_ca_2(), .pads_ca_3(), .pads_ca_4(),
    .pads_ca_5(), .pads_ca_6(), .pads_ca_7(), .pads_ca_8(), .pads_ca_9(),
    .pads_ca_10(), .pads_ca_11(), .pads_ca_12(),
    .pads_ras_n(), .pads_cas_n(), .pads_we_n(), .pads_act_n(),
    .pads_bg_0(), .pads_cid_0(),
    .pads_odt_0(), .pads_odt_1(), .pads_reset_n(), .pads_par(),
    .pads_dq_0(), .pads_dq_1(), .pads_dq_2(), .pads_dq_3(),
    .pads_dq_4(), .pads_dq_5(), .pads_dq_6(), .pads_dq_7(),
    .pads_dq_8(), .pads_dq_9(), .pads_dq_10(), .pads_dq_11(),
    .pads_dq_12(), .pads_dq_13(), .pads_dq_14(), .pads_dq_15(),
    .pads_dqs_t_0(), .pads_dqs_t_1(),
    .pads_dqs_c_0(), .pads_dqs_c_1(),
    .pads_dqs_in_t_0(1'b0), .pads_dqs_in_t_1(1'b0),
    .pads_dm_dbi_n_0(), .pads_dm_dbi_n_1(),
    .wlDelayOut(),
    .clk(refclk), .reset(~rst_n)
  );

  // Convenience aliases for the pins under test.
  wire p_ras = dut.pads_ras_n, p_cas = dut.pads_cas_n, p_we = dut.pads_we_n;
  wire p_act = dut.pads_act_n;
  wire p_cs0 = dut.pads_cs_n_0, p_cs1 = dut.pads_cs_n_1;
  wire p_a10 = dut.pads_ca_10;
  wire p_cke0 = dut.pads_cke_0;

  integer errors = 0;

  // Drive one phase-0 command combination, settle one DFI cycle, check pins.
  // Args are the EXPECTED pin levels (active-low, exactly as Table 37 rows).
  task check_vec(input [3:0] idx, input act_e, ras_e, cas_e, we_e,
                 input a10_e, cs0_e, cs1_e);
    begin
      @(negedge refclk);
      dfi_actN = {1'b1, act_e};
      dfi_rasN = {1'b1, ras_e};
      dfi_casN = {1'b1, cas_e};
      dfi_weN  = {1'b1, we_e};
      dfi_csN  = {cs1_e, cs0_e};
      dfi_addr = {{15{1'b0}}, a10_e, {10{1'b0}}};  // 26b: a10_e at bit10 (15+1+10)
      @(posedge refclk); #1;
      if (p_ras !== ras_e) begin errors = errors + 1; $display("FAIL vec[%0d] ras_n exp %b got %b", idx, ras_e, p_ras); end
      if (p_cas !== cas_e) begin errors = errors + 1; $display("FAIL vec[%0d] cas_n exp %b got %b", idx, cas_e, p_cas); end
      if (p_we  !== we_e)  begin errors = errors + 1; $display("FAIL vec[%0d] we_n  exp %b got %b", idx, we_e,  p_we);  end
      if (p_act !== act_e) begin errors = errors + 1; $display("FAIL vec[%0d] act_n exp %b got %b", idx, act_e, p_act); end
      if (p_cs0 !== cs0_e) begin errors = errors + 1; $display("FAIL vec[%0d] cs_n_0 exp %b got %b", idx, cs0_e, p_cs0); end
      if (p_cs1 !== cs1_e) begin errors = errors + 1; $display("FAIL vec[%0d] cs_n_1 exp %b got %b", idx, cs1_e, p_cs1); end
      if (p_a10 !== a10_e) begin errors = errors + 1; $display("FAIL vec[%0d] ca_10 exp %b got %b", idx, a10_e, p_a10); end
      if (p_cke0 !== 1'b1) begin errors = errors + 1; $display("FAIL vec[%0d] cke_0 exp 1 got %b", idx, p_cke0); end
    end
  endtask

  // ca[12:0] must equal {2'b00, 0, ..., a10 at bit10, ..., 0}: check all 13.
  task check_ca_zero_except_a10(input [3:0] idx, input a10_e);
    integer j;
    begin
      for (j = 0; j < 13; j = j + 1) begin
        case (j)
          0:  if (dut.pads_ca_0  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_0  exp 0 got %b", idx, dut.pads_ca_0);  end
          1:  if (dut.pads_ca_1  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_1  exp 0 got %b", idx, dut.pads_ca_1);  end
          2:  if (dut.pads_ca_2  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_2  exp 0 got %b", idx, dut.pads_ca_2);  end
          3:  if (dut.pads_ca_3  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_3  exp 0 got %b", idx, dut.pads_ca_3);  end
          4:  if (dut.pads_ca_4  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_4  exp 0 got %b", idx, dut.pads_ca_4);  end
          5:  if (dut.pads_ca_5  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_5  exp 0 got %b", idx, dut.pads_ca_5);  end
          6:  if (dut.pads_ca_6  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_6  exp 0 got %b", idx, dut.pads_ca_6);  end
          7:  if (dut.pads_ca_7  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_7  exp 0 got %b", idx, dut.pads_ca_7);  end
          8:  if (dut.pads_ca_8  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_8  exp 0 got %b", idx, dut.pads_ca_8);  end
          9:  if (dut.pads_ca_9  !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_9  exp 0 got %b", idx, dut.pads_ca_9);  end
          10: if (dut.pads_ca_10 !== a10_e) begin errors = errors + 1; $display("FAIL vec[%0d] ca_10 exp %b got %b", idx, a10_e, dut.pads_ca_10); end
          11: if (dut.pads_ca_11 !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_11 exp 0 got %b", idx, dut.pads_ca_11); end
          12: if (dut.pads_ca_12 !== 1'b0) begin errors = errors + 1; $display("FAIL vec[%0d] ca_12 exp 0 got %b", idx, dut.pads_ca_12); end
        endcase
      end
    end
  endtask

  initial begin
    // Reset
    rst_n = 1'b0;
    #40;
    rst_n = 1'b1;
    repeat (4) @(posedge refclk);

    $display("TC7: driving %0d Table-37 command vectors...", 10);

    // idx: ACT  RAS CAS WE  A10 CS0 CS1
    check_vec(4'd0, 1'b1, 1'b1, 1'b1, 1'b1, 1'b0, 1'b1, 1'b1); // NOP
    check_ca_zero_except_a10(4'd0, 1'b0);
    check_vec(4'd1, 1'b0, 1'b0, 1'b1, 1'b0, 1'b0, 1'b0, 1'b1); // ACT RA[15:13]=010
    check_ca_zero_except_a10(4'd1, 1'b0);
    check_vec(4'd2, 1'b1, 1'b1, 1'b0, 1'b1, 1'b0, 1'b0, 1'b1); // READ
    check_ca_zero_except_a10(4'd2, 1'b0);
    check_vec(4'd3, 1'b1, 1'b1, 1'b1, 1'b0, 1'b0, 1'b0, 1'b1); // WRITE
    check_ca_zero_except_a10(4'd3, 1'b0);
    check_vec(4'd4, 1'b1, 1'b0, 1'b1, 1'b0, 1'b0, 1'b0, 1'b1); // PRE
    check_ca_zero_except_a10(4'd4, 1'b0);
    check_vec(4'd5, 1'b1, 1'b0, 1'b1, 1'b0, 1'b1, 1'b0, 1'b1); // PREA (A10=1)
    check_ca_zero_except_a10(4'd5, 1'b1);
    check_vec(4'd6, 1'b1, 1'b0, 1'b0, 1'b1, 1'b0, 1'b0, 1'b1); // REF
    check_ca_zero_except_a10(4'd6, 1'b0);
    check_vec(4'd7, 1'b1, 1'b0, 1'b0, 1'b0, 1'b0, 1'b0, 1'b1); // MRS
    check_ca_zero_except_a10(4'd7, 1'b0);
    check_vec(4'd8, 1'b1, 1'b1, 1'b1, 1'b1, 1'b1, 1'b0, 1'b1); // ZQCL (A10=1, CS=0)
    check_ca_zero_except_a10(4'd8, 1'b1);
    check_vec(4'd9, 1'b1, 1'b1, 1'b0, 1'b1, 1'b0, 1'b1, 1'b0); // READ rank1
    check_ca_zero_except_a10(4'd9, 1'b0);

    repeat (2) @(posedge refclk);
    if (errors == 0)
      $display("TC7_PASS: all 10 command vectors decoded correctly on pins (ras_n/cas_n/we_n/act_n/cs/ca[10]/cke)");
    else
      $display("TC7_FAIL: %0d pin mismatches", errors);
    $finish;
  end
endmodule
