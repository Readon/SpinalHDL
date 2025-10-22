# Coding Standards Specification

## Overview
This specification defines the global coding standards for SpinalHDL library development, ensuring consistent, maintainable, and hardware-correct code generation.

## Requirements

### Component Encapsulation Standards

#### REQ-CS-009: Component IO Bundle Access
All Component class input/output signals MUST be accessed through the `io` named Bundle.

**Rationale**: SpinalHDL Components encapsulate hardware modules, and all external interfaces must go through the dedicated `io` Bundle to maintain proper encapsulation and prevent direct signal access.

**Requirements**:
- Component classes MUST define a `val io = new Bundle { ... }` field
- All input/output signals MUST be defined within the `io` Bundle
- External objects MUST NOT directly access internal Component signals
- Internal signals MAY be accessed within the Component for logic implementation

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
}
```

#### REQ-CS-010: Component Signal Encapsulation
Component internal signals MUST NOT be directly accessible from external objects.

**Rationale**: Components represent hardware modules with well-defined interfaces. Direct access to internal signals breaks encapsulation and can lead to incorrect hardware generation.

**Requirements**:
- Internal signals (regs, wires, etc.) MUST be private to the Component
- External access MUST go through the `io` Bundle only
- Use `io` signals for all inter-component communication

**Invalid Example**:
```scala
case class BadComponent() extends Component {
  val io = new Bundle {
    val output = out UInt(32 bits)
  }

  val internalReg = Reg(UInt(32 bits)) init(0)
}

// ✗ 错误：外部直接访问内部信号
val comp = new BadComponent()
comp.internalReg := someValue  // 破坏封装
```

#### REQ-CS-019: Area and Component Usage Rules
Area classes and their subtypes MUST NOT be accessed through `io` named Bundle objects, while Component subclasses MUST be accessed through `io` Bundle.

**Rationale**: Area is used for logical grouping and organization, not for external interfaces. Component represents hardware modules that require well-defined external interfaces.

**Requirements**:
- Area classes (including ClockingArea, ResetArea, etc.) MUST NOT define or use `io` Bundle
- Only Component subclasses MUST define `val io = new Bundle { ... }`
- Area is used for code organization and naming management
- Composite is used for creating naming namespaces for existing objects

**Area vs Composite vs Component Usage**:
- **Area**: Logical grouping, no external IO, used for organization
- **Composite**: Naming namespace creation, inherits Area functionality
- **Component**: Hardware module with external IO through `io` Bundle

**Common Methods**:
- **Area**: `setCompositeName()`, `rework()`, `childNamePriority`
- **Composite**: Inherits Area methods, focuses on naming
- **Component**: `io` (Bundle definition), `setDefinitionName()`, `setCompositeName()`

**When to Define Class Methods**:
- Extract reusable logic into methods
- Encapsulate complex combinatorial logic
- Create parameterized logic generation
- Avoid cluttering constructor with complex code

**Example**:
```scala
// ✓ Area for logical grouping
val logicGroup = new Area {
  val counter = Reg(UInt(8 bits)) init(0)
  counter := counter + 1
}

// ✓ Composite for naming
val namedLogic = new Composite(someSignal, "logic") {
  // logic implementation
}

