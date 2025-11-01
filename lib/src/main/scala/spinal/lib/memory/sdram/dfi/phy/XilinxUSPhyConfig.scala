package spinal.lib.memory.sdram.dfi.phy

import spinal.lib.memory.sdram.dfi._

case class XilinxUSPhyConfig(
  // 硬件宽度配置
  byteWidth: Int = 8,                        // 数据字节宽度
  patternSelectWidth: Int = 2,               // 模式选择位宽
  phaseCount: Int = 4,                       // 相位数量

  // 延迟和计数器宽度
  delayCounterWidth: Int = 9,                // 延迟计数器（CDLY、DQS延迟）
  timeoutCounterWidth: Int = 8,              // 超时计数器（训练超时、状态机超时）
  timerCounterWidth: Int = 16,               // 定时器计数器（命令定时器、长时间计时）
  ckeTimerWidth: Int = 6,                    // CKE定时器位宽
  pulseCounterWidth: Int = 5,                // 脉冲计数器位宽
  stableCounterWidth: Int = 4,               // 稳定计数器位宽（确认稳定信号）
  tmrdCounterWidth: Int = 3,                 // TMRD定时器位宽（模式寄存器间隔）
  phaseSelectWidth: Int = 2,                 // 相位选择位宽

  // 训练响应宽度
  trainingResultWidth: Int = 1,              // 训练结果位宽（成功/失败）
  trainingStateCodeWidth: Int = 2            // 训练状态码位宽（详细状态）
)

object XilinxUSPhyConfig {
  def apply(): XilinxUSPhyConfig = XilinxUSPhyConfig()

  // 预设配置
  def ddr3 = XilinxUSPhyConfig()

  def ddr4 = XilinxUSPhyConfig()

  // 自定义配置方法
  def custom(
    byteWidth: Int = 8,
    delayCounterWidth: Int = 9,
    timerCounterWidth: Int = 16
  ) = XilinxUSPhyConfig(
    byteWidth = byteWidth,
    delayCounterWidth = delayCounterWidth,
    timerCounterWidth = timerCounterWidth
  )
}
