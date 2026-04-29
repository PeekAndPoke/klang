#!/usr/bin/env bash
#
# Runs DSP benchmarks (ignitors + effects) on JVM and Node.js, saves markdown results to docs/benchmarks/
# and emits a JVM-vs-JS comparison matrix in a separate compare file.
#
# Usage: ./console/run-dsp-benchmarks.sh
#

set -euo pipefail
cd "$(dirname "$0")/.."

DATETIME=$(date +"%Y-%m-%d_%H%M%S")
DOCS_DIR="docs/benchmarks"
mkdir -p "$DOCS_DIR"

# Filenames lead with the timestamp so plain alphabetical sort groups each run together
# (compare/jvm/nodejs adjacent under each datetime).
JVM_FILE="$DOCS_DIR/${DATETIME}_jvm.md"
NODE_FILE="$DOCS_DIR/${DATETIME}_nodejs.md"
COMPARE_FILE="$DOCS_DIR/${DATETIME}_compare.md"

echo "=== Running JVM benchmark ==="
./gradlew :audio_benchmark:jvmRun --quiet 2>/dev/null | sed -n '/^# Audio Benchmark/,$ p' > "$JVM_FILE"
echo "Saved: $JVM_FILE"

echo "=== Running Node.js benchmark ==="
./gradlew :audio_benchmark:jsNodeProductionRun --quiet 2>/dev/null | sed -n '/^# Audio Benchmark/,$ p' > "$NODE_FILE"
echo "Saved: $NODE_FILE"

echo "=== Building comparison matrix ==="
python3 - "$JVM_FILE" "$NODE_FILE" "$COMPARE_FILE" <<'PY'
import re
import sys
from pathlib import Path

jvm_path, node_path, out_path = (Path(p) for p in sys.argv[1:])

# Markdown emitted by runBenchmark contains two sections, each starting with a `# ...` heading
# followed by metadata bullets and a single pipe-table. We split on the headings and parse each
# section's table.

ROW_RE = re.compile(r"^\|\s*(.*?)\s*\|")  # capture first pipe-cell


def parse_sections(text: str) -> dict[str, dict[str, dict[str, float]]]:
    """Returns {section_title: {row_name: {col_name: value, ...}, ...}, ...}"""
    sections: dict[str, dict[str, dict[str, float]]] = {}
    cur_title: str | None = None
    cur_rows: dict[str, dict[str, float]] = {}
    cur_headers: list[str] | None = None

    for raw in text.splitlines():
        line = raw.rstrip()
        if line.startswith("# "):
            if cur_title is not None:
                sections[cur_title] = cur_rows
            cur_title = line[2:].strip()
            cur_rows = {}
            cur_headers = None
            continue
        if not line.startswith("|"):
            continue
        # divider line "|---|"
        if set(line.replace("|", "").replace(":", "").replace("-", "").strip()) == set():
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if cur_headers is None:
            cur_headers = cells
            continue
        if not cells:
            continue
        name = cells[0]
        row: dict[str, float] = {}
        for header, cell in zip(cur_headers[1:], cells[1:]):
            try:
                row[header] = float(cell)
            except ValueError:
                row[header] = float("nan")
        cur_rows[name] = row

    if cur_title is not None:
        sections[cur_title] = cur_rows
    return sections


def fmt(v: float, decimals: int = 6) -> str:
    if v != v:  # NaN
        return "—"
    return f"{v:.{decimals}f}"


def fmt_ratio(jvm: float, js: float) -> str:
    if jvm != jvm or js != js or jvm == 0:
        return "—"
    return f"{js / jvm:.2f}x"


jvm_sections = parse_sections(jvm_path.read_text())
node_sections = parse_sections(node_path.read_text())

# Find the matching section pairs by title.
section_titles = list(jvm_sections.keys())

lines: list[str] = []
lines.append(f"# JVM vs Node.js Benchmark Comparison")
lines.append("")
lines.append(f"- **JVM source:** `{jvm_path.name}`")
lines.append(f"- **Node.js source:** `{node_path.name}`")
lines.append("")
lines.append("RTF = render time / audio time. Lower is better. **JS/JVM ratio** > 1 means JS is slower than JVM "
             "(expected); ratio < 1 means JVM is slower than JS (red flag).")
lines.append("")

for title in section_titles:
    jvm_rows = jvm_sections.get(title, {})
    node_rows = node_sections.get(title, {})
    if not jvm_rows and not node_rows:
        continue
    lines.append(f"## {title}")
    lines.append("")
    lines.append("| Name | JVM RTF | Node.js RTF | JS/JVM ratio | JVM µs/block | Node.js µs/block |")
    lines.append("|------|--------:|------------:|-------------:|-------------:|-----------------:|")
    # Rows ordered by JVM RTF descending (worst first); fall back to Node.js name set
    all_names = sorted(
        set(jvm_rows.keys()) | set(node_rows.keys()),
        key=lambda n: -jvm_rows.get(n, {}).get("RTF", float("-inf")),
    )
    for name in all_names:
        j = jvm_rows.get(name, {})
        n = node_rows.get(name, {})
        j_rtf = j.get("RTF", float("nan"))
        n_rtf = n.get("RTF", float("nan"))
        j_us = j.get("Render µs/block", float("nan"))
        n_us = n.get("Render µs/block", float("nan"))
        lines.append(
            f"| {name} | {fmt(j_rtf)} | {fmt(n_rtf)} | {fmt_ratio(j_rtf, n_rtf)} | "
            f"{fmt(j_us, 4)} | {fmt(n_us, 4)} |"
        )
    lines.append("")

# Summary: any cases where JVM is slower than JS (ratio < 1)?
lines.append("## Red flags (JVM slower than Node.js)")
lines.append("")
slow_lines: list[str] = []
for title in section_titles:
    jvm_rows = jvm_sections.get(title, {})
    node_rows = node_sections.get(title, {})
    for name in sorted(set(jvm_rows.keys()) & set(node_rows.keys())):
        j_rtf = jvm_rows[name].get("RTF", float("nan"))
        n_rtf = node_rows[name].get("RTF", float("nan"))
        if j_rtf > n_rtf and n_rtf > 0 and j_rtf > 0:
            slow_lines.append(f"- **{title} / {name}**: JVM RTF {fmt(j_rtf)} vs Node.js {fmt(n_rtf)} "
                              f"(JVM is {j_rtf / n_rtf:.2f}x slower)")
if slow_lines:
    lines.extend(slow_lines)
else:
    lines.append("None — JVM is at least as fast as Node.js on every case.")
lines.append("")

out_path.write_text("\n".join(lines))
print(f"Saved: {out_path}")
PY

echo
echo "Done. Results in $DOCS_DIR/"
echo "  JVM:     $JVM_FILE"
echo "  Node.js: $NODE_FILE"
echo "  Compare: $COMPARE_FILE"