// ✓ Component with io Bundle
case class MyComponent() extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  // Internal logic
  io.output := io.input + 1
}
```

### Type System Rules

#### REQ-CS-001: Configuration Class Standards
Configuration classes MUST use Scala native types and follow naming conventions.

**Rationale**: Configuration classes are used for parameter passing and do not participate in hardware generation.

**Requirements**:
- Use Scala native types: `Int`, `Boolean`, `String`, etc.
- End with `Config` suffix
- Do not extend `Bundle` or use hardware types

**Example**:
```scala
case class Axi4Config(
  addressWidth: Int,
  dataWidth: Int,
  idWidth: Int = 4
)
```

#### REQ-CS-002: Bundle Hardware Type Enforcement
All Bundle fields MUST be SpinalHDL hardware types.

**Rationale**: Bundles define hardware interfaces and must map to synthesizable signals.

**Requirements**:
- Only use hardware types: `Bool`, `UInt`, `SInt`, `Bits`, `Vec`, `Bundle`
- Prohibit Scala types: `Int`, `String`, `Boolean`
- Use configuration parameters for sizing

**Example**:
```scala
case class Axi4(config: Axi4Config) extends Bundle with IMasterSlave {
  val aw = Stream(Axi4Aw(config))  // ✓ Hardware type
  val w  = Stream(Axi4W(config))   // ✓ Hardware type
}
```

#### REQ-CS-003: Interface Definition Standards
Hardware interfaces MUST use proper direction specifiers and type safety.

**Requirements**:
- Use `in()`, `out()`, `slave()`, `master()` for signal directions
- All signals must be hardware types
- Parameterize using configuration classes

**Example**:
```scala
case class Axi4Aw(config: Axi4Config) extends Bundle {
  val addr = UInt(config.addressWidth bits)  // ✓ Hardware type
  val id   = UInt(config.idWidth bits)        // ✓ Hardware type
}
```

### Code Structure Patterns

#### REQ-CS-004: Standard Configuration Pattern
Configuration classes MUST follow the standard pattern for parameter management.

**Pattern**:
```scala
case class XxxConfig(
  param1: Int,
  param2: Boolean = true,
  param3: String = "default"
)
```

#### REQ-CS-005: Standard Bundle Pattern
Bundles MUST follow the standard pattern for hardware interface definition.

**Pattern**:
```scala
case class Xxx(config: XxxConfig) extends Bundle with IMasterSlave {
  val signal1 = Bool()
  val signal2 = UInt(config.param1 bits)
  val signal3 = config.param2 generate Bool()
}
```

#### REQ-CS-006: Standard Interface Pattern
Complex interfaces MUST follow the standard pattern for modular design.

**Pattern**:
```scala
case class XxxInterface(config: XxxConfig) extends Bundle with IMasterSlave {
  val cmd = slave Stream(XxxCmd(config))
  val rsp = master Stream(XxxRsp(config))
}
```

#### REQ-CS-011: Standard Component Pattern
Components MUST follow the standard pattern for encapsulation and IO definition.

**Pattern**:
```scala
case class XxxComponent(config: XxxConfig) extends Component {
  val io = new Bundle {
    // All external IO signals here
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }

  // Internal logic implementation
  val internalReg = Reg(UInt(32 bits)) init(0)
  io.output := internalReg
  internalReg := io.input
}
```

#### REQ-CS-013: Stream-Based Design Pattern
Data processing components SHOULD use Stream infrastructure for flow control and backpressure.

**Rationale**: Stream-based design improves code readability, maintainability, and reusability by replacing complex state machines with declarative flow control.

**Requirements**:
- Prefer `spinal.lib.Stream` for data flow operations
- Use `StreamTransactionExtender` instead of state machines for transaction handling
- Use `StreamArbiter` for multi-stream arbitration
- Use `StreamDemux` for stream separation
- Prefer `translateWith`, `translateFrom`, `translateInto` for stream transformations

**Example**:
```scala
case class StreamProcessor(config: Config) extends Component {
  val io = new Bundle {
    val input = slave Stream(DataBundle())
    val output = master Stream(DataBundle())
  }

  // ✓ Preferred: Stream-based processing
  io.output << io.input.translateWith {
    val processed = cloneOf(io.input.payload)
    processed.data := io.input.payload.data + 1
    // assignUnassignedByName should be called after all custom assignments
    processed.assignUnassignedByName(io.input.payload)
    processed
  }
}
```

#### REQ-CS-014: Configuration Class Standards
Configuration classes MUST use Scala native types and follow naming conventions.

**Requirements**:
- Use Scala native types: `Int`, `Boolean`, `String`, etc.
- End with `Config` suffix
- Do not extend `Bundle` or use hardware types
- Use case classes for configuration

**Example**:
```scala
case class Axi4Config(
  addressWidth: Int,
  dataWidth: Int,
  idWidth: Int = 4
)
```

#### REQ-CS-015: Bundle Hardware Type Enforcement
All Bundle fields MUST be SpinalHDL hardware types.

**Requirements**:
- Only use hardware types: `Bool`, `UInt`, `SInt`, `Bits`, `Vec`, `Bundle`
- Prohibit Scala types: `Int`, `String`, `Boolean`
- Use configuration parameters for sizing

**Invalid Example**:
```scala
case class InvalidBundle() extends Bundle {
  val scalaInt = 42        // ✗ Scala type in Bundle
  val scalaBool = true     // ✗ Scala type in Bundle
}
```

### Validation and Tooling Rules

#### REQ-CS-007: Bundle Type Validation
Static analysis tools MUST detect and reject Scala types in Bundles.

**Requirements**:
- Compile-time checking for Bundle field types
- Error reporting for invalid type usage
- Integration with build process

#### REQ-CS-008: Configuration Type Validation
Configuration classes MUST be validated for proper Scala type usage.

**Requirements**:
- Ensure no hardware types in configuration classes
- Validate parameter ranges and constraints
- Documentation of parameter meanings

#### REQ-CS-012: Component Encapsulation Validation
Component encapsulation MUST be validated to ensure proper IO Bundle usage.

**Requirements**:
- Verify all external signals go through `io` Bundle
- Detect direct access to internal Component signals
- Ensure Component interfaces are well-defined

#### REQ-CS-016: Stream Usage Validation
Stream-based components MUST be validated for proper flow control implementation.

**Requirements**:
- Verify Stream interfaces use proper ready/valid handshake
- Check for proper backpressure handling
- Validate Stream transformations maintain data integrity
- Ensure no blocking operations in Stream pipelines

#### REQ-CS-017: Bundle Signal Assignment Completeness
When creating hardware signals in Stream transformations or data processing, all Bundle fields MUST be explicitly assigned or connected.

**Rationale**: SpinalHDL requires all hardware signals to have defined connections to avoid synthesis issues and ensure proper hardware generation.

**Requirements**:
- Use `assignUnassignedByName()` or `assignSomeByName()` to connect base fields before custom assignments
- Ensure all Bundle fields have explicit connections
- Avoid partial assignments that leave fields unconnected
- Validate signal completeness in code reviews

**Example**:
```scala
// ✓ Correct: Complete signal assignment
val processed = cloneOf(io.input.payload)
processed.data := io.input.payload.data + 1        // 自定义处理
processed.assignUnassignedByName(io.input.payload)  // assignUnassignedByName 用于连接所有未连接的字段,需在所有自定义赋值之后调用
processed

