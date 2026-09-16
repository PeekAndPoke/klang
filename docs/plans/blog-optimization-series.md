# Blog series: the phone that does not get faster

Status: **plan, 2026-09-16.** A series of posts about every performance optimization Klangmotor
has made, one optimization or one class per post, with the Fairphone 4 as the non-moving target
that songs of growing complexity must keep running on. This plan is written so that the
MECHANICS (checkouts, measurements, figures, links) can be done by lesser agents step by step,
and the WRITING is done by Fable, which the maintainer named for it.

House rules for every post: `docs/blog/howto-write-a-post.md` (identity, front matter, the
paper-skeleton arc, voice, figures, citations, CommonMark only, the pre-publish checklist). This
plan does not repeat them; it adds what this series needs on top.

## 0. The spine of the series

Every post answers the same three questions, in this order, before anything else:

1. **What did the phone say?** The Fairphone 4 (Snapdragon 750G, 2 Cortex-A77 + 6 Cortex-A55,
   the browser's AudioWorklet on V8) is where the songs must play. It has not changed since the
   first measurement. Der Schmetterling has: more guitars, more stages, more voices. Each post
   opens with the state of the phone at its date where a measurement exists, or with the desktop
   measurement that stood in for it and says so.
2. **Why this and not something else?** The measurement that pointed here, the alternative that
   was rejected, the thing that was measured and NOT built. The series' credibility lives in the
   won't-implement decisions (steps 3 and 4 of the optimizer plan, `fastLn`, the chain fusion,
   the `fastCopy` that was 22x slower).
3. **What did it cost in sound?** Bit-identical, within a margin, or judged by ear; and how that
   was guarded (a parity spec with the old implementation as its oracle, a fuzz, a golden).

**RTF alone is not the score.** The real-time factor (render time over audio time, §2.7) of the
live song mixes two things that moved in opposite directions: the engine got faster and the song
got heavier (the guitars alone went from a filtered supersaw to a five-stage rig). Plotted as
one line it says the optimizations went the wrong way. So the series never shows a live-song
RTF without the work it bought, and the closing post separates the two effects (§3, P16):

- **the engine on fixed work**: the same frozen song and the same benchmark rows rendered by
  every engine version (the tags), which isolates the engine;
- **the work the song asked for**: every snapshot of the song rendered by the same engine
  (HEAD), in RTF and in structural work units (passes over the block per note, voices per block),
  which isolates the song;
- **the phone timeline** (stall, half the song, 75 %, barely, smooth), annotated with the song's
  work at each date, so "barely" on 2026-09-15 reads as what it was: a heavier song on the same
  engine speed, then a faster engine.

Every per-post result is stated on fixed work by construction (the same voice or chain, before
and after), so only the live-song numbers need this care; where a post quotes one, it names the
song version next to it.

Tags for the front matter: every post carries `series-fairphone` plus its own. Every post links
its predecessor and successor with relative links (`../YYYY-MM-DD-slug/index.md`), and the
existing posts that overlap (`2026-06-30-killing-the-plastic-pipe`, `2026-08-12-the-fundamental-lottery`,
`2026-03-21-growing-our-own-brain`) get one sentence and a link where they touch.

## 1. The tag map (links go to a tag, never to master)

Every link into the tree is a GitHub permalink at the FIRST tag that contains the change:
`https://github.com/PeekAndPoke/klang/blob/<tag>/<path>#L<from>-L<to>`. Line numbers are taken
from `git show <tag>:<path>` (never from the working tree), see §2.2. Where a post shows a
BEFORE, the before link goes to the last tag WITHOUT the change.

| optimization | first commit(s) | first tag containing it | last tag before it |
|---|---|---|---|
| Rational to CycleTime (final) | `13e3be4b` .. `aa31eb2b`, 2026-05-29/30 | `v0.1.0` (2026-06-24, the first tag) | none (predates every tag; the before link goes to the commit `git rev-parse 13e3be4b^`) |
| oscillator engine unification | `e303c66c` .. `b0173e4d`, 2026-06-03..05 | `v0.1.0` | none (commit `e303c66c^`) |
| mutable `SprudelVoiceData` | `e9fa560c` .. `c303c333`, 2026-06-06/07 | `v0.1.0` | none (commit `e9fa560c^`) |
| worklet trust codec | `096e5e7d`, `59e85839`, 2026-06-07, then the KSP generator | `v0.1.0` | none (commit `096e5e7d^`) |
| body and vowel to the orbit | `af1c08dc` .. `c1e67c4f`, 2026-07-03/04 | `v0.1.2` (2026-07-04) | `v0.1.1` (2026-06-30) |
| constant folding D1a/D1b | `2daaf761`, `974a18db`, `d3f664f4`, 2026-08-19 | `v0.3.1.4` (2026-08-19) | `v0.3.1.3` (2026-08-18) |
| EqCore fusion D2 to D5 and rule R1 | `c2e94a72` .. `076683ab`, `03e995ca`, 2026-08-19..21 | `v0.3.2` (2026-08-23) for R1; `v0.3.1.5` (2026-08-20) for the BELL section | `v0.3.1.4` |
| resource warehouse | `111c7355` .. `0354c9da`, 2026-09-03/04 | `v0.3.7` (2026-09-05) | `v0.3.6` (2026-08-31) |
| sine partial banks | `6d4056f9`, `39119aef` (harness fix), 2026-09-07 | `v0.3.8.2` (2026-09-08) | `v0.3.8` (2026-09-07) |
| DriftLanes and `analogSpread` | `e5596416` .. `54b988aa`, 2026-09-10 | `v0.3.11` (2026-09-10) | `v0.3.10` (2026-09-09) |
| voice culling | `2133056a`, 2026-09-15 | `v0.3.13` (2026-09-15) | `v0.3.12` (2026-09-10) |
| polynomial sine, 2^x, e^x | `80915245`, `2aaaf62b`, `65b1842d`, `b9565f67`, 2026-09-15 | `v0.3.13` | `v0.3.12` |
| analog drift per block | `d4f0d868`, 2026-09-15 | `v0.3.13` | `v0.3.12` |
| the optimizer's margin, Affine, R2, div/minus/neg | `bb9bdbb3`, `a645a97d`, `1056a1c0`, `841cdb8b`, 2026-09-15/16 | `v0.3.14` (2026-09-16) | `v0.3.13` |
| polyphase decimator | `6c1ae38b`, 2026-09-15 | `v0.3.14` | `v0.3.13` |

