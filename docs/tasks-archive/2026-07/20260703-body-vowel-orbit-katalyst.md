# Move body / vowel from per-voice filters to orbit-level Katalyst effects

> **ARCHIVED 2026-07-04 — DONE.** Orbit-level Katalyst body/vowel shipped (see
`audio_be/.../cylinders/katalyst/KatalystBodyEffect.kt`, `KatalystFormantEffect.kt`, wired in `Cylinder.kt`). Only
> by-ear sign-off / song re-orbiting remains.

Status: **IMPLEMENTED + measured 2026-07-03.** Awaiting by-ear sign-off + song re-orbiting (see conflict table).

## Result (same-machine A/B, frozen Der Schmetterling, JVM)

| metric                       | baseline (per-voice body) | orbit-level body |          change |
|------------------------------|--------------------------:|-----------------:|----------------:|
| medRTF (sustained)           |                   0.10228 |          0.08265 |        **−19%** |
| peakRTF (worst render block) |                 **0.936** |        **0.256** | **−73% (3.7×)** |

The worst render block (busy section, all guitar voices active — the browser ~50% number) fell from 94% →
26% of real-time on JVM. The within-run 2×2 confirms the mechanism: body cost is now flat vs `superimpose`
voice count (+0.0027 with or without the doubling; was multiplicative before). `:audio_be:jvmTest` green.

Implemented: `KatalystBodyEffect`/`KatalystFormantEffect` (`cylinders/katalyst/`), `Cylinder` pipeline
`[body, vowel, delay, reverb, phaser, compressor]` + routing in `updateFromVoice` + `reset()` on
deactivate, `Voice.body`/`Voice.vowel` carriers, `VoiceFactory` extracts Body/Formant out of the per-voice
chain. Tests: `KatalystBodyEffectSpec`, pipeline-size + one-pass-per-orbit assertions in
`CylinderKatalystPipelineSpec`.

---
Created 2026-07-03.

## Context

The song CPU benchmark (`runSongBenchmark`, `docs/benchmarks/2026-07-03_der-schmetterling-cpu-analysis.md`)
showed that for Der Schmetterling ~98% of the cost is the 3 super-synth voices, and that `body("wood")`
(an 8-band parallel SVF bank, `+0.0066` medRTF on GTR1) runs **per voice** — so it is multiplied by every
`superimpose` copy and every unison note. On the guitar orbits that is ~544 body instances per block.

`body`/`vowel` are timbre resonators, not per-note articulators. Moving them to the **orbit bus** makes them
**O(orbits)** instead of **O(voices)** — for Der Schmetterling's guitars, ~544× → 1×. This is the single
biggest absolute-CPU lever for these effects and it benefits every platform (JVM, JS, future Wasm).

**Decision (user):** body/vowel become **orbit-level only** (replace the per-voice path). The orbit *is* the
grouping unit — voices needing independent body/vowel go on different orbits. Hardcode the Katalyst order
for now (a Katalyst DSL comes later). Per-voice body/vowel may return later via the **ignitor** path if an
individual voice needs it.

**Accepted sound change (validate by ear):** body now resonates the *summed, already-distorted, panned* mix
(`body(Σ distortᵢ) ≠ Σ body(distortᵢ)`), and it is stereo. Typically fuller / roomier.

## Current wiring (what changes)

- `.body("wood")` / `.vowel("i a e")` → `SprudelVoiceData` resolves to `FilterDef.Body(bands, mix)` /
  `FilterDef.Formant(bands, mix)` and adds it to the voice's `FilterDefs`
  (`SprudelVoiceData.kt:868,877`). **Keep this — the DSL + FilterDef types are unchanged.**
- Per-voice application today: `VoiceFactory.toFilter()` builds them via
  `LowPassHighPassFilters.createBody/createFormant` into the voice's baked filter pipeline
  (`VoiceFactory.kt:356-358`). **Remove Body/Formant from that path.**
