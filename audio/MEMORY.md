# Klang Audio — Memory

## The sample instrument, the strip off for samples (phase 3 step 7, 2026-09-26)

- **A sample voice is the built-in shape over its PCM.** `IgnitorRegistry.builtInVoice(source)` is the one
  home of `source.pregain().onepole(ONEPOLE_SLOT).classic()`; `registerBuiltIn` and
  `IgnitorRegistry.SAMPLE_INSTRUMENT` (= `builtInVoice(IgnitorDsl.Sample)`, optimized once, never throws) both
  use it. The sample instrument is NOT registered under a name: a name would enter the sound namespace.
- **`IgnitorDsl.Sample`** is a knob-less leaf (`@WireName("sample")`, no door on either side). Its runtime is the
  voice's `SampleIgnitor`, handed to the build as `buildExciter(sampleSource = ...)` and carried on
  `IgnitorBuildCache.sampleSource`; without one the leaf builds as silence. `VoiceFactory` still resolves the
  sample and builds the playhead from the typed playback fields (begin, end, speed, loop; `cut` and `n` stay
  voice-level); those become slots in step 8.
- **The sample's meta envelope is per-sample slot defaults** (`_sample_envelope_defaults.kt`): every `adsr.*`
  slot without a finite value takes the meta value, the `mergeWith` layering (pattern, meta, `VOICE_ADSR_*`).
  Plain wavs carry none (no copy); a SoundFont zone carries attack 0, decay 0, sustain 1, release 0.05. No
  corpus song plays a SoundFont sound: `SampleInstrumentSpec` alone guards this.
- **The lifetime is the tree voice's rule** (`treeLifetime`, shared with the built-ins): a negative release
  now plays to the gate (it used to end the voice early). The tail stages are shared too (`treeStages`).
- **The strip-off decision is made before any strip filter is built**, so a sample draws nothing for a strip.
  The playhead is built BEFORE the tree, so its drift lane draws first; the tree's filters draw per filter
  after it. The strip drew all tolerances, all drifts, then the playhead. That is the one corpus move: 7 of 17
  songs, every one with `analog > 0` and a pattern filter on a sample voice. A scratch copy of HEAD whose
  sample path draws in the tree's order rendered all 7 bit-identical to the tree.
- **The corpus render DOES play samples**: the CLI loads them from `./cache` (relative: render from the repo
  root) and `KlangOfflineRenderer` preloads every request before scheduling. Only the `:jvmTest` helper
  `renderSong` has no sample bank.
- **The oracle that outlives the strip**: `SampleInstrumentSpec` plays the plain sine's own output as a sample
  at rate 1.0 (the playhead's interpolation then returns each value exactly), so a sample voice must equal the
  built-in `sine` bit for bit, per classic stage, at 48 and 44.1 kHz.

## The built-ins on `classic()`, the strip off for them (phase 3 step 6, 2026-09-26)

- **One registry method writes the built-in shape**: `IgnitorRegistry.registerBuiltIn(name, source)` registers
  `source.pregain().onepole(ONEPOLE_SLOT).classic()` and flags the name. The `pregain` slot sits on the source,
  in front of every nonlinearity (signal-flow plan sections 5 and 6; commit 2 of step 6, before which a sprudel
  `pregain(x)` on a built-in did nothing); unwritten it folds away at build (the `Affine` arm, the optimizer's
  form of `x.mul(k)`). A gain of 2 commutes with the linear onepole bit for bit, so the spec that pins the
  pregain-before-onepole order uses 1.7. `registerDefaults` registers every entry of
  `builtInSources()` (43 names, 28 trees) through it. `isBuiltIn(name)` answers from the NEAREST registry that
  defines the name, so a fork's `register("saw", ...)` shadows the built-in as an authored instrument.
- **A built-in voice runs no strip.** The factory decides it right after the rng deal and then builds NO strip
  filters: `toFilter`/`toModulator` draw from `voiceRandom` at `analog > 0`, and a discarded draw would shift
  the tree's own. The voice runs ignite plus, only when the build reports `endsInEnvelope = false` (the tree's
  `adsrOff`), `TeardownFadeRenderer`, the strip's `adsrOff` fade extracted unchanged (one law, two hosts).
  Authored instruments keep the strip until step 9 (samples left it in step 7, the entry above).
- **The typed fields reach the slots through `_classic_slot_bag.kt`** (scaffolding: removed in step 8 when the
  sprudel doors write the slot keys). Only set, finite fields are written, so the filter envelope's slot-layer
  fill decides as the strip's `depth ?: 7`, with one accepted difference: an envelope whose only set stage is
  non-finite switches nothing on here, where the strip built one at depth 7. A typed door WINS over a raw
  `oscp` of the same slot. No wire change.
- **A built-in's lifetime is the tree's alone** (`releaseTailSec`, which a switched-off envelope still reports),
  floored at 0: a raw negative release is a zero-length release stage and the voice still plays to its gate
  (review round 1's MAJOR: without the floor it ended 0.1 s early from the sustain level). With the typed
  release still on the wire it equals the strip's; it also removes the 0.05 s floor for a release written
  only as a slot.
- **`BuiltIgnitor.endsInEnvelope`** is a ROOT property: set by the `Adsr` arm when it builds, never absorbed
  from a child of a BUILT stage (an envelope under a later stage does not end the voice). A node that IS its
  inner hands the answer through (`passThrough`): every gate arm that did not build (the filters, onepole,
  distort, drive, crush, coarse, tremolo, a switched-off `Adsr`), the three unity-`mul` folds (both `Times`
  sides and the optimizer's `Affine`, the shipping path), `detune`, and the pitch-mod wrappers.
- **The analog draw order is the one corpus move**, and it is small: the strip draws every filter's tolerance,
  then every drift; the tree draws per filter. The TOTAL draw count per voice is unchanged, so every
  render-time stream (noise, pluck, oscillator drift) starts where it did; only which filter gets which
  tolerance and drift seed moves. 9 of 17 corpus songs moved, -49.5 to -81.9 dB RMS. A scratch control with
  the humanization draws off on BOTH hosts rendered those 9 bit-identical: the draw order is the only cause.
- **Engagement controls need the scenario, not the song name.** Der Schmetterling's built-in shaker (release
  0.01) looked like the lifetime-floor control, but its sustain is 0 and it decays to silence inside its gate,
  so a floored lifetime renders zeros. ATruthWorthLyingFor's bass and IrishLamentTechno's sub (a short release
  from a held level) are the rows that see it.
- `perlin`, `berlin` and `crackle` draw at CONSTRUCTION, so with `analog > 0` even ONE pattern filter moves them
  (`BuiltInVoiceMatrixSpec` records it); every other source draws when it renders.

## One envelope law: `EnvelopeCore` for every envelope (phase 3, D3 commit a1, 2026-09-25)

- **`EnvelopeCore` (`audio_be/`) is the one copy of the ADSR law**, with `prepare` once per block and an
  inline `at(pos)` per sample (block-framing ledger E1's agreed shape). Thin hosts: the chain `adsr`
  (`AdsrIgnitor`), the strip VCA (`EnvelopeRenderer`), the node filter envelope (`SvfIgnitor`, via
  `prepareModEnvelope`), the node FM index envelope, the node pitch envelope, and the strip's
  `calculateControlRateEnvelope` (strip filter and strip FM). The strip pitch envelope joined in commit c1
  (`PitchEnvelopeRenderer`, through the mapping `renderPitchEnvelopeRatios` it shares with the node), with
  sprudel's `penv(amount, attack, decay, sustain, release)` vocabulary. `EnvelopeDeclick` is the one de-click smoother.
- **The law:** attack and decay frames FRACTIONAL; release on `floor(N)` frames over `floor(N) - 1`, so the
  last rendered frame is an exact 0.0; the release offset ("0 means 0") everywhere; the release starts
  from the attack-decay-sustain law AT the gate frame (stateless: `Voice.Envelope` lost `level`,
  `releaseStartLevel`, `releaseStarted`); a non-positive or NaN time is a zero-length stage; the sustain is
  RAW (the chain's and the filter node's [0, 1] clamps went). Per destination: amplitude floors at 0,
  filter and FM depth clamp to [0, 1], pitch raw. Attack and decay progress multiply by a hoisted
  reciprocal, the release divides. The strip VCA reads a non-finite sustain as `VOICE_ADSR_SUSTAIN_LEVEL`.
- **A gate at or before the onset releases from 0** (round 1's MAJOR, found by both reviewers). The
  stateless law evaluated at a negative gate (sprudel `legato(-0.1)` reaches the engine raw) extrapolated
  the attack curve: a Square attack squared a negative progress, a burst at 0 dBFS, and with a zero attack
  about +120 dB. HEAD's stateful latch held 0, a silent voice. The lesson: a stateless rewrite of a
  stateful evaluator must state the DOMAIN of its law, not only its formula. Also: a stage too short to
  have a finite reciprocal (below about 5.6e-309 frames) is a zero-length stage (`0 * Inf` is NaN).
- **Proof by a rung ladder.** Rung 0 was the core with per-host legacy switches (17 of 17 corpus rows
  identical, the old audio_be suite green through shims); then one rule per rung, each with a written
  prediction; every prediction held (after one correction made before its result, below). The switches
  and shims were deleted and a final full render matched the last rung.
- **A 16-bit render cannot see a sub-LSB change.** Phase A predicted Sandsturm and Sakura would move on
  fractional frame counts (0.035 s and 0.07 s are 1e-13 frames off whole at 48 kHz); the level difference
  on that one frame is ~1e-16, far below one LSB (3e-5), and they did not move. Likewise the pitch
  envelope's divide-to-reciprocal change moved no row. Predict with the WAV's resolution in mind; a
  bit-level claim needs a spec, not the corpus.
- **The per-sample read is inline, and the evidence for it is soft.** With `at()` a plain method, Node.js
  ran the `guitar-rig-string-only` benchmark slower than HEAD (23.5 us/block against HEAD's 22.0, three runs
  each); inlined (fields `internal`), no regression was measured (18.4 us/block). The runs were NOT
  interleaved (every HEAD run came first, every inlined run last) and the savings vary by case in a way
  the envelope cannot explain, so read it as "no regression after inlining", not as a speed-up. An
  interleaved A/B (HEAD, tree, HEAD, tree) is the method next time. JVM unchanged either way.
- **The filter node's envelope endpoints were pinned by nothing.** Mutating the block-end read (one frame
  early) or the [0, 1] clamp left the whole audio_be suite green; `EnvelopeLawSpec`'s filter-node row (an
  SVF oracle written in the test, sharing only `computeSvfCoeffs`) now catches both.
- **Running the renderer's `java` from a source directory writes a sample `cache/` there.** Run
  `render-par.sh` from the repo root.

## One crush law, one distort loop: D1 and D2 landed (phase 3 step 4, 2026-09-25)

- **`CrushCore` and `DistortionCore`** (`audio_be/`, next to `TremoloCore`) are the one copy of each law;
  the strip's `CrushRenderer`/`DistortionRenderer` and the Ignitor `Crush` node / fused `Distort` node
  (`fusedDistort` in `IgnitorEffects`) are thin hosts. D1: the Ignitor crush FLOORS (it rounded) and takes
  the strip's NaN handling (a NaN sample out as 0, a NaN amount a bypass, +Inf silence). D2 option A: the
  fused node runs the strip's loop (drive inside the oversampler, DC blocker, no cap); the `distort`/`shape`
  doors keep `Shape(Drive(...))` with the cap, untouched. `ClassicStripParitySpec`: every crush and distort
  row and the combination row flipped to bit-identical at both rates (33/11 at 48 kHz, 32/12 at 44.1 kHz).
- **D2 superseded ledger W5's "delete it"; W5's hazard is designed out, not reintroduced.** The fused
  node has ONE bypass, the build gate on a leaf amount at or below 0. A MODULATED amount at or below 0
  runs the core at unity drive, so the oversampler and DC blocker never go stale across a crossing.
- **A shared core is invisible to a parity spec.** Mutating the law inside the core (floor to round, the
  drive moved outside the oversampler, the cap put back) left all 91 parity rows GREEN, because both
  hosts moved together. `StripLawCoresSpec` pins each law against an ORACLE written in the test; that is
  where those mutations go red. Any future "one law, two hosts" needs the same pair: a parity spec for
  the hosts and an oracle spec for the law.
- **The oversampler's transform lambda is built once per instance** in both cores' hosts (it was a new
  closure per block in the strip's crush and distort); it reads a field set per block. Same bits (the
  oracle rows and the corpus prove it).
- **Songs that PLAY a strip distort are more than the plan listed**: besides Tetris, TetrisRemix,
  IrishLamentTechno and the frozen songs, StrangerThings (melody), ATruthWorthLyingFor (`Gitarre!`,
  bass), DrunkenSailor (bass), SoundOfTheSea (glockenspiel sample) and DerSchmetterling (sample kick).
  Every corpus crush writes `oversample = 1`, which is 0 stages: the corpus never runs an oversampled crush.

## `classic()`: the voice strip as a slotted tail, and the filter envelope's slot-layer fill (2026-09-25)

Phase 3 step 5 (`docs/tasks/builtin-instruments.md` section 9), identity-preserving: no built-in is
re-registered (that is step 6), so no song can change.

- **One function, one order.** `IgnitorDsl.classic()` (`audio_bridge/IgnitorDslClassic.kt`) wraps a
  source in crush, coarse, distort, hpf, bpf, notch, lpf, tremolo, adsr; the script `x.classic()` calls
  it. No arguments. It does NOT contain `pregain` (an instrument places it) or `onepole` (the registry
  hangs it on every instrument, which today is BEFORE the strip; step 6 must re-place it for the
  built-ins or StrangerThings and IrishLamentTechno move).
- **Slot names are sprudel's readers, `<door>.<param>`** (`lpf.freq`, `adsr.attack`, `tremolo.sync`,
  `adsrCurves.attack`, the filter curve groups `lpfCurves.attack` / `hpfCurves` / `bpfCurves` / `notchCurves`
  since step 5b c2, plus `adsr.on`), grouped in `IgnitorDsl.Slots` and exposed as `OscSlot.lpf.freq`.
  The plan's flat sketch (`lpf`, `lpenv`, `lpattack`) was wrong: `decay` is already pluck's slot and
  songs' instruments read `attack` / `sustain` / `release`.
- **Every default is the strip's UNTOUCHED value**, from the constant the strip reads, which is not the
  node's default: tremolo rate 0 (node 5), ADSR sustain 1.0 and release 0.05 (node 0.7 and 0.3; the four
  voice-envelope numbers now live in `VOICE_ADSR_*` and `AdsrDef.Std.defaultSynth` reads them), filter
  cutoffs and depths UNSET, crush / coarse / distort / tremolo depth 0, `adsr.on` 1.0. The de-click is
  `Constant(ENV_DECLICK_SECONDS)`, not a slot: no door writes the strip's de-click per note, and a slot
  named `declickSeconds` with the strip's default would collide with the node's own slot.
- **Distort is the fused `IgnitorDsl.Distort` node** (D2 decided, option A: step 4 gives it the strip's
  law). `Shape(Drive(...))` would put the shaper on every voice, since `Shape` cannot be gated.
- **The filter envelope's slot-layer fill** (`slotLayerDepth` in `IgnitorDslRuntime`): an UNSET depth
  slot (a `Param` whose DEFAULT is non-finite, as `classic()` places it) that the bag did not write takes
  `FILTER_ENV_DEPTH_SEMITONES` when any of the four stage knobs is a `Param` with a FINITE value in the
  bag. A written depth stands (an explicit 0 included), an AUTHORED depth default stands (0 included:
  the first cut filled `Osc.param("e", 0)`, round 1 of the review caught it), a `Constant` depth is
  never a question, an authored stage default alone is not written, a non-finite bag value is unset. The brief first said "any of the five", which would have turned `lpf(env = 0)` plus a
  stage into a 7-semitone sweep; the strip's `depth ?: 7` and `/dsl-design` section 4 say otherwise.
- **`passes` is a knob** (`Lowpass`/`Highpass.passes: IgnitorDsl`, it was an `Int`), read once at
  build, leaf-only, through `coercePasses`, which ROUNDS (as the strip always has for sprudel's value)
  and since this step reads a non-finite value as one pass (`+Infinity` used to saturate to 16 on the
  strip). The optimizer fuses only a `Constant` count. The Kotlin flat doors keep an `Int` parameter.
- **Against the strip, measured** (`ClassicStripParitySpec`, 44 rows, one voice each through the real
  `VoiceFactory`, the classic voice on an EMPTY pipeline): bit-identical for the untouched voice, coarse,
  every static filter (passes and all four together included), tremolo at every shape/skew/phase, the
  ADSR at whole frame counts, its curves, analog on the oscillator alone, and a combination. Divergent,
  each named for its decision: crush (D1, up to 0.707 at amount 1), distort (D2, the soft cap, up to 0.69
  on `gentle`; `tube`/`rectify` at 0.5 only 1.4e-7), every filter envelope (D3), analog with a filter
  (the drift sampling, 7e-6 on one filter; the section 8 draw order with two, 4.8e-4), fractional ADSR
  frame counts (2e-3), a slot release shorter than 0.05 (the factory's lifetime floor keeps the classic
  voice's de-click tail rendering after the strip voice is cut: step 6), and adsrOff (the strip's 4 ms
  teardown fade, exactly the last 192 frames: step 6).
- **A lone filter is FUSED into an `Eq` by the optimizer and never reaches `filterEnvDef`**, so the
  corpus exercises the fill only through UNFUSED filters, the ones with a non-literal `analog`. The
  engagement control (fill always taking 7) moved exactly the four songs predicted from that:
  ATruthWorthLyingFor, DerSchmetterling, FrozenPieceDerSchmetterling, Sakura.

## The envelopes: one adsr shape, curve index knobs, an on switch, one exp bend (2026-09-25)

Phase 3 step 3c (`docs/tasks/builtin-instruments.md` section 3b, the envelope sub-table), identity-preserving.

- **One `adsr(attackSec, decaySec, sustainLevel, releaseSec, configure)` everywhere.** The chain's lambda
  gets an `AdsrBuilder` (`curves`, `declick`); the filter and pitch builders' `adsr` get a `ModAdsrBuilder`
  (`curves` only); fm's `adsr` takes no lambda. The reach-back chain methods `adsrCurves`,
  `declickSeconds` and `expK` are GONE: they copied onto an `Adsr` that was their IMMEDIATE receiver and
  otherwise WRAPPED a second envelope with the node defaults, so `.adsr(..).lowpass(..).adsrCurves(..)`
  was two envelopes. All four corpus sites sat directly after `adsr` (a pure move).
- **One `adsr` call is the whole envelope** inside a builder: a later call replaces an earlier one,
  curves included, and a call without a lambda is unshaped.
- **Curves are INDEX knobs** through `AdsrCurves` (audio_bridge), on the chain, the four filters and the
  pitch envelope. Unlike the shape catalogues there is NO single fallback: `curveOf` returns null for an
  unknown name, and `indexOf(name, fallback)` / `curveAt(index, fallback)` take the READER's default
  (the chain `AdsrCurve.Default`, the modulation envelopes `MOD_ENV_CURVE`). Read once at build,
  leaf-only (`adsrCurveKnob`). Sprudel's private parser goes through `curveOf`; `AdsrCurvesSpec` pins
  the table against the retired `when` copied verbatim.
- **`expK` is gone from the node, the slot, the wire and `Ignitor.adsr`.** `AdsrIgnitor` bends every exp
  stage at `ADSR_EXP_K` with the shared `ADSR_EXP_NORM` (the same function of the same constant, so
  bit-identical to the per-block computation it replaced). `oscp("expK", k)` is now an unread key. The
  Pipeline DSL's `StageDsl.Vca.expK` stays until the Pipeline DSL retires.
- **`Adsr.on`, a node field only.** OFF at exactly 0.0 (the flag rule: non-zero is on, so -1 is ON);
  unset, any other number and a non-leaf are ON: the second "unset is not off" after `mul`. OFF does
  not build the stage or its knob subtrees, but STILL REPORTS THE RELEASE TAIL (leaf-only), because
  the strip's `adsrOff` keeps the voice's lifetime and step 6's identity needs the node to match. A
  non-leaf release on an off envelope reports none (asking would build it and take its draws).
- **Evidence.** The corpus (18 rows) IDENTICAL through `compare.sh`. Six controls, each predicted from
  the arrangements before running and each exact: the name "linear" moved DerSchmetterling and the
  frozen piece; "scurve" moved Sakura (its pad plays from cycle 42); "exp" moved ATruthWorthLyingFor;
  the chain's default curve knob and the inverted `on` test each moved the eight songs that play an
  authored Ignitor `adsr`; the modulation default moved the six pitch-envelope songs. No song plays an
  Ignitor filter envelope or a `declick`, so those paths are proven by specs only.
- **The render harness takes ABSOLUTE corpus paths**: `gradlew -p <tree>` runs the CLI from the tree,
  so a relative `--file` is "File not found" and every row is a FAIL (the compare refuses it).
- **A retyped node field breaks JS-only test sources too.** The first migration pass compiled the JVM
  test sources only, and `audio_bridge/src/jsTest/.../WireCodecRoundTripSpec.kt` still passed an
  `AdsrCurve` to the now-`IgnitorDsl` field; the wire mutation run surfaced it as a compile error.
  Compile `compileTestKotlinJs` of every module before calling a retype migrated.


## The waveshaper and tremolo knobs: one tremolo law, index shapes, a build-time factor (2026-09-25)

Phase 3 step 3b (`docs/tasks/builtin-instruments.md`), identity-preserving.

- **One tremolo law, `TremoloCore`.** The strip's `TremoloRenderer` and the Ignitor `Tremolo` node are thin
  wrappers around it; the per-sample loop is the strip's, moved verbatim. The node gained `shape` (an index
  into `LfoShapes`), `skew` and `phase`. `TremoloNodeStripParitySpec` proves node == strip in RAW BITS on a
  grid (every shape, six skews including NaN, five start phases including 3.25 and -0.4, fractional rates,
  a mid-block voice start). The two sides already agreed at neutral because `(TWO_PI * rate)` and
  `(rate * TWO_PI)` are the same bits (IEEE multiplication commutes) and the node's inlined sine was
  `lfoNorm`'s fast path; what was missing was only the seed, the skew and the shape.
- **A parity spec over a SHARED law only tests the plumbing.** A mutation of `TremoloCore` moves both
  sides and the parity spec stays green; that is `TremoloRendererSpec`'s job (it pins absolute
  waveforms). Mutation-check a parity spec on what is NOT shared: the knob reads, the seeding, the
  host's block contract.
- **Shapes are catalogue INDICES** (`DistortionShapes`, `LfoShapes` in `audio_bridge`, beside
  `BodyMaterials`). The backend's `parseDistortionShape` / `parseLfoShape` go through the index, so the
  strip (a name off the voice) and the tree (an index knob) cannot disagree. The enum order IS the
  catalogue order: append only, pinned by `ShapeCatalogueSpec` against the pre-3b `when` arms copied
  verbatim as the oracle. Resolution: nearest position, ties to even; non-finite, negative or past the
  end is index 0 (`soft`, `sine`), the same as an unknown name. Never a throw.
