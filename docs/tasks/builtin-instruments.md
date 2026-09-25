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
the `pedal` preset retire (the `pedal` preset went early, 2026-09-25, D4); every voice door becomes an `oscp` alias; `VoiceData` is cut to the plan's
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

> **The phase's principle, stated by the maintainer (2026-09-25): a CONSISTENT FOUNDATION comes first, and the
> songs are adjusted to it.** "Sound changes are fine. The whole point of what we are currently doing is to
> build a consistent foundation. Current songs are built on the 'wrong' foundation and need to therefore be
> adjusted." Bit-identity is still the PROOF METHOD for steps that mean to change nothing; it is no longer a
> reason to keep two laws, two defaults or two vocabularies. A step that changes sound on purpose renders
> before/after pairs for the maintainer's ear instead.

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
  REAL release, `sustain` in place of `anchor`, the dead `curve` slot retired); (5) the envelope frame counts
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
- **D5, the frozen pieces.** (2026-09-25: the maintainer REPLACED the frozen Der Schmetterling SONG in
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

Walked one function at a time. The rule applied: the stage's own musical inputs stay on the door (the
phaser precedent), secondary knobs move to a builder behind `configure`, and a door whose only
defaulted knob is temporary stays flat. A knob that nothing reads is removed, not moved.

