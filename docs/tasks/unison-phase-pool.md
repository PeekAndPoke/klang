# Unison phase pool ("wave-pool") — fixing the fundamental lottery in the super-oscillators

> **Status: 🟡 IN PROGRESS — open decisions settled with the user 2026-08-11 (see §9); work runs in
> a self-restarting loop, one phase per iteration, committing directly to `master-dsl` with a
> review-loop round per phase.** Priority proposal: **SHOULD** — it is a *sound*
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

### 3.3 The pool — born warm *(as-built: amortized first-use warm, see §3.6)*

A bounded store of accepted configurations, keyed **per (orbit, unisonCount, base gain profile,
band, maintenance knobs)** — `sideAtten` and the band enter the key because stored scores are only
valid for the (profile, band) they were accepted under; the maintenance knobs enter it because a
knob racing on note-arrival order would be the parameter-parity bug class (§3.6):

- **Size 256 default, 1024 cap** (user decision 2026-08-12 — big vocabularies bought little:
  size governs repetition rarity only, and `refreshEvery` keeps any size evolving). One config =
  N phases as `DoubleArray` ≈ 88 bytes payload at unison 11 → ~22 KB per full default pool
  (~48 KB on JS with array overhead); a song touches ~5–10 pools; the registry caps at 64 pools
  per playback with least-recently-served eviction — the key being played NOW is always pooled,
  and a by-ear knob sweep recycles slots instead of leaking.
- **Born warm — the original plan** was an eager fill at instrument load. As built (§3.6):
  measured 3.8–292 ms of render-callback stall, so the fill is AMORTIZED — a work-budgeted prefix
  at first use, a few µs of top-ups per served note, vocabulary closed after ~256 notes. The K
  distribution is complete from entry one; only repetition-rarity grows.
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

- `roundRobin` — cycle through the pool array in order, 1 → 2 → 3 → … → wrap (**default; user
  decision 2026-08-11**): every note a different entry, no immediate repeats, evenly spread
  vocabulary use, zero extra state beyond an index.
- `random` — every note an independent draw from the pool.
- `sticky` — **PARKED** (settled §9.2): holding one entry per phrase needs a phrase boundary, and
  the backend must stay cycle-free (see the constraint in §6). If ever revived, the boundary must
  derive from the note stream itself (leading candidate: silence-gap detection — source-agnostic).
- **Contextual selection — PARKED** (settled §9.5): evaluate a few candidates' A₁ (t) at the actual note's f₀ and gate
  length (the trajectory is analytic) and prefer the one that *holds*. ⚠️ If revived: pitch-dependent scoring happens at
  **selection time only** — stored scores are K (0), which is frequency-invariant; baking pitch into storage would
  break pool sharing across the scale.

### 3.6 P1 implementation design (recon 2026-08-12)

Integration points, verified in code:

- **Orbit = `VoiceData.cylinder`** (`?: 0`), already available in `IgnitorRegistry.createExciter` —
  no VoiceFactory threading needed.
- **The pool handle rides `IgnitorBuildCache`** (the per-call carrier that already exists for
  `soundIndex` — same precedent, no signature ripple through the recursive build).
