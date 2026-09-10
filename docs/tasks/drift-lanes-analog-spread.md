# DriftLanes: one drift-lane container, `analogSpread` on the whole super family

> Status: **READY TO BUILD (2026-09-10).** Decisions are settled (section 2); no design question is
> open. Written for an implementing agent that has not seen the sine partial banks work; every
> anchor below is a file and a line number as of `main` at `edcb5445` (2026-09-10). Re-grep before
> editing, the numbers drift.
> Priority: engine work-stream, "sound first". Estimated one day including the review loop.
> Companion records: `docs/plans/sine-partial-banks.md` (where `analogSpread` was born, section 5.2
> step 4 has the drift math), `docs/tasks-archive/2026-09/20260907-sine-partial-banks.md`.

## 1. Why

`Osc.sine(x => x.harmonics(7).analogSpread(s))` shipped on 2026-09-07 with a knob the rest of the
engine does not have: `analogSpread`, 0 to 1, blends every partial's analog drift between one walk
shared by the whole oscillator (0: the spectrum stays exactly harmonic, the oscillator wobbles as
one physical VCO) and one independent walk per partial (1: the beating of separately drifting
oscillators). The super oscillators (`supersaw`, `supersine`, `supersquare`, `supertri`,
`superramp`, `superpluck`) have per-voice drift too, and are permanently at the independent end.

The maintainer wants the same knob, same word, same meaning, on the whole super family, and wants
the drift-lane machinery to exist ONCE, as a component every multi-voice site plugs in, instead of
the hand-rolled copy the partial bank carries today.

There is also dead code to remove. `PolyAnalogDrift` (`audio_be/src/commonMain/kotlin/ignitor/PolyAnalogDrift.kt`)
holds N lanes with a sample-major `advanceAll()`. No production code uses it; every hot loop in
the engine is voice-major (one tight loop per voice with the phase in a local), so "advance all
lanes, then read lane n" never fitted anywhere. Its only user is one case in `AnalogDriftSpec`.

## 2. Decisions (maintainer, 2026-09-10; do not reopen)

1. **Remove `PolyAnalogDrift`.** Replace it with `DriftLanes` (section 3). Move its KDoc rationale
   (independent lanes are what keeps a unison stack organic; lockstep kills the analog feel) into
   the new class as the reason the default is 1.
2. **`analogSpread` on every super node**, same name, same 0 to 1 scale, same meaning as on
   `Osc.sine`: 0 = the voices move together, 1 = independent (default, keeps every existing patch
   as it sounds today). The depth stays the node's `analog`. Constant-power weights, so the depth
   is `analog` at every setting.
3. **Bit-exactness of the default is not required.** It comes free anyway if the shared lane is
   created lazily (section 3), and it keeps the phase-pool specs green without touching them, so
   do it, but a review finding that spread 1 is not bit-identical to the old render is a MINOR,
   not a blocker.
4. **Superpluck is in.** Its strings have per-string drift (`superKarplusStrong`), so "same word,
   same meaning across the family" includes it. Mono oscillators are out: one lane has nothing to
   spread.
5. **Performance is a first-class requirement.** Measured 2026-09-10 (single-render harness,
   one voice, 128-frame blocks, 44.1 kHz): a drift lane costs 3.7 ns per voice-sample in the
   stack loop and 6.3 ns in the bank loop; a `sin()` partial costs about 35 ns, a saw voice about
   4.6 ns. `DriftLanes` must not add to the spread-1 path, and spread 0 must be cheaper than today
   (own lanes skipped). Numbers: `docs/benchmarks/` run of 2026-09-10 (section 8).

## 3. The component: `DriftLanes`

New file `audio_be/src/commonMain/kotlin/ignitor/DriftLanes.kt`, package
`io.peekandpoke.klang.audio_be.ignitor`. Wraps `AnalogDrift` (`AnalogDrift.kt`, unchanged: one
two-timescale walk per lane, `nextMultiplier()` per sample, xorshift32 inside).

