package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.fsm.{StateMachine, State, EntryPoint}
import spinal.lib.memory.sdram.dfi.interface._
import spinal.lib.memory.sdram.SdramGeneration.DDR3


class Oserdese3BlackBox extends BlackBox {
  val generic = new Generic {
    val SIM_DEVICE = "ULTRASCALE"
    val DATA_WIDTH = 8
    val INIT = "FALSE"
    val IS_RST_INVERTED = 0
    val IS_CLK_INVERTED = 0
    val IS_CLKDIV_INVERTED = 0
  }

  val io = new Bundle {
    val RST    = in Bool()
    val CLK    = in Bool()
    val CLKDIV = in Bool()
    val D      = in Bits(8 bits)
    val OQ     = out Bool()
    val T_OUT  = out Bool()
  }

  mapCurrentClockDomain(io.CLK, io.RST)
}

// IODELAYE3 BlackBox definition
class IODELAYE3BlackBox extends BlackBox {
    val generic = new Generic {
        val SIM_DEVICE       = "ULTRASCALE"
        val CASCADE          = "NONE"
        val UPDATE_MODE      = "ASYNC"
        val REFCLK_FREQUENCY = 200.0 // Will be parameterized later
        val DELAY_FORMAT     = "TIME"
        val DELAY_TYPE       = "VARIABLE"
        val DELAY_VALUE      = 0
        val IS_CLK_INVERTED  = 0
        val IS_RST_INVERTED  = 0
        val DELAY_SRC        = "IDATAIN"
    }

    val io = new Bundle {
        val RST         = in Bool()
        val CLK         = in Bool()
        val EN_VTC      = in Bool()
        val CE          = in Bool()
        val INC         = in Bool()
        val ODATAIN     = in Bool()
        val DATAOUT     = out Bool()
        val CNTVALUEOUT = out UInt(9 bits) // Assuming 9 bits based on usphy.py
    }
}

case class SdramPads(dfiConfig: DfiConfig) extends Bundle {
  val clk_p = out(Bool())
  val clk_n = out(Bool())
  val clk4x = in(Bool())
  val a = out(Bits(dfiConfig.addressWidth bits))
  val ba = out(Bits(dfiConfig.bankWidth bits))
  val dq = inout(Analog(Bits(dfiConfig.dataWidth bits)))
  val dqs_p = inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dqs_n = inout(Analog(Bits(dfiConfig.dataWidth/8 bits)))
  val dm = out(Bits(dfiConfig.dataWidth/8 bits))
}

class UsDdrPhy(dfiConfig: DfiConfig) extends Component {
  val io = new Bundle {
    val dfi = slave(Dfi(dfiConfig))
    val pads = new SdramPads(dfiConfig)
    val ctrl = new Bundle {
      val reset = in Bool()
      val initDone = out Bool()
    }
    
    // PHY Control Interface
    val phyCtrl = new Bundle {
      // Control Signals
      val en_vtc       = in Bool()
      val wlevel_en    = in Bool()
      val cdly_rst     = in Bool()
      val cdly_inc     = in Bool()
      val dly_sel      = in Bits(8 bits)
      
      // Read Path
      val rdly_dq_rst         = in Bool()
      val rdly_dq_inc         = in Bool()
      val rdly_dq_bitslip_rst = in Bool()
      val rdly_dq_bitslip     = in Bool()
      
      // Write Path
      val wdly_dq_rst         = in Bool()
      val wdly_dq_inc         = in Bool()
      val wdly_dqs_rst        = in Bool()
      val wdly_dqs_inc        = in Bool()
      val wdly_dq_bitslip_rst = in Bool()
      val wdly_dq_bitslip     = in Bool()
      
      // Phase Control
      val rdphase = in UInt(2 bits)
      val wrphase = in UInt(2 bits)
      
      // Status Signals
      val half_sys8x_taps    = out UInt(9 bits)
      val wdly_dqs_inc_count = out UInt(9 bits)
      val cdly_value         = out UInt(9 bits)
    }
  }

  // Clock domains
  val sys4xDomain = ClockDomain(
    clock = io.pads.clk4x,
    reset = ClockDomain.current.reset,
    frequency = FixedFrequency(ClockDomain.current.frequency.getValue*4) // Assuming 4x clock
  )


