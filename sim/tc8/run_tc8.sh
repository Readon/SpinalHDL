#!/bin/bash
# ============================================================================
# run_tc8.sh — TC8: pin-level command decode closed loop (L3), xsim 2023.1
#
# Compile set mirrors TC4/TC7 (custom ODELAYE3/IDELAYE3 behavioral models
# shadow the golden ones; OSERDESE3/ISERDESE3 golden models are proven-good).
# Adds Ddr4PinModel.v (the L3 pin-level command decoder).
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
UV="$XV/data/verilog/src/unisims"
SP="$XV/data/secureip"
WORK="$(cd "$(dirname "$0")" && pwd)"
export PATH="$XV/bin:$PATH"
cd "$WORK"

echo "===== Bootstrap vendor IP (OSERDESE3/ISERDESE3 golden only) ====="
cp -f "$UV/OSERDESE3.v" "$WORK/OSERDESE3.v"
cp -f "$UV/ISERDESE3.v" "$WORK/ISERDESE3.v"
[ -d "$SP/oserdese3_k2" ] && cp -f "$SP/oserdese3_k2"/*.vp "$WORK/"
[ -d "$SP/iserdese3" ]    && cp -f "$SP/iserdese3"/*.vp  "$WORK/"
echo "UsPhy.v: $(stat -c '%y' UsPhy.v | cut -d. -f1)"
grep -q "pads_ras_n" UsPhy.v || { echo "FATAL: UsPhy.v lacks pads_ras_n — regenerate: sbt 'tester/Test/runMain spinal.lib.memory.sdram.dfi.phy.UsPhyGen'"; exit 1; }

echo "===== xvlog ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | tr '\n' ' ')
xvlog glbl.v custom_modules.v \
  OSERDESE3.v ISERDESE3.v \
  custom_unisims_sim.v \
  $VPFILES UsPhy.v Ddr4PinModel.v tc8_pin_model.v 2>&1 | grep -iE "^ERROR|cannot find|syntax|multip" || true

echo "===== xelab ====="
xelab tc8_pin_model -d . --debug typical 2>&1 | grep -iE "ERROR|Built|snapshot" || true

echo "===== xsim (timeout 180s) ====="
OUT=$(timeout 180 xsim tc8_pin_model -R 2>&1)
echo "$OUT" | grep -E "TC8_|FAIL \[" || true

if echo "$OUT" | grep -q "TC8_PASS"; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "POSITIVE RUN FAILED"
  exit 1
fi
echo "===== done ====="
