## Why

SpinalHDL目前缺少标准化的DFI接口DDR PHY组件。虽然在tester模块中存在demo实现，但缺乏生产就绪的标准化PHY组件来与现有的DfiController集成。添加多标准DDR PHY组件将提供：

- **标准化接口**: 为不同DDR标准（DDR2/3/4, LPDDR系列）提供统一的DFI 3.1接口
- **生产就绪**: 将demo代码标准化为可在生产环境中使用的组件
- **与DfiController集成**: 作为slave组件无缝连接到现有的DfiController
- **LiteX兼容性**: 参考成熟的LiteX USPHY架构确保可靠性

## What Changes

- **ADDED**: 新增多标准DFI DDR PHY组件，支持DDR2、DDR3、DDR4、LPDDR2、LPDDR3、LPDDR4
- **ADDED**: 完整的DFI 3.1协议实现，包括所有接口组（控制、数据、状态、训练、低功耗、错误）
- **ADDED**: JEDEC标准时序支持（JESD79系列规范）
- **ADDED**: 高级DDR功能支持（DBI、CRC、CA奇偶校验）
- **ADDED**: 频率比系统支持（1:1、1:2、1:4 MC:PHY频率比）
- **ADDED**: 完整训练操作支持（读训练、写电平校准、CA训练）
- **ADDED**: 模块化架构设计，参考LiteX USPHY实现
- **ADDED**: 标准化配置和监控接口
- **ADDED**: 完整测试和验证支持

## Impact

- **Affected specs**: 新增 `dfi-ddr3-phy` 功能规范
- **Affected code**: 新增PHY组件到 `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/`
- **Migration**: 无破坏性变更，现有的DfiController和DFI基础设施保持不变
- **Dependencies**: 依赖现有的DFI接口定义和DfiController组件
- **Testing**: 需要新增测试套件验证与DfiController的集成
- **Documentation**: 需要用户文档和集成指南