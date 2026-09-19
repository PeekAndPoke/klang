# Klang Audio — Memory

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
- **OPEN, pre-existing, recorded 2026-09-19 (not introduced by the state machine, bit-identical at
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
  constants, so a declared `k.reverb(r => r.wet(0.0).size(6))` that no pattern touches still rents
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
    running orbit, step 5c's to fix). It does NOT newly run on an orbit a song already compressed:
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
  off `katalystParams`, where non-finite is the DECLARED off state), and the only other reader of
  the bag on a render path is `IgnitorRegistry.createExciter`'s `oscParams["onepole"]`, which
  compares `> 0.0` and is therefore already non-finite-safe for NaN, though not for `+Infinity`;
  `VoiceFactory`'s `oscParams["analog"]` is the other, both out of this step's scope.
  `GraphCensus.of` is a FOURTH reader (`countOf`, `params[slot.name] ?: slot.default`) and resolves
  an override with no finite guard at all; it is audio-inert (benchmark only, never on a render
  path) and harmless as it stands, since `NaN.toInt()` is 0 and is then coerced to 1.
  Guard: `VoicePregainWireSpec`.
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
- `duckCylinder` in `VoiceData` sets the cylinder ID to duck when a voice plays; ducking is cross-cylinder sidechain.
- `VoiceData.soundIndex: Int?` is the universal variant channel — consumed by `SampleRequest` for sample-bank picking
  AND by `IgnitorRegistry.createExciter` → `IgnitorDsl.Variants` dispatch.
