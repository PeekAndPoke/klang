# Analog drift — coefficient tuning & in-tune-attack fix

Last updated: 2026-06-17. Status: **DONE / shipped** (branch `engine-dsl-osc-dsl-parameterization`).

Continuation of the [Plastic-Pipe Hunt](20260603-plastic-pipe-hunt.md) lineage — the
two-layer Ornstein–Uhlenbeck drift model (`AnalogDrift` / `PolyAnalogDrift`) it introduced
in Phases 2–3 was implemented everywhere with **educated-guess coefficients that had never
been tuned for the `analog` values songs actually use**. This task tuned them and fixed a
structural seeding bug.

---

## Problem

The `analog` knob ("analog drift") made things feel subtly "off" — most audibly on
**Der Schmetterling** (`analog(3)`, fast notes, high-Q filter): a per-note resonance
warble and a wandering-intonation, "the fiddler needs more practice" quality.

Root insight: **the drift docstring describes a 0–1 knob ("1.0 = maximum, typical
0.05–0.3"), but every song uses `analog(2)`–`analog(8)`.** Because every destination
scales **linearly**, real-world use ran 2–8× hotter than the model was tuned for.

## The `analog` surface (one voice param → 5 destinations, all linear in `analog`)

`.analog(x)` (`sprudel/.../lang_osc_addons.kt`) writes `oscParams["analog"]` on the
**voice** (never on reverb/compressor/distort, even when chained after them). It fans out:

| # | Destination                     | Where                                                            | Per unit `analog`                           | Moves over time?           |
|---|---------------------------------|------------------------------------------------------------------|---------------------------------------------|----------------------------|
| 1 | Osc pitch drift                 | `AnalogDrift`/`PolyAnalogDrift` (`Ignitors.kt`)                  | ±1.0¢ (±0.2 fast + ±0.8 slow)               | yes (slow ~6.7 s revert)   |
| 2 | Filter OB-X character           | `IgnitorFilters` svf LP/HP, `drivePerAnalog=0.5`                 | resonance taming (`kEff = k + analog·tCfb`) | no (static tone)           |
| 3 | Filter **frozen** cutoff offset | `VoiceFactory.perVoiceCutoffOffsetMul`, `cutoffOffsetPerAnalog`  | ±5.2¢·analog, drawn once at note-on         | no (frozen per note)       |
| 4 | Filter cutoff drift             | `VoiceFactory.toModulator` `AnalogDrift(analog × driftRelToOsc)` | ±(1.0·driftRelToOsc)¢·analog                | yes + random seed per note |
| 5 | Sample playback rate            | `SampleIgnitor.kt` `AnalogDrift(analog, sr)`                     | ±1.0¢·analog                                | yes                        |

## Diagnosis (why Der Schmetterling warbled)

- Fast notes + high-Q filter + `analog=3` → the **frozen offset (#3)** re-pitched the
  resonant peak ±15¢ on *every note* → the resonance "sang" at a different pitch per note.
- **The deeper bug — steady-state seeding.** `AnalogDrift` was seeded from a steady-state
  Gaussian so drift was "immediate" on held notes. But on SHORT notes that converts
  "drift over time" into "random per-note detune": the note ends before the OU reverts to
  centre, so each note sits at its seeded offset for its whole life. This hit **oscillator
  pitch too** (mono melodic lines: each note started ±2.4¢ off at analog=3 and stayed) →
  the "fiddle intonation" feel. #4's drift seed compounded it on the filter.
- Extremes were not rare: #3 is **uniform** (edge as likely as centre); #4's seed is an
  **unbounded Gaussian** (real tail past the nominal peak).

## Non-obvious findings (keep for future tuning)

- **Pitch drift is already constant in cents** (logarithmic) — *not* stronger on high
  notes. The perception of "stronger on high notes" is the **beat rate `Δf ∝ f`** (a fixed
  cents detune beats faster in Hz at higher pitch — physically correct). → Do **not** add
  frequency dependence; the lever is depth, not τ, and not a frequency taper.
- **#3 and #4 are partly redundant**: both inject a per-note random cutoff offset (#4 via
  its note-on seed). On short notes the drift's *seed* matters more than its *motion*.
- Shortening the slow τ would sound **worse** (more vibrato-like), not better. The slow
  layer's audibility is depth, not timescale.

## The fix

1. **Seed the slow layer at CENTRE, not steady-state** — the primary fix. `AnalogDrift.kt`
    + `PolyAnalogDrift.kt` init now set `ySlow = 0.0`. Every note attacks in tune; slow
      drift develops only if held. Fast layer still seeds from its steady-state Gaussian
      (settles in ~50 ms → harmless immediate micro-shimmer). Added an injectable
      `rng: Random = Random` to `AnalogDrift` (mirrors `PolyAnalogDrift`) for deterministic tests.
2. **#3 frozen offset lowered**: `cutoffOffsetPerAnalog 0.003 → 0.001` (±15¢ → ±5¢ at analog=3).
3. **#4 filter drift lowered** (it was the hottest multiplier): `driftRelToOsc 5.0 → 2.5`.
4. **Kept the documented twins in sync**: `FilterHumanizationCoeffs.kt`
   `FILTER_CUTOFF_OFFSET_PER_ANALOG` / `FILTER_DRIFT_RELATIVE_TO_OSC` mirror the
   **authoritative** `EngineDsl.Filter` defaults (the per-stage values `VoiceFactory`
   actually consumes; the audio_bridge/audio_be module split forces the duplication).

### Before → after at `analog=3` (Der Schmetterling)

| Layer                     | Before                       | After                       |
|---------------------------|------------------------------|-----------------------------|
| Filter frozen offset (#3) | ±15¢ per note                | **±5¢**                     |
| Filter cutoff drift (#4)  | ±15¢ + random ±12¢ seed/note | **±7.5¢, seeded at centre** |
| Osc pitch (#1)            | ±3¢, **stuck off per note**  | ±3¢, **attacks in tune**    |

User confirmed by ear: "things sound much more coherent now."

## Deferred (intentional)

- **Osc slow-layer depth** `ANALOG_SLOW_PEAK_CENTS = 0.8` (`AnalogDriftCoeffs.kt`) — left
  as-is. The seeding fix resolved the per-note feel; this lever only affects *sustained /
  drone* beating. Revisit only if held/unison patches beat too much.
- **High-note frequency taper** — rejected (drift is already cents-constant; see findings).

## Files changed

- `audio_be/.../ignitor/AnalogDrift.kt` — slow-layer-at-centre seed + injectable `rng` + docs
- `audio_be/.../ignitor/PolyAnalogDrift.kt` — same seeding fix per voice + docs
- `audio_bridge/.../EngineDsl.kt` — `Filter` defaults `cutoffOffsetPerAnalog 0.001`, `driftRelToOsc 2.5`
- `audio_be/.../filters/FilterHumanizationCoeffs.kt` — synced twins + updated cents in docs
- `audio_be/.../commonTest/.../ignitor/AnalogDriftSpec.kt` — **new guard**

## Guard test — `AnalogDriftSpec`

- **In-tune attack** (mono + poly): first `nextMultiplier()` across many seeds is centred
  with only the fast layer present (`< 3.5¢`) — catches a regression of the seeding fix.
- **No runaway**: 2M samples stay centred (`mean ≈ 0 ± 2¢`) and bounded (`max < 25¢`) —
  proves the process can't drift away from centre (both layers are stable AR(1)).
- **Cents budget**: asserts post-tuning ceilings at `analog=3` and prints the
  osc/offset/drift cents table for `analog ∈ {1,2,3,5,8}` so the mapping can't be silently
  cranked back up.

Verification: `:audio_be:jvmTest` green; nothing pins the old EngineDsl defaults.
Constants remain **tune-by-ear** — the spec guards ceilings, not exact feel.
