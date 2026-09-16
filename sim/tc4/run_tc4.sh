#!/bin/bash
# ============================================================================
# run_tc4.sh — TC4: write-leveling closed-loop convergence (xsim 2023.1)
#
# KEY DIFFERENCE from TC1: ODELAYE3/IDELAYE3 are REPLACED by custom behavioral
# models (custom_unisims_sim.v) because the Xilinx golden models do not produce
# a measurable phase shift from CNTVALUEIN in xsim. OSERDESE3/ISERDESE3 still
# use the Xilinx golden models (they work correctly — proven by TC1).
#
# Therefore the compile set deliberately OMITS the golden ODELAYE3.v/IDELAYE3.v
# and includes custom_unisims_sim.v instead (module-name shadowing).
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
UV="$XV/data/verilog/src/unisims"
SP="$XV/data/secureip"
WORK="$(cd "$(dirname "$0")" && pwd)"
export PATH="$XV/bin:$PATH"
cd "$WORK"

echo "===== Bootstrap vendor IP (OSERDESE3/ISERDESE3 golden only) ====="
# Golden SerDes models (these WORK in xsim). Do NOT copy ODELAYE3/IDELAYE3.
cp -f "$UV/OSERDESE3.v" "$WORK/OSERDESE3.v"
cp -f "$UV/ISERDESE3.v" "$WORK/ISERDESE3.v"
[ -d "$SP/oserdese3_k2" ] && cp -f "$SP/oserdese3_k2"/*.vp "$WORK/"
[ -d "$SP/iserdese3" ]    && cp -f "$SP/iserdese3"/*.vp  "$WORK/"
echo ".vp present:"; ls *.vp 2>/dev/null | xargs -n1 basename || echo "(none)"
echo "custom delay models: $(ls custom_unisims_sim.v 2>/dev/null || echo MISSING)"

echo "===== xvlog (custom ODELAYE3/IDELAYE3 SHADOW the golden ones) ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | tr '\n' ' ')
xvlog glbl.v custom_modules.v \
  OSERDESE3.v ISERDESE3.v \
  custom_unisims_sim.v \
  $VPFILES UsPhy.v tc4_wl_convergence.v 2>&1 | grep -iE "^ERROR|cannot find|syntax|multip" || true

echo "===== xelab ====="
xelab tc4_wl_convergence -d . --debug typical 2>&1 | grep -iE "ERROR|Built|snapshot" || true

echo "===== xsim (timeout 180s) ====="
timeout 180 xsim tc4_wl_convergence -R 2>&1 | grep -viE "Unisim.*RST|IDELAYCTRL|Copyright|SW Build|IP Build|WARNING: \[XSIM"
echo "===== done ====="