The tags `v0.3.8.2` to `v0.3.14` were created on 2026-09-16 on their version-bump commits, the
same shape as the older ones. Verify the map before use: `git tag --contains <hash> | sort -V |
head -1`. A mechanics agent must not invent a tag.

## 2. Mechanics that every post shares

### 2.1 Agent tiers (per `/agent-fleet`)

| step | who | why |
|---|---|---|
| link extraction, line ranges at a tag, date lookups, table transcription from `docs/benchmarks` | haiku | mechanical, cheaply checkable |
| artifact digests (a task doc or plan boiled down to the facts a post needs, with quotes and their locations) | sonnet | retrieval and summary |
| figure scripts from given data, graphviz flow charts from a given node list | sonnet | mechanical, the coordinator eyeballs the PNG |
| re-measurements at an old tag, code transplants into a current benchmark row, anything that runs Gradle | opus, one agent at a time, under `console/with-build-lock.sh`, coordinator owns the build | correctness, single-writer Gradle |
| the writing, the final read of every figure, the fact check against the artifacts, the publish checklist | Fable (the session model) | the maintainer's instruction |

The coordinator (Fable) never delegates the writing, and reviews every measurement an agent
brings back against the artifact it claims to come from. A number with no artifact is cut.

### 2.2 Permalinks and line ranges

```bash
# the file as it was at the tag (never the working tree)
git show v0.3.13:audio_be/src/commonMain/kotlin/DspUtil.kt | grep -n "fun fastSin" 
# a symbol's range: first line from the grep, last line by reading the block
git show v0.3.13:audio_be/src/commonMain/kotlin/DspUtil.kt | sed -n '120,160p'
```

Link form: `https://github.com/PeekAndPoke/klang/blob/v0.3.13/audio_be/src/commonMain/kotlin/DspUtil.kt#L120-L160`.
A post quotes the code VERBATIM from `git show <tag>:<path>` (the howto's rule), names the file
in prose, and links the permalink under the block as `*[DspUtil.kt at v0.3.13](...)*`. Before
and after come from two tags (or a tag and its predecessor commit) and are quoted from each.

### 2.3 Re-measuring at an old tag

Two ways, and the second is usually better.

**A worktree at the tag.** `git worktree add /tmp/klang-<tag> <tag>`, then run the benchmark
there. One Gradle build at a time across ALL worktrees (the coordinator runs it, under the lock).
Old tags may not build with today's toolchain, and the harness changed under the numbers: commit
`39119aef` (2026-09-07) fixed the ignitor benchmark rendering every voice TWICE per block, so
every absolute µs/block from an ignitor benchmark before that commit is about double the real
cost (ratios within one run hold). The song benchmark's `peakRTF` skips the first 32 blocks
since the warehouse work. Never put a pre-fix and a post-fix absolute number in one table
without saying so. Remove the worktree when done (`git worktree remove`).

**A transplant into today's harness.** Copy the old implementation into a current benchmark row
or a parity spec as the oracle, the way `OversamplerDecimatorParitySpec` keeps the ring
decimator and `sine-harmonics7-tree` keeps the hand-rolled tree. Same machine, same harness,
same session, so the before and the after are comparable by construction. Prefer this for every
post whose before and after can be expressed as two rows.

**Rules for any number in a post:** same machine, same harness version, same session for an
A/B (the `live` suite reads the working tree, so both arms must run in one session), the
platform named (JVM or node; node is the deployment platform), the block size 128 stated once,
and the `docs/benchmarks` file (or the new file the run writes) cited. Three runs, the median
reported, the spread mentioned when it matters. The D0 comparability rule from
`EffectBenchmark.kt`: chained rows render a source inside the timed step and EqCore rows only
copy, so subtract the source baseline before comparing them.

### 2.4 Figures

- One `make_fig.py` per post in the post's directory (the convention of
  `2026-09-10-off-the-end-of-the-instrument/make_fig.py`): matplotlib, `Agg`, 130 dpi, white
  background, 9 to 11 inches wide, the palette `INK #14202B, GRAY #8D9BA3, CRIM #B23A32 (before),
  VERDE #2F7A4F (after), GOLD #A8791C (special)`. Data is pasted into the script as literals with
  the source file named in a comment, never fetched at run time.
- Flow charts and graph pictures (an authored tree against its optimized tree, a chain of
  ignitors against one fused pass, the wire path) are graphviz: a `.dot` file in the post
  directory rendered with `dot -Tpng -Gdpi=130`, the PNG committed beside it. Mermaid is not
  used (the howto: CommonMark only, no generator assumptions).
- Every figure is opened and looked at by Fable before the post is done. A figure a sonnet agent
  produced is a draft until then.
- Every figure has an italic caption `*Fig. N: what you see, and what to notice.*`

### 2.5 Work units

A structural measure of what a song asks of the engine, so RTF can be put in proportion. For one
voice: the number of passes over the block its optimized graph renders (one per node that is a
pass, an `Eq` counting its sections, a filter its `passes`, scalar leaves and scalar-only
arithmetic counting nothing), which is the fuzz's `workCount` in
`IgnitorDslOptimizerFuzzSpec` made production-grade; for a block: that number summed over the
voices active in the block; for a song: the median and the peak over the run. A `work` column in
`runSongBenchmark` next to `medRTF`/`peakRTF` gives RTF per work unit, the engine's cost per
pass, which is the number that must go DOWN across the series even while the song's RTF goes
up. Mechanics: (opus) add the counter to `SongBenchmark` at HEAD (the walk exists in the fuzz;
move it to `audio_bridge` as `IgnitorDsl.workUnits()` with a spec row, since the benchmark module
may not depend on a test source set); it is only needed at HEAD, since the song snapshots are
rendered by HEAD for the song axis (P16). The census table in the archive record (90 nodes, 35
passes per rhythm-guitar note) is the hand-counted precedent.

**Per instrument, the number the maintainer asked for (2026-09-16): calculations per sample for
a given instrument, before and after.** Two figures per instrument, both on FIXED instrument
text (the instrument as it is at HEAD), so the engine is the only thing that moves:

- *work per note*: passes over the block per note (the walk above on the instrument's optimized
  graph), and the voices the instrument keeps active per block (the rig suite's onsets and the
  `culled` column give the second);