// ✗ Incorrect: Partial assignment may leave fields unconnected
val processed = cloneOf(io.input.payload)
processed.data := io.input.payload.data + 1        // 其他字段未连接
processed
```

#### REQ-CS-018: Direct Object Access for Bundle/HardType Assignment
Bundle/HardType field assignments SHOULD use direct object access instead of method chaining.

**Rationale**: Method chaining with `.setDefaults()` and subsequent field assignments creates temporary objects and reduces code readability. Direct object access is clearer and more efficient.

**Requirements**:
- Prefer direct field access: `io.debug.field <> source`
- Avoid method chaining: `io.debug := BundleType().setDefaults().field1 <> src1.field2 <> src2`
- Use explicit assignments for each field
- Maintain clear assignment relationships

**Example**:
```scala
// ✓ Preferred: Direct object access
io.debug.dfiAdapter <> dfiAdapter.io.debug
io.debug.standardAdapter <> standardAdapter.io.debug
io.debug.timingGenerator <> timingGenerator.io.debug

// ✗ Avoid: Method chaining
io.debug := DfiDdrPhyDebug()
  .setDefaults()
  .dfiAdapter <> dfiAdapter.io.debug
  .standardAdapter <> standardAdapter.io.debug
  .timingGenerator <> timingGenerator.io.debug
```

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
    processed.assignUnassignedByName(io.input.payload)  // assignUnassignedByName 用于连接所有未连接的字段,需在所有自定义赋值之后调用
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

## Testing Standards

### Test Code Structure Rules

#### REQ-CS-020: Test Class Naming Convention
Test classes MUST follow the naming convention ending with "Tester" or "Test".

**Rationale**: Clear identification of test classes improves code organization and test discovery.

**Requirements**:
- Test classes MUST end with "Tester" for simulation-based tests
- Test classes MUST end with "Test" for unit tests
- Test classes SHOULD be placed in `tester/src/test/scala/` directory structure
- Test class names SHOULD reflect the component being tested

**Example**:
```scala
// ✓ Wrong: Tester should be a normal class but component.
class StreamTester extends Component {
  // Component definition
}