| Door | Door parameters | Builder knobs | Notes |
|---|---|---|---|
| `lowpass`, `highpass` | `freq, q = 0.707, configure` | `passes`, `analog`, `humanize`, `env(semitones)`, `adsr(attackSec, decaySec, sustainLevel, releaseSec, e => e.curves(attack, decay, release))` (nested since 3c) | the envelope is ONE `adsr` call, the pattern of the chain's own `adsr`; `env` and `adsr` each switch the cutoff envelope on and the other fills from the constants (the compound fill, now at the end of the lambda). The third positional argument becomes the lambda, which ends the `lowpass`/`lpf` positional trap |
| `bandpass`, `notch` | `freq, q = 0.707, configure` | as above without `passes` | |
| `drive` | `amount` | none | `driveType` is REMOVED everywhere (door, `IgnitorDsl.Drive`, the wire, `DriveIgnitor`): it had one value and nothing read it. `drive` is boost without shaping; every colour belongs to `shape` |
| `shape` | `shape = "soft", oversample = 0` | none | flat: `oversample` is the D7 stopgap and leaves with `oversampling-regions.md` |
| `distort` | `amount, shape = "soft", oversample = 0` | none | flat for the same reason; matches sprudel's `distort` position for position |
| `pitchEnvelope` | `semitones, configure` | `adsr(attackSec, decaySec, sustainLevel, releaseSec, e => e.curves(attack, decay, release))` (nested since 3c) | `releaseSec`, `curve` and `anchor` leave: release and curve were read and DISCARDED on both surfaces (the Ignitor runtime and the strip's `PitchEnvelopeRenderer`); the sustain replaces `anchor` and the release becomes real. An ADSR attacks from 0 where the old envelope attacked from the anchor; no song sets `anchor` or calls `penv`. The 12 call sites become `pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))` |
| `tremolo` | `rate, depth, configure` | `shape(name)`, `skew(amount)`, `phase(cycles)` | the three knobs are 3b's additions (they exist on the strip only). Rate first, like every Ignitor LFO door (`phaser`, `vibrato`); sprudel's `tremolo(depth, sync, ...)` differs in order and calls the rate `sync`, recorded for the sprudel side |
| `shimmer` | `wet, feedback = 0.5, tone = 4000, pitches, configure` | `floor` | the wet rule below; `dryFloor` becomes `floor` |
| `fm` | `modulator, ratio, depth, configure` | `adsr(attackSec, decaySec, sustainLevel, releaseSec)` | `freq` stays HIDDEN: the walk first put `freq(hz)` on the builder, then the maintainer, shown the recorded decision of 2026-08-30 ("a hidden internal of the pitch machinery; the default IS the semantics"), kept it hidden (2026-09-24). The node always had the index envelope; the Kotlin door exposed it (the built-in `sgbell` uses it) and the script door did not, a two-doors gap this closes. No `adsrCurves` yet: the FM envelope has no curve support, and a knob that does nothing is not offered. The maintainer wants it LATER; follow-up in `ignitor-dsl-open-items.md` |
| `phaser` (Ignitor AND Katalyst) | `wet, rate, center, sweep, configure` | `floor` | ONE shape on both hosts. On the Katalyst the door parameters are OPTIONAL, and an omitted one means exactly what an omitted builder knob meant before: the bare stage's fixed default, never the owner voice. Only a `Param` slot reads the orbit's `katp` state, whether `classic()` places it or an author writes it with `Katalyst.param`. The one home of what each bare stage carries is `KatalystStageDsl`'s KDoc in `KatalystDsl.kt`, with the data classes' constructor defaults. (Corrected 2026-09-24 by the 3d(ii) implementer and the round 1 reviewers, who read the code; the first wording was the coordinator's, written without it.) On the Ignitor `rate` stays required, so writing it means writing `wet` too. The PRINCIPLE, decided here for every bus effect: the effect's musical inputs on the door; secondary knobs on the builder |
| `vibrato` | `rate, semitones` | none | no defaults, nothing to decide |
| `eq` (Ignitor AND Katalyst) | `configure` | `band(freq, q = 0.707, db = 0)`, `tap(freq, q = 0.707, gain = 1)` | already one shape; the lambda stays optional like every `configure` (maintainer: all configure callbacks are optional), and an `eq()` with no bands is a transparent stage |
| `reverb` (Katalyst AND Master) | `wet, size, lowpass` | none | all optional (an omitted one is the bare stage's fixed default on both hosts, see the phaser row; never the voice's slot). Flat because nothing is left for a builder. The songs' `m.reverb(r => r.wet(0.05).size(7))` becomes `m.reverb(0.05, 7)` (five songs and the frozen pieces) |
| `delay` (Katalyst AND Master) | `wet, time, feedback, configure` | `cap(level)` | all door parameters optional; `cap` is secondary, like `floor`, so it sits on a builder even as its only knob. The prefix matches sprudel's `delay(wet, time, feedback, cap)`, which stays flat (sprudel has no builder layer) |
| `gain` (Katalyst AND Master) | `gain = 1.0` | none | a single construction input, nothing to decide |
| `compressor` (Katalyst) | `threshold, ratio, knee, attack, release` | none | FLAT, all optional, identical to sprudel's: every knob of a dynamics stage is a musical input |
| `limiter` (Master) | `threshold, ratio, knee, attack, lookahead, release` | none | flat, as the compressor. `thresholdDb` and `kneeDb` are RENAMED `threshold` and `knee` (builder, node, wire): the compressor and sprudel already say so, and the dB unit lives in the KDoc |
| `body` (Katalyst) | `wet, material, configure` | `floor` | the wet rule; `material` moves from the builder to the door, and the door parameter takes a name OR a number/`Katalyst.param` slot, because the builder knob was the only way to write a slot there and step 5a-2 decided the names are index slots (maintainer, 2026-09-18) |
| `vowel` (Katalyst) | `wet, vowel, configure` | `floor` | as `body` |
| `duck` (Katalyst) | `orbit, depth, attack` | none | flat and all optional, like the compressor (a dynamics stage); identical to sprudel's |

Not walked, nothing to decide: `onepole`, `crush`, `coarse`, `detune`, `accelerate` (no defaults), the
oscillators (already `(freq, configure)`).

**The envelopes, walked 2026-09-25 for step 3c (maintainer):**

| Door | Door parameters | Builder knobs | Notes |
|---|---|---|---|
| chain `adsr` | `attackSec, decaySec, sustainLevel, releaseSec, configure` | `curves(attack, decay, release)`, `declick(seconds)` | the reach-back chain methods `adsrCurves`, `declickSeconds` and `expK` RETIRE (they modified the preceding `adsr`, or wrapped a fresh default one). Inside a builder the prefix goes: `curves`, `declick` |
| `adsr` inside the filter and pitch builders | the same | `curves(attack, decay, release)` | NESTED, one shape everywhere: `.lowpass(800, 1.2, x => x.env(24).adsr(0.01, 0.3, 0.2, 0.5, e => e.curves(...)))`. The 3d(i) builder knob `adsrCurves` on the filter and pitch builders moves into the envelope's builder as `curves`. No `declick`: those envelopes have no de-click stage, and a knob that does nothing is not offered |
| `adsr` inside the fm builder | the same, without `configure` | none yet | FM's index envelope has no curve support (the filed follow-up); it gains `configure` with `curves` when that lands |
| `expK` | REMOVED | | "maybe the user wants to set different k for each individual curve": a per-curve bend is its own later design. Until then every exp stage bends at `ADSR_EXP_K` = 3 |
| the ADSR on/off switch | node field only, no Ignitor door | | `classic()` fills it from sprudel's `adsrOn`/`adsrOff` (a built-in's envelope is ON by default and only the slot turns it off); on the Ignitor door, not writing `adsr()` already means no envelope. A deliberate two-door asymmetry |


**Consequences recorded with the decisions:**
- **The default curve of a modulation envelope (filter and pitch) is ONE decision, and it is D3's.** Every
  pitch sweep today is linear on both surfaces and the chain's `adsr` defaults to exp; the filter
  envelope is linear on the node and exp on the strip. With `curves` on every envelope (nested inside `adsr` since 3c), D3 becomes
  "which default", not "which law".
- **The wet rule (maintainer, 2026-09-23): `wet` is the VERY FIRST parameter of every door that has
  one, in all four DSLs, and `floor` lives on the builder.** Consistency over each door's local
  logic. It applies to phaser, shimmer, reverb, delay, body and vowel. SPRUDEL MOVES TOO: its
  `phaser(rate, wet, ...)`, `body(material, wet, floor)` and `vowel(vowel, wet, floor)` become
  wet-first. Measured cost: 7 positional `body("...")` calls in built-in songs (StrangerThings, Sakura,
  IrishLamentTechno, SoundOfTheSea) and 4 in the frozen songs, which become `body(material = "...")`,
  bit-identical; no song calls `vowel` or `phaser` positionally. One semantic knock-on: sprudel's
  "no argument reinterprets the pattern's values as the first parameter" now reinterprets them as
  `wet`, so `seq("<0.2 0.5>").body()` patterns the wet (the first wording here said `n(...)`, which carries
  no value to reinterpret; corrected by the 3d(iii) implementer). What it cost, mapped in 3d(iii)'s plan:
  the bare `"<wood glass>".body()` / `"<a e>".vowel()` shortcut no longer names the stage (write
  `body(material = "<wood glass>")`), and the bare call's CLEAR path for material and vowel is gone (the
  off switch is `material = "none"` / `vowel = "none"`). No song, frozen piece or doc used either. The
  Lexikon, the sprudel reference and the KDoc examples moved with it (`body(0.7, "wood")`, `body(material = "wood")`).
  AND a positional `body("wood")` / `vowel("a")` in a user script outside the repo now SILENTLY does
  nothing: `"wood"` is a valid mini-notation pattern, so it lands in `wet` as a non-number, and the wet head
  writes nothing (the house raw rule forbids a `require`). `phaser(0.3)` becomes wet 0.3 at the default
  rate. Release-note item; whether the editor should WARN on a string literal in a wet slot is open for
  the maintainer.
