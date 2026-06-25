# IR → modal table extraction (impulse response → `body()` material)

Status: **future / idea — not scheduled.** Created 2026-06-24. Too detailed for the current project status
(plenty of open work-streams already). Captured here so the idea isn't lost; pick it up when the body resonator
becomes a priority surface again. Self-contained so it can be resumed cold.

## One-line goal

Build an **offline** tool that takes a recorded impulse response (`.wav`) of a real resonant body and reduces it
to an 8–10 entry `m(freq, db, q)` table — the same format `resolveBodyModes()` already uses — so we can derive
new `body()` materials from real instruments instead of hand-tuning every mode by ear.

## Background — what we have today

The `body()` / `bodyMix()` resonator is a **parametric modal model**: a parallel bank of resonant SVF bandpasses
(`BodyFilter`), one per mode, mixed over the dry source via `ParallelMixFilter` (floor + peaks). Materials are
hand-authored tables in `resolveBodyModes()`:

- `sprudel/src/commonMain/kotlin/SprudelVoiceData.kt:989` — `resolveBodyModes(material)`, four tables
  (`wood`, `tube`, `glass`, `membrane`), eight `m(freq, db, q)` modes each.
- `audio_be/src/commonMain/kotlin/filters/BodyFilter.kt` — the parallel SVF-BPF bank; divides each band by `Q`
  so `db` is the *actual* peak emphasis (independent of sharpness).
- `audio_bridge/src/commonMain/kotlin/FilterDef.kt` — `FilterDef.Body` / `Body.Mode` wire contract.
- See also memory note `project_body_resonator`.

These tables were authored by ear (the comment at `resolveBodyModes()` explicitly calls them "starting-point
tables — expect to tune `db`, the mode sets, and `BODY_FLOOR` by ear"). This task is about **deriving** them
from real recordings instead.

## Why it works — the IR *is* a sum of decaying sinusoids

For a **linear** resonant body, the impulse response decomposes exactly into damped modes:

```
h(t) = Σ_k  A_k · e^(−t/τ_k) · cos(2π·f_k·t + φ_k)
```

Each mode `k` maps directly onto one `m(freq, db, q)` row:

| Table column | Extracted quantity                     | Relationship                                                                                       |
|--------------|----------------------------------------|----------------------------------------------------------------------------------------------------|
| `freq`       | modal frequency `f_k`                  | direct                                                                                             |
| `q`          | decay time constant `τ_k`              | **Q = π · f_k · τ_k** (slower ring → higher Q)                                                     |
| `db`         | peak height above local baseline `A_k` | direct; `BodyFilter`'s `/Q` normalization makes `db` the true peak emphasis, so the mapping is 1:1 |

The only quantity we **cannot** carry over is `φ_k` (phase / inter-mode dispersion) — a parallel BPF bank
synthesizes its own phase. Perceptually irrelevant in the ring; only affects the exact onset transient.

**Sanity check** against our own hand-tuned `wood` table: the 100 Hz mode at Q=12 → τ = 12/(π·100) ≈ 38 ms,
T60 ≈ 0.26 s — a believable wooden-box ring. So our existing tables already sit in physically plausible territory;
the extractor should land near them for a comparable recording.

## Extraction methods (pragmatic → rigorous)

1. **FFT peak-pick + per-band decay fit — RECOMMENDED for a caricature.**
    - Magnitude spectrum of the (trimmed, normalized) IR → pick the N tallest peaks. Space them log/perceptually
      so we don't spend all ~10 modes inside one octave. Gives `freq` + relative `db`.
    - For each picked peak: narrowly bandpass the IR around `f_k`, take the Hilbert-envelope, line-fit the
      **log-envelope slope** → `τ_k` → `Q_k = π·f_k·τ_k`. (This is the room-acoustics RT60-per-band trick.)
    - Robust, controllable, easy to reason about. Best fit for "extract the caricature."
2. **ESPRIT / Matrix Pencil / Prony — the "correct" parametric fit.**
    - Fits the sum-of-complex-exponentials directly in the time domain; outputs `(f_k, τ_k, A_k, φ_k)` for all
      modes at once given a model order. Cleaner in principle but noise-sensitive and order-selection is finicky.
      Overkill for a caricature; revisit only if method 1 proves too noisy.
3. **Vector fitting — frequency-domain rational fit.**
    - Fit a low-order IIR (sum of biquad resonators) to the measured frequency response; poles & residues → modes.
      Worth it only if we ever want to also approximate the **notches** (see catches below).

## Proposed pipeline (method 1)

1. Load IR `.wav`, sum to mono, normalize.
2. Trim: drop pre-delay / silence; optionally gate out the direct-sound transient so we model the *ring*.
3. Peak-pick the magnitude spectrum → candidate `f_k` (+ relative peak heights for `db`).
4. Per-band Hilbert-envelope decay fit → `τ_k` → `Q_k`.
5. Reduce to 8–10 modes: greedily keep the most energetic / perceptually-spaced peaks.
6. Normalize `db` to our table convention (tilt: low modes slightly boosted, rolling off in the highs — match
   the shape of the existing `wood`/`tube` tables rather than a hard rule).
7. Emit ready-to-paste Kotlin:
   ```
   "<material>" -> listOf(
       m(100.0, 3.0, 12.0),
       ...
   )
   ```
   straight into `resolveBodyModes()`.

## Honest limits — why it's a *caricature*, not a clone

- **Modal density.** Real bodies have hundreds of modes, dense in the highs. 8–10 captures the dominant low/mid
  ones; the high end is a smear the sparse fit drops. That loss *is* the caricature — and arguably better for us
  (cleaner, tunable, no mud).
- **Anti-resonances / notches.** A parallel BPF bank only *adds* peaks, so the IR's zeros between modes are lost.
  The floor-mix model papers over this but doesn't reproduce it. (Method 3 could, if ever needed.)
- **Phase / dispersion** lost (see above) — fine for the ring, changes only the onset.
- **One recording ≠ a platonic material.** The result reflects that specific mic position / strike point /
  instrument. Expect to still nudge `db` by ear afterward — exactly what the existing table comment warns.

## Tooling decision (deferred)

- **Throwaway Python + scipy** (recommended for a first pass): FFT, Hilbert, peak-find, curve-fit all built in.
  Drop in a `.wav`, print the `m(...)` lines. Fast to write, not committed, no engine impact.
- **In-repo Kotlin/JVM tool**: more code for the same output, but reproducible (commit the extractor, re-run on
  new IRs). Justified only if deriving materials becomes a recurring activity. The extractor is purely offline —
  it never touches the audio thread either way.

## Acceptance criteria (when picked up)

- Feeding a known IR produces a table whose mode frequencies and decay times match a manual analysis.
- Re-deriving a "wood" IR lands a table audibly in the same family as the current hand-tuned `wood`.
- Output pastes into `resolveBodyModes()` with no hand-editing of the syntax (values may still be tuned by ear).

## Related

- Memory: `project_body_resonator` (the `body()`/`bodyMix()`/`vowel()` POC), `feedback_raw_motor`.
- Code: `SprudelVoiceData.resolveBodyModes()`, `BodyFilter`, `ParallelMixFilter`, `FilterDef.Body`.
