# Signal flow redesign: three owners, one setter each

> **Status: designed 2026-09-17 with the maintainer, not started.** Umbrella for
> [`../tasks/katalyst-dsl.md`](../tasks/katalyst-dsl.md) (its first phase) and two tasks still to
> be opened (pregain, built-in instruments). Every phase leaves every existing song byte-identical
> unless the phase says otherwise, with the frozen-song render as the guard.

## 1. Why

The voice path is a Strudel inheritance: a pattern carries every effect as a field, and the engine
has a stage for every field. That is why the backend bakes crush, coarse, distort, tremolo and a
filter pipeline into every voice, why `reverb()` and `compressor()` are pattern setters, and why the
same word means different things on different hosts (the `size` parity bug of August was one
instance). Sprudel is one frontend among several to come (a pad controller, sheet music, tab), so
every field it stamps on a voice is a word the backend owns forever.

The concrete trigger was measured the same day (klang-ai
`sessions/20260917-der-schmetterling-measure/`): the song is hollow because its guitar wall is thin
at 160 to 400 Hz while the drum bodies sit on top of it, and both fixes are bus EQs after the amp
that no surface can write.

## 2. The flow

```
instrument (Ignitor)   everything per note: oscillators, envelopes, filters, distortion, crush,
                       coarse, tremolo, the amp. Its knobs are slots. The pattern fills slots.
channel                gain, pan, orbit. Three values, not a DSL. The channel fader.
bus (Katalyst)         everything shared: eq, reverb, delay, compressor, phaser, body, vowel,
                       mics, gain. Structure declared once per orbit; knobs are slots (katp).
master                 as shipped.
```

In desk terms: pregain is the guitar's volume knob and the player's touch; the amp lives inside
the instrument; gain is the channel fader with pan after it; the Katalyst is the group's inserts
and fader; the master is the master. There is no postgain in a desk and there is none here.

**The division rule.** A stage belongs to the instrument when it needs something only a note has:
its pitch, its onset, or its own signal alone (the three measured cases in the Katalyst task:
pitch tracking cannot move to a bus, a nonlinearity on a sum intermodulates, a per-note envelope
has no bus meaning). A stage belongs to the bus when it works on a sum.

| sprudel door today | host | why |
|---|---|---|
| `lpf`, `hpf`, `bpf`, `notch` with q, envelope, passes | instrument | the synth's VCF: per-note envelope, pitch tracking, one filter per note in a chord |
| `adsr` and the curves | instrument | onset and release are the note's |
| `distort`, `crush`, `coarse` | instrument | nonlinear, the settled power-amp reason |
| `vibrato`, `penv`, `fm`, `accelerate` | instrument | pitch is the note's |
| `unison`, `spread`, `pregain` | instrument | the instrument's own knobs |
| `velocity` | frontend | articulation shorthand, folded into `gain` at the wire (§6) |
| `tremolo` | instrument | its phase is synced to the onset |
| `phaser` | bus | decided 2026-08-24, one coherent sweep over the bus |
| `delay`, `reverb` | bus | time effects on the sum |
| `compressor`, `duck` | bus | dynamics on the sum; ducking is cross-orbit by nature |
| `body`, `vowel` | bus | already there since July |
| `eq` as mix shaping | bus | linear and static, the case-1 win |
| `gain`, `pan`, `orbit` | channel | |
| `clip`, `late`, `swing`, `mute`, `solo` | timing and playback | not audio |
| `postgain` | retired | renamed to `gain`; folds by multiplication where a song uses both (§6) |

A filter exists on both sides on purpose: the note's VCF and the bus's EQ filter different things.

## 3. The two structural rules

1. **Slots are the only per-event channel.** `gain()`, `oscp()`, `katp()` and every convenience
   door write slots. A rest in a setter's control pattern leaves the slot untouched (the rule
   committed 2026-09-16), so `.lpf("1000 ~")` needs no skip flag: the second event has no `lpf`
   entry and the instrument's default applies.
2. **Structure is declared once and never depends on an event value.** An instrument by name, a
   chain by name. Doors that add structure run at pattern construction and their knobs are
   literals or slots, never event values. Identity is content-addressed (`uniqueId()` on structural
   equality, `AnnounceOnceRegistry` per playback), which is only sound because structure is fixed.

