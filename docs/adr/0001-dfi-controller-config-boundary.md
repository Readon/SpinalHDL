# DfiController 支持配置边界（DDR4 命令总线 / cmdPhase≠0 暂不支持）

`DfiController`（`lib/.../sdram/dfi`）在 elaboration 时断言拒绝两类配置：启用 DDR4 命令总线
（`useCid`/`useAckN`/`useBg`）的配置，以及 `cmdPhase != 0` 的配置。

**背景**：`DfiSignalConfig`/`DfiTimeConfig` 声明了 DDR4 与多相位命令的接口能力，但控制器实现
只对 DDR3 及以下（固定 RAS/CAS/WE 三比特编码、无 cid/act_n/bg）做过验证；读数据对齐
（`RdAlignment` 的相位旋转）与 PHY 参考模型均假设 `cmdPhase == 0`。在无 DDR4/多相位参考模型
的条件下宣称支持是虚假的安全感，故以断言把支持边界划清：配置越界时以明确报错替代静默的
elaboration 崩溃或错位数据（仿真测试 `DfiController_DDR3_fr2_cmdPhase1` 已实证 cmdPhase≠0 读数据错位）。

**被否决的替代方案**：
- 完整支持 DDR4 / cmdPhase≠0：需要动态命令编码 + bg/cid/actN 驱动 + RdAlignment 相位跟踪改造 +
  cmdPhase 感知的 PHY 参考模型，且无验证锚点，作为独立任务另行立项。
- 保留半支持状态（命令侧感知 cmdPhase、数据侧不感知）：正是本 ADR 要消除的隐患。

**后续影响**：`cmdPhase != 0` 或 DDR4 配置的使用者会收到明确的 elaboration 错误；未来如需支持，
应同时修复 `RdAlignment` 相位跟踪与 `DfiMemoryAgent` 的相位模型，并以 cmdPhase≠0 的参考验证收尾。
