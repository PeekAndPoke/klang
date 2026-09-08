# Realtime analysis: crowded, thin, loud, and who is responsible

**Status: FUTURE, proposed 2026-09-08. Not started. Gated on the UI structure decision (section 2).**

This plan is the umbrella for three task documents that grew out of the Der Schmetterling mixing
sessions and that describe the same feature from three angles:

- [`docs/tasks/realtime-analysis-ui.md`](../tasks/realtime-analysis-ui.md): the measurement loop
  brought into the UI, "integrate, don't dance", the panels and their priority.
- [`docs/tasks/realtime-analytics-meters.md`](../tasks/realtime-analytics-meters.md): the meters
  ranked by how often the offline sessions needed them, the telemetry channel, the worker harness.
- [`docs/tasks/auto-mix-advisor.md`](../tasks/auto-mix-advisor.md): attribution and a rule engine
  on top of the measurements.

Those three keep their session-derived detail and decisions. This plan adds what they do not
have: a definition of "crowded" that is distinct from "loud", an inventory of what DAWs offer and
what Klang adopts or declines, one architecture that serves every meter, and a phasing that starts
with a UI decision rather than with code.

## 1. Why this exists

The maintainer's question (2026-09-07): can the UI tell the user which frequency band is
overcrowded and which is too thin, where "overcrowded" means not just loud but too many things
sharing the band?

Every DAW answers "loud" with a spectrum analyser. None of them answers "crowded", because a DAW
only ever sees the stereo sum. Klang is the mixer: the engine owns every orbit buffer before
summation, knows every sounding voice and its pitch, knows the tempo and the cycle position, and
shares a namespace with the song's code. So Klang can say "in 160 to 400 Hz, guitar 2 owns 61 %,
bass 14 %, and the band has no clear peaks" where a DAW can only say "160 to 400 Hz is +4 dB".
That is the feature. Everything else in this plan exists to make that sentence true and readable.

## 2. The gate: UI structure first

The maintainer's prerequisite (2026-09-08): before any of this is built, we decide how the UI is
structured. Nothing in phases 2 and later starts before that decision is recorded.

The maintainer's framing of the size of that decision, same day: the UI needs a complete rework
to gain the space for the editor and the analysis tool side by side. The first idea is to hide the
menu when it is not needed, because a permanent menu is lost real estate. That is an idea, not a
decision: the maintainer wants to think deeply about it, and expects to try things. So the gate is
not a document written in one sitting. It is a short series of throwaway layout prototypes, each
tried with the editor and a mock analysis panel on screen, until one feels right. This plan only
asks that the outcome be written down when it is found, so phase 2 has a place to put its panel.

Today the analysis surface is decorative. The Motor component
(`src/jsMain/kotlin/comp/Motor.kt`) stacks an oscilloscope and a spectrum display behind the title
at fixed pixel heights, at 66 % opacity, with pointer events off. That is a visualiser, and it is
right as one. A measurement workbench has different needs: it is read, not glanced at; it needs
Start, Stop, Freeze and Compare gestures; it converges over cycles rather than dancing per frame;
it has rows per orbit and columns per band; and it needs to be readable next to the code editor,
because the whole point is edit, listen, read, edit.

The questions the UI structure decision has to answer, so this plan can be scheduled behind it:

1. **Where do tools live relative to the editor?** A side panel, a bottom drawer, a tab strip, a
   second page. The same question is open for `sprudel-ui-tools` and for
   `runtime-errors-in-the-editor` phase 4, and for the MIDI panel. One answer for all of them, or the
   tools fight for the same space. The menu is part of this question: if it hides when not needed,
   what summons it back, and does the same gesture summon the tools?
2. **What is always on and what is summoned?** A level and loudness strip is a permanent glance
   item. The tonal balance workbench is opened on purpose. The resonance flags are badges that appear
   only when something is found.
3. **What happens on small screens and in the tutorial pages?** The tutorial curriculum embeds
   playable examples; a "mix this" tutorial would want the balance panel inline, so the panel has to
   exist as a component that can be placed, not only as a page.
