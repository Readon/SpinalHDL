# Coding Standards Specification

## Purpose
This specification defines the global coding standards for SpinalHDL library development, ensuring consistent, maintainable, and hardware-correct code generation. It provides comprehensive guidelines for hardware design using SpinalHDL, covering data types, component design, clock domain management, testing practices, and optimization techniques.
## Requirements
### Requirement: Component IO Bundle Access
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

#### Scenario: Valid Component Encapsulation
- **WHEN** defining a Component
- **THEN** all IO must go through the `io` Bundle and internal signals are properly encapsulated

#### Scenario: Complete Signal Assignment
- **WHEN** creating hardware signals in transformations
- **THEN** all Bundle fields must be explicitly assigned using `assignUnassignedByName()`

#### Scenario: Direct Object Access
- **WHEN** assigning Bundle fields
- **THEN** use direct object access instead of method chaining

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

#### Scenario: Valid Stream-Based Component
- **WHEN** implementing data processing components
- **THEN** Stream infrastructure should be used for flow control with complete signal assignment

#### Scenario: Invalid State Machine Instead of Stream
- **WHEN** using complex state machines for data flow
- **THEN** it should be flagged as potential Stream candidate

#### REQ-CS-026: Test Signal Assignment Patterns
Test signal assignments during simulation MUST follow consistent patterns for different data types.

**Rationale**: Consistent assignment patterns improve test code readability and reduce errors in simulation-based testing. Explicit collection indexing prevents ambiguous assignments and improves maintainability.

**Requirements**:
- Use `#=` operator for all signal assignments in simulation
- Use appropriate data types: `Int` for integer values, `Boolean` for boolean values, `BigInt` for large integers
- Use explicit indexing for all collection assignments (e.g., `signal(index) #= value`)
- **PROHIBITED**: Using `foreach(_ #= value)` on `dut.io` signals to modify collection values
- Use `.randomize()` for generating random test data
- Use descriptive variable names for complex expressions
- Group related assignments and use comments to explain test phases

**Invalid Example**:
```scala
// ✗ PROHIBITED: using foreach on dut.io signals
dut.io.dfi.rdTraining.rdlvlReq.foreach(_ #= false)
```

**Valid Examples**:
```scala
// ✓ Clear: explicit indexing for all collections
dut.io.dfi.rdTraining.rdlvlReq(0) #= false      // Read level training request for slice 0

// ✓ Clear: explicit loop for collection initialization
for (i <- 0 until dut.io.dfi.read.rd.length) {
  dut.io.dfi.read.rd(i).rddataValid #= false
  dut.io.dfi.read.rd(i).rddata #= 0
}
```

#### Scenario: Boolean Signal Assignment
- **WHEN** assigning boolean signals in tests
- **THEN** use `#=` operator with `Boolean` values and explicit indexing

#### Scenario: Integer/Bits Signal Assignment  
- **WHEN** assigning integer or bits signals in tests
- **THEN** use `#=` operator with proper data types and explicit indexing

#### Scenario: Collection Signal Assignment
- **WHEN** assigning signals to collections in tests
- **THEN** use explicit indexing and avoid `foreach(_ #= value)` patterns

### Requirement: Basic Data Type Usage Standards
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

#### Scenario: Proper Bool Type Usage
- **WHEN** declaring single-bit logical signals
- **THEN** use `Bool()` type with `True`/`False` constants and edge detection methods

#### Scenario: Proper Bits Type Usage  
- **WHEN** working with raw bit data
- **THEN** use `Bits(n bits)` with binary/hexadecimal literals and bit manipulation operations

#### Scenario: Proper Integer Arithmetic
- **WHEN** performing arithmetic operations
- **THEN** use `UInt` for unsigned and `SInt` for signed arithmetic with proper type constants

### Requirement: Sequential Logic Design Patterns
Sequential logic MUST use proper register instantiation and timing control patterns.

**Rationale**: Proper sequential logic design ensures correct timing behavior and prevents common hardware errors like combinational loops and unintended latches.

