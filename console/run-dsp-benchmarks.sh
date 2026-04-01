#!/usr/bin/env bash
#
# Runs DSP benchmarks on JVM and Node.js, saves markdown results to docs/benchmarks/
#
# Usage: ./console/run-dsp-benchmarks.sh
#

set -euo pipefail
cd "$(dirname "$0")/.."

DATETIME=$(date +"%Y-%m-%d_%H%M%S")
DOCS_DIR="docs/benchmarks"
mkdir -p "$DOCS_DIR"

echo "=== Running JVM benchmark ==="
JVM_FILE="$DOCS_DIR/dsp_${DATETIME}_jvm.md"
./gradlew :audio_benchmark:jvmRun --quiet 2>/dev/null | sed -n '/^# Audio Benchmark/,$ p' > "$JVM_FILE"
echo "Saved: $JVM_FILE"

echo "=== Running Node.js benchmark ==="
NODE_FILE="$DOCS_DIR/dsp_${DATETIME}_nodejs.md"
./gradlew :audio_benchmark:jsNodeProductionRun --quiet 2>/dev/null | sed -n '/^# Audio Benchmark/,$ p' > "$NODE_FILE"
echo "Saved: $NODE_FILE"

echo
echo "Done. Results in $DOCS_DIR/"
