#!/bin/bash
# ============================================================================
# run_tc10.sh — TC10: end-to-end integration smoke (MC -> PHY -> SDRAM model)
# xsim 2023.1. Vendor IP bootstrapped like TC4: golden OSERDESE3/ISERDESE3,
# CUSTOM ODELAYE3/IDELAYE3 behavioral models (golden ones don't work in xsim).
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
echo "custom delay models: $(ls custom_unisims_sim.v 2>/dev/null || echo MISSING)"

echo "===== xvlog (custom ODELAYE3/IDELAYE3 SHADOW the golden ones) ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | tr '\n' ' ')
xvlog -sv glbl.v custom_modules.v custom_unisims_sim.v \
      OSERDESE3.v ISERDESE3.v \
      $VPFILES \
      Ddr4PinModel.v Ddr4SdramModel.v \
      UsPhy.v DfiController.v \
      tb_tc10_e2e.v

echo "===== xelab ====="
xelab tb_tc10_e2e -s tb_tc10_e2e -d . --debug typical

echo "===== xsim ====="
timeout 300 xsim tb_tc10_e2e -R 2>&1 | tee xsim_tc10.log
if grep -q "TC10_PASS" xsim_tc10.log; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "===== POSITIVE RUN FAILED ====="; exit 1
fi
