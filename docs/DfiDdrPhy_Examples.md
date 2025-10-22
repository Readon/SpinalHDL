# DfiDdrPhy 使用示例

## 基本 DDR3 系统

```scala
import spinal.core._
import spinal.lib._
import spinal.lib.bus.bmb._
import spinal.lib.memory.sdram.dfi._
import spinal.lib.memory.sdram.dfi.phy.DfiDdrPhy

class BasicDdr3System extends Component {
  // SDRAM 配置
  val sdramConfig = SdramConfig(
    generation = SdramGeneration.DDR3,
    bgWidth = 0,
    cidWidth = 0,
    bankWidth = 3,
    columnWidth = 10,
    rowWidth = 13,
    dataWidth = 16,
    ddrMHZ = 400,
    ddrWrLat = 6,
    ddrRdLat = 6,
    sdramtime = SdramTiming(
      generation = 3,
      RFC = 160,
      RAS = 35,
      RP = 15,
      RCD = 15,
      WTR = 8,
      WTP = 15,
      RTP = 8,
      RRD = 6,
      REF = 64,
      FAW = 35
    )
  )

  // DFI 配置
  val dfiConfig = DfiConfig(
    chipSelectNumber = 1,
    dataSlice = 2,
    signalConfig = DfiSignalConfig.DDR3(),
    timeConfig = DfiTimeConfig(
      frequencyRatio = 1,
      cmdPhase = 0,
      tPhyWrLat = 1,
      tPhyWrData = 0,
      tPhyWrCsLat = 0,
      tPhyWrCsGap = 0,
      tRddataEn = 5,
      tPhyRdlat = 6,
      tPhyRdCslat = 0,
      tPhyRdCsGap = 0
    ),
    sdram = sdramConfig
  )

  // BMB 配置
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 32,
    lengthWidth = 6,
    sourceWidth = 0,
    contextWidth = 4,
    canRead = true,
    canWrite = true,
    alignment = BmbParameter.BurstAlignement.WORD
  )

  // 实例化组件
  val dfiController = DfiController(
    bmbp = bmbParameter,
    task = TaskParameter(
      bytePerTaskMax = 64,
      timingWidth = 8,
      refWidth = 16,
      cmdBufferSize = 8,
      dataBufferSize = 8,
      rspBufferSize = 8
    ),
    dfiConfig = dfiConfig,
    addrMap = RowBankColumn
  )

  val phy = DfiDdrPhy.ddr3(
    chipSelectNumber = 1,
    dataWidth = 16,
    sdramConfig = sdramConfig
  )

  // 连接接口
  phy.io.dfi <> dfiController.io.dfi

  // 暴露接口
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val phyStatus = out(phy.io.status)
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.phyStatus := phy.io.status
}
```

## 多芯片 DDR4 系统