```kotlin
/** N own drift lanes plus one shared lane, blended per sample by `analogSpread`. See KDoc in the file. */
class DriftLanes(
    private val analog: Double,      // depth, cents-ish, the node's `analog` read once at the first block
    private val sampleRate: Int,
    private val rng: Random,         // the VOICE rng (ctx.random): lanes draw their seeds from it in a fixed order
) {
    val active: Boolean = analog > 0.0

    /** Grow to [count] own lanes; new lanes are created from [rng] in index order. Never shrinks. */
    fun ensureLanes(count: Int)

    /**
     * Once per block, BEFORE any voice loop. Coerces [spread] to 0..1 (NaN reads as 1, the door
     * default), derives the constant-power weights `wShared = sqrt(1 - s)`, `wOwn = sqrt(s)`,
     * and if `s < 1` advances the SHARED lane once per sample for `off until end` into a
     * growth-only scratch array of deviations (`multiplier - 1`). The shared lane is created on
     * the first call that needs it, never earlier: at spread 1 no shared draw ever happens.
     */
    fun prepareBlock(spread: Double, off: Int, end: Int)

    /**
     * The multiplier for own lane [lane] at sample [i] of the current block:
     * `1 + wShared * sharedDev[i] + wOwn * (own[lane].nextMultiplier() - 1)`.
     * Endpoint fast paths: at s == 1 exactly `own[lane].nextMultiplier()` (today's cost, today's
     * value, bit for bit); at s == 0 exactly `1 + sharedDev[i]` and the own lane is NOT advanced.
     * Inline; no allocation, no branch on anything but the two per-block booleans.
     */
    fun step(lane: Int, i: Int): Double
}
```

Rules the implementation must keep (all proven on the bank, all mutation-checked there):

- **Deviations mix additively in ratio-minus-one space**, not by multiplying multipliers. Second
  order difference at cent scale; the bank's spec reference model uses this form.
- **Growth-only, no per-block allocation.** Lanes and the scratch grow on demand and never shrink.
  `ensureLanes` on a count change creates only the NEW indices from `rng`; surviving lanes keep
  their walk (the stacks guarantee this already: "surviving voices keep their AnalogDrift", ledger
  O4 in `Ignitors.kt:1160`).
- **Fixed RNG draw order**: own lanes in index order as they are created, the shared lane at the
  first `prepareBlock` with spread below 1. Document the order in the KDoc; a seeded voice must
  render reproducibly (`SeededVoiceRngSpec` is the contract).
- **`active == false` means every method is a no-op** and `step` returns 1.0; callers may skip
  calling it entirely (the stacks keep `drift == null` fast paths today; keep that shape: a
  `DriftLanes?` that is null when `analog == 0`).
- Kotlin/JS is a target: no `Long`, no boxing, plain `DoubleArray`, `Array<AnalogDrift>`.

## 4. Adopters, with anchors

All in `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt` unless stated.

### 4.1 The partial bank (already has the behaviour, loses its private copy)

`PartialBankIgnitor`, lines 240 to 460. Today it hand-rolls exactly section 3: `shared`,
`fundLane`, `Bank.lanes`, `sharedDevBuf`, weights `wShared`/`wOwn` (lines 280 to 300, 308 to 313,
366 to 367, 418 to 450), and the blend inside `renderPartial` (lines 320 to 350). Replace all of it
with one `DriftLanes` (lane 0 = fundamental, then the three banks' partials at fixed offsets, or
one `DriftLanes` per bank plus one for the fundamental sharing the same shared lane; pick the
simpler, the first is fine because lanes never shrink). `SinePartialBankSpec` (26 cases, the
"analogSpread" cases have a test-side reference model, `driftReference`, lines 380 to 440) must
stay green unchanged: it pins the exact lane creation order (shared, fundamental, then partials by
index) and the blend at 0, 0.25 and 1 within 1e-9. If the refactor changes the draw order, the
reference model in the spec changes with it, and the reviewer has to agree that the new order is
documented.

### 4.2 The unison stacks (the main deliverable)

`DetunedStackIgnitor`, abstract, line 1085; shapes `SawStackIgnitor` 1417, `PulseStackIgnitor`
1438 (square, and via kind/polarity the tri and ramp), `SineStackIgnitor` 1460. Today:

