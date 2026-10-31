# ADR-0016: DFI 管理平面 v1 — 静态配置 + 最小动态信号（init/start/complete）

- Status: Accepted
- Date: 2026-10-31
- Decider: 用户（裁定「最小版可以」）+ pi

## Decision

补齐 DFI 6.0.1 中「Required for all DRAMs」的管理平面信号（`dfi_init_start` /
`dfi_init_complete`），范围取**最小版**：

1. **配置开关**：三个 E2e 生成器（`E2eMcGen` / `UsPhyGen` / `Tc12X32Gen`）启用
   `useStatusSignals = true`（`DfiFunctionConfig`）。库存 28/28 配置不受影响
   （flag 默认关）。
2. **MC 侧（`Alignment.scala`，flag 门控）**：
   - `initStart`：复位释放沿打一拍单周期脉冲（`RegInit(True)` 下一拍清 0）；
   - `freqRatio`：常量驱动 `U(cfg.frequencyRatio, 2 bits)`；
   - `parityIn` / `dataByteDisable` / `dramClkDisable`：清零（不使用）。
3. **PHY 侧（`UsPhy.scala`，flag 门控）**：
   - `initComplete := True`（常高，诚实建模：PHY 无内部初始化状态，上电即可用）；
   - `alertN.setAll()`（无告警源，常去断言）；
   - **不响应 `initStart`**（不做 dramPhase 重锚定）：v1 无频率切换场景，
     重锚定在中途流量下有破坏相位对应关系的风险而无可观测量益。
4. **验证**：TC10/TC11/TC12 各增 `INITCHK2` 七字段检查（initComplete、
   initStart 脉冲观测、freqRatio==2'b10、alertN、parityIn、dataByteDisable、
   dramClkDisable）；反证：netlist 突变 `initComplete` 恒 0 → 恰 1 条
   MGMT 错误（无级联），变体用完即删。

## Context

2026-10-31 规格符合性审计（DFI 6.0.1 全信号映射）发现：PHY 仅消费
control/write/read/training 四个通道，status 通道完全缺席，而
`dfi_init_start`/`dfi_init_complete` 在 DFI 6.0.1 中标注 **Required for all
DRAMs**——是唯一强制缺口。可选信号（freq_ratio 变化、update iface、
phymngd、低功耗组）依项目定位（自发生钟、无运行时重训需求）评估为 v2+。

## Consequences

- 端口经 `Dfi` bundle 扩展（`useStatusSignals` 门控），**无新增顶层独立端口**
  → ADR-0012 顶层端口冻结维持不破。
- 28/28 DfiControllerTester 零回归（库存配置 flag 关闭，行为不变）。
- TC10/11/12 全绿且 `INITCHK2` 全字段正确（x16: dbDis=4'b0000；x32:
  dbDis=8'b0）。
- **明确推迟（v2+，非欠账）**：
  - 频率变更协议（需 MMCM，见 ADR-0006 自包含时钟边界）；
  - `update` 接口（MC 侧上游亦未实现）；
  - `phymngd`（不在 DFI 6.0.1 标准 bundle 内）；
  - 奇偶校验检测、`dramClkDisable` 的实际时钟门控语义。