```scala
class MultiChipDdr4System extends Component {
  val chipCount = 2

  // SDRAM 配置
  val sdramConfig = SdramConfig(
    generation = SdramGeneration.DDR4,
    bgWidth = 2,
    cidWidth = 0,
    bankWidth = 4,  // DDR4 有 4 位 bank 地址
    columnWidth = 10,
    rowWidth = 15,  // DDR4 行地址更宽
    dataWidth = 32,
    ddrMHZ = 800,   // DDR4-1600
    ddrWrLat = 10,
    ddrRdLat = 11,
    sdramtime = SdramTiming(
      generation = 4,
      RFC = 350,
      RAS = 39,
      RP = 15,
      RCD = 15,
      WTR = 8,
      WTP = 20,
      RTP = 8,
      RRD = 6,
      REF = 64,
      FAW = 25
    )
  )

  // DFI 配置
  val dfiConfig = DfiConfig(
    chipSelectNumber = chipCount,
    dataSlice = 4,
    signalConfig = DfiSignalConfig.DDR4(),
    timeConfig = DfiTimeConfig(
      frequencyRatio = 2,  // 1:2 频率比
      cmdPhase = 0,
      tPhyWrLat = 3,
      tPhyWrData = 1,
      tPhyWrCsLat = 1,
      tPhyWrCsGap = 1,
      tRddataEn = 8,
      tPhyRdlat = 9,
      tPhyRdCslat = 1,
      tPhyRdCsGap = 1
    ),
    sdram = sdramConfig
  )

  // BMB 配置
  val bmbParameter = BmbParameter(
    addressWidth = 32,
    dataWidth = 64,  // 64 位数据宽度
    lengthWidth = 6,
    sourceWidth = 4,
    contextWidth = 4,
    canRead = true,
    canWrite = true,
    alignment = BmbParameter.BurstAlignement.WORD
  )

  // 实例化组件
  val dfiController = DfiController(
    bmbp = bmbParameter,
    task = TaskParameter(
      bytePerTaskMax = 128,
      timingWidth = 8,
      refWidth = 16,
      cmdBufferSize = 16,
      dataBufferSize = 16,
      rspBufferSize = 16
    ),
    dfiConfig = dfiConfig,
    addrMap = BankRowColumn
  )

  val phy = DfiDdrPhy.ddr4(
    chipSelectNumber = chipCount,
    dataWidth = 32,
    sdramConfig = sdramConfig
  )

  // 连接接口
  phy.io.dfi <> dfiController.io.dfi

  // 暴露接口
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = Vec(master(DdrInterface(sdramConfig)), chipCount)
    val phyStatus = out(phy.io.status)
  }

  io.bmb <> dfiController.io.bmb
  for (i <- 0 until chipCount) {
    io.sdram(i) <> phy.io.sdram(i)
  }
  io.phyStatus := phy.io.status
}
```

## 带训练功能的 LPDDR3 系统

```scala
class Lpddr3WithTrainingSystem extends Component {
  // SDRAM 配置
  val sdramConfig = SdramConfig(
    generation = SdramGeneration.DDR3,  // LPDDR3 基于 DDR3 架构
    bgWidth = 0,
    cidWidth = 0,
    bankWidth = 3,
    columnWidth = 10,
    rowWidth = 14,
    dataWidth = 32,
    ddrMHZ = 533,   // LPDDR3-1066
    ddrWrLat = 8,
    ddrRdLat = 10,
    sdramtime = SdramTiming(
      generation = 3,
      RFC = 210,
      RAS = 42,
      RP = 18,
      RCD = 18,
      WTR = 8,
      WTP = 15,
      RTP = 8,
      RRD = 10,
      REF = 64,
      FAW = 50
    )
  )

  // DFI 配置
  val dfiConfig = DfiConfig(
    chipSelectNumber = 1,
    dataSlice = 4,
    signalConfig = DfiSignalConfig.DDR3(),
    timeConfig = DfiTimeConfig(
      frequencyRatio = 2,
      cmdPhase = 0,
      tPhyWrLat = 2,
      tPhyWrData = 0,
      tPhyWrCsLat = 0,
      tPhyWrCsGap = 0,
      tRddataEn = 7,
      tPhyRdlat = 8,
      tPhyRdCslat = 0,
      tPhyRdCsGap = 0
    ),
    sdram = sdramConfig
  )

  // BMB 配置
  val bmbParameter = BmbParameter(
    addressWidth = 30,
    dataWidth = 32,
    lengthWidth = 6,
    sourceWidth = 0,
    contextWidth = 4,
    canRead = true,
    canWrite = true,
    alignment = BmbParameter.BurstAlignement.WORD
  )

  // 实例化组件
  val dfiController = DfiController(
    bmbp = bmbParameter,
    task = TaskParameter(
      bytePerTaskMax = 64,
      timingWidth = 8,
      refWidth = 16,
      cmdBufferSize = 8,
      dataBufferSize = 8,
      rspBufferSize = 8
    ),
    dfiConfig = dfiConfig,
    addrMap = RowBankColumn
  )

  val phy = new DfiDdrPhy(DfiDdrPhyConfig(
    ddrStandard = DdrStandard.LPDDR3,
    dfiConfig = dfiConfig,
    sdramConfig = sdramConfig,
    features = DfiDdrPhyFeatures(
      dbiSupport = true,
      crcSupport = false,  // LPDDR3 可能不支持 CRC
      caParitySupport = true,
      lowPowerSupport = true,
      trainingSupport = true,
      debugSupport = true
    )
  ))

  // 训练控制器
  val trainingController = new TrainingController(dfiConfig)

  // 连接接口
  phy.io.dfi <> dfiController.io.dfi
  trainingController.io.training <> phy.io.training

  // 暴露接口
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val phyStatus = out(phy.io.status)
    val trainingStatus = out(trainingController.io.status)
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.phyStatus := phy.io.status
  io.trainingStatus := trainingController.io.status
}
```

