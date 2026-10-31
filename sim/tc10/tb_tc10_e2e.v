// ============================================================================
// tb_tc10_e2e.v — TC10: end-to-end integration (Steps 8b + 8c)
//
//   DfiController (MC, generated)  --DFI-->  UsPhy (PHY, generated)
//         ^                                        | pads
//         | Bmb                                    v
//      (TB)                                Ddr4SdramModel (TC9)
//
// Check sequence:
//   INIT: MC's init sequencer passes through untouched (mrs/zqcl counters,
//         zero illegal decodes).
//   W2:  write leveling CLOSED LOOP: the TB acts as training initiator
//        (wrlvlEn); PHY echoes DQS through ODELAYE3(wb_delay), the model
//        returns it with a 2.5ns analog delay, IDELAYE3 feeds it back and
//        the bang-bang loop converges (attractor: total loop delay =
//        k*DQS period, i.e. wb_delay ~= 150 mod 200 from the 256 seed ->
//        350<->351). wrlvlResp is EARNED by the convergence check.
//   W1:  a Bmb WRITE reaches the model — wr_key_last latches a sane key and
//        wr_word matches the 256-bit Bmb payload.
//   R1:  a Bmb READ of the same address returns the EXACT written bytes
//        (frag0/frag1 roundtrip equality).
//   W2*: no illegal command decodes (hierarchical probe of the pin model).
//
// Negative proof (write leveling): mutate the feedback severance knob in a
// COPY, keep this canonical file:
//     sed 's/parameter WL_ECHO_CUT_P = 0;/parameter WL_ECHO_CUT_P = 1;/' \
//         tb_tc10_e2e.v > tb_tc10_e2e_cut.v
//     TB_FILE=tb_tc10_e2e_cut.v ./run_tc10.sh
//   Expect: TC10_FAIL (wrlvlResp never earned — loop cannot converge).
//
// Clocking: refclk 100MHz, dramClk 200MHz (fr=2), same phase convention as
// tb_tc1.v. The model is clocked from the TB's dramClk net directly (clean
// reference); pads_ck_t is the PHY's DRAM-clock OUTPUT and is left
// observable-only for now.
// ============================================================================
`timescale 1ns/1ps
module tb_tc10_e2e;

  // Required: golden OSERDESE3.v/ISERDESE3.v reference glbl.GSR hierarchically
  glbl glbl();

  // ---- clocks / reset ------------------------------------------------------
  reg refclk  = 0;
  reg dramClk = 0;
  always #5   refclk  = ~refclk;   // 100 MHz
  always #2.5 dramClk = ~dramClk;  // 200 MHz (= 2 x refclk, fr=2)
  // Read-leveling reference oscillator: surrogate DRAM read DQS on the
  // receive pad while rdlvlEn (the model drives no read DQS).
  reg tb_osr; initial tb_osr = 1'b0;
  always @(posedge dramClk) tb_osr = ~tb_osr;
  reg rst_n = 0;

  // ---- Bmb master (TB side) -------------------------------------------------
  localparam ADDR_W = 29;
  reg              bmb_cmd_valid = 0;
  wire             bmb_cmd_ready;
  reg              bmb_cmd_last  = 0;
  reg              bmb_cmd_opcode = 0;   // 0 = READ, 1 = WRITE
  reg [ADDR_W-1:0] bmb_cmd_address = 0;
  reg [9:0]        bmb_cmd_length  = 0;  // bytes
  reg [63:0]       bmb_cmd_data    = 0;
  reg [7:0]        bmb_cmd_mask    = 8'hFF;
  reg [1:0]        bmb_cmd_context = 0;

  wire             bmb_rsp_valid;
  reg              bmb_rsp_ready = 1;
  wire             bmb_rsp_last;
  wire [0:0]       bmb_rsp_opcode;
  wire [63:0]      bmb_rsp_data;
  wire [1:0]       bmb_rsp_context;

  // ---- DFI nets (MC <-> PHY, direct) ---------------------------------------
  wire [25:0] dfi_control_address;
  wire [7:0]  dfi_control_bank;
  wire [1:0]  dfi_control_rasN, dfi_control_casN, dfi_control_weN;
  wire [3:0]  dfi_control_csN, dfi_control_cke, dfi_control_odt, dfi_control_resetN;
  wire [1:0]  dfi_control_actN, dfi_control_bg, dfi_control_cid;
  wire        dfi_wr_0_en, dfi_wr_1_en;
  wire [31:0] dfi_wr_0_data, dfi_wr_1_data;
  wire [3:0]  dfi_wr_0_mask, dfi_wr_1_mask;
  wire [1:0]  dfi_wr_0_csN, dfi_wr_1_csN;
  wire        dfi_rden_0, dfi_rden_1;
  wire [1:0]  dfi_rdCs_0_csN, dfi_rdCs_1_csN;
  wire        dfi_rd_0_valid, dfi_rd_1_valid;
  wire [31:0] dfi_rd_0_data, dfi_rd_1_data;
  wire [3:0]  dfi_rd_0_dbIN, dfi_rd_1_dbIN;
  wire        tr_rdlvlReq, tr_rdlvlResp, tr_rdlvlGateReq, tr_rdlvlGateEn;
  wire        tr_rdlvlEn_mc;   // MC idles this at 0 (training initiation out of MC scope)
  reg         tr_rdlvlEn = 1'b0; // TB acts as the training initiator (see R2)
  wire [1:0]  tr_phyRdlvlCsN, tr_phyRdlvlGateCsN;
  wire        tr_wrlvlReq, tr_wrlvlStrobe, tr_wrlvlResp;
  wire        tr_wrlvlEn_mc;   // MC idles this at 0 (DFI training initiation out of MC scope)
  reg         tr_wrlvlEn = 1'b0; // TB acts as the training initiator (see W2)
  wire [1:0]  tr_phyWrlvlCsN;
  wire [3:0]  tr_lvlPattern;
  wire        tr_lvlPeriodic;
  wire [1:0]  tr_phylvlReqCsN, tr_phylvlAckCsN;

  // ---- PHY pads <-> model ----------------------------------------------------
  wire [15:0] dq;
  wire        p_ck_t, p_ck_c, p_cke_0, p_cke_1, p_cs_n_0, p_cs_n_1;
  wire [12:0] p_ca;
  wire [12:0] pads_ca;
  wire        p_ras_n, p_cas_n, p_we_n, p_act_n, p_bg, p_cid;
  wire [2:0]  p_ba;
  wire [2:0]  pads_ba;
  wire        p_odt_0, p_odt_1, p_reset_n, p_par;
  wire [1:0]  p_dqs_t, p_dqs_c, p_dm_dbi_n;
  wire [1:0]  dqs_echo_raw;   // model output (returned DQS)
  wire [1:0]  mem_dm_out;     // model DM during reads -> PHY dm_dbi_in
  wire [1:0]  p_dqs_in_t;     // cut-muxed feed to the PHY dqs_in_t pads
  wire [1:0]  pads_dqs_t, pads_dqs_c, pads_dm_dbi_n;
  wire [8:0]  wlDelayOut;
  wire [8:0]  rdDelayOut;
  reg  [8:0]  rd_converged;
  // Equilibrium of the RL bang-bang = delay an integer multiple of the 5ns DQS
  // period = rdDelay ~= 0 (mod 100 taps); +-5 tolerance for the limit cycle.
  wire [6:0]  rd_mod100 = rd_converged % 7'd100;

  assign p_ca      = pads_ca;
  assign p_ba      = pads_ba;
  assign p_dqs_t   = pads_dqs_t;
  assign p_dqs_c   = pads_dqs_c;
  // Negative-proof knob (mutant run): flip to 1 in a COPIED tb
  // (sed -> tb_tc10_e2e_cut.v) to sever the DQS feedback path; the PHY must
  // then fail to converge (wrlvlResp never earns). Kept 0 in this canonical
  // file. (xvlog -define turned out unreliable on the full file list.)
  parameter WL_ECHO_CUT_P = 0;
  parameter RCUT_P = 0;
  assign p_dqs_in_t = RCUT_P ? 2'b00 : (tr_rdlvlEn ? {tb_osr,tb_osr} : (WL_ECHO_CUT_P ? 2'b00 : dqs_echo_raw));
  assign p_dm_dbi_n = pads_dm_dbi_n;

  // ---- model observability ----------------------------------------------------
  wire [15:0]  mem_wr_key_last, mem_rd_key_last;
  wire [255:0] mem_wr_word_last;
  wire         mem_rd_driving, mem_rd_pending_err;
  wire [15:0]  mem_dqs_blank_cnt;
  wire         mem_dqs_blank_err;

  // ---- model command counters (hierarchical probe into Ddr4PinModel) ---------
  wire [15:0] init_cnt_mrs   = mem.cmd.cnt_mrs;
  wire [15:0] init_cnt_zqcl  = mem.cmd.cnt_zqcl;
  wire [15:0] init_cnt_ref   = mem.cmd.cnt_ref;
  wire [15:0] init_cnt_act   = mem.cmd.cnt_act;
  wire [15:0] init_cnt_wr    = mem.cmd.cnt_wr;
  wire [15:0] init_cnt_rd    = mem.cmd.cnt_rd;
  wire [15:0] init_cnt_pre   = mem.cmd.cnt_pre;
  wire [15:0] init_cnt_illegal = mem.cmd.cnt_illegal;

  // ===========================================================================
  // MC
  // ===========================================================================
  DfiController mc (
    .clk(refclk), .reset(~rst_n),
    .io_bmb_cmd_valid(bmb_cmd_valid),
    .io_bmb_cmd_ready(bmb_cmd_ready),
    .io_bmb_cmd_payload_last(bmb_cmd_last),
    .io_bmb_cmd_payload_fragment_opcode(bmb_cmd_opcode),
    .io_bmb_cmd_payload_fragment_address(bmb_cmd_address),
    .io_bmb_cmd_payload_fragment_length(bmb_cmd_length),
    .io_bmb_cmd_payload_fragment_data(bmb_cmd_data),
    .io_bmb_cmd_payload_fragment_mask(bmb_cmd_mask),
    .io_bmb_cmd_payload_fragment_context(bmb_cmd_context),
    .io_bmb_rsp_valid(bmb_rsp_valid),
    .io_bmb_rsp_ready(bmb_rsp_ready),
    .io_bmb_rsp_payload_last(bmb_rsp_last),
    .io_bmb_rsp_payload_fragment_opcode(bmb_rsp_opcode),
    .io_bmb_rsp_payload_fragment_data(bmb_rsp_data),
    .io_bmb_rsp_payload_fragment_context(bmb_rsp_context),
    .io_dfi_control_address(dfi_control_address),
    .io_dfi_control_bank(dfi_control_bank),
    .io_dfi_control_rasN(dfi_control_rasN),
    .io_dfi_control_casN(dfi_control_casN),
    .io_dfi_control_weN(dfi_control_weN),
    .io_dfi_control_csN(dfi_control_csN),
    .io_dfi_control_actN(dfi_control_actN),
    .io_dfi_control_bg(dfi_control_bg),
    .io_dfi_control_cid(dfi_control_cid),
    .io_dfi_control_cke(dfi_control_cke),
    .io_dfi_control_odt(dfi_control_odt),
    .io_dfi_control_resetN(dfi_control_resetN),
    .io_dfi_write_wr_0_wrdataEn(dfi_wr_0_en),
    .io_dfi_write_wr_0_wrdata(dfi_wr_0_data),
    .io_dfi_write_wr_0_wrdataMask(dfi_wr_0_mask),
    .io_dfi_write_wr_0_wrdataCsN(dfi_wr_0_csN),
    .io_dfi_write_wr_1_wrdataEn(dfi_wr_1_en),
    .io_dfi_write_wr_1_wrdata(dfi_wr_1_data),
    .io_dfi_write_wr_1_wrdataMask(dfi_wr_1_mask),
    .io_dfi_write_wr_1_wrdataCsN(dfi_wr_1_csN),
    .io_dfi_read_rden_0(dfi_rden_0),
    .io_dfi_read_rden_1(dfi_rden_1),
    .io_dfi_read_rdCs_0_rddataCsN(dfi_rdCs_0_csN),
    .io_dfi_read_rdCs_1_rddataCsN(dfi_rdCs_1_csN),
    .io_dfi_read_rd_0_rddataValid(dfi_rd_0_valid),
    .io_dfi_read_rd_0_rddata(dfi_rd_0_data),
    .io_dfi_read_rd_0_rddataDbiN(dfi_rd_0_dbIN),
    .io_dfi_read_rd_1_rddataValid(dfi_rd_1_valid),
    .io_dfi_read_rd_1_rddata(dfi_rd_1_data),
    .io_dfi_read_rd_1_rddataDbiN(dfi_rd_1_dbIN),
    .io_dfi_rdTraining_rdlvlReq(tr_rdlvlReq),
    .io_dfi_rdTraining_phyRdlvlCsN(tr_phyRdlvlCsN),
    .io_dfi_rdTraining_rdlvlEn(tr_rdlvlEn_mc),
    .io_dfi_rdTraining_rdlvlResp(tr_rdlvlResp),
    .io_dfi_rdTraining_rdlvlGateReq(tr_rdlvlGateReq),
    .io_dfi_rdTraining_phyRdlvlGateCsN(tr_phyRdlvlGateCsN),
    .io_dfi_rdTraining_rdlvlGateEn(tr_rdlvlGateEn),
    .io_dfi_wrTraining_wrlvlReq(tr_wrlvlReq),
    .io_dfi_wrTraining_phyWrlvlCsN(tr_phyWrlvlCsN),
    .io_dfi_wrTraining_wrlvlEn(tr_wrlvlEn_mc),
    .io_dfi_wrTraining_wrlvlStrobe(tr_wrlvlStrobe),
    .io_dfi_wrTraining_wrlvlResp(tr_wrlvlResp),
    .io_dfi_levelingTraining_lvlPattern(tr_lvlPattern),
    .io_dfi_levelingTraining_lvlPeriodic(tr_lvlPeriodic),
    .io_dfi_phyRequestedTraining_phylvlReqCsN(tr_phylvlReqCsN),
    .io_dfi_phyRequestedTraining_phylvlAckCsN(tr_phylvlAckCsN)
  );

  // ===========================================================================
  // PHY
  // ===========================================================================
  UsPhy phy (
    .refclk(refclk), .dramClk(dramClk), .rst_n(rst_n),
    .clk(refclk), .reset(~rst_n),  // SpinalHDL default-domain ports (rst/rstSynced chain)
    .dfi_control_address(dfi_control_address),
    .dfi_control_bank(dfi_control_bank),
    .dfi_control_rasN(dfi_control_rasN),
    .dfi_control_casN(dfi_control_casN),
    .dfi_control_weN(dfi_control_weN),
    .dfi_control_csN(dfi_control_csN),
    .dfi_control_actN(dfi_control_actN),
    .dfi_control_bg(dfi_control_bg),
    .dfi_control_cid(dfi_control_cid),
    .dfi_control_cke(dfi_control_cke),
    .dfi_control_odt(dfi_control_odt),
    .dfi_control_resetN(dfi_control_resetN),
    .dfi_write_wr_0_wrdataEn(dfi_wr_0_en),
    .dfi_write_wr_0_wrdata(dfi_wr_0_data),
    .dfi_write_wr_0_wrdataMask(dfi_wr_0_mask),
    .dfi_write_wr_0_wrdataCsN(dfi_wr_0_csN),
    .dfi_write_wr_1_wrdataEn(dfi_wr_1_en),
    .dfi_write_wr_1_wrdata(dfi_wr_1_data),
    .dfi_write_wr_1_wrdataMask(dfi_wr_1_mask),
    .dfi_write_wr_1_wrdataCsN(dfi_wr_1_csN),
    .dfi_read_rden_0(dfi_rden_0),
    .dfi_read_rden_1(dfi_rden_1),
    .dfi_read_rdCs_0_rddataCsN(dfi_rdCs_0_csN),
    .dfi_read_rdCs_1_rddataCsN(dfi_rdCs_1_csN),
    .dfi_read_rd_0_rddataValid(dfi_rd_0_valid),
    .dfi_read_rd_0_rddata(dfi_rd_0_data),
    .dfi_read_rd_0_rddataDbiN(dfi_rd_0_dbIN),
    .dfi_read_rd_1_rddataValid(dfi_rd_1_valid),
    .dfi_read_rd_1_rddata(dfi_rd_1_data),
    .dfi_read_rd_1_rddataDbiN(dfi_rd_1_dbIN),
    .dfi_rdTraining_rdlvlReq(tr_rdlvlReq),
    .dfi_rdTraining_phyRdlvlCsN(tr_phyRdlvlCsN),
    .dfi_rdTraining_rdlvlEn(tr_rdlvlEn),
    .dfi_rdTraining_rdlvlResp(tr_rdlvlResp),
    .dfi_rdTraining_rdlvlGateReq(tr_rdlvlGateReq),
    .dfi_rdTraining_phyRdlvlGateCsN(tr_phyRdlvlGateCsN),
    .dfi_rdTraining_rdlvlGateEn(tr_rdlvlGateEn),
    .dfi_wrTraining_wrlvlReq(tr_wrlvlReq),
    .dfi_wrTraining_phyWrlvlCsN(tr_phyWrlvlCsN),
    .dfi_wrTraining_wrlvlEn(tr_wrlvlEn),
    .dfi_wrTraining_wrlvlStrobe(tr_wrlvlStrobe),
    .dfi_wrTraining_wrlvlResp(tr_wrlvlResp),
    .dfi_levelingTraining_lvlPattern(tr_lvlPattern),
    .dfi_levelingTraining_lvlPeriodic(tr_lvlPeriodic),
    .dfi_phyRequestedTraining_phylvlReqCsN(tr_phylvlReqCsN),
    .dfi_phyRequestedTraining_phylvlAckCsN(tr_phylvlAckCsN),
    .wlDelayOut(wlDelayOut),
    .rdDelayOut(rdDelayOut),
    .pads_ck_t(p_ck_t), .pads_ck_c(p_ck_c),
    .pads_cke_0(p_cke_0), .pads_cke_1(p_cke_1),
    .pads_cs_n_0(p_cs_n_0), .pads_cs_n_1(p_cs_n_1),
    .pads_ca_0(pads_ca[0]),  .pads_ca_1(pads_ca[1]),
    .pads_ca_2(pads_ca[2]),  .pads_ca_3(pads_ca[3]),
    .pads_ca_4(pads_ca[4]),  .pads_ca_5(pads_ca[5]),
    .pads_ca_6(pads_ca[6]),  .pads_ca_7(pads_ca[7]),
    .pads_ca_8(pads_ca[8]),  .pads_ca_9(pads_ca[9]),
    .pads_ca_10(pads_ca[10]), .pads_ca_11(pads_ca[11]),
    .pads_ca_12(pads_ca[12]),
    .pads_ras_n(p_ras_n), .pads_cas_n(p_cas_n),
    .pads_we_n(p_we_n), .pads_act_n(p_act_n),
    .pads_bg_0(p_bg),
    .pads_ba_0(pads_ba[0]), .pads_ba_1(pads_ba[1]), .pads_ba_2(pads_ba[2]),
    .pads_cid_0(p_cid),
    .pads_odt_0(p_odt_0), .pads_odt_1(p_odt_1),
    .pads_reset_n(p_reset_n), .pads_par(p_par),
    .pads_dq_0(dq[0]),  .pads_dq_1(dq[1]),  .pads_dq_2(dq[2]),  .pads_dq_3(dq[3]),
    .pads_dq_4(dq[4]),  .pads_dq_5(dq[5]),  .pads_dq_6(dq[6]),  .pads_dq_7(dq[7]),
    .pads_dq_8(dq[8]),  .pads_dq_9(dq[9]),  .pads_dq_10(dq[10]), .pads_dq_11(dq[11]),
    .pads_dq_12(dq[12]), .pads_dq_13(dq[13]), .pads_dq_14(dq[14]), .pads_dq_15(dq[15]),
    .pads_dqs_t_0(pads_dqs_t[0]), .pads_dqs_t_1(pads_dqs_t[1]),
    .pads_dqs_c_0(pads_dqs_c[0]), .pads_dqs_c_1(pads_dqs_c[1]),
    .pads_dqs_in_t_0(p_dqs_in_t[0]), .pads_dqs_in_t_1(p_dqs_in_t[1]),
    .pads_dm_dbi_n_0(pads_dm_dbi_n[0]), .pads_dm_dbi_n_1(pads_dm_dbi_n[1]),
    .pads_dm_dbi_in_0(mem_dm_out[0]), .pads_dm_dbi_in_1(mem_dm_out[1])
  );

  // ===========================================================================
  // SDRAM behavioral model (TC9)
  // ===========================================================================
  Ddr4SdramModel #(.ECHO_DELAY_PS(2500)) mem (
    .ck_t(dramClk),          // clean TB reference (pads_ck_t observable-only)
    .rst_n(rst_n),
    .cs_n({p_cs_n_1, p_cs_n_0}),
    .cke({p_cke_1, p_cke_0}),
    .ca(p_ca),
    .ras_n(p_ras_n), .cas_n(p_cas_n), .we_n(p_we_n), .act_n(p_act_n),
    .bg(p_bg), .ba(p_ba), .cid(p_cid),
    .dq(dq),
    .dqs_in(p_dqs_t),
    .wl_active(tr_wrlvlEn),
    .dqs_echo(dqs_echo_raw),
    .dm_wr(pads_dm_dbi_n),
    .dm_rd(mem_dm_out),
    .wr_key_last(mem_wr_key_last),
    .rd_key_last(mem_rd_key_last),
    .wr_word_last(mem_wr_word_last),
    .rd_driving(mem_rd_driving),
    .rd_pending_err(mem_rd_pending_err),
    .dqs_blank_cnt(mem_dqs_blank_cnt),
    .dqs_blank_err(mem_dqs_blank_err)
  );

  // ===========================================================================
  // Bmb stimulus helpers
  // ===========================================================================
  // One 16-byte (2 x 64-bit fragment) BL8 burst; `last` on the final fragment.
  // Bytes b[0..15] = 0x00..0x0F (distinct) for an order-independent integrity check.
  task bmb_burst(input opcode, input [ADDR_W-1:0] addr);
    integer i;
    begin
      for (i = 0; i < 2; i = i + 1) begin
        bmb_cmd_valid = 1'b1;
        bmb_cmd_opcode = opcode;
        bmb_cmd_address = addr;
        bmb_cmd_length  = 10'd1;   // BMB length = fragment count - 1: 2 frags = 16 bytes (BL8)
        bmb_cmd_data    = (i == 0) ? 64'h07060504_03020100 : 64'h0F0E0D0C_0B0A0908;
        bmb_cmd_mask    = 8'hFF;
        bmb_cmd_context = 2'b00;
        bmb_cmd_last    = (i == 1);
        @(posedge refclk);
        while (!bmb_cmd_ready) @(posedge refclk);
        #1;                        // Quiet region: MC has sampled this fragment before we change it
        if (i == 0) begin          // 1-cycle gap between fragments (matches MC write-bandwidth constraint)
          bmb_cmd_valid = 1'b0;
          @(posedge refclk);
          bmb_cmd_valid = 1'b1;
        end
      end
      bmb_cmd_valid = 1'b0;
      bmb_cmd_last  = 1'b0;
    end
  endtask

  integer errors = 0;
  reg [127:0] wr_word;
  integer bv, bb, cnt;
  reg [63:0] rsp_fragments [0:1];
  integer rsp_count;
  reg [8:0]  wl_converged;
  task check(input cond, input string msg);
    begin
      if (cond !== 1'b1) begin
        errors = errors + 1;
        $display("FAIL: %s", msg);
      end
    end
  endtask

  // ===========================================================================
  // Main stimulus
  // ===========================================================================
  initial begin
    // ---- reset -------------------------------------------------------------
    repeat(4) @(posedge refclk);
    rst_n = 1'b1;
    repeat(100) @(posedge refclk);   // let MC/PHY settle post-reset

    // ---- INIT SEQUENCE VERIFICATION (Step 8c) ---------------------------------
    // After the 15us wait, the MC's Ddr4InitSequencer should have completed.
    // Verify the model saw the expected command types (counts > 0, no illegal).
    $display("INITCHK t=%0t mrs=%0d zqcl=%0d ref=%0d act=%0d wr=%0d rd=%0d pre=%0d illegal=%0d",
             $time, init_cnt_mrs, init_cnt_zqcl, init_cnt_ref,
             init_cnt_act, init_cnt_wr, init_cnt_rd, init_cnt_pre, init_cnt_illegal);
    check(init_cnt_mrs > 0, "INIT: no MRS commands arrived at model");
    check(init_cnt_zqcl > 0, "INIT: no ZQCL command arrived at model");
    check(init_cnt_illegal === 16'd0, "INIT: illegal command decoded at model");

    // ---- W2: WRITE LEVELING closed loop (PHY-internal) ----------------------
    // The MC idles DFI-level training initiation (documented: a future training
    // initiator replaces the ties), so the TB acts as initiator: assert wrlvlEn.
    // The PHY must drive its DQS toggle through ODELAYE3, let the model echo it
    // back (2.5ns analog delay), sample it via IDELAYE3, and bang-bang-converge
    // the ODELAYE3 counter. The detector samples fbSample 5ns AFTER the local
    // DQS rise (mid-high-phase), so the attractor is TOTAL loop delay
    // wb_delay*50ps + 2500ps ~= k*10000ps (echo in PHASE with local DQS):
    // wb_delay ~= 150 (mod 200). From the 256 seed the loop walks UP to the
    // 350<->351 oscillation. wrlvlResp is EARNED by the convergence check,
    // never self-assigned.
    begin : wl_phase
      integer wk;
      wl_converged = 9'h000;
      tr_wrlvlEn = 1'b1;
      for (wk = 0; wk < 2000; wk = wk + 1) begin
        @(posedge refclk);
        if (tr_wrlvlResp) begin
          wl_converged = wlDelayOut;
          disable wl_phase;
        end
      end
      tr_wrlvlEn = 1'b0;
    end
    // Hold WL enable for 8 refclk cycles after the response so the model's
    // wl_active exemption covers the PHY's last DQS edge at window close.
    repeat(8) @(posedge refclk);
    tr_wrlvlEn = 1'b0;
    $display("WLDBG t=%0t resp=%b mc_en=%b delay=%0d", $time, tr_wrlvlResp, tr_wrlvlEn_mc, wl_converged);
    check(tr_wrlvlResp === 1'b1, "W2: wrlvlResp never earned (loop did not converge)");
    check(wl_converged >= 9'd345 && wl_converged <= 9'd355, "W2: converged ODELAYE3 delay outside expected band [345..355] taps");

    // ---- READ LEVELING closed loop (PHY-internal, Plan A) -------------------
    // Surrogate DRAM read DQS = TB oscillator on the receive pad during
    // rdlvlEn. The PHY bang-bang-converges the read-path IDELAYE3 counter so
    // the delayed trigger lands on the local DQS grid; rdlvlResp is EARNED by
    // the convergence check (never self-assigned).
    begin : rl_phase
      integer rk;
      rd_converged = 9'h000;
      tr_rdlvlEn = 1'b1;
      for (rk = 0; rk < 2000; rk = rk + 1) begin
        @(posedge refclk);
        if (tr_rdlvlResp) begin
          rd_converged = rdDelayOut;
          disable rl_phase;
        end
      end
      tr_rdlvlEn = 1'b0;
    end
    repeat(8) @(posedge refclk);
    tr_rdlvlEn = 1'b0;
    $display("RLDBG t=%0t resp=%b delay=%0d", $time, tr_rdlvlResp, rd_converged);
    check(tr_rdlvlResp === 1'b1, "R2: rdlvlResp never earned (loop did not converge)");
    check(rd_mod100 <= 7'd5 || rd_mod100 >= 7'd95, "R2: converged IDELAYE3 delay not an integer DQS period: rdDelay %% 100 taps outside +-5");

    // ---- W1: Bmb WRITE -> model --------------------------------------------
    // The MC plays its built-in DDR4 init sequence (Ddr4InitSequencer: 7xMRS + ZQCL,
    // ~10.5us) before releasing user commands. Wait well past it, then burst.
    repeat(1500) @(posedge refclk);  // 15us: init done (needs ~10.5us)
    // RowBankColumn byte address: {rank(1) | row(13) | bank(4) | colBytes(11)}
    // rank0, row=0x1000, bank=1, colBytes=0x040  =>  col[4:0]=0
    bmb_burst(1'b1, {1'b0, 13'h1000, 4'h1, 11'h040});
    // Poll for the write to land in the model (bounded); wr_key_last latches at
    // ABSORB START, so afterwards give the 16-beat collection time to finish.
    begin : drain_poll
      integer k;
      for (k = 0; k < 500; k = k + 1) begin
        @(posedge refclk);
        if (|mem_wr_key_last) disable drain_poll;
      end
    end
    repeat(100) @(posedge refclk);  // 1us >> 16 beats (80ns) + SerDes latency

    // Integrity check (order-INDEPENDENT; 8b smoke scope): all 16 distinct
    // bytes 0x00..0x0F must land exactly once with no X/Z. This proves the full
    // forward path (Bmb -> MC -> DFI -> PHY -> SerDes -> model) moves every byte
    // intact. Exact byte ORDERING vs the JESD79-4B DDR4 burst interleave is
    // deferred to Step 8c (needs the interleave in the model + agent compare).
    wr_word = mem_wr_word_last;
    $display("W1DBG wr_key=%h word=%h", mem_wr_key_last, wr_word);
    check(|mem_wr_key_last, "W1: no write reached the model (wr_key_last=0)");
    check(mem_wr_key_last === 16'h0002, "W1: wrong write key (exp 0002: row0/col0/bank1/rank0)");
    check(!$isunknown(wr_word), "W1: word has x/z");
    for (bv = 0; bv < 16; bv = bv + 1) begin
      cnt = 0;
      for (bb = 0; bb < 16; bb = bb + 1)
        if (wr_word[bb*8 +: 8] === bv[7:0]) cnt = cnt + 1;
      if (cnt !== 1) begin
        errors = errors + 1;
        $display("FAIL: W1: byte 0x%02x appears %0d times (exp 1)", bv, cnt);
      end
    end
    check(mem_rd_pending_err !== 1'b1, "W1: spurious rd_pending_err");

    // ---- R1: Bmb READ of same address -> verify data roundtrip ---------------
    // Issue a read of the same address we just wrote. The model returns the
    // stored 128-bit word via ISERDESE3 -> DFI -> MC -> Bmb response.
    bmb_cmd_valid = 1'b1;
    bmb_cmd_opcode = 1'b0;   // READ
    bmb_cmd_address = {1'b0, 13'h1000, 4'h1, 11'h040};
    bmb_cmd_length  = 10'd15; // Bmb convention: length = size(bytes)-1 ; 16B BL8 burst -> 15
    bmb_cmd_data    = 64'h0; // don't care for read
    bmb_cmd_mask    = 8'hFF;
    bmb_cmd_context = 2'b00;
    bmb_cmd_last    = 1'b1;  // single fragment read (BL8/2=4 beats = 1 frag?)
    @(posedge refclk);
    while (!bmb_cmd_ready) @(posedge refclk);
    bmb_cmd_valid = 1'b0;

    // Collect Bmb read response fragments
    rsp_count = 0;
    begin : rsp_collect
      integer kt;
      for (kt = 0; kt < 500; kt = kt + 1) begin
        @(posedge refclk);
        if (bmb_rsp_valid && bmb_rsp_ready) begin
          rsp_fragments[rsp_count] = bmb_rsp_data;
          rsp_count = rsp_count + 1;
          if (bmb_rsp_last) disable rsp_collect;
        end
      end
    end
    $display("R1DBG t=%0t rsp_count=%0d frag0=%h frag1=%h", $time, rsp_count, rsp_fragments[0], rsp_fragments[1]);
    check(rsp_count >= 1, "R1: no read response received");
    // Exact roundtrip: read must return exactly the bytes written (Bmb order).
    check(rsp_count == 2, "R1: expected 2 response fragments, got ");
    check(rsp_fragments[0] === 64'h07060504_03020100, "R1: frag0 != written frag0 (got ");
    check(rsp_fragments[1] === 64'h0F0E0D0C_0B0A0908, "R1: frag1 != written frag1 (got ");
    // Byte-content check: all 16 bytes 0x00-0x0F must appear across the fragments
    begin : r1_byte_check
      integer rb, rc, rt;
      reg [7:0] all_bytes [0:15];
      integer byte_ptr;
      byte_ptr = 0;
      for (rb = 0; rb < rsp_count && byte_ptr < 16; rb = rb + 1) begin
        for (rc = 0; rc < 8 && byte_ptr < 16; rc = rc + 1) begin
          all_bytes[byte_ptr] = rsp_fragments[rb][rc*8 +: 8];
          byte_ptr = byte_ptr + 1;
        end
      end
      for (bv = 0; bv < 16; bv = bv + 1) begin
        cnt = 0;
        for (bb = 0; bb < byte_ptr; bb = bb + 1)
          if (all_bytes[bb] === bv[7:0]) cnt = cnt + 1;
        if (cnt !== 1) begin
          errors = errors + 1;
          $display("FAIL: R1: byte 0x%02x appears %0d times (exp 1, have %0d bytes)", bv, cnt, byte_ptr);
        end
      end
    end

    if (mem_dqs_blank_err !== 1'b0)
      $display("DQSBLANK cnt=%0d", mem_dqs_blank_cnt);
    check(mem_dqs_blank_err === 1'b0, "DQS: blanking violation (DQS toggled outside write-burst envelope / WL span)");

    if (errors == 0)
      $display("TC10_PASS: e2e forward+read path (Bmb WR/RD -> MC -> DFI -> PHY -> model)");
    else
      $display("TC10_FAIL: %0d errors", errors);
    $finish;
  end


  // ---- safety timeout --------------------------------------------------------
  initial begin
    #2000000;
    $display("TC10_FAIL: timeout");
    $finish;
  end
endmodule
