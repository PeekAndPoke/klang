# Built-in instruments, and the end of the Pipeline DSL (phase 3)

Phase 3 of `../plans/signal-flow-redesign.md` (its section 5 states the goal and the rules). This
file is the task record: what the spike of 2026-09-20 found, what the maintainer has to decide, and
the step list. Written for a reader who has not read the code.

## 1. The goal in one paragraph

`sound("saw")` is not an oscillator, it is a subtractive synth voice with a saw in it. Phase 3 makes
that explicit: the built-in sounds become INSTRUMENTS written in the Ignitor DSL, registered once,
whose stages are today's voice pipeline in today's order, every stage gated on its slot. `.classic()`
is a plain function over the node type, shipped on both doors. A pattern fills slots, never adds
structure. `PipelineDsl`, the filter pipeline builder, `Cmd.RegisterPipeline`, `PipelineRegistry` and
the `pedal` preset retire; every voice door becomes an `oscp` alias; `VoiceData` is cut to the plan's
section 4.

## 2. The spike's headline (2026-09-20, all numbers MEASURED on the JVM)

- **The gate pays for itself several times over.** With it, a plain `sound("saw")` is 38 % CHEAPER
  than today (720 against 1161 ns per block per voice, 8 sustained voices through the real
  renderer); without it, 2.5 times more expensive (2913). The gate is worth about 2193 ns per block
  per voice, three times a whole voice today. There is no version of phase 3 that ships without it.
  The win is not the gate itself: `AdsrIgnitor` is cheaper than the strip's `EnvelopeRenderer`
  (115 against 497 ns per block per voice), mostly because the strip runs its de-click one-pole
  unconditionally. **If `classic()` has to switch that de-click on for identity, the win shrinks by
  an amount the spike did not measure.**
- **Today's number phase 3 must not regress: 9290 ns per block for 8 sustained saws.**
- **Four of seven stages are already bit-identical** between the strip and the tree: all four filters
  (the analog saturation branch and the `passes` cascade included), coarse, tremolo at neutral, and
  the ADSR at integral frame counts.
- **Three are not**, and each is a taste decision, not a bug: crush (a `floor` quantizer against a
  `round` one, up to 0.125 apart at amount 4), distort (`ShapeIgnitor` applies `softCap`, the strip
  deliberately does not, up to 0.121 apart), and the filter ENVELOPE (a 32-sample coefficient ramp
  then hold, against whole-block interpolation).
- **Seven capabilities of the strip have no expression in the Ignitor DSL at all** (section 4).
- All of it is JVM. The shipping backend is Kotlin/JS in an AudioWorklet, and the codebase's own
  notes say the two diverge on exactly this kind of loop. Re-run the voice probe as `IgnitorBenchmark`
  rows on JS before the step that lands `classic()`.

## 3. Decisions the maintainer owes before the ear-checkpoint steps

The identity-provable steps (1, 2, 3, 5 below) do not wait for these. Steps 4, 6, 7 and 10 do; D7 gates step 6.

- **D1, the crush.** The strip's quantizer floors (an asymmetric quantizer with a DC bias, and its
  KDoc claims the asymmetry IS the classic audible character); the ignitor's rounds (symmetric, no
  DC bias, its KDoc defends that too). Up to 0.125 apart at amount 4. Port `floor` into the ignitor
  (identity for the songs, changes every authored `.crush()` in an ignitor), keep `round` (changes
  the songs that crush), or make it a shape knob.
- **D2, the distort.** `ShapeIgnitor` applies `softCap` per sample, `DistortionRenderer` does not,
  because the strip has its own downstream bounding stages. Drop the cap on the classic tail
  (identity, but a heavy-drive branch can then dominate a mix, which is what the cap exists for), or
  keep it and accept the change on every distorted voice.
