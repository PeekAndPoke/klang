# Pipeline-level oversampling regions (move oversampling off the effects onto the chain)

Status: **future / idea — engine work-stream, not yet scheduled.** Created 2026-07-04. A meaty, sound-affecting
refactor + a real DSL/user-education change; captured while the design context is fresh. Self-contained;
resumable cold. Fits "Sound first" (it's engine work), so it belongs in the work-stream queue alongside voice
culling — not far-future like the native backend. Don't start until the current open threads are closed.

## One-line goal

Stop every nonlinear effect owning its own oversampler. Instead, oversampling is a **positional, absolute
setting on the pipeline** that defines a *region*: everything between an `oversample(N)` marker and the next
marker runs at N× the base rate, with a **single** up-convert entering the region and a **single** down-convert
leaving it. Every effect becomes rate-agnostic; the current rate travels with the audio buffer (in its
processing context). Generalises to Katalyst (bus) and master stages with no new concepts.

```
val mySound = Osc.sine().oversample(4).distort().coarse().oversample(0).lowpass()
//                       └─ up ×4 ──┤ 4× region        ├─ down ×4 ──┘ base rate
```

`oversample(4)` sets the factor to **4** (absolute, not relative); `oversample(0)` (or `1`) resets to base.

## Why (in priority order)

1. **Brilliance — the real motivation.** Two mechanisms make the merged region *brighter/cleaner*:
    - **Fewer anti-alias passes.** Every down/up conversion runs a low-pass that shaves the top octave a
      little. Today `distort(os4).coarse(os4)` does up→distort→**down→up**→coarse→down — two round-trips, two
      extra roll-offs. One region = one round-trip = less cumulative HF loss = less mud.
    - **Less aliasing folded into the audible band.** Nonlinear stages create harmonics above Nyquist that
      *fold down* into the audible range as inharmonic "mud." Oversampling pushes that content up where the
      region's single downsample filter removes it. And because oversampling a region is now cheap/ergonomic,
      users will reach for it more → cleaner sound by default.
2. **Simplification.** distort/coarse/crush stop carrying an oversampler + up/down logic; they become "process
   this buffer at whatever rate it's at." Big reduction in duplicated code and per-effect surface.
3. **Consistency.** One "buffer carries its rate; a region spans stages" model across ignitor → katalyst →
   master. Consecutive nonlinearities interact at the *same* high rate (physically more correct than
   band-limiting between them).
4. **Efficiency — a modest bonus, NOT the headline.** The benchmark shows oversampling is a small cost today
   (distort os4 ≈ +0.001 medRTF). The win is only the *deleted intermediate resamples*, and it scales with how
   many oversampled stages you stack. Sell this as a quality/architecture change with an efficiency bonus.

## Behavioural change — accepted, validate by ear

This changes the sound of stacked oversampled effects (coarse now sees the full high-rate distortion harmonics
with no intermediate band-limit). The user's call: *brighter is better* ("the sound must be brilliant; losing
highs makes things muddy"). Still: like the body→orbit move, it needs by-ear validation and possibly re-tuning
of a few presets. Update goldens once signed off.

## The current state (what changes)

Per-effect oversampling lives in the effect params and renderers:

- `audio_be/.../Oversampler.kt` — the per-effect up/down helper (repurpose this into region resamplers).
- `voices/strip/filter/DistortionRenderer.kt`, `CrushRenderer.kt`, `CoarseRenderer.kt` — each owns its factor.
- DSL params: `distort("amt:shape:OS")`, `coarseos`, `crushos` — these **go away** (see Migration).

## Proposed model

- **A `StageDsl.Oversample(factor)` marker** in the ordered `PipelineDsl`/`StageDsl` list (
  `audio_bridge/.../PipelineDsl.kt`).
  Absolute. `0`/`1` = base. Recommend restricting to **powers of two** (2/4/8/16) — see Design decisions.
- **The block context carries the current rate + length.** `BlockContext` (`voices/strip/BlockContext.kt`)
  gains `currentOversample: Int` and `currentLength: Int` (the block is `blockFrames × factor` samples inside a
  region). "Info carried with the buffer" = carried in the context that accompanies it. Effects read
  `effectiveRate = sampleRate × currentOversample` and iterate `currentLength`.
- **The pipeline builder inserts resample stages** at markers (`buildFilterPipeline`): an up-convert on entering
  a higher factor, a down-convert on leaving, a **resample** on a factor *change* (4→2 is a resample, not a
  halve). Plus a guaranteed **down-convert at the chain boundary** if a chain reaches the send/mix still
  oversampled (output must be base rate).
- **Effects become rate-agnostic**: memoryless shapers need nothing; stateful/time-based effects read the
  effective rate from the context. Same machinery then reused verbatim for Katalyst + master.

## Effects taxonomy (what each needs)

- **Memoryless / rate-independent** (distort, clip, waveshapers, gain): trivial — apply per sample. Free win.
- **Rate-dependent** (any filter, delay, phaser, tremolo/LFO, anything with a time constant): must consume the
  ambient rate — recompute filter coeffs, scale LFO/phase increments, size delay lines for the effective rate.
- **`coarse` is special** — it's *itself* a decimation effect. Define its "reduce to rate R" against the
  **base** rate (musically meaningful) while it *runs* at the ambient rate and lets the region's single
  downsample do the anti-aliasing. Pin this semantic down early.

## Control-graph rate coherence (the modulation/LFO mismatch)

Modulation signals (LFOs, envelopes, patterned params) form a **parallel control graph** that must stay rate-
AND phase-coherent with the audio graph across every oversampling boundary — the control-graph counterpart to
the audio rate-awareness above. Three things must line up:

1. **Length match** — an effect inside a 4× region with a per-sample-modulated param (e.g.
   `.oversample(4).lowpass(sine.range(200,2000).fast(8))`) consumes `blockFrames×4` samples this block, but the
   LFO produced `blockFrames`. The control buffer must be delivered at the region's sample count.
2. **Frequency invariance** — a 5 Hz LFO stays 5 Hz regardless of the region rate: a modulator generated at the
   region rate needs its phase increment scaled by `1/factor`, and its phase accumulator must advance by the
   correct *wall-clock* amount per block so it stays continuous block-to-block. Get this wrong → every
   LFO/tremolo/envelope speeds up/slows down by the oversample factor.
3. **Phase / latency alignment ("the offset")** — if the audio path crossed the boundary through a resampler
   with group delay and the control path didn't match it, modulation drifts against the audio (tremolo dips
   land on the wrong part of the waveform, a filter sweep smears). Control and audio must share the **same time
   reference** across the boundary — same latency discipline as the audio resamplers (decision #3 above).

**Two build options:** **(A, recommended)** generate control at base rate and **upsample the control buffer
into the region** (linear interp is adequate for smooth control), **latency-matched to the audio
up-converter** — keeps modulator sources rate-agnostic, one cheap crossing point. Caveat: control resolution
inside the region is then capped at base rate — fine for LFOs/envelopes, but true *audio-rate* modulation
(sideband-generating) loses detail; document that limit. **(B)** generate the modulator at the region rate
(scaled increment) for full resolution, at the cost of making every modulator source rate-aware and duplicating
a modulator reused at two rates.

**Bounded scope:** this only bites for signals that **vary within the block AND feed an effect inside a
region**. Control-rate (block-constant) params already fold to one per-block value → length-independent → no
mismatch. So the control-resampler is needed for exactly that subset, not everything.

## Design decisions to pin down (the "pay attention to" list)

1. **Resampler quality is now THE sound lever.** With oversampling user-controlled and region-spanning, the
   up/downsample filters dominate quality — a naive linear-interp upsampler + naive decimator would sound worse
   than today's per-effect oversamplers. Use **half-band FIR cascades** for power-of-2 factors (efficient +
   high quality); general polyphase only if non-power-of-2 is allowed. **The downsample filter's cutoff is the
   brilliance-vs-alias knob** — conservative (≈0.9·Nyquist) keeps highs/brilliance but passes more alias;
   aggressive is cleaner but duller. Tune by ear (a Klang-style constant).
