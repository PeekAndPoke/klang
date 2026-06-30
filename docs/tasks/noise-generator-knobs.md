# Noise generator calibration knobs (+ a real chaotic Crackle)

> **STATUS (2026-06-30, branch `dsl-enhancements`):** ✅ IMPLEMENTED — all 5 slices done, full suite green
> (`common`/`audio_bridge` jvm+js/`audio_be`/`klangscript`/`sprudel` jvmTest + BuiltInSongsSmokeTest). **Awaiting
> by-ear listening test before archive.**
>
> Shipped: `fbm()` in the `common` Perlin/Berlin cores (octaves≤1 short-circuits → perf-neutral); `Osc.perlin/
> berlin(octaves, persistence)`; chaotic `CrackleIgnitor` (SC Crackle map + DC-block + NaN-guard) replacing the
> dust-alias, `Osc.crackle(chaos)` + `sndCrackle` repointed density→chaos; white-noise spectral tilt
> `Osc.whitenoise(color)` (one-pole tilt, color=0 bypasses → perf-neutral) + `sndNoise(color)`; `Osc.brownnoise(depth)`
> (parameterized white-leak, default reproduces `/1.02` byte-for-byte) + `sndBrown(depth)`; `Osc.dust(tail, bipolar)`
> (heavy-tail + bipolar pops, default byte-identical) + `sndDust("density:tail")`. New `Slots` (color/chaos/octaves/
> persistence/tail/bipolar/depth) → all oscParam-addressable. Constants in `OscillatorTuning` + `NoiseDefaultsSyncSpec`
> guard. Dual-language specs (`KlangScriptNoiseFbmSpec`/`KlangScriptCrackleSpec` + `StdLibOscTest` field checks);
> render-effect guards (`NoiseFbmEffectSpec` + `IgnitorsTest` crackle/dust/white/brown behavioral). Docs +
> vinyl-crackle recipe in the music-writing skill. **Deferred (out of scope, unchanged):** dust `maxRateHz` rate-cap;
> the `snd*`-family cleanup (per-param control patterns / `snd*(named=…)`).
>
> Original plan below.

## Context

The Motör noise generators are mostly unparameterized. From exploration of the live code:

- **white / brown / pink** — zero knobs. Brown's `0.02`/`1.02` (leak) and pink's Paul-Kellet coefficients are hardcoded
  inline (`Ignitors.kt:362-405`).
- **perlin / berlin** — `rate` only; the `common`-module cores (`PerlinNoise.kt`, `BerlinNoise.kt`) are **single-octave
  ** (no fBm).
- **dust** — `density` only; its `maxRateHz` is a factory arg the runtime **never threads** (locked at 200 Hz).
- **crackle** — **is literally a `dust` alias** (`Ignitors.kt:473-476`, `maxRateHz=800`) and **unipolar** — *not* the
  chaotic, bipolar Crackle its name and our docs (`ref/ignitor-reference.md:118`) claim.

Goal: add the high-value, idiomatic noise knobs common in synths — **spectral tilt/color**, **chaos**, **fBm**, and a
few minor knobs — keeping every change **behavior-preserving by default** and **performance-neutral at the default** (
the explicit constraint: a knob may cost more only when engaged; the default path must keep today's per-sample cost).
Vinyl crackle stays a **KlangScript recipe of primitives** (Motör stays raw) — no dedicated generator/preset.

## Scope (all four groups)

### 1. Spectral tilt / "color" on white noise — `Osc.whitenoise().color(x)`

One control tilts the spectrum white→pink→brown one way, blue→violet the other.

- **Engine:** optional first-order **tilt filter** after the white source (`WhiteNoiseIgnitor`). `color = 0` → pure
  white (filter bypassed). `color < 0` → integrate toward brown (one-pole LP, asymptotic −6 dB/oct); `color > 0` →
  differentiate toward blue/violet (+6 dB/oct). Map `color ∈ [-1, 1]` to the pole. Pink (≈ −3 dB/oct) sits near
  `color ≈ -0.5`; **`Osc.pinknoise()` stays the canonical exact-pink Kellet filter** (enrichment, not replacement).
- **DSL/KS:** `IgnitorDsl.WhiteNoise` gains `color: IgnitorDsl = Constant(0.0)` (audio-rate-capable).
  `Osc.whitenoise(color = 0)` + `.color(x)`. Keep `uid`.

### 2. Real chaotic Crackle + `chaos` — `Osc.crackle(chaos)` ⚠ sound change

Replace the dust-alias with a stateful `CrackleIgnitor`: `y[n] = | a·y[n-1] − y[n-2] − c |` (SuperCollider's Crackle;
`a` = chaos ~1.0–2.0, `c` ≈ 0.05), output centered/bipolar.

- **DSL/KS:** `IgnitorDsl.Crackle` field `density` → `chaos`. `Osc.crackle(chaos = 1.5)`. **`dust` keeps the old
  sparse-impulse behavior** (crackle *was* just dust — nothing lost).
- ⚠ **Behavior change:** any song using `crackle` sounds different (becomes a real crackle). Accepted. Fix the
  inaccurate "bipolar" docs.

### 3. Perlin/Berlin fBm — `Osc.perlin(rate, octaves, persistence)` / same for `berlin`

Add fBm to the **`common`-module cores**: `fbm(x, octaves, persistence)` summing `octaves` evaluations at ×2 frequency (
lacunarity 2.0) and ×`persistence` amplitude, normalized.

- **DSL/KS:** `IgnitorDsl.PerlinNoise`/`BerlinNoise` gain `octaves = Constant(1.0)`, `persistence = Constant(0.5)`.
- **Perf-critical default:** `octaves == 1` **short-circuits to the existing single `noise()` call** (no loop, no
  normalization) → byte-identical to today. Cost scales **linearly** with octaves; cap at **8**.

### 4. Misc polish knobs (dust / brown primitives)

- **Brown "depth":** split inline `0.02`/`1.02` into `k` (default 0.02); `out = (out + k·white)/(1+k)` (precompute
  `1/(1+k)` per block). `Osc.brownnoise(depth)`. Same per-sample cost.
- **Dust rate-cap:** thread dust's locked `maxRateHz` as a DSL field; read once per block — zero per-sample cost. (
  Crackle no longer needs it.)