- **D3, the filter envelope. RE-SCOPED 2026-09-20 in the step 3a review: it is two decisions, and
  the one the spike missed is the bigger.**
  - **The LAW.** The node's envelope segments are LINEAR (`envelopeLevelAtPosition` has no curve in
    it at all); the strip's take `AdsrCurve.Default`, which is Exponential with K = 3, because
    `VoiceFactory` omits the three curve arguments when it builds the `Voice.Envelope`. Measured at
    `env = 24`: up to 806 cents apart at the same instant, RMS 256 cents on a 10 ms / 100 ms pluck
    and 512 on a 2 s pad. The reviewer recommends giving the NODE the house Exponential curve, since
    `AdsrCurve.Default` is the default on every other stage in the engine and a filter envelope
    should not be the one envelope that is secretly linear. If linear is wanted for a filter sweep
    (defensible: with a pitch-linear sweep it is a constant-rate glide in semitones), then the STRIP
    moves, and it is recorded as a deliberate curve choice rather than an accident of an omitted
    constructor argument.
  - **The SAMPLING.** The strip computes the envelope once per block and lets `setCutoff` ramp the
    coefficients over 32 samples and then hold; the node computes it at block start and end and
    interpolates across the whole block. Measured as a residual against an ideal per-sample law, the
    node is 3 to 35 dB closer on every patch tried (a pluck at q 0.707: -56.7 dB against -41.9; a pad:
    -85.8 against -50.3). In cutoff terms the strip lags by up to 635 cents and stairs at the 375 Hz
    block rate, which is the stair-step the node's own KDoc says it exists to avoid. Neither is a
    stability risk (worst pole-radius excess over the endpoints: 1.1e-16, because both endpoints
    share one q). The reviewer recommends KEEPING the node's interpolation and porting it to the
    strip, and notes the ramp exists to mask a block-boundary JUMP that interpolation removes
    entirely. The node's one weakness is a segment CORNER inside a block (at a 0.3 ms attack it
    chords over the peak); the fix, if wanted, is to split the block at segment boundaries, not to
    shorten the ramp.
  - The same sampling question exists for the DRIFT and is settled by measurement: inaudible at
    every shipped setting (-78 to -101 dB RMS), because both branches HOLD the drift across the
    block, which is the strip's law exactly, and only the block boundary differs.
  - Also in this decision's record, from the same review: the node truncates stage frame counts to
    Int where the strip keeps them fractional (220 against 220.5 at `attackSec = 0.005`), the same
    class as the ADSR mismatch the spike recorded.
  - Either way it is an ear checkpoint on Der Schmetterling and Stranger Things, and it changes every
    song that already uses `lpf(env = ...)`, by up to 635 cents at the sweep's steepest.
- **D4, the `pedal` pipeline.** `DialogueWithTheStars` calls `.pipeline("pedal")`, which puts the VCA
  FIRST. It retires with `PipelineDsl` and has no `classic()` spelling. Ship a second named tail
  (a second word for one concept, which the rules register argues against), rewrite the song, or let
  it change.
- **D6, the door's shape (raised in the step 3a review).** The script door's `lowpass` is now eleven
  parameters, nine of them defaulted, in the same file as an `eq` door that takes a `configure`
  lambda, while `/dsl-design` section 2 says "anything with a default is a knob and lives on the
  builder". The flat shape mirrors sprudel's slot-per-knob `lpf` and the door already had four
  defaulted knobs, so this is growth rather than a new violation, but 3b adds the same volume to
  distort and tremolo and 3c to the envelope (crush and coarse gain nothing since their `oversample`
  moved to `oversampling-regions.md`, 2026-09-23). Decide now or they follow. Related, same door: the two
  surfaces disagree on the FOURTH positional argument (`lowpass(freq, q, passes, analog, env, ...)`
  against `lpf(freq, q, passes, env, attack, ...)`), so `lowpass(800, 1.2, 2, 24)` is a very dirty
  filter and `lpf(800, 1.2, 2, 24)` is two octaves of sweep. Both KDocs say to write them named;
  nothing guards it.
- **D5, the frozen pieces.** `FrozenPieces` is captured verbatim and immutable except for door
  renames. Appending `.classic()` is not a rename, and without it those pieces lose their outer
  envelope. The maintainer's word is needed.
