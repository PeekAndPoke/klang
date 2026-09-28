# Built-in instruments, and the end of the Pipeline DSL (phase 3)

> **Closed 2026-09-28, archived: phase 3 is done.** Steps 1 to 10 and 12, the step 12 cleanup and the test
> consolidation landed (section 9); step 11 was deferred by the maintainer. The step 12 plan is archived beside this
> file (`20260928-phase3-step12-master-as-katalyst.md`). The release-note table (section 3c) stays here as the
> record of what the merge of `engine-redesign` changes for a user's own script. Sections 3b (the door shapes) and
> 5b (the off-value table) moved to living homes: `.claude/skills/dsl-design/door-shapes.md` and
> `audio/ref/off-values.md`; the rules register, `/dsl-design` and the code's KDoc point there.
>
> Where each open item now lives:
>
> | item | status | home |
> |---|---|---|
> | Step 11, the editor diagnostics (section 9; the wet-slot string and the silent rows of 3c) | deferred | `docs/tasks/future/editor-voice-door-diagnostics.md` (new) |
> | The phase 3 end checkpoint: retire or regenerate `ClassicVoiceBaselineSpec` and `BuiltInVoiceMatrixSpec` | owed, by ear | `docs/tasks/by-ear/phase3-end-checkpoint.md` (new) |
> | Step 12 decision (g), a request waits for the drain | open, by ear | `docs/tasks/by-ear/chain-swap-request-during-drain.md` (new) |
> | Step 12 risk R0, the house `MasterStage` clip tested through a copy | CLOSED 2026-09-28 (the clip is `pcm16`/`interleavePcm16`, tested for real) | the step 12 plan, section "Risks" |
> | The 9 follow-up, collapse `BaseSvf` and `SvfBPF` | parked | `docs/tasks/future/svf-resonator-class-collapse.md` (new) |
> | The pitch pipeline into the tree (section 5), and the `voices/strip/` package name (step 9's decisions) | later | `docs/tasks/future/pitch-pipeline-into-the-tree.md` (new) |
> | Sprudel's tremolo `sync` against the Ignitor's `rate` (section 3b) | a naming decision | `docs/tasks/future/tremolo-rate-naming-parity.md` (new) |
> | FM envelope curves (section 3b, the `fm` row) | parked | `docs/tasks/future/envelope-shape-followups.md` section 4 |
> | A per-curve bend, the retired `expK` (section 3b, envelopes) | later | `docs/tasks/future/envelope-shape-followups.md` section 5 (added) |
> | A segment corner inside a block (D3, the sampling) | if wanted | `docs/tasks/future/envelope-shape-followups.md` section 6 (added) |
> | The D7 stopgap, crush and coarse `oversample`, the distort soft cap (D2) | scheduled | `docs/tasks/oversampling-regions.md` |
> | The duplicated chain-host plumbing, and the `cylinders/katalyst/` package name at the output | future | `docs/tasks/future/one-chain-host.md` |
> | A door for the one-pole highpass | future | `docs/tasks/future/onepole-highpass-door.md` |
> | Recorded two-door asymmetries, the missing `Osc.sample()` script door | audit | `docs/tasks/dsl-kotlin-surface-parity.md` (bullet added) |
> | Bus references (ducking as composition), `.sprudel()` auto-attach | future | `docs/plans/future/signal-graph-engine.md` sections 1 and 5 |
> | The slot map as an array if `toVoiceData` ever costs too much on JS | not before a number | `docs/plans/signal-flow-redesign.md` section 4 |
> | `Osc` and `oscp` renamed after phase 3 | capture-only | `docs/plans/signal-flow-redesign.md` section 11, `/dsl-design` section 5 |
> | Type inference for stored lambdas | future | `docs/tasks/future/stored-lambda-type-inference.md` |
> | `GraphCensus` does not model the gate (section 2) | a caveat | `audio/MEMORY.md`, the `GraphCensus` entry (added) |
>
> Recorded and closed, no task: the gated stage that skips its knob subtrees' draws (section 5b, chosen
> deliberately); `lowpass(freq = 0)` building a 5 Hz filter (section 5b, the onepole row); Sakura's kick level and
> Greensleeves' inert limiter (kept by the maintainer, section 9).

> **Status 2026-09-27:** step 9 is done: the voice strip, the Pipeline DSL and the typed `VoiceData` door fields
> are retired, and every voice is its Ignitor tree. Where this record says "today" or describes the strip, it
> means the engine at the time of writing; the current shape is `audio/ref/voice-synthesis.md`.

Phase 3 of `docs/plans/signal-flow-redesign.md` (its section 5 states the goal and the rules). This
file is the task record: what the spike of 2026-09-20 found, what the maintainer has to decide, and
the step list. Written for a reader who has not read the code.

## 1. The goal in one paragraph

`sound("saw")` is not an oscillator, it is a subtractive synth voice with a saw in it. Phase 3 makes
that explicit: the built-in sounds become INSTRUMENTS written in the Ignitor DSL, registered once,
whose stages are today's voice pipeline in today's order, every stage gated on its slot. `.classic()`
is a plain function over the node type, shipped on both doors. A pattern fills slots, never adds
structure. `PipelineDsl`, the filter pipeline builder, `Cmd.RegisterPipeline`, `PipelineRegistry` and
the `pedal` preset retire (the `pedal` preset went early, 2026-09-25, D4); every voice door's value reaches its `classic()` slot (since step 8 through sprudel's `toVoiceData()`, the doors staying typed); `VoiceData` is cut to the plan's
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
- **Today's number phase 3 must not regress: 9290 ns per block for 8 sustained saws.** Re-measured in step 6 (2026-09-26,
  `docs/benchmarks/2026-09-26_phase3-step6-ab.md`, the permanent `IgnitorBenchmark` case `sawtooth_8v`): the step 6
  tree is within noise of the tree before it on the JVM (+2.1 %, t about 1.1), on Node.js (+0.9 %) and at song
  level (+0.5 %). Both trees read about 8 % above 9290 ns on the JVM, a harness offset (the spike's harness is gone);
  the spike's predicted per-voice win (720 against 1161 ns) did not appear, because the strip was already down to
  about 790 ns per voice and the tree runs at the same cost. `GraphCensus` (the ledger's `work` column) does not
  model the gate and counts every `classic()` stage of a built-in as built: read the column with that in mind.
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

> **The phase's principle, stated by the maintainer (2026-09-25): a CONSISTENT FOUNDATION comes first, and the
> songs are adjusted to it.** "Sound changes are fine. The whole point of what we are currently doing is to
> build a consistent foundation. Current songs are built on the 'wrong' foundation and need to therefore be
> adjusted." Bit-identity is still the PROOF METHOD for steps that mean to change nothing; it is no longer a
> reason to keep two laws, two defaults or two vocabularies. A step that changes sound on purpose renders
> before/after pairs for the maintainer's ear instead.
>
> **The horizon, stated by the maintainer (2026-09-27): the flexible graph.** "We follow through with the current
> plan, but keep the big goal of the flexible graph in mind on the horizon. So whenever we can already prepare
> things we should do, while keeping the fixed structure in place for now." In practice: extract DSP into shared
> cores usable in any context, prefer one type where the fixed layout has two (the Katalyst and the Master are one
> shared chain at two positions), and keep the fixed layout working. The graph work itself starts after this
> workstream (`docs/plans/future/signal-graph-engine.md`, its fixed-layout inventory first).

## 3. Decisions the maintainer owes before the ear-checkpoint steps

The identity-provable steps (1, 2, 3, 5 below) do not wait for these. Steps 4, 6, 7 and 10 do. D7 is decided.

- **D1, the crush. DECIDED 2026-09-25 (maintainer): FLOOR everywhere**, LANDED in step 4 as `CrushCore` (the
  floor and the strip's NaN rule, shared by both hosts). No song changes (no song crushes at the Ignitor level; StrangerThings, its frozen copy and
  ATruthWorthLyingFor crush through the strip); a user's own Ignitor `.crush()` outside the repo changes
  slightly (release notes). The question as it was raised: the strip's quantizer floors (an asymmetric quantizer with a DC bias, and its
  KDoc claims the asymmetry IS the classic audible character); the ignitor's rounds (symmetric, no
  DC bias, its KDoc defends that too). Up to 0.125 apart at amount 4. Port `floor` into the ignitor
  (identity for the songs, changes every authored `.crush()` in an ignitor), keep `round` (changes
  the songs that crush), or make it a shape knob.