- **Dust bipolar:** optional `bipolar` flag (`rng·2−1` vs `rng`); ~1 op.
- **Dust heavy-tailed amplitude:** optional `k` so amplitude = `rng^k` (default `k=1` = today's uniform →
  behavior-preserving); `k>1` → mostly-tiny / rare-loud, the vinyl-crackle signature. Enables the §5 recipe.

### 5. Vinyl crackle — primitives + documented recipe (decided)

`crackle` stays the **chaotic SC** generator. **No** vinyl generator/preset. Old-record crackle = **combining primitives
in KlangScript** (reusable via export/import). Backed by:

- A **documented recipe** in the music-writing skill: layered `dust` (sparse loud pops + denser quiet crackle) through
  band/high-pass for the "tick" ring, a quiet `pinknoise` hiss bed, optional very-low `brownnoise` rumble — e.g.
  `Osc.dust(0.08).bandpass(2500,4) + Osc.dust(0.02).bandpass(1500).mul(0.6) + Osc.pinknoise().highpass(3000).mul(0.03)`.
- The dust authenticity knobs (group 4) are what make it convincing.

### Constants hygiene (prerequisite)

New **`Noise/Brown/Pink` group in `OscillatorTuning.kt`** (it notes "more families will add groups here"):
`BROWN_LEAK_DEFAULT=0.02`, `DUST_MAX_RATE_HZ=200`, `CRACKLE_CHAOS_DEFAULT`, `PERLIN_FBM_PERSISTENCE_DEFAULT=0.5`,
`NOISE_TILT_DEFAULT=0.0`, etc. DSL data-class default literals mirror these, guarded by a new sync spec — the
`WaveOscDefaultsSyncSpec` cross-module pattern.

## Performance analysis (the figures)

Rough per-sample op counts **per noise instance** (1 flop ≈ one mul/add; rng = one PRNG step). Anchor: a *voice* already
costs **hundreds–thousands of flops/sample**; noise gens are the cheapest elements.

| Generator     | today                       | default after change                    | engaged                                                           |
|---------------|-----------------------------|-----------------------------------------|-------------------------------------------------------------------|
| white         | ~1 rng                      | ~1 rng (color=0 **bypasses** filter)    | **+~5 flops** (one first-order tilt filter)                       |
| brown         | ~4 flops + rng              | **same** (depth=default)                | same (parameterized arithmetic)                                   |
| pink          | ~12 flops + rng             | unchanged (anchor)                      | —                                                                 |
| perlin/berlin | ~20 flops + table lookup    | **same** (octaves=1 **short-circuits**) | **~octaves × 20 flops** (linear)                                  |
| dust          | ~3 flops + rng              | same                                    | +~1 flop/block (rate-cap), +~1 flop (bipolar/amp)                 |
| crackle       | ~3 flops + rng (dust alias) | —                                       | **~5 flops, NO rng** (chaotic recurrence ≈ today, likely cheaper) |

**At 48 kHz, per instance:** white+tilt ≈ **0.34 M flops/s** (negligible — one `onePoleLowpass`). Perlin **4-octave** ≈
**3.8 M flops/s** → **~0.04 % of a core**; **16 voices** × 4-octave ≈ **~0.6 % of a core**. Chaotic crackle ~5 flops, no
rng → ≈ today, likely cheaper.