- **D7, distort's oversampling across step 6 (raised 2026-09-23).** The maintainer moved oversampling
  out of phase 3 into its own task (`oversampling-regions.md`: a region in lambda form, the factor read
  once when the note starts, after the KatalystDsl work stream). For crush and coarse that is free: every
  shipped use pins `oversample = 1`. For distort it is not. The sprudel `distort(amount, shape, N)` is
  oversampled by the strip in Tetris (2x), TetrisRemix (2x), IrishLamentTechno (five voices, 2x and 4x)
  and the frozen songs (2x and 4x). The Ignitor's `Shape` node has an `oversample` field, but it is a
  plain Int, so `classic()` cannot fill it from a per-note slot. So from step 6, when the built-ins leave
  the strip, those voices lose their oversampling: more aliasing, audible at step 6's checkpoint, and the
  step is no longer identity. (The Ignitor-level uses in Sandsturm, DialogueWithTheStars and
  ATruthWorthLyingFor use the field directly and are untouched by phase 3.) Three ways:
  - **(a) Keep one small piece in phase 3**: make `Shape.oversample` a knob read at voice build (step 2's
    `buildTimeKnobValue`), so `classic()` fills it from `distort.oversample`. Cheap, keeps step 6
    identity, and is replaced by the region when the new task lands.
  - **(b) Land the Ignitor half of the regions before step 6.** This changes the agreed order.
  - **(c) Accept the change** in those songs at step 6, and restore it when regions land.
  Recommendation: (a), because it is the smallest change that keeps step 6 provable, and it is
  explicitly temporary. It is also exactly the kind of oversampling sub-task the maintainer asked to
  leave out, so it is the maintainer's call.

Minor, decidable inside their step: the ADSR's Int-against-Double frame counts and its sustain clamp
(up to 7.1e-3 on the gain, about 0.06 dB, on a fractional frame count); whether `classic()`'s
de-click may share the name `declickSeconds` with a slot whose default differs.

## 4. What is missing from the Ignitor DSL (the spike's map)

| Missing | Kind | Who needs it |
|---|---|---|
| ~~The filter ENVELOPE on the four filter nodes~~ | DONE in 3a (2026-09-20) | `env` plus four stage knobs, both doors, the wire, the defaults in `audio_bridge/constants/FilterEnvelopeDefaults.kt` |
| ~~`AnalogDrift` per filter, and the per-voice cutoff tolerance~~ | DONE in 3a | the structural `humanize` flag, because both halves are per-voice DRAWS and no knob can carry a draw; the draw order lives in `audio_be/.../ignitor/FilterHumanization.kt` |
| ~~`oversample` on crush and coarse~~ | MOVED 2026-09-23 to `oversampling-regions.md` | no shipped song: every crush and coarse use pins `oversample = 1` |
| `skew`, `phase`, `shape` on tremolo | 2 knobs + 1 index slot | the tremolo door |
| `shape` on distort | 1 index slot | Tetris, the frozen corpus. Its `oversample` MOVED 2026-09-23 to `oversampling-regions.md`; what that costs is D7 |
| `adsrOn`/`adsrOff` as a gate slot, and the three curves as slots | 1 gate + 3 index slots | every `.adsrOff()` instrument |
| A sample node kind (or a hand-built head with a `classic()` tail) | node kind | `sound("bd")` and Der Schmetterling's drums |

A string knob becomes a numeric INDEX slot, the way `body.material` already does.

**The order `classic()` must have** is today's strip order with the canonical filter sub-order from
`SprudelVoiceData.toVoiceData`: crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo,
adsr. The plan's sketch in section 5 lists the lowpass first, which is wrong and would change every
song with both a highpass and a lowpass at `analog > 0` (at analog 0 the filters commute).

## 5. Three things the plan says that the spike corrected

