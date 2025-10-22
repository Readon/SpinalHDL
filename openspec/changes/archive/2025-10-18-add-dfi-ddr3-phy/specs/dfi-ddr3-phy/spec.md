## ADDED Requirements

### Requirement: 多标准DDR PHY支持
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

### Requirement: DFI 3.1协议兼容性
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

### Requirement: JEDEC标准时序支持
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

### Requirement: PHY接口标准化
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

### Requirement: 高级PHY特性支持
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

### Requirement: 性能和资源优化
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

### Requirement: 测试和验证支持
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

### Requirement: 文档和示例
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

### Requirement: DFI 3.1完整接口支持
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

### Requirement: 频率比系统支持
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

### Requirement: 完整训练操作支持
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

### Requirement: 高级DDR功能支持
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

### Requirement: LiteX USPHY架构兼容性
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