- **The dry floor is `floor` everywhere** (maintainer, 2026-09-23): the Ignitor's `dryFloor` on the phaser
  and shimmer builders, their nodes and the wire is renamed; the Katalyst and sprudel already say `floor`.
  Re-asked 2026-09-24 with the reason the Ignitor KDoc gave for `dryFloor` (`floor()` is the arithmetic
  round-down, one word must not mean two things); the maintainer kept `floor` ON A CONDITION: "as long as
  floor() is only on effect builders we can live with the duplication in naming". So `floor` is a knob on
  effect builders (and a named parameter of sprudel's effect doors, which have no builder layer), never a
  door parameter of an Ignitor effect and never a method on `IgnitorDsl`.
- **Identity while D3 is open.** The curve knobs on the filter and pitch envelopes (`adsr(..., e => e.curves(...))` since 3c) must DEFAULT to
  the law each envelope has today (linear on both nodes), or every filter sweep and every kick in the songs
  changes in a door-shape step. D3 then decides the default for both at once, at its own ear checkpoint.
- **Wire changes** (the wire golden is regenerated, never hand-edited): `driveType` removed; `dryFloor`
  becomes `floor`; the limiter's `thresholdDb`/`kneeDb` become `threshold`/`knee`; the pitch envelope loses
  `releaseSec`, `curve` and `anchor` and gains the ADSR fields.
- **Positional reinterpretation (step 3d(i), 2026-09-24).** Wet-first moves a positional meaning: `phaser(0.3,
  800)` was rate 0.3 Hz and center 800, and is now wet 0.3 with an 800 Hz LFO; `shimmer(0.4)` was feedback 0.4
  and is now wet 0.4, on both doors. No in-tree caller was affected (grepped), but a user script outside the
  repo changes SILENTLY. The old positional filter and `pitchEnvelope` forms fail loudly instead. A release
  note must say it.
- **The limiter's positional order (step 3d(ii)).** `limiter(threshold, ratio, knee, attack, lookahead, release)`
  puts `lookahead` FIFTH, where the compressor and sprudel put `release`: a compressor-shaped 5-argument call
  `m.limiter(-3, 4, 2, 0.01, 0.2)` sets 200 ms of lookahead (bounded to 50 ms, i.e. latency), not a release. It
  is the maintainer's table; the release note recommends named arguments for the limiter.
- **Recorded two-door asymmetries (step 3b, 2026-09-25).** The Kotlin `tremolo` door stays FLAT
  (`tremolo(rate, depth, shape = "sine", skew = 0.0, phase = 0.0)`) where the script door takes a builder, the
  filter doors' precedent. The Kotlin `shape` and `distort` doors keep a `String` shape and an `Int` factor, while
  the script doors also take a number or a slot; a Kotlin caller writes a slot through the node constructor, as
  for `floor` and the pitch envelope.
- **Recorded two-door asymmetries (step 3d(i)).** The Kotlin filter doors stay flat and name the four envelope
  stages separately (audio_bridge cannot see the script builders; a superset of the builder). The Kotlin
  `phaser`/`shimmer` set `floor` only by `.copy(floor = ...)`: `floor` exists only as an effect-builder knob
  (the maintainer's condition), so the Kotlin node extensions `.wet()`/`.dryFloor()` were REMOVED, not renamed.
  There is no Kotlin `pitchEnvelope` door (there was none before either); Kotlin builds the node.
- **Sprudel's `penv` carries two dead slots**, `release` and `curve`, and an `anchor` whose meaning moves
  to a sustain. Raised for the sprudel side, not decided here.

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
  below 0 to the table.

## 5b. The off-value table (the one home; built in step 2, 2026-09-20)

A stage is not built when its gating knob is a `Param` or `Constant` LEAF and resolves either to a
non-finite value (the unset sentinel, `SLOT_UNSET`, is `Double.NaN`) or to the stage's off value
below. A knob that can MOVE within a note is never gated. This is the one home of these values;
code and memory point here and do not restate them.

| stage | off value | why, and what is not a fold |
|---|---|---|
| coarse | unset, or `<= 1.0` | at or below 0 and at a non-finite amount the render already takes a bit-exact bypass, so there the gate folds a bypass. In `(0, 1]` the engaged loop takes every sample but latches it through `nanGuard()`, so a NON-FINITE UPSTREAM SAMPLE used to come out as 0.0 and now passes through |
| crush | unset, or `< 1.0` | a fold: `levels = 2^amount`, and the renderer bypasses below two levels. At exactly 1.0 the quantizer RUNS (with the FLOOR law of D1, landed in step 4 as `CrushCore` (`quantize`; `halfLevels` decides when it engages), a saw becomes a two-level -1/0 pulse, +1 only at an exact +1; it was a three-level staircase under the old round), so 1.0 is ON |
| distort (`IgnitorDsl.Distort`, the fused node) | unset, or `<= 0.0` | **SINCE STEP 4 (2026-09-25) the fused node runs the STRIP's law (`DistortionCore`: no cap, the drive inside the oversampler) and this gate is its ONLY bypass: a leaf `<= 0` is not built; a modulated amount `<= 0` runs at unity drive (W5's state hazard designed out). What follows describes the node before step 4.** **A BEHAVIOUR CHANGE, not a fold.** The node is `drive(amount).shape(shape)` and only the DRIVE half ever bypassed, so the tree's chosen shaper stayed on the signal at unity gain. Modelled on a 220 Hz sine through the real chain (shaper, DC blocker, `softCap`): soft -1.77 dB, tube -6.24 dB at 16.8 % THD, gentle +0.64 to +5.21 dB, zerosquare +1.55 to +16.83 dB at 37 % THD, and `rectify` removes the fundamental altogether (full wave, an octave up). Neither authoring door builds it (both spell `distort` as `Shape(Drive(...))`); since step 5 it is `classic()`'s distort stage (before that, the only production site was `WarmupVocabulary` at 0.3). Gating it aligns that node with the `drive` row and with `Ignitor.distort(Double)`, which always short-circuited at the same value. It does NOT reopen ledger W5's gate-flip pop: W5 is a MODULATED amount crossing 0, and a modulated amount is not a leaf, so it is never gated |
| drive (`IgnitorDsl.Drive`, the row both doors reach) | unset, or `<= 0.0` | two things at once. At or below 0 a TRUE FOLD: `DriveIgnitor` already copies its input through unchanged. At a NON-FINITE amount it CLOSES A HOLE: `amt <= 0.0` is false for a NaN, so the gain was `10^(NaN * 1.2)` and every sample of the voice came out NaN with nothing between it and the orbit mix. Step 3 would have walked into it, because a `classic()` distort slot defaulting to `SLOT_UNSET` wires exactly this node. **`Shape` is not gated and cannot be**: it carries a transfer function and no amount knob, so there is nothing to read an off value from |
| tremolo | unset, or depth `<= 0.0` | a fold. The RATE is not a gating knob, nor are `shape`, `skew` and `phase` (added in 3b). A BUILT tremolo on the signal path sets `BuiltIgnitor.gatesOutput`, so the voice is never culled (section 6, pulled into 3b) |
| `mul` (`Times`, and the optimizer's `Affine(x, -0.0, k, -0.0)`) | exactly `1.0`. **Unset is NOT off** | the one asymmetry, forced by three existing specs: `TimesIgnitor` already sanitises a non-finite factor to an exact zero, and the node also sits in PARAMETER positions where that zero is the point. **Consequence: a `mul` slot must default to a safe literal, never `SLOT_UNSET`, or an unwritten one silences the voice** (guarded since step 2 by a row over `pregain()`, the one door that places a `mul` slot today). The `Affine` form is required because the optimizer rewrites a bare `x.mul(k)` and every registered tree renders optimized. The fold also takes only a SIGNAL survivor (`Ignitor.isBlockConstant`, structural and fixed at construction): what it drops is the multiply's `safeOut`, which on the audio spine fires only on a sample the survivor cannot produce, but in a PARAMETER position is what keeps a non-finite coefficient in range (`q = param("res", +Inf).mul(pregain)` resolved to `SAFE_MAX` and then the q ceiling; folded it would stay `+Inf` and land on the q fallback, a different filter). One qualification, audited in review round 3: on the audio spine every
arithmetic and unary node carries its own scrub, but the KARPLUS family writes `filtered * decay`
straight into its delay line with `decay` read raw, so an authored `decay > 1` diverges and the
fractional read turns the first infinity into a NaN. That divergence is authored character, not a
defect (the Motor stays raw), but it means the rule is "any NEW spine node that can emit a
non-finite sample from finite input owes a substitution at its own read", not "none can" |
| onepole | unset, or `<= 0.0` | NOT a fold on an authored tree: `onepole(0)` was a 5 Hz lowpass (the `clampSvfCutoff` floor), which is -33 dB at 110 Hz and -52 dB at 1 kHz, so a tree that used it as an accidental mute gets 30 to 50 dB louder. It is still right: `0` means off on every other door. Note the wart: `lowpass(freq = 0)` is still a 5 Hz filter, because a NUMBER is never off for the four SVFs |
| the four SVF filters | only when the cutoff is UNSET | a lowpass at 20 kHz is not an off state, which is why the rule is "unset" and not a number. A non-finite AUTHORED cutoff used to build a 1 kHz filter (the `clampSvfCutoff` fallback) and now builds none: a behaviour change as well as a NaN fix |
| the envelope | `on` at exactly 0.0 (either sign); unset is ON (since 3c) | inverted from the plan: the strip VCA runs on every voice today, so `classic()`'s ADSR is built BY DEFAULT and switches off only through the `adsrOn`/`adsrOff` slot of step 3. Its unset case was NOT safe, and step 2 had to make it safe: the unity-`mul` fold removed the `TimesIgnitor` scrub that used to turn a NaN into silence, so `sustainLevel` now substitutes its own default (`ADSR_SUSTAIN_LEVEL`) at the ADSR's read (`expK` did too until 3c removed it; every exp stage now bends at `ADSR_EXP_K`). SINCE 3c the envelope has its own switch: `Adsr.on` OFF at exactly 0.0 (either sign), unset ON (the second 'unset is not off' after `mul`), any other number and a non-leaf ON. OFF is not built, but it still reports the release tail from a LEAF release (a non-leaf release on an off envelope reports none, to avoid building it), because the strip's `adsrOff` keeps the voice's lifetime. Not a clamp: every finite value passes through untouched. The substitution runs BEFORE the existing coercion, so the infinities move too: a `+Inf` sustain used to hold at the 1.0 rail and a `-Inf` at 0.0, and both now read as unset and take the default, which is how every other knob reads a non-finite value. A non-finite `releaseSec` also stopped reporting a NaN release TAIL, which used to swallow a SIBLING's real one. The `adsrOff` slot itself landed in 3c as `Adsr.on`; `classic()` fills it in step 5 (see section 6's caveats). |
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
what sprudel's `!= null` means and is the recommendation. **DECIDED 2026-09-25 (maintainer): finite-in-the-bag only.** A
knob is written when the note's bag holds a finite value for its slot; an authored default alone never
switches the envelope on. **BUILT in step 5 (2026-09-25)** as `slotLayerDepth`/`writtenIn` in
`IgnitorDslRuntime.filterEnvDef`, with the implementer's correction: a WRITTEN depth always stands (an explicit
0 included, the strip's `depth ?: 7`); only an UNSET depth slot (a `Param` whose default is `SLOT_UNSET`, as
`classic()` places it) takes `FILTER_ENV_DEPTH_SEMITONES`, when any of the FOUR stage knobs is written; an
authored depth default, 0 included, is never filled (corrected in step 5's round 1); a `Constant` depth (a
door fill) is never the question. The rule's text lives in `/dsl-design` section 4.