**Requirements**:
- Use `Reg()` for basic registers with optional initialization
- Use `RegNext()` for single-cycle delayed signals
- Use `RegInit()` for registers with explicit reset values
- Use `RegNextWhen()` for conditional sampling
- Avoid combinational loops through proper register usage
- Ensure complete assignment in conditional blocks to prevent latches

#### Scenario: Register Instantiation Patterns
- **WHEN** creating sequential logic elements
- **THEN** use appropriate register types with proper initialization

#### Scenario: Memory Design Patterns
- **WHEN** implementing memory elements
- **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces

### Requirement: Clock Domain Management Principles
Multi-clock domain designs MUST use proper clock domain crossing and synchronization techniques.

**Rationale**: Proper clock domain management prevents metastability and timing violations in designs with multiple clock domains.

**Requirements**:
- Use `ClockDomain()` to define clock domains with proper configuration
- Use `ClockingArea` to encapsulate logic within specific clock domains
- Use `BufferCC` for proper clock domain crossing synchronization
- Avoid direct signal connections between different clock domains
- Use appropriate reset synchronization for multi-clock designs

#### Scenario: Single Clock Domain Design
- **WHEN** designing with single clock domain
- **THEN** use default clock domain with proper reset handling

#### Scenario: Multi Clock Domain Design
- **WHEN** designing with multiple clock domains
- **THEN** use proper clock domain crossing synchronization with `BufferCC`

### Requirement: Advanced Design Patterns
Complex designs MUST use appropriate advanced patterns for state machines and pipelining.

**Rationale**: Advanced design patterns improve code readability, maintainability, and performance for complex hardware implementations.

**Requirements**:
- Use `StateMachine` class for complex state machines with clear state transitions
- Use pipeline stages with `RegNext` for improved throughput
- Use balanced trees for reduction operations in performance-critical paths
- Implement resource sharing for common operations to optimize area
- Consider timing constraints during pipeline design

#### Scenario: State Machine Implementation
- **WHEN** implementing finite state machines
- **THEN** use `StateMachine` class with `whenIsActive` and `goto` methods

#### Scenario: Pipeline Design
- **WHEN** designing high-throughput circuits
- **THEN** use multiple pipeline stages with proper register balancing

### Requirement: Debugging and Optimization Guidelines
Hardware designs MUST include proper verification constructs and optimization techniques.

**Rationale**: Design verification prevents common hardware errors and optimization techniques improve circuit performance and resource utilization.

**Requirements**:
- Use `assert()` statements for design constraints and invariants
- Use `report()` for debugging information during elaboration
- Enable design checks for combinatorial loops and latch detection
- Use conditional compilation for debug features in production code
- Implement performance optimization techniques for critical paths

#### Scenario: Design Verification
- **WHEN** implementing hardware components
- **THEN** include assertions for critical design constraints

#### Scenario: Performance Optimization
- **WHEN** optimizing performance-critical circuits
- **THEN** use pipelining, resource sharing, and balanced operations

### Requirement: Simulation and Testing Best Practices
Test code MUST follow established patterns for reliable verification and maintainability.

**Rationale**: Consistent simulation patterns ensure reliable test results and maintainable test code that clearly separates verification logic from hardware design.

**Requirements**:
- Use `SpinalAnyFunSuite` for unit tests and `SpinalTesterGhdlBase` for simulation tests
- Follow test class naming conventions ending with "Tester" or "Test"
- Organize test code in hierarchical structure mirroring main code
- Use explicit signal assignments with `#=` operator in simulation
- Avoid using `foreach(_ #= value)` on `dut.io` signals for collections
- Use proper data types (`Int`, `Boolean`, `BigInt`) for test assignments
- Group related test assignments logically with descriptive comments

#### Scenario: Test Class Organization
- **WHEN** creating test classes
- **THEN** follow naming conventions and place in appropriate directory structure

#### Scenario: Simulation Signal Assignment
- **WHEN** assigning signals during simulation
- **THEN** use explicit indexing for collections and proper data types

#### Scenario: Test Execution
- **WHEN** executing tests
- **THEN** use standardized sbt commands with proper class names

