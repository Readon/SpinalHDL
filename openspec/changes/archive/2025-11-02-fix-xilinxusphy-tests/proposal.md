# Proposal: Fix All XilinxUSPhy Test Code

## Why
The current XilinxUSPhy test code has several critical issues that prevent it from working properly:

1. **API Compatibility Issues**: Tests are using outdated DFI configuration APIs that don't match the current DfiConfig structure
2. **Test Framework Integration Problems**: Tests are not properly integrated with the current test framework (SpinalSimFunSuite vs SpinalTesterGhdlBase)
3. **Configuration Mismatches**: Test configurations don't align with the XilinxUSPhy implementation requirements
4. **Missing Simulation Infrastructure**: Tests lack proper simulation setup and execution
5. **Incomplete Test Coverage**: Tests don't cover the full functionality of the updated XilinxUSPhy implementation
6. **Test Execution Failures**: Tests fail to run or get stuck during elaboration

## What Changes
- **Modernize Test APIs**: Update all test code to use current DFI configuration APIs
- **Fix Test Framework Integration**: Ensure all tests properly extend and use the appropriate test frameworks
- **Standardize Configuration**: Create consistent, working test configurations that match XilinxUSPhy requirements
- **Add Simulation Infrastructure**: Implement proper simulation setup, signal drivers, and verification logic
- **Complete Test Coverage**: Add comprehensive tests for all XilinxUSPhy features including training, initialization, and data operations
- **Fix Execution Issues**: Resolve all compilation and runtime issues that prevent tests from running successfully

## Impact
- **Affected code**: All XilinxUSPhy test files in `tester/src/test/scala/spinal/tester/` and `tester/src/test/scala/spinal/lib/memory/sdram/dfi/phy/`
- **User impact**: Users will be able to run XilinxUSPhy tests successfully and verify PHY functionality
- **Development impact**: Better validation of XilinxUSPhy changes and improvements

## Non-goals
- No changes to the core XilinxUSPhy implementation (only test fixes)
- No introduction of new testing frameworks (use existing SpinalHDL testing infrastructure)
- No changes to other PHY implementations or tests

## Background / Analysis
Current test issues identified:

1. **DfiConfig API Mismatch**: Tests use parameter names that don't exist in current DfiConfig
- No changes to other PHY implementations or tests

## Implementation Plan

### Phase 1: API Modernization (2-3 days)
- Update all DfiConfig instantiations to use current API
- Fix signal configuration mismatches
- Standardize timing configuration parameters

### Phase 2: Test Framework Integration (2-3 days)
- Fix test class hierarchy and framework usage
- Implement proper test setup and teardown
- Add comprehensive test reporting

### Phase 3: Simulation Infrastructure (3-4 days)
- Add proper signal drivers and monitors
- Implement clock domain management
- Add reset sequence handling
- Create verification logic for all interfaces

### Phase 4: Test Coverage Enhancement (2-3 days)
- Add missing test cases for all XilinxUSPhy features
- Implement parameterized test configurations
- Add stress and edge case testing

### Phase 5: Validation and Integration (1-2 days)
- Run full test suite across multiple backends
- Verify all tests pass consistently
- Update documentation and examples

## Acceptance Criteria
- **All tests compile**: All XilinxUSPhy test files compile without errors
- **All tests execute**: Tests run to completion without hanging or crashing
- **All tests pass**: Tests provide meaningful verification of XilinxUSPhy functionality
- **Multi-backend support**: Tests work with Verilator, GHDL, and other supported backends
- **Documentation**: Updated test documentation and examples for users

## Risks and Mitigations
- **Risk**: Test fixes might reveal actual bugs in XilinxUSPhy implementation
  **Mitigation**: Plan to fix any discovered implementation issues as part of this work
- **Risk**: Complex simulation setup might introduce new test flakiness
  **Mitigation**: Implement robust test infrastructure with proper synchronization and timing
- **Risk**: API changes might break existing user test code
  **Mitigation**: Provide migration guide and examples for users

## Maintainers
SpinalHDL test infrastructure maintainers and XilinxUSPhy development team

## References
- Current XilinxUSPhy implementation: `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala`
- DFI interface specification: `lib/src/main/scala/spinal/lib/memory/sdram/dfi/`
- SpinalHDL testing framework documentation
- Related OpenSpec: align-xilinxusphy-to-litex-usphy (provides context for XilinxUSPhy features)