// ============================================================================
// tc8_pin_model.v — TC8: pin-level command decode closed loop (L3)
//
// Topology: UsPhy (DUT) + Ddr4PinModel (pin-level command decoder).
// DFI command vectors are driven into the PHY exactly as the MC (Task2IDFI)
// encodes them; the MODEL decodes what a real DDR4 DRAM would decode from
// the PHYSICAL PINS. This catches command phasing/mapping bugs that TC7's
// transparency check structurally cannot (TC7 only proves pin == DFI bit).
//
// EXPECTED RESULTS
//   Fixed PHY : TC8_PASS — every command decoded EXACTLY ONCE, fields exact.
//   Buggy PHY : TC8_FAIL — (a) each command decoded TWICE (phase-0 level held
//   across both DRAM cycles of the DFI pair), (b) ACT/PRE bank wrong (dfi_bank
//   never mapped to A[2:0]).
//
// CLOCK ALIGNMENT CONTRACT (DFI spec convention: DFI rising edge aligns with
// DRAM CK falling edge):
//   refclk : 10 ns period, rising at t = 5, 15, 25, ...   (DFI clock)
//   dramClk:  5 ns period, rising at t = 2.5, 7.5, 12.5,  (DRAM CK)
//   - DFI vectors are driven on refclk RISING edges (t = 5+10k), which
//     coincide with dramClk FALLING edges — the spec's alignment.
//   - The model samples on ck_t (=dramClk) RISING edges:
//       t = 7.5+10k  -> first DRAM cycle of DFI pair k  = DFI phase 0 window
//       t = 12.5+10k -> second DRAM cycle of DFI pair k = DFI phase 1 window
//   - UsPhy's dramPhase parity (init False, toggling on dramClk rising, reset
//     held until ~t=10 so the first toggle lands at t=12.5) yields:
//       select = 0 during [7.5+10k, 12.5+10k)  -> phase 0 on pins
//       select = 1 during [12.5+10k, 17.5+10k) -> phase 1 on pins
//   - Reset: rst_n=0 for t in [0,3). rstSynced (2-stage, refclk domain)
//     clears at t=10, inside the first DFI pair, so the parity alignment
//     above holds from the first command window.
//
// VECTOR SET (each command holds 1 DFI cycle, followed by 1 deselect fill
// cycle; fields/counts checked at the fill-cycle midpoint t = 20+10k, after
// BOTH sample edges of the command window have fired):
//   v1  ACT  rank0 row=16'hE123 bank={bg0,ba4}
//   v2  READ         col=10'h2BC bank={bg1,ba2} autoPre=0
//   v3  WRITE        col=10'h123 bank={bg0,ba3} autoPre=0
//   v4  PRE          bank={bg1,ba1} allBanks=0
//   v5  REF
//   v6  MRS          op=5 (MR5) bank={bg0,ba0}
//   v7  ZQCL         (A10=1)
//   v8  NOP          (CS=0, strokes=111, A10=0)
//   v9  ACT  rank1 row=16'h0F00 bank={bg0,ba1}
// ============================================================================
`timescale 1ns/1ps
module tc8_pin_model;
  reg refclk = 0, dramClk = 0, rst_n = 0;
  always #5   refclk  = ~refclk;  // rising at 5,15,25
  always #2.5 dramClk = ~dramClk; // rising at 2.5,7.5,12.5

  // ---- DFI control drivers -------------------------------------------------
  reg [1:0] dfi_actN = 2'b11, dfi_rasN = 2'b11, dfi_casN = 2'b11, dfi_weN = 2'b11;
  reg [1:0] dfi_csN  = 2'b11;
  reg [25:0] dfi_addr = 26'h0;
  reg [7:0]  dfi_bank = 8'h0;
  reg [1:0]  dfi_bg   = 2'b0;

  // ---- DUT ------------------------------------------------------------------
  glbl glbl();
  UsPhy dut (
    .refclk(refclk), .dramClk(dramClk), .rst_n(rst_n),
    .dfi_control_address(dfi_addr), .dfi_control_bank(dfi_bank),
    .dfi_control_rasN(dfi_rasN), .dfi_control_casN(dfi_casN),
    .dfi_control_weN(dfi_weN),
    .dfi_control_csN({2'b11, dfi_csN}),
    .dfi_control_actN(dfi_actN),
    .dfi_control_bg(dfi_bg), .dfi_control_cid(2'h0),
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

  // ---- Pin-level command decoder (L3 model) ---------------------------------
  wire [12:0] pin_a = {dut.pads_ca_12, dut.pads_ca_11, dut.pads_ca_10,
                       dut.pads_ca_9,  dut.pads_ca_8,  dut.pads_ca_7,
                       dut.pads_ca_6,  dut.pads_ca_5,  dut.pads_ca_4,
                       dut.pads_ca_3,  dut.pads_ca_2,  dut.pads_ca_1,
                       dut.pads_ca_0};
  Ddr4PinModel #(
    .NRANKS(2), .ADDR_W(13), .BG_W(1), .BA_W(3),
    .CID_W(1), .COL_W(10), .ROW_W(16)
  ) model (
    .ck_t     (dut.pads_ck_t),
    .rst_n    (rst_n),
    .cs_n     ({dut.pads_cs_n_1, dut.pads_cs_n_0}),
    .cke      ({dut.pads_cke_1,  dut.pads_cke_0}),
    .act_n    (dut.pads_act_n),
    .ras_n    (dut.pads_ras_n),
    .cas_n    (dut.pads_cas_n),
    .we_n     (dut.pads_we_n),
    .bg       (dut.pads_bg_0),
    .ba       ({dut.pads_ba_2, dut.pads_ba_1, dut.pads_ba_0}),  // dedicated BA pads (ADR-0012 amd)
    .a        (pin_a),
    .cid      (dut.pads_cid_0),
    .q_act_pulse(), .q_rd_pulse(), .q_wr_pulse(), .q_pre_pulse(),
    .q_ref_pulse(), .q_mrs_pulse(), .q_zqcl_pulse(), .q_nop_pulse(),
    .q_illegal_pulse(),
    .last_row(), .last_col(), .last_bank(), .last_op(),
    .last_autopre(), .last_allbanks(), .last_cid(), .last_rank(),
    .cnt_act(), .cnt_rd(), .cnt_wr(), .cnt_pre(),
    .cnt_ref(), .cnt_mrs(), .cnt_zqcl(), .cnt_nop(),
    .cnt_illegal(), .cnt_pd(), .cnt_deselect(),
    .illegal_flag()
  );

  integer errors = 0;

  // Drive one DFI cycle: phase 0 = command, phase 1 = NOP (MC convention).
  task drive_cmd(input act_p0, ras_p0, cas_p0, we_p0, cs0, cs1,
                 input [12:0] addr_p0, input bg_p0, input [2:0] ba_p0);
    begin
      @(posedge refclk);  // DFI rising edge = DRAM CK falling edge
      dfi_actN = {1'b1, act_p0};
      dfi_rasN = {1'b1, ras_p0};
      dfi_casN = {1'b1, cas_p0};
      dfi_weN  = {1'b1, we_p0};
      dfi_csN  = {cs1, cs0};
      dfi_addr = {{13{1'b0}}, addr_p0};
      dfi_bg   = {1'b0, bg_p0};
      dfi_bank = {4'b0000, {1'b0, ba_p0}};  // phase1=0, phase0={0,ba}
    end
  endtask

  // Deselect fill cycle (both phases CS=1).
  task drive_deselect;
    begin
      @(posedge refclk);
      dfi_actN = 2'b11; dfi_rasN = 2'b11; dfi_casN = 2'b11; dfi_weN = 2'b11;
      dfi_csN  = 2'b11;
      dfi_addr = 26'h0; dfi_bg = 2'b0; dfi_bank = 8'h0;
    end
  endtask

  // chk<id> map: 1=v1.cnt_act 2=v1.last_row 3=v1.last_bank 4=v1.last_rank
  //               5=v2.cnt_rd  6=v2.last_col 7=v3.cnt_wr  8=v3.last_col
  //               9=v4.cnt_pre 10=v4.last_bank 11=v4.allbanks 12=v5.cnt_ref
  //               13=v6.cnt_mrs 14=v6.last_op 15=v6.last_bank 16=v7.cnt_zqcl
  //               17=v8.cnt_nop 18=v9.cnt_act 19=v9.last_row 20=v9.last_rank
  //               21=cnt_illegal 22=illegal_flag
  task chk(input [7:0] id, input [15:0] got, input [15:0] exp);
    begin
      if (got !== exp) begin
        errors = errors + 1;
        $display("FAIL [chk%0d] got=%0d exp=%0d", id, got, exp);
      end
    end
  endtask

  initial begin
    rst_n = 1'b0;
    #3;
    rst_n = 1'b1;

    // Wait for the PHY's two-stage reset (rstSynced) to release (~t=17.5 ns)
    // and re-anchor on a refclk edge. Without this, the first command window
    // straddles the release: dramPhase is held at False (reset value) across
    // BOTH DRAM cycles of the window, so phase 0 decodes twice. Waiting an
    // INTEGER number of refclk cycles (each = 2 DRAM cycles) preserves the
    // toggle parity of dramPhase for all subsequent vectors.
    repeat(4) @(posedge refclk);

    // ---- v1: ACT rank0, row=16'hE123 = {1,1,1, 13'h123}, bank={bg0,ba4} ----
    //      (0xE123 = 1110_0001_0010_0011: row[15:13]=111, row[12:0]=13'h123)
    drive_cmd(1'b0, 1'b1, 1'b1, 1'b1, 1'b0, 1'b1, 13'h123, 1'b0, 3'b100);
    drive_deselect;
    @(negedge refclk); #1;  // t ~= 21: both sample edges of cmd window fired
    chk(8'd1, model.cnt_act, 16'd1);
    chk(8'd2, {16{1'b0}} | {model.last_row}, 16'hE123);
    chk(8'd3, {16{1'b0}} | {model.last_bank}, 16'd4);
    chk(8'd4, {16{1'b0}} | {model.last_rank}, 16'd0);

    // ---- v2: READ col=10'h2BC, bank={bg1,ba2}, autoPre=0 --------------------
    drive_cmd(1'b1, 1'b1, 1'b0, 1'b1, 1'b0, 1'b1, {2'b00, 1'b0, 10'h2BC}, 1'b1, 3'b010);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd5, model.cnt_rd, 16'd1);
    chk(8'd6, {16{1'b0}} | {model.last_col}, 16'h2BC);

    // ---- v3: WRITE col=10'h123, bank={bg0,ba3} ------------------------------
    drive_cmd(1'b1, 1'b1, 1'b1, 1'b0, 1'b0, 1'b1, {2'b00, 1'b0, 10'h123}, 1'b0, 3'b011);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd7, model.cnt_wr, 16'd1);
    chk(8'd8, {16{1'b0}} | {model.last_col}, 16'h123);

    // ---- v4: PRE bank={bg1,ba1}, allBanks=0 ---------------------------------
    drive_cmd(1'b1, 1'b0, 1'b1, 1'b0, 1'b0, 1'b1, 13'h000, 1'b1, 3'b001);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd9, model.cnt_pre, 16'd1);
    chk(8'd10, {16{1'b0}} | {model.last_bank}, 16'd9);
    chk(8'd11, {16{1'b0}} | {model.last_allbanks}, 16'd0);

    // ---- v5: REF ------------------------------------------------------------
    drive_cmd(1'b1, 1'b0, 1'b0, 1'b1, 1'b0, 1'b1, 13'h000, 1'b0, 3'b000);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd12, model.cnt_ref, 16'd1);

    // ---- v6: MRS op=5 (lands on a[9:6]), bank={bg0,ba0} ------------------
    //      13-bit addr: a[12:11]=00, a[9:6]=0101, a[5:0]=000000  (3+4+6=13)
    drive_cmd(1'b1, 1'b0, 1'b0, 1'b0, 1'b0, 1'b1, {3'b000, 4'd5, 6'b000000}, 1'b0, 3'b000);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd13, model.cnt_mrs, 16'd1);
    chk(8'd14, {16{1'b0}} | {model.last_op}, 16'd5);
    chk(8'd15, {16{1'b0}} | {model.last_bank}, 16'd0);

    // ---- v7: ZQCL (A10=1, strokes=111) --------------------------------------
    drive_cmd(1'b1, 1'b1, 1'b1, 1'b1, 1'b0, 1'b1, {2'b00, 1'b1, 10'h000}, 1'b0, 3'b000);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd16, model.cnt_zqcl, 16'd1);

    // ---- v8: NOP (CS=0, strokes=111, A10=0) ---------------------------------
    drive_cmd(1'b1, 1'b1, 1'b1, 1'b1, 1'b0, 1'b1, 13'h000, 1'b0, 3'b000);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd17, model.cnt_nop, 16'd1);

    // ---- v9: ACT rank1, row=16'h0F00 = {0,0,0, 13'h0F00}, bank={bg0,ba1} ---
    //      (0x0F00 = 0000_1111_0000_0000: row[15:13]=000, row[12:0]=13'h0F00)
    drive_cmd(1'b0, 1'b0, 1'b0, 1'b0, 1'b1, 1'b0, 13'h0F00, 1'b0, 3'b001);
    drive_deselect;
    @(negedge refclk); #1;
    chk(8'd18, model.cnt_act, 16'd2);
    chk(8'd19, {16{1'b0}} | {model.last_row}, 16'h0F00);
    chk(8'd20, {16{1'b0}} | {model.last_rank}, 16'd1);

    // ---- global: no illegal decodes -----------------------------------------
    repeat (2) @(posedge refclk);
    chk(8'd21, {16{1'b0}} | {model.cnt_illegal}, 16'd0);
    chk(8'd22, {16{1'b0}} | {model.illegal_flag}, 16'd0);

    if (errors == 0)
      $display("TC8_PASS: pin-level command decode closed loop — every command decoded exactly once with correct fields");
    else
      $display("TC8_FAIL: %0d mismatches (double-execution and/or bank-loss indicated)", errors);
    $finish;
  end
endmodule
