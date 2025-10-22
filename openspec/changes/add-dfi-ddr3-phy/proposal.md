## Why

SpinalHDL目前已有完整的DFI（DDR PHY Interface）基础设施，包括`DfiController`控制器，该控制器通过`dfi`接口（`master(Dfi(dfiConfig))`）连接到PHY。现有的DDR3 PHY实现在`tester/src/test/scala/spinal/demo/phy/`目录中仅作为参考实现。为了提供给用户一个可直接与`DfiController`配合使用的、生产级别的多标准DDR PHY组件，需要在主库中创建新的实现，支持多DDR标准（DDR2、DDR3、DDR4、LPDDR系列），确保完全兼容DFI 3.1规范，参考LiteX USPHY实现和现有demo代码的设计思路。

## What Changes

- **创建新的PHY实现**: 在`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/`中创建标准化的多标准DDR PHY组件
- **参考现有设计**: 参考`tester/src/test/scala/spinal/demo/phy/`中的demo代码设计思路和架构模式
- **实现多标准支持**: 创建支持DDR2/3/4和LPDDR系列的PHY实现
- **确保DFI 3.1合规性**: 实现完全兼容DFI 3.1规范的接口和功能
- **参考LiteX USPHY**: 参考usphy.py实现进行架构设计和优化
- **标准化配置接口**: 提供灵活的配置接口适应不同应用场景

**BREAKING**: 无，此为新增功能，不影响现有代码。

## Impact

- **Affected specs**: 新增`dfi-ddr3-phy`能力规范，支持多标准和DFI 3.1合规性
- **Affected code**:
  - `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/` - 新增多标准PHY实现
  - `lib/src/main/scala/spinal/lib/memory/sdram/dfi/` - 扩展配置和接口支持
  - `tester/src/test/scala/spinal/demo/phy/` - 现有demo代码作为参考保留
- **用户影响**: 用户将获得全新的标准化的多标准DFI PHY组件，可直接在生产设计中使用