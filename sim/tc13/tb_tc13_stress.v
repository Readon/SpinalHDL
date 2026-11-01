// ============================================================================
// TC13: BMB full-path test — Bmb(sourceWidth=1) -> MC(DfiController) ->
//       PHY(UsPhy) -> Ddr4SdramModel, with Ddr4PinModel legality monitoring.
//
// Contract (ratified 2026-11-01; see .usphy_redesign_docs/TASK.md 8c⑫):
//   P1 MULTI-SOURCE: 2 BMB sources share ONE address space. 128 overlap
//      rounds: both sources issue writes, WAIT for both write responses,
//      then issue reads and collect them from the SINGLE merged rsp stream
//      (out-of-order across sources by construction). The rsp-ordered flow
//      is the BMB consumer contract: a read of a cell is issued only after
//      its write's response has been collected (RAW hazard ownership sits
//      with the requester, as in the 28/28 BmbMasterAgent discipline).
//      Per-txn exact readback (own pattern) — stronger than final-state
//      scoring; reservation (wr_busy) forbids cross-source concurrent
//      writers to one cell, so the readback expectation is well-defined.
//   P2 BACKPRESSURE: 64 more overlap rounds (same rsp-ordered flow);
//      every 8th round holds bmb_rsp_ready low for 100 refclk cycles while
//      cmds keep streaming. Checks: no deadlock (watchdog), no loss/dup
//      (per-class fragment conservation below).
//   P3 MASKED WRITES: 8 directed addresses; baseline full write (salt A),
//      then 6 mask classes of salt-B writes; per-byte expectation:
//      enabled bytes take B, disabled bytes keep their previous value
//      (model DM byte-enable semantics, USE_WRDBI=0).
//   Context scheme (2-bit ctx): write ctx = {src,0}, read ctx = {src,1}.
//      Class index = {rsp_source, rsp_ctx[0]}: 0=src0-W, 1=src0-R,
//      2=src1-W, 3=src1-R. Echo integrity: rsp_ctx[1] must equal rsp_source
//      (this is exactly what negative proof N2 severs).
//   Rsp fragment counts (Bmb2PreTaskPort contract): a 16B write burst
//      (2x64-bit fragments, length=1) yields 1 rsp fragment; a 16B read
//      (length=15) yields 2 rsp fragments. bytePerTaskMax=64 => no
//      per-chunk splitting. Fragment conservation:
//      cls0 = 128+64+56 = 248, cls1 = (128+64+56)*2 = 496,
//      cls2 = 128+64 = 192,  cls3 = (128+64)*2 = 384.
//   Payload: byte[i] = key[7:0]^key[15:8]^i[^salt], key mirrors the model.
//   Preamble: TC11-standard (INITCHK, write leveling, read leveling,
//      INITCHK2 management plane), then PINCHK + DQS blanking.
// Limits: 2-bit ctx bounds in-flight read concurrency to 2 (one per source);
// model zero-beat-skip heuristic (first DQ beat must be nonzero — payload
// construction makes this a non-event); unaligned reads out of scope (28/28).
// ============================================================================
module tb_tc13_stress;
  parameter SEED = 42;

  glbl glbl();

  // ---- clocks / reset ------------------------------------------------------
  reg refclk  = 0;
  reg dramClk = 0;
  always #5   refclk  = ~refclk;   // 100 MHz
  always #2.5 dramClk = ~dramClk;  // 200 MHz (= 2 x refclk, fr=2)
  reg tb_osr; initial tb_osr = 1'b0;
  always @(posedge dramClk) tb_osr = ~tb_osr;
  reg rst_n = 0;

  // ---- Bmb master (TB side, 2 sources) -------------------------------------
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
  reg              bmb_cmd_source  = 0;

  wire             bmb_rsp_valid;
  reg              bmb_rsp_ready = 1;
  wire             bmb_rsp_last;
  wire [0:0]       bmb_rsp_opcode;
  wire [63:0]      bmb_rsp_data;
  wire [1:0]       bmb_rsp_context;
  wire             bmb_rsp_source;

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
  wire        tr_rdlvlEn_mc;
  reg         tr_rdlvlEn = 1'b0;
  wire [1:0]  tr_phyRdlvlCsN, tr_phyRdlvlGateCsN;
  wire        tr_wrlvlReq, tr_wrlvlStrobe, tr_wrlvlResp;
  wire        tr_wrlvlEn_mc;
  reg         tr_wrlvlEn = 1'b0;
  wire [1:0]  tr_phyWrlvlCsN;
  wire [3:0]  tr_lvlPattern;
  wire        tr_lvlPeriodic;
  wire [1:0]  tr_phylvlReqCsN, tr_phylvlAckCsN;
  wire        dfi_status_initStart, dfi_status_initComplete;
  wire [1:0]  dfi_status_freqRatio, dfi_status_alertN, dfi_status_parityIn;
  wire [3:0]  dfi_status_dataByteDisable;
  wire [1:0]  dfi_status_dramClkDisable;
  reg         init_start_seen = 1'b0;
  always @(posedge refclk) if (dfi_status_initStart) init_start_seen = 1'b1;

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
  wire [1:0]  dqs_echo_raw;
  wire [1:0]  mem_dm_out;
  wire [1:0]  p_dqs_in_t;
  wire [1:0]  pads_dqs_t, pads_dqs_c, pads_dm_dbi_n;
  wire [8:0]  wlDelayOut;
  wire [8:0]  rdDelayOut;
  reg  [8:0]  rd_converged;
  wire [6:0]  rd_mod100 = rd_converged % 7'd100;

  assign p_ca      = pads_ca;
  assign p_ba      = pads_ba;
  assign p_dqs_t   = pads_dqs_t;
  assign p_dqs_c   = pads_dqs_c;
  assign p_dqs_in_t = tr_rdlvlEn ? {tb_osr,tb_osr} : dqs_echo_raw;
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
    .io_bmb_cmd_payload_fragment_source(bmb_cmd_source),
    .io_bmb_rsp_valid(bmb_rsp_valid),
    .io_bmb_rsp_ready(bmb_rsp_ready),
    .io_bmb_rsp_payload_last(bmb_rsp_last),
    .io_bmb_rsp_payload_fragment_opcode(bmb_rsp_opcode),
    .io_bmb_rsp_payload_fragment_data(bmb_rsp_data),
    .io_bmb_rsp_payload_fragment_context(bmb_rsp_context),
    .io_bmb_rsp_payload_fragment_source(bmb_rsp_source),
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
    .io_dfi_phyRequestedTraining_phylvlAckCsN(tr_phylvlAckCsN),
    .io_dfi_status_initStart(dfi_status_initStart),
    .io_dfi_status_initComplete(dfi_status_initComplete),
    .io_dfi_status_freqRatio(dfi_status_freqRatio),
    .io_dfi_status_alertN(dfi_status_alertN),
    .io_dfi_status_parityIn(dfi_status_parityIn),
    .io_dfi_status_dataByteDisable(dfi_status_dataByteDisable),
    .io_dfi_status_dramClkDisable(dfi_status_dramClkDisable)
  );

  // ===========================================================================
  // PHY
  // ===========================================================================
  UsPhy phy (
    .refclk(refclk), .dramClk(dramClk), .rst_n(rst_n),
    .clk(refclk), .reset(~rst_n),
    .dfi_control_address(dfi_control_address),
    .dfi_control_bank(dfi_control_bank),
    .dfi_control_rasN(dfi_control_rasN),
    .dfi_control_casN(dfi_control_casN),
    .dfi_control_weN(dfi_control_weN),
    .dfi_control_csN(dfi_control_csN),
    .dfi_control_cke(dfi_control_cke),
    .dfi_control_odt(dfi_control_odt),
    .dfi_control_resetN(dfi_control_resetN),
    .dfi_control_actN(dfi_control_actN),
    .dfi_control_bg(dfi_control_bg),
    .dfi_control_cid(dfi_control_cid),
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
    .pads_dm_dbi_in_0(mem_dm_out[0]), .pads_dm_dbi_in_1(mem_dm_out[1]),
    .dfi_status_initStart(dfi_status_initStart),
    .dfi_status_initComplete(dfi_status_initComplete),
    .dfi_status_freqRatio(dfi_status_freqRatio),
    .dfi_status_alertN(dfi_status_alertN),
    .dfi_status_parityIn(dfi_status_parityIn),
    .dfi_status_dataByteDisable(dfi_status_dataByteDisable),
    .dfi_status_dramClkDisable(dfi_status_dramClkDisable)
  );

  // ===========================================================================
  // SDRAM behavioral model (DM = write byte-enable, USE_WRDBI=0 default)
  // ===========================================================================
  Ddr4SdramModel #(.ECHO_DELAY_PS(2500)) mem (
    .ck_t(dramClk),
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
  // Helpers: RNG, key mirror, payloads
  // ===========================================================================
  integer errors = 0;
  integer rv;
  reg  [31:0] rng;
  reg  [8:0]  wl_converged;
  integer     tb_wr_acc = 0, tb_rd_acc = 0;

  task check(input cond, input string msg);
    begin
      if (cond !== 1'b1) begin
        errors = errors + 1;
        $display("FAIL: %s", msg);
      end
    end
  endtask

  task rnd32(output [31:0] v);
    begin
      v   = rng;
      rng = rng * 32'd1664525 + 32'd1013904223;
    end
  endtask

  // Mirror of Ddr4SdramModel.v storage key: {row[5:0], col[4:0], bank[3:0], rank}
  function [15:0] mk_key(input [ADDR_W-1:0] a);
    mk_key = {a[27:22], a[10:6], a[14:11], a[28]};
  endfunction

  function [7:0] paybyte(input [15:0] k, input [7:0] i);
    paybyte = k[7:0] ^ k[15:8] ^ i;
  endfunction

  function [63:0] payfrag(input [15:0] k, input [3:0] base);
    integer kk;
    begin
      for (kk = 0; kk < 8; kk = kk + 1)
        payfrag[kk*8 +: 8] = paybyte(k, base + kk[3:0]);
    end
  endfunction

  function [7:0] paybyte_s(input [15:0] k, input [7:0] i, input [7:0] salt);
    paybyte_s = k[7:0] ^ k[15:8] ^ i ^ salt;
  endfunction

  function [63:0] payfrag_s(input [15:0] k, input [3:0] base, input [7:0] salt);
    integer kk;
    begin
      for (kk = 0; kk < 8; kk = kk + 1)
        payfrag_s[kk*8 +: 8] = paybyte_s(k, base + kk[3:0], salt);
    end
  endfunction

  // Random address inside the model key space (bijective to a model cell).
  task gen_addr(output [ADDR_W-1:0] a);
    integer rr, cc, rk;
    reg [12:0] rw;
    reg [10:0] cw;
    begin
      rnd32(rr); rnd32(cc); rnd32(rk);
      if (rr[7:0] < 8'd5)       rw = 13'd0;
      else if (rr[7:0] < 8'd10) rw = 13'd63;
      else                      rw = rr[12:0] % 13'd64;
      if (cc[7:0] < 8'd5)       cw = 11'd0;
      else if (cc[7:0] < 8'd10) cw = 11'd48;
      else                      cw = cc[1:0] * 11'd16;  // {0,16,32,48}
      a = {rk[0], rw, cc[3:0], cw};
    end
  endtask

  // ---- cross-source cell reservation -----------------------------------------
  // Set while a pipeline owns a cell (from write issue until its readback is
  // collected); forbids the OTHER source from writing the same cell
  // concurrently, which keeps readback expectations well-defined.
  reg wr_busy [0:65535];
  integer ri0;
  initial for (ri0 = 0; ri0 < 65536; ri0 = ri0 + 1) wr_busy[ri0] = 1'b0;

  task gen_reserved_addr(output [ADDR_W-1:0] a);
    integer tries;
    begin
      for (tries = 0; tries < 64; tries = tries + 1) begin
        gen_addr(a);
        if (!wr_busy[mk_key(a)]) break;
      end
      if (wr_busy[mk_key(a)]) begin
        errors = errors + 1;
        $display("FAIL: RESERV: 64-way reservation collision (addr=%07h)", a);
      end
      wr_busy[mk_key(a)] = 1'b1;
    end
  endtask

  // ---- Bmb stimulus tasks (source-aware) --------------------------------------
  // 16-byte write burst (2 x 64-bit fragments); ctx = {src, 0}; mask applied
  // to BOTH fragments (byte i of each fragment gated by mask[i]).
  task bmb_wr_s(input src, input [ADDR_W-1:0] addr, input [7:0] salt, input [7:0] mask8);
    integer i;
    begin
      for (i = 0; i < 2; i = i + 1) begin
        bmb_cmd_valid   = 1'b1;
        bmb_cmd_opcode  = 1'b1;
        bmb_cmd_address = addr;
        bmb_cmd_length  = 10'd1;
        bmb_cmd_data    = (i == 0) ? payfrag_s(mk_key(addr), 4'd0, salt)
                                   : payfrag_s(mk_key(addr), 4'd8, salt);
        bmb_cmd_mask    = mask8;
        bmb_cmd_context = {src, 1'b0};
        bmb_cmd_source  = src;
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

  // 16-byte read command (single fragment); ctx = {src, 1}.
  task bmb_rd_i(input src, input [ADDR_W-1:0] addr);
    begin
      bmb_cmd_valid   = 1'b1;
      bmb_cmd_opcode  = 1'b0;
      bmb_cmd_address = addr;
      bmb_cmd_length  = 10'd15;
      bmb_cmd_data    = 64'h0;
      bmb_cmd_mask    = 8'hFF;
      bmb_cmd_context = {src, 1'b1};
      bmb_cmd_source  = src;
      bmb_cmd_last    = 1'b1;
      @(posedge refclk);
      while (!bmb_cmd_ready) @(posedge refclk);
      tb_rd_acc = tb_rd_acc + 1;
      #1;
      bmb_cmd_valid = 1'b0;
      bmb_cmd_last  = 1'b0;
    end
  endtask

  // ---- per-class rsp fragment accounting (loss/dup detection) -----------------
  // class = {rsp_source, rsp_ctx[0]}: 0=src0-W, 1=src0-R, 2=src1-W, 3=src1-R.
  // Counted CONSUMER-SIDE inside collect_cls: the main block is the sole rsp
  // consumer, so consumption counts are exact by construction (a separate
  // always-block observer undersamples sub-edge handshakes in xsim).
  reg [9:0] rsp_frag [0:3];
  integer rci;
  initial for (rci = 0; rci < 4; rci = rci + 1) rsp_frag[rci] = 10'd0;

  // Collect one transaction's rsp fragments from the MERGED rsp stream
//      (out-of-order across sources is legal; fragments of other classes
//      are consumed/skipped). cls: 0=src0-W(1), 1=src0-R(2), 2=src1-W(1),
//      3=src1-R(2). nwant = expected fragment count for that class.
  task collect_cls(input [1:0] cls, input integer nwant, output [63:0] f0, output [63:0] f1, output integer n);
    integer kt;
    begin
      n        = 0;
      f0       = 64'h0;
      f1       = 64'h0;
      for (kt = 0; kt < 3000; kt = kt + 1) begin
        @(posedge refclk);
        if (bmb_rsp_valid && bmb_rsp_ready &&
            bmb_rsp_source === cls[1] && bmb_rsp_context === {cls[1], cls[0]}) begin
          if (bmb_rsp_context[1] !== bmb_rsp_source) begin
            errors = errors + 1;
            $display("FAIL: RSPECHO t=%0t src=%b ctx=%b (ctx[1] != source)",
                     $time, bmb_rsp_source, bmb_rsp_context);
          end
          rsp_frag[{bmb_rsp_source, bmb_rsp_context[0]}] =
            rsp_frag[{bmb_rsp_source, bmb_rsp_context[0]}] + 10'd1;
          if (n == 0) f0 = bmb_rsp_data;
          if (n == 1) f1 = bmb_rsp_data;
          n = n + 1;
          if (n == nwant) break;
        end
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
    repeat(100) @(posedge refclk);

    // ---- INIT SEQUENCE VERIFICATION ------------------------------------------
    $display("INITCHK t=%0t mrs=%0d zqcl=%0d ref=%0d act=%0d wr=%0d rd=%0d pre=%0d illegal=%0d",
             $time, init_cnt_mrs, init_cnt_zqcl, init_cnt_ref,
             init_cnt_act, init_cnt_wr, init_cnt_rd, init_cnt_pre, init_cnt_illegal);
    check(init_cnt_mrs > 0, "INIT: no MRS commands arrived at model");
    check(init_cnt_zqcl > 0, "INIT: no ZQCL command arrived at model");
    check(init_cnt_illegal === 16'd0, "INIT: illegal command decoded at model");

    // ---- WRITE LEVELING closed loop -------------------------------------------
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
    repeat(8) @(posedge refclk);
    tr_wrlvlEn = 1'b0;
    $display("WLDBG t=%0t resp=%b mc_en=%b delay=%0d", $time, tr_wrlvlResp, tr_wrlvlEn_mc, wl_converged);
    check(tr_wrlvlResp === 1'b1, "WL: wrlvlResp never earned");
    check(wl_converged >= 9'd345 && wl_converged <= 9'd355, "WL: converged delay outside [345..355]");

    // ---- READ LEVELING closed loop --------------------------------------------
    begin : rl_phase
      integer rk2;
      rd_converged = 9'h000;
      tr_rdlvlEn = 1'b1;
      for (rk2 = 0; rk2 < 2000; rk2 = rk2 + 1) begin
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
    check(tr_rdlvlResp === 1'b1, "RL: rdlvlResp never earned");
    check(rd_mod100 <= 7'd5 || rd_mod100 >= 7'd95, "RL: rdDelay %% 100 outside +-5");

    // ---- let the MC's built-in init sequence finish ----------------------------
    repeat(1500) @(posedge refclk);
    $display("TC13_SEED %0d", SEED);
    rng = SEED;

    // ---- P1: MULTI-SOURCE, 128 overlap rounds ----------------------------------
    begin : p1
      integer round, g;
      reg [28:0] a0, a1;
      reg [63:0] f0a, f1a, f0b, f1b, wda, wdb, wdc, wdd;
      integer    na, nb, nw0, nw1;
      for (round = 0; round < 128; round = round + 1) begin
        gen_reserved_addr(a0);
        gen_reserved_addr(a1);
        bmb_wr_s(1'b0, a0, 8'h00, 8'hFF);
        bmb_wr_s(1'b1, a1, 8'h00, 8'hFF);
        // RAW ordering: wait for BOTH write responses before reading.
        collect_cls(2'b00, 1, wda, wdb, nw0);
        check(nw0 == 1, "P1: src0 write rsp missing");
        collect_cls(2'b10, 1, wdc, wdd, nw1);
        check(nw1 == 1, "P1: src1 write rsp missing");
        bmb_rd_i(1'b0, a0);
        bmb_rd_i(1'b1, a1);
        collect_cls(2'b01, 2, f0a, f1a, na);
        if (round < 4 || round % 32 == 0)
          $display("P1DBG t=%0t r=%0d a0=%07h a1=%07h na=%0d nb=%0d f0a=%016h ex=%016h",
                   $time, round, a0, a1, na, nb, f0a, payfrag(mk_key(a0), 4'd0));
        check(na == 2, "P1: src0 read returned != 2 fragments");
        check(f0a === payfrag(mk_key(a0), 4'd0), "P1: src0 frag0 mismatch");
        check(f1a === payfrag(mk_key(a0), 4'd8), "P1: src0 frag1 mismatch");
        collect_cls(2'b11, 2, f0b, f1b, nb);
        check(nb == 2, "P1: src1 read returned != 2 fragments");
        check(f0b === payfrag(mk_key(a1), 4'd0), "P1: src1 frag0 mismatch");
        check(f1b === payfrag(mk_key(a1), 4'd8), "P1: src1 frag1 mismatch");
        wr_busy[mk_key(a0)] = 1'b0;
        wr_busy[mk_key(a1)] = 1'b0;
        rnd32(g);
        if (g[31:16] < 16'h1999)
          repeat (1 + g[15:13]) @(posedge refclk);
      end
    end

    // ---- P2: BACKPRESSURE, 64 rounds, 8 throttle windows -----------------------
    begin : p2
      integer round, g;
      reg [28:0] a0, a1;
      reg [63:0] f0a, f1a, f0b, f1b, wda, wdb, wdc, wdd;
      integer    na, nb, nw0, nw1;
      for (round = 0; round < 64; round = round + 1) begin
        if (round % 8 == 4) begin
          // Throttle window: sink stalls for 100 refclk cycles while cmds
          // keep streaming (previous round's rsps accumulate in the MC).
          bmb_rsp_ready = 1'b0;
          repeat(100) @(posedge refclk);
          bmb_rsp_ready = 1'b1;
        end
        gen_reserved_addr(a0);
        gen_reserved_addr(a1);
        bmb_wr_s(1'b0, a0, 8'h00, 8'hFF);
        bmb_wr_s(1'b1, a1, 8'h00, 8'hFF);
        collect_cls(2'b00, 1, wda, wdb, nw0);
        check(nw0 == 1, "P2: src0 write rsp missing");
        collect_cls(2'b10, 1, wdc, wdd, nw1);
        check(nw1 == 1, "P2: src1 write rsp missing");
        bmb_rd_i(1'b0, a0);
        bmb_rd_i(1'b1, a1);
        collect_cls(2'b01, 2, f0a, f1a, na);
        check(na == 2, "P2: src0 read returned != 2 fragments");
        check(f0a === payfrag(mk_key(a0), 4'd0), "P2: src0 frag0 mismatch");
        check(f1a === payfrag(mk_key(a0), 4'd8), "P2: src0 frag1 mismatch");
        collect_cls(2'b11, 2, f0b, f1b, nb);
        check(nb == 2, "P2: src1 read returned != 2 fragments");
        check(f0b === payfrag(mk_key(a1), 4'd0), "P2: src1 frag0 mismatch");
        check(f1b === payfrag(mk_key(a1), 4'd8), "P2: src1 frag1 mismatch");
        wr_busy[mk_key(a0)] = 1'b0;
        wr_busy[mk_key(a1)] = 1'b0;
        rnd32(g);
        if (g[31:16] < 16'h1999)
          repeat (1 + g[15:13]) @(posedge refclk);
      end
      check(bmb_rsp_ready === 1'b1, "P2: rsp sink left stalled");
    end

    // ---- P3: MASKED WRITES, 8 addresses x 6 mask classes -----------------------
    begin : p3
      integer pa, k, mi;
      reg [28:0] da;
      reg [7:0]  mks [0:5];
      reg [63:0] f0, f1, wda, wdb;
      integer    n, nw;
      reg [127:0] exp16;
      mks[0] = 8'h0F;  // lower half of each fragment
      mks[1] = 8'hF0;  // upper half
      mks[2] = 8'hAA;  // alternating even bytes
      mks[3] = 8'h55;  // alternating odd bytes
      mks[4] = 8'h01;  // single byte (byte 0 of each fragment)
      mks[5] = 8'hFE;  // single byte DISABLED (byte 0 of each fragment)
      for (pa = 0; pa < 8; pa = pa + 1) begin
        da = {pa[0], 13'(17*pa + 3), 4'(2*pa), 11'(16*pa)};
        // baseline full write (salt A)
        bmb_wr_s(1'b0, da, 8'hA5, 8'hFF);
        collect_cls(2'b00, 1, wda, wdb, nw);
        check(nw == 1, "P3: baseline write rsp missing");
        for (mi = 0; mi < 16; mi = mi + 1)
          exp16[mi*8 +: 8] = paybyte_s(mk_key(da), mi[3:0], 8'hA5);
        bmb_rd_i(1'b0, da);
        collect_cls(2'b01, 2, f0, f1, n);
        check(n == 2, "P3: baseline read returned != 2 fragments");
        for (mi = 0; mi < 8; mi = mi + 1) begin
          check(f0[mi*8 +: 8] === exp16[mi*8 +: 8], "P3: baseline frag0 byte mismatch");
          check(f1[mi*8 +: 8] === exp16[(mi+8)*8 +: 8], "P3: baseline frag1 byte mismatch");
        end
        for (k = 0; k < 6; k = k + 1) begin
          bmb_wr_s(1'b0, da, 8'h5A, mks[k]);
          collect_cls(2'b00, 1, wda, wdb, nw);
          check(nw == 1, "P3: masked write rsp missing");
          for (mi = 0; mi < 16; mi = mi + 1)
            if (mks[k][mi[2:0]])
              exp16[mi*8 +: 8] = paybyte_s(mk_key(da), mi[3:0], 8'h5A);
          bmb_rd_i(1'b0, da);
          collect_cls(2'b01, 2, f0, f1, n);
          $display("P3DBG t=%0t pa=%0d k=%0d mask=%02h f0=%016h f1=%016h e0=%016h e1=%016h",
                   $time, pa, k, mks[k], f0, f1,
                   exp16[63:0], exp16[127:64]);
          check(n == 2, "P3: masked read returned != 2 fragments");
          for (mi = 0; mi < 8; mi = mi + 1) begin
            check(f0[mi*8 +: 8] === exp16[mi*8 +: 8], "P3: masked frag0 byte mismatch");
            check(f1[mi*8 +: 8] === exp16[(mi+8)*8 +: 8], "P3: masked frag1 byte mismatch");
          end
        end
      end
    end

    // ---- RESPONSE CONSERVATION (no loss / no dup) -------------------------------
    $display("RSCNT t=%0t cls0=%0d/%0d cls1=%0d/%0d cls2=%0d/%0d cls3=%0d/%0d",
             $time, rsp_frag[0], 248, rsp_frag[1], 496, rsp_frag[2], 192, rsp_frag[3], 384);
    check(rsp_frag[0] == 10'd248, "RSP: cls0 (src0 writes) fragment count != 248");
    check(rsp_frag[1] == 10'd496, "RSP: cls1 (src0 reads) fragment count != 496");
    check(rsp_frag[2] == 10'd192, "RSP: cls2 (src1 writes) fragment count != 192");
    check(rsp_frag[3] == 10'd384, "RSP: cls3 (src1 reads) fragment count != 384");

    // ---- PIN LEGALITY (cumulative counters) -------------------------------------
    $display("PINCHK t=%0t mrs=%0d zqcl=%0d ref=%0d act=%0d wr=%0d rd=%0d pre=%0d illegal=%0d bmb_wr=%0d bmb_rd=%0d",
             $time, init_cnt_mrs, init_cnt_zqcl, init_cnt_ref,
             init_cnt_act, init_cnt_wr, init_cnt_rd, init_cnt_pre, init_cnt_illegal,
             tb_wr_acc, tb_rd_acc);
    check(init_cnt_illegal === 16'd0, "PIN: illegal command decoded during traffic");
    check(init_cnt_wr >= 440, "PIN: <440 write commands reached the model");
    check(init_cnt_rd >= 440, "PIN: <440 read commands reached the model");
    check(init_cnt_ref > 0, "PIN: no refresh issued during traffic window");

    if (mem_dqs_blank_err !== 1'b0)
      $display("DQSBLANK cnt=%0d", mem_dqs_blank_cnt);
    check(mem_dqs_blank_err === 1'b0, "DQS: blanking violation (outside write envelope / WL span)");

    // ---- DFI MANAGEMENT PLANE (ADR-0016 v1) --------------------------------------
    $display("INITCHK2 initComplete=%b initStartSeen=%b freqRatio=%b alertN=%b parityIn=%b dbDis=%b clkDis=%b",
             dfi_status_initComplete, init_start_seen, dfi_status_freqRatio,
             dfi_status_alertN, dfi_status_parityIn,
             dfi_status_dataByteDisable, dfi_status_dramClkDisable);
    check(dfi_status_initComplete === 1'b1, "MGMT: initComplete not asserted");
    check(init_start_seen === 1'b1, "MGMT: initStart pulse never observed");
    check(dfi_status_freqRatio === 2'b10, "MGMT: freqRatio != 2");
    check(dfi_status_alertN === 2'b11, "MGMT: alertN not high");
    check(dfi_status_parityIn === 2'b00, "MGMT: parityIn not idle-0");
    check(dfi_status_dataByteDisable === 4'b0000, "MGMT: dataByteDisable not idle-0");
    check(dfi_status_dramClkDisable === 2'b00, "MGMT: dramClkDisable not idle-0");

    if (errors == 0)
      $display("TC13_PASS: BMB full-path (2-source OOO + backpressure + masked writes -> MC -> DFI -> PHY -> model)");
    else
      $display("TC13_FAIL: %0d errors", errors);
    $finish;
  end

  // ---- safety timeout --------------------------------------------------------
  initial begin
    #1500000;
    $display("TC13_FAIL: timeout");
    $finish;
  end
endmodule