## 带错误处理的系统

```scala
class DdrSystemWithErrorHandling extends Component {
  // ... 配置部分与基本示例相同 ...

  val dfiController = DfiController(/* ... */)
  val phy = DfiDdrPhy.ddr3(/* ... */)

  // 连接接口
  phy.io.dfi <> dfiController.io.dfi

  // 错误处理逻辑
  val errorHandler = new Area {
    val errorDetected = RegInit(False)
    val errorCode = RegInit(U(0, 8 bits))
    val recoveryAttempts = RegInit(U(0, 4 bits))

    when(phy.io.status.error && !errorDetected) {
      errorDetected := True
      errorCode := phy.io.status.errorCode
    }

    when(errorDetected) {
      switch(errorCode) {
        is(0x01) { // 可恢复错误
          when(recoveryAttempts < 3) {
            // 尝试恢复
            recoveryAttempts := recoveryAttempts + 1
            // 触发重新初始化
            reinitSequence.start()
          } otherwise {
            // 进入安全模式
            enterSafeMode()
          }
        }
        is(0x02) { // 严重错误
          // 立即停止并报告
          emergencyStop()
        }
      }
    }

    when(reinitSequence.done) {
      errorDetected := False
      recoveryAttempts := 0
    }
  }

  // 状态监控
  val statusMonitor = new Area {
    val initComplete = RegInit(False)
    val calibrationComplete = RegInit(False)
    val operational = RegInit(False)

    when(phy.io.status.initialized && !initComplete) {
      initComplete := True
      reportEvent("PHY initialization complete")
    }

    when(phy.io.status.calibrating && initComplete && !calibrationComplete) {
      calibrationComplete := True
      reportEvent("PHY calibration complete")
    }

    operational := initComplete && calibrationComplete && !phy.io.status.error
  }

  // 暴露接口
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val systemStatus = out(new Bundle {
      val operational = Bool()
      val error = Bool()
      val errorCode = Bits(8 bits)
      val recoveryAttempts = UInt(4 bits)
    })
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.systemStatus.operational := statusMonitor.operational
  io.systemStatus.error := errorHandler.errorDetected
  io.systemStatus.errorCode := errorHandler.errorCode
  io.systemStatus.recoveryAttempts := errorHandler.recoveryAttempts
}
```

## 频率比测试系统

