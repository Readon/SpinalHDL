## Why

当前DFI PHY架构存在过度模块化的复杂性问题。现有实现包含6个主要子模块（DfiAdapter、StandardAdapter、TimingGenerator、DataPath、CalibrationEngine、InitializationManager），每个模块内部又有多个嵌套子组件，导致：

- 模块间接口复杂，增加了维护难度
- 调试和测试变得困难
- 资源开销增加
- 代码可读性下降

通过合并相关功能模块，可以显著简化架构，同时保持DFI 3.1合规性和多标准支持。

## What Changes

- **合并适配器模块**: 将DfiAdapter和StandardAdapter合并为UnifiedAdapter，统一处理DFI协议转换和标准适配
- **合并数据和时序管理**: 将TimingGenerator和DataPath合并为DataManager，统一管理时序控制和数据流
- **合并控制功能**: 将CalibrationEngine和InitializationManager合并为ControlManager，统一处理校准和初始化
- **简化主PHY组件**: 更新DfiDdrPhy使用新的3模块架构，减少组件实例化和连接复杂度

**BREAKING**: 接口保持兼容，但内部架构重构可能影响调试接口。

## Impact

- **Affected specs**: 更新dfi-ddr3-phy规范，反映简化后的架构设计
- **Affected code**:
  - `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/` - 重构为3模块架构
  - 移除过度模块化的子组件，合并相关功能
  - 简化模块间接口和连接逻辑
- **用户影响**: API保持不变，但内部实现更高效，调试接口可能调整