- **`PhasePools` lives on the per-playback context** next to `ignitorRegistry` (pools are
  per-playback by design — per-orbit characters, superimpose copies share their orbit's pool).
- **Key:** data class over (orbit, voices, sideAtten, kMin, kMax, drawTries, poolSize,
  refreshEvery) — the band because stored scores are only valid for the profile+band they were
  accepted under, the maintenance knobs because a knob whose effect depends on note-arrival
  order (two sounds racing for one key) is the parameter-parity bug class. `selection` stays
  out: it is a per-call serving policy, not pool state. (No `Double.toRawBits` — Long is banned
  on the JS audio path.)
- **Born-warm collapses to AMORTIZED first-use warm:** `voices` is a pattern-level param unknown
  at registration time, AND an eager full fill measurably blows the render budget (measured on
  V8: 3.8–20 ms cold at shipped defaults vs the 2.67 ms block — up to 292 ms at the caps). So a
  pool seeds a WORK-budgeted prefix at construction (up to 32 entries, fewer for deep-tries ×
  many-voices configs) and tops up a few work-capped entries per served note (µs each) until full
  — the default 256-entry vocabulary closes after ~224 notes. The K distribution is complete from
  entry one — vocabulary size governs repetition audibility, not quality. Fill scores against the BASE gain profile
  (`superSawVoiceGains(v, sideAtten)`, no jitter exists at fill); the per-note jittered gains
  perturb the effective K second-order (documented deviation from the stateless path's
  exact-gain scoring).
- **Note-on:** near-zero allocation — `pool.next()` returns a stored `DoubleArray` reference,
  the engine copies values into the voice states; refresh draws use a preallocated scratch
  entry. (One small Key object per note-on lookup remains — noise next to the per-note ignitor
  graph. The pool store is a flat array with random eviction, not a heap — §6's original
  "fixed-size heap ops" plan was over-engineered for random-not-worst eviction.)
- **Selection knob:** `0 = roundRobin` (default, settled §9.2), `1 = random`.
- **One user knob stays `phasePool`:** `1` = pooled when a `PhasePools` registry is reachable,
  stateless banded best-of-M as the fallback (direct factory calls, tests). No second flag.
- **Seed:** `AudioBackendContext.create(..., phasePoolSeed: Int? = null)` — offline renderer
  passes a fixed Int seed → reproducible pools; live leaves it null (clock).

## 4. Applicability across the family

One implementation in the shared unison init covers everyone; per-waveform *defaults* differ:

| Oscillator               | How much it matters                                      | Default band note       |
|--------------------------|----------------------------------------------------------|-------------------------|
| supersine                | **Critical** — K is the entire note (no other harmonics) | high band, ≈ [0.5, 0.8] |
| supertri                 | Near-critical (1/k² harmonics)                           | ≈ [0.40, 0.65]          |
| supersaw / superramp     | The measured case                                        | ≈ [0.30, 0.55]          |
| supersquare / superpulse | Same statistics, odd harmonics                           | ≈ [0.30, 0.55]          |
| superpluck               | **Excluded** — excitation-based onset                    | —                       |

## 5. Configuration

All per-sound (engine-default layer in `OscillatorTuning.kt`, overridable via the usual knob path):

| Knob            | Default                             | Meaning                                               |
|-----------------|-------------------------------------|-------------------------------------------------------|
| `phasePool`     | **off**                             | **Full bypass. Off = today's engine (identical rng stream).** |
| `drawTries` (M) | 5 / 16 supertri / 40 supersine      | Candidates per draw (engine caps at 64); higher bands are rarer per draw, so their search is deeper — a missed band degrades to closest-candidate = K-maximization |
| `kMin` / `kMax` | per waveform                        | Accepted quality band; doubles as a timbre control    |
| `poolSize`      | 256                                 | Vocabulary size per pool key (§3.3; engine caps 1024) |
| `refreshEvery`  | 10                                  | Notes between fresh draws; 0 = frozen pool            |
| `selection`     | `roundRobin`                        | `roundRobin` / `random` (`sticky` parked)             |
| RNG source      | clock (live) / fixed seed (offline) | takes vary live; offline renders reproducible         |

## 6. Constraints & known side effects

- **The backend stays cycle-free (user ruling 2026-08-11):** the audio backend must never learn
  about cycles. Sprudel is a *special case* — note events can come from other sources (MIDI,
  sequencer) that don't operate on cycles. Only seconds cross the FE→BE wire (`ScheduledVoice`),
  and that stays so. Any future phrase-aware logic derives from the note stream itself.
- **Bypass = RNG-stream discipline:** with `phasePool off` the engine must consume the RNG stream
  *exactly* as today — no speculative candidate draws on the off path, and no trailing draws
  either (all super-oscs share `Random.Default` live, so one extra draw reorders every later
  note). Guarded by `PhasePoolBypassGoldenSpec`: sample goldens at 1e-9 relative tolerance
  (libm trig is 1-ulp-specified, not bit-reproducible across platforms — the *engine* is
  rng-stream-identical, the *guard* is tolerance-based) plus an exact stream-position case.
- **gainJitter ordering (settled §9.3):** each note draws its jittered gains FIRST; the M phase
  candidates are then scored against those actual gains. Exact K, jitter stays per-note, pool
  entries stay phases-only.
- **Mid-note voice-count changes:** the pool governs note-on only. Voices added mid-note (lazy
  `voices` param change) get plain random phases, exactly as today.
- **Audio thread:** amortized first-use warm (see §3.6 — an eager fill was measured to drop
  blocks and was rejected); entries allocate LAZILY, a few per served note during the growth
  phase (µs-scale, work-budgeted), then the full pool is alloc-free — refresh redraws in place
  (flat array + random eviction, no heap needed). Stateless-path scoring cost per note-on:
  ≤ ~900 sin/cos at the deepest default (supersine, 40 tries × 11 voices); pooled note-on cost:
  one entry copy + a few work-capped top-up draws while growing.
- **`refreshEvery` counts SERVED ENTRIES, not musical notes:** a 3-note chord with a
  `.superimpose()` copy churns its shared pool 6× per musical event — §3.4's "~20 min" horizon
  is proportionally shorter on dense polyphony. Deliberate (the pool serves voices, not events);
  documented so the knob reads honestly.
- **Mix impact:** conditioning raises the *average* fundamental ~4–5 dB on affected low voices — a fixed, predictable
  shift (unlike the lottery). Songs will want a one-time low-end retrim after enabling. Document prominently.
- **Reproducibility:** offline renderer fills and refreshes pools from a fixed seed → identical renders. Live uses the
  clock, consistent with the house "takes vary" philosophy.

## 7. Validation (harness must be REBUILT)

> ⚠️ **2026-08-11:** the `superdist.py` / `dist.py` scripts are no longer anywhere in `klang-ai` —
> only the session *renders* (`dist-*.sprudel` / `.wav`) survive. Rebuilding the 120-repeat harness
> is part of P0, and this time the scripts live durably in `klang-ai/scripts/` (session dirs hold
> only renders + results: `klang-ai/sessions/20260811-unison-phase-pool/`).

The 120-repeat single-note harness verifies per variant:

1. Hole rate (steady fundamental > 6 dB under median): from 11–21 % → **< 1 %** expected.
2. Steady fundamental sd: ~4.9 dB → ~2 dB expected.
3. Steady-state texture unchanged: long-term spectrum and band levels within ±0.5 dB of bypass (selection must not
   brighten/thin the sustain).
4. `phasePool off` renders with an **identical rng stream** to the pre-change engine (the bypass
   guarantee; guarded at 1e-9 relative tolerance + an exact stream-position golden — see §6).

### P0 measured results (2026-08-11, session `klang-ai/sessions/20260811-unison-phase-pool/`)

**All four criteria pass, with one honest reframing.** The onset window (0.05–0.35 s) is where
selection's guarantee lives: guitar-2 patch holes 15.8 % → **0.8 %**, sd 4.69 → **1.38 dB**;
clean patch holes 16.7 % → **1.7 %**, sd 5.17 → **2.00 dB**. "Never rings" (hollow in both
onset and steady windows): 6/120 → **0/120**. Texture (§7.3, clean patch): all bands ≥160 Hz
within **±0.21 dB**; fundamental region +2 dB average. §7.4 holds as a JVM golden-fixture spec
(`PhasePoolBypassGoldenSpec`, seeded rng, analog 0 — WAV-level determinism is impossible while
`AnalogDrift` seeds from `Random.Default`).

**Reframing:** the *steady* window (0.4–1.4 s) improves less (sd 5.0 → 3.6 dB, holes 13 % → 10 %)
because at spread 0.07 st the edge voices rotate ~60° within the note — the scored t=0
configuration partially re-randomizes. §1's "born hollow stays hollow (r = 0.82)" overstated
persistence: the rebuilt harness measures r ≈ 0.3 on the original Aug-08 render as well (the
0.82 definition was lost with the old scripts). Steady fundamental at guitar-2 spread is
drift-dominated; remaining steady dips are motion on notes that arrived full, not dead notes.
If that tail ever needs tightening, the parked contextual selection (§3.5) is the lever.

## 8. Phasing (updated after §9 settlement)

- **P0 — harness rebuild + stateless banded best-of-5** behind the bypass flag, verified per §7
  (baseline first: re-measure the defect on the current engine, then the fix). Smallest shippable
  win; no state, no persistence questions.
- **P1 — the pool:** born-warm fill, (orbit, unison, gain-profile) registry, random-eviction
  refresh, `roundRobin` (default) + `random` selection.
- **P2 — per-waveform tuning defaults** (supersine band! — by ear, with the user) + docs
  (`ignitor-reference.md` recipes). Includes the bands' voice-count sensitivity: shipped values
  are calibrated for unison ≈ 7–11 (in-band probability collapses at v ≥ ~16, e.g. supersine
  ~48 % at v=16) — decide between voices-aware bands or documenting the calibrated range.

(The former P2 "selection policies" phase collapsed: `roundRobin`/`random` are trivial and land
with P1; `sticky` and contextual selection are parked — see §9.)

## 9. Decisions — SETTLED 2026-08-11 (with the user)

1. **Per-waveform default bands:** ship the provisional §4 values now; tune by ear in P2 with the
   user listening. Talk can't settle this one.
2. **`sticky` boundary:** dissolved — default selection is `roundRobin` (cycle the pool array);
   `sticky` is PARKED and with it the boundary question. Hard constraint recorded in §6: the
   backend never learns about cycles.
3. **gainJitter:** stays per-note; phase candidates are scored against the note's actual jittered
   gains (draw gains first, then score). Exact K, small entries, reuse inaudible.
4. **Pool persistence:** PARKED — born-warm fill makes cold starts painless; revisit only if an
   evolved-character use case materializes.
5. **Contextual selection:** PARKED until a measured need appears.
6. **Pool key** includes the base gain profile (`sideAtten`), not just (orbit, unisonCount) — §3.3.
7. **Git flow:** phases land directly on `master-dsl`, one review-loop round per phase.

## 10. Explicitly out of scope

- superpluck and any excitation/physical-model oscillator.
- Changing phase *generation* (tables, Newman phases, bloom/detune envelopes, transient anchors) — those are separate
  ideas on the shelf; this task only selects among honest random draws.
- Scoring by non-analytic signals (rendered/heard outcomes) — the "actually learning" v3; revisit after P1 ships.
