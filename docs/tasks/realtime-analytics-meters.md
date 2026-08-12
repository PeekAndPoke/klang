# Realtime analytics meters — seeing the mix while it plays

> **Status: 🔴 proposed 2026-08-11, not started. Not yet slotted in [`_priorities.md`](_priorities.md).**
> Priority proposal: the balance meter and the limiter GR meter are **SHOULD** ("sound first" — they
> directly shorten the author's edit-listen loop); the rest are **NICE**.
>
> **Where this comes from:** the 2026-08-10/11 Der Schmetterling mixing sessions (klang-ai repo,
> `sessions/20260810-gemini-review/` — 12 versions, each iterated via offline render → 1/3-octave
> measurement → external AI review). Every question those offline tools answered is a question the
> author had to wait a full render cycle for. This doc proposes the realtime versions, ranked by how
> often they were actually needed.
>
> **Decisions already grounded in that session data:**
> 1. **No "harshness" meter.** Perceived harshness never mapped to any measurable band — a 2–5 kHz
>    "buildup" was flagged by review while the band measured 4–7 dB *below* pink. Don't build a meter
>    for a percept with no measurable correlate.
> 2. **Onset-stability stays offline.** It is a statistic over repeated notes (render N repeats,
>    measure the distribution), not a realtime quantity.
> 3. **Low-end judgment must come from measurement, not ears-on-laptop or AI review** — external
>    review flipped polarity on the low end twice in two rounds while measurement was consistent.
>    That is the strongest argument for meter #1 living in the UI permanently.

## 1. What exists today (the delta is small)

| Piece                   | Where                                             | What it gives us                                                                                                             |
|-------------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `AudioAnalyzer`         | `audio_be/src/commonMain/kotlin/AudioAnalyzer.kt` | `getFft(out)` dB magnitudes + `waveform: Stream<AnalyzerBufferHistory>` — the FE data path exists                            |
| `Spectrumeter`          | `src/jsMain/kotlin/comp/Spectrumeter.kt`          | canvas spectrum component, ~30 fps read loop                                                                                 |
| `SpectrumBinning`       | `src/jsMain/kotlin/comp/SpectrumBinning.kt`       | log-spaced buckets, RMS aggregation, **+3 dB/oct slope compensation referenced at 1 kHz — this is already pink-referencing** |
| Limiter/compressor gain | `effects/Compressor.kt`, `master/MasterStage.kt`  | GR is computed every sample today; it is just never published                                                                |
| Per-orbit mix buffers   | `cylinders/Cylinder.kt`                           | per-orbit stereo blocks exist; per-block RMS is a two-line sum                                                               |

Missing entirely: a **control-rate telemetry channel** — engine → FE, a handful of named scalars per block (GR values,
orbit RMS, correlation), the scalar sibling of the existing `waveform` stream. That channel is the one genuinely new
piece of infrastructure; meters 2–5 are all consumers of it.

## 2. The meters, ranked

### 2.1 Spectral balance meter (1/3-octave vs pink) — SHOULD

The realtime version of the offline table that drove the whole session: Welch PSD → 1/3-octave bands (40 Hz – 16 kHz, 26
buckets) → dB **relative to pink** (equal energy per octave = flat line) → anchored so the display shows *shape*, not
level.

Found offline with exactly this view, invisible in the existing Spectrumeter: a +7.6 dB hump at 160–200 Hz masquerading
as "scooped mids", a +5 dB sub excess with a punch *hole* at 80–160 Hz right beside it, and a top octave 12–14 dB below
pink. Each cost a render cycle to see; all would be live glances with this meter.

Design points:

- **Three time layers:** instantaneous bars (fast EMA, ~150 ms) · slow EMA (~2–3 s) drawn as a line — this is the layer
  you mix against · optional session-average ghost.
- **Anchor:** re-anchor each frame to the slow-EMA mean of the 250–500 Hz buckets (the offline scripts' convention, so
  realtime and offline numbers agree). The existing `referenceFreq = 1000.0`
  single-point anchor is more jumpy; a band mean is stabler. Keep both offline scripts and this meter on ONE documented
  convention.
- **Silence gate:** skip frames below an RMS floor so pauses don't drag the averages (offline convention: skip windows <
  1e-4 RMS).
- **Display:** deviation-from-zero bars, ±12 dB scale, the 7 broad regions (sub / bass / lowmid / mid / highmid /
  presence / air) as a summary row.
