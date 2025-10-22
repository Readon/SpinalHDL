# SpinalHDL Design Rules Integration - Incremental Specification

## Overview
This incremental specification integrates additional SpinalHDL design rules from the User Guide into the existing coding standards specification.

## ADDED: Sequential Logic Design Patterns

### REQ-CS-036: Register Instantiation Patterns
Sequential logic MUST use proper register instantiation and timing control patterns.

**Rationale**: Proper sequential logic design ensures correct timing behavior and prevents common hardware errors like combinational loops and unintended latches.

**Requirements**:
- Use `Reg()` for basic registers with optional initialization
- Use `RegNext()` for single-cycle delayed signals
- Use `RegInit()` for registers with explicit reset values
- Use `RegNextWhen()` for conditional sampling
- Avoid combinational loops through proper register usage
- Ensure complete assignment in conditional blocks to prevent latches

**Example**:
```scala
// ✓ Correct register usage
val counter = Reg(UInt(8 bits)) init(0)
val delayedSignal = RegNext(inputSignal)
val resetCounter = RegInit(U(0, 8 bits))
val conditionalReg = RegNextWhen(data, condition)

// ✗ Avoid: Combinational loop
val a = Bool()
val b = Bool()
a := b
b := a  // Combinational loop!
```

#### Scenario: Register Instantiation Patterns
- **WHEN** creating sequential logic elements
- **THEN** use appropriate register types with proper initialization

#### Scenario: Memory Design Patterns
- **WHEN** implementing memory elements
- **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces

### REQ-CS-037: Memory Design Standards
Memory elements MUST use SpinalHDL memory constructs with proper interfaces.

**Rationale**: SpinalHDL provides specialized memory constructs that ensure proper hardware generation and timing behavior.

**Requirements**:
- Use `Mem()` for RAM and ROM implementations
- Use synchronous read/write ports for predictable timing
- Initialize memory contents using `init()` method
- Use proper address and data width specifications
- Consider memory banking for large memories

**Example**:
```scala
// ✓ Correct memory usage
val ram = Mem(Bits(32 bits), 1024)  // 1KB RAM
ram.init(Seq.fill(1024)(B(0, 32 bits)))  // Initialize with zeros

// Read port
val readData = ram.readSync(readAddress)

// Write port
when(writeEnable) {
  ram.write(writeAddress, writeData)
}
```

## ADDED: Clock Domain Management Principles

### REQ-CS-038: Clock Domain Configuration
Clock domains MUST be properly configured for reliable hardware operation.

**Rationale**: Proper clock domain configuration prevents timing violations and ensures predictable behavior across different clock domains.

**Requirements**:
- Use `ClockDomain()` to define clock domains with proper configuration
- Specify reset kind (SYNC/ASYNC) and active level
- Use appropriate clock frequency constraints
- Document clock domain relationships
- Use `ClockDomainConfig` for consistent configuration

**Example**:
```scala
// ✓ Correct clock domain configuration
val customClockDomain = ClockDomain(
  clock = io.externalClock,
  reset = io.externalReset,
  config = ClockDomainConfig(
    resetKind = SYNC,
    resetActiveLevel = HIGH,
    clockEdge = RISING
  )
)

val customArea = new ClockingArea(customClockDomain) {
  val counter = RegInit(U(0, 8 bits))
  counter := counter + 1
}
```

#### Scenario: Single Clock Domain Design
- **WHEN** designing with single clock domain
- **THEN** use default clock domain with proper reset handling

#### Scenario: Multi Clock Domain Design
- **WHEN** designing with multiple clock domains
- **THEN** use proper clock domain crossing synchronization with `BufferCC`

### REQ-CS-039: Clock Domain Crossing Synchronization
Clock domain crossing signals MUST use proper synchronization techniques.

**Rationale**: Proper synchronization prevents metastability and ensures reliable data transfer between clock domains.

**Requirements**:
- Use `BufferCC` for single-bit signal synchronization
- Use gray coding for multi-bit signal crossing
- Implement proper handshake protocols for data transfer
- Consider timing constraints for synchronization registers
- Document clock domain crossing points