- `createBody(bands, mix, sr)` = `ParallelMixFilter(BodyFilter(bands, sr), amount = mix, floor = BODY_FLOOR)`;
  `createFormant(...)` = `ParallelMixFilter(FormantFilter(bands, sr, VOWEL_TAME), mix, VOWEL_FLOOR)`
  (`LowPassHighPassFilters.kt:269-277`). Both are **mono** `AudioFilter`s. **Reuse them as-is.**
- Bus pipeline: `Cylinder.pipeline: List<KatalystEffect> = listOf(delay, reverb, phaser, compressor)`
  (+ ducking, applied separately). `Cylinder.updateFromVoice(voice)` reads `voice.reverb/delay/phaser/…`
  into the Katalysts each block (last-writer-wins) (`Cylinder.kt:97-155`). **Add body/vowel here.**

## Design

### 1. New Katalyst effects — `KatalystBodyEffect`, `KatalystFormantEffect`

`cylinders/katalyst/KatalystBodyEffect.kt` (and `…FormantEffect.kt`), implementing `KatalystEffect`.
Because `createBody`/`createFormant` are **mono** and the cylinder mix is **stereo**, hold **two** filter
instances (left, right) with independent state:

```kotlin
class KatalystBodyEffect(private val sampleRate: Double) : KatalystEffect {
    private var active = false
    private var curBands: List<FilterDef.Body.Mode>? = null
    private var curMix = Double.NaN
    private var left: AudioFilter? = null
    private var right: AudioFilter? = null

    /** Configure from a voice's body (null = leave unchanged — see routing note). */
    fun configure(body: FilterDef.Body?) {
        if (body == null) return
        if (body.bands != curBands || body.mix != curMix) {              // rebuild only on material/mix change
            left  = LowPassHighPassFilters.createBody(body.bands, body.mix, sampleRate)
            right = LowPassHighPassFilters.createBody(body.bands, body.mix, sampleRate)
            curBands = body.bands; curMix = body.mix
        }
        active = true
    }

    fun reset() { active = false; curBands = null; curMix = Double.NaN; left = null; right = null }

    override fun process(ctx: KatalystContext) {
        if (!active) return
        left?.process(ctx.mixBuffer.left, 0, ctx.blockFrames)
        right?.process(ctx.mixBuffer.right, 0, ctx.blockFrames)
    }
}
```

- **Insert (mix-in-place) effect**, like phaser/compressor — reads/writes `ctx.mixBuffer`. The
  `ParallelMixFilter` inside already does dry/wet (`out = dryGain·dry + amount·wet`), so processing the mix
  in place is correct.
- Rebuild only when `bands`/`mix` change (data-class structural equality) → effectively once per orbit.
  `body.mix`/`bands` are constants resolved at query time (no per-block modulation), so this is cheap.

### 2. Hardcoded pipeline order in `Cylinder`

```kotlin
val body   = KatalystBodyEffect(sampleRate.toDouble())
val vowel  = KatalystFormantEffect(sampleRate.toDouble())
val pipeline = listOf(body, vowel, delay, reverb, phaser, compressor)
```

body/vowel first (resonate the dry mix), then time (delay/reverb) and dynamics (phaser/compressor).

### 3. Route voice → cylinder

- `Voice` gains `val body: FilterDef.Body?` and `val vowel: FilterDef.Formant?` (carry the resolved config,
  like `voice.reverb`). Set in `VoiceFactory` by extracting the Body/Formant `FilterDef`s from the voice's
  `FilterDefs` (instead of baking them into the filter pipeline).
- `Cylinder.updateFromVoice(voice)` calls `body.configure(voice.body)` and `vowel.configure(voice.vowel)`.

**Routing note — null = no-op, not deactivate.** `updateFromVoice` runs once per voice that sends to the
orbit; a non-body voice must NOT turn the orbit's body off. So `configure(null)` leaves the current config
untouched; only a body voice (re)configures. This yields the intended "orbit has a body or it doesn't"
model: for a uniform orbit it's correct; for a *mixed* orbit (a flagged conflict) body applies to the whole
orbit (the warned behaviour). `reset()` is called when the cylinder is fully cleared/evicted so a reused
cylinder starts clean.

