#!/bin/bash
# ============================================================================
# run_tc13.sh — TC13: BMB full-path test (multi-source / backpressure / masked
# writes, MC -> PHY -> SDRAM model)
#
# Three sequential phases after the standard TC11-style preamble
# (init + write leveling + INITCHK/INITCHK2):
#   P1 MULTI-SOURCE: 2 BMB sources (sourceWidth=1) sharing one address space,
#      256 random R/W txns per source; scoreboard: last-writer-wins content,
#      rsp context echo, per-(source,context) rsp count conservation.
#   P2 BACKPRESSURE: 8 rounds of rsp.ready held low ~100 refclk cycles while
#      cmds keep streaming; no deadlock (watchdog), no loss/dup.
#   P3 MASKED WRITES: 8 addresses x 6 mask classes; enabled bytes take the
#      new pattern, disabled bytes keep the old value (model DM byte-enable).
#
# Knobs (negative proofs / reproducibility):
#   TB_FILE=...      swap the testbench          (default tb_tc13_stress.v)
#   MODEL_FILE=...   swap the SDRAM model        (default Ddr4SdramModel.v)
#   PHY_FILE=...     swap the PHY netlist        (default UsPhy.v)
#   MC_FILE=...      swap the MC netlist         (default DfiController.v)
#   SEED=<n>         stress seed (default 42). Non-default seeds build a sed
#                    mutant tb_tc13_seed<n>.v (same module name).
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
WORK="$(cd "$(dirname "$0")" && pwd)"
TB_FILE="${TB_FILE:-tb_tc13_stress.v}"
MODEL_FILE="${MODEL_FILE:-Ddr4SdramModel.v}"
PHY_FILE="${PHY_FILE:-UsPhy.v}"
MC_FILE="${MC_FILE:-DfiController.v}"
SEED="${SEED:-42}"
if [ "$SEED" != "42" ] && [ "${TB_FILE:-tb_tc13_stress.v}" = "tb_tc13_stress.v" ]; then
  sed "s/^  parameter SEED = 42;$/  parameter SEED = ${SEED};/" tb_tc13_stress.v > "tb_tc13_seed${SEED}.v"
  TB_FILE="tb_tc13_seed${SEED}.v"
  echo "SEED=${SEED} -> mutant TB ${TB_FILE}"
fi
export PATH="$XV/bin:$PATH"
cd "$WORK"

echo "custom delay models: $(ls custom_unisims_sim.v 2>/dev/null || echo MISSING)"

echo "===== xvlog (custom SerDes/delay models replace golden+VIP) ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | grep -vi 'oserdese3\|iserdese3' | tr '\n' ' ')
# -work xwork: explicit NON-default library name. Vivado 2023.1 xvlog on this host
# livelocks (100% CPU, 0-byte log) when the work dir is literally named 'work'
# (4/4 spin) but completes in <1s with any other name (3/3). xelab must match.
xvlog -sv -work xwork glbl.v custom_modules.v custom_unisims_sim.v \
      $VPFILES \
      Ddr4PinModel.v $MODEL_FILE \
      $PHY_FILE $MC_FILE \
      $TB_FILE

echo "===== xelab ====="
xelab xwork.tb_tc13_stress -s tb_tc13_stress -d . -L xwork --debug typical

echo "===== xsim ====="
timeout 900 xsim tb_tc13_stress -R 2>&1 | tee xsim_tc13.log
if grep -q "TC13_PASS" xsim_tc13.log; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "===== POSITIVE RUN FAILED ====="; exit 1
fi