- *cost per sample per voice*: the rig suite's `medRTF` for the instrument group, turned into
  ns per sample per active voice (RTF times the block's audio time, over frames, over active
  voices), measured on two engines: `v0.3.12` (2026-09-10, the last tag before the September
  round: culling, the polynomial transcendentals, the block-rate drift, the optimizer's margin
  and folds, the decimator) and `v0.3.14`. The ratio of the two is the September round's effect
  on THAT instrument; dividing by passes per note gives ns per sample per pass, the engine's
  efficiency on that instrument's mix of node kinds.

The four instruments of Der Schmetterling, per the maintainer: the guitars with the full rig,
the bass with its harmonics, the marimba, the Orchestertrommel. Expected shape: the drift round
pays most on the marimba and the trommel (their "no analog" rows), culling on the trommel and
the drums, the decimator on the guitars, the polynomial sine on the bass's harmonic bank. Where
an instrument's HEAD text uses a door that `v0.3.12` lacks (check by parsing; the rig suite fails
loudly), record it and take the nearest older tag that parses it as the "before".

**Memory, two faces (maintainer, 2026-09-16), the same four instruments, the same two engines:**

- *footprint per note*, what a note holds while it plays, which is the cache question on the
  A55: the voice's scratch depth (`ScratchBuffers` high-water mark, times the block in doubles,
  times 8 bytes), the oversampler work buffers its shapers borrow (block times factor times 8,
  per distinct factor), and the per-node state (a filter section's two state doubles and its
  coefficients, an oversampler stage's 13 history doubles, a delay or a sample player's ring,
  the drift lanes), summed by a walk over the optimized graph with a per-kind size table. A
  model count, not a heap measurement, and said so; the JVM heap per active voice from a
  `runSongBenchmark` run (`Runtime.totalMemory - freeMemory` before and after the voices are
  built, three runs) is the cross-check where it can be had.
- *buffer traffic per sample*, what a sample moves: reads plus writes of block buffers per
  sample, from a per-kind table over the optimized graph (an in-place pass reads and writes one
  buffer: 2; a scratch pass reads two and writes one: 3; an `Eq` of N sections in place: 2N; a
  shaper at factor F: the upsample writes F, the shaper reads and writes F, each decimation
  stage reads its input and writes half, plus the copy back; a `MemoizingIgnitor` copy: 2). The
  same walk as the work units, a different weight per kind. This is the number the optimizer's
  passes actually saved (a fused Eq pass against a chain of filters is the same arithmetic and
  a fraction of the traffic), and the one that explains why a 5 % desktop win was run-or-not-run
  on the phone (P7).
- *measured, where the machine allows*: `perf stat -e cache-misses,cache-references,instructions`
  around one isolated rig row on the JVM, per instrument, both engines (`perf` is installed on
  the machine this plan was written on; a JIT'd JVM needs `-XX:+PreserveFramePointer` only for
  attribution, not for these counters). Cache misses per sample per voice is the closest thing
  to what the A55 feels.

**DONE 2026-09-16, the harness.** `GraphCensus` (audio_be, `ignitor/GraphCensus.kt`, a
diagnostic never on the render path; `GraphCensusSpec` pins hand-counted graphs, seven mutations
red) counts passes, buffer traffic and held bytes per voice from the optimized tree; the song
benchmark sums it over the rendering voices after every measured block (`voices`, `work`,
`traffic`, `KiB`, `ns/smp/voice`, `ns/smp/pass` columns in every report); and
`./gradlew runSongBenchmark --args=ledger` renders the six instrument pieces of Der
Schmetterling on the FROZEN song text (`FrozenPieces.kt`, the text at `v0.3.14`) and on the live
text, and APPENDS a row per piece to `docs/benchmarks/ledger.md` with `git describe` and the CPU.
That is the command to run after every optimization round; a piece's history reads down one
column. The "before" of the September round (`v0.3.12`) was measured by the transplant recipe
of §2.3 (a worktree at the tag, the frozen pieces and a minimal ledger suite copied in, no census
there) and pasted into the ledger marked "transplant"; three runs each side, medians:

| piece | v0.3.12 medRTF | v0.3.14 medRTF | change |
|---|---:|---:|---:|
| guitar melody (rig) | 0.01961 | 0.01612 | -18 % |
| guitars rhythm (rig) | 0.04352 | 0.03746 | -14 % |
| marimba | 0.03388 | 0.02897 | -14 % |
| trommel | 0.06037 | 0.03365 | -44 % |
| bass | 0.01009 | 0.00451 | -55 % |
| drums (samples) | 0.03968 | 0.01726 | -57 % |

