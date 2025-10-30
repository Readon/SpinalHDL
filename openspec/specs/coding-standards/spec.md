# Coding Standards Specification

## Purpose
This specification defines the global coding standards for SpinalHDL library development, ensuring consistent, maintainable, and hardware-correct code generation. It provides comprehensive guidelines for hardware design using SpinalHDL, covering data types, component design, clock domain management, testing practices, and optimization techniques.

## Requirements

### Requirement: REQ-CS-001: Component IO Bundle Access and Naming Standards
All Component class input/output signals MUST be accessed through the `io` named Bundle, and internal signals MUST be properly encapsulated.

**Rationale**: SpinalHDL Components encapsulate hardware modules, and all external interfaces must go through the dedicated `io` Bundle to maintain proper encapsulation and prevent direct signal access. Additionally, proper signal assignment completeness ensures all hardware signals have defined connections.

**Requirements**:
- Component classes MUST define a `val io = new Bundle { ... }` field
- All input/output signals MUST be defined within the `io` Bundle
- External objects MUST NOT directly access internal Component signals
- Internal signals MAY be accessed within the Component for logic implementation
- Bundle names MUST follow general naming conventions (see REQ-CS-033)
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

  val processingArea = new Area {
    // ✓ Correct: Descriptive Bundle names in Area
    val controlSignals = new Bundle {
      val enable = Bool()
      val reset = Bool()
    }

    val dataSignals = new Bundle {
      val inputData = UInt(32 bits)
      val outputData = UInt(32 bits)
    }

    // ✗ Incorrect: Avoid "io" in Area objects
    // val io = new Bundle { ... }  // Not allowed in Area
  }
}
```

#### Scenario: Component IO Bundle Access
- **WHEN** defining a Component **THEN** all IO must go through the `io` Bundle and internal signals are properly encapsulated

#### Scenario: Hardware Signal Assignment in Transformations
- **WHEN** creating hardware signals in transformations **THEN** all Bundle fields must be explicitly assigned using `assignUnassignedByName()`

#### Scenario: Bundle Field Assignment
- **WHEN** assigning Bundle fields **THEN** use direct object access instead of method chaining

#### Scenario: Component Encapsulation
- **WHEN** external code accesses Component internal signals **THEN** it must be flagged as encapsulation violation

#### Scenario: AXI4 Interface Definition
- **WHEN** defining an AXI4 interface **THEN** the configuration and bundle must follow standards

#### Scenario: Bundle Type Validation
- **WHEN** a Bundle contains Scala types **THEN** compilation must fail with clear error

#### Scenario: Configuration Class Type Validation
- **WHEN** a configuration class contains hardware types **THEN** it should be flagged as design error

### Requirement: REQ-CS-008: Bundle and Interface Direction Management
Bundle inheritance and IMasterSlave interface implementation MUST follow standardized patterns for signal direction management to ensure flexibility and prevent conflicts.

**Rationale**: When creating new interface types through Bundle inheritance or implementing IMasterSlave interfaces, signal directions should not be specified in signal definitions to avoid unnecessary constraints and potential interface conflicts. Directions must be uniformly managed through the IMasterSlave trait's asMaster() method to maintain interface flexibility and standardization.

**Requirements**:
- When inheriting from Bundle to create new interface types, signal directions MUST NOT be specified in signal definitions
- When implementing IMasterSlave interfaces, signal directions MUST be explicitly specified only in the asMaster function
- In other functions or contexts, signal directions MUST NOT be given
- Directions MUST be managed uniformly through the IMasterSlave trait's asMaster() method
- This prevents unnecessary constraints, potential interface conflicts, and ensures consistent interface behavior

#### Scenario: Bundle Inheritance Interface Creation
- **WHEN** creating new interface types through Bundle inheritance **THEN** signal directions must not be specified in signal definitions and should be managed via IMasterSlave.asMaster()

#### Scenario: IMasterSlave Interface Implementation
- **WHEN** implementing IMasterSlave interface **THEN** signal directions must be specified only in asMaster function

#### Scenario: Bundle Inheritance Direction Validation
- **WHEN** signal directions are specified in Bundle inheritance **THEN** it must be flagged as design error

#### Scenario: IMasterSlave Direction Validation
- **WHEN** signal directions are specified outside asMaster function **THEN** it must be flagged as implementation error

### Requirement: REQ-CS-002: Basic Data Type Usage Standards
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

#### Scenario: Single-bit Logical Signal Declaration
- **WHEN** declaring single-bit logical signals **THEN** use `Bool()` type with `True`/`False` constants and edge detection methods

#### Scenario: Raw Bit Data Processing
- **WHEN** working with raw bit data **THEN** use `Bits(n bits)` with binary/hexadecimal literals and bit manipulation operations

#### Scenario: Arithmetic Operations
- **WHEN** performing arithmetic operations **THEN** use `UInt` for unsigned and `SInt` for signed arithmetic with proper type constants

#### Scenario: Bool Type Usage
- **WHEN** using Bool types for single-bit signals **THEN** proper initialization and edge detection must be used

### Requirement: REQ-CS-003: Sequential Logic Design Patterns
Sequential logic MUST use proper register instantiation and timing control patterns.

**Rationale**: Proper sequential logic design ensures correct timing behavior and prevents common hardware errors like combinational loops and unintended latches.

**Requirements**:
- Use `Reg()` for basic registers with optional initialization
- Use `RegNext()` for single-cycle delayed signals
- Use `RegInit()` for registers with explicit reset values
- Use `RegNextWhen()` for conditional sampling
- Avoid combinational loops through proper register usage
- Ensure complete assignment in conditional blocks to prevent latches
- For combinational logic signals that are conditionally assigned in `when`/`switch` blocks, provide default assignments outside these blocks to prevent latches

#### Scenario: Sequential Logic Element Creation
- **WHEN** creating sequential logic elements **THEN** use appropriate register types with proper initialization

#### Scenario: Memory Element Implementation
- **WHEN** implementing memory elements **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces

#### Scenario: Combinational Logic Design
- **WHEN** designing combinational logic with conditional assignments **THEN** provide default assignments outside `when`/`switch` blocks to prevent latches

### Requirement: REQ-CS-004: Clock Domain Management Principles
Multi-clock domain designs MUST use proper clock domain crossing and synchronization techniques.

**Rationale**: Proper clock domain management prevents metastability and timing violations in designs with multiple clock domains.

**Requirements**:
- Use `ClockDomain()` to define clock domains with proper configuration
- Use `ClockingArea` to encapsulate logic within specific clock domains
- Use `BufferCC` for proper clock domain crossing synchronization
- Avoid direct signal connections between different clock domains
- Use appropriate reset synchronization for multi-clock designs

#### Scenario: Single Clock Domain Design
- **WHEN** designing with single clock domain **THEN** use default clock domain with proper reset handling

#### Scenario: Multi-clock Domain Design
- **WHEN** designing with multiple clock domains **THEN** use proper clock domain crossing synchronization with `BufferCC`

### Requirement: REQ-CS-005: Advanced Design Patterns
Complex designs MUST use appropriate advanced patterns for state machines and pipelining.

**Rationale**: Advanced design patterns improve code readability, maintainability, and performance for complex hardware implementations.

**Requirements**:
- Use `StateMachine` class for complex state machines with clear state transitions
- Use pipeline stages with `RegNext` for improved throughput
- Use balanced trees for reduction operations in performance-critical paths
- Implement resource sharing for common operations to optimize area
- Consider timing constraints during pipeline design

#### Scenario: Finite State Machine Implementation
- **WHEN** implementing finite state machines **THEN** use `StateMachine` class with `whenIsActive` and `goto` methods

#### Scenario: High-throughput Circuit Design
- **WHEN** designing high-throughput circuits **THEN** use multiple pipeline stages with proper register balancing

### Requirement: REQ-CS-006: Debugging and Optimization Guidelines
Hardware designs MUST include proper verification constructs and optimization techniques.

**Rationale**: Design verification prevents common hardware errors and optimization techniques improve circuit performance and resource utilization.

**Requirements**:
- Use `assert()` statements for design constraints and invariants
- Use `report()` for debugging information during elaboration
- Enable design checks for combinatorial loops and latch detection
- Use conditional compilation for debug features in production code
- Implement performance optimization techniques for critical paths
- For combinational signals conditionally assigned in `when`/`switch` blocks, provide default assignments outside these blocks to prevent latches

#### Scenario: Hardware Component Implementation
- **WHEN** implementing hardware components **THEN** include assertions for critical design constraints

#### Scenario: Performance-critical Circuit Optimization
- **WHEN** optimizing performance-critical circuits **THEN** use pipelining, resource sharing, and balanced operations

#### Scenario: Latch Warning Resolution
- **WHEN** detecting latch warnings **THEN** add default assignments outside conditional blocks to resolve the issue

### Requirement: REQ-CS-007: Verification, Simulation and Testing Standards
Test code MUST follow established patterns for reliable verification and maintainability, including DUT definition, SimConfig usage, doSim block logic, simulator support, clock domain management, and assertions. All verification work MUST be completed in SpinalAnyFunSuite or SpinalSimFunSuite test suites, not within the hardware design components.

**Rationale**: Consistent simulation patterns ensure reliable test results and maintainable test code that clearly separates verification logic from hardware design. Hardware components should focus solely on functional implementation, while all assertions, checks, and validation logic belong exclusively in dedicated test suites. Comprehensive testing covers DUT instantiation, configuration, execution, and validation across supported simulators.

**Requirements**:
- Use `SpinalAnyFunSuite` or `SpinalSimFunSuite` (for multi-simulator support) as base classes for test suites
- Test class names MUST follow general naming conventions (see REQ-CS-033)
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
- All verification constructs (assertions, assumptions, covers, etc.) MUST be placed in test suites extending SpinalAnyFunSuite or SpinalSimFunSuite
- Hardware design components MUST NOT contain verification logic or test-specific constructs
- Verification logic MUST NOT be embedded within Component classes, Area objects, or any hardware description code
- Design components MUST remain pure hardware descriptions without any verification dependencies

#### Scenario: Test Class Creation
- **WHEN** creating test classes **THEN** follow naming conventions and place in appropriate directory structure

#### Scenario: Simulation Signal Assignment
- **WHEN** assigning signals during simulation **THEN** use explicit indexing for collections and proper data types

#### Scenario: Test Execution
- **WHEN** executing tests **THEN** use standardized sbt commands with proper class names

#### Scenario: Test Suite Creation
- **WHEN** creating test suites **THEN** use random data generation with controlled seeds

#### Scenario: Test Coverage Implementation
- **WHEN** implementing test suites **THEN** achieve comprehensive coverage of design functionality

#### Scenario: DUT Definition
- **WHEN** defining a DUT for testing **THEN** use a Component class with io Bundle and compile via SimConfig

#### Scenario: Test Logic Implementation
- **WHEN** implementing test logic **THEN** use fork for concurrency, waitSampling for timing control, and assertions for validation

#### Scenario: Multi-simulator Support
- **WHEN** running simulations **THEN** support Verilator, GHDL, IVerilog with appropriate SimConfig settings

#### Scenario: Multi-clock Design Testing
- **WHEN** testing multi-clock designs **THEN** use forkStimulus and ClockDomain for proper timing

#### Scenario: Output Validation
- **WHEN** validating outputs **THEN** use assert or shouldBe with descriptive messages

#### Scenario: Verification Logic Placement
- **WHEN** implementing verification logic **THEN** place it exclusively in SpinalAnyFunSuite or SpinalSimFunSuite test suites

#### Scenario: Assertion Placement
- **WHEN** adding assertions or checks **THEN** ensure they are in test files, not design components

#### Scenario: Hardware Component Purity
- **WHEN** verification logic is found in hardware components **THEN** it must be flagged as architecture violation

#### Scenario: Verification-free Hardware Design
- **WHEN** designing hardware components **THEN** keep them free of any verification constructs

#### Scenario: Test Code Organization
- **WHEN** organizing test code **THEN** use proper test suite classes and directory structure

### Requirement: REQ-CS-013: Stream-Based Design Pattern
Data processing components SHALL use Stream infrastructure for flow control and backpressure.

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

#### Scenario: Stream-based Data Processing
- **WHEN** implementing data processing components **THEN** Stream infrastructure should be used for flow control with complete signal assignment

#### Scenario: State Machine to Stream Conversion
- **WHEN** using complex state machines for data flow **THEN** it should be flagged as potential Stream candidate

### Requirement: REQ-CS-027: Hardware Description Syntax Purity
Hardware description MUST use only SpinalHDL constructs and avoid mixing Scala runtime syntax to ensure correct hardware generation and prevent type mismatches.

**Rationale**: SpinalHDL provides specific constructs for hardware description, and mixing Scala runtime syntax (like conditional expressions) with hardware signals leads to type errors and incorrect behavior. Hardware logic must be described using SpinalHDL's declarative syntax to maintain synthesis compatibility and predictability.

**Requirements**:
- Prohibit the use of Scala conditional expressions (e.g., `cond ? a : b`) in hardware description contexts, as `cond` is a `Bool` hardware signal and Scala `? :` expects `Boolean`
- Use `when() {} otherwise {}` statements for conditional hardware logic
- Use SpinalHDL assignment operators (`:=`) exclusively for hardware signal assignments
- Avoid Scala runtime constructs in Component bodies and Area classes
- Ensure all hardware logic is described using SpinalHDL's built-in constructs

**Valid Examples**:
```scala
case class HardwareLogic(config: Config) extends Component {
  val io = new Bundle {
    val input = in Bool()
    val output = out Bits(32 bits)
  }

  // ✓ Correct: Use when() for conditional logic
  when(io.input) {
    io.output := B"32'hFFFFFFFF"
  } otherwise {
    io.output := B"32'h00000000"
  }

  // ✓ Correct: SpinalHDL assignment
  val signal = Bits(32 bits)
  signal := io.input ? B"32'hAAAAAAAA" | B"32'hBBBBBBBB"  // Valid SpinalHDL mux operator
}
```

**Invalid Examples**:
```scala
case class HardwareLogic(config: Config) extends Component {
  val io = new Bundle {
    val input = in Bool()
    val output = out Bits(32 bits)
  }

  // ✗ Incorrect: Scala conditional expression with hardware signal
  val result = io.input ? B"32'hFFFFFFFF" : B"32'h00000000"  // Type error: Bool vs Boolean

  // ✗ Incorrect: Mixing Scala syntax in hardware context
  io.output := if (io.input) B"32'hFFFFFFFF" else B"32'h00000000"  // Scala if in hardware
}
```

#### Scenario: Conditional hardware logic
- **WHEN** implementing conditional hardware logic **THEN** use `when() {} otherwise {}` statements

#### Scenario: Conditional assignments
- **WHEN** using conditional assignments **THEN** use SpinalHDL mux operators like `? |` for hardware signals

#### Scenario: Scala ternary misuse
- **WHEN** using Scala `? :` with hardware signals **THEN** it must be flagged as type error

#### Scenario: Scala runtime condition misuse
- **WHEN** using Scala `if` or other runtime syntax with hardware signals as condition **THEN** it should be flagged as hardware description violation

### Requirement: REQ-CS-026: Test Signal Assignment Patterns
Test signal assignments during simulation MUST follow consistent patterns for different data types, strictly using `#=` in doSim blocks and avoiding `:=` which is reserved for hardware logic.