The pattern surface does not change for the writer. What changes is who owns the words: the
instruments and the chains do, and the pattern only ever writes slots.

## 4. The bare-minimum voice on the wire

```
timing        start, duration, in seconds
instrument    sound: the name of a registered ignitor (built-in, authored, inline)
note          freq
slots         oscParams: the instrument's knobs, pregain among them (velocity never crosses, §6)
channel       gain, pan, orbit
bus           katalyst: chain name, katalystParams
master        master: chain name
control       the flag for events that carry state only
```

Everything else leaves `VoiceData`: envelope fields, filter definitions, distort, crush, coarse,
tremolo, phaser, the send amounts, reverb and delay parameters, body, vowel, compressor, ducking,
postgain, pipeline. Sample playback fields (begin, end, speed, loop, cut, n) stay only as slots of
the sample instrument.

**`SprudelVoiceData` keeps its typed, grouped, mutate-in-place fields.** That is the query path's
hot loop (the June work: twenty allocations per note down to one). The slot map is built once per
event in `toVoiceData()`, at the boundary that already allocates a `VoiceData` today, so a small
map of the slots a note actually set replaces a hundred-and-fifty-field object. If a measurement
ever shows the string-keyed map hurting on the worklet, the registered instrument's param list is
known at registration and the slots can travel as an array in that order. Not before a number.

**The wire voice is the interchange format.** Nothing a pattern kind knows about an event may live
only in its private event type if another kind should be able to modify or forward it (§8).

## 5. Built-in instruments, and the end of the Pipeline DSL

The saw is an oscillator; it has no lowpass. What the tutorials call `sound("saw").lpf(800)` was
always a subtractive synth voice with a saw in it. Make that explicit: `Osc.saw()` is the
oscillator for authoring; the built-in sound behind `sound("saw")` is an instrument, written in
the Ignitor DSL, registered once, whose stages are today's voice pipeline in today's order, every
stage gated on its slot:

```
let classic = x => x
  .mul(OscSlot.pregain)
  .crush(OscSlot.crush).coarse(OscSlot.coarse).distort(OscSlot.distort)
  .lowpass(freq = OscSlot.lpf, q = OscSlot.lpq, env = OscSlot.lpenv, ...)
  .highpass(freq = OscSlot.hpf, ...)
  .bandpass(freq = OscSlot.bpf, ...)
  .notch(freq = OscSlot.notch, ...)
  .tremolo(OscSlot.tremolo, ...)
  .adsr(OscSlot.attack, OscSlot.decay, OscSlot.sustain, OscSlot.release)

Osc.register("saw", Osc.saw().classic())
Osc.register("supersaw", Osc.supersaw().classic())
```

