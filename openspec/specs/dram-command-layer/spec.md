# dram-command-layer Specification

## Purpose
TBD - created by archiving change enhance-bmb-ddr-bridge-test-coverage. Update Purpose after archive.
## Requirements
### Requirement: DRAM Command Type Coverage
BmbToDdrBridge test suite SHALL provide comprehensive testing of all major DRAM command types including ACTIVATE, PRECHARGE, READ, WRITE, and REFRESH commands through the DFI interface.

#### Scenario: ACTIVATE Command Testing
- **WHEN** BmbToDdrBridge processes an ACTIVATE command through DFI
- **AND** Target bank is currently idle
- **THEN** the test SHALL verify successful bank activation
- **AND** SHALL track row address assignment
- **AND** SHALL validate bank state transition from idle to active

#### Scenario: PRECHARGE Command Testing - Single Bank
- **WHEN** BmbToDdrBridge processes a PRECHARGE command for a single active bank
- **AND** All timing constraints (tRAS, tRTP) are satisfied
- **THEN** the test SHALL verify successful bank precharge
- **AND** SHALL validate bank state transition from active to idle
- **AND** SHALL confirm row address is reset

#### Scenario: PRECHARGE Command Testing - All Banks
- **WHEN** BmbToDdrBridge processes a PRECHARGE ALL command
- **AND** All active banks satisfy timing constraints
- **THEN** the test SHALL verify all banks are precharged
- **AND** SHALL validate all banks transition to idle state
- **AND** SHALL confirm all row addresses are reset

#### Scenario: READ Command Testing
- **WHEN** BmbToDdrBridge processes a READ command through DFI
- **AND** Target bank is properly activated
- **AND** tRCD timing constraint is satisfied
- **THEN** the test SHALL verify successful read data retrieval
- **AND** SHALL validate burst operation execution
- **AND** SHALL confirm proper column address handling

#### Scenario: WRITE Command Testing
- **WHEN** BmbToDdrBridge processes a WRITE command through DFI
- **AND** Target bank is properly activated
- **AND** tRCD timing constraint is satisfied
- **THEN** the test SHALL verify successful write data storage
- **AND** SHALL validate write data masking functionality
- **AND** SHALL confirm proper column address handling

#### Scenario: REFRESH Command Testing
- **WHEN** BmbToDdrBridge processes an AUTO REFRESH command through DFI
- **AND** All banks are in idle state
- **AND** tRFC timing constraint is satisfied from last refresh
- **THEN** the test SHALL verify refresh operation completion
- **AND** SHALL validate that all banks remain in idle state
- **AND** SHALL confirm refresh counter increment

### Requirement: Command Sequencing Validation
BmbToDdrBridge test suite SHALL validate proper DRAM command sequencing, ensuring that commands are executed in the correct order with appropriate timing constraints between related commands.

#### Scenario: Basic Activation-Read-Precharge Sequence
- **WHEN** BmbToDdrBridge executes ACTIVATE → READ → PRECHARGE sequence for same bank
- **AND** All inter-command timing constraints are satisfied
- **THEN** the test SHALL verify each command executes successfully
- **AND** SHALL validate proper state transitions (idle → active → idle)
- **AND** SHALL confirm correct data transfer timing

#### Scenario: Bank Interleaving Sequence
- **WHEN** BmbToDdrBridge executes commands for different banks in interleaved fashion
- **AND** tRRD timing constraint between bank activations is satisfied
- **THEN** the test SHALL verify parallel bank operations
- **AND** SHALL validate bank independence
- **AND** SHALL confirm no bank conflicts occur

#### Scenario: Refresh During Normal Operation
- **WHEN** BmbToDdrBridge processes a REFRESH command during normal operation
- **AND** All banks are properly precharged before refresh
- **THEN** the test SHALL verify refresh operation completes successfully
- **AND** SHALL validate that normal operation resumes correctly after refresh
- **AND** SHALL confirm refresh timing constraints are enforced

#### Scenario: Multi-Bank Activation Sequence
- **WHEN** BmbToDdrBridge activates multiple banks simultaneously
- **AND** tFAW (Four Activate Window) timing constraint is satisfied
- **THEN** the test SHALL verify all banks activate successfully
- **AND** SHALL validate tRRD timing between consecutive activations
- **AND** SHALL confirm tFAW window constraint compliance

### Requirement: Bank State Management Validation
BmbToDdrBridge test suite SHALL validate correct bank state management, ensuring that each DRAM bank maintains proper state information and transitions correctly between idle and active states.

#### Scenario: Bank State Tracking
- **WHEN** Multiple DRAM commands are processed for a specific bank
- **AND** Commands include both activation and precharge operations
- **THEN** the test SHALL accurately track bank state transitions
- **AND** SHALL validate current row address tracking
- **AND** SHALL confirm correct timing state information

#### Scenario: Bank Conflict Detection
- **WHEN** Conflicting commands are issued for the same bank
- **AND** Required timing constraints are not satisfied
- **THEN** the test SHALL detect bank conflicts
- **AND** SHALL validate appropriate conflict resolution
- **AND** SHALL confirm no data corruption occurs

