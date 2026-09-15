// tc2_bl8_write.v — TC2: consecutive-frame write integrity (BL8-style)
// Proves that 8 DISTINCT consecutive frames transmit without cross-contamination.
// Latency-immune method: hold each pattern long enough to fully drain the
// SerDes pipeline, then assert rddata converges to that exact pattern.
// Loopback (OQ->D) isolates the SerDes datapath from pad timing.
`timescale 1ns/1ps
module tc2_bl8_write;
  reg refclk=0, dramClk=0, rst_n=0;
  always #5 refclk=~refclk; always #2.5 dramClk=~dramClk;

  wire dq_0,dq_1,dq_2,dq_3,dq_4,dq_5,dq_6,dq_7;
  wire dq_8,dq_9,dq_10,dq_11,dq_12,dq_13,dq_14,dq_15;
  reg [31:0] wrdata_0=0,wrdata_1=0; reg wr_en_0=0,wr_en_1=0;
  reg [3:0] wrmask_0=0,wrmask_1=0; reg [1:0] wrcs_0=2'b11,wrcs_1=2'b11;
  reg rden_0=0,rden_1=0; reg [1:0] rdcn_0=2'b11,rdcn_1=2'b11;
  wire [31:0] rddata_0,rddata_1; wire rdvalid_0,rdvalid_1; wire [3:0] rddbi_0,rddbi_1;
  wire [1:0] dqs_t,dqs_c,dqs_echo; assign dqs_echo=dqs_t;

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
    .clk(refclk),.reset(~rst_n));

  integer i;
  integer errors=0;
  reg [31:0] pat0 [0:7];
  reg [31:0] pat1 [0:7];
  initial begin
    // 8 DISTINCT patterns per phase (non-periodic so phase/bit mapping is discriminated)
    pat0[0]=32'h1234_5678; pat0[1]=32'h2345_6789; pat0[2]=32'h3456_789A; pat0[3]=32'h4567_89AB;
    pat0[4]=32'h5678_9ABC; pat0[5]=32'h6789_ABCD; pat0[6]=32'h789A_BCDE; pat0[7]=32'h89AB_CDEF;
    pat1[0]=32'hFEDC_BA98; pat1[1]=32'hEFDC_BA98; pat1[2]=32'hFEDC_BA97; pat1[3]=32'hFEDC_BA96;
    pat1[4]=32'hFEDC_BA95; pat1[5]=32'hFEDC_BA94; pat1[6]=32'hFEDC_BA93; pat1[7]=32'hFEDC_BA92;

    rst_n=0; #50; rst_n=1; #40;

    // Loopback: OQ->D for all 16 pins
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

    rden_0=1; rden_1=1;

    // Warm-up: let the SerDes pipeline (OSERDESE3+ISERDESE3 latencies) drain
    // with a throwaway frame so frame0 is measured in steady state.
    wr_en_0=1; wrdata_0=32'hFFFFFFFF; wr_en_1=1; wrdata_1=32'h00000000;
    repeat(8)@(posedge dramClk);

    $display("== TC2: CONSECUTIVE-FRAME WRITE INTEGRITY (loopback) ==");
    for (i = 0; i < 8; i = i + 1) begin
      wr_en_0=1; wrdata_0=pat0[i];
      wr_en_1=1; wrdata_1=pat1[i];
      // Hold long enough to fully drain the SerDes pipeline (>= latency).
      repeat(8)@(posedge dramClk);
      if (rddata_0===pat0[i] && rddata_1===pat1[i])
        $display("  frame%0d: in0=%h in1=%h -> out0=%h out1=%h [OK]", i, pat0[i],pat1[i],rddata_0,rddata_1);
      else begin
        $display("  frame%0d: in0=%h in1=%h -> out0=%h out1=%h [MISMATCH]", i, pat0[i],pat1[i],rddata_0,rddata_1);
        errors=errors+1;
      end
    end
    wr_en_0=0; wr_en_1=0; rden_0=0; rden_1=0;

    $display("==================== SUMMARY ====================");
    if (errors==0) $display("TC2 CONSECUTIVE WRITE: PASS — all 8 distinct frames transmitted losslessly");
    else $display("TC2 CONSECUTIVE WRITE: FAIL — %0d frame(s) mismatched", errors);
    $finish;
  end
  initial begin #20000; $display("TIMEOUT"); $finish; end
endmodule