- `.classic()` is a plain function over the node type, shipped on both doors (Kotlin extension on
  `IgnitorDsl`, KlangScript function on the Osc extensions), so an authored instrument becomes a
  full synth voice with one call and an author who wants another order writes their own lambda,
  as Der Schmetterling does for its amps. No builder, no new node kinds, nothing on the wire.
  (Name chosen 2026-09-17: `classic`; `modern` carried the retired preset's word.)
- **`classic()` does not contain pregain.** Appended to an authored guitar it sits after the amp,
  so a pregain inside it would land in the wrong place for every instrument with its own
  nonlinearity. Built-ins are `Osc.saw().mul(OscSlot.pregain).classic()`; an author places
  `.mul(OscSlot.pregain)` where the player's touch enters, or does not place it, and then the
  instrument has no drive knob (§6: no unconsumed rule, no magic).
- **Authored instruments and the doors: a migration, and a diagnostic.** Today the pipeline runs
  after every ignitor, so `sound(guitar).hpf(120)` works on an authored guitar; Der Schmetterling
  relies on it (the trommel's `.hpf(160).lpf(3500)`, the bass's `.adsr(...).hpf(30)`, the kick's
  filters and `.distort(0.02)`, the guitars' `.adsrOff()`). Under the slot rule those doors write
  slots the instrument does not declare and go silent, so phase 3 is byte-identical for built-ins
  only. The migration: the built-in songs' authored instruments get `.classic()` appended where
  they use the doors, then the frozen-song guard proves identity. The trap gets a diagnostic, not
  magic: the editor knows every registered instrument's slot list (`collectParams`), so
  `sound(guitar).hpf(120)` on an instrument without an `hpf` slot is flagged inline ("guitar
  declares no slot hpf; append .classic() or place .highpass(OscSlot.hpf) in the instrument"),
  and `.katp` is checked against the chain's slots the same way. Auto-wrapping would hide
  structure, which is the thing this plan removes.
- **The gate moves to the node (decided 2026-09-17, the optimization phase 3 stands on).** Today
  the two paths differ. `FilterPipelineBuilder` adds a stage only when the voice wrote it (coarse
  above 1, crush and distort above 0, tremolo depth above 0), so an off stage does not exist. The
  Ignitor DSL path is not free: the literal overloads (`Ignitor.coarse(amount: Double)`) return the
  inner when the amount is off, but a node whose knob is a `Param` or `Constant`, which is what
  `.coarse(OscSlot.coarse)` becomes, builds its `CoarseIgnitor` unconditionally and pays a scratch
  render and a copy per block even at 0. A classic tail of ten slotted stages with nothing written
  would be ten buffer passes per voice. The rule that closes it: **at voice build, a stage whose
  gating knob is `Param` or `Constant` backed and resolves to the unset sentinel or to that stage's
  off value is not built; the builder returns the inner.** Params are per-note constants, so this is
  exact; expression-backed knobs (an LFO on the amount) stay unconditional because they can move
  within a note. The off value is per stage, not a global zero: coarse at or below 1, crush and
  distort at 0, tremolo at depth 0, phaser below `Phaser.MIN_ACTIVE_DEPTH`, a filter only when its
  cutoff is unset (a lowpass has no numeric off short of Nyquist), an envelope when unset. That list
  lives in the phase 3 task next to the sentinel rule, and `Slots.lpf` and friends default to unset
  rather than to a number. The build cache (`IgnitorBuildCache`, keyed on node identity and
  accumulated modifiers) must cover the on and off state in its key, or a voice written with
  `coarse(2)` could reuse a tree built for one without it. With the gate, a plain `sound("saw")` is
  a bare saw, bit-identical to today, and ten slots cost nothing until one is written.
- A pattern fills slots, never adds structure. Someone who wants a second lowpass or the highpass
  before the drive writes an instrument, inline or registered, and the same doors fill the same
  slot names on it. `sound(myGuitar).lpf(100)` on a guitar without an `lpf` slot does nothing.
- `PipelineDsl`, the filter pipeline builder, `Cmd.RegisterPipeline`, `PipelineRegistry` and the
  `pedal` preset (unused) retire. The pitch pipeline (vibrato, accelerate, pitch envelope, FM)
  writes the frequency modulation buffer as today and is untouched by this plan.
- Every voice door becomes an alias: `.lpf(x)` is `oscp("lpf", x)`, `.pregain(x)` is
  `oscp("pregain", x)`, and so on down the table in §2. The editor tools registry reads the slot
  vocabulary from the instrument definitions.

## 6. Pregain, gain, and where velocity went (rewritten 2026-09-18 with the maintainer)

```
Osc -> [A pregain] -> classic -> [B gain] -> pan, sum into the orbit -> Katalyst classic ... -> [C gain] -> master ... -> [D gain]
```

| spot | what it is | word | owner |
|---|---|---|---|
| A | into the instrument, the level at which the sound meets its first nonlinearity: how hard it is played. Changes TIMBRE | `pregain` | the instrument places the slot |
| B | after the voice's chain, per voice, tone-neutral | `gain` | the channel, with pan and orbit |
| C | after the bus chain, per orbit: make-up gain after the bus compressor, the group fader | the Katalyst's `gain` stage | the bus |
| D | end of the master | the master's `gain` stage | the master |

`gain` means ONE thing on every surface, a tone-neutral fader after the processing (the parity
rule). `pregain` is the one level that is not a fader, and it has its own word.

**Where this came from.** Strudel has `gain`, `velocity` and `postgain`, and they are two
POSITIONS, not three levels: `gain *= velocity` sits right after the source, before the filters
and the distortion (spot A), and `postgain` is the only level at the end (spot B); verified in
`superdough.mjs`. Klang's port applied all three at the very end, in the send stage, which is why
they looked redundant: they were. Strudel's `gain` is our `pregain`, Strudel's `postgain` is our
`gain`. Strudel has no shared insert bus, so it has no spot C.

**Rules.**

- **`pregain` is an ordinary slot: it does what the instrument wires it to, and nothing
  otherwise.** No unconsumed rule, no level applied behind the author's back, no analysis of the
  tree, no flag from the build. The built-ins place it explicitly:
  `Osc.saw().mul(OscSlot.pregain).classic()`, with a helper so the line reads
  `Osc.saw().pregain().classic()`. An authored instrument places it in front of its own
  nonlinearity (the Orchestertrommel: on the summed partials, before the skin's `distort`; one
  place, not one per oscillator, because the sum is linear), or not at all. On an instrument
  that never places it, `.pregain()` does nothing, and that surprises nobody: a bare sine has no
  drive. `.pregain(x)` is `.oscp("pregain", x)`.
- **`gain` is the channel, not part of the instrument.** The engine applies it to every voice,
  with pan, whatever the tree says, so it works on any instrument, wired or not. That is not
  magic: the channel was never the instrument's.
- **`velocity` is frontend shorthand and never reaches the backend.** Sprudel keeps the door,
  the field and the accessor for authors, and multiplies velocity into `gain` where the voice
  crosses the wire (`gain * velocity`, the product and order the backend computes today, so every
  song keeps its bits). It folds into `gain`, not into `pregain`: that is where Klang applies it
  today and where a real synth applies it by default (the amp), it works on every instrument,
  and nothing has to be migrated or listened to. It is folded at the wire and not at the door,
  because `velocity(p) = gain(mul(p))` taken literally does nothing on an unset gain and makes
  `.velocity(0.7).gain(0.5)` order-dependent. A pattern that wants touch (play harder, get
  dirtier) writes `.pregain("1 0.7 0.8")`. Another pattern kind may have its own articulation
  dial and folds it the same way. The `velocity` field leaves the wire.
- **`postgain` retires as a word**: it is renamed to `gain`. Where a song uses both, they fold by
  multiplication; they were one multiplier at one point already (`SendRenderer`).
- **`Katalyst.classic` ends in a `gain` stage at unity** (spot C). The stage returns early at
  unity and is bit-transparent, so it costs nothing. No pattern door for it yet: a chain says
  `k.classic().gain(0.8)` and a pattern reaches it with `katp`; a door is one line if the songs
  want it. One value per orbit, like every bus knob: the owner voice's value applies.
  Built 2026-09-19: the slot is `gain.gain` (the `<stage>.<knob>` rule; it reads oddly and it is
  the rule). The stage is last in the SERIAL list, before the duck, whose list position is
  ignored because it runs in the cross-orbit pass. It covers dry AND the delay and reverb
  returns, because those stages mix their returns into the orbit's buffer at their own
  positions. It is on the born-with chain too, and a gain stage never had a voice field, so
  `katp("gain.gain", x)` reaches ANY orbit without a declaration. One consequence worth knowing:
  orbit A's duck trigger is A's post-fader mix, so lowering A's fader also lowers how hard A
  ducks B, which is what a fader on a desk does.
- Non-finite values read as unset (`/dsl-design` §4). A non-finite `gain` is 1.0 at the voice
  factory; a NaN there used to poison the orbit's reverb and delay for the rest of the playback.
  For `pregain` the guard landed where a slot reads its value, the `Param` leaf, and for EVERY
  slot, not by name: a non-finite OVERRIDE from the bag takes the slot's default (the bag is an
  open map any frontend fills). An authored non-finite default still reaches the runtime; that
  is the one confirmed route by which a non-finite slot value still arrives.

**What was tried and deleted (2026-09-18), so nobody rebuilds it.** The first implementation
gave `pregain` and `velocity` an unconsumed rule (an unplaced slot acts as level at the output)
and coupled them (velocity rode on pregain). The slot's value then depended on knowing, BEFORE
the build, which slots the build would create, so a separate analysis of the tree predicted it.
Three review rounds found the prediction disagreeing with the build three times: `Variants`
(the union over variants while the build picks one), two arms that skip a child (`Detune`'s
identity fold, the plain-sine branch), and then the work cap that kept the analysis cheap on
the audio thread mispredicting kits. Lesson, in the review ledger: code that predicts another
walk's outcome drifts from it wherever that walk is conditional. The maintainer's rule that
replaced it: no magic. A knob does what the tree wires, the channel is the channel, and
articulation is the frontend's business.

**Considered and rejected the same day:** wrapping a bare signal in `classic()` automatically
(where is the line: is `Osc.sine().mul(0.5)` bare?); an unplaced knob acting at the output (the
magic above); velocity as a second backend slot; velocity folded into `pregain` (Strudel's
position: a trap for every instrument that does not place the slot, and a sound change for
every driven one). Still open, deliberately: a construction that makes "this instrument does not
listen to that door" impossible to write by accident, without a warning. A type boundary between
a signal and an instrument was discussed and not adopted; `sound()` also takes samples.

**Seen while migrating the songs (2026-09-19), parked for the maintainer.** With `postgain` gone
there is no spelling for "scale this voice's level whatever it is": `gain(x)` replaces, and the
mapper form `gain(mul(x))` scales a gain that is SET and is a silent no-op on an unset one (the
general mapper rule of 2026-09-07: a mapper that yields nothing leaves the field unchanged). It
also joins by `appLeft`, so a rest in a patterned trim drops the note where `postgain(P)` left it
alone. In the repo this is safe: every exported `*_arrange` that trims with `gain(mul(x))` runs
after a `*_shape` that sets `gain`. An importer who applies an arrange without its shape gets
unity. Options when it comes up: leave it (the author sets gain first); let a mapper on a field
with a known neutral value start from that value (`gain` unset reads as 1.0 for `mul`); or a
group-level trim that is not a voice field at all (the Katalyst's `gain` stage, spot C, is
already that for a whole orbit).

## 7. The Katalyst under this plan

The design in `../tasks/katalyst-dsl.md` stands, with three of its parked decisions resolved here:

- **D4:** the chain is the instrument. The bus doors (`reverb`, `delay`, `compressor`, `duck`,
  `phaser`, `body`, `vowel`) stop being voice fields and become `katp` aliases on the orbit's
  chain; the owner-voice override rule of that doc's §2 disappears with the fields it overrode. On
  the default chain every classic effect has its slots, so a beginner's first `.reverb(0.3)`
  works; on a declared chain without a reverb stage it does nothing. Decided 2026-09-18: the
  `body` material and the `vowel` name are numeric INDEX slots into the shared tables in
  `audio_bridge` (`body.material`, `vowel.vowel`, 0 = none), so no string slot kind joins the
  wire and the pattern doors keep working on a declared chain; and `.katalyst(dsl)` REPLACES
  like `sound()` and `master()`, the append rule of the task doc's step 1 is retired.
- **D2 dissolves:** with no per-voice send amounts, the send buffers go. `reverb` and `delay`
  become insert-style stages fed by the mix at their list position scaled by `wet`, exactly the
  master's `MasterStageDsl.Reverb` model. "The room hears the cab" is then just list order.
  The price is per-voice send variation within one orbit, which the lease already made unreliable;
  two orbits or a `katp` pattern cover it.
- **D5:** the orbit `gain` stage stays; it is the group fader after the inserts.
- One chain per orbit, declared where the orbit is; patterns stacked on one orbit share it, and two
  patterns declaring different chains for one orbit is the author error it is on the master.

## 8. Interoperability of pattern kinds (kept in mind, not planned)

`KlangPattern` has one function and its events carry cycles, the inline references a playback must
announce, and `toVoiceData()`. A foreign kind (a pad controller, sheet music, tab) plays through
every playback today. The other direction, `drums.reverb(...)` on a foreign pattern, is one adapter:
`KlangPattern.toSprudel()` lifts each foreign event into a `SprudelPatternEvent` (whole and part
from start and duration, data from `toVoiceData()`), and `PatternLike` accepts a `KlangPattern` by
lifting at the door. `PatternMapperFn` and `lang_helpers.kt` stay sprudel's. The lift is lossless
exactly because the wire voice is the bare minimum of §4, which is why this plan makes
interoperability cheap. The contract a foreign kind must meet is the twelve-cycle rule: each event
exactly once, in the query whose window contains its onset, with its full duration, any sub-cycle
window answered consistently; and `cps` must reach the lift. `KlangPatternEvent` stays free of any
one language's types (the pipeline reference leaves, a Katalyst reference joins).

## 9. Guards

- **Sound identity is proven on minimal examples, not on songs** (§12 replaces the frozen-song
  guard this bullet named until 2026-09-18): one voice or one stage in doubles compared by raw
  bits, small multi-orbit render rows for wiring, and, where a claim is "identical to the code
  before this change", a MINIMAL file rendered at HEAD in a throwaway worktree and on the final
  tree with equal hashes. Every such claim names its render, made on the final tree and
  exercising the changed path. `FrozenSongs` stays what it is, the song benchmark's input.
- The ledger's `ns/smp/pass` and the query-path benchmark before and after a phase, so no phase
  trades speed silently.
- Door parity specs on every surface change; wire round trips for every new variant; every new
  test mutation-checked (audio_be and wire are the mandatory tier).

## 10. Phases

Each phase is its own task, review loop and commit; each ends with the guards green.

1. **Katalyst DSL** (`../tasks/katalyst-dsl.md`, its phases 0 and 1): the chain on the wire,
   the per-cylinder swap, `eq` and `gain`, the bus doors becoming `katp` aliases, insert-style
   sends. Byte-identical except where one orbit carried different per-voice send amounts, which
   the frozen songs do not.
2. **Pregain and the levels** (§6, rewritten 2026-09-18): the `pregain` slot with its door and
   the `.pregain()` helper, inert unless placed; `velocity` folded into `gain` at the wire and
   the `velocity` field off the wire (the wire golden is a baseline and is regenerated);
   `postgain` renamed to `gain` across the built-in songs and the tutorial (the maintainer
   allowed the mechanical change in Der Schmetterling); the finite guards on `pregain` and `gain`;
   `Katalyst.classic` ending in a unity `gain` stage. The first attempt of 2026-09-18 is
   discarded. Two steps: **levels on the wire**, DONE 2026-09-19 (spot B: the velocity fold, `postgain` retired,
   the finite guard on `gain`, the song migration), then **the slot and the bus fader**, DONE
   2026-09-19 (spots A and C: `pregain`, the unity `gain` stage). Identity, stated precisely: a voice that never used
   `postgain` is bit-identical in doubles (`x * 1.0` is exact, and `gain * velocity` is the same
   product computed on the other side of the wire). A voice that used `postgain` changes by
   floating-point rounding only, because `(s * post) * (gain * pan)` became
   `s * ((gain * post) * pan)`; that is accepted and needs no listening checkpoint. The song
   migration is checked per event (old product against new wire gain, relative 1e-12) by a
   one-off fixture that is deleted with the step.
3. **Built-in instruments**: `.classic()` on both doors, the built-ins as registered definitions,
   the node-level gate with the build-cache key covering it, the voice doors as `oscp` aliases,
   `VoiceData` cut to §4, the Pipeline DSL and the filter pipeline builder retired, the built-in
   songs' authored instruments migrated with `.classic()`, the unknown-slot diagnostic in the
   editor. Byte-identical by the gate rule for built-ins and by the migration for the songs.
4. **Interoperability lift**: when the second pattern kind exists, not before.
5. **The announcements, last.** Everything that describes a retired surface is re-read and
   rewritten once the surfaces are gone: the whitepaper (`docs/whitepaper/klang-whitepaper.html`,
   which explains the voice pipeline, `postgain`, the per-voice effect fields and the Strudel
   lineage), the tutorials and the Lexikon (doors described as fields become slot aliases, the
   Pipeline DSL and `pedal` disappear, `Katalyst` and `pregain` appear), the sprudel and ignitor
   reference files that the klang-ai workspace symlinks (`ref/sprudel-reference.md`,
   `ref/ignitor-reference.md`), the `/klang-music-writing` skill, the module `CLAUDE.md` and
   `MEMORY.md` files, and the rules register's retired list, which gets `postgain`, `PipelineDsl`,
   `pedal` and the per-voice bus doors with the date. A grep for each retired word over `docs/`,
   the skills, the tutorials and the builtin songs is the checklist; the phase ends when it is
   empty outside `docs/tasks-archive/`, `docs/history/` and the dev diary, which are never fixed.

## 11. Open points

- The voice's lifetime. Today the pipeline VCA's release decides when a voice ends and is culled.
  With the envelope inside the tree, the rule becomes "duration plus the tree's longest release",
  read from the tree at build. Needs its own paragraph in phase 3's task.
- `Osc` and `oscp` are misnomers for the Ignitor concept (the known debt in `/dsl-design` §5) and get
  renamed in their own item, after phase 3, once the slot vocabulary has settled.
- Tutorials, the Lexikon and the whitepaper describe doors as fields and the voice pipeline as the
  engine; phase 5 re-reads them once the surfaces are gone.
- Sample voices: `sound("bd")` as the sample instrument with its playback slots, in phase 3.
- **Parked for the maintainer, by ear (found in review 2026-09-19, NOT rendered): the group
  fader patterned through exactly 0 on a dry orbit.** `katp("gain.gain", "<0 1>")`, default ADSR,
  no room or line. The orbit's silence gate reads the POST-fader mix, so with the fader at 0 the
  cylinder deactivates and resets while voices still play; the lease is then re-dealt, and the
  first `configure` after a reset SNAPS. Depending on which voice wins the lease, the return to
  level either jumps from 0 to full in one sample (with the muted note's release tail becoming
  audible) or waits for the previous owner to lapse (release plus two blocks, about 56 ms) and
  ramps mid-note. The mechanism is from Katalyst step 4; it is newly reachable because the fader
  now sits on every orbit. The `katp` door says plainly that the fader is a mix knob that follows
  the lease, not a per-note articulation. The root is that an orbit's liveness is judged on
  post-fader output while its reset assumes nobody is playing; candidates: judge liveness before
  the fader, or never snap while voices are active. Belongs with Katalyst 5c (crossfade on every
  switch, the effect state machines), where the gain stage gets its states anyway.
- **Phase 3, from the pregain review (2026-09-19): a placed `pregain` costs CPU at unity.** Neither
  `mulConstInPlace` nor the `Affine` fold special-cases a block-constant multiplier of exactly 1.0
  at render, so every built-in that places the slot pays one multiply and one `safeOut` per sample
  for the life of every voice. Folding it away at build would drop the `safeOut`, which changes
  bits for a NaN or an out-of-range sample, so it is a decision (is the extra scrub wanted?), not
  a blind optimisation. Measure in the phase 3 spike before the built-ins place the slot.
- **Phase 3: `analog` is the one bag key with two readers that disagree on a non-finite value.**
  Since the leaf guard, the oscillator's `Slots.analog` reads a NaN as its default, while
  `VoiceFactory` still hands the raw value to the filter-feel scales and the sample ignitor.
  `oscParams["onepole"]` is NaN-safe by accident and not `+Infinity`-safe. Both go away when those
  doors become slots; until then they are listed here so nobody concludes the bag is guarded
  everywhere. A `ParamIgnitor` that engine code constructs directly never passes the leaf either;
  no production caller does that today (checked 2026-09-19: a `FilterDef` becomes an `AudioFilter`
  and never reaches `Ignitor.svf`, whose `Double` overload has only a test caller). A fourth reader, `GraphCensus`
  (benchmark-only, audio-inert), resolves an override without the guard too. For phase 5's grep
  list: `docs/audio-backend-file-map.md` still spells the orbit chain without body, vowel and the
  fader.
- **Housekeeping, decided 2026-09-17: nothing in the backend or the frontend may allocate without a
  way to clean it up.** The process-wide identity maps (`uniqueId()` for ignitors, masters and
  chains) mint a name per live-coding edit and never forget it, while the backend bounds its built
  chains. Move them behind a global per-playbackId registry of such storages, the frontend twin of
  the backend's per-playback registry forks (`InlineDslRegistrar` already is one), and free the
  playback's entries when it dies. Ids become per playback, which the registry forks allow. Its own
  item, after phase 1; the Katalyst composition memo was retired 2026-09-18 with the append rule.

## 12. Safety nets: what they are for, and when they go (maintainer, 2026-09-18)

A byte-identity net is a MIGRATION TOOL, not a contract. It exists so the loop can run unattended
between two moments at which the maintainer has listened; at the next such moment it has done its
job. Kept past that, it ties knots around our legs: every deliberate sound change fights a test
that only says "different", and the pace drops. Three kinds, three lifetimes:

| kind | examples | lifetime |
|---|---|---|
| **contract** | door parity specs, the defaults-sync specs, catalogue index specs, `ParamBagSpec`, the wire codec round trip | permanent; they state what the surface promises |
| **baseline** | the wire golden (`voicedata_golden.txt`), the frozen minimal corpus of phase 3 (one hash per row of raw doubles) | valid from one ear-confirmed checkpoint to the next; REGENERATED at the checkpoint, never hand-edited, never defended against a change the maintainer approved by ear |
| **migration fixture** | old-path against new-path comparisons (`Katalyst…MatchesUntouchedVoice`, the declared-against-born-with render rows, `*MigrationSpec`, `*ParitySpec` whose two sides are an old and a new implementation) | removed in the change that finishes the migration (the scaffolding guideline); the oracle it carried is frozen into the baseline first if it is still wanted |

**No full songs as tests.** A song renders slowly, exercises only the doors it happens to call
(the phaser fill had no evidence behind two frozen songs), quantises to 16 bits, and a failure
does not say where. Acceptance is minimal examples at three levels: the wire as text, one voice
or one stage in doubles compared by raw bits, and small multi-orbit render rows for wiring
(shared orbit, chain install, swap), each with an engagement control, a not-silence floor and a
mutation. The frozen-song hash used as the acceptance of Katalyst steps 1 to 5a-3 ends with 5a-3.

**A one-off whole-corpus render is not a test, and it is the right net for one kind of claim**
(2026-09-19, Katalyst 5b-1). When a step's claim is "every existing song sounds the same" and
the change is a change of PATH (equal numbers travelling a different route), door-form tables and
value tripwires cannot see a gate that treats equal values differently; rendering every built-in
song and frozen piece at HEAD in a throwaway worktree and on the final tree can. It is a
migration fixture: written for the step, deleted with it, its result recorded in the task doc.
Two things it needs: enough cycles to reach every section (the longest `arrange` and the longest
`mute` alternation set the bar; 256 covered the corpus of 2026-09-19), and the WALL CLOCK PINNED,
because four songs seed their randomness from `timeOfDay` or `sinOfDay` and otherwise differ for
a reason that has nothing to do with the engine. What it cannot see: sample voices (the jvm
offline renderer has no sample bank), differences under one 16-bit count, JS-only behaviour.

**Checkpoints.** A checkpoint is the maintainer saying "this sounds right". The foreseen ones:
after the insert-style sends (Katalyst 5b), after the switch fades (5c), after the phase 3
spike, at the end of phase 3. At each: regenerate the baselines, delete the migration fixtures
whose migration is finished, and run one audit pass over the specs named `*Parity*`,
`*Migration*` and `*Golden*` to classify each as contract, baseline or fixture. The first audit
is due at the 5b checkpoint; candidates seen on 2026-09-18 without reading them: `DelayLineMigrationSpec`,
`OnepoleParitySpec`, `BlockSizeParitySpec`, `OversamplerDecimatorParitySpec`, `OptimizerSongParitySpec`.

## Links

- `../tasks/katalyst-dsl.md`, the first phase, with the measurement that started this.
- `../tasks-archive/2026-08/20260803-master-dsl.md`, the pattern every chain follows.
- `../tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md`, why `SprudelVoiceData` stays typed.
- `../tasks/dsl-kotlin-surface-parity.md`, both doors, every phase.
- klang-ai `sessions/20260917-der-schmetterling-measure/README.md`, the hollow, measured.
