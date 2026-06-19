# Compressor gain smoothing ("ADSR-style" treatment)

> **Status: SHIPPED 2026-06-18.** Implemented in `effects/Compressor.kt` (smoothstep attack↔release blend +
> `GAIN_SKIP_THRESHOLD_DB` cutoff), guard `effects/CompressorSmoothnessSpec.kt`, full `:audio_be:jvmTest`
> green, deployed and confirmed by ear ("the compressor plops are gone"). Tunable consts `ENV_COEFF_BLEND_DB`
> (2.0) / `GAIN_SKIP_THRESHOLD_DB` (−1e-4). Archived from `docs/tasks/`.

## Trigger

Same family of artifact we just chased on the VCA envelope (the low-note "plop" = a slope corner in the
applied gain). The compressor's gain trajectory has analogous hard edges. Goal: make the **compression level
move without stepping**, the way we de-clicked the ADSR — but *inside the compressor's own mechanism*, no
bolted-on extra smoother.

`Compressor` is **one class used in two places** (`effects/Compressor.kt`):

1. Per-cylinder musical compressor — `cylinders/Cylinder.kt:139` (user-facing).
2. Master brickwall limiter — `KlangAudioRenderer.kt:21` (−1 dB ceiling, ratio 20, 1 ms attack, 2 dB knee).

Any change here hits both. The limiter constraint (must keep a fast attack to catch peaks → no overshoot
above ceiling → no clip) is the binding constraint on the design.

## Q1 — what release curve do we use?

**Exponential one-pole, in the dB domain.** Envelope follower (`Compressor.kt:143-144`):

```
coeff       = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
envelopeDb += coeff * (inputDb - envelopeDb)
releaseCoeff = 1 - exp(-1 / (releaseSeconds · sampleRate))     // updateCoefficients(), line 192
```

Same coefficient form as the ADSR de-click. It's an asymptotic time-constant (not a fixed-duration curve like
the ADSR `(e^Kx−1)/(e^K−1)`). **Within a release it is already C∞-smooth** — the roughness is at the
*transitions*, not in the curve itself.

## Approach — option B (chosen)

Discussed and rejected: a stateless / zero-coefficient reformulation (turns the compressor into a waveshaper →
harmonic distortion, worst on low notes), and a symmetric single-coefficient `gr += k·(target−gr)` (loses
independent attack/release → unsafe for the shared master limiter).

**Chosen: remove the *hard edges* in the gain, keep asymmetric attack/release.** Two distinct edges:

### Fix 1 — smooth the attack↔release coefficient switch (`Compressor.kt:143`)

The `if (inputDb > envelopeDb) attackCoeff else releaseCoeff` makes the coefficient a **discontinuous**
function of direction → a slope kink in `envelopeDb` (hence in the gain) at every transition, plus
coefficient *chatter* when the signal ripples across `error ≈ 0`.

Blend instead of switch, branchless, C¹, no transcendental:

```
error = inputDb - envelopeDb
t     = smoothstep01((error + W) / (2·W))         // W = ENV_COEFF_BLEND_DB (dB half-width)
coeff = releaseCoeff + (attackCoeff - releaseCoeff) * t
```

- `error ≥ +W` → `t=1` → full `attackCoeff` (peaks still caught at full speed → **limiter-safe**).
- `error ≤ −W` → `t=0` → full `releaseCoeff`.
- `|error| < W` → smooth blend; no kink, no chatter.
- `smoothstep01(x) = x.coerceIn(0,1).let { it*it*(3 - 2*it) }` — polynomial, cheap.

Note: at `error = 0` the rate is `coeff·error = 0` regardless, so the blended midpoint coefficient only
affects *small* non-zero errors — exactly the transition region we want to soften.

New tunable const `ENV_COEFF_BLEND_DB` (default ~**2.0 dB**). Larger = smoother but softer attack near
threshold; tune by ear.

### Fix 2 — kill the gain-skip step (`Compressor.kt:148`)

```
return if (gainReductionDb < -0.01) exp(gainReductionDb * LN10_OVER_20) else 1.0
```

