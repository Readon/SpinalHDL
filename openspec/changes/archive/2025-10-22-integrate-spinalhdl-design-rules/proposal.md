## Why
SpinalHDL用户指南包含了丰富的设计规则、编码原则和最佳实践，但这些内容尚未完全整合到openspec的全局编码标准中。为了确保开发团队遵循一致的硬件设计规范，需要将这些经验证的设计原则无缝整合到现有规范中。

## What Changes
- **ADDED** 基础数据类型使用标准和最佳实践
- **ADDED** 时序逻辑设计模式和寄存器使用规范  
- **ADDED** 时钟域管理和多时钟设计原则
- **ADDED** 状态机和流水线设计高级模式
- **ADDED** 调试、优化和错误处理指南
- **ADDED** 模拟测试和验证最佳实践
- **MODIFIED** 现有组件设计规则以增强完整性
- **MODIFIED** 类型系统规则以包含更多实践指导

## Impact
- Affected specs: `coding-standards/spec.md`
- Affected code: 所有SpinalHDL硬件设计代码
- 提高代码一致性和可维护性
- 减少常见设计错误和时序问题
- 增强团队协作和知识共享