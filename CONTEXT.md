# I2C Auto Controller

Fully-automatic I2C master and slave peripheral for MCU SoCs, coexisting with the legacy semi-auto controller.

## Language

**Physical Layer** (the sole ground truth):
The `I2cSlave` component — owns SCL/SDA timing, START/STOP detection, debounce, and timeout. Emits bit-level `I2cSlaveBus` (CMD/RSP). All upper layers sit atop this single primitive.
_Avoid_: "PHY", "driver", "bottom layer"

**Transfer Engine**:
The new fully-automatic protocol orchestrator that converts a single software-configured transfer descriptor (address, direction, length) into a sequence of bit-level commands on the `I2cSlaveBus`. Owns the TX/RX FIFOs and the internal state machine.
_Avoid_: "controller", "FIFO engine", "auto FSM"

**Legacy Controller** (semi-auto):
The existing `I2cCtrl.driveI2cSlaveIo` — a memory-mapped register layer where software manually drives each byte and manages START/STOP timing. Remains unchanged.
_Avoid_: "old controller", "half-auto", "manual mode"

**FIFO Depth**:
The number of bytes buffered in TX or RX before back-pressure halts the transfer. Fixed at 64 bytes for this design. No DMA interface (future consideration).
_Avoid_: "buffer size", "queue depth"

**Transfer Descriptor**:
The minimal set of parameters (up to two phases, each with address, R/W direction, byte count) that software writes once to initiate a complete I2C transaction. Phase 2 (if present) is separated by a hardware-managed RESTART. No further CPU intervention needed until completion.
_Avoid_: "command word", "setup packet"

**Sparse Register Map** (slave side):
The slave's internal memory is a parameterized set of non-contiguous byte addresses. Unused addresses are simply absent; the hardware NACKs any access to them. The auto-increment pointer advances linearly (+1) and NACKs on gap.
_Avoid_: "register file", "memory map" (too generic)

**Address Width**:
Initial implementation supports 7-bit I2C addresses only. A class-parameter flag (`support10bit: Boolean = false`) reserves the extension point for 10-bit addressing without changing the interface shape.
_Avoid_: "generics" (Verilog term; SpinalHDL uses class constructor parameters)
