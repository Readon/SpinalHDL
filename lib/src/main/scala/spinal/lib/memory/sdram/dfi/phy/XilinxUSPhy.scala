package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.blackbox.xilinx.ultrascale._

case class PhySettings(
  // 基础时序参数
  tCK: Double,
  // RDIMM配置
  rdimm: Boolean = false,
  rcw: Int = 5,       // Registered CAS Write
  rcd: Int = 5,       // Registered CAS Delay
  rp: Int = 5,        // Registered Precharge
  // 动态计算参数
  cl: Int = 5,
  cwl: Int = 5
) {
  // CL/CWL自动计算逻辑
  def setRdimm(en: Boolean, speedGrade: Int = 1600): PhySettings = {
    val (calcCL, calcCWL) = PhySettings.calculateTiming(speedGrade, tCK)
    this.copy(
      rdimm = en,
      cl = if(en) calcCL else this.cl,
      cwl = if(en) calcCWL else this.cwl
    )
  }
}

object PhySettings {
  // JEDEC标准时序计算（来自usphy.py第523-528行）
  def calculateTiming(speedGrade: Int, tCK: Double): (Int, Int) = {
    val clMap = Map(
      1600 -> 11,
      1866 -> 13,
      2133 -> 15
    )
    val cwl = (scala.math.ceil((tCK - 0.25) / 0.25).toInt).max(5)
    (clMap.getOrElse(speedGrade, 11), cwl)
  }
}

case class SdramIO(dfiConfig: DfiConfig) extends Bundle {
  // Clock signals
  val clk_p   = out(Bool())
  val clk_n   = out(Bool())
  
  // Command and address
  val a       = out(Bits(dfiConfig.addressWidth bits))
  val ba      = out(Bits(dfiConfig.bankWidth bits))
  val bg      = out(Bits(dfiConfig.bankGroupWidth bits))
  val ras_n   = out(Bool())  // Row address strobe
  val cas_n   = out(Bool())  // Column address strobe
  val we_n    = out(Bool())  // Write enable
  val cs_n    = out(Bool())  // Chip select
  val act_n   = out(Bool())  // Activation control
  
  // Control signals
  val cke     = out(Bool())  // Clock enable
  val odt     = out(Bool())  // On-die termination
  val reset_n = out(Bool())  // Asynchronous reset
  
  // Data interface
  val dq      = inout(Analog(Bits(dfiConfig.dataWidth bits)))
  val dqs_p   = inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dqs_n   = inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dm      = out(Bits(dfiConfig.dataWidth/8 bits))  // Data mask
}

class USPhy(dfiConfig: DfiConfig) extends Component {
  val io = new Bundle {
    val dfi = slave(Dfi(dfiConfig))
    val pads = new SdramIO(dfiConfig)
    val clk4x = in Bool()
    
    // 统一PHY控制接口
    val phyCtrl = new Bundle {
      val en_vtc     = in Bool()
      val half_sys8x_taps    = out UInt(9 bits)
      
      // 系统控制
      val ctrl = new Bundle {
        val wlevel_en    = in Bool()
        val dly_sel      = in Bits(8 bits)
        val cdly_rst     = in Bool()
        val cdly_inc     = in Bool()
        val cdly_value   = out UInt(9 bits)
      }
      
      // 读路径控制
      val read = new Bundle {
        val dq_rst         = in Bool()
        val dq_inc         = in Bool()
        val bitslip_rst    = in Bool()
        val bitslip        = in Bool()
      }
      
      // 写路径控制
      val write = new Bundle {
        val dq_rst         = in Bool()
        val dq_inc         = in Bool()
        val dqs_rst        = in Bool()
        val dqs_inc        = in Bool()
        val dqs_inc_count  = out UInt(9 bits)
        val bitslip_rst    = in Bool()
        val bitslip        = in Bool()
      }
      
      // 相位控制
      val phase = new Bundle {
        val rd = in UInt(2 bits)
        val wr = in UInt(2 bits)
      }
    }

    val ctrl = new Bundle {
      val reset = in Bool()
      val initDone = out Bool()
    }
  }

  // Clock domains
  val sys4xDomain = ClockDomain(
    clock = io.clk4x,
    reset = ClockDomain.current.reset,
    frequency = FixedFrequency(ClockDomain.current.frequency.getValue*4) // Assuming 4x clock
  )


