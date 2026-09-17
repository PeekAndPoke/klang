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
| `unison`, `spread`, `pregain`, `velocity` | instrument | the instrument's own knobs |
| `tremolo` | instrument | its phase is synced to the onset |
| `phaser` | bus | decided 2026-08-24, one coherent sweep over the bus |
| `delay`, `reverb` | bus | time effects on the sum |
| `compressor`, `duck` | bus | dynamics on the sum; ducking is cross-orbit by nature |
| `body`, `vowel` | bus | already there since July |
| `eq` as mix shaping | bus | linear and static, the case-1 win |
| `gain`, `pan`, `orbit` | channel | |
| `clip`, `late`, `swing`, `mute`, `solo` | timing and playback | not audio |
| `postgain` | retired | folded into gain by multiplication |

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
slots         oscParams: the instrument's knobs, pregain and velocity among them
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
  and a pregain inside it would consume the slot there, so velocity would drive the rack's distort
  and never the guitar's tubes while the unconsumed rule stayed silent. Built-ins are
  `Osc.saw().mul(OscSlot.pregain).classic()`; an author places `.mul(OscSlot.pregain)` where the
  player's touch enters, or leaves it to the unconsumed rule (§6).
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

## 6. Pregain, velocity, gain

| word | where | meaning |
|---|---|---|
| `pregain`, times velocity | a slot the instrument places, `.mul(OscSlot.pregain)`, before its own chain | how hard the note hits the instrument; drives the amp |
| `gain` | voice: after the instrument, before pan; orbit: after the chain; master: the shipped stage | tone-neutral level, one meaning on every surface |

- **Unconsumed pregain is applied at the instrument's output.** The voice factory already collects
  every `Param` a tree references. If `pregain` is among them, the resolved value goes into the
  slot and nowhere else; if not, it multiplies the ignitor's output, as today. Every existing song
  and authored instrument stays byte-identical; an author opts into drive by placing the slot.
- Velocity is the player: it multiplies the resolved pregain. An instrument that wants velocity as
  timbre reads a `velocity` slot with the same unconsumed rule.
- `postgain` retires. Today gain and postgain are one multiplier at one point (`SendRenderer`), so
  a song's `postgain(x)` folds into `gain` by multiplication; the word is removed.
- `.oscp("pregain", x)` and `.pregain(x)` are the same write; `OscSlot.pregain` carries the
  "filled by the engine, default 1.0" note the other engine-filled slots carry.

## 7. The Katalyst under this plan

The design in `../tasks/katalyst-dsl.md` stands, with three of its parked decisions resolved here:

- **D4:** the chain is the instrument. The bus doors (`reverb`, `delay`, `compressor`, `duck`,
  `phaser`, `body`, `vowel`) stop being voice fields and become `katp` aliases on the orbit's
  chain; the owner-voice override rule of that doc's §2 disappears with the fields it overrode. On
  the default chain every classic effect has its slots, so a beginner's first `.reverb(0.3)`
  works; on a declared chain without a reverb stage it does nothing.
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

- Frozen-song byte identity (`FrozenSongs`, the song benchmark's frozen July text) after every
  phase; the ledger's `ns/smp/pass` and the query-path benchmark before and after, so no phase
  trades sound or speed silently.
- Door parity specs on every surface change; wire round trips for every new variant; every new
  test mutation-checked (audio_be and wire are the mandatory tier).

## 10. Phases

Each phase is its own task, review loop and commit; each ends with the guards green.

1. **Katalyst DSL** (`../tasks/katalyst-dsl.md`, its phases 0 and 1): the chain on the wire,
   the per-cylinder swap, `eq` and `gain`, the bus doors becoming `katp` aliases, insert-style
   sends. Byte-identical except where one orbit carried different per-voice send amounts, which
   the frozen songs do not.
2. **Pregain**: the slot, the unconsumed rule, velocity onto pregain, `postgain` retired and
   folded. Byte-identical for every existing instrument (none places the slot yet).
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
- **Housekeeping, decided 2026-09-17: nothing in the backend or the frontend may allocate without a
  way to clean it up.** The process-wide identity maps (`uniqueId()` for ignitors, masters and
  chains) mint a name per live-coding edit and never forget it, while the backend bounds its built
  chains. Move them behind a global per-playbackId registry of such storages, the frontend twin of
  the backend's per-playback registry forks (`InlineDslRegistrar` already is one), and free the
  playback's entries when it dies. Ids become per playback, which the registry forks allow. Its own
  item, after phase 1; the Katalyst composition memo already lives in the pattern node and dies with it.

## Links

- `../tasks/katalyst-dsl.md`, the first phase, with the measurement that started this.
- `../tasks-archive/2026-08/20260803-master-dsl.md`, the pattern every chain follows.
- `../tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md`, why `SprudelVoiceData` stays typed.
- `../tasks/dsl-kotlin-surface-parity.md`, both doors, every phase.
- klang-ai `sessions/20260917-der-schmetterling-measure/README.md`, the hollow, measured.
