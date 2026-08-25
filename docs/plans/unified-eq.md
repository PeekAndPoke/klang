# Unified Equalizer — filter-chain fusion via graph optimizer

## Context

**Goal: Der Schmetterling runs on an older Fairphone 4 again.** The guitars' ignitor tone chain
(2 parallel bandpass boosts + notch + tracking highpass + double lowpass) executes as ~11 separate
Ignitor nodes — each a full per-sample loop with scratch-buffer traffic. Maintainer confirms the
filters dominate guitar-ignitor cost; guitars ≈ 98% of the song → expected ~35–60% total song CPU
reduction (2–4× on the chain portion, larger on JS). Song: `src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt`
(guitar used by 4 instruments; unison shares one chain/note, superimpose ×2 re-runs it per copy).

## Decisions (agreed with maintainer 2026-08-19)

1. **Eq = section-list model** like every parametric EQ. `.band(freq, q, db)` bell is the only
   gain-bearing section (dB; 0 dB bit-transparent). notch/highpass/lowpass/bandpass sections keep
   today's exact params (freq + q, no gain — matches DAW EQs). Shelves later.
2. **Build-as-authored + optimizer pass**: DSL tree built exactly as written; a pure, individually
   testable `IgnitorDsl → IgnitorDsl` optimizer fuses consecutive filters. Existing chained syntax
   stays THE syntax — NO changes to existing KlangScript filter methods; old songs fuse for free.
   Rewrite rules are separate documented units with expressive comments.
3. **Bit-identity (hard requirement)**: everything the optimizer rewrites (analog = 0 linear path)
   is bit-identical to the unoptimized graph. Verified in review round 1: serial linear sections
   have identical FP data deps block-major vs sample-major (AudioBuffer = DoubleArray — no Float
   round-trip at stage boundaries); SVF coefficients are strictly positive finite, so dropping the
   Bresenham `+= 0.0` steps is bit-neutral; all `controlRateValueOrNull` overrides are pure and
   mirror their `generate` math. ULP-0 parity specs on JVM + JS, **including sub-block renders
   (`offset != 0`, `length < blockFrames` — voices start mid-block, `Voice.kt:109-114`)**.
   Bells are new math (no baseline); 0 dB must be bit-transparent.
4. **Layered for reuse**: freq-agnostic `EqCore` (audio_be/filters, scalars + buffer + sampleRate;
   no Ignitor/IgniteContext/audio_bridge deps — Zig-port purity; mono, one instance per channel)
   so MasterFx.eq and the Katalyst DSL adopt the same machinery. Thin `EqIgnitor` adapter resolves
   IgnitorDsl params per block — the only place `.highpass(freq = Osc.freq().mul(pHpTrack))` can
   exist. NOTE (round 1): coefficient *smoothing* (BaseSvf-style 32-sample ramp on cutoff change)
   is a class-surface convention the ignitor surface doesn't have — EqCore ships snap-only (parity
   with SvfIgnitor) with the API shaped so a ramp variant can be added; MasterFx.eq/Katalyst
   adoption REQUIRES adding that ramp first (stated in the KDoc contract).
5. **Extras**: constant-fold in binary combinators. CAVEAT (round 1): `MemoizingIgnitor` does not
   propagate `controlRateValueOrNull` and `buildIgnitor` wraps every non-leaf node in one — so
   composite constant subtrees (`Times(Freq, Param)`) do NOT fold today. D1a starts with the
   one-line `MemoizingIgnitor.controlRateValueOrNull` propagation (bit-neutral —
   `ControlRateValueSpec` pins scalar ≡ rendered-sample). `passes = N` on standalone LP/HP.
6. **Smallest possible pieces** — even when that means touching the same code multiple times.
   Every deliverable independently tested, review-looped, and (where applicable) measured.

## Design goals (ranked) + CCC-rule

1. **It must work as intended** (bit-identity where promised, correct DSP).
2. **The eq and optimizer code must be easy to follow** — future reference for a Zig port; code
   paths stay concrete and explicit; comments follow the **CCC-rule: Concrete, Correct, Concise**.
3. **Performance must improve** — proven by measurement at each step, never assumed.

## Architecture

```
IgnitorDsl.Eq (audio_bridge wire node; authored via .band()/.eq() OR produced by optimizer)
   │ buildRaw arm (IgnitorDslRuntime.kt)
   ▼
EqIgnitor (audio_be/ignitor) — thin adapter: per-block readParam (voice freqHz lives here),
   │ pushes scalars down; upstream renders into buffer, core processes IN PLACE (no scratch)
   ▼
EqCore (audio_be/filters/EqCore.kt) — freq-agnostic mono core; section types
   LOWPASS/HIGHPASS/BANDPASS/NOTCH/BELL/RAW_TAP. Loop shape decided by MEASUREMENT in D2a
   between two bit-identical candidates (see D2a). Future MasterFx.eq / Katalyst adapters
   call configureSection(...) + process(...) — after a ramp API exists (Decision 4 note).
   Third consumer (D9): sprudel-baked per-voice FilterDefs (static/linear tier snap-safe;
   modulated tier ramp-gated like MasterFx/Katalyst).
```

**Bell math — Simper SVF bell** (verified rounds 1+2 against Cytomic formulas): `A = 10^(db/40)`;
reuse `computeSvfCoeffs(freq, q·A, sr)`; tap `v0 + m1·v1` with **`m1 = safeOut(out.k · (A²−1))`
— derived from the CLAMPED `k` that computeSvfCoeffs actually wrote, never a recomputed
`1/(q·A)`** (with the q·A clamp active the two differ — e.g. q=10, db=+120: correct m1 gives
exactly +120 dB peak, recomputed m1 is 34 dB off). The `safeOut` is the whole guard chain: scrubs
NaN (non-finite db handled upstream → 0 dB), caps the finite-but-astronomical window (db ∈
[346, 6165) gives finite m1 up to 9e305 — unbounded it ducks the master limiter for seconds or
lands NaN→full-scale DC in MasterStage) at SAFE_MAX per the house output-clamp contract; keeps
db→gain monotone. NOT a musical clamp on db (Motor stays raw — none added). Peak gain at
fc = A² = 10^(db/20); cut/boost exactly reciprocal *within the unclamped region* (q clamp
`[0.1, 200]` applies to `q·A`, so deep cuts clamp bandwidth — at q=0.707 below ≈ −34 dB; peak
stays exact). KDoc: q is the *pre-gain* bandwidth parameter, and db is COEFFICIENT-bearing (it
moves bandwidth through q·A) — an LFO on db zippers like an LFO on cutoff, same per-block snap
class as SvfIgnitor.
**RAW_TAP definition** (wire-reachable, so it is the definition, not an invariant): a tap READS
the Eq INPUT sample and CONTRIBUTES at its LIST POSITION (`acc + safeOut(v1 · gain)`). With the
insertion point pinned, both loop-shape candidates are bit-identical for any section order — and
a post-serial tap has exact legacy semantics (`Plus(chainOut, Times(bp(input), g))`). The
optimizer emits taps at the position matching the authored graph. Tap gain 0 NOT skipped (legacy
adds `safeOut(v1·0)`). 0 dB bell → per-block skip, but ONLY when db is Constant/Param-backed
(per-voice constant → no crossing possible); an expression-backed db runs the state update every
block (with A=1 coefficients) and emits `v0` via an EXPLICIT branch — not the algebraic
`v0 + 0·v1`, which flips `-0.0` → `+0.0` — so it stays bit-transparent and state stays coherent
(skip-freeze would click at every zero crossing; moving off 0 dB snaps at the block boundary,
house-consistent).

## Deliverables (each = code + tests + /review-loop round + measurement where applicable)

### D0 — Baseline measurement on UNTOUCHED code (pure additive benchmark code)
- SongBenchmark GTR-FX ladder: the existing `ladder()` helper appends pattern-chain segments and
  CANNOT vary an ignitor definition — write a small per-rung template builder (full source per
  rung: bare signal → +mids tap → +presence tap → +notch → +tracking hp → +lp×2). Enlist it with
  a one-line edit in `ladders()`; `"live"` already exists.
- EffectBenchmark anchor: extend `svfIgnitorCase` (the *Ignitor-form* cases) to a chained
  `Ignitor SVF ×4 (notch+hp+lp+lp)` composite. Do NOT anchor against the class-form
  `SvfLPF/HPF` cases — they have no scratch/plumbing, which is exactly what EqCore removes.
  **Comparability rule** (round 2): `svfIgnitorCase` measures the SINE SOURCE inside the timed
  step, while EqCore's D2a case will be `monoFilterCase`-shaped (pre-rendered source) — so also
  add a bare `Ignitors.sine()`-only baseline case, letting the source cost be subtracted; the
  D2a loop-shape decision must compare source-free deltas, not raw case times.
