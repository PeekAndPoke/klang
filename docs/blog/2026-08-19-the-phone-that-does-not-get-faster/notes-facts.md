# Fairphone 4 as fixed performance target — fact sheet

## Part A — the phone and the desktop, verified online

### Fairphone 4

- Release date: October 25, 2021 (announced September 30, 2021). Sources: [GSMArena](https://m.gsmarena.com/fairphone_4-11136.php), [Wikipedia](https://en.wikipedia.org/wiki/Fairphone_4) — both agree.
- SoC: Qualcomm SM7225 Snapdragon 750G 5G — CONFIRMED. Sources: [GSMArena](https://m.gsmarena.com/fairphone_4-11136.php), [Wikipedia](https://en.wikipedia.org/wiki/Fairphone_4).
- CPU cores: octa-core, 2x Cortex-A77 (Kryo 570 Gold) at 2.2 GHz + 6x Cortex-A55 (Kryo 570 Silver) at 1.8 GHz. Source: [GSMArena](https://m.gsmarena.com/fairphone_4-11136.php) ("Octa-core (2x2.2 GHz Kryo 570 & 6x1.8 GHz Kryo 570)"), cross-checked against [cpu-monkey](https://www.cpu-monkey.com/en/cpu-qualcomm_snapdragon_750g) (Cortex-A77/A55 core mapping and same clocks, via search index).
- Process node: 8 nm. Sources: [GSMArena](https://m.gsmarena.com/fairphone_4-11136.php), [cpu-monkey](https://www.cpu-monkey.com/en/cpu-qualcomm_snapdragon_750g) (SoC announced 2020-09-23, 8nm, via search index).
- RAM options: 6 GB (with 128 GB storage) or 8 GB (with 256 GB storage). Sources: [GSMArena](https://m.gsmarena.com/fairphone_4-11136.php), [Wikipedia](https://en.wikipedia.org/wiki/Fairphone_4) ("6 or 8 GB") — both agree.
- Cortex-A77 cache (ARM documentation, configurable-implementation options): L1 128 KiB per core, fixed (64 KiB instruction cache with parity + 64 KiB data cache); L2 256–512 KiB per core, configurable. Source: [Wikipedia ARM Cortex-A77](https://en.wikipedia.org/wiki/ARM_Cortex-A77) (citing ARM's Technical Reference Manual; ARM's own developer.arm.com TRM page redirected and could not be fetched directly — see note below).
- Cortex-A55 cache (ARM documentation, configurable-implementation options): L1 32–128 KB per core, configurable (16–64 KB instruction cache with parity + 16–64 KB data cache); L2 64–256 KB, configurable (64/128/256 KB options, 4-way set associative, 64 B lines). Source: [Wikipedia ARM Cortex-A55](https://en.wikipedia.org/wiki/ARM_Cortex-A55) (citing ARM's Technical Reference Manual; same access caveat as above).
  - Note: Qualcomm does not publish which end of ARM's configurable cache range it chose for the 750G's A77/A55 clusters; the figures above are ARM's documented options, not a Qualcomm-confirmed exact value — flag this as an open point for the post rather than a stated Snapdragon 750G number.
- Browser engines on the device: Android Chrome runs on the V8 JavaScript engine (also used by Node.js); Firefox for Android runs on SpiderMonkey. Sources: [nodejs.org](https://nodejs.org/learn/getting-started/the-v8-javascript-engine), [Wikipedia SpiderMonkey](https://en.wikipedia.org/wiki/SpiderMonkey).

### AMD Ryzen 9 PRO 7940HS (the desktop/laptop these benchmarks run on)

- Cores/threads: 8 cores / 16 threads, Zen 4 "Phoenix" architecture. Source: [Notebookcheck](https://www.notebookcheck.net/AMD-Ryzen-9-PRO-7940HS-Processor-Benchmarks-and-Specs.725627.0.html).
- Clocks: base 4.0 GHz, boost up to 5.2 GHz (single core). Source: [Notebookcheck](https://www.notebookcheck.net/AMD-Ryzen-9-PRO-7940HS-Processor-Benchmarks-and-Specs.725627.0.html); AMD's own product page (amd.com) times out on direct fetch, but is indexed with the same figures per web search.
- L1 cache: 512 KB total across 8 cores = 32 KB instruction + 32 KB data per core. Source: [Notebookcheck](https://www.notebookcheck.net/AMD-Ryzen-9-PRO-7940HS-Processor-Benchmarks-and-Specs.725627.0.html), corroborated by [Wikipedia Zen 4](https://en.wikipedia.org/wiki/Zen_4) (via search index: "64 KB L1 per core, 32 KB instructions and 32 KB data").
- L2 cache: 8 MB total = 1 MB per core. Source: [Notebookcheck](https://www.notebookcheck.net/AMD-Ryzen-9-PRO-7940HS-Processor-Benchmarks-and-Specs.725627.0.html), corroborated by [Wikipedia Zen 4](https://en.wikipedia.org/wiki/Zen_4) (via search index).
- L3 cache: 16 MB, shared across the 8-core complex (not further split per core in this generation's mobile Phoenix design). Source: [Notebookcheck](https://www.notebookcheck.net/AMD-Ryzen-9-PRO-7940HS-Processor-Benchmarks-and-Specs.725627.0.html).
- Process node: TSMC 4 nm FinFET. Source: [Notebookcheck](https://www.notebookcheck.net/AMD-Ryzen-9-PRO-7940HS-Processor-Benchmarks-and-Specs.725627.0.html).
- Caveat: AMD's own amd.com product pages for both the 7940HS and the PRO 7940HS timed out on direct WebFetch (tried twice); the figures above come from a direct, successful fetch of Notebookcheck's spec page, cross-checked by web-search snippets that quote AMD's page and by Wikipedia's Zen 4 architecture page. No disagreement found between these sources.

---

## Part B — the timeline, from the repository

All hits from `grep -rn "Fairphone\|FF4" docs audio DEV-DIARY.MD` (audio/ had zero hits; DEV-DIARY.MD had zero hits — every mention lives under docs/). Dates in the "date" column are the in-text date when the sentence carries one; where a line carries no date, the date is the commit date from `git blame -L` / `git log -S"Fairphone" -- docs` (marked †) and the milestone it is closest to is noted.

| Date | What was measured or observed (verbatim, short) | file:line |
|---|---|---|
| 2026-08-19 | "ON-DEVICE RESULT (2026-08-19, Fairphone 4, D0..D1b deployed): HALF OF DER SCHMETTERLING RUNS AGAIN." | docs/plans/unified-eq.md:225 |
| 2026-08-20 | "🎉 ON-DEVICE GOAL REACHED (2026-08-20, Fairphone 4)" / "Der Schmetterling runs smoothly on the FF4 at ~75% CPU" | docs/plans/unified-eq.md:808-809 |
| 2026-08-23 † | "...that item should land before the Fairphone perf push." | docs/plans/filter-unification.md:497 |
| 2026-08-31 † | "The plan's own CPU goal is already met (Fairphone 4, ~75%, 2026-08-20), so nothing forces it." | docs/plans/unified-eq.md:761 |
| 2026-08-31 † | "...the plan's CPU goal is already met (Fairphone 4, ~75%), so..." | docs/tasks/by-ear/README.md:100 |
| 2026-09-03 † | "measure on the Fairphone — the stutter should already be gone" (2b's proof column) | docs/plans/resource-warehouse.md:197 |
| 2026-09-03 † | "...Fairphone measurement still owed." | docs/plans/resource-warehouse.md:198 |
| 2026-09-04 | "Measured on the Fairphone with 2a–2g in: the first run of Der Schmetterling still killed the playback." | docs/plans/resource-warehouse.md:212,214 |
| 2026-09-04 | "Step 4 — the warmup vocabulary (2026-09-04, second Fairphone measurement)" | docs/plans/resource-warehouse.md:321 |
| 2026-09-04 | "Der Schmetterling on the Fairphone, three measurements on 2026-09-04:" ... "the warmup now works on the Fairphone" | docs/tasks/future/first-run-spike-v2.md:7,19 |
| 2026-09-04 | "Fairphone, fourth measurement (2026-09-04): the warmup works — the first run of Der Schmetterling plays." | docs/plans/resource-warehouse.md:417 |
| 2026-09-04 † | "Fairphone: the resource stutter is gone; a cold-code spike on the first run was the last symptom, answered by the vocabulary (`da002b73`), measurement pending." | docs/tasks/_v1-scope.md:39 |
| 2026-09-15 | "After the 2026-09-15 CPU round (voice culling, the polynomial sine, `fastExp2`/`fastExp`) the FF4 can barely run Der Schmetterling again." | docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md:40-41 |
| 2026-09-15 | "Not bit-identical, judged by ear, and the phone went from "barely" to "smooth" that evening." | docs/plans/blog-optimization-series.md:527 |
| 2026-09-16 † | "buffer is 1 KB and L1-resident on the desktop and on the Fairphone 4 alike, so this is not memory..." | docs/tasks/future/affine-chain-fusion.md:32 |
| 2026-09-16 † | "The device before/after (the Fairphone 4) is the maintainer's at the next deploy..." | docs/tasks/future/ignitor-optimizer-open-items.md:113 |
| 2026-09-16 † | "series `series-fairphone`, 16 posts ... the Fairphone 4 as the non-moving target" | docs/blog/BACKLOG.md:22 |
| 2026-09-16 † | "...with the Fairphone 4 as the non-moving target" / "What did the phone say? The Fairphone 4 (Snapdragon 750G, 2 Cortex-A77 + 6 Cortex-A55, ...) is where the songs must play." | docs/plans/blog-optimization-series.md:4,17 |
| 2026-09-16 † | "...runs smoothly on the FF4 at about 75 % CPU", the plan's goal." (retrospective restatement of the 2026-08-20 goal) | docs/plans/blog-optimization-series.md:412 |
| 2026-09-16 † | "Four Fairphone measurements in two days, each moving the stall somewhere else until it was gone." (retrospective of the 2026-09-03/04 measurements) | docs/plans/blog-optimization-series.md:435-436 |

† = no date in the line itself; dated via `git blame -L` (all fall on commits also returned by `git log --format="%h %ad %s" --date=short -S"Fairphone" -- docs`, reproduced below).

### git log -S"Fairphone" -- docs (commits that touch a "Fairphone" mention, newest first)

```
9b9ce82e 2026-09-16 docs: plan for the optimization blog series, the Fairphone as the non-moving target
adb31c61 2026-09-16 docs: the optimizer stream archived, its open items moved to future
e861b139 2026-09-16 Park the Affine chain fusion with its reasoning (future, last in line)
b9565f67 2026-09-15 Polynomial e^x in the envelope curve and the compressor gain, pinned 2^x ends
7874fab2 2026-09-04 docs: the warmup works on the Fairphone; the gauge spike is the warmup paying cold work in silence
5f95192c 2026-09-04 docs: first-run spike v2, measure before guessing again
94a386e8 2026-09-04 docs: V1 scope, warehouse and D4 done
da002b73 2026-09-04 warmup: a vocabulary that executes every ignitor node kind before the first song
0354c9da 2026-09-04 resource warehouse: cylinders join it, and a bucketed 16-orbit warmup stocks the shelves
b990f2c4 2026-09-04 resource warehouse, step 2g: a sample whose PCM cannot be allocated is silent, not fatal
ef33ccd4 2026-09-03 resource warehouse, step 2b: a delay ring exists only once a voice asks for one
55f3bcaf 2026-09-03 docs: the resource warehouse is designed — right-size first, shelve what returns
37bb8c5e 2026-08-31 songs planing
8fc7993b 2026-08-23 C0.1: compound colon-parameters become per-param functions, both doors
aa3437ca 2026-08-23 Remove klangblocks — one surface fewer to keep consistent
```

Note: the earliest text-dated mention (2026-08-19, unified-eq.md:225) and the 2026-09-15 "can barely run" line were both committed as part of *later* commits (aa3437ca on 2026-08-23; b9565f67 on 2026-09-15, filed under the pre-archive name `docs/tasks/ignitor-optimizer-followups.md`) — the doc records history retrospectively, so its commit date can trail the event date it names. Use the in-text date, not the commit date, whenever both exist.

### The five requested milestones, as the docs state them

1. **First on-device result — "half of Der Schmetterling runs again"** (2026-08-19): docs/plans/unified-eq.md:225-226 — "ON-DEVICE RESULT (2026-08-19, Fairphone 4, D0..D1b deployed): HALF OF DER SCHMETTERLING RUNS AGAIN. Stall now only at the lead's entry..."
2. **Goal reached (~75% CPU)** (2026-08-20): docs/plans/unified-eq.md:808-809 — "### 🎉 ON-DEVICE GOAL REACHED (2026-08-20, Fairphone 4)" / "**Der Schmetterling runs smoothly on the FF4 at ~75% CPU** — the plan's opening goal, met."
3. **Resource-warehouse device measurements, four, 2026-09-03/04**: docs/plans/resource-warehouse.md — 2b's instruction "measure on the Fairphone" (line 197) and "Fairphone measurement still owed" (line 198, 2026-09-03); measurement result "the first run of Der Schmetterling still killed the playback" (lines 212-214, 2026-09-04, step 3 heading "after the Fairphone"); "second Fairphone measurement" (line 321, 2026-09-04, step 4 heading); "Fairphone, fourth measurement (2026-09-04): the warmup works — the first run of Der Schmetterling plays" (line 417). Cross-referenced in docs/tasks/future/first-run-spike-v2.md:7-21, whose own list numbers four measurements (1-4) under a header that says "three measurements on 2026-09-04" — **this is an internal inconsistency in the repo's own docs, worth flagging**: the header undercounts by one against its own numbered list, and resource-warehouse.md's prose never labels a "third" measurement by name (it jumps from "second" at line 321 to "fourth" at line 417).
4. **"Can barely run"** (2026-09-15): docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md:40-41 — "After the 2026-09-15 CPU round (voice culling, the polynomial sine, `fastExp2`/`fastExp`) the FF4 can barely run Der Schmetterling again. The maintainer's read: the bottleneck is now the memory footprint of the guitars' filter stages..."
5. **"Runs smoothly again," after the block-rate drift fix** (2026-09-15): docs/plans/blog-optimization-series.md:520-527, post plan `2026-09-15-drift-for-free` — the fix is stepping the analog-drift multiplier once per block (375 Hz) instead of once per sample; closing line: "Not bit-identical, judged by ear, and the phone went from "barely" to "smooth" that evening." The literal phrase "runs smoothly again" does not appear for this event (that exact wording is only used for the earlier 2026-08-20 goal and, unrelated to Fairphone, for a 2026-05-01 JVM backend fix in DEV-DIARY.MD:257 / docs/history/2026-Q2.md:33 — a false-positive match on "smoothly," not about the Fairphone); the closest verbatim quote for the 2026-09-15 event is "barely" → "smooth" above.