- **The pitch fields STAY on the wire.** The plan's section 4 lists a minimum with no pitch fields
  while section 5 says the pitch pipeline is untouched; both cannot hold. The spike verified the
  separation is real (`buildPitchPipeline` only ever writes `BlockContext.freqModBuffer`, the
  ignitor reads it as `phaseMod`, and the tree's own pitch mods COMPOSE with it on every Der
  Schmetterling voice today). Moving the pitch pipeline into the tree is its own later item.
- **The build cache needs no key change.** `IgnitorBuildCache` is created fresh per build, and a
  build is per note-on, so the gate's decision is constant for the cache's lifetime. What the plan
  feared would be a CROSS-VOICE cache, which does not exist. Drop it from the scope and add one spec
  pinning the invariant, so a future cross-voice cache cannot reintroduce the hazard silently.
- **Two off values in the plan are wrong.** Crush's off value is `< 1.0`, not 0 (the renderer itself
  bypasses below 2 levels). The ENVELOPE is inverted: today the VCA runs on EVERY voice with
  `AdsrDef.defaultSynth` when the pattern sets nothing, so `classic()`'s ADSR is built BY DEFAULT and
  gated off by an explicit `adsrOff` slot. Also add `mul(pregain)` at exactly 1.0 and `onepole` at or
  below 0 to the table.

## 5b. The off-value table (the one home; built in step 2, 2026-09-20)

A stage is not built when its gating knob is a `Param` or `Constant` LEAF and resolves either to a
non-finite value (the unset sentinel, `SLOT_UNSET`, is `Double.NaN`) or to the stage's off value
below. A knob that can MOVE within a note is never gated. This is the one home of these values;
code and memory point here and do not restate them.