**Rationale**: Consistent assignment patterns improve test code readability and reduce errors in simulation-based testing. Explicit collection indexing prevents ambiguous assignments and improves maintainability. Distinguishing simulation assignments from hardware assignments prevents compilation errors.

**Requirements**:
- Use `#=` operator exclusively for all signal assignments in simulation functions like `doSim` or `doSimUntilVoid`
- Never use `:=` in simulation contexts, as it is for hardware logic and will cause type errors
- Use appropriate data types: `Int` for integer values, `Boolean` for boolean values, `BigInt` for large integers
- Use explicit indexing for all collection assignments (e.g., `signal(index) #= value`)
- Use `.randomize()` for generating random test data
- Variable names MUST follow general naming conventions (see REQ-CS-033)
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

#### Scenario: Boolean signal simulation assignment
- **WHEN** assigning boolean signals in tests **THEN** use `#=` operator with `Boolean` values and explicit indexing

#### Scenario: Integer/bits signal simulation assignment
- **WHEN** assigning integer or bits signals in tests **THEN** use `#=` operator with proper data types and explicit indexing

#### Scenario: Collection assignment in simulation
- **WHEN** assigning signals to collections in tests **THEN** use `foreach(_ #= value)` patterns for Vec, List, and similar container types' assignment.

#### Scenario: Forbidden hardware assignment in simulation
- **WHEN** using `:=` in doSim blocks **THEN** it must be flagged as compilation error since `:=` is for hardware logic

