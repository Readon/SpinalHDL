## Purpose

DFI DDR PHY组件提供DFI 3.1协议兼容的DDR物理层实现，支持多种DDR存储器标准（DDR2/3/4、LPDDR系列），通过简化后的3模块架构（UnifiedAdapter、DataManager、ControlManager）实现高效的协议转换和时序管理。
## Requirements
### Requirement: DFI 3.1协议兼容性
DFI PHY组件SHALL完全兼容DFI 3.1规范，通过XilinxUSPhy实现提供完整的接口信号组和时序参数支持。

#### Scenario: XilinxUSPhy作为主要PHY实现
- **WHEN** 系统需要DFI 3.1兼容的PHY实现
- **AND** 与DfiController集成
- **THEN** XilinxUSPhy必须提供所有必需的DFI 3.1接口支持
- **AND** 维护完整的时序关系和协议合规性

#### Scenario: 控制接口合规性
- **WHEN** DfiController通过dfi接口发送命令
- **AND** 使用XilinxUSPhy作为PHY实现
- **THEN** XilinxUSPhy必须正确解析和响应所有命令类型
- **AND** 维持必需的时序关系（tctrl_delay、tcmd_lat等）

#### Scenario: 数据接口合规性
- **WHEN** DfiController通过dfi接口传输读写数据
- **AND** 使用XilinxUSPhy作为PHY实现
- **THEN** XilinxUSPhy必须正确处理所有数据传输模式
- **AND** 支持频率比系统（1:1、1:2、1:4）

#### Scenario: 训练接口合规性
- **WHEN** DfiController通过dfi接口发起训练操作
- **AND** 使用XilinxUSPhy作为PHY实现
- **THEN** XilinxUSPhy必须支持读训练、写电平校准和CA训练
- **AND** 正确响应训练请求和状态查询

### Requirement: JEDEC标准时序支持
DFI PHY组件SHALL支持JEDEC标准定义的DDR时序参数和操作模式。

#### Scenario: DDR3时序合规性
- **WHEN** 配置为DDR3模式
- **AND** 符合JESD79-3规范
- **THEN** PHY必须实现所有必需的时序参数
- **AND** 支持DDR3特有的功能特性

#### Scenario: DDR4时序合规性
- **WHEN** 配置为DDR4模式
- **AND** 符合JESD79-4规范
- **THEN** PHY必须实现所有必需的时序参数
- **AND** 支持DDR4特有的功能特性（DBI、CRC、CA奇偶校验等）

#### Scenario: LPDDR时序合规性
- **WHEN** 配置为LPDDR模式
- **AND** 符合JESD209系列规范
- **THEN** PHY必须实现低功耗DDR特有的时序要求
- **AND** 支持LPDDR特有的电源管理功能

### Requirement: PHY接口标准化
DFI PHY组件SHALL提供标准化的接口，支持不同应用场景和配置需求。

#### Scenario: 配置接口
- **WHEN** 系统集成PHY组件与DfiController
- **AND** 需要自定义配置
- **THEN** PHY必须提供清晰的参数化配置接口
- **AND** 支持编译时和运行时配置

#### Scenario: 状态和监控
- **WHEN** DfiController需要监控PHY状态
- **AND** 通过dfi接口查询状态
- **THEN** PHY必须提供状态查询接口
- **AND** 支持调试和诊断功能

#### Scenario: 错误报告
- **WHEN** DfiController需要错误信息
- **AND** 通过dfi接口报告错误
- **THEN** PHY必须提供错误检测和报告机制
- **AND** 支持错误恢复和处理

### Requirement: 高级PHY特性支持
DFI PHY组件SHALL通过XilinxUSPhy支持现代DDR接口所需的高级特性。

#### Scenario: XilinxUSPhy高级特性
- **WHEN** 启用DDR高级功能
- **AND** 使用XilinxUSPhy作为PHY实现
- **THEN** XilinxUSPhy必须正确处理数据总线反转（DBI）
- **AND** 支持循环冗余校验（CRC）
- **AND** 支持命令地址奇偶校验

#### Scenario: 高级DDR功能集成
- **WHEN** 系统需要DDR4或LPDDR特定功能
- **AND** 通过BmbToDdrBridge集成
- **THEN** XilinxUSPhy必须正确支持所有相关高级特性
- **AND** 确保与BMB桥接的兼容性

### Requirement: 性能和资源优化
DFI PHY组件SHALL通过XilinxUSPhy在性能和资源使用之间提供良好的平衡。

#### Scenario: XilinxUSPhy性能优化
- **WHEN** 系统要求高性能DDR访问
- **AND** 使用XilinxUSPhy作为PHY实现
- **THEN** XilinxUSPhy必须优化关键路径时序
- **AND** 最小化延迟开销
- **AND** 提供可配置的资源使用选项

### Requirement: 测试和验证支持
DFI PHY组件SHALL提供完整的测试和验证支持。

#### Scenario: 仿真验证
- **WHEN** 进行系统级仿真与DfiController
- **AND** 需要PHY行为模型
- **THEN** PHY必须提供仿真模型
- **AND** 支持不同抽象级别的验证