Same pieces, same machine (Ryzen 9 PRO 7940HS, JVM), back to back, the spread of the three runs
about 10 % (one marimba run caught a pause, peak RTF 1.2, and reads high); the drums went from 24
active voices to 6 rendering (culling). The census at HEAD: a
rhythm guitar note is 57 passes (19 of them the unison stack's voices), a melody guitar note 48,
a marimba note 18, a trommel note 24, the bass 7; ns per sample per pass 6.4 to 8.0 on the
guitars, 5.0 on the marimba, 6.0 on the trommel, 14.6 on the bass. The old engine's graphs for
the same text had a few more passes per note (no `Affine`, so every level knob was its own
`Times`); the ledger's structural columns are HEAD's.

Not measured: `perf stat` cache misses (the machine has `perf`; a later run), and the node side
(the song benchmark is JVM; the `guitar-rig*` rows in `audio_benchmark` are the node precedent).

### 2.6 The post skeleton, series flavour

The howto's arc, with the series' three questions on top:

1. The phone at this date (or the desktop stand-in, said so).
2. The problem, measured, with the number that pointed here.
3. Prior approaches and their failure modes, including OUR rejected one.
4. The bridge: the sentence that made the solution possible.
5. The method: the code, verbatim, before and after, linked at the tags.
6. The results: the receipts, same units as the problem.
7. What it cost in sound, and how that was guarded.
8. Lessons and what stays open, then references.

120 to 220 lines. Longer is two posts.

### 2.7 RTF, defined once

Render time divided by the audio time rendered. A 128-frame block at 48 kHz is 2.67 ms of sound;
rendering it in 0.27 ms is an RTF of 0.1, a tenth of the time available. 1.0 is the deadline.
`medRTF` is the steady-state average (the CPU share); `peakRTF` is the busiest single block after
the first 32 (the one-time allocations skipped), and the peak is what makes a phone stutter. P1
defines it for the reader; every other post links there.

### 2.8 Review before publish

One reviewer (opus) with the post, the artifacts it cites and the checklist from the howto:
every number traced to its artifact, every quote verbatim against `git show`, every link
resolving at the tag, spelling sweep, fence balance. Fable fixes and flips nothing: `status:
draft` stays until the maintainer publishes.

## 3. The posts

Order is publication order; dates are milestone dates (the howto's rule), so the directory sort
tells the story in the order it happened. Working titles are Fable's to change.

### P1. `2026-08-19-the-phone-that-does-not-get-faster` (the thesis)

- **Story.** Why a 2021 phone is the target and what it forces: songs grow, the phone does not, so
  every added complexity must be paid for in the engine. The measurement stack that makes the
  series possible: RTF (defined here, §2.7), work units (§2.5), the `audio_benchmark` rows, the
  song suites, `docs/benchmarks`, JVM against node and why node is the number that counts. And
  the warning the whole series rests on: the live song's RTF got WORSE over the summer while the
  engine got faster, because the song got heavier; the honest scoreboard is cost per unit of
  work on fixed work, which is how every later post reports. The day the goal was written down
  (`docs/plans/unified-eq.md` opens with "Der Schmetterling runs on an older Fairphone 4 again";
  the D0 baseline `316cc8d8` is 2026-08-19).
- **Artifacts.** `docs/plans/unified-eq.md` (goal, the on-device notes of 2026-08-19 and 2026-08-20),
  `docs/plans/resource-warehouse.md` (the four Fairphone measurements of 2026-09-03/04),
  `docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md` ("can barely
  run", 2026-09-15), `docs/benchmarks/2026-07-03_der-schmetterling-cpu-analysis.md` (the first
  song analysis), `audio_benchmark/README.md`, `src/jvmMain/kotlin/SongBenchmark*.kt`,
  `console/run-dsp-benchmarks.sh`, `docs/blog/2026-09-10-off-the-end-of-the-instrument/index.md`
  (for the size of the thing).
- **Mechanics.** (haiku) the phone's spec sheet, verified online (SoC, cores, release year);
  dates of every "Fairphone" mention in the docs (`git log -S Fairphone -- docs`). (sonnet) a
  digest of the measurement stack with the exact column definitions (`medRTF`, `peakRTF`,
  `culled`, the 32-block skip). (sonnet) Fig. 1, a timeline of the phone: stall, half the song
  (2026-08-19), 75 % (2026-08-20), first run plays (2026-09-04), barely (2026-09-15), smooth
  (2026-09-15 evening); Fig. 2, the JVM against node ratio from a compare file
  (`docs/benchmarks/2026-08-19_202119_compare.md`, the ratio column as a sorted bar chart).
- **Writing.** Fable. The FF4 as a constraint that is a gift: it makes "fast enough" measurable.
  The rule that every optimization in the series had a number before it and a number after it,
  and that the ones without a number (class C in the inventory) are told as what they are.

### P2. `2026-05-29-the-triplet-that-never-drifted` (already in the backlog)

- **Story.** Musical time in four representations: `double` (drift, event-fetch errors),
  Rational v1, Rational v2 with the JS BigInt hot path, `CycleTime` fixed-point Int with
  `2^20 * 3 * 5 * 7 = 110,100,480` ticks per cycle. Both a correctness and a performance story.
- **Artifacts.** `docs/history/2026-Q1.md` and `2026-Q2.md`, `sprudel/MEMORY.md`, the commits of
  2026-01-08, 2026-01-13 (PR #3), 2026-02-05, 2026-03-16/17 (PRs #35/#36/#38), 2026-05-29/30;
  `StructuralCycleSelectionSpec`, `LangLateAlternationSpec`; the §04 war-story box cut from the
  whitepaper on 2026-08-28 (in git history of `docs/whitepaper/`).
- **Mechanics.** (sonnet) separate the Rational generations by `git log -p` on the Rational
  file(s), with dates and hashes. (opus) there is no recorded timing; a transplant is possible:
  a JS micro benchmark of `CycleTime` arithmetic against a `Rational` copied from its last
  commit into `audio_benchmark/src/jsMain` for one run, both platforms, then deleted (scaffolding
  rule). (sonnet) Fig. 1, the four representations as a flow of "what breaks" (graphviz); Fig. 2,
  the measured ns/op if the transplant is done.
- **Writing.** Fable. Cross-link `2026-03-21-growing-our-own-brain`.

### P3. `2026-06-05-one-engine-for-five-waves`

- **Story.** Two shape engines and three unison engines became one of each, and the
  control-rate value (`controlRateValueOrNull`) let a block-constant parameter be read as one
  number instead of rendering a scratch buffer to read its first sample.
- **Artifacts.** `docs/tasks-archive/2026-06/20260605-oscillator-engine-unification.md`,
  `audio/MEMORY.md` ("Oscillator Engine Unified (2026-06-05)"), commits `e303c66c` .. `b0173e4d`.
- **Mechanics.** (haiku) the µs/voice table from the archive doc (supersaw 4.65/8.57, supersquare
  5.00/9.60 from about 8.2 JVM, supertri 5.04/9.73, supersine 15.68/22.56 from 18.7). (opus)
  re-run today's `IgnitorBenchmark` rows supersaw/supersquare/supertri/supersine on JVM and node
  for the "and today" column, noting the harness fix of 2026-09-07 makes the old absolutes not
  directly comparable (say so in the caption; compare ratios). (sonnet) Fig. 1, before and after
  per oscillator (grouped bars, JVM and node); Fig. 2, graphviz of the class hierarchy before
  (three stacks) and after (`DetunedStackIgnitor` and its subclasses).
- **Code specimens.** `waveTrapezoid` in `DspUtil.kt` at `v0.1.0`; the `controlRateValueOrNull`
  declaration in `Ignitor.kt` at `v0.1.0` with its KDoc; a before from `e303c66c^` of one of the
  hand-rolled super oscillators.
- **Writing.** Fable. Cross-link `2026-06-30-killing-the-plastic-pipe` (the sound side of the
  same weeks).

### P4. `2026-06-06-twenty-allocations-per-note`

- **Story.** Every pattern modifier copied the voice data; ~20 allocations per voice. The mutable
  single-owner `SprudelVoiceData` with cloning at exactly three leaf emitters, then the
  `SvdGroups` copy-on-write sub-objects: leaf clone 820 ns to 47 ns. The dead end that is the
  best paragraph: a hand-written `fastCopy` via `Object.assign` was 22x SLOWER on V8 than the
  constructor-based `copy()`.
- **Artifacts.** `docs/tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md`,
  `sprudel/MEMORY.md`, `docs/whitepaper/klang-whitepaper.html` (the 17x line), commits
  `e9fa560c` .. `c303c333`.
- **Mechanics.** (opus) run `VoiceDataCopyBenchmark` today on JVM and node
  (`:audio_benchmark:jvmRun`, `:jsNodeProductionRun`); it prints before the captured section.
  (haiku) count the `voiceSetter` sites and the three leaf emitters at `v0.1.0`. (sonnet) Fig. 1,
  ns/op for `copy()`, `clone()`, and the rejected `fastCopy` (log scale, the 18,390 ns bar in
  crimson); Fig. 2, graphviz: a pattern chain with a copy at every node against one with a clone
  at the leaf only.
- **Code specimens.** one `voiceModifier { copy(...) }` before (`e9fa560c^`) against its
  `voiceSetter` after (`v0.1.0`); the no-aliasing invariant KDoc.
- **Writing.** Fable.

### P5. `2026-06-07-sixty-seven-microseconds-to-385-nanoseconds` (in the backlog as "67us to 385ns")

- **Story.** The worklet wire: kotlinx `decodeFromDynamic` at 64 to 67 µs per voice on the audio
  thread, ProtoBuf tried and slower on Kotlin/JS, the hand-rolled proof at 398 ns, then the KSP
  "trust codec" generator: decode 67,001 to 385 ns (174x), encode 4,904 to 478 ns (10x), and the
  schema hash that guards a stale cached worklet.
- **Artifacts.** `docs/tasks-archive/2026-06/20260607-worklet-codec-ksp.md`,
  `docs/tasks-archive/2026-06/20260611-wireformat-enhancements.md`, `docs/history/2026-Q2.md`,
  commits `096e5e7d`, `59e85839`; the `:audio-wire-codec-ksp` module.
- **Mechanics.** (opus) run `WorkletSerializationBenchmark` today (`:audio_benchmark:jsNodeProductionRun`)
  for the current ns/op. (haiku) permalinks to `@WireFormat`, the generated `WireCodecGenerated.kt`
  (generated files are in `build/`, so link the generator and one annotated type instead), the
  `WorkletContract` at `v0.1.0`. (sonnet) Fig. 1, the four bars (kotlinx, ProtoBuf if a number
  exists in the doc, hand-rolled, generated) on a log axis; Fig. 2, graphviz of the wire path
  frontend to worklet with where the decode sits (the audio thread) marked.
- **Writing.** Fable. Cross-link `2026-08-12-one-annotation-six-artifacts` (KSP as a habit).

### P6. `2026-07-04-the-body-that-played-a-thousand-times`

- **Story.** The first whole-song analysis (2026-07-03): GTR2's nested `superimpose` scheduled
  1088 voices, about 52 % of the song, each running the body and vowel resonators per voice.
  Moving body and vowel to the orbit, once on the summed mix, with the owning voice configuring
  it through `VoiceLease` and `KatalystFilterSwap` crossfading a live rebuild.
- **Artifacts.** `docs/benchmarks/2026-07-03_der-schmetterling-cpu-analysis.md`,
  `docs/tasks-archive/2026-07/20260703-body-vowel-orbit-katalyst.md`,
  `20260704-body-vowel-materials-floor-crossfade.md`, `docs/benchmarks/2026-07-04_233042_song_jvm.md`,
  `audio/MEMORY.md` ("Body / Vowel resonators", the superimpose finding marked obsolete).
- **Mechanics.** (haiku) the voice count and the 52 % from the analysis doc, with the rows they
  come from. (opus) today's `EffectBenchmark` rows Body and Vowel (JVM and node) for the cost of
  one instance, and the arithmetic "per voice times 1088 against once per orbit" as a table, not
  a re-measurement of the old song (the song has changed). (sonnet) Fig. 1, graphviz: voices
  fanning into an orbit, the body filter drawn once per voice (before) and once per orbit
  (after); Fig. 2, the cost model bars.
- **Code specimens.** `VoiceFactory` pulling `Body`/`Formant` out of the per-voice chain at
  `v0.1.2`, and the `KatalystBodyEffect` entry.
- **Writing.** Fable. The lesson that carries into P13: the cheapest per-voice work is the work
  moved out of the voice.

### P7. `2026-08-19-half-a-song`

- **Story.** Constant folding D1: `MemoizingIgnitor` propagating the control-rate value, `Times`
  and `Plus` folding a block-constant operand into an in-place scalar loop, then fifteen node
  kinds that were missing the override. Desktop: the GTR-FX ladder 0.00153 to 0.00139 medRTF
  (-9 %), the guitar voice -4.7 %. On the phone: "half of Der Schmetterling runs again". Why a
  5 % desktop win is run-or-not-run on an A55: caches, not arithmetic.
- **Artifacts.** `docs/plans/unified-eq.md` (D0, D1a, D1b, the on-device note of 2026-08-19),
  `docs/benchmarks/2026-08-19_121412_song_jvm.md`, `2026-08-19_123836_*`, commits `2daaf761`,
  `974a18db`, `d3f664f4`.
- **Mechanics.** (haiku) the ladder table from the plan and the benchmark files. (opus) re-run
  `runSongBenchmark --args=ladders` today for the "and today" column. (sonnet) Fig. 1, graphviz
  of the guitar tone chain as 11 ignitor nodes, each a loop and a scratch buffer, the
  block-constant operands marked; Fig. 2, the ladder rungs before and after.
- **Code specimens.** `TimesIgnitor.generate` at `v0.3.1.3` (before) and `v0.3.1.4` (after); the
  `MemoizingIgnitor.controlRateValueOrNull` propagation.
- **Writing.** Fable. The first appearance of the graph optimizer idea: a fold is what you do when
  you know something at build time the render loop does not.

### P8. `2026-08-20-eleven-loops-one-pass`

- **Story.** EqCore: LP, HP, BP, notch, bell and raw-tap sections in one section-major loop with
  coefficients hoisted to locals; the three-way loop-shape bake-off (`SECTION_MAJOR_LOCALS` wins
  on node at every voice count, `sampleMajor` edges ahead on the JVM at N >= 4, and node is the
  platform that counts); the `Eq` wire node; rule R1 fusing adjacent serial filters at
  registration. Guitar tail -40 % on node, -28 % on the JVM. On the phone: "Der Schmetterling
  runs smoothly on the FF4 at about 75 % CPU", the plan's goal.
- **Artifacts.** `docs/plans/unified-eq.md` (D2a bake-off table, D2b, D2c, D3, D4, the milestone
  note of 2026-08-20), `docs/benchmarks/2026-08-19_202119_*` (the EqCore rows against the
  ignitor chain rows), commits `c2e94a72`, `c843dc64`, `302e7f66`, `1d30c9bf`, `5ba804bc`,
  `a4b19970`, `d3b77cc4`, `076683ab`, `03e995ca`.
- **Mechanics.** (haiku) the bake-off table and the effect rows from the benchmark file, with the
  D0 comparability rule applied (subtract the source baseline; the plan explains how). (opus)
  today's `EffectBenchmark` rows "Ignitor SVF x4 chain" against "EqCore 4-serial", JVM and node.
  (sonnet) Fig. 1, graphviz: the authored chain (`lowpass.highpass.notch...`) and the optimized
  tree with one `Eq` of N sections; Fig. 2, the loop shapes as three small diagrams
  (sample-major, section-major, section-major with locals) and their µs/block per voice count.
- **Code specimens.** the `EqCore.process` LOWPASS arm at `v0.3.2`; rule R1's entry in
  `IgnitorDslOptimizer.kt` at `v0.3.2`; the `EqCoreSpec` chain oracle (the fused pass held to the
  chain bit for bit).
- **Writing.** Fable. Two lessons that recur: the loop shape decides, and node decides.

### P9. `2026-09-04-seven-megabytes-of-silence`

- **Story.** A `DelayLine(10.0)` ring is 7.68 MB, 97 % of a cylinder's footprint, zero-filled on
  the audio thread at first use, eight orbits per song: the first-run stutter. Lazy rings in
  power-of-two size classes, grow by migration with contents preserved, a shelf for returned
  units, allocation failures degrading instead of killing the render thread, one shared scratch
  pool, and the warmup vocabulary that pre-builds and JITs every node kind before the first note.
  Four Fairphone measurements in two days, each moving the stall somewhere else until it was
  gone.
- **Artifacts.** `docs/plans/resource-warehouse.md` (the whole plan is a diary of the two days
  with the four device notes), `audio/MEMORY.md`, `docs/tasks/future/first-run-spike-v2.md`
  (what remains), commits `111c7355` .. `d3fb76ba`.
- **Mechanics.** (haiku) the byte arithmetic (7.68 MB, 8 orbits, 63 MB) and the size-class
  ladder from the plan. (opus) a measurement that does not exist yet and would make the post:
  time to first block of Der Schmetterling with and without the warmup, JVM, using the offline
  renderer (`KlangOfflineRenderer`), three runs each; if the warmup cannot be switched off
  without code, skip it and say the win was measured by hand on the device. (sonnet) Fig. 1,
  memory before and after per cylinder as stacked bars; Fig. 2, graphviz of the shelf: request,
  best fit up, migrate, return.
- **Code specimens.** `ResourceWarehouse` size classes at `v0.3.7`; the `catch` site that
  degrades a failed ring allocation; a `WarmupVocabulary` entry.
- **Writing.** Fable. The class with no µs number, told as what it is: bytes and a stall, measured
  by a hand on a phone.

### P10. `2026-09-07-loop-shape-beats-pass-count`

- **Story.** Seven `Sine` nodes, seven `Times`, seven `Plus`: twenty-one passes and seven `sin()`
  per sample for a seven-harmonic voice. The native partial bank was SLOWER in its first
  sample-major form (41 µs against the tree's 24) and faster rewritten partial-major (22). The
  same week the harness was found rendering every voice twice (`39119aef`), so every absolute
  before it is double. Second act, DriftLanes (2026-09-10): one shared drift-lane container with
  the `analogSpread` blend, and the V8 lesson that reading a per-block value inside the sample
  loop cost 16 % of a drifting supersaw and nothing on the JVM.
- **Artifacts.** `docs/plans/sine-partial-banks.md`, `docs/benchmarks/2026-09-07_sine-partial-banks_jvm.md`,
  `docs/tasks-archive/2026-09/20260907-*` (the sine bank record),
  `docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`,
  `docs/benchmarks/2026-09-10_drift-lanes_{jvm,nodejs}.md`, `audio/MEMORY.md` ("Loop shape beats
  block-pass count", "One drift-lane container"), commits `6d4056f9`, `39119aef`, `e5596416` ..
  `54b988aa`.
- **Mechanics.** (haiku) both tables. (opus) today's rows `sine-harmonics7`, `sine-octaves6`,
  `sine-harmonics7-tree`, `supersaw_8v+analog`, `supersaw_8v+analog+spread0` on JVM and node.
  (sonnet) Fig. 1, the three bars sample-major, tree, partial-major (the "slower" bar in
  crimson); Fig. 2, two loop diagrams (outer over samples, outer over partials) with what stays
  in a register; Fig. 3, spread 1 against spread 0 per oscillator family, JVM and node.
- **Code specimens.** the partial-major loop of `PartialBankIgnitor` at `v0.3.8.2`; the
  `driftStep` inline and the hoisted per-block values in `DriftLanes.kt` at `v0.3.11`; the
  sample-major version from the commit before the rewrite, if it was committed (check
  `git log -p` on the day; if it was never committed, describe it and say so).
- **Writing.** Fable. Cross-link `2026-06-30-killing-the-plastic-pipe` for what drift is.

### P11. `2026-09-15-zombies`

- **Story.** A voice kept rendering its whole chain after it had decayed to silence, until its
  scheduled end. Culling in the release phase after 50 ms under -100 dBFS, but as a ZOMBIE that
  keeps its active-list slot and its orbit lease, because the first build removed voices early
  and the null-diff render showed a -32 dBFS mix change: the orbit lease went to whichever voice
  rendered first after an owner died. Where it pays (drums 85 to 90 % culled, trommel 40 %,
  marimba 20 to 40 %) and where it cannot (guitars at 170 ms notes).
- **Artifacts.** `docs/tasks-archive/2026-09/20260915-voice-culling.md`, `audio/MEMORY.md`
  ("Silence culling"), `docs/benchmarks/2026-09-15_172412_song_jvm.md` (the `culled` column),
  commit `2133056a`, `VoiceCullingSpec`.
- **Mechanics.** (haiku) the onsets/culled table per instrument from the benchmark file. (opus)
  re-run `runSongBenchmark --args=songs` today (frozen songs, so the baseline stands) for the
  culled column and the RTF with culling on; culling off needs the `noCull()` door on every
  sound of a frozen song, which is a code edit in `SongBenchmarkCases.kt`, one row, then removed.
  (sonnet) Fig. 1, culled share per instrument (horizontal bars); Fig. 2, a timeline of one voice:
  gate, release, the 50 ms window, the cull, the zombie until the scheduled end.
- **Code specimens.** the release-phase gate and the zombie comment in `Voice.kt` at `v0.3.13`;
  `cull(seconds)` and `noCull()` doors.
- **Writing.** Fable. The -32 dBFS surprise is the paragraph.

### P12. `2026-09-15-eleven-digits-of-sine`

- **Story.** `sin`, `2^x` and `e^x` per sample were the dominant cost of a sine voice and of every
  envelope. Degree-11 minimax sine (1.3e-11), table-and-polynomial `2^x` pinned exact at the
  integers (4.7e-11), `e^x` through `2^x`; the credits to Cody and Waite in `CREDITS.MD`. The
  numbers that did not move as hoped: JVM `exp` is an intrinsic (3.7 to 3.5 ns), and `fastLn`
  was measured to be worth at most 1.3 % of the song and NOT built.
- **Artifacts.** `audio/MEMORY.md` (three entries), `docs/benchmarks/2026-09-15_1508*`, `_1509*`,
  `_1539*`, `_162314`, `_171710`, commits `80915245`, `2aaaf62b`, `65b1842d`, `b9565f67`,
  `FastSinSpec`, `FastExp2Spec`, `CREDITS.MD`, `src/jsMain/kotlin/pages/CreditsPage.kt`.
- **Mechanics.** (opus) `runMathBenchmark` today on JVM and node (it prints before the captured
  section of `:audio_benchmark:jvmRun` / `:jsNodeProductionRun`); the voices rows FM bell,
  vibrato+tremolo pad, lead. (sonnet) Fig. 1, ns per call library against polynomial, JVM and
  node, six bars; Fig. 2, the error curve of `fastSin` over a half period (evaluate against
  `kotlin.math.sin` in a small JVM main or in Python against `numpy.sin` with the same
  coefficients copied from `DspUtil.kt`, say which); Fig. 3, the voices before and after.
- **Code specimens.** `fastSin`, `fastExp2` at `v0.3.13`; the `2.0.pow` call site in
  `VibratoRenderer` before (`v0.3.12`) and after.
- **Writing.** Fable. Citations verified online: Cody and Waite (1980), Software Manual for the
  Elementary Functions; the minimax fitting method used (Remez), one reference.

### P13. `2026-09-15-drift-for-free`

- **Story.** The analog drift stepped every lane per sample: xorshift, two one-poles, the blend,
  20 lanes per guitar note (19 unison voices and the shared lane), for a modulation whose fastest layer has a 50 ms time constant. 19 %
  of a drifting guitar, 13 % of the marimba, 15 % of the trommel. Stepping once per block at
  375 Hz and ramping the multiplier across the block: the rhythm rig 0.040 to 0.036, the "no
  analog" floor at 0.035, so the drift is free; the live song 0.106 to 0.097. Not bit-identical,
  judged by ear, and the phone went from "barely" to "smooth" that evening.
- **Artifacts.** `audio/MEMORY.md` ("Analog drift steps per block"), the archive record of the
  optimizer stream (census table with the 19 %), `docs/benchmarks/2026-09-15_1755*`, `_1759*`,
  `_1800*`, commit `d4f0d868`, `AnalogDriftRampSpec`, `AnalogDriftSpec` (the exact AR(1) sigma).
- **Mechanics.** (haiku) the rig table before and after. (opus) today's rig rows `full` against
  `no analog` per instrument group (`runSongBenchmark --args=rig`). (sonnet) Fig. 1, per
  instrument: drift per sample, drift per block, no drift (three bars each); Fig. 2, one lane's
  multiplier over 512 samples, per-sample walk against the block-stepped ramp (simulate with the
  coefficients from `AnalogDriftCoeffs.kt`, label as simulation).
- **Code specimens.** `AnalogDrift.beginBlock` and one ramp site in `Ignitors.kt` at `v0.3.13`;
  the per-sample `nextMultiplier` call from `v0.3.12`.
- **Writing.** Fable. Cross-link `2026-06-30-killing-the-plastic-pipe`, whose drift this is.

### P14. `2026-09-15-the-promise-is-a-margin`

- **Story.** The optimizer promised bit-identity, which forbade moving a multiply. The maintainer
  replaced it with a margin ("off by a margin that has no musical meaning"), and the harness
  that holds every rule to it: the rule table, render parity, every builtin song, a seeded fuzz
  with adversarial constants and the pass's laws. The `Affine` node, rule R2, then `div`,
  `minus`, `neg` and the zero divisor as a dead branch. Three review rounds and the one lesson
  they kept finding: every coefficient the optimizer synthesizes must be zero exactly when the
  authored one is, or the dead branch renders on one side only and the voice's noise stream
  slips. And the number: measured alone, R2 changed nothing, which is the honest end of the post
  and the door to P15.
- **Artifacts.** `docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md`
  (steps 0 to 4b with the rule derivations), `audio/MEMORY.md` (the two entries), the specs
  named there, commits `bb9bdbb3`, `a645a97d`, `1056a1c0`, `841cdb8b`, and the review triage
  notes in the session scratch (summarized in the archive record).
- **Mechanics.** (haiku) permalinks at `v0.3.14` to `OPTIMIZER_PARITY` and its KDoc, `Affine`,
  `foldArithmetic`, `negated`, `DivIgnitor.divide`, `withinParity` in the render spec. (sonnet)
  Fig. 1, graphviz: `x.add(1).mul(2).add(3)` authored (three nodes) and optimized (one Affine),
  and `mul(2).add(2).mul(2).add(2)` staying two; Fig. 2, the parity oracle as a picture: a block
  with a zero crossing, the per-sample relative band blowing up at the crossing against the
  block-scale band (simulate, label it). (sonnet) a table of the rule rows (what folds, what must
  not) transcribed from `IgnitorDslOptimizerSpec` at `v0.3.14`.
- **Writing.** Fable. The fuzz seed 433 anecdote, the cancellation example with numbers, and
  what a review loop is for.

### P15. `2026-09-15-measure-before-you-build`

- **Story.** The plan said fold the level knobs into the Eq and the drives into the shaper; the
  census said the guitar had seven `mul` walls and five `Drive` passes. Instead of building ten
  loop variants into EqCore, six benchmark rows built the rhythm rig as an inline tree and
  DELETED the nodes outright: under 4 % of the voice. The same rows put 63 % of the rig in the
  shapers' oversampling, and the decimator rewrite that followed (the same taps read straight
  out of the buffer, bit for bit the ring form) took the guitar voice from 53 to 44 µs on node
  and a 4x shaper from 13.4 to 10.1. Two steps declared won't-implement with the number
  attached, one step never planned that paid.
- **Artifacts.** the archive record (the `guitar-rig*` table, steps 3 and 4), `audio/MEMORY.md`
  ("The oversampler's decimator"), `audio_benchmark/src/commonMain/kotlin/IgnitorBenchmark.kt`
  (the rows), `OversamplerDecimatorParitySpec` (the ring oracle), commit `6c1ae38b`.
- **Mechanics.** (opus) `KLANG_BENCH_FILTER=guitar-rig` and `KLANG_BENCH_FILTER=distort` on node
  and JVM today, three runs; the before numbers exist in the archive record. (sonnet) Fig. 1,
  the six rig rows as a waterfall (string, plus rig, minus muls, minus drives, minus both, minus
  oversampling); Fig. 2, the decimator: the ring read (15 slots, wrapping) against the direct
  read (`s[2m-13 .. 2m+1]`) as a diagram; Fig. 3, before and after bars for the three rows.
- **Code specimens.** `decimate2x` at `v0.3.13` (the ring) and `v0.3.14` (the polyphase pass),
  and the `guitarRig` builder in the benchmark.
- **Writing.** Fable. The method is the post: delete before you fold.

### P16. `2026-09-16-the-score-so-far` (the closing ledger)

- **Story.** One table of every optimization in the series with its number, unit, platform and
  sound cost; then the decomposition of §0 as three figures: the engine on fixed work across the
  tags, the song's work across its snapshots on one engine, and the phone timeline with the
  song's work at each date. The sentence the post exists for: the live song's RTF rose over the
  summer AND the engine's cost per pass fell, and both are true. What is open
  (`docs/tasks/future/ignitor-optimizer-open-items.md`, `affine-chain-fusion.md`,
  `optimizer-on-the-frontend.md`, `high-performance-audio-backend.md`) and what the next
  complexity increase will cost at the current cost per pass.
- **Mechanics, the engine axis** (opus, the big one). For each tag in the map: `git worktree
  add`, build, run `runSongBenchmark --args=songs` (the FROZEN Der Schmetterling and Seltsamere
  Dinge of 2026-07-03, `src/jvmMain/kotlin/FrozenSongs.kt`, whose text has been migrated through
  the door renames so the sound stays identical; a tag before 2026-07-03 has no frozen song and
  is recorded as "not measurable") and `:audio_benchmark:jvmRun` for a fixed set of rows that
  exist at every tag (`sine`, `supersaw_8v`, `pluck+distort_4x`, `supersaw+lpf+adsr`; on node
  too where the tag builds it), record medRTF, peakRTF and the row µs/block, remove the
  worktree. Same machine, same JDK, one session, the machine otherwise idle, three runs per tag.
  The harness fix `39119aef` (2026-09-07) halves every ignitor-row absolute before it: apply the
  known factor of two to the earlier rows and say so in the caption, or, better, transplant the
  fixed harness loop into the older worktree for the run. Tags whose build fails with today's
  toolchain are recorded as such, not skipped silently. Expect a day of wall clock; run it last,
  after every other post is drafted.
- **Mechanics, the song axis** (opus). At HEAD only: render every snapshot of Der Schmetterling
  the repository can reproduce (the 2026-07-03 frozen text; the live text at each tag from
  `git show <tag>:src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt`, migrated where a
  door was renamed, exactly as `FrozenSongs.kt` was; where a snapshot no longer parses, say so)
  through `runSongBenchmark` with the `work` column of §2.5: medRTF, peakRTF, median and peak
  work units per block, voices per block. This is the figure that shows the guitars growing from
  a filtered supersaw into a five-stage rig, in passes per note.
- **Mechanics, the instrument table** (opus): the per-instrument before and after of §2.5 for
  the guitars, the bass, the marimba and the Orchestertrommel: passes per note, active voices per
  block, ns per sample per voice on `v0.3.12` and on `v0.3.14`, ns per sample per pass, bytes
  held per note, buffer reads and writes per sample, and cache misses per sample where `perf`
  is available. This is the table that answers "did the optimizations go the wrong way"
  instrument by instrument, with the instrument held still.
- **Figures** (sonnet drafts, Fable's eyes). Fig. 1, the engine axis: cost per work unit (or the
  fixed rows' µs/block) over tags, the optimizations annotated at their tags; Fig. 2, the song
  axis: work units per block over the song's snapshots, with the live RTF at HEAD beside it;
  Fig. 3, the instrument table as grouped bars: ns per sample per voice before and after, one
  group per instrument, the passes per note printed above each group; Fig. 3b, the same for
  bytes per note and buffer traffic per sample; Fig. 4, the phone timeline
  from P1 with the song's work at each date; Fig. 5, the ledger as a table image if the markdown
  table is too wide.
- **Writing.** Fable. Last.

## 4. Order of work

1. P1 and P16's measurement plan first (the spine), then the posts in the order above; P2 and
   P5 already have backlog rows and can be pulled forward since their material is complete.
2. Per post: (a) a sonnet digest of the artifacts, (b) haiku permalinks and tables, (c) opus
   re-measurements where the plan asks for them, one Gradle build at a time, (d) sonnet figure
   drafts, (e) Fable writes, looks at every figure, fixes the scripts, (f) one opus review pass
   against the artifacts, (g) `status: draft`, a backlog row flipped to `published-draft`.
3. The RTF-over-tags measurement for P16 runs once, at the end.
4. Anything that runs Gradle goes through `console/with-build-lock.sh`; worktrees are removed
   after use; transplanted old implementations are deleted after their number is taken
   (scaffolding rule), except where they already live in a parity spec as an oracle.

## 5. What this plan does not decide

- Working titles, figure counts and the split of P10 into two posts are Fable's calls while
  writing.
- Publishing (`status: published`) is the maintainer's.
- Whether P16's engine-axis measurement is worth its day is the maintainer's call; the series
  stands without it, with the song axis (HEAD only, an afternoon) and the phone timeline as the
  closing figures instead. The work-units counter (§2.5) is needed for the song axis either way.
