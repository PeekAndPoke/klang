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

## 3. Suggested phasing

- **Phase 0 — balance meter, UI-only** (`SpectrumBinning` variant + new component; decide FFT size). Independently
  shippable; no engine change if 2048 proves adequate for a first cut.
- **Phase 1 — telemetry channel**: engine → FE named control-rate scalars, one mechanism, sibling of
  `AudioAnalyzer.waveform`. Design once, review once — every later meter is a consumer. ⚠️ JS is the binding constraint
  (`audio/ref/performance.md`): scalar writes per block only, no allocation per block, UI pulls at frame rate rather
  than engine pushing per block.
- **Phase 2 — GR meter** (house limiter first, authored limiters second, orbit compressors later).
- **Phase 3 — orbit ladder.**
- **Phase 4 — correlation, dynamics strip** — opportunistic.

## 4. Open decisions

1. FFT size for the low bands (raise global vs. second decimated tap) — §2.1.
2. Anchor convention: adopt 250–500 Hz band-mean everywhere (this doc's proposal) or move offline scripts to the
   Spectrumeter's 1 kHz reference. Either — but exactly one.
3. Telemetry channel shape: reuse the `Stream` pattern of `AudioAnalyzer` vs. a pull-model snapshot struct the UI reads
   per frame. (Pull avoids per-block allocations on JS; leaning pull.)
4. Where the meters live in the UI (always-on strip vs. a dedicated analytics panel) — author's call.

## 5. Explicitly out of scope

- Harshness/fizz meter (no measurable correlate — see header decision 1).
- Onset-stability metrics (offline statistical tooling; lives in klang-ai analysis scripts).
- Any per-voice (as opposed to per-orbit) metering — voice counts are dynamic and the ladder would be unreadable;
  revisit only with a concrete use case.