#### Scenario: 硬件测试
- **WHEN** 进行与DfiController的硬件测试
- **AND** 需要测试接口
- **THEN** PHY必须提供测试模式和接口
- **AND** 支持生产测试和诊断

### Requirement: 文档和示例
DFI PHY组件SHALL提供完整的文档和示例代码。

#### Scenario: 使用文档
- **WHEN** 开发者需要集成PHY与DfiController
- **AND** 查找使用指南
- **THEN** 必须提供详细的使用文档
- **AND** 包含配置和调试指南

#### Scenario: 示例代码
- **WHEN** 开发者需要参考实现
- **AND** 查找如何与DfiController集成的示例
- **THEN** 必须提供完整的工作示例
- **AND** 涵盖与DfiController的集成场景

### Requirement: Comprehensive DFI 3.1 Interface Testing
XilinxUSPhy测试套件SHALL提供DFI 3.1规范定义的所有接口信号组的完整测试覆盖，确保每个接口组的正确性和合规性。

#### Scenario: DFI控制接口完整测试
- **WHEN** 进行DFI控制接口测试
- **AND** 验证所有必需的控制信号（rasN、casN、weN、csN、address、bank）
- **AND** 验证可选控制信号（cke、odt、resetN、act_n、bg、cid）
- **THEN** 测试必须验证所有命令类型的正确传输和时序关系
- **AND** 确保控制接口与DFI 3.1规范完全合规

#### Scenario: DFI写接口完整测试
- **WHEN** 进行DFI写接口测试
- **AND** 验证写数据信号（wrdata、wrdata_en、wrdata_mask）
- **AND** 验证写数据片选信号（wrdata_csN）
- **AND** 验证写数据字特定信号（_w0、_w1等）
- **THEN** 测试必须验证写数据的正确传输和时序
- **AND** 确保写接口支持所有必需的数据模式

#### Scenario: DFI读接口完整测试
- **WHEN** 进行DFI读接口测试
- **AND** 验证读数据信号（rddata、rddata_valid）
- **AND** 验证读数据片选信号（rddata_csN）
- **AND** 验证读DBI信号（rddata_dbi_n）
- **THEN** 测试必须验证读数据的正确接收和时序
- **AND** 确保读接口支持所有必需的数据模式

#### Scenario: DFI训练接口完整测试
- **WHEN** 进行DFI训练接口测试
- **AND** 验证读训练信号（rdlvl_*）
- **AND** 验证写训练信号（wrlvl_*）
- **AND** 验证CA训练信号（calvl_*）
- **THEN** 测试必须验证所有训练操作的发起和响应
- **AND** 确保训练接口支持DFI 3.1定义的所有训练模式

### Requirement: 频率比系统完整测试
XilinxUSPhy测试套件SHALL提供DFI 3.1频率比系统（1:1、1:2、1:4）的完整测试覆盖，验证相位特定信号处理和命令执行。

#### Scenario: 1:2频率比测试
- **WHEN** 配置为1:2频率比
- **AND** 进行命令和数据传输测试
- **THEN** 测试必须正确处理相位特定信号（_p0、_p1）
- **AND** 验证双相命令执行的正确性
- **AND** 确保数据在正确相位上传输

#### Scenario: 1:4频率比测试
- **WHEN** 配置为1:4频率比
- **AND** 进行命令和数据传输测试
- **THEN** 测试必须正确处理相位特定信号（_p0、_p1、_p2、_p3）
- **AND** 验证四相命令执行的正确性
- **AND** 确保数据字按正确顺序旋转

#### Scenario: 数据字管理测试
- **WHEN** 在频率比系统中传输数据
- **AND** 使用数据字特定信号
- **THEN** 测试必须正确处理读数据字（_w0、_w1等）
- **AND** 维持正确的读数据旋转顺序
- **AND** 确保写数据在正确相位上发送

### Requirement: 多标准DDR支持测试
XilinxUSPhy测试套件SHALL提供多DDR标准（DDR2、DDR3、DDR4、LPDDR2/3/4）的完整测试覆盖，验证标准特定功能和时序要求。

#### Scenario: DDR2标准测试
- **WHEN** 配置为DDR2模式
- **AND** 执行DDR2特定测试序列
- **THEN** 测试必须验证DDR2特有的时序参数
- **AND** 确保与DDR2 JEDEC规范（JESD79-2）的合规性
- **AND** 验证DDR2特有功能（如ODT控制）

#### Scenario: DDR4标准测试
- **WHEN** 配置为DDR4模式
- **AND** 执行DDR4特定测试序列
- **THEN** 测试必须验证Bank Group操作
- **AND** 确保与DDR4 JEDEC规范（JESD79-4）的合规性
- **AND** 验证DDR4特有功能（DBI、CRC、CA parity）

#### Scenario: LPDDR标准测试
- **WHEN** 配置为LPDDR模式
- **AND** 执行LPDDR特定测试序列
- **THEN** 测试必须验证低功耗特性
- **AND** 确保与LPDDR JEDEC规范（JESD209系列）的合规性
- **AND** 验证LPDDR特有功能（深度睡眠、温度补偿刷新）

### Requirement: 高级训练操作测试
XilinxUSPhy测试套件SHALL提供DFI 3.1定义的所有高级训练操作的完整测试覆盖，验证训练算法的正确性和鲁棒性。

