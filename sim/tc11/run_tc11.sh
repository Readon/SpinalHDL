#!/bin/bash
# ============================================================================
# run_tc11.sh — TC11: randomized end-to-end stress (MC -> PHY -> SDRAM model)
#
# 512 WRITE + 512 READ Bmb bursts, random bank/row/col/rank, content-addressed
# payloads, exact per-byte roundtrip check, pin-model legality monitor.
#
# Knobs (negative proofs / reproducibility):
#   TB_FILE=...      swap the testbench          (default tb_tc11_stress.v)
#   MODEL_FILE=...   swap the SDRAM model        (default Ddr4SdramModel.v)
#   PHY_FILE=...     swap the PHY netlist        (default UsPhy.v)
#   SEED is a Verilog parameter of the TB (default 42); pass a mutant TB for
#   a different seed, or edit the parameter in a copied TB.
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
WORK="$(cd "$(dirname "$0")" && pwd)"
TB_FILE="${TB_FILE:-tb_tc11_stress.v}"
MODEL_FILE="${MODEL_FILE:-Ddr4SdramModel.v}"
PHY_FILE="${PHY_FILE:-UsPhy.v}"
export PATH="$XV/bin:$PATH"
cd "$WORK"

echo "custom delay models: $(ls custom_unisims_sim.v 2>/dev/null || echo MISSING)"

echo "===== xvlog (custom SerDes/delay models replace golden+VIP) ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | grep -vi 'oserdese3\|iserdese3' | tr '\n' ' ')
xvlog -sv glbl.v custom_modules.v custom_unisims_sim.v \
      $VPFILES \
      Ddr4PinModel.v $MODEL_FILE \
      $PHY_FILE DfiController.v \
      $TB_FILE

echo "===== xelab ====="
xelab tb_tc11_stress -s tb_tc11_stress -d . --debug typical

echo "===== xsim ====="
timeout 900 xsim tb_tc11_stress -R 2>&1 | tee xsim_tc11.log
if grep -q "TC11_PASS" xsim_tc11.log; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "===== POSITIVE RUN FAILED ====="; exit 1
fi