## Scenarios

### Scenario: Valid Component Encapsulation
**WHEN** defining a Component
**THEN** all IO must go through the `io` Bundle

**Example**:
```scala
case class ValidComponent(config: MyConfig) extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }
  
  // Internal signals are encapsulated
  val internalLogic = io.input + 1
  io.output := internalLogic
}
```

### Scenario: Invalid Direct Signal Access
**WHEN** external code accesses Component internal signals
**THEN** it must be flagged as encapsulation violation

**Invalid Example**:
```scala
case class BadComponent() extends Component {
  val io = new Bundle {
    val output = out UInt(32 bits)
  }
  
  // Exposed internal signal - VIOLATION
  val internalReg = Reg(UInt(32 bits)) init(0)
}

// External access - NOT ALLOWED
val comp = new BadComponent()
comp.internalReg := 42  // ✗ Direct access violation
```

### Scenario: Valid AXI4 Interface Definition
**WHEN** defining an AXI4 interface
**THEN** the configuration and bundle must follow standards

**Example**:
```scala
// Configuration class
case class Axi4Config(
  addressWidth: Int = 32,
  dataWidth: Int = 32,
  idWidth: Int = 4
)

// Bundle definition
case class Axi4(config: Axi4Config) extends Bundle with IMasterSlave {
  val aw = Stream(Axi4Aw(config))
  val w  = Stream(Axi4W(config))
  val b  = Stream(Axi4B(config))
  val ar = Stream(Axi4Ar(config))
  val r  = Stream(Axi4R(config))
}
```

### Scenario: Invalid Bundle with Scala Types
**WHEN** a Bundle contains Scala types
**THEN** compilation must fail with clear error

**Invalid Example**:
```scala
case class InvalidBundle() extends Bundle {
  val scalaInt = 42        // ✗ Scala type in Bundle
  val scalaBool = true     // ✗ Scala type in Bundle
}
```

### Scenario: Configuration Class with Hardware Types
**WHEN** a configuration class contains hardware types
**THEN** it should be flagged as design error

**Invalid Example**:
```scala
case class InvalidConfig() {
  val hardwareSignal = Bool()  // ✗ Hardware type in config
}
```

### Scenario: Valid Stream-Based Component
**WHEN** implementing data processing components
**THEN** Stream infrastructure should be used for flow control

**Example**:
```scala
case class StreamProcessor(config: Config) extends Component {
  val io = new Bundle {
    val input = slave Stream(DataBundle())
    val output = master Stream(DataBundle())
  }

  // ✓ Stream-based processing with backpressure
  io.output << io.input.translateWith {
    val processed = cloneOf(io.input.payload)
    processed.data := io.input.payload.data + 1
    processed.assignUnassignedByName(io.input.payload)  // assignUnassignedByName connects all unassigned fields, must be called after all custom assignments
    processed
  }
}
```

### Scenario: Invalid State Machine Instead of Stream
**WHEN** using complex state machines for data flow
**THEN** it should be flagged as potential Stream candidate

**Invalid Example**:
```scala
case class StateMachineProcessor() extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
    val valid = in Bool()
    val ready = out Bool()
  }

  // ✗ Complex state machine - consider Stream alternative
  val state = Reg(StateEnum()) init(StateEnum.IDLE)
  switch(state) {
    is(StateEnum.IDLE) {
      when(io.valid) { state := StateEnum.PROCESS }
    }
    is(StateEnum.PROCESS) {
      io.output := io.input + 1
      state := StateEnum.DONE
    }
    is(StateEnum.DONE) {
      io.ready := True
      state := StateEnum.IDLE
    }
  }
}
```

### Scenario: Valid Bool Type Usage
**WHEN** using Bool types for single-bit signals
**THEN** proper initialization and edge detection must be used

**Example**:
```scala
val enable = Bool()
val risingEdge = enable.rise()  // ✓ Correct edge detection
val activeHigh = True           // ✓ Correct constant usage
```

### Scenario: Valid State Machine Implementation
**WHEN** implementing state machines
**THEN** SpinalHDL state machine constructs must be used

