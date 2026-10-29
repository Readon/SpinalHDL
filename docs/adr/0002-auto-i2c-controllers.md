# Fully-automatic I2C controller as coexisting components

We decided to add two new independent components (`I2cAutoMaster` and `I2cAutoSlave`) that provide fully-automatic I2C operation, coexisting alongside the existing semi-auto `I2cSlave` + `driveI2cSlaveIo` stack. Both share the same `I2cSlave` physical-layer primitive.

## Decisions

- **Coexistence, not replacement**: the existing semi-auto controller is untouched; new components are additive.
- **Two separate components** (not one with a role parameter): a pin pair is either master or slave, never both.
- **64-byte TX/RX FIFOs** (master); **sparse internal RAM ≤64B** (slave). No DMA (deferred).
- **Atomic composite transfers** (master): a single command register write initiates up to two phases separated by a hardware-managed RESTART.
- **Write-command-registers-to-trigger**: the act of writing the command register IS the trigger; no separate GO bit.
- **Error handling**: address NACK → immediate STOP; data NACK → configurable (abort or continue); timeout → 9-pulse bus recovery; arbitration loss → passive (no STOP).
- **Sparse register map** (slave): parameterized set of non-contiguous addresses; gaps NACK. Auto-increment pointer advances +1.
- **Dual-port slave RAM**: accessible via both the register bus (firmware preload/debug) and the I2C interface (external master).
- **7-bit addresses only** initially; 10-bit reserved via class parameter.
- **BusSlaveFactory** for register access (consistent with existing wrappers).
- **Maskable multi-source interrupts** aggregated onto a single IRQ line.

## Considered alternatives

- Single component with role enum (rejected: DUAL mode is meaningless on one pin pair; couples master/slave iteration cycles)
- Zero-copy memory-mapped data path (rejected: overkill for MCU peripheral, adds bus master port)
- Multi-line IRQ (rejected: wastes SoC interrupt resources; single line + status register is standard)