### Requirement: REQ-CS-028: Signal Connection Standards
Signal connections MUST follow proper operator usage to ensure correctness and consistency, preventing unintended cross-component connections.

**Rationale**: Proper signal connection practices ensure hardware correctness and maintain design integrity. The "<>" operator is designed for automatic signal connection within the same component or bundle initialization context, while cross-component connections require explicit assignment operators to avoid accidental connections and maintain clear design intent.

**Requirements**:
- The "<>" operator MUST be used only for signal connections within the same Component or Bundle initialization block
- The "<>" operator MUST NOT be used for connections between parent and child modules, even within the same Component or Area. Use explicit assignment operators like ":=" for such connections to maintain design clarity
- Cross-component signal connections MUST NOT use the "<>" operator and SHOULD use ":=" or other appropriate assignment operators
- All signal connections MUST be explicit and intentional to prevent unintended connections
- Connection patterns MUST be consistent across the codebase to ensure maintainability

**Example**:
```scala
case class MyComponent(config: MyConfig) extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  // ✓ Correct: <> for internal bundle connections
  val internalBundle = new Bundle {
    val data = UInt(32 bits)
    val valid = Bool()
  }
  internalBundle <> io.input  // Valid within same component

  // ✓ Correct: := for cross-component connections
  io.output := internalBundle.data + 1
}

case class TopLevel() extends Component {
  val comp1 = new MyComponent(config1)
  val comp2 = new MyComponent(config2)

  // ✓ Correct: Explicit assignment for cross-component
  comp2.io.input := comp1.io.output

  // ✗ Incorrect: Using <> for cross-component (compilation error)
  // comp2.io <> comp1.io  // Invalid: <> not allowed across components
}
```