**Example**:
```scala
val fsm = new StateMachine {
  val IDLE = new State with EntryPoint
  val PROCESSING = new State
  
  IDLE.whenIsActive {
    when(trigger) { goto(PROCESSING) }
  }
  
  PROCESSING.whenIsActive {
    when(completed) { goto(IDLE) }
  }
}
```

### Scenario: Valid Pipeline Design
**WHEN** implementing pipelined designs
**THEN** proper register stages must be used for timing

**Example**:
```scala
val stage1 = RegNext(input + offset)
val stage2 = RegNext(stage1 * factor)
val result = RegNext(stage2 - adjustment)
```

### Scenario: Valid Clock Domain Crossing
**WHEN** crossing clock domains
**THEN** proper synchronization must be used

**Example**:
```scala
val slowClock = ClockDomain.external("slow")
val fastClock = ClockDomain.external("fast")

val slowArea = new ClockingArea(slowClock) {
  val counter = RegInit(U(0, 8 bits))
}

val fastArea = new ClockingArea(fastClock) {
  val syncCounter = BufferCC(slowArea.counter, U(0))
}
```

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

## ADDED: SpinalHDL Design Rules from User Guide

### Basic Data Type Usage Standards
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

#### Scenario: Proper Bool Type Usage
- **WHEN** declaring single-bit logical signals
- **THEN** use `Bool()` type with `True`/`False` constants and edge detection methods

#### Scenario: Proper Bits Type Usage
- **WHEN** working with raw bit data
- **THEN** use `Bits(n bits)` with binary/hexadecimal literals and bit manipulation operations

#### Scenario: Proper Integer Arithmetic
- **WHEN** performing arithmetic operations
- **THEN** use `UInt` for unsigned and `SInt` for signed arithmetic with proper type constants

### Sequential Logic Design Patterns
Sequential logic MUST use proper register instantiation and timing control patterns.

**Rationale**: Proper sequential logic design ensures correct timing behavior and prevents common hardware errors like combinational loops and unintended latches.

**Requirements**:
- Use `Reg()` for basic registers with optional initialization
- Use `RegNext()` for single-cycle delayed signals
- Use `RegInit()` for registers with explicit reset values
- Use `RegNextWhen()` for conditional sampling
- Avoid combinational loops through proper register usage
- Ensure complete assignment in conditional blocks to prevent latches

#### Scenario: Register Instantiation Patterns
- **WHEN** creating sequential logic elements
- **THEN** use appropriate register types with proper initialization

#### Scenario: Memory Design Patterns
- **WHEN** implementing memory elements
- **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces

### Clock Domain Management Principles
Multi-clock domain designs MUST use proper clock domain crossing and synchronization techniques.

**Rationale**: Proper clock domain management prevents metastability and timing violations in designs with multiple clock domains.

**Requirements**:
- Use `ClockDomain()` to define clock domains with proper configuration
- Use `ClockingArea` to encapsulate logic within specific clock domains
- Use `BufferCC` for proper clock domain crossing synchronization
- Avoid direct signal connections between different clock domains
- Use appropriate reset synchronization for multi-clock designs

#### Scenario: Single Clock Domain Design
- **WHEN** designing with single clock domain
- **THEN** use default clock domain with proper reset handling

#### Scenario: Multi Clock Domain Design
- **WHEN** designing with multiple clock domains
- **THEN** use proper clock domain crossing synchronization with `BufferCC`

### Advanced Design Patterns
Complex designs MUST use appropriate advanced patterns for state machines and pipelining.

**Rationale**: Advanced design patterns improve code readability, maintainability, and performance for complex hardware implementations.

**Requirements**:
- Use `StateMachine` class for complex state machines with clear state transitions
- Use pipeline stages with `RegNext` for improved throughput
- Use balanced trees for reduction operations in performance-critical paths
- Implement resource sharing for common operations to optimize area
- Consider timing constraints during pipeline design

#### Scenario: State Machine Implementation
- **WHEN** implementing finite state machines
- **THEN** use `StateMachine` class with `whenIsActive` and `goto` methods