  def driveFrom(busCtrl : BusSlaveFactory, address : BigInt) : Unit = {
    // 控制寄存器 (32位对齐)
    val resetReg = busCtrl.createReadAndWrite(Bool(), 0x00, 0) init(False)  // [0]
    val enVtcReg = busCtrl.createReadAndWrite(Bool(), 0x04, 0) init(True)   // [1]
    
    // 状态寄存器
    val initDone       = busCtrl.createReadOnly(Bool(), 0x08, 0)            // [2]
    val halfSys8xTaps  = busCtrl.createReadOnly(UInt(9 bits), 0x0C)         // [3:11]
    
    // 电气特性寄存器组
    val vccConfig = busCtrl.createReadAndWrite(UInt(4 bits), 0x60) init(0)    // 电压配置 [24:27]
    val tempComp = busCtrl.createReadAndWrite(UInt(4 bits), 0x64) init(0)     // 温度补偿 [28:31]
    val driveStrength = busCtrl.createReadAndWrite(UInt(3 bits), 0x68) init(7)// 驱动强度 [32:34]
    
    // 写电平校准寄存器
    val wlevelEn = busCtrl.createReadAndWrite(Bool(), 0x10, 0)       // [4] 写电平使能
    val wlevelStrobe = busCtrl.createWriteOnly(Bool(), 0x14)         // [5] 写电平触发
    val wlevelDone = busCtrl.createReadOnly(Bool(), 0x6C)            // 校准完成状态 [35]
    
    // 命令延迟控制寄存器
    val cdlyRst = busCtrl.createWriteOnly(Bool(), 0x18)         // [6] 延迟线复位
    val cdlyInc = busCtrl.createWriteOnly(Bool(), 0x1C)         // [7] 延迟线增量
    val cdlyValue = busCtrl.createReadOnly(UInt(9 bits), 0x20)  // [8:16] 当前延迟值（只读）
    
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
    val wdlyDqsIncCount = busCtrl.createReadOnly(UInt(9 bits), 0x48)
    
    // Write Bitslip
    val wdlyDqBitslipRst = busCtrl.createWriteOnly(Bool(), 0x4C)
    val wdlyDqBitslip = busCtrl.createWriteOnly(Bool(), 0x50)
    
    // Phase Control
    val rdPhase = busCtrl.createReadAndWrite(UInt(2 bits), 0x54) init(0)
    val wrPhase = busCtrl.createReadAndWrite(UInt(2 bits), 0x58) init(0)

    // Hardware Connections
    io.ctrl.reset := resetReg
    io.ctrl.initDone := initDone
    
    // 连接控制信号到PHY接口
    io.phyCtrl.en_vtc       := enVtcReg
    io.phyCtrl.wlevel_en    := wlevelEn
    io.phyCtrl.cdly_rst     := cdlyRst
    io.phyCtrl.cdly_inc     := cdlyInc
    
    // 连接读延迟控制
    io.phyCtrl.rdly_dq_rst         := rdlyDqRst
    io.phyCtrl.rdly_dq_inc         := rdlyDqInc
    io.phyCtrl.rdly_dq_bitslip_rst := rdlyDqBitslipRst
    io.phyCtrl.rdly_dq_bitslip     := rdlyDqBitslip
    
    // 连接写延迟控制
    io.phyCtrl.wdly_dq_rst         := wdlyDqRst
    io.phyCtrl.wdly_dq_inc         := wdlyDqInc
    io.phyCtrl.wdly_dqs_rst        := wdlyDqsRst
    io.phyCtrl.wdly_dqs_inc        := wdlyDqsInc
    
    // 连接相位控制
    io.phyCtrl.rdphase := rdPhase
    io.phyCtrl.wrphase := wrPhase
    
    // 连接状态信号（方向为out）
    halfSys8xTaps      := io.phyCtrl.half_sys8x_taps
    wdlyDqsIncCount    := io.phyCtrl.wdly_dqs_inc_count
    cdlyValue          := io.phyCtrl.cdly_value
    
    // 连接其他控制信号
    io.phyCtrl.wdly_dq_bitslip_rst := wdlyDqBitslipRst
    io.phyCtrl.wdly_dq_bitslip     := wdlyDqBitslip
    io.phyCtrl.dly_sel             := dlySel

    // 删除旧的phy对象引用
  }

  // Command path
  val cmdPath = new Area {
    val cmdSignals = Vec(
      io.dfi.control.address,
      io.dfi.control.bank,
      io.dfi.control.rasN,
      io.dfi.control.casN,
      io.dfi.control.weN
    ).flatMap(_.asBools)

    val oserdesVec = Seq.fill(cmdSignals.length)(new Oserdese3BlackBox())
    for((osd,sig) <- oserdesVec.zip(cmdSignals)){
      osd.io.RST    := io.ctrl.reset
      osd.io.CLK    := io.pads.clk4x
      osd.io.CLKDIV := ClockDomain.current.readClockWire
      osd.io.D      := sig.asBits #* 8
    }
  }

  // Data path
  val dataPath = new Area {
    // DQS pattern生成模块
    val dqsPattern = new Area {
      val dqs = Reg(Bool())
      val dqs_n = Reg(Bool())
      val phase = io.phyCtrl.wrphase
      
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
    
    val dqOserdes = Seq.fill(dfiConfig.dataWidth)(new Oserdese3BlackBox())
    for((osd, data) <- dqOserdes.zip(wrData.asBools)){
      osd.io.D := data.asBits #* 8
      osd.io.T_OUT := wrDataCsN.map(_.asBools.head).getOrElse(wrDataMask.asBools.head) // 显式转换为Bool
    }

    // Read path
    val rdData = io.dfi.read.rd(0).rddata
    for((osd, data) <- dqOserdes.zip(rdData.asBools)){
      data := osd.io.OQ
    }

    // DQS输出连接
    io.pads.dqs_p := dqsPattern.dqs.asBits
    io.pads.dqs_n := dqsPattern.dqs_n.asBits
  }


  // Training FSM
  val trainingFSM = new Area {
    val initDoneReg = RegInit(False)
    val calibDoneReg = RegInit(False)

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
        whenIsNext(stateCalibration)
      }

      val stateCalibration = new State {
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