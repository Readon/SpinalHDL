#!/bin/bash
# ============================================================================
# run_tc6.sh — TC6: multi-rank CS switching + data isolation (xsim 2023.1)
#
# Reuses the TC4 compile set (golden SerDes + custom delay-line models; the
# training path is quiet in this test). Top module: tc6_multi_rank.
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
UV="$XV/data/verilog/src/unisims"
SP="$XV/data/secureip"
WORK="$(cd "$(dirname "$0")" && pwd)"
export PATH="$XV/bin:$PATH"
cd "$WORK"

echo "===== Bootstrap vendor IP ====="
cp -f "$UV/OSERDESE3.v" "$WORK/OSERDESE3.v"
cp -f "$UV/ISERDESE3.v" "$WORK/ISERDESE3.v"
[ -d "$SP/oserdese3_k2" ] && cp -f "$SP/oserdese3_k2"/*.vp "$WORK/"
[ -d "$SP/iserdese3" ]    && cp -f "$SP/iserdese3"/*.vp  "$WORK/"

echo "===== xvlog ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | tr '\n' ' ')
xvlog glbl.v custom_modules.v \
  OSERDESE3.v ISERDESE3.v \
  custom_unisims_sim.v \
  $VPFILES UsPhy.v tc6_multi_rank.v 2>&1 | grep -iE "^ERROR|cannot find|syntax|multip" || true

echo "===== xelab ====="
xelab tc6_multi_rank -d . --debug typical 2>&1 | grep -iE "ERROR|Built|snapshot" || true

echo "===== xsim (timeout 180s) ====="
timeout 180 xsim tc6_multi_rank -R 2>&1 | grep -viE "Unisim.*RST|IDELAYCTRL|Copyright|SW Build|IP Build|WARNING: \[XSIM"
echo "===== done ====="