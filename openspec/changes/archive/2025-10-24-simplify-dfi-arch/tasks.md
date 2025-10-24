## 1. 架构分析和设计阶段

### 1.1 分析当前架构复杂性
- [x] 分析现有6模块架构的复杂度：DfiAdapter、StandardAdapter、TimingGenerator、DataPath、CalibrationEngine、InitializationManager
- [x] 识别模块间接口复杂度和维护难点
- [x] 评估合并可行性和收益

### 1.2 设计简化架构
- [x] 设计3模块架构：UnifiedAdapter、DataManager、ControlManager
- [x] 定义新模块的职责划分和接口设计
- [x] 确保DFI 3.1合规性和功能完整性

### 1.3 创建change proposal
- [x] 编写proposal.md说明简化理由和影响
- [x] 定义架构重构的任务清单

## 2. 实现阶段

### 2.1 实现UnifiedAdapter模块
- [x] 创建UnifiedAdapter组件，合并DfiAdapter和StandardAdapter功能
- [x] 实现DFI协议解析和标准适配逻辑
- [x] 统一训练接口处理

### 2.2 实现DataManager模块
- [x] 创建DataManager组件，合并TimingGenerator和DataPath功能
- [x] 实现时序控制和数据流管理
- [x] 统一数据缓冲和格式转换

### 2.3 实现ControlManager模块
- [x] 创建ControlManager组件，合并CalibrationEngine和InitializationManager功能
- [x] 实现校准和初始化状态机
- [x] 统一控制逻辑和状态管理

### 2.4 更新主PHY组件
- [x] 修改DfiDdrPhy使用新的3模块架构
- [x] 更新模块实例化和连接逻辑
- [x] 调整调试接口

## 3. 验证和优化阶段

### 3.1 功能验证
- [x] 验证DFI 3.1接口合规性
- [x] 测试多标准支持（DDR2/3/4，LPDDR）
- [x] 验证训练和校准功能

### 3.2 性能和资源优化
- [x] 评估资源使用减少情况
- [x] 验证时序性能
- [x] 优化关键路径

### 3.3 文档更新
- [x] 更新架构文档反映新设计
- [x] 更新调试和使用指南
- [x] 创建迁移指南