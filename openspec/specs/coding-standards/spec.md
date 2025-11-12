# SpinalHDL Coding Standards Specification

## Purpose

This specification defines the coding standards and design patterns for SpinalHDL hardware description language development, focusing on critical hardware vs software thinking differences essential for AI-assisted development.

## Requirements

### Requirement: REQ-CS-001: Hardware vs Software Mental Model
Developers SHALL understand the fundamental differences between SpinalHDL hardware generation and traditional software execution.

#### Scenario: Generation-time vs Runtime Logic
- **WHEN** writing SpinalHDL code
- **THEN** developers SHALL understand that code generates hardware at compile-time, not execute at runtime
- **AND** SHALL use hardware thinking patterns (signals, when/otherwise) instead of software patterns (variables, if/else)

#### Scenario: Type Domain Separation
- **WHEN** designing SpinalHDL components
- **THEN** developers SHALL strictly separate Scala types (generation-time) from SpinalHDL types (hardware signals)
- **AND** SHALL NOT mix domains inappropriately

### Requirement: REQ-CS-002: Component Architecture Standards
All SpinalHDL components SHALL follow proper hardware boundary integrity standards.

#### Scenario: Constructor Parameter Restrictions
- **WHEN** defining Component constructors
- **THEN** developers SHALL only use configuration objects, generation-time values, and type parameters
- **AND** SHALL NOT include hardware signals, data signals, or command signals as constructor parameters

#### Scenario: IO Bundle Integrity
- **WHEN** creating Component interfaces
- **THEN** developers SHALL define all hardware interfaces through the `io` Bundle only
- **AND** SHALL ensure the `io` Bundle represents physical pins that synthesis tools can see

#### Scenario: Component Instantiation
- **WHEN** conditionally creating hardware components
- **THEN** developers SHALL use `generate` patterns or null checks with explicit conditional logic
- **AND** SHALL NOT wrap Components in Scala Option or Either types

### Requirement: REQ-CS-003: Data Type System Usage
Developers SHALL properly use SpinalHDL data types and understand signal creation vs aliasing.

#### Scenario: Signal Declaration and Assignment
- **WHEN** creating hardware signals
- **THEN** developers SHALL use proper SpinalHDL types (UInt, Bool, Bits) with explicit width declarations
- **AND** SHALL understand that declaration creates hardware structure while assignment creates connections

#### Scenario: Signal Alias vs New Signal Creation
- **WHEN** referencing existing signals
- **THEN** developers SHALL understand the difference between signal aliases (same hardware) and new signal creation (additional hardware)
- **AND** SHALL choose the appropriate approach based on hardware requirements

### Requirement: REQ-CS-004: Sequential Logic Design Patterns
Sequential logic SHALL follow proper register usage patterns and initialization methods.

#### Scenario: Register Self-Reference
- **WHEN** designing state-retaining logic
- **THEN** developers SHALL use register self-reference patterns for counters and state machines
- **AND** SHALL understand that self-reference creates hardware feedback paths, not software loops

#### Scenario: Register Initialization
- **WHEN** initializing registers
- **THEN** developers SHALL use appropriate methods: `init()`, `RegInit()`, or `RegNext()` with initialization
- **AND** SHALL ensure consistent usage across the design

### Requirement: REQ-CS-005: Clock Domain Management
Clock domains SHALL be treated as physical time domains with proper crossing techniques.

#### Scenario: Clock Domain Definition
- **WHEN** defining multiple clock domains
- **THEN** developers SHALL specify clock, reset, and frequency parameters for each domain
- **AND** SHALL understand that clock domains represent physical timing, not software threads

#### Scenario: Clock Domain Crossing
- **WHEN** crossing clock domains
- **THEN** developers SHALL use appropriate CDC methods (StreamCCByToggle, StreamFifoCC, StreamCCByWidth)
- **AND** SHALL choose methods based on latency, throughput, and resource requirements