#### Scenario: Internal Signal Connection
- **WHEN** connecting signals within the same Component or Bundle initialization **THEN** use "<>" operator for automatic connection

#### Scenario: Parent-child Module Connection
- **WHEN** connecting signals between parent and child modules **THEN** use explicit assignment operators like ":=" and MUST NOT use "<>" operator

#### Scenario: Cross-component Connection
- **WHEN** connecting signals between different Components **THEN** use explicit assignment operators like ":=" to maintain design clarity

#### Scenario: Cross-component Connection Validation
- **WHEN** using "<>" operator across Components **THEN** it must be flagged as connection violation

#### Scenario: Parent-child Connection Validation
- **WHEN** using "<>" operator between parent and child modules **THEN** it must be flagged as connection violation

#### Scenario: Hierarchical Component Design
- **WHEN** designing hierarchical components **THEN** ensure connection patterns are consistent and explicit

### Requirement: REQ-CS-029: IMasterSlave Interface Connection Functions
IMasterSlave interfaces MUST implement << and >> functions for standardized bidirectional connections with proper signal direction handling.

**Rationale**: Connection functions provide symmetric interface operations, enabling flexible signal routing while ensuring type safety and consistent connection patterns across interface types.

**Requirements**:
- IMasterSlave interfaces SHOULD implement << function for forward connections with proper signal direction handling
- Interfaces implementing << MUST also implement >> function that calls << with reversed arguments for symmetry
- Both functions MUST ensure type safety and prevent invalid signal connections based on master/slave configuration
- Functions MUST support interface composition and chaining for flexible signal routing
- Connect signals defined with << and >> functions with those functions as possible