- **Build-time knobs** (`Shape.shape`, `Shape.oversample`, the same on `Distort`, `Tremolo.shape`,
  `Tremolo.phase`) are read leaf-only through `buildTimeKnobValue`; a non-leaf takes the knob's default
  and is NOT BUILT, so no rng draw moves (the `filterEnvKnob` rule). The skew is read per block, so it
  is built, and an expression works there. `Oversampler.factorOf` is the one factor conversion:
  non-finite is 0, a fraction truncates (sprudel's `asIntOrNull`), no upper clamp (D7 stopgap; the OOM
  edge of a huge factor is the maintainer's to decide).
- **The legacy `Distort` node is kept and retyped**: it is the one node that gates drive and shape as a
  unit, which a slotted `classic()` distort stage needs (D2). Its old KDoc reason ("serialized trees")
  was wrong: wire trees are never persisted.
- **`BuiltIgnitor.gatesOutput`**: a built tremolo on the spine reports it, and the voice factory ORs it
  into the cull-never decision (the strip's rule, section 6). Landed in 3b because a square tree tremolo
  made the culler's first-off-half kill reachable from a script. Step 6's teardown fade builds on it.
- **The KlangScript door forbids mixing positional and named arguments**: `distort(0.5, oversample = 4)`
  is an argument error; write `distort(amount = 0.5, oversample = 4)`.
- **Evidence.** The whole corpus (18 rows) IDENTICAL through `compare.sh` against the pre-3d(i) baseline.
  Controls: perturbing `Oversampler.factorOf` moved ATruthWorthLyingFor, DerSchmetterling,
  FrozenPieceDerSchmetterling and Sandsturm; the "tube" entry moved those that use tube on either host
  (including TetrisRemix, through its import of Tetris's `sub`); `TremoloCore`'s gain moved DrunkenSailor
  alone. No song carries an Ignitor tremolo, so the node side is proven by the parity spec only.
- **An engagement prediction must follow what PLAYS, not what is DEFINED, and must follow imports.**
  Two predictions of this step missed by grep alone: DialogueWithTheStars defines three oversampled tube
  guitars but its arrangement plays only the acoustic placeholder, so neither control moved it; and
  TetrisRemix reaches a tube distort through `import { sub } from "peekandpoke/tetris"`. Before
  predicting, read the song's arrangement and its imports.


## Two banks, one parking slot, 20 ms: the filter swap after the morph was rejected (2026-09-20)

Katalyst step 5c-11, a SOUND CHANGE under the 5c listening checkpoint. The maintainer listened to
5c-10 and **REJECTED the morph** on body and on vowel both: travelling the resonances is an audible
filter sweep ("an 8-bit laser shot" on `body("<wood glass>")`, the same on the vowel), and the
click metric could not see it because a sweep is not a discontinuity. The output crossfade stays.
Then the 10-bank pool went too, and the fade got its own constant.

- **The model.** `KatalystFilterSwap` carries TWO banks: one target and ONE outgoing entry (a pair,
  or dry). Everything 5c-6 decided about the edges is unchanged: off fades to dry and enters Off
  only on the landing, on fades in from dry, a returning owner turns the fade around, the first
  initialisation is instant, `reset` is a synchronous hard cut, and the host's intent flips
  synchronously. What changed is what happens to a change that arrives WHILE a fade runs: the swap
  REFUSES it (`set` and `clear` are no-ops in `Crossfading`; the new `settled` says when they act),
  and the HOST parks it.
- **The parking slot holds a CONFIG, never a built bank.** One slot per host, latest wins; a
  further change overwrites it, so a burst costs one install and not one per event. It is offered
  again from the host's own `process`, AFTER `swap.process`, so the parked change starts on the
  block after the landing rather than one later, and so a chain whose owner has died (still
  processed, no longer configured) does not lose it. Parking the DEF is what keeps the EQ at TWO
  pre-built banks: a parked BANK would have needed a third that nothing could hear, and body and
  vowel would have allocated for a change that a later one overtakes. `KatalystFilterSwap.holds`
  stays, and it is what the EQ asks for a bank nobody hears.
- **The fade is `BANK_CROSSFADE_SECONDS = 0.02`, its own constant** in `audio_bridge/constants/`.
  `KNOB_GLIDE_SECONDS` stays 0.05 and keeps every level and dynamics glide (the compressor's
  release gets worse if shortened). The ONLY reader moved is the swap; the delay's tap crossfade,
  the fader, the gain, the compressor, the phaser, the duck and `KnobGlide` all still read the knob
  glide. `ResonatorBank`'s own reader went with the morph.
- **The INSTRUMENT had to be rebuilt before any of this meant anything** (review round 1). The
  first sweep measured both bands through an 8th-order Butterworth, whose 48 dB/octave skirt leaks
  the SIGNAL into the measured band. Above 8 kHz the source's own top harmonic (2970 Hz, 1.43
  octaves below the cutoff) set a "floor" of -95.3 dB; below 60 Hz the 110 Hz fundamental set one
  of -42 dB, which is why every vowel row sat 0.1 to 0.4 dB over it and measured NOTHING. Proof that
  it was leakage and not noise: the reading tracks the top harmonic and nothing else (1 harmonic
  -303 dB, 5 -207, 10 -162, 20 -116, 27 -95.3, following the skirt), it is identical for two
  sources whose phase arithmetic differs, and one 110 Hz sine six octaves below the cutoff reads
  -261 dB. **The first report's claim that this was the source's own rounding noise is wrong by
  150 dB.** Rebuilt with a 16383-tap Blackman-Harris windowed sinc per band, stopband MEASURED and
  printed with the results (-142.0 dB at 110 Hz for the 60 Hz lowpass, -163.7 dB at 2970 Hz for
  the 8 kHz highpass), and the floor established on a STATIC render (the same material throughout,
  nothing switching) instead of a quiet stretch of the render under test. The lesson that survives:
  **establish a metric's floor on a static render first, and if it is not tens of dB below the
  effect, the instrument cannot answer the question.** That test alone would NOT have caught this
  case: the HF floor WAS measured on a static render, at -95.3 dB, and the readings sat 13 to 33 dB
  below it, which passes. Two more things are what caught it, and they belong to the lesson:
  **vary the SOURCE and watch whether the floor follows it** (a floor that tracks the source's top
  partial is the instrument's skirt, not a floor), and **print the analysis filter's MEASURED
  stopband next to the results** so a reader can see the instrument's own limit without rebuilding
  it.
- **20 ms on the honest instrument**, worst of 8 change instants one block apart, 15 material pairs
  and 6 vowel pairs, static floors -142 dB (sub-60) and -156 to -161 dB (HF):

  | fade | body sub-60 @110 Hz | body sub-60 @220 Hz | vowel sub-60 @110 Hz | body HF @110 Hz | vowel HF @110 Hz |
  |---|---|---|---|---|---|
  | 5 ms | -25.6 | -40.4 | -56.1 | -62.8 | -73.1 |
  | 10 ms | -31.6 | -42.4 | -61.0 | -68.8 | -79.1 |
  | 12 ms | -35.1 | -43.9 | -63.2 | -69.9 | -80.7 |
  | **20 ms** | **-39.6** | **-50.6** | **-68.2** | **-74.9** | **-85.2** |
  | 50 ms | -49.0 | -59.1 | -76.1 | -82.9 | -93.2 |

  At a 55 Hz fundamental the sub-60 band IS the signal (-2.3 dB against a -2.2 floor at every fade
  time), so it says nothing there and is reported as such. The gap between 20 and 50 ms is **8 to
  9.4 dB on both bands**, not the 5 to 8 dB the leaky instrument reported. Per-case HF numbers do
  NOT converge at 8 instants (one pair's high band wanders several dB between fade times, while the
  worst-over-8x15 and worst-over-8x6 columns are monotonic), so only the worst-over-a-family
  columns are quoted, and no single HF case to a tenth of a dB.
- **The on and off EDGES and the RUNS, on the same instrument** (review round 2 asked for this;
  round 1 had them only through the leaky filter, where the sub-60 reading sat a few dB over the
  skirt and therefore said nothing). At 110 Hz, sub-60: an ON edge at 20 ms **-39.2** against
  -50.4 at 50 ms, an OFF edge **-41.3** against -50.3; a 174 BPM run at 20 ms **-38.3 to -40.3**
  against -47.4 to -49.6 at 50 ms. Above 8 kHz the same rows are -80.7 to -82.3 (edges) and -83.0
  to -83.7 (runs) at 20 ms, against floors of -147 to -161 dB. **The edges and the runs land where
  a material change lands**, so the sub-60 headline is not a material-change-only number, and every
  family moves the same 8 to 11 dB between 50 and 20 ms.
- **The burst falls about 10 dB per OCTAVE of source pitch**, which says what it is: the
  transition spreading the source's own low partials downward, not the bank's low mode ringing.
  At 20 ms, worst over five material pairs: 110 Hz -39.6, 220 Hz -50.6, 440 Hz -60.1, 523 Hz
  -59.1, 880 Hz -64.7 dB, all against a -142 dB floor. **So there is no "high source with no bass"
  case that isolates it**: raising the pitch removes the artifact along with the masker. The
  isolation has to come from the ENVELOPE, and it does: a soft attack and a long release drop the
  notes' own sub-60 content by 17 dB (-27.5 to -44.8) while the artifact stays at -39.7 dB, so in
  that render the bank swap is 5.1 dB LOUDER than the note itself below 60 Hz. That rendered
  number agrees with the isolated harness's -39.6 dB to a tenth of a dB, which is the best
  cross-check the two instruments could give each other.
- **Designing a case that can EXPOSE the burst took three conditions at once, and only one of the
  five candidates met them** (review round 2). Artifact relative to the note's own sub-60 content:
  `body-single` (c2, change on a note onset) 20.4 dB UNDER, so a "no" there is not evidence and the
  same holds for all three runs; `lowend-a2` 13.7 dB under; **`lowend-a2-soft` 5.1 dB OVER**;
  `lowend-midnote` (c5, change mid-note) 29.2 dB under; `lowend-midnote-a2` (a2, mid-note, six
  overlapping voices) 29.7 dB under, because the beating of six detuned supersaws puts 26 dB more
  sub-60 into the mix than one soft voice does. The three conditions: the change must not sit under
  a note ONSET, the source must have LOW PARTIALS, and the voice count must stay low. Moving the
  change off the onset is worth doing and is NOT sufficient on its own.
- **The verdict, split by band.** On the CLICK metric 20 ms holds with a wide margin: the worst
  body case is -74.9 dB re the signal (110 Hz), the worst vowel -81.6 (220 Hz), the worst edge
  -80.7 and the worst run -83.0, and the 12 ms this swap ran before
  5c-6 (never the click of that era) measures -69.9. On the SUB-60 metric it does NOT settle the
  question: the worst burst at 20 ms is -38 to -40 dB re the signal wherever it comes from (a
  material change -39.6, an on edge -39.2, an off edge -41.3, a 64th-note run -38.3), 8 to 11 dB
  louder than the same case at 50 ms, which is the level and the class of the phaser "blub" in
  5c-9. **The trade, if it has to move, is low end against rate and it is sharp**: a fade occupies
  whole blocks, so 20 ms lands at 7 blocks / 20.3 ms (44.1 kHz) and 8 / 21.3 ms (48 kHz), 30 ms at
  11 / 31.9 and 12 / 32.0, and 50 ms at 18 / 52.2 and 19 / 50.7. Against 174 BPM (a 64th is
  21.55 ms, a 32nd 43.1, a 16th 86.2) that makes **20 ms the only one of the three that lets 64ths
  through**; 30 ms parks them and clears 32nds, 50 ms parks 32nds and clears 16ths. What 30 ms buys
  below 60 Hz depends on which instrument is asked and the two DISAGREE: 4.8 of 9.4 dB on the
  isolated sweep (about half), 6.4 of 7.9 dB on the rendered diagnostic (about 80 percent). It is
  loudest where a low mode starts cold (`wood->cedar`, `croon->tube`, `bell->wood`);
  the vowel is 25.1 dB quieter worst-to-worst (-64.7 at 220 Hz against the body's -39.6 at
  110 Hz) and 14.1 dB quieter at the same note, and it is not in question at either reading. **That is the maintainer's call by ear, and the
  listening set is built to put it in front of him** (the low end of `body-single` and the runs).
- **The COLD START is what a shorter fade exposes** rather than hides: a fresh bank rings up in
  0 to 30 ms on every material except `bell`, which takes 70 to 105 ms. At 20 ms the fade is over
  before a bell has rung up, so the ring-up is heard in the clear. It is a level and timbre effect,
  not a step: the on-edge metrics stay at -79.8 dB (HF) and -35.8 dB (sub-60).
- **The rate limit is the point.** Block by block at 44.1 and 48 kHz, 174 BPM: the new model at
  20 ms queues NOTHING and drops nothing at 16ths, 32nds or 64ths (21.6 ms apart), and never runs
  more than two banks. The 10-bank pool at 50 ms also dropped nothing but ran up to 3 banks at
  32nds, 4 at 64ths and 6 at 128ths. **Careful with that comparison** (review round 1): it is the
  pool's behaviour on the paths that REACH it, which at `947c2023` were the orbit EQ's per-event
  curve changes, `wet` and `floor` changes, the on and off edges and a change onto a fading pair.
  A material run did NOT reach it there, because the 5c-10 morph carried material changes inside
  the bank in service; the morph's rejection is what put them back on this path. The limit is also
  a BLOCK count, `ceil(fadeLen / blockFrames)`, so it is 20.3 ms at 44.1 kHz (7 blocks of 128) and 21.3 ms at 48 kHz,
  and 64ths clear it at 174 BPM but not at 176. Past it the parking ALIASES rather than thins: the
  surviving changes are phase-locked, so at twice the limit a four-material pattern is heard as a
  two-material alternation with two materials never sounding (at 128ths, 199 of 200 queued and
  93 to 98 dropped). The maintainer accepts that, "a rate faster than that is chaotic in any case".
- **Deleted with the morph:** `ResonatorBank.morphTo` and everything that existed only for it (the
  `MORPH_CAPACITY` capacity preallocation, the log-space from/to arrays, `morphPos`, the bank's own
  `fresh` snap, `bandNow`/`capacityBands`), `LowPassHighPassFilters.BaseSvf.retune` and
  `resetState` (no other caller; `BaseSvf.q` is a `val` again), the split factories `bodyBank` /
  `wrapBody` / `formantBank` / `wrapFormant` (the hosts build through `createBody` / `createFormant`
  again), the `MORPH` flags and `morph` parameters on both hosts, `KatalystFilterSwap.isTarget`,
  `ResonatorBankMorphSpec`. `clampSvfCutoff` / `clampSvfQ` STAY: `computeSvfCoeffs`, the vowel
  gain fold and the bank's own construction use them.
- **Songs** (15 built-in, 3 frozen, 256 cycles, 48 kHz, wall clock pinned, HEAD `947c2023` in a
  throwaway worktree against the tree), **rendered TWICE, once per review round, and the second run
  reproduced the first digit for digit** (same four songs, same worst counts, same first-difference
  times, same span counts); the round-2 tree is also bit-identical to the round-1 tree on all 18,
  so the round-2 delta moved no sample. 14 bit-identical. Four differ, and every one of them is a
  song that changes a material or a vowel on a sounding orbit: Stranger Synths -20.9 dB re peak
  (9 spans), Synthkura -23.3 dB (38 spans), frozen Der Schmetterling 2026-09-16 -23.4 dB (3 spans),
  frozen Stranger Things 2026-07-03 -26.8 dB (1 span). **Attributed**: with every `.body(...)` and
  `.vowel(...)` call stripped from the text on both sides, all four are BIT-IDENTICAL, so nothing
  else in the step moves a sample.
- **Guards, each mutation-checked (10 mutants, all red).** `FilterSwapLaw` is now the TWO-BANK law
  (one entry; `set` and `clear` refused while it fades) and it does NOT model the parking: a spec
  tells it about a parked config at the block the STAGE installs, so a host that installed early,
  late, or dropped one departs from it sample for sample. `KatalystFilterSwapSpec` adds "the fade
  is BANK_CROSSFADE_SECONDS, not the knob glide", "a change mid-fade is REFUSED", "at most two
  banks run on a block", "a clear mid-change is refused too" and "a landed fade and a refused
  change leave no reference behind". Body and vowel each get the four parking rows (parked and
  installed on the landing, a further change replaces it, an OFF parks too with the intent flipping
  at once, a return to the installed config drops what is parked) and "a change builds a NEW bank:
  the bank in service is never retuned", whose teeth are the FRESH reference bank. The EQ's
  every-block row now pins the parking, the overtaken changes and the install COUNT, plus "the
  installs ALTERNATE between two banks", whose seam is which bank an install took: the COUNT
  itself cannot be pinned, because an install only runs while the swap is settled, where it holds
  at most one bank, so a third is unreachable by construction and a pool of three renders
  identically (measured; the row's own comment records it).

## The filter nodes grew a cutoff envelope and a per-voice lane (2026-09-20)

Phase 3 step 3a of `docs/plans/signal-flow-redesign.md` (the missing-knobs table is
`docs/tasks/builtin-instruments.md` section 4, first two rows). The four SVF filter nodes
(`IgnitorDsl.Lowpass`, `Highpass`, `Bandpass`, `Notch`) now carry the cutoff ENVELOPE as five
knobs (`env`, `attackSec`, `decaySec`, `sustainLevel`, `releaseSec`) and the per-voice analog
character as ONE structural flag (`humanize`). Both doors, same names, same defaults.

- **`env` is the NODE's envelope switch, and that is what keeps the step bit-identical.** At
  exactly `0` no `FilterEnvDef` is built, `SvfIgnitor.hasEnv` is false and the filter takes the
  same branch it took before the knobs existed.
- **The DOORS fill, the node does not** (`/dsl-design` section 4, the compound-door rule, applied
  to the first of the voice-side doors it named). The filter envelope has no NAME knob, so ANY of
  its five knobs names the stage and a call that names one writes every companion it left out,
  `env` included, from `audio_bridge/constants/FilterEnvelopeDefaults.kt`. Without that,
  `lowpass(800, decaySec = 0.3, sustainLevel = 0.2)` would be a silent no-op while the identical
  `lpf(800, decay = 0.3, sustain = 0.2)` is an audible pluck, because `SprudelVoiceData` builds a
  `FilterDef` envelope when ANY of `lpattack`/`lpdecay`/`lpsustain`/`lprelease`/`lpenv` is present
  and `FilterEnvDef.resolve()` fills the missing depth with `FILTER_ENV_DEPTH_SEMITONES`. One home
  for the fill, `fillFilterEnvelope` in `audio_bridge`, reached by both doors. A call that names
  NONE of the five is the untouched filter, which is what the identity render rests on; a
  hand-built node is a value, not a call, and keeps the plain `0`.
- **The knobs resolve at BUILD, from `Param` and `Constant` leaves only.** That is not timidity
  and not a perf choice: the strip's envelope is a per-voice constant too (`FilterDef.envelope`
  resolves at note-on), and a leaf provably takes no rng draw, so asking the five questions moves
  no draw. A modulated knob has no build-time answer and the knob's default stands.
- **`humanize` is a FLAG, not a knob, because both halves are per-voice DRAWS.** The fixed cutoff
  tolerance and the `AnalogDrift` lane come off `IgnitorBuildCache.random` at build, and no
  number a pattern writes can carry a draw. The ORDER has one home,
  `ignitor/FilterHumanization.kt`: one `nextDouble()` for the tolerance, then the lane's three
  (two doubles, one int); at `analog` at or below 0, or non-finite, NOTHING is drawn.
  `perVoiceCutoffOffsetMul` is now ONE function that `VoiceFactory` and the tree build share,
  because a second copy of a draw is a second reader of the stream.
  A `passes` cascade draws ONCE and every stage shares the one tolerance and the one lane, the
  way the strip's single `AudioFilter` with N stages does; the lane steps once per block, keyed
  on `IgniteContext.voiceElapsedFrames`.
- **What a tree CANNOT reproduce, recorded rather than hidden.** `VoiceFactory` draws every
  filter's tolerance first and every filter's drift afterwards, in two passes over the filter
  list, and it draws all of that BEFORE the exciter is built. A recursive per-node build
  interleaves them and draws during the exciter. So at `analog > 0` with more than one filter the
  stream shifts, which is exactly what `docs/tasks/builtin-instruments.md` section 8 already says
  identity cannot survive. Nothing in step 3a is reached by it (no built-in sets `humanize`).
- **Fusion refuses both.** `IgnitorDslOptimizer.asFusibleSections` now also refuses a filter whose
  `env` is anything but a literal zero, and any filter with `humanize`: an `EqSection` carries
  freq and q and nothing else, so a fused sweep would silently become a static filter. A
  `Param`-backed `env` refuses for the same reason a `Param`-backed `analog` does.
- **Identity, proven:** all 18 corpus rows (15 built-in songs, 2 `FrozenSongs`, 1 `FrozenPieces`)
  render bit-identically at 256 cycles, wall clock pinned, base commit `ef6a1c4d` against the
  tree. Two engagement controls: `env` defaulted to the shared depth constant instead of 0, and
  `humanize` defaulted to true. The wire moved as designed and only there: six new fields on each
  of the four filter variants in the generated codec, `WIRE_SCHEMA_HASH` -1128448217 to 846243239,
  no `@WireName` touched. The sprudel `voicedata_golden.txt` did NOT move, and should not: it is
  a `VoiceData` baseline and `VoiceData` carries no `IgnitorDsl`.
- **A script-door default must be a LITERAL, and this step measured it rather than trusting the
  rule.** A constant REFERENCE as a default makes KSP emit no thunk, and a named call that skips
  that param then fails at RUNTIME with "complex Kotlin default". The first cut of these doors hit
  it with `FILTER_ENV_ATTACK_SEC` and had to bake the numbers; the compound fill then removed the
  duplication altogether, because every one of the five defaults is now the literal `null` and the
  constants live in `fillFilterEnvelope`, which both doors call. `humanize`'s `false` is the same
  shape of literal. So the lesson stands and the duplicate it used to force is gone.
- **D3 is open on TWO counts, not one, and the LAW is the bigger one.** The spike framed D3 as a
  SAMPLING question (the node computes the envelope at block start and end and interpolates the
  coefficients across the block; the strip computes it once per block and lets `setCutoff` ramp
  them over 32 samples, then holds). Round 1 of this step's review measured the other half: the
  node's envelope is LINEAR (`computeFilterEnvelope` has no curve term at all) while the strip's
  is the house Exponential (`AdsrCurve.Default`, K = 3, because `VoiceFactory` omits the three
  curve arguments). At `env = 24` the two are up to 806 cents apart at the same instant, RMS 256
  cents on a pluck and 512 on a pad, which is 4x to 100x the sampling difference. The two agree on
  the ENDPOINTS and on the stage times. Step 3a changes neither law: it adds the knobs with the
  node's existing behaviour and says so in every KDoc that could be read as claiming parity.
  (Settled 2026-09-25 in step 5b: (a2) gave the strip the node's sampling, (b) set `MOD_ENV_CURVE =
  Exponential`, so both hosts now share one law, one sampling and one default curve.)
  A third, smaller mismatch of the same family, recorded rather than fixed: the node truncates
  stage frame counts to `Int` where the strip keeps them fractional (220 against 220.5 at
  `attackSec = 0.005`), the same class as the ADSR frame-count item the spike already listed.
  (Settled 2026-09-25 by step 5b (a1): one `EnvelopeCore` counts attack and decay in fractional frames
  on every host.)

## The gate: a stage at its off value is NOT BUILT (2026-09-20)

Phase 3 step 2 of `docs/plans/signal-flow-redesign.md`. At voice build, a stage whose gating knob
is a `Param` or `Constant` LEAF and resolves to the unset sentinel or to that stage's OFF value is
not built: the arm in `IgnitorDslRuntime.buildRaw` returns the inner. Everything about the rule
lives in that file's `gatedOff` KDoc; the off VALUES are one table, in
`docs/tasks/builtin-instruments.md` section 5b; `IgnitorGateSpec` holds both to their word.

- **It is the optimisation phase 3 stands on.** One voice, 128-frame blocks, JVM, min..max of
  3 x 200k blocks, base commit against the tree: a nine-stage slotted tail with nothing written
  cost **2022..2040 ns per block** and costs **181..182**, against **146..150** and **136..142**
  for a bare saw. Fully engaged the tail is 3424..3451 against 3365..3401, which is no change
  beyond this box's own run-to-run drift (the bare-saw row, an untouched shape, drifts about 7
  percent between runs); that row is the harness's control, because where the gate cannot fire
  nothing may move. This entry is the ONE home of these numbers: the `gatedOff` KDoc points here
  rather than repeating them, so a re-measurement is never chased through comments. A placed unity `pregain` multiply cost 240 ns as a `Times` and 258 as the
  optimizer's `Affine` and both now fold to the bare saw. (The spike's own probe read 2531 and 149
  for the first two shapes, so the baseline reproduces.) **Not measured anywhere: the ON path's
  BUILD cost**, which is where the gate's own price sits.
- **It is also a NaN GUARDRAIL, and that half is load-bearing.** `SLOT_UNSET` is `Double.NaN`, and
  the `Param` leaf reads a non-finite OVERRIDE as unset and hands back the DEFAULT, so a slot
  whose default IS the sentinel resolves to NaN at the leaf, with nothing downstream to scrub it.
  The gate's `!isFinite()` arm is what keeps it out of the DSP, for every gated stage at once.
- **Restricted to the two leaves, and that is not timidity.** `crackle`, `perlin`, `berlin` and the
  sample ignitor's `AnalogDrift` lane take their rng draws in a PROPERTY INITIALISER, which is
  construction, so a query that built a knob subtree in order to ask it would move those draws in
  front of the inner's and shift the voice silently. The other sources capture the stream and draw
  at generate time, where build order cannot reach them. `controlRateValueOrNull` folds pointwise
  expressions over leaves too, which is stronger than the plan's wording, but telling a draw-free
  expression from a drawing one needs a new walker; that strength stays unused.
- **What the leaf restriction does NOT cover, and it was a choice between THREE options.** A
  gated-off stage is not built at all, so its SIBLING knobs are not built either: a `perlin` in a
  gated filter's `q` stops drawing and every later drawing node shifts. (1) Refusing to gate in
  that case is the worst option, because a filter with an UNSET cutoff would then be built and
  `bilinearK`'s guard would silently substitute 1 kHz, which is the step-1 defect on the stage the
  gate exists for. (2) Gating but still BUILDING the knob subtrees in declaration order and
  discarding them would reproduce the stream exactly and cost nothing per block, and it is the
  option not taken: `buildIgnitor` caches every non-leaf build, so a discarded subtree that is also
  on the live spine (a `let`-bound LFO) is reached again through `getOrPut`, which calls
  `incConsumers()` and flips that node's `MemoizingIgnitor` from pure delegation to a per-block
  cache plus a buffer copy for the whole voice; it also puts allocation back on the OFF path, which
  is the path the gate exists to make cheap. (3) Letting the siblings go with the stage, taken.
  `IgnitorGateSpec` pins it so nobody "fixes" it by accident. Worth revisiting in step 3, when
  every filter cutoff becomes an unset-default slot and the case stops being hypothetical.
- **`mul` is the one asymmetry: unset is NOT off there, and only a SIGNAL survivor folds.** Only
  exactly 1.0 folds. `TimesIgnitor` already sanitises a non-finite factor to an exact zero, so
  there is nothing to guard, and the node also sits in PARAMETER positions where that zero is the
  point (`AffineIgnitorSpec`'s non-finite rows and the C5 non-finite-q row are what refuse the
  other reading). The second half is the same insight: what the fold drops is the multiply's
  `safeOut`, and in a parameter position that clamp does work, so `survivesUnityFold` folds only
  over a survivor whose `isBlockConstant` is false. `q = param("res", +Inf).mul(pregain)` resolved
  to `SAFE_MAX` and then to the filter's q ceiling; folded it would stay `+Inf` and land on the q
  fallback, a different filter. Consequence for the slot side: a `mul` slot must default to a safe
  literal, never to `SLOT_UNSET`, or an unwritten one silences the voice; `IgnitorDsl.pregain()` is
  the one door that places such a slot today and a spec row guards its default. The two doors
  differ on purpose: the hand-authoring `Ignitor.mul(Double)` short-circuits at unity
  unconditionally, control-rate survivor included, because a Kotlin caller writing a literal `1.0`
  has said "no multiply", while the DSL door's 1.0 may be a slot nobody wrote. The fold's safety
  argument (a spine survivor cannot emit a non-finite sample from finite input) is a rule for NEW
  nodes, not an audited invariant: the FEEDBACK sources are the known exception and must stay that
  way, because `Pluck` and `SuperPluck` write `delayLine[writePos] = filtered * decayVal` with
  `decay` read raw off `Slots.decay`, so `oscp("decay", 10)` diverges to an infinity and then to
  NaN. That divergence is authored character and the Motor stays raw.
- **The `Affine` arm is not optional.** Every registered tree renders OPTIMIZED, and the pass
  rewrites a bare `x.mul(k)` into `Affine(x, -0.0, k, -0.0)`. Gating `Times` alone would leave the
  `mul` row unable to fire on the shipping path. The absent-addend test now has ONE home,
  `IgnitorDsl.Affine.isAbsentAddend`, because the optimizer WRITES that encoding and the build
  READS it; a spec row runs the real `optimize()` output through the gate so a change to the
  encoding cannot silently stop the row firing.
- **The envelope is INVERTED from the plan's sketch and stays ungated.** Today's voice strip runs
  its VCA on EVERY voice with `AdsrDef.defaultSynth` when the pattern sets nothing, so the classic
  tail's ADSR has to be built by default; an explicit `adsrOff` slot is what will switch it off.
  **Two of its knobs got a NaN guard in this step, and the unity-`mul` fold is why.** Of the five
  ADSR knobs the three TIMES survive a non-finite value by accident of `Double.toInt()` being 0 for
  a NaN, so a NaN-timed stage has no frames. `sustainLevel` and `expK` did not: `coerceIn` is the
  identity on a NaN, the level multiplies every sample and `expK` reaches `adsrExpShape` the same
  way. Before the fold, a `pregain` at unity after an envelope kept `TimesIgnitor`'s `safeOut` and
  the voice went SILENT; after it the NaN would travel, and "a later stage guards it" is false for
  a pregain placed at the end of a tail (the plan then had `classic()` put it there; it landed at the SOURCE
  instead, step 6 commit 2, where a later stage does guard it). `AdsrIgnitor.finiteOr`
  substitutes `ADSR_SUSTAIN_LEVEL` and `ADSR_EXP_K` at the read. It is not a new clamp and the
  Motor stays raw: every finite value passes through untouched. `ADSR_SUSTAIN_LEVEL` is 0.7, the
  ignitor door's own default, deliberately NOT `AdsrDef.defaultSynth.sustain` (1.0), which is the
  strip VCA's, and the substitution is the ignitor envelope's alone (the strip path has no
  non-finite guard at all). It is not merely "completing the coercion that was there": `finiteOr`
  runs BEFORE `coerceIn`, which is invisible on a NaN and decides the INFINITIES, where `coerceIn`
  had a real answer and this overrules it. A `+Inf` sustain used to hold at the 1.0 rail and a
  `-Inf` at the 0.0 rail; both now read as unset and take 0.7. Deliberate, and it is the house rule
  (`/dsl-design` section 4) the gate applies to every knob it tests.
  Separately, a non-finite `releaseSec` used to poison the subtree's release TAIL. `maxTail` is
  `if (a >= b) a else b` and a NaN loses every comparison, so it wins ONLY as the second argument
  (`maxTail(NaN, 2.0)` discards it, `maxTail(2.0, NaN)` returns it), and `VoiceFactory`'s
  `ignitorTailSec > resolvedAdsr.release` is false for one. So the shapes that broke had the
  non-finite release on the RIGHT: `s.adsr(release = 2.0) + s.adsr(release = NaN)`, because the
  build accumulates the left operand first, and the commoner CHAIN
  `s.adsr(release = 2.0).lowpass(...).adsr(release = NaN)`, because the Adsr arm builds its inner
  before it reads its own release. Both cut the voice to the strip's release. The Adsr build arm
  now contributes no tail for a non-finite release. What is still step 3c's is the `adsrOff` slot.
- **`onepole` moved INTO the tree.** `IgnitorRegistry.createExciter` used to read the bag and test
  `> 0.0` itself, the one stage gate in the engine that was not a door's own. It now hangs an
  `IgnitorDsl.OnePoleLowpass` with a shared `Param("onepole", 0.0)` leaf on the optimized tree and
  lets the build decide, so the rule has one home. Cost: one immutable node per note-on.
- **The build cache needs no key change, verified by caller search.** `IgnitorBuildCache` is
  constructed in exactly one place (`buildExciter`) and `buildExciter` has exactly two production
  callers: `IgnitorRegistry.createExciter`, which `VoiceFactory` runs per note-on, and
  `KatalystSlots`, per knob resolve. So a cache never spans two voices and the gate's decision is
  constant for its whole lifetime. `IgnitorGateSpec`'s different-graphs row is the tripwire under
  that, for the day someone wants a cross-voice cache.
- **Six behaviour changes, all deliberate, none reached by the corpus.** The gate is a fold in
  most rows but not in all, and the honest list is:
  1. **`Distort` at or below 0** now does nothing. That node is `drive(amount).shape(shape)` and
     only the DRIVE half ever bypassed, so the tree's chosen shaper stayed on the signal at unity
     gain: modelled on a 220 Hz sine through the real chain, "soft" is -1.77 dB, "tube" -6.24 dB at
     16.8 percent THD, "gentle" +0.64 to +5.21 dB, "zerosquare" +1.55 to +16.83 dB at 37 percent
     THD, and "rectify" removes the fundamental altogether. It is a LEGACY node: neither authoring
     door builds it (both spell `distort` as `Shape(Drive(...))`) and the only production site left
     is `WarmupVocabulary` at 0.3. (Superseded: since phase 3 step 5 it is `classic()`'s distort
     stage, and since step 4, 2026-09-25, it runs the strip's law through `DistortionCore`.) This does NOT reopen ledger W5's gate-flip pop, because W5 is a
     MODULATED amount crossing 0 and a modulated amount is never a leaf.
  2. **`Drive` at a non-finite amount** used to render ALL-NaN, and that is a closed hole, not a
     change we chose: `amt <= 0.0` is false for a NaN, so the gain was `10^(NaN * 1.2)`. Step 3
     would have walked into it, because a `classic()` distort slot defaulting to `SLOT_UNSET` wires
     exactly this node. At or below 0 the same row is a true fold. `Shape` cannot be gated at all,
     having no amount knob, so which node `classic()`'s distort stage becomes is a design question
     for decision D2.
  3. **An authored `onepole(0)`** on a tree node was a 5 Hz lowpass (`bilinearK` clamps to
     `[5, Nyquist - 1]`), not a bypass, and is now a bypass. The registry path always treated it as
     off, so the two agree now.
  4. **The four SVFs at a non-finite authored cutoff** used to be a 1 kHz filter through the same
     guard and are now absent.
  5. **The unity multiply drops one `safeOut`** on the surviving SIGNAL, which fires only on a
     sample that is already NaN or past `SAFE_MAX`. In the `coarse` band `(0, 1]` the same shape:
     the engaged loop latched through `nanGuard()`, so a non-finite upstream sample came out 0.0
     and now passes through.
  6. **A NaN `sustainLevel` or `expK`** rendered NaN (or silence, behind a unity multiply) and now
     renders the knob's own default; an INFINITE `sustainLevel` moves too, from the rail `coerceIn`
     put it on (1.0 for `+Inf`, 0.0 for `-Inf`) to that same default, because the substitution runs
     before the coercion and unset reads as unset rather than as a rail. **A non-finite
     `releaseSec`** reported a release tail that swallowed a sibling's or an inner envelope's real
     one and now reports none. Holes closed, and listed here because they ARE changes of what those
     inputs produce.
- **Identity, proven:** all 18 corpus rows (15 built-in songs, 2 `FrozenSongs`, 1 `FrozenPieces`)
  render bit-identically at 256 cycles with the wall-clock seeds pinned, base commit against the
  tree, and rendered again after every review round: rounds 1 and 2 changed production code, and
  rounds 3 and 4 changed only comments and specs but were re-rendered anyway. Four runs, all
  agreeing. Four engagement controls, each widening one gate
  row, move 11, 8, 2 and 1 of the 18, so the net sees every row that the corpus can reach.

## A material change MORPHS the resonator bank (2026-09-20)

Katalyst step 5c-10, a SOUND CHANGE under the 5c listening checkpoint, and the only one of the
5c steps whose law is still the MAINTAINER'S to choose: both paths are live and the choice is by
ear, per stage (`docs/tasks/katalyst-dsl.md`, "Morph or output crossfade is chosen per effect BY
EAR"). Until 5c-6 a new body or vowel was a second bank crossfading over the old one; now the bank
IN SERVICE travels its bands to the new material's, which for a vowel sweeps like a mouth.

- **The law, as decided:** bands pair BY POSITION (band n to band n), never by nearest frequency;
  FREQUENCY and Q travel in LOG space, the GAIN linearly, over `KNOB_GLIDE_SECONDS`; a band that
  exists on ONE SIDE ONLY keeps its frequency and fades its gain to or from 0 in place; a band
  arriving on a slot another band left starts COLD; the bank has a fixed preallocated capacity
  (`ResonatorBank.MORPH_CAPACITY = 8`, the widest shipped table; every vowel has 5). The gain moves
  per SAMPLE, the coefficients once per BLOCK with the BLOCK as the ramp length. The landing is
  exact to within the RESOLUTION OF THE LOG AXIS: the last block retunes from the un-logged
  target, because `exp(ln(x))` is x only to within an ulp; and because `ln` is not injective on
  doubles (seven share `ln(1900.0)`), a target a few ulps from where a band already stands reads
  as no move, is skipped, and lands about 3e-16 relative off. No shipped table asks for a move
  that small. A band whose frequency AND q are unchanged is not retuned at all (exact: it
  already stands there), which spares the `tan` for every band that only fades out.
- **The morph allocates NOTHING.** The target crosses as three parallel `DoubleArray`s and a
  count, filled into a scratch the host allocated with itself; the gain rules were split out
  (`LowPassHighPassFilters.bodyGain` / `vowelGain`, with `bodyBand` / `vowelBand` now those plus
  the raw freq and q) so the host can write a gain without building a `Band`.
- **Where the morph applies, and where the 5c-6 crossfade stays:** only a change of the MATERIAL,
  with `wet` and `floor` unchanged, on the pair the swap still converges on
  (`KatalystFilterSwap.isTarget`), and only while the material fits the stage's scratch. ON, OFF,
  a `wet`/`floor` change, a pair that is fading out or gone, and a material wider than the bank
  all keep the output crossfade. The blend lives outside the bank, so a bank morphing under a
  jumping blend would click where it clicks least today.
- **A material change reaches the stage only on an OWNER HANDOVER** (the 5c-9 lesson, paid for
  again here): a voice's material is fixed at note-on, so the stage sees a change when the orbit's
  lease turns over to a voice carrying a different one. A pattern whose material period divides
  the handover period hands the owner the SAME material every time and changes nothing at all:
  `note("c3*8").legato(2.0).body("<wood glass>*8")` drove ONE morph in twelve seconds. Overlap the
  notes only just (`legato` about 1.05 to 1.2) so every note owns in turn, or use an odd number of
  materials. Count the morphs before believing a fixture: a temporary `println` on the taken morph
  is the whole instrument, and it is the same trick the state-machine plan calls an enter counter.
- **The four measurements, made BEFORE it was built** (a Python copy of `SvfBPF`, the click metric
  = peak 0.7 ms RMS above 8 kHz re the signal RMS; the "staircase" = the same peak taken on the
  residual against an IDEAL per-sample glide, which divides the intended sweep out):
  (a) a Q 140 band gliding 800 to 1600 Hz per block, 50 ms: a SNAP measures -62.1 dB click and a
  375 Hz comb at -39.6/-51.0 dB; the shared 32-sample ramp (`FILTER_SMOOTH_SAMPLES`) -91.8 dB and
  -41.1/-57.1; a BLOCK-LONG ramp -108.1 dB and -60.3/-94.3, which IS the ideal per-sample glide
  (-108.0, -60.7/-95.5). So the bank got its OWN ramp length, the block, and the shared constant
  was left to the `lpf` envelope.
  (b) A Q glide 60 to 130 with the shipped fold (the gain carries the clamped q, the tap is `k*v1`)
  against the `v1` form (the un-normalised tap, no q in the gain): click -121.2 against -150.4 dB,
  the two outputs 0.0009 apart, -76.9 dB re peak. The `v1` form buys 29 dB on an artifact 121 dB
  down and would need a second band kind in the hot loop AND change the arithmetic of every
  settled vowel (the 5c-3 regrouping lesson), so the FOLD STAYS.
  (c) A band fading in from gain 0: cold start -121.8 dB, a warm slot from 400 Hz -122.0, a gain
  step control -59.1. The fade-in masks the cold start completely, so a gain-0 band may be skipped.
  (d) The band gain per SAMPLE against per BLOCK: -90.3/-88.9/-81.2 against -36.7/-35.3/-27.6 dB
  over three swings. The per-sample ramp is not optional.
- **Mid-ramp stability with q MOVING** (5c-3 checked a frequency ramp only, where the blended
  radius never exceeds the endpoints'; with q moving it can). Three numbers, kept apart because
  the first cut of this entry ran them together: over 20,000 random (f, q) pairs blended from
  endpoint to endpoint, the HIGHEST radius seen is 0.999993, and that is an ENDPOINT'S OWN radius
  (f 20 Hz, q 197: a 3 s ring), not an excess; the LARGEST EXCESS over both endpoints is +0.050,
  and it sits at an absolute radius of 0.925, nowhere near 1. Restricted to the trajectory the
  code actually produces, one block's worth of travel per ramp, the largest excess over 4,000 full
  morphs is +0.017. Review round 1 ran its own per-block scan and found exactly 0.0; the two are
  different scans over different (f, q) draws, not a disagreement, and the honest statement is
  that a per-block excess is somewhere between nothing and a few hundredths. Stable, with room to
  spare, in every form of the question.
- **Artifact measurements, effect level** (band-limited saw 110 Hz and a three-saw chord, both to
  3 kHz, 44.1 and 48 kHz; click above, plus the low-frequency check = peak 20 ms RMS below 60 Hz re
  the signal, which needs an 8191-tap FIR or the 110 Hz fundamental leaks into the band). These
  rows drive the stage DIRECTLY, with no voice lease between, so every one of them really changes
  the material; the two modes differ on every row but the static controls, which is the proof.
  Both modes, a static control first: body-static and vowel-static are IDENTICAL in the two modes
  (-148 to -160 dB), so a settled bank costs nothing and sounds the same.
  Click, crossfade against morph: body-single -94.9..-105.0 / -90.8..-107.0; body every 125 ms
  -85.8..-88.6 / -82.1..-88.7; body every block -80.6..-84.3 / -81.3..-84.2; the band-count case
  -91.8..-102.0 / -92.4..-115.8. Vowel: single -99.1..-111.5 / -107.1..-123.6; every 125 ms
  -86.9..-91.3 / -102.2..-107.8; every 62.5 ms -85.4..-88.9 / -104.2..-107.0; every block
  -85.6..-89.1 / -100.0..-102.4. On these rows, which are the large F1/F2 moves of the a-e-i-o-u
  sweep, the vowel morph is 13 to 19 dB BELOW the crossfade (it never switches a bank at all).
  **That is not universal:** review round 1 measured six further vowel pairs where two are a wash
  (220 Hz, i to a: -3.4 dB; 220 Hz, a to u: +0.5 dB), so the gain belongs to the big moves. The
  body click rows are a wash in both directions.
- **The morph's low end, the one row that is worse, and why.** Below 60 Hz the body morph measures
  11 to 16 dB ABOVE the crossfade on a wood-to-glass change (single: crossfade -52.4..-59.0
  against morph -41.7..-46.8; every 125 ms: crossfade -48.7..-51.2 against morph -32.6..-34.9). **The mechanism is the SWEEP'S
  OWN ENVELOPE MODULATION, and it is generic**: a resonance travelling 50 ms amplitude-modulates
  whatever it passes, and the modulation's own spectrum lands under 60 Hz. It is NOT the crossing
  of wood's 100 Hz mode over the source's 110 Hz fundamental: review round 1 measured the same
  ratio with nothing at all under the travelling band (saw 440, sine 1500), and a Python rebuild
  of both laws from the Kotlin confirms the sign on every source (saw 110 +6.7 dB, saw 440
  +0.8 dB, sine 1500 +25.7 dB, saw 55 none; the per-source magnitudes differ between the two
  reconstructions, the direction does not). What the fundamental decides is the ABSOLUTE level,
  which is what decides audibility: only the source whose fundamental sits in the travelled range
  reaches -31 to -35 dB re the signal; sine 1500 reaches only -68 dB, and a source whose own low
  end swamps the band (saw 55) shows no difference at all. The vowel is the mirror image: its
  morph is 2 to 6 dB WORSE below 60 Hz on four of review round 1's six rows, at -55 to -71 dB
  absolute, which is inaudible. Where nothing travels in frequency the morph is far better
  (the band-count case: crossfade -46.0..-56.9 against morph -62.9..-73.8), and under a change
  every block, where a retarget never lets a band travel far, it is better too: crossfade
  -39.7..-42.9 against morph -48.0..-49.7.
  **This is the row the maintainer should judge the BODY by ear on.**
- **Songs** (15 built-in and 2 frozen, 256 cycles, 48 kHz, raw post-master doubles, wall clock
  pinned): with the morph OFF, all 17 are BIT-IDENTICAL to `ad9f0d64`, so nothing the step added
  besides the morph changes any sound. Measured TWICE, the second time after the review round 1
  fixes, because those touched `clampSvfCutoff` / `clampSvfQ` and the split-out gain rules, which
  every voice filter in the engine goes through and not only this bank: the capacity and its
  coercion, the bank factories, the `q` var, the fresh flag, the three-array morph door, the
  `retune` skip and the extracted clamps together move not one bit of one song. Both sides ran in
  throwaway worktrees, so the tree under review was never the instrument. With the morph ON, 15 are bit-identical and TWO differ, both the ones whose
  material changes on a LIVE orbit: Seltsamere Dinge (max difference -21.7 dB re its peak, first
  at 7.85 s, per-100 ms difference median -99.5 dB and p90 -60.1, so it sits at the vowel changes
  and nowhere else) and frozen Stranger Things 2026-07-03 (-27.5 dB re peak, first at 7.49 s,
  median -56.3 dB, p90 -29.9: its orbit 1 carries two superimposed voices with wood and glass and
  changes constantly). Synthkura is unchanged because its 443 handovers are ON/OFF edges, and Der
  Schmetterling because its bodies sit on different orbits: neither is a material change on a
  live target. Once a bank's integrators diverge they never re-converge, so 98 % of samples differ
  numerically; the LEVEL is what the numbers above report.
- **Cost:** three timed renders each side, 60 cycles at 30 rpm, on cases that morph every 125 ms.
  The morph is the MORE expensive path, not the cheaper one: +1.5 % on the body case and +2.8 % on
  the vowel case against the crossfade (and +2.5 to +6.7 % on an earlier pair of cases that change
  every 250 ms). It holds one bank instead of several, but it pays a `retune` per MOVING band per
  block (a `tan` each) and a per-sample gain ramp for the whole 50 ms. Nothing when settled.
  The crossfade path itself got slightly heavier (every bank now preallocates 8 SVFs and nine
  small arrays where HEAD built `bands.size` SVFs and one array): measured against HEAD on the
  same two cases, about 950 installs per render, **+1.0 %**, inside the run-to-run spread, so the
  arrays were left at the capacity.
- **Switching modes:** `KatalystBodyEffect.MORPH` and `KatalystFormantEffect.MORPH`, one constant
  per stage. Flip and rebuild. Both are `true` as proposed; the loser is DELETED with the choice,
  together with the constants and the hosts' `morph` parameter.
- **Guards, each mutation-checked (31 mutants, 30 red).** `ResonatorBankMorphSpec` (oracle: the
  law written from scratch over bare `SvfBPF` instances driven by hand) has one row per rule:
  the whole trajectory, log frequency, log q, linear gain, pairing by position against a reversed
  target, a band the target drops, a band that arrives cold, the exact landing, a retarget from
  where the bands stand, the fresh snap, a capacity below the band count being raised, the
  capacity refusal, every shipped table fitting the capacity, a non-finite band on the SVF's own
  clamps, plus three rows that pin `retune` and `resetState` against a filter CONSTRUCTED at the
  numbers. The hosts add: a material change with an unchanged wet morphs, a band-count change
  morphs, a WET change still crossfades, a material wider than the bank installs and crossfades,
  a change on a bank that is FADING OUT installs a fresh one, and with the morph OFF the change
  crossfades exactly as in 5c-6. The ONE surviving mutant is the `retune` skip removed, which is
  the correct outcome for an optimisation: the skip is behaviour-neutral by construction (the
  opposite mutant, skipping every band, is red).
- **Two lessons for the next glide.** (1) A metric built on an FFT brickwall filter reads its own
  edge artifact: the first cut of these measurements reported -19 to -50 dB clicks on runs that
  were at -100 dB, because the brickwall was applied to a WINDOW of the signal. Use a
  windowed-sinc FIR on the whole run, cut its taps off both ends, and give a 60 Hz band a far
  longer FIR than an 8 kHz one. (2) An oracle built from a production primitive cannot test that
  primitive: the morph oracle drives the same `retune`, so a mutant that broke `retune` passed
  every bank row until three rows were added whose oracle is a filter CONSTRUCTED at the numbers.
  And an exactness that goes through `retune` may not be observable at all: `retune` turns the
  target into an increment, `(target - current) / ramp`, and a one-ulp difference survives that
  division only about a quarter of the time at a ramp of 128, so the landing row PROBES candidate
  targets until it finds one that lands observably rather than assuming any will.

## The phaser and the duck switch and change without clicking (2026-09-20)

Katalyst step 5c-9, the last two orbit stages that still switched hard. A SOUND CHANGE at the
stage's own edges (a `.katp` that moves a knob, a `phaser.wet` or `duck.depth` that reaches 0, an
owner handover where only one of two patterns has the stage). The swap's ramps around the duck
(`Cylinder.processDuck`, `KatalystDuckEffect.takeOver`) are untouched: they were always right, and
they never covered these edges.

- **Measure first.** Metric: peak 0.7 ms RMS above 8 kHz and peak 20 ms RMS below 60 Hz, both
  against the signal RMS, on sources band-limited to 3 kHz (saw 110 Hz, a three-saw chord at
  220 Hz, a saw bass at 73 Hz), at 44.1 and 48 kHz, every knob swept its full range in BOTH
  directions. **The phaser has a high own floor, -50 to -61 dB**, set by the block-rate kinks of
  its interpolated alpha; the duck's is -49 to -53. Read every number against the floor of its own
  row, never against the compressor's -81.
- **What glides, and on which axis.**
  - *Phaser `wet` and `floor`*: they feed the C4 law's two coefficients, which are MEMORYLESS, so
    they are LEVEL knobs (jumps -14.8 to -49.8 dB). The output is LINEAR in the pair, so ramping
    both per sample IS a linear crossfade from the old settings to the new: ONE mechanism for the
    `wet` knob, the `floor` knob, the OFF edge and the ON edge, because OFF is exactly
    `dryC = 1, wetC = 0`, which is where the depth gate's target sits. No separate fade was built.
  - *Phaser `center` and `sweep`*: the loudest jumps of the whole stage (-9.7 dB on a chord for
    100 Hz to 18 kHz), because the allpass multiplies its INPUT by alpha, so a step in alpha is a
    step in the output. They glide PER BLOCK (a per-sample glide would put `tan` in the hot path),
    and `PhaserCore.prepareBlock(frames, centerTo, sweepTo)` computes alpha at the block's start
    from the breakpoint IN FORCE and at its end from the new one, so alpha is continuous across
    the seam. With the breakpoint unchanged it is `alphaAt(lfoPhase)` either way, bit-identical.
  - *Phaser `rate`*: NO glide. It only scales the LFO's phase increment and the phase carries on;
    0 to 20 Hz both ways measured at or under the floor, HEAD and tree identical.
  - *Duck `depth`*: a LEVEL knob in the rising direction (the detector's attack is instantaneous,
    so a depth step is a level step: -17.0 to -37.2 dB); the falling direction was already at the
    floor because the release smooths it. It glides PER SAMPLE on its own axis, the target gain
    being linear in depth.
  - *Duck `attack`*: NO glide, the release envelope's time constant, 1 ms to 1 s both ways at the
    floor (the compressor's attack and release measured the same).
  - *Duck sidechain ORBIT*: NO mechanism. The envelope belongs to the effect, not to the buffer,
    so a changed orbit carries the reduction across (at the floor, measured). The other direction,
    a silent trigger replaced by a loud one, reads -19 to -39 dB on BOTH sides and EQUALS a
    control where nothing but the sidechain changed: that is `Ducking`'s instantaneous attack, the
    same step the orbit's own onset makes, and it is not this stage's to smooth.
- **The switch, per stage.** The phaser's OFF drops the cascade only once the coefficient glide has
  landed on identity (entering Off early IS the click); the duck's OFF rides a WEIGHT down,
  `gain = 1 + w * (g - 1)`, so the reduction reaches exactly 0 dB while the envelope keeps running,
  and only then does it let go of the envelope and the source orbit. No dry copy for the duck:
  ducking is one multiply, so scaling the reduction IS blending with dry.
- **The duck's "first initialisation is instant" window is the ORBIT's first block, not its own.**
  The duck runs in a later pass than the chain and not at all while it is off, so its `process`
  cannot be the tick; `KatalystChain.process` calls `KatalystDuckEffect.orbitBlockRan()`. Without
  it a duck that engages at bar five SNAPS to full reduction on a sounding orbit, which is the
  -17 dB click. The phaser needs no such thing (it is in the serial list), but it does need an
  explicit `fresh` flag for the BREAKPOINT: `KnobGlide`'s snap sets the glide's value while the
  cores still hold their built-in 1 kHz, so the first block would start alpha there.
- **Measured, HEAD `01d39fb6` to the tree** (HF above 8 kHz, dB re signal; the floor in brackets):
  phaser wet ON/OFF -44.9..-14.8 -> -58.2..-50.7 (floor -52.8..-50.2); phaser floor -49.8..-24.5
  -> -60.3..-49.6 (-58.5..-51.3); phaser centre -51.7..-9.7 -> -55.0..-48.1 (-56.9..-51.0);
  phaser sweep -47.4..-18.0 -> -59.1..-49.1 (-61.4..-57.2); phaser handover (on/off/on over three
  blocks) -31.4..-15.2 -> -60.0..-52.7 (-60.0..-52.7); duck off -45.8..-18.7 -> -50.5..-47.4
  (-52.8..-49.1); duck on -53.1..-16.8 -> -52.6..-47.1; duck depth rising -52.8..-16.8 ->
  -52.6..-47.1; duck handover -16.4..-6.0 -> -49.5..-48.3. Below 60 Hz the same rows go from
  -20.4..+0.5 to -49.3..-5.9 (what is left is the level change itself, spread over the glide, and
  on the bass row the source's own low content).
  **What did NOT reach its floor: the phaser's `sweep` and `centre` glides sit 6 to 9 dB above it**
  (absolute -48 to -55 dB, the same class as the phaser's own sound while it sweeps). That residue
  is the MODULATION of a fast sweep, not a kink: a breakpoint crossing the spectrum in 50 ms makes
  sidebands, and no axis removes them. A one-block alpha ramp with no glide was measured as the
  alternative and is WORSE where it matters: HF a few dB better, but below 60 Hz -14.8 dB against
  the glide's -37.5 on the same row, worse than HEAD's -24.4. The glide stays.
- **Songs:** 15 built-in songs and 3 frozen pieces, 256 cycles, raw doubles, wall clock pinned,
  HEAD `01d39fb6` in a throwaway worktree: **17 of 18 bit-identical**. No song ducks at all. Three
  use the phaser; two of them (Stein um Stein, the frozen Schmetterling) set it once and are
  unchanged. **The Synthsale Pipers' Last Rave** differs from 66 s to 246 s, worst -26.3 dB re the
  local peak (-31.8 dB re the song's peak): orbit 5 carries `phaser(wet = saw.range(0.3, 0.6)
  .slow(16))`, a continuous wet that moved in steps and now glides, and orbits 5 and 6 hand the
  phaser between owners that have one and owners that do not. Stripping the three `.phaser(...)`
  calls from BOTH sides makes the render bit-identical, so the phaser is the whole of it.
- **No state classes for either stage** (the plan's complexity rule, the `KatalystGainEffect`
  precedent). The phaser's situations are `KnobGlide`'s snap flag, countdown and rest plus
  `Phaser`'s own `engaged` latch next to the cascade it guards; the duck's Off is `ducking` being
  null, the field the stage already had and the one `Cylinders` reads through `duckCylinderId`.
  The four questions of `docs/plans/effect-state-machines.md` are answered in each class's KDoc.
- **A life can end without a fade, and its WEIGHT has to be put down anyway** (review round 1,
  a MAJOR). A duck whose sidechain orbit does not resolve gets no pass at all (`Cylinders`
  `continue`s), so the orbit's mix already goes out unducked and a switch-off there has nothing to
  ride down; `endLife` runs with the weight normally at **1.0**, and leaving it there installs the
  NEXT switch-on at full reduction, because `retarget(1.0)` on an unchanged target is free.
  Reached mid-fade it froze the glide outright (`orbitBlockRan` only advances a settled one), so
  the next life ducked at a wrong, frozen weight for good. `endLife` therefore ends with
  `KnobGlide.settleAt(0.0)`: at 0 with nothing left to travel and **without re-arming the snap**,
  which would make that next switch-on instant, the same click by another door. Reachable in an
  ordinary song shape, not a corner: an orbit that goes inactive between two pad notes takes that
  path, and the listening case `duck-depth` moved by **-11.7 dB re peak** when it was fixed.
  Lesson for the next stage: **every door into a terminal state has to establish the SAME
  precondition; a precondition that only one door happens to satisfy is not one.**
- **A handover carries the WEIGHT and the DEPTH, not only the envelope** (review rounds 1 and 2).
  `takeOver` does `KnobGlide.carryOver` on both, which copies value, target, start and countdown
  and spends the snap window, so the arriving stage's first `configure` turns each glide around
  exactly as a returning owner's would. Without it on the WEIGHT, a stage arriving on a duck that
  stood at `w` snapped to full weight: at depth 0.95 and `w = 0.1` that is -25 dB in one sample,
  and a cached arriving chain whose snap was spent jumped the other way, ramping up from 0.
  Without it on the DEPTH, `Cylinder.beginFade`'s `next.reset()` re-armed the arriving stage's snap
  and its own depth landed in one sample: two chains on one orbit at depth 0.2 and 0.9 on a
  saturated sidechain measured **-38.5 to -18.0 dB against a floor of -52.8, and -50.7 to -49.3
  after**, at the floor. Only the DEEPER direction ever stepped; the shallower one is the
  detector's own release and was already at the floor (-49.9 to -48.9 both before and after).
  A swap between two chains of the SAME depth reads exactly the floor, so the handover path itself
  adds nothing: the two steps were the only things on it.
- **The sidechain ORBIT switch is an OPEN question, measured** (review round 1, MINOR). Onto a
  quieter or silent source it is at the floor (-50.4 to -49.2 against -52.8 to -52.1): the release
  smooths it. Onto a LOUDER one it is in the **click class**, -21.8 to -19.0 dB on a saw and
  -40.8 to -19.0 on a bass, and it stays there when the new source was ALREADY SOUNDING at 0.2
  rather than silent, so the first round's "it equals the same orbit's own onset" is true about
  the magnitude but does NOT make it harmless: an orbit-to-orbit switch brings no new sound into
  the mix to cover it. Smoothing it means blending two sidechain sources, which is a decision
  about what a sidechain switch MEANS. Not taken here.
- **Guards, mutation-checked** (17 mutants, 16 red, 1 replaced): `KatalystPhaserEffectSpec`
  (settled equals the bare DSP bit for bit; the first initialisation instant, breakpoint included;
  the wet line per sample with an exact landing; the OFF edge still wet until it lands and the dry
  mix from the landing sample on; the ON edge within a quarter of the full effect on its first
  block; a breakpoint change continuous at the seam; the breakpoint line per block; rate in force
  at once with a continuous phase; a gated owner not writing the kernel params; a reset mid-glide
  snapping the next life; the gate), `KatalystDuckEffectSpec` (settled equals the bare `Ducking`;
  the weight line per sample BIT for bit, both directions, and the life ending only on the landing
  block; the reduction shrinking every block of the fade; the instant first initialisation and the
  fading later switch-on; a return turning the fade around; the depth line against a bare
  `Ducking` driven per sample; attack in force at once; a changed orbit bit-identical to an
  unchanged one; the handover; a second life snapping its depth; the vanished sidechain; reset),
  `KatalystChainBuilderSpec` (the chain gives its duck the per-block tick), `KnobGlideSpec` (what
  `settleAt` and `carryOver` promise, the rising carry-over and the countdown clamp).
  Review round 1 added
  five rows and five mutants, all red: the weight landing on 0 through the pass-less door and the
  next life still fading in (both red without `settleAt`, and the second also red on a
  `reset()` + `retarget(0.0)` that re-arms the snap); the handover carrying the weight; and
  **three rows at `floor < 1`, which is the only place the DRY coefficient moves** (at the default
  floor of 1.0 the C4 law pins it at 1 for every wet, so the mutant `val dryC = dryTo` had
  survived the whole spec). The mutant that survived round 1's first pass was equivalent code (writing the kernel params in the ON arm, where they are written
  anyway); its real form, retargeting the breakpoint glides in the GATED arm, is red.
  The bit-for-bit duck rows needed the oracle's `line()` to use `1.0 / blocks` as a reciprocal:
  1/17 is not a binary fraction, and `x / 17` and `x * (1 / 17)` differ by a rounding.
- **Timings** (medians of three, same machine, 64 cycles): the phaser-heavy Last Rave 4056 ms HEAD
  against 4047 ms tree; a duck-heavy piece (four ducked orbits on one kick) 1386 ms against
  1373 ms. Within noise.
- **Two shapes of the duck's lifecycle that a reader will otherwise re-derive** (review round 2).
  (1) The switch-off arm has only THREE live doors into the end of a life, not four: a switch-off
  while the snap window is open is unreachable, because getting there needs `duckedLastBlock`,
  which only a finished `process` sets, and that block advanced the fade and so spent the window.
  The arm was deleted (the plan's "delete what no row guards"; no row CAN guard dead code).
  (2) What closes the "first initialisation is instant" window depends on which side of the switch
  the stage is on: while OFF the ORBIT's blocks close it, which is what makes a duck engaging at
  bar five fade in; while ON only the first duck PASS does, because `orbitBlockRan` deliberately
  advances nothing during a life. So a duck configured ON whose sidechain orbit never sounds keeps
  an armed window. No audible failure could be constructed from it (the first pass can only come on
  the block that creates the sidechain cylinder, whose own onset covers the reduction, or on a
  silent orbit), and spending the window while ON would change the bar-five case, so it stays and
  is written down instead.
- **`KnobGlide.carryOver` has a precondition, and the helper clamps rather than trusts it**: the
  countdown is in BLOCKS and the block count is per instance, so carrying a 19-block countdown into
  a 17-block glide would drive the value outside `[start, target]` (1.0 towards 0.0 lands on 1.118,
  which for the duck's weight is over-ducking). Unreachable today, one builder and one sample rate
  per cylinder, but it is a shared helper.
- **Two things the whole glide programme should know** (review round 1's audio reviewer, verified
  by modelling):
  - **A knob retargeted every block converges like a one-pole with tau about 49 ms**, so it is a
    low-pass on the knob: -3 dB at 3.2 Hz, -10 dB at 10 Hz. A `phaser.wet` or `duck.depth` wobbled
    faster than a few Hz LOSES modulation depth. That is a property of every glided knob, not of
    these two stages; it becomes reachable here because these are the first stages whose knobs
    people pattern continuously.
  - **The breakpoint axis question is closed with numbers.** An IDEAL per-sample breakpoint glide
    measures within 1.1 dB of the per-block one on every row, so the centre/sweep residue is
    modulation and a finer rate buys nothing. Log-Hz costs 12 dB above 8 kHz and buys 7 to 18 dB
    below 60 Hz; tan-warped is the mirror. **No axis dominates**, so linear Hz with a continuous
    alpha stands, and the next frequency knob need not re-open it.

## An orbit never deactivates while a voice plays on it; the fader glides (2026-09-19)

Katalyst step 5c-8, a SOUND CHANGE at two edges only: the group fader patterned through exactly 0
on a dry orbit, and every fader move (decided with the maintainer, signal-flow plan section 11,
"DECIDED 2026-09-19").

- **The fix is one condition.** `Cylinder.tryDeactivate(blockStart)` refuses while the orbit's
  `VoiceLease.isHeld(blockStart, blockFrames)`, after the silence grace and the tail test. The
  lease is held whenever ANY voice plays on the orbit (caller search: `lease.claim` has one caller,
  `Cylinder.updateFromVoice`, reached from `Cylinders.getOrInit`, which `SendRenderer`, the last
  stage of every voice pipeline, and `Voice.render`'s culled branch call every block a voice
  renders; a voice turned away from the lease is turned away BECAUSE it is held). Grace: held in
  the block after the owner's last check-in, lapsed in the one after that. The refusal holds the
  silence count at the grace instead of restarting it, so an orbit goes at the first visit after
  the lapse. `isHeld` is also what `claim` tests now (one home for the liveness rule). The block
  start reaches the cylinder through `Cylinders.processAndMix(fusion, blockStart)`, fed
  `PlaybackEngine.renderInto`'s cursor (the frame the voices claimed with).
- **Before (HEAD `4af2e010`), measured:** a muted orbit with notes was deactivated and reset every
  10 blocks (one orbit allocated), about 150 times in 8 s; the fader came back either as a
  ONE-SAMPLE jump (a reset landed at the owner's lapse, the next owner's first configure snapped:
  default ADSR, releases 0.05/0.08/0.13/0.16 s) or as a one-block ramp once the old owner lapsed
  (most releases). Both are reproduced by `CylinderFaderThroughZeroSpec` on HEAD (all three rows
  red at block 9; the audio row alone at block 20, full level where the law says 0).
- **The gain stage keeps no state classes.** Its three situations (fresh, ramping, settled; the
  level outlives them) are exactly `KnobGlide`'s snap flag, countdown and rest, so the stage is now
  a `KnobGlide` plus the unity skip; classes would copy the helper (the plan's complexity rule).
  The four questions are answered in its KDoc, each pinned by a row.
- **The ramp law:** a change glides per sample over `KNOB_GLIDE_SECONDS` in whole blocks (17 at
  44.1 kHz, 19 at 48 kHz), written from the block's end (exact landing), from where the fader
  stands on a retarget. It was one block, from the start: on 4 -> 0.01 that missed the target by a
  rounding in the last sample.
- **Measured** (HF: peak 0.7 ms RMS above 8 kHz, LF: peak 20 ms RMS below 60 Hz, both re the signal
  RMS; sources band-limited to 3 kHz). Effect level, saw 110 Hz, three-saw chord, saw bass 73 Hz,
  44.1 and 48 kHz, jumps 0/1, 1/0, 0.01/4, 4/0.01, -1/1, 1/0/1, a return after 3 blocks, 0.5/1:
  HEAD HF -58 to -79, LF -9 to -33; tree HF -82 to -100, LF -30 to -61 (the LF left is the level
  change itself, largest on the bass). Engine rows (48 kHz renderer, `<0 1>` on sine and saw
  notes): HEAD snap -17.7 / -18.6 dB HF, HEAD lapse ramp -60 to -66; tree -85 to -91 against the
  note-onset controls -95 to -101 (the saw row equals its control, -41). Ordinary orbits that end:
  same final deactivation block, bit-identical; a muted note's orbit goes 2 blocks later (the
  lease grace); intermediate resets while silent voices play are gone (52 and 120 per row became 4).
- **Songs:** 15 built-in songs and 3 frozen pieces, 256 cycles, raw doubles,
  wall clock pinned, HEAD `4af2e010` in a throwaway worktree: 15 of 18 bit-identical. No song moves
  the fader; all three differences come from orbits HEAD reset while a silent voice still played
  (velocity or gain at 0, or a culled release). **The Synthsale Pipers' Last Rave** differs
  audibly (163 to 237 s, worst -24.5 dB re peak, -39 dB RMS): orbit 6's supersaw plays at
  `velocity` 0 for a long stretch under a `phaser(rate = 1/13)`, and HEAD's resets restarted the
  phaser sweep from its clean slate each time (`Phaser.resetForReuse`), so the sweep stood somewhere
  else when the notes became audible; with the phaser stripped from both renders the difference
  falls to 3e-16 (orbit 7's reverb, delay and body, reset at 234 s on HEAD). **Synthsturm** (orbit
  5, the riser at `gain(saw.range(0, ...))` under a stack-wide reverb) and **Synthkura** (orbit 3,
  the sub-bass's silent release under a stack-wide reverb, delay and compressor, last 1.4 s)
  differ at -140 and -146 dB below peak: HEAD zeroed the reverb's `+ ANTI_DENORMAL` residue (the
  orbit's post-chain output read 1e-20 against the tree's 1e-19), and the master limiter's
  gain-skip branch turns that into at most 8e-8 of output, far under one 16-bit count. The
  sweep's continuity is the decided behaviour (the orbit is playing); the maintainer should hear
  the Last Rave section once.
- **Guards, mutation-checked:** `CylinderFaderThroughZeroSpec` (new: the muted
  orbit held while its voice plays and gone on the first block its lease has lapsed (two after the
  last check-in), a non-owner holding it
  after the owner ended, the fader's return by the glide against a reference render at unity, and
  the same hold through a whole `PlaybackEngineDispatcher` with and without a master chain);
  `KatalystGainEffectSpec` (the law over the full range both ways with an exact landing, the step
  bound, a retarget mid-glide turning from where the fader stands, a reset mid-glide snapping the
  next life). 13 mutants, all red: no lease condition; the refusal restarting the silence grace;
  `isHeld` without the one-block grace; `Cylinders` handing a stale frame; `PlaybackEngine` handing
  a stale frame on the fast path, on the master path, and one block late (each red only in its own
  path's row; a first version of the master row never reached the master path and let that mutant
  survive, fixed by registering a real master chain); reset keeping the level; reset not
  re-arming unity; a start-based ramp (red only on 4 -> 0.01, the pair it misses); every change
  snapping; a retarget starting from the old target (`KnobGlide`); the unity path not ending the
  fresh situation. The first three `CylinderFaderThroughZeroSpec` rows are red on HEAD at block 9.
- **Not a new cost (review round 1):** a culled or muted voice keeps its orbit active until its
  scheduled end (it renews the lease). HEAD processed that orbit just as long: every check-in set
  `isActive` again in `updateFromVoice`, so the orbit was reactivated the block after each
  deactivation and its chain ran every block, to the same final deactivation block (measured:
  the culled-release row ends on the same block both sides). What 5c-8 removes is the repeated
  `chain.reset()` and lease re-dealing in between. Do not "fix" a CPU regression that does not
  exist.

## The orbit compressor switches and changes by gliding (2026-09-19)

Katalyst step 5c-7, a SOUND CHANGE under the 5c listening checkpoint (decided with the maintainer,
`docs/tasks/katalyst-dsl.md` step 5c: "the compressor switches by gliding its gain reduction to 0 dB
over the same 50 ms"). No identity-only commit before it (complexity rule of
`docs/plans/effect-state-machines.md` section 2): the states and the glides arrived together.

- **The law.** `KatalystCompressorEffect` has three preallocated states, Off (the instance at rest, `compressor` reads null), Engaged
  (in place) and Fading (`out = dry + w * (compressed - dry)`, `w` linear over `KNOB_GLIDE_SECONDS`
  in samples, the filter swap's law with dry as the partner). OFF fades `w` to 0 while the instance
  keeps running on the live signal, and only on the exact landing (the output IS dry) does
  `Off.enter` reset the instance and forget that life. ON rewrites the reset instance's five
  knobs and fades it in from dry. A return
  either way turns the fade around at the weight it has, on the SAME instance (its envelope
  untouched), over one full fade. The first switch after construction or `reset` acts at once
  until a block has run (`fresh`, the `KnobGlide` snap), so a compressor set on an orbit's first
  block is bit-identical to before. `reset`/`retire` stay the synchronous HARD cut; `Cylinder.kt`
  did not change.
- **The glide is a linear blend with dry, not a dB ramp of the reduction.** Both land on exactly
  0 dB and are continuous; the blend needs no change to the compressor's inner loop and is the law
  every other orbit stage already switches by. Review round 1 compared the alternatives: a dB
  ramp is WORSE on a bass; a smoothstep weight is better only at about 30 dB of reduction (linear
  -58 on the bass, smoothstep -81); at 12 dB all three are at the floor. A safety net does not
  need it: kept linear, the smoothstep weight is the option on file.
- **The ON fade-in was MEASURED first, and built.** Without it a fresh instance is not a click
  (-67 dB or quieter above 8 kHz), but at an attack of 5 ms or less its envelope clamps the level at
  the attack's speed: 8 to 26 dB above the steady floor below 60 Hz (bass and saw at 6 and 12 dB of
  reduction). The fade-in leaves at most 8 dB, like the OFF glide; it cost no state (the return
  needs the fade toward 1 anyway). At a 20 ms attack it changes nothing: the reduction arrives
  after the fade, as on any note from silence. Flipping it off is one line (`Off.switchOn`).
  For the listening note: the fade-in lets a handed-over note through about 5 to 9 dB LOUDER for
  about 50 ms (the decided "ON glides in"), where HEAD clamped it within the attack.
- **Knob changes: threshold, ratio and knee glide PER SAMPLE; attack and release do not.** The gain
  computer is memoryless, so its three knobs step the gain the moment they change: a threshold jump
  measured -9 to -36 dB above 8 kHz, ratio -27 to -43, knee (envelope inside the knee) -34 to -52. A
  per-block glide left -31 to -69 dB (Python model of the classic path), the per-sample ramp reaches
  the steady floor. Attack and release are the follower's coefficients over a continuous state:
  their jumps sat at the floor both ways, so they apply at once. Mechanism: one `KnobGlide` per knob
  (whole blocks, exact landing, first value snaps; forgotten in `Off.enter`) and
  `Compressor.processGliding(from, to)`, a second loop body of the classic path that ramps the
  three per sample, written from the end. `Compressor.process` itself is unchanged; the gain curve
  moved into `gainReductionDb(inputDb, thr, slope, knee)` and the follower into `followEnvelope`,
  both inlined back into the old paths, so the master limiter computes the same doubles.
- **The ratio glides as its INVERSE** (review round 1, a MAJOR). The curve is linear in its slope
  `1 / ratio - 1`, not in the ratio, so a glide linear in the ratio bunched a wide change into one
  end: 100 to 1 at -41 dB, 20 to 1 at -56 (the reviewer's model). `inverseRatioGlide` moves
  `1 / ratio` and `processGliding` hands the curve `inverse - 1`; the settled path computes the same
  `1.0 / ratio - 1.0` it always did. After: every ratio swing at its HF floor in the engine, both
  ways (1 to 100, 100 to 1, 4 to 1, 1 to 4, 20 to 1.5, 20 to 1, 1 to 20, 2 to 8, 8 to 2; saw, chord,
  bass, 44.1 and 48 kHz): HEAD's jumps -16 to -37 dB, the tree -80 to -89 (floors -79 to -89).
  Wide knee swings (0 to 48 and back, envelope inside the knee) also land within 2.3 dB of the
  floor. **Wide RISING threshold swings do not quite:** -40 to 0 dB sits at -62 to -65 dB above
  8 kHz and -30 to -5 at -69 to -71 (floor -81 to -83); HEAD's jumps there were +3 to -17. The
  model reproduces it (-62.1 / -62.3) and splits it: releasing the same 25 dB by the linear blend
  gives -66 to -70, so part is the size of the change in 50 ms and part is the threshold law (the
  reduction moves linearly in dB, which bunches the amplitude change at the end of the glide).
  Falling threshold swings are at the floor. Open, not fixed: a law decision.
- **One `Compressor` per effect, built with it** (review round 1). The ON edge used to build one
  on the audio thread. `Off.enter` resets it (envelope to rest; the lookahead state too, though
  no orbit has one) and empties the settings cache, so the next ON writes all five setters; the
  constructor and the setters store the same coerced values and compute the same coefficients,
  and nothing on an orbit writes `makeupGainDb` (the one field `reset()` leaves alone). Proven by
  the spec rows that compare a new life against a FRESH bare `Compressor` bit for bit (after a
  reset, after a fade-out landing, with every knob different), and by the engine rows that
  re-enter ON repeatedly: bit-identical to the allocating version. One difference for a direct
  caller only: a non-finite knob keeps the previous value where a constructor takes its default;
  the writer never hands one. `compressor` reads null in Off.
- **`writeCompressor` is gone** (the effect's `configure(settings)` replaces it), and with it the
  open performance item: the knobs are written only when the writer hands a NEW settings object
  (it resolves one per owner map), so a running orbit costs one reference compare per block
  instead of five setters and about fifteen `exp()`. The cache (`applied`) is forgotten in
  `Off.enter` with the instance.
- **Measured** (effect level through `KatalystChain`, saw 110 Hz, three-saw chord and saw bass
  73 Hz, all band-limited to 3 kHz, 44.1 and 48 kHz; HF: peak 0.7 ms RMS above 8 kHz, LF: peak 20 ms
  RMS below 60 Hz, both re signal RMS, with each metric's steady floor). HEAD `089d6a47` to tree:
  off at 3/6/12 dB of reduction HF -10..-39 to -78..-86 (floor -81..-83), LF -5..-34 to -25..-39
  (floor -31..-36; the bass at 12 dB keeps 6 to 7 dB over its floor, the level change itself);
  off then on inside the glide (1, 4, 10 blocks) HF -10..-22 to -71..-83; handover every 125/250
  ms HF -14..-32 to -78..-82, LF -12..-26 to -25..-33; on during the fade-in then off HF -28..-86
  to -88..-101; threshold jumps -9..-36 to the floor, ratio -27..-43 to the floor, knee -34..-52
  to the floor, threshold patterned every 250 ms -9..-13 to -72..-74; attack and release jumps
  and steady compression bit-identical. Engine (48 kHz renderer): two sine patterns sharing an
  orbit, one compressed, HF -20.5 to -73.6 (control without compressor -77.1); the same with
  different compressor settings per pattern -19.8 to -73.8; saw handover and a threshold patterned
  per 16th -17 to -32 (their controls, the notes' own onsets, -34). The sine bass handover's LF
  row reads -16.8 to -16.0 against a control of -19.5, but that peak is NOT the compressor
  (review round 1): the tree's absolute LF peak (-25.55 dBFS at 2.012 s) equals the control's to
  0.01 dB, the d2 note's own onset 41 ms BEFORE the compressor switches on at 2.0533 s, and the
  gap is normalisation (compressed RMS 0.332 against the control's 0.497). With the control
  subtracted, the compressor's own LF at the ON edge fell from -20.5 to -34.0: the row improved
  by 13.5 dB. Its OFF edge went from -24 to -45.
- **Songs** (15 built-in, 3 frozen, 256 cycles, raw doubles, wall clock pinned): 18 of 18
  bit-identical. A per-orbit monitor found no compressor switching or changing while an orbit
  sounds in any of them: every compressor snaps on at its orbit's first block and is cut at
  deactivation, both unchanged. The rows above, rendered by the same harness, differ.
- **Cost:** a settled compressor is unchanged (one reference compare per block instead of five
  setters). Der Schmetterling 64 cycles and the two rows, three runs each side, interleaved:
  within noise.
- **Guards, each mutation-checked (25 mutants, all red; review round 1 re-ran them on the changed
  code plus 6 new ones, 31 of 31 red):** `KatalystCompressorEffectSpec` (oracle: a
  bare `Compressor` driven by hand plus the decided law; the knob row sets the bare compressor's
  knobs ONE SAMPLE at a time): first init at full weight, off glides then releases on the landing
  block and is dry bit for bit after it, on fades a fresh instance in, a return turns the fade
  around on the same instance, an off during the fade-in turns too, reset and retire snap the next
  life (no stale fade, no stale glide), the three knobs glide per sample while attack and release
  apply at once, an unchanged owner writes nothing, a wide ratio swing both ways (1 to 100, 100 to
  1; the oracle moves the ratio linearly in its inverse, and the old linear-in-ratio law goes
  red), a life ended by a fade starts the next like a new compressor (the reused instance: red
  without the reset, without a setter). `KatalystCompressorStateIdentitySpec` walks the table.
  `OrbitCompressorSpec`: a non-compressor owner taking a SOUNDING orbit leaves the instance in
  place through the glide and only then clears it (red for the hard cut and for a fade that never
  releases); the old row that passed through the fresh snap only is renamed "before anything
  sounded". Lesson from round 1: a row whose next life has a SLOW attack cannot see a fade-in or
  a stale glide (no reduction arrives inside the first 50 ms), which let two mutants survive
  until the next life got a fast attack.

## Body, vowel and the orbit EQ switch by fading from what sounds now (2026-09-19)

Katalyst step 5c-6, the filter swap's SECOND commit, a SOUND CHANGE under the 5c listening
checkpoint (decided with the maintainer, `docs/tasks/katalyst-dsl.md` step 5c, "how every orbit
stage switches" and "rapid changes").

- **The law.** Every edge of `KatalystFilterSwap` is a linear crossfade over `KNOB_GLIDE_SECONDS`
  (the 12 ms is gone). The pair in service and dry are fade partners: OFF (`clear`) makes the
  pair an OUTGOING entry and dry the target, ON makes dry outgoing. Each outgoing entry is frozen
  at the weight it has at that sample and ramps to exactly 0 over one fade of its OWN; the target
  takes the complement `1 - sum(outgoing)`, so the weights always sum to 1 and a change arriving
  mid-fade never drops a sounding bank. Off is entered only when the last weight has landed (the
  output IS dry). `resume(left)` takes a fading pair back as the target: the fade turns around.
  The first `set`/`clear` after construction or `reset` acts at once until a block has run (the
  `KnobGlide` snap), so a body set on the orbit's first block is bit-identical to before.
  `reset` stays a synchronous HARD cut (deactivation, chain swap, retire); `Cylinder.kt` did not
  change, it already calls `reset`/`retire` only on a silent orbit or the shelf.
- **Rejected:** one shared ramp restarted on every change (all outgoing re-frozen): banks never
  land while changes keep coming, so the pool fills on any change stream faster than one per
  fade; the maintainer's sizing (19 banks at a change per block) is the own-ramp model.
- **The cap: `MAX_BANKS = 10` banks sound per stage** (dry is not a bank), preallocated arrays on
  `Crossfading`. Decided by MEASUREMENT: dropping the QUIETEST bank (its weight moved to the old
  target) measured -35 to -53 dB under a change every block (body -35 to -37, rapidoff -50 to
  -53, vowel -48 to -50, eq -41 to -53), in the hard-switch class. So a change at a full pool is
  PARKED, the latest wins, and goes in at the first block boundary after a bank has landed:
  -76 to -96 dB in the same rows. Price: up to one fade of lag, only while the cap is engaged. At
  48 kHz (2400 frames, 18.75 blocks) a change every OTHER block also reaches the cap.
- **Hosts tell intent from sound.** `active` (intent) flips synchronously in `set`, `resume`,
  `clear`, `reset`; `sounding` is "not Off". Body and vowel: `configure(null)` calls
  `swap.clear()` (a second null is free), an unchanged def with the intent off asks
  `swap.resume(curLeft)` and installs a FRESH bank when it answers false, so a config cache that
  outlived a fade-out to Off never blocks the identical material (question 2 of the plan). The
  EQ keeps no off door; it installs into any of `MAX_BANKS + 2` pre-built banks the swap does not
  `holds` (10 sounding, 1 parked, 1 arriving), so no audible bank is ever zeroed; the old
  two-bank ping-pong would have zeroed a bank that still sounds.
- **Measured** (effect level, band-limited saw 110 Hz and a three-saw chord, both to 3 kHz,
  44.1 and 48 kHz; HF > 8 kHz over 0.7 ms, peak re signal RMS; the steady floor is numerically
  silent for these sources). HEAD `468b8a88` to tree: off -23..-33 to -93..-103; on -25..-36 to
  -96..-102; off/on -17..-31 to -88..-98; owner handover every 250 ms -22..-24 to -90..-94; return
  mid-fade-out -20..-33 to -92..-102; change then off mid-fade -20..-38 to -89..-99; second change
  1/2/4 blocks into a fade -32..-65 to -93..-109; single change -81..-98 to -93..-109; body change
  every block -14..-15 to -78..-80 (parked); vowel -15..-29 (off/on) to -85..-96, every block
  -32..-33 to -93..-96; EQ every block -18..-20 to -87..-90, second change -36..-49 to -108..-115.
  Engine (48 kHz renderer, the investigation's sine rows): `body("<wood none>")` -22.7 to -93.5 dB,
  two patterns sharing an orbit -23.3 to -75.3, `body("<wood glass>")` -88.1 to -97.3.
- **Songs** (15 built-in, 3 frozen, 256 cycles, raw doubles, wall clock pinned): 14 identical.
  Differing, every difference attributed by a per-orbit event log: Synthkura (orbit 0 is shared by
  the koto's mahogany body and the three noise layers without one: 443 owner handovers, each a
  hard cut at HEAD, a fade now); Stranger Synths (orbits 3 and 4, the slowly patterned vowel: 63
  changes each, 12 ms then, 50 ms now); frozen Stranger Things 2026-07-03 (orbit 0 shared by the
  claps and the morse body: 3 on/off; orbit 1, two superimposed voices with wood and glass: 375
  changes; orbits 2 and 3 vowel: 63 changes each); frozen piece Der Schmetterling 2026-09-16
  (orbit 3 shared by guitar 3's rosewood body and the bass: 834 handovers). Every other event
  (a snap on the orbit's first block, a reset at deactivation) is the same on both sides.
- **Cost.** A settled stage is unchanged (Engaged processes in place). Der Schmetterling, 64
  cycles, six interleaved runs each side: within noise. Rapid rows cost more only while fades
  run: a body change every 125 ms +15 %, a vowel change every 62.5 ms +25 % (whole-engine render,
  one voice), because a fade is now 50 ms of two banks instead of 12.
- **Guards, each mutation-checked (31 mutants, all red):** `FilterSwapLaw` (spec helper, the law
  per sample from scratch) is the oracle of `KatalystFilterSwapSpec` (the ramp, the landing, off
  fades and releases, on fades in, the first-init snap, a change mid-fade keeps every bank, the
  return turns around and a second clear is idempotent, clear mid-change, the cap with parking
  and latest-wins, reset from every state, references, a pair's own clock), the body and vowel
  host rows through `SwapHostScript` (reference banks from the bare DSP: off then the SAME
  material installs afresh, the return takes the fading bank back, a change mid-fade, reset
  mid-fade-out is dry at once and the next life snaps) and the EQ row (a curve change on every
  block against fresh reference `EqCore`s; red for the ping-pong and for a pool one bank short).
  `KatalystFilterSwapStateIdentitySpec` walks the new table. `OrbitBusPipelineSpec`'s hand-off
  row reads the intent at once, the body still sounding on the next block, and dry after one fade
  (red for the body hard-cut mutant since review round 1).
- **No allocation at the first fade:** the swap's six scratch buffers are sized at construction
  from `blockFrames` (the builder passes its own, default `RENDER_QUANTUM_FRAMES`); the grow
  fallback remains only for a direct caller with a longer block.
- **Not in this step:** the vowel morph (bands gliding by position, the `v1` tap, a Q setter),
  the compressor and gain, the phaser and duck switch-off, other knob glides (the compressor:
  Katalyst 5c-7, the section above).

## The tail ceiling never under-reports a delay tail (2026-09-19)

Katalyst step 5c-5. Closes the OPEN item of step 5c-1 below (and knob-glide pilot entry 7).
`TailCeiling` said "silent" over an audible repeat in two ways; both repairs only RAISE the
ceiling, so an orbit reset or a chain-swap retire can move LATER, never earlier.

- **Falling feedback (live, after a drain, on the ring-out).** `observe` now keeps the LARGEST
  |feedback| any block of the running window ran at (`feedbackInWindow`, like the input peak) and
  uses it for both terms; each block also counts the value its per-sample ramp STARTED from
  (`lastFeedback`, `DelayLine` ramps from the previous block's feedback), in every window the block
  touches. A steady or rising feedback computes the same doubles as before. Cost: a hold of at
  most about TWO extra windows after a fall (measured 276 blocks at 0.4 s, 1.84 windows): the
  window of the cut keeps the old feedback, and the block that straddles or starts the next window
  credits its ramp start to that whole window too.
- **Self-oscillating drain, tame return.** The drain does not observe, so the ceiling freezes.
  On `Draining -> Active` the effect calls `TailCeiling.resume(drainFeedback)` (O(1)): it credits
  the drain's largest feedback to the next block's ramp start, and up to |feedback| 1 that is
  enough, because the soft cap and the tap never expand (the ring only decayed or held under the
  frozen value). Above 1 it returns true and the effect RE-MEASURES: `remeasure(tapWindowPeakAbs())`
  raises `previous`, the window's input peak and `current` to the measurement, never lowers them.
  One O(delay) scan, only on a return from a self-oscillating drain. Rejected: re-measuring on
  every return (a scan per note toggle, and it moved the steady toggle's fall by 138 blocks; as
  built, two owners alternating per block, one self-oscillating and one with the delay off, still
  pay one O(delay) scan on each off-edge, the countdown's, AND on each on-edge, the re-measure);
  observing through the drain (at |feedback| > 1 the ceiling runs to `CEILING_MAX` while the ring
  sits at the cap, a hold of minutes after a tame return); a `fb > windowFeedback` measure arm
  (measured unexposed, -100 dBFS, and the credit covers it by the argument above).
- **Measured** (effect level, one-off fixture, 0.4 s and 0.1 s, burst of 4 blocks at 0.5, the
  note swept over the window, the cut swept over two windows; "dropped" is the loudest sample the
  ring still emits after the orbit would reset). BEFORE: live handover 0.7 to 0.0 on the orbit
  path (ten silent blocks) -9.1 dBFS in 354 of 1196 phases; the same after a 1/20/60-block drain
  -9.1 dBFS in 373 of 1008; ring-out glide 0.7 to 0.0 (no wait) -9.1 dBFS in 405 of 1196, to 0.05
  -87 dBFS; self-oscillating 1.2 drain, charges 0.1 / 0.01 / 0.005 / 0.001 / 0.0002, return at
  0.9: -87 / -67 / -61 / -47 / -39 dBFS (at 0.3: -94 / -73 / -73 / -63 / -48). AFTER: nothing
  above -110 dBFS in any of them (the fb 0.0 cases drop nothing at all); per phase, 0 resets
  earlier, the rest the same or later (at most 276 blocks, two windows, for the cut; after a
  self-oscillating drain the orbit now holds the real tail, up to 115 000 blocks at a return of
  0.99). Steady and toggled controls: identical reset blocks. The rising-glide-at-leave case was
  -100 dBFS before and after (sub-threshold, unchanged).
- **Song identity.** 15 built-in songs and 3 frozen pieces, 256 cycles, 48 kHz, raw doubles of
  the engine mix per block, wall clock pinned (`timeOfDay`, `sinOfDay`, `sinOfDay2`,
  `timeOfNight`, `sinOfNight` to `pure(0.5)`): bit-identical at `8a2878e2` and on the tree, 18 of
  18. Able to fail: a mutant ceiling without its recirculation term changed 12 of the 18.
- **Guards, each mutation-checked:** `TailCeilingSpec` (falling feedback, ramp start, steady
  equals the old recurrence, resume's answer and credit, remeasure raises only, reset forgets
  the feedback history; the self-oscillation row now expects the extra window);
  `KatalystDelayEffectSpec` (the cut swept over note and cut phase with a ring oracle, the
  self-oscillating return, the steady toggle pinned at `8a2878e2`'s blocks 2476 / 2507);
  `CylinderChainCrossfadeSpec` (the ring-out retires only over an empty ring).
- **Still open:** a tap LENGTHENED, live or on return. The old price (`quiet/loud * 1e-5`, -60 to
  -90 dBFS) was wrong: it assumed the older cells decayed by the feedback, false at a low one. At
  feedback 0 the cut can be the full level of anything still in the ring's span (review of 5c-5:
  a -1 dBFS burst into a 0.03 s delay, then a quiet owner at 0.18 s re-reaches it after "no
  tail"). Pre-existing; the old and new ceilings answer the same there. The reverb's
  drain return does not call `resume` (comb feedback below 1, measured unexposed).

## The orbit-bus fields left the wire (2026-09-19)

Katalyst step 5b-3. `VoiceData` lost `delay*`, `reverb*`, `compressor*` and `duck*`; the orbit's
knobs cross the wire as `katalystParams` slots only. `VoiceFactory` no longer builds `Voice.Delay`,
`Voice.Reverb` (both classes gone), a compressor, a ducking or the body/vowel defs for the voice, and
`Voice` lost those six members. `Voice.Compressor` and `Voice.Ducking` stay: they are the composites
`KatalystSlots` resolves from the slots. Identity: every built-in song and frozen piece rendered
bit-identically in raw doubles at `15b19243` and on the tree (15 built-in songs and 3 frozen
pieces over 256 cycles, 26 door-form rows over 16, wall clock pinned).
Still carried but read by nobody on the backend: the body/vowel `FilterDef`s in `filters` (the
factory drops them from the per-voice chain). The phaser fields stay for a custom pipeline's
per-voice phaser and leave with the Pipeline DSL.

## The orbit delay and reverb are insert-style stages (2026-09-19)

Katalyst step 5b-2, decided with the maintainer (signal-flow plan §7). A SOUND CHANGE, pending the
listening checkpoint.

- **The feed is the orbit mix at the stage's position, times the owner's ONE `wet`**, into a
  block-sized `feed` buffer each stage owns; the return is added into the mix as before, so the
  dry stays. In the classic order the room hears body, vowel and the delay's echoes. The master's
  `MasterStageDsl` model on the orbit bus. No voice sends: `SendRenderer` writes the mix only, the
  cylinder's two send buffers, the swap's two send ramps and `KatalystContext`'s two send fields
  are gone. The chain swap feeds the leaving chain through its one ramped mix, and its ring-out
  keeps the stages ACTIVE on the cleared buffer (`drainSends` is gone): switching them to their
  own silent drain input cut the room's feed of the delay's echoes in one sample at the handover
  (measured -66.8 dB above 8 kHz on a pad, now at the -93 dB floor; review round 1, M1).
- **On/off is unchanged** (`sendStageRuns`): a WRITTEN wet of 0 runs the stage fed nothing, an
  authored 0 nobody writes rents nothing. `Voice.delay` / `Voice.reverb` have no reader any more
  (the cull bound dropped its send factor); they leave with the wire fields in 5b-3.
- **`KnobGlide` has its LEVEL half**, `advanceScaled`: one block's share of the glide as a
  per-sample ramp written from the END (the last sample is the block's value bit for bit), a
  multiply-only fast path when the value did not move, and a short block spreads its share over
  the frames it has. First users: both stages' `wet`.
- **Measured before gliding** (band-limited pad and pluck, energy above 8 kHz, calibrated against a
  hard cut of the return at -24 to -31 dB): a delay TIME jump -20 to -29 dB (hard-cut class), a
  FEEDBACK jump -28 to -35 dB for large moves, -47 dB for 0.3 to 0.4. So `DelayLine` crossfades
  the old tap to the new one over 50 ms (a second read, one ramp, both output and feedback path;
  now at the -91 to -94 dB steady floor), and the feedback glides per block AND `DelayLine` ramps
  each step per sample (one add, 0.0 on a settled line): per block alone left -47 to -55 dB,
  because a gain on the recirculating audio writes its staircase into the ring and it comes back
  every period (review round 1, m1); now -90 to -93 dB. A time change during a running crossfade is PARKED,
  the latest wins when it ends; reset snaps; a grow carries the tap state (`adoptHistory`). The
  tail bounds (`tapWindowPeakAbs`, the countdown, the ceiling window) reach the furthest tap still
  sounding (`reachSamples`), and the ceiling's laps per window count the SHORTEST one; both equal
  the settled values when nothing moves. The drain countdown takes the larger
  feedback magnitude while it glides (pilot entry 6, both directions guarded); entry 7 (a feedback
  falling to 0, also on the chain-swap ring-out) was CLOSED by step 5c-5, see "The tail ceiling
  never under-reports a delay tail" above.
- **Evidence**: 21 renders at HEAD `e08143f3` and on the tree, raw doubles, 256 cycles, wall clock
  pinned. Unaffected songs are identical or at rounding level; every other difference is an orbit
  with a body or vowel before the room, a delay AND a reverb, or voices with and without a wet on
  one orbit. Synthris Echo and Smalltown first read 1.5e-8 / 3.6e-9: traced (review round 1, m2)
  to a PRE-EXISTING stale mix buffer. `clear()` skips an inactive orbit, so the last block's
  sub-floor output stayed in the buffer until a voice reactivated the orbit, summed under that
  voice and, now, fed into the room. `tryDeactivate` clears the mix; with that one line on both
  sides the two songs read 1.7e-16 and 3.6e-16. Guard: `OrbitCleanupTest`. Guards: `KnobGlideSpec`,
  `DelayLineSpec`, `KatalystDelayGlideSpec`, `KatalystReverbGlideSpec`, `KatalystInsertFeedSpec`,
  `CylinderKatalystParamsSpec`, `SendEffectDefaultsParitySpec`, `CylinderChainCrossfadeSpec`.
- **For 5c (review round 1, m6): the wet glide is bypassed exactly where the feed moves most.** Out
  of Off the wet snaps; Active to Draining cuts the feed in one sample; Draining to Active restarts
  from the wet frozen at the drain's start although the real feed was 0. Since the feed is the
  whole orbit mix, each of these cuts or starts every sounding voice on the orbit, not only the
  voice that asked. The switch-on and switch-off fades of 5c are where it belongs.

## The filter swap is a state machine: Off, Engaged, Crossfading (2026-09-19)

Katalyst step 5c-4, the FIRST of the swap's two commits: today's lifecycle as states, a pure
refactor, bit-identical. `KatalystFilterSwap` has the delay's shape (private sealed `State`, three
preallocated inner states, the table in the KDoc on `State`, seam `currentState`). The hosts (body,
vowel, eq) did not change: `set`, `clear`, `process` and `active` kept their names and meaning.

- **Every event dispatches**, `clear` included: Crossfading holds the outgoing pair, so only
  Crossfading can drop it. The delay's shortcut (`reset` enters Off without asking the state)
  would have kept two dead banks alive per body or vowel host until the next fade.
- **The four answers.** (1) What outlives the states: the pair in service (it survives the fade's
  end, so it lives on the swap and `Off.enter` drops it) and the grow-once scratch buffers; the
  pairs are the hosts' resources, the swap only references them. (2) The record a finished life
  leaves is the fade position: Crossfading's own, initialised only by `enter`, so the old `fadePos
  = 0` in `clear()` was a dead write. The hosts' config caches stay the hosts'. (3) The Off
  precondition, today: none; entering Off is the measured cut and leaving it an instant install,
  kept bit for bit. (4) References: the outgoing pair is dropped by the fade's end, by a restart
  (which drops the OLDEST pair) and by `clear`; the pair in service by `Off.enter`.
- **The EQ's ping-pong depends on "a restart drops the oldest pair"**: the bank it reuses and
  zeroes on a change mid-fade is exactly the one the restart drops, before any block hears it.
  "Crossfade from what sounds now" keeps that bank sounding, so the second commit needs more than
  two EQ banks or a different reuse rule.
- **Permanent rows, each mutation-checked:** `KatalystFilterSwapStateIdentitySpec` (red for a fresh
  `Crossfading()` in a transition); in `KatalystFilterSwapSpec` one row per question: "a pair's own
  state runs on across every transition" (red when the outgoing pair runs twice per block, which the
  five older rows do not see, and for a swapped `crossfadeTo`), "every fade starts at t = 0" (red without
  `pos = 0` in `enter`), "Off is a pass-through from every way in" (red when Crossfading's `clear`
  stays in Crossfading or Off's `set` fades; it pins TODAY's cut and turns red with the switch-off
  fade, on purpose), "a finished fade, a restart and a clear() leave no reference to a dead pair"
  through the seam `holds(filter)` (red, and the only red row, for each of the three drops removed).
- **Evidence, fixtures deleted.** (a) A raw-bits harness, 1084 lines, `cmp`-identical at `4306f104`
  and on the tree; a missing `pos = 0` changed 381 lines. Its swap lifecycles, at 44.1 and 48 kHz:
  `off-pass`, `set-from-off`, `fade-completes`, `restart-at-0..5` (set while fading, at every block
  of the fade including the last), `triple-at-0..5`, `clear-mid-0..5`, `set-set-before-block`,
  `clear-from-engaged`, `clear-from-off`, `short-blocks` (64, 1, 100, 0, 200, 7, 300, 500 frames,
  a scratch grow mid-life), `tiny-fade`, `long-fade`; and the three hosts: body and vowel through
  changes at every fade position, off from Engaged and mid-fade, the same material after off, NaN
  knobs, `reset`, `retire`, a catalogue walk; the EQ through its ping-pong at every fade position,
  a wrong shape, `reset` from Engaged and mid-fade, NaN, `retire`. No production host renders a
  block shorter than 128. (b) Raw pre-master doubles, 48 kHz, wall clock pinned, identical: the
  eight songs (Sakura 48 cycles, Irish Lament Techno 160, Final Fantasy 7 Prelude 32, Stranger
  Things 200, Der Schmetterling 160, A Truth Worth Lying For 72, Tetris 40, Sound Of The Sea 16)
  and thirteen synth rows (`body-alternate` and its dry control, `body-rapid`, `body-rapid-off`,
  `body-onoff`, `body-handover`, `vowel-alternate`, `vowel-rapid`, `eq-katp`, `eq-katp-rapid`,
  `rapid-dry`, `eq-rapid-dry`, `eq-static`). 19 of the 20 body and vowel sites were reached,
  measured by host; Sound Of The Sea's glockenspiel is a sample and never configures its body in
  the jvm renderer. No built-in song declares an orbit EQ (the `.eq(...)` in Der Schmetterling is
  the per-voice ignitor), so the rows carry it. (c) Enter counters: the songs drive Off to Engaged,
  Engaged to Off, clear while Off, and Engaged to Crossfading to Engaged (98 fades in Stranger
  Things, its vowel pattern); no song restarts or clears mid-fade. The rapid rows restart mid-fade
  about 1000 times each and `body-rapid-off` clears mid-fade 336 times; the harness drives every
  cell. (e) A Truth Worth Lying For, 32 cycles, interleaved runs: noise.

## Body and vowel run on one resonator bank (2026-09-19)

Katalyst step 5c-3, a pure refactor, bit-identical. `BodyFilter` and `FormantFilter` are gone;
`filters/ResonatorBank.kt` is a parallel bank of `SvfBPF` bands, each a frequency, a q and a LINEAR
gain, and knows nothing about body or vowel. The two gain rules live where a table row becomes a
band, `LowPassHighPassFilters.bodyBand` (`10^(db/20)`) and `vowelBand` (`10^(db/20) * clampedQ *
VOWEL_TAME`, in that operand order: regrouping it changes the doubles and the vowel harness rows
went red on exactly that). Both hand the SVF the RAW q; the vowel folds the clamped one.

- **The hosts stayed two classes** (`KatalystBodyEffect`, `KatalystFormantEffect`): the wire
  types share no supertype that carries bands, mix and floor (only the sealed `FilterDef`), their
  band types share nothing, `KatalystChain.body` / `.vowel` find a stage by its class, and the test
  seams are typed per band kind. One class needed a generic, a factory parameter and a kind marker.
- **Identity:** a raw-bits harness over every material (15) and vowel (75) at six wet/floor pairs
  (set, unset floor, full wet, a high floor, NaN, infinite), odd bands (NaN/infinite dB and q, q out
  of range, NaN freq, freq above Nyquist), empty banks, and both hosts through material changes, a
  change mid-crossfade, unset and non-finite knobs, off, `reset`, `retire` and a catalogue walk:
  114,166 block digests identical. The regrouped vowel fold changed 35,506 of them. Eight built-in
  songs, 20 call sites (19 body, 1 vowel), every site reached, raw pre-master doubles identical; the
  same mutation moved Stranger Things. Sound Of The Sea's body sits on a glockenspiel SAMPLE, silent
  in the jvm renderer.
- **For the morph (next steps):** `SvfBPF.setCutoff` retunes while it plays and keeps its state
  (the integrators are untouched, the coefficients ramp over 32 samples). q is a construction
  `val` in `BaseSvf`; a q glide needs a setter on the same ramp. The vowel's gain depends on q, so a
  q glide must move the band's gain with it. Largest band count today: 8 (the body materials), 5 for
  every vowel; nothing in the code is sized by it, the bank sizes its arrays from the list.

## The orbit reverb is a state machine too (2026-09-19)

Katalyst step 5c-2, the delay's shape copied onto `KatalystReverbEffect` (the plan is
`docs/plans/effect-state-machines.md`). A pure refactor, byte-identical: the private enum and its
`when`s became a private `sealed class State` with three preallocated `inner class` states, the
transition table lives in the KDoc on `State`, the seam is `currentState`. What the reverb's own
answers turned out to be:

- **Outlives the states:** the unit, the tail ceiling AND the size glide. Draining keeps
  advancing the glide, and a return mid-drain glides on from where the room stands.
- **The records of a finished life are forgotten in `Off.enter()`, both of them:** the ceiling
  (the four terminal resets the 5c-1 entry names are now this one line; its line numbers were
  already stale, at `11e02b3f` they sat at 193, 260, 279 and 344) and the size glide. The glide
  reset MOVED: it sat on `configure`'s ON arm behind "the state is Off", i.e. on the way OUT of
  Off. Nothing reads or advances the glide while Off, so forgetting it on the way IN is the same
  behaviour (the raw-bits harness below proves it bit for bit), and it makes the ON arm the same
  from every state, so only the OFF arm dispatches.
- **The Off precondition** has four callers: `Active.deactivate` (silent OR poisoned network,
  `unit.reset()`), `Draining.process` (countdown end, `unit.reset()`), `reset()`, `release()`
  (hands the unit back dirty). The OFF-arm test keeps `|| !remaining.isFinite()` (the heal of a
  poisoned network); the countdown-end test in Draining stays a plain `<= 0.0`.
- **Dead writes removed** with the single-initialiser argument: `drainRemaining = 0.0` in `reset`
  and `release`, and the field write on the arm that went straight to Off. A non-initialising
  `Draining.enter` turns five existing rows of `KatalystReverbEffectSpec` red, so the delay's
  "own countdown" row was not copied.
- **Permanent rows, each mutation-checked:** `KatalystReverbStateIdentitySpec` (every reachable
  cell, both Off arms of the OFF config, the glide edges, the refusal; red for a fresh
  `Draining()`, `Active()` or `Off()` in a transition); "a reverb that returns mid-drain keeps the
  network AND the tail ceiling" (the ceiling reset in `Active.enter` turns (a) red, a network
  reset on Draining to Active turns (b) red and only this row); "a life that ended in Off starts
  the next one with an empty ceiling" (deleting the ceiling reset turns only this row red, and the
  ten-block assertion binds on its own); the existing poisoned-network row (red without the
  `isFinite` arm, as is the identity spec); the existing glide row "reset and retire mid-glide"
  (red without the glide reset in `Off.enter`).
- **Evidence, one-off fixtures deleted with the step.** (a) An effect-level harness, raw bits of
  the mix plus `hasTail`, the unit's size bits and `deniedRents` per block and per event, 19110
  lines, `cmp`-identical at `11e02b3f` and on the tree. Its twenty lifecycles, for the next
  conversion to copy: `off-to-active`, `active-drain-off`, `drain-back-to-active` (a larger room,
  glides), `drain-back-same`, `active-reconfigured` (size and lowpass moving, NaN, Inf, clamped
  sizes), `owner-handover` (NaN and Inf owners as off), `reset-and-relive`, `reset-mid-drain`,
  `retire-and-rent-again` (one shelf, retire from Active and mid-drain, double retire, release),
  `poisoned-network`, `mismatched-block` (built for 64, driven with 128), `silent-network-off`,
  `glide-return-mid-drain`, `glide-return-same-target`, `reset-mid-glide`, `retire-mid-glide`,
  `falling-glide-off`, `refused-then-shelf`, `finished-life-then-new`, `test-seam`. Able to fail:
  deleting the glide reset from `Off.enter` changed 4263 lines, dropping the `isFinite` arm 40030.
  (b) Raw doubles of the engine mix before the master stage, 48 kHz, wall clock pinned: seven
  synth rows (`off-active-drain-active`, `drain-to-off`, `active-reconfigured`, `owner-handover`,
  `deactivate-and-relive`, `swap-reverb-on-both-chains`, `swap-reverb-on-one-chain`), each with a
  peak and a dry control (the row without its reverb, 0.018 to 0.047 apart), and the twelve songs
  with an orbit reverb, each rendered past the first appearance of its last reverb owner
  (Final Fantasy 7 Prelude 32 cycles, Stranger Things 40, Sakura 48, Der Schmetterling 104,
  Tetris 40, Sound Of The Sea 16, Tetris Remix 24, Irish Lament Techno 156, Sandsturm 32, Irish
  Lament 16, Small Town Boy 16, Drunken Sailor 16): identical. Halving the drain countdown changed
  three rows and Irish Lament Techno. (c) Enter counters: only Irish Lament Techno among the songs
  drains (2 countdown ends); the rows drive Draining to Active 23 times and 16 countdown ends;
  NO render reaches the silent or poisoned arm or reset or retire from Draining, which is why (a)
  carries the evidence. Stranger Things drives nothing but Off to Off: its reverb
  owners are samples. (e) Sakura, 64 cycles, three timed renders each side twice: noise.
- **Call sites:** 39 orbit-reverb calls in 12 songs (the four `Master(m => m.reverb(...))` are the
  master's). Eleven of Irish Lament's twelve are overwritten by its outer `.reverb(0.1, 7)`.
  Sample-fed, so silent in the jvm renderer: Stranger Things (all), Der Schmetterling's count-in
  (`size = 0`, OFF) and the drum orbits of its line 433 (its orbit 10, pink noise, is a synth),
  Tetris's drum orbit, Sound Of The Sea's glockenspiel,
  Tetris Remix's snare, Irish Lament Techno's clap and crash, Small Town Boy's drums, Drunken
  Sailor's drums.

## Orbit knobs glide: the pilot on the orbit reverb (2026-09-19)

`docs/plans/knob-glide.md` (its section 5 holds the pilot log). `KnobGlide` (audio_be root, the
sibling of `Crossfade`) moves one COEFFICIENT knob linearly over `KNOB_GLIDE_SECONDS` (0.05, in
`BusEffectDefaults.kt`), rounded to whole 128-frame blocks (17 at 44.1 kHz, 19 at 48 kHz). It lands
on the target bit for bit, restarts from the current value on a new target, ignores a non-finite
target (the owner substitutes its own meaning first), and SNAPS every value after construction or
`reset` until a block has consumed one. A glide is a safety net against AUDIBLE jumps, applied per
knob, not a loudness keeper (maintainer, 2026-09-19). The per-sample LEVEL half arrives with its
first real knob in 5b-2.

- **Only the orbit reverb's SIZE glides**, on the normalized 0..1 axis (affine to the comb
  feedback). Damping does NOT: review round 1 measured a full-span damping jump 66 to 70 dB below
  the tail (the comb one-pole stays continuous, the change reaches the output a comb length later,
  staggered over the combs); a size jump sits 44 to 50 dB below, and the glide takes 12 to 15 dB
  off it (10*log10(17)). The shared `Reverb` gained only the drain's `size` parameter.
- **The old path, literally.** `configure` writes size and lowpass unconditionally, as at HEAD;
  while a glide moves, `advanceGlide` overwrites the size before the block processes. The one
  reader in between is the drain countdown, which reads the glide.
- **Lifecycle.** Leaving Off snaps (the one place a glide is forgotten; reset, release and a
  finished drain all land in Off). Draining keeps gliding, so a drain is still "Active on silent
  input".
- **The drain countdown** takes the LARGER of the size in force and the glide's target: from a
  still-rising feedback it cuts the tail. For a FALLING glide this over-holds, ACCEPTED for
  simplicity (a tighter bound needs its own proof): size 1.0 falling to 0.0, off mid-glide, counts
  down at feedback 0.98, about 21 s from a full-scale peak, where the room decays like 0.7, about
  1.3 s. It holds an idle network and a rented unit on silent input, never audio; rare.
- **The trap every next knob meets:** the owner re-applies its settings EVERY block, so a retarget to
  the same value must be a no-op; a helper that restarts on every call never lands.
- **Evidence:** 18 built-in songs and frozen pieces render bit-identically (raw doubles, 256 cycles,
  wall clock pinned) at `279d5fc4` and on the tree; no song moves an orbit reverb while it plays
  (two count-ins use `size = 0`, which is OFF, so their room arrives from Off). A move is
  identical up to the change and differs from the first block the comb re-emits (one shortest
  comb, 1116 samples, later) onward, decaying with the tail, not only inside the 50 ms. Guards:
  `KnobGlideSpec`, `KatalystReverbGlideSpec`.

## An effect lifecycle as a state machine: the delay is the template (2026-09-19)

Katalyst step 5c-1, the first conversion of `docs/plans/effect-state-machines.md` and the shape
the reverb, the filter swap, the compressor and the cylinder's swap bookkeeping copy. A pure
refactor: `KatalystDelayEffect`'s `private enum class State` became a private `sealed class State`
nested in the effect with three `private inner class` subclasses, one preallocated instance each,
and a `state` pointer. What it replaced, counted rather than remembered: TWO `when (state)`
(`hasTail` and `process`), one `if (state == State.Active)` in `configure`, and the field written at
six sites besides its initializer. Byte-identical, proven twice (below).

- **The shape, as built.** `State` declares three methods: `process(ctx)`, `hasTail()` and
  `deactivate(line)`. The host calls `state.process(ctx)` and `state.hasTail()`; `configure`'s ON
  arm is the same from every state, so it stays on the effect and only its OFF arm dispatches.
  `enter` is the only way into a state and is what resets that state's own data: `Draining` owns
  the countdown and nothing else, `Active` owns NOTHING (its knobs live on the `DelayLine`, where
  the DSP reads them), `Off` owns nothing and its `enter` forgets the tail ceiling.
- **What belongs on the state and what on the effect**, sharper than the plan says it: a datum
  belongs to a state only if it dies with that state. The tail ceiling looked like `Active`'s, but
  it survives `Active -> Draining -> Active`, so it is a resource on the effect, like the ring and
  the silent input buffer. WHAT that survival is worth has one home and it is not this file: the
  `activeTail` KDoc in `KatalystDelayEffect`. Two review rounds were spent on loose versions of the
  sentence here, so the short form is all that belongs in memory: the ceiling is a bound while
  Draining and at the moment of return, wherever the ring decays, which is every `|feedback| < 1`;
  it is NOT a bound at `|feedback| >= 1`, nor across a LENGTHENED tap, nor after a feedback
  REDUCED to (near) zero, and each of those can let `hasTail` answer false over real content. The countdown really does die with
  `Draining`, so it moved.
- **CLOSED by step 5c-5 (see "The tail ceiling never under-reports a delay tail" above); kept as
  the record of the finding. Recorded 2026-09-19 (not introduced by the state machine, bit-identical at
  HEAD, deliberately not fixed in an identity step): a self-oscillating delay can leave a stale
  ceiling that later under-reports a real tail, and so can a feedback reduced to (near) zero.**
  WIDENED in review round 4, measured on the compiled classes: `TailCeiling.observe` recomputes the
  running window with the feedback in force NOW, so a feedback cut from 0.7 to 0.0 (after a drain,
  or LIVE on an owner handover) makes the ceiling vanish at the next window close while the ring
  still holds one repeat: an echo at about -3 to -6 dBFS dropped in a sizeable share of phases,
  the orbit reset within about 80 blocks; a new feedback of 0.01 or more stays below -83 dBFS. It
  needs a new owner that sends nothing audible and a silent mix for ten blocks; no built-in song
  reaches it. RE-MEASURE on return repairs only the frozen variants; the live one needs `observe`
  never to LOWER `current` inside a running window (at most one extra window of hold). The reverb
  is not exposed (measured -103 dBFS). The original sequence, traced through a line-for-line port
  of `TailCeiling.observe`: a delay at `|feedback| >= 1` takes a charge; the owner leaves; the
  drain is infinite, so the ceiling freezes while the ring grows to the cap; a new owner returns
  with a TAME feedback (the escape the class KDoc documents) and quiet sends. Ring and ceiling then
  decay at the same rate, so their ratio is preserved, and the ceiling crosses the 1e-5 silence
  threshold while the ring still holds about `(ring at return / frozen ceiling) * 1e-5`. At a 0.4 s
  delay with a returning feedback near 1, a charge of 0.1 leaves -87 dBFS behind, 0.01 leaves -67 dBFS, 0.005 leaves
  -61 dBFS, 0.001 leaves -47 dBFS and 0.0002 leaves -33 dBFS. It needs hundreds of consecutive
  silent blocks (5 to 9 windows at a tame 0.3, 689 to 1240 blocks of 128), far more than the ten
  the cylinder waits, so only a long gap can reach it. Whoever fixes it: the honest repair is for
  the return to RE-MEASURE rather than resume, which is a design change and not a spelling one.
- **`internal` buys nothing on the JVM, and the plan is wrong about it** (measured with `javap`,
  Kotlin 2.3.10, JVM target 17). A `private` outer member read from an inner class costs a
  synthetic `access$getX$p`, a STATIC call. Making it `internal` replaces that with
  `getX$audio_be()`, a VIRTUAL call. Neither is a plain field load; only `@JvmField` would be, and
  that is JVM-only so it cannot be written in `commonMain`. Everything therefore stays `private`:
  the encapsulation is free. What is NOT optional is the plan's rule 1 (copy into locals before the
  loop), which is about Kotlin/JS, where the outer is reached through a stored property.
- **Cost per block, counted in bytecode, not guessed.** An orbit whose delay is off and never had a
  ring (the common case) is UNCHANGED in `configure`: both sides return at the same null check, the
  same instruction. `process` is the same COST by a different mechanism, and the entry is precise
  about it because a copy will inherit the sentence: the old one returned at a null check, the new
  one calls `Off.process`, whose body is empty. A running delay's
  `configure` gains two calls, both monomorphic and trivially inlinable (`Active.enter` and the
  synthetic state setter), and keeps its eight branches. An off-config on an effect that HAS a ring
  trades one enum comparison for one virtual call. `process` trades a null check plus a tableswitch
  for one virtual call, and `hasTail` a tableswitch for one virtual call. Wall time, "Irish Lament
  Techno" 64 cycles at 48 kHz, three runs each: HEAD 3658 / 3643 / 3641 ms, tree 3662 / 3619 /
  3620 ms. No regression; the difference is noise.
- **Two oddities the old code had**, both kept as behaviour and now impossible to write:
  `drainRemaining` survived `Draining -> Active` (never read there, but it was a state carrying a
  previous life's value), and the tail ceiling's survival across the drain was real but undocumented.
- **How it was accepted, and which render backs the claim.** Both sides of every comparison are
  HEAD `d37032e2` in a throwaway `git worktree` against the FINAL tree, rendered on this machine
  with two one-off fixtures that were deleted with the step.
  The evidence rests on the first one: it drove the effect directly through nine scripted
  lifecycles and wrote a per-block FNV digest of the RAW BITS of every output sample plus the
  `hasTail` answer, 4460 lines, `cmp`-identical (md5 `729ae683b54bba72020103b324126943` both
  sides). Raw doubles, and a difference would have named the block it started in.
  **The nine lifecycles it scripted, named here because the fixture is deleted and the next
  conversion copies the list rather than inventing one:** (1) `off-to-active`, the first config;
  (2) `active-drain-off`, the full Active to Draining to Off run with live sends arriving through
  the drain and having to be discarded; (3) `drain-back-to-active`, the return mid-drain with a
  LONGER time, so the new tap sweeps cells the drain wrote, then out again; (4)
  `active-reconfigured`, time, feedback and cap moving every block, including a ring GROW through
  `adoptHistory`; (5) `owner-handover`, three owners in sequence with the middle one carrying no
  delay; (6) `reset-and-relive`, `reset()` mid-tail and a new life after it; (7)
  `retire-and-rent-again`, `retire()` mid-tail and a fresh rent; (8) `self-oscillation`, feedback
  1.2 with the infinite drain and a tame owner as the escape; (9) `mismatched-block`, an effect
  built for 64 frames driven with a 128-frame context, so the drain clamps and the countdown must
  tick by the clamped count. Each wrote one line per block, and the tail flips are in the step's
  report.
  **The seven render rows, same reason:** `off-active-drain-active`, `drain-to-off`,
  `active-reconfigured`, `owner-handover`, `deactivate-and-relive`, `swap-delay-on-both-chains`,
  `swap-delay-on-one-chain`. Each carried a peak floor and a wet-at-zero engagement control.
  **Both harnesses were proven able to FAIL, which is what makes their agreement worth anything.**
  The raw-bits one: halving the drain countdown (`remaining - frames * 2.0`) changed 1106 of its
  4460 lines. The render one: short-circuiting `Active.process` turned its engagement control red.
  Neither was accepted on a green run alone.
  The second was the end-to-end half: seven synth-only sprudel rows (each with a peak floor and a
  wet-at-zero engagement control) and the six built-in songs that call the delay door, 64 cycles
  each at 48 kHz, every hash identical. The six are **Sandsturm, Tetris, Irish Lament Techno, Sound
  Of The Sea** (with `sinOfDay` pinned to `pure(0.5)`), and, added in review round 2 when the count
  was checked against the sources rather than against the first list, **Sakura and Small Town Boy**.
  Each song was rendered twice in-process as a determinism tripwire, and neither late addition seeds
  anything from the wall clock. **Der Schmetterling was deliberately NOT among them**: it never
  calls the delay door, and it carried the maintainer's uncommitted by-ear edits at the time.
  **What "six songs" does and does not mean, counted by reading the sources in review round 3**,
  because the next conversion will otherwise over-trust it. The six carry SEVEN delay call sites.
  Six of the seven play inside the first 64 cycles; the seventh, `IrishLamentTechno.kt` line 218,
  sits in `darkBuild`, which the arrangement places at cycles 148 to 243 (the song's own comment
  says 211, stale), so a 64-cycle render never reaches it. The plan's acceptance (b) now asks for
  enough cycles to reach every call site; 5c-1 accepted this gap because that site drives only Off
  to Active on a synth, which the raw-bits harness drives too. Of the six that do play, only FOUR are fed by a synth and therefore sound at all in
  this renderer (Sandsturm's `leadPat`, Tetris's `leadShape`, Irish Lament Techno's `leadStyle` from
  cycle 32, and Sakura's outer-stack delay): Sound Of The Sea's Windspiel is a `glockenspiel` SAMPLE
  and Small Town Boy's is a drum pattern of samples, and the jvm offline renderer has no sample
  bank, so those two orbits send silence into a configured delay. The song half is therefore worth
  less than its count suggests, and **the evidence rests on the effect-level raw-bits harness**,
  which drives every edge deliberately.
  Sakura adds one thing the other three audible sites do not: a delay running at `feedback = 0.0`
  in the ACTIVE path. It does NOT drive the `fbAbs <= 0.0` arm of `DelayLine.drainSamplesUntilSilent`
  (a round-2 claim that did not survive reading): that function has exactly one production caller,
  `Active.deactivate`, and Sakura's delay rides the outer stack, so every event carries it and no
  owner ever hands the effect an off-config.
  Which edges the RENDER ROWS really drive was MEASURED with a temporary counter in `enter`, not
  assumed: `drain-to-off` fired the terminal reset 8 times, `off-active-drain-active` did
  `Draining -> Active` 7 times, `deactivate-and-relive` went through the cylinder-reset door 3
  times.
- **What stays, and what the next four conversions copy: FOUR permanent contracts.**
  **1. `KatalystDelayStateIdentitySpec`** drives every REACHABLE cell of the table (all but the two
  marked "never", which are unreachable by construction) plus `configure`'s own three arms: an
  off-config with no ring, one with a ring, and an ON-config the warehouse refuses. It asserts that
  only the three original state objects ever appear, counted by identity and not by `equals` (a
  state written one day as a `data class` would make fresh instances equal and defuse the whole
  spec). That is the "the state machine adds no allocation" guarantee of the plan's section 1 and
  its identity-spec bullet, without a profiler: the only objects
  a transition could allocate are the states. Its final count is a summary of the per-edge
  assertions, not an independent guard. Its seam is `KatalystDelayEffect.currentState`, and it uses
  the PRODUCTION constructor, because the test-seam one installs a ring and the no-ring arm would
  never be walked.
  **2.** The other half needs a BEHAVIOUR row, and this is the one to copy, because review round 1
  found that nothing permanent guarded the template's one non-obvious decision:
  `KatalystDelayEffectSpec`'s "a delay that returns mid-drain keeps the ring AND the tail ceiling".
  A converter who reads "enter resets the target's fields" as a law writes `activeTail.reset()`
  into the Active entry, every spec stays green, and the cost is a CUT ECHO TRAIN: a new owner
  claims the orbit while its own voice is still in its attack and sends nothing, the ceiling reads
  empty, `Cylinder.tryDeactivate` resets the chain and the tail stops dead. The row leaves for 60
  blocks mid-drain, comes back with the same knobs and asserts (a) `hasTail()` never goes false
  and (b) the mix is BIT-identical for 200 blocks to a reference effect that simply stayed Active
  on silent sends. Both halves are mutation-checked, and they fail separately: a ceiling reset in
  the Active entry turns (a) red, a ring reset there turns (b) red.
  The bit-identity in (b) is exact and not an approximation, which is worth knowing before copying
  the shape: `DelayLine.process` reads its input samples, three knobs and its own ring and nothing
  else, so `silentInput` and a silent send buffer are the same input, the re-`configure` writes the
  same three numbers into plain field setters, and neither ring can diverge by a bit.
  **3.** "The countdown a drain runs on is its own, never the previous life's remains" guards the
  single-INITIALISER property (`Draining.enter` initialises the countdown, `Draining.process`
  advances it, nothing outside the state touches it) that made THREE writes of the old flag version
  dead code: `drainRemaining = 0.0` in `reset` and in `release`, and the field write on the arm that
  went straight to Off.
  **4.** "A life that ended in Off starts the next one with an empty ceiling", added in review
  round 2, guards the `activeTail.reset()` inside `Off.enter`. That line had NOTHING behind it:
  deleting it left every row of both specs green, because the Off arm of `hasTail` is a hardcoded
  `false` and a stale ceiling is invisible until the NEXT life reads it through `Active.hasTail`. In
  a copy (the reverb has the same ceiling, reset at FOUR terminal sites today, `KatalystReverbEffect`
  lines 141, 208, 227 and 274) the cost is an orbit
  held open long past its due, about 20 s at a big room's comb feedback and for ever at a delay
  feedback of 1 or more. The row charges four blocks of 0.8, drains to Off, starts a new life and
  asks `hasTail()` BEFORE any block is processed, then again after TEN silent blocks, which is when
  production asks (`Cylinder.silentBlocksBeforeTailCheck` defaults to 10), then sends one loud block
  as a positive control that the fresh life is Active and really answers.
  The reason for asking at zero is NOT that a block would hide the bug. That was written in round 2
  and is false against `TailCeiling.observe`: `inputPeakInWindow` is a running max within the
  window, so a silent block on a stale ceiling recomputes `fresh(0.8, fb, 2)` and still answers
  true. Simulated with a line-for-line port of `observe`, the stale answer survives 31 silent blocks
  at feedback 0.0, about 427 at 0.6, and for ever at 1.2. That is what makes a leak of this shape
  long, and it is worth knowing before someone deletes the row as scaffolding.
  The arithmetic to carry into a copy: one window is `delaySamples + 1` = 2206 samples with
  `lapsPerWindow` 2, so four blocks of 0.8 leave the ceiling at `0.8 * (1 + 0.6) = 1.28` with only
  512 samples elapsed, no window boundary crossed and nothing decayed, against a silence threshold
  of 1e-5. Deleting the reset turns this row red and, measured across the whole module, NO other row
  in 2004; both assertions bind, checked separately by dropping the first one and watching the
  ten-block one fail on its own.
- **What the benchmark harness cannot see**: `audio_benchmark` has a `DelayLine` case, which is the
  untouched DSP core, and no case for the orbit delay effect. A future conversion that wants a
  benchmark number has to add one.

## The born-with chain is slot-driven: one way a bus knob reaches a stage (2026-09-19)

Katalyst step 5b-1. Before it there were two paths into an orbit's stages: a DECLARED chain read
the owner voice's `katalystParams`, and the chain a cylinder is BORN with read the owner voice's
bus FIELDS (`voice.body`, `voice.reverb`, ...). Now there is one, the map, and the voice-driven
writers, the `voiceDriven` flag and the `KatalystOwnerApply` interface are deleted.

- **What the fields still do, and who reads them.** Two readers are left in the whole backend, and
  neither is the bus. `SendRenderer` reads `voice.delay.amount` and `voice.reverb.amount`, the
  per-VOICE send amounts (step 5b-2 takes those). `FilterPipelineBuilder` reads `voice.phaser`, all
  five knobs, for the PER-VOICE phaser of a custom pipeline that declares `StageDsl.Phaser`; no
  built-in preset does (maintainer, 2026-08-24: the phaser is a bus effect, and running it on both
  double-applied the dry floor). Nothing reads `voice.body`, `voice.vowel`, `voice.compressor`,
  `voice.ducking`, `Voice.Delay.time/feedback/cap` or `Voice.Reverb.size/lowpass` any more. They
  are still BUILT by `VoiceFactory` (so the untouched-voice table stays a live oracle for the
  classic chain's slot defaults) and still carried on the wire until 5b-3.
- **`Katalyst(k => k.classic())` is a bit-exact no-op now**, and the 2026-09-18 rule that forbade
  that (`chainFor` must never hand back the born-with instance) is retired with its reason. That
  rule stood on the born-with chain being voice-driven, which made a content-classic declaration
  inert; with both slot-driven the two are one chain, so `chainFor` returns `classicChain` for
  content equal to `KatalystDsl.classic.stages` and `install` / `beginFade` return early when the
  arriving chain is the one already in service. The price of NOT doing it would have been a
  crossfade that is not bit-transparent (the arriving room and ring warm from empty) for a
  declaration that changes nothing. Measured: the same song with and without the declaration
  renders to the same 16-bit hash.
- **Producers that write a bus FIELD and no slot lose their bus effect.** The sprudel doors write
  both, so no song is affected (measured: 15 built-in songs, 3059 events over 8 cycles, zero
  field-without-slot mismatches; 953 playable doc examples, 19642 events, three mismatches, all
  `reverb.size` after a `katp` that the door's field fill then overwrote, which is the intended
  direction). The non-sprudel producers that RENDER are `WarmupRunner`, `IgnitorBenchmark` and
  `KlangBenchmark`, and all three write slots now; `WorkletSerializationBenchmark` already did, and
  `VoiceDataCopyBenchmark` still writes fields only on purpose, because it measures the cost of
  copying a `VoiceData` and never renders one. A raw `VoiceData` with bus fields and no map now reaches a silent
  bus, which is the new wire truth and a guarded row (`CylinderKatalystParamsSpec`).
  One thing a slot cannot express: a hand-built `FilterDef.Body` with private bands. A body is an
  INDEX into the shared catalogue, so the warmup names `wood` and `a` instead of inventing modes.
- **What a non-finite bus knob does, and why every difference in that family is an improvement.**
  One rule covers all of them: HEAD let a non-finite knob fall through to the effect's own setter,
  and those setters DROP a non-finite write and keep whatever was there, which on an orbit that had
  played before is the previous owner's value; the tree substitutes the shared constant before the
  setter sees it, so the result no longer depends on what the orbit played earlier. Verified in
  `git show HEAD:` for each, and the per-family detail, because they are not all the same:
  - `phaser.wet`: `Phaser.depth`'s setter returns on non-finite (`Phaser.kt`, "NaN/Inf silently
    ignored"), so HEAD kept the depth it had; the tree reads it as unset and writes `PHASER_WET`,
    which is off. On a FRESH orbit both are off, so the difference is the stale-depth case only.
  - `phaser.floor`: stored RAW by `Phaser.floor` on purpose, and `WetDryMix.dryCoeff` coerces a
    non-finite floor to **0.0**, which swaps the ADDITIVE law (floor 1.0: the dry signal passes at
    full level beside the wet) for the CROSSFADE law `max(floor, cos(depth*pi/2)^2)`. Verified in
    the code, exponent included: that is about unity just above the engage threshold, -1.4 dB of
    dry at depth 0.25, -6 dB at 0.5, -20 dB at 0.8, and silent only at depth 1.0. So it is audible
    at large depths and nearly inaudible at small ones, NOT the "full notch" an earlier draft of
    this entry claimed. The tree writes `PHASER_FLOOR` (1.0). Reachable only while the phaser is
    engaged, because the floor is written only then.
  - `compressor.*`: **NOT what it looks like, and the claim that HEAD put a NaN gain reduction into
    the orbit mix is wrong.** `Compressor` guards every knob itself, `guardOr(value, <the same
    constant>)` in the constructor and `if (!value.isFinite()) return` in each setter, so a fresh
    compressor at HEAD already resolved to the same five numbers the tree's `finiteOrNull` plus
    `Voice.Compressor.fromParams` produce. The render rows agree: a `compressor(ratio = "NaN")` row
    is bit-identical on both sides. What differs is the same stale case as the phaser's: a SECOND
    owner writing a non-finite knob left the first owner's value at HEAD.
  - `delay.time` and `reverb.size`: the two the previous entry's bullet on the master asymmetry
    covers; those differ on a fresh orbit too, because `VoiceFactory` substituted the constant
    where the slot path reads the non-finite value as the declared off state.
  - **A non-finite `wet`, and this one is NOT a strict improvement, which is why it is written out
    rather than folded into the list.** `reverb("NaN")`, `delay("NaN")`, `katp("reverb.wet", NaN)`.
    At HEAD the field was non-null, so the effect counted as TOUCHED and `orDefault` swallowed the
    NaN: the room ran at `REVERB_WET` / `REVERB_SIZE`, and every voice on the orbit was heard in
    it. On the tree `KatalystKnob.written` is false for a non-finite value, so the stage is off for
    the whole orbit, and another voice sending into it loses its room. That is a real loss, not a
    scrub: what it buys is the slot vocabulary being consistent with itself, "non-finite is unset"
    on every knob including this one, which is what lets a cleared slot read as untouched at all.
    No ordinary spelling produces it: a mapper over an unset field yields null, a rest calls no
    setter, the doors fill finite constants, and KlangScript division by zero throws; it takes a
    string atom (`"NaN"`, `"Infinity"`) or a hand-written `katp`. Guarded by
    `KatalystSlotResolverSpec`'s "a non-finite WRITTEN wet is unset, which is neither touched nor
    an amount, so OFF".
- **The migration fixtures that became vacuous, and where their coverage went.** The content-classic
  shortcut makes any test that compares "with a classic declaration" against "without" true by
  construction. One file in the repository did that, `KatalystDeclaredBodyParitySpec`, whose three
  such rows were the acceptance of steps 5a-2 and 5a-3; they are retired and the file is renamed
  **`KatalystDoorFillRenderSpec`** for what it pins now, the DOOR's fill against the same call with
  every knob spelled out at the shared constant. Verified by breaking both substitutions: the
  door's fill turns the render rows red, and the engine's own `KatalystSlots.bodyDef` substitution
  turns `KatalystClassicMatchesUntouchedVoiceSpec`'s hand-written-slot row red and leaves the
  renders green. A search of every `classic()` and every `maxDiff` / `renderSong` site in all test
  source sets found no other such comparison.
- **Five render rows differ from HEAD, and every one is a documented family** (27 minimal
  multi-orbit rows, a one-off fixture deleted with the step): `reverb(wet = 0)` mid-phrase and
  `katp` reaching the born-with chain (both since RESOLVED, see the send-gate bullet below), plus
  the non-finite `reverb.size`, `delay.time` and `duck.attack` rows above. The other 22 rows,
  every ordinary door form included, are bit-identical.
- **`wet` stayed a PER-VOICE send, and the gate says so** (review round 1). The first cut of this
  step gated the two send stages on `sendIsOn(wet) = wet > 0`, which silenced the room for every
  voice on an orbit as soon as the voice HOLDING THE LEASE wrote `reverb(0)`: `stack(pad.reverb(0),
  lead.reverb(0.4))` lost the lead's room, and swapping the two arms of the stack changed the song,
  because the lease is first-rendered-wins. `VoiceFactory` ran the stage on a TOUCHED field (the
  field non-null, a written 0 included) and let `time` / `size` decide, so the slot twin of touched
  is `KatalystKnob.written`: the owner's map carries the key with a FINITE value. `sendStageRuns`
  is `written || (finite && > 0)`, and the second half keeps the 2026-09-17 decision for AUTHORED
  constants, so a declared `k.reverb(0.0, 6)` that no pattern touches still rents
  nothing. That semantic change belongs to 5b-2, where `wet` becomes the insert amount and the
  maintainer listens.
- **One parity asymmetry, recorded, not fixed**: a non-finite `delay.time` / `reverb.size` is the
  orbit's declared OFF state, while the MASTER's same knob substitutes the shared constant
  (`MasterChain.buildDelay`'s `finite(...)`). It follows from the Katalyst slot rule ("non-finite
  is the declared off state", the reason the `Param` leaf guard was NOT extended to this map) and
  is unreachable from either door, both of which fill with numbers. `SendEffectDefaultsParitySpec`
  states it. Second half of the same corner: `Reverb.normalizeSize` guards NaN (to 0.0) but CLAMPS
  `+Infinity` (to 1.0), so an infinite size is the largest room rather than "unset".
- **The NaN guard in `KatalystBodyEffect.configure` / `KatalystFormantEffect.configure` is now
  defence in depth.** It was added for the born-with voice path, where `toVoiceData` could hand a
  non-finite mix through; the only production caller left is `KatalystSlots.bodyDef` / `vowelDef`,
  which substitute one layer up. Kept, as `KatalystDelayEffect.configure`'s own guard is kept, for
  the same reason its KDoc gives: it is the stage's contract for a direct caller. Still tested by
  `KatalystBodyEffectSpec` / `KatalystFormantEffectSpec`, which call `configure` directly.
- **Cost, stated per block and per owner change, because they are different.**
  - **Per block: nothing allocates, on either side.** The shape changed from one owner lambda per
    stage to one `apply()` per stage, the same count, both writing numbers already in hand.
  - **Per OWNER CHANGE the undeclared orbit newly pays a resolve**, and every event carries a fresh
    map instance, so on a busy orbit that is once per note. The classic chain has 27 knobs
    (body 3, vowel 3, delay 4, reverb 3, phaser 5, compressor 5, gain 1, duck 3), so a resolve is
    27 map lookups plus up to four small allocations, and only for the stages the orbit actually
    uses: a `FilterDef.Body` and a `FilterDef.Formant` when a material or a vowel is named, a
    `Voice.Compressor` when any of its five is set, a `Voice.Ducking` when an orbit is named. An
    orbit whose voices write no bus door allocates none of them. HEAD's born-with path resolved ONE
    knob there (the fader's) and allocated nothing in the chain.
  - **`KatalystChain.resolvedFrom` now holds the owner's map on every orbit**, not only declared
    ones, until `reset()` or `retire()` drops it. It is the identity gate, and it is a reference to
    a map the voice owns anyway.
  - **`VoiceFactory` still builds `voice.body`, `voice.vowel`, `voice.compressor` and
    `voice.ducking` per voice for no reader at all** until 5b-3 takes the fields off the wire.
  - `writeCompressor` is the known open item (five setters, about fifteen `exp()` per block on a
    running orbit, step 5c's to fix; closed and removed by Katalyst 5c-7). It does NOT newly run on an orbit a song already compressed:
    the voice-driven path called the very same `writeCompressor` on every block of every active
    orbit, and a door writes the field and the slot together, so the set of orbits whose compressor
    resolves to settings is unchanged. What IS newly reachable is a raw `katp("compressor.ratio", 8)`
    on an undeclared orbit, which is the feature.
  - Measured with `runSongBenchmark --args=ledger` on both trees: every row inside the run-to-run
    spread (guitar melody 5.9 both, marimba 4.3 to 4.2, bass 11.8 to 12.1 ns/s/pass). No ledger row
    was appended; this is a step, not a phase.

## The pregain slot, the leaf guard, and the orbit's group fader (2026-09-19)

Second half of the signal-flow plan's phase 2 (spots A and C). The first half is the entry below.

- **`pregain` is an ORDINARY slot.** `IgnitorDsl.Slots.pregain` is `Param("pregain", 1.0)`, and
  that is the whole of it: it does what an instrument's tree wires it to and nothing otherwise.
  An instrument that never places it ignores `pregain(x)` bit for bit, which is what
  `PregainSlotRenderSpec` and `VoicePregainWireSpec` assert against a slotless twin. No unconsumed
  rule, no analysis of the tree, no flag from the build; the first attempt of 2026-09-18 had all
  three and is recorded as deleted in the plan's section 6, with the lesson (code that predicts
  another walk's outcome drifts from it wherever that walk is conditional).
- **`.pregain()` is written as `mul(Slots.pregain)`**, not as a hand-built `Times`, so the helper
  and the spelled-out form cannot drift into two operand orders and two content ids. A multiply
  commutes, so a swap would render identically and only a tree comparison can see it:
  `PregainSlotSpec` is that comparison, and the swap is its mutation.
- **What the word is worth, stated honestly**: the slot changes TIMBRE only where the tree puts a
  nonlinearity after it. On a linear tree it is mathematically just a level, and the render spec
  says so with a bit-exact row (`x.pregain()` at 0.5 IS the slotless render times 0.5). The tone
  claim is a separate row on a tree with a `tanh` after the slot, where the render differs from
  `render(1) * 0.5` by 0.05 and more, against rounding at ~1e-16. `gain` is the tone-neutral word,
  and the same two rows on one driven instrument in `VoicePregainWireSpec` are what tells them
  apart.
- **And where it stops working, which is the opposite of the intuition and is now in the door's
  KDoc**: on a CLIPPING shape driven into hard saturation the slot changes neither the tone nor the
  level, because a clipper holds both. Swept on `saw.pregain().distort(d).lowpass(2500)` at
  `pregain` 1 against 0.4, as a normalised-RMS shape distance (level-blind, so a pure level change
  reads 0): d = 0.1 -> 0.082, 0.3 -> 0.181, **0.5 -> 0.223**, 1.0 -> 0.117, 2.0 -> 0.006, with the
  level ratio running 0.487 to 0.999 over the same sweep. So `distort(0.5)` is where touch lives
  and `distort(2)` is where the knob is inert; the doc examples and the driven instrument in
  `VoicePregainWireSpec` use 0.5, and `PregainSlotRenderSpec` guards that choice with a row that
  fails at 2.0.
- **The shape decides, and only the SATURATING shapes behave that way** (2026-09-19, round 3; the
  paragraph above generalised from one shape and was wrong for a whole family). At `distort(2)`,
  the same measurement per shape: `soft` 0.006, `hard` 0.004, `cubic` 0.005, `diode` 0.009,
  `tube` 0.015, `gentle` 0.021, all with a level ratio of 0.999, against the three WAVEFOLDERS,
  which never saturate: `fold` **1.358** (level ratio **4.256**, so halving `pregain` makes the
  note about four times LOUDER), `linearfold` 1.675 (0.854), `sineshaper` 1.757 (1.103).
  `rectify` sits between the families at 0.054. On a folder `pregain` IS the fold depth and stays
  the strongest tone knob at any drive, and it is not monotonic in level. Guard:
  `PregainSlotRenderSpec`'s contrast row, mutation-checked by swapping the shape.
- **The finite guard sits at the `Param` LEAF, for every slot, not for `pregain`** (`IgnitorDslRuntime`,
  the one place a slot resolves against `oscParams`). A non-finite override reads as unset and
  takes the slot's authored default. General and not name-keyed for two reasons: the bag is an open
  `Map<String, Double>` that any frontend may fill, so no name is safer than another, and a NaN that
  gets in multiplies through the rest of the tree and the voice never recovers. It is reachable from
  sprudel today through a string atom (`"NaN"` and `"Infinity"` both parse) or an overflowing power;
  script division by zero throws instead. Checked before it was made general: the Katalyst's
  `SLOT_UNSET` slots never pass through this leaf (they resolve in `KatalystSlots` / `KatalystKnob`
  off `katalystParams`, where non-finite is the DECLARED off state). The two other readers of the
  bag on a render path, `IgnitorRegistry.createExciter`'s `oscParams["onepole"]` and
  `VoiceFactory`'s `oscParams["analog"]`, were out of that step's scope and **carry the same guard
  since 2026-09-20** (phase 3 step 1, the bullet below).
  `GraphCensus.of` is a FOURTH reader (`countOf`, `params[slot.name] ?: slot.default`) and resolves
  an override with no finite guard at all; it is audio-inert (benchmark only, never on a render
  path) and harmless as it stands, since `NaN.toInt()` is 0 and is then coerced to 1.
  Guard: `VoicePregainWireSpec`.
- **The bag's RAW reads now follow the same rule** (2026-09-20, signal-flow phase 3 step 1): THREE
  reads across two readers, `VoiceFactory`'s `oscParams["analog"]` (once for the filters, once again
  in the sample branch) and `IgnitorRegistry.createExciter`'s `oscParams["onepole"]`. Each now takes
  `?.takeIf { it.isFinite() } ?: 0.0`; `analog` is read once at the top of `makeVoice` and the sample
  branch takes that local, so the bag is read once per voice. The detail is in `VoiceBagGuardSpec`,
  which is the one home of it and the guard; in short, and every line of it MEASURED:
  - The readers disagree about WHICH test a non-finite value fails, which is why one guard at the
    read is the only tractable place. `perVoiceCutoffOffsetMul` tests `analog <= 0.0`;
    `AnalogDrift` and the SVF's saturating branch test `analog > 0.0`.
  - A **NaN** `analog` failed `<= 0.0`, so the multiplier and every cutoff went NaN and `bilinearK`
    substituted 1 kHz. On an exciter that never touches the voice rng (`saw`) the poisoned voice was
    a voice whose lowpass really sits at 1 kHz SAMPLE FOR SAMPLE. **It does not generalise**: failing
    that test also consumes one `nextDouble()` PER FILTER off `voiceRandom` before the exciter is
    built off the same stream, so on `supersaw` the poisoned voice was 1 kHz AND a shifted jitter
    stream, measured NOT equal to the 1 kHz supersaw. The extra draw belongs to every non-finite
    value, not to `+Infinity` alone.
  - An **`+Infinity`** `analog` was WORSE, and is what makes this more than hygiene: it fails
    `<= 0.0` and passes `> 0.0`, so the SVF took its saturating branch with `driveScale = Infinity`,
    and at the first sample `ic1eq` is 0.0, so `tCfb` is exactly 0.0 and
    `kEff = k + 2.0 * Infinity * 0.0` is NaN. Every sample of the voice was NaN from frame 0 (all
    1024 frames of the spec's render), nothing in `Voice` or `Cylinder` scrubs it, so the NaN reached
    the ORBIT MIX and stayed in the orbit's send chain for the rest of the playback:
    `note("c3").oscp("analog", "Infinity").lpf(2000)` silenced an orbit. Same failure the `gain`
    guard's comment describes for its own reader.
  - On the SAMPLE read, only `+Infinity` was a defect (the lane tests `> 0.0`, which a NaN fails):
    the drift multiplier went non-finite, the playhead with it, and every frame after the first was
    NaN. Reverting that one read alone turns exactly one spec row red.
  - An **`+Infinity` `onepole`** passed the registry's `> 0.0` gate and built a one-pole whose cutoff
    `bilinearK` clamped to 1 kHz, rendering exactly what `onepole(1000)` renders.
  - A **`-Infinity`** was already safe everywhere (it passes `<= 0.0` and fails `> 0.0`), and so was
    a NaN on the two `> 0.0` gates. Those rows state the rule, they do not close a defect.

  Identity proven for the shipped corpus: no song, frozen text or doc example writes a non-finite
  one, and a one-off HEAD-against-tree render of all 15 built-in songs plus both frozen songs and the
  frozen piece, 256 cycles each at 48 kHz with the wall clock pinned, was 18 of 18 bit-identical.
  All three reads disappear when these doors become slots in the tree.
- **The one behaviour the guard CHANGES, and it is wanted: it closes a voice leak.** An instrument
  whose envelope release is a slot (`.adsr(release = Osc.param("rel", 0.1))`) used to accept
  `oscp("rel", "Infinity")`, which the leaf handed on as an infinite `releaseTailSec`, so
  `VoiceFactory` computed `endFrame = gateEndFrame + release * sampleRate` as infinite and the
  voice was endless and uncullable, ONE PER EVENT. Measured both ways on 2026-09-19: with the guard
  that call resolves to the slot's 0.1, without it to `Infinity`. `"Infinity"` is reachable because
  a sprudel string atom parses it. An authored infinite DEFAULT still produces the endless voice,
  and that stays: an instrument that declares an infinite release is asking for a drone, and the
  wire's NaN rule is about values a PATTERN writes.
- **Precisely what the guard covers, because the difference matters downstream**: every
  `IgnitorDsl.Param` LEAF, and only the OVERRIDE it reads out of `oscParams`. It does NOT cover
  the slot's authored DEFAULT (that is the instrument's own declaration, and
  `IgnitorDslOptimizerFuzzSpec` fuzzes non-finite defaults on purpose), and it does not close
  `IgnitorFilters.scaledBy`'s non-finite-q hazard, which survives by two other routes: a non-finite
  authored DEFAULT, and ARITHMETIC in a q expression, since `Plus` and `Minus` are clamp-free by
  contract (`Osc.param("a", 1e308).plus(Osc.param("b", 1e308))` as a q renders sample for sample
  what a `+Infinity` q renders; verified 2026-09-19). A `ParamIgnitor` that engine code constructs
  directly never passes the leaf either, but no production caller does that today: `scaledBy` has
  exactly two callers, both in `IgnitorDslRuntime`'s passes cascade and both fed `q.noMod()`.
  **A voice's `FilterDef` q is NOT one of these routes**, and three sites said it was for one round
  (this file, `IgnitorFilters` and `IgnitorDslOptimizer`) because the claim was reasoned rather than
  looked up: a `FilterDef` becomes an `AudioFilter` through `LowPassHighPassFilters.createLPF` /
  `createHPF`, and the whole ignitor package never references `FilterDef`. The lesson is the plain
  one: a claim about WHICH CALLERS reach a function is a caller search, not an inference.
- **THREE test rows of `IgnitorDslOptimizerRenderSpec` were silently voided by the guard when it
  landed** (a subtract, a divide and the C5 non-finite q, the last one found a round later because
  its value is a LOOP VARIABLE that a literal grep cannot see). All three delivered NaN, the
  infinities or 1e300 through `oscParams` and all of them read as the slot's finite default
  instead; the C5 row is the one the production comments name as THE guard for the
  `rel == 1.0 -> q` / `factor == 1.0 -> this` pair, so that pair was unguarded for a round. They
  deliver through the authored DEFAULT now and carry an engagement row each.
- **How they were found, and the method to reuse**: a grep for a literal non-finite next to
  `oscParams` misses a loop variable, so the sweep that found the third one instrumented the LEAF
  to throw on any non-finite override and ran the full jvm suites of `audio_be`, `sprudel`,
  `klangscript-libs`, `audio_bridge`, `klang` and the root (2026-09-19). After the repairs only the
  three deliberate `VoicePregainWireSpec` rows trip it. Reuse the instrumented leaf, not a grep,
  whenever a guard changes what reaches a leaf.
- **An engagement row for a PARITY row must not merely differ from the finite case.** The first C5
  engagement row asserted "the poisoned render differs from the q = 1.2 render", which any
  substitution of one finite number also satisfies, so it would have stayed green under the very
  guard that voided the parity row. What no single substitution can fake is that the poisoned
  values DIVERGE FROM EACH OTHER: NaN and -Infinity render identically while +Infinity does not.
  The MECHANISM behind that, read off `computeSvfCoeffs` and not guessed (round 3 corrected an
  earlier wording that said the two "share the scrub", which they do not): `safeOut` maps NaN to
  0.0 and an infinity to a SIGNED SAFE_MAX of 1e15, and `computeSvfCoeffs` then coerces a finite q
  into [0.1, 200], so NaN and -Infinity MEET AT THE 0.1 FLOOR while +Infinity lands on the 200
  ceiling and screams (it peaks 24x to 43x above a q of 1.2 over four blocks).
- **And even that was not enough.** None of it can see a SIGN-PRESERVING scrub of the default
  (NaN to 0.0, the infinities to a signed SAFE_MAX), which keeps every one of those relations.
  What separates "the raw non-finite reached `computeSvfCoeffs`" from "something scrubbed it" is
  the 0.7071 Butterworth fallback (`q.isFinite()` is the test there, so ANY non-finite takes it)
  on the UNWRAPPED middle stage, which only an ODD cascade has: at passes = 3 a raw NaN builds
  the stages (0.1, 0.7071, 0.1) and a scrubbed 0.0 builds (0.1, 0.1, 0.1); at passes = 2 they are
  identical. The row asserts both halves of that, and the sign-preserving scrub is its mutation.
- **`KatalystDsl.classic` ends in a group fader at unity** (`gain.gain`, the dotted convention),
  declared last in the list before the duck. At exactly 1.0 `KatalystGainEffect.process` returns
  before it multiplies, so the historical sound is untouched, which
  `KatalystClassicGainStageSpec` pins against the chain the classic chain WAS (the same stages with
  the fader filtered out) rather than against a remembered number.
- **What the fader covers, and what it does not.** The delay and the reverb are still send buses,
  but their RETURNS are mixed into the orbit's buffer by their own stages, and those sit before the
  fader, so it scales dry and returns alike; a row with the dry input zeroed proves it on the
  returns alone, and its mutation is the fader moved to the front of the pipeline. The duck is the
  one thing it cannot cover: it runs outside the list, in the cross-orbit pass, so a ducked orbit is
  ducked after its own fader. Step 5b does not change either fact.
- **Every chain has the fader, the born-with one included**, because a gain stage is slot-driven
  on every chain (it never had a voice field, and since step 5b-1 no stage does). So
  `katp("gain.gain", 0.5)` reaches a
  group fader on an orbit that declares nothing at all, which is measured end to end through the
  offline renderer as well as at chain level.
- Identity, measured: three minimal multi-orbit rows (no declaration, one orbit declaring
  `Katalyst(k => k.classic())`, both declaring it) render to the same hash at HEAD `df93f9f1` in a
  throwaway worktree and on the final tree, peak 29871 of 32767. Adding `katp("gain.gain", 0.5)`
  changes that hash, which is the engagement control for the whole path.

## The wire carries ONE level word (2026-09-19)

- `VoiceData.velocity` and `VoiceData.postGain` are GONE. `gain` is the channel fader, applied once,
  with pan, in `SendRenderer`; the `signal *= voice.postGain` line went with the field, and
  `measurePeak` and `Voice.heard` read `gain` alone. A frontend's articulation shorthand (sprudel's
  `velocity`, the MIDI playground's key velocity) is multiplied into `gain` BEFORE the voice crosses,
  so the backend never learns that word. Signal-flow plan section 6.
- **Non-finite `gain` reads as UNSET, 1.0, at the voice factory** (the wire's NaN rule,
  `/dsl-design` section 4). It is not a clamp: a negative gain and a gain above 1 stay legal and pass
  through raw. Two readers depended on it and both were wrong for a NaN: `Voice.heard` starts latched
  on a gain of exactly 0 and `NaN == 0.0` is false, so a NaN voice started unlatched; and
  `measurePeak` scales the block peak by `abs(gain)`, so a NaN gain made the peak NaN, which fails
  every compare against the cull floor and read as audible forever, while the NaN itself went on into
  the orbit's reverb and delay and latched them for the rest of the playback. The old comment there
  called that "NaN-guard by inaction"; it is a guard now. Guard: `VoiceGainWireSpec`.
- **What `VoiceGainWireSpec` actually asserts**, since the claim above needs its evidence named,
  in four kinds, because what each kind needs by way of a control differs:
  - **Factory rows** (seven): the substitution for NaN and both infinities, and the raw
    pass-through of a negative, an above-1 and an exactly-0 gain. Direct oracle, value in and
    value out, so they need neither an engagement control nor a floor.
  - **The absolute send row** (one): `TestIgnitors.ramp`'s samples, by their own definition, times
    the pan law computed in the test, compared by raw bits against BOTH mix channels and the delay
    send bus, which the engine takes from the already-panned and gained value. Neither side comes
    from the code under test. It carries both a not-silence floor on all three buses and an
    engagement control (a different gain must render different buses, and the two channels must
    differ from each other). Its reach is the stages THIS voice renders, the amp VCA and the send
    stage: a trim inside a stage only a factory-built voice instantiates, a filter or a
    waveshaper, is not in its pipeline and it cannot see one there.
  - **Whole-path render rows** (two), `VoiceData -> VoiceFactory -> Voice -> render`: a NaN gain
    rendering bit for bit what an unset gain renders, which carries the NOT-SILENCE FLOOR on its
    reference; and a 0.5 gain rendering exactly half of it (0.5 is a power of two, so the product
    is exact either way it associates), which carries the ENGAGEMENT CONTROL.
  - **Cull rows** (two), the only public seam the two readers have, `Voice.culled`: a gain of 0
    starts the `heard` latch so a voice that can never sound is culled, with its ENGAGEMENT
    CONTROL being the same voice at gain 1, which is never heard and never culled; and a NaN gain
    from the wire culled exactly where an unset one is, with its ENGAGEMENT CONTROL being the
    assertion that the reference actually culls.
  A second multiplier reintroduced at the send stage moves the bits and goes red UNLESS it is
  exactly unity, which no bit comparison can see; nothing here claims otherwise.
- Rounding: a voice that never used the retired second multiplier is bit-identical; one that did
  changes by floating-point rounding only. 309,867 onset events of every song text in the repo,
  89.4 % bit-identical, worst relative deviation 2.3e-16.

## The ignitor optimizer's promise is a margin now (2026-09-15)

- `OPTIMIZER_PARITY` (audio_bridge, 1e-12 relative, NaN for NaN, infinity for infinity) replaces
  bit-identity as what `IgnitorDsl.optimize()` promises, so that block-constant arithmetic and
  gains may fold into neighbouring linear nodes (the plan and its steps:
  `docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md`, the open items in `docs/tasks/future/ignitor-optimizer-open-items.md`). The shipped rules still render bit-identical.
- Guards, all green on the current pass: the rule table (`IgnitorDslOptimizerSpec`), render
  parity within the margin plus control-rate semantics and the warmup vocabulary
  (`IgnitorDslOptimizerRenderSpec`), every builtin song's instruments (`OptimizerSongParitySpec`,
  root module), and a thousand generated graphs with adversarial constants, the pass's laws
  (idempotent, work never grows, params survive) and a zero `optimizerFailures` count
  (`IgnitorDslOptimizerFuzzSpec`, jvmTest: it counts by reflection). Mutation-checked: a fused
  coefficient one percent off goes red in the render rows and the fuzz; the sharing guard
  disabled goes red in the rule table (a forked LINEAR subtree renders the same bits, so only
  structure or the fuzz's work count can see it).
- Lesson from the first fuzz run: the `passes = N` expansion repeats a section's `Param` per
  section in `collectParams`; consumers dedupe by name, and the laws compare distinct names.
- Steps 1 and 2 (2026-09-15): `IgnitorDsl.Affine(inner, pre, mul, add)` = `mul · (x + pre) + add`
  in one pass, sanitised like the `Plus`/`Times`/`Plus` chain (`safeOut(mul · (x + pre)) + add`),
  an absent pre-add or add being `Constant(-0.0)` (the bitwise identity of the add; `+ 0.0`
  flips `-0.0`), `mul` without a default. The pre-add exists because `a·x + a·b` for
  `x.add(b).mul(a)` cancels at every zero crossing. Rule R2 folds one node per
  `x [.add] .mul [.add]`, never across an addition, composes literal multiply runs only where the
  chain's clamp cannot differ (growing runs; attenuating runs over a clamped input), folds a Param
  multiply alone, and leaves a left-hand scalar with a Param where it was written (param order).
  On its own it changes nothing measurable (a lone `mul` was one pass already); it was meant as
  the shape the Eq and shaper gain folds (steps 3 and 4) remove entirely.
- Steps 3 and 4 are WON'T IMPLEMENT (2026-09-15): the ceiling was measured before touching
  EqCore. `audio_benchmark` rows `guitar-rig*` (the rhythm rig of Der Schmetterling as an inline
  tree, `KLANG_BENCH_FILTER=guitar-rig` runs only them) DELETE every level `mul` and every
  `Drive` outright, and that buys about 1 to 2 µs of a 53 µs voice on node, under 4 %. The
  same rows put 22 µs of the rig's 35 in the shapers' oversampling, so that is where the work
  went (next entry). Measure the ceiling of a fold by deleting the nodes before building it.

## The ledger: a per-instrument harness for every optimization round (2026-09-16)

- `./gradlew runSongBenchmark --args=ledger` renders the six instrument pieces of Der Schmetterling
  (both guitar rigs, marimba, trommel, bass, drums) solo and ungated on the FROZEN song text of
  `FrozenPieces.kt` (the text at `v0.3.14`; a materially changed instrument gets a new dated
  snapshot, the old one stays) and on the live text, and APPENDS a row per piece to
  `docs/benchmarks/ledger.md` with `git describe` and the CPU. Run it after every optimization
  round; within one piece and one machine the engine is the only thing that moves between rows.
- `GraphCensus` (`ignitor/GraphCensus.kt`, a diagnostic, never on the render path) counts what a
  note asks of the engine from its OPTIMIZED tree: passes over the block, block-buffer reads plus
  writes per sample, bytes of held state, with a weight per node kind read off the runtime
  lowering (shared nodes once plus a memo read per extra consumer, scalar-only arithmetic
  nothing, the first variant only). `GraphCensusSpec` pins hand-counted graphs. The song
  benchmark sums it over the rendering voices after each measured block
  (`VoiceScheduler.renderingVoiceSounds()`, zombies excluded) and reports `voices`, `work`,
  `traffic`, `KiB`, `ns/smp/voice` and `ns/smp/pass`, the last being the engine's cost per unit
  of work, the number that must fall while a song's RTF may rise with the song.
- Why: a live song's RTF over time mixes a faster engine with a heavier song and can read as if
  the optimizations went the wrong way. The ledger holds the work still. First reading, the
  September round (`v0.3.12` by a worktree transplant against `v0.3.14`, medians of three, same
  machine, the spread of three runs about 10 %): melody guitar -18 %, rhythm guitars -14 %,
  marimba -14 % (one run caught a pause; its clean runs say -28 % and -14 %), trommel -44 %, bass
  -55 %, drums -56 % (culling: 24 active voices became 6 rendering). The drums' ns per sample per voice ROSE while their RTF
  halved, which is the case for counting work, not voices. A rhythm guitar note is 57 passes, 19
  of them the unison stack's voices, which the first census draft priced as one.

## div, minus and neg fold; a zero divisor is zero (2026-09-15)

- Engine rule (maintainer): a divisor of EXACTLY zero yields zero. `DivIgnitor` fills zero for a
  block-constant zero and renders nothing upstream (a dead branch), zeroes the sample for a zero
  in a divisor signal, and keeps the `SAFE_MIN` clamp for tiny non-zero divisors. `Ignitor.div(0.0)`
  is a block-constant zero. `Ignitor.neg()` is `mul(-1.0)`, clamp included; `NegIgnitor` is gone.
  A block-constant MULTIPLIER of exactly zero is the same dead branch in `Times`, `mul(0.0)`,
  `Affine` and an `EqCore` tap at gain zero (the band is not run, a bare `+ 0.0` is added, bit
  for bit the chain): without that, a Param divisor at zero skipped its upstream authored and rendered it
  optimized, and the voice's noise stream went out of step (round-1 review). `Recip` at zero is
  the deliberate exception, still the `SAFE_MIN` substitution.
- Rule R2 now covers `x.mul(k).minus(b)` (add `-b`, bare: `-0.0 - b` for a non-literal, never a
  `Neg`, which is a clamping multiply now), `x / k` (a multiply by the expression `1 / k`, so the
  runtime's guard applies once per block), a literal `x / 0` (a multiply by a literal zero: dead,
  but still built, so a phase pool under it and every pool after it draw as authored) and `neg()`
  (a multiply by `-1`, composing with a literal it follows; an inner flip before an attenuation
  over an unclamped input stays two nodes, the chain clamps the input first). `k - x` stays a
  Minus: the fold would clamp a bare subtract and cost more. A literal run whose product
  underflows to zero does not compose (a dead branch the chain never was). Nothing merges across
  an addition still. The lesson of the two review rounds: every place the optimizer SYNTHESIZES
  a coefficient (a reciprocal, a composed product) must not be a zero unless the authored
  coefficient was, or the dead branch renders on one side only and the noise stream slips.
- The parity oracle is relative to the block's loudest sample, at most full scale, not to the
  sample itself: the reciprocal multiply is an ulp off and a filter carries that to a zero
  crossing, where a per-sample relative measure blows up on nothing (fuzz seed 433); the cap keeps
  a saturated sample from buying its neighbours a tolerance of 1e3. Constancy and params stay
  equal on both sides, and scalar-only arithmetic, however deep, counts as no work.
- A lesson from the constant-fold parity rows: a SineIgnitor shared between the folded and the
  reference chain advances twice per block; build a fresh instance per chain.

## The oversampler's decimator is polyphase and indexed (2026-09-15)

- `Oversampler.decimate2x` no longer pushes every sample through a 15-slot ring with wrapping
  reads; output `m` is the FIR at `s[2m-13 .. 2m+1]` read straight out of the work buffer, with
  13 samples of per-stage history and a prefix view for the first 13 outputs (before that point
  an in-place write would land under an unread tap). Same taps, same summation order, so the
  output is bit-identical to the ring form for every block length; `OversamplerDecimatorParitySpec`
  keeps the ring implementation as its oracle over ragged block lengths (1 to 128, both sides of
  13 and 26) and goes red for a history one short, the in-place boundary one early, a tap off by
  one, or the short-block history shift dropped.
- Node 24, µs per block, one voice: `pluck+distort_4x` 13.4 -> 10.1, `pluck+distort_2x`
  9.4 -> 8.6, the guitar rig 53.2 -> 44.3 (its oversampling share 22 -> 12). Every oversampled
  stage takes it: the shaper on the voice doors, the shaper, crush and coarse on the strip (the
  ignitor crush and coarse doors have no oversampler). The copies into the prefix view and the
  history are plain loops: `copyInto` allocates a typed-array view per call on JS, and the
  guitar rig went from 45.5 to 44.3 µs when they went.

## Analog drift steps per block and ramps across it (2026-09-15)

- Every `AnalogDrift` lane (the two-layer OU pitch drift) is built at the BLOCK rate
  (`analogDriftStepRate(sampleRate, blockFrames)`, 375 Hz at 48k/128; `AnalogDriftCoeffs` follows
  the rate, so the time constants in seconds and the peak cents are unchanged) and stepped once
  per block (`beginBlock`: `blockStart` = the previous `blockEnd`, `blockEnd` = one step). Every
  consumer ramps the multiplier linearly across its window: `m = start; dm = (end - start) /
  length; inc = dt * m; m += dm`, one add per sample. `DriftLanes` blends per block
  (`prepareBlock(spread)`, `advanceLane`, `startOf`, `endOf`; no per-sample shared scratch, no
  `driftStep`). Sites: the mono sine, the pulse train, `WaveIgnitor`, the sine partial bank, both
  wave-engine stacks, both strings, the sample player. The filter drift (`FilterModRenderer`) ran
  per block already (a hold). Maintainer decision: per block and interpolated everywhere; not
  bit-identical, judged by ear.
- Why: `DriftLanes` stepped every lane per sample (xorshift, two one-poles, the blend), 20 lanes
  per note on the Schmetterling rhythm guitars (the stack's 19 unison voices and the shared lane;
  an earlier count of 13 read the pattern-level `unison(voices = 13)`, which the sound's literal
  `voices(19)` replaces), for a modulation whose fastest layer has a 50 ms time
  constant: 19 % of a drifting guitar, 13 % of the marimba, 15 % of the trommel.
- Measured (rig and live A/B, seeded, `docs/benchmarks/2026-09-15_1755*` = per sample,
  `_1759*`/`_1800*` = per block): rhythm rig 0.040 -> 0.036 with "no analog" at 0.035 (the drift
  is free now), lead 0.028 -> 0.024, trommel 0.042 -> 0.034, bass 0.0087 -> 0.0063, the live song
  0.106 -> 0.097, the frozen song 0.099 -> 0.082.
- Guards: `AnalogDriftRampSpec` (mono sine, saw and sample player against block-ramp reference
  accumulators; a one-voice supersine's increment read off three consecutive samples, wandering
  across blocks and only ramping within them; the ramp is not a step), `DriftLanesSpec` in block
  terms, `SinePartialBankSpec`'s drift reference on the ramp, `SuperStackDriftSpreadSpec`'s golden
  render retaken. Eleven mutations red.

## Polynomial e^x in the envelopes and the compressor, no fastLn (2026-09-15)

- `fastExp(x) = fastExp2(x · log2 e)` (`DspUtil.kt`) replaces `kotlin.math.exp` per sample in the
  envelopes' exponential curve (`adsrExpShape`, the DEFAULT curve on every stage since 2026-08-24,
  so every voice paid one `exp` per sample through attack, decay and release), the compressor's
  dB-to-linear gain (both the envelope and the lookahead path), the `exp()` ignitor and the two
  exp-based waveshapers (`expClip`, `stompBox`). Fast range `|x| < 22`, beyond it a platform `pow`.
- `fastExp2`'s polynomial was refitted as `1 + f + f(f-1)·r(f)` (r degree 5, 4.7e-11) so that
  `p(0) = 1` and `p(1) = 2` are EXACT in floating point: `fastExp(0) = 1`, `fastExp2(n) = 2^n` bit
  for bit. The envelope specs pin `g(0) = 0`, `g(1) = 1` and the sustain level to 1e-12, and the
  normaliser `adsrExpNorm` now goes through `fastExp` too (same expression above and below the
  line: `g(1)` is `A · (1/A)`, 1.0 or one ulp under). For callers: the pinned start makes the
  absolute error of `fastExp(x) - 1` shrink with `x` (6e-15 at 1e-6); what stays is parts in
  1e-9 of that small difference (no expm1 accuracy).
- Measured (voices and live A/B, same run): pink -10 %, hats -6 %, bass -3 %, the live song
  0.1096 -> 0.1073 (-2 %), frozen 0.0970 -> 0.0949. Less than the linear-curve experiment
  (`docs/benchmarks/2026-09-15_1539*`, 15 to 20 % on a simple voice) promised: the JVM's `exp` is
  an intrinsic. Per call (`audio_benchmark`'s `runMathBenchmark`, ns, library -> polynomial,
  2026-09-15, Ryzen 9 7940HS): JVM sin 6.1 -> 1.7, 2^x 8.9 -> 2.7, e^x 3.7 -> 3.5 (near parity);
  node 24 (V8, the worklet's engine) sin 7.2 -> 1.8, 2^x 12.1 -> 3.8, e^x 7.1 -> 4.5. The e^x
  path costs more than 2^x on both platforms because an envelope's `k · x` spans octaves 0 to 4
  and only octaves 0 and -1 skip the table (on V8 a lazy-init accessor of the top-level val plus
  `numberToInt` per read); the next step there, if wanted, is a branch ladder for the envelope's
  octaves or a bit-free `2^n` by conditional doublings.
- No `fastLn`: the song's orbit compressors (three calls, one instance per orbit covered, nine
  instances) together are 2.6 % of the song with `fastExp` in place
  (`docs/benchmarks/2026-09-15_171710`, the rig suite's `song` group, "no compressors": the song
  ungated and without its count-in so all eight cycles play the band, and its wall-clock seed
  pinned so both arms render the same 913 onsets; the earlier 5.8 %, `_162314`, was gated, seeded
  per pass and with the library `exp`), and `ln` is at most half of that. Every rig case renders
  the pinned seed now: an A/B on the live song was not controlled before. Without bit extraction (`Long` is banned) a log needs a compare
  ladder for the exponent, a divide and an odd series, about 40 cycles against a 60-cycle
  library `ln` on the JVM and near parity on V8: under 1 % of the song for real complexity.

## Table-and-polynomial 2^x in the pitch paths (2026-09-15)

- Vibrato (`2^(sin · depth / 12)`) and the pitch envelope (`2^(semitones · level / 12)`), in the
  voice strip (`VibratoRenderer`, `PitchEnvelopeRenderer`) and the Ignitor twins
  (`PitchModFactories.kt`), call `fastExp2(x)` (`DspUtil.kt`) instead of `2.0.pow(x)`: integer
  octave from a 64-entry table (`n` in `[-32, 32)`), fraction by a degree-7 minimax polynomial,
  relative error 4.0e-11 (7e-8 cents; the first fit, refitted the same day with pinned ends as `1 + f + f(f-1)·r(f)`, r degree 5, 4.7e-11, see the e^x entry above), bound `FAST_EXP2_MAX_REL_ERROR` = 1e-10 (1.7e-7 cents) asserted by
  `FastExp2Spec` (sweep, octave boundaries, both renderers against the pow law). Outside
  `(-32, 32)`, NaN and the infinities fall back to `pow` itself.
- Both pitch-envelope renderers skip the per-sample work once a block starts past attack + decay:
  the anchor ratio once per block. Same law (mutation-checked on both renderers, both buffer paths).
- Measured (one rig A/B pair, back to back, `docs/benchmarks/2026-09-15_1509*` = pow,
  `_1508*` = fastExp2): trommel 0.046 -> 0.044 medRTF (-5.5 %). Cases without a pitch path moved
  between -2 % and +14 % between the same two runs, so the whole-song figure (-1 %) is inside that
  noise. `pow` was a twentieth of a pitch-enveloped voice; the drum's cost is its partial bank,
  drift and body. The octaves 0 and -1 skip the table read (on JS a top-level val is read through
  a lazy-init accessor per call).
- Still on `pow`: the `accelerate` renderers (one `pow` per block, then a multiply per sample),
  `applySemitoneDetuneToFrequency` (per note), the generic `PowIgnitor`/`ExpIgnitor` (user
  arithmetic, any base).

## Polynomial sine in the oscillators (2026-09-15)

- Every oscillator sine (`SineIgnitor`, the partial bank `sinePartials`, the wave-engine sine behind
  `supersine`) calls `fastSin(phase)` (`DspUtil.kt`) instead of `kotlin.math.sin`: a degree-11 odd
  minimax polynomial on the folded half period, max error 1.3e-11 (-217 dB), bound asserted at
  `FAST_SIN_MAX_ERROR` = 1e-10 by `FastSinSpec` against a dense sweep and against the ignitor's own
  render. The phase accumulator, drift and modulation are untouched; only the function changed, so
  the per-sample drift multiplier and per-sample pitch modulation keep working (a rotation
  oscillator would not: it needs a constant increment). The function is bit-identical on JVM
  and JS (pure arithmetic; `Math.sin` never promised that); a whole voice still is not, `pow`,
  `ln` and `cos` upstream of the phase remain platform transcendentals.
- The fold is exact for a quarter period past either end of `[0, 2π)` and diverges fast beyond, so
  every oscillator wraps first. Review found the one site that did not always: the wave-engine
  stacks used the one-subtract `smallNumFastMod` wrap, unsafe once `|dt| >= 1` (a frequency past
  the sample rate in either sign, or `spread(200)` with cents typed into the semitone door), where
  the library sine gave bounded aliasing and the polynomial gave 1.8e34; the trapezoids (stacks and
  the single-voice `WaveIgnitor`) parked on the low plateau (a DC offset) for a positive dt and rode
  the rise ramp without bound for a negative one, and now alias instead. All three sites hoist
  `safeWrap = pm != null || drift != null || !(abs(dt) < 1.0)` per block (drift can hold a near-1
  dt over the edge for seconds). Guard: `SuperSineOutOfRangePhaseSpec` (red first, both signs,
  drift, sine and trapezoid, stack and single voice). Trap for a later
  "finish the job": `FmRenderer` accumulates its modulator phase per sample and wraps only at block
  end, so swapping its `sin` needs a per-sample wrap first.
- Why not a lookup table: same op count with linear interpolation, worse accuracy (3e-7 at 4096
  entries), memory traffic against the audio buffers, bounds checks on JS.
- The modulators followed (2026-09-15, later the same day): the FM modulator (`FmRenderer`), both
  vibrato LFOs (`VibratoRenderer`, `VibratoModIgnitor`), both tremolo LFOs (`TremoloRenderer` via
  `lfoNorm`, the `tremolo` ignitor) and the grain Hann window (`cos(2πp)` as `fastSin(2πp + π/2)`,
  inside the fold for `p` in `[0, 1)`). The FM modulator wrapped at block end only and the
  vibratos never wrapped a negative rate, which `sin` tolerated and the polynomial does not: each
  now wraps per sample with the same hoisted `safeWrap` as the stacks (`!(abs(inc) < TWO_PI)`).
  Guard: `ModulatorPhaseWrapSpec` (FM against a `sin` accumulator, both signs past the sample
  rate, negative LFO rates over seconds, both vibrato buffer paths). Measured (`voices` A/B):
  FM bell 0.0026 -> 0.0022, vibrato + tremolo pad 0.0064 -> 0.0042, the lead 0.022 -> 0.019.
  `TremoloRendererSpec`'s DrunkenSailor guard now holds the shipped tremolo to the polynomial's
  bound instead of bit-identity (the four spellings still must be bit-identical to each other).
- The phase-pool selection at note-on (`Ignitors.kt`, `im += g * sin(a)`), the phaser LFO (two
  `sin` per block) and the `sineshaper` waveshaper keep the library `sin`: not per sample, or
  not a phase.

## Silence culling (2026-09-15)

- A voice stops rendering once it is in its RELEASE and its own output has stayed under
  `VOICE_CULL_FLOOR` (-100 dBFS, the same constant as the cylinder's silence test) for the cull window (`VOICE_CULL_SECONDS` = 50 ms, per voice via
  `cull(seconds)`, off via `noCull()` = `VOICE_CULL_NEVER`; a voice with a `tremolo` is excluded
  unless `cull` is set). Measured in `SendRenderer` (`BlockContext.voiceOutputPeak`, only until
  the voice has been heard and then in its release, pre solo-multiplier, bounded by the largest send), decided in
  `Voice.render`, counted by `VoiceScheduler.culledVoicesTotal`; the benchmark tables carry a
  `culled` column.
- **A culled voice is a zombie, never an early removal.** It keeps its active-list slot and renews
  its orbit lease until its scheduled end. Removing it early reorders the active list, and the
  orbit lease goes to whoever renders first after an owner dies: measured on Der Schmetterling, a
  culled hat changed which of guitar 3 and the bass owned orbit 3, a -32 dBFS difference. With the
  zombie the null-diff against no culling sits at the floor. Lesson: any change to WHEN a voice
  leaves `active` is a mix change on every orbit with mixed bus configs.
- **The gate is never culled, and a voice that has not sounded yet is never culled** (`Voice.heard`
  latch). Decided over the July `cullAfter` fraction: the gate says "told to stop", the latch says
  "has started"; together they need no parameter and protect slow attacks, delayed sample onsets and
  ignitor attacks that outlive a short gate. Residual risk: a release tail with gaps longer than the
  window (a `tremolo` voice is excluded for that reason).
- Where it pays (Der Schmetterling, 2026-09-15 rig suite): sample drums with 2 s releases (85 to 90%
  silent), the Orchestertrommel (about 40%). The marimba was estimated at 20 to 40% and culled
  nothing in the day's rig suite (`docs/benchmarks/2026-09-15_172412`, lead (marimba) 0 of 128). Where it cannot: the guitars,
  whose notes live 170 ms (gate 116 ms + 30 to 50 ms release) and are audible throughout.
- Guard: `VoiceCullingSpec` (mutation-checked: gate rule, pre-multiplier peak, frames-not-blocks,
  negative-means-never). Doors: `LangCullSpec`. Record: `docs/tasks-archive/2026-09/20260915-voice-culling.md`.

## Body / Vowel resonators + live-update fixes (2026-07-04)

- **Body & vowel are ORBIT-level Katalyst effects**, not per-voice filters: `KatalystBodyEffect` /
  `KatalystFormantEffect` (`cylinders/katalyst/`) run once on the summed orbit mix; the owner voice
  configures them via `VoiceLease` (first-writer-wins). `VoiceFactory` pulls `FilterDef.Body`/`Formant`
  OUT of the per-voice chain — its `toFilter` arms for them are unreachable (`error()`). The two
  effects are **intentional un-deduped twins** — change one, mirror the other.
- **Materials** = pure data in `BodyMaterials` (audio_bridge commonMain, moved there from sprudel by
  Katalyst step 3c on 2026-09-17): `names` / `descriptions` / `modesFor(name)` →
  `List<FilterDef.Body.Mode>(freq,db,q)`; the vowel twin is `VowelBands.bandsFor("<voice>:<vowel>")`
  (a bare name is the soprano register). `none` = reset (vowel `none` too). Both tables are read by
  `SprudelVoiceData.toVoiceData` for a voice AND by `KatalystSlots` for a declared Katalyst chain's
  `body`/`vowel` stage, so a name means one thing on both paths. Blend
  = `ParallelMixFilter(inner, mix, floor)`; `floor` user-settable via `bodyFloor()`/`vowelFloor()`
  (`FilterDef.Body/Formant.floor`, null → `BODY_FLOOR`/`VOWEL_FLOOR`), and BOTH `mix` and `floor` are
  coerced into [0, 1] by `ParallelMixFilter` (the pre-C4 raw extension above 1 is gone; `mix <= 0`
  bypasses bit-identically and never runs the inner filter).
  Sprudel fields grouped in `SvdBody`/`SvdVowel`. UI: `SprudelBodyEditorTool` (sprudel jsMain).
- **`names` is an INDEX SPACE since Katalyst step 5a-2 (2026-09-18).** A `body.material` /
  `vowel.vowel` chain slot carries the INDEX of a name in `BodyMaterials.names` /
  `VowelBands.names` (0 = `none`, out of range or non-finite = the stage off), so no string slot
  joins the wire. The conversion is `indexOf(name)` / `modesAt(index)` and `indexOf` / `bandsAt`,
  in ONE place next to each table, and the NAME path now goes THROUGH the index (`modesFor(n)` is
  `modesAt(indexOf(n))`), so the two cannot answer differently. `VowelBands.names` is generated:
  `"none"` plus the cross product of the five register spellings and the fifteen vowel spellings,
  76 entries. **Both tables build their band lists once and hand out one shared instance per
  entry**, which is load-bearing: `KatalystBodyEffect.configure` decides whether to rebuild the
  bank by comparing band lists, so identity short-circuits it instead of walking eight modes per
  note. Consequence: **append only, never reorder** either `names` list, because the index IS the
  wire encoding (and the editor dropdown order). Guard: `CatalogueIndexSpec` (audio_bridge).
- **A non-finite mix or floor is UNSET at the entry of BOTH `configure` functions** (2026-09-18,
  found in review). The declared path already substituted in `KatalystSlots.bodyDef`/`vowelDef`;
  the BORN-WITH path did not, and `body("wood", wet = "NaN")` reaches it as a real NaN
  (`"NaN".toDoubleOrNull()` is NaN, and `toVoiceData` guards a null mix, not a non-finite one).
  The owner re-offers its def every block, `mix != curMix` is true forever for a NaN, so the stage
  allocated two filter banks per block on the audio thread and restarted a 12 ms crossfade that
  never completed, while `ParallelMixFilter` read the non-finite amount as a fully dry 0.0 and the
  body was inaudible (8186 of 13380 counts on a minimal render). Being NULLABLE did not save the
  floor: a `Double?` pair of NaNs answers "not equal" on the JVM and on Kotlin/JS alike (measured).
  **The lesson for any stage that caches its config: compare and store the SUBSTITUTED values,
  never the raw input.** `KatalystGainEffect.configure` took the same guard in the same change
  (a non-finite factor is unset, and unset is unity). Guards: `KatalystBodyEffectSpec`,
  `KatalystFormantEffectSpec`, `KatalystGainEffectSpec`, `KatalystBodyNonFiniteWetSpec` (render).
- **Live-change declick**: `KatalystFilterSwap` crossfades the bank on any material/mix/floor rebuild.
- **Live-update double-voice fix**: `VoiceScheduler.replaceVoices` now dedups incoming voices vs
  already-active ones (`ScheduledVoice.isDuplicate` = startTime+data); grace window 50→200 ms in
  `KlangPatternScheduler`. Root cause + the per-playback engine model: `ref/architecture.md`.
- **Benchmarks**: isolated `Body`/`Vowel` cases in `EffectBenchmark`; a `+vowel(a)` case in the song
  benchmark. Body (8-band) is the priciest single filter (~= reverb), vowel ~62% of it. **The old
  "superimpose × body = super-additive" finding is now OBSOLETE** — body moved to orbit-level, so
  cost(body|super) ≈ cost(body|no-super) (applied once per orbit, not per copy). `analog` still
  multiplies (per-voice). Refresh: `docs/benchmarks/2026-07-04_*_song_jvm.md`.

## Song-level CPU benchmark + Der Schmetterling deep-dive (2026-07-03)

New song-level benchmark harness in the **root** module: `src/jvmMain/kotlin/SongBenchmark*.kt` +
`FrozenSongs.kt` (byte-exact frozen song code so the baseline doesn't move when `builtinsongs/*` change).
Run: `./gradlew runSongBenchmark [--args=voices|ladders|experiments|songs|all]`. It compiles real
song code → `KlangPattern` → drives the actual offline DSP graph, timing every block (medRTF =
steady-state avg, peakRTF = busiest block; first 32 blocks skipped to drop the delay-ring alloc spike).
Unlike `:audio_benchmark` (isolated effects from hand-made VoiceData) this measures whole voices/songs.
Full writeup: `docs/benchmarks/2026-07-03_der-schmetterling-cpu-analysis.md`.

**Findings (JVM; browser ≈ ×2.7):** Der Schmetterling's cost is ~98% in its 3 super-synth voices
(GTR2 > GTR1 > LEAD); bass/drums/pink are negligible. Cost ranking:
`superimpose` (voice-count multiplier — each copy re-runs the WHOLE per-voice chain; GTR2's nested
superimpose = 1088 voices) ≫ `body` (8-band parallel SVF/voice) ≈ `analog` (per-voice drift) >
multi-band filters > distort-oversample/reverb > `unison` (cheap — shares one effect chain) >
`pipeline("pedal")` (~free, just reorders). **Key combination = per-voice effect (`body`/`analog`)
UNDER a `superimpose` stack → paid once per copy (proven super-additive by a 2×2).** Fix levers: cut
superimpose depth on the guitars, prefer `unison`/`spread` over width-superimposes, don't recompute
body/analog per superimposed copy.

## Current Status

- **JVM**: Full audio output via javax.sound.sampled ✅
- **JS**: Full audio output via Web Audio API + AudioWorklet ✅
- Synthesis: oscillators (sine, saw/ramp/square/pulze/triangle + raw zaw/zamp, super* unison,
  karplus, noise family) + sample playback. Sine partial banks (2026-09-07): `IgnitorDsl.Sine` carries
  `harmonics`/`octaves`/`suboctaves` counts with rolloffs, `fundamental` gain and `analogSpread`;
  `Ignitors.sinePartials` renders the bank in one pass, the runtime builds the plain `SineIgnitor` for
  literal defaults (`Sine.isPlainSine()`), partials at Nyquist are silent. Plan and decisions:
  `docs/plans/sine-partial-banks.md`; guard: `SinePartialBankSpec` (golden test against the hand-rolled
  Der Schmetterling stack).
- Effects: delay, reverb, phaser, compressor, ducking, distortion, bit-crush, tremolo

## One drift-lane container: `DriftLanes` (2026-09-10)

Every multi-voice oscillator takes its analog drift from one component
(`audio_be/.../ignitor/DriftLanes.kt`): N own `AnalogDrift` lanes plus one shared lane, blended per
sample by `analogSpread` with constant-power weights, in ratio-minus-one space. The unison stacks,
the sine partial bank and the superpluck strings all plug into it, and `analogSpread` is a knob on
the whole super family now (`supersaw`, `supersine`, `supersquare`, `supertri`, `superramp`,
`superpluck`), same word and same 0 to 1 scale as on `Osc.sine`: 0 is one shared walk, so the stack
wobbles as a single physical oscillator and its unison detune stays static; 1 is a lane per voice,
the default.

**What the default does and does not promise.** Depth, spread and character at spread 1 are exactly
what they were. A seeded RENDER is not. A container takes one int off the voice rng when it is built
(the shared lane's seed, below), so every drifting oscillator seeds its lanes one draw later than it
did before this change. On a unison stack that int lands right before `drawGainJitterFor`, so the
per-voice GAIN BALANCE of every drifting supersaw, supersine, supersquare, supertri and superramp is
redrawn as well, inside its usual range (`SUPERSAW_GAIN_JITTER` 0.15, up to 15 percent per voice
before renormalisation), not just the drift walk. A stack with `analog = 0` builds no container,
draws nothing extra and is untouched. `SuperStackDriftSpreadSpec` pins
one supersaw case sample for sample against the engine as it stands now, which catches an accidental
change to the drift path; it is not a claim about the pre-DriftLanes sound, and the sine stack, the
partial bank and the pluck have no pin of their own. The shipped layer this actually reaches is Der
Schmetterling's bass harmonics (`harmonics(9, 1.0).fundamental(0).analogSpread(0.1)` under
`.analog(feel)` with feel 12): same depth, same spread, different walks.

Two things a reader needs. The endpoints are EXACT, not a limit of the blend: at spread 1 only the
own lane is advanced, at spread 0 only the shared one, which makes spread 0 measurably CHEAPER
(JVM, supersaw 8v with analog: 5.34 against 6.90 µs per block; both platform runs are in
`docs/benchmarks/2026-09-10_drift-lanes_jvm.md` and `..._nodejs.md`). And the draw order is part of
the contract: one int
for the shared lane's SEED at construction, then an own lane whenever `ensureLanes` first reaches its
index. The shared lane is built lazily from that seed and takes nothing further from the voice
stream, so WHEN the spread first drops below 1 cannot shift what any other consumer of the voice rng
gets: without that, lowering `analogSpread` on a superpluck re-rolled its string excitation bursts.
A stack that shrinks retires those lanes (`retireLanes`) and rebuilds them fresh on regrow, so a
returning voice attacks in tune, and the superpluck does the same, because a regrown string
re-plucks. The bank never retires: a partial that comes back resumes its walk, which is inaudible at
cent scale and keeps a count sweep from re-seeding the whole spectrum.

**The JS lesson, a sibling of the loop-shape one below.** The first cut read the blend's per-block
values (the two weights, the two mode flags, the lane out of its array) off the container INSIDE the
sample loop, through a public inline method. On the JVM that is free; on Kotlin/JS it cost a drifting
8-voice supersaw about 16 percent and the superpluck about 12, measured against the pre-change engine
in the same sitting. The fix is the same shape as the loop-shape lesson: hoist every per-block value
into a LOCAL before the sample loop (`ownLane(n)`, `sharedWalk()`, the two weights) and blend through
one `inline fun driftStep(...)` whose inputs are all locals. Node run-to-run variance is wide enough
(two runs of one engine differed by 28 percent on a row) that a claim like this needs the untouched
`sine+analog` row as a control and a ratio, not a raw number.

`PolyAnalogDrift` went with this change. Nothing ever used it: it advanced all lanes sample-major
while every hot loop here is voice-major. Its rationale, that independent lanes are what keeps a
unison stack organic, is now the reason `DriftLanes` defaults to spread 1.
Task: `docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`.

## Loop shape beats block-pass count (2026-09-07, sine partial banks)

`IgnitorBenchmark` case `sine-harmonics7` against `sine-harmonics7-tree` (the hand-rolled
`Sine + Sine(2f)*1/2 + ...` tree it replaces): a sample-major bank loop (outer loop over samples,
inner loop over partials reading/writing `phase[]`, `gain[]`, `inc[]`) rendered at 41 µs/block, the
tree at 24. Partial-major (outer loop over partials, inner tight loop over samples with the phase
in a local, the `SineStackIgnitor` shape) brought the bank to 22 µs. So: twenty-one block passes
cost less than one loop that keeps its phase in an array. When writing a multi-voice or multi-partial
oscillator, render voice-major with locals, accumulate into the buffer (`if (first) s else buffer[i] + s`),
and sample any per-sample shared state (here the shared drift walk) into a scratch array first.
`sin()` dominates the rest; the 2-trig-per-sample recurrence in `docs/plans/sine-partial-banks.md`
§5.3 is the remaining lever, gated on a song profile.

## VCA Gain De-click Smoother (2026-06-08)

`EnvelopeRenderer` (amp VCA) now runs a one-pole low-pass on the final gain to
de-click ADSR segment joins. Root cause of the "plop": the shape curves are
value-continuous (C0) but **not slope-continuous (C1)** — at the attack→decay
peak, gate-off, and instant cutoff the gain changes slope abruptly. That corner
is a fixed-size event; on a **low note** the slow carrier can't mask it, so it
reads as a "plop" (2nd-difference corner/floor ratio ~525x at 40Hz vs ~4x at
880Hz). `exp` curves are worst (steepest joins).

- Constant `ENV_DECLICK_SECONDS = 0.001` (1ms) in
  `audio_bridge/constants/EnvelopeDefaults.kt` (moved there 2026-08-11 — it is a
  `StageDsl.Vca` wire default). The MATH stayed in `AdsrCurveMath.kt`:
  `envDeclickCoeff(declickSeconds, sampleRate)`, `adsrExpNorm`, `ADSR_EXP_NORM` and both `adsrExpShape` overloads.
  Tunable by ear like `ADSR_EXP_K`. The 25x-corner- reduction-at-40Hz / 0-residual-tail measurement was taken at 0.5ms.
- `Voice.Envelope` gained `smoothedLevel` + `smoothPrimed` state. Primed to the
  **first rendered gain** (not env.level) so always-on voices and mid-phase block
  starts don't fade in; only segment-join corners get rounded.
- Applies to the amp VCA only. `EnvelopeCalc` (filter-mod, control-rate) and
  `IgnitorEnvelopes` (per-ignitor `.adsr()`) are NOT de-clicked — separate
  surfaces, less audible. Hard cuts (`releaseFrames == 0`, chokes) ARE de-clicked
  (single code path, 0.5ms fade instead of 1-sample stop) — chosen deliberately.
- Guard: `EnvelopeDeclickSpec` (renders through the real renderer, asserts gain
  per-sample slew < 0.1). `EnvelopeTest` rewritten to assert phase *behaviour*
  (monotonic direction, settled endpoints, the de-click fade) since mid-ramp
  values now lag; exact raw-curve shape stays in `EnvelopeShapeTest` (generator).

## Ignitor ADSR knobs — declick + expK as Slots (2026-07-04)

**Superseded in part (2026-09-25, phase 3 step 3c):** `expK` is removed everywhere on the Ignitor side
(node, slot, door, `Ignitor.adsr`); every exp stage bends at `ADSR_EXP_K`. `declickSeconds` stays, written
by the chain `adsr` builder's `declick`. See the 3c entry at the top.

`.adsr(...)` (per-ignitor envelope, `IgnitorEnvelopes.AdsrIgnitor`) gained two knobs on
`IgnitorDsl.Adsr`, wired as **`IgnitorDsl.Slots` Params** (like the noise knobs), so they're
`oscParam`-addressable, patternable, and discoverable via `collectParams()`. Both
**behaviour-identical by default**:

- `declickSeconds` (`Slots.declickSeconds`, default `0.0` = off — this surface is intentionally
  NOT de-clicked; see the VCA entry above). >0 runs the same one-pole (`envDeclickCoeff`) on the
  output gain, primed to the first rendered level (no fade-in on always-on / mid-phase starts).
- `expK` (`Slots.expK`, default = `ADSR_EXP_K`, the single declaration in `audio_bridge/constants/`). Feeds
  the parameterized `adsrExpShape(x, k, norm)` the amp VCA already used.

Both are `IgnitorDsl` fields read per-block via `readParam` in `AdsrIgnitor` (declick coeff + `expNorm`
derived once per block — NOT ctor-precomputed, since they can now be modulated). KlangScript
`.declickSeconds(x)` / `.expK(x)` take `IgnitorDslLike` (copy-onto-`Adsr` or wrap, same idiom as
`adsrCurves`). Guards: `AdsrIgnitorKnobsSpec` (defaults identical, declick rounds the attack→decay
corner, larger expK steepens the exp decay, + oscParam override reaches both slots), `StdLibOscTest`
(dual-language build), `IgnitorDslWireCodecSpec` (round-trip). First of the `engine-tuning-profile`
Part-A wrapper knobs; filter feel knobs + analog-drift carriers still open.

## Oscillator Engine Unified (2026-06-05)

The `audio_be` oscillator code was consolidated (branch `dedicated-cycle-time`) — see
`docs/tasks-archive/2026-06/20260605-oscillator-engine-unification.md` for the full writeup:

1. **One shape engine** — `analogSawShape` + `pulseTrapezoidShape` → one `waveTrapezoid`
   (`DspUtil.kt`); `SawVoiceState` + `PulseWaveState` → one `WaveVoiceState`. One `WaveIgnitor` behind
   saw/ramp/square/pulze/triangle (+ raw zaw/zamp). Finite-slope edges, no PolyBLEP.
2. **Control-rate value on `interface Ignitor`** — `controlRateValueOrNull(freqHz, ctx)` (non-null iff
   block-constant) + `blockStartValue(...)`; the `ControlRateIgnitor` marker is gone. Pointwise
   combinators fold, so control-rate param reads (freq/analog/duty/detune/spread) no longer render a
   scratch buffer.
3. **One unison engine** — all five super-oscillators (supersaw/ramp/square/tri/sine) share
   `DetunedStackIgnitor` (→ `TrapezoidStackIgnitor` → `SawStackIgnitor`/`PulseStackIgnitor`;
   `SineStackIgnitor`). Each variant has its own `SUPER*_{SIDE_ATTEN,GAIN_JITTER,DETUNE_POWER}` consts
   in `OscillatorTuning.kt`, seeded to the supersaw values. supersquare/supertri/supersine dropped
   PolyBLEP/flat-gain → now center-dominant, per-voice drift, on-note tuning (sound changed by design).

## Architecture Decisions

**State lives at the granularity it is bound to** (FE/BE state placement, 4 steps done 2026-08).
playbackId-bound state sits in `PlaybackEngine` (BE) and the per-playback controller (FE);
global state in `AudioBackendContext` (BE) and `KlangPlayer` (FE). Share only what is expensive
to recreate AND safe to outlive a playback: samples (content-keyed) and built-in oscillators.
Custom oscillators and engines are per-playback so they are collected with the engine. Do not
remove the past-cutoff in `VoiceScheduler.promoteScheduled`: it stops `ReplaceVoices` from
re-promoting already-played voices (duplicate burst). Open follow-up:
`docs/tasks/future/worklet-clock-divergence.md`.

- **Block-based processing**: fixed-size blocks (128–256 frames). No per-sample allocation in hot paths.
- **Ring-buffer IPC**: `KlangCommLink` uses two `KlangRingBuffer`s — no locking between threads.
- **Voice pipeline**: `Voice` interface + `VoiceImpl` runs a **Pitch → Excite → Filter** pipeline.
  Filter stage is a composable `List<BlockRenderer>` built by `buildFilterPipeline()`.
  Pitch stage is still inline (pending extraction). Excite delegates to `Ignitor`.
- **Cylinders = effect buses**: up to 16 mixing channels, each with independent delay/reverb/phaser/compressor/ducking.
- **Master limiter**: −1 dB threshold, 20:1 ratio, **5 ms lookahead + 5 ms gain-smoothing**, 100 ms release — always
  last in chain, on the summed mix. The lookahead delays the whole output by 5 ms (uniform, so nothing desyncs). The
  *authored* `limiter` stage (`Master(m => m.limiter(...))`) differs on purpose: no lookahead, 1 ms one-pole attack, because it is per-playback.
- **`NullLiteral` / singletons**: `audio_bridge` data types use data classes; expect/actual for platform types.
- **Every DSL is immutable at construction time (maintainer principle, 2026-09-05)**: nodes,
  builders, `MasterDsl`, `PipelineDsl`, patterns. A "mutating" call returns a new instance; never
  a `var`, never a mutable builder, never a `MutableList` escaping a DSL type. Why: it removes an
  entire bug class (shared-state mutation at a distance) and therefore an entire chapter of
  explaining; composition falls out of it. Runtime data may be mutable for performance
  (single-owner `SprudelVoiceData`), that is engine-internal and stays. Receiver lambdas were
  parked for exactly this reason (they need mutable builders).
- **Configure lambdas + builder types (decided 2026-09-05, BUILT 2026-09-06, steps S1 to S7)**: sub-type knobs
  (`analog`, `voices`, `spread`, `phasePool`, `band`/`tap`, `wet`/`dryFloor`, master stage and
  pipeline stage knobs) move OFF `IgnitorDsl`/`MasterStageDsl`/`StageDsl` onto immutable
  `Osc*Builder`/`EqBuilder`/`Master*Builder`/`Pipeline*Builder` classes in `klangscript-libs`,
  annotated for KlangScript directly in `klangscript-libs` (module split 2026-09-06). `MasterFx`, `Stage`,
  `Master.of`, `Pipeline.of` and all 17 sub-type extension objects are DELETED, no back-compat.
  Plan: `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`.

## Filter Saturation Dead-End — Linear SVF is the Right Choice (2026-05-28)

Two attempts to add tanh saturation to `SvfLPF`/`SvfHPF` (for "analog warmth"
inside the filter) both failed and were reverted:

1. **Saturating `v1` in the `ic1eq` integrator state update** broke the SVF
   spectral function. `ic1eq` no longer represents the linear bandpass output,
   so `v2` (LPF tap) becomes a corrupted-LPF; the HPF formula `v0 - k·v1 - v2`
   subtracts a corrupted-LPF from input → lows leak through, notch appears at
   cutoff. Symptom matched user reports exactly ("dip in the middle, lows
   passing through HPF").
2. **z⁻¹-delayed saturated feedback** (Zavalishin §5.5 form,
   `bpFb = tanh(drv·v_BP)/drv` used as `v_HP = (v0 - k·bpFb - g·ic1eq - ic2eq)/(1+g²)`)
   — `fastTanh` hard-caps `bpFb` at ±1/drv ≈ ±0.286. Linear feedback `k·v_BP`
   grows with `v_BP` to provide damping; capped feedback loses ~95% of its
   damping strength at hot resonance peaks. Result: filter goes unstable at
   Q ≥ 5 with `analog>0`, peak amplitude ~40× linear.

**Conclusion**: filter feedback nonlinearity needs either (a) a non-hard-capping
saturator like `asinh` (growth-friendly), (b) proper Newton iteration on the
implicit equation, or (c) oversampling. None are trivial. **Filter is now
purely linear** at all `analog` values; the `analog` parameter and
`bpFb`/`g`/`invOnePlusGsq` infrastructure remain in `BaseSvf` / `SvfLPF` /
`SvfHPF` for future re-introduction.

**Where the "warmth" comes from now** — and it works: upstream `.distort()` /
`.onepole()` (ex-warmth) / `.clip()` shapers, oscillator OU drift (per-voice), per-voice
cutoff offset (`FILTER_CUTOFF_OFFSET_PER_ANALOG`), and the coefficient ramp
(`FILTER_SMOOTH_SAMPLES`) on cutoff changes. These collectively give the
"plastic pipe → wooden warm" transformation without needing the filter itself
to be nonlinear.

**Files touched**: `audio_be/.../filters/LowPassHighPassFilters.kt` (kdoc on
`SvfLPF`/`SvfHPF` documents the dead-end inline so future attempts know to
either change topology or change saturator), `docs/agent-tasks/plastic-pipe-hunt.md`.

## Numerical Safety Contract + Distort DC-Lock Fix (2026-04-27)

Established `SAFE_MIN = 1e-15f` / `SAFE_MAX = 1e15f` as the engine's numerical
safety bounds (matches SuperCollider/ChucK convention — see
`audio/ref/numerical-safety.md` for the reasoning and framework precedents).

Two helpers in `Ignitor.kt`: `safeDiv(d)` (sign-preserving magnitude clamp ≥ SAFE_MIN,
scrubs NaN) and `safeOut(v)` (output clamp to ±SAFE_MAX, scrubs NaN to 0).
Applied to divisor-class ops (`Div`, `Mod`, `Recip`) and output-clamp ops
(`Times`, `Pow`, `Exp`, `Sq`, `mul-by-const`).

Distort/clip path now applies the DC blocker **unconditionally** (was previously
only for `diode`/`rectify` shapes). Fixes user-reported rail-lock at extreme
drive amounts where output got stuck at -1 due to envelope-decay asymmetries.
Trade-off: transient overshoots up to ~2x at sharp transitions of square-like
signals — existing tests updated, master limiter handles the spike.

**Edge-overshoot bounder moved to ignitor output (2026-04-28)**: the 2×
DC-blocker transient was audible as per-cycle clicks on heavy-drive
`distort()`/`clip()` users (reported on the rhythm-pattern guitar ignitor in
`TestTextPatterns.kt`). The fix is a single `coerceIn(-1f, 1f)` (hard clip)
pass at the **`IgniteRenderer` boundary** — caps the entire ignitor output
once per output sample at base rate. This is cheaper than tanh-saturating
inside each `distort()`/`clip()` stage at oversampled rate, and identity for
`|x| ≤ 1` so existing tests + clean ignitors are untouched.

- **Why hard clip vs soft tanh wrap**: tanh compresses everywhere
  (`tanh(1)=0.78`, ~22% reduction at peak), changing exact-amplitude
  expectations across ~20 voice/envelope tests. Hard clip is identity for
  `|x| ≤ 1` (preserves all those expectations) and only acts on the rare
  rail-edge transients above ±1 — exactly the click case. Cheaper too
  (`coerceIn` is 2 FLOPs vs `fastTanh`'s ~6).

- **Why at the wrapper, not per-stage**: per-stage cap would fire inside the
  ignitor on `dcOut` peaks of ~±2; the wrapper fires on the final ignitor
  output (post the user's internal LPF/HPF/ADSR), once per output sample at
  base rate (vs `factor×` per oversampled sample inside each distort). Per-stage
  code is back to the pre-2026-04-28 simple `dcOut.toFloat()` write — no
  in-oversampler placement, no α rate-compensation, no per-stage tanh.

- **Trade-off**: hard clip adds a small derivative-discontinuity at ±1, which
  technically aliases. Mitigated because the wrapper sees the signal *after*
  the user's internal LPF/HPF (already band-limited), and the master limiter
  on the cylinder bus handles residuals.

- **Industry context** (researched while designing this): Surge XT and Vital
  don't post-DC-block at all — Klang's click symptom only exists because of
  the 2026-04-27 rail-lock guard. ChowDSP / Jatin Chowdhury use
  Antiderivative Antialiasing (ADAA) to side-step both oversampling *and*
  post-shape DC issues — captured as future direction.

- **Future**: ADAA migration to replace the oversampler+DC-block stack
  entirely (see `chowdsp_waveshapers` and `jatinchowdhury18/ADAA`). Drive
  smoothing (Surge XT's `lipol` pattern) for parameter-change clicks. Soft
  saturator option (tanh) at the wrapper as opt-in for "warmth" character.

- **Scope**: the wrapper lives in `audio_be/.../voices/strip/ignite/IgniteRenderer.kt`.
  `Ignitor.distort()`/`Ignitor.clip()` are responsible only for shape +
  DC-block (no longer for bounding to ±1) — see
  `audio_be/.../ignitor/IgnitorEffects.kt`. The legacy filter-stage
  `DistortionRenderer` (covered by `effects/DistortionSpec.kt`) is a separate
  code path and unchanged. End-to-end coverage: `IgnitorsTest.kt` has both a
  direct-call test (asserts `< 2.5`, the pre-cap envelope) and an
  IgniteRenderer-wrapped test (asserts `< 1.05`, the in-engine invariant).

- **Regression harness — grow it over time**:
  `audio_be/src/commonTest/kotlin/ignitor/GuitarClickHuntTest.kt` is the
  click-hunt harness. **Standing intent: keep adding setups to it whenever a
  new click symptom is reported or a new ignitor archetype is introduced.** It
  is the safety net for any future change to `distort`/`clip`/`IgniteRenderer`
  / DC-blocker / oversampler.

**Pitch-mod factories closed (same day)**: code review identified four pre-existing
hazards in `PitchModFactories.kt` (`vibratoModIgnitor`, `accelerateModIgnitor`,
`pitchEnvelopeModIgnitor`, `fmModIgnitor`) with the same overflow pattern. All
four now apply `safeOut`/`safeDiv`. Stress test for extreme depth exposed a
**latent O(N) hazard in `wrapPhase`** (`DspUtil.kt`) — the `while (p >= period)
p -= period` loop hung the audio thread for huge phase increments coming out of
a SAFE_MAX-clamped pitch-mod ratio. Rewritten as O(1) modulo with `Inf`/`NaN`
recovery; common-case fast subtraction path preserved.

**Generalised lesson** (see `audio/ref/numerical-safety.md` "Why this matters"):
the per-op safety contract guarantees **finite + bounded**, NOT **small**. Any
audio-rate consumer of a value clamped to `±SAFE_MAX` must run in O(1)
regardless of the value's magnitude — no subtraction loops, no retry loops, no
state scaling with input. The `wrapPhase` rewrite is the canonical pattern.

## Ignitor Composable Architecture (2026-03-19)

New package `audio_be/.../ignitor/` — composable per-voice effect combinators.
Files: Ignitor, IgniteContext, ScratchBuffers, IgnitorEnvelopes, IgnitorFilters,
IgnitorEffects, IgnitorPitchMod, IgnitorFm. Phase 0+1 complete (additive, nothing wired in yet).

## Ignitor Param Slots — "Everything is a Signal" (2026-03-26)

All numeric ignitor parameters converted from `Double` to `IgnitorDsl` (Param slots).
`IgnitorDsl.Param(name, default, description)` is a new leaf node that produces a constant signal
by default, but can be replaced with any ignitor subtree for audio-rate modulation.

Key changes:

- **`ParamIgnitor`** runtime class fills buffer with constant value
- **`getParamSlots()`** walks DSL tree to discover all Param leaves (for generic UI)
- **Gain separated from oscillators**: factories produce raw output, gain applied via `withGain()`
- **`analog`** param: lazy `AnalogDrift` init on first block via `initAnalogDrift()`
- **Control-rate params** (filter cutoff, ADSR times, etc.): read once per block via `readParam()`
- **oscParams override**: `toIgnitor(oscParams)` propagates through tree; Param nodes check map by name
- **New DSL nodes**: Distort, Crush, Coarse, Phaser, Tremolo, Vibrato, Accelerate, PitchEnvelope
- **Convenience wrappers**: Double-accepting extension functions on IgnitorDsl still work

### Known Issues to Revisit

- ~~**Filter envelope release jumps from sustainLevel**~~ **FIXED (2026-03-23)**:
  `FilterModRenderer` now calculates the actual envelope level at gate end using
  `levelAtPosition()`, matching the amplitude ADSR's capture behavior.
- **`pitchEnvelope()` per-sample `pow()`**: `2.0.pow(amount * envLevel / 12.0)` called per sample.
  Expensive (~50-100ns per call). Could optimize sustain phase (constant value, compute once).
  `accelerate()` was already optimized to multiplicative stepping.
- **phaseMod save/restore lacks exception safety**: No try/finally wrapper around the
  save→set→generate→restore pattern in vibrato/accelerate/pitchEnvelope/fm combinators.
  Low risk (exceptions in audio hot paths are rare and fatal), but not structurally safe.
- **Stringly-typed distortion shape**: `distort(amount, shape = "soft")` uses String for shape
  selection. Typos silently fall through to default. Consider enum in the future.

## BlockRenderer Pipeline Architecture (2026-03-23)

Voice rendering refactored into **Pitch → Excite → Filter** pipeline using composable `BlockRenderer` stages.

Key files:

- `voices/strip/BlockRenderer.kt` — `fun interface BlockRenderer { fun render(ctx: BlockContext) }`
- `voices/strip/BlockContext.kt` — shared context (buffers, timing, ignitor)
- `voices/strip/EnvelopeCalc.kt` — shared control-rate envelope calculation
- `voices/strip/pitch/` — VibratoRenderer, AccelerateRenderer, PitchEnvelopeRenderer, FmRenderer
- `voices/strip/excite/IgniteRenderer.kt` — wraps Ignitor as BlockRenderer
- `voices/strip/filter/` — FilterModRenderer, AudioFilterRenderer, EnvelopeRenderer, FilterPipelineBuilder

Status: **Complete.** `Voice` (merged from Voice interface + VoiceImpl) runs a `List<BlockRenderer>` pipeline:
Pitch renderers → IgniteRenderer → Filter renderers → SendRenderer.
Bus pipeline: composable `KatalystEffect` pipeline (`cylinders/katalyst/`); since 2026-09-17 (Katalyst DSL
step 2) the cylinder builds its chain from `KatalystDsl.classic` through `KatalystChainBuilder` into a
`KatalystChain` (stage order from the DSL, duck outside the list). Declared chains apply from step 3, and
since step 5b-1 (2026-09-19) EVERY chain takes its knobs from the orbit's param state, the born-with one
included: the voice's bus fields are not a knob source any more (`docs/tasks/katalyst-dsl.md`, and the
top entry of this file).
`VoiceScheduler` split into `VoiceScheduler` (scheduling) + `VoiceFactory` (voice construction).
Legacy effect filters (BitCrush, SampleRateReducer, Distortion, Tremolo, Phaser) replaced by
BlockRenderer implementations. ~426 tests across 35 files.
See `docs/agent-tasks/audio-pipeline-open-topics.md` for remaining open topics.

## Distortion Shape Catalog Extension (2026-05-21)

Added 7 new waveshapers to `ClippingFuncs` (+ `DistortionShape` enum + parser dispatch):

- **softSat** — `x / √(1+x²)`. Gentler than softClip; close to identity at low levels.
- **tube** — Shifted-tanh asymmetric: `(fastTanh(x+0.5) − fastTanh(0.5)) / (1 + fastTanh(0.5))`.
  Normalised so negative rail = −1, positive peak ≈ +0.37. Generates even harmonics + DC.
- **linearFold** — Triangle wavefolder, period 4, identity in [−1,1]. Sharper creases than `sineFold`.
- **zeroSquare** — `fastTanh(8·x)`. Crossover-region timbre, near-square at high drive.
- **sineShaper** — `sin(π·x/2)`. Normalised fold; peak at x=±1, folds outside.
- **asym** — Piecewise polynomial: positive cubic clip, negative sqrt-knee. Even harmonics + DC.
- **stompBox** — Asymmetric diode-pedal: `1 − e^(−1.5·x)` pos / `−(1 − e^(3·x))` neg. Pedal grit.

**Tube bias constant lesson**: Tube uses Padé-consistent constants (`fastTanh(0.5) = 13.625/29.25 ≈ 0.46581` and
`1/(1 + fastTanh(0.5)) = 29.25/42.875 ≈ 0.68222`), NOT the real `tanh(0.5) ≈ 0.46211`. Necessary so `tube(0) = 0`
exactly when the underlying shape is the Padé approximation. If `fastTanh`'s Padé form ever changes, these
constants must be recomputed (or `tube(0) ≠ 0` will fail a bounds test).

Parser accepts canonical lowercase names + underscore/short aliases (e.g. `linearfold`, `linear_fold`, `lfold`;
`zerosquare`, `zero_square`, `square`; `stompbox`, `stomp_box`, `stomp`). See
`DistortionShape.parseDistortionShape()`.

SVG visualisers in `sprudel/.../SprudelDistortEditorTool.kt` + `SprudelDistortShapeEditorTool.kt` mirror the
shapes with the *real* `tanh` (their local helper), so they use real-tanh constants for tube — different
numeric constants from the engine, same logical behaviour (visualisation reference, not audio).

## Ignitor Variants — Dispatch on `soundIndex` (2026-05-22)

New `IgnitorDsl.Variants(children: List<IgnitorDsl>)` sealed-interface node
(`audio_bridge/.../IgnitorDsl.kt`). Lets a single ignitor expose multiple
flavours selectable per note — same mechanism that picks sample-bank variants
(`bd:0`, `bd:1`), now extended to ignitor graphs.

- `cache.soundIndex` rides on `IgnitorBuildCache` (per-call invariant, not
  threaded through every recursive call).
- `toExciter(oscParams, soundIndex = 0)` — new optional param; default 0.
- `buildIgnitor` dispatches `Variants` in the leaf prologue (no cache entry on
  the Variants node itself): `children[cache.soundIndex.mod(children.size)]`.
  Kotlin stdlib `Int.mod` = floor-mod (negative wraps from the end).
- `IgnitorRegistry.createExciter` passes `data.soundIndex ?: 0` into
  `toExciter`. Missing soundIndex → variant 0 (intuitive default).
- `maxReleaseSec()` takes max across all children (conservative — voice may
  live longer than the picked variant needs, silent tail harmless).
- `buildRaw` errors loudly if Variants ever leaks past `buildIgnitor`.

Nested Variants all dispatch on the same `soundIndex` (single switching axis).
Empty children list throws at build time. Shared post-effects like
`Osc.variants(a, b).lowpass(400)` wrap whichever variant got picked.

Archive: `docs/agent-tasks-archive/2026-05/20260522-ignitor-variants.md`.

## Sprudel-side scale + variant split (2026-05-25)

Follow-on to the Variants work. With variants in place, `soundIndex` started
serving two unrelated jobs: scale-step input to `.scale()` AND variant pick to
`Variants` / sample banks. Resolved by splitting on the sprudel side without
touching the bridge.

- `SprudelVoiceData.resolveNote()` now parses `value` lazily at consumption
  time: `step:variant:gain` form when `value` is a String. Step becomes the
  scale-step input; variant overrides `soundIndex`; gain overrides `gain`.
  Parsed null parts never overwrite existing voice fields.
- Priority inverted: `n = newIndex ?: value?.asInt ?: soundIndex`. Value
  wins; soundIndex is only used as the step input when nothing else provides
  one (preserves the strudel-port `n("0").scale(...)` path).
- Scale branch in `resolveNote()` clears `soundIndex` only when soundIndex
  itself was the step source. When `value` provided the step, soundIndex
  survives — it's a variant override now, not a consumed step.
- `applyNote` empty branch (`.note()` reinterpret) gained an "already
  resolved" guard: skip `resolveNote` when `note != null && value == null`
  to prevent double-resolution wiping the variant.
- `nMutation`, `voiceValueModifier`, `noteMutation`, `soundMutation`, and the
  audio_bridge `VoiceData.soundIndex: Int?` type all unchanged. JsCompat
  baseline preserved.

Canonical scale + variant syntax: `seq("0 2 4 4:1 5:1").scale("c4:minor")`
(notes c/d/e/e/f, last two on variant 1).

## Lessons Learned

- `KlangTime.internalMsNow()` is monotonic, NOT wall-clock — use only for relative timing.
- JS target requires ES2015 classes for AudioWorkletProcessor inheritance (KMP default is ES5 — override needed).
- `MonoSamplePcm` is always mono; stereo is handled at the `Cylinders` pan/mix level.
- `FilterDefs.addOrReplace()` is additive — calling it twice with the same filter type replaces, not duplicates.
- `VoiceData` fields are nullable with defaults — omitting a field means "use engine default".
- The `duck.orbit` slot in `VoiceData.katalystParams` names the orbit whose voices duck this one (the `duckCylinder` field left in step 5b-3); ducking is cross-cylinder sidechain.
- `VoiceData.soundIndex: Int?` is the universal variant channel — consumed by `SampleRequest` for sample-bank picking
  AND by `IgnitorRegistry.createExciter` → `IgnitorDsl.Variants` dispatch.
