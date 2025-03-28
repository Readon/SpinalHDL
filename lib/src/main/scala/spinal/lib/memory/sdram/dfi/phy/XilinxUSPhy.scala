package spinal.lib.memory.sdram.dfi.phy

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.memory.sdram.dfi._
import spinal.lib.blackbox.xilinx.ultrascale._

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
        val wlevel_strobe= in Bool()
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


  def driveFrom(busCtrl: BusSlaveFactory, address: BigInt): Unit = {
    // Control register group
    val ctrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x00).init(0)
    io.ctrl.reset      := ctrlReg(0)       // [0] Global reset
    io.phyCtrl.en_vtc  := ctrlReg(1)       // [1] Voltage temp compensation enable
    ctrlReg(8)         := io.ctrl.initDone // [8] Initialization status (RO)

    // Delay control register (0x04)
    val delayCtrlReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x04).init(0)
    io.phyCtrl.ctrl.cdly_rst    := delayCtrlReg(0)  // [0] CDLY reset
    io.phyCtrl.ctrl.cdly_inc    := delayCtrlReg(1)  // [1] CDLY increment 
    io.phyCtrl.ctrl.wlevel_en   := delayCtrlReg(2)  // [2] Write leveling enable
    io.phyCtrl.ctrl.wlevel_strobe := delayCtrlReg(3) // [3] Write leveling trigger
    io.phyCtrl.ctrl.dly_sel     := delayCtrlReg(16 to 23) // [16:23] Byte lane select

    // Read delay control (0x08)
    val readDelayReg = busCtrl.createWriteOnly(Bits(32 bits), 0x08)
    io.phyCtrl.read.dq_rst      := readDelayReg(0)  // [0] Read DQ reset
    io.phyCtrl.read.dq_inc      := readDelayReg(1)  // [1] Read DQ increment
    io.phyCtrl.read.bitslip_rst := readDelayReg(2)  // [2] Bitslip reset
    io.phyCtrl.read.bitslip     := readDelayReg(3)  // [3] Bitslip trigger

    // Write delay control (0x0C)
    val writeDelayReg = busCtrl.createWriteOnly(Bits(32 bits), 0x0C)
    io.phyCtrl.write.dq_rst      := writeDelayReg(0) // [0] Write DQ reset
    io.phyCtrl.write.dq_inc      := writeDelayReg(1) // [1] Write DQ increment
    io.phyCtrl.write.dqs_rst     := writeDelayReg(2) // [2] Write DQS reset 
    io.phyCtrl.write.dqs_inc     := writeDelayReg(3) // [3] Write DQS increment
    io.phyCtrl.write.bitslip_rst := writeDelayReg(4) // [4] Write bitslip reset
    io.phyCtrl.write.bitslip     := writeDelayReg(5) // [5] Write bitslip trigger

    // Status registers
    busCtrl.read(io.phyCtrl.half_sys8x_taps ## io.phyCtrl.ctrl.cdly_value, 0x10) // [0x10] Taps + CDLY value
    busCtrl.read(io.phyCtrl.write.dqs_inc_count, 0x14) // [0x14] DQS increment count

    // Configuration register (0x18)
    val configReg = busCtrl.createReadAndWrite(Bits(32 bits), 0x18).init(0)
    io.phyCtrl.phase.rd     := configReg(13 downto 12).asUInt // [1:0] Read phase
    io.phyCtrl.phase.wr     := configReg(15 downto 14).asUInt // [3:2] Write phase
  }

  // 实例化参数化延迟线组件
  val delayLine = new ClockingArea(sys4xDomain) {
    val delayCells = Seq.fill(8)(new ODELAYE3(refClkFrequency = 200.0))
    
    // 连接控制信号
    for((cell, idx) <- delayCells.zipWithIndex) {
      cell.RST := io.ctrl.reset
      cell.EN_VTC := io.phyCtrl.en_vtc
      cell.CE := io.phyCtrl.ctrl.cdly_inc && io.phyCtrl.ctrl.dly_sel(idx)
      cell.INC := True
      
      // 连接数据通路
      cell.ODATAIN := cmdPath.cmdSignals(idx)
      cmdPath.cmdSignals(idx) := cell.DATAOUT
      
      // 连接状态输出
      when(io.phyCtrl.ctrl.dly_sel(idx)) {
        io.phyCtrl.ctrl.cdly_value := cell.CNTVALUEOUT
      }
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
      osd.D      := B(0, 8 bits).setAllTo(sig)
      osd.T      := False
    }
  }

  // DQSPattern module implementation (from usphy.py lines 564-593)
  class DQSPattern extends Component {
    val io = new Bundle {
      val preamble = in Bool()
      val postamble = in Bool()
      val wlevel_en = in Bool()
      val wlevel_strobe = in Bool()
      val output = out Bits(8 bits)
    }

    // 组合逻辑生成模式
    val pattern = Bits(8 bits)
    val wlevel_strobe_rise = io.wlevel_strobe.rise(False)
    
    pattern := 0x55 // 默认模式 01010101
    when(io.preamble) {
      pattern := 0x15 // 00010101
    }.elsewhen(io.postamble) {
      pattern := 0x54 // 01010100
    }.elsewhen(io.wlevel_en) {
      pattern := 0x00
      when(wlevel_strobe_rise) {
        pattern := 0x01 // 仅在上沿产生单周期脉冲
      }
    }

    // 添加bitslip处理
    val bitslip = new BitSlip(8)
    bitslip.io.input := pattern
    bitslip.io.rst := False
    bitslip.io.slp := False
    
    io.output := bitslip.io.output
  }

  // Data path
  val dataPath = new Area {
    // Control signals
    val dqs_oe = Reg(Bool()) init(False)
    val dq_oe = Reg(Bool()) init(False)
    val dqs_preamble = Reg(Bool()) init(False)
    val dqs_postamble = Reg(Bool()) init(False)

    // DQS pattern generator (已适配新DQSPattern实现)
    val dqsPattern = new DQSPattern
    dqsPattern.io.preamble := dqs_preamble
    dqsPattern.io.postamble := dqs_postamble
    dqsPattern.io.wlevel_en := io.phyCtrl.ctrl.wlevel_en
    dqsPattern.io.wlevel_strobe := io.phyCtrl.ctrl.wlevel_strobe

    // Write path
    val wrData = io.dfi.write.wr(0).wrdata
    val wrDataEn = io.dfi.write.wr(0).wrdataEn
    val wrDataMask = io.dfi.write.wr(0).wrdataMask
    val wrDataCsN = if(dfiConfig.useWrdataCsN) Some(io.dfi.write.wr(0).wrdataCsN) else None

    // Write data bitslip
    val wrBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    for((slip, data) <- wrBitslip.zip(wrData.asBools)) {
      slip.io.input := data.asBits #* 8
      slip.io.rst := io.phyCtrl.write.bitslip_rst
      slip.io.slp := io.phyCtrl.write.bitslip
    }

    // DQ OSERDES
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new OSERDESE3())
    for((osd, slip) <- dqOserdes.zip(wrBitslip)) {
      osd.D := slip.io.output
      osd.T := ~dq_oe
      osd.CLK := io.clk4x
      osd.CLKDIV := ClockDomain.current.readClockWire
      osd.RST := io.ctrl.reset
    }

    // Read path
    val rdData = io.dfi.read.rd(0).rddata
    val rdBitslip = Seq.fill(dfiConfig.dataWidth)(new BitSlip(8))
    for((slip, data) <- rdBitslip.zip(rdData.asBools)) {
      slip.io.input := data.asBits #* 8
      slip.io.rst := io.phyCtrl.read.bitslip_rst
      slip.io.slp := io.phyCtrl.read.bitslip
      data := slip.io.output(0)
    }

    // DQS output
    val dqsOserdes = new OSERDESE3()
    dqsOserdes.D := dqsPattern.io.output
    dqsOserdes.T := ~dqs_oe
    dqsOserdes.CLK := io.clk4x
    dqsOserdes.CLKDIV := ClockDomain.current.readClockWire
    dqsOserdes.RST := io.ctrl.reset

    io.pads.dqs_p := dqsOserdes.OQ.asBits
    io.pads.dqs_n := ~dqsOserdes.OQ.asBits

    // Write latency计算公式说明（依据Xilinx UG571文档）：
    // tPhyWrLat = PHY物理层延迟（包含ODELAY tap值和PCB走线延迟）
    // ddrWrLat  = 控制器级写延迟（对应JEDEC CWL参数）
    // +2周期补偿：
    //   1周期用于4x到1x时钟域转换（UG571 Figure 3-14）
    //   1周期用于OSERDESE3固有延迟（UG571 Table 3-1）
    val writeLatency = dfiConfig.timeConfig.tPhyWrLat - dfiConfig.sdram.ddrWrLat + 2
    val wrDelay = new TappedDelayLine(1, writeLatency + 2)
    wrDelay.io.input := wrDataEn

    dq_oe := wrDelay.io.taps(writeLatency)
    dqs_oe := io.phyCtrl.ctrl.wlevel_en | dq_oe
    dqs_preamble := wrDelay.io.taps(writeLatency - 1) & ~wrDelay.io.taps(writeLatency)
    dqs_postamble := wrDelay.io.taps(writeLatency + 1) & ~wrDelay.io.taps(writeLatency)
  }


  // BitSlip module implementation (from usphy.py lines 530-551)
  class BitSlip(width: Int) extends Component {
    val io = new Bundle {
      val input = in Bits(width bits)
      val output = out Bits(width bits)
      val rst = in Bool()
      val slp = in Bool()
    }

    val shiftReg = History(io.input, length=width, init=B(0, width bits), when=io.slp)
    val ptr = Counter(width, inc=io.slp)

    when(io.rst) {
      ptr.clear()
      shiftReg.foreach(_ := B(0, width bits))
    }

    io.output := shiftReg(ptr.value)
  }

  // TappedDelayLine module implementation (from usphy.py lines 554-561)
  class TappedDelayLine(width: Int, ntaps: Int) extends Component {
    val io = new Bundle {
      val input = in Bool()
      val taps = out Vec(Bool(), ntaps)
    }

    val delayLine = Vec(Reg(Bool()) init(False), ntaps)
    delayLine(0) := io.input
    for(i <- 1 until ntaps) {
      delayLine(i) := delayLine(i-1)
    }
    io.taps := delayLine
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
        onEntry {
          io.ctrl.initDone := True
        }
      }
    }
  }
}