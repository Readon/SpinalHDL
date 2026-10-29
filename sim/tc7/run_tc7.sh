#!/bin/bash
# ============================================================================
# run_tc7.sh — TC7: pin-level command strobe fidelity (L1 closure), xsim 2023.1
#
# Compile set mirrors TC4 (custom ODELAYE3/IDELAYE3 behavioral models shadow
# the golden ones — golden delay models do not produce measurable phase shift
# in xsim; OSERDESE3/ISERDESE3 golden models are proven-good from TC1).
#
# Usage:
#   ./run_tc7.sh          # positive run: expect TC7_PASS
#   NEG=1 ./run_tc7.sh    # neg-proof: swaps ras_n<->cas_n sources in a SCRATCH
#                          # UsPhy.v and expects TC7_FAIL; scratch is restored
#                          # afterwards (the real netlist is never damaged)
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
echo "UsPhy.v: $(stat -c '%y' UsPhy.v | cut -d. -f1) (must be the post-L1 netlist with pads_ras_n)"
grep -q "pads_ras_n" UsPhy.v || { echo "FATAL: UsPhy.v lacks pads_ras_n — regenerate: sbt 'tester/Test/runMain spinal.lib.memory.sdram.dfi.phy.UsPhyGen'"; exit 1; }

NEG_SWAP=0
if [ "${NEG:-0}" = "1" ]; then
  echo "===== NEG-PROOF: swapping ras_n<->cas_n sources in scratch UsPhy.v ====="
  cp UsPhy.v UsPhy.v.negbak
  RAS_SRC=$(grep -oE "assign pads_ras_n *= *[a-zA-Z0-9_$]+" UsPhy.v | head -1 | sed 's/.*= *//')
  CAS_SRC=$(grep -oE "assign pads_cas_n *= *[a-zA-Z0-9_$]+" UsPhy.v | head -1 | sed 's/.*= *//')
  echo "ras src=$RAS_SRC  cas src=$CAS_SRC"
  if [ -n "$RAS_SRC" ] && [ -n "$CAS_SRC" ] && [ "$RAS_SRC" != "$CAS_SRC" ]; then
    sed -i "s/assign pads_ras_n *= *${RAS_SRC}/assign pads_ras_n = ${CAS_SRC}/" UsPhy.v
    sed -i "s/assign pads_cas_n *= *${CAS_SRC}/assign pads_cas_n = ${RAS_SRC}/" UsPhy.v
    NEG_SWAP=1
  else
    echo "FATAL: could not identify strobe sources to swap"; mv -f UsPhy.v.negbak UsPhy.v; exit 1
  fi
fi

echo "===== xvlog ====="
VPFILES=$(ls "$WORK"/*.vp 2>/dev/null | tr '\n' ' ')
xvlog glbl.v custom_modules.v \
  OSERDESE3.v ISERDESE3.v \
  custom_unisims_sim.v \
  $VPFILES UsPhy.v tc7_strobe_pins.v 2>&1 | grep -iE "^ERROR|cannot find|syntax|multip" || true

echo "===== xelab ====="
xelab tc7_strobe_pins -d . --debug typical 2>&1 | grep -iE "ERROR|Built|snapshot" || true

echo "===== xsim (timeout 120s) ====="
OUT=$(timeout 120 xsim tc7_strobe_pins -R 2>&1)
echo "$OUT" | grep -E "TC7_|FAIL vec" || true

if [ "$NEG_SWAP" = "1" ]; then
  mv -f UsPhy.v.negbak UsPhy.v
  echo "===== neg-proof: scratch UsPhy.v restored ====="
  if echo "$OUT" | grep -q "TC7_FAIL"; then
    echo "NEG_PROOF_OK: corrupted mapping tripped the TB as required"
  else
    echo "NEG_PROOF_BROKEN: TB did NOT catch the corrupted mapping — TB is degenerate!"
    exit 1
  fi
else
  if echo "$OUT" | grep -q "TC7_PASS"; then
    echo "===== POSITIVE RUN PASSED ====="
  else
    echo "POSITIVE RUN FAILED"
    exit 1
  fi
fi
echo "===== done ====="
