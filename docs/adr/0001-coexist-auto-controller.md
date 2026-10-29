# Coexist new auto controller alongside legacy semi-auto

We decided to introduce the fully-automatic Transfer Engine as a new independent module (`I2cAutoCtrl`) that coexists with the existing `I2cCtrl.driveI2cSlaveIo`, rather than replacing or extending it in place. Both share the same `I2cSlave` physical layer instance.

**Why**: Preserves backward compatibility for deployed SoCs using the semi-auto controller; isolates risk during development; allows SoC designers to instantiate either or both via generics without coupling their fates.

**Considered alternatives**:
- Replace in place (breaks existing users, no migration path)
- Extend existing module with an `autoEngine` generic flag (invades existing FSM, increases combinatorial complexity)