### Requirement: REQ-CS-006: Signal Assignment Standards
Signal assignments SHALL properly distinguish between combinational and sequential logic.

#### Scenario: Combinational vs Sequential Assignment
- **WHEN** implementing logic
- **THEN** developers SHALL use appropriate assignment patterns for combinational (immediate) vs sequential (clocked) logic
- **AND** SHALL provide default assignments to prevent latch inference

#### Scenario: Conditional Usage
- **WHEN** implementing conditional logic
- **THEN** developers SHALL use Scala if/else for hardware generation decisions
- **AND** SHALL use SpinalHDL when/otherwise for hardware logic multiplexers

### Requirement: REQ-CS-007: Naming Conventions
All identifiers SHALL follow consistent naming conventions based on their type and purpose.

#### Scenario: Standard Naming Patterns
- **WHEN** naming elements
- **THEN** developers SHALL use PascalCase for Components, camelCase for signals, UPPER_CASE for constants
- **AND** SHALL use lowercase for package names

#### Scenario: Signal Naming Patterns
- **WHEN** naming signals
- **THEN** developers SHALL use 'N' suffix for active-low signals (not 'Neg' or 'Bar')
- **AND** SHALL choose signal names that reflect hardware purpose and behavior

### Requirement: REQ-CS-008: Numeric Literal Standards
Numeric literals with semantic meaning SHALL use named constants instead of magic numbers.

#### Scenario: Hardware Width Constants
- **WHEN** specifying signal widths
- **THEN** developers SHALL use named constants from HardwareWidths object (BYTE, WORD, DWORD)
- **AND** SHALL NOT use raw numbers like 8, 16, 32, 64

#### Scenario: Magic Number Elimination
- **WHEN** using numbers with semantic meaning
- **THEN** developers SHALL define named constants for values like timeouts, limits, and configuration parameters
- **AND** SHALL allow:
  - Special literal values (0, False) in all contexts
  - Simple math operations (+1, -1) only
  - Direct Arabic numerals for **Scala software code initialization** (Scala variables, constructor parameters, configuration values)
- **AND** SHALL prohibit direct Arabic numerals in **hardware contexts** (signal widths, memory sizes, non-zero register initialization)

### Requirement: REQ-CS-009: Simulation and Testing Patterns
Test code SHALL use proper SpinalHDL simulation assignment patterns.

#### Scenario: Simulation Signal Assignment
- **WHEN** writing testbench code
- **THEN** developers SHALL use `#=` for simulation signal assignment
- **AND** SHALL NOT use `:=` which is reserved for hardware logic only

## Examples and Guidelines

### Hardware vs Software Thinking Examples

#### Generation-Time vs Runtime Logic
```scala
// ❌ WRONG: Software thinking
def multiplexer(a: Int, b: Int, sel: Boolean): Int = if (sel) a else b

// ✅ CORRECT: Hardware thinking
val a = UInt(8 bits)
val b = UInt(8 bits)
val sel = Bool()
val result = UInt(8 bits)
when(sel) {
  result := a  // Creates 8-bit hardware multiplexer
} otherwise {
  result := b
}
```

#### Type Domain Separation
```scala
// ✅ Use Scala types for configuration (generation-time)
val dataWidth: Int = 32
val enableFeature: Boolean = true
val moduleName: String = "processor"

// ✅ Use SpinalHDL types for hardware signals
val dataBus = Bits(dataWidth bits)
val enableFlag = Bool()
val address = UInt(16 bits)

// ❌ WRONG: Mixing domains
val wrong = UInt(32)  // Scala Int for hardware width
```

**Essential Requirements for LLMs**:
1. SpinalHDL code **generates hardware**, it doesn't execute like software
2. Use explicit hardware declarations (`Reg`, `UInt`, etc.)
3. Understand generation-time vs runtime execution
4. Type domains must be strictly separated

### Component Architecture Examples

