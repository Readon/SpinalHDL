//package spinal.lib.memory
package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._

case class DfiControlInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val address = Bits(config.addressWidth * config.frequencyRatio bits)
  val bank = config.useBank generate Bits(config.bankWidth * config.frequencyRatio bits)
  val rasN = config.useRasN generate Bits(config.controlWidth * config.frequencyRatio bits)
  val casN = config.useCasN generate Bits(config.controlWidth * config.frequencyRatio bits)
  val weN = config.useWeN generate Bits(config.controlWidth * config.frequencyRatio bits)
  val csN = Bits(config.chipSelectNumber * config.frequencyRatio bits)
  val actN = config.useAckN generate Bits(config.frequencyRatio bits)
  val bg = config.useBg generate Bits(config.bankGroupWidth * config.frequencyRatio bits)
  val cid = config.useCid generate Bits(config.chipIdWidth * config.frequencyRatio bits)
  val cke = Bits(config.chipSelectNumber * config.frequencyRatio bits)
  val odt = config.useOdt generate Bits(config.chipSelectNumber * config.frequencyRatio bits)
  val resetN = config.useResetN generate Bits(config.chipSelectNumber * config.frequencyRatio bits)

  override def asMaster(): Unit = {
    out(address, bank, rasN, casN, weN, csN, actN, bg, cid, cke, odt, resetN)
  }

  def <<(that: DfiControlInterface): Unit = {
    this.address := that.address
    if (config.useBank) this.bank := that.bank
    if (config.useRasN) this.rasN := that.rasN
    if (config.useCasN) this.casN := that.casN
    if (config.useWeN) this.weN := that.weN
    this.csN := that.csN
    if (config.useAckN) this.actN := that.actN
    if (config.useBg) this.bg := that.bg
    if (config.useCid) this.cid := that.cid
    this.cke := that.cke
    if (config.useOdt) this.odt := that.odt
    if (config.useResetN) this.resetN := that.resetN
  }
  def >>(that: DfiControlInterface): Unit = that << this
}

case class DfiWr(config: DfiConfig) extends Bundle {
  val wrdataEn = Bool()
  val wrdata = Bits(config.dataWidth bits)
  val wrdataMask = Bits(config.dataWidth / 8 bits)
  // config.dramDataSlice must >= 8, or else config.dataWidth / 8 / config.dataEnableWidth < 1
  val wrdataCsN = config.useWrdataCsN generate Bits(config.chipSelectNumber bits)
}

case class DfiWriteInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val wr = Vec(DfiWr(config), config.frequencyRatio)
  override def asMaster(): Unit = {
    out(wr)
  }

  def <<(that: DfiWriteInterface): Unit = {
    this.wr := that.wr
  }
  def >>(that: DfiWriteInterface): Unit = that << this
}

case class DfiRd(config: DfiConfig) extends Bundle {
  val rddataValid = Bool()
  val rddata = Bits(config.dataWidth bits)
  val rddataDbiN = config.useRddataDbiN generate Bits(config.dbiWidth bits)
  val rddataDnv = config.useRddataDnv generate Bits(config.dataWidth / 8 bits)
}

case class DfiRdCs(config: DfiConfig) extends Bundle {
  val rddataCsN = config.useRddataCsN generate Bits(config.chipSelectNumber bits)
}

case class DfiReadInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val rden = Vec(Bool(), config.frequencyRatio)
  val rdCs = Vec(DfiRdCs(config), config.frequencyRatio)
  val rd = Vec(DfiRd(config), config.frequencyRatio)
  override def asMaster(): Unit = {
    out(rdCs, rden)
    in(rd)
  }

  def <<(that: DfiReadInterface): Unit = {
    this.rden := that.rden
    this.rdCs := that.rdCs
    that.rd := this.rd
  }
  def >>(that: DfiReadInterface): Unit = that << this
}

case class DfiUpdateInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val ctrlupdAck = config.useCtrlupdReq generate Bool()
  val ctrlupdReq = config.useCtrlupdAck generate Bool()
  val phyupdAck = config.usePhyupdAck generate Bool()
  val phyupdReq = config.usePhyupdReq generate Bool()
  val phyupdType = config.usePhyupdType generate Bits(2 bits)
  override def asMaster(): Unit = {
    out(ctrlupdReq, phyupdAck)
    in(ctrlupdAck, phyupdReq, phyupdType)
  }

  def <<(that: DfiUpdateInterface): Unit = {
    if (config.useCtrlupdReq) this.ctrlupdAck := that.ctrlupdAck
    if (config.useCtrlupdAck) this.ctrlupdReq := that.ctrlupdReq
    if (config.usePhyupdAck) this.phyupdAck := that.phyupdAck
    if (config.usePhyupdReq) this.phyupdReq := that.phyupdReq
    if (config.usePhyupdType) this.phyupdType := that.phyupdType
  }
  def >>(that: DfiUpdateInterface): Unit = that << this
}