- per-voice lanes are created at a voice-count change, line 1171:
  `voiceStates[n].drift = if (analogAmt > 0.0) AnalogDrift(...) else null`, inside the block that
  also draws phases and gain jitter (read the DECIDED comment at 1160 first: mid-note count changes
  keep surviving voices' drift, only new indices draw).
- `renderVoice(buffer, off, end, vs, first, pm)` (abstract at 1123) is implemented at 1378 (the
  wave shapes) and 1477 (sine); both advance the lane per sample as `inc *= drift.nextMultiplier()`
  (lines 1403 and 1497), reading `vs.drift`.

Change: one `DriftLanes?` on the stack instead of `WaveVoiceState.drift`; `ensureLanes(v)` where
line 1171 creates lanes today (new indices only, same place in the draw order); `prepareBlock(spread,
off, end)` once per block in `generate` before the voice loop; in both `renderVoice`
implementations `inc *= drift.step(n, i)` where `n` is the voice index (pass it in, the signature
gains an `Int`). Keep the `drift == null` fast path. The `analogSpread` knob arrives as an
`Ignitor` param read once per block with `readParam`, like `voices` and `spread`.

### 4.3 Superpluck strings

`superKarplusStrong`, the per-string loop at line 1866 to 1880: `s.drift ?: AnalogDrift(...)` per
`StringState`, hoisted analog read at 1871. Same change: one `DriftLanes` on the ignitor,
`ensureLanes(v)`, `prepareBlock` before the string loop, `step(n, i)` where the string advances its
delay-line read (find `drift.nextMultiplier` in that class; if the string reads the multiplier once
per block rather than per sample, keep that rate and call `step` at that rate, documenting it).

### 4.4 Not adopters (leave alone)

`SineIgnitor` (147), `WaveIgnitor` mono (514), `karplusStrong` mono (740, 1696), `SampleIgnitor`,
the filter drift in `VoiceFactory.kt:458`. One lane each; `AnalogDrift` stays their primitive.

## 5. Wire and runtime

`audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt`: add `val analogSpread: IgnitorDsl = Constant(1.0)`
to `SuperSaw` (485), `SuperSine` (535), `SuperSquare` (587), `SuperTri` (637), `SuperRamp` (688),
`SuperPluck` (764), next to `analog`, KDoc one line each ("Drift lane blend, 0 = one shared analog
walk for every voice, 1 = independent walks"), `collectParams` extended. A `Constant` default on
the data class, NOT a `Slots` entry (the sine decided the same; nothing on the pattern side asks
for it).

`audio_bridge/src/commonMain/kotlin/IgnitorDslWalk.kt`: the children lists (101 to 106) and the
`copy(...)` rebuilds (251 to 265) gain the field, in constructor order. `IgnitorDslWalkSpec`
(`audio_bridge/src/commonTest/kotlin/`) declares each node's child count by hand: update the six
entries or the walk spec fails, exactly as it did for `Sine`.

`audio_bridge/src/jsTest/kotlin/IgnitorDslWireCodecSpec.kt`: the six super cases set every scalar
field non-default; add `analogSpread = Constant(0.25)` to each so a dropped field shows. The codec
itself is generated by `audio-wire-codec-ksp` over the sealed hierarchy; nothing by hand.

`audio_be/src/commonMain/kotlin/ignitor/IgnitorDslRuntime.kt`, the super dispatch arms (search
`is IgnitorDsl.SuperSine ->`, about line 430, and its siblings): pass `analogSpread.noMod()` into
the `Ignitors.superX(...)` factories, which gain an `analogSpread: Ignitor = ConstantIgnitor(1.0)`
parameter each. `WarmupVocabulary.kt` needs no change (the defaults exercise the spread-1 path,
which is the hot one); optionally set `analogSpread = Constant(0.5)` on one super there so the
shared path gets JIT-warmed too.

`SuperOscDefaultsSyncSpec` is untouched: the new field has a `Constant` default, not a
`SUPER*_` engine constant.

## 6. Doors

`klangscript-libs/src/commonMain/kotlin/stdlib/IgnitorBuilders.kt`: one knob on each of
`OscSuperSawBuilder` (267), `OscSuperSineBuilder` (348), `OscSuperSquareBuilder` (429),
`OscSuperTriBuilder` (510), `OscSuperRampBuilder` (591), `OscSuperPluckBuilder` (701):

```kotlin
/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Osc.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperSawBuilder.analogSpread(amount: IgnitorDslLike): OscSuperSawBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))
```

Same text on all six (copy, do not paraphrase six ways). The KDoc of each builder's data class
lists its knobs ("Knobs: `voices`, `spread`, `analog`, ..."): add `analogSpread`. The door KDocs in
`KlangScriptOsc.kt` list the knobs per door in the `@param configure` line: add it there too. The
same builders are the Kotlin door; no extra Kotlin surface is needed.

Door-parity specs, `klangscript-libs/src/commonTest/kotlin/stdlib/KlangScriptSuper{Saw,Sine,Square,Tri,Ramp}Spec.kt`:
one case each, `"analogSpread(0)"`, in the shape of the existing `"analog(5.0)"` case, plus the
knob added to the "every knob in one lambda" case. Superpluck has no door spec of its own today:
add its two cases to `StdLibOscTest` or create `KlangScriptSuperPluckSpec` in the same pattern
(preferred, the family should be symmetric). Light-tier mutation: delete the `analogSpread =`
assignment in ONE builder and confirm that door's spec goes red (the deletion mutation the review
standard asks for when a spec claims a specific door).

## 7. Retire `PolyAnalogDrift`

Delete `PolyAnalogDrift.kt`. `AnalogDriftSpec.kt` case `"attack is in tune - slow layer seeded at
centre (poly / unison)"` (line 57) uses it: rewrite the case against `DriftLanes` (every own lane
AND the shared lane attack in tune: the first multiplier of each lane is within the fast layer's
budget of 1.0, the slow layer seeds at centre). Grep the repo for `PolyAnalogDrift` afterwards;
on 2026-09-10 the only hits are the class file and that spec case.

## 8. Specs and mutation checks (mandatory tier, `/review-loop` Standard 2)

**`DriftLanesSpec`** (new, `audio_be/src/commonTest/kotlin/ignitor/`), unit level, the same
test-side reference model the bank uses (`SinePartialBankSpec.driftReference`, lines 380 to 440:
build `AnalogDrift`s from `Random(seed)` in the documented order and replay the formula):

- spread 1: `step(n, i)` equals `AnalogDrift.nextMultiplier()` of lane n bit for bit, and no shared
  lane is ever created (count the rng draws: a `Random(seed)` consumed by the lanes alone).
- spread 0: `step(n, i)` is identical for every n and equals `1 + sharedDev[i]`; own lanes are not
  advanced (their next value after the block equals their value before).
- spread 0.25 (unequal weights, never 0.5: at 0.5 a swapped-weights mutation is invisible) matches
  the reference model within 1e-12.
- NaN spread reads as 1; negative and above-1 coerce.
- `ensureLanes` growth keeps existing lanes' state (a lane's next value is unchanged by a grow).
- `active == false`: `step` returns exactly 1.0 and consumes no rng draw.