**Connection Usage Requirements**:
- When connecting IMasterSlave interfaces that implement << and >> functions, these functions MUST be used for connections instead of manual signal assignments
- Prefer << and >> functions over individual signal assignments (:=) for interface connections to ensure consistency and type safety
- Interface connections using << and >> functions improve code readability and maintainability

**Example**:
```scala
trait MyInterface extends Bundle with IMasterSlave {
  val data = UInt(32 bits)
  val valid = Bool()
  val ready = Bool()

  def asMaster(): Unit = {
    out(data, valid)
    in(ready)
  }

  def <<(that: MyInterface) : Unit = {
    this.data := that.data // treat that as master, so that.data is output.
    this.valid := that.valid
    that.ready := this.ready // that's input signals would be assigned.
  }

  def >>(that: MyInterface) : MyInterface = that << this // Reverse operation via << call
}

case class MyComponent() extends Component {
  val io = new Bundle {
    val input = slave(MyInterface())
    val output = master(MyInterface())
  }

  // ✓ Correct: Use << and >> functions for interface connections
  io.output << io.input  // Forward connection
  io.input >> io.output  // Reverse connection

  // ✗ Incorrect: Manual signal assignments when << and >> are available
  // io.output.data := io.input.data
  // io.output.valid := io.input.valid
  // io.input.ready := io.output.ready
}
```

#### Scenario: IMasterSlave Interface Design
- **WHEN** designing IMasterSlave interfaces **THEN** implement both << and >> functions for bidirectional connections

#### Scenario: Interface Connection
- **WHEN** connecting interfaces **THEN** use << or >> functions for consistent, type-safe signal routing

#### Scenario: Interface Chaining
- **WHEN** chaining interfaces **THEN** leverage function composition for flexible signal paths

#### Scenario: Function-based Signal Connection
- **WHEN** connecting signals defined with << and >> functions **THEN** use those functions for connections

#### Scenario: Mandatory Function Usage
- **WHEN** an IMasterSlave interface implements << and >> functions **THEN** connections to that interface MUST use those functions instead of manual signal assignments

#### Scenario: Manual Assignment Validation
- **WHEN** manually assigning individual signals between IMasterSlave interfaces **THEN** it should be flagged as connection pattern violation if << and >> functions are available

#### Scenario: Connection Logic Design
- **WHEN** designing connection logic **THEN** prefer << and >> functions over := assignments for interface connections to improve maintainability

### Requirement: REQ-CS-030: Configuration Class Hardware Syntax Prohibition
Configuration classes SHALL NOT use hardware description syntax to maintain type safety and design clarity.

**Rationale**: Configuration classes should remain pure Scala constructs to avoid type mismatches and ensure they serve only as parameter containers without hardware logic.

**Requirements**:
- Configuration classes MUST NOT contain hardware types (e.g., `Bool`, `UInt`, `SInt`, `Bundle`)
- Configuration classes MUST NOT use hardware description syntax (e.g., `:=`, `when`, `<>`)
- Configuration classes SHOULD contain only pure Scala types and methods

#### Scenario: Configuration Class Definition
- **WHEN** defining a configuration class **THEN** it must not contain hardware types

#### Scenario: Configuration Class Hardware Syntax Validation
- **WHEN** a configuration class contains hardware syntax **THEN** it should be flagged as design error

### Requirement: REQ-CS-032: Area Object Signal Direction Restrictions
Area objects MUST use only directionless signals to maintain proper encapsulation and avoid interface confusion.

**Rationale**: Area objects provide encapsulation for related logic and signals. Direction signals in Area can break encapsulation and make interfaces unclear. Additionally, minimizing Bundle creation in Area objects improves code simplicity and readability.

**Requirements**:
- Area objects MUST NOT define signals with direction specifiers (`in`, `out`, `inout`, `master`, `slave`). All signals in Area objects MUST be directionless
- Area objects SHOULD avoid creating new Bundles; prefer direct signal definitions for simplicity

**Example**:
```scala
case class MyComponent(config: MyConfig) extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  val processingArea = new Area {
    // ✓ Correct: Directionless signals in Area
    val enable = Bool()
    val reset = Bool()
    val data = UInt(32 bits)

    // ✗ Incorrect: Direction signals in Area
    // val input = in UInt(32 bits)  // Not allowed

    // ✗ Incorrect: Avoid new Bundles in Area
    // val signals = new Bundle { ... }  // Discouraged
  }
}
```

#### Scenario: Area Signal Definition
- **WHEN** defining signals in Area objects **THEN** avoid direction specifiers to maintain encapsulation

#### Scenario: Area Direction Validation
- **WHEN** a signal in Area has direction **THEN** it must be flagged as encapsulation violation