#### Scenario: Independent Bank Operation
- **WHEN** Different banks are activated with different row addresses
- **AND** Commands are issued to different banks in parallel
- **THEN** the test SHALL verify independent bank operation
- **AND** SHALL validate that bank states are properly isolated
- **AND** SHALL confirm no cross-bank interference

### Requirement: Command Timing Enforcement Validation
BmbToDdrBridge test suite SHALL validate that all DRAM command timing constraints are properly enforced, ensuring that timing violations are detected and handled appropriately.

#### Scenario: tRCD Timing Violation Detection
- **WHEN** READ or WRITE command is issued immediately after ACTIVATE
- **AND** tRCD minimum timing constraint is violated
- **THEN** the test SHALL detect tRCD timing violation
- **AND** SHALL validate appropriate violation handling
- **AND** SHALL confirm no premature command execution

#### Scenario: tRP Timing Violation Detection
- **WHEN** ACTIVATE command is issued immediately after PRECHARGE
- **AND** tRP minimum timing constraint is violated
- **THEN** the test SHALL detect tRP timing violation
- **AND** SHALL validate appropriate violation handling
- **AND** SHALL confirm no premature bank activation

#### Scenario: tRAS Timing Violation Detection
- **WHEN** PRECHARGE command is issued immediately after ACTIVATE
- **AND** tRAS minimum timing constraint is violated
- **THEN** the test SHALL detect tRAS timing violation
- **AND** SHALL validate appropriate violation handling
- **AND** SHALL confirm no premature bank precharge

#### Scenario: tRRD Timing Violation Detection
- **WHEN** Two ACTIVATE commands are issued for different banks
- **AND** tRRD minimum timing constraint is violated
- **THEN** the test SHALL detect tRRD timing violation
- **AND** SHALL validate appropriate violation handling
- **AND** SHALL confirm proper bank activation sequencing

### Requirement: Mode Register Set Command Validation
BmbToDdrBridge test suite SHALL validate Mode Register Set (MRS) command functionality, ensuring that DDR mode registers can be properly programmed and that configuration changes take effect correctly.

#### Scenario: Basic MRS Command Testing
- **WHEN** BmbToDdrBridge processes a MODE REGISTER SET command
- **AND** Valid register address and data are provided
- **THEN** the test SHALL verify successful register programming
- **AND** SHALL validate that new settings take effect
- **AND** SHALL confirm register value persistence

#### Scenario: Extended Mode Register Testing
- **WHEN** BmbToDdrBridge processes an EXTENDED MODE REGISTER SET command
- **AND** Extended mode register parameters are specified
- **THEN** the test SHALL verify successful extended register programming
- **AND** SHALL validate extended mode functionality
- **AND** SHALL confirm compatibility with base mode settings

### Requirement: DRAM Command Error Handling
BmbToDdrBridge test suite SHALL validate error handling for invalid or illegal DRAM commands, ensuring that error conditions are properly detected and reported.

#### Scenario: Invalid Command Detection
- **WHEN** BmbToDdrBridge receives an invalid DRAM command
- **AND** Command format or parameters are illegal
- **THEN** the test SHALL detect command error
- **AND** SHALL validate appropriate error reporting
- **AND** SHALL confirm system stability is maintained

#### Scenario: Invalid Address Handling
- **WHEN** BmbToDdrBridge processes commands with invalid addresses
- **AND** Row, column, or bank addresses are out of range
- **THEN** the test SHALL detect address error
- **AND** SHALL validate appropriate error handling
- **AND** SHALL confirm no memory corruption occurs

#### Scenario: Bank State Violation Handling
- **WHEN** BmbToDdrBridge processes commands incompatible with current bank state
- **AND** Command conflicts with bank's current state
- **THEN** the test SHALL detect state violation
- **AND** SHALL validate appropriate error response
- **AND** SHALL confirm bank state integrity is maintained

### Requirement: DRAM Command Performance Validation
BmbToDdrBridge test suite SHALL validate that DRAM command execution maintains expected performance characteristics, ensuring that command throughput and latency meet design specifications.

#### Scenario: Command Throughput Testing
- **WHEN** BmbToDdrBridge processes high-volume DRAM commands
- **AND** Commands are issued at maximum sustainable rate
- **THEN** the test SHALL measure actual command throughput
- **AND** SHALL validate throughput meets or exceeds design targets
- **AND** SHALL confirm no command queuing overflow occurs

#### Scenario: Command Latency Validation
- **WHEN** Individual DRAM commands are processed
- **AND** Command execution time is measured from issue to completion
- **THEN** the test SHALL measure actual command latency
- **AND** SHALL validate latency meets design specifications
- **AND** SHALL confirm consistent latency behavior across multiple trials

#### Scenario: Parallel Command Execution Testing
- **WHEN** Multiple commands can be executed in parallel
- **AND** Commands target different banks or have compatible timing
- **THEN** the test SHALL validate parallel execution capability
- **AND** SHALL measure parallelism efficiency
- **AND** SHALL confirm no resource conflicts occur

