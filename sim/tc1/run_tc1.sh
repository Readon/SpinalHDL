#!/bin/bash
# ============================================================================
# run_tc1.sh — Reproducible TC1 SerDes-data-path simulation (xsim 2023.1)
# Bootstraps Xilinx vendor IP from the Vivado install, then runs
# xvlog -> xelab -> xsim for tb_tc1 (WRITE numerical + READ structural).
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
WORK="$(cd "$(dirname "$0")" && pwd)"
UV="$XV/data/verilog/src/unisims"
SP="$XV/data/secureip"
cd "$WORK"

echo "===== Bootstrap vendor IP (unisims .v + secureip .vp) ====="
# Plaintext unisim models
for m in OSERDESE3 ISERDESE3 ODELAYE3 IDELAYE3; do
  cp -f "$UV/$m.v" "$WORK/$m.v"
done
# Secureip .vp (RSA-encrypted, parsed by xvlog). Need the ULTRASCALE (K2)
# variant for OSERDESE3 and the iserdese3/ dir for ISERDESE3.
[ -d "$SP/oserdese3_k2" ] && cp -f "$SP/oserdese3_k2"/*.vp "$WORK/"
[ -d "$SP/iserdese3" ]    && cp -f "$SP/iserdese3"/*.vp  "$WORK/"
echo ".vp present:"; ls *.vp | xargs -n1 basename

echo "===== xvlog ====="
VPFILES=$(ls "$WORK"/*.vp | tr '\n' ' ')
"$XV/bin/xvlog" glbl.v custom_modules.v \
  OSERDESE3.v ISERDESE3.v ODELAYE3.v IDELAYE3.v \
  $VPFILES UsPhy.v tb_tc1.v 2>&1 | grep -iE "^ERROR|cannot find|syntax" || true

echo "===== xelab ====="
"$XV/bin/xelab" tb_tc1 -d . --debug typical 2>&1 | grep -iE "ERROR|Built|snapshot" || true

echo "===== xsim ====="
timeout 120 "$XV/bin/xsim" tb_tc1 -R 2>&1 | grep -viE "Unisim.*RST|IDELAYCTRL|Copyright|SW Build|IP Build"
echo "===== done ====="