#### Scenario: Area Design Simplicity
- **WHEN** designing Area objects **THEN** prefer direct signal definitions over new Bundles for simplicity

### Requirement: REQ-CS-033: General Naming Conventions
All identifiers (classes, components, bundles, signals, variables, etc.) MUST follow consistent naming patterns to improve code readability and maintainability.

**Rationale**: Consistent and descriptive naming makes code self-documenting, reduces cognitive load, and facilitates understanding of design intent across the codebase.

**Requirements**:
- All names MUST be descriptive and indicate their specific purpose
- All names SHOULD reflect the functional role or content of the entity
- Avoid generic names that don't provide context about the entity's usage
- Use camelCase for variables, signals, and methods
- Use PascalCase for classes, components, and bundles
- Test class names MUST end with "Tester" or "Test"
- Bundle names MUST clearly indicate their interface purpose
- Signal names MUST describe their function or data content

**Example**:
```scala
// ✓ Correct: Descriptive naming
case class MemoryController(config: MemoryConfig) extends Component {
  val io = new Bundle {
    val readRequest = in Bool()
    val writeRequest = in Bool()
    val address = in UInt(32 bits)
    val dataOut = out Bits(64 bits)
  }
  
  val addressDecoder = new Area {
    val bankSelect = UInt(4 bits)
    val rowAddress = UInt(16 bits)
  }
}

// ✗ Incorrect: Generic naming
case class MyModule(config: MyConfig) extends Component {
  val io = new Bundle {
    val in1 = in Bool()
    val in2 = in Bool()
    val out1 = out Bits(64 bits)
  }
  
  val area1 = new Area {
    val sig1 = UInt(4 bits)
    val sig2 = UInt(16 bits)
  }
}
```

#### Scenario: Identifier Naming
- **WHEN** naming any identifier **THEN** it must be descriptive and indicate its specific purpose

#### Scenario: Generic Name Validation
- **WHEN** a name is generic or lacks context **THEN** it must be flagged as naming violation

#### Scenario: Configuration Class Naming
- **WHEN** naming configuration classes **THEN** they must end with "Config"

#### Scenario: Test Class Naming
- **WHEN** naming test classes **THEN** they must end with "Tester" or "Test"

#### Scenario: Bundle Naming
- **WHEN** naming bundles **THEN** they must clearly indicate their interface purpose

#### Scenario: Signal Naming
- **WHEN** naming signals **THEN** they must describe their function or data content

### Requirement: REQ-CS-031: Configuration Class Naming Convention
Configuration class names MUST follow standardized naming patterns for consistency and readability.

**Rationale**: Uniform naming conventions improve code maintainability and make configuration classes easily identifiable.

**Requirements**:
- Configuration class names MUST end with "Config"
- Configuration class names MUST follow general naming conventions (see REQ-CS-033)

#### Scenario: Configuration Class Identification
- **WHEN** a class's name ends with "Config" **THEN** it should be considered a configuration class

#### Scenario: Configuration Class Naming Requirement
- **WHEN** naming a configuration class **THEN** it must end with "Config"

#### Scenario: Configuration Class Naming Validation
- **WHEN** a configuration class does not end with "Config" **THEN** it should be flagged as naming violation

### Requirement: REQ-CS-036: Area and Composite Component Definition Order Standards
Area and Composite objects MUST ensure that all sub-components are defined before they are referenced or used to maintain proper dependency resolution and prevent runtime errors.

**Rationale**: In SpinalHDL, Area and Composite constructs allow encapsulation of related logic and signals. However, improper definition order can lead to undefined references, compilation failures, or unexpected behavior. Explicitly requiring components to be defined before use ensures hardware correctness, improves code maintainability, and prevents issues like NullPointerException in conditional logic.

**Requirements**:
- When using `new Area` or Composite constructs, all sub-components (signals, bundles, or nested areas) MUST be defined before any reference to them in assignments, connections, or logic expressions
- Analyze and document component dependency relationships explicitly to avoid circular dependencies or forward references
- Sub-components MUST be instantiated in the order of their usage dependencies
- Avoid forward declarations or lazy initialization that could defer definition until after usage
- For conditional component creation (e.g., via `generate`), ensure the condition is evaluated and components are defined before any dependent logic

**Example**:
```scala
case class MyComponent(config: MyConfig) extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  val processingArea = new Area {
    // ✓ Correct: Define sub-components before usage
    val enableSignal = Bool()
    val dataBuffer = Reg(UInt(32 bits)) init(0)

    // Usage after definition
    when(enableSignal) {
      dataBuffer := io.input + 1
    }
    io.output := dataBuffer

    // ✗ Incorrect: Usage before definition (compilation error)
    // io.output := undefinedBuffer  // undefinedBuffer not yet defined
    // val undefinedBuffer = Reg(UInt(32 bits)) init(0)
  }

  // For Composite (assuming similar construct)
  val compositeLogic = new Composite {
    val subComp1 = Reg(Bool()) init(false)  // Define first
    val subComp2 = Reg(Bool()) init(false)

    subComp2 := subComp1  // Usage after definition
  }
}
```