class StreamTesterGhdlBoot extends SpinalTesterGhdlBase {
  override def getName: String = "StreamTester"
  override def createToplevel: Component = new StreamTester
}

// ✓ Correct: Unit test (Preferred)
class MiscTester extends SpinalAnyFunSuite {
  test("napot") {
    // Test implementation
  }
}
```

#### REQ-CS-021: Test Organization Structure
Test code MUST be organized in a hierarchical structure mirroring the main code.

**Rationale**: Consistent organization improves test maintainability and discoverability.

**Requirements**:
- Test files MUST be placed in `tester/src/test/scala/spinal/lib/` directory
- Directory structure MUST mirror `lib/src/main/scala/spinal/lib/` structure
- Test files SHOULD be named after the component they test
- Related test utilities MAY be placed in appropriate subdirectories

**Example**:
```
lib/src/main/scala/spinal/lib/Stream.scala
tester/src/test/scala/spinal/lib/StreamTester.scala

lib/src/main/scala/spinal/lib/bus/amba4/axi/Axi4Adapter.scala
tester/src/test/scala/spinal/lib/bus/amba4/axi/Axi4AdapterTester.scala
```

#### REQ-CS-022: Test Code vs Design Code Separation
Test code MUST be strictly separated from design code and follow different rules.

**Rationale**: Test code serves verification purposes and should not be constrained by hardware synthesis requirements.

**Requirements**:
- Test code MUST NOT be synthesizable hardware
- Test code MAY use Scala native types (Int, Boolean, String)
- Test code MAY contain simulation-specific constructs
- Test code MUST NOT define hardware interfaces through `io` Bundle
- Test code SHOULD focus on verification logic, not hardware generation

**Key Differences**:
- **Design Code**: Hardware types, `io` Bundle, synthesis constraints
- **Test Code**: Scala types, simulation utilities, verification logic

**Example**:
```scala
// ✗ Not preferred: Hardware component in test is only permitted while creating a fixture.
class BadTestComponent extends Component {
  val io = new Bundle {
    val input = in UInt(32 bits)
    val output = out UInt(32 bits)
  }
  // This should be design code, not test code
}
```

#### REQ-CS-023: Test Execution Commands
Test execution MUST use standardized sbt commands.

**Rationale**: Consistent test execution improves development workflow.

**Requirements**:
- All tests MUST be executed using `sbt "tester/testOnly <ClassName>"`
- `<ClassName>` MUST be the fully qualified class name
- Wildcard execution MAY use `sbt "tester/testOnly *"`
- Formal tests MAY be excluded using `sbt "tester/testOnly * -- -l spinal.tester.formal"`

**Example Commands**:
```bash
# Run specific test class
sbt "tester/testOnly spinal.lib.StreamTester"

# Run all tests
sbt "tester/testOnly *"

# Run tests excluding formal verification
sbt "tester/testOnly * -- -l spinal.tester.formal"
```

#### REQ-CS-024: Test Simulation Patterns
Simulation-based tests MUST follow established patterns for proper verification.

**Rationale**: Consistent simulation patterns ensure reliable and maintainable tests.

**Requirements**:
- Tests MUST use `SpinalAnyFunSuite` for unit tests
- Tests MUST use `SpinalTesterGhdlBase` or `SpinalTesterCocotbBase` for simulation tests
- Simulation tests MUST implement `getName` and `createToplevel` methods
- Tests SHOULD use `SimConfig.compile(...).doSim(...)` pattern
- Test stimulus MUST be properly initialized
- Assertions MUST verify expected behavior

**Example**:
```scala
class MiscTester extends SpinalAnyFunSuite {
  import spinal.core.sim._
  import spinal.core._
  import spinal.lib._

