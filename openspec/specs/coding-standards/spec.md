# Coding Standards Specification

## Purpose
This specification defines the global coding standards for SpinalHDL library development, ensuring consistent, maintainable, and hardware-correct code generation. It provides comprehensive guidelines for hardware design using SpinalHDL, covering data types, component design, clock domain management, testing practices, and optimization techniques.

## Requirements

#### REQ-CS-001: Component IO Bundle Access and Naming Standards
All Component class input/output signals MUST be accessed through the `io` named Bundle, and internal signals MUST be properly encapsulated. Area objects MUST NOT define Bundles named "io"; instead, use more descriptive names that clearly indicate the Bundle's purpose and context.

**Rationale**: SpinalHDL Components encapsulate hardware modules, and all external interfaces must go through the dedicated `io` Bundle to maintain proper encapsulation and prevent direct signal access. Area objects provide encapsulation for related logic and signals. Using generic names like "io" reduces code readability and makes it harder to understand the purpose of different Bundles within an Area. Descriptive naming improves maintainability and makes the design intent clearer. Additionally, proper signal assignment completeness ensures all hardware signals have defined connections.

**Requirements**:
- Component classes MUST define a `val io = new Bundle { ... }` field
- All input/output signals MUST be defined within the `io` Bundle
- External objects MUST NOT directly access internal Component signals
- Internal signals MAY be accessed within the Component for logic implementation
- Area objects MUST NOT define any Bundle with the name "io"
- Bundle names MUST be descriptive and indicate their specific purpose
- Bundle names SHOULD reflect the signals they contain or their functional role
- Avoid generic names that don't provide context about the Bundle's usage
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

**Scenarios**:
- **WHEN** defining a Component **THEN** all IO must go through the `io` Bundle and internal signals are properly encapsulated
- **WHEN** defining Bundles within Area objects **THEN** use descriptive names instead of "io"
- **WHEN** naming Bundles in Area objects **THEN** choose names that reflect their purpose and content
- **WHEN** using generic names like "io" in Area objects **THEN** it must be flagged as naming violation
- **WHEN** designing Area objects **THEN** ensure Bundle names enhance code readability and maintainability
- **WHEN** creating hardware signals in transformations **THEN** all Bundle fields must be explicitly assigned using `assignUnassignedByName()`
- **WHEN** assigning Bundle fields **THEN** use direct object access instead of method chaining
- **WHEN** external code accesses Component internal signals **THEN** it must be flagged as encapsulation violation
- **WHEN** defining an AXI4 interface **THEN** the configuration and bundle must follow standards
- **WHEN** a Bundle contains Scala types **THEN** compilation must fail with clear error
- **WHEN** a configuration class contains hardware types **THEN** it should be flagged as design error

#### REQ-CS-008: Bundle and Interface Direction Management
Bundle inheritance and IMasterSlave interface implementation MUST follow standardized patterns for signal direction management to ensure flexibility and prevent conflicts.

**Rationale**: When creating new interface types through Bundle inheritance or implementing IMasterSlave interfaces, signal directions should not be specified in signal definitions to avoid unnecessary constraints and potential interface conflicts. Directions must be uniformly managed through the IMasterSlave trait's asMaster() method to maintain interface flexibility and standardization.

**Requirements**:
- When inheriting from Bundle to create new interface types, signal directions MUST NOT be specified in signal definitions
- When implementing IMasterSlave interfaces, signal directions MUST be explicitly specified only in the asMaster function
- In other functions or contexts, signal directions MUST NOT be given
- Directions MUST be managed uniformly through the IMasterSlave trait's asMaster() method
- This prevents unnecessary constraints, potential interface conflicts, and ensures consistent interface behavior

**Scenarios**:
- **WHEN** creating new interface types through Bundle inheritance **THEN** signal directions must not be specified in signal definitions and should be managed via IMasterSlave.asMaster()
- **WHEN** implementing IMasterSlave interface **THEN** signal directions must be specified only in asMaster function
- **WHEN** signal directions are specified in Bundle inheritance **THEN** it must be flagged as design error
- **WHEN** signal directions are specified outside asMaster function **THEN** it must be flagged as implementation error

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

**Scenarios**:
- **WHEN** declaring single-bit logical signals **THEN** use `Bool()` type with `True`/`False` constants and edge detection methods
- **WHEN** working with raw bit data **THEN** use `Bits(n bits)` with binary/hexadecimal literals and bit manipulation operations
- **WHEN** performing arithmetic operations **THEN** use `UInt` for unsigned and `SInt` for signed arithmetic with proper type constants
- **WHEN** using Bool types for single-bit signals **THEN** proper initialization and edge detection must be used

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

**Scenarios**:
- **WHEN** creating sequential logic elements **THEN** use appropriate register types with proper initialization
- **WHEN** implementing memory elements **THEN** use `Mem()` for RAM and ROM with proper read/write interfaces

#### REQ-CS-004: Clock Domain Management Principles
Multi-clock domain designs MUST use proper clock domain crossing and synchronization techniques.

**Rationale**: Proper clock domain management prevents metastability and timing violations in designs with multiple clock domains.

**Requirements**:
- Use `ClockDomain()` to define clock domains with proper configuration
- Use `ClockingArea` to encapsulate logic within specific clock domains
- Use `BufferCC` for proper clock domain crossing synchronization
- Avoid direct signal connections between different clock domains
- Use appropriate reset synchronization for multi-clock designs

**Scenarios**:
- **WHEN** designing with single clock domain **THEN** use default clock domain with proper reset handling
- **WHEN** designing with multiple clock domains **THEN** use proper clock domain crossing synchronization with `BufferCC`