#### Scenario: 写电平训练测试
- **WHEN** 发起写电平训练
- **AND** 执行完整的写电平训练序列
- **THEN** 测试必须验证写延迟校准的正确性
- **AND** 确保训练算法收敛到最佳延迟值
- **AND** 验证训练结果的稳定性和可重复性

#### Scenario: 读门训练测试
- **WHEN** 发起读门训练
- **AND** 执行完整的读门训练序列
- **THEN** 测试必须验证读门延迟的优化
- **AND** 确保读数据窗口的最大化
- **AND** 验证门训练在不同频率下的适应性

#### Scenario: 读眼训练测试
- **WHEN** 发起读眼训练
- **AND** 执行完整的读眼训练序列
- **THEN** 测试必须验证读数据眼的优化
- **AND** 确保最佳采样点的确定
- **AND** 验证眼训练对电压和温度变化的适应性

#### Scenario: CA训练测试
- **WHEN** 发起CA训练（LPDDR系统）
- **AND** 执行完整的CA训练序列
- **THEN** 测试必须验证命令/地址信号的优化
- **AND** 确保CA信号的建立和保持时间满足要求
- **AND** 验证CA训练对多模式寄存器的支持

### Requirement: DDR高级功能测试
XilinxUSPhy测试套件SHALL提供现代DDR接口高级功能的完整测试覆盖，验证DBI、CRC和CA parity等特性的正确实现。

#### Scenario: DBI功能测试
- **WHEN** 启用DBI功能
- **AND** 进行读写数据传输
- **THEN** 测试必须验证写DBI的正确处理
- **AND** 确保读DBI的正确解码
- **AND** 验证DBI对数据完整性的影响

#### Scenario: CRC功能测试
- **WHEN** 启用CRC功能
- **AND** 传输写数据
- **THEN** 测试必须验证CRC生成的正确性
- **AND** 确保CRC验证的准确性
- **AND** 验证CRC错误的检测和报告

#### Scenario: CA parity测试
- **WHEN** 启用CA parity功能
- **AND** 发送命令
- **THEN** 测试必须验证奇偶校验位的生成
- **AND** 确保奇偶校验错误的检测
- **AND** 验证校验错误的处理机制

### Requirement: 多芯片选择测试
XilinxUSPhy测试套件SHALL提供多芯片选择（多rank）配置的完整测试覆盖，验证多rank内存系统的正确操作。

#### Scenario: 双rank配置测试
- **WHEN** 配置为双rank内存系统
- **AND** 执行跨rank的操作序列
- **THEN** 测试必须验证rank切换的正确性
- **AND** 确保rank间时序要求的满足
- **AND** 验证多rank操作的并发性

#### Scenario: 四rank配置测试
- **WHEN** 配置为四rank内存系统
- **AND** 执行复杂的rank调度操作
- **THEN** 测试必须验证rank调度的效率
- **AND** 确保rank冲突的避免
- **AND** 验证多rank系统的性能优化

### Requirement: 错误处理和恢复测试
XilinxUSPhy测试套件SHALL提供PHY错误检测、报告和恢复机制的完整测试覆盖，验证系统在异常情况下的鲁棒性。

#### Scenario: 错误检测测试
- **WHEN** 注入各种类型的错误
- **AND** 监控PHY的错误检测机制
- **THEN** 测试必须验证所有错误类型的正确检测
- **AND** 确保错误信息的准确性
- **AND** 验证错误检测的及时性

#### Scenario: 错误恢复测试
- **WHEN** 检测到错误条件
- **AND** 执行错误恢复序列
- **THEN** 测试必须验证错误恢复的有效性
- **AND** 确保系统恢复正常操作
- **AND** 验证恢复过程的可靠性

### Requirement: 性能和资源测试
XilinxUSPhy测试套件SHALL提供性能基准测试和资源利用率验证，确保PHY在性能和资源使用之间达到良好平衡。

#### Scenario: 高频操作测试
- **WHEN** 在高频条件下运行
- **AND** 执行密集的数据传输操作
- **THEN** 测试必须验证时序要求的满足
- **AND** 确保信号完整性的维持
- **AND** 验证高频操作的稳定性

#### Scenario: 资源效率测试
- **WHEN** 在资源受限环境中部署
- **AND** 评估FPGA资源使用情况
- **THEN** 测试必须验证资源使用的效率
- **AND** 确保资源使用的可配置性
- **AND** 验证不同优化级别的影响

### Requirement: 配置接口测试
XilinxUSPhy测试套件SHALL提供配置接口的完整测试覆盖，验证配置参数的正确性和配置过程的一致性。

#### Scenario: 配置参数验证
- **WHEN** 设置各种配置参数
- **AND** 验证参数的有效性
- **THEN** 测试必须验证参数的正确应用
- **AND** 确保配置的一致性
- **AND** 验证配置边界的处理

#### Scenario: 运行时配置测试
- **WHEN** 在运行时修改配置
- **AND** 验证配置的即时生效
- **THEN** 测试必须验证运行时配置的正确性
- **AND** 确保配置切换的平滑性
- **AND** 验证配置更改的安全性

