package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}

case class BmbBridge(bmbp: BmbParameter, taskConfig: TaskConfig, dfiConfig: DfiConfig, addrMap: AddrMap)
    extends Component {

  val io = new Bundle {
    val bmb = slave(Bmb(bmbp))
    val taskPort = master(TaskPort(taskConfig, dfiConfig))
    // Pass-through of the ctrlmsg training pause to MakeTask (present only when the ctrlmsg
    // channel is enabled, see DfiSignalConfig.useCtrlMsg).
    val trainBusy = dfiConfig.signalConfig.useCtrlMsg generate in(Bool())
  }

  val bmbAdapter = BmbAdapter(bmbp, taskConfig, dfiConfig)
  bmbAdapter.io.input <> io.bmb
  bmbAdapter.io.output.writeData >-> io.taskPort.writeData
  bmbAdapter.io.output.rsp <-< io.taskPort.rsp

  val maketask = MakeTask(taskConfig, dfiConfig, addrMap)
  if (dfiConfig.signalConfig.useCtrlMsg) maketask.io.trainBusy := io.trainBusy
  maketask.io.cmd <-< bmbAdapter.io.output.cmd
  maketask.io.writeDataToken <-< bmbAdapter.io.output.writeDataToken
  maketask.io.output <> io.taskPort.tasks
  RegNext(maketask.io.halt) <> bmbAdapter.io.halt
}
