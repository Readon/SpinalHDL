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
#   MC_FILE=...      swap the MC netlist         (default DfiController.v)
#   SEED=<n>         stress seed (default 42). Non-default seeds build a sed
#                    mutant tb_tc11_seed<n>.v (same module name) — the xvlog
#                    -define route turned out unreliable on the full file list.
# ============================================================================
set -e
XV=${XILINX_VIVADO:-/tools/Xilinx/Vivado/2023.1}
WORK="$(cd "$(dirname "$0")" && pwd)"
TB_FILE="${TB_FILE:-tb_tc11_stress.v}"
MODEL_FILE="${MODEL_FILE:-Ddr4SdramModel.v}"
PHY_FILE="${PHY_FILE:-UsPhy.v}"
MC_FILE="${MC_FILE:-DfiController.v}"
SEED="${SEED:-42}"
if [ "$SEED" != "42" ] && [ "${TB_FILE:-tb_tc11_stress.v}" = "tb_tc11_stress.v" ]; then
  sed "s/^  parameter SEED = 42;$/  parameter SEED = ${SEED};/" tb_tc11_stress.v > "tb_tc11_seed${SEED}.v"
  TB_FILE="tb_tc11_seed${SEED}.v"
  echo "SEED=${SEED} -> mutant TB ${TB_FILE}"
fi
# ---- DBI bridge (audit item 3): the mr5=0x1800 netlists gain one
# io_dfi_write_wr_p_wrdataDbiN output (MC) + one dfi_write_wr_p_wrdataDbiN
# input (PHY) each; a plain TB leaves the PHY input floating => X. Build a
# bridge copy of the TB that wires MC->PHY through two 4-bit nets.
if [ "${DBI:-0}" = "1" ]; then
  # Switch to the DBI netlists unless the caller explicitly chose otherwise.
  [ "$PHY_FILE" = "UsPhy.v" ] && PHY_FILE=UsPhy_dbi.v
  [ "$MC_FILE" = "DfiController.v" ] && MC_FILE=DfiController_dbi.v
  if [ ! -f tb_tc11_dbi.v ]; then
    python3 - tb_tc11_stress.v tb_tc11_dbi.v <<'PY'
import sys
inp, outp = sys.argv[1], sys.argv[2]
s = open(inp).read()
subs = [
  ("  wire [3:0]  dfi_wr_0_mask, dfi_wr_1_mask;",
   "  wire [3:0]  dfi_wr_0_mask, dfi_wr_1_mask;\n  wire [3:0]  dfi_wr_0_dbi, dfi_wr_1_dbi;  // DBI bridge (MC -> PHY)"),
  ("    .io_dfi_write_wr_0_wrdata(dfi_wr_0_data),",
   "    .io_dfi_write_wr_0_wrdata(dfi_wr_0_data),\n    .io_dfi_write_wr_0_wrdataDbiN(dfi_wr_0_dbi),"),
  ("    .io_dfi_write_wr_1_wrdata(dfi_wr_1_data),",
   "    .io_dfi_write_wr_1_wrdata(dfi_wr_1_data),\n    .io_dfi_write_wr_1_wrdataDbiN(dfi_wr_1_dbi),"),
  ("    .dfi_write_wr_0_wrdata(dfi_wr_0_data),",
   "    .dfi_write_wr_0_wrdata(dfi_wr_0_data),\n    .dfi_write_wr_0_wrdataDbiN(dfi_wr_0_dbi),"),
  ("    .dfi_write_wr_1_wrdata(dfi_wr_1_data),",
   "    .dfi_write_wr_1_wrdata(dfi_wr_1_data),\n    .dfi_write_wr_1_wrdataDbiN(dfi_wr_1_dbi),"),
  # DBI mode: model must interpret DM as DBI_n, not byte-enable.
  ("  Ddr4SdramModel #(.ECHO_DELAY_PS(2500)) mem (",
   "  Ddr4SdramModel #(.ECHO_DELAY_PS(2500), .USE_WRDBI(1)) mem ("),
]
for old, new in subs:
    assert s.count(old) == 1, old[:50]
    s = s.replace(old, new)
open(outp, 'w').write(s)
print("built", outp)
PY
  fi
  TB_FILE=tb_tc11_dbi.v
  echo "DBI=1 -> PHY=${PHY_FILE} MC=${MC_FILE} TB=${TB_FILE}"
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
xelab xwork.tb_tc11_stress -s tb_tc11_stress -d . -L xwork --debug typical

echo "===== xsim ====="
timeout 900 xsim tb_tc11_stress -R 2>&1 | tee xsim_tc11.log
if grep -q "TC11_PASS" xsim_tc11.log; then
  echo "===== POSITIVE RUN PASSED ====="
else
  echo "===== POSITIVE RUN FAILED ====="; exit 1
fi