### Requirement: BMB到DDR桥接支持
系统SHALL提供完整的BMB总线到DDR存储器接口的桥接功能，通过增强的BmbToDdrBridge实现高效可靠的DDR3和DDR4支持。

#### Scenario: BMB总线桥接
- **WHEN** BMB总线需要访问DDR存储器
- **AND** 通过增强的BmbToDdrBridge组件
- **THEN** 桥接器必须正确处理BMB协议到DDR命令的转换
- **AND** 维护数据完整性和时序约束
- **AND** 支持完整的错误检测和恢复机制

#### Scenario: DDR3标准支持
- **WHEN** 系统配置为DDR3存储器
- **AND** 使用增强的BmbToDdrBridge和XilinxUSPhy
- **THEN** 桥接器必须完全支持DDR3 JEDEC规范（JESD79-3）
- **AND** 正确处理DDR3特定的时序参数和功能
- **AND** 实现8-bank状态管理和优化

#### Scenario: DDR4标准支持
- **WHEN** 系统配置为DDR4存储器
- **AND** 使用增强的BmbToDdrBridge和XilinxUSPhy
- **THEN** 桥接器必须完全支持DDR4 JEDEC规范（JESD79-4）
- **AND** 正确处理DDR4特有功能（Bank Group、DBI、CRC等）
- **AND** 支持Bank Group架构优化

#### Scenario: 地址映射和事务管理
- **WHEN** BMB总线发起DDR访问
- **AND** 通过增强的BmbToDdrBridge
- **THEN** 桥接器必须正确实现地址映射
- **AND** 管理DDR时序约束和事务调度
- **AND** 处理刷新和初始化序列
- **AND** 优化bank级并行访问

### Requirement: 完整测试和验证覆盖
BMB到DDR桥接解决方案SHALL提供全面的测试覆盖，确保系统在各种配置下的正确性和可靠性。

#### Scenario: 桥接功能测试
- **WHEN** 进行BMB到DDR桥接测试
- **AND** 使用增强的BmbToDdrBridge和XilinxUSPhy
- **THEN** 测试套件必须验证完整的读写操作
- **AND** 确保数据正确性和时序合规性
- **AND** 验证多源并发事务处理

#### Scenario: DDR3/DDR4兼容性测试
- **WHEN** 测试不同DDR标准支持
- **AND** 使用统一的增强BmbToDdrBridge接口
- **THEN** 测试必须验证DDR3和DDR4模式的正确切换
- **AND** 确保每种标准的特定功能正常工作
- **AND** 验证标准特定的时序约束

#### Scenario: 错误处理和恢复测试
- **WHEN** 系统遇到DDR访问错误
- **AND** 使用增强的BmbToDdrBridge
- **THEN** 测试必须验证错误检测机制
- **AND** 确保错误恢复和系统稳定性
- **AND** 验证超时和重试逻辑

#### Scenario: 性能和压力测试
- **WHEN** 系统在高负载下运行
- **AND** 通过增强的BmbToDdrBridge访问DDR
- **THEN** 测试必须验证性能指标
- **AND** 确保系统在高压力下的稳定性
- **AND** 验证命令调度和bank并行优化

### Requirement: 简化架构和维护性
代码库SHALL通过清理重复实现和标准化接口来提高维护性和开发效率。

#### Scenario: 代码清理和去重
- **WHEN** 清理DDR DFI实现代码
- **AND** 移除重复的DfiDdrPhy实现
- **THEN** 必须保留所有XilinxUSPhy相关功能
- **AND** 确保清理不破坏现有功能

#### Scenario: 标准化接口设计
- **WHEN** 设计BMB到DDR桥接接口
- **AND** 参考现有最佳实践
- **THEN** 必须使用清晰一致的API设计
- **AND** 确保接口的向后兼容性

#### Scenario: 文档和示例完整性
- **WHEN** 开发者使用BMB到DDR桥接
- **AND** 需要参考文档和示例
- **THEN** 必须提供完整的使用指南
- **AND** 包含DDR3和DDR4配置示例

### Requirement: 完整的BMB协议支持
增强的BMB-DDR桥接器SHALL完整支持BMB总线协议的所有特性，包括读、写、原子操作和响应管理。

#### Scenario: BMB命令处理
- **WHEN** BMB总线发起命令请求
- **AND** 命令类型为READ、WRITE或ATOMIC
- **THEN** 增强桥接器必须正确解析所有命令字段
- **AND** 维护命令的顺序和依赖关系
- **AND** 生成相应的DDR控制信号
- **AND** 支持事务级并行优化

#### Scenario: 多源ID并发处理
- **WHEN** 多个BMB源同时发起请求
- **AND** 不同的源ID和事务ID
- **THEN** 增强桥接器必须正确管理多个并发事务
- **AND** 确保响应与正确的源ID关联
- **AND** 维护每个源的独立事务顺序
- **AND** 实现公平的仲裁机制

#### Scenario: BMB响应生成
- **WHEN** DDR操作完成或出错
- **AND** 需要向BMB总线返回响应
- **THEN** 增强桥接器必须生成符合BMB规范的响应
- **AND** 包含正确的状态信息、错误码和完成标志
- **AND** 遵循BMB响应时序要求
- **AND** 支持乱序响应和重新排序

