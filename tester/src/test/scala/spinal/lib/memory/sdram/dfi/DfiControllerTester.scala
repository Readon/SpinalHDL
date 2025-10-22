package spinal.lib.memory.sdram.dfi

import spinal.lib.bus.bmb.sim.{BmbMasterAgent, BmbRegionAllocator}
import spinal.lib.bus.misc.SizeMapping
import spinal.tester.code.SpinalAnyFunSuite

import scala.util.Random

/**
 * DFI控制器单元测试
 *
 * 测试DFI控制器与BMB总线接口的集成功能
 */
class DfiControllerTester extends SpinalAnyFunSuite {

  import spinal.core._
  import spinal.core.sim._
  import spinal.lib.bus.bmb.BmbParameter

  import scala.collection.mutable

  // 测试常量定义
  private val TEST_TIMING_WIDTH = 5
  private val TEST_REF_WIDTH = 23
  private val TEST_CMD_BUFFER_SIZE = 1024
  private val TEST_DATA_BUFFER_SIZE = 1024
  private val TEST_RSP_BUFFER_SIZE = 1024
  private val TEST_BANK_WIDTH = 3
  private val TEST_COLUMN_WIDTH = 10
  private val TEST_ROW_WIDTH = 15
  private val TEST_DATA_WIDTH = 16
  private val TEST_DDR_MHZ = 200
  private val TEST_DDR_WR_LAT = 4
  private val TEST_DDR_RD_LAT = 4
  private val TEST_CHIP_SELECT_NUMBER = 2
  private val TEST_BURST_LENGTH = 8
  private val TEST_SIMULATION_TIMEOUT = 10000