```scala
class FrequencyRatioTestSystem extends Component {
  val frequencyRatios = Seq(1, 2, 4)

  val systems = frequencyRatios.map { ratio =>
    // 为每个频率比创建配置
    val dfiConfig = DfiConfig(
      // ... 基础配置
      timeConfig = DfiTimeConfig(
        frequencyRatio = ratio,
        // ... 其他时间参数根据频率比调整
      )
    )

    val dfiController = DfiController(/* 配置 */)
    val phy = DfiDdrPhy.ddr3(/* 配置 */)

    phy.io.dfi <> dfiController.io.dfi

    (dfiController, phy)
  }

  // 测试逻辑
  val testController = new Area {
    val currentRatio = RegInit(U(0, 2 bits))
    val testRunning = RegInit(False)
    val testComplete = RegInit(False)

    val testSequence = new StateMachine {
      val idle = new State with EntryPoint
      val testing = new State
      val complete = new State

      idle.whenIsActive {
        when(testRunning) {
          goto(testing)
        }
      }

      testing.whenIsActive {
        // 对当前频率比运行测试
        val (controller, phy) = systems(currentRatio)
        // ... 测试逻辑 ...

        when(/* 测试完成条件 */) {
          currentRatio := currentRatio + 1
          when(currentRatio === (frequencyRatios.length - 1)) {
            goto(complete)
          }
        }
      }

      complete.whenIsActive {
        testComplete := True
      }
    }
  }

  // 暴露接口
  val io = new Bundle {
    val bmb = Vec(slave(Bmb(bmbParameter)), frequencyRatios.length)
    val sdram = Vec(master(DdrInterface(sdramConfig)), frequencyRatios.length)
    val testControl = new Bundle {
      val start = in Bool()
      val complete = out Bool()
      val currentRatio = out UInt(2 bits)
    }
  }

  systems.zipWithIndex.foreach { case ((controller, phy), i) =>
    io.bmb(i) <> controller.io.bmb
    io.sdram(i) <> phy.io.sdram
  }

  io.testControl.start <> testController.testRunning
  io.testControl.complete := testController.testComplete
  io.testControl.currentRatio := testController.currentRatio
}
```

## 性能监控系统

```scala
class PerformanceMonitorSystem extends Component {
  // ... 基本系统配置 ...

  val dfiController = DfiController(/* ... */)
  val phy = DfiDdrPhy.ddr3(/* ... */)

  phy.io.dfi <> dfiController.io.dfi

  // 性能监控
  val performanceMonitor = new Area {
    val transactionCount = RegInit(U(0, 32 bits))
    val errorCount = RegInit(U(0, 16 bits))
    val latencyAccumulator = RegInit(U(0, 32 bits))
    val bandwidthAccumulator = RegInit(U(0, 32 bits))

    val monitorClock = ClockDomain.current

    // 事务计数
    when(dfiController.io.bmb.fire) {
      transactionCount := transactionCount + 1
    }

    // 错误计数
    when(phy.io.status.error) {
      errorCount := errorCount + 1
    }

    // 带宽计算（简化版本）
    val bytesPerTransaction = U(4)  // 假设 32 位系统
    val bandwidth = transactionCount * bytesPerTransaction

    // 延迟测量（简化版本）
    val transactionLatency = RegInit(U(0, 16 bits))
    // ... 延迟测量逻辑 ...
  }

  // 健康监控
  val healthMonitor = new Area {
    val temperature = phy.io.status.temperature
    val errorRate = performanceMonitor.errorCount / performanceMonitor.transactionCount
    val operational = phy.io.status.initialized && !phy.io.status.error

    val alerts = new Bundle {
      val overTemperature = temperature > 80
      val highErrorRate = errorRate > 0.01
      val notOperational = !operational
    }
  }

  // 暴露接口
  val io = new Bundle {
    val bmb = slave(Bmb(bmbParameter))
    val sdram = master(phy.io.sdram)
    val performance = out(new Bundle {
      val transactionCount = UInt(32 bits)
      val errorCount = UInt(16 bits)
      val bandwidth = UInt(32 bits)
    })
    val health = out(new Bundle {
      val operational = Bool()
      val temperature = Bits(8 bits)
      val alerts = healthMonitor.alerts.clone()
    })
  }

  io.bmb <> dfiController.io.bmb
  io.sdram <> phy.io.sdram
  io.performance.transactionCount := performanceMonitor.transactionCount
  io.performance.errorCount := performanceMonitor.errorCount
  io.performance.bandwidth := performanceMonitor.bandwidth
  io.health.operational := healthMonitor.operational
  io.health.temperature := healthMonitor.temperature
  io.health.alerts := healthMonitor.alerts
}