### Requirement: 增强的错误检测和恢复机制
增强的BMB-DDR桥接器SHALL提供全面的错误检测、报告和恢复机制，确保系统可靠性和调试能力。

#### Scenario: 超时错误处理
- **WHEN** DDR操作超时或无响应
- **AND** 超过配置的最大等待时间
- **THEN** 增强桥接器必须检测超时条件
- **AND** 生成超时错误响应
- **AND** 执行恢复序列重置相关状态
- **AND** 提供详细的超时诊断信息

#### Scenario: 数据完整性错误
- **WHEN** 检测到数据错误或损坏
- **AND** 包括CRC错误、ECC错误等
- **THEN** 增强桥接器必须识别错误类型和位置
- **AND** 生成详细的错误报告
- **AND** 支持错误重试和恢复操作
- **AND** 实现数据校验和纠正机制

#### Scenario: 地址和时序违规
- **WHEN** 违反DDR时序约束或地址映射错误
- **AND** 可能导致数据损坏或系统不稳定
- **THEN** 增强桥接器必须预防时序违规
- **AND** 检测和报告地址映射错误
- **AND** 提供调试信息和状态监控
- **AND** 实现主动的违规预防机制

### Requirement: 性能优化和资源管理
增强的BMB-DDR桥接器SHALL在性能、资源使用和功耗之间提供最佳的平衡。

#### Scenario: 命令调度优化
- **WHEN** 多个DDR命令等待执行
- **AND** 需要优化吞吐量和延迟
- **THEN** 增强桥接器必须实现智能命令调度
- **AND** 最大化bank级并行性
- **AND** 最小化命令间的冲突和等待
- **AND** 支持自适应调度算法

#### Scenario: 数据流水线管理
- **WHEN** 处理大量数据传输
- **AND** 需要保持高带宽利用率
- **THEN** 增强桥接器必须实现高效的数据流水线
- **AND** 支持读写操作的流水线重叠
- **AND** 动态调整流水线深度以优化性能
- **AND** 实现可配置的缓冲策略

#### Scenario: 资源使用优化
- **WHEN** 在资源受限的FPGA上实现
- **AND** 需要最小化资源占用
- **THEN** 增强桥接器必须提供可配置的资源优化选项
- **AND** 支持不同性能/资源权衡的配置
- **AND** 优化关键路径时序以满足时钟频率要求
- **AND** 提供资源使用分析和报告功能

### Requirement: BmbToDdrBridge BmbMasterAgent Testing Framework
BmbToDdrBridge测试框架SHALL提供与BmbMasterAgent的完整集成，通过成熟的仿真框架验证BMB到DDR协议转换的正确性。

#### Scenario: BmbMasterAgent桥接测试初始化
- **WHEN** 启动BmbToDdrBridge功能测试
- **AND** 使用BmbMasterAgent作为测试驱动器
- **THEN** 测试框架必须正确初始化BmbToDdrBridge和BmbMasterAgent
- **AND** 建立BMB总线的仿真连接
- **AND** 配置DDR内存模型（Micron ddr2.v或ddr3.v）作为响应目标

#### Scenario: 基础读写事务测试
- **WHEN** BmbMasterAgent发起单次读操作
- **AND** 通过BmbToDdrBridge访问DDR模型
- **THEN** 测试必须验证读数据的完整性和正确性
- **AND** 确保响应时序符合BMB协议要求
- **AND** 验证DDR地址映射的正确性

#### Scenario: 多beat事务传输测试
- **WHEN** BmbMasterAgent发起多beat写操作
- **AND** 传输数据长度超过单个BMB beat
- **THEN** 测试必须验证所有数据beat的正确传输
- **AND** 确保DDR接口处理多beat事务的正确性
- **AND** 验证数据掩码功能的正确应用

### Requirement: DDR2/DDR3 BmbToDdrBridge兼容性测试
BmbToDdrBridge测试框架SHALL提供完整的DDR2和DDR3兼容性验证，确保桥接器正确处理不同DDR标准的时序约束和功能特性。

#### Scenario: DDR2时序参数验证
- **WHEN** 配置为DDR2模式进行测试
- **AND** 使用Micron ddr2.v仿真模型
- **AND** BmbMasterAgent生成不同时序的访问模式
- **THEN** 测试必须验证DDR2 tRCD、tRP、tRAS等时序参数
- **AND** 确保bank激活和预充电序列的正确性
- **AND** 验证刷新操作的适时插入

#### Scenario: DDR3时序参数验证
- **WHEN** 配置为DDR3模式进行测试
- **AND** 使用Micron ddr3.v仿真模型
- **AND** BmbMasterAgent生成不同时序的访问模式
- **THEN** 测试必须验证DDR3 tRCD、tRP、tRAS等时序参数
- **AND** 确保bank激活和预充电序列的正确性
- **AND** 验证刷新操作的适时插入

#### Scenario: DDR2/DDR3模型集成验证
- **WHEN** 测试不同DDR标准的兼容性
- **AND** 包括DDR2和DDR3之间的模型切换
- **THEN** 测试必须验证Micron仿真模型的正确集成
- **AND** 确保每种标准的特定功能正常工作
- **AND** 验证标准特定的时序约束