**Headline:** every default keeps today's cost exactly (tilt bypass, octaves=1 short-circuit, brown/dust defaults). The
only scaling cost is **fBm octaves — linear, opt-in, capped at 8** — worst realistic case well under 1 % of a core.

## Critical files

- `audio_bridge/.../IgnitorDsl.kt` — fields on `WhiteNoise`/`BrownNoise`/`Crackle`/`PerlinNoise`/`BerlinNoise`/`Dust` (+
  `collectParams`).
- `audio_be/.../ignitor/Ignitors.kt` — new `CrackleIgnitor`; widen `whiteNoise`/`brownNoise`/`perlinNoise`/
  `berlinNoise`/`dust` factories; tilt filter; `crackle` no longer aliases `dust`.
- `audio_be/.../ignitor/IgnitorDslRuntime.kt` — thread new fields in the noise `when` branches (`:212-218`).
- `common/.../math/PerlinNoise.kt`, `BerlinNoise.kt` — add `fbm(x, octaves, persistence)` (octaves=1 short-circuit).
- `audio_be/.../ignitor/OscillatorTuning.kt` — new noise constants group.
- `klangscript/.../stdlib/KlangScriptOsc.kt` — widen `whitenoise/brownnoise/crackle/perlin/berlin/dust` methods.
- `audio_be/.../ignitor/IgnitorDefaults.kt` — update the `"crackle"` registration.
- Docs: fix "bipolar crackle" + add a vinyl-crackle recipe to
  `.claude/skills/klang-music-writing/ref/ignitor-reference.md`.

## Tests

- **Dual-language specs** (`KlangScript == Kotlin builder + .copy(...)`) for each new param — mirror
  `KlangScriptSawtoothSpec`.
- **Render-effect guards** (`NoiseParamEffectSpec`, OscShapeEffectSpec-style): perturbing each knob changes the rendered
  block. Deterministic for tilt/brown/crackle/fBm (seed rng where needed); dust paths seed `Random(n)` or compare
  summary stats (RMS/zero-crossings).
- **Sync guard** for the new defaults vs `OscillatorTuning` constants.
- Wire round-trip is automatic (KSP `@WireName` codec) — covered by `IgnitorDslWireCodecSpec` once new fields exist.

## Verification

```
./gradlew :common:jvmTest :audio_bridge:jvmTest :audio_be:jvmTest :klangscript:jvmTest
./gradlew :audio_bridge:jsTest        # wire codec with new fields
./gradlew :jvmTest --tests "*BuiltInSongsSmokeTest"
```

By-ear: `note("c2").s("crackle").chaos(1.7)`, `Osc.whitenoise().color(-0.6)` (≈ pink) vs `.color(0.8)` (airy),
`Osc.perlin(2, 4, 0.6).mul(...)` as modulation; CPU sanity in the live editor with several fBm voices.

## Decisions (final — user, 2026-06-30)

- **`Slots` defined** for the new params (`Slots.color`, `Slots.chaos`, `Slots.octaves`, `Slots.persistence`;
  dust `bipolar`/`tail`). The noise `IgnitorDsl` fields default to them → oscParam-addressable. One name each,
  agreed across layers: `Slots.X` = oscParam key `"X"`.
- **KlangScript `Osc.*` builders take the knobs as named params with defaults** (named args now work in
  KlangScript — the old "no named params" guidance is superseded) → override only what you want:
  `Osc.perlin(octaves = 4)`, `Osc.brownnoise(depth = 0.5)`, `Osc.crackle(chaos = 1.7)`.
- **Sprudel = the EXISTING `snd*` family, COMPOUND colon-string form for now** — `sndCrackle("1.7")`,
  `sndBrown("0.5")`, `sndDust("0.2:1")`, `sndNoise("-0.5")` — exactly like `sndSuperSaw("voices:spread")` parses to
  oscParams. ⚠ **The compound string does NOT yet support per-param control patterns or individual override.**
  That (and a `snd*(named = ...)` form) is a **separate later work-item: the `snd*`-family cleanup** — do NOT get
  pulled into it now. **No new dedicated sprudel verbs** (no `.color()`/`.chaos()`).
- **Perlin/Berlin are not `snd*` sounds** → their fBm (`octaves`/`persistence`) is exposed only via
  `Osc.perlin()/berlin()` named params.
- Specialized noises (vinyl) = **custom KlangScript ignitors** reused via export/import; documented recipes only.
- Keep lean. `fbm()` math in the `common` cores is delivery-agnostic groundwork.
- Implementation order (each a verified slice + render-effect guard): constants → fBm (Osc.perlin/berlin) → chaotic
  crackle (Osc + sndCrackle) → white color (Osc + sndNoise) → dust knobs (Osc + sndDust) → docs/recipe.