- **D2, the distort. DECIDED 2026-09-25 (maintainer): option A, KEEP BOTH LAWS. LANDED in step 4** as
  `DistortionCore` (shared by the strip and the fused node) and `fusedDistort`. `classic()`'s distort is the
  fused `IgnitorDsl.Distort` node running the STRIP's exact law (no soft cap, the drive inside the
  oversampler, ideally the same loop `DistortionRenderer` runs rather than a copy), so no song changes; the
  Ignitor `distort`/`shape` doors keep today's capped `Shape(Drive(...))` law, so no song changes there
  either. Chosen as the least effort because `oversampling-regions.md` phase 0 rebuilds the distort around
  one core anyway and answers the cap question there, once. The second divergence (drive before vs inside
  the oversampler) is inaudible (linear interpolation commutes with the multiply; only the last bits
  differ) and matters only for bit-identity. **D2 supersedes ledger W5's "delete it" (maintainer, 2026-08-30)
  for the fused runtime, knowingly** (the option A the maintainer chose named the fused node): W5's REASON, a
  per-block bypass that left the oversampler and DC blocker state stale when a modulated amount crossed 0,
  is designed out in step 4 (a non-leaf amount at or below 0 runs at unity drive; only a leaf is the build
  gate). The question as it was raised: `ShapeIgnitor` applies `softCap` per sample, `DistortionRenderer` does not,
  because the strip has its own downstream bounding stages. Drop the cap on the classic tail
  (identity, but a heavy-drive branch can then dominate a mix, which is what the cap exists for), or
  keep it and accept the change on every distorted voice.
  **A second divergence, found in step 3b's plan (2026-09-25), read from the code:** oversampled, the
  strip applies the drive INSIDE the oversampler (`shape(work[i] * d)` on the upsampled stream), while the
  tree drives at the base rate and then upsamples; linear interpolation does not round the same either
  way. So even with the cap dropped, an oversampled distort through `Shape(Drive(...))` is not
  bit-identical to the strip. The legacy fused `IgnitorDsl.Distort` node (kept and retyped in 3b for this
  reason) is the one shape that could drive inside the oversampler; D2's answer decides which node
  `classic()` uses.
