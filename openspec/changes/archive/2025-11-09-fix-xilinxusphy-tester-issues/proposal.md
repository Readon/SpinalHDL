## Why
The enhanced XilinxUSPhy test suite (from enhance-xilinxusphy-dfi-ddr-tests) has test runner issues that prevent the tests from being discovered and executed properly by the SBT test framework. The tests compile successfully but cannot be run through `sbt test` or `testOnly` commands, which limits their effectiveness for continuous integration and development validation.

## Current Issues Identified
1. **Test Discovery Problems**: Enhanced test files are located in a custom `enhanced/` subdirectory rather than the standard test path structure used by the project
2. **Test Runner Integration**: Tests follow SpinalAnyFunSuite pattern but may not be properly integrated with the project's test discovery mechanism
3. **Package Structure**: Enhanced tests use a different package structure (`spinal.lib.memory.sdram.dfi.phy.enhanced`) that may affect test discovery
4. **Test Execution**: Tests generate Verilog but don't include actual runtime validation/assertions that could be executed
5. **Dependency Issues**: Some test dependencies or imports may not be properly resolved in the test runner context

## What Changes
- **Fix Test Discovery**: Reorganize enhanced test files to follow standard SpinalHDL test project conventions
- **Improve Test Integration**: Ensure all enhanced tests are properly discovered by SBT test framework
- **Standardize Package Structure**: Align package structure with existing test organization patterns
- **Add Runtime Validation**: Enhance tests with actual functional verification beyond just Verilog generation
- **Fix Dependencies**: Resolve any import or dependency issues preventing test execution
- **Integration with CI**: Ensure tests can run successfully in continuous integration environments

## Impact
- **Affected code**: Enhanced XilinxUSPhy test suite (15 test files)
- **Test coverage**: Will make existing comprehensive tests actually executable and useful
- **CI/CD**: Will enable automated testing of XilinxUSPhy functionality
- **Development workflow**: Will allow developers to run tests locally to validate changes
- **Quality assurance**: Will provide functional validation rather than just compilation checks

## Success Criteria
- All enhanced XilinxUSPhy tests can be discovered and run via `sbt "tester/test"`
- Individual tests can be run via `sbt "tester/testOnly TestClassName"`
- Tests include functional assertions beyond Verilog generation
- Tests run successfully in CI environments
- No regression in existing test functionality