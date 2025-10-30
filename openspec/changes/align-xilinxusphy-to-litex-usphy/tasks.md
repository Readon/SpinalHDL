# Tasks: Align XilinxUSPhy to LiteX usphy.py
Change-id: align-xilinxusphy-to-litex-usphy

Overview:
Break down the work into small, verifiable tasks by priority for gradual submission and regression validation. All modifications follow the principle of minimal intrusion, first fixing simulation/synthesis blocking issues, then aligning behavior and training algorithms.

## 1. Critical Simulation Fixes
- [ ] Task 1.1: Fix blackbox / simulation-friendly defaults
  - Files: [`lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala`](lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala:1)
  - Subtasks:
    - Preserve/confirm simulation-friendly defaults for CNTVALUEIN/CNTVALUEOUT/IDELAYCTRL.RDY or add explanatory comments.
    - Ensure all primitive ports have corresponding stubs during simulation.
  - Validation: sbt test no longer reports "Unknown module type" or missing simulation stub file errors.

- [ ] Task 1.2: Drive ODELAYE3/IDELAYE3 required inputs
  - Files: [`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`](lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala:1)
  - Subtasks:
    - Explicitly assign CNTVALUEIN (default 0 acceptable) for all ODELAYE3/IDELAYE3 instances and drive DATAIN/IDATAIN.
  - Validation: Simulation shows no "NO DRIVER ON" or similar warnings.

- [ ] Task 1.3: Align DQS ODELAY initial offset to LiteX
  - Files: XilinxUSPhy.scala (DQS path)
  - Subtasks:
    - Set DQS ODELAY initial delayValue to equivalent tck/4 (or parameterized constant, adjustable during testing).
  - Validation: DQS phase after initialization matches LiteX expectations (verified through simulation waveforms).

- [ ] Task 1.4: Connect ISERDESE3 FIFO_RD_CLK and FIFO ports
  - Files: XilinxUSPhy.scala, IO.scala
  - Subtasks:
    - Connect FIFO_RD_CLK (CLKDIV) for ISERDESE3 using FIFO and drive FIFO_RD_EN.
  - Validation: Simulation no longer reports undriven ISERDESE3 FIFO_RD_CLK; read data path works (simple read/write loop test).

## 2. Initialization and Command Alignment
- [ ] Task 2.1: Ensure command CS/CKE init & init override semantics
  - Files: XilinxUSPhy.scala (cmdGen / initManager)
  - Subtasks:
    - Verify cmdCsN/cmdCke initialization values, ensure initManager.padOverride makes initCsN/initCke override effective.
    - Add comments and test cases to verify init phase CS/CKE/resetN waveforms.
  - Validation: Initialization sequence simulation (power-up -> MRS -> ZQ) shows CS/CKE/resetN behavior matching LiteX.

## 3. Training Algorithm Fixes
- [ ] Task 3.1: Replace training placeholder sampling with real sampling
  - Files: XilinxUSPhy.scala (WriteLevelingModule, ReadGateModule, ReadEyeModule, CATrainingModule)
  - Subtasks:
    - Replace placeholder receivedPattern/receivedData in modules with real sampling from DQ/CA paths (ISERDESE3 outputs / delay.DATAOUT).
    - Parameterize decision thresholds (stable window length) to match LiteX defaults or be adjustable via CSR.
  - Validation: Run write/read leveling in simulation, verify training succeeds and io.dfi training response is correct.

## 4. Test Infrastructure (Scala Only)
- [ ] Task 4.1: Create Scala-based test suite for XilinxUSPhy
  - Files: [`tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala:1)
  - Subtasks:
    - Implement init process, DQS timing, write/read data integrity, training success/failure scenarios using SpinalSim.
    - Create comprehensive test cases covering all DFI interface groups.
  - Validation: All new Scala tests pass; fixed issues no longer regress.
- [ ] Task 4.2: Develop multi-backend validation framework
  - Subtasks:
    - Create unified test runner for Verilator, IVerilog, and GHDL backends.
    - Add regression test suite with known good patterns.
    - Implement training algorithm verification tests.

- [ ] Task 4.2: Develop training algorithm test scenarios
  - Files: [`tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhyTester.scala:1)
  - Subtasks:
    - Create test cases for write leveling, read gate training, read eye scanning, and command/address training.

## 5. Validation and Documentation
- [ ] Task 5.1: Local CI and cross-backend validation
  - Subtasks:
    - Run sbt test locally across all supported simulation backends.
    - Capture and record any change points conflicting with real device behavior.
  - Validation: All sbt tests pass across all backends.
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