#### REQ-CS-005: Advanced Design Patterns
Complex designs MUST use appropriate advanced patterns for state machines and pipelining.

**Rationale**: Advanced design patterns improve code readability, maintainability, and performance for complex hardware implementations.

**Requirements**:
- Use `StateMachine` class for complex state machines with clear state transitions
- Use pipeline stages with `RegNext` for improved throughput
- Use balanced trees for reduction operations in performance-critical paths
- Implement resource sharing for common operations to optimize area
- Consider timing constraints during pipeline design

**Scenarios**:
- **WHEN** implementing finite state machines **THEN** use `StateMachine` class with `whenIsActive` and `goto` methods
- **WHEN** designing high-throughput circuits **THEN** use multiple pipeline stages with proper register balancing

#### REQ-CS-006: Debugging and Optimization Guidelines
Hardware designs MUST include proper verification constructs and optimization techniques.

**Rationale**: Design verification prevents common hardware errors and optimization techniques improve circuit performance and resource utilization.

**Requirements**:
- Use `assert()` statements for design constraints and invariants
- Use `report()` for debugging information during elaboration
- Enable design checks for combinatorial loops and latch detection
- Use conditional compilation for debug features in production code
- Implement performance optimization techniques for critical paths

**Scenarios**:
- **WHEN** implementing hardware components **THEN** include assertions for critical design constraints
- **WHEN** optimizing performance-critical circuits **THEN** use pipelining, resource sharing, and balanced operations

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

**Scenarios**:
- **WHEN** creating test classes **THEN** follow naming conventions and place in appropriate directory structure
- **WHEN** assigning signals during simulation **THEN** use explicit indexing for collections and proper data types
- **WHEN** executing tests **THEN** use standardized sbt commands with proper class names
- **WHEN** creating test suites **THEN** use random data generation with controlled seeds
- **WHEN** implementing test suites **THEN** achieve comprehensive coverage of design functionality
- **WHEN** defining a DUT for testing **THEN** use a Component class with io Bundle and compile via SimConfig
- **WHEN** implementing test logic **THEN** use fork for concurrency, waitSampling for timing control, and assertions for validation
- **WHEN** running simulations **THEN** support Verilator, GHDL, IVerilog with appropriate SimConfig settings
- **WHEN** testing multi-clock designs **THEN** use forkStimulus and ClockDomain for proper timing
- **WHEN** validating outputs **THEN** use assert or shouldBe with descriptive messages

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

**Scenarios**:
- **WHEN** implementing data processing components **THEN** Stream infrastructure should be used for flow control with complete signal assignment
- **WHEN** using complex state machines for data flow **THEN** it should be flagged as potential Stream candidate

#### REQ-CS-027: Hardware Description Syntax Purity
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

**Scenarios**:
- **WHEN** implementing conditional hardware logic **THEN** use `when() {} otherwise {}` statements
- **WHEN** using conditional assignments **THEN** use SpinalHDL mux operators like `? |` for hardware signals
- **WHEN** using Scala `? :` with hardware signals **THEN** it must be flagged as type error
- **WHEN** using Scala `if` or other runtime syntax with hardware signals as condition **THEN** it should be flagged as hardware description violation

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

**Scenarios**:
- **WHEN** assigning boolean signals in tests **THEN** use `#=` operator with `Boolean` values and explicit indexing
- **WHEN** assigning integer or bits signals in tests **THEN** use `#=` operator with proper data types and explicit indexing
- **WHEN** assigning signals to collections in tests **THEN** use `foreach(_ #= value)` patterns for Vec, List, and similar container types' assignment.
- **WHEN** using `:=` in doSim blocks **THEN** it must be flagged as compilation error since `:=` is for hardware logic

#### REQ-CS-028: Signal Connection Standards
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

**Scenarios**:
- **WHEN** connecting signals within the same Component or Bundle initialization **THEN** use "<>" operator for automatic connection
- **WHEN** connecting signals between parent and child modules **THEN** use explicit assignment operators like ":=" and MUST NOT use "<>" operator
- **WHEN** connecting signals between different Components **THEN** use explicit assignment operators like ":=" to maintain design clarity
- **WHEN** using "<>" operator across Components **THEN** it must be flagged as connection violation
- **WHEN** using "<>" operator between parent and child modules **THEN** it must be flagged as connection violation
- **WHEN** designing hierarchical components **THEN** ensure connection patterns are consistent and explicit

#### REQ-CS-029: IMasterSlave Interface Connection Functions
IMasterSlave interfaces SHOULD implement << and >> functions for standardized bidirectional connections with proper signal direction handling.

**Rationale**: Connection functions provide symmetric interface operations, enabling flexible signal routing while ensuring type safety and consistent connection patterns across interface types.

**Requirements**:
- IMasterSlave interfaces SHOULD implement << function for forward connections with proper signal direction handling
- Interfaces implementing << MUST also implement >> function that calls << with reversed arguments for symmetry
- Both functions MUST ensure type safety and prevent invalid signal connections based on master/slave configuration
- Functions MUST support interface composition and chaining for flexible signal routing
- Connect signals defined with << and >> functions with those functions as possible

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

  io.output << io.input  // Forward connection
  io.input >> io.output  // Reverse connection
}
```

**Scenarios**:
- **WHEN** designing IMasterSlave interfaces **THEN** implement both << and >> functions for bidirectional connections
- **WHEN** connecting interfaces **THEN** use << or >> functions for consistent, type-safe signal routing
- **WHEN** chaining interfaces **THEN** leverage function composition for flexible signal paths
- **WHEN** connecting signals defined with << and >> functions **THEN** use those functions for connections
