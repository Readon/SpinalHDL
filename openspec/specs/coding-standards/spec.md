# Coding Standards Specification

## Purpose
This specification defines the global coding standards for SpinalHDL library development, ensuring consistent, maintainable, and hardware-correct code generation. It provides comprehensive guidelines for hardware design using SpinalHDL, covering data types, component design, clock domain management, testing practices, and optimization techniques.

## Requirements

#### REQ-CS-001: Component IO Bundle Access
All Component class input/output signals MUST be accessed through the `io` named Bundle, and internal signals MUST be properly encapsulated.

**Rationale**: SpinalHDL Components encapsulate hardware modules, and all external interfaces must go through the dedicated `io` Bundle to maintain proper encapsulation and prevent direct signal access. Additionally, proper signal assignment completeness ensures all hardware signals have defined connections.

**Requirements**:
- Component classes MUST define a `val io = new Bundle { ... }` field
- All input/output signals MUST be defined within the `io` Bundle
- External objects MUST NOT directly access internal Component signals
- Internal signals MAY be accessed within the Component for logic implementation
- When creating hardware signals in transformations, all Bundle fields MUST be explicitly assigned using `assignUnassignedByName()` after custom assignments
- Use direct object access for Bundle field assignments instead of method chaining

**Example**:
```scala
case class MyComponent(config: MyConfig) extends Component {
  // ✓ Correct: All IO through io Bundle
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  // Internal logic can access io signals
  io.output := io.input + 1

  // ✓ Correct: Complete signal assignment in transformations
  val processed = cloneOf(io.input.payload)
  processed.data := io.input.payload.data + 1        // Custom processing
  processed.assignUnassignedByName(io.input.payload)  // Connect all unassigned fields
}
```

##### Scenario: Valid Component Encapsulation
- **WHEN** defining a Component
- **THEN** all IO must go through the `io` Bundle and internal signals are properly encapsulated

##### Scenario: Complete Signal Assignment
- **WHEN** creating hardware signals in transformations
- **THEN** all Bundle fields must be explicitly assigned using `assignUnassignedByName()`

##### Scenario: Direct Object Access
- **WHEN** assigning Bundle fields
- **THEN** use direct object access instead of method chaining

##### Scenario: Invalid Direct Signal Access
- **WHEN** external code accesses Component internal signals
- **THEN** it must be flagged as encapsulation violation

##### Scenario: Valid AXI4 Interface Definition
- **WHEN** defining an AXI4 interface
- **THEN** the configuration and bundle must follow standards

##### Scenario: Invalid Bundle with Scala Types
- **WHEN** a Bundle contains Scala types
- **THEN** compilation must fail with clear error

##### Scenario: Configuration Class with Hardware Types
- **WHEN** a configuration class contains hardware types
- **THEN** it should be flagged as design error

#### REQ-CS-002: Basic Data Type Usage Standards
Basic data types MUST be used according to their intended semantics and proper initialization patterns.

**Rationale**: SpinalHDL provides specific data types for hardware description, and proper usage ensures correct hardware generation and predictable behavior.

**Requirements**:
- Use `Bool()` for single-bit logical signals with `True` and `False` constants
- Use `Bits(n bits)` for raw bit vectors without arithmetic semantics
- Use `UInt(n bits)` for unsigned integer arithmetic
- Use `SInt(n bits)` for signed integer arithmetic
- Use `Enum` types for finite state machines and state enumeration
- Initialize all registers with proper reset values
- Use appropriate bit-width selection for operations

##### Scenario: Proper Bool Type Usage
- **WHEN** declaring single-bit logical signals
- **THEN** use `Bool()` type with `True`/`False` constants and edge detection methods

##### Scenario: Proper Bits Type Usage
- **WHEN** working with raw bit data
- **THEN** use `Bits(n bits)` with binary/hexadecimal literals and bit manipulation operations

##### Scenario: Proper Integer Arithmetic
- **WHEN** performing arithmetic operations
- **THEN** use `UInt` for unsigned and `SInt` for signed arithmetic with proper type constants

##### Scenario: Valid Bool Type Usage
- **WHEN** using Bool types for single-bit signals
- **THEN** proper initialization and edge detection must be used

#### REQ-CS-003: Sequential Logic Design Patterns
Sequential logic MUST use proper register instantiation and timing control patterns.

**Rationale**: Proper sequential logic design ensures correct timing behavior and prevents common hardware errors like combinational loops and unintended latches.

**Requirements**:
- Use `Reg()` for basic registers with optional initialization
- Use `RegNext()` for single-cycle delayed signals
- Use `RegInit()` for registers with explicit reset values
- Use `RegNextWhen()` for conditional sampling
- Avoid combinational loops through proper register usage
- Ensure complete assignment in conditional blocks to prevent latches

##### Scenario: Register Instantiation Patterns
- **WHEN** creating sequential logic elements
- **THEN** use appropriate register types with proper initialization

##### Scenario: Memory Design Patterns
- **WHEN** implementing memory elements
- **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces


#### REQ-CS-004: Clock Domain Management Principles
Multi-clock domain designs MUST use proper clock domain crossing and synchronization techniques.

**Rationale**: Proper clock domain management prevents metastability and timing violations in designs with multiple clock domains.

**Requirements**:
- Use `ClockDomain()` to define clock domains with proper configuration
- Use `ClockingArea` to encapsulate logic within specific clock domains
- Use `BufferCC` for proper clock domain crossing synchronization
- Avoid direct signal connections between different clock domains
- Use appropriate reset synchronization for multi-clock designs

##### Scenario: Single Clock Domain Design
- **WHEN** designing with single clock domain
- **THEN** use default clock domain with proper reset handling

##### Scenario: Multi Clock Domain Design
- **WHEN** designing with multiple clock domains
- **THEN** use proper clock domain crossing synchronization with `BufferCC`


#### REQ-CS-005: Advanced Design Patterns
Complex designs MUST use appropriate advanced patterns for state machines and pipelining.

**Rationale**: Advanced design patterns improve code readability, maintainability, and performance for complex hardware implementations.

**Requirements**:
- Use `StateMachine` class for complex state machines with clear state transitions
- Use pipeline stages with `RegNext` for improved throughput
- Use balanced trees for reduction operations in performance-critical paths
- Implement resource sharing for common operations to optimize area
- Consider timing constraints during pipeline design

##### Scenario: State Machine Implementation
- **WHEN** implementing finite state machines
- **THEN** use `StateMachine` class with `whenIsActive` and `goto` methods

##### Scenario: Pipeline Design
- **WHEN** designing high-throughput circuits
- **THEN** use multiple pipeline stages with proper register balancing


#### REQ-CS-006: Debugging and Optimization Guidelines
Hardware designs MUST include proper verification constructs and optimization techniques.

**Rationale**: Design verification prevents common hardware errors and optimization techniques improve circuit performance and resource utilization.

**Requirements**:
- Use `assert()` statements for design constraints and invariants
- Use `report()` for debugging information during elaboration
- Enable design checks for combinatorial loops and latch detection
- Use conditional compilation for debug features in production code
- Implement performance optimization techniques for critical paths

##### Scenario: Design Verification
- **WHEN** implementing hardware components
- **THEN** include assertions for critical design constraints

##### Scenario: Performance Optimization
- **WHEN** optimizing performance-critical circuits
- **THEN** use pipelining, resource sharing, and balanced operations


#### REQ-CS-007: Simulation and Testing Best Practices
Test code MUST follow established patterns for reliable verification and maintainability, including DUT definition, SimConfig usage, doSim block logic, simulator support, clock domain management, and assertions.

**Rationale**: Consistent simulation patterns ensure reliable test results and maintainable test code that clearly separates verification logic from hardware design. Comprehensive testing covers DUT instantiation, configuration, execution, and validation across supported simulators.

**Requirements**:
- Use `SpinalAnyFunSuite` or `SpinalSimFunSuite` (for multi-simulator support) as base classes for test suites
- Follow test class naming conventions ending with "Tester" or "Test"
- Organize test code in hierarchical structure mirroring main code, placed in `tester/src/test/scala/`
- Define DUT as a class extending `Component` with proper `io` Bundle
- Use `SimConfig` with options like `withWave` for compilation and waveform generation
- Implement test logic in `doSim` or `doSimUntilVoid` blocks, using `fork` for concurrency, `waitSampling` for timing, and `assert`/`shouldBe` for validation
- Support multiple simulators: Verilator (preferred for speed), GHDL, IVerilog; configure via `SimConfig.withVerilator` etc.
- Manage clock domains with `forkStimulus` for stimulus generation and `ClockDomain` for multi-domain designs
- Use explicit signal assignments with `#=` operator in simulation (never `:=` which is for hardware logic)
- Use proper data types (`Int`, `Boolean`, `BigInt`) for test assignments
- Group related test assignments logically with descriptive comments
- Ensure test coverage includes functional, timing, boundary, and error conditions
- Distinguish simulation assignments (`#=`) from hardware assignments (`:=`) to avoid compilation errors

##### Scenario: Test Class Organization
- **WHEN** creating test classes
- **THEN** follow naming conventions and place in appropriate directory structure

##### Scenario: Simulation Signal Assignment
- **WHEN** assigning signals during simulation
- **THEN** use explicit indexing for collections and proper data types

##### Scenario: Test Execution
- **WHEN** executing tests
- **THEN** use standardized sbt commands with proper class names

##### Scenario: Valid Test Stimulus Generation
- **WHEN** creating test suites
- **THEN** use random data generation with controlled seeds

##### Scenario: Valid Test Coverage
- **WHEN** implementing test suites
- **THEN** achieve comprehensive coverage of design functionality

##### Scenario: DUT Definition and Compilation
- **WHEN** defining a DUT for testing
- **THEN** use a Component class with io Bundle and compile via SimConfig

