# The Plastic-Pipe Hunt

Last updated: 2026-05-27.

Multi-phase investigation into why the Klang audio engine sounded synthetic /
"plastic pipe" on warm-analog patches — most clearly heard on the bass + pad of
the built-in song **Der Schmetterling**. Each phase targets one suspected
contributor; the engine has been progressively warmed up but the work is not
finished — see "Open backlog" at the bottom.

---

## Diagnosis findings

### Engine-side contributors (in rough order of audible impact)

1. **Linear ADSR envelopes.** Attack/decay/release ramped as straight lines —
   real instruments dissipate energy as a fraction per unit time
   (exponential-ish), not a constant per unit time. → **Fixed in Phase 1.**
2. **Single shared analog drift across unison voices.** SuperSaw's per-voice
   pitch drift was one shared Perlin walk → all unison voices wobbled in
   lockstep. → **Fixed in Phase 2.**
3. **Perlin lattice for drift.** Lattice-based noise has a characteristic
   timescale; real analog drift is broadband and mean-reverting (Ornstein–
   Uhlenbeck). → **Fixed in Phase 3.**
4. **Mathematical-pole-pair filter resonance.** SVF feedback was 100% linear, no
   saturation. Resonance was a pure spike — no peak compression, no harmonic
   generation, no velocity-through-filter feel. → **Fixed in Phase 7 (SvfLPF
   only — HPF/BPF/Notch still linear, deferred until audibly needed).**

### Patch-side contributors (Schmetterling bass, identified during bisection)

The Schmetterling bass chain is **doing several things that always read as
synthetic**, on top of the engine baseline:

- `.notchf(400)` sits exactly in the bass-body / "belly" region (200–500 Hz).
  Removing it returned the wooden warmth. User confirmed during bisection.
- `.superimpose(scaleTranspose(4) …)` adds a fifth-up parallel voice that
  shares the same envelope + filter sweep. The stacked harmonic interval reads
  as chiptune / calliope. User confirmed contributes.
- `.lpe(2.0)` filter envelope opens the cutoff 2 octaves on every note with a
  10 ms attack → identical "wah" transient on every note-on, the classic
  subtractive-synth tell.
- Only 3 unison voices (vs 8 for the lead) → comb-filter beating is audible.
- Sustained level holds at exactly 0.66 of full on every note → no natural
  per-note timbre variation during the hold.

These are patch-design issues; **engine improvements can only do so much** when
the patch leans into synth tropes.

### Honest pattern observed

User reported plastic concentrated in **low frequencies**. Higher notes sounded
fine. This implicated the bass chain specifically more than the engine
baseline.

---

## Work done

### Phase 1 — Per-stage ADSR shape curves *(shipped)*

- `AdsrDef` became a `@Serializable sealed interface` with `Std` variant for
  forward-compat (so future `Drawn` / `Wavetable` variants can slot in).
- Added `AdsrCurve` enum: `Linear`, `Square`, `Cube`.
- Each ADSR stage (attack / decay / release) gets its own curve. Math is
  multiplies-only — no `pow()`, no LUT. `Square = p²` adds 1 mul/sample over
  the linear path.
- Default changed to `Square` on all three stages.
- Curve dispatch lives in three rendering sites:
    - `EnvelopeRenderer.kt` (main VCA)
    - `IgnitorEnvelopes.kt` (`AdsrIgnitor.generate()`)
    - `EnvelopeCalc.kt` (control-rate envelope for FM depth + filter mod)
- Sprudel DSL: `.adsrCurves("linear:square:cube")` (per-stage) and
  `.adsrCurve("square")` (all-three).
- KlangScript: `adsrCurves(self, "square", "square", "square")` — separate
  method because KlangScript has no overloads; `adsr()` itself unchanged.
- Tests: `AdsrDefTest`, `EnvelopeShapeTest`, `LangAdsrCurvesSpec` + updates to
  ~30 existing envelope-related tests.

### Phase 2 — Per-voice unison drift in SuperSaw *(shipped)*

- New `PolyAnalogDrift` class with per-voice independent drift state +
  per-voice ±25% step jitter + per-voice ±5% detune jitter.
- `SuperSawIgnitor` restructured into three code paths:
    - **Analog (sample-major)** when `analog > 0 && phaseMod == null` — uses
      `drift.advanceAll()` + `multipliers[n]` per voice per sample.
    - **Clean digital (voice-major)** when `analog == 0 && phaseMod == null` —
      `dt` is constant per voice, register-resident, no drift work.
    - **Modulated (sample-major)** when `phaseMod != null` — per-sample
      detune recompute, conditional `advanceAll()` on drift.active.
- `advanceAll()` is `inline fun` with `@PublishedApi internal` state, so the
  body expands at the call site on Kotlin/JS (no per-sample method dispatch).
