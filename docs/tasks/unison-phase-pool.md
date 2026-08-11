# Unison phase pool ("wave-pool") — fixing the fundamental lottery in the super-oscillators

> **Status: 🔴 proposed 2026-08-11 (design co-developed with the user, same day), not started. Not
> slotted in [`_priorities.md`](_priorities.md).** Priority proposal: **SHOULD** — it is a *sound*
> fix ("sound first") for a defect measured across weeks of sessions, and it defaults to full
> bypass, so shipping it risks nothing.
>
> **Grounded in:** the klang-ai onset/consistency sessions
> (`sessions/20260808-guitar2-onset/`, `sessions/20260809-superimpose-onset/`,
> `sessions/20260810-gemini-review/`). All numbers below are measured there or derived from a model
> that reproduced those measurements.
>
> **Scope:** the shared unison engine in `ignitor/Ignitors.kt` (`superSawRaw` family). Covers
> supersaw / superramp / supersquare / superpulse / supertri / supersine. Explicitly NOT superpluck
> (excitation-based; different onset physics).

## 1. The defect, measured

Unison voices start at random phases ("random start phase — lush"). The *broadband* result is fine — onset RMS sd ≈ 1
dB, because many harmonics average out. The **fundamental** is the casualty: it is the coherent sum of N random phasors,
and at low pitch the drawn configuration is frozen for the whole note (spread is in semitones → phasor rotation rates ∝
f₀; at E2 with `spread 0.05` the *innermost* pairs beat over ~30–60 s and even the edge voices need ~8 s — nothing
resolves within a note).

Measured on 120 repeats of the same E2 (guitar 2 patch):

- steady-state fundamental level: **sd ≈ 4.9 dB, full range 27.6 dB** across identical notes
- **11–21 % of notes stuck > 6 dB below median** for their entire duration ("holes")
- early→steady correlation **r = 0.82** — a note born hollow stays hollow
- adding the `.superimpose(pan)` copy doubles the per-channel hole rate (independent second draw)

