## MODIFIED Requirements

### Requirement: DFI 3.1完整接口支持
The XilinxUSPhy component SHALL implement the complete DFI 3.1 specification including all interface groups and signals required for full DDR memory controller operation.

#### Scenario: DFI Control Interface Completeness
- **WHEN** DfiController sends control signals through dfi interface
- **AND** all DFI control signals are present (address, bank, ras_n, cas_n, we_n, cs_n, cke, odt, reset_n, act_n)
- **THEN** PHY SHALL correctly decode and process all control signals
- **AND** generate appropriate DDR command sequences

#### Scenario: DFI Data Interface Completeness
- **WHEN** DfiController transmits read/write data through dfi interface
- **AND** using all data interface signals (wrdata, wrdata_en, wrdata_mask, rddata, rddata_valid)
- **THEN** PHY SHALL properly serialize write data and deserialize read data
- **AND** handle data masking and enable signals correctly

#### Scenario: DFI Training Interface Completeness
- **WHEN** DfiController initiates training operations
- **AND** using all training interface signals (wrlvl_*, rdlvl_*, calvl_*)
- **THEN** PHY SHALL execute complete training sequences
- **AND** provide training response signals back to controller

### Requirement: Full DDR Command Generation
The XilinxUSPhy component SHALL generate all required DDR commands with correct timing and signal levels for JEDEC-compliant operation.

#### Scenario: Basic DDR Commands
- **WHEN** DfiController requests DDR operations (ACT, READ, WRITE, PRE)
- **AND** with proper timing constraints
- **THEN** PHY SHALL generate correct command signal combinations
- **AND** maintain JEDEC-specified timing relationships

#### Scenario: Advanced DDR Commands
- **WHEN** DfiController requests advanced operations (REF, MRS, ZQCS)
- **AND** with device-specific parameters
- **THEN** PHY SHALL generate correct command encodings
- **AND** handle multi-chip select and bank addressing

### Requirement: Multi-Device DDR Control
The XilinxUSPhy component SHALL support control of multiple DDR devices with proper chip select and rank management.

#### Scenario: Multi-Chip Select Operation
- **WHEN** system contains multiple DDR devices
- **AND** DfiController specifies target chip select
- **THEN** PHY SHALL route commands to correct device
- **AND** manage per-device timing and control signals

#### Scenario: Rank Management
- **WHEN** DDR devices have multiple ranks
- **AND** DfiController manages rank-specific operations
- **THEN** PHY SHALL handle rank-to-rank timing constraints
- **AND** maintain proper rank activation states

### Requirement: Frequency Ratio Support
The XilinxUSPhy component SHALL support DFI frequency ratios (1:1, 1:2, 1:4) for different memory controller configurations.

#### Scenario: 1:2 Frequency Ratio Operation
- **WHEN** DfiController operates at 1:2 frequency ratio
- **AND** DDR operates at half the controller frequency
- **THEN** PHY SHALL handle phase-specific signals correctly
- **AND** maintain proper data alignment across phases

#### Scenario: 1:4 Frequency Ratio Operation
- **WHEN** DfiController operates at 1:4 frequency ratio
- **AND** DDR operates at quarter the controller frequency
- **THEN** PHY SHALL manage four-phase command/data sequences
- **AND** ensure correct burst data ordering

### Requirement: Complete Training and Calibration
The XilinxUSPhy component SHALL implement comprehensive training and calibration procedures for reliable high-speed DDR operation.

#### Scenario: Write Leveling Training
- **WHEN** write leveling training is initiated
- **AND** DQS-DQ timing needs alignment
- **THEN** PHY SHALL perform write leveling sequence
- **AND** adjust DQS delay lines for optimal timing

#### Scenario: Read Training Sequence
- **WHEN** read training is required
- **AND** for both gate and eye training
- **THEN** PHY SHALL execute complete read training procedures
- **AND** optimize read data capture timing

#### Scenario: CA Training
- **WHEN** command/address training is needed
- **AND** for LPDDR devices requiring CA calibration
- **THEN** PHY SHALL perform CA training sequence
- **AND** align command/address timing

### Requirement: Proper Initialization Sequence
The XilinxUSPhy component SHALL implement complete DDR device initialization sequence according to JEDEC specifications.

#### Scenario: Power-Up Initialization
- **WHEN** DDR devices power up
- **AND** require initialization sequence
- **THEN** PHY SHALL execute proper power-up timing
- **AND** handle CKE and reset sequencing

#### Scenario: Mode Register Programming
- **WHEN** DDR devices require configuration
- **AND** through mode register writes
- **THEN** PHY SHALL program all required mode registers (MR0-MR3)
- **AND** verify programming completion

### Requirement: Data Path Integrity
The XilinxUSPhy component SHALL maintain data integrity through complete read/write data paths with proper timing control.

#### Scenario: Write Data Path
- **WHEN** writing data to DDR devices
- **AND** with proper DQS/DQ timing
- **THEN** PHY SHALL serialize data correctly
- **AND** generate appropriate DQS strobes and DM signals

#### Scenario: Read Data Path
- **WHEN** reading data from DDR devices
- **AND** with proper capture timing
- **THEN** PHY SHALL deserialize data correctly
- **AND** handle DQS gating and delay alignment

### Requirement: Clock and Timing Management
The XilinxUSPhy component SHALL provide proper clock generation and timing control for DDR operation.

#### Scenario: DDR Clock Generation
- **WHEN** DDR devices require clock signals
- **AND** with proper phase alignment
- **THEN** PHY SHALL generate differential clock pairs
- **AND** control clock enable/disable states

#### Scenario: Timing Constraint Management
- **WHEN** DDR operations have timing requirements
- **AND** JEDEC specifications define constraints
- **THEN** PHY SHALL enforce all timing constraints
- **AND** prevent illegal command sequences