#### Constructor Parameter Rules
**✅ Allowed Constructor Parameters:**
- Configuration objects: `config: DfiConfig`
- Generation-time values: `width: Int`, `enableFeature: Boolean`
- Type parameters: `T <: Data`

**❌ Prohibited Constructor Parameters:**
- Hardware signals: `signal: Bool`, `data: Bits`, `cmd: SpinalEnumCraft[MyEnum.type]`

#### Complete Component Example
```scala
// ✅ CORRECT: Complete component structure
class MemoryController(config: MemoryConfig) extends Component {
  val io = new Bundle {
    val input = in(UInt(config.dataWidth bits))
    val output = out(UInt(config.dataWidth bits))
    val enable = in(Bool())
  }

  // Internal signals (private to hardware)
  val internalRegister = Reg(UInt(config.dataWidth bits)) init(0)

  // Hardware connections
  when(io.enable) {
    internalRegister := io.input
  }
  io.output := internalRegister
}

// ❌ WRONG: Hardware signals in constructor
class BadController(config: MemoryConfig, enable: Bool) extends Component
```

**Critical Rule**: Constructor parameter violations are the most serious errors and must be checked first.

#### Component Validation Order

**Always validate components in this priority:**
1. **Constructor Parameters** (Highest Priority) - No hardware signals
2. **io Bundle Definition** - All signals through io Bundle
3. **Numeric Literals** - Use named constants
4. **Naming Conventions** - Proper casing and formats

#### Component Instantiation Patterns
```scala
// ✅ Correct: Conditional hardware creation
val optionalModule = condition generate new MyComponent()

// ✅ Correct: Null check pattern
val conditionalModule = if (condition) {
  new MyComponent()
} else null

// Usage must include null check
if (condition && conditionalModule != null) {
  conditionalModule.io.input := signal
}

// ❌ WRONG: Scala Option wrapping Component
val optionalModule = if (condition) {
  Some(new MyComponent())  // Error: Option + Component type confusion
} else None
```

### Data Type System Examples

**⚠️ Common LLM Error**: Confusing signal references with new hardware creation.

#### Signal Alias vs New Signal Creation
```scala
// Given existing signal
val originalSignal = UInt(8 bits)
originalSignal := io.dataIn

// ✅ Signal Alias (same hardware, different name)
val aliasSignal = originalSignal
// NO additional hardware created - just another name

// ✅ New Signal Creation (new hardware)
val newSignal = UInt(8 bits)
newSignal := originalSignal
// CREATES new 8-bit signal + wiring

// ✅ Buffered Signal (new hardware with timing)
val bufferedSignal = CombInit(originalSignal)  // New buffer
val registeredSignal = RegNext(originalSignal) // New register
```

#### Signal Declaration vs Assignment
```scala
// Declaration creates hardware structure
val counter = UInt(HardwareWidths.WORD bits)     // Creates 16-bit wire
val enable = Bool()             // Creates 1-bit wire
val dataBus = Bits(HardwareWidths.DWORD bits)   // Creates 32-bit bus

// Assignment creates hardware connections
counter := counter + 1          // Creates adder + register feedback
enable := io.start              // Creates wire connection
dataBus := io.dataIn            // Creates bus connection
```

### Sequential Logic Examples

#### Register Self-Reference Patterns
```scala
// ✅ Correct: Register feedback for state retention
val counter = Reg(UInt(8 bits)) init(0)
when(enable) {
  counter := counter + 1  // Self-reference increment
} otherwise {
  counter := counter      // Self-reference hold value
}

// ✅ Correct: Register toggle pattern
val toggleReg = RegInit(False)
when(signal) {
  toggleReg := !toggleReg  // Self-reference toggle
}
```

**Key Hardware Concept**: Register self-reference creates feedback paths in hardware, not software loops.