#### Scenario: Area Object Creation
- **WHEN** creating an Area object **THEN** define all sub-components before any assignments or references to maintain dependency order

#### Scenario: Composite Construct Usage
- **WHEN** using Composite constructs **THEN** ensure sub-components are instantiated in usage order to prevent undefined references

#### Scenario: Definition Order Validation
- **WHEN** a sub-component is referenced before definition **THEN** it must be flagged as definition order violation

#### Scenario: Component Dependency Analysis
- **WHEN** analyzing component dependencies **THEN** document relationships to avoid circular references

#### Scenario: Conditional Component Creation
- **WHEN** conditional component creation is used **THEN** verify definitions occur before dependent logic execution

### Requirement: REQ-CS-035: Signal Assignment and Initialization Standards
Signal assignment MUST follow strict initialization and conditional assignment patterns to ensure correct hardware generation and prevent unintended latches or combinational loops.

**Rationale**: SpinalHDL signals (especially Reg types) require proper initialization and conditional assignment to match hardware semantics. Initialization ensures registers have defined reset values, while conditional assignments prevent direct overwrites that could violate sequential logic rules.

**Requirements**:
- Initialization statements: Reg signals CAN be initialized using `init()` during declaration, or using assignment after declaration. Comb signals MUST be initialized using assignment after declaration.
- After initialization, all direct assignment to signals is prohibited; all subsequent assignments MUST be placed within `when`, `switch`, or similar hardware related conditional constructs
- Initialization statements MUST be positioned before any conditional assignment code to maintain proper hardware synthesis order
- Avoid mixing initialization and conditional assignments in the same Comb signal declaration

**Example**:
```scala
case class SignalAssignmentExample() extends Component {
  val io = new Bundle {
    val condition = in Bool()
    val output = out UInt(32 bits)
  }

  // ✓ Correct: Reg initialization before conditional code
  val myReg = Reg(UInt(32 bits)) init(0)
  when(io.condition) {
    myReg := myReg + 1  // Conditional assignment in when block
  }

  // ✓ Correct: Comb signal with complete assignment
  val myComb = UInt(32 bits)
  myComb := 0  // Default assignment outside conditional
  when(io.condition) {
    myComb := 42
  }

  // ✗ Incorrect: Direct assignment to Reg after initialization
  // myReg := 5  // Prohibited: Direct assignment outside conditional

  // ✗ Incorrect: Initialization after conditional code
  // when(io.condition) { myReg := 1 }
  // val myReg = Reg(UInt(32 bits)) init(0)  // Wrong order
}
```

#### Scenario: Signal Declaration
- **WHEN** declaring signals **THEN** initialize before any conditional assignments on itself

#### Scenario: Conditional Signal Assignment
- **WHEN** assigning to signals after initialization **THEN** place assignments in `when`/`switch` blocks only

#### Scenario: Default Assignment Provision
- **WHEN** declaring signals with conditional assignments **THEN** provide default assignments ahead of conditionals to prevent latches

#### Scenario: Initialization Order Validation
- **WHEN** initialization occurs after conditional code **THEN** it must be flagged as initialization order violation

#### Scenario: Direct Assignment Validation
- **WHEN** direct `:=` assignment to signal occurs outside conditionals and not an initialization **THEN** it must be flagged as assignment violation

#### Scenario: Signal Initialization Completeness
- **WHEN** signals do not have initialized **THEN** add default assignments/initialization to ensure completeness

### Requirement: REQ-CS-034: Conditional Signal Access Safety
All conditionally generated signals MUST be checked for their corresponding configuration conditions before use to prevent NullPointerException and other runtime errors.

**Rationale**: Conditionally generated signals (such as those created via `config.useXxx generate`) are not instantiated when configuration flags are false, leading to null pointer exceptions on direct access. Mandatory checking ensures code robustness and maintainability.

**Requirements**:
- Check corresponding configuration flags before each access to conditionally generated signals
- Use conditional checking pattern: `if (config.useFlag) { signal.operation() } else { defaultLogic }`
- Apply the same checking pattern for debug and statistics code
- Avoid direct access to potentially null signals outside condition checks
- Provide default values or alternative logic when conditions are not met

**Example**:
```scala
case class UnifiedAdapter(config: UnifiedAdapterConfig) extends Component {
  // ✓ Correct: Safe usage after condition check
  io.debug.trainingCount := {
    val reqs = Seq.newBuilder[Bool]
    if (config.dfiConfig.useRdlvlReq) reqs += io.dfi.rdTraining.rdlvlReq.orR
    if (config.dfiConfig.useWrlvlReq) reqs += io.dfi.wrTraining.wrlvlReq.orR
    if (config.dfiConfig.useCalvlReq) reqs += io.dfi.caTraining.calvlReq.orR
    CountOne(reqs.result()).resize(32)
  }

  // ✗ Incorrect: Direct access to potentially null signals
  // io.debug.trainingCount := CountOne(Seq(io.dfi.rdTraining.rdlvlReq.orR, ...))
}
```

