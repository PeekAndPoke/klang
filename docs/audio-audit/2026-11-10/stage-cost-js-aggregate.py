#!/usr/bin/env python3
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Aggregate N Kotlin/JS stage-cost runs (JSON) into stage-cost-js.{txt,json} and compare with the JVM JSON.

usage: aggregate_cost.py <jvm.json> <out_dir> --timing run1.json run2.json ... [--alloc a1.json a2.json ...]
"""
import json
import math
import statistics
import sys


def num(v):
    if isinstance(v, str):
        return {"NaN": math.nan, "Infinity": math.inf, "-Infinity": -math.inf}[v]
    return float(v)


def jnum(x):
    if isinstance(x, (int,)) and not isinstance(x, bool):
        return x
    if math.isnan(x):
        return "NaN"
    if math.isinf(x):
        return "Infinity" if x > 0 else "-Infinity"
    return x


def key(r):
    return (r["family"], r["stage"], r["knobs"])


def med(xs):
    xs = [x for x in xs if not math.isnan(x)]
    return statistics.median(xs) if xs else math.nan


def f(x, d):
    if x == 0:
        x = 0.0  # no "-0"
    if isinstance(x, float) and math.isnan(x):
        return "NaN"
    if isinstance(x, float) and math.isinf(x):
        return "inf" if x > 0 else "-inf"
    t = f"{x:.{d}f}"
    return t[1:] if t.startswith("-") and set(t[1:]) <= set("0.") else t


def table(header, cells):
    widths = [max(len(header[c]), max((len(r[c]) for r in cells), default=0)) for c in range(len(header))]
    line = lambda vals: "  ".join(v.ljust(widths[i]) for i, v in enumerate(vals)).rstrip()
    out = [line(header), "  ".join("-" * w for w in widths)]
    out += [line(r) for r in cells]
    return "\n".join(out) + "\n"


def main():
    args = sys.argv[1:]
    jvm_path, out_dir = args[0], args[1]
    rest = args[2:]
    timing, alloc = [], []
    cur = None
    for a in rest:
        if a == "--timing":
            cur = timing
        elif a == "--alloc":
            cur = alloc
        else:
            cur.append(a)

    runs = [json.load(open(p)) for p in timing]
    allocs = [json.load(open(p)) for p in alloc]
    jvm = json.load(open(jvm_path))
    jvm_by = {key(r): r for r in jvm}
    assert len(jvm_by) == len(jvm), "JVM keys not unique"

    keys = [key(r) for r in runs[0]]
    by_run = [{key(r): r for r in run} for run in runs]
    alloc_by = [{key(r): r for r in run} for run in allocs]
    rows = []
    for k in keys:
        rs = [b[k] for b in by_run]
        g = lambda field: [num(r[field]) for r in rs]
        ratios = g("ratioToSine")
        nets = g("netNsPerSample")
        ratio_med = med(ratios)
        spread = (max(ratios) - min(ratios)) / abs(ratio_med) if ratio_med not in (0.0,) and not math.isnan(ratio_med) else math.nan
        net_med = med(nets)
        net_spread = (max(nets) - min(nets)) / abs(net_med) if net_med != 0 and not math.isnan(net_med) else math.nan
        a_rows = [b[k] for b in alloc_by if k in b]
        alloc_med = med([num(r["allocBytesPerBlock"]) for r in a_rows]) if a_rows else math.nan
        alloc_net = med([num(r["netAllocBytesPerBlock"]) for r in a_rows]) if a_rows else math.nan
        alloc_net_runs = [num(r["netAllocBytesPerBlock"]) for r in a_rows]
        rows.append({
            "key": k,
            "family": k[0], "stage": k[1], "knobs": k[2],
            "baseline": rs[0]["baseline"],
            "net": net_med,
            "net_runs": nets,
            "net_spread": net_spread,
            "rt": med(g("realtimeShare")),
            "ratio": ratio_med,
            "ratio_runs": ratios,
            "ratio_spread": spread,
            "raw": med(g("nsPerSample")),
            "raw_min": min(g("nsPerSampleMin")),
            "raw_max": max(g("nsPerSampleMax")),
            "x_ctrl": med(g("ratioToControl")),
            "build": med(g("buildMicros")),
            "alloc": alloc_med,
            "alloc_net": alloc_net,
            "alloc_net_runs": alloc_net_runs,
            "rounds": rs[0]["rounds"],
        })

    # rank by the median net cost, NaN last (the Kotlin sort)
    rows.sort(key=lambda r: (math.isnan(r["net"]), -r["net"] if not math.isnan(r["net"]) else 0))
    for i, r in enumerate(rows):
        r["rank"] = i + 1

    def stability(r):
        s = r["ratio_spread"]
        if math.isnan(s):
            return "-"
        if abs(r["ratio"]) < 0.5:
            return "noise"
        if s <= 0.10:
            return "stable"
        if s <= 0.25:
            return "fair"
        return "unstable"

    sine = next(r for r in rows if r["family"] == "source" and r["stage"] == "sine" and r["knobs"] == "bare")

    # ---- stage-cost-js.txt
    head = open(timing[0].replace(".json", ".txt")).read().split("\nColumns:")[0].rstrip("\n").splitlines()
    hdr = [
        "Stage cost table, Kotlin/JS (KLANG-00420, probe 7): the median of %d node processes" % len(runs),
        "Each process is one full run of the JVM table's method (below, from the first run's header); a row's value here is the",
        "median over the processes of that process's own median, so one process's JIT mode cannot carry a row.",
        "Quote the RATIOS to the plain sine and the RANKING, not the nanoseconds: node's spread between processes is large.",
        "Plain sine (source, bare): net %.2f ns per sample (process medians %s)." % (sine["net"], ", ".join(f(x, 2) for x in sine["net_runs"])),
        "",
        "Commands (from the repository root; B is the production test bundle's folder):",
        "  export JAVA_HOME=/opt/dev/sdkman/candidates/java/21.0.12-amzn",
        "  console/with-build-lock.sh ./gradlew :audio_be:compileTestProductionExecutableKotlinJs",
        "  B=.build-claude/audio_be/compileSync/js/test/testProductionExecutable/kotlin   # build/ for the maintainer",
        "  for i in 1 2 3 4 5; do (cd $B && KLANG_PROBE_OUT=$SP/run$i.txt node -e \"require('./klang-engine-audio_be-test.js')\\",
        "      .io.peekandpoke.klang.audio_be.probes.runStageCostJs(); process.exit(0)\"); done",
        "  for i in 1 2 3; do (cd $B && KLANG_PROBE_JS_ALLOC=1 KLANG_PROBE_OUT=$SP/alloc$i.txt node --max-semi-space-size=64 \\",
        "      --min-semi-space-size=64 -e \"require('./klang-engine-audio_be-test.js').io.peekandpoke.klang.audio_be.probes.runStageCostJs(); process.exit(0)\"); done",
        "  python3 docs/audio-audit/2026-11-10/stage-cost-js-aggregate.py docs/audio-audit/2026-11-10/stage-cost.json <out dir> \\",
        "      --timing $SP/run1.json ... $SP/run5.json --alloc $SP/alloc1.json $SP/alloc2.json $SP/alloc3.json",
        "Runs of this table: 5 timing processes one after another, then 3 allocation processes, unpinned, node 22.12.0, on a",
        "machine shared with other sessions (load average 4.5 to 6.5 during the runs). Each process took about 20 s.",
        "",
        "First run's header:",
    ] + ["  " + h for h in head[1:]]
    hdr += [
        "",
        "Columns:",
        "  net_ns    ns per sample net of the row's baseline control, median over the processes (each a median over 41 rounds)",
        "  rt_%      net_ns as a share of one sample period at 48 kHz (100 = one whole core for this alone)",
        "  x_sine    net_ns over the plain sine's net_ns, the median over the processes of each process's own ratio",
        "  x_sine runs  each process's ratio, in run order",
        "  spread_%  (max - min) / median of the per-process x_sine",
        "  stab      stable (spread up to 10 %), fair (up to 25 %), unstable (more), noise (x_sine under 0.5: read no ratio)",
        "  raw_ns    ns per sample with the harness, median over the processes; min and max over every round of every process",
        "  x_ctrl    raw_ns over the baseline control's raw_ns, median over the processes",
        "  build_us  the short render (build and first block), median over the processes",
    ]
    if allocs:
        hdr += [
            "  net_B     V8 new-space bytes per block above the baseline control's, median of %d allocation-mode processes" % len(allocs),
            "            (KLANG_PROBE_JS_ALLOC=1, --max-semi-space-size=64 --min-semi-space-size=64); per-process values beside it",
        ]
    hdr += ["  baseline  the control row subtracted", ""]
    header = ["#", "family", "stage", "knobs", "net_ns", "rt_%", "x_sine", "x_sine runs", "spread_%", "stab", "raw_ns", "min", "max", "x_ctrl", "build_us"]
    if allocs:
        header += ["net_B", "net_B runs"]
    header += ["baseline"]
    cells = []
    for r in rows:
        c = [str(r["rank"]), r["family"], r["stage"], r["knobs"], f(r["net"], 2), f(r["rt"] * 100, 3), f(r["ratio"], 2),
             "/".join(f(x, 2) for x in r["ratio_runs"]), f(r["ratio_spread"] * 100, 0), stability(r), f(r["raw"], 2), f(r["raw_min"], 2),
             f(r["raw_max"], 2), f(r["x_ctrl"], 2), f(r["build"], 1)]
        if allocs:
            c += [f(r["alloc_net"], 1), "/".join(f(x, 0) for x in r["alloc_net_runs"])]
        c += [r["baseline"] or "-"]
        cells.append(c)
    txt = "\n".join(hdr) + "\n" + table(header, cells) + "\n%d rows, %d processes\n" % (len(rows), len(runs))
    open(out_dir + "/stage-cost-js.txt", "w").write(txt)

    # ---- stage-cost-js.json: the JVM row fields, plus platform; and two fields of the aggregation
    jrows = []
    for r in rows:
        jrows.append({
            "rank": r["rank"], "family": r["family"], "stage": r["stage"], "knobs": r["knobs"], "baseline": r["baseline"],
            "netNsPerSample": jnum(r["net"]), "realtimeShare": jnum(r["rt"]), "ratioToSine": jnum(r["ratio"]),
            "nsPerSample": jnum(r["raw"]), "nsPerSampleMin": jnum(r["raw_min"]), "nsPerSampleMax": jnum(r["raw_max"]),
            "ratioToControl": jnum(r["x_ctrl"]), "buildMicros": jnum(r["build"]),
            "allocBytesPerBlock": jnum(r["alloc"]), "netAllocBytesPerBlock": jnum(r["alloc_net"]),
            "rounds": r["rounds"], "platform": "js",
            "processes": len(runs), "ratioToSineSpread": jnum(r["ratio_spread"]),
        })
    with open(out_dir + "/stage-cost-js.json", "w") as fh:
        fh.write("[\n  " + ",\n  ".join(json.dumps(x, ensure_ascii=False).replace('":', '": ', 0) for x in jrows) + "\n]\n")

    # ---- compare
    jvm_rank = {key(r): r["rank"] for r in jvm}
    comp = []
    for r in rows:
        j = jvm_by.get(r["key"])
        if j is None:
            continue
        jr = num(j["ratioToSine"])
        comp.append({
            "r": r, "jvm_ratio": jr, "jvm_rank": jvm_rank[r["key"]], "jvm_net": num(j["netNsPerSample"]),
            "rank_diff": jvm_rank[r["key"]] - r["rank"],
            "log_ratio": math.log2(r["ratio"] / jr) if (r["ratio"] > 0 and jr > 0) else math.nan,
        })
    missing = [k for k in jvm_by if k not in {c["r"]["key"] for c in comp}]

    def sig(c):
        # a row is read only when it costs at least half a sine on one platform, and its JS ratio is not unstable
        return c["r"]["family"] != "control" and max(c["r"]["ratio"], c["jvm_ratio"]) >= 0.5 and stability(c["r"]) != "unstable"

    by_ratio = sorted([c for c in comp if sig(c) and not math.isnan(c["log_ratio"])], key=lambda c: -abs(c["log_ratio"]))
    by_rank = sorted([c for c in comp if sig(c)], key=lambda c: -abs(c["rank_diff"]))
    jsx = sine["net"]
    jvm_sine = next(num(j["netNsPerSample"]) for j in jvm if j["family"] == "source" and j["stage"] == "sine" and j["knobs"] == "bare")

    def crow(c):
        r = c["r"]
        return [r["family"], r["stage"], r["knobs"], f(c["jvm_ratio"], 2), str(c["jvm_rank"]), f(r["ratio"], 2), str(r["rank"]),
                f(r["ratio"] / c["jvm_ratio"] if c["jvm_ratio"] else math.nan, 2), "%+d" % c["rank_diff"], stability(r)]

    ch = ["family", "stage", "knobs", "jvm_x_sine", "jvm_rank", "js_x_sine", "js_rank", "js/jvm", "rank_move", "js_stab"]
    out = [
        "Stage cost, JVM against Kotlin/JS (KLANG-00420, probe 7)",
        "JVM: stage-cost.txt (one process, 41 rounds); JS: stage-cost-js.txt (median of %d node processes, production bundle)." % len(runs),
        "Both quoted as the ratio to each platform's own plain sine (JVM sine %.2f ns, JS sine %.2f ns per sample: JS's sine is %.2fx the JVM's)." % (jvm_sine, jsx, jsx / jvm_sine),
        "js/jvm = js_x_sine over jvm_x_sine: above 1 the row costs MORE on JS, relative to a sine, than on the JVM.",
        "rank_move = jvm_rank - js_rank: positive means the row climbs (costs more, relatively) on JS.",
        "Rows read in the two lead sections cost at least half a sine on one platform, are not unstable on JS, and are not controls.",
        "",
        "== The rows whose RATIO differs most (by |log2(js/jvm)|), top 25 ==",
        "",
        table(ch, [crow(c) for c in by_ratio[:25]]),
        "== The rows whose RANK differs most, top 25 ==",
        "",
        table(ch, [crow(c) for c in by_rank[:25]]),
        "== Every row, sorted by JS cost ==",
        "",
        table(ch, [crow(c) for c in comp]),
    ]
    if allocs:
        al = [c for c in comp if c["r"]["family"] != "control" and not math.isnan(c["r"]["alloc_net"]) and abs(c["r"]["alloc_net"]) >= 1.0]
        ah = ["family", "stage", "knobs", "jvm_net_B", "js_net_B (median)", "js_net_B per process", "baseline"]
        arows = [[c["r"]["family"], c["r"]["stage"], c["r"]["knobs"], f(num(jvm_by[c["r"]["key"]]["netAllocBytesPerBlock"]), 1),
                  f(c["r"]["alloc_net"], 1), "/".join(f(x, 1) for x in c["r"]["alloc_net_runs"]), c["r"]["baseline"] or "-"] for c in al]
        out += [
            "== Allocation: the rows with a net byte count per block of 1 or more on either platform (JS: V8 new space, %d processes) ==" % len(allocs),
            "The harness copies with copyInto, which makes a typed-array view per call on JS (96 B): the controls carry it, and so does",
            "the duck's row, whose adapter copies the sidechain too (2 views = 192 B): that row's bytes are the HARNESS, not the duck.",
            "",
            table(ah, arows),
        ]
    if missing:
        out.append("JVM rows with no JS row: %s\n" % missing)
    open(out_dir + "/stage-cost-compare.txt", "w").write("\n".join(out))

    # summary for the report
    print("JS sine net", jsx, "JVM sine net", jvm_sine)
    print("TOP25 JS")
    for r in rows[:25]:
        print(r["rank"], r["family"], r["stage"], r["knobs"], f(r["ratio"], 2), stability(r), f(r["ratio_spread"] * 100, 0))
    print("TOP15 RANK MOVES")
    for c in by_rank[:15]:
        print(crow(c))
    print("TOP15 RATIO MOVES")
    for c in by_ratio[:15]:
        print(crow(c))
    stab = {}
    for r in rows:
        stab[stability(r)] = stab.get(stability(r), 0) + 1
    print("stability counts", stab)


if __name__ == "__main__":
    main()