| stage | off value | why, and what is not a fold |
|---|---|---|
| coarse | unset, or `<= 1.0` | at or below 0 and at a non-finite amount the render already takes a bit-exact bypass, so there the gate folds a bypass. In `(0, 1]` the engaged loop takes every sample but latches it through `nanGuard()`, so a NON-FINITE UPSTREAM SAMPLE used to come out as 0.0 and now passes through |
| crush | unset, or `< 1.0` | a fold: `levels = 2^amount`, and the renderer bypasses below two levels. At exactly 1.0 the quantizer RUNS (a saw becomes a three-level staircase), so 1.0 is ON |
| distort (`IgnitorDsl.Distort`, the legacy node) | unset, or `<= 0.0` | **A BEHAVIOUR CHANGE, not a fold.** The node is `drive(amount).shape(shape)` and only the DRIVE half ever bypassed, so the tree's chosen shaper stayed on the signal at unity gain. Modelled on a 220 Hz sine through the real chain (shaper, DC blocker, `softCap`): soft -1.77 dB, tube -6.24 dB at 16.8 % THD, gentle +0.64 to +5.21 dB, zerosquare +1.55 to +16.83 dB at 37 % THD, and `rectify` removes the fundamental altogether (full wave, an octave up). It is legacy: neither authoring door builds it (both spell `distort` as `Shape(Drive(...))`), and the only production site left is `WarmupVocabulary` at 0.3. Gating it aligns that node with the `drive` row and with `Ignitor.distort(Double)`, which always short-circuited at the same value. It does NOT reopen ledger W5's gate-flip pop: W5 is a MODULATED amount crossing 0, and a modulated amount is not a leaf, so it is never gated |
| drive (`IgnitorDsl.Drive`, the row both doors reach) | unset, or `<= 0.0` | two things at once. At or below 0 a TRUE FOLD: `DriveIgnitor` already copies its input through unchanged. At a NON-FINITE amount it CLOSES A HOLE: `amt <= 0.0` is false for a NaN, so the gain was `10^(NaN * 1.2)` and every sample of the voice came out NaN with nothing between it and the orbit mix. Step 3 would have walked into it, because a `classic()` distort slot defaulting to `SLOT_UNSET` wires exactly this node. **`Shape` is not gated and cannot be**: it carries a transfer function and no amount knob, so there is nothing to read an off value from |
| tremolo | unset, or depth `<= 0.0` | a fold. The RATE is not a gating knob |
| `mul` (`Times`, and the optimizer's `Affine(x, -0.0, k, -0.0)`) | exactly `1.0`. **Unset is NOT off** | the one asymmetry, forced by three existing specs: `TimesIgnitor` already sanitises a non-finite factor to an exact zero, and the node also sits in PARAMETER positions where that zero is the point. **Consequence: a `mul` slot must default to a safe literal, never `SLOT_UNSET`, or an unwritten one silences the voice** (guarded since step 2 by a row over `pregain()`, the one door that places a `mul` slot today). The `Affine` form is required because the optimizer rewrites a bare `x.mul(k)` and every registered tree renders optimized. The fold also takes only a SIGNAL survivor (`Ignitor.isBlockConstant`, structural and fixed at construction): what it drops is the multiply's `safeOut`, which on the audio spine fires only on a sample the survivor cannot produce, but in a PARAMETER position is what keeps a non-finite coefficient in range (`q = param("res", +Inf).mul(pregain)` resolved to `SAFE_MAX` and then the q ceiling; folded it would stay `+Inf` and land on the q fallback, a different filter). One qualification, audited in review round 3: on the audio spine every
arithmetic and unary node carries its own scrub, but the KARPLUS family writes `filtered * decay`
straight into its delay line with `decay` read raw, so an authored `decay > 1` diverges and the
fractional read turns the first infinity into a NaN. That divergence is authored character, not a
defect (the Motor stays raw), but it means the rule is "any NEW spine node that can emit a
non-finite sample from finite input owes a substitution at its own read", not "none can" |
| onepole | unset, or `<= 0.0` | NOT a fold on an authored tree: `onepole(0)` was a 5 Hz lowpass (the `clampSvfCutoff` floor), which is -33 dB at 110 Hz and -52 dB at 1 kHz, so a tree that used it as an accidental mute gets 30 to 50 dB louder. It is still right: `0` means off on every other door. Note the wart: `lowpass(freq = 0)` is still a 5 Hz filter, because a NUMBER is never off for the four SVFs |
| the four SVF filters | only when the cutoff is UNSET | a lowpass at 20 kHz is not an off state, which is why the rule is "unset" and not a number. A non-finite AUTHORED cutoff used to build a 1 kHz filter (the `clampSvfCutoff` fallback) and now builds none: a behaviour change as well as a NaN fix |
| the envelope | NOT gated | inverted from the plan: the strip VCA runs on every voice today, so `classic()`'s ADSR is built BY DEFAULT and switches off only through the `adsrOn`/`adsrOff` slot of step 3. Its unset case was NOT safe, and step 2 had to make it safe: the unity-`mul` fold removed the `TimesIgnitor` scrub that used to turn a NaN into silence, so `sustainLevel` and `expK` now substitute their own defaults (`ADSR_SUSTAIN_LEVEL`, `ADSR_EXP_K`) at the ADSR's read. Not a clamp: every finite value passes through untouched. The substitution runs BEFORE the existing coercion, so the infinities move too: a `+Inf` sustain used to hold at the 1.0 rail and a `-Inf` at 0.0, and both now read as unset and take the default, which is how every other knob reads a non-finite value. A non-finite `releaseSec` also stopped reporting a NaN release TAIL, which used to swallow a SIBLING's real one. What remains step 3c's is the `adsrOff` slot itself |
| the phaser | NOT gated | the plan lists it; no per-voice phaser is in `classic()` and the stage retires with `PipelineDsl`, so the row would be dead code |

**The compound fill does not survive SLOTTING, and step 5 must answer it again (3a review, round 2).**
The filter doors adopted the rules register's compound-door fill in step 3a AT THE DOOR: any of the
five envelope knobs names the stage, and a call that names one writes every companion it left out,
`env` included. That reading happens at CALL time, on named-against-null. A slotted built-in does
not call the door per note: `classic()` will hand `Lowpass(env = Param("lpenv", SLOT_UNSET),
attackSec = Param("lpattack", SLOT_UNSET), ...)` once, and the per-note decision then happens in
`filterEnvDef`, which has no notion of "named". A pattern that writes only `lpattack` leaves `env`
unset, the depth resolves to 0, and the tree renders a STATIC filter where the same `lpf(attack =
...)` through the strip builds a 7-semitone sweep. So the fill has to be answered a second time at
the slot layer, in step 5, and the answer is a design question: what does "the stage is named" mean
when the caller is a pattern writing slots?

**A cheap shape for it, proposed in the 3a review and NOT built** (step 5 decides): ask the same
question one layer down, against the bag instead of against `null`. A knob is "written" when it is a
`Param` whose name resolves to a finite value in `oscParams`; in `filterEnvDef`, when the resolved
depth is 0, if any of the five knobs is written, take `FILTER_ENV_DEPTH_SEMITONES` instead of
returning `NONE`. It is the gate's existing move (the gate already reads the bag at build to decide
whether a stage exists), it composes with the door fill rather than replacing it (a door-filled
depth is a `Constant`, not a `Param`, so the question never arises), it costs five map lookups per
filter per note-on and nothing per block, and an instrument that declares a real default
(`env = Osc.param("lpenv", 24.0)`) is untouched. **The law to decide with it:** does "written" mean
finite-in-the-bag only, or also a slot whose AUTHORED default is a real number? Finite-in-the-bag is
what sprudel's `!= null` means and is the recommendation.