- **D3, the filter envelope. DECIDED 2026-09-25 (maintainer), and widened to every modulation envelope:**
  (1) the CURVE: the filter AND the pitch envelopes default to EXPONENTIAL on all three stages
  (`MOD_ENV_CURVE`); (2) the SAMPLING: the node's smooth per-block interpolation (option A); (3) sprudel gets
  configurable curves: `lpfCurves`, `hpfCurves`, `bpfCurves`, `notchCurves` and `penvCurves(attack, decay,
  release)`, mirroring `adsrCurves`; (4) sprudel's `penv` gets the Ignitor pitch envelope's vocabulary (a
  REAL release, `sustain` in place of `anchor`, the dead `curve` slot retired). RE-CONFIRMED 2026-09-25
  (maintainer): `penv(amount, attack, decay, sustain, release)` plus `penvCurves(attack, decay, release)`,
  NOT a split `penv(amount)` / `padsr(a, d, s, r)` / `pcurves(...)`: every sprudel modulation envelope
  keeps its stages on its own door (`lpf`, `hpf`, `bpf`, `notch`, `fm`), per-target envelope doors
  (`lpadsr`, `pattack`, ...) were retired 2026-09-07, and the curves door is always `<door>Curves`. Its details
  (coordinator, 2026-09-25, from the commit (c) plan, following the record): `curve` and `anchor` are removed
  (a loud unknown-parameter error); the defaults are one constant set both hosts read,
  `constants/PitchEnvelopeDefaults.kt`, attack 0.01, decay 0.1, sustain 0, release 0 (the node's; the strip
  read an unset attack and decay as 0, so a bare `penv(24)` did nothing there and now sweeps); `pamt` stays
  the alias of `amount`; no alias curves doors. Commit (c) is split: c1 the pitch envelope (the strip host
  of `EnvelopeCore`, the wire fields, `penv`, `penvCurves`), c2 the filter curves (`FilterEnvDef` curves,
  `lpfCurves`/`hpfCurves`/`bpfCurves`/`notchCurves`, `classic()`'s curve slots); (5) the envelope frame counts
  become FRACTIONAL everywhere (the coordinator's call under the principle above: one rule, the precise one).
  Sound changes accepted: the Ignitor pitch envelopes' kicks and drops in DerSchmetterling, Sakura, Sandsturm,
  IrishLament and the frozen piece curve now; the maintainer listens to before/after pairs. The decision as it
  was first raised: RE-SCOPED 2026-09-20 in the step 3a review: it is two decisions, and
  the one the spike missed is the bigger.
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
    -85.8 against -50.3). Qualified 2026-09-25 by the (a2) audio review: with the attack/decay corner
    inside a block (an attack of about 2 to 3 ms) the two are level or the node marginally worse (-41.6
    against -42.7 dB at q 0.707); every other alignment tried is 1 to 26 dB better. The corner weakness
    named below, with block splitting as its fix. In cutoff terms the strip lags by up to 635 cents and stairs at the 375 Hz
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
  - And the PITCH envelope (step 3d(i), 2026-09-24): it became an ADSR with the chain's composition
    but kept its FRACTIONAL frame counts (identity for the songs), while the chain `adsr` truncates to
    Int. So one `adsr(a, d, s, r)` call counts frames two ways depending on whether it sits on the chain
    or on a `pitchEnvelope` builder. Same class; D3's frame-count decision covers all three envelopes.
  - Either way it is an ear checkpoint on Der Schmetterling and Stranger Things, and it changes every
    song that already uses `lpf(env = ...)`, by up to 635 cents at the sweep's steepest.
- **D4, the `pedal` pipeline. DECIDED 2026-09-25 (maintainer): REMOVE it fully.** The preset retires with the
  Pipeline DSL; DialogueWithTheStars ("an empty song") drops its call; TetrisRemix's bass drops it (a SOUND
  CHANGE: that bass goes on the listening list); FrozenDerSchmetterling is REPLACED by a FRESH SNAPSHOT of
  today's DerSchmetterling (the live song has no `pipeline("pedal")`; its `pedal*` names are the guitar rig's
  effect pedals). And DialogueWithTheStars LEAVES the built-in songs (maintainer: "I will not work on this one
  any time soon"). Done as a song-housekeeping step before step 6. The question as it was raised: `DialogueWithTheStars` calls `.pipeline("pedal")`, which puts the VCA
  FIRST. **Undercounted until step 5's review (2026-09-25): THREE of the 18 corpus songs use it**:
  DialogueWithTheStars, FrozenDerSchmetterling (three patterns, with distort) and TetrisRemix (the bass,
  `distort(0.8, "soft", 2).pipeline("pedal")`). All three have no `classic()` spelling; the preset retires with `PipelineDsl`. Ship a second named tail
  (a second word for one concept, which the rules register argues against), rewrite the song, or let
  it change.
- **D6, the door's shape (raised in the step 3a review). DECIDED 2026-09-23: a builder wherever a door
  carries secondary knobs, decided function by function with the maintainer, and ONE shape per concept
  across the Ignitor, Katalyst and Master DSLs.** The per-function record is section 3b below; the text
  that follows is the question as it was raised. The script door's `lowpass` is now eleven
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
- **D5, the frozen pieces. DECIDED 2026-09-26 (maintainer): controls, then edit.** Both frozen texts with authored
  instruments (`FrozenPieces.derSchmetterling_2026_09_16` and `FrozenSongs.derSchmetterling_2026_09_25`) stay on the
  strip during step 10's proof, as rows predicted bit-identical; step 10's LAST commit appends `.classic()` to their
  instruments (the sound kept; the ledger rows move once, bass and trommel by the draw order). It must land before
  step 8, which makes the doors write slot keys the strip does not read. The question as raised: (2026-09-25: the maintainer REPLACED the frozen Der Schmetterling SONG in
  `FrozenSongs.kt` with a fresh snapshot, `derSchmetterling_2026_09_25`, D4; `FrozenPieces` itself is still
  open here.) `FrozenPieces` is captured verbatim and immutable except for door
  renames. Appending `.classic()` is not a rename, and without it those pieces lose their outer
  envelope. The maintainer's word is needed.
- **D7, distort's oversampling across step 6 (raised 2026-09-23). DECIDED 2026-09-23: (a).** It
  lands in step 3b; the region task retires it once this plan is fully complete. The maintainer moved oversampling
  out of phase 3 into its own task (`oversampling-regions.md`: a region in lambda form, the factor read
  once when the note starts, after the KatalystDsl work stream). For crush and coarse that is free: every
  shipped use pins `oversample = 1`. For distort it is not. The sprudel `distort(amount, shape, N)` is
  oversampled by the strip in Tetris (2x), TetrisRemix (2x, through `import { sub } from "peekandpoke/tetris"`: step 6's identity list must carry it wherever Tetris is), IrishLamentTechno (five voices, 2x and 4x)
  and the frozen songs (2x and 4x). The Ignitor's `Shape` node has an `oversample` field, but it is a
  plain Int, so `classic()` cannot fill it from a per-note slot. So from step 6, when the built-ins leave
  the strip, those voices lose their oversampling: more aliasing, audible at step 6's checkpoint, and the
  step is no longer identity. (The Ignitor-level uses in Sandsturm, ATruthWorthLyingFor and DerSchmetterling (its guitar rig at 2x
  and 4x, `granCassa` at 2x; the frozen piece too) use the field directly and are untouched by phase 3.
  DialogueWithTheStars DEFINES oversampled tube guitars but its arrangement never plays them; both
  corrections from step 3b, the second found by a control that missed its prediction.) Three ways:
  - **(a) Keep one small piece in phase 3**: make `Shape.oversample` a knob read at voice build (step 2's
    `buildTimeKnobValue`), so `classic()` fills it from `distort.oversample`. Cheap, keeps step 6
    identity, and is replaced by the region when the new task lands.
  - **(b) Land the Ignitor half of the regions before step 6.** This changes the agreed order.
  - **(c) Accept the change** in those songs at step 6, and restore it when regions land.
  Recommendation was (a), because it is the smallest change that keeps step 6 provable and it is
  explicitly temporary; the maintainer chose it.

Minor, decidable inside their step: the ADSR's Int-against-Double frame counts and its sustain clamp
(up to 7.1e-3 on the gain, about 0.06 dB, on a fractional frame count); whether `classic()`'s
de-click may share the name `declickSeconds` with a slot whose default differs.

## 3b. The door shapes, function by function (D6, maintainer, 2026-09-23)

> **Moved 2026-09-28** to its living home, `.claude/skills/dsl-design/door-shapes.md`.

## 3c. Release notes, collected (the one home; append per step)

What a user's own script, outside the repo, experiences from phase 3's door work. No song in the repo is
affected by any of these (each step proved its corpus bit-identical). Loud means the old form now fails
with an error; SILENT means it now means something else or nothing.

| Step | Old form | Now | Kind |
|---|---|---|---|
| 3d(i) | Ignitor `phaser(0.3, 800)` (rate, center) | wet 0.3, rate 800 Hz | SILENT |
| 3d(i) | Ignitor `shimmer(0.4)` (feedback) | wet 0.4 | SILENT |
| 3d(i) | positional `lowpass(800, 1.2, 2)`, `pitchEnvelope(24, 0.001, 0.04)` | the third argument is the builder lambda | loud |
| 3d(i) | `drive(x, "linear")`, `.dryFloor(...)` | removed / renamed `floor` (effect builders only) | loud |
| 3d(ii) | Katalyst `k.body("wood")`, `k.vowel("a")` | `k.body(material = "wood")` or `k.body(wet, "wood")` | loud |
| 3d(ii) | every Katalyst/Master stage lambda (`k.reverb(r => ...)`, `m.limiter(l => l.thresholdDb(...))`) | flat doors, `threshold`/`knee` | loud |
| 3d(ii) | a compressor-shaped 5-argument `m.limiter(-3, 4, 2, 0.01, 0.2)` | the 5th argument is `lookahead` (bounded to 50 ms latency), not a release; use named arguments | SILENT (a new form, not an old one) |
| 3d(iii) | sprudel `body("wood")`, `vowel("a")` | the string lands in `wet` as a non-number and writes NOTHING; write `body(material = "wood")` | SILENT |
| 3d(iii) | sprudel `phaser(0.3)` | wet 0.3 at the default rate | SILENT |
| 3d(iii) | bare `"<wood glass>".body()` / `"<a e>".vowel()` | reinterprets as wet; the bare call no longer clears | SILENT |
| 3c | Ignitor `.adsrCurves(...)`, `.declickSeconds(...)`, `.expK(...)` after `adsr` | `adsr(a, d, s, r, e => e.curves(...).declick(...))` | loud |
| 3c | the same reach-back methods NOT directly after `adsr` (they wrapped a second envelope) | shape the envelope itself: `adsr(a, d, s, r, e => e.curves(...))` | loud |
| 3c | the 3d(i) filter and pitch builder knob `x.adsrCurves(...)` | `x.adsr(a, d, s, r, e => e.curves(...))` | loud |
| 3c | sprudel `oscp("expK", k)` on an ignitor with an `adsr` | an unread key; every exp stage bends at 3 | SILENT |
| 4 (D1) | a user's Ignitor `.crush(n)` | quantizes with the strip's FLOOR instead of round (up to 0.125 apart at amount 4) and carries its DC offset of about -0.5/halfLevels (-0.5 at amount 1). On the Ignitor the amount can be MODULATED, which the strip could not: the offset then moves with it (a 0.8 sine with an amount LFO of 1 to 3 at 0.5 Hz puts about 60 % of the output RMS amplitude (about 36 % of the power) below 20 Hz, where round gave almost none). Measured by the step 4 audio reviewer | SILENT |
| D3 | an envelope whose gate ends inside its attack or decay | the release starts from the envelope's value AT the gate instant (stateless, every host), not from the last rendered level; at most one frame of slope | SILENT |
| D3 | the chain `adsr` with a sustain above 1 (or below 0) | no longer clamped to [0, 1] (the raw Motor); a sustain of 1.5 overshoots | SILENT |
| D3 | sprudel `penv(amount, attack, decay, release, curve, anchor)` | `penv(amount, attack, decay, sustain, release)`: a positional 4th argument turns from release into SUSTAIN; `curve` and `anchor` are gone (`penvCurves`, `sustain`) | SILENT (the 4th) / loud (named `curve`, `anchor`) |
| D3 | a NaN sustain on the strip's amplitude envelope | reads as unset (1.0) | SILENT |
| D3 | a filter envelope's release | ends on an exact 0.0 on the last rendered frame (N-1 base) on both hosts | SILENT |
| D3 | a filter or FM envelope on the Ignitor node with a sustain below 0 | the decay reaches the node's 0 clamp partway through and holds there (it used to reach 0 at the decay's end) | SILENT |
| D3 | a filter or FM envelope on the Ignitor node with release 0 | drops at the gate frame, not one frame later | SILENT |
| D3 | a strip filter or FM envelope with release 0 | 0 from the gate frame; a block starting exactly on the gate used to hold the full level | SILENT |
| D3 | a negative pitch-envelope stage time | a zero-length stage; it used to shift the decay window | SILENT |
| D3 | a negative or NaN stage time on the strip's amplitude, filter or FM envelope (`adsr(attack = -0.01)`, `fm(attack = ...)`) | a zero-length stage, the full decay runs from 1.0; a negative attack used to shift the decay window, a NaN one skipped the decay | SILENT |
| D3 | a filter or FM envelope on the Ignitor node with a sustain above 1 | the sustain is raw and only the output is clamped, so the release holds full depth for its first `1 - 1/s` (it used to be clamped to 1 and fall at once); the strip already did this | SILENT |
| D3 | a gate at or before the note's onset (`legato(x)` with x <= 0) | every envelope releases from 0 (the amplitude hosts were silent already); the modulation envelopes (the node filter, FM and pitch, the strip filter and FM) used to release from the attack curve evaluated at the gate: an extrapolation below the onset, the decay top at a gate of 0 with a zero attack. Audible only on a voice whose amplitude envelope is off | SILENT |
| D3 (a2) | a sprudel `lpf`/`hpf`/`bpf`/`notch` envelope | read at both ends of every 128-frame block and glided across it, as the Ignitor filters already did; it used to be read once per block, eased over 32 samples and held (a 375 Hz staircase, about one block late). Steep, deep sweeps (plucks, `env = 48` subs) arrive earlier and crisper: 8 corpus songs moved by -32 to -52 dB RMS, peaks up to -15 dBFS; the maintainer listens to the pairs | SILENT |
| D3 (a2) | the strip's analog filter drift | changes on the block edge like the Ignitor filters', no longer eased over 32 samples; about -90 dB | SILENT |
| D3 (b) | an Ignitor `pitchEnvelope(...)` whose `adsr` names no curves | every stage exponential (K = 3), was linear: the drop reaches the note much sooner (halfway through the decay the level is 0.18, was 0.5); `e => e.curves("linear", "linear", "linear")` restores the old sweep. The kicks and pitch drops of 6 corpus songs moved; Sakura's kick is about 2.5 dB quieter in isolation | SILENT |
| D3 (b) | an Ignitor filter envelope (`x.adsr(...)` on `lowpass` etc.) without `curves` | exponential stages, was linear: the same sweep as sprudel's `lpf(env)` with the same numbers (bit-identical in `ClassicStripParitySpec`) | SILENT |
| D3 (b) | an Ignitor `fm(..., x => x.adsr(...))` index envelope, and the built-in `sgbell` | exponential stages, was linear; no curve knob yet | SILENT |
| D3 (c1) | sprudel `penv` stages | exponential by default (was linear); `penvCurves("linear", "linear", "linear")` restores the old sweep | SILENT |
| D3 (c1) | sprudel `penv` with an `anchor` | the attack starts from the note (0), not from the anchor; `sustain` is where the decay lands | SILENT |
| D3 (c1) | a sprudel note whose gate ends inside its pitch sweep | the envelope releases at the gate; with the default release 0 it is back on the note at once (the sweep used to run on through the release tail) | SILENT |
| D3 (c1) | a bare sprudel `penv(amount)` with no stages | sweeps over 0.01 s / 0.1 s, the Ignitor `pitchEnvelope`'s defaults (`PitchEnvelopeDefaults`); it used to do nothing | SILENT |
| D3 (c1) | a NaN `penv` amount, a NaN sustain | no pitch envelope; the sustain reads as 0 | SILENT |
| D3 (c1) | the readers `penv.curve` / `penv.anchor`, the named arguments `curve =` / `anchor =`, a 6th positional argument | an error naming the word | loud |
| 6 | a `.pipeline(...)` on a built-in sound | no longer applies (stage order, VCA-off, the filter feel scales); it still does on authored instruments that do not end in `classic()` until step 9 (on samples until step 7, on authored `classic()` instruments until step 10) | SILENT |
| 6 | `crush`/`coarse` with `oversample` above 1 on a built-in | renders without oversampling until `docs/tasks/oversampling-regions.md` (D7's stopgap covers distort only) | SILENT |
| 6 | `analog > 0` with two or more pattern filters on a built-in | the per-filter tolerance and drift draws go to other filters: the same distribution, other values (9 corpus songs, -50 to -82 dB) | SILENT |
| 6 | sprudel `pregain(x)` on a built-in sound (commit 2) | used to do nothing; now scales the source in front of every classic stage (`distort` and `crush` bite harder above 1; with no nonlinear stage written it is a plain level) | SILENT (a new effect of an old form) |
| 6 | a built-in's filter with a non-finite `freq`, or a filter envelope whose only written stage is non-finite | the stage is not built (non-finite reads as unset); the strip built the filter at 1 kHz, and swept an envelope at depth 7 | SILENT |
| 6 | `perlin`/`berlin`/`crackle` as a sound with `analog > 0` and a pattern filter | its construction draws now come before the filter's | SILENT |
| 6 | a negative `release` on a built-in (`adsr(release = -0.1)`) | the voice lives to its gate (the lifetime is floored at 0, as before step 6); the envelope's release is a zero-length stage | none (unchanged; a review-round fix kept it) |
| 6 | a classic slot written with `oscp` (`oscp("lpf.freq", ...)`, `oscp("adsr.release", ...)`) on a built-in | now reaches it; a typed door beats an `oscp` of the same slot on one event (since step 8 inside sprudel's `toVoiceData()`); a built-in lives exactly as long as its tree's release, no 0.05 s floor | SILENT (a new reach, not a changed meaning) |
| 7 | a sample voice with `analog > 0` and a pattern filter | the sample's fast wow/flutter layer and each filter's tolerance and drift go to other draws: the same kit, other dice per hit, a sub-cent detune (median 0.1 to 0.3 cents, at most about 1) and cutoffs within 0.2 %; 7 corpus songs null at only -17 to -35 dB because noise-like top end (hats, cymbals, claps) stops cancelling, not because anything audible moved; levels unchanged | SILENT |
| 7 | a sample voice with two filters of one kind, or filters sent in another order (no sprudel door does either) | `classic()`'s fixed order (hp, bp, notch, lp), one slot per kind, a second one merged per field (as on the built-ins since step 6) | SILENT |
| 7 | a `.pipeline(...)`, or crush/coarse `oversample` above 1, on a sample | no longer applies (as on the built-ins since step 6) | SILENT |
| 7 | a negative `release` on a sample | plays to its gate (it ended before the gate); a non-finite filter `freq` builds no filter (was 1 kHz); a non-finite pattern ADSR stage takes the sample's own value | SILENT |
| 7 | `onepole(x)`, `pregain(x)` and an `oscp("<door>.<param>")` classic slot on a sample | now work (they did nothing on samples) | SILENT (a new reach) |
| 10 | an authored `.classic()` instrument whose OWN root envelope has a modulated (non-static) release, played with `adsrOff()` | the voice ends at `classic()`'s release slot (0.05 s by default), on the teardown fade (as the strip did); write the pattern's `adsr(release = ...)` for a longer tail | SILENT |
| 10 | an instrument with a stage AFTER `classic()` (`x.classic().mul(0.5)`, `a.classic().plus(b.classic())`), played with `onepole(x)` | the tree does not END in `classic()`, so it keeps the strip, and the onepole runs twice (the engine's wrap and `classic()`'s first stage): make `classic()` the last call | SILENT |
| 10 | an authored instrument whose own envelope tail is longer than the pattern's release, once `.classic()` is appended | `classic()` releases over its slot; the strip stretched to the instrument's tail (the 2026-08-27 envelope-ownership fix). Write `adsr(release = <tail>)` (the songs in the repo do) | SILENT |
| 8, 9 | an authored instrument that does NOT end in `classic()`, played with voice doors (the filters and their curves, `adsr`, `adsrCurves`, `adsrOn`/`adsrOff`, crush, coarse, distort, tremolo; on samples begin, end, speed, loop) | the doors no longer reach it (step 8 sends slot keys, the strip reads typed fields); step 9 also removes its default envelope. Append `.classic()` as the last call. A tree with `classic()` inside it gets the doors per branch; a tree reading a slot itself gets that door | SILENT (step 11's editor diagnostic flags it) |
| 8 | a non-finite `begin`, `end` or `speed` on a sample | reads as unset (plays from the start at speed 1, the sample's own loop applies); it used to reach the playhead as NaN | SILENT (a fix) |
| 8 | `oscp("begin" / "end" / "speed" / "loop", x)` on a sample, or an authored instrument declaring a param of that name | now reaches it (the slot names mirror the doors); the door still wins on the same event | SILENT (a new reach) |
| 8 | `oscp("lpf.q", x)` or `oscp("lpf.passes", x)` together with a door that does not name them | kept (the door no longer writes the defaults over them) | SILENT |
| 8 | a non-sprudel producer setting typed `VoiceData` door fields for a built-in, a sample or a `classic()` instrument | ignored; write the slot keys (the typed fields are cut in step 9) | SILENT |
| 9 | sprudel `.pipeline(name)` and the `pipeline(...)` mapper; script `Pipeline(...)`, `Pipeline.modern(...)`, `Pipeline.build(...)` | removed (the Pipeline DSL retired with the voice strip) | loud |
| 9 | a custom pipeline's per-voice phaser, VCA curve (`expK`, `declickSeconds`, `on`) and filter-feel scales | gone with the Pipeline DSL; the phaser lives on the orbit (`phaser(...)`), the envelope's curves on `adsrCurves` | loud |
| 9 | an authored instrument that does NOT end in `classic()`, even with no door | plays as its bare tree: no voice envelope (no 10 ms attack, no 50 ms release ramp), the gate plus 0.05 s at full level (or its own static tail), then a 4 ms fade, unless its own root envelope ends it; pattern doors, `onepole(x)` and `adsrOn`/`adsrOff` do nothing on it. Append `.classic()` | SILENT |
| 9 | `classic()` below an instrument's root (`a.classic().plus(b.classic())`) | no outer envelope and no engine onepole wrap any more (the step 10 "onepole twice" row is moot) | SILENT |
| 9 | Kotlin `VoiceData.pipeline`, `KlangCommLink.Cmd.RegisterPipeline`, `KlangPatternEvent.pipeline` | removed (a compile error) | loud |
| 9 | Kotlin `VoiceData` fields `scale`, `filters`, `adsr`, `distort`, `distortShape`, `distortOversample`, `coarse`, `coarseOversample`, `crush`, `crushOversample`, `phaser`, `phaserDepth`, `phaserCenter`, `phaserSweep`, `phaserFloor`, `tremoloSync`, `tremoloDepth`, `tremoloSkew`, `tremoloPhase`, `tremoloShape`, `cutoff`, `hcutoff`, `bandf`, `resonance`, `begin`, `end`, `speed`, `loop`, `loopBegin`, `loopEnd` | removed (a compile error); a voice door is a `classic()` slot in `oscParams`, an orbit stage a `katalystParams` slot | loud |
| 9 | Kotlin types `FilterDefs`, `FilterDef.LowPass` / `HighPass` / `BandPass` / `Notch`, the bridge `FilterEnvDef`, `AdsrDef.on` and the `AdsrDef` merge and resolve API (`mergeWith`, `resolve`, `Resolved`, `empty`, `defaultSynth`) | removed (a compile error); `FilterDef.Formant` / `Body` stay as the orbit's band carriers (no longer on the wire), `AdsrDef` stays for sample metadata; the voice envelope defaults are the `VOICE_ADSR_*` constants | loud |
| 9 | sprudel `loopBegin(pos)` / `loopb`, `loopEnd(pos)` / `loope` and the `loopBegin` / `loopEnd` field accessors | removed (a script error: no such method); they never reached the engine. A loop region is `loop().begin(x).end(y)` | loud |
| 12 | the script `Master` object: `Master(m => m.X)`, `Master.default()` | removed: `Katalyst(k => k.X)`, `Katalyst()` (a script error "unknown identifier" until migrated); every Katalyst stage now works at the output (eq, compressor, phaser, body, vowel), `duck` is inert there, a `Katalyst.param(...)` stays at its default (nothing fills slots at the output) | loud |
| 12 | `limiter(...)` positional arguments | the fifth positional is `release` (was `lookahead` on the Master door); named arguments unchanged; `lookahead` is a compressor knob, honoured on an orbit too (the orbit runs late by it) | loud |
| 12 | Kotlin `MasterDsl`, `MasterStageDsl`, `MasterValue`, `MasterRegistry`, `KlangCommLink.Cmd.RegisterMaster` | removed (a compile error); `.master()` takes a `KatalystDsl`, one Katalyst registry serves both positions | loud |
| 12 | a master reverb or delay whose unit is refused (out of memory) | recovers when a unit comes back, like an orbit's (was: dropped for the chain's life) | SILENT |
| 12 | a non-finite master knob (only reachable by computing one in script) | a non-finite reverb/delay wet, size or time switches that stage off (was: the constant); a non-finite limiter knob takes the compressor's fallback, not the limiter's | SILENT |
| 12 | a live master at song start | built inside the song's first render callback (was: just before it), like an orbit's first chain; `gain(1.0)` or a dry send at the output keeps the bus path (the last bit of a multi-playback sum only) | SILENT |
| 12 | sprudel `echo(times, delay, decay)` / `stut` with a source gain | each echo layer now MULTIPLIES the source gain by `decay^i` (was: replaced it with `decay^i`, so a quiet part's echoes could be louder than the part); a source without a gain echoes as before (maintainer, 2026-09-28) | SILENT |
| 12 | a chain swapped away (orbit or master) whose tail never ends (a delay at feedback >= 1) | drains naturally for up to 20 s, then an exponential release (60 dB per 3 s, to -90 dB), and a waiting edit lands (was: it drained forever and every later edit on that orbit waited forever) | SILENT |
| 12 | stopping a playback | tails that can end ring out fully, however long; an endless tail (a delay at feedback >= 1) triggers a smooth release 20 s after the last note, of the engine's whole output with whatever rings beside it (the rule's home is the `PlaybackEngine.isIdle` KDoc) (was: the master's tails cut hard 20 s after quiet, an orbit's endless tail kept the engine alive for ever) | SILENT |
| D4 | a song's `pipeline("pedal")` | the preset is gone; the name resolves like any unknown name to `modern` (envelope last), silently | SILENT |
| D4 | a script's `Pipeline.pedal(...)` | removed | loud |
| 4 (D2) | a user's Kotlin-built `IgnitorDsl.Distort` node (no authoring door emits it; `classic()`, new in step 5 and unreleased, does) | the strip's law: no soft cap, the drive inside the oversampler | SILENT |
| 4 (D1) | a user's Ignitor `.crush` with a NaN or +Inf modulated amount, or a NaN input sample | the strip's handling: a NaN amount bypasses (it output NaN), a NaN sample becomes 0, +Inf is silence | SILENT |
| 5 | `passes = +Inf` (either door, or a pattern) | 1 pass (non-finite reads as 1); it was 16 on the strip | SILENT |

Open for the maintainer: whether the editor should WARN on the silent rows (a string literal in a wet slot is
the obvious first one).

## 4. What is missing from the Ignitor DSL (the spike's map)

| Missing | Kind | Who needs it |
|---|---|---|
| ~~The filter ENVELOPE on the four filter nodes~~ | DONE in 3a (2026-09-20) | `env` plus four stage knobs, both doors, the wire, the defaults in `audio_bridge/constants/FilterEnvelopeDefaults.kt` |
| ~~`AnalogDrift` per filter, and the per-voice cutoff tolerance~~ | DONE in 3a | the structural `humanize` flag, because both halves are per-voice DRAWS and no knob can carry a draw; the draw order lives in `audio_be/.../ignitor/FilterHumanization.kt` |
| ~~`oversample` on crush and coarse~~ | MOVED 2026-09-23 to `oversampling-regions.md` | no shipped song: every crush and coarse use pins `oversample = 1` |
| ~~`skew`, `phase`, `shape` on tremolo~~ | DONE in 3b (2026-09-25) | one law with the strip (`TremoloCore`), `shape` an index slot through `LfoShapes`, skew per block, phase and shape read at build |
| ~~`shape` on distort, and its existing `oversample` read at voice build~~ | DONE in 3b (2026-09-25) | `shape` an index slot through `DistortionShapes`, `oversample` a build-time knob (the D7 stopgap) on `Shape` and the legacy `Distort` |
| ~~`adsrOn`/`adsrOff` as a gate slot, and the three curves as slots~~ | DONE on the node in 3c (2026-09-25) | `Adsr.on` (a node field, off at exactly 0.0, unset is ON) and the three curves as index knobs through `AdsrCurves`; `classic()` fills them from sprudel's slots in step 5 |
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
  below 0 to the table. (Both landed; the built-ins place `pregain` since step 6 commit 2, 2026-09-26.)

## 5b. The off-value table (the one home; built in step 2, 2026-09-20)

> **Moved 2026-09-28** to its living home, `audio/ref/off-values.md` (the one home of the off values).

## 6. Two things the factory knows today that only the build can know tomorrow

- **The cull rule.** `VoiceFactory` sets `VOICE_CULL_NEVER` when the tremolo depth is above 0,
  because a square tremolo at full depth is exact silence for half a cycle and the silence culler
  would kill the voice at its first off-half. Once the tremolo is a tree node the factory cannot see
  it: the build must report "this tree gates its own output", the shape `releaseTailSec` already has.
  **Pulled forward into step 3b (2026-09-25):** 3b gives the node tremolo its shapes, which makes the
  hazard reachable from a script (a square tree tremolo at depth 1 on a voice in its release), so 3b
  lands the minimal `BuiltIgnitor.gatesOutput` and the factory's OR into the cull-never decision; step 6
  builds its teardown-fade work on it.
- **Caveats for steps 5 and 6 from the 3c review (2026-09-25, measured by the audio reviewer):** `classic()`
  must default the `Adsr.on` slot to 1.0 or `SLOT_UNSET`, never 0.0 (an unwritten 0.0 slot is OFF, the same
  guidance as the `mul` slots); it must fill `releaseSec` explicitly or give its slot the strip's default
  0.05 (the node's bare default is 0.3), or an `adsrOff` voice lives longer than on the strip; and the strip
  VCA must not retire before the teardown fade lands: an OFF node alone ends on a hard cut (modelled at 48 kHz
  on a 110 Hz tone: a mean last-frame step of 0.64 of peak on a sine, where the strip's 4 ms fade takes about
  36 dB off the energy above 2 kHz).
- **The exciter's `onepole` wrap moves (found in step 5's plan, 2026-09-25).** `IgnitorRegistry.createExciter`
  wraps every instrument in `onepole`, so today it runs BEFORE the strip's crush. Once a built-in is a
  `classic()` tree, that wrap would sit AFTER the ADSR: StrangerThings (`pulse.onepole(3743).crush(5)`) and
  IrishLamentTechno (`distort(...).onepole(...)`) would change. Step 6 must put the wrap where the strip had it
  (on the source, before `classic()`'s stages), and its corpus render proves it. SUPERSEDED 2026-09-26 (maintainer, step
  10's plan): the onepole moves INTO `classic()` as its first stage (slot `onepole`), so an authored `.classic()`
  instrument has it in the right place too; the built-ins' tree is unchanged. What step 6 did: DONE in step 6 (2026-09-26):
  built-ins register as `source.onepole(slot).classic()`; proven by the humanize-off control (the 9 moved songs
  identical on both sides) and an engagement mutation that moves DrunkenSailor.
- **Found in step 5 (2026-09-25), for step 6:** (1) the optimizer fuses every lone filter with a literal
  `analog` into an `Eq`, which never reaches `filterEnvDef`, so the slot-layer fill only runs on filters that
  stay filters; (2) the factory's lifetime floor (0.05 s) keeps a `classic()` voice with a shorter slot release
  rendering its de-click tail where the strip cut it; (3) an authored instrument with `.classic()` is
  enveloped TWICE until the strip VCA stops running for it; (4) the `adsrOff` difference is exactly the last
  192 frames, the strip's 4 ms teardown fade. Status after step 6 (2026-09-26): (1) never applies to a
  `classic()` built-in (its filters carry Param `analog`/`env`/`passes` and `humanize`, so the optimizer never fuses
  them); (2) does not bite with the typed release, and a built-in's lifetime now comes from its tree alone; (3)
  unchanged for AUTHORED `.classic()` instruments until step 10 (2026-09-26: a tree ending in `classic()` now leaves the
  strip); (4) closed by the shared
  `TeardownFadeRenderer`.
- **The teardown fade.** `EnvelopeRenderer.renderGate` is the `adsrOff()` path: a unity gate with a
  linear fade to exact zero over the last frames, which exists because an instrument's own envelope
  sits BEFORE its amp stages. Phase 3 removes the only stage that guarantees an amplitude ramp at the
  voice's end, and Der Schmetterling's lead, three guitars and Orchestertrommel all rely on it (and A Truth
  Worth Lying For's three guitars, found in step 6; all AUTHORED, so all still on the strip until step 9). The
  build reports whether it built the classic ADSR node, and the voice applies the teardown fade when
  it did not. Applying it unconditionally would multiply the last 5 ms of every built-in and break
  identity. LANDED in step 6 (2026-09-26): `BuiltIgnitor.endsInEnvelope` (the root only), one stateless
  `TeardownFadeRenderer` shared by the strip's `adsrOff` and a built-in without a built root `Adsr`. No corpus
  built-in uses `adsrOff`, so the corpus cannot click here; the fade is proven by specs.

## 7. The open points of the plan's section 11, answered

- **The voice's lifetime** is largely already built: `BuiltIgnitor.releaseTailSec` exists and the
  factory already takes the larger of it and the resolved release. `null` means "no static answer"
  and the caller treats it as "contributes nothing", which is today's behaviour. What changes in
  phase 3 is the safety net, not the number: see the teardown fade above.
- **The placed `pregain` at unity costs 115 ns per block at node level and about 90 per voice**, 12.5
  percent of the phase-3 target. FOLD IT AWAY at build when the resolved value is exactly 1.0, and do
  it through the gate rather than as a special case in `mul`. The decisive argument is identity, not
  the cost: the built-ins carried no `pregain` before step 6, so keeping a unity multiply would be the bit
  change, not removing it. Landed in step 6 commit 2 (2026-09-26): `source.pregain().onepole(slot).classic()`,
  the unity `mul` folded by the optimizer's `Affine` arm. What it drops is a `safeOut` scrub that only fires on a sample that is already NaN or
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

(Step 6 correction, 2026-09-26: the built-in sounds left the strip; the lists below were written for the whole
migration. For step 6 itself exactly 9 songs moved, all from the analog draw order: A Truth Worth Lying For,
Final Fantasy 7 Prelude, Frozen Stranger Things, Irish Lament Techno, Sakura, Sound Of The Sea, Stranger Things,
Tetris and Tetris Remix (Sound Of The Sea and Tetris Remix set `analog` at stack or song level, so the list below
missed them; the Der Schmetterling family did not move, being authored plus a one-filter shaker). The draw COUNT
per voice is unchanged, so no noise realisation changes: only which filter gets which tolerance and drift seed
moves, -49.5 to -81.9 dB RMS. Proven twice: with the humanization OFF on both sides the 9 are identical; and the
step 6 audio review's stronger control, HEAD's strip reordered to the tree's per-filter draw order for built-ins
only, humanization ON, rendered all 9 bit-identical to the step 6 tree, so how the drawn values are applied did
not change either.)

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
| 3 | The missing knobs of section 4, every default preserving today's tree bit for bit | the spike's five probes become the specs; door parity per knob | the biggest step by volume; 3a filters DONE; 3b waveshapers DONE 2026-09-25 (distort `shape`, tremolo `skew`/`phase`/`shape`, and distort's existing `oversample` read at voice build per D7; no `oversample` on crush or coarse); 3c envelopes DONE 2026-09-25 (one nested `adsr` shape, `expK` removed, curves as index knobs, the `on` switch). The filter build's rng draw order must reproduce the factory's exactly |
| 3d | THE DOOR SHAPES of section 3b (D6), inserted 2026-09-23 BEFORE 3b so 3b lands its knobs on builders. Three commits: (i) DONE 2026-09-24, the Ignitor doors, `driveType` removed, `dryFloor` renamed, the pitch envelope on `adsr`; (ii) DONE 2026-09-24, the Katalyst and Master doors, the limiter renames; (iii) DONE 2026-09-24, sprudel wet-first (`phaser`, `body`, `vowel`) with the song migration and the docs | the trees of every song bit-identical (a door moves, the sound does not); the wire golden regenerated for the renames; door parity per door | the pitch envelope is the one ENGINE change (it moves onto ADSR fields, the release becomes real): its default curve must stay today's linear law or 12 songs change (at the time; D3 later chose exponential, step 5b (b)). Sprudel's no-argument reinterpretation moves from `material` to `wet` |
| 4 | DONE 2026-09-25: D1 and D2 landed, NO listening needed (both decided so that no song changes): `CrushCore` (floor, the strip's NaN rule) and `DistortionCore` (the strip's law) shared by the strip and the tree; the 15 D1/D2 rows of `ClassicStripParitySpec` flipped to bit-identical at both rates (now 33/11 at 48 kHz, 32/12 at 44.1 kHz) | the corpus identical; controls moved exactly the 3 strip-crush and 11 strip-distort songs | a shared core moves both hosts together, so the parity spec cannot see a mutation inside it: `StripLawCoresSpec` pins the laws with oracles written in the test |
| 5 | DONE 2026-09-25: `classic()` on both doors, in the order of section 4 (`IgnitorDslClassic.kt`, the order written once; slots `<door>.<param>` in `IgnitorDsl.Slots.*` / `OscSlot.*`; `passes` a build-time knob that ROUNDS like the strip; the committed `ClassicStripParitySpec` pins 18 rows bit-identical to the strip and 26 divergent rows at 48 kHz (17 and 27 at 44.1 kHz, where the whole-frame ADSR row also diverges: the frame-count minor), each divergent row naming its cause: D1, D2 (step 4), D3, the ADSR frame counts, the lifetime floor, the teardown fade) | a one-voice render per door, each slot written in turn | the order: write it once, in one place |
| 5b | DONE 2026-09-26. ONE ENVELOPE LAW (D3, decided 2026-09-25), inserted before step 6, four commits: (a1, DONE 2026-09-25: `EnvelopeCore.kt` with six hosts, `EnvelopeLawSpec`; the corpus moved by at most 2 LSB; a gate at or before the onset releases from 0) one `EnvelopeCore` that all seven evaluators become thin hosts of (the chain ADSR, the strip VCA, the node and strip filter envelopes, the node and strip FM, the node pitch envelope; the strip pitch envelope joins in c): fractional attack/decay, release on floor(N) to an exact 0, one curve `when`, N-1 release base, a STATELESS release start, raw core with per-use output clamps; (a2, DONE 2026-09-25: the shared `SvfCoeffSweep`, the strip drift on the block edge; 13 corpus rows moved) the strip filter takes the node's per-block interpolation (the 32-sample ramp retires); (b, DONE 2026-09-25: the FM node's hard-coded Linear and the strip's omitted curve arguments found and fixed; 6 songs moved through their pitch envelopes) `MOD_ENV_CURVE = Exponential` (filter, pitch, FM); (c; c2 DONE 2026-09-26: `lpfCurves`/`hpfCurves`/`bpfCurves`/`notchCurves`, `FilterEnvDef`'s curve fields, `classic()`'s curve slots, no corpus song moved; c1 DONE 2026-09-25: the strip pitch envelope on `EnvelopeCore` through the mapping it shares with the node, `penv(amount, attack, decay, sustain, release)`, `penvCurves`, `PitchEnvelopeDefaults`; no corpus song moved) sprudel `lpfCurves`/`hpfCurves`/`bpfCurves`/`notchCurves`/`penvCurves`, `penv(amount, attack, decay, sustain, release)` with a real release, `classic()`'s filter curve slots | a ladder: rung 0 (the core behind temporary legacy switches) renders 17/17 identical, then one rule per rung with its predicted rows | deliberate SOUND CHANGES in a1 (fractional frames: a few decays), a2 (8 `lpf(env)` songs, audibly at steep onsets: -32 to -52 dB RMS, see the section 3c row), b (the kicks and pitch drops of 6 songs); the maintainer listens to before/after pairs |
| 6 | The built-ins re-registered, the strip off for them | THE step: minimal renders per built-in per door, plus the whole-corpus render | the teardown fade and the cull rule must land here or the corpus clicks and drops tremolo voices (step 6 found neither bites the corpus: no built-in uses `adsrOff`, and the tree cull landed in 3b; both are spec-proven). Re-run the benchmark against 9290 ns. IN PROGRESS: commit 1 (the switch) DONE 2026-09-26 (9 songs moved by the analog draw order, -50 to -82 dB, proven the only cause); commit 2 (`pregain` at the source, folded at unity, no song moved) DONE 2026-09-26; commit 3 (the benchmark, within noise on both platforms) DONE 2026-09-26. STEP 6 DONE, the listening checkpoint waits for the maintainer |
| 7 | The sample instrument | the corpus render DOES load samples (the CLI's `Samples.create` over the repo-root `./cache`; corrected 2026-09-26, only the `:jvmTest` `renderSong` helper has none), and specs register in-memory PCM through `makeVoice(getSample = ...)`; every render checks its sample-load count against the baseline | its own safety net. DONE 2026-09-26 (7 songs moved by the analog draw order, a sub-cent detune per hit, proven the only cause; expected inaudible, the listening checkpoint waits for the maintainer): one generic sample instrument `builtInVoice(IgnitorDsl.Sample)` (not reachable by name; no `Osc.sample()` script door yet, a recorded two-door asymmetry), the sample's own ADSR as per-sample defaults of the `adsr.*` slots, the playback fields (begin, end, speed, loop, cut, n) typed until they become slots in step 8; 7 songs predicted to move by the analog draw order |
| 8 | The doors become `oscp` aliases (about 50 to 60 functions). DONE 2026-09-27 (the mapping in sprudel's `toVoiceData()`, the backend scaffold gone, 17/17 identical). RESHAPED 2026-09-27 (maintainer): the doors, readers and merges stay typed (signal-flow plan section 4, the query hot loop's one allocation); the backend's `_classic_slot_bag.kt` translation MOVES into sprudel's `toVoiceData()`, which writes the classic slot keys onto the wire | door-parity specs, the wire golden regenerated | mechanical but wide; one door group at a time. Also: the sample playback fields begin, end, speed and loop become slots (from step 7; `n` and `cut` stay wire fields). Comes after step 10 (decided 2026-09-26: 10, then 8, then 9): the authored instruments still on the strip read the typed fields until step 10 moves them. Note for the benchmark: the authored voices in `SongBenchmarkCases` (`voices()`, `ladders()`, `experiments()`) stay on the strip, so step 8 changes their workload; a benchmark delta there is not an engine cost |
| 9 | `VoiceData` cut, `PipelineDsl` retired | compile-time, the golden regenerated | irreversible: only after 6 and 7 are ear-confirmed. DONE 2026-09-27: (a) the voice strip and the Pipeline DSL retired, DONE (17/17 identical; the strip's sound frozen as a baseline first); (a2) the strip-only SVF classes, DONE (19/19 identical; the resonators' base class changed in 2 forced lines); (b) the `VoiceData` cut, DONE (30 fields and the orphaned filter and envelope types; 19/19 identical, the golden moved only by `scale=` and the tie order); (c) the docs sweep, DONE (the living references rewritten to the tree, `pipeline-dsl-coefficient-exposure.md` and `engine-tuning-profile.md` archived) |
| 10 | The songs migrated with `.classic()` | per-song render against HEAD | D5 (decided: controls, then edit). DONE 2026-09-26 (the frozen texts too: `.classic()` appended, the two Der Schmetterling snapshots moved by the draw order only, -58 and -60 dB; the ledger's census columns step at this date), three commits: the engine (the tag, `onepole` into `classic()`, identity; DONE 2026-09-26, 17/17 identical), the songs (DONE 2026-09-26: 25 authored instruments end in `classic()`, the release tails written; 3 songs moved by the draw order, 14 identical), the frozen texts |
| 11 | The editor's unknown-slot diagnostic | UI, no audio | none. DEFERRED 2026-09-27 (maintainer): "no warnings yet; diagnostics tools come later once the design is fully settled". The step 11 plan (a `classic()` warning at evaluation time, and the per-slot check as 11b) is kept in the session scratchpad only |
| 9 follow-up | Collapse `BaseSvf` and `SvfBPF` into one static-coefficient resonator class (the (a2) review, 2026-09-27: the sweep path, `sweepCutoff` with frames, the `*Step` fields, `g`/`gStep` and `cutoffOffsetMul`, is dead in production since the strip filters retired; only the constructor snap runs) | renders identical | none; parked |
| 12 | `.master()` accepts a Katalyst; the Master DSL retires (maintainer, 2026-09-27: the Master is the Katalyst at the output position, a verbatim copy today) | an inventory first (both DSLs' stage sets side by side, their DSP classes, how parameters reach each, every place that knows "master" as a type, and the two chain swaps: the `Cylinder`'s five swap fields and the master's), then the render identity. The chain swap becomes ONE `SwapState` (Idle, Pending, Fading, Draining) serving both positions, the last open row of `docs/plans/effect-state-machines.md` section 3, converted once here instead of twice | decisions made 2026-09-27 (maintainer), the plan and inventory in `docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md`: a lookahead limiter can be built and runs anywhere (`lookahead` a knob of the compressor, `limiter(...)` a preset door; an orbit runs late by it), nothing fills a chain's `Param` slots at the output, duck is inert at the output and every other stage is allowed, the master adopts the orbit's swap law (drain, not cut). Six commits C1 to C6: C1 the cylinder's chain swap as `ChainSwap` (Idle, Fading, Draining), DONE (render identity: a raw-bits harness over 8 scenarios and 11085 blocks, 2 swap rows, the 19-row corpus); C2 the compressor's `lookahead` knob and the `limiter(...)` preset door, DONE (the corpus identical; a swap between chains of different latency crossfades with weights summing to one; 3 review rounds); C3 the output runs a `KatalystChain` behind the `MasterDslShim`, `MasterChain` gone, DONE (identity; the master's tests 8 specs and 75 rows to 7 and 43; decision (h), a refused unit recovers like an orbit's); C5 `.master()` takes a Katalyst, the Master DSL, its registry, `RegisterMaster`, the script `Master` object and the shim retired, DONE (identity, 19/19; songs migrated syntax-only); C4 the master on the shared `ChainSwap` (input ramp, then the leaving chain DRAINS instead of a cut), COMMITTED 2026-09-28 at the maintainer's request with review round 1 open: the maintainer's listening verdict on the pair, the drain-cap decision (a self-sustaining delay never finishes draining, so a later edit waits forever, on orbits too), and the round 1 MINORs (the cache guard made identical to the orbit's, two parked-request clears pinned); the listening pair ACCEPTED and the drain capped (decision (i), option A) with the post-stop rule (decision (j), refined: only endless tails are released), DONE 2026-09-28; C6 the docs sweep (skills, module memories and refs, active tasks and plans rewritten to the Katalyst master; `master-dsl-followups.md` sections 2 and 5 closed; the future task `docs/tasks/future/one-chain-host.md` opened), DONE 2026-09-28. DONE; closed 2026-09-28: risk R0 (the house `MasterStage` clip tested only through a copy) and the one-chain-host follow-up |
| 12 cleanup | The master's tests (maintainer, 2026-09-27): no master test repeats an effect test. What stays is the Katalyst and effect tests, plus a minimal set of MASTER-POSITION rows (adoption before the first block, no silence reset at the output, the swap law at the output, defaults-only Param slots, latency at the output, per-playback cleanup, and a Katalyst declared at the output running bit for bit like the same chain standalone). The rows that only duplicated DSP behaviour go in C3 (inventory `test-inventory.md` in the C3 scratch); the Master DSL surface tests (doors, builders, wire, registry) go with the DSL in C5 | the counts before and after, per spec and row; every removed row names the spec that covers it | none; in C3 and C5 |
| test consolidation | The engine redesign's test surface (maintainer, 2026-09-27): most effects now have one DSP core that can be tested on its own; the Ignitor node and the Katalyst stage over it then need their WIRING tested plus a handful of integration rows per effect, not the DSP law again. AUDITED 2026-09-27: 305 to 245 spec files, 3,145 to 2,112 test rows (-33 %), 69,556 to 55,794 lines (-20 %) at roughly the same coverage, plus a few specs to ADD where a core has no law spec; the sub-task, its seven commits and four maintainer decisions: `docs/tasks-archive/2026-09/20260928-test-consolidation.md` | each cut names the spec that covers it; the guards stay | DONE 2026-09-28 (commits 1 to 7 and the golden replacement; about 12,600 lines removed net; the unsure list kept whole by the maintainer), archived |

**Step 6, the plan's decisions (coordinator, 2026-09-26, following the record):** built-ins register as
`source.onepole(slot).classic()` and are flagged; an authored instrument keeps the strip until it retires (step 9, or with step 10, see the
OPEN point below), and the two paths coexist per voice; the backend maps the typed `VoiceData` fields onto the classic slot keys as SCAFFOLDING in
one file, removed in step 8 (the mapping moved into sprudel's `toVoiceData()`; the backend stays lean, as
`docs/plans/future/signal-graph-engine.md` wants); a door beats an `oscp` on the same slot (kept by step 8, in `toVoiceData()`); a built-in's
lifetime comes from its tree alone; the teardown fade is one `TeardownFadeRenderer` the strip's `adsrOff` and a built-in
without a root `Adsr` share (`BuiltIgnitor.endsInEnvelope`); the analog draw-order moves (9 corpus songs, the
cost section 8 names) are accepted under the principle and go on the listening list; a custom `.pipeline()` on a
built-in and a crush/coarse `oversample` above 1 on a built-in go inert (release notes; the latter until
`oversampling-regions.md`); `pregain` on the built-ins is its own later commit (section 7; landed as commit 2: `source.pregain().onepole(slot).classic()`).

**Listening checkpoint PASSED (maintainer, 2026-09-26: "listening test is fine").** Covers every pair on the phase 3
listening list: the D4 TetrisRemix bass, the envelope law's (a2) filter sweeps, (b) kicks and `sgbell`, (c1) `penv`
and (c2) `lpfCurves`, step 6 (the built-ins on `classic()`) and step 7 (the samples). Sakura's kick level and
Greensleeves' inert limiter stay as they are. Steps 6 and 7 are ear-confirmed, which is step 9's precondition; the
order of steps 9 and 10 was decided the same day (below): 10, then 8, then 9.

**DECIDED 2026-09-26 (maintainer): step 10 first, then step 9.** **Merge gate, revised 2026-09-27 (maintainer):** step 11 is deferred, and the merge gate below is dropped: the
maintainer decides about merging `engine-redesign` when step 9 is done, with the release-note list at hand. **Revised again 2026-09-27 (maintainer, after step 9):** "I will merge once we have all steps done": the
branch continues through step 12 (C4 to C6) and the test consolidation, then the maintainer merges.

**Step 9's decisions (2026-09-27, coordinator following the record):** an authored instrument that does not end in
`classic()` plays as the BARE tree (signal-flow section 5: no auto-wrap), keeping the 0.05 s lifetime fallback and
the teardown fade (no onset guard; CONFIRMED by the maintainer 2026-09-27 after step 9: no fade-in for a bare tree, it
plays exactly as authored); the parity specs FREEZE their strip side as a hashed baseline before the strip
goes (signal-flow section 12); the doors `loopBegin`/`loopEnd` go (no engine reader ever existed: a knob nothing
reads is removed); `VoiceData` keeps section 4's fields plus the voice-side pitch row and, each with a named reason,
`note`, `bank`, `soundIndex`, `legato`, `cut`, `solo`, `sourceId`, `cull`, `tags` (`scale` goes); the strip-only
filter classes go in their own commit (the orbit's body/vowel resonators share their base class); the
`voices/strip/` package keeps its name for now.

**Step 8's decisions (maintainer, 2026-09-27):** the mapping moves to `toVoiceData()` (row 8); an authored
instrument WITHOUT `classic()` loses its doors between step 8 and step 9 (the strip reads typed fields no longer
sent), accepted with one release note for steps 8 and 9 on TWO CONDITIONS: the branch is not merged or deployed
between step 8 and step 9, and step 11 (the editor's diagnostic for an instrument that does not end in `classic()`)
lands before the branch merges. Coordinator, following the record: the depth-7 filter fill stays in the slot layer
(step 5), `n` and `cut` stay wire fields (they pick the PCM and the choke group, not a voice stage). The sample playback
slots are flat (`begin`, `end`, `speed`, `loop`) and Kotlin-only (`IgnitorDsl.Slots.sample`, no `OscSlot.sample` on
the script door, a recorded two-door asymmetry: they are read by the engine at the playhead, not by a `classic()`
stage, so a script instrument has no stage to place them in; `Osc.param("speed", 1.0)` reaches them if ever needed),
by the slot naming rule (a slot mirrors the sprudel reader of its
door, as `onepole` and `pregain` do); so an authored instrument that declares a param of that name receives the door,
as every slot does. What the window means exactly (the step 8 audio review): an instrument whose tree does not END in
`classic()` keeps the strip at its default envelope and no pattern filter; one that contains `classic()` below its
root (`a.classic().plus(b.classic())`) now gets the doors PER BRANCH (distort and crush are nonlinear, so the sum
sounds different from the strip's doors after the sum); one that reads a slot itself (`OscSlot.lpf.freq`) now gets the
door directly.

**Step 10's decisions (maintainer, 2026-09-26):** the tag is
structural, `endsInClassic()` (the root is an `Adsr` whose `on` is the slot `adsr.on`; `classic()` is the last call),
the first piece of `docs/plans/future/signal-graph-engine.md`'s `.sprudel()` tag; the strip stretched its release to
an instrument's own tail (the 2026-08-27 envelope-ownership fix) and `classic()` does not, so the songs WRITE
`adsr(release = <tail>)` where a tail is longer (Sakura pad and shaku, Sandsturm pad and ohat, Greensleeves bass,
IrishLament blockfloete and fingerpick), no engine stretch; `onepole` moves into `classic()`; the order is 10, then 8, then 9. The songs' authored instruments move onto
`classic()` while the strip still runs, so no song loses its envelope between two commits. The question as raised:
**the order of steps 9 and 10 (found in step 6's plan, 2026-09-26).** Step 9 retires the
strip and `VoiceData`, step 10 migrates the songs whose AUTHORED instruments still rely on the strip (section 8's
list, `.classic()` appended). Done in that order, those songs lose their outer envelope and every door between the
two commits. Either step 10 comes before step 9, or they land together.

## 10. What the spike could not settle

Whether `classic()` can be byte-identical at all (D1 to D3 decide it); which side moves on the ADSR
frame counts; the JS numbers; the de-click name collision; the `pedal` tail; how the per-voice filter
tolerance and the `AnalogDrift` lane should be expressed on a filter node (per-voice rng draws that
no knob can carry, and the draw order has to match); and whether the filter-envelope difference is
audible (read from the code, not probed; the probe is a small addition to the spike's filter harness
and belongs first in step 3).

The spike's five probes are archived in the session scratchpad at `phase3-spike/`; each is a Kotest
file that drops into `audio_be/src/jvmTest/kotlin/ignitor/`.