- Other 5 super-oscillators **not yet rippled** — still use mono
  `AnalogDrift` internally. See backlog.

### Phase 3 — Two-timescale Ornstein–Uhlenbeck drift *(shipped)*

Replaced the Perlin-based drift with the statistically more honest OU model
recommended by an external Claude consult: smoothed white noise with
mean reversion.

- **Fast layer**: one-pole LPF on white noise. τ ≈ 50 ms, the constant
  micro-wobble of a real VCO.
- **Slow layer**: Ornstein–Uhlenbeck process. τ ≈ 10 s with β = 0.5α mean
  reversion. The lazy "breathing" drift over seconds.
- Inline xorshift32 white-noise source — **no `Random.nextDouble()` in the
  hot path**. Per sample per voice: 3 xorshift ops + 4 muls + 4 adds.
- Steady-state Gaussian seeding via Box-Muller — drift is immediate on
  note-on, no ramp-up (otherwise slow layer would need ~30 s to reach SS).
- **Sample-rate-aware**: coefficients computed from `sampleRate` passed into
  constructor; `Ignitors.initAnalogDrift` and `initPolyAnalogDrift` thread
  `ctx.sampleRate` through; `SampleIgnitor` gained `sampleRate` constructor
  param (with ~19 test sites updated to pass `sampleRate = 48000`).
- Both `AnalogDrift` (mono) and `PolyAnalogDrift` (poly) use the new model.

### Phase 4 — Retune to clean `analog=N ≈ ±N cents` mapping *(shipped)*

Original two-layer defaults gave ±3 cents at `analog=1` (±0.5 fast + ±2.5
slow). User wanted simpler reasoning: `analog=N` ≈ ±N cents total.

Scaled the cents-per-analog constants by 1/3, preserving the 20/80
fast/slow split:

- `ANALOG_FAST_PEAK_CENTS`: 0.5 → 0.2
- `ANALOG_SLOW_PEAK_CENTS`: 2.5 → 0.8

Total at `analog=1` is now ±1 cent. Clean linear mapping.

### Phase 5 — Deduplicate analog-drift constants *(shipped)*

Both `AnalogDrift` and `PolyAnalogDrift` had identical companion constants +
identical coefficient math. Extracted to a single file
`audio_be/.../ignitor/AnalogDriftCoeffs.kt`:

- Nine `@PublishedApi internal const val ANALOG_*` tuning constants at top
  level (so the public `inline fun advanceAll()` on Kotlin/JS can reference
  them at the expansion site).
- `AnalogDriftCoeffs(analog, sampleRate)` helper class — computes α / β /
  scales / steady-state σ from the constants. Both classes call this in
  their `init` block and copy fields out.
- `analogDriftGaussian(rng)` Box-Muller helper.

Single source of truth. Future tuning (or extension to filter cutoff drift)
touches one file.

### Phase 6 — Filter analysis *(diagnosis only, no code)*

Read of `LowPassHighPassFilters.kt` + voice-strip wiring confirmed:

- SVF topology is already ZDF / TPT (Vadim Zavalishin) — correct topology,
  not a naive bilinear cascade. ✓
- Each voice has its own filter instance with isolated state (per
  `VoiceFactory.toFilter()` line 74). ✓
- **No saturation anywhere in any feedback path.** ✗
- **All voices use identical coefficients.** ✗
- **Cutoff modulation snaps at block boundaries** (block-rate staircase on
  aggressive `lpe` sweeps — the `Ignitor.svf` side has per-sample Bresenham
  lerp, the voice-strip side does not). ✗
- **No `VoiceFactory`-side RNG** plumbed for per-voice variance. Would
  need to add.

This phase produced the ranked filter-humanization plan that became Phase 7.

### Phase 7 — Tanh feedback saturation on `SvfLPF` *(shipped)*

The single biggest analog-vs-digital filter cue. Added an `analog`-gated
saturation path to `SvfLPF`:

```kotlin
val v1 = a1 * ic1eq + a2 * v3
val v1Sat = ClippingFuncs.fastTanh(driveK * v1) * invDriveK
ic1eq = (2.0 * v1Sat - ic1eq).flushDenormal()
```

The tanh sits in the integrator-1 feedback (resonance feedback path). LPF
output `v2` is linear *of itself* but depends on `ic1eq` which has been
saturation-shaped → resonance peaks compress, harmonics generate, velocity
feels real through the filter.

- Uses `ClippingFuncs.fastTanh` (Padé approximation) — same primitive as
  `tube()`, `softCap()`, `diodeClip()`, `zeroSquare()`. ~5× faster than
  `kotlin.math.tanh`, no native call on JS, NaN-passthrough (matches engine
  convention). One consistent "tanh feel" across the engine.
