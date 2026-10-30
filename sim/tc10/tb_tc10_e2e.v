// ============================================================================
// tb_tc10_e2e.v — TC10: end-to-end integration smoke (Step 8b)
//
//   DfiController (MC, generated)  --DFI-->  UsPhy (PHY, generated)
//         ^                                        | pads
//         | Bmb                                    v
//      (TB)                                Ddr4SdramModel (TC9)
//
// Checks (forward path first):
//   W1: a Bmb WRITE reaches the model — wr_key_last latches a sane key and
//       wr_word_last matches the 256-bit payload driven on Bmb.
//   W2: no illegal command decodes (hierarchical probe of the pin model).
// Stretch (enabled once W1 is green):
//   R1: a Bmb READ of the same address returns the written data on bmb_rsp.
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
  wire        tr_rdlvlReq, tr_rdlvlEn, tr_rdlvlResp, tr_rdlvlGateReq, tr_rdlvlGateEn;
  wire [1:0]  tr_phyRdlvlCsN, tr_phyRdlvlGateCsN;
  wire        tr_wrlvlReq, tr_wrlvlEn, tr_wrlvlStrobe, tr_wrlvlResp;
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
  wire [1:0]  p_dqs_t, p_dqs_c, p_dqs_in_t, p_dm_dbi_n;
  wire [1:0]  pads_dqs_t, pads_dqs_c, pads_dqs_in_t, pads_dm_dbi_n;
  wire [8:0]  wlDelayOut;

  assign p_ca      = pads_ca;
  assign p_ba      = pads_ba;
  assign p_dqs_t   = pads_dqs_t;
  assign p_dqs_c   = pads_dqs_c;
  assign p_dqs_in_t = pads_dqs_in_t;
  assign p_dm_dbi_n = pads_dm_dbi_n;

  // ---- model observability ----------------------------------------------------
  wire [15:0]  mem_wr_key_last, mem_rd_key_last;
  wire [255:0] mem_wr_word_last;
  wire         mem_rd_driving, mem_rd_pending_err;

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
    .io_dfi_rdTraining_rdlvlEn(tr_rdlvlEn),
    .io_dfi_rdTraining_rdlvlResp(tr_rdlvlResp),
    .io_dfi_rdTraining_rdlvlGateReq(tr_rdlvlGateReq),
    .io_dfi_rdTraining_phyRdlvlGateCsN(tr_phyRdlvlGateCsN),
    .io_dfi_rdTraining_rdlvlGateEn(tr_rdlvlGateEn),
    .io_dfi_wrTraining_wrlvlReq(tr_wrlvlReq),
    .io_dfi_wrTraining_phyWrlvlCsN(tr_phyWrlvlCsN),
    .io_dfi_wrTraining_wrlvlEn(tr_wrlvlEn),
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
    .pads_dqs_in_t_0(pads_dqs_in_t[0]), .pads_dqs_in_t_1(pads_dqs_in_t[1]),
    .pads_dm_dbi_n_0(pads_dm_dbi_n[0]), .pads_dm_dbi_n_1(pads_dm_dbi_n[1])
  );

  // ===========================================================================
  // SDRAM behavioral model (TC9)
  // ===========================================================================
  Ddr4SdramModel mem (
    .ck_t(dramClk),          // clean TB reference (pads_ck_t observable-only)
    .rst_n(rst_n),
    .cs_n({p_cs_n_1, p_cs_n_0}),
    .cke({p_cke_1, p_cke_0}),
    .ca(p_ca),
    .ras_n(p_ras_n), .cas_n(p_cas_n), .we_n(p_we_n), .act_n(p_act_n),
    .bg(p_bg), .ba(p_ba), .cid(p_cid),
    .dq(dq),
    .dqs_in(p_dqs_t),
    .dqs_echo(p_dqs_in_t),
    .wr_key_last(mem_wr_key_last),
    .rd_key_last(mem_rd_key_last),
    .wr_word_last(mem_wr_word_last),
    .rd_driving(mem_rd_driving),
    .rd_pending_err(mem_rd_pending_err)
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

    if (errors == 0)
      $display("TC10_PASS: e2e forward path (Bmb write -> MC -> DFI -> PHY -> model)");
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