#### Register Initialization Methods
```scala
// Method 1: init() method
val counter = Reg(UInt(8 bits)) init(0)

// Method 2: RegInit() function
val flag = RegInit(False)

// Method 3: RegNext with initialization
val delayed = RegNext(True, init = False)
```

### Clock Domain Examples

**⚠️ LLM Misunderstanding**: Clock domains are not software threads - they represent physical timing domains in hardware.

#### Multiple Clock Domains
```scala
// ✅ Multiple clock domains in same design
val fastDomain = ClockDomain(
  clock = io.fastClk,
  reset = io.fastReset,
  frequency = FixedFrequency(200 MHz)
)

val slowDomain = ClockDomain(
  clock = io.slowClk,
  reset = io.slowReset,
  frequency = FixedFrequency(50 MHz)
)
```

#### CDC Methods Selection
| CDC Method | Use Case | Latency | Resources |
|------------|----------|---------|-----------|
| `StreamCCByToggle` | Low latency, bursty | Low | Minimal |
| `StreamFifoCC` | High throughput | Variable | Moderate |
| `StreamCCByWidth` | Small data | Low | Minimal |

### Signal Assignment Examples

#### Combinational vs Sequential Logic
```scala
// ✅ Combinational logic (immediate)
val combSignal = UInt(8 bits)
combSignal := io.input + 1          // Immediate addition

// ✅ Sequential logic (clocked)
val seqSignal = Reg(UInt(8 bits)) init(0)
seqSignal := seqSignal + 1          // Clocked update

// ✅ Default assignment prevents latches
val nextstate = UInt(4 bits)
nextstate := 0                      // Default assignment
when(condition) {
  nextstate := currentState + 1     // Conditional update
}
```

#### Conditional Logic Patterns
```scala
// ✅ Scala if/else for hardware generation
val dataWidth = if (config.useWideBus) 64 else 32
val dataBus = Bits(dataWidth bits)

// ✅ SpinalHDL when/otherwise for hardware logic
val result = UInt(8 bits)
when(sel) {
  result := dataA                    // Creates multiplexer
} otherwise {
  result := dataB
}

// ❌ WRONG: Using when for generation-time decisions
when(config.useFeature) {           // Error: config is Scala value
  val module = new MyModule()       // Should use generate
}
```

### Naming Convention Examples

#### Signal Naming Patterns
```scala
// ✅ Active-low signals (N suffix = active-low)
val chipSelectN = Bool()     // Not chipSelectNeg
val writeEnableN = Bool()    // Not writeEnableBar
val resetN = Bool()          // Not resetBar

// ✅ Signal naming reflecting hardware purpose
val dataReady = Bool()       // Status flag
val dataValid = Bool()       // Validity indicator
val addressBus = UInt(16 bits) // Bus signal
```

#### Naming Convention Table
| Type | Format | Example |
|------|--------|---------|
| Components | PascalCase | `MemoryController`, `DataProcessor` |
| Signals | camelCase | `dataValid`, `addressCounter` |
| Constants | UPPER_CASE | `MAX_WIDTH`, `DEFAULT_VALUE` |
| Packages | lowercase | `memory`, `processing` |

### Numeric Literal Examples

#### Hardware Width Constants
```scala
// ✅ CORRECT: Use named constants
object HardwareWidths {
  val BYTE = 8
  val WORD = 16
  val DWORD = 32
}

object TimingConstants {
  val TIMEOUT_CYCLES = 150000
}

val dataBus = UInt(HardwareWidths.WORD bits)
val timeout = Reg(UInt(32 bits)) init(TimingConstants.TIMEOUT_CYCLES)

// ❌ WRONG: Magic numbers
val dataBus = UInt(16 bits)  // What does 16 mean?
val timeout = Reg(UInt(32 bits)) init(150000)  // What is 150000?

// ✅ ALLOWED: Direct Arabic numerals for Scala initialization
class Config {
  val timeout = 150000  // Scala software initialization
  val retries = 3
}
val counter = 0  // Special literal in all contexts
```