2. **Restrict to powers of two?** 2/4/8/16 → clean half-band cascades, simplest length math (block ×/÷ 2ⁿ).
   Arbitrary factors (3×) need general polyphase + non-integer block lengths. Recommend power-of-two for v1.
3. **Latency / group-delay coherence.** FIR anti-alias filters add group delay. Different voices/regions with
   different factors → different latencies → **phase smear / comb-filtering between layers** (superimpose,
   stereo pan copies, parallel body). Either keep filters short & linear-phase and **compensate latency**, or
   fix one region latency budget. This is the subtlest correctness gotcha — easy to miss, audible as smeared
   transients.
4. **Efficiency trap: EVERYTHING in a region runs at N×.** One round-trip saved, but every stage inside runs on
   N× the samples — including stages that didn't need it. Over-scoping a region is a *perf loss*, not a win.
   The example scopes tightly (`oversample(0)` before `lowpass`). This is the #1 user-education point: **keep
   regions minimal — wrap only the nonlinear stages that need it.**
5. **Oversample is STRUCTURAL, not a signal — and for a load-bearing reason, not "it's a count."** The
   ignitor-DSL norm is "everything is a signal": even a *count* like `voices()`/`unison()` can take an LFO
   (allocate the full stack, modulate how many voices are active / their gains — the super-ignitor is built
   for this). So "it's an integer" does NOT make something structural. Oversample is different: a signal is
   *sampled onto the buffer's timeline*, whereas **oversample DEFINES that timeline** — the buffer's
   sample-count and rate. You can't sample the thing that defines the sampling grid onto its own grid (the
   block can't be 512 samples for its first half and 256 for its second). So `oversample()` is a fixed
   mechanical/compile-time pipeline setting — the substrate the signals are drawn on, not a value in it.
   (Keep the two DSLs distinct here: this is about the ignitor/pipeline DSL. Do NOT conflate with sprudel-level
   `.unison(...)`.)
