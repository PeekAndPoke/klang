# Analog drift: is the pitch/filter ratio inverted?

**Status:** planned · **Opened:** 2026-08-11 · **Precursor:** ✅ `docs/tasks/audio-bridge-constants.md`

**The question (user, 2026-08-11):**

> Ok all the analog stuff comes from the "plastic pipe" hunt some time ago. So maybe we get it the
> exactly wrong way currently … The pitch drift is high and the filter drift is lower. Maybe it
> should be the opposite?

The hypothesis is that Klang's `analog` character is built the wrong way round. Real hardware VCOs hold pitch fairly
well; VCF cutoff wanders much more, with temperature and between units. Klang currently does the opposite.

---

## 1. Where the ratio stands

| layer                     | per unit `analog` | declared in                             | live-tunable?           |
|---------------------------|-------------------|-----------------------------------------|-------------------------|
| osc pitch, fast (τ 50 ms) | 0.2 cents         | `ANALOG_FAST_PEAK_CENTS`                | ❌                      |
| osc pitch, slow (τ 10 s)  | 0.8 cents         | `ANALOG_SLOW_PEAK_CENTS`                | ❌                      |
| **osc pitch, total**      | **1.0 cent**      | `AnalogDriftCoeffs.kt`                  | ❌                      |
| filter cutoff drift       | × 0.25 of osc     | `StageDsl.Filter.driftRelToOsc`         | ✅                      |
| filter frozen offset      | 0.0002            | `StageDsl.Filter.cutoffOffsetPerAnalog` | ✅                      |
| filter drive              | 0.25              | `StageDsl.Filter.drivePerAnalog`        | ✅ (pipeline path only) |

Pitch leads filter **4:1**. The hypothesis says it should be roughly the inverse.

## 2. What is lacking — exactly one thing

The filter side is authorable today; the **oscillator side is not**. So the ratio can only be pushed from one end: you
can raise filter drift, but you cannot lower pitch drift without editing
`AnalogDriftCoeffs.kt` and rebuilding (~5 min per trial).

That asymmetry is the whole blocker. Raising filter drift alone is *not* an equivalent test — it changes the total
amount of movement as well as the ratio, so the two variables are confounded.

## 3. Why the existing task doesn't unblock it

`docs/tasks/engine-tuning-profile.md` Part A.3 owns this, and its call is right: drift depth is **per-engine
character**, not per-oscillator, so it belongs in the Phase 3 `EngineTuning` profile rather than as standalone
`IgnitorDsl` fields. Do not re-litigate that.

But Phase 3 is a large feature — `Osc.EngineDefault` sentinel, flipped defaults across the osc fields, a per-field
resolution cascade, a KlangScript authoring surface — and it carries an unresolved design wrinkle (§"The known wrinkle":
plain-`Double` character knobs need a parallel resolution path to the node-based sentinel). Gating a by-ear question on
all of that is backwards.

**Proposal: build the smallest slice of Phase 3 that answers the question**, not a throwaway hack. Two fields, carried
on the engine, resolved at construction — the same shape Phase 3 will use, so it is a down payment rather than a detour.

## 4. Scope

**In:**

- The two depths become engine-level fields with today's values as defaults, so `modern`/`pedal`
  reproduce today's sound exactly.
- Threaded to `AnalogDriftCoeffs` construction (`Ignitors.kt:1233 initAnalogDrift`,
  `PolyAnalogDrift`), which is construction-time — **zero per-sample cost**.
- KlangScript authoring so the pair is reachable from the live editor.
- `*DefaultsSyncSpec`-family guard + wire round-trip.

**Out:** everything else in Phase 3. `driftFastTauSec` / `driftSlowTauSec` are deliberately excluded — the 06-17
findings established that the slow layer's audibility is **depth, not timescale**, and that shortening τ sounds worse.
Depth is the lever.