### Requirement: BmbToDdrBridge并发事务和错误处理测试
BmbToDdrBridge测试框架SHALL验证多源ID并发访问的正确性和错误处理机制，确保系统在各种条件下的鲁棒性。

#### Scenario: 多源ID并发访问
- **WHEN** 多个BMB源同时发起DDR访问
- **AND** 具有不同的源ID和事务优先级
- **THEN** 测试必须验证所有事务的正确处理
- **AND** 确保响应与正确的源ID关联
- **AND** 验证事务间的隔离性和一致性

#### Scenario: 事务调度和仲裁测试
- **WHEN** 存在资源竞争的并发事务
- **AND** 需要访问相同的DDR bank或资源
- **THEN** 测试必须验证仲裁算法的公平性
- **AND** 确保死锁避免机制的有效性
- **AND** 验证调度策略的性能优化效果

#### Scenario: 超时错误处理
- **WHEN** 模拟DDR访问超时条件
- **AND** 超过配置的最大等待时间
- **THEN** 测试必须验证超时检测的及时性
- **AND** 确保错误响应的正确生成
- **AND** 验证错误恢复机制的有效性

### Requirement: BmbToDdrBridge性能基准测试
BmbToDdrBridge测试框架SHALL提供性能基准测试，验证桥接器在不同负载条件下的性能表现。

#### Scenario: 高带宽传输测试
- **WHEN** 进行连续的高带宽数据传输
- **AND** 最大化BMB总线和DDR接口利用率
- **THEN** 测试必须测量并验证实际传输带宽
- **AND** 确保达到预期的性能指标
- **AND** 验证带宽限制的合理性

#### Scenario: 随机访问模式测试
- **WHEN** BmbMasterAgent生成随机访问模式
- **AND** 模拟真实应用的访问行为
- **THEN** 测试必须验证随机访问的性能表现
- **AND** 确保访问模式的适应性和效率
- **AND** 测量平均延迟和延迟分布

#### Scenario: 性能回归测试
- **WHEN** 进行代码更改或优化
- **AND** 需要验证性能未退化
- **THEN** 测试必须提供性能基准比较
- **AND** 确保关键性能指标的维持
- **AND** 验证优化措施的有效性

## Requirement: 简化DFI PHY架构
DFI PHY组件必须采用简化的3模块架构（UnifiedAdapter、DataManager、ControlManager），通过合并相关功能模块减少复杂度，同时保持DFI 3.1合规性和多标准支持。

#### Scenario: 合并适配器模块
- **WHEN** 设计PHY架构时合并DfiAdapter和StandardAdapter
- **AND** 创建UnifiedAdapter统一处理协议转换和标准适配
- **THEN** 必须减少模块实例化数量
- **AND** 简化模块间接口连接

#### Scenario: 合并数据和时序管理
- **WHEN** 合并TimingGenerator和DataPath为DataManager
- **AND** 统一管理时序控制和数据流
- **THEN** 必须优化关键路径时序
- **AND** 减少延迟开销

#### Scenario: 合并控制功能
- **WHEN** 合并CalibrationEngine和InitializationManager为ControlManager
- **AND** 统一处理校准和初始化
- **THEN** 必须简化状态机逻辑
- **AND** 提高调试可维护性

## Requirement: 多标准DDR PHY支持
DFI PHY组件必须通过配置参数支持多种DDR存储器标准，包括DDR2、DDR3、DDR4以及LP系列（LPDDR2、LPDDR3、LPDDR4）。

#### Scenario: 标准配置和切换
- **WHEN** 系统需要支持不同DDR标准与DfiController集成
- **AND** 提供相应的配置参数
- **THEN** PHY必须根据配置正确适配目标DDR标准的电气特性和协议要求
- **AND** 支持运行时标准切换（在系统复位后）

#### Scenario: 向后兼容性
- **WHEN** 使用旧版DDR标准
- **AND** 与DfiController集成
- **THEN** PHY必须提供兼容接口和时序
- **AND** 确保与现有DFI基础设施的互操作性

## Requirement: DFI 3.1协议兼容性
DFI PHY组件必须完全兼容DFI 3.1规范，支持所有必需的接口组和时序参数。

#### Scenario: 控制接口合规性
- **WHEN** DfiController通过dfi接口发送命令
- **AND** 符合DFI 3.1控制接口规范
- **THEN** PHY必须正确解析和响应所有命令类型
- **AND** 维持必需的时序关系（tctrl_delay、tcmd_lat等）

#### Scenario: 数据接口合规性
- **WHEN** DfiController通过dfi接口传输读写数据
- **AND** 符合DFI 3.1数据接口规范
- **THEN** PHY必须正确处理所有数据传输模式
- **AND** 支持频率比系统（1:1、1:2、1:4）

#### Scenario: 训练接口合规性
- **WHEN** DfiController通过dfi接口发起训练操作
- **AND** 符合DFI 3.1训练接口规范
- **THEN** PHY必须支持读训练、写电平校准和CA训练
- **AND** 正确响应训练请求和状态查询

## Requirement: JEDEC标准时序支持
DFI PHY组件必须支持JEDEC标准定义的DDR时序参数和操作模式。

