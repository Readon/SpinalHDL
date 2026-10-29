#!/bin/bash
# ============================================================================
# run_tc9.sh — TC9: Ddr4SdramModel self-test (command-driven behavioral SDRAM,
# no PHY), xsim 2023.1. No vendor IP needed (pure behavioral model).
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
export PATH="$XV/bin:$PATH"
cd "$(dirname "$0")"

echo "===== xvlog ====="
xvlog -sv glbl.v Ddr4PinModel.v Ddr4SdramModel.v tb_tc9_sdram_model.v
echo "===== xelab ====="
xelab tb_tc9_sdram_model -s tb_tc9_sdram_model
echo "===== xsim ====="
timeout 120 xsim tb_tc9_sdram_model -R 2>&1 | tee xsim_tc9.log
if grep -q "TC9_PASS" xsim_tc9.log; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "===== POSITIVE RUN FAILED ====="; exit 1
fi
