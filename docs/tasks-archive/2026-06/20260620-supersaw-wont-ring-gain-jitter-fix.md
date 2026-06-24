# SuperSaw "won't ring" / per-note volume drift — gain-jitter root cause + fix

> **Status: SHIPPED 2026-06-20.** Root cause found and fixed: the per-voice **gain jitter** was jittering the
> on-pitch **center voice**. Fix = exempt the center voice from the jitter (`Ignitors.computeVoiceGains`).
> `:audio_be:jvmTest` green incl. new `AnalogSawSpec` onset guards. Two earlier *phase* fixes were dead-ends
> (reverted by ear) — kept below so they're not re-attempted. See memory `project_supersaw_onset.md`.

## Context

The mono super-saw (`DetunedStackIgnitor`, `audio_be/.../ignitor/Ignitors.kt` — engine for all `super*`
oscillators) had two long-standing complaints on Der Schmetterling:

- **"won't ring"** — some notes' on-pitch fundamental comes out weak/dull (heard as a "wah/wou" across
  repeated notes), and
- **per-note volume drift** — "every note a different volume / drunk."

(Ruled out along the way: the wood body resonator + resonant Q corners — user removed them, effect persisted;
and the snare "drunk"/"two overlaid" — that was a *sample/authoring* issue, fixed separately by removing the
snare's `.superimpose(bandf(250).bandq(4))` layer.)

## Root cause

Per-note variation came from two independent random things in the unison stack:

1. random partial **phases** (`it.phase = rng.nextDouble()` per voice), and
2. a per-voice **gain jitter** in `computeVoiceGains`: `1 + (rng−0.5)·2·gainJitter`.

The "won't ring" is the **gain jitter**, not the phases. `computeVoiceGains` jittered **every** partial
including the **center voice** (index `(v-1)/2`, detune ≈ 0 — the one carrying the perceived pitch / the
"ring"). A low jitter draw on the center → weak on-pitch fundamental → that note won't ring.

## The fix (shipped) — parameterized center jitter

The center voice gets a **scaled-down** share of the gain jitter (not full, not zero) — `computeVoiceGains`:

```kotlin
val center = (v - 1) / 2
val scale = if (n == center) SUPERSAW_CENTER_JITTER_SCALE else 1.0
val jit = 1.0 + (rng.nextDouble() - 0.5) * 2.0 * gainJitter * scale
```

Side voices always get full jitter **and all phases stay random** → analog character intact. This is the
**gain** axis, not the **phase** axis, which is why it's safe where the phase-anchor wasn't (no "soaring";
timbre untouched). Lets `SUPERSAW_GAIN_JITTER` be pushed high for grit (user set **0.15**) without won't-ring.

**Why parameterized:** fully exempting the center (`scale = 0`) fixed the ring but the user found it
"boring"/flat — the loud center carrying no per-note variation removed liveliness. So
`SUPERSAW_CENTER_JITTER_SCALE` (0..1) is a by-ear dial: `0.0` = stable/boring, `1.0` = original won't-ring
lottery. Default **0.4** (center sees ±0.06 effective jitter vs ±0.15 sides). Applies to all super-* families.

**Caveat:** sum-normalisation already softens the center's swing somewhat (its final gain is `base[center]/s`),
so the scale is the primary knob. If ring-stability ever needs decoupling from the scale entirely, the fuller
fix is *fixed-share* normalisation (center gets a fixed fraction, the rest distributed among the jittered sides).

## Earlier PHASE fixes — BOTH DEAD-ENDS (reverted by ear 2026-06-18). Do NOT re-attempt.

1. **Center-voice phase anchor** (center at phase 0, sides random) → *rings* but **too uniform / "constant
   soaring overlaid"** = auditory streaming (identical attacks fuse into a separate perceptual layer).
2. **Deterministic golden-ratio spread** `frac((i−center)·0.618…)` → **"overtones galore"** (fixed inter-voice
   phase relationships still comb; the off-grid "no N·f comb" theory held on paper, not by ear).

**Lesson:** the random *phases* do real work (organic variation) — keep them. The bug was the *gain*.

## Files

| Concern        | File                                                                                                             |
|----------------|------------------------------------------------------------------------------------------------------------------|
| Fix            | `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt` (`DetunedStackIgnitor.computeVoiceGains`)                   |
| Constant + doc | `audio_be/src/commonMain/kotlin/ignitor/OscillatorTuning.kt` (`SUPERSAW_GAIN_JITTER = 0.15`, center-exempt note) |
| Guard          | `audio_be/src/commonTest/kotlin/ignitor/AnalogSawSpec.kt` (onset tests)                                          |

## Verification

- `AnalogSawSpec` (7/7): two new onset guards — at the production 0.15 jitter, **no dead onsets** across 16
  rng seeds (min onset RMS > floor — the exempt center guarantees a present fundamental), and onsets **still
  vary** across seeds (side jitter + random phases → character preserved, not over-uniformised).
- `./gradlew :audio_be:jvmTest` green (shared engine → covers all `super*`).
- By-ear (user): notes ring consistently at 0.15; analog character intact.

## Related (same session, separate change-sets)

`SUPERSAW_GAIN_JITTER` tuned by ear 0.1→0.0→0.05→0.15; compressor de-click smoothing
(`20260618-compressor-gain-smoothing.md`); default ADSR curves → `exp:exp:exp` — all likely contributed to the
perceived ring improvement too. Constants become per-oscillator-tunable under EngineDsl Phase 2
(`docs/tasks/engine-dsl.md`).