  def driveFrom(busCtrl : BusSlaveFactory, address : BigInt) : Unit = {
    // 控制寄存器 (32位对齐)
    val resetReg = busCtrl.createReadAndWrite(Bool(), 0x00, 0) init(False)  // [0]
    val enVtcReg = busCtrl.createReadAndWrite(Bool(), 0x04, 0) init(True)   // [1]
    
    // 状态寄存器
    val initDone       = busCtrl.createReadOnly(Bool(), 0x08, 0)            // [2]
    busCtrl.read(io.phyCtrl.half_sys8x_taps, 0x0C)         // [3:11]
    
    // 写电平校准寄存器
    val wlevelEn = busCtrl.createReadAndWrite(Bool(), 0x10, 0)       // [4] 写电平使能
    val wlevelStrobe = busCtrl.createWriteOnly(Bool(), 0x14)         // [5] 写电平触发
    val wlevelDone = busCtrl.createReadOnly(Bool(), 0x6C)            // 校准完成状态 [35]
    
    // 命令延迟控制寄存器
    val cdlyRst = busCtrl.createWriteOnly(Bool(), 0x18)         // [6] 延迟线复位
    val cdlyInc = busCtrl.createWriteOnly(Bool(), 0x1C)         // [7] 延迟线增量
    busCtrl.read(io.phyCtrl.ctrl.cdly_value, 0x20)  // [8:16] 当前延迟值（只读）
    
    // 延迟选择寄存器（按字节使能）
    val dlySel = busCtrl.createReadAndWrite(Bits(8 bits), 0x24) init(0)  // [9:16] 字节通道选择
    
    // 读延迟控制寄存器
    val rdlyDqRst = busCtrl.createWriteOnly(Bool(), 0x28)         // [17] 读数据复位
    val rdlyDqInc = busCtrl.createWriteOnly(Bool(), 0x2C)         // [18] 读延迟增加
    val rdlyDqBitslipRst = busCtrl.createWriteOnly(Bool(), 0x30)  // [19] 读位滑动复位
    val rdlyDqBitslip = busCtrl.createWriteOnly(Bool(), 0x34)     // [20] 读位滑动触发
    
    // Write Delay Control
    val wdlyDqRst = busCtrl.createWriteOnly(Bool(), 0x38)
    val wdlyDqInc = busCtrl.createWriteOnly(Bool(), 0x3C)
    val wdlyDqsRst = busCtrl.createWriteOnly(Bool(), 0x40)
    val wdlyDqsInc = busCtrl.createWriteOnly(Bool(), 0x44)
    busCtrl.read(io.phyCtrl.write.dqs_inc_count, 0x48)
    
    // Write Bitslip
    val wdlyDqBitslipRst = busCtrl.createWriteOnly(Bool(), 0x4C)
    val wdlyDqBitslip = busCtrl.createWriteOnly(Bool(), 0x50)
    
    // Phase Control
    val rdPhase = busCtrl.createReadAndWrite(UInt(2 bits), 0x54) init(0)
    val wrPhase = busCtrl.createReadAndWrite(UInt(2 bits), 0x58) init(0)

    // Hardware Connections
    io.ctrl.reset := resetReg
    io.ctrl.initDone := initDone

    // 系统控制信号连接
    io.phyCtrl.en_vtc     := enVtcReg
    io.phyCtrl.ctrl.wlevel_en  := wlevelEn
    io.phyCtrl.ctrl.cdly_rst   := cdlyRst
    io.phyCtrl.ctrl.cdly_inc   := cdlyInc
    
    // 连接读延迟控制
    io.phyCtrl.read.dq_rst         := rdlyDqRst
    io.phyCtrl.read.dq_inc         := rdlyDqInc
    io.phyCtrl.read.bitslip_rst    := rdlyDqBitslipRst
    io.phyCtrl.read.bitslip        := rdlyDqBitslip
    
    // 连接写路径控制
    io.phyCtrl.write.dq_rst         := wdlyDqRst
    io.phyCtrl.write.dq_inc         := wdlyDqInc
    io.phyCtrl.write.dqs_rst        := wdlyDqsRst
    io.phyCtrl.write.dqs_inc        := wdlyDqsInc
    
    // 连接相位控制
    io.phyCtrl.phase.rd := rdPhase
    io.phyCtrl.phase.wr := wrPhase
    
    // 连接其他控制信号
    io.phyCtrl.write.bitslip_rst    := wdlyDqBitslipRst
    io.phyCtrl.write.bitslip        := wdlyDqBitslip
    io.phyCtrl.ctrl.dly_sel         := dlySel // 使用ctrl子Bundle
  }

  // 实例化参数化延迟线组件
  val delayLine = new ClockingArea(sys4xDomain) {
    val delayCells = Seq.fill(8)(new ODELAYE3(REFCLK_FREQUENCY = 200.0))
    
    // 连接控制信号
    for((cell, idx) <- delayCells.zipWithIndex) {
      // cell.CE         := io.phyCtrl.idelay.ce && io.phyCtrl.ctrl.dly_sel(idx)
      // cell.INC        := io.phyCtrl.idelay.inc
      // cell.LOAD         := io.phyCtrl.idelay.ld
      // cell.CNTVALUEIN := io.phyCtrl.idelay.cntvaluein.asBits
    //   cell.EN_VTC     := io.phyCtrl.delay.en_vtc
      // io.phyCtrl.idelay.cntvalueout(idx) := cell.CNTVALUEOUT.asUInt
      
      // 连接数据通路
      cell.ODATAIN  := cmdPath.cmdSignals(idx)
      cmdPath.cmdSignals(idx) := cell.DATAOUT
    }
  }

