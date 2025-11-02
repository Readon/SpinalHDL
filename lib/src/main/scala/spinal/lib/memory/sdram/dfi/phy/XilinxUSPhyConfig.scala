package spinal.lib.memory.sdram.dfi.phy

import spinal.lib.memory.sdram.dfi._

case class XilinxUSPhyConfig(
  // 硬件宽度配置
  bitsPerByte: Int = 8,                      // 每字节位数（固定为8）
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
  // 直接返回标准配置，解决 hanging 问题
  def apply(): XilinxUSPhyConfig = XilinxUSPhyConfig(
    bitsPerByte = 8,
    patternSelectWidth = 2,
    phaseCount = 4,
    delayCounterWidth = 9,
    timeoutCounterWidth = 8,
    timerCounterWidth = 16,
    ckeTimerWidth = 6,
    pulseCounterWidth = 5,
    stableCounterWidth = 4,
    tmrdCounterWidth = 3,
    phaseSelectWidth = 2,
    trainingResultWidth = 1,
    trainingStateCodeWidth = 2
  )

  // 预设配置
  def ddr3 = apply()

  def ddr4 = apply()

  // 自定义配置方法 - 只包含可能变化的参数
  def custom(
    delayCounterWidth: Int = 9,
    timerCounterWidth: Int = 16
  ) = XilinxUSPhyConfig(
    bitsPerByte = 8,  // 固定值，不暴露给自定义接口
    delayCounterWidth = delayCounterWidth,
    timerCounterWidth = timerCounterWidth
  )
}