- ⚠️ **FFT size is the one real problem.** At `fftSize` 2048 / 48 kHz the 40–63 Hz buckets land on 1–2 bins — the low
  bands the meter exists for are exactly the ones it can't resolve. Options:
  raise the analyzer to 16384 (2.9 Hz/bin; ~3 bins at 40 Hz — acceptable), or a second small analyzer tap decimated ×8
  for the bottom three octaves. Decide at implementation; raising the size is the simpler first try, cost is one FFT per
  frame.

UI-only except for the possible FFT-size change. No hot-path cost.

### 2.2 Limiter gain-reduction meter — SHOULD

The "pumping" question consumed three review rounds and an offline envelope-modulation metric, because gain reduction is
invisible. The house limiter (`MasterStage`, 5 ms lookahead) and any authored `MasterFx.limiter()` compute their gain
every sample; publish, per block: **current GR (dB), block-min GR, and a ~10 s history strip** in the UI. Same tap
generalizes to per-orbit Katalyst compressors later.

What it answers at a glance: is the limiter working at all · how many dB on kick hits · does GR return to 0 between
beats (breathing) or stay depressed (the measured 1.25 dB @ 100 ms pump — see
[`master-limiter-lookahead.md`](master-limiter-lookahead.md) §Phase 4, still-open decision) · did a `MasterFx.gain`
change move GR from "occasional −2 dB" to "pinned −6 dB".

It is also the trust indicator for meter 2.1: a program-dependent limiter ducks the low-heavy moments hardest, so under
deep GR the post-master spectrum stops being an honest picture of the *mix*. Measured 2026-08-11: at gain 1.50 the
limiter's spectral fingerprint on Der Schmetterling was ≤ 0.2 dB per band (negligible); at 1.85 drive, a −2.1 dB bass
trim measured back as only −1.7 dB. GR depth tells you which regime you're in.

Needs the telemetry channel. Hot-path cost: one min () per sample already effectively computed; one scalar write per
block.

### 2.3 Per-orbit level ladder — SHOULD (cheap, decide with 2.2)

RMS + peak bars per cylinder/orbit, labeled by what's routed there (lead / guitar 1 / guitar 2 / bass / drums / …), slow
EMA plus peak-hold. Every masking dispute of the session — "snare washed out", "bass buried", "lead never rests" — is
answered by watching which bars are lit and by how much. Also the honest arbiter for arrangement work: it shows *who is
playing right now*, which no spectrum view does.

Needs the telemetry channel; per-block RMS over buffers that already exist. Trivial cost.

### 2.4 Correlation / width meter — NICE

L/R correlation coefficient (+1 … −1, block EMA) plus mid/side energy ratio. Guards the superimpose-pan idiom (the house
stereo technique): instant warning when a patch goes phasey or mono-fragile, quantifies "hole in the middle" / "wall is
centered" review claims. Per-band correlation (3 bands: low/mid/high) is the deluxe version; start full-band.

Needs the telemetry channel. Cost: one multiply-accumulate triple per sample on the master bus.

### 2.5 Dynamics strip — NICE

Short-term loudness + crest factor over a rolling ~10 s. Partially redundant once 2.2 exists (GR history tells most of
the story); build last, or fold into 2.2's strip as a second trace.

### 2.6 Pre/post-master dual view — NICE, but uniquely cheap here

Offline, separating "the mix" from "the master" costs a second render with the limiter idled. In the engine it costs a
second analyzer tap: feed meter 2.1 from BOTH the pre-`MasterStage` sum and the post-master output and show the two
tables (or their diff) side by side. The diff *is* the master chain's live spectral fingerprint — the thing the
2026-08-11 session needed a whole render cycle to obtain. Realtime and nearly free; bundle with Phase 2 since both touch
`MasterStage`.

## 3. Deep analyses — on-demand, too heavy for realtime

The 2026-08-11/12 sessions ran a second kind of tool: analyses over *minutes* of audio that answer structural questions
no realtime meter can. These belong in the UI as **on-demand analyses** — triggered by a user gesture, computed off the
audio and UI threads, results shown as a report panel. Reference implementations exist as Python scripts in the klang-ai
repo (`specbalance.py`, `specdist.py`, `pumping.py`); treat them as the spec.

Downstream consumer: [`auto-mix-advisor.md`](auto-mix-advisor.md) builds attribution ("which orbit owns which band")
and a rule-based suggestion engine on top of these analyses — its P2 phase consumes this doc's capture ring, worker, and
windowed distribution directly.