##### Scenario: doSim Block Logic
- **WHEN** implementing test logic
- **THEN** use fork for concurrency, waitSampling for timing control, and assertions for validation

##### Scenario: Simulator Support
- **WHEN** running simulations
- **THEN** support Verilator, GHDL, IVerilog with appropriate SimConfig settings

##### Scenario: Clock Domain Management
- **WHEN** testing multi-clock designs
- **THEN** use forkStimulus and ClockDomain for proper timing

##### Scenario: Assertion and Validation
- **WHEN** validating outputs
- **THEN** use assert or shouldBe with descriptive messages

#### REQ-CS-013: Stream-Based Design Pattern
Data processing components SHOULD use Stream infrastructure for flow control and backpressure.

**Rationale**: Stream-based design improves code readability, maintainability, and reusability by replacing complex state machines with declarative flow control. Complete signal assignment ensures all hardware signals have proper connections.

**Requirements**:
- Prefer `spinal.lib.Stream` for data flow operations
- Use `StreamTransactionExtender` instead of state machines for transaction handling
- Use `StreamArbiter` for multi-stream arbitration
- Use `StreamDemux` for stream separation
- Prefer `translateWith`, `translateFrom`, `translateInto` for stream transformations
- Ensure all Bundle fields in stream transformations are explicitly assigned using `assignUnassignedByName()` after custom processing

**Example**:
```scala
case class StreamProcessor(config: Config) extends Component {
  val io = new Bundle {
    val input = slave Stream(DataBundle())
    val output = master Stream(DataBundle())
  }

  // ✓ Preferred: Stream-based processing with complete assignment
  io.output << io.input.translateWith {
    val processed = cloneOf(io.input.payload)
    processed.data := io.input.payload.data + 1        // Custom processing
    processed.assignUnassignedByName(io.input.payload)  // Connect all unassigned fields
    processed
  }
}
```

##### Scenario: Valid Stream-Based Component
- **WHEN** implementing data processing components
- **THEN** Stream infrastructure should be used for flow control with complete signal assignment

##### Scenario: Invalid State Machine Instead of Stream
- **WHEN** using complex state machines for data flow
- **THEN** it should be flagged as potential Stream candidate


#### REQ-CS-026: Test Signal Assignment Patterns
Test signal assignments during simulation MUST follow consistent patterns for different data types, strictly using `#=` in doSim blocks and avoiding `:=` which is reserved for hardware logic.

**Rationale**: Consistent assignment patterns improve test code readability and reduce errors in simulation-based testing. Explicit collection indexing prevents ambiguous assignments and improves maintainability. Distinguishing simulation assignments from hardware assignments prevents compilation errors.

**Requirements**:
- Use `#=` operator exclusively for all signal assignments in simulation functions like `doSim` or `doSimUntilVoid`
- Never use `:=` in simulation contexts, as it is for hardware logic and will cause type errors
- Use appropriate data types: `Int` for integer values, `Boolean` for boolean values, `BigInt` for large integers
- Use explicit indexing for all collection assignments (e.g., `signal(index) #= value`)
- Use `.randomize()` for generating random test data
- Use descriptive variable names for complex expressions
- Group related assignments and use comments to explain test phases
- Ensure assignments propagate via `waitSampling()` calls


**Valid Examples**:
```scala
// ✓ Correct: #= for simulation assignments in doSim
dut.io.dfi.rdTraining.rdlvlReq(0) #= false      // Read level training request for slice 0

// ✓ Correct: Loop for collection initialization with #=
for (i <- 0 until dut.io.dfi.read.rd.length) {
  dut.io.dfi.read.rd(i).rddataValid #= false
  dut.io.dfi.read.rd(i).rddata #= 0
}

// ✗ Incorrect: := in simulation (compilation error)
// dut.io.input := 5  // Wrong: := is for hardware logic
```

##### Scenario: Boolean Signal Assignment
- **WHEN** assigning boolean signals in tests
- **THEN** use `#=` operator with `Boolean` values and explicit indexing

##### Scenario: Integer/Bits Signal Assignment
- **WHEN** assigning integer or bits signals in tests
- **THEN** use `#=` operator with proper data types and explicit indexing

##### Scenario: Collection Signal Assignment
- **WHEN** assigning signals to collections in tests
- **THEN** use `foreach(_ #= value)` patterns for Vec, List, and similar container types' assignment.

##### Scenario: Invalid Hardware Assignment in Simulation
- **WHEN** using `:=` in doSim blocks
- **THEN** it must be flagged as compilation error since `:=` is for hardware logic

## Implementation Notes

### Tool Integration
- Static analysis tools should check Bundle definitions
- Build process should include type validation
- IDE plugins should provide real-time feedback
- Component encapsulation validators should be implemented

### Migration Strategy
- Existing code should be gradually migrated
- Automated refactoring tools may be developed
- Backward compatibility maintained during transition

### Testing
- Unit tests for type validation
- Integration tests for code generation
- Regression tests for existing patterns
- Component encapsulation tests