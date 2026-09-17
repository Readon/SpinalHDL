// ============================================================================
// tc5_rd_training_stub.v — TC5: read-training STUB interface contract
//
// Per ADR-0002 the read-training path is a STUB: the DFI interface is COMPLETE
// (correct widths/directions/handshake) and feedback is injectable, but the
// PHY MUST NEVER self-assign a constant "success". This test verifies the
// CONTRACT, not real alignment (which is out of scope for a stub).
//
// Assertions:
//   T1 (IRON RULE): dfi_rdTraining_rdlvlResp stays 0 for the ENTIRE run,
//       including while rdlvlEn=1. The PHY does not claim read-training done.
//   T2: dfi_rdTraining_rdlvlReq[0] mirrors dfi_rdTraining_rdlvlEn (PHY echoes
//       the MC's training-enable onto the request line).
//   T3: dfi_rdTraining_phyRdlvlCsN stays 0 (neutral, no rank selected).
//   T4: dfi_rdTraining_rdlvlGateReq stays 0 (PHY never initiates gate).
//
// PASS = all four hold. Any self-asserted rdlvlResp is an immediate FAIL.
// ============================================================================
`timescale 1ns/1ps

module tc5_rd_training_stub;

  reg refclk  = 0;
  reg dramClk = 0;
  reg rst_n   = 0;
  always #5   refclk  = ~refclk;   // 100 MHz
  always #2.5 dramClk = ~dramClk;  // 200 MHz (fr=2)

  // ---- DQ bus (idle) ----
  wire dq_0,dq_1,dq_2,dq_3,dq_4,dq_5,dq_6,dq_7;
  wire dq_8,dq_9,dq_10,dq_11,dq_12,dq_13,dq_14,dq_15;

  // ---- DFI control (idle) ----
  reg [25:0] dfi_control_address = 0;
  reg [7:0]  dfi_control_bank    = 0;
  reg [1:0]  dfi_control_rasN    = 2'b11;
  reg [1:0]  dfi_control_casN    = 2'b11;
  reg [1:0]  dfi_control_weN     = 2'b11;
  reg [3:0]  dfi_control_csN     = 4'b1111;
  reg [1:0]  dfi_control_actN    = 2'b11;
  reg [1:0]  dfi_control_bg      = 0;
  reg [1:0]  dfi_control_cid     = 0;
  reg [3:0]  dfi_control_cke     = 4'b1111;
  reg [3:0]  dfi_control_odt     = 0;
  reg [3:0]  dfi_control_resetN  = 4'b1111;

  reg  wr_en_0 = 0;  reg [31:0] wrdata_0 = 0;
  reg  [3:0]  wrmask_0 = 0; reg [1:0] wrcs_0 = 2'b11;
  reg  wr_en_1 = 0;  reg [31:0] wrdata_1 = 0;
  reg  [3:0]  wrmask_1 = 0; reg [1:0] wrcs_1 = 2'b11;
  reg  rden_0 = 0;   reg  rden_1 = 0;
  reg  [1:0]  rdcn_0 = 2'b11; reg [1:0] rdcn_1 = 2'b11;
  wire rdvalid_0, rdvalid_1;
  wire [31:0] rddata_0, rddata_1;
  wire [3:0]  rddbi_0, rddbi_1;

  // ---- Read-training channel under test (exact generated port names) ----
  reg  rdlvlEn     = 0;
  reg  rdlvlGateEn = 0;
  wire rdlvlReq;         // PHY -> MC (mirror of rdlvlEn)
  wire rdlvlResp;        // PHY -> MC (MUST stay 0 — iron rule)
  wire rdlvlGateReq;     // PHY -> MC (MUST stay 0)
  wire [1:0] phyRdlvlCsN;   // PHY -> MC (MUST stay 0)
  wire [1:0] phyRdlvlGateCsN; // PHY -> MC (0)

  // ---- Write-training (kept quiet: not under test here) ----
  reg  wrlvlEn = 0, wrlvlStrobe = 0;
  wire wrlvlReq, wrlvlResp;
  wire [1:0] phyWrlvlCsN;

  // ---- Leveling / phy-requested (quiet) ----
  reg [3:0] lvlPattern = 4'b0;
  reg       lvlPeriodic = 0;
  reg [1:0] phylvlReqCsN = 2'b0;
  wire [1:0] phylvlAckCsN;

  // ---- DQS (drive low; not under test) ----
  reg dqs_in_0 = 0, dqs_in_1 = 0;
  wire dqs_t_0, dqs_t_1, dqs_c_0, dqs_c_1;
  wire [8:0] wlDelayOut;

  // ==========================================================================
  // DUT
  // ==========================================================================
  glbl glbl();   // REQUIRED: OSERDESE3.v references glbl.GSR hierarchically

  UsPhy dut (
    .refclk(refclk), .dramClk(dramClk), .rst_n(rst_n),
    .dfi_control_address(dfi_control_address), .dfi_control_bank(dfi_control_bank),
    .dfi_control_rasN(dfi_control_rasN), .dfi_control_casN(dfi_control_casN),
    .dfi_control_weN(dfi_control_weN), .dfi_control_csN(dfi_control_csN),
    .dfi_control_actN(dfi_control_actN), .dfi_control_bg(dfi_control_bg),
    .dfi_control_cid(dfi_control_cid), .dfi_control_cke(dfi_control_cke),
    .dfi_control_odt(dfi_control_odt), .dfi_control_resetN(dfi_control_resetN),
    .dfi_write_wr_0_wrdataEn(wr_en_0), .dfi_write_wr_0_wrdata(wrdata_0),
    .dfi_write_wr_0_wrdataMask(wrmask_0), .dfi_write_wr_0_wrdataCsN(wrcs_0),
    .dfi_write_wr_1_wrdataEn(wr_en_1), .dfi_write_wr_1_wrdata(wrdata_1),
    .dfi_write_wr_1_wrdataMask(wrmask_1), .dfi_write_wr_1_wrdataCsN(wrcs_1),
    .dfi_read_rden_0(rden_0), .dfi_read_rden_1(rden_1),
    .dfi_read_rdCs_0_rddataCsN(rdcn_0), .dfi_read_rdCs_1_rddataCsN(rdcn_1),
    .dfi_read_rd_0_rddataValid(rdvalid_0), .dfi_read_rd_0_rddata(rddata_0),
    .dfi_read_rd_0_rddataDbiN(rddbi_0),
    .dfi_read_rd_1_rddataValid(rdvalid_1), .dfi_read_rd_1_rddata(rddata_1),
    .dfi_read_rd_1_rddataDbiN(rddbi_1),
    .dfi_rdTraining_rdlvlReq(rdlvlReq), .dfi_rdTraining_phyRdlvlCsN(phyRdlvlCsN),
    .dfi_rdTraining_rdlvlEn(rdlvlEn), .dfi_rdTraining_rdlvlResp(rdlvlResp),
    .dfi_rdTraining_rdlvlGateReq(rdlvlGateReq), .dfi_rdTraining_phyRdlvlGateCsN(phyRdlvlGateCsN),
    .dfi_rdTraining_rdlvlGateEn(rdlvlGateEn),
    .dfi_wrTraining_wrlvlReq(wrlvlReq), .dfi_wrTraining_phyWrlvlCsN(phyWrlvlCsN),
    .dfi_wrTraining_wrlvlEn(wrlvlEn), .dfi_wrTraining_wrlvlStrobe(wrlvlStrobe),
    .dfi_wrTraining_wrlvlResp(wrlvlResp),
    .dfi_levelingTraining_lvlPattern(lvlPattern), .dfi_levelingTraining_lvlPeriodic(lvlPeriodic),
    .dfi_phyRequestedTraining_phylvlReqCsN(phylvlReqCsN), .dfi_phyRequestedTraining_phylvlAckCsN(phylvlAckCsN),
    .pads_ck_t(), .pads_ck_c(), .pads_cke_0(), .pads_cke_1(),
    .pads_cs_n_0(), .pads_cs_n_1(),
    .pads_ca_0(), .pads_ca_1(), .pads_ca_2(), .pads_ca_3(),
    .pads_ca_4(), .pads_ca_5(), .pads_ca_6(), .pads_ca_7(),
    .pads_ca_8(), .pads_ca_9(), .pads_ca_10(), .pads_ca_11(), .pads_ca_12(),
    .pads_act_n(), .pads_bg_0(), .pads_cid_0(),
    .pads_odt_0(), .pads_odt_1(), .pads_reset_n(), .pads_par(),
    .pads_dq_0(dq_0), .pads_dq_1(dq_1), .pads_dq_2(dq_2), .pads_dq_3(dq_3),
    .pads_dq_4(dq_4), .pads_dq_5(dq_5), .pads_dq_6(dq_6), .pads_dq_7(dq_7),
    .pads_dq_8(dq_8), .pads_dq_9(dq_9), .pads_dq_10(dq_10), .pads_dq_11(dq_11),
    .pads_dq_12(dq_12), .pads_dq_13(dq_13), .pads_dq_14(dq_14), .pads_dq_15(dq_15),
    .pads_dqs_t_0(dqs_t_0), .pads_dqs_t_1(dqs_t_1),
    .pads_dqs_c_0(dqs_c_0), .pads_dqs_c_1(dqs_c_1),
    .pads_dqs_in_t_0(dqs_in_0), .pads_dqs_in_t_1(dqs_in_1),
    .pads_dm_dbi_n_0(), .pads_dm_dbi_n_1(),
    .wlDelayOut(wlDelayOut),
    .clk(refclk), .reset(~rst_n)
  );

  // ==========================================================================
  // MONITOR + ASSERTIONS
  // ==========================================================================
  integer errors = 0;
  integer resp_seen_nonzero = 0;   // T1 violation counter
  integer cs_seen_nonzero   = 0;   // T3 violation counter
  integer gate_seen_nonzero = 0;   // T4 violation counter
  integer req_mirror_bad    = 0;   // T2 violation counter
  integer t1_pass = 0, t2_pass = 0, t3_pass = 0, t4_pass = 0;

  // T1/T3/T4: sample every refclk edge; any nonzero is a contract violation.
  always @(posedge refclk) begin
    if (rst_n) begin
      if (rdlvlResp !== 1'b0)      resp_seen_nonzero = resp_seen_nonzero + 1;
      if (phyRdlvlCsN !== 2'b00)   cs_seen_nonzero   = cs_seen_nonzero + 1;
      if (rdlvlGateReq !== 1'b0)   gate_seen_nonzero = gate_seen_nonzero + 1;
      // T2: rdlvlReq[0] must equal rdlvlEn (settled relation, both stable per edge)
      if (rdlvlReq !== rdlvlEn)    req_mirror_bad    = req_mirror_bad + 1;
    end
  end

  initial begin
    // Reset
    rst_n = 0;
    #100;
    rst_n = 1;
    #100;

    $display("== TC5: read-training STUB interface contract ==");

    // Phase A: rdlvlEn low (idle)
    $display("  phase A: rdlvlEn=0 (idle)");
    rdlvlEn = 0;
    rdlvlGateEn = 0;
    repeat (20) @(posedge refclk);

    // Phase B: rdlvlEn high (MC requests read training) — the critical case
    $display("  phase B: rdlvlEn=1 (MC requests read training)");
    rdlvlEn = 1;
    repeat (40) @(posedge refclk);
    // rdlvlGateExercised: also pulse gate-en while training on
    rdlvlGateEn = 1;
    repeat (20) @(posedge refclk);
    rdlvlGateEn = 0;
    rdlvlEn = 0;
    repeat (20) @(posedge refclk);

    // ---- Evaluate ----
    $display("");

    // T1: IRON RULE — rdlvlResp must have stayed 0 the whole time.
    if (resp_seen_nonzero == 0) begin
      $display("  [PASS] T1 (ADR-0002): rdlvlResp NEVER self-asserted (0 violations over full run, incl. rdlvlEn=1)");
      t1_pass = 1;
    end else begin
      $display("  [FAIL] T1 (ADR-0002): rdlvlResp self-asserted %0d time(s) — violates 'never self-assign success'", resp_seen_nonzero);
      errors = errors + 1;
    end

    // T2: rdlvlReq mirrors rdlvlEn.
    if (req_mirror_bad == 0) begin
      $display("  [PASS] T2: rdlvlReq[0] mirrored rdlvlEn on every refclk edge (0 mismatches)");
      t2_pass = 1;
    end else begin
      $display("  [FAIL] T2: rdlvlReq/rldlvlEn mismatch on %0d edge(s)", req_mirror_bad);
      errors = errors + 1;
    end

    // T3: phyRdlvlCsN neutral.
    if (cs_seen_nonzero == 0) begin
      $display("  [PASS] T3: phyRdlvlCsN stayed 0 (no rank selected)");
      t3_pass = 1;
    end else begin
      $display("  [FAIL] T3: phyRdlvlCsN nonzero %0d time(s)", cs_seen_nonzero);
      errors = errors + 1;
    end

    // T4: rdlvlGateReq never initiated.
    if (gate_seen_nonzero == 0) begin
      $display("  [PASS] T4: rdlvlGateReq stayed 0 (PHY never initiates gate)");
      t4_pass = 1;
    end else begin
      $display("  [FAIL] T4: rdlvlGateReq nonzero %0d time(s)", gate_seen_nonzero);
      errors = errors + 1;
    end

    // ---- Summary ----
    $display("");
    $display("==================== SUMMARY ====================");
    if (errors == 0)
      $display("TC5: PASS — read-training STUB obeys the ADR-0002 contract (interface complete, never self-claims success)");
    else
      $display("TC5: FAIL — %0d contract violation(s)", errors);
    $display("");
    $display("SCOPE: this verifies the INTERFACE CONTRACT of the read-training stub.");
    $display("       Actual read-path alignment is UNVERIFIED_ON_BOARD (out of scope for a stub).");
    $finish;
  end

  // Safety timeout
  initial begin
    #50000;
    $display("TIMEOUT: TC5 did not finish within 50 us");
    $finish;
  end

endmodule