#### Scenario: Conditional Signal Access
- **WHEN** accessing conditionally generated signals **THEN** check configuration flags first

#### Scenario: Configuration Flag Handling
- **WHEN** configuration flag is false **THEN** provide alternative logic or default values

#### Scenario: Unsafe Access Validation
- **WHEN** direct access to conditional signals occurs without check **THEN** it must be flagged as safety violation

#### Scenario: Debug Logic Implementation
- **WHEN** implementing debug or monitoring logic **THEN** use conditional checks for all training signals

#### Scenario: NullPointerException Prevention
- **WHEN** a NullPointerException occurs from conditional signal access **THEN** add proper condition checks

### Requirement: REQ-CS-037: Component and Area Type Definition Prohibition
New type definitions (both pure Scala types and SpinalHDL types) MUST NOT be placed within Component, Area, or Composite implementations to maintain proper encapsulation and prevent type scope pollution. Only object instantiation is allowed within these hardware constructs.

**Rationale**: Component, Area, and Composite constructs are designed for hardware description and signal logic, not for defining new types. Placing type definitions within these constructs leads to:
- Type scope pollution and namespace conflicts
- Reduced code reusability and maintainability
- Confusion between hardware description and type definition contexts
- Difficulty in type discovery and organization
- Potential compilation issues due to improper type scoping
- Deeply nested module hierarchies that are difficult to understand and maintain

**Requirements**:
- Pure Scala type definitions (classes, objects, traits, type aliases, enums) MUST NOT be defined within Component class bodies
- Pure Scala type definitions MUST NOT be defined within Area objects or implementations
- Pure Scala type definitions MUST NOT be defined within Composite constructs
- SpinalHDL type definitions (Bundle, Component, Area, etc.) MUST NOT be defined within Component and Area implementations
- Only object instantiation of existing types is allowed within Component and Area implementations
- All type definitions MUST be placed at package level, in standalone objects, or in dedicated type definition files
- SpinalEnum objects MUST be defined at package level or in dedicated enumeration objects
- Case classes and Bundle definitions MUST be defined outside Component and Area scopes
- Type definitions SHOULD be organized in logically grouped files or objects

**Valid Examples**:
```scala
// ✓ Correct: Type definitions at package level
object UartCtrlTxState extends SpinalEnum {
  val IDLE, START, DATA, PARITY, STOP = newElement()
}

case class UartConfig(dataWidth: Int, stopBits: Int)

class UartCtrlTx(g: UartCtrlGenerics) extends Component {
  val io = new Bundle {
    val write = slave Stream(Bits(g.dataWidthMax bit))
    val txd = out Bool()
  }
  
  // ✓ Correct: Object instantiation only
  val stateMachine = new Area {
    val state = RegInit(UartCtrlTxState.IDLE)
    // Hardware implementation
  }
}
```

**Invalid Examples**:
```scala
class UartCtrlTx(g: UartCtrlGenerics) extends Component {
  // ✗ Incorrect: Pure Scala object defined within Component
  object Constants {
    val TIMEOUT_VALUE = 1000
  }
  
  // ✗ Incorrect: SpinalHDL type definition within Component
  class InternalComponent extends Component {
    val io = new Bundle {
      val input = in Bool()
      val output = out Bool()
    }
    // Implementation
  }
  
  val io = new Bundle {
    val write = slave Stream(Bits(g.dataWidthMax bit))
    val txd = out Bool()
  }
  
  val processingArea = new Area {
    // ✗ Incorrect: Pure Scala case class defined within Area
    case class InternalConfig(timeout: Int, retryCount: Int)
    
    // ✗ Incorrect: SpinalHDL type definition within Area
    class InternalArea extends Area {
      val signal = Bool()
    }
    
    // ✗ Incorrect: Pure Scala trait defined within Area
    trait ProcessingStrategy {
      def process(data: Bits): Bits
    }
  }
}
```

#### Scenario: Component Type Definition Validation
- **WHEN** defining a Component **THEN** ensure no type definitions (Scala or SpinalHDL) are placed within the Component body

#### Scenario: Area Type Definition Validation
- **WHEN** creating an Area object **THEN** ensure no type definitions (Scala or SpinalHDL) are placed within the Area implementation

#### Scenario: Object Instantiation Only
- **WHEN** working within Component or Area **THEN** only instantiate objects of existing types, do not define new types

#### Scenario: Pure Scala Type Definition Placement
- **WHEN** defining pure Scala types (classes, objects, traits, type aliases, enums) **THEN** place them at package level or in dedicated type definition files

#### Scenario: SpinalHDL Type Definition Placement
- **WHEN** defining SpinalHDL types (Bundle, Component, Area) **THEN** place them at package level or in dedicated type definition files

#### Scenario: Type Definition Organization
- **WHEN** organizing type definitions **THEN** group related types in logically organized files or objects

#### Scenario: Type Scope Pollution Prevention
- **WHEN** type definitions are found within hardware constructs **THEN** move them to appropriate package-level locations