#### Allowed Numeric Usage
| Category | Standard | Examples |
|----------|----------|----------|
| **Hardware Widths** | PROHIBITED | `8`, `16`, `32`, `64` → Use `HardwareWidths.BYTE` |
| **Magic Numbers** | PROHIBITED | `150000`, `42`, `256` → Use named constants |
| **Scala Initialization** | ALLOWED | `val timeout = 150000` (Scala software code) |
| **Math Operations** | ALLOWED | `+ 1`, `- 1` (only simple operations) |
| **Special Literals** | ALLOWED | `0`, `False` (all contexts) |

### Testing Examples

#### Simulation Signal Assignment
```scala
// ✅ Correct: Use #= for simulation
dut.io.dataIn #= 0x55
dut.io.enable #= true

// ❌ WRONG: Use := in simulation
dut.io.dataIn := 0x55  // Error: := is for hardware logic only
```

## Critical Learning Points for AI Models

### Most Common SpinalHDL Mistakes

1. **Constructor Hardware Signals**: Placing hardware signals in constructor parameters
2. **Type Domain Confusion**: Using Scala types where SpinalHDL types needed
3. **Signal Alias vs Creation**: Creating unnecessary hardware when alias would work
4. **Software Conditional Usage**: Using `if/else` instead of `when/otherwise`
5. **Component Type Wrapping**: Using Option/Either with Components
6. **Clock Domain Misunderstanding**: Treating clock domains like software threads
7. **Magic Number Usage**: Using unnamed literals with semantic meaning

### AI Model Survival Rules

1. **Check constructor first**: Are hardware signals in constructor parameters?
2. **Always ask**: Is this generation-time (Scala) or hardware logic (SpinalHDL)?
3. **Never mix**: Scala type system with SpinalHDL Components
4. **Remember**: `when/otherwise` creates hardware, `if/else` controls generation
5. **Understand**: Signal alias vs new signal creation impacts hardware
6. **Respect**: Clock domains represent physical timing, not software concepts
7. **Question numbers**: Does this literal have semantic meaning? If yes, name it

### Quick Reference

| Concept | SpinalHDL Way | Software Way (Avoid) |
|---------|---------------|---------------------|
| Constructor parameters | Config-only: `class C(config: Config)` | Hardware signals: `class C(signal: Bool)` |
| Hardware interface | `io` Bundle only | Constructor parameters |
| Conditional logic | `when/otherwise` | `if/else` |
| Signal creation | `val signal = Type()` | Variable assignment |
| Component instantiation | `new Component()` | Factory patterns |
| Collections for generation | Scala collections | Hardware arrays |
| Type domains | Strict separation | Mixed usage |
| Numeric constants | Named constants | Magic numbers |

### Requirement: REQ-CS-010: SpinalHDL SBT Build Tool Enforcement
All SpinalHDL project compilation, testing, and packaging operations SHALL be executed through SBT tools, with strict prohibition of direct scalac or java command usage, ensuring build consistency across the SpinalHDL ecosystem.

#### Scenario: SpinalHDL Compilation Operations Must Use SBT
- **WHEN** compiling SpinalHDL projects or any modules (projects containing SpinalHDL code)
- **THEN** developers or AI assistants SHALL use SBT commands such as `sbt compile`, `sbt ++${version} compile`, or `sbt project/compile`
- **AND** SHALL NOT directly use `scalac` commands to compile SpinalHDL source files
- **AND** SHALL NOT directly use `javac` commands to compile Java source files
- **AND** SHALL ensure SpinalHDL compiler plugins are properly loaded through SBT
- **AND** SHALL ensure all dependencies are correctly resolved through SBT dependency management