case class DfiStatusInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val alertN = config.useAlertN generate Bits(config.alertWidth * config.frequencyRatio bits)
  val dataByteDisable = config.useDataByteDisable generate Bits(config.dataWidth / 8 bits)
  val dramClkDisable = config.useStatusSignals generate Bits(config.chipSelectNumber bits)
  val freqRatio = config.useFreqRatio generate Bits(2 bits)
  val initComplete = config.useInitStart generate Bool()
  val initStart = config.useInitStart generate Bool()
  val parityIn = config.useParityIn generate Bits(config.frequencyRatio bits)
  override def asMaster(): Unit = {
    out(dataByteDisable, dramClkDisable, freqRatio, initStart, parityIn)
    in(initComplete, alertN)
  }

  def <<(that: DfiStatusInterface): Unit = {
    if (config.useAlertN) this.alertN := that.alertN
    if (config.useDataByteDisable) this.dataByteDisable := that.dataByteDisable
    if (config.useStatusSignals) this.dramClkDisable := that.dramClkDisable
    if (config.useFreqRatio) this.freqRatio := that.freqRatio
    if (config.useInitStart) this.initComplete := that.initComplete
    if (config.useInitStart) this.initStart := that.initStart
    if (config.useParityIn) this.parityIn := that.parityIn
  }
  def >>(that: DfiStatusInterface): Unit = that << this
}

case class DfiReadTrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val rdlvlReq = config.useRdlvlReq generate Bits(config.readLevelingPhyIFWidth bits)
  val phyRdlvlCsN = config.usePhyRdlvlCsN generate Bits(config.chipSelectNumber * config.readTrainingPhyIFWidth bits)
  val rdlvlEn = config.useRdlvlEn generate Bits(config.readLevelingMCIFWidth bits)
  val rdlvlResp = config.useRdlvlResp generate Bits(config.readLevelingResponseWidth bits)
  val rdlvlGateReq = config.useRdlvlGateReq generate Bits(config.readLevelingPhyIFWidth bits)
  val phyRdlvlGateCsN =
    config.usePhyRdlvlGateCsN generate Bits(config.readTrainingPhyIFWidth * config.chipSelectNumber bits)
  val rdlvlGateEn = config.useRdlvlGateEn generate Bits(config.readLevelingMCIFWidth bits)
  override def asMaster(): Unit = {
    out(rdlvlEn, rdlvlGateEn)
    in(rdlvlReq, phyRdlvlCsN, rdlvlResp, rdlvlGateReq, phyRdlvlGateCsN)
  }

  def <<(that: DfiReadTrainingInterface): Unit = {
    if (config.useRdlvlReq) this.rdlvlReq := that.rdlvlReq
    if (config.usePhyRdlvlCsN) this.phyRdlvlCsN := that.phyRdlvlCsN
    if (config.useRdlvlEn) this.rdlvlEn := that.rdlvlEn
    if (config.useRdlvlResp) this.rdlvlResp := that.rdlvlResp
    if (config.useRdlvlGateReq) this.rdlvlGateReq := that.rdlvlGateReq
    if (config.usePhyRdlvlGateCsN) this.phyRdlvlGateCsN := that.phyRdlvlGateCsN
    if (config.useRdlvlGateEn) this.rdlvlGateEn := that.rdlvlGateEn
  }
  def >>(that: DfiReadTrainingInterface): Unit = that << this
}

case class DfiWriteTrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val wrlvlReq = config.useWrlvlReq generate Bits(config.writeLevelingPhyIFWidth bits)
  val phyWrlvlCsN = config.usePhyWrlvlCsN generate Bits(config.chipSelectNumber * config.writeLevelingPhyIFWidth bits)
  val wrlvlEn = config.useWrlvlEn generate Bits(config.writeLevelingMCIFWidth bits)
  val wrlvlStrobe = config.useWrlvlStrobe generate Bits(config.writeLevelingMCIFWidth bits)
  val wrlvlResp = config.useWrlvlResp generate Bits(config.writeLevelingResponseWidth bits)
  override def asMaster(): Unit = {
    out(wrlvlEn, wrlvlStrobe)
    in(wrlvlReq, wrlvlResp, phyWrlvlCsN)
  }

  def <<(that: DfiWriteTrainingInterface): Unit = {
    if (config.useWrlvlReq) this.wrlvlReq := that.wrlvlReq
    if (config.usePhyWrlvlCsN) this.phyWrlvlCsN := that.phyWrlvlCsN
    if (config.useWrlvlEn) this.wrlvlEn := that.wrlvlEn
    if (config.useWrlvlStrobe) this.wrlvlStrobe := that.wrlvlStrobe
    if (config.useWrlvlResp) this.wrlvlResp := that.wrlvlResp
  }
  def >>(that: DfiWriteTrainingInterface): Unit = that << this
}