- **Bit-identical at `analog=0`** — separate code path, gated at
  construction time via `private val saturate: Boolean = analog > 0.0`.
  Per-block branch, monomorphic inner loops.
- Drive scales linearly: `driveK = 1.0 + analog * 0.5`. So `analog=1` →
  driveK=1.5 (mild), `analog=3` → 2.5 (noticeable), `analog=10` → 6.0
  (heavy).
- `analog` plumbed through `VoiceFactory.toFilter(analog: Double)`; read
  once per voice from `data.oscParams?.get("analog") ?: 0.0`.
- **Scope**: voice-strip `SvfLPF` only. `OnePoleLPF` has no feedback path,
  so untouched. `SvfHPF` / `SvfBPF` / `SvfNotch` untouched (LPF was 90% of
  the plastic; the others not usually pushed to resonance).
  `Osc.lowpass()` (ignitor-side `Ignitor.svf`) also untouched.

---

## Open backlog (ranked)

### High priority — natural continuation of the filter work

**Filter Step 2 — Per-voice constant cutoff offset.**
Each voice gets a fixed cutoff multiplier set once at note-on (±0.5% ×
analog ≈ ±15 cents of "filter pitch"). Two voices through the same
configured filter no longer process identically. Requires plumbing a
`Random` source into `VoiceFactory.makeVoice()` + storing the per-voice
offset on `AudioFilter.Tunable` (apply multiplicatively in `setCutoff`).

**Filter Step 3 — Per-voice slow cutoff drift (OU).**
Same two-layer OU model as the new `AnalogDrift`, applied to filter cutoff
instead of oscillator pitch. Models thermal drift of filter components.
Magnitude ±2–5 cents over ~10 s. Builds on Step 2's RNG plumbing — each
`AudioFilter.Tunable` holds its own `AnalogDrift` instance,
`FilterModRenderer` combines drift multiplier with envelope-driven cutoff.

**Extend tanh saturation to `SvfHPF` / `SvfBPF` / `SvfNotch`.**
Architectural consistency. Same `analog`-gated tanh pattern as Phase 7 — only
the per-`process()` override changes (state-update math is shared). No
audible impact in Schmetterling (every HPF there is `OnePoleHPF`, no Q) but
matters when a future patch uses a self-resonant HPF/BPF (MS-20 scream, ping
bass, formant sweep). Pure-sine resonant whistle in those modes is harsh; tanh
tames it into something musical.

### Medium priority — completes the engine warmth pass

**Super-oscillator ripple of per-voice OU drift.**
Right now only `SuperSaw` uses `PolyAnalogDrift`. The other five
(`SuperSine`, `SuperSquare`, `SuperTri`, `SuperRamp`, `SuperKarplusStrong`)
still use mono `AnalogDrift` internally, so their unison voices wobble in
lockstep. Same swap as Phase 2: replace the single `drift: AnalogDrift?`
field with `PolyAnalogDrift`, restructure to expose per-voice multipliers.

**Filter Step 4 (optional) — Per-sample coefficient lerp in
`FilterModRenderer`.**
Today the cutoff snaps at block boundaries — discrete steps every ~5.3 ms
at 256 frames/48 kHz block, audible as a staircase during fast envelope
sweeps. The `Ignitor.svf` side already has Bresenham-style per-sample
coefficient interpolation; port the same pattern to the voice-strip side.
Only do if Steps 2+3 don't fully resolve.

### `warmth` redesign — deferred until after the rest of the filter work

Investigation revealed:

- **Sprudel `.warmth(0..1)`** stores in `oscParams["warmth"]`, applied
  osc-internal as a one-pole IIR LPF with the literal alpha coefficient
  (file: `lang_osc_addons.kt:242`, impl: `IgnitorFilters.kt:532`
  `withWarmth`).
- **KlangScript `Osc.warmth(cutoffHz)`** wraps the ignitor in
  `IgnitorDsl.OnePoleLowpass(cutoffHz)` — completely different semantics,
  param is frequency in Hz (file: `KlangScriptOscExtensions.kt:51`).
- Built-in songs reflect the split: Sprudel uses values 0.1–0.3,
  KlangScript uses 3500–12000.

Two distinct operations under one name. And neither is what users
intuitively mean by "warmth" — real warmth in audio terms is
**saturation** (low-order harmonic generation), not muffling.

**Recommended redesign** (post-other-filters): redefine warmth as the
analog "warm preamp" combo — `lpf_gentle(tanh(drive(amount) * input))`.
Single knob 0..1 unified across Sprudel and KlangScript. Saturation first
(adds 2nd/3rd harmonics via `ClippingFuncs.tube` for asymmetric warmth),
gentle one-pole LPF after (tames the harsh top end). Migration: existing
Sprudel `warmth(0.3)` values still mean "gentle warmth" — amount knob
carries over even though math changes. KlangScript `warmth(Hz)` patches
migrate to `onePoleLowpass(Hz)`.