At the boundary the gain snaps between `exp(-0.01·LN10/20) ≈ 0.99885` and `1.0` — a genuine ~0.12% **step**
every time reduction crosses ≈ −0.01 dB (near-threshold zipper). The branch exists only to skip `exp` when
reduction is negligible; keep the skip but move the boundary to where `exp(boundary) ≈ 1` within ~−100 dB:

```
GAIN_SKIP_THRESHOLD_DB = -1e-4     // exp(-1e-4·LN10/20) ≈ 0.99999 → step < 1e-5 (≈ -100 dB, inaudible)
```

Tiny extra `exp` traffic only for reductions in the −1e-4 … −0.01 dB sliver — negligible.

## Honest scope note

B smooths the **coefficient** and the **gain-curve** edges (the two places the gain is non-smooth *while the
signal is loud*). It does **not** add a one-pole on the final gain. The release *onset* at note-off is an
inherent C¹ corner, but it's mild because the signal is near-silent there, so it isn't excited (unlike the
ADSR plop, where a carrier was still sounding). If a by-ear test still shows stepping under load, the fallback
is a release-direction-only one-pole on the output gain — **deferred**, listed here so we don't forget it.

## No PolyBLEP / flushDenormal (decided)

- **PolyBLEP — no.** It band-limits *oscillator waveform* discontinuities (saw/square) with known sub-sample
  crossings. The compressor gain is a control signal with no known sub-sample crossing and isn't periodic;
  the correct anti-splatter tool for a gain envelope is the smoothing we're doing.
- **flushDenormal — no, by construction.** The only IIR state (`envelopeDb`) lives in the dB domain floored
  at `SILENCE_DB = −100` — it decays toward a normal float, never toward subnormal zero (unlike a
  linear-domain reverb/LPF tail). Gain output is bounded (even −100 dB → `exp(-11.5)` ≈ 1e-5). The two fixes
  add only bounded arithmetic — no new denormal source. This is why `Compressor` has neither call today.

## Files

| Concern                 | File                                                                                                                    |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Both fixes + new consts | `audio_be/src/commonMain/kotlin/effects/Compressor.kt` (single inlined `envelopeStep` → covers stereo + mono `process`) |
| Guard                   | `audio_be/src/commonTest/kotlin/effects/CompressorSmoothnessSpec.kt` (new)                                              |

No call-site changes; both instances inherit the smoother behaviour.

## Verification

- **Existing `effects/CompressorSpec.kt` (7 tests) stay green** — "reduces above threshold", "unchanged below
  threshold", stereo, reset, soft-knee, parseSettings. Defaults chosen so steady-state behaviour ≈ identical;
  these must not regress.
- **New `CompressorSmoothnessSpec`** (guard, EnvelopeDeclickSpec style):
    - Sweep a DC level across the threshold → assert **max per-sample |Δgain| is bounded** (no step), and the
      gain is continuous across the old `-0.01` boundary.
    - **Limiter still bounds**: drive limiter settings (−1 dB / ratio 20 / 1 ms / 2 dB knee) with a hot signal →
      output stays ≤ ceiling (the blend must not let peaks through).
    - Behaviour preserved: still attenuates above threshold, leaves sub-threshold alone.
- `./gradlew :audio_be:jvmTest`.
- **By-ear:** A/B a compressed bus before/after via the recording pipeline; confirm the limiter still feels
  tight on transients.

## Tunable constants (by-ear, per project theme)

| Const                    | Default | Effect                                                                                      |
|--------------------------|---------|---------------------------------------------------------------------------------------------|
| `ENV_COEFF_BLEND_DB`     | ~2.0    | attack↔release blend half-width; larger = smoother transition, softer near-threshold attack |
| `GAIN_SKIP_THRESHOLD_DB` | ~-1e-4  | exp-skip cutoff; sets the (now-inaudible) residual gain step                                |

Not wired to EngineDsl now — internal `const val`, same as the other tuning constants. Could become a
`Stage`/Katalyzer setting later.
