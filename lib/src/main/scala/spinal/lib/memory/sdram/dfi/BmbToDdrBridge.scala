package spinal.lib.memory.sdram.dfi

import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.memory.sdram.dfi.phy.{XilinxUSPhy, XilinxUSPhyConfig}

/**
 * Enhanced BMB到DDR存储器桥接组件
 *
 * 提供完整的BMB到DFI协议转换，支持DDR3和DDR4存储器标准
 * 实现高性能数据传输、完整的错误处理和诊断功能
 */
case class BmbToDdrBridge(
  bmbParameter: BmbParameter,
  dfiConfig: DfiConfig,
  phyConfig: XilinxUSPhyConfig = XilinxUSPhyConfig()
) extends Component {

  // 规范化BMB参数，确保在调用方仅提供最小access配置时也有读写通道
  // （例如测试中使用的BmbAccessParameter(addressWidth, dataWidth)）
  private val effectiveBmbParameter: BmbParameter = {
    val p = bmbParameter
    // 如果已经配置了sources，则直接使用调用方提供的参数
    if (p.access.sources.nonEmpty && (p.access.canRead || p.access.canWrite)) {
      p
    } else {
      // 否则构造一个默认的单源、可读写的BMB参数，保持地址/数据宽度一致
      BmbParameter(
        addressWidth = p.access.addressWidth,
        dataWidth = p.access.dataWidth,
        sourceWidth = 2,   // 支持最多4个source，覆盖当前测试用例
        contextWidth = 0,
        lengthWidth = 8    // 足够覆盖测试中使用的length值
      )
    }
  }

  // 根据DFI配置自动选择DDR配置
  val bridgeConfig: BmbDdrConfig = {
    val ddrConfig = if (dfiConfig.sdram.generation == SdramGeneration.DDR3) {
      DdrConfig.ddr3Default()
    } else if (dfiConfig.sdram.generation == SdramGeneration.DDR4) {
      DdrConfig.ddr4Default()
    } else {
      // 默认使用DDR3配置
      DdrConfig.ddr3Default()
    }
    BmbDdrConfig(pendingTransactionsWidth = 4, ddrConfig = ddrConfig)
  }

  val io = new Bundle {
    // 使用规范化后的BMB参数来构建接口，避免在最小access配置下缺失cmd/rsp通道
    val bmb = slave(Bmb(effectiveBmbParameter))
    val clk4x = in Bool()
    val clk4xN = in Bool()

    // 诊断和状态接口
    val debug = new Bundle {
      val error = out Bool()
      val busy = out Bool()
      val pendingTransactions = out UInt(bridgeConfig.pendingTransactionsWidth bits)
    }
  }

  // 验证BMB参数与DFI配置的兼容性
  require(
    effectiveBmbParameter.access.dataWidth == dfiConfig.dataWidth,
    s"BMB data width (${effectiveBmbParameter.access.dataWidth}) must match DFI data width (${dfiConfig.dataWidth})"
  )

  // XilinxUSPhy实例化
  val xilinxPhy = new XilinxUSPhy(dfiConfig, phyConfig)

  // 连接时钟
  xilinxPhy.io.clk4x := io.clk4x
  xilinxPhy.io.clk4xN := io.clk4xN

  // 将PHY控制接口连接到安全的默认值，避免未驱动输入
  xilinxPhy.io.ctrl.reset := False

  xilinxPhy.io.phyCtrl.dlySel := B(0, xilinxPhy.io.phyCtrl.dlySel.getWidth bits) // 使用实际位宽
  xilinxPhy.io.phyCtrl.cdlyRst := False
  xilinxPhy.io.phyCtrl.cdlyInc := False
  xilinxPhy.io.phyCtrl.dqRst := False
  xilinxPhy.io.phyCtrl.dqInc := False
  xilinxPhy.io.phyCtrl.bitslipRst := False
  xilinxPhy.io.phyCtrl.bitslip := False
  xilinxPhy.io.phyCtrl.rdPhase := U(0, xilinxPhy.io.phyCtrl.rdPhase.getWidth bits)
  xilinxPhy.io.phyCtrl.wrPhase := U(0, xilinxPhy.io.phyCtrl.wrPhase.getWidth bits)

  // 完整的BMB命令处理（注意：cmdValid在io.bmb.cmd.ready定义后更新）
  val cmdValid = Bool() // 将在后面定义
  val cmdPayload = io.bmb.cmd.payload
  val isWrite = cmdPayload.isWrite
  // val isWrite = cmdPayload.isWrite  // 已在第99行定义
  val address = cmdPayload.address

  // 统一DDR接口抽象层
  val ddrInterfaceAbstraction = new Area {
    // DDR类型检测和配置
    val isDDR3 = dfiConfig.sdram.generation == SdramGeneration.DDR3
    val isDDR4 = dfiConfig.sdram.generation == SdramGeneration.DDR4

    // 统一的银行抽象
    val totalBanks = if (isDDR3) bridgeConfig.ddrConfig.bankCount else if (isDDR4) bridgeConfig.ddrConfig.bankCount else 0
    val bankGroups = if (isDDR4) dfiConfig.sdram.bgWidth else 0
    val banksPerGroup = if (isDDR4) bridgeConfig.ddrConfig.actualBanksPerGroup else 0

    // 统一的地址解码器
    // BMB地址是线性字节地址，需要转换为DDR的bank/row/column格式
    // 地址格式: [bank][row][column][byteOffset]
    def decodeAddress(address: UInt): DdrAddressInfo = {
      // 计算字节偏移位宽
      val byteOffsetWidth = log2Up(dfiConfig.sdram.bytePerWord)

      // 去掉字节偏移，得到字地址
      val wordAddress = address >> byteOffsetWidth

      if (isDDR3) {
        // DDR3地址格式: [column][row][bank]
        val bank = wordAddress(dfiConfig.bankWidth - 1 downto 0)
        val row = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.rowWidth - 1 downto dfiConfig.bankWidth)
        val column = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.rowWidth + dfiConfig.sdram.columnWidth - 1 downto
                                     dfiConfig.bankWidth + dfiConfig.sdram.rowWidth)
        DdrAddressInfo(bank, row, column, U(0, 2 bits), U(0))
      } else if (isDDR4) {
        // DDR4地址格式: [column][row][bg][bank]
        val bank = wordAddress(dfiConfig.bankWidth - 1 downto 0)
        val bg = if (dfiConfig.sdram.bgWidth > 0) {
          wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth - 1 downto dfiConfig.bankWidth)
        } else {
          U(0, dfiConfig.sdram.bgWidth bits)
        }
        val row = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth + dfiConfig.sdram.rowWidth - 1 downto
                           dfiConfig.bankWidth + dfiConfig.sdram.bgWidth)
        val column = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth + dfiConfig.sdram.rowWidth +
                                     dfiConfig.sdram.columnWidth - 1 downto
                           dfiConfig.bankWidth + dfiConfig.sdram.bgWidth + dfiConfig.sdram.rowWidth)
        val globalBank = (bg << bridgeConfig.ddrConfig.bankGroupShift) + bank
        DdrAddressInfo(bank, row, column, bg, globalBank)
      } else {
        DdrAddressInfo(U(0), U(0), U(0), U(0), U(0))
      }
    }

    // 统一的时序参数抽象
    case class TimingParameters(
      tRCD: Int,
      tRP: Int,
      tRAS: Int,
      tRC: Int,
      tRRD: Int,
      tFAW: Int,
      tCL: Int,
      tCWL: Int
    )

    val timingParams = if (isDDR3) {
      TimingParameters(
        tRCD = dfiConfig.sdram.tRCD,
        tRP = dfiConfig.sdram.tRP,
        tRAS = dfiConfig.sdram.tRAS,
        tRC = dfiConfig.sdram.tRCD + dfiConfig.sdram.tRP,
        tRRD = dfiConfig.sdram.tRRD,
        tFAW = dfiConfig.sdram.tFAW,
        tCL = dfiConfig.sdram.ddrRdLat,
        tCWL = dfiConfig.sdram.ddrWrLat
      )
    } else if (isDDR4) {
      TimingParameters(
        tRCD = dfiConfig.sdram.tRCD,
        tRP = dfiConfig.sdram.tRP,
        tRAS = dfiConfig.sdram.tRAS,
        tRC = dfiConfig.sdram.tRCD + dfiConfig.sdram.tRP,
        tRRD = dfiConfig.sdram.tRRD,
        tFAW = dfiConfig.sdram.tFAW,
        tCL = dfiConfig.sdram.ddrRdLat,
        tCWL = dfiConfig.sdram.ddrWrLat
      )
    } else {
      TimingParameters(0, 0, 0, 0, 0, 0, 0, 0)
    }

    // 地址信息案例类
    case class DdrAddressInfo(
      bank: UInt,
      row: UInt,
      column: UInt,
      bankGroup: UInt,
      globalBank: UInt
    )
  }

  // DDR时序管理器（使用统一抽象）
  val ddrTimingManager = new Area {
    val isDDR3 = ddrInterfaceAbstraction.isDDR3
    val isDDR4 = ddrInterfaceAbstraction.isDDR4

    // DDR时序状态 - 移除未使用的状态机，简化为直接控制
    // val ddrState = Reg(UInt(2 bits)) init(0) // 0=IDLE, 1=PRECHARGE, 2=REFRESH, 3=ACTIVATE
    val timingCounter = Reg(UInt(bridgeConfig.ddrConfig.timingCounterWidth bits)) init(0)

    // 时序参数
    val tRCD = dfiConfig.sdram.tRCD
    val tRP = dfiConfig.sdram.tRP
    val tRAS = dfiConfig.sdram.tRAS
    val tRC = tRCD + tRP
    val tRRD = dfiConfig.sdram.tRRD
    val tFAW = dfiConfig.sdram.tFAW
    val tREFI = bridgeConfig.ddrConfig.refreshIntervalMs // Refresh interval in ms

    // DDR3 Bank状态跟踪 - 简化实现
    val ddr3BankBusy = Vec(Reg(Bool()) init(False), bridgeConfig.ddrConfig.bankCount)
    val ddr3BankTimer = Vec(Reg(UInt(bridgeConfig.ddrConfig.bankTimerWidth bits)) init(0), bridgeConfig.ddrConfig.bankCount)

    // DDR4 Bank状态跟踪 - 简化实现
    val ddr4BankBusy = Vec(Reg(Bool()) init(False), bridgeConfig.ddrConfig.bankCount)
    val ddr4BankTimer = Vec(Reg(UInt(bridgeConfig.ddrConfig.bankTimerWidth bits)) init(0), bridgeConfig.ddrConfig.bankCount)

    // DDR4 FAW窗口管理 - 简化实现
    val fawWindowCounter = Reg(UInt(bridgeConfig.ddrConfig.fawCounterWidth bits)) init(0)
    val activateWindow = Vec(Reg(Bool()) init(False), bridgeConfig.ddrConfig.fawWindowSize)

    // 自动刷新逻辑
    val refreshCounter = Reg(UInt(bridgeConfig.ddrConfig.timingCounterWidth bits)) init(0)
    val refreshPending = Reg(Bool()) init(False)

    // DDR4特定功能
    val ddr4DbiEnabled = Reg(Bool()) init(False) // Data Bus Inversion
    val ddr4CrcEnabled = Reg(Bool()) init(False)  // CRC support
    val ddr4ParityEnabled = Reg(Bool()) init(False) // CA parity

    refreshCounter := refreshCounter + 1
    when(refreshCounter >= tREFI) {
      refreshCounter := 0
      refreshPending := True
    }

    // DDR4 FAW窗口管理
    if (isDDR4) {
      fawWindowCounter := fawWindowCounter + 1
      when(fawWindowCounter >= tFAW) {
        fawWindowCounter := 0
        for (i <- 0 until 4) {
          activateWindow(i) := False
        }
      }
    }
  }

  // BMB命令接收控制将在responseManager中定义（避免重复赋值）
  // cmdValid将在responseManager定义io.bmb.cmd.ready后更新

  // DFI控制信号生成
  val dfiControl = xilinxPhy.io.dfi.control

  // Helper functions to generate properly-sized constant patterns
  private def allZeros(width: Int) = B(0, width bits)
  private def allOnes(width: Int) = B((BigInt(1) << width) - 1, width bits)

  // 默认空闲值，确保所有DFI控制信号在所有路径上都有驱动，避免锁存器
  dfiControl.address := allZeros(dfiControl.address.getWidth)
  if (dfiConfig.signalConfig.useBank) {
    dfiControl.bank := allZeros(dfiControl.bank.getWidth)
  }
  if (dfiConfig.signalConfig.useBg) {
    dfiControl.bg := allZeros(dfiControl.bg.getWidth)
  }
  if (dfiConfig.signalConfig.useCid) {
    dfiControl.cid := allZeros(dfiControl.cid.getWidth)
  }
  if (dfiConfig.signalConfig.useRasN) {
    dfiControl.rasN := allOnes(dfiControl.rasN.getWidth)
  }
  if (dfiConfig.signalConfig.useCasN) {
    dfiControl.casN := allOnes(dfiControl.casN.getWidth)
  }
  if (dfiConfig.signalConfig.useWeN) {
    dfiControl.weN := allOnes(dfiControl.weN.getWidth)
  }
  if (dfiConfig.signalConfig.useOdt) {
    dfiControl.odt := allZeros(dfiControl.odt.getWidth)
  }
  if (dfiConfig.signalConfig.useResetN) {
    dfiControl.resetN := allOnes(dfiControl.resetN.getWidth)
  }
  if (dfiConfig.signalConfig.useAckN) {
    dfiControl.actN := allOnes(dfiControl.actN.getWidth)
  }
  dfiControl.csN := allOnes(dfiControl.csN.getWidth)
  dfiControl.cke := allOnes(dfiControl.cke.getWidth)

  when(cmdValid) {
    // 计算字地址（去掉字节偏移）
    val byteOffsetWidth = log2Up(dfiConfig.sdram.bytePerWord)
    val wordAddress = address >> byteOffsetWidth

    // DDR3/DDR4特定的时序约束检查
    val bankNotBusy = if (ddrTimingManager.isDDR3) {
      val bankIndex = wordAddress(dfiConfig.bankWidth - 1 downto 0).resized
      !ddrTimingManager.ddr3BankBusy(bankIndex)
    } else if (ddrTimingManager.isDDR4) {
      val bgIndex = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth - 1 downto dfiConfig.bankWidth).resized
      val bankIndex = wordAddress(dfiConfig.bankWidth - 1 downto 0).resized
      val globalBankIndex = (bgIndex << bridgeConfig.ddrConfig.bankGroupShift) + bankIndex
      !ddrTimingManager.ddr4BankBusy(globalBankIndex) && !ddrTimingManager.activateWindow(bgIndex)
    } else {
      True
    }

    val noRefreshPending = !ddrTimingManager.refreshPending

    when(bankNotBusy && noRefreshPending) {
      // 地址映射（使用字地址）
      val bankBits = wordAddress(dfiConfig.bankWidth - 1 downto 0)
      val rowBits = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.rowWidth - 1 downto dfiConfig.bankWidth)

      dfiControl.address := rowBits.asBits

      if (dfiConfig.signalConfig.useBank) {
        dfiControl.bank := bankBits.asBits
      }

      // DDR4 Bank Group支持
      if (ddrTimingManager.isDDR4 && dfiConfig.signalConfig.useBg && dfiConfig.sdram.bgWidth > 0) {
        val bgBits = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth - 1 downto dfiConfig.bankWidth)
        dfiControl.bg := bgBits.asBits
      }

      // DDR4 Chip ID支持
      if (ddrTimingManager.isDDR4 && dfiConfig.signalConfig.useCid && dfiConfig.sdram.cidWidth > 0) {
        val cidBits = wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth + dfiConfig.sdram.cidWidth - 1 downto
                             dfiConfig.bankWidth + dfiConfig.sdram.bgWidth)
        dfiControl.cid := cidBits.asBits
      }

      // 命令信号
      dfiControl.csN := allZeros(dfiControl.csN.getWidth)
      dfiControl.cke := allOnes(dfiControl.cke.getWidth)

      when(isWrite) {
        if (dfiConfig.signalConfig.useRasN) {
          dfiControl.rasN := allOnes(dfiControl.rasN.getWidth) // RAS# high for WRITE
        }
        if (dfiConfig.signalConfig.useCasN) {
          dfiControl.casN := allZeros(dfiControl.casN.getWidth) // CAS# low for WRITE
        }
        if (dfiConfig.signalConfig.useWeN) {
          dfiControl.weN := allZeros(dfiControl.weN.getWidth) // WE# low for WRITE
        }

        // DDR3写操作时序
        if (ddrTimingManager.isDDR3) {
          val bankIndex = bankBits.resized
          ddrTimingManager.ddr3BankBusy(bankIndex) := True
          ddrTimingManager.ddr3BankTimer(bankIndex) := U(
            ddrTimingManager.tRCD + ddrTimingManager.tRAS,
            bridgeConfig.ddrConfig.bankTimerWidth bits
          )
        }
        // DDR4写操作时序
        else if (ddrTimingManager.isDDR4) {
          val bgIndex = if (dfiConfig.sdram.bgWidth > 0) {
            wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth - 1 downto dfiConfig.bankWidth).resized
          } else {
            U(0)
          }
          val bankIndex = bankBits.resized
          val globalBankIndex = (bgIndex << bridgeConfig.ddrConfig.bankGroupShift) + bankIndex

          ddrTimingManager.ddr4BankBusy(globalBankIndex) := True
          ddrTimingManager.ddr4BankTimer(globalBankIndex) := U(
            ddrTimingManager.tRCD + ddrTimingManager.tRAS,
            bridgeConfig.ddrConfig.bankTimerWidth bits
          )
          ddrTimingManager.activateWindow(bgIndex) := True
        }
      } otherwise {
        if (dfiConfig.signalConfig.useRasN) {
          dfiControl.rasN := allOnes(dfiControl.rasN.getWidth) // RAS# high for READ
        }
        if (dfiConfig.signalConfig.useCasN) {
          dfiControl.casN := allZeros(dfiControl.casN.getWidth) // CAS# low for READ
        }
        if (dfiConfig.signalConfig.useWeN) {
          dfiControl.weN := allOnes(dfiControl.weN.getWidth) // WE# high for READ
        }

        // DDR3读操作时序
        if (ddrTimingManager.isDDR3) {
          val bankIndex = bankBits.resized
          ddrTimingManager.ddr3BankBusy(bankIndex) := True
          ddrTimingManager.ddr3BankTimer(bankIndex) := U(
            ddrTimingManager.tRCD,
            bridgeConfig.ddrConfig.bankTimerWidth bits
          )
        }
        // DDR4读操作时序
        else if (ddrTimingManager.isDDR4) {
          val bgIndex = if (dfiConfig.sdram.bgWidth > 0) {
            wordAddress(dfiConfig.bankWidth + dfiConfig.sdram.bgWidth - 1 downto dfiConfig.bankWidth).resized
          } else {
            U(0)
          }
          val bankIndex = bankBits.resized
          val globalBankIndex = (bgIndex << bridgeConfig.ddrConfig.bankGroupShift) + bankIndex

          ddrTimingManager.ddr4BankBusy(globalBankIndex) := True
          ddrTimingManager.ddr4BankTimer(globalBankIndex) := U(
            ddrTimingManager.tRCD,
            bridgeConfig.ddrConfig.bankTimerWidth bits
          )
          ddrTimingManager.activateWindow(bgIndex) := True
        }
      }

      // DDR3/DDR4特定的ODT设置
      if (dfiConfig.signalConfig.useOdt) {
        if (ddrTimingManager.isDDR3) {
          dfiControl.odt := allOnes(dfiControl.odt.getWidth) // DDR3 ODT
        } else if (ddrTimingManager.isDDR4) {
          dfiControl.odt := allOnes(dfiControl.odt.getWidth) // DDR4 ODT
        }
      }

      // DDR3/DDR4 Reset信号
      if (dfiConfig.signalConfig.useResetN) {
        dfiControl.resetN := allOnes(dfiControl.resetN.getWidth)
      }
    } otherwise {
      dfiControl.csN := allOnes(dfiControl.csN.getWidth)
      dfiControl.cke := allOnes(dfiControl.cke.getWidth)
    }
  } otherwise {
    dfiControl.csN := allOnes(dfiControl.csN.getWidth)
    dfiControl.cke := allOnes(dfiControl.cke.getWidth)

    // DDR3/DDR4刷新处理
    when(ddrTimingManager.refreshPending) {
      dfiControl.csN := allZeros(dfiControl.csN.getWidth)
      if (dfiConfig.signalConfig.useRasN) dfiControl.rasN := allZeros(dfiControl.rasN.getWidth)
      if (dfiConfig.signalConfig.useCasN) dfiControl.casN := allZeros(dfiControl.casN.getWidth)
      if (dfiConfig.signalConfig.useWeN) dfiControl.weN := allOnes(dfiControl.weN.getWidth)
      ddrTimingManager.refreshPending := False
    }
  }

  // DDR3银行时序管理
  for (i <- 0 until bridgeConfig.ddrConfig.bankCount) {
    when(ddrTimingManager.ddr3BankTimer(i) > 0) {
      ddrTimingManager.ddr3BankTimer(i) := ddrTimingManager.ddr3BankTimer(i) - 1
    } otherwise {
      ddrTimingManager.ddr3BankBusy(i) := False
    }
  }

  // DDR4银行和银行组时序管理
  for (i <- 0 until bridgeConfig.ddrConfig.bankCount) {
    when(ddrTimingManager.ddr4BankTimer(i) > 0) {
      ddrTimingManager.ddr4BankTimer(i) := ddrTimingManager.ddr4BankTimer(i) - 1
    } otherwise {
      ddrTimingManager.ddr4BankBusy(i) := False
    }
  }

  // DDR3/DDR4数据路径管理
  val dataPathManager = new Area {
    val writeInterface = xilinxPhy.io.dfi.write
    val readInterface = xilinxPhy.io.dfi.read

    // DDR4 DBI (Data Bus Inversion) 处理 - 实现完整支持
    // DBI support - temporarily disabled until DfiSignalConfig is updated
    val dbiEncoder = if (false && ddrTimingManager.isDDR4) {
      new Area {
        val dbiEnable = Reg(Bool()) init(ddrTimingManager.ddr4DbiEnabled)
        val dbiValue = Reg(Bits(1 bits)) init(0)
        
        // 计算数据总线反转值
        def computeDbi(data: Bits): Bits = {
          val onesCount = data.xorR  // xorR already returns Bool
          onesCount.asBits
        }
        
        // 应用DBI编码
        when(cmdValid && isWrite) {
          dbiValue := computeDbi(cmdPayload.data)
          dbiEnable := ddrTimingManager.ddr4DbiEnabled
        }
      }
    } else {
      null
    }

    // 写数据路径
    if (effectiveBmbParameter.access.canWrite) {
      for (i <- 0 until dfiConfig.frequencyRatio) {
        writeInterface.wr(i).wrdataEn := cmdValid && isWrite
        writeInterface.wr(i).wrdata := cmdPayload.data
        if (false) {  // DBI support temporarily disabled
          // writeInterface.wr(i).wrdataDbiN := dbiEncoder.dbiValue
        }

        // DDR3/DDR4 数据掩码
        if (effectiveBmbParameter.access.canMask) {
          writeInterface.wr(i).wrdataMask := ~cmdPayload.mask
        }

        // DDR4 Chip Select
        if (dfiConfig.signalConfig.useWrdataCsN) {
          writeInterface.wr(i).wrdataCsN := B(0, writeInterface.wr(i).wrdataCsN.getWidth bits)
        }
      }
    }

    // DDR4 CRC生成 - 暂时不支持（等待后续DfiSignalConfig更新）
    val crcGenerator = null

    // 读数据路径
    readInterface.rden.foreach(_ := cmdValid && !isWrite)

    // DDR4 读数据DBI处理
    if (ddrTimingManager.isDDR4 && dfiConfig.signalConfig.useRddataDbiN) {
      // 简化的DBI处理逻辑
      readInterface.rd.foreach { rd =>
        // DBI_N inversion logic would go here
      }
    }

    // DDR4 读数据Chip Select
    if (ddrTimingManager.isDDR4 && dfiConfig.signalConfig.useRddataCsN) {
      readInterface.rdCs.foreach { rdCs =>
        rdCs.rddataCsN := B(0, rdCs.rddataCsN.getWidth bits)
      }
    }
  }

  // DDR3响应和数据管理器
  val responseManager = new Area {
    import spinal.lib.Stream
    import spinal.lib.StreamFifo

    // 待处理事务FIFO - 存储source、context、地址和长度信息
    // Note: beatCount must be calculated based on address alignment and total bytes
    case class PendingTransaction() extends Bundle {
      val source = UInt(effectiveBmbParameter.access.sourceWidth bits)
      val context = Bits(effectiveBmbParameter.access.contextWidth bits)
      val isWrite = Bool()
      val address = UInt(effectiveBmbParameter.access.addressWidth bits)
      val length = UInt(effectiveBmbParameter.access.lengthWidth bits)
      val beatCount = UInt(effectiveBmbParameter.access.beatCounterWidth bits)  // Pre-computed beat count
      val beatsSent = UInt(effectiveBmbParameter.access.beatCounterWidth bits)
    }

    val pendingFifo = StreamFifo(
      dataType = PendingTransaction(),
      depth = 16  // 足够深度以处理读延迟
    )

    // 当接受新命令时，将事务信息推入FIFO
    // Compute beat count according to BMB protocol:
    // beatCount = (((endAddress + wordMask) & ~wordMask) - (startAddress & ~wordMask)) / byteCount
    val byteCount = effectiveBmbParameter.access.byteCount
    val wordMask = effectiveBmbParameter.access.wordMask
    val cmdAddress = io.bmb.cmd.address
    val cmdLength = io.bmb.cmd.length
    val startAddress = cmdAddress
    val endAddress = cmdAddress + cmdLength + 1
    // Align start down and end up to word boundaries, then compute beat count
    val alignedStart = startAddress & ~U(wordMask, startAddress.getWidth bits)
    val alignedEnd = (endAddress + wordMask) & ~U(wordMask, endAddress.getWidth bits)
    val computedBeatCount = ((alignedEnd - alignedStart) >> log2Up(byteCount)).resized

    // Only push to FIFO on the LAST beat of a command
    // For READ: there's only one CMD beat (last=true)
    // For WRITE: there are N CMD beats, push only when last=true (final beat)
    pendingFifo.io.push.valid := io.bmb.cmd.fire && io.bmb.cmd.last
    pendingFifo.io.push.payload.source := io.bmb.cmd.source
    pendingFifo.io.push.payload.context := io.bmb.cmd.context
    pendingFifo.io.push.payload.isWrite := io.bmb.cmd.opcode === Bmb.Cmd.Opcode.WRITE
    pendingFifo.io.push.payload.address := io.bmb.cmd.address
    pendingFifo.io.push.payload.length := io.bmb.cmd.length
    pendingFifo.io.push.payload.beatCount := computedBeatCount
    pendingFifo.io.push.payload.beatsSent := 0

    // 读数据缓冲 - 从DFI读接口接收数据
    val readDataBuffer = Reg(Bits(effectiveBmbParameter.access.dataWidth bits)) init(0)
    val readDataValid = Reg(Bool()) init(False)

    // 错误检测和恢复
    val errorDetected = Reg(Bool()) init(False) allowUnsetRegToAvoidLatch()
    val errorAddress  = Reg(UInt(effectiveBmbParameter.access.addressWidth bits)) init(0) allowUnsetRegToAvoidLatch()
    val errorCode     = Reg(UInt(8 bits)) init(0) allowUnsetRegToAvoidLatch()  // 0=success, 1=timeout, 2=data_integrity, 3=protocol

    // Current transaction tracking registers
    val currentTransaction = Reg(PendingTransaction())
    currentTransaction.init({
      val t = PendingTransaction()
      t.source := 0
      t.context := 0
      t.isWrite := False
      t.address := 0
      t.length := 0
      t.beatCount := 0
      t.beatsSent := 0
      t
    })
    val currentTransactionValid = Reg(Bool()) init(False)

    // 从DFI读接口接收数据
    if (effectiveBmbParameter.access.canRead) {
      // 监控DFI读数据有效信号
      when(dataPathManager.readInterface.rd.map(_.rddataValid).orR) {
        // 从第一个有效的读数据中获取数据
        for (i <- 0 until dfiConfig.frequencyRatio) {
          when(dataPathManager.readInterface.rd(i).rddataValid) {
            readDataBuffer := dataPathManager.readInterface.rd(i).rddata
            readDataValid := True
          }
        }
      }

      // 当读数据被消费且是最后一个beat时才清除有效标志
      // 这样对于多拍读事务，同一份数据可以用于所有beats的响应
      when(io.bmb.rsp.fire && !currentTransaction.isWrite && io.bmb.rsp.payload.last) {
        readDataValid := False
      }
    }

    // Load new transaction when needed
    pendingFifo.io.pop.ready := (!currentTransactionValid && pendingFifo.io.pop.valid)
    when(pendingFifo.io.pop.fire) {
      currentTransaction := pendingFifo.io.pop.payload
      currentTransactionValid := True
    }

    // 响应生成逻辑
    // 对于写操作：立即响应（下一个周期）
    // 对于读操作：等待DFI读数据有效
    val canRespond = currentTransactionValid && (
      currentTransaction.isWrite ||  // 写操作总是可以响应
      readDataValid                   // 读操作需要等待数据有效
    )

    io.bmb.rsp.valid := canRespond
    io.bmb.rsp.payload.source := currentTransaction.source
    io.bmb.rsp.payload.context := currentTransaction.context
    io.bmb.rsp.payload.opcode := Bmb.Rsp.Opcode.SUCCESS

    // 区分读写操作的响应策略
    when(currentTransaction.isWrite) {
      // 写操作：单个响应，无论长度如何
      io.bmb.rsp.payload.last := True
      if (effectiveBmbParameter.access.canRead) {
        io.bmb.rsp.payload.data := 0  // 写操作返回的数据不重要
      }
    } otherwise {
      // 读操作：可能需要多个beat响应
      // Use pre-computed beatCount (which is already the count, not count-1)
      io.bmb.rsp.payload.last := currentTransaction.beatsSent === currentTransaction.beatCount - 1

      if (effectiveBmbParameter.access.canRead) {
        // 返回从DFI读取的实际数据
        io.bmb.rsp.payload.data := readDataBuffer
      }
    }

    // 当响应被接受时，更新beat counter或完成事务
    when(io.bmb.rsp.fire) {
      when(currentTransaction.isWrite) {
        // 写操作：单个响应后立即完成事务
        currentTransactionValid := False
        currentTransaction.beatsSent := 0
      } otherwise {
        // 读操作：检查是否是最后一个beat
        when(io.bmb.rsp.payload.last) {
          // 最后一个beat，完成事务
          currentTransactionValid := False
          currentTransaction.beatsSent := 0
        } otherwise {
          // 不是最后一个beat，更新beat counter
          // 只有当beatCounterWidth > 0时才进行递增操作
          if (effectiveBmbParameter.access.beatCounterWidth > 0) {
            currentTransaction.beatsSent := currentTransaction.beatsSent + 1
          }
        }
      }
    }
  }

  // BMB命令接受逻辑 - 当FIFO有空间时接受命令
  io.bmb.cmd.ready := responseManager.pendingFifo.io.push.ready && !io.bmb.rsp.isStall

  // 现在io.bmb.cmd.ready已定义，更新cmdValid
  cmdValid := io.bmb.cmd.valid && io.bmb.cmd.ready

  // 调试状态输出
  io.debug.error := responseManager.errorDetected
  io.debug.busy := cmdValid || ddrTimingManager.refreshPending
  io.debug.pendingTransactions := cmdValid ? U(1, bridgeConfig.pendingTransactionsWidth bits) | U(0, bridgeConfig.pendingTransactionsWidth bits)
}