  // Command path
  val cmdPath = new Area {
    // 命令信号同步寄存器
    val syncedAddress = RegNextWhen(io.dfi.control.address, io.dfi.control.cke.asBool)
    val syncedBank = RegNextWhen(io.dfi.control.bank, io.dfi.control.cke.asBool)
    
    // 组合命令信号并展开为Bool向量
    val cmdSignals: Seq[Bool] = 
      syncedAddress.asBools ++
      syncedBank.asBools ++
      io.dfi.control.rasN.asBools ++
      io.dfi.control.casN.asBools ++
      io.dfi.control.weN.asBools

    val oserdesVec = Seq.fill(cmdSignals.length)(new OSERDESE3())
    for((osd,sig) <- oserdesVec.zip(cmdSignals)){
      osd.RST    := io.ctrl.reset
      osd.CLK    := io.clk4x
      osd.CLKDIV := ClockDomain.current.readClockWire
      osd.D      := sig.asBits #* 8
    }
  }

  // Data path
  val dataPath = new Area {
    // DQS pattern生成模块
    val dqsPattern = new Area {
      val dqs = Reg(Bool())
      val dqs_n = Reg(Bool())
      val phase = io.phyCtrl.phase.wr
      
      // 生成DQS脉冲（4x时钟域）
      sys4xDomain {
        when(io.dfi.write.wr(0).wrdataEn) {
          switch(phase) {
            is(0) { dqs := True; dqs_n := False }
            is(1) { dqs := False; dqs_n := True }
            is(2) { dqs := !dqs; dqs_n := !dqs_n }
          }
        } otherwise {
          dqs := False
          dqs_n := False
        }
      }
    }

    // Write path
    val wrData = io.dfi.write.wr(0).wrdata
    val wrDataEn = io.dfi.write.wr(0).wrdataEn
    val wrDataMask = io.dfi.write.wr(0).wrdataMask
    val wrDataCsN = if(dfiConfig.useWrdataCsN) Some(io.dfi.write.wr(0).wrdataCsN) else None
    
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3())
    for((osd, data) <- dqOserdes.zip(wrData.asBools)){
      osd.D := data.asBits #* 8
      osd.T_OUT := wrDataCsN.map(_.asBools.head).getOrElse(wrDataMask.asBools.head) // 显式转换为Bool
    }

    // Read path
    val rdData = io.dfi.read.rd(0).rddata
    for((osd, data) <- dqOserdes.zip(rdData.asBools)){
      data := osd.OQ
    }

    // DQS输出连接
    io.pads.dqs_p := dqsPattern.dqs.asBits
    io.pads.dqs_n := dqsPattern.dqs_n.asBits
  }


  // Training FSM
  val trainingFSM = new Area {
    val initDoneReg = RegInit(False)
    val calibDoneReg = RegInit(False)
    val writeLevelDone = RegInit(False)
    val readGateDone = RegInit(False)
    val readEyeDone = RegInit(False)

    val fsm = new StateMachine {
      val stateIdle = new State with EntryPoint {
        whenIsActive {
          when(io.ctrl.reset) {
            goto(stateInit)
          }
        }
      }

      val stateInit = new State {
        onEntry(initDoneReg := False)
        whenIsNext(stateWriteLeveling)
      }

      val stateWriteLeveling = new State {
        onEntry(writeLevelDone := False)
        whenIsActive {
          when(io.phyCtrl.ctrl.wlevel_en) {
            writeLevelDone := True
            goto(stateReadGateTraining)
          }
        }
      }

      val stateReadGateTraining = new State {
        onEntry(readGateDone := False)
        whenIsActive {
          // 添加读门训练逻辑
          readGateDone := True
          goto(stateReadEyeTraining)
        }
      }

      val stateReadEyeTraining = new State {
        onEntry(readEyeDone := False)
        whenIsActive {
          // 添加读眼训练逻辑
          readEyeDone := True
          goto(stateCalibrationDone)
        }
      }

      val stateCalibrationDone = new State {
        onEntry(calibDoneReg := False)
        whenIsNext(stateReady)
      }

      val stateReady = new State {
        onEntry(io.ctrl.initDone := True)
      }
      
    // 新增BitSlip模块（第530-551行Python代码转写）
    class BitSlip(width: Int) extends Component {
      val io = new Bundle {
        val input = in Bits(width bits)
        val slip = in Bool()
        val output = out Bits(width bits)
        val rst = in Bool()
      }
    
      val buffer = RegNextWhen(io.input, io.slip) init(0)
      when(io.rst) {
        buffer := 0
      }
      io.output := buffer
    }
    
    // 新增TappedDelayLine模块（第554-561行Python代码转写）
    class TappedDelayLine(width: Int, taps: Int) extends Component {
      val io = new Bundle {
        val input = in Bool()
        val outputs = out Vec(Bool(), taps)
      }
    
      val delayLine = Vec(Reg(Bool())).addAttribute("async_reg")
      delayLine(0) := io.input
      for(i <- 1 until taps) {
        delayLine(i) := delayLine(i-1)
      }
      io.outputs := delayLine
    }
    }
  }
}