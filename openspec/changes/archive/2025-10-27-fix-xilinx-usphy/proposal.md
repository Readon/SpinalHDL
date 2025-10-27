## Why

The XilinxUSPhy implementation has significant functional gaps that prevent it from serving as a complete, DFI-compliant DDR PHY capable of controlling multiple DDR SDRAM devices. Key issues include incomplete DFI interface support, missing DDR command generation, improper data path handling, and inadequate training/calibration logic. The current implementation lacks essential features required for real DDR memory controller operation.

## What Changes

- **Complete DFI 3.1 interface implementation**: Add full support for all DFI interface groups (control, data, training, status, update)
- **Implement proper DDR command generation**: Add correct DDR command encoding and timing for all operations (ACT, READ, WRITE, PRE, REF, MRS, ZQCS)
- **Fix data path timing and control**: Resolve data serialization/deserialization issues and ensure proper DQ/DQS/DM signal handling
- **Enhance training and calibration**: Implement complete write leveling, read gate training, and read eye training with proper DFI training interface compliance
- **Add multi-chip select support**: Enable control of multiple DDR devices with proper chip select handling
- **Implement frequency ratio support**: Add support for 1:1, 1:2, and 1:4 frequency ratios as required by DFI specification
- **Add proper initialization sequence**: Implement complete DDR power-up and initialization sequence with mode register programming

**BREAKING**: No breaking changes - these are functional enhancements that complete the intended PHY implementation.

## Impact

- **Affected specs**: dfi-ddr3-phy capability (completes DFI compliance and DDR control functionality)
- **Affected code**:
  - `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy.scala` - Major functional enhancements for complete DFI/DDR support
- **User impact**: Users will get a fully functional Xilinx UltraScale DDR PHY capable of controlling DDR SDRAM devices with complete DFI 3.1 compliance