| Analysis                                 | Question it answers                                                                                                                                                                            | Input                                                    | Cost                                         |
|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|----------------------------------------------|
| **Windowed balance distribution**        | how does the balance move across sections — best/worst windows, dark/bright arc, break contrast (measured: 14 of 16 windows within ±1 dB; the break inverts the spectrum by 40+ dB in the sub) | N-cycle windows over the full song (or last few minutes) | dozens of Welch batches — seconds of compute |
| **A/B snapshot diff**                    | did that knob change do what I meant — the "lowtrim experiment" as a UI gesture: freeze a reference table (a take, a section, pre-change state), diff live or captured audio against it        | two captures (or capture + live slow-EMA)                | one extra table + a diff                     |
| **Envelope-modulation / pumping report** | is the limiter breathing with the beat — modulation depth at beat-rate fractions of the cycle rate                                                                                             | ≥ 30 s of post-master audio + the RPM                    | envelope extraction + one FFT                |
| **Loudness/crest/ceiling report**        | release-readiness numbers: RMS, peak, crest factor, % samples near ceiling                                                                                                                     | full capture                                             | trivial, batch                               |

Two automation notes from the sessions: (a) every analysis needs the **RPM/cycle length** to window musically — plumb
the current playback tempo into the analysis request; (b) reports should always print the *silence-gated* variants, or
quiet sections corrupt the averages.

### 3.1 Architecture: capture ring + worker

- **Capture ring on the master bus** (and optionally the pre-master tap): last 60–120 s of Float32 stereo (48 kHz × 120
  s × 2 ch ≈ 46 MB — acceptable on desktop; make the length configurable). Plus an explicit
  "record this session" mode for full-song reports.
- **Compute placement:** JS → a **WebWorker**, fed via transferable `Float32Array` chunks (zero-copy); the audio worklet
  and UI thread are never blocked. JVM → background coroutine. The analyses are pure functions over arrays — ideal
  worker material, and the same `commonMain` implementation can serve both platforms.
- **Parity requirement:** the Kotlin implementations must match the Python reference scripts on the same WAV to ±0.1 dB
  per band (Welch 16384/Hann/50% overlap, 1/3-octave energy sums, 250–500 Hz anchor, 1e-4 silence gate). Write that spec
  down once — `docs/audio-audit/` style — so the two toolchains stay cross-checkable.

## 4. Suggested phasing

- **Phase 0 — balance meter, UI-only** (`SpectrumBinning` variant + new component; decide FFT size). Independently
  shippable; no engine change if 2048 proves adequate for a first cut.
- **Phase 1 — telemetry channel**: engine → FE named control-rate scalars, one mechanism, sibling of
  `AudioAnalyzer.waveform`. Design once, review once — every later meter is a consumer. ⚠️ JS is the binding constraint
  (`audio/ref/performance.md`): scalar writes per block only, no allocation per block, UI pulls at frame rate rather
  than engine pushing per block.
- **Phase 2 — GR meter** (house limiter first, authored limiters second, orbit compressors later) **+ the pre/post dual
  tap (§2.6)** — same files.
- **Phase 3 — orbit ladder.**
- **Phase 4 — correlation, dynamics strip** — opportunistic.
- **Phase 5 — capture ring + worker harness** (§3.1): the infrastructure for on-demand analyses, plus the loudness/crest
  report as its hello-world.
- **Phase 6 — windowed distribution + A/B snapshot diff**: the two analyses that did the most work in the sessions.

## 5. Open decisions

1. FFT size for the low bands (raise global vs. second decimated tap) — §2.1.
2. Anchor convention: adopt 250–500 Hz band-mean everywhere (this doc's proposal) or move offline scripts to the
   Spectrumeter's 1 kHz reference. Either — but exactly one.
3. Telemetry channel shape: reuse the `Stream` pattern of `AudioAnalyzer` vs. a pull-model snapshot struct the UI reads
   per frame. (Pull avoids per-block allocations on JS; leaning pull.)
4. Where the meters live in the UI (always-on strip vs. a dedicated analytics panel) — author's call.
5. Capture-ring length and memory budget (60 s vs 120 s vs configurable) — §3.1.
6. Worker implementation: Kotlin/JS-compiled worker sharing `commonMain` analysis code (preferred for parity) vs a
   hand-written JS worker.
7. Where the analysis-algorithm parity spec lives (so Python reference scripts and Kotlin ports stay in lockstep).

## 6. Explicitly out of scope

- Harshness/fizz meter (no measurable correlate — see header decision 1).
- Onset-stability metrics (offline statistical tooling; lives in klang-ai analysis scripts).
- Any per-voice (as opposed to per-orbit) metering — voice counts are dynamic and the ladder would be unreadable;
  revisit only with a concrete use case.
- Automated parameter-sweep re-rendering ("what would gain X sound like" batch experiments) — that is offline render
  tooling, not UI analytics; the A/B snapshot diff (§3) covers the measurement half once the author makes the change.
