## MODIFIED Requirements

### Requirement: LiteX USPHY架构兼容性
DFI PHY组件SHALL参考LiteX USPHY实现，确保架构一致性。

#### Scenario: XilinxUSPhy与LiteX usphy.py对齐
- **WHEN** 修改XilinxUSPhy实现以对齐LiteX usphy.py
- **AND** 确保所有关键功能域保持一致
- **THEN** XilinxUSPhy必须实现与LiteX相同的DQS初始延迟(tck/4)
- **AND** 必须显式驱动所有ODELAYE3/IDELAYE3的CNTVALUEIN端口
- **AND** 必须连接ISERDESE3的FIFO_RD_CLK到CLKDIV
- **AND** 必须确保初始化期间CS/CKE覆盖逻辑正确
- **AND** 必须替换训练模块中的占位式采样为真实采样

#### Scenario: 仿真兼容性修复
- **WHEN** 修复仿真警告和错误
- **AND** 确保所有primitive端口都有驱动
- **THEN** 必须消除"NO DRIVER"警告
- **AND** 必须提供完整的Verilog stub文件
- **AND** 必须保持仿真友好的BlackBox默认值

#### Scenario: 训练算法对齐
- **WHEN** 实现训练模块
- **AND** 参考LiteX训练流程
- **THEN** 必须使用真实采样数据链路
- **AND** 必须实现与LiteX一致的判决阈值
- **AND** 必须支持write-leveling、read-gate、read-eye和CA训练