- Run `--args="ladders"` + `--args="live"`; commit to `docs/benchmarks/`.
- **Checkpoint**: if the tone-chain rungs measure < 15% of guitar-ignitor cost (contradicting the
  maintainer's observation), STOP and re-scope with the maintainer before D2a.
- **D0 RESULT (2026-08-19) — CHECKPOINT ESCALATED, awaiting maintainer re-scope.** Measured
  (reports: 2026-08-19_122623_song_jvm.md, _122623_effect_jvm.md, _123836_{jvm,nodejs,compare}.md):
  tone chain = ~22% of guitar-voice cost (floor-subtracted, unison 15 + analog 13). Per-node:
  chained Ignitor SVF ≈ class-form SVF on BOTH platforms (JVM 0.53 vs 0.52 µs; Node 0.79 vs
  0.85 µs incl. copy) — the scratch/plumbing the fusion removes is nearly free (L1-resident);
  JS/JVM overhead ratio is 1.3–1.6×, NOT the several-× needed to rescue the projection. Honest
  fused-EQ ceiling: ~3–5% of song CPU. The measured CPU lives in the bare signal (78%: supersaw
  unison + distort os4 + analog drift — the LEAD ladder prices analog(feel) alone at +48% of its
  base). Whole-song live baseline: medRTF 0.0752 / peakRTF 0.2831 (JVM).
  Free wins found by review regardless of re-scope: (a) D1a/D1b constant-fold (small, broad);
  (b) widen SvfIgnitor's coefficient-cache predicate to include ConstantIgnitor — script literals
  currently recompute tan() per block per filter (bit-neutral fix, pure win).
- **GTR-POLY sweep** (maintainer's cache-pressure hypothesis; suite `--args=gtrpoly`; reports
  2026-08-19_134527 ascending + _140344 descending — bounded register, both orders per the
  round-5 decisive test): chain marginal cost per voice, ascending
  1.25/1.38/1.36/1.39/1.84/1.24 vs descending 1.18/1.29/1.24/1.27/1.24/1.28 (×1e-3, K=1..64).
  **Conclusion: FLAT across K=1..64 on desktop — the ascending run's +33% at K=32 was
  turbo-clock decay** (bare-before-full ordering × 3.7-5.8× delta amplification; K=64 measured
  10% cheaper per voice when run first). No measurable cache amplification of the chain cost on
  this machine at any polyphony. The hypothesis stays open ONLY for the phone's small caches
  (A55: 32K L1/128K L2, unprobed) — D8 re-runs the suite on-device, in BOTH orders.
- **MAINTAINER DECISION (2026-08-19): continue the full plan.** The eq is needed on all DSL
  layers (ignitor / master / katalyst) regardless of perf — build it and cash in the gains along
  the way. The Fairphone goal is DECOUPLED: it needs the 78% levers (distort oversample, unison
  depth, analog drift, superimpose), maintainer-led by ear, as its own later effort.
- **Measurement procedure note (D0 round-2 finding)**: before/after comparisons use the IN-RUN
  chain total (rung5 − rung0, cycles = 32); `--args=live` comparisons re-run BOTH sides in the
  same session (the live case reads the working-tree song, which is under active mixing — a
  cross-session compare would credit/debit song edits to the engine change).

### D1a — `MemoizingIgnitor.controlRateValueOrNull` propagation + constant-fold Times/Plus
- Step 1 (own commit): `MemoizingIgnitor.controlRateValueOrNull = inner.controlRateValueOrNull(...)`
  — unlocks folding for composite constant subtrees; bit-neutral (all 13 existing overrides
  hand-verified in review to mirror their `generate` math incl. safeOut/safeDiv placement).
  Ships WITH a per-op `toRawBits()` scalar ≡ rendered-sample spec — the existing
  `ControlRateValueSpec` covers only one composite shape at 1e-9 tolerance and is NOT the
  bit-exact evidence this change needs. SECOND consumer (round 3): the pulze `duty` path uses
  `controlRateValueOrNull` as a BRANCH SELECTOR (bake-once vs per-sample PWM, Ignitors.kt:170-206)
  — a composite-constant duty flips branches after this change; verified bit-neutral (same
  arithmetic both branches) but D1a ships a **pulze** parity spec with composite-constant duty to
  pin it (pulze only — square's duty is hardwired `ConstantIgnitor(0.5)`, already non-null before
  the change, so a square case would mutation-check to nothing).
- Step 2: in `TimesIgnitor`/`PlusIgnitor` `generate()`, consult
  `other.controlRateValueOrNull(freqHz, ctx)`; non-null → in-place scalar loop, no scratch.
  Plus: bare add (matches :82). Times: `safeOut(v*k)` (matches :109). Both commutative — fold
  either side. CCC comment stating the bit-identity invariant (`tmp[i] == k` ⇒ same IEEE ops on
  same operands; commutative IEEE `+`/`*` are bitwise order-safe incl. signed zeros).
- Tests: fold path `toRawBits()`-equal to scratch path (reference via a non-foldable identity
  wrapper), incl. ±SAFE_MAX clamp cases and a composite-operand case (`Times(Freq, Param)`).
  Mutation check: drop safeOut → clamp test fails.
- Measure: re-run GTR-FX ladder.
- **D1a RESULT (2026-08-19, committed 2daaf761 + 974a18db)**: chain total (in-run headline)
  0.00153 → 0.00139 medRTF (−9.2%); guitar voice (rung5 − floor) −4.7%; mids-tap marginal cost
  halved (Δ 0.00030 → 0.00014 — the .mul(param) scratch render eliminated). Review-looped 4
  rounds (25 findings, round 4 clean); 12 mutation checks A–L. Also shipped beyond plan: the
  structural `isBlockConstant` flag (16 overrides), both-const fill branches (maintainer),
  shared fold helpers (maintainer), null-fall-through breach policy (maintainer), pulze selector
  migrated to the flag.

### Fold-work checklist (distilled from D1a's 25 findings — implement AGAINST this, hand it to
### round-1 reviewers as "verify handled", so rounds converge instead of rediscovering)
1. **Oracles must not fold**: parity references opacify EVERY operand of a folding op — one
   live foldable operand makes the oracle a fold branch (D1a's twice-found MAJOR).
2. **Parity ≠ liveness**: every fast path gets a `RenderCountProbe` anchor (`generateCalls == 0`)
   — deleting a fold branch leaves all parity cases green.
3. **Guard engagement needs discriminating values**: safeOut masks safeDiv (NaN divisor
   discriminates, zero cannot); bare (clamp-free) contracts need above-threshold pins
   (Plus 1e15+1e15 == 2e15); engagement asserts absolute values, not just parity.
4. **Sub-block coverage for every new windowed loop** — incl. separate branches like fills;
   production shape (`voiceElapsedFrames = -offset`); partial-window FIRST call on fresh state.
5. **Flag↔scalar agreement is table-driven over every child slot**, const + stateful per row.
6. **Per-op safety contracts copied exactly** (clamped vs bare per the safety table);
   non-commutative ops need per-side math; Min/Max are NOT value-commutative under NaN.
7. **Fast-path gates**: structural flag first, null-scalar falls through to the always-correct
   slow path; never throw on the render thread.
8. **Mutation protocol**: mutate code (not tests); confirm the RIGHT test reddens; finish with a
   marker-grep + git-verified full green (guards against the IDE auto-save resurrection race).
9. **Comments state current truth** — stale mutation rationale or independence claims mislead
   the next reviewer into deleting "redundant" tests.
10. **Every new override lands WITH its agreement-spec line** (standing rule below).

### D1b — Constant-fold: remaining binary ops + the 15 missing overrides
- **Scope extension (maintainer Q&A 2026-08-19)**: ternary partial folds where operands are
  commonly constant — Clamp bounds (today: TWO scratch renders per block for `clamp(-1, 1)`),
  Range bounds, Lerp t. Unary ops get NO own fill branch: with the 15 overrides in place a
  constant unary subtree folds at its PARENT, which never calls its generate — a fill branch
  inside would be dead code.
- **Standing rule (from D1a round 2, maintainer-agreed): every new `isBlockConstant` /
  `controlRateValueOrNull` override lands WITH its own agreement line in the scalar-parity spec**
  (flag ⇔ non-null). Runtime policy on contract breach: silent fall-through to the always-correct
  slow path (both breach directions degrade to correct audio; no throw, no log on the audio
  thread — house sound-first policy, same as NaN scrubbing).
- Fold: Minus, Div (hoist `safeDiv(k)`, KEEP true division — never reciprocal-multiply), Mod,
  Min, Max, Pow (signed-magnitude expr). Non-commutative ops handle each side with correct math.
- **Add the MISSING `controlRateValueOrNull` overrides — 15 nodes** (round 2; note they are NOT
  all unary: Lerp/Range/Select are ternary, Mod binary): Sqrt, Sign, Tanh, Lerp, Range, Bipolar,
  Unipolar, Floor, Ceil, Round, Frac, Mod, Recip, Sq, Select — one missing override anywhere in
  a param subtree returns null and kills folding for the entire subtree above it. **Multi-operand
  rule (round 3): resolve ALL children to non-null before returning (the MinIgnitor pattern) —
  Select must NOT short-circuit on the condition**: an untaken branch with stateful nodes must
  keep advancing (generate renders all three unconditionally); a short-circuiting override would
  diverge from the reference in later blocks. Detune stays excluded deliberately (pitch node).
- Tests: same per-op bit-parity pattern (scalar ≡ rendered), incl. Div-by-tiny params AND a
  Select case with a stateful untaken branch over ≥ 2 blocks (single-block specs can't catch it).
- **Maintainer by-ear test (2026-08-19, D1a committed + D1b working tree, browser)**: no audible
  change on Der Schmetterling — the bit-identity promise confirmed by ear — and a slight
  perceived CPU improvement from the constant paths. Exactly the designed outcome.
- **D1b RESULT (2026-08-19, committed d3f664f4)**: 2 review rounds (vs D1a's 4 — the checklist
  worked): round 1 zero arithmetic divergence, 13 findings (12 fixed, 1 rejected: Pow sign-test
  hoist — predictable branch); round 2 three MINORs, fixed; round 3 waived by maintainer on
  round 2's "production code correct as written". 9 mutation checks (M–U). Ladder: chain total
  0.00144 vs D1a's 0.00139 — flat within cross-run noise, EXPECTED (the guitar chain uses no
  D1b ops; those folds pay in other songs/expressions). No regression.
- **ON-DEVICE RESULT (2026-08-19, Fairphone 4, D0..D1b deployed): HALF OF DER SCHMETTERLING
  RUNS AGAIN.** Stall now only at the lead's entry (unison 17 + nested superimpose ≈×3 + steel
  body — the song's peak footprint moment). MAJOR calibration: the desktop-measured −4.7%/voice
  translated to run/not-run territory on the A55s' small caches — the cache-pressure hypothesis
  is CONFIRMED on-device, and D2+'s fused-EQ on-device value should be read through this lens,
  NOT the desktop 3-5% ceiling (which remains true for desktop). Remaining gap levers: continue
  D2a+ (footprint per voice) and/or maintainer's by-ear lead trims (unison, nested superimpose,
  distort os) — maintainer's call.
- Next up on resume: D2a (safety-quartet move to DspUtil.kt, then EqCore skeleton).

### D2a — `EqCore` skeleton + serial structural sections (LP/HP/BP/NOTCH) + LOOP-SHAPE decision
- Pre-step (own commit): **move the whole numerical-safety quartet — `SAFE_MIN`, `SAFE_MAX`
  (public consts), `safeDiv`, `safeOut` (internal) — plus their "Numerical Safety Bounds" header
  block from `Ignitor.kt` to `DspUtil.kt`** (which already hosts `flushDenormal`). Moving only
  half would split a documented pair (`1/SAFE_MIN = SAFE_MAX` round-trip contract; cross-linked
  KDoc). Rationale (round 3): `internal` is MODULE-scoped, so EqCore could technically import
  them today — the move is required by the package-dependency purity contract (`filters` must not
  depend on `ignitor`, for the Zig port), not by visibility; not a hard start-blocker. Call sites
  include the public `SAFE_MAX` test imports (IgnitorArithmeticTest, PitchModSafetyTest) and the
  location reference in `audio/ref/numerical-safety.md`. No numerical/inlining implication.
- New `audio_be/src/commonMain/kotlin/filters/EqCore.kt`. Constructor takes `sectionCount` only —
  `blockFrames` is NOT obtainable at construction (buildRaw has no context; IgniteContext carries
  offset/length but no blockFrames; ScratchBuffers' size is private). Input-copy buffer (needed
  only when the section list contains a RAW_TAP; serial-only Eqs process strictly in place with
  no copy). **The tap flag is a `var` recomputed by `configureSection` over the type array —
  NOT latched at construction** (round 5: section types arrive via configureSection, so a
  construction-latched flag would always be false → taps read an empty copy buffer → the exact
  silent-NaN class the idiom removes). For the copy itself, **use the FormantFilter/BodyFilter
  idiom EXACTLY —
  length-sized buffer grown on `size < length`, `copyInto(inputCopy, 0, offset, offset+length)`,
  reads via `inputCopy[i - offset]` (RELATIVE indexing)** — this removes the out-of-bounds hazard
  class (a mid-block voice start with an absolute-indexed copy would be an exception on JVM but
  SILENT NaN on JS: Float64Array reads undefined → permanently dead voice) rather than guarding
  it. (performance.md Rule 2 grow-on-shape-change; this REPLACES the earlier stricter "no alloc
  post-construction" wording.) API: `configureSection(index, type, freq, q, db, gain,
  sampleRate)`, `reset()`, `process(buffer, offset, length)`.
  KDoc contract (CCC): core owns sections/coeffs/state/loop/flushDenormal/input-copy; surface owns
  param resolution, control-tick timing, smoothing policy (core is snap-only — ramp API required
  before MasterFx.eq/Katalyst adoption), stereo (one core per channel), note frequency. No
  audio_bridge dependency — section-type Ints in EqCore (`companion object` constants); a sync
  spec (D3a) pins them to `EqSectionType.ordinal`.
- **Two candidate loop shapes, both bit-identical under the position-pinned tap definition —
  MEASURE, then keep one** (goal 3):
  (a) sample-major: one loop, per-sample small-int `when` dispatch, coefficients in parallel
  DoubleArrays; the input sample lives in a per-sample local — zero copies.
  (b) section-major: preserve the Eq input for the WHOLE call via the core-owned input copy
  (taken ONLY when a RAW_TAP is present — see above; a serial-only Eq pays no copy in either
  shape, keeping the decision benchmark unbiased), then each section runs its own specialized
  in-place loop over `[offset, length)` with coefficients hoisted to locals, taps applied at
  their list position — preserves SvfIgnitor's two documented optimizations (hoisted mode
  dispatch, register-resident coefficients) while still collapsing the 11 virtual calls + scratch
  round-trips (the per-BLOCK win, which is the big one). **Decision timing** (round 5): D2a
  implements and benchmarks both shapes on the serial-only cases; the FINAL loop-shape decision
  closes at the END of D2b, once the 2-tap rung exists (where (b) pays its copy — RAW_TAP is a
  D2b deliverable, so D2a alone cannot complete its own gate). All comparisons use source-free
  deltas (D0 comparability rule). If (a) wins, the input-copy machinery is removed in D2b, not
  shipped dormant. The winner is what the Zig port inherits. **Harness symmetry** (round 4): `monoFilterCase` pays a `copyInto` inside its timed
  step which `svfIgnitorCase` does not — for the EqCore cases hoist that copy out of the timed
  step (or subtract a copy-only baseline), else the 1-section standalone-conversion decision is
  taken on a biased number.
- Loop expressions copied verbatim from `SvfIgnitor` LINEAR branches — exact ranges:
  LP `IgnitorFilters.kt:194-203`, HP `:223-232`, BP `:237-246`, NOTCH `:250-259` (the range also
  contains the HP *saturating* branch `:209-221` — do NOT copy that). `flushDenormal` on both
  state updates. Bresenham `+= 0.0` drop is bit-neutral (coefficients strictly positive finite —
  verified; FilterEnvDef unreachable from DSL).
- Tests `audio_be/src/commonTest/kotlin/filters/EqCoreSpec.kt` (JVM+JS — JS is mandatory here:
  the input-copy OOB failure mode is silent on JS only): ULP-0 parity of single LP/HP/BP/NOTCH
  sections and a 4-section serial chain vs chained references (verify the oracle's linear branch
  matches SvfIgnitor first — SvfLPF vs Ignitor form); **sub-block cases: `offset != 0` and
  partial `length`, issued as the FIRST call on a fresh core** (a full-block call first grows the
  copy buffer and masks the sizing bug permanently); per-block cutoff changes stay stable.
  Mutation checks: drop flushDenormal, swap a2/a3, switch the input-copy read to absolute
  indexing (`inputCopy[i]`) — the partial-window-first spec must go red.
- Measure: EffectBenchmark `EqCore (4 serial)` + a 2-tap rung vs D0's anchors (source-free
  deltas), both loop shapes; ALSO a 1-section EqCore vs single Ignitor SVF case — decides
  empirically whether R1 converts single standalone filters (do not assume either way).
- **D2a BAKE-OFF STATE (2026-08-19, 3 runs, JVM)**: THREE candidates (review round 1 added
  SECTION_MAJOR_LOCALS — state snapshotted per section loop; the plain section-major's loss is
  CONFIRMED aliasing tax: buffer/ic1/ic2 all DoubleArray, JIT can't disambiguate). 4-serial
  source-free deltas, clean runs: chained 1.81, sampleMajor 1.46-1.54, locals 1.76, sectionMajor
  2.73-2.75 µs/block — sampleMajor leads at N=4. BUT 1-serial (run 3): locals 0.93 < single
  Ignitor 1.00 < sampleMajor 1.44 — sample-major's dispatch only amortizes over sections; at
  N=1 locals wins. Run 3 heavily loaded (+52% baseline drift) — 4-serial ordering unreliable
  there. **DECISION CLOSED (2026-08-19, D2b session, commit 302e7f66): SECTION_MAJOR_LOCALS
  wins.** Quiet machine, both orders (canonical 201841 + reversed 202119), all margins stable.
  Node (deployment platform — browser worklet, the Fairphone): LOCALS wins EVERY N
  (1-serial 0.44 vs 0.74µs, 4-serial 1.77 vs 2.38, 6 w/ 2 taps 2.78 vs 3.38, copy-corrected).
  JVM: sampleMajor wins N≥4 by ~18% — offline-render platform, 10× headroom, overruled by the
  sound-first hierarchy. Plain sectionMajor loses everywhere (aliasing tax confirmed).
  Guitar-tail fusion vs chained anchor: −40% Node, −28% JVM. 1-section LOCALS ≥ single Ignitor
  node on BOTH platforms (equal JVM, −38% Node) → **R1 standalone conversion: YES.** The
  answer to the N-dependence question: sampleMajor's earlier N≥4 lead was JVM-only; on V8 the
  per-sample small-int dispatch never amortizes. Losers deleted per plan.
- **D2a REVIEW RESULT (2026-08-19): 5 rounds × 2 fresh reviewers (coding + DSP), SAFETY VALVE
  reached — no formally clean round, but zero production-correctness findings after round 3;
  rounds 4-5 were bake-off fairness, docs, test power, and one API-semantics choice. All 5-round
  findings fixed (0 rejected, 0 parked). Highlights: R2 one-sided guard NOTCHed negative types
  in sample-major only; R3 UNCONFIGURED=-1 passthrough default (house breach policy resolved a
  reviewer collision), construction require + explicit dispatch, sample-major state-locals
  aliasing fix; R4 k[s] un-hoisted (dead-load bake-off bias — reversed R3 advice on per-type
  load counting), disableSection API, NaN-payload discovery (dossier item 9), per-pathology
  test variants (a single escalating array masked later pathologies — proven by a surviving
  mutation); R5 zero-state-on-disable (thump prevention), negative-half state row (the R4
  `else -> acc` fix had MOVED mutation AA's coverage to a row that didn't exist), out-of-range
  index fall-through (control-rate on audio thread — guard, never require). Mutation ledger
  V–AN: 19 mutations, 19 precise reds, git-verified restores.

### D2b — `EqCore` RAW_TAP section
- `acc + safeOut(v1 · gain)`; a tap READS the Eq input and CONTRIBUTES at its list position —
  the definition, per Architecture. CCC comment. gain flows through readParam later; safeOut
  already bounds the product (house contract) — no extra clamp.
- Tests: RAW_TAP prefix vs hand-composed `x + safeOut(bp(x)·g)` ULP-0 (incl. sub-block case);
  a RAW_TAP placed AFTER a serial section ≡ `Plus(serialChain(x), Times(bp(x), g))` ULP-0 (the
  position-pinned definition, exercised in both loop shapes). Mutation check: negate gain.
- **D2b STATUS (2026-08-19)**: RAW_TAP implemented + 15 parity rows + 8 tap mutations (AO–AV)
  red-verified before the decision session; the reserved-type row reddened on RAW_TAP's
  graduation exactly as designed. Decision session run + committed (302e7f66) → LOCALS won →
  losers DELETED (EqCore single-shape, spec de-parameterized, benchmark collapsed). Review
  round 1 (2 reviewers): both independently found (a) oracle modeled MulConst (unity
  short-circuit) instead of the wire's Times → oracle rebuilt on `Times(bp, Constant(g))`,
  unity gain now a real row; (b) input-copy GROW branch (production onset sequence:
  short-then-full block) untested → same-core continuation row added. Also fixed: tap-gain
  fusion precondition in KDoc (structurally block-constant, never by value — an LFO gain is
  per-sample in legacy Times, snapping it is a different sound); hasRawTap internal + lifecycle
  row (stuck-true flag = silent copy tax on serial cores); pathological-row comment scoped
  (the tap arm is the ONE clamped arm — its safeOut is parity, not protection); onset
  allocation rounds up to 128-multiples (allocate once, not twice — applied since LOCALS
  survived); benchmark anchor comment names the valid subtraction. Mutations AW–AZ arm the
  new rows.
- **D2b COMMITTED (1d30c9bf) after 4 review rounds** — production DSP math clean in ALL four;
  rounds 2-4 findings were docs/pins/guards. Notables: R2 collapse verified by mechanical diff
  vs the measured shape; capacity + flag lifecycle pinned via internal accessors
  (output-invisible perf properties get direct pins — new house pattern); R3/R4 added the
  process() insane-window guard (overflow-proof `length > size - offset`; never throw on the
  audio thread — an escaped exception kills the JS worklet) with all three "ignored" legs
  pinned (untouched buffer, zero allocation, frozen state); THREE optimizer fusion
  preconditions recorded in EqCore KDoc + plan R2 (block-constant gain; ===-source matching —
  structural == would fuse uncorrelated shared-RNG noise into a coherent +6 dB sum;
  left-nested Plus spine, IEEE + non-associative); benchmark subtraction recipe corrected
  TWICE, final: MemoizingIgnitor copies for EVERY consumer once shared → anchor-as-measured vs
  EqCore6−1×copy, win = plumbing + TWO net copies. Convergence declared at R4 without a
  formally clean round: every R4 fix was implemented verbatim from the reviewers' own
  specifications (D1b-waiver class, disclosed to maintainer). Mutation ledger V–BF: 37/37
  precise reds. NEXT: D2c (BELL + computeSvfBellCoeffs).

### D2c — `EqCore` BELL section + `computeSvfBellCoeffs`
- Helper in `LowPassHighPassFilters.kt`: non-finite db → 0.0 (// NaN-guard), `A = 10^(db/40)`,
  `computeSvfCoeffs(freq, q·A, sr, out)`, **`m1 = safeOut(out.k · (A²−1))` — the CLAMPED k the
  helper wrote, never a recomputed `1/(q·A)`** (with the q·A clamp active they differ by tens of
  dB), and safeOut closes the finite-but-astronomical window (db ∈ [346, 6165) → finite m1 up to
  9e305) AND the non-finite case in one guard, keeping db→gain monotone. safeOut is applied to
  the COEFFICIENT at configure time — the per-sample path stays untouched. **`m1` storage without
  per-block allocation** (round 3): `SvfCoeffs` has exactly a1/a2/a3/k/g and the helper is an
  out-param writer called ~66k×/s — add `var m1 = 0.0` to `SvfCoeffs` (verified round 4: no
  existing code path reads it — BaseSvf ramps/reads only the five); never return a Pair.
  **Stale-holder guard** (round 4): plain `computeSvfCoeffs` also writes `out.m1 = 0.0` — a
  shared per-core holder would otherwise carry a bell's m1 into the next configured section
  (harmless today, a trap for the future shelf sections). Honest KDoc limits (round 3): the cap closes
  Inf→NaN, it does NOT close the limiter-duck symptom (dB-domain one-pole release: capping 6120→
  300 dB shortens the duck ~1.7×, still seconds at extreme db — raw Motor, no musical clamp
  added); above the cap (db ≳ 346) achieved peak becomes q-dependent (`1 + 1e15·safeQ`), so
  "peak = A²" holds only below it.
- 0 dB skip: only for Constant/Param-backed db (adapter tells the core — decide the exact flag
  plumbing at implementation, document); expression-backed db runs state (A=1 coefficients) and
  emits v0 at 0 dB via an EXPLICIT branch, not `v0 + 0·v1` (−0.0 → +0.0 flip).
- Tests: 0 dB `toRawBits()`-equal to input INCLUDING a `−0.0` input sample (catches the algebraic
  shortcut); ±12 dB response at fc = A² (tolerance — new math); cut/boost symmetry INSIDE the
  unclamped region; **window test db = 5000** (finite A, m1 capped at SAFE_MAX, output finite and
  bounded); **clamped-k test q·A > 200** (e.g. q=10, db=+120 → peak exactly +120 dB — a
  recomputed-m1 implementation lands 34 dB off); modulated-db zero-crossing renders click-free
  (state continuity). Mutation checks: negate m1, remove the safeOut cap, swap the explicit 0 dB
  branch for the algebraic form.
- **D2c COMMITTED (5ba804bc) after 2 review rounds** — bell math verified EXACT by independent
  hand derivation in both rounds (peak = A² for ANY k: the q·A clamp never moves the peak, only
  bandwidth; the safeOut-on-m1 cap is the sole peak limit, db inert above ≈346; unclamped q·A
  holds half-gain width at exactly 1/q — the parameterization's point). Beyond plan: bell
  RELATION oracle `x + m1·bp(x, q·A)` gives bit-parity rows (and alone carries the q·A pin —
  response rows are k-blind, proven by BK reddening only the clamped-k row); the 0 dB branch's
  A=1 state ≡ BANDPASS state (bit) is the continuity pin; non-finite-db row (Inf legs armed,
  NaN self-heals via safeOut); bare Inf/NaN propagation pin (the bell is the one arm excluded
  from the pathological parity row — its oracle scrubs where the core is bare); SvfCoeffs.m1
  stale-holder pin; ordinal-6 tripwire restored. Mutations BG–BO (9). Ledger total V–BO = 46.
  NEXT: D3a+D3b (Eq wire node + EqIgnitor adapter, ONE review round — exhaustive-when forces
  the pairing).

### D3a — `IgnitorDsl.Eq` node + wire codec + mapping spec
- **MAINTAINER DECISION (2026-08-20): sealed hierarchy, NOT an enum.** `sealed class EqSection`
  with `@WireName` variants each carrying exactly its own params: `Lowpass/Highpass/Bandpass/
  Notch(freq, q)`, `Bell(freq, q, db)`, `RawTap(freq, q, gain)` — all params IgnitorDsl
  (modulatable). Rationale: per-type params (no ignored fields), house pattern (FilterDefs,
  Master DSL sealed wire stages), exhaustive `when` arms everywhere, and it DELETES the
  enum-ordinal append-only wire risk outright (name-keyed variants are order-independent).
  Future shelves add variants with their own shapes. EqCore keeps its plain Int constants
  (bridge-free layering); the D3b builder maps variant → Int in one exhaustive `when` (a new
  variant without a mapping fails compilation); a slim mapping spec guards swapped arms.
- `audio_bridge/.../IgnitorDsl.kt` (after Notch): `@WireName("eq") data class Eq(inner,
  sections: List<EqSection>)`. `collectParams` walks inner then sections (per-variant params).
  `maxReleaseSec` arm: `inner.maxReleaseSec()`.
- `buildRaw` needs an arm to compile (exhaustive when): land D3a+D3b as one review round; if
  split is forced, the stopgap is a passthrough arm building the chained equivalent — never
  `error()`.
- **D3 STATUS (2026-08-20)**: implemented as one slice per plan; base green on first full run
  except one WRONG TEST EXPECTATION (Sine's own "analog" drift param precedes section params
  in collectParams — the inner-first contract working as designed). KSP codec handled the
  sealed-in-sealed EqSection hierarchy with ZERO adjustments (every variant round-trips).
  End-to-end parity rows (chained-vs-fused through the real toExciter, incl. the full 2-tap
  guitar tail with identity-shared legacy source = the D4d replacement target) green on first
  contact. New adapter pins: mid-voice glide retrack (fresh-per-freq rows can't see a frozen
  cache), transient-0dB dynamic bell not disabled (EqCore-direct reference).
- **D3 COMMITTED (a4b19970) after 2 review rounds.** Round-1 HIGH (the workstream's sharpest
  catch): EqIgnitor resolved section params BEFORE upstream.generate — reordering
  drift/unison/noise draws from the shared global Random vs the chained path; fixed
  (upstream first), pinned WHITE-BOX (probe ignitors record call order) because cross-tree
  parity cannot observe RNG order (dossier item 10). Round-1 also: unpinned Bandpass mapping
  arm, unpinned withMod invariant (all rows built Eq at root where mod==null → vibrato-wrapped
  row arms both halves), per-variant collectParams ON the sealed interface, defaults aligned
  to chained nodes. Round-2 (all doc/pin, reviewer-authored): static-cache liveness counters
  across TWO blocks, 48 kHz parity row (deployment rate was untested — a hardcoded 44100
  would have shipped green), codec q values de-defaulted (the defaults change had invalidated
  the row's own guarantee in the same commit), RawTap gain caveat, "deliberately wider"
  predicate comment. Mutations BP–BX (10). Ledger V–BX = 55. NEXT: D4a (childNodes/
  withChildNodes walkers + optimizer skeleton).
- `EqSectionTypeSyncSpec`: pins `EqSectionType.ordinal` ↔ EqCore's Int constants (the
  NoiseDefaultsSyncSpec pattern) — EqCore stays audio_bridge-free.
- Codec: KSP auto-discovers (nested classes + List + enum-ordinal supported); decoder has NO
  missing-field defaults — benign (same-build main⇄worklet wire only, songs persist as source);
  state in commit message. `IgnitorDslWireCodecSpec` entry, every field non-default, exercising
  the LAST enum entry.
- Tests: codec round-trip; collectParams order.

### D3b — `EqIgnitor` adapter + builder arm (same review round as D3a)
- `EqIgnitor` (class form, fields in constructor): upstream → buffer, per-section per-block
  `Ignitors.readParam`, `core.process` in place (no scratch). Two pinned invariants (CCC comments
  with counterexamples):
  1. Builder arm: `withMod()` ONLY on `inner`; `noMod()` on all four section params — mirrors
     the existing filter arms exactly; withMod on a param subtree would change the freqHz the
     params see and break tracking-HP parity.
  2. Coefficient-cache predicate is NODE-TYPE-based (`ParamIgnitor`/`ConstantIgnitor` only) —
     NEVER widen to `controlRateValueOrNull != null`: `FreqIgnitor` is block-constant but NOT
     voice-constant (`.detune(lfo)` changes freqHz per block; a widened predicate would freeze
     coefficients at block 0). This node-type property is exactly why SvfIgnitor's existing
     predicate (IgnitorFilters.kt:164) is unaffected by the D1a MemoizingIgnitor change — cite
     as already-true precedent. Caching Constant-backed too is bit-neutral: computeSvfCoeffs is
     pure, skipped reads are side-effect-free. **Granularity: PER SECTION, not per Eq** (round 3)
     — the guitar chain has ONE expression-backed param (tracking HP) among ~6 sections; a per-Eq
     predicate would recompute tan() for all sections every block, a pure regression silently
     attributed to the "+tracking hp" ladder rung.
- Builder arm in `IgnitorDslRuntime.kt` after Notch (~:356).
- Tests: `IgnitorDslRuntimeTest` arm + oscParams override; parity spec (deterministic sources —
  noise is unseeded): DSL chains vs hand-built Eq ULP-0 bit-equal incl. tracking-HP across several
  freqHz, sub-block render case, BELL 0 dB ≡ passthrough.

### D3c — Seeded per-voice RNG (coreRandom derivation tree) — ADDED 2026-08-20, maintainer-directed
- **Why**: (1) dissolves the RNG-order bug class OUTRIGHT (the D3 HIGH was one instance: any
  two code paths promising the same sound could diverge through the order of draws from the
  process-global `Random`); (2) **bit-reproducible playback/offline renders** — the
  maintainer's explicit goal ("anyway good if we want bit-identical reproduction").
- **Design (maintainer + session, 2026-08-20)**: a DERIVATION TREE mirroring the ownership
  tree. `PlaybackCtx.coreRandom = Random(0x5EED)` (fixed Int seed — reproducible by DEFAULT;
  drift/noise character is statistically identical either way). Per voice:
  `voiceRandom = Random(coreRandom.nextInt())` — the maintainer's dealing scheme; Int seeds
  only (JS Long ban). ONE instance per voice shared by the whole sub-graph (in-graph draw
  order is deterministic → reuse is safe — maintainer's sub-graph rule); it reaches
  build-time consumers via `IgnitorBuildCache.random` (4th passenger, the
  soundIndex/phasePools precedent) and generate-time constructions via
  `IgniteContext.random` — SAME instance both channels.
- **Rejected: sourceId as the seed** (maintainer asked; answered from the project's own
  double-voice-fix lesson): chords share sourceId, superimpose copies share
  sourceId+time+note — identical seeds would collapse them into coherent +6 dB sums
  (audibly different; superimpose is load-bearing in Der Schmetterling). Also settled: seq-
  mixing buys NOTHING over dealing (both are functions of creation index; inserting a note
  shifts all later seeds either way — maintainer's observation). Documented upgrade path on
  coreRandom's KDoc: FULL-identity-derived seeds if live-edit-stable drift is ever wanted.
- **Scope** (all voice-path consumers; orbit-level effects verified RNG-free): 7 noise call
  sites in the runtime (cache.random), 6 drift construction sites (ctx.random incl.
  initAnalogDrift), 6 super-oscillator factories + karplusStrong/superKarplusStrong (rng
  params threaded from cache.random; pluck classes' hardcoded rng fields become params),
  IgnitorRegistry.createExciter + VoiceFactory threading (voiceRandom into BOTH toExciter
  and IgniteContext). Defaults stay `Random` (global) so tests/tools keep working.
- **Tests**: reproducibility row (same seed → bit-identical voices WITH active drift+noise);
  cross-tree chained-vs-fused parity WITH ACTIVE DRIFT (same-seeded Randoms both sides —
  upgrades the D3 white-box order probe to a black-box guarantee and RELAXES dossier item 10
  for same-seed setups); phase-pool interplay audited in review.
- **Status: COMMITTED (d3b77cc4) after 2 review rounds.** Round 1 found the completeness
  holes that made the claim false end-to-end: per-voice filter cutoff tolerance + filter
  drift still global (fixed by HOISTING the deal above the filter build — which also made
  the core draw count independent of async sample-load timing), SampleIgnitor wow/flutter
  never read the stream (dead-wired deal). Round 2: the honest pid story (live pids are
  AUTO-generated → takes vary by design; offline/benchmark constant pids are the
  reproducible paths — "user-chosen names" was false), pid mixed into coreRandom (concurrent
  same-song playbacks decorrelate; PhasePools idiom). Toothless-fixture catches: constant
  PCM hides rate modulation (sample row needs SINE); a chained --tests filter produced 3
  SPURIOUS mutation reds (empty-name tell; guard now standard + kotest-filter memory
  updated). PAYOFF BANKED: EqIgnitorSpec's drift-ACTIVE chained-vs-fused row — dossier item
  10 RELAXED for same-seed setups; D4c corpora may use active drift with same-seeded
  streams. Mutations BY–CH (9). Ledger V–CH = 64.

### D4a-D4c — SHIPPED 2026-08-21 (commit 076683ab)
Walkers + optimizer + kill switch + registry seam + R1 serial fusion, all in one commit.
**Der Schmetterling's guitar tail now fuses to ONE node** (6 sections), bit-identical.
R2 (parallel tap fusion) is NOT implemented and is the top item in
`docs/tasks/ignitor-optimizer-followups.md`, along with the Eq-merge trap (a tap reads the
input of ITS OWN Eq, so merging nested Eqs is only safe when the outer list has no taps),
one-pole sections, the Bandpass/Notch analog nuance, and pitch-mod walls.
Standing rule minted here: **bit-identity is a testing hazard** — where right and wrong render
the same samples, output comparison proves nothing (the createExciter seam, an
over-conservative optimizer, and RNG draw order all needed structural or white-box guards).

### D4a — Optimizer skeleton: `children()`/`withChildren()` walkers + refcount + rewrite (NO rules)
- **The honest size of this slice** (round 2): `IgnitorDsl` has NO generic child
  enumerate/rebuild — every existing walker is a hand-written ~80-arm `when` (77 subtypes). D4a
  delivers TWO new members in audio_bridge: **`childNodes()` / `withChildNodes(new)`** (named to
  avoid the `Variants.children` PROPERTY — a `children()` function would shadow-collide inside
  the Variants arm), both **exhaustive expression-form `when` (house rule — NEVER an
  `else -> this` fallback: a fallback silently never descends into a later-added node, killing
  optimization under it with no compile error)**. Variable-arity contract (round 3): the child
  ORDER per node is defined and documented (Variants: children as-is; Eq: inner first, then per
  section freq,q,db,gain — 1 + 4×N), and `withChildNodes` `require()`s a size match (internal
  invariant — the house require-rule allows it); a mis-chunk must fail loudly, not swap a bell's
  q into its db. D3a's Eq and D4b's OptimizerHint each register in exactly these two walkers +
  collectParams + maxReleaseSec + buildRaw.
- `IgnitorDslOptimizer.kt`: pure `fun IgnitorDsl.optimize()`. Pass 0: identity (`===`) refcount
  (linear-scan idiom from `IgnitorBuildCache` — no IdentityHashMap in common Kotlin, data-class
  hashing is O(tree)). Pass 1: post-order rewrite with identity memo old→new (shared subtrees
  stay shared; untouched subtrees return the SAME instance). CCC header: the two passes + the
  sharing contract + **rule guards consult the refcount of the PRE-rewrite (original) child,
  never the freshly built node** (a fresh Eq is always refcount-1; consulting it would fork
  shared intermediates — silent CPU regression).
- Tests: `children`/`withChildren` round-trip per node kind; identity on a corpus (every node
  `===`); shared-subtree `===` preserved; idempotence.

### D4b — Optimizer kill-switch: `.optimizer(on = 0)` for A/B ear verification
- Purpose: verify the bit-identity promise BY EAR on any live sound; safety hatch for fusion bugs.
  Marker ANYWHERE in a definition disables optimization for the WHOLE registered graph (pre-scan
  during the Pass-0 walk).
- New DSL marker `@WireName("optimizerHint") data class OptimizerHint(inner, on: Int = 1)` —
  plain Int, structural (the optimizer runs at REGISTRATION time — see D4c seam), coerced
  `on != 0`, never require. collectParams/maxReleaseSec passthrough. Dissolves in the
  `buildIgnitor` prologue (like Variants/pitch-mod — zero runtime cost); `buildRaw` still needs
  its exhaustive-when arm — `error("dissolved in buildIgnitor")`, the Variants precedent.
- KlangScript: `optimizer(self, on: Number = 1)` wraps self (included here — the switch is only
  useful if script-reachable).
- Tests AT THIS STAGE (no rules exist yet): runtime output bit-equal with/without the marker;
  codec round-trip (on = 0, non-default); StdLibOscTest dual-language. The "on=0 → rules don't
  fire / on=1 → rules fire" assertions move to D4c where rules exist.

### D4c — Rule R1: serial filter fusion + registry seam
- **R1**: filter ∈ {Lowpass, Highpass, Bandpass, Notch} with `analog == Constant(0.0)` (a Param
  analog NEVER fuses — oscp could enable saturation; verified: DSL and script defaults are
  Constant(0.0), so the guitar chain fuses) whose ORIGINAL inner is refcount-1 and rewrites to an
  Eq → append section; or ORIGINAL inner is a refcount-1 fusible filter → new 2-section Eq.
  (No `passes` guard needed at D4c — the field does not exist until D6, and D6's same-commit
  rule "R1 learns passes-expansion + fusion test at passes=2 in the SAME commit that adds the
  field" is the protection.) Whether single
  standalone filters convert to 1-section Eqs = decided by the D2a measurement — **but rule
  interaction pinned NOW (round 3): post-order rewriting converts the tap's Bandpass BEFORE the
  enclosing Plus is visited, so if standalone conversion is on, R2 MUST also match a 1-section
  BANDPASS Eq as the tap source** (else the guitar tail silently stops fusing and only the D4d
  benchmark would notice); a tree test covers the interaction. OnePoleLowpass excluded. Fusion
  never crosses pitch-mod wrappers.
- **Seam: `IgnitorRegistry.register()`** — NOT createExciter: createExciter runs once per voice;
  a structural `getOrPut(dsl)` there would pay an O(tree) data-class hash per note-on plus a
  first-note optimize() allocation on the render path. `register()` is String-keyed and runs once
  per registration, OFF the per-voice path (on the JS worklet it still executes on the audio
  thread between quanta, alongside the wire decode that already happens there — the win is
  per-registration vs per-note, not thread placement); ALL registration paths funnel through it
  (VoiceScheduler, KlangOfflineRenderer). Three pinned invariants (round 2):
  1. **Overwrite, not getOrPut — gated on reference identity**: recompute
     `optimizedDefs[key] = dsl.optimize()` when `dsl !== defs[key]` (checked BEFORE the defs
     write — no third map; defs already holds the last-registered tree). Names ARE re-registered
     with edited trees (KlangOfflineRenderer user names can even override built-ins), so a plain
     getOrPut would keep playing the old sound after a live edit. Honest sizing (round 4): the
     gate is belt-and-suspenders — on the JS worklet every DSL arrives wire-decoded as a FRESH
     instance, so every register() pays a full optimize() there; upstream, RegisterIgnitor
     already fires once per structurally-unique tree (klang-side `sentToBackend` set), never per
     note. D8's registration-latency measurement is sized on every-register-pays.
  1b. **`register()` MUST remain total** (round 4 — MAJOR): the worklet onmessage dispatch has
     NO try/catch; an `optimize()` throw (e.g. the withChildNodes size require() hitting an
     arity bug) would unwind after `defs[key]` is written but before `optimizedDefs[key]` —
     silently dropping every voice of that instrument on JS (VoiceFactory's contains() reads
     defs, createExciter returns null) while JVM throws loudly, AND the kill-switch cannot
     rescue it (it lives inside the tree that failed). Fix: wrap `optimize()` in try/catch —
     log, and **in the catch write `optimizedDefs[key] = dsl` (the authored tree) — never leave
     the previous entry standing** (round 5: a stale entry after a throwing RE-registration
     would keep PLAYING the old sound while `get()` returns the new tree — the offline renderer
     explicitly supports overriding built-in names, so this is a real path); the optimized
     lookup still falls back `optimizedDefs[key] ?: defs[key] ?: parent-delegate`. The pinned
     spec (round 5 wording): **a throwing optimizer never serves a tree other than the
     last-registered one** — with a re-registration case (first tree optimizes, second throws).
  2. **Parent delegation**: built-ins register on the ROOT registry; `createExciter` runs on a
     per-playback FORK (`VoiceScheduler` forks). The optimized lookup MUST mirror `get()`'s
     `defs[key] ?: parent?.get(name)` fallback — a naive local `optimizedDefs[key]` returns null
     for every built-in and VoiceFactory silently drops the voice (whole song goes silent).
     Each registry optimizes what IT registered; forks delegate upward.
  3. Both maps keyed identically with `name.lowercase()`.
  `get()` returns the AUTHORED tree — rationale: the kill-switch A/B and `maxReleaseSec`
  consumers (VoiceFactory) must see the authored graph.
- Tests: tree-in/tree-out (chain→one Eq; analog Const(2.0)/Param doesn't fuse;
  `let`-shared intermediate keeps `===` sharing — incl. the fresh-Eq-fork counterexample:
  `let t = sig.notch(...); t.lowpass(a).add(t.lowpass(b))` must NOT duplicate the notch;
  children of Variants/binary ops rewritten; idempotent; kill-switch on=0 → `===` identity,
  on=1/absent → fusion fires); rendered ULP-0 parity `toExciter()` vs `optimize().toExciter()`
  over a corpus incl. sub-block renders (JVM+JS); **param-collection assertion: SET of param
  names + first-occurrence order unchanged** (NOT full list equality — collectParams walks
  references, so fusing shared subtrees legitimately removes duplicate entries);
  `maxReleaseSec` equal.
- Measure: re-run GTR-FX ladder → serial-tail rungs collapse; record delta.

### D4d — Rule R2: parallel-boost taps — makes the song fuse completely
- **Two preconditions pinned during D2b review round 3 (already implicit in the pattern below,
  now explicit):** (1) ALL node matching is reference IDENTITY (`===`), never data-class `==` —
  runtime sharing is keyed on IgnitorBuildCache's (node identity, accumulated-mod identity)
  pair, so two structurally-equal source nodes are independently-phased oscillators and a
  structural match would fuse a tap of B onto A (audibly wrong, spec-invisible); (2) taps fuse
  only from the LEFT-NESTED Plus spine — `Plus(x, Plus(t1, t2))` sums `x + (t1 + t2)` and
  IEEE `+` is not associative; the R2 pattern (tap operand must be a Times) already refuses
  that shape, keep it that way. Both are recorded in EqCore's KDoc too.
- Pattern (both operand orders; IEEE `+` bitwise commutative): `Plus(base, Times(Bandpass(tapIn,
  f, q, analog=Const(0)), gain))`, Plus/Times/Bandpass ORIGINAL refcount-1, **and the tap's
  freq, q AND gain all Constant|Param-backed** (round 3): value-bit-identity is proven for any
  order, but fusing moves WHERE in the block the tap's params are EVALUATED — an expression-backed
  param with order-dependent state (noise draws from the shared global Random) would produce
  different draws. Constant/Param params are order-free, which also makes the withMod→noMod
  reparenting a no-op (Times builds its right operand withMod; EqSection params are noMod). The
  real song's taps are all Param-backed → fuses. Cases: (a) `base === tapIn` →
  `Eq(tapIn, [RAW_TAP])`; (b) `base` is an Eq with `base.inner === tapIn` **AND the ORIGINAL
  node that rewrote to `base` is refcount-1** (round 4 — same guard class as R1: a `let`-shared
  base would fork, duplicating tap sections across two Eqs, a silent CPU regression) → append the
  tap at the END of the section list — under the position-pinned tap definition a post-serial tap
  has exact legacy semantics (`Plus(chainOut, Times(bp(input), g))`), so the round-1 refusal is
  lifted.
- Result: whole guitar tail = 1 node, zero sound change, zero song edits; `signal` consumers drop
  3→1 → MemoizingIgnitor takes its direct no-copy path.
- Tests: pattern-match + non-matching shapes (different tap source, stateful gain, shared Times,
  **`let`-shared base** — must NOT fuse, sharing survives `===`);
  post-serial-tap fusion rendered ULP-0 vs unfused; full transcribed guitar tail rendered ULP-0
  parity (JVM+JS, sub-block case); **param-collection assertions: name-SET equality always;
  first-occurrence order asserted ONLY for the base-first operand shape** — the swapped-Plus
  rewrite legitimately reorders first occurrences (Plus walks left-to-right; Eq walks inner
  first), documented as such.
- Measure: `--args="live"` suite; record delta.

### D5 — SHIPPED 2026-08-20 (surface `.eq()` / `.band()` / `.tap()`, both doors)
**What shipped, beyond the original scope:**
- `.eq()` entry point on any IgnitorDsl (idempotent back-to-back; an `.eq()` after other
  filters opens a SECOND Eq, documented).
- `.band(freq, q = 0.707, db = 0.0)` → `EqSection.Bell`, SERIAL (bands compound).
- **`.tap(freq, q = 1.0, gain = 1.0)` → `EqSection.RawTap`, PARALLEL** (reads the Eq input,
  taps sum). NOT in the original plan; added after the maintainer heard the +4.5 dB cross-term
  overshoot from migrating the song's parallel tap bank to serial bells. This is the faithful
  migration target for multi-tap chains and it made RAW_TAP (built in D2b) reachable.
- Both section methods are TYPE extensions on `IgnitorDsl.Eq` (maintainer's call: the supersaw
  config-method pattern), NOT base-type methods: no auto-wrap, `.band()` on a plain osc is a
  dispatch error. Future section methods land on the same receiver.
- Der Schmetterling's guitar migrated to `.eq().tap().tap().notch().highpass().lowpass()x2`,
  bit-identical to the hand-built chain (pinned by a surface-level EqIgnitorSpec row), and
  **the FF4 goal fell out of it** (see the milestone note above). Only the two taps fuse; the
  four serial filters still chain, so D4's optimizer still has those to claim.
- Parameter-parity pins minted: wire-ctor defaults == surface defaults for BOTH Bell and
  RawTap, plus the eqdemo preset knob (three legs of the same drift).

### D5 (original scope) — surface `.eq()` / `.band()` — BOTH DOORS (maintainer rule 2026-08-20)
- **Every DSL surface addition lands on BOTH doors: the KlangScript stdlib function AND the
  Kotlin fluent extension in audio_bridge** (sprudel is the model; see
  docs/tasks/dsl-kotlin-surface-parity.md + memory feedback_dsl_dual_surface). D5 therefore
  ships `IgnitorDsl.eq()`/`.band(...)` Kotlin extensions alongside the stdlib functions,
  with identical names/defaults (parameter-parity rule).

### D5 (original scope) — KlangScript surface `.eq()` / `.band()`
- `klangscript/.../stdlib/KlangScriptOscExtensions.kt`: `eq(self)` (idempotent wrap) and
  `band(self, freq: IgnitorDslLike, q: IgnitorDslLike = 0.707, db: IgnitorDslLike = 0.0)` — all
  IgnitorDslLike (EqSection fields are IgnitorDsl); copy-onto existing Eq (the Adsr
  `declickSeconds` idiom) else wrap. NO alias: `@alias` is docs-only — a callable alias needs a
  second annotated function (the `warmth`/`onePoleLowpass` pattern); keep the surface minimal,
  no `bell()`. NO fold-in changes to existing filter methods. KDoc shows positional usage.
- Tests: `StdLibOscTest` dual-language (band wraps / appends; eq idempotent). Update
  `klangscript/ref/feature-catalog.md`. (A klangblocks round-trip row shipped with D5; the
  block editor was removed 2026-08-23 and that row went with it.)

### D6 — `passes` on standalone Lowpass/Highpass
- DSL: `val passes: Int = 1` (plain Int, structural). Builder: `repeat(passes.coerceAtLeast(1))`
  — coerce, never require. **Same commit: R1 learns to expand passes into N sections + fusion
  test at passes=2 asserting N sections (and −24 dB/oct slope)** — the field and its optimizer
  handling land together, so no window exists where R1 could mis-fuse a passes=2 filter.
  Script param `passes: Number = 1`. Codec spec updated (non-default). Tests: −24 dB/oct for
  passes=2 (tolerance), optimizer expansion, round-trip.

### D7 — Song bell migration (OPTIONAL; per-band exact, but see the TOPOLOGY WARNING)
- With R2, zero edits needed for the full speedup. ONE parallel tap IS a bell (round-1 DSP
  review): `1 + a·BP(s)` matches the Simper bell exactly with **`A² = 1 + a·Q` AND
  `q_bell = Q / A`** — both parameters convert.
- ⚠ **TOPOLOGY WARNING (measured 2026-08-20, on the real song): N parallel taps are NOT N
  serial bells.** Parallel sums `1 + a₁H₁ + a₂H₂`; serial bells multiply
  `(1 + a₁H₁)(1 + a₂H₂)`, leaving the cross term `a₁a₂H₁H₂`. On Der Schmetterling's guitar
  (mids 850 Hz a1.7 + presence 2500 Hz a5.0) that term is **+4.5 dB at 1200 Hz**; on the lead
  and guitar1 **+4.9 / +4.4 dB around 1.2-1.7 kHz** — audibly "mids and highs too strong",
  confirmed by ear by the maintainer. The per-band conversion above is still exact; what does
  not survive is converting a PARALLEL bank into a SERIAL section list.
- **Therefore the faithful migration target for multi-tap chains is RAW_TAP, not BELL** —
  RAW_TAP reads the Eq INPUT and contributes at its list position, which IS the parallel
  topology inside the fused core (that is exactly what R2/D4d emits, bit-identically). A
  BELL-based migration is a deliberate NEW mix, not a conversion, and must be re-tuned by ear.
  Blocking gap: RAW_TAP has no DSL surface yet (D5 shipped `.band()` only) — a `.tap(freq, q,
  gain)` section method on the same typed receiver is the missing piece for hand-authoring the
  faithful form. Per-band table (single-tap exactness, unchanged):
  | band | amount×Q | dB | q_bell |
  |---|---|---|---|
  | mids default | 2.0 × 0.707 | +7.7 | 0.455 |
  | mids GTR1 | 2.0 × 0.8 | +8.3 | 0.496 |
  | presence default | 5.0 × 0.7 | +13.1 | 0.329 |
  | presence Lead | 4.7 × 0.9 | +14.4 | 0.394 |
  | presence GTR1 | 5.5 × 0.85 | +15.1 | 0.357 |
  | presence GTR2/3 | 5.3 × 0.8 | +14.4 | 0.349 |
- One spec asserts parallel-tap ≡ bell response for several (amount, Q) pairs (near-bit tolerance
  — one pow/tan rounding). Rename params mids→midsDb etc. `.lowpass(5300, 0.707, 2)` optional (C5 fixed the slot order: freq, q, passes, analog).

### D8 — Benchmarks, JS + device acceptance, docs, parity table
- EffectBenchmark: full-chain composite `EqCore (2 tap + 4 serial)`; run complete comparison.
- SongBenchmark: GTR-FX ladder re-run + `--args="live"`; results to `docs/benchmarks/`.
  **Also time `register()` (incl. optimize()) in the live run** — registration bursts at song
  start run between worklet quanta; bound the spike (round-3 finding).
- JS: `console/run-dsp-benchmarks.sh`. Acceptance: browser ≈ ×2.7 JVM; live-suite peakRTF with
  headroom; maintainer does the on-device Fairphone pass (sound first).
- **Parity table row NOW, not later**: `docs/tasks/master-dsl-followups.md` §5 requires parity
  names recorded BEFORE shipping — record ignitor `.band(freq, q, db)` alongside the master
  `eqMidDb/eqMidHz/eqMidQ` proposal so the collision is resolved on day one (same meaning/scale).
- Docs: audio/MEMORY.md entry; note EqCore exists for MasterFx.eq (with the ramp-API precondition).
- Fallback levers if the phone still misses: superimpose depth, distort oversample factor.

### D9 — Sprudel voice-filter baking → EqCore (maintainer-added 2026-08-20, LAST in order)
**The third EqCore consumer.** Sprudel-sourced filters (`.lpf()/.hpf()/.bpf()/.notch()` pattern
params → `FilterDef` list on VoiceData) are baked per voice in `VoiceFactory.makeVoice`
(`voiceFilterDefs.map { it.toFilter(...) }` → `ChainAudioFilter`) — N separate class-form
filters, each a virtual per-sample loop over the block, exactly the per-node plumbing the
ignitor fusion removed. Bake the eligible run into ONE EqCore pass instead. Reach is broad:
this path runs for EVERY voice with filters (synth + sample voices), in every song.
- **Eligibility tiers (ground truth from VoiceFactory/LowPassHighPassFilters, read 2026-08-20):**
  1. **v1 fuses: static + linear + Svf-form** — no filter envelope, `analog == 0` (no drift, no
     saturating branch, offsetMul == 1), `q != null` (Svf, not one-pole). Static filters are
     never retuned (construction snaps via `setCutoffSnap`, no ramp ever fires), so the
     BaseSvf linear loop vs EqCore section is a pure loop-shape reorder — the D2a bit-identity
     class. A parity spec must PIN BaseSvf-static ≡ EqCore section ULP-0 first (BaseSvf and
     SvfIgnitor share `computeSvfCoeffs` and the Cytomic form, but the pin is evidence, not
     assumption; incl. sub-block + 48 kHz rows).
  2. **NEVER fuses: `analog > 0` LPF/HPF** — the state-dependent-damping saturated branch is
     deliberate nonlinear character EqCore does not have (raw-Motor; BPF/Notch stay linear by
     decision but carry offsetMul/drift at analog>0 — v1 keeps ALL analog>0 defs class-form
     for one simple rule; offsetMul-only fusion for BPF/Notch is a v2 refinement, it is just
     a constant cutoff multiplier).
  3. **Ramp-gated: envelope- or drift-modulated filters** — FilterModRenderer retunes per
     block via `setCutoff`, and BaseSvf masks the coefficient discontinuity with the
     32-sample increment ramp; EqCore is snap-only. Fusing these BEFORE EqCore has a ramp API
     is an audible regression class (zipper on swept envelopes). Adding the ramp API (the
     SAME Inc-based semantics, bit-compatible, not just "a" ramp) is the documented
     precondition — and the same API unblocks MasterFx.eq + Katalyst adoption. That is D9's
     optional second phase; do not block v1 on it.
  4. **One-poles (`q == null` → OnePoleLPF/HPF) stay class-form in v1** — EqCore has no
     one-pole section type; they are cheap single-state filters, and OnePole HPF carries the
     documented cutoff bias (raw-engine philosophy — do not "fix" it by swapping in an Svf).
     New ONEPOLE section types are a v2 option if measurement says they matter.
- **Seam**: the `filters.combine()` site — an `EqCoreAudioFilter : AudioFilter` adapter wraps
  one EqCore; VoiceFactory folds the maximal CONTIGUOUS run of tier-1 defs into it (chain
  order is a sound decision made upstream — never reorder around an ineligible filter);
  ineligible defs stay class-form in the same ChainAudioFilter. v1 may simplify to
  all-or-nothing per voice if contiguous-run bookkeeping isn't worth it — measure first.
- **BE stays cycle-free**: untouched — this consumes the FilterDef list as-is, pure
  voice-build-time rewiring.
- Tests: BaseSvf-static ≡ EqCore parity pins (the gate for everything); fused-voice vs
  class-form-voice render ULP-0 over eligible corpora (JVM+JS, sub-block); eligibility
  boundary rows (envelope present → class-form; analog>0 → class-form; q null → class-form);
  chain-order preservation row (eligible-ineligible-eligible sandwich).
- Measure: song benchmark on a filter-heavy sample song; on-device lens applies (desktop
  per-node cost is ~free, the phone's small caches are where run/not-run lives).

### 🎉 ON-DEVICE GOAL REACHED (2026-08-20, Fairphone 4)
**Der Schmetterling runs smoothly on the FF4 at ~75% CPU** — the plan's opening goal, met.
Desktop measured -33% CPU on the same change. Path that got there: D0-D1b constant-fold (half
the song back), then D2-D3 EqCore + adapter, then D5's `.eq()/.tap()` surface letting the song's
guitar tail actually USE the fused core. **Be precise about what fused:** the TWO parallel taps
became one Eq node; `.notch()/.highpass()/.lowpass()/.lowpass()` still chain after it (there are
no section methods for serial filters yet), so the tail is 5 nodes, down from ~11 — not 1.
**Consequence: D4 still has real headroom in this song** — four foldable serial nodes per guitar
voice remain unclaimed, which is plausibly part of why the FF4 sits at ~75% rather than lower. Note this was achieved WITHOUT the D4 optimizer: the song was hand-migrated
to the surface. D4 remains valuable (old songs fuse for free, no edits), but it is no longer on
the critical path for the device goal.

## Bit-identity dossier (verified round 1; parity specs pin it)
1. Block-major → sample-major reorder: no cross-sample coupling; AudioBuffer = DoubleArray (no
   Float round-trip at stage boundaries); no FMA contraction on JVM/JS. 2. Verbatim SvfIgnitor
   LINEAR-branch expressions (exact ranges in D2a) + flushDenormal + same computeSvfCoeffs; cache
   vs recompute bit-neutral (pure fn). 3. Bresenham `+= 0.0` drop bit-neutral (coefficients
   strictly positive finite — g>0, k∈[0.005,10], a1∈(0,1], a2>0, a3>0). 4. RAW_TAP ≡
   Plus(_, Times(bp, const)) incl. safeOut placement; IEEE +/* bitwise commutative incl. signed
   zeros. 5. D1 folds: same op, same operands (all controlRateValueOrNull overrides pure & mirror
   generate); Div keeps true division. 6. Branch-free coefficient mixing rejected (sign-of-zero,
   0·Inf). 7. Parity per-platform (JVM≡JVM, JS≡JS); specs run both via commonTest. 8. Sub-block
   renders (`offset != 0`, partial length) covered in every parity spec. 9. **NaN PAYLOAD bits
   are OUTSIDE the contract** (discovered live in D2a round 4: the new pathological-input row
   reddened on 0x7FF8 propagated vs 0xFFF8 fresh-x86-invalid-op NaN — the JVM guarantees only
   "a NaN", and JIT operand commutation of +/* may select either operand's NaN). Every parity
   assertion in this workstream compares NaN-as-NaN, raw bits otherwise; safe because a NaN SVF
   state never returns to finite, so no finite divergence can hide behind the exception. D4c's
   corpus parity specs MUST use the same comparator. 10. **CROSS-TREE parity rows must be
   RNG-inert** (discovered in D3 round 1): two independently built runtime trees draw from the
   ONE global Random in sequence, so any value-bearing draw (active drift `analog > 0`, noise
   sources, unison jitter) diverges the trees regardless of correctness — this binds
   chained-vs-fused rows AND D4c's `toExciter() vs optimize().toExciter()` corpus (keep corpora
   at analog=0, no noise; drift-CONSTRUCTION draws are fine when analog=0 — inert values).
   ORDER-dependent properties (upstream renders before the first section param resolves — the
   D3 HIGH: EqIgnitor originally resolved params first, reordering drift/unison/noise draws vs
   the chained path) are pinned WHITE-BOX with probe ignitors instead.

## Risks
- EqSectionType enum-ordinal wire encoding: append-only is the ONLY guard — the schema hash does
  NOT cover enum entries (KSP early-return). KDoc warning + spec exercises the last entry.
- Decoder has no missing-field defaults: benign (same-build wire, schema-hash gated) — document.
- RAW_TAP: reads-input + contributes-at-list-position is the documented DEFINITION, not an
  assumed invariant — it makes both loop shapes equivalent for ANY wire-supplied section order.
- collectParams over fused trees: name-set + first-occurrence assertions (dup counts change by
  design when sharing collapses).
- D2a test oracle: verify SvfLPF linear branch ≡ SvfIgnitor before using it; else Ignitor-form.
- MasterFx.eq/Katalyst adoption blocked on the ramp API (deliberate, documented in EqCore KDoc).

## Verification
- Per deliverable: `console/with-build-lock.sh ./gradlew :audio_be:jvmTest` (+ :audio_bridge and
  JS test tasks where the spec lives), /review-loop until a clean round, new tests
  mutation-checked.
- Coordinator owns ALL Gradle runs. **The FE watcher must be stopped for EVERY test round, from
  D0 on** — corrected reasoning (round 3): the documented hazard is ANY two concurrent Gradle
  invocations corrupting the sprudel KSP cache (the flock cannot serialize against an external
  watcher), which exposes JVM-only rounds exactly as much as JS rounds; and the dossier requires
  JS runs from D1a anyway (commonTest bit-parity specs). Ask the maintainer before each round;
  recovery: `:sprudel:clean`. Never compile FE changes.
- Measured proof: GTR-FX ladder deltas after D1a/D4c/D4d; EffectBenchmark loop-shape decision in
  D2a; live suite JVM → JS; final by-ear + Fairphone check by maintainer.

## Review provenance
Plan review-looped 2026-08-19: 5 rounds × 2 fresh reviewers (coding + DSP), 78 findings, all
fixed, 0 rejected, 0 parked. Trend 27→17→17→11→6; round-5 findings were plan-text consistency
only. Safety valve reached without a formally clean round — residual risk is text-level, and
every deliverable gets its own implementation review-loop against real code.

## Ordering
D0 (baseline + checkpoint) → D1a → D1b → D2a (incl. loop-shape measurement) → D2b → D2c →
D3a+D3b (one review round — exhaustive-when forces it) → **D3c (seeded per-voice RNG —
maintainer-added 2026-08-20)** → D4a → D4b (kill-switch, BEFORE fusion goes live) → D4c (R1 +
seam) → D4d (R2) → (D5 ∥ D6) → D8 → D7 (optional, exactly-solvable migration + taste pass) →
**D9 (sprudel voice-filter baking → EqCore — maintainer-added 2026-08-20, deliberately LAST:
v1 = static/linear/Svf tier only; ramp API phase unblocks modulated filters + MasterFx/Katalyst).**
Each step: green tests + /review-loop clean round before the next.