**The distort question this leaves open, for D2.** `IgnitorDsl.Distort` is legacy by its own KDoc and
no production site builds it; both doors emit `Shape(Drive(...))`. `Drive` is gated, `Shape` cannot
be (it has no amount knob). So `classic()`'s distort stage is either the legacy `Distort` node, which
gates as a unit, or `Drive` plus a `Shape` that runs at unity gain on every voice, which is D2's
divergence made unconditional. Decide it with D2.

**One consequence recorded rather than fixed.** A gated-off stage does not build its NON-gating
param subtrees, so a `perlin`, `berlin` or `crackle` knob in a gated stage's rate or q position no
longer takes its build-time draws and every later drawing source shifts. Chosen deliberately over
the alternative (refusing to gate unless every param is a leaf), because that would refuse to gate a
filter whose cutoff is unset whenever its `q` draws, and the filter would then build with a NaN
cutoff that `bilinearK` silently substitutes with 1 kHz: the step-1 defect, on the very stage the
gate exists for. Pinned by a row. A third option exists and was recorded as not taken: gate the
stage but still build its knob subtrees in declaration order and discard them, which reproduces the
stream exactly. It is not free, because a discarded subtree that also sits on the live spine is
reached again through the build cache and flips that node's memo from pure delegation to a per-block
cache plus a buffer copy, for every block of the voice. Worth revisiting in step 3, when every
filter cutoff becomes an unset-default slot.

**The gate is also the NaN guardrail.** `SLOT_UNSET` is NaN and the `Param` leaf hands back its
DEFAULT for a non-finite override, so a slot whose default IS the sentinel resolves to NaN. For a
gated stage the gate keeps it out of the DSP. For an UNGATED stage with a NaN knob there is no
second line of defence: the envelope's `sustainLevel` and `expK` are the live examples.

## 6. Two things the factory knows today that only the build can know tomorrow

- **The cull rule.** `VoiceFactory` sets `VOICE_CULL_NEVER` when the tremolo depth is above 0,
  because a square tremolo at full depth is exact silence for half a cycle and the silence culler
  would kill the voice at its first off-half. Once the tremolo is a tree node the factory cannot see
  it: the build must report "this tree gates its own output", the shape `releaseTailSec` already has.