**Example**:
```scala
// ✓ Correct clock domain crossing
val slowClockDomain = ClockDomain.external("slow")
val fastClockDomain = ClockDomain.external("fast")

val slowArea = new ClockingArea(slowClockDomain) {
  val data = RegInit(U(0, 8 bits))
}

val fastArea = new ClockingArea(fastClockDomain) {
  // Synchronize single-bit signal
  val syncData = BufferCC(slowArea.data, U(0))
  
  // For multi-bit signals, consider gray coding
  val grayData = slowArea.data.toGray
  val syncGray = BufferCC(grayData, U(0))
  val recoveredData = syncGray.fromGray
}
```

## ADDED: Advanced Design Patterns

### REQ-CS-040: Pipeline Design Optimization
Pipeline designs MUST use appropriate optimization techniques for performance and area.

**Rationale**: Proper pipeline optimization improves circuit performance and resource utilization while maintaining correct functionality.

**Requirements**:
- Balance pipeline stages for optimal throughput
- Use `RegNext` for single-cycle pipeline stages
- Implement proper data forwarding for dependencies
- Consider resource sharing for common operations
- Use balanced trees for reduction operations

**Example**:
```scala
class OptimizedPipeline extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  // Balanced pipeline stages
  val stage1 = RegNext(io.input + 1)
  val stage2 = RegNext(stage1 * 2)
  val stage3 = RegNext(stage2 - 3)
  
  io.output := stage3

  // Resource sharing example
  val sharedAdder = new Adder()
  when(conditionA) {
    sharedAdder.io.a := inputA
    sharedAdder.io.b := inputB
  } otherwise {
    sharedAdder.io.a := inputC
    sharedAdder.io.b := inputD
  }
}
```

### REQ-CS-041: Resource Sharing Guidelines
Common operations SHOULD be shared to optimize area usage.

**Rationale**: Resource sharing reduces circuit area by reusing hardware components for similar operations.

**Requirements**:
- Identify common operations that can be shared
- Use multiplexers to select between operation inputs
- Consider timing impact of resource sharing
- Document shared resources and their usage
- Balance area savings against performance impact

**Example**:
```scala
// ✓ Resource sharing for common operations
val sharedMultiplier = new Multiplier()

when(operationSelect === 0) {
  sharedMultiplier.io.a := inputA
  sharedMultiplier.io.b := inputB
} elsewhen(operationSelect === 1) {
  sharedMultiplier.io.a := inputC
  sharedMultiplier.io.b := inputD
}

val result = sharedMultiplier.io.result
```

## ADDED: Debugging and Optimization Guidelines

### REQ-CS-042: Design Verification Constructs
Hardware designs MUST include proper verification constructs for debugging and validation.

**Rationale**: Design verification prevents common hardware errors and improves code reliability through early error detection.

**Requirements**:
- Use `assert()` statements for design constraints and invariants
- Use `report()` for debugging information during elaboration
- Enable design checks for combinatorial loops and latch detection
- Use conditional compilation for debug features in production code
- Implement performance counters for critical paths

**Example**:
```scala
class VerifiedComponent extends Component {
  val io = new Bundle {
    val input = in UInt(8 bits)
    val output = out UInt(8 bits)
  }

  // Design assertions
  assert(io.input <= 100, "Input should be <= 100", WARNING)

  // Debug reports
  report(Seq("Input value:", io.input), "DEBUG")

  // Conditional debug features
  val debugCounter = if (globalConfig.generateDebug) {
    RegInit(U(0, 8 bits))
  } else {
    null
  }

  io.output := io.input + 1
}
```

### REQ-CS-043: Performance Optimization Techniques
Performance-critical designs MUST use appropriate optimization techniques.

**Rationale**: Proper optimization techniques improve circuit performance and resource utilization for critical design paths.

**Requirements**:
- Use balanced trees for reduction operations to reduce critical path
- Implement pipelining for high-throughput designs
- Use resource sharing for common operations to optimize area
- Consider timing constraints during design phase
- Use appropriate data path widths to balance performance and area

**Example**:
```scala
// Use balanced tree to reduce delay
val sum = data.reduceBalancedTree(_ + _)

// Use pipelining to improve throughput
val pipelinedSum = RegNext(data(0) + data(1)) + RegNext(data(2) + data(3))

// Resource sharing for area optimization
val sharedMultiplier = new Multiplier()
when(condition1) {
  sharedMultiplier.io.a := input1
  sharedMultiplier.io.b := input2
} otherwise {
  sharedMultiplier.io.a := input3
  sharedMultiplier.io.b := input4
}
```

