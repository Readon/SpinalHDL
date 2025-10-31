# SpinalHDL Hardware Design Guide

## Table of Contents
- [Core Design Philosophy](#core-design-philosophy)
- [Component Architecture](#component-architecture)
- [Data Type System](#data-type-system)
- [Sequential Logic Design](#sequential-logic-design)
- [Clock Domain Management](#clock-domain-management)
- [Signal Assignment and Hardware Syntax](#signal-assignment-and-hardware-syntax)
- [Area and Component Organization](#area-and-component-organization)
- [Simulation and Testing](#simulation-and-testing)
- [Coding Standards and Naming](#coding-standards-and-naming)
- [Quick Reference](#quick-reference)

## Purpose

This guide defines essential coding standards for SpinalHDL development, ensuring consistent, maintainable, and hardware-correct code generation.

## Core Design Philosophy

### SpinalHDL vs Scala: Mental Model Shift for AI Models

**Critical Understanding**: SpinalHDL is NOT just another Scala library - it's a hardware description language that uses Scala as its host language for hardware **generation**.

**Key Concept Shifts:**
- **Software Thinking**: Code executes sequentially at runtime
- **Hardware Thinking**: Code executes at compile-time to generate hardware structures
- **Scala Variables**: Store values during program execution
- **SpinalHDL Signals**: Represent physical wires and storage elements in hardware

### Requirement: SpinalHDL Design Principles
The Coding Agent SHALL understand and apply SpinalHDL's core design philosophy of explicit hardware declaration over inference.

**Fundamental Hardware Concepts:**

#### 1. Generation-Time vs Runtime
```scala
// Scala thinking (WRONG for hardware):
def multiplexer(a: Int, b: Int, sel: Boolean): Int = if (sel) a else b

// SpinalHDL thinking (CORRECT for hardware):
val a = UInt(8 bits)
val b = UInt(8 bits)
val sel = Bool()
val result = UInt(8 bits)
when(sel) {
  result := a  // Creates hardware multiplexer
} otherwise {
  result := b
}
```

**Critical Differences:**
- `if/else` executes at runtime vs `when/otherwise` creates hardware structures
- Scala functions return values vs SpinalHDL creates permanent hardware connections
- Software runs sequentially vs hardware evaluates in parallel

#### 2. Type Domains: When to Use Which
| Context | Use Scala Types | Use SpinalHDL Types |
|---------|-----------------|---------------------|
| Configuration parameters | ✅ `Int`, `Boolean`, `String` | ❌ |
| Loop indices (generation-time) | ✅ `i <- 0 until 8` | ❌ |
| Hardware signals | ❌ | ✅ `UInt`, `Bool`, `Bits` |
| Hardware operations | ❌ | ✅ `+`, `&`, `===` |
| Simulation stimulus | ✅ `dut.io.signal #= 5` | ❌ |

#### 3. Hardware Generation Examples
```scala
// Generation-time loop creates 8 identical hardware units
val data = Vec(UInt(16 bits), 8)
val results = Vec(UInt(16 bits), 8)
for (i <- 0 until 8) {
  val processor = new ProcessingUnit()
  processor.io.input := data(i)
  results(i) := processor.io.output
}

// Configuration resolved at generation time
val dataWidth = if (config.useWideBus) 64 else 32
val dataBus = Bits(dataWidth bits)
```

**Essential Requirements:**
- Use explicit `Reg`, `RegInit`, `RegNext` declarations instead of process-based inference
- Distinguish between Scala types (generation-time) and SpinalHDL types (hardware)
- Understand that SpinalHDL code generates hardware, it doesn't execute like software
- Ensure abstractions generate zero-overhead RTL with preserved hierarchy

#### Scenario: Explicit vs Inferred Design
- **WHEN** declaring sequential elements
- **THEN** use explicit register declarations instead of inference
- **AND** understand SpinalHDL names hardware concepts directly

#### Scenario: Hardware Generation Understanding
- **WHEN** writing SpinalHDL code
- **THEN** understand it creates hardware structures, not executing program logic
- **AND** recognize that loops and conditions create/modify hardware topology

## Component Architecture

### Requirement: REQ-CS-001: Component IO Bundle Access Standards
All Component input/output signals MUST be accessed through the `io` named Bundle, and internal signals MUST be properly encapsulated.

**Hardware Boundary Principles:**

The `io` Bundle represents physical pins and interfaces of hardware, defining what synthesis tools see as external ports.

```scala
case class MemoryController() extends Component {
  val io = new Bundle {
    val address = in UInt(32 bits)    // Input pins
    val dataIn = in UInt(64 bits)     // Input pins
    val dataOut = out UInt(64 bits)   // Output pins
    val enable = in Bool()            // Input pin
    val ready = out Bool()            // Output pin
  }

  // Internal signals are NOT accessible from outside
  val internalState = Reg(UInt(8 bits))  // Internal register only
  val internalLogic = UInt(64 bits)      // Internal wire only
}
```

**Critical Requirements:**
- Component classes MUST define `val io = new Bundle { ... }`
- All external signals MUST be defined within the `io` Bundle
- Internal signals without `io.` prefix become internal nets only
- External access to internal signals is prohibited

#### Hardware Encapsulation Example
```scala
case class MemoryBuffer(depth: Int, width: Int) extends Component {
  val io = new Bundle {
    val write = slave(Stream(Bits(width bits)))  // External interface
    val read = master(Stream(Bits(width bits)))  // External interface
  }

  // Internal signals - completely hidden from outside
  val memoryArray = Mem(Bits(width bits), depth)
  val writePointer = Reg(UInt(log2Up(depth) bits)) init(0)
  val readPointer = Reg(UInt(log2Up(depth) bits)) init(0)

  // Connect to external world only through io Bundle
  io.read.valid := True
  io.read.payload := memoryArray(readPointer)
}
```

#### Hardware Assignment Example
```scala
class GeneratedModule(config: Config) extends Component {
  val io = new Bundle {
    val inputs = Vec(in UInt(8 bits), config.numInputs)
    val output = out UInt(8 bits)
  }

  val processingArea = new Area {
    val signals = Vec(UInt(8 bits), config.numInputs)
    for (i <- 0 until config.numInputs) {
      signals(i) := io.inputs(i) + 1
    }

    // ✓ Correct: Use assignUnassignedByName after custom assignments
    val resultBundle = new ResultBundle()
    resultBundle.data := signals.reduce(_ + _)
    resultBundle.valid := True
    resultBundle.assignUnassignedByName()  // Fills in any unassigned fields
  }
}
```

#### assignUnassignedByName Usage

The `assignUnassignedByName()` method is useful for Bundle connections where some fields need custom assignment and others should be automatically connected by name:

```scala
// Custom assignment for specific fields, auto-assign the rest
val interface = new DataInterface()
interface.customField := customLogic()  // Manual assignment
interface.assignUnassignedByName()      // Auto-assign remaining fields by name

// Common pattern in transformations
val transformedBundle = sourceBundle.clone()
transformedBundle.specialField := calculateSpecial()
transformedBundle.assignUnassignedByName()  // Connect all other fields automatically
```

#### Encapsulation Rules

**WRONG: Direct Internal Signal Access**
```scala
val memory = new MemoryBuffer()
memory.internalState := 5  // ERROR: Internal signal not accessible
memory.memoryArray(10) := data  // ERROR: Internal memory not accessible

// ✓ Correct: Access only through defined interfaces
memory.io.write.valid := True
memory.io.write.payload := data
```

**Type Definition Rules**
- Define types outside of Component bodies
- Don't define new types within Component implementations

### Requirement: REQ-CS-008: Bundle Direction Management
Bundle inheritance and IMasterSlave interface implementation MUST follow standardized patterns for signal direction management.

**Critical Requirements:**
- When inheriting from Bundle, signal directions MUST NOT be specified in signal definitions
- When implementing IMasterSlave interfaces, signal directions MUST be specified only in the asMaster function
- Directions MUST be managed uniformly through the IMasterSlave trait's asMaster() method

## Data Type System

### Requirement: REQ-CS-002: Basic Data Type Usage Standards
Basic data types MUST be used according to their intended semantics and proper initialization patterns.

**Critical Type Domain Understanding:**

#### Hardware vs Software Types
```scala
// Configuration: Scala types (generation-time only)
case class CpuConfig(width: Int, hasFpu: Boolean)

// Hardware: SpinalHDL types (creates physical hardware)
case class CpuIo(config: CpuConfig) extends Bundle {
  val data = UInt(config.width bits)  // Creates physical wires
  val valid = Bool()                  // Creates physical wire
  val ready = Bool()                  // Creates physical wire
}

// WRONG: Mixing domains
val dataBuffer: Int = 5  // This doesn't create hardware!

// CORRECT: Proper domain usage
val dataBuffer = UInt(32 bits)  // Creates 32-bit register
```

**Essential Data Types:**

| Type | Purpose | When to Use |
|------|---------|-------------|
| `Bool()` | Single-bit logical signals | Control signals, flags |
| `Bits(n bits)` | Raw bit vectors | Data paths, bit manipulation |
| `UInt(n bits)` | Unsigned arithmetic | Counters, addresses |
| `SInt(n bits)` | Signed arithmetic | DSP, math operations |
| `Enum` | State encoding | FSM state registers |
| `Bundle` | Named signal collections | Interface grouping |
| `Vec` | Homogeneous arrays | Register files, memories |

#### Type Usage Patterns for AI Models

**1. Signal Declaration vs Assignment**
```scala
// Declaration (creates hardware structure)
val counter = UInt(16 bits)     // Creates 16-bit signal
val enable = Bool()             // Creates 1-bit signal
val dataBus = Bits(64 bits)     // Creates 64-bit bus

// Assignment (creates hardware connections)
counter := counter + 1          // Creates adder + register feedback
enable := io.start              // Creates wire connection
dataBus := io.dataIn            // Creates bus connection
```

**2. Signal Alias vs New Signal Creation - Critical Distinction**
```scala
// Given an existing signal
val originalSignal = UInt(8 bits)
originalSignal := io.dataIn

// ✓ Case 1: Signal Alias (same hardware, different name)
val aliasSignal = originalSignal
// aliasSignal is just another name for the SAME hardware signal
// No additional hardware is created
// Both names refer to the identical physical wire
aliasSignal := 0x55  // This also changes originalSignal

// ✓ Case 2: New Signal with Initialization (separate hardware)
val newSignal = CombInit(originalSignal)
// newSignal is a NEW hardware signal that is initially connected to originalSignal
// Creates a separate physical wire
// Subsequent assignments to newSignal don't affect originalSignal
newSignal := 0xAA  // This only changes newSignal, originalSignal unchanged

// ✓ Case 3: New Signal with Default Assignment
val anotherSignal = UInt(8 bits)
anotherSignal := originalSignal  // Creates new signal and connects it
anotherSignal := 0xCC  // Subsequent assignments are independent
```

**Detailed Hardware Implications:**

#### Signal Alias (`val a = b`)
- **Hardware Result**: No additional hardware created
- **Physical Meaning**: `a` and `b` are the same physical wire with different names
- **Assignment Effect**: `a := x` is identical to `b := x`
- **Usage**: Readability, temporary naming, reference convenience
- **RTL Impact**: Zero hardware overhead

#### New Signal with CombInit (`val a = CombInit(b)`)
- **Hardware Result**: Creates new physical wire with initial connection from `b`
- **Physical Meaning**: `a` is a separate wire that happens to be driven by `b` initially
- **Assignment Effect**: `a := x` only affects `a`, `b` remains unchanged
- **Usage**: Signal buffering, creating breakpoints, signal isolation
- **RTL Impact**: Adds actual wire to the design

#### Direct Assignment (`val a = SignalType(); a := b`)
- **Hardware Result**: Creates new signal and continuous assignment
- **Physical Meaning**: `a` is always driven by `b` unless overridden
- **Assignment Effect**: Later assignments to `a` override the initial connection
- **Usage**: Default connections with override capability
- **RTL Impact**: Adds wire and assignment logic

**Practical Examples:**
```scala
// Debug probe example - alias doesn't affect hardware
val internalData = UInt(32 bits)
internalData := someLogic()
val debugProbe = internalData  // Alias - no hardware overhead
// Can monitor debugProbe without affecting internalData

// Signal buffering example - creates isolation
val sensitiveSignal = UInt(32 bits)
val bufferedSignal = CombInit(sensitiveSignal)  // New wire
// bufferedSignal can be modified without affecting sensitiveSignal

// Signal fanout example
val source = UInt(8 bits)
val dest1 = CombInit(source)  // New wire
val dest2 = CombInit(source)  // Another new wire
dest1 := modify1(dest2)  // Doesn't affect source or dest2
```

**Common Usage Patterns:**
```scala
// Signal aliasing - used for readability
val addressBus = io.memory.address  // alias for readability

// New signal creation - used for signal manipulation
val bufferedData = CombInit(io.input.data)  // Create buffer
val registeredSignal = RegNext(io.signal)   // Create register
```

**2. Initialization Patterns**
```scala
// Register initialization
val pipelineReg = Reg(Bits(32 bits)) init(B"32'h00000000")

// Combinational signal default assignment (prevents latches)
val nextstate = UInt(4 bits)
nextstate := 0  // Default assignment
when(condition) {
  nextstate := newstate  // Conditional assignment
}
```

**3. Type Conversion and Resizing**
```scala
// Resizing
val wide = UInt(16 bits)
val narrow = wide.resize(8 bits)  // Truncates upper bits
val extended = narrow.resized     // Zero-extends

// Type conversion
val signed = SInt(8 bits)
val unsigned = signed.asUInt      // Sign bit preserved
```

```scala
// Good: Type system usage
case class AluInstruction() extends Bundle {
  val opcode = UInt(6 bits)
  val rs1 = UInt(5 bits)
  val rs2 = UInt(5 bits)
  val rd = UInt(5 bits)
  val funct3 = UInt(3 bits)

  override def clone = AluInstruction()
}

// Example: Proper type usage in hardware
class AluUnit(config: AluConfig) extends Component {
  val io = new Bundle {
    val instruction = in(AluInstruction())
    val rs1Data = in(UInt(config.dataWidth bits))
    val rs2Data = in(UInt(config.dataWidth bits))
    val result = out(UInt(config.dataWidth bits))
    val valid = out(Bool())
  }

  // Hardware operations create actual logic
  val aluResult = UInt(config.dataWidth bits)

  switch(io.instruction.opcode) {
    is(0b000001) { aluResult := io.rs1Data + io.rs2Data }  // Creates adder
    is(0b000011) { aluResult := io.rs1Data - io.rs2Data }  // Creates subtractor
    default    { aluResult := 0 }                         // Creates constant driver
  }

  io.result := aluResult
  io.valid := True
}
```

## Sequential Logic Design

### Requirement: REQ-CS-003: Sequential Logic Design Patterns
Sequential logic MUST use proper register instantiation and timing control patterns.

**Register Types:**
- `Reg()` for basic registers with optional initialization
- `RegNext()` for single-cycle delayed signals
- `RegInit()` for registers with explicit reset values
- `RegNextWhen()` for conditional sampling

**Critical Requirements:**
- Avoid combinational loops through proper register usage
- Ensure complete assignment in conditional blocks to prevent latches
- For combinational signals conditionally assigned, provide default assignments

```scala
// Good: Sequential logic patterns
class PipelinedProcessor extends Component {
  val io = new Bundle {
    val instruction = in UInt(32 bits)
    val result = out UInt(32 bits)
    val valid = out Bool()
  }

  val stage1 = RegNext(io.instruction)
  val stage2 = RegNext(stage1)

  io.result := stage2 + 1
  io.valid := RegNext(RegNext(True), init = False)  // Pipeline valid signal: delays True by 2 cycles with False initialization
}
```

## Clock Domain Management

### Requirement: REQ-CS-004: Clock Domain Management Principles
Multi-clock domain designs MUST use proper clock domain crossing and synchronization techniques.

**Physical Timing Domain Fundamentals:**

#### Why Clock Domains are Critical in Hardware

**1. Physical Timing Boundaries**
```scala
// Clock domains represent physical timing boundaries in hardware
// Each domain has its own clock signal and timing characteristics

val mainClock = ClockDomain(
  clock = io.mainClk,
  reset = io.mainReset,
  frequency = FixedFrequency(100 MHz)  // Physical clock frequency
)

val slowClock = ClockDomain(
  clock = io.slowClk,
  reset = io.slowReset,
  frequency = FixedFrequency(25 MHz)   // Different physical clock
)
```

**2. Metastability and Timing Violations**
```scala
// WRONG: Direct connection between clock domains causes metastability
class BadCrossDomain() extends Component {
  val io = new Bundle {
    val fastSignal = in Bool()    // 100MHz domain
    val slowSignal = out Bool()   // 25MHz domain
  }

  // ✗ This creates timing violations and metastability!
  io.slowSignal := io.fastSignal  // Direct crossing
}

// CORRECT: Proper clock domain crossing
class GoodCrossDomain() extends Component {
  val io = new Bundle {
    val fastSignal = in Bool()    // 100MHz domain
    val slowSignal = out Bool()   // 25MHz domain
    val slowClock = in Bool()     // Clock signal for target domain
  }

  // ✓ Proper synchronization with 2-stage synchronizer
  val slowClockDomain = ClockDomain(clock = io.slowClock)
  val synchronizer = BufferCC(io.fastSignal, slowClockDomain)
  io.slowSignal := synchronizer
}
```

**3. Clock Domain Hierarchies**
```scala
// ClockingArea creates hierarchical timing domains
class MultiDomainDesign() extends Component {
  val io = new Bundle {
    val systemClk = in Bool()
    val peripheralClk = in Bool()
    val output = out UInt(16 bits)
  }

  val systemDomain = ClockDomain(clock = io.systemClk)
  val peripheralDomain = ClockDomain(clock = io.peripheralClk)

  val systemArea = new ClockingArea(systemDomain) {
    val systemFlag = Reg(UInt(1 bits)) init(0)
    systemFlag := systemFlag + 1
  }

  val peripheralArea = new ClockingArea(peripheralDomain) {
    val syncFlag = BufferCC(systemArea.systemFlag, peripheralDomain)
    val counter = Reg(UInt(16 bits)) init(0)
    when(syncFlag) { counter := counter + 1 }
    io.output := counter
  }
}
```

**4. Reset Synchronization**
```scala
// Use ClockDomainResetGenerator for proper async reset handling
val resetCtrl = ClockDomainResetGenerator()
resetCtrl.addResetIo(io.extReset)
resetCtrl.buildAsyncAssertSyncDeassert()

val mainDomain = ClockDomain(
  clock = io.clock,
  reset = resetCtrl,
  config = ClockDomainConfig(resetActiveLevel = LOW, resetKind = ASYNC)
)
```

**Essential Requirements:**
- Use `ClockDomain()` to define clock domains with proper configuration
- Use `ClockingArea` to encapsulate logic within specific clock domains
- Use `BufferCC` for proper clock domain crossing synchronization
- Avoid direct signal connections between different clock domains
- Understand that clock domains represent physical timing boundaries, not just organizational constructs

#### Clock Domain Crossing Patterns

**1. Single Bit Synchronization**
```scala
// 2-stage synchronizer for single bit signals
val syncSignal = BufferCC(asyncSignal, targetClockDomain)
```

**2. Multi-bit Data Synchronization**
```scala
// Method 1: StreamCCByToggle (toggle-based protocol)
val handshakedCrossing = StreamCCByToggle(
  dataStream = sourceStream,
  targetClockDomain = targetDomain
)

// Method 2: StreamFifoCC (true clock domain crossing FIFO)
val fifoCrossing = StreamFifoCC(
  dataType = Bits(32 bits),
  depth = 16,  // FIFO depth
  pushClock = sourceClockDomain,
  popClock = targetClockDomain
)

// Method 3: StreamCCByWidth (for small data with minimal latency)
val widthCrossing = StreamCCByWidth(
  dataStream = sourceStream,
  targetClockDomain = targetDomain
)
```

**Cross-Domain FIFO Usage Examples:**
```scala
// High-throughput data crossing with buffering
class DataCrossingBuffer() extends Component {
  val io = new Bundle {
    val sourceClk = in Bool()
    val targetClk = in Bool()
    val sourceStream = slave(Stream(Bits(64 bits)))
    val targetStream = master(Stream(Bits(64 bits)))
  }

  val sourceDomain = ClockDomain(clock = io.sourceClk)
  val targetDomain = ClockDomain(clock = io.targetClk)

  // Create FIFO for true asynchronous domain crossing
  val crossingFifo = StreamFifoCC(
    dataType = Bits(64 bits),
    depth = 32,  // Trade-off: more depth = more tolerance, more latency
    pushClock = sourceDomain,
    popClock = targetDomain
  )

  crossingFifo.io.push <> io.sourceStream
  io.targetStream <> crossingFifo.io.pop
}

// For high-frequency crossing where latency matters
class LowLatencyCrossing() extends Component {
  val io = new Bundle {
    val fastStream = slave(Stream(Bits(8 bits)))
    val slowStream = master(Stream(Bits(8 bits)))
  }

  // Use toggle method for lower latency (no FIFO buffering)
  val crossing = StreamCCByToggle(
    dataStream = io.fastStream,
    targetClockDomain = slowClockDomain
  )
  io.slowStream <> crossing
}
```

**Choosing the Right CDC Method:**

| Method | Best For | Latency | Resource Usage | Data Throughput |
|--------|----------|---------|----------------|-----------------|
| `StreamCCByToggle` | Low latency, bursty data | Low | Minimal | Moderate |
| `StreamFifoCC` | High throughput, continuous data | Variable (depends on depth) | Moderate-High | High |
| `StreamCCByWidth` | Small data, minimal hardware | Low | Minimal | Low-Moderate |

**3. Pulse Synchronization**
```scala
// Convert pulses to level signals for synchronization
val pulseLevel = RegNext(False)
when(asyncPulse) { pulseLevel := True }
when(syncPulseLevel) { pulseLevel := False }
val syncPulse = BufferCC(pulseLevel, targetDomain)
```

#### Common Clock Domain Errors

**WRONG: Mixing Clock Domains**
```scala
// ✗ This creates timing violations
val mixedArea = new ClockingArea(systemDomain) {
  val systemReg = Reg(UInt(8 bits))
  val peripheralReg = Reg(UInt(8 bits))  // Wrong domain!
}
```

**CORRECT: Proper Domain Separation**
```scala
// ✓ Each domain in its own ClockingArea
val systemArea = new ClockingArea(systemDomain) {
  val systemReg = Reg(UInt(8 bits))
}

val peripheralArea = new ClockingArea(peripheralDomain) {
  val peripheralReg = Reg(UInt(8 bits))
}
```

## Signal Assignment and Hardware Syntax

### Requirement: Signal Connection Standards
Signal connections MUST follow proper operator usage to ensure correctness.

**Connection Rules:**
- Use `<>` operator for connections within the same Component or Bundle initialization
- Use explicit assignment operators like `:=` for connections between different Components
- Use `<<` and `>>` functions for IMasterSlave interface connections
- Use `assignUnassignedByName()` for Bundle connections after custom field assignments

### Requirement: REQ-CS-027: Hardware Description Syntax Purity
Hardware description MUST use only SpinalHDL constructs and avoid mixing Scala runtime syntax.

**Critical Requirements:**
- Prohibit Scala conditional expressions in hardware contexts
- Use `when() {} otherwise {}` statements for conditional hardware logic
- Use SpinalHDL assignment operators (`:=`) exclusively for hardware signal assignments
- Avoid Scala runtime constructs in Component bodies and Area classes

#### Why Scala Syntax Fails in Hardware Contexts

**1. Type System Mismatch: Boolean vs Bool**
```scala
// WRONG: Scala Boolean (software type)
val cond: Boolean = true
val result = if (cond) hardwareSignal else otherSignal  // Type error!

// CORRECT: SpinalHDL Bool (hardware type)
val cond = Bool()
val result = UInt(8 bits)
when(cond) {
  result := hardwareSignal  // Hardware multiplexer created
} otherwise {
  result := otherSignal
}
```

**2. Execution Paradigm Mismatch: Sequential vs Parallel**
```scala
// WRONG: Scala thinking (sequential execution)
def multiplex(a: UInt, b: UInt, sel: Bool): UInt = {
  if (sel.toBoolean) a else b  // This executes at runtime!
}

// CORRECT: SpinalHDL thinking (parallel hardware)
val a = UInt(8 bits)
val b = UInt(8 bits)
val sel = Bool()
val result = UInt(8 bits)
when(sel) {
  result := a    // Creates hardware that selects 'a'
} otherwise {
  result := b    // Creates hardware that selects 'b'
}
// Both branches exist simultaneously in hardware!
```

**3. Hardware Structure vs Software Behavior**
```scala
// WRONG: This creates no hardware structure
var counter = 0
if (enable) {
  counter = counter + 1  // Software variable update
}

// CORRECT: This creates actual hardware structure
val enable = Bool()
val counter = Reg(UInt(16 bits)) init(0)
when(enable) {
  counter := counter + 1  // Hardware register + adder + feedback
}
```

**4. Operator Semantics Differences**
```scala
// Scala operators (software semantics)
val softwareA: Int = 5
val softwareB: Int = 3
val softwareResult = if (softwareA == softwareB) true else false  // Software comparison

// SpinalHDL operators (hardware semantics)
val a = UInt(8 bits)
val b = UInt(8 bits)
val equal = a === b  // Creates hardware comparator
val result = UInt(1 bits)
when(equal) {
  result := 1
} otherwise {
  result := 0
}
```

**Valid Examples:**
```scala
// ✓ Correct: Use when() for conditional logic
when(io.input) {
  io.output := B"32'hFFFFFFFF"
} otherwise {
  io.output := B"32'h00000000"
}

// ✓ Correct: SpinalHDL mux operator
val signal = Bits(32 bits)
signal := io.input ? B"32'hAAAAAAAA" | B"32'hBBBBBBBB"

// ✓ Correct: Hardware-style operations
val equal = io.signal1 === io.signal2  // Hardware comparator
val bitwise = io.signal1 & io.signal2   // Hardware AND gate
val logical = io.signal1 && io.signal2  // Logical AND (creates reduction)
```

**Invalid Examples with Explanations:**
```scala
// ✗ Type error: Boolean vs Bool
val result = io.input ? B"32'hFFFFFFFF" : B"32'h00000000"
// Error: io.input is Bool (hardware), ? : expects Boolean (Scala)

// ✗ Paradigm error: Sequential logic in hardware context
io.output := if (io.input) B"32'hFFFFFFFF" else B"32'h00000000"
// Error: if/else is software construct, doesn't create hardware structure

// ✗ Variable assignment error
var counter = 0
counter := counter + 1
// Error: := is for hardware signals, not Scala variables
```

#### Common Syntax Translation Guide

| Software Concept | Hardware Equivalent | Why |
|-----------------|-------------------|-----|
| `if (condition) a else b` | `when(condition) { result := a } otherwise { result := b }` | Creates parallel hardware paths |
| `condition ? a : b` | `condition ? a \| b` | SpinalHDL ternary operator |
| `var x = 0; x = x + 1` | `val x = Reg(UInt(n bits)); x := x + 1` | Hardware register vs software variable |
| `return value` | `result := value` | Hardware assignment vs function return |

## Area and Component Organization

### Requirement: REQ-CS-036: Area Definition Order Standards
Area and Composite objects MUST ensure that all sub-components are defined before they are referenced.

**Critical Requirements:**
- All sub-components MUST be defined before any reference to them in assignments or logic
- Analyze component dependency relationships explicitly to avoid circular dependencies
- Sub-components MUST be instantiated in the order of their usage dependencies

```scala
// ✓ Correct: Define sub-components before usage
val processingArea = new Area {
  val enableSignal = Bool()
  val dataBuffer = Reg(UInt(32 bits)) init(0)

  when(enableSignal) {
    dataBuffer := io.input + 1
  }
  io.output := dataBuffer
}
```

### Requirement: REQ-CS-035: Signal Assignment and Initialization Standards
Signal assignment MUST follow strict initialization and conditional assignment patterns.

**Critical Requirements:**
- Reg signals CAN be initialized using `init()` during declaration or assignment after declaration
- Comb signals MUST be initialized using assignment after declaration
- After initialization, all subsequent assignments MUST be placed within `when`, `switch`, or similar conditional constructs
- Initialization statements MUST be positioned before any conditional assignment code

```scala
// ✓ Correct: Initialization before conditional code
val myReg = Reg(UInt(32 bits)) init(0)
when(io.condition) {
  myReg := myReg + 1
}

// ✓ Correct: Comb signal with complete assignment
val myComb = UInt(32 bits)
myComb := 0  // Default assignment
when(io.condition) {
  myComb := 42
}
```

### Requirement: REQ-CS-034: Conditional Signal Access Safety
All conditionally generated signals MUST be checked for their corresponding configuration conditions before use.

**Critical Requirements:**
- Check configuration flags (Scala Boolean) before accessing conditionally generated signals
- Use Scala `if/else` for conditional signal access
- Provide default values or alternative logic when conditions are not met
- Use `generate` function for conditional hardware structure creation at generation time
- `generate` conditions must be based on Scala Boolean parameters, not SpinalHDL signals
- `generate` creates signals that can be `null`, so access must be guarded by Scala conditions

#### Generate Function for Conditional Signal Creation

The `generate` function enables conditional signal creation based on Boolean conditions. When false, the signal is assigned `null`; when true, the signal is created normally.

**Generate Function Syntax:**
```scala
val signal = condition generate SignalType(parameters)
// If condition is true: signal is created normally
// If condition is false: signal = null (no hardware generated)
```

**Generate Examples:**
```scala
// Conditional signal creation
val debugCounter = config.enableDebugMode generate Reg(UInt(32 bits)) init(0)

// Conditional interface signals
val debugInterface = config.hasDebugInterface generate slave(DebugInterface())
if (config.hasDebugInterface) {
  debugInterface.ready := RegNext(io.enable) init(False)
  io.debug <> debugInterface
}

// Conditional component creation
val fastPathFifo = config.useFastMode generate StreamFifoCC(dataType = Payload(), depth = 16)
val slowPathFifo = (!config.useFastMode) generate StreamFifo(dataType = Payload(), depth = 8)

// Connection logic with proper null checking
if(config.useFastMode) {
  fastPathFifo.io.push <> io.input
  io.output <> fastPathFifo.io.pop
} else {
  slowPathFifo.io.push <> io.input
  io.output <> slowPathFifo.io.pop
}

// Optional bundle fields
val debugSignals = new Bundle {
  val trainingCount = config.hasTraining generate out UInt(32 bits)
  val errorCount = config.hasErrorReporting generate out UInt(16 bits)
}
```

**Generate vs Traditional Block Generate:**

The `generate` function creates individual signals that can be `null`. This differs from block-style `generate {}` syntax:

```scala
// Individual signal generation (returns null when false)
val debugSignal = config.enableDebug generate Reg(UInt(8 bits))

// Block generation (excludes entire hardware block)
generate(config.enableDebug) {
  val debugCounter = Reg(UInt(32 bits)) init(0)
  // Complex debug logic here
}
```

**Safe Usage Patterns:**
```scala
// Safe usage after null check
val advancedProcessor = config.useAdvancedFeatures generate new AdvancedProcessor()

if (config.useAdvancedFeatures && advancedProcessor != null) {
  advancedProcessor.io.input <> io.data
  advancedProcessor.io.enable := io.enable
  io.output.valid := advancedProcessor.io.valid && io.enable
  io.output.data := advancedProcessor.io.data
} else {
  // Default implementation
  io.output.valid := io.enable
  io.output.data := io.data
}

// Multiple compression options example
val lz4Compressor = (config.useCompression && config.compressionAlgorithm == "LZ4") generate new LZ4Compressor()
val gzipCompressor = (config.useCompression && config.compressionAlgorithm == "GZIP") generate new GZIPCompressor()

if (config.useCompression && lz4Compressor != null) {
  lz4Compressor.io.input <> io.uncompressed
  io.compressed <> lz4Compressor.io.output
} else if (config.useCompression && gzipCompressor != null) {
  gzipCompressor.io.input <> io.uncompressed
  io.compressed <> gzipCompressor.io.output
} else {
  io.compressed <> io.uncompressed  // Pass-through
}
```

## Simulation and Testing

### Requirement: REQ-CS-026: Test Signal Assignment Patterns
Test signal assignments during simulation MUST follow consistent patterns, strictly using `#=` in doSim blocks.

**Critical Requirements:**
- Use `#=` operator exclusively for all signal assignments in simulation functions
- Never use `:=` in simulation contexts (reserved for hardware logic)
- Use appropriate data types: `Int` for integers, `Boolean` for booleans, `BigInt` for large integers
- Use explicit indexing for all collection assignments
- Ensure assignments propagate via `waitSampling()` calls

**Valid Examples:**
```scala
// ✓ Correct: #= for simulation assignments
dut.io.dfi.rdTraining.rdlvlReq(0) #= false

// ✓ Correct: Loop for collection initialization
for (i <- 0 until dut.io.dfi.read.rd.length) {
  dut.io.dfi.read.rd(i).rddataValid #= false
  dut.io.dfi.read.rd(i).rddata #= 0
}
```

**Invalid Examples:**
```scala
// ✗ Incorrect: := in simulation (compilation error)
dut.io.input := 5  // Wrong: := is for hardware logic
```

### Testbench Architecture Requirements
- Separate DUT instantiation from test logic
- Use appropriate simulation backends (Verilator, GHDL, etc.)
- Implement both deterministic and constrained-random generation
- Provide clear error reporting and debugging information

```scala
// Good: Testbench example
class UartTester extends SpinalSimFunSuite {
  test("UART transmission") {
    val dut = new UartTx()

    val simConfig = SimConfig.withVerilator.withWave
    simConfig.compile(dut).doSim { dut =>
      dut.io.data.valid #= true
      dut.io.data.payload #= 0x55

      dut.clockDomain.forkStimulus(10 MHz)
      dut.clockDomain.waitSampling()

      assert(dut.io.txd.toBoolean == false)
    }
  }
}
```

## Coding Standards and Naming

### Requirement: REQ-CS-033: General Naming Conventions
All identifiers MUST follow consistent naming patterns to improve code readability and maintainability.

**Naming Rules:**
- Use camelCase for variables, signals, and methods
- Use PascalCase for classes, components, and bundles
- Test class names MUST end with "Tester" or "Test"
- Bundle names MUST clearly indicate their interface purpose
- Signal names MUST describe their function or data content
- All names MUST be descriptive and indicate their specific purpose

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
```

### Requirement: REQ-CS-031: Configuration Class Naming Convention
Configuration class names MUST follow standardized naming patterns.

**Critical Requirements:**
- Configuration class names MUST end with "Config"
- Configuration classes MUST follow general naming conventions (REQ-CS-033)
- Configuration classes MUST NOT contain hardware types

## Component and Area Type Rules

### Requirement: Component Type Definition Prohibition
New type definitions MUST NOT be placed within Component, Area, or Composite implementations.

**Requirements:**
- Only instantiate objects of existing types within hardware components
- Do not define new types (Scala or SpinalHDL) within Component bodies

## Quick Reference

### Essential Syntax Patterns
```scala
// Component definition
case class MyComponent() extends Component {
  val io = new Bundle { ... }
}

// Register types
val reg1 = Reg(UInt(8 bits))           // Basic register
val reg2 = RegNext(True)               // Single-cycle delay
val reg3 = RegInit(0)                  // With initial value

// Signal alias vs new signal (CRITICAL)
val originalSignal = UInt(8 bits)
val aliasSignal = originalSignal       // Same hardware, zero overhead
val newSignal = CombInit(originalSignal)  // New wire, creates hardware

// Bundle connections with assignUnassignedByName
val interface = new DataInterface()
interface.customField := customLogic()
interface.assignUnassignedByName()      // Auto-assign remaining fields

// Conditional hardware logic
val condition = Bool()
val signal = UInt(8 bits)
val value1 = UInt(8 bits)
val value2 = UInt(8 bits)
when(condition) {
  signal := value1
} otherwise {
  signal := value2
}

// Signal assignment in simulation
dut.io.signal #= 5    // Simulation
signal := 0x55        // Hardware
```

### Common Error Prevention
1. **Always use `when/otherwise`** instead of Scala `if/else` for hardware signals
2. **Initialize signals** before conditional assignments
3. **Use `#=` in simulation, `:=` in hardware**
4. **Check conditional signals** before accessing them
5. **Define components before using them** in Areas
6. **Use descriptive names** that indicate purpose
7. **Understand signal alias vs new signal**: `val a = b` creates alias, `val a = CombInit(b)` creates new wire
8. **Use correct conditional logic for generate signals**: `generate` uses Scala Boolean conditions and creates nullable signals, so access must be guarded by Scala `if/else`, not SpinalHDL `when/otherwise`

### Critical Requirements Summary
- REQ-CS-001: Component IO Bundle access and naming
- REQ-CS-002: Basic data type usage
- REQ-CS-003: Sequential logic patterns
- REQ-CS-004: Clock domain management
- REQ-CS-026: Test signal assignment patterns
- REQ-CS-027: Hardware description syntax purity
- REQ-CS-031: Configuration class naming
- REQ-CS-033: General naming conventions
- REQ-CS-034: Conditional signal access safety
- REQ-CS-035: Signal assignment and initialization
- REQ-CS-036: Area definition order standards

This streamlined guide provides the essential requirements for generating robust, efficient, and maintainable SpinalHDL designs while maintaining all critical coding standards and best practices.