## 修正后的测试基础设施规格说明

### 状态评估
**实际完成情况：**
- 基础测试框架：✅ 完成
- API现代化：⚠️ 部分完成
- 仿真基础设施：❌ 未完成
- 训练算法验证：❌ 未完成

### Requirement: XilinxUSPhy测试框架集成
XilinxUSPhy测试代码必须与SpinalHDL测试框架正确集成，确保所有测试能够可靠运行并提供有意义的功能验证。

#### Scenario: 测试类层次结构统一
- **WHEN** 创建XilinxUSPhy测试类
- **AND** 选择适当的测试基类
- **THEN** 测试类必须正确继承适当的测试框架基类

### 当前实现差距

#### 1. API兼容性问题（优先级：高）
- **WHEN** 使用DfiConfig API
- **AND** 参数名称与当前API匹配
- **THEN** 所有测试文件必须能够编译无DfiConfig相关错误

#### 2. 测试框架集成问题
- **WHEN** 混合使用SpinalTesterGhdlBase和SpinalSimFunSuite
- **AND** 缺少适当的测试生命周期管理
- **THEN** 测试必须能够单独运行且作为套件运行无干扰

#### 3. 仿真基础设施缺失（优先级：高）
- **WHEN** 进行XilinxUSPhy仿真测试
- **AND** 需要与DFI接口交互
- **THEN** 测试必须提供：
  - 所有DFI输入信号的驱动器
  - 所有DFI输出信号的监控器
  - 时序准确的信号生成
  - 信号完整性检查

#### 4. 训练和初始化测试（优先级：中）
- **WHEN** 验证DDR3初始化序列
- **AND** 需要JEDEC时序合规性检查
  - 初始化失败场景测试支持

### 实施优先级

**第一阶段（立即执行）：**
- 修复DfiConfig API参数使用
- 标准化测试配置参数
- 实现时钟域管理

### Requirement: 测试覆盖和验证完整性

#### Scenario: 数据路径完整性测试
- **WHEN** 验证XilinxUSPhy的读写数据路径
- **AND** 传输各种数据模式
- **THEN** 测试必须验证：
  - 写数据的正确序列化和传输
  - 读数据的正确解串和接收
- **AND** SHALL测试数据掩码和使能功能
- **AND** SHALL验证数据完整性校验机制

#### Scenario: 边界条件和异常处理
- **WHEN** 测试XilinxUSPhy的边界条件处理能力
- **AND** 施加异常操作条件
- **THEN** 测试必须验证：
  - 时序边界条件的正确处理
  - 非法命令的拒绝机制
  - 配置参数边界值验证

### 成功标准

1. **编译成功**：所有XilinxUSPhy测试文件编译无错误
- **AND** 所有测试执行完成不卡住
- **AND** 所有测试通过并提供有意义的验证

### 风险缓解

- **风险**：API变更可能破坏现有用户测试代码
  **缓解**：提供迁移指南和用户示例

### 维护责任

SpinalHDL测试基础设施维护者和XilinxUSPhy开发团队

### 参考资料
- 当前XilinxUSPhy实现：`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`
- DFI接口规范：`lib/src/main/scala/spinal/lib/memory/sdram/dfi/`
- SpinalHDL测试框架文档
- 相关OpenSpec：align-xilinxusphy-to-litex-usphy（提供XilinxUSPhy功能背景）

### Requirement: XilinxUSPhy功能验证测试
XilinxUSPhy测试SHALL提供全面的功能验证，覆盖所有主要的PHY操作和特性。

#### Scenario: DDR3命令生成测试
- **WHEN** 测试XilinxUSPhy的DDR3命令生成
- **AND** 发送各种DDR3命令
- **THEN** 测试SHALL验证ACT、READ、WRITE、PRE、REF、MRS命令的正确生成
- **AND** SHALL检查命令时序参数合规性
- **AND** SHALL验证银行和地址信号正确性
- **AND** SHALL测试命令队列和调度功能

#### Scenario: 数据路径完整性测试
- **WHEN** 验证XilinxUSPhy的读写数据路径
- **AND** 传输各种数据模式
- **THEN** 测试SHALL验证写数据的正确序列化和传输
- **AND** SHALL检查读数据的正确解串和接收
- **AND** SHALL测试数据掩码和使能功能
- **AND** SHALL验证数据完整性校验机制

#### Scenario: 训练算法验证
- **WHEN** 测试XilinxUSPhy的训练算法
- **AND** 执行各种训练操作
- **THEN** 测试SHALL验证写电平校准的正确执行
- **AND** SHALL检查读门训练的有效性
- **AND** SHALL验证读眼训练的收敛性
- **AND** SHALL测试CA训练（如果适用）的准确性

### Requirement: XilinxUSPhy性能和压力测试
XilinxUSPhy测试SHALL包括性能验证和压力测试，确保PHY在各种条件下都能可靠工作。

#### Scenario: 长期稳定性测试
- **WHEN** 执行XilinxUSPhy长期运行测试
- **AND** 持续进行DDR操作
- **THEN** 测试SHALL验证PHY在长时间运行下的稳定性
- **AND** SHALL检查内存泄漏和资源释放
- **AND** SHALL监控时序漂移和性能退化
- **AND** SHALL验证错误恢复机制

#### Scenario: 多后端兼容性测试
- **WHEN** 使用不同仿真后端运行XilinxUSPhy测试
- **AND** 切换仿真工具
- **THEN** 测试SHALL在Verilator后端下正确运行
- **AND** SHALL在GHDL后端下提供一致结果
- **AND** SHALL支持IVerilog等其他后端
- **AND** SHALL处理后端特定的差异和限制

#### Scenario: 边界条件和异常处理测试
- **WHEN** 测试XilinxUSPhy的边界条件处理
- **AND** 施加异常操作条件
- **THEN** 测试SHALL验证时序边界条件的正确处理
- **AND** SHALL检查非法命令的拒绝机制
- **AND** SHALL测试配置参数边界值
- **AND** SHALL验证错误报告和恢复功能

### Requirement: XilinxUSPhy测试可维护性
XilinxUSPhy测试代码SHALL具有良好的可维护性，易于理解、修改和扩展。

#### Scenario: 测试代码清晰性
- **WHEN** 开发者阅读XilinxUSPhy测试代码
- **AND** 需要理解测试逻辑
- **THEN** 测试代码SHALL具有清晰的命名约定
- **AND** SHALL包含充分的注释和文档
- **AND** SHALL使用一致的代码结构
- **AND** SHALL提供测试意图的明确说明

#### Scenario: 测试配置灵活性
- **WHEN** 用户需要调整XilinxUSPhy测试配置
- **AND** 修改测试参数
- **THEN** 测试配置SHALL支持参数化调整
- **AND** SHALL提供配置模板和示例
- **AND** SHALL允许运行时配置修改
- **AND** SHALL支持测试场景的组合和扩展

#### Scenario: 测试结果分析和报告
- **WHEN** 分析XilinxUSPhy测试结果
- **AND** 生成测试报告
- **THEN** 测试框架SHALL提供详细的测试执行日志
- **AND** SHALL生成易于理解的测试摘要
- **AND** SHALL包含覆盖率统计和性能指标
- **AND** SHALL支持测试结果的比较和趋势分析