## ADDED: Simulation and Testing Best Practices

### REQ-CS-044: Test Stimulus Generation
Test stimulus MUST be properly generated and managed for reliable verification.

**Rationale**: Proper test stimulus generation ensures comprehensive test coverage and reliable verification results.

**Requirements**:
- Use random data generation with controlled seeds
- Implement corner case testing for boundary conditions
- Use transaction-level modeling for complex protocols
- Implement proper test sequence generation
- Document test coverage goals and achieved coverage

**Example**:
```scala
class ComprehensiveTest extends SpinalAnyFunSuite {
  test("corner_cases") {
    SimConfig.doSim(new Dut()) { dut =>
      // Test boundary conditions
      dut.io.input #= 0
      dut.clockDomain.waitSampling()
      assert(dut.io.output.toInt == expectedMin)

      dut.io.input #= 255  // Max value for 8-bit
      dut.clockDomain.waitSampling()
      assert(dut.io.output.toInt == expectedMax)

      // Random testing
      val random = new Random(42)  // Fixed seed for reproducibility
      for (i <- 0 until 100) {
        dut.io.input #= random.nextInt(256)
        dut.clockDomain.waitSampling()
        // Verify expected behavior
      }
    }
  }
}
```

### REQ-CS-045: Test Coverage and Verification
Test suites MUST achieve comprehensive coverage of design functionality.

**Rationale**: Comprehensive test coverage ensures design correctness and prevents regression issues.

**Requirements**:
- Implement functional coverage for all design features
- Use assertion-based verification for critical properties
- Document test coverage metrics and goals
- Implement regression test suites
- Use formal verification for critical safety properties

**Example**:
```scala
class CoverageTest extends SpinalAnyFunSuite {
  test("functional_coverage") {
    SimConfig.doSim(new Dut()) { dut =>
      var coverage = new CoverageTracker()

      fork {
        // Drive inputs and track coverage
        for (i <- 0 until testCycles) {
          val stimulus = generateStimulus()
          dut.io.input #= stimulus
          coverage.record(stimulus)
          dut.clockDomain.waitSampling()
        }
        
        // Report coverage
        coverage.report()
        assert(coverage.achieved >= coverage.target)
      }
    }
  }
}
```

## MODIFIED: Enhanced Existing Requirements

### Component Encapsulation Standards (Enhanced)
All Component class input/output signals MUST be accessed through the `io` named Bundle, and internal signals MUST be properly encapsulated.

**Enhanced Requirements**:
- Component classes MUST define a `val io = new Bundle { ... }` field
- All input/output signals MUST be defined within the `io` Bundle
- External objects MUST NOT directly access internal Component signals
- Internal signals MAY be accessed within the Component for logic implementation
- When creating hardware signals in transformations, all Bundle fields MUST be explicitly assigned using `assignUnassignedByName()` after custom assignments
- Use direct object access for Bundle field assignments instead of method chaining
- **ADDED**: Use proper naming conventions for internal signals to improve readability

### Stream-Based Design Pattern (Enhanced)
Data processing components MUST use Stream infrastructure for flow control and backpressure, with proper signal assignment completeness.

**Enhanced Requirements**:
- Prefer `spinal.lib.Stream` for data flow operations
- Use `StreamTransactionExtender` instead of state machines for transaction handling
- Use `StreamArbiter` for multi-stream arbitration
- Use `StreamDemux` for stream separation
- Prefer `translateWith`, `translateFrom`, `translateInto` for stream transformations
- Ensure all Bundle fields in stream transformations are explicitly assigned using `assignUnassignedByName()` after custom processing
- **ADDED**: Implement proper backpressure handling for all stream interfaces

## Implementation Notes

### Integration Strategy
- These rules complement existing coding standards
- Focus on practical design patterns from SpinalHDL User Guide
- Maintain backward compatibility with existing code
- Provide clear examples and scenarios for each requirement

### Validation Approach
- Static analysis tools should check for compliance
- Code reviews should verify rule adherence
- Test suites should validate design patterns
- Documentation should provide clear guidance

### Migration Considerations
- Existing code should be gradually updated to comply
- New projects should follow these standards from start
- Training materials should incorporate these patterns
- Code generators should produce compliant code