  test("napot") {
    SimConfig.doSim(new Component {
      val value = in Bits(8 bits)
      val napot = out(Napot(value))
    }) { dut =>
      for (v <- 0 until 256) {
        dut.value #= v
        sleep(10)
        val dt = dut.napot.toInt
        // Assertions here
      }
    }
  }
}
```

#### REQ-CS-025: Test Collection Signal Assignment Clarity
Test signal assignments to collections MUST be clear and explicit to improve readability and maintainability.

**Rationale**: Unclear collection signal assignments can lead to hard-to-debug test failures and reduced code maintainability.

**Requirements**:
- **PROHIBITED**: Using `foreach(_ #= value)` on `dut.io` signals to modify collection values
- Prefer explicit indexing for all collections (e.g., `signal(index) #= value`)
- Use descriptive comments to explain the purpose of collection assignments
- Group related collection assignments logically
- Consider using explicit loops for complex collection initialization patterns

**Invalid Example**:
```scala
// ✗ PROHIBITED: using foreach on dut.io signals
dut.io.dfi.rdTraining.rdlvlReq.foreach(_ #= false)
dut.io.dfi.rdTraining.rdlvlGateReq.foreach(_ #= false)
dut.io.dfi.wrTraining.wrlvlReq.foreach(_ #= false)
dut.io.dfi.caTraining.calvlReq.foreach(_ #= false)
dut.io.dfi.read.rd.foreach(_.rddataValid #= false)
```

**Valid Examples**:
```scala
// ✓ Clear: explicit indexing for all collections
dut.io.dfi.rdTraining.rdlvlReq(0) #= false      // Read level training request for slice 0
dut.io.dfi.rdTraining.rdlvlGateReq(0) #= false  // Read level gate training request for slice 0
dut.io.dfi.wrTraining.wrlvlReq(0) #= false      // Write level training request for slice 0
dut.io.dfi.caTraining.calvlReq(0) #= false      // CA level training request for slice 0

// ✓ Clear: explicit loop for collection initialization
for (i <- 0 until dut.io.dfi.read.rd.length) {
  dut.io.dfi.read.rd(i).rddataValid #= false
  dut.io.dfi.read.rd(i).rddata #= 0
}

// ✓ Clear: individual assignments with comments
// Initialize training request signals
dut.io.dfi.rdTraining.rdlvlReq(0) #= false      // Read level training request
dut.io.dfi.rdTraining.rdlvlGateReq(0) #= false  // Read level gate training request
dut.io.dfi.wrTraining.wrlvlReq(0) #= false      // Write level training request
dut.io.dfi.caTraining.calvlReq(0) #= false      // CA level training request
```

#### REQ-CS-026: Test Signal Assignment Patterns
Test signal assignments during simulation MUST follow consistent patterns for different data types.

**Rationale**: Consistent assignment patterns improve test code readability and reduce errors in simulation-based testing.

**Requirements**:
- Use `#=` operator for all signal assignments in simulation
- Use appropriate data types: `Int` for integer values, `Boolean` for boolean values, `BigInt` for large integers
- Use `.randomize()` for generating random test data
- Use descriptive variable names for complex expressions
- Group related assignments and use comments to explain test phases

**Signal Assignment Patterns**:

**Boolean Signals**:
```scala
dut.io.cmd.valid #= false
dut.io.cmd.valid #= true
dut.io.flush #= false
dut.io.start #= b == 0
```

**Integer/Bits Signals**:
```scala
dut.io.cmd.data #= writeData          // Direct assignment
dut.io.cmd.kind #= false              // Boolean to integer conversion
dut.io.config.ss.activeHigh #= 0      // Numeric literal
dut.io.input.payload #= (that(i) >> shift) & 0xF  // Complex expression
dut.io.encoded #= BigInt(encoded, 2)  // BigInt for large values
```

**Random Data Generation**:
```scala
dut.io.s_axis.data.randomize()
dut.io.s_data.payload.randomize()
dut.io.i.randomize()
```

**Protocol-Specific Assignments**:
```scala
// AXI4-Stream
dut.io.s_axis.valid #= true
dut.io.s_axis.last.randomize()
dut.io.m_data.ready #= true

// SPI
dut.io.cmd.kind #= false
dut.io.cmd.data #= writeData
dut.io.config.mod #= mod.id

// Ethernet
dut.io.input.valid #= true
dut.io.input.data #= data
dut.io.input.error #= false
dut.io.input.last #= transferId == transferCount-1
```

**Initialization vs Runtime Assignments**:
```scala
// Initialization phase
dut.io.cmd.valid #= false
dut.io.flush #= false
dut.clockDomain.waitSampling()

// Runtime phase (in test loops)
dut.io.cmd.valid #= true
dut.io.cmd.data #= writeData
dut.clockDomain.waitSamplingWhere(dut.io.cmd.ready.toBoolean)
dut.io.cmd.valid #= false
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