**Stack integration, `SuperStackDriftSpreadSpec`** (new), behavioural, rng-agnostic:

- `superSine(voices = 2, spread = 0.0 unison detune, analog = 20, analogSpread = 0)` rendered for
  5 seconds: the block-wise peak amplitude is constant within 1 percent (two voices at the SAME
  drifting frequency sum to one sine, no beating). At `analogSpread = 1` the peak varies by more
  than 10 percent (they drift apart and beat). Mutating the shared term away makes the 0 case
  beat: red.
- `analogSpread = 1` renders bit-identically to the same stack built before this change. Pin it
  the cheap way: render `superSaw(voices = 7, analog = 3)` with `Random(seed)` into a fixture
  BEFORE the refactor (a few hundred samples as a Kotlin `doubleArrayOf` in the spec, generated by
  a throwaway print, the print removed), assert bit equality after. If decision 3 ever makes this
  impossible, the reviewer decides, not the implementer.
- Superpluck: the same two-string coherence assertion at spread 0.

**Existing suites** that must stay green as-is: `SinePartialBankSpec`, `PhasePoolDslSeamSpec`,
`PhasePoolSpec`, `SuperStackTransitionSpec`, `SeededVoiceRngSpec`, `IgnitorDslWalkSpec`, the
codec spec (`:audio_bridge:jsBrowserTest`), the five super door specs. Run
`:audio_bridge:jvmTest :audio_be:jvmTest :klangscript-libs:jvmTest :audio_bridge:jsBrowserTest`
before every commit. `:audio_be` has no working JS test task (four pre-existing test files
implement a function interface, forbidden on Kotlin/JS); the JS engine is exercised by
`:audio_benchmark:jsNodeProductionRun`, run it once at the end and make sure it does not throw.