4. **Does the Motor stay?** The decorative spectrum and oscilloscope keep their job. The workbench
   is a different component with a different data path (section 5). Decide whether the Motor
   becomes the collapsed state of the workbench or stays independent.
5. **How is analysis state scoped?** Per playback (a snapshot belongs to the song that produced it),
   or per session (compare two songs). The scoping decides whether snapshots live in the player or
   in the page.

The decision itself is a separate task, owned by the maintainer, and is not designed here. Phase 1
(engine side, no UI) can proceed in parallel because it produces numbers, not pixels.

## 3. Vocabulary: loud, thin, crowded, masked

Four words, four measures. Every meter and every verdict in this plan is built from these.

| Word        | What the ear means                                             | Measure                                                                                              |
|-------------|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| **loud**    | the band carries more energy than it should                    | band energy in dB relative to the pink-anchored target curve, integrated over cycles                 |
| **thin**    | the band is missing, the mix has a hole there                  | band energy below the target and below both neighbours by more than a threshold                      |
| **crowded** | too many things live in the band, it reads as mud or smear     | density (below) high AND energy at or above target                                                   |
| **masked**  | something is there but you cannot hear it                      | an orbit's share of a band is small while its own level in that band is not (attribution, section 3.3) |

### 3.1 Energy against a target

The existing spectrum display already references pink noise (+3 dB per octave slope compensation
in `SpectrumBinning`), and the meters doc settled the convention: energy per band, expressed in dB
relative to pink, anchored to the band mean of 250 to 500 Hz so that the display shows shape, not
level. Targets are editable per-style presets expressed as a range per region, never a single
line (the auto-mix-advisor doc, section 2). The seven regions are the vocabulary the sessions
settled on:

| region   | Hz          | display name |
|----------|-------------|--------------|
| sub      | 40 to 80    | Sub          |
| bass     | 80 to 160   | Bass         |
| lowmid   | 160 to 400  | Mud          |
| mid      | 400 to 1.5k | Mids         |
| highmid  | 1.5k to 3k  | Edge         |
| presence | 3k to 6k    | Presence     |
| air      | 6k to 12k   | Air          |

A finer 1/3-octave layer (26 bands, 40 Hz to 16 kHz) sits underneath for hunting.

### 3.2 Density: the measure "loud" does not have

Density says how many distinct components share a band. Three estimators, cheapest first, and the
plan uses all three because they fail differently:

- **Spectral flatness per band.** Geometric mean over arithmetic mean of the FFT magnitudes inside
  the band. A band holding one clean partial scores near 0. A band holding a smear of partials from
  several sources, or noise, scores near 1. Cheap: one pass over the bins the band already sums.
  Fails on: a single noisy source (a hi-hat) scores "crowded" while it is one thing. That is why the
  verdict also needs attribution.
- **Peak count per band.** Local maxima that stand more than a threshold above the band's median
  bin. Several peaks a few bins apart from different sources are exactly the beating and mud the
  word "crowded" describes. Needs resolution: two partials 10 Hz apart are one bump at 2048 points
  (section 5.4). Fails on: a rich single source (a supersaw has many peaks by design). Again,
  attribution disambiguates.
- **Contributor count.** From the attribution matrix (3.3): how many orbits each hold more than a
  share threshold of the band's energy. Two or more contributors with comparable shares is the
  strongest "crowded" signal there is, and it is the one no DAW can compute.

The verdict per band, first cut, to be tuned by ear on Der Schmetterling and two other songs
before any threshold is called final:

```
energy   = band dB relative to target range   (below, inside, above)
flat     = spectral flatness                  (0 to 1)
peaks    = local maxima above median + 6 dB
who      = orbits with share >= 20 % of the band

thin     : energy below AND below both neighbours by >= 3 dB
crowded  : energy inside-or-above AND (who >= 2 OR (flat >= 0.6 AND peaks >= 4))
loud     : energy above AND NOT crowded
ok       : otherwise
```

