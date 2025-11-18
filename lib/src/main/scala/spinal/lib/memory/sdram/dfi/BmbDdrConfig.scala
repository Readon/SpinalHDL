package spinal.lib.memory.sdram.dfi

/**
 * BMB到DDR桥接配置参数
 *
 * 提供结构化的配置管理，符合SpinalHDL组件设计原则
 */
case class BmbDdrConfig(
  // 基础配置
  pendingTransactionsWidth: Int = 4,  // log2(16)

  // DDR配置
  ddrConfig: DdrConfig
) {
  // 计算衍生配置
  val maxPendingTransactions = 1 << pendingTransactionsWidth
}

/**
 * DDR存储器配置参数
 */
case class DdrConfig(
  // DDR类型
  generation: SdramGeneration,

  // 银行配置
  bankCount: Int,
  bankWidth: Int,  // log2(bankCount)

  // DDR4特定配置
  bankGroups: Int = 1,  // DDR3为1，DDR4为4
  banksPerGroup: Int = 8,  // 将在工厂方法中计算

  // 时序配置
  timingCounterWidth: Int = 16,
  bankTimerWidth: Int = 8,
  responseLatencyWidth: Int = 8,

  // 超时和延迟配置
  timeoutCycles: Int = 100,
  writeLatencyCycles: Int = 8,
  refreshIntervalMs: Int = 64,

  // 地址配置
  bankGroupShift: Int = 2,  // DDR4 bank组位移

  // FAW窗口配置
  fawWindowSize: Int = 4,
  fawCounterWidth: Int = 8
) {
  // 计算衍生值
  val isDDR3 = generation == SdramGeneration.DDR3
  val isDDR4 = generation == SdramGeneration.DDR4

  // 计算实际银行组配置
  val actualBanksPerGroup: Int = if (isDDR3) bankCount else (bankCount / bankGroups)
}

/**
 * 预定义的DDR配置
 */
object DdrConfig {
  def ddr3Default(): DdrConfig = DdrConfig(
    generation = SdramGeneration.DDR3,
    bankCount = 8,
    bankWidth = 3,
    bankGroups = 1,
    banksPerGroup = 8,  // DDR3没有银行组概念
    timingCounterWidth = 16,
    bankTimerWidth = 8,
    responseLatencyWidth = 8,
    timeoutCycles = 100,
    writeLatencyCycles = 8,
    refreshIntervalMs = 64,
    bankGroupShift = 2,
    fawWindowSize = 4,
    fawCounterWidth = 8
  )

  def ddr4Default(): DdrConfig = DdrConfig(
    generation = SdramGeneration.DDR4,
    bankCount = 16,
    bankWidth = 4,
    bankGroups = 4,
    banksPerGroup = 4,  // DDR4: 16个银行，4个银行组，每组4个银行
    timingCounterWidth = 16,
    bankTimerWidth = 8,
    responseLatencyWidth = 8,
    timeoutCycles = 100,
    writeLatencyCycles = 8,
    refreshIntervalMs = 64,
    bankGroupShift = 2,
    fawWindowSize = 4,
    fawCounterWidth = 8
  )
}