#### Scenario: DDR3时序合规性
- **WHEN** 配置为DDR3模式
- **AND** 符合JESD79-3规范
- **THEN** PHY必须实现所有必需的时序参数
- **AND** 支持DDR3特有的功能特性

#### Scenario: DDR4时序合规性
- **WHEN** 配置为DDR4模式
- **AND** 符合JESD79-4规范
- **THEN** PHY必须实现所有必需的时序参数
- **AND** 支持DDR4特有的功能特性（DBI、CRC、CA奇偶校验等）

#### Scenario: LPDDR时序合规性
- **WHEN** 配置为LPDDR模式
- **AND** 符合JESD209系列规范
- **THEN** PHY必须实现低功耗DDR特有的时序要求
- **AND** 支持LPDDR特有的电源管理功能

## Requirement: PHY接口标准化
DFI PHY组件必须提供标准化的接口，支持不同应用场景和配置需求。

#### Scenario: 配置接口
- **WHEN** 系统集成PHY组件与DfiController
- **AND** 需要自定义配置
- **THEN** PHY必须提供清晰的参数化配置接口
- **AND** 支持编译时和运行时配置

#### Scenario: 状态和监控
- **WHEN** DfiController需要监控PHY状态
- **AND** 通过dfi接口查询状态
- **THEN** PHY必须提供状态查询接口
- **AND** 支持调试和诊断功能

#### Scenario: 错误报告
- **WHEN** DfiController需要错误信息
- **AND** 通过dfi接口报告错误
- **THEN** PHY必须提供错误检测和报告机制
- **AND** 支持错误恢复和处理

## Requirement: 高级PHY特性支持
DFI PHY组件必须支持现代DDR接口所需的高级特性。

#### Scenario: 数据总线反转（DBI）
- **WHEN** 启用DBI功能
- **AND** 传输数据过程中
- **THEN** PHY必须正确处理读写DBI操作
- **AND** 维持数据完整性

#### Scenario: 循环冗余校验（CRC）
- **WHEN** 启用CRC功能
- **AND** 传输写数据
- **THEN** PHY必须支持CRC生成和验证
- **AND** 正确报告CRC错误

#### Scenario: 命令地址奇偶校验
- **WHEN** 启用CA奇偶校验
- **AND** 发送命令
- **THEN** PHY必须支持奇偶校验生成和验证
- **AND** 正确处理校验错误

## Requirement: 性能和资源优化
DFI PHY组件必须在性能和资源使用之间提供良好的平衡。

#### Scenario: 高性能操作
- **WHEN** 在高频条件下运行
- **AND** 需要最大化带宽利用
- **THEN** PHY必须优化关键路径时序
- **AND** 最小化延迟开销

#### Scenario: 资源效率
- **WHEN** 在资源受限环境中部署
- **AND** 需要最小化FPGA资源使用
- **THEN** PHY必须提供可配置的资源使用选项
- **AND** 支持不同优化级别的实现

## Requirement: 测试和验证支持
DFI PHY组件必须提供完整的测试和验证支持。

#### Scenario: 仿真验证
- **WHEN** 进行系统级仿真与DfiController
- **AND** 需要PHY行为模型
- **THEN** PHY必须提供仿真模型
- **AND** 支持不同抽象级别的验证

#### Scenario: 硬件测试
- **WHEN** 进行与DfiController的硬件测试
- **AND** 需要测试接口
- **THEN** PHY必须提供测试模式和接口
- **AND** 支持生产测试和诊断

## Requirement: 文档和示例
DFI PHY组件必须提供完整的文档和示例代码。

#### Scenario: 使用文档
- **WHEN** 开发者需要集成PHY与DfiController
- **AND** 查找使用指南
- **THEN** 必须提供详细的使用文档
- **AND** 包含配置和调试指南

#### Scenario: 示例代码
- **WHEN** 开发者需要参考实现
- **AND** 查找如何与DfiController集成的示例
- **THEN** 必须提供完整的工作示例
- **AND** 涵盖与DfiController的集成场景

## Requirement: DFI 3.1完整接口支持
DFI PHY组件必须实现DFI 3.1规范定义的所有接口信号组和功能，作为DfiController的slave接收DFI信号。

#### Scenario: DFI接口连接
- **WHEN** DfiController的dfi接口连接到PHY
- **AND** DfiController.dfi (master) <> DfiDdrPhy.dfi (slave)
- **THEN** PHY必须正确接收所有DFI 3.1接口信号
- **AND** 作为slave响应DfiController的命令

#### Scenario: 控制接口完整实现
- **WHEN** DfiController发送命令通过dfi接口
- **AND** 使用DFI 3.1控制接口信号
- **THEN** PHY必须正确响应所有控制信号
- **AND** 支持DDR技术特定的信号（dfi_act_n、dfi_bg、dfi_cid等）

#### Scenario: 数据接口完整实现
- **WHEN** 传输读写数据
- **AND** 使用DFI 3.1数据接口信号
- **THEN** PHY必须正确处理写数据（dfi_wrdata、dfi_wrdata_en等）
- **AND** 正确生成读数据（dfi_rddata、dfi_rddata_valid等）

