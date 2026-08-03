# DfiController 支持配置边界（DDR4 命令总线不支持；cmdPhase≠0 已支持）

`DfiController`（`lib/.../sdram/dfi`）在 elaboration 时断言拒绝启用 DDR4 命令总线
（`useCid`/`useAckN`/`useBg`）的配置；`cmdPhase != 0` 已自 `b1fee0c71` 起支持
（`DfiController_DDR3_fr2_cmdPhase1`、`DfiController_DDR3_fr4_cmdPhase1` 测试通过）。

**背景**：`DfiSignalConfig`/`DfiTimeConfig` 声明了 DDR4 与多相位命令的接口能力，但控制器实现
只对 DDR3 及以下（固定 RAS/CAS/WE 三比特编码、无 cid/act_n/bg）做过验证。

- **cmdPhase≠0**（原 ADR 判定不支持，`b65156807`）：读数据对齐曾假设 `cmdPhase == 0`。
  `b1fee0c71` 放宽断言并改造 `RdAlignment`（动态跟踪承载首个 burst 分片的相位 lane）与
  `DfiMemoryAgent` 参考模型（按相位解码命令 + 同套 lane 跟踪），fr2/fr4 的 cmdPhase1 测试已实证。
- **DDR4 命令总线**：仍不支持。实现为固定 RAS/CAS/WE 三比特编码，无 cid/act_n/bg 驱动；
  在无 DDR4 参考模型的条件下宣称支持是虚假的安全感，故以断言把支持边界划清：配置越界时
  以明确报错替代静默的 elaboration 崩溃或错位数据。

**被否决的替代方案**：
- 完整支持 DDR4：需要动态命令编码 + bg/cid/actN 驱动 + DDR4 参考模型，且无验证锚点，
  作为独立任务另行立项。
- 保留半支持状态（命令侧感知 cmdPhase、数据侧不感知）：正是早期 ADR 要消除的隐患，已随
  cmdPhase≠0 支持（`b1fee0c71`）消除。

**后续影响**：DDR4 配置的使用者会收到明确的 elaboration 错误；未来如需支持 DDR4，
应以 DDR4 命令编码 + 参考模型的验证收尾。
