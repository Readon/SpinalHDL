# Proposal: Align XilinxUSPhy implementation to LiteX usphy.py

Change-id: align-xilinxusphy-to-litex-usphy

## Why
The current Scala implementation of XilinxUSPhy has functional differences and simulation/synthesis warnings compared to the LiteX reference implementation, causing initialization/training uncertainties and simulation errors.

## What Changes
- Align DQS initial delay to tck/4 as implemented in LiteX
- Drive all ODELAYE3/IDELAYE3 required inputs (CNTVALUEIN, DATAIN/IDATAIN) to eliminate simulation warnings
- Connect ISERDESE3 FIFO_RD_CLK to CLKDIV and drive FIFO_RD_EN
- Ensure command CS/CKE initialization and init override semantics match LiteX
- Replace training module placeholder sampling with real sampling from DQ/CA paths
- Add/fix Verilog stubs for primitives in tester directory
- Maintain simulation-friendly BlackBox defaults with proper documentation

## Impact
- Affected specs: dfi-ddr3-phy
- Affected code: [`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`](lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala:1), [`lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala`](lib/src/main/scala/spinal/lib/blackbox/xilinx/ultrascale/IO.scala:1)
- Affected tests: [`tester/src/test/python/spinal/XilinxUSPhyTester/`](tester/src/test/python/spinal/XilinxUSPhyTester/:1)

## Non-goals
- No changes to DFI protocol definition or upper memory controller interface
- No introduction of new hardware features inconsistent with current hardware requirements
- Priority on minimal fixes to match LiteX semantics

## Background / Analysis
Completed line-by-line comparison and recorded differences:
- Clock pattern/phase variations
- DQS ODELAY initial value (LiteX uses p_DELAY_VALUE = tck/4)
- ODELAY/IDELAY CNTVALUEIN/IDATAIN/DATAIN need explicit driving to avoid NO DRIVER warnings
- ISERDESE3 FIFO_RD_CLK needs connection
- Initialization CS/CKE initial values and override logic need alignment
- Training modules contain placeholder sampling logic that needs replacement with real sampling

Test feedback shows missing primitives simulation stubs and several undriven ports causing simulation failures. Both simulation stubs and blackbox maintenance are required.

## Implementation plan (high-level timeline)
- Week 1: Implement and verify critical simulation fixes (CNTVALUEIN, FIFO_RD_CLK, DQS initial delay, cmdCsN initialization) and fix test stubs
- Week 2: Fix training module sampling logic, adjust criteria and add regression tests for training
- Week 3: Multi-rank, edge cases validation, write change description and openspec documentation, prepare PR

## Acceptance criteria
- Unit/integration tests: XilinxUSPhy tests in `tester` pass (Verilator & GHDL); eliminate NO DRIVER on ODELAY/IDELAY/ISERDESE3/FIFO_RD_CLK simulation errors
- Behavioral alignment: In init/training scenarios, Scala PHY matches LiteX reference implementation on key waveforms (CS/CKE/resetN/DQS pre/postamble, rddata_valid timing) allowing for parameterized differences
- Documentation: `openspec/changes/align-xilinxusphy-to-litex-usphy/{proposal.md,tasks.md,design.md}` submitted and passes `openspec validate <id> --strict`

## Risks and mitigations
- Risk: Modifying delay initial values or training algorithms may cause existing FPGA hardware regression failures
  Mitigation: All changes introduced in a parameterized/configurable manner, preserving old behavior switches; gradual merging and regression on multiple platforms
- Risk: Simulation stubs inconsistent with real FPGA primitives causing misjudgment
  Mitigation: Record differences in blackbox wrapper, prioritize making simulation stubs functionally equivalent to LiteX; add device-level testing if necessary

## Maintainers
spinallib team

## References
- LiteX reference: [`usphy.py`](usphy.py:1)
- Current Scala implementation: [`lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`](lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala:1)