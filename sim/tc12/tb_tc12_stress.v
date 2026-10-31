// ============================================================================
// TC12: randomized end-to-end stress, x32 variant — Bmb -> MC(DfiController)
//       -> PHY(UsPhy) -> Ddr4SdramModel(DATAW=32), with Ddr4PinModel legality
//       monitoring. Same traffic contract as TC11 (ratified 2026-10-31),
//       scaled to a 32-bit DQ interface:
//
//   * BMB word = 128 bits = 16 bytes; 1 DRAM burst (BL8 x32) = 32 bytes =
//     2 BMB words. Write: 2 fragments, length=1; read: length=31 (bytes-1);
//     read response: 2 x 128-bit fragments.
//   * Address pack {rank(1)|row(13)|bank(4)|colBytes(12)}; row in [0,63],
//     colBytes in {0,32,64,96} (32B-aligned = one full burst per cell), so
//     every TB address maps bijectively onto a model cell. 5%/5% boundary
//     bias to 0/max. (Model cell = one 32B word keyed on physical
//     column[4:0]; unaligned/multi-cell traffic is out of scope, cf. TC11.)
//   * Content-addressed payload: byte[i] = key[7:0] ^ key[15:8] ^ i.
//   * 512 WRITE + 512 READ bursts, LCG(SEED) order, 64-entry recent-ring for
//     reads, ~10% idle gaps.
//   * Directed phase: 8 overwrite pairs (salt A5 -> 5A) + 16 corner cells
//     (salt C3), exact compares.
//   * TC10 preamble: init passthrough (INITCHK) + WL closed loop [345..355].
//   * Pin legality (cumulative): illegal == 0, wr/rd >= 512, ref > 0.
//   * DQS blanking observer (model-side, JESD79-4B tWPRE/tWPST/tDQSS).
//
// Known limits: model key truncates row/col (wrap); mid-burst reset undefined.
// ============================================================================
module tb_tc12_stress;
  parameter SEED = 42;

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
  localparam ADDR_W = 30;   // {rank(1)|row(13)|bank(4)|colBytes(12)}
  reg              bmb_cmd_valid = 0;
  wire             bmb_cmd_ready;
  reg              bmb_cmd_last  = 0;
  reg              bmb_cmd_opcode = 0;   // 0 = READ, 1 = WRITE
  reg [ADDR_W-1:0] bmb_cmd_address = 0;
  reg [9:0]        bmb_cmd_length  = 0;
  reg [127:0]      bmb_cmd_data    = 0;
  reg [15:0]       bmb_cmd_mask    = 16'hFFFF;
  reg [1:0]        bmb_cmd_context = 0;

  wire             bmb_rsp_valid;
  reg              bmb_rsp_ready = 1;
  wire             bmb_rsp_last;
  wire [0:0]       bmb_rsp_opcode;
  wire [127:0]     bmb_rsp_data;
  wire [1:0]       bmb_rsp_context;

  // ---- DFI nets (MC <-> PHY, direct) ---------------------------------------
  wire [25:0] dfi_control_address;
  wire [7:0]  dfi_control_bank;
  wire [1:0]  dfi_control_rasN, dfi_control_casN, dfi_control_weN;
  wire [3:0]  dfi_control_csN, dfi_control_cke, dfi_control_odt, dfi_control_resetN;
  wire [1:0]  dfi_control_actN, dfi_control_bg, dfi_control_cid;
  wire        dfi_wr_0_en, dfi_wr_1_en;
  wire [63:0] dfi_wr_0_data, dfi_wr_1_data;
  wire [7:0]  dfi_wr_0_mask, dfi_wr_1_mask;
  wire [1:0]  dfi_wr_0_csN, dfi_wr_1_csN;
  wire        dfi_rden_0, dfi_rden_1;
  wire [1:0]  dfi_rdCs_0_csN, dfi_rdCs_1_csN;
  wire        dfi_rd_0_valid, dfi_rd_1_valid;
  wire [63:0] dfi_rd_0_data, dfi_rd_1_data;
  wire [7:0]  dfi_rd_0_dbIN, dfi_rd_1_dbIN;
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
  wire [31:0] dq;
  wire        p_ck_t, p_ck_c, p_cke_0, p_cke_1, p_cs_n_0, p_cs_n_1;
  wire [12:0] p_ca;
  wire [12:0] pads_ca;
  wire        p_ras_n, p_cas_n, p_we_n, p_act_n, p_bg, p_cid;
  wire [2:0]  p_ba;
  wire [2:0]  pads_ba;
  wire        p_odt_0, p_odt_1, p_reset_n, p_par;
  wire [3:0]  p_dqs_t, p_dqs_c, p_dm_dbi_n;
  wire [3:0]  dqs_echo_raw;   // model output (returned DQS)
  wire [3:0]  mem_dm_out;     // model DM during reads -> PHY dm_dbi_in
  wire [3:0]  p_dqs_in_t;     // cut-muxed feed to the PHY dqs_in_t pads
  wire [3:0]  pads_dqs_t, pads_dqs_c, pads_dm_dbi_n;
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
  // Negative-proof knob (mutant run): flip to 1 in a COPIED tb to sever the
  // DQS feedback path; the PHY must then fail to converge. Canonical = 0.
  parameter WL_ECHO_CUT_P = 0;
  parameter RCUT_P = 0;
  assign p_dqs_in_t = RCUT_P ? 4'b0000 : (tr_rdlvlEn ? {tb_osr,tb_osr,tb_osr,tb_osr} : (WL_ECHO_CUT_P ? 4'b0000 : dqs_echo_raw));
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
    .pads_dq_16(dq[16]), .pads_dq_17(dq[17]), .pads_dq_18(dq[18]), .pads_dq_19(dq[19]),
    .pads_dq_20(dq[20]), .pads_dq_21(dq[21]), .pads_dq_22(dq[22]), .pads_dq_23(dq[23]),
    .pads_dq_24(dq[24]), .pads_dq_25(dq[25]), .pads_dq_26(dq[26]), .pads_dq_27(dq[27]),
    .pads_dq_28(dq[28]), .pads_dq_29(dq[29]), .pads_dq_30(dq[30]), .pads_dq_31(dq[31]),
    .pads_dqs_t_0(pads_dqs_t[0]), .pads_dqs_t_1(pads_dqs_t[1]),
    .pads_dqs_t_2(pads_dqs_t[2]), .pads_dqs_t_3(pads_dqs_t[3]),
    .pads_dqs_c_0(pads_dqs_c[0]), .pads_dqs_c_1(pads_dqs_c[1]),
    .pads_dqs_c_2(pads_dqs_c[2]), .pads_dqs_c_3(pads_dqs_c[3]),
    .pads_dqs_in_t_0(p_dqs_in_t[0]), .pads_dqs_in_t_1(p_dqs_in_t[1]),
    .pads_dqs_in_t_2(p_dqs_in_t[2]), .pads_dqs_in_t_3(p_dqs_in_t[3]),
    .pads_dm_dbi_n_0(pads_dm_dbi_n[0]), .pads_dm_dbi_n_1(pads_dm_dbi_n[1]),
    .pads_dm_dbi_n_2(pads_dm_dbi_n[2]), .pads_dm_dbi_n_3(pads_dm_dbi_n[3]),
    .pads_dm_dbi_in_0(mem_dm_out[0]), .pads_dm_dbi_in_1(mem_dm_out[1]),
    .pads_dm_dbi_in_2(mem_dm_out[2]), .pads_dm_dbi_in_3(mem_dm_out[3])
  );

  // ===========================================================================
  // SDRAM behavioral model (x32)
  // ===========================================================================
  Ddr4SdramModel #(.ECHO_DELAY_PS(2500), .DATAW(32)) mem (
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
  integer errors = 0;
  task check(input cond, input string msg);
    begin
      if (cond !== 1'b1) begin
        errors = errors + 1;
        $display("FAIL: %s", msg);
      end
    end
  endtask

  // ===========================================================================
  // Stress machinery: RNG, model-key mirroring, payload, ring, tasks
  // ===========================================================================
  reg  [31:0] rng;
  reg  [29:0] ring [0:63];
  integer     ring_count = 0;
  integer     tb_wr_acc = 0, tb_rd_acc = 0;

  task rnd32(output [31:0] v);
    begin
      v   = rng;
      rng = rng * 32'd1664525 + 32'd1013904223;
    end
  endtask

  // Mirror of Ddr4SdramModel.v storage key: {row[5:0], col[4:0], bank[3:0], rank}
  // a = {rank(1)=a[29], row(13)=a[28:16], bank(4)=a[15:12], colBytes(12)=a[11:0]}
  // physical col[4:0] = colBytes[6:2] = a[6:2]
  function [15:0] mk_key(input [ADDR_W-1:0] a);
    mk_key = {a[21:16], a[6:2], a[15:12], a[29]};
  endfunction

  function [7:0] paybyte(input [15:0] k, input [7:0] i);
    paybyte = k[7:0] ^ k[15:8] ^ i;
  endfunction

  // One 128-bit BMB fragment = 16 payload bytes starting at index `base`.
  function [127:0] payfrag(input [15:0] k, input [4:0] base);
    integer kk;
    begin
      for (kk = 0; kk < 16; kk = kk + 1)
        payfrag[kk*8 +: 8] = paybyte(k, base + kk[4:0]);
    end
  endfunction

  // Random address inside the model key space (bijective to a model cell).
  task gen_addr(output [ADDR_W-1:0] a);
    integer rr, cc, rk;
    reg [12:0] rw;
    reg [11:0] cw;
    begin
      rnd32(rr); rnd32(cc); rnd32(rk);
      if (rr[7:0] < 8'd5)       rw = 13'd0;      // boundary bias: row 0
      else if (rr[7:0] < 8'd10) rw = 13'd63;     // boundary bias: row max
      else                      rw = rr[12:0] % 13'd64;
      // 32B-aligned only: one full BL8 x32 burst per model cell (see header).
      if (cc[7:0] < 8'd5)       cw = 12'd0;      // boundary bias: colBytes 0
      else if (cc[7:0] < 8'd10) cw = 12'd96;     // boundary bias: colBytes max
      else                      cw = cc[1:0] * 12'd32;  // {0,32,64,96}
      a = {rk[0], rw, cc[3:0], cw};
    end
  endtask

  // 32-byte content-addressed write burst (2 x 128-bit fragments = 1 burst).
  task bmb_wr_burst(input [ADDR_W-1:0] addr);
    integer i;
    begin
      for (i = 0; i < 2; i = i + 1) begin
        bmb_cmd_valid   = 1'b1;
        bmb_cmd_opcode  = 1'b1;
        bmb_cmd_address = addr;
        bmb_cmd_length  = 10'd1;   // words-1: 2 words (32B) = one BL8 x32 burst
        bmb_cmd_data    = (i == 0) ? payfrag(mk_key(addr), 5'd0)
                                   : payfrag(mk_key(addr), 5'd16);
        bmb_cmd_mask    = 16'hFFFF;
        bmb_cmd_context = 2'b00;
        bmb_cmd_last    = (i == 1);
        @(posedge refclk);
        while (!bmb_cmd_ready) @(posedge refclk);
        if (i == 1) tb_wr_acc = tb_wr_acc + 1;
        #1;
        if (i == 0) begin
          bmb_cmd_valid = 1'b0;
          @(posedge refclk);
          bmb_cmd_valid = 1'b1;
        end
      end
      bmb_cmd_valid = 1'b0;
      bmb_cmd_last  = 1'b0;
    end
  endtask

  // 32-byte read command (single fragment, length = bytes-1 = 31).
  task bmb_rd_burst(input [ADDR_W-1:0] addr, input [1:0] ctx);
    begin
      bmb_cmd_valid   = 1'b1;
      bmb_cmd_opcode  = 1'b0;
      bmb_cmd_address = addr;
      bmb_cmd_length  = 10'd31;
      bmb_cmd_data    = 128'h0;
      bmb_cmd_mask    = 16'hFFFF;
      bmb_cmd_context = ctx;
      bmb_cmd_last    = 1'b1;
      @(posedge refclk);
      while (!bmb_cmd_ready) @(posedge refclk);
      tb_rd_acc = tb_rd_acc + 1;
      #1;
      bmb_cmd_valid = 1'b0;
      bmb_cmd_last  = 1'b0;
    end
  endtask

  // Collect up to 2 response fragments (bounded); record seen context.
  task collect_rsp(output [127:0] f0, output [127:0] f1,
                   output integer n, output [1:0] ctx_seen);
    integer kt;
    begin
      n        = 0;
      f0       = 128'h0;
      f1       = 128'h0;
      ctx_seen = 2'bxx;
      for (kt = 0; kt < 2000; kt = kt + 1) begin
        @(posedge refclk);
        if (bmb_rsp_valid && bmb_rsp_ready && bmb_rsp_context === 2'b01) begin
          // only our read's fragments (ctx=01); write completions (ctx=00)
          // are consumed and skipped
          if (n == 0) begin f0 = bmb_rsp_data; ctx_seen = bmb_rsp_context; end
          if (n == 1) f1 = bmb_rsp_data;
          n = n + 1;
          if (bmb_rsp_last) break;
        end
      end
    end
  endtask

  // ---- salted payload variants (directed phase) ------------------------------
  function [7:0] paybyte_s(input [15:0] k, input [7:0] i, input [7:0] salt);
    paybyte_s = k[7:0] ^ k[15:8] ^ i ^ salt;
  endfunction

  function [127:0] payfrag_s(input [15:0] k, input [4:0] base, input [7:0] salt);
    integer kk;
    begin
      for (kk = 0; kk < 16; kk = kk + 1)
        payfrag_s[kk*8 +: 8] = paybyte_s(k, base + kk[4:0], salt);
    end
  endfunction

  task bmb_wr_burst_s(input [ADDR_W-1:0] addr, input [7:0] salt);
    integer i;
    begin
      for (i = 0; i < 2; i = i + 1) begin
        bmb_cmd_valid   = 1'b1;
        bmb_cmd_opcode  = 1'b1;
        bmb_cmd_address = addr;
        bmb_cmd_length  = 10'd1;
        bmb_cmd_data    = (i == 0) ? payfrag_s(mk_key(addr), 5'd0, salt)
                                   : payfrag_s(mk_key(addr), 5'd16, salt);
        bmb_cmd_mask    = 16'hFFFF;
        bmb_cmd_context = 2'b00;
        bmb_cmd_last    = (i == 1);
        @(posedge refclk);
        while (!bmb_cmd_ready) @(posedge refclk);
        if (i == 1) tb_wr_acc = tb_wr_acc + 1;
        #1;
        if (i == 0) begin
          bmb_cmd_valid = 1'b0;
          @(posedge refclk);
          bmb_cmd_valid = 1'b1;
        end
      end
      bmb_cmd_valid = 1'b0;
      bmb_cmd_last  = 1'b0;
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

    // ---- INIT SEQUENCE VERIFICATION -----------------------------------------
    $display("INITCHK t=%0t mrs=%0d zqcl=%0d ref=%0d act=%0d wr=%0d rd=%0d pre=%0d illegal=%0d",
             $time, init_cnt_mrs, init_cnt_zqcl, init_cnt_ref,
             init_cnt_act, init_cnt_wr, init_cnt_rd, init_cnt_pre, init_cnt_illegal);
    check(init_cnt_mrs > 0, "INIT: no MRS commands arrived at model");
    check(init_cnt_zqcl > 0, "INIT: no ZQCL command arrived at model");
    check(init_cnt_illegal === 16'd0, "INIT: illegal command decoded at model");

    // ---- WRITE LEVELING closed loop (PHY-internal) --------------------------
    begin : wl_phase
      integer wk;
      reg [8:0] wl_converged;
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
    repeat(8) @(posedge refclk);
    tr_wrlvlEn = 1'b0;
    $display("WLDBG t=%0t resp=%b mc_en=%b delay=%0d", $time, tr_wrlvlResp, tr_wrlvlEn_mc, wlDelayOut);
    check(tr_wrlvlResp === 1'b1, "W2: wrlvlResp never earned (loop did not converge)");
    check(wlDelayOut >= 9'd345 && wlDelayOut <= 9'd355, "W2: converged ODELAYE3 delay outside expected band [345..355] taps");

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

    // ---- STRESS: 512 WR + 512 RD, random bank/row/col/rank -------------------
    repeat(1500) @(posedge refclk);  // 15us: init done
    $display("TC12_SEED %0d", SEED);
    rng = SEED;

    // ---- DIRECTED: overwrite pairs + corner matrix ---------------------------
    begin : directed
      integer di, cj;
      reg [29:0] da;
      reg [12:0] drow;
      reg [11:0] dcol;
      reg [3:0]  dbank;
      reg        drank;
      reg [127:0] df0, df1;
      integer    dn;
      reg [1:0]  dctx;
      for (di = 0; di < 8; di = di + 1) begin
        da = {di[0], 13'(di), 4'(di * 2), 12'd0};
        bmb_wr_burst_s(da, 8'hA5);
        bmb_rd_burst(da, 2'b01);
        collect_rsp(df0, df1, dn, dctx);
        $display("DIROVW t=%0t ovw=%0d addr=%08h salt=A5 rn=%0d f0=%032h ex0=%032h",
                 $time, di, da, dn, df0, payfrag_s(mk_key(da), 5'd0, 8'hA5));
        check(dn == 2, "DIR-OVW: expected 2 response fragments (salt A)");
        check(df0 === payfrag_s(mk_key(da), 5'd0, 8'hA5), "DIR-OVW: frag0 salt-A mismatch");
        check(df1 === payfrag_s(mk_key(da), 5'd16, 8'hA5), "DIR-OVW: frag1 salt-A mismatch");
        bmb_wr_burst_s(da, 8'h5A);
        bmb_rd_burst(da, 2'b01);
        collect_rsp(df0, df1, dn, dctx);
        $display("DIROVW t=%0t ovw=%0d addr=%08h salt=5A rn=%0d f0=%032h ex0=%032h",
                 $time, di, da, dn, df0, payfrag_s(mk_key(da), 5'd0, 8'h5A));
        check(dn == 2, "DIR-OVW: expected 2 response fragments (salt B)");
        check(df0 === payfrag_s(mk_key(da), 5'd0, 8'h5A), "DIR-OVW: frag0 salt-B mismatch (overwrite failed?)");
        check(df1 === payfrag_s(mk_key(da), 5'd16, 8'h5A), "DIR-OVW: frag1 salt-B mismatch (overwrite failed?)");
      end
      for (cj = 0; cj < 16; cj = cj + 1) begin
        drank = cj[0];
        dbank = cj[1] ? 4'd15 : 4'd0;
        drow  = cj[2] ? 13'd63 : 13'd0;
        dcol  = cj[3] ? 12'd96 : 12'd0;
        da = {drank, drow, dbank, dcol};
        bmb_wr_burst_s(da, 8'hC3);
        bmb_rd_burst(da, 2'b01);
        collect_rsp(df0, df1, dn, dctx);
        $display("DIRCOR t=%0t cor=%0d addr=%08h key=%04h rn=%0d f0=%032h ex0=%032h",
                 $time, cj, da, mk_key(da), dn, df0, payfrag_s(mk_key(da), 5'd0, 8'hC3));
        check(dn == 2, "DIR-COR: expected 2 response fragments");
        check(df0 === payfrag_s(mk_key(da), 5'd0, 8'hC3), "DIR-COR: frag0 mismatch (corner cell)");
        check(df1 === payfrag_s(mk_key(da), 5'd16, 8'hC3), "DIR-COR: frag1 mismatch (corner cell)");
      end
    end

    begin : stress
      integer txn, wr_left, rd_left, g;
      reg op_wr;
      reg [29:0] cur_addr;
      reg [127:0] rf0, rf1;
      integer rn;
      reg [1:0] rsp_ctx;
      wr_left = 512; rd_left = 512;
      for (txn = 0; txn < 1024; txn = txn + 1) begin
        if (ring_count == 0) op_wr = 1'b1;
        else if (wr_left == 0) op_wr = 1'b0;
        else if (rd_left == 0) op_wr = 1'b1;
        else begin rnd32(g); op_wr = g[31]; end
        if (op_wr) begin
          gen_addr(cur_addr);
          bmb_wr_burst(cur_addr);
          ring[ring_count % 64] = cur_addr;
          if (ring_count < 64) ring_count = ring_count + 1;
          wr_left = wr_left - 1;
        end else begin
          rnd32(g);
          cur_addr = ring[g[5:0] % ring_count];
          bmb_rd_burst(cur_addr, 2'b01);
          collect_rsp(rf0, rf1, rn, rsp_ctx);
          $display("RSP t=%0t txn=%0d addr=%08h key=%04h rdk=%04h rn=%0d ctx=%b f0=%032h f1=%032h ex0=%032h ex1=%032h",
                   $time, txn, cur_addr, mk_key(cur_addr), mem_rd_key_last, rn, rsp_ctx,
                   rf0, rf1, payfrag(mk_key(cur_addr), 5'd0), payfrag(mk_key(cur_addr), 5'd16));
          check(rn == 2, "RD: expected 2 response fragments");
          check(rf0 === payfrag(mk_key(cur_addr), 5'd0), "RD: frag0 != f(key)");
          check(rf1 === payfrag(mk_key(cur_addr), 5'd16), "RD: frag1 != f(key)");
          check(rsp_ctx === 2'b01, "RD: context echo mismatch");
          rd_left = rd_left - 1;
        end
        rnd32(g);
        if (g[31:16] < 16'h1999)
          repeat (1 + g[15:13]) @(posedge refclk);
      end
    end

    // ---- PIN LEGALITY (cumulative counters) -----------------------------------
    $display("PINCHK t=%0t mrs=%0d zqcl=%0d ref=%0d act=%0d wr=%0d rd=%0d pre=%0d illegal=%0d bmb_wr=%0d bmb_rd=%0d",
             $time, init_cnt_mrs, init_cnt_zqcl, init_cnt_ref,
             init_cnt_act, init_cnt_wr, init_cnt_rd, init_cnt_pre, init_cnt_illegal,
             tb_wr_acc, tb_rd_acc);
    check(init_cnt_illegal === 16'd0, "PIN: illegal command decoded during stress");
    check(init_cnt_wr >= 512, "PIN: <512 write commands reached the model");
    check(init_cnt_rd >= 512, "PIN: <512 read commands reached the model");
    check(init_cnt_ref > 0, "PIN: no refresh issued during stress window");

    if (mem_dqs_blank_err !== 1'b0)
      $display("DQSBLANK cnt=%0d", mem_dqs_blank_cnt);
    check(mem_dqs_blank_err === 1'b0, "DQS: blanking violation (DQS toggled outside write-burst envelope / WL span)");

    if (errors == 0)
      $display("TC12_PASS: x32 e2e stress (512W+512R Bmb -> MC -> DFI -> PHY -> model), exact roundtrip + pin legality");
    else
      $display("TC12_FAIL: %0d errors", errors);
    $finish;
  end

  // ---- safety timeout --------------------------------------------------------
  initial begin
    #2000000;
    $display("TC12_FAIL: timeout");
    $finish;
  end
endmodule