case class DfiCATrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val calvlReq = config.useCalvlReq generate Bits(config.caTrainingPhyIFWidth bits)
  val phyCalvlCsN = config.usePhyCalvlCsN generate Bits(config.chipSelectNumber bits)
  val calvlEn = config.useCalvlEn generate Bits(config.caTrainingMCIFWidth bits)
  val calvlCapture = config.useCalvlCapture generate Bits(config.caTrainingMCIFWidth bits)
  val calvlResp = config.useCalvlResp generate Bits(config.caTrainingResponseWidth bits)
  override def asMaster(): Unit = {
    out(calvlCapture, calvlEn)
    in(phyCalvlCsN, calvlReq, calvlResp)
  }

  def <<(that: DfiCATrainingInterface): Unit = {
    if (config.useCalvlReq) this.calvlReq := that.calvlReq
    if (config.usePhyCalvlCsN) this.phyCalvlCsN := that.phyCalvlCsN
    if (config.useCalvlEn) this.calvlEn := that.calvlEn
    if (config.useCalvlCapture) this.calvlCapture := that.calvlCapture
    if (config.useCalvlResp) this.calvlResp := that.calvlResp
  }
  def >>(that: DfiCATrainingInterface): Unit = that << this
}

case class DfiLevelingTrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val lvlPattern = config.useLvlPattern generate Bits(4 * config.readTrainingPhyIFWidth bits)
  val lvlPeriodic = config.useLvlPeriodic generate Bits(config.levelingPhyIFWidth bits)
  override def asMaster(): Unit = {
    out(lvlPattern, lvlPeriodic)
  }

  def <<(that: DfiLevelingTrainingInterface): Unit = {
    if (config.useLvlPattern) this.lvlPattern := that.lvlPattern
    if (config.useLvlPeriodic) this.lvlPeriodic := that.lvlPeriodic
  }
  def >>(that: DfiLevelingTrainingInterface): Unit = that << this
}

case class DfiPhyRequesetedTrainingInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val phylvlReqCsN = config.usePhylvlReqCsN generate Bits(config.rankWidth bits)
  val phylvlAckCsN = config.usePhylvlAckCsN generate Bits(config.chipSelectNumber bits)

  override def asMaster(): Unit = {
    out(phylvlReqCsN)
    in(phylvlAckCsN)
  }

  def <<(that: DfiPhyRequesetedTrainingInterface): Unit = {
    if (config.usePhylvlReqCsN) this.phylvlReqCsN := that.phylvlReqCsN
    if (config.usePhylvlAckCsN) this.phylvlAckCsN := that.phylvlAckCsN
  }
  def >>(that: DfiPhyRequesetedTrainingInterface): Unit = that << this
}

case class DfiLowPowerControlInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val lpCtrlReq = config.useLpCtrlReq generate Bool()
  val lpDataReq = config.useLpDataReq generate Bool()
  val lpWakeUp = config.useLpWakeUp generate Bits(4 bits)
  val lpAck = config.useLpAck generate Bool()
  override def asMaster(): Unit = {
    out(lpCtrlReq, lpDataReq, lpWakeUp)
    in(lpAck)
  }

  def <<(that: DfiLowPowerControlInterface): Unit = {
    if (config.useLpCtrlReq) this.lpCtrlReq := that.lpCtrlReq
    if (config.useLpDataReq) this.lpDataReq := that.lpDataReq
    if (config.useLpWakeUp) this.lpWakeUp := that.lpWakeUp
    if (config.useLpAck) this.lpAck := that.lpAck
  }
  def >>(that: DfiLowPowerControlInterface): Unit = that << this
}

case class DfiErrorInterface(config: DfiConfig) extends Bundle with IMasterSlave {
  val error = config.useError generate Bits(config.errorNumber bits)
  val error_info = config.useErrorInfo generate Bits(config.errorNumber * 4 bits)
  override def asMaster(): Unit = {
    in(error, error_info)
  }

  def <<(that: DfiErrorInterface): Unit = {
    if (config.useError) this.error := that.error
    if (config.useErrorInfo) this.error_info := that.error_info
  }
  def >>(that: DfiErrorInterface): Unit = that << this
}

case class Dfi(config: DfiConfig) extends Bundle with IMasterSlave {
   val control = DfiControlInterface(config)
   val write = DfiWriteInterface(config)
   val read = DfiReadInterface(config)
   val update = DfiUpdateInterface(config)
   val status = DfiStatusInterface(config)
   val rdTraining = DfiReadTrainingInterface(config)
   val wrTraining = DfiWriteTrainingInterface(config)
   val caTraining = DfiCATrainingInterface(config)
   val levelingTraining = DfiLevelingTrainingInterface(config)
   val phyRequesetedTraining = DfiPhyRequesetedTrainingInterface(config)
   val lowPowerControl = DfiLowPowerControlInterface(config)
   val error = DfiErrorInterface(config)

   override def asMaster(): Unit = {
     master(
       control,
       read,
       status,
       write,
       update,
       rdTraining,
       wrTraining,
       caTraining,
       levelingTraining,
       phyRequesetedTraining,
       lowPowerControl,
       error
     )
   }

   def <<(that: Dfi): Unit = {
     this.control << that.control
     this.write << that.write
     this.read << that.read
     this.update << that.update
     this.status << that.status
     this.rdTraining << that.rdTraining
     this.wrTraining << that.wrTraining
     this.caTraining << that.caTraining
     this.levelingTraining << that.levelingTraining
     this.phyRequesetedTraining << that.phyRequesetedTraining
     this.lowPowerControl << that.lowPowerControl
     this.error << that.error
   }

   def >>(that: Dfi): Unit = that << this
 }