#### Scenario: 状态接口完整实现
- **WHEN** 系统初始化和监控
- **AND** 使用DFI 3.1状态接口信号
- **THEN** PHY必须提供初始化状态（dfi_init_start、dfi_init_complete）
- **AND** 支持频率比配置（dfi_freq_ratio）
- **AND** 支持时钟控制（dfi_dram_clk_disable）

#### Scenario: 训练接口完整实现
- **WHEN** DfiController通过dfi接口发起训练操作
- **AND** 使用DFI 3.1训练接口信号
- **THEN** PHY必须支持读训练（dfi_rdlvl_*信号）
- **AND** 支持写电平校准（dfi_wrlvl_*信号）
- **AND** 支持CA训练（dfi_calvl_*信号）

#### Scenario: 高级接口完整实现
- **WHEN** DfiController通过dfi接口使用高级功能
- **AND** 使用DFI 3.1高级接口信号
- **THEN** PHY必须支持低功耗控制（dfi_lp_*信号）
- **AND** 支持错误接口（dfi_error、dfi_error_info）
- **AND** 支持更新接口（dfi_ctrlupd_*、dfi_phyupd_*信号）

## Requirement: 频率比系统支持
DFI PHY组件必须支持DFI 3.1定义的频率比系统（1:1、1:2、1:4）。

#### Scenario: 1:2频率比操作
- **WHEN** DfiController配置为1:2频率比
- **AND** 通过dfi接口传输
- **THEN** PHY必须正确处理相位特定信号（_p0、_p1）
- **AND** 支持双相命令执行

#### Scenario: 1:4频率比操作
- **WHEN** DfiController配置为1:4频率比
- **AND** 通过dfi接口传输
- **THEN** PHY必须正确处理相位特定信号（_p0、_p1、_p2、_p3）
- **AND** 支持四相命令执行

#### Scenario: 数据字管理
- **WHEN** DfiController在频率比系统中传输数据
- **AND** 通过dfi接口使用数据字特定信号
- **THEN** PHY必须正确处理读数据字（_w0、_w1等）
- **AND** 维持正确的读数据旋转顺序

## Requirement: 完整训练操作支持
DFI PHY组件必须支持DFI 3.1定义的所有训练操作。

#### Scenario: 读训练操作
- **WHEN** DfiController通过dfi接口发起读训练
- **AND** 包括门训练和数据眼训练
- **THEN** PHY必须支持DDR4、DDR3、LPDDR3、LPDDR2的读训练
- **AND** 正确处理dfi_lvl_pattern编码
- **AND** 支持长短训练序列（dfi_lvl_periodic）

#### Scenario: 写电平校准
- **WHEN** DfiController通过dfi接口发起写电平校准
- **AND** 针对DDR4、DDR3、LPDDR3
- **THEN** PHY必须支持写电平训练序列
- **AND** 正确处理dfi_wrlvl_*信号
- **AND** 支持芯片选择特定的训练

#### Scenario: CA训练操作
- **WHEN** DfiController通过dfi接口发起CA训练
- **AND** 针对LPDDR3系统
- **THEN** PHY必须支持CA训练序列
- **AND** 正确处理dfi_calvl_*信号
- **AND** 支持多模式寄存器训练

## Requirement: 高级DDR功能支持
DFI PHY组件必须支持现代DDR接口的高级功能。

#### Scenario: 数据总线反转（DBI）
- **WHEN** DfiController通过dfi接口启用DBI功能
- **AND** 传输读写数据
- **THEN** PHY必须支持写DBI（dfi_wrdata_mask作为DBI）
- **AND** 支持读DBI（dfi_rddata_dbi_n）
- **AND** 正确处理DBI生成模式（MC或PHY生成）

#### Scenario: 循环冗余校验（CRC）
- **WHEN** DfiController通过dfi接口启用CRC功能
- **AND** 传输写数据
- **THEN** PHY必须支持CRC生成和验证
- **AND** 正确处理CRC模式（phycrc_mode）
- **AND** 支持CRC错误报告（dfi_alert_n）

#### Scenario: 命令地址奇偶校验
- **WHEN** DfiController通过dfi接口启用CA奇偶校验
- **AND** 发送命令
- **THEN** PHY必须支持奇偶校验生成（dfi_parity_in）
- **AND** 支持奇偶校验错误检测和报告
- **AND** 正确处理校验错误状态

## Requirement: LiteX USPHY架构兼容性
DFI PHY组件必须参考LiteX USPHY实现，确保架构一致性。

#### Scenario: 模块化设计
- **WHEN** 设计PHY架构
- **AND** 参考usphy.py实现
- **THEN** PHY必须采用分层架构设计
- **AND** 包含DFI适配器、标准适配器、时序生成器等模块

#### Scenario: 配置管理
- **WHEN** 配置PHY参数
- **AND** 参考LiteX配置方式
- **THEN** PHY必须提供类似LiteX的配置接口
- **AND** 支持运行时参数调整

#### Scenario: 校准和训练
- **WHEN** 执行校准和训练
- **AND** 参考LiteX训练流程
- **THEN** PHY必须实现类似的训练状态机
- **AND** 支持增量训练和完全重新训练