### 4. Remove per-voice body/vowel

In `VoiceFactory`, exclude `FilterDef.Body`/`FilterDef.Formant` from the per-voice filter-pipeline build
(and from `toModulator`, already null for them). Everything else (LowPass/HighPass/BandPass/Notch) stays
per-voice.

## Songs that need re-orbiting (user will listen + fix after impl)

Two conflict types once body/vowel are orbit-level:

- **A — multiple body/vowel MATERIALS on one orbit** → last-writer-wins keeps one material; others lose theirs.
- **B — a body/vowel voice shares an orbit with non-body voices** → the resonance bleeds onto them.

| Song                                  | Orbit(s)                            | Issue                                                                                    | Fix                                                                      |
|---------------------------------------|-------------------------------------|------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| **Seltsamere Dinge** (StrangerThings) | orbit 1                             | **A**: melody base `wood` + its superimpose `glass`                                      | move the glass superimpose to its own orbit                              |
| **Seltsamere Dinge**                  | orbit 0                             | **B**: lyrics `membrane` + claps + shore (no body)                                       | give the lyrics voice its own orbit                                      |
| **Sakura**                            | (no `.orbit()` set → all default 0) | **A + B**: `wood` + `glass` + `tube` bodies **and** every other voice all on orbit 0     | assign distinct orbits per body voice + the rest                         |
| **IrishLamentTechno**                 | orbit 0                             | **B**: `membrane` lead shares orbit 0 with the saw melodies (`mel1/mel3`, others)        | move the membrane voice to its own orbit                                 |
| **SoundOfTheSea**                     | orbit 1                             | **B? (verify)**: glockenspiel `glass` on orbit 1 — check nothing non-body shares orbit 1 | re-orbit if it shares                                                    |
| **Der Schmetterling**                 | orbit 1                             | cosmetic: Guitar1 + Guitar2 both `wood` on orbit 1 → now ONE shared body (was two)       | user preference: split guitars to orbit 1 & 2 for two independent bodies |
| **ATruthWorthLyingFor**               | orbits 1/2/3                        | none — each guitar on its own orbit, all `wood`, no non-body co-tenants                  | —                                                                        |
| **Tetris**                            | orbit 0                             | none — lead `wood` + its superimposes only, no non-body co-tenant                        | —                                                                        |

(Confidence: table is from static reading of orbit/`.body()`/`superimpose` placement; final call is by ear
after the change. superimpose copies inherit the base voice's orbit + body unless the lambda overrides.)

## Verification

- `runSongBenchmark --args=songs` on the frozen Der Schmetterling **before/after** — expect a large medRTF
  drop (the guitars' body cost collapses from per-voice to per-orbit). GTR1's isolated `+body` rung should
  no longer scale with voice count.
- `./gradlew :audio_be:jvmTest` green. Existing `BodyFilterSpec` / `FormantBlendSpec` / `ParallelMixFilterSpec`
  still cover the filters. Add a cylinder-level test: an orbit fed N voices with `FilterDef.Body` runs the
  body filter **once** (per channel), not N times; a voice without body doesn't deactivate a configured
  orbit body; `reset()` clears it.
- By-ear A/B on Der Schmetterling (guitars split to separate orbits) — user signs off on the summed-body
  sound (`body(Σ distort) ≠ Σ body(distort)`).

## Effort

Medium. New: 2 small Katalyst classes. Edits: `Cylinder` (2 fields + pipeline order + 2 `configure` calls +
`reset` wiring), `Voice` (+2 fields), `VoiceFactory` (extract Body/Formant to the Voice, drop from the
per-voice pipeline). No DSL / `FilterDef` / sprudel changes. Golden/tests + a new cylinder test.

## Follow-ups (out of scope here)

- A **Katalyst DSL** to configure orbit effects explicitly (this hardcodes the order for now).
- **Per-voice body/vowel via ignitors** for voices that genuinely need their own resonance.
- **Voice culling** (separate task doc / plan) — revisit after this lands.