#### Scenario: SpinalHDL Test Execution Must Use SBT
- **WHEN** running any tests for SpinalHDL projects (HDL generation tests, simulation tests, etc.)
- **THEN** developers or AI assistants SHALL use SBT test commands such as `sbt test`, `sbt project/test`, or `sbt "testOnly TestClass"`
- **AND** SHALL NOT directly use `scala` or `java` commands to execute SpinalHDL test classes
- **AND** SHALL manage SpinalHDL test classpaths and compiler plugins through SBT configuration
- **AND** SHALL use SBT's parallel testing and sharding features for improved efficiency
- **AND** SHALL ensure SpinalHDL simulator integration is properly configured through SBT

#### Scenario: SpinalHDL Packaging and Publishing Must Use SBT
- **WHEN** creating JAR files for SpinalHDL projects or publishing projects containing SpinalHDL code
- **THEN** developers or AI assistants SHALL use SBT commands such as `sbt assembly`, `sbt package`, or `sbt publish`
- **AND** SHALL NOT manually create JAR files or use external packaging tools
- **AND** SHALL manage packaging configuration through SBT plugins (such as sbt-assembly)
- **AND** SHALL ensure generated JAR files contain all required SpinalHDL dependencies and metadata

#### Scenario: SpinalHDL Dependency Management Must Use SBT
- **WHEN** adding, updating, or managing dependencies for SpinalHDL projects (including SpinalHDL itself, simulators, etc.)
- **THEN** developers or AI assistants SHALL declare dependencies in `build.sbt` files
- **AND** SHALL NOT manually download SpinalHDL JAR files or use other dependency management tools
- **AND** SHALL leverage SBT's dependency resolution and conflict resolution mechanisms
- **AND** SHALL use SBT's forced version override functionality to ensure SpinalHDL version consistency

#### Scenario: SpinalHDL Project Configuration Management Must Use SBT
- **WHEN** configuring SpinalHDL project compilation options, SpinalHDL compiler plugin parameters, or project settings
- **THEN** developers or AI assistants SHALL configure in `build.sbt`
- **AND** SHALL manage Scala versions, SpinalHDL compiler plugins, and other plugins through SBT settings
- **AND** SHALL use SBT's environment variable and system property configuration mechanisms
- **AND** SHALL ensure all SpinalHDL-related configuration changes take effect through SBT

### Requirement: REQ-CS-011: SpinalHDL AI Assistant SBT Command Usage Guidelines
AI assistants SHALL strictly follow SBT command patterns and best practices when executing SpinalHDL project-related operations, ensuring consistent development experience across the SpinalHDL ecosystem.

#### Scenario: SpinalHDL Standard Compilation Mode
- **WHEN** AI assistants need to compile SpinalHDL project code
- **THEN** SHALL use `sbt compile` for full project compilation
- **AND** SHALL use `sbt project/compile` for specific module compilation (such as: core, lib, etc.)
- **AND** SHALL use `sbt ++${scalaVersion} compile` to specify Scala version compilation (SpinalHDL supports multiple versions)
- **AND** SHALL use `sbt clean compile` for clean full compilation

#### Scenario: SpinalHDL Test Execution Mode
- **WHEN** AI assistants need to run SpinalHDL project tests (including HDL generation, simulation, waveform, etc.)
- **THEN** SHALL use `sbt test` to run all SpinalHDL-related tests
- **AND** SHALL use `sbt "testOnly TestClassName"` to run specific SpinalHDL test classes
- **AND** SHALL use `sbt project/test` to run tests for specific SpinalHDL modules
- **AND** SHALL use filtering parameters based on SpinalHDL test tags (such as `-l formal`, `-n formal`, `-l simulation`)

#### Scenario: SpinalHDL Development and Debugging Mode
- **WHEN** AI assistants need to perform interactive development or debugging for SpinalHDL projects
- **THEN** SHALL use `sbt console` to start Scala console with SpinalHDL libraries
- **AND** SHALL use `sbt project/console` to start SpinalHDL console for specific modules
- **AND** SHALL use `sbt run` to run SpinalHDL code generation or simulation applications
- **AND** SHALL use SBT's continuous compilation (`~compile`) functionality for SpinalHDL development-time compilation