Perceptually (author + blind externals agree): "some notes have bass, some don't", random dynamics ("the house is
shaking"), weak/hollow/phasey onsets. The *broadband* onset hash is acceptable — it reads as a plectrum strike — **if
and only if it is consistent.** The fundamental lottery is what breaks the illusion.

## 2. The analytic foundation

Voice *n*: gain gₙ, start phase φₙ, detune rate Δₙ = fₙ − f₀. For any harmonic waveform, the ensemble's fundamental
amplitude is a closed-form phasor sum, for all t, not just onset:

```
A₁(t) ∝ |Σₙ gₙ · e^{i2π(φₙ + Δₙ·t)}|          (waveform only scales the constant)
K     = |Σₙ gₙ · e^{i2πφₙ}| / Σₙ gₙ ∈ [0,1]   (0 = cancelled, 1 = coherent)
```

Statistics of a random draw (complex-Gaussian / Rayleigh approximation):

```
P(K < k) = 1 − exp(−k²·N_eff),   N_eff = (Σg)² / Σg²
```

Guitar 2's ensemble (unison 11, sideAtten 0.5): N_eff ≈ 10.5 → predicted 16–21 % holes (K < 0.15), **matching the
measured 11–21 %**. The model is validated; predictions below are trustworthy.

**Frequency invariance (the property that makes a shared pool work):** K contains no frequency — a configuration scored
once is valid at every pitch. The *coherence time* scales as 1/f₀, so the pool's influence is strongest exactly where
the defect lives (low, fast notes) and fades to irrelevance at high pitch where beating self-heals within the note. No
pitch switch needed.

## 3. The design

### 3.1 Banded acceptance — "good", never "best"

Maximizing K is a trap (Goodhart): K → 1 is phase-aligned saws — thin, buzzy, static. The lushness IS the incoherence.
All selection below therefore targets a **quality band** `K ∈ [kMin, kMax]`
(supersaw default ≈ [0.30, 0.55]), never the maximum. The band is also secretly a timbre control —
[0.05, 0.25] is a deliberately hollow pad that breathes into existence; see §5.

### 3.2 Best-of-M draws (the stateless core)

At note-on, draw M (default 5) candidate phase sets, score each analytically (~11 sin/cos per candidate), keep the one
closest to the band. Predicted effect at N_eff 10.5, M = 5:

|                  | single draw | best of 5          |
|------------------|-------------|--------------------|
| median K         | 0.26        | 0.44 (+4.7 dB)     |
| p10 floor        | 0.10        | **0.31 (+9.7 dB)** |
| p10–p90 spread   | 13.4 dB     | 5.8 dB             |
| holes (K < 0.15) | 1 in 5      | **1 in ~2400**     |

The catastrophic tail disappears; residual ±3 dB variation is musician-level, not lottery-level. Notes remain genuinely
random — this is selection, not alignment; the steady texture is untouched.

### 3.3 The pool — born warm

A bounded store of accepted configurations, keyed **per (orbit, unisonCount)**:

- **Size ~1000** (config = N phases ≈ 44 bytes → ~44 KB per pool; a song touches ~5–10 pools).
- **Born warm:** because scoring is analytic, the pool is filled at instrument load — ~3000 draws, well under a
  millisecond. "Warming over time" collapses into initialization; there is no cold period.
- **Per-orbit on purpose (user decision):** guitar 1 and guitar 2 develop different characters over time. Divergence is
  bounded by construction — every pool lives in the same band, so orbits differ in *which* configurations they favor,
  never in quality. "Slight" is guaranteed by the guardrail.
- **Superimpose copies share their orbit's pool** and pull different entries per note — the same instrument,
  double-tracked. Feature, not bug.
- The octave sub-stacks inside composite Oscs (e.g. `supersawHp`'s ×2/×4 layers) get their own pools via the unisonCount
  key automatically.

### 3.4 Evolution — refresh with RANDOM eviction (user decision)

Every ~10th note-on performs a fresh banded best-of-M draw and inserts it, **evicting a random entry — not the worst.**
Evict-worst would homogenize the pool toward the band center over time; random eviction keeps it a fair rolling sample
of the accept distribution forever. Turnover: pool 1000 × refresh 1/10 ≈ 10,000 notes ≈ ~20 min at 8 notes/s — the
instrument slowly becomes a different individual while sounding coherent at any moment. Refresh rate = evolution-speed
knob; 0 = frozen (and reproducible).

### 3.5 Selection policy (per sound)

- `random` — every note a different vocabulary entry (default)
- `roundRobin` — cycle
- `sticky` — hold one entry per phrase/cycle, switch at the boundary: machine-gun repeats strike identically within a
  phrase ("persistent strings"-lite; likely the guitar-2 feel)
- **Contextual selection (optional refinement):** evaluate a few candidates' A₁ (t) at the actual note's f₀ and gate
  length (the trajectory is analytic) and prefer the one that *holds*. ⚠️ Pitch-dependent scoring happens at **selection
  time only** — stored scores are K (0), which is frequency-invariant; baking pitch into storage would break pool
  sharing across the scale.

## 4. Applicability across the family

One implementation in the shared unison init covers everyone; per-waveform *defaults* differ:

| Oscillator               | How much it matters                                      | Default band note       |
|--------------------------|----------------------------------------------------------|-------------------------|
| supersine                | **Critical** — K is the entire note (no other harmonics) | high band, ≈ [0.5, 0.8] |
| supertri                 | Near-critical (1/k² harmonics)                           | high-ish                |
| supersaw / superramp     | The measured case                                        | ≈ [0.30, 0.55]          |
| supersquare / superpulse | Same statistics, odd harmonics                           | ≈ [0.30, 0.55]          |
| superpluck               | **Excluded** — excitation-based onset                    | —                       |

## 5. Configuration

All per-sound (engine-default layer in `OscillatorTuning.kt`, overridable via the usual knob path):

| Knob            | Default                             | Meaning                                               |
|-----------------|-------------------------------------|-------------------------------------------------------|
| `phasePool`     | **off**                             | **Full bypass. Off = today's engine, bit-identical.** |
| `drawTries` (M) | 5                                   | Candidates per draw; 1 + pool off = legacy random     |
| `kMin` / `kMax` | per waveform                        | Accepted quality band; doubles as a timbre control    |
| `poolSize`      | 1000                                | Vocabulary size per (orbit, unison)                   |
| `refreshEvery`  | 10                                  | Notes between fresh draws; 0 = frozen pool            |
| `selection`     | `random`                            | `random` / `roundRobin` / `sticky`                    |
| RNG source      | clock (live) / fixed seed (offline) | takes vary live; offline renders reproducible         |

## 6. Constraints & known side effects

- **Audio thread:** pools preallocated at load; insert/evict via fixed-size heap ops; zero allocation at note-on.
  Scoring cost per note-on: ≤ a few hundred sin/cos — negligible even on JS.
- **Mix impact:** conditioning raises the *average* fundamental ~4–5 dB on affected low voices — a fixed, predictable
  shift (unlike the lottery). Songs will want a one-time low-end retrim after enabling. Document prominently.
- **Reproducibility:** offline renderer fills and refreshes pools from a fixed seed → identical renders. Live uses the
  clock, consistent with the house "takes vary" philosophy.

## 7. Validation (harness exists)

The 120-repeat single-note harness (klang-ai `superdist.py` / `dist.py`) verifies per variant:

1. Hole rate (steady fundamental > 6 dB under median): from 11–21 % → **< 1 %** expected.
2. Steady fundamental sd: ~4.9 dB → ~2 dB expected.
3. Steady-state texture unchanged: long-term spectrum and band levels within ±0.5 dB of bypass (selection must not
   brighten/thin the sustain).
4. `phasePool off` renders **bit-identical** to pre-change engine (the bypass guarantee).

## 8. Suggested phasing

- **P0 — stateless banded best-of-5** behind the bypass flag + harness verification of §7. Smallest shippable win; no
  state, no persistence questions.
- **P1 — the pool:** born-warm fill, (orbit, unison) registry, random-eviction refresh.
- **P2 — selection policies** (`sticky` esp.) + contextual selection.
- **P3 — per-waveform tuning defaults** (supersine band!) + docs (`ignitor-reference.md` recipes).

## 9. Open decisions

1. Exact per-waveform default bands (supersine's especially — needs ears).
2. `sticky` boundary definition (per cycle? per phrase-detection? per N notes?).
3. Whether gainJitter draws join the pooled configuration or stay per-note (leaning: per-note — they are level texture,
   not phase structure; keeps entries smaller and reuse less audible).
4. Pool persistence across sessions (localStorage/file) — nice, not needed; born-warm makes cold starts painless anyway.
5. Whether the contextual-selection refinement is worth its complexity in P2 or parks until a measured need appears.

## 10. Explicitly out of scope

- superpluck and any excitation/physical-model oscillator.
- Changing phase *generation* (tables, Newman phases, bloom/detune envelopes, transient anchors) — those are separate
  ideas on the shelf; this task only selects among honest random draws.
- Scoring by non-analytic signals (rendered/heard outcomes) — the "actually learning" v3; revisit after P1 ships.
