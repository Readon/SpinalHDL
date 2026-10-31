// ============================================================================
// TC11: randomized end-to-end stress — Bmb -> MC(DfiController) -> PHY(UsPhy)
//       -> Ddr4SdramModel, with Ddr4PinModel legality monitoring.
//
// Traffic contract (ratified 2026-10-31; see .usphy_redesign_docs/TASK.md):
//   * 512 WRITE + 512 READ Bmb bursts, order randomized by LCG(SEED).
//   * Address pack {rank(1)|row(13)|bank(4)|colBytes(11)}; row in [0,63] and
//     colBytes in {0,16,32,48} (16B-aligned) so every TB address maps
//     bijectively onto a model cell; 5%/5% boundary bias to 0/max.
//     16B alignment is REQUIRED: the model stores one 16B word per 32B cell
//     (key col = physical column[4:0]) while the MC strides 16B bursts by 8
//     physical columns - unaligned reads would span two cells. Unaligned
//     (2-burst) reads are an MC capability covered by the 28/28 SpinalSim
//     regression (DfiMemoryAgent), out of this PHY-datapath test's scope.
//   * Content-addressed payload: byte[i] = key[7:0] ^ key[15:8] ^ i with key
//     mirroring Ddr4SdramModel.v's storage key — overwrites of a colliding
//     cell stay scoreboard-consistent (no write-history RAM needed).
//   * Reads only from a 64-entry ring of recently written addresses.
//   * ~10% chance of a 1-8 cycle idle gap between transactions.
// Checks:
//   * TC10 preamble: init passthrough (INITCHK) + WL closed loop [345..355].
//   * Every read: 2 fragments, EXACT vs f(key), context echo == 2'b01.
//   * Pin legality (cumulative): illegal == 0, wr >= 512, rd >= 512, ref > 0.
// Determinism: LCG(1664525, 1013904223) seeded by SEED (default 42), printed.
// Known limits: model key truncates row/col (wrap); mid-burst reset undefined;
// unaligned reads excluded (see above).
// ============================================================================
module tb_tc11_stress;
  parameter SEED = 42;

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
  wire [1:0]  p_dqs_in_t;     // cut-muxed feed to the PHY dqs_in_t pads
  wire [1:0]  pads_dqs_t, pads_dqs_c, pads_dm_dbi_n;
  wire [8:0]  wlDelayOut;

  assign p_ca      = pads_ca;
  assign p_ba      = pads_ba;
  assign p_dqs_t   = pads_dqs_t;
  assign p_dqs_c   = pads_dqs_c;
  // Negative-proof knob (mutant run): flip to 1 in a COPIED tb
  // (sed -> tb_tc10_e2e_cut.v) to sever the DQS feedback path; the PHY must
  // then fail to converge (wrlvlResp never earns). Kept 0 in this canonical
  // file. (xvlog -define turned out unreliable on the full file list.)
  parameter WL_ECHO_CUT_P = 0;
  assign p_dqs_in_t = WL_ECHO_CUT_P ? 2'b00 : dqs_echo_raw;
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
    .io_dfi_rdTraining_rdlvlEn(tr_rdlvlEn),
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
    .pads_dm_dbi_n_0(pads_dm_dbi_n[0]), .pads_dm_dbi_n_1(pads_dm_dbi_n[1])
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
  // Stress machinery (TC11): RNG, model-key mirroring, payload, ring, tasks
  // ===========================================================================
  reg  [31:0] rng;
  reg  [28:0] ring [0:63];
  integer     ring_count = 0;
  integer     tb_wr_acc = 0, tb_rd_acc = 0;

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

  // Random address inside the model key space (bijective to a model cell).
  task gen_addr(output [ADDR_W-1:0] a);
    integer rr, cc, rk;
    reg [12:0] rw;
    reg [10:0] cw;
    begin
      rnd32(rr); rnd32(cc); rnd32(rk);
      if (rr[7:0] < 8'd5)       rw = 13'd0;      // boundary bias: row 0
      else if (rr[7:0] < 8'd10) rw = 13'd63;     // boundary bias: row max
      else                      rw = rr[12:0] % 13'd64;
      // 16B-aligned only: the behavioral model stores one 16B word per 32B cell
      // (key col = physical column[4:0]) while the MC strides 16B bursts by
      // 8 physical columns; unaligned/multi-burst reads would hit sibling cells.
      // Unaligned (2-burst) reads are an MC capability covered by the 28/28
      // SpinalSim regression (DfiMemoryAgent), out of TC11's PHY-datapath scope.
      if (cc[7:0] < 8'd5)       cw = 11'd0;      // boundary bias: colBytes 0
      else if (cc[7:0] < 8'd10) cw = 11'd48;     // boundary bias: colBytes max
      else                      cw = cc[1:0] * 11'd16;  // {0,16,32,48}
      a = {rk[0], rw, cc[3:0], cw};
    end
  endtask

  // 16-byte content-addressed write burst (2 x 64-bit fragments).
  task bmb_wr_burst(input [ADDR_W-1:0] addr);
    integer i;
    begin
      for (i = 0; i < 2; i = i + 1) begin
        bmb_cmd_valid   = 1'b1;
        bmb_cmd_opcode  = 1'b1;
        bmb_cmd_address = addr;
        bmb_cmd_length  = 10'd1;
        bmb_cmd_data    = (i == 0) ? payfrag(mk_key(addr), 4'd0)
                                   : payfrag(mk_key(addr), 4'd8);
        bmb_cmd_mask    = 8'hFF;
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

  // 16-byte read command (single fragment, length = 16-1).
  task bmb_rd_burst(input [ADDR_W-1:0] addr, input [1:0] ctx);
    begin
      bmb_cmd_valid   = 1'b1;
      bmb_cmd_opcode  = 1'b0;
      bmb_cmd_address = addr;
      bmb_cmd_length  = 10'd15;
      bmb_cmd_data    = 64'h0;
      bmb_cmd_mask    = 8'hFF;
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
  task collect_rsp(output [63:0] f0, output [63:0] f1,
                   output integer n, output [1:0] ctx_seen);
    integer kt;
    begin
      n        = 0;
      f0       = 64'h0;
      f1       = 64'h0;
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

    // ---- STRESS: 512 WR + 512 RD, random bank/row/col/rank -----------------------
    // Content-addressed: expected read data = f(mk_key(addr)) — no history RAM.
    // Reads drawn from the 64-entry ring of recently written addresses.
    // The MC plays its built-in DDR4 init sequence (Ddr4InitSequencer: 7xMRS +
    // ZQCL, ~10.5us) before releasing user commands — wait well past it.
    repeat(1500) @(posedge refclk);  // 15us: init done
    $display("TC11_SEED %0d", SEED);
    rng = SEED;

    begin : stress
      integer txn, wr_left, rd_left, g;
      reg op_wr;
      reg [28:0] cur_addr;
      reg [63:0] rf0, rf1;
      integer rn;
      reg [1:0] rsp_ctx;
      wr_left = 512; rd_left = 512;
      for (txn = 0; txn < 1024; txn = txn + 1) begin
        // Op selection (quota-enforced 512/512, random order)
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
          $display("RSP t=%0t txn=%0d addr=%07h key=%04h rdk=%04h rn=%0d ctx=%b f0=%016h f1=%016h ex0=%016h ex1=%016h",
                   $time, txn, cur_addr, mk_key(cur_addr), mem_rd_key_last, rn, rsp_ctx,
                   rf0, rf1, payfrag(mk_key(cur_addr), 4'd0), payfrag(mk_key(cur_addr), 4'd8));
          check(rn == 2, "RD: expected 2 response fragments");
          check(rf0 === payfrag(mk_key(cur_addr), 4'd0), "RD: frag0 != f(key)");
          check(rf1 === payfrag(mk_key(cur_addr), 4'd8), "RD: frag1 != f(key)");
          check(rsp_ctx === 2'b01, "RD: context echo mismatch");
          rd_left = rd_left - 1;
        end
        // ~10% idle gap of 1-8 cycles
        rnd32(g);
        if (g[31:16] < 16'h1999)
          repeat (1 + g[15:13]) @(posedge refclk);
      end
    end

    // ---- PIN LEGALITY (cumulative counters) -------------------------------------
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
      $display("TC11_PASS: e2e stress (512W+512R Bmb -> MC -> DFI -> PHY -> model), exact roundtrip + pin legality");
    else
      $display("TC11_FAIL: %0d errors", errors);
    $finish;
  end




  // ---- safety timeout --------------------------------------------------------
  initial begin
    #2000000;
    $display("TC11_FAIL: timeout");
    $finish;
  end
endmodule
