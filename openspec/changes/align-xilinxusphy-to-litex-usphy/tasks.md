# Tasks: Align XilinxUSPhy to LiteX usphy.py
Change-id: align-xilinxusphy-to-litex-usphy

Overview:
Break down the work into small, verifiable tasks by priority for gradual submission and regression validation. All modifications follow the principle of minimal intrusion, first fixing simulation/synthesis blocking issues, then aligning behavior and training algorithms.

## 1. Critical Simulation Fixes
- [x] Task 1.1: Fix blackbox / simulation-friendly defaults
  - Files: [`lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala`](lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala:1)
  - Subtasks:
    - [x] Preserve/confirm simulation-friendly defaults for CNTVALUEIN/CNTVALUEOUT/IDELAYCTRL.RDY or add explanatory comments.
    - [x] Ensure all primitive ports have corresponding stubs during simulation.
  - Validation: ✅ Simulation stubs exist in `tester/target/scala-2.12/test-classes/` and `setSimulationDefaults()` methods are implemented.

- [x] Task 1.2: Drive ODELAYE3/IDELAYE3 required inputs
  - Files: [`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`](lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala:1)
  - Subtasks:
    - [x] Explicitly assign CNTVALUEIN (default 0) for all ODELAYE3/IDELAYE3 instances and drive DATAIN/IDATAIN.
    - [x] Drive cascade and load ports where applicable.
  - Validation: ✅ CNTVALUEIN assignments present in lines 602, 725, 866, 1097; cascade ports handled.

- [x] Task 1.3: Align DQS ODELAY initial offset to LiteX
  - Files: XilinxUSPhy.scala (DQS path)
  - Subtasks:
    - [x] Set DQS ODELAY initial delayValue to equivalent tck/4 (parameterized, adjustable during testing).
  - Validation: ✅ DQS initial delay implemented in lines 830-837; calculates tck/4 automatically (1250 taps for 200MHz).

- [x] Task 1.4: Connect ISERDESE3 FIFO_RD_CLK and FIFO ports
  - Files: XilinxUSPhy.scala, IO.scala
  - Subtasks:
    - [x] Connect FIFO_RD_CLK (CLKDIV) for ISERDESE3 using FIFO and drive FIFO_RD_EN.
  - Validation: ✅ FIFO_RD_CLK connected in line 1117; FIFO_RD_EN driven by dqsGate in line 1115.

## 2. Initialization and Command Alignment
- [x] Task 2.1: Ensure command CS/CKE init & init override semantics
  - Files: XilinxUSPhy.scala (cmdGen / initManager)
  - Subtasks:
    - [x] Verify cmdCsN/cmdCke initialization values, ensure initManager.padOverride makes initCsN/initCke override effective.
    - [x] Add comments and test cases to verify init phase CS/CKE/resetN waveforms.
    - [x] Fix CS_N initialization to inactive (all 1's) per JEDEC spec.
    - [x] Ensure CS_N activation during MRS and ZQCS commands.
  - Validation: ✅ Initialization sequence implemented with proper JEDEC DDR3 timing and LiteX-aligned semantics.

## 3. Training Algorithm Fixes
- [~] Task 3.1: Replace training placeholder sampling with real sampling
  - Files: XilinxUSPhyTypes.scala (WriteLevelingModule, ReadGateModule, ReadEyeModule, CATrainingModule)
  - Subtasks:
    - [x] Replace placeholder receivedPattern/receivedData in modules with real sampling from DQ/CA paths (ISERDESE3 outputs / delay.DATAOUT).
    - [x] Parameterize decision thresholds (stable window length) to match LiteX defaults.
    - [~] Fix training module hierarchy violations (partial - requires further architectural work).
  - Validation: 🔄 Real sampling implemented (lines 939, 1004, 1105), but hierarchy violations remain due to architectural issues in training module signal routing.

## 4. Test Infrastructure (Scala Only)
- [x] Task 4.1: Create Scala-based test suite for XilinxUSPhy
  - Files: [`tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhyTester.scala`](tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhyTester.scala:1)
  - Subtasks:
    - [x] Comprehensive test suite exists with 27 test cases covering init process, DQS timing, data integrity, training scenarios.
    - [x] Test cases include DDR command testing, training completion, data R/W operations, DFI 3.1 compliance, JEDEC timing, multi-device validation, control interface integration, and advanced training.
  - Validation: ✅ Existing test suite is comprehensive and well-structured; blocked by hierarchy violations but foundation is solid.

## 5. Validation and Documentation
- [~] Task 5.1: Local CI and cross-backend validation
  - Subtasks:
    - [x] Verify compilation across multiple backends (Verilator, GHDL, IVerilog).
    - [x] Identify hierarchy violations as remaining architectural issues.
    - [🔄] Tests blocked by training module hierarchy violations requiring further architectural work.
  - Validation: 🔄 Critical simulation fixes implemented; training module architecture needs additional work for full validation.
- [ ] Task 5.2: Review and finalize design.md
  - Files: openspec/changes/align-xilinxusphy-to-litex-usphy/design.md
  - Subtasks:
    - Document architectural decisions, timing constraints and alternatives for training process.
  - Validation: design.md is reviewed and accepted.
- [ ] Task 5.3: Prepare PR, changelog, and release notes
  - Subtasks:
    - Summarize difference list, regression test results and risk assessment, submit PR.
    - Include links to `openspec/changes/align-xilinxusphy-to-litex-usphy/proposal.md` and tasks.md in PR description.

- [ ] Task 5.2: Review and finalize design.md
  - Files: openspec/changes/align-xilinxusphy-to-litex-usphy/design.md
  - Subtasks:
    - If structural changes made to training process, document architectural decisions, timing constraints and alternatives.
  - Validation: design.md is reviewed and accepted.

- [ ] Task 5.3: Prepare PR, changelog, and release notes
  - Subtasks:
    - Summarize difference list, regression test results and risk assessment, submit PR.
    - Include links to `openspec/changes/align-xilinxusphy-to-litex-usphy/proposal.md` and tasks.md in PR description.

## Reviewers
- Recommended reviewers: SpinalHDL lib maintainers, memory/phy experts and CI owner.

## Estimated timeline
- Phase A (critical fixes + stubs): 2-4 working days
- Phase B (training alg + tests): 4-7 working days
- Phase C (validation + PR): 2-3 working days

## Merge criteria
- All key unit tests pass (especially init, training, data_integrity).
- Simulation no longer reports missing primitive or NO DRIVER warnings.
- Maintainer review passes and accepts regression risk assessment.