6. **Sample-count constants must scale with the rate.** Any constant expressed in *samples* (not seconds) is
   wrong inside a region: `FILTER_SMOOTH_SAMPLES` (the cutoff coefficient ramp), `envDeclickCoeff` (0.5 ms
   de-click), etc. At 4× a fixed sample count is ¼ the wall-clock time. Audit sample-based constants and derive
   them from the effective rate. Scattered → easy to miss one.
7. **Where do regions live in presets?** The `pedal`/`modern` `PipelineDsl` presets define stage order
   (FilterMod→Vca→Crush→Coarse→Distort→Filter→…). Decide whether presets bake a **default oversample region**
   around their nonlinear cluster (Crush/Coarse/Distort) so existing sounds keep oversampling, while custom
   `Pipeline.of(...)` chains place `oversample()` explicitly.
8. **DC-blocker placement.** Nonlinear stages generate DC (distort has a DC blocker). Decide where DC-blocking
   sits relative to the region — likely at base rate after the down-convert, or once per region — not per
   effect.
9. **Resampler state continuity across blocks.** The FIR up/down filters carry history (previous block's tail)
   → per-voice-region state, allocated once, never reset per block. The variable internal length means the
   region produces `length×N` / `length÷N` samples; the block loop must handle that its internal length differs
   from the voice block length.
10. **Zero-cost when off.** Default / `oversample(0)` must insert **no** resample stages — the non-oversampled
    path stays byte-identical and free (protect the common case).
11. **Scratch sizing.** Region buffers need `maxFactor × blockFrames` doubles. Pre-size `ScratchBuffers` for the
    max supported factor (16× → 16 KB per scratch at 128 frames — fine).
12. **`oversample()` after the source only INTERPOLATES — it does not fix oscillator aliasing.** You can't
    un-alias by upsampling. Klang's oscillators anti-alias via finite-slope edges, not oversampling, so this is
    consistent — but docs must be explicit so nobody expects `.oversample(4)` to clean a raw `zaw`. (Possible
    later extension: allow `oversample(4)` *before* the source so it generates at high rate — bigger change,
    skip for v1.)
13. **Naming / sugar.** `oversample(0)` to reset reads slightly oddly (0 = off); `oversample(1)` (1× = base) is
    arguably clearer — support both. A scoped sugar `oversampled(4){ distort(); coarse() }` could wrap the
    imperative set/reset, but the flat absolute form is the primitive.

## Migration & user education (non-trivial)

- **Remove** the oversample params from `distort`/`coarse`/`crush` (`:OS` suffix, `coarseos`, `crushos`); add
  the `oversample()` marker.
- **Codemod the built-in songs + goldens.** Smart migration: detect a *run* of oversampled effects and wrap the
  whole run in one region (this is the intended sound change), rather than one region per effect (which would
  reproduce the old sound but defeat the point). Der Schmetterling's guitars (`distort("1:tube:4")` +
  `coarse(2).coarseos(4)`) are the canonical case to get right.