The thresholds are a fixed learnable target (the caricature guideline: no adaptive parameters).
They are constants in one place, and the by-ear tuning record goes into `docs/tasks/by-ear/`.

### 3.3 Attribution: who owns the band

The matrix `orbit × band`, energy per cell, integrated over the same cycle windows as everything
else. Rows are named by what is routed to the orbit: the `tags` on the voice data cross the wire
today (`audio_bridge/src/commonMain/kotlin/VoiceData.kt`), so "guitar 2" is a label the engine can
supply, with "orbit 3" as the fallback.

Two readings from one matrix:

- **Share**: the cell divided by the band's total. "Mud: guitar 2 61 %, bass 14 %, drums 8 %."
- **Masking**: an orbit whose own level in a band is healthy but whose share is small. "Bass is
  present in 80 to 160 Hz at -18 dB but holds 14 % of the band." This is the buried-bass finding of
  round 40, available on day one.

Per-voice attribution is out of scope (the meters doc, section 6): voice counts change every
cycle and the rows would be unreadable. Per-orbit is the unit.

## 4. What DAWs offer, and what Klang does with each

An inventory of the realtime analysis tools found in the DAWs and metering suites people actually
mix with (Ableton, Logic, Cubase, Reaper; iZotope Insight, Ozone and Neutron; Voxengo SPAN; Waves
PAZ; Youlean; FabFilter Pro-Q's analyser), with Klang's decision per tool.

| Tool                                              | What it answers                                     | Klang                                                                                                            |
|---------------------------------------------------|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Peak, RMS, clip indicator                         | is it too hot                                       | **adopt**, level strip, per orbit and master                                                                     |
| True peak (oversampled, ITU BS.1770)               | will it clip after the DAC or the encoder           | **adopt** on the master, the `Oversampler` exists                                                                |
| LUFS momentary, short-term, integrated; LRA       | is it as loud as the platform expects, how dynamic  | **adopt**, master only; K-weighting is two biquads and a gated mean square                                       |
| PSR or PLR (peak to loudness ratio)               | is it over-compressed                               | **adopt**, derived from the two above, one number                                                                |
| Crest factor                                      | dynamics sanity                                     | **adopt** (the sessions used it to overrule a reviewer's "over-limited" claim)                                   |
| Gain reduction meter with history                 | is the limiter working, breathing, or pinned        | **adopt**, house limiter first, authored limiter second, orbit compressors later                                 |
| FFT spectrum, peak hold, averaging, tilt          | where the energy is right now                       | **exists** as the decorative Spectrumeter; stays decorative                                                      |
| Tonal balance against a genre target (Ozone)      | is the shape right                                  | **adopt and extend** with density and attribution: the core panel                                                |
| Masking meter (Neutron, between two plugins)      | which instrument hides which                        | **adopt and exceed**: full orbit × band matrix from the engine, no sidechain wiring                              |
| Spectrogram or waterfall                          | how the spectrum moves in time                      | **decline for now**; the cycle-windowed distribution (meters doc section 3) answers the musical version of this  |
| Mid/side spectrum                                 | what is in the middle and what is on the sides      | **later**, the stereo strip grows a band split                                                                   |
| Correlation meter                                 | mono compatibility, phasey doubling                 | **adopt**, full band first, three bands later                                                                    |
| Goniometer or vectorscope                         | the stereo image at a glance                        | **adopt** as the visual half of the stereo strip; cheap, it is the waveform history plotted L against R          |
| Per-band stereo width                             | is the low end mono                                 | **adopt** as one indicator: correlation below 120 Hz                                                             |
| EQ match, reference overlay                       | how far from a reference track                      | **adopt** as the snapshot A/B (freeze a curve, show deltas); reference tracks from files are out of scope        |
| Tuner, pitch detection                            | is it in tune                                       | **decline**: the engine knows the pitch, there is nothing to detect                                              |
| Key and BPM detection                             | what key, what tempo                                | **decline** for the same reason; instead label the Hz axis in the song's scale (the "small delight" panel)       |
| Transient and onset detection                     | where the hits are                                  | **decline as a meter**; onset stability stays an offline statistic (meters doc, decision 2)                      |
| Beat-synced modulation metering                   | is the limiter pumping with the beat                | **adopt**, the tempo-aware strip; a DAW has to guess the tempo, Klang knows it                                   |
| Harshness or "fizz" meter                         | does it hurt                                        | **decline**: no measurable correlate was found in twelve versions (meters doc, decision 1)                       |
| DC offset, bit meter                              | housekeeping                                        | **decline**: the master DC blockers make DC a non-question, and there is no bit depth to meter before the output |

## 5. Architecture

One architecture, every meter a consumer. The pieces, in the order the signal meets them.

### 5.1 Three taps

| Tap            | Where in the engine                                                                             | Feeds                                                     |
|----------------|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| per orbit      | `Cylinders.processAndMix`, each `cylinder.mixBuffer` after the orbit's bus effects, before the sum | attribution matrix, orbit level ladder, orbit GR later    |
| pre-master sum | the summed mix as it enters `MasterStage.process`                                               | tonal balance of the mix, correlation, crest              |
| post-master    | after the limiter and clip, what the listener hears                                             | true peak, LUFS, house GR, and the pre/post fingerprint diff |

The pre/post pair is uniquely cheap here and is what the sessions needed a whole render cycle to
obtain (meters doc, section 2.6).

All three are read-only taps. They do not touch the audio, so the "Motor stays raw" rule and the
"engine is the horse" rule are not in play. The hot-path rules are: no allocation per block, no
exceptions, preallocated state, flushed IIR state.

### 5.2 Two kinds of measurement, two places to compute them

**Scalar measures** are cheap enough to compute in the engine, every block, on every platform:

- RMS and peak per orbit and per tap.
- Band energy per orbit through a fixed filterbank (seven region band-passes, or the 1/3-octave
  set): per-sample IIR, a few multiplies, exact, no FFT, no resolution problem in the low end.
- Correlation and mid/side energy on the master (three multiply-accumulates per sample).
- K-weighted mean square for LUFS, and the oversampled peak for true peak, on the post-master tap.
- Gain reduction, which the limiter already computes and simply never publishes.

These work identically in the browser worklet and in the JVM offline renderer, which is what makes
the offline reports and the live meters agree by construction.

**Spectral measures** need an FFT: the fine 1/3-octave curve, spectral flatness, peak counting,
resonance flags. An 8192-point FFT costs roughly a third of a millisecond in JavaScript, and the
audio thread has 2.7 ms per 128-frame block, so sixteen orbits' worth of FFTs cannot run on the
audio thread. They run off it:

- the worklet keeps one preallocated ring per tap (2048 to 8192 frames) and posts a transferable
  copy every hop, double-buffered so the transfer costs no allocation in steady state;
- a Kotlin/JS worker compiled from the same `commonMain` analysis code runs the FFTs (the meters doc
  section 3.1 already proposes this worker for the on-demand analyses; it is the same worker);
- on the JVM the same code runs in a background coroutine during offline rendering.

Sixteen orbits at 8192 frames stereo every 43 ms is about 6 MB per second across the message port,
which is acceptable on desktop. The pre-master and post-master taps add two more rings.

### 5.3 The analysis kernel in `commonMain`

A new package in `audio_be` (or its own small module if the worker build needs it; consult before
choosing the module, the complexity rule applies), pure functions over arrays, no platform code:

- real radix-2 FFT with a Hann window, sizes 2048 to 16384, preallocated twiddles and scratch;
- band summation tables for the seven regions and the 26 third-octave bands;
- spectral flatness and peak counting per band;
- Welch averaging (50 % overlap) so the fine curve matches the Python reference scripts to
  within 0.1 dB per band on the same WAV (the parity requirement from the meters doc, section 3.1;
  write the convention down once in `docs/audio-audit/`);
- the K-weighting filter pair, the 400 ms and 3 s loudness windows, the absolute and relative gates;
- the scalar measures of 5.2 as small classes with `process(block)` and `reset()`.

Every function is a candidate for the JVM benchmark module, and every one gets a mutation-checked
spec (the review-loop rule, mandatory tier since it is engine code).

### 5.4 Resolution in the low end

The one physical problem. At 48 kHz:

| FFT size | bin width | bins between 30 and 100 Hz | latency of one window |
|----------|-----------|----------------------------|-----------------------|
| 2048     | 23.4 Hz   | 3                          | 43 ms                 |
| 4096     | 11.7 Hz   | 6                          | 85 ms                 |
| 8192     | 5.9 Hz    | 12                         | 171 ms                |
| 16384    | 2.9 Hz    | 24                         | 341 ms                |

The filterbank has no such problem for energy, so the region bars and the attribution matrix never
depend on FFT size. For density and the fine curve, the recommendation is one 8192-point FFT per
tap with a 2048-frame hop, and 16384 only for the on-demand resonance hunt. Peak counting below
80 Hz is not attempted; a sub band is either thin or not, and "crowded" there comes from the
contributor count alone. Decide at implementation, after measuring the worker's cost; the meters
doc's alternative (a decimated second tap for the bottom three octaves) stays on the table.

### 5.5 Transport: the telemetry message

A new wire feedback type next to `Diagnostics` in `KlangCommLink.Feedback`, emitted from the
dispatcher on the same cadence as the diagnostics (every 20 ms, not every block):

```kotlin
@WireName("analysis")
data class Analysis(
    override val playbackId: String,
    val blockStart: Double,             // engine frame at the start of the window, for cycle alignment
    val master: TapScalars,             // pre and post: rms, peak, truePeak, correlation, mid, side, gr, lufsM, lufsS
    val orbits: List<OrbitScalars>,     // id, label, rms, peak, bands: DoubleArray(7)
)
```

Flat arrays, doubles only, no boxed types, no per-block allocation on the engine side (the message
is built once per emission from preallocated accumulators, the same discipline the warehouse stats
follow). The spectral results from the worker do not cross this wire at all; the worker posts them
straight to the frontend.

The existing `AudioAnalyzer` interface and the browser `AnalyserNode` stay as they are and keep
feeding the decorative Motor. They are not extended: they cannot attribute, they do not exist on
the JVM, and their smoothing is a display choice, not a measurement.

### 5.6 Integration: cycles, not milliseconds

The frontend owns the accumulators, because the frontend knows the cycle position and the user's
Start, Stop and Freeze gestures. Windows are measured in cycles (default 16), aligned to cycle
boundaries via `blockStart` and the playback clock. Per band the accumulator keeps the median and
the p10 to p90 spread, not a running mean, so one loud bar does not drag a whole measurement. A
silence gate (RMS below 1e-4) skips windows so pauses do not corrupt averages. Snapshots are
immutable copies of the accumulator state and export as JSON, which is the same shape a review
agent consumes offline today.

## 6. The deliverables

Named so that the UI structure decision can place them. Each is one component with one data
source.

1. **Tonal balance workbench.** The core. Seven region bars with the target range drawn behind them,
   the median line and spread ribbon converging as cycles accumulate. Each bar is stacked by orbit
   (attribution) and carries a density texture (flatness). Under each bar the verdict badge:
   crowded, thin, loud, ok. Expand for the 26-band curve and the named markers seeded from the
   song's exported frequencies. Start, Stop, Freeze, Compare. Answers the maintainer's question.
2. **Level and loudness strip.** Always on if the UI decision allows it. Per orbit: RMS, peak hold,
   a label. Master: true peak, LUFS momentary and short-term, integrated since Start, crest, PLR,
   the house limiter's gain reduction with a ten-second history.
3. **Stereo strip.** Correlation, mid/side dB, the low-band mono indicator, a small goniometer.
4. **Tempo-aware strip.** Envelope modulation depth at cycle, beat, eighth and sixteenth rates,
   full band and sub band. Klang's structural advantage; no DAW has the tempo.
5. **Resonance flags.** Badges from the worker: a narrow peak or notch that persists while the
   notes change. Appears only when found.
6. **Snapshot A/B.** Freeze, edit, compare, keep. The workflow collapser; if only two things get
   built, build 1 and 6.

The auto-mix-advisor's suggestion engine consumes 1 and 6 and is its own plan.

## 7. Phasing

| Phase | Deliverable                                                                                                       | Depends on          | Ships alone |
|-------|-------------------------------------------------------------------------------------------------------------------|---------------------|-------------|
| 0     | **UI rework**: layout prototypes tried by the maintainer until one holds the editor and a mock analysis panel; the outcome recorded as a task in `docs/tasks/`, answering the five questions of section 2 | the maintainer | n/a |
| 1     | Engine taps, scalar measures, filterbank, the `Analysis` wire message, JVM parity spec, offline report printing the orbit × band matrix | nothing (can start now) | yes: the offline report is useful without any UI |
| 2     | Tonal balance workbench with attribution and the energy-only verdicts (loud, thin), Start, Stop, Freeze           | 0, 1                | yes         |
| 3     | FFT kernel, worker harness, density (flatness, peaks), the full crowded verdict, the fine curve                    | 2                   | yes         |
| 4     | Level and loudness strip, GR meter, true peak, LUFS                                                                | 0, 1                | yes, can run parallel to 2 |
| 5     | Snapshot A/B with JSON export                                                                                      | 2                   | yes         |
| 6     | Stereo strip, tempo-aware strip, resonance flags                                                                   | 3, 4                | each alone  |
| 7     | Hand-off to the auto-mix-advisor: the matrix and the snapshots are its inputs                                      | 5                   | n/a         |

Phase 1 is the one piece of genuinely new infrastructure. Design it once, review it once, and every
later phase is a consumer. It is also the phase that risks the audio thread, so it carries the
mandatory mutation tier and a benchmark entry before and after.

## 8. Open decisions

Consolidated from the three task docs, keeping only what is still open.

1. The UI structure (section 2). Gate.
2. Module placement of the analysis kernel: inside `audio_be` or its own module. The worker build
   may force the answer; consult before adding a module.
3. FFT size and hop per tap (section 5.4), after measuring the worker.
4. The verdict thresholds (section 3.2), by ear, on at least three songs, recorded in
   `docs/tasks/by-ear/`.
5. Anchor convention: the 250 to 500 Hz band mean everywhere, including the decorative
   Spectrumeter's 1 kHz single-point reference, or leave the decorative one alone because it is not
   a measurement. Leaning: leave it alone, document the difference in `SpectrumBinning`.
6. Ring lengths and the memory budget for the capture rings (the meters doc's 60 to 120 s question
   applies only to the on-demand analyses; the live rings are one hop deep).
7. Whether an orbit's label comes from the voice tags, from a new `label` on the orbit, or from the
   pattern's variable name. Tags exist today; start there.

## 9. Explicitly out of scope

- A harshness meter, onset-stability metrics, per-voice metering, parameter-sweep rendering: the
  meters doc's exclusions stand.
- Extending the browser `AnalyserNode` path. It stays decorative.
- Reference tracks from audio files (EQ match against a WAV). The snapshot A/B compares Klang
  against Klang.
- Any automatic change to the song. Verdicts and attribution are readings; the advisor plan owns
  suggestions, and even there nothing is applied without the author.
- Native or Wasm compute for the kernel. Kotlin/JS in a worker first; the performance-backend plan
  owns anything beyond that.

## 10. What we built the plan from

The three task docs are the record of about sixty measurement rounds on Der Schmetterling in
August 2026, where the maintainer iterated the mix against offline scripts and an external
reviewer. The lesson that shaped this plan is theirs: a small set of integrated, pink-referenced,
cycle-windowed measurements did nearly all the work, and every one of them can run live, most of
them better than offline, because the engine knows what a WAV file does not.