  test("DfiController_BasicIntegration") {
    SimConfig.withVcdWave
      .compile {
        val task: TaskParameter =
          TaskParameter(
            timingWidth = TEST_TIMING_WIDTH,
            refWidth = TEST_REF_WIDTH,
            cmdBufferSize = TEST_CMD_BUFFER_SIZE,
            dataBufferSize = TEST_DATA_BUFFER_SIZE,
            rspBufferSize = TEST_RSP_BUFFER_SIZE
          )
        val sdramtime = SdramTiming(
          generation = 3,
          RFC = 260,
          RAS = 38,
          RP = 15,
          RCD = 15,
          WTR = 8,
          WTP = 0,
          RTP = 8,
          RRD = 6,
          REF = 64000,
          FAW = 35
        )
        val sdram = SdramConfig(
          SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = TEST_BANK_WIDTH,
          columnWidth = TEST_COLUMN_WIDTH,
          rowWidth = TEST_ROW_WIDTH,
          dataWidth = TEST_DATA_WIDTH,
          ddrMHZ = TEST_DDR_MHZ,
          ddrWrLat = TEST_DDR_WR_LAT,
          ddrRdLat = TEST_DDR_RD_LAT,
          sdramtime = sdramtime
        )
        val timeConfig = DfiTimeConfig(
          frequencyRatio = 2,
          cmdPhase = 0,
          tPhyWrLat = sdram.tPhyWrlat,
          tPhyWrData = 0,
          tPhyWrCsGap = 3,
          tRddataEn = sdram.tRddataEn,
          tPhyRdlat = 4,
          tPhyRdCsGap = 3,
          tPhyRdCslat = 0,
          tPhyWrCsLat = 0
        )
        val dfiConfig: DfiConfig = DfiConfig(
          chipSelectNumber = TEST_CHIP_SELECT_NUMBER,
          dataSlice = 1,
          signalConfig = new DDR3SignalConfig(DfiFunctionConfig(), false) {
            override val useWrdataCsN = false
            override val useRddataCsN = false
          },
          timeConfig = timeConfig,
          sdram = sdram
        )
        val bmbp: BmbParameter = BmbParameter(
          addressWidth = sdram.byteAddressWidth + log2Up(dfiConfig.chipSelectNumber),
          dataWidth = dfiConfig.beatWidth,
          sourceWidth = 0,
          contextWidth = 2,
          lengthWidth = 10,
          alignment = BmbParameter.BurstAlignement.WORD
        )
        val dut = DfiController(bmbp, task, dfiConfig, RowBankColumn)
        dut
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(10)

        // 初始化所有读数据有效信号
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
        }

        val memorySize = 1 << dut.io.bmb.p.access.addressWidth
        val allowedWrites = mutable.HashMap[Long, Byte]()
        val allowedWritesStandby = mutable.HashMap[Long, Byte]()
        val addrMap = dut.addrMap

        /**
         * 重新排列地址位以匹配SDRAM地址映射
         *
         * @param value 原始地址值
         * @param segmentLengths 地址段定义 (宽度, 顺序)
         * @return 重新排列后的地址
         */
        def rearrangeBits(value: Long, segmentLengths: Array[(Int, Int)]): Long = {
          val segmentWidth = segmentLengths.map(_._1)
          val segmentOrder = segmentLengths.map(_._2)
          require(segmentWidth.sum == 64, "地址段总宽度必须为64位")
          require(segmentOrder.distinct.length == segmentOrder.length, "地址段顺序必须唯一")
          val binaryString = value.toBinaryString.reverse.padTo(64, '0').reverse
          val segments = Array.fill[String](segmentWidth.length)("")
          var offset = 0
          for (i <- segmentWidth.indices) {
            val segment = binaryString.substring(offset, offset + segmentWidth(i))
            segments(i) = segment
            offset += segmentWidth(i)
          }
          val rearrangedBinaryString = segmentOrder.map(segments).mkString("")
          java.lang.Long.parseLong(rearrangedBinaryString, 2)
        }

        /**
         * 将BMB地址转换为DFI地址
         *
         * @param bmbAddr BMB总线地址
         * @return DFI地址
         */
        def addressTranslation(bmbAddr: Long): Long = {
          // 地址宽度定义: (宽度, 顺序)
          val reservedBitsWidth = (64 - dut.dfiConfig.sdram.byteAddressWidth, 0)
          val bankWidth = (dut.dfiConfig.sdram.bankWidth, 1)
          val rowWidth = (dut.dfiConfig.sdram.rowWidth, 2)
          val colAddrHiWidth = (dut.dfiConfig.sdram.columnWidth - log2Up(dut.dfiConfig.transferPerBurst), 3)
          val colAddrLoWidth = (log2Up(dut.dfiConfig.transferPerBurst), 4)
          val byteWidth = (log2Up(dut.dfiConfig.sdram.bytePerWord), 5)

          val segmentLengths = addrMap match {
            case RowBankColumn =>
              Array[(Int, Int)](reservedBitsWidth, rowWidth, bankWidth, colAddrHiWidth, colAddrLoWidth, byteWidth)
            case BankRowColumn =>
              Array[(Int, Int)](reservedBitsWidth, bankWidth, rowWidth, colAddrHiWidth, colAddrLoWidth, byteWidth)
            case RowColumnBank =>
              Array[(Int, Int)](reservedBitsWidth, rowWidth, colAddrHiWidth, bankWidth, colAddrLoWidth, byteWidth)
          }
          val dfiAddr = rearrangeBits(bmbAddr, segmentLengths)
          dfiAddr
        }

        val dfiMemoryAgent = new DfiMemoryAgent(dut.io.dfi, dut.clockDomain) {
          override def writeNotification(address: Long, value: Byte): Unit = {
            val option = allowedWrites.get(address)
            assert(option.isDefined, s"地址 $address 未在允许写入列表中")
            assert(option.get == value, s"地址 $address 写入值不匹配: 期望 ${option.get}, 实际 $value")
            allowedWrites.remove(address)
            if (allowedWritesStandby.contains(address)) {
              allowedWrites(address) = allowedWritesStandby(address)
              allowedWritesStandby.remove(address)
            }
          }
        }

        val regions = BmbRegionAllocator(alignmentMinWidth = 6)
        val bmbAgent = new BmbMasterAgent(dut.io.bmb, dut.clockDomain) {
          override def onRspRead(address: BigInt, data: Seq[Byte]): Unit = {
            val ref = (0 until data.length).map(i => dfiMemoryAgent.getByte(addressTranslation(address.toLong + i)))
            if (ref != data) {
              val master = dut.io.bmb
              simFailure(s"BMB总线读取不匹配: $master\n  期望=$ref\n  实际=$data")
            }
          }
          override def getCmd(): () => Unit =
            if ((!rspQueue.exists(_.nonEmpty)) | (cmdQueue.nonEmpty)) super.getCmd() else null
          override def maskRandom() = true
          override def onCmdWrite(address: BigInt, data: Byte): Unit = {
            val addressLong = addressTranslation(address.toLong)
            if (allowedWrites.contains(addressLong)) {
              allowedWritesStandby(addressLong) = data
            } else {
              assert(!allowedWrites.contains(addressLong), s"地址已被占用: $address")
              allowedWrites(addressLong) = data
            }
          }
          override def regionAllocate(sizeMax: Int): SizeMapping = regions.allocate(
            Random.nextInt(memorySize) & ~((1 << regions.alignmentMinWidth) - 1),
            sizeMax,
            dut.io.bmb.p
          )
          override def regionFree(region: SizeMapping): Unit = regions.free(region)
          override def regionIsMapped(region: SizeMapping, opcode: Int): Boolean = true
        }

        // 等待所有响应完成
        while (bmbAgent.rspQueue.exists(_.nonEmpty)) {
          dut.clockDomain.waitSampling(TEST_SIMULATION_TIMEOUT)
        }
        dut.clockDomain.waitSampling(TEST_SIMULATION_TIMEOUT)
        simSuccess()
      }
  }

  // 边界情况测试
  test("DfiController_BoundaryConditions") {
    SimConfig.withVcdWave
      .compile {
        // 使用最小配置进行边界测试
        val task: TaskParameter =
          TaskParameter(
            timingWidth = 1,
            refWidth = 1,
            cmdBufferSize = 1,
            dataBufferSize = 1,
            rspBufferSize = 1
          )
        val sdramtime = SdramTiming(
          generation = 3,
          RFC = 1,
          RAS = 1,
          RP = 1,
          RCD = 1,
          WTR = 1,
          WTP = 0,
          RTP = 1,
          RRD = 1,
          REF = 1,
          FAW = 1
        )
        val sdram = SdramConfig(
          SdramGeneration.DDR3,
          bgWidth = 0,
          cidWidth = 0,
          bankWidth = 1,
          columnWidth = 1,
          rowWidth = 1,
          dataWidth = 8,
          ddrMHZ = 100,
          ddrWrLat = 1,
          ddrRdLat = 1,
          sdramtime = sdramtime
        )
        val timeConfig = DfiTimeConfig(
          frequencyRatio = 1,
          cmdPhase = 0,
          tPhyWrLat = 1,
          tPhyWrData = 0,
          tPhyWrCsGap = 1,
          tRddataEn = 1,
          tPhyRdlat = 1,
          tPhyRdCsGap = 1,
          tPhyRdCslat = 0,
          tPhyWrCsLat = 0
        )
        val dfiConfig: DfiConfig = DfiConfig(
          chipSelectNumber = 1,
          dataSlice = 1,
          signalConfig = new DDR3SignalConfig(DfiFunctionConfig(), false) {
            override val useWrdataCsN = false
            override val useRddataCsN = false
          },
          timeConfig = timeConfig,
          sdram = sdram
        )
        val bmbp: BmbParameter = BmbParameter(
          addressWidth = sdram.byteAddressWidth + log2Up(dfiConfig.chipSelectNumber),
          dataWidth = dfiConfig.beatWidth,
          sourceWidth = 0,
          contextWidth = 1,
          lengthWidth = 1,
          alignment = BmbParameter.BurstAlignement.WORD
        )
        val dut = DfiController(bmbp, task, dfiConfig, RowBankColumn)
        dut
      }
      .doSimUntilVoid { dut =>
        dut.clockDomain.forkStimulus(10)

        // 初始化所有读数据有效信号
        for (i <- 0 until dut.io.dfi.read.rd.length) {
          dut.io.dfi.read.rd(i).rddataValid #= false
        }

        // 测试最小配置下的基本功能
        dut.clockDomain.waitSampling(100)
        simSuccess()
      }
  }
}
