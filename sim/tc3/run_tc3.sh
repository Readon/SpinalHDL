#!/bin/bash
# ============================================================================
# run_tc3.sh — TC3: continuous multi-frame read deserialization (xsim 2023.1)
#
# Shares the TC4 compile set (golden SerDes + custom delay-line shadow models).
# Vendor IP is bootstrapped from the Vivado install; only our own sources are
# committed (see .gitignore whitelist).
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
UV="$XV/data/verilog/src/unisims"
SP="$XV/data/secureip"
WORK="$(cd "$(dirname "$0")" && pwd)"
export PATH="$XV/bin:$PATH"
export SIM_DEVICE=ULTRASCALE
cd "$WORK"

echo "===== Bootstrap vendor IP ====="
cp -f "$UV/OSERDESE3.v" "$WORK/OSERDESE3.v"
cp -f "$UV/ISERDESE3.v" "$WORK/ISERDESE3.v"
[ -d "$SP/oserdese3_k2" ] && cp -f "$SP/oserdese3_k2"/*.vp "$WORK/"
[ -d "$SP/iserdese3" ]    && cp -f "$SP/iserdese3"/*.vp  "$WORK/"
# Generated netlist is shared across the sim dirs (single source of truth).
[ -f UsPhy.v ] || cp -f ../../UsPhy.v "$WORK/UsPhy.v"
# Shared sim sources live in sim/tc4 (SSOT); borrow, do not duplicate.
SHARED="$WORK/../tc4"
for f in custom_modules.v custom_unisims_sim.v glbl.v; do
  [ -f "$f" ] || cp -f "$SHARED/$f" "$WORK/$f"
done

echo "===== xvlog ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | tr '\n' ' ')
xvlog glbl.v custom_modules.v \
  OSERDESE3.v ISERDESE3.v \
  custom_unisims_sim.v \
  $VPFILES UsPhy.v tc3_bl8_read.v 2>&1 | grep -iE "^ERROR|cannot find|syntax|multip" || true

echo "===== xelab ====="
xelab tc3_bl8_read -d . --debug typical 2>&1 | grep -iE "ERROR|Built|snapshot" || true

echo "===== xsim (timeout 180s) ====="
timeout 180 xsim tc3_bl8_read -R 2>&1 | grep -viE "Unisim.*RST|IDELAYCTRL|Copyright|SW Build|IP Build|WARNING: \[XSIM"
echo "===== done ====="