#### Scenario: Pipeline Design
- **WHEN** designing high-throughput circuits
- **THEN** use multiple pipeline stages with proper register balancing

### Debugging and Optimization Guidelines
Hardware designs MUST include proper verification constructs and optimization techniques.

**Rationale**: Design verification prevents common hardware errors and optimization techniques improve circuit performance and resource utilization.

**Requirements**:
- Use `assert()` statements for design constraints and invariants
- Use `report()` for debugging information during elaboration
- Enable design checks for combinatorial loops and latch detection
- Use conditional compilation for debug features in production code
- Implement performance optimization techniques for critical paths

#### Scenario: Design Verification
- **WHEN** implementing hardware components
- **THEN** include assertions for critical design constraints

#### Scenario: Performance Optimization
- **WHEN** optimizing performance-critical circuits
- **THEN** use pipelining, resource sharing, and balanced operations

### Simulation and Testing Best Practices
Test code MUST follow established patterns for reliable verification and maintainability.

**Rationale**: Consistent simulation patterns ensure reliable test results and maintainable test code that clearly separates verification logic from hardware design.

**Requirements**:
- Use `SpinalAnyFunSuite` for unit tests and `SpinalTesterGhdlBase` for simulation tests
- Follow test class naming conventions ending with "Tester" or "Test"
- Organize test code in hierarchical structure mirroring main code
- Use explicit signal assignments with `#=` operator in simulation
- Use proper data types (`Int`, `Boolean`, `BigInt`) for test assignments
- Group related test assignments logically with descriptive comments

#### Scenario: Test Class Organization
- **WHEN** creating test classes
- **THEN** follow naming conventions and place in appropriate directory structure

#### Scenario: Simulation Signal Assignment
- **WHEN** assigning signals during simulation
- **THEN** use explicit indexing for collections and proper data types

#### Scenario: Test Execution
- **WHEN** executing tests
- **THEN** use standardized sbt commands with proper class names

## MODIFIED: Enhanced Requirements

### Component Encapsulation Standards (Enhanced)
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

#### Scenario: Valid Component Encapsulation
- **WHEN** defining a Component
- **THEN** all IO must go through the `io` Bundle and internal signals are properly encapsulated

#### Scenario: Complete Signal Assignment
- **WHEN** creating hardware signals in transformations
- **THEN** all Bundle fields must be explicitly assigned using `assignUnassignedByName()`

#### Scenario: Direct Object Access
- **WHEN** assigning Bundle fields
- **THEN** use direct object access instead of method chaining

### Stream-Based Design Pattern (Enhanced)
Data processing components MUST use Stream infrastructure for flow control and backpressure, with proper signal assignment completeness.

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

#### Scenario: Valid Stream-Based Component
- **WHEN** implementing data processing components
- **THEN** Stream infrastructure should be used for flow control with complete signal assignment

#### Scenario: Invalid State Machine Instead of Stream
- **WHEN** using complex state machines for data flow
- **THEN** it should be flagged as potential Stream candidate

### Test Signal Assignment Patterns (Enhanced)
Test signal assignments during simulation MUST follow consistent patterns for different data types with explicit collection indexing.

**Rationale**: Consistent assignment patterns improve test code readability and reduce errors in simulation-based testing. Explicit collection indexing prevents ambiguous assignments and improves maintainability.

**Requirements**:
- Use `#=` operator for all signal assignments in simulation
- Use appropriate data types: `Int` for integer values, `Boolean` for boolean values, `BigInt` for large integers
- Use explicit indexing for all collection assignments (e.g., `signal(index) #= value`)
- Use `.randomize()` for generating random test data
- Use descriptive variable names for complex expressions
- Group related assignments and use comments to explain test phases

**Valid Examples**:
```scala
// ✓ Clear: explicit indexing for all collections
dut.io.dfi.rdTraining.rdlvlReq(0) #= false      // Read level training request for slice 0

// ✓ Clear: explicit loop for collection initialization
for (i <- 0 until dut.io.dfi.read.rd.length) {
  dut.io.dfi.read.rd(i).rddataValid #= false
  dut.io.dfi.read.rd(i).rddata #= 0
}
```

