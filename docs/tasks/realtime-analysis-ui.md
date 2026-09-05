# Realtime analysis in the UI — the measurement loop, built in

> **Status (2026-08-25)**: PROPOSED, not started. Distilled from ~50 measurement rounds on
> Der Schmetterling, where every iteration was: edit → offline render → run analysis scripts →
> read numbers → edit again. The finding: a small, stable set of measurements did nearly all the
> work, and every one of them can run live in the UI while the song plays — most of them *better*
> than offline, because the engine knows things a WAV file doesn't (tempo, cycle position, notes,
> orbits, song params).

## The design principle everything hangs on: integrate, don't dance

Every DAW ships an instantaneous FFT analyzer, and nobody can make mix decisions with one — the
display flickers with the music. What made the offline numbers decision-grade was:

1. **Integration over musical time** (windows measured in cycles, not milliseconds),
2. **A fixed reference contour** (dB relative to pink noise, so "flat" means "balanced," not "6 dB/oct tilt"),
3. **Median + spread**, not a live wiggle — a band is described by where it sits AND how much it moves.

The UI flow the maintainer sketched is exactly this: a **Start analysis** button resets the
accumulators; from then on data accumulates and the display *converges* instead of dancing. After
~8 cycles the picture is stable enough to read. Stop/start = new measurement.

## What ~50 rounds actually measured (the tool inventory to port)

| # | Measurement | What it answered | Port to realtime |
|---|---|---|---|
| 1 | **Mix curve** — band energy dB rel. pink, integrated per N-cycle window; per-band median + p10/p90 | "Is the scoop gone? Is the mud back? Did 300–600 fill?" | Core panel (below) |
| 2 | **A/B band delta** — same bands, version N vs N−1 | "What did my edit change?" — the single most-used tool | Snapshot compare (below) |
| 3 | **Crest factor + RMS/peak** | Dynamics sanity; repeatedly overruled an LLM reviewer's "over-limited" hallucination | Level strip |
| 4 | **Beat-rate articulation** — envelope modulation depth at cycle / beat / 8th / 16th rates | Caught release-tail smear, confirmed pluck rebuilds, exposed doubling phaser | Tempo-aware strip (unique to Klang) |
| 5 | **Stereo** — side/mid energy, L/R correlation | Caught the doubling comb (fake width), confirmed placement panning (real width) | Stereo strip |
| 6 | **Narrow-band anomaly probes** — 20–60 Hz-wide bands around suspects | Found a +12 dB fixed string resonance at 2263 Hz and a persistent 220–260 Hz carve hole | Resonance flags (below) |
| 7 | **Stem-vs-mix masking** (offline solo renders) | "Bass is physically present but 30 dB under the mix in its own band" | Per-orbit attribution (below) |

## The panels

### 1. Mix curve (the core)

Accumulating band bars: dB relative to pink per band, median line + p10–p90 ribbon, converging as
cycles accumulate. Canonical regions (the vocabulary the whole iteration loop settled on):

| region | Hz | display name |
|---|---|---|
| sub | 40–80 | **Sub** |
| bass | 80–160 | Bass |
| lowmid | 160–400 | **Mud** |
| mid | 400–1.5k | Mids |
| highmid | 1.5k–3k | Edge |
| presence | 3k–6k | **Presence** |
| air | 6k–12k | **Air** |

Name only the evocative ones prominently (Sub, Mud, Presence, Air); the rest can just show ranges.
Underneath the region bars, keep a finer resolution (⅓-octave or the 26-band table) available on
expand — region bars for decisions, fine bands for hunting.

**Named markers, seeded from the song.** Thin vertical flags at arbitrary frequencies, user-named
("snare slot", "lead home"). Because song params live in the code, markers can be seeded
automatically from exported values (`snareHz`, `leadHz`, …). No DAW can do this — the analyzer and
the song share a namespace.

### 2. Snapshot A/B (the workflow collapser)