- **The teardown fade.** `EnvelopeRenderer.renderGate` is the `adsrOff()` path: a unity gate with a
  linear fade to exact zero over the last frames, which exists because an instrument's own envelope
  sits BEFORE its amp stages. Phase 3 removes the only stage that guarantees an amplitude ramp at the
  voice's end, and Der Schmetterling's lead, three guitars and Orchestertrommel all rely on it. The
  build reports whether it built the classic ADSR node, and the voice applies the teardown fade when
  it did not. Applying it unconditionally would multiply the last 5 ms of every built-in and break
  identity.

## 7. The open points of the plan's section 11, answered

- **The voice's lifetime** is largely already built: `BuiltIgnitor.releaseTailSec` exists and the
  factory already takes the larger of it and the resolved release. `null` means "no static answer"
  and the caller treats it as "contributes nothing", which is today's behaviour. What changes in
  phase 3 is the safety net, not the number: see the teardown fade above.
- **The placed `pregain` at unity costs 115 ns per block at node level and about 90 per voice**, 12.5
  percent of the phase-3 target. FOLD IT AWAY at build when the resolved value is exactly 1.0, and do
  it through the gate rather than as a special case in `mul`. The decisive argument is identity, not
  the cost: today's built-ins carry no `pregain`, so keeping a unity multiply is the bit change, not
  removing it. What it drops is a `safeOut` scrub that only fires on a sample that is already NaN or
  above 1e15, which a bare oscillator cannot produce and every downstream stage still guards.
- **`analog`'s readers disagreed, and it was worse than recorded. CLOSED in step 1 (2026-09-20).**
  A NaN `analog` failed the `analog <= 0.0` test in `perVoiceCutoffOffsetMul`, so the multiplier was
  NaN, the cutoff NaN, and `bilinearK`'s guard substituted 1 kHz: a NaN `analog` silently retuned
  every filter on the voice to 1 kHz, measured bit-identical to a genuine 1 kHz lowpass ON A SAW;
  on a voice that draws from the rng (supersaw, dust, whitenoise, pluck) it was 1 kHz AND a shifted
  noise stream, because failing `analog <= 0.0` also consumes one draw per filter. `+Infinity` was a
  THIRD behaviour and the worst: it fails `<= 0.0` (the cutoffs clamp) AND passes `> 0.0`, so the
  saturating branch runs with an infinite drive, `kEff = k + 2 * Infinity * 0.0` is NaN at the first
  sample, and every sample of that voice came out NaN with nothing between it and the ORBIT MIX;
  on a sample voice the infinite drift did the same (measured: frame 0 is the PCM's own first
  sample, every frame after it NaN), so `note("c3").oscp("analog", "Infinity").lpf(2000)` poisoned
  the whole orbit's send chain for the rest of the playback. `-Infinity` was always safe, and on the
  sample read a NaN never was a defect (`AnalogDrift.active` is `analog > 0.0`, which a NaN fails). `oscParams["onepole"]` passed `+Infinity` through its `> 0.0` gate and then rendered
  exactly what `onepole(1000)` renders. The sample ignitor read the bag a SECOND time, so the fix was
  one guard plus one de-duplicated read, not one line. Every reader on a render path is guarded now;
  `GraphCensus` is not, deliberately, because it is benchmark-only and audio-inert.

## 8. The migration risk, and it is bigger than the plan states

Not only the doors. **Today the strip's VCA runs on EVERY voice, including every authored ignitor,
with `AdsrDef.defaultSynth` when the pattern set nothing.** So every authored instrument in every
song is double-enveloped today: its own in-tree envelope and then the strip VCA on top. Remove the
strip and the outer envelope vanishes whether or not the pattern ever touched a door. Appending
`.classic()` restores it exactly, because its ADSR defaults are `defaultSynth` and the two envelopes
are bit-equal at those values (measured).

**Needs `.classic()` appended** (an authored instrument, with or without doors on it): A Truth Worth
Lying For (its guitar, plus `analog = 10`), Der Schmetterling (marimba, the three guitars, bass,
granCassa, and its SAMPLE drums), Sakura (kick, sub, pad, koto, shaku, rim, brush), Sandsturm (all
eight), Irish Lament (fingerpick, contrabass, blockfloete), Greensleeves (lute, bass), Dialogue With
The Stars (the placeholder guitar), and the frozen Der Schmetterling 2026-09-16.