**The distort question this leaves open, for D2.** `IgnitorDsl.Distort` WAS legacy by its own KDoc (step 3b
corrected it: it is kept as the one node that gates drive and shape as a unit, and the only one that could
drive inside the oversampler as the strip does; `Shape` itself is still not gated, its shape and factor are
read at build) and no production site builds it; both doors emit `Shape(Drive(...))`. `Drive` is gated, `Shape` cannot
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
second line of defence: the envelope's `sustainLevel` is the live example (`expK` was the other until 3c removed it).

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
  (on the source, before `classic()`'s stages), and its corpus render proves it.
- **Found in step 5 (2026-09-25), for step 6:** (1) the optimizer fuses every lone filter with a literal
  `analog` into an `Eq`, which never reaches `filterEnvDef`, so the slot-layer fill only runs on filters that
  stay filters; (2) the factory's lifetime floor (0.05 s) keeps a `classic()` voice with a shorter slot release
  rendering its de-click tail where the strip cut it; (3) an authored instrument with `.classic()` is
  enveloped TWICE until the strip VCA stops running for it; (4) the `adsrOff` difference is exactly the last
  192 frames, the strip's 4 ms teardown fade.
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
| 3 | The missing knobs of section 4, every default preserving today's tree bit for bit | the spike's five probes become the specs; door parity per knob | the biggest step by volume; 3a filters DONE; 3b waveshapers DONE 2026-09-25 (distort `shape`, tremolo `skew`/`phase`/`shape`, and distort's existing `oversample` read at voice build per D7; no `oversample` on crush or coarse); 3c envelopes DONE 2026-09-25 (one nested `adsr` shape, `expK` removed, curves as index knobs, the `on` switch). The filter build's rng draw order must reproduce the factory's exactly |
| 3d | THE DOOR SHAPES of section 3b (D6), inserted 2026-09-23 BEFORE 3b so 3b lands its knobs on builders. Three commits: (i) DONE 2026-09-24, the Ignitor doors, `driveType` removed, `dryFloor` renamed, the pitch envelope on `adsr`; (ii) DONE 2026-09-24, the Katalyst and Master doors, the limiter renames; (iii) DONE 2026-09-24, sprudel wet-first (`phaser`, `body`, `vowel`) with the song migration and the docs | the trees of every song bit-identical (a door moves, the sound does not); the wire golden regenerated for the renames; door parity per door | the pitch envelope is the one ENGINE change (it moves onto ADSR fields, the release becomes real): its default curve must stay today's linear law or 12 songs change. Sprudel's no-argument reinterpretation moves from `material` to `wet` |
| 4 | DONE 2026-09-25: D1 and D2 landed, NO listening needed (both decided so that no song changes): `CrushCore` (floor, the strip's NaN rule) and `DistortionCore` (the strip's law) shared by the strip and the tree; the 15 D1/D2 rows of `ClassicStripParitySpec` flipped to bit-identical at both rates (now 33/11 at 48 kHz, 32/12 at 44.1 kHz) | the corpus identical; controls moved exactly the 3 strip-crush and 11 strip-distort songs | a shared core moves both hosts together, so the parity spec cannot see a mutation inside it: `StripLawCoresSpec` pins the laws with oracles written in the test |
| 5 | DONE 2026-09-25: `classic()` on both doors, in the order of section 4 (`IgnitorDslClassic.kt`, the order written once; slots `<door>.<param>` in `IgnitorDsl.Slots.*` / `OscSlot.*`; `passes` a build-time knob that ROUNDS like the strip; the committed `ClassicStripParitySpec` pins 18 rows bit-identical to the strip and 26 divergent rows at 48 kHz (17 and 27 at 44.1 kHz, where the whole-frame ADSR row also diverges: the frame-count minor), each divergent row naming its cause: D1, D2 (step 4), D3, the ADSR frame counts, the lifetime floor, the teardown fade) | a one-voice render per door, each slot written in turn | the order: write it once, in one place |
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
