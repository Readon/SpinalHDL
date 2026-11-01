# Register bus as Bmb slave port with internal BmbSlaveFactory

We decided to expose the register interface of `I2cAutoMaster` and `I2cAutoSlave` as a standard **Bmb slave port** (`io.regBus`), with the register map instantiated **inside** the component via `BmbSlaveFactory(io.regBus)`. This replaces the earlier ad-hoc 5-wire flat bus (`addr/wdata/wen/re/rdata`) and satisfies the requirement that register access go through the house `BusSlaveFactory` machinery.

## Context

- ADR-0002 promised "BusSlaveFactory for register access (consistent with existing wrappers)". The first implementation shipped a hand-rolled 5-signal flat bus instead.
- The natural-looking fix — a legacy-style `def driveFrom(busCtrl: BusSlaveFactory, baseAddress)` called from the SoC top — **cannot work** for these components: the register map binds to deep internal signals (FSM state, FIFO pointers, command registers), and SpinalHDL's `PhaseCheckHierarchy` rejects any read of a child-internal signal from an Area elaborated in a parent component (75 hierarchy violations observed). The legacy `I2CSlave.driveFrom` only works because it binds exclusively to IO-port-level and locally-created signals.
- Therefore the factory must elaborate **inside** the owning component, which implies the bus itself is an IO port of that component.

## Decisions

- **`io.regBus = slave(Bmb(getBmbParameter()))`** on both controllers; parameter built by `BmbAccessParameter(8, 32).addSources(1, BmbSourceParameter(contextWidth = 0, lengthWidth = 3))` (8-bit address, 32-bit data, single source, masked writes).
- **Register map via `BmbSlaveFactory`** inside the component. Offsets are stable (master: CMD@0x00 … CTRL@0x38; slave: ADDR@0x00 … CFG_TIMEOUT@0x20).
- **Atomic CMD trigger**: CMD data fields (`write`) and the trigger pulse (`onWrite`) both take effect at the same `doWrite` handshake edge — the write-command-to-trigger contract of ADR-0002 is preserved by the factory's scheduling, not by coincidence.
- **Pop-on-read RX_DATA** (master 0x10): `read()` elements are assigned combinationally, so the returned data is the FIFO head *before* the `onRead` pop fires.
- **RAM indirect write** (slave 0x08): address/data staged into Wires (combinational), actual `ram.write` issued in `onWrite` — no extra register stage in the datapath.
- **Error semantics**: unmapped addresses yield `rsp.opcode = ERROR` (factory default), giving firmware a built-in register-space validation.
- **Demo top ties the port off** (`cmd.valid = 0`, `rsp.ready = 0`); testbenches drive the port with a small handshake helper (`bmbWrite`) and override the tie-offs via SpinalSim `#=`.

## Considered alternatives

- **External `driveFrom(factory, baseAddress)`** (legacy pattern): rejected — SpinalHDL hierarchy rules forbid a parent-elaborated Area from registering writes to child-internal signals (empirically verified: PhaseCheckHierarchy failure).
- **Flat custom 5-wire bus** (previous state): rejected — not the house bus convention, no error reporting, no handshake, and it forced testbenches to pulse `wen` manually.
- **Bmb port + factory instantiated in the SoC top**: rejected — same hierarchy problem as `driveFrom`; the factory's `Area` would still read child-internal signals.

## Consequences

- SoC integrators connect any Bmb master (APB3/AXI4-Lite bridge) to `io.regBus`; no per-peripheral glue.
- Testbenches must speak Bmb (valid/ready handshake) rather than single-cycle `wen` pulses; encapsulated in the `bmbWrite` helper.
- `BmbSourceParameter` must keep `canMask = true`: `BmbSlaveFactory.writeByteEnable()` references `bus.cmd.mask` unconditionally.
- Simulation note: Bmb input ports need elaboration-time drivers (tie-offs in the test top); `#=` overrides them at run time.