**Safe, built-in sounds and samples only:** Drunken Sailor, Final Fantasy 7 Prelude, Irish Lament
Techno, Smalltown Boy, Sound Of The Sea, Stranger Things, Tetris, Tetris Remix, and both frozen
songs. Safe CONDITIONAL on the built-ins being byte-identical, which is what D1 to D3 decide: those
songs exercise every one of the three red stages between them.

**The one that `.classic()` cannot fix: `analog > 0` plus a pattern filter.** The per-voice cutoff
tolerance and the `AnalogDrift` lane are drawn from the voice's rng BEFORE the exciter build, so
moving the filters into the tree shifts the rng DRAW ORDER, which changes every noise source and
every supersaw jitter on the same voice. At `analog == 0` no draw is consumed and identity holds. At
`analog > 0` it does not, which affects A Truth Worth Lying For, Stranger Things, Sakura, Tetris,
Final Fantasy 7 Prelude, Irish Lament Techno and both frozen songs.

## 9. The step list

One review loop and one commit each. Steps 1, 2, 3 and 5 are provably identity-preserving and run
unattended; steps 4, 6, 7 and 10 each need a listening checkpoint.

| # | Step | Identity | The risk |
|---|---|---|---|
| 1 | The bag guard: `takeIf { isFinite() }` on `analog` and `onepole` | provable, no song writes a non-finite one | none; the line deletes itself later |
| 2 | The gate alone (`controlRateValueOrNull` in `buildRaw`), with the off-value table as ONE list | today's built-ins have no slotted stages, so nothing is gated; a spec compares gated-off against inner in raw bits | a knob subtree that draws rng would shift the stream: restrict the query to `Param` and `Constant` leaves. Add the cross-voice-cache invariant spec |
| 3 | The missing knobs of section 4, every default preserving today's tree bit for bit | the spike's five probes become the specs; door parity per knob | the biggest step by volume; consider 3a filters, 3b waveshapers (distort `shape`, tremolo `skew`/`phase`/`shape`; no `oversample`, see D7), 3c envelope. The filter build's rng draw order must reproduce the factory's exactly |
| 4 | D1 and D2 landed | by ear | the checkpoint is the gate |
| 5 | `classic()` on both doors, in the order of section 4 | a one-voice render per door, each slot written in turn | the order: write it once, in one place |
| 6 | The built-ins re-registered, the strip off for them | THE step: minimal renders per built-in per door, plus the whole-corpus render | the teardown fade and the cull rule must land here or the corpus clicks and drops tremolo voices. Re-run the benchmark against 9290 ns |
| 7 | The sample instrument | needs a JS or in-memory PCM harness: the jvm renderer has no sample bank | its own safety net |
| 8 | The doors become `oscp` aliases (about 50 to 60 functions) | door-parity specs, the wire golden regenerated | mechanical but wide; one door group at a time |
| 9 | `VoiceData` cut, `PipelineDsl` retired | compile-time, the golden regenerated | irreversible: only after 6 and 7 are ear-confirmed |
| 10 | The songs migrated with `.classic()` | per-song render against HEAD | D5 |
| 11 | The editor's unknown-slot diagnostic | UI, no audio | none |

## 10. What the spike could not settle

Whether `classic()` can be byte-identical at all (D1 to D3 decide it); which side moves on the ADSR
frame counts; the JS numbers; the de-click name collision; the `pedal` tail; how the per-voice filter
tolerance and the `AnalogDrift` lane should be expressed on a filter node (per-voice rng draws that
no knob can carry, and the draw order has to match); and whether the filter-envelope difference is
audible (read from the code, not probed; the probe is a small addition to the spike's filter harness
and belongs first in step 3).

The spike's five probes are archived in the session scratchpad at `phase3-spike/`; each is a Kotest
file that drops into `audio_be/src/jvmTest/kotlin/ignitor/`.
