## Context

当前DFI PHY实现采用了过度模块化的架构，包含6个主要子模块，每个模块内部又有多个嵌套的子组件。这种设计虽然提供了良好的关注点分离，但在实际使用中带来了以下问题：

- **复杂性过高**: 模块间接口众多，连接逻辑复杂
- **维护困难**: 调试时需要跟踪多个模块间的交互
- **资源开销**: 额外的模块实例化带来不必要的开销
- **学习曲线陡峭**: 新开发者需要理解6个模块及其关系

通过将相关功能合并到3个核心模块中，可以显著简化架构，同时保持功能完整性和DFI 3.1合规性。

## Goals / Non-Goals

### Goals
- 将6模块架构简化为3模块架构
- 保持DFI 3.1规范的完全合规性
- 维持多标准DDR支持（DDR2/3/4，LPDDR系列）
- 减少模块间接口复杂度
- 提高代码可维护性和可读性
- 降低资源使用开销

### Non-Goals
- 不改变外部API接口
- 不减少功能特性
- 不影响性能指标
- 不改变调试能力（可能调整接口）

## Decisions

### 架构简化策略
**Decision**: 采用功能合并策略，将相关职责的模块合并为更大的组件

**UnifiedAdapter**: 合并DfiAdapter + StandardAdapter
- 统一DFI协议解析和标准适配
- 简化DFI到内部命令的转换流程
- 减少协议转换层的复杂度

**DataManager**: 合并TimingGenerator + DataPath
- 统一时序控制和数据流管理
- 整合命令调度和数据缓冲
- 优化时序关键路径

**ControlManager**: 合并CalibrationEngine + InitializationManager
- 统一校准和初始化控制逻辑
- 整合状态机和控制流程
- 简化控制状态管理

**Rationale**: 功能合并可以减少模块间通信开销，简化调试流程，同时保持清晰的职责分离。合并后的模块仍然有明确的关注点，但接口更简洁。

### 接口设计原则
**Decision**: 保持外部接口不变，内部接口适度简化

- 外部DFI接口保持完全兼容
- DDR接口保持不变
- 调试接口可能调整但功能保持
- 内部模块间接口减少到最少

**Rationale**: 确保现有用户代码无需修改，同时简化内部实现。

### 向后兼容性保证
**Decision**: 通过适配层确保现有功能完整性

- 所有DFI 3.1接口信号继续支持
- 多标准配置保持兼容
- 训练和校准流程保持不变
- 调试信息以不同形式提供

**Rationale**: 架构简化不应影响功能完整性。

## Simplified Architecture Diagram

```mermaid
graph TB
    subgraph "DfiController (MC)"
        DFI_CTRL[DfiController<br/>dfi = master(Dfi)]
    end

    subgraph "DFI 3.1 Interface"
        DFI_IF[DFI Interface<br/>DfiController.dfi <> DfiDdrPhy.dfi]
    end

    subgraph "DfiDdrPhy (PHY)"
        DFI_SLAVE[DFI Slave Interface<br/>dfi = slave(Dfi)]
        UNIFIED_ADAPTER[UnifiedAdapter<br/>DFI解析+标准适配]
    end

    subgraph "Core Modules"
        DATA_MANAGER[DataManager<br/>时序+数据管理]
        CONTROL_MANAGER[ControlManager<br/>校准+初始化]
    end

    subgraph "DDR Interface"
        DDR_IO[DDR I/O Signals<br/>master(DdrIO)]
    end

    DFI_CTRL --> DFI_IF
    DFI_IF --> DFI_SLAVE
    DFI_SLAVE --> UNIFIED_ADAPTER
    UNIFIED_ADAPTER --> DATA_MANAGER
    UNIFIED_ADAPTER --> CONTROL_MANAGER
    DATA_MANAGER --> DDR_IO
    CONTROL_MANAGER --> DDR_IO
    CONTROL_MANAGER --> DATA_MANAGER
```

## Module Specifications

### UnifiedAdapter
**职责**: DFI协议解析、命令转换、标准适配、训练接口处理

**输入**:
- DFI接口信号 (slave)
- 配置参数

**输出**:
- 内部命令流
- 数据流控制
- 训练状态

**关键组件**:
- DFI命令解析器
- 数据适配器
- 训练处理器
- 标准转换器

### DataManager
**职责**: 时序控制、数据缓冲、格式转换、命令调度

**输入**:
- 内部命令流
- 数据流
- 时序参数

**输出**:
- DDR控制信号
- 处理后的数据流
- 时序状态

**关键组件**:
- 命令调度器
- 时序控制器
- 数据缓冲器
- 格式转换器

### ControlManager
**职责**: 初始化序列、校准流程、状态管理

**输入**:
- 初始化请求
- 校准命令
- 系统状态

**输出**:
- 初始化完成状态
- 校准结果
- 控制命令

**关键组件**:
- 初始化状态机
- 校准引擎
- 模式寄存器控制器

## Implementation Strategy

### Phase 1: UnifiedAdapter Implementation
1. 合并DfiAdapter和StandardAdapter的核心逻辑
2. 统一DFI解析和命令转换
3. 整合训练接口处理
4. 实现标准适配逻辑

### Phase 2: DataManager Implementation
1. 合并TimingGenerator和DataPath功能
2. 实现统一的时序和数据管理
3. 优化命令调度逻辑
4. 整合数据缓冲和转换

### Phase 3: ControlManager Implementation
1. 合并CalibrationEngine和InitializationManager
2. 统一状态机设计
3. 整合控制逻辑
4. 优化状态管理

### Phase 4: Integration and Testing
1. 更新主PHY组件
2. 验证功能完整性
3. 性能和资源评估
4. 文档更新

## Risks / Trade-offs

### 合并风险
- **Risk**: 合并可能引入新的bug或回归
- **Mitigation**: 逐步合并，充分测试每个阶段

### 调试复杂性
- **Risk**: 大模块可能更难调试
- **Mitigation**: 保持清晰的内部接口和调试点

### 维护性权衡
- **Trade-off**: 模块更大但数量更少
- **Decision**: 选择更少的模块以简化整体架构

### 性能影响
- **Risk**: 合并可能影响时序收敛
- **Mitigation**: 保持关键路径优化，必要时添加pipeline

## Migration Plan

### 渐进式迁移
1. **保持兼容**: 现有API完全保持不变
2. **内部重构**: 逐步替换内部实现
3. **测试验证**: 每个阶段都进行完整测试
4. **文档更新**: 更新架构文档和调试指南

### 回滚计划
- 保留原有6模块实现作为备选
- 提供配置选项在两种架构间切换
- 确保可以快速回滚到原有实现

## Success Criteria

- **功能完整性**: 所有DFI 3.1功能正常工作
- **性能保持**: 时序和资源使用不劣化
- **代码简化**: 减少50%的模块数量
- **维护性提升**: 调试和修改更加容易
- **兼容性保证**: 现有用户代码无需修改