### Requirement: REQ-CS-012: SpinalHDL Project SBT Configuration and Plugin Management
SpinalHDL project SBT configurations, plugins, and settings SHALL follow standard patterns of the SpinalHDL ecosystem, ensuring build consistency and maintainability.

#### Scenario: SpinalHDL Plugin Dependency Management
- **WHEN** configuring SBT plugins for SpinalHDL projects
- **THEN** SHALL declare plugin dependencies in `project/plugins.sbt`
- **AND** SHALL include SpinalHDL compiler plugins (spinalhdl-idsl-plugin)
- **AND** SHALL use SpinalHDL ecosystem standard plugin versions (such as sbt-assembly, scalafmt, etc.)
- **AND** SHALL ensure plugin configuration is compatible with SpinalHDL version requirements
- **AND** SHALL avoid using non-standard plugins that may affect SpinalHDL code generation

#### Scenario: SpinalHDL Multi-Module Project Configuration
- **WHEN** handling SpinalHDL multi-module SBT project structures (such as: core, lib, tester module separation)
- **THEN** SHALL correctly configure project dependency relationships (`dependsOn`)
- **AND** SHALL ensure SpinalHDL compiler plugins are properly loaded in all required subprojects
- **AND** SHALL use appropriate configuration scopes (Compile, Test, Runtime)
- **AND** SHALL manage cross-project SpinalHDL settings and configuration inheritance

#### Scenario: SpinalHDL Build Optimization and Parallelization
- **WHEN** optimizing SpinalHDL project SBT build performance
- **THEN** SHALL reasonably use SBT parallel execution features (especially suitable for SpinalHDL code generation)
- **AND** SHALL configure appropriate memory settings (`SBT_OPTS`) to support large SpinalHDL projects
- **AND** SHALL use SBT's incremental compilation and caching mechanisms (especially important for SpinalHDL development)
- **AND** SHALL avoid unnecessary SpinalHDL code regeneration and dependency resolution

## Prohibited Command Patterns in SpinalHDL Projects

### Strictly Prohibited Commands
The following commands are strictly prohibited in any SpinalHDL project as they bypass SpinalHDL compiler plugins and dependency management:

```bash
# ❌ PROHIBITED: Direct scalac compilation of SpinalHDL code (cannot load compiler plugins)
scalac -classpath "lib/*" src/main/scala/*.scala

# ❌ PROHIBITED: Direct java execution of SpinalHDL applications
java -cp "target/classes:lib/*" com.example.MySpinalHDLApp

# ❌ PROHIBITED: Manual creation of JAR files containing SpinalHDL code
jar cf myapp.jar -C target/classes .

# ❌ PROHIBITED: Manual download and management of SpinalHDL JAR dependencies
wget https://repo1.maven.org/.../spinalhdl-core.jar
```

### Correct SpinalHDL SBT Command Patterns
```bash
# ✅ CORRECT: Use SBT to compile SpinalHDL projects (automatically loads compiler plugins)
sbt compile
sbt ++2.12.15 compile
sbt core/compile

# ✅ CORRECT: Use SBT to test SpinalHDL code
sbt test
sbt project/test  # Determine specific module based on project structure
sbt "testOnly spinal.lib.*"

# ✅ CORRECT: Use SBT to package SpinalHDL projects
sbt package
sbt assembly
sbt publishLocal

# ✅ CORRECT: Use SBT console for SpinalHDL development
sbt console
sbt "~compile"  # Continuous compilation of SpinalHDL code
```

---

This specification provides the foundation for consistent, maintainable SpinalHDL code development, with particular emphasis on helping AI assistants understand the critical differences between hardware description and software programming, and enforcing proper SBT build practices across the SpinalHDL ecosystem.