**Mutation list**, one at a time, each `mutate -> build -> restore` inside ONE
`console/with-build-lock.sh` hold, expected red test named per mutation, `No tests found` treated
as a script error (the bank's runner is a usable template, it lived in the session scratchpad;
write your own, it is twenty lines): drop the shared term; swap the weights; drop the own term;
create the shared lane eagerly (the draw-count case goes red); `ensureLanes` re-creates surviving
lanes; `step` at spread 0 still advances own lanes; NaN spread reads as 0 instead of 1; the stack
passes `n` wrong (always 0) to `step`; the runtime drops `analogSpread` from one factory call.

## 9. Benchmark

`audio_benchmark/src/commonMain/kotlin/IgnitorBenchmark.kt` already carries (uncommitted in the
tree on 2026-09-10, commit them with this task) three rows: `sine+analog`, `supersaw_8v+analog`,
`sine-harmonics7+analog`. Add `supersaw_8v+analog+spread0` (needs an inline DSL `sounds` entry with
`analogSpread = Constant(0.0)`; the `Case.sounds` map exists for that) and `sine-harmonics7+analog+spread0`.
Run `:audio_benchmark:jvmRun` under the lock, save the markdown section as
`docs/benchmarks/2026-09-10_drift-lanes_jvm.md` (the wrapper `console/run-dsp-benchmarks.sh` also
runs Node and needs the JS watcher off; the JVM-only sed from that script is enough). Acceptance:
spread-1 rows within noise of the pre-change rows (the 2026-09-10 numbers above), spread-0 rows
cheaper than spread-1.

The harness renders each block once since `39119aef`; do not reintroduce `scheduler.process()`
next to `renderBlock()`.

## 10. Docs and memory

- `.claude/skills/klang-music-writing/ref/ignitor-reference.md`: the "Builder knobs of the super
  oscillators" paragraph gains `analogSpread(x)` with the one-line meaning; the sine partial banks
  paragraph gets "the same knob the super oscillators carry".
- `audio/MEMORY.md`: replace the `PolyAnalogDrift` mention; one status line for `DriftLanes` and
  the family-wide knob, pointing at this task (moved to `docs/tasks-archive/2026-09/` when done).
- `klangscript/MEMORY.md`: one line under Recent Work.
- `docs/plans/sine-partial-banks.md` section 5.2 step 4: one sentence that the drift lanes moved
  into `DriftLanes` and the supers share the knob.

## 11. Process

- Read `.claude/BUILD-LOCK.md` as its own step before the first build; take the advisory record
  when it says FREE; every Gradle call through `console/with-build-lock.sh`; release and delete
  your row when committed. One `--tests` FQCN per run, unquoted.
- Order: 3 (component + `DriftLanesSpec`, mutation-checked) -> 4.1 (bank refactor, its spec
  unchanged and green) -> 4.2 + 5 + 6 (stacks, wire, doors, door specs) -> 4.3 (superpluck) -> 7
  (retire) -> 8 stack specs and mutations -> 9 benchmark -> 10 docs. Commit after each reviewed
  step on a branch off `main` (suggested name `drift-lanes`).
- Review loop per `/review-loop`: coding reviewer plus audio reviewer, round 1 blind, round 2
  two-phase; the audio reviewer should be pointed at the RNG draw order, the endpoint fast paths,
  and the constant-power claim; give both reviewers `git diff HEAD` plus the untracked files, not
  a stale snapshot.
- House rules that bit last time: no em-dashes in any text; braces always; blank line around
  `if`; NaN-guard comment where a guard exists; comment findings only when text is factually
  wrong; scaffolding (probe prints, fixture generators) removed in the same change.

## What we built

Named and scoped with the maintainer on 2026-09-10 from the question "can the super oscillators
reuse `analogSpread`?" and the finding that the one reusable drift container in the tree was the
one nothing used.