One button: **freeze** the current accumulated curve as reference. Edit code; analysis restarts on
the running playback; the display shows **delta bars vs. the frozen snapshot** (the offline
`specdiff` table, live). This collapses the offline loop — render, script, compare, minutes per
iteration — to seconds, without stopping the music. If only two things get built, build the core
panel and this.

Snapshots should be keepable (small JSON: band medians + spreads + level stats) — that turns the
session-artifact habit ("v49 vs v48") into a first-class UI object, and gives A/B/C comparisons.

### 3. Tempo-aware strip (Klang's structural advantage)

Crest factor, RMS, and **articulation meters**: envelope modulation depth at cycle, beat, 8th, and
16th rates. The engine knows `cps` and the cycle position exactly, so beat-synced metrics are free
and exact where a DAW analyzer would have to guess tempo. Four small bars labeled in musical units.
When release tails smear the groove, the 16th bar visibly dies; when a sidechain pumps, the beat
bar in the sub band spikes. (Same machinery, two readings: modulation in the full band = articulation;
modulation isolated to the sub band at beat rate = pumping.)

### 4. Resonance flags (the automated bug-hunter)

Accumulate a high-resolution average spectrum; flag narrow peaks/dips deviating >~6 dB from the
local spectral median **that persist while the notes change**. The engine knows which notes are
sounding (voice events carry pitch), so it can distinguish "peak that follows the music" from
"fixed resonance parked at 2263 Hz." The fixed-Hz-filter bug class — which cost days of by-ear
hunting — becomes a badge: `⚠ constant peak 2263 Hz`. Same mechanism flags persistent notches
("carve hole 220–260 Hz") which are over-EQ symptoms.

### 5. Stereo strip

Side/mid dB, L/R correlation, and a low-band mono indicator (correlation below ~120 Hz). One-line
lesson from the song iterations: correlated doubling reads as width but measures as a comb; this
strip is what tells the difference.

### 6. Per-orbit attribution (the flagship, later)

Because the orbit buses exist separately in the engine (the Katalyst layer processes them), each
orbit's contribution per region can be metered before summation: a live **masking view** — "in
Mud: guitars 78%, bass 14%, drums 8%." The buried-bass problem would have been visible on day one
instead of round 40. The `.tag()` vocabulary (voice tags cross the wire) gives human names for the
rows. Even a coarse first version — per-orbit RMS per region — exceeds anything a DAW offers,
because a DAW only ever sees the sum.

### Small delight: note-named frequency axis

Label the Hz axis in **note names, in the song's key** (the engine knows the scale from the
pattern). Iterating the song involved constant by-hand Hz↔note conversion ("tune `midsHz` to a
fifth on the scale"). The analyzer should say that 660 Hz *is* E5 and highlight scale degrees.

## Implementation notes

- **Windowing by cycles, not wall-clock.** All accumulation aligned to cycle boundaries via the
  engine clock; window length in cycles (default ~16) — this is what makes median/spread musically
  meaningful.
- **Accumulators are cheap.** Band energies via a fixed filterbank or FFT binning per block;
  Welch-style averaging; envelope followers per rate band. Nothing here threatens the audio thread;
  heavy display math (percentiles) can live frontend-side on the transported per-block band frames.
  The jsMain analyzer buffer plumbing that feeds the existing visualizations is the natural
  transport to extend.
- **Reset semantics**: "Start analysis" clears accumulators; snapshots are immutable copies.
  Auto-reset on code eval is worth considering (an edit invalidates the measurement) — but keep the
  frozen snapshot across evals, since "before my edit vs after" is the whole point.
- **Pink reference**: store the reference contour once (analytic −3 dB/oct on power), apply at
  display time, so raw accumulators stay reference-agnostic.
- **Export**: a snapshot exports as JSON (band stats + crest/RMS + stereo + flags). This is the
  bridge to the agentic loop: the same numbers a review agent consumes offline today.

## Priority

1. Mix curve panel with Start/Reset + named regions + song-param markers
2. Snapshot A/B deltas
3. Tempo-aware strip (crest + articulation)
4. Resonance/notch flags
5. Stereo strip
6. Per-orbit attribution
