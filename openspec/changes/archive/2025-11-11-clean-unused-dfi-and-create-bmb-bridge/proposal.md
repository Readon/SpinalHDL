## Why

Current codebase contains multiple DDR DFI PHY implementations creating maintenance burden and code duplication, while existing BMB-to-DDR bridge implementations have compilation issues and incomplete test coverage. Need to streamline codebase to focus on XilinxUSPhy as the primary PHY implementation and provide a robust BMB-to-DDR bridge supporting DDR3 and DDR4 standards.

## What Changes

- **Remove unused DFI PHY implementations**: Clean up DfiDdrPhy and related test files that duplicate functionality already provided by XilinxUSPhy
- **Preserve XilinxUSPhy ecosystem**: Keep all XilinxUSPhy-related design and test code which provides complete DFI 3.1 compliant PHY implementation
- **Create robust BMB-to-DDR bridge**: Implement new clean BmbToDdrBridge component that bridges BMB bus to DDR interface through XilinxUSPhy
- **Fix broken test infrastructure**: Repair and consolidate test code, ensuring comprehensive coverage for bridge functionality
- **Add DDR3/DDR4 support**: Ensure bridge module properly supports both DDR3 and DDR4 memory standards through XilinxUSPhy integration

## Impact

- **Affected specs**: dfi-ddr3-phy (MODIFIED), potentially new specs for bmb-ddr-bridge capability
- **Affected code**:
  - Removal: `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/DfiDdrPhy.scala` and related files
  - Preservation: All `lib/src/main/scala/spinal/lib/memory/sdram/dfi/phy/XilinxUSPhy*` files
  - Creation: New `lib/src/main/scala/spinal/lib/memory/sdram/dfi/BmbToDdrBridge.scala`
  - Updates: Enhanced `lib/src/main/scala/spinal/lib/memory/sdram/dfi/DfiController.scala`
- **Test impact**: Clean up duplicate DFI PHY tests, consolidate and fix bridge/DDR integration tests