#### Scenario: Boolean Signal Assignment
- **WHEN** assigning boolean signals in tests
- **THEN** use `#=` operator with `Boolean` values and explicit indexing

#### Scenario: Integer/Bits Signal Assignment
- **WHEN** assigning integer or bits signals in tests
- **THEN** use `#=` operator with proper data types and explicit indexing

#### Scenario: Collection Signal Assignment
- **WHEN** assigning signals to collections in tests
- **THEN** use explicit indexing and avoid `foreach(_ #= value)` patterns

### Scenario: Valid Register Instantiation
- **WHEN** creating sequential logic elements
- **THEN** use appropriate register types with proper initialization

**Example**:
```scala
// ✓ Correct register usage
val counter = Reg(UInt(8 bits)) init(0)
val delayedSignal = RegNext(inputSignal)
val resetCounter = RegInit(U(0, 8 bits))
val conditionalReg = RegNextWhen(data, condition)
```

### Scenario: Valid Memory Design
- **WHEN** implementing memory elements
- **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces

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

### Scenario: Valid Clock Domain Configuration
- **WHEN** designing with multiple clock domains
- **THEN** use proper clock domain configuration and synchronization

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

### Scenario: Valid Clock Domain Crossing
- **WHEN** crossing clock domains
- **THEN** use proper synchronization with `BufferCC`

**Example**:
```scala
// ✓ Correct clock domain crossing
val slowClockDomain = ClockDomain.external("slow")
val fastClockDomain = ClockDomain.external("fast")

val slowArea = new ClockingArea(slowClockDomain) {
  val data = RegInit(U(0, 8 bits))
}

val fastArea = new ClockingArea(fastClockDomain) {
  val syncData = BufferCC(slowArea.data, U(0))
}
```

### Scenario: Valid Pipeline Optimization
- **WHEN** designing high-throughput circuits
- **THEN** use multiple pipeline stages with proper register balancing

**Example**:
```scala
// ✓ Balanced pipeline stages
val stage1 = RegNext(io.input + 1)
val stage2 = RegNext(stage1 * 2)
val stage3 = RegNext(stage2 - 3)
io.output := stage3
```

### Scenario: Valid Resource Sharing
- **WHEN** optimizing area usage
- **THEN** share common operations using multiplexers

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

### Scenario: Valid Design Verification
- **WHEN** implementing hardware components
- **THEN** include assertions for critical design constraints

**Example**:
```scala
// ✓ Design assertions
assert(io.input <= 100, "Input should be <= 100", WARNING)

// ✓ Debug reports
report(Seq("Input value:", io.input), "DEBUG")
```

### Scenario: Valid Performance Optimization
- **WHEN** optimizing performance-critical circuits
- **THEN** use balanced trees and pipelining techniques

**Example**:
```scala
// ✓ Use balanced tree to reduce delay
val sum = data.reduceBalancedTree(_ + _)

// ✓ Use pipelining to improve throughput
val pipelinedSum = RegNext(data(0) + data(1)) + RegNext(data(2) + data(3))
```

### Scenario: Valid Test Stimulus Generation
- **WHEN** creating test suites
- **THEN** use random data generation with controlled seeds

**Example**:
```scala
// ✓ Random testing with controlled seed
val random = new Random(42)  // Fixed seed for reproducibility
for (i <- 0 until 100) {
  dut.io.input #= random.nextInt(256)
  dut.clockDomain.waitSampling()
  // Verify expected behavior
}
```

### Scenario: Valid Test Coverage
- **WHEN** implementing test suites
- **THEN** achieve comprehensive coverage of design functionality

**Example**:
```scala
// ✓ Functional coverage tracking
var coverage = new CoverageTracker()
for (i <- 0 until testCycles) {
  val stimulus = generateStimulus()
  dut.io.input #= stimulus
  coverage.record(stimulus)
  dut.clockDomain.waitSampling()
}

// ✓ Report coverage
coverage.report()
assert(coverage.achieved >= coverage.target)
```