**Expose both layers separately, not one combined scalar.** They are perceptually distinct (50 ms micro-shimmer vs 10 s
wander) and the hypothesis may well apply to only one of them.

### Where the fields live — decide first

`PipelineDsl` is the engine, but the oscillator is **not** a pipeline stage (it is the ignitor, upstream of the strip),
so there is no existing slot. Options:

1. A tuning object carried on `PipelineDsl` alongside `stages` — the literal first slice of Phase 3's
   `EngineTuning`. Preferred: it is where Phase 3 puts it anyway.
2. A `StageDsl` entry — rejected: the drift is not a stage, and this would imply per-stage drift.

## 5. What the experiment is, once unblocked

Hold total movement roughly constant and sweep the ratio: `(osc 1.0 / filter 0.25)` today →
`(0.5 / 0.5)` → `(0.25 / 1.0)` → `(0.1 / 2.5)`. By ear on held/unison material, which is where the 06-17 doc parked this
("revisit only if held/unison patches beat too much" — a condition now met).

### Measurements that already constrain the answer (2026-08-11)

Do not re-derive these:

- **Pitch drift is constant in cents and measures so.** At `analog=12`, the fundamental deviates
  ~1.1–1.2 cents RMS at both c4 and c6, against an OU ground truth of 1.32. There is no low-note bias in the code and
  none in the measurement (c2 is unresolvable — the `analog=0` estimator floor exceeds the signal). What reads as "worse
  on low notes" is beat *rate*: 12 cents beats at 0.45 Hz on a c2 fundamental (a slow lurch, heard as out-of-tune)
  versus 7 Hz at c6 (heard as chorus).
- **Do not add a frequency taper.** Rejected 2026-06-17 and re-confirmed by the above.
- **Depth cannot fix stereo decorrelation of the top octaves.** Two superimposed voices at
  `analog=12` stay effectively mono below 640 Hz but decorrelate badly above: 0.78 at 1.3–2.6 kHz, 0.50 at 2.6–5 k, 0.23
  at 5–10 k, 0.07 above 10 k. At 12 cents the phase slips a full cycle every 14 ms at 10 kHz, so **any depth that still
  sounds analog is far past the wrap point** — halving it changes nothing up there (measured: 0.8 → 0.4 → 0.2 leaves the
  HF bands at 0.23 / 0.29 / 0.22). Depth *does* buy the 1.3–2.6 kHz band, 0.78 → 0.88, which is where wobble reads as
  pitch beating.
- **The lever for stereo width, if that is what is wanted, is a shared drift seed** between superimposed copies — they
  wander together against time and other notes but stay coherent with each other. Costs nothing per sample. Separate
  from this ratio question; note it here so the two do not get conflated.

## 6. Guard

`AnalogDriftSpec`'s cents-budget ceilings are deliberately tight and **will trip** when the ratio moves
(`driftRelToOsc ≥ 0.5` breaks `filterDriftPeak shouldBeLessThan 1.5`). That is intended — the bounds are a tuning
record, not a regression alarm. Move the bound *with* the value and say why; do not widen it in advance.

## 7. Links

- `docs/tasks/pipeline-dsl-coefficient-exposure.md` — the full survey of engine coefficients that still lack a DSL home,
  scoped into sub-tasks. **This task is its S1** — the one sub-task that blocks a by-ear question, and the one that
  creates the `EngineTuning` slot the others hang off. That doc also flags `ANALOG_MEAN_REVERSION_RATIO` (0.5), a drift
  character knob neither doc had listed: decide in or out when §4's scope is finalised.
- `docs/tasks/engine-tuning-profile.md` — Part A.3 owns the long-term home; keep these in sync.
- `docs/tasks/audio-bridge-constants.md` §6 — the precursor, and why the filter side is already live.
- `docs/tasks-archive/2026-06/20260617-analog-drift-coefficient-tuning.md` — where 0.2/0.8 came from (answer: they were
  never tuned; listed under "Deferred (intentional)").
