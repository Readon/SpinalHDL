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
TB_FILE="${TB_FILE:-tb_tc10_e2e.v}"  # negative proofs: TB_FILE=tb_tc10_e2e_cut.v
MODEL_FILE="${MODEL_FILE:-Ddr4SdramModel.v}"  # negative proofs: MODEL_FILE=Ddr4SdramModel_cut.v
PHY_FILE="${PHY_FILE:-UsPhy.v}"  # negative proofs: PHY_FILE=UsPhy_cut.v
export PATH="$XV/bin:$PATH"
cd "$WORK"

echo "custom delay models: $(ls custom_unisims_sim.v 2>/dev/null || echo MISSING)"

echo "===== xvlog (custom SerDes/delay models replace golden+VIP) ====="
# Golden OSERDESE3/ISERDESE3 wrappers + their SIP .vp files are intentionally
# ABSENT: custom_unisims_sim.v provides silicon-semantics behavioral models
# under the same module names (Plan A: expel the inverted-CLKDIV sim hack).
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | grep -vi 'oserdese3\|iserdese3' | tr '\n' ' ')
xvlog -sv glbl.v custom_modules.v custom_unisims_sim.v \
      $VPFILES \
      Ddr4PinModel.v $MODEL_FILE \
      $PHY_FILE DfiController.v \
      $TB_FILE

echo "===== xelab ====="
xelab tb_tc10_e2e -s tb_tc10_e2e -d . --debug typical

echo "===== xsim ====="
timeout 300 xsim tb_tc10_e2e -R 2>&1 | tee xsim_tc10.log
if grep -q "TC10_PASS" xsim_tc10.log; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "===== POSITIVE RUN FAILED ====="; exit 1
fi