Deferred because: Filter Steps 2+3 might want per-voice drive jitter on
warmth too, so the RNG plumbing belongs first.

### Low priority — nice-to-haves

**Tape saturation as a dedicated effect / distortion shape.**

Today the engine has `chebyshev` (Chebyshev T3 polynomial — pure 3rd
harmonic generator) documented as "tape saturation feel" in
`ClippingFunctions.kt:127`, but it's a single primitive, not a real tape
effect. A proper tape saturator is a multi-stage chain:

1. Soft saturation (e.g. `expClip` or `tanh`) — gentle compression knee.
2. Slight asymmetric bias before saturation — small DC offset generates
   even harmonics; differentiates "tape" from pure-tanh "tube".
3. One-pole LPF at ~10–15 kHz — models tape-head HF loss + gap loss.
4. DC blocker after — removes the asymmetric-bias DC.

Optional bells:

- Per-band saturation curves (typical tape has different knees in LF / MF /
  HF — emulated as parallel filtered saturation paths).
- Subtle wow/flutter modulation (pitch wobble) — could borrow from
  `AnalogDrift` infrastructure.
- Tape-hiss noise floor — usually too low to matter musically.

Implementation options:

- **New `DistortionShape.TAPE`** — single-shape addition to the existing
  `distort()` chain. Cheapest, slots into the existing `applyDistortionShape`
  per-sample dispatch.
- **New stand-alone `.tape(amount)` effect** — multi-stage chain (sat + LPF +
  DC blocker bundled). Slightly more work, cleaner user-facing intent.

Either way: this is a "complete the warmth/saturation toolkit" item, not a
plastic-pipe fix. Low priority but easy to bolt on once the higher items
are landed.

**Filter saturation on `Osc.lowpass()` (ignitor-side `Ignitor.svf`).**
Mirror Phase 7's change in the ignitor chain. Cheap add — same math, just
different file.

**Other plastic-pipe causes documented but out of scope.**

- "Don't compensate the bass loss" — already correct in our filters, we
  don't make-up-gain. ✓
- Per-pole stagger in multi-pole stacks — N/A for single-section SVF.
  Would only matter if we add a 4-pole-cascade filter type.
- Asymmetric saturation on the LPF — small DC offset before tanh generates
  even harmonics. Free if we have `tanh` already, but defer.
- Phase randomization on note-on — already done; oscillator phases start
  randomized via per-voice initial state.

---

## Tuning constants reference

All in `audio_be/.../ignitor/AnalogDriftCoeffs.kt` (oscillator drift) and
`audio_be/.../filters/LowPassHighPassFilters.kt` (filter saturation).

| Constant                      | Current | Effect                                         |
|-------------------------------|---------|------------------------------------------------|
| `ANALOG_FAST_TAU_SEC`         | 0.05    | Fast-jitter time constant (s)                  |
| `ANALOG_SLOW_TAU_SEC`         | 10.0    | Slow-drift time constant (s)                   |
| `ANALOG_MEAN_REVERSION_RATIO` | 0.5     | β = α × this; 0 = random walk, 1 = strong pull |
| `ANALOG_FAST_PEAK_CENTS`      | 0.2     | Fast-layer ±cents per unit `analog`            |
| `ANALOG_SLOW_PEAK_CENTS`      | 0.8     | Slow-layer ±cents per unit `analog`            |
| `SvfLPF.DRIVE_PER_ANALOG`     | 0.5     | `driveK = 1 + analog × this`                   |

At `analog=N`, total oscillator drift peak ≈ ±N cents.

---

## Listening-test notes (for self / future Claude)

When auditioning changes, check Der Schmetterling at `feel = 3.0`:

1. **Sustained notes should breathe.** Hold a single pad voice and listen
   for the slow wander over 5–15 seconds. Should feel lazy-moving, not flat.
2. **Unison voices should sound wider but not detuned.** SuperSaw with
   per-voice OU should sound alive, not locked.
3. **Filter resonance should feel rounded, not mathematical.** With the
   new tanh feedback, fast `lpe` sweeps should sound less wah-pedal-on-rails
   and more amp-like. Loud parts saturate more than quiet parts → velocity
   feels real through the filter.
4. **Low-frequency plastic should be reduced.** That's the band where the
   filter saturation does most of its work.

If still plastic after a phase ships:

- First A/B against `.analog(0)` to confirm the engine improvements are
  audible at all.
- Then strip the patch effect-by-effect (the "STAGE 0 → STAGE 8" bisection
  list lives in conversation history) to identify whether residue is
  patch-side or engine-side.