- Consider a **deprecation window**: the old per-effect param still parses and maps to a tight one-effect region
  (old sound), with a warning, so existing user songs don't break on day one.
- Docs: a clear "oversampling regions" section — the region concept, keep-regions-minimal, structural-not-signal,
  and the doesn't-fix-osc-aliasing note.

## Testing strategy

- **Null test:** `oversample(1)` / default == the non-oversampled path, byte-identical.
- **Rate-agnosticism:** a stateful effect processed at base vs (up→effect@N×→down) matches within the
  resampler's expected tolerance; add per effect as they're migrated.
- **A/B vs old per-effect oversampling** on the canonical stacked case (distort+coarse) — characterise the
  (intended) brightening.
- **Golden update** once the user signs off by ear; the song benchmark (`runSongBenchmark`) measures the CPU
  delta (expect roughly neutral-to-slightly-better, not a big swing).
- **Latency/phase test:** two layers with different oversample factors summed — assert no unexpected
  comb-filtering (guards decision #3).

## Rough scope / where it touches

- `audio_bridge/.../PipelineDsl.kt` (+ `StageDsl.Oversample`), the sprudel DSL surface for `.oversample()`.
- `voices/strip/BlockContext.kt` (rate + length), `voices/strip/filter/FilterPipelineBuilder.kt` (insert
  resample stages, thread rate), the effect renderers (drop internal oversamplers, read ambient rate).
- `Oversampler.kt` → generalised region up/down/resample renderers with per-voice state.
- Rate-derive the sample-count constants (`FILTER_SMOOTH_SAMPLES`, `envDeclickCoeff`, delay/LFO increments).
- `ScratchBuffers` sizing.
- Later: reuse the same machinery in `cylinders/katalyst/*` (bus) and `MasterStage`.

## Key files / anchors & references

- `audio_be/.../Oversampler.kt`, `voices/strip/filter/{Distortion,Crush,Coarse}Renderer.kt`
- `voices/strip/BlockContext.kt`, `voices/strip/filter/FilterPipelineBuilder.kt`
- `audio_bridge/.../PipelineDsl.kt` / `StageDsl`
- Memory: `pipeline_stage_design`, `engine_dsl_misnamed`, `project_song_cpu_benchmark`, `project_sound_first`.
- Related distortion/oversampling history: `docs/tasks-archive/2026-*` (DC-lock / edge-overshoot notes),
  `audio/MEMORY.md` (numerical safety, distort DC blocker).
